//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast;

import io.nop.commons.functional.visit.AbstractVisitor;

// tell cpd to start ignoring code - CPD-OFF
public class EqlASTVisitor extends AbstractVisitor<EqlASTNode>{

    @Override
    public void visit(EqlASTNode node){
        switch(node.getASTKind()){
        
                case SqlProgram:
                    visitSqlProgram((SqlProgram)node);
                    return;
            
                case SqlQualifiedName:
                    visitSqlQualifiedName((SqlQualifiedName)node);
                    return;
            
                case SqlTableName:
                    visitSqlTableName((SqlTableName)node);
                    return;
            
                case SqlColumnName:
                    visitSqlColumnName((SqlColumnName)node);
                    return;
            
                case SqlInsert:
                    visitSqlInsert((SqlInsert)node);
                    return;
            
                case SqlValues:
                    visitSqlValues((SqlValues)node);
                    return;
            
                case SqlUpdate:
                    visitSqlUpdate((SqlUpdate)node);
                    return;
            
                case SqlAlias:
                    visitSqlAlias((SqlAlias)node);
                    return;
            
                case SqlAssignment:
                    visitSqlAssignment((SqlAssignment)node);
                    return;
            
                case SqlDelete:
                    visitSqlDelete((SqlDelete)node);
                    return;
            
                case SqlWhere:
                    visitSqlWhere((SqlWhere)node);
                    return;
            
                case SqlCteStatement:
                    visitSqlCteStatement((SqlCteStatement)node);
                    return;
            
                case SqlSelectWithCte:
                    visitSqlSelectWithCte((SqlSelectWithCte)node);
                    return;
            
                case SqlQuerySelect:
                    visitSqlQuerySelect((SqlQuerySelect)node);
                    return;
            
                case SqlParameterMarker:
                    visitSqlParameterMarker((SqlParameterMarker)node);
                    return;
            
                case SqlHaving:
                    visitSqlHaving((SqlHaving)node);
                    return;
            
                case SqlDecorator:
                    visitSqlDecorator((SqlDecorator)node);
                    return;
            
                case SqlUnionSelect:
                    visitSqlUnionSelect((SqlUnionSelect)node);
                    return;
            
                case SqlExprProjection:
                    visitSqlExprProjection((SqlExprProjection)node);
                    return;
            
                case SqlAllProjection:
                    visitSqlAllProjection((SqlAllProjection)node);
                    return;
            
                case SqlPartitionBy:
                    visitSqlPartitionBy((SqlPartitionBy)node);
                    return;
            
                case SqlOrderBy:
                    visitSqlOrderBy((SqlOrderBy)node);
                    return;
            
                case SqlGroupBy:
                    visitSqlGroupBy((SqlGroupBy)node);
                    return;
            
                case SqlGroupByItem:
                    visitSqlGroupByItem((SqlGroupByItem)node);
                    return;
            
                case SqlOrderByItem:
                    visitSqlOrderByItem((SqlOrderByItem)node);
                    return;
            
                case SqlLimit:
                    visitSqlLimit((SqlLimit)node);
                    return;
            
                case SqlFrom:
                    visitSqlFrom((SqlFrom)node);
                    return;
            
                case SqlSingleTableSource:
                    visitSqlSingleTableSource((SqlSingleTableSource)node);
                    return;
            
                case SqlJoinTableSource:
                    visitSqlJoinTableSource((SqlJoinTableSource)node);
                    return;
            
                case SqlSubqueryTableSource:
                    visitSqlSubqueryTableSource((SqlSubqueryTableSource)node);
                    return;
            
                case SqlNotExpr:
                    visitSqlNotExpr((SqlNotExpr)node);
                    return;
            
                case SqlAndExpr:
                    visitSqlAndExpr((SqlAndExpr)node);
                    return;
            
                case SqlOrExpr:
                    visitSqlOrExpr((SqlOrExpr)node);
                    return;
            
                case SqlStringLiteral:
                    visitSqlStringLiteral((SqlStringLiteral)node);
                    return;
            
                case SqlNumberLiteral:
                    visitSqlNumberLiteral((SqlNumberLiteral)node);
                    return;
            
                case SqlDateTimeLiteral:
                    visitSqlDateTimeLiteral((SqlDateTimeLiteral)node);
                    return;
            
                case SqlHexadecimalLiteral:
                    visitSqlHexadecimalLiteral((SqlHexadecimalLiteral)node);
                    return;
            
                case SqlBitValueLiteral:
                    visitSqlBitValueLiteral((SqlBitValueLiteral)node);
                    return;
            
                case SqlBooleanLiteral:
                    visitSqlBooleanLiteral((SqlBooleanLiteral)node);
                    return;
            
                case SqlNullLiteral:
                    visitSqlNullLiteral((SqlNullLiteral)node);
                    return;
            
                case SqlBinaryExpr:
                    visitSqlBinaryExpr((SqlBinaryExpr)node);
                    return;
            
                case SqlIsNullExpr:
                    visitSqlIsNullExpr((SqlIsNullExpr)node);
                    return;
            
                case SqlCompareWithQueryExpr:
                    visitSqlCompareWithQueryExpr((SqlCompareWithQueryExpr)node);
                    return;
            
                case SqlSubQueryExpr:
                    visitSqlSubQueryExpr((SqlSubQueryExpr)node);
                    return;
            
                case SqlInQueryExpr:
                    visitSqlInQueryExpr((SqlInQueryExpr)node);
                    return;
            
                case SqlInValuesExpr:
                    visitSqlInValuesExpr((SqlInValuesExpr)node);
                    return;
            
                case SqlBetweenExpr:
                    visitSqlBetweenExpr((SqlBetweenExpr)node);
                    return;
            
                case SqlLikeExpr:
                    visitSqlLikeExpr((SqlLikeExpr)node);
                    return;
            
                case SqlUnaryExpr:
                    visitSqlUnaryExpr((SqlUnaryExpr)node);
                    return;
            
                case SqlAggregateFunction:
                    visitSqlAggregateFunction((SqlAggregateFunction)node);
                    return;
            
                case SqlRegularFunction:
                    visitSqlRegularFunction((SqlRegularFunction)node);
                    return;
            
                case SqlWindowExpr:
                    visitSqlWindowExpr((SqlWindowExpr)node);
                    return;
            
                case SqlMultiValueExpr:
                    visitSqlMultiValueExpr((SqlMultiValueExpr)node);
                    return;
            
                case SqlExistsExpr:
                    visitSqlExistsExpr((SqlExistsExpr)node);
                    return;
            
                case SqlIntervalExpr:
                    visitSqlIntervalExpr((SqlIntervalExpr)node);
                    return;
            
                case SqlCaseExpr:
                    visitSqlCaseExpr((SqlCaseExpr)node);
                    return;
            
                case SqlCaseWhenItem:
                    visitSqlCaseWhenItem((SqlCaseWhenItem)node);
                    return;
            
                case SqlCastExpr:
                    visitSqlCastExpr((SqlCastExpr)node);
                    return;
            
                case SqlTypeExpr:
                    visitSqlTypeExpr((SqlTypeExpr)node);
                    return;
            
                case SqlCollectionAccessExpr:
                    visitSqlCollectionAccessExpr((SqlCollectionAccessExpr)node);
                    return;
            
                case SqlCommit:
                    visitSqlCommit((SqlCommit)node);
                    return;
            
                case SqlRollback:
                    visitSqlRollback((SqlRollback)node);
                    return;
            
        default:
        throw new IllegalArgumentException("invalid ast kind");
        }
    }

    
            public void visitSqlProgram(SqlProgram node){
            
                    this.visitChildren(node.getStatements());         
            }
        
