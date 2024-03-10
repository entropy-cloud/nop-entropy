/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLValue;

import java.util.Map;

public abstract class GraphQLValue extends _GraphQLValue {
    public abstract Object buildValue(Map<String, Object> vars);

    /**
     * 是否包含GraphQLVariable。如果不包含，则表示为固定值
     */
    public abstract boolean containsVariable();
}