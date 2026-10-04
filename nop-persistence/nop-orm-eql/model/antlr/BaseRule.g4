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
    // WI5 Phase 5: 放开五个日期词的关键字遮蔽（roadmap WI5——month()/year() 能解析而
    // day()/hour()/minute()/second()/quarter() 不能，纯属遮蔽不一致）。词法 token 不动
    // （Keyword.g4 保留 token 不删不改）；五词经 sqlIdentifier_ 进入函数名/标识符位置。
    // interval 1 day 等既有形态不受影响（intervalUnit_ 上下文只有该规则可达）；
    // 7 个语料文件对这些词 0 命中（WI0 基线棘轮兜底）。day() 等的归一化日期函数注册
    // 属 WI6（EXTRACT 字段函数已随 WI5 Phase 3 注册的方言上可正常 lowering）。
    | DAY | HOUR | MINUTE | SECOND | QUARTER
    | LOWER | UPPER | ZONE |WORK
    | RECURSIVE | CURRENT_USER | USER | DATE | OCTET_LENGTH
    | CURRENT_DATE | BIT_LENGTH |GROUP |TIMESTAMP
    | BEGIN|END | CROSS
    | RANGE | WINDOW | GROUPS | ROW | PRECEDING | FOLLOWING | UNBOUNDED
    | TUMBLE
    // WI7 Phase 3: NULLS 无保留 token（词法落 IDENTIFIER_），经 unreservedWord_ 进入
    // NULLS FIRST/LAST 子句位置与标识符位置（plan 26 Phase 3）
    | NULLS
    // WI7b Phase 2: ROLLUP/CUBE 有 Literals.g4 显式 token（grouping 操作符），双登记
    // unreservedWord_ 保住标识符位置（列名/别名 rollup/cube 仍可解析——plan 37，
    // ⓒ-A 裁定文档 §3.1）。非 GROUP BY 位置的 rollup(x)/cube(x) 仍经 sqlIdentifier_func_
    // 解析为 SqlRegularFunction，报错依赖函数表未注册（unknown-function）。
    | ROLLUP | CUBE
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
    // WI8 Phase 1（plan 27；裁定 ai-dev/design/eql-cross-db-compat/2026-10-04-wi8-phase0-adjudications.md §九）：
    // IS [NOT] DISTINCT FROM 两备选——label 前缀=目标 AST 类（SqlBinaryExpr/SqlNotExpr），
    // NOT 方向由备选拆分承载（SqlBinaryExpr 无 not prop）；操作数用无 label 规则引用
    //（codegen prop 校验只对带 label 元素做 ast.define 映射——SqlNotExpr 仅 expr prop，
    // 双操作数形态照 SqlGroupByItem_rollup 先例全部经 ctx 访问器在手写 EqlASTBuildVisitor
    // 完全重写不调 super，WI5 position 先例）。
    // DISTINCT/TRUE/FALSE/UNKNOWN 均为保留 token（Keyword.g4/SQL92Keyword.g4），不与 IDENTIFIER_ 竞争。
    | sqlExpr_primary IS NOT DISTINCT FROM sqlExpr_predicate # SqlBinaryExpr_isNotDistinctFrom
    | sqlExpr_primary IS DISTINCT FROM sqlExpr_predicate # SqlNotExpr_isDistinctFrom
    // WI8 Phase 2（plan 27；裁定同上 §一.3/§九）：IS [NOT] TRUE/FALSE/UNKNOWN。
    // isUnknown 保留 expr=/not= label（SqlIsNullExpr 两 prop 俱在，生成体即正确、零手写覆盖）；
    // TRUE/FALSE 四备选走构建期真值表展开（安全词表，手写 visit 完全重写不调 super），
    // 操作数无 label 规则引用（§九 9.2.5 codegen prop 校验机制）。
    | expr=sqlExpr_primary IS not=NOT? UNKNOWN # SqlIsNullExpr_isUnknown
    | sqlExpr_primary IS TRUE # SqlAndExpr_isTrue
    | sqlExpr_primary IS NOT TRUE # SqlNotExpr_isNotTrue
    | sqlExpr_primary IS FALSE # SqlAndExpr_isFalse
    | sqlExpr_primary IS NOT FALSE # SqlNotExpr_isNotFalse
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
    // WI5 Phase 2: SUBSTRING(x FROM a [FOR b]) → substr(x, a[, b])，复用方言函数表
    // substr 的 minArgCount=2/maxArgCount=3 两元多态。首/中/尾 arg 限定 sqlExpr_bit：
    // FROM/FOR 不是 sqlExpr_bit 层算子，参数解析不会吞掉分隔关键字。
    | SUBSTRING LP_ sqlExpr_bit FROM sqlExpr_bit (FOR sqlExpr_bit)? RP_ # SqlRegularFunction_substring
    // WI5 Phase 3: EXTRACT(f FROM d) → 逐字段函数 year(d)/month(d)/...（默认形态已钉死）。
    // 字段用独立 extractField_ 规则（intervalUnit_ 同款先例）：只放行有 lowering 目标的
    // 七个字段，WEEK/MICROSECOND 等文法层即拒绝；字段位置只认这七个 token，与遮蔽放开
    // （unreservedWord_）解耦。
    | EXTRACT LP_ extractField__ FROM sqlExpr RP_ # SqlRegularFunction_extract
    // WI5 Phase 4: TRIM 标准三段式两档——无 chars 形态按方向归一化 trim/ltrim/rtrim
    // （单参已注册）；带 chars 形态归一化 trimChars/ltrimChars/rtrimChars 三件套（逐方言
    // <template> 注册，无模板方言编译期响亮报错，禁止静默丢 chars/降级单参）。
    // 单参 trim(x) 不在此列——仍走 sqlRegularFunction，不新增第二个可解析入口。
    | TRIM LP_ trimDirection__ FROM sqlExpr RP_ # SqlRegularFunction_trimDirection
    | TRIM LP_ trimDirection__ sqlExpr_simple FROM sqlExpr RP_ # SqlRegularFunction_trimDirectionChars
    | TRIM LP_ sqlExpr_simple FROM sqlExpr RP_ # SqlRegularFunction_trimChars
    ;

