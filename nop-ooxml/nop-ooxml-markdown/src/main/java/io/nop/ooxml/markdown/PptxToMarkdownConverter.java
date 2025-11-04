package io.nop.ooxml.markdown;

import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.excel.model.ExcelTable;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.model.MarkdownSection;
import io.nop.markdown.table.TableToMarkdownConverter;
import io.nop.markdown.utils.MarkdownHelper;
import io.nop.ooxml.common.model.ImageUrlMapper;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.pptx.PptxConstants;
import io.nop.ooxml.pptx.model.PptOfficePackage;
import io.nop.ooxml.pptx.model.SlidePart;
import io.nop.ooxml.pptx.parse.PptxTableParser;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Pattern;

public class PptxToMarkdownConverter {
    private File imagesDir;
    private boolean includeSlideNumbers = true;
    private boolean addSlideSeparators = true;
    private String slideSeparator = "\n\n---\n\n";
    private boolean useSlideAsSection = true;
    private int baseSectionLevel = 1;
    private String imageBaseUrl;

    // 图片文件扩展名模式
    private static final Pattern IMAGE_PATTERN = Pattern.compile(".*\\.(jpg|jpeg|png|gif|bmp|svg|webp)$", Pattern.CASE_INSENSITIVE);

    public MarkdownDocument convertFromResource(IResource resource) {
        return convertFromResource(resource, null);
    }

    public MarkdownDocument convertFromResource(IResource resource, String imageBaseUrl) {
        this.imageBaseUrl = imageBaseUrl;

        PptOfficePackage pkg = new PptOfficePackage();
        try {
            pkg.loadFromResource(resource);

            if (imagesDir != null) {
                pkg.saveImagesToDir(imagesDir);
            }

            return convertFromPackage(pkg);
        } finally {
            IoHelper.safeCloseObject(pkg);
        }
    }

    public PptxToMarkdownConverter imagesDirPath(String path) {
        return imagesDir(FileHelper.resolveFile(path));
    }

    public PptxToMarkdownConverter imagesDir(File imagesDir) {
        this.imagesDir = imagesDir;
        return this;
    }

    public PptxToMarkdownConverter includeSlideNumbers(boolean include) {
        this.includeSlideNumbers = include;
        return this;
    }

    public PptxToMarkdownConverter addSlideSeparators(boolean add) {
        this.addSlideSeparators = add;
        return this;
    }

    public PptxToMarkdownConverter slideSeparator(String separator) {
        this.slideSeparator = separator;
        return this;
    }

    public PptxToMarkdownConverter useSlideAsSection(boolean use) {
        this.useSlideAsSection = use;
        return this;
    }

    public PptxToMarkdownConverter baseSectionLevel(int level) {
        this.baseSectionLevel = Math.max(1, Math.min(6, level));
        return this;
    }

    public MarkdownDocument convertFromPackage(PptOfficePackage pkg) {
        MarkdownDocument doc = new MarkdownDocument();

        Stack<MarkdownSection> sectionStack = new Stack<>();
        MarkdownSection root = new MarkdownSection();
        sectionStack.push(root);
        doc.setRootSection(root);

        StringBuilder currentText = new StringBuilder();

        // 获取所有幻灯片
        int slideCount = pkg.getSlideCount();

        for (int i = 0; i < slideCount; i++) {
            SlidePart slide = pkg.getSlide(i);
            if (slide != null) {
                handleSlide(slide, i + 1, pkg, sectionStack, currentText);

                // 添加幻灯片分隔符（除了最后一张）
                if (addSlideSeparators && !useSlideAsSection && i < slideCount - 1) {
                    currentText.append(slideSeparator);
                }
            }
        }

        if (currentText.length() > 0) {
            appendTextToCurrentSection(sectionStack.peek(), currentText);
        }

        return doc;
    }

    private void handleSlide(SlidePart slide, int slideNumber, PptOfficePackage pkg,
                             Stack<MarkdownSection> sectionStack, StringBuilder currentText) {

        SlideContent slideContent = extractSlideContent(slide, pkg);

        if (useSlideAsSection) {
            // 将每个幻灯片作为独立的section
            if (currentText.length() > 0) {
                appendTextToCurrentSection(sectionStack.peek(), currentText);
            }

            String title = slideContent.title != null ? slideContent.title :
                    (includeSlideNumbers ? "Slide " + slideNumber : "Untitled Slide");
            createNewSlideSection(title, slideNumber, sectionStack);
        } else if (slideContent.title != null) {
            // 只有有标题的幻灯片才创建section
            if (currentText.length() > 0) {
                appendTextToCurrentSection(sectionStack.peek(), currentText);
            }
            createNewSlideSection(slideContent.title, slideNumber, sectionStack);
        } else if (includeSlideNumbers) {
            // 添加幻灯片编号但不创建section
            if (currentText.length() > 0) {
                currentText.append("\n\n");
            }
            currentText.append("### Slide ").append(slideNumber).append("\n\n");
        }

        // 添加幻灯片内容
        if (!slideContent.content.isEmpty()) {
            if (currentText.length() > 0) {
                currentText.append("\n\n");
            }
            currentText.append(slideContent.content);
        }
    }

