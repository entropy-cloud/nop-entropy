package io.nop.ib.cli;

import io.nop.ib.model.IbModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defect 20 regression pin: the surefire-shaped fallback must delegate to the
 * Maven {@code test} LIFECYCLE phase with flag passthrough — never to the
 * {@code surefire:test} goal, whose direct invocation bypasses phase bindings
 * and the default-test execution's configuration (flink fs-tests: the goal
 * dispatch dispatched the integration set the unit execution excludes,
 * 41/7/2 vs the lifecycle's 40/7/1).
 */
class TestSurefireFallbackArgs {

    private IbModule module(String artifactId) {
        IbModule m = new IbModule();
        m.setArtifactId(artifactId);
        return m;
    }

    @Test
    void delegatesToTestLifecyclePhaseNotSurefireGoal() {
        String args = TestCommand.surefireFallbackArgs(module("flink-fs-tests"), null);
        assertTrue(args.contains("-pl :flink-fs-tests test"),
                "must run the test lifecycle phase, got: " + args);
        assertFalse(args.contains("surefire:test"),
                "direct goal invocation ignores phase bindings (defect 20): " + args);
    }

    @Test
    void passesEnforcerAndBuildCacheSkipFlags() {
        String args = TestCommand.surefireFallbackArgs(module("m"), List.of("a.BTest"));
        assertTrue(args.contains("-Denforcer.skip=true"),
                "enforcer version pins (e.g. flink locks Maven 3.8.6) must not gate the fallback");
        assertTrue(args.contains("-Dmaven.build.cache.skipCache=true"),
                "the fallback must not serve cached results");
    }

    @Test
    void explicitClassesUseTestPropertyWithNoFailOnEmpty() {
        String args = TestCommand.surefireFallbackArgs(module("m"),
                List.of("a.ATest", "b.BTest"));
        assertTrue(args.contains(" -Dtest=a.ATest,b.BTest"), args);
        assertTrue(args.contains("-DfailIfNoSpecifiedTests=false"), args);
    }

    @Test
    void fullModuleRunOmitsTestProperty() {
        String args = TestCommand.surefireFallbackArgs(module("m"), null);
        assertFalse(args.contains("-Dtest="), args);
        String empty = TestCommand.surefireFallbackArgs(module("m"), List.of());
        assertFalse(empty.contains("-Dtest="), empty);
        assertEquals(args, empty);
    }

    /**
     * Defect 20 co-finding pin: per-class surefire lines must not be summed as
     * module summaries — the real 40/7/1 flink-fs-tests run used to report
     * 80/14/2 (every test counted twice).
     */
    @Test
    void extractSummariesIgnoresPerClassLines() {
        String output = """
                [INFO] Running org.apache.flink.hdfstests.HDFSTest
                [ERROR] Tests run: 6, Failures: 6, Errors: 0, Skipped: 0, Time elapsed: 0.035 s -- in org.apache.flink.hdfstests.HDFSTest
                [INFO] Running org.apache.flink.hdfstests.ContinuousFileProcessingMigrationTest
                [INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.178 s -- in org.apache.flink.hdfstests.ContinuousFileProcessingMigrationTest
                [INFO]
                [ERROR] Tests run: 40, Failures: 7, Errors: 1, Skipped: 0
                [INFO] BUILD FAILURE
                """;
        List<int[]> summaries = TestCommand.extractSummaries(output);
        assertEquals(1, summaries.size(), "only the module summary must be extracted");
        assertArrayEquals(new int[]{40, 7, 1, 0}, summaries.get(0));
    }
}
