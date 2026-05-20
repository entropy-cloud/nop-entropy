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
public class ZSetEntry {
    private final String member;
    private final double score;

    public ZSetEntry(String member, double score) {
        this.member = member;
        this.score = score;
    }

    public String getMember() {
        return member;
    }

    public double getScore() {
        return score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ZSetEntry))
            return false;
        ZSetEntry other = (ZSetEntry) o;
        return Objects.equals(member, other.member) && Double.compare(score, other.score) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(member, score);
    }

    @Override
    public String toString() {
        return "ZSetEntry[member=" + member + ",score=" + score + "]";
    }
}
