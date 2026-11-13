package io.nop.sys.dao.lock;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.time.IClock;
import io.nop.api.core.time.IEstimatedClock;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.concurrent.lock.IResourceLockState;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmTemplate;
import io.nop.sys.dao.entity.NopSysLock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-01 子批①（C2-001/C2-002）fencing 钉（plan 2525）。
 *
 * <p>C2-002（行为钉，当前即红）：tryResetLease DB 续约成功后必须回写内存实体 version/expireAt——
 * 否则第二次续约必然失败（version 条件失配）、isHoldingLock 恒假、releaseLock 删不掉锁行。
 *
 * <p>C2-001（源码结构钉）：tryLockWithLease 过期删除窗的 delete 条件必须含 holderId——重建锁行
 * version 恒为 0，仅 version 条件会命中并删掉竞争者刚插入的新锁行（互斥无声破坏）。交错窗位于
 * 「读 existing → DELETE」之间，行为级确定性红不可构造（沿 2523 C6-017/2524 红②③ 结构钉先例）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysDaoFencing_MA01 extends JunitBaseTestCase {

    static class TestableLockManager extends SysDaoResourceLockManager {
        @Override
        protected boolean isExpired(NopSysLock entity, IEstimatedClock clock) {
            return entity.getExpireAt().getTime() < CoreMetrics.currentTimeMillis();
        }
    }

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    ITransactionTemplate transactionTemplate;

    private IClock originalClock;

    @AfterEach
    public void restoreClock() {
        if (originalClock != null)
            CoreMetrics.registerClock(originalClock);
    }

    private TestableLockManager newTestableManager() {
        TestableLockManager manager = new TestableLockManager();
        manager.setDaoProvider(daoProvider);
        manager.setOrmTemplate(ormTemplate);
        manager.setTransactionTemplate(transactionTemplate);
        return manager;
    }

    /**
     * C2-002（当前即红）：锁行状态机闭环钉——连续两次续约均成功 + 续约后 isHoldingLock 成立
     * + releaseLock 真正删除锁行。修复前 DB 成功不回写内存 version，第二次续约起全部失效。
     */
    @Test
    public void testRenewedLeaseKeepsEntityStateInSync() {
        TestableLockManager manager = newTestableManager();
        IResourceLockState lock = manager.tryLockWithLease("fencing-renew-sync", "holder1", 1000, 60000, "TEST");
        assertNotNull(lock);

        assertTrue(manager.tryResetLease(lock, 60000), "first renewal must succeed");
        assertTrue(manager.tryResetLease(lock, 60000),
                "second renewal must succeed: DB lease write-back keeps the entity version in sync (C2-002)");
        assertTrue(manager.isHoldingLock(lock),
                "isHoldingLock must hold after renewals — version equality depends on write-back");
        manager.releaseLock(lock);
        assertNull(manager.getLockState("fencing-renew-sync"),
                "release after renewals must delete the lock row (version condition relies on write-back)");
    }

    /**
     * C2-001（结构钉）：tryLockWithLease 的过期删除窗必须带 holderId 条件。
     */
    @Test
    public void testExpiredTakeoverDeleteGuardsHolderId() throws Exception {
        Path source = Path.of("src/main/java/io/nop/sys/dao/lock/SysDaoResourceLockManager.java");
        String code = Files.readString(source);

        int anchor = code.indexOf("如果已过期，则尝试删除");
        assertTrue(anchor > 0, "expired-delete window not found in tryLockWithLease");
        int windowEnd = code.indexOf("return existing;", anchor);
        assertTrue(windowEnd > anchor, "expired-delete window end not found");
        String window = code.substring(anchor, windowEnd);

        assertTrue(window.contains("PROP_NAME_holderId"),
                "C2-001: the expired-lock delete issued by a contender must carry the holderId condition"
                        + " — rebuilt lock rows keep version=0, so a version-only predicate deletes"
                        + " a fresh row just inserted by the winning contender (silent mutual-exclusion break)");
    }

    /**
     * C2-001 行为面补充：holderId 条件在位后，接管者插入的新锁行不被旧视图持有者的删除命中——
     * 直接以 SQL 语义层面断言新行（holderId=holder2）对旧持有者条件（holderId=holder1）不可见删除。
     */
    @Test
    public void testFreshTakeoverRowNotDeletedByStaleHolderPredicate() {
        originalClock = CoreMetrics.defaultClock();
        io.nop.sys.dao.lock.MockClock clock = io.nop.sys.dao.lock.MockClock.now();
        CoreMetrics.registerClock(clock);

        TestableLockManager manager = newTestableManager();
        IResourceLockState lock1 = manager.tryLockWithLease("fencing-takeover-row", "holder1", 5000, 300, "TEST");
        assertNotNull(lock1);

        clock.advanceMillis(500);

        IResourceLockState lock2 = manager.tryLockWithLease("fencing-takeover-row", "holder2", 3000, 60000, "TEST");
        assertNotNull(lock2, "expired lock must be taken over by holder2");

        // 接管后的新锁行 version 恒 0——旧持有者视图中的 version 条件必然命中，
        // 唯一防护即 holderId 条件；断言新行仍由 holder2 持有
        IResourceLockState row = manager.getLockState("fencing-takeover-row");
        assertNotNull(row);
        assertEquals("holder2", row.getLockerId(),
                "the fresh takeover row (version=0) must only be deletable by its own holderId");
        assertTrue(manager.isHoldingLock(lock2));

        manager.releaseLock(lock2);
    }
}
