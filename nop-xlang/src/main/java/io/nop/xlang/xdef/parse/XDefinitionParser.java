/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.type.IGenericType;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.feature.XModelInclude;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefBodyType;
import io.nop.xlang.xdef.XDefKeys;
import io.nop.xlang.xdef.XDefOverride;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.impl.XDefAttribute;
import io.nop.xlang.xdef.impl.XDefComment;
import io.nop.xlang.xdef.impl.XDefHelper;
import io.nop.xlang.xdef.impl.XDefNode;
import io.nop.xlang.xdef.impl.XDefProp;
import io.nop.xlang.xdef.impl.XDefRefResolver;
import io.nop.xlang.xdef.impl.XDefinition;
import io.nop.xlang.xdsl.AbstractDslParser;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xdsl.XDslExtendPhase;
import io.nop.xlang.xdsl.XDslExtendResult;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.XDslParseHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_ID;
import static io.nop.xlang.XLangErrors.ARG_LOC_A;
import static io.nop.xlang.XLangErrors.ARG_LOC_B;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_REF_NAME;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ERR_XDEF_DUPLICATE_CHILD;
import static io.nop.xlang.XLangErrors.ERR_XDEF_DUPLICATE_LOCAL_REF;
import static io.nop.xlang.XLangErrors.ERR_XDEF_INTERNAL_REF_NODE_NOT_ALLOW_ATTRS;
import static io.nop.xlang.XLangErrors.ERR_XDEF_LIST_NODE_NOT_ALLOW_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_MAP_NODE_NOT_ALLOW_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_REF_ONLY_ALLOW_ON_OBJ_NODE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_SET_NODE_CHILD_NO_ATTR;
import static io.nop.xlang.XLangErrors.ERR_XDEF_UNIQUE_ATTR_NOT_ALLOW_ON_XDEF_ANY_TAG;
import static io.nop.xlang.XLangErrors.ERR_XDEF_UNIQUE_ATTR_VALUE_MUST_BE_NODE_ATTR;
import static io.nop.xlang.XLangErrors.ERR_XDEF_UNKNOWN_DEFINITION_REF;
import static io.nop.xlang.xdsl.XDslParseHelper.checkAttrNames;
import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrBoolean;
import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrClassName;
import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrClassNameSet;
import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrDefType;
import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrEnumValue;
import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrGenericType;
import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrGenericTypes;
import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrLocalRefName;
import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrPropName;
import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrVPath;
import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrXmlName;
import static io.nop.xlang.xdsl.XDslParseHelper.requireAttrDefType;

public class XDefinitionParser extends AbstractDslParser<XDefinition> {

    private XDefKeys keys;
    // 从xdef:name映射到对应节点
    private Map<String, XDefNode> localRefs = new HashMap<>();

    // 从xdef:id映射到对应节点
    private Map<String, XDefNode> idRefs = new HashMap<>();

    private int idSeq = 0;

    private boolean resolveRef = true;

    private Set<String> propNs;
    private XDslKeys dslKeys;

    public XDefinitionParser() {
        setRequiredSchema(XLangConstants.XDSL_SCHEMA_XDEF);
    }

    public XDefinitionParser resolveRef(boolean value) {
        this.resolveRef = value;
        return this;
    }

    @Override
    protected XDefinition doParseResource(IResource resource) {
        XNode node = XModelInclude.instance().keepComment(true).loadActiveNodeFromResource(resource);
        setResourcePath(resource.getPath());
        if (!resource.getStdPath().equals(XDslConstants.XDSL_SCHEMA_XDEF)) {
            XDslExtendResult extendResult = getModelLoader().loadFromResource(resource, getRequiredSchema(),
                    XDslExtendPhase.validate);
            compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
            setXdef(extendResult.getXdef());
            node = extendResult.getNode();
            // XDslKeys keys = XDslKeys.of(node);
            // String schemaPath = node.attrText(keys.SCHEMA);
            // if (StringHelper.isEmpty(schemaPath))
            // throw new NopException(ERR_XDSL_NO_SCHEMA).param(ARG_NODE, node);
            // IXDefinition def = SchemaLoader.loadXDefinition(schemaPath);
            //
            // if (!def.getAllRefSchemas().contains(getRequiredSchema()))
            // throw new NopException(ERR_XDSL_NOT_REQUIRED_SCHEMA)
            // .param(ARG_REQUIRED_SCHEMA, getRequiredSchema())
            // .param(ARG_SCHEMA_PATH, schemaPath);
            // new XDslValidator(keys).validate(node, def.getRootNode(), true);
        }
        return doParseNode(node);
    }

