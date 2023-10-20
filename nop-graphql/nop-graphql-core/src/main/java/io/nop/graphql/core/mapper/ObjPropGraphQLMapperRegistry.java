/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.mapper;

import io.nop.xlang.xmeta.mapper.ObjPropMapperRegistry;

public class ObjPropGraphQLMapperRegistry {
    static final ObjPropMapperRegistry _instance = new ObjPropMapperRegistry();

    public static ObjPropMapperRegistry instance() {
        return _instance;
    }
}
