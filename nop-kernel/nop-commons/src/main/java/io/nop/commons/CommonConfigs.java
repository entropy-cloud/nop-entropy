/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface CommonConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(CommonConfigs.class);

    @Description("全局定时器线程池的固定线程数（ScheduledThreadPoolExecutor 的容量只由 corePoolSize 决定）")
    IConfigReference<Integer> CFG_CONCURRENT_GLOBAL_TIMER_CORE_POOL_SIZE = varRef(s_loc,
            "nop.commons.concurrent.global-timer.corePoolSize", Integer.class, 4);

    @Deprecated
    @Description("已废弃：globalTimer 容量请改用 nop.commons.concurrent.global-timer.corePoolSize。"
            + "仅当本键被显式配置且新键未配置时回读其值并记录 WARN，其余情况本键被忽略")
    IConfigReference<Integer> CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE = varRef(
            s_loc, "nop.commons.concurrent.global-timer.maxPoolSize", Integer.class, 5);

    @Description("cached 形态线程池（maxPoolSize<0 的显式 cached 标记，含 nop-cached-thread-pool）的最大线程数上界，构造期读取、热更不生效")
    IConfigReference<Integer> CFG_CONCURRENT_CACHED_POOL_MAX_POOL_SIZE = varRef(s_loc,
            "nop.commons.concurrent.cached-pool.maxPoolSize", Integer.class, 200);

    @Description("全局工作线程池的大小")
    IConfigReference<Integer> CFG_CONCURRENT_GLOBAL_WORKER_MAX_POOL_SIZE = varRef(s_loc,
            "nop.commons.concurrent.global-worker.maxPoolSize", Integer.class, 30);

    @Description("限制StringHelper.repeat函数的参数值小于指定值，不会过大")
    IConfigReference<Integer> CFG_UTILS_STRING_MAX_REPEAT_LEN = varRef(s_loc, "nop.commons.util.string-max-repeat-len",
            Integer.class, 2000);

    @Description("限制StringHelper.pad函数的参数值小于指定值，不会过大")
    IConfigReference<Integer> CFG_UTILS_STRING_MAX_PAD_LEN = varRef(s_loc, "nop.commons.util.string-max-pad-len",
            Integer.class, 2000);

    IConfigReference<Integer> CFG_IO_DEFAULT_BUF_SIZE = varRef(s_loc, "nop.commons.io.default-buf-size", Integer.class, 8192);

    IConfigReference<Integer> CFG_CONTINUATION_MAX_QUEUE_SIZE = varRef(s_loc,
            "nop.commons.concurrent.continuation-max-queue-size", Integer.class, 1000);

    @Description("设置查找本机ip时，忽略属于指定网络的ip。这里配置的匹配模式为简单匹配，两侧的*表示模糊匹配，例如192.168.*")
    IConfigReference<String> CFG_NET_IGNORE_INTERFACE_PATTERN = varRef(s_loc, "nop.commons.net.ignored-interface-pattern",
            String.class, null);

    @Description("查找本机ip时是否只考虑local address，而忽略所有外网ip")
    IConfigReference<Boolean> CFG_NET_USE_ONLY_SITE_LOCAL_INTERFACES = varRef(s_loc,
            "nop.commons.net.use-only-site-local-interfaces", Boolean.class, false);

    @Description("设置查找本机ip时，只识别属于指定网络的ip。这里配置的匹配模式为简单匹配，两侧的*表示模糊匹配，例如192.168.*")
    IConfigReference<String> CFG_NET_PREFER_NETWORK_PATTERN = varRef(s_loc, "nop.commons.net.preferred-network-pattern",
            String.class, null);

    @Description("正则表达式编译缓存的大小")
    IConfigReference<Integer> CFG_REGEX_COMPILE_CACHE_SIZE = varRef(s_loc, "nop.regex.compile-cache-size", Integer.class, 500);

    @Description("内置加密算法的随机salt")
    IConfigReference<String> CFG_CRYPT_DEFAULT_SALT = varRef(s_loc, "nop.crypt.default-salt", String.class, "");

    @Description("内置加密算法的缺省输入向量")
    IConfigReference<String> CFG_CRYPT_DEFAULT_IV = varRef(s_loc, "nop.crypt.default-iv", String.class, "");

    @Description("内置加密算法的加密密钥")
    IConfigReference<String> CFG_CRYPT_DEFAULT_ENC_KEY = varRef(s_loc, "nop.crypt.default-enc-key", String.class, "");

    @Description("是否启用虚拟线程池支持")
    IConfigReference<Boolean> CFG_ENABLE_VIRTUAL_THREAD_POOL = varRef(s_loc, "nop.commons.enable-virtual-thread-pool", Boolean.class, true);

    @Description("退役代际机制（plan 2504 裁定①③）的宽限期：RoundRobinSupplier收缩/VFS zip刷新/缓存失效销毁"
            + "等旧代资源自退役登记起至少存活该时长，宽限到期后由共享退役队列排空关闭。"
            + "≤0为关闭档：退化为同步立即关闭（修复前语义，显式回退通道）")
    IConfigReference<java.time.Duration> CFG_CONCURRENT_RETIREMENT_GRACE_PERIOD = varRef(s_loc,
            "nop.commons.concurrent.retirement.grace-period", java.time.Duration.class, java.time.Duration.ofSeconds(5));
}