/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

/**
 * 短信验证码存储（设计 §3.3），独立于图形验证码缓存（{@code IUserContextCache}）。
 * <p>
 * key 隔离：调用方传入完整逻辑 key——登录 {@code "login:" + phone}、MFA {@code "mfa:" + userId}——
 * 互不通用（同一验证码不能同时被登录与 MFA 入口消费）。
 * <p>
 * 三态 {@link CodeVerifyResult}：VALID（匹配+未过期，成功原子消费）、EXPIRED（不存在/已过期）、
 * MISMATCH（存在+未过期但不匹配，内部失败计数递增，达 max-attempts 作废）。
 * <ul>
 *   <li>Local 实现：ConcurrentHashMap 原子 compute。</li>
 *   <li>Redis 实现：写 {@code putExAsync}，读不刷新 TTL 的 {@code get}，成功消费 {@code removeIfMatch}。</li>
 * </ul>
 */
public interface SmsCodeStore {

    /**
     * 生成 6 位随机验证码并以 key 存储（TTL 由配置），返回明文验证码。
     * 实际短信发送由调用方经 {@code ISmsSender} 完成（store 只负责生成与存储）。
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
