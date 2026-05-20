/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface INosqlZSetOperations {
    CompletableFuture<Boolean> addAsync(String member, double score);

    boolean add(String member, double score);

    CompletableFuture<Long> addAllAsync(Collection<ZSetEntry> entries);

    long addAll(Collection<ZSetEntry> entries);

    CompletableFuture<Boolean> removeAsync(String member);

    boolean remove(String member);

    CompletableFuture<Double> scoreAsync(String member);

    Double score(String member);

    CompletableFuture<Long> rankAsync(String member);

    Long rank(String member);

    CompletableFuture<Long> revRankAsync(String member);

    Long revRank(String member);

    CompletableFuture<Long> cardAsync();

    long card();

    CompletableFuture<Double> incrementScoreAsync(String member, double delta);

    double incrementScore(String member, double delta);

    CompletableFuture<List<ZSetEntry>> revRangeAsync(long start, long end);

    List<ZSetEntry> revRange(long start, long end);
}
