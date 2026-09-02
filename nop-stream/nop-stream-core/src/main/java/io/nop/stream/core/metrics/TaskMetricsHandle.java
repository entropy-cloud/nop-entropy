/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.metrics;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Serializable, re-targetable {@link StreamTaskMetrics} holder shared between
 * the {@code StreamTaskInvokable} and its wired outputs (item 16, P-REQ-1).
 *
 * <p>{@code StreamTaskInvokable} is serializable, and the outputs it wires in
 * its constructors capture this handle by reference. The micrometer-backed
 * implementation is NOT serializable, so the delegate is transient: after
 * deserialization the handle falls back to {@link StreamTaskMetrics#NOOP}
 * until the runtime injects the real implementation via
 * {@link #setDelegate(StreamTaskMetrics)}.
 */
public final class TaskMetricsHandle implements StreamTaskMetrics, Serializable {

    private static final long serialVersionUID = 1L;

    private transient volatile StreamTaskMetrics delegate = StreamTaskMetrics.NOOP;

    public void setDelegate(StreamTaskMetrics delegate) {
        this.delegate = delegate == null ? StreamTaskMetrics.NOOP : delegate;
    }

    public StreamTaskMetrics getDelegate() {
        return delegate;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.delegate = StreamTaskMetrics.NOOP;
    }

    @Override
    public void recordsIn(long n) {
        delegate.recordsIn(n);
    }

    @Override
    public void recordsOut(long n) {
        delegate.recordsOut(n);
    }

    @Override
    public void recordsConsumed(long n) {
        delegate.recordsConsumed(n);
    }

    @Override
    public void recordsEmitted(long n) {
        delegate.recordsEmitted(n);
    }

    @Override
    public void processingTime(long nanos) {
        delegate.processingTime(nanos);
    }
}
