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
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * WI6 union end-to-end test: parses an XDSL pipeline that merges two sources through a
 * {@code <union>} transform and executes it through the full
 * {@code .stream.xml → DslModelParser → StreamModelDslBuilder → StreamExecutionEnvironment
 * → execute() → sink} path — the "two sources into one operator" evidence the roadmap
 * completion criteria require.
 *
 * <p>Pipeline: {@code src1[1,1,2,2,2]} + {@code src2[2,4]} → union → map(×10) → sink.
 * Expected sink multiset: every source element times ten, exactly once per declared
 * edge input.
 *
 * <p>One execute per test class: the local runner delivers only the first execute of a
 * JVM (pre-existing limitation, reproduced with the union-free reduce pipeline — see
 * plan 13 Non-Blocking Follow-ups), so the self-union variant lives in
 * {@link TestSelfUnionPipelineE2E}.
 */
public class TestUnionPipelineE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-union-pipeline.beans.xml"));
        container = builder.build("stream-flow-union-e2e");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    @Test
    public void twoSourceUnionPipelineProducesMergedOutput() throws Exception {
        StreamModel model = parseStreamXml("/nop/stream/test/test-union-pipeline.stream.xml");
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("union-pipeline-e2e");

        @SuppressWarnings("unchecked")
        CollectingSinkFunction<Integer> sink = (CollectingSinkFunction<Integer>)
                BeanContainer.instance().getBean("unionCollectingSink");

        // src1 [1,1,2,2,2] + src2 [2,4] merged, each element scaled by 10
        List<Integer> collected = sink.getCollected();
        List<Integer> sorted = new ArrayList<>(collected);
        sorted.sort(Integer::compareTo);
        assertEquals(Arrays.asList(10, 10, 20, 20, 20, 20, 40), sorted,
                "union must deliver every element of both sources exactly once (order across inputs undefined)");
        assertEquals(7, collected.size(), "seven merged elements expected");
    }

    private static StreamModel parseStreamXml(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }
}
