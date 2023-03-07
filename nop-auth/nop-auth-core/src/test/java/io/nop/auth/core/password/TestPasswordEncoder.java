/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.password;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPasswordEncoder {
    @Test
    public void testBCrypt() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String salt = encoder.generateSalt();
        String encodedPassword = encoder.encodePassword(salt, "123");
        System.out.println(encodedPassword);
        assertTrue(encoder.passwordMatches(salt, "123", encodedPassword));
    }

    @Test
    public void testComposite() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        SHA256PasswordEncoder sha256 = new SHA256PasswordEncoder();
        CompositePasswordEncoder encoder = new CompositePasswordEncoder();
        encoder.setFirstEncoder(sha256);
        encoder.setSecondEncoder(bcrypt);

        String salt = encoder.generateSalt();
        // $2a$10$s/qisjNS9c99dsKCrntyceUd1QdyxMJxo1gTjJyZuRFdZsfMKo82K
        String encodedPassword = encoder.encodePassword(salt, "123");
        System.out.println(encodedPassword);
        assertTrue(encoder.passwordMatches(salt, "123", encodedPassword));
    }
}
