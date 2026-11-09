# 2548 CRA Phase 8.54 — R4-14 2.1 销毁链/BlockingSource 束 b（G27 后半，17 工单）

> Plan Status: draft
> Last Reviewed: 2026-11-09
> Source: roadmap 工作项 8.54（`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` :225，Status=todo；Owner Doc=同 R4-13；Deps=「batch-proposal §二 R4-14（17 项）；软依赖 8.53」）；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 :129 R4-14 行 + §二.5 G27 行；成员发现 = `ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/`
> Related: plan 2547（R4-13，completed——本批**复用其 C4-002 destroy 语义、C1-010 generation 守卫、C2-019 CLASS_MODELS**，不重建）；设计文档 = `ai-dev/design/concurrency/kernel-destroy-chain-blockingsource-and-resource-lifecycle.md`
> **Protected Area 授权**：nop-kernel / nop-utils = **plan-first**。授权载体 = 设计文档 + 独立 draft review + §五 回归面。

## Purpose

关闭 R4-14 的 17 个 nop-kernel P2 工单（**C6-001 为 P1**）：提交失败结算（1）、全局池生命周期（2）、指标注销（1）、缓存销毁链（3）、阻塞等待可中断（3）、监听/注册表/死旋钮（7）。

## Current Baseline

- **判级**：16 项 P2 + **1 项 P1**（C6-001，本 campaign R4 批唯一 P1）。
- **依赖**：软依赖 8.53（plan 2547 completed）。
- **PA 判定**：全部在 nop-kernel/nop-utils = plan-first。
- **与 2547 的分工**（防冲突）：C7-011 与 2547 的 C1-010 同文件但不同失效面（前者=推进者死亡逃生门，后者=旧周期不污染新周期）；C4-005/C6-003 与 2547 的 C4-002 分工（前者=注册表移除，后者=destroy 内部等待）；C9-004 复用 2547 建好的 CLASS_MODELS。
- **live 基线事实**（2026-11-09 亲读）：
  - `ExecutorHelper.submit/schedule` 五个入口：`future` 先建、`executor.submit(...)` 抛 `RejectedExecutionException` 即逃逸，**future 永不完成**（C6-001 P1 确认）。
  - `GlobalExecutors.destroy` 语义：`getExecutor(name).destroy()` 后 `g_executors` 保留死实例（`register` 才是 put+destroy，正确）。
  - `CacheConfig(CacheConfig)` 拷贝构造漏 `destroyOnRemove`/`refreshAfterWrite`。
  - `ThreadPoolConfig` 的 `threadPriority`/`allowCoreThreadTimeOut` 全仓零消费者。
  - `CycleSynchronizer.beginCycle` 在 `processing==true` 时抛错，无逃生门（C7-011）；2547 已加 generation 守卫（不冲突）。
  - `DefaultRateLimiter` 用 `sleepMicrosUninterruptibly`；`IRateLimiter.acquire()` 默认无上界。
  - `ExecutorHelper.throttleExecute` 用 `acquireUninterruptibly`。
- **基线测试签名**：`nop-kernel` 全量 1856 tests（2547 收口值）。

## Goals

- 17 工单收口；C6-001（P1）必须最先且单独验证。
- 每条 ≥1 可被回归捕获的载体；PA 批次标明消费方回归面。
- 不回退 2547 已建立的语义（generation / destroy 等待 / CLASS_MODELS）。
- owner doc 落账。

## Non-Goals

- 不做 R4-32（8.72）的 C7-004；不做 8.55 起的成员。
- 不改 `nop-xdefs` schema；不改 orm 模型。
- **不做宿主 `nop-boot` 的 destroyAll 接线**（属 2.2 批）→ 本批只提供 API，接线登记 successor。
- **不做「换 registry 自动重绑」**（C6-004 只做池 destroy 时注销）。

## Scope

### In Scope
`nop-kernel/nop-commons`（`ExecutorHelper`、`GlobalExecutors`、`ThreadPoolConfig`、`CycleSynchronizer`、`CacheConfig`、`LocalCache`、`DefaultRateLimiter`、`IRateLimiter`、`DefaultScheduledExecutor`）
`nop-kernel/nop-core`（`ResourceLoadingCache`、`TenantAwareResourceLoadingCache`、`FileWatchEntry`、`NioFileWatchService`、`ResourceDependsManager`）
`nop-kernel/nop-xlang`（`XLangSemantics`）
`nop-utils/nop-commons-java21`（虚拟线程开关）
测试树 + owner doc。

### Out Of Scope
Non-Goals 所列。

## Execution Plan

### Phase 1 - 设计裁定

Status: in progress
Targets: `ai-dev/design/concurrency/kernel-destroy-chain-blockingsource-and-resource-lifecycle.md`

- Item Types: `Decision`

- [x] 设计文档落盘（六条裁定 A–F + 17 成员唯一形态 + 与 2547 分工 + 回归面）
- [ ] 独立 draft review（fresh session，逐条 live 核实）
- [ ] review 发现的 Blocker/Major 全闭环
- [ ] PA 授权证据齐备
- [ ] `ai-dev/logs/` 已更新

Exit Criteria:

- [ ] 设计文档在案且 17 成员各有唯一形态
- [ ] 独立 draft review PASS（或 PASS-with-revisions 且修订全闭环）
- [ ] 回归面覆盖全部 17 成员
- [ ] `ai-dev/logs/` 已更新

### Phase 2 - 17 工单实现 + 回归测试

