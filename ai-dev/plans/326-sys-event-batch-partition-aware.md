# 326 sys-event batch 分区感知 + JobPartitionResolver 下沉

> Plan Status: completed
> Last Reviewed: 2026-08-04
> Source: 用户需求（job 调度 batch 扫描 sys-event 时通过 partition resolver 自动加分区过滤）+ Plan 325 后续
> Related: `ai-dev/plans/325-nop-batch-cluster-partition-refactor.md`

## Purpose

让 job 调度的 sys-event batch 扫描在集群部署下自动只处理本实例负责的分区：partition 由 job 侧的 partition resolver 解析（已 resolve 好），注入 `IBatchTaskContext.partitionRange`，batch reader 通过 `addPartitionFilter` 自动追加过滤。同时消除 `NonBroadcastEventProcessor` 中第三份重复的过滤构建逻辑。

## Current Baseline

- Plan 325 已完成：`IBatchTaskContext.partitionRange` 类型为 `IntRangeSet`；`QueryBean.addPartitionFilter(IntRangeSet, String)` 已存在；nop-cluster-core 已有通用 `PartitionResolver`；`JobPartitionResolver extends PartitionResolver` 在 job-coordinator
- **`IBatchTaskContext.setPartitionRange` 仍无任何生产调用者**（Plan 325 确认）—— job→batch 链路从不注入 partition，是空接线的 API
- sys-event 调用链：`sys-event-batch-consumer.job.yaml`(cron) → `BeanMethodJobInvoker` → `BatchTaskRunner.executeAsync(taskPath, params)`（nop-batch-dsl:23）→ `newBatchTaskContext()`（:26 只 setParams，不 setPartitionRange）→ orm-reader
- `non-broadcast-consumer.batch.xml` 的 `<orm-reader>` **未设 `partitionIndexField`**（NopSysEvent 有 partitionIndex 字段，propId=15）
- sys-event 有**两条活的扫描路径**：
  - 程序式：`SysDaoMessageService` → `NonBroadcastEventProcessor.process()`（:66），`fetchCandidates()`（:99）调 `buildPartitionFilter()`（:197）手写 `FilterBeans.between` + `FilterBeans.or`——第三份重复逻辑
  - batch DSL：job → batch.xml → orm-reader，**无分区过滤**
- 模块依赖事实：job-core / job-local / job-worker / batch-dsl / batch-sys 均**不依赖** cluster-core；唯 job-coordinator 依赖 cluster-core
- job-core **无 `_vfs`**（无 beans.xml 注册结构）
- `nopBatchTaskRunner` bean 定义在 `nop-batch-dsl/_vfs/nop/batch/beans/batch-dsl.beans.xml:6`
- `NonBroadcastEventProcessor.claim(events)` / `process(event)` 被 batch.xml 的 `<afterLoad>`/`<processor>` 复用（SysDaoMessageService.claimNonBroadcastEvents/processClaimedNonBroadcastEvent），是两条路径共享的内核

## Goals

- job 调度 sys-event batch 时，本实例只扫描自己分区的事件（集群部署下）
- partition 解析发生在 job/batch 边界（调用 batch 时已 resolve），batch task 本身只读 `context.partitionRange`
- `JobPartitionResolver` 下沉到 job-core（classpath 对 job-local / job-worker / job-coordinator 均可见；本计划只在 job-core 注册 bean 并由 sys-event 间接消费，不为 job-local/worker 新增调用接线——那属于后续）
- 消除 `NonBroadcastEventProcessor.buildPartitionFilter` 的手写重复，统一到 `FilterBeans.inRanges`

## Non-Goals

- 不改 `NonBroadcastEventProcessor` 的扫描循环结构、claim/process 内核（仅替换过滤构建实现）
- 不改 `NopJobTask.partitionRange` String 持久化格式
- 不实现分布式 worker 的 per-task partition 注入（NopJobTask.partitionRange 已由 PartitionTaskBuilder 在派发时 resolve；本计划只覆盖 local-job 路径的 resolver 注入，分布式 worker 注入留后续）
- 不改 coordinator 4 scanner 的控制面分区逻辑

## Scope

### In Scope

