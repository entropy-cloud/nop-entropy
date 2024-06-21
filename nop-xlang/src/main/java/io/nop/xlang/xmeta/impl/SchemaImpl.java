/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.impl;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.commons.collections.IKeyedElement;
import io.nop.commons.collections.KeyedList;
import io.nop.commons.text.regex.IRegex;
import io.nop.commons.text.regex.RegexHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.type.IGenericType;
import io.nop.xlang.xdef.SchemaKind;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ISchemaNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SchemaImpl extends SchemaNodeImpl implements ISchema, IKeyedElement, IObjSchemaImpl {
    private ListSchemaData listSchema;
    private SimpleSchemaData simpleSchema;
    private ObjSchemaData objSchema;
    private UnionSchemaData unionSchema;
    private MapSchemaData mapSchema;
    private boolean explicitDefine;
    // private String defName;

    private static class UnionSchemaData {
        String subTypeProp;
        List<ISchema> oneOf;

        void appendTo(XNode node, Map<ISchemaNode, XNode> nodeRefs) {
            if (subTypeProp != null) {
                node.setAttr(node.getLocation(), "subTypeOf", subTypeProp);
            }
            if (oneOf != null) {
                XNode oneOfN = XNode.make("oneOf");
                for (ISchema schema : oneOf) {
                    oneOfN.appendChild(schema.toNode(nodeRefs));
                }
                node.appendChild(oneOfN);
            }
        }

        public UnionSchemaData cloneInstance() {
            UnionSchemaData ret = new UnionSchemaData();
            ret.subTypeProp = subTypeProp;
            ret.oneOf = oneOf == null ? null : new ArrayList<>(oneOf);
            return ret;
        }
    }

    private static class MapSchemaData {
        ISchema valueSchema;

        void appendTo(XNode node, Map<ISchemaNode, XNode> nodeRefs) {
            if (valueSchema != null) {
                XNode item = valueSchema.toNode(nodeRefs);
                item.setTagName("unknownTag");
                node.appendChild(item);
            }
        }

        public MapSchemaData cloneInstance() {
            MapSchemaData ret = new MapSchemaData();
            ret.valueSchema = valueSchema;
            return ret;
        }
    }

    private static class ListSchemaData {
        Integer minItems;
        Integer maxItems;
        String keyProp;
        String orderProp;
        ISchema itemSchema;
        // String childName;

        public ListSchemaData cloneInstance() {
            ListSchemaData ret = new ListSchemaData();
            ret.minItems = minItems;
            ret.maxItems = maxItems;
            ret.keyProp = keyProp;
            ret.orderProp = orderProp;
            ret.itemSchema = itemSchema;
            return ret;
        }

        void appendTo(XNode node, Map<ISchemaNode, XNode> nodeRefs) {
            if (minItems != null) {
                node.setAttr(node.getLocation(), "minItems", minItems);
            }
            if (maxItems != null) {
                node.setAttr(node.getLocation(), "maxItems", maxItems);
            }
            if (keyProp != null) {
                node.setAttr(node.getLocation(), "keyProp", keyProp);
            }
            if (orderProp != null) {
                node.setAttr(node.getLocation(), "orderProp", orderProp);
            }
            if (itemSchema != null) {
                XNode item = itemSchema.toNode(nodeRefs);
                item.setTagName("item");
                node.appendChild(item);
            }
        }

        String getKeyAttr() {
            if (keyProp == null)
                return null;
            IObjPropMeta prop = itemSchema.getProp(keyProp);
            return prop == null ? null : prop.getXmlName();
        }

        String getOrderAttr() {
            if (orderProp == null)
                return null;
            IObjPropMeta prop = itemSchema.getProp(orderProp);
            return prop == null ? null : prop.getXmlName();
        }
    }

    private static class ObjSchemaData {
        Boolean _abstract;
        Boolean _interface;

        String typeValue;
        IGenericType extendsType;
        List<IGenericType> implementedTypes = Collections.emptyList();
        Integer minProperties;
        Integer maxProperties;
        KeyedList<ObjPropMetaImpl> props = KeyedList.emptyList();

        ISchema unknownAttrSchema;
        ISchema unknownTagSchema;

        Boolean supportExtends;
        String uniqueProp;

        public ObjSchemaData cloneInstance() {
            ObjSchemaData ret = new ObjSchemaData();
            ret._abstract = _abstract;
            ret._interface = _interface;
            ret.typeValue = typeValue;
            ret.extendsType = extendsType;
            ret.implementedTypes = implementedTypes.isEmpty() ? implementedTypes : new ArrayList<>(implementedTypes);
            ret.minProperties = minProperties;
            ret.maxProperties = maxProperties;
            ret.props = props.cloneInstance();
            ret.unknownAttrSchema = unknownAttrSchema;
            ret.unknownTagSchema = unknownTagSchema;
            ret.supportExtends = supportExtends;
            ret.uniqueProp = uniqueProp;
            return ret;
        }

        void appendTo(XNode node, Map<ISchemaNode, XNode> nodeRefs) {
            if (_abstract != null && _abstract) {
                node.setAttr(node.getLocation(), "abstract", _abstract);
            }
            if (_interface != null && _interface) {
                node.setAttr(node.getLocation(), "interface", _interface);
            }
            if (extendsType != null) {
                node.setAttr(node.getLocation(), "extends", extendsType);
            }
            if (implementedTypes != null && !implementedTypes.isEmpty()) {
                node.setAttr(node.getLocation(), "implements", StringHelper.join(implementedTypes, ","));
            }

            setAttr(node, "typeValue", typeValue);

            setAttr(node, "minProperties", minProperties);

            setAttr(node, "maxProperties", maxProperties);

            if (props != null) {
                XNode propsNode = XNode.make("props");
                for (IObjPropMeta propMeta : props) {
                    XNode propN = propMeta.toNode(nodeRefs);
                    propsNode.appendChild(propN);
                }
                if (propsNode.hasChild())
                    node.appendChild(propsNode);
            }

            if (unknownAttrSchema != null) {
                XNode unknownAttr = unknownAttrSchema.toNode(nodeRefs);
                unknownAttr.setTagName("unknownAttr");
                node.appendChild(unknownAttr);
            }

            if (unknownTagSchema != null) {
                XNode unknownChildN = unknownTagSchema.toNode(nodeRefs);
                unknownChildN.setTagName("unknownTag");
                node.appendChild(unknownChildN);
            }

            setAttr(node, "supportExtends", supportExtends);

            setAttr(node, "uniqueProp", uniqueProp);
        }

        static void setAttr(XNode node, String name, Object value) {
            if (value != null) {
                node.setAttr(node.getLocation(), name, value);
            }
        }
    }

    private static class SimpleSchemaData {
        String dict;
        Integer precision;
        Integer scale;
        String pattern;
        IRegex regex;
        Double min;
        Double max;
        Boolean excludeMin;
        Boolean excludeMax;
        Integer minLength;
        Integer maxLength;
        Integer multipleOf;

        public SimpleSchemaData cloneInstance() {
            SimpleSchemaData ret = new SimpleSchemaData();
            ret.dict = dict;
            ret.precision = precision;
            ret.scale = scale;
            ret.pattern = pattern;
            ret.regex = regex;
            ret.min = min;
            ret.max = max;
            ret.excludeMin = excludeMin;
            ret.excludeMax = excludeMax;
            ret.minLength = minLength;
            ret.maxLength = maxLength;
            ret.multipleOf = multipleOf;
            return ret;
        }

        void appendTo(XNode node) {
            if (dict != null)
                node.setAttr(node.getLocation(), "dict", dict);
            if (precision != null) {
                node.setAttr(node.getLocation(), "precision", precision);
            }
            if (scale != null) {
                node.setAttr(node.getLocation(), "scale", scale);
            }
            if (pattern != null) {
                node.setAttr(node.getLocation(), "pattern", pattern);
            }
            if (min != null) {
                node.setAttr(node.getLocation(), "min", min);
            }
            if (max != null) {
                node.setAttr(node.getLocation(), "max", max);
            }
            if (excludeMin != null) {
                node.setAttr(node.getLocation(), "excludeMin", excludeMin);
            }
            if (excludeMax != null) {
                node.setAttr(node.getLocation(), "excludeMax", excludeMax);
            }
            if (minLength != null) {
                node.setAttr(node.getLocation(), "minLength", minLength);
            }
            if (maxLength != null) {
                node.setAttr(node.getLocation(), "maxLength", maxLength);
            }
            if (multipleOf != null) {
                node.setAttr(node.getLocation(), "multipleOf", multipleOf);
            }
        }
    }

    private ListSchemaData makeListSchemaData() {
        if (listSchema == null) {
            listSchema = new ListSchemaData();
        }
        return listSchema;
    }

    private MapSchemaData makeMapSchemaData() {
        if (mapSchema == null) {
            mapSchema = new MapSchemaData();
        }
        return mapSchema;
    }

    private SimpleSchemaData makeSimpleSchemaData() {
        if (simpleSchema == null) {
            simpleSchema = new SimpleSchemaData();
        }
        return simpleSchema;
    }

    private ObjSchemaData makeObjSchemaData() {
        if (objSchema == null) {
            objSchema = new ObjSchemaData();
        }
        return objSchema;
    }

    private UnionSchemaData makeUnionSchemaData() {
        if (unionSchema == null)
            unionSchema = new UnionSchemaData();
        return unionSchema;
    }

    public boolean isExplicitDefine() {
        return explicitDefine;
    }

    public void setExplicitDefine(boolean explicitDefine) {
        checkAllowChange();
        this.explicitDefine = explicitDefine;
    }

    // public String getDefName() {
    // return defName;
    // }
    //
    // public void setDefName(String defName) {
    // checkAllowChange();
    // this.defName = defName;
    // }

    protected SchemaImpl newInstance() {
        return new SchemaImpl();
    }

    public SchemaImpl cloneInstance() {
        SchemaImpl ret = newInstance();
        copyTo(ret);
        ret.listSchema = listSchema == null ? null : listSchema.cloneInstance();
        ret.simpleSchema = simpleSchema == null ? null : simpleSchema.cloneInstance();
        ret.objSchema = objSchema == null ? null : objSchema.cloneInstance();
        ret.unionSchema = unionSchema == null ? null : unionSchema.cloneInstance();
        ret.mapSchema = mapSchema == null ? null : mapSchema.cloneInstance();
        ret.explicitDefine = explicitDefine;
        return ret;
    }

    public String key() {
        return getName();
    }

    @NoReflection
    public boolean isAbstract() {
        return objSchema != null && Boolean.TRUE.equals(objSchema._abstract);
    }

    public Boolean getAbstract() {
        return objSchema == null ? null : objSchema._abstract;
    }

    public void setAbstract(Boolean value) {
        checkAllowChange();
        if ((value == null || !value) && objSchema == null)
            return;

        makeObjSchemaData()._abstract = value;
    }

    @NoReflection
    public boolean isInterface() {
        return objSchema != null && Boolean.TRUE.equals(objSchema._interface);
    }

    public Boolean getInterface() {
        return objSchema == null ? null : objSchema._interface;
    }

    public void setInterface(Boolean value) {
        checkAllowChange();
        if ((value == null || !value) && objSchema == null)
            return;

        makeObjSchemaData()._interface = value;
    }

    @Override
    public Integer getMinItems() {
        return listSchema == null ? null : listSchema.minItems;
    }

    public void setMinItems(Integer minItems) {
        checkAllowChange();
        if (minItems == null && listSchema == null)
            return;
        makeListSchemaData().minItems = minItems;
    }

    @Override
    public Integer getMaxItems() {
        return listSchema == null ? null : listSchema.maxItems;
    }

    public void setMaxItems(Integer maxItems) {
        checkAllowChange();
        if (maxItems == null && listSchema == null)
            return;
        makeListSchemaData().maxItems = maxItems;
    }

    @Override
    public String getKeyAttr() {
        return listSchema == null ? null : listSchema.getKeyAttr();
    }

    @Override
    public String getKeyProp() {
        return listSchema == null ? null : listSchema.keyProp;
    }

    @Override
    public String getOrderAttr() {
        return listSchema == null ? null : listSchema.getOrderAttr();
    }

    public void setKeyProp(String keyProp) {
        checkAllowChange();
        if (keyProp == null && listSchema == null)
            return;
        makeListSchemaData().keyProp = keyProp;
    }

    @Override
    public String getOrderProp() {
        return listSchema == null ? null : listSchema.orderProp;
    }

    public void setOrderProp(String orderProp) {
        checkAllowChange();
        if (orderProp == null && listSchema == null)
            return;
        makeListSchemaData().orderProp = orderProp;
    }

    @Override
    public ISchema getItemSchema() {
        return listSchema == null ? null : listSchema.itemSchema;
    }

    public void setItemSchema(ISchema itemSchema) {
        checkAllowChange();
        if (itemSchema == null && listSchema == null)
            return;
        makeListSchemaData().itemSchema = itemSchema;
    }

    @Override
    public ISchema getMapValueSchema() {
        return mapSchema == null ? null : mapSchema.valueSchema;
    }

    public void setMapValueSchema(ISchema valueSchema) {
        checkAllowChange();
        if (valueSchema == null && mapSchema == null)
            return;
        makeMapSchemaData().valueSchema = valueSchema;
    }

    @Override
    public SchemaKind getSchemaKind() {
        if (objSchema != null && objSchema.props != null)
            return SchemaKind.OBJ;

        if (listSchema != null && listSchema.itemSchema != null)
            return SchemaKind.LIST;

        if (unionSchema != null && unionSchema.oneOf != null && !unionSchema.oneOf.isEmpty())
            return SchemaKind.UNION;

        if (mapSchema != null && mapSchema.valueSchema != null)
            return SchemaKind.MAP;

        return SchemaKind.SIMPLE;
    }

    @Override
    public String getDict() {
        return simpleSchema == null ? null : simpleSchema.dict;
    }

    public void setDict(String dict) {
        checkAllowChange();
        if (dict == null && simpleSchema == null)
            return;
        makeSimpleSchemaData().dict = dict;
    }

    @Override
    public Integer getPrecision() {
        return simpleSchema == null ? null : simpleSchema.precision;
    }

    public void setPrecision(Integer precision) {
        checkAllowChange();
        if (precision == null && simpleSchema == null)
            return;
        makeSimpleSchemaData().precision = precision;
    }

    @Override
    public Integer getScale() {
        return simpleSchema == null ? null : simpleSchema.scale;
    }

    public void setScale(Integer scale) {
        checkAllowChange();
        if (scale == null && simpleSchema == null)
            return;
        makeSimpleSchemaData().scale = scale;
    }

    @Override
    public String getPattern() {
        return simpleSchema == null ? null : simpleSchema.pattern;
    }

    public void setPattern(String pattern) {
        checkAllowChange();
        if (pattern == null && simpleSchema == null)
            return;
        makeSimpleSchemaData().pattern = pattern;
        if (!StringHelper.isEmpty(pattern)) {
            simpleSchema.regex = RegexHelper.compileRegex(pattern);
        }
    }

    @Override
    public boolean matchPattern(String str) {
        if (simpleSchema != null && simpleSchema.regex != null) {
            return simpleSchema.regex.test(str);
        }
        return true;
    }

    @Override
    public Double getMin() {
        return simpleSchema == null ? null : simpleSchema.min;
    }

    public void setMin(Double min) {
        checkAllowChange();
        if (min == null && simpleSchema == null)
            return;
        makeSimpleSchemaData().min = min;
    }

    @Override
    public Double getMax() {
        return simpleSchema == null ? null : simpleSchema.max;
    }

    public void setMax(Double max) {
        checkAllowChange();
        if (max == null && simpleSchema == null)
            return;
        makeSimpleSchemaData().max = max;
    }

    @Override
    public Boolean getExcludeMin() {
        return simpleSchema == null ? null : simpleSchema.excludeMin;
    }

    public void setExcludeMin(Boolean excludeMin) {
        checkAllowChange();
        if (excludeMin == null && simpleSchema == null)
            return;
        makeSimpleSchemaData().excludeMin = excludeMin;
    }

    @Override
    public Boolean getExcludeMax() {
        return simpleSchema == null ? null : simpleSchema.excludeMax;
    }

    public void setExcludeMax(Boolean excludeMax) {
        checkAllowChange();
        if (excludeMax == null && simpleSchema == null)
            return;
        makeSimpleSchemaData().excludeMax = excludeMax;
    }

    @Override
    public Integer getMinLength() {
        return simpleSchema == null ? null : simpleSchema.minLength;
    }

    public void setMinLength(Integer minLength) {
        checkAllowChange();
        if (minLength == null && simpleSchema == null)
            return;
        makeSimpleSchemaData().minLength = minLength;
    }

    @Override
    public Integer getMaxLength() {
        return simpleSchema == null ? null : simpleSchema.maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        checkAllowChange();
        if (maxLength == null && simpleSchema == null)
            return;
        makeSimpleSchemaData().maxLength = maxLength;
    }

    @Override
    public Integer getMultipleOf() {
        return simpleSchema == null ? null : simpleSchema.multipleOf;
    }

    public void setMultipleOf(Integer multipleOf) {
        checkAllowChange();
        if (multipleOf == null && simpleSchema == null)
            return;
        makeSimpleSchemaData().multipleOf = multipleOf;
    }

    @Override
    public IGenericType getExtendsType() {
        return objSchema == null ? null : objSchema.extendsType;
    }

    public void setExtendsType(IGenericType extendsType) {
        checkAllowChange();
        if (extendsType == null && objSchema == null)
            return;
        makeObjSchemaData().extendsType = extendsType;
    }

    @Override
    public List<IGenericType> getImplementsTypes() {
        return objSchema == null ? null : objSchema.implementedTypes;
    }

    public void setImplementsTypes(List<IGenericType> types) {
        checkAllowChange();
        if ((types == null || types.isEmpty()) && objSchema == null)
            return;
        makeObjSchemaData().implementedTypes = types;
    }

    @Override
    public String getUniqueProp() {
        return objSchema == null ? null : objSchema.uniqueProp;
    }

    public void setUniqueProp(String uniqueProp) {
        checkAllowChange();
        if (uniqueProp == null && objSchema == null)
            return;
        makeObjSchemaData().uniqueProp = uniqueProp;
    }

    @Override
    public Integer getMinProperties() {
        return objSchema == null ? null : objSchema.minProperties;
    }

    public void setMinProperties(Integer minProperties) {
        checkAllowChange();
        if (minProperties == null && objSchema == null)
            return;
        makeObjSchemaData().minProperties = minProperties;
    }

    @Override
    public Integer getMaxProperties() {
        return objSchema == null ? null : objSchema.maxProperties;
    }

    public void setMaxProperties(Integer maxProperties) {
        checkAllowChange();
        if (maxProperties == null && objSchema == null)
            return;
        makeObjSchemaData().maxProperties = maxProperties;
    }

    @Override
    public String getTypeValue() {
        return objSchema == null ? null : objSchema.typeValue;
    }

    public void setTypeValue(String typeValue) {
        checkAllowChange();
        if (typeValue == null && objSchema == null)
            return;
        makeObjSchemaData().typeValue = typeValue;
    }

    @Override
    public List<ObjPropMetaImpl> getProps() {
        return objSchema == null ? null : objSchema.props;
    }

    public void setProps(List<ObjPropMetaImpl> props) {
        checkAllowChange();
        if (props == null && objSchema == null)
            return;
        makeObjSchemaData().props = KeyedList.fromList(props, ObjPropMetaImpl::getName);
    }

    public void addProp(ObjPropMetaImpl prop) {
        checkAllowChange();
        ObjSchemaData data = makeObjSchemaData();
        if (data.props.isEmpty()) {
            KeyedList<ObjPropMetaImpl> props = new KeyedList<>(ObjPropMetaImpl::getName);
            data.props = props;
        } else {
            data.props.add(prop);
        }
    }

    @Override
    public Boolean getSupportExtends() {
        return objSchema == null ? null : objSchema.supportExtends;
    }

    public void setSupportExtends(Boolean supportExtends) {
        checkAllowChange();
        if (supportExtends == null && objSchema == null)
            return;
        makeObjSchemaData().supportExtends = supportExtends;
    }

    @Override
    public String getSubTypeProp() {
        return unionSchema == null ? null : unionSchema.subTypeProp;
    }

    public void setSubTypeProp(String subTypeProp) {
        checkAllowChange();
        if (subTypeProp == null && unionSchema == null)
            return;
        makeUnionSchemaData().subTypeProp = subTypeProp;
    }

    @Override
    public List<ISchema> getOneOf() {
        return unionSchema == null ? null : unionSchema.oneOf;
    }

    public void setOneOf(List<ISchema> oneOf) {
        checkAllowChange();
        if (oneOf == null && unionSchema == null)
            return;
        UnionSchemaData unionSchema = makeUnionSchemaData();
        unionSchema.oneOf = oneOf;
    }

    @Override
    public IObjPropMeta getProp(String name) {
        if (objSchema == null)
            return null;

        IObjPropMeta prop = objSchema.props.getByKey(name);
        return prop;
    }

    @Override
    public boolean isPropInherited(String name) {
        if (getRefSchema() != null)
            return getRefSchema().hasProp(name);
        return false;
    }

    @Override
    public boolean hasProp(String name) {
        if (objSchema == null)
            return false;
        return objSchema.props != null && objSchema.props.containsKey(name);
    }

    @Override
    public boolean hasProps() {
        if (objSchema == null)
            return false;
        return objSchema.props != null && !objSchema.props.isEmpty();
    }

    @Override
    public ISchema getUnknownAttrSchema() {
        return objSchema == null ? null : objSchema.unknownAttrSchema;
    }

    @Override
    public ISchema getUnknownTagSchema() {
        return objSchema == null ? null : objSchema.unknownTagSchema;
    }

    public void setUnknownAttrSchema(ISchema schema) {
        checkAllowChange();
        if (schema == null && objSchema == null)
            return;

        makeObjSchemaData().unknownAttrSchema = schema;
    }

    public void setUnknownTagSchema(ISchema schema) {
        checkAllowChange();
        if (schema == null && objSchema == null)
            return;

        makeObjSchemaData().unknownTagSchema = schema;
    }

    @NoReflection
    public boolean isRefResolved() {
        return Boolean.TRUE.equals(getRefResolved());
    }

    @Override
    public XNode toNode(Map<ISchemaNode, XNode> nodeRefs) {
        XNode refNode = nodeRefs.get(this);
        if (refNode != null) {
            if (getId() == null) {
                setId(StringHelper.generateUUID());
            }
            // 只有被引用的节点才输出id属性，避免最终输出受到无关信息的干扰
            refNode.setAttr(null, "id", getId());
            XNode node = XNode.make(explicitDefine ? "define" : "schema");
            node.setAttr("ref", getId());
            return node;
        }

        XNode node = super.toNode(nodeRefs);
        node.setTagName(explicitDefine ? "define" : "schema");

        if (simpleSchema != null)
            simpleSchema.appendTo(node);
        if (unionSchema != null) {
            unionSchema.appendTo(node, nodeRefs);
        }
        if (objSchema != null)
            objSchema.appendTo(node, nodeRefs);
        if (listSchema != null) {
            listSchema.appendTo(node, nodeRefs);
        }

        if (mapSchema != null) {
            mapSchema.appendTo(node, nodeRefs);
        }

        return node;
    }
}