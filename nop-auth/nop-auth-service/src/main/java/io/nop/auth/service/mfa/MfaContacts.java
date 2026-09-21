/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.commons.util.StringHelper;

/**
 * 联系方式脱敏共享工具（plan 2274 Phase 1 落点，Phase 3/4 迁移 LoginServiceImpl /
 * NopAuthUserBizModel 的私有副本至此）。手机号仅显示后 4 位；邮箱保留本地部分前 2 位 + 域名。
 * PII 不外露裁定：限流/审计/日志中的联系方式一律经此类脱敏。
 */
public final class MfaContacts {

    private MfaContacts() {
    }

    /** 手机号脱敏：仅显示后 4 位。 */
    public static String maskPhone(String phone) {
        if (StringHelper.isEmpty(phone) || phone.length() <= 4) {
            return phone;
        }
        return StringHelper.repeat("*", phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    /** 邮箱脱敏：保留本地部分前 2 位 + 域名。 */
    public static String maskEmail(String email) {
        if (StringHelper.isEmpty(email)) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String local = email.substring(0, at);
        String prefix = local.substring(0, Math.min(2, local.length()));
        return prefix + "***" + email.substring(at);
    }
}
