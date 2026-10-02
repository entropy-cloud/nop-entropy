# 25 WI17 SQL 编译器实现

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI17 行）、`ai-dev/design/nop-stream/sql-compiler-contract.md`（D7/D8/D13）、`ai-dev/design/nop-stream/sql-subset-and-semantics.md`（§4a/4b/4d）、`ai-dev/design/nop-stream/sql-landing-decision.md`（D14）
> Related: `ai-dev/plans/nop-stream-sql/15-wi8c-parameterized-aggregate.md`、`16-wi8d-parameterized-join.md`、`11-wi8b-schemas-consumer.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）
> Revision: r2——对抗性审查（5 Blocker + 6 Major）后裁定级重写。五项 Blocker 裁定（受托 owner 级，沿 sql-compiler-contract.md :5 委托链先例）：
> - **B1 裁定（组键与多聚合）**：编译器合成**复合聚合器**——合成 aggregator 条目（fnId=sql 侧复合 spec）经既有 IAggregatorFunctionResolver SPI 由 sql 模块解析为组合 AggregateFunction：内部持 group-key 求值器（复用 WI9 evaluator，对记录重求键）+ N 个子聚合累加器，getResult 产出 SQL 行（组键在前、聚合按 SELECT 序）——**不动 WI8c 一 transform 一 aggregatorRef 的面**，无 roadmap 回改。
> - **B2 裁定（占位 source）**：FROM 表名即 bean 名（显式契约：应用以表名注册 SourceFunction bean；不发明命名约定、不生成空数据 source）。测试以表名注册 stub bean。
> - **B3 裁定（聚合 dispatch）**：TUMBLE 在场 → keyBy+window+aggregate（复合聚合器）；无 TUMBLE 有 GROUP BY → 持续聚合管线 = map(记录→累加器行) + keyBy + reduce(累加器合并)（count/avg 在累加器内表达，运行行输出 = last-value-wins per D1）；无 GROUP BY 的全局聚合 → v1 fail-fast（§4b 纳入面未含，入 fail-fast 矩阵）。
> - **B4 裁定（`<sql>` 机制，依 D8 §2.2 口径）**：`<sql>` 是**模型级生成器**而非 transform——顶层元素，编译发生在 builder.build() 的 buildTransforms 之前：SPI 返回完整流模型 XML（除 sink 外），builder 解析回读并替换父模型的 transforms/edges/registries；`<sql>` 必须是模型唯一内容（与其他 transforms/edges 并存 → fail-fast）；元素带 sinkBean 属性（编译器据其追加 sink 声明与边，产物自洽可执行）。
> - **B5 裁定（测试分层）**：flow = fake ISqlStreamCompiler（BeanContainerBuilder 注入）展开 + 无 provider 钉码；sql 模块 = 真实编译器→解析→build 接线 + app-beans 反空壳钉（WI8c 三层先例照搬）。

## Purpose

实现 StreamSqlCompiler：EQL AST → 可被既有 xdef 校验与 StreamModelDslBuilder 消费的流模型 XML，覆盖 §4b 纳入面；补 TUMBLE 伪表函数 grammar 落地项；落 D8 `<sql>` 声明面与编译 SPI；EQL 错误码映射到 stream 错误码（§4d 零新增码）。

## Current Baseline（2026-10-03 实测锚点，含审查复核）

- **TUMBLE grammar 未落地**：DMLStatement.g4 表源恰三分支（:145-149）、TUMBLE 注释残迹（:216-221）；`TUMBLE` token 全仓 g4 不存在——需 SQL92Keyword.g4 新增 token + unreservedWord 登记；INTERVAL grammar 已有（sqlIntervalExpr BaseRule.g4:345-346 + intervalUnit_ :349-351）。WI1 硬约束：「每条新 grammar 规则必须有同名 AST 类、标签与字段一一对应」——新规则对应新 AST 类（非增字段）。AstToEqlGenerator/AstToSqlGenerator 不认识新表源节点 → toSQL 对 TUMBLE 查询静默丢表源（WI1 Baseline 同类不对称在案）——**round-trip 不对称显式记录：grammar 测试禁用 toSQL 当 oracle，SQL 通道补齐归 WI20**。
- **WI9 求值器在案**（nop-stream-sql/eval）：StreamSqlExprCompiler（标量子集）/StreamSqlAggregations（五 id）/resolveType——编译器合成 spec 复用其原语。
- **SPI 三层先例在案**：IAggregatorFunctionResolver（flow/spi）+ BeanContainer.tryGetBeanByType（AdvancedTransforms:408-424）+ app-aggregator.beans.xml + 无 provider 钉码（TestAdvancedTransforms:178）+ 真实装配钉在 sql 模块（TestStreamAggregatorFunctionResolver:69-75）；flow 测试类路径无 sql 模块（TestAdvancedTransforms:181 在案）。
- **声明面落 base stream.xdef**（D13 回改裁定）+ flow typed model 重生成（`./mvnw install -pl nop-kernel/nop-xdefs -DskipTests` 刷 jar → flow `_gen` 重生成；非下划线 wrapper 为手写保留文件——WI8c plan :24 先例）。
- **nop-orm-eql 回归基线**：执行前先实测记录（WI2 时点 94 绿，当前约 95——以执行日实测为准）；回归波含 nop-orm（WI1 先例 208 绿）。
- **_module 目录歧义**：nop-stream-sql 资源有 `_vfs/nop/stream-sql/_module` 与 `_vfs/nop/stream/sql/_module` 两个标记（WI8c 遗留）——实现时以实际生效者为准并在日志记录裁定。
- **错误码 §4d 零新增码**；ORDER BY/LIMIT 钉死单一码 `nop.err.eql.dialect-not-support-feature`（4a#1 主选，既有消费点 EqlTransformVisitor）——M6 收口。

## Goals（含 r2 裁定的编译形态规格）

- **TUMBLE grammar 落地**：SQL92Keyword.g4 新增 TUMBLE token + unreservedWord 登记；DMLStatement.g4 表源分支新增伪表函数规则（新 AST 类一一对应）；EqlASTParser 解析；eql-and-database-compatibility.md 同步。
- **StreamSqlCompiler 产物规格**：
  - **行形状**：SQL 行 = 组键在前、聚合按 SELECT 序的有序结构（v1 载体：`List<Object>` 或定界字符串，实现时定并钉入测试）；
  - **投影/WHERE**：编译器由 AST 发射 XLang xpl 内联（map/filter body）；**与 WI9 evaluator 的语义等价钉子测试**（同一记录矩阵双引擎对拍：null 传播/除法 double/三值逻辑），分歧显式记录；
  - **聚合 dispatch**（B3）：TUMBLE → keyBy(groupKeys)+window(duration)+aggregate(aggregatorRef=合成复合条目)；无 TUMBLE 有 GROUP BY → map(累加器行)+keyBy+reduce(合并)；全局聚合 → fail-fast；
  - **join 映射**（M3）：joinType→流形态映射 INNER/LEFT/RIGHT/FULL → hash 形态（无 windowStrategyRef；FULL 窗口限制不适用——hash FULL 为 EquiJoinOperator 合法形态）；ON 条件分解为合取等值对 → leftKeyExprs/rightKeyExprs（SQL 列 → `event.col`，行载体为 Map——**v1 契约：行 = Map**）；join 输出 JoinMatch<L,R>，两表列投影经 join 后 map 引用 `event.left.*`/`event.right.*`；
  - **事件时间**（M1）：TUMBLE 查询产物含 `<timestampsAndWatermarks>` + inline timestampAssigner（t 列 bigint epoch-millis，D7 §1.4）；
  - **fail-fast 矩阵**（M4）：§4a 九项 + **default-reject**（HAVING/CTE-WITH/SELECT DISTINCT/INTERSECT/EXCEPT/括号选择/LATERAL/全局聚合等一切未处理构造 → `nop.err.stream.invalid-arg`，AST 走查无静默分支）+ UNION 语义裁定（v1：UNION = bag union 不去重，SQL DISTINCT UNION → fail-fast，文档记录）；
  - **占位 source**（B2）：FROM 表名即 bean 名。
- **`<sql>` 声明面与 SPI**（B4/B5）：base stream.xdef 顶层 `<sql>`（sql 文本 + 内嵌 schema + sinkBean 属性）；builder.buildTransforms 前预处理：有 provider → SPI 返回 XML → DslModelParser 回读 → 替换父模型内容（`<sql>` 唯一内容裁定：与父 transforms/edges 并存 fail-fast）；无 provider → NOT_IMPLEMENTED 形态 fail-fast。

## Non-Goals

- TestStreamSqlEntryE2E 与用户文档（WI18）；Delta 验证（WI19）；RDBMS 目标 SQL 产出与 toSQL 的 TUMBLE 渲染（WI20）；HOP/SESSION；OVER 的 DSL/编译面（SELECT 含 OVER → default-reject fail-fast，指引后续）；Java API/CLI/GraphQL 入口（D8 拒绝）。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql`：TUMBLE token/规则/AST/parser（生成管线，plan-first 证据=本 plan+回归）；eql-and-database-compatibility.md 同步
- `nop-kernel/nop-xdefs`：stream.xdef 顶层 `<sql>` 元素
- `nop-stream/nop-stream-flow`：typed model 重生成 + builder `<sql>` 预处理消费 + ISqlStreamCompiler SPI
- `nop-stream/nop-stream-sql`：StreamSqlCompiler + 复合聚合器 SPI 扩展 + ISqlStreamCompiler 实现 + app-beans
- 测试：eql（TUMBLE parse + 基线回归 + nop-orm 波）、flow（fake 展开 + 无 provider）、sql（编译器全矩阵 + 语义等价钉子 + 接线反空壳）
- 文档：eql-and-database-compatibility.md、sql-subset-and-semantics.md §4b 锚点更新、parameterized-declarations.md 如需

