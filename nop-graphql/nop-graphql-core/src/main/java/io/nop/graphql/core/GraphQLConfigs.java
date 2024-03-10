/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface GraphQLConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(GraphQLConfigs.class);
    @Description("GraphQL查询语句的解析缓存的超时时间")
    IConfigReference<Duration> CFG_GRAPHQL_QUERY_PARSE_CACHE_TIMEOUT = varRef(s_loc, "nop.graphql.query.parse-cache-timeout",
            Duration.class, null);

    @Description("GraphQL查询语句的解析缓存的大小")
    IConfigReference<Integer> CFG_GRAPHQL_QUERY_PARSE_CACHE_SIZE = varRef(s_loc, "nop.graphql.query.parse-cache-size",
            Integer.class, 2000);

    @Description("GraphQL查询语句的最大文本大小")
    IConfigReference<Integer> CFG_GRAPHQL_QUERY_PARSE_MAX_LENGTH = varRef(s_loc, "nop.graphql.query.parse-max-length",
            Integer.class, 4096);

    @Description("GraphQL查询允许的最大嵌套深度")
    IConfigReference<Integer> CFG_GRAPHQL_QUERY_MAX_DEPTH = varRef(s_loc, "nop.graphql.query.max-depth", Integer.class, 7);

    @Description("GraphQL单次查询所允许的操作个数")
    IConfigReference<Integer> CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT = varRef(s_loc, "nop.graphql.query.max-operation-count", Integer.class, 10);

    @Description("GraphQL引擎启用Maker Checker机制，在执行变更操作之前会先尝试执行对应的tryAction")
    IConfigReference<Boolean> CFG_GRAPHQL_MAKER_CHECKER_ENABLED = varRef(s_loc, "nop.graphql.maker-checker.enabled",
            Boolean.class, false);

    @Description("GraphQL允许返回的最多的数据行数")
    IConfigReference<Integer> CFG_GRAPHQL_MAX_PAGE_SIZE = varRef(s_loc, "nop.graphql.max-page-size", Integer.class, 1000);

    @Description("GraphQL缺省的分页行数")
    IConfigReference<Integer> CFG_GRAPHQL_DEFAULT_PAGE_SIZE = varRef(s_loc, "nop.graphql.default-page-size", Integer.class,
            10);

    @Description("是否启用GraphQL内置的内省功能来获取对象定义")
    IConfigReference<Boolean> CFG_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED = varRef(s_loc,
            "nop.graphql.schema-introspection.enabled", Boolean.class, false);

    @Description("GraphQL引擎内置加载的定义文件")
    IConfigReference<String> CFG_GRAPHQL_BUILTIN_SCHEMA_PATHS = varRef(s_loc, "nop.graphql.builtin-schema-path", String.class,
            null);

    @Description("系统启动时主动初始化所有BizObject对象")
    IConfigReference<Boolean> CFG_GRAPHQL_EAGER_INIT_BIZ_OBJECT = varRef(s_loc, "nop.graphql.eager-init-biz-object",
            Boolean.class, true);

    @Description("GraphQL模型装载的时候就检查字典是否存在，避免运行时报错")
    IConfigReference<Boolean> CFG_GRAPHQL_CHECK_DICT_WHEN_INIT = varRef(s_loc, "nop.graphql.check-dict-when-init",
            Boolean.class, true);

    @Description("GraphQL解析结果是否需要检查动态更新")
    IConfigReference<Boolean> CFG_GRAPHQL_PARSE_CACHE_CHECK_CHANGED = varRef(s_loc, "nop.graphql.parse-cache-check-changed",
            Boolean.class, true);
}