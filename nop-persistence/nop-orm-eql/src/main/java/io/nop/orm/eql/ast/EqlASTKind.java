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
        
            SqlNotExpr, // ordinal: 30
        
            SqlAndExpr, // ordinal: 31
        
            SqlOrExpr, // ordinal: 32
        
            SqlStringLiteral, // ordinal: 33
        
            SqlNumberLiteral, // ordinal: 34
        
            SqlDateTimeLiteral, // ordinal: 35
        
            SqlHexadecimalLiteral, // ordinal: 36
        
            SqlBitValueLiteral, // ordinal: 37
        
            SqlBooleanLiteral, // ordinal: 38
        
            SqlNullLiteral, // ordinal: 39
        
            SqlBinaryExpr, // ordinal: 40
        
            SqlIsNullExpr, // ordinal: 41
        
            SqlCompareWithQueryExpr, // ordinal: 42
        
            SqlSubQueryExpr, // ordinal: 43
        
            SqlInQueryExpr, // ordinal: 44
        
            SqlInValuesExpr, // ordinal: 45
        
            SqlBetweenExpr, // ordinal: 46
        
            SqlLikeExpr, // ordinal: 47
        
            SqlUnaryExpr, // ordinal: 48
        
            SqlAggregateFunction, // ordinal: 49
        
            SqlRegularFunction, // ordinal: 50
        
            SqlWindowExpr, // ordinal: 51
        
            SqlMultiValueExpr, // ordinal: 52
        
            SqlExistsExpr, // ordinal: 53
        
            SqlIntervalExpr, // ordinal: 54
        
            SqlCaseExpr, // ordinal: 55
        
            SqlCaseWhenItem, // ordinal: 56
        
            SqlCastExpr, // ordinal: 57
        
            SqlTypeExpr, // ordinal: 58
        
            SqlCommit, // ordinal: 59
        
            SqlRollback, // ordinal: 60
        
}
