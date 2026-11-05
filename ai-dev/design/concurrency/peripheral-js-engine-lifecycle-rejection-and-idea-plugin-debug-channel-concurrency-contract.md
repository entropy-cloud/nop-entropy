# JS 引擎 worker 生命周期拒绝语义与 IDEA 插件调试通道配对、插件全局注册契约（CRA 8.40 R3-10 设计裁定）

> Status: active（final-state 写法——本文只记录当前生效的设计决策与使用契约；设计评审 R1 修订闭环完成，R2 确认评审见评审记录节）
> Last Reviewed: 2026-11-06
> Source: plan 2533（[2533-cra-phase8.40-r3-10-peripheral.md](../../plans/2533-cra-phase8.40-r3-10-peripheral.md)，CRA Phase 8.40 R3-10 6.5 peripheral 批）；审计目录 `ai-dev/audits/2026-10/2026-10-23-1100-deep-audit-nop-peripheral-concurrency/`（merge-adjudication §二/§三、C1/C6/C10 三维度文件）；批次定义 `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.3 R3-10 + §四.1 CL-301；范式先例 [leiden-timeout-envelope-and-code-graph-self-heal-singleflight-guard-contract.md](leiden-timeout-envelope-and-code-graph-self-heal-singleflight-guard-contract.md)（裁定 + 被否决备选 + 零触碰边界 + 测试缝总表结构）
> Owners: 裁定①（nop-js worker 生命周期域，6.5-W-01 + CL-001/CL-002/CL-201）、裁定②（调试通道配对域，6.5-W-02，CL-301 通道②）、裁定③（插件生命周期域，6.5-W-03 通道③ + 6.5-W-05 registrar 通道② + CL-204）、裁定④（XLangDebugger 挂起交接域，6.5-W-04）、裁定⑤（XLangDebugger 清理周期域，6.5-W-06 + CL-003）、裁定⑥（测试缝总表）

本文是 plan 2533 全部成员修复的唯一设计裁决权威。每条裁定给出：决策（唯一预设默认分支）、理由、被否决的备选、零触碰边界。行号以 live 基线 `6a928f26d7`（分支 `concurrency-audit-remediation`）亲读值为锚——与 plan 2533 起草基线 `bea2be9fb1` 站点零源码漂移（`git diff bea2be9fb1..6a928f26d7 -- nop-frontend-support nop-dev-tools nop-kernel nop-network` 零 diff，本批逐行亲读核对），后续漂移以 live 为准。与 plan 2533 文本不一致的 live 核证事实集中登记于「plan 输入勘正」节，plan 侧回写归 Phase 2 前置。

## plan 输入勘正（live 亲读核证，裁定以本节为准）

| # | plan 2533 表述 | live 事实（file:line） | 对裁定的影响 |
|---|---|---|---|
| K1 | 失效层②「被路由到死 worker 的任务同样永久搁浅」 | 死 worker 的 run finally 恒置 `closed=true`（JavaScriptWorker :310）并排空队列（:311），其 `getWaitingTasks()==0`；invokeAsync 零等待快路径（JavaScriptService :91-94）**优先命中死 worker** → schedule → execute 前置检查（JavaScriptWorker :99-100）同步抛 `ERR_JS_CONTEXT_ALREADY_CLOSED`。死 worker 路由的终态是**同步拒绝**（不搁浅）；index 0 worker 死亡即全部请求同步失败，即便其余 worker 存活——可用性缺陷 | 裁定① 失效层② 的修复目标 = 存活过滤恢复可用性（不是消除搁浅）；搁浅只发生在失效层① 的窗口 |
| K2 | CL-201 站点 FUNC_REINIT「:142-148」 | FUNC_REINIT 分支 = JavaScriptWorker :142-145（:143 safeClose 旧 context、:144 init、:145 else 起）；**该分支从不完成传入的 future**——`invokeAsync("reinit")` 经路由返回的 future 永不完成（future-永不完成族在 W-01 内的第二站点） | 裁定① CL-201 增补 reinit future 显式完成义务；红① 增补 reinit 驱动器 |
| K3 | Major-4：registerXlib 经 `ResourceComponentManager.instance()` 写**全局** modelTypeConfigs，属「同构缺陷面」 | 插件启动时 NopAppListener :50 以 `ProjectResourceComponentManager` 替换全局实例（ResourceComponentManager :71-79 `_instance`/`registerInstance`）；其 `registerComponentModelConfig`（ProjectResourceComponentManager :33-34）委托 `NopProjectService.get().getComponentManager()`（:28-29）——即 **per-project** 的 `ResourceComponentManager(false)`（NopProjectService :27）。registerXlib（:80-88，在 `ProjectEnv.withProject` 作用域内）写入的是**本项目**组件管理器，:226 Guard 在两项目间不碰撞；XLangCoreInitializer.registerXDef（:71-82）同经此委托链落 per-project。**前提条件（R1 m4b）**：K3 委托链以 NopAppListener :50 已注册 ProjectResourceComponentManager 为前提——生产路径由插件启动注册；harness 中 BaseXLangPluginTestCase :86 手工调用——**红⑥ harness setUp 必须先照此注册**，否则委托链不成立、per-project 判定失效 | 裁定③：registerXlib 与 registerXDef 不进入 registrar 引用计数集（per-project 委托本就正确配对）；registerXlib 纳入 W-05 覆盖集的形态 = CL-204 失败序覆盖。**原 per-project 委托表述恢复为准，「同构缺陷面」表述废止** |
| K4 | CL-204 修复「inited 置位时机后移」可选 | init 内的 registerXDef/registerXlib 经上述委托链**重入** `NopProjectService.get()` → `init(project)`（:66-67，synchronized 可重入同线程）；现形态依赖 :48 先置 inited 截断重入。单纯后移置位 → 重入时 inited 仍为 false → 递归再入 init → 无界递归 | 裁定③ CL-204 采三态（初始化中/就绪/失败）唯一形态；「置位后移」列为被否决备选 |
| K5 | TestRemoteDebugger.tearDown（:140-146）为「动作齐全零断言的唯一配对参照」、「顺序先关通道后关池」 | live tearDown = :141-147（@AfterEach :141）；顺序为 `clientFactory.destroy()`（:143）→ `server.stop()`（:144）→ `DestroyHelper.safeDestroy(debugger)`（:145）→ `server.stop()`（:146）。代理实现接口集 = `IDebuggerAsync` + `IRpcProxy`（SimpleRpcClientFactory :185），二者均非 `IDestroyable`/`AutoCloseable`（IDebuggerAsync→IDebugger→IBreakpointManager 继承链无关闭接口），DestroyHelper :20-35 对其**无动作**——tearDown 既非先通道后池，也未关闭通道 | 裁定②：配对参照改为平台通道契约本体（`IRpcProxy.proxy_destroy` → RpcInvocationHandler :61-63 → SimpleRpcClient.close :113-114 → SocketClient.close :274-277）；tearDown 零触碰并登记测试债 |
| K6 | 红①「概率 loop-捕获（提交轮次放大使命中可靠）」 | 调用方窗口 = execute :99 读 closed 与 :102 入队之间（一次 volatile 读 + 入队，纳秒级）；搁浅要求调用方恰在此间隙被抢占并跨越 worker 整段终局序列（:310-314）——提交轮次放大无法使命中可靠 | 裁定⑥：红①(a)(c) 改为**闩控确定性**驱动（包内队列注入构造缝），服务层循环提交降为冒烟 |
| K7 | Major-3「doStop worker close 循环 :138-141 中断链不劣化」 | doStop close 循环 = JavaScriptService :137-141；`close()`（JavaScriptWorker :109-119）为置位 `closed` + 队列直送 pill（:115-118），无任何线程中断语义；executor.destroy（:142）= shutdown 不中断 | 「不劣化」的契约含义改写为：close 永不因容量阻塞或抛错、pill 永不经 execute 准入 |
| K8 | 裁定④ 纪元号「挂起代次 counter 每次 doSuspend 递增」 | doSuspend（XLangDebugger :472-482）位于 notifier 回调（如 :466）**之后**；在 doSuspend 内递增的代次无法区分「回调期间到达的 resume」与「上一停点的 resume」 | 裁定④：纪元由 resume 侧推进、快照在停点**宣告前**取得（唯一可绿形态，见裁定④.1） |
| K9 | refreshConfig 扩容分支与新建 worker「拒绝语义同覆盖」 | 扩容分支 :107-115 存在独立的既有缺陷：:112 `workers[i] = worker` 写入**旧数组**（i ≥ 旧长度 → ArrayIndexOutOfBoundsException），新数组对应槽位留 null；:111 直接 `new JavaScriptWorker()` 绕过 newWorker（:127-132）的 initScriptPath/jsLibLoader 装配 | 不在 R3-10 finding 清单（6.5 C10 §七 仅登记收缩路径零测试）；本批零触碰，登记为批外新发现（见「批外新发现登记」节） |

## 裁定① W-01：JavaScriptWorker 生命周期拒绝语义（三失效层 + CL-201，P1）

**决策**：

1. **拒绝通道契约（R21 纪律落点）**：worker 级提交的拒绝分两条且仅两条通道——①**准入拒绝 = execute 同步抛 NopException**（既有通道 :99-100 维持：调用方在 schedule/invokeAsync 调用点即得异常，DynamicJsLoader :129-131 的 syncGet 不会被调用）；②**已入队后取消 = future 以 `CancellationException` 异常完成**（既有通道 invokeFunction :139-141 维持：L310 置位先于 L311 排空，排空路径任务全部经此完成）。本批修复的全部新增拒绝点均落这两条通道之一，禁止第三种形态（静默丢弃、返回已完成 future、永久挂起）。
2. **失效层①（关闭窗口 check-then-act）= execute 入队后复查 + 复查失败通路含同引用队列移除**（唯一预设）：execute 的序列契约为「前置检查 closed → 容量准入（第 4 条）→ 入队 → 复查 closed」；复查为真时以**同一任务引用**从队列移除：移除成功 → 该任务从未对消费方可见，按通道① 同步抛 `ERR_JS_CONTEXT_ALREADY_CLOSED`（与前置检查同码同语义，调用方不可区分两者——有意为之）；移除失败 → 任务已被消费方取走（run 循环或终局排空），由通道② 交付（invokeFunction 读字段 closed 为真 → CancellationException；或先于关闭被正常执行 → 正常完成），execute 正常返回。移除按引用身份判定：任务为 schedule 内新建的 lambda 实例，`LinkedBlockingQueue.remove(Object)` 的 equals 退化为 Object 身份比较——满足同引用 identity 约束（R1 Major-2）。
   **并发完备性论证**：worker 的全部退出路径（close pill、graceful 空闲退出 :298-300、init 失败、InterruptedException :303-305）都经 finally 的「L310 `closed=true` → L311 排空」。调用方「入队 → 读 closed」与 worker「写 closed → 排空 poll」构成 Dekker 对：若调用方复查读到 false，则其入队（LinkedBlockingQueue count 原子更新）在同步序中先于该读、该读先于 worker 的 L310 写、L310 写先于排空的 poll——排空必见该任务并经通道② 完成；若复查读到 true，则移除成功者走通道①、移除失败者已被消费方持有走通道②。两分支覆盖全部交错，**无残窗**；红①(c)「终局后队列为空」由「移除成功即出队」保证可达。
   **schedule lambda 形参 `closed` 不激活**：run 循环 :289 传入的是同一字段的读值、cancelAllTasks :332 传入恒 true，与 invokeFunction 读字段等价；激活形参只会制造两个权威源，零语义增益。
3. **失效层②（死 worker 路由）= invokeAsync 存活过滤**：零等待快路径与 least-waiting 选择两处候选集均排除 `isClosed()` 为真的 worker；候选全部关闭 → 同步抛 `ERR_JS_CONTEXT_ALREADY_CLOSED`（复用既有码：服务停止与全部 worker 死亡对调用方不可区分且处置相同——均为「无可用 JS 执行环境」，新码零可行动增益）。过滤与 execute 之间 worker 恰好关闭的竞态由第 2 条的 execute 前置/复查兜住，落通道① 单次同步拒绝，不重试其他 worker（罕见竞态，单次尝试保持 invokeAsync 语义简单；可观测、非搁浅）。嵌套调用路径（:81-84 getCurrentWorker 直调）零改动。**worker 自动重建不入本批**（涉 executor 生命周期与 init 确定性失败下的重启风暴治理，归 plan Non-Blocking Follow-up）。
4. **失效层③（taskQueue 无界）= execute 准入时软上界 + 拒绝即异常，物理队列维持 JDK 无界 LinkedBlockingQueue（:54）**：每 worker 等待任务上界缺省 1000（JavaScriptWorker 公共常量，测试与文档可直接引用；配置键化归 plan Non-Blocking Follow-up），准入检查读 `size()`，达界 → 通道① 同步抛**新码** `ERR_JS_WORKER_QUEUE_FULL`（`nop.err.js.worker-queue-full`，英文消息，参数 `ARG_MAX_WAITING_TASKS`（JsErrors 新增 ARG 常量，命名随既有 `ARG_*` 惯例）携带上界值；落手写保留文件 JsErrors.java :14-19 命名空间，grep 零重名——R1 m7 核证）。软上界的越界幅度至多为并发提交者数——上界的目的是封住失控异步提交者的无界积压，非精确配额。缺省值论证：唯一生产消费链 DynamicJsLoader 为同步等待（syncGet 阻塞请求线程），每 worker 在途量受调用方线程数约束，1000 远高于正常量级（TestJavaScriptService 5 测试均为单在途），正常负载零误拒。
   **close pill 绕过容量界限（R1 Major-3）**：close（:109-119）的 pill 维持队列直送、**永不经 execute 准入**；由于界限落在准入检查而非物理队列容量，pill 入队永不阻塞、永不因满抛错——doStop close 循环（JavaScriptService :137-141）的「置位 + pill 唤醒 poll」语义零劣化（K7）。
   **doStart 池工厂契约零触碰红线**：JavaScriptService :40-56（plan 2497 显式 cached 形态 :44-48）与 `TestJavaScriptServiceExecutorContract` 断言零 diff——容量治理只收敛于 worker 队列侧。
5. **CL-201 FUNC_REINIT = build-then-swap + reinit future 显式完成 + 嵌套拒绝**：重建原子单元 = context 与 globals 及其构建副产物（createContext :339-358 写入的 fileSystem/outStream/errStream 字段）——先完整构建新单元（新 context + 初始化脚本求值成功），成功后一次性换绑全部字段并关闭旧 context，future 正常完成（null）；构建失败 → 关闭半成品新 context、旧单元全部字段保持不变且可继续服务、future 以失败原因异常完成。run 启动时的首次 init（:281）不是换绑场景，维持原语义（失败即 worker 死亡 → 失效层② 过滤接管）。**嵌套路径拒绝（R1 m6）**：invokeAsync 嵌套分支（:82-84 getCurrentWorker 直调）遇 `FUNC_REINIT` 时不再进入 invokeFunction 重建分支——返回异常完成的 future（`IllegalStateException`，英文消息 "FUNC_REINIT cannot be invoked from within a running JS task"）——重建只能在空闲任务边界以排队任务形态执行，JS 回调内重建必关本线程 entered 的 context（safeClose 吞错 → 旧 context 泄漏）；**reinitWorkers 容错（R1 m6）**：reinitWorkers（:58-63）循环体逐 worker 捕获同步拒绝（已关闭 worker 的 execute 抛 `ERR_JS_CONTEXT_ALREADY_CLOSED`）WARN 后继续，不再任由首个异常中止循环致存活 worker 漏重建。红①(f) 驱动器随此扩展（见裁定⑥）。
6. **refreshConfig 覆盖声明**：收缩分支（:116-124，shutdownGracefully :122）的退出经同一 finally 终局序列，第 2 条复查天然覆盖；扩容分支新建的 worker 为同一类，execute 契约自动覆盖。扩容分支的既有数组写入缺陷（K9/N1）不入本批。

**被否决的备选**：
- **关闭侧终局对账循环（独立形态）**：终局排空无论循环几轮，都无法观测到「排空结束之后」才入队的任务——只有调用方侧复查能闭合窗口；对账循环只缩窄不闭合，且叠加复查后无增量。
- **锁化 execute 与终局序列（单锁临界区）**：在热路径引入锁竞争，且终局序列含 safeClose（GraalVM context 关闭，毫秒级）——锁内阻塞所有提交者；Dekker 复查无锁即闭合。
- **平台 `IBlockingQueue`/`OverflowBlockingQueue` + `QueueOverflowPolicy`**：IBlockingQueue 接口无按元素移除（`nop-kernel/nop-commons/.../IBlockingQueue.java` 方法集 offer/poll/send/size/clear 等），第 2 条的同引用移除不可表达，须改平台设施本体（条件申报③，禁选）；DROP_NEWEST/DROP_ELDEST 静默丢弃任务 = future 搁浅（违 R21）；BLOCK_WAIT 阻塞提交者，pill 亦被阻塞——stop 挂死；ERROR 语义即本裁定第 4 条，不需引入该设施。
- **物理有界 `LinkedBlockingQueue(capacity)`**：满时 pill 的 add 抛 IllegalStateException、offer 丢 pill——close 语义被容量反噬（Major-3 正面冲突）；为 pill 开专用通道需改 run 循环消费结构，复杂度高于准入软上界。
- **准入拒绝改为返回异常完成的 future**：改变既有 execute/invokeAsync 同步抛错契约（调用方 catch 面变更），且与既有前置检查通道分裂。
- **全死时新码 `ERR_JS_NO_AVAILABLE_WORKER`**：见第 3 条，零可行动增益。
- **CL-201 先关后建（现状）**、**失败时 worker 自杀**：前者即缺陷；后者把可恢复的脚本错误放大为容量损失。

**零触碰边界**：doStart（:40-56）与 ExecutorContract 钉零 diff；invokeFunction 字段检查通路（:139-141）、asyncReturn/complete/reject（:161-224）、run 循环消费结构（:283-308）、cancelAllTasks（:325-337）零行为改动；nop-web DynamicJsLoader（:127-131）与 nop-api-core FutureHelper.syncGet（:158 起）零触碰——修复只在 future 生产侧收敛，不改消费侧等待语义；refreshConfig 扩容分支零触碰（K9）。

## 裁定② W-02：调试 socket 通道配对关闭（CL-301 通道②，P1）

**决策**：

1. **connector 物理下沉 nop-xlang-debugger，包 `io.nop.xlang.debugger`，类名不变**（唯一预设，CL-301 通道②）：可行性三证——①XLangDebugConnector import 全集（:10-16）= nop-api-core/nop-api-debugger/nop-commons/nop-rpc-simple/nop-socket，零 IntelliJ 平台 API；②nop-xlang-debugger pom 已依赖 nop-xlang/nop-api-debugger/nop-rpc-simple（pom :17/:22/:27），sink 零新依赖边；③插件经既有坐标 `nop-xlang-debugger:2.0.0-SNAPSHOT`（build.gradle.kts :30）可见。插件侧 diff = XLangDebugProcess **新增 1 条 import**（现同包无 import——package :8），:62/:73/:131/:132/:159 引用点零改动；原文件从插件删除（迁移非复制，防双源）；XLangDebugContextListener :21 javadoc 纯文本提及零改动。字节码版本：插件已在消费 release 25 编译的 nop-xlang-debugger/nop-xlang 产物（根 pom :25-30），sink 不新增版本约束。**模块边界论证**：connector 是「调试协议客户端」，与 nop-xlang-debugger 的「调试协议服务端」（XLangDebugger 经 SimpleRpcServer 暴露）同属调试协议域，下沉落在协议所属模块，不构成越界。
2. **connector 持有通道代理**：connect 成功返回的代理由 connector 持有（单会话单代理——插件 connect 循环 :127-148 仅在成功时返回一次；失败尝试在 SocketClient.connect :121-123 内已关闭半开 socket 且 recv 未启动，零残留）。同一 connector 已持有活代理时再次 connect → fail-fast（编程错误显式化，与 SimpleRpcClientFactory destroy 后 newInstance fail-fast :169-175 同纪律）；destroy 之后 connect → 经工厂既有 fail-fast 抛出。
2b. **connector 级并发契约（connect×destroy 串行化，R1 M2）**：stop（EDT）与 connect（后台 Task.Backgroundable）并发，destroy 可落在 `newInstance` 越过 destroyed 检查之后、代理存入之前——connector 以单一监视器锁把 connect() 与 destroy() 串行化（两方法均为会话级低频调用，无热路径顾虑；**已接受语义（R2 watch-12 登记）**：锁覆盖 newInstance 内 `client.connect()`（ClientConfig connectTimeout 缺省 5000ms）——destroy 与在途 connect 并发时 destroy 至多等待一个 connectTimeout，有界；stop 运行于 EDT 时为有界 UI 卡顿，接受）。锁内序：connect = 工厂 `newInstance()`（destroyed 后经工厂 fail-fast :170-175 抛出）→ 存代理 → 返回；destroy = 取当前持有代理 → 非空则 `proxy_destroy`（通道半，见第 3 条）→ **try/finally 保证 `clientFactory.destroy()`（池半）必然执行**。**幂等定义（否决早退标志形态）**：幂等 = 「重复 destroy 无副作用」由工厂 destroy 幂等（destroyed 置位 + timer/executor destroy 幂等）与「代理已关闭再 proxy_destroy 无害」共同承载——**不采「destroyed 标志早退」**：早退会让「destroy 先于代理存入」竞态下后到的代理永不关闭（socket + recv 线程双泄漏残窗，评审 M2 正面案例）。
3. **destroy 配对序 = 先通道后池，幂等**：`IRpcProxy.proxy_destroy()`（RpcInvocationHandler :61-63 → DestroyHelper → SimpleRpcClient.close :113-114 → SocketClient.close :274-277：置 closed + 关 socket + 取消心跳）→ `clientFactory.destroy()`（:148-159，executor/timer shutdown）。顺序敏感论证：recv 线程阻塞于 socket read（readTimeout=0 :28，不响应中断），executor shutdown 不中断在途任务——只有关闭 socket 才能使 read 抛错、recv 循环（SocketClient :224-232）退出、线程归还后被 shutdown 回收；先关池则 recv 线程在已 shutdown 的池中继续钉死。proxy_destroy 的异常由 DestroyHelper 吞并记录，不阻断后续工厂销毁（关闭路径尽力而为）；重复 destroy 无副作用。
4. **重连终止语义**：proxy_destroy 置 SocketClient `closed=true` 后，recv 失败分支的 `if (!closed)`（:205）与重试体内 `if (closed)`（:208）均短路——autoReconnect（ClientConfig :28 缺省 true + connector reconnectPolicy :31-36）循环在 destroy 后停止，消除「debuggee 被杀 → 执行器已 shutdown 语境下的无限重连」僵尸面。**平台残余（零触碰登记，watch-only）**：重试体已越过 :208 检查、reconnect 已投递 executor 时恰逢 close，`reconnect()`（:139-142）不复查 closed，可在 close 之后重开一个 socket——属 nop-socket 平台站点，窗口为单次重试调度间隙，本批不改平台本体（条件申报③）。
5. **TestRemoteDebugger.tearDown 零触碰**（K5）：其 safeDestroy(代理) 为无动作、顺序为先池后通道；改写它不是 C10-002 的验收载体（红② 承载配对行为），且改动会扰动 111 基线钉的 tearDown 形态。登记测试债（通道未关闭 + 重复 server.stop）至 plan Non-Blocking Follow-up「tearDown 断言形态升级」条目。
6. **插件侧接线时机维持**：XLangDebugProcess 在 connect 成功后注册 `connector::destroy`（:132），stop（:254-261）经 cleanup.cancel 触发。**stop×connect 并发窗闭合（R1 M2 订正）**：Cancellable.appendOnCancel 在已取消后追加会**立即执行**回调（:63-72 append + :107-123 tryRunCallbacks[R2 watch-14 行号订正]）——stop 先到达时，后成功的 connect 注册 destroy 即被补跑，叠加第 2b 条 connector 内部串行化（destroy 竞态下后到代理由 destroy 的锁内取代理兜住），stop 与 connect 任意交错 destroy 均必达，**插件会话编排面无泄漏窗**。真实残窗（批外新发现 N2，见登记节）= connect 从未成功即退出重试循环（debuggee 已终止 `isProcessTerminated` 或任务取消 `checkCanceled` 抛出）时 `connector::destroy` 从未登记，而首轮 `newInstance` 已经 init 建出工厂 timer/executor（SimpleRpcClientFactory :176 init 先于 :188 connect）——该窗属插件会话编排面，不在 C6-001 缺陷描述内，本批零改动。

**被否决的备选**：
- **通道③（不下沉，Gradle harness 登记）**：可测逻辑零平台依赖，下沉成本仅 1 import；通道③ 会把 P1 工单的验收推到本环境不可执行面。**回退预案（仅当评审认定下沉破坏模块边界）**：W-02 改走通道③ + 设计文档登记验证盲区，红② 驱动器形态不变（对已下沉/未下沉的 connector 同构）。
- **下沉到 nop-rpc-simple/nop-commons 等平台模块**：调试专用客户端不属平台通用设施，且触条件申报③。
- **destroy 先池后通道（TestRemoteDebugger 现序）**：见第 3 条顺序论证。
- **destroy 幂等采「destroyed 标志早退」形态**：connect×destroy 竞态下（destroy 越过检查时代理尚未存入），后到代理永不关闭——socket + recv 线程双泄漏残窗（评审 M2）；第 2b 条的锁内取代理 + try/finally 形态无此残窗。
- **改 readTimeout 为非零以借超时退出 recv**：调试会话为长空闲连接，读超时会触发 recv 失败 → autoReconnect 抖动，属行为变更且不关闭 fd。
- **逐会话代理注册表（多代理）**：插件为单会话单连接，注册表无使用面。

**零触碰边界**：SimpleRpcClientFactory/SimpleRpcClient/SocketClient/RpcInvocationHandler/ClientConfig（nop-rpc-simple、nop-rpc-core、nop-socket）零 diff；nop-api-debugger 全部接口（IDebuggerAsync 等——近失裁定非契约叶）零 diff；XLangDebugProcess 除 1 条 import 外零 diff；XLangBreakpointHandler（W-07 :42 watch 面）零触碰。

## 裁定③ W-03 / W-05：插件项目级生命周期与全局注册契约（CL-301 通道③/通道②，P2）

### ③-A W-03 ProjectFileHelper 静态缓存配对移除（通道③）

**决策**：

1. **移除挂点 = 插入点登记、以插件项目级服务为父 Disposable**（唯一预设）：`nopVfsPathCaches`（:49）的唯一写入 computeIfAbsent（:174-175）在**为某 Project 首次建条目时**，同时以该项目的 NopProjectService 实例（plugin.xml :93 `projectService`，Disposable :26）为父登记一个子 Disposable，其动作 = `caches.remove(project, value)`（键值双参，仅移除本条目）。Project 引用由该子 Disposable 捕获（R1 Minor-⑤ 的「捕获 Project」由插入点闭包承载，NopProjectService 无需新增 Project 字段，W-03 的产品 diff 限 ProjectFileHelper 单文件）。父选 NopProjectService 而非 Project 本身：IntelliJ 平台约定插件可卸载性要求插件 Disposable 以插件自有服务为父——项目关闭与插件卸载两路径都会触发移除。**服务获取与登记的落点细则（R1 M4）**：
   - **服务获取** = `project.getService(NopProjectService.class)`（惰性获取接口形态），**禁止 `NopProjectService.get()`**——后者在 `ProjectEnv` 作用域之外（如 NopVirtualFileReference.getVariants 的调用栈）抛 IllegalStateException 且会触发服务 init，会使补全功能失效；
   - **登记时机在 `computeIfAbsent` 映射函数之外**：映射函数只负责建值；进入前取 `previous = caches.get(project)`，computeIfAbsent 返回后 `value != previous` 即「本次新建」（两线程并发首建时两边都会登记子 Disposable——`remove(k, v)` 键值双参幂等，良性重复[R2 watch-13 登记]）——此时才执行 `Disposer.tryRegister(service, () -> caches.remove(project, value))`。**禁止在映射函数内登记**：若父服务正在/已经 dispose，子 Disposable 的动作会在 CHM compute 期间对同键执行 remove（"Recursive update" 异常），且向已 dispose 父对象 register 抛 IncorrectOperationException；
   - **关闭竞态以 tryRegister 失败回滚闭合（替代预检查）**：`tryRegister` 返回 false（父已 dispose）→ 立即 `caches.remove(project, value)` 并返回本次一次性计算结果——不设「先检查 disposed 再建条目」的预检查（检查与登记之间存在竞态窗口，回滚形态对 pre-disposed 与并发 dispose 两态同一处理，无窗口）。
2. **覆盖性论证**：每个条目都由其插入动作自带移除登记——与「哪个调用方先请求了 NopProjectService」无关。对比 dispose 追加 remove 形态：NopProjectService 为惰性服务，仅经 get()（:60-69）实例化；`getCachedNopVfsPaths`（:140-146）的调用方（NopVirtualFileReference :75、ProjectFileHelper :120/:125）不经 get()——存在「条目已建、服务从未实例化、dispose 永不发生」的结构缺口。
3. **验证通道 = 通道③（工单行登记文本）**：「6.5-W-03 验证通道 = Gradle/IntelliJ Platform harness（JUnit 4，`nop-dev-tools/nop-idea-plugin` Gradle 独立构建），Maven 门禁外；本环境不执行，harness 测试文件随批写入留痕；Maven 侧以变异/回退论证承载 E2 或分支」。harness 形态见裁定⑥ 红⑥。

**被否决的备选**：
- **NopProjectService.dispose 追加 remove（plan 预设、C6-003 最简闭合）**：第 2 条结构缺口；且须为 dispose 新增 Project 字段捕获。
- **弱键（WeakHashMap 或同类）**：CachedValue 由 `CachedValuesManager.getManager(p).createCachedValue(...)` 创建，其提供者闭包强引用 p（:175-185）——值强引用键，弱键永不回收（WeakHashMap 经典泄漏形态）；CachedValue 的依赖失效（:177-180）只使值重算，不解除键引用。
- **改存 Project UserData（取消静态 Map）**：需改公开静态泛型方法 getCachedSearchResult（:170）签名形态，diff 大于插入点登记且无额外覆盖收益。
- **以 Project 为父 Disposable**：违反插件可卸载约定（插件卸载后子 Disposable 仍挂在 Project 上引用插件类）。

**零触碰边界**：findAllNopVfsPaths（:147-167）搜索逻辑、CachedValue 依赖集（:177-180）零改动；getCachedSearchResult 公开签名零改动。

### ③-B W-05 XLangCoreInitializer 全局设施引用计数 registrar（通道②）

**决策**：

1. **实际跨项目反注册集（live 亲读，覆盖集以此为准）**：XLangCoreInitializer.initialize（:46-65）/destroy（:85-88）中，经 destroy 反注册且为**进程级全局**的 = EvalGlobalRegistry 三组静态函数（:47-49；registerFunction 以 put 覆盖、以 `remove(name, func)` 撤销——func 为 ReflectionManager 类模型缓存实例，两项目注册的是**同一实例**，任一项目撤销即删掉所有项目的注册）、ValueResolverCompilerRegistry `expr`（:53-56，同一常量 `ExprValueResolver.COMPILER`，remove(type, resolver) 同理）、ScriptCompilerRegistry `java`（:62/:86，JaninoScriptCompiler 单例 INSTANCE）。注册不反注册（覆盖式、幂等、不随 destroy 撤销）= XPathProvider（:51）与 EvalExprProvider 三项（:58-60）——复核精确化注记成立。**per-project（经 K3 委托链）** = registerXDef（:71-82）与 NopProjectService.registerXlib（:80-88）——这两项由各项目 cleanup 正确配对，**不进入引用计数集**。
2. **registrar 形态 = 进程级单例，引用计数只门控销毁，不门控初始化**（唯一预设）：每个项目照常执行一次自己的 XLangCoreInitializer.initialize——全局部分为同实例幂等覆盖（无害），per-project 部分（registerXDef）**必须**每项目执行（否则后到项目的组件管理器缺 xdef 模型配置）。registrar 在初始化成功后记一个持有者；持有者释放时计数减一；**计数归零时按获取逆序对全部保留的初始化器逐个执行 destroy**（R1 m4 修订——非仅执行最后一份）：`remove(name, func)` 按实例匹配，ReflectionManager 的 `introspectCache`（:117/:185-190，maximumSize + weakKeys、可清空）被淘汰/清空后两项目可能拿到**不同**函数实例——仅 destroy 最后一份会留下先到项目实例的残余注册；逐份逆序 destroy 在同实例前提下幂等无害、在异实例前提下精确撤销。失败释放的孤儿清理同理：唯一持有者初始化失败时 destroy 本次初始化器一份（防孤儿全局注册），存在其他持有者时仅丢弃本次初始化器不 destroy（跨项目销毁即缺陷复现）。持有者凭单次有效的持有凭据释放，重复释放无效（防 Disposer 重入导致二次扣减）。registrar 的抽象面 = 平台既有 `ICoreInitializer` 使用面（XLangCoreInitializer :37 实现之），测试以计数替身实现同一接口；registrar 全部操作串行化（低频，项目开/关粒度）。
2b. **registrar API 契约（R1 M3 钉死——插件侧调用在本环境无 Gradle 编译验证，API 必须落档）**：类名 `SharedCoreInitializerRegistrar`，包 `io.nop.xlang.debugger`（与下沉 connector 同包）；方法签名 `ICancellable acquire(ICoreInitializer init)`——语义 = **同步执行** `init.initialize()`（在调用方线程，NopProjectService.init 调用形态不变）：初始化成功 → 持有者计数 +1 并返回单次有效凭据（凭据释放 = 计数 −1，重复释放无效）；初始化失败 → 不计数、原异常上抛（调用方按 CL-204 三态处理）。生产访问 = `static instance()` 进程级单例；**包内可见构造器**供测试使用——红⑤ 每个用例 new 独立实例，用例间零共享状态（静态单例共享计数会使断言互相污染）。**作用域契约**：`acquire` 须在 `ProjectEnv.withProject` 作用域内调用——initialize 的 registerXDef 经 K3 委托链需要当前项目（NopProjectService.init 天然在该作用域内；契约写入 javadoc，违者按 delegate 链 IllegalStateException 显式失败）。
3. **registrar 落位 = nop-xlang-debugger**（CL-301 通道②）：插件经既有坐标可见（build.gradle.kts :30）；XLangCoreInitializer 所在 nop-xlang 与 ICoreInitializer 所在 nop-core 属平台核心（条件申报③，禁选）。落位理由声明：nop-xlang-debugger 是插件在 Maven 反应器内唯一既有依赖模块，registrar 为插件运行时的 XLang 设施宿主契约，非调试协议本身——此为通道② 可执行性优先的取舍，登记为评审挑战面（Risks 同源）。
4. **NopProjectService 接线**：init 内以 registrar 获取持有（替换 :51-53 的直接 initialize + 追加 destroy），持有凭据的释放登记进 cleanup（:30），dispose（:75-78）经 cleanup.cancel 释放；registerXlib 与 ProjectEnv.withProject 作用域（:50-57）零改动。
5. **CL-204 = 三态失败标记**（唯一预设，「三选一」定夺）：init 状态 = 未初始化 / 初始化中 / 就绪 / 失败。进入 init 即置「初始化中」（承担现 :48 先置的重入截断职能——K4 委托链同线程重入时立即返回）；全部步骤成功后置「就绪」；任一步骤失败 → 置「失败」并记录原因，释放本次已取得的 registrar 持有（计数感知：其他项目持有时不触发全局 destroy，本项目为唯一持有者时 destroy 本次初始化器以免遗留孤儿全局注册），原异常上抛；此后 get() 对「失败」态 fail-fast 抛出携带原因的异常（**异常形态 = IllegalStateException（插件既有形态，R1 m7），英文消息，cause 携带初始化失败原因**——不再静默返回半初始化服务），恢复路径 = 重开项目（新服务实例）。
6. **契约语义声明（owner doc 归属指针）**：「XLangCoreInitializer 在插件内为进程级共享设施：每项目初始化、引用计数销毁、最后关闭的项目撤销全局注册；per-project 组件模型注册经 ProjectResourceComponentManager 委托随项目配对」——本节为该契约的当前承载；nop-dev-tools 无 `docs-for-ai/03-modules/` owner doc（缺口声明维持），owner doc 归属由 R-G 治理批承接。

**被否决的备选**：
- **引用计数同时门控初始化（首项目初始化、后续项目跳过）**：后续项目跳过 registerXDef → 其组件管理器缺 xdef 配置（K3），功能回归。
- **app 级一次初始化 + project 级委托（NopAppListener.appFrameCreated 内初始化）**：registerXDef 经委托链需要当前项目（NopProjectService.get() :61-64 无项目即抛 IllegalStateException），app 级上下文无项目——初始化必然失败。
- **registerXlib/registerXDef 纳入引用计数集（R1 Major-4 原始要求）**：前提「写全局」经 K3 证伪；纳入后反而会让后到项目缺 per-project 配置或让先关项目的撤销失配。
- **CL-204「置位后移」**：K4 无界递归。
- **CL-204「失败回滚 + 自动重试」**：回滚须撤销本次初始化，但其全局部分与其他项目为同一实例——撤销即跨项目销毁（缺陷复现）；不撤销则 per-project 的 registerXDef 残留使重试在 :226 Guard 上确定性失败。在 XLangCoreInitializer 零触碰约束下，重试不可靠。
- **改 XLangCoreInitializer 拆分全局/项目两段**：条件申报③（平台本体），禁选。

**零触碰边界**：XLangCoreInitializer、EvalGlobalRegistry、ValueResolverCompilerRegistry、ScriptCompilerRegistry、ResourceComponentManager、ICoreInitializer 零 diff；ProjectResourceComponentManager、NopAppListener、plugin.xml 零 diff；NopProjectService 的 getDict/get/getComponentManager/registerXlib 方法体零改动（仅 init/dispose 接线与状态机）。

## 裁定④ W-04：XLangDebugger 挂起交接纪元化（P2）

**决策**：

1. **resume 纪元 + 宣告前快照**（唯一可绿形态）：调试器维护单调递增的 resume 纪元，由 monitorNotify（:195-204）在 monitorLock 内推进（全部 resume/step/close 命令入口——:155/:165/:175/:192 与 runToPosition :236-239、close :88——经此唯一路径）。六个停点序列（checkStepInto :366-375、checkTempBreakpoint :377-388、checkSuspend :390-398、checkStepOver :400-421、checkStepOut :423-441、checkHitBreakpoint :443-470）各在 **stepMode 复位之后、notifier 回调之前**读取纪元快照并交给 doSuspend。等待判据 = 「纪元仍等于快照且未 closed」；快照之后到达的任何 resume（包括 notifier 回调内同步投递的 resume）都使该停点直接完成、不进入等待——**可观测新语义：停点快照之后到达的 resume 一定生效，不再挂死**；**边界措辞（R1 m5 订正）**：无效的是「**快照之前**」到达的 resume（属上一停点或无停点，与 pre-fix 一致）；快照之后、挂起发布（向 IDE 宣告停点命中）之前到达的 resume 同样使本停点直接完成——此时 IDE 已收到停点命中通知而线程不挂起，该「已宣告未挂起」窗口为**已接受的竞争语义**（pre-fix 形态下该窗口挂死，新语义严格更优）。
2. **发布与等待同一临界区**：doSuspend 在 monitorLock 内判定纪元——已前进 → 不发布任何挂起标志（per-thread 与全局 `suspended` 均不置位，`lastSuspendThread` 指针照常发布）直接返回；未前进 → 按既有可见性序（:473-477 注释语义零回退：per-thread 先于全局）置位、唤醒 waitSuspended 观察者（:539 语义维持），在纪元条件上等待，finally 复位 per-thread 标志。monitorNotify 的清标志（现 :197 锁外）移入锁内与纪元推进同一临界区。由此 C1-001 的两种失配同时闭合：幽灵挂起项（清标志落在 :479 与 :535 之间——per-thread 标志置位后无人复位）不再可能，因为置位只发生在锁内且仅在纪元未前进时；吞挂起（回调内 resume 被后续置位覆盖）由纪元判据消除。**`lastSuspendThread` 发布时机维持 doSuspend 内（R1 M1）**：不为其测试用途提前发布——stepOver 的 `lastSuspendThread==null` 直接 return 守卫（:159-166）依赖「已发布 = 已经过一次完整停点」，提前发布会改动停点序列语义，违反零触碰边界；红③ 的 step 变体驱动形态据此设计（见裁定⑥）。
3. **语义保持**：closed 抛异常路径（:553-555）维持——无论等待与否，doSuspend 退出时 closed 为真即抛 `ERR_XLANG_DEBUGGER_ALREADY_CLOSED`；中断语义维持（恢复中断标志、退出等待、复位 per-thread）；多线程同时挂起时一次 resume 释放全部等待者（与 pre-fix 全局 `suspended=false` 语义一致）；pause 命令 suspend()（:179-188）不推进纪元，waitSuspended（:127-146）判据不变。
4. **既有钉相容**：TestXLangDebugger :50 与 TestRemoteDebugger :85 的 runToPosition 先于被调试线程启动，其 resume 推进的纪元早于任何停点快照——首停点不受影响；后续 resume 均发生在 assertBreakAt 确认挂起之后——消息序零变化。

**被否决的备选**：
- **monitorWait 复位无条件化**（进入即 lock + finally 复位 + 循环条件锁内快照）与 **doSuspend/monitorNotify 统一 monitorLock 临界区**（signal-before-wait 锁内重查）：二者只能消除幽灵项，回调内同步投递的 resume 仍先于置位发生、被随后的置位覆盖 → 红③ 场景继续挂死（R1 Major-1 相容性裁定）。
- **纪元在 doSuspend 内递增**（K8）：doSuspend 在回调之后，无法把回调期间的 resume 归属到本停点。
- **resume 计数「待消费令牌」（resume 未匹配停点时存一张令牌，下一停点消费）**：宣告前到达的陈旧 resume 会错误地放行下一个停点——改变「暂停在下一断点」的用户语义。

**零触碰边界**：matchTempBreakpoint（:484-512，C1-006 P3 watch 面）零改动；checkBreakpoint 分派（:333-364）、各停点序列的判定逻辑与 notifier 调用零改动（仅增快照点）；IDebugNotifier/DebugNotifier（W-10 面）零触碰。

## 裁定⑤ W-06：suspendedThreads 清理周期化（P2，吸 CL-003）

**决策**：

1. **调度形态 = 固定延迟周期**：构造器（:79-81）的一次性 `schedule(cleanup, 30s)` 改为固定延迟周期调度，缺省初始延迟 30s、周期 30s（与原 30s 节拍一致）。固定延迟而非固定速率：cleanup 持 suspendedThreads 监视器遍历，慢执行时不得补偿性连发。
2. **周期任务绝缘**：周期体捕获异常并 WARN 记录后继续——ScheduledExecutorService 周期任务一次抛出即永久取消后续执行，清理停摆即缺陷复现（[periodic-task-insulation.md](periodic-task-insulation.md) 的绝缘语义同族，本处为 JDK 单线程调度器使用面，不引入平台设施）。
3. **可测性 seam = 包内可见构造器注入初始延迟与周期**：调度在构造期发生，setter 无法改写已提交的调度——注入点只能是构造器；公共无参构造器委托缺省值，生产调用面零改动。（monitorWaitInterval setter :105-111 为「可注入参数」先例，形态因构造期调度而取构造器。）
4. **语义保持**：cleanup 判活逻辑（:94-103，`isAlive` 删除）零改动；close（:84-91）的 shutdown 与 clear 保留；清理线程维持 daemon 命名工厂（:72-73 `xlang-debugger-cleanup`）。

**被否决的备选**：固定速率周期（慢执行连发）；平台 globalTimer/IScheduledExecutor（调试器生命周期与平台共享定时器解耦困难，close 时需逐任务取消，[pool-facility-hardening.md](pool-facility-hardening.md) 的 globalTimer 消费方矩阵外扩）；put/remove 处即时摘除（线程死亡无回调点，仍需周期兜底）；弱引用条目（条目载荷 EvalRuntime 由被调试线程强持有期间才有意义，死亡后需显式清除，弱引用不改变清理时机的可观测性）。

**零触碰边界**：makeSuspendedThread（:558-571）、getSuspendedThread（:241-250）、getSuspendedThreads（:223-233）零改动。

## 条件申报登记（禁静默扩权——预设全部不触发）

①`*-api` 叶（nop-api-debugger 等）→ 四件证据链（plan 追加申报 + 本文档专节修订 + draft review 记录追加 + 回归测试）——预设不触发（IDebuggerAsync/IRpcProxy 为使用面）。②orm/DB 承载——全批无 DB 面，禁选。③平台设施本体（nop-commons 队列设施、nop-socket/nop-rpc-*、nop-core 注册表、nop-xlang XLangCoreInitializer）——预设不触发：裁定① 否决平台队列设施、裁定② 平台残余零触碰、裁定③ registrar 包装调用。④`_` 生成物或插件以外的 Gradle 面——预设不触发；JsErrors.java 为手写保留文件。

## 批外新发现登记（本批零触碰，plan 侧须登记归属）

- **N1（K9）**：JavaScriptService.refreshConfig 扩容分支 :107-115——:112 写旧数组越界、:111 绕过 newWorker 装配。不在 R3-10 finding 清单；归 plan Deferred（out-of-scope，successor 待 plan 指定）。
- **N2（裁定②.6，R1 M2 订正后文本）**：XLangDebugProcess 会话编排残窗 = **connect 从未成功即退出重试循环**（debuggee 已终止 `isProcessTerminated` 或后台任务取消 `checkCanceled` 抛出）时 `connector::destroy` 从未登记进 cleanup，而首轮 `newInstance` 已经 init 建出工厂 timer/executor（SimpleRpcClientFactory :176 init 先于 :188 connect）——工厂线程资源泄漏。**stop×connect 并发不是残窗**：Cancellable.appendOnCancel 已取消后追加立即执行（:63-72/:106-123），destroy 必达（原「追加不补跑」表述系事实错误，废止）。插件 Gradle 面，归 plan Deferred（out-of-scope，successor 待 plan 指定）。
- **N3（裁定②.4）**：nop-socket SocketClient.reconnect 不复查 closed 的平台残余窗——watch-only，平台批承接。
- **N4（closure audit m-5 登记；执行期发现）**：SimpleRpcClient 构造器覆写 SimpleRpcClientFactory 在 SocketClient 上设置的 onChannelOpen/onChannelClose 回调（SimpleRpcClient :55-65 覆写 SimpleRpcClientFactory.newClient :195 设置）——工厂侧回调永不触发；红② 僵尸重连观测据此改服务端 accept 计数承载。平台设施面零触碰（条件申报③），watch-only 平台批承接。

## 批域边界

**与 8.41+（R4 批次）边界**：本批与 R4-01~R4-33 零依赖零文件交集；cra-index 的「W-03=R22 成员 / W-04=R06 cross-ref / W-06=F4 清理族」为家族关联注记，R4 侧同族修复以本文为对齐参考，不回写本批；nop-web DynamicJsLoader/CL-004（C3/C9 域留档）零触碰。

**与 R-G 治理批边界**：四模块（nop-dev-tools/nop-frontend-support/nop-demo/nop-entropy-e2e）owner doc 补齐、通道①（gradlew harness 入 CI 与 IntelliJ 平台下载面）、触发条款登记册、demo 治理（W-09/W-11）全部归 R-G。本文与 R-G 的衔接面 = ①CL-301 通道裁定落档（W-02/W-05=②，W-03=③，W-07=宣读，①归 R-G）②裁定③-B.6 契约语义的 owner doc 归属指针 ③缺口声明维持。

**批内 watch/P3 边界**：W-07（XLangBreakpointHandler :42 muted 非 volatile，P3）本文宣读登记、零修复零触碰；W-08~W-11 与 C10 watch-only W-1~W-5 零触碰。

## 裁定⑥ 测试缝总表（红①–红⑥ 驱动器 + 零回退钉清单 + C10 映射）

### 红绿判据（先红后绿，E2）

本批 **0 条反向钉改写、0 条变异锚定面**。全部测试零 mockito（nop-js/nop-xlang-debugger pom 仅 junit-jupiter），观测一律 JDK 并发原语 + 手工替身。**orTimeout 假绿陷阱纪律（全批强制）**：future 完成性断言只允许同步 `get(timeout)`，捕获 `TimeoutException` 即判红；`CancellationException`/`ExecutionException` 均计为「已完成」；禁用 `orTimeout`/`completeOnTimeout`——它们会把搁浅 future 以超时异常完成，使「isDone 为真」成为假绿通道。

**产品侧测试缝（全批共 4 处——S1/S2/S3 为 Maven 面包内可见；S4 为 Gradle 面缝，R1 m8 登记）**：S1 JavaScriptWorker 队列注入构造器（红① 闩控）；S2 JavaScriptService 当前 worker 数组快照访问（红①(b)(d) 观测——`workers`/`getCurrentWorker` 均 private，R1 Minor-④）；S3 XLangDebugger 清理延迟/周期注入构造器（红④）；S4 ProjectFileHelper 缓存条目键集合的包内可见只读访问（红⑥ harness 断言用，落 `io.nop.idea.plugin.utils` 测试包——Gradle 面非 Maven 门禁，通道③ 承载）。W-02/W-05 下沉类以公共 API 直测，无缝；W-04 无新缝。nop-js 新测试落 `io.nop.js.engine` 测试包（缝所在包），沿 TestJavaScriptService 的 CoreInitialization + `/nop/js/test-server.mjs` 夹具。

| 钉 | 成员 | 驱动器形态 | 红判据形态 | 缺陷态红判据 |
|---|---|---|---|---|
| 红① | W-01（nop-js） | (a)(c) **闩控确定性主驱动（S1）**：注入的队列在**被标记的提交线程**首次入队时、**委托 `super.add` 之前**阻塞于闩（R1 m1——阻塞点在入队前，任务对消费方不可见，排空不会取走它；其余线程含 pill 不受影响）→ 提交线程已越过前置检查 → 主线程触发关闭并轮询至 `getExecuteThread()==null`（终局序列完成）→ 放闸 → 断言：提交或同步抛 `ERR_JS_CONTEXT_ALREADY_CLOSED`，或返回的 future `get(5s)` 完成；且 `getWaitingTasks()==0`。**预热前置（R1 m1）**：闩测试前先以正常 `schedule(...).get(5s)` 完成一次预热调用，确认 worker 线程已启动且 init 完成——防「`getExecuteThread()` 初始即为 null 使轮询立即假成立」与「worker 后启动 do-while 至少执行一轮把任务以 CancellationException 完成」两态伪绿。**双关闭入口各一轮**：close() 一轮、shutdownGracefully() 一轮（后者等待空闲退出）。闩带上界（≤30s）且收尾必放闸。(b) **close 链零劣化绿锚**：两入口关闭后 worker 线程 5s 内终止。(d) **失效层② 死路由（S2）**：workerCount=2 启动，经 S2 关闭 index 0 worker 并等其线程终止 → 连续 invokeAsync N=10 次全部成功（落存活 worker）；全部关闭后 invokeAsync 同步抛 `ERR_JS_CONTEXT_ALREADY_CLOSED`（既有码零回退锚）；**随 m6 扩展**：关闭 index 0 后调用 `reinitWorkers()` 断言不抛且存活 worker 完成 reinit（**观测口径（R2 watch-9）**：reinitWorkers 返回 void 丢弃 future——reinit 完成性经 S2 取存活 worker 以普通函数调用 FIFO 证明 reinit 已执行，reinit future 完成性断言只在 (f) 的 schedule(reinit) 通路承载）。(e) **失效层③ 容量**：未启动的 worker 连续 execute 至公共上界常量 → 下一次同步抛 `ERR_JS_WORKER_QUEUE_FULL`（断言携带 ARG_MAX_WAITING_TASKS 参数值）；随后 close() 正常返回且 pill 入队（等待数 = 上界 + 1——Major-3 绕过断言）。(f) **CL-201**：启动后 schedule(reinit) → `get(5s)` 正常完成；改不可解析脚本路径后再 reinit → future 异常完成，随后普通函数调用仍成功（旧单元可用）；**嵌套拒绝变体**：worker 内 JS 回调路径触发嵌套 FUNC_REINIT → future 异常完成（IllegalStateException）且旧 context 保持可用。(g) **服务层冒烟**：workerCount=1，另起线程循环 invokeAsync，主线程 stop()，全部返回 future 按纪律 `get(5s)` 完成（非门禁性，K6） | 确定性（闩控）+ 编译期构造 | (a) pre-fix 任务入队于终局排空之后 → future `get(5s)` TimeoutException——红；(c) 等待数 = 1——红；(d) pre-fix index 0 死 worker 零等待优先命中 → 同步抛错——红（确定性）；(d-reinit) pre-fix 死 worker execute 同步抛错中止循环——红；(e) pre-fix 无上界常量——编译期红；(f) pre-fix reinit future 永不完成——红（确定性），失败变体旧 context 已关——红；(b)/(d) 全死分支为绿锚 |
| 红② | W-02（nop-xlang-debugger） | 本地 SimpleRpcServer（独立固定端口，与 TestRemoteDebugger :53 的 12345 分立；surefire 类串行）+ 被测 XLangDebugger 服务实现；**每轮新建 connector（含其工厂）**——destroy 后工厂 newInstance fail-fast（:169-175），复用非法（R1 Minor-③）。三轮 connect → destroy；**线程观测按名过滤 + 身份快照（R1 m3）**：以工厂执行器命名前缀（`simple-rpc-client-executor-`，SimpleRpcClientFactory :136）枚举线程，**轮前对线程对象做身份快照（Set<Thread>）**、destroy 后 5s 内（50ms 轮询）断言「本轮快照中新增的线程全部 `!isAlive`」——不做数量基线比较（TestRemoteDebugger 的 tearDown 不关通道，会在同 JVM 留下异步死亡的同名线程，数量基线可被抵消）；三轮后新增集合为空。**无僵尸重连（接线验证绿锚）**：connect 后先停服（模拟 debuggee 被杀，进入重连）→ destroy → 同端口重启服务 → 2s 观察窗内 connector 的 onChannelOpen 回调计数不增长 | 确定性 | pre-fix destroy 不关 socket，recv 线程钉死于 read（readTimeout=0）→ 本轮新增线程 5s 内持续 isAlive——红（确定性）。无僵尸重连断言 pre-fix 红性取决于平台 RetryHelper 对已销毁 timer 的行为，**不作门禁**，仅作 post-fix 接线验证 |
| 红③ | W-04（nop-xlang-debugger） | 替身 notifier（CollectNotifier 扩展）在 notifyBreakAt 回调内**同步**调用 resume()；被调试 daemon 线程依次经过 N=20 个普通断点行（不使用 runToPosition——避开 matchTempBreakpoint C1-006 面）；断言线程 5s 内 join 完成、回调计数 == 20、终态 `getSuspendedThreads()`（:223）为空且 `isSuspended()==false`。**step 变体（R1 M1 修订形态）**：第 1 个停点在回调内投递 resume（使 `lastSuspendThread` 完成一次正常发布——stepOver 的 `lastSuspendThread==null` 直接 return 守卫（:159-166）使首停点回调内 step 命令为空操作）；自第 2 个停点起回调内投递 stepOver；替身在 notifyBreakAt/notifyStepOver/notifyStepInto 三个回调内按停点序投递下一条命令，断言回调总数与推进——**禁止为此提前发布 `lastSuspendThread`**（裁定④.2 零触碰边界）。收尾 close() 释放 pre-fix 挂死线程 | 确定性 | pre-fix 首个断点：回调内 resume 先清标志，随后 doSuspend 置位并等待 → 线程挂死、join 超时——红（确定性） |
| 红④ | W-06（nop-xlang-debugger） | S3 注入 50ms/50ms；被调试线程挂起后**保持挂起跨越首个清理节拍（≥3 个周期，注入 50ms 即 ≥150ms）再 resume 并结束**（R1 m2——一次性调度在首个节拍后不再触发，无法清除后死条目；跨越多节拍区分「周期调度」与「可注入延迟的一次性调度」）→ 以包内既有 getSuspendedThread（:241-250）按线程 id 轮询（≤5s），断言条目被清除 | 编译期构造 + 绿后行为 | pre-fix 无 S3 构造器——编译期红；绿后条目在周期内清除 |
| 红⑤ | W-05（nop-xlang-debugger，registrar 下沉） | **每用例 new 独立 registrar 实例（包内构造器，R1 M3——静态单例共享计数会串扰）**。计数替身 ICoreInitializer：acquire×2 → 初始化计数 2；release×1 → 销毁计数 0（全局存活）；release×2 → **保留集（2 份）按获取逆序全部 destroy——销毁计数 == 2 且顺序断言（最后销毁的是最先 acquire 的那份）**（R1 m4）；同一凭据重复释放 → 无效（计数不二次扣减）；失败替身（初始化抛错）：有其他持有者时 acquire 抛出、计数不变、销毁计数 0；无其他持有者时销毁计数 1（孤儿注册清理）。真实 XLangCoreInitializer 不入 Maven 测试：plain JVM 下全局 ResourceComponentManager 非 per-project，二次 registerXDef 必触 :226 Guard——真实接线验证属 Gradle 面（通道③ 登记） | 编译期构造 + 绿后行为 | pre-fix 无 registrar——编译期红；绿后引用计数与失败语义断言 |
| 红⑥ | W-03 + CL-204 + W-05 接线（nop-idea-plugin Gradle 面，通道③） | **harness 文件写入、本环境不执行**：**setUp 须先注册 ProjectResourceComponentManager（BaseXLangPluginTestCase :86 先例——K3 委托链前提，R1 m4b）**。W-03——临时 heavy 项目（开 → getCachedNopVfsPaths → 关闭并销毁）后断言缓存不再含该项目（经 S4 包内可见只读访问观测条目键集合，测试落 `io.nop.idea.plugin.utils` 测试包）；CL-204——init 失败后 get() fail-fast 携带原因（IllegalStateException）；W-05 接线——两项目开、关一个后全局函数仍可解析。**Maven 侧变异/回退论证**：W-03 修复 = 插入点一处移除登记，回退 = 删除该登记 → 条目永久滞留（结构论证，留痕于 red-green 总册） | harness 写入 + 变异/回退论证 | 不执行留痕（禁伪绿）；E2「或变异/回退论证」分支承载 |

变异探针纪律：本批无变异锚定面；红⑥ 的回退论证为结构论证，不执行探针。红绿快照落 `_tmp/red-green-2533-summary.md` + 逐 trial 日志。红① (a)(c) 的闩必须带上界（≤30s）且收尾必放闸，防测试自身挂死。

### 零回退钉清单

- 三通道基线 **111 tests 0F 0E 1S**（plan 勘误 4 亲跑在案）：`./mvnw -o test -pl nop-dev-tools` 8（TestDebugValueHelper 3 + TestXLangDebugger 1 + TestRemoteDebugger 2 + nop-maven-shaded-plugin 2）、`./mvnw -o test -pl nop-frontend-support` 96（1S）、`./mvnw -o test -f nop-frontend-support/nop-js/pom.xml` 7——零回退实证 = 三通道 BUILD SUCCESS（111 + 新钉，0F0E）+ **1S 签名比对**（TestFluxNormalizeAction 1，nop-web 批前既有，数量与归属不变）；执行期出现 Skipped/Failures/Errors 即登记归因，禁静默。
- 点名既有钉族：nop-js——TestJavaScriptService 5（正常量级零触发容量上界、零触发拒绝）、**TestJavaScriptServiceExecutorContract 1（plan 2497 doStart 契约钉，零 diff 红线）**、TestLogOutputStream 1；nop-xlang-debugger——TestXLangDebugger 1 与 TestRemoteDebugger 2（消息序零变化，裁定④.4；assertBreakAt 单发 sleep 测试债零改动）、TestDebugValueHelper 3、CollectNotifier 替身零改动（红③ 以扩展形态承载）；nop-web——TestPageProviderPermissionsTransform 5、TestWebPageExporter 4。
- 平台产物面零 diff：plan 2497 doStart/ExecutorContract、plan 2523 SimpleRpcClientFactory 世代令牌——closure audit git 过滤核对。

### C10 缺保护 6 缺陷面同 diff 映射

C10-001↔红①↔W-01、C10-002↔红②↔W-02、C10-003↔红③↔W-04、C10-004↔红④↔W-06、C10-005↔红⑤↔W-05、C10-006↔红⑥↔W-03——六面与各自本体同 diff 落地，禁拆分交付（E2）。CL-201 随红①(f)、CL-204 随红⑥（通道③）、CL-001/CL-002 随红①(d)/(e)、CL-003 随红④ 验收。测试类命名带单元可辨识归属（6.5 工单号与其他单元 W-01/W-02 同号异站——类名或注释显式归属 6.5）。

## 评审记录

- **设计评审 R1**（2026-11-06，独立评审子 agent agent_47a994a2，fresh session 只读，评审基线 `6a928f26d7`；含想象性分析 + K1–K9 亲读核证）：**0 Blocker + 5 Major + 9 Minor**。裁定① Dekker 论证、裁定④ 纪元方案、裁定③-B 引用计数均判定成立；K1–K9 全部属实（N2 部分不属实——Cancellable 追加已取消立即执行）。Major：M1 红③ step 变体修复后仍红（`lastSuspendThread==null` 守卫 + notifyStepOver 分派）/M2 connector connect×destroy 并发契约缺失 + N2「不补跑」前提错误/M3 registrar API 面未钉死/M4 ③-A IntelliJ 落地细则不足（getService 形态/映射函数内登记的 Recursive update 窗/预检查竞态）/M5 plan 正文回写清单。Minor：m1 闩控阻塞点与预热、m2 红④ 周期判别、m3 红② 身份快照、m4 反注册集缓存边界（逆序全 destroy）、m4b K3 前提条件、m5 快照边界措辞、m6 嵌套 reinit + reinitWorkers 容错、m7 错误码参数与异常类型、m8 测试缝计数 3→4。**全部修订随本轮落盘**（M1→裁定④.2+裁定⑥红③、M2→裁定②新增 2b+⑥订正+②.6 改写+被否决备选增补、M3→③-B 新增 2b、M4→③-A.1 细则化、m1–m8/m4b→各对应裁定与红表；M5 归 plan 正文回写随本轮执行）。修订后进入第二轮评审确认。
- **设计评审 R2**（2026-11-06，独立评审子 agent agent_cfb9d850，fresh session 只读）：设计文档本体 **PASS**（M1–M4 修订全部落盘且自洽、裁定间无新矛盾、行号抽查一致）；FAIL 项仅 M5 残留——plan 2533 正文 6 处活性表述（tearDown 参照/字段捕获/:142-148/对账循环分支/裁定③ 覆盖集与 CL-204 三选一旧文/tearDown :140-146）未带勘误 8 指针，随轮全部订正落盘；watch-only 8 条（闩控主句口径/CL-204 归红⑥/reinit 观测口径/「可重试」措辞/K1 同步拒绝措辞/②.2b connectTimeout 已接受语义/③-A.1 并发首建良性重复/Cancellable :107-123 行号订正）随轮处理或在 plan 登记 watch-only。**R2 后终态：PASS，放行 Phase 2**。
