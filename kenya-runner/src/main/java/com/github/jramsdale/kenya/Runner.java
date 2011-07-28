package com.github.jramsdale.kenya;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.launcher.Launcher;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.DependencyCollectionException;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.sonatype.aether.util.graph.PreorderNodeListGenerator;
import org.sonatype.guice.bean.binders.WireModule;
import org.sonatype.guice.bean.reflect.URLClassSpace;
import org.sonatype.guice.plexus.shim.PlexusSpaceModule;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class Runner {

	// Setup Logger in init based on kenya.logLevel system property
	private static Logger logger;

	private static final String MAVEN_CENTRAL_REPOSITORY = "http://repo1.maven.org/maven2/";

	private static final String PROJECT_NAME = "kenya";

	static final String PROPERTY_GAV = PROJECT_NAME + ".gav";
	// FIXME: Support type?
	static final String PROPERTY_TYPE = PROJECT_NAME + ".type";
	static final String PROPERTY_JAR = PROJECT_NAME + ".jar";
	static final String PROPERTY_MAIN = PROJECT_NAME + ".main";
	static final String PROPERTY_REPO = PROJECT_NAME + ".repo";
	static final String PROPERTY_SCOPE = PROJECT_NAME + ".scope";
	static final String PROPERTY_LOG_LEVEL = PROJECT_NAME + ".logLevel";
	static final String PROPERTY_SETTINGS = PROJECT_NAME + ".settings";

	private static final String USER_HOME = "user.home";
	private static final String M2 = ".m2";
	private static final String REPOSITORY = "repository";
	private static final String SETTINGS_XML = "settings.xml";

	private static final String APP_REALM = "appRealm";

	private String jarPathProperty;
	private String artifactGavProperty;
	private String repoPathProperty;
	private String mainClassProperty;

	private String javaScope;

	private Launcher launcher = new Launcher();

	private String settingsPath;

	private Injector injector;

	public Runner() {

		printVersion();

		initFromProperties();

		injector = Guice.createInjector(new WireModule(new PlexusSpaceModule(new URLClassSpace(getClass()
				.getClassLoader()))));

		final RepositorySystem repoSystem = injector.getInstance(RepositorySystem.class);
		final RepositorySystemSession session = newSession(repoSystem, repoPathProperty);

		final Settings settings = getSettingsForPath(settingsPath);
		final List<RemoteRepository> remoteRepositories = getRemoteRepositories(settings);

		final Artifact rootArtifact = resolveRootArtifact(repoSystem, session, remoteRepositories);
		final List<Artifact> artifacts = resolveDependencies(rootArtifact, repoSystem, session, remoteRepositories);

		final Attributes manifestAttributes = getManifestAttributes(rootArtifact);
		final String mainClass = getMainClass(mainClassProperty, rootArtifact, manifestAttributes);

		launcher.setWorld(setupClassWorld(artifacts));
		launcher.setAppMain(mainClass, APP_REALM);
	}

	private void printVersion() {
		final String kenyaVersion = getClass().getPackage().getImplementationVersion();
		final String versionMsg = "Kenya Runner, version: " + (kenyaVersion == null ? "<unknown>" : kenyaVersion);
		System.out.println(versionMsg);
	}

	private void initFromProperties() {
		final String logLevelProperty = System.getProperty(PROPERTY_LOG_LEVEL);
		final Level level;
		if (logLevelProperty != null) {
			level = Level.valueOf(logLevelProperty.toUpperCase());
		} else {
			level = Level.ERROR;
		}
		((ConfigurableSimpleLoggerFactory) LoggerFactory.getILoggerFactory()).setDefaultLoggerLevel(level);
		logger = LoggerFactory.getLogger(Runner.class);

		if (logLevelProperty != null) {
			logPropertyDiscovery(PROPERTY_LOG_LEVEL, logLevelProperty);
		}

		settingsPath = System.getProperty(PROPERTY_SETTINGS);
		if (settingsPath != null) {
			logPropertyDiscovery(PROPERTY_SETTINGS, settingsPath);
		}

		jarPathProperty = System.getProperty(PROPERTY_JAR);
		if (jarPathProperty != null) {
			logPropertyDiscovery(PROPERTY_JAR, jarPathProperty);
		}

		artifactGavProperty = System.getProperty(PROPERTY_GAV);
		if (artifactGavProperty != null) {
			logPropertyDiscovery(PROPERTY_GAV, artifactGavProperty);
		}

		repoPathProperty = System.getProperty(PROPERTY_REPO);
		if (repoPathProperty != null) {
			logPropertyDiscovery(PROPERTY_REPO, repoPathProperty);
		}

		final String scopeProperty = System.getProperty(PROPERTY_SCOPE);
		if (scopeProperty != null) {
			final String[] scopes = new String[] { JavaScopes.COMPILE, JavaScopes.PROVIDED, JavaScopes.RUNTIME,
					JavaScopes.TEST };
			for (String scope : scopes) {
				if (scope.equalsIgnoreCase(scopeProperty)) {
					javaScope = scope;
				}
			}
			logPropertyDiscovery(PROPERTY_SCOPE, scopeProperty);
		}

		mainClassProperty = System.getProperty(PROPERTY_MAIN);
		if (mainClassProperty != null) {
			logPropertyDiscovery(PROPERTY_MAIN, mainClassProperty);
		}

		boolean isRunnerPropertyFound = false;
		for (Object key : Collections.list(System.getProperties().keys())) {
			if (((String) key).startsWith(PROJECT_NAME)) {
				isRunnerPropertyFound = true;
				break;
			}
		}
		if (!isRunnerPropertyFound) {
			printHelp();
			throw new KenyaHelpException();
		}
	}

	private void logPropertyDiscovery(String propertyName, String propertyValue) {
		logger.debug("Found system property [{}] with value: {}", propertyName, propertyValue);
	}

	private RepositorySystemSession newSession(RepositorySystem system, String repoProperty) {
		final File localRepositoryFile;
		if (repoProperty == null) {
			localRepositoryFile = new File(System.getProperty(USER_HOME), M2 + File.separator + REPOSITORY);
		} else {
			localRepositoryFile = new File(repoProperty);
			if (!localRepositoryFile.exists()) {
				throw new KenyaHelpException("Provided repository path [" + localRepositoryFile + "] does not exist");
			}
			if (!localRepositoryFile.isDirectory()) {
				throw new KenyaHelpException("Provided repository path [" + localRepositoryFile
						+ "] is not a directory");
			}
		}
		final LocalRepository localRepository = new LocalRepository(localRepositoryFile);
		final MavenRepositorySystemSession session = new MavenRepositorySystemSession();
		session.setLocalRepositoryManager(system.newLocalRepositoryManager(localRepository));
		return session;
	}

	private Artifact getRootArtifactTemplate(String jarPath, String gav) {
		if (jarPath == null && gav == null) {
			throw new KenyaHelpException("Must provide either " + PROJECT_NAME + ".jar or " + PROJECT_NAME
					+ ".gav, but not both");
		}
		if (jarPath != null) {
			if (gav != null) {
				throw new KenyaHelpException("Cannot provide both " + PROJECT_NAME + ".jar and " + PROJECT_NAME
						+ ".gav");
			} else {
				return createArtifactFromJar(jarPath);
			}
		}
		return new DefaultArtifact(gav);
	}

	// FIXME: This mechanism doesn't actually use the provided jar. It just gets the pom.properties.
	private Artifact createArtifactFromJar(String jarPath) {
		final JarFile jarFile;
		try {
			jarFile = new JarFile(jarPath);
		} catch (IOException e) {
			throw new KenyaException("Could not load jar from file: " + jarPath, e);
		}
		for (JarEntry jarEntry : Collections.list(jarFile.entries())) {
			if (jarEntry.getName().startsWith("META-INF/maven/") && jarEntry.getName().endsWith("/pom.properties")) {
				final Properties pomProperties = new Properties();
				try {
					pomProperties.load(jarFile.getInputStream(jarEntry));
					return new DefaultArtifact(pomProperties.getProperty("groupId"),
							pomProperties.getProperty("artifactId"), "jar", pomProperties.getProperty("version"));
				} catch (IOException e) {
					throw new KenyaException("Couldn't load pom.properties from artifact: " + jarPath, e);
				}
			}
		}
		throw new KenyaHelpException("Couldn't find pom.properties in artifact: " + jarPath);
	}

	private List<Artifact> resolveDependencies(Artifact rootArtifact, final RepositorySystem repoSystem,
			final RepositorySystemSession session, final List<RemoteRepository> remoteRepositories) {
		final CollectRequest collectRequest = new CollectRequest(new Dependency(rootArtifact, javaScope),
				remoteRepositories);
		try {
			final DependencyNode node = repoSystem.collectDependencies(session, collectRequest).getRoot();
			repoSystem.resolveDependencies(session, new DependencyRequest(node, null));
			final PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
			node.accept(nlg);
			rootArtifact = node.getDependency().getArtifact();
			// TODO: What to do with unresolved artifacts?
			return nlg.getArtifacts(false);
		} catch (DependencyCollectionException e) {
			throw new KenyaException("Internal error", e);
		} catch (DependencyResolutionException e) {
			throw new KenyaException("Internal error", e);
		}
	}

	private Settings getSettingsForPath(String settingsPath) {
		final File userSettingsFile;
		if (settingsPath == null) {
			userSettingsFile = new File(System.getProperty(USER_HOME), M2 + File.separator + SETTINGS_XML);
		} else {
			userSettingsFile = new File(settingsPath);
		}
		if (userSettingsFile.exists()) {
			logger.info("Using settings file for path: {}", userSettingsFile);
		} else {
			logger.warn("Settings file does not exist: {}", settingsPath);
		}
		final DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
		request.setUserSettingsFile(userSettingsFile);
		try {
			final SettingsBuilder settingsBuilder = injector.getInstance(SettingsBuilder.class);
			return settingsBuilder.build(request).getEffectiveSettings();
		} catch (SettingsBuildingException e) {
			throw new KenyaException("Failed to load settings", e);
		}
	}

	private final List<RemoteRepository> getRemoteRepositories(Settings settings) {
		final List<RemoteRepository> repositories = new ArrayList<RemoteRepository>();
		repositories.add(new RemoteRepository("central", "default", MAVEN_CENTRAL_REPOSITORY));
		@SuppressWarnings("unchecked")
		final Map<String, Profile> profilesAsMap = settings.getProfilesAsMap();
		for (String profileName : settings.getActiveProfiles()) {
			final Profile profile = profilesAsMap.get(profileName);
			for (Repository repo : profile.getRepositories()) {
				final RemoteRepository remoteRepository = new RemoteRepository(repo.getId(), repo.getLayout(),
						repo.getUrl());
				final RepositoryPolicy snapShotPolicy = new RepositoryPolicy(repo.getSnapshots().isEnabled(), repo
						.getSnapshots().getUpdatePolicy(), repo.getSnapshots().getChecksumPolicy());
				final RepositoryPolicy releasesPolicy = new RepositoryPolicy(repo.getReleases().isEnabled(), repo
						.getReleases().getUpdatePolicy(), repo.getReleases().getChecksumPolicy());
				remoteRepository.setPolicy(true, snapShotPolicy);
				remoteRepository.setPolicy(false, releasesPolicy);
				repositories.add(remoteRepository);
			}
		}
		return repositories;
	}

	private Artifact resolveRootArtifact(final RepositorySystem repoSystem, final RepositorySystemSession session,
			List<RemoteRepository> remoteRepositories) {
		final Artifact rootArtifactTemplate = getRootArtifactTemplate(jarPathProperty, artifactGavProperty);
		try {
			final ArtifactRequest artifactRequest = new ArtifactRequest(rootArtifactTemplate, remoteRepositories, null);
			final ArtifactResult artifactResult = repoSystem.resolveArtifact(session, artifactRequest);
			return artifactResult.getArtifact();
		} catch (ArtifactResolutionException e) {
			throw new KenyaException("Error resolving artifact: " + rootArtifactTemplate, e);
		}
	}

	private Attributes getManifestAttributes(Artifact rootArtifact) {
		final File artifactFile = rootArtifact.getFile();
		try {
			final JarFile jarFile = new JarFile(artifactFile);
			return jarFile.getManifest().getMainAttributes();
		} catch (IOException e) {
			throw new KenyaException("Could not load jar manifest from file: " + artifactFile, e);
		}
	}

	private String getMainClass(String mainClassFromProperty, Artifact rootArtifact, Attributes manifestAttributes) {
		if (mainClassFromProperty != null) {
			return mainClassFromProperty;
		}
		final String mainClassFromManifest = (String) manifestAttributes.get(Attributes.Name.MAIN_CLASS);
		if (mainClassFromManifest == null) {
			throw new KenyaHelpException("Artifact didn't have a Main-Class attribute in MANIFEST.MF: "
					+ rootArtifact.getFile());
		} else {
			return mainClassFromManifest;
		}
	}

	private ClassWorld setupClassWorld(List<Artifact> artifacts) {
		final ClassWorld classWorld = new ClassWorld();
		try {
			final ClassRealm realm = classWorld.newRealm(APP_REALM);
			for (Artifact artifact : artifacts) {
				realm.addURL(artifact.getFile().toURI().toURL());
			}
		} catch (DuplicateRealmException e) {
			throw new KenyaException("Internal error", e);
		} catch (MalformedURLException e) {
			throw new KenyaException("Internal error", e);
		}
		return classWorld;
	}

	private void run(String[] args) {
		try {
			launcher.launch(args);
		} catch (NoSuchRealmException e) {
			throw new KenyaException("Internal error", e);
		} catch (ClassNotFoundException e) {
			throw new KenyaException("Main class [" + launcher.getMainClassName() + "] was not found", e);
		} catch (IllegalAccessException e) {
			throw new KenyaException("Internal error", e);
		} catch (InvocationTargetException e) {
			throw new KenyaException("Internal error", e);
		} catch (NoSuchMethodException e) {
			throw new KenyaException("No main method for class [" + launcher.getMainClassName() + "]", e);
		}
	}

	private static void printHelp() {
		try {
			final InputStream helpStream = Runner.class.getClassLoader().getResourceAsStream("help.txt");
			final BufferedReader reader = new BufferedReader(
					new InputStreamReader(helpStream, Charset.forName("UTF-8")));
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}
		} catch (IOException e) {
			throw new KenyaException("Internal error", e);
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			try {
				new Runner().run(args);
			} catch (KenyaHelpException e) {
				if (e.getMessage() != null) {
					logger.error(e.getMessage());
				}
			}
		} catch (KenyaException e) {
			if (logger.isDebugEnabled() && e.getCause() != null) {
				logger.error(e.getMessage(), e.getCause());
			} else {
				logger.error(e.getMessage());
			}
		}
	}

}
