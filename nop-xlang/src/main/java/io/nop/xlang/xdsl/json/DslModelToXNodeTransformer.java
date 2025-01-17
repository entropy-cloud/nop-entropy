/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl.json;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonEncodeString;
import io.nop.core.lang.xml.IObjectToXNodeTransformer;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.XNodeValuePosition;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.IUnionSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_SCHEMA_KIND;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ARG_SUB_TYPE_PROP;
import static io.nop.xlang.XLangErrors.ARG_SUB_TYPE_VALUE;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_UNKNOWN_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NOT_SUPPORTED_SCHEMA_KIND;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NO_SUB_SCHEMA_DEFINITION;
import static io.nop.xlang.XLangErrors.ERR_XDSL_PROP_LIST_ITEM_NOT_MAP;
import static io.nop.xlang.XLangErrors.ERR_XDSL_PROP_NO_SUB_SCHEMA_DEFINITION;
import static io.nop.xlang.XLangErrors.ERR_XDSL_PROP_VALUE_NOT_LIST;
import static io.nop.xlang.XLangErrors.ERR_XDSL_PROP_VALUE_NOT_MAP;
import static io.nop.xlang.XLangErrors.ERR_XDSL_SUB_TYPE_PROP_IS_EMPTY;
import static io.nop.xlang.XLangErrors.ERR_XDSL_SUB_TYPE_PROP_VALUE_NOT_STRING;

/**
 * 根据XDefinition设置，将json对象转换为XNode结构
 */
public class DslModelToXNodeTransformer implements IObjectToXNodeTransformer {
    static final Logger LOG = LoggerFactory.getLogger(DslModelToXNodeTransformer.class);

    private final IObjMeta objMeta;

    private final Map<Object, XNode> objCache = new IdentityHashMap<>();

    public DslModelToXNodeTransformer(IObjMeta objMeta) {
        this.objMeta = objMeta;
    }

    @Override
    public XNode transformToXNode(Object value) {
        ISchema schema = objMeta.getRootSchema();
        String tagName = objMeta.getXmlName();
        XNode node = transformObj(schema, value);
        if (tagName != null)
            node.setTagName(tagName);

        // 确保属性顺序
        node.removeAttr(XDslKeys.DEFAULT.SCHEMA);
        node.removeAttr("xmlns:x");

        node.setAttr(XDslKeys.DEFAULT.SCHEMA, objMeta.resourcePath());
        node.setAttr("xmlns:x", XLangConstants.XDSL_SCHEMA_XDSL);
        return node;
    }

    private IObjSchema determineObjSchema(IObjSchema schema, Object map) {
        if (schema.isUnionSchema()) {
            IUnionSchema unionSchema = (IUnionSchema) schema;
            String subType = (String) BeanTool.getProperty(map, unionSchema.getSubTypeProp());
            if (subType == null)
                throw new NopException(ERR_XDSL_SUB_TYPE_PROP_IS_EMPTY).param(ARG_SUB_TYPE_PROP, unionSchema.getSubTypeProp());
            IObjSchema subSchema = unionSchema.getSubSchema(subType);
            if (subSchema == null)
                throw new NopException(ERR_XDSL_NO_SUB_SCHEMA_DEFINITION).param(ARG_SUB_TYPE_VALUE, subType)
                        .param(ARG_SUB_TYPE_PROP, unionSchema.getSubTypeProp());
            return subSchema;
        }
        return schema;
    }

