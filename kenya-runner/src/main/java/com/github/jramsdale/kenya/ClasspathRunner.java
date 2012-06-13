package com.github.jramsdale.kenya;

import java.io.File;

import com.google.common.base.Joiner;

public class ClasspathRunner extends AbstractRunner {

	public ClasspathRunner() {
		super();
	}

	@Override
	protected void run(String[] args) {
		System.out.println(Joiner.on(File.pathSeparatorChar).join(artifacts));
	}

	public static void main(String[] args) {
		new ClasspathRunner().run(args);
	}

}
