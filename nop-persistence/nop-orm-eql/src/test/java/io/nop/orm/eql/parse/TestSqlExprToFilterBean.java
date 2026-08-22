package io.nop.orm.eql.parse;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.query.FilterBeanFormatter;
import io.nop.orm.eql.eval.SqlExprTransformHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestSqlExprToFilterBean {
    @Test
    public void testTransform() {
        String sql = "name = '1' or ( x = 1 and y.status > 3) ";
        TreeBean filter = SqlExprTransformHelper.parseSqlToFilter(null, sql);
        System.out.println(JsonTool.serialize(filter, true));
        assertEquals("{\n" +
                "  \"$body\": [\n" +
                "    {\n" +
                "      \"$type\": \"eq\",\n" +
                "      \"name\": \"name\",\n" +
                "      \"value\": \"1\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"$body\": [\n" +
                "        {\n" +
                "          \"$type\": \"eq\",\n" +
                "          \"name\": \"x\",\n" +
                "          \"value\": 1\n" +
                "        },\n" +
                "        {\n" +
                "          \"$type\": \"gt\",\n" +
                "          \"name\": \"y.status\",\n" +
                "          \"value\": 3\n" +
                "        }\n" +
                "      ],\n" +
                "      \"$loc\": \"[1:16:0:0]<unknown>\",\n" +
                "      \"$type\": \"and\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"$loc\": \"[1:0:0:0]<unknown>\",\n" +
                "  \"$type\": \"or\"\n" +
                "}", JsonTool.serialize(filter, true));

        String formated = new FilterBeanFormatter(name-> name).format(filter);
        System.out.println(formated);
    }

    @Test
    public void testNotExpr() {
        // not条件必须转换为not过滤器，不能退化为or(单参数等价于原样返回)
        TreeBean filter = SqlExprTransformHelper.parseSqlToFilter(null, "not (o.status = 3)");
        assertEquals("not", filter.getTagName());
        assertEquals(1, filter.getChildren().size());

        TreeBean body = filter.getChildren().get(0);
        assertEquals("eq", body.getTagName());
        assertEquals("o.status", body.getAttrs().get("name"));
        assertEquals(3, ((Number) body.getAttrs().get("value")).intValue());
    }

    @Test
    public void testNonLiteralValueThrowsNopException() {
        // 右操作数不是字面量时应该抛出带错误码的NopException，而不是裸IllegalArgumentException
        assertThrows(NopException.class,
                () -> SqlExprTransformHelper.parseSqlToFilter(null, "a = b + 1"));
    }
}
