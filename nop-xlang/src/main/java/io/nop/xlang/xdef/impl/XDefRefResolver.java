package io.nop.xlang.xdef.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.hook.SerializableExtensibleObject;
import io.nop.xlang.utils.RefResolver;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_LOC_A;
import static io.nop.xlang.XLangErrors.ARG_LOC_B;
import static io.nop.xlang.XLangErrors.ARG_REF_NAME;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ARG_TYPE_A;
import static io.nop.xlang.XLangErrors.ARG_TYPE_B;
import static io.nop.xlang.XLangErrors.ERR_XDEF_ATTR_NOT_ALLOW_OVERRIDE_REF;
import static io.nop.xlang.XLangErrors.ERR_XDEF_CHILD_NOT_ALLOW_OVERRIDE_REF;
import static io.nop.xlang.XLangErrors.ERR_XDEF_UNKNOWN_DEFINITION_REF;

public class XDefRefResolver {
    static ThreadLocal<RefResolver.ResolveState<XDefNode>> s_state = new ThreadLocal<>();

    private Set<String> propNs;

    public void resolve(XDefinition def) {
        propNs = def.getXdefPropNs();

        new RefResolver<XDefNode>().resolve(new RefResolver.IResolveNodeModel<>() {
            @Override
            public String getRef(XDefNode node) {
                return node.getXdefRef();
            }

            @Override
            public XDefNode getRefNode(XDefNode node) {
                return (XDefNode) node.getRefNode();
            }

            @Override
            public boolean isRefResolved(XDefNode node) {
                return node.isRefResolved();
            }

            @Override
            public void setRefNode(XDefNode node, XDefNode refNode) {
                if (node.isRefResolved())
                    return;
                node.setRefNode(refNode);
                node.setRefResolved(true);
            }

            @Override
            public void mergeNode(XDefNode node, XDefNode refNode) {
                if (node.frozen())
                    return;

                mergeDefNode(node, refNode);
            }

            @Override
            public String getResourcePath() {
                return def.resourcePath();
            }

            @Override
            public Map<String, XDefNode> getNamedNodes() {
                Map<String, XDefNode> ret = new HashMap<>();
                def.getXdefDefines().forEach(node -> {
                    ret.put(node.getXdefName(), node);
                });
                return ret;
            }

            @Override
            public void forEachNode(Consumer<XDefNode> consumer) {
                Set<XDefNode> visited = CollectionHelper.newIdentityHashSet();
                def.getXdefDefines().forEach(node -> {
                    if (node.isExplicitDefine())
                        visitNode(node, consumer, visited);
                });
                visitNode(def.getRootNode(), consumer, visited);
            }

            @Override
            public XDefNode loadRefNode(XDefNode node, String refPath) {
                String localRef = XDefHelper.getLocalRef(refPath);
                if (localRef != null) {
                    throw new NopException(ERR_XDEF_UNKNOWN_DEFINITION_REF).param(ARG_REF_NAME, refPath).source(node);
                } else {
                    IXDefinition refDef;
                    try {
                        refDef = SchemaLoader.loadXDefinition(refPath);
                    } catch (NopException e) {
                        e.addXplStack(node);
                        throw e;
                    }

                    if (refDef == null)
                        throw new NopException(ERR_XDEF_UNKNOWN_DEFINITION_REF).param(ARG_REF_NAME, refPath).source(node);
                    return (XDefNode) refDef.getRootNode();
                }
            }
        }, s_state);

        LinkedHashSet<String> refPaths = new LinkedHashSet<>();
        collectRefPaths(def, refPaths);
        def.setAllRefSchemas(refPaths);
    }

    void collectRefPaths(IXDefNode node, Set<String> refPaths) {
        if (node.getRefNode() != null) {
            collectRefPaths(node.getRefNode(), refPaths);
        }
        refPaths.add(node.resourcePath());
    }

    private static void visitNode(XDefNode node, Consumer<XDefNode> consumer, Set<XDefNode> visited) {

        if (!visited.add(node))
            return;

        consumer.accept(node);

        for (XDefNode child : node.getChildren().values()) {
            visitNode(child, consumer, visited);
        }

        if (node.getXdefUnknownTag() != null)
            visitNode(node.getXdefUnknownTag(), consumer, visited);
    }

    private void mergeDefNode(XDefNode defNode, XDefNode refNode) {
        mergeRefNodeProps(refNode, defNode);
        mergeAttrs(refNode, defNode);
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
                if (!allowOverride(old, attr)) {
                    throw new NopException(ERR_XDEF_ATTR_NOT_ALLOW_OVERRIDE_REF).param(ARG_ATTR_NAME, attr.getName())
                            .param(ARG_LOC_B, attr.getLocation()).param(ARG_LOC_A, attrs.get(attrName).getLocation())
                            .param(ARG_TYPE_B, attr.getType()).param(ARG_TYPE_A, attrs.get(attrName).getType());
                }

                attrs.put(attrName, attr);
            }
            defNode.setAttributes((Map) attrs);
        }
    }

    private boolean isDefaultBeanProp(IXDefNode defNode) {
        if (defNode.getXdefBeanProp() == null)
            return true;

        return defNode.getXdefBeanProp().equals(XDefHelper.buildPropName(propNs, defNode.getTagName()));
    }

    private boolean allowOverride(IXDefAttribute old, IXDefAttribute attr) {
        if (old == null)
            return true;
        if (old.getType().getStdDomain().equals("string"))
            return true;
        return old.getType().getStdDomain().equals(attr.getType().getStdDomain());
    }

    void mergeChildren(IXDefNode refNode, XDefNode defNode) {
        if (defNode.getXdefUnknownTag() == null)
            defNode.setXdefUnknownTag((XDefNode) refNode.getXdefUnknownTag());

        if (refNode.getChildren().isEmpty())
            return;

        if (defNode.getChildren().isEmpty()) {
            defNode.setChildren((Map) refNode.getChildren());
        } else {
            Map<String, IXDefNode> children = new LinkedHashMap<>(refNode.getChildren());

            for (Map.Entry<String, ? extends IXDefNode> entry : defNode.getChildren().entrySet()) {
                String tagName = entry.getKey();
                IXDefNode child = entry.getValue();
                IXDefNode refChild = children.get(tagName);
                if (refChild != null) {
                    if (refChild == child)
                        continue;
                    throw new NopException(ERR_XDEF_CHILD_NOT_ALLOW_OVERRIDE_REF)
                            .source(child)
                            .param(ARG_TAG_NAME, child.getTagName());
                }

                children.put(tagName, child);
            }
            defNode.setChildren((Map) children);
        }


    }
}
