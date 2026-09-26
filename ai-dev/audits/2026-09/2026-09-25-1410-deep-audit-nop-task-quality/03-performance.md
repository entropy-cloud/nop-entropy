# 维度 03：性能（Performance）— nop-task 深度审核

- **审核日期**: 2026-09-25
- **轮次**: 第 1 轮（初审）完成，待深挖与复核
- **基线**: `_tmp/audit-baseline.txt`（263 tests 全绿，不重跑）
- **口径**: live code 静态分析；生成文件仅在追根到源模型/模板时引用

## 第 1 轮（初审）

### [维度03-01] `nop_task_step_instance` 无二级索引，步骤状态读写的 4 次 `findFirstByQuery` 全部无索引扫描

- **文件**: `nop-task/model/nop-task.orm.xml:259-374`（NopTaskStepInstance 实体块，全文无 `<indexes>`）、`nop-task/deploy/sql/mysql/_create_nop-task.sql:80-115`、`nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:316-324`
- **证据片段**:
  ```java
  protected NopTaskStepInstance findStepEntity(String taskInstanceId, String stepPath) {
      if (StringHelper.isEmpty(taskInstanceId) || StringHelper.isEmpty(stepPath))
          return null;
      QueryBean query = new QueryBean();
      query.addFilter(FilterBeans.eq(PROP_NAME_taskInstanceId, taskInstanceId));
      query.addFilter(FilterBeans.eq(PROP_NAME_stepPath, stepPath));
      return stepDao().findFirstByQuery(query);   // → OrmEntityDao:513 → orm().findFirst(sql)
  }
  ```
  ```sql
  -- _create_nop-task.sql:80-115：该表全表无任何 KEY / INDEX 声明（grep "KEY |INDEX" 仅命中 114 行 PK）
  ```
- **严重程度**: P2
- **现状**: `nop_task_step_instance` 只有 `STEP_INSTANCE_ID` 主键，无 `(TASK_INSTANCE_ID, STEP_PATH)` 或至少 `TASK_INSTANCE_ID` 二级索引；`findStepEntity` 生成的 `WHERE TASK_INSTANCE_ID=? AND STEP_PATH=? LIMIT 1` 只能顺序扫描。
- **风险**: 表行数 M = 历史累计 step 实例数（无按时间/状态清理，`grep "NopTaskStepInstance" × (delete|purge|clean)` 零命中，仅靠 `nop_task_instance.steps` 的 `cascadeDelete` 联动）。随机 UUID 主键 → 期望扫描 ~M/2 行；乘上 03-02 每步 4 次该查询，10 步任务 ≈ 44 次无索引查询；M=10⁵ 时单次任务约 2.2×10⁶ 行检视，随业务量单调恶化。
- **建议**: 源模型 `NopTaskStepInstance` 加 `<indexes>`（参照 `nop-wf/model/nop-wf.orm.xml:474-483` 的 `IX_WF_STEP_*`），改完重新生成 DDL。
- **信心水平**: 确定（三口径：源模型 grep `indexes` 零命中；部署 SQL 区间仅 PK；nop-wf 有 `<indexes>` 证明平台支持）
- **误报排除**: `docs-for-ai/03-modules/nop-task.md:56` 已知边界只声明**无唯一索引**（正确性问题），与本条**查找性能无索引**是不同问题；`nop_task_instance` 走 PK 不涉及。
- **复核状态**: 未复核

### [维度03-02] 持久化模式下每个 step 生命周期 7 次同步 DB 往返（4 SELECT + 3 写），其中 4 次为无索引查询、3 次写为全列直写

