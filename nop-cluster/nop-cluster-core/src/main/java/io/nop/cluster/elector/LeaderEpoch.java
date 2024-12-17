/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.elector;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.Guard;
import jakarta.annotation.Nonnull;

/**
 * 每一次leader发生切换都产生一个新的epoch
 */
@DataBean
public class LeaderEpoch {
    private final String leaderId;
    private final long epoch;

    public LeaderEpoch(@JsonProperty("leaderId") String leaderId, @JsonProperty("epoch") long epoch) {
        this.leaderId = Guard.notEmpty(leaderId, "leaderId");
        this.epoch = epoch;
    }

    public @Nonnull String getLeaderId() {
        return leaderId;
    }

    public long getEpoch() {
        return epoch;
    }
}
