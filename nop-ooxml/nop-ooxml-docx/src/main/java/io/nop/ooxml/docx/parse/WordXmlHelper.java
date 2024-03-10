/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx.parse;

import io.nop.core.lang.xml.XNode;

public class WordXmlHelper {
    public static boolean isHyperlink(XNode node) {
        return node.getTagName().equals("w:hyperlink");
    }

    public static boolean isTable(XNode node) {
        return node.getTagName().equals("w:tbl");
    }

    public static boolean isDrawing(XNode node) {
        return node.getTagName().equals("w:drawing");
    }

    public static String getText(XNode node) {
        StringBuilder sb = new StringBuilder();
        getText(node, sb);
        return sb.toString();
    }

    public static void getText(XNode node, StringBuilder sb) {
        if (node.hasChild()) {
            int i, n = node.getChildCount();
            for (i = 0; i < n; i++) {
                XNode child = node.child(i);
                String name = child.getTagName();
                if (name.equals("w:rPr")) {
                    continue;
                } else if (name.equals("w:br")) {
                    sb.append("\n");
                } else if (child.hasContent()) {
                    sb.append(child.getContentValue());
                } else if (name.equals("w:t") && child.hasContent()) {
                    sb.append(child.getContentValue());
                } else if (name.equals("w:pict") || name.equals("w:binData")) {
                    continue;
                } else if (name.equals("w:p")) {
                    if (sb.length() > 0)
                        sb.append('\n');
                    getText(child, sb);
                } else {
                    getText(child, sb);
                }
            }
        } else if (node.getTagName().equals("w:t")) {
            sb.append(node.content().asString(""));
        }
    }
}
