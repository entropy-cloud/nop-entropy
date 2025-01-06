package io.nop.sys.dao.elector;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.cluster.elector.LeaderEpoch;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.commons.concurrent.thread.ThreadHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.sys.dao.entity.NopSysClusterLeader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
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
    public void testElection() {
        LeaderEpoch leaderEpoch = FutureHelper.syncGet(leaderElector.whenElectionCompleted());
        assertEquals(1, leaderEpoch.getEpoch());

        ThreadHelper.sleep(1000);
        leaderEpoch = FutureHelper.syncGet(leaderElector.whenElectionCompleted());
        assertEquals(1, leaderEpoch.getEpoch());

        IEntityDao<NopSysClusterLeader> dao = daoProvider.daoFor(NopSysClusterLeader.class);
        NopSysClusterLeader leader = dao.getEntityById("global");
        assertEquals("test", leader.getLeaderId());

        leaderElector.restartElection();
        ThreadHelper.sleep(1000);
        leaderEpoch = FutureHelper.syncGet(leaderElector.whenElectionCompleted());
        assertEquals(3, leaderEpoch.getEpoch());
    }
}
