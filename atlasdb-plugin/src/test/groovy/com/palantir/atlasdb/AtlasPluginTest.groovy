package com.palantir.atlasdb

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

class AtlasPluginTest {
    private Project project

    @Before
    public void setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply AtlasPlugin
    }

    @Test
    public void test() {

    }

}