### Out Of Scope

- WI18/19/20/23 交付物；HOP/SESSION；OVER 编译面。

## Execution Plan

### Phase 1 - TUMBLE 语法落地（protected：生成管线，plan-first 证据=本 plan+回归）

Status: completed
Targets: `nop-persistence/nop-orm-eql`

- Item Types: `Feature`

- [x] 实测记录 nop-orm-eql 与 nop-orm 回归基线数（改前）（2026-10-03 实测：nop-orm-eql 116 绿 0F/0E/0S；nop-orm 208 tests 0F/0E 6 skip=docker opt-in——日志 `_tmp/wi17-baseline-eql.log` / `_tmp/wi17-baseline-orm.log`，计数落 ai-dev/logs/2026/10-03.md）
- [x] SQL92Keyword.g4 新增 TUMBLE token + unreservedWord 登记；DMLStatement.g4 表源分支新增伪表函数规则（同名新 AST 类——WI1 一一对应硬约束）；`./mvnw generate-sources -pl nop-persistence/nop-orm-eql` 重生成 + 生成物 diff 白名单程序化核对（WI1 plan :100-101 先例）（2026-10-03 完成：规则 `sqlTumbleTableSource` ↔ AST 类 `SqlTumbleTableSource` 一一对应；表源语法 `TUMBLE(表名.时间列, INTERVAL n 单位)`；白名单 26 文件全部命中——脚本 `_tmp/wi17-whitelist-check.sh` 输出 WHITELIST OK）
- [x] EqlASTParser 解析断言（AST 类与字段）+ INTERVAL→duration 提取（非正时长 fail-fast）（EqlParseHelper.intervalDurationMillis：正整数检查 + 固定单位换算 + 日历单位 MONTH/QUARTER/YEAR fail-fast + 新错误码 nop.err.eql.invalid-interval-value；ORM 通道对 TUMBLE 表源 resolve fail-fast nop.err.eql.table-source-not-resolved——EqlTransformVisitor.addAliasToScope 显式分支防静默跳过）
- [x] 测试：TUMBLE parse 断言（不用 toSQL 当 oracle——round-trip 不对称在案）+ nop-orm-eql 与 nop-orm 回归零退化（TestEqlTumbleGrammarParse 14 用例；红→绿实证：stash 实现后测试构建失败（编译期符号缺失形态）、恢复后 14/14 绿；回归 eql 116→130（+14 新增，0F/0E）、orm 208 不变 0F/0E 6 skip 既有）
- [x] docs-for-ai/02-core-guides/eql-and-database-compatibility.md 同步（guide 规则 17；roadmap Cross-Cuting 8 义务）（新增「TUMBLE 时间切片伪表函数」小节：语法/stream-only 范围限定/标识符兼容/round-trip 不对称警示）

