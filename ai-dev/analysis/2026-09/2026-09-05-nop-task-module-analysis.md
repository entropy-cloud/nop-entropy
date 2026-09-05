# nop-task 模块质量分析：bug、易用性与语法定义改进

> Status: open
> Date: 2026-09-05
> Scope: nop-task 全部 11 个子模块（core/api/dao/service/web/meta/queue/ext/codegen/app）+ task.xdef 语法定义
> Conclusion: 引擎单机执行链路经多轮修补已较完整，但存在三条系统性主题缺陷——①SUSPEND（挂起）语义未在框架层契约化，共 8 处实现点各自以不同方式破坏挂起；②持久化恢复（saveState/resume）核心承诺在 stateBean 不落库、SUSPEND 误驱动为 COMPLETED、DB 状态码与字典错位三个点上断裂；③DSL 表达力属同类第一梯队但存在命名不一致、新旧语法并存、无任务级生命周期 API 等易用性短板。建议按报告末尾的优先级序列立项修复。

## Context

- 分析动机：评估 nop-task 模块（可替代 Spring Batch / 轻量工作流的异步任务编排引擎）的当前质量，回答三个问题：是否存 bug、使用是否方便、语法定义是否需要改进。
- 分析基线：当前工作区（master 分支 + 2 处未提交修复：`TaskImpl.java` metrics 判空守卫、`GraphTaskStep.java` runningCount 先级联后减计数）。两处未提交修复经复核均正确且必要。
- 方法：3 个专项审查（step 实现并发审查 / 状态持久化与上层模块审查 / API 可用性审查）+ 主会话对关键断言逐条回读源码验证。与既有两轮全仓审计（`ai-dev/audits/check/nop-task.md` 已处置 14 条、`ai-dev/audits/check2/nop-task.md` 未处置 19 条）交叉比对，区分新发现与已知问题。
- 所有发现均标注证据位置（文件:行号），关键 P0 已由主会话独立复核。

---

## 一、现状概述

### 1.1 模块结构

| 子模块 | 完成度 | 简评 |
|---|---|---|
| nop-task-core | 高（引擎本体） | 状态机驱动、cancel/timeout 分类、终态 driver、异常序列化恢复成体系（plan 252-266 痕迹明显）；致命伤集中在 SUSPEND 契约与恢复完整性 |
| nop-task-ext | 中 | 5 个 decorator（retry/timeout/ratelimit/txn/orm-session）短小可用；reliability 测试是全模块最扎实部分 |
| nop-task-dao | 中 | DaoTaskStateStore 功能完整且有 round-trip 测试；缺唯一约束、缺并发控制、状态码与字典错位 |
| nop-task-api / meta | 中 | codegen 生成物齐全 |
| nop-task-service | 低（空壳） | 4 个 BizModel 全是裸 CrudBizModel，无任何任务生命周期逻辑 |
| nop-task-web | 低（脚手架） | 仅自动生成 AMIS CRUD 页 |
| nop-task-queue | 空壳 | 唯一源文件 `ITaskQueue.java` 仅一个无参空方法 `enqueueTask()`，无实现无调用方 |
| nop-task-codegen / app | 开发工具/启动器 | 非产品功能 |

### 1.2 审计历史（本轮分析之前）

- `check/nop-task.md`（2026-08-19，P0×3 P1×5 P2×4 P3×2）：全部处置完毕。11 条已修复（commit c3dc34df53、e6010dc6eb），2 条暂缓（stateBean 持久化、continuation-skip outputs 重放——合并为"DB 断点续跑完整性"设计主题），1 条维持现状（fork 聚合 key 用 index 系结构必然）。
- `check2/nop-task.md`（2026-08-23，P0×2 P1×6 P2×6 P3×5）：**全部未处置**（plan 346 中 nop-task.md 未勾选）。其中 P1「TaskImpl 异常出口 metrics 未判空」在当前工作区已有未提交修复。
- 未提交的 `GraphTaskStep` runningCount 修复不在任何审计清单中，系更新的独立发现，已复核正确。

---

## 二、Bug 分析

### 2.1 本轮新发现（两轮审计均未覆盖）

