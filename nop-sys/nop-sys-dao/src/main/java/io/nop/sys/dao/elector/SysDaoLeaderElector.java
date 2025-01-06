/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao.elector;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.time.IEstimatedClock;
import io.nop.cluster.elector.AbstractPollingLeaderElector;
import io.nop.cluster.elector.LeaderEpoch;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.sys.dao.entity.NopSysClusterLeader;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;

public class SysDaoLeaderElector extends AbstractPollingLeaderElector {
    static final Logger LOG = LoggerFactory.getLogger(SysDaoLeaderElector.class);

    private IOrmTemplate ormTemplate;
    private IDaoProvider daoProvider;

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    private IEntityDao<NopSysClusterLeader> dao() {
        return daoProvider.daoFor(NopSysClusterLeader.class);
    }

    @Override
    protected Void checkElection() {
        try {
            int loopCount = 0;
            do {
                if (loopCount > 20)
                    break;

                boolean bLeader = isLeader();

                IEntityDao<NopSysClusterLeader> dao = dao();
                NopSysClusterLeader leader = dao.getEntityById(getClusterId());
                if (bLeader) {
                    if (checkLeader(leader, dao))
                        break;
                } else {
                    // 当前状态为follower
                    if (checkFollower(leader, dao))
                        break;
                }
                loopCount++;
            } while (!isStopping());
        } catch (Exception e) {
            LOG.info("nop.cluster.leader-elector.check-fail", e);
            onException(e);

            this.onBecomeFollower(null);
            this.onRestartElection();
        }
        if (!isStopping())
            scheduleCheck();
        return null;
    }

    private boolean checkLeader(NopSysClusterLeader leader, IEntityDao<NopSysClusterLeader> dao) {
        IEstimatedClock clock = dao.getDbEstimatedClock();
        long currentTime = clock.getMaxCurrentTimeMillis();

        if (leader == null || !leader.getLeaderId().equals(getHostId())) {
            // 如果数据库中记录的leader不是当前节点，则出现了未知错误
            LeaderEpoch leaderEpoch = toLeaderEpoch(leader);
            this.onBecomeFollower(leaderEpoch);
            onRestartElection();
            return false;
        } else if (leader.getLeaderEpoch() < getCurrentEpoch()) {
            changeLeader(leader, dao);
            return false;
        } else if (leader.getLeaderEpoch() > getCurrentEpoch()) {
            LOG.error("nop.cluster.leader-elector.leader-epoch-mismatch:leaderInDb={},current={}", leader.getLeaderEpoch(), getCurrentEpoch());
            this.onBecomeFollower(toLeaderEpoch(leader));
            onRestartElection();
            return false;
        } else if (currentTime < leader.getExpireAt().getTime() - this.getLeaseSafeGap()) {
            // 是leader，且肯定没有超时
            refreshLeader(leader, dao);
            return true;
        } else {
            // 是leader，但可能已超时，先尝试续期
            if (!tryRenewLease(leader, dao)) {
                this.onBecomeFollower(null);
                onRestartElection();
                return false;
            } else {
                return true;
            }
        }
    }

    private void refreshLeader(NopSysClusterLeader leader, IEntityDao<NopSysClusterLeader> dao) {
        IEstimatedClock clock = dao.getDbEstimatedClock();
        leader.setRefreshTime(clock.getMinCurrentTime());
        leader.setExpireAt(new Timestamp(clock.getMaxCurrentTimeMillis() + this.getLeaseMs()));
        dao.updateEntityDirectly(leader);
    }

    private boolean tryRenewLease(NopSysClusterLeader leader, IEntityDao<NopSysClusterLeader> dao) {
        IEstimatedClock clock = dao.getDbEstimatedClock();
        leader.orm_disableOptimisticLock(true);

        leader.setRefreshTime(clock.getMinCurrentTime());
        leader.setExpireAt(new Timestamp(clock.getMaxCurrentTimeMillis() + this.getLeaseMs()));
        dao.updateEntityDirectly(leader);
        return !leader.orm_readonly();
    }

