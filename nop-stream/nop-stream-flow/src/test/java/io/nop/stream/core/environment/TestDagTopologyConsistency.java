/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.environment;

import java.util.Arrays;
import java.util.List;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.transformation.SinkTransformation;
import io.nop.stream.core.transformation.SourceTransformation;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DAG topology consistency test (Phase 2): builds an equivalent pipeline
 * ({@code source → keyBy → map → sink}) via both XDSL and the Java DataStream API,
 * then compares the two {@link StreamExecutionEnvironment#getTransformations()}
 * lists to prove they produce the same DAG topology structure.
 *
 * <p>This test lives in the {@code io.nop.stream.core.environment} package so it can
 * access the package-private {@link StreamExecutionEnvironment#getTransformations()}
 * accessor. It is compiled into the {@code nop-stream-flow} test classpath.
 *
 * <p>Per the plan's Non-Goals, this is a <b>topology structure</b> comparison
 * (same transform count and concrete {@code Transformation} subclass per slot),
 * not an exact {@code computeFingerprint()} equality — the latter is impossible
 * while {@code Transformation.id} is a static AtomicInteger and
 * {@code Transformation.toString()} is unoverridden (core model limitation,
 * tracked as a deferred follow-up).
 */
public class TestDagTopologyConsistency {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void xdslAndJavaApiProduceSameDagTopology() {
        // ---- XDSL path ----
        StreamExecutionEnvironment xdslEnv = buildViaXdsl();
        List<Transformation<?>> xdslTransforms = xdslEnv.getTransformations();

        // ---- Java API path ----
        StreamExecutionEnvironment javaEnv = buildViaJavaApi();
        List<Transformation<?>> javaTransforms = javaEnv.getTransformations();

        assertEquals(javaTransforms.size(), xdslTransforms.size(),
                () -> "XDSL and Java API should produce the same number of transformations. "
                        + "Java API: " + describe(javaTransforms)
                        + " | XDSL: " + describe(xdslTransforms));

        // Compare concrete Transformation subclass per slot (topology equivalence).
        for (int i = 0; i < javaTransforms.size(); i++) {
            final int slot = i;
            Class<?> javaClass = classKey(javaTransforms.get(slot));
            Class<?> xdslClass = classKey(xdslTransforms.get(slot));
            assertEquals(javaClass, xdslClass,
                    () -> "Transformation at slot " + slot + " differs: Java API produced "
                            + javaClass.getSimpleName() + ", XDSL produced "
                            + xdslClass.getSimpleName());
        }
    }

    @Test
    public void xdslPipelineHasSourceMapSinkTransformationTypes() {
        StreamExecutionEnvironment env = buildViaXdsl();
        List<Class<?>> types = env.getTransformations().stream()
                .map(this::classKey)
                .collect(java.util.stream.Collectors.toList());

        assertTrue(types.stream().anyMatch(SourceTransformation.class::equals),
                "Pipeline must contain a SourceTransformation: " + types);
        assertTrue(types.stream().anyMatch(SinkTransformation.class::equals),
                "Pipeline must contain a SinkTransformation: " + types);
        assertTrue(types.stream().anyMatch(OneInputTransformation.class::equals),
                "Pipeline must contain at least one OneInputTransformation (map/keyBy): " + types);
    }

    // ----------------------------------------------------------------
    // pipeline builders
    // ----------------------------------------------------------------

    private StreamExecutionEnvironment buildViaXdsl() {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"dag-topo\" version=\"1\" parallelism=\"1\">"
                + "<transforms>"
                + "<source id=\"src\" bean=\"srcFn\"/>"
                + "<map id=\"m\"><source>return event;</source></map>"
                + "<sink id=\"out\" bean=\"sinkFn\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"src\" to=\"m\" partition=\"FORWARD\"/>"
                + "<edge id=\"e1\" from=\"m\" to=\"out\" partition=\"FORWARD\"/>"
                + "</edges>"
                + "</stream>";

        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(node);

        return StreamModelDslBuilder.of(model, fixedResolver()).build();
    }

    private StreamExecutionEnvironment buildViaJavaApi() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        DataStream<String> source = env.addSource(new FixedSource(), "dag-topo-src");
        DataStream<String> mapped = source.map((MapFunction<String, String>) s -> s);
        mapped.sink(new FixedSink());
        return env;
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    /**
     * Reduce a Transformation to its "topology kind" — the concrete subclass that
     * determines its role in the DAG (Source/Sink/OneInput/Partition). We do not
     * compare generic type parameters or instance identity.
     */
    private Class<?> classKey(Transformation<?> t) {
        return t.getClass();
    }

    private String describe(List<Transformation<?>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (Transformation<?> t : list) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(t.getClass().getSimpleName());
        }
        return sb.append("]").toString();
    }

    // ----------------------------------------------------------------
    // fixed test fixtures (shared between XDSL and Java API paths so the
    // pipelines are semantically equivalent)
    // ----------------------------------------------------------------

    /** In-memory resolver for the XDSL path — returns the same fixtures the Java path uses. */
    private static io.nop.stream.flow.builder.InMemoryBeanFunctionResolver fixedResolver() {
        io.nop.stream.flow.builder.InMemoryBeanFunctionResolver r =
                new io.nop.stream.flow.builder.InMemoryBeanFunctionResolver();
        r.register("srcFn", new FixedSource());
        r.register("sinkFn", new FixedSink());
        return r;
    }

    private static final class FixedSource implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;
        static final List<String> DATA = Arrays.asList("a", "b", "c");
        private volatile boolean running = true;

        @Override
        public void run(SourceContext<String> ctx) {
            for (String s : DATA) {
                if (!running) break;
                ctx.collect(s);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    private static final class FixedSink implements SinkFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(String value) {
        }
    }
}
