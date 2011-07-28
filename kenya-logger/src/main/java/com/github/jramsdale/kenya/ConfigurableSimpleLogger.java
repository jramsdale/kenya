package com.github.jramsdale.kenya;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

public class ConfigurableSimpleLogger extends MarkerIgnoringBase {

	private static final long serialVersionUID = 1L;

	private Level logLevel;

	private static long startTime = System.currentTimeMillis();

	public static final String LINE_SEPARATOR = System.getProperty("line.separator");

	ConfigurableSimpleLogger(String name, Level logLevel) {
		this.name = name;
		this.logLevel = logLevel;
	}
	
	public void setLevel(Level level) {
		logLevel = level;
	}

	public boolean isTraceEnabled() {
		return Level.TRACE.ordinal() >= logLevel.ordinal();
	}

	public void trace(String msg) {
		if (isTraceEnabled()) {
			log(Level.TRACE, msg, null);
		}
	}

	public void trace(String format, Object arg) {
		if (isTraceEnabled()) {
			formatAndLog(Level.TRACE, format, arg, null);
		}
	}

	public void trace(String format, Object arg1, Object arg2) {
		if (isTraceEnabled()) {
			formatAndLog(Level.TRACE, format, arg1, arg2);
		}
	}

	public void trace(String format, Object[] argArray) {
		if (isTraceEnabled()) {
			formatAndLog(Level.TRACE, format, argArray);
		}
	}

	public void trace(String msg, Throwable t) {
		if (isTraceEnabled()) {
			log(Level.TRACE, msg, t);
		}
	}

	public boolean isDebugEnabled() {
		return Level.DEBUG.ordinal() >= logLevel.ordinal();
	}

	public void debug(String msg) {
		if (isDebugEnabled()) {
			log(Level.DEBUG, msg, null);
		}
	}

	public void debug(String format, Object arg) {
		if (isDebugEnabled()) {
			formatAndLog(Level.DEBUG, format, arg, null);
		}
	}

	public void debug(String format, Object arg1, Object arg2) {
		if (isDebugEnabled()) {
			formatAndLog(Level.DEBUG, format, arg1, arg2);
		}
	}

	public void debug(String format, Object[] argArray) {
		if (isDebugEnabled()) {
			formatAndLog(Level.DEBUG, format, argArray);
		}
	}

	public void debug(String msg, Throwable t) {
		if (isDebugEnabled()) {
			log(Level.DEBUG, msg, t);
		}
	}

	private void log(Level level, String message, Throwable t) {
		StringBuffer buf = new StringBuffer();

		long millis = System.currentTimeMillis();
		buf.append(millis - startTime);

		buf.append(" [");
		buf.append(Thread.currentThread().getName());
		buf.append("] ");

		buf.append(level.name());
		buf.append(" ");

		buf.append(name);
		buf.append(" - ");

		buf.append(message);

		buf.append(LINE_SEPARATOR);

		System.err.print(buf.toString());
		if (t != null) {
			t.printStackTrace(System.err);
		}
		System.err.flush();
	}

	private void formatAndLog(Level level, String format, Object arg1, Object arg2) {
		FormattingTuple tp = MessageFormatter.format(format, arg1, arg2);
		log(level, tp.getMessage(), tp.getThrowable());
	}

	private void formatAndLog(Level level, String format, Object[] argArray) {
		FormattingTuple tp = MessageFormatter.arrayFormat(format, argArray);
		log(level, tp.getMessage(), tp.getThrowable());
	}

	public boolean isInfoEnabled() {
		return Level.INFO.ordinal() >= logLevel.ordinal();
	}

	public void info(String msg) {
		if (isInfoEnabled()) {
			log(Level.INFO, msg, null);
		}
	}

	public void info(String format, Object arg) {
		if (isInfoEnabled()) {
			formatAndLog(Level.INFO, format, arg, null);
		}
	}

	public void info(String format, Object arg1, Object arg2) {
		if (isInfoEnabled()) {
			formatAndLog(Level.INFO, format, arg1, arg2);
		}
	}

	public void info(String format, Object[] argArray) {
		if (isInfoEnabled()) {
			formatAndLog(Level.INFO, format, argArray);
		}
	}

	public void info(String msg, Throwable t) {
		if (isInfoEnabled()) {
			log(Level.INFO, msg, t);
		}
	}

	public boolean isWarnEnabled() {
		return Level.WARN.ordinal() >= logLevel.ordinal();
	}

	public void warn(String msg) {
		if (isWarnEnabled()) {
			log(Level.WARN, msg, null);
		}
	}

	public void warn(String format, Object arg) {
		if (isWarnEnabled()) {
			formatAndLog(Level.WARN, format, arg, null);
		}
	}

	public void warn(String format, Object arg1, Object arg2) {
		if (isWarnEnabled()) {
			formatAndLog(Level.WARN, format, arg1, arg2);
		}
	}

	public void warn(String format, Object[] argArray) {
		if (isWarnEnabled()) {
			formatAndLog(Level.WARN, format, argArray);
		}
	}

	public void warn(String msg, Throwable t) {
		if (isWarnEnabled()) {
			log(Level.WARN, msg, t);
		}
	}

	public boolean isErrorEnabled() {
		return Level.ERROR.ordinal() >= logLevel.ordinal();
	}

	public void error(String msg) {
		if (isErrorEnabled()) {
			log(Level.ERROR, msg, null);
		}
	}

	public void error(String format, Object arg) {
		if (isErrorEnabled()) {
			formatAndLog(Level.ERROR, format, arg, null);
		}
	}

	public void error(String format, Object arg1, Object arg2) {
		if (isErrorEnabled()) {
			formatAndLog(Level.ERROR, format, arg1, arg2);
		}
	}

	public void error(String format, Object[] argArray) {
		if (isErrorEnabled()) {
			formatAndLog(Level.ERROR, format, argArray);
		}
	}

	public void error(String msg, Throwable t) {
		if (isErrorEnabled()) {
			log(Level.ERROR, msg, t);
		}
	}
	
}
