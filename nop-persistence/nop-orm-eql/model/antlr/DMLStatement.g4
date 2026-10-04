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

grammar DMLStatement;

import Symbol, Keyword, SQL92Keyword, Literals, BaseRule;

// 必须包含into
sqlInsert
    : decorators=sqlDecorators_? INSERT INTO tableName=sqlTableName LP_ <indent> columns=columnNames_ <br> RP_
        ( values=sqlValues | select=sqlSelect)
    ;

sqlUpdate
    : decorators=sqlDecorators_? UPDATE tableName=sqlTableName (AS? alias=sqlAlias?)
    (<br> assignments=sqlAssignments_)
    (<br> where=sqlWhere)?
    (RETURNING (returnAll=ASTERISK_ | returnProjections=sqlProjections_))?
    ;

sqlAssignments_
    : SET e=sqlAssignment (COMMA_ <indent> e=sqlAssignment)*
    ;

sqlAssignment
    : columnName=sqlColumnName EQ_ expr=sqlExpr
    ;

sqlValues:
    values=sqlValues_;

sqlValues_
    : VALUES  LP_ <indent> e=sqlExpr (COMMA_ e=sqlExpr)* (<br> RP_)
//    | LP_ RP_
    ;

//assignmentValue
//    : expr //| DEFAULT | blobValue
//    ;

//blobValue
//    : STRING_
//    ;

sqlDelete
    : decorators=sqlDecorators_? DELETE FROM tableName=sqlTableName AS? alias=sqlAlias? (<br> where=sqlWhere)?
    ;

//singleTableClause
//    : FROM tableName AS? alias?
//    ;

sqlSelectWithCte:
    decorators=sqlDecorators_? withCtes=sqlCteStatements_ select=sqlSelect;

sqlCteStatement
    : recursive=RECURSIVE? name=sqlIdentifier_ AS LP_ statement=sqlSelect RP_
    ;

sqlCteStatements_
    : WITH e=sqlCteStatement (COMMA_ e=sqlCteStatement)*
    ;

sqlSelect
    :
     sqlQuerySelect #SqlQuerySelect_ex
     | LP_ sqlSelect RP_ # SqlSelect_ex
     | left=sqlSelect decorators=sqlDecorators_? unionType=unionType_  right=sqlSelect #SqlUnionSelect_ex
    ;


//sqlUnionSelect
//    : left=sqlQuerySelect decorators=sqlDecorators_? unionType=unionType_  right=sqlSelect #SQLUnionSelect_ex2
//    | LP_ sqlQuerySelect RP_ decorators=sqlDecorators_? unionType=unionType_  right=sqlSelect #SQLUnionSelect_ex2
//    ;

unionType_:
    (UNION|INTERSECT|EXCEPT) (ALL)?;

sqlQuerySelect
    : decorators=sqlDecorators_? SELECT distinct=distinct_?
        (selectAll=ASTERISK_ | projections=sqlProjections_)
        (<br> from=sqlFrom)?
        (<br> where=sqlWhere)?
        (<br> groupBy=sqlGroupBy)?
        (<br> having=sqlHaving)?
        (<br> windowClause=sqlWindowClause)?
        (<br> orderBy=sqlOrderBy)?
        (<br> limit=sqlLimit)?
        (<br> forUpdate=forUpdate_)?
    ;

//selectSpecification
//    : duplicateSpecification
//    ;

sqlProjections_
    : e=sqlProjection (COMMA_ e=sqlProjection)*
    ;

sqlProjection
    : sqlExprProjection | sqlAllProjection
    ;

sqlExprProjection:
    expr=sqlExpr AS? alias=sqlAlias? decorators=sqlDecorators_?;

sqlAllProjection:
    owner=sqlQualifiedName DOT_ASTERISK_;

sqlAlias
    : alias=sqlAlias_
    ;

sqlAlias_:
    sqlIdentifier_ | STRING_;

sqlFrom
    : FROM decorators=sqlDecorators_? tableSources=tableSources_
    ;

tableSources_
//    : escapedTableReference (COMMA_ escapedTableReference)*
    : e=sqlTableSource (COMMA_ e=sqlTableSource)*
    ;

//escapedTableReference
//    : tableReference  | LBE_ tableReference RBE_
//    ;

sqlTableSource:
    sqlSingleTableSource  #SqlSingleTableSource_ex
    | sqlSubqueryTableSource #SqlSubqueryTableSource_ex
    | sqlTumbleTableSource #SqlTumbleTableSource_ex
    | left=sqlTableSource  joinType=joinType_ right=sqlTableSource_joinRight  (ON condition=sqlExpr)? #SqlJoinTableSource
    ;

sqlSingleTableSource:
    tableName=sqlTableName AS? ({_input.LA(1) != CROSS || _input.LA(2) != JOIN}?
      alias=sqlAlias)? decorators=sqlDecorators_?;

