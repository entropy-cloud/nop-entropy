/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.elector;

public interface ILeaderElectionListener {

    void becomeLeader(LeaderEpoch leaderEpoch);

    void becomeFollower(LeaderEpoch leaderEpoch);

    default void onException(Throwable e) {
        becomeFollower(null);
    }

    default void onStop() {
        becomeFollower(null);
    }
}
