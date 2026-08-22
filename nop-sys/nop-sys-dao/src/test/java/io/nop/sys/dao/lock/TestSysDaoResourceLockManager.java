package io.nop.sys.dao.lock;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.concurrent.lock.IResourceLockState;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysDaoResourceLockManager extends JunitBaseTestCase {

    @Inject
    SysDaoResourceLockManager lockManager;

    @Test
    public void testLock() {
        lockManager.runWithLock("test", "aa", "DEMO", lock -> {
            assertTrue(lock.isHoldingLock());
            System.out.println("run");
        });
    }

    /**
     * 未过期的锁在争用时不能被其他持有者抢占删除，否则互斥失效
     */
    @Test
    public void testTryLockContentionDoesNotStealValidLock() {
        IResourceLockState lock1 = lockManager.tryLockWithLease("test-valid-lock", "holder1", 5000, 60000, "TEST");
        assertNotNull(lock1);

        IResourceLockState lock2 = lockManager.tryLockWithLease("test-valid-lock", "holder2", 1000, 60000, "TEST");
        assertNull(lock2, "unexpired lock must not be acquired by a contending holder");

        assertTrue(lockManager.isHoldingLock(lock1));

        lockManager.releaseLock(lock1);
    }

    /**
     * 持有者宕机未释放时，锁过期后应被回收，新持有者可以获取
     */
    @Test
    public void testExpiredLockIsRecovered() {
        IResourceLockState lock1 = lockManager.tryLockWithLease("test-expired-lock", "holder1", 5000, 300, "TEST");
        assertNotNull(lock1);

        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", e);
        }

        IResourceLockState lock2 = lockManager.tryLockWithLease("test-expired-lock", "holder2", 3000, 60000, "TEST");
        assertNotNull(lock2);
        assertTrue(lockManager.isHoldingLock(lock2));

        lockManager.releaseLock(lock2);
    }
}
