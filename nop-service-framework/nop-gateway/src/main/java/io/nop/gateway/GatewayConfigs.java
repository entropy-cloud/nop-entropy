/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface GatewayConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(GatewayConfigs.class);

    @Description("网关模型文件对应的虚拟文件路径")
    IConfigReference<String> CFG_GATEWAY_MODEL_PATH =
            varRef(s_loc, "nop.gateway.model-path", String.class, "/nop/main/app.gateway.xml");
}
