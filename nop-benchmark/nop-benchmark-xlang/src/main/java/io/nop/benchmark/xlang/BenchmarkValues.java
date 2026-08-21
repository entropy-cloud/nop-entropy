/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.xlang;

import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.xml.XNode;

import java.util.Objects;

/**
 * 基准载体的值相等判定（main 域自持，无 test-jar 消费；口径与对拍 harness 的
 * CompareValues 一致——XNode/SQL 无值 equals 实现，取序列化内容比较）。
 */
public final class BenchmarkValues {

    private BenchmarkValues() {
    }

    public static boolean valueEquals(Object a, Object b) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        if (a.getClass() != b.getClass())
            return false;
        if (a instanceof XNode)
            return ((XNode) a).xml().equals(((XNode) b).xml());
        if (a instanceof SQL)
            return a.toString().equals(b.toString());
        return Objects.equals(a, b);
    }
}
