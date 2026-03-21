/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.test;

import io.nop.stream.core.operators.InternalTimer;
import io.nop.stream.core.state.PriorityComparable;

/**
 * Mock implementation of InternalTimer for testing.
 *
 * @param <K> The type of key
 * @param <N> The type of namespace
 */
public class MockInternalTimer<K, N> implements InternalTimer<K, N>, PriorityComparable<InternalTimer<?, ?>> {

    private final long timestamp;
    private final K key;
    private final N namespace;

    public MockInternalTimer(long timestamp, K key, N namespace) {
        this.timestamp = timestamp;
        this.key = key;
        this.namespace = namespace;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public N getNamespace() {
        return namespace;
    }

    @Override
    public int comparePriorityTo(InternalTimer<?, ?> other) {
        return Long.compare(this.timestamp, other.getTimestamp());
    }
}
