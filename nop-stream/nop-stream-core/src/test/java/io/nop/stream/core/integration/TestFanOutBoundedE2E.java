/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamSourceOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-03 (open-audit) end-to-end verification of the fan-out close fix: a producer task with
 * 2+ outgoing edges must close ALL its {@link RecordWriter}s on completion so every downstream
 * sink receives end-of-stream. The historical bug kept only {@code fanOutWriters.get(0)} (edge 0),
 * so edges 2..N never saw EOS and bounded fan-out jobs hung their downstream sinks forever.
 *
 * <ul>
 *   <li>Rule #22 (end-to-end): a bounded fan-out job (one source, two sinks) must terminate
 *       through the FULL production path ({@code env.execute()} → {@code JobGraphGenerator} →
 *       {@code GraphExecutionPlan} → {@code TaskExecutor} → {@code StreamTaskInvokable}) with
 *       both sinks receiving every record — red before the fix (job hangs on sink 2's gate),
 *       green after.</li>
 *   <li>Rule #23/#24 (wiring + no-silent-skip): at the invokable level, the close path must
 *       explicitly traverse the FULL writer list. The tail operator's
 *       {@code BroadcastingRecordWriterOutput.close()} cannot do this — it delegates to
 *       {@code RecordWriterOutput.close()}, a documented no-op — so closing via the operator
 *       output would silently skip every writer.</li>
 * </ul>
 */
public class TestFanOutBoundedE2E {

    /**
     * Rule #22 end-to-end: bounded source → two sinks. Before the P1-03 fix, edge 2's writer was
     * never closed, sink 2's InputGate polled forever, and {@code env.execute()} never returned
     * (the test fails on the {@code @Timeout}). After the fix both sinks drain fully and the job
     * terminates.
     */
    @Test
    @Timeout(30)
    void testBoundedFanOutJobTerminatesWithAllSinksEos() throws Exception {
        List<Integer> sinkA = new ArrayList<>();
        List<Integer> sinkB = new ArrayList<>();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        io.nop.stream.core.datastream.DataStream<Integer> stream = env.fromElements(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        stream.sink(sinkA::add);
        stream.sink(sinkB::add);

        env.execute("fan-out-bounded");

        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), sinkA,
                "sink A (edge 1) must receive every element of the bounded source");
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), sinkB,
                "sink B (edge 2) must receive every element of the bounded source");
    }

    /**
     * Rule #23/#24 wiring verification at the invokable level: the fan-out constructor must
     * RETAIN the full writer list ({@code getFanOutWriters()}), and {@code invoke()} must close
     * every writer so every edge's {@link ResultPartition} finishes (EOS). Both channels are
     * drained to empty to prove the consumer-visible EOS, not just an internal flag.
     */
    @Test
    @Timeout(30)
    void testFanOutCloseTraversesAllWriters() throws Exception {
        ResultPartition partition1 = new ResultPartition();
        ResultPartition partition2 = new ResultPartition();
        RecordWriter<Object> writer1 = new RecordWriter<>(partition1);
        RecordWriter<Object> writer2 = new RecordWriter<>(partition2);

        OperatorChain chain = new OperatorChain(List.of(
                (io.nop.stream.core.operators.StreamOperator<?>) new StreamSourceOperator<>(boundedSource())));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain, List.of(writer1, writer2));

        assertEquals(2, invokable.getFanOutWriters().size(),
                "the full fan-out writer list must be retained (one per outgoing edge)");
        assertEquals(writer1, invokable.getOutputWriter(),
                "outputWriter remains edge 0 for role/API compatibility");

        invokable.invoke();

        InputChannel channel1 = new InputChannel(partition1);
        InputChannel channel2 = new InputChannel(partition2);
        assertEquals(5, drain(channel1),
                "edge 1 must deliver all 5 records then EOS");
        assertEquals(5, drain(channel2),
                "edge 2 must deliver all 5 records then EOS");
        assertTrue(channel1.isFinished(),
                "edge 1 must be finished (EOS) after the fan-out producer closes");
        assertTrue(channel2.isFinished(),
                "edge 2 must be finished (EOS) after the fan-out producer closes — the historical "
                        + "bug left edge 2 open and its sink hung forever");
    }

    private static SourceFunction<Integer> boundedSource() {
        return new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) {
                for (int i = 1; i <= 5; i++) {
                    ctx.collect(i);
                }
            }

            @Override
            public void cancel() {
            }
        };
    }

    /**
     * Drains a channel until EOS (bounded read returns null for poll-timeout AND EOS, so
     * {@code isFinished()} disambiguates). Returns the number of records drained (watermarks
     * and other element types are consumed but not counted).
     */
    private static int drain(InputChannel channel) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!channel.isFinished() && System.currentTimeMillis() < deadline) {
            channel.read(10, TimeUnit.MILLISECONDS);
        }
        assertTrue(channel.isFinished(), "channel must reach EOS (isFinished) within the drain deadline");
        int count = 0;
        while (true) {
            io.nop.stream.core.streamrecord.StreamElement element = channel.read(10, TimeUnit.MILLISECONDS);
            if (element == null) {
                break;
            }
            if (element.isRecord()) {
                count++;
            }
        }
        return count;
    }
}
