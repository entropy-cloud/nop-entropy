/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.hook.SerializableExtensibleObject;
import io.nop.core.resource.ResourceHelper;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_LOC_A;
import static io.nop.xlang.XLangErrors.ARG_LOC_B;
import static io.nop.xlang.XLangErrors.ARG_REF_NAME;
import static io.nop.xlang.XLangErrors.ARG_TYPE_A;
import static io.nop.xlang.XLangErrors.ARG_TYPE_B;
import static io.nop.xlang.XLangErrors.ERR_XDEF_ATTR_NOT_ALLOW_OVERRIDE_REF;
import static io.nop.xlang.XLangErrors.ERR_XDEF_REF_NOT_ALLOW_CIRCULAR_REFERENCE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_UNKNOWN_DEFINITION_REF;

public class XDefRefResolver {
    private ResolveState resolveState;
    private Set<String> propNs;

    // tell cpd to start ignoring code - CPD-OFF

    /**
     * 在resolve过程中通过上下文缓存识别循环引用
     */
    static class ResolveState {
        static ThreadLocal<ResolveState> s_state = new ThreadLocal<>();

        int refCount;
        Map<String, IXDefNode> refCache = new HashMap<>();
        Set<String> resolving = new HashSet<>();

        static ResolveState get() {
            ResolveState state = s_state.get();
            if (state == null) {
                state = new ResolveState();
                s_state.set(state);
            }
            state.inc();
            return state;
        }

        public void inc() {
            refCount++;
        }

        public boolean dec() {
            refCount--;
            if (refCount == 0) {
                s_state.remove();
                return true;
            }
            return false;
        }
    }
    // resume CPD analysis - CPD-ON

    public void resolve(XDefinition def) {
        Guard.notEmpty(def.resourcePath(), "def.resourcePath");

        // def.toNode().dump();

        resolveState = ResolveState.get();

        try {
            String stdPath = ResourceHelper.getStdPath(def.resourcePath());
            if (!resolveState.resolving.add(stdPath))
                throw new NopException(ERR_XDEF_REF_NOT_ALLOW_CIRCULAR_REFERENCE).param(ARG_REF_NAME, stdPath);

            for (IXDefNode localDef : def.getXdefDefines()) {
                String refPath = XDefHelper.buildFullRefPath(stdPath, localDef.getXdefName());
                resolveState.refCache.put(refPath, localDef);
            }

            resolveRef(def);

            propNs = def.getXdefPropNs();

            for (IXDefNode localDef : def.getXdefDefines()) {
                resolveNode(localDef);
            }

            resolveAttrs(def);

            resolveChildren(def);

            LinkedHashSet<String> refPaths = new LinkedHashSet<>();
            collectRefPaths(def, refPaths);
            def.setAllRefSchemas(refPaths);

            // def.toNode().dump();
        } finally {
            resolveState.dec();
        }
    }

    void collectRefPaths(IXDefNode node, Set<String> refPaths) {
        if (node.getRefNode() != null) {
            collectRefPaths(node.getRefNode(), refPaths);
        }
        refPaths.add(node.resourcePath());
    }

