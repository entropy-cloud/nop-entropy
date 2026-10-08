/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile.testing;

import io.nop.stream.core.common.functions.source.SourceFunction;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WI25 E2E fixture: a source whose tail contains a record that is LATE relative to
 * the per-event watermark (event ts 3000 arrives after the watermark already passed
 * 6000). Used by the lateness E2E pair — with a declared lateness the late record
 * updates the already-fired window (D9 fire-and-update); without one it is dropped.
 * {@code OrdersSourceFunction.FIXED_DATA} is left untouched (existing assertions).
 */
public class LateOrdersSourceFunction implements SourceFunction<Map<String, Object>> {

    private static final long serialVersionUID = 1L;

    private volatile boolean running = true;

    public static Map<String, Object> record(long ts, String item, Object amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ts", ts);
        m.put("item", item);
        m.put("amount", amount);
        return m;
    }

    public static List<Map<String, Object>> LATE_DATA = Arrays.asList(
            record(1000L, "a", 1),
            // pushes the per-event watermark past 5000 — window [0,5s) fires with a=1
            record(6000L, "b", 8),
            // LATE: watermark is 6000, event ts 3000 belongs to the fired window
            record(3000L, "a", 5),
            // pushes the watermark past every window end so all remaining windows fire
            record(17000L, "c", 1));

    @Override
    public void run(SourceContext<Map<String, Object>> ctx) {
        for (Map<String, Object> r : LATE_DATA) {
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
