# merge-adjudication — WI 6.5 nop-peripheral 外围支撑抽查并发审计合并裁定

- 裁定人：CRA WI 6.5（nop-peripheral）独立合并裁定子代理（非 C1/C6 初审与深挖人、非 C10 初审人、非三轮复核人）。裁定日期：2026-10-23。
- 裁定输入（只读，未修改）：本目录 C1-shared-mutable-state.md（7 初审 + 1 深挖 + 复核节）、C6-resource-lifecycle.md（3 初审 + 1 深挖 + 复核节）、C10-test-protection.md（6 立案 + watch 5 + CL-301 + 复核节）、dispatch-log.md（Phase 1–5 全记录）、`ai-dev/audits/cra-index.md`（WI 1.1–6.4 台账）、plan 2492、方法论 `ai-dev/skills/deep-audit-prompts.md`「跨报告合并裁定」节（同族相邻按「根因+失效层」三态判定）、先例格式 6.3 merge-adjudication.md。
- 裁定纪律：12 个保留实体逐条三态终裁定（维持含终严重度 / 跨维度调整 / 移交）；CL 线索 10 条逐条终处置；判级终审对照 campaign 判级校准（可具体化时序 + 生产/工具真实面 + 无守卫 + 功能性后果）；不新增发现、不重写实体、不修改 C1/C6/C10/dispatch-log/cra-index/plan/产品代码；每条一句理由并引 file:line 或维度文件节；工单编号 W-01~W-11 为本裁定自建索引（**零填充，与 C10 文件内 watch-only 清单编号 W-1~W-5 属不同命名空间，cra-index 登记以「工单号 + 主发现 ID」双键为准**），cra-index 登记仍以主登记发现编号为准（`nop-peripheral-` 前缀，最终以 roadmap 单元名为准；plan 2492 / 基线 a20ca25130）。
- 裁定前独立复核（裁定人自行执行，非转抄）：cra-index 全文 grep 12 个缺陷站点标识（ProjectFileHelper/NopProjectService/XLangDebugConnector/XLangDebugger/JavaScriptWorker/JavaScriptService/suspendedThreads/SuspendedThread/tempBreakpoint/RESOURCE_STORE/ReportController/DynamicJsLoader/sendStack/muted）**零命中**——零同站点重复立项独立证实；WI 2.1 C4-003 的 F3 缺陷面清单（`C4-thread-pools.md` F3 枚举）明列 `JavaScriptService.java:40`（-1,-1 静默 cached，缺陷面）——CL-203 移交对象证实；future-永不完成族记序第 4 例（4.3 C3-001）/第 5 例（4.4 C6-007）经 cra-index 台账行证实。

---

## §一 裁定输入与实体对账

| 维度文件 | 初审 | 深挖 R2 | 复核保留 | 驳回 | 降级 | 归并结构 |
|---|---|---|---|---|---|---|
| C1 共享可变状态 | 7（P1×1/P2×3/P3×3） | 1（C1-R2-001，P3） | 8 | 0 | 0 | 族主 1（C1-002 吸 CL-001/CL-002/CL-201，§二.2）；同站点主登记 1（C1-004 吸 C6-003，§二.3）；CL-001~004 确认 |
| C6 资源生命周期 | 3（P1×1/P2×2） | 1（C6-R2-001，P3 demo） | 4 | 0 | 0 | 别名让位 1（C6-003→C1-004）；同站点吸并 1（CL-003→C6-002，复核已裁）；CL-201~205 确认 |
| **合计** | **10** | **2** | **12** | **0** | **0** | **同站点归并 1 组 + 族吸收（CL 侧）→ 11 工单** |

- 实体链：初审 10（C1×7 + C6×3，dispatch-log Phase 2 汇总「10 立案 = P1×2 + P2×5 + P3×3」）+ 深挖 2（C1-R2-001 + C6-R2-001，Phase 3 汇总「12 = P1×2 + P2×5 + P3×5」）= **12 立案** → Phase 4 复核 12/12 保留、0 驳回、0 降级（复核修正 3 条注记均为精确化/勘误，非实体增删）→ **12 实体**（C10 矩阵 11 行 = 12 实体、C1-004≡C6-003 按防双计合并 1 行，一一对应）→ 同站点归并 1 组（C6-003→C1-004）→ **11 工单**。
- 实体级严重度（复核终态）：**P1×2 / P2×5 / P3×5 = 12**。
- 工单级严重度（归并后）：**P1×2 / P2×4 / P3×5 = 11**。
- P0：**0**（两轮复核均无 P0 候选；本裁定 §四 终审确认无升 P0；P0 即修通道显式空执行，对齐 campaign 惯例）。
- Protected Area：**零 api 叶**——nop-api-debugger 近失裁定为调试工具模块非契约叶（plan/Phase 1 已核）；idea-plugin 为 IDE 插件宿主面（平台惯例校准非 Protected Area）；**0 张工单触碰 api 叶**。
- CL 线索编号 10 条全部复核确认、0 驳回（C1 段 CL-001~004 + C6 段 CL-201~205 + C10 段 CL-301），不计实体；处置见 §五。
- e2e 零审计面（0f main，Playwright/packages 形态）与 nop-benchmark 不审口径沿 plan 显式登记，不占计数。

## §二 实体三态终裁定（12 条权威表）

三态定义（deep-audit-prompts.md「跨报告合并裁定」）：**同一缺陷/同站点**（取一为主条目，其余交叉引用或别名关闭）/ **同族相邻**（同一根因不同失效层，合并为单一缺陷族工单，修复验证须覆盖各失效面）/ **不同**（保留各自立案）。本轮无「移交」终态实体（P1×2 的「移交 7.1」为通道标注非三态移交）。行序对齐 C10 矩阵行序。

