package io.nop.ai.code_analyzer.maven;

import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.github.javaparser.utils.Utils.assertNotNull;

public class MavenProjectTest extends BaseTestCase {
    @Test
    public void testFindJavaFile() {
        MavenProject project = new MavenProject(new File(getModuleDir(), ".."));
        File file = project.findJavaFileByClassName(MavenProjectTest.class.getName());
        assertNotNull(file);
    }
}