// WI5: TRIM 方向关键字（SQL92Keyword LEADING/BOTH/TRAILING），仅 TRIM 特殊备选可达。
// 规则名带双下划线后缀 = AstGrammarBuilder notInAst 约定（getInnerRule 跳过，同 extractField__）。
trimDirection__
    : LEADING | BOTH | TRAILING
    ;

// WI5: EXTRACT 允许的 datetime 字段——与归一化目标函数（year/month/day/hour/minute/
// second/quarter，见 default.dialect.xml 及各方言模板）一一同名；扩展字段须先注册函数。
// 规则名带双下划线后缀 = AstGrammarBuilder 的"不属抽象语法树"约定（notInAst）：
// 单下划线规则会被生成器的 getInnerRule 误选为备选 innerRule 且无返回类型（public null 编译错），
// 双下划线被 getInnerRule 显式跳过，备选 innerRule 落到 sqlExpr（生成体被手写 visit 完全重写）。
extractField__
    : YEAR | MONTH | DAY | HOUR | MINUTE | SECOND | QUARTER
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

// WI7（roadmap EC ②，ai-dev/plans/26-eql-wi7-pagination-and-nulls.md Phase 3）：接受
// ORDER BY … NULLS FIRST/LAST，赋值既有 SqlOrderByItem.nullsFirst（Boolean 三态）。
// 门与消费已先于文法落地（Phase 2 AstToSqlGenerator 三分门）；不删除任何 token。
sqlOrderByItem
    : expr=sqlExpr asc=(ASC | DESC)? (NULLS nullsFirst=(FIRST | LAST))?
    ;

