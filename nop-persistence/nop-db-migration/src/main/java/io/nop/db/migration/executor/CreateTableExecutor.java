/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical-entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.executor;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.dialect.IDialect;
import io.nop.db.migration.ForeignKeyAction;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.ColumnDefinition;
import io.nop.db.migration.model.CreateTableChange;
import io.nop.db.migration.model.ForeignKeyConstraint;
import io.nop.db.migration.model.PrimaryKeyConstraint;
import io.nop.db.migration.model.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;

public class CreateTableExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "createTable";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        CreateTableChange createTable = (CreateTableChange) change;
        String tableName = createTable.getName();
        if (StringHelper.isBlank(tableName)) {
            return;
        }
        List<String> statements = buildCreateTableStatements(createTable, dialect);
        for (String sql : statements) {
            if (StringHelper.isNotBlank(sql)) {
                context.getJdbcTemplate().executeUpdate(
                    SQL.begin()
                        .querySpace(context.getQuerySpace())
                        .name("create-table-" + tableName)
                        .append(sql)
                        .end()
                );
            }
        }
    }

    @Override
    public String generateRollbackSql(AbstractComponentModel change, IDialect dialect) {
        CreateTableChange createTable = (CreateTableChange) change;
        return "DROP TABLE IF EXISTS " + dialect.escapeSQLName(createTable.getName());
    }

    @Override
    public boolean supports(String changeType) {
        return CHANGE_TYPE.equals(changeType);
    }

    /**
     * The CREATE TABLE statement plus, on dialects without inline COMMENT
     * support (PostgreSQL/H2/Oracle), the separate COMMENT ON statement.
     */
    protected List<String> buildCreateTableStatements(CreateTableChange change, IDialect dialect) {
        List<String> statements = new ArrayList<>();
        statements.add(buildCreateTableSql(change, dialect));
        statements.addAll(DdlSyntax.buildTableCommentSql(dialect, change.getName(), change.getRemark()));
        return statements;
    }

    protected String buildCreateTableSql(CreateTableChange change, IDialect dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ");
        sb.append(dialect.escapeSQLName(change.getName()));
        sb.append(" (\n");

        boolean first = true;
        if (change.getColumns() != null) {
            for (ColumnDefinition column : change.getColumns()) {
                if (!first) {
                    sb.append(",\n");
                }
                first = false;
                sb.append("  ");
                sb.append(dialect.escapeSQLName(column.getName()));
                sb.append(" ");
                sb.append(buildColumnType(column, dialect));

                if (column.isPrimaryKey()) {
                    sb.append(" PRIMARY KEY");
                }
                if (!column.isNullable()) {
                    sb.append(" NOT NULL");
                }
                if (column.isAutoIncrement()) {
                    sb.append(DdlSyntax.autoIncrementClause(dialect));
                }
                if (StringHelper.isNotBlank(column.getDefaultValue())) {
                    DdlSyntax.checkDefaultValue(column.getDefaultValue(), change.getName(), column.getName());
                    sb.append(" DEFAULT ").append(column.getDefaultValue());
                }
            }
        }

        // Table-level constraints: composite primary keys / unique constraints /
        // foreign keys can only be expressed here (column-level primaryKey
        // covers single-column keys only)
        String constraintSql = buildConstraintSql(change, dialect);
        if (!constraintSql.isEmpty()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append(constraintSql);
        }

        sb.append("\n)");

        if (StringHelper.isNotBlank(change.getRemark()) && DdlSyntax.supportsInlineComment(dialect)) {
            sb.append(" COMMENT '").append(DdlSyntax.escapeComment(change.getRemark())).append("'");
        }

        return sb.toString();
    }

    private String buildConstraintSql(CreateTableChange change, IDialect dialect) {
        StringBuilder sb = new StringBuilder();

        PrimaryKeyConstraint primaryKey = change.getPrimaryKey();
        if (primaryKey != null && primaryKey.getColumnNames() != null && !primaryKey.getColumnNames().isEmpty()) {
            if (sb.length() > 0) {
                sb.append(",\n  ");
            }
            sb.append("  ");
            if (StringHelper.isNotBlank(primaryKey.getName())) {
                sb.append("CONSTRAINT ").append(dialect.escapeSQLName(primaryKey.getName())).append(" ");
            }
            sb.append("PRIMARY KEY (").append(escapeNameList(dialect, primaryKey.getColumnNames())).append(")");
        }

        UniqueConstraint unique = change.getUniqueConstraint();
        if (unique != null && unique.getColumnNames() != null && !unique.getColumnNames().isEmpty()) {
            if (sb.length() > 0) {
                sb.append(",\n  ");
            }
            sb.append("  ");
            if (StringHelper.isNotBlank(unique.getName())) {
                sb.append("CONSTRAINT ").append(dialect.escapeSQLName(unique.getName())).append(" ");
            }
            sb.append("UNIQUE (").append(escapeNameList(dialect, unique.getColumnNames())).append(")");
        }

        ForeignKeyConstraint foreignKey = change.getForeignKey();
        if (foreignKey != null && foreignKey.getColumnNames() != null && !foreignKey.getColumnNames().isEmpty()) {
            if (sb.length() > 0) {
                sb.append(",\n  ");
            }
            sb.append("  ");
            if (StringHelper.isNotBlank(foreignKey.getName())) {
                sb.append("CONSTRAINT ").append(dialect.escapeSQLName(foreignKey.getName())).append(" ");
            }
            sb.append("FOREIGN KEY (").append(escapeNameList(dialect, foreignKey.getColumnNames())).append(")")
                .append(" REFERENCES ");

            String refSchema = foreignKey.getRefSchemaName();
            if (StringHelper.isNotBlank(refSchema)) {
                sb.append(dialect.escapeSQLName(refSchema)).append(".");
            }
            sb.append(dialect.escapeSQLName(foreignKey.getRefTableName()));
            sb.append(" (").append(escapeNameList(dialect, foreignKey.getRefColumnNames())).append(")");

            String onDelete = foreignActionSql(foreignKey.getOnDelete());
            if (onDelete != null) {
                sb.append(" ON DELETE ").append(onDelete);
            }
            String onUpdate = foreignActionSql(foreignKey.getOnUpdate());
            if (onUpdate != null) {
                sb.append(" ON UPDATE ").append(onUpdate);
            }
        }

        return sb.toString();
    }

    private String escapeNameList(IDialect dialect, Iterable<String> columnNames) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String columnName : columnNames) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(dialect.escapeSQLName(columnName));
        }
        return sb.toString();
    }

    private String foreignActionSql(ForeignKeyAction action) {
        if (action == null) {
            return null;
        }
        switch (action) {
            case CASCADE:
                return "CASCADE";
            case SET_NULL:
                return "SET NULL";
            case RESTRICT:
                return "RESTRICT";
            case NO_ACTION:
            default:
                return "NO ACTION";
        }
    }

    protected String buildColumnType(ColumnDefinition column, IDialect dialect) {
        return AddColumnExecutor.buildColumnType(column, dialect);
    }
}
