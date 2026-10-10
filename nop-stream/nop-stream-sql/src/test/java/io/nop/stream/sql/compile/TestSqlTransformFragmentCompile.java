package io.nop.stream.sql.compile;

import io.nop.core.lang.xml.XNode;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.windowing.StreamEmitMode;
import io.nop.stream.flow.spi.ISqlStreamCompiler;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * U2 (deferred-unlock, plan 2375): the {@code <sqlTransform>} fragment contract.
 * The product must be a complete xdef-valid stream model WITHOUT a sink whose
 * FROM-table source vertex carries the reserved entry id — the builder strips it
 * and splices the chain onto the declaring vertex's upstream. Multi-source shapes
 * (JOIN, UNION) fail fast: a fragment has exactly one entry.
 */
public class TestSqlTransformFragmentCompile {

    static final Map<String, String> ORDERS_SCHEMA = schema(
            entry("ts", "bigint"), entry("item", "string"), entry("amount", "int"));

    private static Map<String, String> schema(Map.Entry<String, String>... entries) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entries) {
            m.put(e.getKey(), e.getValue());
        }
        return m;
    }

    private static Map.Entry<String, String> entry(String k, String v) {
        return Map.entry(k, v);
    }

    private static XNode compileTransform(String sql) {
        return XNode.parse(StreamSqlCompiler.compileTransform(null, sql, ORDERS_SCHEMA,
                StreamEmitMode.CLOSE, java.util.Collections.emptyMap()));
    }

    private static XNode childByTag(XNode node, String tag) {
        if (node.getChildren() == null)
            return null;
        for (XNode c : node.getChildren()) {
            if (c.getTagName().equals(tag))
                return c;
        }
        return null;
    }

    private static boolean hasDescendantTag(XNode node, String tag) {
        if (tag.equals(node.getTagName()))
            return true;
        if (node.getChildren() == null)
            return false;
        for (XNode c : node.getChildren()) {
            if (hasDescendantTag(c, tag))
                return true;
        }
        return false;
    }

    @org.junit.jupiter.api.BeforeAll
    public static void init() {
        io.nop.core.initialize.CoreInitialization.initializeTo(
                io.nop.core.CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
    }

    @org.junit.jupiter.api.AfterAll
    public static void destroy() {
        io.nop.core.initialize.CoreInitialization.destroy();
    }

    /** Entry contract: reserved source id, no sink, edges anchored on the entry. */
    @Test
    public void plainFilterFragmentHasEntrySourceAndNoSink() {
        XNode stream = compileTransform(
                "SELECT item FROM orders WHERE amount > 5");
        assertEquals("stream", stream.getTagName(), "fragment root is a stream model");
        assertNull(childByTag(stream, "sink"), "a fragment product has no sink");

        XNode transforms = childByTag(stream, "transforms");
        XNode entry = null;
        int sourceCount = 0;
        for (XNode t : transforms.getChildren()) {
            if ("source".equals(t.getTagName())) {
                sourceCount++;
                if (ISqlStreamCompiler.TRANSFORM_ENTRY_ID.equals(t.getAttr("id")))
                    entry = t;
            }
        }
        assertEquals(1, sourceCount, "exactly one (entry) source vertex");
        assertNotNull(entry, "the FROM-table source keeps the reserved entry id");
        assertEquals("orders", entry.getAttr("bean"), "entry source binds the FROM table");

        boolean entryAnchored = false;
        for (XNode e : childByTag(stream, "edges").getChildren()) {
            if (ISqlStreamCompiler.TRANSFORM_ENTRY_ID.equals(e.getAttr("from")))
                entryAnchored = true;
        }
        assertTrue(entryAnchored, "the fragment chain starts at the entry id");
    }

    /** TUMBLE windowed aggregate fragment: strategy + tsw chain inside the fragment. */
    @Test
    public void tumbleAggregateFragmentCarriesStrategyChain() {
        XNode stream = compileTransform(
                "SELECT item, sum(amount) AS total FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) "
                        + "GROUP BY item");
        assertNotNull(childByTag(stream, "windowingStrategies"),
                "windowed fragment carries its strategy");
        assertNotNull(childByTag(stream, "aggregators"),
                "windowed fragment carries its aggregator registry");
        assertNull(childByTag(stream, "sink"), "no sink in the fragment");
        assertTrue(hasDescendantTag(stream, "timestampsAndWatermarks"),
                "TUMBLE fragment carries the timestamps node");
    }

    /** UPDATE emit is legal on the windowed path and lands on the strategy. */
    @Test
    public void updateEmitLandsOnWindowedStrategy() {
        XNode stream = XNode.parse(StreamSqlCompiler.compileTransform(null,
                "SELECT item, sum(amount) AS total FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) "
                        + "GROUP BY item",
                ORDERS_SCHEMA, StreamEmitMode.UPDATE, java.util.Collections.emptyMap()));
        XNode strategy = childByTag(stream, "windowingStrategies").getChildren().get(0);
        assertEquals("UPDATE", strategy.getAttr("emit"), "UPDATE emit reaches the strategy");
    }

    /** UPDATE on a plain (no-strategy) path fails fast — no silent drop. */
    @Test
    public void updateOnPlainPathFailsFast() {
        assertThrows(StreamException.class, () -> StreamSqlCompiler.compileTransform(null,
                "SELECT item FROM orders WHERE amount > 5",
                ORDERS_SCHEMA, StreamEmitMode.UPDATE, java.util.Collections.emptyMap()));
    }

    /** JOIN produces two sources — outside the single-entry fragment surface. */
    @Test
    public void joinFragmentFailsFast() {
        StreamException e = assertThrows(StreamException.class, () -> compileTransform(
                "SELECT o.item FROM orders o JOIN orders b ON o.item = b.item"));
        assertTrue(e.getMessage().contains("two-source"), () -> "message: " + e.getMessage());
    }

    /** UNION ALL produces two sources — fail-fast at fragment dispatch. */
    @Test
    public void unionFragmentFailsFast() {
        StreamException e = assertThrows(StreamException.class, () -> compileTransform(
                "SELECT item FROM orders UNION ALL SELECT item FROM orders"));
        assertTrue(e.getMessage().contains("UNION"), () -> "message: " + e.getMessage());
    }

    /** TUMBLE time-column lateness maps onto the fragment strategy. */
    @Test
    public void timeColumnLatenessMapsToStrategy() {
        XNode stream = XNode.parse(StreamSqlCompiler.compileTransform(null,
                "SELECT item, sum(amount) AS total FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) "
                        + "GROUP BY item",
                ORDERS_SCHEMA, StreamEmitMode.CLOSE, Map.of("ts", 1000L)));
        XNode strategy = childByTag(stream, "windowingStrategies").getChildren().get(0);
        assertEquals("1000", String.valueOf(strategy.getAttr("allowedLateness")),
                "TUMBLE time-column lateness becomes the strategy allowedLateness");
    }

    /** Existing v1 subset surfaces still fail fast in fragment mode (no silent pass). */
    @Test
    public void cteAndDistinctStillFailFast() {
        assertThrows(StreamException.class, () -> compileTransform(
                "WITH x AS (SELECT item FROM orders) SELECT item FROM x"));
        assertThrows(StreamException.class, () -> compileTransform(
                "SELECT DISTINCT item FROM orders"));
    }

}
