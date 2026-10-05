# 2317 nop-stream R5：文档契约批次（DC-1..14 + RD-6 + REG-6 文档面）

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: ai-dev/audits/2026-10/2026-10-05-0523-deep-audit-nop-stream-quality-r5/09-doc-contract-consistency.md（DC-1..14 最终保留项）、07-readability-structure.md（RD-6）、00-fix-of-fix-regression.md（REG-6 javadoc 面）；summary.md 修复归属 2317
> Related: 2312（同轮代码计划）
> Review: 2026-10-05 独立子代理对抗性审查：1 Blocker（DC-2 含产品代码文案）+ 5 Major 全部吸收——DC-2 文案改写划入代码轨并补 flow focused test；验证规则按 guide L278 字面修正（不再误引纯文档豁免）；DC-10 纳入 :131 sideOutput 行；补 checklist 工具门/No-new-test 注记/00 报告标注归属；DC-1/DC-4 修复方向按审计建议校正。

## Purpose

把 R5 审计确认的 16 项文档/注释漂移收敛到 live baseline：文档承诺的 API 面与代码一致、模块计数正确、设计文档不与现实矛盾、javadoc 不宣称已被推翻的契约。

## Current Baseline

各条目的 文档行号 ↔ 代码现实 证据已在审计报告复核锁定（见 Source 各报告「最终保留项」与发现正文）。修复方向汇总：

| # | 目标 | 修复方向 |
|---|------|---------|
| DC-1 (P1) | user-guide:73 能力边界 | union 全支持（Java API + XDSL）；sideOutput **提取 API**（DataStream.getSideOutput/SideOutputDataStream）已支持；但 XDSL `<sideOutput>` 变换与图级 side-output 下游 transform 仍 build 期 fail-fast（ERR_STREAM_UNSUPPORTED，测试钉定）——能力边界按此三分口径改写，附测试锚点 |
| DC-2 (P2，含代码文案项) | flow AdvancedTransforms javadoc :89-93 与 :787-789 段注释；**:793-798 ERR_STREAM_NOT_IMPLEMENTED 文案字面量（产品代码）**；fraud-s1-cdc.stream.xml:12 头注释 | javadoc/段注释删除过时表述；异常 ARG_DETAIL 文案改为「sideOutput XDSL 变换未支持（提取 API 已可用）」口径（测试只钉错误码+"sideOutput" 子串，TestAdvancedTransforms:523，不破）；改后必须跑 flow focused tests |
| DC-3 (P2) | connectors.md SPI 表 | 按审计建议：移动「Source API 冻结政策」节恢复单张 8 行表；:35「上表 8 个」指称同步核对 |
| DC-4 (P2) | connectors.md:96 + `TwoPhaseCommitSinkFunction` javadoc :118/:125 | 两处 `UnsupportedOperationException` 表述 → 实际 `StreamException(ERR_STREAM_UNSUPPORTED)`（文档从 javadoc 转抄，需同改） |
| DC-5 (P2) | nop-stream/README.md:7 | 独立进程入口改为与 owner doc/user-guide 一致的 test-scope 诚实化表述 |
| DC-6 (P2) | README:24 + INDEX.md:249 | 子模块计数 10→11，补 nop-stream-sql（与 module-groups.md:23 对齐） |
| DC-7 (P3) | owner doc nop-stream.md:9-17 模块路由表 | 补 nop-stream-sql 行 |
| DC-8 (P2) | user-guide transform 清单 | 补 `join` 与 `union`（xdef :202/:227 已声明） |
| DC-9 (P2) | distributed-runbook §7 :158-159 | 删除 manifest checksum/version、队列水位 gauge 两条「未落地」表述（均已落地，与本文件 §6.1 自洽） |
| DC-10 (P2) | stream-dsl-design :130 `<union>` 行 + **:131 `<sideOutput>` 行**（同表相邻同样过时，审查 M2） | 按目录惯例**补落地更新注记**（core-design 先例），不删除历史表述；两行一并更新为已实现/提取 API 支持口径 |
| DC-11 (P3) | cdc-cookbook :5 附近 | TestDebeziumEngineProperties 锚点模块归属改为 nop-message-debezium |
| DC-12 (P3) | user-guide :89-114 XDSL 示例 | 移除代码块内嵌的两段 markdown 正文（:97/:99），示例成为合法 XML |
| DC-13 (P3) | user-guide triggers 计数 | 10→11 文件（ContinuousIntervalTrigger 抽象基类） |
| DC-14 (P3) | user-guide :71 | 「ServiceLoader 风格发现」改为实际的硬编码 Class.forName 反射注册描述（plan 原稿 :206-209 系笔误，审计正文 :71 为准） |
| RD-6 (P2) | runtime 两处 javadoc（RemotePipelineResolver/SubtaskPlanBuilder）+ pom 注释 | 改写动机段（非纯删除）：换成「声明格式由 flow 拥有，runtime 经依赖消费」的真实理由，保留间接层存在理由叙述；pom 注释交叉引用一并更新 |
| REG-6 (P3) | StateShard:113-116 + KeyGroupAssignment 类 javadoc:26-28 | 删除 legacy 哈希平价承诺（stableHash 已统一 murmur3，plan 2306 裁定） |

