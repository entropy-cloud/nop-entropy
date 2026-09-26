# 维度 06：测试有效性（Test Effectiveness）— nop-task 深度审核

- **审核日期**: 2026-09-25
- **轮次**: 第 1 轮（初审）完成，待深挖与复核
- **基线**: `_tmp/audit-baseline.txt` — `./mvnw -f nop-task/pom.xml test` → BUILD SUCCESS，263 tests / 0 failures（core 144、ext 117、service 1、web 1）
- **方法**: 以 `ai-dev/skills/unit-test-antipatterns.md` 的反模式清单为尺子；先给强度，再报缺口

## 强度（先记账，避免只挑毛病）

- **恢复/终态契约覆盖扎实**: resume、terminal lifecycle、exception round-trip、suspend 6 用例、cross-restart hook、continuation-skip 各有专项测试（ext 117 个用例中 resume/reliability 系列占主体）
- **Race 专项存在**: `TestGraphDrainRace`（275 行）、`TestChildRuntimeCancelPropagation`、`TestTaskKilledTimeoutResumeE2E`
- **诚实断言风格**: `TestReliabilityDecorators` 普遍带 `fail("should throw...")` + 计数断言（`assertEquals(3, counter)`），注释记录修复前后可观测差异，反"断言异常即绿"的弱模式
- **常量对齐有守卫**: `TestPlan349Fixes:83-100` 断言 `TaskConstants` 与 `_NopTaskCoreConstants` 同值（但见 02-03：守卫真实位置与注释不符）

## 第 1 轮（初审）

### [维度06-01] 无任何测试断言错误诊断参数 → `TaskStepHelper.newError` 的 5 处不可达 `.param(...)` 在测试盲区

- **文件**: 测试全树 vs `nop-task-core/.../utils/TaskStepHelper.java:54-67`（见 01-01）
- **证据片段**:
  ```bash
  $ rg -n "ARG_NEXT_STEP|ARG_LIB_NAME|ARG_STEP_NAME|ARG_KEY" nop-task --glob '*/src/test/*'
  (0 命中；ARG_* 常量仅出现在 TaskErrors.java 与 main 源码)
  # 即：没有任何测试读取 NopException 的 params，全部只断言 errorCode / 计数 / 后置状态
  ```
- **严重程度**: P2
- **现状**: 全模块 263 个用例对异常只断言 `e.getErrorCode()` 或"抛了就行"，从不校验 `.param` 内容。这正是 01-01（`newError` 实为 thrower，5 个调用点的 `nextStep`/`libName`/`stepName`/`rate-limit key`/`loop begin-end-step` 参数静默丢失）能潜伏至今的直接原因——即便有"断言异常类型"的用例，也测不到参数缺失。
- **风险**: 错误诊断质量属"只在排障时被消费"的属性，无测试即无回归保护；同族问题（未来任何 `throw newError(...).param(...)` 写法）会再次静默复制。
- **建议**: 补 1-2 个参数断言用例（如 sequential 配置不存在的 `nextStepName`，断言 `e.getParams()` 含 `nextStep=xxx`）；顺带即把 01-01 钉死。
- **信心水平**: 确定（双关键词 0 命中）
- **误报排除**: 测试确实断言 errorCode（不算裸奔），但 params 层完全无覆盖是独立事实。
- **复核状态**: 未复核

### [维度06-02] nop-task 的 data-auth 配置全生命周期零测试 → 04-02 的坏标签断裂 19 个月无任何信号

- **文件**: 测试全树 vs `nop-task-app/.../auth/app.data-auth.xml`
- **证据片段**:
  ```bash
  $ rg -rn "data-auth" --glob '*Test*.java'   # （-n 普通检索）
  仅命中 nop-datav（2 个 auth 测试）与 nop-auth TestSqlLib（指定 data-auth-config-path）
  $ rg -l "data-auth" nop-task --glob '*Test*'    # 0 命中
  # 对照：TestSqlLib 正是"加载 data-auth 资源"这一动作的可复用模板，nop-task 未采纳
  ```
