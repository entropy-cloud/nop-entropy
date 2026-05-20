/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Objects;

@DataBean
public class RateLimiterConfig {
    private final double rate;
    private final double capacity;

    public RateLimiterConfig(double rate, double capacity) {
        this.rate = rate;
        this.capacity = capacity;
    }

    public double getRate() {
        return rate;
    }

    public double getCapacity() {
        return capacity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RateLimiterConfig))
            return false;
        RateLimiterConfig other = (RateLimiterConfig) o;
        return Double.compare(rate, other.rate) == 0
                && Double.compare(capacity, other.capacity) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rate, capacity);
    }

    @Override
    public String toString() {
        return "RateLimiterConfig[rate=" + rate + ",capacity=" + capacity + "]";
    }
}
