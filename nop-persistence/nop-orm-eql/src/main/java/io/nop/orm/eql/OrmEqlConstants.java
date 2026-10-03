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
    String FEATURE_SUPPORT_LATERAL = "supportLateral";
    // WI2 BUG-6: full [outer] join 编译期能力门
    String FEATURE_SUPPORT_FULL_JOIN = "supportFullJoin";
    String FEATURE_SUPPORT_WINDOW_FRAME_ROWS = "supportWindowFrameRows";
    String FEATURE_SUPPORT_WINDOW_FRAME_RANGE = "supportWindowFrameRange";
    String FEATURE_SUPPORT_WINDOW_FRAME_GROUPS = "supportWindowFrameGroups";

    String VAR_PARAMS = "params";

    String VAR_O = "o";
}