Status: planned
Targets: nop-commons / nop-core / nop-xlang / nop-commons-java21 + 测试树

- Item Types: `Fix`（16）、`Proof`（1）

**实施顺序（设计 §六）**
- 组 1（P1 最先）：C6-001
- 组 2：C4-005、C6-003、C6-004
- 组 3（依赖序）：C5-003 → C6-005 → C6-012
- 组 4：C6-006、C6-007、C7-008
- 组 5：C7-010、C7-015、C7-002
- 组 6：C4-011、C4-012、C9-004
- 组 7（最后，需复核 2547）：C7-011

- [ ] G1 C6-001（P1）：`ExecutorHelper` 五入口提交失败结算 future
- [ ] G2 C4-005：`GlobalExecutors.destroy` 移除注册表条目
- [ ] G3 C6-003：`GlobalExecutors.destroyAll()` 平台级 API
- [ ] G4 C6-004：`registerMetrics` 注销对称
- [ ] G5 C5-003：`CacheConfig` 拷贝构造补两字段
- [ ] G6 C6-005：`ResourceLoadingCache` 驱逐执行 destroy 回调
- [ ] G7 C6-012：`TenantAwareResourceLoadingCache` 容器 destroyOnRemove
- [ ] G8 C6-006：`FileWatchEntry` unwatch 摘父 + 重复 watch 复用
- [ ] G9 C6-007：`NioFileWatchService` daemon 化 + awaitTermination
- [ ] G10 C7-008：`ResourceDependsManager` 逐条淘汰
- [ ] G11 C7-010：`throttleExecute` 可中断有界
- [ ] G12 C7-015：`IRateLimiter.tryAcquire(timeout)` + 默认上界
- [ ] G13 C7-002：`scheduleAtFixedRate` 补偿语义显式化
- [ ] G14 C4-011：`ThreadPoolConfig` 死旋钮删除/接线
- [ ] G15 C4-012：虚拟线程开关默认/声明
- [ ] G16 C9-004：`XLangSemantics` 复用 CLASS_MODELS
- [ ] G17 C7-011：`CycleSynchronizer` 死区逃生门（**不回退 2547 generation**）
- [ ] 回归测试：nop-kernel 全量 + 设计 §五 逐条
- [ ] **无静默跳过**（rule 24）

**工单 × 载体表**（Phase 1 review 后补全，逐条 grep 校验）

Exit Criteria:

- [ ] 17 工单全部 landed（含 C6-001 P1 单独验证）
- [ ] 载体表逐条存在且断言目标行为
- [ ] 关键项先红后绿或变异论证（≥4：C6-001 future 结算、C6-005 销毁回调、C7-010 可中断、C7-015 有界）
- [ ] `./mvnw -o clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` BUILD SUCCESS
- [ ] `./mvnw test -pl nop-kernel` BUILD SUCCESS（clean）
- [ ] 设计 §五 回归面可执行面逐条执行并登记
- [ ] **端到端验证**：C6-001 的 destroy-then-submit 路径（future 必须异常完成，不挂起）
- [ ] **接线验证**：C9-004 的静态方法调用确实复用 CLASS_MODELS（计数桩）
- [ ] **不回退 2547**：generation 守卫 / destroy 有界等待 / CLASS_MODELS 占位的既有钉全绿
- [ ] `ai-dev/logs/` 已更新

### Phase 3 - 全量验证 + owner doc 落账

Status: planned
Targets: nop-kernel 全量 + owner doc

- Item Types: `Proof`

- [ ] E3 分解通道：`./mvnw test -pl nop-kernel -am` BUILD SUCCESS + 逐模块签名
- [ ] owner doc 落账：`source-anchors.md`（销毁链/监听锚点）+ `ioc-and-config.md`（池/缓存生命周期）
- [ ] 工具门禁：check-doc-links EXIT=0；scan-hollow `--module nop-kernel --severity high` EXIT=0
- [ ] `ai-dev/logs/` 已更新

Exit Criteria:

- [ ] E3 验收且逐条登记
- [ ] owner doc 落账核对
- [ ] 双门禁 EXIT=0 登记
- [ ] `ai-dev/logs/` 已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.54 行、cra-index P2-G27 束行

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session）
- [ ] cra-index 台账回填 + roadmap 8.54 → done
- [ ] 双门禁 + Closure Evidence

Exit Criteria:

- [ ] closure audit 证据写入 Closure 段
- [ ] cra-index + roadmap 在案
- [ ] 双门禁 EXIT=0

## Closure Gates

- [ ] 17 工单全部收口（含 P1 C6-001）
- [ ] 每工单 ≥1 可被回归捕获的载体
- [ ] `./mvnw test -pl nop-kernel` BUILD SUCCESS（clean）
- [ ] 设计 §五 回归面可执行面逐条执行（PA 硬门禁）
- [ ] **不回退 2547 语义**（三条既有钉全绿）
- [ ] owner doc 落账完成
- [ ] scan-hollow nop-kernel `--severity high` EXIT=0
- [ ] 不存在被静默降级的 in-scope live defect
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] **Anti-Hollow Check**
- [ ] `check-plan-checklist --strict` EXIT=0；`check-doc-links --strict` EXIT=0
- [ ] cra-index P2-G27 收敛注 + roadmap 8.54 → done

## Deferred But Adjudicated

（Phase 1 review 后补）

## Non-Blocking Follow-ups

（Phase 1 review 后补）

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
