package io.nop.ooxml.docx.model;

import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.impl.XmlOfficePackagePart;
import io.nop.ooxml.docx.parse.WordXmlHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * <w:comment w:id="1" w:author="John" w:date="2023-10-05T09:00:00Z">
 * <w:p>
 * <w:r>
 * <w:t>This is a sample comment.</w:t>
 * </w:r>
 * </w:p>
 * </w:comment>
 */
public class WordCommentsPart extends XmlOfficePackagePart {
    private Map<String, String> comments = new HashMap<>();

    public WordCommentsPart(String path, XNode node) {
        super(path, node);
        parse();
    }

    protected void parse() {
        for (XNode child : this.getNode().getChildren()) {
            String tagName = child.getTagName();
            if (tagName.equals("w:comment")) {
                String id = child.attrText("w:id");
                String text = WordXmlHelper.getText(child);
                comments.put(id, text);
            }
        }
    }

    public String getComment(String commentId) {
        return comments.get(commentId);
    }
}
