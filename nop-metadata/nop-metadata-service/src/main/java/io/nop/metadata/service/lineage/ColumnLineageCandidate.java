package io.nop.metadata.service.lineage;

/**
 * 列级血缘边候选（架构基线 §2.6.1 列级 sql_parse，P2-5+ 裁定 D1/D3）。
 *
 * <p>由 {@link SqlColumnLineageExtractor} 解析 SELECT projections 产出。BizModel 层负责将
 * {@code sourceTableName} 匹配目录 {@code NopMetaTable.tableName}：
 * <ul>
 *   <li>命中 → upsert NopMetaLineageEdge 列级边（sourceColumn/targetColumn/transformType 填充，lineageSource=sql_parse）。</li>
 *   <li>未命中或 {@link #unresolvable} → 进 unresolved 列表（不建悬空边）。</li>
 * </ul>
 *
 * <p>软引用裁定（D1）：sourceColumn/targetColumn 存列名字符串原样，不校验列是否存在。
 *
 * <p>{@code sourceTableName} 为 SQL 中归属的源表裸名（去 schema 前缀），用于匹配目录；
 * {@link #unresolvable} 为 true 时表示列引用无法归属（如多表无限定符、CTE/子查询别名、通配符），
 * 此时 sourceTableName/sourceColumn 可能仍保留原样用于诊断（unresolvedReason 说明原因）。
 */
public final class ColumnLineageCandidate {
    private final String targetColumn;
    private final String sourceTableName;
    private final String sourceColumn;
    private final String transformType;
    private final boolean unresolvable;
    private final String unresolvedReason;

    private ColumnLineageCandidate(String targetColumn, String sourceTableName, String sourceColumn,
                                   String transformType, boolean unresolvable, String unresolvedReason) {
        this.targetColumn = targetColumn;
        this.sourceTableName = sourceTableName;
        this.sourceColumn = sourceColumn;
        this.transformType = transformType;
        this.unresolvable = unresolvable;
        this.unresolvedReason = unresolvedReason;
    }

    /** 可解析的列级边候选。 */
    public static ColumnLineageCandidate resolved(String targetColumn, String sourceTableName,
                                                   String sourceColumn, String transformType) {
        return new ColumnLineageCandidate(targetColumn, sourceTableName, sourceColumn, transformType, false, null);
    }

    /** 不可解析的列引用（进 unresolved，不建边）。reason 说明原因（不静默丢弃）。 */
    public static ColumnLineageCandidate unresolvable(String targetColumn, String sourceColumn,
                                                       String reason) {
        return new ColumnLineageCandidate(targetColumn, null, sourceColumn, null, true, reason);
    }

    /** 输出列名（alias 优先 / 列名次之 / 表达式标记 &lt;expr_N&gt;）。 */
    public String getTargetColumn() {
        return targetColumn;
    }

    /** 归属的源表裸名（用于匹配目录 tableName）；unresolvable 时为 null。 */
    public String getSourceTableName() {
        return sourceTableName;
    }

    /** 源列名（列名字符串原样，软引用）。 */
    public String getSourceColumn() {
        return sourceColumn;
    }

    /** 转换类型：direct / derived / aggregated（D3）；unresolvable 时为 null。 */
    public String getTransformType() {
        return transformType;
    }

    /** 是否不可归属（多表无限定符 / CTE 子查询别名 / 通配符等）。 */
    public boolean isUnresolvable() {
        return unresolvable;
    }

    /** 不可归属原因（诊断用，不静默丢弃）。 */
    public String getUnresolvedReason() {
        return unresolvedReason;
    }
}
