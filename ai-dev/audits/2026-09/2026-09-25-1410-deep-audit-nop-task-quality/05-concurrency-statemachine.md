# 维度 05：并发与状态机正确性（Concurrency & State Machine）— nop-task 深度审核

- **审核日期**: 2026-09-25
- **轮次**: 第 1 轮（初审）完成，待深挖与复核
- **基线**: `_tmp/audit-baseline.txt`（263 tests 全绿，不重跑）
- **口径**: live code；owner 文档已声明的边界（`docs-for-ai/03-modules/nop-task.md:56` 事务/幂等边界、first-terminal-wins 语义）不重复入账，只报**实现与文档承诺不一致**或**未声明**的部分

## 第 1 轮（初审）

### [维度05-01] 终态守卫半实现：`succeed()` 有 first-terminal-wins 守卫，`fail()` 没有；task 级守卫是三步非原子序列——文档承诺的"后到终态 driver 不覆写先到者"仅 best-effort

- **文件**: `nop-task-core/.../state/TaskStepStateBean.java:45-57`、`nop-task-core/.../step/TaskStepExecution.java:296-317`（异步 err 分支）、`nop-task-core/.../impl/TaskImpl.java:183-266`
- **证据片段**:
  ```java
  // TaskStepStateBean — 同一类内两个终态写入口不对称
  public void succeed(Object result, String nextStepId, ITaskRuntime taskRt) {
      if (isDone())          // ← 有守卫：已终态不覆写
          return;
      setResultValue(result);
      setStepStatus(TASK_STEP_STATUS_COMPLETED);
  }
  public void fail(Throwable exception, ITaskRuntime taskRt) {
      exception(exception);   // ← 无守卫：无条件覆写 exception（状态随后由调用方另行设置）
  }

  // TaskStepExecution FAILED driver（cancel 分支 :297-305 同构）— fail 与 setStepStatus 之间无 isDone 检查
  stepRt.getState().fail(err, taskRt);
  stepRt.getState().setStepStatus(TASK_STEP_STATUS_FAILED);   // 守卫只在 setStepStatus 内部
  saveTerminalStateIfDone(stepRt);

  // TaskImpl — check-then-act 三步非原子（4 个 driver 同构）
  if (skipTerminalOverwrite(taskState, TaskConstants.TASK_STATUS_COMPLETED))  // ①读 status 判断
      return;
  taskState.result(ret);                                    // ②写 result/exception（无守卫）
  taskState.setTaskStatus(TaskConstants.TASK_STATUS_COMPLETED); // ③再写 status
  taskRt.saveTaskState();                                   // ④落盘（乐观锁另算）
  ```
- **严重程度**: P2
- **现状**: owner 文档 `nop-task.md:66` 承诺终态语义 "first-terminal-wins：后到的终态 driver 不会覆写先到者…仅在日志留痕"。实现层面：① step 级 `succeed` 有守卫而 `fail` 无——已 KILLED/EXPIRED 的步骤可被后到 `fail()` 覆写 exception（随后 `saveTerminalStateIfDone` 落盘），resume 重抛的异常与状态标注不符（状态=KILLED、errorBeanData=另一错误）；② task 级 `skipTerminalOverwrite` 与后续 `setTaskStatus`/`saveTaskState` 之间无原子性，两线程可同时通过判定；③ `stepStatus/taskStatus/resultValue/exception` 均为无同步普通字段，跨线程读（kill 线程 vs 完成回调线程）依赖 CompletableFuture 链的隐式 HB。
- **风险**: 竞态窗口内出现 ①终态+错误 exception 的不一致快照（resume 诊断错误），②两个 driver 双双通过守卫后 last-writer-wins（与文档承诺相反），③task 行并发 save 触发乐观锁异常冒泡（见 05-02）。代码注释自证该竞态是预期场景（`TaskStepStateBean:46` "如 kill 竞态先到"、`TaskImpl:251-252` "cancel 路径先写 KILLED，异步完成的主流程 thenCompose 稍后成功返回"）。
- **建议**: `fail()` 对齐 `succeed()` 加 `isDone()` 守卫；task 级把"判定+写入"收敛为单个 `synchronized`/CAS 方法（status 用 `AtomicInteger`），`saveTaskState` 放守卫内；补 kill-vs-completion 并发回归测试（断言 exception 与 status 同源）。
- **信心水平**: 很可能（守卫不对称、三步序列、字段无同步均为直读确定；"实际触发"取决于 05-02 及完成链路的并发交错，代码注释表明团队已观测到该类竞态）
- **误报排除**: 非"终态守卫缺失"——守卫存在但只覆盖一半写入口，属实现与自己文档的偏差；非"无事务原子性"——owner 文档 `:56` 裁定的是**业务副作用与保存之间**的窗口（步骤须幂等），未豁免**状态字段自身**的 first-terminal-wins 承诺。
- **复核状态**: 未复核

