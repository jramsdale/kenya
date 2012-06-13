package com.github.jramsdale.kenya;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.maven.model.DependencyManagement;
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
import org.sonatype.aether.util.filter.ExclusionsDependencyFilter;
import org.sonatype.aether.util.graph.DefaultDependencyNode;
import org.sonatype.guice.bean.binders.WireModule;
import org.sonatype.guice.bean.reflect.URLClassSpace;
import org.sonatype.guice.plexus.shim.PlexusSpaceModule;

import thirdparty.org.apache.maven.repository.internal.DefaultModelResolver;

import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class ClasspathHelper {

	private static final String MAVEN_CENTRAL_REPOSITORY = "http://repo1.maven.org/maven2/";

	private static final String USER_HOME = "user.home";
	private static final String M2 = ".m2";
	private static final String REPOSITORY = "repository";
	private static final String SETTINGS_XML = "settings.xml";

	private Injector injector;

	private RepositorySystem repoSystem;
	private RepositorySystemSession session;
	private List<RemoteRepository> remoteRepositories;

	private ConsoleHelper consoleHelper = new ConsoleHelper(false);

	public ClasspathHelper(final String repoPath, final String settingsPath, final boolean isVerbose) {
		injector = Guice.createInjector(new WireModule(new PlexusSpaceModule(new URLClassSpace(getClass()
				.getClassLoader()))));

		repoSystem = injector.getInstance(RepositorySystem.class);
		session = newSession(repoPath);
		remoteRepositories = getRemoteRepositories(loadSettings(settingsPath));
		consoleHelper = new ConsoleHelper(isVerbose);
	}

	private RepositorySystemSession newSession(final String repoPath) {
		final File localRepositoryFile;
		if (repoPath == null) {
			// TODO: Use M2_HOME unless overridden?
			localRepositoryFile = new File(System.getProperty(USER_HOME), M2 + File.separator + REPOSITORY);
		} else {
			localRepositoryFile = new File(repoPath);
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
		// FIXME: Failures sometimes if update policy isn't set
		session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);
		return session;
	}

	private Settings loadSettings(final String settingsPath) {
		final File userSettingsFile;
		if (settingsPath == null) {
			userSettingsFile = new File(System.getProperty(USER_HOME), M2 + File.separator + SETTINGS_XML);
		} else {
			userSettingsFile = new File(settingsPath);
		}
		if (userSettingsFile.exists()) {
			consoleHelper.printIfVerboseOn("Using settings file: " + userSettingsFile);
		} else {
			consoleHelper.printIfVerboseOn("WARN: Settings file does not exist: " + settingsPath, System.err);
		}
		final DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest()
				.setUserSettingsFile(userSettingsFile);
		try {
			return injector.getInstance(SettingsBuilder.class).build(request).getEffectiveSettings();
		} catch (SettingsBuildingException e) {
			throw new KenyaException("Failed to load settings", e);
		}
	}

	private List<RemoteRepository> getRemoteRepositories(final Settings settings) {
		final List<RemoteRepository> repositories = new ArrayList<RemoteRepository>();
		repositories.add(new RemoteRepository("central", "default", MAVEN_CENTRAL_REPOSITORY));
		final Map<String, Profile> profilesAsMap = settings.getProfilesAsMap();
		for (Profile profile : settings.getProfiles()) {
			// TODO: Replace with real Activation logic
			if (profile.getActivation().isActiveByDefault()) {
				settings.addActiveProfile(profile.getId());
			}
		}
		for (String profileName : settings.getActiveProfiles()) {
			final Profile profile = profilesAsMap.get(profileName);
			for (Repository repo : profile.getRepositories()) {
				final RemoteRepository remoteRepository = new RemoteRepository(repo.getId(), repo.getLayout(),
						repo.getUrl());
				final RepositoryPolicy snapShotPolicy = new RepositoryPolicy(repo.getSnapshots().isEnabled(), repo
						.getSnapshots().getUpdatePolicy(), repo.getSnapshots().getChecksumPolicy());
				snapShotPolicy.setUpdatePolicy(repo.getSnapshots().getUpdatePolicy());
				final RepositoryPolicy releasesPolicy = new RepositoryPolicy(repo.getReleases().isEnabled(), repo
						.getReleases().getUpdatePolicy(), repo.getReleases().getChecksumPolicy());
				releasesPolicy.setUpdatePolicy(repo.getSnapshots().getUpdatePolicy());
				remoteRepository.setPolicy(true, snapShotPolicy);
				remoteRepository.setPolicy(false, releasesPolicy);
				repositories.add(remoteRepository);
			}
		}
		return repositories;
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

	private List<Dependency> convertDependencies(final List<org.apache.maven.model.Dependency> modelDependencies) {
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

	private ModelSource loadPomIntoModelSource(final JarFile jarFile, final ZipEntry pomEntry) {
		InputStreamReader reader = null;
		try {
			reader = new InputStreamReader(jarFile.getInputStream(pomEntry));
			return new StringModelSource(CharStreams.toString(reader));
		} catch (IOException e) {
			throw new KenyaException("Couldn't load pom [" + pomEntry + "] from jar [" + jarFile + "]", e);
		} finally {
			Closeables.closeQuietly(reader);
		}
	}

	Artifact createArtifactFromJar(final String jarPath) {
		if (!new File(jarPath).exists()) {
			throw new KenyaHelpException("Artifact does not exist at path: " + jarPath);
		}
		if (!new File(jarPath).isFile()) {
			throw new KenyaHelpException("Artifact exists but is not a file: " + jarPath);
		}
		try {
			final JarFile jarFile = new JarFile(jarPath);
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
		} catch (IOException e) {
			throw new KenyaException("Could not load jar from file: " + jarPath, e);
		}
		throw new KenyaHelpException("Couldn't find pom.properties in artifact: " + jarPath);
	}

	List<Artifact> resolveArtifactsForGav(final Artifact rootArtifact, final String scope) {
		final CollectRequest collectRequest = new CollectRequest(new Dependency(rootArtifact, scope),
				remoteRepositories);
		final DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
		return resolveArtifactsForDependencyRequest(dependencyRequest, null);
	}

	Artifact getRootArtifactForGav(final String artifactGav, final String scope) {
		final Artifact rootArtifactTemplate = new DefaultArtifact(artifactGav);
		final Artifact rootArtifact;
		try {
			final DefaultDependencyNode dependencyNode = new DefaultDependencyNode(new Dependency(rootArtifactTemplate,
					scope));
			final ArtifactRequest artifactRequest = new ArtifactRequest(dependencyNode);
			artifactRequest.setRepositories(remoteRepositories);
			final ArtifactResult artifactResult = repoSystem.resolveArtifact(session, artifactRequest);
			rootArtifact = artifactResult.getArtifact();
		} catch (ArtifactResolutionException e) {
			throw new KenyaException("Error resolving artifact: " + rootArtifactTemplate, e);
		}
		return rootArtifact;
	}

	List<Artifact> resolveArtifactsForJarArtifact(final Artifact rootArtifact) {
		final Model model = loadModel(rootArtifact);
		if (model.getDependencyManagement() == null) {
			model.setDependencyManagement(new DependencyManagement());
		}
		final CollectRequest collectRequest = new CollectRequest(convertDependencies(model.getDependencies()),
				convertDependencies(model.getDependencyManagement().getDependencies()), remoteRepositories);
		final ExclusionsDependencyFilter filter = new ExclusionsDependencyFilter(Collections.singleton(rootArtifact
				.getGroupId() + ":" + rootArtifact.getArtifactId()));
		final DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, filter)
				.setCollectRequest(collectRequest);
		return resolveArtifactsForDependencyRequest(dependencyRequest, rootArtifact);
	}

	private List<Artifact> resolveArtifactsForDependencyRequest(final DependencyRequest dependencyRequest,
			final Artifact rootArtifactTemplate) {
		try {
			final DependencyResult dependencyResult = repoSystem.resolveDependencies(session, dependencyRequest);
			final List<Artifact> artifacts = new ArrayList<Artifact>();
			consoleHelper.printIfVerboseOn("Classpath entries:");
			for (ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
				artifacts.add(artifactResult.getArtifact());
				consoleHelper.printIfVerboseOn("    " + artifactResult.getArtifact().getFile());
			}
			return artifacts;
		} catch (DependencyResolutionException e) {
			throw new KenyaException("Internal error", e);
		}
	}

//	private MavenExecutionRequest populateFromSettings(final MavenExecutionRequest request, final Settings settings)
//			throws MavenExecutionRequestPopulationException {
//		if (settings == null) {
//			return request;
//		}
//		request.setOffline(settings.isOffline());
//		request.setInteractiveMode(settings.isInteractiveMode());
//		request.setPluginGroups(settings.getPluginGroups());
//		request.setLocalRepositoryPath(settings.getLocalRepository());
//		for (Server server : settings.getServers()) {
//			server = server.clone();
//			request.addServer(server);
//		}
//
//		// <proxies>
//		// <proxy>
//		// <active>true</active>
//		// <protocol>http</protocol>
//		// <host>proxy.somewhere.com</host>
//		// <port>8080</port>
//		// <username>proxyuser</username>
//		// <password>somepassword</password>
//		// <nonProxyHosts>www.google.com|*.somewhere.com</nonProxyHosts>
//		// </proxy>
//		// </proxies>
//
//		for (Proxy proxy : settings.getProxies()) {
//			if (!proxy.isActive()) {
//				continue;
//			}
//			proxy = proxy.clone();
//			request.addProxy(proxy);
//		}
//
//		// <mirrors>
//		// <mirror>
//		// <id>nexus</id>
//		// <mirrorOf>*</mirrorOf>
//		// <url>http://repository.sonatype.org/content/groups/public</url>
//		// </mirror>
//		// </mirrors>
//
//		for (Mirror mirror : settings.getMirrors()) {
//			mirror = mirror.clone();
//			request.addMirror(mirror);
//		}
//		request.setActiveProfiles(settings.getActiveProfiles());
//		for (org.apache.maven.settings.Profile rawProfile : settings.getProfiles()) {
//			request.addProfile(SettingsUtils.convertFromSettingsProfile(rawProfile));
//		}
//		return request;
//	}

}
