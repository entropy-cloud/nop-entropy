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
    | BEGIN|END | CROSS
    | RANGE | WINDOW | GROUPS | ROW | PRECEDING | FOLLOWING | UNBOUNDED
    | TUMBLE
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
    : (NOT|NOT_) expr=sqlExpr  # SqlNotExpr
    | left=sqlExpr (AND | AND_) right=sqlExpr # SqlAndExpr
    | left=sqlExpr OR right=sqlExpr # SqlOrExpr
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
    : left=sqlExpr_bit operator=CARET_ right=sqlExpr_bit  # SqlBinaryExpr
    | left=sqlExpr_bit operator=(ASTERISK_ | SLASH_ | MOD_) right=sqlExpr_bit # SqlBinaryExpr
    | left=sqlExpr_bit operator=(PLUS_ | MINUS_) right=sqlExpr_bit  # SqlBinaryExpr
    | left=sqlExpr_bit operator=(SIGNED_LEFT_SHIFT_ | SIGNED_RIGHT_SHIFT_) right=sqlExpr_bit  # SqlBinaryExpr
    | left=sqlExpr_bit operator=AMPERSAND_ right=sqlExpr_bit     # SqlBinaryExpr
    | left=sqlExpr_bit operator=VERTICAL_BAR_ right=sqlExpr_bit  # SqlBinaryExpr
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
    | sqlCollectionAccessExpr
    ;

sqlCollectionAccessExpr
    : collection=sqlColumnName '[' where=sqlExpr? (ORDER BY orderBy=sqlOrderBy)? ']'
      DOT_ collFuncName=sqlIdentifier_ LP_ collFuncArgs=functionArgs_? RP_
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
    : function=sqlWindowFunction_ OVER
        ( LP_ partitionBy=sqlPartitionBy? orderBy=sqlOrderBy? frame=sqlWindowFrame? RP_
        | windowName=sqlIdentifier_
        )
    ;

sqlWindowFunction_: sqlAggregateFunction|sqlRegularFunction;

sqlPartitionBy:
  PARTITION BY items=sqlPartitionByItems_;

sqlPartitionByItems_:
   e=sqlExpr (COMMA_ e=sqlExpr)*;

sqlWindowFrame
    : unit=sqlWindowFrameUnit_ BETWEEN? start=sqlWindowFrameBound (AND end=sqlWindowFrameBound)?
    ;

sqlWindowFrameUnit_
    : ROWS
    | RANGE
    | GROUPS
    ;

// boundType/offset 由手写 EqlASTBuildVisitor 解析：unbounded preceding/following 优先于 offset 形态
// （同一 prop 不能出现在规则不同备选分支，AstGrammarBuilder 的 duplicate-prop-label 校验）
sqlWindowFrameBound
    : offset=sqlExpr? boundType=sqlWindowFrameBoundType_
    ;

sqlWindowFrameBoundType_
    : UNBOUNDED PRECEDING
    | PRECEDING
    | CURRENT ROW
    | FOLLOWING
    | UNBOUNDED FOLLOWING
    ;

sqlWindowClause
    : WINDOW items=sqlWindowDeclItems_
    ;

sqlWindowDeclItems_:
   e=sqlWindowDecl (COMMA_ e=sqlWindowDecl)*;

sqlWindowDecl
    : name=sqlIdentifier_ AS LP_ partitionBy=sqlPartitionBy? orderBy=sqlOrderBy? frame=sqlWindowFrame? RP_
    ;

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
// WI5: altLabel 三约束（ai-dev/design/eql-cross-db-compat/2026-10-04-wi5-phase0-adjudications.md §3）：
// ① label 以目标 AST 类名为前缀（getAstNodeName 取首段下划线前缀）；② 备选内不写 prop 赋值
// （prop 名必须命中 AST 类真实属性，否则 AstGrammarBuilder 报 ERR_GRAMMAR_INVALID_AST_PROP；
// 生成体只建空节点+validate 必失败，归一化全部在手写 EqlASTBuildVisitor 完全重写、不调 super，
// WI1 visitSqlIntervalExpr_string 同款）；③ 既有 sqlCastExpr 备选同步补 label——全部备选
// label 化后规则级生成 ctx.accept 分派，无 label 备选推导出的 SqlExpr 与子节点类型冲突。
sqlExpr_special
//    : castFunction | convertFunction | positionFunction | substringFunction | extractFunction | trimFunction
    : sqlCastExpr # SqlCastExpr_cast
    // WI5 Phase 1: POSITION(x IN y) → instr(y, x)，调用点换序在构建期完成（instr(str, substr) 参数序）。
    // 首 arg 限定 sqlExpr_bit：IN 是 sqlExpr_predicate 层算子，首参解析不会吞掉分隔符 IN。
    | POSITION LP_ sqlExpr_bit IN sqlExpr RP_ # SqlRegularFunction_position
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
    : INTERVAL expr=sqlExpr intervalUnit=intervalUnit_ # SqlIntervalExpr_unit
    // WI1 BUG-5: PG 形态 `interval '1 day'`。字符串字面量在手写 EqlASTBuildVisitor
    // 的 visitSqlIntervalExpr_string 构建处归一化为 value+unit 装入既有 SqlIntervalExpr
    // （不新增 AST 节点；label 后缀按 astNodeName 规则截断后同为 SqlIntervalExpr）
    | INTERVAL STRING_ # SqlIntervalExpr_string
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
    // WI2 BUG-8: BOOLEAN 是独立关键字 token（不在 unreservedWord_，sqlIdentifier_ 备选
    // 救不了它）。语义链已就绪：SqlTypeExpr_name 的 toUpperCase + StdSqlType.fromStdName
    // 校验放行 StdSqlType.BOOLEAN，生成侧经 dialect.stdToNativeSqlType 产方言映射
    | BOOLEAN
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