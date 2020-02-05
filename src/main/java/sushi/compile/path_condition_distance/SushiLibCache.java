package sushi.compile.path_condition_distance;

import java.util.HashMap;
import java.util.Map;

public class SushiLibCache {
    private final Map<String, ParsedOrigin> parsedOrigins = new HashMap<>();

    /* The following variables are used only for profiling purposes:
	int attempts = 0;
	int partialHits = 0;
	int hits = 0;
	int misses = 0;
	int nextOutputAtAttempt = 100; */

    public SushiLibCache() { }

    public ParsedOrigin getParsedOrigin(String origin)  {
        if (!this.parsedOrigins.containsKey(origin)) {
            final ParsedOrigin newCachedOrigin = new ParsedOrigin(origin);
            this.parsedOrigins.put(origin, newCachedOrigin);
        }
        return this.parsedOrigins.get(origin);
    }

}
