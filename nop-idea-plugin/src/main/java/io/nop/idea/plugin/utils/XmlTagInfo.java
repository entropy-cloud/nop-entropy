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
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import io.nop.xlang.xdef.impl.XDefAttribute;
import io.nop.xlang.xdef.parse.XDefTypeDeclParser;
import io.nop.xlang.xdsl.XDslConstants;

public class XmlTagInfo {
    private final XmlTag tag;

    private final IXDefinition def;
    private final IXDefNode defNode;
    private final IXDefNode xdslDefNode;

    private final IXDefNode parentDefNode;
    private final String xdefNs;
    private final String xdslNs;

    private final boolean custom;

    public XmlTagInfo(
            XmlTag tag, XmlTagInfo parentTagInfo, //
            IXDefinition def, IXDefNode defNode, //
            IXDefNode xdslDefNode, //
            String xdefNs, String xdslNs //
    ) {
        this.tag = tag;
        this.def = def;
        this.defNode = defNode;
        this.xdslDefNode = xdslDefNode;

        this.parentDefNode = parentTagInfo != null ? parentTagInfo.getDefNode() : null;
        this.xdefNs = xdefNs;
        this.xdslNs = xdslNs;

        this.custom = parentTagInfo != null //
                      && (parentTagInfo.isCustom() || parentTagInfo.isSupportBody());
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
     * 判断当前节点上的指定属性是否为 XDef 元模型的元属性（即，定义属性名及其类型）
     * <p/>
     * 对于这类属性，仅做类型引用跳转，不做文件或名字引用跳转
     */
    public boolean isDefDeclaredAttr(String attrName) {
        // Note:
        // - 自定义节点（包括 Xpl 类型节点及其子节点）没有元模型
        // - 在 DSL 节点上的属性也不是元属性
        if (isCustom() || isDslNode()) {
            return false;
        }

        // 检查在 *.xdef 节点上的元属性
        String ns = StringHelper.getNamespace(attrName);
        if (StringHelper.isEmpty(ns)) {
            return true;
        }

        // xdef.xdef 中 xdef 名字空间的属性均视为元属性
        if (isXDefNode()) {
            return "xdef".equals(ns);
        }
        // xdsl.xdef 中 x 名字空间的属性均视为元属性
        else if (isXDslNode()) {
            return "x".equals(ns);
        }
        // 对于普通的 *.xdef，除 xmlns、xdef、x 名字空间以外的属性，均视为元属性
        return !ns.equals("xdef") && !ns.equals("x") && !ns.equals("xmlns");
    }

    /** 获取当前节点上指定属性的 xdef 定义 */
    public IXDefAttribute getDefAttr(String attrName) {
        DefAttrWithNode attr = getDefAttrInfo(attrName);

        return attr != null ? attr.attr : null;
    }

    /** 获取当前节点上指定属性的类型 */
    public XDefTypeDecl getDefAttrType(String attrName) {
        IXDefAttribute attr = getDefAttr(attrName);

        return attr != null ? attr.getType() : null;
    }

    /** 获取节点注释 */
    public IXDefComment getDefNodeComment() {
        return defNode != null ? defNode.getComment() : null;
    }

    /** 获取指定属性的注释 */
    public IXDefSubComment getDefAttrComment(String attrName) {
        if (isXmlns(attrName)) {
            return null;
        }

        IXDefComment comment = null;
        DefAttrWithNode attr = getDefAttrInfo(attrName);
        if (attr != null) {
            comment = attr.node.getComment();
            attrName = attr.attr.isUnknownAttr() ? "xdef:unknown-attr" : attr.attr.getName();
        }

        return comment != null ? comment.getSubComments().get(attrName) : null;
    }

    private boolean isXmlns(String name) {
        return name.equals("xmlns") || name.startsWith("xmlns:");
    }

    /** 当前节点是否为 DSL 节点 */
    private boolean isDslNode() {
        return !XDslConstants.XDSL_SCHEMA_XDEF.equals(def.resourcePath());
    }

    /** 是否为 xdef.xdef 中的节点 */
    private boolean isXDefNode() {
        return xdefNs != null && !"xdef".equals(xdefNs);
    }

    /** 是否为 xdsl.xdef 中的节点 */
    private boolean isXDslNode() {
        return xdslNs != null && !"x".equals(xdslNs);
    }

    /** 记录属性所在节点 */
    record DefAttrWithNode(IXDefNode node, IXDefAttribute attr) {}

    /** 获取当前节点上指定属性的定义信息 */
    private DefAttrWithNode getDefAttrInfo(String attrName) {
        // 为 xmlns 节点构造属性
        if (isXmlns(attrName)) {
            String attrValue = tag.getAttributeValue(attrName);
            // 忽略 xmlns:biz="biz" 形式的属性
            if (attrName.endsWith(":" + attrValue)) {
                return null;
            }

            XDefTypeDecl type = new XDefTypeDeclParser().parseFromText(null, XDefConstants.STD_DOMAIN_XDEF_REF);

            XDefAttribute attr = new XDefAttribute();
            attr.setName(attrName);
            attr.setType(type);

            return defNode != null ? new DefAttrWithNode(defNode, attr) : null;
        }

        attrName = XDefPsiHelper.normalizeNamespace(attrName, xdefNs, xdslNs);

        // 查找在当前节点上声明的属性
        IXDefAttribute attr = defNode != null ? defNode.getAttribute(attrName) : null;
        if (attr != null) {
            return new DefAttrWithNode(defNode, attr);
        }

        // 查找在对应的 xdsl.xdef 节点上声明的属性
        attr = xdslDefNode != null ? xdslDefNode.getAttribute(attrName) : null;
        if (attr != null) {
            return new DefAttrWithNode(xdslDefNode, attr);
        }

        // 查找当前节点上声明的 xdef:unknown-attr 属性：
        // 在当前 dsl 为 *.xdef（即，x:schema 为 /nop/schema/xdef.xdef）时有效
        attr = defNode != null ? defNode.getAttribute("xdef:unknown-attr") : null;
        if (attr != null) {
            return new DefAttrWithNode(defNode, attr);
        }

        // 针对 xdef.xdef 中的未确定属性：本质上都是 XDefNode 节点上的属性
        if (isXDefNode()) {
            IXDefNode node = def.getXdefUnknownTag();

            return new DefAttrWithNode(node, node.getAttribute(attrName));
        }

        // Note: 在普通 *.xdef 的 IXDefNode 中，
        // 对 xdef:unknown-attr 只记录了类型，并没有 IXDefAttribute 实体，
        // 其处理逻辑见 XDefinitionParser#parseNode
        XDefTypeDecl xdefUnknownAttrType = defNode != null ? defNode.getXdefUnknownAttr() : null;
        if (xdefUnknownAttrType != null) {
            XDefAttribute at = new XDefAttribute() {
                @Override
                public boolean isUnknownAttr() {
                    return true;
                }
            };

            at.setName(attrName);
            at.setType(xdefUnknownAttrType);

            return new DefAttrWithNode(defNode, at);
        }

        return null;
    }
}
