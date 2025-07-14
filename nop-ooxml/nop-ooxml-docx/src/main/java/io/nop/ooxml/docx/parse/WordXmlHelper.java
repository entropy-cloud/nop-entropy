/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx.parse;

import io.nop.commons.util.IoHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.ooxml.docx.model.WordOfficePackage;

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
        processNode(node, new IContentHandler() {
            @Override
            public void content(String text) {
                if (text != null)
                    sb.append(text);
            }

            @Override
            public void image(String imageId) {
            }

            @Override
            public void br() {
                sb.append("\n");
            }

            @Override
            public void beginParagraph() {
                if (sb.length() > 0)
                    sb.append('\n');

            }

            @Override
            public void endParagraph() {

            }
        });
    }

    public static void processNode(XNode node, IContentHandler handler) {
        if (node.hasChild()) {
            int i, n = node.getChildCount();
            for (i = 0; i < n; i++) {
                XNode child = node.child(i);
                String name = child.getTagName();
                if (name.equals("w:rPr") || name.equals("w:binData")) {
                    continue;
                } else if (name.equals("w:br")) {
                    handler.br();
                } else if (child.hasContent()) {
                    handler.content(child.contentText());
                } else if (name.equals("w:t") && child.hasContent()) {
                    handler.content(child.contentText());
                } else if (name.equals("w:pict") || name.equals("w:drawing")) {
                    continue;
                } else if (name.equals("w:p")) {
                    handler.beginParagraph();
                    processNode(child, handler);
                    handler.endParagraph();
                } else {
                    processNode(child, handler);
                }
            }
        } else if (node.getTagName().equals("w:t")) {
            handler.content(node.contentText());
        }
    }

    public static XNode loadDocxXml(IResource resource) {
        WordOfficePackage pkg = new WordOfficePackage();
        try {
            pkg.loadFromResource(resource);

            XNode doc = pkg.getWordXml();

            return doc;
        } finally {
            IoHelper.safeCloseObject(pkg);
        }
    }

    public static String generateParaId() {
        // 32位十六进制 (8字节)，兼容Word Online
        return StringHelper.intToHex(MathHelper.random().nextInt());
    }
}
