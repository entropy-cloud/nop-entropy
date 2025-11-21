/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl.json;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.collections.KeyedList;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.xml.IXNodeToObjectTransformer;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import io.nop.xlang.xdef.domain.UnknownStdDomainHandler;
import io.nop.xlang.xdef.impl.XDefComment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NODE_UNEXPECTED_TAG_NAME;

public class DslXNodeToJsonTransformer implements IXNodeToObjectTransformer {
    /**
     * 为编辑器提供数据，此时只解析数字类型和boolean类型，其他类型都作为字符串返回
     */
    protected final boolean forEditor;
    protected final XLangCompileTool compileTool;
    protected final IXDefinition rootDefNode;

    private boolean ignoreUnknown;

    public DslXNodeToJsonTransformer(boolean forEditor, IXDefinition rootDefNode, XLangCompileTool compileTool) {
        this.forEditor = forEditor;
        this.rootDefNode = rootDefNode;
        if (compileTool == null)
            compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        this.compileTool = compileTool;
    }

    public DslXNodeToJsonTransformer ignoreUnknown(boolean ignoreUnknown) {
        this.ignoreUnknown = ignoreUnknown;
        return this;
    }

    public XLangCompileTool getCompileTool() {
        return compileTool;
    }

    @Override
    public Object transformToObject(XNode node) {
        return transformToObject(rootDefNode, node, null);
    }

    public Object transformToObject(IXDefNode defNode, XNode node) {
        return transformToObject(defNode, node, null);
    }

    public Object transformToObject(IXDefNode defNode, XNode node, String subTypeProp) {
        if (defNode.isSimple()) {
            return parseBody(defNode, node);
        } else {
            return parseObject(defNode, node, subTypeProp);
        }
    }

    protected Object parseBody(IXDefNode defNode, XNode node) {
        // 先检查xdef:value格式。
        if (defNode.getXdefValue() != null) {
            // 如果xdef:value执行xml body，则直接使用xdef:value格式来解析
            if (defNode.getXdefValue().isSupportBody(compileTool)) {
                return parseBodyValue(node, defNode.getXdefValue());
            } else {
                // 如果node没有子节点，则尝试用xdef:value来解析。
                if (!node.hasChild()) {
                    if (node.content().isEmpty()) {
                        return defNode.getXdefValue().getDefaultValue();
                    } else {
                        return parseValue(node.content(), CoreConstants.XML_PROP_BODY, defNode.getXdefValue());
                    }
                }
            }
        }

        // 如果node有子节点，且xdef:value不支持xml body，则按照xdef:body-type设置来解析
        if (defNode.getXdefBodyType() == null)
            return null;

        switch (defNode.getXdefBodyType()) {
            // case set:
            // return parseBodySet(defNode, node);
            case list:
                return parseBodyList(defNode, node);
            case union:
                return parseBodyUnion(defNode, node);
            case map:
                return parseBodyMap(defNode, node);
            default:
                return null;
        }
    }
    //
    // private KeyedList<IKeyedElement> parseBodySet(IXDefNode defNode, XNode node) {
    // KeyedList<IKeyedElement> list = new KeyedList<>();
    // for (XNode child : node.getChildren()) {
    // IXDefNode childDef = defNode.getChild(child.getTagName());
    // if (childDef != null) {
    // DynamicObject obj = parseObject(childDef, child);
    // list.add(obj);
    // }
    // }
    // return list;
    // }

