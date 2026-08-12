/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

/**
 * SmsCodeStore.verify 的三态结果（设计 §3.8）。
 * <ul>
 *   <li>{@link #VALID} — 验证码存在、未过期且匹配（成功时已原子消费）。</li>
 *   <li>{@link #EXPIRED} — 验证码不存在（Redis TTL 已过期/Local 已过期）或已过 expireAt。</li>
 *   <li>{@link #MISMATCH} — 验证码存在、未过期但与输入不匹配（失败计数递增）。</li>
 * </ul>
 */
public enum CodeVerifyResult {
    VALID,
    EXPIRED,
    MISMATCH
}
