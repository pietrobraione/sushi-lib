package sushi.compile.path_condition_distance;

import java.util.Map;

import sushi.logging.Logger;

public class SimilarityWithRefNotNull extends SimilarityWithRef {
	private static final Logger logger = new Logger(SimilarityWithRefNotNull.class);
	
	public SimilarityWithRefNotNull(String theReferenceOrigin) {
		super(theReferenceOrigin);
	}

	protected double evaluateSimilarity(CandidateBackbone backbone, Object referredObject) {
		logger.debug("Reference shall be not null");
		
		double similarity = 0.0d;
		
		if (referredObject != null) {
			logger.debug("Confirmed non-null. Field " + theReferenceOrigin + " is not null");
			similarity += 1.0d;	
		}
		else {
			logger.debug("Unconfirmed non-null. Field " + theReferenceOrigin + " is null in candidate");
		}
		
		logger.debug("Similarity increases by: " + similarity);
		return similarity;
	}

}
