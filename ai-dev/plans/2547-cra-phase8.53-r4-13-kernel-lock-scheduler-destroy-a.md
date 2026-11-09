# 2547 CRA Phase 8.53 — R4-13 2.1 锁纪律/调度器/destroy 束 a（G27 前半，18 工单）

> Plan Status: draft
> Last Reviewed: 2026-11-09
> Source: roadmap 工作项 8.53（`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` :224，Status=todo；Owner Doc=`docs-for-ai/04-reference/source-anchors.md`；`docs-for-ai/02-core-guides/ioc-and-config.md`；Deps=「batch-proposal.md §二 R4-13（18 项）；硬依赖 8.2/8.3/8.10（均已 done）」）；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 :128 R4-13 行 + §二.5 G27 行「36 | R4-13（18）+ R4-14（17）+ R4-32（C7-004）」；成员发现 = `ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/`
> Related: plan 2468（2.1 审计）、plan 2504（R2-01 kernel 批，C1-010/C2-004 邻接先例）；设计文档 = `ai-dev/design/concurrency/kernel-lock-discipline-interrupt-contract-and-executor-destroy-semantics.md`
> **Protected Area 授权**：`nop-kernel/nop-commons` / `nop-core` / `nop-xlang` = AGENTS.md「框架核心引擎」= **plan-first**。授权载体 = 设计文档 + 独立 draft review + §五 回归面清单。**未过 review 不得改码。**

## Purpose

关闭 R4-13 的 18 个 nop-kernel P2 工单：复合操作原子性（4）、中断契约（1）、回调锁域（2）、destroy 语义（1）、提交/拒绝显式性（5）、声明/配置面（3）、单例槽位（1）、文件锁句柄会话（1）。

## Current Baseline

- **判级**：18 项全 P2（merge-adjudication 单元内裁定）。
- **依赖**：8.2（R1-01 绝缘层，done）、8.3（R1-02 池设施，done）、8.10（R2-01 kernel 退役代际，done）。
- **PA 判定**：全部站点在 nop-kernel（框架核心）→ plan-first。**本批是本 campaign 首个全 PA 批次**，须走完整设计 → review → 实施 → 独立 closure audit。
- **live 基线事实**（2026-11-09 亲读 + 设计文档 §三 逐条锚定）：
  - `RateLimitExecutorImpl.debounce` :50-67 `get`-`cancel`-`schedule`-`put` 非原子 + `holder[0]` 提交后写（`throttle` :29-47 已有 `compute` 正确先例）。
  - `ResourceComponentManager` :334-344 `computeIfAbsent` 内 `transform`；`XLangSemantics` :1094-1098 `CLASS_MODELS.computeIfAbsent` 内 `ClassHelper.forName`。
  - `AbstractResourceLock.tryLockWithLease` :50-69 把中断转 `NopException`；`lockInterruptibly()` 无 `throws`。
  - `CycleSynchronizer` :36-46/:66-102 持 `this` 锁跑回调、无 generation。
  - `LocalFileLock` :31-37/:45-90 句柄为实例字段无同步；`tryLock(0)` 失败路径泄漏。
  - `ErrorMessageManager` :42-45/:172-17x 构造器 this-escape；多处 `_instance` 非 volatile。
  - `SequentialTaskExecutor.execute` :79-83 忽略 offer 返回值；`BindScheduledExecutor` :88-116 重排无 catch；`BlockingSourceConsumeTask` :48-76 循环可静默死亡；`AbstractBatchProcessService.destroy` :71-78 丢弃队列；`DefaultTaskExecutionQueue` :236-240 `bindCancel` 参数反接。
  - `IAsyncCache`/`LocalCache`/`CacheConfig` 语义分叉与 executor 固化。
  - `BaseThreadPoolConfig` :17-35 的 `waitForTasksToCompleteOnShutdown`/`awaitTerminationSeconds` 为死值；`destroy()` 不等待。
- **基线测试签名**（起草期）：`./mvnw test -pl nop-kernel` 全绿。

## Goals

- 18 工单收口（裁定 A–H 八组）。
- 每条工单 ≥1 可被回归捕获的载体；**PA 批次额外要求**：每条都标明消费方回归面（设计 §五）。
- C2-007 的接口签名变更须**同 diff 完成全仓调用点改造**（否则编译断）。
- owner doc 落账（`source-anchors.md` + `ioc-and-config.md`）。

