package sushi.compile.path_condition_distance;

import sushi.compile.distance.PrefixDistance;
import sushi.logging.Logger;

public class SimilarityWithRefToFreshObject extends SimilarityWithRef {
	private static final Logger logger = new Logger(SimilarityWithRefToFreshObject.class);

	private final Class<?> theReferredClass;
	
	public SimilarityWithRefToFreshObject(String theReferenceOrigin, Class<?> theReferredClass) {
	    super(theReferenceOrigin);
	    if (theReferredClass == null) {
	        throw new SimilarityComputationException("Class cannot be null");
	    }
	    this.theReferredClass = theReferredClass;
	}

        public SimilarityWithRefToFreshObject(String theReferenceOrigin) {
            super(theReferenceOrigin);
            this.theReferredClass = null;
        }

	@Override
	protected double evaluateSimilarity(CandidateBackbone backbone, Object referredObject) {
	    logger.debug("Ref to a fresh object");

	    final double freshnessSimilarity = 0.3d;
	    final double samePackageSimilarity = 0.3d;
	    final double sameClassSimilarity = 1.0d - freshnessSimilarity - samePackageSimilarity;
	    assert (0 <= freshnessSimilarity && freshnessSimilarity <= 1.0d);
	    assert (0 <= samePackageSimilarity && samePackageSimilarity <= 1.0d);
	    assert (0 <= sameClassSimilarity && sameClassSimilarity <= 1.0d);

	    boolean isFreshObject = false;
	    double similarity = 0.0d;

	    if (referredObject == null) {
	        logger.debug(this.theReferenceOrigin + " is not a fresh object in candidate, rather it is null");
	    } else {
	        final String objOrigin = backbone.getOrigin(referredObject);
	        if (objOrigin.equals(this.theReferenceOrigin)) {
                    logger.debug(this.theReferenceOrigin + " is a fresh object also in candidate");
                    isFreshObject = true;
                    similarity += freshnessSimilarity;
	        } else { //it is an alias rather than a fresh object
                    logger.debug(this.theReferenceOrigin + " is not a fresh object in candidate, rather it aliases " + objOrigin);
                    final int distance = PrefixDistance.calculateDistance(this.theReferenceOrigin, objOrigin);
                    assert (distance != 0);
                    similarity += InverseDistances.inverseDistanceExp(distance, freshnessSimilarity);
	        }
	    }

	    if (!isFreshObject) {
	        logger.debug("Similarity increases by: " + similarity);
	        return similarity;
	    }

	    if (this.theReferredClass == null) {
	        logger.debug(this.theReferenceOrigin + " refers to an object compatible with its static type");
	        similarity += sameClassSimilarity + samePackageSimilarity;
	    } else if (referredObject.getClass().equals(this.theReferredClass)) {
	        logger.debug(this.theReferenceOrigin + " refers to an object that matches " + this.theReferredClass);
	        similarity += sameClassSimilarity + samePackageSimilarity;
	    } else {
	        logger.debug(this.theReferenceOrigin + " refers to an object of class " + referredObject.getClass() + " rather than " + this.theReferredClass);
	        String classNameTarget = this.theReferredClass.getName();
	        int splitPoint = classNameTarget.lastIndexOf('.');
	        final String packageTarget = classNameTarget.substring(0, splitPoint);
	        classNameTarget = classNameTarget.substring(splitPoint, classNameTarget.length());

	        String classNameCandidate = referredObject.getClass().getName();
	        splitPoint = classNameCandidate.lastIndexOf('.');
	        final String packageCandidate = classNameCandidate.substring(0, splitPoint);
	        classNameCandidate = classNameCandidate.substring(splitPoint, classNameCandidate.length());

	        final int packageDistance = PrefixDistance.calculateDistance(packageTarget, packageCandidate);
	        similarity += InverseDistances.inverseDistanceExp(packageDistance, samePackageSimilarity);
	        if (packageDistance == 0) {
	            logger.debug("The packages are the same");
	            final double classNameDistance = PrefixDistance.calculateDistance(classNameTarget, classNameCandidate);
	            similarity += InverseDistances.inverseDistanceExp(classNameDistance, sameClassSimilarity);
	        }
	    }

	    logger.debug("Similarity increases by: " + similarity);
	    return similarity;
	}
}
