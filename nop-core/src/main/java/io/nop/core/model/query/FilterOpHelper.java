/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.query;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.text.regex.IRegex;
import io.nop.commons.text.regex.RegexHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class FilterOpHelper {

    public static boolean length(Object value, Object length) {
        Integer len = ConvertHelper.toInt(length, NopException::new);
        if (len == null)
            return true;
        String str = ConvertHelper.toString(value, "");
        return str.length() == len;
    }

    public static boolean regex(Object value, Object pattern) {
        String s1 = ConvertHelper.toString(value, "");
        String s2 = ConvertHelper.toString(pattern, "");
        if (s1.length() <= 0 || s2.length() <= 0)
            return false;

        IRegex regex = RegexHelper.fromPattern(s2);
        return regex.test(s1);
    }

    public static boolean startsWith(Object v1, Object v2) {
        String s1 = ConvertHelper.toString(v1, "");
        String s2 = ConvertHelper.toString(v2, "");
        return s1.startsWith(s2);
    }

    public static boolean endsWith(Object v1, Object v2) {
        String s1 = ConvertHelper.toString(v1, "");
        String s2 = ConvertHelper.toString(v2, "");
        return s1.endsWith(s2);
    }

    public static boolean contains(Object v1, Object v2) {
        String s1 = ConvertHelper.toString(v1, "");
        String s2 = ConvertHelper.toString(v2, "");
        return s1.indexOf(s2) >= 0;
    }

    public static boolean icontains(Object v1, Object v2) {
        String s1 = ConvertHelper.toString(v1, "");
        String s2 = ConvertHelper.toString(v2, "");
        return StringHelper.indexOfIgnoreCase(s1, s2) >= 0;
    }

    public static boolean in(Object v1, Object v2) {
        if (v2 == null)
            return false;

        List list = ConvertHelper.toCsvList(v2, NopException::new);
        for (Object v : list) {
            if (eq(v, v1))
                return true;
        }
        return false;
    }

    public static boolean notIn(Object v1, Object v2) {
        return !in(v1, v2);
    }

    public static boolean eq(Object v1, Object v2) {
        if (v1 instanceof String || v2 instanceof String) {
            String s1 = ConvertHelper.toString(v1, "");
            String s2 = ConvertHelper.toString(v2, "");
            return s1.equals(s2);
        }
        return MathHelper.eq(v1, v2);
    }

    public static boolean ne(Object v1, Object v2) {
        return !eq(v1, v2);
    }

    public static boolean gt(Object v1, Object v2) {
        return MathHelper.gt(v1, v2);
    }

    public static boolean ge(Object v1, Object v2) {
        return MathHelper.ge(v1, v2);
    }

    public static boolean lt(Object v1, Object v2) {
        return MathHelper.lt(v1, v2);
    }

    public static boolean le(Object v1, Object v2) {
        return MathHelper.le(v1, v2);
    }

    public static boolean isEmpty(Object o) {
        return StringHelper.isEmptyObject(o);
    }

    public static boolean isNull(Object o) {
        return o == null;
    }

    public static boolean isTrue(Object o) {
        if (o == null)
            return false;
        Boolean b = ConvertHelper.toBoolean(o);
        return Boolean.TRUE.equals(b);
    }

    public static boolean isFalse(Object o) {
        if (o == null)
            return false;
        return !isTrue(o);
    }

    public static boolean notTrue(Object o) {
        if (o == null)
            return true;
        return !isTrue(o);
    }

    public static boolean notFalse(Object o) {
        if (o == null)
            return true;
        return isTrue(o);
    }

    public static boolean isNumber(Object o) {
        if (o instanceof Number)
            return true;
        if (o instanceof String)
            return StringHelper.isNumber(o.toString());
        return false;
    }

    public static boolean notNumber(Object o) {
        return !isNumber(o);
    }

    public static boolean isBlank(Object o) {
        if (o == null)
            return true;
        return StringHelper.isBlank(o.toString());
    }

    public static boolean notEmpty(Object o) {
        return !isEmpty(o);
    }

    public static boolean notNull(Object o) {
        return !isNull(o);
    }

    public static boolean notBlank(Object o) {
        return !isBlank(o);
    }

    public static boolean between(Object value, Object min, Object max, boolean excludeMin, boolean excludeMax) {
        if (min != null) {
            int cmp1 = MathHelper.compareWithConversion(value, min);
            if (excludeMin ? cmp1 <= 0 : cmp1 < 0) {
                return false;
            }
        }
        if (max != null) {
            int cmp1 = MathHelper.compareWithConversion(value, max);
            if (excludeMax ? cmp1 >= 0 : cmp1 > 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean dateBetween(Object value, Object min, Object max, boolean excludeMin, boolean excludeMax) {
        LocalDate d1 = ConvertHelper.toLocalDate(value, NopException::new);
        if (d1 == null)
            return false;

        LocalDate m1 = ConvertHelper.toLocalDate(min, NopException::new);
        LocalDate m2 = ConvertHelper.toLocalDate(min, NopException::new);

        if (m1 != null) {
            int cmp1 = d1.compareTo(m1);
            if (excludeMin ? cmp1 <= 0 : cmp1 < 0) {
                return false;
            }
        }
        if (m2 != null) {
            int cmp1 = d1.compareTo(m2);
            if (excludeMax ? cmp1 >= 0 : cmp1 > 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean dateTimeBetween(Object value, Object min, Object max, boolean excludeMin,
                                          boolean excludeMax) {
        LocalDateTime d1 = ConvertHelper.toLocalDateTime(value, NopException::new);
        if (d1 == null)
            return false;

        LocalDateTime m1 = ConvertHelper.toLocalDateTime(min, NopException::new);
        LocalDateTime m2 = ConvertHelper.toLocalDateTime(max, NopException::new);

        if (m1 != null) {
            int cmp1 = d1.compareTo(m1);
            if (excludeMin ? cmp1 <= 0 : cmp1 < 0) {
                return false;
            }
        }
        if (m2 != null) {
            int cmp1 = d1.compareTo(m2);
            if (excludeMax ? cmp1 >= 0 : cmp1 > 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean lengthBetween(Object value, Object min, Object max, boolean excludeMin, boolean excludeMax) {
        int d1 = ConvertHelper.toString(value, "").length();

        Integer m1 = ConvertHelper.toInt(min, NopException::new);
        Integer m2 = ConvertHelper.toInt(min, NopException::new);

        if (m1 != null) {
            int cmp1 = Integer.compare(d1, m1);
            if (excludeMin ? cmp1 <= 0 : cmp1 < 0) {
                return false;
            }
        }
        if (m2 != null) {
            int cmp1 = Integer.compare(d1, m2);
            if (excludeMax ? cmp1 >= 0 : cmp1 > 0) {
                return false;
            }
        }
        return true;
    }
}