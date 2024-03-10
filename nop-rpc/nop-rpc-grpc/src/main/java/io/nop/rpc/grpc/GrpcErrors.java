/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.grpc;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface GrpcErrors {
    String ARG_PROP_ID = "propId";
    String ARG_NAME = "name";

    String ARG_DATA_TYPE = "dataType";
    ErrorCode ERR_GRPC_UNKNOWN_FIELD_FOR_PROP_ID =
            define("nop.err.grpc.unknown-field-for-prop-id", "未知的字段:propId={propId}", ARG_PROP_ID);

    ErrorCode ERR_GRPC_UNKNOWN_FIELD_FOR_NAME =
            define("nop.err.grpc.unknown-field-for-name", "未知的字段:{name}", ARG_NAME);

    ErrorCode ERR_GRPC_NOT_SUPPORT_DATA_TYPE =
            define("nop.err.grpc.not-support-data-type", "不支持的类型:{}", ARG_DATA_TYPE);

    ErrorCode ERR_GRPC_FIELD_NAME_DUPLICATE =
            define("nop.err.grpc.field-name-duplicate", "字段名重复");

    ErrorCode ERR_GRPC_FIELD_PROP_ID_DUPLICATE =
            define("nop.err.grpc.field-prop-id-duplicate", "字段propId重复");
}
