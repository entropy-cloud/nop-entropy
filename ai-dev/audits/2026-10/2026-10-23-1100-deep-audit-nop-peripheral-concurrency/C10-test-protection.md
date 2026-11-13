# C10 并发正确性的测试保护力 — 初审（盲审·全单元单批·最后批次）

- **审计单元**: nop-peripheral = nop-dev-tools(155f/16,376L) + nop-frontend-support(84f/6,751L) + nop-demo(94f/3,760L) + nop-entropy-e2e(0f，零 Java 审计面)；全单元 333f/26,887L
- **维度**: C10 并发正确性的测试保护力（`ai-dev/skills/concurrency-resource-audit-prompt.md` C10 正文 + 附录 A）
- **批次**: 全单元单批·C10 最后批次（依赖 Phase 4 复核后的 C1/C6 缺陷清单做变异推演输入）
- **CL 前缀**: 本维度集外线索使用 `nop-peripheral-CL-3NN`
- **盲审声明**: 本初审为盲审——除本 prompt 维度正文、本目录 `C1-shared-mutable-state.md`、`C6-resource-lifecycle.md`（C10 依赖的前序缺陷清单，属派发方明示允许读取范围）与 `dispatch-log.md` 外，未读取 `ai-dev/audits/` 其他目录及 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何文件。全部测试面盘点与生产锚点均为本审计者在 worktree（分支 concurrency-audit-remediation，基线 a20ca25130）独立读取/grep 核实。所有条目 `复核状态: 未复核`。
- **编号规则**: C10 发现 `C10-001` 起；维度集外线索以 `nop-peripheral-CL-3NN` 编号附记。
- **校准声明（本维度口径）**:
  - **变异推演口径**: 对 Phase 4 复核保留的 12 条缺陷逐条回答「缺陷被修复后又被复 introduced（变异回退）时，现有哪个测试会红？」；同时反向核对「补上正确实现」不会误红任何既有测试（确认无测试钉死现状缺陷行为）。无测试会红 → 登记「缺保护」。前序缺陷本体不重复计数，本维度只计保护力缺口。
  - **watch-only 口径**: P3 级缺陷的缺保护与 demo 降口径缺陷（C1-007、C6-R2-001）的缺保护，按本单元 C1/C6 校准惯例（demo 不按生产标准判级、P3 后果轻微单次自愈）记 **watch-only**，不立案计数——为其补 P2 级回归测试的投入产出比低于 P1/P2 缺陷面；仅在修复对应 P3 缺陷的同一 diff 内顺带补测。P1/P2 缺陷的缺保护照常立案。
  - **同站点防双计**: C1-004 ≡ C6-003（ProjectFileHelper 静态缓存，Phase 4 已裁定归并）的缺保护只登记一条。
  - **「测试」范围**: 各模块 `src/test` 下 JUnit 面 + nop-entropy-e2e Playwright 面。**结构性事实**：nop-idea-plugin 为 Gradle 独立构建（`nop-dev-tools/pom.xml:20-24` 的 modules 仅含 nop-api-debugger/nop-maven-shaded-plugin/nop-xlang-debugger），其 16 个测试文件不参与 `./mvnw test` 门禁（详见 CL-301）。
  - **正面样板承认**: 本单元存在 1 个高质量真并发回归测试（TestPageProviderPermissionsTransform.concurrentGetPageNeverMutatesCachedPageModel），它保护的是**已修复**的历史缺陷（copy-on-write 转换），与 12 条在案缺陷无交集——计入盘点，不计入保护力。
- **误报基线遵守**: 平台基础设施（nop-kernel/nop-rpc-simple/nop-socket）自身测试面不在本单元要求范围；「有测试但测试对象不同」不算保护（见 TccGatewayInterceptorTest 对 C1-007 的甄别）；测试可运行性以当前构建体系为准（Gradle-only 模块的测试不视为 Maven 门禁内的保护力）。

---

## 一、既有测试盘点表（测试文件 × 保护范围 × 判定）

四模块 `src/test` Java 测试共 32 文件 + e2e TS 面。与并发/资源/异步相关者 6 个，其余 26 个为功能性/模型/代码生成测试（逐一甄别无并发断言，不列入下表；甄别依据：grep `Thread|sleep|Latch|Executor|CompletableFuture|syncGet|close|destroy` 全量命中点核对）。

| 测试文件 | 保护范围 | 判定 |
|---|---|---|
| `nop-dev-tools/nop-xlang-debugger/src/test/java/io/nop/xlang/debugger/TestXLangDebugger.java` | 进程内 XLangDebugger 单 debuggee 线程端到端：断点注册、runToPosition（L50）、resume/stepInto/stepOver/stepOut（L80/85/93/109）、表达式求值（L89-107）、mute（L117-118）；通知经 CollectNotifier 的 BlockingQueue.take()（L77-119）阻塞等待 | **单线程挂起/恢复 happy path 唯一覆盖**；无多线程汇聚、无 resume×挂起交接竞态注入、无 suspendedThreads 规模/清理断言、会话时长 < 30s（cleanup 一次性调度不可观测） |
| `nop-dev-tools/nop-xlang-debugger/src/test/java/io/nop/xlang/debugger/TestRemoteDebugger.java` | 同上场景经 SimpleRpcServer/SimpleRpcClientFactory socket 传输（setUp L48-72）；`tearDown`（L141-147）为全单元**唯一正确资源配对参照**（clientFactory.destroy → server.stop → DestroyHelper.safeDestroy） | **传输层 happy path 覆盖 + 配对参照**；teardown 动作齐全但**零断言**（无线程退出/socket 关闭/注册表空校验）；不覆盖插件侧 XLangDebugConnector |
| `nop-dev-tools/nop-xlang-debugger/src/test/java/io/nop/xlang/debugger/TestDebugValueHelper.java` | DebugValueHelper 纯函数回归（数组/Map 展开，L26-65） | 非并发面，闭合 |
| `nop-dev-tools/nop-xlang-debugger/src/test/java/io/nop/xlang/debugger/CollectNotifier.java` | 测试替身（LinkedBlockingDeque 收集通知，L16-57） | 测试基础设施，闭合 |
| `nop-dev-tools/nop-maven-shaded-plugin/src/test/java/io/nop/maven/plugin/shaded/TestXdslResourceTransformer.java` | shade 转换器单线程冒烟（L31-41） | 非并发面，闭合 |
| `nop-dev-tools/nop-idea-plugin/src/test/**`（16 文件：completion/doc/lang/utils 面） | PSI 补全/解析/重命名/文档 | **与 12 条在案缺陷的 idea-plugin 站点（debugger/services/utils 三包）零交集**；且 Gradle-only 不入 Maven 门禁（CL-301） |
| `nop-frontend-support/nop-js/src/test/java/io/nop/js/TestJavaScriptService.java` | JavaScriptService 正常路径 5 测试：start → invokeAsync → `FutureHelper.syncGet`（L58/68/85/97/112）→ stop（L60/71/87/102/117）；testSpeed 循环 10 次（L78-87） | **只覆盖「先完成后停」顺序**；无停机窗口交叠提交、无 refreshConfig 收缩、无 worker 线程终止断言、无 future 兜底完成断言——C1-002 的窗口结构性不可达 |
| `nop-frontend-support/nop-js/src/test/java/io/nop/js/TestLogOutputStream.java` | LogOutputStream 单线程 flush（L22-30） | 非并发面，闭合 |
| `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestPageProviderPermissionsTransform.java` | **全单元唯一真并发回归测试**：`concurrentGetPageNeverMutatesCachedPageModel`（L131-166）——8 线程 + CountDownLatch 起跑门（L137-139）+ 确定性缓存隔离断言（L161-165）；best-effort 部分有自aware注释（L153-155） | 正面样板（保护已修复的 copy-on-write 缺陷）；与 12 条在案缺陷无交集 |
| `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestWebPageExporter.java` | `testThreadedExportMatchesSerialResult`（L116-130）：threadCount=2（L122）与串行导出结果等价性 | 并发相邻（并行渲染等价性）；仅 threadCount=2 一个值，无边界 |
| `nop-demo/nop-spring-gateway/src/test/java/io/nop/demo/gateway/TccGatewayInterceptorTest.java` | TccGatewayInterceptor 单元测试（Mockito mock 引擎，L30-163） | **不覆盖 DemoServiceBizModel.RESOURCE_STORE**（该类位于 nop-spring-gateway-demo-service，该子模块无 src/test）——C1-007 零保护 |
| `nop-entropy-e2e/packages/**`（Playwright TS） | job/auth/web/code 业务流；`fault-tolerance.spec.ts` 属 nop-job 单元 | 与调试器/JS 引擎/插件面零交集，闭合 |

