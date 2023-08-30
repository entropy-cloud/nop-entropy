package io.nop.web.page.condition;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.json.JSON;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestConditionExprHelper {
    @Test
    public void testTransform(){
        Map<String,Object> cond = (Map<String, Object>) JsonTool.parseNonStrict("{left: {type:'field',field:'aa'},op:'is_not_empty'}");


        TreeBean filter = ConditionExprHelper.conditionToFilter(cond);
        System.out.println(JsonTool.serialize(filter,true));
        assertEquals("{\n" +
                "  \"$type\": \"notEmpty\",\n" +
                "  \"name\": \"aa\"\n" +
                "}",JsonTool.serialize(filter,true));
    }
}
