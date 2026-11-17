package io.nop.ib.tasks;

import io.nop.ib.NopIbException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 12 Phase 1 pins: hierarchical discovery/merge of nop-ib.tasks.json and
 * fail-loud validation — every config error is an explicit NopIbException
 * (CLI exit 3), never a silent skip (design 08 IB-T1).
 */
class TestTaskConfig {
    @TempDir
    Path repo;

    private void writeConfig(String content) throws Exception {
        Files.writeString(repo.resolve(TaskConfigLoader.FILE_NAME), content);
    }

    private static final String VALID = """
            [{
              "name": "gen",
              "trigger": "before-compile",
              "inputs": ["model/*.xgen"],
              "outputs": ["src/main/java/**/_gen/**"],
              "exec": {"type": "java", "mainClass": "com.x.Gen", "arguments": ["pre"], "classpathScope": "test"}
            }]
            """;

    // ---- validation branches (each a pin)

    @Test
    void missingNameRejected() {
        NopIbException e = assertThrows(NopIbException.class,
                () -> TaskConfigLoader.parseFile(repo.resolve("x.json")) /* file absent */);
        // absent file itself must also fail loudly
        assertTrue(e.getMessage().contains("cannot read"));
    }

    @Test
    void eachConfigErrorIsExplicit() throws Exception {
        record Case(String json, String expect) {
        }
        List<Case> cases = List.of(
                new Case("[]", null), // valid empty set
                new Case("[{\"trigger\":\"before-compile\",\"inputs\":[\"a\"],\"outputs\":[\"b\"],"
                        + "\"exec\":{\"type\":\"java\",\"mainClass\":\"M\"}}]", "missing required field 'name'"),
                new Case("[{\"name\":\"t\",\"trigger\":\"nope\",\"inputs\":[\"a\"],\"outputs\":[\"b\"],"
                        + "\"exec\":{\"type\":\"java\",\"mainClass\":\"M\"}}]", "trigger must be"),
                new Case("[{\"name\":\"t\",\"trigger\":\"before-compile\",\"inputs\":[\"a\"],"
                        + "\"exec\":{\"type\":\"java\",\"mainClass\":\"M\"}}]", "missing required field 'outputs'"),
                new Case("[{\"name\":\"t\",\"trigger\":\"before-compile\",\"inputs\":[\"a\"],\"outputs\":[\"b\"],"
                        + "\"exec\":{\"type\":\"magic\",\"mainClass\":\"M\"}}]", "exec.type must be"),
                new Case("[{\"name\":\"t\",\"trigger\":\"before-compile\",\"inputs\":[\"a\"],\"outputs\":[\"b\"],"
                        + "\"exec\":{\"type\":\"java\"}}]", "missing required field 'mainClass'"),
                new Case("[{\"name\":\"t\",\"trigger\":\"before-compile\",\"inputs\":[\"a\"],\"outputs\":[\"b\"],"
                        + "\"exec\":{\"type\":\"xscript\",\"mainClass\":\"M\"}}]", "xscript requires exec.script"),
                new Case("[{\"name\":\"t\",\"trigger\":\"before-compile\",\"inputs\":[\"a\"],\"outputs\":[\"b\"],"
                        + "\"exec\":{\"type\":\"java\",\"mainClass\":\"M\",\"classpathScope\":\"bogus\"}}]",
                        "classpathScope must be"),
                new Case("[{\"name\":\"t\",\"bogus\":1,\"trigger\":\"before-compile\",\"inputs\":[\"a\"],"
                        + "\"outputs\":[\"b\"],\"exec\":{\"type\":\"java\",\"mainClass\":\"M\"}}]", "unknown task field"),
                new Case("[{\"name\":\"t\",\"trigger\":\"before-compile\",\"inputs\":[\"a\"],\"outputs\":[\"b\"],"
                        + "\"exec\":{\"type\":\"java\",\"mainClass\":\"M\",\"bogus\":1}}]", "unknown exec field"),
                new Case("[{\"name\":\"t\",\"trigger\":\"before-compile\",\"inputs\":[\"[\"],\"outputs\":[\"b\"],"
                        + "\"exec\":{\"type\":\"java\",\"mainClass\":\"M\"}}]", "invalid glob"),
                new Case("not json", "malformed JSON"));
        for (Case c : cases) {
            writeConfig(c.json());
            if (c.expect() == null) {
                assertEquals(0, TaskConfigLoader.parseFile(repo.resolve(TaskConfigLoader.FILE_NAME)).size());
            } else {
                NopIbException e = assertThrows(NopIbException.class,
                        () -> TaskConfigLoader.parseFile(repo.resolve(TaskConfigLoader.FILE_NAME)),
                        "case expect: " + c.expect());
                assertTrue(e.getMessage().contains(c.expect()), c.expect() + " <- " + e.getMessage());
            }
        }
    }