- **严重程度**: P2
- **现状**: 被 04-02 判为 P1 的配置断裂（引用已改名删除的 `GenFromModules` 标签、缺 `xpl:lib`）之所以能存在约 19 个月（改名 commit `8438c69a2f`，2025-02-21），直接原因是 app 启动/配置解析路径没有任何 nop-task 测试触达：`nop-task-app` 模块无测试、service 测试不加载 app auth 配置、web 测试只验 page。
- **风险**: 配置型缺陷（schema 演进、标签改名、xdef 收紧）在本模块属"静默失败区"——04-02 的两种结局（解析抛错 / 空模型 fail-open）都不会被 CI 捕获。
- **建议**: 加一个冒烟用例：`DslModelParser().parseFromResource("/nop/task/auth/app.data-auth.xml")` 断言成功 + gen-extends 展开后含模块级 `<objs>`；app 模块启动级 smoke 可复用 `NopTaskWebPagesTest` 的 `JunitBaseTestCase` 模式。
- **信心水平**: 确定（0 命中 + 模块测试分布核实）
- **误报排除**: 非"配置在 e2e 覆盖"——`rg data-auth nop-entropy-e2e` 仅命中 vfs-index 文件。
- **复核状态**: 未复核

### [维度06-03] `TestReflectionTaskStepBuilder.testBuild` 零断言：构建 + dump 即通过

- **文件**: `nop-task-core/src/test/java/io/nop/task/reflect/TestReflectionTaskStepBuilder.java:27-35`
- **证据片段**:
  ```java
  @Test
  public void testBuild() {
      GraphTaskStepModel stepModel = new ReflectionTaskStepBuilder().buildTaskStepGraph(classModel, taskGraph, "myFlow");
      new GraphStepAnalyzer().analyze(stepModel);
      XNode node = stepModel.toNode();
      node.dump();          // 仅打印——无 assertEquals / assertThrows / assertDoesNotThrow
  }
  ```
- **严重程度**: P3
- **现状**: 全类 0 处 `assert`（ZERO_ASSERT 扫描命中）。`buildTaskStepGraph` 若把 `enterSteps/next/outputs` 映射错、`analyze` 若漏掉校验，测试照样绿；`dump()` 是人工观察手段而非断言。
- **风险**: 注解→模型的反射映射（`@TaskStep(timeout=3, concurrent=true, next="step2")`、`@TaskStepOutput(exportAs="A2")` 这些字段恰恰是转换易错点）无回归保护。
- **建议**: 对 `stepModel` 断言关键字段（step1.timeout==3、concurrent、next、outputs 映射、enterSteps/exitSteps 集合），10 行即可。
- **信心水平**: 确定（全类直读）
- **误报排除**: "smoke 测试无断言"若能因抛异常失败则部分有效——本测试连 `analyze` 的预期报错路径都未走，只要不抛就绿，无正向断言。
- **复核状态**: 未复核

### [维度06-04] `TestReliabilityDecorators` 内注释与代码/兄弟用例自相矛盾（stale comment）

- **文件**: `nop-task-ext/src/test/.../TestReliabilityDecorators.java:89-93` 对照 `:110-119`、`TaskStepStateBean.fail:54-56`
- **证据片段**:
  ```java
  // :89-93（retry_exhaustedHonestThrow 注释，声称现状）：
  // "nop-task 既有 in-memory TaskStepStateBean 不在 fail() 中保存 exception 引用，
  //  因此 RetryPolicy.getRetryDelay 接收 null exception 跳过 isRecoverableException 判定…"
  // :110-119（同文件 retry_recoverableExceptionRetriedE2e 注释，声称已修复）：
  // "plan 247 修复后 state.exception() 返回真实异常…"
  // 代码事实：TaskStepStateBean.fail → exception(exception) 保存引用（与 :110-119 一致）
  ```
- **严重程度**: P3
- **现状**: 同一测试类内两段注释对"state.fail 是否保存 exception"给出相反陈述，`:89-93` 描述的是 plan 247 修复前的世界，修复后未同步更新；且该段还写"不在本计划 scope 内修正"，会让读者以为缺陷仍存在。
- **风险**: 后续维护者/审计 agent 据 stale 注释推断"分类功能缺失"，导致错误决策（重复修复或误报）；与 02-03（常量注释指向不存在的测试）同类——**测试注释是一级文档，其失真直接污染下游分析**。
- **建议**: 删除/改写 `:89-93` 为历史背景（"plan 247 前曾…"），或直接引用 `TestTaskStepStateBeanExceptionPersistence` 作为该语义的唯一权威。
- **信心水平**: 确定（三处对照直读）
- **误报排除**: 非"措辞差异"——两注释对同一 API 行为的断言互斥，必有一假。
- **复核状态**: 未复核

