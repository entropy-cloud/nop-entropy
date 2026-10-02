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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMessageSubscribeOptions {

    @Test
    public void testDefaults() {
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        assertNull(options.getSubscribeName());
        assertNull(options.getSubscriptionType());
        assertNull(options.getSeekMode());
        assertNull(options.getSeekToMessage());
        assertFalse(options.isTransactional());
        assertFalse(options.allowBatchConsume());
        assertEquals(0, options.getBatchReceiveCount());
        assertEquals(0, options.getBatchReceiveTimeout());
        assertEquals(0, options.getConcurrency());
        assertEquals(0, options.getTransactionTimeout());
        assertEquals(0, options.getSeekToTime());
    }

    @Test
    public void testBatchConsumeGateIsDrivenByCountOnly() {
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        // 仅配置超时不开批量
        options.setBatchReceiveTimeout(100);
        assertFalse(options.allowBatchConsume());

        // batchReceiveCount > 0 即开启批量
        options.setBatchReceiveCount(10);
        assertTrue(options.allowBatchConsume());
        assertEquals(100, options.getBatchReceiveTimeout());
        // 负数视为关闭
        options.setBatchReceiveCount(-1);
        assertFalse(options.allowBatchConsume());
    }

    @Test
    public void testSetterRoundTrip() {
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-1");
        options.setTransactional(true);
        options.setTransactionTimeout(5000L);
        options.setConcurrency(3);
        options.setSubscriptionType(SubscriptionType.Shared);
        options.setSeekMode(SeekMode.ALWAYS_SEEK_TO_END);
        options.setSeekToMessage("msg-1");
        options.setSeekToTime(123456789L);

        assertEquals("sub-1", options.getSubscribeName());
        assertTrue(options.isTransactional());
        assertEquals(5000L, options.getTransactionTimeout());
        assertEquals(3, options.getConcurrency());
        assertEquals(SubscriptionType.Shared, options.getSubscriptionType());
        assertEquals(SeekMode.ALWAYS_SEEK_TO_END, options.getSeekMode());
        assertEquals("msg-1", options.getSeekToMessage());
        assertEquals(123456789L, options.getSeekToTime());
    }
}
