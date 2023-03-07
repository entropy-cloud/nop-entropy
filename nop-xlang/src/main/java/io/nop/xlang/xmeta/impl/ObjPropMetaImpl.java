/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.impl;

import io.nop.commons.text.CDataText;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.XNodeValuePosition;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchemaNode;
import io.nop.xlang.xmeta.impl._gen._ObjPropMetaImpl;

import java.util.Map;

public class ObjPropMetaImpl extends _ObjPropMetaImpl implements IObjPropMeta {

    public String getChildName() {
        String name = super.getChildName();
        if (name == null) {
            name = StringHelper.xmlNameToPropName(getChildXmlName());
        }
        return name;
    }

    public String toString() {
        return ObjPropMetaImpl.class.getSimpleName() + "[name=" + getName() + ",loc=" + getLocation() + "]";
    }

    public XNode toNode(Map<ISchemaNode, XNode> nodeRefs) {
        XNode node = XNode.make("prop");
        node.setLocation(getLocation());
        if (getName() != null) {
            node.setAttr(getLocation(), "name", getName());
        }
        if (getDisplayName() != null)
            node.setAttr(getLocation(), "displayName", getDisplayName());
        if (!StringHelper.isEmpty(getDescription()))
            node.makeChild("description").content(getLocation(), CDataText.encodeIfNecessary(getDescription()));
        if (getPropId() != null)
            node.setAttr(getLocation(), "propId", getPropId());
        if (isMandatory())
            node.setAttr(getLocation(), "mandatory", true);
        if (isInternal())
            node.setAttr(getLocation(), "internal", true);
        if (isDeprecated()) {
            node.setAttr(getLocation(), "deprecated", true);
        }
        if (getAllowCpExpr() != null && getAllowCpExpr())
            node.setAttr(getLocation(), "allowCpExpr", getAllowCpExpr());
        if (getXmlName() != null && !getXmlName().equals(getName()))
            node.setAttr(getLocation(), "xmlName", getXmlName());
        if (getChildXmlName() != null)
            node.setAttr(getLocation(), "childXmlName", getChildXmlName());
        if (super.getChildName() != null && !super.getChildName().equals(getChildXmlName())) {
            node.setAttr(getLocation(), "childName", getChildName());
        }
        if (getDefaultOverride() != null)
            node.setAttr(getLocation(), "defaultOverride", getDefaultOverride().toString());
        if (getDepends() != null && !getDepends().isEmpty())
            node.setAttr(getLocation(), "depends", StringHelper.join(getDepends(), ","));

        if (getTagSet() != null && !getTagSet().isEmpty())
            node.setAttr(getLocation(), "tagSet", StringHelper.join(getTagSet(), ","));
        if (getDefaultValue() != null)
            node.setAttr(getLocation(), "defaultValue", getDefaultValue());
        if (this.getXmlPos() != null && this.getXmlPos() != XNodeValuePosition.attr)
            node.setAttr(getLocation(), "xmlPos", this.getXmlPos());
        if (getMapTo() != null)
            node.setAttr(getLocation(), "mapTo", getMapTo());

        if (getSchema() != null) {
            XNode schemaN = getSchema().toNode(nodeRefs);
            node.appendChild(schemaN);
        }
        return node;
    }
}