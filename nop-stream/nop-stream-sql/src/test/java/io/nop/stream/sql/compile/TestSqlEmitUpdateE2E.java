/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.windowing.StreamEmitMode;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.sql.compile.testing.SqlTestSink;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI37 (D11 release) 端到端判别：{@code emit=UPDATE} 的窗口聚合经真实执行逐记录发射窗口
 * 更新值（ON_WINDOW_UPDATE 语义），对照 close 档（TestTumbleAggregateQueryE2E）的窗口末
 * 单次发射。EmitUpdateTrigger + ACCUMULATING 的组合在每条记录上发射当前聚合值，窗口末
 * 触发器再发射一次终值（last-value-wins 等值幂等）。
 *
 * <p>数据面（OrdersSourceFunction.FIXED_DATA）：窗口 [0,5s) 内 a=1(ts=1s), b=2, a=null
 * （sum 跳过 null——发射不变运行值 a=1）, a=3（累计 a=4）；窗口 [10,15s) 内 b=4, a=-5；
 * 窗口 [15,20s) 内 c=1。update 档预期发射 = 7 条逐记录（a=1, b=2, a=1, a=4, b=4, a=-5,
 * c=1）+ 7 条窗口末终值（a=4, b=2, a=4, a=-5, b=4, c=1 中的 5 个非空窗口键窗）——实际
 * 合计 12 行：a=1×2, a=4×2, a=-5×2, b=2×2, b=4×2, c=1×2（每值恰好 2 次：逐记录一次 +
 * 窗口末一次）。
 */
public class TestSqlEmitUpdateE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        container = builder.build("sql-emit-update-e2e");
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
    @SuppressWarnings("unchecked")
    public void emitUpdateEmitsPerRecordWindowUpdates() throws Exception {
        String xml = StreamSqlCompiler.compile(null,
                "SELECT item, sum(amount) AS total FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) "
                        + "GROUP BY item",
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", StreamEmitMode.UPDATE);
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("emit-update-e2e");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        List<Map<String, Object>> rows = sink.getCollected().stream()
                .map(o -> (Map<String, Object>) o).collect(Collectors.toList());

        List<String> sorted = rows.stream()
                .map(r -> r.get("item") + "=" + r.get("total"))
                .sorted().collect(Collectors.toList());
        // per-record emissions (a=1, b=2, a=4, b=4, c=1) + window-end finals
        // (a=4, b=2, b=4, c=1) — the discriminating signature of the update tier
        assertEquals(Arrays.asList(
                        "a=-5", "a=-5", "a=1", "a=1", "a=4", "a=4",
                        "b=2", "b=2", "b=4", "b=4",
                        "c=1", "c=1"),
                sorted, () -> "emit=UPDATE emissions: " + rows);
        assertEquals(12, rows.size(),
                "every record emits the running window aggregate once (null-amount record "
                        + "emits the unchanged running value) and every keyed window emits "
                        + "its final value once");
    }
}