| # | 编号 | 终态 | 终严重度 | 工单 | 通道 | 一句裁定理由 |
|------|------|------|---------|------|------|------------|
| 1 | C1-001 | 维持 | P2 | W-04 | P2 裁决台账 | 交错序列复核独立复算成立（doSuspend L478-480 → monitorNotify L197 清标志 → monitorWait L535 守卫读 false → L549 复位永不执行），且 5 个通知点全部先于 doSuspend 使窗口系统性暴露；维持 P2（后果限调试会话 + 下次真实挂起自愈）。与 C1-006 为「易失标志/交接非原子」同族注记不合并（修复面不同：monitorLock 临界区 vs tempBreakpoint CAS）。 |
| 2 | C1-002 | 维持（族主，吸 CL-001/CL-002/CL-201） | P1 | W-01 | **移交 7.1 P1 台账** | 四要素齐备（时序可具体化 L99-L102 vs L297-315 / 生产真实面 nop-js→DynamicJsLoader 消费链 / 无守卫——搁浅 future 无兜底完成路径 / 功能后果——syncGet 无超时永久阻塞）；同根因簇并族裁定见 §二.2。 |
| 3 | C1-003 | 维持（工单伴生面 CL-204） | P2 | W-05 | P2 裁决台账 | 项目级 Disposable 持进程级注册表注销权错配成立；复核精确化注记（destroy 实际反注册集不含 XPathProvider/EvalExprProvider）随迁修复设计；CL-204（inited 先置半初始化）同文件同 diff 修复批并入工单伴生面（不吸收为同一缺陷）。 |
| 4 | C1-004 | 维持（同站点主登记，吸 C6-003 别名） | P2 | W-03 | P2 裁决台账 | 无 remove 路径两维复核独立 grep 双重复现（全文件仅 L49/L141/L174）；主登记裁定见 §二.3。 |
| 5 | C1-005 | 维持 | P3 | W-07 | 登记级（P3） | EDT 写者链（XLangDebugProcess L78-80）/ReadAction 读者链（L165-168→L123）复核独立核实；窗口窄单次自愈 P3 恰当；同文件兄弟 `XLangDebugProcess.debugger` 已 volatile 反衬守卫遗漏而非惯例。 |
| 6 | C1-006 | 维持 | P3 | W-08 | 登记级（P3） | 读 L485/判/清 L503 三步非原子复核核实；多线程同行汇聚双停、幂等 null 写不阻止；P3（调试语义偏离无数据后果）恰当。与 C1-001 同族注记不合并。 |
| 7 | C1-007 | 维持（demo 降口径） | P3 | W-09 | 登记级（P3 demo） | get-then-put 非原子（L40-46）+ 无 confirm 删除路径（L69 唯一 remove）复核核实；降口径 P3 维持——结构性缺陷立案不升级，符合 plan demo 降口径门槛。 |
| 8 | C1-R2-001 | 维持 | P3 | W-10 | 登记级（P3） | 守卫不对称（sendBreakpointNotice L89/L93-95 局部快照 vs sendStack L116 裸读）复核核实（行号勘误 L92→L89 已落盘）；停机窗口单请求裸 NPE 不可积累，P3 恰当。 |
| 9 | C6-001 | 维持 | P1 | W-02 | **移交 7.1 P1 台账** | 通道级关闭契约（proxy_destroy→SimpleRpcClient.close→SocketClient.close）插件生产面零调用复核全链独立核实（工厂不登记已建 client + 插件零 close 调用 + 双侧 readTimeout/idleTimeout=0）；每会话确定性泄漏 1 fd + Disconnect 钉死 1 recv 线程；autoReconnect 子场景精确化（无限重连僵尸循环）不改核心泄漏主张。 |
| 10 | C6-002 | 维持（吸 CL-003 别名） | P2 | W-06 | P2 裁决台账 | L80 一次性 schedule（非 scheduleAtFixedRate）+ put L567/cleanup L94-103/clear L90 三点复核核实；条目载荷为最后挂起点 EvalRuntime 全量对象图；CL-003 同站点别名关闭防双计（复核已裁，本裁定确认）。 |
| 11 | C6-003 | 跨维度调整（并入 W-03，关闭为别名） | P2（吸收） | W-03 | — | 与 C1-004 同站点同缺陷（ProjectFileHelper.nopVfsPathCaches 静态 Map 项目键无移除），两维复核双向标注移交合并；让位主登记 C1-004（C1 先序 + F3 失效面枚举更宽），其生命周期侧独有表述（CachedValue 失效≠键强引用、弱键/Disposable 双方案、NopProjectService.dispose 追加 remove 最简闭合）随迁 W-03 修复范围。 |
| 12 | C6-R2-001 | 维持（demo 降口径） | P3 | W-11 | 登记级（P3 demo） | 获取点 L67/L85 + 失败删除 L76/L95 返回值忽略 + 一次性 5min 延迟删除 L133-136 复核核实；初审 #24 甄别漏检经 CL-205 记录性修正；降口径 P3 维持。 |

**三态分布：维持 11 / 跨维度调整 1 / 移交 0**（12 实体 → 11 工单，折算 12 − 1 别名 = 11）。

### §二.2 C1-002 族裁定（同根因簇并族：主条目 C1-002，吸收 CL-001/CL-002）

