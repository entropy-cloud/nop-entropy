package io.nop.stream.core.environment;

import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.datastream.DataStreamImpl;
import io.nop.stream.core.datastream.DataStreamSource;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.graph.StreamGraph;
import io.nop.stream.core.graph.StreamGraphGenerator;
import io.nop.stream.core.graph.StreamNode;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.transformation.SinkTransformation;
import io.nop.stream.core.transformation.Transformation;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 29 (Phase 2) core unit tests for the per-operator parallelism API entry:
 * {@code DataStream.setParallelism(int)} (covariant on
 * {@code SingleOutputStreamOperator} / {@code KeyedStream}), the
 * {@code sink(fn, parallelism)} registration overload, and the interactions with
 * the {@code forceNonParallel()} lock, StreamNode resolution and chain breaking.
 *
 * <p>Lives in the environment package to read the registered transformations
 * (package-private accessor) for graph generation.
 */
class TestDataStreamParallelism {

    private static SourceFunction<String> boundedSource() {
        return new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("done");
            }

            @Override
            public void cancel() {
            }
        };
    }

    private static SinkFunction<String> collectingSink(List<String> into) {
        return new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                into.add(value);
            }
        };
    }

    private static List<Transformation<?>> sinksOf(StreamExecutionEnvironment env) {
        List<Transformation<?>> sinks = new ArrayList<>();
        for (Transformation<?> t : env.getTransformations()) {
            if (t instanceof SinkTransformation) {
                sinks.add(t);
            }
        }
        return sinks;
    }

    @Test
    void mapSetParallelismStampsTransformation() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        SingleOutputStreamOperator<String> mapped = env.addSource(boundedSource(), "src")
                .map((MapFunction<String, String>) v -> v);

        assertEquals(2, ((DataStreamImpl<String>) mapped).getTransformation().getParallelism(),
                "undeclared inherits the environment value");
        assertSame(mapped, mapped.setParallelism(5));
        assertEquals(5, ((DataStreamImpl<String>) mapped).getTransformation().getParallelism(),
                "declared value overrides the environment stamp");
    }

    @Test
    void keyBySetParallelismStampsPartitionTransformation() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);
        KeyedStream<String, String> keyed = env.addSource(boundedSource(), "src")
                .keyBy(v -> v);

        assertEquals(1, ((DataStreamImpl<String>) keyed).getTransformation().getParallelism());
        assertSame(keyed, keyed.setParallelism(4));
        assertEquals(4, ((DataStreamImpl<String>) keyed).getTransformation().getParallelism(),
                "keyBy parallelism targets the PartitionTransformation vertex");
    }

    @Test
    void sourceSetParallelismStampsSourceTransformation() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);
        DataStreamSource<String> source = env.addSource(boundedSource(), "src");

        assertSame(source, source.setParallelism(3));
        assertEquals(3, ((DataStreamImpl<String>) source).getTransformation().getParallelism());
    }

    @Test
    void sinkOverloadStampsSinkTransformation() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);
        List<String> collected = new ArrayList<>();
        env.addSource(boundedSource(), "src").sink(collectingSink(collected), 4);

        JobGraph jobGraph = env.buildJobGraph("sink-parallelism");
        int sinkParallelism = -1;
        for (JobVertex vertex : jobGraph.getVertices().values()) {
            if (vertex.getName().contains("Sink")) {
                sinkParallelism = vertex.getParallelism();
            }
        }
        assertEquals(4, sinkParallelism, "sink(fn, 4) must stamp the sink JobVertex parallelism");
    }

    @Test
    void setParallelismBelowOneIsRejected() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        SingleOutputStreamOperator<String> mapped = env.addSource(boundedSource(), "src")
                .map((MapFunction<String, String>) v -> v);

        StreamException ex = assertThrows(StreamException.class, () -> mapped.setParallelism(0));
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode());

        assertThrows(StreamException.class, () -> mapped.setParallelism(-1));
        StreamException sinkEx = assertThrows(StreamException.class,
                () -> env.addSource(boundedSource(), "src").sink(collectingSink(new ArrayList<>()), 0));
        assertEquals("nop.err.stream.invalid-arg", sinkEx.getErrorCode());
    }

    @Test
    void forceNonParallelLockInteractions() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(4);
        SingleOutputStreamOperator<String> locked = env.addSource(boundedSource(), "src")
                .map((MapFunction<String, String>) v -> v)
                .forceNonParallel();
        List<String> collected = new ArrayList<>();
        ((io.nop.stream.core.datastream.DataStream<String>) locked).sink(collectingSink(collected));

        // The lock wins over both the environment stamp and any prior value at
        // graph resolution: the locked operator's StreamNode is forced to 1.
        StreamGraph graph = new StreamGraphGenerator().generate(sinksOf(env));
        int lockedNodeP = -1;
        for (StreamNode node : graph.getStreamNodes().values()) {
            if (node.getName().equals("Map")) {
                lockedNodeP = node.getParallelism();
            }
        }
        assertEquals(1, lockedNodeP, "locked operator must resolve to parallelism 1 (env is 4)");

        // Setting 1 on a locked transformation stays allowed (value-wise no-op)...
        locked.setParallelism(1);
        // ...while a non-1 value is rejected with the typed lock error.
        StreamException ex = assertThrows(StreamException.class, () -> locked.setParallelism(2));
        assertEquals("nop.err.stream.invalid-state", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("forceNonParallel"),
                "lock error must name the lock source: " + ex.getMessage());
    }

    @Test
    void mixedParallelismReachesStreamNodesAndBreaksChaining() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);
        SingleOutputStreamOperator<String> first = env.addSource(boundedSource(), "src")
                .map((MapFunction<String, String>) v -> v);
        first.setParallelism(2);
        // Same parallelism on the second map: chainable with the first.
        SingleOutputStreamOperator<String> second = first
                .map((MapFunction<String, String>) v -> v);
        second.setParallelism(2);
        List<String> collected = new ArrayList<>();
        ((io.nop.stream.core.datastream.DataStream<String>) second).sink(collectingSink(collected), 2);

        StreamGraph graph = new StreamGraphGenerator().generate(sinksOf(env));
        // Per-vertex wiring verification: declared values reach the StreamNode level.
        int sourceP = -1;
        int mapCount = 0;
        int mapP = -1;
        int sinkP = -1;
        for (StreamNode node : graph.getStreamNodes().values()) {
            if (node.getName().equals("src")) {
                sourceP = node.getParallelism();
            } else if (node.getName().equals("Map")) {
                mapCount++;
                mapP = node.getParallelism();
            } else if (node.getName().equals("Sink")) {
                sinkP = node.getParallelism();
            }
        }
        assertEquals(1, sourceP, "undeclared source inherits the stream-level value");
        assertEquals(2, mapCount, "both map transformations must be present as nodes");
        assertEquals(2, mapP, "declared map parallelism reaches the StreamNode");
        assertEquals(2, sinkP, "declared sink parallelism reaches the StreamNode");

        // Chaining interaction: source(P=1) -> map(P=2) must NOT chain (parallelism
        // mismatch breaks the chain); map(P=2) -> map(P=2) -> sink(P=2) may chain.
        JobGraph jobGraph = env.buildJobGraph("mixed-parallelism");
        assertEquals(2, jobGraph.getNumberOfVertices(),
                "source(1) vs map(2) must occupy separate vertices; the equal-parallelism "
                        + "map->map->sink tail chains into one vertex");
        boolean sawParallelOneVertex = false;
        boolean sawParallelTwoVertex = false;
        for (JobVertex vertex : jobGraph.getVertices().values()) {
            if (vertex.getParallelism() == 1) {
                sawParallelOneVertex = true;
            }
            if (vertex.getParallelism() == 2) {
                sawParallelTwoVertex = true;
            }
        }
        assertTrue(sawParallelOneVertex && sawParallelTwoVertex,
                "the mixed-parallelism graph must contain both a parallel-1 and a parallel-2 vertex");
    }
}
