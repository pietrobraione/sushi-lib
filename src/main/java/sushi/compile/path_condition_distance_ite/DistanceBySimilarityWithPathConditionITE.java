package sushi.compile.path_condition_distance_ite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sushi.compile.path_condition_distance.CandidateBackbone;
import sushi.compile.path_condition_distance.FieldDependsOnInvalidFieldPathException;
import sushi.compile.path_condition_distance.FieldNotInCandidateException;
import sushi.compile.path_condition_distance.ObjectNotInCandidateException;
import sushi.compile.path_condition_distance.StringCalculator;
import sushi.compile.path_condition_distance.SushiLibCache;
import sushi.logging.Logger;

public class DistanceBySimilarityWithPathConditionITE {
    private static final Logger logger = new Logger(DistanceBySimilarityWithPathConditionITE.class);

    public static <COLOR_TYPE> double distance(List<ClauseSimilarityHandlerITE<COLOR_TYPE>> pathConditionHandler, Map<String, Object> candidateObjects, Map<Long, String> constants, ClassLoader classLoader, PathConditionColor<COLOR_TYPE> color) {
        return distance(pathConditionHandler, candidateObjects, constants, classLoader, null/*no-caching behavior*/, color); 
    }

    public static <COLOR_TYPE> double distance(List<ClauseSimilarityHandlerITE<COLOR_TYPE>> pathConditionSimilarityHandlers, Map<String, Object> candidateObjects, Map<Long, String> constants, ClassLoader classLoader, SushiLibCache cache, PathConditionColor<COLOR_TYPE> color) {
        logger.debug("Computing similarity with path condition: ");

        double achievedSimilarity = 0.0d;		
        CandidateBackbone backbone = CandidateBackbone.makeNewBackbone(classLoader); 
        if (color != null) {
        	color.reset();
        }
        for (ClauseSimilarityHandlerITE<COLOR_TYPE> handler : pathConditionSimilarityHandlers) {
            achievedSimilarity += handler.evaluateSimilarity(backbone, candidateObjects, constants, cache);
            if (color != null) {
            	color.merge(handler.getColor());
            }
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
            } catch (ObjectNotInCandidateException e) {
                    logger.debug("Field " + theVariableOrigin + " refers concrete objects in candidate that could not be stored (currently only concrete strings are stored)");                        
            } catch (FieldDependsOnInvalidFieldPathException e) {
                    logger.debug("Field " + theVariableOrigin + " depends on field path that did not converge yet: " + e.getMessage());                     
            }
        }
    }
}
