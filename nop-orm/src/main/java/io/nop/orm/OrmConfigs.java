/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface OrmConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(OrmConfigs.class);
    @Description("保存实体的时候检查所有必填字段都非空")
    IConfigReference<Boolean> CFG_ORM_CHECK_MANDATORY_WHEN_SAVE = varRef(s_loc, "nop.orm.check-mandatory-when-save",
            Boolean.class, true);

    @Description("更新实体的时候检查所有必填字段都非空")
    IConfigReference<Boolean> CFG_ORM_CHECK_MANDATORY_WHEN_UPDATE = varRef(s_loc, "nop.orm.check-mandatory-when-update",
            Boolean.class, true);

    @Description("批量加载时一次性最多加载多少条记录")
    IConfigReference<Integer> CFG_ORM_DEFAULT_ENTITY_BATCH_LOAD_SIZE = varRef(s_loc, "nop.orm.default-entity-batch-load-size",
            Integer.class, 1000);

    @Description("EQL语句编译的缓存大小")
    IConfigReference<Integer> CFG_QUERY_PLAN_CACHE_SIZE = varRef(s_loc, "nop.orm.query-plan-cache-size", Integer.class, 1000);

    @Description("针对每个实体的全局缓存的大小")
    IConfigReference<Integer> CFG_ENTITY_GLOBAL_CACHE_SIZE = varRef(s_loc, "nop.orm.entity-global-cache.size", Integer.class,
            10000);

    @Description("ORM全局缓存的超时时间")
    IConfigReference<Duration> CFG_ENTITY_GLOBAL_CACHE_TIMEOUT = varRef(s_loc, "nop.orm.entity-global-cache.timeout",
            Duration.class, Duration.of(10, ChronoUnit.MINUTES));

    @Description("控制是否开启全局缓存的开关。只有打开全局开关，具体orm文件的useGlobalCache配置才起作用")
    IConfigReference<Boolean> CFG_ENTITY_GLOBAL_CACHE_ENABLED = varRef(s_loc, "nop.orm.entity.global-cache.enabled",
            Boolean.class, true);

    @Description("根据ORM模型初始化数据库")
    IConfigReference<Boolean> CFG_INIT_DATABASE_SCHEMA = varRef(s_loc, "nop.orm.init-database-schema",
            Boolean.class, false);

    /**
     * 注意，数据库自动升级由 DataBaseUpgradeInitializer 提供实现，在工程内需引入依赖 io.github.entropy-cloud:nop-dbtool-core
     */
    @Description("根据ORM模型与数据库的差异自动升级数据库。注意，自动升级有风险，请不要在生产环境开启，并注意备份数据")
    IConfigReference<Boolean> CFG_DB_DIFFER_AUTO_UPGRADE_DATABASE = varRef(s_loc, "nop.orm.db-differ.auto-upgrade-database",
            Boolean.class, false);

    @Description("启动的时候自动校验所有sql-lib中管理的SQL格式正确")
    IConfigReference<Boolean> CFG_CHECK_ALL_SQL_LIB_WHEN_INIT =
            varRef(s_loc, "nop.orm.check-all-sql-lib-when-init", Boolean.class, false);

    @Description("dao资源文件检查数据库实体的间隔时间，单位为毫秒")
    IConfigReference<Long> CFG_DAO_RESOURCE_CHECK_INTERVAL =
            varRef(s_loc, "nop.orm.dao-resource-check-interval", Long.class, 5000L);

    @Description("OrmInterceptor模型缓存是否需要支持动态加载")
    IConfigReference<Boolean> CFG_ORM_INTERCEPTOR_CACHE_CHECK_CHANGE =
            varRef(s_loc, "nop.orm.interceptor-cache-check-change", Boolean.class, true);

    @Description("Orm模型缓存是否需要支持动态加载")
    IConfigReference<Boolean> CFG_ORM_MODEL_CACHE_CHECK_CHANGE =
            varRef(s_loc, "nop.orm.model-cache-check-change", Boolean.class, true);

    @Description("在启用tenant定制的情况下，最多允许多少个tenant的ORM缓存在内存中")
    IConfigReference<Integer> CFG_ORM_MODEL_CACHE_TENANT_CACHE_CONTAINER_SIZE = varRef(s_loc,
            "nop.orm.orm-model-cache.tenant-cache-container-size", Integer.class, 100);

    @Description("缺省的系统用户名，当上下文没有用户信息，且需要记录创建者时使用")
    IConfigReference<String> CFG_ORM_SYS_USER_NAME = varRef(s_loc, "nop.orm.sys-user-name", String.class, OrmConstants.USER_NAME_SYS);

    @Description("检查Session绑定的上下文对象。一个OrmSession只允许在一个上下文中执行")
    IConfigReference<Boolean> CFG_ORM_SESSION_CHECK_CONTEXT = varRef(s_loc, "nop.orm.session-check-context",
            Boolean.class, true);

}
