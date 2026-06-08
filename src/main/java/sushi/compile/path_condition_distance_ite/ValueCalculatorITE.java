package sushi.compile.path_condition_distance_ite;

import java.util.List;

public abstract class ValueCalculatorITE<VALUE_TYPE, COLOR_TYPE> {
	private PathConditionColor<COLOR_TYPE> color = new PathConditionColor<>();

	public abstract Iterable<String> getVariableOrigins();

	/**
	 * <p>Compute the fitness value, 0 if optimal.</p>
	 *
	 * @param variables  the values that correspond to Origins, may contain {@code null} values if some origin is not in the backbone
	 * @throws NullPointerException 	if a null value is actually accessed for calculating the fitness
	 */
	public abstract double calculate(List<Object> variables);

	protected VALUE_TYPE ite(COLOR_TYPE colorTrue, COLOR_TYPE colorFalse, 
			Object r1, Object r2, VALUE_TYPE valTrue, VALUE_TYPE valFalse) {
		if (r1 != null && r1 == r2) {
			if (colorTrue != null) {
				color.merge(colorTrue);
			}
			return valTrue;
		} else {
			if (colorFalse != null) {
				color.merge(colorFalse);
			}
			return valFalse;
		}
	}

	protected VALUE_TYPE ite(COLOR_TYPE colorTrue,  
			Object r1, Object r2, VALUE_TYPE valTrue, VALUE_TYPE valFalse) {
		return ite(colorTrue, null, r1, r2, valTrue, valFalse);
	}

	public PathConditionColor<COLOR_TYPE> getColor() {
		return this.color;
	}


}
