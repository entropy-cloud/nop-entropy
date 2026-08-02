/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

/**
 * Controls when the TTL access-timestamp of a keyed state entry is refreshed.
 *
 * <ul>
 *   <li>{@link #Disabled} — no TTL; the state never expires (default, backward compatible).</li>
 *   <li>{@link #OnCreateAndWrite} — the TTL window is refreshed only when the entry is
 *       created or written. Reads do not extend the lifetime.</li>
 *   <li>{@link #OnReadAndWrite} — the TTL window is refreshed on both reads and writes,
 *       so an entry that is being actively read stays alive.</li>
 * </ul>
 *
 * <p>This enum covers only processing-time TTL. Event-time TTL (watermark driven) is a
 * future enhancement and is explicitly out of scope for the first version (see plan
 * {@code 2026-08-02-0955-3-state-ttl.md} Non-Goals).
 */
public enum StateTtlUpdateType {
    Disabled,
    OnCreateAndWrite,
    OnReadAndWrite;

    public boolean isDisabled() {
        return this == Disabled;
    }

    public boolean refreshesOnRead() {
        return this == OnReadAndWrite;
    }
}
