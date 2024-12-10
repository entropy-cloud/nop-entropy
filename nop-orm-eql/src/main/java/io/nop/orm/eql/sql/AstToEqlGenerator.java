/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.sql;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.ISqlExpr;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SqlExprList;
import io.nop.core.lang.sql.StringSqlExpr;
import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.EqlASTVisitor;
import io.nop.orm.eql.ast.SqlAggregateFunction;
import io.nop.orm.eql.ast.SqlAlias;
import io.nop.orm.eql.ast.SqlAllProjection;
import io.nop.orm.eql.ast.SqlAndExpr;
import io.nop.orm.eql.ast.SqlAssignment;
import io.nop.orm.eql.ast.SqlBetweenExpr;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlBitValueLiteral;
import io.nop.orm.eql.ast.SqlBooleanLiteral;
import io.nop.orm.eql.ast.SqlCaseExpr;
import io.nop.orm.eql.ast.SqlCaseWhenItem;
import io.nop.orm.eql.ast.SqlCastExpr;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlCompareWithQueryExpr;
import io.nop.orm.eql.ast.SqlCteStatement;
import io.nop.orm.eql.ast.SqlDateTimeLiteral;
import io.nop.orm.eql.ast.SqlDelete;
import io.nop.orm.eql.ast.SqlExistsExpr;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlFrom;
import io.nop.orm.eql.ast.SqlGroupBy;
import io.nop.orm.eql.ast.SqlGroupByItem;
import io.nop.orm.eql.ast.SqlHaving;
import io.nop.orm.eql.ast.SqlHexadecimalLiteral;
import io.nop.orm.eql.ast.SqlInQueryExpr;
import io.nop.orm.eql.ast.SqlInValuesExpr;
import io.nop.orm.eql.ast.SqlInsert;
import io.nop.orm.eql.ast.SqlIntervalExpr;
import io.nop.orm.eql.ast.SqlIsNullExpr;
import io.nop.orm.eql.ast.SqlJoinTableSource;
import io.nop.orm.eql.ast.SqlLikeExpr;
import io.nop.orm.eql.ast.SqlLimit;
import io.nop.orm.eql.ast.SqlMultiValueExpr;
import io.nop.orm.eql.ast.SqlNotExpr;
import io.nop.orm.eql.ast.SqlNullLiteral;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlOrExpr;
import io.nop.orm.eql.ast.SqlOrderBy;
import io.nop.orm.eql.ast.SqlOrderByItem;
import io.nop.orm.eql.ast.SqlParameterMarker;
import io.nop.orm.eql.ast.SqlPartitionBy;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQualifiedName;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlRegularFunction;
import io.nop.orm.eql.ast.SqlSelectWithCte;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlStringLiteral;
import io.nop.orm.eql.ast.SqlSubQueryExpr;
import io.nop.orm.eql.ast.SqlSubqueryTableSource;
import io.nop.orm.eql.ast.SqlTableName;
import io.nop.orm.eql.ast.SqlTypeExpr;
import io.nop.orm.eql.ast.SqlUnaryExpr;
import io.nop.orm.eql.ast.SqlUnionSelect;
import io.nop.orm.eql.ast.SqlUpdate;
import io.nop.orm.eql.ast.SqlValues;
import io.nop.orm.eql.ast.SqlWhere;
import io.nop.orm.eql.ast.SqlWindowExpr;
import io.nop.orm.eql.enums.SqlCompareRange;
import io.nop.orm.eql.enums.SqlOperator;

import java.util.List;

public class AstToEqlGenerator extends EqlASTVisitor {
    protected final SQL.SqlBuilder sb;

    private int indentLevel;
    private boolean pretty = true;

    protected String ownerShouldBeIgnored;

    public AstToEqlGenerator(SQL.SqlBuilder sb) {
        this.sb = sb;
    }

    public AstToEqlGenerator() {
        this(SQL.begin());
    }

    public boolean isPretty() {
        return pretty;
    }

