/*******************************************************************************
 * Copyright (c) 2008-2013 Sonatype, Inc.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.launching.environments.IExecutionEnvironmentsManager;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.internal.M2EUtils;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ILifecycleMapping;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.eclipse.m2e.jdt.IClasspathDescriptor;
import org.eclipse.m2e.jdt.IClasspathEntryDescriptor;
import org.eclipse.m2e.jdt.IJavaProjectConfigurator;
import org.eclipse.m2e.jdt.MavenJdtPlugin;


/**
 * AbstractJavaProjectConfigurator
 * 
 * @author igor
 */
public abstract class AbstractJavaProjectConfigurator extends AbstractProjectConfigurator {
  private static final Logger log = LoggerFactory.getLogger(AbstractJavaProjectConfigurator.class);

  private static final String GOAL_COMPILE = "compile";

  private static final String GOAL_TESTCOMPILE = "testCompile";

  public static final String COMPILER_PLUGIN_ARTIFACT_ID = "maven-compiler-plugin";

  public static final String COMPILER_PLUGIN_GROUP_ID = "org.apache.maven.plugins";

  protected static final List<String> SOURCES = Arrays.asList("1.1,1.2,1.3,1.4,1.5,5,1.6,6,1.7,7,1.8,8".split(",")); //$NON-NLS-1$ //$NON-NLS-2$

  protected static final List<String> TARGETS = Arrays
      .asList("1.1,1.2,1.3,1.4,jsr14,1.5,5,1.6,6,1.7,7,1.8,8".split(",")); //$NON-NLS-1$ //$NON-NLS-2$

  private static final String GOAL_RESOURCES = "resources";

  private static final String GOAL_TESTRESOURCES = "testResources";

  private static final String RESOURCES_PLUGIN_ARTIFACT_ID = "maven-resources-plugin";

  private static final String RESOURCES_PLUGIN_GROUP_ID = "org.apache.maven.plugins";

  protected static final LinkedHashMap<String, String> ENVIRONMENTS = new LinkedHashMap<String, String>();

  static {
    ENVIRONMENTS.put("1.1", "JRE-1.1"); //$NON-NLS-1$ //$NON-NLS-2$
    ENVIRONMENTS.put("1.2", "J2SE-1.2"); //$NON-NLS-1$ //$NON-NLS-2$
    ENVIRONMENTS.put("1.3", "J2SE-1.3"); //$NON-NLS-1$ //$NON-NLS-2$
    ENVIRONMENTS.put("1.4", "J2SE-1.4"); //$NON-NLS-1$ //$NON-NLS-2$
    ENVIRONMENTS.put("1.5", "J2SE-1.5"); //$NON-NLS-1$ //$NON-NLS-2$
    ENVIRONMENTS.put("jsr14", "J2SE-1.5"); //$NON-NLS-1$ //$NON-NLS-2$
    ENVIRONMENTS.put("1.6", "JavaSE-1.6"); //$NON-NLS-1$ //$NON-NLS-2$
    ENVIRONMENTS.put("1.7", "JavaSE-1.7"); //$NON-NLS-1$ //$NON-NLS-2$
    ENVIRONMENTS.put("1.8", "JavaSE-1.8"); //$NON-NLS-1$ //$NON-NLS-2$
  }

  protected static final String DEFAULT_COMPILER_LEVEL = "1.4"; //$NON-NLS-1$

  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
    IProject project = request.getProject();

    monitor.setTaskName(Messages.AbstractJavaProjectConfigurator_task_name + project.getName());

    addJavaNature(project, monitor);

    IJavaProject javaProject = JavaCore.create(project);

    Map<String, String> options = new HashMap<String, String>();

    addJavaProjectOptions(options, request, monitor);

    IClasspathDescriptor classpath = new ClasspathDescriptor(javaProject);

    addProjectSourceFolders(classpath, request, monitor);

    String environmentId = getExecutionEnvironmentId(options);

    addJREClasspathContainer(classpath, environmentId);

    addMavenClasspathContainer(classpath);

    addCustomClasspathEntries(javaProject, classpath);

    invokeJavaProjectConfigurators(classpath, request, monitor);

    // now apply new configuration

    // A single setOptions call erases everything else from an existing settings file.
    // Must invoke setOption individually to preserve previous options. 
    for(Map.Entry<String, String> option : options.entrySet()) {
      javaProject.setOption(option.getKey(), option.getValue());
    }

    IContainer classesFolder = getOutputLocation(request, project);

    javaProject.setRawClasspath(classpath.getEntries(), classesFolder.getFullPath(), monitor);

