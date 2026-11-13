/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.core.operators.windowing;

import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.metrics.StreamMetricsRegistries;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.join.EquiJoinOperator;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.41 R4-01 E2 pin for W-15 (finding 1.1-C7-012 ← C5-008; plan 2534 ruling
 * E.3): operator-layer counters must register under STABLE keys (deployment
 * TaskLocation scope; stable subtask-identity fallback — never
 * {@code identityHashCode}) and must be REMOVED from the registry on operator
 * close. Pre-fix the tags embedded {@code SimpleName@identityHashCode} (every
 * redeploy minted a fresh meter — the counter fleet grew with every restart) and
 * close() removed nothing: the post-close lookups here returned live meters (red).
 *
 * <p>Same-key rebuild is idempotent: Micrometer returns the existing meter for a
 * repeated id, so instances rebuilt on the same scope (redeploy window overlap)
 * share one counter, and close-removal converges.
 */
class TestOperatorCounterStableKeyAndCloseRemovalR401 {

    private static io.micrometer.core.instrument.Meter findMeter(String name, String operatorTag,
                                                                 String subtaskTag) {
        return StreamMetricsRegistries.registry()
                .find(name)
                .tags("operator", operatorTag, "subtask", subtaskTag)
                .meter();
    }

    private static WindowingTestSupport.TestableWindowOperator newWindowOperator() {
        return new WindowingTestSupport.TestableWindowOperator(
                TumblingEventTimeWindows.of(100L),
                new WindowingTestSupport.SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new WindowingTestSupport.SimpleStringSerializer(),
                String.class,
                new WindowingTestSupport.ToStringWindowFunction<>(),
                EventTimeTrigger.create(),
                0L,
                null);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void locatedWindowOperatorUsesStableKeyAndCloseRemovesCounter() throws Exception {
        WindowingTestSupport.TestableWindowOperator base = newWindowOperator();
        WindowOperator located = base.copyForSubtask(new TaskLocation("job-r15", "p1", "vertex-win", 0));
        located.setOutput((Output) new TestOutput<String>());
        located.open();
        try {
            String operatorTag = located.getLateDropMetricOperatorTag();
            assertEquals("job-r15.p1.vertex-win", operatorTag,
                    "the TaskLocation path keeps the W-M1 scope key");
            assertEquals("0", located.getLateDropMetricSubtaskTag());
            assertFalse(operatorTag.contains("@"),
                    "the stable key must not embed identityHashCode: " + operatorTag);
            assertNotNull(findMeter("numLateRecordsDropped", operatorTag, "0"),
                    "precondition: the counter is registered under the stable key");

            located.close();
            assertNull(findMeter("numLateRecordsDropped", operatorTag, "0"),
                    "operator close must remove the counter from the registry (W-15)");
        } finally {
            located.close(); // idempotent sweep for the shared registry's hygiene
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void locationlessWindowOperatorUsesStableFallbackKeyAndCloseRemoves() throws Exception {
        WindowOperator located = newWindowOperator().copyForSubtask(3);
        located.setOutput((Output) new TestOutput<String>());
        located.open();
        try {
            String operatorTag = located.getLateDropMetricOperatorTag();
            assertFalse(operatorTag.contains("@"),
                    "the no-location fallback must be a stable injected key, not identityHashCode: "
                            + operatorTag);
            assertEquals("3", located.getLateDropMetricSubtaskTag(),
                    "the injected subtask index must surface in the fallback scope");
            assertNotNull(findMeter("numLateRecordsDropped", operatorTag, "3"),
                    "precondition: the counter is registered under the stable fallback key");

            located.close();
            assertNull(findMeter("numLateRecordsDropped", operatorTag, "3"),
                    "operator close must remove the counter from the registry (W-15)");
        } finally {
            located.close();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void sameKeyRebuildSharesCounterIdempotentlyAndLastCloseRemoves() throws Exception {
        TaskLocation location = new TaskLocation("job-r15s", "p1", "vertex-win", 2);
        WindowOperator first = newWindowOperator().copyForSubtask(location);
        first.setOutput((Output) new TestOutput<String>());
        first.open();

        // redeploy-window overlap: the rebuilt instance re-resolves the SAME meter id
        WindowOperator second = newWindowOperator().copyForSubtask(location);
        second.setOutput((Output) new TestOutput<String>());
        second.open();

        io.micrometer.core.instrument.Counter counterFirst =
                StreamMetricsRegistries.registry().find("numLateRecordsDropped")
                        .tags("operator", first.getLateDropMetricOperatorTag(),
                                "subtask", first.getLateDropMetricSubtaskTag())
                        .counter();
        io.micrometer.core.instrument.Counter counterSecond =
                StreamMetricsRegistries.registry().find("numLateRecordsDropped")
                        .tags("operator", second.getLateDropMetricOperatorTag(),
                                "subtask", second.getLateDropMetricSubtaskTag())
                        .counter();
        assertNotNull(counterFirst, "precondition: first instance registered its counter");
        assertNotNull(counterSecond, "the rebuild must re-register/share its counter");
        assertSame(counterFirst, counterSecond,
                "same-scope rebuild must share the idempotent Micrometer counter");
        assertTrue(first.getLateDropMetricOperatorTag().equals(second.getLateDropMetricOperatorTag()),
                "same location must yield the same stable key");

        second.close();
        assertNull(findMeter("numLateRecordsDropped", second.getLateDropMetricOperatorTag(),
                        second.getLateDropMetricSubtaskTag()),
                "the last close on the shared scope removes the counter (converged release)");
        first.close();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void equiJoinAndOverWindowCountersUseStableKeysAndCloseRemoves() throws Exception {
        // ---- EquiJoinOperator ----
        // the registry is JVM-global and other tests (e.g. TestE2EOverWindowWithCheckpoint)
        // leave DEFAULT-scope ("0"/"0") meters behind unclosed: scope this test to
        // UNIQUE stable tags so the assertions never couple to foreign registry state.
        EquiJoinOperator join = new EquiJoinOperator<>(io.nop.stream.core.model.JoinType.INNER, 1000L, 0L)
                .copyForSubtask(new TaskLocation("job-r15ej", "p1", "vertex-ej", 5));
        join.setOutput((Output) new TestOutput<String>());
        join.open();
        io.micrometer.core.instrument.Meter joinMeter = StreamMetricsRegistries.registry()
                .find("numJoinOutputsEmitted")
                .tags("operator", "job-r15ej|vertex-ej|5", "subtask", "5").meter();
        assertNotNull(joinMeter, "precondition: EquiJoin registers its counter under the stable key");
        assertFalse(joinMeter.getId().getTag("operator").contains("@"),
                "EquiJoin counter tag must not embed identityHashCode: "
                        + joinMeter.getId().getTag("operator"));
        join.close();
        assertNull(StreamMetricsRegistries.registry().find("numJoinOutputsEmitted")
                        .tags("operator", "job-r15ej|vertex-ej|5", "subtask", "5").meter(),
                "EquiJoin close must remove its own counter (W-15)");

        // ---- OverWindowOperator ----
        OverWindowOperator over = new OverWindowOperator(OverWindowOperator.FrameKind.ROW_NUMBER, 0, null)
                .copyForSubtask(new TaskLocation("job-r15ow", "p1", "vertex-ow", 7));
        over.setOutput((Output) new TestOutput<String>());
        over.open();
        io.micrometer.core.instrument.Meter overMeter = StreamMetricsRegistries.registry()
                .find("numOverRowsEmitted")
                .tags("operator", "job-r15ow|vertex-ow|7", "subtask", "7").meter();
        assertNotNull(overMeter, "precondition: OverWindow registers its counter under the stable key");
        assertFalse(overMeter.getId().getTag("operator").contains("@"),
                "OverWindow counter tag must not embed identityHashCode: "
                        + overMeter.getId().getTag("operator"));
        over.close();
        assertNull(StreamMetricsRegistries.registry().find("numOverRowsEmitted")
                        .tags("operator", "job-r15ow|vertex-ow|7", "subtask", "7").meter(),
                "OverWindow close must remove its own counter (W-15)");
    }
}
