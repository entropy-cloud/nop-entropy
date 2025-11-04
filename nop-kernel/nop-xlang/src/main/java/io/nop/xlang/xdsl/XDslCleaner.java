package io.nop.xlang.xdsl;

import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.Iterator;
import java.util.Map;

/**
 * 清除XDSL上的多余属性，只保留xdef元模型上允许的属性
 */
public class XDslCleaner {
    public static XDslCleaner INSTANCE = new XDslCleaner();

    public void cleanForXDef(String xdefPath, XNode node) {
        IXDefinition xdef = SchemaLoader.loadXDefinition(xdefPath);
        clean(node, xdef.getRootNode());
    }

    public void removeMergeOp(XNode node) {
        node.forEachNode(child -> {
            child.removeAttrsIf((name, value) -> {
                return name.startsWith(XDslKeys.DEFAULT.X_NS_PREFIX) && !name.equals(XDslKeys.DEFAULT.SCHEMA);
            });
        });
    }

    public void clean(XNode node, IXDefNode defNode) {
        if (defNode == null)
            return;

        clearAttrs(defNode, node);

        String content = node.contentText();
        if ("...".equals(content)) {
            node.content(null);
            return;
        }

        if (defNode.getChildren().isEmpty() && defNode.getXdefValue() != null)
            return;

        Iterator<XNode> childIt = node.childIterator();
        while (childIt.hasNext()) {
            XNode child = childIt.next();
            IXDefNode childDef = defNode.getChild(child.getTagName());
            if (childDef == null) {
                childIt.remove();
                if (child.hasContent()) {
                    String childName = child.getTagName();
                    if (defNode.getAttribute(childName) != null) {
                        // 子节点没有模型定义，但是同名的属性有模型定义。这有可能是AI错误的将属性生成为了子节点
                        if (!node.hasAttr(childName)) {
                            node.setAttr(childName, child.content());
                        }
                    }
                }
            } else {
                clean(child, childDef);
            }
        }
    }

    private void clearAttrs(IXDefNode defNode, XNode node) {
        if (!node.hasAttr())
            return;

        Iterator<Map.Entry<String, ValueWithLocation>> it = node.attrValueLocs().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ValueWithLocation> entry = it.next();
            String name = entry.getKey();

            if (defNode.getAttribute(name) != null)
                continue;

            it.remove();
        }
    }
}
