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
import java.util.List;
import java.util.Map;

public class AiXDefHelper {

    public static XNode loadXDefForAi(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        return transformForAi(node);
    }

    public static XNode transformForAi(XNode defNode) {
        resolveXDefRef(defNode);

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
            if (name.startsWith("xdef:") || name.startsWith("x:") || name.startsWith("xmlns:") || name.equals("xmlns")) {
                it.remove();
            } else {
                ValueWithLocation vl = entry.getValue();
                String defType = transformDefType(vl.getLocation(), name, vl.asString());
                entry.setValue(ValueWithLocation.of(vl.getLocation(), defType));
            }
        }

        defNode.getChildren().removeIf(node -> node.getTagName().startsWith("xdef:"));

        for (XNode child : defNode.getChildren()) {
            transformForAi(child);
        }
        return defNode;
    }

    static void resolveXDefRef(XNode defNode){
        String refPath = defNode.attrText("xdef:ref");
        if (refPath != null && refPath.endsWith(".xdef")) {
            refPath = defNode.attrVPath("xdef:ref");
            XNode refNode = loadXDefForAi(refPath);
            defNode.mergeAttrs(refNode);
            List<XNode> children = refNode.detachChildren();
            if (defNode.hasChild()) {
                for (XNode child : children) {
                    if (!defNode.hasChild(child.getTagName())){
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