**盘点结论**：并发/资源保护力集中于 nop-xlang-debugger 的 2 个调试器 happy-path 测试与 nop-web 的 1 个真并发回归测试；**9 个有主源码生产线程协作面的类（XLangDebugProcess/XLangDebugConnector/NopProjectService/ProjectFileHelper/XLangBreakpointHandler/DynamicJsLoader/ReportController/DemoServiceBizModel/JavaScriptWorker）零测试触及**（grep `ProjectFileHelper|NopProjectService|XLangBreakpointHandler|XLangDebugProcess|XLangDebugConnector|DynamicJsLoader|ReportController|DemoServiceBizModel|JavaScriptWorker` 于四模块 src/test 零命中）。

---

## 二、保护力矩阵（缺陷 ID × 既有测试 × 变异结论）

变异命题：缺陷被修复后，若修复被回退（缺陷复 introduced），现有套件中哪个测试会红？

| 缺陷（级别） | 站点 | 最近既有测试 | 变异结论 | 反向核对（补正确实现是否误红） |
|---|---|---|---|---|
| **C1-002**（P1） | JavaScriptWorker 关闭窗口任务搁浅 | TestJavaScriptService 5 测试（全部 stop-after-syncGet 顺序） | **无测试**——无任何测试在停机窗口内提交任务，搁浅 future 不可观测；stop 后无 future 完成/线程终止断言 | 不会误红（无测试钉死关闭语义） |
| **C6-001**（P1） | 插件调试会话 socket 从不关闭 | TestRemoteDebugger.tearDown（正确配对参照，但测试对象是调试器+传输层，非插件 connector） | **无测试**——XLangDebugConnector/XLangDebugProcess 零测试触及；idea-plugin Gradle-only 不入门禁 | 不会误红 |
| **C1-001**（P2） | 挂起交接非原子（幽灵挂起/吞命令） | TestXLangDebugger.testDebug、TestRemoteDebugger.testDebug | **假保护（偶发）**——「吞 step 命令」变体在消息时序交叠时可使 testDebug 断言 `stepInto:[1:0:0:0]b` 偶发收到 `break:[21:...]a` 而红（L85-118），但概率极低且不可复现；「幽灵挂起」变体（thread.suspended 永久 true）在两测试中**零观测点**（无 getSuspendedThreads 悬挂项断言），单 debuggee 线程场景下次真实挂起即自愈，消息序列不变 | 不会误红（现有测试对正确/错误实现均绿） |
| **C1-003**（P2） | NopProjectService 全局注册表交叉销毁 | 无 | **无测试**——NopProjectService 零测试触及；多项目交错场景需 IntelliJ harness | 不会误红 |
| **C1-004 ≡ C6-003**（P2） | ProjectFileHelper 静态缓存 Project 泄漏 | 无 | **无测试**——ProjectFileHelper 零测试触及；Gradle-only | 不会误红 |
| **C6-002**（P2） | suspendedThreads 一次性清理只进不出 | TestXLangDebugger/TestRemoteDebugger（会话 < 30s 即结束；close 清空注册表但无断言） | **无测试**——一次性 `schedule` vs `scheduleAtFixedRate` 的变异（回退为一次性）在任何现有测试中不可观测：测试会话短于 30s，死线程清理时机无断言 | 不会误红 |
| C1-005（P3） | muted 非 volatile | 无（XLangBreakpointHandler 零测试，Gradle-only） | 无测试 → **watch-only**（W-1） | 不会误红 |
| C1-006（P3） | tempBreakpoint 检查-清除非原子 | TestXLangDebugger L50 / TestRemoteDebugger L85 的 runToPosition（均单 debuggee 线程） | 无测试会红（缺陷需 ≥2 线程同行汇聚）→ **watch-only**（W-2） | 不会误红 |
| C1-007（P3，demo） | RESOURCE_STORE 检查-行动非原子 | 无（nop-spring-gateway-demo-service 无 src/test；TccGatewayInterceptorTest 为另一模块另一类） | 无测试 → **watch-only**（demo 降口径，W-3） | 不会误红 |
| C1-R2-001（P3） | sendStack 裸读 server 字段 | TestRemoteDebugger（无停机×断点命中交叠） | 无测试会红 → **watch-only**（W-4） | 不会误红 |
| C6-R2-001（P3，demo） | ReportController 临时文件生命周期 | 无（ReportController 零测试触及） | 无测试 → **watch-only**（demo 降口径，W-5） | 不会误红 |

**矩阵结论**：12 条在案缺陷中 **P1×2 + P2×4（含同站点合并计 1 条）共 6 个保护面缺口全部无确定性保护**；C1-001 存在低概率偶发红通道（假保护）；P3/demo 5 条 watch-only。无任何一条缺陷存在「修复会误红」的过约束测试——即现有套件对全部 12 条站点**零钉死力**。

---

## 三、发现条目（缺保护立案，附录 A 格式）

### [C10-001] JavaScriptWorker 关闭路径任务搁浅（C1-002，P1）无回归保护：测试套件结构性不可达该窗口

- **文件**: `nop-frontend-support/nop-js/src/test/java/io/nop/js/TestJavaScriptService.java:47-118`（对照缺陷站点 `nop-frontend-support/nop-js/src/main/java/io/nop/js/engine/JavaScriptWorker.java:98-103,283-316`）
- **证据片段**:
  ```java
  // 全部 5 个测试的唯一形态：先 syncGet 等待完成，再 stop —— 提交与关闭零交叠
  String result = (String) FutureHelper.syncGet(future);   // L58
  System.out.println(result);
  service.stop();                                          // L60
  ```
