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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI37 (D11 release): the {@code <sql emit>} declaration is carried into the compiled
 * product's windowing strategy — UPDATE only on the windowed-aggregate (TUMBLE GROUP
 * BY) path; the plain-pipeline (including the plain-TUMBLE variant, which produces no
 * strategy), continuous-aggregate and join paths fail fast instead of silently
 * dropping the declaration (guide #24). The declared surface is the uppercase enum
 * constant (F-1: xdef enum dict matches constant names).
 */
public class TestSqlEmitMode {

    private static final String TUMBLE_AGG =
            "SELECT item, sum(amount) AS total FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) "
                    + "GROUP BY item";
    private static final String PLAIN =
            "SELECT item FROM orders WHERE amount > 0";
    private static final String PLAIN_TUMBLE =
            "SELECT item FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) WHERE amount > 0";
    private static final String CONTINUOUS =
            "SELECT item, sum(amount) AS total FROM orders GROUP BY item";

    static XNode compileNode(String sql, StreamEmitMode emit) {
        return XNode.parse(StreamSqlCompiler.compile(null, sql,
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", emit));
    }

    @Test
    void emitUpdateIsWrittenToProductStrategy() {
        XNode n = compileNode(TUMBLE_AGG, StreamEmitMode.UPDATE);
        XNode strategy = n.childByTag("windowingStrategies").childByTag("strategy");
        assertNotNull(strategy);
        assertEquals("UPDATE", strategy.attrText("emit"),
                "the declared emit mode must reach the product strategy");
    }

    @Test
    void defaultProductCarriesNoEmitAttribute() {
        XNode n = compileNode(TUMBLE_AGG, StreamEmitMode.CLOSE);
        XNode strategy = n.childByTag("windowingStrategies").childByTag("strategy");
        assertNotNull(strategy);
        assertNull(strategy.attrText("emit"),
                "the legacy 4-arg product shape must stay unchanged (CLOSE writes nothing)");
    }

    @Test
    void fourArgCompileOverloadStillProducesLegacyShape() {
        XNode n = XNode.parse(StreamSqlCompiler.compile(null, TUMBLE_AGG,
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink"));
        assertNull(n.childByTag("windowingStrategies").childByTag("strategy").attrText("emit"),
                "the compatibility overload delegates to CLOSE");
    }

    @Test
    void plainTumblePipelineRejectsEmitUpdate() {
        // the plain-TUMBLE variant produces NO strategy — an emit declaration would be
        // silently dropped, so the path must reject it explicitly
        StreamException ex = assertThrows(StreamException.class,
                () -> compileNode(PLAIN_TUMBLE, StreamEmitMode.UPDATE));
        assertTrue(ex.getMessage().contains("emit=update"),
                () -> "expected the emit gate, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("plain pipeline"),
                () -> "gate must name the rejected path: " + ex.getMessage());
    }

    @Test
    void plainPipelineRejectsEmitUpdate() {
        StreamException ex = assertThrows(StreamException.class,
                () -> compileNode(PLAIN, StreamEmitMode.UPDATE));
        assertTrue(ex.getMessage().contains("emit=update"));
    }

    @Test
    void continuousAggregateRejectsEmitUpdate() {
        StreamException ex = assertThrows(StreamException.class,
                () -> compileNode(CONTINUOUS, StreamEmitMode.UPDATE));
        assertTrue(ex.getMessage().contains("continuous aggregate"),
                () -> "gate must name the rejected path: " + ex.getMessage());
    }

    @Test
    void joinQueryRejectsEmitUpdate() {
        String joinSql = "SELECT a.id, b.name FROM orders a JOIN dim b ON a.id = b.id";
        StreamException ex = assertThrows(StreamException.class,
                () -> compileNode(joinSql, StreamEmitMode.UPDATE));
        assertTrue(ex.getMessage().contains("join"),
                () -> "gate must name the rejected path: " + ex.getMessage());
    }

    @Test
    void closeIsLegalOnEveryPath() {
        // CLOSE is the legacy behavior — no gate fires
        assertFalse(XNode.parse(StreamSqlCompiler.compile(null, PLAIN,
                        TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink", StreamEmitMode.CLOSE))
                .childByTag("transforms").getChildren().isEmpty());
    }
}
