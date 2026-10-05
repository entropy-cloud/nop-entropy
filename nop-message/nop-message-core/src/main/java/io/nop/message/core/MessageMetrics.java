/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * WI30 (MSG-F-23): send / consume / failure counters and send timer, registered via the
 * platform Micrometer registry. A {@code null} registry produces a no-op instance (zero
 * overhead when metrics are not enabled).
 */
public class MessageMetrics {
    private final Counter sent;
    private final Counter consumed;
    private final Counter failed;
    private final Timer sendTimer;

    private MessageMetrics(Counter sent, Counter consumed, Counter failed, Timer sendTimer) {
        this.sent = sent;
        this.consumed = consumed;
        this.failed = failed;
        this.sendTimer = sendTimer;
    }

    public static MessageMetrics create(MeterRegistry registry, String backend) {
        if (registry == null) {
            return noop();
        }
        Counter sent = Counter.builder("nop.message." + backend + ".sent")
                .description("Messages successfully submitted for send").register(registry);
        Counter consumed = Counter.builder("nop.message." + backend + ".consumed")
                .description("Messages dispatched to a consumer handler").register(registry);
        Counter failed = Counter.builder("nop.message." + backend + ".failed")
                .description("Message consume failures").register(registry);
        Timer sendTimer = Timer.builder("nop.message." + backend + ".send.duration")
                .description("Send invocation duration").register(registry);
        return new MessageMetrics(sent, consumed, failed, sendTimer);
    }

    public static MessageMetrics noop() {
        return new MessageMetrics(null, null, null, null);
    }

    public void incrementSent() {
        if (sent != null)
            sent.increment();
    }

    public void incrementConsumed() {
        if (consumed != null)
            consumed.increment();
    }

    public void incrementFailed() {
        if (failed != null)
            failed.increment();
    }

    public long sentCount() {
        return sent == null ? 0 : (long) sent.count();
    }

    public long consumedCount() {
        return consumed == null ? 0 : (long) consumed.count();
    }

    public long failedCount() {
        return failed == null ? 0 : (long) failed.count();
    }

    /** Records the send duration (nanoseconds). No-op when metrics are disabled. */
    public void recordSendDuration(long nanos) {
        if (sendTimer != null)
            sendTimer.record(nanos, TimeUnit.NANOSECONDS);
    }
}
