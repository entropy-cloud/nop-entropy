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

    long getSize();

    CompletableFuture<Void> clearAsync();

    void clear();

    CompletableFuture<Void> addAsync(Object value);

    void add(Object value);

    CompletableFuture<Void> addAllAsync(Collection<?> values);

    void addAll(Collection<?> values);

    CompletableFuture<List<Object>> getRangeAsync(long start, int maxCount);

    List<Object> getRange(long start, int maxCount);

    CompletableFuture<Boolean> trimAsync(long start, long end);

    boolean trim(long start, long end);

    CompletableFuture<Object> leftPopAsync();

    Object leftPop();

    CompletableFuture<Object> rightPopAsync();

    Object rightPop();

    CompletableFuture<List<Object>> leftPopMultiAsync(int maxCount);

    List<Object> leftPopMulti(int maxCount);

    CompletableFuture<Void> forEachItemAsync(Consumer<Object> consumer);

    void forEachItem(Consumer<Object> consumer);
}