#### [P0-N1] DB 状态码双体系数值错位：引擎写入值经字典/UI 解读后全部错位

- 证据：
  - 引擎全链路（写入 DB 的值）使用 `TaskConstants`（`nop-task-core/.../TaskConstants.java:79-91`）：ACTIVE=10、COMPLETED=30、KILLED=40、FAILED=50、TIMEOUT=60。
  - 展示层使用 `dict/task/task-status.dict.yaml` + ORM 列绑定 `ext:dict="task/task-status"`（`nop-task-dao/.../_app.orm.xml:185`）：SUSPENDED=10、ACTIVATED=30（执行中）、COMPLETED=40、EXPIRED=50、FAILED=60、KILLED=70。
  - 同一引擎内部亦混用：task 级终态用 `TaskConstants`（`TaskImpl.java:175,186,216,226`、`ITaskState.isTerminal()`），step 级终态却用与字典对齐的 `_NopTaskCoreConstants`（`TaskStepExecution.java:273-286` FAILED=60/EXPIRED=50/KILLED=70、`TaskStepStateBean.java:43,56-64`）。
- 后果：一个**已完成**的任务实例（引擎写 30）在所有经 dict 的展示/过滤/GraphQL 消费方显示为「执行中」；**已中止**（40）显示为「已完成」；**已失败**（50）显示为「已超时」；**已超时**（60）显示为「已失败」；运行中（10）显示为「已暂停」。step 级初始 ACTIVE=10（`DaoTaskStateStore.java:206,241,390`）同样被显示为「已暂停」，仅终态值碰巧对齐。引擎自身语义自洽（`isTerminal()` 用 TaskConstants），破坏面在一切 DB/持久化消费方。
- 修复方向：以 ORM 字典为准重构 `TaskConstants` 状态段（生成常量 `_NopTaskCoreConstants` 与字典已经对齐，说明 40/50/60/70 体系是后定的"正解"），并补一条 `TaskConstants` 与 `_NopTaskCoreConstants`/dict 数值一致性断言测试。

#### [P0-N2] SUSPEND 语义未契约化：`isSuspend()` 对象身份判断导致 8 处实现点各自破坏挂起

- 根因：`TaskStepReturn.isSuspend()` 是 `this == SUSPEND` 身份判断（`TaskStepReturn.java:211-213`），而 `RETURN(nextStepName, outputs)` 工厂（:59-63）不按 `STEP_NAME_SUSPEND` 归一化。任何包装层用 `RETURN(...)` 重建返回值都会把挂起"洗掉"。`TaskStepReturn.of`（:121-122）已有归一化，证明这是 `RETURN` 的遗漏而非设计。
- 8 个破坏点（全部在代码中确认）：

| # | 位置 | 破坏方式 |
|---|---|---|
| 1 | `TaskImpl.java:159`（check2 P0-2，同根因） | task 层无 `isSuspend()` 分支，挂起被 driveTaskCompleted 置 COMPLETED 终态并持久化，之后 resume 被终态短路永久失效 |
| 2 | `BuildOutputTaskStepWrapper.java:51` | 声明了 outputs 的步骤挂起时被 `RETURN(res.getNextStepName(), result)` 重建为普通返回 → 步骤被标 COMPLETED + 父层抛 ERR_TASK_UNKNOWN_NEXT_STEP |
| 3 | `TaskStepHelper.retry`（:256-266 sync、:276-289 doRetry） | 挂起返回被 `state.succeed(...)` 标成 COMPLETED 终态，resume 时 continuation-skip 命中，挂起的子流程永久跳过 |
| 4 | `SequentialTaskStep.java:79-92` | 同步分支有 suspend 检查（:62），异步 thenApply 分支没有 → 异步挂起抛 ERR_TASK_UNKNOWN_NEXT_STEP |
| 5 | `SelectorTaskStep.java:103-110`（check2 P2，同根因） | 异步挂起被当 falsy 静默跳到下一候选 |
| 6 | `AbstractForkTaskStep.java:101-105` | `if (stepResult.isSuspend()) return stepResult; return stepResult;` 两个分支返回值相同——删除处理逻辑后遗留的死代码，挂起被当成功加入聚合 |
| 7 | `ParallelTaskStep.java:68-69` / `ForkTaskStep.java:68-69` / `ForkNTaskStep.java:48-49` | 挂起分支的（已完成态）promise 参与聚合，join 正常返回 |
| 8 | `GraphTaskStep.java:233-239` | 挂起节点被记为无 outputs 的"成功"结果且 `stepFuture.complete(null)` 级联后继继续跑 |

