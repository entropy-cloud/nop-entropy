/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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

    @Description("启用动态querySpace支持，可以动态将default的querySpace映射为指定的querySpace")
    IConfigReference<Boolean> CFG_ORM_ENABLE_DYNAMIC_QUERY_SPACE = varRef(s_loc,
            "nop.orm.enable-dynamic-query-space", Boolean.class, true);

    @Description("查询空间和数据库方言之间的映射关系")
    IConfigReference<String> CFG_QUERY_SPACE_TO_DIALECT = varRef(s_loc,
            "nop.dao.config.query-space-to-dialect", String.class, null);

    @Description("读写分离配置（plan 2319），JSON对象文本，key为querySpace名。value对象字段："
            + "write-ds（写库DataSource bean名）、read-ds（从库DataSource bean名数组）、"
            + "load-balancer（round-robin|random|weight，缺省round-robin）、weights（从库bean名到权重，weight算法用，缺省等权）、"
            + "replica-fail-threshold（连续失败次数阈值，达到后进入冷却，缺省3）、"
            + "replica-cooldown-seconds（冷却时长秒数，期满半开探测，缺省30）。"
            + "未配置的querySpace行为不变。物理数据源bean名前缀建议nopReadWriteDs_，避免被nopDataSource_的collect-beans误收集")
    IConfigReference<String> CFG_DAO_READWRITE = varRef(s_loc,
            "nop.dao.readwrite", String.class, null);

    @Description("分片策略配置（plan 2320），JSON对象文本。结构：{strategies:{<策略名>:{type:mod|hash-mod|range|interval|inline,"
            + "shard-count,ranges,interval-step,datetime-lower,datetime-upper,format,algorithm-expression,"
            + "table-pattern(缺省'{table}_{shard}'),query-space-pattern,missing-shard-policy(fail|default-shard|fanout,缺省fail),"
            + "default-shard,shard-names,entities:[实体名列表]}},default-strategy(可选全局回落策略名)}。"
            + "实体绑定用显式实体名列表（设计拒绝通配符）。missing-shard-policy=fanout（plan 2321）声明该策略绑定实体的"
            + "缺分片值SELECT查询按分片全集展开执行（需nop.orm.shard.fanout-enabled=true）。未配置时分片行为零变更")
    IConfigReference<String> CFG_ORM_SHARD = varRef(s_loc,
            "nop.orm.shard", String.class, null);

    @Description("分片执行引擎全局开关（plan 2321）。缺省false：missing-shard-policy=fanout的策略在运行期退化为fail"
            + "（抛ERR_ORM_SHARD_CONDITION_MISSING，不静默降级单分片），启动期记WARN；true时缺分片值的SELECT查询"
            + "按分片全集串行执行并归并")
    IConfigReference<Boolean> CFG_ORM_SHARD_FANOUT_ENABLED = varRef(s_loc,
            "nop.orm.shard.fanout-enabled", Boolean.class, false);

    @Description("分片执行引擎归并行数上限（plan 2321），缺省10000。逐分片增量计数：每拉回一个分片立即累计，"
            + "超过上限立即中断后续分片、不返回部分结果，抛ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED。"
            + "配置为非正数启动即抛ERR_DAO_SHARD_CONFIG_INVALID")
    IConfigReference<Integer> CFG_ORM_SHARD_FANOUT_MAX_ROWS = varRef(s_loc,
            "nop.orm.shard.fanout-max-rows", Integer.class, 10000);
}
