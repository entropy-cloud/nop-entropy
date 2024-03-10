/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
