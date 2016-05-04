package com.palantir.atlasdb

import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskAction

class CleanSchemasTask extends Delete {

    @TaskAction
    public void clean() {
        delete "src/generated/java"
    }

}