    protected List<?> parseBodyList(IXDefNode defNode, XNode node) {
        if (defNode.getXdefKeyAttr() == null) {
            List<Object> list = new ArrayList<>(node.getChildCount());
            for (XNode child : node.getChildren()) {
                IXDefNode childDef = defNode.getChild(child.getTagName());
                if (childDef != null) {
                    Object obj = transformToObject(childDef, child);
                    list.add(obj);
                } else {
                    if (ignoreUnknown)
                        continue;

                    throw new NopException(ERR_XDSL_NODE_UNEXPECTED_TAG_NAME).param(ARG_NODE, child)
                            .param(ARG_TAG_NAME, child.getTagName())
                            .param(ARG_ALLOWED_NAMES, defNode.getChildren().keySet());
                }
            }
            return list;
        } else {
            KeyedList<Object> list = new KeyedList<>(node.getChildCount(),
                    item -> getKey(item, defNode.getXdefKeyAttr()));
            for (XNode child : node.getChildren()) {
                IXDefNode childDef = defNode.getChild(child.getTagName());
                if (childDef != null) {
                    Object obj = parseObject(childDef, child, defNode.getXdefBeanSubTypeProp());
                    list.add(obj);
                } else {
                    if (ignoreUnknown)
                        continue;
                    throw new NopException(ERR_XDSL_NODE_UNEXPECTED_TAG_NAME).param(ARG_NODE, child)
                            .param(ARG_TAG_NAME, child.getTagName())
                            .param(ARG_ALLOWED_NAMES, defNode.getChildren().keySet());
                }
            }
            return list;
        }
    }

    protected String getKey(Object item, String keyProp) {
        return StringHelper.toString(BeanTool.instance().getProperty(item, keyProp), null);
    }

    private Object parseBodyUnion(IXDefNode defNode, XNode node) {
        for (XNode child : node.getChildren()) {
            IXDefNode childDef = defNode.getChild(child.getTagName());
            if (childDef != null) {
                return transformToObject(childDef, child, defNode.getXdefBeanSubTypeProp());
            }
        }
        return null;
    }

    private Object parseBodyMap(IXDefNode defNode, XNode node) {
        Map<String, Object> obj = new LinkedHashMap<>();
        for (XNode child : node.getChildren()) {
            IXDefNode childDef = defNode.getChild(child.getTagName());
            Object value;
            if (childDef != null) {
                value = transformToObject(childDef, child);
            } else {
                value = parseExtObject(child);
            }
            obj.put(child.getTagName(), value);
        }
        return obj;
    }

    public Object parseObject(XNode node) {
        return parseObject(rootDefNode, node);
    }

    public final Object parseObject(IXDefNode defNode, XNode node) {
        return parseObject(defNode, node, null);
    }

    public Object parseObject(IXDefNode defNode, XNode node, String subTypeProp) {
        String objName = defNode.getXdefBeanClass();
        if (objName == null) {
            objName = defNode.getTagName();
        }
        DynamicObject obj = new DynamicObject(objName, defNode.getXdefUniqueProp());
        obj.setLocation(node.getLocation());
        if (subTypeProp != null) {
            obj.addProp(subTypeProp, node.getTagName());
        }

        String tagProp = defNode.getXdefBeanTagProp();
        if (tagProp != null) {
            obj.addProp(tagProp, node.getTagName());
        }

        node.forEachAttr((name, vl) -> {
            // 对xmlns名字空间特殊处理
            if (name.startsWith("xmlns:")) {
                obj.addProp(name, vl.getValue());
                return;
            }

            IXDefAttribute attr = defNode.getAttribute(name);
            if (attr == null) {
                if (!ignoreUnknown)
                    obj.addProp(name, vl.getValue());
            } else {
                Object value = parseValue(vl, name, attr.getType());
                String propName = attr.getPropName();
                if (propName == null)
                    propName = name;
                obj.addProp(propName, value);
            }
        });

        // 节点上缺失的属性需要设置缺省值
        defNode.getAttributes().forEach((name, attr) -> {
            if (!node.hasAttr(name)) {
                Object defaultValue = attr.getType().getDefaultValue();
                if (defaultValue == null) {
                    obj.addPropDefault(attr.getPropName(), defaultValue);
                } else {
                    // 如果值非空，则需要作为明确的属性保存，否则转换为强类型对象时可能会导致缺省值未被设置
                    obj.addProp(name, defaultValue);
                }
            }
        });

        // 节点的注释可能被解析为XDefComment类型的属性。主要是xdef模型定义文件在使用
        if (defNode.getXdefBeanCommentProp() != null) {
            XDefComment comment = XDefComment.parseComment(node);
            if (comment != null) {
                obj.addProp(defNode.getXdefBeanCommentProp(), comment);
            } else {
                obj.addPropDefault(defNode.getXdefBeanCommentProp(), null);
            }
        }

        if (useValue(defNode, node) || defNode.getXdefBodyType() != null) {
            Object value = parseBody(defNode, node);
            if (node.hasBody()) {
                obj.addProp(defNode.getXdefBeanBodyProp(), value);
            } else {
                obj.addPropDefault(defNode.getXdefBeanBodyProp(), value);
            }
        } else {
            for (IXDefNode childDef : defNode.getChildren().values()) {
                if (childDef.getXdefBeanProp() != null) {
                    obj.addPropDefault(childDef.getXdefBeanProp(), null);
                }
            }

            for (XNode child : node.getChildren()) {
                IXDefNode childDef = defNode.getChild(child.getTagName());
                if (childDef == null) {
                    obj.addProp(child.getTagName(), parseExtObject(child));
                } else if (childDef.getXdefUniqueAttr() != null) {
                    String propName = childDef.getXdefBeanProp();
                    if (propName == null) {
                        propName = child.getTagName();
                    }
                    KeyedList<Object> list = (KeyedList<Object>) obj.prop_get(propName);
                    if (list == null) {
                        list = new KeyedList<>(item -> getKey(item, childDef.getXdefUniqueAttr()));
                        obj.prop_set(propName, list);
                    }
                    Object childObj = parseObject(childDef, child);
                    list.add(childObj);
                } else {
                    String propName = childDef.getXdefBeanProp();
                    if (propName == null)
                        propName = child.getTagName();
                    Object value = transformToObject(childDef, child);
                    obj.addProp(propName, value);
                }
            }
        }
        return obj;
    }

