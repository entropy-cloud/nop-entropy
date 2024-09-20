/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef.impl;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.cache.ResourceCacheEntry;
import io.nop.xlang.feature.XModelInclude;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefKeys;
import io.nop.xlang.xdef.impl._gen._XDefinition;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xdsl.XDslKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class XDefinition extends _XDefinition implements IXDefinition {
    static final Logger LOG = LoggerFactory.getLogger(XDefinition.class);

    private String xdslSchema;
    private String xdslTransform;
    private Set<String> refSchemas = Collections.emptySet();

    private XDefKeys defKeys;

    private ResourceCacheEntry<XNode> defaultsDsl;

    public Set<String> getAllRefSchemas() {
        return refSchemas;
    }

    public void setAllRefSchemas(Set<String> refSchemas) {
        checkAllowChange();
        this.refSchemas = refSchemas;
    }

    @Override
    public XDefNode getRootNode() {
        return this;
    }

    @Override
    public XDefKeys getDefKeys() {
        return defKeys;
    }

    public void setDefKeys(XDefKeys defKeys) {
        checkAllowChange();
        this.defKeys = defKeys;
    }

    @Override
    public XNode getDefaultExtendsNode() {
        String defaultExtends = getXdefDefaultExtends();
        if (StringHelper.isEmpty(defaultExtends))
            return null;

        IResource resource = VirtualFileSystem.instance().getResource(defaultExtends);
        if (!resource.exists()) {
            LOG.trace("nop.xdef.ignore-unknown-default-extends:path={}", defaultExtends);
            return null;
        }

        if (this.defaultsDsl == null) {
            this.defaultsDsl = new ResourceCacheEntry<>(defaultExtends);
        }

        return this.defaultsDsl.getObject(true, path -> {
            return XModelInclude.instance().loadActiveNodeFromResource(resource);
        });
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
    public XNode toNode() {
        Map<IXDefNode, XNode> nodeRefs = new IdentityHashMap<>();

        XNode node = super.toNode(getDefKeys(), nodeRefs);
        if (getXdefDefaultExtends() != null) {
            node.setAttr(getLocation(), defKeys.DEFAULT_EXTENDS, getXdefDefaultExtends());
        }

        node.setAttr(XDslKeys.DEFAULT.SCHEMA, XDslConstants.XDSL_SCHEMA_XDEF);
        node.setAttr("xmlns:x", XDslConstants.XDSL_SCHEMA_XDSL);
        node.setAttr("xmlns:xdef", XDslConstants.XDSL_SCHEMA_XDEF);

        Set<String> checkNs = getXdefCheckNs();
        if (checkNs != null && !checkNs.isEmpty())
            node.setAttr(getLocation(), defKeys.CHECK_NS, StringHelper.join(checkNs, ","));

        Set<String> propNs = getXdefPropNs();
        if (propNs != null && !propNs.isEmpty())
            node.setAttr(getLocation(), defKeys.PROP_NS, StringHelper.join(propNs, ","));

        for (IXDefNode localDef : getXdefDefines()) {
            if (!localDef.isExplicitDefine())
                continue;

            node.appendChild(localDef.toNode(defKeys, nodeRefs));
        }

        return node;
    }
}