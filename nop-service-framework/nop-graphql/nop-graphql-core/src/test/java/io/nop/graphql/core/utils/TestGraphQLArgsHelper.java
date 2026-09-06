package io.nop.graphql.core.utils;

import io.nop.api.core.beans.FieldSelectionBean;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGraphQLArgsHelper {
    @Test
    public void testSubArgs() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_subArgs.userMappings.filter_status", 3);
        map.put("_subArgs.userMappings.query_orderBy", "name desc,status asc");

        FieldSelectionBean selectionBean = new FieldSelectionBean();
        GraphQLArgsHelper.normalizeSubArgs(selectionBean, map);

        assertTrue(map.isEmpty());
        System.out.println(selectionBean);
        assertEquals("userMappings(query:{orderBy:[\"name desc\",\"status asc\"],filter:{\"$type\":\"and\",\"$body\":[{\"$type\":\"eq\",name:\"status\",value:3}]}})",
                selectionBean.toString());
    }

    /**
     * 前缀本身以点号结尾：无二级点号的畸形参数（如REST查询参数?_subArgs.foo=1）不得触发
     * StringIndexOutOfBoundsException，应跳过并保留在args中等待后续归一化处理。
     */
    @Test
    public void testMalformedSubArgsPrefixDoesNotThrow() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_subArgs.foo", 1);

        FieldSelectionBean selectionBean = new FieldSelectionBean();
        GraphQLArgsHelper.normalizeSubArgs(selectionBean, map);

        assertEquals(1, map.get("_subArgs.foo"), "malformed entry must be left in args");
    }

    /**
     * 恰好等于前缀的参数名（"_subArgs."）同样跳过，不抛越界异常。
     */
    @Test
    public void testBareSubArgsPrefixDoesNotThrow() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_subArgs.", 1);

        FieldSelectionBean selectionBean = new FieldSelectionBean();
        GraphQLArgsHelper.normalizeSubArgs(selectionBean, map);

        assertEquals(1, map.get("_subArgs."));
    }
}
