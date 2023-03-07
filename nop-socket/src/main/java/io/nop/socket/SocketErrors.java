/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.socket;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface SocketErrors {
    String ARG_HOST = "host";
    String ARG_PORT = "port";

    ErrorCode ERR_SOCKET_ACCEPT_FAIL = define("nop.err.socket.accept-fail", "网络连接失败");

    ErrorCode ERR_SOCKET_CONNECT_FAIL = define("nop.err.socket.connect-fail", "网络连接失败");

    ErrorCode ERR_SOCKET_READ_TIMEOUT = define("nop.err.socket.read-timeout", "读取数据超时");

    ErrorCode ERR_SOCKET_WRITE_FAIL = define("nop.err.socket.write-fail", "网络写数据失败");

    ErrorCode ERR_SOCKET_READ_FAIL = define("nop.err.socket.read-fail", "网络读取数据失败");
}
