# 325 nop-batch 分区配置 IntRangeSet 化与 PartitionResolver 抽取

> Plan Status: completed
> Last Reviewed: 2026-08-04
> Source: 用户需求（BatchTaskContext 分区配置 IntRangeBean → IntRangeSet；JobPartitionResolver 公共部分抽取到 cluster）+ 前置分析

## Purpose

把批处理任务的"分区"概念从单区间 `IntRangeBean` 统一为多区间 `IntRangeSet`（多 worker 各拿一组不连续区间），并消除 nop-job / nop-retry 中重复的 cluster 分区解析逻辑。

## Current Baseline

- `IBatchTaskContext.getPartitionRange()/setPartitionRange()` 使用 `IntRangeBean`（单区间），见 `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchTaskContext.java:97-99`；`setPartitionRange` 当前无生产调用者（由外部 worker/派发注入）
- 两个 reader 消费 `getPartitionRange()`：`JdbcBatchLoaderProvider.java:199-204`、`OrmQueryBatchLoaderProvider.java:109-114`，均手动 `FilterBeans.between(...)`
- `QueryBean.addPartitionFilter(QueryBean query, IntRangeSet partitions, String partitionProp)` 签名怪异（实例方法却传 query 参数），`QueryBean.java:546`；唯一直接调用者为 `JobQueryHelper.java:24-26`（其静态包装被 nop-job-dao 7 处调用，签名保持不变即可）
- `JobPartitionResolver`（nop-job-coordinator）与 `RetryScannerImpl.resolvePartitions()`（nop-retry-engine `RetryScannerImpl.java:173-202`）逻辑重复：assignedPartitions 配置优先 → namingService.getInstances → 按 instanceId 排序 → `PartitionAssignHelper.getMyRange` → `toRangeSet`
- `NopJobTask.partitionRange` 为 ORM String 字段（`IntRangeBean.toString()` 格式 "offset,limit"），持久化格式不动
- `PartitionTaskBuilder.PARTITION_HASH_RANGE = intRange(0, Short.MAX_VALUE+1)`（AR-98 off-by-one 修复常量，注释禁止修改）

## Goals

- `IBatchTaskContext` 分区配置改为 `IntRangeSet`
- 两个 reader 复用 `QueryBean.addPartitionFilter`
- `QueryBean.addPartitionFilter` 修正为纯实例方法（不传 query）
- `nop-cluster-core` 新增通用 `PartitionResolver`；`JobPartitionResolver` 退化为缺省配置子类；`RetryScannerImpl` 组合委托

## Non-Goals

- 不改 `NopJobTask.partitionRange` String 持久化格式与 `PartitionTaskBuilder`/`Assignment` 的 String 字段
- 不改 `PartitionTaskBuilder.PARTITION_HASH_RANGE` 常量
- 不改 coordinator 4 个 scanner 对 `JobPartitionResolver` 的引用结构（类名/构造/方法保持兼容）

## Scope

### In Scope

- `QueryBean.addPartitionFilter` 签名修正 + `JobQueryHelper` 适配
- `IBatchTaskContext` / `BatchTaskContextImpl` 分区类型改 `IntRangeSet`
- `JdbcBatchLoaderProvider` / `OrmQueryBatchLoaderProvider` 改用 `addPartitionFilter`
- `nop-cluster-core` 新增 `io.nop.cluster.naming.PartitionResolver` + 单测
- `JobPartitionResolver` 继承改造 + `RetryScannerImpl` 委托改造
- 相关 docs-for-ai / ai-dev 文档与日志

### Out Of Scope

- NopJobTask ORM 模型字段变更
- 分区分配算法（WeightedPartitionAssigner）变更
- coordinator scanner 内部调度语义调整

## Execution Plan

### Phase 1 - nop-api-core: QueryBean.addPartitionFilter 修正

Status: completed
Targets: `nop-kernel/nop-api-core/.../query/QueryBean.java`, `nop-job/nop-job-dao/.../helper/JobQueryHelper.java`

- Item Types: `Fix`

