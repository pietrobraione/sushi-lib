package sushi.configure;

import java.nio.file.Path;
import java.util.regex.Pattern;

public class MinimizerParameters {
	private Path fBranches;
	private Path fCoverage;
	private Path fOutput;
	private Pattern toIgnore;
	private Pattern toCover;
	
	public void setBranchesFilePath(Path f) {
		this.fBranches = f;
	}
	
	public void setCoverageFilePath(Path f) {
		this.fCoverage = f;
	}
	
	public void setOutputFilePath(Path f) {
		this.fOutput = f;
	}
	
	public void setBranchesToIgnore(String pattern) {
		this.toIgnore = Pattern.compile(pattern);
		this.toCover = null;
	}
	
	public void setBranchesToCover(String pattern) {
		this.toIgnore = null;
		this.toCover = Pattern.compile(pattern);
	}
	
	public Path getBranchesFilePath() {
		return this.fBranches;
	}
	
	public Path getCoverageFilePath() {
		return this.fCoverage;
	}
	
	public Path getOutputFilePath() {
		return this.fOutput;
	}
	
	public Pattern getBranchesToIgnore() {
		return this.toIgnore;
	}
	
	public Pattern getBranchesToCover() {
		return this.toCover;
	}
}
