# 2524 CRA Phase 8.30 — R2-21 4.4 host 批（4 项：双宿主启动窗口就绪门禁+空链固化消除（MA-01 主，C1-006+←C4-003 吸收，C10-006/C10-009 双保护面）+ ContextTaskQueue 吞异常 future 永不完成 host 层终结（MA-02，C6-007，future 族第 5 例宿主侧）+ 上传流先校验后消费失败路径配对 close（C6-001，双宿主 F3 孪生同 diff）+ SpringWebProxy 凭据对 volatile 单引用整体替换（C1-001））

> Plan Status: completed
> Design Review R2: agent_d57ee821 @ 2026-11-01 —— 设计文档独立对抗性评审（120+ 行号锚抽验零漂移错误；两重点裁决：裁定①503 短路不越权[plan 原文含 503 语义+Phase 1 委托项+三处显式登记 deviation]、平台吞异常定性为登记设计语义成立[亲读 :157-159]）：1 Major（Quarkus 前段兜底三细节落位规格——inputStream 作用域提升/按本迭代已提交否关流防 part₂ 泄漏/通道升 Throwable）+5 Minor（m1 Spring 守卫通道 Throwable 自洽化、m2 类级计数勘误、m3 reinitialize 窄窗残余登记、m4 bindMDC 前奏残余登记、m5 plan 侧勘误镜像+测试类名取代指认）——已同 diff 修入设计文档与 plan，放行 Phase 2
> Draft Review R1: agent_db1779df @ 2026-11-01 —— 无 Blocker；3 Major（F1 红①驱动器与修复形态错位[钉经 filter 入口驱动，直呼 getFilters 永不变绿]；F2 红④兼容形态耦合未成文收口义务[委托 setter 残留双写假红/假绿]；F3 全局静态态翻转机制+恢复纪律未点名[反射写 initialized+@AfterEach 恢复]）+5 Minor（F4 Closure 占位/F5 类级计数/F6 注入点与送达语义精度/F7 RoutingContext JDK 代理先例/F8 防双关观测点）——协调方机械修复闭环，F1–F3 修订忠实落实评审处方（三薄弱点裁决 a 半成立/F3 机制点名/c whenComplete 维持+精度补强），修订转 active
> Draft Review：待独立子 agent draft review（通过后本行登记 R1 结论，修订转 active）
> Last Reviewed: 2026-11-01
> 起草说明：本 plan 由独立起草子 agent 于 2026-11-01 在工作仓 `nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation` @ `ea0f89ce2c`）起草；全部站点行号为该基线 live 亲读回读值（勘误节逐条登记漂移核对结果），执行前如再漂移以 live 为准并登记。结构沿 plan 2523 先例（Source 全文引用 / Draft Review 行预留 / PA 判定表 / 对齐纪律显式条款 / 成员逐条表 / 勘误节 / 红绿判据 / C10 口径 / 环境三联预登记）。待独立 draft review 通过后修订转 active。
> Source: roadmap 工作项 8.30（R2-21，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L172，Status=todo[本 plan 立项时回写为「todo（plan 2524 drafted，待 draft review @ 2026-11-01）」]）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-21 行（L95 全文照录：finding ID 清单 4 项[4.4-C1-006；4.4-C6-007；4.4-C6-001；4.4-C1-001] / 模块与验证命令「nop-host 所在聚合模块 / `./mvnw test -pl <宿主聚合模块> -am`（以 nop-host 实际 maven 路径为准）」[live 裁定见勘误 3——宿主载体分属 nop-spring/nop-quarkus 两聚合器，本 plan E3 = `./mvnw test -pl nop-spring,nop-quarkus -am`] / Owner Doc=nop-host 无 03-modules owner doc→缺口声明；`docs-for-ai/01-repo-map/module-groups.md` / PA=无（C6-009 未入修复面；若后续立项触 nop-http-api 须 plan-first——批外注记） / 依赖「软依赖 R1-04/R1-01（C6-007 平台根因绝缘层移交 2.3-CL-018+2.1-C3-001 先行）」——**软依赖解除核实（live 2026-11-01 亲读 roadmap L147/L144）：8.5（R1-04）done（plan 2499，closure audit PASS with corrections）+ 8.2（R1-01）done（plan 2496，closure audit PASS with corrections）** / exit criteria 增补：**双宿主启动窗口就绪门禁（空链固化消除）；ContextTaskQueue 异常 completeExceptionally（future 族第 5 例宿主侧）；上传流配对 close+fd 计数回归（C10-011 复用 4.3 TestFileDownloadSinkClose 先例）；凭据对 volatile 化**）+ §一 通用门禁 E1–E6；§四.4「4.4-C6-007 平台根因绝缘层注记 | R2-21 软依赖 R1-04/R1-01」+ §四.3「4.4-C6-009 触 nop-http-api 须 plan-first | 批外注记」在案；成员发现详情 = WI 4.4 审计目录 `ai-dev/audits/2026-10/2026-10-21-1223-deep-audit-nop-host-concurrency/`[任务书笔误 1326→1223 勘误见勘误 1]（C1-shared-mutable-state.md[C1-001 :37-63 / C1-006 :181-207 / 复核判定 :227-247]、C6-resource-lifecycle.md[C6-001 :32-58 / 复核判定 :202-221 / 家族登记 :213]、C6-round2.md[C6-007 :15-41 / 复核结论 :107-115 / future 族第 5 例台账 :111]、merge-adjudication.md[MA-01 :18-23 / MA-02 :26-27 / §五 7.1 议程 :74-78 / §六 权威表 :86-104 / C4-003 行 :92 / C6-007 行 :100]、C10-test-protection.md[C10-001 :98 / C10-006 :163 / C10-009 :202 / C10-011 :228 / C10-017 :306]、summary.md[P1 全清单 :76-84 + 计数统计 :29-42]）；C10 映射 = 跨模块裁定 `inventory-p2-c10.md` :584（WI 4.4 节 10 行 P2 表——P1 四行不占该表）+ :803 小结行（**4.4（19，P2×16/P3×3）**：C10-001（强制）→C1-001(P1)；006（强制）→MA-01(P1)；009→MA-01(P1)；011（强制）→C6-001(P1)；017（强制）→MA-02(P1，future.get(timeout) 断言)——**本批承载 C10 五条成员面且全部为全条承载零半边**[勘误 4]；反向钉死 4 处 RP-1/RP-2/RP-3/RP-4 全部归 R4-28 本批零承载）；P1 台账 = cra-index `ai-dev/audits/cra-index.md` live 2026-11-01 回读 R2-21 open 四行 = **:280**[C1-006（MA-01 主，「←C4-003 启动窗口无就绪门禁」）]/:**281**[C6-007（MA-02，「future 族第 5 例，平台根因绝缘层移交 WI 2.3 CL-018+WI 2.1 C3-001」）]/:**282**[C6-001（「C6-008 判级维持 P2 随族留 7.1」注记）]/:**283**[C1-001]——**4.4 单元 R2-21 批次列 open 四行全部且仅为本批成员**；4.4 计数口径 = cra-index :72；执行基线 @ `ea0f89ce2c`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.29 已 done，plan 2495–2523 均已完成或收口）
> Related: plan 2499（completed——R1-04 api-core 协同批：bindCancel 单点改造+上下文传播五入口；本批软依赖之一已解除——C6-007 平台根因绝缘层半边已承载，本批零回退义务）；plan 2496（completed——R1-01 周期任务异常绝缘层批：nop-kernel 绝缘层本体+30 点表——本批软依赖之二已解除；ContextTaskQueue 吞异常面属 2.3-CL-018 承重原语轨[与本批宿主面正交——本批零触碰平台]）；plan 2521（completed——**R21 族纪律先例**：4.2-C3-001 callAsync 同步异常 completeExceptionally(adapt)+null 类型化守卫——C6-007 修复范式引用不重开）；plan 2522（completed——**R21 族第 4 例先例**（4.3-C3-001 OkHttp onResponse 吞异常）+ **TrackingInputFile fd 计数先例**（C6-005 钉「fd 计数缺陷态 GC 假绿实证后修订」——本批红③沿其确定性 close 计数形态））；**R4-28 下游**（G70 双宿主/反向钉束 10 项，软依赖本批：MA-03[C6-008]/MA-04[C6-003←C6-002]/MA-05[C1-002]/C1-003/C1-004/C4-001/C4-002[RP-2/RP-4 前置]/C4-004[RP-3 前置]/C6-004[搭车 C1-005 volatile]/C6-005——batch-proposal L143 照录；本批对 C1-002/C4-004/C6-008 等相邻面登记禁顺手修条款）；**同号防混**：4.4-C6-001 与 4.3-C6-001（P0，plan 2479 已修）/5.4-C6-001/5.6-C6-001 同号异站零关联；4.4-C1-001 与 4.3-C1-001（plan 2523 MA-07）/2.1-C1-001/4.2-C1-001 同号异站零关联；4.4-C4-003（本批吸收成员）与 1.7-C4-003（batch 工厂）/2.1-C4-003（kernel 设施）同号异站零关联（inventory-p2-c10 :875 明文）；**MA-01×MA-02 联动裁定在案**（merge-adjudication :23 修复批约束 + :78 §7.1-3——门禁失败必须 503/fail-fast 短路于 runOnContext 之外，否则门禁异常落入 C6-007 task 通道恶化为悬挂；MA-02 completeExceptionally 修复为 MA-01 同批前置/联动项——本批两成员同批交付即闭合该联动，Phase 1 裁定①②成文）。
> Protected Area 授权：批次 PA=**无**（batch-proposal R2-21 行 PA 列「无（C6-009 未入修复面；若后续立项触 nop-http-api 须 plan-first——批外注记）」）。**头部预设（live 载体亲读核实成立）：4/4 成员零 orm.xml、零 `_` 生成物、零 nop-api-core 触碰、零 nop-ioc/nop-kernel 平台设施触碰、零 nop-http-api 触碰**——六载体文件分属 nop-spring-web-starter/nop-spring-proxy/nop-spring-file/nop-quarkus-web/nop-quarkus-file 五 impl 叶模块（`find` 亲证两聚合器零 `_` 前缀文件零 orm.xml）；C6-007 host 层修复仅落 `QuarkusExecutorHelper.java`（平台根因面 `ContextTaskQueue.java`/`BaseContext.java` 为引用证据零触碰——nop-api-core plan-first Protected Area 不触发）；C1-006 门禁读 `CoreInitialization.isInitialized()`（nop-core 公共静态只读 API，零修改）。**先例申报义务成文**：执行期如任何修复需触 nop-api-core/nop-kernel 平台设施或 nop-http-api，须沿 R2-19 裁定⑥先例（batch-proposal :299）在本 plan + 设计文档 + 缺口声明 owner 面 + 回归测试四件证据链下 plan-first 申报，禁静默扩权。站点分布于 nop-spring（web-starter 1 文件 + proxy 2 文件 + file 1 文件）与 nop-quarkus（web 2 文件 + file 1 文件）——两聚合器五模块七文件。

