/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisScriptingAsyncCommands;
import io.nop.commons.util.objects.DigestedText;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class LettuceExecutor {
    public static <T> CompletionStage<T> evalScript(RedisScriptingAsyncCommands<String, Object> async, DigestedText script,
                                                    ScriptOutputType outputType, String[] keys, Object[] values) {
        String digest = script.getDigestString();

        return async.<T>evalsha(digest, outputType, keys, values)
                .handle((result, ex) -> {
                    if (ex != null) {
                        if (isNoScriptException(ex)) {
                            return async.<T>eval(script.getText(), outputType, keys, values);
                        }
                        if (ex instanceof RuntimeException)
                            throw (RuntimeException) ex;
                        throw new CompletionException(ex);
                    }
                    @SuppressWarnings("unchecked")
                    CompletionStage<T> completed = CompletableFuture.completedFuture(result);
                    return completed;
                })
                .thenCompose(Function.identity());
    }

    private static boolean isNoScriptException(Throwable ex) {
        if (ex instanceof RedisNoScriptException)
            return true;
        return ex instanceof CompletionException && ex.getCause() instanceof RedisNoScriptException;
    }
}
