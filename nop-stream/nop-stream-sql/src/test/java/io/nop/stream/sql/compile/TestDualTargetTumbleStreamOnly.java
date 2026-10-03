/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile;

import io.nop.core.lang.xml.XNode;
import io.nop.orm.eql.ast.SqlProgram;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI20: the TUMBLE (W2) query's RDBMS-target boundary — per §4c the windowed
 * form is <b>T1 stream-only</b>: it compiles and executes on the nop-stream
 * target (proven by WI17's {@code TestTumbleAggregateQueryE2E}), while the
 * RDBMS target has no native equivalent (external fact, not verified in this
 * repository) — the ORM channel fails fast with table-source-not-resolved
 * (WI17's round-trip asymmetry, in case). This test pins the boundary: stream
 * target compiles; RDBMS channel refuses — the documented W2 semantic-boundary
 * annotation, not a defect.
 *
 * <p>D15 scope: only H2 is the default physical target; a TUMBLE query would
 * need T2 per-dialect approximation mapping, which is out of WI20 scope.
 */
public class TestDualTargetTumbleStreamOnly {

    private static final String QUERY =
            "SELECT item, sum(amount) AS total FROM "
                    + "TUMBLE(orders.ts, INTERVAL 5 SECOND) WHERE amount > 0 GROUP BY item";

    private static Map<String, String> schema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("ts", "bigint");
        schema.put("item", "string");
        schema.put("amount", "int");
        return schema;
    }

    @Test
    public void streamTargetCompilesTheTumbleQuery() {
        String xml = StreamSqlCompiler.compile(null, QUERY, schema(), "testSink");
        XNode node = XNode.parse(xml);
        // the compiled product carries the parameterized window declaration
        assertTrue(xml.contains("tumbling-event-time"),
                () -> "the compiled product must declare the tumbling window: " + xml);
        assertTrue(xml.contains("timestampsAndWatermarks"),
                () -> "the compiled product must assign event time from the t column: " + xml);
        assertEquals("sql-compiled", node.attrText("name"));
    }

    @Test
    public void rdbmsChannelRendersTumbleLoudlyNotSilently() {
        // W2/T1 boundary: the SQL channel renders the pseudo-table function
        // VERBATIM (WI20 printing backfill) — the text is deliberately not valid
        // RDBMS SQL, so any attempt to run it on a real database fails loudly at
        // parse time instead of silently querying the wrong rows. The semantic
        // RESOLUTION still refuses (table-source-not-resolved, WI17).
        SqlProgram program = new io.nop.orm.eql.parse.EqlASTParser().parseFromText(null, QUERY);
        String sql = program.toSQL().getText();
        assertTrue(sql.contains("TUMBLE(orders.ts"),
                () -> "the SQL channel must render the TUMBLE pseudo-table loudly: " + sql);
        assertTrue(sql.contains("INTERVAL"),
                () -> "the SQL channel must render the interval: " + sql);
        // and the semantic resolution still refuses (stream-only contract):
        // the from-clause's table source resolve accessors throw
        io.nop.orm.eql.ast.SqlQuerySelect select =
                (io.nop.orm.eql.ast.SqlQuerySelect) program.getStatements().get(0);
        io.nop.orm.eql.ast.SqlTumbleTableSource table =
                (io.nop.orm.eql.ast.SqlTumbleTableSource) select.getFrom()
                        .getTableSources().get(0);
        assertThrows(io.nop.api.core.exceptions.NopException.class,
                table::getSourceSelect,
                "ORM-channel semantic resolution must still refuse the TUMBLE source");
        assertThrows(io.nop.api.core.exceptions.NopException.class,
                table::getResolvedTableMeta,
                "ORM-channel meta resolution must still refuse the TUMBLE source");
    }
}
