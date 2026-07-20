/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * granularity→SQL 分桶表达式翻译（架构基线 §4.4.2 D7）。
 *
 * <p>按方言（H2/PostgreSQL/MySQL）把时间维度 {@code granularity}（year/quarter/month/week/day/hour）
 * 翻译为 SQL 分桶表达式。首版以 H2 可用函数为准（H2/PostgreSQL 用 {@code DATE_TRUNC}，MySQL 用 {@code DATE_FORMAT}）。
 *
 * <p>无状态，方法为静态。仅用于 external/sql 路径（withConnection 原生 SQL）——entity 路径经
 * {@code orm().executeQuery} 时 EQL 编译器校验函数名（FORMATDATETIME 等被判 unknown-function），故 entity
 * 时间分桶首版仅支持 EQL 已知函数，完整 granularity 分桶为 follow-up（见 §4.4.2 D7）。
 */
public final class GranularityBucketing {

    /** 首版支持的方言（H2/MySQL/PostgreSQL，与单表查询 §4.4 一致）。 */
    static final Set<String> SUPPORTED_DIALECTS = Set.of("H2", "MySQL", "PostgreSQL");


    /** H2 与 PostgreSQL 的分桶表达式模板（参数：列表达式）。DATE_TRUNC 在 H2 2.x / PostgreSQL 可用。 */
    private static final Map<String, String> H2_PG_TEMPLATES = new HashMap<>();
    /** MySQL 的分桶表达式模板（参数：列表达式）。 */
    private static final Map<String, String> MYSQL_TEMPLATES = new HashMap<>();

    static {
        H2_PG_TEMPLATES.put("year", "DATE_TRUNC('year',%s)");
        H2_PG_TEMPLATES.put("quarter", "DATE_TRUNC('quarter',%s)");
        H2_PG_TEMPLATES.put("month", "DATE_TRUNC('month',%s)");
        H2_PG_TEMPLATES.put("week", "DATE_TRUNC('week',%s)");
        H2_PG_TEMPLATES.put("day", "DATE_TRUNC('day',%s)");
        H2_PG_TEMPLATES.put("hour", "DATE_TRUNC('hour',%s)");

        MYSQL_TEMPLATES.put("year", "DATE_FORMAT(%s,'%Y-01-01 00:00:00')");
        MYSQL_TEMPLATES.put("quarter", "DATE_FORMAT(%s,'%Y-%m-01 00:00:00')");
        MYSQL_TEMPLATES.put("month", "DATE_FORMAT(%s,'%Y-%m-01 00:00:00')");
        MYSQL_TEMPLATES.put("week", "DATE_FORMAT(%s,'%Y-%m-%d 00:00:00')");
        MYSQL_TEMPLATES.put("day", "DATE_FORMAT(%s,'%Y-%m-%d 00:00:00')");
        MYSQL_TEMPLATES.put("hour", "DATE_FORMAT(%s,'%Y-%m-%d %H:00:00')");
    }

    private GranularityBucketing() {
    }

    /**
     * 翻译 granularity 为分桶 SQL 表达式。
     *
     * @param granularity   granularity 值（year/quarter/month/week/day/hour，大小写不敏感）
     * @param columnExpr    已校验的列表达式（物理列名，已通过标识符白名单）
     * @param dialect       数据库方言（H2/MySQL/PostgreSQL）
     * @param dimensionName 维度名（仅用于错误上下文）
     * @return 分桶 SQL 表达式（如 {@code DATE_TRUNC('month',COL)})
     */
    public static String translate(String granularity, String columnExpr, String dialect, String dimensionName) {
        if (granularity == null || granularity.trim().isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_GRANULARITY_NOT_SUPPORTED)
                    .param("granularity", String.valueOf(granularity))
                    .param("dimensionName", String.valueOf(dimensionName));
        }
        String g = granularity.trim().toLowerCase(Locale.ROOT);
        Map<String, String> templates;
        if ("MySQL".equalsIgnoreCase(dialect)) {
            templates = MYSQL_TEMPLATES;
        } else {
            // H2 / PostgreSQL 共用 DATE_TRUNC
            templates = H2_PG_TEMPLATES;
        }
        String tpl = templates.get(g);
        if (tpl == null) {
            throw new NopException(NopMetadataErrors.ERR_GRANULARITY_NOT_SUPPORTED)
                    .param("granularity", granularity)
                    .param("dimensionName", String.valueOf(dimensionName));
        }
        return String.format(tpl, columnExpr);
    }
}
