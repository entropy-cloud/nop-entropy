# 维度 02：设计坏味道（Design Smells）— nop-task 深度审核

- **审核日期**: 2026-09-25
- **轮次**: 第 1 轮（初审）完成，待深挖与复核
- **基线**: `_tmp/audit-baseline.txt`
- **口径**: live code；生成物排除；计数双口径交叉验证

## 第 1 轮（初审）

### [维度02-01] `<simple bean="X">` 步骤共享 IoC 单例，`initAbstractStep` 原地覆写导致跨步骤配置串扰

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepBuilder.java:152,177,378-380,408-415`
- **证据片段**:
  ```java
  private AbstractTaskStep buildSimpleStep(SimpleTaskStepModel taskStepModel) {
      return (AbstractTaskStep) BeanContainer.instance().getBean(taskStepModel.getBean());
  }
  private void initAbstractStep(TaskStepModel stepModel, AbstractTaskStep step) {
      step.setLocation(stepModel.getLocation());
      step.setInputs(stepModel.getInputs());
      step.setOutputs(stepModel.getOutputs());
      step.setConcurrent(stepModel.isConcurrent());
      step.setPersistVars(stepModel.getPersistVars());
  }
  // 使用方：test/next-jump-01/v1.task.xml:3,8,13 — <simple name="a|b|c" bean="plusOne" .../>
  ```
- **严重程度**: P2
- **现状**: `buildSimpleStep` 返回 IoC 容器同一 bean 实例，随后每个 stepModel 调 `initAbstractStep` 把该模型的 location/concurrent/persistVars 写进共享对象；后构建覆盖先构建。运行期确从共享 step 对象读取：`TaskStepExecution.java:185` 用 `step.getPersistVars()`/`isConcurrent()` 决定子作用域并发访问，`:427,441` 用 `persistVars` 决定落盘变量。
- **风险**: 两个 `<simple bean="same">` 步骤只要一个声明 `persistVars` 或 `concurrent="true"`，另一个静默继承/丢失；并发标志串扰把本应隔离的子 scope 变成可并发访问，直接引入数据竞争；`location` 串扰让错误堆栈指向错误步骤。
- **建议**: 共享实例后 `cloneInstance()`/新建包装（或 `DelegateTaskStep` 持共享实例、模型参数存到执行期 `TaskStepExecution`，与 inputs/outputs 现有做法对齐）。
- **信心水平**: 确定（共享实例 + 运行期读取点三方闭环；当前测试恰好未声明 persistVars/concurrent 故未暴露）
- **误报排除**: 非"看起来不优雅"——写入点、共享来源、运行期读取点均已定位；inputs/outputs 已按模型侧建副本，只有 concurrent/persistVars/location 漏在共享对象上。
- **复核状态**: 未复核

### [维度02-02] `TaskStepExecution` 成功/失败驱动在 sync 与 async 两个出口重复实现，且已发生语义漂移

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java:316-324`（async 出口）对照 `:376-394`（sync 出口）
- **证据片段**:
  ```java
  // async 出口（thenCompose 的 err != null 分支）
  stepRt.getState().fail(err, taskRt);
  stepRt.getState().setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
  capturePersistVars(stepRt);
  saveTerminalStateIfDone(stepRt);
  if (nextStepNameOnError != null)
      return buildErrorResult(stepRt, parentScope, err);      // ← 先 return
  if (err instanceof NopException)
      ((NopException) err).addXplStack(stepRt.getStepPath() + '@' + this.getLocation());  // ← 永不执行
  throw NopException.adapt(err);
  ```
- **严重程度**: P2
- **现状**: 同一段"日志 → cancel 分支 → FAILED 分支 → nextStepNameOnError → addXplStack → rethrow"逻辑在 async 出口（:287-324）与 sync 出口（:357-394）各写一份约 35 行；已产生可见漂移——**sync 出口先执行 `addXplStack` 再进 `nextStepNameOnError` 分支，async 出口先 return、`addXplStack` 永不执行**。
- **风险**: 配置 `nextOnError` 的步骤同步抛错带 XPL 调用栈、异步失败不带，同一错误两种可观测性；后续任一出口补充驱动逻辑极易只改一处形成新漂移。
- **建议**: 抽 `driveStepFailure(stepRt, err, nextStepNameOnError, parentScope)` 统一两出口；补覆盖 sync/async + nextOnError 的回归测试锁住 `addXplStack` 语义。
- **信心水平**: 确定（重复区间与顺序差异逐行比对）
- **误报排除**: 两处已出现行为差异（addXplStack 有无），是可观察契约漂移，非审美问题。
- **复核状态**: 未复核

### [维度02-03] 状态常量存在两套同值异名的定义，单文件内混用；注释引用的守卫测试不存在

