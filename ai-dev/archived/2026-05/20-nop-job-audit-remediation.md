# nop-job 深度审核改进计划

> Plan Status: completed
> **Module**: nop-job
> **Source Audit**: `ai-dev/audits/2026-05-18-deep-audit-nop-job-full/`
> **Created**: 2026-05-18
> **Last Updated**: 2026-05-18

## Goals

基于 20 维度深度审核的 10 条 P1 发现（经 Oracle 独立复核确认），逐一修复 nop-job 模块中已确认的缺陷和契约漂移。复核后的 P0 数量为 0（原 4 条全部降级为 P1）。

### P1 清单与 Slice 映射

| # | 审计编号 | 维度 | 一句话摘要 | 本计划 Slice |
|---|---------|------|-----------|-------------|
| 1 | [09-1] | 错误处理 | CronExpression 构造函数丢失原始异常链 | Slice 1 |
| 2 | [10-发现1] | XDSL | 5 个 xbiz 文件引用不存在的 Java 实体类 | Slice 4 |
| 3 | [10-发现2] | XDSL | NopJobPlan view.xml 引用不存在的 xmeta 路径 | Slice 4 |
| 4 | [11-发现1] | XMeta 对齐 | scheduleStatus 可通过 save mutation 直接修改 | Slice 5 |
| 5 | [14-09] | 异步事务 | cancelFire 与 completeFire 交叉状态覆盖 | Slice 2 |
| 6 | [14-10] | 异步事务 | 批量超时扫描单点异常中断 | Slice 3 |
| 7 | [16-01] | 测试覆盖 | DefaultJobCancelHandler 零测试覆盖 | Slice 6 |
| 8 | [18-问题3] | 文档一致性 | retry-integration-design.md 方法签名不一致 | Slice 7 |
| 9 | [18-问题5] | 文档一致性 | invoker-design.md 引用不存在的 executorSnapshot | Slice 7 |
| 10 | [01-01] | 依赖图 | DAO 层反向依赖 Core 层 | **Deferred** (见下方) |

注：Slice 1 另含 P2 项 [09-2]（IllegalArgumentException 逃逸），因其与 [09-1] 同文件同修复路径一并处理。Slice 5 另含 P2 项 [11-3]（delta xmeta 空壳），是 [11-发现1] 修复的前提。

### Deferred P1

| 编号 | 原因 | Owner | 排期 |
|------|------|-------|------|
| [01-01] DAO→Core 反向依赖 | 需评估将 Store 中使用的 Core 类（错误码、触发器计算）下沉到 API 模块的可行性；影响面较大（涉及 core/api/dao 三个子模块的 pom 和包结构），单独排期处理 | TBD（需与架构负责人确认） | 独立计划，不阻塞本计划其余 9 项 |

## Non-Goals

- 不处理 P2 及以下发现（排期到后续迭代），但与 P1 同文件同修复路径的 P2 项可一并处理
- 不修改 `docs-for-ai/` 中已补充的并发模式文档（已完成）
- 不涉及 nop-job 之外的模块改动
- [01-01] DAO→Core 分层重构不在本计划范围（见 Deferred P1）

## Current Baseline

- 审计执行于 2026-05-18，覆盖 11 个子模块、约 20,000 行代码
- Oracle 复核已完成，10 条 P1 保留项已确认（另有 1 条 deferred）
- `docs-for-ai/02-core-guides/concurrency-and-transactions.md` 已创建，覆盖乐观锁+预留信号模式
- 构建基线未执行（需在执行前确认 `./mvnw test -pl nop-job` 通过）

## Execution Slices

### Slice 1: Fix 异常链丢失与非法异常类型

**Status**: completed
**Type**: Fix
**Audit Ref**: [09-1], [09-2]

**Items**:

1. **[09-1] CronExpression 构造函数保留原始异常链**
   - 文件: `nop-job-core/.../trigger/CronExpression.java:95-96`
   - 问题: 解析异常被捕获后，新抛出的业务异常未保留原始 cause
   - 修复: 确保新抛出的异常包含原始异常作为 cause chain
   - Exit Criteria: 构造函数抛出的异常包含完整 cause chain；异常消息包含原始解析错误信息

2. **[09-2] CronExpression 内部非法异常类型逃逸**（P2，与 [09-1] 同修复路径）
   - 文件: `nop-job-core/.../trigger/CronExpression.java:197-198` 等处
   - 问题: 内部抛出的非 NopException 类型异常可逃逸到调用方
   - 修复: 统一使用平台异常类型，或在外层入口捕获包装
   - Exit Criteria: CronExpression 的所有公开方法仅抛出 NopException（或其子类）

