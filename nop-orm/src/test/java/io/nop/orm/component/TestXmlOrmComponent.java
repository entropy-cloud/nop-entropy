/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.component;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestXmlOrmComponent {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
        ;
    }

    @Test
    public void testProp() {
        XmlOrmComponent comp = new XmlOrmComponent();
        comp.setNormalizedXml("<rule><inputs><input name='a' mandatory='true' /></inputs></rule>");

        List<Map<String, Object>> value = (List<Map<String, Object>>) comp.getChildValue("/nop/schema/rule.xdef", "inputs");
        System.out.println(JsonTool.serialize(value, true));

        value.get(0).put("name", "b");
        value.get(0).put("displayName", "xx");
        comp.setChildValue("/nop/schema/rule.xdef", "inputs", value);

        String xml = comp.getNormalizedXml();
        assertEquals("<rule>\n" +
                "    <inputs>\n" +
                "        <input name=\"b\" mandatory=\"true\" displayName=\"xx\"/>\n" +
                "    </inputs>\n" +
                "</rule>", xml);
    }
}
