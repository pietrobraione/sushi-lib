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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sushi.util.ReflectionUtils;

public class ParsedOrigin {
    private final String origin;
    private final String[] fields;
    private final Set<String> dependedOrigins = new HashSet<>();
    private final OriginAccessor[] originAccessSpecifier;
    private int nextUnparsed = 0;

    public ParsedOrigin(String origin) {
        assert (origin != null && !origin.isEmpty()); 
        this.origin = origin;
        this.fields = splitFields(origin); 
        this.originAccessSpecifier = new OriginAccessor[this.fields.length];

        String dependedOrigin = this.fields[0];
        this.dependedOrigins.add(dependedOrigin);
        for (int i = 1; i < this.fields.length; ++i) {
            dependedOrigin += "." + this.fields[i];
            this.dependedOrigins.add(dependedOrigin);			
        }
    }

    private String[] splitFields(String origin) {
        final List<String> fields = new ArrayList<>();

        int startField = 0;
        int i = 0;
        while (i < origin.length()) {
            if (fields.isEmpty() && i == 0) {
                //first one
                if (origin.charAt(0) == '{' || origin.charAt(0) == '[') {
                    //it is a local variable or a static field
                    ++i; //skips first '[', if on it
                    while (i < origin.length() && origin.charAt(i) != '.' /*GIO*/ && origin.charAt(i) != '[') { //TODO why '['???
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
            } else if (origin.charAt(i) == '[') {
                ++i; //skips first '['
                int nestingLevel = 1;
                while (i < origin.length() && nestingLevel != 0) {
                    if (origin.charAt(i) == '[') {
                        ++nestingLevel;
                    } else if (origin.charAt(i) == ']') {
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
                //anyone after the first
                if (i < origin.length() - 1) {
                    ++i; //skips '.' if on it
                }
                while (i < origin.length() && origin.charAt(i) != '.'  /*GIO*/ && origin.charAt(i) != '[') { //TODO why '['???
                    ++i;
                }
            }
            //invariant: i >= origin.length() || origin.charAt(i) == '.' 
            fields.add(origin.substring(startField, i));
            startField = (i < origin.length() && origin.charAt(i) == '.') ? i + 1 : i;
        }

        return fields.toArray(new String[0]);
    }

    public Object get(Map<String, Object> candidateObjects, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) 
    throws FieldDependsOnInvalidFieldPathException, FieldNotInCandidateException {
        //1. Check if any dependedOrigin is invalid, throw exception to abort
        final Set<String> smallerSet;
        final Set<String> biggerSet;
        if (candidateBackbone.getInvalidFieldPaths().size() <= this.dependedOrigins.size()) {
            smallerSet = candidateBackbone.getInvalidFieldPaths();
            biggerSet = this.dependedOrigins;
        } else {
            smallerSet = this.dependedOrigins;
            biggerSet = candidateBackbone.getInvalidFieldPaths();
        }
        for (String s : smallerSet) {
            if (biggerSet.contains(s)) {
                throw new FieldDependsOnInvalidFieldPathException(s);
            }
        }

        /* You may want to activate this code to profile the execution:
		if (nextUnparsed <= 0) {
			SushiLibCache._I().misses++;
		} else if (nextUnparsed >= fields.length) {
			SushiLibCache._I().hits++;			
		} else {
			SushiLibCache._I().partialHits++;
		}*/

        //2. retrieve the object for the already parsed fields
        Object obj = null;
        for (int i = 0; i < this.originAccessSpecifier.length; ++i) {
            final OriginAccessor accessor = this.originAccessSpecifier[i]; 
            if (accessor == null) { 
                if (i == 1) {
                    continue; //check also next accessor: the 2nd accessor is empty for origins that start from static fields
                } else {
                    break;
                }
            }
            obj = accessor.getActualObject(candidateObjects, obj, candidateBackbone, constants, cache);
        }

        //3. complete parsing, if not yet done or done only partially
        while (this.nextUnparsed < this.fields.length) {
            if (this.nextUnparsed == 0) {
                final boolean startsFromRootVariable = this.fields[0].startsWith("{");
                final boolean startsFromStaticField = this.fields[0].startsWith("[");
                final boolean startsFromMethodInvocation = this.fields[0].startsWith("<");
                if (startsFromStaticField) {
                    obj = parseAccessorStaticField();
                    this.nextUnparsed = 2;		
                } else if (startsFromRootVariable) {
                    obj = parseAccessorRootObject(candidateObjects);
                    this.nextUnparsed = 1;		
                } else if (startsFromMethodInvocation) {
                    obj = parseAccessorMethodInvocation(candidateObjects, candidateBackbone, constants, cache);
                    this.nextUnparsed = 1;							
                } else {
                    throw new SimilarityComputationException("Unrecognized origin " + origin + ".");
                }
            } else {
                if (obj == null) {
                    throw new FieldNotInCandidateException();
                } else {
                    if ("<identityHashCode>".equals(this.fields[this.nextUnparsed])) {
                        obj = parseAccessorIdentityHashCode(obj);
                    } else if (obj.getClass().isArray()) {
                        obj = parseAccessorArrayLocation(obj, candidateObjects, candidateBackbone, constants, cache);  
                    } else {
                        obj = parseAccessorField(obj);
                    }
                    ++this.nextUnparsed;
                }
            }
        }

        return obj;
    }

    private Object parseAccessorStaticField() {	
        final String className = javaClass(this.fields[0].substring(1, this.fields[0].length() - 1), false);
        final String fieldName = this.fields[1].substring(this.fields[1].indexOf(':') + 1);
        try {
            final Field f = Class.forName(className).getDeclaredField(fieldName);
            if (f == null) {
                throw new SimilarityComputationException("Static field with name " + fieldName + " does not exist in class " + className + "; origin " + this.origin + ".");
            }
            final OriginAccessorStaticField accessor = new OriginAccessorStaticField(f);
            final Object ret = accessor.getActualObject();
            this.originAccessSpecifier[0] = accessor;
            return ret;
        } catch (NoSuchFieldException | SecurityException | ClassNotFoundException e) {
            throw new SimilarityComputationException("Reflective exception while accessing static field " + className + "." + fieldName + ". Exception: " + e );
        }

    }

    private Object parseAccessorRootObject(Map<String, Object> candidateObjects) {
        final String root = this.fields[0];
        if (candidateObjects.containsKey(root)) {
            final OriginAccessorRootObject accessor = new OriginAccessorRootObject(root);
            final Object ret = accessor.getActualObject(candidateObjects);
            this.originAccessSpecifier[0] = accessor;
            return ret;
        } else {
            throw new SimilarityComputationException("Local variable (parameter) origin " + root + " not found in candidateObjects.");
        }
    }

    private Object parseAccessorMethodInvocation(Map<String, Object> candidateObjects, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) 
    throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
        //gets the position of the first semicolon
        final int firstSemicolonIndex = this.fields[0].indexOf(':');
        if (firstSemicolonIndex == -1) {
            throw new SimilarityComputationException("Unrecognized origin " + origin + " (first semicolon missing).");
        }

        //gets the position of the second semicolon
        if (this.fields[0].substring(firstSemicolonIndex + 1).indexOf(':') == -1) {
            throw new SimilarityComputationException("Unrecognized origin " + origin + " (second semicolon missing).");
        }
        final int secondSemicolonIndex = this.fields[0].substring(firstSemicolonIndex + 1).indexOf(':') + firstSemicolonIndex + 1;

        //gets the position of the first @ character
        if (this.fields[0].substring(secondSemicolonIndex).indexOf('@') == -1) {
            throw new SimilarityComputationException("Unrecognized origin " + origin + " (first at-sign missing).");
        }
        final int firstAtSignIndex = this.fields[0].substring(secondSemicolonIndex).indexOf('@') + secondSemicolonIndex;

        //gets the position of the end of the parameter list
        final int endOfParameterList;
        if (this.fields[0].substring(secondSemicolonIndex).lastIndexOf('@') == -1) {
            endOfParameterList = this.fields[0].length() - 1;
        } else {
            endOfParameterList = this.fields[0].substring(secondSemicolonIndex).lastIndexOf('@') + secondSemicolonIndex;
        }
        if (firstAtSignIndex >= endOfParameterList) {
            throw new SimilarityComputationException("Unrecognized origin " + origin + " (the origin does not appear to be well-terminated).");
        }
        final String methodClassName = this.fields[0].substring(1, firstSemicolonIndex);
        final String methodDescriptor = this.fields[0].substring(firstSemicolonIndex + 1, secondSemicolonIndex);
        final String methodName = this.fields[0].substring(secondSemicolonIndex + 1, firstAtSignIndex);
        final String parameters = this.fields[0].substring(firstAtSignIndex + 1, endOfParameterList);

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
            } else if (parameters.charAt(i) == '<' || parameters.charAt(i) == '[') {
                ++nestingLevel;
            } else if (parameters.charAt(i) == '>' || parameters.charAt(i) == ']') {
                --nestingLevel;
            } //else nothing
        }
        parametersList.add(parameters.substring(beginParameter).trim()); //last parameter

        //performs the method invocation
        try {
            final Class<?> methodClass = candidateBackbone.getClassLoader().loadClass(javaClass(methodClassName, false));
            final Method m = method(methodClass, methodDescriptor, methodName);
            final boolean isMethodStatic = Modifier.isStatic(m.getModifiers());
            if (parametersList.size() != splitParametersDescriptors(methodDescriptor).length + (isMethodStatic ? 0 : 1)) {
                throw new RuntimeException("Internal error: parameters list (" + parameters + ") was split into " + parametersList.size() + " parameters, but descriptor " + methodDescriptor + " says that there should be " + splitParametersDescriptors(methodDescriptor).length + " parameters instead.");
            }

            final OriginAccessorMethodInvocation accessor = new OriginAccessorMethodInvocation(m, isMethodStatic, parametersList);
            final Object ret = accessor.getActualObject(candidateObjects, candidateBackbone, constants, cache);
            this.originAccessSpecifier[0] = accessor;
            return ret;
        } catch (NoSuchMethodException | ClassNotFoundException | SecurityException e) {
            throw new SimilarityComputationException("Reflective exception while invoking method " + methodClassName + ":" + methodDescriptor + ":" + methodName + ". Exception: " + e.toString());
        } 		
    }

    private Object parseAccessorIdentityHashCode(Object obj) throws FieldNotInCandidateException {
        final OriginAccessorIdentityHashCode accessor = new OriginAccessorIdentityHashCode();
        final Object ret = accessor.getActualObject(obj); 
        this.originAccessSpecifier[this.nextUnparsed] = accessor;
        return ret;
    }

    private Object parseAccessorArrayLocation(Object obj, Map<String, Object> candidateObjects, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) 
    throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
        final String arrayAccessor = this.fields[this.nextUnparsed];
        if (arrayAccessor.equals("length")) {
            final OriginAccessorArrayLength accessor = new OriginAccessorArrayLength();
            final Object ret = accessor.getActualObject(obj);
            this.originAccessSpecifier[this.nextUnparsed] = accessor;
            return ret;
        } else if (arrayAccessor.matches("\\[.*\\]")) {
            final String indexString = arrayAccessor.substring(1, arrayAccessor.length() - 1);
            try {
                int index = Integer.parseInt(indexString);
                final OriginAccessorArrayLocationResolvedIndex accessor = new OriginAccessorArrayLocationResolvedIndex(index);
                final Object ret = accessor.getActualObject(obj); 
                this.originAccessSpecifier[this.nextUnparsed] = accessor;
                return ret;
            } catch (NumberFormatException e) {
                final OriginAccessorArrayLocationUnresolvedIndex accessor = new OriginAccessorArrayLocationUnresolvedIndex(indexString);
                final Object ret = accessor.getActualObject(candidateObjects, obj, candidateBackbone, constants, cache); 
                this.originAccessSpecifier[this.nextUnparsed] = accessor;
                return ret;				
            }
        } else {
            throw new SimilarityComputationException("Unexpected array accessor " +  arrayAccessor + " (neither index nor length).");					
        }
    }