    public XNode transformObj(IObjSchema schema, Object map) {
        IObjSchema objSchema = determineObjSchema(schema, map);

        String tagName = CoreConstants.DUMMY_TAG_NAME;
        XNode node = XNode.make(tagName);
        node.setLocation(getLocation(map));

        objCache.put(map, node);

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(map.getClass());
        beanModel.forEachReadWriteProp(propModel -> {
            if (!propModel.isSerializable())
                return;
            String key = propModel.getName();
            if (!shouldIncludeProp(objSchema, key, map, false))
                return;

            Object value = propModel.getPropertyValue(map);
            if (value == null)
                return;

            addToNode(objSchema, node, map, key, value);
        });

        Set<String> propNames = beanModel.getExtPropertyNames(map);
        if (propNames != null) {
            for (String key : propNames) {
                if (!shouldIncludeProp(objSchema, key, map, true))
                    continue;

                Object value = beanModel.getExtProperty(map, key, DisabledEvalScope.INSTANCE);
                if (value == null)
                    continue;
                addToNode(objSchema, node, map, key, value);
            }
        }

        onTransformObj(objSchema, map, node);

        return node;
    }

    protected boolean shouldIncludeProp(IObjSchema schema, String propName, Object bean, boolean extProp) {
        return true;
    }

    protected void onTransformObj(IObjSchema schema, Object map, XNode node) {

    }

    protected void addToNode(IObjSchema schema, XNode node, Object map, String key, Object value) {
        IObjPropMeta propMeta = schema.getProp(key);
        if (propMeta != null) {
            SourceLocation loc = getLocation(map, key, value);
            addProp(node, propMeta, value, loc);
        } else if (schema.getUnknownTagSchema() != null) {
            addObject(node, key, schema.getUnknownTagSchema(), value);
        } else if (schema.getUnknownAttrSchema() != null) {
            node.setAttr(getLocation(map, key, value), key, value);
        } else if (key.indexOf(':') > 0) {
            // 具有名字空间的属性
            if (value instanceof Map<?, ?>) {
                Map<String,Object> mapValue = (Map<String, Object>) value;
                if(mapValue.get(ApiConstants.TREE_BEAN_PROP_TYPE) == null){
                    node.setAttr(key, JsonEncodeString.of(null, value));
                }else {
                    TreeBean bean = TreeBean.createFromJson(mapValue);
                    node.appendChild(XNode.fromTreeBean(bean));
                }
            } else {
                node.setAttr(getLocation(map, key, value), key, value);
            }
        } else {
            if (key.equals("location"))
                return;
            if (LOG.isTraceEnabled())
                LOG.trace("nop.dsl.ignore-unknown-prop:key={},value={},loc={},class={}", key, value,
                        getLocation(map, key, value), map.getClass());
        }
    }

    private String serialize(IObjPropMeta propMeta, Object value) {
        if (value == null)
            return null;

        if (Objects.equals(value, propMeta.getDefaultValue()))
            return null;

        String stdDomain = propMeta.getStdDomain();
        if (stdDomain != null) {
            IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler(stdDomain);
            value = handler.serializeToString(value);
        }

        return value.toString();
    }

    private void addProp(XNode node, IObjPropMeta propMeta, Object value, SourceLocation loc) {
        XNodeValuePosition pos = propMeta.getXmlPos();
        if (pos == null)
            pos = XNodeValuePosition.attr;

        switch (pos) {
            case tag: {
                node.setTagName((String) value);
                break;
            }
            case comment: {
                node.setComment((String) value);
                break;
            }
            case value: {
                XNode valueNode = parseXmlBody(propMeta, loc, value);
                if (valueNode == null) {
                    value = serialize(propMeta, value);
                    if (propMeta.getXmlName() != null) {
                        valueNode = XNode.make(propMeta.getXmlName());
                        valueNode.content(value);
                        node.appendChild(valueNode);
                    } else {
                        node.content(loc, value);
                    }
                } else {
                    if (valueNode.isDummyNode()) {
                        valueNode.setTagName(propMeta.getXmlName());
                    }
                    node.appendChild(valueNode);
                }
                break;
            }
            case attr: {
                String xmlName = propMeta.getXmlName();
                if (xmlName == null)
                    xmlName = propMeta.getName();

                value = serialize(propMeta, value);
                node.setAttr(loc, xmlName, value);
                break;
            }
            case child: {
                XNode valueNode = parseXmlBody(propMeta, loc, value);
                if (valueNode != null) {
                    node.appendChild(valueNode);
                } else {
                    addChild(node, propMeta, value);
                }
                break;
            }
        }
    }