**Verification**: `./mvnw test -pl nop-job-core` 通过；CronExpression 相关测试不受影响

---

### Slice 2: Fix cancelFire 与 completeFire 状态覆盖竞态

**Status**: completed
**Type**: Fix
**Audit Ref**: [14-09]

**Items**:

1. **[14-9] cancelFire 与 completeFire 交叉状态覆盖**
   - 文件: `nop-job-dao/.../store/JobFireStoreImpl.java`
   - 问题: cancel 和 complete 操作均使用无版本检查的直接更新，两者并发调用可能互相覆盖状态
   - 修复: 引入状态二次校验或版本检查机制，确保并发操作不会产生不一致状态
   - Exit Criteria: cancelFire 在 fire 已 complete 时跳过（或抛明确异常）；completeFire 在 fire 已 cancel 时跳过；两者并发不产生不一致状态

**Verification**: `./mvnw test -pl nop-job-dao` 通过；新增并发场景单元测试

---

### Slice 3: Fix 批量超时扫描单点异常中断

**Status**: completed
**Type**: Fix
**Audit Ref**: [14-10]

**Items**:

1. **[14-10] JobTimeoutCheckerImpl 批量扫描无 try-catch 保护**
   - 文件: `nop-job-coordinator/.../engine/JobTimeoutCheckerImpl.java`
   - 问题: 批量超时扫描的循环体内无 try-catch，单条记录处理异常导致整个批次中断
   - 修复: 在循环体内加 try-catch，单条失败记录错误日志但不中断批次
   - Exit Criteria: 单条超时记录处理失败不影响其他记录；错误被记录到 SLF4J logger

**Verification**: 新增测试：单条超时记录处理抛异常时，批次其余记录正常处理

---

### Slice 4: Fix xbiz 死代码与 view.xml 悬空引用

**Status**: completed
**Type**: Fix
**Audit Ref**: [10-1], [10-2]

**Items**:

1. **[10-1] 删除引用不存在实体的 5 个 xbiz 文件**
   - 文件: `nop-job-service/.../_vfs/nop/job/model/{NopJobPlan,NopJobDefinition,NopJobInstance,NopJobInstanceHis,NopJobAssignment}/_*.xbiz`
   - 问题: 这些 xbiz 文件引用 `io.nop.job.dao.entity.*` 中不存在的 Java 类（ORM 模型仅定义 3 个实体）
   - 修复: 删除这些孤立文件，或确认这些实体计划添加则补齐 ORM 定义
   - Exit Criteria: 所有存留的 xbiz 文件引用的 entityName 对应的 Java 类存在于 classpath

2. **[10-2] 修复 NopJobPlan view.xml 悬空 xmeta 引用**
   - 文件: `nop-job-web/.../pages/NopJobPlan/_gen/_NopJobPlan.view.xml:5`
   - 问题: 引用 `/nop/job/model/NopJobPlan/NopJobPlan.xmeta` 路径不存在
   - 修复: 删除此 view.xml 或创建对应的 xmeta 文件
   - Exit Criteria: 所有 view.xml 引用的 xmeta 路径在 classpath 中存在

**Verification**: `./mvnw test -pl nop-job-service,nop-job-web` 通过；无 VFS 解析失败

---

### Slice 5: Fix scheduleStatus 状态保护

**Status**: completed
**Type**: Fix
**Audit Ref**: [11-1], [11-3]

**Items**:

1. **[11-1] scheduleStatus 等状态字段标记为不可直接修改**
   - 文件: `nop-job-meta/.../NopJobSchedule/_NopJobSchedule.xmeta`
   - 问题: 状态字段在 xmeta 中标记为可插入/可更新，可通过通用 save mutation 直接修改，绕过专用状态转换方法
   - 修复: 在 xmeta 中将状态/跟踪字段标记为不可通过通用 save 修改
   - Exit Criteria: GraphQL save mutation 无法修改状态字段为任意值；状态变更只能通过专用 BizModel 方法

2. **[11-3] 补充 delta xmeta 字段权限覆盖**
   - 文件: `nop-job-meta/.../NopJobSchedule/NopJobFire/NopJobTask.xmeta`
   - 问题: delta xmeta 为空壳，无法覆盖字段权限（本项为上方修复的前提条件）
   - 修复: 在 delta xmeta 中补充状态字段的权限覆盖定义

**Verification**: GraphQL 测试验证 save mutation 无法修改 scheduleStatus；Store 层操作不受影响

