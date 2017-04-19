/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.core.launch

import spock.lang.Shared
import spock.lang.Specification

import com.gradleware.tooling.toolingclient.GradleDistribution
import com.gradleware.tooling.toolingmodel.Path;

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.debug.core.DebugPlugin

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.GradlePluginsRuntimeException
import org.eclipse.buildship.core.configuration.ProjectConfiguration;
import org.eclipse.buildship.core.test.fixtures.EclipseProjects
import org.eclipse.buildship.core.util.gradle.GradleDistributionSerializer
import org.eclipse.buildship.core.util.gradle.GradleDistributionWrapper
import org.eclipse.buildship.core.util.gradle.GradleDistributionWrapper.DistributionType

class GradleRunConfigurationAttributesTest extends Specification {

    @Shared def Attributes defaults = new Attributes (
        tasks : ['clean'],
        workingDir : "/home/user/workspace",
        gradleDistr : GradleDistributionSerializer.INSTANCE.serializeToString(GradleDistribution.fromBuild()),
        javaHome : "/.java",
        arguments : ["-q"],
        jvmArguments : ["-ea"],
        showExecutionView :  true,
        showConsoleView : true,
        overrideWorkspaceSettings : false,
        isOffline: false,
        buildScansEnabled: false,
    )

    def "Can create a new valid instance"() {
        when:
        GradleRunConfigurationAttributes configuration = defaults.toConfiguration()

        then:
        // not null
        configuration != null
        // check non-calculated values
        configuration.getTasks() == defaults.tasks
        configuration.getWorkingDirExpression() == defaults.workingDir
        configuration.getJavaHomeExpression() == defaults.javaHome
        configuration.getJvmArguments() == defaults.jvmArguments
        configuration.getArguments() == defaults.arguments
        configuration.isShowExecutionView() == defaults.showExecutionView
        configuration.isShowConsoleView() == defaults.showConsoleView
        configuration.isOverrideWorkspaceSettings() == defaults.overrideWorkspaceSettings
        configuration.isOffline() == defaults.isOffline
        configuration.isBuildScansEnabled() == defaults.buildScansEnabled
        // check calculated values
        configuration.getArgumentExpressions() == defaults.arguments
        configuration.getJvmArgumentExpressions() == defaults.jvmArguments
        configuration.getWorkingDir().getAbsolutePath() == new File(defaults.workingDir).getAbsolutePath()
        configuration.getJavaHome().getAbsolutePath() == new File(defaults.javaHome).getAbsolutePath()
        configuration.getGradleDistribution() == GradleDistributionSerializer.INSTANCE.deserializeFromString(defaults.gradleDistr)
    }

    def "Can create a new valid instance with valid null arguments"(Attributes attributes) {
        when:
        def configuration = attributes.toConfiguration()

        then:
        configuration != null
        attributes.javaHome != null || configuration.getJavaHome() == null

        where:
        attributes << [
            defaults.copy { javaHome = null },
        ]
    }

    def "Creation fails when null argument passed"(Attributes attributes) {
        when:
        attributes.toConfiguration()

        then:
        thrown(RuntimeException)

        where:
        attributes << [
            defaults.copy { tasks = null },
            defaults.copy { workingDir = null },
            defaults.copy { jvmArguments = null},
            defaults.copy { arguments = null}
        ]
    }

    def "Expressions can be resolved in the parameters"() {
        when:
        def Attributes attributes = defaults.copy {
            workingDir = '${workspace_loc}/working_dir'
            javaHome = '${workspace_loc}/java_home'
        }
        def configuration = attributes.toConfiguration()

        then:
        configuration.getWorkingDir().getPath().endsWith("working_dir")
        !(configuration.getWorkingDir().getPath().contains('$'))
        configuration.getJavaHome().getPath().endsWith("java_home")
        !(configuration.getJavaHome().getPath().contains('$'))
    }

    def "Unresolvable expressions in Java home results in runtime exception"() {
        setup:
        def Attributes attributes = defaults.copy {
            javaHome = '${nonexistingvariable}/java_home'
        }
        def configuration = attributes.toConfiguration()

        when:
        configuration.getJavaHome()

        then:
        thrown(GradlePluginsRuntimeException)

    }

    def "Unresolvable expressions in working directory results in runtime exception"() {
        setup:
        def Attributes attributes = defaults.copy {
            workingDir = '${nonexistingvariable}/working_dir'
        }
        def configuration = attributes.toConfiguration()

        when:
        configuration.getWorkingDir()

        then:
        thrown(GradlePluginsRuntimeException)
    }

