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
        (<br> forUpdate=forUpdate_ (nowait=NOWAIT | skipLocked=skipLocked_)?)?
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
    | sqlValuesTableSource #SqlValuesTableSource_ex
    | left=sqlTableSource  joinType=joinType_ right=sqlTableSource_joinRight  (ON condition=sqlExpr)? #SqlJoinTableSource
    ;

sqlSingleTableSource:
    tableName=sqlTableName AS? alias=sqlAlias? decorators=sqlDecorators_?;

// WI17: TUMBLE(t, INTERVAL) 流时间切片伪表函数（D4 裁定，stream-only 目标）。
// t 为限定的时间列（表名.列名，bigint epoch-millis），INTERVAL 为窗口时长；
// 对应新 AST 类 SqlTumbleTableSource（WI1 一一对应硬约束）。
sqlTumbleTableSource:
    TUMBLE LP_ tableName=sqlIdentifier_ DOT_ timeColumn=sqlIdentifier_ COMMA_
    interval=sqlIntervalExpr RP_ AS? alias=sqlAlias? decorators=sqlDecorators_?
    ;

sqlSubqueryTableSource:
   lateral=LATERAL? LP_  query=sqlSelect RP_ AS? alias=sqlAlias
   ;

// WI12 Phase 4（plan 38，裁定 ai-dev/design/eql-cross-db-compat/
// 2026-10-05-wi12-phase0-adjudications.md §4）：FROM (VALUES (1,2),(3,4)) alias(c1,c2) 表源
// （H2 官方文法 Table Expression `( query ) [ [AS] alias [(columnName,...)] ]`，query 含
// tableValue）。rows 复用既有 SqlMultiValueExpr（LP_ expr (COMMA_ expr)* 行结构同构）；
// alias 必填（EQL 相对 H2 收紧——作用域注册与 select * 展开锚点，登记 owner doc 不等价行）；
// 列名清单缺省时元数据列名取 C1..Cn（H2 惯例）。零新 token（VALUES 已在 Keyword.g4）。
sqlValuesTableSource
    : LP_ VALUES rows=sqlRowList_ RP_ AS? alias=sqlAlias sqlColumnAliases__?
    ;

// 行列表 helper（sqlProjections_ 同构：e=X (COMMA_ e=X)* → 生成 buildSqlRowList_）
sqlRowList_
    : e=sqlMultiValueExpr (COMMA_ e=sqlMultiValueExpr)*
    ;

// 别名列清单内联规则（notInAst——`__` 后缀 = AstGrammarBuilder getInnerRule 跳过，
// extractField__/trimDirection__ 先例）：元素文本经手写 visitSqlValuesTableSource 读取，
// 不产生 AST 子节点。
sqlColumnAliases__
    : LP_ sqlIdentifier_ (COMMA_ sqlIdentifier_)* RP_
    ;

//sqlJoinTableSource
//    : left=sqlSingleTableSource <indent> joinType=joinType_ right=sqlTableSource_joinRight <indent> (ON condition=sqlExpr)?
//     | left=sqlJoinTableSource <indent> joinType=joinType_ right=sqlTableSource_joinRight <indent> (ON condition=sqlExpr)?
//    ;


joinType_:
    innerJoin_ | leftJoin_ | rightJoin_ | fullJoin_ | crossJoin_;

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
// Plan 2307 V2（merge 并集）：CROSS JOIN。
//
// 该规则只能覆盖「左表已带别名」的情形（如 `from AppUser u cross join AppRole r`）：
// 此时 cross 不再可能被 sqlSingleTableSource 的可选 alias 吃掉，必须由本规则显式匹配。
//
// 而「左表未带别名」的情形（`from AppUser cross join AppRole r`）中，ANTLR 的 ALL(*)
// 预测面对「alias=cross + 裸 JOIN」与「CROSS JOIN」两条都能完整解析的路径时会取最小备选号，
// 即仍然先吃别名——实测加上本规则并不能改变该处的选择结果。
// 该残余情形由 SqlJoinTableSource#normalize() 在建树时还原为 CROSS_JOIN，
// 故本规则与 normalize() 两者缺一不可，不是冗余定义。
// 【merge note】备选顺序取 master（crossJoin_ 置尾）：master 的
// sqlSingleTableSource 语义谓词（CROSS+JOIN 相邻时不吃别名）已独立保证核心修复，
// 而置尾使 `from a cross join b on <cond>` 按既定边界解析为「别名 cross + 带 ON 普通 join」
// （TestEqlCrossJoinGrammarParse 钉死）；ours 原前置序会令该形态解析为 CROSS_JOIN，
// 与 AstToSqlGenerator 对带条件 CROSS_JOIN 的显式报错口径冲突。
crossJoin_:
    CROSS JOIN;
// 【merge note 2】别名谓词消歧（本分支 WI1 原有 {_input.LA(1) != CROSS || _input.LA(2) != JOIN}?）
// 已移除：与 master 的 normalize() 还原层语义冲突（谓词强制 CROSS_JOIN，吞掉 `cross join b on cond`
// 的别名解读）。合并语义 = master 的「plain alias + normalize 兜底」；本分支 WI1 的 CROSS_JOIN
// 断言经 normalize 后 AST 等价保持（TestEqlCrossJoinParse/Compile 双向验证）。

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
// （sqlIntervalExpr 先例）；FIRST 集 {LIMIT}/{FETCH}/{OFFSET} 两两不相交无 LL(*) 冲突。
// token 面零改动：FETCH/FIRST/ONLY/ROW/ROWS（SQL92Keyword.g4）与 NEXT（Keyword.g4）已是
// 保留 token，零 lexer 改动。OFFSET n ROWS（无 FETCH）不进本 WI（plan Deferred）。
// ⚠️ 执行期实测修订（plan 差异登记）：新备选 prop **复用** limit=/offset=——① antlr4-tool
// codegen（AstGrammarBuilder.buildAstNode）每个 altLabel 独立 AstRule ⇒ duplicate-prop-label
// 是备选内检查、跨备选复用合法；② /nop/templates/antlr/@init.xrun 的 initRuleReturnType 经
// ast.getDefine(astNodeName).getProp(propName).type 解析 visit 返回类型，prop 名必须是 AST
// 节点既有字段（rowCount=/offsetExpr=/limitExpr= 之类独立命名会 renderModel 失败：
// get-prop-on-null-obj）——故 prop 复用不是自由选择而是 codegen 契约；生成 visitSqlLimit_*
// 因此逐字段赋值正确（FETCH 备选 offset 恒缺省=字段不赋值），无需手写 override。
sqlLimit
    : LIMIT limit=sqlExpr_limitRowCount (OFFSET offset=sqlExpr_limitOffset)? # SqlLimit_limit
    | FETCH FIRST limit=sqlExpr_limitRowCount ROWS ONLY # SqlLimit_fetchFirst
    | OFFSET offset=sqlExpr_limitOffset ROWS FETCH NEXT limit=sqlExpr_limitRowCount ROWS ONLY # SqlLimit_offsetFetch
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

skipLocked_
    : SKIP_ LOCKED
    ;
