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
        
            SqlNotExpr, // ordinal: 31
        
            SqlAndExpr, // ordinal: 32
        
            SqlOrExpr, // ordinal: 33
        
            SqlStringLiteral, // ordinal: 34
        
            SqlNumberLiteral, // ordinal: 35
        
            SqlDateTimeLiteral, // ordinal: 36
        
            SqlHexadecimalLiteral, // ordinal: 37
        
            SqlBitValueLiteral, // ordinal: 38
        
            SqlBooleanLiteral, // ordinal: 39
        
            SqlNullLiteral, // ordinal: 40
        
            SqlBinaryExpr, // ordinal: 41
        
            SqlIsNullExpr, // ordinal: 42
        
            SqlCompareWithQueryExpr, // ordinal: 43
        
            SqlSubQueryExpr, // ordinal: 44
        
            SqlInQueryExpr, // ordinal: 45
        
            SqlInValuesExpr, // ordinal: 46
        
            SqlBetweenExpr, // ordinal: 47
        
            SqlLikeExpr, // ordinal: 48
        
            SqlUnaryExpr, // ordinal: 49
        
            SqlAggregateFunction, // ordinal: 50
        
            SqlRegularFunction, // ordinal: 51
        
            SqlWindowExpr, // ordinal: 52
        
            SqlWindowFrame, // ordinal: 53
        
            SqlWindowFrameBound, // ordinal: 54
        
            SqlWindowDecl, // ordinal: 55
        
            SqlWindowClause, // ordinal: 56
        
            SqlMultiValueExpr, // ordinal: 57
        
            SqlExistsExpr, // ordinal: 58
        
            SqlIntervalExpr, // ordinal: 59
        
            SqlCaseExpr, // ordinal: 60
        
            SqlCaseWhenItem, // ordinal: 61
        
            SqlCastExpr, // ordinal: 62
        
            SqlTypeExpr, // ordinal: 63
        
            SqlCollectionAccessExpr, // ordinal: 64
        
            SqlCommit, // ordinal: 65
        
            SqlRollback, // ordinal: 66
        
}
