# 109 nop-job Deep Audit Security & Quality Remediation

> Plan Status: completed
> Last Reviewed: 2026-06-04
> Reviewed By: Independent sub-agent adversarial review (ses_170c87ab0ffesY83cYxj77dZfN) — Not Ready → fixed 3 blocking issues (wrong auth mechanism, wrong method names, underspecified retry refactor)
> Source: `ai-dev/audits/2026-06-03-deep-audit-nop-job/summary.md` (21-dimension deep audit: 6 unfixed P2 in uncovered dimensions), `ai-dev/audits/2026-06-03-adversarial-review-nop-job/summary.md` (6 rounds, 47 findings)
> Related: `ai-dev/plans/108-nop-job-remaining-r6-r7-audit-findings-remediation.md` (completed), `ai-dev/plans/107-nop-job-round7-and-deep-audit-remediation.md` (completed, covered 7 of 21 deep-audit dimensions)

## Purpose

修复 2026-06-03 深度审计 21 维度中 Plan 107 未覆盖的 14 个维度里仍然存活的 4 项 P2 发现，将 nop-job 模块的权限模型和数据完整性保护补齐。

## Current Baseline

- Plan 107 已完成（覆盖维度 01, 04, 05, 07, 09, 11, 16 的 P2，共 12 项）
- Plan 108 已完成（覆盖 R6/R7 剩余 P2 + 高价值 P3，共 12 项）
- 对抗性审查 7 轮累计 53 项发现中 47+ 已修复，剩余 5 项 P3 为 watch-only
- 深度审计 21 维度中 Plan 107 覆盖了 7 维度，剩余 14 维度中 6 维度无发现、4 维度仅 P3、4 维度有 P2
- `./mvnw clean test -pl nop-job -am` 当前基线为 BUILD SUCCESS
- **Dim14-01 已在 Plan 107 AR-51 中修复**（retry 耗尽后抛异常而非 fallback）
- **Dim20-01/AR-52 已在 Plan 108 中确认状态**（retryRecordId 为跨系统已知限制，ORM 注释已更新）

### 仍然存活的未修复 P2 项（4 项）

| ID | 描述 | 深度审计维度 | 修复复杂度 |
|----|------|------------|-----------|
| Dim13-01 | NopJobScheduleBizModel 6 个 + NopJobFireBizModel 2 个自定义方法无权限控制 — 任何认证用户可执行全部调度操作 | 13 安全与权限 | 低 |
| Dim13-02 | nop-job.data-auth.xml 完全为空 — 多租户场景无数据隔离 | 13 安全与权限 | 低 |
| Dim11-02 | NopJobTask.xmeta 缺少 jobFireId/shardingIndex/shardingTotal 的 updatable=false 保护 | 11 XMeta 对齐 | 低 |
| Dim02-03 | JobScheduleStoreImpl 乐观锁重试模式重复 7 次 — 字段恢复逻辑分散 | 02 模块职责 | 中 |

## Goals

- 修复全部 4 项 in-scope P2 发现
- 所有代码修复有对应测试覆盖（如适用）
- 权限模型补齐：BizModel 方法级权限 + 数据级隔离规则

## Non-Goals

- 不修复已确认 P3 项（AR-7, AR-14, AR-15, AR-16, AR-32 等 watch-only residual）
- 不修复 Dim14-02（overlayFireAndAdvanceSchedule 长事务风险 — 架构级，需独立评估）
- 不修复 Dim20-01/AR-52（retryRecordId 跨系统追踪 — 已确认状态，需 nop-retry 接口重新设计）
- 不修复深度审计 P3 项（代码风格、命名、类型安全等）
- 不重新设计调度器架构、ORM 模型结构或跨模块 API

## Scope

### In Scope

- Dim13-01 (P2): 为 NopJobScheduleBizModel 和 NopJobFireBizModel 的自定义方法添加 xbiz `<auth>` 权限规则
- Dim13-02 (P2): 为 nop-job.data-auth.xml 添加基于 namespaceId 的数据权限规则
- Dim11-02 (P2): 在 NopJobTask delta xmeta 中为 jobFireId、shardingIndex、shardingTotal 添加 updatable=false
- Dim02-03 (P2): 将 JobScheduleStoreImpl 的 7 处乐观锁重试模式提取为共享方法

### Out Of Scope

- Dim14-02 (P2): overlayFireAndAdvanceSchedule 长事务风险（架构级）
- Dim20-01/AR-52 (P2): retryRecordId 跨系统问题（需独立设计）
- 对抗性审查所有 deferred P3 项
- 深度审计所有 P3 项

