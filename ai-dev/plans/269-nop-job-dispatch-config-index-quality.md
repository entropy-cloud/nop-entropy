# 269 nop-job 派发/配置/索引质量收尾

> Plan Status: planned
> Last Reviewed: 2026-06-19
> Source: `ai-dev/audits/2026-06-19-0931-adversarial-review-nop-job/01-open-findings.md`（AR-89, AR-90, AR-91, AR-92, AR-96, AR-97, AR-98, AR-99）
> Related: `ai-dev/plans/267-nop-job-resource-limit-worker-correctness.md`（AR-95 修复 cost/priority null 归一，是 AR-92 NULL 排序修复的前置）

## Purpose

把 R9 后新代码（Plan 213~215）中的"名实不符、配置陷阱、性能热路径、边界 off-by-one、类型脆弱"等 P2/P3 缺陷收口，使 best-fit 派发的命名/语义自洽、capacity 配置无黑洞、优先级排序有索引且跨库一致、派发热路径有缓存、边界与类型安全。

## Current Baseline

- `SingleBestFitStrategy` 类名/文档/bean id 称 "Best-Fit"，实现选 loadScore 最小（最闲），实为 worst-fit/spread。
- `MetadataWorkerCapacityProvider`：config `0` 视为未设→MAX_VALUE（无限），metadata `"0"` 解析为真实零→worker 拒绝一切；负数无校验。测试未覆盖字面量 "0"。
- `DefaultJobTaskBuilder`（single 模式）把 `workerInstanceId` 设为 coordinator hostId；分离部署 worker 开 `enforceAttribution=true` 时 single 任务（非 NULL、非 worker hostId）无人认领。
- `fetchWaitingTasks` 按 `priority DESC` 排序，无以 priority 开头的复合索引（filesort）；`priority` 为可空 Integer，跨 MySQL/Oracle/PostgreSQL NULL 排序不一致。
- `DefaultWorkerLoadProvider.getWorkerLoads` 每个 fire 重查服务发现 + GROUP-BY 聚合，无 per-scan 缓存。
- `ResourceVector.add` 为 int+int 静默溢出；`sum(costCpu)` SQL 映射 Integer。
- `PartitionTaskBuilder` 用 `shortRange()[0,32766]`，但 `partition_index` SMALLINT max=32767。
- `AdaptiveJobTaskBuilder` `(String) jobParams.get("serviceName")` 强转，非 String 时 CCE。

## Goals

- best-fit 派发的命名/文档与实际装箱语义自洽（改名或改方向，并对齐文档）。
- capacity 配置无"0=无限 vs 0=零"的语义分叉，负数被拒绝。
- single 模式任务在 `enforceAttribution=true` + 非同地部署下不被饿死。
- 优先级排序有支持索引、跨库行为一致；派发热路径有 per-scan 缓存。
- 分片边界无 off-by-one；serviceName 取值类型安全。

## Non-Goals

- 不改资源限制 double-count（归 Plan 267）、扫描循环错误隔离（归 Plan 268）。
- 不重写分区分配算法的分布质量（WeightedPartitionAssigner 的不均分布仅记录）。
- 不改 `ResourceVector` 为任意 key 维度（保持 cpu/memory 二维）。

## Scope

### In Scope

- `SingleBestFitStrategy` 命名/语义对齐（AR-89）
- capacity "0" 语义统一 + 负数校验（AR-90）
- single 模式 enforceAttribution 饥饿修复（AR-91）
- 优先级复合索引 + NULL 排序收敛（AR-92）
- per-scan worker load 快照缓存（AR-96）
- `ResourceVector.add` 防溢出 + SQL SUM 类型安全（AR-97）
- `PartitionTaskBuilder` 区间上界修正（AR-98）
- `AdaptiveJobTaskBuilder` serviceName 类型安全（AR-99）

### Out Of Scope

- 优先级惊群（thundering herd）的真实负载量化（仅记录）。
- dispatcher 跨事务并发模型（归 Plan 267/后续）。