## Goals

- 上表 16 项全部收敛；文档不再承诺不存在的 API、不再否定已落地能力、不再保留与代码矛盾的禁令/计数。
- `check-doc-links --strict` 保持 0 error。

## Non-Goals

- 不改产品**行为**（RD-6/REG-6 只动 javadoc/注释文本；DC-2 只改异常 ARG_DETAIL 文案字面量，不改错误码与控制流——错误契约由既有测试钉定并复跑验证）。
- 不处理审计 09 报告中已驳回/未立案的措辞类小项。
- 不重写文档结构（只做事实性收敛）。

## Scope

### In Scope

- `docs-for-ai/03-modules/nop-stream-user-guide.md`、`nop-stream-connectors.md`、`nop-stream-cdc-cookbook.md`、`nop-stream.md`、`nop-stream-migration-guide.md`（如 DC-9 涉及）
- `docs-for-ai/INDEX.md`
- `nop-stream/README.md`
- `ai-dev/design/nop-stream/stream-dsl-design.md`、`distributed-runbook.md`
- `nop-stream-flow`/`nop-stream-runtime`/`nop-stream-core` 相应 javadoc/注释（RD-6、REG-6、DC-2）
- `nop-stream/nop-stream-fraud-example/.../fraud-s1-cdc.stream.xml` 头注释（DC-2 连带）

### Out Of Scope

- 其余 P3 措辞项；文档重构；代码行为变更。

## Execution Plan

### Phase 1 - 文档事实性收敛（16 项）

Status: completed（16 项修复与验证全部完成；待 ai-dev/logs 条目（主代理统一维护）与独立 closure audit 后转 completed）
Targets: 上表全部文件

- Item Types: `Fix`

- [x] 按 Current Baseline 表逐项修复（执行者以审计报告 09/07/00 的对应发现正文为准，含行号与代码证据）
- [x] 每项修复后 grep 复核不再残留过时表述（「does not yet expose」「共 10 个」「UnsupportedOperationException」在对应语境；DC-10 另核中文「尚未」表述于 :131）——grep 复核全部 CLEAN；DC-10 :130-131 历史行按修复方向保留「尚未」并附表后落地更新注记（:139）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 error / 3 个既有 warning，位于未触达的 ai-dev/plans/nop-bytecode/06-resource-leak-v1.md）
- [x] DC-2 文案项：`./mvnw test -pl nop-stream/nop-stream-flow` 通过（163 tests，0 failures；TestAdvancedTransforms 错误契约保持）

Phase 1 逐项完成记录（文件:行号 → 修改摘要，行号为改后行号）：

