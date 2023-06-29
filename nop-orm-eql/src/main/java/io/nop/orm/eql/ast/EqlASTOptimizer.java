//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast;

import io.nop.core.lang.ast.optimize.AbstractOptimizer;

// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class EqlASTOptimizer<C> extends AbstractOptimizer<EqlASTNode,C>{

    public EqlASTNode optimize(EqlASTNode node,C context){
        switch(node.getASTKind()){
        
                case SqlProgram:
                return optimizeSqlProgram((SqlProgram)node,context);
            
                case SqlQualifiedName:
                return optimizeSqlQualifiedName((SqlQualifiedName)node,context);
            
                case SqlTableName:
                return optimizeSqlTableName((SqlTableName)node,context);
            
                case SqlColumnName:
                return optimizeSqlColumnName((SqlColumnName)node,context);
            
                case SqlInsert:
                return optimizeSqlInsert((SqlInsert)node,context);
            
                case SqlValues:
                return optimizeSqlValues((SqlValues)node,context);
            
                case SqlUpdate:
                return optimizeSqlUpdate((SqlUpdate)node,context);
            
                case SqlAlias:
                return optimizeSqlAlias((SqlAlias)node,context);
            
                case SqlAssignment:
                return optimizeSqlAssignment((SqlAssignment)node,context);
            
                case SqlDelete:
                return optimizeSqlDelete((SqlDelete)node,context);
            
                case SqlWhere:
                return optimizeSqlWhere((SqlWhere)node,context);
            
                case SqlCteStatement:
                return optimizeSqlCteStatement((SqlCteStatement)node,context);
            
                case SqlSelectWithCte:
                return optimizeSqlSelectWithCte((SqlSelectWithCte)node,context);
            
                case SqlQuerySelect:
                return optimizeSqlQuerySelect((SqlQuerySelect)node,context);
            
                case SqlParameterMarker:
                return optimizeSqlParameterMarker((SqlParameterMarker)node,context);
            
                case SqlHaving:
                return optimizeSqlHaving((SqlHaving)node,context);
            
                case SqlDecorator:
                return optimizeSqlDecorator((SqlDecorator)node,context);
            
                case SqlUnionSelect:
                return optimizeSqlUnionSelect((SqlUnionSelect)node,context);
            
                case SqlExprProjection:
                return optimizeSqlExprProjection((SqlExprProjection)node,context);
            
                case SqlAllProjection:
                return optimizeSqlAllProjection((SqlAllProjection)node,context);
            
                case SqlOrderBy:
                return optimizeSqlOrderBy((SqlOrderBy)node,context);
            
                case SqlGroupBy:
                return optimizeSqlGroupBy((SqlGroupBy)node,context);
            
                case SqlGroupByItem:
                return optimizeSqlGroupByItem((SqlGroupByItem)node,context);
            
                case SqlOrderByItem:
                return optimizeSqlOrderByItem((SqlOrderByItem)node,context);
            
                case SqlLimit:
                return optimizeSqlLimit((SqlLimit)node,context);
            
                case SqlFrom:
                return optimizeSqlFrom((SqlFrom)node,context);
            
                case SqlSingleTableSource:
                return optimizeSqlSingleTableSource((SqlSingleTableSource)node,context);
            
                case SqlJoinTableSource:
                return optimizeSqlJoinTableSource((SqlJoinTableSource)node,context);
            
                case SqlSubqueryTableSource:
                return optimizeSqlSubqueryTableSource((SqlSubqueryTableSource)node,context);
            
                case SqlNotExpr:
                return optimizeSqlNotExpr((SqlNotExpr)node,context);
            
                case SqlAndExpr:
                return optimizeSqlAndExpr((SqlAndExpr)node,context);
            
                case SqlOrExpr:
                return optimizeSqlOrExpr((SqlOrExpr)node,context);
            
                case SqlStringLiteral:
                return optimizeSqlStringLiteral((SqlStringLiteral)node,context);
            
                case SqlNumberLiteral:
                return optimizeSqlNumberLiteral((SqlNumberLiteral)node,context);
            
                case SqlDateTimeLiteral:
                return optimizeSqlDateTimeLiteral((SqlDateTimeLiteral)node,context);
            
                case SqlHexadecimalLiteral:
                return optimizeSqlHexadecimalLiteral((SqlHexadecimalLiteral)node,context);
            
                case SqlBitValueLiteral:
                return optimizeSqlBitValueLiteral((SqlBitValueLiteral)node,context);
            
                case SqlBooleanLiteral:
                return optimizeSqlBooleanLiteral((SqlBooleanLiteral)node,context);
            
                case SqlNullLiteral:
                return optimizeSqlNullLiteral((SqlNullLiteral)node,context);
            
                case SqlBinaryExpr:
                return optimizeSqlBinaryExpr((SqlBinaryExpr)node,context);
            
                case SqlIsNullExpr:
                return optimizeSqlIsNullExpr((SqlIsNullExpr)node,context);
            
                case SqlCompareWithQueryExpr:
                return optimizeSqlCompareWithQueryExpr((SqlCompareWithQueryExpr)node,context);
            
                case SqlSubQueryExpr:
                return optimizeSqlSubQueryExpr((SqlSubQueryExpr)node,context);
            
                case SqlInQueryExpr:
                return optimizeSqlInQueryExpr((SqlInQueryExpr)node,context);
            
                case SqlInValuesExpr:
                return optimizeSqlInValuesExpr((SqlInValuesExpr)node,context);
            
                case SqlBetweenExpr:
                return optimizeSqlBetweenExpr((SqlBetweenExpr)node,context);
            
                case SqlLikeExpr:
                return optimizeSqlLikeExpr((SqlLikeExpr)node,context);
            
                case SqlUnaryExpr:
                return optimizeSqlUnaryExpr((SqlUnaryExpr)node,context);
            
                case SqlAggregateFunction:
                return optimizeSqlAggregateFunction((SqlAggregateFunction)node,context);
            
                case SqlRegularFunction:
                return optimizeSqlRegularFunction((SqlRegularFunction)node,context);
            
                case SqlMultiValueExpr:
                return optimizeSqlMultiValueExpr((SqlMultiValueExpr)node,context);
            
                case SqlExistsExpr:
                return optimizeSqlExistsExpr((SqlExistsExpr)node,context);
            
                case SqlIntervalExpr:
                return optimizeSqlIntervalExpr((SqlIntervalExpr)node,context);
            
                case SqlCaseExpr:
                return optimizeSqlCaseExpr((SqlCaseExpr)node,context);
            
                case SqlCaseWhenItem:
                return optimizeSqlCaseWhenItem((SqlCaseWhenItem)node,context);
            
                case SqlCastExpr:
                return optimizeSqlCastExpr((SqlCastExpr)node,context);
            
                case SqlTypeExpr:
                return optimizeSqlTypeExpr((SqlTypeExpr)node,context);
            
                case SqlCommit:
                return optimizeSqlCommit((SqlCommit)node,context);
            
                case SqlRollback:
                return optimizeSqlRollback((SqlRollback)node,context);
            
        default:
        throw new IllegalArgumentException("invalid ast kind");
        }
    }

    
	public EqlASTNode optimizeSqlProgram(SqlProgram node, C context){
        SqlProgram ret = node;

        
                    if(node.getStatements() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlStatement> statementsOpt = optimizeList(node.getStatements(),true, context);
                            if(statementsOpt != node.getStatements()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(statementsOpt); ret = node.deepClone();}
                                ret.setStatements(statementsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlQualifiedName(SqlQualifiedName node, C context){
        SqlQualifiedName ret = node;

        
                    if(node.getNext() != null){
                    
                            io.nop.orm.eql.ast.SqlQualifiedName nextOpt = (io.nop.orm.eql.ast.SqlQualifiedName)optimize(node.getNext(),context);
                            if(nextOpt != node.getNext()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { nextOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setNext(nextOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlTableName(SqlTableName node, C context){
        SqlTableName ret = node;

        
                    if(node.getOwner() != null){
                    
                            io.nop.orm.eql.ast.SqlQualifiedName ownerOpt = (io.nop.orm.eql.ast.SqlQualifiedName)optimize(node.getOwner(),context);
                            if(ownerOpt != node.getOwner()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { ownerOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setOwner(ownerOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlColumnName(SqlColumnName node, C context){
        SqlColumnName ret = node;

        
                    if(node.getOwner() != null){
                    
                            io.nop.orm.eql.ast.SqlQualifiedName ownerOpt = (io.nop.orm.eql.ast.SqlQualifiedName)optimize(node.getOwner(),context);
                            if(ownerOpt != node.getOwner()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { ownerOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setOwner(ownerOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlInsert(SqlInsert node, C context){
        SqlInsert ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getTableName() != null){
                    
                            io.nop.orm.eql.ast.SqlTableName tableNameOpt = (io.nop.orm.eql.ast.SqlTableName)optimize(node.getTableName(),context);
                            if(tableNameOpt != node.getTableName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { tableNameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTableName(tableNameOpt);
                            }
                        
                    }
                
                    if(node.getColumns() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlColumnName> columnsOpt = optimizeList(node.getColumns(),true, context);
                            if(columnsOpt != node.getColumns()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(columnsOpt); ret = node.deepClone();}
                                ret.setColumns(columnsOpt);
                            }
                        
                    }
                
                    if(node.getSelect() != null){
                    
                            io.nop.orm.eql.ast.SqlSelect selectOpt = (io.nop.orm.eql.ast.SqlSelect)optimize(node.getSelect(),context);
                            if(selectOpt != node.getSelect()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { selectOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setSelect(selectOpt);
                            }
                        
                    }
                
                    if(node.getValues() != null){
                    
                            io.nop.orm.eql.ast.SqlValues valuesOpt = (io.nop.orm.eql.ast.SqlValues)optimize(node.getValues(),context);
                            if(valuesOpt != node.getValues()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valuesOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValues(valuesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlValues(SqlValues node, C context){
        SqlValues ret = node;

        
                    if(node.getValues() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlExpr> valuesOpt = optimizeList(node.getValues(),true, context);
                            if(valuesOpt != node.getValues()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(valuesOpt); ret = node.deepClone();}
                                ret.setValues(valuesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlUpdate(SqlUpdate node, C context){
        SqlUpdate ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getTableName() != null){
                    
                            io.nop.orm.eql.ast.SqlTableName tableNameOpt = (io.nop.orm.eql.ast.SqlTableName)optimize(node.getTableName(),context);
                            if(tableNameOpt != node.getTableName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { tableNameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTableName(tableNameOpt);
                            }
                        
                    }
                
                    if(node.getAlias() != null){
                    
                            io.nop.orm.eql.ast.SqlAlias aliasOpt = (io.nop.orm.eql.ast.SqlAlias)optimize(node.getAlias(),context);
                            if(aliasOpt != node.getAlias()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { aliasOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setAlias(aliasOpt);
                            }
                        
                    }
                
                    if(node.getAssignments() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlAssignment> assignmentsOpt = optimizeList(node.getAssignments(),true, context);
                            if(assignmentsOpt != node.getAssignments()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(assignmentsOpt); ret = node.deepClone();}
                                ret.setAssignments(assignmentsOpt);
                            }
                        
                    }
                
                    if(node.getWhere() != null){
                    
                            io.nop.orm.eql.ast.SqlWhere whereOpt = (io.nop.orm.eql.ast.SqlWhere)optimize(node.getWhere(),context);
                            if(whereOpt != node.getWhere()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { whereOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setWhere(whereOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlAlias(SqlAlias node, C context){
        SqlAlias ret = node;

        
		return ret;
	}
    
	public EqlASTNode optimizeSqlAssignment(SqlAssignment node, C context){
        SqlAssignment ret = node;

        
                    if(node.getColumnName() != null){
                    
                            io.nop.orm.eql.ast.SqlColumnName columnNameOpt = (io.nop.orm.eql.ast.SqlColumnName)optimize(node.getColumnName(),context);
                            if(columnNameOpt != node.getColumnName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { columnNameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setColumnName(columnNameOpt);
                            }
                        
                    }
                
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlDelete(SqlDelete node, C context){
        SqlDelete ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getTableName() != null){
                    
                            io.nop.orm.eql.ast.SqlTableName tableNameOpt = (io.nop.orm.eql.ast.SqlTableName)optimize(node.getTableName(),context);
                            if(tableNameOpt != node.getTableName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { tableNameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTableName(tableNameOpt);
                            }
                        
                    }
                
                    if(node.getAlias() != null){
                    
                            io.nop.orm.eql.ast.SqlAlias aliasOpt = (io.nop.orm.eql.ast.SqlAlias)optimize(node.getAlias(),context);
                            if(aliasOpt != node.getAlias()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { aliasOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setAlias(aliasOpt);
                            }
                        
                    }
                
                    if(node.getWhere() != null){
                    
                            io.nop.orm.eql.ast.SqlWhere whereOpt = (io.nop.orm.eql.ast.SqlWhere)optimize(node.getWhere(),context);
                            if(whereOpt != node.getWhere()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { whereOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setWhere(whereOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlWhere(SqlWhere node, C context){
        SqlWhere ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlCteStatement(SqlCteStatement node, C context){
        SqlCteStatement ret = node;

        
                    if(node.getStatement() != null){
                    
                            io.nop.orm.eql.ast.SqlSelect statementOpt = (io.nop.orm.eql.ast.SqlSelect)optimize(node.getStatement(),context);
                            if(statementOpt != node.getStatement()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { statementOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setStatement(statementOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlSelectWithCte(SqlSelectWithCte node, C context){
        SqlSelectWithCte ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getWithCtes() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlCteStatement> withCtesOpt = optimizeList(node.getWithCtes(),true, context);
                            if(withCtesOpt != node.getWithCtes()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(withCtesOpt); ret = node.deepClone();}
                                ret.setWithCtes(withCtesOpt);
                            }
                        
                    }
                
                    if(node.getSelect() != null){
                    
                            io.nop.orm.eql.ast.SqlSelect selectOpt = (io.nop.orm.eql.ast.SqlSelect)optimize(node.getSelect(),context);
                            if(selectOpt != node.getSelect()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { selectOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setSelect(selectOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlQuerySelect(SqlQuerySelect node, C context){
        SqlQuerySelect ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getProjections() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlProjection> projectionsOpt = optimizeList(node.getProjections(),true, context);
                            if(projectionsOpt != node.getProjections()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(projectionsOpt); ret = node.deepClone();}
                                ret.setProjections(projectionsOpt);
                            }
                        
                    }
                
                    if(node.getFrom() != null){
                    
                            io.nop.orm.eql.ast.SqlFrom fromOpt = (io.nop.orm.eql.ast.SqlFrom)optimize(node.getFrom(),context);
                            if(fromOpt != node.getFrom()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { fromOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setFrom(fromOpt);
                            }
                        
                    }
                
                    if(node.getWhere() != null){
                    
                            io.nop.orm.eql.ast.SqlWhere whereOpt = (io.nop.orm.eql.ast.SqlWhere)optimize(node.getWhere(),context);
                            if(whereOpt != node.getWhere()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { whereOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setWhere(whereOpt);
                            }
                        
                    }
                
                    if(node.getGroupBy() != null){
                    
                            io.nop.orm.eql.ast.SqlGroupBy groupByOpt = (io.nop.orm.eql.ast.SqlGroupBy)optimize(node.getGroupBy(),context);
                            if(groupByOpt != node.getGroupBy()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { groupByOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setGroupBy(groupByOpt);
                            }
                        
                    }
                
                    if(node.getHaving() != null){
                    
                            io.nop.orm.eql.ast.SqlHaving havingOpt = (io.nop.orm.eql.ast.SqlHaving)optimize(node.getHaving(),context);
                            if(havingOpt != node.getHaving()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { havingOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setHaving(havingOpt);
                            }
                        
                    }
                
                    if(node.getOrderBy() != null){
                    
                            io.nop.orm.eql.ast.SqlOrderBy orderByOpt = (io.nop.orm.eql.ast.SqlOrderBy)optimize(node.getOrderBy(),context);
                            if(orderByOpt != node.getOrderBy()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { orderByOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setOrderBy(orderByOpt);
                            }
                        
                    }
                
                    if(node.getLimit() != null){
                    
                            io.nop.orm.eql.ast.SqlLimit limitOpt = (io.nop.orm.eql.ast.SqlLimit)optimize(node.getLimit(),context);
                            if(limitOpt != node.getLimit()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { limitOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLimit(limitOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlParameterMarker(SqlParameterMarker node, C context){
        SqlParameterMarker ret = node;

        
		return ret;
	}
    
	public EqlASTNode optimizeSqlHaving(SqlHaving node, C context){
        SqlHaving ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlDecorator(SqlDecorator node, C context){
        SqlDecorator ret = node;

        
                    if(node.getArgs() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlLiteral> argsOpt = optimizeList(node.getArgs(),true, context);
                            if(argsOpt != node.getArgs()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(argsOpt); ret = node.deepClone();}
                                ret.setArgs(argsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlUnionSelect(SqlUnionSelect node, C context){
        SqlUnionSelect ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getLeft() != null){
                    
                            io.nop.orm.eql.ast.SqlSelect leftOpt = (io.nop.orm.eql.ast.SqlSelect)optimize(node.getLeft(),context);
                            if(leftOpt != node.getLeft()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { leftOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLeft(leftOpt);
                            }
                        
                    }
                
                    if(node.getRight() != null){
                    
                            io.nop.orm.eql.ast.SqlSelect rightOpt = (io.nop.orm.eql.ast.SqlSelect)optimize(node.getRight(),context);
                            if(rightOpt != node.getRight()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { rightOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRight(rightOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlExprProjection(SqlExprProjection node, C context){
        SqlExprProjection ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
                    if(node.getAlias() != null){
                    
                            io.nop.orm.eql.ast.SqlAlias aliasOpt = (io.nop.orm.eql.ast.SqlAlias)optimize(node.getAlias(),context);
                            if(aliasOpt != node.getAlias()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { aliasOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setAlias(aliasOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlAllProjection(SqlAllProjection node, C context){
        SqlAllProjection ret = node;

        
                    if(node.getOwner() != null){
                    
                            io.nop.orm.eql.ast.SqlQualifiedName ownerOpt = (io.nop.orm.eql.ast.SqlQualifiedName)optimize(node.getOwner(),context);
                            if(ownerOpt != node.getOwner()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { ownerOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setOwner(ownerOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlOrderBy(SqlOrderBy node, C context){
        SqlOrderBy ret = node;

        
                    if(node.getItems() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlOrderByItem> itemsOpt = optimizeList(node.getItems(),true, context);
                            if(itemsOpt != node.getItems()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(itemsOpt); ret = node.deepClone();}
                                ret.setItems(itemsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlGroupBy(SqlGroupBy node, C context){
        SqlGroupBy ret = node;

        
                    if(node.getItems() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlGroupByItem> itemsOpt = optimizeList(node.getItems(),true, context);
                            if(itemsOpt != node.getItems()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(itemsOpt); ret = node.deepClone();}
                                ret.setItems(itemsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlGroupByItem(SqlGroupByItem node, C context){
        SqlGroupByItem ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlOrderByItem(SqlOrderByItem node, C context){
        SqlOrderByItem ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlLimit(SqlLimit node, C context){
        SqlLimit ret = node;

        
                    if(node.getLimit() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr limitOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getLimit(),context);
                            if(limitOpt != node.getLimit()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { limitOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLimit(limitOpt);
                            }
                        
                    }
                
                    if(node.getOffset() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr offsetOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getOffset(),context);
                            if(offsetOpt != node.getOffset()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { offsetOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setOffset(offsetOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlFrom(SqlFrom node, C context){
        SqlFrom ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getTableSources() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlTableSource> tableSourcesOpt = optimizeList(node.getTableSources(),true, context);
                            if(tableSourcesOpt != node.getTableSources()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(tableSourcesOpt); ret = node.deepClone();}
                                ret.setTableSources(tableSourcesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlSingleTableSource(SqlSingleTableSource node, C context){
        SqlSingleTableSource ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getTableName() != null){
                    
                            io.nop.orm.eql.ast.SqlTableName tableNameOpt = (io.nop.orm.eql.ast.SqlTableName)optimize(node.getTableName(),context);
                            if(tableNameOpt != node.getTableName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { tableNameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTableName(tableNameOpt);
                            }
                        
                    }
                
                    if(node.getAlias() != null){
                    
                            io.nop.orm.eql.ast.SqlAlias aliasOpt = (io.nop.orm.eql.ast.SqlAlias)optimize(node.getAlias(),context);
                            if(aliasOpt != node.getAlias()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { aliasOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setAlias(aliasOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlJoinTableSource(SqlJoinTableSource node, C context){
        SqlJoinTableSource ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getLeft() != null){
                    
                            io.nop.orm.eql.ast.SqlTableSource leftOpt = (io.nop.orm.eql.ast.SqlTableSource)optimize(node.getLeft(),context);
                            if(leftOpt != node.getLeft()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { leftOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLeft(leftOpt);
                            }
                        
                    }
                
                    if(node.getRight() != null){
                    
                            io.nop.orm.eql.ast.SqlTableSource rightOpt = (io.nop.orm.eql.ast.SqlTableSource)optimize(node.getRight(),context);
                            if(rightOpt != node.getRight()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { rightOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRight(rightOpt);
                            }
                        
                    }
                
                    if(node.getCondition() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr conditionOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getCondition(),context);
                            if(conditionOpt != node.getCondition()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { conditionOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setCondition(conditionOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlSubqueryTableSource(SqlSubqueryTableSource node, C context){
        SqlSubqueryTableSource ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getQuery() != null){
                    
                            io.nop.orm.eql.ast.SqlSelect queryOpt = (io.nop.orm.eql.ast.SqlSelect)optimize(node.getQuery(),context);
                            if(queryOpt != node.getQuery()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { queryOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setQuery(queryOpt);
                            }
                        
                    }
                
                    if(node.getAlias() != null){
                    
                            io.nop.orm.eql.ast.SqlAlias aliasOpt = (io.nop.orm.eql.ast.SqlAlias)optimize(node.getAlias(),context);
                            if(aliasOpt != node.getAlias()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { aliasOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setAlias(aliasOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlNotExpr(SqlNotExpr node, C context){
        SqlNotExpr ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlAndExpr(SqlAndExpr node, C context){
        SqlAndExpr ret = node;

        
                    if(node.getLeft() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr leftOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getLeft(),context);
                            if(leftOpt != node.getLeft()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { leftOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLeft(leftOpt);
                            }
                        
                    }
                
                    if(node.getRight() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr rightOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getRight(),context);
                            if(rightOpt != node.getRight()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { rightOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRight(rightOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlOrExpr(SqlOrExpr node, C context){
        SqlOrExpr ret = node;

        
                    if(node.getLeft() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr leftOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getLeft(),context);
                            if(leftOpt != node.getLeft()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { leftOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLeft(leftOpt);
                            }
                        
                    }
                
                    if(node.getRight() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr rightOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getRight(),context);
                            if(rightOpt != node.getRight()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { rightOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRight(rightOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlStringLiteral(SqlStringLiteral node, C context){
        SqlStringLiteral ret = node;

        
		return ret;
	}
    
	public EqlASTNode optimizeSqlNumberLiteral(SqlNumberLiteral node, C context){
        SqlNumberLiteral ret = node;

        
		return ret;
	}
    
	public EqlASTNode optimizeSqlDateTimeLiteral(SqlDateTimeLiteral node, C context){
        SqlDateTimeLiteral ret = node;

        
		return ret;
	}
    
	public EqlASTNode optimizeSqlHexadecimalLiteral(SqlHexadecimalLiteral node, C context){
        SqlHexadecimalLiteral ret = node;

        
		return ret;
	}
    
	public EqlASTNode optimizeSqlBitValueLiteral(SqlBitValueLiteral node, C context){
        SqlBitValueLiteral ret = node;

        
		return ret;
	}
    
	public EqlASTNode optimizeSqlBooleanLiteral(SqlBooleanLiteral node, C context){
        SqlBooleanLiteral ret = node;

        
		return ret;
	}
    
	public EqlASTNode optimizeSqlNullLiteral(SqlNullLiteral node, C context){
        SqlNullLiteral ret = node;

        
		return ret;
	}
    
	public EqlASTNode optimizeSqlBinaryExpr(SqlBinaryExpr node, C context){
        SqlBinaryExpr ret = node;

        
                    if(node.getLeft() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr leftOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getLeft(),context);
                            if(leftOpt != node.getLeft()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { leftOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLeft(leftOpt);
                            }
                        
                    }
                
                    if(node.getRight() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr rightOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getRight(),context);
                            if(rightOpt != node.getRight()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { rightOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRight(rightOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlIsNullExpr(SqlIsNullExpr node, C context){
        SqlIsNullExpr ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlCompareWithQueryExpr(SqlCompareWithQueryExpr node, C context){
        SqlCompareWithQueryExpr ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
                    if(node.getQuery() != null){
                    
                            io.nop.orm.eql.ast.SqlSubQueryExpr queryOpt = (io.nop.orm.eql.ast.SqlSubQueryExpr)optimize(node.getQuery(),context);
                            if(queryOpt != node.getQuery()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { queryOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setQuery(queryOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlSubQueryExpr(SqlSubQueryExpr node, C context){
        SqlSubQueryExpr ret = node;

        
                    if(node.getSelect() != null){
                    
                            io.nop.orm.eql.ast.SqlSelect selectOpt = (io.nop.orm.eql.ast.SqlSelect)optimize(node.getSelect(),context);
                            if(selectOpt != node.getSelect()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { selectOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setSelect(selectOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlInQueryExpr(SqlInQueryExpr node, C context){
        SqlInQueryExpr ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
                    if(node.getQuery() != null){
                    
                            io.nop.orm.eql.ast.SqlSubQueryExpr queryOpt = (io.nop.orm.eql.ast.SqlSubQueryExpr)optimize(node.getQuery(),context);
                            if(queryOpt != node.getQuery()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { queryOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setQuery(queryOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlInValuesExpr(SqlInValuesExpr node, C context){
        SqlInValuesExpr ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
                    if(node.getValues() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlExpr> valuesOpt = optimizeList(node.getValues(),true, context);
                            if(valuesOpt != node.getValues()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(valuesOpt); ret = node.deepClone();}
                                ret.setValues(valuesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlBetweenExpr(SqlBetweenExpr node, C context){
        SqlBetweenExpr ret = node;

        
                    if(node.getTest() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr testOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getTest(),context);
                            if(testOpt != node.getTest()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { testOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTest(testOpt);
                            }
                        
                    }
                
                    if(node.getBegin() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr beginOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getBegin(),context);
                            if(beginOpt != node.getBegin()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { beginOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBegin(beginOpt);
                            }
                        
                    }
                
                    if(node.getEnd() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr endOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getEnd(),context);
                            if(endOpt != node.getEnd()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { endOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setEnd(endOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlLikeExpr(SqlLikeExpr node, C context){
        SqlLikeExpr ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
                    if(node.getValue() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr valueOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
                    if(node.getEscape() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr escapeOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getEscape(),context);
                            if(escapeOpt != node.getEscape()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { escapeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setEscape(escapeOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlUnaryExpr(SqlUnaryExpr node, C context){
        SqlUnaryExpr ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlAggregateFunction(SqlAggregateFunction node, C context){
        SqlAggregateFunction ret = node;

        
                    if(node.getArgs() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlExpr> argsOpt = optimizeList(node.getArgs(),true, context);
                            if(argsOpt != node.getArgs()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(argsOpt); ret = node.deepClone();}
                                ret.setArgs(argsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlRegularFunction(SqlRegularFunction node, C context){
        SqlRegularFunction ret = node;

        
                    if(node.getArgs() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlExpr> argsOpt = optimizeList(node.getArgs(),true, context);
                            if(argsOpt != node.getArgs()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(argsOpt); ret = node.deepClone();}
                                ret.setArgs(argsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlMultiValueExpr(SqlMultiValueExpr node, C context){
        SqlMultiValueExpr ret = node;

        
                    if(node.getValues() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlExpr> valuesOpt = optimizeList(node.getValues(),true, context);
                            if(valuesOpt != node.getValues()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(valuesOpt); ret = node.deepClone();}
                                ret.setValues(valuesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlExistsExpr(SqlExistsExpr node, C context){
        SqlExistsExpr ret = node;

        
                    if(node.getQuery() != null){
                    
                            io.nop.orm.eql.ast.SqlSubQueryExpr queryOpt = (io.nop.orm.eql.ast.SqlSubQueryExpr)optimize(node.getQuery(),context);
                            if(queryOpt != node.getQuery()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { queryOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setQuery(queryOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlIntervalExpr(SqlIntervalExpr node, C context){
        SqlIntervalExpr ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlCaseExpr(SqlCaseExpr node, C context){
        SqlCaseExpr ret = node;

        
                    if(node.getTest() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr testOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getTest(),context);
                            if(testOpt != node.getTest()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { testOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTest(testOpt);
                            }
                        
                    }
                
                    if(node.getCaseWhens() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlCaseWhenItem> caseWhensOpt = optimizeList(node.getCaseWhens(),true, context);
                            if(caseWhensOpt != node.getCaseWhens()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(caseWhensOpt); ret = node.deepClone();}
                                ret.setCaseWhens(caseWhensOpt);
                            }
                        
                    }
                
                    if(node.getElseExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr elseExprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getElseExpr(),context);
                            if(elseExprOpt != node.getElseExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { elseExprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setElseExpr(elseExprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlCaseWhenItem(SqlCaseWhenItem node, C context){
        SqlCaseWhenItem ret = node;

        
                    if(node.getWhen() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr whenOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getWhen(),context);
                            if(whenOpt != node.getWhen()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { whenOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setWhen(whenOpt);
                            }
                        
                    }
                
                    if(node.getThen() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr thenOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getThen(),context);
                            if(thenOpt != node.getThen()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { thenOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setThen(thenOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlCastExpr(SqlCastExpr node, C context){
        SqlCastExpr ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.orm.eql.ast.SqlExpr exprOpt = (io.nop.orm.eql.ast.SqlExpr)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
                    if(node.getDataType() != null){
                    
                            io.nop.orm.eql.ast.SqlTypeExpr dataTypeOpt = (io.nop.orm.eql.ast.SqlTypeExpr)optimize(node.getDataType(),context);
                            if(dataTypeOpt != node.getDataType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { dataTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setDataType(dataTypeOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlTypeExpr(SqlTypeExpr node, C context){
        SqlTypeExpr ret = node;

        
		return ret;
	}
    
	public EqlASTNode optimizeSqlCommit(SqlCommit node, C context){
        SqlCommit ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public EqlASTNode optimizeSqlRollback(SqlRollback node, C context){
        SqlRollback ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.orm.eql.ast.SqlDecorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
}
// resume CPD analysis - CPD-ON