- 修复方向（一句话可闭合 80%）：`isSuspend()` 改为按 `STEP_NAME_SUSPEND.equals(nextStepName)` 判断（身份判断改为值判断），随后各容器补齐 SUSPEND 短路。挂起是任务引擎的一等公民语义（`<suspend>` 是一等步骤类型），值得一个专门的契约测试套件（对每种容器 × 同步/异步 × 挂起做矩阵测试）。

#### [P1-N3] GraphTaskStep waitErrorSteps 在"被等待步骤成功"时 waitFuture 永不完成，图挂死

- 位置：`GraphTaskStep.java:128-137`（`GraphStepNode.buildWaitFuture` 的 waitError 回调仅 `err != null` 时才可能 complete）。
- 触发：任何节点声明 waitErrorSteps 而被等待步骤实际成功结束 → 后继节点永不执行，图既不成功也不失败。与 check2 P1「错误路径使 onError 边不可达」互补：该条是失败时错误边不可达，本条是成功时连正常边也挂死——错误分支特性当前在两个方向上都是坏的。
- 修复方向：waitError 语义应为"对方失败我才跑，对方成功则按跳过完成"，成功分支在计数归零时也 complete。

#### [P1-N4] ExecutorTaskStepWrapper 取消后返回 promise 永不完成，任务挂死

- 位置：`ExecutorTaskStepWrapper.java:33-47`。`ret`（返回给上层的 future）与被提交的 `future` 之间仅靠 `bindCancelToken`（取消 → `future.cancel(false)`）；future 被取消时 callable 永不运行，`ret` 无任何 complete 路径 → 上层续延永不触发。
- 修复方向：为 future 注册 whenComplete，`isCancelled()` 时 `ret.completeExceptionally(NopTaskCancelledException)` 兜底。

#### [P1-N5] timeout 只取消 token、不强制终结返回 promise，超时后依旧可能挂死

- 位置：`TaskStepHelper.timeout`（:181-212）+ `TimeoutTaskStepWrapper.java:19-24`。超时到期仅 `cancellable.cancel(TIMEOUT)`；若被包装步骤不检查 token 且 promise 永不完成（叠加 N4 即成必现），任务在超时后依旧挂死，TIMEOUT 终态永远不被驱动。附带：TimeoutTaskStepWrapper 完成后不恢复 stepRt 原 cancelToken（对照同文件 `withCancellable:311` 有恢复）。
- 修复方向：建立不变量"取消/超时必须使返回 promise 在有限时间内终结"，超时触发时同步 `completeExceptionally` 兜底；wrapper 恢复外层 token。

#### [P1-N6] nextStepNameOnError 与终态 FAILED driver 互相矛盾：resume 后错误处理分支不可达

- 位置：`TaskStepExecution.java:285-296`。失败时先落 FAILED 终态再 `return buildErrorResult(...)` 走错误续延；一旦 suspend→resume（或步骤重入），continuation-skip 命中 `isDone() && !isSuccess()` 直接重抛异常，错误处理分支再也不会执行。plan 254 的 FAILED driver 与 nextOnError 特性未对齐。
- 修复方向：走 nextStepNameOnError 时不落终态（或落"已处理"中间态），由错误处理结果决定终态。

#### [P1-N7] 终态可被覆盖：task/step 驱动与存储两层均无终态守卫

- 位置：`TaskImpl.driveTask*`（:173-228）无条件 setTaskStatus+save；`TaskStepStateBean.succeed()`（:41-44）无条件置 COMPLETED；`DaoTaskStateStore.saveTaskState`（:130-194）upsert 不检查行已是终态。
- 触发：异步完成与 cancel/kill 并发——cancel 先写 KILLED，主流程 thenCompose 稍后成功返回即用 COMPLETED 覆盖。
- 修复方向：driver 前检查 `isTerminal()`；存储层条件更新。

