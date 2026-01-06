/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.collections.KeyedList;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNodeValuePosition;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefComment;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefProp;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefBodyType;
import io.nop.xlang.xdef.XDefOverride;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ISchemaNode;
import io.nop.xlang.xmeta.impl.IObjSchemaImpl;
import io.nop.xlang.xmeta.impl.ObjMetaImpl;
import io.nop.xlang.xmeta.impl.ObjMetaRefResolver;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import io.nop.xlang.xmeta.impl.SchemaImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ERR_XDEF_KEYED_LIST_MUST_ASSIGN_BEAN_BODY_TYPE_EXPLICITLY;
import static io.nop.xlang.XLangErrors.ERR_XDEF_LIST_NO_CHILD;
import static io.nop.xlang.XLangErrors.ERR_XDEF_MAP_NO_CHILD;
import static io.nop.xlang.XLangErrors.ERR_XDEF_UNKNOWN_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ERR_XMETA_SCHEMEA_DEFINE_NO_NAME_ATTR;
import static io.nop.xlang.XLangErrors.ERR_XMETA_UNION_SCHEMA_NO_SUB_TYPE_PROP;

/**
 * 将{@link IXDefinition}转换为{@link ObjMetaImpl}结构
 */
public class XDefToObjMeta {

    private final Map<IXDefNode, ISchema> uniqueSchemas = new IdentityHashMap<>();

    /**
     * IXDefNode可能构成递归引用，需要确保每个IXDefNode只生成一个对应的ObjPropMeta对象
     */
    private final Map<IXDefNode, ObjPropMetaImpl> uniqueProps = new IdentityHashMap<>();

    // 从id映射到对应节点
    private Map<String, ISchema> idRefs = new HashMap<>();

    private KeyedList<ISchema> defines = new KeyedList<>(ISchema::getName);

    private int idSeq = 0;

    private boolean resolveRef = true;

    private String xdefPath;

    public XDefToObjMeta resolveRef(boolean resolveRef) {
        this.resolveRef = resolveRef;
        return this;
    }

    public ObjMetaImpl transform(IXDefinition def) {
        this.xdefPath = ResourceHelper.getStdPath(def.resourcePath());
        ObjMetaImpl meta = new ObjMetaImpl();
        meta.setXdslTransform(XLangConstants.MODEL_TYPE_XMETA);
        meta.setXdslSchema(XLangConstants.XDSL_SCHEMA_XMETA);
        meta.setXmlName(def.getTagName());
        meta.setVersion(def.getXdefVersion());
        meta.setParseForHtml(def.getXdefParseForHtml());
        meta.setParseKeepComment(def.getXdefParseKeepComment());
        meta.setParserClass(def.getXdefParserClass());
        meta.setCheckNs(def.getXdefCheckNs());
        meta.setDefaultExtends(def.getXdefDefaultExtends());
        meta.setModelNameProp(def.getXdefModelNameProp());
        meta.setModelVersionProp(def.getXdefModelVersionProp());

        initSchema(meta, def);

        meta.setRefResolved(def.isRefResolved());
        uniqueSchemas.put(def, meta);

        IXDefNode refNode = def.getRefNode();
        if (refNode != null) {
            ISchema refSchema = toSchema(refNode);
            meta.setRefSchema(refSchema);
        }

        defines = toDefines(def.getXdefDefines());
        meta.setDefines(defines);

        transformObjSchema(meta, def);

        calcRefResolved(meta);

        if (resolveRef)
            new ObjMetaRefResolver().resolve(meta);
        return meta;
    }

    private boolean calcRefResolved(ISchema node) {
        if (node == null)
            return true;

        if (node.isRefResolved())
            return true;

        boolean resolved = node.getRef() == null;

        if (node.getProps() != null) {
            for (IObjPropMeta prop : node.getProps()) {
                if (!calcRefResolved(prop.getSchema()))
                    resolved = false;
            }
        }

        if (node.getItemSchema() != null) {
            if (!calcRefResolved(node.getItemSchema()))
                resolved = false;
        }

        if (node.getOneOf() != null) {
            for (ISchema schema : node.getOneOf()) {
                if (!calcRefResolved(schema))
                    resolved = false;
            }
        }

        if (node.getUnknownTagSchema() != null) {
            if (!calcRefResolved(node.getUnknownTagSchema()))
                resolved = false;
        }

        if (resolved)
            node.setRefResolved(true);
        return resolved;
    }

