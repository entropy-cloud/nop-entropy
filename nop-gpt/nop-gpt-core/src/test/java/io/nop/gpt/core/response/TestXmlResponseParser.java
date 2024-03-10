/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gpt.core.response;

import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestXmlResponseParser extends BaseTestCase {
    @Test
    public void testParse() {
        String response = classpathResource("xml-response1.txt").readText();
        XNode node = new XmlResponseParser().parseResponse(response);
        assertNotNull(node);
        node.dump();
    }
}
