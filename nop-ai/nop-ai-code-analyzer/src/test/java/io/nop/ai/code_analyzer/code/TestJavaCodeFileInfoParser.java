package io.nop.ai.code_analyzer.code;

import com.github.javaparser.JavaParser;
import io.nop.ai.code_analyzer.maven.MavenModuleStructure;
import io.nop.ai.code_analyzer.maven.MavenProject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestJavaCodeFileInfoParser extends BaseTestCase {
    @Test
    public void testParse() {


        File file = new File(getSrcDir(), "main/java/io/nop/ai/code_analyzer/code/JavaCodeFileInfoParser.java");
        MavenProject project = new MavenProject(this.getModuleDir());
        project.generateDependencyTree();
        MavenModuleStructure structure = project.loadModuleStructure();

        JavaParser javaParser = new JavaParserBuilder().addReflection().addModuleJars(structure.getRootModule()).build();
        JavaCodeFileInfoParser parser = new JavaCodeFileInfoParser(javaParser);
        CodeFileInfo fileInfo = parser.parseFromFile(file);
        System.out.println(JsonTool.serialize(fileInfo, true));
    }
}
