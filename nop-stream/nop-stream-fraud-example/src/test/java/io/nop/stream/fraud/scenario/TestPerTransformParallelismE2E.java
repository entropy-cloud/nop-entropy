package io.nop.stream.fraud.scenario;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static io.nop.stream.fraud.scenario.ScenarioTestSupport.parseStreamXml;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 29 (Phase 2) LOCAL e2e: {@code transforms/@parallelism} is consumed end-to-end
 * from the XDSL declaration through {@code env.execute()} to the sink output.
 *
 * <p>Topology (see {@code fraud-per-transform-parallelism.stream.xml}): bounded source
 * (P=1) --HASH edge--> map (P=4) --> collecting sink (P=4). The HASH edge is the
 * load-bearing detail: a FORWARD edge with srcP=1, tgtP=4 concentrates all traffic on
 * target subtask 0 (modulo semantics) and would make the distribution check a
 * structural false-green. Assertions:
 * <ol>
 *   <li><b>Exactness</b>: every source element reaches the sink exactly once;</li>
 *   <li><b>Distribution</b>: elements were processed by &gt; 1 map subtask (the map
 *       bean is shared across subtasks; each subtask invokes it on its own task
 *       thread, and a latch forces two distinct subtask threads to overlap before
 *       the pipeline may finish — a single-subtask execution fails the latch);</li>
 *   <li><b>Plan-side</b>: the declared per-vertex parallelism is what the execution
 *       plan materializes (per-vertex subtask count == declared value), asserted on
 *       an identically rebuilt graph.</li>
 * </ol>
 */
public class TestPerTransformParallelismE2E {

    private static final String SCENARIO_PATH = "/nop/stream/test/fraud-per-transform-parallelism.stream.xml";

    private static final int ELEMENT_COUNT = 40;

    /** Shared map bean: per-subtask observation via the invoking task thread. */
    public static final class ThreadingMap implements MapFunction<String, String> {
        private static final long serialVersionUID = 1L;

        final Map<Long, Integer> perThreadCounts = new ConcurrentHashMap<>();
        final CountDownLatch twoSubtasks = new CountDownLatch(2);

        @Override
        public String map(String value) throws Exception {
            perThreadCounts.merge(Thread.currentThread().getId(), 1, Integer::sum);
            // Force two distinct subtask threads to overlap: every arrival counts down
            // once two distinct threads have been observed, opening the gate for all
            // waiters. A single-subtask execution stalls until the bounded wait
            // elapses and then fails the distinct-thread assertion.
            if (perThreadCounts.size() >= 2) {
                twoSubtasks.countDown();
            }
            twoSubtasks.await(20, TimeUnit.SECONDS);
            return value;
        }
    }

    /** Bounded keyed source: ELEMENT_COUNT distinct keys spread over the hash edge. */
    public static final class KeyedElementsSource implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(SourceContext<String> ctx) {
            for (int i = 0; i < ELEMENT_COUNT; i++) {
                ctx.collect("key-" + i);
            }
        }

        @Override
        public void cancel() {
        }
    }

    /** Shared collecting sink bean (plain SinkFunctions stay shared across subtasks). */
    public static final class CollectingSink implements SinkFunction<String> {
        private static final long serialVersionUID = 1L;

        final List<String> collected = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void consume(String value) {
            collected.add(value);
        }
    }

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    @Timeout(120)
    public void declaredParallelismExecutesToEndWithDistributedData() throws Exception {
        ThreadingMap mapBean = new ThreadingMap();
        CollectingSink sink = new CollectingSink();
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("paraSource", new KeyedElementsSource());
        resolver.register("paraMap", mapBean);
        resolver.register("paraSink", sink);

        StreamModel model = parseStreamXml(SCENARIO_PATH);
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();

        // (3, plan side) same declaration rebuilt: per-vertex subtask counts match the
        // declared values (source undeclared -> stream level 1; map/sink declared 4).
        StreamModel planModel = parseStreamXml(SCENARIO_PATH);
        StreamExecutionEnvironment planEnv = StreamModelDslBuilder.of(planModel,
                new InMemoryBeanFunctionResolver()
                        .register("paraSource", new KeyedElementsSource())
                        .register("paraMap", new ThreadingMap())
                        .register("paraSink", new CollectingSink())).build();
        JobGraph jobGraph = planEnv.buildJobGraph("per-transform-parallelism-plan");
        GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph, null, false);
        int sourceSubtasks = -1;
        int wideSubtasks = -1;
        for (java.util.Map.Entry<String, JobVertex> entry : jobGraph.getVertices().entrySet()) {
            int declared = entry.getValue().getParallelism();
            int materialized = plan.getSubtasks(entry.getKey()).size();
            assertEquals(declared, materialized,
                    "execution plan must materialize exactly the declared subtask count for "
                            + entry.getValue().getName());
            if (entry.getValue().getName().startsWith("paraSource")) {
                sourceSubtasks = materialized;
            } else {
                wideSubtasks = materialized;
            }
        }
        assertEquals(1, sourceSubtasks, "undeclared source inherits stream-level parallelism 1");
        assertEquals(4, wideSubtasks, "declared map/sink parallelism 4 must materialize 4 subtasks");

        // (1 + 2, execution side) full LOCAL run to the sink.
        env.execute("fraud-per-transform-parallelism");

        // Exact output: every element exactly once (multiset equality).
        Map<String, Integer> counts = new TreeMap<>();
        synchronized (sink.collected) {
            for (String value : sink.collected) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        assertEquals(ELEMENT_COUNT, counts.size(), "all distinct elements must be collected");
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            assertEquals(1, counts.get("key-" + i),
                    "element key-" + i + " must reach the sink exactly once");
        }

        // Distribution: > 1 map subtask actually processed data.
        assertTrue(mapBean.perThreadCounts.size() >= 2,
                "HASH edge must distribute data across >1 map subtask; observed threads: "
                        + mapBean.perThreadCounts.keySet());
        int totalMapped = mapBean.perThreadCounts.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(ELEMENT_COUNT, totalMapped, "every element must have crossed the map vertex");
    }
}
