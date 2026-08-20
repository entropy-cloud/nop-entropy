package io.nop.xlang.backend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 依赖方向结构性断言（不泄漏口径扩展到 SPI 层）：nop-xlang main 不得引用后端模块类型
 * （io.nop.xlang.truffle / io.nop.xlang.java. / org.graalvm），pom 不得依赖后端模块与
 * GraalVM 坐标。该断言同时是"出口不得自带后端 if/else"的结构性保证——后端类型在 nop-xlang
 * main 不可引用，if/else 结构性不可能（Phase 1 §1 定形机制）。
 */
public class TestEvalBackendDependencyDirection {

    private static final Pattern FORBIDDEN_IMPORT = Pattern.compile(
            "^import\\s+(io\\.nop\\.xlang\\.truffle\\..*|io\\.nop\\.xlang\\.java\\..*|org\\.graalvm\\..*);");

    private static final Pattern FORBIDDEN_PACKAGE = Pattern.compile(
            "^package\\s+(io\\.nop\\.xlang\\.truffle\\..*|io\\.nop\\.xlang\\.java\\..*|org\\.graalvm\\..*);");

    private static final Pattern FORBIDDEN_POM = Pattern.compile(
            "(nop-xlang-truffle|nop-xlang-java|org\\.graalvm)");

    @Test
    public void testNoBackendOrGraalvmReferencesInMainSources() throws IOException {
        Path mainJava = Paths.get("src", "main", "java");
        assertTrue(Files.isDirectory(mainJava), "expect nop-xlang main sources at " + mainJava);

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mainJava)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (FORBIDDEN_IMPORT.matcher(line).matches() || FORBIDDEN_PACKAGE.matcher(line).matches())
                            violations.add(p + ":" + (i + 1) + " " + line);
                    }
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
        assertTrue(violations.isEmpty(), "nop-xlang main must not reference backend modules or GraalVM: " + violations);
    }

    @Test
    public void testNoBackendOrGraalvmDependenciesInPom() throws IOException {
        Path pom = Paths.get("pom.xml");
        assertTrue(Files.isRegularFile(pom));
        String content = new String(Files.readAllBytes(pom), StandardCharsets.UTF_8);
        // 注释行不计（pom 注释中的说明性文字允许）
        List<String> codeLines = new ArrayList<>();
        for (String line : content.split("\n")) {
            if (!line.trim().startsWith("<!--"))
                codeLines.add(line);
        }
        for (String line : codeLines) {
            assertTrue(!FORBIDDEN_POM.matcher(line).find(),
                    "nop-xlang pom must not depend on backend modules or GraalVM: " + line);
        }
    }
}
