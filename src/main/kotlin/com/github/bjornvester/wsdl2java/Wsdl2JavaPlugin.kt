package com.github.bjornvester.wsdl2java

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

class Wsdl2JavaPlugin : Plugin<Project> {
    companion object {
        const val MINIMUM_GRADLE_VERSION = "6.0"
        const val MINIMUM_GRADLE_VERSION_GROUPING = "7.0"
        const val PLUGIN_ID = "com.github.bjornvester.wsdl2java"
        const val WSDL2JAVA_TASK_NAME = "wsdl2java"
        const val WSDL2JAVA_EXTENSION_NAME = "wsdl2java"
        const val WSDL2JAVA_CONFIGURATION_NAME = "wsdl2java"
        const val XJC_PLUGINS_CONFIGURATION_NAME = "xjcPlugins"
    }

    override fun apply(project: Project) {
        project.logger.info("Applying $PLUGIN_ID to project ${project.name}")
        verifyGradleVersion()
        project.plugins.apply(JavaPlugin::class.java)
        val extension = project.extensions.create(WSDL2JAVA_EXTENSION_NAME, Wsdl2JavaPluginExtension::class.java)
        val wsdl2JavaConfiguration = createResolvableConfiguration(project, WSDL2JAVA_CONFIGURATION_NAME)
        createResolvableConfiguration(project, XJC_PLUGINS_CONFIGURATION_NAME)

        wsdl2JavaConfiguration.defaultDependencies {
            addLater(extension.cxfVersion.map { project.dependencies.create("org.apache.cxf:cxf-tools-wsdlto-frontend-jaxws:$it") })
            addLater(extension.cxfVersion.map { project.dependencies.create("org.apache.cxf:cxf-tools-wsdlto-databinding-jaxb:$it") })
            add(project.dependencies.create("jakarta.xml.ws:jakarta.xml.ws-api:2.3.3"))
            add(project.dependencies.create("javax.annotation:javax.annotation-api:1.3.2"))
            add(project.dependencies.create("org.slf4j:slf4j-simple:1.7.30"))
        }


        project.configurations.named("implementation") {
            if (extension.addCompilationDependencies.get()) {
                dependencies.add(project.dependencies.create("jakarta.xml.ws:jakarta.xml.ws-api:2.3.3"))
                dependencies.add(project.dependencies.create("jakarta.jws:jakarta.jws-api:1.1.1"))
            }
        }

        val defaultTask = addWsdl2JavaTask(WSDL2JAVA_TASK_NAME, project, extension)

        extension.groups.all {
            if (GradleVersion.current() < GradleVersion.version(MINIMUM_GRADLE_VERSION_GROUPING)) {
                throw UnsupportedOperationException("Plugin $PLUGIN_ID requires at least Gradle $MINIMUM_GRADLE_VERSION_GROUPING when using the 'groups' property, but you are using ${GradleVersion.current().version}")
            }

            defaultTask.configure {
                enabled = false
            }

            addWsdl2JavaTask(WSDL2JAVA_TASK_NAME + name.capitalize(), project, this)
        }
    }

    private fun addWsdl2JavaTask(name: String, project: Project, group: Wsdl2JavaPluginExtensionGroup): TaskProvider<Wsdl2JavaTask> {
        var wsdl2JavaTask = project.tasks.register(name, Wsdl2JavaTask::class.java) {
            wsdlInputDir.convention(group.wsdlDir)
            includes.convention(group.includes)
            includesWithOptions.convention(group.includesWithOptions)
            bindingFile.convention(group.bindingFile)
            options.convention(group.options)
            verbose.convention(group.verbose)
            suppressGeneratedDate.convention(group.suppressGeneratedDate)
            markGenerated.convention(group.markGenerated)
            sourcesOutputDir.convention(group.generatedSourceDir)
            packageName.convention(group.packageName)

            val sourceSets = project.properties["sourceSets"] as SourceSetContainer

            sourceSets.named(MAIN_SOURCE_SET_NAME) {
                java.srcDir(sourcesOutputDir)
            }
        }

        project.tasks.named(JavaPlugin.COMPILE_JAVA_TASK_NAME) {
            dependsOn(wsdl2JavaTask)
        }

        return wsdl2JavaTask
    }

    private fun verifyGradleVersion() {
        if (GradleVersion.current() < GradleVersion.version(MINIMUM_GRADLE_VERSION)) {
            throw UnsupportedOperationException(
                "Plugin $PLUGIN_ID requires at least Gradle $MINIMUM_GRADLE_VERSION, " +
                        "but you are using ${GradleVersion.current().version}"
            )
        }
    }

    private fun createResolvableConfiguration(project: Project, name: String): Configuration {
        return project.configurations.maybeCreate(name).apply {
            isCanBeConsumed = false
            isCanBeResolved = true
            isVisible = false
        }
    }
}