    @Override
    protected XDefinition doParseNode(XNode node) {
        XDefKeys keys = XDefKeys.of(node);
        this.keys = keys;
        this.dslKeys = XDslKeys.of(node);
        // checkAttrNames(node, keys.NS, keys.ROOT_ATTR_NAMES);

        String version = intern(node.attrText(keys.VERSION));
        boolean parseKeepComment = node.attrBoolean(keys.PARSE_KEEP_COMMENT);
        boolean parseForHtml = node.attrBoolean(keys.PARSE_FOR_HTML);
        String parserClass = parseAttrClassName(node, keys.PARSER_CLASS);
        String defaultExtends = parseAttrVPath(node, keys.DEFAULT_EXTENDS);
        Set<String> transformerClass = parseAttrClassNameSet(node, keys.TRANSFORMER_CLASS);
        Set<String> checkNs = node.attrCsvSet(keys.CHECK_NS);
        if (checkNs == null)
            checkNs = Collections.emptySet();

        String xdefBase = node.attrText(keys.BASE);
        String xdefModelNameProp = node.attrText(keys.MODEL_NAME_PROP);
        String xdefModelVersionProp = node.attrText(keys.MODEL_VERSION_PROP);

        Set<String> propNs = node.attrCsvSet(keys.PROP_NS);
        if (propNs == null)
            propNs = Collections.emptySet();

        this.propNs = propNs;

        String beanPackage = parseAttrClassName(node, keys.BEAN_PACKAGE);
        if (beanPackage == null) {
            // 根据xdef:class设置猜测xdef:bean-package
            String beanClass = parseAttrClassName(node, keys.BEAN_CLASS);
            if (beanClass != null)
                beanPackage = StringHelper.packageName(beanClass);
            if (StringHelper.isEmpty(beanPackage))
                beanPackage = null;
        }

        XDefinition def = new XDefinition();
        def.setLocation(node.getLocation());

        collectLocalRefs(def, node);

        def.setDefKeys(keys);
        def.setXdefVersion(version);
        def.setXdefParseKeepComment(parseKeepComment);
        def.setXdefParseForHtml(parseForHtml);
        def.setXdefParserClass(parserClass);
        def.setXdefTransformerClass(transformerClass);
        def.setXdefDefaultExtends(defaultExtends);
        def.setXdefCheckNs(checkNs);
        def.setXdefPropNs(propNs);
        def.setXdefBeanPackage(beanPackage);
        def.setXdefBase(xdefBase);
        def.setXdefModelNameProp(xdefModelNameProp);
        def.setXdefModelVersionProp(xdefModelVersionProp);

        XNode preParse = node.uniqueChild(keys.PRE_PARSE);
        if (preParse != null) {
            def.setXdefPreParse(getCompileTool().compileTagBody(preParse));
        }

        XNode postParse = node.uniqueChild(keys.POST_PARSE);
        if (postParse != null) {
            def.setXdefPostParse(getCompileTool().compileTagBody(postParse));
        }

        parseDefinitions(node, beanPackage);

        parseNode(def, node, true, def.getXdefBeanPackage(), null);

        def.setXdefDefines(new ArrayList<>(localRefs.values()));

        calcRefResolved(def);

        // def.toNode().dump();
        if (resolveRef)
            new XDefRefResolver().resolve(def);
        return def;
    }

    private void collectLocalRefs(XDefNode defNode, XNode node) {
        String xdefName = parseAttrLocalRefName(node, keys.NAME);
        if (!StringHelper.isEmpty(xdefName)) {
            if (defNode == null)
                defNode = new XDefNode();
            defNode.setLocation(node.getLocation());
            defNode.setXdefName(xdefName);
            XDefNode oldChild = localRefs.put(xdefName, defNode);
            if (oldChild != null)
                throw new NopException(ERR_XDEF_DUPLICATE_LOCAL_REF).param(ARG_REF_NAME, xdefName)
                        .param(ARG_LOC_A, oldChild.getLocation()).param(ARG_LOC_B, defNode.getLocation());
        }

        String id = node.attrText(keys.ID);
        if (id != null) {
            if (defNode == null) {
                defNode = new XDefNode();
                defNode.setLocation(node.getLocation());
            }
            defNode.setXdefId(id);

            XDefNode oldChild = idRefs.put(defNode.getXdefId(), defNode);
            if (oldChild != null)
                throw new NopException(ERR_XDEF_DUPLICATE_LOCAL_REF).param(ARG_ID, id)
                        .param(ARG_LOC_A, oldChild.getLocation()).param(ARG_LOC_B, defNode.getLocation());
        }

        for (XNode child : node.getChildren()) {
            collectLocalRefs(null, child);
        }
    }