- **文件**: `nop-task-core/.../TaskConstants.java:77-82,107,117` 与 `nop-task-core/.../core/_NopTaskCoreConstants.java:24,79`
- **证据片段**:
  ```java
  /* 任务/步骤状态码（plan 349 Phase 2 对齐）：数值必须与 ORM 字典及生成常量
   * _NopTaskCoreConstants 保持一致，守卫测试 TestTaskConstantsAlignment 强制约束。 */
  int TASK_STATUS_TIMEOUT = 50;     // TaskConstants.java:107
  int TASK_STEP_STATUS_ACTIVE = 30; // TaskConstants.java:117
  // 生成侧：
  int TASK_STATUS_EXPIRED = 50;       // _NopTaskCoreConstants.java:79
  int TASK_STEP_STATUS_ACTIVATED = 30; // _NopTaskCoreConstants.java:24
  ```
- **严重程度**: P2
- **现状**: 手写与生成常量对同一字典值给出不同名字（30 = ACTIVE/ACTIVATED，50 = TIMEOUT/EXPIRED）；两套在 `DaoTaskStateStore` 单文件混用（:116,238,273,429,569-572 用 `TaskConstants`，:480-483 用 `_NopTaskCoreConstants`）。`TaskConstants.java:82` 宣称的 `TestTaskConstantsAlignment` 全仓搜索仅命中注释本身——**测试不存在**，真实断言埋在 `TestPlan349Fixes.java:83-100`。
- **风险**: 字典值调整时 codegen 只重新生成 `_NopTaskCoreConstants`，手写 `TaskConstants` 不同步；混用点分布两模块，`rg TASK_STATUS_` 无法发现同值双名；注释指向不存在的测试误导维护者以为已有守卫。
- **建议**: 手写常量改为引用生成常量（`int TASK_STATUS_TIMEOUT = _NopTaskCoreConstants.TASK_STATUS_EXPIRED;`）使漂移编译期暴露；把对齐断言重命名/抽出为 `TestTaskConstantsAlignment` 或至少修正注释。
- **信心水平**: 确定（测试名 0 命中；混用点按文件统计）
- **误报排除**: 非"生成代码不作发现对象"——问题在手写常量与注释，生成常量只作对照面。
- **复核状态**: 未复核

### [维度02-04] `ITaskStateStore` 两个实现的状态构造逻辑逐字重复，注释自证发生过一次漂移

- **文件**: `nop-task-core/.../state/DefaultTaskStateStore.java:24-53` 对照 `nop-task-dao/.../store/DaoTaskStateStore.java:231-240,265-280`
- **证据片段**:
  ```java
  public ITaskStepState newMainStepState(ITaskState taskState) {
      TaskStepStateBean state = new TaskStepStateBean();
      // 对齐 DaoTaskStateStore.newMainStepState（plan 349 Phase 2）：
      // 补 taskInstanceId/stepInstanceId，修复前 getTaskInstanceId() 返回 null
      state.setStepInstanceId(StringHelper.generateUUID());
      state.setTaskInstanceId(taskState.getTaskInstanceId());
      state.setStepPath(TaskConstants.MAIN_STEP_NAME);
      state.setRunId(0);
      state.setStepType(TaskConstants.STEP_TYPE_TASK);
      state.setStepStatus(TaskConstants.TASK_STEP_STATUS_ACTIVE);
      return state;
  }
  ```
- **严重程度**: P2
- **现状**: 内存 store 与 DB store 的 `newMainStepState`、`newStepState` 方法体逐字相同（仅注释不同），`newTaskState` 是同一赋值逻辑的两种落点；`:26` 注释"对齐 DaoTaskStateStore.newMainStepState"直接说明曾不一致并人工同步修过。
- **风险**: 状态字段"初始值语义"是 resume/续跑硬契约；任一侧新增初始化必须记得改另一侧；`TaskStepRuntimeImpl.newStepRuntime:144-151` 在 load 未命中时回退 `newStepState`，两 store 构造差异直接表现为"内存跑得通、落盘跑不通"或反向。
- **建议**: 抽共享 `TaskStateFactory.newStepState(...)`，两个 store 只负责差异部分。
- **信心水平**: 确定（方法体并排比对，重复 11 行 + 16 行）
- **误报排除**: 重复部分是纯 bean 赋值、无存储差异；代码自身注释记录了一次真实漂移。
- **复核状态**: 未复核

### [维度02-05] `DaoTaskStateStore` 内 17 行 exception 持久化块在 task 级与 step 级重复