    MavenJdtPlugin.getDefault().getBuildpathManager().updateClasspath(project, monitor);
  }

  protected IContainer getOutputLocation(ProjectConfigurationRequest request, IProject project) {
    MavenProject mavenProject = request.getMavenProject();
    return getFolder(project, mavenProject.getBuild().getOutputDirectory());
  }

  protected String getExecutionEnvironmentId(Map<String, String> options) {
    return ENVIRONMENTS.get(options.get(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM));
  }

  protected void addJavaNature(IProject project, IProgressMonitor monitor) throws CoreException {
    addNature(project, JavaCore.NATURE_ID, monitor);
  }

  protected void addCustomClasspathEntries(IJavaProject javaProject, IClasspathDescriptor classpath)
      throws JavaModelException {
  }

  protected void invokeJavaProjectConfigurators(IClasspathDescriptor classpath, ProjectConfigurationRequest request,
      final IProgressMonitor monitor) throws CoreException {
    IMavenProjectFacade facade = request.getMavenProjectFacade();
    IProjectConfigurationManager configurationManager = MavenPlugin.getProjectConfigurationManager();
    ILifecycleMapping lifecycleMapping = configurationManager.getLifecycleMapping(facade);
    if(lifecycleMapping == null) {
      return;
    }
    for(AbstractProjectConfigurator configurator : lifecycleMapping.getProjectConfigurators(facade, monitor)) {
      if(configurator instanceof IJavaProjectConfigurator) {
        ((IJavaProjectConfigurator) configurator).configureRawClasspath(request, classpath, monitor);
      }
    }
  }

  protected void addJREClasspathContainer(IClasspathDescriptor classpath, String environmentId) {

    IClasspathEntry cpe;
    IExecutionEnvironment executionEnvironment = getExecutionEnvironment(environmentId);
    if(executionEnvironment == null) {
      cpe = JavaRuntime.getDefaultJREContainerEntry();
    } else {
      IPath containerPath = JavaRuntime.newJREContainerPath(executionEnvironment);
      cpe = JavaCore.newContainerEntry(containerPath);
    }

    final IPath pathToKeep = cpe.getPath();
    // remove existing JRE entry, only if the path is different from the entry we are going to add. See bug398121
    classpath.removeEntry(new ClasspathDescriptor.EntryFilter() {
      public boolean accept(IClasspathEntryDescriptor descriptor) {
        return JavaRuntime.JRE_CONTAINER.equals(descriptor.getPath().segment(0))
            && !descriptor.getPath().equals(pathToKeep);
      }
    });

    classpath.addEntry(cpe);
  }

  private IExecutionEnvironment getExecutionEnvironment(String environmentId) {
    IExecutionEnvironmentsManager manager = JavaRuntime.getExecutionEnvironmentsManager();
    for(IExecutionEnvironment environment : manager.getExecutionEnvironments()) {
      if(environment.getId().equals(environmentId)) {
        return environment;
      }
    }
    return null;
  }

  protected void addMavenClasspathContainer(IClasspathDescriptor classpath) {

    IClasspathEntry cpe = MavenClasspathHelpers.getDefaultContainerEntry();
    // add new entry without removing existing entries first, see bug398121
    classpath.addEntry(cpe);
  }

  protected void addProjectSourceFolders(IClasspathDescriptor classpath, ProjectConfigurationRequest request,
      IProgressMonitor monitor) throws CoreException {
    SubMonitor mon = SubMonitor.convert(monitor, 6);
    try {
      IProject project = request.getProject();
      MavenProject mavenProject = request.getMavenProject();
      IMavenProjectFacade projectFacade = request.getMavenProjectFacade();

      IFolder classes = getFolder(project, mavenProject.getBuild().getOutputDirectory());
      IFolder testClasses = getFolder(project, mavenProject.getBuild().getTestOutputDirectory());

      M2EUtils.createFolder(classes, true, mon.newChild(1));
      M2EUtils.createFolder(testClasses, true, mon.newChild(1));

      IPath[] inclusion = new IPath[0];
      IPath[] exclusion = new IPath[0];

      IPath[] inclusionTest = new IPath[0];
      IPath[] exclusionTest = new IPath[0];

      String mainSourceEncoding = null;
      String testSourceEncoding = null;

      String mainResourcesEncoding = null;
      String testResourcesEncoding = null;

      List<MojoExecution> executions = getCompilerMojoExecutions(request, mon.newChild(1));

      for(MojoExecution compile : executions) {
        if(isCompileExecution(compile)) {
          mainSourceEncoding = maven.getMojoParameterValue(mavenProject, compile, "encoding", String.class, monitor); //$NON-NLS-1$
          try {
            inclusion = toPaths(maven.getMojoParameterValue(mavenProject, compile, "includes", String[].class, monitor)); //$NON-NLS-1$
          } catch(CoreException ex) {
            log.error("Failed to determine compiler inclusions, assuming defaults", ex);
          }
          try {
            exclusion = toPaths(maven.getMojoParameterValue(mavenProject, compile, "excludes", String[].class, monitor)); //$NON-NLS-1$
          } catch(CoreException ex) {
            log.error("Failed to determine compiler exclusions, assuming defaults", ex);
          }
        }
      }

      for(MojoExecution compile : executions) {
        if(isTestCompileExecution(compile)) {
          testSourceEncoding = maven.getMojoParameterValue(mavenProject, compile, "encoding", String.class, monitor); //$NON-NLS-1$
          try {
            inclusionTest = toPaths(maven.getMojoParameterValue(mavenProject, compile,
                "testIncludes", String[].class, monitor)); //$NON-NLS-1$
          } catch(CoreException ex) {
            log.error("Failed to determine compiler test inclusions, assuming defaults", ex);
          }
          try {
            exclusionTest = toPaths(maven.getMojoParameterValue(mavenProject, compile,
                "testExcludes", String[].class, monitor)); //$NON-NLS-1$
          } catch(CoreException ex) {
            log.error("Failed to determine compiler test exclusions, assuming defaults", ex);
          }
        }
      }

      for(MojoExecution resources : projectFacade.getMojoExecutions(RESOURCES_PLUGIN_GROUP_ID,
          RESOURCES_PLUGIN_ARTIFACT_ID, mon.newChild(1), GOAL_RESOURCES)) {
        mainResourcesEncoding = maven.getMojoParameterValue(mavenProject, resources, "encoding", String.class, monitor); //$NON-NLS-1$
      }

      for(MojoExecution resources : projectFacade.getMojoExecutions(RESOURCES_PLUGIN_GROUP_ID,
          RESOURCES_PLUGIN_ARTIFACT_ID, mon.newChild(1), GOAL_TESTRESOURCES)) {
        testResourcesEncoding = maven.getMojoParameterValue(mavenProject, resources, "encoding", String.class, monitor); //$NON-NLS-1$
      }

      addSourceDirs(classpath, project, mavenProject.getCompileSourceRoots(), classes.getFullPath(), inclusion,
          exclusion, mainSourceEncoding, mon.newChild(1));
      addResourceDirs(classpath, project, mavenProject.getBuild().getResources(), classes.getFullPath(),
          mainResourcesEncoding, mon.newChild(1));

      addSourceDirs(classpath, project, mavenProject.getTestCompileSourceRoots(), testClasses.getFullPath(),
          inclusionTest, exclusionTest, testSourceEncoding, mon.newChild(1));
      addResourceDirs(classpath, project, mavenProject.getBuild().getTestResources(), testClasses.getFullPath(),
          testResourcesEncoding, mon.newChild(1));
    } finally {
      mon.done();
    }
  }

  protected boolean isTestCompileExecution(MojoExecution execution) {
    return GOAL_TESTCOMPILE.equals(execution.getGoal());
  }

  protected boolean isCompileExecution(MojoExecution execution) {
    return GOAL_COMPILE.equals(execution.getGoal());
  }

  private IPath[] toPaths(String[] values) {
    if(values == null) {
      return new IPath[0];
    }
    IPath[] paths = new IPath[values.length];
    for(int i = 0; i < values.length; i++ ) {
      if(values[i] != null && !"".equals(values[i].trim())) {
        paths[i] = new Path(values[i]);
      }
    }
    return paths;
  }

  private void addSourceDirs(IClasspathDescriptor classpath, IProject project, List<String> sourceRoots,
      IPath outputPath, IPath[] inclusion, IPath[] exclusion, String sourceEncoding, IProgressMonitor monitor)
      throws CoreException {

    for(int i = 0; i < sourceRoots.size(); i++ ) {
      IFolder sourceFolder = getFolder(project, sourceRoots.get(i));

      if(sourceFolder == null) {
        // this cannot actually happen, unless I misunderstand how project.getFolder works
        continue;
      }

      // be extra nice to less perfectly written maven plugins, which contribute compile source root to the model 
      // but do not use BuildContext to tell as about the actual resources 
      sourceFolder.refreshLocal(IResource.DEPTH_ZERO, monitor);

      if(sourceFolder.exists() && !sourceFolder.getProject().equals(project)) {
        // source folders outside of ${project.basedir} are not supported
        continue;
      }

      // Set folder encoding (null = platform/container default)
      if(sourceFolder.exists()) {
        sourceFolder.setDefaultCharset(sourceEncoding, monitor);
      }

      IClasspathEntryDescriptor enclosing = getEnclosingEntryDescriptor(classpath, sourceFolder.getFullPath());
      if(enclosing == null || getEntryDescriptor(classpath, sourceFolder.getFullPath()) != null) {
        log.info("Adding source folder " + sourceFolder.getFullPath());

        // source folder entries are created even when corresponding resources do not actually exist in workspace
        // to keep JDT from complaining too loudly about non-existing folders, 
        // all source entries are marked as generated (a.k.a. optional)
        classpath.addSourceEntry(sourceFolder.getFullPath(), outputPath, inclusion, exclusion, true /*generated*/);
      } else {
        log.info("Not adding source folder " + sourceFolder.getFullPath() + " because it overlaps with "
            + enclosing.getPath());
      }
    }
  }

  private IClasspathEntryDescriptor getEnclosingEntryDescriptor(IClasspathDescriptor classpath, IPath fullPath) {
    for(IClasspathEntryDescriptor cped : classpath.getEntryDescriptors()) {
      if(cped.getPath().isPrefixOf(fullPath)) {
        return cped;
      }
    }
    return null;
  }

  private IClasspathEntryDescriptor getEntryDescriptor(IClasspathDescriptor classpath, IPath fullPath) {
    for(IClasspathEntryDescriptor cped : classpath.getEntryDescriptors()) {
      if(cped.getPath().equals(fullPath)) {
        return cped;
      }
    }
    return null;
  }

  private void addResourceDirs(IClasspathDescriptor classpath, IProject project, List<Resource> resources,
      IPath outputPath, String resourceEncoding, IProgressMonitor monitor) throws CoreException {

    for(Resource resource : resources) {
      String directory = resource.getDirectory();
      if(directory == null) {
        continue;
      }
      File resourceDirectory = new File(directory);
      if(resourceDirectory.exists() && resourceDirectory.isDirectory()) {
        IPath relativePath = getProjectRelativePath(project, directory);
        IResource r = project.findMember(relativePath);
        if(r == project) {
          /* 
           * Workaround for the Java Model Exception: 
           *   Cannot nest output folder 'xxx/src/main/resources' inside output folder 'xxx'
           * when pom.xml have something like this:
           * 
           * <build>
           *   <resources>
           *     <resource>
           *       <directory>${basedir}</directory>
           *       <targetPath>META-INF</targetPath>
           *       <includes>
           *         <include>LICENSE</include>
           *       </includes>
           *     </resource>
           */
          log.error("Skipping resource folder " + r.getFullPath());
        } else if(r != null && project.equals(r.getProject())) {
          IClasspathEntryDescriptor enclosing = getEnclosingEntryDescriptor(classpath, r.getFullPath());
          if(enclosing == null || isResourceDescriptor(getEntryDescriptor(classpath, r.getFullPath()))) {
            log.info("Adding resource folder " + r.getFullPath());
            classpath.addSourceEntry(r.getFullPath(), outputPath, new IPath[0] /*inclusions*/, new IPath[] {new Path(
                "**")} /*exclusion*/, false /*optional*/);
          } else {
            // resources and sources folders overlap. make sure JDT only processes java sources.
            log.info("Resources folder " + r.getFullPath() + " overlaps with sources folder " + enclosing.getPath());
            enclosing.addInclusionPattern(new Path("**/*.java"));
          }

          // Set folder encoding (null = platform default)
          IFolder resourceFolder = project.getFolder(relativePath);
          resourceFolder.setDefaultCharset(resourceEncoding, monitor);
        } else {
          log.info("Not adding resources folder " + resourceDirectory.getAbsolutePath());
        }
      }
    }
  }

  private boolean isResourceDescriptor(IClasspathEntryDescriptor cped) {
    //How can we know for sure this is a resource folder?
    if(cped != null) {
      IPath[] exclusionPatterns = cped.getExclusionPatterns();
      if(exclusionPatterns != null && exclusionPatterns.length == 1) {
        IPath excludeAllPattern = new Path("**");
        return excludeAllPattern.equals(exclusionPatterns[0]);
      }
    }
    return false;
  }

  protected void addJavaProjectOptions(Map<String, String> options, ProjectConfigurationRequest request,
      IProgressMonitor monitor) throws CoreException {
    String source = null, target = null;

    for(MojoExecution execution : getCompilerMojoExecutions(request, monitor)) {
      source = getCompilerLevel(request.getMavenProject(), execution, "source", source, SOURCES, monitor); //$NON-NLS-1$
      target = getCompilerLevel(request.getMavenProject(), execution, "target", target, TARGETS, monitor); //$NON-NLS-1$
    }

    if(source == null) {
      source = DEFAULT_COMPILER_LEVEL;
      log.warn("Could not determine source level, using default " + source);
    }

    if(target == null) {
      target = DEFAULT_COMPILER_LEVEL;
      log.warn("Could not determine target level, using default " + target);
    }

    // While "5" and "6" are valid synonyms for Java 5 and Java 6 source,
    // Eclipse expects the values 1.5 and 1.6.
    if(source.equals("5")) {
      source = "1.5";
    } else if(source.equals("6")) {
      source = "1.6";
    } else if(source.equals("7")) {
      source = "1.7";
    } else if(source.equals("8")) {
      source = "1.8";
    }

    // While "5" and "6" are valid synonyms for Java 5 and Java 6 target,
    // Eclipse expects the values 1.5 and 1.6.
    if(target.equals("5")) {
      target = "1.5";
    } else if(target.equals("6")) {
      target = "1.6";
    } else if(target.equals("7")) {
      target = "1.7";
    } else if(target.equals("8")) {
      target = "1.8";
    }

    options.put(JavaCore.COMPILER_SOURCE, source);
    options.put(JavaCore.COMPILER_COMPLIANCE, source);
    options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, target);

    // 360962 keep forbidden_reference severity set by the user
    IJavaProject jp = JavaCore.create(request.getProject());
    if(jp != null && jp.getOption(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, false) == null) {
      options.put(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, "warning"); //$NON-NLS-1$
    }
  }

  protected List<MojoExecution> getCompilerMojoExecutions(ProjectConfigurationRequest request, IProgressMonitor monitor)
      throws CoreException {
    return request.getMavenProjectFacade().getMojoExecutions(COMPILER_PLUGIN_GROUP_ID, COMPILER_PLUGIN_ARTIFACT_ID,
        monitor, GOAL_COMPILE, GOAL_TESTCOMPILE);
  }

  private String getCompilerLevel(MavenProject mavenProject, MojoExecution execution, String parameter, String source,
      List<String> levels, IProgressMonitor monitor) {
    int levelIdx = getLevelIndex(source, levels);

    try {
      source = maven.getMojoParameterValue(mavenProject, execution, parameter, String.class, monitor);
    } catch(CoreException ex) {
      log.error("Failed to determine compiler " + parameter + " setting, assuming default", ex);
    }

    int newLevelIdx = getLevelIndex(source, levels);

    if(newLevelIdx > levelIdx) {
      levelIdx = newLevelIdx;
    }

    if(levelIdx < 0) {
      return DEFAULT_COMPILER_LEVEL;
    }

    return levels.get(levelIdx);
  }

  private int getLevelIndex(String level, List<String> levels) {
    return level != null ? levels.indexOf(level) : -1;
  }

  public void unconfigure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
    super.unconfigure(request, monitor);
    removeMavenClasspathContainer(request.getProject());
  }

  private void removeMavenClasspathContainer(IProject project) throws JavaModelException {
    IJavaProject javaProject = JavaCore.create(project);
    if(javaProject != null) {
      // remove classpatch container from JavaProject
      ArrayList<IClasspathEntry> newEntries = new ArrayList<IClasspathEntry>();
      for(IClasspathEntry entry : javaProject.getRawClasspath()) {
        if(!MavenClasspathHelpers.isMaven2ClasspathContainer(entry.getPath())) {
          newEntries.add(entry);
        }
      }
      javaProject.setRawClasspath(newEntries.toArray(new IClasspathEntry[newEntries.size()]), null);
    }
  }

  protected IFolder getFolder(IProject project, String absolutePath) {
    return project.getFolder(getProjectRelativePath(project, absolutePath));
  }

  protected IPath getProjectRelativePath(IProject project, String absolutePath) {
    File basedir = project.getLocation().toFile();
    String relative;
    if(absolutePath.equals(basedir.getAbsolutePath())) {
      relative = "."; //$NON-NLS-1$
    } else if(absolutePath.startsWith(basedir.getAbsolutePath())) {
      relative = absolutePath.substring(basedir.getAbsolutePath().length() + 1);
    } else {
      relative = absolutePath;
    }
    return new Path(relative.replace('\\', '/')); //$NON-NLS-1$ //$NON-NLS-2$
  }
}