    private Object parseAccessorField(Object obj) throws FieldNotInCandidateException {
        final String fieldAndClassName = this.fields[this.nextUnparsed];
        final String[] fieldAndClassNameSplit = fieldAndClassName.split(":");
        final String fieldName = fieldAndClassNameSplit[1];
        final String className = fieldAndClassNameSplit[0];
        final Field f = ReflectionUtils.getInheritedPrivateField(obj.getClass(), fieldName, className);
        if (f == null) {
            throw new FieldNotInCandidateException(); // This can happen if the origin refers to a field of a sub-type
        }
        final OriginAccessorField accessor = new OriginAccessorField(f);
        final Object ret = accessor.getActualObject(obj);
        this.originAccessSpecifier[this.nextUnparsed] = accessor;
        return ret;
    }

    private abstract class OriginAccessor {
        abstract Object getActualObject(Map<String, Object> candidateObjects, Object obj, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) 
        throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException;
    }

    private class OriginAccessorStaticField extends OriginAccessor {
        private final Field field;

        OriginAccessorStaticField(Field field) {
            this.field = field;
        }

        @Override
        Object getActualObject(Map<String, Object> candidateObjects, Object obj, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) {
            return getActualObject();
        }

        Object getActualObject() {
            this.field.setAccessible(true);
            try {
                return this.field.get(null);
            } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
                throw new SimilarityComputationException("Unexpected error while retrieving the value of a static field: " + this.field);
            }
        }
    }

    private class OriginAccessorRootObject extends OriginAccessor {
        private final String rootObjIdentifier;

        OriginAccessorRootObject(String rootObjIdentifier) {
            this.rootObjIdentifier = rootObjIdentifier;
        }

        @Override
        Object getActualObject(Map<String, Object> candidateObjects, Object obj, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) {
            return getActualObject(candidateObjects);
        }

        Object getActualObject(Map<String, Object> candidateObjects) {
            return candidateObjects.get(this.rootObjIdentifier);
        }
    }

    private class OriginAccessorMethodInvocation extends OriginAccessor {
        private final Method method;
        private final boolean isMethodStatic;
        private final List<String> parametersList;

        OriginAccessorMethodInvocation(Method method, boolean isMethodStatic, List<String> parametersList) {
            this.method = method;
            this.isMethodStatic = isMethodStatic;
            this.parametersList = parametersList;
        }

        @Override
        Object getActualObject(Map<String, Object> candidateObjects, Object obj, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache)
        throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
            return getActualObject(candidateObjects, candidateBackbone, constants, cache);
        }

        Object getActualObject(Map<String, Object> candidateObjects, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
            //gets the parameters in the list
            final Object[] objParameters = new Object[this.parametersList.size()];
            for (int i = 0; i < this.parametersList.size(); ++i) {
                final String parameter = this.parametersList.get(i);
                final Object objParameter = eval(parameter, candidateObjects, candidateBackbone, constants, cache);
                objParameters[i] = objParameter;
            }

            try {
                this.method.setAccessible(true);
                if (this.isMethodStatic) {
                    return this.method.invoke(null, objParameters);
                } else if (objParameters[0] == null) {
                    //instance method with a null 'this' parameter
                    throw new FieldNotInCandidateException();
                } else {
                    return this.method.invoke(objParameters[0], Arrays.copyOfRange(objParameters, 1, objParameters.length));
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new SimilarityComputationException("Reflective exception while invoking method " + method + "; exception: " + e.toString());
            }
        }
    }

    private class OriginAccessorIdentityHashCode extends OriginAccessor {
        private final Method identityHashCodeMethod;

        OriginAccessorIdentityHashCode() {
            try {
                this.identityHashCodeMethod = System.class.getMethod("identityHashCode", Object.class);
            } catch (NoSuchMethodException | SecurityException e) {
                throw new SimilarityComputationException("Reflective exception while seacrhing for method identityHashCode; exception: " + e.toString());
            }
        }

        @Override
        Object getActualObject(Map<String, Object> candidateObjects, Object obj, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) 
        throws FieldNotInCandidateException {
            return getActualObject(obj);
        }

        Object getActualObject(Object obj) throws FieldNotInCandidateException {
            if (obj == null) {
                throw new FieldNotInCandidateException();
            }	
            try {
                obj = this.identityHashCodeMethod.invoke(obj);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new SimilarityComputationException("Reflective exception while invoking method " + this.identityHashCodeMethod + "; exception: " + e.toString());
            }
            return obj;
        }		
    }

    private class OriginAccessorField extends OriginAccessor {
        private final Field field;

        OriginAccessorField(Field field) {
            this.field = field;
        }

        @Override
        Object getActualObject(Map<String, Object> candidateObjects, Object obj, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) 
        throws FieldNotInCandidateException {
            return getActualObject(obj);
        }

        private Object getActualObject(Object obj) throws FieldNotInCandidateException {
            if (obj == null) {
                throw new FieldNotInCandidateException();
            }	
            try {
                this.field.setAccessible(true);
                return this.field.get(obj);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new FieldNotInCandidateException();
                //throw new SimilarityComputationException("Unexpected error while retrieving the value of member field: " + field + ", from object of class " + obj.getClass());
            }
        }
    }

    private class OriginAccessorArrayLength extends OriginAccessor {
        OriginAccessorArrayLength() { }

        @Override
        Object getActualObject(Map<String, Object> candidateObjects, Object obj, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) 
        throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
            if (obj == null) {
                throw new FieldNotInCandidateException();
            }	
            return getActualObject(obj);
        }	

        Object getActualObject(Object obj) 
        throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
            return Array.getLength(obj);
        }
    }

    private class OriginAccessorArrayLocationResolvedIndex extends OriginAccessor {
        private final int index;	

        OriginAccessorArrayLocationResolvedIndex(int index) {
            this.index = index;					
        }

        @Override
        Object getActualObject(Map<String, Object> candidateObjects, Object obj, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
            if (obj == null) {
                throw new FieldNotInCandidateException();
            }	
            return getActualObject(obj);
        }	

        Object getActualObject(Object obj) 
        throws FieldNotInCandidateException {
            try {
                return Array.get(obj, this.index);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new FieldNotInCandidateException();
            }
        }
    }

    private class OriginAccessorArrayLocationUnresolvedIndex extends OriginAccessor {
        private final String indexString;	

        OriginAccessorArrayLocationUnresolvedIndex(String indexString) {
            this.indexString = indexString;					
        }

        @Override
        Object getActualObject(Map<String, Object> candidateObjects, Object obj, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
            if (obj == null) {
                throw new FieldNotInCandidateException();
            }	
            return retrieveFromArray(obj, candidateBackbone, candidateObjects, constants, cache);
        }	

        private Object retrieveFromArray(Object obj, CandidateBackbone candidateBackbone, Map<String, Object> candidateObjects, Map<Long, String> constants, SushiLibCache cache) 
        throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
            final Object value = eval(this.indexString, candidateObjects, candidateBackbone, constants, cache);
            if (value instanceof Integer) {
                try {
                    final int index = ((Integer) value).intValue();
                    return Array.get(obj, index);
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw new FieldNotInCandidateException();
                }
            } else {
                throw new SimilarityComputationException("Unexpected array access with noninteger index " + this.indexString + ".");			
            }
        }
    }

    private static final String ADD   = "+";
    private static final String SUB   = "-";
    private static final String MUL   = "*";
    private static final String DIV   = "/";
    private static final String REM   = "%";
    private static final String SHL   = "<<";
    private static final String SHR   = ">>";
    private static final String USHR  = ">>>";
    private static final String ORBW  = "|";
    private static final String ANDBW = "&";
    private static final String XORBW = "^";
    private static final String NEG   = "~";

    private Object eval(String valueString, Map<String, Object> candidateObjects, CandidateBackbone candidateBackbone, Map<Long, String> constants, SushiLibCache cache) 
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
            final Object arg = eval(argString, candidateObjects, candidateBackbone, constants, cache);
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
            final Object arg = eval(argString, candidateObjects, candidateBackbone, constants, cache);
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

        //Any, DefaultValue, ReferenceArrayImmaterial
        if ("*".equals(valueString) || "<DEFAULT>".equals(valueString) || valueString.startsWith("{R[")) {
            throw new SimilarityComputationException("Found Any, DefaultValue, or ReferenceArrayImmaterial value: " + valueString + ".");
        }

        //ReferenceConcrete
        if (valueString.startsWith("Object[")) {
            //TODO support concrete references to constant objects other than Strings
            try {
                final Long heapPos = Long.parseLong(valueString.substring(valueString.indexOf('[') + 1, valueString.length() - 1));
                if (constants.containsKey(heapPos)) {
                    return constants.get(heapPos);
                } else {
                    throw new SimilarityComputationException("Found ReferenceConcrete value: " + valueString + ", not corresponding to any literal.");
                }
            } catch (NumberFormatException e) {
                throw new SimilarityComputationException("Unexpected invalid concrete object value: " + valueString + ".");
            }
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
                if (nestingLevel == 1) {
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
                final Object arg = eval(argString, candidateObjects, candidateBackbone, constants, cache);
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
                final String arg1String = valueString.substring(beginArg1 + 1, endArg1); //trim parentheses
                final String arg2String = valueString.substring(beginArg2 + 1, endArg2); //trim parentheses
                final Object arg1 = eval(arg1String, candidateObjects, candidateBackbone, constants, cache);
                final Object arg2 = eval(arg2String, candidateObjects, candidateBackbone, constants, cache);
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

        //PrimitiveSymbolicAtomic, PrimitiveSymbolicApply, ReferenceSymbolic: retrieve
        return candidateBackbone.retrieveOrVisitField(valueString, candidateObjects, constants, cache);
    }
}
