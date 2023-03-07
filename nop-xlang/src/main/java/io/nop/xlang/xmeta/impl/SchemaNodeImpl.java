/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.impl;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.commons.text.CDataText;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.IJsonSerializable;
import io.nop.core.lang.xml.XNode;
import io.nop.core.type.IGenericType;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ISchemaNode;
import io.nop.xlang.xmeta.impl._gen._SchemaNodeImpl;

import java.util.Map;

public abstract class SchemaNodeImpl extends _SchemaNodeImpl implements ISchemaNode, IJsonSerializable {
    private ISchema refSchema;

    public String toString() {
        return getClass().getSimpleName() + "[name=" + getName() + ",loc=" + getLocation() + "]";
    }

    @Override
    public String getClassName() {
        IGenericType type = getType();
        if (type == null)
            return Object.class.getName();
        return type.getClassName();
    }

    @Override
    public ISchema getRefSchema() {
        return refSchema;
    }

    public void setRefSchema(ISchema refSchema) {
        checkAllowChange();
        this.refSchema = refSchema;
    }

    @Override
    @NoReflection
    public boolean isRefResolved() {
        return Boolean.TRUE.equals(getRefResolved());
    }

    @Override
    public XNode toNode(Map<ISchemaNode, XNode> nodeRefs) {
        XNode node = XNode.make("schema");
        node.setAttr(getLocation(), "name", getName());

        node.setLocation(getLocation());

        nodeRefs.put(this, node);

        if (getDomain() != null) {
            node.setAttr(getLocation(), "domain", getDomain());
        }

        // 如果stdDomain已经确定type，则不输出type
        boolean fixedType = false;
        if (getStdDomain() != null) {
            node.setAttr(getLocation(), "stdDomain", getStdDomain());
            IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler(getStdDomain());
            if (handler != null && handler.isFixedType())
                fixedType = true;
        }
        IGenericType type = getType();
        if (type != null && !fixedType) {
            node.setAttr(getLocation(), "type", type.toString());
        }

        if (getDisplayName() != null) {
            node.setAttr(getLocation(), "displayName", getDisplayName());
        }
        if (!StringHelper.isEmpty(getDescription())) {
            node.makeChild("description").content(getLocation(), CDataText.encodeIfNecessary(getDescription()));
        }

        if (getValidator() != null) {
            XNode validatorN = getValidator().toNode();
            node.appendChild(validatorN);
        }

        if (getRef() != null)
            node.setAttr(getLocation(), "ref", getRef());

        return node;
    }
}