Exit Criteria:

- [x] TUMBLE parse 测试绿；基线数与改后数落日志（零退化证明）
- [x] 生成物白名单零越界
- [x] docs-for-ai 文档已更新
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - StreamSqlCompiler（编译器主体）

Status: completed
Targets: `nop-stream/nop-stream-sql`

- Item Types: `Feature`

- [x] 复合聚合器：合成 aggregator 条目（fnId=sql 复合 spec，expr 携带组键+子聚合结构化 spec）经 IAggregatorFunctionResolver 解析为组合 AggregateFunction（键重求值 + N 子累加器 + 行重构 getResult）——WI8c 面零改动（SqlRowAggregateSpec（JSON spec，fnId=sql-row-agg）+ SqlRowCompositeFunction（键重求值+N 子累加器+getResult 产 List 行：组键在前聚合按 SELECT 序）+ SqlRowAggregateOps（持续聚合 xpl 静态分派）+ resolver 的 sql-row-agg 分支拦截——WI8c 既有五 id 分发零改动）
- [x] StreamSqlCompiler.compile（B2/B3 裁定形态）：EqlASTParser 入口 → AST 走查 → 模型 XML（FROM 表名=bean 名；行=Map；TUMBLE 时 timestampsAndWatermarks+assigner+per-event watermark generator；dispatch：TUMBLE→keyBy+window+aggregate(复合条目)/无 TUMBLE 有 GROUP BY→map(begin)+keyBy(累加器键头)+reduce(merge)+map(toRow)/全局聚合 fail-fast；UNION=bag union 仅 UNION_ALL）
- [x] fail-fast 矩阵（M4）：§4a 九项射程内钉码 + default-reject（HAVING/CTE/DISTINCT 选择/INTERSECT/EXCEPT/括号选择/LATERAL/SELECT */全局聚合/GROUP BY 无聚合/DISTINCT 聚合/WHERE 含聚合/非 GROUP BY 列/TUMBLE+join/聚合+join/多语句/非受管 schema 类型/空 SQL/空 sinkBean→`nop.err.stream.invalid-arg`；ORDER BY/LIMIT→`nop.err.eql.dialect-not-support-feature`；INTERVAL 非正→eql invalid-interval-value 穿透）——AST 走查无静默分支（ORM 通道 addAliasToScope 显式 TUMBLE fail-fast）
- [x] `TestStreamSqlCompiler`（sql 模块）：纳入面产物结构断言（TUMBLE 窗口聚合/持续聚合/plain/join/union 五形态）+ fail-fast 逐项钉码 20 项 + 自校验闭合（产物经 DslModelParser 回读 + builder build + env.execute 成功——测试注入表名 bean stub，四个具名 E2E 类：TUMBLE 窗口聚合 [a=4,b=2,b=4,c=1]、持续 GROUP BY 逐元素 last-value-wins [a=1,b=2,a=1,a=4,b=6,a=-1,c=1]、自 join [1=x,2=y]、UNION ALL bag 七元素）
- [x] 语义等价钉子（M2）：同一记录矩阵（5 记录×14 表达式）经产物 xpl（filter body 执行）与 WI9 evaluator 对拍全等（null 传播/除法 double/三值逻辑/IS NULL/BETWEEN/IN/NOT IN/一元负号）——XLang 原生算子 null 语义分歧（比较→false/算术→NaN/NOT→true）经发射显式三值守卫消除，等价成立非分歧记录

