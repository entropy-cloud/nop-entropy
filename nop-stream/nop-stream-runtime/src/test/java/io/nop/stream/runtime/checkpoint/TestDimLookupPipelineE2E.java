/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI14 DSL-level evidence: the dimension-table lookup on the FULL DSL → runtime path
 * ({@code .stream.xml → DslModelParser → StreamModelDslBuilder → execute() → sink}).
 * This test lives in the runtime module because only the runtime classpath carries
 * the checkpoint engine (ServiceLoader {@code ICheckpointExecutorFactory}), whose
 * task wiring provisions the keyed state backend the lookup function's keyed cache
 * needs — the flow module's local path has no backend provisioning.
 */
public class TestDimLookupPipelineE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-dim-lookup-pipeline.beans.xml"));
        container = builder.build("stream-runtime-dim-lookup-e2e");
        container.start();
        io.nop.api.core.ioc.BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
        io.nop.api.core.ioc.BeanContainer.registerInstance(null);
    }

    @Test
    public void dimLookupPipelineProducesEnrichedOutput() throws Exception {
        DimCollectingSink.clear();

        StreamModel model = parseStreamXml("/nop/stream/test/test-dim-lookup-pipeline.stream.xml");
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("dim-lookup-e2e");

        // source [k1, k2, k1] enriched through the keyed dim cache:
        //   k1 → merchant-a, k2 → merchant-b, k1 again → cache hit (same value)
        List<Map<String, Object>> collected = DimCollectingSink.getCollected();
        List<String> dimValues = collected.stream()
                .map(m -> String.valueOf(m.get("dimValue")))
                .sorted()
                .collect(Collectors.toList());
        assertEquals(List.of("merchant-a", "merchant-a", "merchant-b"), dimValues,
                "every record must carry its dimension attribute (k1 twice — cache hit included)");
    }

    private static StreamModel parseStreamXml(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }
}
