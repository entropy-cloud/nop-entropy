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
