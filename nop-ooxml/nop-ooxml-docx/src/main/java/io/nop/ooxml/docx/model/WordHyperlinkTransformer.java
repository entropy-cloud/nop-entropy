package io.nop.ooxml.docx.model;

import io.nop.core.lang.xml.XNode;

public class WordHyperlinkTransformer {

    public static void transformNode(XNode node) {
        String content = node.contentText().trim();
        int pos = content.indexOf("HYPERLINK \"");
        if (pos < 0)
            return;

        node = node.getParent();
        XNode parent = node.getParent();

        int index = node.childIndex();
        int beginIndex = findFldBegin(parent, index);
        if (beginIndex < 0)
            return;

        int endIndex = findFldEnd(parent, index);

        if (endIndex < 0)
            return;

        XNode textNode = null;
        for (int i = index + 1; i < endIndex; i++) {
            XNode child = parent.child(i);
            XNode tNode = child.childByTag("w:t");
            if (tNode != null) {
                textNode = child;
                break;
            }
        }

        if (textNode == null)
            return;

        if (content.endsWith("\"")) {
            String url = content.substring(pos + "HYPERLINK \"".length(), content.length() - 1);
            XNode link = XNode.make("w:hyperlink");
            link.setAttr("url", url);

            for (int i = beginIndex; i <= endIndex; i++) {
                parent.removeChildByIndex(beginIndex);
            }
            link.appendChild(textNode.detach());
            parent.insertChild(beginIndex, link);
        }
    }

    static int findFldBegin(XNode node, int index) {
        for (int i = index - 1; i >= 0; i--) {
            XNode child = node.child(i);
            if (child.getTagName().equals("w:r")) {
                XNode fldChar = child.childByTag("w:fldChar");
                if (fldChar != null && "begin".equals(fldChar.getAttr("w:fldCharType"))) {
                    return i;
                }
            }
        }
        return -1;
    }

    static int findFldEnd(XNode node, int index) {
        for (int i = index + 1, n = node.getChildCount(); i < n; i++) {
            XNode child = node.child(i);
            if (child.getTagName().equals("w:r")) {
                XNode fldChar = child.childByTag("w:fldChar");
                if (fldChar != null && "end".equals(fldChar.getAttr("w:fldCharType"))) {
                    return i;
                }
            }
        }
        return -1;
    }
}