    private void mergeRefNodeProps(IXDefNode def, XDefNode ret) {
        // ret.setTagName(def.getTagName());

        ret.mergeExtPropsIfAbsent((SerializableExtensibleObject) def);

        if (ret.getComment() == null) {
            ret.setComment((XDefComment) def.getComment());
        } else if (def.getComment() != null) {
            // 合并comment
            ret.setComment((XDefComment) def.getComment().applyOverride(ret.getComment()));
        }

        if (ret.getXdefBeanChildName() == null)
            ret.setXdefBeanChildName(def.getXdefBeanChildName());

        // ret.setXdefName(def.getXdefName());
        if (ret.getXdefMandatory() == null)
            ret.setXdefMandatory(def.getXdefMandatory());
        if (ret.getXdefUnknownAttr() == null)
            ret.setXdefUnknownAttr(def.getXdefUnknownAttr());
        if (ret.getXdefBodyType() == null)
            ret.setXdefBodyType(def.getXdefBodyType());
        if (ret.getXdefBeanSubTypeProp() == null)
            ret.setXdefBeanSubTypeProp(def.getXdefBeanSubTypeProp());
        if (ret.getXdefBeanUnknownAttrsProp() == null)
            ret.setXdefBeanUnknownAttrsProp(def.getXdefBeanUnknownAttrsProp());
        if (ret.getXdefBeanUnknownChildrenProp() == null)
            ret.setXdefBeanUnknownChildrenProp(def.getXdefBeanUnknownChildrenProp());
        // ret.setRef(def.getRef());
        if (ret.getXdefAllowMultiple() == null)
            ret.setXdefAllowMultiple(def.getXdefAllowMultiple());
        if (ret.getXdefDeprecated() == null)
            ret.setXdefDeprecated(def.getXdefDeprecated());
        if (ret.getXdefInternal() == null)
            ret.setXdefInternal(def.getXdefInternal());
        if (ret.getXdefUniqueAttr() == null)
            ret.setXdefUniqueAttr(def.getXdefUniqueAttr());
        if (ret.getXdefKeyAttr() == null)
            ret.setXdefKeyAttr(def.getXdefKeyAttr());
        if (ret.getXdefOrderAttr() == null)
            ret.setXdefOrderAttr(def.getXdefOrderAttr());
        if (ret.getXdefBeanBodyType() == null)
            ret.setXdefBeanBodyType(def.getXdefBeanBodyType());
        if (ret.getXdefBeanClass() == null)
            ret.setXdefBeanClass(def.getXdefBeanClass());
        // ret.setBeanProp(def.getBeanProp());
        // ret.setBeanRefProp(def.getBeanRefProp());
        if (ret.getXdefBeanBodyProp() == null)
            ret.setXdefBeanBodyProp(def.getXdefBeanBodyProp());
        if (ret.getXdefBeanTagProp() == null)
            ret.setXdefBeanTagProp(def.getXdefBeanTagProp());
        if (ret.getXdefBeanCommentProp() == null)
            ret.setXdefBeanCommentProp(def.getXdefBeanCommentProp());

        if (ret.getXdefValue() == null)
            ret.setXdefValue(def.getXdefValue());

        // 标记了xdef:unique-attr的节点对应的bean属性名缺省为 tagName + 's'
        if (ret.getXdefUniqueAttr() != null && isDefaultBeanProp(ret)) {
            String beanProp = StringHelper.xmlNameToVarName(ret.getTagName()) + 's';
            ret.setXdefBeanProp(beanProp);
        }
    }

    private boolean isDefaultBeanProp(IXDefNode defNode) {
        if (defNode.getXdefBeanProp() == null)
            return true;

        return defNode.getXdefBeanProp().equals(XDefHelper.buildPropName(propNs, defNode.getTagName()));
    }

    private void resolveNode(IXDefNode defNode) {
        if (defNode == null)
            return;

        if (defNode.isRefResolved())
            return;

        resolveRef(defNode);

        resolveAttrs((XDefNode) defNode);

        resolveChildren((XDefNode) defNode);
    }

    private void resolveAttrs(XDefNode defNode) {
        IXDefNode refNode = getRefNode(defNode);
        if (refNode == null) {
            return;
        }

        defNode.setRefNode(refNode);
        defNode.setRefResolved(true);
        mergeRefNodeProps(refNode, defNode);
        mergeAttrs(refNode, defNode);
    }