    private SlideContent extractSlideContent(SlidePart slide, PptOfficePackage pkg) {
        SlideContent content = new SlideContent();

        // 创建幻灯片专用的图片URL映射器
        SlideImageUrlMapper imageUrlMapper = new SlideImageUrlMapper(slide, pkg, imageBaseUrl);

        // 获取标题
        SlidePart.SlideShape titleShape = slide.getTitleShape();
        if (titleShape != null && titleShape.getText() != null && !titleShape.getText().trim().isEmpty()) {
            content.title = cleanText(titleShape.getText().trim());
        }

        StringBuilder contentBuilder = new StringBuilder();

        // 处理内容形状
        List<SlidePart.SlideShape> contentShapes = slide.getContentShapes();
        for (SlidePart.SlideShape shape : contentShapes) {
            if (shape.getText() != null && !shape.getText().trim().isEmpty()) {
                String shapeText = processShapeText(shape.getText());
                if (!shapeText.isEmpty()) {
                    if (contentBuilder.length() > 0) {
                        contentBuilder.append("\n\n");
                    }
                    contentBuilder.append(shapeText);
                }
            }
        }

        // 处理表格
        processSlideTablesNew(slide, contentBuilder, imageUrlMapper);

        // 处理图片
        processSlideImages(slide, contentBuilder, imageUrlMapper);

        content.content = contentBuilder.toString();
        return content;
    }

    private void processSlideTablesNew(SlidePart slide, StringBuilder contentBuilder, SlideImageUrlMapper imageUrlMapper) {
        XNode slideXml = slide.getNode();
        List<XNode> tables = slideXml.findAll(node -> "a:tbl".equals(node.getTagName()));

        for (XNode tableNode : tables) {
            try {
                ExcelTable table = new PptxTableParser()
                        .forMarkdown(true)
                        .imageUrlMapper(imageUrlMapper)
                        .parseTable(tableNode);

                if (table.getRowCount() > 0) {
                    if (contentBuilder.length() > 0) {
                        contentBuilder.append("\n\n");
                    }
                    new TableToMarkdownConverter().convertToMarkdown(table, contentBuilder);
                }
            } catch (Exception e) {
                // 表格解析失败时添加占位符
                if (contentBuilder.length() > 0) {
                    contentBuilder.append("\n\n");
                }
                contentBuilder.append("*[Table content could not be parsed]*");
            }
        }
    }