- **文件**: `nop-task-core/.../impl/TaskStepRuntimeImpl.java:144-146`、`step/TaskStepExecution.java:245-257,415-419`、`step/SequentialTaskStep.java:76`、`nop-task-dao/.../store/DaoTaskStateStore.java:297-313`
- **证据片段**:
  ```java
  // TaskStepRuntimeImpl.java:144-146 — 首次实例化即查库（非 resume 也查）
  if (isFirstInstantiation(stepName)) {
      newState = stateStore.loadStepState(stepState, stepName, stepType, taskRt);  // 1× SELECT
  }
  // TaskStepExecution.java:245-257 步骤开始：stepRt.saveState();   // 1× SELECT + 1× INSERT
  // TaskStepExecution.java:415-419 终态：stepRt.saveState();        // 1× SELECT + 1× UPDATE
  // SequentialTaskStep.java:76 每子步完成回写：stepRt.saveState();   // 1× SELECT + 1× UPDATE
  // DaoTaskStateStore.java:297-312 — 每次 save 先 SELECT 再 INSERT/UPDATE，类上无 @Transactional
  ```
- **严重程度**: P2
- **现状**: 每个 `ITaskStepExecution` 生命周期产生 4 次 `findStepEntity`（SELECT）+ 3 次写，全部内联同步串行；每次写都是"先查后写"，`updateEntityDirectly` 为**全列直写**、不走脏检查部分更新。
- **风险**: 双口径一致：7 次 DB 往返/step。10 叶子步 + 1 顶层 sequential 的任务 ≈ 7×11+任务级 3 ≈ **80 次往返**，其中 44 次是无索引查询；局域网 DB 单次往返 0.5–1ms 时纯状态开销 40–80ms/任务且随步数线性增长；3 次全列 UPDATE 放大 redo/WAL 与 `stateBeanData`(VARCHAR 4000) 行宽。
- **建议**: ① `findStepEntity` 合并/缓存（同一 runtime 3 次查同一条记录）；② ACTIVE 与终态两次 save 延迟/合并；③ `updateEntityDirectly` 改按变更列更新；④ 评估 `@main` 进度回写频率。
- **信心水平**: 确定（双口径交叉验证，行号可复现）
- **误报排除**: 仅在 `defaultSaveState=true` 时生效，默认 `task.xdef:9` 为 false → 默认零 DB，**不是"每步无条件写库"**；owner 文档已声明"非原子性是设计"——本条报的是未文档化的往返次数与全列写量级。
- **复核状态**: 未复核

### [维度03-03] 每次 `saveStepState` 全量序列化 stateBean/outputs/persistVars，循环步骤每迭代一次 → O(K²) 序列化

- **文件**: `nop-task-dao/.../store/DaoTaskStateStore.java:446,490-517`、`nop-task-core/.../step/LoopTaskStep.java:148-171`
- **证据片段**:
  ```java
  protected String serializeStepStateData(ITaskStepState state) {
      Map<String, Object> wrapper = new LinkedHashMap<>();
      wrapper.put(STATE_DATA_VERSION_KEY, STATE_DATA_VERSION_VALUE);
      wrapper.put("resultValue", state.getResultValue());
      wrapper.put("stateBean", state.getStateBean(Object.class));   // LoopStateBean 含全部 items
      wrapper.put("outputs", state.getOutputs());
      wrapper.put("nextStepName", state.getSavedNextStepName());
      wrapper.put("persistVars", state.getPersistVarsSnapshot());
      String json = JsonTool.serialize(wrapper, false);             // 第 1 次全量序列化
      if (json != null && json.length() <= 4000) return json;
      ... json = JsonTool.serialize(minimal, false);                // 超长时第 2 次序列化
  }
  // LoopTaskStep.java:155-157（每迭代一次）：stateBean.incIndex(); stepRt.setBodyStepIndex(0); stepRt.saveState();
  ```
- **严重程度**: P2
- **现状**: 序列化粒度是整个步骤状态 wrapper（含 `LoopStateBean.items` 全列表、outputs 全量、persistVarsSnapshot 全量）；循环每完成一个 item 就全量序列化 + 全列 UPDATE。
- **风险**: K 次迭代 × O(S)（S ∝ K）→ **总序列化 O(K²)**：K=1000 条 items×100 字节 → 约 1000 次×100KB ≈ 100MB 字符串序列化纯 CPU，外加 1000 次无索引 SELECT + 1000 次全列 UPDATE。wrapper >4000 字符时每次 save **两次**序列化 + `LOG.warn`；降级只留 `resultValue` 意味着大 stateBean 不被持久化（正确性隐患，交其他维度）。
- **建议**: ① loop 类步骤增量持久化（只写 index/游标）；② state 未变更时跳过重序列化；③ 先做尺寸预检再决定降级。
- **信心水平**: 确定（代码路径直读，K→O(K²) 为算术推导）
- **误报排除**: 序列化次数与字节数可由循环次数直接推导；`stateBeanData` 列宽 4000 使超长降级成为常态路径。
- **复核状态**: 未复核