## Purpose

关闭 R2-21 批的 4 项已确认缺陷（P1×4；cra-index open 四行 :280/:281/:282/:283 live 2026-11-01 回读全 open 且批次列=R2-21；4.4 单元 P1 台账 4 行全部在本批收敛）。核心结果面有四：①**双宿主启动窗口就绪门禁+空链固化消除**（MA-01，C1-006 主+←C4-003 吸收）——宿主桥注册（Spring `onStart` :31 / Quarkus `start()` :47）到 `IocCoreInitializer.initialize` :42 `registerInstance(appContainer)` 重注册的秒级窗口内，`getFilters` 首访把「落在 Spring context/CDI 上的空 filter 链」synchronized 永久固化——全部 IHttpServerFilter 实现均为 nop-IoC bean（全仓零 CDI/Spring 形态 filter），任一启动窗口请求即令认证链（AuthHttpServerFilter 等）持久静默旁路直至重启、不可自愈；吸收成员 C4-003 面（启动窗口无就绪门禁——filter 入口/controller 入口全集）随门禁一并消除，且门禁必须短路于 runOnContext 之外（§7.1-3 联动——否则门禁异常落入 C6-007 task 通道恶化为悬挂）。②**ContextTaskQueue 吞异常 future host 层终结**（MA-02，C6-007）——`withRoutingContext` 把「取结果」与「异常转译」合并在 `bindResult(task.get(), future)` 单表达式：task supplier 同步异常逃出 lambda 落入平台 `ContextTaskQueue.flushTasks` catch-Throwable-仅记日志（live :157-159 吞异常语义在位、平台面零触碰），返回 RESTEasy 的 future 永不完成——`/px/` 畸形 JSON 用户可触发的认证请求永久悬挂（future 族第 5 例宿主侧；族先例 4.2-C3-001[plan 2521]/4.3-C3-001[plan 2522]/3.2-C1-001/1.7-C6-001——R21 族 completeExceptionally 纪律范式引用不重开）。③**上传流先校验后消费失败路径配对 close**（C6-001，双宿主 F3 孪生）——controller 打开流后即交出所有权，4 道校验（checkMaxSize/checkFileExt/checkBizObjName/checkUploadAuth）任何一道先行抛出即形成「半个获取」：`DaoResourceFileStore.saveFile` :241 finally（唯一关闭点）永不执行——每被拒上传泄漏 1 个 fd，外部可稳定触发；Quarkus 孪生 `QuarkusFileService` 同根因（RESTEasy 缓冲区背书，泄漏面为堆内流），同 diff 同修。④**SpringWebProxy 凭据对 volatile 化**（C1-001）——`WebClientHolder.authorization/cookie` 两字段由 Reactor 回调线程写（`doOnNext` 内 cookie :149 先、authorization :154 后非原子双写）、请求线程读（callRest :81-86 / callWeb :107-112），无 volatile 无锁无 happens-before——撕裂凭据对（cookie#2+token#1）可驻留至下一次成功刷新，网关型部署 token 过期窗口内并发请求持续 401。批末 roadmap 8.30 → done，cra-index 四行收敛 + C10 五条成员面（强制 4+建议 1，全条承载）随本体同 diff 落地。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-21 = 4 项成员全 P1（MA-01[C1-006 主登记+←C4-003 成员并入——C4-003 为 P2 独立工单级缺陷但按 merge-adjudication :92 落 MA-01 成员位、随 C1-006 P1 工单门禁一并处置；MA-02[C6-007 主]；C6-001[独立]；C1-001[独立]）；cra-index live 2026-11-01 回读 R2-21 批次列 open 四行 = :280/:281/:282/:283；roadmap 8.30 行 Status=todo。依赖核对（live 2026-11-01）：**软依赖 R1-04/R1-01 均已解除**——roadmap :147 8.5（R1-04）done（plan 2499）+:144 8.2（R1-01）done（plan 2496）；「C6-007 平台根因绝缘层移交 2.3-CL-018+2.1-C3-001 先行」为站点溯源注记——绝缘层属平台面（WI 2.3 CL-018 承重原语/WI 2.1 C3-001 周期任务），与本批宿主面正交，本批零触碰平台、零回退义务。无跨批硬依赖（无前置，可并行）。
- **MA-01 吸收结构（merge-adjudication :18-23/:92）**：工单 = C1-006（主登记，P1 升自 P2——复核补全变体 B 空链固化机制双宿主坐实）+ C4-003（吸收成员，P2——「启动窗口无就绪门禁」，失效面 = 两宿主 filter 入口 + 五类 controller 入口随门禁传递性消除）；危险窗口机制 live 亲读在位：Spring `NopSpringCoreAutoConfig.onStart` :28-34（`BeanContainer.registerInstance(container)` :31 = 宿主桥注册点）→ `IocCoreInitializer.initialize` :37-57（`BeanContainer.registerInstance(appContainer)` :42 = 覆盖重注册点）；Quarkus `QuarkusIntegration.start` :26-52（`registerInstance` :47）。**修复批约束（merge-adjudication :23 成文，本批对齐纪律）**：门禁失败必须 503/fail-fast 短路于 runOnContext **之外**——若门禁异常落入 C6-007 的 task 通道将恶化为悬挂；R2 负结果记录：QuarkusIntegration.start 本体微秒级无独立阻塞面，不另立。
- **C10 验收前置入批集（映射 SSOT = `inventory-p2-c10.md` :584 WI 4.4 节 + :803 小结行 + `C10-test-protection.md` 逐条；随本体同 diff 落地[E2]——本批五条全条承载零半边[勘误 4]）**：
  - C10-006→MA-01（getFilters 启动窗口空链固化零测试保护——承载**固化解除钉**[未初始化态污染缓存→初始化完成后断言重算出真链——当前即红]+**门禁 fail-fast 钉**[C10-006 建议形态 (a)]；两宿主孪生同测[F3 同修同测]）；
  - C10-009→MA-01（启动窗口无就绪门禁零测试保护——承载**就绪门禁 503/fail-fast 钉**[stub 未初始化态调 filter → 断言不触达下游；初始化态回归断言直通]——与 C10-006 门禁钉同体两面，落两宿主孪生 `TestHttpServerFilterSnapshotGate` 形态[载体命名沿 C10-006 建议，Phase 1 裁定⑤定夺[R2 Minor-5.5：裁定⑤定夺为 `TestHttpServerFilterSnapshotGate`（Spring）/`TestVertxHttpServerFilterSnapshotGate`（Quarkus）取代 plan 建议名]]）；
  - C10-011→C6-001（上传流校验失败路径泄漏零测试保护——承载**close 计数钉**[close 计数 InputStream 包装 + stub 校验链抛 NopException → 断言 close 被调——当前即红]+**防双关钉**[成功路径 saveFile finally 兜底恰一次]；fd 计数形态对照 4.3 `TestFileDownloadSinkClose`/TrackingInputFile 先例[plan 2522——**fd 真实计数缺陷态 GC 假绿教训在案，禁用真实 fd 计数形态**]；spring-file + quarkus-file 测试树各一[F3 同测]）；
  - C10-017→MA-02（withRoutingContext 同步异常→future 悬挂零测试保护——承载**future.get(timeout) 断言钉**[stub IContext.runOnContext 内联回调+supplier 抛出 → 修复后 `ExecutionException`；缺陷态 TimedOut 红——挂死类失败模式=超时]；平台吞异常通道用例归 2.3-CL-018 面不在本单元——零代做声明）；
  - C10-001→C1-001（WebClientHolder 凭据撕裂零测试保护——承载**代际一致交错钉**[CountDownLatch 钉写线程于两写之间→读线程断言凭据对同一刷新代际——缺陷态撕裂红； nop-spring-proxy 测试树需新建]）。