// WI17: TUMBLE(t, INTERVAL) 流时间切片伪表函数（D4 裁定，stream-only 目标）。
// t 为限定的时间列（表名.列名，bigint epoch-millis），INTERVAL 为窗口时长；
// 对应新 AST 类 SqlTumbleTableSource（WI1 一一对应硬约束）。
sqlTumbleTableSource:
    TUMBLE LP_ tableName=sqlIdentifier_ DOT_ timeColumn=sqlIdentifier_ COMMA_
    interval=sqlIntervalExpr RP_ AS? alias=sqlAlias? decorators=sqlDecorators_?
    ;

sqlSubqueryTableSource:
   lateral=LATERAL? LP_  query=sqlSelect RP_ AS? alias=sqlAlias;

//sqlJoinTableSource
//    : left=sqlSingleTableSource <indent> joinType=joinType_ right=sqlTableSource_joinRight <indent> (ON condition=sqlExpr)?
//     | left=sqlJoinTableSource <indent> joinType=joinType_ right=sqlTableSource_joinRight <indent> (ON condition=sqlExpr)?
//    ;


joinType_:
    crossJoin_ | innerJoin_ | leftJoin_ | rightJoin_ | fullJoin_;

sqlTableSource_joinRight:
    sqlSingleTableSource | sqlSubqueryTableSource;

innerJoin_:
    INNER? JOIN;

// WI2 BUG-6: 补 OUTER? 与 leftJoin_/rightJoin_ 形态对齐（修复前 `full outer join`
// 解析失败 expected=JOIN）。OUTER/FULL 均为关键字 token，不在 unreservedWord_，无别名歧义
fullJoin_:
   FULL OUTER? JOIN;

leftJoin_:
    LEFT OUTER? JOIN;

rightJoin_:
    RIGHT OUTER? JOIN;

// WI1 BUG-10: cross join 此前被误解析（cross 经 unreservedWord_ 消费为别名）。
// CROSS 不移出 unreservedWord_ 与各方言 reservedKeywords（roadmap 明令）
crossJoin_:
    CROSS JOIN;

sqlWhere
    : WHERE expr=sqlExpr
    ;

sqlGroupBy
    : GROUP BY items=sqlGroupByItems_
    ;

sqlGroupByItems_:
      e=sqlGroupByItem (COMMA_ e=sqlGroupByItem)*;

//groupWindow
//    : regularFunction;


sqlHaving
    : HAVING expr=sqlExpr
    ;

// WI7（roadmap EC ①，ai-dev/plans/26-eql-wi7-pagination-and-nulls.md）：SQL 标准分页
// 两形态归一化进既有 SqlLimit{limit, offset}（裁定=候选 A，ai-dev/design/eql-cross-db-compat/
// 2026-10-04-wi7-phase0-adjudications.md §一/§二——buildPageExpr 无形态参数、输出形态由方言
// paginationHandler 决定，不加"语法形态"字段；ast/_gen 零触碰）。三备选全 altLabel
// （sqlIntervalExpr 先例）；新备选 prop 独立命名（duplicate-prop-label 约束）；FIRST 集
// {LIMIT}/{FETCH}/{OFFSET} 两两不相交无 LL(*) 冲突。FETCH 备选的手写 visit 完全重写
// （SqlLimit 无 mandatory 校验 ⇒ 生成空节点会静默通过 validate）。token 面零改动：
// FETCH/FIRST/ONLY/ROW/ROWS（SQL92Keyword.g4）与 NEXT（Keyword.g4）已是保留 token，
// 零 lexer 改动。OFFSET n ROWS（无 FETCH）不进本 WI（plan Deferred But Adjudicated）。
sqlLimit
    : LIMIT limit=sqlExpr_limitRowCount (OFFSET offset=sqlExpr_limitOffset)? # SqlLimit_limit
    | FETCH FIRST rowCount=sqlExpr_limitRowCount ROWS ONLY # SqlLimit_fetchFirst
    | OFFSET offsetExpr=sqlExpr_limitOffset ROWS FETCH NEXT limitExpr=sqlExpr_limitRowCount ROWS ONLY # SqlLimit_offsetFetch
    ;

sqlExpr_limitRowCount
    : sqlNumberLiteral | sqlParameterMarker
    ;

sqlExpr_limitOffset
    : sqlNumberLiteral | sqlParameterMarker
    ;

sqlSubQueryExpr
    : LP_ <indent> select=sqlSelect <br> RP_
    ;


// window_type ::= TUMBLING some_interval
//               | HOPPING  some_interval some_interval
//               | SESSION  some_interval

//INTERVAL <num> <time_unit>

forUpdate_
    : FOR UPDATE
    ;
