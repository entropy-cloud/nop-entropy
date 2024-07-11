/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

grammar BaseRule;

import Symbol, Keyword, SQL92Keyword, Literals;

sqlParameterMarker
    : QUESTION_
    ;

sqlLiteral
    : sqlStringLiteral
    | sqlNumberLiteral
    | sqlDateTimeLiteral
    | sqlHexadecimalLiteral
    | sqlBitValueLiteral
    | sqlBooleanLiteral
    | sqlNullLiteral
    ;

sqlStringLiteral
    : value=STRING_ //characterSetName? STRING_ collateClause?
    ;

sqlNumberLiteral
   : value=NUMBER_
   ;

// {d sss} {t sss} {ts ssss} 
sqlDateTimeLiteral
    : type=(DATE | TIME | TIMESTAMP) value=STRING_
    //LBE_ type=sqlDataTimeType_ value=STRING_ RBE_
    ;
//
//sqlDataTimeType_:
//    sqlIdentifier_;

sqlHexadecimalLiteral
    : value=HEX_DIGIT_ //characterSetName? HEX_DIGIT_ collateClause?
    ;

sqlBitValueLiteral
    : value=BIT_NUM_ //characterSetName? BIT_NUM_ collateClause?
    ;

sqlBooleanLiteral
    : value=(TRUE | FALSE)
    ;

sqlNullLiteral
    : NULL
    ;

sqlIdentifier_:
    IDENTIFIER_ | unreservedWord_;

unreservedWord_:
    //: ADA | C92
    CATALOG_NAME | CHARACTER_SET_CATALOG | CHARACTER_SET_NAME | CHARACTER_SET_SCHEMA
    | CLASS_ORIGIN | COBOL | COLLATION_CATALOG | COLLATION_NAME | COLLATION_SCHEMA
    | COLUMN_NAME | COMMAND_FUNCTION | COMMITTED | CONDITION_NUMBER | CONNECTION_NAME
    | CONSTRAINT_CATALOG | CONSTRAINT_NAME | CONSTRAINT_SCHEMA | CURSOR_NAME
    | DATA | DATETIME_INTERVAL_CODE | DATETIME_INTERVAL_PRECISION | DYNAMIC_FUNCTION
    | FORTRAN |SECTION|LANGUAGE|INSENSITIVE|INDICATOR
    | LENGTH
    | MESSAGE_LENGTH | MESSAGE_OCTET_LENGTH | MESSAGE_TEXT | MORE92 | MUMPS
    | NAME | NULLABLE | NUMBER
    | PASCAL | PLI
    | REPEATABLE | RETURNED_LENGTH | RETURNED_OCTET_LENGTH | RETURNED_SQLSTATE | ROW_COUNT
    | SCALE | SCHEMA_NAME | SERIALIZABLE | SERVER_NAME | SUBCLASS_ORIGIN
    | TABLE_NAME | TYPE
    | UNCOMMITTED | UNNAMED
    | VALUE | POSITION | ORDER
    | LEVEL | SESSION |COUNT |COALESCE|YEAR|MONTH
    | LOWER | UPPER | ZONE |WORK
    | RECURSIVE | CURRENT_USER | USER | DATE | OCTET_LENGTH
    | CURRENT_DATE | BIT_LENGTH |GROUP |TIMESTAMP
    | BEGIN|END
    ;

// variable
//     : AT_ AT_  identifier
//     ;

sqlTableName
    : (owner=sqlQualifiedName DOT_)? name=sqlIdentifier_
    ;

sqlColumnName
    : (owner=sqlQualifiedName DOT_)? name=sqlIdentifier_
    ;

//viewName
//    : identifier
//    | (owner DOT_)? identifier
//    ;

sqlQualifiedName
    : name=sqlIdentifier_ (DOT_ next=sqlQualifiedName)?
//    | (owner DOT_)? identifier
    ;

columnNames_
//    : LP_? columnName (COMMA_ columnName)* RP_?
    : e=sqlColumnName (COMMA_ e=sqlColumnName)*
    ;

//tableNames
////    : LP_? tableName (COMMA_ tableName)* RP_?
//    : sqlTableName (COMMA_ sqlTableName)*
//    ;

//characterSetName
//    : IDENTIFIER_
//    ;

sqlExpr
    : left=sqlExpr (AND | AND_) right=sqlExpr # SqlAndExpr
    | left=sqlExpr OR right=sqlExpr # SqlOrExpr
    | (NOT|NOT_) expr=sqlExpr  # SqlNotExpr
    | sqlExpr_primary  # SqlExpr_primary2
    ;