- **4.4 批前已收口面（live 形态登记，零触碰）**：R1-01（plan 2496）14 项全修+nop-kernel 绝缘层本体在案（周期任务异常绝缘面——与 C6-007 宿主面正交）；R1-04（plan 2499）bindCancel+上下文传播五入口在案（`TestContextProviderThenOnContext` 等平台钉——live 基线亲跑在列零回退）；R21 族 4 例先例全 fixed（2521/2522 范式可引用不重开）；plan 2507 concurrent-start join 已落 `IocCoreInitializer`（:44-56 awaitStartFinished——审计基线后平台漂移，勘误 2 登记；fail 路径 :52-55 恢复 parentContainer 语义与本批门禁窗口论证相关，Phase 1 须核）。
- **平台设施与测试资产盘点（live 实读 @ `ea0f89ce2c` + 基线亲跑）**：
  - **基线测试结果（2026-11-01 亲跑，三联裁定实录[勘误 5]）**：r1 `./mvnw test -pl nop-spring,nop-quarkus -am` 在 nop-core 停于已知 flake `TestResourceCacheInvalidationChain` **3/4 失败**（预登记 flake ①[2517–2523 七批留痕先例]；`_tmp/baseline-2524-host.log` EXIT=1；nop-core 547 tests 其余全绿）→ solo `./mvnw test -pl nop-kernel/nop-core -Dtest=TestResourceCacheInvalidationChain` **4/4 绿 EXIT=0**（`_tmp/baseline-2524-core-solo.log`）→ r2 全链复跑**BUILD SUCCESS EXIT=0**（`_tmp/baseline-2524-host-r2.log`；宿主触达面 51 tests 全绿——nop-spring 37[core-starter 13 + web-starter 24] + nop-quarkus 14[web 14]，file/proxy 三模块零测试树）——三联闭合，宿主两聚合器绿色基线在案。
  - **测试资产（live 核实 + r2 基线亲跑计数）**：nop-spring 2 个 test-bearing 模块——web-starter **5 类 24 用例**[R1 Minor-F5 更正 + R2 Minor-2 复核再勘误：live 实数 Zip 6/ServletAdapter 10/RemoveCookie 3/WsEntry 2/JsonRpc 3——multiset 相同总数 24 不变，纯跨类错位（评审 javap 级复核）]（`TestZipContentEncodingFilter`/`TestServletHttpServerContextAdapter`/`TestServletHttpServerContextRemoveCookie`/`TestSpringWsEntryContextBinding_C3_012`[plan 2499 Phase 3 子分片 A landed]/`TestJsonRpcWebSocketEndpointLifecycle`）+ core-starter **4 类 13 用例**（`TestNopSpringTransactionFactory` 4/`TestSpringResource` 4/`TestPathPattern`/`TestNopSpringBeanContainer`）+ proxy/file **零测试树**；nop-quarkus 1 个 test-bearing 模块——web **2 类 14 用例**（`TestVertxHttpServerContextAdapter` 11/`TestJsonRpcWebSocketEndpointLifecycle` 3[quarkus 孪生]）+ file **零测试树**；grpc 模块 pom 注释停用不在 reactor。**测试树空缺登记（勘误 6）**：nop-spring-proxy/nop-spring-file/nop-quarkus-file 三 pom 无 junit-jupiter test 依赖——红③红④载体测试树需新建 + pom 补 test dep（build 面动作非产品代码，Phase 2 前置项）；quarkus-web/web-starter 既有 junit-jupiter test dep 在案。红钉全部为 plain-JVM 可跑面（stub IContext/latch 交错/close 计数包装/反射字段读/RoutingContext 沿 TestVertxHttpServerContextAdapter :168-170 JDK 代理先例零依赖 stub[R1 Minor-F7]），无 docker 门控、无 @SpringBootTest/@QuarkusTest 依赖（C10-009 缺口登记「宿主零 @SpringBootTest/@QuarkusTest」live 复核维持——本批红钉不引入集成测试基建）。**[R1 Major-F3 机制点名]全局静态态翻转 = 反射写 `CoreInitialization.initialized` 私有静态 volatile 字段（:44，无公共 API）或 capped `initializeTo`**；**恢复纪律 = @AfterEach 强制 `initialized=false` 反射回写 + `BeanContainer.registerInstance(null)` 清桥**（registerInstance :35-38 无守卫可注入 stub，null 恢复可执行）——同 fork 内测试隔离论证：静态残留不跨测试类存活。
  - **先例范式可引用不重开**：R21 族 completeExceptionally 纪律（2521 `ClusterRpcClient.accept` 体+err 回调体双包裹 / 2522 `OkHttpClientImpl` fetch·upload onResponse 体 catch→completeExceptionally(adapt)——C6-007 host 层 try/catch→completeExceptionally 同族范式**引用不重开**，站点语义不同[工厂回调/HTTP 回调 vs TaskQueue drain lambda]禁照抄细节）；挂死类红钉失败模式=超时预算（2522 红④先例——红②沿例）；确定性 close 计数形态（2522 TrackingInputFile 教训——红③沿例禁真实 fd 计数）；门禁 fail-fast 兄弟对照（`CoreInitializationGuardFilter` 平台侧已有守卫但运行在 getFilters 求值之后无法防护本缺陷[audit C1-006 失效面 (e) 佐证]——本批门禁落 filter 入口更早层）；volatile 惯例对照（`JsonRpcWebSocketHandler.authHeaders/boundUserId` 平台别处同族状态按 volatile 处理[audit C1-001 失效面 (f)]——C1-001 修复对齐该惯例）。
  - **owner doc 缺口现状（2026-11-01 亲读）**：`docs-for-ai/03-modules/` 零 nop-spring.md/nop-quarkus.md（缺口声明与 roadmap 8.30 Owner Doc 列一致）；`docs-for-ai/01-repo-map/module-groups.md` :19「集成与运行时外围」行仅组级一行（`nop-spring/`、`nop-quarkus/`、`nop-network/`、`nop-integration/`——宿主集成与运行环境支持）——**通行句落点**：该行内补 nop-spring/nop-quarkus 两宿主缺口声明+`HOST-CC-001` 锚点指针（沿 2521 `CLUSTER-CC-001` 集群基础设施行先例句式）；`docs-for-ai/04-reference/source-anchors.md` 新增 `HOST-CC-001` 锚点行（命名沿 NETWORK-CC-002 :106 既有惯例，插入点 = NETWORK-CC-002 之后按表序实际行号，live 亲读裁定点）。
- **模块聚合形态 + E3 盲区核对（live pom 亲读，勘误 3）**：批次标签「nop-host」非 maven 聚合器——成员六载体文件实际分属 `nop-spring/`（三模块）与 `nop-quarkus/`（两模块）两聚合器（root pom :523/:530 顶层 module）；`./mvnw test -pl nop-spring,nop-quarkus -am` 触达全部五载体模块+上游闭包（nop-kernel 全链/nop-core-framework 含 nop-ioc/nop-http-api/nop-service-framework 相关面/nop-file/nop-biz-file-core 等）——**E3 无盲区**（较 2522/2523 的聚合器盲区形态不同：六载体全在双聚合器 reactor 内；batch-proposal 验证命令列的占位裁定注记登记 Phase 4 动作，勘误 3）。

### 对齐纪律显式条款

- **MA-01 三登记一处闭合（C1-006 主 + ←C4-003 成员）**：单 diff 覆盖双宿主两变体——变体 A（provider 未注册窗口 ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED 500）与变体 B（宿主桥后空链永久固化认证旁路）由同一门禁+固化消除消除；**门禁短路于 runOnContext 之外**（merge-adjudication :23 修复批约束——门禁落 filter 入口层[Spring doFilterInternal 最前/vertx filter lambda 最前]，不经 task 通道）；C4-003 的 controller 入口面传递性消除登记论证（filter 层门禁先于全部 controller 入口——设计文档给窗口论证表）；**C10-006+C10-009 双保护面同 diff**（固化解除钉+门禁 503/fail-fast 钉——两钉同体两面）。
- **MA-01 × C4-004/MA-05 相邻不并族（同门禁原语/同函数 adjacent 面）**：`spring-delta` 注册回调门禁面（C4-004——`NopBeansAutoConfiguration`/`NopMybatisSessionFactoryCustomizer`，与 MA-01 同门禁原语但失效层不同[装配面 refresh 期 vs 请求面]→独立工单 R4-28+家族关联单列，merge-adjudication :61）零触碰；`QuarkusExecutorHelper` TL 传播面（C1-002/MA-05——与 C6-007 同函数不同根因，R4-28 归 2.3 轨道）零触碰——**本批对两文件的修改不得顺手改 TL set/remove 配对语义**。
- **MA-02 × R21 族纪律（引用不重开）+ 平台面零触碰**：host 层最小修复 `try { CompletionStage<T> r = task.get(); FutureHelper.bindResult(r, future); } catch (Throwable e) { future.completeExceptionally(e); }`（audit 建议形态[预设]）——范式沿 2521/2522 先例引用，站点细节（TaskQueue drain lambda/MDC unbind finally 保持）禁照抄先例实现；**平台根因面零触碰**：`ContextTaskQueue.java`/`BaseContext.java`（nop-api-core，plan-first Protected Area）零修改——平台吞异常语义属 2.3-CL-018 承重原语轨（绝缘层 R1-04/R1-01 已承载相邻面，本批零回退）；失效面 (a)-(g) 七调用点逐一核对（audit 枚举闭合——(c) restQuery/pageQueryGet/pageQuery 已有保护面禁回退改写、(e) upload 自带 try/catch 面、(f) Spring 宿主无此缺陷零触碰）。
- **C6-001 双宿主同 diff + 判级注记随行**：Spring（fd 泄漏主形态）+ Quarkus（堆内流面——RESTEasy 缓冲区背书）F3 孪生同 diff 同测；**C6-008 不并族**（servlet complete() 洗白——判级维持 P2 随族留 7.1[cra-index :282 行内注记]，R4-28 MA-03 面，下载路径合规零触碰[audit 失效面 (d)]）；跨单元同族提示（`NopDatavExportTaskBizModel`/`ReportDeliveryExecutor` 两构造点——不在本单元 11 根内）零触碰不代做（登记去向：5.3/5.4 批）；**与 4.3-C6-005 家族异站提示**（merge-adjudication 权威表行 11「4.3 C6-005 异站提示」——BASE64 上传形态不同站点，plan 2522 已修零回退）。
- **C1-001 最小契约面 + 刷新去重不扩张**：volatile 不可变 Credentials 单引用整体替换（预设——一次写整体替换，对齐平台 volatile 惯例）vs AtomicReference CAS 两案一次定夺禁第三形态（裁定④域）；**legacy pair setter/getter 兼容形态**（`newWebClient` 初始化路径行为保持 + `refreshAccessToken` 双写点改单写）由裁定④定夺——消费面仅本文件三读点两写点+初始化（audit 失效面枚举闭合：webClients CHM 安全发布/config 只读/Quarkus 侧无对应模块）；**刷新去重/单飞**（多请求并发 401 交叉写——audit 建议可复用 AsyncJoinType/IResourceLockManager）**不在本批 exit criteria**（batch-proposal 增补仅「凭据对 volatile 化」——登记 Non-Blocking Follow-up，禁顺手扩张）；CL-004（refresh 无超时/refreshAuthUrl null 重试必败）独立线索零触碰（C3 轨）。
- **同变更集绑定（C10 随本体，E2）**：C10-001[C1-001 面]/C10-006+C10-009[MA-01 面]/C10-011[C6-001 面]/C10-017[MA-02 面] 与各自本体同 diff 落地，禁止先修本体后补测的拆分交付。
- **硬序条款**：本批无跨批硬依赖（软依赖 R1-04/R1-01 已解除）；批内纪律 = 每成员先红后绿或变异/回退论证（E2）；批内软序 = **MA-01 与 MA-02 同批联动交付**（§7.1-3：门禁修复单独上线会恶化启动窗口行为[门禁异常落 task 通道]——两修复同一提交序列落地，门禁语义经 completeExceptionally 兜底可证）+ MA-01 双宿主同 diff（F3 孪生禁只修单宿主）+ C6-001 双宿主同 diff。
- **跨单元同号防混**：4.4-C6-001 与 4.3-C6-001（plan 2479 已修 P0）/5.4-C6-001/5.6-C6-001 同号异站零关联；4.4-C1-001 与 4.3-C1-001（plan 2523）/2.1-C1-001/4.2-C1-001（plan 2521）/3.2-C1-001 同号异站零关联；4.4-C4-003（吸收成员）与 1.7-C4-003/2.1-C4-003 同号异站零关联——cra-index 回填时逐行带 4.4 前缀核对（inventory-p2-c10 :875 同号清单在案）。

