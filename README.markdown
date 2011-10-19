# Kenya #

Kenya is a Java application runner that resolves artifacts and creates a runtime classpath using 
[Maven](http://maven.apache.org "Apache Maven") metadata. Thus, a simple command and a single
jar can result in the downloading and running of a Java application whose dependencies are available
in known Maven repositories and whose metadata describes its runtime classpath.

Kenya is provided as a self-contained runnable jar. System properties are used to identify a Maven artifact 
containing a Java class with a main method. Kenya then runs the main class, assembling a runtime classpath 
from the artifact's Maven pom. The arguments provided to Kenya are passed along unchanged to the application.

## Download Kenya ##

To use Kenya, first download [kenya.jar](https://github.com/downloads/jramsdale/kenya/kenya.jar).
Nothing needs to be installed as kenya.jar contains everything required to run Kenya.

## Running Kenya ##

Running Kenya is simple since Kenya's own classpath is contained in one jar and the user's application
classpath is obtained from Maven. There are two ways to identify the artifact containing the
application main class to Kenya: using Maven GAV Coordinates, or using a provided jar file. 

Note that with either method only the artifact containing the main class need be provided. Dependencies
are discovered using the Maven pom.

The example below starts a sample web app provided by the Jetty developers. Running the application
starts a file server for the current working directory. After running the command, browse to
[http://localhost:8080](http://localhost:8080) to see it in action. `CTRL-C` at the terminal will stop 
the server.

### Using Maven GAV Coordinates ###

Maven artifacts are identified using a coordinate system colloquially known as GAV, which stands for 
_groupId_, _artifactId_, and _version_. The easiest way to use Kenya is to provide it the GAV coordinates
via the `kenya.gav` system property (identified to the java command with `-D` prefix). For example:

    java -Dkenya.gav=org.eclipse.jetty:example-jetty-embedded:8.0.0.M0 -Dkenya.main=org.eclipse.jetty.embedded.FileServer -jar kenya.jar

Note that the system properties must come before `-jar kenya.jar` in the commandline.

Also, the `kenya.main` property is only required if the artifact doesn't have a `Main-Class` attribute
in the artifact's `MANIFEST.MF`.

### Using a Provided Jar ###

Using a provided jar is almost the same as using Maven GAV coordinates. Simply change the system property
to `kenya.jar` and provide the path to the jar containing the main class. 

    java -Dkenya.jar=example-jetty-embedded-8.0.0.M2.jar -Dkenya.main=org.eclipse.jetty.embedded.FileServer -jar kenya.jar

As with GAV use the `kenya.main` property is only required if the artifact doesn't have a `Main-Class` attribute
in the artifact's `MANIFEST.MF`.

### Providing Application Arguments ###

Any arguments passed to Kenya are passed along to the user application without modification.

## Getting Help ##

When kenya.jar is run without Kenya-specific system properties, Kenya displays its help:

    java -jar kenya.jar

The help text:

    Kenya Runner, version: 1.0.0-SNAPSHOT
    Kenya Runner is a utility that runs Java applications by assembling a
    runtime classpath using Maven metadata associated with jars. This dramatically
    simplifies the commandline required to run Java applications comprising
    multiple jars. More info at: https://github.com/jramsdale/kenya

    Usage: java [options...] -jar kenya.jar [args...]

    where options include:
        -Dkenya.gav=<gav>   
                Maven artifact address in groupId:artifactId:version format. 
                Cannot be used with kenya.jar option.
           
        -Dkenya.jar=<jarPath>
                Jar file to run. Must contain a valid pom. Cannot be used with
                kenya.gav option.
           
        -Dkenya.main=<mainClass>
                Fully qualified name of main class to run
                Defaults to value of Main-Class attribute in MANIFEST.MF
                
        -Dkenya.verbose
    			Increase Kenya output verbosity.
    			
        -Dkenya.scope=<mavenScope>
                Maven classpath scope, one of "compile", "provided", "runtime"
                or "test"
                Defaults to: compile
    
        -Dkenya.logLevel=<logLevel>
                Log level for Kenya output, one of: "TRACE", "DEBUG", "INFO", 
                "WARN", or "ERROR"
                Defaults to: INFO
    
        -Dkenya.repo=<repoDirPath>
                Path to local Maven repository.
                Defaults to: $HOME/.m2/repository
    
        -Dkenya.settings=<settingsPath>
                Path to user's settings.xml file.
                Defaults to: $HOME/.m2/settings.xml

    and args are passed through to the referenced application
    

