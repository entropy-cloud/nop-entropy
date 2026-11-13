# C8 背压、流控与过载行为 — nop-wf + nop-task 第 2 轮深挖（盲审）

- 审计单元：`nop-wf`（10 jar 子模块）+ `nop-task`（10 jar 子模块），379 files / 36,107 lines
- 审计维度：C8 背压、流控与过载行为（第 2 轮深挖）
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）
- 审计日期：2026-10-15
- 盲审纪律：已遵守。本轮仅读取前序轮次文件 `C8-backpressure.md`（任务简报指定的必要输入）；未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何其他文件，未读取其他维度审计文件。
- 深挖方向（按任务简报）：(1) C8-006 失效面扩展——catch(Exception) 漏 Error/Throwable 的许可泄漏/资源释放同族清扫（F3 finally 缺失形态矩阵）；(2) fork/parallel 子任务派发边界部分派发失败时的占位清理与计数一致性复核；(3) wf 引擎侧过载可观测核对（C8-005 的 wf 对应面）；(4) throttle 等待点与 step timeout/orTimeout 的交互（C8-010 延伸）——等待中的 throttle 是否可被超时/取消终结。

## 检查范围说明

**本轮新增覆盖面**（第 1 轮未逐行核过的方法体级细节）：
- nop-task-core 全部 6 个装饰 wrapper（`TryTaskStepWrapper`/`SyncTaskStepWrapper`/`RunOnContextTaskStepWrapper`/`ValidatorTaskStepWrapper`/`BuildOutputTaskStepWrapper`/`RetryTaskStepWrapper`）+ `ExecutorTaskStepWrapper`/`TimeoutTaskStepWrapper`/`ThrottleTaskStepWrapper`/`RateLimitTaskStepWrapper` 的 Error 传播路径。
- `TaskStepHelper` 的 `timeout`/`retry`/`doRetry`/`withCancellable` 全方法体；`TaskImpl.execute` 同步/异步双出口与四个终态 driver；`TaskStepExecution.executeWithParentRt` 全方法体（:206-371）与 `driveStepFailure`。
- `AbstractForkTaskStep`/`ForkTaskStep`/`ForkNTaskStep`/`ParallelTaskStep` 派发循环与聚合占位逐行复核；`AsyncHelper.waitAsync`/`FutureHelper.bindCancelToken`/`DefaultSemaphore.tryAcquire` 的使用契约核验。
- nop-task-ext 全部 5 个装饰器（`RateLimit/Retry/Timeout/Transaction/OrmSessionTaskStepDecorator`）与 `TransactionalTaskStepWrapper`/`OrmSessionTaskStepWrapper`。
- nop-wf：`WfTaskScanner` 现状复核、`WorkflowEngineImpl` 5 处 catch 站点（:860/:887/:1200/:1309/:1701）、`WorkflowServiceImpl:246`、全模块指标暴露 grep。
- 全单元 `catch (Exception|Throwable|Error` 站点全量枚举（50 处，含 nop-task-dao 11 处），逐一判定是否落入 C8 关心的"资源释放/promise 终结"面。

**判级口径**（沿 campaign）：P0=主路径语义违约/包络内必然资源耗尽；P1=负载上升必然死锁/队列溢出/崩溃恢复静默丢数；P2=无观测/边界配置缺陷/理论风险；P3=优化候选。Error 门控路径沿第 1 轮 C8-006 先例判 P2/P3。

## 1. 本轮发现

### [nop-wf-nop-task-C8-013]

1. **发现ID**：nop-wf-nop-task-C8-013
2. **严重度**：P2（Error 门控低频，与 C8-006 同门控；后果为永久挂死而非可恢复的配额缩水，故为 C8-006 的加重同族）
3. **类别**：C8-2 + F3 finally 缺失形态矩阵——异步派发回调 catch(Exception) 漏 Error，返回 promise 无终结路径
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/step/ExecutorTaskStepWrapper.java:33-52`（实例 A）；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/step/RunOnContextTaskStepWrapper.java:26-38`（实例 B）；平台契约对照 `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/FutureHelper.java:518-526`
5. **证据片段**：
   ```java
   // ExecutorTaskStepWrapper.java:35-51 —— body 同步抛 Error 时 catch(Exception) 不命中，
   // ret 永不 complete；:67 的 bindCancelToken 只做"取消→future.cancel"方向绑定（FutureHelper:518-526），
   // future 以 Error 异常完成的结果没有任何消费者，也不桥接到 ret
   try {
       TaskStepReturn result = getTaskStep().execute(stepRt);
       ...
   } catch (Exception e) {              // ← Error 穿透
       ret.completeExceptionally(e);
   }
   return null;
   ```
   ```java
   // RunOnContextTaskStepWrapper.java:27-37 —— 实例 B 更弱：无任何取消兜底，
   // context.execute 回调内 Error 同样使 future 永不终结
   context.execute(() -> {
       try {
           TaskStepReturn result = getTaskStep().execute(stepRt);
           FutureHelper.bindResult(result.getReturnPromise(), future);
       } catch (Exception e) {          // ← Error 穿透
           future.completeExceptionally(e);
       }
   });
   ```
