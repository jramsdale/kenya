package com.github.jramsdale.kenya.fakeapp;

import java.net.URL;
import java.net.URLClassLoader;

public class FakeApp {

	static void printClasspath() {
		final URLClassLoader systemClassLoader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
		System.out.println("Classpath:");
		for (URL url : systemClassLoader.getURLs()) {
			System.out.println("\t" + url);
		}
	}
	
	public static void main(String[] args) {
		printClasspath();
	}
	
}
