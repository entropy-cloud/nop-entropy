/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.verifycode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class VerifyCode {
    private final String code;
    private final String captcha;

    public VerifyCode(@JsonProperty("code") String code, @JsonProperty("captcha") String captcha) {
        this.code = code;
        this.captcha = captcha;
    }

    public String getCode() {
        return code;
    }

    public String getCaptcha() {
        return captcha;
    }
}
