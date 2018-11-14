package sushi.formatters;

import java.util.Set;

import jbse.apps.Formatter;

/**
 * A formatter for symbolic execution.
 * 
 * @author Pietro Braione
 */
public interface FormatterSushi extends Formatter {
	void formatStringLiterals(Set<String> stringLiterals);
}