- **三态判定：同族相邻 → 并族为单一缺陷族工单 W-01**，主发现 C1-002（P1），吸收成员 CL-001（P3 级线索）、CL-002（C5/C8 形态线索）；CL-201 为同文件同批伴生修复面随族（不计吸收实体）。族名：**JavaScriptWorker 生命周期健壮性族**。
- **并族依据（根因 + 失效层）**：三条线索共享同一组件（JavaScriptWorker/JavaScriptService 的任务提交-执行-关闭链）与同一后果层（任务搁浅 → future 永不完成 → 调用方 `DynamicJsLoader` 以 `FutureHelper.syncGet` 无超时永久阻塞），根因簇统一表述为「**生命周期路径（关闭/死亡）无拒绝语义 + 队列容量无治理**」：
  - 失效层①（C1-002 本体，语义侧）：关闭窗口 check-then-act——execute 检查 L99 与入队 L102 非原子，worker 排空退出（L297-315）后入队的任务永久搁浅且无异常完成路径；
  - 失效层②（CL-001，路由侧）：worker init 失败即死亡无监督，`invokeAsync` L78-92 按 least-waiting 路由无存活检查——被路由任务同样永久搁浅（复核修正：仅在各活队列非空且其为 least-waiting 时吸入，核心搁浅结论不变）；
  - 失效层③（CL-002，容量侧）：taskQueue 无界 LinkedBlockingQueue（L54）无溢出策略——积压无上界，叠加②成放大结构。
- **不判「同一缺陷」的理由**：三机制修复面不同（execute 复查 + 存量 future 兜底完成 / worker 存活探测 + 故障转移或重建 / 有界队列 + QueueOverflowPolicy），单一修复不能覆盖全部失效面——按方法论「同族相邻合并为单一缺陷族工单，修复验证须同时覆盖各失效面」处置；C1-002 为唯一实体立案故为族主与判级载体（P1）。
- **CL-201 随族注记**：FUNC_REINIT 半状态（先 safeClose 旧 context 再 init，失败后 globals 悬挂）为同文件生命周期健壮性面，与族同 diff 修复批伴生，不计吸收实体、不改 W-01 判级。
- **家族关联（跨单元，不合并）**：future-永不完成族**第 6 例**（已记序第 4 例 = 4.3 nop-network-integration-C3-001、第 5 例 = 4.4 nop-host-C6-007；平台根因绝缘层面移交 WI 2.3 CL-018 + WI 2.1 C3-001 的口径随迁注记）；CL-002 工厂参数校验面本体归 WI 2.1 C4-003（见 §五 CL-203/CL-002 条，使用方↔设施两面非重复立项）。

### §二.3 C1-004 ≡ C6-003 归并裁定（同站点同缺陷）

- **三态判定：同一缺陷（同站点）**——同一载体（`ProjectFileHelper.nopVfsPathCaches` static CHM，L49 声明、L174 computeIfAbsent 唯一写入、全文件无 remove）、同一根因（生命周期键对象无对称注销）、同一修复方向（项目关闭 remove / 弱键 / Disposable 挂钩三选一）。两维复核双向独立 grep 复现并互标移交。
- **主登记：C1-004**（C1 维度先序；F3 失效面枚举更宽——全单元 Project 键静态 Map 唯一性 + `PsiClassHelper.primitiveTypeWrapper` 不可变对照；复核节已按归并方向登记）。**C6-003 关闭为别名**，其独有登记面随迁 W-03：CachedValue 失效（invalidation）不影响键强引用的辨析、IntelliJ 平台弱键/Disposable 惯例依据、`NopProjectService.dispose()` 追加 remove 的最简闭合方案。
- **防双计约束**：cra-index 仅登记 `nop-peripheral-C1-004` 一条（W-03）；C6-003 不单独立台账行；C10-006 缺保护登记沿用同站点合并口径（两维文件与 C10 文件已一致，无需回改）。

## §三 工单级清单（合并裁定后 11 张）