- **严重程度**: P2（缺保护力，按 C10 正文口径立项；缺陷本体 C1-002 为 P1）
- **并发场景**: 缺陷本体为调用方线程（invokeAsync → execute L99 检查 → L102 入队）与关闭方线程（doStop/refreshConfig → worker run() finally L309-315 cancelAllTasks 排空）的窗口交叠；测试套件中不存在任何并发于 `service.stop()` 的提交。
- **触发时序**（变异不可观测论证）: 现有 5 测试均为 start → invokeAsync → syncGet（阻塞至完成）→ stop 严格串行；变异回退（移除任何「worker 退出后兜底完成存量 future」的正确实现）后，没有任何测试存在「在途/窗口内提交」的 future 可供断言其完成性——套件全绿。反向：补上正确实现亦不误红（无测试钉死当前关闭行为）。
- **现状**: 零测试触及 JavaScriptWorker 类本身（grep 证实）；无 future 完成性断言、无 `getExecuteThread()` 终止断言、无 `getWaitingTasks()==0` 停机后断言。
- **风险**: P1 缺陷修复后将无回归防线；C1-002 的修复属时序敏感改动（execute 复查/cancelAllTasks 兜底），恰是最容易被后续重构无声破坏的形态。
- **建议**: 新增 `testShutdownDuringInflightSubmit`：workerCount=1 服务 start 后，另起线程循环提交 `invokeAsync`（每次 `orTimeout(5s)`），主线程在提交进行中调用 `stop()` 与 `shutdownGracefully()` 各一轮；断言 (a) 每个 future 在超时内完成（正常或异常均可，禁止永久挂起）；(b) stop 返回后 worker 线程终止（`worker.getExecuteThread()` 为 null 或 !isAlive，轮询至多 5s）；(c) `getWaitingTasks()==0`。窗口命中概率随提交轮次放大（L99-L102 检查-入队窗口在连续提交下高概率被击中）。
- **失效面枚举**（F3 同族清扫）: 缺陷本体失效面（schedule 全部提交点 + close 关闭任务，见 C1-002）全部落入同一缺口；测试面无例外兄弟——nop-js 仅 2 个测试文件，另一个（TestLogOutputStream）为非并发面。同族关闭入口 refreshConfig（JavaScriptService.java:96-116）同样零测试。
- **信心水平**: 确定（套件对缺陷站点零触及经 grep 与逐文件读取证实）
- **误报排除**: 非「测试存在即有保护」——5 个测试的对象是正常路径而非关闭窗口，保护力判定以变异可观测性为准；非平台设施测试缺位（JavaScriptWorker 为本单元自有代码）；非 demo/test 专用代码豁免面。
- **复核状态**: 已保留（P2）｜campaign 首报（cra-index 无 WI 6.5/nop-peripheral 条目）｜5 测试串行形态、零触及 grep、建议断言可实现性（getExecuteThread JavaScriptWorker.java:70 / getWaitingTasks L86）均独立核实，详见复核结论节第 1 条

### [C10-002] 插件调试会话 socket 泄漏（C6-001，P1）无回归保护：零测试 + 零泄漏断言钩子 + Gradle 构建隔离三重缺失

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/debugger/XLangDebugConnector.java:49-56`（缺陷站点；测试面证据为全单元唯一配对参照 `nop-dev-tools/nop-xlang-debugger/src/test/java/io/nop/xlang/debugger/TestRemoteDebugger.java:141-147`）
- **证据片段**:
  ```java
  // 全单元唯一资源配对参照（测试面）——动作齐全，但零断言：
  @AfterEach
  public void tearDown() {
      clientFactory.destroy();          // L143
      server.stop();                    // L144
      DestroyHelper.safeDestroy(debugger); // L145
      server.stop();                    // L146（重复调用，幂等性未断言）
  }
  // 缺陷站点 XLangDebugConnector（插件面）：零测试触及
  ```
- **严重程度**: P2（缺保护力；缺陷本体 C6-001 为 P1）
- **并发场景**: 缺陷本体为会话停止线程（EDT stop → connector.destroy）与 recv 池线程（阻塞于 socket read）的关闭配对缺失；测试面上没有任何代码路径实例化 XLangDebugConnector。
- **触发时序**（变异不可观测论证）: 变异回退（移除 connector.destroy 中的 proxy_destroy 调用）不使任何测试变红——插件模块零调试器测试，且其测试面在 Gradle 构建体系内、不参与 `./mvnw test` 门禁（见 CL-301）；TestRemoteDebugger.tearDown 自身的 destroy/stop 调用亦无任何「线程已退出/socket 已关闭」断言，即使该测试改测插件 connector 也检测不到泄漏。
- **现状**: 三重缺失：① 插件站点零测试；② 全单元无泄漏检测钩子（无线程存活断言、无句柄/fd 计数、无注册表空校验——BaseTestCase 为数据驱动助手，无 AfterEach 资源校验设施）；③ 构建隔离使即便补测也不入 Maven 门禁。
- **风险**: C6-001 的修复（destroy 先 proxy_destroy 后关池）是配对顺序敏感改动，无测试钉死则回归（顺序颠倒/漏调）无声复活；每次调试会话泄漏 1 fd + Disconnect 钉死 1 线程的后果重新累积。
- **建议**: ① 最小形态：在 Gradle 测试面新增 connector 级测试——镜像 TestRemoteDebugger.setUp 建本地 server + connector.connect()，断言 destroy 后 recv 线程在超时内退出且再次 connect/destroy 循环 N 次线程数不增长（需要 XLangDebugConnector 对 SimpleRpcClientFactory 的依赖不拖入 IntelliJ 平台类——当前该类本身不依赖平台 API，具备 plain-JVM 可测性）；② 配套：tearDown 形态升级为断言形态（`assertTrue(client.isClosed())` / 线程存活轮询），作为全单元配对测试的模板；③ 构建面处置见 CL-301。
- **失效面枚举**（F3 同族清扫）: 缺陷本体失效面收敛为 XLangDebugConnector 单点（C6-001 F3 已清扫：SimpleRpcClientFactory 全仓生产调用方仅此一处）；测试面同族缺口 = idea-plugin 全部 16 个测试文件均不涉 debugger/services/utils 三包（逐一核对），无「已有测试但未断言关闭」的半缺口兄弟。
- **信心水平**: 确定
- **误报排除**: 非「TestRemoteDebugger.tearDown 已示范配对即算保护」——示范在调试器测试面，缺陷站点在插件面，变异回退不可观测；非「IDE 退出兜底泄漏」类误报（缺陷是会话粒度）；非平台设施测试义务（connector 为本单元自有代码）。
- **复核状态**: 已保留（P2）｜campaign 首报｜三重缺失、tearDown 零断言（L141-147 动作齐全无校验）与 Gradle 隔离独立核实；附两处精确化注记（utils 包措辞、BaseXLangPluginTestCase harness 触及面），零保护结论不变，详见复核结论节第 2 条

### [C10-003] XLangDebugger 挂起交接竞态（C1-001，P2）为假保护：既有测试仅存在低概率偶发红通道，幽灵挂起变体零观测

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/test/java/io/nop/xlang/debugger/TestXLangDebugger.java:85-118`（对照缺陷站点 `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/XLangDebugger.java:472-482,534-556`）
- **证据片段**:
  ```java
  debugger.stepInto();                                   // L85 —— 命令与被调试线程的
  assertEquals("stepInto:[1:0:0:0]b", messages.take());  // L118 —— 挂起处理存在并发窗口，
                                                         //         但无任何悬挂项断言
  // 全测试无一处：debugger.getSuspendedThreads() 的悬挂/清理校验
  ```