## Cross-Plan Execution Order

Phase 3 的 AR-92（优先级 NULL 排序收敛）依赖 Plan 267 Phase 1 的 AR-95（priority 落库前 null→0 归一）先落地，否则跨库 NULL 排序验收无法成立。**Plan 267 Phase 1 必须先于本计划 Phase 3 执行。** 其余 Phase（1/2）与 267/268 无强依赖。

## Execution Plan

### Phase 1 - best-fit 命名/语义对齐 + 资源运算防溢出

> **方向裁定（回应对抗审查 Blocker-1）**：`dispatchMode=bestFit` 是 dict 合法值（`dispatch-mode.dict.yaml`），`resolveTaskBuilder` 用 `nopJobTaskBuilder_bestFit` 查 bean，已落库 schedule 行的 dispatchMode 也是 `bestFit`。**保留 dispatchMode 值 `bestFit` 与 bean id `nopJobTaskBuilder_bestFit` 不变**（避免路由断裂 + 存量数据迁移），只允许重命名策略类 `SingleBestFitStrategy` 与策略 bean id `workerAssignmentStrategy`，并对齐类注释/文档。改方向（变最紧装箱）作为替代选项，但需同步改 `ResourceVector.loadScore` 注释与回归用例。
>
> **AR-97 裁定（回应 Major-1）**：`ResourceVector` 保持 `int`（现实单 worker 容量远小于 2^31，溢出不现实）；`add` 加 `Math.addExact` 防御性抛错；SQL `SUM` 保持现状（聚合按 worker 分组，单 worker reserved 不会溢出 int），不强制 BIGINT cast（避免 int↔long 阻抗与 row type 链式改动）。

Status: planned
Targets: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/SingleBestFitStrategy.java`, `nop-job/nop-job-api/src/main/java/io/nop/job/api/resource/ResourceVector.java`, `nop-job/nop-job-coordinator/src/main/resources/_vfs/nop/job/beans/app-engine.beans.xml`, `nop-job/nop-job-meta/src/main/resources/_vfs/dict/job/dispatch-mode.dict.yaml`, `ai-dev/design/nop-job/worker-assignment-design.md`

- Item Types: `Decision`, `Fix`

- [ ] 决策（二选一并记录到 `ai-dev/design/nop-job/worker-assignment-design.md`）：(a) 重命名策略类 `SingleBestFitStrategy`→反映 spread/least-loaded 语义 + 更新 `app-engine.beans.xml` 的 `class` 与策略 bean id `workerAssignmentStrategy`（**不改** `nopJobTaskBuilder_bestFit` 与 dispatchMode 值）；或 (b) 改比较方向为真正 best-fit（最紧）+ 同步改 `ResourceVector.loadScore` 注释。两种均须更新 dispatch-mode.dict.yaml 标签与 design 文档使名实一致
- [ ] `ResourceVector.add` 改为 `Math.addExact`（溢出显式抛 ArithmeticException 而非静默回绕）

Exit Criteria:

- [ ] 决策记录写入 `ai-dev/design/nop-job/worker-assignment-design.md`（具体路径，验收可查）
- [ ] 回归测试：dispatchMode=bestFit 的派发路由仍生效（`nopJobTaskBuilder_bestFit` bean 仍被解析），存量 bestFit schedule 行为不变
- [ ] 回归测试：`SingleBestFitStrategy` 行为与命名/文档一致（若改方向，新增最紧装箱用例；若改名，原 spread 行为用例保留）
- [ ] 回归测试：`ResourceVector.add` 溢出场景抛 ArithmeticException 不静默回绕
- [ ] **接线验证**：重命名/改向后，`nopJobTaskBuilder_bestFit` 在运行时仍被 `resolveTaskBuilder` 解析并调用（断言）
- [ ] `./mvnw test -pl nop-job -am` 全过
- [ ] design/owner doc 同步（派发策略语义）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - capacity 配置与归因饥饿修复

> **方向裁定（回应对抗审查 Blocker-2 / Major-4）**：AR-90 的 config-vs-metadata "0" 语义分叉在 worker 侧（`MetadataWorkerCapacityProvider`）与 **dispatcher 侧（`DefaultWorkerLoadProvider.parseCapacity`）各有一处**，必须同修否则语义分叉被搬家。AR-91 single 模式 workerInstanceId 留 NULL 的交互已验证：SUSPICIOUS 探活只对 RUNNING 生效且认领后 workerInstanceId 被设为 worker hostId（探活不破坏）；`sumReservedCostByWorker` SQL `where workerInstanceId is not null` 会排除 NULL 行（single 任务本不归因具体 worker，correct 行为）。

Status: planned
Targets: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/capacity/MetadataWorkerCapacityProvider.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultWorkerLoadProvider.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultJobTaskBuilder.java`

