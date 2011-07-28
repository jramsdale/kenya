package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

import com.github.jramsdale.kenya.ConfigurableSimpleLoggerFactory;

public class StaticLoggerBinder implements LoggerFactoryBinder {

  private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();
  
  public static final StaticLoggerBinder getSingleton() {
    return SINGLETON;
  }

  // to avoid constant folding by the compiler, this field must *not* be final
  public static String REQUESTED_API_VERSION = "1.6";  // !final
  
  private static final String loggerFactoryClassStr = ConfigurableSimpleLoggerFactory.class.getName();

  private final ILoggerFactory loggerFactory;
  
  private StaticLoggerBinder() {
    loggerFactory = new ConfigurableSimpleLoggerFactory();
  }
  
  public ILoggerFactory getLoggerFactory() {
    return loggerFactory;
  }
  
  public String getLoggerFactoryClassStr() {
    return loggerFactoryClassStr;
  }   

}
