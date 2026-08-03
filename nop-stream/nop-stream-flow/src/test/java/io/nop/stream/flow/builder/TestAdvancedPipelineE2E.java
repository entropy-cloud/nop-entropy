/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.List;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.testing.CollectingSinkFunction;
import io.nop.stream.flow.testing.SumReduceFunction;
import io.nop.stream.flow.testing.TestSourceFunction;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

/**
 * Advanced pipeline end-to-end test (Phase 2): parses an XDSL pipeline that uses
 * the {@code <reduce>} advanced transform on a {@code KeyedStream} upstream and
 * executes it through the full
 * {@code .stream.xml → DslModelParser → StreamModelDslBuilder → StreamExecutionEnvironment
 * → execute() → sink} path.
 *
 * <p><b>Why reduce-on-KeyedStream and not window→aggregate?</b> The plan's Phase 2
 * exit criterion calls for a window→aggregate end-to-end pipeline. However,
 * {@code WindowedStream.aggregate/reduce} require an {@code IWindowOperatorFactory}
 * (provided by {@code nop-stream-runtime}, a heavy dependency not on the
 * {@code nop-stream-flow} test classpath). {@code KeyedStream.reduce} executes
 * without that factory (proven by {@code TestKeyedStreamAggregation} in
 * {@code nop-stream-core}), so this test exercises the advanced-transform dispatch
 * (reduce on KeyedStream) end-to-end. The window→aggregate wiring is covered by
 * {@link TestAdvancedTransforms#aggregateDispatchesToWindowedAggregatePath}
 * (proves dispatch up to the runtime boundary).
 *
 * <p>Pipeline: {@code source[bean=intSource]} (emits [1,2,3]) → {@code keyBy[keyExpr]}
 * → {@code reduce[bean=sumReducer]} → {@code sink[bean=collectingSink]}.
 */
public class TestAdvancedPipelineE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-reduce-pipeline.beans.xml"));
        container = builder.build("stream-flow-advanced-e2e");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
    }

    @Test
    public void reducePipelineProducesCorrectAggregatedOutput() throws Exception {
        StreamModel model = parseStreamXml("/nop/stream/test/test-reduce-pipeline.stream.xml");
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();

        env.execute("reduce-pipeline-e2e");

        @SuppressWarnings("unchecked")
        CollectingSinkFunction<Integer> sink = (CollectingSinkFunction<Integer>)
                BeanContainer.instance().getBean("advancedCollectingSink");
        List<Integer> collected = sink.getCollected();

        // Source emits [1,1,2,2,2], keyBy on the value itself groups them:
        //   key 1: [1, 1] → reduce(sum): first 1 (no previous), then 1+1=2
        //   key 2: [2, 2, 2] → reduce(sum): first 2, then 4, then 6
        // KeyedStream.reduce emits one output per element (first element of a key is
        // emitted as-is). So the sink collects [1, 2, 2, 4, 6] in arrival order.
        assertEquals(Arrays.asList(1, 2, 2, 4, 6), collected,
                "Keyed reduce should produce running sums per key");
    }

    private static StreamModel parseStreamXml(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }
}
