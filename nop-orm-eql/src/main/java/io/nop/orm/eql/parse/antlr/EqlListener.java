// Nop Generated from Eql.g4 by ANTLR 4.10.1
package io.nop.orm.eql.parse.antlr;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link EqlParser}.
 */
public interface EqlListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlProgram}.
	 * @param ctx the parse tree
	 */
	void enterSqlProgram(EqlParser.SqlProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlProgram}.
	 * @param ctx the parse tree
	 */
	void exitSqlProgram(EqlParser.SqlProgramContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlStatements_}.
	 * @param ctx the parse tree
	 */
	void enterSqlStatements_(EqlParser.SqlStatements_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlStatements_}.
	 * @param ctx the parse tree
	 */
	void exitSqlStatements_(EqlParser.SqlStatements_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlStatement}.
	 * @param ctx the parse tree
	 */
	void enterSqlStatement(EqlParser.SqlStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlStatement}.
	 * @param ctx the parse tree
	 */
	void exitSqlStatement(EqlParser.SqlStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlDmlStatement}.
	 * @param ctx the parse tree
	 */
	void enterSqlDmlStatement(EqlParser.SqlDmlStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlDmlStatement}.
	 * @param ctx the parse tree
	 */
	void exitSqlDmlStatement(EqlParser.SqlDmlStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlTransactionStatement}.
	 * @param ctx the parse tree
	 */
	void enterSqlTransactionStatement(EqlParser.SqlTransactionStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlTransactionStatement}.
	 * @param ctx the parse tree
	 */
	void exitSqlTransactionStatement(EqlParser.SqlTransactionStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlCommit}.
	 * @param ctx the parse tree
	 */
	void enterSqlCommit(EqlParser.SqlCommitContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlCommit}.
	 * @param ctx the parse tree
	 */
	void exitSqlCommit(EqlParser.SqlCommitContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlRollback}.
	 * @param ctx the parse tree
	 */
	void enterSqlRollback(EqlParser.SqlRollbackContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlRollback}.
	 * @param ctx the parse tree
	 */
	void exitSqlRollback(EqlParser.SqlRollbackContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlInsert}.
	 * @param ctx the parse tree
	 */
	void enterSqlInsert(EqlParser.SqlInsertContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlInsert}.
	 * @param ctx the parse tree
	 */
	void exitSqlInsert(EqlParser.SqlInsertContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlUpdate}.
	 * @param ctx the parse tree
	 */
	void enterSqlUpdate(EqlParser.SqlUpdateContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlUpdate}.
	 * @param ctx the parse tree
	 */
	void exitSqlUpdate(EqlParser.SqlUpdateContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlAssignments_}.
	 * @param ctx the parse tree
	 */
	void enterSqlAssignments_(EqlParser.SqlAssignments_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlAssignments_}.
	 * @param ctx the parse tree
	 */
	void exitSqlAssignments_(EqlParser.SqlAssignments_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlAssignment}.
	 * @param ctx the parse tree
	 */
	void enterSqlAssignment(EqlParser.SqlAssignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlAssignment}.
	 * @param ctx the parse tree
	 */
	void exitSqlAssignment(EqlParser.SqlAssignmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlValues}.
	 * @param ctx the parse tree
	 */
	void enterSqlValues(EqlParser.SqlValuesContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlValues}.
	 * @param ctx the parse tree
	 */
	void exitSqlValues(EqlParser.SqlValuesContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlValues_}.
	 * @param ctx the parse tree
	 */
	void enterSqlValues_(EqlParser.SqlValues_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlValues_}.
	 * @param ctx the parse tree
	 */
	void exitSqlValues_(EqlParser.SqlValues_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlDelete}.
	 * @param ctx the parse tree
	 */
	void enterSqlDelete(EqlParser.SqlDeleteContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlDelete}.
	 * @param ctx the parse tree
	 */
	void exitSqlDelete(EqlParser.SqlDeleteContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlSelectWithCte}.
	 * @param ctx the parse tree
	 */
	void enterSqlSelectWithCte(EqlParser.SqlSelectWithCteContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlSelectWithCte}.
	 * @param ctx the parse tree
	 */
	void exitSqlSelectWithCte(EqlParser.SqlSelectWithCteContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlCteStatement}.
	 * @param ctx the parse tree
	 */
	void enterSqlCteStatement(EqlParser.SqlCteStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlCteStatement}.
	 * @param ctx the parse tree
	 */
	void exitSqlCteStatement(EqlParser.SqlCteStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlCteStatements_}.
	 * @param ctx the parse tree
	 */
	void enterSqlCteStatements_(EqlParser.SqlCteStatements_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlCteStatements_}.
	 * @param ctx the parse tree
	 */
	void exitSqlCteStatements_(EqlParser.SqlCteStatements_Context ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlUnionSelect_ex}
	 * labeled alternative in {@link EqlParser#sqlSelect}.
	 * @param ctx the parse tree
	 */
	void enterSqlUnionSelect_ex(EqlParser.SqlUnionSelect_exContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlUnionSelect_ex}
	 * labeled alternative in {@link EqlParser#sqlSelect}.
	 * @param ctx the parse tree
	 */
	void exitSqlUnionSelect_ex(EqlParser.SqlUnionSelect_exContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlQuerySelect_ex}
	 * labeled alternative in {@link EqlParser#sqlSelect}.
	 * @param ctx the parse tree
	 */
	void enterSqlQuerySelect_ex(EqlParser.SqlQuerySelect_exContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlQuerySelect_ex}
	 * labeled alternative in {@link EqlParser#sqlSelect}.
	 * @param ctx the parse tree
	 */
	void exitSqlQuerySelect_ex(EqlParser.SqlQuerySelect_exContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlSelect_ex}
	 * labeled alternative in {@link EqlParser#sqlSelect}.
	 * @param ctx the parse tree
	 */
	void enterSqlSelect_ex(EqlParser.SqlSelect_exContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlSelect_ex}
	 * labeled alternative in {@link EqlParser#sqlSelect}.
	 * @param ctx the parse tree
	 */
	void exitSqlSelect_ex(EqlParser.SqlSelect_exContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#unionType_}.
	 * @param ctx the parse tree
	 */
	void enterUnionType_(EqlParser.UnionType_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#unionType_}.
	 * @param ctx the parse tree
	 */
	void exitUnionType_(EqlParser.UnionType_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlQuerySelect}.
	 * @param ctx the parse tree
	 */
	void enterSqlQuerySelect(EqlParser.SqlQuerySelectContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlQuerySelect}.
	 * @param ctx the parse tree
	 */
	void exitSqlQuerySelect(EqlParser.SqlQuerySelectContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlProjections_}.
	 * @param ctx the parse tree
	 */
	void enterSqlProjections_(EqlParser.SqlProjections_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlProjections_}.
	 * @param ctx the parse tree
	 */
	void exitSqlProjections_(EqlParser.SqlProjections_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlProjection}.
	 * @param ctx the parse tree
	 */
	void enterSqlProjection(EqlParser.SqlProjectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlProjection}.
	 * @param ctx the parse tree
	 */
	void exitSqlProjection(EqlParser.SqlProjectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlExprProjection}.
	 * @param ctx the parse tree
	 */
	void enterSqlExprProjection(EqlParser.SqlExprProjectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlExprProjection}.
	 * @param ctx the parse tree
	 */
	void exitSqlExprProjection(EqlParser.SqlExprProjectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlAllProjection}.
	 * @param ctx the parse tree
	 */
	void enterSqlAllProjection(EqlParser.SqlAllProjectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlAllProjection}.
	 * @param ctx the parse tree
	 */
	void exitSqlAllProjection(EqlParser.SqlAllProjectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlAlias}.
	 * @param ctx the parse tree
	 */
	void enterSqlAlias(EqlParser.SqlAliasContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlAlias}.
	 * @param ctx the parse tree
	 */
	void exitSqlAlias(EqlParser.SqlAliasContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlAlias_}.
	 * @param ctx the parse tree
	 */
	void enterSqlAlias_(EqlParser.SqlAlias_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlAlias_}.
	 * @param ctx the parse tree
	 */
	void exitSqlAlias_(EqlParser.SqlAlias_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlFrom}.
	 * @param ctx the parse tree
	 */
	void enterSqlFrom(EqlParser.SqlFromContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlFrom}.
	 * @param ctx the parse tree
	 */
	void exitSqlFrom(EqlParser.SqlFromContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#tableSources_}.
	 * @param ctx the parse tree
	 */
	void enterTableSources_(EqlParser.TableSources_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#tableSources_}.
	 * @param ctx the parse tree
	 */
	void exitTableSources_(EqlParser.TableSources_Context ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlJoinTableSource}
	 * labeled alternative in {@link EqlParser#sqlTableSource}.
	 * @param ctx the parse tree
	 */
	void enterSqlJoinTableSource(EqlParser.SqlJoinTableSourceContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlJoinTableSource}
	 * labeled alternative in {@link EqlParser#sqlTableSource}.
	 * @param ctx the parse tree
	 */
	void exitSqlJoinTableSource(EqlParser.SqlJoinTableSourceContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlSingleTableSource_ex}
	 * labeled alternative in {@link EqlParser#sqlTableSource}.
	 * @param ctx the parse tree
	 */
	void enterSqlSingleTableSource_ex(EqlParser.SqlSingleTableSource_exContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlSingleTableSource_ex}
	 * labeled alternative in {@link EqlParser#sqlTableSource}.
	 * @param ctx the parse tree
	 */
	void exitSqlSingleTableSource_ex(EqlParser.SqlSingleTableSource_exContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlSubqueryTableSource_ex}
	 * labeled alternative in {@link EqlParser#sqlTableSource}.
	 * @param ctx the parse tree
	 */
	void enterSqlSubqueryTableSource_ex(EqlParser.SqlSubqueryTableSource_exContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlSubqueryTableSource_ex}
	 * labeled alternative in {@link EqlParser#sqlTableSource}.
	 * @param ctx the parse tree
	 */
	void exitSqlSubqueryTableSource_ex(EqlParser.SqlSubqueryTableSource_exContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlSingleTableSource}.
	 * @param ctx the parse tree
	 */
	void enterSqlSingleTableSource(EqlParser.SqlSingleTableSourceContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlSingleTableSource}.
	 * @param ctx the parse tree
	 */
	void exitSqlSingleTableSource(EqlParser.SqlSingleTableSourceContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlSubqueryTableSource}.
	 * @param ctx the parse tree
	 */
	void enterSqlSubqueryTableSource(EqlParser.SqlSubqueryTableSourceContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlSubqueryTableSource}.
	 * @param ctx the parse tree
	 */
	void exitSqlSubqueryTableSource(EqlParser.SqlSubqueryTableSourceContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#joinType_}.
	 * @param ctx the parse tree
	 */
	void enterJoinType_(EqlParser.JoinType_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#joinType_}.
	 * @param ctx the parse tree
	 */
	void exitJoinType_(EqlParser.JoinType_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlTableSource_joinRight}.
	 * @param ctx the parse tree
	 */
	void enterSqlTableSource_joinRight(EqlParser.SqlTableSource_joinRightContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlTableSource_joinRight}.
	 * @param ctx the parse tree
	 */
	void exitSqlTableSource_joinRight(EqlParser.SqlTableSource_joinRightContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#innerJoin_}.
	 * @param ctx the parse tree
	 */
	void enterInnerJoin_(EqlParser.InnerJoin_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#innerJoin_}.
	 * @param ctx the parse tree
	 */
	void exitInnerJoin_(EqlParser.InnerJoin_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#fullJoin_}.
	 * @param ctx the parse tree
	 */
	void enterFullJoin_(EqlParser.FullJoin_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#fullJoin_}.
	 * @param ctx the parse tree
	 */
	void exitFullJoin_(EqlParser.FullJoin_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#leftJoin_}.
	 * @param ctx the parse tree
	 */
	void enterLeftJoin_(EqlParser.LeftJoin_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#leftJoin_}.
	 * @param ctx the parse tree
	 */
	void exitLeftJoin_(EqlParser.LeftJoin_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#rightJoin_}.
	 * @param ctx the parse tree
	 */
	void enterRightJoin_(EqlParser.RightJoin_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#rightJoin_}.
	 * @param ctx the parse tree
	 */
	void exitRightJoin_(EqlParser.RightJoin_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlWhere}.
	 * @param ctx the parse tree
	 */
	void enterSqlWhere(EqlParser.SqlWhereContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlWhere}.
	 * @param ctx the parse tree
	 */
	void exitSqlWhere(EqlParser.SqlWhereContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlGroupBy}.
	 * @param ctx the parse tree
	 */
	void enterSqlGroupBy(EqlParser.SqlGroupByContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlGroupBy}.
	 * @param ctx the parse tree
	 */
	void exitSqlGroupBy(EqlParser.SqlGroupByContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlGroupByItems_}.
	 * @param ctx the parse tree
	 */
	void enterSqlGroupByItems_(EqlParser.SqlGroupByItems_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlGroupByItems_}.
	 * @param ctx the parse tree
	 */
	void exitSqlGroupByItems_(EqlParser.SqlGroupByItems_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlHaving}.
	 * @param ctx the parse tree
	 */
	void enterSqlHaving(EqlParser.SqlHavingContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlHaving}.
	 * @param ctx the parse tree
	 */
	void exitSqlHaving(EqlParser.SqlHavingContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlLimit}.
	 * @param ctx the parse tree
	 */
	void enterSqlLimit(EqlParser.SqlLimitContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlLimit}.
	 * @param ctx the parse tree
	 */
	void exitSqlLimit(EqlParser.SqlLimitContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlExpr_limitRowCount}.
	 * @param ctx the parse tree
	 */
	void enterSqlExpr_limitRowCount(EqlParser.SqlExpr_limitRowCountContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlExpr_limitRowCount}.
	 * @param ctx the parse tree
	 */
	void exitSqlExpr_limitRowCount(EqlParser.SqlExpr_limitRowCountContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlExpr_limitOffset}.
	 * @param ctx the parse tree
	 */
	void enterSqlExpr_limitOffset(EqlParser.SqlExpr_limitOffsetContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlExpr_limitOffset}.
	 * @param ctx the parse tree
	 */
	void exitSqlExpr_limitOffset(EqlParser.SqlExpr_limitOffsetContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlSubQueryExpr}.
	 * @param ctx the parse tree
	 */
	void enterSqlSubQueryExpr(EqlParser.SqlSubQueryExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlSubQueryExpr}.
	 * @param ctx the parse tree
	 */
	void exitSqlSubQueryExpr(EqlParser.SqlSubQueryExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#forUpdate_}.
	 * @param ctx the parse tree
	 */
	void enterForUpdate_(EqlParser.ForUpdate_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#forUpdate_}.
	 * @param ctx the parse tree
	 */
	void exitForUpdate_(EqlParser.ForUpdate_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlParameterMarker}.
	 * @param ctx the parse tree
	 */
	void enterSqlParameterMarker(EqlParser.SqlParameterMarkerContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlParameterMarker}.
	 * @param ctx the parse tree
	 */
	void exitSqlParameterMarker(EqlParser.SqlParameterMarkerContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlLiteral}.
	 * @param ctx the parse tree
	 */
	void enterSqlLiteral(EqlParser.SqlLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlLiteral}.
	 * @param ctx the parse tree
	 */
	void exitSqlLiteral(EqlParser.SqlLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlStringLiteral}.
	 * @param ctx the parse tree
	 */
	void enterSqlStringLiteral(EqlParser.SqlStringLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlStringLiteral}.
	 * @param ctx the parse tree
	 */
	void exitSqlStringLiteral(EqlParser.SqlStringLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlNumberLiteral}.
	 * @param ctx the parse tree
	 */
	void enterSqlNumberLiteral(EqlParser.SqlNumberLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlNumberLiteral}.
	 * @param ctx the parse tree
	 */
	void exitSqlNumberLiteral(EqlParser.SqlNumberLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlDateTimeLiteral}.
	 * @param ctx the parse tree
	 */
	void enterSqlDateTimeLiteral(EqlParser.SqlDateTimeLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlDateTimeLiteral}.
	 * @param ctx the parse tree
	 */
	void exitSqlDateTimeLiteral(EqlParser.SqlDateTimeLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlHexadecimalLiteral}.
	 * @param ctx the parse tree
	 */
	void enterSqlHexadecimalLiteral(EqlParser.SqlHexadecimalLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlHexadecimalLiteral}.
	 * @param ctx the parse tree
	 */
	void exitSqlHexadecimalLiteral(EqlParser.SqlHexadecimalLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlBitValueLiteral}.
	 * @param ctx the parse tree
	 */
	void enterSqlBitValueLiteral(EqlParser.SqlBitValueLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlBitValueLiteral}.
	 * @param ctx the parse tree
	 */
	void exitSqlBitValueLiteral(EqlParser.SqlBitValueLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlBooleanLiteral}.
	 * @param ctx the parse tree
	 */
	void enterSqlBooleanLiteral(EqlParser.SqlBooleanLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlBooleanLiteral}.
	 * @param ctx the parse tree
	 */
	void exitSqlBooleanLiteral(EqlParser.SqlBooleanLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlNullLiteral}.
	 * @param ctx the parse tree
	 */
	void enterSqlNullLiteral(EqlParser.SqlNullLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlNullLiteral}.
	 * @param ctx the parse tree
	 */
	void exitSqlNullLiteral(EqlParser.SqlNullLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlIdentifier_}.
	 * @param ctx the parse tree
	 */
	void enterSqlIdentifier_(EqlParser.SqlIdentifier_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlIdentifier_}.
	 * @param ctx the parse tree
	 */
	void exitSqlIdentifier_(EqlParser.SqlIdentifier_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#unreservedWord_}.
	 * @param ctx the parse tree
	 */
	void enterUnreservedWord_(EqlParser.UnreservedWord_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#unreservedWord_}.
	 * @param ctx the parse tree
	 */
	void exitUnreservedWord_(EqlParser.UnreservedWord_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlTableName}.
	 * @param ctx the parse tree
	 */
	void enterSqlTableName(EqlParser.SqlTableNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlTableName}.
	 * @param ctx the parse tree
	 */
	void exitSqlTableName(EqlParser.SqlTableNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlColumnName}.
	 * @param ctx the parse tree
	 */
	void enterSqlColumnName(EqlParser.SqlColumnNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlColumnName}.
	 * @param ctx the parse tree
	 */
	void exitSqlColumnName(EqlParser.SqlColumnNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlQualifiedName}.
	 * @param ctx the parse tree
	 */
	void enterSqlQualifiedName(EqlParser.SqlQualifiedNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlQualifiedName}.
	 * @param ctx the parse tree
	 */
	void exitSqlQualifiedName(EqlParser.SqlQualifiedNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#columnNames_}.
	 * @param ctx the parse tree
	 */
	void enterColumnNames_(EqlParser.ColumnNames_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#columnNames_}.
	 * @param ctx the parse tree
	 */
	void exitColumnNames_(EqlParser.ColumnNames_Context ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlExpr_primary2}
	 * labeled alternative in {@link EqlParser#sqlExpr}.
	 * @param ctx the parse tree
	 */
	void enterSqlExpr_primary2(EqlParser.SqlExpr_primary2Context ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlExpr_primary2}
	 * labeled alternative in {@link EqlParser#sqlExpr}.
	 * @param ctx the parse tree
	 */
	void exitSqlExpr_primary2(EqlParser.SqlExpr_primary2Context ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlOrExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr}.
	 * @param ctx the parse tree
	 */
	void enterSqlOrExpr(EqlParser.SqlOrExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlOrExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr}.
	 * @param ctx the parse tree
	 */
	void exitSqlOrExpr(EqlParser.SqlOrExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlNotExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr}.
	 * @param ctx the parse tree
	 */
	void enterSqlNotExpr(EqlParser.SqlNotExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlNotExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr}.
	 * @param ctx the parse tree
	 */
	void exitSqlNotExpr(EqlParser.SqlNotExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlAndExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr}.
	 * @param ctx the parse tree
	 */
	void enterSqlAndExpr(EqlParser.SqlAndExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlAndExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr}.
	 * @param ctx the parse tree
	 */
	void exitSqlAndExpr(EqlParser.SqlAndExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlBinaryExpr_compare}
	 * labeled alternative in {@link EqlParser#sqlExpr_primary}.
	 * @param ctx the parse tree
	 */
	void enterSqlBinaryExpr_compare(EqlParser.SqlBinaryExpr_compareContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlBinaryExpr_compare}
	 * labeled alternative in {@link EqlParser#sqlExpr_primary}.
	 * @param ctx the parse tree
	 */
	void exitSqlBinaryExpr_compare(EqlParser.SqlBinaryExpr_compareContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlExpr_predicate2}
	 * labeled alternative in {@link EqlParser#sqlExpr_primary}.
	 * @param ctx the parse tree
	 */
	void enterSqlExpr_predicate2(EqlParser.SqlExpr_predicate2Context ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlExpr_predicate2}
	 * labeled alternative in {@link EqlParser#sqlExpr_primary}.
	 * @param ctx the parse tree
	 */
	void exitSqlExpr_predicate2(EqlParser.SqlExpr_predicate2Context ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlIsNullExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_primary}.
	 * @param ctx the parse tree
	 */
	void enterSqlIsNullExpr(EqlParser.SqlIsNullExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlIsNullExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_primary}.
	 * @param ctx the parse tree
	 */
	void exitSqlIsNullExpr(EqlParser.SqlIsNullExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlCompareWithQueryExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_primary}.
	 * @param ctx the parse tree
	 */
	void enterSqlCompareWithQueryExpr(EqlParser.SqlCompareWithQueryExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlCompareWithQueryExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_primary}.
	 * @param ctx the parse tree
	 */
	void exitSqlCompareWithQueryExpr(EqlParser.SqlCompareWithQueryExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#comparisonOperator_}.
	 * @param ctx the parse tree
	 */
	void enterComparisonOperator_(EqlParser.ComparisonOperator_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#comparisonOperator_}.
	 * @param ctx the parse tree
	 */
	void exitComparisonOperator_(EqlParser.ComparisonOperator_Context ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlInQueryExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 */
	void enterSqlInQueryExpr(EqlParser.SqlInQueryExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlInQueryExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 */
	void exitSqlInQueryExpr(EqlParser.SqlInQueryExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlInValuesExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 */
	void enterSqlInValuesExpr(EqlParser.SqlInValuesExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlInValuesExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 */
	void exitSqlInValuesExpr(EqlParser.SqlInValuesExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlBetweenExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 */
	void enterSqlBetweenExpr(EqlParser.SqlBetweenExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlBetweenExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 */
	void exitSqlBetweenExpr(EqlParser.SqlBetweenExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlLikeExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 */
	void enterSqlLikeExpr(EqlParser.SqlLikeExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlLikeExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 */
	void exitSqlLikeExpr(EqlParser.SqlLikeExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlExpr_bit2}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 */
	void enterSqlExpr_bit2(EqlParser.SqlExpr_bit2Context ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlExpr_bit2}
	 * labeled alternative in {@link EqlParser#sqlExpr_predicate}.
	 * @param ctx the parse tree
	 */
	void exitSqlExpr_bit2(EqlParser.SqlExpr_bit2Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlInValues_}.
	 * @param ctx the parse tree
	 */
	void enterSqlInValues_(EqlParser.SqlInValues_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlInValues_}.
	 * @param ctx the parse tree
	 */
	void exitSqlInValues_(EqlParser.SqlInValues_Context ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlBinaryExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_bit}.
	 * @param ctx the parse tree
	 */
	void enterSqlBinaryExpr(EqlParser.SqlBinaryExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlBinaryExpr}
	 * labeled alternative in {@link EqlParser#sqlExpr_bit}.
	 * @param ctx the parse tree
	 */
	void exitSqlBinaryExpr(EqlParser.SqlBinaryExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SqlExpr_simple2}
	 * labeled alternative in {@link EqlParser#sqlExpr_bit}.
	 * @param ctx the parse tree
	 */
	void enterSqlExpr_simple2(EqlParser.SqlExpr_simple2Context ctx);
	/**
	 * Exit a parse tree produced by the {@code SqlExpr_simple2}
	 * labeled alternative in {@link EqlParser#sqlExpr_bit}.
	 * @param ctx the parse tree
	 */
	void exitSqlExpr_simple2(EqlParser.SqlExpr_simple2Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlExpr_simple}.
	 * @param ctx the parse tree
	 */
	void enterSqlExpr_simple(EqlParser.SqlExpr_simpleContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlExpr_simple}.
	 * @param ctx the parse tree
	 */
	void exitSqlExpr_simple(EqlParser.SqlExpr_simpleContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlUnaryExpr}.
	 * @param ctx the parse tree
	 */
	void enterSqlUnaryExpr(EqlParser.SqlUnaryExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlUnaryExpr}.
	 * @param ctx the parse tree
	 */
	void exitSqlUnaryExpr(EqlParser.SqlUnaryExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlExpr_brace}.
	 * @param ctx the parse tree
	 */
	void enterSqlExpr_brace(EqlParser.SqlExpr_braceContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlExpr_brace}.
	 * @param ctx the parse tree
	 */
	void exitSqlExpr_brace(EqlParser.SqlExpr_braceContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlMultiValueExpr}.
	 * @param ctx the parse tree
	 */
	void enterSqlMultiValueExpr(EqlParser.SqlMultiValueExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlMultiValueExpr}.
	 * @param ctx the parse tree
	 */
	void exitSqlMultiValueExpr(EqlParser.SqlMultiValueExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlExistsExpr}.
	 * @param ctx the parse tree
	 */
	void enterSqlExistsExpr(EqlParser.SqlExistsExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlExistsExpr}.
	 * @param ctx the parse tree
	 */
	void exitSqlExistsExpr(EqlParser.SqlExistsExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlExpr_functionCall}.
	 * @param ctx the parse tree
	 */
	void enterSqlExpr_functionCall(EqlParser.SqlExpr_functionCallContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlExpr_functionCall}.
	 * @param ctx the parse tree
	 */
	void exitSqlExpr_functionCall(EqlParser.SqlExpr_functionCallContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlAggregateFunction}.
	 * @param ctx the parse tree
	 */
	void enterSqlAggregateFunction(EqlParser.SqlAggregateFunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlAggregateFunction}.
	 * @param ctx the parse tree
	 */
	void exitSqlAggregateFunction(EqlParser.SqlAggregateFunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlIdentifier_agg_}.
	 * @param ctx the parse tree
	 */
	void enterSqlIdentifier_agg_(EqlParser.SqlIdentifier_agg_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlIdentifier_agg_}.
	 * @param ctx the parse tree
	 */
	void exitSqlIdentifier_agg_(EqlParser.SqlIdentifier_agg_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#distinct_}.
	 * @param ctx the parse tree
	 */
	void enterDistinct_(EqlParser.Distinct_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#distinct_}.
	 * @param ctx the parse tree
	 */
	void exitDistinct_(EqlParser.Distinct_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#functionArgs_}.
	 * @param ctx the parse tree
	 */
	void enterFunctionArgs_(EqlParser.FunctionArgs_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#functionArgs_}.
	 * @param ctx the parse tree
	 */
	void exitFunctionArgs_(EqlParser.FunctionArgs_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlExpr_special}.
	 * @param ctx the parse tree
	 */
	void enterSqlExpr_special(EqlParser.SqlExpr_specialContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlExpr_special}.
	 * @param ctx the parse tree
	 */
	void exitSqlExpr_special(EqlParser.SqlExpr_specialContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlCastExpr}.
	 * @param ctx the parse tree
	 */
	void enterSqlCastExpr(EqlParser.SqlCastExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlCastExpr}.
	 * @param ctx the parse tree
	 */
	void exitSqlCastExpr(EqlParser.SqlCastExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlRegularFunction}.
	 * @param ctx the parse tree
	 */
	void enterSqlRegularFunction(EqlParser.SqlRegularFunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlRegularFunction}.
	 * @param ctx the parse tree
	 */
	void exitSqlRegularFunction(EqlParser.SqlRegularFunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlIdentifier_func_}.
	 * @param ctx the parse tree
	 */
	void enterSqlIdentifier_func_(EqlParser.SqlIdentifier_func_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlIdentifier_func_}.
	 * @param ctx the parse tree
	 */
	void exitSqlIdentifier_func_(EqlParser.SqlIdentifier_func_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlDecorators_}.
	 * @param ctx the parse tree
	 */
	void enterSqlDecorators_(EqlParser.SqlDecorators_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlDecorators_}.
	 * @param ctx the parse tree
	 */
	void exitSqlDecorators_(EqlParser.SqlDecorators_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlDecorator}.
	 * @param ctx the parse tree
	 */
	void enterSqlDecorator(EqlParser.SqlDecoratorContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlDecorator}.
	 * @param ctx the parse tree
	 */
	void exitSqlDecorator(EqlParser.SqlDecoratorContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#decoratorArgs_}.
	 * @param ctx the parse tree
	 */
	void enterDecoratorArgs_(EqlParser.DecoratorArgs_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#decoratorArgs_}.
	 * @param ctx the parse tree
	 */
	void exitDecoratorArgs_(EqlParser.DecoratorArgs_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlCaseExpr}.
	 * @param ctx the parse tree
	 */
	void enterSqlCaseExpr(EqlParser.SqlCaseExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlCaseExpr}.
	 * @param ctx the parse tree
	 */
	void exitSqlCaseExpr(EqlParser.SqlCaseExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#caseWhens_}.
	 * @param ctx the parse tree
	 */
	void enterCaseWhens_(EqlParser.CaseWhens_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#caseWhens_}.
	 * @param ctx the parse tree
	 */
	void exitCaseWhens_(EqlParser.CaseWhens_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlCaseWhenItem}.
	 * @param ctx the parse tree
	 */
	void enterSqlCaseWhenItem(EqlParser.SqlCaseWhenItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlCaseWhenItem}.
	 * @param ctx the parse tree
	 */
	void exitSqlCaseWhenItem(EqlParser.SqlCaseWhenItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlIntervalExpr}.
	 * @param ctx the parse tree
	 */
	void enterSqlIntervalExpr(EqlParser.SqlIntervalExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlIntervalExpr}.
	 * @param ctx the parse tree
	 */
	void exitSqlIntervalExpr(EqlParser.SqlIntervalExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#intervalUnit_}.
	 * @param ctx the parse tree
	 */
	void enterIntervalUnit_(EqlParser.IntervalUnit_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#intervalUnit_}.
	 * @param ctx the parse tree
	 */
	void exitIntervalUnit_(EqlParser.IntervalUnit_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlOrderBy}.
	 * @param ctx the parse tree
	 */
	void enterSqlOrderBy(EqlParser.SqlOrderByContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlOrderBy}.
	 * @param ctx the parse tree
	 */
	void exitSqlOrderBy(EqlParser.SqlOrderByContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlOrderByItems_}.
	 * @param ctx the parse tree
	 */
	void enterSqlOrderByItems_(EqlParser.SqlOrderByItems_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlOrderByItems_}.
	 * @param ctx the parse tree
	 */
	void exitSqlOrderByItems_(EqlParser.SqlOrderByItems_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlOrderByItem}.
	 * @param ctx the parse tree
	 */
	void enterSqlOrderByItem(EqlParser.SqlOrderByItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlOrderByItem}.
	 * @param ctx the parse tree
	 */
	void exitSqlOrderByItem(EqlParser.SqlOrderByItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlGroupByItem}.
	 * @param ctx the parse tree
	 */
	void enterSqlGroupByItem(EqlParser.SqlGroupByItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlGroupByItem}.
	 * @param ctx the parse tree
	 */
	void exitSqlGroupByItem(EqlParser.SqlGroupByItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#sqlTypeExpr}.
	 * @param ctx the parse tree
	 */
	void enterSqlTypeExpr(EqlParser.SqlTypeExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#sqlTypeExpr}.
	 * @param ctx the parse tree
	 */
	void exitSqlTypeExpr(EqlParser.SqlTypeExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#dataTypeName_}.
	 * @param ctx the parse tree
	 */
	void enterDataTypeName_(EqlParser.DataTypeName_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#dataTypeName_}.
	 * @param ctx the parse tree
	 */
	void exitDataTypeName_(EqlParser.DataTypeName_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#characterSet_}.
	 * @param ctx the parse tree
	 */
	void enterCharacterSet_(EqlParser.CharacterSet_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#characterSet_}.
	 * @param ctx the parse tree
	 */
	void exitCharacterSet_(EqlParser.CharacterSet_Context ctx);
	/**
	 * Enter a parse tree produced by {@link EqlParser#collateClause_}.
	 * @param ctx the parse tree
	 */
	void enterCollateClause_(EqlParser.CollateClause_Context ctx);
	/**
	 * Exit a parse tree produced by {@link EqlParser#collateClause_}.
	 * @param ctx the parse tree
	 */
	void exitCollateClause_(EqlParser.CollateClause_Context ctx);
}