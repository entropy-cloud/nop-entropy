/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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

    ErrorCode ERR_SOCKET_START_SERVER_FAIL = define("nop.err.socket.start-server-fail", "启动服务端失败");

    ErrorCode ERR_SOCKET_CONNECT_FAIL = define("nop.err.socket.connect-fail", "网络连接失败");

    ErrorCode ERR_SOCKET_READ_TIMEOUT = define("nop.err.socket.read-timeout", "读取数据超时");

    ErrorCode ERR_SOCKET_WRITE_FAIL = define("nop.err.socket.write-fail", "网络写数据失败");

    ErrorCode ERR_SOCKET_READ_FAIL = define("nop.err.socket.read-fail", "网络读取数据失败");

    // 4.3-C1-016（plan 2561 裁定 B）：单读线程契约违约（call 与 recv-loop 并发读同一连接）
    ErrorCode ERR_SOCKET_CONCURRENT_READ = define("nop.err.socket.concurrent-read", "网络连接正被其他线程读取");

    // 4.3-C3-026（plan 2561 裁定 H）：连接尚未建立时 send 的 typed 异常
    ErrorCode ERR_SOCKET_NOT_CONNECTED = define("nop.err.socket.not-connected", "网络连接尚未建立");
}