- **严重程度**: P2（缺保护力；缺陷本体 C1-001 为 P2）
- **并发场景**: 缺陷本体为 RPC 命令线程（resume/step → monitorNotify L195-204）与被调试线程（doSuspend L472-482 → monitorWait L535 守卫读）的窗口交叠。测试中 test 主线程扮演命令角色、自建 Thread 扮演被调试线程——**角色结构与缺陷场景一致**，故存在偶发命中。
- **触发时序**（假保护论证）: 「吞 step 命令」变体——T_dbg 在 L479 置 `suspended=true` 前发通知（L466 notifyBreakAt 先于 doSuspend），T_test 被 messages.take() 唤醒并执行 stepInto（L85），monitorNotify 落在 T_dbg L479-L535 窗口内时 signal 丢失、挂起被跳过，T_dbg 继续运行至 a(21) 断点发 `break:[21:0:0:0]a`，T_test 在 L118 期望 `stepInto:[1:0:0:0]b` 则断言红——**但该交错窗口极窄且单 debuggee 场景下次挂起自愈，实践中表现为罕见 flake 而非可依赖的回归信号**；「幽灵挂起」变体（thread.suspended 永久 true）在两测试中无任何观测点（测试不检查 getSuspendedThreads 的悬挂项，L89 读取仅在正常挂起态取 threadId），单线程会话结束时 close 清表掩盖一切。
- **现状**: 两个调试器测试对正确实现与缺陷实现均绿（无钉死力）；唯一的红通道是非确定性的时序彩蛋。
- **风险**: C1-001 修复（monitorLock 临界区统一交接）后无回归防线；该修复同样是时序敏感改动，且修复本身可能引入新的交接缺陷（无测试兜底）。
- **建议**: 新增确定性变体测试 `testResumeCommandRacingSuspendHandoff`：自建 notifier 在 `notifyBreakAt` 回调内（保证命令必然落在通知与挂起处理之间）立即投递 resume/step 命令到单线程执行器延迟队列，循环 N=100 轮断点命中；每轮结束后断言 (a) `getSuspendedThreads()` 不含 `isSuspended()==true` 的悬挂项；(b) 被 debug 线程在超时内完成全部迭代（未被永久卡住）。轮次放大使命中确定化，终态断言确定性。
- **失效面枚举**（F3 同族清扫）: 缺陷本体失效面 = monitorNotify 全部 5 调用方 + doSuspend 全部 6 调用方（C1-001 已枚举），测试面同族缺口 = 全部调试器测试仅 2 个（本条 + TestRemoteDebugger），形态相同一并覆盖；TestDebugValueHelper 非并发面无同族。
- **信心水平**: 确定（假保护定性：偶发红通道存在性可由消息时序推演证实；「实践中罕见」为工程判断）
- **误报排除**: 非「测试覆盖了 resume/step 即算保护」——保护力以变异可观测性判定：正确实现与缺陷实现下测试结果相同；非要求真实压测（建议形态为小规模参数化循环，符合 C10 第 5 步口径）。
- **复核状态**: 已保留（P2，缺保护本体成立）｜campaign 首报｜**「假保护（偶发红通道）」定性经变异重放证伪，修正为「无测试（零可观测）」**——消息序在竞态下不变（通知先于 doSuspend 发出），被吞挂起的下一命中点经持续 stepMode 如期对齐，详见复核结论节第 3 条

### [C10-004] suspendedThreads 一次性清理（C6-002，P2）无回归保护：清理意图无任何测试锚定

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/XLangDebugger.java:80`（缺陷站点；测试面证据 `TestXLangDebugger.java` 全文件无 30s 以上会话、无注册表断言）
- **证据片段**:
  ```java
  cleanupThread.schedule(this::cleanup, 30, TimeUnit.SECONDS);   // L80 缺陷站点：一次性调度
  // 测试面：两调试器测试会话均在毫秒级结束，cleanup 永不在测试内触发；
  // close() L89-91 清空注册表但无任何断言校验
  ```
- **严重程度**: P2（缺保护力；缺陷本体 C6-002 为 P2）
- **并发场景**: 缺陷本体为被调试工作线程（makeSuspendedThread L558-571 put）与清理线程（30s 一次性 cleanup L94-103）的注册/注销不对称。
- **触发时序**（变异不可观测论证）: 变异回退（把正确实现 `scheduleAtFixedRate` 改回一次性 `schedule`）不使任何测试红——现有测试会话 < 30s，cleanup 在测试内从不运行；死线程条目滞留的后果（getSuspendedThreads 返回已死线程）无断言校验（测试在正常挂起态读取线程列表，从不校验线程活性过滤）。
- **现状**: 清理行为（一次性 vs 周期）是纯实现细节，公开接口无差异——恰恰是最需要行为锚定测试的形态；现有套件零锚定。
- **风险**: C6-002 修复（改 scheduleWithFixedDelay）后，任何后续重构（如误删调度、改回一次性）无声复活只进不出泄漏。
- **建议**: ① 可测性 seam：将初始延迟/周期改为构造参数或包私有可注入（现 `monitorWaitInterval` 已有 setter 先例 L105-111）；② 测试形态（小规模参数化模拟）：debuggee 线程命中断点后退出（`!isAlive()`）→ 触发一轮 cleanup（注入零延迟或包私有 `cleanupForTest()`）→ 断言 `getSuspendedThreads()` 不再含该线程；③ 周期性契约测试：注入短周期（如 50ms）连续 kill 多个 debuggee 线程，断言每轮清理后注册表活性项收敛。
- **失效面枚举**（F3 同族清扫）: 缺陷本体失效面 = suspendedThreads 单注册表三点（put L567/remove L99/clear L90，C6-002 已全量核对）；测试面同族 = 调试器仅 2 测试文件均无注册表断言，无例外兄弟。
- **信心水平**: 确定
- **误报排除**: 非「close() 会 clear 即兜底」——close 是会话终点，缺陷主张是会话中途死线程滞留（EvalRuntime 全量现场），测试恰在 close 前后即结束故不可观测；非平台调度设施测试义务（cleanupThread 为本单元自有代码）。
- **复核状态**: 已保留（P2）｜campaign 首报｜一次性 schedule（L80）、close 清表无断言、测试零会话超 30s 与零注册表活性断言均独立核实，变异回退不可观测论证成立，详见复核结论节第 4 条

### [C10-005] NopProjectService 全局注册表交叉销毁（C1-003，P2）无回归保护

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/services/NopProjectService.java:44-58`（缺陷站点；测试面证据 `nop-dev-tools/nop-idea-plugin/src/test/**` 16 文件零触及 services 包）
- **证据片段**:
  ```java
  private synchronized void init(Project project) {      // 缺陷站点：项目级生命周期
      if (inited) { return; }
      inited = true;
      ProjectEnv.withProject(project, () -> {
          XLangCoreInitializer xlang = new XLangCoreInitializer();
          xlang.initialize();                            // 写进程级全局注册表
          cleanup.appendOnCancelTask(xlang::destroy);    // 项目 dispose 反注册全局
  // 测试面：idea-plugin 测试仅覆盖 completion/doc/lang/utils，services/debugger 包零触及
  ```
- **严重程度**: P2（缺保护力；缺陷本体 C1-003 为 P2）
- **并发场景**: 缺陷本体为多项目开窗/关窗交错（非单点数据竞争，属生命周期错配）；测试面上 NopProjectService 零实例化。
- **触发时序**（变异不可观测论证）: 变异回退（移除引用计数/app 级注册的正确实现）不使任何测试红——无测试打开两个项目再关闭其一并校验另一项目的 XLang 设施存活；现有 16 个 idea-plugin 测试均为单项目 PSI 功能面。
- **现状**: 零测试 + 修复方案（引用计数或分层注册）本身是可 plain-JVM 测试的纯逻辑（若按建议把全局注册收敛为独立 registrar 类）。
- **风险**: C1-003 修复后无防线；生命周期错配类缺陷在重构中复活概率高（如有人「简化」掉引用计数）。
- **建议**: 修复时将全局注册收敛为可独立测试的 registrar（引用计数 + 注册/反注册配对），配套 plain-JVM 单元测试：initialize×2 → destroy×1 → 断言全局设施仍存活；destroy×2 → 断言全部反注册。若维持 IDE 内实现，则用 IntelliJ harness 的多项目测试（成本高，列为次选）。
- **失效面枚举**（F3 同族清扫）: 缺陷本体失效面 = 同族「局部生命周期 × 全局注册表」4 处中仅本处错配（C1-003 已枚举）；测试面同族 = services 包 3 个主源码类（NopProjectService/NopAppListener/ProjectEnv）零测试触及，全缺口。
- **信心水平**: 确定
- **误报排除**: 非「PSI 测试已覆盖插件功能即算保护」——PSI 测试不触 init/destroy 生命周期；非要求真实多项目 IDE 集成测试（建议形态为修复伴生的 plain-JVM registrar 测试，符合小规模模拟口径）。
- **复核状态**: 已保留（P2）｜campaign 首报｜缺陷站点行号（L44-58/L75-78）、零触及 grep、13 个测试类仅 PSI 功能面均独立核实，详见复核结论节第 5 条

