/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.bind.resolver;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.json.bind.IValueResolver;
import io.nop.core.lang.json.bind.ValueResolverCompileOptions;

import java.util.List;

/**
 * 生成指定字节个数的随机文本串。例如 @uuid:16
 */
public class UuidResolver implements IValueResolver {
    private final String prefix;
    private final int len;

    private final String postfix;

    public UuidResolver(int len, String prefix, String postfix) {
        this.len = len;
        this.prefix = prefix;
        this.postfix = postfix;
    }

    @Override
    public Object resolveValue(IEvalContext ctx) {
        String rand = StringHelper.generateUUID(len);
        if (prefix == null && postfix == null)
            return rand;
        return (prefix == null ? "" : prefix) + rand + (postfix == null ? "" : postfix);
    }

    public static IValueResolver compile(SourceLocation loc, Object value, ValueResolverCompileOptions options) {
        String config = ValueResolverCompileHelper.getStringConfig("uuid", loc, value);
        if (StringHelper.isEmpty(config)) {
            return new UuidResolver(32, null, null);
        } else {
            String trimed = config.trim();
            if (trimed.indexOf(',') < 0) {
                int len = ConvertHelper.toPrimitiveInt(trimed, NopException::new);
                return new UuidResolver(len, null, null);
            } else {
                List<String> parts = StringHelper.split(trimed, ',');
                int len = ConvertHelper.toPrimitiveInt(parts.get(0), NopException::new);
                String prefix = parts.get(1).trim();
                String postfix = parts.size() > 2 ? parts.get(2) : null;
                return new UuidResolver(len, prefix, postfix);
            }
        }
    }
}