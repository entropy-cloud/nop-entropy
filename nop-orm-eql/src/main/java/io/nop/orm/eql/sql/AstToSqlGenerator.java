/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.sql;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.text.marker.Marker;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SqlExprList;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.SQLDataType;
import io.nop.dao.dialect.function.ISQLFunction;
import io.nop.orm.eql.OrmEqlConstants;
import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlAlias;
import io.nop.orm.eql.ast.SqlBooleanLiteral;
import io.nop.orm.eql.ast.SqlCastExpr;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlDateTimeLiteral;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlLikeExpr;
import io.nop.orm.eql.ast.SqlLimit;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlRegularFunction;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlStringLiteral;
import io.nop.orm.eql.ast.SqlTableName;
import io.nop.orm.eql.ast.SqlTypeExpr;
import io.nop.orm.eql.compile.SqlPropJoin;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.utils.EqlHelper;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.eql.utils.EqlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static io.nop.orm.eql.OrmEqlErrors.ARG_AST_NODE;

public class AstToSqlGenerator extends AstToEqlGenerator {
    static final Logger LOG = LoggerFactory.getLogger(AstToSqlGenerator.class);

    private final IDialect dialect;

    public AstToSqlGenerator(IDialect dialect, SQL.SqlBuilder sb) {
        super(sb);
        this.dialect = dialect;
    }

    public AstToSqlGenerator(IDialect dialect) {
        this(dialect, SQL.begin());
    }

    protected String normalizeTableName(String tableName) {
        return EqlHelper.normalizeTableName(dialect, tableName);
    }

    @Override
    public void visitSqlQuerySelect(SqlQuerySelect node) {
        if (node.getLimit() != null) {
            SqlLimit limit = node.getLimit();
            SqlExprList exprs = dialect.getPaginationHandler().buildPageExpr(limit.getLimit(), limit.getOffset(),
                    new PrintSqlSelect(node));
            printExprList(exprs);
        } else {
            printSelect(node);
        }
    }

    class PrintSqlSelect extends SqlNodeWrapper {
        public PrintSqlSelect(SqlQuerySelect select) {
            super(select);
        }

        @Override
        public void appendTo(SQL.SqlBuilder buf) {
            AstToEqlGenerator gen = new AstToSqlGenerator(dialect, buf);
            gen.setPretty(true);
            gen.visit(getNode());
        }
    }

    protected void printSelect(SqlQuerySelect node) {
        if (node.getFrom() == null && node.getWhere() == null) {
            // select xx 语法
            AstToSqlGenerator gen = new AstToSqlGenerator(dialect);
            gen.setPretty(isPretty());
            gen.printList(node.getProjections(), ",");
            SQL sql = gen.getSql().end();
            String text = dialect.getSelectFromDualSql(sql.getText());
            sb.append(text);
            if (!sql.getMarkers().isEmpty()) {
                int offset = text.indexOf(sql.getText());
                for (Marker marker : sql.getMarkers()) {
                    sb.appendMarker(marker.offset(offset));
                }
            }
        } else {
            super.printSelect(node);
        }
    }

    @Override
    public void visitSqlExprProjection(SqlExprProjection node) {
        SqlExpr expr = node.getExpr();
        ISqlExprMeta exprMeta = expr.getResolvedExprMeta();

        SqlAlias alias = node.getAlias();

        String owner = expr.getResolvedOwner();

        List<String> colNames = exprMeta.getColumnNames();
        if (colNames == null) {
            super.visitSqlExprProjection(node);
        } else {
            for (int i = 0, n = exprMeta.getColumnCount(); i < n; i++) {
                if (i != 0) {
                    print(',');
                }
                printCol(owner, colNames.get(i));
                printAlias(alias, i, n);
            }
        }
    }

    void printAlias(SqlAlias alias, int index, int count) {
        if (alias == null)
            return;

        String name = EqlHelper.getAlias(alias.getAlias(), index, count);
        print(" as ");
        if (!alias.isGenerated()) {
            name = dialect.escapeSQLName(name);
        }
        print(name);
        print(' ');
    }

    void printCol(String owner, String colName) {
        // 如果是sqlText
        if (colName.startsWith("@")) {
            String sqlText = colName.substring(1);
            sqlText = EqlHelper.replaceOwner(owner, sqlText);
            print(sqlText);
        } else {
            if (owner != null) {
                print(owner);
                print('.');
            }
            print(colName);
        }
    }

    @Override
    protected void printWhere(SqlQuerySelect node) {
        List<SqlSingleTableSource> tables = node.getFrom().getEntitySources();
        if (tables.isEmpty()) {
            if (node.getWhere() != null) {
                println();
                visitSqlWhere(node.getWhere());
            }
        } else {
            println();
            boolean first = true;
            for (SqlSingleTableSource table : tables) {
                EntityTableMeta tableMeta = (EntityTableMeta) table.getResolvedTableMeta();
                IEntityModel entityModel = tableMeta.getEntityModel();

                String alias = table.getAliasName();

                if (entityModel.isUseTenant()) {
                    if (first) {
                        sb.where();
                    } else {
                        sb.and();
                    }
                    EqlHelper.appendCol(sb, dialect, alias, entityModel.getTenantColumn());
                    sb.append('=');
                    sb.markWithProvider("?", OrmEqlConstants.MARKER_TENANT_ID, () -> ContextProvider.currentTenantId());
                    first = false;
                }
            }

            if (node.getWhere() != null && node.getWhere().getExpr() != null) {
                if (first) {
                    sb.where();
                } else {
                    sb.and();
                }
                visit(node.getWhere().getExpr());
            }
        }
    }

