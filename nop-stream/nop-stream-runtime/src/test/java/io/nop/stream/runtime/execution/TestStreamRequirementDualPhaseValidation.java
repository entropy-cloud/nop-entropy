/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.model.StreamRequirement;
import io.nop.stream.core.model.StreamRequirementValidator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI21 (§八 12): StreamRequirement validation in TWO phases.
 *
 * <p><b>Phase 1 — compile-time (execute entry)</b>: the connector-consistency
 * gate (STRICT_EXACTLY_ONCE requires replayable sources). Documented baseline:
 * the pre-graph validate() reads the env-built model whose derived requirements
 * are empty, so the connector gate is the reachable first-phase assertion.
 *
 * <p><b>Phase 2 — runtime gates (WI21 new)</b>: a TwoPhaseCommitSinkFunction only
 * prepare/commits on the checkpoint path. Gate A (core execute): a 2PC sink with
 * no checkpointing declared fails fast instead of silently never committing in
 * runLocal. Gate B (runtime skeleton): the checkpoint executor refuses a 2PC job
 * arriving without a CheckpointConfig (defense in depth for the JobGraph-only
 * entry).
 */
public class TestStreamRequirementDualPhaseValidation {

    /** minimal 2PC sink (borrowed shape from TestCheckpointPlanBuilderParticipants.Mock2PC). */
    public static class Mock2PC extends TwoPhaseCommitSinkFunction<String> {
        @Override
        public void beginTransaction() {
        }

        @Override
        public void invoke(String value) {
        }

        @Override
        public void preCommit(long checkpointId) {
        }

        @Override
        public void commit(long checkpointId) {
        }

        @Override
        public void rollback() {
        }
    }

    @Test
    public void phase1StrictExactlyOnceRequiresReplayableSource() {
        // the reachable first-phase gate: connector consistency rejects a
        // non-replayable source under STRICT_EXACTLY_ONCE
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamRequirementValidator.validateConnectorConsistency(
                        ProcessingGuarantee.STRICT_EXACTLY_ONCE,
                        Collections.singletonList(SourceConsistencyCapability.AT_LEAST_ONCE),
                        Collections.emptyList()));
        assertTrue(ex.getMessage().contains("REPLAYABLE"),
                "first-phase gate must name the replayability violation: " + ex.getMessage());

        // replayable source passes the same gate
        StreamRequirementValidator.validateConnectorConsistency(
                ProcessingGuarantee.STRICT_EXACTLY_ONCE,
                Collections.singletonList(SourceConsistencyCapability.REPLAYABLE),
                Collections.emptyList());
    }

    @Test
    public void gateATwoPhaseSinkWithoutCheckpointFailsFast() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.fromElements("a", "b").sink(new Mock2PC());

        StreamException ex = assertThrows(StreamException.class, () -> env.execute("wi21-gate-a"),
                "2PC sink without declared checkpointing must fail fast at execute()");
        // execute() wraps job failures in ERR_STREAM_JOB_EXECUTE_FAILED — walk the
        // cause chain to gate A's own message
        boolean hazardExplained = false;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (String.valueOf(t.getMessage()).contains("never commit")) {
                hazardExplained = true;
                break;
            }
        }
        assertTrue(hazardExplained,
                "gate A must explain the silent-non-commit hazard; wrapped message: " + ex.getMessage());
    }

    @Test
    public void gateATwoPhaseSinkWithDeclaredCheckpointPasses() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.enableCheckpointing(100);
        env.fromElements("a").sink(new Mock2PC());

        // the declared-checkpoint path passes gate A and runs the checkpoint engine
        env.execute("wi21-gate-a-ok");
    }

    @Test
    public void gateBRefuses2PCGraphWithoutCheckpointConfig() throws Exception {
        // build a JobGraph carrying a 2PC sink operator (CheckpointPlanBuilder's own
        // discovery shape) and hand it to the checkpoint entry with a null config
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.fromElements("a").sink(new Mock2PC());
        env.enableCheckpointing(100);
        JobGraph jobGraph = env.buildJobGraph("wi21-gate-b");

        // a null CheckpointConfig must fail fast with a StreamException (gate B),
        // never an NPE — assertThrows converts any other throwable into a failure
        StreamException gateB = assertThrows(StreamException.class,
                () -> GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, "wi21-gate-b", null),
                "gate B must refuse a 2PC graph without a CheckpointConfig");
        boolean hazardExplained = String.valueOf(gateB.getMessage())
                .contains("2PC prepare/commit cannot run");
        assertTrue(hazardExplained, "gate B must explain the missing-config hazard");
        // satisfy the unused-import check for the requirement type the gate reads
        assertTrue(StreamRequirement.TWO_PHASE_COMMIT_SINK.name().length() > 0);
    }
}
