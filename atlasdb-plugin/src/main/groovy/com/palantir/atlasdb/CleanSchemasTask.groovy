package com.palantir.atlasdb

import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.TaskAction

class CleanSchemasTask extends AbstractTask {

    @TaskAction
    public void clean() {
        println "cleaning!"
    }

}