- **文件**: `nop-task-dao/.../store/DaoTaskStateStore.java:197-216`（saveTaskState）对照 `:450-469`（copyStepStateToEntity）
- **证据片段**:
  ```java
  Throwable exp = state.exception();
  if (exp != null) {
      ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, exp, false, false);
      if (errorBean != null) {
          entity.setErrCode(errorBean.getErrorCode());
          entity.setErrMsg(errorBean.getDescription());
          String errorBeanJson = serializeErrorBeanData(exp, errorBean);
          if (errorBeanJson != null)
              entity.setErrorBeanData(errorBeanJson);
      }
      String stack = extractErrorStack(exp);
      if (stack != null)
          entity.setErrorStack(stack);
  }
  ```
- **严重程度**: P2
- **现状**: 同一段 17 行"exception → errCode/errMsg/errorBeanData/errorStack"提取逻辑在 task 级与 step 级各写一份，注释互相指认（`:195` "镜像 step 级..."、`:462` "对称 task 级..."）——850 行类内部的第二处复制。
- **风险**: 错误诊断字段写入契约是 cross-restart resume 重抛能力的基础；新增字段只改一侧 → task 实例查得到诊断而 step 行查不到（或反向），无编译/测试错误提示。
- **建议**: 抽出 `writeErrorColumns(entity, exp)` 两侧共用。
- **信心水平**: 确定（重复区间逐行比对，注释互相指认）
- **误报排除**: 同文件同写入阶段重复，两处写完全相同的 4 个列，非字段差异的合理分叉。
- **复核状态**: 未复核

### [维度02-06] 同一能力有两条配置路径，校验策略分裂：first-class 属性静默跳过，decorator 硬失败

- **文件**: `nop-task-core/.../builder/TaskStepEnhancer.java:121-136,199-212` 对照 `nop-task-ext/.../timeout/TimeoutTaskStepDecorator.java:19-29`、`retry/RetryTaskStepDecorator.java:20-47`、`ratelimit/RateLimitTaskStepDecorator.java:20-45`
- **证据片段**:
  ```java
  // first-class 路径（TaskStepEnhancer.wrap）
  if (stepModel.getTimeout() > 0) {
      step = new TimeoutTaskStepWrapper(step, stepModel.getTimeout());
  }
  if (stepModel.getRateLimit() != null && stepModel.getRateLimit().getRequestPerSecond() > 0) {
      step = new RateLimitTaskStepWrapper(step, rateLimitModel.getRequestPerSecond(), ...);
  }
  // decorator 路径（TimeoutTaskStepDecorator.decorate）
  if (timeout <= 0) {
      throw invalidConfig(config, ATTR_TIMEOUT_TIMEOUT, timeout, "must be > 0");
  }
  ```
- **严重程度**: P2
- **现状**: `<timeout value="-1"/>`（first-class）被 `>0` 判定为不装 wrapper，**静默不生效、无日志**；同一语义写成 `<decorator name="timeout">` 则抛 `ERR_TASK_DECORATOR_INVALID_CONFIG` 硬失败。`<rateLimit requestPerSecond="0"/>` 同样静默跳过；first-class `<retry>` 的 `buildRetryPolicy`（:199-212）完全无校验，decorator 路径校验 `maxRetryCount>=0`、`retryDelay>=0`、`maxRetryDelay>=retryDelay`。
- **风险**: 用户配负值/零值超时与限流，任务照常运行且无保护生效，只在真超时/打爆时暴露；两种写法行为差异无文档或告警，排障时"我明明配了 timeout"无法自查。
- **建议**: first-class 路径对非正数抛配置错误（与 decorator 对齐）或至少 `LOG.warn`；`buildRetryPolicy` 复用 ext 侧数值校验。
- **信心水平**: 确定（两路径判定条件均已定位比对）
- **误报排除**: 同模块族内两种等价写法对同一非法值给出相反结果（抛错 vs 静默），属跨路径契约不一致。
- **复核状态**: 未复核

### [维度02-07] 三套 step 扩展机制并存，其中 `ITaskStepEnhancer` 是不可替换的假扩展点

- **文件**: `nop-task-core/.../builder/TaskStepBuilder.java:77-81`
- **证据片段**:
  ```java
  private final ITaskStepEnhancer stepEnhancer;
  public TaskStepBuilder() {
      this.compileTool = XLang.newCompileTool();
      this.stepEnhancer = new TaskStepEnhancer();   // 硬编码，唯一实例化点
  }
  ```
