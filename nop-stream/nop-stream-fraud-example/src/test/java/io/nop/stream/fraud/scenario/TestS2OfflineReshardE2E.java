/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.rocksdb.RocksDBStateBackend;
import io.nop.stream.runtime.checkpoint.reshard.MaxParallelismReshardMigration;
import io.nop.stream.runtime.checkpoint.reshard.ReshardMigrationResult;
import io.nop.stream.runtime.checkpoint.storage.CheckpointSerDe;
import io.nop.stream.core.checkpoint.StorageJobIds;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.nop.stream.fraud.scenario.ScenarioTestSupport.S2_STREAM_PATH;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.T0;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.buildEnv;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.parseStreamXml;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.registerCheckpointExecutorFactory;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.s2Resolver;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.unregisterCheckpointExecutorFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2 offline maxParallelism reshard E2E (design §3.2.4 A2-5): the scenario
 * pipeline produces a REAL savepoint (keyed window state + source cursor
 * operator state), the offline {@link MaxParallelismReshardMigration} tool
 * reshards it 128 -> 256, and run 2 restores from the MIGRATED savepoint (new
 * maxParallelism backend) and completes the input with exactly-once output.
 *
 * <p>Key conservation on the scenario's real keyed state and cursor-state
 * preservation (slice A is NOT re-read after the migration) are both asserted.
 */
public class TestS2OfflineReshardE2E {

    private static final int OLD_MAX_P = 128;
    private static final int NEW_MAX_P = 256;

    @TempDir
    Path tempDir;

