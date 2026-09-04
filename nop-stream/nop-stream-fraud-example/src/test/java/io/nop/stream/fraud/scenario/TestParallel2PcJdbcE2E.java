/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.sql.DataSource;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.JDBC_P3_STREAM_PATH;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.JDBC_STREAM_PATH;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.TOTAL_KEYS;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.createDataTable;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.createLedgerTable;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.expectedValueCounts;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.hasEpochWithMultipleSubtasks;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.localJdbcResolver;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.readLedgerSubtasks;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.readValueCounts;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.buildEnv;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.newH2DataSource;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.newJdbcTemplate;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.parseStreamXml;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.registerCheckpointExecutorFactory;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.unregisterCheckpointExecutorFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CONN-01 successor (roadmap item 35) LOCAL e2e — the composite C2 routed form:
 * keyed-P&gt;1 + {@code JdbcTwoPhaseCommitSink} P=2 ({@code
 * fraud-parallel-2pc-jdbc.stream.xml}: HASH edge keyExpr=event -> P=2 map/sink
 * chain). This is the shape the CONN-01 planning gate used to reject; the gate is
 * removed and the proof obligation lands here (plan guide #22: user entry — XDSL
 * {@code env.execute()} — to final output — JDBC table rows).
 *
 * <p>Assertions (each maps to a plan exit criterion):
 * <ol>
 *   <li><b>Exactly-once multiset</b> — after BOTH runs the data table equals the
 *       full expected multiset (every {@code key-i} exactly once, no PK on the
 *       data table: a duplicate slip surfaces here, not in an SQL error).</li>
 *   <li><b>kill/recover (same P)</b> — run 2 restores from the latest durable
 *       epoch manifest and re-processes the tail: the final multiset is UNCHANGED
 *       (ledger idempotent guard + cursor restore), and ledger epochs strictly
 *       increased past run 1 (post-restore commits are new epochs).</li>
 *   <li><b>Per-subtask commit key (wiring verification, #23)</b> — the ledger
 *       contains an epoch committed by 2 DISTINCT subtask ids: the composite key
 *       (epoch_id, subtask_id) is the direct behavioral evidence that the runtime
 *       built independent per-subtask UDF copies whose subtask identity enters
 *       the commit key.</li>
 *   <li><b>D1 rejection (checkpoint-design.md §8.5.2)</b> — restoring the P=2
 *       checkpoint against the P=3 declaration
 *       ({@code fraud-parallel-2pc-jdbc-p3.stream.xml}) fails fast with
 *       {@code ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED} and the
 *       old/new parallelism params.</li>
 * </ol>
 */
public class TestParallel2PcJdbcE2E {

    @TempDir
    Path tempDir;

    private Path inputDir;
    private Path storageDir;

    private static DataSource dataSource;
    private static IJdbcTemplate jdbcTemplate;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        registerCheckpointExecutorFactory();
        dataSource = newH2DataSource("fraud-parallel-2pc-jdbc");
        jdbcTemplate = newJdbcTemplate(dataSource);
        createDataTable(jdbcTemplate);
        createLedgerTable(jdbcTemplate);
    }

    @AfterAll
    public static void destroy() {
        unregisterCheckpointExecutorFactory();
        CoreInitialization.destroy();
    }

    private void setUp() throws Exception {
        inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        storageDir = tempDir.resolve("checkpoints");
        // Time-partitioned fixture (same shape as TestS1CdcRecoveryE2E): run 1 sees
        // ONLY slice A; slice B is written before run 2, so the restore genuinely
        // resumes computing the remaining input (new commits in strictly greater
        // epochs) instead of re-reading an exhausted directory.
        writeKeys("part-1.txt", 0, TOTAL_KEYS / 2);
    }

    private void writeKeys(String fileName, int from, int to) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            sb.append("key-").append(i).append('\n');
        }
        Files.write(inputDir.resolve(fileName), sb.toString().getBytes());
    }

    private void run(String streamPath, Path storage) throws Exception {
        StreamModel model = parseStreamXml(streamPath);
        InMemoryBeanFunctionResolver resolver = localJdbcResolver(
                jdbcTemplate, inputDir.toString(), 25L, 600L);
        StreamExecutionEnvironment env = buildEnv(model, resolver, storage.toString(), null);
        env.execute("fraud-parallel-2pc-jdbc");
    }

    @Test
    public void parallelJdbc2PcSinkExactlyOnceWithRecoveryAndPerSubtaskLedger() throws Exception {
        setUp();

        // ---- run 1: keyed-P>1 + 2PC sink P=2 runs slice A to completion ----
        run(JDBC_STREAM_PATH, storageDir);

        Map<String, Integer> sliceA = expectedValueCounts();
        sliceA.keySet().removeIf(k -> Integer.parseInt(k.substring(4)) >= TOTAL_KEYS / 2);
        assertEquals(sliceA, readValueCounts(jdbcTemplate),
                "run 1 must commit slice A exactly once (multiset equality)");

        // run 1 leaves a durable epoch manifest (restore anchor for run 2)
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(storageDir.toString());
        assertNotNull(storage.loadLatestEpochManifest("stream-job", "pipeline-0"),
                "run 1 must leave a durable epoch manifest");
        long maxEpochAfterRun1 = maxLedgerEpoch();

        // Per-subtask commit key (wiring verification #23): some epoch carries
        // commits from 2 distinct subtasks — both independent sink copies keyed
        // the ledger with their own subtask identity.
        assertTrue(hasEpochWithMultipleSubtasks(jdbcTemplate),
                "ledger must contain an epoch committed by 2 distinct subtask ids "
                        + "(per-subtask commit-key evidence); ledger="
                        + readLedgerSubtasks(jdbcTemplate));

        // ---- run 2: kill/recover shape — restore, then resume slice B ----
        writeKeys("part-2.txt", TOTAL_KEYS / 2, TOTAL_KEYS);
        run(JDBC_STREAM_PATH, storageDir);

        assertEquals(expectedValueCounts(), readValueCounts(jdbcTemplate),
                "run 2 (restore + slice-B resume) must converge to the full multiset "
                        + "exactly-once (no duplicates from replay, no losses)");
        assertTrue(maxLedgerEpoch() > maxEpochAfterRun1,
                "run 2 must commit epochs strictly greater than run 1's: "
                        + maxLedgerEpoch() + " vs " + maxEpochAfterRun1);

        // ---- D1 rejection: P=3 declaration against the P=2 checkpoint ----
        // env.execute wraps failures in ERR_STREAM_JOB_EXECUTE_FAILED; the typed D1
        // rejection is carried on the cause chain (same unwrap pattern as
        // TestXplSourceCancelE2E).
        StreamException ex = assertThrows(StreamException.class,
                () -> run(JDBC_P3_STREAM_PATH, storageDir),
                "a restore across a 2PC sink parallelism change must fail fast (D1, §8.5.2)");
        StreamException d1 = findTypedCause(ex,
                NopStreamErrors.ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED);
        assertNotNull(d1, "cause chain must carry the typed D1 rejection; chain=" + chainToString(ex));
        assertEquals(2, d1.getParam("oldParallelism"));
        assertEquals(3, d1.getParam("newParallelism"));
        assertNotNull(d1.getParam("vertexId"));
    }

    private static StreamException findTypedCause(Throwable t, io.nop.api.core.exceptions.ErrorCode errorCode) {
        while (t != null) {
            if (t instanceof StreamException se && errorCode.getErrorCode().equals(se.getErrorCode())) {
                return se;
            }
            t = t.getCause();
        }
        return null;
    }

    private static String chainToString(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t != null) {
            sb.append(t.getClass().getName()).append(": ")
                    .append(t.getMessage() == null ? "(no message)" : t.getMessage()).append('\n');
            t = t.getCause();
        }
        return sb.toString();
    }

    private long maxLedgerEpoch() {
        long max = -1;
        for (Long epoch : readLedgerSubtasks(jdbcTemplate).keySet()) {
            max = Math.max(max, epoch);
        }
        return max;
    }
}
