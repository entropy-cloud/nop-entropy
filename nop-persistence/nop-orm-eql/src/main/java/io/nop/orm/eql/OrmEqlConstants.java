/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

public interface OrmEqlConstants {
    String PROP_ID = "id";

    String PREFIX_PLACEHOLDER = "{prefix}";

    String DECORATOR_DUMP = "dump";
    String DECORATOR_QUERY_SPACE = "querySpace";

    String DECORATOR_ENABLE_FILTER = "enableFilter";


    String FUNC_COUNT = "count";

    String MARKER_TENANT_ID = "tenantId";

    String FEATURE_SUPPORT_RETURNING_FOR_UPDATE = "supportReturningForUpdate";

    String VAR_PARAMS = "params";

    String VAR_O = "o";
}
