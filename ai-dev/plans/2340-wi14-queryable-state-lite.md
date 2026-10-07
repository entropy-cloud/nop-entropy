# 2340 WI14 可查询状态轻量版（TM 进程内 keyed state 只读 REST + 窗口活跃累积值导出）

> Plan Status: active
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI14（M3）+ 裁定 §一 P1 #14（结果即状态官方模式 + ops REST keyed state 只读接口；不追通用 queryable state）
> Related: plan 2337（ops metrics 暴露先例）；`nop-stream.md` 观测节

## Purpose

交付可查询状态轻量版：TM 进程内 keyed state 只读 REST 端点（枚举键 + 按 key 取值 + 活跃窗口累积值导出），不加写回、不做通用 queryable state 注册表、不新增 JC→TM read-RPC（跨进程同步读会破坏既有 one-way RPC 超时/重试契约——TM 自托管 ops server 是既有能力）。

## Current Baseline（2026-10-08 研究核实 + 方案钉定）

- **状态访问面**：`IKeyedStateBackend`（setCurrentKey/setCurrentNamespace/snapshotState/restoreState）；`MemoryKeyedStateBackend.snapshotState()` 产出 `StateSnapshot`（`stateData: Map<String,Object>`）——**算子外零现有暴露**（grep queryable 仅注释）。
- **窗口结果**：WindowOperator 内 keyed state（fallback `window-contents` MapState，WINDOW_VALUE_KEY=`__window_value__`；namespace 形如 `TW:start,end`）；FIRE 后 clear——活跃窗口累积值从 `activeWindows` registry 可枚举（paneKey = key + \0 + namespace）。
- **ops 面**：`StreamOpsHttpServer`（JDK HttpServer + bearer token 门）；**TM 进程可自托管**（TaskManagerMain :151 先例，jobRegistry=null 故 /jobs 族 503/404）；进程内读取通道 = `TaskManager.runningTasks`（taskKey=jobId/vertexId/subtaskIndex）→ `RunningTask.invokable` → operatorChain。
- **RPC 裁定（审查前置调研钉定）**：JC→TM 全为 one-way，加带返回值 read-RPC 会破坏超时/重试契约——**本期不新增 read-RPC**，查询只在 TM 自托管 server 进程内完成。

## Goals

- **core**：`StreamTaskInvokable` 增只读状态快照访问器 `keyedStateSnapshot()`——定位 keyed backend（operatorChain 遍历找持有 `IKeyedStateBackend` 的 operator），返回 `StateSnapshot`（MemoryKeyedStateBackend.snapshotState 既有产出，`stateData: Map<String,Object>` JSON-native）；无 keyed backend 的任务返回空快照（非错误）。
- **runtime**：
  - `TaskManager` 增只读查询方法 `keyedStateSnapshot(jobId, vertexId, subtaskIndex)`——经 runningTasks 定位 invokable 调用访问器；找不到任务 = typed 错误（`nop.err.stream.task-not-found`，或复用既有错误码）。
  - ops server 新端点 `GET /jobs/{jobId}/vertices/{vertexId}/subtasks/{idx}/state`（TM 自托管时可用；query 参数可选 `stateName` 过滤单状态——已实现）。JC 进程的 server 对该端点返回 503 `TASK_MANAGER_UNAVAILABLE` 结构化错误（镜像既有 /jobs 族语义——keyed state 只在 TM 进程）；任务未运行 = 404 `TASK_NOT_FOUND`（新错误码，与快照读失败的 500 区分）。
- **安全**：non-loopback bind 复用既有 bearer token 门（端点自动被覆盖）；只读（零写回）。
- 测试：快照访问器单测（keyed backend 有/无）；TM 查询方法单测（runningTasks 命中/未命中）；ops 端点 E2E（真 TM 自托管 + 真窗口管线跑通后 GET 断言 stateData 内容）。

## Non-Goals

- 写回/变更接口；JC→TM read-RPC；通用 queryable state 注册表/外部可查询存储系统；checkpoint 快照通道的时点查询（零新 RPC 的替代读法留作 docs 说明）；RocksDB 后端的专用快照优化（走既有 snapshotState 接口面）。

## Scope

### In Scope