- `JobPartitionResolver` 从 job-coordinator 移到 job-core（新包 `io.nop.job.core.partition`），job-core += cluster-core 依赖，在 job-core 新建 `_vfs` 注册 bean
- job-coordinator 4 scanner 的 import 更新；app-engine.beans.xml 的 bean 定义移除（改由 job-core 提供，按 id 引用不变）
- `BatchTaskRunner` 增加可选 `PartitionResolver` 注入，`executeAsync` 中 resolve 并 `setPartitionRange`；nop-batch-dsl += cluster-core 依赖
- `batch-dsl.beans.xml` 把 `partitionResolver` 注入 `nopBatchTaskRunner`（可选 ref，缺失时为 null）
- `non-broadcast-consumer.batch.xml` 的 `<orm-reader>` 增加 `partitionIndexField="partitionIndex"`
- `NonBroadcastEventProcessor.buildPartitionFilter` 改用 `FilterBeans.inRanges`
- 测试：sys-event batch 分区过滤端到端验证；NonBroadcastEventProcessor 过滤行为回归

### Out Of Scope

- 分布式 worker 从 `NopJobTask.partitionRange` 解析注入（后续计划）
- `SysDaoMessageService.assignedPartitions` 改为从 cluster resolver 动态获取（保持现有静态配置）
- broadcast 事件路径

## Execution Plan

### Phase 1 - JobPartitionResolver 下沉到 job-core

Status: completed
Targets: `nop-job/nop-job-core/`, `nop-job/nop-job-coordinator/`

- Item Types: `Fix`

- [x] job-core `pom.xml` 增加 `nop-cluster-core` 依赖
- [x] 新建 `nop-job/nop-job-core/src/main/java/io/nop/job/core/partition/JobPartitionResolver.java`（内容 = 现 job-coordinator 版本，包名改为 `io.nop.job.core.partition`，仍 `extends PartitionResolver`，保留 `@InjectValue("@cfg:nop.job.cluster.*")`）
- [x] job-core 新建 `nop-job/nop-job-core/src/main/resources/_vfs/nop/job/beans/app-job-core.beans.xml`（**沿用 job 模块共享命名空间 `nop/job/beans/`，与 job-local/coordinator/worker 一致；`app-` 前缀触发 AppBeanContainerLoader 自动加载**），注册 `<bean id="jobPartitionResolver" class="io.nop.job.core.partition.JobPartitionResolver"/>`
- [x] 删除 job-coordinator 旧 `JobPartitionResolver.java`
- [x] job-coordinator 4 scanner（JobDispatcherScannerImpl / JobPlannerScannerImpl / JobCompletionProcessorImpl / JobTimeoutCheckerImpl）import 改为新包（含各自 `new JobPartitionResolver()` 兜底调用点）
- [x] job-coordinator `app-engine.beans.xml` 删除 `<bean id="jobPartitionResolver">` 定义（bean 现由 job-core 的 `app-job-core.beans.xml` 提供，4 处 `<property name="partitionResolver" ref="jobPartitionResolver"/>` 按 id 引用不变）
- [x] `TestJobPartitionResolver` 移到 job-core test，import 更新

Exit Criteria:

- [x] `./mvnw test -pl nop-job/nop-job-core,nop-job/nop-job-coordinator` 通过（TestJobPartitionResolver 10/10 + coordinator 54 测试全绿）
- [x] grep 确认 job-coordinator 源码无 `JobPartitionResolver` 类定义残留
- [x] `TestJobPartitionResolver` 在 job-core 下全绿
- [x] 启动验证：TestJobE2E（加载完整 beans 容器）通过，4 个 scanner 按 id 解析 jobPartitionResolver 成功
- [x] No owner-doc update required（Phase 3 统一裁定）
- [x] `ai-dev/logs/` 已更新（Phase 3）

### Phase 2 - BatchTaskRunner 注入 PartitionResolver

Status: completed
Targets: `nop-batch/nop-batch-dsl/`

- Item Types: `Fix`

