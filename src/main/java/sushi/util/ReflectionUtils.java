package sushi.util;

import static sushi.util.TypeUtils.ARRAYOF;
import static sushi.util.TypeUtils.BOOLEAN;
import static sushi.util.TypeUtils.BYTE;
import static sushi.util.TypeUtils.CHAR;
import static sushi.util.TypeUtils.DOUBLE;
import static sushi.util.TypeUtils.FLOAT;
import static sushi.util.TypeUtils.INT;
import static sushi.util.TypeUtils.LONG;
import static sushi.util.TypeUtils.REFERENCE;
import static sushi.util.TypeUtils.SHORT;
import static sushi.util.TypeUtils.javaClass;
import static sushi.util.TypeUtils.splitParametersDescriptors;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReflectionUtils {

    private final static Set<String> excluded;
    public  final static Set<Class<?>> primitives;
    public  final static Set<String> primitivesStringRepresentation;

    static {
        primitives = new HashSet<Class<?>>();
        primitives.add(Integer.class);
        primitives.add(Short.class);
        primitives.add(Long.class);
        primitives.add(Boolean.class);
        primitives.add(Character.class);
        primitives.add(Double.class);
        primitives.add(Float.class);
        primitives.add(Byte.class);

        primitivesStringRepresentation = new HashSet<String>();
        primitivesStringRepresentation.add("Integer");
        primitivesStringRepresentation.add("Short");
        primitivesStringRepresentation.add("Long");
        primitivesStringRepresentation.add("Boolean");
        primitivesStringRepresentation.add("Character");
        primitivesStringRepresentation.add("Double");
        primitivesStringRepresentation.add("Float");
        primitivesStringRepresentation.add("Byte");


        excluded = new HashSet<String>();
        excluded.add("equals");
        excluded.add("hashCode");
        excluded.add("toString");
        excluded.add("clone");
        excluded.add("immutableEnumSet");
    }


    /**
     * Returns the number of inheritance hops between two classes.
     * 
     * @param child
     *            the child class, may be null
     * @param parent
     *            the parent class, may be null
     * @return the number of generations between the child and parent; 0 if the
     *         same class; -1 if the classes are not related as child and parent
     *         (includes where either class is null)
     */
    public static int classDistance(final Class<?> child, final Class<?> parent) {
        if (child == null || parent == null) {
            return -1;
        }

        if (child.equals(parent)) {
            return 0;
        }

        final Class<?> cParent = child.getSuperclass();
        int d = parent.equals(cParent) ? 1 : 0;

        if (d == 1) {
            return d;
        }
        d += classDistance(cParent, parent);
        return d > 0 ? d + 1 : -1;
    }


    public static List<Field> getInheritedPrivateFields(final Class<?> type) {
        List<Field> result = new ArrayList<Field>();

        Class<?> i = type;
        while (i != null && i != Object.class) {
            for (Field field : i.getDeclaredFields()) {
                // theoretically, excluding synthetic fields is the right thing to do
                // in practice, lazy initialization heavily relies on anonymous and inner 
                // 				classes that do use and access fields in the enclosing class
                if (i.isAnonymousClass() || !field.isSynthetic()) {
                    result.add(field);
                }
            }
            i = i.getSuperclass();
        }

        return result;
    }

    public static Field getInheritedPrivateField(final Class<?> type, String fname, String cname) {
        Class<?> i = type;
        while (i != null && i != Object.class) {
            if (i.getName().replace('.', '/').equals(cname)) {
                Field field = null;
                try {
                    field = i.getDeclaredField(fname);
                } catch (NoSuchFieldException | SecurityException e) {
                    //do nothing
                }			
                return field;
            }
            i = i.getSuperclass();
        }
        return null;
    }

    public static boolean isArray(final Object obj) {
        if (obj == null || obj.getClass() == null) {
            return false;
        }
        return obj.getClass().isArray();
    }

    public static boolean isConstant(final Field f) {
        if (Modifier.isFinal(f.getModifiers()) && Modifier.isStatic(f.getModifiers())) {
            return true;
        }
        return false;
    }

    public static boolean isPrimitive(final Class<?> clazz) {
        if (clazz == null) {
            return false;
        }

        if (clazz.isPrimitive() || primitives.contains(clazz)) {
            return true;
        }

        return false;
    }

    public static boolean isPrimitive(final Object o) {
        if (o == null || o.getClass() == null) {
            return false;
        }

        Class<?> clazz = o.getClass().getComponentType() == null ? o.getClass() : o.getClass().getComponentType();
        return isPrimitive(clazz);
    }

    public static boolean isString(final Object o) {
        if (o == null || o.getClass() == null) {
            return false;
        }
        else if (o.getClass().equals(String.class)) {
            return true;
        }
        return false;
    }

    public static Method method(Class<?> methodClass, String methodDescriptor, String methodName) 
    throws ClassNotFoundException, NoSuchMethodException, SecurityException {
        final ArrayList<Class<?>> parametersClasses = new ArrayList<>();
        for (String parameterType : splitParametersDescriptors(methodDescriptor)) {
            final Class<?> parameterClass;
            if (parameterType.charAt(0) == ARRAYOF || parameterType.charAt(0) == REFERENCE) {
                final String parameterJavaType = javaClass(parameterType, false);
                parameterClass = Class.forName(parameterJavaType);
            } else {
                switch (parameterType.charAt(0)) {
                case BOOLEAN:
                    parameterClass = boolean.class;
                    break;
                case BYTE:
                    parameterClass = byte.class;
                    break;
                case CHAR:
                    parameterClass = char.class;
                    break;
                case DOUBLE:
                    parameterClass = double.class;
                    break;
                case FLOAT:
                    parameterClass = float.class;
                    break;
                case INT:
                    parameterClass = int.class;
                    break;
                case LONG:
                    parameterClass = long.class;
                    break;
                case SHORT:
                    parameterClass = short.class;
                    break;
                default:
                    throw new RuntimeException("Reached unreachable default: unrecognized primitive type for argument: " + parameterType.charAt(0));
                }
            }
            parametersClasses.add(parameterClass);
        }
        final Method retVal = methodClass.getDeclaredMethod(methodName, parametersClasses.toArray(new Class<?>[0]));
        return retVal;
    }
}
