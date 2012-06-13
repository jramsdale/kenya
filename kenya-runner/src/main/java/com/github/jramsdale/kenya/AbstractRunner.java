package com.github.jramsdale.kenya;

import java.util.List;

import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.util.artifact.JavaScopes;

import com.google.common.collect.Sets;

public abstract class AbstractRunner {
	
	static final String PROJECT_NAME = "kenya";

	static final String PROPERTY_GAV = PROJECT_NAME + ".gav";
	static final String PROPERTY_JAR = PROJECT_NAME + ".jar";
	static final String PROPERTY_REPO = PROJECT_NAME + ".repo";
	static final String PROPERTY_SCOPE = PROJECT_NAME + ".scope";
	static final String PROPERTY_SETTINGS = PROJECT_NAME + ".settings";

	protected ConsoleHelper consoleHelper = new ConsoleHelper(false);
	
	List<Artifact> artifacts;
	Artifact rootArtifact;

	final Options options = new Options();

	private ClasspathHelper classpathHelper;

	protected AbstractRunner() {
		initializeFromOptions();
		classpathHelper = new ClasspathHelper(options.getRepoPath(), options.getSettingsPath(), options.isVerbose());
		resolveArtifacts();
	}

	protected void initializeFromOptions() {
		final String settingsPath = System.getProperty(PROPERTY_SETTINGS);
		consoleHelper.printPropertyDiscovery(PROPERTY_SETTINGS, settingsPath);
		options.setSettingsPath(settingsPath);

		final String jarPath = System.getProperty(PROPERTY_JAR);
		consoleHelper.printPropertyDiscovery(PROPERTY_JAR, jarPath);
		options.setJarPath(jarPath);

		final String artifactGav = System.getProperty(PROPERTY_GAV);
		consoleHelper.printPropertyDiscovery(PROPERTY_GAV, artifactGav);
		options.setArtifactGav(artifactGav);

		if (jarPath == null && artifactGav == null) {
			throw new KenyaHelpException("Must provide either " + PROJECT_NAME + ".jar or " + PROJECT_NAME
					+ ".gav, but not both");
		}
		if (jarPath != null && artifactGav != null) {
			throw new KenyaHelpException("Cannot provide both " + PROJECT_NAME + ".jar and " + PROJECT_NAME + ".gav");
		}

		final String repoPath = System.getProperty(PROPERTY_REPO);
		consoleHelper.printPropertyDiscovery(PROPERTY_REPO, repoPath);
		options.setRepoPath(repoPath);

		final String scopeProperty = System.getProperty(PROPERTY_SCOPE, JavaScopes.RUNTIME).toLowerCase();
		final String[] scopes = new String[] { JavaScopes.COMPILE, JavaScopes.PROVIDED, JavaScopes.RUNTIME,
				JavaScopes.TEST };
		if (!Sets.newHashSet(scopes).contains(scopeProperty)) {
			throw new KenyaHelpException("System property " + PROPERTY_SCOPE + " must be in " + scopes + " but was '"
					+ scopeProperty + "'");
		}
		consoleHelper.printPropertyDiscovery(PROPERTY_SCOPE, scopeProperty);
	}

	private void resolveArtifacts() {
		if (options.getJarPath() != null) {
			rootArtifact = classpathHelper.createArtifactFromJar(options.getJarPath());
			artifacts = classpathHelper.resolveArtifactsForJarArtifact(rootArtifact);
			artifacts.add(rootArtifact);
			consoleHelper.printIfVerboseOn("    " + rootArtifact.getFile());
		} else {
			rootArtifact = classpathHelper.getRootArtifactForGav(options.getArtifactGav(), options.getScope());
			artifacts = classpathHelper.resolveArtifactsForGav(rootArtifact, options.getScope());
		}
	}

	protected abstract void run(String[] args);

}
