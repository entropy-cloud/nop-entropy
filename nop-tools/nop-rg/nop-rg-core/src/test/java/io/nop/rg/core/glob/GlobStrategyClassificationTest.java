package io.nop.rg.core.glob;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 5 item 20（plan 2574 Phase D）：GlobSet 策略分类——分类正确性 + 快/慢路径对拍 fuzz。
 */
public class GlobStrategyClassificationTest {

    // ---- 分类正确性：每族至少一例 ----

    @Test
    public void testClassificationKinds() {
        assertEquals(GlobMatcher.Kind.EXTENSION, GlobMatcher.classify("*.java").kind());
        assertEquals(GlobMatcher.Kind.EXTENSION, GlobMatcher.classify("*.tar.gz").kind());
        assertEquals(GlobMatcher.Kind.EXTENSION, GlobMatcher.classify("*.").kind());
        assertEquals(GlobMatcher.Kind.LITERAL_BASENAME, GlobMatcher.classify("Makefile").kind());
        assertEquals(GlobMatcher.Kind.LITERAL_BASENAME, GlobMatcher.classify(".gitignore").kind());
        assertEquals(GlobMatcher.Kind.LITERAL_PATH, GlobMatcher.classify("src/Main.java").kind());
        assertEquals(GlobMatcher.Kind.LITERAL_PATH, GlobMatcher.classify("a/b/c").kind());
        // 通配/转义/brace 形态落 SLOW
        assertEquals(GlobMatcher.Kind.SLOW, GlobMatcher.classify("**/*.java").kind());
        assertEquals(GlobMatcher.Kind.SLOW, GlobMatcher.classify("a?c").kind());
        assertEquals(GlobMatcher.Kind.SLOW, GlobMatcher.classify("*Makefile").kind()); // 后缀不以 '.' 开头
        assertEquals(GlobMatcher.Kind.SLOW, GlobMatcher.classify("a\\*b").kind());
        assertEquals(GlobMatcher.Kind.SLOW, GlobMatcher.classify("*.{java,kt}").kind());
        assertEquals(GlobMatcher.Kind.SLOW, GlobMatcher.classify("[abc]x").kind());
        // 带斜杠的 *.java（路径形态）不走扩展名快路径（扩展名集语义是 basename）
        assertEquals(GlobMatcher.Kind.SLOW, GlobMatcher.classify("a/*.java").kind());
    }

    // ---- 快路径与全慢路径语义一致（含 exclude 语义与优先级）----

    private static boolean slowReference(List<String> globs, String path) {
        List<CompiledGlob> includes = new ArrayList<>();
        List<CompiledGlob> excludes = new ArrayList<>();
        for (String glob : globs) {
            if (glob.isEmpty()) {
                continue;
            }
            if (glob.startsWith("!")) {
                excludes.add(CompiledGlob.compile(glob.substring(1)));
            } else {
                includes.add(CompiledGlob.compile(glob));
            }
        }
        for (CompiledGlob e : excludes) {
            if (e.matches(path)) {
                return false;
            }
        }
        if (includes.isEmpty()) {
            return true;
        }
        for (CompiledGlob i : includes) {
            if (i.matches(path)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testFastPathMatchesSlowReference() {
        // 扩展名（含 basename 恰为后缀：'*' 匹配空串语义）、字面量 basename/path、混合
        List<List<String>> cases = List.of(
                List.of("*.java"),
                List.of("*.tar.gz", "!*.bak"),
                List.of("Makefile"),
                List.of("src/Main.java"),
                List.of("*.java", "!*Test.java"), // exclude SLOW + include EXT
                List.of("!*.java"), // 仅负则
                List.of("Makefile", "*.md", "docs/*.md"),
                List.of("!Makefile", "*.md"));
        String[] paths = {"Main.java", "a/b/Main.java", ".java", "x.tar.gz", "x.tar.gz.bak",
                "Makefile", "a/Makefile", "src/Main.java", "notes.md", "docs/notes.md",
                "Main.java.bak", "README", ""};
        for (List<String> globs : cases) {
            GlobMatcher fast = GlobMatcher.of(globs);
            for (String path : paths) {
                assertEquals(slowReference(globs, path), fast.accept(path),
                        () -> "globs=" + globs + " path=" + path);
            }
        }
    }

    @Test
    public void testFuzzFastVsSlowEquivalence() {
        Random random = new Random(99);
        String[] segments = {"a", "ab", "Main.java", "notes.md", "Makefile", "x.tar.gz", "b.c", "*Test.java"};
        String[] patternForms = {"*.java", "*.md", "Makefile", "src/Main.java", "**/*.java",
                "a?c", "*Test.java", "*.tar.gz", "!*.java", "*.b.c", "src/*.java", "a/**/z",
                "x[ab]y", "*.java"};
        for (int iter = 0; iter < 500; iter++) {
            List<String> globs = new ArrayList<>();
            int n = 1 + random.nextInt(3);
            for (int i = 0; i < n; i++) {
                String form = patternForms[random.nextInt(patternForms.length)];
                globs.add(random.nextBoolean() ? form : "!" + form);
            }
            GlobMatcher fast = GlobMatcher.of(globs);
            for (int k = 0; k < 8; k++) {
                StringBuilder sb = new StringBuilder();
                int depth = random.nextInt(3);
                for (int d = 0; d <= depth; d++) {
                    if (d > 0) {
                        sb.append('/');
                    }
                    sb.append(segments[random.nextInt(segments.length)]);
                }
                String path = sb.toString();
                assertEquals(slowReference(globs, path), fast.accept(path),
                        () -> "globs=" + globs + " path=" + path);
            }
        }
    }

    @Test
    public void testEmptyGlobListAcceptsAll() {
        assertTrue(GlobMatcher.of(List.of()).accept("anything/x.java"));
        assertFalse(GlobMatcher.of(List.of("!*.java")).accept("X.java"));
        assertTrue(GlobMatcher.of(List.of("!*.java")).accept("X.md"));
    }
}
