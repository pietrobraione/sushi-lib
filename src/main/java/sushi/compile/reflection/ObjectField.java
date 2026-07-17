package sushi.compile.reflection;

import static java.lang.System.identityHashCode;

import java.lang.reflect.Field;

public class ObjectField {
	
	private final Object obj;
	private final Field fld;

	public ObjectField(Object obj, String fldName) {
		this.obj = obj;
		try {
        	Class<?> clazz = obj.getClass();
        	Field fldTmp = null;
        	do {
        		try {
        			fldTmp = clazz.getDeclaredField(fldName);
        			break;
        		} catch (NoSuchFieldException e) {
        			clazz = clazz.getSuperclass();
        		}
        	} while (clazz != null);
        	if (fldTmp == null) {
        		throw new NoSuchFieldException();
        	}
        	this.fld = fldTmp;
		} catch (NoSuchFieldException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}
	
	public ObjectField(Object obj, Field fld) {
		this.obj = obj;
		this.fld = fld;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.fld == null) ? 0 : this.fld.hashCode());
		result = prime * result + ((this.obj == null) ? 0 : identityHashCode(this.obj));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ObjectField other = (ObjectField) obj;
		if (this.fld == null) {
			if (other.fld != null) {
				return false;
			}
		} else if (!this.fld.equals(other.fld)) {
			return false;
		}
		if (this.obj == null) {
			if (other.obj != null) {
				return false;
			}
		} else if (this.obj != other.obj) {
			return false;
		}
		return true;
	}
}
