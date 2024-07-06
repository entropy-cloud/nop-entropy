/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.utils.XNodeAttrComparator;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefBodyType;
import io.nop.xlang.xdef.XDefOverride;
import io.nop.xlang.xdef.XDefTypeDecl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;
import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_ATTR_VALUE;
import static io.nop.xlang.XLangErrors.ARG_EXPECTED;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_NODES;
import static io.nop.xlang.XLangErrors.ARG_NODE_A;
import static io.nop.xlang.XLangErrors.ARG_NODE_B;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ARG_XDEF_NODE_NAME;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_ALLOWED;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_VALUE_IS_EMPTY;
import static io.nop.xlang.XLangErrors.ERR_XDSL_DELTA_NODE_NO_INHERIT;
import static io.nop.xlang.XLangErrors.ERR_XDSL_MISSING_MANDATORY_CHILD;
import static io.nop.xlang.XLangErrors.ERR_XDSL_MULTIPLE_NODE_HAS_SAME_UNIQUE_ATTR_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NODE_CONTENT_NOT_ALLOW_EMPTY;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NODE_DUPLICATE_CHILD;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NODE_NOT_ALLOW_CONTENT;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NODE_UNEXPECTED_TAG_NAME;
import static io.nop.xlang.XLangErrors.ERR_XDSL_UNDEFINED_CHILD_NODE;

/**
 * 检查节点、属性的唯一性和是否非空。副作用是会删除x名字空间下的属性和子节点，并对列表节点按照orderAttr排序
 */
public class XDslValidator {
    private final XDslKeys keys;

    public XDslValidator(XDslKeys keys) {
        this.keys = keys;
    }

    public void validate(XNode node, IXDefNode defNode, boolean checkRootName) {
        validateNode(node, defNode, checkRootName, new HashSet<>());
    }

    private void validateNode(XNode node, IXDefNode defNode, boolean checkRootName, Set<String> checkNs) {
        checkNs = mergeCheckNs(defNode, checkNs);

        if (checkRootName && !defNode.isUnknownTag() && !node.getTagName().equals(defNode.getTagName()))
            throw new NopException(ERR_XDSL_NODE_UNEXPECTED_TAG_NAME).param(ARG_NODE, node)
                    .param(ARG_EXPECTED, defNode.getTagName()).param(ARG_TAG_NAME, node.getTagName());

        checkAttrs(defNode, node, checkNs);

        if (node.hasChild()) {
            for (int i = 0, n = node.getChildCount(); i < n; i++) {
                XNode child = node.child(i);
                if (shouldRemove(child)) {
                    node.removeChildByIndex(i);
                    i--;
                    n--;
                } else {
                    if(!defNode.getChildren().isEmpty()) {
                        // 如果checkNs中指定名字空间需要校验，则不使用unknownAttr来匹配
                        if (!checkNs.isEmpty()
                                && StringHelper.hasNamespace(child.getTagName())) {
                            String ns = StringHelper.getNamespace(child.getTagName());
                            if (checkNs.contains(ns))
                                if (defNode.getChildren().get(child.getTagName()) == null)
                                    throw new NopException(ERR_XDSL_UNDEFINED_CHILD_NODE).param(ARG_NODE, child)
                                            .param(ARG_XDEF_NODE_NAME, defNode.getTagName())
                                            .param(ARG_TAG_NAME, child.getTagName())
                                            .param(ARG_ALLOWED_NAMES, defNode.getChildren().keySet());
                        }
                    }

                    IXDefNode childDef = defNode.getChild(child.getTagName());
                    if (childDef == null) {
                        if (defNode.getXdefValue() == null || !defNode.getXdefValue().isSupportBody()) {
                            if (!isIgnorableChild(child.getTagName())) {
                                throw new NopException(ERR_XDSL_UNDEFINED_CHILD_NODE).param(ARG_NODE, child)
                                        .param(ARG_XDEF_NODE_NAME, defNode.getTagName())
                                        .param(ARG_TAG_NAME, child.getTagName())
                                        .param(ARG_ALLOWED_NAMES, defNode.getChildren().keySet());
                            }
                        }
                        clean(child);
                    } else {
                        if (childDef.getXdefUniqueAttr() == null && defNode.getXdefKeyAttr() == null
                                && defNode.getXdefBodyType() != XDefBodyType.list) {
                            if (node.countChildByTag(child.getTagName()) > 1)
                                throw new NopException(ERR_XDSL_NODE_DUPLICATE_CHILD)
                                        .param(ARG_XDEF_NODE_NAME, defNode.getTagName())
                                        .param(ARG_NODES, node.childrenByTag(child.getTagName()).subList(0, 2))
                                        .param(ARG_TAG_NAME, child.getTagName());
                        }

                        validateNode(child, childDef, true, checkNs);
                    }
                }
            }
        } else {
            // 没有子节点，需要检查内容是否满足非空要求
            XDefTypeDecl value = defNode.getXdefValue();
            if (value == null) {
                if (!node.content().isNull())
                    throw new NopException(ERR_XDSL_NODE_NOT_ALLOW_CONTENT).loc(node.content().getLocation())
                            .param(ARG_NODE, node).param(ARG_TAG_NAME, node.getTagName());
            } else if (value.isMandatory()) {
                if (node.content().isEmpty())
                    throw new NopException(ERR_XDSL_NODE_CONTENT_NOT_ALLOW_EMPTY).param(ARG_NODE, node);
            }
        }

        checkUniqueOrKeyAttr(node, defNode);

        // 检查非空节点
        for (IXDefNode childDef : defNode.getChildren().values()) {
            // 因为node现在没有子节点，所以只要def定义中有必须存在的子节点就抛出异常。
            if (childDef.isMandatory() && !hasChild(childDef, node)) {
                throw new NopException(ERR_XDSL_MISSING_MANDATORY_CHILD).param(ARG_TAG_NAME, childDef.getTagName())
                        .param(ARG_XDEF_NODE_NAME, defNode.getTagName())
                        .param(ARG_NODE, node);
            }
        }

        if (defNode.getXdefOrderAttr() != null) {
            sortChildren(defNode, node);
        }
    }

