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

    String ARG_HTTP_STATUS = "httpStatus";

    String ARG_BODY = "body";

    String ARG_EXCEPTION = "exception";

    /**
     * 非成功响应的响应头 Map（含 Retry-After 等）。挂到非 2xx 抛出的异常上，
     * 供上层（如 LLM 错误规范化）读取 Retry-After 做配额感知重试。
     */
    String ARG_RESPONSE_HEADERS = "responseHeaders";

    /**
     * 通用超时预算参数（毫秒）。4.3-C6-004 面① SSE demand 停滞终态使用。
     */
    String ARG_TIMEOUT = "timeout";

    ErrorCode ERR_HTTP_INIT_SSL_FAIL = ErrorCode.define("nop.err.http.init-ssl-fail", "初始化SSL失败");

    ErrorCode ERR_HTTP_RESPONSE_TEXT_NOT_JSON = ErrorCode.define("nop.err.http.response-text-not-json",
            "返回的内容不是JSON格式");

    ErrorCode ERR_HTTP_RESPONSE_FORMAT_NOT_EXPECTED = ErrorCode.define("nop.err.http.response-format-not-expected",
            "返回的内容格式不符合预期");

    ErrorCode ERR_HTTP_CONNECT_FAIL = ErrorCode.define("nop.err.http.connect-fail",
            "无法建立http连接");

    ErrorCode ERR_HTTP_RESPONSE_ERROR =
            ErrorCode.define("nop.err.http.response-error", "http响应错误:httpStatus={httpStatus}");

    ErrorCode ERR_HTTP_TIMEOUT =
            ErrorCode.define("nop.err.http.timeout","http请求超时");

    /**
     * 4.3-C6-004 面①（plan 2505，设计权威来源
     * ai-dev/design/concurrency/sse-backpressure-and-body-phase-lifecycle.md §3.1）：
     * SSE 订阅者 demand 等待超过停滞预算——订阅以错误终态化（cleanup + onError），
     * 不再无限期挂起承载线程（R21 纪律：异常终态非静默）。
     */
    ErrorCode ERR_HTTP_SSE_DEMAND_TIMEOUT =
            ErrorCode.define("nop.err.http.sse-demand-timeout",
                    "SSE subscriber demand wait exceeded the stall budget (timeout={timeout} ms); "
                            + "subscription terminated");

    String ARG_EXPECTED = "expected";
    String ARG_ACTUAL = "actual";
    String ARG_ALGORITHM = "algorithm";

    ErrorCode ERR_HTTP_DOWNLOAD_CHECKSUM_MISMATCH =
            ErrorCode.define("nop.err.http.download-checksum-mismatch",
                    "下载内容校验失败:algorithm={algorithm}");

    ErrorCode ERR_HTTP_DOWNLOAD_NO_CHECKSUM =
            ErrorCode.define("nop.err.http.download-no-checksum",
                    "要求校验下载内容但没有可用的校验来源");

    ErrorCode ERR_HTTP_UPLOAD_INPUT_FILE =
            ErrorCode.define("nop.err.http.upload-input-file", "读取上传文件失败");
}