    @Override
    public void visitSqlSingleTableSource(SqlSingleTableSource node) {
        visitSqlTableName(node.getTableName());

        appendPropJoins(node);
    }

    void appendPropJoins(SqlSingleTableSource node) {
        if (node.getPropJoins() != null) {
            for (SqlPropJoin propJoin : node.getPropJoins().values()) {
                if (propJoin.isExplicit())
                    continue;

                println();
                incIndent();
                sb.append(' ');
                sb.append(propJoin.getJoinType().getText());
                sb.append(' ');
                visitSqlTableName(propJoin.getRight().getTableName());
                sb.append("on ");
                visit(propJoin.getCondition());
                decIndent();
                appendPropJoins(propJoin.getRight());
            }
        }
    }

    @Override
    public void visitSqlTableName(SqlTableName node) {
        ISqlTableMeta table = node.getResolvedTableMeta();
        String alias = getTableAlias(node);
        if (table instanceof EntityTableMeta) {
            IEntityModel entityModel = ((EntityTableMeta) table).getEntityModel();
            sb.markTable(normalizeTableName(entityModel.getTableName()), alias, entityModel.getName());
        } else {
            sb.markTable(normalizeTableName(node.getName()), alias, null);
        }
    }

    String getTableAlias(SqlTableName node) {
        if (node.getASTParent().getASTKind() == EqlASTKind.SqlSingleTableSource) {
            SqlSingleTableSource source = (SqlSingleTableSource) node.getASTParent();
            SqlAlias alias = source.getAlias();
            if (alias == null)
                return null;
            return alias.getAlias();
        }
        return null;
    }

    @Override
    public void visitSqlColumnName(SqlColumnName node) {
        ISqlExprMeta exprMeta = node.getResolvedExprMeta();
        String owner = node.getResolvedOwner();

        List<String> colNames = exprMeta.getColumnNames();
        if (colNames == null) {
            super.visitSqlColumnName(node);
            return;
        }
        if (colNames.size() == 1) {
            printCol(owner, colNames.get(0));
        } else {
            print('(');
            for (int i = 0, n = exprMeta.getColumnCount(); i < n; i++) {
                if (i != 0) {
                    print(',');
                }
                printCol(owner, colNames.get(i));
            }
            print(')');
        }
    }

    @Override
    public void visitSqlStringLiteral(SqlStringLiteral node) {
        print(' ');
        print(dialect.getStringLiteral(node.getValue()));
        print(' ');
    }

    @Override
    public void visitSqlBooleanLiteral(SqlBooleanLiteral node) {
        print(dialect.getBooleanValueLiteral(node.getValue()));
    }


    @Override
    public void visitSqlDateTimeLiteral(SqlDateTimeLiteral node) {
        print(' ');
        switch (node.getType()) {
            case DATE: {
                LocalDate date = ConvertHelper.toLocalDate(node.getValue(), err -> newError(err, node));
                print(dialect.getDateLiteral(date));
                break;
            }
            case TIME: {
                LocalTime time = ConvertHelper.toLocalTime(node.getValue(), err -> newError(err, node));
                print(dialect.getTimeLiteral(time));
                break;
            }
            case TIMESTAMP: {
                Timestamp timestamp = ConvertHelper.toTimestamp(node.getValue(), err -> newError(err, node));
                print(dialect.getTimestampLiteral(timestamp));
                break;
            }
        }
        print(' ');
    }

    private NopException newError(ErrorCode errorCode, EqlASTNode node) {
        return new NopException(errorCode).param(ARG_AST_NODE, node);
    }

    @Override
    public void visitSqlRegularFunction(SqlRegularFunction node) {
        ISQLFunction func = node.getResolvedFunction();
        SqlExprList funcExpr = func.buildFunctionExpr(node.getLocation(), node.getArgs(), dialect);
        this.printExprList(funcExpr);
    }

    @Override
    public void visitSqlCastExpr(SqlCastExpr node) {
        SqlTypeExpr expr = node.getDataType();
        StdSqlType sqlType = StdSqlType.fromStdName(expr.getName());
        if (sqlType == null) {
            super.visitSqlCastExpr(node);
            return;
        }

        print("cast(");
        visit(node.getExpr());
        print(" as ");
        int precision = expr.getPrecision() <= 0 ? -1 : expr.getPrecision();
        int scale = expr.getScale();
        if (precision < 0 && sqlType == StdSqlType.VARCHAR) {
            print("VARCHAR");
        } else {
            SQLDataType dataType = dialect.stdToNativeSqlType(sqlType, precision, scale);
            print(dataType.toString());
        }
        print(")");
    }

    @Override
    public void visitSqlLikeExpr(SqlLikeExpr node) {
        if (!node.getIgnoreCase()) {
            super.visitSqlLikeExpr(node);
            return;
        }

        ISQLFunction func = dialect.getFunction(SqlOperator.ILIKE.getText());
        if (func != null) {
            SqlExprList expr = func.buildFunctionExpr(node.getLocation(), Arrays.asList(node.getExpr(), node.getValue()), dialect);
            printExprList(expr);
        } else {
            LOG.debug("nop.orm.dialect-not-support-ilike,so-use-like-instead:{}",node);
            printBinaryExpr(node.getExpr(), SqlOperator.LIKE, node.getValue());
        }
    }
}