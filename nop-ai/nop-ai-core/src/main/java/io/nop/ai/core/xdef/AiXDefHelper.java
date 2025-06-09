package io.nop.ai.core.xdef;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdsl.DslNodeLoader;
import io.nop.xlang.xdsl.XDslParseHelper;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AiXDefHelper {
    public static Map<String, Object> loadXDefForAiAsJson(String path) {
        XNode node = loadXDefForAi(path, true);
        return xdefToJson(node);
    }

    static Map<String, Object> xdefToJson(XNode node) {
        Map<String, Object> ret = new LinkedHashMap<>();
        String beanTagProp = node.attrText("xdef:bean-tag-prop");
        if (beanTagProp != null) {
            ret.put(beanTagProp, node.getTagName());
        }

        node.forEachAttr((name, value) -> {
            if (name.startsWith("xdef:"))
                return;
            ret.put(name, value.asString());
        });

        String bodyProp = node.attrText("xdef:bean-body-prop");
        if (bodyProp == null)
            bodyProp = "body";

        if (node.hasContent()) {
            ret.put(bodyProp, node.getContentValue());
        } else {
            boolean list = "list".equals(node.attrText("xdef:body-type"));
            if (list) {
                List<Map<String, Object>> body = node.getChildren().stream().map(AiXDefHelper::xdefToJson).collect(Collectors.toList());
                ret.put(bodyProp, body);
            } else {
                for (XNode child : node.getChildren()) {
                    if (ret.containsKey(child.getTagName()))
                        continue;

                    boolean hasUniqueAttr = child.hasAttr("xdef:unique-attr");
                    if (hasUniqueAttr) {
                        List<Map<String, Object>> body = node.childrenByTag(child.getTagName()).stream().map(AiXDefHelper::xdefToJson).collect(Collectors.toList());
                        ret.put(child.getTagName(), body);
                    } else {
                        if (isSimpleList(child)) {
                            List<Map<String, Object>> body = child.getChildren().stream().map(AiXDefHelper::xdefToJson).collect(Collectors.toList());
                            ret.put(child.getTagName(), body);
                        } else {
                            Map<String, Object> body = xdefToJson(child);
                            ret.put(child.getTagName(), body);
                        }
                    }
                }
            }
        }

        return ret;
    }

    private static boolean isSimpleList(XNode node) {
        boolean list = "list".equals(node.attrText("xdef:body-type"));
        if (!list)
            return false;
        // 如果有xdef元数据之外的属性，则不是简单列表
        for (String name : node.getAttrNames()) {
            if (!name.startsWith("xdef:"))
                return false;
        }
        return true;
    }

    public static XNode loadXDefForAi(String path) {
        return loadXDefForAi(path, false);
    }

    public static XNode loadXDefForAi(String path, boolean keepXDefAttr) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        return transformForAi(node, keepXDefAttr);
    }

    public static XNode transformForAi(XNode defNode, boolean keepXDefAttr) {
        resolveXDefRef(defNode, keepXDefAttr);

        ValueWithLocation xdefValue = defNode.attrValueLoc("xdef:value");
        if (!xdefValue.isNull()) {
            defNode.setContentValue(transformDefType(xdefValue.getLocation(), "xdef:value", xdefValue.asString()));
        } else {
            ValueWithLocation content = defNode.content();
            if (!content.isNull())
                defNode.setContentValue(transformDefType(content.getLocation(), "body", content.asString()));
        }

        Iterator<Map.Entry<String, ValueWithLocation>> it = defNode.attrValueLocs().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ValueWithLocation> entry = it.next();
            String name = entry.getKey();
            if (name.startsWith("x:") || name.startsWith("xmlns:") || name.equals("xmlns")) {
                it.remove();
            } else if (name.startsWith("xdef:")) {
                if (!keepXDefAttr)
                    it.remove();
            } else {
                ValueWithLocation vl = entry.getValue();
                String defType = transformDefType(vl.getLocation(), name, vl.asString());
                entry.setValue(ValueWithLocation.of(vl.getLocation(), defType));
            }
        }

        defNode.getChildren().removeIf(node -> node.getTagName().startsWith("xdef:"));

        for (XNode child : defNode.getChildren()) {
            transformForAi(child, keepXDefAttr);
        }
        return defNode;
    }

    static void resolveXDefRef(XNode defNode, boolean removeXDefAttr) {
        String refPath = defNode.attrText("xdef:ref");
        if (refPath != null && refPath.endsWith(".xdef")) {
            refPath = defNode.attrVPath("xdef:ref");
            XNode refNode = loadXDefForAi(refPath, removeXDefAttr);
            defNode.mergeAttrs(refNode);
            List<XNode> children = refNode.detachChildren();
            if (defNode.hasChild()) {
                for (XNode child : children) {
                    if (!defNode.hasChild(child.getTagName())) {
                        defNode.appendChild(child);
                    }
                }
            } else {
                defNode.appendChildren(children);
            }
        }
    }

    static String transformDefType(SourceLocation loc, String propName, String defType) {
        XDefTypeDecl typeDecl = XDslParseHelper.parseDefType(loc, propName, defType);
        String stdDomain = typeDecl.getStdDomain();
        if (typeDecl.getOptions() != null)
            return stdDomain + ":" + typeDecl.getOptions();
        return stdDomain;
    }
}
