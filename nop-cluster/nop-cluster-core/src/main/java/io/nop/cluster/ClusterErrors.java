/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface ClusterErrors {
    String ARG_SERVICE_NAME = "serviceName";

    ErrorCode ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE = define("nop.err.cluster.core.no-available-server-instance",
            "服务[{serviceName}]没有可用的实例", ARG_SERVICE_NAME);
}
