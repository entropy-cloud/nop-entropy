/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface INosqlRanking {
    CompletableFuture<Boolean> addAsync(String member, double score);

    boolean add(String member, double score);

    CompletableFuture<Double> incrementScoreAsync(String member, double delta);

    double incrementScore(String member, double delta);

    CompletableFuture<Long> getRankAsync(String member);

    long getRank(String member);

    CompletableFuture<Double> getScoreAsync(String member);

    double getScore(String member);

    CompletableFuture<List<RankingEntry>> getTopNAsync(int n);

    List<RankingEntry> getTopN(int n);

    CompletableFuture<List<RankingEntry>> getAroundAsync(String member, int distance);

    List<RankingEntry> getAround(String member, int distance);

    CompletableFuture<Long> sizeAsync();

    long size();

    CompletableFuture<Boolean> removeAsync(String member);

    boolean remove(String member);
}
