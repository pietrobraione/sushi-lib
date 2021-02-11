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

	    final double freshnessSimilarity = 1.0d;

	    final double similarity;

	    if (referredObject == null) {
	        logger.debug(this.theReferenceOrigin + " is not a fresh object in candidate, rather it is null");
	        similarity = 0.0d;
	    } else {
	        final String objOrigin = backbone.getOrigin(referredObject);
	        if (objOrigin.equals(this.theReferenceOrigin)) {
                    logger.debug(this.theReferenceOrigin + " is a fresh object also in candidate");
                    similarity = freshnessSimilarity;
	        } else { //it is an alias rather than a fresh object
                    logger.debug(this.theReferenceOrigin + " is not a fresh object in candidate, rather it aliases " + objOrigin);
                    final int distance = PrefixDistance.calculateDistance(this.theReferenceOrigin, objOrigin);
                    assert (distance != 0);
                    similarity = InverseDistances.inverseDistanceExp(distance, freshnessSimilarity);
	        }
	    }

	    logger.debug("Similarity increases by: " + similarity);
	    return similarity;
	}
}