### [维度05-02] 持久化模式下同一 step 行的并发 `saveState` 直接触发 ORM 乐观锁异常并冒泡为分支失败——owner 已知边界只覆盖"跨进程 fork+resume"，未覆盖运行期并发写

- **文件**: `nop-task-dao/.../DaoTaskStateStore.java:297-313`、`nop-persistence/nop-orm/.../GenSqlHelper.java:300-334`、`EntityPersisterImpl.java:487,505-515`、`docs-for-ai/03-modules/nop-task.md:56`
- **证据片段**:
  ```java
  // DaoTaskStateStore.saveStepState：先读后写，无版本处理/无重试
  NopTaskStepInstance entity = findStepEntity(taskInstanceId, stepPath);   // 每次全新读
  ... copyStepStateToEntity(state, entity);
  stepDao().updateEntityDirectly(entity);                                 // 直写
  // ORM update 语句强制版本条件（nop_task_step_instance 有 versionProp="version"，orm.xml:261-262）：
  //   UPDATE ... SET version = version + 1 WHERE ... AND version = ?
  // 影响行数 != 1 → checkUpdateResult → 抛 OrmException（乐观锁失败）
  ```
  ```java
  // owner 文档已知边界（仅此一条，且只讲跨进程 resume）：
  // "fork/fork-n 的并发分支共享同一 stepPath 行（无唯一索引），跨进程 fork+DB-resume 场景尚不可靠（暂缓实施）"
  ```
- **严重程度**: P2
- **现状**: `defaultSaveState=true` 时，forkN/loop 复用同一 stepPath 的并发分支、或 kill/timeout/完成双 driver（05-01）会对**同一行**执行"读→改→写"；第二个写入者因 `version` 条件不满足被 `checkUpdateResult` 抛 `OrmException`。该异常不经任何降级/重试，沿分支 promise 冒泡为**该分支失败**——与任务语义（限流/超时/正常完成）完全无关的持久化错误。内存 store（默认）无此问题，故测试与"内存跑得通"的场景均不暴露。
- **风险**: 落盘模式 + 并发结构（forkN/parallel/graph 多 driver）下出现随机 `ERR_ORM_*` 分支失败，排障指向 ORM 而非根因；若团队为消除该异常关闭 `versionProp`，则 05-01 的竞态彻底失去最后兜底。
- **建议**: ① `saveStepState` 捕获乐观锁冲突后重读-合并-重试（有限次）；② 同 runtime 内对 (taskInstanceId, stepPath) 行加 per-key 串行化（本地锁即可覆盖进程内并发）；③ 把"运行期并发写同 step 行"补进 owner 已知边界。
- **信心水平**: 确定（版本条件、影响行数检查、抛错路径、save 无重试均为直读；并发写同行为 05 已知结构 forkN 共享 stepPath——代码注释 `TaskStepRuntimeImpl:152-154` 自证）
- **误报排除**: 与 owner 已知边界**不重合**——文档条目是"行复用导致 resume 语义不可靠（跨进程）"，本条是"运行期并发写触发乐观锁异常（正常执行路径）"，触发条件、后果、修复面均不同。
- **复核状态**: 未复核

