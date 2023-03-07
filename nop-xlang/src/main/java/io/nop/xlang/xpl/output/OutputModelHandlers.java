/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.output;

import io.nop.xlang.ast.XLangOutputMode;

import java.util.EnumMap;
import java.util.Map;

public class OutputModelHandlers {
    static final Map<XLangOutputMode, IXplUnknownTagCompiler> handlers = new EnumMap<>(XLangOutputMode.class);

    static {
        registerHandler(XLangOutputMode.html, HtmlOutputTagCompiler.INSTANCE);
        registerHandler(XLangOutputMode.xml, XmlOutputTagCompiler.INSTANCE);
        registerHandler(XLangOutputMode.text, TextOutputTagCompiler.INSTANCE);
        registerHandler(XLangOutputMode.none, NoneOutputTagCompiler.INSTANCE);
        registerHandler(XLangOutputMode.node, NodeOutputTagCompiler.INSTANCE);
        registerHandler(XLangOutputMode.sql, TextOutputTagCompiler.INSTANCE);
        registerHandler(XLangOutputMode.xjson, NodeOutputTagCompiler.INSTANCE);
    }

    public static void registerHandler(XLangOutputMode outputMode, IXplUnknownTagCompiler handler) {
        handlers.put(outputMode, handler);
    }

    public static IXplUnknownTagCompiler getHandler(XLangOutputMode outputMode) {
        return handlers.get(outputMode);
    }
}
