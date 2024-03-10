/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.impl;

import io.nop.core.resource.component.IComponentTransformer;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdef.SchemaKind;
import io.nop.xlang.xdef.XDefBodyType;
import io.nop.xlang.xdef.XDefKeys;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.impl.XDefAttribute;
import io.nop.xlang.xdef.impl.XDefComment;
import io.nop.xlang.xdef.impl.XDefNode;
import io.nop.xlang.xdef.impl.XDefinition;
import io.nop.xlang.xmeta.*;

import java.util.*;

public class ObjMetaToXDef implements IComponentTransformer<IObjMeta, XDefinition> {
    private final XDefKeys keys;

    public ObjMetaToXDef(XDefKeys keys) {
        this.keys = keys;
    }

    public ObjMetaToXDef() {
        this(XDefKeys.DEFAULT);
    }

    @Override
    public XDefinition transform(IObjMeta objMeta) {
        XDefinition def = new XDefinition();
        def.setXdslSchema(XLangConstants.XDSL_SCHEMA_XDEF);
        def.setXdslTransform(XLangConstants.MODEL_TYPE_XDEF);
        def.setXdefVersion(objMeta.getVersion());
        def.setXdefBeanPackage(objMeta.getPackageName());
        def.setXdefParserClass(objMeta.getParserClass());
        def.setXdefDefaultExtends(objMeta.getDefaultExtends());
        def.setDefKeys(keys);
        def.setXdefCheckNs(objMeta.getCheckNs());
        def.setLocation(objMeta.getLocation());
        def.setXdefDefines(toDefinitions(objMeta.getDefines()));
        XDefNode node = toNode(objMeta.getRootSchema());
        if (node == null)
            throw new IllegalStateException("root schema transform to null node");

        node.setTagName(objMeta.getXmlName());
        // def.setRootNode(node);
        return def;
    }

    private List<XDefNode> toDefinitions(List<? extends ISchema> localDefs) {
        if (localDefs == null || localDefs.isEmpty())
            return Collections.emptyList();

        List<XDefNode> ret = new ArrayList<>(localDefs.size());
        for (ISchema schema : localDefs) {
            String name = schema.getName();
            String displayName = schema.getDisplayName();
            String description = schema.getDescription();

            XDefNode node = toNode(schema);
            if (node == null)
                throw new IllegalStateException("schema transform to null node");
            node.setXdefName(name);

            if (displayName != null || description != null) {
                XDefComment comment = node.makeComment();
                if (displayName != null)
                    comment.setMainDisplayName(displayName);
                if (description != null)
                    comment.setMainDescription(description);
            }
            ret.add(node);
        }
        return ret;
    }

    private XDefNode toNode(ISchema schema) {
        SchemaKind kind = schema.getSchemaKind();
        switch (kind) {
            case SIMPLE:
                return simpleSchemaToNode(schema);
            case LIST:
                return listSchemaToNode(schema);
            case UNION:
                return unionSchemaToNode(schema);
            case OBJ:
                return objSchemaToNode(schema);
        }
        return null;
    }

    private XDefNode simpleSchemaToNode(ISimpleSchema schema) {
        XDefTypeDecl type = new XDefTypeDecl(false, false, false, false, schema.getStdDomain(), schema.getDomain(),
                null, null, false, false);
        XDefNode node = new XDefNode();
        node.setTagName(keys.UNIT);
        node.setXdefValue(type);
        return node;
    }

    private XDefNode listSchemaToNode(IListSchema schema) {
        XDefNode node = new XDefNode();
        node.setTagName(keys.UNIT);
        node.setXdefOrderAttr(schema.getOrderAttr());
        node.setXdefBodyType(XDefBodyType.list);

        ISchema itemSchema = schema.getItemSchema();
        if (itemSchema.getSchemaKind() == SchemaKind.UNION) {
            List<XDefNode> children = unionSchemaToNodes(itemSchema);
            Map<String, XDefNode> map = new HashMap<>();
            for (XDefNode child : children) {
                map.put(child.getTagName(), child);
            }
            node.setChildren(map);
        } else {
            XDefNode child = toNode(itemSchema);
            if (child == null)
                throw new IllegalStateException("itemSchema transform to null node");
            node.setChildren(Collections.singletonMap(child.getTagName(), child));
        }
        return node;
    }

    private List<XDefNode> unionSchemaToNodes(IUnionSchema schema) {
        List<XDefNode> list = new ArrayList<>(schema.getOneOf().size());
        for (ISchema subSchema : schema.getOneOf()) {
            XDefNode node = toNode(subSchema);
            list.add(node);
        }
        return list;
    }

    private XDefNode unionSchemaToNode(IUnionSchema schema) {
        XDefNode node = new XDefNode();
        node.setTagName(keys.UNIT);
        node.setXdefRef(schema.getRef());
        List<XDefNode> children = unionSchemaToNodes(schema);
        Map<String, XDefNode> map = new HashMap<>();
        for (XDefNode child : children) {
            map.put(child.getTagName(), child);
        }
        node.setChildren(map);
        return node;
    }

    private XDefNode objSchemaToNode(IObjSchema schema) {
        XDefNode node = new XDefNode();
        node.setXdefRef(schema.getRef());
        Map<String, XDefAttribute> attrs = new HashMap<>();
        Map<String, XDefNode> children = new HashMap<>();

        for (IObjPropMeta prop : schema.getProps()) {
            switch (prop.getXmlPos()) {
                case value: {
                    node.setXdefValue(propToDefType(prop));
                    break;
                }
                case attr: {
                    attrs.put(prop.getXmlName(), propToAttr(prop));
                    break;
                }
                case child: {
                    XDefNode child = toNode(prop.getSchema());
                    if (child == null)
                        throw new IllegalStateException("prop schema transform to null node:" + prop.getName());
                    child.setTagName(prop.getXmlName());
                    children.put(child.getTagName(), child);
                    break;
                }
                case tag: {
                    node.setXdefBeanTagProp(prop.getName());
                    break;
                }
            }
        }

        node.setAttributes(attrs);
        node.setChildren(children);
        return node;
    }

    private XDefTypeDecl propToDefType(IObjPropMeta prop) {
        ISchema schema = prop.getSchema();
        return new XDefTypeDecl(prop.isDeprecated(), prop.isInternal(), prop.isMandatory(), false,
                schema.getStdDomain(), schema.getDomain(), null, prop.getDefaultValue(), false, false);
    }

    private XDefAttribute propToAttr(IObjPropMeta prop) {
        XDefAttribute attr = new XDefAttribute();
        attr.setLocation(prop.getLocation());
        attr.setName(prop.getXmlName());
        attr.setType(propToDefType(prop));
        attr.setPropName(prop.getName());
        return attr;
    }
}