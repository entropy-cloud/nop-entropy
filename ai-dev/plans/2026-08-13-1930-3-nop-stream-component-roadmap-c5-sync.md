# 3 component-roadmap C5 容错缺口表与 §5 技术债表同步（P1-18-02）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Mission: nop-stream-invariant-loop
> Source: `ai-dev/audits/2026-08-13-1930-multi-audit-nop-stream-invariant-loop.md` P1-18-02（component-roadmap C5 状态表三行过期 + §5 自相矛盾）
> Related: `2026-08-13-1243-3-nop-stream-cross-module-contract-remediation.md`（P1-DOC-01/02/03 文档契约修复先例）；`ai-dev/design/nop-stream/checkpoint-design.md`（权威状态来源 §13.2/§8.7）

## Purpose

把 `ai-dev/design/nop-stream/component-roadmap.md` 中 C5 容错缺口表的过期行（abort 接线 / 并发能力一致 / 背压逃生，含表内同类过期行 :189 abort 传播通道）与 §5 已知技术债表的矛盾行（:308 unaligned、:307 distributed abort 表述）回写到 live baseline 一致状态，消除文档契约漂移。

## Current Baseline

- **live 缺陷点（已核实）**：
  - `component-roadmap.md:186`（abort 接线行）：写"distributed 路径 abort 接线（cancelTask RPC + JobCoordinator listener）仍为 Deferred（lease failover 兜底）"；**live 代码** `JobCoordinator.registerDistributedAbortHandler()`（`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1551`）+ `RpcDistributedExecutor.java:250 coordinator.registerDistributedAbortHandler()` 均存在——distributed abort 已接线。
  - `component-roadmap.md:191`（并发能力一致行）：写"`effectiveMaxConcurrent = Math.min(1, config)` 强制 max=1"；**live 代码** `CheckpointCoordinator.java:370`（`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:370`）为 `int effectiveMaxConcurrent = config.getMaxConcurrentCheckpoints();` ——**无 Math.min(1,...) clamp**。
  - `component-roadmap.md:193`（背压逃生/unaligned 行）：写"端到端背压验证见 Phase 4"（隐含未完成）；**live 代码** unaligned 全链路已实现且有 `TestChannelStateRescaleE2E`/`TestInputGateSingleChannelRemoteLiveness`。
  - `component-roadmap.md:308`（§5 已知技术债表）：写"unaligned checkpoint 未实现（Stage 43 Phase 2–4 进行中）"——与 :193 自身矛盾，且与 live code 不符。
- **权威对照**：同目录 `checkpoint-design.md` §13.2（容错契约状态表，abort/并发/unaligned 各行均已同步）/§8.7（checkpoint 超时 abort 接线，distributed 部分 Stage 39 Phase 3 已落地）/§2.11（unaligned 背压逃生语义）为已同步的权威状态。
- **表内同类过期表述（复核发现，同属 P1-18-02 漂移族）**：`:189`（abort 传播通道行）写"前置依赖：独立控制通道（local 直接方法调用，distributed 需 RPC 通道）| P1"——§13.2 abort 传播通道行已标注 distributed 部分 Stage 39 Phase 3 落地（`cancelTask` RPC 是独立控制通道，local 形态 mailbox + interrupt）；`§5 :307` 行写"abort 控制通道 local 路径已完成（distributed 仍 Deferred）"——与 §13.2 传播通道行矛盾，均应随本次回写修正。
- **审计注记**：roadmap §4 有"动态规划"免责声明，但 §3 C5 是明确标注"当前实现状态"的状态表，且该表其他行（对齐超时/心跳）均与代码一致，证明本应同步而这三行遗漏。

## Goals

- C5 相关行（abort 接线 :186 / 并发能力一致 :191 / 背压逃生 :193 / 传播通道 :189）按 live code + checkpoint-design 状态回写。
- §5 技术债表矛盾行（:308 unaligned、"abort 控制通道 distributed 仍 Deferred" :307）删除或修正。
- `node ai-dev/tools/check-doc-links.mjs --strict` exit 0。

## Non-Goals

- 不修改 checkpoint-design.md（其已是权威同步状态）。
- 不处理其他文档漂移（P2-18-03/08/13/14 及 P2-DOC-04~11 批次，backlog 维持）。
- 不涉及任何代码变更。

## Scope

### In Scope

- `ai-dev/design/nop-stream/component-roadmap.md` C5 表过期行（:186/:189/:191/:193）+ §5 技术债表矛盾行（:307/:308）。
- 审计/backlog 状态流转（roadmap backlog 或 audit 文件状态）。

### Out Of Scope

- 代码变更（纯文档计划）。
- 其他文档文件。

## Execution Plan

### Phase 1 - C5 三行 + §5 矛盾行回写

Status: completed
Targets: `ai-dev/design/nop-stream/component-roadmap.md`

- Item Types: `Fix`

