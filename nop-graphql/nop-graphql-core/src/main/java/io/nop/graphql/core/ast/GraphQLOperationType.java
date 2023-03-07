/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import io.nop.commons.util.StringHelper;

public enum GraphQLOperationType {
    query, mutation, subscription, action; // 内部调用函数

    public String getTypeName() {
        return StringHelper.capitalize(name());
    }
}
