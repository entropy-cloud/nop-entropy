package io.nop.ooxml.common;

import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.ooxml.common.impl.XmlOfficePackagePart;
import io.nop.ooxml.common.model.OfficeRelsPart;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 回归：copy() 此前与模板共享可变部件（rels HashMap / XNode），
 * 渲染期的修改会污染缓存模板并在并发渲染时产生数据竞争
 */
public class TestOfficePackageCopy {

    static XNode parseNode(String xml) {
        return XNodeParser.instance().parseFromResource(
                new ByteArrayResource("/test.xml", xml.getBytes(StandardCharsets.UTF_8), 0));
    }

    @Test
    public void testCopyIsolatesRelsAndXmlParts() {
        OfficePackage pkg = new OfficePackage();
        OfficeRelsPart rels = new OfficeRelsPart("word/_rels/document.xml.rels");
        rels.addRelationship("rId1", "type-a", "target-a", null);
        pkg.addFile(rels);

        XNode node = parseNode("<root><item>1</item></root>");
        pkg.addFile(new XmlOfficePackagePart("word/document.xml", node));

        OfficePackage copy = pkg.copy();

        // 副本上追加关系与修改XML节点
        OfficeRelsPart copyRels = copy.getRels("word/_rels/document.xml.rels");
        copyRels.addRelationship("rId2", "type-b", "target-b", null);
        XmlOfficePackagePart copyDoc = (XmlOfficePackagePart) copy.getFile("word/document.xml");
        copyDoc.getNode().makeChild("added");

        // 模板不受污染
        OfficeRelsPart origRels = pkg.getRels("word/_rels/document.xml.rels");
        assertNotNull(origRels.getRelationship("rId1"));
        assertNull(origRels.getRelationship("rId2"));
        assertNotNull(copyRels.getRelationship("rId2"));

        XNode origDoc = ((XmlOfficePackagePart) pkg.getFile("word/document.xml")).getNode();
        assertEquals(1, origDoc.getChildCount());
        assertNotSame(origDoc, copyDoc.getNode());
    }
}