### [维度06-05] `rateLimit_realLimitingFires` 对第一次调用"通过或拒绝"双结局均放行，首调许可语义无断言

- **文件**: `nop-task-ext/src/test/.../TestReliabilityDecorators.java:458-484`
- **证据片段**:
  ```java
  try {
      runTask("test/rate-limit-decorator-fires");        // ← 无 fail()：允许成功也允许抛
  } catch (NopException e) {
      // "初始 permit 也可能因 Guava 实现细节被拒，只要 errorCode 匹配即视为限流生效"
      assertEquals(ERR_TASK_REQUEST_RATE_EXCEED_LIMIT.getErrorCode(), e.getErrorCode(), ...);
  }
  // 第二次调用有 fail() 守卫 ✓；第一次没有
  ```
- **严重程度**: P3
- **现状**: 与同类用例（`:76-77` 等 10 处均带 `fail()`）不一致：第一次调用若因回归从"允许 1 个初始 permit"变成"首调即拒绝"，测试依旧绿；反之若限流完全失效导致第二次通过，会被 `fail()` 抓住——即测试只锁住了半边语义。注释把"双结局"归因于 Guava 实现细节，但 `DefaultRateLimiter` 是本模块可控封装，首调行为应可确定。
- **风险**: 限流器初始化回归（如 permit 数算错为 0）不可见；注释也为弱断言提供了长期免责理由。
- **建议**: 明确 `DefaultRateLimiter` 首调契约并断言之（首调必过 → catch 分支加 `fail`）；或拆为两个用例分别钉死"首调许可"与"次调拒绝"。
- **信心水平**: 确定（结构与兄弟用例对照）
- **误报排除**: 非"竞态导致不确定"——rps=0.0001、maxWait=0 的首调结果由封装类构造决定，确定可断言。
- **复核状态**: 未复核

### [维度06-06] 并发交错测试缺口：05 维度三条 P2/P3 场景（kill-vs-完成、同 step 行乐观锁、挂起态 kill）均无对应用例

- **文件**: 测试全树（race 系列仅 `TestGraphDrainRace`、`TestChildRuntimeCancelPropagation`、`TestTaskKilledTimeoutResumeE2E`）
- **证据片段**:
  ```bash
  $ rg -ln "new Thread|CountDownLatch|CyclicBarrier" nop-task --glob '*/src/test/*'
  (race 系列文件均未使用显式并发原语——交错靠 future 时序自然发生)
  # 05-01（终态守卫竞态）、05-02（同 stepPath 行并发 save）、05-03（挂起态 kill）：
  $ rg -n "KILLED" nop-task --glob '*/src/test/*'   # kill 覆盖均为"执行中 kill + resume"序列
  ```
- **严重程度**: P2
- **现状**: 263 个用例全部是**串行时序**下的断言；`first-terminal-wins` 是 owner 文档明确承诺的并发语义（`nop-task.md:66`），但没有任何用例主动构造"kill 线程 × 完成回调线程"、"两分支并发写同 step 行"、"SUSPENDED 后 kill"三种交错。现有 race 测试只覆盖 graph 计数与子运行时取消传播。
- **风险**: 05 维度的核心缺陷（守卫非原子、乐观锁冒泡、kill 不转移）因此在 CI 中结构性不可见；修复后也无回归保护（极易被再次重构破坏）。
- **建议**: 优先补 3 个最小交错用例：① 双线程分别调 `driveTaskCompleted`/`driveTaskKilled`（可直接测 `TaskImpl` 驱动函数，不必起真线程竞速——循环 N 次交替调用断言终态+exception 同源）；② `DaoTaskStateStore.saveStepState` 同 key 并发（2 线程 CountDownLatch 齐射）断言不抛乐观锁异常或按 05-02 建议重试；③ suspend 后 `cancel()` 断言状态迁移。
- **信心水平**: 确定（用例清单全量核对；三种交错均无匹配文件）
- **误报排除**: 非"并发测试天然难写"——建议的最小用例均可用同步交替调用模拟，不依赖 timing。
- **复核状态**: 未复核