    private KeyedList<ISchema> toDefines(List<? extends IXDefNode> defs) {
        KeyedList<ISchema> ret = new KeyedList<>(defs.size(), ISchemaNode::getName);
        for (IXDefNode def : defs) {
            ISchema schema = toSchema(def);
            if (schema == null || schema.getName() == null)
                throw new NopException(ERR_XMETA_SCHEMEA_DEFINE_NO_NAME_ATTR).source(schema);
            if (def.isExplicitDefine())
                ((SchemaImpl) schema).setExplicitDefine(def.isExplicitDefine());
            ret.add(schema);
        }
        return ret;
    }

    private ISchema toSchema(IXDefNode node) {
        ISchema schema = uniqueSchemas.get(node);
        if (schema != null)
            return schema;

        if (node.isSimpleValue()) {
            return toSimpleSchema(node.getLocation(), node.getXdefValue());
        }

        if (node.isSimpleList()) {
            return toListSchema(newSchema(node), node);
        }

        if (node.isSimpleUnion()) {
            return toUnionSchema(newSchema(node), node, false);
        }

        if (node.isSimpleMap()) {
            return toMapSchema(newSchema(node), node);
        }

        schema = newSchema(node);
        transformObjSchema((IObjSchemaImpl) schema, node);
        return schema;
    }

    private KeyedList<ObjPropMetaImpl> objProps(IXDefNode node) {
        KeyedList<ObjPropMetaImpl> props = new KeyedList<>(IObjPropMeta::key);

        IXDefComment comment = node.getComment();
        for (IXDefAttribute attr : node.getAttributes().values()) {
            ObjPropMetaImpl prop = attrToProp(attr);
            if (comment != null) {
                prop.setDisplayName(comment.getSubDisplayName(attr.getName()));
                prop.setDescription(comment.getSubDescription(attr.getName()));
            }
            props.add(prop);
        }

        if (node.getXdefBeanCommentProp() != null) {
            ObjPropMetaImpl prop = new ObjPropMetaImpl();
            prop.setName(node.getXdefBeanCommentProp());
            prop.setMandatory(true);
            prop.setXmlPos(XNodeValuePosition.comment);
            SchemaImpl schema = new SchemaImpl();
            schema.setLocation(node.getLocation());
            genId(schema);
            schema.setRefResolved(true);
            schema.setType(ReflectionManager.instance().buildGenericType(XDefComment.class));
            prop.setSchema(schema);
            props.add(prop);
        }

        if (node.getXdefBeanTagProp() != null) {
            ObjPropMetaImpl prop = new ObjPropMetaImpl();
            prop.setLocation(node.getLocation());
            prop.setName(node.getXdefBeanTagProp());
            prop.setXmlPos(XNodeValuePosition.tag);
            SchemaImpl schema = new SchemaImpl();
            schema.setLocation(node.getLocation());
            genId(schema);
            schema.setRefResolved(true);
            schema.setType(PredefinedGenericTypes.STRING_TYPE);
            schema.setStdDomain(StdDataType.STRING.getName());
            prop.setSchema(schema);
            props.add(prop);
        }

        if (node.getXdefBeanUnknownAttrsProp() != null && node.getXdefUnknownAttr() != null) {
            ObjPropMetaImpl prop = new ObjPropMetaImpl();
            prop.setName(node.getXdefBeanUnknownAttrsProp());
            prop.setXmlPos(XNodeValuePosition.attr);

            SchemaImpl schema = new SchemaImpl();
            genId(schema);
            schema.setRefResolved(true);
            XDefTypeDecl type = node.getXdefUnknownAttr();
            IGenericType valueType = getGenericType(node.getLocation(), type, false);
            schema.setStdDomain(type.getStdDomain());
            schema.setType(valueType);

            SchemaImpl mapSchema = new SchemaImpl();
            genId(mapSchema);
            mapSchema.setRefResolved(true);
            mapSchema.setType(GenericTypeHelper.buildMapType(valueType));
            mapSchema.setMapValueSchema(schema);

            prop.setSchema(mapSchema);
            props.add(prop);
        }

        if (node.getXdefUnknownTag() != null && node.getXdefBeanUnknownChildrenProp() != null) {
            ObjPropMetaImpl prop = new ObjPropMetaImpl();
            prop.setName(node.getXdefBeanUnknownChildrenProp());
            prop.setXmlPos(XNodeValuePosition.tag);

            ISchema itemSchema = toSchema(node.getXdefUnknownTag());

            SchemaImpl schema = new SchemaImpl();
            genId(schema);
            schema.setRefResolved(true);
            schema.setType(GenericTypeHelper.buildMapType(itemSchema.getType()));
            schema.setMapValueSchema(schema);

            prop.setSchema(schema);
            props.add(prop);
        }

        XDefBodyType bodyType = node.getXdefBodyType();
        if (bodyType == null) {
            for (IXDefNode child : node.getChildren().values()) {
                ObjPropMetaImpl prop = nodeToProp(child);
                props.add(prop);
            }
            if (node.getXdefValue() != null) {
                props.add(valueToProp(node));
            }
        } else {
            props.add(bodyToProp(node));
        }

        for (IXDefProp propNode : node.getXdefProps()) {
            String name = propNode.getName();

            ObjPropMetaImpl propMeta = props.getByKey(name);
            if (propMeta != null) {
                for (String propName : propNode.prop_names()) {
                    Object value = propNode.prop_get(propName);
                    BeanTool.instance().setProperty(propMeta, propName, value);
                }
            }
        }

        return props;
    }

