/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.bind.resolver;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.json.bind.IValueResolver;
import io.nop.core.lang.json.bind.ValueResolverCompileOptions;

/**
 * 根据资源路径装载文本文件内容，例如 @load:/my.txt
 */
public class EmptyTextResolver implements IValueResolver {

    @Override
    public String resolveValue(IEvalContext ctx) {
        return "";
    }

    public static IValueResolver compile(SourceLocation loc, Object value, ValueResolverCompileOptions options) {
        return new EmptyTextResolver();
    }
}