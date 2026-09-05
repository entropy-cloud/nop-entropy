package io.nop.stream.flow.builder;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.flow.model.StreamEdgeModel;
import io.nop.stream.flow.model.StreamKeyByModel;
import io.nop.stream.flow.model.StreamMapModel;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.model.StreamSinkModel;
import io.nop.stream.flow.model.StreamSourceModel;
import io.nop.stream.flow.testing.CollectingSinkFunction;
import io.nop.stream.flow.testing.TestSourceFunction;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 29 (Phase 2) flow→core behavior pinning: the {@code transforms/@parallelism}
 * declaration is consumed end-to-end. Asserted at the downstream consumption points
 * (JobVertex per-vertex parallelism), aligned with the programmatic precedent
 * {@code TestStreamGraphGenerator.testDifferentParallelism} /
 * {@code TestJobGraph.testDifferentParallelismVertices}.
 *
 * <p>The HASH-edge form also pins the item-29 partition-vertex alignment: a HASH
 * edge into a wider target must spread across the target's subtasks (the implicit
 * partition vertex takes the target's declared parallelism), not concentrate on
 * target subtask 0 via FORWARD modulo semantics.
 */
public class TestPerTransformParallelismWiring {

    /** Trivial constant key expression: parallelism wiring does not depend on the key value. */
    private static IEvalAction constantKeyExpr() {
        return new IEvalAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public Object invoke(io.nop.core.context.IEvalContext ctx) {
                return "constant-key";
            }
        };
    }

    @Test
    public void hashEdgeIntoWiderTargetStampsPartitionAndTargetVertices() {
        StreamModel model = new StreamModel();
        model.setName("hash-edge-mixed");
        model.setVersion(1L);
        model.setParallelism(1);

        StreamSourceModel src = new StreamSourceModel();
        src.setId("src");
        src.setBean("srcBean");

        StreamMapModel map = new StreamMapModel();
        map.setId("enrich");
        map.setBean("mapBean");
        map.setParallelism(4);

        StreamSinkModel sink = new StreamSinkModel();
        sink.setId("out");
        sink.setBean("sinkBean");
        sink.setParallelism(4);

        model.setTransforms(Arrays.asList(src, map, sink));

        // HASH edge into the wider map target: the implicit partition vertex must
        // take the target's declared parallelism (4), spreading across subtasks.
        StreamEdgeModel e0 = new StreamEdgeModel();
        e0.setId("e0");
        e0.setFrom("src");
        e0.setTo("enrich");
        e0.setPartition(PartitionPolicy.HASH);
        StreamEdgeModel e1 = new StreamEdgeModel();
        e1.setId("e1");
        e1.setFrom("enrich");
        e1.setTo("out");
        model.setEdges(Arrays.asList(e0, e1));

        // The HASH edge needs a keyExpr: reuse the <keyBy> model holder to provide one
        // by declaring the edge's keyExpr through a compiled IEvalAction.
        e0.setKeyExpr(constantKeyExpr());

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model,
                new InMemoryBeanFunctionResolver()
                        .register("srcBean", new TestSourceFunction())
                        .register("mapBean", (io.nop.stream.core.common.functions.MapFunction<Object, Object>) v -> v)
                        .register("sinkBean", new CollectingSinkFunction<>())).build();

        JobGraph jobGraph = env.buildJobGraph("hash-edge-mixed");
        int sourceP = -1;
        int partitionP = -1;
        boolean sawWideVertex = false;
        for (JobVertex vertex : jobGraph.getVertices().values()) {
            String name = vertex.getName();
            if (name.endsWith(":src")) {
                sourceP = vertex.getParallelism();
            } else if (name.startsWith("KeyBy")) {
                // The implicit partition node chains with its equal-parallelism
                // downstream operators; the chain vertex takes the chain head's
                // (partition node's) parallelism.
                partitionP = vertex.getParallelism();
                sawWideVertex = vertex.getParallelism() == 4;
            } else if (vertex.getParallelism() == 4) {
                sawWideVertex = true;
            }
        }
        assertEquals(1, sourceP, "undeclared source inherits the stream-level parallelism");
        assertEquals(4, partitionP,
                "implicit partition vertex on a HASH edge must take the target's declared "
                        + "parallelism (4) — not the stream-level value — so the hash spreads "
                        + "across the target's subtasks");
        assertTrue(sawWideVertex, "map/sink declared parallelism 4 must reach the downstream vertices");
    }

    @Test
    public void keyByTransformParallelismReachesPartitionVertex() {
        StreamModel model = new StreamModel();
        model.setName("keyby-parallel");
        model.setVersion(1L);
        model.setParallelism(1);

        StreamSourceModel src = new StreamSourceModel();
        src.setId("src");
        src.setBean("srcBean");

        StreamKeyByModel keyBy = new StreamKeyByModel();
        keyBy.setId("kb");
        keyBy.setKeyExpr(constantKeyExpr());
        keyBy.setParallelism(4);

        StreamMapModel map = new StreamMapModel();
        map.setId("enrich");
        map.setBean("mapBean");
        map.setParallelism(4);

        StreamSinkModel sink = new StreamSinkModel();
        sink.setId("out");
        sink.setBean("sinkBean");
        sink.setParallelism(4);

        model.setTransforms(Arrays.asList(src, keyBy, map, sink));
        model.setEdges(Arrays.asList(edge("e0", "src", "kb"), edge("e1", "kb", "enrich"),
                edge("e2", "enrich", "out")));

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model,
                new InMemoryBeanFunctionResolver()
                        .register("srcBean", new TestSourceFunction())
                        .register("mapBean", (io.nop.stream.core.common.functions.MapFunction<Object, Object>) v -> v)
                        .register("sinkBean", new CollectingSinkFunction<>())).build();

        JobGraph jobGraph = env.buildJobGraph("keyby-parallel");
        int keyByP = -1;
        for (JobVertex vertex : jobGraph.getVertices().values()) {
            if (vertex.getName().startsWith("KeyBy")) {
                keyByP = vertex.getParallelism();
            }
        }
        assertEquals(4, keyByP, "declared keyBy (partition vertex) parallelism must reach its vertex "
                + "(the KeyBy node chains with its equal-parallelism downstream operators)");
    }

    /** Equal-parallelism adjacent transforms stay chainable (no forced break). */
    @Test
    public void equalParallelismAdjacentTransformsRemainChainable() {
        StreamModel model = new StreamModel();
        model.setName("equal-parallelism");
        model.setVersion(1L);
        model.setParallelism(2);

        StreamSourceModel src = new StreamSourceModel();
        src.setId("src");
        src.setBean("srcBean");
        src.setParallelism(2);

        StreamMapModel map = new StreamMapModel();
        map.setId("double");
        map.setBean("mapBean");
        map.setParallelism(2);

        StreamSinkModel sink = new StreamSinkModel();
        sink.setId("out");
        sink.setBean("sinkBean");
        sink.setParallelism(2);

        model.setTransforms(Arrays.asList(src, map, sink));
        model.setEdges(Arrays.asList(edge("e0", "src", "double"), edge("e1", "double", "out")));

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model,
                new InMemoryBeanFunctionResolver()
                        .register("srcBean", new TestSourceFunction())
                        .register("mapBean", (io.nop.stream.core.common.functions.MapFunction<Object, Object>) v -> v)
                        .register("sinkBean", new CollectingSinkFunction<>())).build();

        JobGraph jobGraph = env.buildJobGraph("equal-parallelism");
        assertEquals(1, jobGraph.getNumberOfVertices(),
                "equal-parallelism source->map->sink must chain into a single vertex");
        for (JobVertex vertex : jobGraph.getVertices().values()) {
            assertEquals(2, vertex.getParallelism());
        }
    }

    private static StreamEdgeModel edge(String id, String from, String to) {
        StreamEdgeModel e = new StreamEdgeModel();
        e.setId(id);
        e.setFrom(from);
        e.setTo(to);
        return e;
    }
}