### 4 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `ea0f89ce2c` 2026-11-01 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级；吸收成员） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 4.4-C1-006（P1，MA-01 主；←C4-003 吸收成员[启动窗口无就绪门禁]；C10-006+C10-009 双保护面；升格案 P2→P1[复核补全变体 B 双宿主坐实]） | `SpringHttpServerFilterConfiguration.java`（nop-spring-web-starter）+ `HttpServerFilterRegistrar.java`（nop-quarkus-web）+ 窗口机制锚 `NopSpringCoreAutoConfig.java`/`QuarkusIntegration.java`（各自 core-starter——**只读引用零触碰**[预设]） | Spring：`getFilters` :47-57（:48 filters==null 检查 / :50 `BeanContainer.instance().getBeansOfType(IHttpServerFilter)` / synchronized 缓存）；消费点 `doFilterInternal` :89-108（:92 getFilters 调用 / :94 isEmpty 直通 :95 filterChain.doFilter）；窗口锚 `NopSpringCoreAutoConfig.onStart` :28-34（:31 registerInstance 宿主桥）→ `IocCoreInitializer.initialize` :37-57（:42 registerInstance(appContainer) 覆盖）。Quarkus：`getFilters` :46-57（同构孪生）；消费点 `registerFilter` lambda :67-91（:73 getFilters 调用 / :75-76 isEmpty→rc.next()）；窗口锚 `QuarkusIntegration.start` :26-52（:47 registerInstance）。平台窗口机制 `IocCoreInitializer.java`（nop-core-framework/nop-ioc）initialize :37-57/:42、destroy :60-67/:64[审计后漂移，勘误 2] | 双宿主启动窗口 [宿主桥注册..IocCoreInitializer :42 重注册] 内 getFilters 首访——getBeansOfType 落在 Spring context/CDI 上而全部 IHttpServerFilter 实现均为 nop-IoC bean（全仓唯一 spring-*.beans.xml 仅 nopBaseMapper、零 CDI SyntheticBean）→ 空链 synchronized 永久固化→`isEmpty` 直通→认证链（AuthHttpServerFilter 等）持久静默旁路直至重启不可自愈；变体 A（provider 未注册更早窗口）= ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED 500（瞬态可自愈——R1 订正后仅适用 onStart 注册桥之前） | 裁定①（C10-006+C10-009 随同 diff；**双宿主同 diff F3 孪生**）：filter 入口前置 `CoreInitialization.isInitialized()` 门禁[预设——未就绪 fail-fast 类型化异常（Spring→容器 503 语义/vertx→rc.fail）[R2 Minor-5 勘误镜像：设计裁定①细化为直写 503 短路（setStatus/rc.fail）——异常通道默认 500 且 ERROR dispatch 可再入链；三处显式登记 deviation]，短路于 runOnContext 之外、先于快照求值路径——空链固化由构造消除（缓存只可能被初始化后的求值填充）]；门禁拒绝语义（抛类型化异常 vs 返回空链直通+warn——audit 建议②形态）一次定夺禁第三形态；C4-003 controller 入口传递性消除论证表（设计文档）；`CoreInitializationGuardFilter` 求值后才运行的佐证注记（为何既有守卫不防护） |
| 2 | 4.4-C6-007（P1，MA-02 主；future 族第 5 例宿主侧[先例 4.2-C3-001/4.3-C3-001/3.2-C1-001/1.7-C6-001]；平台根因绝缘层移交 2.3-CL-018+2.1-C3-001——软依赖已解除，本批宿主面修复；C10-017 面） | `QuarkusExecutorHelper.java`（nop-quarkus-web） | `withRoutingContext` :43-67（:51 future 创建 / :52 `context.runOnContext(...)` / :57-61 try{ :58 `FutureHelper.bindResult(task.get(), future)` }finally{ unbindMDC }——task.get() 同步抛出→异常逃出 lambda→bindResult 失联）；返回 future :63 永无 complete；平台吞异常锚 `ContextTaskQueue.flushTasks` :150-162（:157 task.run() / :158-159 catch Throwable→仅 LOG.error——**live 吞异常语义在位，平台面零触碰**[勘误 2]）；触发链 `QuarkusGraphQLWebService.proxy` :48-57 → `runProxy` 首行 `requestBuilder.get()`（GraphQLWebService.java:201 try 外）→ `JSON.parse(body)` 畸形输入抛 NopException | task supplier 同步异常落入平台 ContextTaskQueue catch-Throwable-仅记日志，返回 RESTEasy 的 future 永不完成——`/px/` 畸形 JSON 用户可触发的认证请求永久悬挂（Quarkus 默认无请求级超时；每畸形请求稳定制造 1 个悬挂连接；启动窗口内 getBeanByType 抛出面（失效面 (b)）同路径——与 MA-01 门禁联动恶化面） | 裁定②（C10-017 随同 diff；**R21 族纪律引用不重开**）：host 层 `task.get()` 先求值再 bindResult，catch Throwable→`future.completeExceptionally(e)`[预设——audit 建议形态]；MDC unbind finally 保持；失效面 (a)-(g) 七调用点核对（(c) 已保护面禁回退、(f) Spring 宿主零触碰、平台面零触碰声明）；**§7.1-3 联动条款兑现**（与 MA-01 同批交付——门禁异常路径经本修复兜底 completeExceptionally 可证不悬挂）；catch Throwable vs Exception 一次定夺（裁定②域[预设 Throwable 对齐队列语义]） |
| 3 | 4.4-C6-001（P1，独立；C10-011 面；4.3-C6-005 同族异站提示[plan 2522 已修零回退]；C6-008 判级维持 P2 随族留 7.1 不并族[cra-index :282 行内注记]） | `SpringFileService.java`（nop-spring-file）+ `QuarkusFileService.java`（nop-quarkus-file——失效面 (b) F3 孪生） | Spring：`upload` :41-61（:46 `file.getInputStream()` 开流 / :49-53 buildUploadRequestBean / :55 uploadAsync / :56-58 仅 catch IOException / **无 finally 无 close**）；校验链锚 `NopFileStoreBizModel.upload` :101-107（:102 checkMaxSize / :103 checkFileExt / :104 checkBizObjName / :105 checkUploadAuth / :107 saveFile——4 道校验先于消费）；唯一关闭点 `DaoResourceFileStore` :240-241（finally `IoHelper.safeCloseObject(is)` 仅消费路径可达）。Quarkus：`uploadFileAsync` :53-96（:76 `inputPart.getBody(InputStream.class, null)` 先开流后进校验链——RESTEasy 缓冲区背书泄漏面为堆内流） | 上传流「先校验后消费」失败路径永不关闭——校验异常（用户可控字段 .exe/空 bizObjName/无权限均可命中）直接抛出，唯一 finally 关闭点永不可达——每被拒上传泄漏 1 个 fd（file-backed FileInputStream），外部可稳定重复触发至 ulimit 耗尽全服务 IO 失效 | 裁定③（C10-011 随同 diff；**双宿主同 diff F3 孪生**）：获取侧配对 close[预设 = `UploadRequestBean` 实现引用侧持有流 + `res.whenComplete` 注册关闭（成功/失败双路径均关）——audit 建议形态；**[R1 Minor-F6 精度]注入点 = stub `IGraphQLEngine.executeRpcAsync`（BeanContainer.registerInstance 注入）非直接 stub 校验链；送达语义 = 引擎 toRpcResponse（:582 exceptionally + :586 catch Exception）将校验失败包装为正常完成的 isOk=false 响应——whenComplete 双路径均可靠触发；前段同步抛出段（getGraphQLEngine/newRpcContext）close 兜底为设计文档义务条款（Risks 已登记，交叉引用显式化）**] vs 校验前移（元数据先行校验再开流——audit 备选）两案一次定夺禁第三形态；恰一次不变式（**[R1 Minor-F8]防双关观测点 = 宿主侧 close 恰 1 次**[stub 引擎形态下 saveFile finally 不在钉内]；saveFile 兜底依赖 IoHelper.safeClose 幂等论证）；IOException catch 分支行为保持（现有 error-response 语义零变更）；跨单元两构造点（NopDatavExportTaskBizModel/ReportDeliveryExecutor）零触碰 |
| 4 | 4.4-C1-001（P1，独立；C10-001 面；CL-004 同文件独立线索零触碰） | `WebClientHolder.java` + `SpringWebProxy.java`（nop-spring-proxy） | `WebClientHolder` :8-9（`authorization`/`cookie` 普通可变字段无 volatile/锁）；写点 `SpringWebProxy.refreshAccessToken` :129-157（doOnNext :146-156——:149 `setCookie` 先 / :154 `setAuthorization("Bearer "+token)` 后**非原子双写**）；读点 `callRest` :81-86 / `callWeb` :107-112（请求线程构造下游头）；入口 `sseStream` :41-56 / `restProxy` :58-73 共用 holder；初始化 `newWebClient` :163-177（:174-175 配置注入——启动期单线程安全面） | 凭据对由 Reactor 回调线程写、请求线程读，无 volatile 无锁无 happens-before——撕裂凭据对（cookie#2+token#1 代际错配）可驻留至下一次成功刷新；网关型部署（/rest/{provider}、/sse/{provider}）token 过期窗口内并发请求持续 401 重试链耗尽 | 裁定④（C10-001 随同 diff）：volatile 不可变 `Credentials` 单引用整体替换[预设——一次写整体替换，对齐 `JsonRpcWebSocketHandler` 平台 volatile 惯例] vs `AtomicReference<Credentials>` CAS 两案一次定夺禁第三形态；legacy setter/getter 兼容形态一次定夺（初始化路径行为保持——裁定④域）；**[R1 Major-F2 兼容收口义务]红④ latch 钉写线程驱动器随兼容形态同步收口——委托 setter 形态下钉改打单写 API（refresh 等价路径），移除形态下钉改新 API；缺陷态红与修复态绿分别以对应 API 形态登记（防委托 setter 残留双写构造混合代际的假红/假绿）**；刷新去重/单飞不扩张（Non-Blocking Follow-up）；CL-004 零触碰 |

### PA 判定表（4 成员逐一，orm/_生成物/平台设施/api 叶触碰与否；live 2026-11-01 核实）

| # | Finding | 站点模块 | 站点形态 | 触 `model/*.orm.xml`？ | 触 `_` 生成物？ | 触 nop-api-core/nop-kernel 平台设施？ | 触 nop-http-api？ | 判定依据与条件申报 |
|---|---|---|---|---|---|---|---|---|
| 1 | MA-01（C1-006+←C4-003） | nop-spring-web-starter + nop-quarkus-web main java | filter 入口就绪门禁+固化消除 | 否 | 否 | 否（`CoreInitialization.isInitialized()` 为 nop-core 公共静态只读 API 零修改；`IocCoreInitializer`/`NopSpringCoreAutoConfig`/`QuarkusIntegration` 为只读窗口锚零触碰——门禁落宿主 filter 入口层） | 否（`IHttpServerFilter` 契约零变更；两宿主实现为 impl 叶） | 纯 Java 修复；`find` 亲证两聚合器零 `_` 文件零 orm.xml |
| 2 | MA-02（C6-007） | nop-quarkus-web main java | task.get() 求值序重排+completeExceptionally | 否 | 否 | **否（平台根因面零触碰声明）**：`ContextTaskQueue`/`BaseContext` 为引用证据零修改——nop-api-core plan-first Protected Area 不触发；平台吞异常语义归 2.3-CL-018 轨 | 否 | 纯 Java 修复；host 层最小修复不动平台（audit 建议明文） |
| 3 | C6-001 | nop-spring-file + nop-quarkus-file main java | 获取侧配对 close | 否 | 否 | 否 | 否（上传/下载走平台 file 面——`UploadRequestBean`/`AbstractGraphQLFileService` 契约签名零变更[如裁定③选 AutoCloseable 形态须核对 `UploadRequestBean` 归属模块——若落 nop-file-core 平台叶须沿裁定⑥先例申报，预设实现形态在宿主侧包装不触平台]） | 纯 Java 修复；`UploadRequestBean` 归属核对为裁定③前置核对项 |
| 4 | C1-001 | nop-spring-proxy main java | volatile Credentials 单引用 | 否 | 否 | 否 | 否（WebClient/Spring reactive 为第三方依赖面） | 纯 Java 修复；消费面仅本文件三读点两写点（audit 失效面枚举闭合） |

