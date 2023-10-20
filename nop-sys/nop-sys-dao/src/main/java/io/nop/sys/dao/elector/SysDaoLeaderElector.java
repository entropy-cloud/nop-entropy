/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.sys.dao.elector;

import io.nop.api.core.time.IEstimatedClock;
import io.nop.cluster.elector.AbstractLeaderElector;
import io.nop.cluster.elector.LeaderEpoch;
import io.nop.commons.util.MathHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.sys.dao.entity.NopSysClusterLeader;

import jakarta.inject.Inject;
import java.util.concurrent.TimeUnit;

public class SysDaoLeaderElector extends AbstractLeaderElector {
    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    private IEntityDao<NopSysClusterLeader> dao() {
        return daoProvider.daoFor(NopSysClusterLeader.class);
    }

    @Override
    public void restartElection(String preferredLeaderId) {
        IEntityDao<NopSysClusterLeader> dao = dao();
        NopSysClusterLeader leader = dao.getEntityById(getClusterId());
        if (leader != null) {
            removeEpoch(leader);
        }
        tryBecomeLeader();
    }

    protected Void checkLeader() {
        try {
            do {
                IEntityDao<NopSysClusterLeader> dao = dao();
                NopSysClusterLeader leader = dao.getEntityById(getClusterId());
                if (leader == null) {
                    scheduledExecutor.schedule(this::tryBecomeLeader, randomDelay(), TimeUnit.MICROSECONDS);
                } else {
                    IEstimatedClock clock = dao.getDbEstimatedClock();
                    // leader expired
                    if (leader.getExpireAt().getTime() < clock.getMaxCurrentTimeMillis()) {
                        if (removeEpoch(leader)) {
                            // 删除已过期的记录之后，尝试成为leader
                            return tryBecomeLeader();
                        } else {
                            // epoch已经被更新，重新检查
                            continue;
                        }
                    } else {
                        // leader处于有效租期内
                        updateLeader(new LeaderEpoch(leader.getLeaderId(), leader.getLeaderEpoch()));
                        scheduleCheck();
                    }
                }
                break;
            } while (true);
        } catch (Exception e) {
            scheduleCheck();
        }
        return null;
    }

    private void scheduleCheck() {
        scheduledExecutor.schedule(this::checkLeader, getCheckIntervalMs(), TimeUnit.MICROSECONDS);
    }

    private boolean removeEpoch(NopSysClusterLeader leader) {
        NopSysClusterLeader example = new NopSysClusterLeader();
        example.setClusterId(leader.getClusterId());
        example.setLeaderEpoch(leader.getLeaderEpoch());
        return dao().deleteByExample(example) > 0;
    }

    private long randomDelay() {
        return MathHelper.random().nextLong(0, 500);
    }

    protected Void tryBecomeLeader() {
        newElection();
        return null;
    }
}
