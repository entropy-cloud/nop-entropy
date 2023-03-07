/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.time;

import io.nop.api.core.beans.LongRangeBean;

import java.sql.Timestamp;

/**
 * 无法获取到当前的精确时间，但是可以对当前时间进行一个估计，真实时间处于最小和最大的范围之间
 */
public interface IEstimatedClock {

    /**
     * currentTimeMillis >= minCurrentTimeMillis
     *
     * @return 返回当前真实时间的下限
     */
    long getMinCurrentTimeMillis();

    /**
     * currentTimeMillis <= maxCurrentTimeMillis
     *
     * @return 返回当前真实时间的上限
     */
    long getMaxCurrentTimeMillis();

    LongRangeBean getCurrentTimeRange();

    default Timestamp getMinCurrentTime() {
        return new Timestamp(getMinCurrentTimeMillis());
    }

    default Timestamp getMaxCurrentTime() {
        return new Timestamp(getMaxCurrentTimeMillis());
    }
}