    private boolean useValue(IXDefNode defNode, XNode node) {
        if (defNode.getXdefValue() == null)
            return false;
        if (defNode.hasChild() && node.hasChild())
            return false;
        return true;
    }

    public Object parseValue(ValueWithLocation vl, String propName, XDefTypeDecl type) {
        IStdDomainHandler handler = getHandler(vl.getLocation(), type);
        Object value;
        if (vl.isEmpty()) {
            value = type.getDefaultValue();
            if (forEditor && value != null) {
                if (!(value instanceof Number) && !(value instanceof Boolean)) {
                    value = handler.serializeToString(value);
                }
            }
        } else {
            if (forEditor) {
                StdDataType dataType = StdDataType.fromStdName(handler.getName());
                if (dataType == null || !dataType.isNumericType() && !dataType.isBoolType()) {
                    value = vl.asString();
                } else {
                    value = handler.parseProp(type.getOptions(), vl.getLocation(), propName, vl.asString(),
                            getCompileTool());
                }
            } else {
                value = handler.parseProp(type.getOptions(), vl.getLocation(), propName,
                        parseCpValue(vl, type), getCompileTool());
            }
        }
        return value;
    }

    private Object parseCpValue(ValueWithLocation vl, XDefTypeDecl type) {
        if (type.isAllowCpExpr()) {
            String value = vl.asString();
            if (value != null && value.indexOf("#{") >= 0) {
                Object v = getCompileTool().getStaticValue(vl.getLocation(), value);
                return v;
            }
        }
        return vl.getValue();
    }

    private Object parseBodyValue(XNode node, XDefTypeDecl type) {
        // 如果node没有body，则这里会返回对象而不是null

        IStdDomainHandler handler = getHandler(node.getLocation(), type);
        Object value;

        // 为编辑器提供数据时返回xml
        if (forEditor)
            return node.bodyFullXml();

        value = handler.parseXmlChild(type.getOptions(), node, getCompileTool());
        return value;
    }

    protected IStdDomainHandler getHandler(SourceLocation loc, XDefTypeDecl type) {
        IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler(type.getStdDomain());
        if (handler == null)
            return new UnknownStdDomainHandler(type.getStdDomain());
        return handler;
    }

    protected Object parseExtObject(XNode node) {
        // 返回的结果可以实现与XNode的双向可逆转换
        return node.toJsonObject();
    }
}