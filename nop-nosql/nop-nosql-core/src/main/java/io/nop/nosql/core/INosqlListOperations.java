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
import java.util.function.Consumer;

public interface INosqlListOperations {
    CompletableFuture<Long> getSizeAsync();

    CompletableFuture<Void> clearAsync();

    CompletableFuture<Void> addAsync(Object value);

    CompletableFuture<Void> addAllAsync(Collection<?> values);

    CompletableFuture<List<Object>> getRangeAsync(long start, int maxCount);

    CompletableFuture<Boolean> trimAsync(long start, long end);

    CompletableFuture<Object> leftPopAsync();

    CompletableFuture<Object> rightPopAsync();

    CompletableFuture<List<Object>> leftPopMultiAsync(int maxCount);

    CompletableFuture<Void> forEachItemAsync(Consumer<Object> consumer);
}