- [x] Fix：:186 abort 接线行回写——distributed 路径已接线（`registerDistributedAbortHandler` + RpcDistributedExecutor 调用），标注 Stage 39 Phase 3 落地状态；同步核对 :189 abort 传播通道行——"无 CancelCheckpointMarker"仍为真（§13.2.1 Decision-only），但"前置依赖：独立控制通道"已落地（local mailbox + distributed cancelTask RPC，Stage 39 Phase 3），按 §13.2 传播通道行更新状态、删除过期 P1 前置依赖表述。
- [x] Fix：:191 并发能力一致行回写——删除 `Math.min(1, config)` 与"降级 + 警告"描述，按 §13.2 并发能力一致行（Coordinator 层完整尊重 `maxConcurrentCheckpoints` 配置值，task/对齐器层 Stage 45 多 in-flight 已满足，无降级语义）与 live 代码（`effectiveMaxConcurrent = config.getMaxConcurrentCheckpoints()`，:370）更新；同步删除行内"task 层多 checkpoint 支持作为 Deferred"过期表述。
- [x] Fix：:193 背压逃生行回写——unaligned 全链路已实现 + 已修复状态（Stage 43 Phase 2-3），"端到端背压验证"按实际状态更新（如已由 E2E 覆盖则标注 ✅；否则标注剩余验证项）。
- [x] Fix：:308 §5 技术债表矛盾行——删除"unaligned checkpoint 未实现"行或修正为已实现状态（与 :193 及 live code 一致）；同步核对 §5 其他行同类过期表述（:307 "abort 控制通道 distributed 仍 Deferred"与 §13.2 传播通道行矛盾则一并修正）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] C5 表相关行（:186/:189/:191/:193）与 live code 及 §13.2 一致（grep 核对 `registerDistributedAbortHandler` / `effectiveMaxConcurrent` / unaligned 相关描述；:189 控制通道状态按 §13.2 传播通道行更新）。
- [x] §5 表无与 C5 及 live code 矛盾的 unaligned/abort 表述（:308 unaligned 行 + :307 distributed abort 表述）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0。
- [x] 变更仅限 `component-roadmap.md`（+ 必要的审计/日志文件）；No code change required（纯文档）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

> **纯文档计划**：不涉及代码变更，`./mvnw test` / `./mvnw lint` 等构建验证条目已从 Closure Gates 删除。

- [x] P1-18-02 已修复（C5 相关行 :186/:189/:191/:193 + §5 矛盾行 :307/:308 与 live code 及 §13.2 一致）。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope owner-doc drift。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0。
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2026-08-13-1930-3-nop-stream-component-roadmap-c5-sync.md --strict` exit 0（Minimum Rule #26）。

## Deferred But Adjudicated

### P2-18-03/08/13/14 及其他文档漂移批次

- Classification: `watch-only residual`（backlog 登记）
- Why Not Blocking Closure: 独立文档面（cep-design/core-design/architecture-baseline 等），与本 plan 的 C5 表修复不重叠；backlog 触发条件维持。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 无（纯文档计划，无 plan-owned 剩余工作）。

## Closure

Status Note: P1-18-02 六行文档契约漂移（C5 表 :186/:189/:191/:193 + §5 :307/:308）已全部按 live code 与 checkpoint-design §13.2/§8.7 权威状态回写，纯文档计划，零代码变更；独立 closure audit APPROVE。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（task `ses_0041924cfffe4XrCPYlg8oy1JP`，read-only fresh session）
- Evidence:
  - Exit Criteria 1（C5 行与 live code 一致）：PASS——:186 `JobCoordinator.registerDistributedAbortHandler`（live JobCoordinator.java:1593）+ `RpcDistributedExecutor.java:250` 实证；:191 `effectiveMaxConcurrent = config.getMaxConcurrentCheckpoints()`（live CheckpointCoordinator.java:370，无 clamp）；:193 三个测试文件（TestUnalignedCheckpointBackpressure / TestUnalignedCheckpointMultiInput / TestChannelStateRescaleE2E）在盘实证；:189 控制通道按 §13.2 传播通道行更新。
  - Exit Criteria 2（§5 与 C5/live code 无矛盾）：PASS——:307 无 "distributed 仍 Deferred"、:308 无 "unaligned checkpoint 未实现"；全文件 grep `Math.min(1` / `未实现（Stage 43` / `仍为 Deferred` 零命中。
  - Exit Criteria 3（doc-links）：PASS——`node ai-dev/tools/check-doc-links.mjs --strict` exit 0（0 errors；3 warnings 为 credential plan 既有噪音，与本变更无关）。
  - Exit Criteria 4（仅 component-roadmap.md）：PASS——git diff 仅 4 个 ai-dev 文件（component-roadmap.md 6+/6- + backlog/log/plan），零代码变更。
  - Exit Criteria 5（日志）：PASS——`ai-dev/logs/2026/08-13.md` 条目在案。
  - Closure Gate 4（check-plan-checklist）：PASS——`node ai-dev/tools/check-plan-checklist.mjs 2026-08-13-1930-3-nop-stream-component-roadmap-c5-sync.md --strict` exit 0。
  - Anti-Hollow：纯文档计划不适用代码链验证；closure audit 已实证文档描述与 live code 一致（检查项 1-6 全 PASS），无空壳表述。
  - Deferred 项分类检查：P2-18-03/08/13/14 及其他文档漂移批次 = `watch-only residual`（独立文档面，与本 plan 不重叠），无 in-scope live defect 被降级。
- Follow-up:
  - 无 plan-owned 剩余工作（纯文档计划）。Non-blocking：plan Current Baseline 引用的 `JobCoordinator.java:1551` 实际为 :1593（文档正文已用正确行号，仅 plan 叙述行号陈旧，不影响交付）。
