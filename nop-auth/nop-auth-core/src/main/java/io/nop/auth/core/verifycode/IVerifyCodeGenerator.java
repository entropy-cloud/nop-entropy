/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.verifycode;

/**
 * 验证码生成器
 */
public interface IVerifyCodeGenerator {
    VerifyCode generateCode(String secret);

    boolean checkValid(String cachedCode, String inputCode, String secret);
}