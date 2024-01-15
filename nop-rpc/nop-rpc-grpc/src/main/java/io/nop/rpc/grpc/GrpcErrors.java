package io.nop.rpc.grpc;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface GrpcErrors {
    String ARG_PROP_ID = "propId";
    String ARG_NAME = "name";
    ErrorCode ERR_GRPC_UNKNOWN_FIELD_FOR_PROP_ID =
            define("nop.err.grpc.unknown-field-for-prop-id", "未知的字段:propId={propId}", ARG_PROP_ID);

    ErrorCode ERR_GRPC_UNKNOWN_FIELD_FOR_NAME =
            define("nop.err.grpc.unknown-field-for-name", "未知的字段:propName={}", ARG_NAME);
}