### [C10-006] ProjectFileHelper 静态缓存 Project 泄漏（C1-004 ≡ C6-003，P2）无回归保护（同站点合并登记）

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/utils/ProjectFileHelper.java:49,170-187`（缺陷站点；测试面证据 `nop-dev-tools/nop-idea-plugin/src/test/java/io/nop/idea/plugin/utils/` 仅 TestMarkdownHelper/TestXDefPsiHelper，零触及 ProjectFileHelper）
- **证据片段**:
  ```java
  private static final Map<Project, CachedValue<Collection<String>>> nopVfsPathCaches =
          new ConcurrentHashMap<>();                     // L49 缺陷站点：无移除路径
  ...
  return caches.computeIfAbsent(project, (p) => {...}).getValue();  // L174 只进不出
  // 测试面：utils 包 2 个测试文件均不涉及缓存生命周期
  ```
- **严重程度**: P2（缺保护力；缺陷本体 C1-004/C6-003 为 P2，Phase 4 已裁定同站点归并）
- **并发场景**: 缺陷本体为任意工作线程 computeIfAbsent 写入与项目关闭（dispose）的配对缺失；测试面上 ProjectFileHelper 零实例化。
- **触发时序**（变异不可观测论证）: 变异回退（移除项目关闭 remove/弱键改造）不使任何测试红——无测试在项目 dispose 后校验 `nopVfsPathCaches` 为空（该字段 private static，测试亦无从校验——修复时需同步提供可观测 seam）。
- **现状**: 零测试 + 缺陷载体为 private static 字段（修复后测试需经项目关闭监听器行为或包私有可见性校验）。
- **风险**: C1-004/C6-003 修复（弱键或 Disposable 挂钩）后无防线；泄漏类缺陷的回归在功能测试中永远不可见，只能靠配对断言。
- **建议**: 修复采用「`NopProjectService.dispose()` 追加 remove」方案时（C6-003 建议的最简闭合），配套 IntelliJ harness 测试：创建项目 → 触发 `getCachedNopVfsPaths` → dispose 项目 → 断言 map 为空（经包私有访问器或反射免);若采用弱键方案，断言弱键在 GC 后条目消失（`gc()` + `WeakReference` 探针，参数化 2 轮即可）。
- **失效面枚举**（F3 同族清扫）: 缺陷本体失效面 = 全单元 Project 键静态 Map 唯一一处（C1-004 F3 已清扫）；测试面同族 = idea-plugin 全部测试无任何静态缓存生命周期断言，全缺口。
- **信心水平**: 确定
- **误报排除**: 非「CachedValue 平台机制自带失效即有保护」——失效不清理键强引用（C6-003 已辨析）；非平台设施测试义务（静态 Map 为插件自有代码）；同站点只登记一条系防双计口径而非遗漏。
- **复核状态**: 已保留（P2）｜campaign 首报｜缺陷站点行号（L49/L170-187 computeIfAbsent L174）、零触及 grep、private static 不可观测主张均独立核实；证据片段 `(p) =>` 系 `(p) ->` 笔误，详见复核结论节第 6 条

## watch-only 清单（不立案计数，P3/demo 降口径）

| # | 对应缺陷 | 缺保护事实 | watch-only 理由 | 建议顺带补测时机 |
|---|---|---|---|---|
| W-1 | C1-005（muted 非 volatile，P3） | XLangBreakpointHandler 零测试触及；Gradle-only | P3 窗口窄、单次自愈；测试需 IntelliJ harness，单独投入不成比例 | 修复 C1-005 的同 diff 内加 EDT/ReadAction 双线程断言（或声明 volatile 后以结构检查覆盖） |
| W-2 | C1-006（tempBreakpoint 非原子，P3） | 两调试器测试的 runToPosition 均单 debuggee 线程（TestXLangDebugger L50 / TestRemoteDebugger L85），多线程汇聚不可达 | P3 无数据后果、resume 一次全恢复 | 修复同 diff：2 个被调试线程参数化对冲同一 tempBreakpoint 行，断言恰 1 线程挂起 |
| W-3 | C1-007（demo RESOURCE_STORE，P3 demo 降口径） | nop-spring-gateway-demo-service 无 src/test；TccGatewayInterceptorTest 属另一模块另一类（mock 引擎，不触 DemoServiceBizModel） | demo 降口径（本单元 C1/C6 校准惯例） | 若修复（putIfAbsent 一行）则同 diff 加 2 线程同 resourceId 参数化断言恰 1 次成功 |
| W-4 | C1-R2-001（sendStack 裸读，P3） | TestRemoteDebugger 无停机×断点命中交叠场景 | P3 后果为停机期单请求裸 NPE、不可积累 | 修复同 diff：TestRemoteDebugger 变体（断点命中在途时 server.stop，断言请求得异常完成而非 NPE 泄漏——可选） |
| W-5 | C6-R2-001（demo 临时文件，P3 demo 降口径） | ReportController 零测试触及 | demo 降口径 | 若补 demo 测试则断言 5min 删除任务失败重试/启动清扫（mock timer） |

---

## 四、维度集外线索附记（CL-3NN）

### [nop-peripheral-CL-301]（保护面/构建治理形态，强）nop-idea-plugin 为 Gradle 独立构建：16 个测试文件与全部主源码缺陷站点不参与 `./mvnw test` 门禁

- **文件**: `nop-dev-tools/pom.xml:20-24`；`nop-dev-tools/nop-idea-plugin/build.gradle.kts:22,36`
- **证据片段**:
  ```xml
  <!-- nop-dev-tools/pom.xml modules —— 不含 nop-idea-plugin -->
  <module>nop-api-debugger</module>
  <module>nop-maven-shaded-plugin</module>
  <module>nop-xlang-debugger</module>
  ```
  ```kotlin
  // nop-idea-plugin/build.gradle.kts —— IntelliJ Platform Gradle Plugin 独立体系
  testFramework(TestFrameworkType.Platform)   // L22
  testImplementation("junit:junit:4.13.2")    // L36（JUnit 4，与全仓 JUnit 5 惯例分叉）
  ```
- **线索**: nop-idea-plugin 位于 Maven 反应器之外（AGENTS.md 的验证命令 `./mvnw test -pl <module>` 对其无效），其测试需独立 `./gradlew test` + IntelliJ Platform test harness。后果：① C6-001/C1-003/C1-004≡C6-003/C1-005 四个缺陷站点即使补测也不入默认验证门禁（C10-002/C10-005/C10-006 的保护力打折）；② 插件测试用 JUnit 4（L36）与全仓 JUnit 5 惯例分叉；③ campaign 的「`./mvnw test` 通过」验证清单对该模块不构成任何并发回归约束。属测试基础设施/构建治理面（超出 C10 缺陷立案范围），供治理轨聚类。
- **建议方向**: 至少将 `./gradlew test` 纳入 idea-plugin 变更的验证清单与 CI；中期评估插件测试向 Maven 反应器内 plain-JVM 可测层（connector/registrar 等无平台依赖 seam）下沉。
- **状态**: 新发现线索，供 7.1 聚类。｜复核已确认（构建隔离全链独立核实：modules 三项精确匹配 + 全仓 pom 无 idea-plugin 引用 + build.gradle.kts L22/L36 属实；campaign 首报，无历史 cross-ref——WI 4.4 为 nop-host 宿主过滤器族不同范畴，已显式核对），见复核结论节 CL-301 条。

---

## 五、时序敏感测试审查结果

| 测试点 | 形态 | 判定 |
|---|---|---|
| `TestXLangDebugger.assertBreakAt`（L122-130） | `if(!isSuspended()) Thread.sleep(1000)` **单发重试**后断言；`catch(Exception e){}` 吞中断 | **平台脆弱（中等风险）**：慢 CI 下 1s 不足即假红；吞中断违反平台线程中断惯例。主等待链（messages.take() 阻塞等待通知）本身是正确异步等待形态，sleep 仅为可见性 belt-and-braces——整体未到立案门槛，登记为测试债 |
| `TestRemoteDebugger.assertBreakAt`（L122-127） | 同形态，sleep 仅 **100ms**，且经 socket 传输（时延更高） | 同上，脆弱性更高；`tearDown` L144/L146 重复 `server.stop()` 无害但属配对意图不清的信号 |
| `TestJavaScriptService` 5 测试（L58/68/85/97/112） | `FutureHelper.syncGet` **无显式超时** | 若正常路径回归为挂起，表现为 surefire 超时 hang 而非快速失败（慢失败）；建议新测试采用 `orTimeout`（见 C10-001 建议），存量可不改 |
| `TestPageProviderPermissionsTransform.concurrentGetPageNeverMutatesCachedPageModel`（L131-166） | CountDownLatch 起跑门 + 确定性缓存隔离断言；对非确定性部分有自 aware 注释（L153-155） | **正面样板**——单元内唯一正确形态的真并发测试，可作新增并发测试的模板 |
| `TestWebPageExporter.testThreadedExportMatchesSerialResult`（L116-130） | threaded(2) vs 串行结果等价 | 合理；仅一个 threadCount 取值（见边界缺口） |
| 测试自吞竞争扫描 | grep 全部测试 `catch` + 断言组合 | 除上述 assertBreakAt 吞 InterruptedException 外无「测试自己 catch 掉竞争」形态——闭合 |

---

## 六、资源泄漏测试钩子缺口

- **现状**：全单元**零泄漏断言钩子**。`TestRemoteDebugger.tearDown`（L141-147）有完整配对动作（destroy/stop/safeDestroy）但无任何断言；`TestJavaScriptService` 的 `service.stop()` 后零断言；`BaseTestCase`（nop-core）为数据驱动助手，无 AfterEach 资源校验设施；无 Awaitility 类等待工具依赖。
- **P1 级资源缺陷建议测试形态**（对应正文第 4 步）：
  - **C6-001**：`destroy()` 后轮询断言 recv 线程终止（`Thread::isAlive` 至多 5s）+ 重复 connect/destroy N=3 轮线程数/fd 不增长（fd 不可移植，以线程数与 `SocketClient` closed 状态代位）——plain-JVM 可测（C10-002）。
  - **C1-002**：停机交叠后断言全部在途 future 完成性 + `getWaitingTasks()==0` + worker 线程终止（C10-001）。
  - **C6-002**：cleanup 触发后断言 `getSuspendedThreads()` 不含已死线程（C10-004）。
- **通用建议**：调试器测试补一个共享的 after-test 钩子：close 后断言 `getSuspendedThreads()` 为空——当前 close 清表行为本身也无数值断言保护。

---

## 七、边界测试缺口（小规模参数化模拟口径）

| 边界 | 现状 | 缺口 |
|---|---|---|
| `JavaScriptService.refreshConfig` 收缩路径（JavaScriptService.java:108-116 shutdownGracefully 裁撤 worker） | 零测试 | workerCount 1→2→1 参数化：裁撤 worker 终止、任务不搁浅（与 C10-001 测试同族） |
| `JavaScriptWorker.taskQueue` 容量策略（CL-002 无界 LinkedBlockingQueue） | 零测试（无策略可测） | 若按 CL-002 建议引入 `QueueOverflowPolicy`，需配套队列满策略测试（拒绝/阻塞/降级各一）；当前无界形态无可断言边界，登记为修复伴生测试项 |
| `WebPageExporter` threadCount（PageExportOptions） | 仅 threadCount=2（TestWebPageExporter L122） | 无 0/负值/超订（>页数）边界；参数化 {0,1,2,页数+1} 断言等价性与失败页计数 |
| `XLangDebugger.monitorWaitInterval`（L105-111 setter seam 已存在） | 零测试利用 | 可作为 C10-003 建议测试的窗口放大器（注入极小值提高竞态命中轮次密度），非独立缺口 |
| `BreakpointManagerImpl` 外部缓存上限（LocalCache 100/1000） | 平台设施自带淘汰 | 合规，不要求业务测试（误报基线） |
| demo RESOURCE_STORE 无界（C1-007 关联面） | 零测试（demo 降口径） | 并入 W-3 |

---

## 八、终止声明与统计

- **覆盖声明**：四模块全部 `src/test` Java 测试文件（32 个）逐目录盘点，并发/资源相关 6 个精读，其余经 grep（`Thread|sleep|Latch|Executor|CompletableFuture|syncGet|close|destroy`）命中点甄别闭合；e2e Playwright 面按零 Java 审计面口径核对（与 12 条缺陷站点零交集）；nop-api-debugger 零测试面（与 C1/C6「纯 DTO/接口」结论一致，无保护义务）；变异推演覆盖全部 12 条 Phase 4 复核保留缺陷（P1×2 逐条、P2×4 全覆盖、P3×5 全覆盖）。
- **清扫终止信号**：测试面穷举闭合——9 个有生产线程协作面的类零测试触及（grep 证实）；单元内并发测试总数 3（其中真并发回归 1、并发相邻等价性 1、调试器 happy-path 2 中含 1 传输层），无未盘点测试残留。
- **轮次终止信号**：C10 为本单元最后批次维度，且 12 条缺陷的保护力判定全部收敛（6 立案 + 5 watch-only + 1 同站点归并），继续第 2 轮的预期增量为零——建议 C10 维度在此终止。
- **统计**：发现（缺保护立案）**6 条 = P2×6**（C10-001~006）；watch-only **5 条**（W-1~W-5，不计入发现数）；CL 线索 **1 条**（CL-301）。既有测试盘点 12 行（并发/资源相关 6 + 零交集面 6 类）。保护力矩阵结论：**P1×2 + P2 全部 4 个保护面均无确定性保护；唯一红通道为 C1-001 的低概率偶发 flake（假保护）；全单元对 12 条缺陷站点零钉死力**。
- 全部条目复核状态：未复核。

---

# C10 维度复核结论（独立复核）

- **复核方式**: 独立复核子代理对 C10 全部 6 条立案 + 5 条 watch-only + CL-301 逐条重开 live code（worktree @ concurrency-audit-remediation，基线 a20ca25130）核实；以下 file:line 均为复核者独立读取/grep 所得，非转抄初审。复核输入为 C10 文件全文、同目录 Phase 4 复核后的 C1/C6 两份维度文件（缺陷 ID/判级/站点基准）与 `cra-index`（现状查证允许范围）。对每条「无测试/假保护」结论执行反向验证：grep 三模块全部 src/test（含嵌套子模块）确认无其他测试触及缺陷站点；对「假保护」判定做逐交错变异重放（见第 3 条，该判定被证伪）。
- **P0 候选**: 无（缺保护类天然 P2 上限，与派发口径一致）。
- **初审事实性错误清单**: 1 处实质定性错误（C10-003 的「假保护（偶发红通道）」经变异重放证伪，修正为「无测试（零可观测）」——该修正使保护缺口判定更彻底，立案本体加强而非削弱）＋ 2 处簿记/措辞错误（盘点文件总数 32 与实测不符；C10-002 F3 的 utils 包措辞）。全部修正不改变立案数量（6）与判级（P2×6）。

## 逐条核实结果

1. **C10-001（C1-002 P1 缺保护）成立，保留**。独立核实：`TestJavaScriptService.java` 5 个测试（testExecute L47-61 / testReturnMap L63-72 / testSpeed L74-88 / testRollup L90-103 / testImportLib L105-118）全部为 start → invokeAsync → `FutureHelper.syncGet`（L58/68/85/97/112）→ `service.stop()`（L60/71/87/102/117，经 `LifeCycleSupport.stop()` → `doStop` JavaScriptService.java:127）严格串行——提交与关闭零交叠；无 future 完成性断言、无 worker 线程终止断言、无 `getWaitingTasks()` 停机后断言。反向验证：grep 三模块全部 src/test 对 `JavaScriptWorker` 零命中；nop-js 仅 2 个测试文件，另一个（TestLogOutputStream L22-30）为单线程 flush。缺陷站点行号核实：execute 检查 L99/入队 L102、run finally L309-315（cancelAllTasks L311、executeThread=null L314）。建议可实现性核实：`getExecuteThread()`（JavaScriptWorker.java:70）与 `getWaitingTasks()`（L86）均为 public，建议的三项断言（future 完成性/线程终止/队列清空）全部可落。同族入口 refreshConfig（JavaScriptService.java:96-116，shutdownGracefully 调用 L114）核实。P2（缺保护）恰当。
2. **C10-002（C6-001 P1 缺保护）成立，保留（附两处精确化注记）**。独立核实：`XLangDebugConnector.java` destroy 仅 `clientFactory.destroy()`（L49-51）、connect 不持有 proxy（L53-55）；`TestRemoteDebugger.tearDown`（L141-147）动作齐全（clientFactory.destroy L143 / server.stop L144 / DestroyHelper.safeDestroy L145 / server.stop L146 重复）但零断言——「全单元唯一配对动作参照、无校验力」定性成立；grep 三模块全部 src/test 对 `XLangDebugConnector|XLangDebugProcess` 零命中；Gradle 构建隔离独立核实（见 CL-301 条）。**注记 ①**：F3 措辞「16 个测试文件均不涉 debugger/services/utils 三包」在包层面不精确——`TestMarkdownHelper`/`TestXDefPsiHelper` 即位于 utils 包；准确表述为「utils 包 2 个测试文件零触及 ProjectFileHelper（缺陷站点），debugger/services 包零测试文件」。**注记 ②**：`BaseXLangPluginTestCase.setUp` 确实在 harness 内执行 `new NopAppListener().appFrameCreated(...)` 与匿名 `XLangDebuggerInitializer` 子类构造（idea-plugin 13 个测试类的公共基类）——但二者均非 12 条缺陷站点，且无任何生命周期/注册/关闭断言；9 类零触及 grep 独立复现成立，「零保护」结论不变。
3. **C10-003（C1-001 P2 缺保护）立案保留；「假保护（偶发红通道）」定性证伪，修正为「无测试（零可观测）」**。站点行号独立核实：doSuspend L472-482（per-thread 置位 L478 → 全局 L479）、monitorWait L534-556（守卫读 L535、复位仅 L549 finally）、monitorNotify L195-204（清标志 L197）、通知点先于 doSuspend（checkHitBreakpoint L466→L468、checkStepInto L372→L374）、checkStepInto 任意行命中且 L367 复位 stepMode=RESUME。**证伪推演（对初审红通道命题逐步重放）**：C1-001 竞态的全部后果发生在通知发出**之后**——消息在 checkXXX 内先于 doSuspend 入队，故无论 monitorNotify 落在 L479-L535 窗口何处，`messages.take()` 收到的消息序列与无缺陷运行完全一致；初审所述「testDebug 断言 `stepInto:[1:0:0:0]b` 偶发收到 `break:[21:...]a` 而红」不可发生：被吞的是挂起而非消息，且被吞挂起的下一站 b(1) 由持续生效的 stepMode=STEP_INTO 经 checkStepInto 如期命中并发出完全相同的期望消息——初审推演「继续运行至 a(21) 发 break:[21:...]a」跳过了该必经命中点，不成立。幽灵变体下 assertBreakAt 同样不红：线程被吞挂起后在微秒级重新挂起到恰好下一个期望站点（期望序列与实际站点 1:1 对齐），1s/100ms sleep 全覆盖该间隙；复用条目（makeSuspendedThread L564-568 同 threadId 返回既有条目）使滞留的 per-thread 标志在下一站 monitorWait 的 finally（L549）自愈；getLastBreakLocation 经滞留条目读到的 lastBreakLocation 恰为期望站点。终局 resume（TestXLangDebugger L118 / TestRemoteDebugger L118 后无断言）与 mute 后的「end」消息由被调试线程自身追加，亦不可红。**修正后裁定**：C1-001 的变异结论为「无测试（零可观测）」——保护缺口为完全缺失，较「假保护」更强支撑立案；P2 维持；保护力矩阵该格与「唯一红通道」总结句按本条联动修正（三态计数：会红 0 / 假保护 0 / 无测试 6）。**引用勘误**：本条证据片段第二行的 `assertEquals("stepInto:[1:0:0:0]b", messages.take())` 标注 L118 系 TestRemoteDebugger 的行号，TestXLangDebugger 中同一断言在 **L86**，文件头「TestXLangDebugger.java:85-118」存在两文件行号混写。建议的确定性变体测试（notifyBreakAt 回调内投递命令 + 终态悬挂项断言 + 轮次放大）方向正确且仍然必要。
4. **C10-004（C6-002 P2 缺保护）成立，保留**。独立核实：`XLangDebugger.java:80` 一次性 `schedule(this::cleanup, 30, TimeUnit.SECONDS)`；close L84-92（suspendedThreads.clear L89-91）无任何断言保护；两调试器测试内全部时延点仅 assertBreakAt 的 `Thread.sleep(1000)`（TestXLangDebugger L125）/`Thread.sleep(100)`（TestRemoteDebugger L125），会话毫秒级结束、cleanup 从不在测试内触发；`getSuspendedThreads` 于测试面仅 L89（TestXLangDebugger，正常挂起态取 threadId）与 L133（getStackTrace 助手，同形态）两处，无活性过滤断言。变异命题（scheduleAtFixedRate 回退为一次性在现有套件不可观测）成立。可测性 seam 核实：`monitorWaitInterval` setter L105-111 存在。P2 恰当。
5. **C10-005（C1-003 P2 缺保护）成立，保留**。独立核实：`NopProjectService.java` init L44-58（inited=true L48、`xlang.initialize()` L52、`cleanup.appendOnCancelTask(xlang::destroy)` L53）、dispose L75-78；grep 三模块全部 src/test 对 `NopProjectService` 零命中；idea-plugin 13 个测试类经 BaseXLangPluginTestCase 仅覆盖 completion/doc/lang/utils 功能面，无多项目开闭与全局注册表校验。变异回退（移除引用计数/分层注册）不可观测——成立。修复伴生 plain-JVM registrar 测试建议方向合理。P2 恰当。
6. **C10-006（C1-004≡C6-003 P2 缺保护）成立，保留**。独立核实：`ProjectFileHelper.java:49` 静态 CHM 声明、getCachedSearchResult L170-187（computeIfAbsent L174 唯一写入形态）；grep 三模块全部 src/test 对 `ProjectFileHelper` 零命中；字段为 `private static`，既有测试无从校验——「修复需同步提供可观测 seam」主张成立。同站点防双计（Phase 4 已裁定 C1-004≡C6-003 归并）与单条登记口径一致。证据片段 `(p) =>` 为 `(p) ->` 笔误，不影响结论。P2 恰当。

## 反向验证汇总

- **9 类零触及 grep 独立复现**：`ProjectFileHelper|NopProjectService|XLangBreakpointHandler|XLangDebugProcess|XLangDebugConnector|DynamicJsLoader|ReportController|DemoServiceBizModel|JavaScriptWorker` 于 nop-dev-tools / nop-frontend-support / nop-demo 全部 src/test（含嵌套子模块）**零命中**——初审盘点结论证实。
- **并发关键词全量复扫**：grep `Thread|Latch|Executor|CompletableFuture|syncGet|CountDown` 于三模块全部 src/test/java 命中 10 文件——7 个已入盘点表；未入表的 3 个（TestAutoPopupCompletion 的 `runInDispatchThread` EDT 开关、TestDemoBizModel 的 graphQLEngine syncGet、LmStudioGatewayTest 的 httpClient syncGet + CountDownLatch）经逐一读取核实与 12 条缺陷站点零交集，初审「其余无并发断言」结论维持，但该 3 文件未在甄别记录中留痕，属盘点簿记不完整（不影响闭合性）。
- **盘点总数勘误**：初审称「四模块 src/test Java 测试共 32 文件」——实测 nop-dev-tools 21（idea-plugin 16 + maven-shaded-plugin 1 + xlang-debugger 4）+ nop-frontend-support 19 = **40** 个 src/test/java 测试文件（不含 idea-plugin test resources 下 6 个 fixture .java）；含 nop-demo 57 个则为 97。无论何种口径均非 32；6 个并发/资源相关测试的甄别与闭合结论不受影响。
- **零测试面声明复核**：nop-entropy-e2e 零 .java 文件、nop-api-debugger 零 src/test——独立复现，与「零 Java 审计面」「纯 DTO 无保护义务」声明一致。
- **时序敏感测试审查表复核**：assertBreakAt 单发 sleep 重试 + 吞中断形态（TestXLangDebugger L122-130 / TestRemoteDebugger L122-130，sleep 分别 1s/100ms）、TestJavaScriptService syncGet 无显式超时、TestPageProviderPermissionsTransform 正面样板（L131-166：8 线程 L137、latch L139/144/148、确定性断言 L161-165、自 aware 注释 L153-154）、TestWebPageExporter threadCount=2（L122，测试体实际 L116-132）——全部独立核实属实；「除 assertBreakAt 吞 InterruptedException 外无测试自吞竞争」经复扫维持。

## CL-301 裁定

**确认，保留（供 7.1 聚类），campaign 首报，无历史 cross-ref**。独立核实：`nop-dev-tools/pom.xml:20-24` 的 `<modules>` 精确为 nop-api-debugger/nop-maven-shaded-plugin/nop-xlang-debugger 三项，nop-idea-plugin 不在其列；全仓 grep `<module>nop-idea-plugin` 零命中（根 pom 无其他挂载路径）——Maven 反应器隔离确证，`./mvnw test -pl <module>` 对其无效的结构性事实成立。`nop-idea-plugin/build.gradle.kts` 独立 Gradle 体系确证：`testFramework(TestFrameworkType.Platform)`（L22）、`testImplementation("junit:junit:4.13.2")`（L36，JUnit 4 与全仓 JUnit 5 惯例分叉属实）、IntelliJ Platform Gradle Plugin 2.16.0（L12）、自带 gradlew/settings.gradle.kts。「16 个测试文件」计数与实测一致（15 个 Test* + BaseXLangPluginTestCase）。交叉核对：cra-index 对 gradle/idea-plugin/构建门禁治理零提及；**WI 4.4 为 nop-host**（Spring/CDI 宿主过滤器启动窗口族），与 idea-plugin 构建治理无同站点关系——不构成 cross-reference，按任务要求显式登记核对结论。

## watch-only 口径核对

W-1~W-5 与校准声明逐条核对**一致**：5 条对应缺陷全部为 P3（C1-005/006、C1-R2-001）或 demo 降口径（C1-007、C6-R2-001），与 Phase 4 复核后 C1/C6 判级逐一相符（C1 复核节第 5/6/7/8 条、C6 复核节第 4 条）；立案 6 条恰好覆盖全部 P1×2（C1-002/C6-001）+ P2×4（C1-001/C1-003/C1-004≡C6-003 合并计 1/C6-002），无 P1/P2 缺保护遗漏、无 P3 违规立案——不立案计数成立。

## 保护力矩阵完整性核对

12 条在案缺陷全部出现在矩阵中（11 行，C1-004≡C6-003 按防双计合并为 1 行），每行「最近既有测试 / 变异结论 / 反向核对」三格均已填，无空格。判级引用与 Phase 4 复核后基准（C1：P1×1+P2×3+P3×3+R2 P3×1；C6：P1×1+P2×2+R2 P3×1）逐格比对一致。修正后三态计数：**会红 0 / 假保护 0 / 无测试 6**（初审「假保护×1（C1-001）」经复核第 3 条证伪并入无测试）；P3/demo 5 条 watch-only 标注与矩阵行一致。矩阵结论句「唯一红通道为 C1-001 的低概率偶发 flake（假保护）」与统计节相应表述按复核第 3 条修正为「全部 6 个保护面缺口零观测、全单元对 12 条站点零钉死力且零检出通道」。

## 现状标注汇总

- C10-001~006 经查证全部为 **campaign 首报**：cra-index 无 WI 6.5/nop-peripheral 条目（grep 证实）；2026-09 全仓审计 G 系列对四模块零提及（沿 C1/C6 复核节已证结论）；campaign 既有单元无同站点缺保护登记（WI 4.4 宿主审计的「C10 缺保护 19 条」针对 nop-host 面，与本案 6 站点无交集；WI 6.2 的「现有测试时序敏感假保护」watch 项针对 NodeTscBridge，亦无交集）。
- CL-301 为新发现构建治理线索，无历史 cross-ref（WI 4.4 nop-host 不同范畴，核对结论已登记于 CL-301 裁定）。
- 复核修正 3 类：① C10-003「假保护」定性证伪 →「无测试（零可观测）」（矩阵格、矩阵结论句、统计表述三处联动，正文按不修改初审正文原则以本复核节为准）；② 盘点总数 32 → 40（不含 demo）/97（含 demo）勘误 + 3 个关键词命中文件的甄别留痕补记；③ 措辞/引用勘误（C10-002 F3 utils 包表述、BaseXLangPluginTestCase harness 触及面注记、C10-003 证据片段 L118/L86 行号混写、C10-006 `(p) =>` 笔误）。全部修正不改变立案数量（P2×6）、watch-only 计数（5）与 CL 计数（1）。
