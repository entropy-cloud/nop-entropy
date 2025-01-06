package io.nop.sys.dao.elector;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.time.CoreMetrics;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.sys.dao.entity.NopSysClusterLeader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestOptimisticLock extends JunitBaseTestCase {
    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testDisableOptimisticLock(){
        IEntityDao<NopSysClusterLeader> dao = daoProvider.daoFor(NopSysClusterLeader.class);

        NopSysClusterLeader leader = dao.newEntity();
        leader.setLeaderId("test");
        leader.setClusterId("test");
        leader.setLeaderAdder("localhost");
        leader.setAppName("app");
        leader.setElectTime(CoreMetrics.currentTimestamp());
        leader.setExpireAt(CoreMetrics.currentTimestamp());
        leader.setRefreshTime(CoreMetrics.currentTimestamp());
        leader.setLeaderEpoch(100L);
        dao.saveEntity(leader);

        leader.setVersion(100);
        leader.orm_clearDirty();

        leader.setLeaderEpoch(120L);
        try {
            dao.updateEntityDirectly(leader);
        }catch (Exception e){
            leader.orm_disableOptimisticLock(true);
            leader.setLeaderAdder("2021");
            dao.updateEntityDirectly(leader);
        }
        assertTrue(leader.orm_readonly());
    }
}
