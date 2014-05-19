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

package org.eclipse.m2e.jdt.ui.internal.actions;

import java.util.Iterator;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkingSet;

import org.eclipse.m2e.jdt.IClasspathManager;
import org.eclipse.m2e.jdt.MavenJdtPlugin;


public class DownloadSourcesAction implements IObjectActionDelegate, IExecutableExtension {

  //TODO private
  public static final String ID_SOURCES = "downloadSources"; //$NON-NLS-1$

  //TODO private
  public static final String ID_JAVADOC = "downloadJavaDoc"; //$NON-NLS-1$

  private IStructuredSelection selection;

  private String id;

  public DownloadSourcesAction() {
    this(ID_SOURCES);
  }

  public DownloadSourcesAction(String id) {
    this.id = id;
  }

  /* (non-Javadoc)
   * @see org.eclipse.core.runtime.IExecutableExtension#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
   */
  public void setInitializationData(IConfigurationElement config, String propertyName, Object data) {
    if(data != null) {
      id = (String) data;
    }
  }

  public void run(IAction action) {
    if(selection != null) {
      IClasspathManager buildpathManager = MavenJdtPlugin.getDefault().getBuildpathManager();
      for(Iterator<?> it = selection.iterator(); it.hasNext();) {
        Object element = it.next();
        if(element instanceof IProject) {
          IProject project = (IProject) element;
          buildpathManager.scheduleDownload(project, ID_SOURCES.equals(id), ID_JAVADOC.equals(id));
        } else if(element instanceof IPackageFragmentRoot) {
          IPackageFragmentRoot fragment = (IPackageFragmentRoot) element;
          buildpathManager.scheduleDownload(fragment, ID_SOURCES.equals(id), ID_JAVADOC.equals(id));
        } else if(element instanceof IWorkingSet) {
          IWorkingSet workingSet = (IWorkingSet) element;
          for(IAdaptable adaptable : workingSet.getElements()) {
            IProject project = (IProject) adaptable.getAdapter(IProject.class);
            buildpathManager.scheduleDownload(project, ID_SOURCES.equals(id), ID_JAVADOC.equals(id));
          }
        }
      }
    }
  }

  public void selectionChanged(IAction action, ISelection selection) {
    if(selection instanceof IStructuredSelection) {
      this.selection = (IStructuredSelection) selection;
    } else {
      this.selection = null;
    }
  }

  public void setActivePart(IAction action, IWorkbenchPart targetPart) {
  }

}
