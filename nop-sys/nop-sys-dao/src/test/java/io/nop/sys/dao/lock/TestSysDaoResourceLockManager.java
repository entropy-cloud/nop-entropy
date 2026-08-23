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

    /**
     * check 审计 [P1]：重建的锁行 version 恒为 0，旧持有者 unlock 的版本条件形同虚设。
     * 修复前旧持有者（租约过期后被接管）执行 DELETE WHERE version=0 会删掉新持有者的锁；
     * 修复后删除带 holderId 条件，新持有者的锁不受影响。
     */
    @Test
    public void testStaleHolderReleaseDoesNotDeleteTakeoverLock() {
        IResourceLockState lock1 = lockManager.tryLockWithLease("test-takeover-release", "holder1", 5000, 300, "TEST");
        assertNotNull(lock1);

        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", e);
        }

        IResourceLockState lock2 = lockManager.tryLockWithLease("test-takeover-release", "holder2", 3000, 60000, "TEST");
        assertNotNull(lock2, "expired lock must be taken over by holder2");

        // 旧持有者迟到的unlock：不得删除新持有者的锁
        lockManager.releaseLock(lock1);

        assertTrue(lockManager.isHoldingLock(lock2),
                "stale holder's late unlock must NOT delete the new holder's lock");

        lockManager.releaseLock(lock2);
    }

    /**
     * check 审计 [P1] 同族：旧持有者迟到的续约不得改写新持有者的 expireAt。
     */
    @Test
    public void testStaleHolderResetLeaseDoesNotExtendTakeoverLock() {
        IResourceLockState lock1 = lockManager.tryLockWithLease("test-takeover-lease", "holder1", 5000, 300, "TEST");
        assertNotNull(lock1);

        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", e);
        }

        IResourceLockState lock2 = lockManager.tryLockWithLease("test-takeover-lease", "holder2", 3000, 60000, "TEST");
        assertNotNull(lock2);

        boolean renewed = lockManager.tryResetLease(lock1, 60000);
        assertTrue(!renewed, "stale holder must not reset lease on the new holder's lock");
        assertTrue(lockManager.isHoldingLock(lock2));

        lockManager.releaseLock(lock2);
    }
}
