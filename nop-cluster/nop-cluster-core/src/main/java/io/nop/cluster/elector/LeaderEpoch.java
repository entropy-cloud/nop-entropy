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

import java.sql.Timestamp;

/**
 * 每一次leader发生切换都产生一个新的epoch
 */
@DataBean
public class LeaderEpoch {
    private final String leaderId;
    private final long epoch;
    private final Timestamp expireAt;

    public LeaderEpoch(@JsonProperty("leaderId") String leaderId, @JsonProperty("epoch") long epoch,
                       @JsonProperty("expireAt") Timestamp expireAt) {
        this.leaderId = Guard.notEmpty(leaderId, "leaderId");
        this.epoch = epoch;
        this.expireAt = expireAt;
    }

    public Timestamp getExpireAt() {
        return expireAt;
    }

    public @Nonnull String getLeaderId() {
        return leaderId;
    }

    public long getEpoch() {
        return epoch;
    }
}
