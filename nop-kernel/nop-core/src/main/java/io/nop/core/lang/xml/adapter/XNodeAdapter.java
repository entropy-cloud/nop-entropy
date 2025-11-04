/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml.adapter;

import io.nop.core.lang.xml.XNode;

import java.util.Collection;

public class XNodeAdapter implements IXNodeAdapter<XNode> {
    public static XNodeAdapter INSTANCE = new XNodeAdapter();

    @Override
    public String tagName(XNode node) {
        return node.getTagName();
    }

    @Override
    public Object attr(XNode node, String attrName) {
        return node.getAttr(attrName);
    }

    @Override
    public XNode getParent(XNode node) {
        return node.getParent();
    }

    @Override
    public Collection<XNode> getChildren(XNode node) {
        return node.getChildren();
    }

    @Override
    public Object value(XNode node) {
        return node.getContentValue();
    }

    @Override
    public String xml(XNode node) {
        return node.xml();
    }

    @Override
    public String innerXml(XNode node) {
        return node.innerXml();
    }

    @Override
    public String html(XNode node) {
        return node.html();
    }

    @Override
    public String innerHtml(XNode node) {
        return node.innerHtml();
    }

    @Override
    public String text(XNode node) {
        return node.text();
    }

    @Override
    public void setAttr(XNode node, String attrName, Object value) {
        node.setAttr(attrName, value);
    }

    @Override
    public void setValue(XNode node, Object value) {
        node.setContentValue(value);
    }

    @Override
    public void setXml(XNode node, String xml) {
        node.replaceByXml(null, xml, false);
    }

    @Override
    public void setInnerXml(XNode node, String innerXml) {
        node.setInnerXml(innerXml);
    }

    @Override
    public void setHtml(XNode node, String html) {
        node.replaceByXml(null, html, true);
    }

    @Override
    public void setInnerHtml(XNode node, String html) {
        node.setInnerXml(null, html, true);
    }

    @Override
    public void setText(XNode node, String text) {
        node.setContentValue(text);
    }
}
