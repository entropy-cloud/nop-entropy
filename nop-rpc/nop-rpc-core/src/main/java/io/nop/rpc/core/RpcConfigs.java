/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
}