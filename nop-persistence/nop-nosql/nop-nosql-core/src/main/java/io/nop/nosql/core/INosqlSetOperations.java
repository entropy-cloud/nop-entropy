/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface INosqlSetOperations {
    CompletableFuture<Boolean> addAsync(Object value);

    boolean add(Object value);

    CompletableFuture<Boolean> removeAsync(Object value);

    boolean remove(Object value);

    CompletableFuture<Void> removeAllAsync(Collection<?> values);

    void removeAll(Collection<?> values);

    CompletableFuture<Boolean> containsAsync(Object value);

    boolean contains(Object value);

    CompletableFuture<Boolean> containsAllAsync(Collection<?> values);

    boolean containsAll(Collection<?> values);

    CompletableFuture<Long> sizeAsync();

    long size();

    CompletableFuture<Set<Object>> membersAsync();

    Set<Object> members();

    CompletableFuture<Object> randomMemberAsync();

    Object randomMember();

    CompletableFuture<Object> popAsync();

    Object pop();
}
