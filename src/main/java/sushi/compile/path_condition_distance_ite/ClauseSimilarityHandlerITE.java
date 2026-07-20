package sushi.compile.path_condition_distance_ite;

import java.util.Map;

import sushi.compile.path_condition_distance.CandidateBackbone;
import sushi.compile.path_condition_distance.SushiLibCache;

public interface ClauseSimilarityHandlerITE<COLOR_TYPE> {
	
	double evaluateSimilarity(CandidateBackbone vdata, Map<String, Object> candidateObjects, Map<Long, String> constants, SushiLibCache cache);

	PathConditionColor<COLOR_TYPE> getColor();
}