#### [P1-N8] task 级输入与 taskVars 不持久化，恢复即丢失

- 位置：`DaoTaskStateStore.saveTaskState`（:130-194）只存 name/version/status/result/exception；`ITaskRuntime.java:34` 注释明确 "taskVars保存需要持久化的Task级别的状态变量"，实际无持久化通路。进程崩溃后 resume 时调用方输入与 taskVars 静默丢失。

#### [P1-N9] 事务装饰器存在"副作用已提交、终态未保存"的非原子窗口

- 位置：`nop-task-ext/.../TransactionalTaskStepWrapper.java:26-38`——事务只包住步骤体；终态 driver + `saveTerminalStateIfDone` 在事务外（`TaskStepExecution.java:289,321`）。业务事务提交后、终态 save 前崩溃 → DB 留 ACTIVE 行 + 已提交副作用 → resume 重执行步骤体 → 副作用重复。
- 修复方向：终态 save 纳入同一事务，或文档化"步骤体必须幂等"为硬契约。

#### [P2-N10] 其余新发现（低危/边界，列出备查）

| 位置 | 问题 |
|---|---|
| `ForkTaskStep.java:54,60-64` | producer 求值为 null 时 `CollectionHelper.toList(null)` 返回 null → `new ArrayList<>(items.size())` NPE（LoopTaskStep:130 有同款守卫，fork 漏了） |
| `ForkNTaskStep.java:42-56` | count=0 → `AsyncHelper.waitAsync` Guard 抛 IAE；负数 → `new ArrayList<>(count)` 抛异常 |
| `LoopNTaskStep.java:217 vs 224` | until 满足时异步路径返回 END、同步路径返回 CONTINUE——同一模型仅因同步/异步而语义不同；且 exit 分支（:198-199,213-214）丢弃 body 的 exit outputs，与 LoopTaskStep:161/176 不一致 |
| `TaskStepHelper.retry:267-272` | 取消异常被当作一次失败计入 retryAttempt 并先落 FAILED 状态，才在循环顶抛出 |
| `SleepTaskStep.java:26-33` | 阻塞调用线程（100ms 轮询 Thread.sleep），被取消后返回 CONTINUE 而非取消异常，吞掉取消 |
| `GraphStepBuilder.java:13-28` + `GraphStepAnalyzer.java:45-107` | 不校验模型显式声明的 waitSteps/waitErrorSteps 存在性、不校验步骤名唯一性 → 拼错名字运行期 NPE（真实 typo 位置丢失）、重名时计数错乱 |
| `TaskStepRuntimeImpl.java:33` | cancelToken 字段非 volatile，执行线程写/回调线程读无 happens-before |
| `MultiStepResultBean.java:29-32` | setter 语义为 putAll 合并而非替换，@DataBean 反序列化时旧数据残留 |
| `TaskStepReturn.of:114-118` | returnValue 自带 nextStepName 时静默丢弃调用方显式传入的 nextStepName |
| `DefaultTaskStateStore.newMainStepState:24-31` | 不设置 taskInstanceId/stepInstanceId（同文件 newStepState:34-49 有设置），行为不一致 |
| `DaoTaskStateStore.java:150-161,404-414` | resultValue 序列化失败或为 null 时保留旧列值，重试失败后旧行仍带旧结果 JSON（展示层误导） |
| `TaskConstants.java:87` | `TASK_STATUS_HISTORY_BOUND` 无任何使用方（死常量） |
| `TaskFlowManagerImpl.java:149-159` | `getTaskStepLib/loadTaskStepLibFromPath` 无生产调用方，lib 路径复用 MODEL_TYPE_TASK 未经验证 |

### 2.2 已知未处置问题（check2，plan 346 待清账）

check2 的 19 条（P0×2 P1×6 P2×6 P3×5）经本轮复核**基本仍然存活**，要点与状态：