sqlExpr_primary
    : expr=sqlExpr_primary IS not=NOT? NULL # SqlIsNullExpr
   // | booleanPrimary SAFE_EQ_ predicate
    | left=sqlExpr_primary operator=comparisonOperator_ right=sqlExpr_predicate # SqlBinaryExpr_compare
    | expr=sqlExpr_primary operator=comparisonOperator_ compareRange=(ALL | ANY | SOME) query=sqlSubQueryExpr # SqlCompareWithQueryExpr
    | sqlExpr_predicate # SqlExpr_predicate2
    ;

comparisonOperator_
    : EQ_ | GTE_ | GT_ | LTE_ | LT_ | NEQ_
    ;

sqlExpr_predicate
    : expr=sqlExpr_bit not=NOT? IN query=sqlSubQueryExpr   # SqlInQueryExpr
    | expr=sqlExpr_bit not=NOT? IN LP_ values=sqlInValues_ RP_ # SqlInValuesExpr
    | test=sqlExpr_bit not=NOT? BETWEEN begin=sqlExpr_bit AND end=sqlExpr_predicate  # SqlBetweenExpr
    | expr=sqlExpr_bit not=NOT? LIKE value=sqlExpr_simple (ESCAPE escape=sqlExpr_simple)?  # SqlLikeExpr
    | expr=sqlExpr_bit not=NOT? ignoreCase=ILIKE value=sqlExpr_simple (ESCAPE escape=sqlExpr_simple)?  # SqlLikeExpr
    | sqlExpr_bit  # SqlExpr_bit2
    ;

sqlInValues_:
      e=sqlExpr (COMMA_ e=sqlExpr)*;

sqlExpr_bit
    : left=sqlExpr_bit operator=VERTICAL_BAR_ right=sqlExpr_bit  # SqlBinaryExpr
    | left=sqlExpr_bit operator=AMPERSAND_ right=sqlExpr_bit     # SqlBinaryExpr
    | left=sqlExpr_bit operator=SIGNED_LEFT_SHIFT_ right=sqlExpr_bit  # SqlBinaryExpr
    | left=sqlExpr_bit operator=SIGNED_RIGHT_SHIFT_ right=sqlExpr_bit # SqlBinaryExpr
    | left=sqlExpr_bit operator=PLUS_ right=sqlExpr_bit  # SqlBinaryExpr
    | left=sqlExpr_bit operator=MINUS_ right=sqlExpr_bit  # SqlBinaryExpr
    | left=sqlExpr_bit operator=ASTERISK_ right=sqlExpr_bit # SqlBinaryExpr
    | left=sqlExpr_bit operator=SLASH_ right=sqlExpr_bit  # SqlBinaryExpr
    | left=sqlExpr_bit operator=MOD_ right=sqlExpr_bit   # SqlBinaryExpr
    | left=sqlExpr_bit operator=CARET_ right=sqlExpr_bit  # SqlBinaryExpr
//    | left=bitExpr PLUS_ right=sqlIntervalExpr  # SqlPlusIntervalExpr_plus
//    | left=bitExpr MINUS_ right=sqlIntervalExpr # SqlPlusIntervalExpr_minus
    | sqlExpr_simple  # SqlExpr_simple2
    ;

sqlExpr_simple
    : sqlExpr_functionCall
    | sqlWindowExpr
    | sqlParameterMarker
    | sqlLiteral
    | sqlColumnName
//    | simpleExpr COLLATE (STRING_ | sqlIdentifier)
//    | variable
    | sqlSubQueryExpr
    | sqlUnaryExpr
    | sqlExpr_brace
    | sqlMultiValueExpr
    | sqlExistsExpr
//    | LBE_ identifier expr RBE_
//    | matchExpression
    | sqlCaseExpr
    | sqlIntervalExpr
    ;

sqlUnaryExpr:
    operator=(PLUS_ | MINUS_ | TILDE_ | NOT_) expr=sqlExpr_simple;

sqlExpr_brace:
    LP_ sqlExpr RP_;

sqlMultiValueExpr:
    LP_ values=sqlInValues_ RP_;

sqlExistsExpr:
     EXISTS query=sqlSubQueryExpr;

sqlExpr_functionCall
    : sqlAggregateFunction | sqlExpr_special | sqlRegularFunction
    ;

sqlAggregateFunction
    : name=sqlIdentifier_agg_ LP_ distinct=distinct_? (args=functionArgs_ | selectAll=ASTERISK_)? RP_
    ;

sqlWindowExpr
    : function=sqlWindowFunction_ OVER LP_
        partitionBy=sqlPartitionBy orderBy=sqlOrderBy
    RP_
    ;

