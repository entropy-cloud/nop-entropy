/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.login;

import io.nop.commons.util.StringHelper;

public class RandomSessionIdGenerator implements ISessionIdGenerator {
    public static final RandomSessionIdGenerator INSTANCE = new RandomSessionIdGenerator();

    @Override
    public String generateId() {
        return StringHelper.generateUUID();
    }
}
