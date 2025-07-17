package io.nop.ooxml.docx.parse;

import io.nop.commons.util.IoHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.ooxml.common.model.ImageUrlMapper;
import io.nop.ooxml.docx.model.WordOfficePackage;


public class WordXmlHelper {

    // 样式状态
    private static class RunStyle {
        public boolean bold = false;
        public boolean italic = false;
        public boolean underline = false;
        public boolean strike = false;

        public RunStyle fromRun(XNode run) {
            if (run == null || !run.hasChild("w:rPr")) return this;
            XNode rPr = run.childByTag("w:rPr");
            if (rPr.hasChild("w:b")) bold = true;
            if (rPr.hasChild("w:i")) italic = true;
            if (rPr.hasChild("w:u")) underline = true;
            if (rPr.hasChild("w:strike")) strike = true;
            return this;
        }
    }

    public static boolean isHyperlink(XNode node) {
        return node.getTagName().equals("w:hyperlink");
    }

    public static boolean isTable(XNode node) {
        return node.getTagName().equals("w:tbl");
    }

    public static boolean isDrawing(XNode node) {
        return node.getTagName().equals("w:drawing");
    }

    // 支持图片映射接口
    public static String getText(XNode node, boolean forMarkdown, ImageUrlMapper imageUrlMapper) {
        StringBuilder sb = new StringBuilder();
        getText(node, sb, forMarkdown, imageUrlMapper);
        return sb.toString();
    }

    public static void getText(XNode node, StringBuilder sb, boolean forMarkdown, ImageUrlMapper imageUrlMapper) {
        processNode(node, sb, forMarkdown, imageUrlMapper);
    }

    // 保持兼容旧版
    public static String getText(XNode node) {
        return getText(node, false, null);
    }

    public static void getText(XNode node, StringBuilder sb) {
        getText(node, sb, false, null);
    }

    // 递归处理，支持图片提取
    public static void processNode(XNode node, StringBuilder sb, boolean forMarkdown, ImageUrlMapper imageUrlMapper) {
        String tag = node.getTagName();
        if (tag.equals("w:t")) {
            if (!node.hasContent())
                return;

            if (forMarkdown) {
                XNode run = node.getParent();
                RunStyle style = new RunStyle().fromRun(run);
                String text = node.contentText();
                text = StringHelper.escapeMarkdown(text);

                // 下划线/删除线/粗斜处理顺序
                if (style.underline)
                    text = "<u>" + text + "</u>";
                if (style.strike)
                    text = "~~" + text + "~~";
                if (style.bold && style.italic) {
                    text = "***" + text + "***";
                } else if (style.bold) {
                    text = "**" + text + "**";
                } else if (style.italic) {
                    text = "*" + text + "*";
                }
                sb.append(text);
            } else {
                sb.append(node.contentText());
            }
        } else if (tag.equals("w:p")) {
            if (sb.length() > 0) sb.append('\n');
            for (XNode child : node.getChildren()) {
                processNode(child, sb, forMarkdown, imageUrlMapper);
            }
        } else if (tag.equals("w:br")) {
            sb.append('\n');
        } else if (tag.equals("w:tab")) {
            sb.append('\t');
        } else if (tag.equals("w:drawing")) {
            if (forMarkdown && imageUrlMapper != null) {
                String imgUrl = extractImageUrl(node, imageUrlMapper);
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    sb.append("![](").append(imgUrl).append(")");
                }
            }
            // 不递归子节点
        } else {
            for (XNode child : node.getChildren()) {
                processNode(child, sb, forMarkdown, imageUrlMapper);
            }
        }
    }

    /**
     * 从w:drawing节点中提取rId, 用ImageUrlMapper获取url
     */
    public static String extractImageUrl(XNode drawingNode, ImageUrlMapper imageUrlMapper) {
        XNode blipNode = drawingNode.find(node -> "a:blip".equals(node.getTagName()));
        if (blipNode != null) {
            String rId = blipNode.attrText("r:embed");
            if (rId != null) {
                return imageUrlMapper.getImageUrl(rId);
            }
        }
        return null;
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