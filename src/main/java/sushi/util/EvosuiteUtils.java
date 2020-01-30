package sushi.util;

import java.lang.reflect.InvocationTargetException;

public class EvosuiteUtils {

	public static void logMessageToEvosuiteConsoleLogger(String msg) {
		try {
			Object logger = Class.forName("org.evosuite.utils.LoggingUtils").getMethod("getEvoLogger", (Class<?>[]) null).invoke(null, (Object[]) null);
			logger.getClass().getMethod("info", new Class<?>[] {String.class}).invoke(logger, new Object[] {msg});
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException e) { }
	}
	
}
