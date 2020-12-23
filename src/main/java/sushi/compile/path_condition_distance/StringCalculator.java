package sushi.compile.path_condition_distance;

import java.util.List;

public interface StringCalculator {

	Iterable<String> getVariableOrigins();

	String getString(List<Object> variables);
	
}
