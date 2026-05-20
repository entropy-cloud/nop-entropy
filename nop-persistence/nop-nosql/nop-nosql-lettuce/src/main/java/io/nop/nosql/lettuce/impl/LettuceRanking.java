/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.ScoredValue;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.nop.nosql.core.INosqlRanking;
import io.nop.nosql.core.RankingEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LettuceRanking implements INosqlRanking {
    private final LettuceRedisConnectionProvider client;
    private final String key;

    public LettuceRanking(LettuceRedisConnectionProvider client, String key) {
        this.client = client;
        this.key = key;
    }

    protected RedisAdvancedClusterAsyncCommands<String, Object> async() {
        return client.getConnection().async();
    }

    protected RedisAdvancedClusterCommands<String, Object> sync() {
        return client.getConnection().sync();
    }

    @Override
    public CompletableFuture<Boolean> addAsync(String member, double score) {
        return async().zadd(key, score, member)
                .thenApply(n -> n != null && n > 0)
                .toCompletableFuture();
    }

    @Override
    public boolean add(String member, double score) {
        return addAsync(member, score).join();
    }

    @Override
    public CompletableFuture<Double> incrementScoreAsync(String member, double delta) {
        return async().zincrby(key, delta, member).toCompletableFuture();
    }

    @Override
    public double incrementScore(String member, double delta) {
        return incrementScoreAsync(member, delta).join();
    }

    // getRank = ZREVRANK (0-based, highest score = rank 0)
    @Override
    public CompletableFuture<Long> getRankAsync(String member) {
        return async().zrevrank(key, member)
                .thenApply(v -> v != null ? v : -1L)
                .toCompletableFuture();
    }

    @Override
    public long getRank(String member) {
        return getRankAsync(member).join();
    }

    @Override
    public CompletableFuture<Double> getScoreAsync(String member) {
        return async().zscore(key, member).toCompletableFuture();
    }

    @Override
    public double getScore(String member) {
        Double score = getScoreAsync(member).join();
        return score != null ? score : Double.NaN;
    }

    @Override
    public CompletableFuture<List<RankingEntry>> getTopNAsync(int n) {
        // ZREVRANGE WITHSCORES returns members with highest scores first
        // Optimization: since ZREVRANGE is ordered, rank = offset + index
        return async().zrevrangeWithScores(key, 0, n - 1)
                .<List<RankingEntry>>thenApply(list -> {
                    if (list == null)
                        return new ArrayList<>();
                    List<RankingEntry> entries = new ArrayList<>(list.size());
                    long idx = 0;
                    for (ScoredValue<Object> sv : list) {
                        entries.add(new RankingEntry(
                                String.valueOf(sv.getValue()),
                                sv.getScore(),
                                idx++));
                    }
                    return entries;
                }).toCompletableFuture();
    }

    @Override
    public List<RankingEntry> getTopN(int n) {
        return getTopNAsync(n).join();
    }

    @Override
    public CompletableFuture<List<RankingEntry>> getAroundAsync(String member, int distance) {
        return getRankAsync(member).thenCompose(rank -> {
            if (rank < 0) {
                return CompletableFuture.completedFuture(new ArrayList<RankingEntry>());
            }
            long start = Math.max(0, rank - distance);
            long end = rank + distance;
            return async().zrevrangeWithScores(key, start, end)
                    .<List<RankingEntry>>thenApply(list -> {
                        if (list == null)
                            return new ArrayList<>();
                        List<RankingEntry> entries = new ArrayList<>(list.size());
                        long currentRank = start;
                        for (ScoredValue<Object> sv : list) {
                            entries.add(new RankingEntry(
                                    String.valueOf(sv.getValue()),
                                    sv.getScore(),
                                    currentRank++));
                        }
                        return entries;
                    }).toCompletableFuture();
        }).toCompletableFuture();
    }

    @Override
    public List<RankingEntry> getAround(String member, int distance) {
        return getAroundAsync(member, distance).join();
    }

    @Override
    public CompletableFuture<Long> sizeAsync() {
        return async().zcard(key).toCompletableFuture();
    }

    @Override
    public long size() {
        return sizeAsync().join();
    }

    @Override
    public CompletableFuture<Boolean> removeAsync(String member) {
        return async().zrem(key, member)
                .thenApply(n -> n != null && n > 0)
                .toCompletableFuture();
    }

    @Override
    public boolean remove(String member) {
        return removeAsync(member).join();
    }
}
