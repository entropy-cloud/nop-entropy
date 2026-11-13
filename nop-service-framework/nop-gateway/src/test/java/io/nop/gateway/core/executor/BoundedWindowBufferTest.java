package io.nop.gateway.core.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 共享有界窗口缓冲状态核单元测试（plan 2509 R2-06 裁定①六合一契约 + 裁定⑤(a) fail-loud）。
 * 组件自身不加锁（由宿主 monitor 保护）——本测试单线程验证状态语义。
 */
class BoundedWindowBufferTest {

    @Test
    void capacityBoundsEnforcedBothSidesOfWindow() {
        BoundedWindowBuffer<String> buffer = new BoundedWindowBuffer<>(3, false, 1000L);
        buffer.beginWindow();
        assertTrue(buffer.shouldBufferInWindow());
        assertTrue(buffer.add("a1"));
        // 窗口期只缓冲 1 个（本测试聚焦容量界，不触发 N 满提前越窗）
        buffer.markPassed();
        // 越窗后同一容量上界：可继续入队至 capacity，满 = fail-loud 拒绝（非静默丢弃）
        assertTrue(buffer.add("a2"));
        assertTrue(buffer.add("a3"));
        assertFalse(buffer.add("a4"), "越窗后缓冲满必须拒绝（fail-loud 溢出）");
        assertEquals(3, buffer.size());
    }

    @Test
    void overflowIsTypedFailLoudException() {
        // 类型化溢出错误可构造且语义独立（宿主据此跳过重试决策直接断流）
        StreamingBufferOverflowException ex = new StreamingBufferOverflowException(
                "streaming buffer overflow: backlog 3 items exceeds bufferSize 2");
        assertEquals("streaming buffer overflow: backlog 3 items exceeds bufferSize 2", ex.getMessage());
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    void demandLedgerSaturatesAtLongMax() {
        BoundedWindowBuffer<String> buffer = new BoundedWindowBuffer<>(4, false, 1000L);
        assertFalse(buffer.hasDemand());
        buffer.addDemand(Long.MAX_VALUE);
        buffer.addDemand(1); // 溢出饱和
        assertTrue(buffer.hasDemand());
        buffer.decDemand();
        assertTrue(buffer.hasDemand(), "饱和后不越零");
    }

    @Test
    void pendingCompleteSuspensionLifecycle() {
        BoundedWindowBuffer<String> buffer = new BoundedWindowBuffer<>(4, false, 1000L);
        buffer.beginWindow();
        buffer.add("tail");
        buffer.setPendingComplete(true);
        assertTrue(buffer.isPendingComplete());
        assertEquals("tail", buffer.poll());
        assertTrue(buffer.isEmpty());
        buffer.setPendingComplete(false);
        assertFalse(buffer.isPendingComplete());
    }

    @Test
    void cancelCleanupClearsEverything() {
        BoundedWindowBuffer<String> buffer = new BoundedWindowBuffer<>(4, false, 1000L);
        buffer.beginWindow();
        buffer.add("a1");
        buffer.addDemand(3);
        buffer.setPendingComplete(true);
        buffer.clearForCancel();
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isPendingComplete(), "取消后挂起终态不再补发");
        assertFalse(buffer.hasDemand());
        assertTrue(buffer.isCancelled());
    }

    @Test
    void passthroughTierNeverWindowBuffers() {
        BoundedWindowBuffer<String> buffer = new BoundedWindowBuffer<>(4, true, 1000L);
        buffer.beginWindow();
        assertFalse(buffer.isPassthrough() == false);
        assertFalse(buffer.shouldBufferInWindow(), "直通档零窗口缓冲");
    }

    @Test
    void invalidParametersRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedWindowBuffer<>(0, false, 1000L));
        assertThrows(IllegalArgumentException.class, () -> new BoundedWindowBuffer<>(4, false, -1L));
    }

    @Test
    void pollEmptyReturnsNull() {
        BoundedWindowBuffer<String> buffer = new BoundedWindowBuffer<>(4, false, 1000L);
        buffer.beginWindow();
        assertNull(buffer.poll());
        buffer.clear();
        assertTrue(buffer.isEmpty());
    }
}
