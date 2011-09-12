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

import org.eclipse.core.runtime.CoreException;

import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.internal.embedder.MavenImpl;


/**
 * AbstractProjectConfigurator that allows configuration via lifecycle mapping metadata. This is particularly useful
 * when the same generic configuration logic can be applied to several independent unrelated maven plugins. For example,
 * most java code generation plugins can be supported by a single project configurator parametrised with input resources
 * and generated sources directories.
 * <p>
 * Implementation supports most of ${properties} allowed in plugin execution configuration element of pom.xml file, i.e.
 * ${project.basedir} and similar. Mojo configuration parameter values can be referenced via ${mojo.someParameter}.
 * 
 * @since 1.1
 */
public abstract class AbstractProjectConfigurator2 extends AbstractProjectConfigurator {

  private Xpp3Dom configuration;

  protected <T> T getConfiguratorParameterValue(final String parameter, final Class<T> asType,
      final MavenSession session, final MojoExecution mojoExecution) throws CoreException {
    if(configuration == null) {
      return null;
    }

    // if the parameter is not defined, make sure we don't use similarly-named mojo configuration parameter
    if(configuration.getChild(parameter) == null) {
      return null;
    }

    final MavenImpl maven = (MavenImpl) MavenPlugin.getMaven();

    // TODO refactor PluginParameterExpressionEvaluator to allow easier customization
    ExpressionEvaluator expressionEvaluator = new PluginParameterExpressionEvaluator(session, mojoExecution) {
      public Object evaluate(String expr, Class<?> type) throws ExpressionEvaluationException {
        if(expr.startsWith("${mojo.") && expr.endsWith("}")) {
          String paramName = expr.substring(7, expr.length() - 1);
          try {
            return maven.getMojoParameterValue(session, mojoExecution, paramName, asType);
          } catch(CoreException ex) {
            throw new ExpressionEvaluationException(ex.getMessage(), ex);
          }
        }
        return super.evaluate(expr, type);
      }
    };

    return maven.getMojoParameterValue(session, mojoExecution, parameter, asType, new XmlPlexusConfiguration(
        configuration), expressionEvaluator);
  }

  /**
   * @noreference
   */
  public void setConfiguration(Xpp3Dom configuration) {
    this.configuration = configuration;
  }
}
