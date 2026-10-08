/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.triggers;

import io.nop.stream.core.windowing.windows.Window;

/**
 * WI37 (D11 release): trigger for the {@code emit="update"} tier — fires the window
 * on every element (Kafka Streams {@code EmitStrategy.ON_WINDOW_UPDATE} shape) and
 * once more at the window end so the final value is emitted even when the last
 * record arrives exactly at the boundary.
 *
 * <p>Must be combined with {@code AccumulationMode.ACCUMULATING}: FIRE does not
 * purge, so the window contents survive each emission and per-record emissions show
 * the running aggregate (D1 last-value-wins terminal-value semantics make the
 * repeated emissions safe downstream). The operator's cleanup timer clears the
 * state past the lateness bound; a cleanup-time {@code onEventTime} callback that
 * is not the window end returns CONTINUE (the cleanup path does not need a
 * redundant emission).
 *
 * <p>Follows the {@link EventTimeTrigger} registration pattern: the window-end
 * event-time timer is (re-)registered on every element, and is deleted in
 * {@link #clear}.
 */
public class EmitUpdateTrigger<T, W extends Window> extends Trigger<T, W> {
    private static final long serialVersionUID = 1L;

    @Override
    public TriggerResult onElement(T element, long timestamp, W window, TriggerContext ctx)
            throws Exception {
        if (window.maxTimestamp() <= ctx.getCurrentWatermark()) {
            // watermark already past the window end: fire immediately (registration
            // would never be reached by the timer path)
            return TriggerResult.FIRE;
        }
        ctx.registerEventTimeTimer(window.maxTimestamp());
        return TriggerResult.FIRE;
    }

    @Override
    public TriggerResult onEventTime(long time, W window, TriggerContext ctx) {
        return time == window.maxTimestamp() ? TriggerResult.FIRE
                : TriggerResult.CONTINUE;
    }

    @Override
    public TriggerResult onProcessingTime(long time, W window, TriggerContext ctx) {
        return TriggerResult.CONTINUE;
    }

    @Override
    public void clear(W window, TriggerContext ctx) {
        ctx.deleteEventTimeTimer(window.maxTimestamp());
    }

    @Override
    public boolean canMerge() {
        return true;
    }

    @Override
    public void onMerge(W window, OnMergeContext ctx) {
        long windowMaxTimestamp = window.maxTimestamp();
        if (windowMaxTimestamp > ctx.getCurrentWatermark()) {
            ctx.registerEventTimeTimer(windowMaxTimestamp);
        }
    }

    @Override
    public String toString() {
        return "EmitUpdateTrigger()";
    }
}
