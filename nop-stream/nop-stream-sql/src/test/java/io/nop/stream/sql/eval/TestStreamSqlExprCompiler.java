/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.sql.eval.StreamRecordEvaluator;
import io.nop.stream.sql.eval.StreamSqlExprCompiler;
import io.nop.stream.sql.parse.EqlExprSupport;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9: EQL text → AST → scalar stream evaluator over record objects. The supported
 * subset compiles without ever surfacing the EQL-side ERR_EQL_UNSUPPORTED_EVAL_EXPR;
 * out-of-subset constructs fail fast with the stream-side invalid-arg code.
 */
public class TestStreamSqlExprCompiler {

    private static StreamRecordEvaluator compile(String expr) {
        return StreamSqlExprCompiler.compileScalar(EqlExprSupport.parseExpr(expr));
    }

    private static Object eval(String expr, Map<String, Object> record) {
        return compile(expr).eval(record);
    }

    private static Map<String, Object> rec(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void columnAccessOnMapRecords() {
        assertEquals("x", eval("name", rec("name", "x")));
        assertEquals(3, eval("t.amount", rec("amount", 3)), "qualified name uses the last segment as the column key");
    }

    @Test
    public void missingColumnFailsFast() {
        StreamException e = assertThrows(StreamException.class, () -> eval("nope", rec("a", 1)));
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode());
    }

    @Test
    public void literals() {
        assertEquals(123L, eval("123", rec()));
        assertEquals(12.5, eval("12.5", rec()));
        assertEquals("str", eval("'str'", rec()));
        assertEquals(Boolean.TRUE, eval("true", rec()));
        assertNull(eval("null", rec()));
    }

    @Test
    public void arithmeticWithNullPropagationAndPromotion() {
        assertEquals(7L, eval("a + b * 2", rec("a", 3, "b", 2)), "multiplication binds tighter");
        assertEquals(3L, eval("a - 1", rec("a", 4)));
        assertEquals(6.0, eval("a * 1.5", rec("a", 4)), "float operand promotes to double");
        assertNull(eval("a + 1", rec("a", null)), "arithmetic with null operand yields null");
        assertEquals(2L, eval("a % 3", rec("a", 5)));
    }

    @Test
    public void divisionIsAlwaysDouble() {
        assertEquals(2.5, eval("a / 2", rec("a", 5)),
                "D7 has no DECIMAL type — division is double division, integer division is out of scope");
    }

    @Test
    public void comparisons() {
        assertEquals(Boolean.TRUE, eval("a >= 3", rec("a", 3)));
        assertEquals(Boolean.FALSE, eval("a <> 3", rec("a", 3)));
        assertEquals(Boolean.TRUE, eval("a != 3", rec("a", 4)), "!= parses as NE");
        assertEquals(Boolean.TRUE, eval("s < 'b'", rec("s", "a")), "string comparison uses natural order");
        assertNull(eval("a = null", rec("a", 1)), "null comparison propagates null (no three-valued true)");
    }

    @Test
    public void logicalOperatorsShortCircuit() {
        assertEquals(Boolean.TRUE, eval("a > 1 and b > 1", rec("a", 2, "b", 2)));
        assertEquals(Boolean.FALSE, eval("a > 1 and b > 1", rec("a", 2, "b", 0)));
        assertEquals(Boolean.TRUE, eval("a > 1 or b > 1", rec("a", 0, "b", 2)));
        assertEquals(Boolean.FALSE, eval("not a > 1", rec("a", 2)));
        assertEquals(Boolean.TRUE, eval("a > 1 or b > 1", rec("a", 2, "b", null)),
                "null right operand does not mask a true left operand (short-circuit)");
    }

    @Test
    public void isNullForms() {
        assertEquals(Boolean.TRUE, eval("a is null", rec("a", null)));
        assertEquals(Boolean.TRUE, eval("a is not null", rec("a", 1)));
        assertEquals(Boolean.FALSE, eval("a is null", rec("a", 1)));
    }

    @Test
    public void betweenAndIn() {
        assertEquals(Boolean.TRUE, eval("a between 1 and 5", rec("a", 3)));
        assertEquals(Boolean.FALSE, eval("a between 1 and 5", rec("a", 6)));
        assertEquals(Boolean.TRUE, eval("a not between 1 and 5", rec("a", 6)));
        assertEquals(Boolean.TRUE, eval("a in (1, 2, 3)", rec("a", 2)));
        assertEquals(Boolean.FALSE, eval("a in (1, 2, 3)", rec("a", 9)));
        assertEquals(Boolean.TRUE, eval("a not in (1, 2, 3)", rec("a", 9)));
    }

    @Test
    public void beanShapeRecordAccess() {
        Bean bean = new Bean();
        bean.setName("n1");
        assertEquals("n1", compile("name").eval(bean), "bean records resolve via property getters");
    }

    @Test
    public void outOfSubsetFailsFastWithStreamSideCode() {
        // regular function call
        StreamException e1 = assertThrows(StreamException.class, () -> eval("concat(a, b)", rec("a", 1, "b", 2)));
        assertEquals("nop.err.stream.invalid-arg", e1.getErrorCode());
        // CASE expression
        assertThrows(StreamException.class, () -> eval("case when a > 1 then 1 else 2 end", rec("a", 2)));
        // CAST expression
        assertThrows(StreamException.class, () -> eval("cast(a as varchar)", rec("a", 2)));
        // aggregate in scalar position
        assertThrows(StreamException.class, () -> eval("sum(a) + 1", rec("a", 2)));
        // null AST
        assertThrows(StreamException.class,
                () -> StreamSqlExprCompiler.compileScalar((io.nop.orm.eql.ast.SqlExpr) null));
    }

    @Test
    public void resolveTypeUsesD7ColumnMapping() {
        Map<String, BasicTypeInfo<?>> columns = new HashMap<>();
        columns.put("name", BasicTypeInfo.STRING);
        columns.put("amount", BasicTypeInfo.INT);

        assertEquals(BasicTypeInfo.STRING, StreamSqlExprCompiler.resolveType(
                EqlExprSupport.parseExpr("name"), columns::get));
        assertEquals(BasicTypeInfo.LONG, StreamSqlExprCompiler.resolveType(
                EqlExprSupport.parseExpr("amount * 2"), columns::get), "int arithmetic promotes to long");
        assertEquals(BasicTypeInfo.LONG, StreamSqlExprCompiler.resolveType(
                EqlExprSupport.parseExpr("count(amount)"), columns::get));
        assertEquals(BasicTypeInfo.DOUBLE, StreamSqlExprCompiler.resolveType(
                EqlExprSupport.parseExpr("avg(amount)"), columns::get));
        assertEquals(BasicTypeInfo.INT, StreamSqlExprCompiler.resolveType(
                EqlExprSupport.parseExpr("max(amount)"), columns::get));
    }

    @Test
    public void evaluatorsAreSerializable() throws Exception {
        byte[] bytes;
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos)) {
            oos.writeObject(compile("a + 1"));
            bytes = baos.toByteArray();
        }
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes))) {
            StreamRecordEvaluator copy = (StreamRecordEvaluator) ois.readObject();
            assertEquals(4L, copy.eval(rec("a", 3)), "evaluator must survive Java serialization");
        }
    }

    public static class Bean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
