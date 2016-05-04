package com.palantir.atlasdb

import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.TaskAction

class GenerateSchemasTask extends AbstractTask {

    @TaskAction
    public void generate() {
        println "generating!"
    }

}
