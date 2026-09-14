/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.response;

import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestXmlResponseParser extends BaseTestCase {
    @Test
    public void testParse() {
        String response = classpathResource("xml-response1.txt").readText();
        XNode node = new XmlResponseParser().parseResponse(response);
        assertNotNull(node);
        // Real structural verification (replaces the old dump-only smoke test):
        // the parser must skip the leading prose and return the first entity
        // node with its attributes and columns intact.
        assertEquals("entity", node.getTagName());
        assertEquals("nop_auth_user", node.getAttr("name"));
        XNode columns = node.childByTag("columns");
        assertNotNull(columns);
        assertEquals(2, columns.getChildren().size());
        assertEquals("USER_NAME", columns.getChildren().get(0).getAttr("name"));
        assertEquals("PASSWORD", columns.getChildren().get(1).getAttr("name"));
    }
}
