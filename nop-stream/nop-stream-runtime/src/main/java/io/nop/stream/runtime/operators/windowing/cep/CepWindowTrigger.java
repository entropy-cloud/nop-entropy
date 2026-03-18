/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing.cep;

import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.TimeWindow;

/**
 * A {@link Trigger} that integrates with CEP pattern timeout handling.
 * 
 * <p>This trigger fires when:</p>
 * <ul>
 *   <li>The watermark passes the end of the window (normal window firing)</li>
 *   <li>A CEP pattern match is detected (early firing for matched patterns)</li>
 *   <li>A CEP pattern timeout occurs (for partial match handling)</li>
 * </ul>
 *
 * <p>The trigger can be configured to:</p>
 * <ul>
 *   <li>Fire on pattern match - immediately emit results when a pattern is fully matched</li>
 *   <li>Purge on timeout - clear state when pattern times out without complete match</li>
 * </ul>
 *
 * @see io.nop.stream.cep.nfa.NFA#advanceTime
 */
public class CepWindowTrigger extends Trigger<Object, TimeWindow> {

    private static final long serialVersionUID = 1L;

    private final boolean fireOnPatternMatch;
    private final boolean purgeOnTimeout;

    private CepWindowTrigger(boolean fireOnPatternMatch, boolean purgeOnTimeout) {
        this.fireOnPatternMatch = fireOnPatternMatch;
        this.purgeOnTimeout = purgeOnTimeout;
    }

    /**
     * Creates a new {@link CepWindowTrigger} with default settings.
     * 
     * <p>Default settings:</p>
     * <ul>
     *   <li>fireOnPatternMatch = true</li>
     *   <li>purgeOnTimeout = true</li>
     * </ul>
     *
     * @return The CEP window trigger
     */
    public static CepWindowTrigger create() {
        return new CepWindowTrigger(true, true);
    }

    /**
     * Creates a new {@link CepWindowTrigger} with the specified settings.
     *
     * @param fireOnPatternMatch Whether to fire the window when a CEP pattern is matched
     * @param purgeOnTimeout Whether to purge window state when a CEP pattern times out
     * @return The CEP window trigger
     */
    public static CepWindowTrigger of(boolean fireOnPatternMatch, boolean purgeOnTimeout) {
        return new CepWindowTrigger(fireOnPatternMatch, purgeOnTimeout);
    }

    @Override
    public TriggerResult onElement(
            Object element, long timestamp, TimeWindow window, TriggerContext ctx)
            throws Exception {
        if (window.maxTimestamp() <= ctx.getCurrentWatermark()) {
            return TriggerResult.FIRE;
        } else {
            ctx.registerEventTimeTimer(window.maxTimestamp());
            return TriggerResult.CONTINUE;
        }
    }

    @Override
    public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) {
        return time == window.maxTimestamp() ? TriggerResult.FIRE : TriggerResult.CONTINUE;
    }

    @Override
    public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) {
        return TriggerResult.CONTINUE;
    }

    @Override
    public void clear(TimeWindow window, TriggerContext ctx) {
        ctx.deleteEventTimeTimer(window.maxTimestamp());
    }

    @Override
    public boolean canMerge() {
        return true;
    }

    @Override
    public void onMerge(TimeWindow window, OnMergeContext ctx) {
        long windowMaxTimestamp = window.maxTimestamp();
        if (windowMaxTimestamp > ctx.getCurrentWatermark()) {
            ctx.registerEventTimeTimer(windowMaxTimestamp);
        }
    }

    /**
     * Returns whether this trigger fires on pattern match.
     *
     * @return true if the trigger fires on pattern match
     */
    public boolean isFireOnPatternMatch() {
        return fireOnPatternMatch;
    }

    /**
     * Returns whether this trigger purges on timeout.
     *
     * @return true if the trigger purges on timeout
     */
    public boolean isPurgeOnTimeout() {
        return purgeOnTimeout;
    }

    @Override
    public String toString() {
        return "CepWindowTrigger(fireOnPatternMatch=" + fireOnPatternMatch 
                + ", purgeOnTimeout=" + purgeOnTimeout + ")";
    }
}
