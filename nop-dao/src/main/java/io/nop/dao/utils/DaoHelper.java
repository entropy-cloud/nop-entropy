/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.utils;

import io.nop.commons.util.StringHelper;
import io.nop.dao.DaoConstants;

import java.util.Collections;
import java.util.Map;

import static io.nop.dao.DaoConfigs.CFG_QUERY_SPACE_TO_DIALECT;

public class DaoHelper {
    public static boolean isDefaultQuerySpace(String querySpace) {
        return StringHelper.isEmpty(querySpace) || DaoConstants.DEFAULT_QUERY_SPACE.equals(querySpace);
    }

    public static String normalizeQuerySpace(String querySpace) {
        if (querySpace == null)
            return DaoConstants.DEFAULT_QUERY_SPACE;
        return querySpace;
    }

    public static String getChangeType(Map<String, Object> map) {
        return StringHelper.toString(map.get(DaoConstants.PROP_CHANGE_TYPE), "");
    }

    public static Map<String, String> getQuerySpaceToDialectConfig() {
        String text = CFG_QUERY_SPACE_TO_DIALECT.get();
        if (StringHelper.isEmpty(text))
            return Collections.emptyMap();

        Map<String, String> map = StringHelper.parseStringMap(text, '=', ',');
        if (map == null)
            map = Collections.emptyMap();
        return map;
    }
}
