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
public class RankingEntry {
    private final String member;
    private final double score;
    private final long rank;

    public RankingEntry(String member, double score, long rank) {
        this.member = member;
        this.score = score;
        this.rank = rank;
    }

    public String getMember() {
        return member;
    }

    public double getScore() {
        return score;
    }

    public long getRank() {
        return rank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RankingEntry))
            return false;
        RankingEntry other = (RankingEntry) o;
        return Objects.equals(member, other.member);
    }

    @Override
    public int hashCode() {
        return member == null ? 0 : member.hashCode();
    }

    @Override
    public String toString() {
        return "RankingEntry[member=" + member + ",score=" + score + ",rank=" + rank + "]";
    }
}
