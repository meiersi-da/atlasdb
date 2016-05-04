package com.palantir.atlasdb

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.plugins.quality.FindBugsPlugin
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin;

class AtlasPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.create("atlasdb", AtlasPluginExtension)

        project.tasks.create("generateSchemas", GenerateSchemasTask.class)
        project.tasks.create("cleanSchemas", CleanSchemasTask.class)

        applyPlugins(project)
        setupGeneratedSourceSet(project)
        setupTaskDependencies(project)
    }

    void applyPlugins(Project project) {
        project.plugins.apply JavaPlugin
        project.plugins.apply IdeaPlugin
        project.plugins.apply EclipsePlugin
        project.plugins.apply CheckstylePlugin
        project.plugins.apply FindBugsPlugin
    }

    void setupGeneratedSourceSet(Project project) {
        project.sourceSets {
            generated
        }

        project.idea {
            module {
                sourceDirs += project.file('src/generated/java')
                generatedSourceDirs += project.file('src/generated/java')
            }
        }

        project.configurations {
            generatedCompile.extendsFrom compile
        }
    }

    void setupTaskDependencies(Project project) {
        project.tasks.compileGeneratedJava.dependsOn project.tasks.generateSchemas
        project.tasks.check.dependsOn project.tasks.compileGeneratedJava
        project.tasks.eclipse.dependsOn project.tasks.generateSchemas
        project.tasks.idea.dependsOn project.tasks.generateSchemas
        project.tasks.clean.dependsOn project.tasks.cleanSchemas

        project.tasks.checkstyleGenerated.enabled = false
        project.tasks.findbugsGenerated.enabled = false

        project.jar {
            from project.sourceSets.generated.output
            dependsOn project.compileGeneratedJava
        }
    }

}
