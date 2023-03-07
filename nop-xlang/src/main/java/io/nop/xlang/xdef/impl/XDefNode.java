/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef.impl;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefKeys;
import io.nop.xlang.xdef.impl._gen._XDefNode;

import java.util.Map;
import java.util.TreeMap;

public class XDefNode extends _XDefNode implements IXDefNode {

    private IXDefNode refNode;
    // 是否已经根据ref查找到具体的refNode
    private boolean refResolved;

    private boolean explicitDefine;

    public String toString() {
        return getClass().getSimpleName() + "[tagName=" + getTagName() + ",loc=" + getLocation() + "]";
    }

    public XDefComment makeComment() {
        XDefComment comment = getComment();
        if (getComment() == null)
            setComment(new XDefComment());
        return comment;
    }

    public boolean isRefResolved() {
        return refResolved;
    }

    public void setRefResolved(boolean refResolved) {
        this.refResolved = refResolved;
    }

    public IXDefNode getRefNode() {
        return refNode;
    }

    public void setRefNode(IXDefNode refNode) {
        checkAllowChange();
        this.refNode = refNode;
    }

    @Override
    public boolean isValueInherited() {
        if (getXdefValue() == null)
            return false;

        if (refNode != null)
            return refNode.getXdefValue() != null;

        return false;
    }

    @Override
    public boolean isAttrInherited(String attrName) {
        if (refNode != null) {
            return refNode.getAttributes().containsKey(attrName);
        }
        return false;
    }

    @Override
    public boolean isChildInherited(String childName) {
        if (refNode != null) {
            return refNode.getChildren().containsKey(childName);
        }
        return false;
    }

    public boolean isUnknownAttrInherited() {
        if (refNode != null)
            return refNode.getXdefUnknownAttr() != null;
        return false;
    }

    public boolean isUnknownChildInherited() {
        if (refNode != null)
            return refNode.getXdefUnknownTag() != null;
        return false;
    }

    public boolean isExplicitDefine() {
        return explicitDefine;
    }

    public void setExplicitDefine(boolean explicitDefine) {
        this.explicitDefine = explicitDefine;
    }

    @Override
    public XNode toNode(XDefKeys keys, Map<IXDefNode, XNode> nodeRefs) {
        XNode refNode = nodeRefs.get(this);
        if (refNode != null) {
            if (getXdefId() == null) {
                setXdefId(StringHelper.generateUUID());
            }
            // 只有被引用的节点才输出id属性，避免最终输出受到无关信息的干扰
            refNode.setAttr(getLocation(), keys.ID, getXdefId());
            XNode node = XNode.make(isUnknownTag() ? keys.UNKNOWN_TAG : getTagName());
            node.setAttr(keys.REF, getXdefId());
            return node;
        }

        XNode node = XNode.make(isUnknownTag() ? keys.UNKNOWN_TAG : getTagName());
        nodeRefs.put(this, node);

        node.setLocation(getLocation());
        if (isExplicitDefine()) {
            node.setTagName(keys.DEFINE);
        }

        XDefComment comment = getComment();
        if (comment != null) {
            String str = comment.toComment();
            node.setComment(str);
        }

        String xdefName = getXdefName();
        if (xdefName != null)
            node.setAttr(getLocation(), keys.NAME, xdefName);

        String ref = getXdefRef();
        if (ref != null) {
            node.setAttr(getLocation(), keys.REF, ref);
            if (refResolved)
                node.setAttr(getLocation(), keys.REF_RESOLVED, true);
        }

        for (IXDefAttribute attr : new TreeMap<>(this.getAttributes()).values()) {
            node.setAttr(attr.getLocation(), attr.getName(), attr.getType().toString());
        }

        if (this.getXdefUnknownAttr() != null) {
            node.setAttr(null, keys.UNKNOWN_ATTR, getXdefUnknownAttr().toString());
        }

        if (this.getXdefValue() != null) {
            node.setAttr(getLocation(), keys.VALUE, getXdefValue().toString());
        }

        for (IXDefNode childDef : new TreeMap<>(this.getChildren()).values()) {
            XNode child = childDef.toNode(keys, nodeRefs);
            node.appendChild(child);
        }

        if (this.getXdefUnknownTag() != null) {
            XNode child = getXdefUnknownTag().toNode(keys, nodeRefs);
            node.appendChild(child);
        }

        setAttr(node, keys.BODY_TYPE, getXdefBodyType());
        setAttr(node, keys.MANDATORY, getXdefMandatory());
        setAttr(node, keys.ALLOW_MULTIPLE, getXdefAllowMultiple());
        setAttr(node, keys.INTERNAL, getXdefInternal());
        setAttr(node, keys.DEPRECATED, getXdefDeprecated());
        setAttr(node, keys.SUPPORT_EXTENDS, getXdefSupportExtends());

        setAttr(node, keys.BEAN_CLASS, getXdefBeanClass());
        setAttr(node, keys.BEAN_BODY_TYPE, getXdefBeanBodyType());
        if (getXdefBeanProp() != null && !getXdefBeanProp().equals(getTagName()))
            setAttr(node, keys.BEAN_PROP, getXdefBeanProp());
        setAttr(node, keys.BEAN_TAG_PROP, getXdefBeanTagProp());
        setAttr(node, keys.BEAN_BODY_PROP, getXdefBeanBodyProp());

        return node;
    }

    private void setAttr(XNode node, String name, Object value) {
        if (value != null) {
            node.setAttr(getLocation(), name, value.toString());
        }
    }

    // protected XDefNode newInstance() {
    // return new XDefNode();
    // }
    //
    // @Override
    // public XDefNode cloneUnresolved() {
    // if (isRefResolved())
    // return this;
    //
    // XDefNode ret = newInstance();
    // ret.setLocation(getLocation());
    // copyExtPropsTo(ret);
    // ret.setAttributes(attributes);
    //
    // if (!children.isEmpty()) {
    //
    // }
    // private Map<String, ? extends IXDefNode> children = Collections.emptyMap();
    //
    // ret.setUnknownAttr(unknownAttr);
    // if (unknownTag != null)
    // ret.setUnknownTag(unknownTag.cloneUnresolved());
    // ret.setTagName(tagName);
    // ret.setDefaultOverride(defaultOverride);
    //
    // ret.setValue(value);
    // ret.setSupportExtends(supportExtends);
    // ret.setMandatory(mandatory);
    // ret.setDeprecated(deprecated);
    // ret.setAllowMultiple(allowMultiple);
    // ret.setInternal(internal);
    //
    // ret.setKeyAttr(keyAttr);
    // ret.setUniqueAttr(uniqueAttr);
    // ret.setOrderAttr(orderAttr);
    //
    // ret.setXdefName(xdefName);
    // ret.setRef(ref);
    // ret.setBodyType(bodyType);
    //
    // ret.setComment(comment);
    //
    // ret.setBeanClass(beanClass);
    // ret.setBeanTagProp(beanTagProp);
    // ret.setBeanBodyProp(beanBodyProp);
    // ret.setBeanProp(beanProp);
    // ret.setBeanRefProp(beanRefProp);
    //
    // ret.setBeanBodyType(beanBodyType);
    //
    // // ret.setRefNode(refNode);
    // // ret.setRefResolved(refResolved);
    //
    // ret.setDefine(define);
    // ret.setId(id);
    // return ret;
    // }
}