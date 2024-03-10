/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.script;

import io.nop.api.core.annotations.core.GlobalInstance;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一管理XLang中可以嵌入使用的外部脚本引擎
 */
@GlobalInstance
public class ScriptCompilerRegistry {
    private static final ScriptCompilerRegistry _instance = new ScriptCompilerRegistry();

    public static ScriptCompilerRegistry instance() {
        return _instance;
    }

    private Map<String, IScriptCompiler> compilers = new ConcurrentHashMap<>();

    public void registerCompiler(String lang, IScriptCompiler compiler) {
        this.compilers.put(lang, compiler);
    }

    public void unregisterCompiler(String lang, IScriptCompiler compiler) {
        this.compilers.remove(lang, compiler);
    }

    public IScriptCompiler getCompiler(String lang) {
        return compilers.get(lang);
    }

    public Set<String> getRegisteredLanguages() {
        return compilers.keySet();
    }
}