## Execution Plan

### Phase 1 - 权限模型补齐（Dim13-01, Dim13-02）

Status: completed
Targets: `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobSchedule/NopJobSchedule.xbiz`（delta xbiz 文件）, `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobFire/NopJobFire.xbiz`（delta xbiz 文件）, `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/auth/nop-job.data-auth.xml`

- Item Types: `Fix`

- [x] **Dim13-01 (P2)**: 在 delta xbiz 文件中为以下 8 个公开自定义方法添加 `<auth>` 权限规则。Nop 平台的权限模型使用 xbiz XML 中的 `<auth>` 元素（非 Java 注解）。
  - `NopJobScheduleBizModel`（6 个方法）：enableSchedule, disableSchedule, pauseSchedule, resumeSchedule, archiveSchedule, triggerNow
  - `NopJobFireBizModel`（2 个方法）：rerunFire, cancelFire
  - 实现方式：在对应 delta xbiz 文件的 `<xbiz>` 根元素下为每个 mutation 添加 `<mutation name="methodName"><auth roles="admin" permission="NopJobSchedule:methodName"/></mutation>` 或等效模式。参考 `nop-auth` 模块的 delta xbiz 文件中的 `<auth>` 用法模式（如 `NopAuthSite.xbiz` 使用 `<mutation name="update"><auth roles="user" /></mutation>`）。
- [x] **Dim13-02 (P2)**: 在 `nop-job.data-auth.xml` 中添加基于 `namespaceId` 的数据权限规则。至少配置：用户只能查看/操作自己 namespaceId 下的 schedule 和 fire 记录。参考 `nop-auth` 模块的 `nop-auth.data-auth.xml` 作为模板。

Exit Criteria:

- [x] `NopJobSchedule.xbiz` delta 文件为 6 个自定义方法有 `<auth>` 权限规则
- [x] `NopJobFire.xbiz` delta 文件为 2 个自定义方法有 `<auth>` 权限规则
- [x] `nop-job.data-auth.xml` 有基于 namespaceId 的数据隔离规则
- [x] 添加测试验证权限检查生效（至少 1 个方法级 + 1 个数据级测试，或确认 xbiz auth 规则被框架正确解析）
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] 若权限模型影响 API 行为文档，更新 `docs-for-ai/02-core-guides/api-and-graphql.md`；否则写 No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 数据完整性保护（Dim11-02）

Status: completed
Targets: `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobTask/NopJobTask.xmeta`

- Item Types: `Fix`

- [x] **Dim11-02 (P2)**: 在 `NopJobTask.xmeta` delta 文件中为 `jobFireId`、`shardingIndex`、`shardingTotal` 三个字段添加 `updatable="false"` 属性。这三个字段在 task 创建后不应被 API 修改。**禁止编辑** `_NopJobTask.xmeta`（生成文件）。

Exit Criteria:

- [x] `NopJobTask.xmeta` delta 文件包含 jobFireId、shardingIndex、shardingTotal 的 updatable=false 声明
- [x] 验证方式：读取 xmeta 文件确认声明存在；若可行则通过 GraphQL update mutation 验证字段不可修改
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required（xmeta 字段保护为内部安全约束）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 代码质量：重试模式去重（Dim02-03）

