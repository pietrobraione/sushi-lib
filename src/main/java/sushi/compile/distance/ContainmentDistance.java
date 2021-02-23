package sushi.compile.distance;

public class ContainmentDistance {

	public static int calculateDistance(final String superstring, final String substring) {
		int superLength = (superstring == null) ? 0 : superstring.length();
		int subLength = (substring == null) ? 0 : substring.length();

		if (superLength <= subLength) {
		    return LevenshteinDistance.calculateDistance(superstring, substring);
		} else if (subLength == 0) {
		    return superLength;
		}
		
		int distance = superLength;
		for (int i = 0; i < superLength - subLength; ++i) {
		    final String superstringFragment = superstring.substring(i, i + subLength);
		    distance = Math.min(distance, LevenshteinDistance.calculateDistance(superstringFragment, substring));
		}
		
		return distance;
	}
}
