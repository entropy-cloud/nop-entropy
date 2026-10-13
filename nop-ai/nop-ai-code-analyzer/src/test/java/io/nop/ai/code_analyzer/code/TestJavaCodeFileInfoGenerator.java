package io.nop.ai.code_analyzer.code;

import com.github.javaparser.JavaParser;
import io.nop.ai.code_analyzer.maven.MavenDependency;
import io.nop.ai.code_analyzer.maven.MavenDependencyNode;
import io.nop.ai.code_analyzer.maven.MavenModule;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJavaCodeFileInfoGenerator extends BaseTestCase {

    private static final String SAMPLE_JAVA =
            "package demo;\n" +
                    "public class Demo {\n" +
                    "    public String hello() { return \"hi\"; }\n" +
                    "}\n";

    private MavenModule newModule() {
        return new MavenModule("demo",
                new MavenDependencyNode(new MavenDependency("demo", "demo", "jar", "1.0", "compile", null)));
    }

    private void writeSummary(File summaryFile, String content) throws IOException {
        Files.createDirectories(summaryFile.getParentFile().toPath());
        Files.writeString(summaryFile.toPath(), content, StandardCharsets.UTF_8);
    }

    @Test
    public void testGenerateWithNonListSummaryFails() throws IOException {
        File rootDir = Files.createTempDirectory("code-analyzer-summary-test").toFile();
        try {
            File javaSrcDir = new File(rootDir, "demo/src/main/java");
            Files.createDirectories(javaSrcDir.toPath());
            Files.writeString(new File(javaSrcDir, "Demo.java").toPath(), SAMPLE_JAVA, StandardCharsets.UTF_8);

            File outDir = new File(rootDir, "out");
            Files.createDirectories(outDir.toPath());

            File summaryDir = new File(rootDir, "summary");
            writeSummary(new File(summaryDir, "demo/src/main/java/Demo.summary.json"), "{\"name\":\"Demo\"}");

            JavaCodeFileInfoGenerator generator = new JavaCodeFileInfoGenerator(new JavaCodeFileInfoParser(new JavaParser()));

            assertThrows(NopException.class, () -> generator.generate(newModule(), rootDir, outDir, summaryDir));
        } finally {
            deleteRecursively(rootDir);
        }
    }

    @Test
    public void testGenerateWithNonListFunctionsFails() throws IOException {
        File rootDir = Files.createTempDirectory("code-analyzer-summary-test").toFile();
        try {
            File javaSrcDir = new File(rootDir, "demo/src/main/java");
            Files.createDirectories(javaSrcDir.toPath());
            Files.writeString(new File(javaSrcDir, "Demo.java").toPath(), SAMPLE_JAVA, StandardCharsets.UTF_8);

            File outDir = new File(rootDir, "out");
            Files.createDirectories(outDir.toPath());

            File summaryDir = new File(rootDir, "summary");
            writeSummary(new File(summaryDir, "demo/src/main/java/Demo.summary.json"),
                    "[{\"name\":\"demo.Demo\",\"summary\":\"demo class\",\"functions\":{\"bad\":1}}]");

            JavaCodeFileInfoGenerator generator = new JavaCodeFileInfoGenerator(new JavaCodeFileInfoParser(new JavaParser()));

            assertThrows(NopException.class, () -> generator.generate(newModule(), rootDir, outDir, summaryDir));
        } finally {
            deleteRecursively(rootDir);
        }
    }

    @Test
    public void testGenerateWithValidSummarySucceeds() throws IOException {
        File rootDir = Files.createTempDirectory("code-analyzer-summary-test").toFile();
        try {
            File javaSrcDir = new File(rootDir, "demo/src/main/java");
            Files.createDirectories(javaSrcDir.toPath());
            Files.writeString(new File(javaSrcDir, "Demo.java").toPath(), SAMPLE_JAVA, StandardCharsets.UTF_8);

            File outDir = new File(rootDir, "out");
            Files.createDirectories(outDir.toPath());

            File summaryDir = new File(rootDir, "summary");
            writeSummary(new File(summaryDir, "demo/src/main/java/Demo.summary.json"),
                    "[{\"name\":\"demo.Demo\",\"summary\":\"demo class\"," +
                            "\"functions\":[{\"name\":\"demo.Demo::hello(0)\",\"summary\":\"greets\"}]}]");

            JavaCodeFileInfoGenerator generator = new JavaCodeFileInfoGenerator(new JavaCodeFileInfoParser(new JavaParser()));

            generator.generate(newModule(), rootDir, outDir, summaryDir);

            File outFile = new File(outDir, "demo/src/main/java/Demo.info.json");
            assertTrue(outFile.exists(), "info json should be generated for valid summary input");
        } finally {
            deleteRecursively(rootDir);
        }
    }

    private void deleteRecursively(File dir) {
        if (dir == null || !dir.exists())
            return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteRecursively(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}