- Item Types: `Fix`

- [ ] 统一 capacity "0" 语义（worker 侧 `MetadataWorkerCapacityProvider` 与 dispatcher 侧 `DefaultWorkerLoadProvider.parseCapacity` 一致：同为"未设→MAX_VALUE"或同为真实零，并文档化）；两侧对负数 capacity 抛明确异常
- [ ] 修复 single 模式 enforceAttribution 饥饿：`DefaultJobTaskBuilder`（single 模式）`workerInstanceId` 留 NULL（走 IS NULL 分支被任意 worker 认领）；保留认领后由 `tryLockTasksForExecute` 设为 worker hostId 的既有行为（SUSPICIOUS 探活不受影响）

Exit Criteria:

- [ ] 回归测试：config 与 metadata 同填 `0` 行为**两侧一致**；新增字面量 "0" 的 metadata 用例（worker 侧 + dispatcher 侧）
- [ ] 回归测试：负数 capacity 在两侧均被拒绝并抛明确错误
- [ ] 回归测试：分离部署 worker 开 `enforceAttribution=true` 时，single 模式任务能被认领（不再饿死）
- [ ] **接线验证**：single 模式任务认领后 SUSPICIOUS 探活仍生效（workerInstanceId 认领后被设为 hostId，探活路径未被破坏）
- [ ] **无静默跳过**：负数 capacity 抛异常而非静默退化为某默认值
- [ ] `./mvnw test -pl nop-job -am` 全过
- [ ] 相关 owner doc 同步（capacity 配置语义、enforceAttribution 适用范围）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 索引/缓存/边界/类型安全

> **方向裁定（回应对抗审查 Major-2/3/5/6）**：(a) AR-96 per-scan 缓存需给 `IWorkerLoadProvider` 加 scan 生命周期 hook 并由 `JobDispatcherScannerImpl.scanOnce` 调用（这两个文件加入 Targets），单纯改 `DefaultWorkerLoadProvider` 内部无法感知 scan 边界。(b) AR-92 的 `partitionIndex` 是 BETWEEN 范围谓词，复合索引在范围列之后的列（priority/createTime）无法用于索引排序——filesort 可能无法完全消除；本计划如实评估并优先靠 267 的 NULL 归一解决跨库分叉，索引作为尽力优化。(c) AR-99 同型 `(String)` 强转存在于三个 builder，同批修齐。(d) AR-98 禁止改共享方法 `IntRangeBean.shortRange()`，只在 `PartitionTaskBuilder` 局部修正。

