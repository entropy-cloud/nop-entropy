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

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI26: HOP/SESSION 伪表函数的编译产物 shape 与 no-silent 门——strategy windowFnId
 * 泛化（sliding-event-time/session-event-time）、HOP slide 属性、slide<=size 双守卫、
 * plain 路径拒绝、window_start/window_end 保留名 typed 拒绝（FU-E2 命名契约）、
 * WI25 lateness 推广到伪表函数时间列。
 */
public class TestSqlHopSessionDeclaration {

    private static final String HOP_AGG =
            "SELECT item, sum(amount) AS total FROM "
                    + "HOP(orders.ts, INTERVAL 2 SECOND, INTERVAL 4 SECOND) GROUP BY item";
    private static final String SESSION_AGG =
            "SELECT item, sum(amount) AS total FROM "
                    + "SESSION(orders.ts, INTERVAL 5 SECOND) GROUP BY item";

    private static XNode strategyNode(String sql) {
        XNode n = XNode.parse(StreamSqlCompiler.compile(null, sql,
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink"));
        return n.childByTag("windowingStrategies").childByTag("strategy");
    }

    @Test
    void hopProductCarriesSlidingStrategyWithSlide() {
        XNode strategy = strategyNode(HOP_AGG);
        assertEquals("sliding-event-time", strategy.attrText("windowFnId"));
        assertEquals("4000ms", strategy.attrText("duration"));
        assertEquals("2000ms", strategy.attrText("slide"));
    }

    @Test
    void sessionProductCarriesSessionStrategyWithGapDuration() {
        XNode strategy = strategyNode(SESSION_AGG);
        assertEquals("session-event-time", strategy.attrText("windowFnId"));
        assertEquals("5000ms", strategy.attrText("duration"));
        assertNull(strategy.attrText("slide"), "session windows carry no slide");
    }

    @Test
    void hopSlideLargerThanSizeFailsFastAtSqlLayer() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null,
                        "SELECT item, sum(amount) AS total FROM "
                                + "HOP(orders.ts, INTERVAL 5 SECOND, INTERVAL 4 SECOND) GROUP BY item",
                        TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink"));
        assertTrue(ex.getMessage().contains("slide"),
                () -> "expected the slide<=size gate, got: " + ex.getMessage());
    }

    @Test
    void plainHopQueryFailsFast() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null,
                        "SELECT item FROM HOP(orders.ts, INTERVAL 2 SECOND, INTERVAL 4 SECOND)",
                        TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink"));
        assertTrue(ex.getMessage().contains("windowed aggregate"),
                () -> "expected the plain-path gate, got: " + ex.getMessage());
    }

    @Test
    void plainSessionQueryFailsFast() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null,
                        "SELECT item FROM SESSION(orders.ts, INTERVAL 5 SECOND)",
                        TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink"));
        assertTrue(ex.getMessage().contains("session window table source"),
                () -> "expected the plain-path gate, got: " + ex.getMessage());
    }

    @Test
    void windowStartReservedNameIsTypedRejected() {
        // 命名契约钉码：FU-E2 指向而非未知列错误
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null,
                        "SELECT window_start, item, sum(amount) AS total FROM "
                                + "TUMBLE(orders.ts, INTERVAL 5 SECOND) GROUP BY item",
                        TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink"));
        assertTrue(ex.getMessage().contains("FU-E2"),
                () -> "expected the FU-E2 naming-contract error, got: " + ex.getMessage());
    }

    @Test
    void windowEndReservedNameIsTypedRejected() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null,
                        "SELECT item, sum(amount) AS total FROM "
                                + "SESSION(orders.ts, INTERVAL 5 SECOND) GROUP BY item, window_end",
                        TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink"));
        assertTrue(ex.getMessage().contains("FU-E2"));
    }

    @Test
    void latenessGeneralizesToPseudoTableTimeColumns() {
        // WI25 lateness 推广：HOP/SESSION 时间列同样映射 allowedLateness
        XNode hop = XNode.parse(StreamSqlCompiler.compile(null, HOP_AGG,
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", StreamEmitMode.CLOSE,
                Collections.singletonMap("ts", 20000L)));
        assertEquals("20000", hop.childByTag("windowingStrategies").childByTag("strategy")
                .attrText("allowedLateness"));
        XNode session = XNode.parse(StreamSqlCompiler.compile(null, SESSION_AGG,
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", StreamEmitMode.CLOSE,
                Collections.singletonMap("ts", 20000L)));
        assertEquals("20000", session.childByTag("windowingStrategies").childByTag("strategy")
                .attrText("allowedLateness"));
    }

    @Test
    void hopNonTimeColumnLatenessFailsFast() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null, HOP_AGG,
                        TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", StreamEmitMode.CLOSE,
                        Collections.singletonMap("item", 5000L)));
        assertTrue(ex.getMessage().contains("pseudo-table time column"),
                () -> "expected the generalized time-column gate, got: " + ex.getMessage());
    }
}
