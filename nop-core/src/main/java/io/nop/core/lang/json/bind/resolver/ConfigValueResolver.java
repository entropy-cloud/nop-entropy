/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.bind.resolver;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.bind.IValueResolver;
import io.nop.core.lang.json.bind.ValueResolverCompileOptions;

import java.util.List;

/**
 * 获取配置变量的值，允许设置缺省值。例如 @cfg: a.b.c,b.c.e|3 表示获取配置项a.b.c或者b.c.e的值， 如果这些配置项都不存在，则返回缺省值3。
 */
public class ConfigValueResolver implements IValueResolver {
    private final List<String> configKeys;
    private final Object defaultValue;

    public ConfigValueResolver(List<String> configKeys, Object defaultValue) {
        this.configKeys = configKeys;
        this.defaultValue = defaultValue;
    }

    @Override
    public Object resolveValue(IEvalContext ctx) {
        for (String key : configKeys) {
            Object value = AppConfig.var(key);
            if (value != null)
                return value;
        }
        return defaultValue;
    }

    public static IValueResolver compile(SourceLocation loc, Object value, ValueResolverCompileOptions options) {
        String config = ValueResolverCompileHelper.getStringConfig("cfg", loc, value);
        if (StringHelper.isEmpty(config))
            return null;

        int pos = config.indexOf('|');
        Object defaultValue = null;
        if (pos >= 0) {
            config = config.substring(0, pos);
            defaultValue = JsonTool.parseSimpleJsonValue(config.substring(pos + 1).trim());
        }
        List<String> configKeys = StringHelper.stripedSplit(config, ',');
        return new ConfigValueResolver(configKeys, defaultValue);
    }
}
