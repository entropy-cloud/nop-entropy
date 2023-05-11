/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.HashMap;
import java.util.Map;

public class InternalTagCompilers {
    static Map<String, IXplTagCompiler> tags = new HashMap<>();

    static {
        registerTagCompiler("c:break", BreakTagCompiler.INSTANCE);
        registerTagCompiler("c:predicate", PredicateTagCompiler.INSTANCE);
        registerTagCompiler("c:choose", ChooseTagCompiler.INSTANCE);
        registerTagCompiler("c:collect", CollectTagCompiler.INSTANCE);
        registerTagCompiler("c:continue", ContinueTagCompiler.INSTANCE);
        registerTagCompiler("c:for", ForTagCompiler.INSTANCE);
        registerTagCompiler("c:if", IfTagCompiler.INSTANCE);
        registerTagCompiler("c:import", ImportTagCompiler.INSTANCE);
        registerTagCompiler("c:include", IncludeTagCompiler.INSTANCE);
        // registerTagCompiler("c:tags", LibTagCompiler.INSTANCE);
        registerTagCompiler("c:log", LogTagCompiler.INSTANCE);
        registerTagCompiler("c:out", OutTagCompiler.INSTANCE);
        registerTagCompiler("c:print", PrintTagCompiler.INSTANCE);
        registerTagCompiler("c:return", ReturnTagCompiler.INSTANCE);
        registerTagCompiler("c:script", ScriptTagCompiler.INSTANCE);
        registerTagCompiler("c:throw", ThrowTagCompiler.INSTANCE);
        registerTagCompiler("c:try", TryTagCompiler.INSTANCE);
        registerTagCompiler("c:unit", UnitTagCompiler.INSTANCE);
        registerTagCompiler("c:var", VarTagCompiler.INSTANCE);
        registerTagCompiler("c:while", WhileTagCompiler.INSTANCE);

        registerTagCompiler("macro:var", MacroVarTagCompiler.INSTANCE);
        registerTagCompiler("macro:gen", MacroGenTagCompiler.INSTANCE);
        registerTagCompiler("macro:script", MacroScriptTagCompiler.INSTANCE);
        registerTagCompiler("macro:for", MacroForTagCompiler.INSTANCE);
        registerTagCompiler("macro:if", MacroIfTagCompiler.INSTANCE);
    }

    public static void registerTagCompiler(String tagName, IXplTagCompiler cp) {
        tags.put(tagName, cp);
    }

    public static IXplTagCompiler getTagCompiler(String tagName) {
        return tags.get(tagName);
    }
}