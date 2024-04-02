/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.netty;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopNettyErrors {
    ErrorCode ERR_CHANNEL_NOT_ACTIVE = define("nop.err.netty.channel-not-active",
            "尚未建立连接");

    ErrorCode ERR_TCP_CONNECT_FAIL = define("nop.err.netty.tcp-connect-fail",
            "Tcp连接失败");

    ErrorCode ERR_TOO_MANY_REQUEST_IN_FLIGHT = define("nop.err.netty.too-many-request-in-flight",
            "正在进行的请求数过多，无法接受更多请求");
}
