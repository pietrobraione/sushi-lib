package sushi.compile.path_condition_distance;

import sushi.compile.distance.EdgeDistance;
import sushi.logging.Logger;

public class SimilarityWithRefToAlias extends SimilarityWithRef {
	private static final Logger logger = new Logger(SimilarityWithRefToAlias.class);

	private final String theAliasOrigin;
	
	public SimilarityWithRefToAlias(String theReferenceOrigin, String theAliasOrigin) {
		super(theReferenceOrigin);
		if (theAliasOrigin == null) {
			throw new SimilarityComputationException("Alias origin cannot be null");
		}
		this.theAliasOrigin = theAliasOrigin;
	}

	@Override
	protected double evaluateSimilarity(CandidateBackbone backbone, Object referredObject) {
		logger.debug("Ref that aliases another ref");
		
		final double similarity;
		
		//Note that the aliased object must have been retrieved in the past,
		//thus we do not need to call backbone.retrieveOrVisitField in this case
		final Object alias = backbone.getObjectByOrigin(this.theAliasOrigin); 

		if (referredObject == null) {
                    logger.debug("Non matching aliases: field " + this.theReferenceOrigin + " is null rather than alias of " + this.theAliasOrigin);
                    similarity = 0.0d;
		} else if (referredObject == alias) {
		    logger.debug("Matching aliases between field " + this.theReferenceOrigin + " and field " + this.theAliasOrigin);
		    similarity = 1.0d;
		} else {
		    final String objOrigin = backbone.getOrigin(referredObject);
		    logger.debug("Non matching aliases: field " + this.theReferenceOrigin + " corresponds to field " + 
		    objOrigin + " rather than to " + this.theAliasOrigin);
		    assert (objOrigin != null);
		    final int distance = EdgeDistance.calculateDistance(this.theAliasOrigin, objOrigin);
		    assert (distance != 0);
		    similarity = InverseDistances.inverseDistanceExp(distance, 1.0d);
		}	
		
		logger.debug("Similarity increases by: " + similarity);
		return similarity;
	}

}