- **P0**：suspend 步骤 NPE（`TaskStepExecution.java:252` `metrics.endStep(meter,false)` 无判空，已复核仍存活）；SUSPEND 被驱动为 COMPLETED（同 2.1 P0-N2 #1，同一根因）。
- **P1**：TaskImpl metrics 判空（**当前工作区已有未提交修复**，复核正确）；BuildOutput 篡改 SUSPEND（同 2.1 P0-N2 #2）；GraphTaskStep 错误路径 onError 边不可达；fork 分支同 stepPath 并发写同一行（无唯一约束、无并发控制）；continuation-skip 不重放 outputs；stateBean 未持久化（见下）。
- **P2**：persistVars 全链路死代码（xdef 属性名还是拼错的 `persisVars`）；newChildRuntime 取消传播自引用；Sequential/Selector 异步 suspend（同 2.1）；retry 对 SUSPEND 调 succeed（同 2.1）；resume 路径 metrics 未初始化。
- **P3**：resetGlobalStats 漏限流器；全局限流器参数固化；TaskFlowAnalyzer if/then/else 非递归遍历；DaoTaskStateStore 无 beans.xml 注册（生产无入口，见 2.3）；ParallelTaskStep 聚合静默丢弃未完成分支。

### 2.3 恢复（resume）能力的总体评价

"可从任意步骤中断并恢复执行"（task.xdef 头注释）是模块的核心承诺，但当前实现与承诺存在系统性落差：

1. **stateBean（continuation 闭包状态）从不持久化**（`DaoTaskStateStore.copyStepStateToEntity` 只 round-trip resultValue）。后果逐项确认：CallTaskStep 恢复时子任务 taskId 丢失、重新创建全新子任务实例执行（旧实例成孤儿、副作用重复——这是两轮审计均未发现的加重情节）；If/Choose 恢复时条件重算可能换分支；Loop/LoopN 恢复时 index 重置 0 从头重跑（且 saveStepState 按 stepPath upsert 单行，只有最后一次迭代的 body 行存活）。此问题 check/check2 各记过一条（P1，均已裁定"暂缓、合并立项"），本轮证实其影响面比原判更大，建议升级处置优先级。
2. **SUSPEND 全链路断裂**（2.1 P0-N2）：挂起本身是恢复的前提场景，当前挂起 → COMPLETED → 永不可恢复。
3. **状态码错位**（2.1 P0-N1）：跨进程消费方（另一个进程从 DB 读状态做调度决策）读到系统性错误的终态。
4. 缺失的基础设施：task 输入/taskVars 不持久化（P1-N8）、终态无守卫（P1-N7）、步骤副作用与终态保存无原子性（P1-N9）、(taskInstanceId, stepPath) 无唯一索引且 fork 分支并发写同一行。

**结论：当前 saveState/resume 只对"平铺 sequential + 同步步骤 + 单值输出"这一窄面真正可靠；含 loop/fork/call-task/if/choose/suspend 的任务在恢复场景下存在副作用重复或状态错误的真实风险。** 这与模块单机执行链路的高完成度形成反差，是质量画像中最需要正视的一点。

---

## 三、易用性分析

### 3.1 定义并运行一个任务（现状流程）

1. 在 VFS `/nop/task/<任务名>/v<版本>.task.xml` 放置模型文件（`task.register-model.xml` 注册 6 种格式）。
2. 运行有三种入口：
   - **Java API 三步仪式**：`getTask(name, version)` → `newTaskRuntime(task, saveState, svcCtx)` → `task.execute(taskRt).syncGetOutputs()`；
   - **xbiz 声明式绑定（最省）**：`<mutation name="callTask" task:name="test/DemoTask"/>`，input/output 自动映射 GraphQL；
   - **xpl 标签库**：`<task:Execute taskModelPath=... inputs=... outputNames=.../>`。
3. 测试：`AbstractTaskTestCase.runTask(name)` 一行跑任务。

DSL 表达力本身属同类第一梯队（仓库内对比结论，见 References）：23 种步骤类型、声明式 decorator（retry/timeout/throttle/rate-limit/catch/validator 全部 XML 配置）、断点恢复、XDSL 继承/Delta 定制，均为独有或领先优势。

