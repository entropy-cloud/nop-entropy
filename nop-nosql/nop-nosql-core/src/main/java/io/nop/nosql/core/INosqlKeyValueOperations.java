/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core;

import io.nop.commons.collections.IAsyncMap;

import java.util.concurrent.CompletionStage;

public interface INosqlKeyValueOperations extends IAsyncMap<String, Object> {
    CompletionStage<Long> getSizeAsync();

    long getSize();

    CompletionStage<Void> putExAsync(String key, Object value, long timeout);

    CompletionStage<Object> getExAsync(String key, long timeout);

    CompletionStage<Boolean> putIfAbsentExAsync(String key, Object value, long timeout);

    CompletionStage<String> putIfAbsentOrMatchExAsync(String key, String value, long timeout);

    CompletionStage<Object> getAndSetExAsync(String key, Object value, long timeout);

    CompletionStage<Long> getTimeoutAsync(String key);

    CompletionStage<Boolean> setTimeoutAsync(String key, long timeout);
}
