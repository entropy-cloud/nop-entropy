/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.utils;

import com.intellij.psi.xml.XmlTag;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;

public class XmlTagInfo {
    private final XmlTag tag;
    private final IXDefNode defNode;
    private final IXDefNode parentDefNode;
    private final boolean custom;
    private final IXDefNode dslNode;
    private final IXDefinition xdef;

    public XmlTagInfo(XmlTag tag, IXDefNode defNode, IXDefNode parentDefNode,
                      boolean custom, IXDefNode dslNode, IXDefinition xdef) {
        this.tag = tag;
        this.defNode = defNode;
        this.parentDefNode = parentDefNode;
        this.custom = custom;
        this.dslNode = dslNode;
        this.xdef = xdef;
    }

    public boolean isAllowedUnknownName(String name) {
        if (!StringHelper.hasNamespace(name))
            return false;

        String ns = StringHelper.getNamespace(name);
        if (xdef.getXdefCheckNs() == null || xdef.getXdefCheckNs().isEmpty())
            return true;

        return !xdef.getXdefCheckNs().contains(ns);
    }

    public IXDefinition getXdef() {
        return xdef;
    }

    public IXDefNode getDslNode() {
        return dslNode;
    }

    public IXDefNode getParentDefNode() {
        return parentDefNode;
    }

    public IXDefNode getDslNodeChild(String tagName) {
        if (dslNode == null)
            return null;
        return dslNode.getChild(tagName);
    }

    public IXDefNode getDefNodeChild(String tagName) {
        if (defNode == null)
            return null;
        return defNode.getChild(tagName);
    }

    public boolean isCustom() {
        return custom;
    }

    public boolean isSupportBody() {
        if (defNode == null)
            return false;
        if (defNode.getXdefValue() == null)
            return false;
        return defNode.getXdefValue().isSupportBody();
    }

    public XmlTag getTag() {
        return tag;
    }

    public IXDefNode getDefNode() {
        return defNode;
    }
}
