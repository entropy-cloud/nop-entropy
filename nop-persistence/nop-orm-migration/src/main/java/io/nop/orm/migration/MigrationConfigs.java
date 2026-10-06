/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * WI9 编排配置面（设计 §4.8）：与 {@code nop.orm.shard}（2320）、{@code nop.dao.readwrite}（2319）
 * 同构的部署属性层，JSON 配置串。写停止关是独立平面键（{@code nop.orm.migration.write-gate}，
 * 登记 OrmConfigs——检查点所在层），编排器在阶段推进时经 ConfigProvider 动态翻转该平面键。
 */
@Locale("zh-CN")
public interface MigrationConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(MigrationConfigs.class);

    @Description("数据搬迁编排配置（WI9），JSON对象文本。结构：{scenario(rescale|relocate),entity(实体名),"
            + "source-query-spaces:[源querySpace列表],target-strategy(rescale场景必填,新nop.orm.shard完整配置对象),"
            + "target-query-space-mapping(relocate场景必填,旧querySpace到新querySpace映射),"
            + "write-gate(open|closed,作业启动时的写停止关初始值,运行期以平面键nop.orm.migration.write-gate为准),"
            + "incremental:{enabled,connector-name,position-p0(PREPARE钉定后回填),quiesce-seconds(缺省30)},"
            + "verify:{count(缺省true),sample-ratio(缺省0),full-checksum(缺省false)},"
            + "throttle:{rate-limit(缺省0不限),batch-size(缺省1000),concurrency(缺省1)},batch-task-name(缺省orm-migration-<实体名>)}")
    IConfigReference<String> CFG_ORM_MIGRATION = varRef(s_loc, "nop.orm.migration", String.class, null);
}