    private XDefComment parseComment(XNode node) {
        return XDefComment.parseComment(node);
    }

    private XDefNode parseNode(XNode node, boolean root, String beanPackage, String keyAttr) {
        String ref = node.attrText(keys.REF);
        if (ref != null) {
            // 如果是指向xdef:id，则表示循环引用，直接返回
            XDefNode refNode = idRefs.get(ref);
            if (refNode != null)
                return refNode;
        }
        XDefNode defNode = newDefNode(node);
        return parseNode(defNode, node, root, beanPackage, keyAttr);
    }

    private XDefNode parseNode(XDefNode defNode, XNode node, boolean root, String beanPackage, String parentKeyAttr) {
        if (root) {
            checkAttrNames(node, keys.NS, keys.ROOT_ATTR_NAMES);
        } else {
            checkAttrNames(node, keys.NS, keys.ATTR_NAMES);
        }

        defNode.setLocation(node.getLocation());

        // 总是给节点分配一个唯一id
        String id = node.attrText(keys.ID);
        if (id == null)
            id = genId();
        defNode.setXdefId(id);
        idRefs.put(id, defNode);

        XDefComment comment = parseComment(node);
        defNode.setComment(comment);
        if (node.getTagName().equals(keys.UNKNOWN_TAG)) {
            defNode.setTagName("*");
        } else {
            defNode.setTagName(node.getTagName());
        }

        String ref = node.attrText(keys.REF);
        if (ref != null) {
            if (ref.startsWith("@")) {
                if (node.getAttrs().size() != 1)
                    throw new NopException(ERR_XDEF_INTERNAL_REF_NODE_NOT_ALLOW_ATTRS).param(ARG_NODE, node);
            } else if (!ref.startsWith("#") && ref.indexOf('.') > 0) {
                SourceLocation loc = node.attrLoc(keys.REF);
                if (loc != null) {
                    ref = StringHelper.absolutePath(loc.getPath(), ref);
                }
            }
        }

        String beanClass = parseAttrClassName(node, keys.BEAN_CLASS);
        beanClass = StringHelper.fullClassName(beanClass, beanPackage);

        XDefTypeDecl value = parseAttrDefType(node, keys.VALUE);

        Boolean supportExtends = parseAttrBoolean(node, keys.SUPPORT_EXTENDS, null);

        String beanTagProp = parseAttrPropName(node, keys.BEAN_TAG_PROP);
        String beanBodyProp = parseAttrPropName(node, keys.BEAN_BODY_PROP);
        // String beanChildrenProp = parseAttrPropName(node, keys.BEAN_CHILD_NAME);
        String beanProp = parseAttrPropName(node, keys.BEAN_PROP);
        String beanCommentProp = parseAttrPropName(node, keys.BEAN_COMMENT_PROP);
        IGenericType beanBodyType = parseAttrGenericType(node, keys.BEAN_BODY_TYPE, getRawTypeResolver());
        String beanChildName = parseAttrPropName(node, keys.BEAN_CHILD_NAME);
        String beanSubTypeProp = parseAttrPropName(node, keys.BEAN_SUB_TYPE_PROP);
        String beanUnknownAttrsProp = parseAttrPropName(node, keys.BEAN_UNKNOWN_ATTRS_PROP);
        String beanUnknownChildrenProp = parseAttrPropName(node, keys.BEAN_UNKNOWN_CHILDREN_PROP);

        // String beanRefProp = parseAttrPropName(node, keys.BEAN_REF_PROP);
        // String beanAnyAttrProp = parseAttrPropName(node, keys.BEAN_ANY_ATTR_PROP);
        // String beanAnyChildProp = parseAttrPropName(node, keys.BEAN_ANY_CHILD_PROP);

        XDefBodyType bodyType = parseAttrEnumValue(node, keys.BODY_TYPE, XDefBodyType.class, XDefBodyType::fromText);
        String keyAttr = parseAttrXmlName(node, keys.KEY_ATTR);
        String uniqueAttr = parseAttrXmlName(node, keys.UNIQUE_ATTR);
        String orderAttr = parseAttrXmlName(node, keys.ORDER_ATTR);
        String xdefName = parseAttrLocalRefName(node, keys.NAME);
        Boolean allowMultiple = node.attrBoolean(keys.ALLOW_MULTIPLE, null);
        if (allowMultiple == null && (parentKeyAttr != null || uniqueAttr != null))
            allowMultiple = true;

        Boolean mandatory = node.attrBoolean(keys.MANDATORY, null);

        XDefOverride defaultOverride = parseAttrEnumValue(node, keys.DEFAULT_OVERRIDE, XDefOverride.class,
                XDefOverride::fromText);
        Boolean internal = node.attrBoolean(keys.INTERNAL, null);
        Boolean deprecated = node.attrBoolean(keys.DEPRECATED, null);
        XDefTypeDecl unknownAttr = parseAttrDefType(node, keys.UNKNOWN_ATTR);

        IGenericType beanExtendsType = parseAttrGenericType(node, keys.BEAN_EXTENDS_TYPE, getRawTypeResolver());
        List<IGenericType> beanImplementsTypes = parseAttrGenericTypes(node, keys.BEAN_IMPLEMENTS_TYPES,
                getRawTypeResolver());

        if (beanClass == null && beanPackage != null && xdefName != null) {
            beanClass = StringHelper.fullClassName(xdefName, beanPackage);
        }

        defNode.setXdefBeanExtendsType(beanExtendsType);
        defNode.setXdefBeanImplementsTypes(beanImplementsTypes);
        defNode.setXdefBeanClass(beanClass);
        defNode.setXdefBeanProp(beanProp);
        defNode.setXdefBeanBodyProp(beanBodyProp);
        defNode.setXdefBeanTagProp(beanTagProp);
        defNode.setXdefBeanCommentProp(beanCommentProp);
        defNode.setXdefBeanSubTypeProp(beanSubTypeProp);
        defNode.setXdefBeanUnknownAttrsProp(beanUnknownAttrsProp);
        defNode.setXdefBeanUnknownChildrenProp(beanUnknownChildrenProp);
        // defNode.setBeanChildrenProp(beanChildrenProp);
        defNode.setXdefBeanBodyType(beanBodyType);
        // defNode.setBeanAnyAttrProp(beanAnyAttrProp);
        // defNode.setBeanAnyChildProp(beanAnyChildProp);
        defNode.setXdefBeanChildName(beanChildName);

        if (value != null && value.isSupportBody() && supportExtends == null)
            supportExtends = true;

        if (ref != null && supportExtends == null)
            supportExtends = true;

        defNode.setXdefSupportExtends(supportExtends);

        boolean refResolved = node.attrBoolean(keys.REF_RESOLVED);

        if (ref != null && !refResolved && XDefHelper.isLocalRef(ref)) {
            if (!localRefs.containsKey(XDefHelper.getLocalRef(ref)))
                throw new NopException(ERR_XDEF_UNKNOWN_DEFINITION_REF).param(ARG_NODE, node).param(ARG_REF_NAME, ref);
        }

        if (ref != null)
            ref = XDefHelper.buildFullRefPath(getResourceStdPath(), ref);

        defNode.setXdefBodyType(bodyType);
        defNode.setXdefKeyAttr(keyAttr);
        defNode.setXdefUniqueAttr(uniqueAttr);
        defNode.setXdefOrderAttr(orderAttr);
        defNode.setXdefName(xdefName);
        defNode.setXdefRef(ref);
        defNode.setRefResolved(refResolved);

        defNode.setXdefValue(value);

        defNode.setXdefAllowMultiple(allowMultiple);
        defNode.setXdefDeprecated(deprecated);
        defNode.setXdefInternal(internal);
        defNode.setXdefMandatory(mandatory);
        defNode.setXdefDefaultOverride(defaultOverride);

        if (unknownAttr != null) {
            defNode.setXdefUnknownAttr(unknownAttr);
        }

        Map<String, XDefAttribute> attrs = parseAttrs(node, root);
        defNode.setAttributes(attrs);
        Map<String, XDefNode> children = parseChildren(node, beanPackage, keyAttr);
        defNode.setChildren(children);

        // 暂时先取消attr和child名称的重复性检查
//        if (!attrs.isEmpty()) {
//            children.values().forEach(child -> {
//                if (attrs.containsKey(child.getTagName()))
//                    throw new NopException(ERR_XDEF_TAG_NAME_CONFLICT_WITH_ATTR_NAME).source(child).param(ARG_TAG_NAME,
//                            child.getTagName());
//            });
//        }

        parseXdefChildren(node, defNode);

        XNode unknownTag = node.childByTag(keys.UNKNOWN_TAG);
        if (unknownTag != null) {
            // Note: xdef:unknown-tag 为当前节点的子节点，
            // 所以，应该向 parseNode 函数传递当前节点属性 xdef:key-attr 的值
            defNode.setXdefUnknownTag(parseNode(unknownTag, false, beanPackage, keyAttr));
        }

        // 标记了xdef:unique-attr的节点对应的bean属性名缺省为 tagName + 's'
        if (beanProp == null && uniqueAttr != null) {
            beanProp = StringHelper.xmlNameToVarName(defNode.getTagName()) + 's';
            defNode.setXdefBeanProp(beanProp);
        }

        if (beanBodyProp == null && !defNode.isSimple()
                && (defNode.getXdefBodyType() != null || defNode.getXdefValue() != null)) {
            beanBodyProp = CoreConstants.PROP_BODY;
            defNode.setXdefBeanBodyProp(beanBodyProp);
        }

        if (defNode.getXdefBodyType() == null) {
            for (IXDefNode child : defNode.getChildren().values()) {
                String childProp = child.getXdefBeanProp();
                if (childProp == null) {
                    childProp = buildPropName(child.getTagName());
                    ((XDefNode) child).setXdefBeanProp(childProp);
                }
            }
        }

        validateNode(defNode, node);
        return defNode;
    }

