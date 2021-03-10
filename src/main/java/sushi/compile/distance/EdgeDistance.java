package sushi.compile.distance;

public class EdgeDistance {
	public static int calculateDistance(final String s, final String t) {
		int sLength = (s == null) ? 0 : s.length();
		int tLength = (t == null) ? 0 : t.length();

		if (sLength == 0) {
			return tLength;
		} else if (tLength == 0) {
			return sLength;
		}

		int countMissing = sLength;
		int minLength = Math.min(sLength, tLength);
		for (int i = 0; i < minLength; i++) {
			if (s.charAt(i) == t.charAt(i)) 
				--countMissing;
			else break;		
		}
		
		int result = (countMissing != 0) ? countMissing : tLength - sLength;
	
		return result;
	}
}
