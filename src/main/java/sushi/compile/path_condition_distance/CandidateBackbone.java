package sushi.compile.path_condition_distance;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CandidateBackbone {
	private final ClassLoader classLoader;
	
	// We keep the direct and reverse mapping between visited objects and their origins 
	private final Map<ObjectMapWrapper, String> visitedObjects = new HashMap<ObjectMapWrapper, String>(); 
	private final Map<String, Object> visitedOrigins = new HashMap<String, Object>(); 

	private final Collection<String> invalidFieldPaths = new HashSet<String>(); 
	
	public CandidateBackbone(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}
	
	public ClassLoader getClassLoader() {
		return this.classLoader;
	}
	
	private void storeInBackbone(Object obj, String origin) {
		// If another origin already exist, this is an alias path
		// and then it shall not be stored
		if (!this.visitedObjects.containsKey(new ObjectMapWrapper(obj))) {
			this.visitedOrigins.put(origin, obj);		
			this.visitedObjects.put(new ObjectMapWrapper(obj), origin);
		}
	}

	public Object getVisitedObject(String origin) {
		return this.visitedOrigins.get(origin);
	}

	public String getOrigin(Object obj) {
		return this.visitedObjects.get(new ObjectMapWrapper(obj));
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
	throws FieldNotInCandidateException, FieldDependsOnInvalidFieldPathException {
		assert (origin != null); 
		
		if (cache == null) {
			cache = new SushiLibCache(); //no-cache behavior: use a throw-away local cache 
		}
		
		//check in the cache of the visited object
		Object obj = getVisitedObject(origin);
		if (obj != null) {
			return obj;
		}
		
		ParsedOrigin parsedOrigin = cache.getParsedOrigin(origin);
		obj = parsedOrigin.get(candidateObjects, this, constants, cache);
		storeInBackbone(obj, origin);

		return obj;
	}
	
}