    private void transformObjSchema(IObjSchemaImpl schema, IXDefNode node) {
        if (node.getXdefUnknownAttr() != null && node.getXdefBeanUnknownAttrsProp() == null) {
            XDefTypeDecl unknownAttrType = node.getXdefUnknownAttr();
            ISchema anyAttrSchema = toSimpleSchema(null, unknownAttrType);
            schema.setUnknownAttrSchema(anyAttrSchema);
        }

        // 如果设置了xdef:body-type，则xdef:unknown-tag子节点将作为body的一部分，而不是单独的属性
        if (node.getXdefUnknownTag() != null
                && (node.getXdefBodyType() == null && node.getXdefBeanUnknownChildrenProp() == null)) {
            ISchema anyChildSchema = toSchema(node.getXdefUnknownTag());
            schema.setUnknownTagSchema(anyChildSchema);
        }

        KeyedList<ObjPropMetaImpl> props = objProps(node);
        schema.setProps(props);
    }

    private SchemaImpl newSchema(IXDefNode defNode) {
        SchemaImpl schema = new SchemaImpl();
        schema.setId(defNode.getXdefId());
        schema.setLocation(defNode.getLocation());
        uniqueSchemas.put(defNode, schema);
        schema.setRefResolved(defNode.isRefResolved());

        genId(schema);

        initSchema(schema, defNode);

        IXDefNode refNode = defNode.getRefNode();
        if (refNode != null) {
            ISchema refSchema = toSchema(refNode);
            schema.setRefSchema(refSchema);
        }

        return schema;
    }

    private void initSchema(IObjSchemaImpl schema, IXDefNode node) {
        schema.setName(node.getXdefName());
        schema.setLocation(node.getLocation());
        schema.setExtendsType(node.getXdefBeanExtendsType());
        schema.setImplementsTypes(node.getXdefBeanImplementsTypes());

        if (node.getComment() != null) {
            schema.setDisplayName(node.getComment().getMainDisplayName());
            schema.setDescription(node.getComment().getMainDescription());
        }

        if (node.getXdefBeanClass() != null) {
            schema.setType(GenericTypeHelper.buildRawType(node.getXdefBeanClass()));
        }

        if (node.getXdefRef() != null) {
            schema.setRef(node.getXdefRef());
        }
    }

    private void genId(SchemaImpl schema) {
        // 总是给节点分配一个唯一id
        String id = schema.getId();
        if (id == null)
            id = genId();
        schema.setId(id);
        idRefs.put(id, schema);
    }

    private String genId() {
        do {
            String id = "@" + (idSeq++) + "@" + xdefPath;
            if (!idRefs.containsKey(id))
                return id;
        } while (true);
    }

