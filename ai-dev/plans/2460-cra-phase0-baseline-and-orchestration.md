# 2460 CRA Phase 0 — 全量基线与 campaign 编排

> Plan Status: completed
> Last Reviewed: 2026-10-14
> Source: `ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` Phase 0（工作项 0.1 / 0.2 / 0.3）
> Related: roadmap 工作项 0.1、0.2、0.3

## Purpose

把并发·异步·资源专项审计 campaign（CRA）的编排基线收口到可执行状态：绿色构建基线已记录、campaign 索引已初始化、mission 配置已生成并通过 dry-run 验证。本 plan 完成后，Wave 1–6 审计工作项的全部前置依赖（0.1/0.2/0.3）即满足。

## Current Baseline

- roadmap 已立项并提交于 master（bcc960e7cc）；本工作基于 master b3a9c76bed 的新分支 `concurrency-audit-remediation`（worktree `nop-entropy-concurrency-audit`）。
- `ai-dev/audits/` 下无任何 `*-concurrency*` 审计目录——C1–C10 口径的首次全模块滚动，无历史包袱。
- 仓库含 mission-driver 工具链（`tools/mission-driver/` + `tools/mission-driver.sh`），已有 31 个 mission 配置可作格式范例（如 `missions/audit-remediation.json`）。
- 环境：Java 26（Zulu 26.0.1）、Maven wrapper 4.0.0-rc-5。
- 真正的 gap：无绿色基线记录、无 `ai-dev/audits/cra-index.md`、无 `missions/concurrency-audit.json`。

## Goals

- 工作项 0.1：`./mvnw clean install -T 1C` 全量通过，基线 commit 记录在 plan 与 cra-index。
- 工作项 0.2：`ai-dev/audits/cra-index.md` campaign 索引建立（报告清单 + P0 追踪 + P1/P2 台账骨架）。
- 工作项 0.3：`missions/concurrency-audit.json` 生成，roadmapPath/plansDir/auditsDir 均指向 `ai-dev/` 下路径，mission-check 与 dry-run 验证通过。
- roadmap 顶部 Work Item Status 中 0.1/0.2/0.3 状态更新为 `done`。

## Non-Goals

- 不执行任何审计工作项（Phase 1–6）。
- 不修改任何产品代码、ORM 模型或 owner doc。
- 不初始化 arm-* 系列索引（那是 security/full-code-review campaign 的；本 campaign 用 cra-index.md）。

## Scope

### In Scope

- 全量构建验证与基线记录
- `ai-dev/audits/cra-index.md` 新建
- `missions/concurrency-audit.json` 新建 + `node tools/mission-driver/src/mission-check.mjs` 校验 + dry-run
- roadmap 状态块更新、daily log、本 plan 收口

### Out Of Scope

- 一切审计执行与修复
- mission-driver 的正式 run（campaign 由本会话串行驱动，mission 配置仅作为交付物 + dry-run 验证）

## Execution Plan

### Phase 1 - WI 0.1 全量绿色基线

Status: completed
Targets: 全仓库构建

- Item Types: `Proof`

- [x] `./mvnw clean install -T 1C` 全量通过（后台日志 `_tmp/build-baseline.log`）
- [x] 基线 commit SHA 记录到本 plan Closure 段与 cra-index.md

Exit Criteria:

- [x] 构建日志末尾 `BUILD SUCCESS`，退出码 0
- [x] 基线 commit = 执行期间工作区 HEAD（b3a9c76bed 或之后仅含本 campaign 文档提交的 SHA），已记录
- [x] `ai-dev/logs/` 对应日期条目已更新

> 执行记录：首轮 452/453 模块过，nop-lint-js 3+1 测试失败——根因是 `ai-dev/tools` 缺 `typescript` npm 包（tsc-bridge 的 devDependency，见 plan 2026-09-22-0854-1-tsc-bridge.md 的环境契约），`npm install` 后恢复；随后单次完整 `clean install -T 1C` 重跑：453/453 SUCCESS，10:51 min，日志 `_tmp/build-baseline-full.log`。

### Phase 2 - WI 0.2 cra-index.md campaign 索引

Status: completed
Targets: `ai-dev/audits/cra-index.md`

- Item Types: `Feature`

