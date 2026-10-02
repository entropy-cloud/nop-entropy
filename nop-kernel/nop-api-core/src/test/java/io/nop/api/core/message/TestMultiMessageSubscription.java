/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMultiMessageSubscription {

    static class FakeSubscription implements IMessageSubscription {
        boolean suspended;
        boolean cancelled;
        final AtomicInteger suspendCalls = new AtomicInteger();
        final AtomicInteger resumeCalls = new AtomicInteger();
        final AtomicInteger cancelCalls = new AtomicInteger();
        RuntimeException failOn;

        RuntimeException onCall(AtomicInteger counter) {
            counter.incrementAndGet();
            return failOn;
        }

        @Override
        public void cancel() {
            RuntimeException e = onCall(cancelCalls);
            if (e != null)
                throw e;
            cancelled = true;
        }

        @Override
        public boolean isSuspended() {
            return suspended;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void suspend() {
            RuntimeException e = onCall(suspendCalls);
            if (e != null)
                throw e;
            suspended = true;
        }

        @Override
        public void resume() {
            RuntimeException e = onCall(resumeCalls);
            if (e != null)
                throw e;
            suspended = false;
        }
    }

    @Test
    public void testStateIsAllMatchSemantics() {
        FakeSubscription a = new FakeSubscription();
        FakeSubscription b = new FakeSubscription();
        MultiMessageSubscription multi = new MultiMessageSubscription(Arrays.asList(a, b));

        // 全部未挂起 → false
        assertFalse(multi.isSuspended());
        // 只要有一个挂起 → 仍不算全部挂起
        a.suspended = true;
        assertFalse(multi.isSuspended());
        a.suspend();
        b.suspend();
        assertTrue(multi.isSuspended());

        // cancelled 同为 all 语义
        assertFalse(multi.isCancelled());
        a.cancelled = true;
        assertFalse(multi.isCancelled());
        b.cancelled = true;
        assertTrue(multi.isCancelled());
    }

    @Test
    public void testEmptySubscriptionIsVacuouslyTrue() {
        MultiMessageSubscription multi = new MultiMessageSubscription(Collections.emptyList());
        assertTrue(multi.isSuspended());
        assertTrue(multi.isCancelled());
    }

    @Test
    public void testCancelPropagatesToAll() {
        FakeSubscription a = new FakeSubscription();
        FakeSubscription b = new FakeSubscription();
        MultiMessageSubscription multi = new MultiMessageSubscription(Arrays.asList(a, b));

        multi.cancel();
        assertEquals(1, a.cancelCalls.get());
        assertEquals(1, b.cancelCalls.get());
        assertTrue(a.cancelled);
        assertTrue(b.cancelled);
    }

    @Test
    public void testFailureStillCancelsRestAndThrowsFirstWithSuppressed() {
        FakeSubscription a = new FakeSubscription();
        FakeSubscription b = new FakeSubscription();
        FakeSubscription c = new FakeSubscription();
        a.failOn = new IllegalStateException("a-failed");
        c.failOn = new IllegalArgumentException("c-failed");
        MultiMessageSubscription multi = new MultiMessageSubscription(Arrays.asList(a, b, c));

        IllegalStateException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                multi::cancel);
        assertEquals("a-failed", e.getMessage());
        // 失败不阻断后续订阅的取消
        assertEquals(1, b.cancelCalls.get());
        assertTrue(b.cancelled);
        // 第一个异常被抛出，后续异常挂到 suppressed
        assertEquals(1, e.getSuppressed().length);
        assertEquals("c-failed", e.getSuppressed()[0].getMessage());
    }

    @Test
    public void testSuspendResumePropagateAndFailFastSemantics() {
        FakeSubscription a = new FakeSubscription();
        FakeSubscription b = new FakeSubscription();
        MultiMessageSubscription multi = new MultiMessageSubscription(Arrays.asList(a, b));

        multi.suspend();
        assertTrue(a.suspended);
        assertTrue(b.suspended);
        multi.resume();
        assertFalse(a.suspended);
        assertFalse(b.suspended);
        assertEquals(1, a.resumeCalls.get());
        assertEquals(1, b.resumeCalls.get());

        // suspend 抛错时同样继续处理其余订阅并抛出第一个异常
        a.failOn = new IllegalStateException("suspend-failed");
        RuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, multi::suspend);
        assertEquals("suspend-failed", e.getMessage());
        // b 第一次 suspend 在前半段已调用，本轮失败重试后累计为 2
        assertEquals(2, b.suspendCalls.get());
    }

    @Test
    public void testAcceptsListInterface() {
        List<IMessageSubscription> subs = List.of(new FakeSubscription());
        MultiMessageSubscription multi = new MultiMessageSubscription(subs);
        assertFalse(multi.isSuspended());
    }
}