### [维度05-03] kill 对驻留 SUSPENDED 任务无可达驱动：SUSPENDED→KILLED 转移延迟到下次 resume 才发生，kill 调用却立即返回成功

- **文件**: `nop-task-core/.../impl/TaskRuntimeImpl.java:84-89`、`impl/TaskImpl.java:94-119,154-181`、`utils/TaskStepHelper.java:246-247`
- **证据片段**:
  ```java
  public void cancel(String reason) {
      super.cancel(reason);            // 仅置 cancel token + svcCtx.cancel
      if (svcCtx != null) svcCtx.cancel(reason);
  }                                    // ← 无任何 terminal driver 调用

  // TaskImpl 中全部4 个终态 driver（driveTaskFailed/Killed/Tok/Completed）只从 execute 的
  // catch 与 thenCompose 两个出口触发；任务已从 execute 返回（SUSPENDED，:159-160）后，
  // 这两个出口都已关闭——不存在能驱动 KILLED 的执行链。
  // 唯一后续触发点：resume 重新 execute → 第一个 checkNotCancelled(stepRt)（retry:247 等）抛取消异常
  ```
- **严重程度**: P3
- **现状**: 任务挂起后处于"无驱动在飞"状态；`cancel()` 只设置 token 即返回，DB 中 `nop_task_instance.status` 保持 `SUSPENDED(?)`。调用方拿到成功响应，但查询状态仍是挂起；若此后不再 resume，任务**永远**显示挂起而非已中止，超时/中止看板失真。首次真实状态变化发生在任意久之后的 resume 尝试。
- **风险**: 运维 kill 挂起任务后状态面板不更新，误判任务存活；批量 kill 清理场景下残留 SUSPENDED 行。另：token 设置后到 resume 前，若存在进程内 `appendOnCancel` 监听者残留（挂起时应已清理），回调也不会改变任务状态。
- **建议**: `TaskRuntimeImpl.cancel` 检测 `taskState` 处于 SUSPENDED 时直接驱动 KILLED + `saveTaskState`；或 owner 文档明确"挂起任务的 kill 在 resume 时生效"。
- **信心水平**: 很可能（cancel 无驱动、driver 出口仅两处为直读；"挂起时无监听者残留"未逐一验证各 wrapper 的 onCancel 清理）
- **误报排除**: 非"kill 全链路无测试"——`TestTaskKilledTimeoutResumeE2E` 覆盖的是**执行中** kill；挂起态 kill 无测试（见 06-06）。非文档化行为——owner 文档挂起章节未提及 kill 交互。
- **复核状态**: 未复核

### [维度05-04] 全局限流器/信号量存于有界 Caffeine 缓存（max=10000），淘汰在用实例 → permit 池分裂，maxConcurrency/速率短暂失效

- **文件**: `nop-task-core/.../impl/TaskFlowManagerImpl.java:57-61`、`TaskConfigs.java:12-17`、`nop-kernel/nop-commons/.../LocalCache.java:80-84`
- **证据片段**:
  ```java
  private final LocalCache<String, IRateLimiter> globalRateLimiters = LocalCache.newCache(
          "task-global-rate-limiter", CacheConfig.newConfig(CFG_TASK_MAX_GLOBAL_RATE_LIMITERS.get()));  // 默认 10000
  private final LocalCache<String, ISemaphore> globalSemaphores = LocalCache.newCache(
          "task-global-semaphore", CacheConfig.newConfig(CFG_TASK_MAX_GLOBAL_SEMAPHORES.get()));        // 默认 10000
  // LocalCache.buildCache → Caffeine.newBuilder().maximumSize(config.getMaximumSize())   ← 淘汰型有界缓存
  ```
