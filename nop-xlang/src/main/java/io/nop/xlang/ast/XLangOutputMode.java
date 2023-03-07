/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Label;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.exceptions.NopException;

import java.util.HashMap;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_OUTPUT_MODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_UNKNOWN_OUTPUT_MODE;

@Locale("zh-CN")
public enum XLangOutputMode {
    @Label("输出XNode节点")
    node,
    @Label("输出XML文本")
    xml, // 输出的文本需要进行xml转义

    @Label("输出HTML文本")
    html,

    @Label("输出纯文本")
    text, // 输出文本，不进行xml转义

    @Label("输出SQL语句")
    @Description("通过MutableString来收集sql文本和参数，通过表达式输出变量时将作为sql参数被收集起来，而不是直接作为文本拼接。")
    sql, // 通过MutableString来收集sql文本和参数，通过表达式输出变量时将作为sql参数被收集起来，而不是直接作为文本拼接。

    @Label("输出JSON对象")
    xjson,

    @Label("不允许输出")
    none;

    static final Map<String, XLangOutputMode> textMap = new HashMap<>();

    static {
        for (XLangOutputMode mode : XLangOutputMode.values()) {
            textMap.put(mode.name().toLowerCase(), mode);
        }
    }

    public boolean isAllowTextOut() {
        return this == xml || this == html || this == text;
    }

    public boolean isXmlOrHtml() {
        return this == xml || this == html;
    }

    public String toString() {
        return name().toLowerCase();
    }

    @StaticFactoryMethod
    public static XLangOutputMode fromText(String text) {
        if (text == null)
            return null;
        return textMap.get(text);
    }

    public static XLangOutputMode requireFromText(String text) {
        XLangOutputMode mode = textMap.get(text);
        if (mode == null)
            throw new NopException(ERR_XPL_UNKNOWN_OUTPUT_MODE).param(ARG_OUTPUT_MODE, text);
        return mode;
    }
}