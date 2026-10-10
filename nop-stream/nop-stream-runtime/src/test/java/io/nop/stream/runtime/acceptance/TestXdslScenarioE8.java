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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI24 E8 (SeaTunnel three-examples degradation): ONE source fanning out to FOUR
 * filter branches/sinks (branch-by-kind), plus the multi-table degradation routing
 * (WI20 tableTag pattern). Each branch receives exactly its own records — the
 * 1-source-4-branch-4-sink fan-out data-plane pin.
 */
public class TestXdslScenarioE8 {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/xdsl-e8-seatunnel-multi.beans.xml"))
                .build("xdsl-e8-seatunnel-multi");
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
    public void fanOutRoutesEachRecordToExactlyOneBranchSink() throws Exception {
        io.nop.stream.runtime.acceptance.E8MultiSinkBeans.BY_KIND.clear();

        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e8-seatunnel-multi.stream.xml"));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("xdsl-e8-seatunnel-multi");

        var byKind = io.nop.stream.runtime.acceptance.E8MultiSinkBeans.byKind();
        // each branch sink received exactly its own single record
        assertEquals(1, byKind.get("A").size(), () -> "byKind: " + byKind);
        assertEquals(1, byKind.get("B").size(), () -> "byKind: " + byKind);
        assertEquals(1, byKind.get("C").size(), () -> "byKind: " + byKind);
        assertEquals(1, byKind.get("D").size(), () -> "byKind: " + byKind);
        // no cross-contamination: totals sum to the source count
        int total = byKind.values().stream().mapToInt(List::size).sum();
        assertEquals(4, total, () -> "byKind: " + byKind);
    }
}
