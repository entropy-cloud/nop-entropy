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

/** E3 word-stream beans: mixed-length words [cat, long, dog, word]. */
public final class E3WordBeans {

    private E3WordBeans() {
    }

    public static class WordSource implements SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(SourceContext<Map<String, Object>> ctx) throws Exception {
            String[] words = {"cat", "long", "dog", "word"};
            long ts = 1000L;
            for (String w : words) {
                if (!running)
                    return;
                ctx.collectWithTimestamp(Map.of("word", w, "len", w.length()), ts);
                ts += 1000;
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    public static class ShortSink implements SinkFunction<Integer> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(Integer len) {
            E3Stores.SHORT.add(len);
        }
    }

    public static class LongSink implements SinkFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(String value) {
            E3Stores.LONG.add(value);
        }
    }
}
