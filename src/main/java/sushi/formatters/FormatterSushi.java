package sushi.formatters;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jbse.apps.Formatter;

/**
 * A formatter for symbolic execution.
 * 
 * @author Pietro Braione
 */
public interface FormatterSushi extends Formatter {
	void setStringsConstant(Map<Long, String> stringLiterals);
	void setStringsNonconstant(Set<Long> stringOthers);
	void setForbiddenExpansions(List<String> forbiddenExpansions);
}