- [x] 新建 `ai-dev/audits/cra-index.md`：报告清单表 + P0 发现追踪表（即时通道）+ P1 发现汇总表（待 Phase 8 批量修复）+ P2 裁决台账表（7.1 裁决后回填）骨架
- [x] 表头与 roadmap 的 finding ID 规范（`{单元标识}-C{N}-{序号}`）一致，附使用说明
- [x] 在 `ai-dev/audits/README.md` 登记 cra-index 这一 campaign 索引约定（Owner Doc 归位）

Exit Criteria:

- [x] cra-index.md 存在且含全部四张表骨架，表结构与 roadmap「修复立项方法论」对应（P0 即时通道 / P1 批量 / P2 裁决 / 报告清单）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - WI 0.3 mission 配置与 dry-run

Status: completed
Targets: `missions/concurrency-audit.json`

- Item Types: `Feature`

- [x] 参照 `missions/audit-remediation.json` 格式生成 `missions/concurrency-audit.json`：roadmapPath=`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md`、plansDir=`ai-dev/plans`、auditsDir=`ai-dev/audits`、planGuide 指向 plan guide、commands 用仓库真实命令
- [x] `node tools/mission-driver/src/mission-check.mjs missions/concurrency-audit.json .` 通过
- [x] `./tools/mission-driver.sh run concurrency-audit --dry-run --no-monitor` 通过（不产生实际执行）

Exit Criteria:

- [x] mission-check 退出码 0，dry-run 编排输出覆盖 Phase 0–9 工作项且无路径错误
- [x] roadmapPath/plansDir/auditsDir 全部落在 `ai-dev/` 下（roadmap Rules 最后一条的验收条件）
- [x] `ai-dev/logs/` 对应日期条目已更新

> 执行记录：mission-check `valid: true`；dry-run completed（16 步，识别 34 个 plan 工作项，mock 模式无实际执行）。

### Phase 4 - 状态写回与收口

Status: completed
Targets: roadmap、daily log、本 plan

- Item Types: `Follow-up`

- [x] roadmap Work Item Status 块 0.1/0.2/0.3 → `done`（逐项完成时即时更新，不批处理）
- [x] daily log `ai-dev/logs/2026/10-14.md` 记录 Phase 0 完成
- [x] 本 plan checklist 全勾、closure audit 通过后 Plan Status → `completed`

Exit Criteria:

- [x] roadmap 状态块与实际一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 独立子 agent closure audit 通过并记录证据

## Closure Gates

- [x] WI 0.1/0.2/0.3 的全部 Exit Criteria 已勾选
- [x] 无 in-scope live defect 被降级（本 plan 无代码变更，不适用项已显式标注）
- [x] 独立子 agent closure-audit 完成并记录证据
- [x] 文档链接检查 `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

> 构建验证即 Phase 1 本体；Phase 2/3 为纯文档/配置新增，无代码变更，无需 mvn test 重复验证。

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: Phase 0 三个工作项全部完成并经独立 closure audit PASS（2026-10-14）：绿色基线 453/453 模块 BUILD SUCCESS（master b3a9c76bed 单次完整运行）、cra-index 台账就绪、mission 配置 mission-check valid + dry-run completed。Wave 1–6 审计的全部前置依赖满足。
Completed: 2026-10-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_91badc32-9161-4f17-9e86-89a6aad45106）
- Evidence:
  - WI 0.1：`_tmp/build-baseline-full.log` 末尾 BUILD SUCCESS、453 个 SUCCESS 模块、0 次 Failed to execute；基线 commit b3a9c76bed 已记录于 cra-index.md 头部 — PASS
  - WI 0.2：cra-index.md 四张台账表 + 使用说明 + finding ID 规范齐备；`ai-dev/audits/README.md` 已登记约定 — PASS
  - WI 0.3：mission json 路径全部落在 `ai-dev/` 下；`mission-check.mjs` 实跑 valid:true 退出码 0 — PASS
  - roadmap 0.1/0.2/0.3 = done；daily log 一致；`check-doc-links.mjs --strict` 退出码 0 — PASS
  - Anti-Hollow：cra-index 非空壳、mission json 机器校验通过、daily log 与 live 产物对应 — PASS
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2460-cra-phase0-baseline-and-orchestration.md --strict` 退出码 0（回填后复验）

Follow-up:

- no remaining plan-owned work
