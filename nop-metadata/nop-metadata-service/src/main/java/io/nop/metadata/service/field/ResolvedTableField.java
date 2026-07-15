package io.nop.metadata.service.field;

/**
 * 解析后的逻辑表字段（架构基线 §2.5.2 D2）：{@link MetaTableFieldResolver} 按 tableType 分派解析得到。
 *
 * <p>{@code name} 为字段输出名——entity 表取 {@code NopMetaEntityField.fieldName}；
 * external 表取 {@code buildSql} JSON 的 {@code columnName}；sql 表取 SELECT 解析出的字段名/别名。
 * 永不 null。
 *
 * <p>{@code sourceType} 为字段来源类型标记（entity/external/sql），便于消费方区分字段血缘来源。
 *
 * <p>{@code dataType} 为字段数据类型——entity 表取 {@code NopMetaEntityField.stdSqlType}；
 * external 表取 {@code buildSql} JSON 的 {@code dataType}；sql 表首版恒为 null（不伪造，与 §4.2.1 方案 A 一致）。
 */
public final class ResolvedTableField {
    public static final String SOURCE_ENTITY = "entity";
    public static final String SOURCE_EXTERNAL = "external";
    public static final String SOURCE_SQL = "sql";

    private final String name;
    private final String sourceType;
    private final String dataType;

    public ResolvedTableField(String name, String sourceType, String dataType) {
        this.name = name;
        this.sourceType = sourceType;
        this.dataType = dataType;
    }

    /** 字段名（永不 null）。 */
    public String getName() {
        return name;
    }

    /** 字段来源类型（entity/external/sql）。 */
    public String getSourceType() {
        return sourceType;
    }

    /** 字段数据类型（可能为 null，sql 表首版不取类型）。 */
    public String getDataType() {
        return dataType;
    }
}
