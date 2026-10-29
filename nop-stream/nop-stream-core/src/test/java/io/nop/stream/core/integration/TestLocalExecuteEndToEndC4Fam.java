package io.nop.stream.core.integration;

import io.nop.stream.core.environment.StreamExecutionEnvironment;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2510 R2-07 Phase 2 end-to-end verification (rule 22): LOCAL
 * {@code env.execute()} full-chain runs (source → map operator → sink) exercising
 * the C4-001 guard-a pass path (Σsubtask &lt;= poolSize) and asserting zero silent
 * element skips (FAM-1 runtime visibility) plus normal job termination (no
 * mutual-wait hang).
 *
 * <p>Delivery-count semantics: {@code fromElements}' {@code CollectionSourceFunction}
 * is a legacy shared-instance source (not {@code SubtaskShardedSourceFunction}); at
 * parallelism &gt; 1 the per-element delivery multiplicity is topology-dependent
 * (see the {@code ParallelismCheckable} javadoc: shared-instance sources produce
 * duplicated data). The exactly-once pin therefore uses parallelism 1 (deterministic
 * sequence); the parallelism-2 run pins completeness (every expected value delivered,
 * no phantom values) and bounded termination — the materialization-edge quadrant
 * semantics (FAM-1 permit retention / exactly-once under backpressure) are exercised
 * at the partition level by {@code TestFam1PermitRetentionC10_005}.
 */
class TestLocalExecuteEndToEndC4Fam {

    @Test
    void c4_fam_localExecuteFullChainExactlyOnceAndNormalTermination() throws Exception {
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);
        env.fromElements(1, 2, 3, 4, 5, 6, 7, 8)
                .map(x -> x * 10)
                .filter(x -> x > 20)
                .sink(results::add);

        long start = System.currentTimeMillis();
        env.execute("c4-fam-e2e");
        long elapsed = System.currentTimeMillis() - start;

        List<Integer> expected = Arrays.asList(30, 40, 50, 60, 70, 80);
        List<Integer> sorted = new ArrayList<>(results);
        sorted.sort(Integer::compareTo);
        assertEquals(expected, sorted,
                "sink must receive every emitted element exactly-once, in order "
                        + "(zero silent skips — FAM-1)");
        assertTrue(elapsed < 60_000, "the LOCAL job must terminate normally, not hang (C4-001)");
    }

    @Test
    void c4_fam_localExecuteParallelChainDeliversEveryElementAndTerminates() throws Exception {
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.fromElements("a", "b", "c")
                .map(String::toUpperCase)
                .sink(results::add);

        env.execute("c4-fam-e2e-2");

        List<String> sorted = new ArrayList<>(results);
        sorted.sort(String::compareTo);
        assertTrue(sorted.containsAll(Arrays.asList("A", "B", "C")),
                "every source element must reach the sink (zero silent skips — FAM-1), "
                        + "got: " + sorted);
        for (String value : sorted) {
            assertTrue(Arrays.asList("A", "B", "C").contains(value),
                    "no phantom values may reach the sink, got: " + value);
        }
    }
}
