/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.bind.resolver;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.core.lang.json.bind.IValueResolver;
import io.nop.core.lang.json.bind.ValueResolverCompileOptions;

import java.util.List;

/**
 * 获取多语言字符串，例如 @i18n: UserInfo.form.add.title|新增
 */
public class I18nTextResolver implements IValueResolver {
    private final List<String> messageKeys;
    private final String defaultValue;

    public I18nTextResolver(List<String> messageKeys, String defaultValue) {
        this.messageKeys = messageKeys;
        this.defaultValue = defaultValue;
    }

    @Override
    public Object resolveValue(IEvalContext ctx) {
        String locale = ContextProvider.currentLocale();

        for (String key : messageKeys) {
            String value = I18nMessageManager.instance().getMessage(locale, key, null);
            if (value != null)
                return value;
        }

        return defaultValue;
    }

    public static IValueResolver compile(SourceLocation loc, Object value, ValueResolverCompileOptions options) {
        String config = ValueResolverCompileHelper.getStringConfig("i18n", loc, value);
        if (StringHelper.isEmpty(config))
            return null;

        String defaultValue = null;
        // 废除@i18n: ?key这种形式
        if (config.startsWith("?")) {
            throw new UnsupportedOperationException("nop.core.optional-i18n-string-is-deprecated:" + config);
        }

        int pos = config.indexOf('|');
        if (pos > 0) {
            defaultValue = config.substring(pos + 1);
            config = config.substring(0, pos);
        }

        List<String> keys = StringHelper.stripedSplit(config, ',');
        return new I18nTextResolver(keys, defaultValue);
    }
}
