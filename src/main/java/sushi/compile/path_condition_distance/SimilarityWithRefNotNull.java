package sushi.compile.path_condition_distance;

import sushi.logging.Logger;

public class SimilarityWithRefNotNull extends SimilarityWithRef {
	private static final Logger logger = new Logger(SimilarityWithRefNotNull.class);
	
	public SimilarityWithRefNotNull(String theReferenceOrigin) {
		super(theReferenceOrigin);
	}

	protected double evaluateSimilarity(CandidateBackbone backbone, Object referredObject) {
		logger.debug("Reference shall be not null");
		
		double similarity = 0.0d;
		
		if (referredObject == null) {
                    logger.debug("Unconfirmed non-null. Field " + this.theReferenceOrigin + " is null in candidate");
                    similarity = 0.0d;
		} else {
                    logger.debug("Confirmed non-null. Field " + this.theReferenceOrigin + " is not null");
                    similarity = 1.0d;      
		}
		
		logger.debug("Similarity increases by: " + similarity);
		return similarity;
	}

}
