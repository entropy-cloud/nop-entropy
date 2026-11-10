/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.rpc;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * 2.5-MA-11（plan 2554 裁定 D）：RPC 实体驱动批请求规模上界配置。
 * batchExecuteAsync/batchLoadAsync 整批单请求会随批规模线性放大单个 RPC 报文，
 * 按 {@code nop.orm.rpc.batch-size} 拆分为多个顺序请求。
 */
public interface OrmRpcConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(OrmRpcConfigs.class);

    @Description("RPC 实体驱动单次批请求（batchModify/batchDelete/batchGet）允许的最大条目数")
    IConfigReference<Integer> CFG_ORM_RPC_BATCH_SIZE = varRef(s_loc, "nop.orm.rpc.batch-size",
            Integer.class, 500);
}