Exit Criteria:

- [x] TestStreamSqlCompiler 全绿（含逐项钉码与等价钉子）（30/30 绿 + 四 E2E 类绿；sql 模块全量 68 绿（含 WI9/8c 既有 24+10））
- [x] 编译产物端到端 build 成功（自校验闭合）（四 E2E：parse→build→execute→sink 断言）
- [x] `ai-dev/logs/` 条目更新

### Phase 3 - `<sql>` 声明面与 SPI（B4/B5 裁定形态）

Status: completed
Targets: `nop-kernel/nop-xdefs`、`nop-stream/nop-stream-flow`、`nop-stream/nop-stream-sql`

- Item Types: `Feature`

- [x] stream.xdef 顶层 `<sql>` 元素（sql 文本 + 内嵌 schema + sinkBean；base xdef 纪律同 WI8c/d：xdefs install → flow `_gen` 重生成 → 手写 wrapper 保留）（`<sql sinkBean="!string">` + `<schemas><field/></schemas>` + `<source>` CDATA；`./mvnw install -pl nop-kernel/nop-xdefs -DskipTests` → flow generate-sources 重生成：_StreamSqlModel/_StreamSqlFieldModel + wrapper 模板创建；_StreamModel 增 sql 属性——diff 白名单 5 文件零越界）
- [x] ISqlStreamCompiler SPI（flow 定义，返回完整模型 XML 除 sink 外）+ sql 模块实现（app-beans 注册——_module 目录歧义按实测生效者裁定并记录：沿 WI8c 双标记布局不变，新增 beans 落 `_vfs/nop/stream/sql/beans/app-sql-compiler.beans.xml`）；builder.buildTransforms 前预处理：`<sql>` 唯一内容裁定（transforms/edges/aggregators/joins/schemas/windowingStrategies 并存 fail-fast）+ SPI 回读替换（build() 首步 expandSqlModel：tryGetBeanByType 无 provider fail-fast 点名 nop-stream-sql + DslModelParser 回读 + 父模型内容全量替换 + setSql(null)）
- [x] 测试三层（B5）：flow fake provider 展开断言 + 无 provider 钉码（aggregateRefWithoutProvider 同款）；sql 模块真实接线（`<sql>` 模型 → build 成功）+ app-beans 反空壳钉（flow TestSqlModelExpansion 3 用例：fake 展开（产物 transforms/edges 替换父模型+getSql 消费）/并存 fail-fast/空容器无 provider 点名 nop-stream-sql；sql TestSqlModelDeclarationE2E：xdef 校验的 `<sql>` 声明模型 → builder 展开 → execute → sink 断言 [a=4,b=2,b=4,c=1] + tryGetBeanByType 反空壳钉）。红→绿实证：stash SPI/预处理后 flow 测试构建失败（ISqlStreamCompiler 符号缺失）
- [x] docs 同步：sql-subset-and-semantics.md §4b TUMBLE 锚点更新为已落地 + `<sql>` 面登记；编译产物终态形状（含 sinkBean 派生 sink、投影末态 map）入 §4b 或 compiler-contract（compiler-contract §2.2 补 WI17 落地形态与产物形状段；eql-and-database-compatibility.md Phase 1 已更新）

