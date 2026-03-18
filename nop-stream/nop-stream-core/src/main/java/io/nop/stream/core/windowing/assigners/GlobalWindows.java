/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.assigners;

import io.nop.core.context.IServiceContext;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.GlobalWindow;

import java.util.Collection;
import java.util.Collections;

public class GlobalWindows extends WindowAssigner<Object, GlobalWindow> {
    private static final long serialVersionUID = 1L;

    private static final GlobalWindows INSTANCE = new GlobalWindows();

    private GlobalWindows() {
    }

    public static GlobalWindows create() {
        return INSTANCE;
    }

    @Override
    public Collection<GlobalWindow> assignWindows(
            Object element, long timestamp, WindowAssignerContext context) {
        return Collections.singletonList(GlobalWindow.get());
    }

    @Override
    public Trigger<Object, GlobalWindow> getDefaultTrigger(IServiceContext env) {
        return new NeverTrigger();
    }

    @Override
    public boolean isEventTime() {
        return false;
    }

    @Override
    public String toString() {
        return "GlobalWindows()";
    }

    private static class NeverTrigger extends Trigger<Object, GlobalWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public TriggerResult onElement(
                Object element,
                long timestamp,
                GlobalWindow window,
                TriggerContext ctx) {
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onProcessingTime(
                long time,
                GlobalWindow window,
                TriggerContext ctx) {
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onEventTime(
                long time,
                GlobalWindow window,
                TriggerContext ctx) {
            return TriggerResult.CONTINUE;
        }

        @Override
        public void clear(GlobalWindow window, TriggerContext ctx) {
        }

        @Override
        public boolean canMerge() {
            return false;
        }

        @Override
        public void onMerge(GlobalWindow window, OnMergeContext ctx) {
        }

        @Override
        public String toString() {
            return "NeverTrigger()";
        }
    }
}