    private void parseXdefChildren(XNode node, XDefNode defNode) {
        node.forEachChild(child -> {
            if (child.getTagName().equals(keys.PROP)) {
                XDefProp prop = new XDefProp();
                prop.setLocation(child.getLocation());

                child.forEachAttr((name, vl) -> {
                    if (name.equals("name")) {
                        prop.setName(vl.asString());
                    } else {
                        prop.prop_set(name, vl.asString());
                    }
                });
                defNode.addXdefProp(prop);
            }
        });
    }

    private String genId() {
        do {
            String id = "@" + (idSeq++) + "@" + getResourcePath();
            if (!idRefs.containsKey(id))
                return id;
        } while (true);
    }

    private boolean calcRefResolved(IXDefNode node) {
        if (node.isRefResolved())
            return true;

        boolean resolved = true;
        if (node.getXdefRef() != null) {
            resolved = false;
        }

        for (IXDefNode child : node.getChildren().values()) {
            if (!calcRefResolved(child))
                resolved = false;
        }

        if (node.getXdefUnknownTag() != null) {
            if (!calcRefResolved(node.getXdefUnknownTag()))
                resolved = false;
        }

        if (resolved)
            node.setRefResolved(true);
        return resolved;
    }

    private XDefNode newDefNode(XNode node) {
        String xdefName = parseAttrLocalRefName(node, keys.NAME);
        // 如果xdef:name属性不为空，则此前必然已经收集到localRefs集合中
        if (!StringHelper.isEmpty(xdefName)) {
            return localRefs.get(xdefName);
        }
        return new XDefNode();
    }