### 3.2 易用性痛点（按影响排序）

1. **Java API 缺一站式入口**：新手必须理解 getTask → newTaskRuntime → execute → syncGetOutputs 链条，`saveState`/`svcCtx`/`scope` 参数对新手是噪音。`task:Execute` 标签已证明封装可行，建议补 `executeTask(name, inputs)` 便捷重载。
2. **service 层没有生命周期 API**：`NopTaskInstanceBizModel` 等 4 个 BizModel 全是空壳 CrudBizModel，xbiz 全部空 `<actions/>`。没有 start/resume/kill 受控入口，实例与步骤实例表反而可被持有通用 CRUD 权限的用户直接改 status/删终态行，绕过本就薄弱的状态机。`NopTaskDefinition` 表与引擎的模型加载（走 VFS 资源）完全脱节，表里的 UNPUBLISHED/PUBLISHED/ARCHIVED 状态无人消费。**这是"引擎能力"与"产品能力"之间最大的断层。**
3. **魔法变量约定无 schema 化**：`RESULT`（sequential 自动透传、测试基类硬编码断言 `RESULT=="OK"`）、`STEP_RESULT.b`（单步）与 `STEP_RESULTS.x.outputs.RESULT`（图/并行，单复数两个名字两种取值路径）全是"看不见的 schema"，task.xdef 未声明。
4. **测试基类与断言耦合**：`AbstractTaskTestCase.runTask`（:41）硬编码断言 RESULT=="OK"，所有测试任务被迫写 `return ... ? 'OK' : 'FAIL'`，无法直接测期望失败/特定输出的任务；且 `new TaskFlowManagerImpl()` 手工构造（:33），ext 测试注释自己承认应由 IoC 管理。
5. **文档覆盖尚可但分散、有陈旧片段**：`docs-for-ai/03-modules/nop-task.md`（233 行）质量高；`docs/dev-guide/workflow/task-flow.md` 的"通用配置"片段仍是旧版 xdef（含已不存在的 `ignoreResult`），且未覆盖 if/selector/fork-n/suspend 专章；无步骤类型×属性速查表，用户只能读 xdef 或 20+ 个测试 fixture。
6. **常用结构缺示例**：测试 fixture 有 20+ 目录但没有 if/selector/suspend 专属示例——新手最常用的 if 反而要自己猜写法。
7. **无可视化**：仅 AMIS CRUD 页，无流程拓扑渲染（graph 依赖分析器已有，只读渲染成本不高）。
8. **写法样板偏多**：与 solon-flow 等对比（仓库内已有对比文档），简单场景必须 `<steps>` 包裹、每步必写 name、input/output 显式声明——表达力换来了啰嗦。

### 3.3 用户会期望但缺失的功能

| 期望功能 | 现状 | 预留程度 |
|---|---|---|
| 结构化 `<try>` 步骤 | 仅每步 `<catch>/<finally>/<allowFailure>` | TryTaskStepWrapper 已存在，只差 xdef 暴露 |
| map/reduce 语法糖 | 靠 fork+aggregator 组合 | aggregator 机制已通用，成本低 |
| 动态超时 `timeoutExpr` | timeout 是静态 long（delay/sleep 却都是表达式） | 未预留，加属性即可 |
| HTTP/RPC 内置调用节点 | invoke/invoke-static 仅本地 bean/静态方法 | 未预留 |
| 事件/消息订阅步骤 | 仅 onEnter/onReload + decorator | nop-message 已有 IMessageService，缺声明式绑定 |
| 运行中任务控制面（暂停/跳步/改变量） | 仅 cancel | 未预留 |
| 可视化设计器 | 无 | 未预留（只读渲染可先行） |

**已预留但零文档的强能力**（建议写进 docs-for-ai）：`graphql:operationType` 步骤直出 GraphQL、`<flags match/enable/disable/rename>` 按 flag 启停步骤、`throttle/rate-limit` 的 `global="true"` 跨实例全局限流、根节点 `graphMode` 整任务直接按 DAG 跑、`x:extends` 任务继承。

---

