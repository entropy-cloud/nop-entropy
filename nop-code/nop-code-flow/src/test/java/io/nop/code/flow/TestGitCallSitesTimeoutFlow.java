package io.nop.code.flow;

import io.nop.code.core.util.GitProcessRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-04 (plan 2565, adjudication C / facility F1): ChangeAnalyzer site leg — a bridge timedOut
 * result must surface as truncated=true WITHOUT throwing (parse contract preserved), within
 * the test window. Bridge hang-stub behavior proven in nop-code-core TestGitProcessTimeout.
 */
class TestGitCallSitesTimeoutFlow {

    @Test
    void testParseGitDiffTimedOutIsTruncatedNotThrown() {
        ChangeAnalyzer analyzer = new ChangeAnalyzer();
        analyzer.setGitRunnerForTest(new GitProcessRunner("git") {
            @Override
            public Result runLines(java.nio.file.Path dir, long timeoutMillis, List<String> args,
                                   java.util.Map<String, String> extraEnv, LineSink sink) {
                return new Result(null, null, true);
            }
        });

        long start = System.currentTimeMillis();
        ChangeAnalyzer.DiffResult diff = analyzer.parseGitDiff("HEAD~1", "HEAD", null);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(diff, "must not throw on bridge timeout");
        assertTrue(diff.truncated, "bridge timedOut must map to truncated=true");
        assertTrue(diff.fileChanges.isEmpty());
        assertTrue(elapsed < 10_000L, "must return within the window, took " + elapsed + "ms");
    }

    @Test
    void testParseGitDiffDelegatesLinesToBridgeSink() {
        // parse contract preserved: the sink receives diff lines and the state machine
        // produces the same hunk map as the pre-bridge inline loop
        ChangeAnalyzer analyzer = new ChangeAnalyzer();
        AtomicInteger lineCount = new AtomicInteger();
        analyzer.setGitRunnerForTest(new GitProcessRunner("git") {
            @Override
            public Result runLines(java.nio.file.Path dir, long timeoutMillis, List<String> args,
                                   java.util.Map<String, String> extraEnv, LineSink sink) {
                sink.accept("diff --git a/Foo.java b/Foo.java");
                sink.accept("--- a/Foo.java");
                sink.accept("+++ b/Foo.java");
                sink.accept("@@ -10,3 +10,4 @@");
                sink.accept(" context");
                lineCount.set(5);
                return new Result(null, 0, false);
            }
        });

        ChangeAnalyzer.DiffResult diff = analyzer.parseGitDiff("a", "b", null);
        assertFalse(diff.truncated);
        assertEquals(5, lineCount.get());
        assertTrue(diff.fileChanges.containsKey("Foo.java"));
        assertEquals(1, diff.fileChanges.get("Foo.java").size());
        assertEquals(10, diff.fileChanges.get("Foo.java").get(0).start);
        assertEquals(13, diff.fileChanges.get("Foo.java").get(0).end);
    }

    @Test
    void testParseGitDiffNonZeroExitIsTruncated() {
        ChangeAnalyzer analyzer = new ChangeAnalyzer();
        analyzer.setGitRunnerForTest(new GitProcessRunner("git") {
            @Override
            public Result runLines(java.nio.file.Path dir, long timeoutMillis, List<String> args,
                                   java.util.Map<String, String> extraEnv, LineSink sink) {
                return new Result(null, 128, false);
            }
        });
        ChangeAnalyzer.DiffResult diff = analyzer.parseGitDiff("bad~1", "bad~2", null);
        assertTrue(diff.truncated, "non-zero git exit (P1-6.7-009) must surface as truncated");
    }
}
