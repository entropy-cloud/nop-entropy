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
public enum LockMode {
    NONE, OPTIMISTIC, PESSIMISTIC_READ, PESSIMISTIC_WRITE,
}
