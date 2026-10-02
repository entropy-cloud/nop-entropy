/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.xml.XNode;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamMapModel;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.sql.eval.StreamRecordEvaluator;
import io.nop.stream.sql.eval.StreamSqlExprCompiler;
import io.nop.stream.sql.parse.EqlExprSupport;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI17 Phase 2: StreamSqlCompiler product-structure assertions, the fail-fast matrix
 * (M4), and the WI9 semantic-equivalence pin (M2 — the same record matrix through the
 * compiled product's xpl body and the WI9 evaluator: null propagation, double division,
 * three-valued logic). End-to-end build/execute closures live in the dedicated E2E
 * classes (local runner: one execute per test class).
 *
 * <p>Parsed AST structure assertions only — toSQL() is never used as an oracle
 * (round-trip asymmetry, WI17 Baseline).
 */
public class TestStreamSqlCompiler {

    @org.junit.jupiter.api.BeforeAll
    public static void init() {
        io.nop.core.initialize.CoreInitialization.initializeTo(
                io.nop.core.CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
    }

    @org.junit.jupiter.api.AfterAll
    public static void destroy() {
        io.nop.core.initialize.CoreInitialization.destroy();
    }

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

    static String compile(String sql) {
        return StreamSqlCompiler.compile(null, sql, ORDERS_SCHEMA, "testSink");
    }

    static XNode compileNode(String sql) {
        return XNode.parse(compile(sql));
    }

    private static XNode childByTag(XNode node, String tag) {
        for (XNode c : node.getChildren()) {
            if (c.getTagName().equals(tag))
                return c;
        }
        return null;
    }

    private static XNode child(XNode node, String tag) {
        for (XNode c : node.getChildren()) {
            if (c.getTagName().equals(tag))
                return c;
        }
        throw new AssertionError("missing child <" + tag + "> under <" + node.getTagName() + ">");
    }

    private static XNode transform(XNode node, String id) {
        for (XNode t : child(node, "transforms").getChildren()) {
            if (id.equals(t.attrText("id")))
                return t;
        }
        throw new AssertionError("missing transform id=" + id);
    }

    private static XNode edge(XNode node, String id) {
        for (XNode e : child(node, "edges").getChildren()) {
            if (id.equals(e.attrText("id")))
                return e;
        }
        throw new AssertionError("missing edge id=" + id);
    }

    // ----------------------------------------------------------------
    // 纳入面产物结构断言
    // ----------------------------------------------------------------

    @Test
    public void tumbleWindowedAggregateProductShape() {
        XNode n = compileNode(
                "SELECT item, sum(amount) AS total FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) "
                        + "GROUP BY item");
        // FROM 表名即 bean 名 (B2)
        assertEquals("orders", transform(n, "ssrc").attrText("bean"));
        // TUMBLE 产物含 timestampsAndWatermarks + inline assigner (M1)
        XNode tsw = transform(n, "stsw");
        assertEquals("ts", tsw.childByTag("timestampAssigner").contentText().trim()
                .replace("return event['", "").replace("'];", ""));
        assertTrue(tsw.childByTag("watermarkGenerator").contentText().contains("emitWatermark"),
                "per-event watermark generator");
        // keyBy(groupKeys) + window(duration) + aggregate(合成复合条目) (B3/B1)
        assertEquals("event['item']", transform(n, "skey").attrText("keyExpr"));
        XNode window = transform(n, "swin");
        assertEquals("ws0", window.attrText("strategyRef"));
        XNode strategy = child(n, "windowingStrategies").childByTag("strategy");
        assertEquals("tumbling-event-time", strategy.attrText("windowFnId"));
        assertEquals("5000ms", strategy.attrText("duration"));
        XNode aggregate = transform(n, "sagg");
        assertEquals("aggr0", aggregate.attrText("aggregatorRef"));
        XNode aggEntry = child(n, "aggregators").childByTag("aggregator");
        assertEquals("sql-row-agg", aggEntry.attrText("fnId"));
        assertEquals("sqlschema0", aggEntry.attrText("schemaId"));
        // expr 携带结构化 spec：组键在前、聚合按 SELECT 序 (B1)
        assertTrue(aggEntry.attrText("expr").contains("\"keys\":[\"item\"]"),
                () -> "spec keys: " + aggEntry.attrText("expr"));
        assertTrue(aggEntry.attrText("expr").contains("\"fn\":\"sum\"")
                        && aggEntry.attrText("expr").contains("\"expr\":\"amount\""),
                () -> "spec aggs: " + aggEntry.attrText("expr"));
        // schema 内嵌声明 (D7)
        assertEquals("bigint", child(n, "schemas").childByTag("schema").childByTag("fields")
                .getChildren().get(0).attrText("type"));
        // 投影末态 map 引用聚合行槽位：组键在前、聚合按 SELECT 序
        String proj = transform(n, "sproj").childByTag("source").contentText();
        assertTrue(proj.contains("\"item\": event[0]") && proj.contains("\"total\": event[1]"),
                () -> "projection body: " + proj);
        // sinkBean 派生 sink + 边 (B4 产物自洽)
        assertEquals("testSink", transform(n, "out").attrText("bean"));
        assertEquals("sproj", edge(n, "e_out").attrText("from"));
        assertEquals("out", edge(n, "e_out").attrText("to"));
    }

    @Test
    public void continuousAggregateProductShape() {
        XNode n = compileNode(
                "SELECT item, count(*) AS c FROM orders WHERE amount > 0 GROUP BY item");
        assertEquals("orders", transform(n, "ssrc").attrText("bean"));
        // 无 TUMBLE：无 timestampsAndWatermarks
        for (XNode t : child(n, "transforms").getChildren()) {
            assertTrue(!"timestampsAndWatermarks".equals(t.getTagName()),
                    "continuous pipeline carries no event-time assigner");
        }
        // filter 先行
        assertEquals("return (((event['amount']) == null || (0) == null) ? null : "
                        + "((event['amount']) > (0)));",
                transform(n, "sflt").childByTag("source").contentText().trim());
        // map(记录→累加器行) + keyBy(累加器行首) + reduce(合并) + map(行) (B3)
        String begin = transform(n, "sbeg").childByTag("source").contentText();
        assertTrue(begin.contains("SqlRowAggregateOps.begin(") && begin.contains("keys"),
                () -> "begin body: " + begin);
        assertEquals("event[0][0]", transform(n, "skey").attrText("keyExpr"),
                "keyBy reads the accumulator keys-array head");
        String reduce = transform(n, "sred").childByTag("source").contentText();
        assertTrue(reduce.contains("SqlRowAggregateOps.merge("), () -> "reduce body: " + reduce);
        String row = transform(n, "srow").childByTag("source").contentText();
        assertTrue(row.contains("SqlRowAggregateOps.toRow("), () -> "row body: " + row);
        // 持续聚合管线无 <aggregate> 节点：spec 直接内嵌于 begin/merge/toRow xpl 体；
        // 累加语义单源于 SqlRowCompositeFunction（B1 扩展 resolver 供窗口路径使用）
        for (XNode t : child(n, "transforms").getChildren()) {
            assertTrue(!"aggregate".equals(t.getTagName()),
                    "continuous pipeline synthesizes no <aggregate> node");
        }
    }

    @Test
    public void plainPipelineWithTumbleProductShape() {
        XNode n = compileNode(
                "SELECT item FROM TUMBLE(orders.ts, INTERVAL 1 MINUTE) WHERE amount >= 1");
        assertEquals("orders", transform(n, "ssrc").attrText("bean"));
        // 无聚合的 TUMBLE 管线没有 window 节点——不合成 windowingStrategies
        assertNull(childByTag(n, "windowingStrategies"), "no window node, no strategy registry");
        assertNotNull(transform(n, "stsw"), "event-time assigner present");
        assertNotNull(transform(n, "sflt"), "filter present");
        String proj = transform(n, "sproj").childByTag("source").contentText();
        assertTrue(proj.contains("\"item\": event['item']"), () -> "projection body: " + proj);
        // 无聚合：无 aggregators 注册表
        for (XNode c : n.getChildren()) {
            assertTrue(!"aggregators".equals(c.getTagName()),
                    "plain pipeline synthesizes no aggregator entry");
        }
    }

    @Test
    public void joinProductShape() {
        XNode n = XNode.parse(StreamSqlCompiler.compile(null,
                "SELECT o.item AS i, u.name AS n FROM orders o JOIN items u "
                        + "ON o.item = u.name",
                schema(entry("item", "string"), entry("name", "string"),
                        entry("id", "int"), entry("ts", "bigint")),
                "testSink"));
        assertEquals("orders", transform(n, "slsrc").attrText("bean"));
        assertEquals("items", transform(n, "srsrc").attrText("bean"));
        assertEquals("jspec0", transform(n, "sjoin").attrText("joinRef"));
        XNode spec = child(n, "joins").childByTag("joinSpec");
        assertEquals("INNER", spec.attrText("joinType"));
        assertEquals("event['item']", spec.attrText("leftKeyExprs"));
        assertEquals("event['name']", spec.attrText("rightKeyExprs"));
        String proj = transform(n, "sproj").childByTag("source").contentText();
        assertTrue(proj.contains("\"i\": event.left['item']"), () -> "join projection: " + proj);
        assertTrue(proj.contains("\"n\": event.right['name']"), () -> "join projection: " + proj);
    }

    @Test
    public void joinReversedOnDecomposesByScopeNotPosition() {
        // MIN-3 pin (wi17 audit): the ON condition is decomposed by table-alias
        // SCOPE, not operand position — a reversed form (right alias on the left
        // operand) must still map each key expr to its own side
        XNode n = XNode.parse(StreamSqlCompiler.compile(null,
                "SELECT o.item AS i, u.name AS n FROM orders o JOIN items u "
                        + "ON u.name = o.item",
                schema(entry("item", "string"), entry("name", "string")),
                "testSink"));
        XNode spec = child(n, "joins").childByTag("joinSpec");
        assertEquals("event['item']", spec.attrText("leftKeyExprs"),
                "left key must come from the orders (o) alias regardless of ON operand order");
        assertEquals("event['name']", spec.attrText("rightKeyExprs"),
                "right key must come from the items (u) alias regardless of ON operand order");
    }

    @Test
    public void unionAllBagProductShape() {
        XNode n = compileNode(
                "SELECT item FROM orders WHERE amount > 0 UNION ALL "
                        + "SELECT item FROM orders WHERE amount < 0");
        assertNotNull(transform(n, "s0proj"), "left side pipeline");
        assertNotNull(transform(n, "s1proj"), "right side pipeline");
        assertNotNull(transform(n, "su"), "union node");
        assertEquals("s0proj", edge(n, "se0").attrText("from"));
        assertEquals("s1proj", edge(n, "se1").attrText("from"));
        assertEquals("su", edge(n, "e_out").attrText("from"));
    }

    // ----------------------------------------------------------------
    // fail-fast 矩阵（M4：§4a 九项在编译器射程内的钉码 + default-reject）
    // ----------------------------------------------------------------

    private static void assertInvalidArg(String sql) {
        StreamException e = assertThrows(StreamException.class, () -> compile(sql));
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode().toString());
    }

