/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

/**
 * Default {@link TtlTimeProvider} backed by {@link System#currentTimeMillis()}.
 */
public final class SystemTtlTimeProvider implements TtlTimeProvider {

    public static final SystemTtlTimeProvider INSTANCE = new SystemTtlTimeProvider();

    private SystemTtlTimeProvider() {
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
