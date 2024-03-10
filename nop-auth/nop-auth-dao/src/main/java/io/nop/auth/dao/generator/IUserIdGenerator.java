/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.dao.generator;

import io.nop.auth.dao.entity.NopAuthUser;

public interface IUserIdGenerator {
    String generateUserId(NopAuthUser user);

    String generateUserOpenId(NopAuthUser user);
}
