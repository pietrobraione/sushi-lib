package sushi.compile.path_condition_distance;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CandidateBackbone {
	private static CandidateBackbone _I = null;
	private static boolean reuseBackbone = false;

	private final ClassLoader classLoader;
	
	// We keep the direct and reverse mapping between visited objects and their origins 
	private final Map<ObjectMapWrapper, String> freshObjects = new HashMap<ObjectMapWrapper, String>(); 
	private final Map<String, Object> visitedOrigins = new HashMap<String, Object>(); 
	private final Collection<String> invalidFieldPaths = new HashSet<String>(); 
	
	public CandidateBackbone(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}
	
	public ClassLoader getClassLoader() {
		return this.classLoader;
	}
	
	public static CandidateBackbone makeNewBackbone(ClassLoader classLoader) {
		if (!reuseBackbone) {
			return new CandidateBackbone(classLoader);
		}
		if(_I == null) {
			_I = new CandidateBackbone(classLoader);
		} else {
			_I.invalidFieldPaths.clear(); /* the information on the invalid field paths is specific for each path condition and shall not be reused across path conditions */
			_I.freshObjects.clear();
		}
		return _I;
	}
	
	public static void resetAndReuseUntilReset() {
		reuseBackbone = true;
		_I = null; // resetting the backbone
	}

	private void storeInBackboneIfFresh(Object obj, String origin) {
		// If another origin already exist for a non-null object, this is an alias path
		// and then it shall not be stored
		if (obj != null && !this.freshObjects.containsKey(new ObjectMapWrapper(obj))) {
			this.freshObjects.put(new ObjectMapWrapper(obj), origin);
		}
	}

	public boolean isVisitedOrigin(String origin) {
		return this.visitedOrigins.containsKey(origin);
	}

	public Object getObjectByOrigin(String origin) {
		return this.visitedOrigins.get(origin);
	}

	public String getOrigin(Object obj) {
		return this.freshObjects.get(new ObjectMapWrapper(obj));
	}

	public void addInvalidFieldPath(String refPath) {
		this.invalidFieldPaths.add(refPath);
	}
	
	public Set<String> getInvalidFieldPaths() {
		return new HashSet<>(this.invalidFieldPaths);
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

	public Object retrieveOrVisitField(String origin, Map<String, Object> candidateObjects, Map<Long, String> constants, SushiLibCache cache) 
	throws FieldNotInCandidateException, ObjectNotInCandidateException, FieldDependsOnInvalidFieldPathException {
		assert (origin != null); 
		
		if (cache == null) {
			cache = new SushiLibCache(); //no-cache behavior: use a throw-away local cache 
		}
		
		Object obj;
		// for origins that are not function calls, check in the cache of the visited object
		if (origin.charAt(0) != '<' && isVisitedOrigin(origin)) {
			obj = getObjectByOrigin(origin);
		} else {
			ParsedOrigin parsedOrigin = cache.getParsedOrigin(origin);
			obj = parsedOrigin.get(candidateObjects, this, constants, cache);
			this.visitedOrigins.put(origin, obj);			
		}
		storeInBackboneIfFresh(obj, origin);
		return obj;
	}
	
}
