package io.nop.graphql.gateway;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface GraphqlGatewayErrors {
    ErrorCode ERR_GATEWAY_NO_RPC_SUPPORT = define("nop.err.gateway.no-rpc-support",
            "没有引入nop-rpc-cluster模块，不支持RPC代理调用");
}