### [维度06-07] 7 个近似重复的快照型测试 store 助手，模式未收敛

- **文件**: `nop-task-core/src/test/.../state/{FullSnapshotTaskStateStore,SnapshotTaskStateStore,TaskLevelSnapshotTaskStateStore}.java` + `nop-task-ext/src/test/.../reliability/{StateCapturingTaskStateStore,ResumeCapableTaskStateStore,TaskResumeSnapshotTaskStateStore,SnapshotResumeTaskStateStore}.java`
- **证据片段**:
  ```bash
  $ rg -l "TaskStateStore" nop-task/*/src/test | xargs -n1 basename | sort
  FullSnapshotTaskStateStore / SnapshotTaskStateStore / TaskLevelSnapshotTaskStateStore
  StateCapturingTaskStateStore / ResumeCapableTaskStateStore / TaskResumeSnapshotTaskStateStore
  SnapshotResumeTaskStateStore   ← 7 个助手 vs 22 个测试类
  ```
- **严重程度**: P3
- **现状**: 命名高度相似（Snapshot/Resume/StateCapturing/Full/TaskLevel 任意组合），职责差异仅能靠逐个读源码分辨；每个测试作者按需新造一个而非复用参数化基类。
- **风险**: 后续测试者不知有现成助手而第 8 个复制粘贴；语义细微差异（capture 时机、是否回放）一旦漂移，各用例实际测的"恢复语义"并不等价，断言跨文件不可比。
- **建议**: 收敛为一个参数化助手（capture-scope × resume-capability 两个维度开关），或至少在 `state/` 包头 javadoc 列一张"助手 × 语义"对照表。
- **信心水平**: 确定（文件清单与命名）
- **误报排除**: 各助手确有职责差异（非纯重复），故报"未收敛/无索引"而非"删除冗余"。
- **复核状态**: 未复核

### [维度06-08] 模块测试分布失衡：service 仅 1 用例、dao 模块 0 测试树、dao 的测试寄居 ext 且包名越界

- **文件**: `nop-task-service/src/test/.../TestTaskFlow.java`（唯一用例）、`nop-task-dao`（无 `src/test`）、`nop-task-ext/src/test/.../dao/store/TestTaskExceptionRegistry.java`、`ext/dao/TestTransactionDecorator.java`
- **证据片段**:
  ```bash
  # 基线计数：core 144 / ext 117 / service 1 / web 1
  # dao 模块测试：find nop-task-dao -path "*/src/test/*" → 0 文件
  # 越界包名（被测类在 nop-task-dao，测试在 nop-task-ext 测试树）：
  #   package io.nop.task.dao.store;   ← 文件位于 nop-task-ext/src/test/java/io/nop/task/dao/store/
  ```
- **严重程度**: P3
- **现状**: ① service 层唯一用例测的是自定义 beanContainer，4 个裸 CrudBizModel 的保存/更新链（04-01 的字段写入面）在 service 模块零覆盖——04-01 的"status 可被 CRUD 写入"这一事实没有任何测试视角拦截；② `DaoTaskStateStore`（850 行）的 6 个 round-trip 测试全部位于 ext 模块（依赖方向 ext→dao 可编译，但模块职责错位）；③ `TestTaskExceptionRegistry` 的包名 `io.nop.task.dao.store` 与所在模块不一致，按包导航会找错模块。
- **风险**: 模块级 coverage 报告失真（dao 显示 0%，ext 虚高）；改 dao 时默认不跑"自己模块"的测试（需意识到跑 ext）。
- **建议**: dao 测试迁回 `nop-task-dao/src/test`（若 ext 测试依赖 ext 专属 fixture 再例外说明）；service 补 1 个 CRUD 字段写入用例（顺带钉死 04-01）。
- **信心水平**: 确定（文件树 + 基线计数 + 包名核对）
- **误报排除**: 依赖方向允许测试跨模块放置，但"被测类所在模块 0 测试 + 包名伪装成本模块"仍是导航与工具链的真实成本。
- **复核状态**: 未复核

