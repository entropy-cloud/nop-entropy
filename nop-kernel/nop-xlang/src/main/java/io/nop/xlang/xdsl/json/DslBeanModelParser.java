/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl.json;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.commons.collections.KeyedList;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.impl.XDefComment;

import java.util.List;

public class DslBeanModelParser extends DslXNodeToJsonTransformer {

    public DslBeanModelParser(boolean forEditor, IXDefinition rootDefNode, XLangCompileTool compileTool) {
        super(forEditor, rootDefNode, compileTool);
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

    @Override
    public Object parseObject(IXDefNode defNode, XNode node, String subTypeProp) {
        String objName = defNode.getXdefBeanClass();
        if (objName == null) {
            return super.parseObject(defNode, node, subTypeProp);
        }

        // 有可能只是接口
        IBeanModel objBeanModel = ReflectionManager.instance().loadBeanModel(objName);

        Object obj = objBeanModel.newInstance();
        if (!obj.getClass().getName().equals(objBeanModel.getClassName())) {
            objBeanModel = ReflectionManager.instance().getBeanModelForClass(obj.getClass());
        }

        IBeanModel beanModel = objBeanModel;

        if (obj instanceof ISourceLocationSetter)
            ((ISourceLocationSetter) obj).setLocation(node.getLocation());

        String tagProp = defNode.getXdefBeanTagProp();
        if (tagProp != null) {
            beanModel.setProperty(obj, tagProp, node.getTagName());
        }

        node.forEachAttr((name, vl) -> {
            try {
                IXDefAttribute attr = defNode.getAttribute(name);
                if (attr == null) {
                    if (beanModel.isAllowSetExtProperty())
                        beanModel.setProperty(obj, name, vl.getValue());
                } else {
                    Object value = parseValue(vl, name, attr.getType());
                    String propName = attr.getPropName();
                    if (propName == null)
                        propName = name;
                    beanModel.setProperty(obj, propName, value);
                }
            } catch (NopException e) {
                e.addXplStack(node);
                throw NopException.adapt(e);
            }
        });

        // 节点上缺失的属性需要设置缺省值
        defNode.getAttributes().forEach((name, attr) -> {
            if (!node.hasAttr(name)) {
                Object defaultValue = attr.getType().getDefaultValue();
                if (defaultValue != null) {
                    beanModel.setProperty(obj, attr.getPropName(), defaultValue);
                }
            }
        });

        // 节点的注释可能被解析为XDefComment类型的属性。主要是xdef模型定义文件在使用
        if (defNode.getXdefBeanCommentProp() != null) {
            XDefComment comment = XDefComment.parseComment(node);
            if (comment != null) {
                beanModel.setProperty(obj, defNode.getXdefBeanCommentProp(), comment);
            }
        }

        if (defNode.getXdefValue() != null || defNode.getXdefBodyType() != null) {
            if (node.hasBody()) {
                Object value = parseBody(defNode, node);
                beanModel.setProperty(obj, defNode.getXdefBeanBodyProp(), value);
            }
        } else {
            for (XNode child : node.getChildren()) {
                IXDefNode childDef = defNode.getChild(child.getTagName());
                if (childDef == null) {
                    beanModel.setProperty(obj, child.getTagName(), parseExtObject(child));
                } else if (childDef.getXdefUniqueAttr() != null) {
                    String propName = childDef.getXdefBeanProp();
                    if (propName == null) {
                        propName = child.getTagName();
                    }
                    List<Object> list = (List<Object>) beanModel.getProperty(obj, propName);
                    if (list == null || list.isEmpty()) {
                        list = new KeyedList<>(item -> getKey(item, childDef.getXdefUniqueAttr()));
                        beanModel.setProperty(obj, propName, list);
                    }
                    Object childObj = parseObject(childDef, child);
                    list.add(childObj);
                } else {
                    String propName = childDef.getXdefBeanProp();
                    if (propName == null)
                        propName = child.getTagName();
                    Object value = transformToObject(childDef, child);
                    beanModel.setProperty(obj, propName, value);
                }
            }

            defNode.getChildren().forEach((name, childDef) -> {
                String propName = childDef.getXdefBeanProp();
                if (propName == null)
                    propName = childDef.getTagName();

                if (propName.indexOf(':') > 0)
                    return;

                if (!childDef.isUnknownTag() && beanModel.getPropertyModel(propName) == null) {
                    if (node.childByTag(childDef.getTagName()) == null) {
                        if (childDef.getXdefUniqueAttr() != null || childDef.getXdefKeyAttr() != null) {
                            String keyAttr = childDef.getXdefUniqueProp() != null ? childDef.getXdefUniqueProp() : childDef.getXdefKeyProp();
                            KeyedList<Object> list = new KeyedList<>(item -> getKey(item, keyAttr));
                            beanModel.setProperty(obj, propName, list);
                        } else {
                            beanModel.setProperty(obj, propName, null);
                        }
                    }
                }
            });
        }
        return obj;
    }
}