Status: planned
Targets: `nop-job/model/nop-job.orm.xml`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/IWorkerLoadProvider.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultWorkerLoadProvider.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/PartitionTaskBuilder.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/AdaptiveJobTaskBuilder.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcBroadcastTaskBuilder.java`

- Item Types: `Fix`, `Decision`

- [ ] AR-92：评估并尝试为 priority 排序优化索引（如实评估 BETWEEN 范围谓词对复合索引的限制）；优先保证 267 AR-95 的 NULL 归一落地后跨库排序一致；若 filesort 无法消除则记录为 watch-only residual 并说明理由
- [ ] AR-96：给 `IWorkerLoadProvider` 增加 scan 生命周期（如 beginScan/endScan 或快照获取），`JobDispatcherScannerImpl.scanOnce` 在批处理开始时触发一次快照，`DefaultWorkerLoadProvider` 在 scan 作用域内缓存发现 + 聚合结果（同一次 scanOnce 内不随 fire 数线性增长）
- [ ] AR-98：在 `PartitionTaskBuilder` 局部用覆盖全 SMALLINT 范围（含 32767）的区间，**禁止修改共享方法 `IntRangeBean.shortRange()`**（该方法被 nop-cluster 的 `PartitionAssignHelper.SHORT_HASH_RANGE` 等共享，改它会跨模块漂移）
- [ ] AR-99：`AdaptiveJobTaskBuilder`、`PartitionTaskBuilder`、`RpcBroadcastTaskBuilder` 三处的 `(String) jobParams.get("serviceName")` 同批改为类型安全转换（非 String 时显式失败/跳过，不抛 CCE）

Exit Criteria:

- [ ] AR-92：ORM 模型索引变更（如采纳）写入源模型 `nop-job.orm.xml`，`_app.orm.xml` 经 codegen 重新生成后一致；评估结论（是否消除 filesort）以 EXPLAIN 输出或等效可观测证据记录，不使用"合理等效"模糊措辞；跨库 NULL 排序因 267 归一而一致
- [ ] AR-96 回归测试：一次 `scanOnce` 内 `getWorkerLoads` 的发现/聚合查询次数不随 fire 数线性增长（断言查询计数）
- [ ] **接线验证（AR-96）**：scan 生命周期 hook 在运行时被 `JobDispatcherScannerImpl.scanOnce` 调用，缓存确实被命中（计数器断言）
- [ ] AR-98 回归测试：partition 哈希到 32767 的数据被某 task 覆盖（不再丢边界）；`IntRangeBean.shortRange()` 未被修改（nop-cluster 调用方行为不变）
- [ ] AR-99 回归测试：三个 builder 的 jobParams 中 serviceName 为非 String 类型时不抛 CCE（显式失败或跳过）
- [ ] **端到端验证**：partition 模式从调度→分片 task→worker 按 partitionRange 处理→完成完整跑通，边界 partition（32767）被覆盖
- [ ] **无静默跳过**：AR-99 非 String 分支显式失败而非静默 continue
- [ ] `./mvnw clean install -pl nop-job -am -DskipTests`（含 codegen 重新生成）+ `./mvnw test -pl nop-job -am` 全过
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] AR-89/90/91/92/96/97/98/99 的 confirmed defect 已修复且有回归测试
- [ ] best-fit 命名/语义自洽；capacity 无黑洞；优先级有索引且跨库一致；热路径有缓存；边界/类型安全
- [ ] 不存在被静默降级到 deferred 的 in-scope defect
- [ ] 受影响 owner docs（`docs-for-ai/03-modules/nop-job.md`、`docs-for-ai/04-reference/source-anchors.md` 如有索引/字段变化）已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：索引/缓存/边界修复在运行时确实生效
- [ ] `./mvnw clean install -pl nop-job -am -DskipTests`（codegen 重新生成）
- [ ] `./mvnw test -pl nop-job -am`
- [ ] checkstyle 通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/269-nop-job-dispatch-config-index-quality.md --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-job --severity high` 退出码 0

## Non-Blocking Follow-ups

- 优先级惊群（thundering herd）在高 worker 数下的 CAS 浪费量化与缓解（性能优化，不影响正确性）
- `WeightedPartitionAssigner` 在权重/limit 组合下的不均分布改善（已确认无重叠/无间隙，仅分布质量问题）

## Closure

Status Note: (待 closure audit 填写)
Completed: (待定)

Closure Audit Evidence:
- (待独立子 agent closure audit 后写入)
