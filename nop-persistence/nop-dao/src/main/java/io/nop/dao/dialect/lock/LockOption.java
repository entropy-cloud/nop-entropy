/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.lock;

/**
 * @author canonical_entropy@163.com
 */
public class LockOption {
    public static final LockOption NONE = new LockOption(LockMode.NONE, -1);
    public static final LockOption OPTIMISTIC = new LockOption(LockMode.OPTIMISTIC, -1);
    public static final LockOption PESSIMISTIC_READ = new LockOption(LockMode.PESSIMISTIC_READ, -1);
    public static final LockOption PESSIMISTIC_WRITE = new LockOption(LockMode.PESSIMISTIC_WRITE, -1);

    private final LockMode lockMode;

    private final int timeout;

    public LockOption(LockMode lockMode, int timeout) {
        this.lockMode = lockMode;
        this.timeout = timeout;
    }

    public LockMode getLockMode() {
        return lockMode;
    }

    public int getTimeout() {
        return timeout;
    }
}
