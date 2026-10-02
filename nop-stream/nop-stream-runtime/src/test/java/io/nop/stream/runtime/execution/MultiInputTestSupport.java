/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WI7 shared helper: the element/barrier observation sequence recorded by the
 * {@code DataStream.transform}-injected observing operator.
 */
public final class MultiInputTestSupport {

    private MultiInputTestSupport() {
    }

    /**
     * Single interleaved sequence: elements as "e:&lt;value&gt;", barriers as
     * "b:&lt;id&gt;" — position AND temporal assertions read this one list.
     */
    public static final class Observation {
        public final List<String> sequence = new CopyOnWriteArrayList<>();
        public final List<Long> atMillis = new CopyOnWriteArrayList<>();

        public void addEvent(Object v) {
            sequence.add("e:" + v);
            atMillis.add(System.nanoTime() / 1_000_000);
        }

        public void addBarrier(long id) {
            sequence.add("b:" + id);
            atMillis.add(System.nanoTime() / 1_000_000);
        }

        /** observed wall-clock millis between two sequence entries, or -1 */
        public long elapsedMillis(String fromKey, String toKey) {
            int i = sequence.indexOf(fromKey);
            int j = sequence.indexOf(toKey);
            if (i < 0 || j < 0 || i >= atMillis.size() || j >= atMillis.size()) {
                return -1;
            }
            return atMillis.get(j) - atMillis.get(i);
        }

        /** count of barrier markers observed */
        public int barrierCount() {
            return (int) sequence.stream().filter(s -> s.startsWith("b:")).count();
        }
    }
}
