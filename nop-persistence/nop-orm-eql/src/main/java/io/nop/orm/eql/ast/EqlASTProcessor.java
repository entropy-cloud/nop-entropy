//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast;

// tell cpd to start ignoring code - CPD-OFF
public class EqlASTProcessor<T,C>{

    public T processAST(EqlASTNode node, C context){
        if(node == null)
            return null;
       switch(node.getASTKind()){
    
            case SqlProgram:
                return processSqlProgram((SqlProgram)node,context);
        
            case SqlQualifiedName:
                return processSqlQualifiedName((SqlQualifiedName)node,context);
        
            case SqlTableName:
                return processSqlTableName((SqlTableName)node,context);
        
            case SqlColumnName:
                return processSqlColumnName((SqlColumnName)node,context);
        
            case SqlInsert:
                return processSqlInsert((SqlInsert)node,context);
        
            case SqlValues:
                return processSqlValues((SqlValues)node,context);
        
            case SqlUpdate:
                return processSqlUpdate((SqlUpdate)node,context);
        
            case SqlAlias:
                return processSqlAlias((SqlAlias)node,context);
        
            case SqlAssignment:
                return processSqlAssignment((SqlAssignment)node,context);
        
            case SqlDelete:
                return processSqlDelete((SqlDelete)node,context);
        
            case SqlWhere:
                return processSqlWhere((SqlWhere)node,context);
        
            case SqlCteStatement:
                return processSqlCteStatement((SqlCteStatement)node,context);
        
            case SqlSelectWithCte:
                return processSqlSelectWithCte((SqlSelectWithCte)node,context);
        
            case SqlQuerySelect:
                return processSqlQuerySelect((SqlQuerySelect)node,context);
        
            case SqlParameterMarker:
                return processSqlParameterMarker((SqlParameterMarker)node,context);
        
            case SqlHaving:
                return processSqlHaving((SqlHaving)node,context);
        
            case SqlDecorator:
                return processSqlDecorator((SqlDecorator)node,context);
        
            case SqlUnionSelect:
                return processSqlUnionSelect((SqlUnionSelect)node,context);
        
            case SqlExprProjection:
                return processSqlExprProjection((SqlExprProjection)node,context);
        
            case SqlAllProjection:
                return processSqlAllProjection((SqlAllProjection)node,context);
        
            case SqlPartitionBy:
                return processSqlPartitionBy((SqlPartitionBy)node,context);
        
            case SqlOrderBy:
                return processSqlOrderBy((SqlOrderBy)node,context);
        
            case SqlGroupBy:
                return processSqlGroupBy((SqlGroupBy)node,context);
        
            case SqlGroupByItem:
                return processSqlGroupByItem((SqlGroupByItem)node,context);
        
            case SqlOrderByItem:
                return processSqlOrderByItem((SqlOrderByItem)node,context);
        
            case SqlLimit:
                return processSqlLimit((SqlLimit)node,context);
        
            case SqlFrom:
                return processSqlFrom((SqlFrom)node,context);
        
            case SqlSingleTableSource:
                return processSqlSingleTableSource((SqlSingleTableSource)node,context);
        
            case SqlJoinTableSource:
                return processSqlJoinTableSource((SqlJoinTableSource)node,context);
        
            case SqlSubqueryTableSource:
                return processSqlSubqueryTableSource((SqlSubqueryTableSource)node,context);
        
            case SqlNotExpr:
                return processSqlNotExpr((SqlNotExpr)node,context);
        
            case SqlAndExpr:
                return processSqlAndExpr((SqlAndExpr)node,context);
        
            case SqlOrExpr:
                return processSqlOrExpr((SqlOrExpr)node,context);
        
            case SqlStringLiteral:
                return processSqlStringLiteral((SqlStringLiteral)node,context);
        
            case SqlNumberLiteral:
                return processSqlNumberLiteral((SqlNumberLiteral)node,context);
        
            case SqlDateTimeLiteral:
                return processSqlDateTimeLiteral((SqlDateTimeLiteral)node,context);
        
            case SqlHexadecimalLiteral:
                return processSqlHexadecimalLiteral((SqlHexadecimalLiteral)node,context);
        
            case SqlBitValueLiteral:
                return processSqlBitValueLiteral((SqlBitValueLiteral)node,context);
        
            case SqlBooleanLiteral:
                return processSqlBooleanLiteral((SqlBooleanLiteral)node,context);
        
            case SqlNullLiteral:
                return processSqlNullLiteral((SqlNullLiteral)node,context);
        
            case SqlBinaryExpr:
                return processSqlBinaryExpr((SqlBinaryExpr)node,context);
        
            case SqlIsNullExpr:
                return processSqlIsNullExpr((SqlIsNullExpr)node,context);
        
            case SqlCompareWithQueryExpr:
                return processSqlCompareWithQueryExpr((SqlCompareWithQueryExpr)node,context);
        
            case SqlSubQueryExpr:
                return processSqlSubQueryExpr((SqlSubQueryExpr)node,context);
        
            case SqlInQueryExpr:
                return processSqlInQueryExpr((SqlInQueryExpr)node,context);
        
            case SqlInValuesExpr:
                return processSqlInValuesExpr((SqlInValuesExpr)node,context);
        
            case SqlBetweenExpr:
                return processSqlBetweenExpr((SqlBetweenExpr)node,context);
        
            case SqlLikeExpr:
                return processSqlLikeExpr((SqlLikeExpr)node,context);
        
            case SqlUnaryExpr:
                return processSqlUnaryExpr((SqlUnaryExpr)node,context);
        
            case SqlAggregateFunction:
                return processSqlAggregateFunction((SqlAggregateFunction)node,context);
        
            case SqlRegularFunction:
                return processSqlRegularFunction((SqlRegularFunction)node,context);
        
            case SqlWindowExpr:
                return processSqlWindowExpr((SqlWindowExpr)node,context);
        
            case SqlMultiValueExpr:
                return processSqlMultiValueExpr((SqlMultiValueExpr)node,context);
        
            case SqlExistsExpr:
                return processSqlExistsExpr((SqlExistsExpr)node,context);
        
            case SqlIntervalExpr:
                return processSqlIntervalExpr((SqlIntervalExpr)node,context);
        
            case SqlCaseExpr:
                return processSqlCaseExpr((SqlCaseExpr)node,context);
        
            case SqlCaseWhenItem:
                return processSqlCaseWhenItem((SqlCaseWhenItem)node,context);
        
            case SqlCastExpr:
                return processSqlCastExpr((SqlCastExpr)node,context);
        
            case SqlTypeExpr:
                return processSqlTypeExpr((SqlTypeExpr)node,context);
        
            case SqlCommit:
                return processSqlCommit((SqlCommit)node,context);
        
            case SqlRollback:
                return processSqlRollback((SqlRollback)node,context);
        
          default:
             throw new IllegalArgumentException("invalid ast kind");
       }
    }

    
	public T processSqlProgram(SqlProgram node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlQualifiedName(SqlQualifiedName node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlTableName(SqlTableName node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlColumnName(SqlColumnName node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlInsert(SqlInsert node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlValues(SqlValues node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlUpdate(SqlUpdate node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlAlias(SqlAlias node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlAssignment(SqlAssignment node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlDelete(SqlDelete node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlWhere(SqlWhere node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlCteStatement(SqlCteStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlSelectWithCte(SqlSelectWithCte node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlQuerySelect(SqlQuerySelect node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlParameterMarker(SqlParameterMarker node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlHaving(SqlHaving node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlDecorator(SqlDecorator node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlUnionSelect(SqlUnionSelect node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlExprProjection(SqlExprProjection node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlAllProjection(SqlAllProjection node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlPartitionBy(SqlPartitionBy node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlOrderBy(SqlOrderBy node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlGroupBy(SqlGroupBy node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlGroupByItem(SqlGroupByItem node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlOrderByItem(SqlOrderByItem node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlLimit(SqlLimit node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlFrom(SqlFrom node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlSingleTableSource(SqlSingleTableSource node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlJoinTableSource(SqlJoinTableSource node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlSubqueryTableSource(SqlSubqueryTableSource node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlNotExpr(SqlNotExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlAndExpr(SqlAndExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlOrExpr(SqlOrExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlStringLiteral(SqlStringLiteral node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlNumberLiteral(SqlNumberLiteral node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlDateTimeLiteral(SqlDateTimeLiteral node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlHexadecimalLiteral(SqlHexadecimalLiteral node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlBitValueLiteral(SqlBitValueLiteral node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlBooleanLiteral(SqlBooleanLiteral node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlNullLiteral(SqlNullLiteral node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlBinaryExpr(SqlBinaryExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlIsNullExpr(SqlIsNullExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlCompareWithQueryExpr(SqlCompareWithQueryExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlSubQueryExpr(SqlSubQueryExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlInQueryExpr(SqlInQueryExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlInValuesExpr(SqlInValuesExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlBetweenExpr(SqlBetweenExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlLikeExpr(SqlLikeExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlUnaryExpr(SqlUnaryExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlAggregateFunction(SqlAggregateFunction node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlRegularFunction(SqlRegularFunction node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlWindowExpr(SqlWindowExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlMultiValueExpr(SqlMultiValueExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlExistsExpr(SqlExistsExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlIntervalExpr(SqlIntervalExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlCaseExpr(SqlCaseExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlCaseWhenItem(SqlCaseWhenItem node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlCastExpr(SqlCastExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlTypeExpr(SqlTypeExpr node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlCommit(SqlCommit node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSqlRollback(SqlRollback node, C context){
        return defaultProcess(node, context);
	}
    

    public T defaultProcess(EqlASTNode node, C context){
        return null;
    }
}
// resume CPD analysis - CPD-ON
