package io.nop.xlang.xmeta;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.json.DslModelToXNodeTransformer;

import java.util.List;
import java.util.stream.Collectors;

public class ObjXmlValueHelper {
    public static Object getChildValueForDef(XNode node, IXDefNode parentDef, String childName) {
        if (node == null)
            return null;

        IXDefNode defNode = parentDef.getChildByPropName(childName);
        if (defNode == null) {
            XNode inputsNode = node.childByTag(childName);
            if (inputsNode == null)
                return null;
            return inputsNode.toJsonObject();
        }

        if (defNode.getXdefUniqueAttr() != null) {
            // 集合节点
            List<XNode> nodes = node.childrenByTag(defNode.getTagName());
            return nodes.stream().map(child -> JsonTool.beanToJsonObject(DslModelHelper.dslNodeToJson(defNode, child)))
                    .collect(Collectors.toList());
        }

        XNode inputsNode = node.childByTag(defNode.getTagName());
        if (inputsNode == null)
            return null;

        return JsonTool.beanToJsonObject(DslModelHelper.dslNodeToJson(defNode, inputsNode));
    }

    public static void setChildValueForDef(XNode node, IObjPropMeta propMeta, String childName, Object value) {
        XNode list = new DslModelToXNodeTransformer(null).transformValue(propMeta, value);
        if (propMeta.isVirtualListNode()) {
            if (list == null || !list.hasChild()) {
                node.removeChildrenByTag(propMeta.getChildXmlName());
            } else {
                node.replaceChildrenByTag(propMeta.getChildName(), list.detachChildren());
            }
        } else {
            String xmlName = propMeta.getXmlName();
            if (xmlName == null)
                xmlName = childName;

            if (list != null) {
                XNode inputsNode = node.childByTag(xmlName);
                if (inputsNode == null) {
                    node.appendChild(list);
                } else {
                    inputsNode.replaceBy(list);
                }
            } else {
                node.removeChildByTag(xmlName);
            }
        }
    }
}
