# 31 WI24 兼容迁移说明与 docs 收口

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI24 行、roadmap 级完成判定四条）
> Related: 全部前序 WI plans（01-30）
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

stream.xdef 变更与四个 fail-fast 放行对既有 .stream.xml 用户的兼容与迁移说明落档；docs-for-ai/INDEX.md 与 source-anchors.md 同步；check-doc-links --strict 0；独立 closure audit 通过且 roadmap 状态、承载 plan、当日日志三处一致（roadmap WI24 行）。

## Current Baseline（2026-10-03 实测）

- stream.xdef 本 roadmap 实际变更面：`<sql>` 顶层元素（WI17，sinkBean + 内嵌 schema + CDATA）——对既有 .stream.xml 用户为纯增量（旧模型不含 `<sql>` 即零影响；含 `<sql>` 则需 nop-stream-sql 在 classpath）。
- 四个 fail-fast 放行（WI10/D9-D12）：allowedLateness 解除双层 fail-fast（节点级覆盖 strategy 级）、accumulationMode/triggerId/窗口级 parallelism 保持 fail-fast——对既有用户零行为变更（放行项此前即 fail-fast，现按 D9 语义消费）。
- 观察面 W2/T1（WI20）：SQL 通道 TUMBLE 原样回显，真实 RDBMS 解析即失败——须标注。
- docs-for-ai/03-modules/nop-stream-sql.md 已建（WI18）；INDEX.md 与 source-anchors.md 已登记；audit OBS-1（user-guide/owner doc 回链）与 OBS-2（compile schema 可空措辞）归本 WI 顺带。
- roadmap 级完成判定四条需逐条核验：31 项 done + roadmapAllDone、WI24 audit 通过、无 hollow 项、D1-D15 落档含负责人与日期。

## Goals

- 迁移说明：docs-for-ai/03-modules/nop-stream-sql.md 增「兼容与迁移」节（`<sql>` 增量语义、SPI classpath 依赖、W2/T1 边界、D9 allowedLateness 放行说明、D1 语义标注）。
- OBS-1：nop-stream.md（owner doc）或 user-guide 与 nop-stream-sql.md 互相回链；OBS-2：nop-stream-sql.md compile 签名处 schema 可空措辞精确化。
- roadmap WI19/WI22 audit 后新登记的 Follow-up Backlog 条目核对在位。

## Non-Goals

- 新能力；改 WI17 编译行为（文档措辞精确化而已）。

## Scope

### In Scope

- `docs-for-ai/03-modules/nop-stream-sql.md`：兼容迁移节 + OBS-2 措辞
- `docs-for-ai/03-modules/nop-stream.md` 或 `nop-stream-user-guide.md`：回链
- `ai-dev/backlog/nop-stream-sql-roadmap.md`：WI24 翻转 + M5/M6 翻转 + 完成判定四条核验记录
- 日志

### Out Of Scope

- 新功能；历史 plan 回写（guide 规则 20）。

## Execution Plan

### Phase 1 - 迁移说明与 docs 收口

Status: planned
Targets: docs-for-ai

- Item Types: `Proof`

- [x] nop-stream-sql.md 增「兼容与迁移」节（`<sql>` 增量语义/classpath 依赖/W2-T1 边界/D9 说明/D1 标注）
- [x] OBS-1 回链 + OBS-2 措辞精确化
- [x] 日志

Exit Criteria:

- [x] 文档节落地且与 live 一致
- [x] check-doc-links --strict 0
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - roadmap 完成判定核验与收口

Status: planned
Targets: roadmap 与本 plan

- Item Types: `Proof`

- [ ] roadmap 完成判定四条逐条核验：(1) 31 项 done + roadmapAllDone=true；(2) 本 plan 独立 audit 通过；(3) 无 hollow 项（scan-hollow 全模块 + invariants 0 violations）；(4) D1-D15 落档核对（ai-dev/design/nop-stream/ 四份文档含负责人与日期）
- [ ] roadmap WI24 `todo` → `done` + M5/M6 翻转 + Last updated 头注记
- [ ] Plan Status → `completed`；check-plan-checklist --strict 0；check-doc-links --strict 0

Exit Criteria:

- [ ] 四条判定核验记录落档（日志）
- [ ] roadmap 全部翻转且解析器断言成立（items=31/milestones=7/done=31）
- [ ] 双门禁退出码 0

## Closure Gates

- [ ] 迁移说明节落地且与 live 一致
- [ ] OBS-1/OBS-2 落档
- [ ] roadmap 四条完成判定逐条核验记录在案
- [ ] roadmap WI24/M5/M6 翻转 + 解析器断言成立（31/7/done=31/roadmapAllDone=true）
- [ ] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/31-wi24-compat-and-docs-closure.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Current Baseline

（见上。）

## Closure

Status Note: 兼容迁移节与 docs 收口落地；roadmap 级完成判定四条全部满足（31/31 done + allDone、独立 audit PASS、六模块 hollow 0 + invariants 0、D1-D15 五文档落档含负责人与日期）——nop-stream-sql roadmap 收口成立。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）
- Evidence: ai-dev/audits/nop-stream-sql/wi24-closure-audit.md——PASS（0 Blocker/0 Major/4 Minor 收口落笔：v9 头注记、五份计数、日志节、WI24 行 PASS 记录）；§5 兼容迁移五条逐句对照实码成立；OBS-1/OBS-2 落档

Follow-up:

- no remaining plan-owned work；roadmap 级遗留见 Follow-up Backlog（FU-1 至 FU-10）