            public void visitSqlQualifiedName(SqlQualifiedName node){
            
                    this.visitChild(node.getNext());
            }
        
            public void visitSqlTableName(SqlTableName node){
            
                    this.visitChild(node.getOwner());
            }
        
            public void visitSqlColumnName(SqlColumnName node){
            
                    this.visitChild(node.getOwner());
            }
        
            public void visitSqlInsert(SqlInsert node){
            
                    this.visitChildren(node.getDecorators());         
                    this.visitChild(node.getTableName());
                    this.visitChildren(node.getColumns());         
                    this.visitChild(node.getSelect());
                    this.visitChild(node.getValues());
            }
        
            public void visitSqlValues(SqlValues node){
            
                    this.visitChildren(node.getValues());         
            }
        
            public void visitSqlUpdate(SqlUpdate node){
            
                    this.visitChildren(node.getDecorators());         
                    this.visitChild(node.getTableName());
                    this.visitChild(node.getAlias());
                    this.visitChildren(node.getAssignments());         
                    this.visitChild(node.getWhere());
                    this.visitChildren(node.getReturnProjections());         
            }
        
            public void visitSqlAlias(SqlAlias node){
            
            }
        
            public void visitSqlAssignment(SqlAssignment node){
            
                    this.visitChild(node.getColumnName());
                    this.visitChild(node.getExpr());
            }
        
