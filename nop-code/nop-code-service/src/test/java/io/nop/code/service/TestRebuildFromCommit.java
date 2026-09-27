package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N2.4 end-to-end: triggerRebuildFromCommit over a real git repository fixture — rebuild,
 * replay idempotency, debounce, no-change skip, HEAD consistency guard.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestRebuildFromCommit extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @TempDir
    Path tempDir;

    // exact copy of the proven TestGraphMetricMaterialization fixture (call-resolving)
    private static final String ALPHA_JAVA = """
            package demo;

            import demo.svc.GreeterService;
            import demo.util.NameUtil;

            public class Alpha {
                private GreeterService greeter = new GreeterService();

                public String run(String name) {
                    String normalized = NameUtil.normalize(name);
                    return greeter.greet(normalized);
                }

                public static void main(String[] args) {
                    Alpha alpha = new Alpha();
                    alpha.run("world");
                }
            }
            """;

    private static final String NAME_UTIL_JAVA = """
            package demo.util;

            public class NameUtil {
                public static String normalize(String name) {
                    return name.trim().toLowerCase();
                }
            }
            """;

    private static final String GREETER_JAVA = """
            package demo.svc;

            import demo.util.NameUtil;

            public class GreeterService {
                public String greet(String name) {
                    return "hello " + NameUtil.normalize(name);
                }
            }
            """;

    private static final String GREETER_V2_JAVA = """
            package demo.svc;

            import demo.util.NameUtil;

            public class GreeterService {
                public String greet(String name) {
                    return "hello " + NameUtil.normalize(name);
                }

                public String shout(String name) {
                    return NameUtil.normalize(name).toUpperCase();
                }
            }
            """;

    private Path repo;
    private String base;
    private String target;
    private String indexId;

    private void assumeGit() {
        try {
            new ProcessBuilder("git", "--version").start().waitFor();
        } catch (Exception e) {
            fail("git not available");
        }
    }

    private String execGit(String... args) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(repo.toAbsolutePath().toString());
        cmd.addAll(java.util.Arrays.asList(args));
        Process pb = new ProcessBuilder(cmd).start();
        String out;
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(pb.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            out = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
        int code = pb.waitFor();
        assertEquals(0, code, "git failed: " + String.join(" ", cmd) + " -> " + out);
        return out.trim();
    }

    private void writeJava(String name, String content) throws Exception {
        Path file = repo.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void commit(String msg) throws Exception {
        execGit("add", ".");
        execGit("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", msg);
        Thread.sleep(50);
    }

    private void setupRepo() throws Exception {
        repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        execGit("init");
        writeJava("src/demo/util/NameUtil.java", NAME_UTIL_JAVA);
        writeJava("src/demo/svc/GreeterService.java", GREETER_JAVA);
        writeJava("src/demo/Alpha.java", ALPHA_JAVA);
        commit("c1");
        base = execGit("rev-parse", "HEAD");

        indexId = "n24-rebuild";
        // full index while the worktree is at the baseline commit
        codeIndexService.indexDirectory(indexId, repo.toAbsolutePath().toString(), null);
    }

    private void advanceToTarget() throws Exception {
        writeJava("src/demo/svc/GreeterService.java", GREETER_V2_JAVA);
        commit("c2");
        target = execGit("rev-parse", "HEAD");
    }

    private void setDebounce(long millis) throws Exception {
        java.lang.reflect.Field f = codeIndexService.getClass().getDeclaredField("debounceMillis");
        f.setAccessible(true);
        f.setLong(codeIndexService, millis);
    }

    private void resetDebounce() throws Exception {
        java.lang.reflect.Field f = codeIndexService.getClass().getDeclaredField("rebuildDebounceMap");
        f.setAccessible(true);
        ((java.util.concurrent.ConcurrentHashMap<?, ?>) f.get(codeIndexService)).clear();
    }

    private long countFileRows(String indexId) {
        IEntityDao<io.nop.code.dao.entity.NopCodeFile> dao =
                daoProvider.daoFor(io.nop.code.dao.entity.NopCodeFile.class);
        return dao.findAllByQuery(new io.nop.api.core.beans.query.QueryBean()
                        .addFilter(io.nop.api.core.beans.FilterBeans.eq("indexId", indexId)))
                .size();
    }

    private long countRows(String indexId, String metricType) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        return dao.findAllByQuery(new io.nop.api.core.beans.query.QueryBean()
                        .addFilter(io.nop.api.core.beans.FilterBeans.and(
                                io.nop.api.core.beans.FilterBeans.eq("indexId", indexId),
                                io.nop.api.core.beans.FilterBeans.eq("metricType", metricType))))
                .size();
    }

    @Test
    void testRebuildIndexesChangesAndInvalidatesMetrics() throws Exception {
        assumeGit();
        setupRepo();
        setDebounce(0);
        codeIndexService.materializeGraphMetrics(indexId);
        assertTrue(countRows(indexId, "HUB") > 0);

        advanceToTarget();
        var result = codeIndexService.triggerRebuildFromCommit(indexId,
                repo.toAbsolutePath().toString(), base, target);
        assertFalse(result.isDebounced());
        assertFalse(result.isSkippedNoChanges());
        assertTrue(result.getChangedCount() > 0,
                "rebuild should index the changed file: msg=" + result.getStatusMessage());
        // N1.2 semantics: actual change invalidates materialized rows
        assertEquals(0, countRows(indexId, "HUB"));

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testReplayIdempotentAfterDebounceReset() throws Exception {
        assumeGit();
        setupRepo();
        setDebounce(0);
        String id = "n24-replay";
        advanceToTarget();

        var first = codeIndexService.triggerRebuildFromCommit(id,
                repo.toAbsolutePath().toString(), base, target);
        assertTrue(first.getChangedCount() > 0,
                "first rebuild should index: msg=" + first.getStatusMessage());
        long filesAfterFirst = countFileRows(id);
        resetDebounce();
        // replay: fingerprint detection skips already-indexed files -> changedCount 0, no side effects
        var second = codeIndexService.triggerRebuildFromCommit(id,
                repo.toAbsolutePath().toString(), base, target);
        assertEquals(0, second.getChangedCount(), "replay must skip already-indexed changes");
        assertEquals(filesAfterFirst, countFileRows(id), "replay must not duplicate file records");

        codeIndexService.deleteIndex(id);
    }

    @Test
    void testDebounceRejectsSecondCallWithinWindow() throws Exception {
        assumeGit();
        setDebounce(0);
        setupRepo();
        advanceToTarget();
        setDebounce(30_000);

        var first = codeIndexService.triggerRebuildFromCommit(indexId,
                repo.toAbsolutePath().toString(), base, target);
        assertFalse(first.isDebounced());

        var second = codeIndexService.triggerRebuildFromCommit(indexId,
                repo.toAbsolutePath().toString(), base, target);
        assertTrue(second.isDebounced(), "second call within window must be debounced");
        assertEquals(0, second.getChangedCount());

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testNoChangeCommitsSkipped() throws Exception {
        assumeGit();
        setupRepo();
        setDebounce(0);
        // a commit that changes nothing indexed (e.g. README)
        Files.writeString(repo.resolve("README.md"), "docs\n");
        commit("docs");
        String newTarget = execGit("rev-parse", "HEAD");

        var result = codeIndexService.triggerRebuildFromCommit(indexId,
                repo.toAbsolutePath().toString(), base, newTarget);
        // git diff reports README.md, but fingerprint pipeline no-ops on non-indexed files
        assertTrue(result.getChangedCount() == 0);
        assertFalse(result.isDebounced());

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testBaselineEqualsTargetSkipsViaGitDiff() throws Exception {
        assumeGit();
        setupRepo();
        setDebounce(0);
        // baseline == target: git diff empty -> short-circuit before the pipeline
        var result = codeIndexService.triggerRebuildFromCommit(indexId,
                repo.toAbsolutePath().toString(), base, base);
        assertTrue(result.isSkippedNoChanges(), "empty git diff must short-circuit");
        assertEquals(0, result.getChangedCount());
        assertFalse(result.isDebounced());

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testHeadBehindTargetThrowsHeadMismatch() throws Exception {
        assumeGit();
        setupRepo();
        setDebounce(0);
        advanceToTarget();
        // worktree advances past target (c3): HEAD != target
        // -> ERR_CODE_REBUILD_HEAD_MISMATCH (rev-parse comparison branch)
        writeJava("src/demo/Extra.java", "package demo;\npublic class Extra { }");
        commit("c3");
        assertThrows(Exception.class, () -> codeIndexService.triggerRebuildFromCommit(indexId,
                repo.toAbsolutePath().toString(), base, target));

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testHeadMismatchThrows() throws Exception {
        assumeGit();
        setupRepo();
        setDebounce(0);

        // worktree HEAD is at target; pass an unrelated commitish as target -> must throw
        assertThrows(Exception.class, () -> codeIndexService.triggerRebuildFromCommit(indexId,
                repo.toAbsolutePath().toString(), base, "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testSubdirectoryProjectPathThrows() throws Exception {
        assumeGit();
        setupRepo();
        setDebounce(0);
        Path subdir = repo.resolve("src/demo");

        assertThrows(Exception.class, () -> codeIndexService.triggerRebuildFromCommit(indexId,
                subdir.toAbsolutePath().toString(), base, target));

        codeIndexService.deleteIndex(indexId);
    }
}
