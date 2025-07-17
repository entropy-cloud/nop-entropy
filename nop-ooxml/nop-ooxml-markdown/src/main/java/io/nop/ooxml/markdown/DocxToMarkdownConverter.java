package io.nop.ooxml.markdown;

import io.nop.commons.util.IoHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.excel.model.ExcelTable;
import io.nop.markdown.simple.MarkdownDocument;
import io.nop.markdown.simple.MarkdownSection;
import io.nop.markdown.simple.TableToMarkdownConverter;
import io.nop.ooxml.common.model.ImageUrlMapper;
import io.nop.ooxml.common.model.RelsImageUrlMapper;
import io.nop.ooxml.docx.DocxConstants;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.ooxml.docx.parse.WordTableParser;
import io.nop.ooxml.docx.parse.WordXmlHelper;

import java.util.Stack;

public class DocxToMarkdownConverter {

    public MarkdownDocument convertFromResource(IResource resource) {
        return convertFromResource(resource, null);
    }

    public MarkdownDocument convertFromResource(IResource resource, String imageBaseUrl) {

        WordOfficePackage pkg = new WordOfficePackage();
        try {
            pkg.loadFromResource(resource);

            XNode doc = pkg.getWordXml();

            ImageUrlMapper urlMapper = new RelsImageUrlMapper(pkg.getRels(DocxConstants.PATH_WORD_DOCUMENT), imageBaseUrl);
            return convertFromNode(doc, urlMapper);
        } finally {
            IoHelper.safeCloseObject(pkg);
        }
    }

    public MarkdownDocument convertFromNode(XNode node, ImageUrlMapper urlMapper) {
        MarkdownDocument doc = new MarkdownDocument();
        XNode bodyNode = node.childByTag("w:body");
        if (bodyNode == null)
            return doc;

        Stack<MarkdownSection> sectionStack = new Stack<>();
        MarkdownSection root = new MarkdownSection();
        sectionStack.push(root);
        doc.setRootSection(root);

        StringBuilder currentText = new StringBuilder();
        // 当前列表层级状态（使用数组实现引用传递）
        int[] currentListLevel = new int[]{-1}; // -1表示不在列表中

        for (XNode child : bodyNode.getChildren()) {
            if ("w:p".equals(child.getTagName())) {
                ParagraphInfo paraInfo = getParagraphInfo(child);
                handleParagraph(child, urlMapper, sectionStack, currentText, paraInfo, currentListLevel);
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

    // 修改：增加paraInfo和currentListLevel参数
    private void handleParagraph(XNode paraNode, ImageUrlMapper urlMapper, Stack<MarkdownSection> sectionStack,
                                 StringBuilder currentText, ParagraphInfo paraInfo,
                                 int[] currentListLevel) {
        switch (paraInfo.type) {
            case HEADING:
                // 遇到标题时重置列表状态
                currentListLevel[0] = -1;
                if (currentText.length() > 0) {
                    appendTextToCurrentSection(sectionStack.peek(), currentText);
                }
                createNewSection(paraNode, urlMapper, sectionStack);
                break;

            case LIST_ITEM:
                // 列表项处理
                handleListItem(paraNode, urlMapper, currentText, paraInfo.listInfo, currentListLevel);
                break;

            default:
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
                                StringBuilder currentText,
                                ListInfo listInfo, int[] currentListLevel) {
        int newLevel = listInfo.level;

        // 只在列表层级下降时添加空行（从子列表回到父列表时）
        if (currentListLevel[0] != -1 && newLevel < currentListLevel[0]) {
            currentText.append("\n");
        }

        // 在任何列表项开始前加空行（确保换行）
        if (currentText.length() > 0) {
            currentText.append("\n");
        }

        // 生成缩进（每级2空格）
        String indent = "  ".repeat(newLevel);
        String listItemText = extractParagraphText(paraNode, urlMapper);

        // 添加列表项
        currentText.append(indent).append("- ").append(listItemText);

        // 设置当前层级
        currentListLevel[0] = newLevel;
    }

    // 修改：增强段落信息检测
    private ParagraphInfo getParagraphInfo(XNode paraNode) {
        // 检测标题
        int headingLevel = getHeadingLevel(paraNode);
        if (headingLevel > 0) {
            return new ParagraphInfo(ParagraphType.HEADING, null);
        }

        // 检测列表项
        XNode pPr = paraNode.childByTag("w:pPr");
        if (pPr != null) {
            XNode numPr = pPr.childByTag("w:numPr");
            if (numPr != null) {
                int listLevel = getListLevel(paraNode);
                return new ParagraphInfo(ParagraphType.LIST_ITEM, new ListInfo(listLevel));
            }
        }

        return new ParagraphInfo(ParagraphType.REGULAR, null);
    }

    // 新增：获取列表层级
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

    private static class ParagraphInfo {
        final ParagraphType type;
        final ListInfo listInfo; // 仅LIST_ITEM类型有效

        ParagraphInfo(ParagraphType type, ListInfo listInfo) {
            this.type = type;
            this.listInfo = listInfo;
        }
    }

    // 新增：列表信息封装类
    private static class ListInfo {
        final int level; // 列表嵌套层级（0=顶级）

        ListInfo(int level) {
            this.level = level;
        }
    }

    private enum ParagraphType {
        HEADING,
        LIST_ITEM,
        REGULAR
    }


    private void createNewSection(XNode headingNode, ImageUrlMapper urlMapper, Stack<MarkdownSection> sectionStack) {
        int level = getHeadingLevel(headingNode);
        String title = extractParagraphText(headingNode, urlMapper);

        // Pop stack until we reach appropriate parent level
        while (sectionStack.size() > 1 && sectionStack.peek().getLevel() >= level) {
            sectionStack.pop();
        }

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

    private int getHeadingLevel(XNode paraNode) {
        XNode pPr = paraNode.childByTag("w:pPr");
        if (pPr != null) {
            XNode pStyle = pPr.childByTag("w:pStyle");
            if (pStyle != null) {
                String styleName = pStyle.attrText("w:val");
                if (styleName != null && styleName.startsWith("Heading")) {
                    try {
                        return Integer.parseInt(styleName.substring(7));
                    } catch (NumberFormatException e) {
                        // Fall through to default
                    }
                }
            }
        }
        return 0;
    }

    private String extractParagraphText(XNode paraNode, ImageUrlMapper urlMapper) {
        return WordXmlHelper.getText(paraNode, true, urlMapper);
    }
}