/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.utils;

import io.nop.commons.util.StringHelper;
import io.nop.dao.DaoConstants;

public class DaoHelper {
    public static boolean isDefaultQuerySpace(String querySpace) {
        return StringHelper.isEmpty(querySpace) || DaoConstants.DEFAULT_QUERY_SPACE.equals(querySpace);
    }

    public static String normalizeQuerySpace(String querySpace) {
        if (querySpace == null)
            return DaoConstants.DEFAULT_QUERY_SPACE;
        return querySpace;
    }
}
