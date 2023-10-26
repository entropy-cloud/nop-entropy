/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.password;

public interface IPasswordEncoder {
    String generateSalt();

    /**
     * 数据库中存储的密码为加密形式
     *
     * @param type     密码类型
     * @param password 密码
     * @return 加密后的密码
     */
    String encodePassword(String type, String salt, String password);

    default String encodePassword(String salt, String password) {
        return encodePassword(null, salt, password);
    }

    /**
     * 判断用户输入的密码是否与数据库中存储的加密后的密码匹配
     *
     * @param type            密码类型
     * @param password        用户输入的密码
     * @param encodedPassword 存储在数据库中的加密后的密码
     * @return 返回密码是否与存储的密码相匹配
     */
    boolean passwordMatches(String type, String salt, String password, String encodedPassword);

    default boolean passwordMatches(String salt, String password, String encodedPassword) {
        return passwordMatches(null, salt, password, encodedPassword);
    }
}
