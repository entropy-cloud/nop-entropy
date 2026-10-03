# 10 WI8a D14 编译落点落档

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI8a 行、D14 行、§3.5、Cross-Cuting 4）
> Related: `ai-dev/design/nop-stream/sql-compiler-contract.md`（D7/D8/D13 边界）、`ai-dev/design/nop-stream/sql-vision-conflict-resolution.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

把 owner 已裁定的 D14=(a) 参数化算子面落档到 ai-dev/design/nop-stream/sql-landing-decision.md：记录 (a) 相对 (b) 预置 bean 家族与 (c) 绕过 XDSL 直产 StreamGraph 保留了什么（xdef 校验、Delta 面、builder fail-fast 保护）与承担的迁移影响；确认 WI8b/WI8c/WI8d 的范围边界；D7 的 schema 与类型映射结论不被本裁定改写。未落档前 WI17 不可实施。

## Current Baseline

- D14 在 roadmap 中**已裁定走 (a)**（owner 2026-09-30，roadmap D14 行），但落档义务归本计划；WI8b/c/d 的 roadmap 行与 Cross-Cuting 4 已按 (a) 预写确定触碰项。
- 选项内容（roadmap D14 行）：(a) 给 StreamModel 增加参数化算子面；(b) 预置 bean 家族模型只引用 bean 名；(c) 绕过 XDSL 直产 StreamGraph。D14 与 D13 正交：D13 定模块（已裁 (a) 新模块 nop-stream-sql），D14 定产物形态。
- 边界事实：D7 的 schema 来源（SQL 面自带 schema）与九类型映射、解析入口 EqlASTParser 已由 `sql-compiler-contract.md` §1 落档；D8 `<sql>` xdef 元素已落 §2——本裁定不得改写。
- WI8b/c/d 的范围边界（roadmap §3.5 与 Cross-Cuting 4 预写）：WI8b 让 `<schemas>` 声明面有消费者（field type 收敛受管类型名→BasicTypeInfo，coder 取内建 SimpleTypeSerializer，coders 注册表仍 fail-fast 留 FU-3）；WI8c 新增 aggregators 注册表与 aggregate 的 aggregatorRef（bean 属性保留转可选，恰好其一 fail-fast）；WI8d 新增 joins 注册表与 join 的 joinRef（仅声明与构造期校验，运行时求值归 WI13）。
- 平台无注解扫描（AGENTS.md）：A4——五个内置聚合 id 非 bean，是 aggregators 注册表纯参数描述符；真正 bean 是 join 算子基类与 `<process>`/`<custom>` 路径算子类（Delta 或 beans.xml 显式定义）。

## Goals

- 新建 ai-dev/design/nop-stream/sql-landing-decision.md：D14 裁定记录（三选项对比、结论 (a)、负责人=仓库 owner 2026-09-30 裁定/2026-10-02 落档、保留面与迁移影响）+ WI8b/c/d 范围边界确认 + 与 D7/D8/D13 的边界声明。
- **保留面分化（审查问题 2）**：(b) 不整体丢失 builder fail-fast——bean 存在性/类型检查保留，丢失的是模型级参数契约、模型级 Delta 再参数化与组合爆炸消除三项；落档文档对代码行为声称附 file:line。
- roadmap WI8a 状态行 → done。

## Non-Goals

- 不实施 WI8b/c/d 的任何代码；不改 D7/D8/D13/D14 的既有结论方向；不改 stream.xdef。

## Scope

### In Scope

- 新建 `ai-dev/design/nop-stream/sql-landing-decision.md`
- roadmap：WI8a 状态行、前置裁定表 D14 行同步（状态列去「待 WI8a 落档」、结论列补落档位置——先例 plan 03 同款交付）
- 当日日志；plan 收口

### Out Of Scope

- WI8b/c/d 实施；D9-D12 落档（WI0d 已完成）；其它设计文档。

## Execution Plan

### Phase 1 - 落档文档

Status: completed
Targets: ai-dev/design/nop-stream/sql-landing-decision.md（未来交付物）

- Item Types: `Decision`

- [x] 新建文档：D14 三选项对比与结论 (a)；保留面分化表述（审查问题 2 修正）——(a) 保住 xdef 校验、Delta 面、builder fail-fast 与**模型级参数契约**；(b) 保留 bean 存在性/类型检查（AdvancedTransforms.java:284 resolveBean、StreamModelDslBuilder.java:687-697 构建期抛错），丢失的是模型级参数契约、模型级 Delta 再参数化与消除预写 bean 组合爆炸三项，**不丢失 builder fail-fast**（对代码行为声称附 file:line）；(c) 整体绕过 xdef 校验/builder/Delta（vision §三 #1/#2、§八 2 与 10）；迁移影响（**按 D13 落新模块 schema、经 delta 扩展 stream.xdef，机制归 WI8b plan 裁定并附回改条款**；既有 bean 路径与新 aggregatorRef/joinRef 互斥校验构造期 fail-fast，迁移说明归 WI24，WI8c 将取代现行 bean 缺失时的报错）；WI8b/c/d 范围边界三条确认；与 D7（schema/类型映射）、D8（接口面）、D13（宿主模块）的边界声明——D7/D8/D13 结论不被本裁定改写；负责人与双日期（证据指针：`ai-dev/logs/2026/09-30.md` owner 裁定理由全文）

Exit Criteria:

- [x] 文档四节齐备（裁定记录/保留面/迁移影响/范围边界与正交声明）
- [x] 与 roadmap D14 行、§3.5、Cross-Cuting 4 表述一致（无方向性偏离）
- [x] No new test required: 纯文档变更
- [x] ai-dev/logs/ 当日条目已更新

### Phase 2 - 收口

Status: planned
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：核验文档与 roadmap 既有裁定一致性、边界不被改写；证据落 ai-dev/audits/nop-stream-sql/wi8a-closure-audit.md 与 plan Closure 段
- [x] audit 通过后 roadmap WI8a `todo` → `done`（括注单层非嵌套、内容无任何圆括号字符）；解析器核对 31 + 7（于仓库根执行）：`node -e "import('./tools/mission-driver/src/roadmap-check.mjs').then(m=>{const r=m.parseRoadmapMarkdown(require('fs').readFileSync('ai-dev/backlog/nop-stream-sql-roadmap.md','utf8'));console.log('items',r.phases.filter(i=>!i.isMilestone).length,'milestones',r.phases.filter(i=>i.isMilestone).length)})"`
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI8a = done，解析器 31 + 7
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Closure Gates

- [x] Anti-Hollow Check（文档版）：裁定声称与 live 代码一致（resolveBean file:line 抽查）
- [x] 落档文档四节齐备且与 roadmap 无方向性偏离（audit 核对；迁移影响落点为 D13 新模块 schema 分支，非直改 nop-xdefs stream.xdef）
- [x] D7/D8/D13 结论未被改写（audit 对照 sql-compiler-contract.md 核对）
- [x] Anti-Hollow Check（文档版）：(b)/(c) 丢失面表述与 live 代码一致（resolveBean 构建期行为 file:line 抽查）；D14 行已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [x] ai-dev/logs/ 当日条目已更新
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/10-wi8a-landing-decision.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: D14=(a) 落档 sql-landing-decision.md 四节齐备（裁定/保留面分化/迁移影响按 D13 新模块分支/边界与正交），D14 行同步、WI8a 翻 done、解析器 31+7 复核。纯文档计划。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi8a-closure-audit.md
- Evidence:
  - 落档文档四节齐备；(b) 丢失面分化经 audit 三处源码证实（resolveBean 存在性/类型检查在 GlobalBeanFunctionResolver，ERR_STREAM_BEAN_NOT_FOUND/TYPE_MISMATCH）
  - audit 两项 Major（Phase 1 Status 滞留、D14 行同步无 checklist 承载）已在翻转批次内完成
  - check-doc-links --strict 退出码 0；解析器翻转后 items 31 milestones 7
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/10-wi8a-landing-decision.md --strict` 退出码 0

Follow-up:

- 无 plan-owned 剩余工作；delta 扩展机制归 WI8b plan（回改条款有效）
