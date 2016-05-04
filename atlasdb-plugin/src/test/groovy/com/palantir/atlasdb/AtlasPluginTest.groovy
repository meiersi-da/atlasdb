package com.palantir.atlasdb

import org.gradle.api.Project
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.plugins.quality.FindBugsPlugin
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

class AtlasPluginTest {
    private Project project

    @Before
    public void setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply IdeaPlugin
        project.plugins.apply EclipsePlugin
        project.plugins.apply CheckstylePlugin
        project.plugins.apply FindBugsPlugin
        project.plugins.apply AtlasPlugin
    }

    @Test
    public void test() {

    }

}
