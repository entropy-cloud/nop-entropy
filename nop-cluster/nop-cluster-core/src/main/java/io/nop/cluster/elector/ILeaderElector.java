/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.elector;

import java.util.concurrent.CompletionStage;

public interface ILeaderElector {
    /**
     * 每一个参与leader选举的节点都有一个唯一id
     */
    String getHostId();

    /**
     * 返回leader节点对应的hostId
     */
    default String getLeaderId() {
        LeaderEpoch leaderEpoch = getLeaderEpoch();
        return leaderEpoch == null ? null : leaderEpoch.getLeaderId();
    }

    default long getCurrentEpoch() {
        LeaderEpoch leaderEpoch = getLeaderEpoch();
        return leaderEpoch == null ? -1L : leaderEpoch.getEpoch();
    }

    LeaderEpoch getLeaderEpoch();

    AutoCloseable addElectionListener(ILeaderElectionListener listener);

    /**
     * 判断当前节点是否被选举为leader
     */
    default boolean isLeader() {
        return getHostId().equals(getLeaderId());
    }

    /**
     * 等待一次leader选举完毕。每次发生leader选举都会返回一个新的CompletionStage对象。
     */
    CompletionStage<LeaderEpoch> whenElectionCompleted();

    void restartElection();
}