- **严重程度**: P3
- **现状**: `<throttle global="true">` 的信号量是缓存值。缓存键超过 10000（`taskName:key` 粒度，key 可由表达式生成、高基数）或 Caffeine 驱逐策略换出后，**已持有 permit 的旧实例与新实例并存**：持有者向旧实例 release（自洽），新获取者拿新实例的 permit → `maxConcurrency=N` 被拆分为多个实例各自的 N → 实际并发超过配置值；rate limiter 同理，驱逐即令牌预算重置。02-18 的"首配置固化告警"只覆盖速率参数不覆盖驱逐。
- **风险**: 高基数 key 场景下并发闸门静默失效（资源打爆）；属低概率、难复现的配置依赖型缺陷。
- **建议**: 全局闸门不入有界缓存（改受控的强引用注册表 + 显式生命周期），或缓存对"仍有在用 permit"的条目禁用驱逐（`evictionListener` 时若有未释放 permit 记告警并重建时继承计数）。
- **信心水平**: 很可能（缓存类型、上限、Caffeine 淘汰语义确定；高基数 key 的实际暴露取决于 `keyExpr` 使用习惯）
- **误报排除**: 非"缓存本意就是近似"——信号量语义要求精确计数，驱逐直接破坏功能正确性而非仅精度。
- **复核状态**: 未复核

### [维度05-05] retry 计数三步（fail → increment → save）存在 crash 窗口，resume 后重试预算被重置

- **文件**: `nop-task-core/.../utils/TaskStepHelper.java:301-312`
- **证据片段**:
  ```java
  } catch (Exception e) {
      if (e instanceof CancellationException || e instanceof NopTaskCancelledException)
          throw NopException.adapt(e);
      state.fail(e, stepRt.getTaskRuntime());          // ① 写 exception（内存）
  }
  state.setRetryAttempt(retryAttempt + 1);              // ② 写计数（内存）
  stepRt.saveState();                                   // ③ 才落盘
  ```
- **严重程度**: P3
- **现状**: ①② 仅内存修改，③ 才持久化。进程在 ③ 之前崩溃/被杀（或 ③ 因 05-02 乐观锁失败），该轮 attempt 丢失；resume 加载的是上一轮落盘的（更小的） `retryAttempt`，重试预算相对 `maxRetryCount` 被重置，配合可无限产生瞬态异常的下游，理论重试次数可超出配置上限。另注意：③ 失败本身会抛出（乐观锁/DB 故障），此时 exception 已置但状态仍是 ACTIVE，落盘行与内存不一致。
- **风险**: 低概率（窗口位于失败分支、紧邻一次 DB 写），但违反"maxRetryCount 是硬上限"的直觉契约；崩溃恢复类缺陷排查成本高。
- **建议**: ①②③ 顺序改为"先增量落盘、再执行下一轮"（即 save 提到循环头 `checkNotCancelled` 之前的状态转换处），或把 attempt 增量合并进 `state.fail` 的同一次 save；③ 失败时至少 `LOG.error` 标记内存/DB 漂移。
- **信心水平**: 确定（顺序与字段语义直读；触发需进程级崩溃或 05-02，故定级 P3）
- **误报排除**: 非"无事务原子性"（owner `:56` 裁定的窗口是业务副作用 vs 保存）——本条是**引擎自身记账字段**的丢失，与业务幂等无关。
- **复核状态**: 未复核

### [维度05-06] `TaskStepRuntimeImpl` 字段可见性配置不对称：`cancelToken` 有 volatile + 专项注释，`stepState/outputNames/exception/persistVars` 无任何同步，跨线程正确性全押在 future 链隐式 HB 上