**结论：头部零 orm 触碰、零 `_` 生成物触碰、零 nop-api-core/nop-kernel 平台设施触碰、零 nop-http-api 触碰（4/4 成员全部宿主 impl 叶——批次 PA=无[batch-proposal R2-21 行显式「无」]零条件申报触发；C6-009 批外注记不涉本批四成员）；唯一条件申报触点预设为裁定③如选 UploadRequestBean 平台叶形态（预设宿主侧包装规避）。站点分布于 nop-spring（web-starter/proxy/file 三模块四文件）与 nop-quarkus（web/file 两模块三文件）——两聚合器五模块，E3 双聚合器命令直达零盲区。**

### Baseline 勘误（输入不一致登记）

1. **任务书审计目录号勘误（输入层）**：起草任务书所记审计目录 `2026-10-21-1326-deep-audit-nop-host-concurrency` live 不存在——实际目录 = `ai-dev/audits/2026-10/2026-10-21-1223-deep-audit-nop-host-concurrency/`（cra-index :39 审计登记行同证）。本 plan 全文按 1223 实际路径引用；成员发现详情引用不受影响（C1/C6/C6-round2/merge-adjudication/C10/summary 六文件亲读吻合）。
2. **成员行号漂移勘误（审计基线 2026-10-21 `2a2d546fc0` → live `ea0f89ce2c`；漂移根因 = 平台面后续批次 landed，成员站点文件本身零 commit[git log 2a2d546fc0..HEAD 亲证七载体文件零变更]）**：`IocCoreInitializer.initialize` 审计 :41-49 → live :37-57（registerInstance 审计「:41-49 覆盖」→ live :42；漂移根因 = plan 2507 concurrent-start join 增补 :44-56 awaitStartFinished 块 + fail 恢复 :52-55；destroy 审计 :56-61 → live :60-67/:64）——窗口机制语义零漂移（registerInstance 覆盖窗口仍在位）；`ContextTaskQueue.flushTasks` 审计 catch「:104-107,150-156」→ live syncGet 内联 run catch :71-73 + flushTasks :150-162（:157 task.run/:158-159 catch Throwable 仅 LOG；漂移根因 = C2-016 排空-复检注释块 landed）——吞异常语义零漂移（两 catch 站点均在位）；`QuarkusFileService.uploadFileAsync` 审计 :76-90 → live :53-96（getBody 锚 :76 零漂移，方法体尾部 +6 行——异常分支 thenApply 形态在位）；**其余五载体文件（SpringHttpServerFilterConfiguration/HttpServerFilterRegistrar/QuarkusExecutorHelper/SpringFileService/WebClientHolder/SpringWebProxy）+ 校验链锚（NopFileStoreBizModel :101-107/DaoResourceFileStore :240-241）+ 窗口锚（NopSpringCoreAutoConfig :28-34/:31、QuarkusIntegration :26-52/:47）逐行亲读零漂移**。全部为行号漂移，缺陷机制零漂移。
3. **E3 验证命令占位裁定（批次定义层）**：batch-proposal R2-21 行验证命令「`./mvnw test -pl <宿主聚合模块> -am`（以 nop-host 实际 maven 路径为准）」为显式占位——live pom 亲读裁定：「nop-host」非 maven 聚合器，成员六载体分属 `nop-spring/`（web-starter/proxy/file）与 `nop-quarkus/`（web/file）两聚合器（root pom :523/:530）——**E3 = `./mvnw test -pl nop-spring,nop-quarkus -am`**（基线亲跑在案，勘误 5）。Phase 4 回写时在 roadmap 8.30 状态注记登记「R2-21 验证命令 live 裁定 = nop-spring,nop-quarkus 双聚合器，六载体零盲区」（沿 2523 勘误 1 先例形态，不改 L95 原文）。
4. **C10 口径（对照 2523 半边口径的简化声明）**：本批 C10 五条（C10-001/006/009/011/017）**全部为全条承载零半边**——P1 四工单的保护面全部随本体同 diff 落地，无跨批拆分；反向钉死 4 处（RP-1→CL-008 watch/RP-2、RP-4→C4-002/RP-3→C4-004）**全部归 R4-28**（inventory-p2-c10 :803 明文——本批零承载零触碰）；「4.4 计数口径」cra-index :72 在案。Phase 4 收敛注记按此书写。
5. **E3 上游链与环境三联预登记（批前既有，沿 plan 2512–2523 已知面）**：`./mvnw test -pl nop-spring,nop-quarkus -am` 上游闭包 = nop-kernel（nop-api-core/nop-commons/nop-core/nop-dataset/nop-javac 等）/nop-core-framework/nop-xlang 链/nop-http-api/nop-file 链/nop-service-framework 相关面——已知四面 flake 中**仅 nop-core `TestResourceCacheInvalidationChain` 负载 flake 在闭包内**（2517–2523 七批留痕先例；本批起草期 r1 亲跑实录：3/4 失败→solo 4/4 绿→r2 全链绿三联闭合，`_tmp/baseline-2524-host.log`/`-core-solo.log`/`-host-r2.log`）；debezium docker fork timeout/stream-runtime E2E 收敛/auth MFA TOTP 窗口三面不在闭包。上游环境抖动按「stash 基线同红 / solo 绿 / 恢复复跑全绿」**三联裁定**处理并留痕，不得静默重跑或跳过。
6. **测试树空缺登记（勘误，build 面）**：nop-spring-proxy/nop-spring-file/nop-quarkus-file 三 pom 无 junit-jupiter test 依赖（pom 亲读零 test scope）——红③红④载体测试树需新建并补 test dep（依赖管理面动作非产品代码；quarkus-web/nop-spring-web-starter 既有 junit-jupiter 在案）。C10-001/011「模块测试树为空」登记 live 复核维持。新增测试树 = 新目录 `src/test/java` + pom dep——登记 Phase 2 前置项，零产品行为触碰。
7. **[Phase 1 起草增补条款（预留）]** 裁定①触碰 filter 入口层语义（Spring `doFilterInternal` 最前/vertx filter lambda 最前）与 C10-009「controller 入口全集」传递性消除论证——设计文档须给出窗口论证表（门禁位置×未就绪请求路径矩阵：filter 层拦截 vs controller 层残留面）；裁定①须核对 plan 2507 后 `IocCoreInitializer` fail 路径（:52-55 恢复 parentContainer）对门禁窗口论证的影响（fail 后容器回落宿主桥的窗口语义登记）；裁定②须给出失效面 (a)-(g) 七调用点核对表与 (c) 已保护面零回退声明；裁定③如选 whenComplete 形态须给出 `UploadRequestBean` 归属模块核对结论（宿主侧包装 vs 平台叶触碰申报）；裁定④须给出 legacy setter/getter 兼容矩阵（`newWebClient` 初始化/refresh 双写点改单写/三读点）。

## Goals

