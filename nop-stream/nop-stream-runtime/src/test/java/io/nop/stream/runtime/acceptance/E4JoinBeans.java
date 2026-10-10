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

/** E4 window-join beans: two keyed event-time streams. */
public final class E4JoinBeans {

    private E4JoinBeans() {
    }

    public static class LeftSource implements SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(SourceContext<Map<String, Object>> ctx) throws Exception {
            long ts = 1000L;
            for (String k : new String[]{"a", "b"}) {
                if (!running)
                    return;
                ctx.collectWithTimestamp(Map.of("k", k, "side", "L"), ts);
                ts += 1000;
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    public static class RightSource implements SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(SourceContext<Map<String, Object>> ctx) throws Exception {
            long ts = 1500L;
            for (String k : new String[]{"a"}) {
                if (!running)
                    return;
                ctx.collectWithTimestamp(Map.of("k", k, "side", "R"), ts);
                ts += 1000;
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    public static class JoinSink implements SinkFunction<Object> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(Object value) {
            E4Stores.PAIRS.add(value);
        }
    }
}
