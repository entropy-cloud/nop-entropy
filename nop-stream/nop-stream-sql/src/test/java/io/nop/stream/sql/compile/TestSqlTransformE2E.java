package io.nop.stream.sql.compile;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.sql.compile.testing.SqlTestSink;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * U2 (deferred-unlock, plan 2375): the {@code <sqlTransform>} declaration executes
 * end-to-end with the REAL compiler — declared vertex → EQL fragment splice →
 * windowed aggregate (UPDATE emit) → sink. Same data and expectation as the
 * top-level {@code <sql>} generator path (TestTumbleAggregateQueryE2E), proving the
 * transform-level face computes the same windows from a graph position.
 */
public class TestSqlTransformE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        // the module's own SPI provider (ISqlStreamCompiler) and sql-row-agg resolver
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-sql-compiler.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        container = builder.build("sql-transform-e2e");
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

    private static final String MODEL_XML = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
            + "x:schema=\"/nop/schema/stream/stream.xdef\" "
            + "name=\"sql-transform-e2e\" version=\"1\">"
            + "<transforms>"
            + "<source id=\"events\" name=\"Events\" bean=\"orders\"/>"
            + "<sqlTransform id=\"agg\" name=\"TumbleSum\" emit=\"UPDATE\">"
            + "<schemas>"
            + "<field name=\"ts\" type=\"bigint\"/>"
            + "<field name=\"item\" type=\"string\"/>"
            + "<field name=\"amount\" type=\"int\"/>"
            + "</schemas>"
            + "<source>SELECT item, sum(amount) AS total FROM TUMBLE(events.ts, INTERVAL 5 SECOND) "
            + "WHERE amount > 0 GROUP BY item</source>"
            + "</sqlTransform>"
            + "<sink id=\"out\" name=\"Out\" bean=\"testSink\"/>"
            + "</transforms>"
            + "<edges>"
            + "<edge id=\"e0\" from=\"events\" to=\"agg\"/>"
            + "<edge id=\"e1\" from=\"agg\" to=\"out\"/>"
            + "</edges>"
            + "</stream>";

    @Test
    public void sqlTransformWindowedAggregateExecutesEndToEnd() throws Exception {
        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        sink.getCollected().clear();

        StreamModel model = (StreamModel) new DslModelParser()
                .parseFromNode(XNode.parse(MODEL_XML));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("sql-transform-e2e");

        List<Map<String, Object>> rows = sink.getCollected().stream()
                .map(o -> (Map<String, Object>) o).collect(Collectors.toList());
        assertTrue(!rows.isEmpty(), "UPDATE emit must produce rows");

        // UPDATE emit fires per record: the LAST row per item carries the window's
        // final total — same values as the top-level <sql> path
        // (TestTumbleAggregateQueryE2E: window [0,5s) a=1+3, b=2; [10s,15s) b=4;
        // trailing [15s,20s) c=1)
        Map<String, Object> finalTotals = new HashMap<>();
        for (Map<String, Object> r : rows) {
            finalTotals.put(String.valueOf(r.get("item")), r.get("total"));
        }
        assertEquals("4", String.valueOf(finalTotals.get("a")), () -> "rows: " + rows);
        assertEquals("4", String.valueOf(finalTotals.get("b")), () -> "rows: " + rows);
        assertEquals("1", String.valueOf(finalTotals.get("c")), () -> "rows: " + rows);
    }

    /**
     * U2 audit must-fix 1 (review M4): TWO windowed fragments on separate branches
     * coexist — each fragment carries its own windowingStrategies/aggregators
     * registries, renamed with its vertex-id prefix and merged into the parent model
     * without id collisions. A broken rename (or merge) fails the build with a
     * duplicate registry id, so build+execute green plus the two distinct strategy
     * ids pin the mechanics.
     */
    @Test
    public void twoWindowedFragmentsCoexistWithDistinctRegistryIds() throws Exception {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"sql-transform-dual-window\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"events\" name=\"Events\" bean=\"orders\"/>"
                + "<sqlTransform id=\"t1\" name=\"Tumble5s\" emit=\"UPDATE\">"
                + "<schemas>"
                + "<field name=\"ts\" type=\"bigint\"/>"
                + "<field name=\"item\" type=\"string\"/>"
                + "<field name=\"amount\" type=\"int\"/>"
                + "</schemas>"
                + "<source>SELECT item, sum(amount) AS total FROM TUMBLE(events.ts, INTERVAL 5 SECOND) "
                + "WHERE amount > 0 GROUP BY item</source>"
                + "</sqlTransform>"
                + "<sqlTransform id=\"t2\" name=\"Tumble10s\" emit=\"UPDATE\">"
                + "<schemas>"
                + "<field name=\"ts\" type=\"bigint\"/>"
                + "<field name=\"item\" type=\"string\"/>"
                + "<field name=\"amount\" type=\"int\"/>"
                + "</schemas>"
                + "<source>SELECT item, sum(amount) AS total FROM TUMBLE(events.ts, INTERVAL 10 SECOND) "
                + "WHERE amount > 0 GROUP BY item</source>"
                + "</sqlTransform>"
                + "<sink id=\"out1\" name=\"Out1\" bean=\"testSink\"/>"
                + "<sink id=\"out2\" name=\"Out2\" bean=\"testSink\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"events\" to=\"t1\"/>"
                + "<edge id=\"e1\" from=\"t1\" to=\"out1\"/>"
                + "<edge id=\"e2\" from=\"events\" to=\"t2\"/>"
                + "<edge id=\"e3\" from=\"t2\" to=\"out2\"/>"
                + "</edges>"
                + "</stream>";

        StreamModel model = (StreamModel) new DslModelParser()
                .parseFromNode(XNode.parse(xml));

        // CRA 8.45 R4-05 W-07 reshape (E-2538-2, plan 2538 组 A): build() is
        // zero-consumption — the fragment splice (registry rename + merge) runs on
        // the builder's internal defensive copy, so the caller-held model stays
        // pristine and the expanded strategy ids are no longer observable on it.
        // Rename/merge mechanics are pinned through the output-level oracle (both
        // branches execute their windowed aggregates; a broken rename/merge fails
        // the build with a duplicate registry id) plus a zero-consumption pin and a
        // re-entrant rebuild from the same pristine model.
        StreamModelDslBuilder.of(model).build().execute("sql-transform-dual-window");
        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        assertTrue(!sink.getCollected().isEmpty(),
                "both branches feed the sink store");
        int rowsFirstRun = sink.getCollected().size();
        sink.getCollected().clear();

        // re-entrant rebuild: the second expansion starts from the untouched model
        // (both sqlTransform vertices still declared) and must splice + execute
        // identically — under the old consume-in-place semantics this would collide
        // with the already-spliced transforms
        long remainingSqlTransforms = model.getTransforms().stream()
                .filter(t -> t instanceof io.nop.stream.flow.model.StreamSqlTransformModel)
                .count();
        assertTrue(model.getWindowingStrategies().isEmpty() && remainingSqlTransforms == 2,
                () -> "caller model stays pristine after build (W-07 zero-consumption): "
                        + "the raw <sqlTransform> vertices survive and no fragment "
                        + "strategies leaked onto the parsed model; transforms="
                        + model.getTransforms().size() + ", strategies="
                        + model.getWindowingStrategies().size());
        StreamModelDslBuilder.of(model).build().execute("sql-transform-dual-window-rebuild");
        assertEquals(rowsFirstRun, sink.getCollected().size(),
                "rebuild from the pristine model must reproduce the same pipeline");
    }
}
