package io.nop.orm.component;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.orm.IOrmEntity;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.json.DslModelToXNodeTransformer;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
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

        XNode valueNode = XNodeParser.instance().parseFromText(null, xml);
        if (valueNode.getTagName().equals(childName) || child.getTagName().equals(DUMMY_TAG_NAME)) {
            child.content(valueNode.content());
            child.appendChildren(valueNode.detachChildren());
        } else {
            child.clearBody();
            child.appendChild(valueNode);
        }
    }

    public Object getChildValue(String xdefPath, String childName) {
        XNode node = getNode();
        if (node == null)
            return null;

        XNode inputsNode = node.childByTag(childName);
        if (inputsNode == null)
            return null;

        IXDefinition objDef = SchemaLoader.loadXDefinition(xdefPath);
        IXDefNode defNode = objDef.getChild(childName);

        return JsonTool.serializeToJson(DslModelHelper.dslNodeToJson(defNode, inputsNode));
    }

    public void setChildValue(String xdefPath, String childName, Object value) {
        markDirty();

        IObjMeta objMeta = SchemaLoader.loadXMeta(xdefPath);
        IObjPropMeta propMeta = objMeta.getProp(childName);
        XNode list = new DslModelToXNodeTransformer(objMeta).transformValue(propMeta, value);
        XNode node = makeNode(objMeta.getXmlName());
        if (list != null) {
            XNode inputsNode = node.childByTag(childName);
            if(inputsNode == null){
                node.appendChild(list);
            }else {
                inputsNode.replaceBy(list);
            }
        } else {
            node.removeChildByTag(childName);
        }
    }

    @Override
    public void flushToEntity() {
        if (node != null) {
            String xmlText = node.xml();
            internalSetPropValue(PROP_NAME_xmlText, xmlText);
        }
    }
}