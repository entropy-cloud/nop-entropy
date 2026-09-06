package io.nop.sys.dao.lock;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.concurrent.lock.IResourceLockState;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmTemplate;
import io.nop.sys.dao.entity.NopSysLock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysDaoResourceLockManager extends JunitBaseTestCase {

    @Inject
    SysDaoResourceLockManager lockManager;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    ITransactionTemplate transactionTemplate;

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
        assertNotNull(lock2, "expired lock must be taken over by holder2");

        boolean renewed = lockManager.tryResetLease(lock1, 60000);
        assertTrue(!renewed, "stale holder must not reset lease on the new holder's lock");
        assertTrue(lockManager.isHoldingLock(lock2));

        lockManager.releaseLock(lock2);
    }

    /**
     * check2 审计 [P3]：锁行已删除但重插失败（非重复键的持续快速失败）路径无退避，
     * waitTime 窗口内以每轮3次DB访问空转。修复后该分支同样退避100ms再进入下一轮。
     */
    @Test
    public void testDeletedRowRetryFailureBacksOffInsteadOfSpinning() {
        AtomicInteger attempts = new AtomicInteger();
        SysDaoResourceLockManager failingManager = new SysDaoResourceLockManager() {
            @Override
            NopSysLock saveNew(String resourceId, String lockId, long leaseTime, String lockReason, long currentTime) {
                attempts.incrementAndGet();
                throw new IllegalStateException("injected persistent save failure");
            }
        };
        failingManager.setDaoProvider(daoProvider);
        failingManager.setOrmTemplate(ormTemplate);
        failingManager.setTransactionTemplate(transactionTemplate);

        long begin = System.currentTimeMillis();
        IResourceLockState state = failingManager.tryLockWithLease("backoff-test", "holder", 1000, 60000, "TEST");
        long elapsed = System.currentTimeMillis() - begin;

        assertNull(state, "waitTime must be exhausted without acquiring the lock");
        // 注意：测试环境 TestClock 会让 CoreMetrics 虚拟时间快于墙钟（wait 循环提前退出），
        // 不能断言墙钟耗时，退避与否以 saveNew 尝试次数为准（无退避时在虚拟 waitTime 内
        // 空转出数百次以上尝试；100ms 退避后每轮至少间隔 100ms 墙钟，尝试数被限制在几十次内）
        assertTrue(attempts.get() < 100,
                "persistent save failure must back off between retries instead of spinning on the DB, attempts="
                        + attempts.get() + ", elapsed=" + elapsed);
    }
}