    private ObjPropMetaImpl nodeToProp(IXDefNode node) {
        ObjPropMetaImpl prop = uniqueProps.get(node);
        if (prop != null)
            return prop;

        prop = new ObjPropMetaImpl();
        uniqueProps.put(node, prop);

        prop.setLocation(node.getLocation());
        boolean simpleValue = node.isSimpleValue();
        prop.setXmlPos(XNodeValuePosition.child);

        if (node.getXdefMandatory() != null) {
            prop.setMandatory(node.getXdefMandatory());
        }
        if (node.getXdefInternal() != null) {
            prop.setInternal(node.getXdefInternal());
        }
        if (node.getXdefDeprecated() != null) {
            prop.setDeprecated(node.getXdefDeprecated());
        }

        prop.setChildName(node.getXdefBeanChildName());

        IXDefComment comment = node.getComment();
        if (comment != null) {
            prop.setDescription(comment.getMainDescription());
            prop.setDisplayName(comment.getMainDisplayName());
        }

        if (simpleValue || node.getXdefUniqueAttr() == null) {
            prop.setXmlName(node.getTagName());
            String name = node.getXdefBeanProp();
            if (name == null)
                name = StringHelper.xmlNameToPropName(node.getTagName());
            prop.setName(name);
            prop.setSchema(toSchema(node));
            if (prop.getSchema().isListSchema() || node.getXdefBodyType() == XDefBodyType.map) {
                IXDefNode child = getUniqueChild(node);
                if (child != null && !child.isUnknownTag()) {
                    if (prop.getChildName() == null) {
                        prop.setChildName(StringHelper.xmlNameToPropName(child.getTagName()));
                    }

                    if (prop.getChildXmlName() == null)
                        prop.setChildXmlName(child.getTagName());
                }
            }
        } else {
            String name = node.getXdefBeanProp();
            if (name == null)
                name = StringHelper.xmlNameToPropName(node.getTagName());
            prop.setName(name);
            prop.setXmlName(node.getTagName());
            prop.setChildXmlName(node.getTagName());
            if (prop.getChildName() == null)
                prop.setChildName(StringHelper.xmlNameToPropName(node.getTagName()));

            ISchema schema = toSchema(node);
            SchemaImpl listSchema = new SchemaImpl();
            genId(listSchema);
            listSchema.setLocation(node.getLocation());
            listSchema.setRefResolved(schema.isRefResolved());
            listSchema.setLocation(node.getLocation());
            listSchema.setKeyProp(StringHelper.xmlNameToPropName(node.getXdefUniqueAttr()));
            IGenericType type = getSchemaType(schema);
            listSchema.setType(GenericTypeHelper.buildListType(type));
            listSchema.setItemSchema(schema);

            prop.setSchema(listSchema);
        }
        return prop;
    }

    private ISchema toSimpleSchema(SourceLocation loc, XDefTypeDecl type) {
        if (type == null)
            return null;
        SchemaImpl schema = new SchemaImpl();
        schema.setRefResolved(true);
        schema.setLocation(loc);
        schema.setDomain(type.getDomain());
        schema.setStdDomain(type.getStdDomain());
        genId(schema);

        IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler(type.getStdDomain());
        if (handler == null)
            throw new NopException(ERR_XDEF_UNKNOWN_STD_DOMAIN).loc(loc).param(ARG_STD_DOMAIN, type.getStdDomain());

        schema.setType(getGenericType(loc, type, type.isMandatory()));
        return schema;
    }

    private IGenericType getGenericType(SourceLocation loc, XDefTypeDecl type, boolean mandatory) {
        IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler(type.getStdDomain());
        if (handler == null)
            throw new NopException(ERR_XDEF_UNKNOWN_STD_DOMAIN).loc(loc).param(ARG_STD_DOMAIN, type.getStdDomain());

        return handler.getGenericType(mandatory, type.getOptions());
    }

