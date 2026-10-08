/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile;

import io.nop.core.lang.xml.XNode;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.windowing.StreamEmitMode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI25/Q1: the schema-level lateness annotation (time column, Feldera LATENESS shape
 * subset to the single event-time column) maps onto the product strategy's
 * allowedLateness — the D9 pipeline consumes it with zero runtime change. A lateness
 * on a non-time column, a negative value, or a declaration on a path that produces no
 * window strategy fails fast (no silent drop).
 */
public class TestSqlLatenessDeclaration {

    private static final String TUMBLE_AGG =
            "SELECT item, sum(amount) AS total FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) "
                    + "GROUP BY item";

    private static Map<String, Long> lateness(String column, long millis) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put(column, millis);
        return m;
    }

    @Test
    void timeColumnLatenessIsWrittenToProductStrategy() {
        XNode n = XNode.parse(StreamSqlCompiler.compile(null, TUMBLE_AGG,
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", StreamEmitMode.CLOSE,
                lateness("ts", 20000L)));
        XNode strategy = n.childByTag("windowingStrategies").childByTag("strategy");
        assertEquals("20000", strategy.attrText("allowedLateness"),
                "the time column's lateness must map onto the product strategy (raw long)");
    }

    @Test
    void defaultProductCarriesNoAllowedLatenessAttribute() {
        XNode n = XNode.parse(StreamSqlCompiler.compile(null, TUMBLE_AGG,
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink"));
        assertNull(n.childByTag("windowingStrategies").childByTag("strategy")
                        .attrText("allowedLateness"),
                "the legacy product shape must stay unchanged");
    }

    @Test
    void emitUpdateAndLatenessCombineInProductShape() {
        XNode n = XNode.parse(StreamSqlCompiler.compile(null, TUMBLE_AGG,
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", StreamEmitMode.UPDATE,
                lateness("ts", 20000L)));
        XNode strategy = n.childByTag("windowingStrategies").childByTag("strategy");
        assertEquals("UPDATE", strategy.attrText("emit"));
        assertEquals("20000", strategy.attrText("allowedLateness"),
                "the two declaration forms are orthogonal and both reach the strategy");
    }

    @Test
    void nonTimeColumnLatenessFailsFast() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null, TUMBLE_AGG,
                        TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", StreamEmitMode.CLOSE,
                        lateness("item", 5000L)));
        assertTrue(ex.getMessage().contains("time column"),
                () -> "expected the time-column gate, got: " + ex.getMessage());
    }

    @Test
    void negativeLatenessFailsFast() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null, TUMBLE_AGG,
                        TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", StreamEmitMode.CLOSE,
                        lateness("ts", -1L)));
        assertTrue(ex.getMessage().contains("non-negative"),
                () -> "expected the negative-value gate, got: " + ex.getMessage());
    }

    @Test
    void plainTumblePipelineRejectsLateness() {
        // no window strategy → no carrier → explicit rejection, never a silent drop
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null,
                        "SELECT item FROM TUMBLE(orders.ts, INTERVAL 5 SECOND)",
                        TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", StreamEmitMode.CLOSE,
                        lateness("ts", 20000L)));
        assertTrue(ex.getMessage().contains("plain pipeline"),
                () -> "gate must name the rejected path: " + ex.getMessage());
    }

    @Test
    void continuousAggregateRejectsLateness() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null,
                        "SELECT item, sum(amount) AS total FROM orders GROUP BY item",
                        TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", StreamEmitMode.CLOSE,
                        lateness("ts", 20000L)));
        assertTrue(ex.getMessage().contains("continuous aggregate"));
    }

    @Test
    void joinQueryRejectsLateness() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null,
                        "SELECT a.id, b.name FROM orders a JOIN dim b ON a.id = b.id",
                        TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", StreamEmitMode.CLOSE,
                        lateness("ts", 20000L)));
        assertTrue(ex.getMessage().contains("join"));
    }
}
