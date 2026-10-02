# 14 WI9 聚合与标量表达式求值

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI9 行、§3.1 聚合行、Assumptions A4）、`ai-dev/design/nop-stream/sql-compiler-contract.md` §1.3（D7 映射表）
> Related: `ai-dev/plans/nop-stream-sql/11-wi8b-schemas-consumer.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）
>
> 审查修订记录：经独立子 agent 对抗性审查一轮修订（含 parse 实测）——M1 钉死 StreamSqlAggregation 直接实现 AggregateFunction 四方法（add 收流记录内部求值，WI8c 零适配接线）；M2 补标量语义基线（null 传播/数值提升/double 除/Comparable 字符串比较）；m1 grammar 行号更正（218-220/272-274）；m2 逻辑运算专用节点注明；m3 number literal 为 String 文本；m4 resolveType 列 key 对齐末段；m5 owner-doc 显式裁定（No owner-doc update required，用户文档归 WI18）；m6 目录条目暴露 arity；m7 null-AST 与 InQueryExpr/ParameterMarker fail-fast 点名。

## Purpose

为流记录提供聚合与标量表达式求值设施：sum/count/avg/min/max 五个内置聚合 id 拥有累加实现与 fnId→求值实现绑定（roadmap A4），EQL 投影与聚合表达式可编译为可序列化的流侧求值器（不再落到 `ERR_EQL_UNSUPPORTED_EVAL_EXPR`），列类型经 D7 映射表落 BasicTypeInfo。解锁 WI8c（聚合声明面只引用 id 与参数形状）、WI11（持续 GROUP BY）、WI17（SQL 编译器投影/聚合翻译）。

## Current Baseline

- **XLang 无聚合函数**（roadmap §3.1 实测：GlobalFunctions.java 按名检索命中 0，无静态注册表）——聚合求值只能新建。
- `SqlExprToFilterBeanTransformer`（nop-orm-eql eval 包）仅覆盖谓词种类（And/Or/Not/Binary/Like/Unary/IsNull/In/Between），投影与聚合表达式抛 `ERR_EQL_UNSUPPORTED_EVAL_EXPR`（`getValue` 仅接受 SqlLiteral；transformBinary 的非列名侧抛同码）。
- `SqlExprToExpressionTransformer`（同包）把 SqlExpr 转 XLang Expression AST，但列名固定展开为 `o.prop` 成员表达式、聚合函数节点直接抛 `ERR_EQL_UNSUPPORTED_EVAL_EXPR`（dispatch 无 SqlAggregateFunction 分支）；XLang Expression 的独立求值需走 nop-xlang exec 编译链，脱离 XDSL 场景复用成本高。
- **聚合 AST**：`SqlAggregateFunction extends SqlFunction`，字段 args（List<SqlExpr>）/distinct/name/selectAll（`_gen/_SqlAggregateFunction.java` 实测）。SUM/COUNT/AVG/MIN/MAX 由 grammar 关键字闭集产生（`BaseRule.g4:218-220` 的 sqlAggregateFunction、`:272-274` 的 sqlIdentifier_agg_）；**name 一律归一为小写**；`COUNT(*)` → args 空表 + selectAll=true；`COUNT(DISTINCT x)` → distinct=true + args 单元素。逻辑运算落专用节点：AND→SqlAndExpr、OR→SqlOrExpr、NOT→SqlNotExpr（非 SqlBinaryExpr）；一元负号→SqlUnaryExpr(op=MINUS)。`SqlNumberLiteral.getValue()` 返回 String 原始文本（无数值字段），类型判定需自行 parse。
- **表达式解析入口**：`EqlExprASTParser.parseFromText(SourceLocation, String)` 返回 SqlExpr（public，可直接用于单测）。
- **既有累加契约**：`io.nop.stream.core.common.functions.AggregateFunction<IN,ACC,OUT>`（createAccumulator/add/getResult/merge，Serializable，@Internal）——WindowedStream.aggregate 的算子面契约；WI8c 的 aggregators 注册表最终要经它接 WindowOperator。
- **keyExpr 取值通路**：`EvalActionKeySelector`（flow builder 包）——`IEvalAction` + child scope 绑 `event`；roadmap 说「表达式取值求值复用既有 keyExpr 的 !expr 加 TreeBean 通路」指的是**取值表达式**（按键取值）这条路，聚合累加实现是 WI9 新建（A4：五内置 id 非 bean，累加由 WI9 求值器提供，WI8c 只声明 id 与参数形状）。
- **D7 类型映射已落地**：`StreamSchemaRegistry.resolveManagedType`（WI8b，nop-stream-flow）九名受管类型→BasicTypeInfo 九实例；FieldSpec（name/type/nullable/defaultValue）为列类型来源。
- **模块宿主**：nop-stream-sql（D13 分支 a）pom 已具备（依赖 nop-stream-flow + nop-orm-eql + junit test）；当前仅 `_vfs` 骨架。EQL AST 类与 StreamException/NopStreamErrors 均在其依赖闭包内。
- 错误码惯例（WI8b 先例）：复用既有 `ERR_STREAM_INVALID_ARG`（`nop.err.stream.invalid-arg`）+ ARG_DETAIL 定位，不新增 core 错误码。

## Goals

- **新求值器接口**（nop-stream-sql，public，Serializable）：`StreamRecordEvaluator`（`Object eval(Object record)`）——标量表达式对单条流记录求值；列访问支持 `Map`（get(name)）与 bean property（getter 反射，Nop `ReflectionHelper` 既有能力）两种记录形态，列不存在 fail-fast。
- **标量子集编译器**：`StreamSqlExprCompiler.compileScalar(SqlExpr)` → StreamRecordEvaluator。v1 子集（按 EqlASTKind 分派）：列引用 SqlColumnName（含 `t.col` 限定名，列 key 取末段 name）、字面量 SqlNumberLiteral/SqlStringLiteral/SqlBooleanLiteral/SqlNullLiteral（number 文本自行 parse：无 `.`/e → long，否则 double）、算术二元 SqlBinaryExpr（ADD/MINUS/MULTIPLY/DIVIDE/MOD）、比较二元（EQ/NE/LT/LE/GT/GE）、逻辑 SqlAndExpr/SqlOrExpr/SqlNotExpr、SqlIsNullExpr（含 not）、SqlBetweenExpr（含 not）、SqlInValuesExpr（含 not）。**标量语义基线（显式裁定）**：算术/比较操作数含 null 结果即 null（null 参与返回 null，不抛）；数值运算经 ConvertHelper.toDouble/toLong 提升（int+long→long，任一浮点→double）；`/` 恒为 double 除（D7 无 DECIMAL，整数除进不支持语义）；字符串比较用 Comparable 自然序；`!=` 解析为 NE。子集外（SqlRegularFunction、SqlCaseExpr、SqlCastExpr、SqlInQueryExpr、SqlParameterMarker、SqlAggregateFunction 出现在标量位、空 AST null 等）fail-fast `ERR_STREAM_INVALID_ARG`（ARG_DETAIL 含 AST 节点类型与 location），**不抛 EQL 侧错误码**。
- **五聚合求值器**：`StreamSqlAggregation` **直接实现 `io.nop.stream.core.common.functions.AggregateFunction<Object,ACC,Object>`**（IN=流记录：add 内经 arg 求值器取值；含 createAccumulator/add/getResult/merge 四方法，Serializable；ACC/OUT 由各 fnId 语义定）——WI8c 经 WindowedStream.aggregate 接线零适配。语义：nulls skipped；count 空集=0、sum/avg/min/max 空集=null；sum 整型提升 long；avg 结果 double；count 形态二：无参/selectAll（`COUNT(*)`）计记录数。distinct=true v1 fail-fast（进不支持清单，WI16 定稿）。
- **fnId 目录**：`StreamSqlAggregations`——静态五 id（sum/count/avg/min/max，与 AST 小写 name 直接对齐）；`resolve(String fnId)` 未知名返回 null（供 WI8c 先查 beanResolver 再落此目录，A4 全序）；目录条目暴露 `arity()`（count 0..1、其余恰 1，供 WI8c 参数个数 fail-fast）、`create(StreamRecordEvaluator argEvaluator)` 工厂、`resultType(BasicTypeInfo argType)` 结果类型推断。
- **表达式静态类型**：`StreamSqlExprCompiler.resolveType(SqlExpr, Function<String,BasicTypeInfo> columnTypes)` → BasicTypeInfo——列 key 与 compileScalar 同一规则（取末段 name），经 D7 映射函数（调用方从 StreamSchemaRegistry FieldSpec 桥接）；字面量按 parse 后值类，count→LONG、avg→DOUBLE、sum 按参数提升（整型→LONG、浮点→DOUBLE）、min/max 透传参数类型；无法推断返回 UnknownTypeInformation。本 plan 用户文档不更新（归 WI18），owner doc 无需变更。
- **测试**：五聚合各有单测（含 null 语义与空集），标量子集逐类用例，子集外 fail-fast，类型推断断言；E2E 形态证据——EQL 文本经 EqlExprASTParser → compileScalar → 对 Map 记录求值全链。

## Non-Goals

- 不建 xdef 声明面、不动 stream.xdef（WI8c 才有声明面）；不实现 BeanContainer bean 解析（WI8c 的全序里有 beanResolver 前置）；不做 DISTINCT；不做正则函数/CASE/CAST 求值（WI17 按需扩展本编译器）；不接 WindowOperator（WI8c/WI11 消费）；不改 nop-orm-eql 任何文件；不新增 core 错误码。

## Scope

### In Scope

- `nop-stream/nop-stream-sql`：StreamRecordEvaluator、StreamSqlExprCompiler、StreamSqlAggregation、StreamSqlAggregations 及测试
- roadmap WI9 行同步（Phase 收口时）；`ai-dev/design/nop-stream/sql-compiler-contract.md` §1 补一行 WI9 求值器落点注记（若 audit 认为必要）；当日日志

### Out Of Scope

- WI8c 聚合声明面；WindowOperator 接线；nop-orm-eql 变更；DISTINCT 与扩展函数子集。

## Execution Plan

### Phase 1 - 红：聚合求值器测试先行

Status: completed
Targets: nop-stream-sql 测试

- Item Types: `Feature`

- [x] 新增 `TestStreamSqlAggregations`（11 用例）：五 id resolve 命中 + 未知名返回 null；sum（int 提升 long、null 跳过、空集 null、浮点保持 double）、count（null 跳过 vs COUNT(*) 记录数、空集 0）、avg（double、空集 null）、min/max（含字符串自然序）、merge 组合、distinct=true fail-fast、arity、resultType 规则、Serializable 往返
- [x] 新增 `TestStreamSqlExprCompiler`（13 用例）：EQL 文本（EqlExprSupport.parseExpr）→ compileScalar → Map 记录求值——列访问、限定名末段、四则与提升、除法恒 double、比较、三值逻辑短路、IS NULL、BETWEEN、IN、字面量；Map 无此列 fail-fast；bean 形态求值；子集外五类 fail-fast（正则函数/CASE/CAST/标量位聚合/null AST）；resolveType D7 列映射断言；求值器 Serializable 往返
- [x] 确认红（类不存在编译失败即红）

Exit Criteria:

- [x] 测试类编译前红（缺实现）
- [x] 聚合语义断言覆盖 roadmap 完成判定的五个函数各至少一例

### Phase 2 - 实现

Status: completed
Targets: nop-stream-sql 主代码

- Item Types: `Feature`

- [x] `StreamRecordEvaluator` 接口 + `RecordColumnAccess` 列访问器（Map 优先、bean getter 反射缓存回退、缺失 fail-fast）
- [x] `StreamSqlExprCompiler.compileScalar`：v1 子集递归编译（getASTKind 分派），子集外 `ERR_STREAM_INVALID_ARG`（AST 节点类型进 ARG_DETAIL）
- [x] `StreamSqlAggregation`（直接实现 AggregateFunction 四方法 + withDistinct fail-fast + arity + resultType）+ `StreamSqlAggregations` 五 id 目录（resolve 未知名返回 null）
- [x] `resolveType(SqlExpr, Function<String,BasicTypeInfo>)`：列经 D7 映射函数、字面量按值、五聚合按规则（含 SqlAggregateFunction 分支）
- [x] Phase 1 全部转绿（24/24）；`./mvnw test -pl nop-stream/nop-stream-sql -am` 全量门见 Phase 3 复验
- [x] ai-dev/logs/ 当日条目已更新

Exit Criteria:

- [x] sum/count/avg/min/max 在流记录（Map 形态）上求值正确且各有具名单测（roadmap 完成判定第一句）
- [x] EQL 投影与聚合表达式（子集内）经编译器不再抛 `ERR_EQL_UNSUPPORTED_EVAL_EXPR`（13 用例全走 EqlExprASTParser→compileScalar 转换链，全程零 EQL 错误码）
- [x] 子集外构造 fail-fast 且码串钉住（无静默返回，规则 #24）
- [x] resolveType 列类型经 D7 映射函数落到 BasicTypeInfo（STRING/INT→LONG 提升/count→LONG/avg→DOUBLE/max 透传断言）
- [x] `./mvnw test -pl nop-stream/nop-stream-sql` 绿（24/24）；-am 全量门在 Phase 3 收口复核
- [x] ai-dev/logs/ 当日条目已更新

### Phase 3 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：判定 PASS（5 项 Minor 不阻塞；M-1 SumAgg 双轨混型登记 Non-Blocking Follow-ups，M-minor DISTINCT 码串已补钉）；证据落 ai-dev/audits/nop-stream-sql/wi9-closure-audit.md
- [x] audit 通过后 roadmap WI9 `todo` → `done`（括注单层无嵌套）；`parseRoadmapMarkdown` 复核 31 工作项 + 7 里程碑、14 done、无静默丢弃
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-sql --severity high` 退出码 0
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI9 = done + 解析器 31 + 7 复核通过
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0；scan-hollow 高危零发现

