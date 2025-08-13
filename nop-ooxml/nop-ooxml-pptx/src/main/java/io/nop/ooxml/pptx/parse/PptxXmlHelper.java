package io.nop.ooxml.pptx.parse;

import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.model.ImageUrlMapper;

import java.util.List;

public class PptxXmlHelper {

    // PowerPoint文本运行样式
    private static class RunStyle {
        public boolean bold = false;
        public boolean italic = false;
        public boolean underline = false;
        public boolean strike = false;

        public RunStyle fromRun(XNode run) {
            if (run == null || !run.hasChild("a:rPr")) return this;
            XNode rPr = run.childByTag("a:rPr");
            if (rPr.attrText("b") != null && !"0".equals(rPr.attrText("b"))) bold = true;
            if (rPr.attrText("i") != null && !"0".equals(rPr.attrText("i"))) italic = true;
            if (rPr.attrText("u") != null && !"none".equals(rPr.attrText("u"))) underline = true;
            if (rPr.attrText("strike") != null && !"noStrike".equals(rPr.attrText("strike"))) strike = true;
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

        if (tag.equals("a:t")) {
            // PowerPoint文本内容
            if (!node.hasContent()) return;
            if (forMarkdown) {
                sb.append(StringHelper.escapeMarkdown(node.contentText()));
            } else {
                sb.append(node.contentText());
            }
        } else if (tag.equals("a:p")) {
            // PowerPoint段落
            if (sb.length() > 0) sb.append('\n');

            if (forMarkdown) {
                // 处理项目符号
                String bulletType = getBulletType(node);
                if (bulletType != null) {
                    sb.append(bulletType).append(" ");
                }

                // 合并样式处理
                RunStyle lastStyle = null;
                StringBuilder runText = new StringBuilder();

                List<XNode> children = node.getChildren();
                for (int i = 0; i <= children.size(); i++) {
                    XNode child = (i < children.size()) ? children.get(i) : null;
                    boolean isAr = (child != null && child.getTagName().equals("a:r"));
                    RunStyle currStyle = null;
                    String text = null;

                    if (isAr) {
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
                    } else if (!isAr) {
                        shouldFlush = runText.length() > 0;
                    } else if (runText.length() > 0 && lastStyle != null && !lastStyle.equals(currStyle)) {
                        shouldFlush = true;
                    }

                    if (shouldFlush && runText.length() > 0) {
                        String txt = runText.toString();
                        if (lastStyle != null) {
                            txt = txt.trim();
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

                    if (isAr && text != null && text.length() > 0) {
                        runText.append(text);
                        lastStyle = currStyle;
                    } else if (child != null && !isAr) {
                        processNode(child, sb, forMarkdown, imageUrlMapper);
                    }
                }
            } else {
                for (XNode child : node.getChildren()) {
                    processNode(child, sb, forMarkdown, imageUrlMapper);
                }
            }
        } else if (tag.equals("a:br")) {
            sb.append('\n');
        } else if (tag.equals("a:tab")) {
            sb.append('\t');
        } else if (tag.equals("p:pic")) {
            // PowerPoint图片
            if (forMarkdown && imageUrlMapper != null) {
                String imgUrl = extractImageUrl(node, imageUrlMapper);
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    sb.append("![](").append(imgUrl).append(")");
                }
            }
        } else if (tag.equals("p:sp")) {
            // PowerPoint形状/文本框
            if (forMarkdown) {
                // 检查是否是标题
                String titleLevel = getTitleLevel(node);
                if (titleLevel != null) {
                    sb.append(titleLevel).append(" ");
                }
            }
            for (XNode child : node.getChildren()) {
                processNode(child, sb, forMarkdown, imageUrlMapper);
            }
        } else if (tag.equals("p:sld")) {
            // 幻灯片分隔
            if (forMarkdown && sb.length() > 0) {
                sb.append("\n\n---\n\n");
            }
            for (XNode child : node.getChildren()) {
                processNode(child, sb, forMarkdown, imageUrlMapper);
            }
        } else {
            for (XNode child : node.getChildren()) {
                processNode(child, sb, forMarkdown, imageUrlMapper);
            }
        }
    }

    // 获取项目符号类型
    private static String getBulletType(XNode pNode) {
        XNode pPr = pNode.childByTag("a:pPr");
        if (pPr == null) return null;

        if (pPr.hasChild("a:buChar")) {
            return "-"; // 无序列表
        } else if (pPr.hasChild("a:buAutoNum")) {
            return "1."; // 有序列表
        }
        return null;
    }

    // 获取标题级别
    private static String getTitleLevel(XNode spNode) {
        // 这里可以根据形状的样式或位置判断标题级别
        // 简化实现，返回二级标题
        XNode nvSpPr = spNode.childByTag("p:nvSpPr");
        if (nvSpPr != null) {
            XNode cNvPr = nvSpPr.childByTag("p:cNvPr");
            if (cNvPr != null) {
                String name = cNvPr.attrText("name");
                if (name != null && name.toLowerCase().contains("title")) {
                    return "##"; // 标题
                }
            }
        }
        return null;
    }

    public static boolean isSlide(XNode node) {
        return node.getTagName().equals("p:sld");
    }

    public static boolean isShape(XNode node) {
        return node.getTagName().equals("p:sp");
    }

    public static boolean isPicture(XNode node) {
        return node.getTagName().equals("p:pic");
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

    // 兼容旧版本
    public static String getText(XNode node) {
        return getText(node, false, null);
    }

    public static void getText(XNode node, StringBuilder sb) {
        getText(node, sb, false, null);
    }


    public static String extractImageUrl(XNode drawingNode, ImageUrlMapper imageUrlMapper) {
        // PowerPoint中图片可能在不同的结构中
        XNode blipNode = null;

        // 尝试多种路径查找图片节点
        if (drawingNode.getTagName().equals("p:pic")) {
            // 在 p:pic 中查找
            blipNode = drawingNode.find(node -> "a:blip".equals(node.getTagName()));
        } else if (drawingNode.getTagName().equals("a:blip")) {
            // 直接是 blip 节点
            blipNode = drawingNode;
        } else {
            // 在子节点中递归查找
            blipNode = drawingNode.find(node -> "a:blip".equals(node.getTagName()));
        }

        if (blipNode != null) {
            String rId = blipNode.attrText("r:embed");
            if (rId != null && imageUrlMapper != null) {
                String url = imageUrlMapper.getImageUrl(rId);
                // 验证URL是否指向图片文件
                if (url != null && isImageFile(url)) {
                    return url;
                }
            }
        }
        return null;
    }

    private static boolean isImageFile(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                lowerUrl.endsWith(".png") || lowerUrl.endsWith(".gif") ||
                lowerUrl.endsWith(".bmp") || lowerUrl.endsWith(".svg") ||
                lowerUrl.contains("/media/"); // PowerPoint图片通常在media目录
    }


    public static String generateSlideId() {
        // 生成幻灯片ID
        return StringHelper.intToHex(MathHelper.random().nextInt());
    }
}