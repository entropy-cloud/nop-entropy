/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WI27 (D4): the neutral mode field is additive and null-defaulted (D8).
 */
class TestSubscriptionModeOptions {

    @Test
    void subscriptionModeDefaultsToNullAndRoundTrips() {
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        assertNull(options.getSubscriptionMode());
        options.setSubscriptionMode(SubscriptionMode.LOAD_BALANCED);
        assertEquals(SubscriptionMode.LOAD_BALANCED, options.getSubscriptionMode());
        // legacy field untouched
        assertNull(options.getSubscriptionType());
    }
}
