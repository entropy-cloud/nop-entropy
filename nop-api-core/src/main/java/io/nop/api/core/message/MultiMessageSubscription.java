/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
        RuntimeException err = null;
        for (IMessageSubscription subscription : subscriptions) {
            try {
                subscription.cancel();
            } catch (RuntimeException e) {
                err = e;
            }
        }
        if (err != null)
            throw err;
    }

    @Override
    public void suspend() {
        RuntimeException err = null;
        for (IMessageSubscription subscription : subscriptions) {
            try {
                subscription.suspend();
            } catch (RuntimeException e) {
                err = e;
            }
        }
        if (err != null)
            throw err;
    }

    @Override
    public void resume() {
        RuntimeException err = null;

        for (IMessageSubscription subscription : subscriptions) {
            try {
                subscription.resume();
            } catch (RuntimeException e) {
                err = e;
            }
        }
        if (err != null)
            throw err;
    }
}