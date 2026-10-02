/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7 regression 2/4 — multi-input watermark min-merge, end to end. The union
 * vertex's input gate min-merges per-channel watermarks and forwards only the
 * combined (advanced) value to the observing operator: with the fast source far
 * ahead, the merged watermark must be held at the slow side's level and then follow
 * as the slow side advances.
 *
 * <p>EOS guard: any finishing source vertex pushes MAX_WATERMARK downstream, which
 * would instantly saturate the merged value — both sources latch until the observer
 * has collected the expected sequence, then finish.
 */
public class TestMultiInputWatermarkMinMerge {

    static class LatchedWatermarkSource implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;
        private final String tag;
        private final long[] marks;
        private final CountDownLatch release;
        private volatile boolean running = true;

        LatchedWatermarkSource(String tag, long[] marks, CountDownLatch release) {
            this.tag = tag;
            this.marks = marks;
            this.release = release;
        }

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            for (long mark : marks) {
                if (!running) return;
                ctx.collect(tag + "-e" + mark);
                ctx.emitWatermark(mark);
                Thread.sleep(40);
            }
            // hold the vertex open so EOS does not push MAX_WATERMARK into the merge
            release.await(8, TimeUnit.SECONDS);
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /** Observes the forwarded (min-merged) watermark sequence, then forwards. */
    static class WatermarkObserver extends AbstractStreamOperator<String>
            implements OneInputStreamOperator<String, String> {
        private static final long serialVersionUID = 1L;
        final List<Long> merged;
        final CountDownLatch expectedSeen;
        final int expectedCount;

        WatermarkObserver(List<Long> merged, CountDownLatch expectedSeen, int expectedCount) {
            this.merged = merged;
            this.expectedSeen = expectedSeen;
            this.expectedCount = expectedCount;
        }

        @Override
        public WatermarkObserver copyForSubtask() {
            return new WatermarkObserver(merged, expectedSeen, expectedCount);
        }

        @Override
        public void processElement(StreamRecord<String> element) {
            output.collect(element);
        }

        @Override
        public void processWatermark(Watermark mark) {
            merged.add(mark.getTimestamp());
            if (merged.size() >= expectedCount) {
                expectedSeen.countDown();
            }
            output.emitWatermark(mark);
        }

        @Override
        public void processWatermarkStatus(WatermarkStatus status) {
            output.emitWatermarkStatus(status);
        }
    }

    @Test
    public void unionWatermarkIsMinOfChannelsAndFollowsSlowSide() throws Exception {
        List<Long> merged = new CopyOnWriteArrayList<>();
        // slow side advances 100→200→300; fast side jumps far ahead (900, 950)
        // expected merged sequence: 100, 200, 300 (fast never lifts the merge)
        CountDownLatch seen = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(2);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.addSource(new LatchedWatermarkSource("slow", new long[]{100, 200, 300}, release), "slow")
                .union(env.addSource(new LatchedWatermarkSource("fast", new long[]{900, 950}, new CountDownLatch(0)), "fast"))
                .transform("wm-observe", null, new WatermarkObserver(merged, seen, 3))
                .sink(v -> {
                });

        // release the latches once the expected merged sequence has been observed
        Thread driver = new Thread(() -> {
            try {
                seen.await(6, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            release.countDown();
            release.countDown();
        });
        driver.start();

        env.execute("wi7-watermark-min-merge");
        driver.join(1000);

        // the EOS MAX watermark after both sources finish is expected — exclude it
        List<Long> mergedData = merged.stream().filter(m -> m < Long.MAX_VALUE).collect(java.util.stream.Collectors.toList());
        assertTrue(mergedData.size() >= 3, "at least the three slow-side marks must be forwarded, got " + merged);
        assertEquals(100L, mergedData.get(0), "first merge = min(100, 900) = slow's 100");
        // fast's 900 must not lift the merge above slow's level before slow advances
        assertTrue(mergedData.get(1) == 200L || mergedData.get(1) == 300L,
                "second merge must still be slow-driven, got " + mergedData.get(1));
        for (int i = 1; i < mergedData.size(); i++) {
            assertTrue(mergedData.get(i) >= mergedData.get(i - 1), "merged watermarks are monotonic");
        }
        assertTrue(mergedData.stream().noneMatch(m -> m >= 900),
                "fast-side far-ahead watermark must not lift the min merge: " + mergedData);
    }
}