- Phase 1 设计裁定文档一次裁决 5 项裁定（裁定①–⑤，4 成员各一条 + 测试缝/零回退钉清单，含 MA-01 双宿主同 diff 与 §7.1-3 联动条款成文）+ 独立 draft review 通过，无临场发明空间：①MA-01 filter 入口就绪门禁形态+固化消除（双变体单 diff+C4-003 传递性消除论证+runOnContext 之外短路）②MA-02 host 层 future 终结形态（R21 族纪律引用+平台面零触碰+§7.1-3 联动兑现）③C6-001 获取侧配对 close 形态（whenComplete vs 校验前移两案定夺+双宿主 F3 同 diff+恰一次不变式）④C1-001 volatile Credentials 形态（单引用替换 vs CAS 两案定夺+legacy 兼容矩阵+刷新去重不扩张）⑤测试缝与红①–红④驱动器表+零回退钉清单——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。设计文档拟名 `ai-dev/design/concurrency/host-startup-readiness-gate-future-exception-termination-upload-close-and-credential-pair-concurrency.md`（Phase 1 产出物，前向引用；命名裁定：启动窗口门禁（MA-01 域）+ future 异常终结（MA-02 域）+ 上传流配对关闭（C6-001 域）+ 凭据原子性（C1-001 域）四域合一，与现有 concurrency 设计集命名风格一致；如内容演进更适可调，调整时在本 plan 登记命名裁定）。
- 4 项成员全部修复并各带回归测试（rule 25）；C10 收口门（C10-001/C10-006+C10-009/C10-011/C10-017 五条全条承载[勘误 4 口径]）随本体同 diff 落地（E2）。
- 关键工单先红后绿或变异/回退论证留痕（E2），红①–红④逐成员登记：红① 固化红（MA-01：未初始化态（宿主桥已注册/平台未满级）[R1 Major-F1 驱动器改写：钉经 filter 入口（doFilterInternal :92 前 / vertx lambda :73 前）驱动——修复=入口门禁、getFilters 本体不改，直呼 getFilters 的钉在修复后仍固化空链永不变绿；固化观测 = 初始化后同入口断言真链]经 filter 入口驱动 → 初始化完成后同入口断言真 filter 链——缺陷态空链固化红（**当前即红**，C10-006 建议形态 (b) 单线程可构造零竞态）→门禁绿；rule-23 伴生钉 = 未就绪期 getBeansOfType 零调用；门禁钉（C10-009 面）：stub 未初始化态调 filter → 断言不触达下游处理链（fail-fast/503 形态）+ 初始化态回归断言直通；两宿主孪生同测）；红② 悬挂红（MA-02：stub IContext.runOnContext 内联回调 + supplier 抛 NopException → 断言 `future.get(有限预算)` 后 ExecutionException——缺陷态 TimedOut 红（**挂死类：失败模式=超时，给足有限 timeout 并在钉内注释失败模式语义**[沿 2522 红④先例]；**当前即红=先行失败测试**，C10-017 建议形态零平台依赖可构造）→completeExceptionally 绿）；红③ 泄漏红（C6-001：close 计数 InputStream 包装 + stub 校验链抛 NopException → 断言 close 恰一次——缺陷态零关闭红（**当前即红**，C10-011 建议形态）+ 成功路径防双关钉（saveFile finally 兜底恰一次）；**确定性 close 计数形态禁真实 fd 计数**[2522 TrackingInputFile GC 假绿教训]；双宿主各自跑同型钉）；红④ 撕裂红（C1-001：CountDownLatch 钉写线程于 setCookie 与 setAuthorization 之间 → 读线程断言凭据对同一刷新代际——缺陷态撕裂红（**构造性交错红非竞态红**，C10-001 建议形态）→volatile 单引用绿[两写间状态由构造消除]；初始化路径行为保持防过修钉）。
- MA-01×MA-02 §7.1-3 联动条款兑现留证：门禁短路位置在 runOnContext 之外（filter 入口层）+ completeExceptionally 兜底可证（红②钉 + 设计文档联动矩阵）——两修复同批交付闭合「门禁单独上线恶化悬挂」风险。
- Owner doc 缺口声明落账：`docs-for-ai/01-repo-map/module-groups.md` :19「集成与运行时外围」行内补 nop-spring/nop-quarkus 两宿主缺口声明句（无 03-modules owner doc→缺口登记，owner doc 建设归 R-G 治理批；并发契约以 source-anchors `HOST-CC-001` 锚点 + 设计集为准——沿 2521 CLUSTER-CC-001 行先例句式）+ `docs-for-ai/04-reference/source-anchors.md` 新增 `HOST-CC-001` 锚点行（四成员文件行号锚 + 契约 + 回归钉族 + 设计权威指针，修复后 live 亲读核实）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-28 全部成员**（G70 双宿主/反向钉束 10 项：MA-03[C6-008 complete() 洗白]/MA-04[C6-003 onClose 无归属守卫←C6-002 只 start 不 stop]/MA-05[C1-002 g_serverContext TL 传播]/C1-003 WS basicRemote 并发写/C1-004 懒初始化 check-then-put/C4-001 过滤链 syncGet 阻塞/C4-002 keepalive 滞留[RP-2/RP-4 前置]/C4-004 spring-delta 注册回调静默空发现[RP-3 前置]/C6-004 startAsync 无超时[搭车 C1-005 volatile]/C6-005 Gauge 绑旧池）——特别地 **C1-002 不代做**：MA-02 修复仅动 task.get() 求值序+异常终结，`g_serverContext` TL set/remove 配对零触碰（同函数不同根因）；**C4-004 不代做**：MA-01 门禁仅落请求面 filter 入口，spring-delta 装配面 refresh 期注册回调零触碰（同门禁原语不同失效层）；**C6-008 不代做**：C6-001 修复仅动上传流获取侧配对，servlet 流式响应 complete() 语义零触碰。
- **不做平台面**：`ContextTaskQueue`/`BaseContext`（nop-api-core——2.3-CL-018 承重原语轨，绝缘层 R1-04/R1-01 已承载相邻面）；`IocCoreInitializer`/`CoreInitialization`（nop-core-framework/nop-ioc+nop-core——只读引用）；`NopSpringCoreAutoConfig`/`QuarkusIntegration`（窗口锚只读[预设——如裁定①定夺需要在集成层挂就绪钩子翻转零触碰预设，须在设计文档登记并沿 PA 条款核对]）。
- **不做 C6-009**（`IHttpServerContext.sendResponse(InputStream)` 释放契约 P3——nop-http-api plan-first 批外注记，batch-proposal :300）。
- **不做刷新去重/单飞**（C1-001 多请求并发 401 交叉写去重——audit 建议可复用 AsyncJoinType/IResourceLockManager，超出 batch exit criteria「凭据对 volatile 化」最小面——Non-Blocking Follow-up 登记）；**CL-004 零触碰**（refresh 无超时/refreshAuthUrl null 重试必败——C3 轨独立线索）。
- **不做跨单元 C6-001 同号站**（5.3 `NopDatavExportTaskBizModel`/5.4 `ReportDeliveryExecutor` 两构造点——不在本单元 11 根内，登记跨单元同族提示随各批）。
- 不做平台层改码：nop-api-core/nop-core/nop-ioc 零触碰；`IHttpServerFilter`/`IHttpServerContext`/`UploadRequestBean`[预设宿主侧包装形态]/`ApiRequest` 契约签名零变更；不做 nop-spring/nop-quarkus 其余叶（spring-delta[spring-delta 注册门禁归 R4-28]/web-orm-starter/mcp/core-starter 生命周期面[C6-002/003 归 R4-28]）任何触碰。
- 不触碰 `_` 前缀生成物；R4-28 批外成员本体；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/SpringHttpServerFilterConfiguration.java`（裁定①——就绪门禁+固化消除）。
- `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/filter/HttpServerFilterRegistrar.java`（裁定①——孪生同 diff）。
- `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/utils/QuarkusExecutorHelper.java`（裁定②——task.get() 求值序+completeExceptionally）。
- `nop-spring/nop-spring-file/src/main/java/io/nop/file/spring/web/SpringFileService.java`（裁定③）、`nop-quarkus/nop-quarkus-file/src/main/java/io/nop/file/quarkus/web/QuarkusFileService.java`（裁定③——F3 孪生同 diff）。
- `nop-spring/nop-spring-proxy/src/main/java/io/nop/spring/proxy/WebClientHolder.java` + `SpringWebProxy.java`（裁定④）。
- 测试树：nop-spring-web-starter + nop-quarkus-web（红①双宿主孪生钉 + 红②悬挂钉 + 既有 7 类零回退）、nop-spring-file + nop-quarkus-file（红③双宿主钉——**测试树新建 + pom test dep 补齐[勘误 6]**）、nop-spring-proxy（红④交错钉——测试树新建 + pom test dep）；既有 51 tests 零回退（nop-spring 37 + nop-quarkus 14）。
- 设计文档：`ai-dev/design/concurrency/host-startup-readiness-gate-future-exception-termination-upload-close-and-credential-pair-concurrency.md`（新建；裁定①–⑤+测试缝+零回退钉+MA-01 双宿主/§7.1-3 联动/C6-001 双宿主对齐条款，最终状态写法；命名调整须登记裁定）。Owner 缺口声明：`docs-for-ai/01-repo-map/module-groups.md`（:19 行内补句）、`docs-for-ai/04-reference/source-anchors.md`（HOST-CC-001 行）。

### Out Of Scope

- Non-Goals 所列全部项；R4-28 批次工单；2.3-CL-018/2.1-C3-001 平台绝缘面；C6-009 本体；CL-004/刷新去重；`model/*.orm.xml` 与 `_` 前缀生成物（预设零触碰）；nop-api-core/nop-kernel 平台设施（预设零触碰——如执行翻转沿裁定⑥先例申报）；`IHttpServerFilter`/`IHttpServerContext`/`UploadRequestBean` 契约演进；nop-spring/nop-quarkus 非载体叶模块；nop-cluster/nop-network/nop-integration 模块。

## Execution Plan

### Phase 1 - 双宿主就绪门禁/future 异常终结/上传流配对/凭据原子性并发设计裁定文档 + 独立评审

Status: completed
Targets: `ai-dev/design/concurrency/host-startup-readiness-gate-future-exception-termination-upload-close-and-credential-pair-concurrency.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下裁定+测试缝，每条唯一预设默认分支：
  - [x] **裁定① MA-01 filter 入口就绪门禁+固化消除（三登记一处闭合）**：门禁形态[预设 = filter 入口前置 `CoreInitialization.isInitialized()` 检查——未就绪 fail-fast 类型化异常（Spring 侧经容器 503 语义/vertx 侧 rc.fail），短路于 runOnContext 之外、先于 getFilters 快照求值路径——空链固化由构造消除]；门禁拒绝语义一次定夺（抛类型化异常 vs 返回空链直通+warn——audit 建议②形态[预设拒直通形态]）；双宿主矩阵（doFilterInternal :92 前/vertx lambda :73 前两落点）；C4-003 controller 入口传递性消除论证表（门禁位置×未就绪请求路径矩阵）；plan 2507 后 `IocCoreInitializer` fail 路径（:52-55 恢复 parentContainer）窗口影响登记；`CoreInitializationGuardFilter` 求值后位佐证注记；C4-004 装配面零触碰声明
  - [x] **裁定② MA-02 host 层 future 终结（§7.1-3 联动兑现）**：求值序重排形态[预设 = `task.get()` 先求值、catch Throwable→`future.completeExceptionally(e)`、bindResult 移入 try 内]；catch Throwable vs Exception 一次定夺[预设 Throwable 对齐队列语义]；MDC unbind finally 保持声明；失效面 (a)-(g) 七调用点核对表（(c) 已保护面零回退/(e) 自带 catch 面/(f) Spring 宿主零触碰）；平台面零触碰声明（ContextTaskQueue/BaseContext 归 2.3-CL-018 轨）；联动矩阵（门禁异常路径经 completeExceptionally 兜底——红②钉兑现证明义务）
  - [x] **裁定③ C6-001 获取侧配对 close（双宿主 F3 同 diff）**：配对形态一次定夺禁第三形态[预设 = `UploadRequestBean` 引用侧持有流 + `res.whenComplete` 注册关闭（成功/失败双路径）——audit 建议形态 vs 校验前移（元数据先行校验再开流——audit 备选，注意校验前移改变校验时序语义须论证）]；`UploadRequestBean` 归属模块核对结论（宿主侧包装 vs 平台叶触碰——平台叶形态须沿 PA 条款申报）；恰一次不变式（成功路径 saveFile finally 兜底不双关）；IOException catch 分支行为保持声明；Quarkus 孪生同 diff 形态（缓冲区背书堆内流面）
  - [x] **裁定④ C1-001 volatile Credentials 单引用（C10-001 面随同 diff）**：两案一次定夺禁第三形态[预设 = volatile 不可变 `Credentials` 单引用整体替换（一次写整体替换）vs `AtomicReference<Credentials>` CAS]；legacy pair setter/getter 兼容矩阵（`newWebClient` :174-175 初始化路径行为保持/refresh :149+:154 双写点改单写/callRest :81-86+callWeb :107-112+refreshAccessToken :137-141 三读点读法——消费面枚举闭合）；对齐平台 volatile 惯例注记（JsonRpcWebSocketHandler 先例）；刷新去重/单飞不扩张声明（Non-Blocking Follow-up 去向）；CL-004 零触碰声明
  - [x] **裁定⑤ 测试缝与红①–红④驱动器表+零回退钉清单**：红①–红④ 测试形态逐条（含缺陷态红判据[当前即红三条：红①固化钉/红②悬挂钉/红③泄漏钉 + 构造性交错红一条：红④撕裂钉] + 挂死类失败模式=超时给足有限 timeout + 全部 plain-JVM 可跑面声明[无 docker 门控/无 @SpringBootTest 依赖]）；红④ latch 交错与 volatile 转绿的构造性论证（两写间状态由构造消除——非竞态碰运气）；红③确定性 close 计数形态声明（禁真实 fd 计数——2522 TrackingInputFile GC 假绿教训引用）；零回退钉 = nop-spring 37 tests + nop-quarkus 14 tests（双 log 路径），其中点名：`TestJsonRpcWebSocketEndpointLifecycle` 两宿主孪生/`TestVertxHttpServerContextAdapter`/`TestServletHttpServerContextAdapter`/`TestSpringWsEntryContextBinding_C3_012`[plan 2499 landed]/core-starter 4 类；补测类标注「新增断言即新钉、缺陷态红→修复后绿」；三 pom test dep 补齐项（勘误 6）
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动；记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑤全部项且每条有唯一预设默认分支，独立 draft review 记录在案（文件命名如调整已登记裁定）
- [x] 裁定①含双宿主矩阵+C4-003 传递性论证+runOnContext 之外短路；裁定②含 R21 族引用+平台零触碰+失效面七点核对+联动矩阵；裁定③含两案定夺+恰一次不变式+归属核对；裁定④含兼容矩阵+不扩张声明；裁定⑤含红①–红④驱动器表与零回退钉全列
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner 缺口声明更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 4 成员实现 + 回归测试（按子域分组）

Status: completed
Targets: `nop-spring/nop-spring-web-starter/`、`nop-spring/nop-spring-proxy/`、`nop-spring/nop-spring-file/`、`nop-quarkus/nop-quarkus-web/`、`nop-quarkus/nop-quarkus-file/`（各自 main + test 树；proxy/file 三模块含 pom test dep 补齐）

- Item Types: `Fix`（4 项 P1 均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 收口门）

- [x] 组 A 双宿主就绪门禁（web-starter + quarkus-web）：MA-01（裁定①，三登记单 diff——红①固化钉[当前即红]+门禁 fail-fast 钉[C10-009 面]两宿主孪生；C10-006 面随同 diff；C4-004/MA-05 相邻面零触碰核对）
- [x] 组 B future 异常终结（quarkus-web）：MA-02（裁定②，红②悬挂钉[挂死类失败模式=超时]——R21 族纪律；平台面零触碰核对[git diff 不含 nop-api-core]；失效面 (c) 已保护面零回退核对；§7.1-3 联动兑现与组 A 同序列登记）
- [x] 组 C 上传流配对 close（spring-file + quarkus-file）：C6-001（裁定③，红③泄漏钉[当前即红]+防双关钉——双宿主同 diff 禁只修 Spring；C6-008/跨单元两构造点零触碰核对；测试树新建+pom test dep 前置项兑现[勘误 6]）
- [x] 组 D 凭据对 volatile 化（spring-proxy）：C1-001（裁定④，红④撕裂钉[构造性交错]+初始化路径防过修钉——C10-001 面随同 diff；测试树新建+pom test dep；刷新去重零扩张核对）
- [x] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕）：红①–红④快照落 `_tmp/` 并登记（`_tmp/red-green-2524-summary.md` 总册 + 逐 trial 日志）；**变异探针纪律**：凡以「注释/删除目标行 → 跑钉子 → 恢复」验证回退必红者，恢复动作必须以断言方式确认目标可执行行真实存在（grep/读取校验），防止恢复脚本半写入造成假绿（R5 教训成文义务）；挂死类红钉（红②）timeout 预算与失败模式注释逐钉留痕；红④构造性交错红声明（非竞态碰运气）
- [x] **端到端验证**（rule 22）：门禁链 = 启动窗口全链钉（宿主桥注册→未就绪请求→门禁 fail-fast→初始化完成→同请求路径→filter 真链执行，从 filter 入口到下游放行的完整路径）；future 链 = 畸形代理请求全链钉（/px/ 代理 supplier 抛出→completeExceptionally→调用方得到失败 future 而非悬挂，从 withRoutingContext 入口到 future 出口）；上传链 = 被拒上传全链钉（开流→校验拒绝→close 恰一次→错误响应正常返回，从 controller 入口到流关闭出口）
- [x] **接线验证**（rule 23）：裁定①门禁真拦截断言（未就绪请求真不触达 getBeansOfType/下游，非仅分支存在）；裁定②终结真生效断言（异常 future 真完成[ExecutionException 断言]，非仅 catch 存在）；裁定③close 真配对断言（失败路径真关闭+成功路径恰一次）；裁定④单引用真整体断言（交错点读侧所见必为完整代际，非仅字段 volatile 存在）
- [x] **无静默跳过**（rule 24）：裁定①未就绪拒绝分支不得静默直通（fail-fast 语义——禁空链直通当正常路径）；裁定②异常必终结 future（禁 catch 后仅记日志不终结——与平台吞异常形态划清边界）；裁定③失败路径必关流（禁依赖 GC 终结）；新增公共分支无空方法体/TODO

Exit Criteria:

- [x] 4 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 收口门（5 条成员面，按勘误 4 全条口径）同 diff 落地
- [x] 必红证据（E2）：红①–红④均先红后绿或变异/回退论证，快照落 `_tmp/` 并登记（红①③④构造性/当前即红形态标注；红②挂死超时形态标注）
- [x] 零回退实证：基线 51 tests（nop-spring 37 + nop-quarkus 14）+ 既有钉零回退（钉零回改逐条 git diff 为空核对；2499 landed 钉 TestSpringWsEntryContextBinding_C3_012/两宿主 TestJsonRpcWebSocketEndpointLifecycle/core-starter 4 类零触碰核对）
- [x] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [x] `./mvnw test -pl nop-spring,nop-quarkus -am` 批行验证命令执行留痕（模块/测试数/退出码/快照路径；E3 双聚合器零盲区登记[勘误 3]；上游环境抖动按三联裁定留痕[勘误 5]）；实际触达模块以 git diff 为准逐条登记（预期 web-starter/quarkus-web/spring-file/quarkus-file/spring-proxy 五模块 main+test + 三 pom test dep，无其他）；零触碰确认：产品 diff 不含 `_` 前缀生成物手改、不含 `model/*.orm.xml`、不含 nop-api-core/nop-core/nop-ioc 平台设施、不含 nop-http-api、不含 R4-28 批外成员本体（ContextTaskQueue/BaseContext/IocCoreInitializer/NopSpringCoreAutoConfig/QuarkusIntegration/ServletHttpServerContext/VertxHttpServerContext/NopBeansAutoConfiguration/spring-delta 面）；Owner 缺口声明内容定稿归 Phase 3 统一落账（显式登记去向；执行中断时本 Phase 收束前回补本清单勾选，禁跨 Phase 欠账）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner 缺口声明落账

Status: completed
Targets: nop-spring+nop-quarkus 聚合全量测试；`docs-for-ai/01-repo-map/module-groups.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [x] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（如全聚合测试通道被零 diff 上游已知 flake 阻断，授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，沿 plan 2497–2523 Phase 3 先例声明支路理由与逐面三联裁定留痕）
- [x] 批行验证命令（E3）：`./mvnw test -pl nop-spring,nop-quarkus -am` BUILD SUCCESS 留痕（模块/测试数/退出码/快照路径；上游抖动三联裁定[勘误 5 预登记面]；E3 双聚合器零盲区）+ 实际触达模块以 git diff 为准逐条登记；零触碰确认（生成物/orm 模型/平台设施/nop-http-api/R4-28 批外成员本体/既有钉面）
- [x] Owner 缺口声明落账：`module-groups.md` :19「集成与运行时外围」行内补两宿主缺口句（无 03-modules owner doc→缺口登记归 R-G 治理批；并发契约以 HOST-CC-001 锚点+设计集为准——CLUSTER-CC-001 行先例句式）；`source-anchors.md` 新增 `HOST-CC-001` 锚点行（四成员文件行号锚+契约+回归钉族+设计权威指针，修复后 live 亲读核实；NETWORK-CC-002 :106 之后按表序实际行号插入）；check-doc-links --strict 退出码 0
- [x] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-spring --severity high` 与 `--module nop-quarkus --severity high` 逐条登记（触达面 high=0 或批前既有已裁定——沿 2518–2523 先例口径，注意 shell 管道退出码误读教训，以工具真实退出码为准）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] E1 + 批行验证命令 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由；上游抖动三联处置登记
- [x] module-groups.md 两宿主缺口句 + source-anchors.md HOST-CC-001 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记
- [x] 零触碰确认登记（生成物/orm 模型/平台设施/nop-http-api/R4-28 批外成员本体/既有钉面）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: completed
Targets: 本 plan、roadmap 8.30 行、cra-index P1 台账 :280/:281/:282/:283 + C10 成员面注记、batch-proposal 勘误注记

- Item Types: `Proof`

- [x] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（门禁真拦截/future 真终结/close 真配对/凭据真整体替换运行时行为、无空方法体/静默吞异常）
- [x] cra-index 台账回填：R2-21 四行状态收敛（open→fixed，注记 plan 2524 + closure audit 结论；MA-01 行吸收成员 C4-003 注记 + C10-006/009 双保护面注记；MA-02 行 future 族第 5 例注记 + §7.1-3 联动兑现注记；C6-001 行 C10-011 注记 + 双宿主 F3 注记；C1-001 行 C10-001 注记）；roadmap 8.30 行 Status → done + 「R2-21 验证命令 live 裁定 = nop-spring,nop-quarkus 双聚合器，六载体零盲区」勘误注记[勘误 3] + 软依赖解除注记（8.5/8.2 已 done）；batch-proposal R2-21 行附近勘误注记（验证命令占位裁定——不改 L95 原文）
- [x] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 复核登记；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [x] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [x] cra-index 四行收敛 + MA-01 吸收注记 + C10 五条注记、roadmap 8.30 = done（含 E3 裁定注记 + 软依赖解除注记）、batch-proposal 勘误注记完成
- [x] 双门禁退出码 0 登记在案
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 4 项成员全部修复：MA-01（双宿主就绪门禁+空链固化消除，三登记一处闭合[←C4-003]，runOnContext 之外短路）、MA-02（host 层 completeExceptionally future 终结，R21 族纪律，平台零触碰）、C6-001（获取侧配对 close，双宿主 F3 同 diff，恰一次不变式）、C1-001（volatile Credentials 单引用整体替换）
- [x] batch-proposal R2-21 行 exit criteria 增补逐条达成并留证：双宿主启动窗口就绪门禁（空链固化消除）；ContextTaskQueue 异常 completeExceptionally（future 族第 5 例宿主侧）；上传流配对 close+fd 计数回归（C10-011 复用 4.3 TestFileDownloadSinkClose 先例——确定性 close 计数形态）；凭据对 volatile 化
- [x] 对齐纪律守约：MA-01 双宿主同 diff（F3 孪生）兑现；MA-01×C4-004/MA-05 不并族兑现；MA-02×R21 族引用不重开+平台零触碰兑现；§7.1-3 联动兑现（门禁位置+completeExceptionally 兜底可证）；C6-001 双宿主同 diff+C6-008 不并族兑现；C1-001 刷新去重不扩张+CL-004 零触碰兑现；C10 五条同 diff 兑现（勘误 4 全条口径）；PA 零触碰声明闭环（git diff 核对平台设施/nop-http-api/orm/生成物零触碰）
- [x] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红④均有先红后绿或变异/回退论证（E2；挂死类失败模式=超时预算留痕；构造性交错红/当前即红三条标注）；C10 收口门（5 条成员面）同 diff 落地
- [x] 设计文档裁定①–⑤成文且经独立 draft review；既有钉（基线 51 tests + 2499 landed 钉 + 两宿主 TestJsonRpcWebSocketEndpointLifecycle + core-starter 4 类零触碰）零回退实证
- [x] 全量构建绿：E1（含授权支路声明[如有]）+ 批行验证命令 BUILD SUCCESS（E3 双聚合器零盲区；上游环境抖动三联裁定留痕）
- [x] Owner 缺口声明落账完成：module-groups.md 两宿主缺口句 + source-anchors.md HOST-CC-001（E5）
- [x] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0 或批前既有已裁定（逐条登记，真实退出码）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [x] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）门禁/终结/配对/整体替换运行时真实生效（不只类型系统——红绿日志证行为改变），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile` + `./mvnw test`（nop-spring+nop-quarkus 聚合）；checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] cra-index R2-21 四行收敛 + roadmap 8.30 → done（E6）

## Deferred But Adjudicated

### R4-28 束成员（G70 双宿主/反向钉束：MA-03/MA-04/MA-05/C1-003/C1-004/C4-001/C4-002/C4-004/C6-004/C6-005 等 P2 半边 + RP-1~RP-4 反向钉死义务）与平台绝缘面（2.3-CL-018/2.1-C3-001 相邻轨）

- Classification: `out-of-scope improvement`（batch-proposal §二 R4-28 行 :143 + cra-index :454 P2-G70 行批前裁定批外面，非本批降级）；Successor Required: `yes`；Successor Path: `ai-dev/plans/` R4-28 批 plan（软依赖 8.30——本批 done 后解除 8.30 侧软依赖）
- Why Not Blocking Closure: 判级对齐零重复——R4-28 十成员批次列显式（RP-2/RP-4→C4-002、RP-3→C4-004 先于修复义务随行）；本批已收敛 R2-21 单元全部 open P1（4 张）；C1-002/C4-004/C6-008 承接面在本批修复中已登记禁顺手引入接缝（对齐条款与 Non-Goals）。

### C1-001 刷新去重/单飞（多请求并发 401 交叉写）与 CL-004（refresh 无超时/refreshAuthUrl null 重试必败）

- Classification: `optimization candidate`（刷新去重）+ `watch-only residual`（CL-004——审计 C3 轨独立线索在案）；Successor Required: `no`（CL-004 归审计 CL 台账既有轨迹；去重候选留 owner 演进）
- Why Not Blocking Closure: batch exit criteria 仅「凭据对 volatile 化」——撕裂/可见性缺陷已闭合；去重属并发写竞争优化（volatile 单引用后最后写者胜出语义完整、无撕裂），不影响本批缺陷面成立；CL-004 与 C6-007 同文件不同本体（Spring 宿主面，未来宿主侧工单候选）。

## Non-Blocking Follow-ups

- **R-G 治理批 owner doc 建设候选**（nop-spring/nop-quarkus 两宿主 03-modules owner doc——缺口声明落账后，owner doc 建设归 R-G 治理批[roadmap 8.30 Owner Doc 列明文]）。
- **C1-002/MA-05 与 C6-007 同函数演进注记**（R4-28 修复 TL 传播时与 MA-02 已落求值序形态对齐——登记 R4-28 对齐注记去向）。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure Audit Corrections（登记 @ Phase 4）

closure audit（agent_66ecba7d，APPROVED with corrections @ 2026-11-01）四 Major + 六 Minor 处置：
1. **Major-1** Quarkus 孪生钉：设计 §5.5 双宿主同 diff 满足（quarkus-file 结构钉在 2523 已建）。
2. **Major-2** 红②③承载形态从行为钉降级为源码结构钉——裁定修正登记（2522 R2 Major-3 同源方法论）。
3. **Major-3** Quarkus 错误语义变更已回退为既有 error envelope 形态（catch Exception+Throwable 拆通道）。
4. **Major-4** HOST-CC-001 四处行号修正已提交。
Minor×6 登记即闭。

## Closure

Status Note: R2-21 批 4 项 P1（MA-01/MA-02/C6-001/C1-001）全部修复并各带 focused 回归测试；红①④行为红+红②③结构钉（裁定修正登记——行为探针不可判别实证）；设计文档裁定①–⑤经独立评审 R2 闭环（1 Major+5 Minor 修复）；owner doc 五处落点 + HOST-CC-001 锚点；E1 快速通道 + E3 三联 final 全链绿；独立 closure audit APPROVED with corrections——4 Major+6 Minor 全部闭环（Major-3 回退为 error envelope、Major-4 锚点修正、其余登记即闭）；cra-index 四行收敛 + roadmap 8.30 done。无剩余 plan-owned work。
Completed: 2026-11-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent `agent_66ecba7d`（fresh session）@ 2026-11-01，审计基线 `464138f794`
- Audit Session: agent_66ecba7d（复跑 web-starter 27/0/0 + proxy 2/0/0 @ HEAD）
- Evidence:
  - Phase 1 Exit 4/4 PASS（设计文档 189→约 195 行；Draft Review R1[agent_db1779df] + R2[agent_d57ee821] 双轮闭环）
  - Phase 2 Exit 7/7 PASS（四成员逐行核验全中；红证：红①④行为红+红②③结构钉[缺陷基线零缺行]）
  - Phase 3 Exit 5/5 PASS（module-groups.md 宿主集成行 + HOST-CC-001 锚点 + doc-links EXIT=0 + scan-hollow 0 findings）
  - Phase 4 Exit 4/4 PASS（本段 + cra-index 四行 fixed + roadmap 8.30 done + 双门禁）
  - Closure Gates 15/15 PASS（含四 Major 闭环后复验）
  - check-plan-checklist --strict 退出码 0
  - Anti-Hollow：红→绿行为差闭环 + 源码结构钉；scan-hollow EXIT=0 零发现
  - Deferred：R4-28 承接面批前裁定批外零触碰
  - 环境抖动：E3 r1 nop-core flake→solo→r6 EXIT=0 三联留痕
  - audit 发现处置：4 Major 全闭环（M3 回退+M4 修正+M1/M2 登记即闭）；Minor×6 登记即闭

Follow-up:

- no remaining plan-owned work。Non-Blocking Follow-ups（mockwebserver/SMTP 同族/CL-021）均已登记 successor 指向。

## Risks And Rollback

- **裁定①门禁的行为面风险**：filter 入口门禁改变「启动窗口请求直通下游」现状——健康检查/探针类端点若落在未就绪窗口将收到 fail-fast 而非直通（变体 A 的 500 形态已有先例，行为面收窄为类型化错误）；门禁读 `CoreInitialization.isInitialized()` 的判定时序与 plan 2507 fail 恢复路径（parentContainer 回落）的交互若论证不足可能引入新窗口面——设计文档论证表为 Phase 1 放行前置；回滚 = 门禁行单点可逆，红①双钉防形态漂移。
- **裁定②求值序重排的回归面风险**：`task.get()` 求值序重排触碰 withRoutingContext 全部 7 调用点的执行时序——失效面 (c) 已保护面（restQuery/pageQuery 三入口）禁回退为硬门禁；catch Throwable 过宽可能吞 Error 类语义差异（Throwable 对齐队列语义 vs Exception 一次定夺并留论证）；回滚 = try/catch 包裹单点可逆，红②钉防退化。
- **裁定③whenComplete 形态的时序风险**：`res.whenComplete` 注册关闭依赖 future 必完成（校验失败时 future 经引擎错误通道完成——若引擎自身异常未完成 future 则 close 悬挂）——设计文档须论证 future 完成性覆盖所有失败路径（含 uploadAsync 同步抛出段）；UploadRequestBean 归属若裁定触平台叶须走 PA 申报（预设宿主侧包装规避）；回滚 = 配对行单点可逆，红③双钉（失败 close+成功恰一次）防漂移。
- **裁定④兼容形态风险**：legacy setter/getter 若保留为委托形态，`refreshAccessToken` 外部若有未枚举的双写消费方将语义漂移——消费面枚举闭合（audit 失效面 (a)-(f)）为前置；volatile 单引用对「读旧值构造头再被并发刷新覆盖」的合法竞态（最后写者胜出）语义完整——撕裂消除后残余为正常竞态非缺陷（登记注记）；回滚 = holder 内部结构单点可逆，红④钉防漂移。
- **红②挂死红的可复现性风险**：缺陷态为确定性悬挂（stub IContext.runOnContext 内联回调确定性构造，非竞态红），红证稳定；风险在测试自身超时设计（缺陷态断言必超时红——钉的失败模式是超时而非断言失败，须给足但有限 timeout 并在钉内注释失败模式语义[沿 2522 红④先例]）。
- **测试树新建的基建风险**：三模块（proxy/spring-file/quarkus-file）零测试树+零 junit test dep——新建目录与 pom dep 为 build 面动作，若 dep 版本对齐失误将阻塞 E3 全链（Phase 2 前置项先落并单独跑通）；无 @SpringBootTest 依赖声明维持（红钉全部 plain-JVM——引入集成测试基建超范围）。
- **上游链环境噪声风险**：`./mvnw test -pl nop-spring,nop-quarkus -am` 触达上游闭包（nop-core/nop-kernel/nop-commons/nop-xlang 链等），nop-core `TestResourceCacheInvalidationChain` 负载 flake 有 2517–2523 七批先例（勘误 5，本批起草期 r1→solo→r2 三联实录在案）——三联裁定执行并留痕，不得静默重跑或跳过。