- **严重程度**: P3
- **现状**: 三条并行扩展机制：(1) `<decorator>` → `ITaskStepDecorator`（配置驱动、`BeanContainer.getBean` 可替换）；(2) first-class 模型属性 → `TaskStepEnhancer.wrap()`；(3) `ITaskStepEnhancer` 接口——唯一实现由构造器 `new` 硬编码，全仓无第二实例化点、无 setter。对照 `ITaskFlowManagerImplementor`/`ITaskStepLibBuilder` 是经 `TaskFlowManagerImpl` protected 工厂提供扩展的。
- **风险**: 接口存在向使用者承诺"可替换"，实际不可替换；想统一拦截/包装步骤构建只能改 `TaskStepBuilder` 构造器，而它同时是 `buildRawStep` 分发中心，改动面大。
- **建议**: 经 `TaskFlowManagerImpl` protected 工厂注入 `ITaskStepEnhancer`，或去掉接口只留实现类。
- **信心水平**: 确定（`:81` 为唯一实例化点）
- **误报排除**: 同构建器内另一扩展通道确实走容器可替换，两者待遇不一致构成可验证的内部矛盾。
- **复核状态**: 未复核

### [维度02-08] `throttle` 只有 first-class 一条路径、无对应 decorator，扩展面与其它能力不对称

- **文件**: `nop-task-core/.../builder/TaskStepEnhancer.java:125-130`；`nop-task-ext/.../_vfs/nop/task/beans/task-ext.beans.xml`
- **证据片段**:
  ```java
  if (stepModel.getThrottle() != null) {
      TaskThrottleModel throttleModel = stepModel.getThrottle();
      step = new ThrottleTaskStepWrapper(step, throttleModel.isGlobal(),
              throttleModel.getMaxConcurrency(), throttleModel.getMaxWait(), throttleModel.getKeyExpr());
  }
  // 容器注册的 decorator 仅有：nopTaskStepDecorator_transaction / _ormSession / _retry / _timeout / _rateLimit
  ```
- **严重程度**: P3
- **现状**: `retry`/`timeout`/`rateLimit` 各有一条 first-class 路径和一条 `<decorator>` 路径，而 `throttle`/`executor`/`validator`/`sync`/`allowFailure` 只有 first-class；反向 `transaction`/`ormSession` 只有 decorator、无 first-class 属性。
- **风险**: 使用者无法从能力清单推断哪些可写成 decorator；混合写法又与 02-06 的校验差异叠加。
- **建议**: 明确收敛方向（优先 decorator 为唯一扩展面，first-class 下沉到同一批校验），补齐缺失项或在 xdef 标注"仅属性可用"。
- **信心水平**: 确定（ext main 9 个 java 全列、`task-ext.beans.xml` 5 个 bean 全列）
- **误报排除**: 本模块已为三项能力付出"两套实现 + 委托"成本，剩余项不一致使成本无法形成模式收益。
- **复核状态**: 未复核

### [维度02-09] 三个 ext decorator 逐字复制配置读取与错误构造工具方法

- **文件**: `nop-task-ext/.../retry/RetryTaskStepDecorator.java:86-92`、`timeout/TimeoutTaskStepDecorator.java:43-49`、`ratelimit/RateLimitTaskStepDecorator.java:89-95`
- **证据片段**:
  ```java
  private static NopException invalidConfig(TaskDecoratorModel config, String attrName, Object value, String reason) {
      return new NopException(TaskExtErrors.ERR_TASK_DECORATOR_INVALID_CONFIG)
              .param(TaskExtErrors.ARG_DECORATOR_NAME, DECORATOR_NAME)
              .param(TaskExtErrors.ARG_ATTR_NAME, attrName)
              .param(TaskExtErrors.ARG_ATTR_VALUE, value)
              .param(TaskExtErrors.ARG_REASON, reason);
  }
  ```
- **严重程度**: P3
- **现状**: `invalidConfig` 6 行实现三份完全相同（仅 `DECORATOR_NAME` 不同），配套 `readInt`/`readLong`/`readDouble`/`readBoolean` 也各写一份。
- **风险**: 报错文案或参数 key 调整只改一处，三种 decorator 配置错误提示开始不一致；`TaskExtErrors` 新增错误码易遗漏。
- **建议**: 下沉 `io.nop.task.ext.TaskDecoratorConfigHelper` 静态工具。
- **信心水平**: 确定（三处方法体比对一致）
- **误报排除**: 同包、同错误码、同参数名的真实复制粘贴；仅影响维护成本故 P3。
- **复核状态**: 未复核

### [维度02-10] `ITaskStepRuntime` 中两个成员是死水位：`exception` 只写不读，`addStepCleanup` 无生产调用方

- **文件**: `nop-task-core/.../ITaskStepRuntime.java:131-137`；`impl/TaskStepRuntimeImpl.java:171-177,196-203`
- **证据片段**:
  ```java
  void addStepCleanup(Runnable cleanup);
  void runStepCleanups();
  Throwable getException();
  void setException(Throwable exception);
  ```
