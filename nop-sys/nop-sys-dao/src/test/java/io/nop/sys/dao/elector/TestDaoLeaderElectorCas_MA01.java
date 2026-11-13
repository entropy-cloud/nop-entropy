package io.nop.sys.dao.elector;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.sys.dao.entity.NopSysClusterLeader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-01 子批②（C2-004/C2-007）判定修正特征钉（plan 2525）。
 *
 * <p>两 finding 的「disableVersionCheckError 后写覆盖先写/无条件续期」前提经 live ORM 语义核实
 * 证伪：genUpdateSql 生成的实体 UPDATE 恒带 "AND version=?" 条件（GenSqlHelper :349-354），该标志
 * 只把 0 行结果从抛异常降级为 orm_readonly 标记（EntityPersisterImpl.checkUpdateResult）。故
 * changeLeader/tryRenewLease 现状即乐观锁 CAS——本钉固化该平台语义，防止后续被误判为缺陷再次「修复」：
 * 同行双实体竞争更新（对应双 follower 同周期抢占 / 他人接管后旧 leader 续约）至多一个胜出。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoLeaderElectorCas_MA01 extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private NopSysClusterLeader insertLeaderRow(String leaderId) {
        IEntityDao<NopSysClusterLeader> dao = daoProvider.daoFor(NopSysClusterLeader.class);
        NopSysClusterLeader row = dao.newEntity();
        row.setClusterId("cas-cluster");
        row.setLeaderId(leaderId);
        row.setLeaderEpoch(1L);
        row.setExpireAt(new Timestamp(System.currentTimeMillis() + 60_000));
        row.setRefreshTime(new Timestamp(System.currentTimeMillis()));
        row.setAppName("test-app");
        row.setLeaderAdder("localhost:8010");
        row.setElectTime(new Timestamp(System.currentTimeMillis()));
        dao.saveEntityDirectly(row);
        return row;
    }

    private NopSysClusterLeader loadDetached(String clusterId) {
        // orm().get 每次调用独立 session：两次加载得到同一 DB 行的两个独立实体副本
        return daoProvider.daoFor(NopSysClusterLeader.class).getEntityById(clusterId);
    }

    /**
     * C2-004 面：双 follower 同读一行后各自 changeLeader 语义的更新（epoch+1 + 自立为主），
     * 乐观锁版本条件保证至多一个当选。
     */
    @Test
    public void testConcurrentChangeLeaderAtMostOneWinner() {
        insertLeaderRow("node-0");
        NopSysClusterLeader a = loadDetached("cas-cluster");
        NopSysClusterLeader b = loadDetached("cas-cluster");
        assertNotSame(a, b, "two detached copies of the same row are required");

        // 两个 follower 各自推进 epoch 并自立为主（changeLeader 语义）
        a.setLeaderId("node-a");
        a.setLeaderEpoch(a.getLeaderEpoch() + 1);
        a.setExpireAt(new Timestamp(System.currentTimeMillis() + 1000));
        a.orm_disableVersionCheckError(true);
        daoProvider.daoFor(NopSysClusterLeader.class).updateEntityDirectly(a);
        assertFalse(a.orm_readonly(), "first changeLeader must win the version CAS");

        b.setLeaderId("node-b");
        b.setLeaderEpoch(b.getLeaderEpoch() + 1);
        b.setExpireAt(new Timestamp(System.currentTimeMillis() + 1000));
        b.orm_disableVersionCheckError(true);
        daoProvider.daoFor(NopSysClusterLeader.class).updateEntityDirectly(b);
        assertTrue(b.orm_readonly(),
                "second changeLeader against the same base version must lose (orm_readonly) —"
                        + " no transient dual-leader within one polling cycle");
    }

    /**
     * C2-007 面：他人接管后旧 leader 的续约（仅推进 refreshTime/expireAt）被版本 CAS 拒绝。
     */
    @Test
    public void testStaleLeaderRenewalRejectedByVersionCas() {
        insertLeaderRow("node-0");
        NopSysClusterLeader a = loadDetached("cas-cluster");
        NopSysClusterLeader b = loadDetached("cas-cluster");

        // a 正常续期一次，DB version 前进
        a.setRefreshTime(new Timestamp(System.currentTimeMillis()));
        a.setExpireAt(new Timestamp(System.currentTimeMillis() + 2000));
        a.orm_disableVersionCheckError(true);
        daoProvider.daoFor(NopSysClusterLeader.class).updateEntityDirectly(a);
        assertFalse(a.orm_readonly(), "fresh leader renewal must succeed");

        // b（过期副本，等价旧 leader 视角）续期：版本条件失配 → readonly → tryRenewLease 语义返回 false
        b.setRefreshTime(new Timestamp(System.currentTimeMillis()));
        b.setExpireAt(new Timestamp(System.currentTimeMillis() + 2000));
        b.orm_disableVersionCheckError(true);
        daoProvider.daoFor(NopSysClusterLeader.class).updateEntityDirectly(b);
        assertTrue(b.orm_readonly(),
                "stale leader's lease renewal must be rejected by the version CAS (tryRenewLease returns false)");
    }
}