- [x] `QueryBean.addPartitionFilter` 改为实例方法 `addPartitionFilter(IntRangeSet partitions, String partitionProp)`，行为不变（单区间→between；多区间→OR），内部复用 `FilterBeans.inRanges`
- [x] `JobQueryHelper.addPartitionFilter` 静态包装适配新签名（对外签名不变）
- [x] 新增 `QueryBean.addPartitionFilter` 单测（单区间/多区间/空 set/已有 filter 追加）

Exit Criteria:

- [x] `QueryBean.addPartitionFilter(partitions, prop)` 无 query 参数；`JobQueryHelper` 7 个调用点不变仍可编译
- [x] 新增单测验证三种分支（5 case 全绿）
- [x] `./mvnw test -pl nop-api-core` 通过
- [x] No owner-doc update required（行为不变，仅签名修正；文档同步在 Phase 5 统一裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 5）

### Phase 2 - nop-batch: 分区配置 IntRangeSet 化

Status: completed
Targets: `IBatchTaskContext.java`, `BatchTaskContextImpl.java`, `JdbcBatchLoaderProvider.java`, `OrmQueryBatchLoaderProvider.java`

- Item Types: `Fix`

- [x] `IBatchTaskContext`/`BatchTaskContextImpl` 的 `getPartitionRange()/setPartitionRange()` 类型改为 `IntRangeSet`
- [x] `JdbcBatchLoaderProvider.newState`：`IntRangeSet partitions = context.getPartitionRange()`，`partitionIndexField != null && partitions != null` 时 `query.addPartitionFilter(partitions, partitionIndexField)`
- [x] `OrmQueryBatchLoaderProvider.newLoaderState`：同款改造

Exit Criteria:

- [x] 批处理侧 grep 无残留 `IntRangeBean range = context.getPartitionRange()` 用法
- [x] 两 reader 均调用 `QueryBean.addPartitionFilter(IntRangeSet, String)`
- [x] `./mvnw test -pl nop-batch-core,nop-batch-jdbc,nop-batch-orm -am` 通过
- [x] No owner-doc update required（Phase 5 统一裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 5）

### Phase 3 - nop-cluster-core: PartitionResolver 抽取

