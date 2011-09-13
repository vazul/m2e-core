/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.jdt.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.JavaCore;

import org.codehaus.plexus.util.Scanner;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;

import org.sonatype.plexus.build.incremental.BuildContext;
import org.sonatype.plexus.build.incremental.ThreadBuildContext;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.lifecyclemapping.model.IPluginExecutionMetadata;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.MavenProjectUtils;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator2;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.eclipse.m2e.jdt.IClasspathDescriptor;
import org.eclipse.m2e.jdt.IJavaProjectConfigurator;


/**
 * Generic java source code generation project configurator. Can be parametrised using the following lifecycle mapping
 * metadata configuration parameters
 * <ul>
 * <li>File outputDirecotry, location of generated sources on the local filesystem. Default ${mojo.outputDirecotry}.</li>
 * <li>File sourceDirectory, location of input resources used by the code generation plugin. Used to suppress unneeded
 * code generation when there are no changes. Can be null, in which case code generation will be executed on full
 * workspace builds only.</li>
 * <li>Boolean runOnIncremental, if <code>true</code> the code generation will be executed on incremental builds, if
 * <code>false</code> the code generation will be executed on full builds only. Default is to execute code generation on
 * incremental builds if sourceDirectory is provided.</li>
 * <li>boolean refreshOutput, <code>true</code> (the default) if outputDirectory should be refreshed from local after
 * code generation.</li>
 * </ul>
 * 
 * @since 1.1
 */
public final class SourcesGenerationProjectConfigurator extends AbstractProjectConfigurator2 implements
    IJavaProjectConfigurator {

  protected static final Logger log = LoggerFactory.getLogger(BuildPathManager.class);

  protected IMaven maven = MavenPlugin.getMaven();

  @SuppressWarnings("unused")
  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
  }

  @SuppressWarnings("unused")
  public void configureClasspath(IMavenProjectFacade facade, IClasspathDescriptor classpath, IProgressMonitor monitor)
      throws CoreException {
  }

  public void configureRawClasspath(ProjectConfigurationRequest request, IClasspathDescriptor classpath,
      IProgressMonitor monitor) throws CoreException {
    IMavenProjectFacade facade = request.getMavenProjectFacade();

    assertHasNature(request.getProject(), JavaCore.NATURE_ID);

    ArrayList<File> outputDirectories = new ArrayList<File>();

    for(MojoExecution mojoExecution : getMojoExecutions(request, monitor)) {
      File outputDirectory = getOutputDirectory(request.getMavenSession(), mojoExecution);
      if(outputDirectory != null) {
        outputDirectories.add(outputDirectory);
      }
    }

    for(File outputDirectory : outputDirectories) {
      IPath sourcePath = getFullPath(facade, outputDirectory);

      if(sourcePath != null && !classpath.containsPath(sourcePath)) {
        classpath.addSourceEntry(sourcePath, facade.getOutputLocation(), true);
      }
    }
  }

  public AbstractBuildParticipant getBuildParticipant(IMavenProjectFacade projectFacade, final MojoExecution execution,
      IPluginExecutionMetadata executionMetadata) {
    return new AbstractBuildParticipant() {

      @Override
      public Set<IProject> build(int kind, IProgressMonitor monitor) throws Exception {
        BuildContext buildContext = getBuildContext();

        // check if any of the input resources changed
        if(isIncrementalBuil(kind) && !shouldRunOnIncrementalBuild(getSession(), execution)) {
          return null;
        }

        // execute mojo
        maven.execute(getSession(), execution, monitor);

        // tell m2e builder to refresh generated files
        if(shouldRefreshOutput(getSession(), execution)) {
          File generated = getOutputDirectory(getSession(), execution);
          if(generated != null) {
            buildContext.refresh(generated);
          }
        }

        return null;
      }

      @Override
      public void clean(IProgressMonitor monitor) throws CoreException {
        super.clean(monitor);

        File outputFolder = getOutputDirectory(getSession(), execution);
        if(outputFolder != null) {
          log.debug("Deleting directory {}", outputFolder.getAbsolutePath());
          if(!outputFolder.exists()) {
            log.debug("Directory {} does not exist", outputFolder.getAbsolutePath());
            return;
          }

          IProject project = getMavenProjectFacade().getProject();
          IFolder iOutputFolder = project.getFolder(getProjectRelativePath(project, outputFolder));
          if(iOutputFolder != null) {
            iOutputFolder.delete(true /* force */, monitor);
          }
        }
      }

    };
  }

  protected boolean shouldRefreshOutput(MavenSession session, MojoExecution execution) throws CoreException {
    Boolean refreshOutput = getConfiguratorParameterValue("refreshOutput", Boolean.class, session, execution);

    return !Boolean.FALSE.equals(refreshOutput);
  }

  protected boolean shouldRunOnIncrementalBuild(MavenSession session, MojoExecution execution) throws CoreException {
    Boolean runOnIncremental = getConfiguratorParameterValue("runOnIncremental", Boolean.class, session, execution);

    if(Boolean.FALSE.equals(runOnIncremental)) {
      // we are explicitly told NOT to run on incremental build
      return false;
    }

    File sourceDirecotry = getConfiguratorParameterValue("sourceDirectory", File.class, session, execution);

    if(sourceDirecotry == null) {
      return Boolean.TRUE.equals(runOnIncremental);
    }

    //File source = maven.getMojoParameterValue(getSession(), getMojoExecution(), "sourceDirectory", File.class);
    Scanner ds = ThreadBuildContext.getContext().newScanner(sourceDirecotry); // delta or full scanner
    ds.scan();
    String[] includedFiles = ds.getIncludedFiles();
    if(includedFiles != null && includedFiles.length > 0) {
      return true;
    }

    return false;
  }

  protected boolean isIncrementalBuil(int kind) {
    return IncrementalProjectBuilder.AUTO_BUILD == kind || IncrementalProjectBuilder.INCREMENTAL_BUILD == kind;
  }

  /**
   * Returns one or more generated sources directories. Generated sources directories are added to JDT project build
   * path as source folders and, optionally, refreshed from local filesystem after mojo execution.
   * <p>
   * Generated sources directories are expected to be inside ${project.basedir}, directories that are not inside
   * ${project.basedir} will be logged and ignored.
   */
  protected File getOutputDirectory(MavenSession session, MojoExecution mojoExecution) throws CoreException {
    File generatedSources = getConfiguratorParameterValue("outputDirecotry", File.class, session, mojoExecution);

    if(generatedSources == null) {
      generatedSources = maven.getMojoParameterValue(session, mojoExecution, "outputDirectory", File.class);
    }

    return generatedSources;
  }

  protected static IPath getFullPath(IMavenProjectFacade facade, File file) {
    IProject project = facade.getProject();
    IPath path = MavenProjectUtils.getProjectRelativePath(project, file.getAbsolutePath());
    return project.getFullPath().append(path);
  }

  protected <T> T getConfiguratorParameterValue(String parameter, Class<T> asType, MavenSession session,
      MojoExecution mojoExecution) throws CoreException {
    return super.getConfiguratorParameterValue(parameter, asType, session, mojoExecution);
  }

  public static IPath getProjectRelativePath(IProject project, File file) {
    IPath projectPath = project.getLocation();
    IPath filePath = new Path(file.getAbsolutePath());
    if(!projectPath.isPrefixOf(filePath)) {
      return null;
    }

    return filePath.removeFirstSegments(projectPath.segmentCount());
  }
}