- **严重程度**: P3
- **现状**: `addStepCleanup` 全仓仅接口声明与实现体本身（0 生产调用方）；`setException` 仅 `TryTaskStepWrapper:45,67` 写入，`getException` 在 main 源码无读取方（步骤异常权威存储是 `ITaskStepState.exception()`）。且 `addStepCleanup` 是 `synchronized` 而 `runStepCleanups`（:180-185）不加锁。
- **风险**: 同一步骤异常存在两个语义重叠槽位，维护者易读错只写不读的槽位（`stepRt.getException()` 恒为最近 try/catch 捕获值，可能与 `state.exception()` 不同步）；锁不对称的 cleanup 列表一旦启用即为竞态缺陷。
- **建议**: 删除死成员并把 `TryTaskStepWrapper` 写入改写 `state.exception()`，或 javadoc 明确"该槽位仅供 XPL 读取、权威异常在 state"，并补齐 `runStepCleanups` 同步。
- **信心水平**: 很可能（读取方缺失 grep 双口径确认；"仅供 XPL"可能性已检查 task xml 无引用）
- **误报排除**: `ITaskStepRuntime` 其余方法均有生产调用方，这两个是唯一例外；任务脚本资源也未检索到引用。
- **复核状态**: 未复核

### [维度02-11] `ITaskStepState.fail()` 与 `succeed()` 契约不对称，终态需调用方手工配对设置

- **文件**: `nop-task-core/.../state/TaskStepStateBean.java:45-57`
- **证据片段**:
  ```java
  public void succeed(Object result, String nextStepId, ITaskRuntime taskRt) {
      // 终态守卫（plan 349 Phase 2）：已终态（如 kill 竞态先到）不得被覆写为 COMPLETED
      if (isDone())
          return;
      setResultValue(result);
      setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED);
  }
  @Override
  public void fail(Throwable exception, ITaskRuntime taskRt) {
      exception(exception);   // 不改 status，调用方必须手工 setStepStatus(FAILED/EXPIRED/KILLED)
  }
  ```
- **严重程度**: P3
- **现状**: `succeed()` 自己写终态并带守卫；`fail()` 只存异常不改 status；配对约定仅以注释存在于 `TaskStepExecution:310,382`，接口 `ITaskStepState:124` 无 javadoc。5 个 `fail()` 调用点中 `TaskStepHelper.retry:307` 故意不配对（非终态），4 个在 `TaskStepExecution` 配对。
- **风险**: 新增失败驱动时忘记配对 `setStepStatus` → `exception` 非空但 `isDone()==false` → `saveTerminalStateIfDone` 不落盘、resume 重跑该步骤并再次失败，形成静默重复执行。
- **建议**: `ITaskStepState.fail` 补 javadoc 明确"非终态、需配合 setStepStatus"，或提供 `failTerminal(Throwable, int status, ITaskRuntime)` 一步完成。
- **信心水平**: 确定（接口无 javadoc、5 个调用点全列）
- **误报排除**: 注释自证是有意识设计，故不判 P2；但契约只写在调用方注释里仍是真实维护风险。
- **复核状态**: 未复核

### [维度02-12] 终态守卫是 check-then-act 非原子操作，与其宣称要防护的竞态目标不符

- **文件**: `nop-task-core/.../state/TaskStepStateBean.java:183-189`；`impl/TaskImpl.java:252-261`
- **证据片段**:
  ```java
  public void setStepStatus(Integer stepStatus) {
      // 终态守卫（plan 349 Phase 2）：已终态时忽略不同的状态设置（first-terminal-wins），
      // 相同终态的重复设置幂等放行
      if (stepStatus != null && isDone() && !stepStatus.equals(this.stepStatus))
          return;
      this.stepStatus = stepStatus;   // ← isDone() 读与写之间无同步，字段非 volatile
  }
  ```
- **严重程度**: P3
- **现状**: 注释明确以"kill 竞态先到"为防护目标，但 `isDone()` 读与写之间无任何同步；`TaskImpl.skipTerminalOverwrite` 同样是"读 → 判断 → 再写 status + exception + save"的多步非原子序列，而 `TaskRuntimeImpl:73-74` 注释说明 taskRt 会被多线程访问。
- **风险**: 两线程同时驱动不同终态（KILLED vs FAILED/COMPLETED）时守卫可能双双通过，最终状态取决于写入顺序，`exception` 与 `taskStatus` 可能来自不同驱动——正是守卫声称要消除的 first-terminal-wins 违例。
- **建议**: 守卫与写入收敛到 `synchronized`/CAS 方法（状态用 `AtomicInteger`），`skipTerminalOverwrite` 与 `setTaskStatus` 共用同一原子入口。
- **信心水平**: 很可能（非原子性由结构确定；触发取决于 fork/async 是否共享同一 state 实例，`TaskStepRuntimeImpl:134` 并发子 scope 表明确有并发路径）
- **误报排除**: 防护注释本身为竞态而写，用非原子实现防竞态属实现与目标不一致。
- **复核状态**: 未复核