## 四、语法定义（task.xdef）改进建议

### 4.1 应当修复的定义缺陷

1. **`persisVars` 拼写错误被固化为公共 API**（task.xdef:32，应为 persistVars）：且整个 persist 通路是死代码（check2 P2），建议实现或删除该属性并在文档修正承诺。
2. **同类属性单复数不一致**：input `role="string"`（:50）vs output `roles="csv-set"`（:64）——且两者在非生成代码中均无消费者（本轮 grep 验证），属未接线的装饰属性。
3. **input/output persist 默认值相反且无注释**：input `persist="!boolean=true"`（:51）vs output `persist="!boolean=false"`（:62）。在持久化通路本身是死代码的前提下，这两个默认值会误导用户以为变量级持久化存在。
4. **retry 注释与语义矛盾**：task.xdef:95-96 注释写"如果发生异常，则重试整个task"，实际是单步骤重试包装。
5. **`executor` 与 `bean` 两个 bean-name 属性含义不同**：executor 是线程池（TaskFlowManagerImpl.getThreadPoolExecutor），`<simple bean>` 是 ITaskStep bean。建议改名 `threadPool` 或在注释中显式区分。

### 4.2 一致性整理建议（不破坏兼容的前提下）

1. **同功能多写法收敛**：`<step>` / `<xpl>`（Deprecated 但全部测试仍在用，官方示例与弃用指引自相矛盾）/ `<custom customType>`（Deprecated）三者并存。建议给出行稳定迁移路径并让测试先行迁移。
2. **循环/分支四兄弟参数形状对齐**：fork（varName+producer）/ fork-n（countExpr 无 varName）/ loop（itemsExpr/maxCount）/ loop-n（beginExpr/endExpr/stepExpr）四种形状，属性名约定（varName/indexName 位置、有无默认值）宜统一。
3. **`exit` vs `end` 命名**：exit=退出 sequential/loop、end=退出整个任务，命名未体现作用域层级（`break`/`return` 类比更直白）；至少补强注释。
4. **choose 与 if 的分支建模不一致**：if 是内联 then/else 块；choose 的 case 是 `to="stepName"` 引用跳转（goto 风格），case 本体为空。同层级的两种分支建模让用户需要记两套心智模型；可考虑给 case 增加内联 body 的可选形式。
5. **超时/延迟能力不对称**：timeout 静态 long，delay/sleep 用表达式。加 `timeoutExpr` 成本极低。
6. **作用域开关散布 3 层 5 个**：`useParentScope`（step）/`fromTaskScope`（input）/`toTaskScope`（output）/`exportAs`（output）/`defaultUseParentScope`（task），加 choose case 强制共享父 scope 的隐式规则——缺一张作用域决策表。
7. **input/output 三种等价写法**（显式子节点 / `in:` 属性 / `in:` 子节点，经 InOutNodeTransformer）灵活但混用时校验报错难定位。

### 4.3 图模式（graph）语法

- 根节点 `graphMode/enterSteps/exitSteps` 与 `<graph>` 步骤双入口设计合理，`next`→wait 边、`STEP_RESULTS.x.error` 数据依赖推导错误边的设计有想象力。
- 但当前错误边运行期两个方向都是坏的（2.1 P1-N3 + check2 P1），且 waitSteps/waitErrorSteps 拼错名字无构建期校验（P2-N10）。**语法设计领先于实现可靠性**——建议要么补齐实现，要么在 xdef 注释与文档中明确错误边为实验性。

---

## 五、修复优先级建议

1. **P0 立即修复**（行为正确性，且多有现成测试模式可循）：
   - SUSPEND 契约化（isSuspend 改值判断 + 8 个破坏点短路 + 容器×同步/异步×挂起矩阵测试）——一并收口 check2 两条 P0 与 BuildOutput/retry/Sequential/Selector/fork/parallel/graph 各点；
   - 状态码双体系对齐（TaskConstants 以字典为准重构 + 一致性断言测试）；
   - suspend 步骤 metrics NPE（check2 P0-1，一行守卫）。
