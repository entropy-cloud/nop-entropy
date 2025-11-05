/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.reflection;

import io.nop.graphql.core.IGraphQLExecutionContext;

import java.util.Map;

/**
 * 对前台传入的参数进行规范化之后再传给服务函数
 */
public class DummyGraphQLArgsNormalizer implements IGraphQLArgsNormalizer {
    @Override
    public Map<String, Object> normalize(Map<String, Object> args, IGraphQLExecutionContext context) {
        return args;
    }
}