| 工单 | 主发现 | 吸收成员/伴生面 | 判级 | 一句话 | 失效层 | 涉及模块 | C10 前置 | 验证通道 |
|------|--------|----------------|------|--------|--------|----------|----------|----------|
| W-01 | C1-002 | 吸 CL-001、CL-002；伴生 CL-201 | **P1** | JavaScriptWorker 生命周期族：关闭窗口任务搁浅 future 永不完成 + 死亡无监督路由黑洞 + 队列无界积压（future 族第 6 例） | 语义侧（check-then-act 交接）/路由侧（无监督）/容量侧（无界队列）——修复验证须覆盖三层 | nop-frontend-support/nop-js（消费面 nop-web DynamicJsLoader） | C10-001（验收前置） | Maven：`./mvnw test -pl nop-frontend-support/nop-js -am` |
| W-02 | C6-001 | — | **P1** | IDEA 插件调试会话 socket 通道从不关闭（proxy_destroy 零调用）——每会话泄漏 1 fd，Disconnect 钉死 1 recv 线程 | 资源侧（通道级配对缺失，设施使用方面） | nop-dev-tools/nop-idea-plugin（设施契约 nop-rpc-simple，不重审设施本体） | C10-002（验收前置） | **Gradle**（`./gradlew test` + IntelliJ harness 或 plain-JVM connector seam 下沉）——**不得依赖 `./mvnw test`**（CL-301，§六） |
| W-03 | C1-004 | 吸 C6-003（别名） | P2 | ProjectFileHelper 静态 Map 以 Project 为键无移除——关闭项目对象图 + 全量 VFS 路径集永久滞留 | 生命周期侧（注册/注销不对称，静态共享态载体） | nop-dev-tools/nop-idea-plugin | C10-006 | Gradle harness 或 dispose 钩子行为测试（同 CL-301 约束） |
| W-04 | C1-001 | — | P2 | XLangDebugger 挂起交接非原子——resume/step 与断点命中交叠产生幽灵挂起/吞挂起 | 语义侧（monitorLock 交接窗口） | nop-dev-tools/nop-xlang-debugger | C10-003 | Maven：`./mvnw test -pl nop-dev-tools/nop-xlang-debugger -am` |
| W-05 | C1-003 | 伴生 CL-204 | P2 | NopProjectService 以项目级生命周期驱动全局注册表——多项目开窗/关窗交叉销毁 XLang 全局状态 | 生命周期错配（注册作用域 app/project 未分层） | nop-dev-tools/nop-idea-plugin（全局注册表契约 nop-kernel XLangCoreInitializer） | C10-005 | plain-JVM registrar seam（C10-005 建议形态）优先；IntelliJ harness 次选 |
| W-06 | C6-002 | 吸 CL-003（别名） | P2 | suspendedThreads 只进不出：清理为一次性 30s 调度，死线程条目（含 EvalRuntime 全量现场）永不再清 | 资源侧（清理调度失效，F4 形态） | nop-dev-tools/nop-xlang-debugger | C10-004 | Maven（同 W-04；可测性 seam = 注入延迟/周期） |
| W-07 | C1-005 | — | P3 | XLangBreakpointHandler.muted 非 volatile——EDT 写/ReadAction 读过期静默位 | 可见性侧（单字段） | nop-dev-tools/nop-idea-plugin | watch-1（修复同 diff 顺带） | 同 CL-301 约束 |
| W-08 | C1-006 | — | P3 | runToPosition 临时断点检查-清除非原子——多线程同行汇聚一次命令多线程齐停 | 语义侧（一次性标志复合操作） | nop-dev-tools/nop-xlang-debugger | watch-2（修复同 diff 顺带） | Maven |
| W-09 | C1-007 | — | P3（demo 降口径） | DemoServiceBizModel.RESOURCE_STORE 检查-行动非原子双重预留 + 无界只增 | 语义 + 容量侧（demo） | nop-demo/nop-spring-gateway-demo-service | watch-3 | demo 降口径（该子模块无 src/test；修复为 putIfAbsent 一行） |
| W-10 | C1-R2-001 | — | P3 | DebugNotifier.sendStack 裸读非 volatile server 字段——停机窗口断点命中业务线程裸 NPE | 可见性/守卫侧（停机交接） | nop-dev-tools/nop-xlang-debugger | watch-4 | Maven（一行守卫，同文件正确形态可复制） |
| W-11 | C6-R2-001 | — | P3（demo 降口径） | ReportController 临时文件一次性定时删除无失败核验、无重启清扫 | 资源侧（demo 临时文件生命周期） | nop-demo/nop-spring-report-demo | watch-5 | demo 降口径 |

- 工单级分布：**P1×2（W-01/W-02）+ P2×4（W-03~W-06）+ P3×5（W-07~W-11）= 11**。
- 族工单 1 张（W-01 吸收 2 条 CL + 1 伴生）、别名归并 1 组（W-03）、同站点吸并 1 组（W-06）；其余 8 张为独立缺陷工单。
- P1×2 通道 = 移交 7.1 P1 台账（§十）；P2×4 按 cra-index「P2 裁决台账」规则待 7.1 逐项裁决；P3×5 登记级不阻塞。

## §四 判级终审

- **P1×2 维持**：
  - **C1-002（W-01）P1 维持**——判级校准四要素逐项对照成立：① 可具体化时序（execute L99 检查与 L102 入队之间被抢占 × worker L297-315 排空退出的交错，两轮复核独立复算）；② 生产/工具真实面（nop-js 为 nop-frontend-support 生产子模块，消费链 `DynamicJsLoader.transformToSystemJs` 在 nop-web 资源加载路径，非 demo 非测试面）；③ 无守卫（搁浅 future 无任何兜底完成路径，cancelAllTasks 后队列无消费者，经代码核实）；④ 功能性后果（`FutureHelper.syncGet` 无显式超时——FutureHelper.java:158-173 复核核实——调用线程无限阻塞 + 停机期请求线程滞留阻碍优雅停机）。判级一致性：与 1.7 C6-001（Error 逃逸 future 永不完成，P1）、4.4 C6-007（future 族第 5 例，P1）同族同判。触发窗口（停机/缩容 × 在途提交交叠）为真实运维场景而非人工构造时序，P1 恰当。
  - **C6-001（W-02）P1 维持**——泄漏为**会话粒度确定性**（每次调试会话 100% 泄漏 1 fd，非竞态概率事件），Disconnect 场景（debuggee 存活）每次钉死 1 个池线程 + 1 条活连接，双侧 readTimeout=0/idleTimeout=0 无空闲回收复核核实；载体为 IDE 插件工具面，但 IDE 进程长驻、调试为插件主用法，泄漏累积至 IDE 重启不可回收。与 4.4 C6-001（上传流校验失败 fd 泄漏，P1）「每次操作确定性泄漏」同判。维持 P1、不升 P0（见下）。
- **demo 降口径 P3×2 维持**：C1-007（W-09）/C6-R2-001（W-11）均为 demo 模块结构性并发/资源缺陷，按 plan 降口径门槛立案不升级（demo 不按生产标准判级），两轮复核维持，本裁定确认。
- **升 P0 审查：无（显式声明空执行）**。P0 形态（生产主路径成功即泄漏/必然时序/不可自愈数据损坏）在本单元两条 P1 上均不成立：C1-002 需停机/缩容窗口与在途提交交叠（非主路径必然时序）；C6-001 载体为 IDE 工具进程的会话路径（非生产运行时主路径）。两轮维度复核 P0 候选均为「无」，本裁定终审确认——**P0 即修通道零执行，Phase 7 显式空记录即视为完成**。
- 判级修正：0 条（12 实体判级与两轮复核终态逐一相符，无升档无降档）。

