/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.password;

import io.nop.commons.util.StringHelper;

public class SHA256PasswordEncoder implements IPasswordEncoder {

    @Override
    public String generateSalt() {
        return StringHelper.generateUUID();
    }

    @Override
    public String encodePassword(String type, String salt, String password) {
        return StringHelper.sha256Hash(password, salt);
    }

    @Override
    public boolean passwordMatches(String type, String salt, String password, String encodedPassword) {
        return StringHelper.sha256Hash(password, salt).equals(encodedPassword);
    }
}
