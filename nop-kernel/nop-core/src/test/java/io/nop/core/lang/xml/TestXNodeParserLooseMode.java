package io.nop.core.lang.xml;

import io.nop.core.lang.xml.parse.XNodeParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestXNodeParserLooseMode {
    @Test
    public void testXplReturn() {
        String xml = "<xpl a='1'><return='3'/></xpl>";
        XNode node = XNodeParser.instance().looseMode(true).parseFromText(null, xml);
        assertEquals("<xpl a=\"1\">\n" +
                "    <return>3</return>\n" +
                "</xpl>", node.xml());
    }

    @Test
    public void testComment() {
        String xml = "<root><!--data--><sub/></root>";
        XNode node = XNodeParser.instance().keepComment(true).looseMode(true).parseFromText(null, xml);
        assertEquals("data", node.childByTag("sub").getComment());
    }

    @Test
    public void testCDATA() {
        String xml = "<root><![CDATA[<sub/>]]></root>";
        XNode node = XNodeParser.instance().looseMode(true).parseFromText(null, xml);
        assertEquals("<sub/>", node.contentText());
    }
}
