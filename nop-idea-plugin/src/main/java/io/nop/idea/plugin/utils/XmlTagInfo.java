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
import io.nop.xlang.xdef.IXDefComment;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefSubComment;
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

    /** 获取当前节点的 xml 标签 */
    public XmlTag getTag() {
        return tag;
    }

    /** 获取当前节点所在 DSL 的 xdef 定义 */
    public IXDefinition getDef() {
        return def;
    }

    /** 获取当前节点的 xdef 定义 */
    public IXDefNode getDefNode() {
        return defNode;
    }

    /** 获取当前节点指定子节点的 xdef 定义 */
    public IXDefNode getDefNodeChild(String tagName) {
        if (defNode == null) {
            return null;
        }
        return defNode.getChild(tagName);
    }

    /** 获取当前节点父节点的 xdef 定义 */
    public IXDefNode getParentDefNode() {
        return parentDefNode;
    }

    /** 获取当前节点在 xdsl.xdef 中所对应节点的 xdef 定义 */
    public IXDefNode getXDslDefNode() {
        return xdslDefNode;
    }

    /** 获取当前节点在 xdsl.xdef 中所对应节点的指定子节点的 xdef 定义 */
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

    /**
     * 判断当前节点上的指定属性是否为 *.xdef 的声明属性
     * <p/>
     * 也就是，在 *.xdef 中是在定义该属性及其类型，而不是为该属性赋值。
     * 在 xdef.xdef 这类自举定义的 xdsl 中，会通过不同的名字空间来区分属性声明和属性赋值。
     * 比如，名字空间为 meta 和 x 的属性则为赋值属性，其余（无名字空间和 xdef 名字空间）的则为声明属性
     */
    public boolean isXDefDeclaredAttr(String attrName) {
        String ns = StringHelper.getNamespace(attrName);

        return StringHelper.isEmpty(ns) || (!ns.equals(xdefNs) && !ns.equals(xdslNs) && !ns.equals("xmlns"));
    }

    /**
     * 获取当前节点上指定属性的 xdef 定义
     * <ul>
     *     <li><code>xdef.xdef</code> 节点属性上的 <code>meta</code> 名字空间自动转换为 <code>xdef</code>；</li>
     *     <li><code>xdsl.xdef</code> 节点属性上的 <code>xdsl</code> 名字空间自动转换为 <code>x</code>；</li>
     * </ul>
     */
    public IXDefAttribute getAttr(String attrName) {
        // TODO xpl 名字空间的节点定义在 xpl.xdef 中
        attrName = XDefPsiHelper.normalizeNamespace(attrName, xdefNs, xdslNs);

        IXDefAttribute attr = defNode != null ? defNode.getAttribute(attrName) : null;
        if (attr == null) {
            attr = xdslDefNode != null ? xdslDefNode.getAttribute(attrName) : null;
        }
        return attr;
    }

    /** 获取当前节点上指定属性的类型 */
    public XDefTypeDecl getAttrType(String attrName) {
        IXDefAttribute attr = getAttr(attrName);

        if (attr == null) {
            return defNode != null ? defNode.getXdefUnknownAttr() : null;
        }
        return attr.getType();
    }

    /** 获取节点注释 */
    public IXDefComment getComment() {
        return defNode != null ? defNode.getComment() : null;
    }

    /**
     * 获取指定属性的注释
     * <ul>
     *     <li><code>xdef.xdef</code> 节点属性上的 <code>meta</code> 名字空间自动转换为 <code>xdef</code>；</li>
     *     <li><code>xdsl.xdef</code> 节点属性上的 <code>xdsl</code> 名字空间自动转换为 <code>x</code>；</li>
     * </ul>
     */
    public IXDefSubComment getAttrComment(String attrName) {
        attrName = XDefPsiHelper.normalizeNamespace(attrName, xdefNs, xdslNs);

        IXDefComment comment;
        if (defNode == null || defNode.getAttribute(attrName) == null) {
            comment = xdslDefNode != null ? xdslDefNode.getComment() : null;
        } else {
            comment = getComment();
        }

        return comment != null ? comment.getSubComments().get(attrName) : null;
    }
}