    public void setPretty(boolean pretty) {
        this.pretty = pretty;
    }

    public AstToEqlGenerator pretty(boolean pretty) {
        this.pretty = pretty;
        return this;
    }

    public SQL.SqlBuilder getSql() {
        return sb;
    }

    protected void print(char c) {
        sb.append(c);
    }

    protected void print(String text) {
        sb.append(text);
    }

    protected void beginBlock() {
        incIndent();
        println();
    }

    protected void beginBraceBlock() {
        print('(');
        beginBlock();
    }

    protected void endBraceBlock() {
        decIndent();
        println();
        print(')');
    }

    protected void endBlock() {
        decIndent();
        println();
    }

    protected void printAlias(SqlAlias alias) {
        if (alias != null) {
            print(" as ");
            print(alias.getAlias());
        }
    }

    void incIndent() {
        indentLevel++;
    }

    void decIndent() {
        indentLevel--;
    }

    void indent() {
        if (pretty) {
            for (int i = 0; i < this.indentLevel; ++i) {
                print("  ");
            }
        }
    }

    void println() {
        print("\n");
        indent();
    }

    void printLeft(EqlASTNode node, SqlOperator operator) {
        int priority = operator.getPriority();
        if (node.requireParentheses(priority, priority)) {
            sb.append("(");
            visit(node);
            sb.append(")");
        } else {
            visit(node);
        }
    }

    void printRight(EqlASTNode node, SqlOperator operator) {
        int priority = operator.getPriority();
        if (node.requireParentheses(priority, priority)) {
            sb.append("(");
            visit(node);
            sb.append(")");
        } else {
            visit(node);
        }
    }

    void printOperator(SqlOperator operator) {
        print(' ');
        print(operator.getText());
        print(' ');
    }

    void printBinaryExpr(SqlExpr left, SqlOperator operator, SqlExpr right) {
        printLeft(left, operator);
        printOperator(operator);
        printRight(right, operator);
    }

    protected void printList(List<? extends EqlASTNode> list, String separator) {
        if (list != null) {
            for (int i = 0, n = list.size(); i < n; i++) {
                if (i != 0) {
                    print(separator);
                    if (pretty)
                        println();
                }
                visit(list.get(i));
            }
        }
    }

    void printExprList(SqlExprList exprList) {
        for (ISqlExpr expr : exprList.getExprs()) {
            if (expr instanceof EqlASTNode) {
                visit((EqlASTNode) expr);
            } else if (expr instanceof StringSqlExpr) {
                print(((StringSqlExpr) expr).getSqlString());
            } else if (expr instanceof SqlNodeWrapper) {
                printWrappedNode((SqlNodeWrapper) expr);
            } else {
                throw new IllegalArgumentException("not supported:" + expr);
            }
        }
    }

    protected void printWrappedNode(SqlNodeWrapper wrapper) {
        EqlASTNode expr = wrapper.getNode();
        if (expr instanceof SqlQuerySelect) {
            printSelect((SqlQuerySelect) expr);
        } else {
            visit(expr);
        }
    }

    protected static abstract class SqlNodeWrapper implements ISqlExpr {
        private final EqlASTNode node;

        public SqlNodeWrapper(EqlASTNode node) {
            this.node = node;
        }

        public EqlASTNode getNode() {
            return node;
        }

        @Override
        public abstract void appendTo(SQL.SqlBuilder buf);
    }

    protected String normalizeTableName(String tableName) {
        return tableName;
    }

    protected String normalizeColName(String colName) {
        return colName;
    }

    @Override
    public void visitSqlProgram(SqlProgram node) {
        printList(node.getStatements(), ";\n");
    }

    // @Override
    // public void visitSqlIdentifier(SqlIdentifier node) {
    // print(dialect.escapeSQLName(node.getName()));
    // }

    @Override
    public void visitSqlQualifiedName(SqlQualifiedName node) {
        print(node.getFullName());
    }

