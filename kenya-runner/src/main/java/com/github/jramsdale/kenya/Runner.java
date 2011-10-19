package com.github.jramsdale.kenya;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
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
import java.util.zip.ZipEntry;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.building.StringModelSource;
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
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.impl.ArtifactResolver;
import org.sonatype.aether.impl.RemoteRepositoryManager;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.resolution.DependencyResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.sonatype.aether.util.filter.ExclusionsDependencyFilter;
import org.sonatype.aether.util.graph.DefaultDependencyNode;
import org.sonatype.guice.bean.binders.WireModule;
import org.sonatype.guice.bean.reflect.URLClassSpace;
import org.sonatype.guice.plexus.shim.PlexusSpaceModule;

import thirdparty.org.apache.maven.repository.internal.DefaultModelResolver;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class Runner {

	// Setup Logger in init based on kenya.logLevel system property
	private static Logger logger;

	private static final String MAVEN_CENTRAL_REPOSITORY = "http://repo1.maven.org/maven2/";

	private static final String PROJECT_NAME = "kenya";

	static final String PROPERTY_GAV = PROJECT_NAME + ".gav";
	static final String PROPERTY_JAR = PROJECT_NAME + ".jar";
	static final String PROPERTY_MAIN = PROJECT_NAME + ".main";
	static final String PROPERTY_REPO = PROJECT_NAME + ".repo";
	static final String PROPERTY_SCOPE = PROJECT_NAME + ".scope";
	static final String PROPERTY_VERBOSE = PROJECT_NAME + ".verbose";
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
	private String settingsPathProperty;

	private boolean isVerbose = false;

	private String javaScope;

	private final Launcher launcher;

	private Injector injector;

	private RepositorySystem repoSystem;
	private RepositorySystemSession session;
	private List<RemoteRepository> remoteRepositories;

	public Runner() {

		printVersion();

		initFromProperties();

		injector = Guice.createInjector(new WireModule(new PlexusSpaceModule(new URLClassSpace(getClass()
				.getClassLoader()))));

		repoSystem = injector.getInstance(RepositorySystem.class);
		session = newSession();
		remoteRepositories = getRemoteRepositories(loadSettings());

		if (jarPathProperty != null) {
			launcher = createLauncherForJar();
		} else {
			launcher = createLauncherForGav();
		}
	}

	private void printVersion() {
		final String kenyaVersion = getClass().getPackage().getImplementationVersion();
		final String versionMsg = "Kenya Runner, version: " + (kenyaVersion == null ? "<unknown>" : kenyaVersion);
		System.out.println(versionMsg);
	}

	private void initFromProperties() {
		// Detect verbose first
		final String isVerboseProperty = System.getProperty(PROPERTY_VERBOSE);
		if (isVerboseProperty != null) {
			isVerbose = true;
			printIfVerboseOn("Found system property [" + PROPERTY_VERBOSE + "]. Verbose output enabled.");
		}

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
			printPropertyDiscovery(PROPERTY_LOG_LEVEL, level.name());
		}

		settingsPathProperty = System.getProperty(PROPERTY_SETTINGS);
		if (settingsPathProperty != null) {
			printPropertyDiscovery(PROPERTY_SETTINGS, settingsPathProperty);
		}

		jarPathProperty = System.getProperty(PROPERTY_JAR);
		if (jarPathProperty != null) {
			printPropertyDiscovery(PROPERTY_JAR, jarPathProperty);
		}

		artifactGavProperty = System.getProperty(PROPERTY_GAV);
		if (artifactGavProperty != null) {
			printPropertyDiscovery(PROPERTY_GAV, artifactGavProperty);
		}

		if (jarPathProperty == null && artifactGavProperty == null) {
			throw new KenyaHelpException("Must provide either " + PROJECT_NAME + ".jar or " + PROJECT_NAME
					+ ".gav, but not both");
		}
		if (jarPathProperty != null && artifactGavProperty != null) {
			throw new KenyaHelpException("Cannot provide both " + PROJECT_NAME + ".jar and " + PROJECT_NAME + ".gav");
		}
		repoPathProperty = System.getProperty(PROPERTY_REPO);
		if (repoPathProperty != null) {
			printPropertyDiscovery(PROPERTY_REPO, repoPathProperty);
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
			printPropertyDiscovery(PROPERTY_SCOPE, scopeProperty);
		}

		mainClassProperty = System.getProperty(PROPERTY_MAIN);
		if (mainClassProperty != null) {
			printPropertyDiscovery(PROPERTY_MAIN, mainClassProperty);
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

	private void printPropertyDiscovery(String propertyName, String propertyValue) {
		printIfVerboseOn("Found system property [" + propertyName + "] with value: " + propertyValue);
	}

	private void printIfVerboseOn(String message) {
		printIfVerboseOn(message, System.out);
	}

	private void printIfVerboseOn(String message, PrintStream stream) {
		if (isVerbose) {
			stream.println(message);
		}
	}

	private RepositorySystemSession newSession() {
		final File localRepositoryFile;
		if (repoPathProperty == null) {
			localRepositoryFile = new File(System.getProperty(USER_HOME), M2 + File.separator + REPOSITORY);
		} else {
			localRepositoryFile = new File(repoPathProperty);
			if (!localRepositoryFile.exists()) {
				throw new KenyaHelpException("Provided repository path [" + localRepositoryFile + "] does not exist");
			}
			if (!localRepositoryFile.isDirectory()) {
				throw new KenyaHelpException("Provided repository path [" + localRepositoryFile
						+ "] is not a directory");
			}
		}
		final MavenRepositorySystemSession session = new MavenRepositorySystemSession();
		final LocalRepository localRepository = new LocalRepository(localRepositoryFile);
		session.setLocalRepositoryManager(repoSystem.newLocalRepositoryManager(localRepository));
		return session;
	}

	private Settings loadSettings() {
		final File userSettingsFile;
		if (settingsPathProperty == null) {
			userSettingsFile = new File(System.getProperty(USER_HOME), M2 + File.separator + SETTINGS_XML);
		} else {
			userSettingsFile = new File(settingsPathProperty);
		}
		if (userSettingsFile.exists()) {
			printIfVerboseOn("Using settings file: " + userSettingsFile);
		} else {
			printIfVerboseOn("WARN: Settings file does not exist: " + settingsPathProperty, System.err);
		}
		final DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest()
				.setUserSettingsFile(userSettingsFile);
		try {
			return injector.getInstance(SettingsBuilder.class).build(request).getEffectiveSettings();
		} catch (SettingsBuildingException e) {
			throw new KenyaException("Failed to load settings", e);
		}
	}

	private final List<RemoteRepository> getRemoteRepositories(Settings settings) {
		final List<RemoteRepository> repositories = new ArrayList<RemoteRepository>();
		repositories.add(new RemoteRepository("central", "default", MAVEN_CENTRAL_REPOSITORY));
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

	private Launcher createLauncherForJar() {
		final Artifact rootArtifact = createArtifactFromJar(jarPathProperty);
		final Model model = loadModel(rootArtifact);
		final CollectRequest collectRequest = new CollectRequest(convertDependencies(model.getDependencies()),
				convertDependencies(model.getDependencyManagement().getDependencies()), remoteRepositories);
		final ExclusionsDependencyFilter filter = new ExclusionsDependencyFilter(Collections.singleton(rootArtifact
				.getGroupId() + ":" + rootArtifact.getArtifactId()));
		final DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, filter)
				.setCollectRequest(collectRequest);
		return createLauncherForDependencyRequest(repoSystem, session, dependencyRequest, rootArtifact);
	}

	private Model loadModel(final Artifact rootArtifact) {
		try {
			final JarFile jarFile = new JarFile(rootArtifact.getFile());
			final ZipEntry pomEntry = jarFile.getEntry("META-INF/maven/" + rootArtifact.getGroupId() + "/"
					+ rootArtifact.getArtifactId() + "/pom.xml");
			final ModelBuildingRequest request = new DefaultModelBuildingRequest();
			final ArtifactResolver resolver = injector.getInstance(ArtifactResolver.class);
			final RemoteRepositoryManager manager = injector.getInstance(RemoteRepositoryManager.class);
			request.setModelResolver(new DefaultModelResolver(session, null, null, resolver, manager,
					remoteRepositories));
			request.setModelSource(loadPomIntoModelSource(jarFile, pomEntry));
			request.setProcessPlugins(false);
			request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
			final ModelBuilder modelBuilder = injector.getInstance(DefaultModelBuilderFactory.class).newInstance();
			return modelBuilder.build(request).getEffectiveModel();
		} catch (IOException e) {
			throw new KenyaException("Couldn't load pom from jar: " + rootArtifact.getFile(), e);
		} catch (ModelBuildingException e) {
			throw new KenyaException("Couldn't build pom from jar: " + rootArtifact.getFile(), e);
		}
	}

	private List<Dependency> convertDependencies(List<org.apache.maven.model.Dependency> modelDependencies) {
		final List<Dependency> dependencies = new ArrayList<Dependency>();
		if (modelDependencies != null) {
			for (org.apache.maven.model.Dependency dependency : modelDependencies) {
				final Artifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
						dependency.getClassifier(), dependency.getType(), dependency.getVersion());
				dependencies.add(new Dependency(artifact, dependency.getScope()));
			}
		}
		return dependencies;
	}

	private ModelSource loadPomIntoModelSource(JarFile jarFile, ZipEntry pomEntry) {
		final StringBuilder pom = new StringBuilder();
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(jarFile.getInputStream(pomEntry)));
			String line;
			while ((line = in.readLine()) != null) {
				pom.append(line);
			}
		} catch (IOException e) {
			throw new KenyaException("Couldn't load pom [" + pomEntry + "] from jar [" + jarFile + "]", e);
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				throw new KenyaException("Failed to close pom", e);
			}
		}
		return new StringModelSource(pom.toString());
	}

	private Artifact createArtifactFromJar(String jarPath) {
		final JarFile jarFile;
		try {
			jarFile = new JarFile(jarPath);
		} catch (IOException e) {
			throw new KenyaException("Could not load jar from file: " + jarPath, e);
		}
		for (JarEntry jarEntry : Collections.list(jarFile.entries())) {
			if (jarEntry.getName().startsWith("META-INF/maven/") && jarEntry.getName().endsWith("/pom.properties")) {
				try {
					final Properties pomProperties = new Properties();
					pomProperties.load(jarFile.getInputStream(jarEntry));
					final DefaultArtifact artifact = new DefaultArtifact(pomProperties.getProperty("groupId"),
							pomProperties.getProperty("artifactId"), "jar", pomProperties.getProperty("version"));
					return artifact.setFile(new File(jarPath));
				} catch (IOException e) {
					throw new KenyaException("Couldn't load pom.properties from artifact: " + jarPath, e);
				}
			}
		}
		throw new KenyaHelpException("Couldn't find pom.properties in artifact: " + jarPath);
	}

	private Launcher createLauncherForGav() {
		final Artifact rootArtifactTemplate = new DefaultArtifact(artifactGavProperty);
		final Artifact rootArtifact;
		try {
			final DefaultDependencyNode dependencyNode = new DefaultDependencyNode(new Dependency(rootArtifactTemplate,
					javaScope));
			final ArtifactRequest artifactRequest = new ArtifactRequest(dependencyNode);
			artifactRequest.setRepositories(remoteRepositories);
			final ArtifactResult artifactResult = repoSystem.resolveArtifact(session, artifactRequest);
			rootArtifact = artifactResult.getArtifact();
		} catch (ArtifactResolutionException e) {
			throw new KenyaException("Error resolving artifact: " + rootArtifactTemplate, e);
		}
		final CollectRequest collectRequest = new CollectRequest(new Dependency(rootArtifact, javaScope),
				remoteRepositories);
		final DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
		return createLauncherForDependencyRequest(repoSystem, session, dependencyRequest, null);
	}

	private Launcher createLauncherForDependencyRequest(RepositorySystem repoSystem, RepositorySystemSession session,
			final DependencyRequest dependencyRequest, Artifact rootArtifactTemplate) {
		try {
			final DependencyResult dependencyResult = repoSystem.resolveDependencies(session, dependencyRequest);
			final List<Artifact> artifacts = new ArrayList<Artifact>();
			printIfVerboseOn("Classpath entries:");
			for (ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
				artifacts.add(artifactResult.getArtifact());
				printIfVerboseOn("    " + artifactResult.getArtifact().getFile());
			}
			final Artifact rootArtifact;
			if (rootArtifactTemplate != null) {
				artifacts.add(rootArtifactTemplate);
				printIfVerboseOn("    " + rootArtifactTemplate.getFile());
				rootArtifact = rootArtifactTemplate;
			} else {
				rootArtifact = dependencyResult.getRoot().getDependency().getArtifact();
			}

			final Attributes manifestAttributes = getManifestAttributes(rootArtifact);
			final String mainClass = getMainClass(mainClassProperty, rootArtifact, manifestAttributes);

			final Launcher launcher = new Launcher();
			launcher.setWorld(classWorldForArtifacts(artifacts));
			launcher.setAppMain(mainClass, APP_REALM);
			return launcher;
		} catch (DependencyResolutionException e) {
			throw new KenyaException("Internal error", e);
		}
	}

	private ClassWorld classWorldForArtifacts(final List<Artifact> artifacts) {
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
			printIfVerboseOn("Running provided main class: " + mainClassFromProperty);
			return mainClassFromProperty;
		}
		final String mainClassFromManifest = (String) manifestAttributes.get(Attributes.Name.MAIN_CLASS);
		if (mainClassFromManifest == null) {
			throw new KenyaHelpException("Artifact didn't have a Main-Class attribute in MANIFEST.MF: "
					+ rootArtifact.getFile());
		} else {
			printIfVerboseOn("Using main class discovered in MANIFEST.MF: " + mainClassFromManifest);
			return mainClassFromManifest;
		}
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