    private static void assertDialectFeature(String sql, String feature) {
        NopException e = assertThrows(NopException.class, () -> compile(sql));
        assertEquals("nop.err.eql.dialect-not-support-feature", e.getErrorCode().toString());
        assertTrue(String.valueOf(e.getParam("feature")).contains(feature),
                () -> "feature must name " + feature + ": " + e.getMessage());
    }

    @Test
    public void orderByFailsFastWithDialectCode() {
        assertDialectFeature("SELECT item FROM orders ORDER BY item", "ORDER BY");
    }

    @Test
    public void limitFailsFastWithDialectCode() {
        assertDialectFeature("SELECT item FROM orders LIMIT 5", "LIMIT");
    }

    @Test
    public void havingFailsFast() {
        assertInvalidArg("SELECT item, sum(amount) AS t FROM orders GROUP BY item "
                + "HAVING sum(amount) > 0");
    }

    @Test
    public void cteFailsFast() {
        assertInvalidArg("WITH c AS (SELECT item FROM orders) SELECT item FROM c");
    }

    @Test
    public void distinctSelectFailsFast() {
        assertInvalidArg("SELECT DISTINCT item FROM orders");
    }

    @Test
    public void intersectAndExceptFailFast() {
        assertInvalidArg("SELECT item FROM orders INTERSECT SELECT item FROM orders");
        assertInvalidArg("SELECT item FROM orders EXCEPT SELECT item FROM orders");
    }

