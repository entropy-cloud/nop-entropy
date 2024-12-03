/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.bind.resolver;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.json.bind.IValueResolver;
import io.nop.core.lang.json.bind.ValueResolverCompileOptions;

/**
 * 得到scope中的变量值。例如 @var: abc
 */
public class ScopeVarResolver implements IValueResolver {
    private final String varName;

    public ScopeVarResolver(String varName) {
        this.varName = varName;
    }

    @Override
    public Object resolveValue(IEvalContext ctx) {
        return ctx.getEvalScope().getValueByPropPath(varName);
    }

    public static IValueResolver compile(SourceLocation loc, Object value, ValueResolverCompileOptions options) {
        String config = ValueResolverCompileHelper.getStringConfig("var", loc, value);
        if (StringHelper.isEmpty(config)) {
            return null;
        }
        return new ScopeVarResolver(config);
    }
}