## Closure Gates

- [x] 五内置聚合 id 的累加实现与 fnId 绑定关系落地（StreamSqlAggregations，resolve 未知名返回 null 与 A4 全序一致：bean 前置、目录兜底）
- [x] EQL 子集内投影与聚合表达式编译为可序列化求值器（Serializable 往返断言两处），不抛 ERR_EQL_UNSUPPORTED_EVAL_EXPR（audit 零功能引用核验）
- [x] 列类型经 D7 映射落 BasicTypeInfo（resolveType 测试断言 STRING/INT→LONG/count→LONG/avg→DOUBLE/max 透传）
- [x] 子集外构造显式 fail-fast（码串钉住两处），无静默跳过
- [x] WI8c 消费契约清晰（create 返回 core AggregateFunction 四方法 + allowsNoArg/withDistinct/resultType 元数据——audit 核验 WindowedStream.aggregate 零适配兼容）
- [x] `./mvnw test -pl nop-stream/nop-stream-sql -am` 绿（core/cep/flow/sql 全 SUCCESS）
- [x] scan-hollow 高危零发现（全级别零发现）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id，判定 PASS）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/14-wi9-aggregate-eval.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### DISTINCT 聚合

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: v1 SQL 子集确认（WI16）尚未定稿 DISTINCT；聚合描述符形状（WI8c）已预留 distinct 标志位，fail-fast 保证不静默近似
- Successor Required: `no`
- Successor Path: WI16 定稿后若纳入，扩展 StreamSqlAggregation 的累加器为 set-based

