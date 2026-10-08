/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI8 Phase 2 (plan 2332) end-to-end: an XDSL {@code <sideOutput tag condition>} node
 * plus its consuming {@code <edge ... outputTag>} feeds a downstream transform through
 * the tag channel while the main path stays untouched. Full chain:
 * {@code .stream.xml → DslModelParser → StreamModelDslBuilder → env.execute() →
 * StreamGraph/JobGraph tag edge → GraphExecutionPlan tag wiring → registered tag-edge
 * consumer → downstream transform → sink}.
 *
 * <p>Pipeline: source [1,2,3] → sideOutput(tag="big", condition="event &gt; 1") —
 * main path → mainSink (ALL records unchanged), tag channel → tagMap → tagSink
 * (only 2 and 3). The condition must be genuinely evaluated per record — a silent
 * copy-all or a filtered main path both fail the assertions.
 */
public class TestSideOutputPipelineE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-side-output-pipeline.beans.xml"));
        container = builder.build("stream-runtime-side-output-e2e");
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
    public void sideOutputRoutesTagChannelAndKeepsMainPathIntact() throws Exception {
        SideOutputSinkStore.clear();

        StreamModel model = parseStreamXml("/nop/stream/test/test-side-output-pipeline.stream.xml");
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("side-output-pipeline-e2e");

        // Main path: the tap passes every record through unchanged.
        assertEquals(List.of(1, 2, 3), SideOutputSinkStore.get("main"),
                "the tap's main output must carry every source record unchanged");

        // Tag channel: only condition-matching records (event > 1) cross the tag edge
        // into the downstream transform.
        assertEquals(List.of(2, 3), SideOutputSinkStore.get("tag"),
                "the tag channel must carry exactly the condition-matching records");
    }

    private static StreamModel parseStreamXml(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }
}
