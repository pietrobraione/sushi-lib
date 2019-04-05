package sushi.compile.path_condition_distance;

import java.util.HashMap;
import java.util.Map;

public class SushiLibCache {
	
	private final Map<String, ParsedOrigin> parsedOrigins = new HashMap<>();
	
	/* OThe following variables are used only for profiling purposes:
	int attempts = 0;
	int partialHits = 0;
	int hits = 0;
	int misses = 0;
	int nextOutputAtAttempt = 100; */
	
	public SushiLibCache() {
	}
	
	public ParsedOrigin getParsedOrigin(String origin)  {
		final ParsedOrigin cachedOrigin = parsedOrigins.get(origin);
		if (cachedOrigin != null) {
			return cachedOrigin;
		} else {
			final ParsedOrigin newCachedOrigin = new ParsedOrigin(origin);
			parsedOrigins.put(origin, newCachedOrigin);
			return newCachedOrigin;
		}
	}

}
