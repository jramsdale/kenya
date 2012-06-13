package com.github.jramsdale.kenya;

public class Options {

	private boolean isVerbose;
	private String settingsPath;
	private String jarPath;
	private String artifactGav;
	private String repoPath;
	private String mainClass;
	private String scope;

	public boolean isVerbose() {
		return isVerbose;
	}

	public void setVerbose(boolean isVerbose) {
		this.isVerbose = isVerbose;
	}

	public String getJarPath() {
		return jarPath;
	}

	public void setJarPath(String jarPath) {
		this.jarPath = jarPath;
	}

	public String getSettingsPath() {
		return settingsPath;
	}

	public void setSettingsPath(String settingsPath) {
		this.settingsPath = settingsPath;
	}

	public String getArtifactGav() {
		return artifactGav;
	}

	public void setArtifactGav(String artifactGav) {
		this.artifactGav = artifactGav;
	}

	public String getRepoPath() {
		return repoPath;
	}

	public void setRepoPath(String repoPath) {
		this.repoPath = repoPath;
	}

	public String getMainClass() {
		return mainClass;
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	public String getScope() {
		return scope;
	}
	
	public void setScope(String scope) {
		this.scope = scope;
	}

}