### [维度06-09] 非测试驱动类混入 `src/test`（codegen/fixture），且与真测试同树

- **文件**: `nop-task-codegen/src/test/.../NopTaskCodeGen.java`、`nop-task-web/src/test/.../NopTaskWebCodeGen.java`、`nop-task-service/src/test/.../{MyDemoHandler}.java`、`nop-task-ext/src/test/.../demo/BookOrder.java`
- **证据片段**:
  ```bash
  $ rg -n "@Test" NopTaskCodeGen.java NopTaskWebCodeGen.java
  (0 命中——两个类无任何 @Test，是手工触发的代码生成驱动)
  # ZERO_ASSERT 扫描同样命中：NopTaskWebCodeGen / NopTaskCodeGen / MyDemoHandler / BookOrder …
  ```
- **严重程度**: P3
- **现状**: codegen 驱动（`*CodeGen`）与 demo fixture（`BookOrder`、`MyDemoHandler`）与 JUnit 测试同树，靠 surefire 的 `Test*` 命名约定隐式排除；`@Test` 检索确认它们不执行。
- **风险**: ① `rg src/test` 统计口径的"测试数"被污染（本次审计的 ZERO_ASSERT 扫描即先误报后人工剔除）；② 若 surefire includes 模式收紧（如 `**/*Test.java` 之外的自定义模式），这些类可能被当测试装载报初始化错误；③ 阅读者按目录结构推断"codegen 有测试"。
- **建议**: 移到 `src/main`（codegen 本是可复用工具）或独立 `src/tools`；fixture 类加清晰命名（`*Fixture`）并在包级 README/注释说明。
- **信心水平**: 确定（@Test 检索 0 命中）
- **误报排除**: 非"辅助类不该在测试树"（test fixture 属正常）——本条针对**无可执行断言的工具驱动类**，与 fixture 性质不同。
- **复核状态**: 未复核

### [维度06-10] 异步/超时/限流用例依赖墙钟 `Thread.sleep`，CI 抖动下有假绿/假红风险

- **文件**: `nop-task-ext/src/test/.../reliability/StepStateTestHelper.java:36-47`、`TestReliabilityDecorators.java:441-444`
- **证据片段**:
  ```java
  public CompletableFuture<String> asyncSuccess(String value, long delayMs) {
      return CompletableFuture.supplyAsync(() -> {
          try { Thread.sleep(delayMs); }          // ← 制造"异步未完成"窗口，靠真实时间
          catch (InterruptedException e) { Thread.currentThread().interrupt(); }
          return value;
      });
  }
  // TestReliabilityDecorators:441-444："step 体 sleep 2000ms，timeout=200ms，应真实超时失败"
  ```
- **严重程度**: P3
- **现状**: retry 走 thenCompose 路径的判定依赖 `delayMs > 0 使 isDone()=false`（注释自证）；timeout 用例用 2000ms vs 200ms 的绝对时间差。全部 timing 参数为固定毫秒，无重试/无时间源注入。
- **风险**: CI 负载高时 sleep 漂移 → 本应"未完成"的 future 提前完成（假绿：走了快捷路径却未察觉）或 timeout 触发延迟（假红）；2000ms 占用还拉长套件时长。
- **建议**: 改用可控门闩（`CompletableFuture` 手动 complete、`CountDownLatch` 协作）替代 sleep 制造时序；超时用例通过注入短 tick 的 scheduled executor 或放宽断言至相对关系（timeout < body 且允许 N 倍余量）。
- **信心水平**: 确定（代码与注释直读）；未统计历史 flaky 率（无法从静态得出）。
- **误报排除**: 非"异步测试必然用 sleep"——同仓库 `TestGraphDrainRace` 即用 future 协作完成交错，证明替代模式可用。
- **复核状态**: 未复核

### [维度06-11] `retry_exhaustedHonestThrow` 捕获面宽于契约（`catch (Exception)` + 仅 `assertNotNull`），错误异常类型可假绿

