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
    SourceLocation S_LOC = SourceLocation.fromClass(RpcConfigs.class);

    @Description("RPC客户端调用pollingMethod的时间间隔")
    IConfigReference<Duration> CFG_RPC_CLIENT_EXT_DEFAULT_POLL_INTERVAL =
            varRef(S_LOC, "nop.rpc.client-ext.default-poll-interval", Duration.class, Duration.ofMillis(1000));

    @Description("RPC客户端调用pollingMethod的最大错误次数，超过失败次数则认为返回失败")
    IConfigReference<Integer> CFG_RPC_CLIENT_EXT_MAX_POLL_ERROR_COUNT =
            varRef(S_LOC, "nop.rpc.client-ext.max-poll-error-count", Integer.class, 5);
}