## §五 CL 终处置表（10 条全落位）

| CL | 内容一句话 | 复核裁定 | 终处置 | 去向 |
|----|-----------|---------|--------|------|
| CL-001 | JavaScriptWorker 死亡无监督→路由黑洞任务搁浅 | 确认（放大表述修正已落盘） | **并入工单**（族成员·失效层②，§二.2）；F5 看门狗族家族关联随迁 | W-01 |
| CL-002 | taskQueue 无界 LinkedBlockingQueue 无溢出策略 | 确认 | **并入工单**（族成员·失效层③，§二.2）；与 WI 2.1 C4-003 家族关联防双计（工厂校验面本体归 2.1，消费侧无界面归本工单） | W-01 |
| CL-003 | XLangDebugger 清理一次性调度 | 确认（复核裁定并入 C6-002 同站点防双计） | **并入工单**（同站点别名关闭，本裁定确认生效） | W-06（=C6-002 别名） |
| CL-004 | DynamicJsLoader 资源加载路径同步等待 JS transform future（C3/C9 弱信号） | 确认（弱信号维持） | **独立线索·域留档**（C3/C9 域，7.1 聚类候选；调用频率与缓存层级待核；与 W-01 修复联动注记——W-01 修复后其阻塞放大面收敛，但异步链同步化形态本身独立待核） | 7.1 域留档 |
| CL-201 | FUNC_REINIT 半状态：旧 Context 已关闭 init 失败后 globals 悬挂 | 确认（信心可上调确定） | **并入工单**（W-01 同文件同批伴生修复面，§二.2；不计吸收实体） | W-01（伴生） |
| CL-202 | BreakpointManagerImpl.toSourcePath 写共享 SourceLocation | 确认（维持不立案） | **watch-only**（与 C1 载体 5 SourceLocation 行同案；平台 SourceLocation 契约收紧时两处一并复查） | watch |
| CL-203 | JavaScriptService newExecutor("javascript-engine", -1, -1) 参数语义无声明 | 确认（cross-ref） | **移交他单元**：本体归 **WI 2.1 nop-kernel-concurrency-C4-003**（P2，复核保留，其 F3 缺陷面清单明列 `JavaScriptService.java:40`——本裁定独立复核 `C4-thread-pools.md` F3 枚举证实；已知未修复）；本单元 cross-ref 关闭防双计（沿 WI 1.7 C4-003↔2.1 C4-003 同设施同面先例，2.1 merge-adjudication §四·A 行 12） | WI 2.1 C4-003（台账关系确认） |
| CL-204 | NopProjectService.init 先置 inited 再初始化——半初始化固化无重试 | 确认 | **并入工单**（W-05 同文件同 diff 修复批伴生面，不计吸收实体） | W-05（伴生） |
| CL-205 | 初审 #24 临时文件 grep 模式漏检（甄别修正记录） | 确认（记录性条目） | **记录性关闭**（不计缺陷、不计发现统计；漏检属实、进程面/socket 面经复核维持闭合） | 关闭 |
| CL-301 | nop-idea-plugin Gradle 独立构建不入 `./mvnw test` 门禁（campaign 首报） | 确认（构建隔离全链独立核实；WI 4.4 为 nop-host 不同范畴无 cross-ref 已显式核对） | **独立线索·域留档**（构建治理轨，7.1 聚类候选）；附**工单验证通道约束**（§六） | 7.1 治理轨 |

**落位统计：并入工单 5（CL-001/002/201→W-01、CL-003→W-06、CL-204→W-05）+ 移交 1（CL-203→WI 2.1）+ 独立线索 2（CL-004、CL-301）+ watch 1（CL-202）+ 记录性关闭 1（CL-205）= 10。** 确认 10/10、驳回 0。

## §六 C10 缺保护处置

- **C10 缺保护 6 条单列**（campaign 惯例口径；全部 P2 缺保护，覆盖缺陷本体 P1×2 + P2×4）：C10-001（→W-01）/C10-002（→W-02）/C10-003（→W-04，定性经复核证伪修正为「无测试·零可观测」）/C10-004（→W-06）/C10-005（→W-05）/C10-006（→W-03，同站点合并登记口径）。保护力矩阵三态：**会红 0 / 假保护 0 / 无测试 6**（复核修正后权威口径）——全单元对 12 条在案缺陷零钉死力且零检出通道。
- **watch-only 5 条**（W-1~W-5，不计立案）对应 P3/demo 工单 W-07~W-11，补测时机 = 修复同 diff 顺带（C10 文件 watch-only 清单口径经复核逐条核对一致，本裁定确认）。
- **会红=0 序数：campaign 第 13 例**（沿既定链 4.5=第4/5.1=第5/5.2=第6/5.4=第7/5.5=第8/5.6=第9/6.1=第10/6.2=第11/6.3=第12；6.4 零缺陷面盘点形态不计序 → 本单元 6.5=第 13 例）。
- **CL-301 验证通道约束（修复批次显式义务）**：idea-plugin 站点工单 **W-02/W-03/W-05**（及 watch 级 W-07）的回归验证**不得依赖 `./mvnw test`**——nop-idea-plugin 位于 Maven 反应器之外（`nop-dev-tools/pom.xml:20-24` modules 三项不含之，复核独立核实），campaign「`./mvnw test` 通过」门禁对其零约束。验证通道三选一并写入工单验收标准：① `./gradlew test`（IntelliJ Platform test harness，JUnit 4 体系）纳入 CI；② 修复时将可测逻辑下沉 plain-JVM seam（connector 配对/registrar 引用计数——两站点均具备无平台依赖可测性，C10-002/C10-005 建议形态已论证）；③ 最低限度在 cra-index 工单行显式登记「验证通道 = Gradle/harness，Maven 门禁外」。Maven 反应器内工单（W-01/W-04/W-06/W-08/W-10）验证通道不变（`./mvnw test -pl <module> -am`）。
- C10 复核修正的联动确认：C10-003「假保护」→「无测试（零可观测）」证伪修正已落盘于复核节，矩阵格/结论句/统计三处联动，本裁定以其为权威口径，不改 C10 正文（沿「不修改初审正文、以复核节为准」原则）。

