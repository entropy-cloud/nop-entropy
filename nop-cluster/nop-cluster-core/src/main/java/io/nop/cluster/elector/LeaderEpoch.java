/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.elector;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

/**
 * 每一次leader发生切换都产生一个新的epoch
 */
@DataBean
public class LeaderEpoch {
    private final String leaderId;
    private final long epoch;

    public LeaderEpoch(@JsonProperty("leaderId") String leaderId, @JsonProperty("epoch") long epoch) {
        this.leaderId = leaderId;
        this.epoch = epoch;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public long getEpoch() {
        return epoch;
    }
}
