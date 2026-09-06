# 2026-08-28 GraphTaskStep Drain Race False Positive

## Problem

- 全量 `./mvnw test`（fix-ai-check 分支，2026-08-28）在 nop-ai-agent 中断：`TestMultiMemberFanOutSuccess` / `TestMultiMemberFanOutEndToEnd` / `TestAsyncSpawnStepParallelBranches` 的菱形 DAG 测试随机失败，签名一致：`TeamTaskFlowResult{success=false, completed=[3/4], failedTaskId=null, join任务startOrder=4但不完成}`，且失败发生在 ~185ms（远小于 30s 测试等待与 360s 整体超时）。
- 单独运行这些测试类**全部通过**；只在全模块（3387 tests）或多类组合运行时随机失败——时序敏感。

## Diagnostic Method

- 诊断难点：单类运行无法复现；失败签名（无 failedTaskId、join 未完成、快速返回）与任何节点失败形态都不匹配。
- 先排除：整体超时（orTimeout=memberExecTimeoutMs×maxDepth=120s×3，远大于 185ms）；测试间 bean/配置污染（team 根包+fan-out 组合通过）。
- 按 surefire 报告文件 mtime 还原全量运行的真实类顺序，再按类组合对半二分，缩小到 `team.flow.TestAsync*` 前缀类与 fan-out 类共存即复现——且 TestAsyncSpawnStepParallelBranches 自身也失败，排除"纯测试污染"，锁定并发交错。
- 读 `TeamTaskFlowOrchestrator.executeAsync` 的异常转换：`exceptionally` 对任何图级异常产出 `buildResult(false)`（failedTaskId=null 当且仅当 recorder 无节点失败记录）→ 唯一匹配签名的异常源是 `ERR_TASK_GRAPH_NO_ACTIVE_STEP` 的 drain 判定。
- 决定性证据：在 nop-task-core 写 barrier 同步双前驱完成的菱形压力测试（TestGraphDrainRace），未修复代码上稳定复现 `nop.err.task.graph-no-active-step`（异步阶段、非建图阶段）。

## Root Cause

- `GraphTaskStep.runStep` 完成回调中先 `runningCount.decrementAndGet()`、再触发完成级联（`stepFuture.complete` → 同步调度后继 `runStep` → `incrementAndGet`）。菱形双前驱 B/C 在不同线程并发完成时，后完成者线程存在"decrement 已执行、后继 increment 未执行"的瞬时零窗口；另一线程的 drain 判定（`runningCount.get()==0 && !future.isDone()`）在该窗口内读到 0，误判图已排空。
- plan344（commit `c3dc34df53`，2026-08-23）把该判定从 `throw`（异常进入被丢弃的依赖 future，静默无害）改为 `completeExceptionally`（修复图挂死的正确改动）后，这个长期潜伏的竞态假阳性真正开始生效——修复暴露了另一个缺陷。

## Fix

- `GraphTaskStep.runStep` 的 `runningCount.decrementAndGet()` 后置于完成级联（err/success 处理、`stepFuture.complete*`、exit 完成之后，drain 判定之前）。级联期间完成节点仍被计数，任何并发 drain 判定都不可能读到"后继调度中"的瞬时 0；真正的 drain（无可达 exit）语义不变（`buildDrainError`/firstError 保留）。
- 不变量：后继的 increment 永远在级联内同步发生，而级联期间其前驱被计数 → runningCount 到 0 当且仅当无在执行节点且无 pending 调度。

## Tests

- `nop-task/nop-task-core/src/test/java/io/nop/task/step/TestGraphDrainRace.java` - 菱形 A→{B,C}→D，B/C future 由两线程经 CyclicBarrier 对齐后同时完成（whenComplete 回调并发进入完成路径），300 轮迭代断言无一次 drain 误判。红验证：临时回退修复后稳定红（ExecutionException: nop.err.task.graph-no-active-step，异步阶段命中）；修复后绿。
- 既有回归：nop-task-core 117 tests、nop-ai-agent 全模块 3387 tests 修复后全绿（修复前全模块运行 3 处失败）。

## Affected Files

- `nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java`（runStep 完成回调的 decrement 顺序）
- `nop-task/nop-task-core/src/test/java/io/nop/task/step/TestGraphDrainRace.java`（新增）

## Notes For Future Refactors

- 不要把 `runningCount.decrementAndGet()` 提前到完成级联之前——"完成节点在级联期间仍被计数"是 drain 判定正确性的前提，顺序回归会重新引入瞬时零竞态。
- drain 判定（`completeExceptionally`）语义依赖"后继调度同步发生在前驱 stepFuture.complete 的级联内"；若未来把等待图改为异步组合（thenApplyAsync 等），需重新设计计数与判定。
- 时序敏感竞态的验证不能只靠单类运行：nop-ai-agent 的失败只在全模块运行时出现，单类/小组合全绿。诊断此类问题时按 surefire 报告 mtime 还原真实执行顺序再二分，比逐类猜有效得多。