    @Override
    public void visitSqlTableName(SqlTableName node) {
        sb.markTable(normalizeTableName(node.getFullName()), null, null, false);
    }

    @Override
    public void visitSqlColumnName(SqlColumnName node) {
        if (node.getOwner() != null && !node.getOwner().getFullName().equals(ownerShouldBeIgnored)) {
            visitSqlQualifiedName(node.getOwner());
            print('.');
        }
        String colName = normalizeColName(node.getName());
        print(colName);
    }

    @Override
    public void visitSqlInsert(SqlInsert node) {
        printInsertKeyword();

        print("into ");
        visitSqlTableName(node.getTableName());
        beginBraceBlock();
        printList(node.getColumns(), ",");
        endBraceBlock();
        if (node.getSelect() != null) {
            visit(node.getSelect());
        } else {
            visitSqlValues(node.getValues());
        }
    }

    protected void printInsertKeyword() {
        print("insert ");
    }

    @Override
    public void visitSqlValues(SqlValues node) {
        println();
        print("values(");
        beginBlock();
        printList(node.getValues(), ",");
        endBlock();
        println();
        print(")");
    }

    @Override
    public void visitSqlUpdate(SqlUpdate node) {
        if (node.getAlias() != null)
            this.ownerShouldBeIgnored = node.getAlias().getAlias();

        printUpdateKeyword();
        beginBlock();
        visitSqlTableName(node.getTableName());
        endBlock();
        print("set ");
        beginBlock();
        printList(node.getAssignments(), ",");
        endBlock();
        printWhere(node.getWhere());

        if (node.getReturnProjections() != null && !node.getReturnProjections().isEmpty()) {
            print(" returning ");
            printList(node.getReturnProjections(), ",");
        } else if (node.getReturnAll()) {
            print(" returning *");
        }
    }

    protected void printUpdateKeyword() {
        print("update");
    }

    @Override
    public void visitSqlAlias(SqlAlias node) {
        printAlias(node);
    }

    @Override
    public void visitSqlAssignment(SqlAssignment node) {
        visitSqlColumnName(node.getColumnName());
        print("=");
        printRight(node.getExpr(), SqlOperator.EQ);
    }

    @Override
    public void visitSqlDelete(SqlDelete node) {
        if (node.getAlias() != null)
            this.ownerShouldBeIgnored = node.getAlias().getAlias();

        print("delete\nfrom");
        beginBlock();
        visitSqlTableName(node.getTableName());
        endBlock();
        printWhere(node.getWhere());
    }

    protected void printWhere(SqlWhere where) {
        if (where != null) {
            visitSqlWhere(where);
        }
    }

    @Override
    public void visitSqlWhere(SqlWhere node) {
        if (node.getExpr() == null)
            return;
        print("where ");
        beginBlock();
        visit(node.getExpr());
        endBlock();
    }

    @Override
    public void visitSqlQuerySelect(SqlQuerySelect node) {
        printSelect(node);
    }

    @Override
    public void visitSqlSelectWithCte(SqlSelectWithCte node) {
        List<SqlCteStatement> withCtes = node.getWithCtes();
        if (withCtes != null && !withCtes.isEmpty()) {
            for (int i = 0, n = withCtes.size(); i < n; i++) {
                if (i != 0) {
                    println();
                    print(',');
                } else {
                    print("with ");
                }
                SqlCteStatement stm = withCtes.get(i);
                if (stm.getRecursive()) {
                    print(" recursive ");
                }
                print(stm.getName());
                appendWithColumns(stm);
                print(" as (");
                incIndent();
                println();
                visit(stm.getStatement());
                print(")");
                decIndent();
                println();
            }
        }
        visit(node.getSelect());
    }

    protected void appendWithColumns(SqlCteStatement stm) {

    }

