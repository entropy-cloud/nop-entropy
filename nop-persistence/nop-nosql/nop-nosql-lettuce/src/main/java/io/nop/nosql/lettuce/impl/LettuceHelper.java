/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.KeyValue;
import io.nop.commons.util.CollectionHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LettuceHelper {
    public static Map<String, Object> toMap(List<KeyValue<String, Object>> list) {
        if (list == null || list.isEmpty())
            return new HashMap<>(0);

        Map<String, Object> ret = CollectionHelper.newHashMap(list.size());
        for (KeyValue<String, Object> pair : list) {
            ret.put(pair.getKey(), pair.getValue());
        }
        return ret;
    }
}