    private void processSlideImages(SlidePart slide, StringBuilder contentBuilder, SlideImageUrlMapper imageUrlMapper) {
        List<SlidePart.SlideImage> images = slide.getImages();
        for (SlidePart.SlideImage image : images) {
            if (image.getRelationshipId() != null) {
                String imageUrl = imageUrlMapper.getImageUrl(image.getRelationshipId());
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    if (contentBuilder.length() > 0) {
                        contentBuilder.append("\n\n");
                    }

                    // 添加图片描述（如果有）
                    String altText = image.getName() != null ? image.getName() : "";
                    contentBuilder.append("![").append(altText).append("](").append(imageUrl).append(")");
                }
            }
        }
    }

    private String processShapeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        //text = cleanText(text);

        // 处理列表项
        if (isListContent(text)) {
            return formatListContent(text);
        }

        return text;
    }

    private String cleanText(String text) {
        if (text == null) return "";

        // 移除多余的空白字符
        text = text.replaceAll("\\s+", " ").trim();

        // 转义Markdown特殊字符
        text = StringHelper.escapeMarkdown(text);

        return text;
    }

    private boolean isListContent(String text) {
        // 检测是否是列表内容
        return text.contains("•") || text.contains("◦") || text.contains("▪") ||
                text.matches(".*\\d+\\.\\s.*") || text.matches(".*[a-zA-Z]\\)\\s.*") ||
                text.matches(".*[\\u2022\\u25E6\\u25AA].*");
    }

    private String formatListContent(String text) {
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 替换常见的列表符号
            if (line.startsWith("•") || line.startsWith("◦") || line.startsWith("▪")) {
                line = "- " + line.substring(1).trim();
            } else if (line.matches("\\d+\\.\\s.*")) {
                // 已经是有序列表格式，保持不变
            } else if (line.matches("[a-zA-Z]\\)\\s.*")) {
                // 字母编号转换为数字编号
                line = "1. " + line.substring(2).trim();
            } else if (line.matches(".*[\\u2022\\u25E6\\u25AA].*")) {
                // 处理Unicode项目符号
                line = line.replaceFirst("[\\u2022\\u25E6\\u25AA]", "- ");
            }

            if (result.length() > 0) {
                result.append("\n");
            }
            result.append(line);
        }

        return result.toString();
    }

    private void createNewSlideSection(String title, int slideNumber, Stack<MarkdownSection> sectionStack) {
        int level = baseSectionLevel + 1;

        title = MarkdownHelper.removeStyle(title);

        while (sectionStack.size() > 1 && sectionStack.peek().getLevel() >= level) {
            sectionStack.pop();
        }

        MarkdownSection newSection = new MarkdownSection();
        newSection.setLevel(level);
        newSection.setTitle(title);

        sectionStack.peek().addChild(newSection);
        sectionStack.push(newSection);
    }

    private void appendTextToCurrentSection(MarkdownSection section, StringBuilder currentText) {
        if (currentText.length() == 0) return;

        String newText = currentText.toString().trim();
        if (newText.isEmpty()) {
            currentText.setLength(0);
            return;
        }

        if (section.getText() == null || section.getText().isEmpty()) {
            section.setText(newText);
        } else {
            section.setText(section.getText() + "\n\n" + newText);
        }
        currentText.setLength(0);
    }

    // 专门的幻灯片图片URL映射器
    private static class SlideImageUrlMapper implements ImageUrlMapper {
        private final SlidePart slide;
        private final PptOfficePackage pkg;
        private final String imageBaseUrl;
        private final Map<String, String> imageUrlCache = new HashMap<>();

        public SlideImageUrlMapper(SlidePart slide, PptOfficePackage pkg, String imageBaseUrl) {
            this.slide = slide;
            this.pkg = pkg;
            this.imageBaseUrl = imageBaseUrl;
        }

        @Override
        public String getImageUrl(String rId) {
            if (rId == null) return null;

            // 使用缓存
            if (imageUrlCache.containsKey(rId)) {
                return imageUrlCache.get(rId);
            }

            String imageUrl = resolveImageUrl(rId);
            imageUrlCache.put(rId, imageUrl);
            return imageUrl;
        }

        private String resolveImageUrl(String rId) {
            // 首先尝试幻灯片级别的关系
            OfficeRelsPart slideRels = pkg.getRelsForPart(slide);

            if (slideRels != null) {
                OfficeRelationship rel = slideRels.getRelationship(rId);
                if (rel != null && isImageRelation(rel)) {
                    return buildImageUrl(rel.getTarget());
                }
            }

            // 如果幻灯片级别没找到，尝试演示文稿级别
            OfficeRelsPart presentationRels = pkg.getRels(PptxConstants.PATH_PPT_RELS);
            if (presentationRels != null) {
                OfficeRelationship rel = presentationRels.getRelationship(rId);
                if (rel != null && isImageRelation(rel)) {
                    return buildImageUrl(rel.getTarget());
                }
            }

            return null;
        }

        private boolean isImageRelation(OfficeRelationship rel) {
            if (rel == null) return false;

            String type = rel.getType();
            String target = rel.getTarget();

            // 检查关系类型
            if (type != null && type.contains("image")) {
                return true;
            }

            // 检查目标文件是否是图片
            if (target != null) {
                return IMAGE_PATTERN.matcher(target).matches() || target.contains("/media/");
            }

            return false;
        }

        private String buildImageUrl(String target) {
            if (target == null) return null;

            // 确保不是XML文件
            if (target.endsWith(".xml")) {
                return null;
            }

            // 处理相对路径
            if (target.startsWith("../")) {
                target = target.substring(3);
            }

            if (imageBaseUrl != null) {
                return imageBaseUrl + "/" + target;
            }

            return target;
        }
    }

    private static class SlideContent {
        String title;
        String content = "";
    }

    /**
     * 转换单个幻灯片为Markdown文本
     */
    public String convertSlideToMarkdown(SlidePart slide, PptOfficePackage pkg) {
        SlideContent content = extractSlideContent(slide, pkg);

        StringBuilder sb = new StringBuilder();

        if (content.title != null) {
            sb.append("## ").append(content.title).append("\n\n");
        }

        if (!content.content.isEmpty()) {
            sb.append(content.content);
        }

        return sb.toString();
    }

    /**
     * 获取演示文稿的纯文本内容
     */
    public String convertToPlainText(IResource resource) {
        PptOfficePackage pkg = new PptOfficePackage();
        try {
            pkg.loadFromResource(resource);

            StringBuilder sb = new StringBuilder();
            int slideCount = pkg.getSlideCount();

            for (int i = 0; i < slideCount; i++) {
                SlidePart slide = pkg.getSlide(i);
                if (slide != null) {
                    String slideText = slide.getAllText();
                    if (!slideText.trim().isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append("\n\n");
                        }
                        if (includeSlideNumbers) {
                            sb.append("Slide ").append(i + 1).append(":\n");
                        }
                        sb.append(slideText.trim());
                    }
                }
            }

            return sb.toString();
        } finally {
            IoHelper.safeCloseObject(pkg);
        }
    }
}