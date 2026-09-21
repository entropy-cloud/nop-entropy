/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.login;

/**
 * {@link ILoginAttemptStore} 缺省实例持有者（design nop-auth §3.2"缺省实例同一性"裁定，
 * plan 2274 Phase 2）。
 * <p>
 * 容器路径：{@code nopUserContextCache} 与 {@code LoginServiceImpl} 注入同一
 * {@code ILoginAttemptStore} bean，天然同一实例。缺省（手工 wiring）路径：消费方经
 * {@link #sharedLocalDefault()} 解析到同一 JVM 级共享缺省实例——避免"写进 A 读到 B"。
 * 该共享单例仅兜底（无 wiring 的直接 {@code new} 场景）；经
 * {@code AbstractUserContextCache} 的正常路径优先使用缓存自身持有的 per-instance store
 * （测试隔离性与 TTL 语义与原 {@code loginFailCache} 一致）。
 */
public final class LoginAttemptStores {

    private static volatile ILoginAttemptStore sharedDefault;

    private LoginAttemptStores() {
    }

    public static ILoginAttemptStore sharedLocalDefault() {
        ILoginAttemptStore store = sharedDefault;
        if (store == null) {
            synchronized (LoginAttemptStores.class) {
                store = sharedDefault;
                if (store == null) {
                    store = new LocalLoginAttemptStore();
                    sharedDefault = store;
                }
            }
        }
        return store;
    }
}
