package io.nop.orm.component;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;

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

    public String getNormalizedXml() {
        XNode node = getNode();
        return node == null ? null : node.xml();
    }

    public void setNormalizedXml(String xml) {
        if (StringHelper.isEmpty(xml)) {
            this.setXmlText(null);
        } else {
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

    @Override
    public void flushToEntity() {
        if (node != null) {
            String xmlText = node.xml();
            internalSetPropValue(PROP_NAME_xmlText, xmlText);
        }
    }
}