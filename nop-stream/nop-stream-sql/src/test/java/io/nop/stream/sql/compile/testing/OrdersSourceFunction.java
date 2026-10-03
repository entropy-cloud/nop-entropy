/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile.testing;

import java.util.LinkedHashMap;
import java.util.Map;

import io.nop.stream.core.common.functions.source.SourceFunction;

/**
 * WI17 test fixture: the "orders" table bean (r2 B2 — FROM 表名即 bean 名). Fixed
 * records: two items across two 5s TUMBLE windows plus one null amount (null-skipping
 * aggregate semantics) and one filtered-out row.
 */
public final class OrdersSourceFunction implements SourceFunction<Map<String, Object>> {

    private static final long serialVersionUID = 1L;

    public static Map<String, Object> record(long ts, String item, Object amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ts", ts);
        m.put("item", item);
        m.put("amount", amount);
        return m;
    }

    public static java.util.List<Map<String, Object>> FIXED_DATA = java.util.Arrays.asList(
            record(1000L, "a", 1),
            record(2000L, "b", 2),
            record(3000L, "a", null),
            record(4000L, "a", 3),
            // second window
            record(11000L, "b", 4),
            // filtered out by amount > 0 in the filter query
            record(12000L, "a", -5),
            // trailing record: pushes the watermark past the second window's end so
            // [10s,15s) fires (its own window stays unfired — no EOS watermark needed)
            record(17000L, "c", 1));

    private volatile boolean running = true;

    @Override
    public void run(SourceContext<Map<String, Object>> ctx) {
        for (Map<String, Object> r : FIXED_DATA) {
            if (!running)
                break;
            ctx.collect(r);
        }
    }

    @Override
    public void cancel() {
        running = false;
    }
}
