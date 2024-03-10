/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.GetExArgs;
import io.lettuce.core.KeyValue;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.functional.Functionals;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.nosql.core.INosqlHashOperations;
import io.nop.nosql.core.INosqlListOperations;
import io.nop.nosql.core.INosqlService;
import io.nop.nosql.core.INosqlSetOperations;
import io.nop.nosql.core.INosqlZSetOperations;
import io.nop.nosql.core.script.RedisScripts;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class LettuceMessageService implements INosqlService {
    private final LettuceRedisConnectionProvider client;

    public LettuceMessageService(LettuceRedisConnectionProvider client) {
        this.client = client;
    }

    protected RedisAdvancedClusterAsyncCommands<String, Object> async() {
        return client.getConnection().async();
    }

    protected RedisAdvancedClusterCommands<String, Object> sync() {
        return client.getConnection().sync();
    }

    @Override
    public long getSize() {
        return sync().dbsize();
    }

    @Override
    public Object get(String key) {
        return sync().get(key);
    }

    @Override
    public Object computeIfAbsent(String key, Function<? super String, ?> mappingFunction) {
        RedisAdvancedClusterCommands<String, Object> commands = sync();
        Object value = commands.get(key);
        if (value == null) {
            value = mappingFunction.apply(key);
            commands.set(key, value);
        }
        return value;
    }

    @Override
    public Map<String, Object> getAll(Collection<? extends String> keys) {
        List<KeyValue<String, Object>> list = sync().mget(keys.toArray(new String[keys.size()]));
        Map<String, Object> ret = CollectionHelper.newHashMap(list.size());
        for (KeyValue<String, Object> kv : list) {
            ret.put(kv.getKey(), kv.getValue());
        }
        return ret;
    }

    @Override
    public boolean containsKey(String key) {
        return sync().get(key) != null;
    }

    @Override
    public void put(String key, Object value) {
        sync().set(key, value);
    }

    @Override
    public void putAll(Map<? extends String, ?> map) {
        if (map == null || map.isEmpty())
            return;

        sync().mset((Map<String, Object>) map);
    }

    @Override
    public boolean putIfAbsent(String key, Object value) {
        return false;
    }

    @Override
    public Object getAndSet(String key, Object value) {
        return null;
    }

    @Override
    public void remove(String key) {
        sync().del(key);
    }

    @Override
    public boolean removeIfMatch(String key, Object object) {
        return false;
    }

    @Override
    public void removeAll(Collection<? extends String> keys) {
        sync().del(keys.toArray(new String[keys.size()]));
    }

    @Override
    public void clear() {
        sync().flushdb();
    }

    @Override
    public void forEachEntry(BiConsumer<? super String, ? super Object> consumer) {

    }

    @Override
    public CompletionStage<Object> getAsync(String key) {
        return async().get(key);
    }

    @Override
    public CompletionStage<Object> computeIfAbsentAsync(String key, Function<? super String, ?> mappingFunction) {
        return async().get(key).thenCompose(value -> {
            if (value == null) {
                value = mappingFunction.apply(key);
                Object ret = value;
                return async().set(key, value).thenApply(v -> ret);
            } else {
                return FutureHelper.success(value);
            }
        });
    }

    @Override
    public CompletionStage<Map<String, Object>> getAllAsync(Collection<? extends String> keys) {
        return async().mget(StringHelper.toStringArray(keys)).thenApply(LettuceHelper::toMap);
    }

    @Override
    public CompletionStage<Boolean> containsKeyAsync(String key) {
        return async().exists(key).thenApply(value -> value == 1);
    }

    @Override
    public CompletionStage<Void> putAsync(String key, Object value) {
        return async().set(key, value).thenApply(Functionals.toVoid());
    }

    @Override
    public CompletionStage<Void> putAllAsync(Map<? extends String, ?> map) {
        return async().mset((Map) map);
    }

    @Override
    public CompletionStage<Boolean> putIfAbsentAsync(String key, Object value) {
        return async().setnx(key, value);
    }

    @Override
    public CompletionStage<Object> getAndSetAsync(String key, Object value) {
        return async().getset(key, value);
    }

    @Override
    public CompletionStage<Void> removeAsync(String key) {
        return async().del(key).thenApply(Functionals.toVoid());
    }

    @Override
    public CompletionStage<Boolean> removeIfMatchAsync(String key, Object object) {
        return LettuceExecutor.evalScript(async(), RedisScripts.REMOVE_IF_MATCH, ScriptOutputType.BOOLEAN,
                new String[]{key}, new Object[]{object});
    }

    @Override
    public CompletionStage<Void> removeAllAsync(Collection<? extends String> keys) {
        return async().del(StringHelper.toStringArray(keys)).thenApply(Functionals.toVoid());
    }

    @Override
    public CompletionStage<Void> clearAsync() {
        return async().flushdb().thenApply(Functionals.toVoid());
    }

    @Override
    public CompletionStage<Void> forEachEntryAsync(BiConsumer<? super String, ? super Object> consumer) {
        return null;
    }

    @Override
    public CompletionStage<Long> getSizeAsync() {
        return async().dbsize();
    }

    @Override
    public CompletionStage<Void> putExAsync(String key, Object value, long timeout) {
        return async().psetex(key, timeout, value).thenApply(Functionals.toVoid());
    }

    @Override
    public CompletionStage<Object> getExAsync(String key, long timeout) {
        if (timeout < 0)
            return getAsync(key);

        GetExArgs args = new GetExArgs();
        args.px(timeout);
        return async().getex(key, args);
    }

    @Override
    public CompletionStage<Boolean> putIfAbsentExAsync(String key, Object value, long timeout) {
        if (timeout < 0)
            return putIfAbsentAsync(key, value);

        SetArgs args = new SetArgs();
        args.px(timeout);
        args.nx();
        return async().set(key, value, args).thenApply(s -> "OK".equals(s));
    }

    @Override
    public CompletionStage<String> putIfAbsentOrMatchExAsync(String key, String value, long timeout) {
        return null;
    }

    @Override
    public CompletionStage<Object> getAndSetExAsync(String key, Object value, long timeout) {
        if (timeout < 0)
            return getAndSetAsync(key, value);

        SetArgs args = new SetArgs();
        args.px(timeout);
        return async().setGet(key, value, args);
    }

    @Override
    public CompletionStage<Long> getTimeoutAsync(String key) {
        return async().pttl(key);
    }

    @Override
    public CompletionStage<Boolean> setTimeoutAsync(String key, long timeout) {
        return async().pexpire(key, timeout);
    }

    @Override
    public INosqlHashOperations hashOps(String key) {
        return null;
    }

    @Override
    public INosqlListOperations listOps(String key) {
        return null;
    }

    @Override
    public INosqlSetOperations setOps(String key) {
        return null;
    }

    @Override
    public INosqlZSetOperations zSetOps(String key) {
        return null;
    }

    @Override
    public IMessageService getMessageService() {
        return null;
    }
}
