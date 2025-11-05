package io.nop.ooxml.markdown;

import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.excel.model.ExcelTable;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.model.MarkdownSection;
import io.nop.markdown.table.TableToMarkdownConverter;
import io.nop.markdown.utils.MarkdownHelper;
import io.nop.ooxml.common.model.ImageUrlMapper;
import io.nop.ooxml.common.model.RelsImageUrlMapper;
import io.nop.ooxml.docx.DocxConstants;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.ooxml.docx.model.WordStylesPart;
import io.nop.ooxml.docx.parse.WordTableParser;
import io.nop.ooxml.docx.parse.WordXmlHelper;

import java.io.File;
import java.util.Stack;

public class DocxToMarkdownConverter {
    private File imagesDir;

    public MarkdownDocument convertFromResource(IResource resource) {
        return convertFromResource(resource, null);
    }

    public MarkdownDocument convertFromResource(IResource resource, String imageBaseUrl) {
        WordOfficePackage pkg = new WordOfficePackage();
        try {
            pkg.loadFromResource(resource);

            if (imagesDir != null)
                pkg.saveImagesToDir(imagesDir);

            XNode doc = pkg.getWordXml();

            // 获取样式信息
            WordStylesPart stylesPart = pkg.getStyles();

            ImageUrlMapper urlMapper = new RelsImageUrlMapper(pkg.getRelsForPartPath(DocxConstants.PATH_WORD_DOCUMENT), imageBaseUrl);
            return convertFromNode(doc, urlMapper, stylesPart);
        } finally {
            IoHelper.safeCloseObject(pkg);
        }
    }

    public DocxToMarkdownConverter imagesDirPath(String path) {
        return imagesDir(FileHelper.resolveFile(path));
    }

    public DocxToMarkdownConverter imagesDir(File imagesDir) {
        this.imagesDir = imagesDir;
        return this;
    }

    public MarkdownDocument convertFromNode(XNode node, ImageUrlMapper urlMapper, WordStylesPart stylesPart) {
        MarkdownDocument doc = new MarkdownDocument();
        XNode bodyNode = node.childByTag("w:body");
        if (bodyNode == null)
            return doc;

        Stack<MarkdownSection> sectionStack = new Stack<>();
        MarkdownSection root = new MarkdownSection();
        sectionStack.push(root);
        doc.setRootSection(root);

        StringBuilder currentText = new StringBuilder();
        int[] currentListLevel = new int[]{-1}; // -1表示不在列表中

        for (XNode child : bodyNode.getChildren()) {
            if ("w:p".equals(child.getTagName())) {
                // 先读取段落样式，然后基于样式信息进行处理
                ParagraphStyle paragraphStyle = readParagraphStyle(child, stylesPart);
                handleParagraph(child, urlMapper, sectionStack, currentText, paragraphStyle, currentListLevel);
            } else if ("w:tbl".equals(child.getTagName())) {
                // 处理表格前重置列表状态
                if (currentListLevel[0] != -1) {
                    currentListLevel[0] = -1;
                }
                handleTable(child, currentText, urlMapper);
            }
        }

        if (currentText.length() > 0) {
            appendTextToCurrentSection(sectionStack.peek(), currentText);
        }

        return doc;
    }

