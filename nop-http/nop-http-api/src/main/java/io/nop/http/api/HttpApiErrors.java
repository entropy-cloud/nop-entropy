/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api;

import io.nop.api.core.exceptions.ErrorCode;

public interface HttpApiErrors {
    String ARG_HEADER_NAME = "headerName";

    ErrorCode ERR_HTTP_INIT_SSL_FAIL = ErrorCode.define("nop.err.http.init-ssl-fail", "初始化SSL失败");

    ErrorCode ERR_HTTP_RESPONSE_TEXT_NOT_JSON = ErrorCode.define("nop.err.http.response-text-not-json",
            "返回的内容不是JSON格式");

    ErrorCode ERR_HTTP_RESPONSE_FORMAT_NOT_EXPECTED = ErrorCode.define("nop.err.http.response-format-not-expected",
            "返回的内容格式不符合预期");

    ErrorCode ERR_HTTP_CONNECT_FAIL = ErrorCode.define("nop.err.http.connect-fail",
            "无法建立http连接");
}
