/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.dao.generator;

import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.commons.util.StringHelper;

public class DefaultUserIdGenerator implements IUserIdGenerator {

    @Override
    public String generateUserId(NopAuthUser user) {
        return StringHelper.generateUUID();
    }

    @Override
    public String generateUserOpenId(NopAuthUser user) {
        return StringHelper.generateUUID();
    }
}