    private void validateNode(IXDefNode defNode, XNode node) {
        if (defNode.getXdefRef() != null && defNode.isSimple()) {
            if (defNode.getXdefBodyType() != null && !defNode.isRefResolved())
                throw new NopException(ERR_XDEF_REF_ONLY_ALLOW_ON_OBJ_NODE).param(ARG_NODE, node);
        }

        if (defNode.getXdefBodyType() == XDefBodyType.list) {
            if (defNode.getXdefValue() != null)
                throw new NopException(ERR_XDEF_LIST_NODE_NOT_ALLOW_VALUE).param(ARG_NODE, node);

            if (defNode.getXdefKeyAttr() != null) {
                for (IXDefNode child : defNode.getChildren().values()) {
                    if (!hasRefKeyAttr(child, defNode.getXdefKeyAttr())) {
                        throw new NopException(ERR_XDEF_SET_NODE_CHILD_NO_ATTR)
                                .param(ARG_ATTR_NAME, defNode.getXdefKeyAttr())
                                .param(ARG_NODE, node.childByTag(child.getTagName()));
                    }
                }
            }
        } else if (defNode.getXdefBodyType() == XDefBodyType.map) {
            if (defNode.getXdefValue() != null)
                throw new NopException(ERR_XDEF_MAP_NODE_NOT_ALLOW_VALUE).param(ARG_NODE, node);

            if (defNode.getXdefKeyAttr() != null) {
                for (IXDefNode child : defNode.getChildren().values()) {
                    if (child.getAttributes().get(defNode.getXdefKeyAttr()) == null)
                        throw new NopException(ERR_XDEF_SET_NODE_CHILD_NO_ATTR)
                                .param(ARG_ATTR_NAME, defNode.getXdefKeyAttr())
                                .param(ARG_NODE, node.childByTag(child.getTagName()));
                }

                if (defNode.getXdefUnknownTag() != null) {
                    if (defNode.getXdefUnknownTag().getAttributes().get(defNode.getXdefKeyAttr()) == null) {
                        throw new NopException(ERR_XDEF_SET_NODE_CHILD_NO_ATTR)
                                .param(ARG_ATTR_NAME, defNode.getXdefKeyAttr())
                                .param(ARG_NODE, node.childByTag(defNode.getXdefUnknownTag().getTagName()));
                    }
                }
            }
        }

        if (defNode.getXdefUniqueAttr() != null) {
            if (defNode.isUnknownTag())
                throw new NopException(ERR_XDEF_UNIQUE_ATTR_NOT_ALLOW_ON_XDEF_ANY_TAG).param(ARG_NODE, node);

            Map<String, ? extends IXDefAttribute> attrs = defNode.getAttributes();
            if (attrs == null || !attrs.containsKey(defNode.getXdefUniqueAttr()))
                throw new NopException(ERR_XDEF_UNIQUE_ATTR_VALUE_MUST_BE_NODE_ATTR).param(ARG_NODE, node)
                        .param(ARG_ATTR_NAME, defNode.getXdefUniqueAttr());
        }
    }

