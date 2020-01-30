package sushi.compile.path_condition_distance;

import java.util.Map;

import sushi.logging.Logger;

public class SimilarityWithRefNotAlias extends SimilarityWithRef {
	private static final Logger logger = new Logger(SimilarityWithRefNotAlias.class);

	private final String theAliasOrigin;
	
	public SimilarityWithRefNotAlias(String theReferenceOrigin, String theAliasOrigin) {
		super(theReferenceOrigin);
		if (theAliasOrigin == null) {
			throw new SimilarityComputationException("Alias origin cannot be null");
		}
		this.theAliasOrigin = theAliasOrigin;
	}

	protected double evaluateSimilarity(CandidateBackbone backbone, Object referredObject) {
		//logger.debug("Ref that do not alias another ref");
		
		double similarity;
		
		Object alias = backbone.getVisitedObject(theAliasOrigin);

		if (referredObject != null && referredObject == alias) {
			logger.debug("Unconfirmed non-matching aliases. There is match between field " + theReferenceOrigin + " and field " + theAliasOrigin);
			similarity = 0.0d;
		}
		else {
			similarity = 1.0d;
			logger.debug("Confirmed non-matching aliases");
		}

		logger.debug("Similarity increases by: " + similarity);
		return similarity;
	}

}
