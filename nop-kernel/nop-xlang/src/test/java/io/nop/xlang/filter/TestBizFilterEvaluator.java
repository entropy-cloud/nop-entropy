/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.filter;

import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBizFilterEvaluator {
    @Test
    public void testFilterForEntity() {
        String text = "<eq name='status' value='1' />";
        XNode node = XNodeParser.instance().forFragments(true).parseFromText(null, text);

        Map<String, Object> entity = new HashMap<>();
        entity.put("status", 1);

        boolean b = new BizFilterEvaluator(new ServiceContextImpl()).testForEntity(node, entity);
        assertTrue(b);
    }

    @Test
    public void testAlwaysTrue(){
        String text = "<alwaysTrue/>";

        XNode node = XNodeParser.instance().forFragments(true).parseFromText(null, text);

        Map<String, Object> entity = new HashMap<>();
        entity.put("status", 1);

        boolean b = new BizFilterEvaluator(new ServiceContextImpl()).testForEntity(node, entity);
        assertTrue(b);
    }
}
