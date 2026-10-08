/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing;

import java.io.Serializable;

/**
 * WI8 (plan 2332): window lifecycle event — the TDengine-style trigger/compute
 * decoupling surface. Emitted on a dedicated per-operator side-output tag (opt-in via
 * {@code WindowedStream.emitWindowEvents()}); the main output stream is untouched.
 *
 * <ul>
 *   <li>{@link Type#OPEN} — first record assigned to the window (registry insertion)</li>
 *   <li>{@link Type#CLOSE} — window lifetime ended (cleanup-time state release)</li>
 *   <li>{@link Type#IDLE} — upstream marked the watermark idle while this window is
 *       still active</li>
 *   <li>{@link Type#RESUME} — watermark resumed (ACTIVE status or first record after
 *       idle)</li>
 * </ul>
 *
 * <p>Note: trigger FIRING is deliberately NOT a lifecycle event — it is data-plane
 * output, observable on the main stream. GlobalWindows never reach cleanup time, so
 * they emit OPEN/IDLE but never CLOSE.
 */
public final class WindowLifecycleEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type { OPEN, CLOSE, IDLE, RESUME }

    private final Type type;
    private final Object key;
    private final long windowStart;
    private final long windowEnd;

    public WindowLifecycleEvent(Type type, Object key, long windowStart, long windowEnd) {
        this.type = type;
        this.key = key;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public Type getType() {
        return type;
    }

    public Object getKey() {
        return key;
    }

    public long getWindowStart() {
        return windowStart;
    }

    public long getWindowEnd() {
        return windowEnd;
    }

    @Override
    public String toString() {
        return "WindowLifecycleEvent{" + type + ", key=" + key
                + ", window=[" + windowStart + "," + windowEnd + ")}";
    }
}
