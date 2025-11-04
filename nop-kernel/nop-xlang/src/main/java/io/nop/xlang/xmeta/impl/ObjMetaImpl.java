/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.impl;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ISchemaNode;
import io.nop.xlang.xmeta.impl._gen._ObjMetaImpl;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ObjMetaImpl extends _ObjMetaImpl implements IObjMeta, ISchema {
    private String xdslSchema;
    private String xdslTransform;

    private Boolean hasMapToProp;

    @Override
    public String getBizObjName() {
        String bizObjName = super.getBizObjName();
        if (bizObjName == null)
            bizObjName = StringHelper.fileNameNoExt(resourcePath());
        return bizObjName;
    }

    @Override
    public boolean hasMapToProp() {
        if (hasMapToProp == null) {
            for (IObjPropMeta propMeta : getProps()) {
                if (propMeta.getMapToProp() != null) {
                    hasMapToProp = true;
                    return true;
                }
            }
            hasMapToProp = false;
            return false;
        }
        return hasMapToProp;
    }

    @Override
    public String getXdslSchema() {
        return xdslSchema;
    }

    @Override
    public void setXdslSchema(String xdslSchema) {
        checkAllowChange();
        this.xdslSchema = xdslSchema;
    }

    @Override
    public String getXdslTransform() {
        return xdslTransform;
    }

    @Override
    public void setXdslTransform(String xdslTransform) {
        checkAllowChange();
        this.xdslTransform = xdslTransform;
    }

    @Override
    public ISchema getRootSchema() {
        return this;
    }

    @Override
    public List<IObjSchema> getDefinedObjSchemas() {
        Map<String, IObjSchema> ret = new TreeMap<>();
        for (ISchema schema : getDefines()) {
            if (schema.isObjSchema()) {
                ret.put(schema.getName(), schema);
            }
        }
        return new ArrayList<>(ret.values());
    }

    @Override
    public XNode toNode() {
        Map<ISchemaNode, XNode> nodeRefs = new IdentityHashMap<>();
        XNode node = super.toNode(nodeRefs);
        node.setTagName("meta");
        node.setAttr(XDslKeys.DEFAULT.SCHEMA, XDslConstants.XDSL_SCHEMA_XMETA);
        node.setAttr("xmlns:x", XDslConstants.XDSL_SCHEMA_XDSL);
        if (getVersion() != null)
            node.setAttr(getLocation(), "version", getVersion());

        if (getDefaultExtends() != null)
            node.setAttr(getLocation(), "defaultExtends", getDefaultExtends());

        if (getXmlName() != null) {
            node.setAttr(getLocation(), "xmlName", getXmlName());
        }


        XNode defNodes = XNode.make("defines");
        for (ISchema def : getDefines()) {
            if (!def.isExplicitDefine())
                continue;

            XNode child = def.toNode(nodeRefs);
            defNodes.appendChild(child);
        }
        if (defNodes.hasChild()) {
            node.appendChild(defNodes);
        }

        XNode propsN = node.makeChild("props");
        for (IObjPropMeta prop : this.getProps()) {
            XNode propN = prop.toNode(nodeRefs);
            propsN.appendChild(propN);
        }

        return node;
    }

    @Override
    public ISchema getMapValueSchema() {
        return null;
    }

    @Override
    public Integer getMinItems() {
        return null;
    }

    @Override
    public Integer getMaxItems() {
        return null;
    }

    @Override
    public String getKeyAttr() {
        return null;
    }

    @Override
    public String getKeyProp() {
        return null;
    }

    @Override
    public String getOrderAttr() {
        return null;
    }

    @Override
    public String getOrderProp() {
        return null;
    }

    @Override
    public ISchema getItemSchema() {
        return null;
    }

    @Override
    public String getDict() {
        return null;
    }

    @Override
    public Integer getPrecision() {
        return null;
    }

    @Override
    public Integer getScale() {
        return null;
    }

    @Override
    public String getPattern() {
        return null;
    }

    @Override
    public boolean matchPattern(String str) {
        return false;
    }

    @Override
    public Double getMin() {
        return null;
    }

    @Override
    public Double getMax() {
        return null;
    }

    @Override
    public Boolean getExcludeMin() {
        return null;
    }

    @Override
    public Boolean getExcludeMax() {
        return null;
    }

    @Override
    public Integer getMinLength() {
        return null;
    }

    @Override
    public Integer getUtf8Length() {
        return null;
    }

    @Override
    public Integer getMaxLength() {
        return null;
    }

    @Override
    public Integer getMultipleOf() {
        return null;
    }

    @Override
    public String getSubTypeProp() {
        return null;
    }

    @Override
    public List<ISchema> getOneOf() {
        return null;
    }

    @Override
    public boolean isExplicitDefine() {
        return false;
    }

    @NoReflection
    public boolean isAbstract() {
        return Boolean.TRUE.equals(getAbstract());
    }

    @NoReflection
    public boolean isInterface() {
        return Boolean.TRUE.equals(getInterface());
    }

    @NoReflection
    public boolean isRefResolved() {
        return Boolean.TRUE.equals(getRefResolved());
    }

}