/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.tdengine;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * 2.5-MA-11（plan 2554 裁定 D）：TDengine 批写 chunk 上界配置。
 * TDengine 驱动不支持参数绑定，批写 SQL 以字面量内联整批展开（{@code genBatchInsertSubTableSql}），
 * 无上界时单条 SQL 随批规模线性膨胀（堆驻留 + 服务端解析压力）。按
 * 「行数 × 子表声明数 × 估算字节」三因子切分。
 */
public interface TdEngineConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(TdEngineConfigs.class);

    @Description("TDengine 批写单个 chunk 允许的最大数据行数")
    IConfigReference<Integer> CFG_TD_BATCH_MAX_ROWS = varRef(s_loc, "nop.orm.td.batch-max-rows",
            Integer.class, 1000);

    @Description("TDengine 批写单个 chunk 允许的最大估算 SQL 字节数")
    IConfigReference<Integer> CFG_TD_BATCH_MAX_BYTES = varRef(s_loc, "nop.orm.td.batch-max-bytes",
            Integer.class, 1024 * 1024);
}
