/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.common.functions.source.SourceFunction;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** Bounded source emitting keyed records for the dim-lookup pipeline. */
public class DimKeySourceFunction implements SourceFunction<Map<String, Object>>, Serializable {

    private static final long serialVersionUID = 1L;

    private volatile boolean running = true;

    @Override
    public void run(SourceContext<Map<String, Object>> ctx) throws Exception {
        for (String key : new String[]{"k1", "k2", "k1"}) {
            if (!running) {
                break;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("dimKey", key);
            ctx.collect(m);
        }
    }

    @Override
    public void cancel() {
        running = false;
    }
}
