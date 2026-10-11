/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10 (plan 2426): MAINTAINER TOOL — regenerates the legacy checkpoint fixture
 * under {@code src/test/resources/checkpoint-legacy-fixture/}.
 *
 * <p>Gated behind {@code -Dnop.stream.test.fixture.regenerate=true} so it never
 * runs in the default suite: the fixture is the cross-version FORMAT pin
 * (roadmap WI10 hard constraint — stored bytes must stay restorable), and it may
 * only be regenerated DELIBERATELY when the format legitimately evolves, with
 * the evolution recorded in the roadmap/design docs (see the fixture README).
 */
class TestLegacyCheckpointFixtureGenerator {

    @TempDir
    Path tempDir;

    @Test
    void regenerateFixture() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Boolean.getBoolean("nop.stream.test.fixture.regenerate"),
                "fixture regeneration is an explicit maintainer action "
                        + "(-Dnop.stream.test.fixture.regenerate=true)");

        String jobId = "fixture-source-job";
        Path storageDir = tempDir.resolve("storage");
        List<Integer> committed = new ArrayList<>();

        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setProcessingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE);
        config.setCheckpointInterval(150L);
        config.setCheckpointTimeout(30_000L);
        config.setMaxRetainedCheckpoints(5);
        config.setStorageProperty("path", storageDir.toFile().getAbsolutePath());
        config.setJobId(jobId);
        config.setPipelineId("pipeline-0");

        io.nop.stream.core.environment.StreamExecutionEnvironment environment =
                io.nop.stream.core.environment.StreamExecutionEnvironment.createTestEnvironment();
        environment.setParallelism(1);
        environment.enableCheckpointing(150L);
        environment.getCheckpointConfig().setProcessingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE);
        environment.addSource(
                new TestCheckpointDualRunDifferential.PacingInput<>(
                        TestCheckpointDualRunDifferential.firstInput(), 25L), "paced-source")
                .map(v -> v)
                .keyBy(v -> v % 2)
                .reduce((a, b) -> a + b)
                .sink(new TestCheckpointDualRunDifferential.RecordingTwoPhaseSink(jobId, committed));
        JobGraph jobGraph = environment.buildJobGraph(jobId);
        GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, jobId, config);

        assertTrue(!committed.isEmpty(), "generator run must commit outputs");
        assertTrue(TestCheckpointDualRunDifferential.countManifests(
                storageDir.toFile().getAbsolutePath()) >= 2, "fixture must contain >= 2 epochs");

        // Copy the storage tree into test resources.
        Path target = Path.of("src/test/resources/checkpoint-legacy-fixture/storage");
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(target),
                "refusing to overwrite an existing fixture — delete it explicitly first "
                        + "(format evolution must be a recorded decision)");
        copyTree(storageDir, target);
        System.out.println("Fixture written to " + target.toAbsolutePath()
                + " (" + TestCheckpointDualRunDifferential.countManifests(target.toString()) + " manifests)");
    }

    static void copyTree(Path from, Path to) throws Exception {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path src : walk.toList()) {
                Path rel = from.relativize(src);
                Path dst = to.resolve(rel);
                if (src.toFile().isDirectory()) {
                    Files.createDirectories(dst);
                } else {
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
