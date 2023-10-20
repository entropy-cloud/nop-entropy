/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.gateway;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface GraphqlGatewayErrors {
    ErrorCode ERR_GATEWAY_NO_RPC_SUPPORT = define("nop.err.gateway.no-rpc-support",
            "没有引入nop-rpc-cluster模块，不支持RPC代理调用");
}
