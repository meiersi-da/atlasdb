package com.palantir.atlasdb

import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.TaskAction

class GenerateSchemasTask extends AbstractTask {

    @TaskAction
    public void generate() {
        AtlasPluginExtension ext = project.extensions.atlasdb
        ext.schemas.each { schema ->
            def exit = project.javaexec {
                classpath project.sourceSets.main.runtimeClasspath
                main = schema
            }
        }
    }

}
