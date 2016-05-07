package com.palantir.atlasdb

import org.gradle.api.Project
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.plugins.quality.FindBugsPlugin
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue

class AtlasPluginTest {
    private static final String TEST_FILE_PACKAGE = "com.palantir.atlasdb.schema"
    private static final String TEST_FILE_NAME = "TestSchema.java"
    private static final String GEN_FILE_NAME = "TestSchemaGen.java";
    private Project project

    @Before
    public void setup() {
        project = ProjectBuilder.builder().build()

        project.plugins.apply IdeaPlugin
        project.plugins.apply EclipsePlugin
        project.plugins.apply CheckstylePlugin
        project.plugins.apply FindBugsPlugin
        project.plugins.apply AtlasPlugin

        addTestSchemaToProject(project)

        AtlasPluginExtension ext = project.extensions.findByName("atlasdb")
        ext.atlasVersion = "0.3.35"
        ext.schemas = ["com.palantir.atlasdb.schema.TestSchema"]

        project.evaluate();
        project.tasks.compileJava.execute()
    }

    void addTestSchemaToProject(Project project) {
        File rootDir = project.sourceSets.main.java.srcDirs.first();
        File testFile = new File(rootDir, TEST_FILE_PACKAGE.replace('.', '/') + '/' + TEST_FILE_NAME);
        testFile.getParentFile().mkdirs()
        testFile.write(sprintf(testFileContents(), TEST_FILE_PACKAGE, GEN_FILE_NAME))
    }

    static String testFileContents() {
        return '''
            package %s;

            import java.io.File;
            import java.io.FileWriter;
            import java.io.IOException;

            public class TestSchema {
                public static void main(String[] args) throws IOException {
                    FileWriter fileout = new FileWriter(new File("%s"));
                    fileout.write("This is a test file!");
                }
            }
        '''
    }

    @Test
    public void basicFunctionality() {
        File rootDir = project.projectDir
        File generatedFile = new File(rootDir, GEN_FILE_NAME)

        project.tasks.generateSchemas.execute()
        assertTrue generatedFile.exists()

//        project.tasks.cleanSchemas.execute()
//        assertFalse generatedFile.exists()
    }

}
