package sushi.util;

import java.util.ArrayList;

public final class TypeUtils {
    public static final char BOOLEAN  		= 'Z';
    public static final char BYTE     		= 'B';
    public static final char CHAR     		= 'C';
    public static final char DOUBLE   		= 'D';
    public static final char FLOAT    		= 'F';
    public static final char INT      		= 'I';
    public static final char LONG     		= 'J';
    public static final char SHORT    		= 'S';
    
    public static final char ARRAYOF        = '[';
    public static final char REFERENCE		= 'L';
    public static final char TYPEEND        = ';';
	
	public static String javaPrimitiveType(char type) {
		if (type == BOOLEAN) {
			return "boolean";
		} else if (type == BYTE) {
			return "byte";
		} else if (type == CHAR) {
			return "char";
		} else if (type == DOUBLE) {
			return "double";
		} else if (type == FLOAT) {
			return "float";
		} else if (type == INT) {
			return "int";
		} else if (type == LONG) {
			return "long";
		} else if (type == SHORT) {
			return "short";
		} else {
			throw new RuntimeException("Unexpected primitive type " + type + ".");
		}
	}

	public static String javaClass(String type, boolean forDeclaration) {
		if (type == null) {
			return null;
		}
		final String a = type.replace('/', '.');
		final String s = (forDeclaration ? a.replace('$', '.') : a);

		if (forDeclaration) {
			final char[] tmp = s.toCharArray();
			int arrayNestingLevel = 0;
			boolean hasReference = false;
			int start = 0;
			for (int i = 0; i < tmp.length ; ++i) {
				if (tmp[i] == '[') {
					++arrayNestingLevel;
				} else if (tmp[i] == 'L') {
					hasReference = true;
				} else {
					start = i;
					break;
				}
			}
			final String t = hasReference ? s.substring(start, tmp.length - 1) : javaPrimitiveType(s.charAt(start));
			final StringBuilder retVal = new StringBuilder(t);
			for (int k = 1; k <= arrayNestingLevel; ++k) {
				retVal.append("[]");
			}
			return retVal.toString();
		} else {
			return (isReference(s) ? className(s) : s);
		}
	}
	
    public static boolean isReference(char c) {
        return (c == REFERENCE);
    }

    public static boolean isReference(String type) {
        if (type == null || type.length() < 3) { //at least L + single char + ;
            return false;
        }
        final char c = type.charAt(0);
        final char cc = type.charAt(type.length() - 1);
        return (isReference(c) && cc == TYPEEND);
    }

    public static boolean isArray(char type) {
        return (type == ARRAYOF);
    }

    public static boolean isArray(String type) {
        if (type == null || type.length() < 2) { //at least [ + single char
            return false;
        } else {
            final char c = type.charAt(0);
            return isArray(c);
        }
    }

    private static String getReferenceClassName(String type) {
        if (isReference(type)) {
            return type.substring(1, type.length() - 1);
        } else {
            return null;
        }
    }

    public static String className(String type) {
        //if reference, remove REFERENCE and TYPEEND; 
        //if array, just return it
        return (isReference(type) ? getReferenceClassName(type) : 
            isArray(type) ? type : null);
    }


    /**
     * Given a descriptor of a method returns an array of 
     * {@link String}s containing the descriptors of its parameters.
     * 
     * @param methodDescriptor a {@link String}, the descriptor of a method.
     * @return a {@link String}{@code []}, whose i-th
     *         element is the descriptor of the method's i-th
     *         parameter.
     */
    public static String[] splitParametersDescriptors(String methodDescriptor){
        ArrayList<String> myVector = new ArrayList<String>();
        for (int j = 1; j < methodDescriptor.lastIndexOf(')'); j++) {
            if (methodDescriptor.charAt(j) == REFERENCE) {
                final int z = j;
                while (methodDescriptor.charAt(j) != TYPEEND) {
                    j++;
                }
                myVector.add(methodDescriptor.substring(z, j + 1));
            } else if (methodDescriptor.charAt(j) == ARRAYOF) {
                final int z = j;
                while (methodDescriptor.charAt(j) == ARRAYOF) {
                    j++;
                }
                if (methodDescriptor.charAt(j) == REFERENCE) {
                    while (methodDescriptor.charAt(j) != TYPEEND) {
                        j++;
                    }
                }
                myVector.add(methodDescriptor.substring(z, j + 1));
            } else {
                myVector.add("" + methodDescriptor.charAt(j));
            }
        }
        final String[] retString = new String[myVector.size()];
        for (int b = 0; b < myVector.size(); b++) {
            retString[b] = myVector.get(b);
        }
        return (retString);
    }
}
