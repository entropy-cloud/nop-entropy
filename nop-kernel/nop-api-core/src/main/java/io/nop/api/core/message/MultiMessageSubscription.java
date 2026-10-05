/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

import java.util.List;

public class MultiMessageSubscription implements IMessageSubscription {
    private final List<IMessageSubscription> subscriptions;

    public MultiMessageSubscription(List<IMessageSubscription> subscriptions) {
        // WI29 (F-37): immutable snapshot — the fan-out callers used to pass a shared
        // mutable list that was populated AFTER this constructor returned.
        this.subscriptions = List.copyOf(subscriptions);
    }

    @Override
    public boolean isSuspended() {
        // WI29 (F-37): empty list returns false (the old allMatch returned true — a
        // vacuous-truth semantic trap that the WI2 test accidentally固化).
        boolean result = false;
        for (IMessageSubscription sub : subscriptions) {
            if (!sub.isSuspended())
                return false;
            result = true;
        }
        return result;
    }

    @Override
    public boolean isCancelled() {
        boolean result = false;
        for (IMessageSubscription sub : subscriptions) {
            if (!sub.isCancelled())
                return false;
            result = true;
        }
        return result;
    }

    @Override
    public void cancel() {
        RuntimeException first = null;
        for (IMessageSubscription subscription : subscriptions) {
            try {
                subscription.cancel();
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                } else {
                    first.addSuppressed(e);
                }
            }
        }
        if (first != null)
            throw first;
    }

    @Override
    public void suspend() {
        RuntimeException first = null;
        for (IMessageSubscription subscription : subscriptions) {
            try {
                subscription.suspend();
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                } else {
                    first.addSuppressed(e);
                }
            }
        }
        if (first != null)
            throw first;
    }

    @Override
    public void resume() {
        RuntimeException first = null;

        for (IMessageSubscription subscription : subscriptions) {
            try {
                subscription.resume();
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                } else {
                    first.addSuppressed(e);
                }
            }
        }
        if (first != null)
            throw first;
    }
}