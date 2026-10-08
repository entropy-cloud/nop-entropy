//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast;

public enum EqlASTKind{

            SqlProgram, // ordinal: 0
        
            SqlQualifiedName, // ordinal: 1
        
            SqlTableName, // ordinal: 2
        
            SqlColumnName, // ordinal: 3
        
            SqlInsert, // ordinal: 4
        
            SqlValues, // ordinal: 5
        
            SqlUpdate, // ordinal: 6
        
            SqlAlias, // ordinal: 7
        
            SqlAssignment, // ordinal: 8
        
            SqlDelete, // ordinal: 9
        
            SqlWhere, // ordinal: 10
        
            SqlCteStatement, // ordinal: 11
        
            SqlSelectWithCte, // ordinal: 12
        
            SqlQuerySelect, // ordinal: 13
        
            SqlParameterMarker, // ordinal: 14
        
            SqlHaving, // ordinal: 15
        
            SqlDecorator, // ordinal: 16
        
            SqlUnionSelect, // ordinal: 17
        
            SqlExprProjection, // ordinal: 18
        
            SqlAllProjection, // ordinal: 19
        
            SqlPartitionBy, // ordinal: 20
        
            SqlOrderBy, // ordinal: 21
        
            SqlGroupBy, // ordinal: 22
        
            SqlGroupByItem, // ordinal: 23
        
            SqlOrderByItem, // ordinal: 24
        
            SqlLimit, // ordinal: 25
        
            SqlFrom, // ordinal: 26
        
            SqlSingleTableSource, // ordinal: 27
        
            SqlJoinTableSource, // ordinal: 28
        
            SqlSubqueryTableSource, // ordinal: 29
        
            SqlTumbleTableSource, // ordinal: 30
        
            SqlHopTableSource, // ordinal: 31
        
            SqlSessionTableSource, // ordinal: 32
        
            SqlNotExpr, // ordinal: 33
        
            SqlAndExpr, // ordinal: 34
        
            SqlOrExpr, // ordinal: 35
        
            SqlStringLiteral, // ordinal: 36
        
            SqlNumberLiteral, // ordinal: 37
        
            SqlDateTimeLiteral, // ordinal: 38
        
            SqlHexadecimalLiteral, // ordinal: 39
        
            SqlBitValueLiteral, // ordinal: 40
        
            SqlBooleanLiteral, // ordinal: 41
        
            SqlNullLiteral, // ordinal: 42
        
            SqlBinaryExpr, // ordinal: 43
        
            SqlIsNullExpr, // ordinal: 44
        
            SqlCompareWithQueryExpr, // ordinal: 45
        
            SqlSubQueryExpr, // ordinal: 46
        
            SqlInQueryExpr, // ordinal: 47
        
            SqlInValuesExpr, // ordinal: 48
        
            SqlBetweenExpr, // ordinal: 49
        
            SqlLikeExpr, // ordinal: 50
        
            SqlUnaryExpr, // ordinal: 51
        
            SqlAggregateFunction, // ordinal: 52
        
            SqlRegularFunction, // ordinal: 53
        
            SqlWindowExpr, // ordinal: 54
        
            SqlWindowFrame, // ordinal: 55
        
            SqlWindowFrameBound, // ordinal: 56
        
            SqlWindowDecl, // ordinal: 57
        
            SqlWindowClause, // ordinal: 58
        
            SqlMultiValueExpr, // ordinal: 59
        
            SqlExistsExpr, // ordinal: 60
        
            SqlIntervalExpr, // ordinal: 61
        
            SqlCaseExpr, // ordinal: 62
        
            SqlCaseWhenItem, // ordinal: 63
        
            SqlCastExpr, // ordinal: 64
        
            SqlTypeExpr, // ordinal: 65
        
            SqlCommit, // ordinal: 66
        
            SqlRollback, // ordinal: 67
        
            SqlGroupingOp, // ordinal: 68
        
            SqlValuesTableSource, // ordinal: 69
        
}
