package sushi.compile.distance;

public class ContainmentDistance {
    public static int calculateDistance(final String superstring, final String substring) {
        int superLength = (superstring == null) ? 0 : superstring.length();
        int subLength = (substring == null) ? 0 : substring.length();

        if (superLength <= subLength) {
            return LevenshteinDistance.calculateDistance(superstring, substring);
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
}