Status: completed
Targets: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java`

- Item Types: `Fix`

- [x] **Dim02-03 (P2)**: 提取 `JobScheduleStoreImpl` 中 7 处重复的乐观锁重试 + 异常抛出模式为共享方法。
  - 7 处重试块的共同模式：5 次重试 → 每次失败后从 DB 重新加载 schedule → 重新计算修改字段 → `tryUpdateManyWithVersionCheck` → 耗尽后抛 `NopException(ERR_JOB_FIRE_STATUS_CONFLICT)`
  - 差异点：每次重试失败后需要恢复/重算的字段不同（如 fireCount+activeFireCount vs totalFireCount+failFireCount vs 所有五个计数器）
  - 建议方法签名：`updateScheduleWithVersionRetry(NopJobSchedule schedule, BiConsumer<NopJobSchedule, NopJobSchedule> fieldRestorer)`，其中 `fieldRestorer` 接受（staleSchedule, freshSchedule）参数，负责将 freshSchedule 的最新字段值合并到 staleSchedule 上。或者分两步：先调用一个 `refreshScheduleFields(stale, fresh)` 模板方法恢复公共字段（version），再由调用方恢复特有字段。
  - **若提取统一方法会导致过度抽象（7 处恢复逻辑差异显著），允许保留为 2-3 个分组共享方法**，只要消除了逐字段的重复代码。

Exit Criteria:

- [x] `JobScheduleStoreImpl` 中乐观锁重试模式提取为 1-3 个共享方法（7 处调用共享），消除了逐字段的重复代码
- [x] 重试语义不变：5 次重试 + 版本刷新 + 异常抛出
- [x] `./mvnw test -pl nop-job -am` 全过（确保所有 7 个路径的行为不变）
- [x] No owner-doc update required（纯内部重构）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 4 项 in-scope P2 发现已修复
- [x] 权限规则（xbiz auth + data-auth.xml）已添加并有测试保护
- [x] NopJobTask 关键字段有 xmeta 保护
- [x] 乐观锁重试模式去重且行为不变
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] Anti-Hollow Check: closure audit 已验证（a）组件间调用链在运行时确实连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-job -am` 成功
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Dim14-02 overlayFireAndAdvanceSchedule 长事务风险

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 单事务内多表多行操作是当前架构的正常模式（REQUIRES_NEW 事务）。在高并发场景下可能产生锁竞争，但在当前部署规模下无用户可感知影响。优化需评估事务拆分策略，属架构级变更。
- Successor Required: yes
- Successor Path: 需独立计划评估事务拆分或批量化方案

### Dim20-01/AR-52 retryRecordId 跨系统追踪

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 跨系统问题，需重新设计 nop-retry 异步 API 回调机制。当前返回 null（不写入误导数据），ORM 注释已准确标注状态。
- Successor Required: yes
- Successor Path: 需要独立的 nop-retry 集成重新设计 plan

### 对抗性审查 Watch-Only P3 项

- Classification: `watch-only residual`
- Why Not Blocking Closure: 全部为 P3，7 轮审查确认无升级趋势。不影响数据完整性或状态机正确性。
- Successor Required: no
- Items: AR-7 (maxFailedCount), AR-14 (copyMap), AR-15 (findFirstErrorTask), AR-16 (taskPayload), AR-32 (时钟不一致)

### 深度审计 P3 项

- Classification: `optimization candidate`
- Why Not Blocking Closure: 全部为 P3 代码规范/可维护性优化，不影响运行时正确性
- Successor Required: no

## Non-Blocking Follow-ups

- 考虑为 `JobFireBizModel` 的其他方法也添加权限注解（如有新增）
- 考虑提取 3 套独立 Mock Store 为共享 mock 类（Dim16-03）
- 考虑将 Calendar 类的 `IllegalArgumentException` 替换为 `NopException`（Dim09-01）
- 考虑为 `.param()` 名提取 ARG_* 常量（Dim09-02）

## Closure

Status Note: All 4 in-scope P2 findings fixed and verified. Phase 1: auth rules for 8 BizModel methods + namespaceId data-auth. Phase 2: xmeta updatable=false for 3 fields. Phase 3: extracted shared updateScheduleWithRetry from 7 duplicate patterns. All nop-job tests pass. Independent closure audit confirmed no blocking issues.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (ses_170b69f3fffe2IteJd1vioEGha)
- Evidence:
  - Dim13-01 PASS: NopJobSchedule.xbiz has 6 mutation auth rules, NopJobFire.xbiz has 2 mutation auth rules, all matching auth.xdef schema (roles="admin" permissions="...")
  - Dim13-02 PASS: nop-job.data-auth.xml has 3 obj entries with namespaceId filter for user role
  - Dim11-02 PASS: NopJobTask.xmeta delta has updatable=false for jobFireId, shardingIndex, shardingTotal
  - Dim02-03 PASS: updateScheduleWithRetry called from all 7 locations, retry semantics preserved (5 retries + version refresh + exception)
  - `./mvnw test -pl nop-job -am` → BUILD SUCCESS
  - Anti-Hollow Check: no empty methods, no silent skips, no no-op implementations
  - Deferred items correctly classified (Dim14-02, Dim20-01/AR-52, P3 watch-only)
  - `node ai-dev/tools/check-plan-checklist.mjs` → exit code 0

Follow-up:

- Dim14-02 长事务优化（需独立计划）
- Dim20-01/AR-52 retryRecordId 跨系统重新设计（需独立计划）
- 对抗性审查 P3 watch-only 项（无 successor 计划）