## §七 跨单元查重与家族关联（对照 cra-index WI 1.1–6.4，零同站点重复立项）

**查重结论**：cra-index 全文对 12 个缺陷站点标识 grep **零命中**（本裁定独立复核；与两轮 Phase 4 复核「cra-index 与 2026-09 G 系列对四模块零命中、campaign 既有提及均为设施侧不同缺陷面」三方一致）；2026-09 全仓审计 G 系列 13 文件对四模块零提及（复核 grep 证实）。**同站点重复立项 0、跨单元移交入 0、预登记承接 0**（本单元为 campaign 内首报单元，无 G 系列闭环回填项）。家族/同型关联如下（修复方案层复用，不合并工单、零重复计数）：

### #1 future-永不完成族第 6 例（W-01）
已记序：第 4 例 = 4.3 C3-001（OkHttp 吞异常 future 永不完成）、第 5 例 = 4.4 C6-007（ContextTaskQueue 吞异常 future 永不完成）→ **本单元 W-01 = 第 6 例**，机制新变体：非「异常被吞」而是「拒绝语义缺失」（关闭窗口入队成功但消费者已退场）。平台根因绝缘层面注记随迁（4.4 C6-007 口径：移交 WI 2.3 CL-018 + WI 2.1 C3-001 统一_future 兜底完成语义时，本族成员修复方案对齐）。

### #2 socket/fd 泄漏族新成员（W-02）
campaign 既有成员：4.3 C6-001（P0 已修复，下载成功路径 fd）、4.4 C6-001（P1，上传流校验失败 fd）、3.2 C6-001（P1，沙箱孪生子进程 reader/fd）。本单元为**通道级 socket + recv 线程钉死双后果**成员（唯一「泄漏 fd 兼钉死线程」形态）。**SimpleRpcClientFactory 使用面/设施面两侧互补注记**：4.3 MA-07（C1-001，工厂 init 非幂等）与 WI 2.1 CL-024 为设施面缺陷，本单元 W-02 为使用方配对缺失——同设施类不同缺陷面，非重复立项（沿 2.1「使用方↔设施两面」映射模式）。

### #3 IDE 插件项目泄漏族首例（W-03）
campaign 首个 IntelliJ Project 键静态缓存形态（平台惯例违背类：弱键/Disposable 二选一）。与 5.4 C1-002「全局注册表裸写族」、2.4 C2-004「全局实体缓存」为异机制同域（静态容器生命周期治理），家族关联不合并。

### #4 只进不出/F4 清理不对称族（W-06）
家族关联：3.1 MA-03（F4 引擎终态清理族 22+ 站点）、5.1 MA-01（六族 DB 清理配对）、5.5 W-02（append-only 表族）、5.6 W-01（账本泄漏族）。本单元特色 = 清理意图存在但调度形态笔误（一次性 schedule vs scheduleAtFixedRate），修复为一行。单元内同形态 W-03/W-09 为异站点异载体，不合并、并列注记。

### #5 WI 2.1 C4-003 工厂校验面（CL-203 移交 + CL-002 家族关联）
CL-203 站点 `JavaScriptService.java:40` 为 2.1 C4-003 F3 缺陷面成员（明列），本体归 2.1；CL-002（消费侧 taskQueue 无界）归本单元 W-01——使用方↔设施两面切分，防双计不防登记。

### #6 停机窗口拒绝/兜底语义族（W-01/W-02 共同形态注记）
「关闭路径无拒绝语义或兜底」跨单元先例：2.2 C1-009（close 竞态后重建永不关闭连接，P1）、1.7 C6-001（Error 逃逸 future 永不完成，P1）。本单元两 P1 均属该族（W-01 拒绝语义缺失 / W-02 关闭配对缺失），7.1 批次可对齐「生命周期终止语义统一」修复方向。

**其他同型注记（不展开）**：W-04/W-08 check-then-act 复合操作 ↔ campaign C1 广谱形态（单元内同族相邻已注记不合并）；W-05 ↔ 2.2 C1-010（插件 unload 永久泄漏，插件生命周期域）；W-10 停机裸读 ↔ 4.2 C1-001（事件线程原地改写，停机交接可见性域）。

## §八 结构性发现（供 cra-index 计数口径引用）