            public void visitSqlDelete(SqlDelete node){
            
                    this.visitChildren(node.getDecorators());         
                    this.visitChild(node.getTableName());
                    this.visitChild(node.getAlias());
                    this.visitChild(node.getWhere());
            }
        
            public void visitSqlWhere(SqlWhere node){
            
                    this.visitChild(node.getExpr());
            }
        
            public void visitSqlCteStatement(SqlCteStatement node){
            
                    this.visitChild(node.getStatement());
            }
        
            public void visitSqlSelectWithCte(SqlSelectWithCte node){
            
                    this.visitChildren(node.getDecorators());         
                    this.visitChildren(node.getWithCtes());         
                    this.visitChild(node.getSelect());
            }
        
            public void visitSqlQuerySelect(SqlQuerySelect node){
            
                    this.visitChildren(node.getDecorators());         
                    this.visitChildren(node.getProjections());         
                    this.visitChild(node.getFrom());
                    this.visitChild(node.getWhere());
                    this.visitChild(node.getGroupBy());
                    this.visitChild(node.getHaving());
                    this.visitChild(node.getOrderBy());
                    this.visitChild(node.getLimit());
            }
        
            public void visitSqlParameterMarker(SqlParameterMarker node){
            
            }
        
            public void visitSqlHaving(SqlHaving node){
            
                    this.visitChild(node.getExpr());
            }
        
            public void visitSqlDecorator(SqlDecorator node){
            
                    this.visitChildren(node.getArgs());         
            }
        
            public void visitSqlUnionSelect(SqlUnionSelect node){
            
                    this.visitChildren(node.getDecorators());         
                    this.visitChild(node.getLeft());
                    this.visitChild(node.getRight());
            }
        
            public void visitSqlExprProjection(SqlExprProjection node){
            
                    this.visitChildren(node.getDecorators());         
                    this.visitChild(node.getExpr());
                    this.visitChild(node.getAlias());
            }
        
            public void visitSqlAllProjection(SqlAllProjection node){
            
                    this.visitChild(node.getOwner());
            }
        
            public void visitSqlPartitionBy(SqlPartitionBy node){
            
                    this.visitChildren(node.getItems());         
            }
        
            public void visitSqlOrderBy(SqlOrderBy node){
            
                    this.visitChildren(node.getItems());         
            }
        
            public void visitSqlGroupBy(SqlGroupBy node){
            
                    this.visitChildren(node.getItems());         
            }
        
            public void visitSqlGroupByItem(SqlGroupByItem node){
            
                    this.visitChild(node.getExpr());
            }
        
            public void visitSqlOrderByItem(SqlOrderByItem node){
            
                    this.visitChild(node.getExpr());
            }
        
            public void visitSqlLimit(SqlLimit node){
            
                    this.visitChild(node.getLimit());
                    this.visitChild(node.getOffset());
            }
        
            public void visitSqlFrom(SqlFrom node){
            
                    this.visitChildren(node.getDecorators());         
                    this.visitChildren(node.getTableSources());         
            }
        
            public void visitSqlSingleTableSource(SqlSingleTableSource node){
            
                    this.visitChildren(node.getDecorators());         
                    this.visitChild(node.getTableName());
                    this.visitChild(node.getAlias());
            }
        
            public void visitSqlJoinTableSource(SqlJoinTableSource node){
            
                    this.visitChildren(node.getDecorators());         
                    this.visitChild(node.getLeft());
                    this.visitChild(node.getRight());
                    this.visitChild(node.getCondition());
            }
        
            public void visitSqlSubqueryTableSource(SqlSubqueryTableSource node){
            
                    this.visitChildren(node.getDecorators());         
                    this.visitChild(node.getQuery());
                    this.visitChild(node.getAlias());
            }
        
            public void visitSqlNotExpr(SqlNotExpr node){
            
                    this.visitChild(node.getExpr());
            }
        
            public void visitSqlAndExpr(SqlAndExpr node){
            
                    this.visitChild(node.getLeft());
                    this.visitChild(node.getRight());
            }
        
            public void visitSqlOrExpr(SqlOrExpr node){
            
                    this.visitChild(node.getLeft());
                    this.visitChild(node.getRight());
            }
        
