/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;

import static io.nop.api.core.config.AppConfig.varRef;

public interface RpcConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(RpcConfigs.class);

    @Description("RPC客户端调用pollingMethod的时间间隔")
    IConfigReference<Duration> CFG_RPC_CLIENT_EXT_DEFAULT_POLL_INTERVAL =
            varRef(s_loc, "nop.rpc.client-ext.default-poll-interval", Duration.class, Duration.ofMillis(1000));

    @Description("RPC客户端调用pollingMethod的最大错误次数，超过失败次数则认为返回失败")
    IConfigReference<Integer> CFG_RPC_CLIENT_EXT_MAX_POLL_ERROR_COUNT =
            varRef(s_loc, "nop.rpc.client-ext.max-poll-error-count", Integer.class, 5);

    /**
     * 4.2-C4-003（plan 2498 Phase 6）：轮询链的整体截止时间。缺省 ZERO = 不启用（可选 deadline，
     * 升级零行为变化）；配置正值后，被放弃/远端挂死的 polling 请求在 deadline 处强制终止——
     * 不再形成不可回收的常驻轮询链。请求属性 pollTimeout 可按请求覆盖本键。
     */
    @Description("RPC客户端轮询链的整体截止时间，0表示不启用")
    IConfigReference<Duration> CFG_RPC_CLIENT_EXT_DEFAULT_POLL_TIMEOUT =
            varRef(s_loc, "nop.rpc.client-ext.default-poll-timeout", Duration.class, Duration.ZERO);
}