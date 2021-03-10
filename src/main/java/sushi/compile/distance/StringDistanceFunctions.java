package sushi.compile.distance;

public interface StringDistanceFunctions {
    static int distanceEditLevenshtein(final String s, final String t) {
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

    static int distanceContainment(final String superstring, final String substring) {
        int superLength = (superstring == null) ? 0 : superstring.length();
        int subLength = (substring == null) ? 0 : substring.length();

        if (superLength <= subLength) {
            return distanceEditLevenshtein(superstring, substring);
        } else if (subLength == 0) {
            return 0; //an empty string is a substring of any string
        }

        final int[][] previousCost = new int[superLength - subLength + 1][superLength + 1]; // 'previous' cost arrays, horizontally
        final int[][] cost = new int[superLength - subLength + 1][superLength + 1]; // cost arrays, horizontally

        for (int k = 0; k < superLength - subLength + 1; ++k) {
            for (int i = 0; i < superLength + 1; ++i) {
                previousCost[k][i] = i - k;
            }
        }

        for (int j = 0; j < subLength; ++j) {
            final char substring_j = substring.charAt(j); // jth character of substring
            for (int k = 0; k < superLength - subLength + 1; ++k) {
                cost[k][k] = j + 1;
                for (int i = k; i < k + subLength; i++) {
                    final int singleCost = (superstring.charAt(i) == substring_j) ? 0 : 1;
                    // minimum of cell to the left+1, to the top+1, diagonally left and up + cost
                    cost[k][i + 1] = Math.min(Math.min(cost[k][i] + 1, previousCost[k][i + 1] + 1), previousCost[k][i] + singleCost);
                }
                final int[] _temp = previousCost[k];
                previousCost[k] = cost[k];
                cost[k] = _temp;
            }
        }

        // our last action in the above loop was to switch d and p, so p now
        // actually has the most recent cost counts
        int result = Integer.MAX_VALUE;
        for (int k = 0; k < superLength - subLength + 1; ++k) {
            result = Math.min(result, previousCost[k][k + subLength]);
        }

        return result;
    }

    static int distancePrefix(final String prefix, final String superstring) {
        int lengthPrefix = (prefix == null) ? 0 : prefix.length();
        int lengthSuperstring = (superstring == null) ? 0 : superstring.length();

        if (lengthPrefix == 0) {
            return 0; //an empty string is a prefix of any string
        } else if (lengthSuperstring == 0) {
            return lengthPrefix;
        }

        int retVal = lengthPrefix;
        int minLength = Math.min(lengthPrefix, lengthSuperstring);
        for (int i = 0; i < minLength; ++i) {
            if (prefix.charAt(i) == superstring.charAt(i)) { 
                --retVal;
            }
        }

        return retVal;
    }

    static int distanceSuffix(final String suffix, final String superstring) {
        return distancePrefix(new StringBuilder(suffix).reverse().toString(), new StringBuilder(superstring).reverse().toString());
    }
}