    private Path inputDir;
    private Path outputDir;
    private Path storageDir;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        registerCheckpointExecutorFactory();
    }

    @AfterAll
    public static void destroy() {
        unregisterCheckpointExecutorFactory();
        CoreInitialization.destroy();
    }

    private void setUp() throws IOException {
        inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        outputDir = tempDir.resolve("out");
        storageDir = tempDir.resolve("checkpoints");

        writeLines("part-a1.txt",
                "u1,50," + (T0 + 0),
                "u2,30," + (T0 + 1000),
                "u1,70," + (T0 + 2000),
                "u3,20," + (T0 + 3000),
                "u2,10," + (T0 + 1500));
        writeLines("part-a2.txt",
                "u1,30," + (T0 + 8000),
                "u3,40," + (T0 + 9000),
                "u2,60," + (T0 + 11000),
                "u1,20," + (T0 + 12000));    // W1 head in-flight across the migration

        writeLines("part-b1.txt",
                "u3,80," + (T0 + 16000),
                "u2,50," + (T0 + 17000),
                "u1,90," + (T0 + 21000),
                "u3,70," + (T0 + 22000),
                "u9,1," + (T0 + 25000),
                "u9,2," + (T0 + 26000),
                "u9,3," + (T0 + 32000),
                "u9,4," + (T0 + 33000),
                "u9,5," + (T0 + 34000),
                "u9,6," + (T0 + 35000),
                "u9,7," + (T0 + 36000),
                "u9,8," + (T0 + 37000),
                "u9,9," + (T0 + 38000));
    }

    private void writeLines(String fileName, String... lines) throws IOException {
        Files.write(inputDir.resolve(fileName), String.join("\n", lines).getBytes());
    }

    private void run(RocksDBStateBackend backend, String storage) throws Exception {
        StreamModel model = parseStreamXml(S2_STREAM_PATH);
        StreamExecutionEnvironment env = buildEnv(
                model,
                s2Resolver(inputDir.toString(), outputDir.toString(), 100L, 3000L),
                storage, backend);
        env.execute("fraud-s2-reshard");
    }

    @Test
    public void s2OfflineReshard128to256ThenRestoreCompletesExactlyOnce() throws Exception {
        setUp();

        // ---- run 1 at maxParallelism 128 (RocksDB backend) ----
        run(new RocksDBStateBackend(tempDir.resolve("rdb-run1").toString(), OLD_MAX_P),
                storageDir.toString());

        // ---- migrate the scenario's latest checkpoint 128 -> 256 ----
        String latest = findLatestCheckpointPath();
        assertNotNull(latest, "run 1 must have produced a durable checkpoint");

        Path outBase = tempDir.resolve("reshard-out");
        ReshardMigrationResult result = MaxParallelismReshardMigration.migrate(
                latest, OLD_MAX_P, NEW_MAX_P, outBase.toString());
        assertEquals(OLD_MAX_P, result.getOldMaxParallelism());
        assertEquals(NEW_MAX_P, result.getNewMaxParallelism());
        assertTrue(result.totalKeyedEntries() > 0,
                "the in-flight W1 keyed window state must be carried by the migration "
                        + "(key conservation on real scenario state)");
        assertTrue(Files.exists(Path.of(result.getNewSavepointPath())),
                "migrated savepoint must exist on disk");

        // the migrated savepoint really re-laid-out keys under the new maxP
        String newSavepointDirName = Path.of(result.getNewSavepointPath()).getFileName().toString();
        long migratedId = Long.parseLong(
                newSavepointDirName.substring("savepoint-".length()));
        io.nop.stream.core.checkpoint.CompletedCheckpoint migrated =
                CheckpointSerDe.deserializeCheckpoint(
                        Files.readAllBytes(Path.of(result.getNewSavepointPath())
                                .resolve(migratedId + ".checkpoint")));
        assertNotNull(migrated);

        // ---- run 2: restore from the MIGRATED savepoint under maxParallelism 256 ----
        // stage the migrated checkpoint as the only checkpoint of a fresh storage
        // (env-path restore resolves <base>/stream-job/pipeline-0/<id>.checkpoint)
        Path storage2 = tempDir.resolve("checkpoints-migrated");
        Path targetDir = storage2.resolve(StorageJobIds.sanitizeJobId("fraud-s2-reshard")).resolve("pipeline-0");
        Files.createDirectories(targetDir);
        Files.copy(Path.of(result.getNewSavepointPath()).resolve(migratedId + ".checkpoint"),
                targetDir.resolve(migratedId + ".checkpoint"), StandardCopyOption.REPLACE_EXISTING);

        run(new RocksDBStateBackend(tempDir.resolve("rdb-run2").toString(), NEW_MAX_P),
                storage2.toString());

        // ---- exactly-once completion over the full input ----
        Map<String, double[]> actual = new java.util.TreeMap<>();
        for (TxSummaryRow row : TestS2FileAggregationE2E.readOutputRows(outputDir)) {
            String key = row.getUserId() + "|" + row.getWindowStart();
            double[] agg = actual.computeIfAbsent(key, k -> new double[2]);
            agg[0] += row.getCount();
            agg[1] += row.getTotalAmount().doubleValue();
        }
        Map<String, double[]> expected = TestS2RecoveryAndRescaleE2E.fullExpected();
        assertEquals(expected.keySet(), actual.keySet(),
                "reshard-restored output must cover the full input exactly once.\nexpected="
                        + expected.keySet() + "\nactual=" + actual.keySet());
        for (Map.Entry<String, double[]> e : expected.entrySet()) {
            double[] act = actual.get(e.getKey());
            assertEquals(e.getValue()[0], act[0], 0.0001, "count for " + e.getKey());
            assertEquals(e.getValue()[1], act[1], 0.0001, "amount for " + e.getKey());
        }
        TestS2FileAggregationE2E.assertNoTempResidue(outputDir);
    }

    /** Finds the latest durable checkpoint file of the env-path storage layout. */
    private String findLatestCheckpointPath() throws IOException {
        Path dir = storageDir.resolve(StorageJobIds.sanitizeJobId("fraud-s2-reshard")).resolve("pipeline-0");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        long bestId = -1;
        Path best = null;
        try (var files = Files.list(dir)) {
            for (Path p : files.toList()) {
                String name = p.getFileName().toString();
                if (name.endsWith(".checkpoint")) {
                    long id = Long.parseLong(name.substring(0, name.length() - ".checkpoint".length()));
                    if (id > bestId) {
                        bestId = id;
                        best = p;
                    }
                }
            }
        }
        return best != null ? best.toString() : null;
    }
}