Exit Criteria:

- [x] 三层测试绿（fake 展开 / 无 provider 钉码 / 真实接线反空壳）
- [x] flow/core 全量零退化（xdef 变更波及）（提交前全量复测：flow/core/sql/eql/xdefs 见日志终条）
- [x] docs 更新落档
- [x] `ai-dev/logs/` 条目更新

### Phase 4 - 收口

Status: planned
Targets: plan 与 roadmap

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session）：纳入面/不支持面/裁定形态逐项核验 + Anti-Hollow（编译产物真实可执行）+ 全量实跑；证据落 ai-dev/audits/nop-stream-sql/wi17-closure-audit.md
- [ ] audit 通过后 roadmap WI17 `todo` → `done`（括注单层一对）；解析器断言：items=31、milestones=7、WI17=done、退出码 0
- [ ] Plan Status → `completed`；check-plan-checklist --strict 0；check-doc-links --strict 0
- [ ] r2 五项 Blocker 裁定的 roadmap/设计文档回写复核（B1 复合聚合器如触 §4b 表述则同步）

Exit Criteria:

- [ ] 独立 audit 证据落档两处
- [ ] roadmap WI17 = done + 解析器断言成立
- [ ] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Closure Gates

- [ ] TUMBLE grammar 落地（token/规则/AST 类一一对应 + parse 断言 + 白名单零越界 + eql/nop-orm 回归零退化）
- [ ] StreamSqlCompiler 覆盖 §4b 纳入面（含复合聚合器/事件时间/join 映射/UNION 语义）+ fail-fast 矩阵（§4a 九项 + default-reject + 钉死错误码）
- [ ] 语义等价钉子测试（产物 xpl vs WI9 evaluator）
- [ ] 编译产物端到端可 build（自校验闭合）
- [ ] `<sql>` 声明面 + 三层 SPI 测试（fake 展开/无 provider/真实接线反空壳）
- [ ] eql/flow/core/sql/nop-xdefs 波及面全量零退化
- [ ] design 文档同步（subset §4b 锚点、compiler-contract 产物形状、eql-and-database-compatibility）
- [ ] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/25-wi17-sql-compiler.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### OVER 分析窗口的编译面

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 既有口径（OVER DSL 面归 WI17/WI21 后续裁定）在 WI21 已记录未承接；SELECT 含 OVER 的 default-reject fail-fast 保证无静默错译；§4b 纳入面不含 OVER
- Successor Required: `yes`
- Successor Path: 独立裁定项（OVER DSL 元素 + 编译映射），随 WI24 收口时登记 Follow-up Backlog

### HOP/SESSION 伪表函数

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: D4 仅 TUMBLE 定形态；§4b 纳入面仅 TUMBLE；grammar/编译可平行扩展
- Successor Required: `yes`
- Successor Path: Follow-up Backlog 登记

## Closure

Status Note: <<完成时填写>>
Completed:

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<验证结果>>

Follow-up:

- <<no remaining plan-owned work 或列出>>
