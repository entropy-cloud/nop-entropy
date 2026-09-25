# 02 WI2 M0 spike——SymbolSolver 覆盖率实测 + P1 rename 符号域裁定

> Plan Status: active
> Last Reviewed: 2026-09-25
> Source: ai-dev/backlog/nop-refactor-roadmap.md（M0 WI2）；ai-dev/design/nop-refactor/01-architecture-baseline.md §七；ai-dev/analysis/2026-09/2026-09-25e-jdt-java-refactoring-catalog-and-nop-path.md（M0 spike 定义）
> Related: 01-dependency-gate-verification.md（WI1）；后续 09 操作框架 plan（本 plan 裁定的转录义务方）
> Review: R1 对抗审查（2026-09-25，fresh session）：REVISE——2 Major（Closure Gates 缺 vision 原则 1–9 回扣 gate；roadmap WI2 勾选时点弱于 roadmap"勾选 = 独立 closure audit 通过"门且无 FAIL 分支）+ 7 Minor；全部修订后执行（R2 复核见底部 Review 记录）

## Purpose

执行 roadmap WI2（Item Type: Decision）：在 nop-entropy 自身（Maven 多模块）实测 JavaParser SymbolSolver 的跨模块类型解析率，据实测数字裁定 P1 rename 符号域（v1 单模块内 / classpath 可达域）与引用搜索落点（操作器内嵌轻量索引 vs nop-code 查询面），并履行对 WI9 plan Current Baseline 的转录义务。

## Current Baseline

- **spike 已执行（本 plan 起草前的侦察产出，证据在案）**：`_tmp/wi2-spike/`（WI2SymbolSolverSpike.java + cp.txt + results.txt + run.log），25 模块 84 文件 / 5292 类型引用 / 4886 方法调用。关键数字：GLOBAL（全源码根）72.3%、SINGLE（单模块）61.9%、**跨模块解析 18/5292（0.3%）**、第三方依赖重模块无 Maven classpath 装配器时解析面崩塌（nop-lint-core 9.0%、nop-lint-java 14.4%）。全文见 `ai-dev/analysis/2026-09/2026-09-25-wi2-symbol-solver-coverage-spike.md`。
- roadmap WI2 原文要求交付：spike 代码/脚本落 `_tmp/`（已满足）；裁定记录与覆盖率数字落当日 log + analysis 文档（analysis 已落、log 待写——log 为多会话共享文件，写入安排在收口时读-合并-写）；WI9 plan 起草时转录进其 Current Baseline（义务已在 analysis §五 显式登记）。
- baseline §七 预留的裁定口："语义级操作（rename 等）的逐操作可行性、classpath 装配与引用搜索来源（内嵌索引 vs nop-code 查询面）在逐操作 design 中裁定"——本 plan 即该裁定。
- nop-code 查询面实况：nop-code-service 提供 CodeSearch/CodeQuery/CodeIndex/CodeGraph 服务族（带索引栈的服务形态），非进程内轻量查询。

## Goals

- 裁定 1（符号域）：P1 rename v1 符号域 = 单模块内——WI10 局部变量/参数（单文件，ScopeAnalyzer）；WI11 字段/非虚方法/类型（模块内，简单名 + 同包/import 绑定过滤，歧义/跨模块显式 nonApplied）。
- 裁定 2（引用搜索落点）：操作器内嵌轻量索引，不消费 nop-code；baseline §二 的 CORE→CODE 边在 v1 不接线，需 design 01 增注记录。
- 裁定记录 + 覆盖率数字落当日 log；roadmap WI2 checkbox 勾选；WI9 转录义务登记在案。

## Non-Goals

- 不实现任何 rename/codemod 代码（WI9-WI12 的 scope）。
- 不修改 nop-lint / nop-treesitter / nop-code 任何行为（本 plan 零产品代码变更）。
- 不补测"编译类路径模式"（理由见 analysis §三.4：即便补测也不改变裁定——成本模型与无状态语义冲突）。
- 不为工程级 rename 设计升级方案（超预算，vision 原则 9（§三.9）复杂度预算；仅登记升级路径存在）。

## Scope

### In Scope

- `ai-dev/analysis/2026-09/2026-09-25-wi2-symbol-solver-coverage-spike.md`（已落，本 plan 收口其裁定效力）
- `ai-dev/design/nop-refactor/01-architecture-baseline.md`（§二 依赖边增注：CODE 边 v1 不接线 + 裁定引用）
- `ai-dev/backlog/nop-refactor-roadmap.md`（WI2 checkbox）
- `ai-dev/logs/2026/09-25.md`（裁定条目）

