package sushi.formatters;

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
	void setForbiddenExpansions(Set<String> forbiddenExpansions);
}