    private void sortChildren(IXDefNode defNode, XNode node) {
        for (XNode child : node.getChildren()) {
            ValueWithLocation vl = child.attrValueLoc(defNode.getXdefOrderAttr());
            Object value = vl.getValue();
            IXDefNode childDef = defNode.getChild(child.getTagName());
            if (childDef != null) {
                IXDefAttribute attrDef = childDef.getAttribute(defNode.getXdefOrderAttr());
                if (attrDef != null) {
                    if (value == null) {
                        value = attrDef.getType().getDefaultValue();
                    } else {
                        value = attrDef.getType().getStdDataType().convert(value);
                    }
                    child.setAttr(vl.getLocation(), defNode.getXdefOrderAttr(), value);
                }
            }
        }

        node.getChildren().sort(new XNodeAttrComparator(defNode.getXdefOrderAttr()));
    }


    private boolean hasChild(IXDefNode childDef, XNode node) {
        if (childDef.isUnknownTag())
            return node.hasChild();
        return node.hasChild(childDef.getTagName());
    }

    private Set<String> mergeCheckNs(IXDefNode defNode, Set<String> checkNs) {
        if (defNode instanceof IXDefinition) {
            IXDefinition xdef = (IXDefinition) defNode;
            if (xdef.getXdefCheckNs() != null && !xdef.getXdefCheckNs().isEmpty()) {
                Set<String> merged = new HashSet<>(checkNs);
                merged.addAll(xdef.getXdefCheckNs());
                return merged;
            }
        }
        return checkNs;
    }

    /**
     * 删除x名字空间的属性和子节点
     *
     * @param node
     */
    public void clean(XNode node) {
        node.removeAttrsWithPrefix(keys.X_NS_PREFIX);
        if (node.hasAttr(keys.INHERIT))
            throw new NopException(ERR_XDSL_DELTA_NODE_NO_INHERIT).source(node).param(ARG_NODE, node);

        for (int i = 0, n = node.getChildCount(); i < n; i++) {
            XNode child = node.child(i);
            if (shouldRemove(child)) {
                node.removeChildByIndex(i);
                i--;
                n--;
            } else {
                clean(child);
            }
        }
    }

    boolean shouldRemove(XNode node) {
        String tagName = node.getTagName();
        // 删除x名字空间下的节点
        if (tagName.startsWith(keys.X_NS_PREFIX)) {
            return true;
        }
        return node.attrBoolean(keys.ABSTRACT) || XDefOverride.REMOVE.getText().equals(node.attrText(keys.OVERRIDE))
                || node.attrBoolean(keys.VIRTUAL);
    }

    void checkAttrs(IXDefNode defNode, XNode node, Set<String> checkNs) {
        if (node.hasAttr()) {
            Iterator<Map.Entry<String, ValueWithLocation>> it = node.attrValueLocs().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, ValueWithLocation> entry = it.next();
                String name = entry.getKey();
                ValueWithLocation vl = entry.getValue();

                // 如果checkNs中指定名字空间需要校验，则不使用unknownAttr来匹配
                if (!checkNs.isEmpty() && StringHelper.hasNamespace(name)) {
                    String ns = StringHelper.getNamespace(name);
                    if (checkNs.contains(ns))
                        if (defNode.getAttributes().get(name) == null)
                            throw new NopException(ERR_XDSL_ATTR_NOT_ALLOWED).source(vl).param(ARG_NODE, node)
                                    .param(ARG_ATTR_NAME, name)
                                    .param(ARG_ALLOWED_NAMES, defNode.getAttributes().keySet());
                }

                if (name.startsWith(keys.X_NS_PREFIX)) {
                    if (!keys.ATTR_NAMES.contains(name))
                        throw new NopException(ERR_XDSL_ATTR_NOT_ALLOWED).source(vl).param(ARG_NODE, node)
                                .param(ARG_ATTR_NAME, name).param(ARG_ALLOWED_NAMES, keys.ATTR_NAMES);

                    it.remove();
                } else if (!defNode.isAllowUnknownAttr()) {
                    if (defNode.getAttribute(name) == null && !isIgnorableAttr(name))
                        throw new NopException(ERR_XDSL_ATTR_NOT_ALLOWED).source(vl).param(ARG_NODE, node)
                                .param(ARG_ATTR_NAME, name).param(ARG_ALLOWED_NAMES, defNode.getAttributes().keySet());
                }
            }
        }

