package sushi.compile.path_condition_distance_ite;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class PathConditionColor<COLOR_TYPE> {

	private Set<COLOR_TYPE> color = new HashSet<>();
	
	public PathConditionColor<COLOR_TYPE> merge(PathConditionColor<COLOR_TYPE> otherColor) {
		if (otherColor != null) {
			color.addAll(otherColor.color);
		}
		return this;
	}

	public PathConditionColor<COLOR_TYPE> merge(COLOR_TYPE aSingletonColor) {
		if (aSingletonColor != null) {
			color.add(aSingletonColor);
		}
		return this;
	}

	@Override
	public int hashCode() {
		return Objects.hash(color);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PathConditionColor<COLOR_TYPE> other = (PathConditionColor<COLOR_TYPE>) obj;
		return Objects.equals(color, other.color);
	}

	public void reset() {
		color.clear();
	}
	
	
}
