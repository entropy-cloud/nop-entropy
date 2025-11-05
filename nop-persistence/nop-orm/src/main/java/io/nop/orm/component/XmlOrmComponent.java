/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.component;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ObjXmlValueHelper;
import io.nop.xlang.xmeta.SchemaLoader;

import static io.nop.core.CoreConstants.DUMMY_TAG_NAME;

public class XmlOrmComponent extends AbstractOrmComponent {
    public static final String PROP_NAME_xmlText = "xmlText";

    private XNode node;

    public String getXmlText() {
        return (String) internalGetPropValue(PROP_NAME_xmlText);
    }

    public void setXmlText(String xmlText) {
        this.node = null;
        internalSetPropValue(PROP_NAME_xmlText, xmlText);
    }

    @Override
    public void reset() {
        node = null;
    }

    public String getNormalizedXml() {
        XNode node = getNode();
        return node == null ? null : node.xml();
    }

    public void setNormalizedXml(String xml) {
        if (StringHelper.isEmpty(xml)) {
            this.setXmlText(null);
        } else {
            markDirty();
            this.node = XNodeParser.instance().parseFromText(null, xml);
        }
    }

    public XNode getNode() {
        if (node == null) {
            String text = getXmlText();
            node = parseXml(text);
        }
        return node;
    }

    public XNode makeNode(String tagName) {
        XNode node = getNode();
        if (node == null) {
            node = XNode.make(tagName);
            setNode(node);
        }
        return node;
    }

    public void setNode(XNode node) {
        if (node == null) {
            setXmlText(null);
        } else {
            markDirty();
            this.node = node;
        }
    }

    public Object getJsonObject() {
        XNode node = getNode();
        if (node == null)
            return null;
        return node.toJsonObject();
    }

    public void setJsonObject(Object value) {
        this.node = XNode.fromValue(value);
        if (this.node == null) {
            setXmlText(null);
        } else {
            markDirty();
        }
    }

    public Object getXJson() {
        XNode node = getNode();
        if (node == null)
            return null;
        return node.toXJson();
    }

    protected XNode parseXml(String xml) {
        if (StringHelper.isEmpty(xml))
            return null;
        return XNodeParser.instance().parseFromText(null, xml);
    }

    public String getChildBodyXml(String childName) {
        XNode node = getNode();
        if (node == null)
            return null;

        XNode child = node.childByTag(childName);
        if (child == null)
            return null;

        if (child.getChildCount() == 1)
            return child.child(0).xml();

        return child.bodyFullXml();
    }

    public void setChildBodyXml(String rootTagName,
                                String childName, String xml) {
        markDirty();
        XNode node = makeNode(rootTagName);
        XNode child = node.makeChild(childName);
        if (StringHelper.isBlank(xml)) {
            child.clearBody();
            return;
        }

        child.clearBody();

        XNode valueNode = XNodeParser.instance().parseFromText(null, xml);
        if (valueNode.getTagName().equals(childName) || valueNode.getTagName().equals(DUMMY_TAG_NAME)) {
            child.content(valueNode.content());
            child.appendChildren(valueNode.detachChildren());
        } else {
            child.appendChild(valueNode);
        }
    }

    public Object getChildValue(String xdefPath, String childName) {
        IXDefinition objDef = SchemaLoader.loadXDefinition(xdefPath);
        return ObjXmlValueHelper.getChildValueForDef(getNode(), objDef, childName);
    }

    public void setChildValue(String xdefPath, String childName, Object value) {
        markDirty();

        IObjMeta objMeta = SchemaLoader.loadXMeta(xdefPath);
        IObjPropMeta propMeta = objMeta.getProp(childName);
        if (propMeta == null) {
            throw new IllegalArgumentException("nop.err.invalid-child-name:" + childName);
        }
        String xmlName = objMeta.getXmlName();
        ObjXmlValueHelper.setChildValueForDef(makeNode(xmlName), propMeta, childName, value);
    }

    @Override
    public void flushToEntity() {
        if (node != null) {
            String xmlText = node.xml();
            internalSetPropValue(PROP_NAME_xmlText, xmlText);
        }
    }
}