    private XNode parseXmlBody(IObjPropMeta propMeta, SourceLocation loc, Object value) {
        String stdDomain = propMeta.getSchema().getStdDomain();
        if (stdDomain == null)
            return null;

        IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler(stdDomain);
        if (handler == null)
            throw new NopException(ERR_XDEF_UNKNOWN_STD_DOMAIN).loc(loc).param(ARG_STD_DOMAIN, stdDomain);

        if (handler.supportXmlChild()) {
            XNode node = handler.transformToNode(value);
            if (node != null) {
                String xmlName = propMeta.getXmlName();
                if (xmlName == null)
                    xmlName = propMeta.getName();

                if (node.isDummyNode() || node.getTagName().equals(XLangConstants.TAG_C_UNIT)) {
                    node.setTagName(xmlName);
                } else if (!node.getTagName().equals(xmlName)) {
                    XNode parent = XNode.make(xmlName);
                    parent.appendChild(node);
                    return parent;
                }
            }
            return node;
        }
        return null;
    }

    public XNode transformValue(IObjPropMeta propMeta, Object value) {
        if (value == null)
            return null;
        if (propMeta.getSchema() != null && propMeta.getSchema().isListSchema())
            return transformList(propMeta, value);
        XNode node = transformObj(propMeta.getSchema(), value);
        if (propMeta.getXmlName() != null)
            node.setTagName(propMeta.getXmlName());
        return node;
    }

    public XNode transformList(IObjPropMeta propMeta, Object value) {
        if (value == null)
            return null;
        if (!(value instanceof Collection)) {
            throw new NopException(ERR_XDSL_PROP_VALUE_NOT_LIST).source(propMeta)
                    .param(ARG_PROP_NAME, propMeta.getName()).param(ARG_VALUE, value);
        }

        XNode children = XNode.make(CoreConstants.DUMMY_TAG_NAME);
        if (propMeta.getXmlName() != null && !propMeta.getXmlName().equals(propMeta.getChildXmlName()))
            children.setTagName(propMeta.getXmlName());

        Collection<Object> list = (Collection<Object>) value;

        for (Object item : list) {
            if (item == null) {
                // 不允许空值
                item = Collections.emptyMap();
            }

            XNode child = transformListItem(item, propMeta);
            children.appendChild(child);
        }
        return children;
    }

    private void addChild(XNode node, IObjPropMeta propMeta, Object value) {
        ISchema schema = propMeta.getSchema();
        switch (schema.getSchemaKind()) {
            case SIMPLE: {
                XNode child = XNode.make(propMeta.getXmlName());
                child.content(value);
                node.appendChild(child);
                break;
            }

            case LIST: {
                if (!(value instanceof Collection)) {
                    throw new NopException(ERR_XDSL_PROP_VALUE_NOT_LIST).source(propMeta)
                            .param(ARG_PROP_NAME, propMeta.getName()).param(ARG_VALUE, value);
                }

                XNode children = node;
                Collection<Object> list = (Collection<Object>) value;
                if (propMeta.getXmlName() != null && !propMeta.getXmlName().equals(propMeta.getChildXmlName())) {
                    children = XNode.make(propMeta.getXmlName());
                }
                for (Object item : list) {
                    if (item == null) {
                        // 不允许空值
                        item = Collections.emptyMap();
                    }

                    XNode child = transformListItem(item, propMeta);
                    children.appendChild(child);
                }

                // 如果不是非空属性，则不添加空集合对应的节点
                if (children != node && (propMeta.isMandatory() || children.getChildCount() > 0)) {
                    node.appendChild(children);
                }
                break;
            }
            case OBJ: {
                addObject(node, propMeta.getXmlName(), schema, value);
                break;
            }
            case MAP: {
                if (!(value instanceof Map)) {
                    throw new NopException(ERR_XDSL_PROP_VALUE_NOT_MAP).source(propMeta)
                            .param(ARG_PROP_NAME, propMeta.getName()).param(ARG_VALUE, value);
                }
                addMap(node, propMeta, (Map<String, Object>) value);
                break;
            }
            case UNION: {
                if (!(value instanceof Map))
                    throw new NopException(ERR_XDSL_PROP_LIST_ITEM_NOT_MAP).source(propMeta)
                            .param(ARG_PROP_NAME, propMeta.getName()).param(ARG_VALUE, value);
                XNode child = transformUnion(value, schema, propMeta.getName());
                node.appendChild(child);
            }
            default: {
                throw new NopException(ERR_XDSL_NOT_SUPPORTED_SCHEMA_KIND).source(schema).param(ARG_SCHEMA_KIND,
                        schema.getSchemaKind());
            }
        }
    }

