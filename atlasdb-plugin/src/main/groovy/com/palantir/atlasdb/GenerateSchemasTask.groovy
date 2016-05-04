package com.palantir.atlasdb

import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.TaskAction

class GenerateSchemasTask extends AbstractTask {

    @TaskAction
    public void generate() {
        project.afterEvaluate {
            AtlasPluginExtension ext = project.extensions.atlasdb
            ext.scheams.each {
                def exit = project.javaexec {
                    classpath project.sourceSets.main.runtimeClasspath
                    main = "${it}"
                }
            }
        }
    }

}
