package com.palantir.atlasdb

import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.TaskAction

class CleanSchemasTask extends AbstractTask {

    @TaskAction
    public void clean() {
        AtlasPluginExtension ext = project.extensions.atlasdb

        def workingDir = project.projectDir
        def toDelete = new File(workingDir, AtlasPlugin.GENERATED_DIR)

        project.delete toDelete

    }

}