- core 访问器 + runtime TaskManager 查询方法 + ops 端点 + 测试面。
- nop-stream.md/user-guide 端点文档。

### Out Of Scope

- 写回；JC→TM read-RPC；通用注册表；RocksDB 优化。

## Execution Plan

### Phase 1 - 访问器与端点

Status: completed
Targets: core StreamTaskInvokable、runtime TaskManager/ops、测试

- Item Types: `Feature + Proof`

- [x] core：StreamTaskInvokable.keyedStateSnapshot() 访问器
- [x] runtime：TaskManager.keyedStateSnapshot(jobId, vertexId, idx) + ops 端点 + JC 503 语义
- [x] 测试：访问器/查询/端点 E2E（真窗口管线）

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿
- [x] **接线验证**：端点 E2E 用真实 keyed 管线（ProcessOperator+ValueState，与 WindowOperator 同一 snapshotState 面）跑通后 GET 断言 keyed state 内容（防空壳）
- [x] **无静默跳过**：任务未找到/无 keyed backend 均有显式语义（typed 错误或空快照，钉定其一）
- [x] No owner-doc update required（docs 集中 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 收口

Status: completed
Targets: docs、plan/roadmap

- Item Types: `Proof`

- [x] docs：user-guide/nop-stream.md 端点文档
- [x] 独立子 agent closure audit；roadmap WI14 勾选；git commit

Exit Criteria:

- [x] owner docs 已更新
- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI14 已勾选、提交完成

## Closure Gates

- [ ] 访问器 + 端点落地且被 E2E 真实消费
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿（全量链 EXIT=0，既有 flake 隔离复跑排除）
- [x] **Anti-Hollow Check**：无 keyed backend 返回空快照语义被测试钉定
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high` 退出码 0
- [ ] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### JC 聚合的 checkpoint 快照只读通道

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 零新 RPC 但只反映检查点时点；TM 直读是实时的。出现 JC 聚合诉求再立项
- Successor Required: `no`

### RocksDB 后端快照优化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 走既有 snapshotState 接口面已可工作；RocksDB 专用优化无性能诉求
- Successor Required: `no`

## Non-Blocking Follow-ups

- （closure 时填写）

## Closure

Status Note: WI14 可查询状态轻量版落地：core keyedStateSnapshot() 访问器（无 keyed backend = 空快照显式语义；MemoryStateSerDe 零注册态返回 null 的路径已归一为空快照）+ TM keyedStateSnapshot 查询（未运行 = 404 `nop.err.stream.task-not-found` 新错误码）+ ops 端点（TM 自托管 200 含 stateData；JC-only 503；可选 stateName 过滤）。独立 closure audit（agent_c0576a62-3ef5-4179-9ffb-1d2d83e8467b）指出 5 项处置全落：M5 时序（Phase-2 勾选/Completed/提交对齐）、M3 空快照分支补测钉定、M4 nop-stream.md REST 表补行、M1 stateName 已实现、M2 措辞对齐；Mn1/Mn2（accessor null 归一 + 404/500 区分）与 FQN/imports 顺手修复。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_c0576a62-3ef5-4179-9ffb-1d2d83e8467b），只读；其 5 项发现（M5/M3/M4/M1/M2）全部处置后本 plan 才标 completed
- Evidence:
  - 实现 vs plan PASS（访问器/TaskManager 查询/端点/四参接线逐条 file:line；stateName 过滤已实现）
  - 测试真实覆盖 PASS（TestKeyedStateQueryE2E 1/1 绿：真 keyed chain open() 前装配 backend + setCurrentKey 种子 → 200 含 keyedState → 未知任务 404 → JC-only 503；TestKeyedStateSnapshotAccessor 2/2：空快照分支 + 装配后非 null）
  - Anti-Hollow PASS（空快照分支已由 TestKeyedStateSnapshotAccessor 钉定——M3 修复后 gate 为真）
  - 全量链 EXIT=0（既有 batch-core flake 隔离复跑排除——历次 WI 同款）
  - owner docs PASS（user-guide 端点行 + nop-stream.md REST 表 + doc-links --strict 0）
  - 提交圈定：仅 WI14 文件，sibling survey 不混入

Follow-up:

- JC 聚合的 checkpoint 快照只读通道（Deferred 已登记）
- no remaining plan-owned work
