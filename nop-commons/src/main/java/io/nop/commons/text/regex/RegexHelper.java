/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text.regex;

import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.text.regex.impl.JdkRegexCompiler;

import static io.nop.commons.CommonConfigs.CFG_REGEX_COMPILE_CACHE_SIZE;
import static io.nop.commons.cache.CacheConfig.newConfig;

public class RegexHelper {
    static final ICache<String, IRegex> s_cache = LocalCache.newCache("regex-compile-cache",
            newConfig(CFG_REGEX_COMPILE_CACHE_SIZE.get()), null);

    static IRegexCompiler s_compiler = JdkRegexCompiler.INSTANCE;

    public static void registerRegexCompiler(IRegexCompiler regexCompiler) {
        s_compiler = regexCompiler;
    }

    public static IRegex fromPattern(String pattern) {
        return s_cache.computeIfAbsent(pattern, p -> {
            return compileRegex(pattern);
        });
    }

    public static IRegex compileRegex(String pattern) {
        return s_compiler.compileRegex(pattern);
    }
}