### 正则函数 / CASE / CAST 标量子集

- Classification: `optimization candidate`
- Why Not Blocking Closure: WI17 编译器按查询需要才消费；本 plan 交付的编译器结构（AST 分派 + fail-fast）为扩展留了单一入口
- Successor Required: `no`
- Successor Path: WI17 plan 按需扩展 StreamSqlExprCompiler 分派分支

## Non-Blocking Follow-ups

- SqlExprToExpressionTransformer 的 XLang Expression 独立求值链路（exec 编译包）未复用——本 plan 裁定为直接编译（依赖轻、可序列化）；若日后需要 XLang 全函数面再评估。
- SumAgg 双轨累加器（audit M-1）：同列混型输入（int/double 混杂或浮点轨抵消为 0）返回静默偏差结果——D7 单列单类型模型之外，WI16/WI17 落类型系统时改单轨或混型 fail-fast（`nop.err.stream.invalid-arg`）。

## Closure

Status Note: 聚合与标量表达式求值落地——五个内置聚合 id 拥有直接实现 core AggregateFunction 的累加器（WI8c 零适配），EQL 子集内投影与聚合表达式经单一编译器转为可序列化求值器且全程不抛 EQL 侧错误码，列类型经 D7 映射函数落 BasicTypeInfo，子集外全部 fail-fast。独立 closure audit 判定 PASS（5 项 Minor：M-1 SumAgg 双轨混型登记 follow-up，其余为钉码/命名/文档细节，DISTINCT 码串已补钉复验）。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi9-closure-audit.md（判定 PASS）
- Evidence:
  - 实跑 `./mvnw test -pl nop-stream/nop-stream-sql` 24/24 绿；判别性抽查全数成立（sum Long 提升/空集 null/count(*) vs count(expr)/avg Double/字符串自然序/DISTINCT fail-fast/invalid-arg 钉码）
  - Anti-Hollow：StreamSqlAggregation 真实 implements core AggregateFunction 四方法 + Serializable 往返测试；WindowedStream.aggregate 签名零适配兼容
  - ERR_EQL_UNSUPPORTED_EVAL_EXPR 零功能引用（仅 2 处 javadoc 行文）；13 编译器用例全走 parseExpr→compileScalar 转换链
  - 工具门禁：doc-links --strict 0、scan-hollow 全级别 0、invariants sync 0
  - git 纪律：仅 nop-stream-sql 模块 + ai-dev 文档，零 _gen diff
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/14-wi9-aggregate-eval.md --strict` 退出码 0

Follow-up:

- SumAgg 双轨混型（audit M-1）→ WI16/WI17 类型系统落点（见 Non-Blocking Follow-ups）
- no remaining plan-owned work