    private ISchema toListSchema(SchemaImpl schema, IXDefNode node) {
        String keyAttr = node.getXdefKeyAttr();
        if (keyAttr != null) {
            schema.setKeyProp(StringHelper.xmlNameToPropName(keyAttr));
        }

        String orderAttr = node.getXdefOrderAttr();
        if (orderAttr != null) {
            schema.setOrderProp(StringHelper.xmlNameToPropName(orderAttr));
        }

        if (!node.hasChild()) {
            throw new NopException(ERR_XDEF_LIST_NO_CHILD).param(ARG_NODE, node);
        }

        IXDefNode child = getUniqueChild(node);
        if (child != null) {
            schema.setItemSchema(toSchema(child));
        } else {
            SchemaImpl itemSchema = new SchemaImpl();
            itemSchema.setLocation(node.getLocation());
            schema.setItemSchema(toUnionSchema(itemSchema, node, true));
        }

        if (node.getXdefBeanBodyType() != null) {
            schema.setType(node.getXdefBeanBodyType());
        } else {
            IGenericType itemType = getSchemaType(schema.getItemSchema());
            if (itemType == PredefinedGenericTypes.ANY_TYPE && node.getXdefKeyAttr() != null
                    && isItemHasType(schema.getItemSchema()))
                throw new NopException(ERR_XDEF_KEYED_LIST_MUST_ASSIGN_BEAN_BODY_TYPE_EXPLICITLY).param(ARG_NODE, node);
            schema.setType(GenericTypeHelper.buildListType(itemType));

            if (schema.getItemSchema() != null && schema.getItemSchema().getType() == null)
                ((SchemaImpl) schema.getItemSchema()).setType(itemType);
        }
        return schema;
    }

    boolean isItemHasType(ISchema schema) {
        if (!schema.isUnionSchema()) {
            return getSchemaType(schema) != PredefinedGenericTypes.ANY_TYPE;
        }

        List<ISchema> subs = schema.getOneOf();
        for (ISchema sub : subs) {
            if (getSchemaType(sub) != PredefinedGenericTypes.ANY_TYPE)
                return true;
        }

        return true;
    }

    private ISchema toMapSchema(SchemaImpl schema, IXDefNode node) {
        // String keyAttr = node.getXdefKeyAttr();
        // if (keyAttr != null) {
        // schema.setKeyProp(StringHelper.xmlNameToPropName(keyAttr));
        // }

        // String orderAttr = node.getXdefOrderAttr();
        // if (orderAttr != null) {
        // schema.setOrderProp(StringHelper.xmlNameToPropName(orderAttr));
        // }

        if (!node.hasChild()) {
            throw new NopException(ERR_XDEF_MAP_NO_CHILD).source(node);
        }

        IXDefNode child = getUniqueChild(node);
        if (child != null) {
            schema.setMapValueSchema(toSchema(child));
        } else {
            schema.setMapValueSchema(toUnionSchema(schema, node, true));
        }

        if (node.getXdefBeanBodyType() != null) {
            schema.setType(node.getXdefBeanBodyType());
        } else {
            IGenericType itemType = getSchemaType(schema.getMapValueSchema());
            schema.setType(GenericTypeHelper.buildMapType(itemType));
        }
        return schema;
    }

    private IGenericType getSchemaType(ISchema schema) {
        IGenericType type = schema.getType();
        if (type == null)
            type = PredefinedGenericTypes.ANY_TYPE;
        return type;
    }

    private ISchema toUnionSchema(SchemaImpl schema, IXDefNode node, boolean forItem) {
        String subTypeProp = node.getXdefBeanSubTypeProp();
        if (subTypeProp == null)
            subTypeProp = getBeanTagProp(node);

        if (subTypeProp == null) {
            throw new NopException(ERR_XMETA_UNION_SCHEMA_NO_SUB_TYPE_PROP).param(ARG_NODE, node);
        }

        List<ISchema> subs = new ArrayList<>();
        node.getChildren().values().forEach(child -> {
            ISchema subSchema = toUnionItem(child, node.getXdefBeanSubTypeProp());
            subs.add(subSchema);
        });

        if (!forItem) {
            if (node.getXdefBeanBodyType() != null)
                schema.setType(node.getXdefBeanBodyType());
        }

        if (node.getXdefUnknownTag() != null)
            subs.add(toUnionItem(node.getXdefUnknownTag(), node.getXdefBeanSubTypeProp()));

        if (node.getXdefValue() != null) {
            subs.add(toSimpleSchema(node.getLocation(), node.getXdefValue()));
        }

        schema.setSubTypeProp(subTypeProp);
        schema.setOneOf(subs);
        return schema;
    }

