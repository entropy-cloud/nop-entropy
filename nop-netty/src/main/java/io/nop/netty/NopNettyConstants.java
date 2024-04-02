/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.netty;

public interface NopNettyConstants {
    String HANDLER_SSL = "ssl";

    String HANDLER_LOG = "log";

    String HANDLER_READ_IDLE_TIMEOUT = "readIdleTimeout";

    String HANDLER_WRITE_IDLE_TIMEOUT = "writeIdleTimeout";

    String HANDLER_TEST_FRAGMENT = "testFragment";

    String HANDLER_CLOSE_ON_ERROR = "closeOnError";

    String HANDLER_GLOBAL_TRAFFIC_SHAPING = "globalTrafficShaping";

    String HANDLER_CHANNEL_TRAFFIC_SHAPING = "channelTrafficShaping";

    String HANDLER_CHANNEL_GROUP = "channelGroup";
}
