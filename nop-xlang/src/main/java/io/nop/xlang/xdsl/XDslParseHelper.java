/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.collections.IKeyedElement;
import io.nop.commons.collections.KeyedList;
import io.nop.commons.text.tokenizer.SimpleTextReader;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.parse.GenericTypeParser;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.parse.XDefTypeDeclParser;
import io.nop.xlang.xdsl.json.DslModelToXNodeTransformer;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;
import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_ATTR_VALUE;
import static io.nop.xlang.XLangErrors.ARG_ENUM_CLASS;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_ALLOWED;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_VALID_BOOLEAN;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_VALID_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_VALID_DEF_TYPE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_VALID_ENUM_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_VALID_GENERIC_TYPE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_VALID_LOCAL_REF;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_VALID_PROP_NAME;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_VALID_V_PATH;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_VALID_XML_NAME;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_VALUE_IS_EMPTY;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NODE_DUPLICATE_CHILD_FOR_MAP;
import static io.nop.xlang.XLangErrors.ERR_XDSL_TAG_NAME_NOT_ALLOWED;
import static io.nop.xlang.XLangErrors.ERR_XDSL_VALUE_NOT_VALID_DEF_TYPE;
import static io.nop.xlang.XLangErrors.ERR_XPL_ENUM_NO_FACTORY_METHOD;

public class XDslParseHelper {
    public static NopException newAttrError(ErrorCode err, XNode node, String attrName) {
        ValueWithLocation attr = node.attrValueLoc(attrName);
        return new NopException(err).source(attr).param(ARG_ATTR_VALUE, attr.getValue()).param(ARG_NODE, node)
                .param(ARG_ATTR_NAME, attrName);
    }

    public static NopException newAttrIsEmptyError(XNode node, String attrName) {
        return newAttrError(ERR_XDSL_ATTR_VALUE_IS_EMPTY, node, attrName);
    }

    public static <T extends IKeyedElement> KeyedList<T> parseChildrenAsList(XNode node,
                                                                             Function<XNode, T> nodeParser) {
        KeyedList<T> list = new KeyedList<>(node.getChildCount(), item -> item.key());
        for (XNode child : node.getChildren()) {
            T obj = nodeParser.apply(child);
            list.add(obj);
        }
        return list;
    }

    public static <T extends IKeyedElement> Map<String, T> parseChildrenAsMap(XNode node,
                                                                              Function<XNode, T> nodeParser) {
        Map<String, T> map = new HashMap<>(node.getChildCount());
        for (XNode child : node.getChildren()) {
            T obj = nodeParser.apply(child);
            String key = child.getTagName();
            T old = map.put(key, obj);
            if (old != null)
                throw new NopException(ERR_XDSL_NODE_DUPLICATE_CHILD_FOR_MAP).param(ARG_NODE, child).param(ARG_TAG_NAME,
                        child.getTagName());
        }
        return map;
    }

    public static <T extends IKeyedElement> KeyedList<T> parseSelectedChildrenAsList(XNode node, String tagName,
                                                                                     Function<XNode, T> nodeParser) {
        KeyedList<T> list = new KeyedList<>(node.getChildCount(), item -> item.key());
        for (XNode child : node.getChildren()) {
            if (child.getTagName().equals(tagName)) {
                T obj = nodeParser.apply(child);
                list.add(obj);
            }
        }
        return list;
    }

