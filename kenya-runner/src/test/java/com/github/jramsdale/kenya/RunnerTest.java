package com.github.jramsdale.kenya;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.output.TeeOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.jramsdale.kenya.fakeapp.FakeApp;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

public class RunnerTest {

	private static final String LINE_SEPARATOR = System.getProperty("line.separator");

	private final ByteArrayOutputStream console = new ByteArrayOutputStream();
	private final ByteArrayOutputStream errConsole = new ByteArrayOutputStream();
	private Properties savedProperties;

	@Before
	public void setUpStreams() {
		System.setOut(new PrintStream(new TeeOutputStream(System.out, console)));
		System.setErr(new PrintStream(new TeeOutputStream(System.err, errConsole)));
		savedProperties = System.getProperties();
	}

	@After
	public void cleanUpStreams() {
		System.setOut(null);
		System.setErr(null);
		System.setProperties(savedProperties);
	}

	@Test
	public void testRunner() {
		// FIXME: Need a released test subject so test can run reliably
		System.setProperty("kenya.gav", "com.github.jramsdale.kenya:kenya-fakeapp:1.0.0-SNAPSHOT");
		System.setProperty("kenya.main", FakeApp.class.getCanonicalName());
		ContainerRunner.main(new String[] {});
		final List<String> lines = Lists.newArrayList(Splitter.on(LINE_SEPARATOR).omitEmptyStrings().split(console.toString()));
		assertTrue(lines.get(0).startsWith("Kenya Runner"));
		assertEquals("Classpath:", lines.get(1));
		final List<String> dependencies = lines.subList(2, lines.size());
		assertEquals(3, dependencies.size());
	}

}
