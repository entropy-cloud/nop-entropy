/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.grpc;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface GrpcConfigs {
    SourceLocation S_LOC = SourceLocation.fromClass(GrpcConfigs.class);
    @Description("grpc服务端API包名，缺省为graphql.api")
    IConfigReference<String> CFG_GRAPHQL_API_PACKAGE =
            varRef(S_LOC, "nop.grpc.graphql-api-package", String.class, "graphql.api");

    @Description("是否自动初始化grpc消息字段的propId，缺省为true")
    IConfigReference<Boolean> CFG_GRPC_AUTO_INIT_PROP_ID =
            varRef(S_LOC, "nop.grpc.auto-init-prop-id", Boolean.class, true);
}