- **文件**: `nop-task-core/.../impl/TaskStepRuntimeImpl.java:37-49,93-97,163-177`
- **证据片段**:
  ```java
  // volatile（plan 349 Phase 4）：执行线程写…异步回调线程/超时定时器线程经 isCancelled() 读，
  // 需要跨线程可见性
  private volatile ICancelToken cancelToken;      // ← 有防护 + 显式注释
  private Set<String> outputNames;                // ← 无
  private ITaskStepState stepState;               // ← 无（保存/恢复的状态载体本体）
  private Throwable exception;                    // ← 无（TryTaskStepWrapper 跨线程写）
  private List<Runnable> stepCleanups;            // ← add 是 synchronized，run 不是（见 02-10）
  ```
  ```java
  // ITaskStepRuntime 默认方法在任意调用线程读 state：
  default String getStepPath() { return getState().getStepPath(); }
  // 调用方之一：TaskStepHelper.checkNotCancelled 的 LOG.warn（可能在定时器/异步回调线程）
  ```
- **严重程度**: P3
- **现状**: 同一个类里，团队明确知道跨线程可见性问题并只修复了 `cancelToken` 一字段。`getState()`/`getStepPath()`/`getRunId()` 是无锁读非 volatile 字段，`setException`（TryTaskStepWrapper:45,67）在包装层写、恢复逻辑读。当前这些读写多数被 CompletableFuture 注册/完成的同步点间接覆盖（HB 链成立），但该保证是**结构性巧合**而非代码内约束——任何新增的"不经 future 链"的读（定时器日志、metrics 上报、新 wrapper）都可能读到过期值，且编译器/JIT 语义上无禁止。
- **风险**: 防线脆弱：plan 349 已经为一个字段打了补丁，其余同类字段依赖未文档化的 HB 推理；后续维护者极易引入真正不可见的读。
- **建议**: `stepState` 至少加 volatile（它是所有默认读方法的根）；`exception`/`outputNames` 同判；或在类头 javadoc 写明"除 cancelToken 外所有字段仅允许经 future 链访问"的不变式。
- **信心水平**: 确定（字段声明与注释对照直读；"当前无实际竞态"的判断依赖 HB 推理，故不定高）
- **误报排除**: 不是假想问题——同文件 37-39 注释证明该类真实发生过可见性缺陷并修复了一处，其余字段同一暴露面。
- **复核状态**: 未复核

### [维度05-07] graph 的 `runningCount==0` 终结判定是分离的 racy read——代码注释自证曾因同类窗口误判 `ERR_TASK_GRAPH_NO_ACTIVE_STEP`，修复靠调序而非原子判据

- **文件**: `nop-task-core/.../step/GraphTaskStep.java:210-215,269-271,329-340`
- **证据片段**:
  ```java
  // 出口 A（waitFuture 错误分支，:213-215）：无任何计数保护直接判零
  stepFuture.completeExceptionally(err);
  if (runningCount.get() == 0 && !future.isDone())
      future.completeExceptionally(noActiveStepError(stepRt));

  // 成功分支自证调序修复（:330-334 注释）：
  // "先触发后继级联再减计数：…若先减计数，并发完成窗口内会出现瞬态 runningCount==0
  //  （本节点已减、后继未加），被下方检查误判为 ERR_TASK_GRAPH_NO_ACTIVE_STEP（图未结束但无活跃步骤）"
  ```
