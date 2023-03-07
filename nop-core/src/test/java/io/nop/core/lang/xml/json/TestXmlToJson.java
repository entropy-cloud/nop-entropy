/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml.json;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestXmlToJson extends BaseTestCase {
    @Test
    public void testStdXmlToJson() {
        IResource resource = attachmentResource("test.xml");
        XNode node = XNodeParser.instance().parseFromResource(resource);

        Object json = node.toJsonObject();
        System.out.println(JsonTool.stringify(json, null, "  "));
        XNode node2 = StdJsonToXNodeTransformer.INSTANCE.transformToXNode(json);
        assertEquals(node.xml(), node2.xml());
    }

    @Test
    public void testCompactXmlToJson() {
        XNode node = attachmentXml("test-compact.xml");
        Object json = new CompactXNodeToJsonTransformer().transformToObject(node);
        System.out.println(JsonTool.serialize(json, true));
        assertEquals(attachmentJsonText("test-compact.json"), JsonTool.serialize(json, true));
    }

    @Test
    public void testDynamic() {
        XNode node = attachmentXml("test-dynamic-key.xml");
        Object json = new CompactXNodeToJsonTransformer().transformToObject(node);
        System.out.println(JsonTool.serialize(json, true));
        assertEquals(attachmentJsonText("test-dynamic-key.json"), JsonTool.serialize(json, true));
    }

    @Test
    public void testSimpleChild() {
        XNode node = XNodeParser.instance().parseFromText(null, "<div><body><text>s</text></body></div>");
        Object json = new CompactXNodeToJsonTransformer().transformToObject(node);
        System.out.println(JsonTool.stringify(json));
        assertEquals("{\"type\":\"div\",\"body\":\"s\"}", JsonTool.stringify(json));
    }
}