            public void visitSqlStringLiteral(SqlStringLiteral node){
            
            }
        
            public void visitSqlNumberLiteral(SqlNumberLiteral node){
            
            }
        
            public void visitSqlDateTimeLiteral(SqlDateTimeLiteral node){
            
            }
        
            public void visitSqlHexadecimalLiteral(SqlHexadecimalLiteral node){
            
            }
        
            public void visitSqlBitValueLiteral(SqlBitValueLiteral node){
            
            }
        
            public void visitSqlBooleanLiteral(SqlBooleanLiteral node){
            
            }
        
            public void visitSqlNullLiteral(SqlNullLiteral node){
            
            }
        
            public void visitSqlBinaryExpr(SqlBinaryExpr node){
            
                    this.visitChild(node.getLeft());
                    this.visitChild(node.getRight());
            }
        
            public void visitSqlIsNullExpr(SqlIsNullExpr node){
            
                    this.visitChild(node.getExpr());
            }
        
            public void visitSqlCompareWithQueryExpr(SqlCompareWithQueryExpr node){
            
                    this.visitChild(node.getExpr());
                    this.visitChild(node.getQuery());
            }
        
            public void visitSqlSubQueryExpr(SqlSubQueryExpr node){
            
                    this.visitChild(node.getSelect());
            }
        
            public void visitSqlInQueryExpr(SqlInQueryExpr node){
            
                    this.visitChild(node.getExpr());
                    this.visitChild(node.getQuery());
            }
        
            public void visitSqlInValuesExpr(SqlInValuesExpr node){
            
                    this.visitChild(node.getExpr());
                    this.visitChildren(node.getValues());         
            }
        
            public void visitSqlBetweenExpr(SqlBetweenExpr node){
            
                    this.visitChild(node.getTest());
                    this.visitChild(node.getBegin());
                    this.visitChild(node.getEnd());
            }
        
            public void visitSqlLikeExpr(SqlLikeExpr node){
            
                    this.visitChild(node.getExpr());
                    this.visitChild(node.getValue());
                    this.visitChild(node.getEscape());
            }
        
            public void visitSqlUnaryExpr(SqlUnaryExpr node){
            
                    this.visitChild(node.getExpr());
            }
        
            public void visitSqlAggregateFunction(SqlAggregateFunction node){
            
                    this.visitChildren(node.getArgs());         
            }
        
            public void visitSqlRegularFunction(SqlRegularFunction node){
            
                    this.visitChildren(node.getArgs());         
            }
        
            public void visitSqlWindowExpr(SqlWindowExpr node){
            
                    this.visitChild(node.getFunction());
                    this.visitChild(node.getPartitionBy());
                    this.visitChild(node.getOrderBy());
            }
        
            public void visitSqlMultiValueExpr(SqlMultiValueExpr node){
            
                    this.visitChildren(node.getValues());         
            }
        
            public void visitSqlExistsExpr(SqlExistsExpr node){
            
                    this.visitChild(node.getQuery());
            }
        
            public void visitSqlIntervalExpr(SqlIntervalExpr node){
            
                    this.visitChild(node.getExpr());
            }
        
            public void visitSqlCaseExpr(SqlCaseExpr node){
            
                    this.visitChild(node.getTest());
                    this.visitChildren(node.getCaseWhens());         
                    this.visitChild(node.getElseExpr());
            }
        
            public void visitSqlCaseWhenItem(SqlCaseWhenItem node){
            
                    this.visitChild(node.getWhen());
                    this.visitChild(node.getThen());
            }
        
            public void visitSqlCastExpr(SqlCastExpr node){
            
                    this.visitChild(node.getExpr());
                    this.visitChild(node.getDataType());
            }
        
            public void visitSqlTypeExpr(SqlTypeExpr node){
            
            }
        
            public void visitSqlCollectionAccessExpr(SqlCollectionAccessExpr node){
            
                    this.visitChild(node.getCollection());
                    this.visitChild(node.getWhere());
                    this.visitChild(node.getOrderBy());
                    this.visitChildren(node.getCollFuncArgs());         
            }
        
            public void visitSqlCommit(SqlCommit node){
            
                    this.visitChildren(node.getDecorators());         
            }
        
            public void visitSqlRollback(SqlRollback node){
            
                    this.visitChildren(node.getDecorators());         
            }
        
}
// resume CPD analysis - CPD-ON
