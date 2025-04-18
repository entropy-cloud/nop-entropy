/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.type.IGenericType;
import io.nop.xlang.api.XLangCompileTool;

import java.util.Collection;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ERR_XDEF_STD_DOMAIN_NOT_SUPPORT_XML_CHILD;

/**
 * 负责解析defType描述文本
 */
public interface IStdDomainHandler {
    String getName();

    default boolean isFixedType() {
        return false;
    }

    default String getXDefPath() {
        return null;
    }

    /**
     * 得到解析结果对应的GenericType类型
     */
    IGenericType getGenericType(boolean mandatory, String options);

    /**
     * 解析XML属性配置
     */
    Object parseProp(String options, SourceLocation loc, String propName, Object text, XLangCompileTool cp);

    default String serializeToString(Object value) {
        if (value == null)
            return null;
        if (value instanceof Collection)
            return StringHelper.join((Collection<?>) value, ",");
        return value.toString();
    }

    default XNode transformToNode(SourceLocation loc, Object value) {
        throw new UnsupportedOperationException("transformToNode:" + getName());
    }

    default String getSerializeFunction() {
        return null;
    }

    default boolean isFullXmlNode() {
        return false;
    }

    /**
     * 是否支持XML格式的配置。
     *
     * @return
     */
    default boolean supportXmlChild() {
        return false;
    }

    /**
     * 解析XML节点配置
     */
    default Object parseXmlChild(String options, XNode body, XLangCompileTool cp) {
        throw new NopException(ERR_XDEF_STD_DOMAIN_NOT_SUPPORT_XML_CHILD).param(ARG_NODE, body).param(ARG_STD_DOMAIN,
                getName());
    }

    void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector);
}