// WI7b（roadmap EC ①，ai-dev/plans/37-eql-wi7b-analytic-features.md Phase 2）：GROUP BY 位置
// grouping 操作符 ROLLUP(...)/CUBE(...)。ⓒ-A 裁定（ai-dev/design/eql-cross-db-compat/
// 2026-10-04-wi7b-phase0-adjudications.md §3）：新备选必须**前置**——ROLLUP(a,b) 同时匹配
// 既有 expr=sqlExpr 备选，ANTLR 歧义由备选顺序决定，排后即死代码；label 备选内零 prop 赋值，
// 构建全部在手写 EqlASTBuildVisitor visitSqlGroupByItem_rollup/_cube 完成（wrapper 模式：
// SqlGroupByItem.expr = SqlGroupingOp，visitSqlIntervalExpr_string 同款）；既有备选同步补
// label（多备选规则全 label 是 ANTLR 硬要求，sqlExpr 规则四 label 先例）。
// 非 GROUP BY 位置 rollup(x)/cube(x) 仍经 unreservedWord_ → sqlIdentifier_func_ 解析为
// SqlRegularFunction，报错 = 函数表永不注册 → ERR_EQL_UNKNOWN_FUNCTION（放置排他来自
// "GROUP BY 之外无注册"）。GROUPING SETS 永久否决（roadmap ledger），无第三备选。
sqlGroupByItem
    : ROLLUP LP_ functionArgs_? RP_ # SqlGroupByItem_rollup
    | CUBE LP_ functionArgs_? RP_ # SqlGroupByItem_cube
    | expr=sqlExpr # SqlGroupByItem_expr
    ;

sqlTypeExpr
    : name=dataTypeName_ ( LP_ precision=NUMBER_ (COMMA_ scale=NUMBER_)? RP_)? characterSet=characterSet_? collate=collateClause_?
    ;

// WI9a BUG-11（ai-dev/plans/25-eql-wi9a-cast-type-aliases.md）：删除 14 个"解析可过、
// SqlTypeExpr_name 必拒"的死文法备选——CHARACTER / CHARACTER VARYING / NATIONAL CHARACTER /
// NATIONAL CHARACTER VARYING / NCHAR / NATIONAL CHAR / NATIONAL CHAR VARYING / BIT / BIT VARYING /
// DEC / DOUBLE PRECISION / INTERVAL / TIME WITH TIME ZONE / TIMESTAMP WITH TIME ZONE——文法接受面
// 不再承诺注定被 AST 拒绝的拼写。收窄边界=候选 A（保留 sqlIdentifier_ 单词兜底，未知拼写仍走
// nop.err.eql.invalid-sql-type 语义拒绝，WI2 nosuchtype 断言不破）；错误形态迁移：上述 14 拼写从
// "解析过 + nop.err.eql.invalid-sql-type"变为"解析失败（nop.err.antlr.*）"，同为响亮失败。
// EC ③ 对照表：ai-dev/design/eql-cross-db-compat/2026-10-04-wi9a-phase0-adjudications.md §五。
// 不删除任何 lexer token（无重编号）；不触碰 characterSet_/collateClause_（WI2 BUG-15 守卫）；
// TIME/TIMESTAMP WITH LOCAL TIME ZONE 拼写归 WI9b（两个名字集，互不替）。
dataTypeName_
    : CHAR | VARCHAR | NUMERIC | DECIMAL | INTEGER | SMALLINT
    | FLOAT | REAL | DATE | TIME | TIMESTAMP
    // WI2 BUG-8: BOOLEAN 是独立关键字 token（不在 unreservedWord_，sqlIdentifier_ 备选
    // 救不了它）。语义链已就绪：SqlTypeExpr_name 的 toUpperCase + StdSqlType.fromStdName
    // 校验放行 StdSqlType.BOOLEAN，生成侧经 dialect.stdToNativeSqlType 产方言映射
    | BOOLEAN
    // WI9b（ai-dev/plans/32-eql-wi9b-tz-types.md Phase 2）：LOCAL 时区两多词备选（标准
    // SQL:1999 拼写，全历史首次新增——与 WI9a 删除的非 LOCAL 备选是两个名字集，互不替）。
    // bare 形态：sqlTypeExpr 的 (precision[,scale]) 紧跟 dataTypeName_ 整体，多词备选不接
    // precision（已知能力边界，plan Deferred 登记）。闸门 A 的多 token 名称归一化在手写
    // EqlASTBuildVisitor.SqlTypeExpr_name（getText() 无分隔符拼接必须重建，裁定文档 §5.3）。
    // AST SqlTypeExpr 复用既有 name 字段（存归一化枚举名），零新节点零新字段。
    | TIME WITH LOCAL TIME ZONE
    | TIMESTAMP WITH LOCAL TIME ZONE
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