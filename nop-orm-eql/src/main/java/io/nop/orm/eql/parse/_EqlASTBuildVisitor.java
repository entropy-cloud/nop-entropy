
package io.nop.orm.eql.parse;

import io.nop.orm.eql.parse.antlr.EqlBaseVisitor;
import io.nop.orm.eql.parse.antlr.EqlParser.*;
import io.nop.api.core.exceptions.NopException;  //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.util.SourceLocation; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.commons.util.CollectionHelper;//NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.commons.util.StringHelper;//NOPMD - suppressed UnusedImports - Auto Gen Code
import org.antlr.v4.runtime.tree.ParseTree;
import io.nop.antlr4.common.ParseTreeHelper;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import io.nop.orm.eql.ast.EqlASTNode;



// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public abstract class _EqlASTBuildVisitor extends EqlBaseVisitor<EqlASTNode>{

      public io.nop.orm.eql.ast.SqlAndExpr visitSqlAndExpr(SqlAndExprContext ctx){
          io.nop.orm.eql.ast.SqlAndExpr ret = new io.nop.orm.eql.ast.SqlAndExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.left != null){
               ret.setLeft((visitSqlExpr(ctx.left)));
            }
            if(ctx.right != null){
               ret.setRight((visitSqlExpr(ctx.right)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlBetweenExpr visitSqlBetweenExpr(SqlBetweenExprContext ctx){
          io.nop.orm.eql.ast.SqlBetweenExpr ret = new io.nop.orm.eql.ast.SqlBetweenExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.test != null){
               ret.setTest((visitSqlExpr_bit(ctx.test)));
            }
            if(ctx.not != null){
               ret.setNot((SqlBetweenExpr_not(ctx.not)));
            }
            if(ctx.begin != null){
               ret.setBegin((visitSqlExpr_bit(ctx.begin)));
            }
            if(ctx.end != null){
               ret.setEnd((visitSqlExpr_predicate(ctx.end)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlBinaryExpr visitSqlBinaryExpr(SqlBinaryExprContext ctx){
          io.nop.orm.eql.ast.SqlBinaryExpr ret = new io.nop.orm.eql.ast.SqlBinaryExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.left != null){
               ret.setLeft((visitSqlExpr_bit(ctx.left)));
            }
            if(ctx.operator != null){
               ret.setOperator((SqlBinaryExpr_operator(ctx.operator)));
            }
            if(ctx.right != null){
               ret.setRight((visitSqlExpr_bit(ctx.right)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlBinaryExpr visitSqlBinaryExpr_compare(SqlBinaryExpr_compareContext ctx){
          io.nop.orm.eql.ast.SqlBinaryExpr ret = new io.nop.orm.eql.ast.SqlBinaryExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.left != null){
               ret.setLeft((visitSqlExpr_primary(ctx.left)));
            }
            if(ctx.operator != null){
               ret.setOperator((SqlBinaryExpr_operator(ctx.operator)));
            }
            if(ctx.right != null){
               ret.setRight((visitSqlExpr_predicate(ctx.right)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlCompareWithQueryExpr visitSqlCompareWithQueryExpr(SqlCompareWithQueryExprContext ctx){
          io.nop.orm.eql.ast.SqlCompareWithQueryExpr ret = new io.nop.orm.eql.ast.SqlCompareWithQueryExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr_primary(ctx.expr)));
            }
            if(ctx.operator != null){
               ret.setOperator((SqlCompareWithQueryExpr_operator(ctx.operator)));
            }
            if(ctx.compareRange != null){
               ret.setCompareRange((SqlCompareWithQueryExpr_compareRange(ctx.compareRange)));
            }
            if(ctx.query != null){
               ret.setQuery((visitSqlSubQueryExpr(ctx.query)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
        public io.nop.orm.eql.ast.SqlExpr visitSqlExpr_bit2(SqlExpr_bit2Context ctx){
           SqlExpr_bitContext node = ctx.sqlExpr_bit();
           return node == null ? null : visitSqlExpr_bit(node);
        }
      
        public io.nop.orm.eql.ast.SqlExpr visitSqlExpr_predicate2(SqlExpr_predicate2Context ctx){
           SqlExpr_predicateContext node = ctx.sqlExpr_predicate();
           return node == null ? null : visitSqlExpr_predicate(node);
        }
      
        public io.nop.orm.eql.ast.SqlExpr visitSqlExpr_primary2(SqlExpr_primary2Context ctx){
           SqlExpr_primaryContext node = ctx.sqlExpr_primary();
           return node == null ? null : visitSqlExpr_primary(node);
        }
      
        public io.nop.orm.eql.ast.SqlExpr visitSqlExpr_simple2(SqlExpr_simple2Context ctx){
           SqlExpr_simpleContext node = ctx.sqlExpr_simple();
           return node == null ? null : visitSqlExpr_simple(node);
        }
      
      public io.nop.orm.eql.ast.SqlInQueryExpr visitSqlInQueryExpr(SqlInQueryExprContext ctx){
          io.nop.orm.eql.ast.SqlInQueryExpr ret = new io.nop.orm.eql.ast.SqlInQueryExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr_bit(ctx.expr)));
            }
            if(ctx.not != null){
               ret.setNot((SqlInQueryExpr_not(ctx.not)));
            }
            if(ctx.query != null){
               ret.setQuery((visitSqlSubQueryExpr(ctx.query)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlInValuesExpr visitSqlInValuesExpr(SqlInValuesExprContext ctx){
          io.nop.orm.eql.ast.SqlInValuesExpr ret = new io.nop.orm.eql.ast.SqlInValuesExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr_bit(ctx.expr)));
            }
            if(ctx.not != null){
               ret.setNot((SqlInValuesExpr_not(ctx.not)));
            }
            if(ctx.values != null){
               ret.setValues((buildSqlInValues_(ctx.values)));
            }else{
               ret.setValues(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlIsNullExpr visitSqlIsNullExpr(SqlIsNullExprContext ctx){
          io.nop.orm.eql.ast.SqlIsNullExpr ret = new io.nop.orm.eql.ast.SqlIsNullExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr_primary(ctx.expr)));
            }
            if(ctx.not != null){
               ret.setNot((SqlIsNullExpr_not(ctx.not)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlJoinTableSource visitSqlJoinTableSource(SqlJoinTableSourceContext ctx){
          io.nop.orm.eql.ast.SqlJoinTableSource ret = new io.nop.orm.eql.ast.SqlJoinTableSource();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.left != null){
               ret.setLeft((visitSqlTableSource(ctx.left)));
            }
            if(ctx.joinType != null){
               ret.setJoinType((SqlJoinTableSource_joinType(ctx.joinType)));
            }
            if(ctx.right != null){
               ret.setRight((visitSqlTableSource_joinRight(ctx.right)));
            }
            if(ctx.condition != null){
               ret.setCondition((visitSqlExpr(ctx.condition)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlLikeExpr visitSqlLikeExpr(SqlLikeExprContext ctx){
          io.nop.orm.eql.ast.SqlLikeExpr ret = new io.nop.orm.eql.ast.SqlLikeExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr_bit(ctx.expr)));
            }
            if(ctx.not != null){
               ret.setNot((SqlLikeExpr_not(ctx.not)));
            }
            if(ctx.ignoreCase != null){
               ret.setIgnoreCase((SqlLikeExpr_ignoreCase(ctx.ignoreCase)));
            }
            if(ctx.value != null){
               ret.setValue((visitSqlExpr_simple(ctx.value)));
            }
            if(ctx.escape != null){
               ret.setEscape((visitSqlExpr_simple(ctx.escape)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlNotExpr visitSqlNotExpr(SqlNotExprContext ctx){
          io.nop.orm.eql.ast.SqlNotExpr ret = new io.nop.orm.eql.ast.SqlNotExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr(ctx.expr)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlOrExpr visitSqlOrExpr(SqlOrExprContext ctx){
          io.nop.orm.eql.ast.SqlOrExpr ret = new io.nop.orm.eql.ast.SqlOrExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.left != null){
               ret.setLeft((visitSqlExpr(ctx.left)));
            }
            if(ctx.right != null){
               ret.setRight((visitSqlExpr(ctx.right)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
        public io.nop.orm.eql.ast.SqlQuerySelect visitSqlQuerySelect_ex(SqlQuerySelect_exContext ctx){
           SqlQuerySelectContext node = ctx.sqlQuerySelect();
           return node == null ? null : visitSqlQuerySelect(node);
        }
      
        public io.nop.orm.eql.ast.SqlSelect visitSqlSelect_ex(SqlSelect_exContext ctx){
           SqlSelectContext node = ctx.sqlSelect();
           return node == null ? null : visitSqlSelect(node);
        }
      
        public io.nop.orm.eql.ast.SqlSingleTableSource visitSqlSingleTableSource_ex(SqlSingleTableSource_exContext ctx){
           SqlSingleTableSourceContext node = ctx.sqlSingleTableSource();
           return node == null ? null : visitSqlSingleTableSource(node);
        }
      
        public io.nop.orm.eql.ast.SqlSubqueryTableSource visitSqlSubqueryTableSource_ex(SqlSubqueryTableSource_exContext ctx){
           SqlSubqueryTableSourceContext node = ctx.sqlSubqueryTableSource();
           return node == null ? null : visitSqlSubqueryTableSource(node);
        }
      
      public io.nop.orm.eql.ast.SqlUnionSelect visitSqlUnionSelect_ex(SqlUnionSelect_exContext ctx){
          io.nop.orm.eql.ast.SqlUnionSelect ret = new io.nop.orm.eql.ast.SqlUnionSelect();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.left != null){
               ret.setLeft((visitSqlSelect(ctx.left)));
            }
            if(ctx.decorators != null){
               ret.setDecorators((buildSqlDecorators_(ctx.decorators)));
            }else{
               ret.setDecorators(Collections.emptyList());
            }
            
            if(ctx.unionType != null){
               ret.setUnionType((SqlUnionSelect_unionType(ctx.unionType)));
            }
            if(ctx.right != null){
               ret.setRight((visitSqlSelect(ctx.right)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.orm.eql.ast.SqlCaseWhenItem> buildCaseWhens_(CaseWhens_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlCaseWhenItem> list = new ArrayList<>();
    List<SqlCaseWhenItemContext> elms = ctx.sqlCaseWhenItem();
    if(elms != null){
      for(SqlCaseWhenItemContext elm: elms){
         list.add(visitSqlCaseWhenItem(elm));
      }
    }
    return list;
}
      
public java.util.List<io.nop.orm.eql.ast.SqlColumnName> buildColumnNames_(ColumnNames_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlColumnName> list = new ArrayList<>();
    List<SqlColumnNameContext> elms = ctx.sqlColumnName();
    if(elms != null){
      for(SqlColumnNameContext elm: elms){
         list.add(visitSqlColumnName(elm));
      }
    }
    return list;
}
      
public java.util.List<io.nop.orm.eql.ast.SqlLiteral> buildDecoratorArgs_(DecoratorArgs_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlLiteral> list = new ArrayList<>();
    List<SqlLiteralContext> elms = ctx.sqlLiteral();
    if(elms != null){
      for(SqlLiteralContext elm: elms){
         list.add(visitSqlLiteral(elm));
      }
    }
    return list;
}
      
public java.util.List<io.nop.orm.eql.ast.SqlExpr> buildFunctionArgs_(FunctionArgs_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlExpr> list = new ArrayList<>();
    List<SqlExprContext> elms = ctx.sqlExpr();
    if(elms != null){
      for(SqlExprContext elm: elms){
         list.add(visitSqlExpr(elm));
      }
    }
    return list;
}
      
      public io.nop.orm.eql.ast.SqlAggregateFunction visitSqlAggregateFunction(SqlAggregateFunctionContext ctx){
          io.nop.orm.eql.ast.SqlAggregateFunction ret = new io.nop.orm.eql.ast.SqlAggregateFunction();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((SqlAggregateFunction_name(ctx.name)));
            }
            if(ctx.distinct != null){
               ret.setDistinct((SqlAggregateFunction_distinct(ctx.distinct)));
            }
            if(ctx.args != null){
               ret.setArgs((buildFunctionArgs_(ctx.args)));
            }else{
               ret.setArgs(Collections.emptyList());
            }
            
            if(ctx.selectAll != null){
               ret.setSelectAll((SqlAggregateFunction_selectAll(ctx.selectAll)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlAlias visitSqlAlias(SqlAliasContext ctx){
          io.nop.orm.eql.ast.SqlAlias ret = new io.nop.orm.eql.ast.SqlAlias();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.alias != null){
               ret.setAlias((SqlAlias_alias(ctx.alias)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlAllProjection visitSqlAllProjection(SqlAllProjectionContext ctx){
          io.nop.orm.eql.ast.SqlAllProjection ret = new io.nop.orm.eql.ast.SqlAllProjection();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.owner != null){
               ret.setOwner((visitSqlQualifiedName(ctx.owner)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlAssignment visitSqlAssignment(SqlAssignmentContext ctx){
          io.nop.orm.eql.ast.SqlAssignment ret = new io.nop.orm.eql.ast.SqlAssignment();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.columnName != null){
               ret.setColumnName((visitSqlColumnName(ctx.columnName)));
            }
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr(ctx.expr)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.orm.eql.ast.SqlAssignment> buildSqlAssignments_(SqlAssignments_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlAssignment> list = new ArrayList<>();
    List<SqlAssignmentContext> elms = ctx.sqlAssignment();
    if(elms != null){
      for(SqlAssignmentContext elm: elms){
         list.add(visitSqlAssignment(elm));
      }
    }
    return list;
}
      
      public io.nop.orm.eql.ast.SqlBitValueLiteral visitSqlBitValueLiteral(SqlBitValueLiteralContext ctx){
          io.nop.orm.eql.ast.SqlBitValueLiteral ret = new io.nop.orm.eql.ast.SqlBitValueLiteral();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.value != null){
               ret.setValue((SqlBitValueLiteral_value(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlBooleanLiteral visitSqlBooleanLiteral(SqlBooleanLiteralContext ctx){
          io.nop.orm.eql.ast.SqlBooleanLiteral ret = new io.nop.orm.eql.ast.SqlBooleanLiteral();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.value != null){
               ret.setValue((SqlBooleanLiteral_value(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlCaseExpr visitSqlCaseExpr(SqlCaseExprContext ctx){
          io.nop.orm.eql.ast.SqlCaseExpr ret = new io.nop.orm.eql.ast.SqlCaseExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.test != null){
               ret.setTest((visitSqlExpr_simple(ctx.test)));
            }
            if(ctx.caseWhens != null){
               ret.setCaseWhens((buildCaseWhens_(ctx.caseWhens)));
            }else{
               ret.setCaseWhens(Collections.emptyList());
            }
            
            if(ctx.elseExpr != null){
               ret.setElseExpr((visitSqlExpr(ctx.elseExpr)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlCaseWhenItem visitSqlCaseWhenItem(SqlCaseWhenItemContext ctx){
          io.nop.orm.eql.ast.SqlCaseWhenItem ret = new io.nop.orm.eql.ast.SqlCaseWhenItem();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.when != null){
               ret.setWhen((visitSqlExpr(ctx.when)));
            }
            if(ctx.then != null){
               ret.setThen((visitSqlExpr(ctx.then)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlCastExpr visitSqlCastExpr(SqlCastExprContext ctx){
          io.nop.orm.eql.ast.SqlCastExpr ret = new io.nop.orm.eql.ast.SqlCastExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr(ctx.expr)));
            }
            if(ctx.dataType != null){
               ret.setDataType((visitSqlTypeExpr(ctx.dataType)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlColumnName visitSqlColumnName(SqlColumnNameContext ctx){
          io.nop.orm.eql.ast.SqlColumnName ret = new io.nop.orm.eql.ast.SqlColumnName();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.owner != null){
               ret.setOwner((visitSqlQualifiedName(ctx.owner)));
            }
            if(ctx.name != null){
               ret.setName((SqlColumnName_name(ctx.name)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlCommit visitSqlCommit(SqlCommitContext ctx){
          io.nop.orm.eql.ast.SqlCommit ret = new io.nop.orm.eql.ast.SqlCommit();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlCteStatement visitSqlCteStatement(SqlCteStatementContext ctx){
          io.nop.orm.eql.ast.SqlCteStatement ret = new io.nop.orm.eql.ast.SqlCteStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((SqlCteStatement_name(ctx.name)));
            }
            if(ctx.statement != null){
               ret.setStatement((visitSqlSelect(ctx.statement)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.orm.eql.ast.SqlCteStatement> buildSqlCteStatements_(SqlCteStatements_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlCteStatement> list = new ArrayList<>();
    List<SqlCteStatementContext> elms = ctx.sqlCteStatement();
    if(elms != null){
      for(SqlCteStatementContext elm: elms){
         list.add(visitSqlCteStatement(elm));
      }
    }
    return list;
}
      
      public io.nop.orm.eql.ast.SqlDateTimeLiteral visitSqlDateTimeLiteral(SqlDateTimeLiteralContext ctx){
          io.nop.orm.eql.ast.SqlDateTimeLiteral ret = new io.nop.orm.eql.ast.SqlDateTimeLiteral();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.type != null){
               ret.setType((SqlDateTimeLiteral_type(ctx.type)));
            }
            if(ctx.value != null){
               ret.setValue((SqlDateTimeLiteral_value(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlDecorator visitSqlDecorator(SqlDecoratorContext ctx){
          io.nop.orm.eql.ast.SqlDecorator ret = new io.nop.orm.eql.ast.SqlDecorator();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((SqlDecorator_name(ctx.name)));
            }
            if(ctx.args != null){
               ret.setArgs((buildDecoratorArgs_(ctx.args)));
            }else{
               ret.setArgs(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.orm.eql.ast.SqlDecorator> buildSqlDecorators_(SqlDecorators_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlDecorator> list = new ArrayList<>();
    List<SqlDecoratorContext> elms = ctx.sqlDecorator();
    if(elms != null){
      for(SqlDecoratorContext elm: elms){
         list.add(visitSqlDecorator(elm));
      }
    }
    return list;
}
      
      public io.nop.orm.eql.ast.SqlDelete visitSqlDelete(SqlDeleteContext ctx){
          io.nop.orm.eql.ast.SqlDelete ret = new io.nop.orm.eql.ast.SqlDelete();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.decorators != null){
               ret.setDecorators((buildSqlDecorators_(ctx.decorators)));
            }else{
               ret.setDecorators(Collections.emptyList());
            }
            
            if(ctx.tableName != null){
               ret.setTableName((visitSqlTableName(ctx.tableName)));
            }
            if(ctx.alias != null){
               ret.setAlias((visitSqlAlias(ctx.alias)));
            }
            if(ctx.where != null){
               ret.setWhere((visitSqlWhere(ctx.where)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlDmlStatement visitSqlDmlStatement(SqlDmlStatementContext ctx){
        
            return (io.nop.orm.eql.ast.SqlDmlStatement)this.visitChildren(ctx);
          
      }
            
      public io.nop.orm.eql.ast.SqlExistsExpr visitSqlExistsExpr(SqlExistsExprContext ctx){
          io.nop.orm.eql.ast.SqlExistsExpr ret = new io.nop.orm.eql.ast.SqlExistsExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.query != null){
               ret.setQuery((visitSqlSubQueryExpr(ctx.query)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlExpr visitSqlExpr(SqlExprContext ctx){
        
            return (io.nop.orm.eql.ast.SqlExpr)ctx.accept(this);
          
      }
            
      public io.nop.orm.eql.ast.SqlExprProjection visitSqlExprProjection(SqlExprProjectionContext ctx){
          io.nop.orm.eql.ast.SqlExprProjection ret = new io.nop.orm.eql.ast.SqlExprProjection();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr(ctx.expr)));
            }
            if(ctx.alias != null){
               ret.setAlias((visitSqlAlias(ctx.alias)));
            }
            if(ctx.decorators != null){
               ret.setDecorators((buildSqlDecorators_(ctx.decorators)));
            }else{
               ret.setDecorators(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlExpr visitSqlExpr_bit(SqlExpr_bitContext ctx){
        
            return (io.nop.orm.eql.ast.SqlExpr)ctx.accept(this);
          
      }
            
        public io.nop.orm.eql.ast.SqlExpr visitSqlExpr_brace(SqlExpr_braceContext ctx){
           SqlExprContext node = ctx.sqlExpr();
           return node == null ? null : visitSqlExpr(node);
        }
      
      public io.nop.orm.eql.ast.SqlExpr visitSqlExpr_functionCall(SqlExpr_functionCallContext ctx){
        
            return (io.nop.orm.eql.ast.SqlExpr)this.visitChildren(ctx);
          
      }
            
      public io.nop.orm.eql.ast.SqlExpr visitSqlExpr_limitOffset(SqlExpr_limitOffsetContext ctx){
        
            return (io.nop.orm.eql.ast.SqlExpr)this.visitChildren(ctx);
          
      }
            
      public io.nop.orm.eql.ast.SqlExpr visitSqlExpr_limitRowCount(SqlExpr_limitRowCountContext ctx){
        
            return (io.nop.orm.eql.ast.SqlExpr)this.visitChildren(ctx);
          
      }
            
      public io.nop.orm.eql.ast.SqlExpr visitSqlExpr_predicate(SqlExpr_predicateContext ctx){
        
            return (io.nop.orm.eql.ast.SqlExpr)ctx.accept(this);
          
      }
            
      public io.nop.orm.eql.ast.SqlExpr visitSqlExpr_primary(SqlExpr_primaryContext ctx){
        
            return (io.nop.orm.eql.ast.SqlExpr)ctx.accept(this);
          
      }
            
      public io.nop.orm.eql.ast.SqlExpr visitSqlExpr_simple(SqlExpr_simpleContext ctx){
        
            return (io.nop.orm.eql.ast.SqlExpr)this.visitChildren(ctx);
          
      }
            
        public io.nop.orm.eql.ast.SqlCastExpr visitSqlExpr_special(SqlExpr_specialContext ctx){
           SqlCastExprContext node = ctx.sqlCastExpr();
           return node == null ? null : visitSqlCastExpr(node);
        }
      
      public io.nop.orm.eql.ast.SqlFrom visitSqlFrom(SqlFromContext ctx){
          io.nop.orm.eql.ast.SqlFrom ret = new io.nop.orm.eql.ast.SqlFrom();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.decorators != null){
               ret.setDecorators((buildSqlDecorators_(ctx.decorators)));
            }else{
               ret.setDecorators(Collections.emptyList());
            }
            
            if(ctx.tableSources != null){
               ret.setTableSources((buildTableSources_(ctx.tableSources)));
            }else{
               ret.setTableSources(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlGroupBy visitSqlGroupBy(SqlGroupByContext ctx){
          io.nop.orm.eql.ast.SqlGroupBy ret = new io.nop.orm.eql.ast.SqlGroupBy();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.items != null){
               ret.setItems((buildSqlGroupByItems_(ctx.items)));
            }else{
               ret.setItems(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlGroupByItem visitSqlGroupByItem(SqlGroupByItemContext ctx){
          io.nop.orm.eql.ast.SqlGroupByItem ret = new io.nop.orm.eql.ast.SqlGroupByItem();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr(ctx.expr)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.orm.eql.ast.SqlGroupByItem> buildSqlGroupByItems_(SqlGroupByItems_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlGroupByItem> list = new ArrayList<>();
    List<SqlGroupByItemContext> elms = ctx.sqlGroupByItem();
    if(elms != null){
      for(SqlGroupByItemContext elm: elms){
         list.add(visitSqlGroupByItem(elm));
      }
    }
    return list;
}
      
      public io.nop.orm.eql.ast.SqlHaving visitSqlHaving(SqlHavingContext ctx){
          io.nop.orm.eql.ast.SqlHaving ret = new io.nop.orm.eql.ast.SqlHaving();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr(ctx.expr)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlHexadecimalLiteral visitSqlHexadecimalLiteral(SqlHexadecimalLiteralContext ctx){
          io.nop.orm.eql.ast.SqlHexadecimalLiteral ret = new io.nop.orm.eql.ast.SqlHexadecimalLiteral();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.value != null){
               ret.setValue((SqlHexadecimalLiteral_value(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.orm.eql.ast.SqlExpr> buildSqlInValues_(SqlInValues_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlExpr> list = new ArrayList<>();
    List<SqlExprContext> elms = ctx.sqlExpr();
    if(elms != null){
      for(SqlExprContext elm: elms){
         list.add(visitSqlExpr(elm));
      }
    }
    return list;
}
      
      public io.nop.orm.eql.ast.SqlInsert visitSqlInsert(SqlInsertContext ctx){
          io.nop.orm.eql.ast.SqlInsert ret = new io.nop.orm.eql.ast.SqlInsert();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.decorators != null){
               ret.setDecorators((buildSqlDecorators_(ctx.decorators)));
            }else{
               ret.setDecorators(Collections.emptyList());
            }
            
            if(ctx.tableName != null){
               ret.setTableName((visitSqlTableName(ctx.tableName)));
            }
            if(ctx.columns != null){
               ret.setColumns((buildColumnNames_(ctx.columns)));
            }else{
               ret.setColumns(Collections.emptyList());
            }
            
            if(ctx.values != null){
               ret.setValues((visitSqlValues(ctx.values)));
            }
            if(ctx.select != null){
               ret.setSelect((visitSqlSelect(ctx.select)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlIntervalExpr visitSqlIntervalExpr(SqlIntervalExprContext ctx){
          io.nop.orm.eql.ast.SqlIntervalExpr ret = new io.nop.orm.eql.ast.SqlIntervalExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr(ctx.expr)));
            }
            if(ctx.intervalUnit != null){
               ret.setIntervalUnit((SqlIntervalExpr_intervalUnit(ctx.intervalUnit)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlLimit visitSqlLimit(SqlLimitContext ctx){
          io.nop.orm.eql.ast.SqlLimit ret = new io.nop.orm.eql.ast.SqlLimit();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.limit != null){
               ret.setLimit((visitSqlExpr_limitRowCount(ctx.limit)));
            }
            if(ctx.offset != null){
               ret.setOffset((visitSqlExpr_limitOffset(ctx.offset)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlLiteral visitSqlLiteral(SqlLiteralContext ctx){
        
            return (io.nop.orm.eql.ast.SqlLiteral)this.visitChildren(ctx);
          
      }
            
      public io.nop.orm.eql.ast.SqlMultiValueExpr visitSqlMultiValueExpr(SqlMultiValueExprContext ctx){
          io.nop.orm.eql.ast.SqlMultiValueExpr ret = new io.nop.orm.eql.ast.SqlMultiValueExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.values != null){
               ret.setValues((buildSqlInValues_(ctx.values)));
            }else{
               ret.setValues(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlNullLiteral visitSqlNullLiteral(SqlNullLiteralContext ctx){
          io.nop.orm.eql.ast.SqlNullLiteral ret = new io.nop.orm.eql.ast.SqlNullLiteral();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlNumberLiteral visitSqlNumberLiteral(SqlNumberLiteralContext ctx){
          io.nop.orm.eql.ast.SqlNumberLiteral ret = new io.nop.orm.eql.ast.SqlNumberLiteral();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.value != null){
               ret.setValue((SqlNumberLiteral_value(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlOrderBy visitSqlOrderBy(SqlOrderByContext ctx){
          io.nop.orm.eql.ast.SqlOrderBy ret = new io.nop.orm.eql.ast.SqlOrderBy();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.items != null){
               ret.setItems((buildSqlOrderByItems_(ctx.items)));
            }else{
               ret.setItems(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlOrderByItem visitSqlOrderByItem(SqlOrderByItemContext ctx){
          io.nop.orm.eql.ast.SqlOrderByItem ret = new io.nop.orm.eql.ast.SqlOrderByItem();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr(ctx.expr)));
            }
            if(ctx.asc != null){
               ret.setAsc((SqlOrderByItem_asc(ctx.asc)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.orm.eql.ast.SqlOrderByItem> buildSqlOrderByItems_(SqlOrderByItems_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlOrderByItem> list = new ArrayList<>();
    List<SqlOrderByItemContext> elms = ctx.sqlOrderByItem();
    if(elms != null){
      for(SqlOrderByItemContext elm: elms){
         list.add(visitSqlOrderByItem(elm));
      }
    }
    return list;
}
      
      public io.nop.orm.eql.ast.SqlParameterMarker visitSqlParameterMarker(SqlParameterMarkerContext ctx){
          io.nop.orm.eql.ast.SqlParameterMarker ret = new io.nop.orm.eql.ast.SqlParameterMarker();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlProgram visitSqlProgram(SqlProgramContext ctx){
          io.nop.orm.eql.ast.SqlProgram ret = new io.nop.orm.eql.ast.SqlProgram();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.statements != null){
               ret.setStatements((buildSqlStatements_(ctx.statements)));
            }else{
               ret.setStatements(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlProjection visitSqlProjection(SqlProjectionContext ctx){
        
            return (io.nop.orm.eql.ast.SqlProjection)this.visitChildren(ctx);
          
      }
            
public java.util.List<io.nop.orm.eql.ast.SqlProjection> buildSqlProjections_(SqlProjections_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlProjection> list = new ArrayList<>();
    List<SqlProjectionContext> elms = ctx.sqlProjection();
    if(elms != null){
      for(SqlProjectionContext elm: elms){
         list.add(visitSqlProjection(elm));
      }
    }
    return list;
}
      
      public io.nop.orm.eql.ast.SqlQualifiedName visitSqlQualifiedName(SqlQualifiedNameContext ctx){
          io.nop.orm.eql.ast.SqlQualifiedName ret = new io.nop.orm.eql.ast.SqlQualifiedName();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((SqlQualifiedName_name(ctx.name)));
            }
            if(ctx.next != null){
               ret.setNext((visitSqlQualifiedName(ctx.next)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlQuerySelect visitSqlQuerySelect(SqlQuerySelectContext ctx){
          io.nop.orm.eql.ast.SqlQuerySelect ret = new io.nop.orm.eql.ast.SqlQuerySelect();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.decorators != null){
               ret.setDecorators((buildSqlDecorators_(ctx.decorators)));
            }else{
               ret.setDecorators(Collections.emptyList());
            }
            
            if(ctx.distinct != null){
               ret.setDistinct((SqlQuerySelect_distinct(ctx.distinct)));
            }
            if(ctx.selectAll != null){
               ret.setSelectAll((SqlQuerySelect_selectAll(ctx.selectAll)));
            }
            if(ctx.projections != null){
               ret.setProjections((buildSqlProjections_(ctx.projections)));
            }else{
               ret.setProjections(Collections.emptyList());
            }
            
            if(ctx.from != null){
               ret.setFrom((visitSqlFrom(ctx.from)));
            }
            if(ctx.where != null){
               ret.setWhere((visitSqlWhere(ctx.where)));
            }
            if(ctx.groupBy != null){
               ret.setGroupBy((visitSqlGroupBy(ctx.groupBy)));
            }
            if(ctx.having != null){
               ret.setHaving((visitSqlHaving(ctx.having)));
            }
            if(ctx.orderBy != null){
               ret.setOrderBy((visitSqlOrderBy(ctx.orderBy)));
            }
            if(ctx.limit != null){
               ret.setLimit((visitSqlLimit(ctx.limit)));
            }
            if(ctx.forUpdate != null){
               ret.setForUpdate((SqlQuerySelect_forUpdate(ctx.forUpdate)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlRegularFunction visitSqlRegularFunction(SqlRegularFunctionContext ctx){
          io.nop.orm.eql.ast.SqlRegularFunction ret = new io.nop.orm.eql.ast.SqlRegularFunction();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((SqlRegularFunction_name(ctx.name)));
            }
            if(ctx.args != null){
               ret.setArgs((buildFunctionArgs_(ctx.args)));
            }else{
               ret.setArgs(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlRollback visitSqlRollback(SqlRollbackContext ctx){
          io.nop.orm.eql.ast.SqlRollback ret = new io.nop.orm.eql.ast.SqlRollback();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlSelect visitSqlSelect(SqlSelectContext ctx){
        
            return (io.nop.orm.eql.ast.SqlSelect)ctx.accept(this);
          
      }
            
      public io.nop.orm.eql.ast.SqlSelectWithCte visitSqlSelectWithCte(SqlSelectWithCteContext ctx){
          io.nop.orm.eql.ast.SqlSelectWithCte ret = new io.nop.orm.eql.ast.SqlSelectWithCte();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.decorators != null){
               ret.setDecorators((buildSqlDecorators_(ctx.decorators)));
            }else{
               ret.setDecorators(Collections.emptyList());
            }
            
            if(ctx.withCtes != null){
               ret.setWithCtes((buildSqlCteStatements_(ctx.withCtes)));
            }else{
               ret.setWithCtes(Collections.emptyList());
            }
            
            if(ctx.select != null){
               ret.setSelect((visitSqlSelect(ctx.select)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlSingleTableSource visitSqlSingleTableSource(SqlSingleTableSourceContext ctx){
          io.nop.orm.eql.ast.SqlSingleTableSource ret = new io.nop.orm.eql.ast.SqlSingleTableSource();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.tableName != null){
               ret.setTableName((visitSqlTableName(ctx.tableName)));
            }
            if(ctx.alias != null){
               ret.setAlias((visitSqlAlias(ctx.alias)));
            }
            if(ctx.decorators != null){
               ret.setDecorators((buildSqlDecorators_(ctx.decorators)));
            }else{
               ret.setDecorators(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlStatement visitSqlStatement(SqlStatementContext ctx){
        
            return (io.nop.orm.eql.ast.SqlStatement)this.visitChildren(ctx);
          
      }
            
public java.util.List<io.nop.orm.eql.ast.SqlStatement> buildSqlStatements_(SqlStatements_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlStatement> list = new ArrayList<>();
    List<SqlStatementContext> elms = ctx.sqlStatement();
    if(elms != null){
      for(SqlStatementContext elm: elms){
         list.add(visitSqlStatement(elm));
      }
    }
    return list;
}
      
      public io.nop.orm.eql.ast.SqlStringLiteral visitSqlStringLiteral(SqlStringLiteralContext ctx){
          io.nop.orm.eql.ast.SqlStringLiteral ret = new io.nop.orm.eql.ast.SqlStringLiteral();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.value != null){
               ret.setValue((SqlStringLiteral_value(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlSubQueryExpr visitSqlSubQueryExpr(SqlSubQueryExprContext ctx){
          io.nop.orm.eql.ast.SqlSubQueryExpr ret = new io.nop.orm.eql.ast.SqlSubQueryExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.select != null){
               ret.setSelect((visitSqlSelect(ctx.select)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlSubqueryTableSource visitSqlSubqueryTableSource(SqlSubqueryTableSourceContext ctx){
          io.nop.orm.eql.ast.SqlSubqueryTableSource ret = new io.nop.orm.eql.ast.SqlSubqueryTableSource();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.lateral != null){
               ret.setLateral((SqlSubqueryTableSource_lateral(ctx.lateral)));
            }
            if(ctx.query != null){
               ret.setQuery((visitSqlSelect(ctx.query)));
            }
            if(ctx.alias != null){
               ret.setAlias((visitSqlAlias(ctx.alias)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlTableName visitSqlTableName(SqlTableNameContext ctx){
          io.nop.orm.eql.ast.SqlTableName ret = new io.nop.orm.eql.ast.SqlTableName();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.owner != null){
               ret.setOwner((visitSqlQualifiedName(ctx.owner)));
            }
            if(ctx.name != null){
               ret.setName((SqlTableName_name(ctx.name)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlTableSource visitSqlTableSource(SqlTableSourceContext ctx){
        
            return (io.nop.orm.eql.ast.SqlTableSource)ctx.accept(this);
          
      }
            
      public io.nop.orm.eql.ast.SqlTableSource visitSqlTableSource_joinRight(SqlTableSource_joinRightContext ctx){
        
            return (io.nop.orm.eql.ast.SqlTableSource)this.visitChildren(ctx);
          
      }
            
      public io.nop.orm.eql.ast.SqlTransactionStatement visitSqlTransactionStatement(SqlTransactionStatementContext ctx){
        
            return (io.nop.orm.eql.ast.SqlTransactionStatement)this.visitChildren(ctx);
          
      }
            
      public io.nop.orm.eql.ast.SqlTypeExpr visitSqlTypeExpr(SqlTypeExprContext ctx){
          io.nop.orm.eql.ast.SqlTypeExpr ret = new io.nop.orm.eql.ast.SqlTypeExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((SqlTypeExpr_name(ctx.name)));
            }
            if(ctx.precision != null){
               ret.setPrecision((SqlTypeExpr_precision(ctx.precision)));
            }
            if(ctx.scale != null){
               ret.setScale((SqlTypeExpr_scale(ctx.scale)));
            }
            if(ctx.characterSet != null){
               ret.setCharacterSet((SqlTypeExpr_characterSet(ctx.characterSet)));
            }
            if(ctx.collate != null){
               ret.setCollate((SqlTypeExpr_collate(ctx.collate)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlUnaryExpr visitSqlUnaryExpr(SqlUnaryExprContext ctx){
          io.nop.orm.eql.ast.SqlUnaryExpr ret = new io.nop.orm.eql.ast.SqlUnaryExpr();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.operator != null){
               ret.setOperator((SqlUnaryExpr_operator(ctx.operator)));
            }
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr_simple(ctx.expr)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlUpdate visitSqlUpdate(SqlUpdateContext ctx){
          io.nop.orm.eql.ast.SqlUpdate ret = new io.nop.orm.eql.ast.SqlUpdate();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.decorators != null){
               ret.setDecorators((buildSqlDecorators_(ctx.decorators)));
            }else{
               ret.setDecorators(Collections.emptyList());
            }
            
            if(ctx.tableName != null){
               ret.setTableName((visitSqlTableName(ctx.tableName)));
            }
            if(ctx.alias != null){
               ret.setAlias((visitSqlAlias(ctx.alias)));
            }
            if(ctx.assignments != null){
               ret.setAssignments((buildSqlAssignments_(ctx.assignments)));
            }else{
               ret.setAssignments(Collections.emptyList());
            }
            
            if(ctx.where != null){
               ret.setWhere((visitSqlWhere(ctx.where)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.orm.eql.ast.SqlValues visitSqlValues(SqlValuesContext ctx){
          io.nop.orm.eql.ast.SqlValues ret = new io.nop.orm.eql.ast.SqlValues();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.values != null){
               ret.setValues((buildSqlValues_(ctx.values)));
            }else{
               ret.setValues(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.orm.eql.ast.SqlExpr> buildSqlValues_(SqlValues_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlExpr> list = new ArrayList<>();
    List<SqlExprContext> elms = ctx.sqlExpr();
    if(elms != null){
      for(SqlExprContext elm: elms){
         list.add(visitSqlExpr(elm));
      }
    }
    return list;
}
      
      public io.nop.orm.eql.ast.SqlWhere visitSqlWhere(SqlWhereContext ctx){
          io.nop.orm.eql.ast.SqlWhere ret = new io.nop.orm.eql.ast.SqlWhere();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitSqlExpr(ctx.expr)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.orm.eql.ast.SqlTableSource> buildTableSources_(TableSources_Context ctx){
    java.util.List<io.nop.orm.eql.ast.SqlTableSource> list = new ArrayList<>();
    List<SqlTableSourceContext> elms = ctx.sqlTableSource();
    if(elms != null){
      for(SqlTableSourceContext elm: elms){
         list.add(visitSqlTableSource(elm));
      }
    }
    return list;
}
      
  /**
   * rules: sqlAggregateFunction
   */
  public abstract boolean SqlAggregateFunction_distinct(ParseTree node);

  /**
   * rules: sqlAggregateFunction
   */
  public abstract boolean SqlAggregateFunction_selectAll(org.antlr.v4.runtime.Token token);

  /**
   * rules: SqlBetweenExpr
   */
  public abstract boolean SqlBetweenExpr_not(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlBooleanLiteral
   */
  public abstract boolean SqlBooleanLiteral_value(org.antlr.v4.runtime.Token token);

  /**
   * rules: SqlInQueryExpr
   */
  public abstract boolean SqlInQueryExpr_not(org.antlr.v4.runtime.Token token);

  /**
   * rules: SqlInValuesExpr
   */
  public abstract boolean SqlInValuesExpr_not(org.antlr.v4.runtime.Token token);

  /**
   * rules: SqlIsNullExpr
   */
  public abstract boolean SqlIsNullExpr_not(org.antlr.v4.runtime.Token token);

  /**
   * rules: SqlLikeExpr
   */
  public abstract boolean SqlLikeExpr_ignoreCase(org.antlr.v4.runtime.Token token);

  /**
   * rules: SqlLikeExpr
   */
  public abstract boolean SqlLikeExpr_not(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlOrderByItem
   */
  public abstract boolean SqlOrderByItem_asc(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlQuerySelect
   */
  public abstract boolean SqlQuerySelect_distinct(ParseTree node);

  /**
   * rules: sqlQuerySelect
   */
  public abstract boolean SqlQuerySelect_forUpdate(ParseTree node);

  /**
   * rules: sqlQuerySelect
   */
  public abstract boolean SqlQuerySelect_selectAll(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlSubqueryTableSource
   */
  public abstract boolean SqlSubqueryTableSource_lateral(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlTypeExpr
   */
  public abstract int SqlTypeExpr_precision(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlTypeExpr
   */
  public abstract int SqlTypeExpr_scale(org.antlr.v4.runtime.Token token);

  /**
   * rules: SqlCompareWithQueryExpr
   */
  public abstract io.nop.orm.eql.enums.SqlCompareRange SqlCompareWithQueryExpr_compareRange(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlDateTimeLiteral
   */
  public abstract io.nop.orm.eql.enums.SqlDateTimeType SqlDateTimeLiteral_type(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlIntervalExpr
   */
  public abstract io.nop.orm.eql.enums.SqlIntervalUnit SqlIntervalExpr_intervalUnit(ParseTree node);

  /**
   * rules: SqlJoinTableSource
   */
  public abstract io.nop.orm.eql.enums.SqlJoinType SqlJoinTableSource_joinType(ParseTree node);

  /**
   * rules: SqlBinaryExpr_compare
   */
  public abstract io.nop.orm.eql.enums.SqlOperator SqlBinaryExpr_operator(ParseTree node);

  /**
   * rules: SqlBinaryExpr
   */
  public abstract io.nop.orm.eql.enums.SqlOperator SqlBinaryExpr_operator(org.antlr.v4.runtime.Token token);

  /**
   * rules: SqlCompareWithQueryExpr
   */
  public abstract io.nop.orm.eql.enums.SqlOperator SqlCompareWithQueryExpr_operator(ParseTree node);

  /**
   * rules: sqlUnaryExpr
   */
  public abstract io.nop.orm.eql.enums.SqlOperator SqlUnaryExpr_operator(org.antlr.v4.runtime.Token token);

  /**
   * rules: SqlUnionSelect_ex
   */
  public abstract io.nop.orm.eql.enums.SqlUnionType SqlUnionSelect_unionType(ParseTree node);

  /**
   * rules: sqlAggregateFunction
   */
  public abstract java.lang.String SqlAggregateFunction_name(ParseTree node);

  /**
   * rules: sqlAlias
   */
  public abstract java.lang.String SqlAlias_alias(ParseTree node);

  /**
   * rules: sqlBitValueLiteral
   */
  public abstract java.lang.String SqlBitValueLiteral_value(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlColumnName
   */
  public abstract java.lang.String SqlColumnName_name(ParseTree node);

  /**
   * rules: sqlCteStatement
   */
  public abstract java.lang.String SqlCteStatement_name(ParseTree node);

  /**
   * rules: sqlDateTimeLiteral
   */
  public abstract java.lang.String SqlDateTimeLiteral_value(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlDecorator
   */
  public abstract java.lang.String SqlDecorator_name(ParseTree node);

  /**
   * rules: sqlHexadecimalLiteral
   */
  public abstract java.lang.String SqlHexadecimalLiteral_value(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlNumberLiteral
   */
  public abstract java.lang.String SqlNumberLiteral_value(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlQualifiedName
   */
  public abstract java.lang.String SqlQualifiedName_name(ParseTree node);

  /**
   * rules: sqlRegularFunction
   */
  public abstract java.lang.String SqlRegularFunction_name(ParseTree node);

  /**
   * rules: sqlStringLiteral
   */
  public abstract java.lang.String SqlStringLiteral_value(org.antlr.v4.runtime.Token token);

  /**
   * rules: sqlTableName
   */
  public abstract java.lang.String SqlTableName_name(ParseTree node);

  /**
   * rules: sqlTypeExpr
   */
  public abstract java.lang.String SqlTypeExpr_characterSet(ParseTree node);

  /**
   * rules: sqlTypeExpr
   */
  public abstract java.lang.String SqlTypeExpr_collate(ParseTree node);

  /**
   * rules: sqlTypeExpr
   */
  public abstract java.lang.String SqlTypeExpr_name(ParseTree node);

}
 // resume CPD analysis - CPD-ON
