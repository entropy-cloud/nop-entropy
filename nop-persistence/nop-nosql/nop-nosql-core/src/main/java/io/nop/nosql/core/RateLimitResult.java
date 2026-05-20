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
public class RateLimitResult {
    private final boolean allowed;
    private final long remainingTokens;

    public RateLimitResult(boolean allowed, long remainingTokens) {
        this.allowed = allowed;
        this.remainingTokens = remainingTokens;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getRemainingTokens() {
        return remainingTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RateLimitResult))
            return false;
        RateLimitResult other = (RateLimitResult) o;
        return allowed == other.allowed && remainingTokens == other.remainingTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowed, remainingTokens);
    }

    @Override
    public String toString() {
        return "RateLimitResult[allowed=" + allowed + ",remainingTokens=" + remainingTokens + "]";
    }
}
