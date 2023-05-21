package io.nop.rpc.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;

import java.time.Duration;

import static io.nop.api.core.config.AppConfig.varRef;

public interface RpcConfigs {
    @Description("RPC客户端调用pollingMethod的时间间隔")
    IConfigReference<Duration> CFG_RPC_CLIENT_EXT_DEFAULT_POLL_INTERVAL =
            varRef("nop.rpc.client-ext.default-poll-interval", Duration.class, Duration.ofMillis(1000));
}