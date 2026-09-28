package io.nop.code.core.framework;

import java.util.ArrayList;
import java.util.List;

import io.nop.core.lang.xml.XNode;

/**
 * N5.3: 框架模式 DSL 解析（语法见 ai-dev/plans/nop-code/17-n5-3-dsl-driven-framework-adapter.md）。
 * 结构非法（未知元素/缺 framework@name/缺 mapping 属性）显式抛 IllegalArgumentException。
 */
public final class FrameworkPatternDsl {

    private FrameworkPatternDsl() {
    }

    public static List<FrameworkPatternModel> parse(String xml) {
        XNode root = XNode.parse(xml);
        if (root == null || !"framework-patterns".equals(root.getTagName())) {
            throw new IllegalArgumentException("DSL root element must be <framework-patterns>");
        }
        List<FrameworkPatternModel> models = new ArrayList<>();
        for (XNode child : root.getChildren()) {
            if (!"framework".equals(child.getTagName())) {
                throw new IllegalArgumentException("unknown element <" + child.getTagName()
                        + "> under <framework-patterns>");
            }
            String name = child.attrText("name");
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("<framework> requires a non-empty name attribute");
            }
            models.add(parseFramework(child, name));
        }
        if (models.isEmpty()) {
            throw new IllegalArgumentException("<framework-patterns> must contain at least one <framework>");
        }
        return models;
    }

    private static FrameworkPatternModel parseFramework(XNode node, String name) {
        FrameworkPatternModel model = new FrameworkPatternModel();
        model.setName(name);
        for (XNode child : node.getChildren()) {
            switch (child.getTagName()) {
                case "entry-point":
                    parseEntryPoint(child, model);
                    break;
                case "route-convention":
                    parseRouteConvention(child, model);
                    break;
                default:
                    throw new IllegalArgumentException("unknown element <" + child.getTagName()
                            + "> under <framework name=\"" + name + "\">");
            }
        }
        return model;
    }

    private static void parseEntryPoint(XNode node, FrameworkPatternModel model) {
        for (XNode child : node.getChildren()) {
            switch (child.getTagName()) {
                case "annotation-fqn":
                    model.getAnnotationFqns().add(child.contentText());
                    break;
                case "class-suffix":
                    model.getClassSuffixes().add(child.contentText());
                    break;
                case "name-pattern":
                    model.getNamePatterns().add(child.contentText());
                    break;
                default:
                    throw new IllegalArgumentException("unknown element <" + child.getTagName()
                            + "> under <entry-point>");
            }
        }
    }

    private static void parseRouteConvention(XNode node, FrameworkPatternModel model) {
        for (XNode child : node.getChildren()) {
            switch (child.getTagName()) {
                case "mapping": {
                    String annotation = child.attrText("annotation");
                    if (annotation == null || annotation.isEmpty()) {
                        throw new IllegalArgumentException("<mapping> requires a non-empty annotation attribute");
                    }
                    String method = child.attrText("method");
                    model.getMappingAnnotations().add(annotation);
                    model.getHttpMethodMappings().put(annotation, method == null ? "" : method);
                    break;
                }
                case "class-prefix-annotation":
                    model.getClassPrefixAnnotations().add(child.contentText());
                    break;
                default:
                    throw new IllegalArgumentException("unknown element <" + child.getTagName()
                            + "> under <route-convention>");
            }
        }
    }
}
