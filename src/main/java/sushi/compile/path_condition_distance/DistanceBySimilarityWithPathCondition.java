package sushi.compile.path_condition_distance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sushi.logging.Logger;

public class DistanceBySimilarityWithPathCondition {
    private static final Logger logger = new Logger(DistanceBySimilarityWithPathCondition.class);

    public static double distance(List<ClauseSimilarityHandler> pathConditionHandler, Map<String, Object> candidateObjects, Map<Long, String> constants, ClassLoader classLoader) {
        return distance(pathConditionHandler, candidateObjects, constants, classLoader, null/*no-caching behavior*/); 
    }

    public static double distance(List<ClauseSimilarityHandler> pathConditionSimilarityHandlers, Map<String, Object> candidateObjects, Map<Long, String> constants, ClassLoader classLoader, SushiLibCache cache) {
        logger.debug("Computing similarity with path condition: ");

        double achievedSimilarity = 0.0d;		
        CandidateBackbone backbone = CandidateBackbone.makeNewBackbone(classLoader); 
        for (ClauseSimilarityHandler handler : pathConditionSimilarityHandlers) {
            achievedSimilarity += handler.evaluateSimilarity(backbone, candidateObjects, constants, cache);
        }

        logger.debug("Similarity with path condition is " + achievedSimilarity);

        final double goalSimilarity = pathConditionSimilarityHandlers.size();
        final double distance = goalSimilarity - achievedSimilarity;
        assert (distance >= 0);

        logger.debug("Distance from path condition is " + distance);

        return distance;
    }
    
    public static void completeFinalHeap(Map<Long, StringCalculator> stringCalculators, Map<String, Object> candidateObjects, Map<Long, String> constants, ClassLoader classLoader) {
        completeFinalHeap(stringCalculators, candidateObjects, constants, classLoader, null);
    }
    
    public static void completeFinalHeap(Map<Long, StringCalculator> stringCalculators, Map<String, Object> candidateObjects, Map<Long, String> constants, ClassLoader classLoader, SushiLibCache cache) {
        logger.debug("Computing final heap objects with path condition: ");
        CandidateBackbone backbone = CandidateBackbone.makeNewBackbone(classLoader);
        for (Map.Entry<Long, StringCalculator> entry: stringCalculators.entrySet()) {
            String theVariableOrigin = null; //only for exceptions
            try {
                final long heapPosition = entry.getKey();
                final StringCalculator theStringCalculator = entry.getValue();
                final ArrayList<Object> variables = new ArrayList<>();
                for (String variableOrigin : theStringCalculator.getVariableOrigins()) {
                    theVariableOrigin = variableOrigin;
                    Object variableValue = backbone.retrieveOrVisitField(variableOrigin, candidateObjects, constants, cache); //TODO constant might not yet contain the necessary object to perform the evaluation. Establish an evaluation order that ensures that everything is found 
                    variables.add(variableValue);
                }
                final String theString = theStringCalculator.getString(variables);
                constants.put(heapPosition, theString);
                logger.debug("Added heap object (String) for position: " + heapPosition);
            } catch (FieldNotInCandidateException e) {
                    logger.debug("Field " + theVariableOrigin + " does not yet exist in candidate");                        
            } catch (FieldDependsOnInvalidFieldPathException e) {
                    logger.debug("Field " + theVariableOrigin + " depends on field path that did not converge yet: " + e.getMessage());                     
            }
        }
    }
}