- **文件**: `nop-task-ext/src/test/.../TestReliabilityDecorators.java:96-105`
- **证据片段**:
  ```java
  try {
      runTask("test/retry-decorator-exhausted");
      fail("should throw after retry exhausted");
  } catch (Exception e) {                      // ← 宽捕获
      assertNotNull(e, "exception must propagate after retry exhausted");   // ← 类型无关
  }
  assertEquals(3, counter().get(), ...);       // ← 计数在本例仍会满足（3 次执行后无论抛什么）
  // 对照：同文件 :78/:131/:160 等兄弟用例均 catch (NopException e) 并进一步断言 errorCode/isBizFatal
  ```
- **严重程度**: P3
- **现状**: 期望契约是"抛 `ERR_TASK_RETRY_TIMES_EXCEED_LIMIT` 类 NopException"，但本用例只要抛出**任何** Exception（NPE、ORM 异常、类转换错误）+ 执行计数=3 即绿。与兄弟用例的严格度不一致（其它用例锁 errorCode/`isBizFatal`）。
- **风险**: 耗尽路径被替换为意外异常类型时（例如 05-02 的乐观锁异常恰在第 3 次触发）测试无法区分——把缺陷当通过。
- **建议**: 收窄为 `catch (NopException e)` + `assertEquals(ERR_TASK_RETRY_TIMES_EXCEED_LIMIT.getErrorCode(), e.getErrorCode())`，与兄弟用例对齐。
- **信心水平**: 确定（三行结构直读 + 兄弟对照）
- **误报排除**: `fail()` 守卫在（无异常会红），故非完全裸奔——问题仅在"错类型的异常也绿"。
- **复核状态**: 未复核

## 检查范围清单（第 1 轮）

### 全量测试清单（81 个测试类/文件，逐一定位用途）
- **core（144 用例）**: builder 分析 3、impl 生命周期/契约 10（含 Plan349/Audit fixes/GlobalStats/MetricsGuard/Suspend/RepeatedStep/ChildCancel）、state 恢复 6、step 结构 9（graph×3/suspend×2/parallel/executor/continuation/cancel-driver）、utils retry×4、reflect×1、helpers（FakeStepRt/Snapshot×3）
- **ext（117 用例）**: reliability 系列 20+（decorator 行为、DB round-trip×6、resume×7、lifecycle hook×2）、dao store 1、transaction 1、demo 1
- **service（1）**: custom bean loader；**web（1）**: validateAllPages
- **非测试**: NopTaskCodeGen、NopTaskWebCodeGen（0 @Test）

### 扫描动作与口径
| 反模式（unit-test-antipatterns.md） | 命令口径 | 结论 |
|---|---|---|
| 零断言测试 | 全测试树 `assert` 计数=0 扫描 | 17 文件命中 → 人工分类：9 个合法 fixture/helper、2 个 codegen（06-09）、1 个真测试（06-03）、余为隐式断言（validateAllPages 抛错即失败，可接受） |
| 吞异常/空 catch | catch 块结构检索 | 测试内 catch 均带断言或 fail() 守卫，无空 catch ✓ |
| `assertTrue(true)`/@Ignore/@Disabled | 关键词检索 | 0 命中 ✓ |
| 宽捕获+弱断言 | catch 类型 vs 断言内容抽样 | 06-11 命中 1 处；其余兄弟用例均锁 errorCode |
| 硬编码 sleep | `sleep(` 检索 | 06-10 命中 2 处（helper + timeout 用例） |
| 无失败路径断言 | `fail(` 使用频次 | 11 处 fail() 守卫 ✓，06-05 漏 1 处 |
| 并发原语使用 | `new Thread/CountDownLatch/CyclicBarrier` | 测试树 0 命中 → 06-06 缺口 |
| 错误诊断断言 | `ARG_*` 检索 | 0 命中 → 06-01 |
| 配置解析覆盖 | `data-auth` in tests | 0 命中 → 06-02 |
| 模块分布 | 基线计数 + 文件树 | 06-08 |

### 零发现项
1. 空 catch / 吞断言：测试内 catch 全部有 fail() 或断言承接
2. `@Disabled/@Ignore/assertTrue(true)`：0 命中
3. 重复粘贴的巨型 fixture：单测试最长 640 行（TestReliabilityDecorators），方法级组织清晰、注释带修复前可观测差异
4. 断言异常即绿（无 fail 守卫版）：11/12 抛错型用例带 `fail()`（例外 1 处已单列 06-05）
5. 基线本身：263/263 全绿、0 skip（测试是活的，非长期红）
