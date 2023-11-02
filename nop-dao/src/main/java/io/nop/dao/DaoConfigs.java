/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface DaoConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(DaoConfigs.class);
    @Description("分布式系统中各个应用所在的本地时钟可能有差异，此时可以统一使用数据库时间作为系统时间。为避免频繁获取，从数据库获取时间之后可以缓存一段时间。"
            + "在缓存时间内，通过本地时钟的差量来对数据库时间进行调整。具体参见IJdbcTemplate.getDbEstimatedClock(querySpace)函数")
    IConfigReference<Integer> CFG_DAO_DB_TIME_CACHE_TIMEOUT = varRef(s_loc, "nop.dao.db-time-cache-timeout", Integer.class,
            30000);

    @Description("禁用JDBC批量提交")
    IConfigReference<Boolean> CFG_DAO_JDBC_DISABLE_BATCH_UPDATE = varRef(s_loc, "nop.dao.jdbc.disable-batch-update",
            Boolean.class, false);

    @Description("JDBC批量提交的最大条目数")
    IConfigReference<Integer> CFG_DAO_JDBC_MAX_BATCH_UPDATE_SIZE = varRef(s_loc, "nop.dao.jdbc.max-batch-update-size",
            Integer.class, 200);

    @Description("执行查询的最大超时时间，会限制所有查询操作")
    IConfigReference<Integer> CFG_DAO_MAX_QUERY_TIMEOUT = varRef(s_loc, "nop.dao.max-query-timeout", Integer.class,
            10 * 60 * 1000);

    @Description("执行更改操作的最大操作时间，会限制所有数据库修改操作")
    IConfigReference<Integer> CFG_DAO_MAX_UPDATE_TIMEOUT = varRef(s_loc, "nop.dao.max-update-timeout", Integer.class,
            10 * 60 * 1000);

    @Description("JDBC的驱动类")
    IConfigReference<String> CFG_DATASOURCE_DRIVER_CLASS_NAME = varRef(s_loc, "nop.datasource.driver-class-name", String.class,
            null);

    @Description("数据库连接的用户名")
    IConfigReference<String> CFG_DATASOURCE_USERNAME = varRef(s_loc, "nop.datasource.username", String.class, null);

    @Description("数据库连接的密码")
    IConfigReference<String> CFG_DATASOURCE_PASSWORD = varRef(s_loc, "nop.datasource.password", String.class, null);

    @Description("JDBC连接字符串")
    IConfigReference<String> CFG_DATASOURCE_JDBC_URL = varRef(s_loc, "nop.datasource.jdbc-url", String.class, null);

    @Description("保存字符串类似的字段时总是将空字符串转化为NULL。有些数据库无法区分空字符串和null，例如Oracle")
    IConfigReference<Boolean> CFG_AUTO_CONVERT_EMPTY_STRING_TO_NULL = varRef(s_loc,
            "nop.orm.auto_convert_empty_string_to_null", Boolean.class, true);

    @Description("启用租户特性")
    IConfigReference<Boolean> CFG_ORM_ENABLE_TENANT_BY_DEFAULT = varRef(s_loc, "nop.orm.enable-tenant-by-default", Boolean.class, false);

}