    private boolean hasRefKeyAttr(IXDefNode childDef, String attrName) {
        if (childDef.getAttributes().get(attrName) != null)
            return true;

        String ref = childDef.getLocalRef();
        if (ref == null)
            return false;

        IXDefNode refNode = localRefs.get(ref);
        if (refNode == null)
            return false;
        return refNode.getAttributes().get(attrName) != null;
    }

    private Map<String, XDefAttribute> parseAttrs(XNode node, boolean root) {
        if (!node.hasAttr())
            return Collections.emptyMap();

        Map<String, XDefAttribute> attrs = new LinkedHashMap<>();
        node.forEachAttr((name, v) -> {
            if (StringHelper.startsWithNamespace(name, keys.NS))
                return;
            if (root) {
                if (StringHelper.startsWithNamespace(name, XDslConstants.XMLNS_NAME))
                    return;
                if (name.startsWith(dslKeys.X_NS_PREFIX))
                    return;
            }

            XDefTypeDecl type = requireAttrDefType(node, name);
            XDefAttribute attr = new XDefAttribute();
            attr.setLocation(v.getLocation());
            attr.setName(name);
            attr.setPropName(buildPropName(name));
            attr.setType(type);
            attrs.put(name, attr);
        });
        return attrs;
    }

    private String buildPropName(String name) {
        return XDefHelper.buildPropName(propNs, name);
    }

    private Map<String, XDefNode> parseChildren(XNode node, String beanPackage, String keyAttr) {
        if (!node.hasChild())
            return Collections.emptyMap();

        Map<String, XDefNode> children = new HashMap<>();
        node.forEachChild(child -> {
            String name = child.getTagName();
            if (StringHelper.startsWithNamespace(name, keys.NS)) {
                // if (root) {
                // checkTagNames(child, keys.NS, keys.ROOT_CHILD_NAMES);
                // } else {
                // checkTagNames(child, keys.NS, keys.CHILD_NAMES);
                // }
                return;
            }
            XDefNode defNode = parseNode(child, false, beanPackage, keyAttr);
            XDefNode oldChild = children.put(name, defNode);
            if (oldChild != null)
                throw new NopException(ERR_XDEF_DUPLICATE_CHILD).param(ARG_TAG_NAME, name)
                        .param(ARG_LOC_A, oldChild.getLocation()).param(ARG_LOC_B, defNode.getLocation());
        });
        return children;
    }

    private void parseDefinitions(XNode node, String beanPackage) {
        String name = node.getTagName();
        if (name.equals(keys.DEFINE)) {
            XDefNode defNode = parseNode(node, false, beanPackage, null);
            defNode.setExplicitDefine(true);
            if (defNode.getXdefName() == null) {
                throw XDslParseHelper.newAttrIsEmptyError(node, keys.NAME);
            }
        } else {
            for (XNode child : node.getChildren()) {
                parseDefinitions(child, beanPackage);
            }
        }
    }
}
