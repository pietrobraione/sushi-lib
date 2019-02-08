package sushi.compile.path_condition_distance;

import static sushi.util.ReflectionUtils.method;
import static sushi.util.TypeUtils.DOUBLE;
import static sushi.util.TypeUtils.FLOAT;
import static sushi.util.TypeUtils.INT;
import static sushi.util.TypeUtils.LONG;
import static sushi.util.TypeUtils.javaClass;
import static sushi.util.TypeUtils.splitParametersDescriptors;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import sushi.util.ReflectionUtils;

public class CandidateBackbone {
	// We keep the direct and reverse mapping between visited objects and their origins 
	private final Map<ObjectMapWrapper, String> visitedObjects = new HashMap<ObjectMapWrapper, String>(); 
	private final Map<String, Object> visitedOrigins = new HashMap<String, Object>(); 

	private final Collection<String> invalidFieldPaths = new HashSet<String>(); 
	
	private void storeInBackbone(Object obj, String origin) {
		// If another origin already exist, this is an alias path
		// and then it shall not be stored
		if (!visitedObjects.containsKey(new ObjectMapWrapper(obj))) {
			visitedOrigins.put(origin, obj);		
			visitedObjects.put(new ObjectMapWrapper(obj), origin);
		}
	}

	public Object getVisitedObject(String origin) {
		return visitedOrigins.get(origin);
	}

	public String getOrigin(Object obj) {
		return visitedObjects.get(new ObjectMapWrapper(obj));
	}

	public void addInvalidFieldPath(String refPath) {
		invalidFieldPaths.add(refPath);
	}
	
