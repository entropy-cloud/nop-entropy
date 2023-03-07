/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.json;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;

import java.lang.reflect.Type;
import java.util.function.Function;

import static io.nop.api.core.ApiErrors.ERR_JSON_PROVIDER_NOT_INITIALIZED;

@Locale("zh-CN")
@Description("提供JSON解析和序列化功能")
public class JSON {
    static IJsonProvider s_provider;

    public static void registerProvider(IJsonProvider provider) {
        s_provider = provider;
    }

    public static IJsonProvider getProvider() {
        return s_provider;
    }

    static private IJsonProvider requireProvider() {
        IJsonProvider provider = s_provider;
        if (provider == null)
            throw new NopException(ERR_JSON_PROVIDER_NOT_INITIALIZED);
        return provider;
    }

    public static String stringify(Object o) {
        return stringify(o, null, null);
    }

    public static String stringify(Object o, Function<String, String> transformer, String indent) {
        return requireProvider().stringify(o, transformer, indent);
    }

    public static String serialize(Object o, boolean pretty) {
        return stringify(o, null, pretty ? "  " : null);
    }

    public static Object parse(String str) {
        JsonParseOptions options = new JsonParseOptions();
        options.setStrictMode(true);
        return requireProvider().parseFromText(null, str, options);
    }

    public static Object parseFromText(SourceLocation loc, String str, JsonParseOptions options) {
        return requireProvider().parseFromText(loc, str, options);
    }

    /**
     * 按照非严格模式来解析，允许注释，可以使用单引号
     */
    public static Object parseNonStrict(SourceLocation loc, String str) {
        JsonParseOptions options = new JsonParseOptions();
        options.setStrictMode(false);
        return requireProvider().parseFromText(loc, str, options);
    }

    public static Object parseToBean(SourceLocation loc, String str, Type targetType,
                                     boolean strictMode, boolean ignoreUnknownProps) {
        JsonParseOptions options = new JsonParseOptions();
        options.setStrictMode(strictMode);
        options.setIgnoreUnknownProp(ignoreUnknownProps);
        options.setTargetType(targetType);
        return parseFromText(loc, str, options);
    }

    public static Object parseToBean(SourceLocation loc, String str, Type targetType) {
        return parseToBean(loc, str, targetType, true, false);
    }
}