### [维度03-04] `TaskStepStateBean.getStateBean(Class)` 对 Map 形态每次都 `serialize + parseBeanFromText` 且不写回，resume 后的异步循环每迭代重复转换

- **文件**: `nop-task-core/.../state/TaskStepStateBean.java:203-215`
- **证据片段**:
  ```java
  public <T> T getStateBean(Class<T> beanType) {
      if (stateBean == null)
          return null;
      if (beanType.isInstance(stateBean))
          return (T) stateBean;
      if (stateBean instanceof Map) {
          return (T) io.nop.core.lang.json.JsonTool.parseBeanFromText(
                  io.nop.core.lang.json.JsonTool.serialize(stateBean, false), beanType);
      }   // ↑ 不 setStateBean(结果)，字段仍是 Map
      return (T) stateBean;
  }
  // LoopTaskStep.java:121 与 :182 — 异步分支每迭代重新进入 execute()，重调 getStateBean
  ```
- **严重程度**: P3
- **现状**: Map 分支每次都做完整 `serialize + parseBeanFromText` 往返，结果不写回 `stateBean` 字段，同一 runtime 后续调用重复执行同样转换。
- **风险**: 触发条件是跨进程/crash resume（`parseStepStateData` 把 stateBean 置为 Map）；resume 后 `LoopTaskStep` 异步路径每迭代重新进入 `:121`，而 `:157/:171 saveState()` 又从仍是 Map 的字段取值 → K 次迭代 K 次转换，每次序列化 items 为 O(K) → CPU 侧 O(K²)（与 03-03 叠加）。同步路径每次 `execute()` 只 1 次，故 P3。
- **建议**: Map 分支命中后 `this.stateBean = converted` 写回（一行改动）；同时确认 `serializeStepStateData` 取到写回后的最新对象。
- **信心水平**: 很可能（重复转换路径确定；"K 次进入"依赖异步循环体返回 promise 前提）
- **误报排除**: 已区分快路径（`isInstance` 命中，非 resume 场景零开销）与 Map 慢路径，仅 resume 后特定序列退化，故 P3。
- **复核状态**: 未复核