## Non-Goals

- 不做 R4-14（8.54）的 17 个成员；不做 R4-32（8.72）的 C7-004 墙钟批。
- 不改 `nop-xdefs` schema；不改 orm 模型。
- **不做 C3-009 的行为重写**（改 async 加载语义会破坏既有契约）→ 仅声明面。
- **不做 C3-010 的缺省 executor 更换**（改 globalWorker 有死锁风险）→ 仅给配置出口。

## Scope

### In Scope

`nop-kernel/nop-commons`（`RateLimitExecutorImpl`、`CycleSynchronizer`、`LocalFileLock`、`AbstractResourceLock`/`ResourceLock`/`ResourceMultiLock`、`LocalResourceLockManager`、`SequentialTaskExecutor`、`BindScheduledExecutor`、`BlockingSourceConsumeTask`、`AbstractBatchProcessService`、`ExecutorHelper`、`DefaultThreadPoolExecutor`、`DefaultScheduledExecutor`、`BaseThreadPoolConfig`、`LocalCache`、`CacheConfig`、`IAsyncCache`）
`nop-kernel/nop-core`（`ResourceComponentManager`、`ModuleManager`、`ErrorMessageManager`、`VirtualFileSystem`、`DefaultTaskExecutionQueue`）
`nop-kernel/nop-xlang`（`XLangSemantics`）
测试树 + owner doc。

### Out Of Scope

Non-Goals 所列。

## Execution Plan

### Phase 1 - 设计裁定

Status: in progress
Targets: `ai-dev/design/concurrency/kernel-lock-discipline-interrupt-contract-and-executor-destroy-semantics.md`

- Item Types: `Decision`

- [x] 设计文档落盘（八条裁定 A–H + 18 成员逐条唯一形态 + 25 条拒绝方案 + 回归面清单）
- [ ] 独立 draft review（fresh session，逐条到 live 代码核实可执行性）
- [ ] review 发现的 Blocker/Major 全部闭环
- [ ] PA 授权证据齐备（设计 + review + 回归面）
- [ ] `ai-dev/logs/` 已更新

Exit Criteria:

- [ ] 设计文档在案且 18 成员各有唯一形态
- [ ] 独立 draft review PASS（或 PASS-with-revisions 且修订全闭环）
- [ ] 回归面清单覆盖全部 18 成员
- [ ] `ai-dev/logs/` 已更新

### Phase 2 - 18 工单实现 + 回归测试

Status: planned
Targets: nop-commons / nop-core / nop-xlang + 测试树

- Item Types: `Fix`（15）、`Proof`（3 声明面）

**实施顺序（设计 §六，依赖驱动）**
- 组 1（无兼容性破坏）：C1-004、C1-009、C1-011、C2-003、C2-008、C2-019、C3-002、C3-008、C3-011、C3-012、C3-018
- 组 2（语义变化）：C1-010 + C2-004（同 diff）、C3-013、C4-002
- 组 3（源兼容性破坏，最后）：C2-007
- 组 4（声明/配置）：C3-009、C3-010

- [ ] G1 C1-004：`debounce` 改 `promiseMap.compute`
- [ ] G2 C1-009：单例槽位 `static volatile` + `ErrorMessageManager` this-escape 消除
- [ ] G3 C1-011：`LocalFileLock` 句柄会话化
- [ ] G4 C1-010 + C2-004（同 diff）：`CycleSynchronizer` generation + 回调出锁域 + 逐回调隔离
- [ ] G5 C2-003：`ResourceComponentManager` 占位模式
- [ ] G6 C2-007：`InterruptedException` 传播链（**同 diff 全仓调用点**）
- [ ] G7 C2-008：`LocalResourceLockManager` 过期判定入 latch 锁
- [ ] G8 C2-019：`XLangSemantics` 占位模式
- [ ] G9 C3-002：`BindScheduledExecutor` 重排异常兜底
- [ ] G10 C3-008：`SequentialTaskExecutor.execute` 拒绝显式化
- [ ] G11 C3-009：`IAsyncCache` 语义声明
- [ ] G12 C3-010：`CacheConfig` executor 出口
- [ ] G13 C3-011：`BlockingSourceConsumeTask` 循环韧性
- [ ] G14 C3-012：`AbstractBatchProcessService.destroy` 有界等待 + 丢弃清单
- [ ] G15 C3-013：`execute` 路径上下文卫生
- [ ] G16 C3-018：`DefaultTaskExecutionQueue` bindCancel 与计数
- [ ] G17 C4-002：两类池 destroy 有界等待 + `stats()` 类型化 fail-fast
- [ ] 回归测试：nop-kernel 全量 + 消费方 focused（设计 §五 逐条）
- [ ] **无静默跳过**（rule 24）：拒绝/丢弃/回调异常全部显式，无空 catch

