/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.flow.builder.functions.XplFunctionSupport;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * WI8 Phase 2 (plan 2332): unit contract of the {@code <sideOutput>} tap operator —
 * copy semantics. The MAIN output must carry EVERY record unchanged (the tap never
 * filters), and the tag channel must carry exactly the condition-matching records.
 * This pins that the condition is genuinely CONSUMED per record — an implementation
 * that silently copied everything (or filtered the main path) would fail here.
 */
public class TestSideOutputTapOperator {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /** Records main-path and tagged emissions separately. */
    static class RecordingOutput implements io.nop.stream.core.operators.Output<StreamRecord<Integer>> {
        final List<StreamRecord<Integer>> main = new ArrayList<>();
        final List<StreamRecord<Integer>> tagged = new ArrayList<>();

        @Override
        public void collect(StreamRecord<Integer> record) {
            main.add(record);
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
            tagged.add((StreamRecord<Integer>) (StreamRecord<?>) record);
        }

        @Override
        public void close() {
        }

        @Override
        public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark mark) {
        }

        @Override
        public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
        }

        @Override
        public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
        }

        @Override
        public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier barrier) {
        }
    }

    private static IEvalAction compileCondition(String expr) {
        // Same binding contract as <keyBy keyExpr>: `event` is bound at invocation
        // time, so the compile tool must not reject it as an unresolved scope var.
        return XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .compileSimpleExpr(null, expr);
    }

    @Test
    public void conditionMatchingRecordsCopiedMainPathUntouched() throws Exception {
        OutputTag<Integer> tag = new OutputTag<>("big", BasicTypeInfo.INT);
        SideOutputTapOperator<Integer> tap =
                new SideOutputTapOperator<>(tag, compileCondition("event > 1"));
        RecordingOutput out = new RecordingOutput();
        tap.setOutput(out);

        tap.processElement(new StreamRecord<>(1, 10L));
        tap.processElement(new StreamRecord<>(2, 20L));
        tap.processElement(new StreamRecord<>(3, 30L));

        assertEquals(List.of(1, 2, 3), out.main.stream().map(StreamRecord::getValue).toList(),
                "main output must carry EVERY record unchanged (copy, not filter)");
        assertEquals(List.of(2, 3), out.tagged.stream().map(StreamRecord::getValue).toList(),
                "tag channel must carry exactly the condition-matching records");
        assertEquals(20L, out.tagged.get(0).getTimestamp(),
                "the tagged copy must preserve the record timestamp");
    }

    @Test
    public void noConditionCopiesEveryRecord() throws Exception {
        OutputTag<Integer> tag = new OutputTag<>("all", BasicTypeInfo.INT);
        SideOutputTapOperator<Integer> tap = new SideOutputTapOperator<>(tag, null);
        RecordingOutput out = new RecordingOutput();
        tap.setOutput(out);

        tap.processElement(new StreamRecord<>(7, 1L));
        tap.processElement(new StreamRecord<>(8, 2L));

        assertEquals(List.of(7, 8), out.main.stream().map(StreamRecord::getValue).toList());
        assertEquals(List.of(7, 8), out.tagged.stream().map(StreamRecord::getValue).toList(),
                "a condition-less <sideOutput> is a declared copy-all tap");
    }

    @Test
    public void copyForSubtaskReturnsIndependentInstance() {
        OutputTag<Integer> tag = new OutputTag<>("t", BasicTypeInfo.INT);
        SideOutputTapOperator<Integer> tap = new SideOutputTapOperator<>(tag, null);
        SideOutputTapOperator<Integer> copy = tap.copyForSubtask();
        assertNotSame(tap, copy, "per-subtask copy must not alias the original");
    }

    /**
     * The condition evaluation helper must treat null/zero as false (truthy contract,
     * same as {@code XplFilterFunction}) — a condition returning a non-boolean value
     * is interpreted, never silently treated as match-all.
     */
    @Test
    public void truthyContractOnConditionResult() {
        assertEquals(false, XplFunctionSupport.toBoolean(null));
        assertEquals(false, XplFunctionSupport.toBoolean(0));
        assertEquals(true, XplFunctionSupport.toBoolean(1));
        assertEquals(true, XplFunctionSupport.toBoolean("x"));
    }
}
