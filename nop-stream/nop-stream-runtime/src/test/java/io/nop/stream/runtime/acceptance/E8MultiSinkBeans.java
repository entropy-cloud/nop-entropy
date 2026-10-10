/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.acceptance;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** E8 SeaTunnel-degradation beans: one mixed source, four branch sinks. */
public final class E8MultiSinkBeans {

    private E8MultiSinkBeans() {
    }

    public static class MixedSource implements SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(SourceContext<Map<String, Object>> ctx) throws Exception {
            String[] kinds = {"A", "B", "C", "D"};
            for (int i = 0; i < kinds.length; i++) {
                if (!running)
                    return;
                ctx.collectWithTimestamp(Map.of("kind", kinds[i], "seq", i + 1), 1000L + i);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /**
     * Records the incoming value into the per-kind store (the branch filter already
     * narrowed to one kind — the sink journals by the record's own kind field so the
     * four sinks can share this class).
     */
    public static class SinkByKind implements SinkFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(Map<String, Object> value) {
            BY_KIND.computeIfAbsent((String) value.get("kind"),
                    k -> java.util.Collections.synchronizedList(new ArrayList<>())).add(value);
        }
    }

    public static final Map<String, List<Map<String, Object>>> BY_KIND =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** accessor */
    public static Map<String, List<Map<String, Object>>> byKind() {
        return BY_KIND;
    }
}
