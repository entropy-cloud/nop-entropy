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
}
