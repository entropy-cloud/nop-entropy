package io.nop.metadata.service.lineage;

/**
 * SQL 源表引用：从 sourceSql 的 FROM/JOIN 子句抽取出的表引用（架构基线 §2.6.1）。
 *
 * <p>{@code fullName} 为 SQL 中书写的原始形式（可能含 schema 前缀，如 {@code my_schema.orders}），
 * 用于 unresolved 报告（让用户看到实际引用名）。{@code simpleName} 为去掉 schema 前缀的裸表名
 * （如 {@code orders}），用于匹配目录 {@code NopMetaTable.tableName}。
 */
public final class TableReference {
    private final String fullName;
    private final String simpleName;

    public TableReference(String fullName, String simpleName) {
        this.fullName = fullName;
        this.simpleName = simpleName;
    }

    /** SQL 中书写的原始形式（可能含 schema 前缀）。 */
    public String getFullName() {
        return fullName;
    }

    /** 去掉 schema 前缀的裸表名，用于匹配目录表名。 */
    public String getSimpleName() {
        return simpleName;
    }
}
