package sushi.compile.distance;

/*
 * The difference between this impl. and the standard one is that, rather
 * than creating and retaining a matrix of size threadName.length()+1 by
 * t.length()+1, we maintain two single-dimensional arrays of length
 * threadName.length()+1. The first, d, is the 'current working' distance array
 * that maintains the newest distance cost counts as we iterate through
 * the characters of String threadName. Each time we increment the index of
 * String t we are comparing, d is copied to p, the second int[]. Doing
 * so allows us to retain the previous cost counts as required by the
 * algorithm (taking the minimum of the cost count to the left, up one,
 * and diagonally up and to the left of the current cost count being
 * calculated).
 */
public class LevenshteinDistance {
	public static int calculateDistance(final String s, final String t) {
		int sLength = (s == null) ? 0 : s.length();
		int tLength = (t == null) ? 0 : t.length();

		if (sLength == 0) {
			return tLength;
		} else if (tLength == 0) {
			return sLength;
		}

		int[] previousCost = new int[sLength + 1]; // 'previous' cost array, horizontally
		int[] cost = new int[sLength + 1]; // cost array, horizontally

		for (int i = 0; i < sLength + 1; ++i) {
			previousCost[i] = i;
		}

		for (int j = 0; j < tLength; ++j) {
			final char t_j = t.charAt(j); // jth character of t
			
			cost[0] = j + 1;
			for (int i = 0; i < sLength; i++) {
				final int singleCost = s.charAt(i) == (t_j) ? 0 : 1;
				// minimum of cell to the left+1, to the top+1, diagonally left and up + cost
				cost[i + 1] = Math.min(Math.min(cost[i] + 1, previousCost[i + 1] + 1), previousCost[i] + singleCost);
			}

			// copy current distance counts to 'previous row' distance counts
			final int[] _temp = previousCost;
			previousCost = cost;
			cost = _temp;
		}

		// our last action in the above loop was to switch d and p, so p now
		// actually has the most recent cost counts
		final int result = previousCost[sLength];
		return result;
	}
}