    @Test
    public void unionDistinctFailsFastBagUnionOnly() {
        assertInvalidArg("SELECT item FROM orders UNION SELECT item FROM orders");
    }

    @Test
    public void subqueryAndLateralTableSourceFailFast() {
        assertInvalidArg("SELECT item FROM (SELECT item FROM orders) x");
        assertInvalidArg("SELECT o.item FROM orders o JOIN LATERAL (SELECT name FROM items) u "
                + "ON o.item = u.name");
    }

    @Test
    public void globalAggregationFailsFast() {
        assertInvalidArg("SELECT sum(amount) AS t FROM orders");
    }

    @Test
    public void groupByWithoutAggregateFailsFast() {
        assertInvalidArg("SELECT item FROM orders GROUP BY item");
    }

    @Test
    public void selectStarFailsFast() {
        assertInvalidArg("SELECT * FROM orders");
    }

    @Test
    public void distinctAggregateFailsFast() {
        assertInvalidArg("SELECT item, count(DISTINCT amount) AS c FROM orders GROUP BY item");
    }

    @Test
    public void aggregateInWhereFailsFast() {
        assertInvalidArg("SELECT item FROM orders WHERE sum(amount) > 0 GROUP BY item");
    }

    @Test
    public void nonGroupedColumnFailsFast() {
        assertInvalidArg("SELECT amount, sum(amount) AS t FROM orders GROUP BY item");
    }

