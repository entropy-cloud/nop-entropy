/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators.windowing;

import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.Trigger.TriggerContext;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.TimeWindow;

/**
 * WI8 (plan 2332): wraps a delegate trigger and counts the processing-time callbacks —
 * the proof that the maxDelay fallback never feeds the user trigger. The delegate's
 * methods declare no checked exceptions beyond onElement, so the forwarding overrides
 * here drop the throws clause accordingly.
 */
class CountingProcessingTimeTrigger extends Trigger<Object, TimeWindow> {
    private final Trigger<Object, TimeWindow> delegate;
    int onProcessingTimeCalls;

    CountingProcessingTimeTrigger(Trigger<Object, TimeWindow> delegate) {
        this.delegate = delegate;
    }

    @Override
    public TriggerResult onElement(Object element, long timestamp, TimeWindow window,
                                   TriggerContext ctx) throws Exception {
        return delegate.onElement(element, timestamp, window, ctx);
    }

    @Override
    public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) {
        onProcessingTimeCalls++;
        return delegate.onProcessingTime(time, window, ctx);
    }

    @Override
    public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) {
        return delegate.onEventTime(time, window, ctx);
    }

    @Override
    public void clear(TimeWindow window, TriggerContext ctx) {
        delegate.clear(window, ctx);
    }
}
