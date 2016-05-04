package com.palantir.atlasdb

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.plugins.quality.FindBugsPlugin
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin

class AtlasPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply JavaPlugin
        project.extensions.create("atlasdb", AtlasPluginExtension)

        project.tasks.create("generateSchemas", GenerateSchemasTask.class)
        project.tasks.create("cleanSchemas", CleanSchemasTask.class)

        setupGeneratedSourceSet(project)
        setupTaskDependencies(project)
    }

    void setupGeneratedSourceSet(Project project) {
        project.sourceSets {
            generated
        }

        project.plugins.withType(IdeaPlugin) {
            project.idea {
                module {
                    sourceDirs += project.file('src/generated/java')
                    generatedSourceDirs += project.file('src/generated/java')
                }
            }
        }

        project.configurations {
            generatedCompile.extendsFrom compile
        }
    }

    void setupTaskDependencies(Project project) {
        project.tasks.compileGeneratedJava.dependsOn project.tasks.generateSchemas
        project.tasks.check.dependsOn project.tasks.compileGeneratedJava
        project.tasks.clean.dependsOn project.tasks.cleanSchemas
        project.plugins.withType(EclipsePlugin) {
            project.tasks.eclipse.dependsOn project.tasks.generateSchemas
        }
        project.plugins.withType(IdeaPlugin) {
            project.tasks.idea.dependsOn project.tasks.generateSchemas
        }

        project.plugins.withType(CheckstylePlugin) {
            project.tasks.checkstyleGenerated.enabled = false
        }
        project.plugins.withType(FindBugsPlugin) {
            project.tasks.findbugsGenerated.enabled = false
        }

        project.jar {
            from project.sourceSets.generated.output
            dependsOn project.compileGeneratedJava
        }
    }

}
