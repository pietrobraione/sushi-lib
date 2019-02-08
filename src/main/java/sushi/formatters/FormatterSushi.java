package sushi.formatters;

import java.util.Map;

import jbse.apps.Formatter;

/**
 * A formatter for symbolic execution.
 * 
 * @author Pietro Braione
 */
public interface FormatterSushi extends Formatter {
	void setConstants(Map<Long, String> stringLiterals);
}
