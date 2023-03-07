/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.utils;

import io.nop.commons.functional.IEqualsChecker;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JsonMatchHelper {
    public static boolean valueEquals(Object v1, Object v2) {
        if (Objects.equals(v1, v2))
            return true;

        if (v1 instanceof Timestamp) {
            v1 = v1.toString().substring(0, 19);
            v2 = limitLen(v2, 19);

        } else if (v2 instanceof Timestamp) {
            v2 = v2.toString().substring(0, 19);
            v1 = limitLen(v1, 19);
        }

        if (StringHelper.isEmptyObject(v1))
            v1 = null;

        if (StringHelper.isEmptyObject(v2))
            v2 = null;

        if (v1 instanceof String || v2 instanceof String) {
            String s1 = StringHelper.toString(v1, null);
            String s2 = StringHelper.toString(v2, null);

            return Objects.equals(s1, s2);
        }

        return MathHelper.eq(v1, v2);
    }

    static String limitLen(Object v1, int len) {
        if (v1 == null)
            return null;
        String str = v1.toString();
        if (str.length() > len)
            str = str.substring(0, len);
        return str;
    }

    public static boolean deepEquals(Object o1, Object o2, IEqualsChecker checker) {
        if (o1 == o2)
            return true;
        if (o1 instanceof Map) {
            if (o2 instanceof Map) {
                Map<String, Object> m1 = (Map<String, Object>) o1;
                Map<String, Object> m2 = (Map<String, Object>) o2;
                return deepEqualsMap(m1, m2, checker);
            } else {
                return false;
            }
        } else if (o1 instanceof List) {
            if (o2 instanceof List) {
                List<Object> l1 = (List<Object>) o1;
                List<Object> l2 = (List<Object>) o2;
                return deepEqualsList(l1, l2, checker);
            } else {
                return false;
            }
        } else {
            return checker.isEquals(o1, o2);
        }
    }

    public static boolean deepEqualsMap(Map<String, ?> m1, Map<String, ?> m2, IEqualsChecker checker) {
        if (m1.size() != m2.size())
            return false;

        for (Map.Entry<String, ?> entry : m1.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!deepEquals(value, m2.get(key), checker))
                return false;
        }
        return true;
    }

    public static boolean deepEqualsList(List<?> l1, List<?> l2, IEqualsChecker checker) {
        if (l1.size() != l2.size())
            return false;

        for (int i = 0, n = l1.size(); i < n; i++) {
            if (!deepEquals(l1.get(i), l2.get(i), checker))
                return false;
        }
        return true;
    }
}
