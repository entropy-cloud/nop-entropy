/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.sqlview;

/**
 * SQL 视图字段：从 tableType=sql 的 sourceSql 的 SELECT 输出列解析得到（架构基线 §4.2.1）。
 *
 * <p>{@code name} 为字段输出名——别名优先（{@code expr AS alias} → alias），无别名时取列名
 * （{@code SqlColumnName.getName()}），无别名且为表达式列时取标记 {@code <expr_N>}（不静默跳过）。
 *
 * <p>{@code alias} 为显式别名（仅当 SELECT 项带 {@code AS alias} 时非 null），便于 UI 区分"原始列名"与"别名"。
 *
 * <p>{@code type} 为字段类型——首版（方案 A）恒为 {@code null}（不伪造）。类型获取（方案 B LIMIT 0 经
 * ResultSetMetaData / 方案 C 手动录入）为 follow-up，届时此字段承载真实类型。
 */
public final class SqlViewField {
    private final String name;
    private final String alias;
    private final String type;

    public SqlViewField(String name, String alias, String type) {
        this.name = name;
        this.alias = alias;
        this.type = type;
    }

    /** 字段输出名（别名优先 / 列名次之 / 表达式标记 &lt;expr_N&gt;）。永不 null。 */
    public String getName() {
        return name;
    }

    /** 显式别名（仅 {@code AS alias} 时非 null）。 */
    public String getAlias() {
        return alias;
    }

    /** 字段类型；首版（方案 A）恒为 null（不伪造）。 */
    public String getType() {
        return type;
    }
}
