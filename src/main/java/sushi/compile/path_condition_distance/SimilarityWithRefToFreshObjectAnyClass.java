package sushi.compile.path_condition_distance;

import sushi.compile.distance.PrefixDistance;
import sushi.logging.Logger;

public class SimilarityWithRefToFreshObjectAnyClass extends SimilarityWithRef {
	private static final Logger logger = new Logger(SimilarityWithRefToFreshObjectAnyClass.class);

	public SimilarityWithRefToFreshObjectAnyClass(String theReferenceOrigin) {
		super(theReferenceOrigin);
	}

	@Override
	protected double evaluateSimilarity(CandidateBackbone backbone, Object referredObject) {
		logger.debug("Ref to a fresh object (without considering of possible diversity of type)");
		
		final double freshnessSimilarity = 1d;
		
		double similarity = 0.0d;
		
		if (referredObject == null) {
			logger.debug(theReferenceOrigin + " is not a fresh object in candidate, rather it is null");
		}
		else {
			String objOrigin = backbone.getOrigin(referredObject);
			if (!objOrigin.equals(theReferenceOrigin)) { //it is an alias rather than a fresh object
				logger.debug(theReferenceOrigin + " is not a fresh object in candidate, rather it aliases " + objOrigin);
				int distance = PrefixDistance.calculateDistance(theReferenceOrigin, objOrigin);
				assert (distance != 0);
				similarity += InverseDistances.inverseDistanceExp(distance, freshnessSimilarity);
			}
			else {
				logger.debug(theReferenceOrigin + " is a fresh object also in candidate");
				similarity += freshnessSimilarity;
			}
		}
			
		logger.debug("Similarity increases by: " + similarity);
		return similarity;
	}

}