    private ISchema toUnionItem(IXDefNode node, String subTypeProp) {
        ISchema schema = toSchema(node);
        schema.setTypeValue(node.getTagName());

        if (subTypeProp != null && schema.getProp(subTypeProp) == null) {
            ObjPropMetaImpl prop = new ObjPropMetaImpl();
            prop.setLocation(node.getLocation());
            prop.setInternal(true);
            prop.setName(subTypeProp);
            prop.setXmlPos(XNodeValuePosition.tag);
            SchemaImpl typeSchema = new SchemaImpl();
            typeSchema.setLocation(node.getLocation());
            //  genId(typeSchema);
            typeSchema.setRefResolved(true);
            typeSchema.setType(PredefinedGenericTypes.STRING_TYPE);
            typeSchema.setStdDomain(StdDataType.STRING.getName());
            prop.setSchema(typeSchema);
            ((SchemaImpl) schema).addProp(prop);
        }
        return schema;
    }

    private String getBeanTagProp(IXDefNode node) {
        Collection<? extends IXDefNode> children = node.getChildren().values();
        for (IXDefNode child : children) {
            String tagProp = child.getXdefBeanTagProp();
            if (tagProp != null)
                return tagProp;
        }

        if (node.getXdefUnknownTag() != null)
            return node.getXdefUnknownTag().getXdefBeanTagProp();
        return null;
    }

    /**
     * 如果列表元素只有唯一的类型
     */
    private IXDefNode getUniqueChild(IXDefNode node) {
        if (node.getXdefUnknownTag() == null && node.getChildren().size() == 1) {
            return CollectionHelper.first(node.getChildren().values());
        }
        if (node.getChildren().isEmpty())
            return node.getXdefUnknownTag();
        return null;
    }

    private ObjPropMetaImpl valueToProp(IXDefNode node) {
        XDefTypeDecl type = node.getXdefValue();
        ISchema schema = toSimpleSchema(node.getLocation(), type);
        String name = node.getXdefBeanBodyProp();
        if (name == null)
            name = XDslConstants.PROP_BODY;

        ObjPropMetaImpl prop = new ObjPropMetaImpl();
        prop.setLocation(node.getLocation());
        prop.setName(name);
        prop.setSchema(schema);
        if (type != null) {
            prop.setDeprecated(type.isDeprecated());
            prop.setMandatory(type.isMandatory());
            prop.setInternal(type.isInternal());
        }

        prop.setDefaultOverride(XDefOverride.REPLACE);
        prop.setXmlPos(XNodeValuePosition.value);
        return prop;
    }

    private ObjPropMetaImpl bodyToProp(IXDefNode node) {
        // 这里需要新建一个schema节点
        SchemaImpl schema = new SchemaImpl();
        genId(schema);
        schema.setLocation(node.getLocation());

        switch (node.getXdefBodyType()) {
            case list: {
                toListSchema(schema, node);
                break;
            }
            case union: {
                toUnionSchema(schema, node, false);
                break;
            }
            case map: {
                toMapSchema(schema, node);
                break;
            }
            default:
                throw new UnsupportedOperationException();
        }
        String name = node.getXdefBeanBodyProp();
        if (name == null)
            name = XDslConstants.PROP_BODY;

        ObjPropMetaImpl prop = new ObjPropMetaImpl();
        prop.setLocation(node.getLocation());
        prop.setName(name);
        prop.setSchema(schema);
        prop.setXmlPos(XNodeValuePosition.child);

        prop.setChildName(node.getXdefBeanChildName());

        if (prop.getSchema().isListSchema()) {
            IXDefNode child = getUniqueChild(node);
            if (child != null && !child.isUnknownTag()) {
                if (prop.getChildName() == null)
                    prop.setChildName(StringHelper.xmlNameToPropName(child.getTagName()));
                prop.setChildXmlName(child.getTagName());
            }
        }
        return prop;
    }

    private ObjPropMetaImpl attrToProp(IXDefAttribute attr) {
        ObjPropMetaImpl prop = new ObjPropMetaImpl();
        prop.setLocation(attr.getLocation());
        prop.setName(attr.getPropName());
        XDefTypeDecl type = attr.getType();
        prop.setDefaultValue(type.getDefaultValue());
        prop.setXmlPos(XNodeValuePosition.attr);
        prop.setDeprecated(type.isDeprecated());
        prop.setMandatory(type.isMandatory());
        prop.setInternal(type.isInternal());
        prop.setSchema(toSimpleSchema(attr.getLocation(), type));
        prop.setXmlName(attr.getName());
        return prop;
    }
}