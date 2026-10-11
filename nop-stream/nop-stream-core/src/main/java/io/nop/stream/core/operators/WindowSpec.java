/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.io.Serializable;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.WindowLifecycleEvent;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.Window;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * WI8 (plan 2420): immutable value object carrying every window-configuration
 * axis of a window operator build. The former 17-method
 * {@code IWindowOperatorFactory} surface grew one overload generation per new
 * parameter (elementType → lateDataOutputTag → maxDelay/emitWindowEvents/
 * windowEventsOutputTag → accumulationMode), each default shim silently
 * dropping the new argument. All twelve axes travel here instead; the
 * operation function and (aggregate only) the accumulator type stay as the
 * factory method's sole companion parameters, so a new configuration axis can
 * never fork another interface method.
 *
 * <p>Defaults mirror the {@code WindowedStreamImpl} fluent fields:
 * {@code allowedLateness = 0}, {@code maxDelay = 0}, {@code emitWindowEvents
 * = false}, every object field null. Assigner, keySelector and keyClass are
 * required — the builder fails fast on a missing required value.
 */
public final class WindowSpec<IN, K, W extends Window> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final WindowAssigner<? super IN, W> windowAssigner;
    private final Trigger<? super IN, ? super W> trigger;
    private final Evictor<? super IN, W> evictor;
    private final long allowedLateness;
    private final KeySelector<IN, K> keySelector;
    private final Class<K> keyClass;
    private final Class<IN> elementType;
    private final OutputTag<IN> lateDataOutputTag;
    private final long maxDelay;
    private final boolean emitWindowEvents;
    private final OutputTag<WindowLifecycleEvent> windowEventsOutputTag;
    private final AccumulationMode accumulationMode;

    private WindowSpec(Builder<IN, K, W> b) {
        this.windowAssigner = b.windowAssigner;
        this.trigger = b.trigger;
        this.evictor = b.evictor;
        this.allowedLateness = b.allowedLateness;
        this.keySelector = b.keySelector;
        this.keyClass = b.keyClass;
        this.elementType = b.elementType;
        this.lateDataOutputTag = b.lateDataOutputTag;
        this.maxDelay = b.maxDelay;
        this.emitWindowEvents = b.emitWindowEvents;
        this.windowEventsOutputTag = b.windowEventsOutputTag;
        this.accumulationMode = b.accumulationMode;
    }

    public static <IN, K, W extends Window> Builder<IN, K, W> builder(
            WindowAssigner<? super IN, W> windowAssigner,
            KeySelector<IN, K> keySelector,
            Class<K> keyClass) {
        return new Builder<>(windowAssigner, keySelector, keyClass);
    }

    /**
     * Copy builder: seeds a new builder with every field of {@code from}, for
     * callers that need a single-field variation (test counter-example stubs,
     * per-window overrides). The source spec is never mutated.
     */
    public static <IN, K, W extends Window> Builder<IN, K, W> builder(WindowSpec<IN, K, W> from) {
        return new Builder<>(from.windowAssigner, from.keySelector, from.keyClass)
                .trigger(from.trigger)
                .evictor(from.evictor)
                .allowedLateness(from.allowedLateness)
                .elementType(from.elementType)
                .lateDataOutputTag(from.lateDataOutputTag)
                .maxDelay(from.maxDelay)
                .emitWindowEvents(from.emitWindowEvents)
                .windowEventsOutputTag(from.windowEventsOutputTag)
                .accumulationMode(from.accumulationMode);
    }

    public WindowAssigner<? super IN, W> getWindowAssigner() {
        return windowAssigner;
    }

    public Trigger<? super IN, ? super W> getTrigger() {
        return trigger;
    }

    public Evictor<? super IN, W> getEvictor() {
        return evictor;
    }

    public long getAllowedLateness() {
        return allowedLateness;
    }

    public KeySelector<IN, K> getKeySelector() {
        return keySelector;
    }

    public Class<K> getKeyClass() {
        return keyClass;
    }

    public Class<IN> getElementType() {
        return elementType;
    }

    public OutputTag<IN> getLateDataOutputTag() {
        return lateDataOutputTag;
    }

    public long getMaxDelay() {
        return maxDelay;
    }

    public boolean isEmitWindowEvents() {
        return emitWindowEvents;
    }

    public OutputTag<WindowLifecycleEvent> getWindowEventsOutputTag() {
        return windowEventsOutputTag;
    }

    public AccumulationMode getAccumulationMode() {
        return accumulationMode;
    }

    @Override
    public String toString() {
        return "WindowSpec{" +
                "windowAssigner=" + windowAssigner +
                ", trigger=" + trigger +
                ", evictor=" + evictor +
                ", allowedLateness=" + allowedLateness +
                ", keyClass=" + keyClass +
                ", elementType=" + elementType +
                ", lateDataOutputTag=" + lateDataOutputTag +
                ", maxDelay=" + maxDelay +
                ", emitWindowEvents=" + emitWindowEvents +
                ", windowEventsOutputTag=" + windowEventsOutputTag +
                ", accumulationMode=" + accumulationMode +
                '}';
    }

    public static final class Builder<IN, K, W extends Window> {

        private final WindowAssigner<? super IN, W> windowAssigner;
        private final KeySelector<IN, K> keySelector;
        private final Class<K> keyClass;
        private Trigger<? super IN, ? super W> trigger;
        private Evictor<? super IN, W> evictor;
        private long allowedLateness;
        private Class<IN> elementType;
        private OutputTag<IN> lateDataOutputTag;
        private long maxDelay;
        private boolean emitWindowEvents;
        private OutputTag<WindowLifecycleEvent> windowEventsOutputTag;
        private AccumulationMode accumulationMode;

        private Builder(WindowAssigner<? super IN, W> windowAssigner,
                        KeySelector<IN, K> keySelector,
                        Class<K> keyClass) {
            if (windowAssigner == null) {
                throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "windowAssigner");
            }
            if (keySelector == null) {
                throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "keySelector");
            }
            if (keyClass == null) {
                throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "keyClass");
            }
            this.windowAssigner = windowAssigner;
            this.keySelector = keySelector;
            this.keyClass = keyClass;
        }

        public Builder<IN, K, W> trigger(Trigger<? super IN, ? super W> trigger) {
            this.trigger = trigger;
            return this;
        }

        public Builder<IN, K, W> evictor(Evictor<? super IN, W> evictor) {
            this.evictor = evictor;
            return this;
        }

        public Builder<IN, K, W> allowedLateness(long allowedLateness) {
            this.allowedLateness = allowedLateness;
            return this;
        }

        public Builder<IN, K, W> elementType(Class<IN> elementType) {
            this.elementType = elementType;
            return this;
        }

        public Builder<IN, K, W> lateDataOutputTag(OutputTag<IN> lateDataOutputTag) {
            this.lateDataOutputTag = lateDataOutputTag;
            return this;
        }

        public Builder<IN, K, W> maxDelay(long maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder<IN, K, W> emitWindowEvents(boolean emitWindowEvents) {
            this.emitWindowEvents = emitWindowEvents;
            return this;
        }

        public Builder<IN, K, W> windowEventsOutputTag(OutputTag<WindowLifecycleEvent> windowEventsOutputTag) {
            this.windowEventsOutputTag = windowEventsOutputTag;
            return this;
        }

        public Builder<IN, K, W> accumulationMode(AccumulationMode accumulationMode) {
            this.accumulationMode = accumulationMode;
            return this;
        }

        public WindowSpec<IN, K, W> build() {
            return new WindowSpec<>(this);
        }
    }
}
