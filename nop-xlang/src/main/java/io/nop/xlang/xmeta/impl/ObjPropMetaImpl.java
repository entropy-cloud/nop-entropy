/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.impl;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.commons.text.CDataText;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.TagsHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.XNodeValuePosition;
import io.nop.core.type.IGenericType;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ISchemaNode;
import io.nop.xlang.xmeta.impl._gen._ObjPropMetaImpl;

import java.util.Map;
import java.util.Set;

public class ObjPropMetaImpl extends _ObjPropMetaImpl implements IObjPropMeta {
    static final ActionAuthMeta NULL_AUTH = new ActionAuthMeta(false, null, null);
    private ActionAuthMeta writeAuth;

    public ActionAuthMeta getReadAuth() {
        ObjPropAuthModel authModel = getAuth(ApiConstants.AUTH_FOR_READ);
        if (authModel != null) {
            return authModel.toActionAuthMeta();
        }
        return getWriteAuth();
    }

    public ActionAuthMeta getWriteAuth() {
        if (writeAuth != null) {
            if (writeAuth == NULL_AUTH) return null;
            return writeAuth;
        }

        ObjPropAuthModel authModel = getAuth(ApiConstants.AUTH_FOR_WRITE);
        if (authModel == null) {
            authModel = getAuth(ApiConstants.AUTH_FOR_ALL);
        }
        if (authModel != null) {
            writeAuth = authModel.toActionAuthMeta();
        }
        if (writeAuth == null)
            writeAuth = NULL_AUTH;

        if (writeAuth == NULL_AUTH)
            return null;
        return writeAuth;
    }

    public ActionAuthMeta getDeleteAuth() {
        ObjPropAuthModel authModel = getAuth(ApiConstants.AUTH_FOR_DELETE);
        if (authModel != null) {
            return authModel.toActionAuthMeta();
        }
        return getWriteAuth();
    }

    public String getChildName() {
        String name = super.getChildName();
        if (name == null) {
            name = StringHelper.xmlNameToPropName(getChildXmlName());
        }
        return name;
    }

    public IGenericType getType() {
        ISchema schema = getSchema();
        return schema == null ? null : schema.getType();
    }

    public void setType(IGenericType type) {
        ISchema schema = getSchema();
        if (schema == null) {
            schema = new SchemaImpl();
            setSchema(schema);
        }
        ((SchemaNodeImpl) schema).setType(type);
    }

    public Set<String> getDependOnProps() {
        return TagsHelper.removeInternalPrefix(getDepends());
    }

    public boolean hasInternalDependOn() {
        return TagsHelper.containsInternal(getDepends());
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
        if (getDisplayName() != null) node.setAttr(getLocation(), "displayName", getDisplayName());
        if (!StringHelper.isEmpty(getDescription()))
            node.makeChild("description").content(getLocation(), CDataText.encodeIfNecessary(getDescription()));
        if (getPropId() != null) node.setAttr(getLocation(), "propId", getPropId());
        if (isMandatory()) node.setAttr(getLocation(), "mandatory", true);
        if (isInternal()) node.setAttr(getLocation(), "internal", true);
        if (isDeprecated()) {
            node.setAttr(getLocation(), "deprecated", true);
        }
        if (getAllowCpExpr() != null && getAllowCpExpr()) node.setAttr(getLocation(), "allowCpExpr", getAllowCpExpr());
        if (getXmlName() != null && !getXmlName().equals(getName()))
            node.setAttr(getLocation(), "xmlName", getXmlName());
        if (getChildXmlName() != null) node.setAttr(getLocation(), "childXmlName", getChildXmlName());
        if (super.getChildName() != null && !super.getChildName().equals(getChildXmlName())) {
            node.setAttr(getLocation(), "childName", getChildName());
        }
        if (getDefaultOverride() != null)
            node.setAttr(getLocation(), "defaultOverride", getDefaultOverride().toString());
        if (getDepends() != null && !getDepends().isEmpty())
            node.setAttr(getLocation(), "depends", StringHelper.join(getDepends(), ","));

        if (getTagSet() != null && !getTagSet().isEmpty())
            node.setAttr(getLocation(), "tagSet", StringHelper.join(getTagSet(), ","));
        if (getDefaultValue() != null) node.setAttr(getLocation(), "defaultValue", getDefaultValue());
        if (this.getXmlPos() != null && this.getXmlPos() != XNodeValuePosition.attr)
            node.setAttr(getLocation(), "xmlPos", this.getXmlPos());
        if (getMapToProp() != null) node.setAttr(getLocation(), "mapToProp", getMapToProp());

        if (getSchema() != null) {
            XNode schemaN = getSchema().toNode(nodeRefs);
            node.appendChild(schemaN);
        }
        return node;
    }
}