### [维度02-13] `GraphStepNode` 构造器就地修改调用方传入的集合，而调用方直接传模型字段

- **文件**: `nop-task-core/.../step/GraphTaskStep.java:76-88`；`builder/GraphStepBuilder.java:21`
- **证据片段**:
  ```java
  Set<String> successSteps = waitSteps == null ? Collections.emptySet() : waitSteps;
  Set<String> errorSteps = waitErrorSteps == null ? Collections.emptySet() : waitErrorSteps;
  Set<String> completeSteps = new HashSet<>(successSteps);
  completeSteps.retainAll(errorSteps);
  successSteps.removeAll(completeSteps);   // 就地修改
  errorSteps.removeAll(completeSteps);
  // 调用方：new GraphStepNode(subStep.getWaitSteps(), subStep.getWaitErrorSteps(), ...)
  ```
- **严重程度**: P3
- **现状**: 构造器对入参 `Set` 做 `removeAll` 未防御复制；`_TaskStepModel.getWaitSteps():396-398` 直接返回字段 `_waitSteps`，被修改的就是模型对象本身。这两个集合同时出现在 `serializeToMap`（:437-438）与 `copyTo`（:463-464）。
- **风险**: 建完 graph 后模型的交集元素被永久移除；构建后再 clone/序列化模型丢失交集信息；同一模型二次构建时 `completeSteps` 已空，图等待语义被静默改变。
- **建议**: 构造器内 `new HashSet<>(waitSteps)` 复制后再 `removeAll`。
- **信心水平**: 确定（入参即模型字段、`removeAll` 就地修改、getter 无防御复制均已核对）
- **误报排除**: 传入方是参与序列化/克隆的持久化模型对象，构造器副作用外溢到模型层。
- **复核状态**: 未复核

### [维度02-14] `ITaskFlowMetrics` 用 `Object` 作 meter 契约，且 `beginStep` 的两个参数被实现完全忽略

- **文件**: `nop-task-core/.../metrics/ITaskFlowMetrics.java:4-10`；`metrics/TaskFlowMetricsImpl.java:71-75`
- **证据片段**:
  ```java
  // ITaskFlowMetrics
  Object beginStep(String stepPath, String stepType);
  void endStep(Object meter, boolean success);
  // TaskFlowMetricsImpl
  public Object beginStep(String stepPath, String stepType) {
      return Timer.start(registry);   // 两个参数全部丢弃
  }
  // 调用方 TaskStepExecution:267 认真传入 stepRt.getStepPath() 与 step.getStepType()
  ```
- **严重程度**: P3
- **现状**: 实现丢弃参数，所有步骤共用同一 `stepSuccessTimer`/`stepFailureTimer`；`endStep`/`endTask` 再把 `Object` 强转回 `Timer.Sample`（:67,:78）；`createCounter`（:50）main 源码无调用方。
- **风险**: 按步骤名/类型下钻的耗时与失败率指标无法产出，"步骤级指标"开关实际只能区分整体开关；`Object meter` 使自定义实现的返回类型错误只能运行期 `ClassCastException` 暴露。
- **建议**: `beginStep` 返回具体句柄（或泛型），实现按 `stepPath`/`stepType` 建 tag 或独立 timer。
- **信心水平**: 确定（参数丢弃可直接读出）
- **误报排除**: 框架内部契约，参数被静默丢弃是实现未兑现接口语义，与 Nop 动态边界豁免无关。
- **复核状态**: 未复核

### [维度02-15] core 层公开接口的 javadoc 反向 `{@link}` dao 模块实现类（core 不依赖 dao）

- **文件**: `nop-task-core/.../ITaskState.java:115,125`；`step/TaskStepExecution.java:411`
- **证据片段**:
  ```java
  /**
   * task 级状态从持久化存储中恢复后调用（对称 step 级 {@link ITaskStepState#afterLoad(ITaskRuntime)}）。
   * <p>
   * {@link io.nop.task.dao.store.DaoTaskStateStore#loadTaskState} 在 {@code toTaskStateBean} 完成
   * 既有内联 reconstruction 之后、返回之前调用本 hook。...
   */
  default void afterLoad(ITaskRuntime taskRt) { }
  ```
- **严重程度**: P3
- **现状**: `nop-task-core/pom.xml` 依赖仅 `nop-xlang`+`nop-ioc`，不含 `nop-task-dao`（dao 反向依赖 core）。因此 `ITaskState:115,125` 与 `TaskStepExecution:411` 中的 `{@link io.nop.task.dao.store.DaoTaskStateStore#...}` 在 core javadoc 编译时无法解析。
- **风险**: `mvn javadoc` 产生无法解析引用警告/错误；core 公开契约文档以"下游实现类"为唯一描述对象，`DaoTaskStateStore` 改名时 core 文档静默失效，依赖方向决定 core 编译期发现不了。
- **建议**: 改为行为契约描述，或用 `{@code DaoTaskStateStore}` 纯文本引用。
- **信心水平**: 确定（依赖方向由两个 pom 交叉确认）
- **误报排除**: 引用目标所在模块不在本模块 classpath，是可编译期验证的依赖方向违反（文档层面）。
- **复核状态**: 未复核

