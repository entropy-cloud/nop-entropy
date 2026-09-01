/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.fraud.model.TransactionEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.nop.stream.fraud.scenario.ScenarioTestSupport.S2_DELTA_STREAM_PATH;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.S2_STREAM_PATH;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.T0;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.WINDOW_SIZE_MS;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.buildEnv;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.parseStreamXml;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.registerCheckpointExecutorFactory;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.s2Resolver;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.txWatermarks;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.unregisterCheckpointExecutorFactory;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.windowStart;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2 composite scenario E2E (roadmap item 13, design §3.2): file source ->
 * parse -> watermarks -> keyBy -> window aggregate -> exactly-once file sink,
 * covering assertions A2-1 (exact aggregation), A2-2 (delta effectiveness),
 * A2-3 (DataStream-API entry equivalence) and A2-7 (file sink exactly-once
 * output-directory contract).
 */
public class TestS2FileAggregationE2E {

    @TempDir
    Path tempDir;

    private Path inputDir;
    private Path baseOutput;
    private Path deltaOutput;
    private Path javaOutput;
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
        // unique input/output/storage per test method: runs must not restore or
        // read another test's checkpoints/output
        String runId = String.valueOf(System.nanoTime());
        inputDir = tempDir.resolve("input-" + runId);
        Files.createDirectories(inputDir);
        baseOutput = tempDir.resolve("out-base-" + runId);
        deltaOutput = tempDir.resolve("out-delta-" + runId);
        javaOutput = tempDir.resolve("out-java-" + runId);
        storageDir = tempDir.resolve("checkpoints-" + runId);

