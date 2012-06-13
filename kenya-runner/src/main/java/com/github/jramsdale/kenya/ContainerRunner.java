package com.github.jramsdale.kenya;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.launcher.Launcher;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.gossip.Gossip;
import org.sonatype.gossip.Level;

public class ContainerRunner extends AbstractRunner {

	// Setup Logger in initialize() based on kenya.logLevel system property
	private static Logger logger;

	private static final String APP_REALM = "appRealm";

	static final String PROPERTY_MAIN = PROJECT_NAME + ".main";
	static final String PROPERTY_VERBOSE = PROJECT_NAME + ".verbose";
	static final String PROPERTY_LOG_LEVEL = PROJECT_NAME + ".logLevel";

	public ContainerRunner() {
		super();
	}

	@Override
	protected void initializeFromOptions() {
		final boolean isVerboseProperty = Boolean.getBoolean(System.getProperty(PROPERTY_VERBOSE, "false"));
		consoleHelper = new ConsoleHelper(isVerboseProperty);
		consoleHelper.printIfVerboseOn("Found system property [" + PROPERTY_VERBOSE + "]. Verbose output enabled.");
		options.setVerbose(isVerboseProperty);

		final String logLevelProperty = System.getProperty(PROPERTY_LOG_LEVEL, Level.ERROR.name());
		final Level level = Level.valueOf(logLevelProperty.toUpperCase());
		Gossip.getInstance().getRoot().setLevel(level);
		logger = LoggerFactory.getLogger(AbstractRunner.class);
		consoleHelper.printPropertyDiscovery(PROPERTY_LOG_LEVEL, level.name());

		super.initializeFromOptions();

		final String mainClass = System.getProperty(PROPERTY_MAIN);
		consoleHelper.printPropertyDiscovery(PROPERTY_MAIN, mainClass);
		options.setMainClass(mainClass);
	}

	private Launcher createLauncher() {
		final String mainClass = getMainClass(options.getMainClass());
		final Launcher launcher = new Launcher();
		try {
			final ClassWorld classWorld = new ClassWorld();
			final ClassRealm realm = classWorld.newRealm(APP_REALM);
			for (Artifact artifact : artifacts) {
				realm.addURL(artifact.getFile().toURI().toURL());
			}
			launcher.setSystemClassLoader(realm);
			launcher.setWorld(classWorld);
			launcher.setAppMain(mainClass, APP_REALM);
			return launcher;
		} catch (DuplicateRealmException e) {
			throw new KenyaException("Internal error", e);
		} catch (MalformedURLException e) {
			throw new KenyaException("Internal error", e);
		}
	}

	private String getMainClassNameFromManifest() {
		final File artifactFile = rootArtifact.getFile();
		try {
			final Attributes manifestAttributes = new JarFile(artifactFile).getManifest().getMainAttributes();
			return (String) manifestAttributes.get(Attributes.Name.MAIN_CLASS);
		} catch (IOException e) {
			throw new KenyaException("Could not load jar manifest from file: " + artifactFile, e);
		}
	}

	private String getMainClass(final String mainClassFromProperty) {
		if (mainClassFromProperty != null) {
			consoleHelper.printIfVerboseOn("Running provided main class: " + mainClassFromProperty);
			return mainClassFromProperty;
		}
		final String mainClassFromManifest = getMainClassNameFromManifest();
		if (mainClassFromManifest == null) {
			throw new KenyaHelpException("Artifact didn't have a Main-Class attribute in MANIFEST.MF: "
					+ rootArtifact.getFile());
		} else {
			consoleHelper.printIfVerboseOn("Using main class discovered in MANIFEST.MF: " + mainClassFromManifest);
			return mainClassFromManifest;
		}
	}

	protected void run(final String[] args) {
		removeKenyaSystemProperties();
		final Launcher launcher = createLauncher();
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

	private void removeKenyaSystemProperties() {
		for (Entry<?, ?> entry : System.getProperties().entrySet().toArray(new Map.Entry[] {})) {
			if (entry.getKey().toString().startsWith(PROJECT_NAME)) {
				System.getProperties().remove(entry.getKey());
			}
		}
	}

	public static void printHelp() {
		try {
			final InputStream helpStream = ContainerRunner.class.getClassLoader().getResourceAsStream("help.txt");
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
		final String kenyaVersion = ContainerRunner.class.getPackage().getImplementationVersion();
		final String versionMsg = "Kenya Runner, version: " + (kenyaVersion == null ? "<unknown>" : kenyaVersion);
		System.out.println(versionMsg);
		try {
			try {
				new ContainerRunner().run(args);
			} catch (KenyaHelpException e) {
				if (e.getMessage() != null) {
					logger.error(e.getMessage());
				}
				printHelp();
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
