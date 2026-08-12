/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

/**
 * MFA challenge 状态存储（设计 §3.3）。
 * <p>
 * 生命周期：{@code create}（随机 token + TTL）→ {@code peek}（读取，<b>不刷新 TTL</b>，
 * 避免失败重试把 300s 变成滑动窗口）→ {@code incrFailCount}（原子失败计数，超限由调用方作废）
 * → {@code consume}（成功后一次性原子消费）。<b>先 peek 后 consume</b>：保证失败计数可达 max-attempts。
 * <p>
 * challenge 自身不承担 TOTP 窗口防重放（那是 per-user 的 lastVerifiedWindow）。
 * <ul>
 *   <li>Local 实现：JVM 内 ConcurrentHashMap 原子 compute，无 TTL 竞态。</li>
 *   <li>Redis 实现（nop-auth-service）：写一律 {@code putExAsync}（TTL 在此设定），
 *       读用不刷新 TTL 的 {@code get}（禁用 {@code getExAsync}/GETEX），
 *       原子消费用 {@code removeIfMatch}，原子计数用 {@code INosqlCounter.incrementAsync}(INCRBY)。</li>
 * </ul>
 */
public interface MfaChallengeStore {

    /**
     * 创建 challenge，返回随机 challengeToken（UUID）。
     *
     * @param userId    用户 ID
     * @param mfaType   第二因子类型（totp/sms）
     * @param loginType 触发 MFA 的登录方式（用于 mfaVerify 出口判定）
     * @param tenantId  租户 ID（completeLogin 时包裹 runWithTenant）
     * @param phone     手机号（sms 类型用；可空）
     * @return challengeToken
     */
    String create(String userId, String mfaType, int loginType, String tenantId, String phone);

    /**
     * 读取 challenge（不删除、不刷新 TTL）。过期或不存在返回 null。
     */
    MfaChallenge peek(String challengeToken);

    /**
     * 原子递增失败计数，返回递增后的值（供调用方与 max-attempts 比较）。
     * 计数键独立于 challenge（"mfa:fail:" + token），challenge 作废时计数自然失效。
     */
    int incrFailCount(String challengeToken);

    /**
     * 原子一次性消费 challenge（删除并返回）。不存在/已过期/已被消费返回 null。
     */
    MfaChallenge consume(String challengeToken);
}