6. **机制描述**：`<step executor="...">` 或 `runOnContext="true"` 的步骤，其 body 在派发线程上同步抛 **Error**（`StackOverflowError`/`OutOfMemoryError` 等）时，手工创建的返回 promise（`ret`/`future`）没有任何 `completeExceptionally` 路径：`catch (Exception)` 不命中 Error，`whenComplete` 桥接未注册（Error 发生在返回 promise 之前），`bindCancelToken` 仅消费取消事件。对照正确形态：`TryTaskStepWrapper` 异步路径的 `(ret, err)` 回调以 **Throwable** 接收错误；无 executor 的内联执行中 Error 同步上抛、响亮失败——executor/runOnContext 包装把"响亮失败"变成"静默悬挂"。`ExecutorTaskStepWrapper` 的取消兜底（:59-66）仅在 cancel 事件发生时补终结；无人取消且未配 timeout 时永久悬挂。
7. **积压-降级链**：body 抛 Error（可由 C8-009 的 call-task 自递归 SOE、深层 XPL 编译产物 SOE 触发）→ `ret` 永不终结 → 上游消费方分叉：(a) `SequentialTaskStep` 的 `thenApply` 链或 fork/parallel 聚合（`AsyncHelper.waitAsync` 的 `completedCount` 永不达 n，AsyncHelper.java:45-46）悬挂 → task promise 悬挂 → `TaskImpl.execute` 的终态 thenCompose（:153-174）不执行——**终态 driver、`taskRt.runCleanup()`、`metrics.endTask` 全部跳过**，task 实例行永驻非终态且无任何日志/指标（F5 挂死不可见同族）；(b) 顶层经 `TaskGenHelper:41-43` 返回给 GraphQL 的 promise 永不 resolve → 连接与 future 链内存占用累积，客户端重试叠加新任务实例；(c) `sync=true` 路径（`SyncTaskStepWrapper.sync()`）请求线程永久阻塞 → 工作线程池耗尽 → 全服务请求排队；(d) 若该步骤外层配 throttle，`ThrottleTaskStepWrapper:46` 的 `whenComplete` 注册在永不终结的 promise 上 → **许可永久泄漏**（C8-006 的 Error 泄漏在本实例中放大为挂死+配额缩水双重后果）。
8. **触发条件与规模对照**：模型使用 executor/runOnContext（owner doc 声明的一等特性）+ body 抛 Error。Error 来源在单元内可达：C8-009 自递归 SOE、XPL 深层求值 SOE。低频但每次发生即产生一个不可自愈的悬挂任务；无 timeout 配置时无任何兜底（配 timeout 时被 `TaskStepHelper.timeout` 的 timeoutGuard 竞速兜底，降级为有界）。
9. **影响**：任务实例永驻非终态、许可泄漏、连接/线程占用累积；错误完全静默（Error 被派发线程的 uncaughtExceptionHandler 吞掉，业务层零信号）。
10. **与 owner doc 声明对照**：nop-task.md L35 声明 executor 并行为一等特性，未声明 body Error 时的 promise 终结契约——未声明缺口。
11. **缺陷族对照**：**F3 命中**（catch 形态矩阵主条目：派发回调 catch(Exception) 漏 Error 且无 finally 型终结兜底）；F5 命中（悬挂不可见——无日志无指标无终态）；F2 关联（过载时悬挂占用而非快速失败）。
12. **修复建议**：两处 `catch (Exception e)` 改为 `catch (Throwable e)` + `ret.completeExceptionally(e)`（Error 包装后传播，保持终结不变量）；或在 runnable 最外层加 `finally { if (!ret.isDone()) ret.completeExceptionally(new NopException(...)); }` 形态。`RunOnContextTaskStepWrapper` 额外补对称的 cancel 兜底（对照 ExecutorTaskStepWrapper :59-66）。回归测试：executor 步骤 body 抛 Error（如自定义 step 抛 `new Error("boom")`）断言 task 进入 FAILED/KILLED 终态且 throttle 许可归还。
13. **误报校准**：Error 门控故 P2 而非 P1（沿 C8-006 先例）；非"已声明边界"（owner doc 无该语义声明）；timeout 配置可兜底悬挂但 owner doc 未要求步骤必配 timeout，不能作为已有防护抵扣。非测试/生成代码。
- **复核状态**：已保留（P2，可见性修正。核心机制实证：双实例 catch(Exception) 漏 Error（ExecutorTaskStepWrapper.java:48、RunOnContextTaskStepWrapper.java:29）、手工 promise 无 Error 终结路径、bindCancelToken 仅"取消→future.cancel"方向（FutureHelper.java:518-526）、悬挂链 (a)-(d) 逐环成立（AsyncHelper.java:45-46 计数不收敛、TaskImpl.java:153-174 thenCompose 跳过致 runCleanup/endTask/终态 driver 全不执行、TaskGenHelper.java:41-43 顶层 promise 永不 resolve、SyncTaskStepWrapper.sync() 永久阻塞、throttle whenComplete 挂死即许可泄漏）。**可见性修正**：实例 A 的 Error 并非"完全静默"——ExecutorHelper.submit 以 catch(Throwable) 记 ERROR 日志 `nop.executor.execute-fail` 并使 future 异常完成（该 future 无业务消费者），平台层有断连信号、业务层与悬挂本身仍零信号；实例 B（RunOnContext）无此平台层日志，原文表述成立。悬挂后果链不受修正影响，Error 门控 P2 维持。现状：新发现）

