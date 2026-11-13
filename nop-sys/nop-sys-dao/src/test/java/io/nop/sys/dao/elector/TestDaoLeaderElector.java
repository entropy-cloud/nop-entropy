package io.nop.sys.dao.elector;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.cluster.elector.LeaderEpoch;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.sys.dao.entity.NopSysClusterLeader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoLeaderElector extends JunitBaseTestCase {
    private DefaultScheduledExecutor executor;
    private SysDaoLeaderElector leaderElector;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @BeforeEach
    public void setUp() {
        this.executor = DefaultScheduledExecutor.newSingleThreadTimer("task");

        leaderElector = new SysDaoLeaderElector();
        leaderElector.setDaoProvider(daoProvider);
        leaderElector.setOrmTemplate(ormTemplate);
        leaderElector.setScheduledExecutor(executor);

        leaderElector.setHostId("test");
        leaderElector.setCheckIntervalMs(100);
        leaderElector.setAddr("localhost");
        leaderElector.setPort(8010);
        leaderElector.setClusterId("global");
        leaderElector.setLeaseSafeGap(100);
        leaderElector.setLeaseMs(1000);
        leaderElector.start();
    }

    @AfterEach
    public void tearDown() {
        this.executor.destroy();
        if (leaderElector != null)
            leaderElector.stop();
    }

    @Test
    public void testElection() throws Exception {
        LeaderEpoch leaderEpoch = FutureHelper.syncGet(leaderElector.whenElectionCompleted());
        assertEquals(1, leaderEpoch.getEpoch());

        IEntityDao<NopSysClusterLeader> dao = daoProvider.daoFor(NopSysClusterLeader.class);
        NopSysClusterLeader leader = dao.getEntityById("global");
        assertEquals("test", leader.getLeaderId());

        // C10 时序敏感改写（MA-01 同 diff）：sleep(1000) 固定等待改为状态轮询——
        // 轮询到 leader 行 expireAt 被续期推进（至少一次 refreshLeader 生效），
        // 再断言 epoch 未变（无伪重选）
        java.sql.Timestamp expireAt0 = leader.getExpireAt();
        assertTrue(awaitLeaderRenewed(dao, expireAt0, 5000),
                "leader must renew its lease within the timeout");
        leaderEpoch = FutureHelper.syncGet(leaderElector.whenElectionCompleted());
        assertEquals(1, leaderEpoch.getEpoch());

        leaderElector.restartElection();
        // sleep(1000) → 轮询 DB 行 epoch 到达重选后的预期值 3（1 --restartElection bump→ 2
        // --leader 发现 epoch 落后重新当选→ 3）
        assertTrue(awaitLeaderEpoch(dao, 3, 5000), "re-election must reach epoch 3");
        leaderEpoch = FutureHelper.syncGet(leaderElector.whenElectionCompleted());
        assertEquals(3, leaderEpoch.getEpoch());
    }

    private boolean awaitLeaderRenewed(IEntityDao<NopSysClusterLeader> dao, java.sql.Timestamp expireAt0,
                                       long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            NopSysClusterLeader leader = dao.getEntityById("global");
            if (leader != null && leader.getExpireAt() != null
                    && leader.getExpireAt().getTime() > expireAt0.getTime())
                return true;
            Thread.sleep(20);
        }
        return false;
    }

    private boolean awaitLeaderEpoch(IEntityDao<NopSysClusterLeader> dao, long expectedEpoch,
                                     long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            NopSysClusterLeader leader = dao.getEntityById("global");
            if (leader != null && leader.getLeaderEpoch() == expectedEpoch)
                return true;
            Thread.sleep(20);
        }
        return false;
    }
}
