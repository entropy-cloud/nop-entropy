/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.orm;

import io.nop.api.core.exceptions.ErrorCode;

public interface GraphQLOrmErrors {
    String ARG_PROP_NAME = "propName";

    ErrorCode ERR_BIZ_CONNECTION_PROP_NOT_RELATION =
            ErrorCode.define("nop.err.biz.connection-prop-not-relation",
                    "属性[propName]不是关联属性", ARG_PROP_NAME);

}
