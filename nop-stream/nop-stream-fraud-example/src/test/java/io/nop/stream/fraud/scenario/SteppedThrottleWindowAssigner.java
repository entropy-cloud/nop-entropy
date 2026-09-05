/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.util.Collection;

import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.windows.Window;

/**
 * Item 32 (OBS-1): pass-through per-record stepped throttle applied at the
 * WINDOW ASSIGNER — the consumer vertex's per-record {@code assignWindows} call
 * — driven by the same live-stepped level file as the BP-1 sink throttle (a
 * single long ms value ≥ 0 polled per record; 0 = release).
 *
 * <p><strong>Placement adjudication (live evidence runIds
 * {@code 1788468752196-1} / {@code 1788469038532-1}, 2026-09-04)</strong>: in the
 * S2 shape (source..watermarks | remote edge | keyBy..window..sink) neither
 * obvious throttle point saturates the cross-task channel — a throttled SINK is
 * absorbed by the window operator (backlog accumulates in window state; the
 * channel gauge stayed 0/max 6), and a map chained with the source merely
 * throttles the producer. The window assigner runs INSIDE the consumer vertex's
 * processElement path (its per-record input dispatch), so throttling it makes
 * the consumer's sustained throughput fall below the source rate and the
 * channel queue genuinely fills — the direct gauge evidence the
 * observation-surface drill exists to capture. Window/aggregate semantics are
 * unchanged (the wrapper delegates everything).
 */
public final class SteppedThrottleWindowAssigner<T, W extends Window> extends WindowAssigner<T, W> {

    private static final long serialVersionUID = 1L;

    private final WindowAssigner<T, W> delegate;
    private final String levelFilePath;

    public SteppedThrottleWindowAssigner(WindowAssigner<T, W> delegate, String levelFilePath) {
        this.delegate = delegate;
        this.levelFilePath = levelFilePath;
    }

    @Override
    public Collection<W> assignWindows(T element, long timestamp,
                                       WindowAssigner.WindowAssignerContext context) {
        long level = ThrottledScenarioSinks.currentThrottleLevel(levelFilePath);
        if (level > 0L) {
            try {
                Thread.sleep(level);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("SteppedThrottleWindowAssigner interrupted", e);
            }
        }
        return delegate.assignWindows(element, timestamp, context);
    }

    @Override
    public io.nop.stream.core.windowing.triggers.Trigger<T, W> getDefaultTrigger(
            io.nop.core.context.IServiceContext env) {
        return delegate.getDefaultTrigger(env);
    }

    @Override
    public boolean isEventTime() {
        return delegate.isEventTime();
    }
}
