package io.nop.graphql.core.jsonrpc;

// 标准错误码常量类
public interface JsonRpcErrorCodes {
    int PARSE_ERROR = -32700;
    int INVALID_REQUEST = -32600;
    int METHOD_NOT_FOUND = -32601;
    int INVALID_PARAMS = -32602;
    int INTERNAL_ERROR = -32603;
}
