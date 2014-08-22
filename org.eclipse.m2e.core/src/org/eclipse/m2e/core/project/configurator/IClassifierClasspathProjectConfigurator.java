/*******************************************************************************
 * Copyright (c) 2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.core.project.configurator;

import java.util.Map;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.m2e.core.project.IMavenProjectFacade;


/**
 * Project configurators may implement this interface to get the list of classifiers associated with output locations
 *
 * @author laszlo.varadi@gmail.com
 */
public interface IClassifierClasspathProjectConfigurator {

  /**
   * Should return a map of classifier -> IFolder entries resolving any possible classified output to an output
   * location. Usually the output location.
   * 
   * @param facade
   * @param monitor
   * @return never null
   * @throws CoreException
   */
  public Map<String, IFolder> getClassifiedOutputLocations(IMavenProjectFacade facade, IProgressMonitor monitor)
      throws CoreException;
}