### [维度02-16] `nopTaskExecutionQueue` bean 声明但全仓零引用

- **文件**: `nop-task-core/.../_vfs/nop/task/beans/task-defaults.beans.xml:8-15`
- **证据片段**:
  ```xml
  <bean id="nopTaskExecutionQueue" class="io.nop.core.execution.DefaultTaskExecutionQueue">
      <property name="threadPoolConfig">
          <bean class="io.nop.commons.concurrent.executor.ThreadPoolConfig"
                ioc:config-prefix="nop.task.execution-queue.executor">
              <property name="name" value="nop-task-execution-queue"/>
          </bean>
      </property>
  </bean>
  ```
- **严重程度**: P3
- **现状**: `rg "nopTaskExecutionQueue|ITaskExecutionQueue|taskExecutionQueue"`（排除 target/ai-dev）全仓 4 处命中：bean 声明、nop-kernel 接口与实现定义、reflect-config 反射登记；**无任何 `@Inject`、`getBean`、xpl 或 xdef 引用**。
- **风险**: 声明了线程池配置前缀却从不被创建/启动，使用者配置该前缀无任何效果；bean 占用容器实例化名额，形成"看起来有执行队列"假象。
- **建议**: 删除该 bean，或在实际执行路径（`TaskFlowManagerImpl` 异步步骤提交）接入 `ITaskExecutionQueue`。
- **信心水平**: 确定（双关键词口径，已排除历史记录）
- **误报排除**: id 与类型全仓 0 引用，且 nop-task 自身执行路径未使用队列。
- **复核状态**: 未复核

### [维度02-17] `nop-task-queue` 为僵尸模块：pom 无任何依赖，接口无法被有效实现也无人引用

- **文件**: `nop-task/nop-task-queue/pom.xml`；`nop-task-queue/src/main/java/io/nop/task/queue/ITaskQueue.java:1-5`
- **证据片段**:
  ```java
  package io.nop.task.queue;
  public interface ITaskQueue {
      void enqueueTask();   // 无参数、无返回值
  }
  // pom.xml 仅有 <parent> + <artifactId> + 编码属性，无任何 <dependencies> 段
  ```
- **严重程度**: P3
- **现状**: `rg -n "\bITaskQueue\b"`（排除 target）命中 2 处（定义自身 + ai-dev 历史文档），代码引用 0 处；`nop-task/pom.xml:23` 仍把该模块列入 reactor；模块不依赖 `nop-task-core`，实现者拿不到 task/step 上下文。
- **风险**: 占用构建与 reactor 槽位；空接口被当作"任务队列扩展点已预留"信号，后续补实现时发现既定签名无法承载信息，不得不破坏性改签名。
- **建议**: 删除模块（连同 parent `<module>` 行），或补齐依赖与有意义签名后再保留。
- **信心水平**: 确定（0 代码引用 + pom 无依赖，双口径）
- **误报排除**: 全仓 48 个单文件模块中多数有真实消费者；本模块是唯一"声明接口却无实现无调用方、pom 零依赖"的情形。
- **复核状态**: 未复核

### [维度02-18] 非 global 限流器首次冻结速率无告警，与 global 路径不一致

- **文件**: `nop-task-core/.../impl/TaskFlowManagerImpl.java:170-186`
- **证据片段**:
  ```java
  if (global) {
      String cacheKey = taskRt.getTaskName() + ":" + key;
      IRateLimiter limiter = globalRateLimiters.computeIfAbsent(cacheKey, k -> new DefaultRateLimiter(requestPerSecond));
      // 全局限流器首配置固化告警（plan 349 Phase 5，check2 P3）：同 key 二次配置不同速率时
      // 旧参数继续生效（缓存命中即返回），此处告警提示，避免参数调优静默不生效
      if (Math.abs(limiter.getPermitsPerSecond() - requestPerSecond) > 1e-9) {
          LOG.warn("nop.task.global-rate-limiter-config-frozen:cacheKey={},configuredPerSecond={},activePerSecond={}",
                  cacheKey, requestPerSecond, limiter.getPermitsPerSecond());
      }
      return limiter;
  }
  return (IRateLimiter) taskRt.computeAttributeIfAbsent("rate-limit:" + key, k -> {
      return new DefaultRateLimiter(requestPerSecond);   // ← 相同"首次冻结、后续忽略"语义，无告警
  });
  ```
