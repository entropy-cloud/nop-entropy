 # nop-job Worker Assignment Design

 **Date**: 2026-07-07
 **Status**: active baseline
 **Related**: `invoker-design.md`, `cluster-ha-design.md`, `priority-design.md`, `01-architecture-baseline.md`

 ## Scope

 This document records the current worker-assignment contract that is already supported by live code in
 `nop-job-coordinator`. It only covers coordinator-side task building and dispatch normalization.

 It does not redefine ORM fields, `ResourceVector` public API, or partition/broadcast semantics.

 ## Live Baseline

 - `dispatchMode=bestFit` routes to `AdaptiveJobTaskBuilder`.
 - `dispatchMode=partition` routes to `PartitionTaskBuilder`; it does not call `IWorkerAssignmentStrategy`.
 - `dispatchMode=broadcast` and legacy `executorKind=rpcBroadcast` route to `RpcBroadcastTaskBuilder`; they do not call `IWorkerAssignmentStrategy`.
 - `dispatchMode ∈ {null, blank, single}` falls back to `executorKind`, then to `DefaultJobTaskBuilder`.
 - Missing non-single builders fail fast with `nop.err.job.dispatch-mode-not-implemented`; they do not silently degrade to single-task dispatch.

 ## Assignment Contract

 `Assignment` is the typed internal result of a best-fit worker decision. It is a `@DataBean` and is allowed to use only fields that already map to existing `NopJobTask` columns.

 Current typed fields:

 - `workerInstanceId: String`
 - `targetHost: String`
 - `shardingIndex: Integer`
 - `shardingTotal: Integer`
 - `partitionRange: String`
 - `cost: ResourceVector`

 Design decisions:

 - `partitionRange` stays a string because `NopJobTask.partitionRange` is already a string column and existing partition code uses the serialized range form.
 - No untyped `Map<String,Object>` or `params/attributes` bag is used for framework-owned routing fields.
 - JSON / BeanTool construction of `Assignment` must follow normal DataBean type-conversion rules; invalid value types fail through the existing conversion path rather than being ignored.

 ## Best-Fit Strategy Contract

 `IWorkerAssignmentStrategy` remains:

 ```java
 AssignmentPlan assign(ResourceVector taskCost, List<WorkerLoad> workers)
 ```

 The current built-in implementation is `LeastLoadedStrategy`.

 Its contract is:

 - input `taskCost` is the schedule-derived default cost for this fire
 - it may return one `Assignment` containing the chosen worker and optional task-column metadata
 - empty plan means no fitting worker
 - best-fit currently supports exactly one resulting task per fire

 `AssignmentPlan` with more than one entry is not supported on the current best-fit path. `AdaptiveJobTaskBuilder` fails fast instead of silently ignoring extra assignments.

 ## Adaptive Builder Mapping

 `AdaptiveJobTaskBuilder` maps `Assignment` onto existing `NopJobTask` typed columns:

 - `workerInstanceId -> task.workerInstanceId`
 - `targetHost -> task.targetHost`
 - `shardingIndex -> task.shardingIndex`
 - `shardingTotal -> task.shardingTotal`
 - `partitionRange -> task.partitionRange`
 - `cost.cpu/memory -> task.costCpu/costMemory`

 Validation rules:

 - empty plan still means `ERR_JOB_NO_FITTING_WORKER`
 - plan size must be exactly 1
 - the selected `Assignment` must be non-null
 - `workerInstanceId` must be non-blank

 These are fail-fast invariants. The builder does not silently drop malformed assignment data.

 ## Cost And Priority Normalization

 The supported cost contract is:

 - schedule `taskCostCpu/taskCostMemory` provides the default cost input to best-fit selection
 - if `Assignment.cost` is non-null, it is the effective task cost for the generated `NopJobTask`
 - if `Assignment.cost` is null, task cost falls back to the schedule default

 Dispatcher normalization in `JobDispatcherScannerImpl.scanOnce()` is single-point and non-destructive:

 - preserve builder-set `costCpu`, `costMemory`, and `priority`
 - only fill null values from schedule defaults
 - normalize remaining null schedule values to `0`

 This keeps best-fit assignment cost alive while preserving existing default/single, partition, and broadcast behavior.

 ## Non-Goals

 The following are explicitly outside the current baseline:

 - changing `nop-job/model/nop-job.orm.xml`
 - changing `ResourceVector` public API
 - introducing a context-aware replacement for `IWorkerAssignmentStrategy`
 - supporting best-fit multi-task plans from one fire
 - moving partition or broadcast dispatch onto the old best-fit strategy interface

 ## Verification Anchors

 - `Assignment.java`
 - `AdaptiveJobTaskBuilder.java`
 - `JobDispatcherScannerImpl.java`
 - `TestAdaptiveJobTaskBuilder.java`
 - `TestJobCoordinatorScanner.java`
