package io.nop.xlang.xdsl.transformer;

import io.nop.core.lang.xml.IXNodeTransformer;
import io.nop.core.lang.xml.XNode;

public class InOutNodeTransformer implements IXNodeTransformer {
    static final String IN_PREFIX = "in:";
    static final String OUT_PREFIX = "out:";
    static final String TAG_INPUT = "input";
    static final String TAG_OUTPUT = "output";
    static final String ATTR_NAME = "name";
    static final String TAG_SOURCE = "source";

    @Override
    public XNode transform(XNode node) {
        doTransform(node);
        return node;
    }

    void doTransform(XNode node){
        transformAttrs(node);
        for (int i = 0, n = node.getChildCount(); i < n; i++) {
            XNode child = node.child(i);
            String tagName = child.getTagName();
            if (tagName.startsWith(IN_PREFIX)) {
                String name = tagName.substring(IN_PREFIX.length());
                child.setTagName(TAG_INPUT);
                child.setAttr(ATTR_NAME, name);
                XNode source = XNode.make(TAG_SOURCE);
                child.wrapChildren(source);
            } else if (tagName.endsWith(OUT_PREFIX)) {
                String name = tagName.substring(OUT_PREFIX.length());
                child.setTagName(TAG_OUTPUT);
                child.setAttr(ATTR_NAME, name);
                XNode source = XNode.make(TAG_SOURCE);
                child.wrapChildren(source);
            } else {
                doTransform(child);
            }
        }
    }

    private void transformAttrs(XNode node) {
        node.removeAttrsIf((attrName, vl) -> {
            if (attrName.startsWith(IN_PREFIX)) {
                String name = attrName.substring(IN_PREFIX.length());
                XNode input = XNode.make(TAG_INPUT);
                input.setAttr(ATTR_NAME, name);
                input.addChild(TAG_SOURCE).content(vl);
                node.appendChild(input);
                return true;
            } else if (attrName.startsWith(OUT_PREFIX)) {
                String name = attrName.substring(OUT_PREFIX.length());
                XNode output = XNode.make(TAG_OUTPUT);
                output.setAttr(ATTR_NAME, name);
                output.addChild(TAG_SOURCE).content(vl);
                node.appendChild(output);
                return true;
            } else {
                return false;
            }
        });
    }
}
