/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

/**
 * 邮件验证码存储（设计 §5.3.3，W15-impl）——与 {@link SmsCodeStore} 同形的平行接口
 * （不泛化改名，设计 §5.4 拒绝项），key 隔离纪律同 sms：调用方传入完整逻辑 key——
 * MFA {@code "mfa-email:" + userId}、登记通道 proof {@code "proof-email:" + userId}——
 * 与 sms 侧 key（{@code mfa:}/{@code proof:}）互不通用（同一验证码不能跨通道消费）。
 * <p>
 * 三态 {@link CodeVerifyResult}：VALID（匹配+未过期，成功原子消费）、EXPIRED（不存在/已过期）、
 * MISMATCH（存在+未过期但不匹配，内部失败计数递增，达 max-attempts 作废）。
 * <ul>
 *   <li>Local 实现：ConcurrentHashMap 原子 compute（core 内）。</li>
 *   <li>Db 实现：nop_auth_email_code 表（结构对齐 nop_auth_sms_code）。</li>
 *   <li>Redis 实现：写 {@code putExAsync}，读不刷新 TTL 的 {@code get}，成功消费 {@code removeIfMatch}。</li>
 * </ul>
 * migration note：跨模块公共 API 新增（nop-biz-auth-core），纯新增接口，无破坏性变更。
 */
public interface EmailCodeStore {

    /**
     * 生成 6 位随机验证码并以 key 存储（TTL 由配置），返回明文验证码。
     * 实际邮件发送由调用方经 {@code IEmailSender} 完成（store 只负责生成与存储，同一期 sms 模式）。
     */
    String send(String key);

    /**
     * 校验验证码。VALID 时原子消费；MISMATCH 时内部失败计数递增（达 max-attempts 作废）。
     */
    CodeVerifyResult verify(String key, String code);

    /**
     * 显式删除 key（备用，如重发时作废旧码）。
     */
    void consume(String key);
}
