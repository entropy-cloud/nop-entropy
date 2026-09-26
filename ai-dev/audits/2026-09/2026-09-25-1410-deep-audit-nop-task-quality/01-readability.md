# 维度 01：可读性（Readability）— nop-task 深度审核

- **审核日期**: 2026-09-25
- **轮次**: 第 1 轮（初审）完成，待深挖与复核
- **基线**: `_tmp/audit-baseline.txt`（263 tests 全绿；手写 Java 239 文件 / 25466 行）
- **口径**: live code；排除 `_` 前缀生成物、`_gen/`、`target/`、`_dump/`、test 代码；`nop-task-api` 与 `nop-task-meta` 字典带 `//__XGEN_FORCE_OVERRIDE__` 生成标记已整体排除

## 第 1 轮（初审）

### [维度01-01] `TaskStepHelper.newError` 声明返回 `NopException` 却在方法体直接 `throw`，5 个调用点的 `.param(...)` 链全部不可达，错误消息参数丢失

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:54-67`；调用点 `RateLimitTaskStepWrapper.java:40`、`ThrottleTaskStepWrapper.java:40`、`SequentialTaskStep.java:105`、`LoopNTaskStep.java:164`、`CallStepTaskStep.java:74`
- **证据片段**:
  ```java
  public static NopException newError(SourceLocation loc, ITaskStepRuntime stepRt, ErrorCode errorCode) {
      throw new NopException(errorCode).loc(loc).param(TaskErrors.ARG_TASK_NAME, stepRt.getTaskRuntime().getTaskName())
              .param(TaskErrors.ARG_STEP_PATH, stepRt.getStepPath()).param(TaskErrors.ARG_RUN_ID, stepRt.getRunId())
              .param(TaskErrors.ARG_STEP_TYPE, stepRt.getStepType());
  }
  // 调用点（RateLimitTaskStepWrapper.java:39-41）：
      if (!rateLimiter.tryAcquire(1, maxWait))
          throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_REQUEST_RATE_EXCEED_LIMIT)
                  .param(ARG_KEY, key);   // <- 永不执行
  ```
- **严重程度**: P2（可读性维度默认上限 P2，此处因掩盖真实缺陷上报）
- **现状**: 方法签名承诺返回异常对象，实现却直接抛出；5 处 `throw newError(...).param(...)` 中 `.param(...)` 全部不可达。`ErrorMessageManager.resolveDescription`（`nop-kernel/nop-core/.../ErrorMessageManager.java:144-149`）在参数缺失时原样返回 `{name}`，导致 `nop.err.task.request-rate-exceed-limit`（缺 `requestPerSecond`）、`loop-step-invalid-loop-var`（begin/end/step 全缺）、`unknown-step-in-lib`（libName/stepName 全缺）、`unknown-next-step`（缺 `nextStep`）等模板把占位符字面量渲染给用户。
- **风险**: 线上错误消息出现字面量 `{requestPerSecond}` 等，无法定位出错的限流 key / 库 / 跳转目标；持久化 `errorBean.params` 同样缺失诊断字段。调用点读起来像补了参数，实际从未生效。
- **建议**: 改 `newError` 为真正返回 `NopException`（去掉方法体 `throw`），或改名 `throwError(...)` 并把 5 处 `.param(...)` 迁入构造参数；修复后补断言消息不含 `{` 的回归测试。
- **信心水平**: 确定（Java 可达性语义 + 缺参渲染逻辑均直读源码确认）
- **误报排除**: 非"参数名与模板不匹配"误报——已逐条比对 `TaskErrors.java:85-103` 模板与 `resolveDescription` 实现；问题在不可达而非多余。
- **复核状态**: 未复核

### [维度01-02] `GraphStepAnalyzer` exit 步骤校验循环复用 `enterStep` 变量名，错误码两个参数全部错绑

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/builder/GraphStepAnalyzer.java:70-83`（缺陷）、`139-143`（同文件正确用法）
- **证据片段**:
  ```java
  for (String enterStep : stepModel.getExitSteps()) {      // 行 78：迭代 exitSteps，变量却叫 enterStep
      if (!stepModel.hasStep(enterStep))
          throw new NopException(ERR_TASK_UNKNOWN_STEP_IN_GRAPH)
                  .source(stepModel)
                  .param(ARG_STEP_NAME, stepModel.getName());  // 把图名塞进 stepName，且缺 graphStepName
  }
  // 对照正确用法（行 139-143）：.param(ARG_GRAPH_STEP_NAME, stepModel.getName()).param(ARG_STEP_NAME, varStepName)
  ```
- **严重程度**: P2
- **现状**: `ERR_TASK_UNKNOWN_STEP_IN_GRAPH` 模板为 `流程图[{graphStepName}]中没有定义步骤[{stepName}]`（`TaskErrors.java:117-119`），行 74/82 只传图名到 `stepName`，缺 `graphStepName`；行 78 循环变量名与 enter 循环相同，极易误读为重复代码。
- **风险**: 图定义错误时输出 `流程图[{graphStepName}]中没有定义步骤[myGraph]`——既不知道缺哪个步骤，还把图名当步骤名展示；变量命名掩盖了 exit 分支不调用 `dag.addNextNode` 的真实差异。
- **建议**: 按行 139-143 模式补齐 `.param(ARG_GRAPH_STEP_NAME, ...)` 并传实际 `exitStep`；循环变量改名 `exitStep`。
- **信心水平**: 确定（模板、常量与正确用法三方交叉验证）
- **误报排除**: 行 140-143 已是正确写法，说明非平台约定差异而是局部遗漏；`ERR_TASK_GRAPH_STEP_NO_EXIT_STEPS` 传图名正确，不报。
- **复核状态**: 未复核

### [维度01-03] `TaskImpl.checkInputs` 把 stepType 的值绑给 `ARG_STEP_PATH` 键

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:299-305`
- **证据片段**:
  ```java
  if (input.isMandatory() && StringHelper.isEmptyObject(value)) {
      throw new NopException(ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY)
              .source(mainStep)
              .param(ARG_TASK_NAME, taskRt.getTaskName())
              .param(ARG_STEP_PATH, mainStep.getStepType())   // 值是 stepType，键是 stepPath
              .param(ARG_INPUT_NAME, input.getName());
  }
  ```
- **严重程度**: P2
- **现状**: `mainStep.getStepType()`（如 `task`/`seq`）挂在 `ARG_STEP_PATH` 键下；同类错误路径 `TaskStepExecution.initInputs` 传 `stepRt.getStepPath()` 语义正确。
- **风险**: 必填输入缺失时错误消息显示 `步骤[task]` 之类无意义"路径"；按 `stepPath` 键过滤/聚合日志会拿到错误值。
- **建议**: 改传真实路径或删除该行（`ARG_TASK_NAME` 已足够定位）；补断言 `stepPath` 值符合路径格式的用例。
- **信心水平**: 确定（同仓库正确写法可直接对照）
- **误报排除**: 非"stepPath 允许 stepType"的口径差异——`TaskErrors.java` 中 `ARG_STEP_PATH` 其余 30+ 处使用全部传路径。
- **复核状态**: 未复核

### [维度01-04] `DaoTaskStateStore` 用字面量 `4000` 守卫写入 `REMARK` 列（实际 `VARCHAR(200)`），超长守卫阈值与列宽差 20 倍

- **文件**: `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:183-184`（写）、`344-351`（读）、`55/60`（已有 `= 4000` 常量未复用）
- **证据片段**:
  ```java
  String json = JsonTool.serialize(resultValue, false);
  if (json != null && json.length() <= 4000)   // 行 183：阈值 4000
      entity.setRemark(json);
  // 列定义（_create_nop-task.sql:55）：REMARK VARCHAR(200) NULL COMMENT '备注'
  // ORM（_vfs/nop/task/orm/_app.orm.xml:247）：<column code="REMARK" ... precision="200" .../>
  ```
- **严重程度**: P1（可读性维度默认上限 P2，因守卫完全失效掩盖真实逻辑错误而上调）
- **现状**: 注释明确"超长跳过非致命"，阈值却按 `TASK_INPUTS`/`ERROR_BEAN_DATA` 的 `4000` 写（行 163），与 `REMARK VARCHAR(200)` 不一致；同文件已有 `ERROR_BEAN_DATA_MAX_LEN = 4000` 常量但此路径用裸字面量。读取侧（344-351）`JsonTool.parse` 失败时静默回退把原始文本当 `resultValue`。
- **风险**: 结果 JSON 长度 201–4000 时（任务返回 Map/列表很常见）：严格模式 DB 抛错致 `saveTaskState` 失败；非严格模式被截断成非法 JSON，resume 时 parse 失败并静默把 String 当 resultValue 返回，下游按 Map 取值类型错误——错误被兜底掩盖极难定位。仓库自身约定"按列 precision 截断"（`TestDaoTaskStateStoreErrorStackRoundTrip:217-220`）被本路径违反。
- **建议**: 阈值改按 `REMARK` 列 precision 定义常量（200），对 201/200/199 三档补截断或跳过回归测试；复核 `setErrMsg(errorBean.getDescription())`（行 203/456）对应 `ERR_MSG VARCHAR(500)` 是否需上限。
- **信心水平**: 确定（列宽由 DDL + ORM 双源确认，写入路径仅行 184 一处）；"实际 DB 行为"子项需复核（严格/非严格 SQL 模式差异）
- **误报排除**: 非"domain remark precision=1000 会被使用"——该列 `precision="200"` 覆盖 domain，DDL 与 ORM 一致；生成实体无 maxLength 强制。
- **复核状态**: 未复核

### [维度01-05] `executeWithParentRt` 长达 219 行，同步/异步两套终态驱动块与注释逐字复制

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java:178-396`（方法本体）；`307-311` 与 `379-383`（复制的注释块）
- **证据片段**:
  ```java
  178:    public TaskStepReturn executeWithParentRt(ITaskStepRuntime parentRt) {
  307:                    // plan 254: 终态失败 FAILED driver wiring（对称 plan 252/253 succeed-driver）。
  311:                    // retry-wrapped step 已在 TaskStepHelper.retry:178 由 fail() 保存 exception, harmless re-save.
  379:            // plan 254: 终态失败 FAILED driver wiring（对称 plan 252/253 succeed-driver）。   ← 与 307 逐字重复
  383:            // retry-wrapped step 已在 TaskStepHelper.retry:178 由 fail() 保存 exception, harmless re-save.
  ```
- **严重程度**: P2
- **现状**: 方法覆盖初始化→执行→同步出错→异步回调出错→终态驱动→指标→保存全生命周期，219 行需靠行号注释导航；async 出口（292-324）与 sync 出口（365-394）约 30 行逻辑+注释逐字重复，两处 `TaskStepHelper.retry:178` 行号引用均已失效。同方法内行 234、272 还有两处 `:257`/`:330` 出口式行号导航。
- **风险**: 同一错误处理语义两份实现，任一侧修补会静默分叉；行号注释让"读懂代码"退化为"对照历史行号读代码"。`LoopNTaskStep` 行 219-225 注释自证这类双份结构在本仓库出过 bug。
- **建议**: 抽取 `handleFailedResult(...)` / `handleAsyncResult(...)` 统一两类出口；219 行方法按"执行、结果分发、终态驱动、落库"拆私有方法。
- **信心水平**: 确定（方法长度由括号配对脚本精确计算；重复块逐行比对）
- **误报排除**: 长方法本身只评 P3，因同构重复 + 失效行号导航叠加升 P2；非单纯格式偏好。
- **复核状态**: 未复核

### [维度01-06] `SelectorTaskStep.execute` 94 行 + 同步/异步双份候选推进逻辑 + 不可达的 `isSuspend` 死分支

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/SelectorTaskStep.java:29-122`；`97` 与 `110-111`（死分支）
- **证据片段**:
  ```java
   97:                    } else if (result.isSuspend()) {          // 已经拦截 SUSPEND
  101:                    } else if (result.isEnd()) {
  107:                    } else {                                   // 走到这里 => isSuspend() 必为 false
  108:                        // 异步完成值为 SUSPEND 时原样透传（与同步路径的 isSuspend 判定对齐），
  110:                        if (result.isSuspend())
  111:                            return result;                     // <- 不可达
  ```
- **严重程度**: P2
- **现状**: `TaskStepReturn.isSuspend()` 是纯值判断，`nextStepName` 无 setter，lambda 参数 `result` 在行 97→110 间未被改写，行 110 条件恒为 false，行 108-111 为死代码，且两段注释互相矛盾（一处说"已对偶"，一处说"否则挂起会被静默跳过"）。方法 94 行，行 40-58（同步）与 82-119（异步）是同一套候选推进语义的两份实现。
- **风险**: 读者需证明该分支不可达；矛盾注释让人不敢清理；双份候选推进逻辑后续修改易分叉。
- **建议**: 删除行 110-111 死代码，合并两段注释；把候选推进抽成共享方法。
- **信心水平**: 确定（`isSuspend()` 实现与 `nextStepName` 无 setter 均直读确认）
- **误报排除**: 需 `thenCompose` 回调传入新对象才可能复活该分支——已核对回调参数为同一引用且无中间 setter。
- **复核状态**: 未复核

### [维度01-07] 注释用「源码行号 + 历史 plan 编号」导航：5 处行号引用已失效，过程术语 164 处 / 31 文件

- **文件**: `DaoTaskStateStore.java:129,146`；`TaskStepHelper.java:119`；`TaskStepExecution.java:272,311,383`
- **证据片段**:
  ```java
  // DaoTaskStateStore.java:129  注释：loadStepState:188   → 实际 loadTaskState 起于 283；188 行是 saveTaskState 内的日志
  // TaskStepHelper.java:119     注释：timeout :125 / withCancellable :235 → 实际 timeout 起于 182、withCancellable 起于 337
  // TaskStepExecution.java:311  注释：TaskStepHelper.retry:178 → 实际 retry 起于 244；178 行是 castInt 的 return
  ```
- **严重程度**: P2
- **现状**: 主源码中 `plan NNN` 形式过程引用共 **164 处 / 31 文件**（双口径：`rg -o` 命中 164、`rg -l` 文件 31），另有 `check2 P0-1`、`裁定 1/2` 等过程编号 56 处；行号型注释抽查 8 处，5 处已失效、3 处正确。
- **风险**: 读者无法判断注释描述当前代码还是历史 plan 结论；失效行号把排查引向错误方法；`ai-dev/` 未读区被注释隐式引用，脱离过程记录后成为无法验证的断言。
- **建议**: 失效行号改方法名引用（`{@link TaskStepHelper#retry}`，`TaskImpl.java:205-208` 已有先例）；"为什么"留在注释，"哪次改动引入"交给 `ai-dev/logs/`。
- **信心水平**: 确定（8 处行号逐条 `awk NR==` 核对；计数双口径交叉验证）
- **误报排除**: 计数限定 nop-task 主源码、排除 test；非把中文注释当噪声（平台约定即中文注释）。
- **复核状态**: 未复核

### [维度01-08] `ITaskStepState.succeed(result, nextStepId, taskRt)` 参数名与语义都不符，唯一生产实现完全忽略该参数

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepState.java:122`；实现 `state/TaskStepStateBean.java:46-52`；调用 `TaskStepHelper.java:299,332`、`TaskStepExecution.java:346`
- **证据片段**:
  ```java
  122:    void succeed(Object result, String nextStepId, ITaskRuntime taskRt);   // 声明
  // TaskStepStateBean.java:46-52：
      public void succeed(Object result, String nextStepId, ITaskRuntime taskRt) {
          if (isDone()) return;
          setResultValue(result);
          setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED);
      }                                                                        // nextStepId 未被使用
  ```
- **严重程度**: P2
- **现状**: 参数名 `nextStepId` 与调用方传入的 `getNextStepName()` 不同概念；实现既不读取也不落库，路由实际由 `TaskStepExecution:346-349` 的 `setSavedNextStepName` 独立完成；测试文件同一概念又写作 `nextStepId`，三个名字描述同一件事。
- **风险**: 读者会以为 `succeed` 决定下一步走哪，排查路由问题盯错位置；后续维护者可能"补上" `setNextStepId` 而重复路由逻辑制造真分叉。
- **建议**: 参数改名 `nextStepName`；javadoc 写明"本方法只负责终态标记，路由交给 runtime 的 savedNextStepName"；若永不需要则从签名删除。
- **信心水平**: 确定（声明/唯一实现/全部调用点已枚举）
- **误报排除**: 已确认 `TaskStepStateBean` 是唯一生产实现，不存在"其他实现会用到该参数"。
- **复核状态**: 未复核

### [维度01-09] `GraphTaskStep.runStep` 106 行，`runningCount==0 && !future.isDone()` 守卫在同文件复制 6 处

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:246-351`；守卫出现于 `215,237,268,288,324,345`
- **证据片段**:
  ```java
  215:  if (runningCount.get() == 0 && !future.isDone())
  268:  if (runningCount.get() == 0 && !future.isDone())
  324:  if (runningCount.get() == 0 && !future.isDone())
  345:  if (runningCount.get() == 0 && !future.isDone()) {
  ```
- **严重程度**: P3
- **现状**: "图执行结束"语义判定以相同表达式散布 6 处，每处上下文靠注释解释（331-333 解释计数递减顺序的并发窗口）；`runStep` 106 行承担依赖就绪判断、启动、双出口回调与图级收敛。
- **风险**: 判定条件调整时漏改任一处即"图提前收敛"或"图永不结束"，6 处相似代码让漏改难被 review 捕获。
- **建议**: 抽取 `tryCompleteGraph(future, runningCount)` 统一收敛判定；拆分 runStep。
- **信心水平**: 确定（6 处 grep 定位 + 方法边界括号计算）
- **误报排除**: 6 处语义均为"图是否可收敛"，上下文注释自我说明。
- **复核状态**: 未复核

### [维度01-10] `GraphStepNode` 构造器原地改写调用方传入的集合，副作用隐藏在构造逻辑里

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:76-94`；调用方 `builder/GraphStepBuilder.java:21-22`
- **证据片段**:
  ```java
  // GraphStepBuilder.java:21（传入的是模型对象自己的 set）
      return new GraphTaskStep.GraphStepNode(subStep.getWaitSteps(), subStep.getWaitErrorSteps(), step, enter, end, subStep.getNextOnError());
  // GraphStepNode 构造器：
  79:  Set<String> successSteps = waitSteps == null ? Collections.emptySet() : waitSteps;
  84:  successSteps.removeAll(completeSteps);   // 直接改写模型上的 waitSteps
  ```
- **严重程度**: P3
- **现状**: `TaskStepModel.getWaitSteps()` 返回模型内部 `LinkedHashSet` 活引用，构造器把"集合三分"归一化写成对入参的 `removeAll`；构造后再序列化/复查，`waitSteps` 已被裁剪。当前恰被 `TaskFlowModel.getTask()` 的 `task == null` 缓存挡住二次构建风险——正确性依赖与本类无关的缓存。
- **风险**: 构造函数带可观察副作用是隐藏契约：任何绕过缓存的重建路径都会让图节点丢依赖；模型序列化展示与 DSL 原文不一致。
- **建议**: 构造器内 `new HashSet<>(waitSteps)` 后再裁剪，或提取 `GraphWaitSteps.normalize(model)` 静态工厂。
- **信心水平**: 确定（构造器与调用点直读；getter 为生成代码直接返回字段）
- **误报排除**: 已确认当前 `getTask()` 缓存使二次构建不发生，故不评 P2。
- **复核状态**: 未复核

### [维度01-11] 长方法群：`buildRawStep` 91 行、`saveTaskState` 89 行、`TaskImpl.execute` 85 行、`LoopNTaskStep.execute` 81 行

- **文件**: `builder/TaskStepBuilder.java:89-179`；`DaoTaskStateStore.java:138-226`；`TaskImpl.java:91-175`；`LoopNTaskStep.java:153-233`
- **证据片段**:
  ```java
  89:    public AbstractTaskStep buildRawStep(TaskStepModel stepModel) throws NopException {   // 91 行
  138:    public void saveTaskState(ITaskRuntime taskRt) {                                    // 89 行
   91:    public TaskStepReturn execute(ITaskRuntime taskRt, Set<String> outputNames) {        // 85 行
  153:    public TaskStepReturn execute(ITaskStepRuntime stepRt) {                             // 81 行
  ```
- **严重程度**: P3
- **现状**: 四方法均超 80 行（括号配对脚本计算）。`buildRawStep` 是 10+ 分支类型分发 + 参数装配；`saveTaskState` 混合五段互不相关序列化；`TaskImpl.execute` 混合输入校验、执行、指标与终态驱动；`LoopNTaskStep.execute` 同步/异步两套循环推进。
- **风险**: 单方法承载多个变化原因，review 与回归定位成本高。
- **建议**: 按职责抽私有方法，优先拆 `buildRawStep` 与 `saveTaskState`（分别叠加 01-13、01-04 问题）。
- **信心水平**: 确定（脚本计算行数）
- **误报排除**: 单纯长度只评 P3，不夸大。
- **复核状态**: 未复核

### [维度01-12] `TaskConstants` 9 个常量全仓零引用（双口径验证）

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java:21,23,25,27,54,74,75,77,198`
- **证据片段**:
  ```java
  21:     String FILE_TYPE_TASK = "task.xml";
  25:     String ATTR_TASK_NAME = "task:name";
  54:     String PROP_OUTPUTS = "outputs";
  74:     String PARAM_DELAY = "delay";
  198:    String EXECUTOR_BEAN_PREFIX = "executor_";
  // 双口径核对：rg -c "\b<NAME>\b"（java/xml/xlib/xpl）每个均 total=1，rg -l 均只命中 TaskConstants.java 自身
  ```
- **严重程度**: P3
- **现状**: `FILE_TYPE_TASK`、`FILE_TYPE_TASK_LIB`、`ATTR_TASK_NAME`、`ATTR_TASK_VERSION`、`PROP_OUTPUTS`、`PARAM_DELAY`、`PARAM_TIMEOUT`、`PARAM_COUNT`、`EXECUTOR_BEAN_PREFIX` 共 9 个常量全仓零引用；相邻的 `PROP_ERROR`/`VAR_REQUEST` 有真实引用，说明是局部遗留。
- **风险**: 读者假设这些常量对应仍在使用的约定（如 `EXECUTOR_BEAN_PREFIX` 暗示 executor bean 命名机制），排查时搜索不存在的机制。
- **建议**: 删除；若属预留 API 面，加 `@Deprecated` 与"预留勿依赖"注释。
- **信心水平**: 确定（逐 token 命中计数 + 文件级枚举两口径一致）
- **误报排除**: 已排除 XPL 字符串拼接引用陷阱——查询覆盖 `*.xml`/`*.xlib`/`*.xpl`，`TaskGenHelper` 被 `biz-gen.xlib` 反射调用的先例已因此排除在死码外。
- **复核状态**: 未复核

### [维度01-13] 四处无调用者/空壳成员：`getStepNames`、`needSave`、`NopTaskErrors`、`ITaskQueue`

- **文件**: `builder/GraphStepAnalyzer.java:202`；`ITaskStepState.java:130`（实现 `TaskStepStateBean.java:76`）；`nop-task-service/.../NopTaskErrors.java:3`；`nop-task-queue/.../ITaskQueue.java:3`
- **证据片段**:
  ```java
  // GraphStepAnalyzer.java:202
      public Set<String> getStepNames() {          // 全仓无调用
  // ITaskStepState.java:130 / TaskStepStateBean.java:76
      boolean needSave();                          // 仅声明 + 实现返回固定 true，0 个调用者
  // NopTaskErrors.java:3 — 空接口，无方法
  // ITaskQueue.java:3 — 唯一方法 enqueueTask() 无实现、无调用
  ```
- **严重程度**: P3
- **现状**: 四个成员构成"看起来是扩展点"的死代码面。`needSave()` 更具迷惑性——读代码的人会找"什么情况下返回 false"，实际没有调用者。
- **风险**: 读者为理解 `needSave` 开关语义浪费时间；`nop-task-queue` 整模块（pom + 唯一空接口）会被误判为可用队列抽象。
- **建议**: 删除 `getStepNames`、`needSave`、`NopTaskErrors`；`ITaskQueue` 与 nop-task-queue 模块需产品决策（补实现或删除）。
- **信心水平**: 确定（逐符号全仓检索，含 java/xml/xlib）
- **误报排除**: 已排除同名不同类命中（`WfStepModel.getStepNames`）与 XPL 反射调用。
- **复核状态**: 未复核

### [维度01-14] 两组近重复方法体：`TaskImpl` 三个终态 driver 各 4 行完全同构；`TaskStepBuilder` 三份 sub-step 收集循环逐字重复

- **文件**: `impl/TaskImpl.java:195-201,227-233,239-245`；`builder/TaskStepBuilder.java:266-307,357-358`
- **证据片段**:
  ```java
  // TaskImpl 三个方法体（差异仅状态常量）：
      private void driveTaskFailed(...) { if (skipTerminalOverwrite(taskState, TaskConstants.TASK_STATUS_FAILED)) return;
          taskState.exception(err); taskState.setTaskStatus(TaskConstants.TASK_STATUS_FAILED); taskRt.saveTaskState(); }
      private void driveTaskKilled(...) { ... TASK_STATUS_KILLED ... }
      private void driveTaskTimeout(...) { ... TASK_STATUS_TIMEOUT ... }
  // TaskStepBuilder 三处循环逐字相同：
      for (TaskStepModel subStepModel : model.getSteps()) {
          if (subStepModel.isDisabled()) continue;
          steps.add(buildStepExecution(subStepModel));
      }
  ```
- **严重程度**: P3
- **现状**: 三份 4 行 driver 各配 4-6 行 javadoc，合计约 45 行表达一个模式；`TaskStepBuilder` 三处收集循环仅容器类型不同，另有 `buildSequentialBody`/`buildForkBody` 两个单行转发。
- **风险**: 终态写入顺序（exception → setTaskStatus → saveTaskState）需三处同步保持，漏改一处产生终态不一致；单行包装让人误以为做过滤。
- **建议**: `driveTaskTerminal(taskState, status, err)` 收敛；收集循环抽 `collectActiveSteps(...)`；单行包装内联。
- **信心水平**: 确定（方法体逐行比对）
- **误报排除**: `buildParallelStep` 循环后续还有 set*，按"循环体重复"计入而非方法重复。
- **复核状态**: 未复核

### [维度01-15] ext 装饰器的配置读取/校验 helper 在 3 个文件中复制

- **文件**: `nop-task-ext/.../ratelimit/RateLimitTaskStepDecorator.java:51,63,76,89`；`retry/RetryTaskStepDecorator.java:61,74,86`；`timeout/TimeoutTaskStepDecorator.java:31,43`
- **证据片段**:
  ```java
  // RateLimit:51 readDouble / :63 readInt / :76 readBoolean / :89 invalidConfig
  // Retry:61 readInt / :74 readBoolean / :86 invalidConfig（与 RateLimit 版本基本相同）
  // Timeout:31 readLong / :43 invalidConfig
  ```
- **严重程度**: P3
- **现状**: 同一组 `TaskDecoratorModel` 属性读取 + 类型不符报错逻辑在三个装饰器类各写一份（`invalidConfig` 三份、`readInt` 两份、`readBoolean` 两份），每份 12-15 行；`Retry.readBoolean` 与 `RateLimit.readBoolean` 返回类型 `Boolean` vs `boolean`、默认值语义不同——同源复制后分叉。
- **风险**: 错误消息格式/异常码在三个装饰器间不一致；`readBoolean` 的 null 语义差异让"未配置"与"显式 false"在两处不同。
- **建议**: 抽公共 `TaskDecoratorConfigReader`，显式化两处 `readBoolean` 的 null 语义。
- **信心水平**: 确定（同名方法体文本比对，identical=False 部分已标注）
- **误报排除**: 明确标注非逐字重复部分不算复制；不建议未确认语义差异前强行合并。
- **复核状态**: 未复核

### [维度01-16] 注释掉的死代码残留在主源码中

- **文件**: `builder/TaskStepEnhancer.java:88-98`；`step/ExecutorTaskStepWrapper.java:19,34`
- **证据片段**:
  ```java
  // TaskStepEnhancer.java:88-98（11 行整体注释掉的方法）
  //    private IEvalAction buildValidator(ValidatorModel validatorModel) {
  //        if (validatorModel == null) return null;
  //        return ctx -> { BizValidatorHelper.runValidatorModelForValue(...); return null; };
  //    }
  // ExecutorTaskStepWrapper.java:19
      //  static final Logger LOG = LoggerFactory.getLogger(ExecutorTaskStepWrapper.class);
  // ExecutorTaskStepWrapper.java:34
              // LOG.info("in thread");
  ```
- **严重程度**: P3
- **现状**: 11 行完整方法被整体注释，另两处日志语句被注释保留（行 34 是调试残留）。
- **风险**: 读者需判断 validator 是待实现功能还是已废弃设计；被注释的 logger 让人以为日志被有意关闭。
- **建议**: 删除；若 validator 是待实现需求，建 backlog 条目记录而非留在实现文件。
- **信心水平**: 确定（直接读取）
- **误报排除**: 问题在于占据核心 builder 文件并暗示不存在的能力，非"不喜欢注释代码"。
- **复核状态**: 未复核

### [维度01-17] 命名与布尔陷阱：`requirePersistStateState` 拼写、`newTaskRuntime(task, saveState, ...)` 裸布尔、`OrmSessionTaskStepWrapper` 双布尔四分支

- **文件**: `impl/TaskFlowManagerImpl.java:92-93,109,117`；`nop-task-ext/.../orm/OrmSessionTaskStepWrapper.java:14`
- **证据片段**:
  ```java
   92:    public ITaskRuntime newTaskRuntime(ITask task, boolean saveState, IServiceContext svcCtx, IEvalScope scope) {
   93:        ITaskStateStore stateStore = saveState ? requirePersistStateState() : nonPersistStateStore;
  109:    private ITaskStateStore requirePersistStateState() {   // "StateState" 拼写
  // OrmSessionTaskStepWrapper.java:14
      public OrmSessionTaskStepWrapper(ITaskStep taskStep, IOrmTemplate ormTemplate, boolean newSession, boolean sync) {
  ```
- **严重程度**: P3
- **现状**: `requirePersistStateState()`（"State" 重复）两处调用；`newTaskRuntime` 第二个布尔在调用点呈现为 `newTaskRuntime(task, false, svcCtx)` 裸值；`OrmSessionTaskStepWrapper` 两个连续布尔形成 2×2 四组合。
- **风险**: 调用者读到 `false` 无法判断含义；四组合合法性只能读实现分支；拼写错误被搜索继承。
- **建议**: 改名 `requirePersistStateStore()`；布尔改枚举或拆方法；装饰器构造器用配置对象。
- **信心水平**: 确定（调用点枚举确认）
- **误报排除**: 布尔陷阱是本维度明确检查项，非纯命名偏好。
- **复核状态**: 未复核

### [维度01-18] 可观测性输出不一致：指标接口参数名与语义相反，多处日志缺少 task 上下文

- **文件**: `metrics/EmptyTaskFlowMetrics.java:22`；`GraphTaskStep.java:338`；`TaskStepExecution.java:493,510`；`LogEvalFunction.java:26`
- **证据片段**:
  ```java
  22:    public void endStep(Object meter, boolean endStep) {      // 形参叫 endStep，接口语义是 success
  // 对照同文件行 12：public void endTask(Object meter, boolean success)
  338: LOG.info("nop.task.run-graph-end:stepPath={},outputs={}", stepRt.getStepPath(), v.getOutputs());
  26: LOG.info("task.run-fail:stepName={},loc={}", stepName, loc, error);
  // 对照标准写法（CallStepTaskStep.java:79-80）：带 taskName/taskInstanceId/stepPath/runId/loc
  ```
- **严重程度**: P3
- **现状**: 同接口 `endTask(..., success)` 与 `endStep(..., endStep)` 形参语义命名相反；`run-graph-end`/`step-input`/`task.run-fail` 三条日志均无 `taskInstanceId`/`taskName`/`runId`，与本模块其他日志格式不一致。
- **风险**: 用 `taskInstanceId` 过滤排查时图级结束与失败日志漏掉；无法从形参名判断布尔含义。
- **建议**: 形参改名 `success`；三条日志补齐上下文键。
- **信心水平**: 确定（同文件/同模块正确写法可直接对照）
- **误报排除**: 不报中文日志/占位符风格差异，只报上下文键缺失与同接口命名冲突。
- **复核状态**: 未复核

### [维度01-19] 错误码 ID 拼写错误 `should-no-be`（且已进入 i18n bundle）

- **文件**: `nop-task/nop-task-core/src/main/java/io/nop/task/TaskErrors.java:67-69`
- **证据片段**:
  ```java
  ErrorCode ERR_TASK_ASYNC_RETURN_NEXT_STEP_SHOULD_NOT_BE_ASYNC =
          define("nop.err.task.step.async-return-next-step-should-no-be-async",   // should-no-be → should-not-be
                  "异步步骤的返回结果不应为ASYNC标记");
  // i18n 已同步该错误码：nop-runner/nop-cli-core/.../nop-cli-errors.i18n.yaml（en 与 zh-CN 两份）
  ```
- **严重程度**: P3
- **现状**: 常量名 `..._SHOULD_NOT_BE_ASYNC` 拼写正确，错误码字符串是 `should-no-be-async`（漏 `t`）；该 ID 已写入 en/zh-CN 两个 i18n 文件。
- **风险**: 按常量名搜 `should-not-be` 找不到日志/DB 中的错误码；修复需同步 i18n，只改一处会造成消息回退。
- **建议**: 若 ID 未被持久化消费，统一更正并同步两份 i18n；若有历史数据引用，保留 ID 并 javadoc 标注"历史拼写勿改"。
- **信心水平**: 确定（源码 + 两份 i18n 均已定位）
- **误报排除**: 非中文消息/i18n 约定问题，报的是常量名与 ID 字符串不一致。
- **复核状态**: 未复核

## 检查范围清单（第 1 轮）

### 完整读取
`TaskStepHelper.java`(386)、`SelectorTaskStep.java`(122)、`LoopNTaskStep.java`(233)、`TaskImpl.java`(307)、`TaskStepBuilder.java`(416)、`GraphStepBuilder.java`(29)、`GraphStepAnalyzer.java`(209)、`TaskErrors.java`(169)、`TaskStepStateBean.java`(生命周期方法全量)、`TaskFlowModel.java`(全文)

### 抽样读取
`TaskStepExecution.java`(178-396,480-520)、`GraphTaskStep.java`(60-95,246-351)、`DaoTaskStateStore.java`(150-200,335-360)、`SequentialTaskStep`、`LoopTaskStep`(155-185)、`TaskStepRuntimeImpl`(1-120)、`RateLimit/Throttle/CallStepTaskStep`、`ExecutorTaskStepWrapper`、`TaskStepEnhancer`、`LogEvalFunction`、`EmptyTaskFlowMetrics`、`TaskFlowManagerImpl`、`ITaskStepState`、`TaskStepReturn`、`TaskConstants`、`TaskStepModel`、`orm/_app.orm.xml`、`deploy/sql/mysql/_create_nop-task.sql`、ext 三个 Decorator、`NopException`、`ErrorMessageManager`、`ErrorCode`、`docs-for-ai/02-core-guides/{error-handling,code-style}.md`

### 零发现检查项
1. 无标签 break/continue、switch fall-through（`TaskStepBuilder` 各 case 均有 break）
2. `System.out`/`printStackTrace` 主源码零命中（仅 test）
3. ORM propId 空洞 → 跨模块平台惯例（nop-wf 同类）
4. ORM 未使用 domain（声明 20 / 使用 7）→ nop-wf/nop-job 同样模板化残留
5. 中文 ErrorCode 消息 → 平台 `@Locale("zh-CN")` + i18n bundle 约定
6. `TaskGenHelper` 疑似死码 → 经 `biz-gen.xlib` XPL 反射调用，非死码
7. `nop-task-api` 全文件、`nop-task-meta` 字典 yaml 带生成标记 → 排除
8. `GraphStepNode` 空集合 `removeAll(immutable)` → 推演不抛异常，不构成独立缺陷
9. `LoopTaskStep:162/177`、`LoopNTaskStep:223` 行号注释经核对仍准确 → 不报

### 计数双口径核对
| 结论 | 口径 A | 口径 B | 一致 |
|---|---|---|---|
| `TaskConstants` 零引用常量 = 9 | `rg -c` 逐 token（java/xml/xlib/xpl）命中均为 1 | `rg -l` 文件级均只命中自身 | 是 |
| 过程术语 `plan NNN` = 164 处 / 31 文件 | `rg -o \| wc -l` = 164 | `rg -l \| wc -l` = 31 | 是 |
| `check2`/`裁定` = 56 处 | `rg -o` 命中计数 | — | 单口径，仅作规模描述 |
| 长方法行数 | 括号配对脚本 | `wc -l`/签名行 grep | 是 |

### 未读/受限区域
`ai-dev/audits|plans|bugs|lessons` 按口径禁读；`docs/`、`docs-for-ai-old/` 未读；`nop-task-ext/_dump/` 快照未读；跨模块仅作单点惯例对照，未纳入审查范围。
