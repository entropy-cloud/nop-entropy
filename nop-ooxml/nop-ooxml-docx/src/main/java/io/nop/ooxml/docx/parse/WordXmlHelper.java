package io.nop.ooxml.docx.parse;

import io.nop.commons.util.IoHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.ooxml.common.model.ImageUrlMapper;
import io.nop.ooxml.docx.model.WordOfficePackage;

import java.util.List;


public class WordXmlHelper {

    // 修改、优化后的RunStyle，重写equals
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

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            RunStyle other = (RunStyle) obj;
            return bold == other.bold && italic == other.italic
                    && underline == other.underline && strike == other.strike;
        }

        @Override
        public int hashCode() {
            return (bold ? 1 : 0) | ((italic ? 1 : 0) << 1) | ((underline ? 1 : 0) << 2) | ((strike ? 1 : 0) << 3);
        }
    }

    public static void processNode(XNode node, StringBuilder sb, boolean forMarkdown, ImageUrlMapper imageUrlMapper) {
        String tag = node.getTagName();
        if (tag.equals("w:t")) {
            if (!node.hasContent()) return;
            if (forMarkdown) {
                sb.append(StringHelper.escapeMarkdown(node.contentText()));
            } else {
                sb.append(node.contentText());
            }
        } else if (tag.equals("w:p")) {
            if (sb.length() > 0) sb.append('\n');

            if (forMarkdown) {
                // forMarkdown下：合并样式后的实现
                RunStyle lastStyle = null;
                StringBuilder runText = new StringBuilder();

                List<XNode> children = node.getChildren();
                for (int i = 0; i <= children.size(); i++) { // 多循环一轮，最后flush
                    XNode child = (i < children.size()) ? children.get(i) : null;
                    boolean isWr = (child != null && child.getTagName().equals("w:r"));
                    RunStyle currStyle = null;
                    String text = null;

                    if (isWr) {
                        currStyle = new RunStyle().fromRun(child);
                        StringBuilder rt = new StringBuilder();
                        for (XNode t : child.getChildren()) {
                            processNode(t, rt, forMarkdown, imageUrlMapper);
                        }
                        text = rt.toString();
                    }

                    boolean shouldFlush = false;
                    if (i == children.size()) {
                        shouldFlush = true;
                    } else if (!isWr) {
                        shouldFlush = runText.length() > 0;
                    } else if (runText.length() > 0 && lastStyle != null && !lastStyle.equals(currStyle)) {
                        shouldFlush = true;
                    }

                    if (shouldFlush && runText.length() > 0) {
                        String txt = runText.toString();
                        if (lastStyle != null) {
                            if (lastStyle.underline) txt = "<u>" + txt + "</u>";
                            if (lastStyle.strike) txt = "~~" + txt + "~~";
                            if (lastStyle.bold && lastStyle.italic) {
                                txt = "___" + txt + "___";
                            } else if (lastStyle.bold) {
                                txt = "**" + txt + "**";
                            } else if (lastStyle.italic) {
                                txt = "*" + txt + "*";
                            }
                        }
                        sb.append(txt);
                        runText.setLength(0);
                        lastStyle = null;
                    }

                    if (isWr && text != null && text.length() > 0) {
                        runText.append(text);
                        lastStyle = currStyle;
                    } else if (child != null && !isWr) {
                        // 递归处理非w:r
                        processNode(child, sb, forMarkdown, imageUrlMapper);
                    }
                }
            } else {
                // 非forMarkdown，直接遍历所有子节点，顺序输出文本
                for (XNode child : node.getChildren()) {
                    processNode(child, sb, forMarkdown, imageUrlMapper);
                }
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
        } else {
            for (XNode child : node.getChildren()) {
                processNode(child, sb, forMarkdown, imageUrlMapper);
            }
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