    protected void printSelect(SqlQuerySelect node) {
        print("select ");
        beginBlock();
        if (node.getDistinct()) {
            print("distinct ");
        }
        printList(node.getProjections(), ",");
        endBlock();

        if (node.getFrom() != null) {
            visitSqlFrom(node.getFrom());
        }

        printWhere(node);

        if (node.getGroupBy() != null) {
            visitSqlGroupBy(node.getGroupBy());
        }

        if (node.getHaving() != null) {
            visitSqlHaving(node.getHaving());
        }

        if (node.getOrderBy() != null) {
            visitSqlOrderBy(node.getOrderBy());
        }

        if (node.getForUpdate()) {
            println();
            print("for update");
        }
    }

    protected void printWhere(SqlQuerySelect node) {
        if (node.getWhere() != null) {
            visitSqlWhere(node.getWhere());
        }
    }

    @Override
    public void visitSqlSubQueryExpr(SqlSubQueryExpr node) {
        beginBraceBlock();
        visit(node.getSelect());
        endBraceBlock();
    }

    @Override
    public void visitSqlParameterMarker(SqlParameterMarker node) {
        sb.markValue("?", null, node.isMasked());
    }

    @Override
    public void visitSqlHaving(SqlHaving node) {
        print(" having ");
        beginBlock();
        visit(node.getExpr());
        endBlock();
    }

//    @Override
//    public void visitSqlDecorator(SqlDecorator node) {
//        super.visitSqlDecorator(node);
//    }

    @Override
    public void visitSqlUnionSelect(SqlUnionSelect node) {
        beginBraceBlock();
        visit(node.getLeft());
        endBraceBlock();

        println();
        switch (node.getUnionType()) {
            case UNION_ALL:
                sb.append(" union all ");
                break;
            case INTERSECT_ALL:
                sb.append(" ").append(getIntercectKeyword()).append(" all ");
                break;
            case EXCEPT_ALL:
                sb.append(" ").append(getExceptKeyword()).append(" all ");
                break;
            case INTERSECT:
                sb.append(" ").append(getIntercectKeyword()).append(" ");
                break;
            case EXCEPT:
                sb.append(" ").append(getExceptKeyword()).append(" ");
                break;
            default:
                sb.append(" union ");
        }
        println();
        beginBraceBlock();
        visit(node.getRight());
        endBraceBlock();
    }

    protected String getIntercectKeyword() {
        return "intersect";
    }

    protected String getExceptKeyword() {
        return "except";
    }

    @Override
    public void visitSqlExprProjection(SqlExprProjection node) {
        visit(node.getExpr());
        visitSqlAlias(node.getAlias());
    }

    @Override
    public void visitSqlAllProjection(SqlAllProjection node) {
        if (node.getOwner() != null) {
            visitSqlQualifiedName(node.getOwner());
            print(".");
        }
        print("*");
    }

    @Override
    public void visitSqlOrderBy(SqlOrderBy node) {
        print(" order by ");
        beginBlock();
        printList(node.getItems(), ",");
        endBlock();
    }

    @Override
    public void visitSqlGroupBy(SqlGroupBy node) {
        print("group by ");
        beginBlock();
        printList(node.getItems(), ",");
        endBlock();
    }

    @Override
    public void visitSqlGroupByItem(SqlGroupByItem node) {
        visit(node.getExpr());
    }

    @Override
    public void visitSqlOrderByItem(SqlOrderByItem node) {
        visit(node.getExpr());
        if (node.getAsc()) {
            print(" asc ");
        } else {
            print(" desc ");
        }
        if (node.getNullsFirst() != null) {
            if (node.getNullsFirst()) {
                print(" nulls first ");
            } else {
                print(" nulls last ");
            }
        }
    }

    @Override
    public void visitSqlLimit(SqlLimit node) {
        if (node.getLimit() != null) {
            print("limit ");
            visit(node.getLimit());
        }
        if (node.getOffset() != null) {
            print(" offset ");
            visit(node.getOffset());
        }
    }

    @Override
    public void visitSqlFrom(SqlFrom node) {
        print("from ");
        beginBlock();
        printList(node.getTableSources(), ",");
        endBlock();
    }