    void mergeAttrs(IXDefNode refNode, XDefNode defNode) {
        if (defNode.getXdefUnknownAttr() == null)
            defNode.setXdefUnknownAttr(refNode.getXdefUnknownAttr());

        if (defNode.getAttributes().isEmpty()) {
            defNode.setAttributes((Map) refNode.getAttributes());
        } else {
            Map<String, IXDefAttribute> attrs = new HashMap<>(refNode.getAttributes());
            for (Map.Entry<String, ? extends IXDefAttribute> entry : defNode.getAttributes().entrySet()) {
                String attrName = entry.getKey();
                IXDefAttribute attr = entry.getValue();
                IXDefAttribute old = attrs.get(attrName);
                if (old != null && !old.getType().getStdDomain().equals(attr.getType().getStdDomain())) {
                    throw new NopException(ERR_XDEF_ATTR_NOT_ALLOW_OVERRIDE_REF).param(ARG_ATTR_NAME, attr.getName())
                            .param(ARG_LOC_B, attr.getLocation()).param(ARG_LOC_A, attrs.get(attrName).getLocation())
                            .param(ARG_TYPE_B, attr.getType()).param(ARG_TYPE_A, attrs.get(attrName).getType());
                }

                attrs.put(attrName, attr);
            }
            defNode.setAttributes((Map) attrs);
        }
    }

    private void resolveChildren(XDefNode defNode) {
        if (defNode.getXdefUnknownTag() != null) {
            resolveNode(defNode.getXdefUnknownTag());
        }

        for (IXDefNode childDef : defNode.getChildren().values()) {
            resolveNode(childDef);
        }

        IXDefNode refNode = getRefNode(defNode);
        if (refNode == null) {
            return;
        }

        mergeChildren(refNode, defNode);

        if (defNode.getXdefBeanChildName() == null) {
            if (defNode.getXdefKeyAttr() != null) {
                if (defNode.getChildren().size() == 1) {
                    IXDefNode childDef = defNode.getChildren().values().iterator().next();
                    if (!childDef.isUnknownTag())
                        defNode.setXdefBeanChildName(StringHelper.xmlNameToVarName(childDef.getTagName()));
                }
            } else if (defNode.getXdefUniqueAttr() != null) {
                defNode.setXdefBeanChildName(StringHelper.xmlNameToVarName(defNode.getTagName()));
            }
        }
    }

    void mergeChildren(IXDefNode refNode, XDefNode defNode) {
        if (defNode.getXdefUnknownTag() == null)
            defNode.setXdefUnknownTag((XDefNode) refNode.getXdefUnknownTag());

        if (defNode.getChildren().isEmpty()) {
            defNode.setChildren((Map) refNode.getChildren());
        } else {
            Map<String, IXDefNode> children = new HashMap<>(refNode.getChildren());

            for (Map.Entry<String, ? extends IXDefNode> entry : defNode.getChildren().entrySet()) {
                String tagName = entry.getKey();
                IXDefNode child = entry.getValue();
                IXDefNode refChild = children.get(tagName);
                if (refChild != null) {
                    mergeNode(refChild, (XDefNode) child);
                }

                children.put(tagName, child);
            }
            defNode.setChildren((Map) children);
        }
    }

    private void mergeNode(IXDefNode refNode, XDefNode defNode) {
        mergeRefNodeProps(refNode, defNode);
        mergeAttrs(refNode, defNode);
        mergeChildren(refNode, defNode);
    }

    private void resolveRef(IXDefNode defNode) {
        defNode.setRefResolved(true);
        IXDefNode refNode = getRefNode(defNode);
        if (refNode != null) {
            defNode.setRefNode(refNode);
        }
    }

    private IXDefNode getRefNode(IXDefNode defNode) {
        String ref = defNode.getXdefRef();
        if (ref == null)
            return null;

        IXDefNode refNode = resolveState.refCache.get(ref);
        if (refNode != null)
            return refNode;

        String localRef = defNode.getLocalRef();
        if (localRef != null) {
            throw new NopException(ERR_XDEF_UNKNOWN_DEFINITION_REF).param(ARG_REF_NAME, ref).source(defNode);
        } else {
            IXDefinition refDef;
            try {
                refDef = SchemaLoader.loadXDefinition(ref);
            } catch (NopException e) {
                e.addXplStack(defNode);
                throw e;
            }

            if (refDef == null)
                throw new NopException(ERR_XDEF_UNKNOWN_DEFINITION_REF).param(ARG_REF_NAME, ref).source(defNode);
            refNode = refDef.getRootNode();
        }
        resolveState.refCache.put(ref, refNode);
        return refNode;
    }
}