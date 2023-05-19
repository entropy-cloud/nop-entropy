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
    String ARG_VERSION = "version";

    ErrorCode ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE = define("nop.err.cluster.core.no-available-server-instance",
            "服务[{serviceName}]没有可用的实例", ARG_SERVICE_NAME);

    ErrorCode ERR_CLUSTER_APP_VERSION_MUST_BE_NPM_LIKE =
            define("nop.err.cluster.app-version-must-be-npm-like",
                    "应用的版本号格式应该符合npm包的标准，采用语义版本号规则，至少包含三个部分major.minor.patch，三个部分都要是数字：{version}",
                    ARG_VERSION);
}
