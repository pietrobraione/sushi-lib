package sushi.compile.path_condition_distance_ite;

import static sushi.compile.path_condition_distance.InverseDistances.inverseDistanceRatio;

import java.util.ArrayList;
import java.util.Map;

import sushi.compile.path_condition_distance.CandidateBackbone;
import sushi.compile.path_condition_distance.FieldDependsOnInvalidFieldPathException;
import sushi.compile.path_condition_distance.FieldNotInCandidateException;
import sushi.compile.path_condition_distance.ObjectNotInCandidateException;
import sushi.compile.path_condition_distance.SimilarityComputationException;
import sushi.compile.path_condition_distance.SushiLibCache;
import sushi.logging.Logger;

public class SimilarityWithConditionalExpressionITE<VALUE_TYPE, COLOR_TYPE> implements ClauseSimilarityHandlerITE<COLOR_TYPE> {
	private static final Logger logger = new Logger(SimilarityWithConditionalExpressionITE.class);
	
	private final ValueCalculatorITE<VALUE_TYPE, COLOR_TYPE> theValueCalculator;
	
	public SimilarityWithConditionalExpressionITE(ValueCalculatorITE<VALUE_TYPE, COLOR_TYPE> theValueCalculator) {
	    if (theValueCalculator == null) {
	        throw new SimilarityComputationException("Value calculator cannot be null");
	    }
	    this.theValueCalculator = theValueCalculator;
	}

	@Override
	public double evaluateSimilarity(CandidateBackbone backbone, Map<String, Object> candidateObjects, Map<Long, String> constants, SushiLibCache cache) {
	    logger.debug("Handling similarity for numeric expression");

	    double similarity = 0.0d;
	    String theVariableOrigin = null; //only for exceptions
	    final ArrayList<Object> variables = new ArrayList<>();
	    for (String variableOrigin : this.theValueCalculator.getVariableOrigins()) {
	    	theVariableOrigin = variableOrigin;
	    	Object variableValue = null;
			try {
				variableValue = backbone.retrieveOrVisitField(variableOrigin, candidateObjects, constants, cache);
			} catch (FieldNotInCandidateException e) {
		        logger.debug("Field " + theVariableOrigin + " does not yet exist in candidate");	
		        /* variableValue remains null */
		    } catch (ObjectNotInCandidateException e) {
		        logger.debug("Field " + theVariableOrigin + " refers concrete objects in candidate that could not be stored (currently only concrete strings are stored)");                        
		        /* variableValue remains null */
		    } catch (FieldDependsOnInvalidFieldPathException e) {
		        logger.debug("Field " + theVariableOrigin + " depends on field path that did not converge yet: " + e.getMessage());			
		        /* variableValue remains null */
		    }
	    	variables.add(variableValue);
	    }
	    this.theValueCalculator.getColor().reset();
	    similarity += inverseDistanceRatio(this.theValueCalculator.calculate(variables), 1.0d); 
	    logger.debug("Similarity increases by: " + similarity);
	    return similarity;
	}

	@Override
	public PathConditionColor<COLOR_TYPE> getColor() {
		return theValueCalculator.getColor();
	}

}
