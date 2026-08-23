package io.nop.ooxml.pptx.model;

import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.ooxml.common.impl.XmlOfficePackagePart;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回归：getSlidesXml 此前按 TreeMap 字典序返回，slide10 会排在 slide2 之前
 */
public class TestPptSlidesOrder {

    static XmlOfficePackagePart part(String name, String text) {
        byte[] bytes = ("<slide><t>" + text + "</t></slide>").getBytes(StandardCharsets.UTF_8);
        XNode node = XNodeParser.instance().parseFromResource(
                new ByteArrayResource("/" + name, bytes, 0));
        return new XmlOfficePackagePart("ppt/slides/" + name, node);
    }

    @Test
    public void testSlidesOrderedByNumber() {
        PptOfficePackage pkg = new PptOfficePackage();
        // 乱序添加，TreeMap 内部按字典序 slide1,slide10,slide2
        pkg.addFile(part("slide1.xml", "one"));
        pkg.addFile(part("slide10.xml", "ten"));
        pkg.addFile(part("slide2.xml", "two"));

        List<XNode> slides = pkg.getSlidesXml();
        assertEquals(3, slides.size());
        assertEquals("one", slides.get(0).childByTag("t").text());
        assertEquals("two", slides.get(1).childByTag("t").text());
        assertEquals("ten", slides.get(2).childByTag("t").text());
    }
}
