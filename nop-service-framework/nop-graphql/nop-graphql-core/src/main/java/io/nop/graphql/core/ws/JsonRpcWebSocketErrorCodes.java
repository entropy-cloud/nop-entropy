/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ws;

/**
 * JSON-RPC 2.0 WebSocket error codes.
 */
public interface JsonRpcWebSocketErrorCodes {
    int PARSE_ERROR = -32700;
    int INVALID_REQUEST = -32600;
    int METHOD_NOT_FOUND = -32601;
    int INVALID_PARAMS = -32602;
    int INTERNAL_ERROR = -32603;

    int TOO_MANY_SUBSCRIPTIONS = -32502;
    int FORBIDDEN = -32503;
    int SUBSCRIPTION_EXISTS = -32504;
}
