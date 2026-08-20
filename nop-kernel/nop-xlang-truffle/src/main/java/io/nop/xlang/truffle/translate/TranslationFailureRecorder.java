package io.nop.xlang.truffle.translate;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/**
 * 翻译失败事件的默认内存记录器（plan I8 Phase 1 §6：默认实现 = 内存记录 + 可查询；
 * 无外部消费者时不静默——本记录器随语言实例恒开）。
 *
 * <p>有界环形保留（最新 {@link #MAX_RECORDED_EVENTS} 条）+ 累计计数（溢出不丢计数只丢明细）。
 */
public final class TranslationFailureRecorder {

    public static final int MAX_RECORDED_EVENTS = 256;

    private final ArrayDeque<TranslationFailureEvent> events = new ArrayDeque<>();

    private long totalReported;

    public void record(TranslationFailureEvent event) {
        Objects.requireNonNull(event, "event");
        synchronized (this) {
            if (events.size() == MAX_RECORDED_EVENTS)
                events.pollFirst();
            events.addLast(event);
            totalReported++;
        }
    }

    public synchronized List<TranslationFailureEvent> getEvents() {
        return List.copyOf(events);
    }

    public synchronized boolean hasEvents() {
        return !events.isEmpty();
    }

    public synchronized long getTotalReported() {
        return totalReported;
    }

    public synchronized void clear() {
        events.clear();
        totalReported = 0;
    }
}