1. **审计面薄但缺陷集中「双进程调试协作面 + JS 工作线程面」**：全单元 333f/26,887 行立案 12 条，分布 nop-xlang-debugger 4 + nop-idea-plugin 4 + nop-demo 2（降口径）+ nop-js 1（族载体），而 nop-api-debugger（11 文件纯 DTO/接口）与 nop-maven-shaded-plugin（4 文件单线程 Mojo）两子模块经两轮清扫**面闭合零缺陷**。两张 P1 分别落在 JS 引擎停机交接与 IDE 插件 socket 配对——**跨进程/跨线程交接与生命周期配对路径是外围支撑的系统性薄弱带**；e2e 零 Java 审计面显式登记。
2. **idea-plugin Gradle 构建隔离 = 测试门禁盲区（CL-301）**：4 个 idea-plugin 缺陷站点（C6-001/C1-003/C1-004≡C6-003/C1-005）全部游离于 `./mvnw test` 门禁之外，即使补测也不构成 campaign 验证约束（JUnit 4 与全仓 JUnit 5 惯例分叉）——会红=0 的结构性成因之一，修复批次验证通道必须显式声明（§六）。
3. **会红=0 第 13 例且形态纯净**：会红 0/假保护 0/无测试 6——唯一假保护候选（C10-003）经复核逐交错变异重放**证伪**后，全单元对 12 条站点零钉死力且零检出通道；全单元唯一真并发测试（TestPageProviderPermissionsTransform）保护的是历史修复回归面，与 5.5「测试基建只向后看」形态同型；9 个有生产线程协作面的类零测试触及（grep 复核独立复现）。
4. **demo 降口径立案形态 + 甄别自纠教训**：demo 2 条立案均为结构性缺口（防重非原子 + 临时文件生命周期），符合降口径门槛设计；其中 C6-R2-001 的立案源于初审 grep 模式漏检（`createTempFile` 漏 `getTempResource`）的 **R2 自我修正**（CL-205）——降口径单元「只查结构性缺陷」的甄别模式完备性需二次复查，登记为方法论教训（与 campaign 内 R2 甄别修正先例同族）。

**owner doc drift**：四模块（nop-dev-tools/nop-frontend-support/nop-demo/nop-entropy-e2e）**无 owner doc**（roadmap owner 列「—」）——按 plan 治理轨**单元级整体登记**，不逐项立案（沿 4.2/5.4/5.6/6.1/6.2 owner doc drift 先例）；修复落地时若涉及跨模块公共语义（如 XLangCoreInitializer 注册契约），由 7.1 批次同步指定 owner doc 归属。

## §九 数字自洽对账（七步，全部闭合）

1. **实体链闭合**：初审 10（C1×7 + C6×3）+ 深挖 2（C1-R2-001 + C6-R2-001）= **12 立案** → Phase 4 复核 12/12 保留、0 驳回、0 降级 → **12 实体**，实体级 P1×2/P2×5/P3×5。与 dispatch-log Phase 2 汇总（10 = 2+5+3）、Phase 3 汇总（12 = 2+5+5）、两维度文件复核节（C1 8 条 + C6 4 条）三方一致。✓
2. **C10 对账**：缺保护立案 6（P2×6，覆盖 P1×2 + P2×4 保护面）+ watch-only 5 + CL-301 = C10 全部落盘条目 12；矩阵 11 行（12 实体、同站点合并 1 行）三格全填；三态 会红 0/假保护 0/无测试 6（复核修正后权威口径）。C10 复核 6/6 保留 + 1 定性证伪修正（立案加强）+ 簿记勘误（盘点总数 32→40/97、措辞/行号）——不改立案数/判级/矩阵三态计数。✓
3. **归并折算**：12 实体 − 1 别名（C6-003→C1-004）= **11 工单**；工单级 P1 = 2−0 = **2**、P2 = 5−1 = **4**、P3 = 5−0 = **5**（C6-003 为 P2 故 P2 减 1）。✓
4. **CL 对账**：编号 10 条（CL-001~004/201~205/301）确认 10/10、驳回 0；落位 并入工单 5 + 移交 1 + 独立线索 2 + watch 1 + 记录性关闭 1 = **10 全落位**（§五）。CL-205 记录性条目与 CL-202 watch 不计发现统计（与 C6/C10 文件口径一致）。✓
5. **P0 通道**：**0**，即修通道显式空执行（两轮复核无 P0 候选 + 本裁定 §四 终审确认无升 P0）。✓
6. **判级链**：实体级 P1×2/P2×5/P3×5（复核终态）→ 工单级 P1×2/P2×4/P3×5；判级修正 0、升档 0、降档 0。与 Phase 3 汇总及 C10 矩阵判级列逐格相符。✓
7. **dispatch-log 汇总核对**：Phase 1/2/3/4/5 各汇总行复算**无误，本单元无勘误**（对照 6.3 先例的勘误节，本节为零勘误声明）。跨单元查重以本裁定 §七 + 两轮复核现状标注为三方一致权威。✓

**对账总式**：初审 10 + 深挖 2 = 12 实体（复核全保留）→ 归并 1 → **11 张工单 + C10 缺保护 6 条单列 + CL 10 条全落位**。

## §十 通道与后续

1. **P0**：0，即修通道显式空执行（沿 campaign 惯例记录；plan Phase 7 显式空记录即完成）。
2. **P1×2 工单 → 移交 7.1 P1 台账**（主 agent 登记；Finding ID 前缀 `nop-peripheral-`）：
   - `nop-peripheral-C1-002`（W-01，JavaScriptWorker 生命周期族主，吸 CL-001/CL-002/伴生 CL-201；C10-001 验收前置；future 族第 6 例）；
   - `nop-peripheral-C6-001`（W-02；C10-002 验收前置；**验证通道 = Gradle/harness，Maven 门禁外**，CL-301 约束）。
