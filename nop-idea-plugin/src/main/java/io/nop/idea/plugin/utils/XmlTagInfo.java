/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.utils;

import com.intellij.psi.xml.XmlTag;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.domain.StdDomainRegistry;

public class XmlTagInfo {
    private final XmlTag tag;

    private final IXDefinition def;
    private final IXDefNode defNode;
    private final IXDefNode parentDefNode;
    private final IXDefNode xdslDefNode;

    private final boolean custom;
    private final String xdefNs;
    private final String xdslNs;

    public XmlTagInfo(
            XmlTag tag, //
            IXDefinition def, IXDefNode defNode, IXDefNode parentDefNode, //
            IXDefNode xdslDefNode, //
            boolean custom, String xdefNs, String xdslNs
    ) {
        this.tag = tag;
        this.def = def;
        this.defNode = defNode;
        this.parentDefNode = parentDefNode;
        this.xdslDefNode = xdslDefNode;
        this.custom = custom;
        this.xdefNs = xdefNs;
        this.xdslNs = xdslNs;
    }

    public boolean isAllowedUnknownName(String name) {
        if (!StringHelper.hasNamespace(name)) {
            return false;
        }

        String ns = StringHelper.getNamespace(name);
        if (def.getXdefCheckNs() == null || def.getXdefCheckNs().isEmpty()) {
            return true;
        }

        return !def.getXdefCheckNs().contains(ns);
    }

    public IXDefinition getDef() {
        return def;
    }

    public IXDefNode getDefNode() {
        return defNode;
    }

    public IXDefNode getDefNodeChild(String tagName) {
        if (defNode == null) {
            return null;
        }
        return defNode.getChild(tagName);
    }

    public IXDefNode getParentDefNode() {
        return parentDefNode;
    }

    public IXDefNode getXDslDefNode() {
        return xdslDefNode;
    }

    public IXDefNode getXDslDefNodeChild(String tagName) {
        if (xdslDefNode == null) {
            return null;
        }
        return xdslDefNode.getChild(tagName);
    }

    public boolean isCustom() {
        return custom;
    }

    public boolean isSupportBody() {
        if (defNode == null) {
            return false;
        }
        if (defNode.getXdefValue() == null) {
            return false;
        }
        return defNode.getXdefValue().isSupportBody(StdDomainRegistry.instance());
    }

    public XmlTag getTag() {
        return tag;
    }

    public IXDefAttribute getAttr(String attrName) {
        attrName = XDefPsiHelper.normalizeNamespace(attrName, xdefNs, xdslNs);

        IXDefAttribute attr = getDefNode().getAttribute(attrName);
        if (attr == null) {
            attr = getXDslDefNode().getAttribute(attrName);
        }
        return attr;
    }

    public XDefTypeDecl getAttrType(String attrName) {
        IXDefAttribute attr = getAttr(attrName);

        if (attr == null) {
            return getDefNode().getXdefUnknownAttr();
        }
        return attr.getType();
    }
}
