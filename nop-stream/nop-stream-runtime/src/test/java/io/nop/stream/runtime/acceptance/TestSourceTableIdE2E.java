package io.nop.stream.runtime.acceptance;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * U4 (deferred-unlock, plan 2377): the declared {@code <source tableId>} executes
 * end-to-end — two sources declaring distinct tableIds merge through a union into a
 * {@code <custom>} observer operator that reads the record-level metadata, then a
 * PLAIN (non change-aware) sink. Pins:
 * <ul>
 * <li>per-record table ownership: every record carries ITS source's declared tableId,
 * zero cross-contamination (the metadata-driven complement of the WI20 field-routing
 * degradation form)</li>
 * <li>pipeline transparency: the INSERT stamp passes the sink guard (no
 * CdcChangeAwareSink needed) and every record reaches the sink</li>
 * <li>the declaration survives the copyForSubtask copy path — env.execute() always
 * copies the source operator (any out-edge), so a hollow carrier would read null here</li>
 * </ul>
 */
public class TestSourceTableIdE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/test-table-id-e2e.beans.xml"))
                .build("table-id-e2e");
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
    public void declaredTableIdsRoutePerRecordThroughUnionToObserver() throws Exception {
        TableIdE2EBeans.TableIdObserver.OBSERVED.clear();
        TableIdE2EBeans.CollectingSink.ROWS.clear();

        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"table-id-e2e\" version=\"1\" parallelism=\"1\">"
                + "<transforms>"
                + "<source id=\"orders\" name=\"OrdersSrc\" bean=\"ordersSrc\" tableId=\"db.orders\"/>"
                + "<source id=\"users\" name=\"UsersSrc\" bean=\"usersSrc\" tableId=\"db.users\"/>"
                + "<union id=\"merge\" name=\"MergeTables\"/>"
                + "<custom id=\"observe\" name=\"ObserveTableId\" customType=\"tableIdObserver\"/>"
                + "<sink id=\"out\" name=\"Out\" bean=\"tableIdSink\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"orders\" to=\"merge\" partition=\"FORWARD\"/>"
                + "<edge id=\"e1\" from=\"users\" to=\"merge\" partition=\"FORWARD\"/>"
                + "<edge id=\"e2\" from=\"merge\" to=\"observe\" partition=\"FORWARD\"/>"
                + "<edge id=\"e3\" from=\"observe\" to=\"out\" partition=\"FORWARD\"/>"
                + "</edges>"
                + "</stream>";

        StreamModel model = (StreamModel) new DslModelParser()
                .parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("table-id-e2e");

        // per-record table ownership: kind:id → declared tableId of its own source
        Map<String, String> expected = new HashMap<>();
        for (int i = 1; i <= 3; i++)
            expected.put("order:" + i, "db.orders");
        for (int i = 1; i <= 2; i++)
            expected.put("user:" + i, "db.users");

        var observed = TableIdE2EBeans.TableIdObserver.OBSERVED;
        assertEquals(5, observed.size(), () -> "every record must be observed: " + observed);
        for (String[] pair : observed) {
            assertEquals(expected.get(pair[0]), pair[1],
                    () -> "record " + pair[0] + " must carry its own source's declared tableId, got "
                            + pair[1] + " (declaration was not wired to the executed copy)");
            assertNotNull(pair[1], "a declared tableId must never be observed as null");
        }

        // transparency: the plain (non change-aware) sink received every record —
        // the INSERT stamp passes the guard and nothing was dropped
        var rows = TableIdE2EBeans.CollectingSink.ROWS;
        assertEquals(5, rows.size(), () -> "every record must reach the sink: " + rows);
        assertTrue(rows.stream().allMatch(r -> expected.containsKey(r.get("kind") + ":" + r.get("id"))),
                "sink values must be exactly the union of both sources");
    }

    /**
     * Negative control: an UNDECLARED source keeps the historic null metadata — the
     * declaration face adds behavior, it does not silently stamp by default.
     */
    @Test
    public void undeclaredSourcesKeepNullTableIdAtRuntime() throws Exception {
        TableIdE2EBeans.TableIdObserver.OBSERVED.clear();
        TableIdE2EBeans.CollectingSink.ROWS.clear();

        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"table-id-e2e-undeclared\" version=\"1\" parallelism=\"1\">"
                + "<transforms>"
                + "<source id=\"orders\" name=\"OrdersSrc\" bean=\"ordersSrc\"/>"
                + "<sink id=\"out\" name=\"Out\" bean=\"tableIdSink\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"orders\" to=\"out\" partition=\"FORWARD\"/>"
                + "</edges>"
                + "</stream>";

        // same observer wiring, via a custom node, to keep the read seam identical
        String xmlWithObserver = xml.replace(
                "<sink id=\"out\" name=\"Out\" bean=\"tableIdSink\"/>",
                "<custom id=\"observe\" name=\"ObserveTableId\" customType=\"tableIdObserver\"/>"
                        + "<sink id=\"out\" name=\"Out\" bean=\"tableIdSink\"/>")
                .replace("<edge id=\"e0\" from=\"orders\" to=\"out\" partition=\"FORWARD\"/>",
                        "<edge id=\"e0\" from=\"orders\" to=\"observe\" partition=\"FORWARD\"/>"
                                + "<edge id=\"e3\" from=\"observe\" to=\"out\" partition=\"FORWARD\"/>");

        StreamModel model = (StreamModel) new DslModelParser()
                .parseFromNode(XNode.parse(xmlWithObserver));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("table-id-e2e-undeclared");

        var observed = TableIdE2EBeans.TableIdObserver.OBSERVED;
        assertEquals(3, observed.size(), () -> "every record must be observed: " + observed);
        for (String[] pair : observed) {
            assertNull(pair[1],
                    () -> "no declaration = null tableId (historic default), got " + pair[1]
                            + " for " + pair[0]);
        }
        assertEquals(3, TableIdE2EBeans.CollectingSink.ROWS.size());
    }
}