3. **P2×4**：按 cra-index「P2 裁决台账」规则待 7.1 逐项裁决（修 in-scope / deferred + Why Not Blocking）：W-03（C1-004，吸 C6-003；C10-006；验证通道约束）、W-04（C1-001；C10-003）、W-05（C1-003，伴生 CL-204；C10-005；registrar seam 建议）、W-06（C6-002，吸 CL-003；C10-004）。P2 站点若涉 idea-plugin 同受 §六验证通道约束。
4. **P3×5**：登记级不阻塞；W-07~W-11 补测随修复同 diff（C10 watch-only 清单）；W-09/W-11 为 demo 降口径。
5. **CL 处置**：见 §五；CL-301 治理轨线索建议 7.1 与测试基建聚类合议（idea-plugin 测试下沉 plain-JVM seam 的中期方向已登记）。
6. **cra-index 回填待办（主 agent，本裁定不代改）**：① 报告清单新增 WI 6.5 行；② 计数口径块（§十一 文本）；③ P1 台账追加 2 行；④ 新增 WI 6.5 CL 小节。**无 G 系列闭环回填项**（2026-09 G 系列对四模块零命中，无预登记承接）。
7. **closure audit 输入**：本文件 §二/§三 为编号与计数权威表；§九.7 为零勘误声明；C10-003 定性修正以 C10 文件复核节为权威（本裁定 §六 已联动确认）。

---

## 附：cra-index 回填文本（供主 agent 抄录）

### 1. 报告清单 WI 6.5 行

```markdown
| `2026-10/2026-10-23-1100-deep-audit-nop-peripheral-concurrency` | 6.5 | nop-peripheral | C1 C6 抽查 + C10（其余维度零立案，CL 附记形态） | 0 | 2 | 4+5+6（含 C10 缺保护登记） | 5 | done（closure audit PASS，plan 2492；**初审审计阶段收官单元 WI 1.1–6.5 全部完成**） |
```

> 行内计数说明：P1 数 = 工单级 2；P2 数格 = 工单级 P2×4 + 工单级 P3×5 + C10 缺保护 6（沿 6.2/6.3 行格式）；P3 列 = 工单级 5。实体级口径见计数口径块。

### 2. 计数口径块

```markdown
> 6.5 计数口径：合并裁定后 11 张工单（12 实体[初审 10+深挖 2] 复核全保留 0 驳回 0 降级 → 同站点归并 1[C6-003→C1-004 别名，ProjectFileHelper 静态缓存]；维持 11/跨维度调整 1/移交 0；实体级 P1×2/P2×5/P3×5，工单级 **P1×2**——W-01 JavaScriptWorker 生命周期族[C1-002 主，吸 CL-001/002，future 族第 6 例]/W-02 IDEA 插件调试 socket 泄漏[C6-001，每会话 1 fd+Disconnect 钉死 recv 线程]；P2×4/P3×5；demo 降口径 P3×2[W-09/W-11]）；P0=0（即时通道显式空执行）；C10 缺保护 6 条单列（矩阵会红 0/假保护 0/无测试 6——**会红=0 第 13 例**，全单元零钉死力零检出通道；C10-003「假保护」经复核变异重放证伪修正为「无测试」）；CL 编号 10 条[CL-001~004/201~205/301]全落位（并入工单 5/移交 1[CL-203→WI 2.1 C4-003]/独立线索 2[CL-004 C3/C9 域、CL-301 治理轨]/watch 1/记录性关闭 1）；**结构性发现：缺陷集中双进程调试协作面与 JS 工作线程面（api-debugger/maven-shaded 面闭合零缺陷）、idea-plugin Gradle 构建隔离=测试门禁盲区[CL-301，4 站点修复验证不得依赖 ./mvnw test]、会红=0 第 13 例且唯一真并发测试只护历史修复、demo 降口径甄别自纠[R2 grep 漏检自我修正]**；Protected Area 零 api 叶（nop-api-debugger 近失裁定非契约叶）；owner doc drift：四模块无 owner doc 整体登记（治理轨）；家族关联 6 组零同站点重复（future 族第 6 例/socket-fd 族/IDE 插件项目泄漏族首例/F4 清理族/C4-003 使用方↔设施两面/停机拒绝语义族）；跨单元移交 1（CL-203 本体归 WI 2.1 C4-003，cross-ref 防双计）
```

### 3. P1 台账行（2 行）

```markdown
| nop-peripheral-C1-002（W-01，JavaScriptWorker 生命周期族主，吸 CL-001/CL-002，伴生 CL-201） | 6.5 | nop-peripheral | 关闭/死亡路径无拒绝语义：execute 检查-入队非原子 + worker 死亡无监督路由黑洞 + taskQueue 无界——停机/缩容窗口在途调用 future 永不完成，DynamicJsLoader.syncGet 无超时永久阻塞（future 族第 6 例；工厂校验面本体归 WI 2.1 C4-003；C10-001 验收前置） | 待 7.1 | open |
| nop-peripheral-C6-001（W-02） | 6.5 | nop-peripheral | IDEA 插件调试会话 socket 通道从不关闭（proxy_destroy 零调用，工厂不登记已建 client）——每会话泄漏 1 fd，Disconnect 钉死 1 recv 线程（双侧 readTimeout/idleTimeout=0 无空闲回收；修复参照 TestRemoteDebugger.tearDown 配对；C10-002 验收前置；**验证通道=Gradle/harness 非 ./mvnw**[CL-301]） | 待 7.1 | open |
```

### 4. CL 小节

```markdown
### WI 6.5 nop-peripheral（CL-001~004/201~205/301 分段编号 10 条：10/10 确认 0 驳回；归并去向——CL-001/002 并 W-01 族成员、CL-201 W-01 伴生、CL-003 归 W-06 同站点别名、CL-204 并 W-05 同 diff、CL-203 移交 WI 2.1 C4-003[F3 成员明列 JavaScriptService.java:40]、CL-004 独立线索域留档 C3/C9、CL-301 独立线索治理轨[idea-plugin Gradle 门禁隔离，附工单验证通道约束]、CL-202 watch-only、CL-205 记录性关闭；逐条见 merge-adjudication.md §五）
```
