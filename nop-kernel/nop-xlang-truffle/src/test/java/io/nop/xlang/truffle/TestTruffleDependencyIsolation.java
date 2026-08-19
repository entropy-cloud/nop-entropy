package io.nop.xlang.truffle;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不泄漏断言（roadmap I5 验收第二项，口径钉死）：
 *
 * <p>扫描 {@code nop-kernel} 域内全部 pom 的 {@code <dependencies>} 段（不含
 * {@code dependencyManagement}——版本管理不是模块依赖），断言
 * {@code org.graalvm.truffle:*} / {@code org.graalvm.polyglot:*} 依赖声明仅出现在
 * {@code nop-xlang-truffle/pom.xml}。
 *
 * <p>显式豁免（不算命中）：{@code nop-frontend-support/nop-js}（非 nop-kernel 域，GraalJS
 * 嵌入先例）；{@code org.graalvm.buildtools:native-maven-plugin}（构建插件非依赖，
 * 位于 nop-kernel-cli）。
 */
public class TestTruffleDependencyIsolation {

    private static final String MODULE_POM = "nop-xlang-truffle/pom.xml";

    private static final Pattern DEPENDENCIES_SECTION = Pattern.compile(
            "<dependencies>(.*?)</dependencies>", Pattern.DOTALL);
    private static final Pattern DEPENDENCY_BLOCK = Pattern.compile(
            "<dependency>(.*?)</dependency>", Pattern.DOTALL);
    private static final Pattern GROUP_ID = Pattern.compile(
            "<groupId>\\s*([^<\\s]+)\\s*</groupId>");
    private static final Pattern ARTIFACT_ID = Pattern.compile(
            "<artifactId>\\s*([^<\\s]+)\\s*</artifactId>");

    @Test
    public void testGraalVmTrufflePolyglotDepsOnlyInTruffleModule() throws IOException {
        Path kernelDir = locateKernelDir();
        List<String> offenders = new ArrayList<>();
        List<String> hits = new ArrayList<>();
        try (Stream<Path> poms = Files.walk(kernelDir)) {
            poms.filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .forEach(pom -> scanPom(pom, offenders, hits));
        }
        assertTrue(hits.contains(MODULE_POM), "truffle module pom must declare the graalvm deps, scanned: " + hits);
        assertEquals(List.of(), offenders,
                "org.graalvm.truffle/polyglot <dependency> declarations are only allowed in "
                        + MODULE_POM + " within nop-kernel; exemptions: nop-js (non-kernel), "
                        + "org.graalvm.buildtools build plugins");
    }

    private static void scanPom(Path pom, List<String> offenders, List<String> hits) {
        String text;
        try {
            text = new String(Files.readAllBytes(pom), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("read pom failed: " + pom, e);
        }
        String relative = relativeToKernel(pom);
        Matcher sections = DEPENDENCIES_SECTION.matcher(text);
        while (sections.find()) {
            Matcher deps = DEPENDENCY_BLOCK.matcher(sections.group(1));
            while (deps.find()) {
                String block = deps.group(1);
                String groupId = firstGroup(GROUP_ID, block);
                if (!"org.graalvm.truffle".equals(groupId) && !"org.graalvm.polyglot".equals(groupId))
                    continue;
                String artifactId = firstGroup(ARTIFACT_ID, block);
                hits.add(relative);
                if (!MODULE_POM.equals(relative)) {
                    offenders.add(relative + " -> " + groupId + ":" + artifactId);
                }
            }
        }
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private static String relativeToKernel(Path pom) {
        String normalized = pom.toString().replace('\\', '/');
        int idx = normalized.indexOf("/nop-kernel/");
        String relative = idx >= 0 ? normalized.substring(idx + "/nop-kernel/".length()) : normalized;
        return relative;
    }

    private static Path locateKernelDir() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !dir.getFileName().toString().equals("nop-xlang-truffle")) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "must run inside nop-xlang-truffle module, cwd=" + System.getProperty("user.dir"));
        return dir.getParent();
    }
}
