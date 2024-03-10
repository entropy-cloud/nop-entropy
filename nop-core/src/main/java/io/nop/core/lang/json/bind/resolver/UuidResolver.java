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

/**
 * 生成指定字节个数的随机文本串。例如 @uuid:16
 */
public class UuidResolver implements IValueResolver {
    private final int len;

    public UuidResolver(int len) {
        this.len = len;
    }

    @Override
    public Object resolveValue(IEvalContext ctx) {
        return StringHelper.generateUUID(len);
    }

    public static IValueResolver compile(SourceLocation loc, Object value, ValueResolverCompileOptions options) {
        String config = ValueResolverCompileHelper.getStringConfig("uuid", loc, value);
        if (StringHelper.isEmpty(config)) {
            return new UuidResolver(32);
        } else {
            int len = ConvertHelper.toPrimitiveInt(config.trim(), NopException::new);
            return new UuidResolver(len);
        }
    }
}