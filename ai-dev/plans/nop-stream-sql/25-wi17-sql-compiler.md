# 25 WI17 SQL 编译器实现

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI17 行）、`ai-dev/design/nop-stream/sql-compiler-contract.md`（D7/D8/D13）、`ai-dev/design/nop-stream/sql-subset-and-semantics.md`（§4a 不支持清单 / §4b 纳入面 / §4d 错误码表）、`ai-dev/design/nop-stream/sql-landing-decision.md`（D14）
> Related: `ai-dev/plans/nop-stream-sql/16-wi8d-parameterized-join.md`、`ai-dev/plans/nop-stream-sql/15-wi8c-parameterized-aggregate.md`、`ai-dev/plans/nop-stream-sql/11-wi8b-schemas-consumer.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

实现 StreamSqlCompiler：EQL AST → 可被既有 xdef 校验与 StreamModelDslBuilder 消费的流模型（XML/模型对象），覆盖 §4b 纳入面（投影/WHERE/聚合/GROUP BY/TUMBLE/join/union/schema）；补 TUMBLE 伪表函数 grammar 落地项（WI16 §4b 在案的前置缺口）；落 D8 的 `<sql>` xdef 声明面与编译 SPI。EQL 错误码映射到 stream 错误码（§4d 零新增码）。

## Current Baseline（2026-10-03 实测锚点）

- **TUMBLE grammar 未落地**（WI16 §4b 在案锚点）：DMLStatement.g4 表源仅三分支、TUMBLE 仅注释残迹；D4 已裁语法面=表源分支伪表函数 + EqlAST.xjava AST 字段；WI1 先例（窗口 grammar 四能力）提供 ANTLR 重生成 + AST codegen + unreservedWord 登记全套纪律。
- **WI9 求值器在案**：nop-stream-sql 的 StreamSqlExprCompiler（标量子集→StreamRecordEvaluator）/StreamSqlAggregations（五 id）/resolveType（D7 映射）——编译器的表达式语义与其对齐（同一子集、同一 fail-fast）。
- **WI8b/c/d 声明面在案**：schemas 三字段受管类型闭集（StreamSchemaRegistry）；aggregators 注册表 + aggregatorRef（恰一校验 + IAggregatorFunctionResolver SPI）；joins/joinRef 八项校验 + buildJoin 真实装配（WI13）。
- **D13 回改裁定在案**：声明面落 base stream.xdef（delta 不可行实证），消费面经 SPI 由 nop-stream-sql 承载（app-beans 自动注册）——`<sql>` 元素照此先例。
- **错误码 §4d 零新增码**：编译期错误统一 `nop.err.stream.invalid-arg`（子集外/未知 fnId/DISTINCT/schema 未知类型等）；ORDER BY/LIMIT 按 4a#1 报 `nop.err.eql.dialect-not-support-feature` 或对应 stream 码。
- 模块依赖：nop-stream-sql 已依赖 nop-stream-flow + nop-orm-eql（D13 (a) 落地，WI8b 骨架）+ nop-stream-core。

## Goals

- **TUMBLE grammar 落地**：DMLStatement.g4 表源分支新增 TUMBLE(t, INTERVAL) 伪表函数形态；EqlASTParser 解析为 AST 字段（EqlAST.xjava 增量 + codegen 重生成；unreservedWord 登记）；W1 分析窗口能力不回归。
- **StreamSqlCompiler**（nop-stream-sql）：`compile(sqlText)` → StreamSqlCompileResult（含规范化 stream 模型 XML 字符串 + 结构摘要），EqlASTParser 为入口（D7 §1.2）；SELECT 投影/WHERE → map/filter 内联 xpl；GROUP BY+聚合 → keyBy+window+aggregate(aggregatorRef)（窗口聚合）或 keyBy+reduce（持续聚合，last-value-wins 语义标注）；TUMBLE → windowingStrategies(duration)+window 声明；双流 join → joins 注册表 + join 元素（恰两源）；多源 union；schema → schemas 注册表（九受管类型）；ORDER BY/LIMIT/子查询/DISTINCT/集合外类型 → §4a fail-fast。
- **`<sql>` xdef 声明面 + 编译 SPI**：base stream.xdef 顶层 `<sql>` 元素（sql 文本内嵌 + schema 内嵌声明，属性面最小闭合——按 WI8c/d 先例 base xdef + flow typed model + builder fail-fast 默认 + ISqlStreamCompiler SPI 由 nop-stream-sql 实现自动注册；builder 遇 `<sql>` 有 provider 则编译展开为等价 transforms、无 provider 则既有 NOT_IMPLEMENTED 形态 fail-fast）。
- **测试**（roadmap 明文类名）：`TestStreamSqlCompiler`（nop-stream-sql：各纳入面编译产物结构断言 + 不支持清单逐项 fail-fast + 错误码钉值）+ `TestStreamSqlEntryE2E`（WI18 交付，本 WI 不做）。

## Non-Goals

- TestStreamSqlEntryE2E 与用户文档（WI18）；Delta 验证（WI19）；RDBMS 目标 SQL 产出（WI20）；HOP/SESSION（D4 语法面仅 TUMBLE 定形态，HOP/SESSION 归后续按需——纳入面总表仅 TUMBLE）；Java API/CLI/GraphQL 入口（D8 拒绝）；分析窗口 OVER 的 DSL 面（roadmap 既有口径：归 WI17/WI21 后续裁定——本 WI 仅在 SELECT 含 OVER 时 fail-fast 指引后续）。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql`：DMLStatement.g4 表源分支 + AST + parser + codegen 重生成（生成物白名单纪律同 WI1）
- `nop-kernel/nop-xdefs`：stream.xdef 顶层 `<sql>` 元素声明
- `nop-stream/nop-stream-flow`：typed model 重生成 + builder 消费（SPI 查找 + 编译展开 / 无 provider fail-fast）
- `nop-stream/nop-stream-sql`：StreamSqlCompiler + ISqlStreamCompiler SPI 实现 + app-beans 注册
- 测试三类：eql（TUMBLE grammar/AST/parser）、flow（`<sql>` SPI 展开 + 无 provider fail-fast）、sql（编译器全矩阵）

