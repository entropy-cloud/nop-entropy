/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.model.StreamModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.FILE_STREAM_PATH;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.TOTAL_KEYS;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.expectedValueCounts;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.localFileResolver;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.buildEnv;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.parseStreamXml;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.registerCheckpointExecutorFactory;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.unregisterCheckpointExecutorFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CONN-01 successor (roadmap item 35) LOCAL e2e — {@code FileTwoPhaseCommitSink}
 * at P=2 ({@code fraud-parallel-2pc-file.stream.xml}). Per-subtask isolation
 * evidence (the former gate's P0: shared pendingCommits / identical temp paths):
 *
 * <ol>
 *   <li><b>Per-subtask output files + manifest keys coexist</b> — some epoch N has
 *       BOTH the legacy unsuffixed file/manifest key (subtask 0:
 *       {@code epoch-N.txt} / key {@code N}) and the suffixed ones (subtask 1:
 *       {@code epoch-N.s1.txt} / key {@code N.s1}). This is the direct wiring
 *       evidence (#23) that the subtask identity entered the commit key.</li>
 *   <li><b>Exact output file set</b> — every file under the output dir is a
 *       committed epoch file (unsuffixed or {@code .sK}); no two subtasks
 *       overwrote each other's batch (a lost batch would miss keys in the
 *       multiset below).</li>
 *   <li><b>Exactly-once multiset incl. recovery</b> — two runs (the second
 *       restores from the latest durable manifest and replays the tail): the
 *       union of all committed lines equals the full expected multiset with no
 *       duplicates and no temp-file residue.</li>
 * </ol>
 *
 * <p><b>Coverage-matrix note (plan Phase 3)</b>: the File family is proven in the
 * LOCAL execution form here; the real multi-JVM proof is carried by the JDBC
 * family ({@code TestParallel2PcMultiJvmE2E}) — the multi-JVM file-sink shared
 * output directory would require a shared filesystem contract outside this
 * plan's scope (explicitly marked LOCAL-only in the matrix).
 */
public class TestParallel2PcFileE2E {

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

    private void setUp() throws Exception {
        inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        outputDir = tempDir.resolve("out");
        storageDir = tempDir.resolve("checkpoints");
        // Time-partitioned fixture (same shape as the JDBC variant): run 1 sees only
        // slice A; slice B lands before run 2, so the restore resumes the remaining
        // input and the no-duplicates obligation covers both the resumed commits and
        // any replayed tail.
        writeKeys("part-1.txt", 0, TOTAL_KEYS / 2);
    }

    private void writeKeys(String fileName, int from, int to) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            sb.append("key-").append(i).append('\n');
        }
        Files.write(inputDir.resolve(fileName), sb.toString().getBytes());
    }

    private void run(Path storage) throws Exception {
        StreamModel model = parseStreamXml(FILE_STREAM_PATH);
        InMemoryBeanFunctionResolver resolver = localFileResolver(
                inputDir.toString(), outputDir.toString(), 25L, 600L);
        StreamExecutionEnvironment env = buildEnv(model, resolver, storage.toString(), null);
        env.execute("fraud-parallel-2pc-file");
    }

    @Test
    public void parallelFile2PcSinkPerSubtaskFilesAndExactlyOnceWithRecovery() throws Exception {
        setUp();

        // ---- run 1: P=2 file sink to completion over slice A ----
        run(storageDir);

        // ---- run 2: restore + slice-B resume (kill/recover shape, same P) ----
        writeKeys("part-2.txt", TOTAL_KEYS / 2, TOTAL_KEYS);
        run(storageDir);

        // (1) per-subtask files + manifest keys coexist: some epoch N has BOTH
        // epoch-N.txt / key N (subtask 0) AND epoch-N.s1.txt / key N.s1 (subtask 1).
        Properties manifest = readManifest(outputDir);
        assertNotNull(manifest, "manifest.properties must exist after the runs");
        String sharedEpoch = null;
        for (String name : manifest.stringPropertyNames()) {
            String base = name.contains(".s") ? name.substring(0, name.indexOf(".s")) : name;
            if (!name.contains(".s") && manifest.containsKey(base + ".s1")) {
                sharedEpoch = base;
                break;
            }
        }
        assertNotNull(sharedEpoch,
                "some epoch must be committed by BOTH subtasks (legacy key + .s1 key); "
                        + "manifest keys=" + manifest.stringPropertyNames());
        assertTrue(Files.exists(outputDir.resolve("epoch-" + sharedEpoch + ".txt")),
                "subtask 0's unsuffixed epoch file must exist for epoch " + sharedEpoch);
        assertTrue(Files.exists(outputDir.resolve("epoch-" + sharedEpoch + ".s1.txt")),
                "subtask 1's .s1 epoch file must exist for epoch " + sharedEpoch);

        // (2) exact output file set: only committed epoch files (unsuffixed or .sK),
        // manifest keys == epoch file set (no orphans, no overwrites, no tmp residue).
        // AR-14 (plan 2026-09-04-1326-3): `.manifest.lock` is the EXPECTED cross-writer
        // manifest lock file (serializes parallel subtask manifest read-modify-write) —
        // a legitimate durable artifact, not residue.
        try (Stream<Path> files = Files.list(outputDir)) {
            for (Path p : files.toList()) {
                String name = p.getFileName().toString();
                assertTrue(name.equals("manifest.properties")
                                || name.equals(".manifest.lock")
                                || (name.startsWith("epoch-") && name.endsWith(".txt")),
                        "unexpected file in output dir (temp residue / overwrite artifact): " + name);
            }
        }
        int epochFileCount;
        try (Stream<Path> files = Files.list(outputDir)) {
            epochFileCount = (int) files.filter(p -> p.getFileName().toString().startsWith("epoch-")).count();
        }
        assertEquals(manifest.stringPropertyNames().size(), epochFileCount,
                "manifest key set must equal the committed epoch file set");

        // (3) exactly-once multiset over ALL committed files (both runs, no
        // duplicates from the recovery replay, no lost batches).
        assertEquals(expectedValueCounts(), readOutputLineCounts(outputDir),
                "union of committed lines must equal the full expected multiset");
    }

    private static Properties readManifest(Path outputDir) throws IOException {
        Path manifestPath = outputDir.resolve("manifest.properties");
        if (!Files.exists(manifestPath)) {
            return null;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(manifestPath)) {
            props.load(in);
        }
        return props;
    }

    /** Multiset read of every committed epoch file's lines (both subtask families). */
    static Map<String, Integer> readOutputLineCounts(Path outputDir) throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        List<Path> files;
        try (Stream<Path> stream = Files.list(outputDir)) {
            files = stream.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("epoch-") && n.endsWith(".txt");
            }).sorted().toList();
        }
        for (Path file : files) {
            for (String line : Files.readAllLines(file)) {
                if (!line.isBlank()) {
                    counts.merge(line, 1, Integer::sum);
                }
            }
        }
        return counts;
    }
}
