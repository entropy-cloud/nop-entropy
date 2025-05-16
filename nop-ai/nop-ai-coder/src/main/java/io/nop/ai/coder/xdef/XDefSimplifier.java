package io.nop.ai.coder.xdef;

import io.nop.core.lang.xml.XNode;

public class XDefSimplifier {
    public static XDefSimplifier INSTANCE = new XDefSimplifier();

    public XNode simplify(XNode node) {
        String xdefValue = node.attrText("xdef:value");
        if (xdefValue != null) {
            node.removeAttr("xdef:value");
            node.setContentValue(xdefValue);
        }
        node.removeAttrsWithPrefix("xdef:");

        node.getChildren().removeIf(child -> child.getTagName().startsWith("xdef:"));

        for (XNode child : node.getChildren()) {
            simplify(node);
        }
        return node;
    }
}