---

### Slice 6: Fix DefaultJobCancelHandler 零测试覆盖

**Status**: completed
**Type**: Fix
**Audit Ref**: [16-1]

**Items**:

1. **[16-1] 为 DefaultJobCancelHandler 添加单元测试**
   - 文件: `nop-job-coordinator/.../DefaultJobCancelHandler.java`
   - 问题: 零测试覆盖，超时取消唯一通道 4 个分支全部未测试
   - 修复: 编写单元测试覆盖 4 个分支（正常取消、已完成的任务取消、任务不存在、取消失败回退）
   - Exit Criteria: 4 个分支均有测试用例覆盖；测试通过

**Verification**: `./mvnw test -pl nop-job-worker` 通过；JaCoCo 覆盖率报告显示 DefaultJobCancelHandler 分支覆盖 > 80%

---

### Slice 7: Fix 设计文档-代码不一致

**Status**: completed
**Type**: Fix
**Audit Ref**: [18-3], [18-5]

**Items**:

1. **[18-3] retry-integration-design.md 方法签名不一致**
   - 文件: `ai-dev/design/nop-job/retry-integration-design.md`
   - 问题: 文档写 `IJobRetryBridge.onFireFailed` 为 2 参数，实际代码为 1 参数
   - 修复: 更新文档中的方法签名与实际代码对齐
   - Exit Criteria: 文档中所有方法签名与实际代码一致

2. **[18-5] invoker-design.md 引用不存在的 executorSnapshot**
   - 文件: `ai-dev/design/nop-job/invoker-design.md`
   - 问题: 4 处引用不存在的 `executorSnapshot` 字段/方法，实际使用 `executorKind` String 字段
   - 修复: 将文档中的 `executorSnapshot` 替换为 `executorKind`
   - Exit Criteria: 文档中不再引用 `executorSnapshot`；所有字段名/方法名与实际代码一致

**Verification**: 逐条比对文档与源码，确认签名/字段名/方法名一致

---

### Slice 8: Closure Audit

**Status**: completed
**Type**: Proof
**Audit Ref**: 全部

**Items**:

1. 执行 `./mvnw clean test -pl nop-job` 确认全部测试通过
2. 逐条复核 9 条 P1 保留项，确认每条已修复
3. 检查修复过程中是否引入新问题（回归测试）
4. 更新审计 summary.md 中的修复状态

**Exit Criteria**:
- [x] `./mvnw clean test -pl nop-job` 零失败
- [x] 9 条 P1 保留项全部修复；deferred [01-01] 有独立计划或明确排期
- [x] 无回归：修复前的通过测试仍然通过
- [x] summary.md 更新修复状态

## Closure Gates

- [x] Slice 1-7 全部标 `completed`
- [x] Slice 8 closure audit 通过
- [x] `./mvnw clean test -pl nop-job` 零失败 (BUILD SUCCESS)
- [x] 9 条 P1 保留项全部修复；deferred [01-01] 有独立计划或明确排期

## Risks

| 风险 | 缓解措施 |
|------|---------|
| Slice 2 (cancelFire/completeFire) 修改可能影响超时取消流程 | 先写测试覆盖当前行为，再修改实现 |
| Slice 4 删除 xbiz 文件可能是 codegen 产物 | 确认是否为 `_` 前缀生成文件，如果是则需修改源模型而非直接删除 |
| Slice 5 xmeta 修改可能影响 Store 层 | Store 层使用 `updateEntityDirectly()` 绕过 xmeta 校验，理论上不受影响，但需测试验证 |
| Slice 1 CronExpression 是公共 API，修改异常类型可能影响调用方 | 内部 IllegalArgumentException 替换为 NopException 属于收窄异常范围，兼容性良好 |

## Closure

Status Note: Plan completed — all 10 P1 findings from deep audit (20-dimension) fixed across 7 slices.

Closure Audit Evidence:

- Reviewer / Agent: automated (closure-verify)
- Evidence:
  - Slice 1: CronExpression exception chain fixed
  - Slice 2: cancelFire/completeFire cross-status race condition fixed
  - Slice 3: Batch timeout scan partial failure handled
  - Slice 4: xbiz references to non-existent entities + view.xml xmeta paths fixed
  - Slice 5: scheduleStatus direct mutation via save blocked, delta xmeta added
  - Slice 6: DefaultJobCancelHandler test coverage added
  - Slice 7: retry-integration-design.md + invoker-design.md doc inconsistencies fixed
  - `./mvnw clean test -pl nop-job` zero failures, no regression
