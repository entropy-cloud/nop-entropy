package io.nop.xlang.xdsl;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xmeta.SchemaLoader;

/**
 * 清除XDSL上的多余属性，只保留xdef元模型上允许的属性
 */
public class XDslExtracter {
    public static XDslExtracter INSTANCE = new XDslExtracter();

    public XNode extractForXDef(String xdefPath, XNode node) {
        IXDefinition xdef = SchemaLoader.loadXDefinition(xdefPath);
        return extract(node, xdef.getRootNode());
    }

    public XNode extract(XNode node, IXDefNode defNode) {
        if (defNode == null)
            return null;

        XNode ret = XNode.make(node.getTagName());
        ret.setTagName(node.getTagName());
        ret.setLocation(node.getLocation());

        extractAttrs(defNode, node, ret);
        ret.content(node.content());

        for (XNode child : node.getChildren()) {
            IXDefNode childDef = defNode.getChild(child.getTagName());
            if (childDef != null) {
                XNode newChild = extract(child, childDef);
                ret.appendChild(newChild);
            }

        }
        return ret;
    }

    private void extractAttrs(IXDefNode defNode, XNode node, XNode ret) {
        if (!node.hasAttr())
            return;

        node.forEachAttr((attrName, value) -> {
            if (defNode.getAttribute(attrName) != null)
                ret.setAttr(attrName, value);
        });
    }
}