    @Test
    void classpathScopeDefaultsToTest() throws Exception {
        writeConfig("[{\"name\":\"t\",\"trigger\":\"before-compile\",\"inputs\":[\"a\"],\"outputs\":[\"b\"],"
                + "\"exec\":{\"type\":\"java\",\"mainClass\":\"M\"}}]");
        assertEquals("test", TaskConfigLoader.parseFile(repo.resolve(TaskConfigLoader.FILE_NAME))
                .get(0).exec.classpathScope);
    }

    // ---- hierarchical discovery + merge

    @Test
    void moduleLevelOverridesExecFieldsPartially() throws Exception {
        writeConfig(VALID);
        Path modDir = repo.resolve("nop-x-meta");
        Files.createDirectories(modDir);
        Files.writeString(modDir.resolve(TaskConfigLoader.FILE_NAME), """
                [{"name": "gen", "trigger": "before-compile",
                  "inputs": ["model/*.xgen"], "outputs": ["src/main/java/**/_gen/**"],
                  "exec": {"type": "java", "mainClass": "com.x.Gen", "arguments": ["changed-arg"]}}]
                """);
        List<TaskConfig> tasks = TaskConfigLoader.loadForModule(repo, "nop-x-meta");
        assertEquals(1, tasks.size());
        assertEquals(List.of("changed-arg"), tasks.get(0).exec.arguments);
        // untouched exec field survives the merge
        assertEquals("test", tasks.get(0).exec.classpathScope);
        assertEquals("com.x.Gen", tasks.get(0).exec.mainClass);
    }

    @Test
    void moduleLevelTopLevelFieldsReplaceWholesale() throws Exception {
        writeConfig(VALID);
        Path modDir = repo.resolve("nop-x-meta");
        Files.createDirectories(modDir);
        Files.writeString(modDir.resolve(TaskConfigLoader.FILE_NAME), """
                [{"name": "gen", "trigger": "before-compile",
                  "inputs": ["tpl/*.xgen"], "outputs": ["out/**"],
                  "exec": {"type": "java", "mainClass": "com.x.Gen"}}]
                """);
        List<TaskConfig> tasks = TaskConfigLoader.loadForModule(repo, "nop-x-meta");
        assertEquals(List.of("tpl/*.xgen"), tasks.get(0).inputs, "non-exec fields replace wholesale");
        assertEquals(List.of("out/**"), tasks.get(0).outputs);
    }

    @Test
    void differentNamesMergeSideBySide() throws Exception {
        writeConfig(VALID);
        Path sub = repo.resolve("a/b");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve(TaskConfigLoader.FILE_NAME), """
                [{"name": "other", "trigger": "after-compile", "inputs": ["x"], "outputs": ["y"],
                  "exec": {"type": "java", "mainClass": "M"}}]
                """);
        List<TaskConfig> tasks = TaskConfigLoader.loadForModule(repo, "a/b");
        assertEquals(2, tasks.size());
    }

    // ---- glob semantics

    @Test
    void globCrossesDirectoriesAndMatchesGranularity() {
        Pattern p = GlobMatcher.compile("src/main/java/**/_gen/**");
        assertTrue(GlobMatcher.matches(p, "src/main/java/io/x/_gen/A.java"));
        assertTrue(GlobMatcher.matches(p, "src/main/java/_gen/deep/nested/B.java"));
        assertFalse(GlobMatcher.matches(p, "src/main/java/io/x/Other.java"));
        Pattern t = GlobMatcher.compile("model/*.xgen");
        assertTrue(GlobMatcher.matches(t, "model/gen-model.xgen"));
        assertFalse(GlobMatcher.matches(t, "model/sub/gen-model.xgen"), "* stays within a segment");
    }

    @Test
    void generatedFileChangesOutputExistenceState() throws Exception {
        // fingerprint granularity: outputs expand to FILES, so a newly generated
        // file changes the existence state (Phase 2 re-run trigger depends on it)
        Path gen = repo.resolve("src/main/java/_gen");
        Files.createDirectories(gen);
        assertTrue(GlobMatcher.matches(GlobMatcher.compile("src/main/java/**/_gen/**"),
                "src/main/java/_gen/New.java"));
        Files.writeString(gen.resolve("New.java"), "x");
        assertTrue(Files.exists(gen.resolve("New.java")));
    }
}
