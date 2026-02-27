/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.subscription;

import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;

public class TestSubscriptionInfo extends BaseTestCase {

    @Test
    public void testMatchesTopic_exactMatch() {
        SubscriptionInfo info = new SubscriptionInfo();
        info.setTopic("graphql-subscription/onUserChanged/123");

        assertTrue(info.matchesTopic("graphql-subscription/onUserChanged/123"));
        assertFalse(info.matchesTopic("graphql-subscription/onUserChanged/456"));
        assertFalse(info.matchesTopic("graphql-subscription/onOrderChanged/123"));
    }

    @Test
    public void testMatchesTopic_wildcard() {
        SubscriptionInfo info = new SubscriptionInfo();
        info.setTopic("graphql-subscription/onUserChanged/*");

        assertTrue(info.matchesTopic("graphql-subscription/onUserChanged/123"));
        assertTrue(info.matchesTopic("graphql-subscription/onUserChanged/456"));
        assertTrue(info.matchesTopic("graphql-subscription/onUserChanged/abc"));
        assertFalse(info.matchesTopic("graphql-subscription/onOrderChanged/123"));
        assertFalse(info.matchesTopic("graphql-subscription/onUserChanged"));
        assertFalse(info.matchesTopic("other-prefix/onUserChanged/123"));
    }

    @Test
    public void testMatchesTopic_nullCases() {
        SubscriptionInfo info = new SubscriptionInfo();
        info.setTopic("graphql-subscription/onUserChanged/123");

        assertFalse(info.matchesTopic(null));
        
        info.setTopic(null);
        assertFalse(info.matchesTopic("graphql-subscription/onUserChanged/123"));
        assertFalse(info.matchesTopic(null));
    }

    @Test
    public void testMatchesTopic_emptyTopic() {
        SubscriptionInfo info = new SubscriptionInfo();
        info.setTopic("");

        assertFalse(info.matchesTopic("graphql-subscription/onUserChanged/123"));
        assertTrue(info.matchesTopic(""));
    }

    @Test
    public void testCancel() {
        SubscriptionInfo info = new SubscriptionInfo();
        
        // Test cancel with no subscription
        info.cancel();
        assertNull(info.getFlowSubscription());

        // Test cancel with subscription
        Flow.Subscription mockSubscription = new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        };
        info.setFlowSubscription(mockSubscription);
        assertEquals(mockSubscription, info.getFlowSubscription());

        info.cancel();
        assertNull(info.getFlowSubscription());
        
        // Cancel again should not throw
        info.cancel();
    }

    @Test
    public void testCreateTime() {
        long before = System.currentTimeMillis();
        SubscriptionInfo info = new SubscriptionInfo();
        long after = System.currentTimeMillis();

        assertTrue(info.getCreateTime() >= before);
        assertTrue(info.getCreateTime() <= after);

        info.setCreateTime(12345L);
        assertEquals(12345L, info.getCreateTime());
    }

    @Test
    public void testAllProperties() {
        SubscriptionInfo info = new SubscriptionInfo();
        
        info.setOperationId("op-123");
        assertEquals("op-123", info.getOperationId());
        
        info.setOperationName("onUserChanged");
        assertEquals("onUserChanged", info.getOperationName());
        
        info.setTopic("graphql-subscription/onUserChanged/123");
        assertEquals("graphql-subscription/onUserChanged/123", info.getTopic());
        
        info.setVariables(java.util.Map.of("userId", "123"));
        assertEquals("123", info.getVariables().get("userId"));
    }
}
