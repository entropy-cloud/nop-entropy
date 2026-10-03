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
 * WI17 test fixture: the "items" table bean for the join pipeline (id 1..2).
 */
public final class ItemsSourceFunction implements SourceFunction<Map<String, Object>> {

    private static final long serialVersionUID = 1L;

    private volatile boolean running = true;

    @Override
    public void run(SourceContext<Map<String, Object>> ctx) {
        ctx.collect(row(1, "x"));
        ctx.collect(row(2, "y"));
    }

    static Map<String, Object> row(int id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        return m;
    }

    @Override
    public void cancel() {
        running = false;
    }
}
