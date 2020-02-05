package sushi.compile.path_condition_distance;

import java.util.Map;

import sushi.logging.Logger;

public abstract class SimilarityWithRef implements ClauseSimilarityHandler {
	private static final Logger logger = new Logger(SimilarityWithRef.class);

	protected final String theReferenceOrigin;
	
	public SimilarityWithRef(String theReferenceOrigin) {
		if (theReferenceOrigin == null) {
			throw new SimilarityComputationException("Origin cannot be null");
		}
		this.theReferenceOrigin = theReferenceOrigin;
	}

	@Override
	public final double evaluateSimilarity(CandidateBackbone backbone, Map<String, Object> candidateObjects, Map<Long, String> constants, SushiLibCache cache) {
		logger.debug("Handling similarity with field reference " + this.theReferenceOrigin);
		
		double similarity = 0.0d;
		try {
			final Object referredObj = backbone.retrieveOrVisitField(this.theReferenceOrigin, candidateObjects, constants, cache);
			similarity = evaluateSimilarity(backbone, referredObj);
			if (similarity != 1.0d) {
				backbone.addInvalidFieldPath(this.theReferenceOrigin);
			}			
		} catch (FieldNotInCandidateException e) {
			logger.debug("Field " + theReferenceOrigin + " does not yet exist in candidate");			
			backbone.addInvalidFieldPath(this.theReferenceOrigin);
		} catch (FieldDependsOnInvalidFieldPathException e) {
			logger.debug("Field " + theReferenceOrigin + " depends on field path that did not converge yet: " + e.getMessage());			
		}
		return similarity;
	}

	protected abstract double evaluateSimilarity(CandidateBackbone backbone, Object referredObject);
}