### Out Of Scope

- `_tmp/wi2-spike/` 代码本身（已存在，不进 git）
- 一切产品模块代码

## Execution Plan

### Phase 1 - 裁定落档（Decision）

Status: planned
Targets: `ai-dev/design/nop-refactor/01-architecture-baseline.md`、`ai-dev/analysis/2026-09/2026-09-25-wi2-symbol-solver-coverage-spike.md`

- Item Types: `Decision`

- [ ] design 01 增注：§二 依赖规则处补一行"引用搜索（CORE→CODE 边）：WI2 实测裁定 v1 不接线——跨模块源码根解析率 0.3%（18/5292）、第三方模块无 classpath 装配器时解析面崩塌（nop-lint-core 9.0%、nop-lint-java 14.4%、nop-java-parser 30.7%），引用搜索落操作器内嵌轻量索引（证据：`ai-dev/analysis/2026-09/2026-09-25-wi2-symbol-solver-coverage-spike.md`）"；§二 mermaid 图的 `CORE --> CODE` 边同步加"v1 不接线"标注（图与文字不得矛盾，design 为最终态）；§七 **仅对"classpath 装配与引用搜索来源"子句**补"已裁定（2026-09-25）"指针——同句"逐操作可行性"部分仍未裁定、留给逐操作 design，不得一并关闭
- [ ] 核对 analysis 文档 §四 两项裁定的措辞与 roadmap WI10/WI11 的 fail-closed 语义一致（歧义 → nonApplied 枚举值，不静默）；顺手修正 analysis §四 裁定 2 的错指针（"roadmap §七'不引入外部索引器'"实际出自 roadmap Framework/Platform Reuse 表——改为指向该表）
- [ ] `ai-dev/logs/` 对应日期条目已更新（读-合并-写，含三项关键数字）

Exit Criteria:

- [ ] design 01 增注落档且与 analysis 文档数字一致（9.0%/14.4%/30.7%、72.3%/61.9%、18/5292）；mermaid 图标注与文字增注一致；§七 注记仅覆盖 classpath/引用搜索子句
- [ ] 裁定记录在 log 可见（含 spike 证据路径）；analysis 错指针已修正
- [ ] 纯文档任务：No build required（零产品代码变更）
- [ ] 任一项 FAIL（措辞不一致/编辑冲突）：本 plan 不得关闭——`Plan Status` 置为 `blocked`，阻塞点落执行当日 log，roadmap WI2 保持未勾

### Phase 2 - roadmap 勾选与转录义务登记（Proof）

Status: planned
Targets: `ai-dev/backlog/nop-refactor-roadmap.md`

- Item Types: `Proof`

- [ ] roadmap `Work Item Status` WI2 checkbox 勾选——**勾选时点 = 本 plan Closure Gates 全过且独立 closure audit PASS 之后**（roadmap 状态块语义："勾选 = 独立 closure audit 通过"；不得在 audit 前预勾）；任一 FAIL 则本 plan 置 `blocked`、WI2 保持未勾
- [ ] roadmap Current Baseline 补一行 WI2 裁定结果（"P1 符号域 v1=单模块内、引用搜索=内嵌索引（2026-09-25 WI2 spike，`ai-dev/analysis/2026-09/2026-09-25-wi2-symbol-solver-coverage-spike.md` 为证）"），供 WI9 plan 起草者一眼可见

Exit Criteria:

- [ ] WI2 checkbox 勾选且 Current Baseline 行落档；勾选前提 = Phase 1 全部完成 **且本 plan Closure Gates 全过、独立 closure audit PASS**（执行顺序：Phase 1 → Closure Gates → audit → 回填勾选）
- [ ] `ai-dev/logs/` 对应日期条目已更新（或与 Phase 1 合并为同一条目）

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 两项裁定（符号域/引用搜索落点）落档 analysis + design 增注，数字与 spike 证据一致
- [ ] roadmap WI2 勾选 + Current Baseline 裁定行落档
- [ ] WI9 转录义务在 analysis §五 与 roadmap baseline 行双重登记
- [ ] 零产品代码变更确认（git diff 仅 ai-dev/ 文档）
- [ ] vision 原则 1–9 回扣核对（roadmap Cross-Cutting 要求）：本 WI 为纯文档裁定 plan，原则未被触及——其中原则 9（复杂度预算）恰为裁定 1 的依据（单模块内符号域 = 预算内的收敛选择），closure audit 须显式记录该回扣
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出 0
- [ ] 纯文档计划：No build required（`./mvnw` 条目不适用，已按 guide 例外删除）

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （关闭时填写或写 no remaining plan-owned work）