**工单 × 载体表**（Phase 1 review 后补全并逐条 `grep` 校验存在性）

Exit Criteria:

- [ ] 18 工单全部 landed
- [ ] 载体表逐条存在且断言目标行为（非"不抛异常"）
- [ ] 关键项先红后绿或变异论证（≥4 条：C1-004 重复执行、C2-004 自死锁、C2-007 中断传播、C4-002 destroy 等待）
- [ ] `./mvnw clean install -T 1C -DskipTests` BUILD SUCCESS
- [ ] `./mvnw test -pl nop-kernel` BUILD SUCCESS
- [ ] 设计 §五 回归面逐条执行且登记
- [ ] **端到端验证**（如适用）：`deathTime`-style 生命周期路径（destroy → 有界等待 → stats fail-fast）
- [ ] **接线验证**：C2-007 的 `throws InterruptedException` 在全部调用点被编译验证（全仓编译即证据）
- [ ] `ai-dev/logs/` 已更新

### Phase 3 - 全量验证 + owner doc 落账

Status: planned
Targets: nop-kernel 全量 + owner doc

- Item Types: `Proof`

- [ ] E3 分解通道：`./mvnw test -pl nop-kernel -am` BUILD SUCCESS + 逐模块签名
- [ ] owner doc 落账：`source-anchors.md`（锁/调度/destroy 锚点）+ `ioc-and-config.md`（池配置项生效语义）
- [ ] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--module nop-kernel --severity high` EXIT=0
- [ ] `ai-dev/logs/` 已更新

Exit Criteria:

- [ ] E3 验收且逐条登记
- [ ] owner doc 落账核对
- [ ] 双门禁 EXIT=0 登记
- [ ] `ai-dev/logs/` 已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.53 行、cra-index P2-G27 束行

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session）：逐条核对 Exit Criteria / Closure Gates / Anti-Hollow / PA 回归面完整性
- [ ] cra-index 台账回填：P2-G27 束行（R4-13 半边收敛注）+ roadmap 8.53 → done
- [ ] 双门禁 + Closure Evidence 写入本 plan

Exit Criteria:

- [ ] closure audit 证据写入 Closure 段
- [ ] cra-index + roadmap 在案
- [ ] 双门禁 EXIT=0

## Closure Gates

- [ ] 18 工单全部收口
- [ ] 每工单 ≥1 可被回归捕获的载体
- [ ] `./mvnw test -pl nop-kernel -am` BUILD SUCCESS
- [ ] 设计 §五 回归面逐条执行（PA 硬门禁）
- [ ] owner doc 落账完成
- [ ] scan-hollow nop-kernel `--severity high` EXIT=0
- [ ] 不存在被静默降级的 in-scope live defect
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] **Anti-Hollow Check**
- [ ] `check-plan-checklist --strict` EXIT=0；`check-doc-links --strict` EXIT=0
- [ ] cra-index P2-G27 收敛注 + roadmap 8.53 → done

## Deferred But Adjudicated

（Phase 1 review 后按需补）

## Non-Blocking Follow-ups

（Phase 1 review 后按需补）

## Closure

Status Note: （收口时填写）
Completed: （YYYY-MM-DD）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent fresh session）
- Audit Session: （session ID）
- Evidence:
  - （每条 Exit Criterion / Closure Gate 的 PASS/FAIL）
  - `check-plan-checklist --strict` 退出码
  - Anti-Hollow 结果 + scan-hollow 退出码
  - Deferred 分类检查

Follow-up:

- （仅 non-blocking）
