# C4 深挖第 2 轮 — nop-ai-toolkit-tools-shell（WI 3.2）

- 审计日期：2026-10-20（深挖第 2 轮，合并批 C1/C4/C6/C8）
- 定位：第 1 轮 C4 共 7 条（C4-001…C4-007）集中于 pipeline/pathQueues/reader 泵；本轮按派发单指定邻接面深挖，编号续接 C4-008。

## 深挖范围与方法

| # | 指定深挖面 | 核对方法与结论（file:line 为 live code） |
|---|---|---|
| 1 | 沙箱 exec 线程模型（心跳线程） | 心跳线程本体在 nop-ai-agent（超范围）；模块内对接面为 `BashExecutionTracker`（register:44-50 / progressSnapshot:63-75 / cancelExecution:82-98）与两个 sandbox 的 `progressSnapshot`/`cancelExecution` 委托（Host:134-142 / Docker:158-166）。逐行核对：tracker CHM 配对正确；心跳侧残留面（活 StringBuilder 竞态）已由第 1 轮 C6-008 立案；快照拷贝代价归 CL-019。本轮无新发现（取消路径的 remove-before-act 缺陷归 C6-009，见 C6 报告）。 |
| 2 | mavenvfs 构建线程 | 逐文件核实 `mavenvfs/**`：`VfsMavenCli.execute` 为 stub（VfsMavenCli.java:129-142，仅拼装命令列表返回，注释明示不执行）；全包 grep `Thread|Executor|ProcessBuilder` 零命中。**mavenvfs 无任何线程创建点**，第 1 轮清单表第 7 行结论复核成立。 |
| 3 | 后台 job 线程组生命周期 | `BashExecutor.doExecuteBackground`（BashExecutor.java:180-257）：body 经 `IBackgroundTaskRegistry.run` fork 到 registry 专用后台执行器（接口 javadoc :30-37 明示"off the foreground dispatch pool"，且**接口契约自带安全网**——"the fork completes the task as FAILED when the body throws without completing"），body 内 catch(BashSandboxException) 之外的异常由该契约兜底为 FAILED 完成，**不构成本模块缺陷**（与 C4-007 的 submit→run 无回滚是两回事，run 的 UOE 抛出点已由 C4-007 立案）。shell `&` 的 sink 生命周期：`IBackgroundJobSink` javadoc 明示 body"may throw — the sink maps the throwable to a FAILED completion"（:33-34）且 close() 不取消 job 为声明设计（ShellCommandExecutor.java:636-641 注释）——均为已声明边界。 |
| 4 | FutureHelper 取消链覆盖面 | **发现 C4-008**。全根 grep `appendOnCancel|removeOnCancel|isCancelled|getCancelToken`：`ICancelToken` 消费点**全三根唯一命中 `JqToolExecutor.java:40-47`**；`isCancelled` 零命中。17 个 executor 的取消能力逐一对账见 C4-008 失效面。平台事实：`ExecutorHelper.submit → FutureHelper.bindCancel`（第 1 轮 C4 误报排除已核实）使直发路径的 `future.cancel(true)` 可中断工作线程——但该绑定在排队路径被断链（见 C4-008）。 |
| 5 | 未覆盖线程站点全清单复核 | 全三根 grep `new Thread|Executors.|newFixedThreadPool|newCachedThreadPool|supplyAsync|runAsync|commonPool|parallelStream|ScheduledExecutor|Timer|ForkJoin`（逐命中核对）：① HostBashSandbox.java:92（reader 裸线程，C4-001 已立案）；② DockerBashSandbox.java:107（同型，已立案）；③ ShellCommandExecutor.java:263（pipeline 阶段 supplyAsync(…, executor)，C4-003/004 已立案）；④ ShellCommandExecutor.java:60（缺省回退 globalWorker，C4-004 已立案）。**无未登记的线程/执行器站点**；nop-ai-tools 零线程创建点复核成立；三根无定时调度器（`scheduleAtFixedRate` 族零命中）复核成立。 |

---

## 新增发现

