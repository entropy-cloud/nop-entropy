// Nop Generated from Eql.g4 by ANTLR 4.10.1
package io.nop.orm.eql.parse.antlr;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link EqlParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface EqlVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlProgram}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlProgram(EqlParser.SqlProgramContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlStatements_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlStatements_(EqlParser.SqlStatements_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlStatement(EqlParser.SqlStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlDmlStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlDmlStatement(EqlParser.SqlDmlStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlTransactionStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlTransactionStatement(EqlParser.SqlTransactionStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlCommit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlCommit(EqlParser.SqlCommitContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlRollback}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlRollback(EqlParser.SqlRollbackContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlInsert}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlInsert(EqlParser.SqlInsertContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlUpdate}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlUpdate(EqlParser.SqlUpdateContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlAssignments_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlAssignments_(EqlParser.SqlAssignments_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlAssignment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlAssignment(EqlParser.SqlAssignmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlValues}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlValues(EqlParser.SqlValuesContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlValues_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlValues_(EqlParser.SqlValues_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlDelete}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlDelete(EqlParser.SqlDeleteContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlSelectWithCte}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlSelectWithCte(EqlParser.SqlSelectWithCteContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlCteStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlCteStatement(EqlParser.SqlCteStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlCteStatements_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlCteStatements_(EqlParser.SqlCteStatements_Context ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlUnionSelect_ex}
	 * labeled alternative in {@link EqlParser#sqlSelect}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlUnionSelect_ex(EqlParser.SqlUnionSelect_exContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlQuerySelect_ex}
	 * labeled alternative in {@link EqlParser#sqlSelect}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlQuerySelect_ex(EqlParser.SqlQuerySelect_exContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlSelect_ex}
	 * labeled alternative in {@link EqlParser#sqlSelect}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlSelect_ex(EqlParser.SqlSelect_exContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#unionType_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnionType_(EqlParser.UnionType_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlQuerySelect}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlQuerySelect(EqlParser.SqlQuerySelectContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlProjections_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlProjections_(EqlParser.SqlProjections_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlProjection}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlProjection(EqlParser.SqlProjectionContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlExprProjection}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlExprProjection(EqlParser.SqlExprProjectionContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlAllProjection}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlAllProjection(EqlParser.SqlAllProjectionContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlAlias}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlAlias(EqlParser.SqlAliasContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlAlias_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlAlias_(EqlParser.SqlAlias_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlFrom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlFrom(EqlParser.SqlFromContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#tableSources_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTableSources_(EqlParser.TableSources_Context ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlJoinTableSource}
	 * labeled alternative in {@link EqlParser#sqlTableSource}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlJoinTableSource(EqlParser.SqlJoinTableSourceContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlSingleTableSource_ex}
	 * labeled alternative in {@link EqlParser#sqlTableSource}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlSingleTableSource_ex(EqlParser.SqlSingleTableSource_exContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlSubqueryTableSource_ex}
	 * labeled alternative in {@link EqlParser#sqlTableSource}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlSubqueryTableSource_ex(EqlParser.SqlSubqueryTableSource_exContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlSingleTableSource}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlSingleTableSource(EqlParser.SqlSingleTableSourceContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlSubqueryTableSource}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlSubqueryTableSource(EqlParser.SqlSubqueryTableSourceContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#joinType_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitJoinType_(EqlParser.JoinType_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlTableSource_joinRight}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlTableSource_joinRight(EqlParser.SqlTableSource_joinRightContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#innerJoin_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInnerJoin_(EqlParser.InnerJoin_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#fullJoin_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFullJoin_(EqlParser.FullJoin_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#leftJoin_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLeftJoin_(EqlParser.LeftJoin_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#rightJoin_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRightJoin_(EqlParser.RightJoin_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlWhere}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlWhere(EqlParser.SqlWhereContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlGroupBy}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlGroupBy(EqlParser.SqlGroupByContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlGroupByItems_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlGroupByItems_(EqlParser.SqlGroupByItems_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlHaving}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlHaving(EqlParser.SqlHavingContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlLimit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlLimit(EqlParser.SqlLimitContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlExpr_limitRowCount}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlExpr_limitRowCount(EqlParser.SqlExpr_limitRowCountContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlExpr_limitOffset}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlExpr_limitOffset(EqlParser.SqlExpr_limitOffsetContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlSubQueryExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlSubQueryExpr(EqlParser.SqlSubQueryExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#forUpdate_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForUpdate_(EqlParser.ForUpdate_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlParameterMarker}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlParameterMarker(EqlParser.SqlParameterMarkerContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlLiteral(EqlParser.SqlLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlStringLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlStringLiteral(EqlParser.SqlStringLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlNumberLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlNumberLiteral(EqlParser.SqlNumberLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlDateTimeLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlDateTimeLiteral(EqlParser.SqlDateTimeLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlHexadecimalLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlHexadecimalLiteral(EqlParser.SqlHexadecimalLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlBitValueLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlBitValueLiteral(EqlParser.SqlBitValueLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlBooleanLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlBooleanLiteral(EqlParser.SqlBooleanLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlNullLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlNullLiteral(EqlParser.SqlNullLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlIdentifier_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlIdentifier_(EqlParser.SqlIdentifier_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#unreservedWord_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnreservedWord_(EqlParser.UnreservedWord_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlTableName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlTableName(EqlParser.SqlTableNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlColumnName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlColumnName(EqlParser.SqlColumnNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlQualifiedName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlQualifiedName(EqlParser.SqlQualifiedNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#columnNames_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitColumnNames_(EqlParser.ColumnNames_Context ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlExpr_primary2}
	 * labeled alternative in {@link EqlParser#sqlExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlExpr_primary2(EqlParser.SqlExpr_primary2Context ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlOrExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlOrExpr(EqlParser.SqlOrExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlNotExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlNotExpr(EqlParser.SqlNotExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlAndExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlAndExpr(EqlParser.SqlAndExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlBinaryExpr_compare}
	 * labeled alternative in {@link EqlParser#sqlExpr_primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlBinaryExpr_compare(EqlParser.SqlBinaryExpr_compareContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlExpr_predicate2}
	 * labeled alternative in {@link EqlParser#sqlExpr_primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlExpr_predicate2(EqlParser.SqlExpr_predicate2Context ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlIsNullExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlIsNullExpr(EqlParser.SqlIsNullExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlCompareWithQueryExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlCompareWithQueryExpr(EqlParser.SqlCompareWithQueryExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#comparisonOperator_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparisonOperator_(EqlParser.ComparisonOperator_Context ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlInQueryExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlInQueryExpr(EqlParser.SqlInQueryExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlInValuesExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlInValuesExpr(EqlParser.SqlInValuesExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlBetweenExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlBetweenExpr(EqlParser.SqlBetweenExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlLikeExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlLikeExpr(EqlParser.SqlLikeExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlExpr_bit2}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlExpr_bit2(EqlParser.SqlExpr_bit2Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlInValues_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlInValues_(EqlParser.SqlInValues_Context ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlBinaryExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_bit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlBinaryExpr(EqlParser.SqlBinaryExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SqlExpr_simple2}
	 * labeled alternative in {@link EqlParser#sqlExpr_bit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlExpr_simple2(EqlParser.SqlExpr_simple2Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlExpr_simple}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlExpr_simple(EqlParser.SqlExpr_simpleContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlUnaryExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlUnaryExpr(EqlParser.SqlUnaryExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlExpr_brace}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlExpr_brace(EqlParser.SqlExpr_braceContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlMultiValueExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlMultiValueExpr(EqlParser.SqlMultiValueExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlExistsExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlExistsExpr(EqlParser.SqlExistsExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlExpr_functionCall}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlExpr_functionCall(EqlParser.SqlExpr_functionCallContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlAggregateFunction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlAggregateFunction(EqlParser.SqlAggregateFunctionContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlWindowExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlWindowExpr(EqlParser.SqlWindowExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlWindowFunction_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlWindowFunction_(EqlParser.SqlWindowFunction_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlPartitionBy}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlPartitionBy(EqlParser.SqlPartitionByContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlPartitionByItems_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlPartitionByItems_(EqlParser.SqlPartitionByItems_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlIdentifier_agg_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlIdentifier_agg_(EqlParser.SqlIdentifier_agg_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#distinct_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDistinct_(EqlParser.Distinct_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#functionArgs_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionArgs_(EqlParser.FunctionArgs_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlExpr_special}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlExpr_special(EqlParser.SqlExpr_specialContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlCastExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlCastExpr(EqlParser.SqlCastExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlRegularFunction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlRegularFunction(EqlParser.SqlRegularFunctionContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlIdentifier_func_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlIdentifier_func_(EqlParser.SqlIdentifier_func_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlDecorators_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlDecorators_(EqlParser.SqlDecorators_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlDecorator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlDecorator(EqlParser.SqlDecoratorContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#decoratorArgs_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDecoratorArgs_(EqlParser.DecoratorArgs_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlCaseExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlCaseExpr(EqlParser.SqlCaseExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#caseWhens_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCaseWhens_(EqlParser.CaseWhens_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlCaseWhenItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlCaseWhenItem(EqlParser.SqlCaseWhenItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlIntervalExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlIntervalExpr(EqlParser.SqlIntervalExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#intervalUnit_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntervalUnit_(EqlParser.IntervalUnit_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlOrderBy}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlOrderBy(EqlParser.SqlOrderByContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlOrderByItems_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlOrderByItems_(EqlParser.SqlOrderByItems_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlOrderByItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlOrderByItem(EqlParser.SqlOrderByItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlGroupByItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlGroupByItem(EqlParser.SqlGroupByItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#sqlTypeExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqlTypeExpr(EqlParser.SqlTypeExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#dataTypeName_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDataTypeName_(EqlParser.DataTypeName_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#characterSet_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCharacterSet_(EqlParser.CharacterSet_Context ctx);
	/**
	 * Visit a parse tree produced by {@link EqlParser#collateClause_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCollateClause_(EqlParser.CollateClause_Context ctx);
}