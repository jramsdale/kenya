package com.github.jramsdale.kenya;

import java.io.PrintStream;

public class ConsoleHelper {

	private boolean isVerbose;

	public ConsoleHelper(boolean isVerbose) {
		this.isVerbose = isVerbose;
	}
	
	public void printPropertyDiscovery(String propertyName, String propertyValue) {
		if (propertyValue != null) {
			printIfVerboseOn("Found system property [" + propertyName + "] with value: " + propertyValue);
		}
	}

	public void printIfVerboseOn(String message) {
		printIfVerboseOn(message, System.out);
	}

	public void printIfVerboseOn(String message, PrintStream stream) {
		if (isVerbose) {
			stream.println(message);
		}
	}

}