    private void addObject(XNode node, String xmlName, ISchema schema, Object value) {
        if (objCache.get(value) != null)
            return;
        XNode child = transformObj(schema, value);
        if (child.isDummyNode()) {
            child.setTagName(xmlName);
        }
        node.appendChild(child);
    }

    private void addMap(XNode node, IObjPropMeta propMeta, Map<String, Object> value) {
        XNode mapNode = XNode.make(propMeta.getXmlName());
        node.appendChild(mapNode);
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            XNode child = transformObj(propMeta.getSchema().getMapValueSchema(), entry.getValue());
            if (child.isDummyNode()) {
                child.setTagName(entry.getKey());
            }
            mapNode.appendChild(child);
        }
    }

    private XNode transformListItem(Object value, IObjPropMeta propMeta) {
        ISchema schema = propMeta.getItemSchema();
        switch (schema.getSchemaKind()) {
            case OBJ: {
                XNode child = transformObj(schema, value);
                if (child.getTagName().equals(CoreConstants.DUMMY_TAG_NAME))
                    child.setTagName(propMeta.getChildXmlName());
                return child;
            }
            case UNION: {
                return transformUnion(value, schema, propMeta.getName());
            }
            default:
                throw new NopException(ERR_XDSL_NOT_SUPPORTED_SCHEMA_KIND).param(ARG_PROP_NAME, propMeta.getName())
                        .param(ARG_SCHEMA_KIND, schema.getSchemaKind());
        }
    }

    private XNode transformUnion(Object value, IUnionSchema schema, String propName) {
        String subTypeProp = schema.getSubTypeProp();
        Object typeValue = BeanTool.instance().getProperty(value, subTypeProp);
        if (typeValue == null)
            throw new NopException(ERR_XDSL_SUB_TYPE_PROP_VALUE_NOT_STRING).loc(getLocation(value))
                    .param(ARG_PROP_NAME, propName).param(ARG_SUB_TYPE_PROP, subTypeProp)
                    .param(ARG_SUB_TYPE_VALUE, typeValue);

        ISchema subSchema = getSubSchema(schema.getOneOf(), typeValue.toString());
        if (subSchema == null)
            throw new NopException(ERR_XDSL_PROP_NO_SUB_SCHEMA_DEFINITION).loc(getLocation(value))
                    .param(ARG_PROP_NAME, propName).param(ARG_SUB_TYPE_PROP, subTypeProp)
                    .param(ARG_SUB_TYPE_VALUE, typeValue);

        return transformObj(subSchema, value);
    }

    private ISchema getSubSchema(List<ISchema> oneOf, String typeValue) {
        if (oneOf == null || oneOf.isEmpty())
            return null;
        for (ISchema schema : oneOf) {
            if (typeValue.equals(schema.getTypeValue()))
                return schema;
        }
        return null;
    }

    private SourceLocation getLocation(Object value) {
        if (value instanceof ISourceLocationGetter)
            return ((ISourceLocationGetter) value).getLocation();
        return null;
    }

    private SourceLocation getLocation(Object map, String propName, Object value) {
        if (value instanceof ISourceLocationGetter)
            return ((ISourceLocationGetter) value).getLocation();
        if (map instanceof JObject)
            return ((JObject) map).getLocation(propName);
        return null;
    }
}