        // 检查非空属性
        for (IXDefAttribute attr : defNode.getAttributes().values()) {
            // 如果要求属性非空，且没有设置缺省值
            if (attr.getType().isMandatory() && attr.getType().getDefaultValue() == null) {
                String name = attr.getName();
                ValueWithLocation vl = node.attrValueLoc(name);
                if (vl.isEmpty()) {
                    if (attr.getType().getDefaultAttrNames() != null) {
                        if (addDefaultAttrValue(node, name, attr.getType().getDefaultAttrNames()))
                            continue;
                    }
                    throw new NopException(ERR_XDSL_ATTR_VALUE_IS_EMPTY).param(ARG_NODE, node)
                            .param(ARG_TAG_NAME, node.getTagName()).param(ARG_ATTR_NAME, name);
                }
            }
        }
    }

    private boolean addDefaultAttrValue(XNode node, String name, List<String> defaultAttrNames) {
        StringBuilder sb = new StringBuilder();
        boolean hasValue = false;
        SourceLocation loc = null;
        for (int i = 0, n = defaultAttrNames.size(); i < n; i++) {
            if (i != 0)
                sb.append('|');
            String attrName = defaultAttrNames.get(i);
            Object value = node.getAttr(attrName);
            if (value != null) {
                loc = node.attrLoc(attrName);
                sb.append(value);
                hasValue = true;
            }
        }
        String defaultValue = sb.toString();
        if (hasValue) {
            node.setAttr(name, ValueWithLocation.of(loc, defaultValue));
        }
        return hasValue;
    }

    private boolean isIgnorableAttr(String name) {
        if (XLangConstants.XMLNS_NAME.equals(name))
            return true;

        return name.indexOf(':') > 0;
    }

    private boolean isIgnorableChild(String name) {
        return name.indexOf(':') > 0;
    }

    /**
     * 检查节点的uniqueAttr以及keyAttr的有效性
     */
    private void checkUniqueOrKeyAttr(XNode node, IXDefNode defNode) {
        if (defNode == null)
            return;

        String uniqueAttr = defNode.getXdefUniqueAttr();
        if (uniqueAttr != null) {
            // 已经处理过唯一属性
            if (node.uniqueAttr() != null)
                return;

            // IXDefAttribute defAttr = defNode.getAttribute(uniqueAttr);
            XNode parent = node.getParent();
            if (parent != null) {
                checkUniqueAttr(parent.getChildren(), defNode.getTagName(), uniqueAttr);
            }
        }

        String keyAttr = defNode.getXdefKeyAttr();
        if (keyAttr != null) {
            checkKeyAttr(node, defNode, keyAttr);
        }
    }

    private void checkUniqueAttr(List<XNode> nodes, String tagName, String uniqueAttr) {
        Map<String, XNode> map = new HashMap<>();
        for (XNode child : nodes) {
            if (child.getTagName().equals(tagName)) {
                child.uniqueAttr(uniqueAttr);
                String keyValue = child.attrText(uniqueAttr);
                if (StringHelper.isEmpty(keyValue))
                    continue;

                XNode oldChild = map.put(keyValue, child);
                if (oldChild != null) {
                    throw new NopException(ERR_XDSL_MULTIPLE_NODE_HAS_SAME_UNIQUE_ATTR_VALUE)
                            .param(ARG_NODE_A, oldChild).param(ARG_NODE_B, child).param(ARG_ATTR_NAME, uniqueAttr)
                            .param(ARG_ATTR_VALUE, keyValue);
                }
            }
        }
    }

    private void checkKeyAttr(XNode node, IXDefNode defNode, String keyAttr) {
        Map<String, XNode> map = new HashMap<>();
        for (XNode child : node.getChildren()) {

            IXDefNode defChild = defNode.getChild(child.getTagName());
            if (defChild == null)
                continue;

            IXDefAttribute defAttr = defChild.getAttribute(keyAttr);
            if (defAttr == null)
                continue;

            child.uniqueAttr(keyAttr);

            String keyValue = child.attrText(keyAttr);
            if (StringHelper.isEmpty(keyValue))
                continue;

            XNode oldChild = map.put(keyValue, child);
            if (oldChild != null) {
                throw new NopException(ERR_XDSL_MULTIPLE_NODE_HAS_SAME_UNIQUE_ATTR_VALUE).param(ARG_NODE_A, oldChild)
                        .param(ARG_NODE_B, child).param(ARG_ATTR_NAME, keyAttr).param(ARG_ATTR_VALUE, keyValue);
            }
        }
    }
}