        // part-001: W0 events with intra-file out-of-order arrival + the
        // watermark-flush tail. The u9 lines at 25/26/27s (window W2) advance the
        // watermark so the W0/W1 windows fire mid-run on periodic checkpoints; the
        // FINAL line (u9@32s) is the terminator that fires W2 itself — its own row
        // stays in-flight at end-of-stream (engine semantics: 2PC sinks commit only
        // on completed checkpoints) and is completed by the recovery run instead.
        writeLines("part-001.txt",
                "u2,30," + (T0 + 1000),
                "u1,70," + (T0 + 2000),
                "u1,50," + (T0 + 0),          // out-of-order (eventTime before prior line)
                "user-bob,100," + (T0 + 3000),
                "u3,80," + (T0 + 4000),
                "u2,20," + (T0 + 1500),      // out-of-order
                "u1,60," + (T0 + 8000),
                "user-eve,90," + (T0 + 9000),
                "u3,40," + (T0 + 12000),
                "u1,90," + (T0 + 11000),
                "user-bob,110," + (T0 + 13000),
                "u9,1," + (T0 + 25000),
                "u9,2," + (T0 + 26000),
                "u9,3," + (T0 + 27000),
                "u9,4," + (T0 + 32000));
    }

    private void writeLines(String fileName, String... lines) throws IOException {
        Files.write(inputDir.resolve(fileName), String.join("\n", lines).getBytes());
    }

    // ----------------------------------------------------------------
    // Expected outputs (pre-computed from the fixture, window = 10s)
    // ----------------------------------------------------------------

    private Set<TxSummaryRow> baseExpected() {
        Set<TxSummaryRow> rows = new LinkedHashSet<>();
        // W0
        rows.add(row("u1", T0, 3, "180"));            // 70 + 50 + 60
        rows.add(row("u2", T0, 2, "50"));             // 30 + 20
        rows.add(row("user-bob", T0, 1, "100"));
        rows.add(row("u3", T0, 1, "80"));
        rows.add(row("user-eve", T0, 1, "90"));
        // W1
        rows.add(row("u3", T0 + 10000, 1, "40"));
        rows.add(row("u1", T0 + 10000, 1, "90"));
        rows.add(row("user-bob", T0 + 10000, 1, "110"));
        // W2 (flush lines)
        rows.add(row("u9", T0 + 20000, 3, "6"));
        return rows;
    }

    private Set<TxSummaryRow> deltaExpected() {
        // A2-2: base minus ALL rows of blacklisted users (user-bob, user-eve)
        Set<TxSummaryRow> rows = new LinkedHashSet<>();
        for (TxSummaryRow row : baseExpected()) {
            if (!"user-bob".equals(row.getUserId()) && !"user-eve".equals(row.getUserId())) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static TxSummaryRow row(String userId, long anyEventTime, long count, String total) {
        long start = windowStart(anyEventTime);
        return new TxSummaryRow(userId, start, start + WINDOW_SIZE_MS, count, new BigDecimal(total));
    }

    // ----------------------------------------------------------------
    // Output-directory reading (D6 assertion surface)
    // ----------------------------------------------------------------

    /** All committed epoch file lines, parsed as TxSummaryRow. */
    static List<TxSummaryRow> readOutputRows(Path outputDir) throws IOException {
        List<TxSummaryRow> rows = new ArrayList<>();
        try (Stream<Path> files = Files.list(outputDir)) {
            List<Path> epochFiles = new ArrayList<>();
            files.filter(p -> p.getFileName().toString().startsWith("epoch-"))
                    .sorted().forEach(epochFiles::add);
            for (Path file : epochFiles) {
                for (String line : Files.readAllLines(file)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] parts = line.split("\\|");
                    rows.add(new TxSummaryRow(parts[0], Long.parseLong(parts[1]),
                            Long.parseLong(parts[2]), Long.parseLong(parts[3]),
                            new BigDecimal(parts[4])));
                }
            }
        }
        return rows;
    }

    static Set<String> readManifestKeys(Path outputDir) throws IOException {
        Path manifest = outputDir.resolve("manifest.properties");
        Properties props = new Properties();
        try (var in = Files.newInputStream(manifest)) {
            props.load(in);
        }
        return props.keySet().stream().map(Object::toString)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    static void assertNoTempResidue(Path outputDir) throws IOException {
        try (Stream<Path> files = Files.list(outputDir)) {
            List<Path> temps = files.filter(p -> p.getFileName().toString().contains(".tmp"))
                    .toList();
            assertTrue(temps.isEmpty(), "no .tmp residue allowed in 2PC output dir: " + temps);
        }
    }

    static void assertExactlyOnceOutput(Path outputDir, Set<TxSummaryRow> expected) throws IOException {
        List<TxSummaryRow> actual = readOutputRows(outputDir);
        // multiset equality: every expected row appears exactly once
        Map<TxSummaryRow, Integer> expectedCounts = new TreeMap<>(java.util.Comparator
                .comparing(TxSummaryRow::toString));
        for (TxSummaryRow row : expected) {
            expectedCounts.merge(row, 1, Integer::sum);
        }
        Map<TxSummaryRow, Integer> actualCounts = new TreeMap<>(java.util.Comparator
                .comparing(TxSummaryRow::toString));
        for (TxSummaryRow row : actual) {
            actualCounts.merge(row, 1, Integer::sum);
        }
        assertEquals(expectedCounts, actualCounts,
                "epoch file lines must equal the expected per-window rows exactly once "
                        + "(no duplicates, no losses)");
        // manifest keys == committed epoch x subtask set == the epoch files present
        Set<String> epochFiles = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(outputDir)) {
            files.filter(p -> p.getFileName().toString().startsWith("epoch-")).forEach(p -> {
                String name = p.getFileName().toString();
                epochFiles.add(name.substring("epoch-".length(), name.length() - ".txt".length()));
            });
        }
        assertEquals(epochFiles, readManifestKeys(outputDir),
                "manifest keys must equal the committed epoch files");
        assertNoTempResidue(outputDir);
    }

    // ----------------------------------------------------------------
    // Runs
    // ----------------------------------------------------------------

    private void runXdsl(String streamPath, Path outputDir) throws Exception {
        // Each pipeline VARIANT gets its own checkpoint storage: the delta model
        // has a different DAG fingerprint than the base model, and manifest
        // restore correctly fail-fasts on fingerprint mismatch.
        StreamExecutionEnvironment env = buildEnv(
                parseStreamXml(streamPath),
                s2Resolver(inputDir.toString(), outputDir.toString(), 100L, 600L),
                storageDir.resolve(outputDir.getFileName()).toString(), null);
        env.execute("fraud-s2-" + outputDir.getFileName());
    }

    /** A2-3: the same topology assembled through the DataStream API. */
    private void runJavaVariant(Path outputDir) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.enableCheckpointing(100L);
        env.getCheckpointConfig().setMinPause(50L);
        env.getCheckpointConfig().setStorageProperty("path", storageDir.resolve("java").toString());
        env.addSource(new DirectoryFileSourceFunction(inputDir.toString(), 100L, 600L), "file-source")
                .map(new TransactionLineParser())
                .assignTimestampsAndWatermarks(txWatermarks())
                .keyBy((KeySelector<TransactionEvent, String>) TransactionEvent::getUserId)
                .window(TumblingEventTimeWindows.of(WINDOW_SIZE_MS))
                .aggregate(new TransactionWindowAggregate(WINDOW_SIZE_MS))
                .sink(new io.nop.stream.connector.file.FileTwoPhaseCommitSink<>(outputDir.toString()));
        env.execute("fraud-s2-java");
    }

    @Test
    public void s2XdslBaseAggregatesExactlyAndSinkIsExactlyOnce() throws Exception {
        setUp();
        runXdsl(S2_STREAM_PATH, baseOutput);
        assertExactlyOnceOutput(baseOutput, baseExpected());
    }

    @Test
    public void s2DeltaBlacklistRemovesExactlyTheBlacklistedRows() throws Exception {
        setUp();
        runXdsl(S2_STREAM_PATH, baseOutput);
        runXdsl(S2_DELTA_STREAM_PATH, deltaOutput);

        Set<TxSummaryRow> deltaRows = new LinkedHashSet<>(readOutputRows(deltaOutput));
        Set<TxSummaryRow> deltaExpected = deltaExpected();
        assertEquals(deltaExpected, deltaRows,
                "S2-delta output must equal S2-base output minus all blacklisted-user rows");

        // delta-unique behaviour proof: blacklisted rows present in base output,
        // absent in delta output (the delta actually rewired the topology)
        Set<String> baseUsers = new LinkedHashSet<>();
        readOutputRows(baseOutput).forEach(r -> baseUsers.add(r.getUserId()));
        assertTrue(baseUsers.contains("user-bob") && baseUsers.contains("user-eve"),
                "base output must contain the blacklisted users (delta removes them)");
        for (TxSummaryRow row : deltaRows) {
            assertFalse("user-bob".equals(row.getUserId()) || "user-eve".equals(row.getUserId()),
                    "blacklisted user row must not survive the delta filter: " + row);
        }
    }

    @Test
    public void s2JavaVariantMatchesXdslOutput() throws Exception {
        setUp();
        runXdsl(S2_STREAM_PATH, baseOutput);
        runJavaVariant(javaOutput);

        Set<TxSummaryRow> xdsl = new LinkedHashSet<>(readOutputRows(baseOutput));
        Set<TxSummaryRow> javaVariant = new LinkedHashSet<>(readOutputRows(javaOutput));
        assertEquals(xdsl, javaVariant,
                "DataStream-API entry must produce the same output as the XDSL entry "
                        + "(three-entry-point unification, A2-3)");
        assertEquals(baseExpected(), javaVariant, "java variant must hit the expected set");
    }
}