    @Override
    public void visitSqlSingleTableSource(SqlSingleTableSource node) {
        visit(node.getTableName());
        printAlias(node.getAlias());
    }

    @Override
    public void visitSqlJoinTableSource(SqlJoinTableSource node) {
        visit(node.getLeft());
        print(" ");
        incIndent();
        println();
        print(node.getJoinType().getText());
        print(" ");
        visit(node.getRight());
        printJoin(node);
        decIndent();
    }

    protected void printJoin(SqlJoinTableSource node) {
        if (node.getCondition() != null) {
            print(" on ");
            visit(node.getCondition());
        }
    }

    @Override
    public void visitSqlSubqueryTableSource(SqlSubqueryTableSource node) {
        print('(');
        beginBlock();
        visit(node.getQuery());
        endBlock();
        print(')');
        printAlias(node.getAlias());
    }

    // @Override
    // public void visitSqlSubQuery(SqlSubQuery node) {
    // print("(");
    // indent();
    // println();
    // visit(node.getSelect());
    // print(")");
    // }

    @Override
    public void visitSqlNotExpr(SqlNotExpr node) {
        printOperator(SqlOperator.NOT);
        print('(');
        println();
        incIndent();
        visit(node.getExpr());
        decIndent();
        println();
        print(')');
    }

    @Override
    public void visitSqlAndExpr(SqlAndExpr node) {
        printBinaryExpr(node.getLeft(), SqlOperator.AND, node.getRight());
    }

    @Override
    public void visitSqlOrExpr(SqlOrExpr node) {
        if (node.getASTParent().getASTKind() == EqlASTKind.SqlOrExpr) {
            printBinaryExpr(node.getLeft(), SqlOperator.OR, node.getRight());
        } else {
            print('(');
            printBinaryExpr(node.getLeft(), SqlOperator.OR, node.getRight());
            print(')');
        }
    }

    @Override
    public void visitSqlStringLiteral(SqlStringLiteral node) {
        String str = " '" + StringHelper.escapeSql(node.getValue(), false) + "' ";
        print(str);
    }

    @Override
    public void visitSqlNumberLiteral(SqlNumberLiteral node) {
        print(' ');
        print(node.getValue());
        print(' ');
    }

    @Override
    public void visitSqlDateTimeLiteral(SqlDateTimeLiteral node) {
        print(' ');
        print(node.getType().getText());
        print(" '");
        print(node.getValue());
        print("' ");
    }

    @Override
    public void visitSqlHexadecimalLiteral(SqlHexadecimalLiteral node) {
        print(" X'");
        print(node.getValue());
        print("' ");
    }

    @Override
    public void visitSqlBitValueLiteral(SqlBitValueLiteral node) {
        print(" B'");
        print(node.getValue());
        print("' ");
    }

    @Override
    public void visitSqlBooleanLiteral(SqlBooleanLiteral node) {
        print(node.getValue() ? " 1 " : " 0 ");
    }

    @Override
    public void visitSqlNullLiteral(SqlNullLiteral node) {
        print(" null ");
    }

    @Override
    public void visitSqlBinaryExpr(SqlBinaryExpr node) {
        printBinaryExpr(node.getLeft(), node.getOperator(), node.getRight());
    }

    @Override
    public void visitSqlIsNullExpr(SqlIsNullExpr node) {
        printLeft(node.getExpr(), SqlOperator.IS);
        if (node.getNot()) {
            print(" is not null ");
        } else {
            print(" is null ");
        }
    }

    @Override
    public void visitSqlCompareWithQueryExpr(SqlCompareWithQueryExpr node) {
        printLeft(node.getExpr(), node.getOperator());
        printOperator(node.getOperator());
        if (node.getCompareRange() != null) {
            print(' ');
            printCompareRange(node.getCompareRange());
            print(' ');
        }
        visit(node.getQuery());
    }

    protected void printCompareRange(SqlCompareRange compareRange) {
        print(compareRange.name());
    }