	private static final class ObjectMapWrapper {
		private Object o;
		ObjectMapWrapper(Object o) { this.o = o; }
	
		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof ObjectMapWrapper)) {
				return false;
			}
			final ObjectMapWrapper omw = (ObjectMapWrapper) obj;
			return (this.o == omw.o);
		}
	
		@Override
		public int hashCode() {
			return System.identityHashCode(this.o);
		}
	}

	public Object retrieveOrVisitField(String origin, Map<String, Object> candidateObjects) 
	throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
		assert (origin != null); 
		
		//check in the cache of the visited object
		Object obj = getVisitedObject(origin);
		if (obj != null) {
			return obj;
		}
		
		final boolean startsFromLocalVariable = origin.startsWith("{");
		final boolean startsFromStaticField = origin.startsWith("[");
		final boolean startsFromMethodInvocation = origin.startsWith("<");
		if (startsFromLocalVariable || startsFromStaticField || startsFromMethodInvocation) {
			final String[] fields = splitFields(origin);

			String originPrefix = fields[0];
			if (this.invalidFieldPaths.contains(originPrefix)) {
				throw new FieldDependsOnInvalidFieldPathException(originPrefix);
			}
			
			//first field
			if (startsFromLocalVariable) {
				final boolean hasArrayIndexAccess = fields[0].contains("[");
				final String root = (hasArrayIndexAccess ? fields[0].substring(0, fields[0].indexOf('[')) : fields[0]);
				if (candidateObjects.containsKey(root)) {
					obj = candidateObjects.get(root);
					if (hasArrayIndexAccess) {
						if (obj.getClass().isArray()) {
							obj = retrieveFromArray(obj, fields[0].substring(fields[0].indexOf('[')), candidateObjects);
						} else {
							throw new SimilarityComputationException("Tried an array index access but the object is not an array.");
						}
					}
				} else {
					throw new SimilarityComputationException("Local variable (parameter) origin " + root + " not found in candidateObjects.");
				}
			} else if (startsFromStaticField) {
				originPrefix += "." + fields[1];
				if (this.invalidFieldPaths.contains(originPrefix)) {
					throw new FieldDependsOnInvalidFieldPathException(originPrefix);
				}
				try {
					final String className = javaClass(fields[0].substring(1, fields[0].length() - 1), false);
					final boolean hasArrayIndexAccess = fields[1].contains("[");
					final String fieldName = (hasArrayIndexAccess ? fields[1].substring(0, fields[1].indexOf('[')) : fields[1]);
					final Field f = Class.forName(className).getDeclaredField(fieldName);
					if (f == null) {
						throw new SimilarityComputationException("Field name " + fieldName + " in origin " + origin + " does not exist in the class " + className + ".");
					}
					f.setAccessible(true);
					obj = f.get(null);
					if (hasArrayIndexAccess) {
						if (obj.getClass().isArray()) {
							obj = retrieveFromArray(obj, fields[1].substring(fields[1].indexOf('[')), candidateObjects);
						} else {
							throw new SimilarityComputationException("Tried an array index access but the object is not an array.");
						}
					}
				} catch (ClassNotFoundException | NoSuchFieldException e) {
					throw new SimilarityComputationException("Static field origin " + originPrefix + " does not exist.");
				} catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
					throw new SimilarityComputationException("Unexpected error while retrieving the value of a static field: " + originPrefix);
				}
			} else { //starts from method invocation
				//separates method signature and parameters list
				final int firstSemicolonIndex = fields[0].indexOf(':');
				if (firstSemicolonIndex == -1) {
					throw new SimilarityComputationException("Unrecognized origin " + origin + ".");
				}
				final int secondSemicolonIndex = fields[0].substring(firstSemicolonIndex + 1).indexOf(':') + firstSemicolonIndex + 1;
				if (secondSemicolonIndex == -1) {
					throw new SimilarityComputationException("Unrecognized origin " + origin + ".");
				}
				final int firstParensIndex = fields[0].substring(secondSemicolonIndex).indexOf('(') + secondSemicolonIndex;
				final int lastParensIndex = fields[0].substring(secondSemicolonIndex).lastIndexOf(')') + secondSemicolonIndex;
				if (firstParensIndex == -1 || lastParensIndex == -1 || firstParensIndex > lastParensIndex) {
					throw new SimilarityComputationException("Unrecognized origin " + origin + ".");
				}
				final String methodClassName = fields[0].substring(1, firstSemicolonIndex); //remove leading '<'
				final String methodDescriptor = fields[0].substring(firstSemicolonIndex + 1, secondSemicolonIndex);
				final String methodName = fields[0].substring(secondSemicolonIndex + 1, firstParensIndex);
				final String parameters = fields[0].substring(firstParensIndex + 1, lastParensIndex);
				
				//splits the parameters list into parameters
				final ArrayList<String> parametersList = new ArrayList<>();
				int beginParameter = 0;
				int nestingLevel = 0;
				for (int i = 0; i < parameters.length(); ++i) {
					if (parameters.charAt(i) == ',' && nestingLevel == 0) {
						if (i < parameters.length() - 1) {
							parametersList.add(parameters.substring(beginParameter, i).trim());
							beginParameter = i + 1;
						} else {
							throw new SimilarityComputationException("Function application found with wrong parameters list: " + parameters + ".");
						}
					} else if (parameters.charAt(i) == '(' || parameters.charAt(i) == '[') {
						++nestingLevel;
					} else if (parameters.charAt(i) == ')' || parameters.charAt(i) == ']') {
						--nestingLevel;
					} //else nothing
				}
				parametersList.add(parameters.substring(beginParameter).trim()); //last parameter
				
				//gets the parameters in the list
				final Object[] objParameters = new Object[parametersList.size()];
				for (int i = 0; i < parametersList.size(); ++i) {
					final String parameter = parametersList.get(i);
					final Object objParameter = eval(parameter, candidateObjects);
					objParameters[i] = objParameter;
				}
				
				//performs the method invocation
				try {
					final Class<?> methodClass;
					if (objParameters.length > 0) {
						final Object param0 = objParameters[0];
						final Class<?> param0Class = param0.getClass();
						final ClassLoader param0ClassLoader = param0Class.getClassLoader();
						if (param0ClassLoader == null) {
							//it is a standard library class
							methodClass = Class.forName(javaClass(methodClassName, false));
						} else {
							methodClass = param0ClassLoader.loadClass(javaClass(methodClassName, false));
						}
					} else {
						methodClass = Class.forName(javaClass(methodClassName, false));
					}
					final Method m = method(methodClass, methodDescriptor, methodName);
					final boolean isMethodStatic = Modifier.isStatic(m.getModifiers());
					if (parametersList.size() != splitParametersDescriptors(methodDescriptor).length + (isMethodStatic ? 0 : 1)) {
						throw new RuntimeException("Internal error: parameters list (" + parameters + ") was split into " + parametersList.size() + " parameters, but descriptor " + methodDescriptor + " says that they should be " + splitParametersDescriptors(methodDescriptor).length + ".");
					}
					m.setAccessible(true);
					if (isMethodStatic) {
						obj = m.invoke(null, objParameters);
					} else if (objParameters[0] == null) {
						//instance method with a null 'this' parameter
						throw new FieldNotInCandidateException();
					} else {
						obj = m.invoke(objParameters[0], Arrays.copyOfRange(objParameters, 1, objParameters.length));
					}
				} catch (NoSuchMethodException | ClassNotFoundException | SecurityException e) {
					throw new SimilarityComputationException("Reflective exception while invoking method " + methodClassName + ":" + methodDescriptor + ":" + methodName + ": class not found, or method not found, or accessibility error. Exception: " + e.toString());
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					throw new SimilarityComputationException("Reflective exception while invoking method " + methodClassName + ":" + methodDescriptor + ":" + methodName + "; exception: " + e.toString());
				}
				final boolean hasArrayIndexAccess = !fields[0].substring(fields[0].lastIndexOf('>') + 1).isEmpty();
				if (hasArrayIndexAccess) {
					if (obj.getClass().isArray()) {
						obj = retrieveFromArray(obj, fields[0].substring(fields[0].lastIndexOf('>') + 1), candidateObjects);
					} else {
						throw new SimilarityComputationException("Tried an array index access but the object is not an array.");
					}
				}
			}
			
			//other fields
			for (int i = (startsFromStaticField ? 2 : 1); i < fields.length; ++i) {
				if (obj == null) {
					throw new FieldNotInCandidateException();
				}

				//checks if the origin path until now is still valid
				originPrefix += "." +  fields[i];
				if (this.invalidFieldPaths.contains(originPrefix)) {
					throw new FieldDependsOnInvalidFieldPathException(originPrefix);
				}

				if ("<identityHashCode>".equals(fields[i])) {
					try {
						obj = System.class.getMethod("identityHashCode", Object.class).invoke(obj);
					} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
							| NoSuchMethodException | SecurityException e) {
						throw new RuntimeException(e);
					}
				} else if ("length".equals(fields[i]) && obj.getClass().isArray()) {
					obj = retrieveFromArray(obj, fields[i], candidateObjects);
				} else {
					final boolean hasArrayIndexAccess = fields[i].contains("[");
					final String fieldName = (hasArrayIndexAccess ? fields[i].substring(0, fields[i].indexOf('[')) : fields[i]);
					final Field f = ReflectionUtils.getInheritedPrivateField(obj.getClass(), fieldName);
					if (f == null) {
						throw new SimilarityComputationException("Field name " + fieldName + " in origin " + origin + " does not exist in the corrsponding object.");
					}
					f.setAccessible(true);

					try {
						obj = f.get(obj);
					} catch (IllegalArgumentException | IllegalAccessException e) {
						throw new SimilarityComputationException("Unexpected error while retrieving the value of a field");
					}
					if (hasArrayIndexAccess) {
						if (obj.getClass().isArray()) {
							obj = retrieveFromArray(obj, fields[i].substring(fields[i].indexOf('[')), candidateObjects);
						} else {
							throw new SimilarityComputationException("Tried an array index access but the object is not an array.");
						}
					}
				}
			}
		} else {
			throw new SimilarityComputationException("Unrecognized origin " + origin + ".");
		}
		
		storeInBackbone(obj, origin);
		return obj;
	}

	private String[] splitFields(String origin) {
		List<String> fields = new ArrayList<>();

		int startField = 0;
		int i = 0;
		while (i < origin.length()) {
			if (fields.isEmpty() && i == 0) {
				//first one
				if (origin.charAt(0) == '{' || origin.charAt(0) == '[') {
					//it is a local variable or a static field
					while (i < origin.length() && origin.charAt(i) != '.') {
						++i;
					}
				} else if (origin.charAt(0) == '<') {
					//it is a function application
					int nestingLevel = 1;
					++i;
					while (i < origin.length() && nestingLevel != 0) {
						if (origin.charAt(i) == '<') {
							++nestingLevel;
						} else if (origin.charAt(i) == '>') {
							--nestingLevel;
						} //else, do nothing
						++i;
					}
					if (i < origin.length() - 1) {
						if (origin.charAt(i) != '.') {
							throw new SimilarityComputationException("Unrecognized origin " + origin + ".");
						}
					} else if (nestingLevel != 0) {
						throw new SimilarityComputationException("Unrecognized origin " + origin + ".");
					}
				} else {
					throw new SimilarityComputationException("Unrecognized origin " + origin + ".");
				}
			} else {
				//anyone after the first
				if (i < origin.length() - 1) {
					++i; //skips '.' if on it
				}
				int nestingLevel = 0;
				while (i < origin.length() && (nestingLevel != 0 || origin.charAt(i) != '.')) {
					if (origin.charAt(i) == '[') {
						++nestingLevel;
					} else if (origin.charAt(i) == ']') {
						--nestingLevel;
					} //else, do nothing
					++i;
				}
			}
			//invariant: i >= origin.length() || origin.charAt(i) == '.' 
			fields.add(origin.substring(startField, i));
			startField = i + 1;
		}
		
		return fields.toArray(new String[0]);
	}

	private Object retrieveFromArray(Object obj, String arrayAccessor, Map<String, Object> candidateObjects) 
	throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
		if (arrayAccessor.equals("length")) {
			return Array.getLength(obj);
		} else if (arrayAccessor.matches("\\[.*\\]")) {
			final String indexString = arrayAccessor.substring(1, arrayAccessor.length() - 1);
			final Object value = eval(indexString, candidateObjects);
			if (value instanceof Integer) {
			    try {
			    	final int index = ((Integer) value).intValue();
			    	return Array.get(obj, index);
			    } catch (ArrayIndexOutOfBoundsException e) {
			    	throw new FieldNotInCandidateException();
			    }
			} else {
				throw new SimilarityComputationException("Unexpected array access with noninteger index " + indexString + ".");			
			}
		} else {
			throw new SimilarityComputationException("Unexpected array accessor " +  arrayAccessor + " (neither index nor length).");					
		}
	}
	
    private static final String ADD = "+";
    private static final String SUB = "-";
    private static final String MUL = "*";
    private static final String DIV = "/";
    private static final String REM = "%";
    private static final String SHL = "<<";
    private static final String SHR = ">>";
    private static final String USHR = ">>>";
    private static final String ORBW = "|";
    private static final String ANDBW = "&";
    private static final String XORBW = "^";
    private static final String NEG = "~";

	
	private Object eval(String valueString, Map<String, Object> candidateObjects) 
	throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException, SimilarityComputationException {
		if (valueString == null) {
			throw new SimilarityComputationException("Trying to eval a null String.");
		}
		
		//Simplex
		if ("false".equals(valueString)) {
			return Boolean.FALSE;
		}
		if ("true".equals(valueString)) {
			return Boolean.TRUE;
		}
		try {
			final Integer retVal = Integer.parseInt(valueString);
			return retVal;
		} catch (NumberFormatException e) {
			//it is not an int, fall through
		}
		if (valueString.endsWith("L")) {
			try {
				final Long retVal = Long.parseLong(valueString.substring(0, valueString.length() - 1));
				return retVal;
			} catch (NumberFormatException e) {
				//it is not a long, fall through
			}
		}
		if (valueString.endsWith("f")) {
			try {
				final Float retVal = Float.parseFloat(valueString);
				return retVal;
			} catch (NumberFormatException e) {
				//it is not a float, fall through
			}
		}
		if (valueString.endsWith("d")) {
			try {
				final Double retVal = Double.parseDouble(valueString);
				return retVal;
			} catch (NumberFormatException e) {
				//it is not a double, fall through
			}
		}
		if (valueString.startsWith("(byte) ")) {
			try {
				final Byte retVal = Byte.parseByte(valueString.substring("(byte) ".length()));
				return retVal;
			} catch (NumberFormatException e) {
				throw new SimilarityComputationException("Ill-formed byte value " + valueString + ".");
			}
		}
		if (valueString.startsWith("(short) ")) {
			try {
				final Short retVal = Short.parseShort(valueString.substring("(short) ".length()));
				return retVal;
			} catch (NumberFormatException e) {
				throw new SimilarityComputationException("Ill-formed byte value " + valueString + ".");
			}
		}
		if (valueString.startsWith("'") && valueString.endsWith("'")) {
			if (valueString.length() == 3) {
				final Character retVal = Character.valueOf(valueString.charAt(1));
				return retVal;
			} else {
				throw new SimilarityComputationException("Ill-formed char value " + valueString + ".");
			}
		}
		
		//WideningConversion
		if (valueString.startsWith("WIDEN-")) {
			final char destinationType = valueString.charAt("WIDEN-".length());
			final String argString = valueString.substring("WIDEN-X(".length(), valueString.length() - 1);
			final Object arg = eval(argString, candidateObjects);
			if (arg instanceof Number) {
				switch (destinationType) {
				case DOUBLE:
					return Double.valueOf(((Number) arg).doubleValue());
				case FLOAT:
					return Float.valueOf(((Number) arg).floatValue());
				case INT:
					return Integer.valueOf(((Number) arg).intValue());
				case LONG:
					return Long.valueOf(((Number) arg).longValue());
				default:
					throw new SimilarityComputationException("Ill-formed widening value " + valueString + ".");
				}
			} else {
				throw new SimilarityComputationException("Ill-formed widening value " + valueString + ".");
			}
		}
		
		//NarrowingConversion
		if (valueString.startsWith("NARROW-")) {
			final char destinationType = valueString.charAt("NARROW-".length());
			final String argString = valueString.substring("NARROW-X(".length(), valueString.length() - 1);
			final Object arg = eval(argString, candidateObjects);
			if (arg instanceof Number) {
				switch (destinationType) {
				case FLOAT:
					return Float.valueOf(((Number) arg).floatValue());
				case INT:
					return Integer.valueOf(((Number) arg).intValue());
				case LONG:
					return Long.valueOf(((Number) arg).longValue());
				default:
					throw new SimilarityComputationException("Ill-formed narrowing value " + valueString + ".");
				}
			} else {
				throw new SimilarityComputationException("Ill-formed narrowing value " + valueString + ".");
			}
		}
		
		//Null
		if ("null".equals(valueString)) {
			return null;
		}
		
		//Any, DefaultValue, ReferenceArrayImmaterial, ReferenceConcrete
		if ("*".equals(valueString) || "<DEFAULT>".equals(valueString) || 
			valueString.startsWith("{R[") || valueString.startsWith("Object[")) {
			throw new SimilarityComputationException("Found Any, DefaultValue, ReferenceArrayImmaterial or ReferenceConcrete value: " + valueString + ".");
		}		
		
		//Expression
		int nestingLevel = 0;
		boolean isUnary = false;
		int beginArg1 = -1, endArg1 = -1, beginArg2 = -1, endArg2 = -1;
		int beginOperator = -1, endOperator = -1;
		for (int i = 0; i < valueString.length(); ++i) {
			final char currentChar = valueString.charAt(i);
			if (i == 0) {
				isUnary = (currentChar != '('); 
			}
			if (currentChar == '(') {
				if (nestingLevel == 0) {
					if (beginArg1 == -1) {
						beginArg1 = i;
					} else {
						beginArg2 = i;
					}
				}
				++nestingLevel;
			} else if (currentChar == ')') {
				if (nestingLevel == 0) {
					if (endArg1 == -1) {
						endArg1 = i;
					} else {
						endArg2 = i;
					}
				}
				--nestingLevel;
			} else if (nestingLevel == 0) {
				if (beginOperator == -1) {
					beginOperator = i;
				}
				endOperator = i + 1;
			}
		}
		
		if (beginArg1 != -1 && endArg1 != -1 && (isUnary || beginArg2 != -1) && (isUnary || endArg2 != -1) &&
		    beginArg1 < endArg1 && (isUnary || (beginArg2 == endArg1 + (endOperator - beginOperator) + 1 && beginArg2 < endArg2)) &&
		    (!isUnary || (beginOperator == 0 && endOperator == 1)) && (isUnary || (beginOperator < endOperator && endOperator - beginOperator <= 3)) &&
		    (!isUnary || NEG.equals(valueString.substring(beginOperator, endOperator))) &&
		    (isUnary || ADD.equals(valueString.substring(beginOperator, endOperator)) 
		    		 || SUB.equals(valueString.substring(beginOperator, endOperator))
		    		 || MUL.equals(valueString.substring(beginOperator, endOperator))
		    		 || DIV.equals(valueString.substring(beginOperator, endOperator))
		    		 || REM.equals(valueString.substring(beginOperator, endOperator))
		    		 || SHL.equals(valueString.substring(beginOperator, endOperator))
		    		 || SHR.equals(valueString.substring(beginOperator, endOperator))
		    		 || USHR.equals(valueString.substring(beginOperator, endOperator))
		    		 || ORBW.equals(valueString.substring(beginOperator, endOperator))
		    		 || ANDBW.equals(valueString.substring(beginOperator, endOperator))
		    		 || XORBW.equals(valueString.substring(beginOperator, endOperator)))) {
			//it is an expression
			final String operatorString = valueString.substring(beginOperator, endOperator);
			if (isUnary) {
				final String argString = valueString.substring(beginArg1 + 1, endArg1 - 1); //trim parentheses
				final Object arg = eval(argString, candidateObjects);
				if (NEG.equals(operatorString)) {
					if (arg instanceof Byte) {
						return Byte.valueOf((byte) - ((Byte) arg).byteValue());
					} else if (arg instanceof Double) {
						return Double.valueOf(- ((Double) arg).doubleValue());
					} else if (arg instanceof Float) {
						return Float.valueOf(- ((Float) arg).floatValue());
					} else if (arg instanceof Integer) {
						return Integer.valueOf(- ((Integer) arg).intValue());
					} else if (arg instanceof Long) {
						return Long.valueOf(- ((Long) arg).longValue());
					} else if (arg instanceof Short) {
						return Short.valueOf((short) - ((Short) arg).shortValue());
					} else {
						throw new SimilarityComputationException("Found an arithmetic negation whose operand has wrong type: " + valueString + ".");
					}
				} else {
					throw new RuntimeException("Internal error: unreachable case reached (possibly unforeseen or badly detected expected operator); operator: " + operatorString + ".");
				}
			} else {
				final String arg1String = valueString.substring(beginArg1 + 1, endArg1 - 1); //trim parentheses
				final String arg2String = valueString.substring(beginArg2 + 1, endArg2 - 1); //trim parentheses
				final Object arg1 = eval(arg1String, candidateObjects);
				final Object arg2 = eval(arg2String, candidateObjects);
				switch (operatorString) {
				case ADD:
					if (arg1 instanceof Byte && arg2 instanceof Byte) {
						return Byte.valueOf((byte) (((Byte) arg1).byteValue() + ((Byte) arg2).byteValue()));
					} else if (arg1 instanceof Double && arg2 instanceof Double) {
						return Double.valueOf(((Double) arg1).doubleValue() + ((Double) arg2).doubleValue());
					} else if (arg1 instanceof Float && arg2 instanceof Float) {
						return Float.valueOf(((Float) arg1).floatValue() + ((Float) arg2).floatValue());
					} else if (arg1 instanceof Integer && arg2 instanceof Integer) {
						return Integer.valueOf(((Integer) arg1).intValue() + ((Integer) arg2).intValue());
					} else if (arg1 instanceof Long && arg2 instanceof Long) {
						return Long.valueOf(((Long) arg1).longValue() + ((Long) arg2).longValue());
					} else if (arg1 instanceof Short && arg2 instanceof Short) {
						return Short.valueOf((short) (((Short) arg1).shortValue() + ((Short) arg2).shortValue()));
					} else {
						throw new SimilarityComputationException("Found a sum whose operands have different types: " + valueString + ".");
					}
				case SUB:
					if (arg1 instanceof Byte && arg2 instanceof Byte) {
						return Byte.valueOf((byte) (((Byte) arg1).byteValue() - ((Byte) arg2).byteValue()));
					} else if (arg1 instanceof Double && arg2 instanceof Double) {
						return Double.valueOf(((Double) arg1).doubleValue() - ((Double) arg2).doubleValue());
					} else if (arg1 instanceof Float && arg2 instanceof Float) {
						return Float.valueOf(((Float) arg1).floatValue() - ((Float) arg2).floatValue());
					} else if (arg1 instanceof Integer && arg2 instanceof Integer) {
						return Integer.valueOf(((Integer) arg1).intValue() - ((Integer) arg2).intValue());
					} else if (arg1 instanceof Long && arg2 instanceof Long) {
						return Long.valueOf(((Long) arg1).longValue() - ((Long) arg2).longValue());
					} else if (arg1 instanceof Short && arg2 instanceof Short) {
						return Short.valueOf((short) (((Short) arg1).shortValue() - ((Short) arg2).shortValue()));
					} else {
						throw new SimilarityComputationException("Found a subtraction whose operands have different types: " + valueString + ".");
					}
				case MUL:
					if (arg1 instanceof Byte && arg2 instanceof Byte) {
						return Byte.valueOf((byte) (((Byte) arg1).byteValue() * ((Byte) arg2).byteValue()));
					} else if (arg1 instanceof Double && arg2 instanceof Double) {
						return Double.valueOf(((Double) arg1).doubleValue() * ((Double) arg2).doubleValue());
					} else if (arg1 instanceof Float && arg2 instanceof Float) {
						return Float.valueOf(((Float) arg1).floatValue() * ((Float) arg2).floatValue());
					} else if (arg1 instanceof Integer && arg2 instanceof Integer) {
						return Integer.valueOf(((Integer) arg1).intValue() * ((Integer) arg2).intValue());
					} else if (arg1 instanceof Long && arg2 instanceof Long) {
						return Long.valueOf(((Long) arg1).longValue() * ((Long) arg2).longValue());
					} else if (arg1 instanceof Short && arg2 instanceof Short) {
						return Short.valueOf((short) (((Short) arg1).shortValue() * ((Short) arg2).shortValue()));
					} else {
						throw new SimilarityComputationException("Found a multiplication whose operands have different types: " + valueString + ".");
					}
				case DIV:
					if (arg1 instanceof Byte && arg2 instanceof Byte) {
						return Byte.valueOf((byte) (((Byte) arg1).byteValue() / ((Byte) arg2).byteValue()));
					} else if (arg1 instanceof Double && arg2 instanceof Double) {
						return Double.valueOf(((Double) arg1).doubleValue() * ((Double) arg2).doubleValue());
					} else if (arg1 instanceof Float && arg2 instanceof Float) {
						return Float.valueOf(((Float) arg1).floatValue() * ((Float) arg2).floatValue());
					} else if (arg1 instanceof Integer && arg2 instanceof Integer) {
						return Integer.valueOf(((Integer) arg1).intValue() * ((Integer) arg2).intValue());
					} else if (arg1 instanceof Long && arg2 instanceof Long) {
						return Long.valueOf(((Long) arg1).longValue() * ((Long) arg2).longValue());
					} else if (arg1 instanceof Short && arg2 instanceof Short) {
						return Short.valueOf((short) (((Short) arg1).shortValue() * ((Short) arg2).shortValue()));
					} else {
						throw new SimilarityComputationException("Found a division whose operands have different types: " + valueString + ".");
					}
				case REM:
					if (arg1 instanceof Byte && arg2 instanceof Byte) {
						return Byte.valueOf((byte) (((Byte) arg1).byteValue() % ((Byte) arg2).byteValue()));
					} else if (arg1 instanceof Double && arg2 instanceof Double) {
						return Double.valueOf(((Double) arg1).doubleValue() % ((Double) arg2).doubleValue());
					} else if (arg1 instanceof Float && arg2 instanceof Float) {
						return Float.valueOf(((Float) arg1).floatValue() % ((Float) arg2).floatValue());
					} else if (arg1 instanceof Integer && arg2 instanceof Integer) {
						return Integer.valueOf(((Integer) arg1).intValue() % ((Integer) arg2).intValue());
					} else if (arg1 instanceof Long && arg2 instanceof Long) {
						return Long.valueOf(((Long) arg1).longValue() % ((Long) arg2).longValue());
					} else if (arg1 instanceof Short && arg2 instanceof Short) {
						return Short.valueOf((short) (((Short) arg1).shortValue() % ((Short) arg2).shortValue()));
					} else {
						throw new SimilarityComputationException("Found a remainder whose operands have different types: " + valueString + ".");
					}
				case SHL:
					if (arg1 instanceof Integer && arg2 instanceof Integer) {
						return Integer.valueOf(((Integer) arg1).intValue() << ((Integer) arg2).intValue());
					} else if (arg1 instanceof Long && arg2 instanceof Integer) {
						return Long.valueOf(((Long) arg1).longValue() << ((Integer) arg2).intValue());
					} else {
						throw new SimilarityComputationException("Found a left shift whose operands have wrong types: " + valueString + ".");
					}
				case SHR:
					if (arg1 instanceof Integer && arg2 instanceof Integer) {
						return Integer.valueOf(((Integer) arg1).intValue() >> ((Integer) arg2).intValue());
					} else if (arg1 instanceof Long && arg2 instanceof Integer) {
						return Long.valueOf(((Long) arg1).longValue() >> ((Integer) arg2).intValue());
					} else {
						throw new SimilarityComputationException("Found an arithmetic right shift whose operands have wrong types: " + valueString + ".");
					}
				case USHR:
					if (arg1 instanceof Integer && arg2 instanceof Integer) {
						return Integer.valueOf(((Integer) arg1).intValue() >>> ((Integer) arg2).intValue());
					} else if (arg1 instanceof Long && arg2 instanceof Integer) {
						return Long.valueOf(((Long) arg1).longValue() >>> ((Integer) arg2).intValue());
					} else {
						throw new SimilarityComputationException("Found a logical right shift whose operands have wrong types: " + valueString + ".");
					}
				case ORBW:
					if (arg1 instanceof Integer && arg2 instanceof Integer) {
						return Integer.valueOf(((Integer) arg1).intValue() | ((Integer) arg2).intValue());
					} else if (arg1 instanceof Long && arg2 instanceof Long) {
						return Long.valueOf(((Long) arg1).longValue() | ((Long) arg2).longValue());
					} else {
						throw new SimilarityComputationException("Found a bitwise or whose operands have wrong types: " + valueString + ".");
					}
				case ANDBW:
					if (arg1 instanceof Integer && arg2 instanceof Integer) {
						return Integer.valueOf(((Integer) arg1).intValue() & ((Integer) arg2).intValue());
					} else if (arg1 instanceof Long && arg2 instanceof Long) {
						return Long.valueOf(((Long) arg1).longValue() & ((Long) arg2).longValue());
					} else {
						throw new SimilarityComputationException("Found a bitwise and whose operands have wrong types: " + valueString + ".");
					}
				case XORBW:
					if (arg1 instanceof Integer && arg2 instanceof Integer) {
						return Integer.valueOf(((Integer) arg1).intValue() ^ ((Integer) arg2).intValue());
					} else if (arg1 instanceof Long && arg2 instanceof Long) {
						return Long.valueOf(((Long) arg1).longValue() ^ ((Long) arg2).longValue());
					} else {
						throw new SimilarityComputationException("Found a bitwise xor whose operands have wrong types: " + valueString + ".");
					}
				default:
					throw new RuntimeException("Internal error: unreachable case reached (possibly unforeseen or badly detected expected operator); operator: " + operatorString + ".");
				}
			}
		} //else, fall through
		
		//PrimitiveSymbolicAtomic, PrimitiveSymbolicApply, ReferenceSymbolic: retrieve,
		return retrieveOrVisitField(valueString, candidateObjects);
	}
}
