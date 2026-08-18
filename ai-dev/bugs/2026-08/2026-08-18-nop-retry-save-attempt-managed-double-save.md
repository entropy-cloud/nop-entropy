# 2026-08-18 nop-retry saveAttempt Double-Save and Stale Test Assertions

## Problem

- `nop-retry-engine` 聚焦测试（TestRetryEngineImpl / TestRetryRecordStoreImpl）大面积失败：所有走 RPC 执行路径的用例在 `future.get()` 处抛 `ERR_ORM_SAVE_ENTITY_NOT_TRANSIENT` 包裹的 `ExecutionException`。
- 另有 4 个用例假定 `future.get()` 直接抛 `NopException`（实际被 `ExecutionException` 包裹）；deadline 用例假定 `executeTask` 重复提交会触发 deadline 检查（live 路径不会）。

## Diagnostic Method

- 表面是 ORM 异常，先怀疑 ORM 模型/字典改动，检查 `nop-retry.orm.xml` 无异常后排除。
- 读 `OrmSessionImpl.saveDirectly`（nop-persistence/nop-orm）：`if (!state.isTransient()) throw ERR_ORM_SAVE_ENTITY_NOT_TRANSIENT`，确认 `saveDirectly` 只接受 TRANSIENT 实体。
- 读 `RetryEngineImpl.doExecute`：同一 `NopRetryAttempt` 对象保存两次（执行前 RUNNING 插入、执行后写终态），第一次 `saveDirectly` 后 `persisterPostSave` 将实体置为 MANAGED（OrmSessionImpl:1167），第二次再走 `saveDirectly` 必然抛错。
- deadline 用例：live 路径 `executeTask` 对重复幂等键走 `handleBlockStrategy`（DISCARD 直接丢弃），deadline 检查只在 `executeWithRetry` 执行路径，确认用例写错路径。
- 排查 createTime 被 ORM 盖写：`OrmTimestampHelper.onCreate` 默认强制以当前时间覆盖 createTime，测试需 `orm_disableAutoStamp(true)` 才能保留手工设置的旧时间。

## Root Cause

- `RetryRecordStoreImpl.saveAttempt` 无条件调用 `saveEntityDirectly`，对已 MANAGED 的 attempt 实体二次保存抛 `ERR_ORM_SAVE_ENTITY_NOT_TRANSIENT`。
- 测试层 4 处断言把 `ExecutionException` 当 `NopException` 断言；deadline 用例选错触发路径；`MockRpcServiceInvoker.setResponses` 不重置 `responseIndex` 导致跨阶段响应越界回落为 null；测试 helper `addOrderField(name, true)` 误当升序（第二参是 desc）；分区用例断言两个不同幂等键同分区；回调用例 `callbackPolicyId` 自引用造成回调链递归。

## Fix

- `RetryRecordStoreImpl.saveAttempt`：按 `attempt.orm_state().isTransient()` 分流——TRANSIENT 走 `saveEntityDirectly`（插入），MANAGED 走 `updateEntityDirectly`（更新）。
- deadline 用例改为 `executeRetryFromScanner(oldRecord, null)` 触发（deadline 属执行路径），并对老记录 `orm_disableAutoStamp(true)` 保留旧 createTime。
- 新增 `expectFutureFailure` helper：断言 `ExecutionException` 且 cause 为 `NopException`。
- `MockRpcServiceInvoker.setResponses` 重置 `responseIndex`。
- 测试 helper 排序改 `addOrderField(name, false)`（升序）；分区用例只断言同键同分区、异键在有效范围；回调用例改用独立回调策略（自身不配 callback，杜绝递归回调链）。

## Tests

- `nop-retry/nop-retry-engine/src/test/java/io/nop/retry/engine/store/TestRetryRecordStoreImpl.java` - 新增 `testSaveAttempt_shouldInsertThenUpdateSameEntity`：同一 attempt 实体先插后更，终态落库且仅一行。
- `nop-retry/nop-retry-engine/src/test/java/io/nop/retry/engine/impl/TestRetryEngineImpl.java` - `testExecuteTask_shouldPersistAttemptsForEachExecution` 覆盖引擎内 doExecute 双保存路径（attempt 终态 FAILED/SUCCESS 正确落库）；deadline/dead-letter 断言改为正确路径与异常解包。

## Affected Files

- `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java`
- `nop-retry/nop-retry-engine/src/test/java/io/nop/retry/engine/impl/MockRpcServiceInvoker.java`
- `nop-retry/nop-retry-engine/src/test/java/io/nop/retry/engine/impl/TestRetryEngineImpl.java`
- `nop-retry/nop-retry-engine/src/test/java/io/nop/retry/engine/store/TestRetryRecordStoreImpl.java`

## Notes For Future Refactors

- nop-orm 语义：`saveEntityDirectly` 只接受 TRANSIENT，`updateEntityDirectly` 只接受 MANAGED；对同一实体"先插后更"必须按 `orm_state()` 分流，不能复用同一个 save 调用。
- ORM 默认会用当前时间覆盖 createTimeProp/updateTimeProp；测试要构造历史时间必须 `orm_disableAutoStamp(true)`。
- `QueryBean.addOrderField(name, desc)` 第二参是降序标志，false 才是升序。
- 回调策略若 `callbackPolicyId` 指回自身（或任何开启了 callback 的策略）会形成回调链递归；引擎层目前不设防，配置时必须使用无 callback 配置的独立策略。
