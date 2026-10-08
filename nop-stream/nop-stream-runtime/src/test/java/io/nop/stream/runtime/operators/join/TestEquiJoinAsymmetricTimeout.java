/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.join;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.model.JoinMatch;
import io.nop.stream.core.model.JoinSideRecord;
import io.nop.stream.core.model.JoinType;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.join.EquiJoinOperator;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI27 三轴判别（R1-B1 语义边界内的可达判别）：窗口 equi-join 的 per-side 非对称宽限。
 * 配对语义为窗口内叉积（每左配所有已到右）；配对不变式：记录被接受 ⇔ 到达时水位居于
 * 其自身侧 fire 点（ws+wd+T_side）之前——被接受记录集合的叉积即配对面。非对称的交付
 * 自由度：① LEFT 未匹配左的 (L,null) 完成时点=leftTimeout；② 长侧缓冲保留至自身
 * fire 点（状态量）；③ 短侧 fire 点后到达的对侧记录 sight-drop（对称长 timeout 档会
 * 接受——完成/接受行为判别于对称档）。执行发现：首版测试按一对一配对误设期望，实跑
 * 揭示叉积语义（J|c|b），按真实语义重设判别。
 */
public class TestEquiJoinAsymmetricTimeout {
    /**
     * dur=10, leftTimeout=20, rightTimeout=5（LEFT join；窗口内配对为叉积语义）：
     * ① 配对不变式——左 a@1、c@2 到达时各与已缓冲右 b@1 叉积配对（J|a|b、J|c|b）；
     * ② wm=15（右 fire=0+10+5）：右侧丢弃，左侧保留（bufferedCount=2）；
     * ③ 左 d@5 于 wm=15 到达（未过左 fire=30）被接受——右缓冲已空故无配对，缓冲为
     *    未匹配左；对称 rightTimeout=20 档下 d 亦被接受且与 b 无异（b 已配）；真判别
     *    在 wm=30：d 以 (L,null) 完成——对称 5 档下 d 在 wm=15 即被 sight-drop、无完成。
     * 最终发射 [J|a|b, J|c|b, J|d|null]。
     */
    @Test
    void asymmetricTimeoutDecouplesCompletionAndRetention() throws Exception {
        EquiJoinOperator<String, String> op = join(JoinType.LEFT, 10L, 20L, 5L);

        feed(op, true, "k", "a", 1L);
        feed(op, false, "k", "b", 1L);
        feed(op, true, "k", "c", 2L); // cross-product: pairs with buffered right b
        assertEquals(Arrays.asList("J|a|b", "J|c|b"), outs(op),
                "eager cross-product pairing (accepted within own side fire point)");

        wm(op, 15L); // right fire = 0 + 10 + 5
        assertEquals(2, op.bufferedCount(),
                "left side retained past the short side's fire point (axis ②)");
        assertEquals(Arrays.asList("J|a|b", "J|c|b"), outs(op),
                "no LEFT completion at the right side's fire point (axis ①)");

        feed(op, true, "k", "d", 5L); // wm=15 < left fire 30 → accepted, unmatched
        assertEquals(3, op.bufferedCount());

        wm(op, 30L); // left fire = 0 + 10 + 20
        assertEquals(Arrays.asList("J|a|b", "J|c|b", "J|d|null"), outs(op),
                "left d (arrived in (15,30]) completes at leftTimeout=20 — "
                        + "the symmetric rightTimeout=5 form would have dropped it on sight (axis ③)");
        assertEquals(0, op.bufferedCount(), "left side dropped after its fire");
    }

    /**
     * 配对面不变式回归：swap（5/20）下配对集合与 min 档一致，完成时点解耦到左 fire=15
     * （未匹配左在 15 完成，wm=30 前已发射）；wm=15 后到达的左被 sight-drop。
     */
    @Test
    void swappedTimeoutsKeepPairingSetAtSymmetricMin() throws Exception {
        EquiJoinOperator<String, String> swapped = join(JoinType.LEFT, 10L, 5L, 20L);
        feed(swapped, true, "k", "a", 1L);
        feed(swapped, false, "k", "b", 1L);
        feed(swapped, true, "k", "c", 2L);
        assertEquals(Arrays.asList("J|a|b", "J|c|b"), outs(swapped));

        wm(swapped, 15L); // left fire = 0 + 10 + 5
        assertEquals(Arrays.asList("J|a|b", "J|c|b"), outs(swapped),
                "a/c both matched — no (L,null) completions at the left fire point");
        // bufferedCount sums both sides: matched-but-unfired right b@1 is retained
        // until ITS fire point (0+10+20=30) — the retention asymmetry in reverse
        assertEquals(1, swapped.bufferedCount(),
                "left dropped at its fire; right retained until its own fire point");

        feed(swapped, true, "k", "d", 5L); // past left fire → sight-drop
        assertEquals(Arrays.asList("J|a|b", "J|c|b"), outs(swapped),
                "late left dropped on sight (short side governs arrival window)");
        assertEquals(1, swapped.bufferedCount(),
                "d dropped leaves no state; only the retained right remains");
    }

    private static EquiJoinOperator<String, String> join(JoinType type, long duration,
                                                         long leftTimeout, long rightTimeout)
            throws Exception {
        EquiJoinOperator<String, String> op = new EquiJoinOperator<>(type, duration,
                leftTimeout, rightTimeout);
        op.setStateBackend(new MemoryStateBackend());
        op.setOutput(new WindowListOutput());
        op.open();
        return op;
    }

    private static void feed(EquiJoinOperator<String, String> op, boolean left, String key,
                             String payload, long ts) throws Exception {
        op.processElement(new StreamRecord<>(new JoinSideRecord<Object>(left, key, payload), ts));
    }

    private static void wm(EquiJoinOperator<String, String> op, long ts) throws Exception {
        op.processWatermark(new Watermark(ts));
    }

    private static List<String> outs(EquiJoinOperator<String, String> op) {
        return ((WindowListOutput) op.getOutput()).records;
    }

    static final class WindowListOutput implements Output<StreamRecord<JoinMatch<String, String>>> {
        final List<String> records = new java.util.ArrayList<>();

        @Override
        public void collect(StreamRecord<JoinMatch<String, String>> record) {
            records.add(record.getValue().toString());
        }

        @Override
        public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
        }

        @Override
        public void emitWatermark(Watermark mark) {
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

        @Override
        public void close() {
        }
    }
}