- [x] DC-1: `docs-for-ai/03-modules/nop-stream-user-guide.md:73` → 能力边界段按三分口径改写（union 全支持 Java API+XDSL / 侧输出提取 API 已支持 / XDSL `<sideOutput>` 与 side-output 视图下游 transform 仍 fail-fast ERR_STREAM_UNSUPPORTED），附测试锚点
- [x] DC-2: `AdvancedTransforms.java:87-95`（类 javadoc）+ `:789-814`（sideOutput 段注释 + buildSideOutput 异常 ARG_DETAIL 文案，保留 ERR_STREAM_NOT_IMPLEMENTED 错误码与 "sideOutput" 子串）+ `fraud-s1-cdc.stream.xml:12-13`（头注释删除「`<union>` 为 build 期 fail-fast」过时口径）
- [x] DC-3: `nop-stream-connectors.md:20-29` → 「Source API 冻结政策」节移至 SPI 表之后（:58），恢复单张 8 行完整表；:31「上表 8 个」指称核对无误
- [x] DC-4: `nop-stream-connectors.md:98` + `TwoPhaseCommitSinkFunction.java:116-127` → `UnsupportedOperationException` 表述改为实际 `StreamException(ERR_STREAM_UNSUPPORTED)`
- [x] DC-5: `nop-stream/README.md:9` → 补 F-11 test-scope 诚实化注（与 owner doc「运维手册」首注、user-guide「入口类现状」同口径）
- [x] DC-6: `nop-stream/README.md:24-25` + `docs-for-ai/INDEX.md:249` → 模块计数 10→11，两处清单补 `nop-stream-sql` 行
- [x] DC-7: `docs-for-ai/03-modules/nop-stream.md:17` → 模块路由表补 `nop-stream-sql` 行（链接 nop-stream-sql.md）
- [x] DC-8: `nop-stream-user-guide.md:117` → transform 清单补 `join`（joinRef + `<joins>` 注册表）与 `union`
- [x] DC-9: `ai-dev/design/nop-stream/distributed-runbook.md` §7 → 删除 manifest checksum/version（item 25）与背压 gauge（item 32）两条已落地边界；其余条目保留
- [x] DC-10: `ai-dev/design/nop-stream/stream-dsl-design.md:139` → 按目录惯例在表后补落地更新注记（:130-131 历史行保留），union 已实现/侧输出提取 API 已交付口径 + 测试锚点
- [x] DC-11: `nop-stream-cdc-cookbook.md:5` → 首注标注引擎面锚点 `TestDebeziumEngineProperties` 位于 `nop-message/nop-message-debezium`
- [x] DC-12: `nop-stream-user-guide.md:90-110` → XDSL 示例围栏内两段 markdown 正文移出至示例后（:113-115），示例补 `duration` 用法 XML 注释，成为合法 XML
- [x] DC-13: `nop-stream-user-guide.md:214` → triggers 目录计数改「11 文件，含包私有抽象基类 `ContinuousIntervalTrigger`」
- [x] DC-14: `nop-stream-user-guide.md:71` → 「ServiceLoader 风格发现」改为固定 FQCN `Class.forName` 反射描述，并区分上文 checkpoint 工厂的真 ServiceLoader
- [x] RD-6: `RemotePipelineResolver.java:12-29` + `SubtaskPlanBuilder.java:99-103` javadoc 动机段改写（声明格式由 flow/scenario 拥有、runtime 部署路径与声明语法解耦；注明与 flow compile 依赖并存的原因）+ `nop-stream-runtime/pom.xml:22-30` 注释交叉引用
- [x] REG-6: `StateShard.java:111-121` + `KeyGroupAssignment.java:20-33` 类 javadoc → 删除 legacy `hashCode()` 平价承诺，改为 plan 2306 统一 murmur3 口径（含 2.0.0-SNAPSHOT 布局不兼容裁定与 AR-01 parity 保留说明）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 16 项逐项完成并在日志登记（文件:行号 → 修改摘要）——16 项逐项摘要已登记于上方 Phase 1 完成记录；ai-dev/logs 条目由主代理统一维护（本计划执行者不勾该 logs 项）
- [x] No new test required：文档/javadoc/注释/异常文案修正无新功能面（DC-2 文案由既有 TestAdvancedTransforms 契约测试守护，不新增）
- [x] 审计报告标注已修复：在 09/07/**00** 三个报告对应条目复核状态处补注修复指针（不改历史内容）；本项为执行项，由本计划执行者完成，主代理复核
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `./mvnw test -pl nop-stream-flow`（DC-2 文案门）通过；其余触达 src/main javadoc/README/XML 的改动以 compile 级复核（`./mvnw compile -pl nop-stream-flow,nop-stream-runtime,nop-stream-core`）——test: BUILD SUCCESS（163 tests）；compile: exit 0
- [x] `ai-dev/logs/` 对应日期条目已更新（主代理统一维护，本执行者不勾）
- [x] 无产品行为变更（git diff 仅文档/注释/javadoc/异常文案字面量—— AdvancedTransforms 仅异常 ARG_DETAIL 字面量改动，错误码/控制流未动）

## Closure Gates

> 纯文档计划：按 plan guide 规则免除 `./mvnw test`/compile 构建验证项。

- [x] 16 项文档漂移全部收敛
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope owner-doc drift
- [x] `check-doc-links --strict` 0 error
- [x] `./mvnw test -pl nop-stream-flow` 通过（DC-2 文案门）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2317-nop-stream-r5-doc-contract-batch.md --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 审计建议附带项随下次文档轮次：DC-6 README 补 sql 文档链接、DC-8 sideOutput 例外注明、DC-11 connectors.md:87 锚点标注、RD-6 pom 注释交叉引用复核、DC-1 审计指出的次要瑕疵（风险段竞品表表述）。
- 审计 09 报告 P3 措辞类未立案小项。
- （closure audit 观察）`ai-dev/design/nop-stream/01-architecture-baseline.md:32` 残留「共 10 个」模块计数——plan 范围外，随下次文档轮次收敛。

## Closure

Status Note: 16 项文档漂移（DC-1..14 + RD-6 + REG-6）全部收敛；check-doc-links --strict 0 error；flow 163 tests 绿（DC-2 错误契约守护）；compile 三模块过；审计报告 09/07/00 已补修复指针。独立 closure audit APPROVE。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure auditor（general-purpose 子代理，2026-10-05）
- Evidence: 16 项逐项 文件:行号→摘要 由执行代理交付并经审计抽查；check-doc-links --strict exit 0；`./mvnw test -pl nop-stream-flow` BUILD SUCCESS（163/0，TestAdvancedTransforms 错误契约）；compile flow/runtime/core exit 0；git diff 仅文档/注释/javadoc/异常文案字面量；checklist --strict exit 0

Follow-up:

- 措辞类小项（见上）
