/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef.impl;

import io.nop.commons.util.StringHelper;

import java.util.Set;

public class XDefHelper {
    public static boolean isLocalRef(String refName) {
        if (refName.startsWith("@"))
            return true;
        return refName.indexOf('.') < 0 || refName.indexOf('#') > 0;
    }

    public static String buildFullRefPath(String path, String refName) {
        if (refName.startsWith("@") || refName.indexOf('#') > 0)
            return refName;

        if (isLocalRef(refName)) {
            return path + '#' + refName;
        }
        return StringHelper.absolutePath(path, refName);
    }

    public static String getLocalRef(String ref) {
        if (ref == null)
            return null;
        if (ref.startsWith("@"))
            return ref;

        int pos = ref.indexOf('#');
        if (pos > 0) {
            return ref.substring(pos + 1);
        }
        if (ref.indexOf('.') < 0)
            return ref;
        return null;
    }

    public static String buildPropName(Set<String> propNs, String name) {
        if (name.indexOf(':') > 0) {
            if (propNs != null) {
                String ns = StringHelper.getNamespace(name);
                if (propNs.contains(ns)) {
                    return StringHelper.xmlNameToPropName(name);
                }
            }
            return name;
        }
        return StringHelper.xmlNameToPropName(name);
    }
    //
    // public static String xmlNameToPropName(String name) {
    // return StringHelper.xmlNameToVarName(name);
    // }
    //
    // public static void parseSimpleSchemaAttrs(XNode valueNode, SchemaImpl schema) {
    // String pattern = valueNode.attrText(XDslConstants.ATTR_PATTERN);
    // Double min = valueNode.attrDouble(ATTR_MIN);
    // Double max = valueNode.attrDouble(ATTR_MAX);
    // Integer minLength = valueNode.attrInt(ATTR_MIN_LENGTH);
    // Integer maxLength = valueNode.attrInt(ATTR_MAX_LENGTH);
    // String dict = valueNode.attrText(ATTR_DICT);
    // Integer precision = valueNode.attrInt(ATTR_PRECISION);
    // Integer scale = valueNode.attrInt(ATTR_SCALE);
    // Boolean excludeMin = valueNode.attrBoolean(ATTR_EXCLUDE_MIN);
    // Boolean excludeMax = valueNode.attrBoolean(ATTR_EXCLUDE_MAX);
    // Integer multipleOf = valueNode.attrInt(ATTR_MULTIPLE_OF);
    //
    // schema.setDict(dict);
    // schema.setPattern(pattern);
    // schema.setMin(min);
    // schema.setMax(max);
    // schema.setMinLength(minLength);
    // schema.setMaxLength(maxLength);
    // schema.setPrecision(precision);
    // schema.setScale(scale);
    // schema.setExcludeMax(excludeMax);
    // schema.setExcludeMin(excludeMin);
    // schema.setMultipleOf(multipleOf);
    // }
    //
    // public static void parseProp(XNode node, ObjPropMetaImpl prop) {
    // Integer propId = node.attrInt(ATTR_PROP_ID);
    // Set<String> depends = node.attrCsvSet(ATTR_DEPENDS);
    // prop.setPropId(propId);
    // prop.setDepends(depends);
    // }
}