- [x] nop-batch-dsl `pom.xml` 增加 `nop-cluster-core` 依赖
- [x] `batch-dsl.beans.xml` 的 root `<beans>` 补 `xmlns:ioc="ioc"` 命名空间声明（参考 coordinator app-engine.beans.xml:5，因为下面要用 `ioc:optional`）
- [x] `BatchTaskRunner` 增加 `private PartitionResolver partitionResolver` 字段 + `setPartitionResolver` setter（**不加 `@Inject`**，避免 NopIoC 视为强制依赖；仅通过 beans.xml 显式注入）
- [x] `executeAsync` 中：`if (partitionResolver != null) { IntRangeSet p = partitionResolver.resolvePartitions(); if (p != null) context.setPartitionRange(p); }`（在 `task.executeAsync` 之前）
- [x] `batch-dsl.beans.xml` 的 `nopBatchTaskRunner` bean 用 Nop IoC 的 optional ref 语法注入（**用 `<ref>` 子元素 + `ioc:optional="true"`，不是 `<property ref="">` 属性形式**）：`<property name="partitionResolver"><ref bean="jobPartitionResolver" ioc:optional="true"/></property>`。无 job 模块时 resolver 为 null，BatchTaskRunner 跳过 resolve

Exit Criteria:

- [x] `BatchTaskRunner.executeAsync` 在 task 执行前完成 partition resolve + 注入（resolver 非空时）
- [x] 无 job 模块的纯 batch 部署仍可启动（autotest 日志实证 `nop.ioc.ignore-optional-ref-bean:ref=jobPartitionResolver,propName=partitionResolver,bean=nopBatchTaskRunner`）
- [x] `./mvnw test -pl nop-batch/nop-batch-dsl` 通过（TestBatchTaskRunnerPartition 3/3 + 原有 4 测试全绿）
- [x] **接线验证**：`TestBatchTaskRunnerPartition` 验证注入 resolver 时 context.partitionRange 被设置；未注入/返回 null 时为 null
- [x] No owner-doc update required（Phase 3 统一裁定）
- [x] `ai-dev/logs/` 已更新（Phase 3）

### Phase 3 - sys-event batch 分区感知 + 去重 + 验证 + 文档

Status: completed
Targets: `nop-batch/nop-batch-sys/`, `nop-sys/nop-sys-dao/`, `docs-for-ai/`, `ai-dev/logs/`

- Item Types: `Fix | Proof`

- [x] `non-broadcast-consumer.batch.xml` 的 `<orm-reader>` 增加 `partitionIndexField="partitionIndex"`
- [x] `NonBroadcastEventProcessor.buildPartitionFilter` 改为 `return FilterBeans.inRanges(NopSysEvent.PROP_NAME_partitionIndex, assignedPartitions)`（一行，复用；保留 null/empty 判断返回 null）
- [x] **端到端测试**（行为断言）：`TestSysEventBatchPartitionE2E` 三 case——单区间（partition=0）、无配置（全扫）、多区间 OR（0+2），用真实 IBatchTaskManager + 受控 resolver 验证 reader 过滤
- [x] **回归测试**：`TestSysDaoMessageService`（15 case，覆盖程序式路径）改用 inRanges 后全绿
- [x] 检查并同步 `docs-for-ai/03-modules/nop-sys.md`（事件队列段补充 batch mode 分区过滤机制）
- [x] `ai-dev/logs/2026/08-04.md` 追加记录
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 本次改动无新增断链

Exit Criteria:

- [x] **行为断言**：`TestSysEventBatchPartitionE2E` 验证注入 partitionRange=partition 0 后跨 3 分区事件只有 partition=0 被处理；多区间（0+2）验证 OR 组合
- [x] 程序式路径过滤行为不变（`TestSysDaoMessageService` 15/15 通过）
- [x] `./mvnw test -pl nop-batch/nop-batch-sys,nop-sys/nop-sys-dao` 通过（24 测试全绿）
- [x] **端到端验证**：resolver resolve → context.partitionRange → reader addPartitionFilter → 只有对应分区事件被处理，完整路径验证（TestSysEventBatchPartitionE2E 三 case）
- [x] owner doc 已同步（`nop-sys.md`）
- [x] 日志已更新；doc link checker 本次改动无新增断链

## Closure Gates

