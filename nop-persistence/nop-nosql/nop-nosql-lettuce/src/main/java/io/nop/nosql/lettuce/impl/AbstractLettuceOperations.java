/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;

public abstract class AbstractLettuceOperations {
    protected final LettuceRedisConnectionProvider client;

    protected AbstractLettuceOperations(LettuceRedisConnectionProvider client) {
        this.client = client;
    }

    protected RedisAsyncCommands<String, Object> async() {
        return client.getConnection().async();
    }

    protected RedisCommands<String, Object> sync() {
        return client.getConnection().sync();
    }
}
