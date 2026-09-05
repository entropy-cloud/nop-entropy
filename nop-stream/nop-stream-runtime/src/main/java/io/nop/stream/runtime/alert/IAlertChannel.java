/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.alert;

/**
 * Item 16 (P-REQ-12): pluggable alert channel abstraction. Implementations
 * live inside the runtime (lightweight channels — logging / webhook) per the
 * Phase 1 channel-boundary decision; the abstraction stays stable so
 * email/IM channels can be added later WITHOUT touching the engine.
 *
 * <p>Implementations may throw from {@link #send}; {@link AlertService}
 * catches per-channel failures and logs them — a broken channel never breaks
 * the job control path.
 */
public interface IAlertChannel {

    /** Channel identity for logs and diagnostics. */
    String getName();

    /** Delivers the alert. May throw on delivery failure. */
    void send(AlertEvent event) throws Exception;
}
