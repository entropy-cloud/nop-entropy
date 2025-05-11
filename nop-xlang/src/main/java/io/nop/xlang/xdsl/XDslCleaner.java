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

    public void cleanForXDef(String xdefPath, XNode node) {
        IXDefinition xdef = SchemaLoader.loadXDefinition(xdefPath);
        clean(node, xdef.getRootNode());
    }

    public void clean(XNode node, IXDefNode defNode) {
        if (defNode == null)
            return;

        clearAttrs(defNode, node);

        Iterator<XNode> childIt = node.childIterator();
        while (childIt.hasNext()) {
            XNode child = childIt.next();
            IXDefNode childDef = defNode.getChild(child.getTagName());
            if (childDef == null) {
                childIt.remove();
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
