package io.nop.orm.eql.parse;

import io.nop.api.core.beans.TreeBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.query.FilterBeanFormatter;
import io.nop.orm.eql.eval.SqlExprTransformHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
