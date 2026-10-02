/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.common.functions.SinkFunction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Collecting sink exposing the enriched records for assertions. */
public class DimCollectingSink implements SinkFunction<Map<String, Object>>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final List<Map<String, Object>> COLLECTED = new ArrayList<>();

    @Override
    public void consume(Map<String, Object> value) {
        COLLECTED.add(value);
    }

    public static List<Map<String, Object>> getCollected() {
        synchronized (COLLECTED) {
            return new ArrayList<>(COLLECTED);
        }
    }

    public static void clear() {
        synchronized (COLLECTED) {
            COLLECTED.clear();
        }
    }
}