    @Test
    public void nonEquiJoinFailsFast() {
        assertInvalidArg("SELECT o.item AS i FROM orders o JOIN items u ON o.item < u.name");
    }

    @Test
    public void unqualifiedJoinColumnFailsFast() {
        assertInvalidArg("SELECT item AS i FROM orders JOIN items u ON o.item = u.name");
    }

    @Test
    public void tumbleOverJoinFailsFast() {
        assertInvalidArg("SELECT o.item AS i FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) o "
                + "JOIN items u ON o.item = u.name");
    }

    @Test
    public void aggregateOverJoinFailsFast() {
        assertInvalidArg("SELECT o.item AS i, count(*) AS c FROM orders o JOIN items u "
                + "ON o.item = u.name GROUP BY o.item");
    }

    @Test
    public void multiStatementFailsFast() {
        StreamException e = assertThrows(StreamException.class,
                () -> compile("SELECT item FROM orders; SELECT item FROM orders"));
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode().toString());
    }

    @Test
    public void unmanagedSchemaTypeFailsFast() {
        StreamException e = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null, "SELECT item FROM orders",
                        schema(entry("amount", "decimal")), "testSink"));
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode().toString());
        assertTrue(e.getMessage().contains("decimal"), () -> e.getMessage());
    }

    @Test
    public void blankSqlAndBlankSinkFailFast() {
        assertInvalidArg("   ");
        StreamException e = assertThrows(StreamException.class,
                () -> StreamSqlCompiler.compile(null, "SELECT item FROM orders", ORDERS_SCHEMA, " "));
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode().toString());
    }

    @Test
    public void nonPositiveTumbleIntervalFailsFast() {
        // eql 侧 INTERVAL 提取 fail-fast（Phase 1 错误码，编译期直接穿透）
        NopException e = assertThrows(NopException.class,
                () -> compile("SELECT item, sum(amount) AS t FROM TUMBLE(orders.ts, INTERVAL 0 SECOND) GROUP BY item"));
        assertEquals("nop.err.eql.invalid-interval-value", e.getErrorCode().toString());
    }

    @Test
    public void outOfSubsetExpressionFailsFastWithStreamCode() {
        // CASE 在 WI9 标量子集外——编译期走 default-reject（零 EQL 错误码泄漏）
        assertInvalidArg("SELECT case when amount > 0 then 1 else 2 end AS x FROM orders");
        assertInvalidArg("SELECT cast(amount as bigint) AS x FROM orders");
    }

    // ----------------------------------------------------------------
    // 语义等价钉子（M2）：产物 xpl body vs WI9 evaluator 同一记录矩阵对拍
    // ----------------------------------------------------------------

    private static Map<String, Object> rec(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static List<Map<String, Object>> MATRIX = java.util.Arrays.asList(
            rec("amount", 1, "item", "a"),
            rec("amount", null, "item", "a"),
            rec("amount", -3, "item", null),
            rec("amount", 5, "item", "b"),
            rec("amount", null, "item", "a"));

    /**
     * Compiles one query whose WHERE is {@code expr}, parses the product back, and
     * evaluates the product's filter body over the record matrix. The WI9 evaluator
     * compiles the same expression text. Every (record, engine) result pair must be
     * EQUAL — including null (three-valued) results.
     */
    private void assertFilterEquivalence(String expr) throws Exception {
        String xml = compile("SELECT item FROM orders WHERE " + expr);
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        IEvalFunction body = null;
        for (io.nop.stream.flow.model.StreamTransformModel t : model.getTransforms()) {
            if (t instanceof io.nop.stream.flow.model.StreamFilterModel) {
                body = ((io.nop.stream.flow.model.StreamFilterModel) t).getSource();
            }
        }
        assertNotNull(body, "product must carry a filter transform for WHERE");
        StreamRecordEvaluator evaluator = StreamSqlExprCompiler.compileScalar(
                EqlExprSupport.parseExpr(expr));
        for (Map<String, Object> record : MATRIX) {
            Object productResult = body.call1(null, record, null);
            Object evaluatorResult = evaluator.eval(record);
            if (productResult == null || evaluatorResult == null) {
                assertNull(productResult, () -> expr + " on " + record
                        + ": product must propagate null like the WI9 evaluator");
                assertNull(evaluatorResult, () -> expr + " on " + record
                        + ": evaluator must propagate null like the product");
            } else {
                assertEquals(evaluatorResult, productResult,
                        () -> expr + " on " + record);
            }
        }
    }

    @Test
    public void filterEquivalenceNullPropagationAndThreeValuedLogic() throws Exception {
        assertFilterEquivalence("amount > 0");
        assertFilterEquivalence("amount + 1 > 0");
        assertFilterEquivalence("amount / 2 > 1");
        assertFilterEquivalence("NOT (amount > 0)");
        assertFilterEquivalence("amount > 0 OR item = 'a'");
        assertFilterEquivalence("amount > 0 AND item = 'a'");
        assertFilterEquivalence("item IS NULL");
        assertFilterEquivalence("item IS NOT NULL");
        assertFilterEquivalence("amount BETWEEN 1 AND 3");
        assertFilterEquivalence("amount NOT BETWEEN 1 AND 3");
        assertFilterEquivalence("amount IN (1, 2)");
        assertFilterEquivalence("amount IN (1, NULL)");
        assertFilterEquivalence("amount NOT IN (1, 2)");
        assertFilterEquivalence("-amount > 0");
    }

    /**
     * Double-division pin: the WI9 rule "除法恒 double" must hold through the product
     * xpl (XLang native division is double — verified, not assumed).
     */
    @Test
    public void divisionIsDoubleThroughProductBody() throws Exception {
        String xml = compile("SELECT amount / 2 AS d FROM orders WHERE item = 'a'");
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        IEvalFunction body = null;
        for (io.nop.stream.flow.model.StreamTransformModel t : model.getTransforms()) {
            if (t instanceof io.nop.stream.flow.model.StreamMapModel
                    && "sproj".equals(t.getId())) {
                body = ((io.nop.stream.flow.model.StreamMapModel) t).getSource();
            }
        }
        assertNotNull(body, "projection map body must exist");
        Object result = body.call1(null, rec("amount", 5, "item", "a"), null);
        assertTrue(result instanceof Map, () -> "row carrier is a Map: " + result);
        Object d = ((Map<?, ?>) result).get("d");
        Object expected = StreamSqlExprCompiler.compileScalar(
                EqlExprSupport.parseExpr("amount / 2")).eval(rec("amount", 5));
        assertEquals(2.5, expected, "WI9 evaluator divides as double");
        assertEquals(expected, d, () -> "product xpl division must equal the WI9 evaluator");
    }
}
