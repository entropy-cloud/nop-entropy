/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.ScoredValue;
import io.nop.api.core.util.FutureHelper;
import io.nop.nosql.core.INosqlZSetOperations;
import io.nop.nosql.core.ZSetEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LettuceZSetOperations extends AbstractLettuceOperations implements INosqlZSetOperations {
    private final String key;

    public LettuceZSetOperations(LettuceRedisConnectionProvider client, String key) {
        super(client);
        this.key = key;
    }

    @Override
    public CompletableFuture<Boolean> addAsync(String member, double score) {
        return async().zadd(key, score, member).thenApply(n -> n > 0).toCompletableFuture();
    }

    @Override
    public boolean add(String member, double score) {
        return FutureHelper.syncGet(addAsync(member, score));
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
        return FutureHelper.syncGet(addAllAsync(entries));
    }

    @Override
    public CompletableFuture<Boolean> removeAsync(String member) {
        return async().zrem(key, member).thenApply(n -> n > 0).toCompletableFuture();
    }

    @Override
    public boolean remove(String member) {
        return FutureHelper.syncGet(removeAsync(member));
    }

    @Override
    public CompletableFuture<Double> scoreAsync(String member) {
        return async().zscore(key, member).toCompletableFuture();
    }

    @Override
    public Double score(String member) {
        return FutureHelper.syncGet(scoreAsync(member));
    }

    @Override
    public CompletableFuture<Long> rankAsync(String member) {
        return async().zrank(key, member).toCompletableFuture();
    }

    @Override
    public Long rank(String member) {
        return FutureHelper.syncGet(rankAsync(member));
    }

    @Override
    public CompletableFuture<Long> revRankAsync(String member) {
        return async().zrevrank(key, member).toCompletableFuture();
    }

    @Override
    public Long revRank(String member) {
        return FutureHelper.syncGet(revRankAsync(member));
    }

    @Override
    public CompletableFuture<Long> cardAsync() {
        return async().zcard(key).toCompletableFuture();
    }

    @Override
    public long card() {
        return FutureHelper.syncGet(cardAsync());
    }

    @Override
    public CompletableFuture<Double> incrementScoreAsync(String member, double delta) {
        return async().zincrby(key, delta, member).toCompletableFuture();
    }

    @Override
    public double incrementScore(String member, double delta) {
        return FutureHelper.syncGet(incrementScoreAsync(member, delta));
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
        return FutureHelper.syncGet(revRangeAsync(start, end));
    }
}
