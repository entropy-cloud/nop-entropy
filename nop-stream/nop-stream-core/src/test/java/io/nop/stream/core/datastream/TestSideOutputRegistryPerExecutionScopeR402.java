/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.ChainingOutput;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.core.util.OutputTag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.42 R4-02 W-07 (1.1-FAM-2 = C1-002 + C5-007 + C7-006, C10-022, 红⑤):
 * per-execution SideOutputRegistry scoping. The pre-fix registry was a
 * process-static map keyed by bare tagId — two jobs on the same host registering
 * the same tag silently REPLACED each other (the second registration won and the
 * first job's tagged records were routed into the second job's sink: a silent
 * cross-job mis-delivery), and terminal jobs left their registrations (and the
 * whole operator chain they close over) pinned in the static map forever.
 *
 * <p>Post-fix (adjudication C.2): the registry is an instance hung on the
 * {@code StreamExecutionEnvironment} — one execution context = one scope.
 * Registration ({@code getSideOutput(tag).sink(...)}) targets the owning env's
 * registry; the runtime read points (ChainingOutput fallback, cross-task
 * StreamTaskInvokable routing) consult the invokable's own env registry. Two
 * envs with the same tag are physically separate key spaces (toppling is
 * structurally impossible), and a new env on the same host starts with an empty
 * registry (no resurrection of a previous execution's consumer).
 *
 * <p>Driver shape (design ruling D / F-4): the cross-delivery legs are
 * behavioral (envA's emit reaches sinkA only, and symmetrically); the lifecycle
 * legs assert the behavioral face — a fresh same-host env with the same tag does
 * not resurrect any prior execution's consumer, and registries are env-identity
 * distinct (GC-reachability is deliberately NOT asserted — unreliable).
 */
class TestSideOutputRegistryPerExecutionScopeR402 {

    private static final String SHARED_TAG = "r402-shared-tag";

    private static Transformation<String> anyTransformation(StreamExecutionEnvironment env) {
        return ((DataStreamImpl<String>) env.fromCollection(Collections.singletonList("tx")))
                .getTransformation();
    }

    /** Recording sink for the SideOutputDataStream registration path. */
    private static final class RecordingSink
            implements io.nop.stream.core.common.functions.SinkFunction<String> {
        final List<String> received = new CopyOnWriteArrayList<>();

        @Override
        public void consume(String value) {
            received.add(value);
        }
    }

    @Test
    void sameTagOnTwoEnvsNeverCrossDelivers() {
        StreamExecutionEnvironment envA = StreamExecutionEnvironment.createTestEnvironment();
        StreamExecutionEnvironment envB = StreamExecutionEnvironment.createTestEnvironment();

        OutputTag<String> tag = new OutputTag<>(SHARED_TAG, BasicTypeInfo.STRING);
        RecordingSink sinkA = new RecordingSink();
        RecordingSink sinkB = new RecordingSink();
        // The real assembly-time registration path: getSideOutput(tag).sink(...)
        new SideOutputDataStream<>(envA, anyTransformation(envA), tag).sink(sinkA);
        new SideOutputDataStream<>(envB, anyTransformation(envB), tag).sink(sinkB);

        // envA's runtime read point: the LOCAL fallback lookup consults the
        // invokable's env registry (task-local map empty).
        ChainingOutput<Object> readPointA = new ChainingOutput<>(
                new NoopDownstream(), "envA-task", new java.util.HashMap<>(),
                envA.getSideOutputRegistry());
        readPointA.collect(tag, new StreamRecord<>("a1"));

        assertEquals(List.of("a1"), sinkA.received,
                "envA's tagged record must reach envA's consumer");
        assertTrue(sinkB.received.isEmpty(),
                "pre-fix defect: envB's same-tag registration toppled envA's consumer "
                        + "and received envA's records (silent cross-job mis-delivery)");

        // Symmetric leg: envB's emit reaches sinkB only (双向断言防单向假绿).
        ChainingOutput<Object> readPointB = new ChainingOutput<>(
                new NoopDownstream(), "envB-task", new java.util.HashMap<>(),
                envB.getSideOutputRegistry());
        readPointB.collect(tag, new StreamRecord<>("b1"));

        assertEquals(List.of("b1"), sinkB.received, "envB's tagged record reaches envB's consumer");
        assertEquals(List.of("a1"), sinkA.received, "envA's sink is untouched by envB's emission");
    }

    @Test
    void newEnvOnSameHostDoesNotResurrectOldConsumer() {
        StreamExecutionEnvironment envA = StreamExecutionEnvironment.createTestEnvironment();
        OutputTag<String> tag = new OutputTag<>(SHARED_TAG, BasicTypeInfo.STRING);
        RecordingSink sinkA = new RecordingSink();
        new SideOutputDataStream<>(envA, anyTransformation(envA), tag).sink(sinkA);

        // envA's execution completes (the env object graph is done); a NEW env on
        // the same host builds a pipeline reusing the same tag id.
        StreamExecutionEnvironment envC = StreamExecutionEnvironment.createTestEnvironment();

        // The fresh env's registry starts empty — no prior execution's consumer.
        assertNull(envC.getSideOutputRegistry().get(SHARED_TAG),
                "a new env's registry must not contain any prior execution's consumer");
        assertNotSame(envA.getSideOutputRegistry(), envC.getSideOutputRegistry(),
                "registries are env-identity distinct (one execution context = one scope)");

        RecordingSink sinkC = new RecordingSink();
        new SideOutputDataStream<>(envC, anyTransformation(envC), tag).sink(sinkC);

        ChainingOutput<Object> readPointC = new ChainingOutput<>(
                new NoopDownstream(), "envC-task", new java.util.HashMap<>(),
                envC.getSideOutputRegistry());
        readPointC.collect(tag, new StreamRecord<>("c1"));

        assertEquals(List.of("c1"), sinkC.received, "envC's consumer receives envC's records");
        assertEquals(List.of(), sinkA.received,
                "the previous execution's consumer is NOT resurrected by the new env's traffic");
    }

    /** Minimal downstream input (the side-output routing never reaches headInput). */
    private static final class NoopDownstream implements io.nop.stream.core.operators.Input<Object> {
        @Override
        public void processElement(StreamRecord<Object> record) {
        }

        @Override
        public void processWatermark(io.nop.stream.core.streamrecord.watermark.Watermark mark) {
        }

        @Override
        public void processWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
        }

        @Override
        public void processLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker marker) {
        }

        @Override
        public void setKeyContextElement(StreamRecord<Object> record) {
        }
    }
}