Status: completed
Targets: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/PartitionResolver.java`, 新增测试

- Item Types: `Fix`

- [x] 新增 `io.nop.cluster.naming.PartitionResolver`：字段 `namingService/serviceName/enableCluster/assignedPartitions` + 10s 缓存；`resolvePartitions()` 语义 = 原 JobPartitionResolver 逻辑
- [x] 新增 `TestPartitionResolver`：静态配置优先、cluster 关闭→null、无 namingService→null、单实例全区间、多实例分区、实例未找到→null、TTL 缓存（10 case）

Exit Criteria:

- [x] `PartitionResolver.resolvePartitions()` 覆盖 JobPartitionResolver 全部现有分支
- [x] 新增单测覆盖上述 7 类分支（实际 10 case，全绿）
- [x] `./mvnw test -pl nop-cluster-core` 通过
- [x] No owner-doc update required（Phase 5 统一裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 5）

### Phase 4 - nop-job / nop-retry 适配

Status: completed
Targets: `JobPartitionResolver.java`, `RetryScannerImpl.java`, `app-engine.beans.xml`

- Item Types: `Fix`

- [x] `JobPartitionResolver extends PartitionResolver`，仅保留 `@InjectValue("@cfg:nop.job.cluster.*")` 缺省配置 setter
- [x] `RetryScannerImpl` 组合内部 `PartitionResolver`，转发 `setNamingService/setServiceName/setEnableCluster/setAssignedPartitions`（`@InjectValue("@cfg:nop.retry.scanner.*")` 保留），`resolvePartitions()` 委托
- [x] `TestJobPartitionResolver` 不修改仍全部通过（继承后 API 兼容，10/10）

Exit Criteria:

- [x] `JobPartitionResolver` 源码中无重复的 resolvePartitions 逻辑（grep 确认）
- [x] `RetryScannerImpl` 无私有 resolvePartitions 复制逻辑（仅一行委托）
- [x] beans.xml 无需改动；`./mvnw test -pl nop-job-coordinator,nop-retry-engine` 通过（162+9 全绿）
- [x] No owner-doc update required（Phase 5 统一裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 5）

### Phase 5 - 验证、文档与收口

Status: completed
Targets: `docs-for-ai/`, `ai-dev/logs/`

- Item Types: `Fix | Proof`

- [x] 全量受影响模块 `./mvnw test` 通过（nop-api-core/batch-*/cluster-core/job-coordinator/job-dao/retry-engine）
- [x] 检查并同步 `docs-for-ai`（`batch.xdef` 的 `@partitionIndexField` 注释更新为多区间 OR；`nop-job.md` NopJobTask.partitionRange 为 String 字段不变无需改）
- [x] `ai-dev/design/nop-sys/sys-event-architecture.md` §3.4.1 loader 过滤约定同步为 `addPartitionFilter`（多区间 OR）
- [x] `ai-dev/logs/2026/08-04.md` 记录本次改动事实与验证结果
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动未引入任何新断链（6 条历史断链均为既存 app-erp 示例引用，与本改动无关）

Exit Criteria:

- [x] 构建与测试全绿（见上）
- [x] owner doc 已同步（`batch.xdef` + `sys-event-architecture.md`）
- [x] 日志已更新
- [x] doc link checker：本次改动无新增断链

## Closure Gates

- [x] 所有 Phase 的 Exit Criteria 已勾选且 Phase Status 为 completed
- [x] 无 in-scope live defect 被降级为 deferred/follow-up
- [x] `JobPartitionResolver` 与 `RetryScannerImpl` 的重复逻辑已收敛到 `PartitionResolver`
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证 PartitionResolver 被 JobPartitionResolver/RetryScannerImpl 运行时调用（非仅类型存在）
- [x] `./mvnw compile` 相关模块通过
- [x] `./mvnw test` 相关模块通过

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 无。

## Closure

Status Note: 批处理分区配置已统一为 IntRangeSet（多区间），QueryBean.addPartitionFilter 修正为纯实例方法，nop-job/nop-retry 的重复 cluster 分区解析逻辑已收敛到 nop-cluster-core 的 PartitionResolver。所有受影响模块测试全绿，独立 closure audit 通过且无空壳发现。
Completed: 2026-08-04

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（general, task ses_035293f50ffe3VizcYYxXoIReD，fresh session）
- Audit Session: ses_035293f50ffe3VizcYYxXoIReD
- Evidence:
  - Phase 1 PASS：`QueryBean.java:549` 实例方法 `addPartitionFilter(IntRangeSet, String)` 无 query 参数，`:553` 委托 `FilterBeans.inRanges`；`JobQueryHelper.java:25` 调用新签名；`TestQueryBeanPartitionFilter.java` 5 case
  - Phase 2 PASS：`IBatchTaskContext.java:97-99` / `BatchTaskContextImpl.java:47,192-199` 均为 IntRangeSet；`JdbcBatchLoaderProvider.java:198-203` 与 `OrmQueryBatchLoaderProvider.java:108-113` 调用 addPartitionFilter；nop-batch 无残留 IntRangeBean 用法
  - Phase 3 PASS：`PartitionResolver.java` 含 4 字段 + `CACHE_TTL_MS=10_000` + 完整 resolvePartitions 算法；`TestPartitionResolver.java` 10 case
  - Phase 4 PASS：`JobPartitionResolver.java:11 extends PartitionResolver`，无 resolvePartitions body；`RetryScannerImpl.java:34` 组合 + 4 setter 转发 + `:161-163` 一行委托；无残留禁用 import
  - Anti-Hollow PASS：4 个 coordinator scanner（JobDispatcherScannerImpl:145 / JobPlannerScannerImpl:106 / JobCompletionProcessorImpl:121 / JobTimeoutCheckerImpl:146）运行时调用 `partitionResolver.resolvePartitions()`；beans.xml:22,40,51,58 注入；RetryScannerImpl.doScan:125 调用 resolvePartitions；PartitionResolver 同时被继承与组合引用，非死类
  - Docs PASS：`batch.xdef:123` 多区间 OR 注释；`sys-event-architecture.md:296` addPartitionFilter
  - 无空壳实现：JobPartitionResolver 故意薄（仅绑配置）但继承的 resolvePartitions 被 4 scanner 运行时调用；RetryScannerImpl 一行委托到真实实现

Follow-up:

- no remaining plan-owned work