    public static String parseAttrClassName(XNode node, String attrName) {
        String text = node.attrText(attrName);
        if (text == null)
            return null;
        if (!StringHelper.isValidClassName(text))
            throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_CLASS_NAME, node, attrName);
        return text;
    }

    public static String requireAttrClassName(XNode node, String attrName) {
        String text = parseAttrClassName(node, attrName);
        if (text == null)
            throw newAttrIsEmptyError(node, attrName);
        return text;
    }

    public static String parseAttrVPath(XNode node, String attrName) {
        String text = node.attrText(attrName);
        if (text == null)
            return null;
        if (!StringHelper.isValidVPath(text))
            throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_V_PATH, node, attrName);

        SourceLocation loc = node.attrLoc(attrName);
        if (loc != null) {
            text = StringHelper.absolutePath(loc.getPath(), text);
        }
        return text;
    }

    public static String requireAttrVPath(XNode node, String attrName) {
        String text = parseAttrVPath(node, attrName);
        if (text == null)
            throw newAttrIsEmptyError(node, attrName);
        return text;
    }

    public static String parseAttrPropName(XNode node, String attrName) {
        String text = node.attrText(attrName);
        if (text == null)
            return null;
        if (!StringHelper.isValidPropName(text))
            throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_PROP_NAME, node, attrName);

        return text;
    }

    public static Boolean parseAttrBoolean(XNode node, String attrName, Boolean defaultValue) {
        Object value = node.getAttr(attrName);
        if (StringHelper.isEmptyObject(value))
            return defaultValue;

        if (value instanceof Boolean)
            return (Boolean) value;

        if (value instanceof String) {
            if ("true".equals(value) || "1".equals(value))
                return true;
            if ("false".equals(value) || "0".equals(value))
                return false;
        }

        throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_BOOLEAN, node, attrName);
    }

    public static String requireAttrPropName(XNode node, String attrName) {
        String text = parseAttrPropName(node, attrName);
        if (text == null)
            throw newAttrIsEmptyError(node, attrName);
        return text;
    }

    public static String parseAttrXmlName(XNode node, String attrName) {
        String text = node.attrText(attrName);
        if (text == null)
            return null;
        if (!StringHelper.isValidXmlName(text, true, true))
            throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_XML_NAME, node, attrName);

        return text;
    }

    public static String requireAttrXmlName(XNode node, String attrName) {
        String text = parseAttrXmlName(node, attrName);
        if (text == null)
            throw newAttrIsEmptyError(node, attrName);
        return text;
    }

    public static <T> T parseAttrEnumValue(XNode node, String attrName, Class<T> enumClass) {
        String text = node.attrText(attrName);
        if (text == null)
            return null;

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(enumClass);
        IFunctionModel fn = beanModel.getFactoryMethod();
        if (fn == null) {
            throw newAttrError(ERR_XPL_ENUM_NO_FACTORY_METHOD, node, attrName).param(ARG_CLASS_NAME,
                    enumClass.getName());
        }
        T value = (T) fn.call1(null, text, DisabledEvalScope.INSTANCE);
        if (value == null)
            throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_ENUM_VALUE, node, attrName).param(ARG_ENUM_CLASS, enumClass);
        return value;
    }

    public static <T> T parseAttrEnumValue(XNode node, String attrName, Class<T> enumClass,
                                           Function<String, T> factoryMethod) {
        String text = node.attrText(attrName);
        if (text == null)
            return null;

        T value = factoryMethod.apply(text);
        if (value == null)
            throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_ENUM_VALUE, node, attrName).param(ARG_ENUM_CLASS, enumClass);
        return value;
    }

    public static <T> T parseAttrEnumValue(XNode node, String attrName, T defaultValue, Class<T> enumClass,
                                           Function<String, T> factoryMethod) {
        String text = node.attrText(attrName);
        if (text == null)
            return defaultValue;

        T value = factoryMethod.apply(text);
        if (value == null)
            throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_ENUM_VALUE, node, attrName).param(ARG_ENUM_CLASS, enumClass);
        return value;
    }

    public static String parseAttrLocalRefName(XNode node, String attrName) {
        String text = node.attrText(attrName);
        if (text == null)
            return null;
        if (!StringHelper.isValidSimpleVarName(text))
            throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_LOCAL_REF, node, attrName);
        return text;
    }

    public static String parseAttrRefName(XNode node, String attrName) {
        String text = node.attrText(attrName);
        if (text == null)
            return null;
        if (!StringHelper.isValidVPath(text))
            throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_LOCAL_REF, node, attrName);
        return text;
    }

    public static List<IGenericType> parseAttrGenericTypes(XNode node, String attrName, IRawTypeResolver resolver) {
        Set<String> typeNames = node.attrCsvSet(attrName);
        if (typeNames == null || typeNames.isEmpty())
            return Collections.emptyList();

        SourceLocation loc = node.attrLoc(attrName);
        try {
            return typeNames.stream().map(typeName -> {
                return parseGenericType(loc, typeName, resolver);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_GENERIC_TYPE, node, attrName).cause(e);
        }
    }

    public static IGenericType parseAttrGenericType(XNode node, String attrName, IRawTypeResolver resolver) {
        String text = node.attrText(attrName);
        if (text == null)
            return null;

        SourceLocation loc = node.attrLoc(attrName);
        try {
            return parseGenericType(loc, text, resolver);
        } catch (Exception e) {
            throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_GENERIC_TYPE, node, attrName).cause(e);
        }
    }

    public static IGenericType parseGenericType(SourceLocation loc, String text, IRawTypeResolver resolver) {
        GenericTypeParser parser = new GenericTypeParser();
        if (resolver != null)
            parser.rawTypeResolver(resolver);
        return parser.intern(true).parseFromText(loc, text);
    }

    public static IGenericType requireAttrGenericType(XNode node, String attrName, IRawTypeResolver resolver) {
        IGenericType type = parseAttrGenericType(node, attrName, resolver);
        if (type == null)
            throw newAttrIsEmptyError(node, attrName);
        return type;
    }

    static final ICache<String, XDefTypeDecl> defTypeCache = LocalCache.newCache("def-type-parse-cache",
            newConfig(1000));

    public static XDefTypeDecl parseAttrDefType(XNode node, String attrName) {
        String text = node.attrText(attrName);
        if (text == null)
            return null;

        SourceLocation loc = node.attrLoc(attrName);
        try {
            return defTypeCache.computeIfAbsent(text, k -> new XDefTypeDeclParser().parseFromText(loc, text));
        } catch (Exception e) {
            throw newAttrError(ERR_XDSL_ATTR_NOT_VALID_DEF_TYPE, node, attrName).cause(e);
        }
    }

    public static XDefTypeDecl parseDefType(SourceLocation loc, String propName, String text) {
        try {
            return defTypeCache.computeIfAbsent(text, k -> new XDefTypeDeclParser().parseFromText(loc, text));
        } catch (Exception e) {
            throw new NopException(ERR_XDSL_VALUE_NOT_VALID_DEF_TYPE).param(ARG_PROP_NAME, propName)
                    .param(ARG_VALUE, text).cause(e);
        }
    }

    public static XDefTypeDecl cacheDefType(XDefTypeDecl type) {
        return defTypeCache.computeIfAbsent(type.toString(), k -> type);
    }

    public static void clearDefTypeCache() {
        defTypeCache.clear();
    }

    public static XDefTypeDecl requireAttrDefType(XNode node, String attrName) {
        XDefTypeDecl type = parseAttrDefType(node, attrName);
        if (type == null)
            throw newAttrIsEmptyError(node, attrName);
        return type;
    }

    public static void checkAttrNames(XNode node, String namespace, Set<String> allowedNames) {
        node.forEachAttr((name, v) -> {
            if (namespace == null || StringHelper.startsWithNamespace(name, namespace))
                if (!allowedNames.contains(name))
                    throw newAttrError(ERR_XDSL_ATTR_NOT_ALLOWED, node, name).param(ARG_ALLOWED_NAMES, allowedNames);
        });
    }

    public static void checkChildNames(XNode node, String namespace, Set<String> allowedNames) {
        node.forEachChild(child -> {
            checkTagNames(child, namespace, allowedNames);
        });
    }

    public static void checkTagNames(XNode node, String namespace, Set<String> allowedNames) {
        String name = node.getTagName();
        if (namespace == null || StringHelper.startsWithNamespace(name, namespace))
            if (!allowedNames.contains(name))
                throw new NopException(ERR_XDSL_TAG_NAME_NOT_ALLOWED).param(ARG_NODE, node).param(ARG_TAG_NAME, name)
                        .param(ARG_ALLOWED_NAMES, allowedNames);
    }

    public static XNode parseSchema(SourceLocation loc, String text) {
        IObjMeta objMeta = SchemaLoader.loadXMeta(XLangConstants.XDSL_SCHEMA_SCHEMA);
        XNode schema = XDslParseHelper.parseXJson(loc, text, objMeta);
        return schema;
    }

    public static XNode parseXJson(SourceLocation loc, String text, IObjMeta objMeta) {
        if (StringHelper.isEmpty(text))
            return null;

        SimpleTextReader reader = new SimpleTextReader(loc, text);
        reader.skipBlank();
        XNode node;
        if (reader.startsWith("<")) {
            node = XNodeParser.instance().parseFromText(loc, text);
        } else if (reader.startsWith("{")) {
            Map<String, Object> map = (Map<String, Object>) JsonTool.parseNonStrict(loc, text);
            if (objMeta == null) {
                node = XNode.fromTreeBean(TreeBean.createFromJson(map));
            } else {
                node = new DslModelToXNodeTransformer(objMeta).transformToXNode(map);
            }
        } else {
            node = XNode.make(CoreConstants.DUMMY_TAG_NAME);
            node.content(loc, text);
        }
        if (objMeta != null && objMeta.getXmlName() != null) {
            node.setTagName(objMeta.getXmlName());
        }
        return node;
    }
}
