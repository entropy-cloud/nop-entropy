/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.acceptance;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI22 E3 (Flink SideOutputExample shape): long words (len &gt; 3) route through the
 * side-output tap channel to the downstream vertex sink; short words stay on the
 * main path into the window-sum vertex. Partition-ownership data-plane assertion.
 */
public class TestXdslScenarioE3 {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/xdsl-e3-sideoutput.beans.xml"))
                .build("xdsl-e3-sideoutput");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null)
            container.stop();
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    @Test
    public void longWordsRouteToTapChannel() throws Exception {
        E3Stores.SHORT.clear();
        E3Stores.LONG.clear();

        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e3-sideoutput.stream.xml"));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("xdsl-e3-sideoutput");

        // tap channel ownership: exactly the condition-matching records (len>3)
        // reach the tag-channel downstream vertex
        assertEquals(List.of("long", "word"), E3Stores.LONG, () -> "long: " + E3Stores.LONG);
    }

    /**
     * Full partition ownership on the tap shape. WI22 recorded this as a "live
     * engine routing defect" (3-out-edge tap vertex swallowing untagged main-path
     * records, expected SHORT=[2,3]) — that adjudication was a diagnostic misread,
     * corrected by the U5 investigation (deferred-unlock roadmap): the fixture
     * words are [cat(3), long(4), dog(3), word(4)], so untagged = cat+dog and
     * SHORT=[3,3] was the CORRECT observation all along ("cat len=2" in the WI22
     * evidence is an arithmetic error — "cat".length()==3). The always-true-filter
     * control run SHORT=[3,4,3,4] is the copy-tap contract itself: the tap adds a
     * matched side channel and never filters the main path (SideOutputTapOperator),
     * so all four records legitimately appear upstream of the len<=3 filter.
     * Engine routing (EdgeWriterOutput per-edge adjudication) is correct; WI8b's
     * TestSideOutputPipelineE2E main=[1,2,3] pins the same semantics.
     */
    @Test
    public void shortWordsStayOnMainPath() throws Exception {
        E3Stores.SHORT.clear();
        E3Stores.LONG.clear();

        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e3-sideoutput.stream.xml"));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("xdsl-e3-sideoutput");

        // full partition ownership: short words (len<=3) reach the window vertex,
        // long words (len>3) reach the tap-channel vertex, no cross-contamination
        assertEquals(List.of(3, 3), E3Stores.SHORT, () -> "short: " + E3Stores.SHORT);
        assertEquals(List.of("long", "word"), E3Stores.LONG, () -> "long: " + E3Stores.LONG);
    }
}