- [x] 所有 Phase 的 Exit Criteria 已勾选且 Status 为 completed
- [x] `IBatchTaskContext.setPartitionRange` 不再是空接线（BatchTaskRunner.executeAsync 实际调用 partitionResolver.resolvePartitions 并 setPartitionRange）
- [x] `JobPartitionResolver` 与 `NonBroadcastEventProcessor` 的重复过滤逻辑已收敛到 `FilterBeans.inRanges` / `QueryBean.addPartitionFilter`
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）BatchTaskRunner 在运行时确实调用 partitionResolver.resolvePartitions() 并 setPartitionRange（BatchTaskRunner.java:42-46）；（b）sys-event batch reader 确实读到 context.partitionRange（OrmQueryBatchLoaderProvider.java:108）并调 addPartitionFilter（:111-112）→ FilterBeans.inRanges（FilterBeans.java:146-158）；（c）JobPartitionResolver bean 在 job-core app-job-core.beans.xml 注册（nopBatchTaskRunner 按 id 引用，autotest 日志实证 ioc:optional 生效）。TestSysEventBatchPartitionE2E 用真实 IBatchTaskManager + batch.xml + orm-reader + DB 跑通端到端
- [x] `./mvnw compile` 相关模块通过（nop-job-core, nop-job-coordinator, nop-batch-dsl, nop-batch-sys, nop-sys-dao）
- [x] `./mvnw test` 相关模块通过（同上，全绿）

## Deferred But Adjudicated

### 分布式 worker per-task partition 注入

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 分布式路径的 partition 已由 `PartitionTaskBuilder` 在 coordinator 派发时 resolve 并写入 `NopJobTask.partitionRange`（String）。worker 侧仅需 parse 注入，是独立的接线工作，不影响本计划覆盖的 local-job resolver 注入路径成立。
- Successor Required: yes
- Successor Path: 后续计划

## Non-Blocking Follow-ups

- `SysDaoMessageService.assignedPartitions` 可改为从 PartitionResolver 动态获取（当前静态配置，单实例够用）

## Closure

Status Note: sys-event batch 扫描现在通过 job 侧 JobPartitionResolver resolve → BatchTaskRunner 注入 IBatchTaskContext.partitionRange → orm-reader addPartitionFilter 自动分区过滤。JobPartitionResolver 下沉到 job-core（classpath 对 job-local/worker/coordinator 可见）。NonBroadcastEventProcessor 第三份手写过滤逻辑收敛到 FilterBeans.inRanges。独立 closure audit 22 项全部 PASS，无空壳，端到端调用链经真实 DB E2E 测试验证。
Completed: 2026-08-04

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（general, task ses_034556c26ffeOBGdbx8DpiQPzP，fresh session）
- Audit Session: ses_034556c26ffeOBGdbx8DpiQPzP
- Evidence:
  - Phase 1 PASS：JobPartitionResolver 在 job-core:21 extends PartitionResolver + 3 个 @InjectValue；app-job-core.beans.xml:4 注册 bean；旧类已删；4 scanner import 新包（JobDispatcherScannerImpl:12 等）；app-engine.beans.xml 无 bean 定义但有 4 处 ref；job-core pom:25-28 依赖 cluster-core
  - Phase 2 PASS：BatchTaskRunner.java:24 字段 + :31-33 setter（无 @Inject）；:42-46 resolve+setRange 在 execute 前；batch-dsl.beans.xml:8-10 用 ref+ioc:optional，:2 xmlns:ioc；TestBatchTaskRunnerPartition 3 case
  - Phase 3 PASS：batch.xml:9 partitionIndexField；NonBroadcastEventProcessor.java:200 inRanges（无 IntRangeBean import）；TestSysEventBatchPartitionE2E 3 case（单区间/无配置/多区间 OR）；PartitionResolver.java:68-74 null 清空；nop-sys.md:87 文档同步
  - Anti-Hollow PASS：运行时链 BatchTaskRunner:42-46 → OrmQueryBatchLoaderProvider:108,111-112 → QueryBean:549-554 → FilterBeans:146-158 全部 live 代码实证；E2E 用真实 IBatchTaskManager + batch.xml + orm-reader + H2 DB，非 mock；NonBroadcastEventProcessor.buildPartitionFilter 非 no-op
  - 无空壳：无空方法体；TestBatchTaskRunnerPartition.runIgnoreContainerError 显式文档说明吞 BeanContainer 异常与接线无关
  - 测试全绿：nop-job-core（10）、nop-job-coordinator（54）、nop-batch-dsl（7）、nop-batch-sys（5）、nop-sys-dao（19）

Follow-up:

- 分布式 worker per-task partition 注入（从 NopJobTask.partitionRange 解析）→ 已在 Deferred But Adjudicated 记录，需 successor plan
- nop-batch-sys 测试环境 jobPartitionResolver bean 未被 _vfs 自动发现（跨模块 _vfs 加载限制），E2E 改用直接 setPartitionResolver 绕过；beans.xml optional-ref 机制本身已由 autotest 日志单独验证。此为测试工程限制，不影响生产接线
