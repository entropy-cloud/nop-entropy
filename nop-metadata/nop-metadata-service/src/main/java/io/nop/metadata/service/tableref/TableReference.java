package io.nop.metadata.service.tableref;

import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.service.field.ResolvedTableField;

import java.util.List;

/**
 * 解析后的表执行引用（架构基线 §4.4 D3）：由 {@link MetaTableReferenceResolver} 按 {@code tableType} 分派解析得到，
 * 供 Catalog/Quality/Profiling 三大执行器统一消费。executor 内部不再硬编码 external-only，而是按 reference 形态执行。
 *
 * <p>三态（D3）：
 * <ul>
 *   <li><b>EXTERNAL</b>：经 {@code withConnection} callback 语义 + 物理 {@code tableName}。
 *       {@link #dataSource} 非空，{@link #entity} 为 null，{@link #fields} 为 null（列由 DatabaseMetaData.getColumns 运行时读）。</li>
 *   <li><b>ENTITY</b>：平台 JDBC Connection（{@code IJdbcTransaction.getConnection()}）+ 物理表名 {@code entity.tableName}。
 *       {@link #entity} 非空，{@link #platformQuerySpace} 为平台库 querySpace，{@link #dataSource} 为 null，
 *       {@link #fields} 为 null（列由 DatabaseMetaData.getColumns 运行时读，与 external 同机制）。</li>
 *   <li><b>SQL</b>：经 {@code withConnection} callback + {@code (<sourceSql>) _t} 子查询。
 *       {@link #dataSource} 非空，{@link #sourceSql} 为用户定义 SQL 文本，{@link #fields} 非空（AST 解析，因
 *       DatabaseMetaData.getColumns 对子查询不适用）。</li>
 * </ul>
 *
 * <p>失败语义（D3）：reference 不可解析时由 resolver 显式抛 inline ErrorCode（不静默返回 null、不静默空集）。
 */
public final class TableReference {

    public enum Kind { EXTERNAL, ENTITY, SQL }

    private final Kind kind;
    private final String metaTableId;

    /** external/entity: 物理表名（简单标识符）。sql: null（用 sourceSql 构造子查询）。 */
    private final String physicalTableName;

    /** sql: 用户定义 SQL 文本。external/entity: null。 */
    private final String sourceSql;

    /** external/sql: 经 withConnection 建连的数据源。entity: null（用平台 Connection）。 */
    private final NopMetaDataSource dataSource;

    /** entity: 解析得到的平台 ORM 实体。external/sql: null。 */
    private final NopMetaEntity entity;

    /** entity: 平台库 querySpace（用于 IJdbcTransaction 取 Connection）。external/sql: null。 */
    private final String platformQuerySpace;

    /** sql: AST 解析的字段集合（DatabaseMetaData.getColumns 对子查询不适用）。external/entity: null。 */
    private final List<ResolvedTableField> fields;

    public TableReference(Kind kind, String metaTableId,
                          String physicalTableName, String sourceSql,
                          NopMetaDataSource dataSource, NopMetaEntity entity,
                          String platformQuerySpace, List<ResolvedTableField> fields) {
        this.kind = kind;
        this.metaTableId = metaTableId;
        this.physicalTableName = physicalTableName;
        this.sourceSql = sourceSql;
        this.dataSource = dataSource;
        this.entity = entity;
        this.platformQuerySpace = platformQuerySpace;
        this.fields = fields;
    }

    public Kind getKind() {
        return kind;
    }

    public String getMetaTableId() {
        return metaTableId;
    }

    public String getPhysicalTableName() {
        return physicalTableName;
    }

    public String getSourceSql() {
        return sourceSql;
    }

    public NopMetaDataSource getDataSource() {
        return dataSource;
    }

    public NopMetaEntity getEntity() {
        return entity;
    }

    public String getPlatformQuerySpace() {
        return platformQuerySpace;
    }

    public List<ResolvedTableField> getFields() {
        return fields;
    }

    /** 是否为子查询形态（sql 表）。executor 据此跳过标识符校验 + getIndexInfo/getColumns。 */
    public boolean isSubquery() {
        return kind == Kind.SQL;
    }
}
