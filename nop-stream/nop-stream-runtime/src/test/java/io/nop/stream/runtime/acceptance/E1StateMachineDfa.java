/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.acceptance;

import io.nop.stream.core.common.functions.KeyedProcessFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.util.Collector;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WI21 E1: the StateMachineExample degradation beans. The DFA is a two-state
 * per-entity machine (IDLE --start--> RUNNING --error--> ALERT --reset--> IDLE);
 * the alert transition is ALSO emitted to the alert journal. Rate limiting is
 * degraded to in-bean self-limiting per the WI0 adjudication (the source emits with
 * inter-record pacing, bounded by the fixed event set).
 */
public final class E1StateMachineDfa {

    private E1StateMachineDfa() {
    }

    /** Emits the transition-driving event sequence for two entities. */
    public static class EventSource implements SourceFunction<Map<String, String>> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(SourceContext<Map<String, String>> ctx) throws Exception {
            List<Map<String, String>> events = new ArrayList<>();
            events.add(ev("e1", "start"));
            events.add(ev("e2", "start"));
            events.add(ev("e2", "error"));
            events.add(ev("e1", "error"));
            events.add(ev("e1", "reset"));
            for (Map<String, String> e : events) {
                if (!running)
                    return;
                // in-bean self-limiting (WI0 degradation): the fixed event set bounds
                // emission. 时间确定化（plan 2510）：原 pacing sleep 不承载被测语义
                // （无任何断言依赖发射间隔），串行 local runner 下删除真实时间依赖。
                ctx.collect(e);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }

        private static Map<String, String> ev(String entity, String event) {
            return Map.of("entity", entity, "event", event);
        }
    }

    /** The per-key DFA: IDLE --start--> RUNNING --error--> ALERT --reset--> IDLE. */
    public static class DfaFunction
            extends KeyedProcessFunction<String, Map<String, String>, Map<String, String>> {
        private static final long serialVersionUID = 1L;

        // per-entity state (embedded local runner: per-key journal map; production
        // keyed state is the WI14 surface)
        private final Map<String, String> state = new HashMap<>();

        @Override
        public void processElement(Map<String, String> value,
                                   io.nop.stream.core.common.functions.ProcessFunction<Map<String, String>, Map<String, String>>.Context ctx,
                                   Collector<Map<String, String>> out) throws Exception {
            String entity = value.get("entity");
            String event = value.get("event");
            String cur = state.getOrDefault(entity, "IDLE");
            String next = transition(cur, event);
            E1Stores.JOURNAL.add(entity + ":" + cur + "--" + event + "-->" + next);
            if (!next.equals(cur)) {
                state.put(entity, next);
                out.collect(Map.of("entity", entity, "state", next));
            }
        }

        static String transition(String cur, String event) {
            switch (cur) {
                case "IDLE":
                    return "start".equals(event) ? "RUNNING" : cur;
                case "RUNNING":
                    return "error".equals(event) ? "ALERT" : cur;
                case "ALERT":
                    return "reset".equals(event) ? "IDLE" : cur;
                default:
                    return cur;
            }
        }
    }

    public static class MainSink implements SinkFunction<Map<String, String>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(Map<String, String> value) {
            E1Stores.MAIN.add(value);
        }
    }

    public static class AlertSink implements SinkFunction<Map<String, String>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(Map<String, String> value) {
            E1Stores.ALERT.add(value);
        }
    }

    /** journal helper type marker (unused at runtime). */
    interface Marker extends Serializable {
    }
}
