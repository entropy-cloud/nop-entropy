package io.nop.ooxml.pptx.model;

import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.impl.XmlOfficePackagePart;
import io.nop.ooxml.pptx.parse.PptxXmlHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PowerPoint幻灯片部件
 * <p:sld xmlns:p="..." xmlns:a="...">
 * <p:cSld>
 * <p:spTree>
 * <p:sp>...</p:sp>
 * <p:pic>...</p:pic>
 * </p:spTree>
 * </p:cSld>
 * </p:sld>
 */
public class SlidePart extends XmlOfficePackagePart {
    private List<SlideShape> shapes = new ArrayList<>();
    private List<SlideImage> images = new ArrayList<>();
    private Map<String, String> textBoxes = new HashMap<>();
    private String slideTitle;

    public SlidePart(String path, XNode node) {
        super(path, node);
        parse();
    }

    protected void parse() {
        XNode slide = this.getNode();
        if (!slide.getTagName().equals("p:sld")) {
            return;
        }

        // 解析公共幻灯片数据
        XNode cSld = slide.childByTag("p:cSld");
        if (cSld == null) {
            return;
        }

        // 解析形状树
        XNode spTree = cSld.childByTag("p:spTree");
        if (spTree == null) {
            return;
        }

        // 解析所有子元素
        for (XNode child : spTree.getChildren()) {
            String tagName = child.getTagName();
            if (tagName.equals("p:sp")) {
                parseShape(child);
            } else if (tagName.equals("p:pic")) {
                parseImage(child);
            }
        }
    }

    private void parseShape(XNode spNode) {
        SlideShape shape = new SlideShape();

        // 获取形状属性
        XNode nvSpPr = spNode.childByTag("p:nvSpPr");
        if (nvSpPr != null) {
            XNode cNvPr = nvSpPr.childByTag("p:cNvPr");
            if (cNvPr != null) {
                shape.id = cNvPr.attrText("id");
                shape.name = cNvPr.attrText("name");
            }
        }

        // 获取文本内容
        XNode txBody = spNode.childByTag("p:txBody");
        if (txBody != null) {
            shape.text = PptxXmlHelper.getText(txBody, true, null);

            // 判断是否是标题
            if (shape.name != null && shape.name.toLowerCase().contains("title")) {
                slideTitle = shape.text;
                shape.isTitle = true;
            }

            // 存储到文本框映射中
            if (shape.id != null) {
                textBoxes.put(shape.id, shape.text);
            }
        }

        shapes.add(shape);
    }

    private void parseImage(XNode picNode) {
        SlideImage image = new SlideImage();

        // 获取图片属性
        XNode nvPicPr = picNode.childByTag("p:nvPicPr");
        if (nvPicPr != null) {
            XNode cNvPr = nvPicPr.childByTag("p:cNvPr");
            if (cNvPr != null) {
                image.id = cNvPr.attrText("id");
                image.name = cNvPr.attrText("name");
            }
        }

        // 获取图片关系ID
        XNode blipFill = picNode.childByTag("p:blipFill");
        if (blipFill != null) {
            XNode blip = blipFill.childByTag("a:blip");
            if (blip != null) {
                image.relationshipId = blip.attrText("r:embed");
            }
        }

        images.add(image);
    }

    /**
     * 获取幻灯片的所有文本内容
     */
    public String getAllText() {
        return PptxXmlHelper.getText(this.getNode());
    }

    /**
     * 获取幻灯片的所有文本内容（Markdown格式）
     */
    public String getAllTextAsMarkdown() {
        return PptxXmlHelper.getText(this.getNode(), true, null);
    }

    /**
     * 获取幻灯片标题
     */
    public String getSlideTitle() {
        return slideTitle;
    }

    /**
     * 获取指定ID的文本框内容
     */
    public String getTextBox(String shapeId) {
        return textBoxes.get(shapeId);
    }

    /**
     * 获取所有文本框
     */
    public Map<String, String> getAllTextBoxes() {
        return new HashMap<>(textBoxes);
    }

    /**
     * 获取所有形状
     */
    public List<SlideShape> getShapes() {
        return new ArrayList<>(shapes);
    }

    /**
     * 获取所有图片
     */
    public List<SlideImage> getImages() {
        return new ArrayList<>(images);
    }

    /**
     * 获取标题形状
     */
    public SlideShape getTitleShape() {
        return shapes.stream()
                .filter(shape -> shape.isTitle)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取内容形状（非标题）
     */
    public List<SlideShape> getContentShapes() {
        return shapes.stream()
                .filter(shape -> !shape.isTitle)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * 幻灯片形状类
     */
    public static class SlideShape {
        public String id;
        public String name;
        public String text;
        public boolean isTitle = false;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getText() {
            return text;
        }

        public boolean isTitle() {
            return isTitle;
        }
    }

    /**
     * 幻灯片图片类
     */
    public static class SlideImage {
        public String id;
        public String name;
        public String relationshipId;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getRelationshipId() {
            return relationshipId;
        }
    }
}