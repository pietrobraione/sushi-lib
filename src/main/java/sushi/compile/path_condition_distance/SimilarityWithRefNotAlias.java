package sushi.compile.path_condition_distance;

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
		
		final double similarity;
		
		//Note that the aliased object must have been retrieved in the past,
		//thus we do not need to call backbone.retrieveOrVisitField in this case
		final Object alias = backbone.getObjectByOrigin(this.theAliasOrigin);

		if (referredObject != null && referredObject == alias) {
		    logger.debug("Unconfirmed non-matching aliases. There is match between field " + this.theReferenceOrigin + " and field " + this.theAliasOrigin);
		    similarity = 0.0d;
		} else {
		    logger.debug("Confirmed non-matching aliases");
		    similarity = 1.0d;
		}

		logger.debug("Similarity increases by: " + similarity);
		return similarity;
	}
}
