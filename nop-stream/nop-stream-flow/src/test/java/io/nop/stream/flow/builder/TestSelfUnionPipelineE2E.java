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
 * WI6 self-union end-to-end test (constraint 2 at the DSL level): one source declared
 * TWICE into the same {@code <union>} — each declared edge is one merge input, so the
 * sink sees every element exactly twice. The historical per-vertex-pair JobEdge dedup
 * would have delivered each element once (the defect this WI fixes).
 *
 * <p>Owns its own JVM-wide init/destroy cycle: the local runner delivers only the first
 * execute per JVM (pre-existing limitation, reproduced with the union-free reduce
 * pipeline — see plan 13 Non-Blocking Follow-ups).
 */
public class TestSelfUnionPipelineE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-union-pipeline.beans.xml"));
        container = builder.build("stream-flow-self-union-e2e");
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
    public void selfUnionDeliversEachElementTwice() throws Exception {
        StreamModel model = parseStreamXml("/nop/stream/test/test-self-union-pipeline.stream.xml");
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("self-union-e2e");

        @SuppressWarnings("unchecked")
        CollectingSinkFunction<Integer> sink = (CollectingSinkFunction<Integer>)
                BeanContainer.instance().getBean("unionCollectingSink");

        List<Integer> collected = sink.getCollected();
        List<Integer> sorted = new ArrayList<>(collected);
        sorted.sort(Integer::compareTo);
        assertEquals(Arrays.asList(1, 1, 1, 1, 2, 2, 2, 2, 2, 2), sorted,
                "self-union must deliver each element twice (one per declared edge)");
    }

    private static StreamModel parseStreamXml(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }
}
