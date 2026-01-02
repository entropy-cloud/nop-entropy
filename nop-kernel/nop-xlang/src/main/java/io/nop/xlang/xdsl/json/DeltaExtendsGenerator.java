/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl.json;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.delta.IDeltaExtendsGenerator;
import io.nop.core.lang.xml.XJsonNode;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;

import java.util.Map;

public class DeltaExtendsGenerator implements IDeltaExtendsGenerator {
    public static final DeltaExtendsGenerator INSTANCE = new DeltaExtendsGenerator();

    @Override
    public Object genExtends(SourceLocation loc, Object source, Map<String, Object> json) {
        XNode node = toNode(loc, source);
        XLangCompileTool cp = XLang.newCompileTool();
        ExprEvalAction action = cp.compileXjson(node);
        if (action == null)
            return null;
        return JsonTool.beanToJsonObject(action.invoke(XLang.newEvalScope()), true);
    }

    XNode toNode(SourceLocation loc, Object source) {
        if (source instanceof XNode)
            return (XNode) source;

        if (source instanceof XJsonNode)
            return ((XJsonNode) source).getNode();

        if (!(source instanceof String)) {
            throw new IllegalArgumentException("nop.err.json.source-not-string:" + source);
        }
        return XNodeParser.instance().forFragments(true).parseFromText(loc, (String) source);
    }
}