### [维度03-05] 步骤级 `saveState` 属性无任何运行时消费者，写放大无法按步收敛

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:4,127,138`、`nop-task-core/.../utils/TaskGenHelper.java:14-19`、`impl/TaskFlowManagerImpl.java:92-93`
- **证据片段**:
  ```xml
  <!-- task.xdef:4（xdef 头注释，公开契约） -->
  支持异步执行的轻量化任务引擎。持久化状态为可选特性，如果在步骤上配置了saveState，则可以从任意步骤中断并恢复执行。
  <!-- task.xdef:127 -->
  @saveState 是否需要持久化状态用于失败后重新执行本步骤时的状态恢复
  ```
  ```java
  // TaskGenHelper.java:17-18 — 只读 task 级 defaultSaveState，不读 step 级 saveState
  "const taskRt = taskFlowManager.newTaskRuntime(task," + taskFlowModel.isDefaultSaveState() + ",svcCtx);\n"
  // TaskFlowManagerImpl.java:93 — 运行期唯一开关
  ITaskStateStore stateStore = saveState ? requirePersistStateState() : nonPersistStateStore;
  ```
- **严重程度**: P2
- **现状**: `TaskStepModel.saveState`（xdef 声明 + 头注释明确承诺）全仓库**零消费者**——`grep "getSaveState"` 仅命中生成物 `_TaskStepModel.java:301/433/459`；`ReflectionTaskStepBuilder:94 step.setSaveState(true)` 只写不读；持久化开关只有任务级 `defaultSaveState` 与调用方参数。
- **风险**: 写放大不可调：任务一旦 `defaultSaveState=true`，`TaskStepExecution:257/:418` 对**每一个** step 无条件写库，无法按 xdef 承诺"只在需断点恢复的步骤持久化"→ 03-02 的 7 次往返/步唯一减半手段实际不存在，优化空间钉死在任务级二元开关。
- **建议**: ① 在 `TaskStepExecution`/`TaskStepRuntimeImpl` 消费 `stepModel.getSaveState()`（显式 false 跳过 save）使契约落地；或 ② 若设计意图本就是任务级粒度，从 xdef 头注释删除 per-step 承诺。
- **信心水平**: 确定（两组独立 grep 零读取方，唯一写入方也被命中）
- **误报排除**: 发现指向 xdef 源模型 + 手写消费链缺失，非"标记生成物"；也非"设计上只做任务级"——xdef 把 per-step 写成了公开能力。本条同时具契约漂移属性，可能被正确性维度以更高严重度收录。
- **复核状态**: 未复核

### [维度03-06] `parallel`/`fork`/`fork-n`/`graph` 默认在调用线程内联串行执行子步，无 executor 时"并行"不产生并行

- **文件**: `nop-task-core/.../step/ParallelTaskStep.java:63-78`、`step/AbstractForkTaskStep.java:97-103`、`step/GraphTaskStep.java:274`、`builder/TaskStepEnhancer.java:111-113`
- **证据片段**:
  ```java
  // ParallelTaskStep.java:66-77 — for 循环内联调用，未提交任何线程池
  Supplier<CompletionStage<Void>> action = () -> {
      for (int i = 0, n = steps.size(); i < n; i++) {
          ITaskStepExecution step = steps.get(i);
          try {
              TaskStepReturn stepResult = step.executeWithParentRt(stepRt);   // 同步子步 → 串行
              promises.add(stepResult.getReturnPromise());
          } catch (Exception e) { promises.add(FutureHelper.reject(e)); }
      }
      return AsyncHelper.waitAsync(promises, stepJoinType);
  };
  // TaskStepEnhancer.java:111-113 — executor 是唯一离场通道，且需显式配置
  if (!StringHelper.isEmpty(stepModel.getExecutor())) {
      step = new ExecutorTaskStepWrapper(step, stepModel.getExecutor());
  }
  ```
- **严重程度**: P3
- **现状**: 四种结构都在调用线程内联执行；只有每个子 step 单独配 `executor="bean-name"` 才提交线程池。全同步子步的 `parallel` 实际等价于顺序执行；`graph` 的 `ITaskStepExecution.executeAsync` 默认实现对同步步骤就是同步执行。
- **风险**: N 个同步子步放进 `parallel` → 时延 = Σ(子步时延)，用户预期的并行加速为 0；影响所有把耗时同步 IO 步骤放进 `parallel` 的模型。
- **建议**: 在 `docs-for-ai/03-modules/nop-task.md` 控制结构表明确补"`parallel`/`fork`/`graph` 只聚合 promise，子步需配 executor 才真正并行"；或引入 step 级默认 executor。
- **信心水平**: 很可能（机制三处源码直读确定；"是设计意图而非缺陷"未找到文档明示）
- **误报排除**: 本维度担心的"误用共享 ForkJoinPool"已确认**不存在**（未配置 executor 不触碰任何线程池）；本条报"默认不并行 + 文档未说明"的预期落差。
- **复核状态**: 未复核

### [维度03-07] `SleepTaskStep` 用 100ms `Thread.sleep` 轮询阻塞工作线程，而同模块 `DelayTaskStep` 已给出非阻塞实现

- **文件**: `nop-task-core/.../step/SleepTaskStep.java:34-41`、`nop-kernel/nop-api-core/.../util/FutureHelper.java:496-516`
- **证据片段**:
  ```java
  // SleepTaskStep.java:34-41
  public TaskStepReturn execute(ITaskStepRuntime stepRt) {
      Long sleep = ConvertHelper.toLong(sleepMillisExpr.invoke(stepRt));
      if (sleep == null) sleep = -1L;
      if (sleep <= 0) return TaskStepReturn.CONTINUE;
      FutureHelper.waitUntil(stepRt::isCancelled, sleep);   // 阻塞当前线程直到超时或被 cancel
      TaskStepHelper.checkNotCancelled(stepRt);
      return TaskStepReturn.CONTINUE;
  }
  // FutureHelper.java:496-516 — 固定 100ms 轮询粒度：while (!test.getAsBoolean()) { Thread.sleep(100); ... }
  ```
- **严重程度**: P3
- **现状**: `sleep` 步骤在整个 sleep 期间占住执行线程并以固定 100ms 轮询；同模块 `DelayTaskStep:30-43` 已用 `getScheduledExecutor().schedule(...)` + `ASYNC` 实现零线程占用的等价语义。
- **风险**: N 个并发 `sleep` = N 条线程被占 N 毫秒（100 并发 30s sleep = 100 线程×30s 不可用）；`sleepMillis=30000` 产生约 300 次 100ms 唤醒；与 03-06 交互——即便配了 executor 线程池，`sleep` 也把池线程按 sleep 时长吃掉。
- **建议**: 改为与 `DelayTaskStep` 相同的调度器 + ASYNC 返回（cancel 语义用 `FutureHelper.bindCancelToken`）；或至少把 100ms 轮询换成按剩余时长调度。
- **信心水平**: 很可能（阻塞机制与对照实现均直读；未压测量化线程占用）
- **误报排除**: `task.xdef:210-211` 注释明示"调用Thread.sleep阻塞当前线程"，**阻塞本身是 schema 层明示设计不报**；报的是①固定 100ms 轮询而非调度式完成，②同模块已有非阻塞实现而语义差异未在 owner 文档区分。
- **复核状态**: 未复核

### [维度03-08] `TaskExceptionRegistry.resolveReflective` 成功命中时每次都重做 `Class.forName` + 最多 4 次构造尝试，无正向缓存

- **文件**: `nop-task-dao/.../store/TaskExceptionRegistry.java:78-81,104-105,126-163`
- **证据片段**:
  ```java
  private void registerReflective(String fqcn) {
      registeredFqcns.add(fqcn);
      factories.put(fqcn, (errorCode, cause) -> resolveReflective(fqcn, errorCode, cause));  // 每次都重新反射
  }
  // :104-105 — 只缓存"失败"结果（reflectiveMissing），成功结果不缓存
  // :126-155 — 成功路径每次 create() 都走：Class.forName(fqcn) + 最多 4 次 tryConstruct
  ```
- **严重程度**: P3
- **现状**: `factories` 的 value 是每次调用都执行 `resolveReflective` 的 lambda；`reflectiveMissing` 只缓存缺类/构造失败，成功解析出的 `Constructor` 不落缓存。
- **风险**: 每个被恢复的异常都要付一次类加载查找 + ≤4 次反射构造查找；单次微秒级，仅在高频 resume/批量恢复场景累积（恢复 10⁴ 个失败步骤 → 10⁴×4 次构造器查找）。绝对量小故 P3。
- **建议**: `factories` 缓存成功解析的 `Constructor<?>`（与 `reflectiveMissing` 对称的正向缓存）。
- **信心水平**: 确定（代码路径直读，正/负缓存不对称是明确事实）
- **误报排除**: 该类自己已实现负缓存（`:51` 注释明确），说明作者已意识到重复反射成本，只是漏了正向半边；`Class.forName` 走 `NopException` 子类（部署期已加载）无类初始化副作用。
- **复核状态**: 未复核

## 检查范围清单（第 1 轮）

### 必读文档
`_tmp/audit-shared-prefix.md`、`_tmp/audit-baseline.txt`、`docs-for-ai/03-modules/nop-task.md`（owner 文档全文 270 行：事务模型、挂起/恢复、已知边界、控制结构表）、`docs-for-ai/02-core-guides/dql-query.md`、`concurrency-and-transactions.md`、`01-repo-map/{module-groups,domain-module-pattern}.md`、`INDEX.md`

### 扫描过的代码（按检查项）
| 检查项 | 覆盖与结论 |
|---|---|
| DB 访问/N+1 | `DaoTaskStateStore` 850 行方法枚举；全模块 `grep "QueryBean\|findAllByQuery\|queryEntityByDql"` 仅 1 处 QueryBean；service 层无自定义查询 |
| 索引/查询计划 | 源模型 `indexes` 0 命中；部署 SQL 仅 PK；对照 nop-wf 有 `<indexes>`；`findFirstByQuery` → 真实 SQL 非内存过滤 |
| 清理/数据增长 | `NopTaskStepInstance` delete/purge/clean 0 命中；仅 cascadeDelete |
| 读-改-写事务 | `DaoTaskStateStore` 无 `@Transactional`；owner 文档声明默认复用 ambient 事务 → 非原子性为文档化设计不报 |
| 线程池 | `TaskStepEnhancer` 全装配点；`executor` 默认空；**未发现 commonPool/默认池误用** |
| 线程阻塞 | `Thread.sleep`/`.get()`/`join()`/`CountDownLatch` → 命中 `FutureHelper.waitUntil`（SleepTaskStep）；`DelayTaskStep`/`timeout` 用调度器非阻塞；`sync=true` 阻塞已文档化不报；RateLimit/Throttle 用 park 非自旋 |
| 忙等/轮询 | `FutureHelper:500-516` 100ms 轮询（已报 03-07）；`GraphStepAnalyzer` 嵌套 for 为构建期一次性不报 |
| 锁竞争/锁内 IO | `newRunId` synchronized 短临界区；`addStepCleanup` synchronized 短；`TaskFlowModel` lazy init synchronized；cancelToken volatile → **全部无锁内 IO，零发现** |
| 内存泄漏 | metrics 固定 tag 无基数爆炸；`globalRateLimiters/Semaphores` 为有界 LocalCache（默认 10000）；stepCleanups 生产恒空；attrs 有界于任务生命周期 |
| 序列化/拷贝 | `serializeStepStateData`（已报 03）、`getStateBean`（已报 04）；`copyStepStateToEntity` 并入 02；`ErrorBean` 构建仅异常分支非热路径 |
| saveState 频率枚举 | `grep "stepRt.saveState();" step/*.java` → CallTaskStep:86、ChooseTaskStep:61、ForkTaskStep:59、IfTaskStep:53、LoopNTaskStep:195/210、LoopTaskStep:157/171、SelectorTaskStep:55/79/94/116、SequentialTaskStep:76/93、TaskStepExecution:257/279/418 —— 除 loop 每迭代一次外其余状态首次变化一次；`isDone()` 守卫避免重复提交 |

### 已核查排除的潜在发现
1. `GraphStepAnalyzer` 嵌套循环 O(n²) — 构建期一次性，随模型缓存
2. `TaskExceptionRegistry` DB 查询 — 实测纯内存 ConcurrentHashMap 无 DB（仅保留 03-08 反射缓存）
3. `TaskStateStore` 未在 beans.xml 注册 — 已确认 opt-in，默认 `DefaultTaskStateStore` 零 DB → 所有 DB 发现均标注触发条件
4. `sync=true` 时 wrapper 阻塞持事务/会话 — xdef 文档化 opt-in 语义
5. `TimeoutTaskStepWrapper` — 调度器竞速非阻塞
6. `updateEntityDirectly` 无乐观锁版本检查 — 属正确性/并发维度
7. `stateBeanData` >4000 降级致 stateBean 丢失 — 正确性问题（owner 文档 :56 裁定暂缓），本维度仅引用其性能事实
8. service 层 CrudBizModel、`I*Biz` 于 dao、`@Inject protected` — 平台标准模式