sqlWindowFunction_: sqlAggregateFunction|sqlRegularFunction;

sqlPartitionBy:
  PARTITION BY items=sqlPartitionByItems_;

sqlPartitionByItems_:
   e=sqlExpr (COMMA_ e=sqlExpr)*;

sqlIdentifier_agg_
    : MAX | MIN | SUM | COUNT | AVG
    ;

distinct_
    : DISTINCT
    ;

functionArgs_:
   e=sqlExpr (COMMA_ e=sqlExpr)*;

// EXTRACT(unit FROM date)
// extract函数在mysql中可以用date(value), time(value)等函数代替
sqlExpr_special
//    : castFunction | convertFunction | positionFunction | substringFunction | extractFunction | trimFunction
    : sqlCastExpr //| extractFunction
    ;

sqlCastExpr
    : CAST LP_ (expr=sqlExpr | NULL) AS dataType=sqlTypeExpr RP_
    ;

//convertFunction
//    : CONVERT LP_ expr USING identifier RP_
//    ;

// positionFunction
//     : POSITION LP_ expr IN expr RP_
//     ;

//substringFunction
//    : SUBSTRING LP_ expr FROM NUMBER_ (FOR NUMBER_)? RP_
//    ;

// EXTRACT( datetime FROM datetime_value)
// extractFunction
//     : EXTRACT LP_ identifier FROM expr RP_
//     ;

//trimFunction
//    : TRIM LP_ (LEADING | BOTH | TRAILING) STRING_ FROM STRING_ RP_
//    ;

sqlRegularFunction
    : name=sqlIdentifier_func_ LP_ args=functionArgs_? RP_
    ;

sqlIdentifier_func_
    : sqlIdentifier_ | IF | CURRENT_TIMESTAMP | LOCALTIME | LOCALTIMESTAMP | INTERVAL | EXTRACT | NULLIF | TRIM
    | SUBSTRING
    ;

sqlDecorators_: e=sqlDecorator (COMMA_ e=sqlDecorator)*;

sqlDecorator: AT_ name=sqlIdentifier_ (LP_ args=decoratorArgs_? RP_)?;

decoratorArgs_: e=sqlLiteral (COMMA_ e=sqlLiteral)*;

// matchExpression
//     : literals MATCH UNIQUE? (PARTIAL | FULL)  subquery
//     ;

sqlCaseExpr
    : CASE test=sqlExpr_simple? caseWhens=caseWhens_ (ELSE elseExpr=sqlExpr)? END
    ;

caseWhens_:
    e=sqlCaseWhenItem+;

sqlCaseWhenItem
    : WHEN when=sqlExpr THEN then=sqlExpr
    ;

sqlIntervalExpr
    : INTERVAL expr=sqlExpr intervalUnit=intervalUnit_
    ;

intervalUnit_
    : MICROSECOND | SECOND | MINUTE | HOUR | DAY | WEEK | MONTH | QUARTER | YEAR
    ;

sqlSubQueryExpr
    : 'Default does not match anything'
    ;

sqlOrderBy
    : ORDER BY items=sqlOrderByItems_
    ;

sqlOrderByItems_:
   e=sqlOrderByItem (COMMA_ e=sqlOrderByItem)*;

sqlOrderByItem
    : expr=sqlExpr asc=(ASC | DESC)?
    ;

sqlGroupByItem
    : expr=sqlExpr
    ;

sqlTypeExpr
    : name=dataTypeName_ ( LP_ precision=NUMBER_ (COMMA_ scale=NUMBER_)? RP_)? characterSet=characterSet_? collate=collateClause_?
    ;

dataTypeName_
    : CHARACTER | CHARACTER VARYING | NATIONAL CHARACTER | NATIONAL CHARACTER VARYING | CHAR | VARCHAR | NCHAR
    | NATIONAL CHAR | NATIONAL CHAR VARYING | BIT | BIT VARYING | NUMERIC | DECIMAL | DEC | INTEGER | SMALLINT
    | FLOAT | REAL | DOUBLE PRECISION | DATE | TIME | TIMESTAMP | INTERVAL | TIME WITH TIME ZONE | TIMESTAMP WITH TIME ZONE
    | sqlIdentifier_
    ;

characterSet_
    : (CHARACTER | CHAR) SET EQ_? characterSet=STRING
    ;

collateClause_
    : COLLATE EQ_? collate=STRING_
    ;

//ignoredIdentifier
//    : sqlIdentifier (DOT_ sqlIdentifier)?
//    ;

//dropBehaviour
//    : (CASCADE | RESTRICT)?
//    ;