- **严重程度**: P3
- **现状**: global 分支为"同 key 二次配置不同速率时旧参数继续生效"专门加了告警；非 global 分支用 `computeAttributeIfAbsent` 实现完全相同语义却无告警；`getSemaphore`（:188-193）同样模式。
- **风险**: 非 global 下调整 `requestPerSecond` 后新值静默不生效，与 global 路径可观测性不一致。
- **建议**: 抽共用 `computeOrWarn(key, configured, supplier)` 两分支复用。
- **信心水平**: 确定（两分支语义一致、告警仅其一）
- **误报排除**: 该告警是针对"参数调优静默不生效"的既有修复，同方法另一分支具相同失效模式未同步修复，属修复面不完整。
- **复核状态**: 未复核

## 检查范围清单（第 1 轮）

### 计数口径（可复现）
| 结论 | 口径 A（`rg -n`，排除 target） | 口径 B（交叉验证） |
|---|---|---|
| 状态常量双套 | 分别 `rg -n "TASK_STATUS_\|TASK_STEP_STATUS_"` 于两文件 | `rg -o "TaskConstants\.TASK_...\|_NopTaskCoreConstants\.TASK_..."` → `DaoTaskStateStore` 单文件同时含 2 家 |
| `TestTaskConstantsAlignment` 不存在 | 全仓 `rg` → 仅 1 命中（注释本身） | `rg -ln "TASK_STATUS_TIMEOUT" --glob '*Test*.java'` → 3 文件均无该类名 |
| `nopTaskExecutionQueue` 0 引用 | 双关键词 4 命中 | 排除 `ai-dev/` 后结论不变 |
| `ITaskQueue` 0 代码引用 | 2 命中（定义 + 文档） | pom 无 `<dependencies>` |
| `addStepCleanup` 0 生产调用方 | `rg nop-task/*/src/main` → 2 行 | `runStepCleanups` 生产 1 处（`TryTaskStepWrapper:57,80`） |
| ext decorator 清单 | 9 个 java | 5 个 bean |

### 读过的关键文件
- **core 抽象**: `ITask`、`ITaskStep`、`ITaskStepExecution`、`ITaskStepRuntime`、`ITaskStepState`、`ITaskState`、`ITaskStateStore`、`ITaskFlowManager`、`ITaskStepDecorator`、`ITaskStepEnhancer`、`ITaskStepBuilder`、`ITaskFlowBuilder`、`ITaskStepLibBuilder`、`ITaskBeanContainerBuilder`、`ITaskFlowMetrics`
- **core 实现**: `TaskImpl`(307)、`TaskRuntimeImpl`(258)、`TaskStepRuntimeImpl`(204)、`TaskFlowManagerImpl`(232)、`TaskStepExecution`(514)、`TaskStepHelper`(386)、`GraphTaskStep`(436)、`AbstractTaskStep`、`DelegateTaskStep`、`TryTaskStepWrapper`、`AbstractForkTaskStep`
- **builder**: `TaskStepBuilder`(416)、`TaskStepEnhancer`(213)、`GraphStepBuilder`、`GraphStepAnalyzer`、`TaskFlowBuilder`、`TaskFlowModel`
- **state/常量/metrics/dao/ext/service**: 见各发现文件引用
- **平台侧参照**: `BeanContainerImpl.getBeanScope`、`StaticBeanContainer`、nop-task 全部 4 个 pom

### 零发现项
1. **模块分层**: core pom 仅 nop-xlang+nop-ioc，无 dao/service 依赖；源码无反向 import（javadoc 3 处已单列 02-15）；service→dao+core 方向正确
2. **服务层越权**: 4 个 15 行 CrudBizModel 无直接操作 state/store 代码
3. **继承/组合层次**: `AbstractTaskStep`/`DelegateTaskStep` 层次完整，6 个元数据方法全量转发
4. **wrapper/decorator 双实现假设（已证伪）**: ext decorator 分别委托 core Wrapper，不存在算法双实现；真实问题是 02-06 校验分歧
5. **生成代码排除**: api/beans、`_gen/`、`_*.xml`、`_service.beans.xml`
6. **空 `NopTask{Configs,Constants,Errors}`**: 与 `NopFileConfigs`/`NopDatavConstants` 同构，全仓约定——核查后主动放弃该发现
7. **平台豁免项**: BizModel 返回实体、`@Inject protected`、`I*Biz` 位于 dao、`jakarta.*` 于 core、CrudBizModel 继承、xbiz 签名差异、传递依赖声明
8. **继承图/装饰顺序**: `decorateStep` 先于 first-class wrapper（内层），语义由 `TestReliabilityDecorators:512` 注释说明，记为观察不构成发现
