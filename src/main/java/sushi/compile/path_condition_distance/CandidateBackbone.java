package sushi.compile.path_condition_distance;

import static sushi.util.ReflectionUtils.method;
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
			if (startsFromLocalVariable) {
				if (candidateObjects.containsKey(originPrefix)) {
					obj = candidateObjects.get(originPrefix);
				} else {
					throw new SimilarityComputationException("Local variable (parameter) origin " + originPrefix + " not found in candidateObjects.");
				}
			} else if (startsFromStaticField) {
				try {
					final String className = originPrefix.substring(1, originPrefix.length() - 1).replace('/', '.');
					final Field f = Class.forName(className).getDeclaredField(fields[1]);
					f.setAccessible(true);
					obj = f.get(null);
				} catch (ClassNotFoundException | NoSuchFieldException e) {
					throw new SimilarityComputationException("Static field origin " + originPrefix + "." + fields[1] + " does not exist.");
				} catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
					throw new SimilarityComputationException("Unexpected error while retrieving the value of a static field: " + originPrefix + "." + fields[1]);
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
				final String className = fields[0].substring(1, firstSemicolonIndex); //remove leading '<'
				final String descriptor = fields[0].substring(firstSemicolonIndex + 1, secondSemicolonIndex);
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
					final Object objParameter = retrieveOrVisitField(parameter, candidateObjects);
					objParameters[i] = objParameter;
				}
				
				//performs the method invocation
				try {
					final Method m = method(className, descriptor, methodName);
					final boolean isMethodStatic = Modifier.isStatic(m.getModifiers());
					if (parametersList.size() != splitParametersDescriptors(descriptor).length + (isMethodStatic ? 0 : 1)) {
						throw new RuntimeException("Internal error: parameters list (" + parameters + ") was split into " + parametersList.size() + " parameters, but descriptor " + descriptor + " says that they should be " + splitParametersDescriptors(descriptor).length + ".");
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
					throw new SimilarityComputationException("Reflective exception while invoking method " + className + ":" + descriptor + ":" + methodName + ": class not found, or method not found, or accessibility error. Exception: " + e.toString());
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					throw new SimilarityComputationException("Reflective exception while invoking method " + className + ":" + descriptor + ":" + methodName + "; exception: " + e.toString());
				}
			}
			for (int i = (startsFromStaticField ? 2 : 1); i < fields.length; ++i) {
				if (obj == null) {
					throw new FieldNotInCandidateException();
				}

				if (this.invalidFieldPaths.contains(originPrefix)) {
					throw new FieldDependsOnInvalidFieldPathException(originPrefix);
				}
				originPrefix += "." +  fields[i];

				if ("<identityHashCode>".equals(fields[i])) {
					try {
						obj = System.class.getMethod("identityHashCode", Object.class).invoke(obj);
					} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
							| NoSuchMethodException | SecurityException e) {
						throw new RuntimeException(e);
					}
				} else if (obj.getClass().isArray()) {
					obj = retrieveFromArray(obj, fields[i], candidateObjects);
				} else {
					Object hack = hack4StringJava6(obj, fields[i]); //GIO: TODO
					if (hack != null) {
						obj = hack;
						continue;
					}

					Field f = ReflectionUtils.getInheritedPrivateField(obj.getClass(), fields[i]);

					if (f == null) {
						throw new SimilarityComputationException("Field name " + fields[i] + " in origin " + origin + " does not exist in the corrsponding object");
					}
					f.setAccessible(true);

					try {
						obj = f.get(obj);
					} catch (IllegalArgumentException | IllegalAccessException e) {
						throw new SimilarityComputationException("Unexpected error while retrieving the value of a field");
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
						++i;
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

	private Object retrieveFromArray(Object obj, String fieldSpec, Map<String, Object> candidateObjects) 
			throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
		if (fieldSpec.equals("length")) {
			return Array.getLength(obj);
		}
		else if (fieldSpec.matches("\\[.*\\]")) {
			String indexString = fieldSpec.substring(1, fieldSpec.length() - 1);
			
			int index = 0;

			while (indexString.indexOf('{') >= 0) {
				int startOrigin = indexString.indexOf('{');
				
				int endOrigin = indexString.indexOf(')', startOrigin);
				if (endOrigin < 0) {
					endOrigin = indexString.length();
				}
				
				String origin = indexString.substring(startOrigin, endOrigin);

				Object o = retrieveOrVisitField(origin, candidateObjects);
				if (o instanceof Integer) {
					index += (Integer) o;
					indexString = indexString.substring(0, startOrigin) +  /*(Integer) o +*/ indexString.substring(endOrigin);
				} else {
					throw new SimilarityComputationException("Unexpected type (" + o.getClass() +") while retrieving the value of an array index: " + origin + "." + fieldSpec);
				}

			}
			
			//TODO: Fix to support evaluation of arbitrary expressions
			int last = 0;
			while (indexString.indexOf('1', last) >= 0) {
				index += 1;
				last = indexString.indexOf('1', last) + 1;
			}

			/*ScriptEngineManager mgr = new ScriptEngineManager();
		    ScriptEngine engine = mgr.getEngineByName("JavaScript");
		    Object ev;
		    try {
		    	ev = engine.eval(indexString);
			} catch (ScriptException e) {
				throw new SimilarityComputationException("Cannot evaluate an array index out of expression " + indexString + " for " + fieldSpec);
			}
		    if (ev instanceof Integer) {
				index = (Integer) ev;
			} else {
				throw new SimilarityComputationException("Unexpected type (" + ev.getClass() +") while retrieving the value of an array index: " + indexString + "." + fieldSpec);
			}*/
		    
		    try {
		    	return Array.get(obj, index);
		    } catch (ArrayIndexOutOfBoundsException e) {
		    	throw new FieldNotInCandidateException();
		    }
		    
		} else {
			throw new SimilarityComputationException("Unexpected field or indexSpec in array object: " +  fieldSpec);					
		}
	}

	private Object hack4StringJava6(Object obj, String fname) {
		if (obj instanceof String) {
			if ("offset".equals(fname)) {
				return 0;
			}
			else if ("count".equals(fname)) {
				try {
					return String.class.getMethod("length", (Class<?>[]) null).invoke(obj, (Object[]) null);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
						| NoSuchMethodException | SecurityException e) {
					throw new RuntimeException(e);
				}
			}
		}
		return null;
	}
}
