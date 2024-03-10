/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface RpcModelErrors {
    String ARG_VERSION = "version";
    String ARG_PATH = "path";

    String ARG_DATA_TYPE = "dataType";

    ErrorCode ERR_RPC_UNSUPPORTED_PROTO_VERSION =
            define("nop.err.rpc.unsupported-proto-version", "不支持的协议版本: {version}", ARG_VERSION);

    ErrorCode ERR_RPC_INVALID_IMPORT_PATH =
            define("nop.err.rpc.invalid-import-path", "无效的导入路径: {path}", ARG_PATH);

    ErrorCode ERR_PROTO_NOT_SUPPORT_DATA_TYPE =
            define("nop.err.proto.not-support-data-type", "不支持的类型:{}", ARG_DATA_TYPE);

}