### [nop-wf-nop-task-C8-014]

1. **发现ID**：nop-wf-nop-task-C8-014
2. **严重度**：P2
3. **类别**：C8-2 信号量配额语义——timeout/kill 竞速终结即释放 throttle 许可，maxConcurrency 上界在过载场景失效（僵尸执行叠加）
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:46-48`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:197-204,227-237`；wrap 顺序 `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:118-142`（Executor→Timeout→Throttle，Throttle 外层）；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/step/ExecutorTaskStepWrapper.java:59-67`
5. **证据片段**：
   ```java
   // ThrottleTaskStepWrapper.java:46-48 —— release 挂在内层（Timeout）返回的 promise 上：
   // timeout 竞速终结时即触发，不等待 body 实际终止
   return getTaskStep().execute(stepRt).whenComplete((ret, err) -> {
       semaphore.release(1);
   });
   ```
   ```java
   // TaskStepHelper.java:199-204 —— timer 到点无条件终结 winner（body 可能仍在运行）
   Future<?> timer = executor.schedule(() -> {
       cancellable.cancel(ICancellable.CANCEL_REASON_TIMEOUT);
       timeoutGuard.completeExceptionally(
               NopTaskCancelledException.forReason(ICancellable.CANCEL_REASON_TIMEOUT));
   ```
   ```java
   // ExecutorTaskStepWrapper.java:60-63,67 —— 取消方向仅 completeExceptionally + future.cancel(false)：
   // 不中断运行线程，body 成为僵尸继续占用 executor 线程与下游资源
   Consumer<String> onCancel = reason -> {
       if (!ret.isDone())
           ret.completeExceptionally(NopTaskCancelledException.forReason(reason));
   };
   ...
   FutureHelper.bindCancelToken(cancelToken, future);   // → future.cancel(false)，无中断
   ```
6. **机制描述**：同一步骤同时配置 `throttle` + `timeout`（或被 kill）时，`TaskStepHelper.timeout` 的竞速兜底在 timeout 毫秒使 winner promise 异常终结 → Throttle 外层的 `whenComplete` 立即 `release(1)` → 新请求即刻获得许可并提交**新的** body 执行；而被放弃的旧 body 仅收到 `future.cancel(false)`（不中断线程）与 cancel token 信号——不响应取消的 body（阻塞 JDBC、长循环、外部调用无超时）继续运行。任一 cancel/timeout 路径（含父任务 kill 经 ExecutorTaskStepWrapper :59-66 补终结）都产生同样的"许可先于执行归还"。
7. **积压-降级链**：下游变慢 → 每个请求在 timeout 后判超时、立即归还许可 → 新请求获得许可再提交 body → 僵尸执行数随时间单调累积（每个占 1 个 executor 线程 + 1 份下游资源）→ executor 线程池耗尽（所有 executor 步骤平台级停摆）+ 下游实际并发远超 `maxConcurrency`（限流闸门在最需要保护的过载场景失效）→ 下游雪崩加速。恢复仅当僵尸自然结束或进程重启。
8. **触发条件与规模对照**：`throttle`+`timeout` 同配（或 kill 与 throttle 同配）+ body 不响应取消——均为模型可配的常态组合；规模 = 过载时长 × 超时速率，timeout 越短僵尸累积越快。
9. **影响**：maxConcurrency 保证的下游保护上界被击穿；executor 线程池级联耗尽；运维看到的 throttle 指标（若有）与真实并发脱节。
10. **与 owner doc 声明对照**：nop-task.md 未声明 timeout/kill 与 throttle 的许可归还时序语义——提前释放是 liveness 取舍（避免悬挂 body 永久占用配额）但该边界未声明，且失效点恰在过载场景。
11. **缺陷族对照**：F2 命中（过载响应：本应"有界并发"退化为"无界僵尸叠加"）；F3 关联（取消路径的许可/执行生命周期错配）；F5 不命中（可见性尚可——超时错误可见）。
12. **修复建议**：文档显式声明"timeout/kill 后许可立即归还，body 不响应取消时实际并发可短暂超过 maxConcurrency"；或提供 `throttle.releaseOn="promise|cancel-aware"` 语义选项（cancel-aware 模式下 release 延迟到 body 真正终结，配合 body 侧强制定期检查 cancel token）；或对被 timeout 放弃的 body 在 executor 层启用中断型取消（需评估下游中断安全性）。
13. **误报校准**：无 timeout/kill 时许可语义正确（body promise 完成才释放，已核）；本条仅主张 cancel/timeout 路径的时序缺口，且需 body 不响应取消才成立——该前提在阻塞 IO 场景是常态而非边角。非测试/生成代码。
- **复核状态**：已保留（P2。复核属实：release 挂 Timeout winner promise（ThrottleTaskStepWrapper.java:46-48）、timer 到点无条件终结 winner 且 body 可能仍在运行（TaskStepHelper.java:199-204）、取消路径仅 completeExceptionally + future.cancel(false) 无线程中断（ExecutorTaskStepWrapper.java:59-67、FutureHelper.java:518-526；TaskRuntimeImpl.cancel 全链核实无 interrupt）、许可先于 body 终结归还的时序成立；僵尸叠加链以 throttle+timeout 同配 × body 不响应取消为前提（阻塞 IO 常态），不命中"负载上升必然"三情形故 P2 恰当。现状：新发现）

### [nop-wf-nop-task-C8-015]

1. **发现ID**：nop-wf-nop-task-C8-015
2. **严重度**：P2（与 C8-005 同级同性质：能力缺位而非规模触发）
3. **类别**：C8-4 过载可观测缺失——wf 引擎侧零指标暴露（C8-005 的 wf 侧对应面）
4. **位置**：全 `nop-wf` 10 子模块 `src/main`（grep 证据）；重点 `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:78-143`（B1/B2 两个无界边界无任何计数）
5. **证据片段**：
   ```text
   // grep -rn "MeterRegistry|Meter\b|createTimer|createCounter|metrics|Metrics" nop-wf/*/src/main/java
   // → 唯一命中类别为 io.nop.api.core.time.CoreMetrics.currentTimestamp()（时间工具，11 处），
   //   无任何 MeterRegistry / meter / counter / timer 注册点
   ```
   ```java
   // WfTaskScanner.java:79,96 —— 扫描轮无深度/时长/成败计数，唯一信号是散点日志
   for (IWorkflowStepRecord stepRecord : workflowStore.findDueActivatedSteps()) {
       ...
       FutureHelper.syncGet(workflowService.invokeActionAsync(request, null, newSchedulerContext()));
   ```
6. **机制描述**：nop-wf 全模块不存在任何指标注册设施——对照 nop-task 侧至少有 `TaskFlowMetricsImpl`（task/step 成败计时，接入 `GlobalMeterRegistry`）与 stats API（C8-005 指出其零消费），wf 侧连数据结构都不存在：C8-001 的扫描积压深度（`dueTime<=now AND status=ACTIVATED` 行数）、C8-002 的永驻步骤数（无 dueAction/持续失败）、C8-011 的 remind 驻留行数、扫描单轮时长、dueAction 成功/失败速率——全部无 gauge/counter。运维仅能靠通用 CRUD API（`NopWfStepInstanceApi`）即席构造过滤查询，无任何告警钩子或第一类观测入口。
7. **积压-降级链**：C8-001 的积压形成过程（R 超过单轮消化能力 → 延迟无界放大 → 连接长期占用）在指标层面完全不可见——唯一的滞后信号是业务方投诉"审批超时未流转"；C8-002(a) 的 dueAction=null 静默驻留连日志都没有（DEBUG 级）。容量规划、积压告警、事后归因三个环节全部缺数据。
8. **触发条件与规模对照**：规模无关的能力缺位；任何启用 wf 扫描 job 的部署在第一次积压时即暴露。
9. **影响**：wf 过载（B1/B2 边界）不可告警、不可归因；与 C8-005/C8-013/C8-014 的"无终态/无指标"残留叠加后，过载诊断只能靠 DB 即席查询。
10. **与 owner doc 声明对照**：nop-wf.md 未声明任何 wf 侧指标边界——缺失的能力面（与 nop-task.md L33"按配置容量阈值告警"形成模块间不对称）。
11. **缺陷族对照**：F5 关联（积压/滞留不可见同族，与 C8-005 同根不同模块）；F2/F3 不直接命中。
12. **修复建议**：在 `WfTaskScanner` 每轮扫描出口注册 gauge（due 深度 = 查询 count、本轮处理数、本轮耗时、失败数）与 remind 驻留数 gauge，接入 `GlobalMeterRegistry`；沿用 task 侧 `TaskFlowMetricsImpl` 的命名模式（`nop.wf.scan` 维度）。
13. **误报校准**："CRUD 可查"不等于可观测——无第一类 gauge/告警入口即无持续观测（与 C8-005 的"API 存在即算可观测不成立"同一校准逻辑）；grep 全量证实非抽查。非测试/生成代码。
- **复核状态**：已保留（P2。复核属实：nop-wf 全部 10 子模块主代码零指标注册（grep 复证，唯一命中类别为 CoreMetrics.currentTimestamp 时间工具调用）；WfTaskScanner B1/B2 两边界无任何计数实证；与 C8-005 同性质的 wf 侧能力缺位成立。现状：新发现）

### [nop-wf-nop-task-C8-016]

1. **发现ID**：nop-wf-nop-task-C8-016
2. **严重度**：P3（Error 门控；各子项残留有界，无独立挂死/泄漏链）
3. **类别**：C8-2 + F3 finally 缺失形态矩阵——Error 穿透点的清理/终态/定时器残留面（C8-006 失效面扩展的清扫结果）
4. **位置**：(a) `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:142-151`；(b) `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java:360-370`；(c) `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:209-216`；(d) 同文件 `:379-385`（withCancellable）；(e) `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/step/ForkTaskStep.java:68-75`、`ForkNTaskStep.java:48-55`、`ParallelTaskStep.java:67-75`（派发循环）
5. **证据片段**：
   ```java
   // (a) TaskImpl.java:142-150 —— Error 时 runCleanup/endTask/终态 driver 三者全跳过
   } catch (Exception e) {
       taskRt.runCleanup();
       if (metrics != null) metrics.endTask(meter, false);
       driveTaskTerminal(taskRt, taskState, e);
   ```
   ```java
   // (c) TaskStepHelper.java:209-216 —— Error 时超时定时器不取消（延迟触发一次）
   } catch (Exception e) {
       if (cancelToken != null) cancelToken.removeOnCancel(cancel);
       timer.cancel(false);
       throw NopException.adapt(e);
   ```
   ```java
   // (e) ForkTaskStep.java:72-74 —— Error 中断派发循环，后续分支不派发、
   // withCancellable 的完成期 auto-cancel 兜底不执行
   } catch (Exception e) {
       promises.add(FutureHelper.reject(e));
   }
   ```
6. **机制描述**：全单元 50 处 catch 站点逐一枚举后，除 C8-006/C8-013 的两处 promise 终结面外，Error 穿透的残余面收敛为五类，全部**有界**：(a) task seam——Error 传播响亮但 `runCleanup()`（含 task 级 beanContainer.stop）、`metrics.endTask`、终态落盘（FAILED）全部跳过，task 实例永驻非终态（内存 beanContainer 等待 GC）；(b) step seam——同形态，meter 不关、FAILED driver 不执行（内联路径 Error 继续上抛响亮失败）；(c) `timeout()`——Error 时 timer 不取消，延至 timeout 毫秒空触发一次（timeoutGuard 无监听者，自限），`removeOnCancel` 滞留至外层 token 结束；(d) `withCancellable()`——Error 时 `cancellable` 不取消、`stepRt.cancelToken` 不复位、外层监听器滞留（token 生命周期内有界）；(e) fork/forkN/parallel 派发循环——Error 中断循环，`buildAggResult` 不执行（execute 同步上抛，无悬挂），已派发分支失去完成期 auto-cancel 兜底、各自运行至自然完成。
7. **积压-降级链**：逐项写不出无界链：(a)(b) 残留物由 GC 与自然完成兜底、终态缺失属恢复语义退化（resume 时重跑，见 TaskImpl:97-98 非 terminal 不短路）；(c) 单次空触发；(d)(e) 分支数量有限且自然终结。故按口径降 P3 标注"理论风险"，作为矩阵登记防漏。
8. **触发条件与规模对照**：均需 Error 触发；频率与 C8-013 同源（SOE/OOM 类）。
9. **影响**：终态/清理/指标残留；无挂死、无许可泄漏、无连接占用。
10. **与 owner doc 声明对照**：未声明 Error 路径的清理契约——缺口，但影响面小。
11. **缺陷族对照**：**F3 命中**（形态矩阵登记：`catch (Exception)` 与 `finally` 型清理的错配清单）；F5 弱关联（(a) 非终态行不可见，但 resume 重跑兜底存在）。
12. **修复建议**：与 C8-013 一并做 catch 形态治理：task/step seam 的 `catch (Exception e)` 改 `catch (Throwable e)` 后走同一 driver（终态/清理/关 meter）再 re-throw；`timeout()`/`withCancellable()` 的清理块移入 `finally`。低优先级，可与 C8-013 的回归测试同批落地。
13. **误报校准**：各子项独立不构成 P2 链（已逐项验证有界），合并矩阵登记而非拆分充数；对照正面样本 `WorkflowServiceImpl:246` 的 `catch (Throwable)` 与 `TaskStepRuntimeImpl.runCleanup(Runnable)` 的单清理隔离（:200-206）——说明仓库已有正确形态，矩阵项属遗漏非取向。
- **复核状态**：已保留（P3。五站点行号逐一实证：(a) TaskImpl.java:142-151、(b) TaskStepExecution.java:360-370、(c) TaskStepHelper.java:209-216、(d) :379-385、(e) ForkTaskStep.java:68-75/ForkNTaskStep.java:48-55/ParallelTaskStep.java:67-75 均为 catch(Exception) 漏 Error 且残留有界（GC/自然完成/resume 重跑 TaskImpl.java:98 兜底）；矩阵登记口径与 P3 判级正确。现状：新发现）

### [nop-wf-nop-task-C8-017]

1. **发现ID**：nop-wf-nop-task-C8-017
2. **严重度**：P3（有界延迟，无错误行为）
3. **类别**：C8-2 等待点语义——取消传播不覆盖 throttle/rate-limit 等待点（C8-010(b) 的 kill 方向延伸）
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:39-42`；`RateLimitTaskStepWrapper.java:39-43`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/semaphore/DefaultSemaphore.java:37-50`
5. **证据片段**：
   ```java
   // ThrottleTaskStepWrapper.java:39-44 —— 阻塞等待发生在 acquire 期间，
   // 期间不检查 cancelToken、无取消唤醒；checkNotCancelled 在 acquire 之后
   if (!semaphore.tryAcquire(1, maxWait)) {
       throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_THROTTLE_TIMEOUT)...
   }
   try {
       TaskStepHelper.checkNotCancelled(stepRt);
   ```
   ```java
   // DefaultSemaphore.java:38-49 —— 可中断（恢复中断位），但 Nop 取消机制走
   // cancelToken 回调而非线程中断，park 中的 tryAcquire 对取消无感知
   boolean b = semaphore.tryAcquire(permits, timeout, TimeUnit.MILLISECONDS);
   ...
   } catch (InterruptedException e) {
       Thread.currentThread().interrupt();
       throw NopException.adapt(e);
   }
   ```
6. **机制描述**：父任务 kill / 步骤取消（`cancelToken.appendOnCancel` 回调）不会唤醒 park 在 `semaphore.tryAcquire(1, maxWait)` / `rateLimiter.tryAcquire(1, maxWait)` 上的执行线程——取消传播依赖 token 检查点，而下一个检查点在 acquire 成功之后。等待中的 throttle **不能**被超时取消（timeout 计时起点也在 acquire 之后，`TaskStepEnhancer:137-142` 确认 Throttle 在 Timeout 外层），也不能被 kill 取消。
7. **积压-降级链**：kill 延迟上界 = maxWait（可配秒级以上）× 同步骤嵌套 throttle/rate-limit 层数；期间工作线程继续被占（与 C8-010(b) 的 timeout 方向同根：等待点在取消/超时语义覆盖之外，线程占用由 maxWait 而非 timeout/kill 决定）。写不出无界链（必有 maxWait 上界）→ 按纪律降 P3。
8. **触发条件与规模对照**：过载 + 运维 kill 高排队任务时触发；规模影响 kill 响应延迟与线程占用时长。
9. **影响**：kill 请求看似成功但线程仍被占 maxWait；批量 kill 过载任务时线程回收被拉长，延长池耗尽窗口。
10. **与 owner doc 声明对照**：未声明取消延迟上界——文档缺口（可并入 C8-010 的文档化建议）。
11. **缺陷族对照**：F2 弱关联（过载时取消不回收资源）；F5 不命中（有界必终结）。
12. **修复建议**：`tryAcquire` 拆分为短周期轮询 + 每轮 `checkNotCancelled`（如 100ms 步进），或注册 cancel 回调向等待线程 interrupt（DefaultSemaphore 已正确处理中断）。可与 C8-010 的修复合并评估。
13. **误报校准**：与 C8-010(b) 的区别——C8-010 主张 timeout 覆盖缺失，本条主张 kill/取消通道对同一等待点同样失效，属同一位置的第二个触发源，独立登记便于修复验证；有界故 P3。非测试/生成代码。
- **复核状态**：已保留（P3。复核属实：tryAcquire 阻塞期无取消检查点（ThrottleTaskStepWrapper.java:39-44、RateLimitTaskStepWrapper.java:39-43，checkNotCancelled 均在 acquire 之后）、DefaultSemaphore 仅处理线程中断而 Nop 取消走 token 回调（:47-59）、kill 链路（TaskRuntimeImpl.cancel）核实全程无 interrupt；kill 延迟有界于 maxWait，P3 恰当。现状：新发现）

## 2. 排除项/定向核查

**定向核查 1——fork/parallel 部分派发失败占位清理与计数一致性（简报指定复核）**：复核通过，第 1 轮"聚合 allSuccess fail-fast 非半个成功"结论维持。Exception 路径：派发循环 `catch (Exception e)` → `FutureHelper.reject(e)` 占位（ForkTaskStep:72-74、ForkNTaskStep:52-54、ParallelTaskStep:72-74），`promises.size()` 始终等于分支总数，聚合索引映射 `promises[i]↔steps[i]`（ParallelTaskStep:92-93、AbstractForkTaskStep:123-132）保持一致；`AsyncHelper.waitAsync`（AsyncHelper.java:29-56）allSuccess 下首个 failure 记入 `failure`、计数达 n 后整聚合异常返回——fail-fast；`withCancellable` 的 Exception 路径清理正确（TaskStepHelper:379-385：cancel+removeOnCancel+token 复位三件事齐做），已派发分支被 auto-cancel 并补 `ERR_TASK_CANCELLED` 占位（AbstractForkTaskStep:128-132、ParallelTaskStep:98-102）。Error 路径残留已登记为 C8-016(e)，不重复立案。

**定向核查 2——throttle 等待能否被 step timeout/orTimeout 取消（简报指定核验）**：**不能**。wrap 顺序实核为 Executor(:118-120) → RunOnContext(:123-125) → Timeout(:130-135) → Throttle(:137-142) → RateLimit(:145-153)（内→外），throttle 的 `tryAcquire` 阻塞发生在 `TaskStepHelper.timeout` 创建 timer 之前——timeout 竞速、orTimeout 型兜底（timeoutGuard，:197-204）均无法覆盖等待段。timeout 方向已由第 1 轮 C8-010(b) 立案；kill 方向缺口本轮登记为 C8-017。

**定向核查 3——`TimeoutTaskStepWrapper` 恢复外层 token 的复位链**：`ret.whenComplete((v,e) -> stepRt.setCancelToken(outerToken))`（TimeoutTaskStepWrapper:28）挂在 winner 上，timeout 异常终结同样触发复位——取消/超时后 token 不滞留，无发现。

**定向核查 4——ext 五装饰器**：纯构建期配置校验（requestPerSecond<=0、timeout<=0、retry 各参数<0 均硬失败，与 plan 364 口径对齐），无运行时 acquire/release 面；`TransactionalTaskStepWrapper`/`OrmSessionTaskStepWrapper` 的 async 形态把内层 promise 交给事务模板展平——内层 promise 悬挂（C8-013 场景）会连带事务/连接不归还，已并入 C8-013 影响链 (c)，wrapper 自身无缺陷。

**排除项清单（确认干净）**：
1. `WorkflowServiceImpl.transferActorsAsync:246` 使用 `catch (Throwable)`——批量转办的单条隔离形态正确（F3 矩阵正面样本，反证 C8-013 两处为遗漏）。
2. `TaskStepRuntimeImpl.runCleanup(Runnable)`（:200-206）单清理失败隔离不中断其余清理——正确。
3. `DaoTaskStateStore` 11 处 `catch (Exception)`——持久化降级/漂移标记面，plan 364 已裁定文档化窗口，非 C8 新面。
4. `DefaultSemaphore.tryAcquire` 中断处理正确（恢复中断位 + NopException.adapt）——线程中断通道健全（仅 Nop 取消机制不走该通道，见 C8-017）。
5. `RateLimitTaskStepWrapper` 无许可需释放；acquire 后 `checkNotCancelled` 语义正确。
6. `WorkflowEngineImpl` transit/action 的 `catch (Exception) → handleError`（:860/:1309）为 wf 错误处理语义（onError XPL / 重抛），非过载面；`getAllowedActions:1200` 单 action 跳过为列表接口容错，正确。
7. `runSourceWithRetry`（WorkflowEngineImpl:1187-1210）的 `Thread.sleep` 阻塞重试：受 wf.xdef 声明的 `retry.maxRetryCount/retryDelay/maxRetryDelay` 有界约束，属已声明模型能力的固有形态（与 C8-010(a) 同族），不另立案。
8. `AbstractForkTaskStep.valueOfDone`/`ParallelTaskStep.valueOfDone`/`StepResultBean.buildFrom` 的 catch(Exception)——已完成 future 的 `getNow` 容错，异常由占位路径记录，无资源面。
9. `BuildOutputTaskStepWrapper`/`ValidatorTaskStepWrapper`/`SyncTaskStepWrapper` 无 acquire/release 与 promise 创建面，Error 传播为同步响亮失败——干净。
10. `AsyncHelper.waitAsync` 空 promises Guard（:21）与 fork/forkN 空集合守卫（ForkTaskStep:66-81、ForkNTaskStep:59-63）——空聚合路径已闭合，无发现。
11. wf 侧 `CoreMetrics.currentTimestamp()` 11 处命中均为时间工具调用，非指标注册——C8-015 的 grep 判定无假阳性。

## 3. 终止声明

本轮为 C8 维度第 2 轮深挖，产出 2×P2（C8-013、C8-014）+ 1×P2（C8-015）+ 2×P3（C8-016、C8-017）。简报指定的四个深挖方向已全部闭环：

1. C8-006 失效面扩展：全单元 50 处 catch 站点全量枚举完毕，promise 终结面收敛为 C8-006（已立案）+ C8-013（本轮立案），清理残留面收敛为 C8-016 矩阵，无未分类残留点。
2. fork/parallel 派发边界：Exception 路径复核干净，Error 路径残留已入矩阵，计数一致性确认。
3. wf 过载可观测：grep 全量证实零指标，C8-015 立案，wf 侧观测面已盘清（无更多可盘结构）。
4. throttle/timeout/取消交互：wrap 顺序、计时起点、取消通道、许可归还时序四点全部实核，C8-014/C8-017 立案，交互面无剩余未知。

上述方向不存在需要第 3 轮继续下钻的盲区；剩余 Error 门控 P3 已全部矩阵化登记。**未发现需要第三轮深挖的高价值方向。C8 维度深挖终止。**

## 4. 覆盖声明

**本轮已覆盖（增量）**：
- nop-task-core 装饰 wrapper 全部 10 个类的 Error/取消传播路径逐行核验；`TaskStepHelper` 4 个关键方法（timeout/retry/doRetry/withCancellable）全方法体；`TaskImpl` 双出口 + 4 终态 driver；`TaskStepExecution.executeWithParentRt`（:206-371）+ `driveStepFailure`。
- fork/forkN/parallel 派发-聚合全链路（派发循环、占位、计数、autoCancel、aggregator 入口）；`GraphTaskStep` 节点等待结构（waitCount/buildWaitFuture 计数面复核，与第 1 轮 runningCount 结论一致）。
- nop-task-ext 5 装饰器 + 2 wrapper；nop-task-dao catch 站点 11 处判定。
- nop-wf：WfTaskScanner 现状（与第 1 轮证据一致）、WorkflowEngineImpl 5 处 catch、WorkflowServiceImpl:246、全模块指标暴露 grep（零命中证实）。
- 平台使用契约：`FutureHelper.bindCancelToken`（仅取消方向）、`AsyncHelper.waitAsync`（计数收敛判据）、`DefaultSemaphore.tryAcquire`（中断语义）、`IContext.execute(Runnable)` 契约、`TaskGenHelper` 顶层 promise 交付形态。

**仍然不在范围**（沿第 1 轮）：平台设施自身实现（`StandardThreadPoolExecutor` force 队列、`DefaultRateLimiter`、`GlobalExecutors` 默认参数、`FutureHelper` 其余方法）；nop-job 调度策略；`WorkflowEngineImpl` 表达式求值内部（C1/C2 维度）；`_gen/` 与测试代码；页面层与 codegen/meta 模块。

**规模口径复核**：两单元 379 files/36,107 行中，本轮触达 C8 相关运行时逻辑文件 41 个（task-core 31 + task-ext 7 + wf-core/scheduler 3），其余为 CRUD/bean/模型层，与第 1 轮边界声明一致。