    @Override
    public void visitSqlInQueryExpr(SqlInQueryExpr node) {
        visit(node.getExpr());

        if (node.getNot()) {
            print(" not ");
        }

        print(" in ");
        visit(node.getQuery());
    }

    @Override
    public void visitSqlInValuesExpr(SqlInValuesExpr node) {
        visit(node.getExpr());

        if (node.getNot()) {
            print(" not ");
        }
        print(" in ");

        print("(");
        incIndent();
        println();
        printList(node.getValues(), ",");
        decIndent();
        println();

        print(')');
    }

    @Override
    public void visitSqlBetweenExpr(SqlBetweenExpr node) {
        visit(node.getTest());

        if (node.getNot()) {
            print(" not");
        }
        print(" between ");
        visit(node.getBegin());
        print(" and ");
        visit(node.getEnd());
    }

    @Override
    public void visitSqlLikeExpr(SqlLikeExpr node) {
        printBinaryExpr(node.getExpr(), node.getIgnoreCase() ? SqlOperator.ILIKE : SqlOperator.LIKE, node.getValue());
    }

    @Override
    public void visitSqlUnaryExpr(SqlUnaryExpr node) {
        printOperator(node.getOperator());
        printRight(node.getExpr(), node.getOperator());
    }

    @Override
    public void visitSqlAggregateFunction(SqlAggregateFunction node) {
        print(node.getName());
        print("(");
        if (node.getDistinct()) {
            print(" distinct ");
        }
        if (node.getSelectAll()) {
            print("*");
        } else {
            printList(node.getArgs(), ",");
        }
        print(')');
    }

    @Override
    public void visitSqlRegularFunction(SqlRegularFunction node) {
        print(node.getName());
        print('(');
        printList(node.getArgs(), ",");
        print(')');
    }

    @Override
    public void visitSqlMultiValueExpr(SqlMultiValueExpr node) {
        print("(");
        printList(node.getValues(), ",");
        print(")");
    }

    @Override
    public void visitSqlExistsExpr(SqlExistsExpr node) {
        if (node.getNot()) {
            print(" not ");
        }
        print(" exists ");
        visit(node.getQuery());
    }

    @Override
    public void visitSqlIntervalExpr(SqlIntervalExpr node) {
        print(" interval ");
        visit(node.getExpr());
        print(" ");
        print(node.getIntervalUnit().name());
    }

    @Override
    public void visitSqlCaseExpr(SqlCaseExpr node) {
        print("case ");
        if (node.getTest() != null) {
            visit(node.getTest());
            print(" ");
        }

        printList(node.getCaseWhens(), " ");

        if (node.getElseExpr() != null) {
            print(" else ");
            visit(node.getElseExpr());
        }

        print(" end");
    }

    @Override
    public void visitSqlCaseWhenItem(SqlCaseWhenItem node) {
        print("when ");
        visit(node.getWhen());
        print(" then ");
        visit(node.getThen());
    }

    @Override
    public void visitSqlCastExpr(SqlCastExpr node) {
        print("cast(");
        visit(node.getExpr());
        print(" as ");
        visit(node.getDataType());
        print(")");
    }

    @Override
    public void visitSqlTypeExpr(SqlTypeExpr node) {
        String typeName = node.getName();
        print(typeName);

        if (node.getPrecision() > 0) {
            print('(');
            print(String.valueOf(node.getPrecision()));
            if (node.getScale() > 0) {
                print(",");
                print(String.valueOf(node.getScale()));
            }
            print(')');
        }
    }

    @Override
    public void visitSqlWindowExpr(SqlWindowExpr node) {
        this.visitChild(node.getFunction());
        print(" over( ");
        this.visitChild(node.getPartitionBy());
        this.visitChild(node.getOrderBy());
        print(") ");
    }

    @Override
    public void visitSqlPartitionBy(SqlPartitionBy node) {
        print(" partition by ");
        beginBlock();
        printList(node.getItems(), ",");
        endBlock();
    }
}