package io.nop.commons.concurrent;

import io.nop.commons.concurrent.semaphore.HighWatermarkSemaphore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestHighWatermarkSemaphore {
    @Test
    public void testWatermark() throws Exception {
        HighWatermarkSemaphore sem = new HighWatermarkSemaphore(10, 5);

// 初始获取 (used=8)
        assertTrue(sem.tryAcquire(8, 1));

// 超过高水位 (used=11)
        assertFalse(sem.tryAcquire(3, 1));
        assertTrue(sem.tryAcquire(1, 0));

// 新请求必须等待，即使释放到可以满足
        assertFalse(sem.tryAcquire(2, 100)); // 应该超时而非获取成功

// 部分释放但不低于低水位 (11->7)
        sem.release(4);

// 完全释放到低水位以下 (7->3)
        sem.release(4);

// 现在可以正常获取
        assertTrue(sem.tryAcquire(5, 1)); // used=8
    }
}