### Out Of Scope

- WI18/19/20/23 交付物；HOP/SESSION；OVER DSL 面。

## Execution Plan

### Phase 1 - TUMBLE 语法落地（protected：生成管线，plan-first 证据=本 plan + 回归）

Status: planned
Targets: `nop-persistence/nop-orm-eql`

- Item Types: `Feature`

- [ ] DMLStatement.g4 表源分支新增 TUMBLE(t, INTERVAL)（伪表函数形态，D4 裁定）；ANTLR 重生成（不手改 EqlParser.java）；EqlAST.xjava 增字段经 codegen；EqlASTParser 解析；unreservedWord 登记
- [ ] TUMBLE 编译期语义：间隔字面量→毫秒 duration（时长仅 tumbling；非正时长 fail-fast）；t 列绑定按 D7 §1.4（bigint epoch-millis 承载，DATE/TIMESTAMP 列绑定保持编译期报错）
- [ ] 测试：eql 模块 TUMBLE parse 断言（AST 字段）+ 既有窗口能力回归（nop-orm-eql -am 全量）

Exit Criteria:

- [ ] TUMBLE parse 测试绿 + 既有 eql 回归零退化
- [ ] 生成物白名单零越界（WI1 纪律）
- [ ] `ai-dev/logs/` 条目更新

### Phase 2 - StreamSqlCompiler（编译器主体）

Status: planned
Targets: `nop-stream/nop-stream-sql`

- Item Types: `Feature`

