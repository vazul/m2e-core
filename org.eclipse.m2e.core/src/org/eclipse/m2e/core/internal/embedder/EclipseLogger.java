/*******************************************************************************
 * Copyright (c) 2008-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.core.internal.embedder;

import org.slf4j.LoggerFactory;

import org.codehaus.plexus.logging.Logger;

import org.eclipse.m2e.core.embedder.IMavenConfiguration;
import org.eclipse.m2e.core.internal.Messages;


public class EclipseLogger implements Logger {
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(EclipseLogger.class);

  public static InheritableThreadLocal<String> prefix = new InheritableThreadLocal<String>() {
    protected String initialValue() {
      return "";
    };
  };

  private final IMavenConfiguration mavenConfiguration;

  public EclipseLogger(IMavenConfiguration mavenConfiguration) {
    this.mavenConfiguration = mavenConfiguration;
  }

  public void debug(String msg) {
    if(isDebugEnabled()) {
      log.debug(prefix.get() + msg);
    }
  }

  public void debug(String msg, Throwable t) {
    if(isDebugEnabled()) {
      log.debug(prefix.get() + msg + " " + t.getMessage(), t);
    }
  }

  public void info(String msg) {
    if(isInfoEnabled()) {
      log.info(prefix.get() + msg);
    }
  }

  public void info(String msg, Throwable t) {
    if(isInfoEnabled()) {
      log.info(prefix.get() + msg + " " + t.getMessage(), t);
    }
  }

  public void warn(String msg) {
    if(isWarnEnabled()) {
      log.warn(prefix.get() + msg);
    }
  }

  public void warn(String msg, Throwable t) {
    if(isWarnEnabled()) {
      log.warn(prefix.get() + msg + " " + t.getMessage(), t);
    }
  }

  public void fatalError(String msg) {
    if(isFatalErrorEnabled()) {
      log.error(prefix.get() + msg);
    }
  }

  public void fatalError(String msg, Throwable t) {
    if(isFatalErrorEnabled()) {
      log.error(prefix.get() + msg + " " + t.getMessage(), t);
    }
  }

  public void error(String msg) {
    if(isErrorEnabled()) {
      log.error(prefix.get() + msg);
    }
  }

  public void error(String msg, Throwable t) {
    if(isErrorEnabled()) {
      log.error(prefix.get() + msg + " " + t.getMessage(), t);
    }
  }

  public boolean isDebugEnabled() {
    return mavenConfiguration.isDebugOutput();
  }

  public boolean isInfoEnabled() {
    return true;
  }

  public boolean isWarnEnabled() {
    return true;
  }

  public boolean isErrorEnabled() {
    return true;
  }

  public boolean isFatalErrorEnabled() {
    return true;
  }

  public void setThreshold(int treshold) {
  }

  public int getThreshold() {
    return LEVEL_DEBUG;
  }

  public Logger getChildLogger(String name) {
    return this;
  }

  public String getName() {
    return Messages.EclipseLogger_name;
  }
}
