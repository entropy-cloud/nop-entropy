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
        this.subscriptions = subscriptions;
    }

    @Override
    public boolean isSuspended() {
        return subscriptions.stream().allMatch(IMessageSubscription::isSuspended);
    }

    @Override
    public boolean isCancelled() {
        return subscriptions.stream().allMatch(IMessageSubscription::isCancelled);
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