- [ ] StreamSqlCompiler.compile：EqlASTParser 入口 → AST 走查 → 模型 XML 产出（sources 由 schema 推定的占位 source 声明 + transforms + edges + registries）；XML 经 DslModelParser 回读验证（自校验闭合）
- [ ] 纳入面逐项：投影/WHERE（xpl 内联，语义经 WI9 编译器同款子集）；GROUP BY+聚合（keyBy+window+aggregate aggregatorRef，窗口声明 duration 参数化）；持续 GROUP BY（keyBy+reduce，last-value-wins javadoc/产物标注）；TUMBLE（windowingStrategies+window）；join（joins 注册表+join 元素，恰两源）；union 多源；schema（schemas 注册表，九受管类型）
- [ ] 不支持清单逐项 fail-fast（§4a 九项对应的编译期分支；ORDER BY/LIMIT 按 4a#1 码）
- [ ] `TestStreamSqlCompiler`（nop-stream-sql）：纳入面产物结构断言（XML 含预期 transforms/registries）+ §4a 逐项钉码 + 自校验闭合（编译产物经 DslModelParser+builder build 成功）

Exit Criteria:

- [ ] TestStreamSqlCompiler 全绿（含逐项 fail-fast 钉码）
- [ ] 编译产物端到端可 build（xdef 校验 + builder 零错误）
- [ ] `ai-dev/logs/` 条目更新

### Phase 3 - `<sql>` xdef 声明面与 SPI

Status: planned
Targets: `nop-kernel/nop-xdefs`、`nop-stream/nop-stream-flow`、`nop-stream/nop-stream-sql`

- Item Types: `Feature`

- [ ] stream.xdef 顶层 `<sql>` 元素（sql 文本 + 内嵌 schema 声明；声明面纪律同 WI8c/d：base xdef + flow typed model 重生成）
- [ ] ISqlStreamCompiler SPI（flow 定义）+ nop-stream-sql 实现（app-beans 自动注册）；builder 遇 `<sql>`：有 provider → 编译展开为等价 transforms 后继续构建；无 provider → 既有 NOT_IMPLEMENTED 形态 fail-fast（flow 类路径无 sql 模块时合法）
- [ ] 测试：flow 模块 `<sql>` SPI 展开 E2E（小查询 → 编译 → 构建成功）+ 无 provider fail-fast 钉码（aggregateRefWithoutProvider 同款）

Exit Criteria:

- [ ] `<sql>` 展开与无 provider 两测试绿
- [ ] flow/core 全量零退化（stream.xdef 变更波及面）
- [ ] `ai-dev/logs/` 条目更新

### Phase 4 - 收口

Status: planned
Targets: plan 与 roadmap

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session）：纳入面/不支持面逐项核验 + Anti-Hollow（编译产物真实可执行）+ 全量实跑；证据落 ai-dev/audits/nop-stream-sql/wi17-closure-audit.md
- [ ] audit 通过后 roadmap WI17 `todo` → `done`（括注单层一对）；解析器断言：items=31、milestones=7、WI17=done、退出码 0
- [ ] Plan Status → `completed`；check-plan-checklist --strict 0；check-doc-links --strict 0

Exit Criteria:

- [ ] 独立 audit 证据落档两处
- [ ] roadmap WI17 = done + 解析器断言成立
- [ ] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Closure Gates

- [ ] TUMBLE grammar 落地（parse 断言 + 生成物纪律 + eql 回归零退化）
- [ ] StreamSqlCompiler 覆盖 §4b 纳入面 + §4a 逐项 fail-fast（含错误码钉值）
- [ ] 编译产物端到端可 build（自校验闭合）
- [ ] `<sql>` xdef 声明面 + SPI 展开 + 无 provider fail-fast
- [ ] eql/flow/core/sql 四模块全量零退化
- [ ] design 文档同步（sql-compiler-contract §0 index 如有新面；sql-subset-and-semantics §4b TUMBLE 锚点更新为已落地）
- [ ] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/25-wi17-sql-compiler.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（无——HOP/SESSION 与 OVER DSL 面为 Non-Goals 声明的范围外，非 deferred。）

## Closure

Status Note: <<完成时填写>>
Completed:

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<验证结果>>

Follow-up:

- <<no remaining plan-owned work 或列出>>