    /**
     * 读取段落样式信息，正确处理outlineLevel的优先级
     */
    private ParagraphStyle readParagraphStyle(XNode paraNode, WordStylesPart stylesPart) {
        XNode pPr = paraNode.childByTag("w:pPr");
        if (pPr == null) {
            return new ParagraphStyle(null, null, null, null, null, 0, false, 0);
        }

        // 1. 优先检查当前段落的直接大纲级别定义
        Integer directOutlineLevel = null;
        XNode directOutlineLvl = pPr.childByTag("w:outlineLvl");
        if (directOutlineLvl != null) {
            String val = directOutlineLvl.attrText("w:val");
            if (val != null) {
                try {
                    directOutlineLevel = Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        }

        // 2. 读取样式ID和样式信息
        XNode pStyle = pPr.childByTag("w:pStyle");
        String styleId = null;
        String styleName = null;
        Integer styleOutlineLevel = null;

        if (pStyle != null) {
            styleId = pStyle.attrText("w:val");

            if (stylesPart != null && styleId != null) {
                WordStylesPart.StyleInfo styleInfo = stylesPart.getStyleInfo(styleId);
                if (styleInfo != null) {
                    styleName = styleInfo.styleName;
                    styleOutlineLevel = styleInfo.outlineLevel;
                }
            }
        }

        // 3. 确定最终的大纲级别（直接定义优先于样式定义）
        Integer finalOutlineLevel = directOutlineLevel != null ? directOutlineLevel : styleOutlineLevel;
        int headingLevel = 0;
        if (finalOutlineLevel != null) {
            headingLevel = Math.min(finalOutlineLevel + 1, 6);
        }

        // 4. 检查列表信息
        boolean isListItem = false;
        int listLevel = 0;
        XNode numPr = pPr.childByTag("w:numPr");
        if (numPr != null) {
            isListItem = true;
            listLevel = getListLevel(paraNode);
        }

        return new ParagraphStyle(styleId, styleName, directOutlineLevel, styleOutlineLevel,
                finalOutlineLevel, headingLevel, isListItem, listLevel);
    }

    private void handleParagraph(XNode paraNode, ImageUrlMapper urlMapper, Stack<MarkdownSection> sectionStack,
                                 StringBuilder currentText, ParagraphStyle paragraphStyle,
                                 int[] currentListLevel) {
        if (paragraphStyle.headingLevel > 0) {
            // 遇到标题时重置列表状态
            currentListLevel[0] = -1;
            if (currentText.length() > 0) {
                appendTextToCurrentSection(sectionStack.peek(), currentText);
            }
            createNewSection(paraNode, urlMapper, sectionStack, paragraphStyle.headingLevel);
        } else if (paragraphStyle.isListItem) {
            // 列表项处理
            handleListItem(paraNode, urlMapper, currentText, paragraphStyle.listLevel, currentListLevel);
        } else {
            // 普通段落：重置列表状态
            if (currentListLevel[0] != -1) {
                currentListLevel[0] = -1;
            }
            String text = extractParagraphText(paraNode, urlMapper);
            if (!text.isEmpty()) {
                if (currentText.length() > 0) {
                    currentText.append("\n\n");
                }
                currentText.append(text);
            }
        }
    }

    private void handleListItem(XNode paraNode, ImageUrlMapper urlMapper,
                                StringBuilder currentText, int listLevel,
                                int[] currentListLevel) {
        // 只在列表层级下降时添加空行（从子列表回到父列表时）
        if (currentListLevel[0] != -1 && listLevel < currentListLevel[0]) {
            currentText.append("\n");
        }

        // 在任何列表项开始前加空行（确保换行）
        if (currentText.length() > 0) {
            currentText.append("\n");
        }

        // 生成缩进（每级2空格）
        String indent = "  ".repeat(listLevel);
        String listItemText = extractParagraphText(paraNode, urlMapper);

        // 添加列表项
        currentText.append(indent).append("- ").append(listItemText);

        // 设置当前层级
        currentListLevel[0] = listLevel;
    }

    private int getListLevel(XNode paraNode) {
        XNode pPr = paraNode.childByTag("w:pPr");
        if (pPr != null) {
            XNode numPr = pPr.childByTag("w:numPr");
            if (numPr != null) {
                XNode ilvl = numPr.childByTag("w:ilvl");
                if (ilvl != null) {
                    String val = ilvl.attrText("w:val");
                    if (val != null) {
                        try {
                            return Integer.parseInt(val);
                        } catch (NumberFormatException e) {
                            // 忽略格式错误
                        }
                    }
                }
            }
        }
        return 0; // 默认为0级
    }

    /**
     * 段落样式信息封装类，包含完整的优先级信息
     */
    private static class ParagraphStyle {
        final String styleId;
        final String styleName;
        final Integer directOutlineLevel;  // 段落直接定义的大纲级别
        final Integer styleOutlineLevel;   // 样式中定义的大纲级别
        final Integer finalOutlineLevel;   // 最终使用的大纲级别
        final int headingLevel;            // 计算后的标题级别(1-6)
        final boolean isListItem;
        final int listLevel;

        ParagraphStyle(String styleId, String styleName,
                       Integer directOutlineLevel, Integer styleOutlineLevel, Integer finalOutlineLevel,
                       int headingLevel, boolean isListItem, int listLevel) {
            this.styleId = styleId;
            this.styleName = styleName;
            this.directOutlineLevel = directOutlineLevel;
            this.styleOutlineLevel = styleOutlineLevel;
            this.finalOutlineLevel = finalOutlineLevel;
            this.headingLevel = headingLevel;
            this.isListItem = isListItem;
            this.listLevel = listLevel;
        }

        @Override
        public String toString() {
            return String.format("ParagraphStyle{styleId='%s', directOutline=%s, styleOutline=%s, finalOutline=%s, headingLevel=%d}",
                    styleId, directOutlineLevel, styleOutlineLevel, finalOutlineLevel, headingLevel);
        }
    }

    private void createNewSection(XNode headingNode, ImageUrlMapper urlMapper, Stack<MarkdownSection> sectionStack, int level) {
        String title = extractParagraphText(headingNode, urlMapper);

        // Pop stack until we reach appropriate parent level
        while (sectionStack.size() > 1 && sectionStack.peek().getLevel() >= level) {
            sectionStack.pop();
        }

        title = MarkdownHelper.removeStyle(title);
        MarkdownSection newSection = new MarkdownSection();
        newSection.setLevel(level);
        newSection.setTitle(title);

        sectionStack.peek().addChild(newSection);
        sectionStack.push(newSection);
    }

    private void handleTable(XNode tableNode, StringBuilder currentText, ImageUrlMapper urlMapper) {
        ExcelTable table = new WordTableParser().forMarkdown(true).imageUrlMapper(urlMapper).parseTable(tableNode);
        currentText.append("\n"); // 多插入一个空行，否则有些Markdown渲染器不识别
        new TableToMarkdownConverter().convertToMarkdown(table, currentText);
    }

    private void appendTextToCurrentSection(MarkdownSection section, StringBuilder currentText) {
        if (section.getText() == null || section.getText().isEmpty()) {
            section.setText(currentText.toString());
        } else {
            section.setText(section.getText() + "\n\n" + currentText);
        }
        currentText.setLength(0); // Clear buffer
    }

    private String extractParagraphText(XNode paraNode, ImageUrlMapper urlMapper) {
        return WordXmlHelper.getText(paraNode, true, urlMapper);
    }
}