    def "Unresolvable expressions in arguments results in runtime exception"() {
        setup:
        def Attributes attributes = defaults.copy {
            arguments = ['${nonexistingvariable}/arguments']
        }
        def configuration = attributes.toConfiguration()

        when:
        configuration.getArguments()

        then:
        thrown(GradlePluginsRuntimeException)
    }

    def "Unresolvable expressions in jvm arguments results in runtime exception"() {
        setup:
        def Attributes attributes = defaults.copy {
            jvmArguments = ['${nonexistingvariable}/jvmarguments']
        }
        def configuration = attributes.toConfiguration()

        when:
        configuration.getJvmArguments()

        then:
        thrown(GradlePluginsRuntimeException)
    }

    def "All configuration can be saved to Eclipse settings"() {
        setup:
        def launchManager = DebugPlugin.getDefault().getLaunchManager();
        def type = launchManager.getLaunchConfigurationType(GradleRunConfigurationDelegate.ID);
        def eclipseConfig = type.newInstance(null, "launch-config-name")

        when:
        assert eclipseConfig.getAttributes().isEmpty()
        def gradleConfig = defaults.toConfiguration()
        gradleConfig.apply(eclipseConfig)

        then:
        eclipseConfig.getAttributes().size() == defaults.size()
    }

    def "All valid configuration settings can be stored and retrieved"(Attributes attributes) {
        setup:
        def launchManager = DebugPlugin.getDefault().getLaunchManager();
        def type = launchManager.getLaunchConfigurationType(GradleRunConfigurationDelegate.ID);
        def eclipseConfig = type.newInstance(null, "launch-config-name")

        when:
        def gradleConfig1 = attributes.toConfiguration()
        gradleConfig1.apply(eclipseConfig)
        def gradleConfig2 = GradleRunConfigurationAttributes.from(eclipseConfig)

        then:
        gradleConfig1.getTasks() == gradleConfig2.getTasks()
        gradleConfig1.getWorkingDirExpression() == gradleConfig2.getWorkingDirExpression()
        gradleConfig1.getGradleDistribution() == gradleConfig2.getGradleDistribution()
        gradleConfig1.getJavaHomeExpression() == gradleConfig2.getJavaHomeExpression()
        gradleConfig1.getJvmArguments() == gradleConfig2.getJvmArguments()
        gradleConfig1.getArguments() == gradleConfig2.getArguments()
        gradleConfig1.isShowExecutionView() == gradleConfig2.isShowExecutionView()
        gradleConfig1.isOverrideWorkspaceSettings() == gradleConfig2.isOverrideWorkspaceSettings()
        gradleConfig1.isOffline() == gradleConfig2.isOffline()
        gradleConfig1.isBuildScansEnabled() == gradleConfig2.isBuildScansEnabled()

        where:
        attributes << [
            defaults,
            defaults.copy { javaHome = null },
        ]
    }

    def "Saved Configuration attributes has same unique attributes"() {
        setup:
        def launchManager = DebugPlugin.getDefault().getLaunchManager();
        def type = launchManager.getLaunchConfigurationType(GradleRunConfigurationDelegate.ID);
        def eclipseConfig = type.newInstance(null, "launch-config-name")

        when:
        def gradleConfig = defaults.toConfiguration()
        gradleConfig.apply(eclipseConfig)

        then:
        gradleConfig.hasSameAttributes(eclipseConfig)
    }

    static class Attributes implements Cloneable {
        def tasks
        def workingDir
        def gradleDistr
        def javaHome
        def arguments
        def jvmArguments
        def showExecutionView
        def showConsoleView
        def overrideWorkspaceSettings
        def isOffline
        def buildScansEnabled

        def GradleRunConfigurationAttributes toConfiguration() {
            new GradleRunConfigurationAttributes(tasks, workingDir, gradleDistr, javaHome, jvmArguments, arguments, showExecutionView, showConsoleView, overrideWorkspaceSettings, isOffline, buildScansEnabled)
        }

        def Attributes copy(@DelegatesTo(value = Attributes, strategy=Closure.DELEGATE_FIRST) Closure closure) {
            def clone = clone()
            def Closure clonedClosure =  closure.clone()
            clonedClosure.setResolveStrategy(Closure.DELEGATE_FIRST)
            clonedClosure.setDelegate(clone)
            clonedClosure.call(clone)
            return clone
        }

        def int size() {
            Attributes.declaredFields.findAll { it.synthetic == false }.size
        }
    }

}