- **严重程度**: P3
- **现状**: 判定模式是 `读计数 → 判零 → complete`，与计数的增减分离；正确性依赖每个出口的**人工调序约定**。成功路径已发生过一次真实误判并以调序修复（注释记录），错误分支（:213）沿用同一分离读模式：节点失败 → `decrementAndGet`（:272）→ `completeExceptionally` 触发下游同步回调 → 回调内读 count——此时若另一分支处于"已减未加"的窗口（与被修 bug 同构的互补方向），仍可能在图有活跃步骤时读到 0。`TestGraphDrainRace`（275 行）覆盖了部分交错，但对"错误分支 × 并发成功分支"的组合未见针对性用例。
- **风险**: 概率低（窗口极窄、需错误与成功回调交错），但一旦命中，正常图被以"无活跃步骤"错误终结，属数据相关的随机失败，排障困难。
- **建议**: 判零收敛为单个方法 `tryCompleteNoActiveStep()`（内部 `runningCount.updateAndGet(c -> c==0 ?...` 或对"注册-计数"用同一把锁/同一原子协议），消除对调序的依赖；补错误分支×并发分支的交错测试。
- **信心水平**: 很可能（模式与已修 bug 同构为直读结论；新交错路径是否实际可达未穷举证明）
- **误报排除**: 已修案例证明该误判模式**真实发生过**而非理论洁癖；本条报的是同一模式在错误出口的残留，非重复报告已修问题。
- **复核状态**: 未复核（阶段二请对照 `TestGraphDrainRace` 已有用例做缺口分析）

## 检查范围清单（第 1 轮）

### 读过的并发关键路径
- **驱动/终态**: `TaskImpl`（execute 两出口、4 driver、skipTerminalOverwrite、suspend 分支）、`TaskStepExecution`（同步/异步两出口、cancel/failed/succeed driver、saveTerminalStateIfDone）、`TaskStepStateBean` 全类、`ITaskStepState` 接口
- **运行时/取消**: `TaskRuntimeImpl`（cancel、attrs、computeAttributeIfAbsent）、`TaskStepRuntimeImpl` 全类（volatile 注释、first-instantiation CHM、newStepRuntime）、`TaskStepHelper`（timeout 竞速 :185-241、retry :244-313、checkNotCancelled、getCancelReason）
- **结构并发**: `GraphTaskStep`（execute/runStep/buildWaitFuture/initFutures/makeResults 全读）、`ParallelTaskStep`、`AbstractForkTaskStep`、`ForkTaskStep`、`LoopTaskStep/LoopNTaskStep`、`SequentialTaskStep`
- **共享资源**: `TaskFlowManagerImpl`（globalRateLimiters/globalSemaphores/newRunId）、`LocalCache` 实现（Caffeine maximumSize）、`DaoTaskStateStore`（读-写序列）、ORM 版本机制（GenSqlHelper update WHERE version、EntityPersisterImpl.checkUpdateResult）
- **文档基线**: owner 文档事务模型/终态语义/挂起恢复/已知边界全文（`nop-task.md:40-95`）

### 零发现项
1. **属性表**: `TaskRuntimeImpl.attrs` 为 `ConcurrentHashMap`，`computeIfAbsent` 原子 ✓
2. **fork 并发实例化**: `isFirstInstantiation` 用 `ConcurrentHashMap.newKeySet().add` 保证原子（代码注释明确）✓
3. **cancel 可见性**: `cancelToken` volatile + plan 349 专项修复 ✓（残留问题已单列 05-06）
4. **parallel 聚合**: promises 列表在派发线程构建，异步子步仅经 `whenComplete` 补充，无共享可变集合写冲突 ✓
5. **graph 共享集合**: `stepResults` 为 ConcurrentHashMap；`stepFutures`/`errorConsumers` 构造完成后只读，且发布经 future 注册同步点（HB 成立）✓
6. **retry 延迟**: 走 `scheduledExecutor`，不阻塞线程、不自旋 ✓
7. **timeout 竞速**: `winner` 用 CompletableFuture 原子 complete，先完成者胜，无双驱动 ✓
8. **suspend 传播**: sequential/selector/loop/fork/graph 各路径有专项测试（`TestSuspendContract` 6 用例 + `TestSuspendSemantics`）✓
9. **cancel 传播**: `TestChildRuntimeCancelPropagation`（49 行）+ `withCancellable` 注册对称（含 plan349 自引用 bug 修复 :186-190）✓
10. **owner 已裁定边界（不入账）**: 步骤副作用与保存无事务原子（文档 :56）、fork 跨进程 resume 不可靠（文档 :56）、同步步骤阻塞语义（xdef 注释）
