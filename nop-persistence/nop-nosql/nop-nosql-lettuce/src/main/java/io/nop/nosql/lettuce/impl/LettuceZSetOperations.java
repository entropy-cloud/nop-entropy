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
import io.nop.nosql.core.INosqlZSetOperations;
import io.nop.nosql.core.ZSetEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LettuceZSetOperations implements INosqlZSetOperations {
    private final LettuceRedisConnectionProvider client;
    private final String key;

    public LettuceZSetOperations(LettuceRedisConnectionProvider client, String key) {
        this.client = client;
        this.key = key;
    }

    protected RedisAdvancedClusterAsyncCommands<String, Object> async() {
        return client.getConnection().async();
    }

    @Override
    public CompletableFuture<Boolean> addAsync(String member, double score) {
        return async().zadd(key, score, member).thenApply(n -> n > 0).toCompletableFuture();
    }

    @Override
    public boolean add(String member, double score) {
        return addAsync(member, score).join();
    }

    @Override
    public CompletableFuture<Long> addAllAsync(Collection<ZSetEntry> entries) {
        Object[] scoresAndMembers = new Object[entries.size() * 2];
        int i = 0;
        for (ZSetEntry entry : entries) {
            scoresAndMembers[i++] = entry.getScore();
            scoresAndMembers[i++] = entry.getMember();
        }
        return async().zadd(key, scoresAndMembers).toCompletableFuture();
    }

    @Override
    public long addAll(Collection<ZSetEntry> entries) {
        return addAllAsync(entries).join();
    }

    @Override
    public CompletableFuture<Boolean> removeAsync(String member) {
        return async().zrem(key, member).thenApply(n -> n > 0).toCompletableFuture();
    }

    @Override
    public boolean remove(String member) {
        return removeAsync(member).join();
    }

    @Override
    public CompletableFuture<Double> scoreAsync(String member) {
        return async().zscore(key, member).toCompletableFuture();
    }

    @Override
    public Double score(String member) {
        return scoreAsync(member).join();
    }

    @Override
    public CompletableFuture<Long> rankAsync(String member) {
        return async().zrank(key, member).toCompletableFuture();
    }

    @Override
    public Long rank(String member) {
        return rankAsync(member).join();
    }

    @Override
    public CompletableFuture<Long> revRankAsync(String member) {
        return async().zrevrank(key, member).toCompletableFuture();
    }

    @Override
    public Long revRank(String member) {
        return revRankAsync(member).join();
    }

    @Override
    public CompletableFuture<Long> cardAsync() {
        return async().zcard(key).toCompletableFuture();
    }

    @Override
    public long card() {
        return cardAsync().join();
    }

    @Override
    public CompletableFuture<Double> incrementScoreAsync(String member, double delta) {
        return async().zincrby(key, delta, member).toCompletableFuture();
    }

    @Override
    public double incrementScore(String member, double delta) {
        return incrementScoreAsync(member, delta).join();
    }

    @Override
    public CompletableFuture<List<ZSetEntry>> revRangeAsync(long start, long end) {
        CompletableFuture<List<ZSetEntry>> result = async().zrevrangeWithScores(key, start, end).thenApply(list -> {
            if (list == null)
                return new ArrayList<ZSetEntry>();
            List<ZSetEntry> entries = new ArrayList<>(list.size());
            for (ScoredValue<Object> sv : list) {
                entries.add(new ZSetEntry(String.valueOf(sv.getValue()), sv.getScore()));
            }
            return entries;
        }).toCompletableFuture();
        return result;
    }

    @Override
    public List<ZSetEntry> revRange(long start, long end) {
        return revRangeAsync(start, end).join();
    }
}
