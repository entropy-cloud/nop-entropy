/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisScriptingAsyncCommands;
import io.nop.commons.util.objects.DigestedText;

public class LettuceExecutor {
    public static <T> RedisFuture<T> evalScript(RedisScriptingAsyncCommands<String, Object> async, DigestedText script,
                                                ScriptOutputType outputType, String[] keys, Object[] values) {
        String digest = script.getDigestString();

        return async.evalsha(digest, outputType, keys, values);
    }
}