### [C4-008] 取消链覆盖面：仅 jq 接线 cancelToken；排队路径的手工 result future 与执行链断链——取消对排队/执行中的调用静默无效

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:104-119`（断链点：手工 result future）；`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/JqToolExecutor.java:40-47`（全根唯一 cancelToken 接线点）；平台契约 `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/ICancelToken.java:11-25`
- **证据片段**:
  ```java
  // ToolManagerImpl.java:104-119 —— 返回给调用方的 result 与执行链无取消绑定
  CompletableFuture<AiToolCallResult> result = new CompletableFuture<>();
  pathQueues.compute(pathKey, (k, tail) -> {
      ...
      return trigger.thenCompose(x -> executeNow.get()
              .whenComplete((r, err) -> { ... result.complete(r); })   // 单向：链 → result
              .thenApply(r -> (Void) null));
  });
  return result;                     // result.cancel(true) 不触及 executeNow 链
  // JqToolExecutor.java:40-47 —— 全根唯一 cancelToken 消费；排队期监听器尚不存在
  ICancelToken cancelToken = context.getCancelToken();
  if (cancelToken != null) {
      Consumer<String> onCancel = reason -> future.cancel(true);
      cancelToken.appendOnCancel(onCancel);        // 仅在该调用轮次到来时才注册
  ```
- **严重程度**: P2
- **并发场景**: 引擎派发线程调用 `callTool` 拿到 result future；会话取消方（用户中止/超时治理）在两处发出取消：(a) 对返回 future `cancel(true)`；(b) 触发 `context.getCancelToken()` 的 onCancel 事件。被取消对象是排队中（pathQueues 链上等待）或已在 executor 池上运行的工具调用。
- **触发时序**: (a) T1 调用 B 对某 path 排队（result 未完成）→ T2 引擎对 B 的返回 future 调 `cancel(true)`：`CompletableFuture.cancel` 只把 `result` 置为 cancelled 并向调用方**返回 true**——不向上游传播（CF 取消语义），`executeNow.get()` 链与底层 `executor.submit` 任务原样保留 → 轮次到来时 bash/jq **照常完整执行**（bash 前台可达 wallSeconds、后台 1h），`result.complete(r)` 对已取消 future 失效，调用方以为已取消、进程仍在跑。(b) T1' 取消事件在调用 B 尚在排队时触发：此刻 B 的 `executeAsync` 未运行，jq 的 `appendOnCancel` 监听器不存在（它在轮次到来时才注册，JqToolExecutor.java:45）；入口只查 `expireAt`（:64）不查 `isCancelled()`；其余 16 个 executor 根本不读 cancelToken → 已取消调用在轮次到来后无任何短路路径，全量执行。`ICancelToken` 接口契约（javadoc :19-22）只说"增加取消操作时的回调"，**未承诺对已取消 token 立即触发**——即便平台实现兜底了注册后立即补发，排队窗口内的取消仍无任何调用方可感知的短路。
- **现状**: 直发路径（非排队）依赖调用方对返回 future cancel → bindCancel 中断线程，bash/jq 有效（第 1 轮已核实）；排队路径引入手工 future 后该绑定被静默切断，且模块无任何 `isCancelled()` 复查兜底（全根零命中）。
- **风险**: 会话中止/超时治理对排队与在途工具调用静默失效：后台 bash（1h 帽）与 http（30s 超时）在取消后继续消耗线程与外部资源；调用方拿到 cancel=true 的假确认。与 C4-005（排队调用永不完成）、C1-001(c) 同一代码块但后果不同根（本条是**取消语义违约**，前两条是完成语义违约）。
- **建议**: (1) 排队分支给 result 挂取消联动：`result.whenCancelled → trigger.cancel(true)`（或在 compute 内对链挂 `cancelOnCancel`）；(2) `executeNow.get()` 内先 `if (context.getCancelToken() != null && context.getCancelToken().isCancelled()) return FutureHelper.success(buildErrorResult(...))` 统一短路；(3) 与 CL-012 的注册窗口一并修：注册前同步 `isCancelled` 补触发一次。
- **失效面枚举**（F3）：17 个 executor 取消能力全对账——`JqToolExecutor`（监听器+窗口缺陷 CL-012，本条 (b) 的排队盲区）；`BashExecutor`（:103-105 不读 cancelToken，依赖 future.cancel → waitFor 中断 → killTree，直发有效、排队被断链，本条 (a) 主证）；`HttpRequestExecutor`（:73-97 校验面在派发线程、`doExecute` 提交池上同步 `httpClient.fetch`，:97/:178，不读 cancelToken）、`GraphqlQueryExecutor`（:105-160 同族，fetchAsync 链，不读 cancelToken）、`SkillExecutor`/`ReadRefExecutor`/`AskOracleExecutor`/fs 族 10 个 executor（`context.getExecutor().submit` 直发，无 cancelToken 面，短任务暴露小）、`UpdateTodosExecutor`/`SearchEngineExecutor`（无 executor 提交或 fetchAsync，同前）。ToolManagerImpl 自身（callTool/executeSequential/executeParallel）均无 isCancelled 复查。全族状态已逐一登记。
- **信心水平**: 确定（(a) 为 CompletableFuture 规范语义推演、代码路径完整；(b) 的监听器缺席由全根 grep 证实，`ICancelToken` 契约沉默按保守口径计）
- **误报排除**: 非"平台设施自身重审"——断链点是本模块手工 future 的组合方式；非 C4-005 同根——本条主张"取消无效且返回假确认"，C4-005 主张"result 永不完成"，修复动作不同（取消联动 vs 异常完成）；非 CL-012 重复——CL-012 是 jq 单点的注册窄窗，本条是排队路径全族的结构性盲区（窗口=整个排队时长）。
- **复核状态**: 保留(P2)｜新发现——复核确认：(a)(b) 两面机制经 JDK 25 实测与全根 grep 复现（cancelToken 全三根唯一消费点 JqToolExecutor.java:40-47、isCancelled 零命中、ICancelToken 契约对已取消 token 无补发承诺）。**实证补充（欠报修正）**：直发路径返回 `executeAsync(…).thenApply(…)` 包装 future，实测 `wrapper.cancel(true)` 不传播上游（bindCancel 只绑定池 future↔FutureTask）——原条目"直发路径 bash/jq 有效"不准确：经 manager 返回 future 的取消在任何路径都不达池任务（bash 实际 kill 通道只剩 agent 心跳，与 WI 3.1 MA-08 的"进程级 kill 通道仅 heartbeat 一处调用"衔接）；缺陷面比原条目更宽，P2 维持（心跳通道存在 + 与 MA-08 同族避免双计 P1）。同 diff：CL-012（jq 注册窄窗）；协同：WI 3.1 MA-08、WI 2.1 api-core bindCancel 单点改造

---

## 维度集外线索（overlay）

（本轮 C4 深挖未产生 C2/C3/C5/C7/C9 新线索：后台 job 异常传播面经 `IBackgroundTaskRegistry.run`/`IBackgroundJobSink` 契约核实为已声明边界；线程站点清单经全根 grep 穷举无未登记站点。）

---

## 继续判定

本轮新增 1 条（P2×1：C4-008）。派发单五个深挖面中：心跳线程（模块内对接面已核）、mavenvfs（stub 零线程）、后台 job 生命周期（接口契约安全网 + 声明设计）、线程站点全清单（穷举复核，全部已立案）均收敛，唯一活跃面（FutureHelper 取消链覆盖）已立案为 C4-008——**新增为 P2 且各面有明确收敛证据**，输出 **终止信号**（C4 维度建议收敛，不再开第 3 轮）。

---

## 复核结论（WI 3.2 独立复核，2026-10-20）

- **C4-008：保留(P2)**。两面机制（排队路径手工 future 取消断链、排队窗口 cancelToken 盲区）经 JDK 实测与 grep 复现成立；复核实证补充一处**欠报**：直发路径经 ToolManagerImpl `thenApply` 包装后调用方 cancel 同样不达池任务，"直发 bash 有效"表述修正（详见该条复核状态行）——缺陷面更宽，判级不变。
- **深挖面收敛证据独立复核成立**：round2 的 cancelToken 唯一消费点、mavenvfs/nop-ai-tools 零线程、线程站点 4 站点穷举、无定时调度器四项结论均经复核员独立 grep 复现确认，无未登记站点。**终止信号确认，C4 维度收敛。**
- 全维度 8 条（C4-001…008）的判定表、同案命中清单、跨单元对照与最终保留项见 `C4-thread-pools.md`「维度复核结论」「最终保留项」两节（本文件不重复）。