2. **P1 短期修复**：GraphTaskStep waitError 成功路径挂死 + check2 错误边不可达（同文件一并修）；ExecutorTaskStepWrapper/timeout 的"取消必须终结 promise"不变量；nextOnError 与 FAILED driver 的矛盾；终态守卫；提交 plan 346 工作区中已有的两处未提交修复（先补红测试）。
3. **立项设计**（已有暂缓裁定合并主题，本轮证据支持升级）："DB 断点续跑完整性"——stateBean/outputs/taskVars/inputs 持久化 + fork 分支行标识 + 唯一索引 + 事务原子性 + 状态码字典对齐，应作为单一设计文档统一定案，避免逐条打补丁。
4. **产品化补课**：service 层生命周期 action（start/resume/kill）+ 关闭实例表的通用 mutation；DaoTaskStateStore 的默认装配或官方集成说明（当前 DB 持久化全链路只能被测试触达）。
5. **文档与语法清理**（低成本高感知）：修正 task-flow.md 陈旧片段；生成"步骤×属性×作用域"速查表（以 xdef 为单一生成源）；补 if/selector/suspend 示例；xdef 五处定义缺陷修正；文档化 flags/graphMode/graphql:operationType 等零文档能力。

## Conclusion

- nop-task 的引擎分层设计（ITaskStep / ITaskStepExecution / wrapper / TaskStepReturn 统一同步异步）清晰，DSL 表达力在同类中属第一梯队，近期 plan 252-266 的终态 driver/cancel reason/持久化 wiring 工作质量高。
- 但**三条系统性主题缺陷**使其当前不具备生产可靠性：SUSPEND 语义未契约化（8 处破坏点）、恢复承诺在含循环/分支/子任务/挂起的任务上不成立（stateBean 等不落库 + SUSPEND 断裂 + 状态码错位）、上层产品能力（生命周期 API/持久化装配/可视化）基本空缺。
- 被否决的方向：不建议逐条零散修复恢复类缺陷——check/check2 两轮已出现同位置重复发现（stateBean、continuation-skip outputs 各被记两次），应一次性设计定案。
- 后续工作：P0 修复建议立 plan（可复用 check2 处置流程）；恢复完整性建议先出 design 文档（`ai-dev/design/`）再立 plan；语法清理可并入 docs 补全任务。

## Open Questions

- [ ] 状态码对齐方向需平台裁定：以 ORM 字典（40/50/60/70 体系，`_NopTaskCoreConstants` 生成常量已对齐）为准重构 `TaskConstants`，还是改字典？前者动引擎常量涉及 plan 258-260 已落库语义，后者动 meta/dict 展示层——建议前者（生成常量证明字典体系是后定正解），但涉及已持久化数据的兼容迁移。
- [ ] `TaskConstants` 是否属于受保护的框架核心（nop-kernel 之外的 core 引擎常量）？对齐重构涉及生成文件 `_NopTaskCoreConstants` 的上游源（dict/constants 模型），需确认生成链路后再动手。
- [ ] fork/parallel 分支内的 suspend 语义应如何定义：向上传播挂起并停止派发剩余分支（推荐），还是不支持（xdef 层显式禁止分支内 suspend）？需要设计决策。
- [ ] nop-task-queue 空壳模块是否应删除或补实（当前 `ITaskQueue.enqueueTask()` 无参无实现无调用方）。

## References

- 审计报告：`ai-dev/audits/check/nop-task.md`（已处置）、`ai-dev/audits/check2/nop-task.md`（19 条待处置）
- 处置计划：`ai-dev/plans/344-check-audit-p1-p2-p3-remediation.md`、`ai-dev/plans/346-check2-audit-p0-p3-remediation.md`
- 持久化/恢复系列设计：`ai-dev/plans/252` ~ `266`（终态 lifecycle、resume 编排、异常持久化）
- 语法定义：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef`
- 模块文档：`docs-for-ai/03-modules/nop-task.md`
- 对比分析：`docs/compare/nop-task-flow-vs-solon-flow.md`、`ai-dev/analysis/2026-05-18-juggle-vs-nop-task-comparison.md`、`ai-dev/analysis/2026-07/2026-07-24-nop-module-matrix.md`
