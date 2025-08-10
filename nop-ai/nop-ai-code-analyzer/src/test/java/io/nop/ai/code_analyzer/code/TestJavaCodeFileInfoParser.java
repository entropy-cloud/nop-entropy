package io.nop.ai.code_analyzer.code;

import com.github.javaparser.JavaParser;
import io.nop.ai.code_analyzer.maven.MavenModuleStructure;
import io.nop.ai.code_analyzer.maven.MavenProject;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestJavaCodeFileInfoParser extends BaseTestCase {
    @Disabled
    @Test
    public void testParse() {
        File file = new File(getSrcDir(), "main/java/io/nop/ai/code_analyzer/code/JavaCodeFileInfoParser.java");
        MavenProject project = new MavenProject(this.getModuleDir());
        project.generateDependencyTree();
        MavenModuleStructure structure = project.loadModuleStructure();

        JavaParser javaParser = new JavaParserBuilder().addReflection().addModuleJars(structure.getRootModule()).build();
        JavaCodeFileInfoParser parser = new JavaCodeFileInfoParser(javaParser);
        CodeFileInfo fileInfo = parser.parseFromFile(file);
        //fileInfo.trimPrivate();
        System.out.println(JsonTool.serialize(fileInfo, true));
    }

    @Disabled
    @Test
    public void testGenerateFileInfo() {
        File rootModuleDir = FileHelper.getAbsoluteFile(new File(getModuleDir(), "../.."));
        MavenProject project = new MavenProject(rootModuleDir);
        //project.generateDependencyTree();
        MavenModuleStructure structure = project.loadModuleStructure();

        File outDir = new File("c:/test/java-code-info");
        File summaryDir = new File("c:/test/data");

        structure.forEachModule(module -> {
            JavaParser javaParser = new JavaParserBuilder().addReflection().addModuleJars(module).build();
            JavaCodeFileInfoParser parser = new JavaCodeFileInfoParser(javaParser);
            JavaCodeFileInfoGenerator generator = new JavaCodeFileInfoGenerator(parser);
            generator.generate(module, rootModuleDir, outDir, summaryDir);
        });

    }
}
