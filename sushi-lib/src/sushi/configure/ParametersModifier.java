package sushi.configure;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import jbse.apps.settings.ParseException;
import jbse.apps.settings.SettingsReader;

public class ParametersModifier {
	public void modify(Options p) { }
	
	public void modify(JBSEParameters p)
	throws FileNotFoundException, ParseException, IOException { }
	
	protected final void loadHEXFile(String path, JBSEParameters p) 
	throws FileNotFoundException, ParseException, IOException {
		final SettingsReader sr = new SettingsReader(path);
		sr.fillRunnerParameters(p.getRunnerParameters());
		sr.fillRulesLICS(p.getLICSRulesRepo());
		sr.fillRulesClassInit(p.getClassInitRulesRepo());
	}

	public void modify(MinimizerParameters p) { }

	public void modify(List<String> p) { }	
}
