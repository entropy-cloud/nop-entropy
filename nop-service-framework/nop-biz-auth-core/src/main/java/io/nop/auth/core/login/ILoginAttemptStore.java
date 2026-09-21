/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.login;

/**
 * 登录失败计数 store（design nop-auth §3.2，plan 2274 Phase 2）：用户/IP 双维度的登录失败
 * 计数，接口级<b>原子递增</b>契约（{@link #incrementLoginFailCount} 返回递增后的新值）——
 * read-modify-write 的原子性由实现内聚（Local 为临界区，Redis 为 INCRBY），
 * 调用方不再自持 JVM 锁（原 {@code LoginServiceImpl.loginFailCountLock} 随之消灭）。
 * <p>
 * 键为维度前缀字符串（{@link #userKey}/{@link #ipKey}），本接口不区分维度——维度前缀
 * 由键承载。装配：{@code nop.auth.login-attempt.store-type}（{@code local|redis}，默认
 * {@code local}），collect-beans 模式（plan 2274 Phase 2），fail-closed。
 */
public interface ILoginAttemptStore {

    /** 用户维度键前缀（与既有 {@code AbstractUserContextCache.userKey} 一致）。 */
    String USER_KEY_PREFIX = "un:";

    /** IP 维度键前缀（与既有 {@code AbstractUserContextCache.ipKey} 一致）。 */
    String IP_KEY_PREFIX = "ip:";

    static String userKey(String userName) {
        return USER_KEY_PREFIX + userName;
    }

    static String ipKey(String ip) {
        return IP_KEY_PREFIX + ip;
    }

    /** 读取计数；无记录返回 0。 */
    int getLoginFailCount(String key);

    /** 写入计数（覆盖）。 */
    void setLoginFailCount(String key, int count);

    /** 清零（移除记录）。 */
    void resetLoginFailCount(String key);

    /**
     * 原子递增并返回递增后的新值。实现必须保证 check-then-act 原子性：并发对同一 key
     * 递增 N 次后，读取值必须恰好为 N（无丢失更新）。
     */
    int incrementLoginFailCount(String key);
}