    private boolean checkFollower(NopSysClusterLeader leader, IEntityDao<NopSysClusterLeader> dao) {
        IEstimatedClock clock = dao.getDbEstimatedClock();
        long currentTime = clock.getMaxCurrentTimeMillis();

        if (leader == null) {
            // 如果当前没有leader
            try {
                tryBecomeLeader(dao);
            } catch (Exception e) {
                // 竞争成为leader失败，可以安全忽略此异常，下次再重试
                LOG.debug("nop.cluster.leader-elector.become-leader-fail", e);
            }
            return true;
        } else if (leader.getLeaderId().equals(getHostId())) {
            // 数据库记录认为当前节点是leader，但是实际情况却是follower。
            if (changeLeader(leader, dao)) {
                LOG.info("nop.cluster.leader-elector.change-leader:leaderId={}", getHostId());
            }
            // 立刻重试
            return false;
        } else if (currentTime < leader.getExpireAt().getTime()) {
            if (!leader.getLeaderId().equals(this.getLeaderId()) || leader.getLeaderEpoch() != getCurrentEpoch()) {
                LeaderEpoch leaderEpoch = toLeaderEpoch(leader);
                this.onBecomeFollower(leaderEpoch);
                onElectionCompleted(leaderEpoch);
            }

            // leader未超时
            return true;
        } else {
            // leader已超时，尝试删除
            if (changeLeader(leader, dao)) {
                LOG.info("nop.cluster.leader-elector.change-leader-when-expired:leaderId={}", getHostId());
            }
            // 立刻重试
            return false;
        }
    }

    private void tryBecomeLeader(IEntityDao<NopSysClusterLeader> dao) {
        IEstimatedClock clock = dao.getDbEstimatedClock();
        Timestamp currentTime = clock.getMinCurrentTime();
        NopSysClusterLeader leader = dao.newEntity();
        leader.setClusterId(getClusterId());
        leader.setLeaderId(getHostId());
        leader.setLeaderEpoch(1L);
        leader.setExpireAt(new Timestamp(clock.getMaxCurrentTimeMillis() + this.getLeaseMs()));
        leader.setRefreshTime(currentTime);
        leader.setAppName(AppConfig.appName());
        leader.setLeaderAdder(getLeaderAddr());
        leader.setElectTime(currentTime);
        dao.saveEntityDirectly(leader);

        LeaderEpoch leaderEpoch = toLeaderEpoch(leader);
        onBecomeLeader(leaderEpoch);

        onElectionCompleted(leaderEpoch);
    }

    private LeaderEpoch toLeaderEpoch(NopSysClusterLeader leader) {
        if (leader == null)
            return null;
        return new LeaderEpoch(leader.getLeaderId(), leader.getLeaderEpoch(), leader.getExpireAt());
    }

    private boolean changeLeader(NopSysClusterLeader leader, IEntityDao<NopSysClusterLeader> dao) {
        IEstimatedClock clock = dao.getDbEstimatedClock();
        leader.setLeaderAdder(getLeaderAddr());
        leader.setLeaderId(getHostId());
        leader.setLeaderEpoch(leader.getLeaderEpoch() + 1);
        leader.setExpireAt(new Timestamp(clock.getMaxCurrentTimeMillis() + this.getLeaseMs()));
        leader.setRefreshTime(clock.getMinCurrentTime());
        leader.setElectTime(clock.getMinCurrentTime());
        leader.setAppName(AppConfig.appName());

        leader.orm_disableOptimisticLock(true);

        dao.updateEntityDirectly(leader);

        if (leader.orm_readonly()) {
            LOG.debug("nop.cluster.leader-elector.change-leader-fail:leaderId={},epoch={}", getLeaderId(), leader.getLeaderEpoch());
            return false;
        }

        LeaderEpoch leaderEpoch = toLeaderEpoch(leader);
        onBecomeLeader(leaderEpoch);
        onElectionCompleted(leaderEpoch);
        return true;
    }

    @Override
    public void restartElection() {
        IEntityDao<NopSysClusterLeader> dao = dao();

        for (int i = 0; i < 10; i++) {
            NopSysClusterLeader leader = getEntity(dao);
            if (leader == null)
                return;

            // 增大epoch将导致当前的leader发现epoch已改变，需要重新获取leader
            leader.setLeaderEpoch(leader.getLeaderEpoch() + 1);
            leader.orm_disableOptimisticLock(true);
            dao.updateEntityDirectly(leader);

            if (!leader.orm_readonly())
                break;
        }
    }

    NopSysClusterLeader getEntity(IEntityDao<NopSysClusterLeader> dao) {
        return ormTemplate.runInNewSession(session -> {
            return dao.getEntityById(getClusterId());
        });
    }
}