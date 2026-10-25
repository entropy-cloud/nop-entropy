# kernel 退役代际与模型加载锁序设计（Retirement Generation and Model Load Locking）

> Status: draft（Phase 1a 产出；独立 draft review 为 plan-first 硬前置，0 Blocker 后转 active）
> Last Reviewed: 2026-10-25（plan 2504 Phase 1a 落盘，行号 = live @ `474695a0f2` 回读值，执行前如漂移以 live 为准并登记）
> **评审**：agent_47d95478 通过进入实现（0 Blocker/0 Major；m-1 口径精确化与 m-2 委托族零 diff 登记已落盘；m-3 调度器 destroy 窗口残余/m-4 clear×在飞装载交错/m-5 双档断言结构为 Phase 2 实现注记）
> Source: plan 2504（`ai-dev/plans/2504-cra-phase8.10-r2-01-kernel.md`）Phase 1 裁定①–⑥义务；发现事实源 = WI 2.1 审计（`ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/` 各单元 C1/C2 文件）与 WI 2.5 审计（C6-005）；批次归组 = family-clustering `2.1-退役代际族（混合）` + `2.1-ResourceCacheEntry/死锁族（混合）` + §四#6 双轨裁定
> 实现锚：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/`（RoundRobinSupplier + 退役队列载体）+ `nop-kernel/nop-core/src/main/java/io/nop/core/resource/`（DefaultVirtualFileSystem / ResourceCacheEntry / ResourceLoadingCache / ResourceComponentManager）+ `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/xlib/XplLibTagCompiler.java` + `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java`

## 一、设计结论

1. **裁定①（退役代际平台机制单点）**：三站（RoundRobinSupplier 收缩窗 / DefaultScheduledExecutor destroy 置空窗 / DefaultVirtualFileSystem zip 句柄在飞读者窗）以同一机制收敛——**「发布新代 + 旧代退役登记 + 宽限延迟关闭」**。机制载体 = nop-commons 并发包内新增的唯一退役队列设施（退役动作批、宽限期、排空调度缝、时间锚的唯一实现位）；读侧一律经不可变代际快照访问资源（RoundRobinSupplier.get 热路径维持无锁快照、零新增代价），替换点只发布新代、不内联关闭旧代；对已退役代际的新提交/新获取以类型化终态收敛（ERR_SERVICE_NOT_ACTIVE / 宽限后关闭）。C1-003 形态定稿 = **七读点局部快照 + 死代际 REE 类型化**（destroy 保持置 null，零形态变化）；close() 同族面（C1-001 F3③）走同一整代退役路径。
2. **裁定②（三层监视器链统一不变式）**：C2-001 与 C2-017 同一变更集落地，不变式 = **「监视器内只做同 key 去重 + 占位 + 发布；加载/编译体（I/O、解析、import 求值、宏执行、类绑定）一律在监视器外」**，贯穿三层：entry 层（ResourceCacheEntry 装载状态机：LOADING 占位 + 监视器外装载 + 等待者 wait）、LazyFn 层（LazyCompiledFunction.compile 单飞：监视器内 compiled 双检 + 编译中占位，编译体外移）、标签 entry 层（parseTag/getFunctionModel 的 lazyCompile 调用点经 entry 层修复后已在监视器外，边界显式声明）。跨线程装载/编译等待统一登记于同一**等待图**做环检测：环命中 → 类型化环错误即时打断（闭合双线程死锁与环④/环⑤）；同线程重入 → 类型化环错误（闭合 SOE 面）；检测竞态漏检 → 有界等待兜底超时类型化（闭合永久悬挂）。失败占位复位不驻留（不复刻 C2-015 式条目驻留）。
3. **裁定③（失效/销毁时序）**：C2-002 终裁 = **标记失效 + 延迟销毁**（复用裁定①退役队列，销毁动作 = listener.onDestroyed + safeDestroy 在排空期执行），拒绝读者登记分支（热路径登记税 + 借还制缺失）；`clear()`（覆盖 ResourceLoadingCache.remove 与 state_loadFrom 两失效入口与 destroy()）与 `reloadObject→destroyObject` 两销毁点全部改走延迟销毁，快路径无锁交出形态零改动；**终态为真实代码路径，非 javadoc-only/warn-only**。
4. **裁定④（2.5-C6-005 双轨联动）**：设施轨 = 裁定①产物对「已交付在用共享连接」的关闭时机契约（缩容只退役被裁剪尾段；已交付连接存活 ≥ 宽限期；新取用只见存活代；共享 RoundRobin 无借还制，宽限期为有界自限的排空语义）；使用面轨 = LettuceRedisConnectionProvider 三取用入口 javadoc 共享连接不得 close 契约 + refreshConfig 逻辑零改动预期。**测试缝子句**：随修复抽取 `protected` 连接供应商工厂方法（`createConnectionSupplier(factory, n)`）供测试子类注入 fake supplier，显式声明为 C6-005 修复面一部分；C10-026 缩容触发回归经该缝走真实 refreshConfig→resize 链先红后绿（dockerless）。
5. **裁定⑤（N2 钉改写契约）**：`TestDefaultVirtualFileSystemRefresh` 与 C1-014 修复同变更集改写——保留「重建期间不得提前关闭」断言；原 :66「refresh 返回后旧 zip 立即 closed」断言改写为退役契约的**可观测等价二段断言**：「refresh 返回时旧 zip 未被同步关闭（已退役）」+「确定性触发排空后旧 zip 必然关闭」，并新增「refresh 后读取解析到新代」接线断言；改写后断言在修复前基线可红（现行为立即关闭）、修复后绿。
6. **裁定⑥（边界声明）**：R1-01 绝缘层（destroy 拒绝链 / periodicTasks / trackPeriodicTask / rejectChain）、R1-02 池设施（refreshConfig 类型化语义 / ExecutorHelper 收缩重排）、R1-05 缓存门（shouldReloadObject / verdict 记忆化 / 双 mtime 守卫 / deps-空豁免）**零回改**，共享代码位改动以 §3.6 逐项声明交互；本批新增时间机制（退役宽限期、装载等待上界）**一律复用 `CoreMetrics.currentTimeMillis`**（F4，不引入可注入时钟），确定性测试缝 = 时间阈值可配置且 **≤0 = 关闭档（退化为现行同步语义）** + 排空调度器可注入（零 sleep 断言）。

## 二、背景与动机

R2-01 收敛 7 项已确认并发缺陷（P1×2 + P2×5），分两族：

- **退役代际族（C1-001/C1-003/C1-014，同构缺陷三站）**：三处「先发布新代、再关闭旧代资源」的替换点共享同一缺陷形态——发布动作保护了**后发**读者（新读者只见新代），但对**发布前已捕获旧代引用的在飞读者**零保护：RoundRobinSupplier resize 收缩在锁外关闭旧数组尾段（:60-61 发布 / :83-86 finally 锁外 closeNext，:89-93），并发 get 经 :41-45 快照取得已关闭资源；DefaultVirtualFileSystem refresh/destroy 发布新 store 后关闭旧 zipFiles（:70-77/:87-94），在飞读者经 ZipEntryResource 共享 ZipFile 句柄直读（impl/ZipEntryResource.java:98-100）抛 `zip file closed`；DefaultScheduledExecutor destroy 无锁置空 executor 字段（:115-126），六入口 + stats() 的 checkEnabled-then-use（:144-149/:152-214/:111-113）窗口内以裸 NPE 失败而非类型化 ERR_SERVICE_NOT_ACTIVE。三站的共同本质：**资源的退役（关闭/置空）时机与「引用已交付」事件无同步关系**。
- **RCE 死锁/失效族（C2-001/C2-017/C2-002）**：ResourceCacheEntry 持 entry 监视器执行模型装载（getObject :255-287 synchronized 内 :265 loadObject；reloadObject :348-361 同链），装载器递归经 ResourceComponentManager（ModelLoader :107-128）与 GlobalFunctions.loadDslModel（:438-441 → requireComponentModel）再入其他 entry：循环依赖 + 双线程相反顺序 = 无超时永久死锁，单线程环 = 可重入监视器无限重载至 StackOverflowError；XplLibTagCompiler 的 LazyCompiledFunction.compile（:734-770 synchronized 持自身监视器完整编译，:800 runImportExprs import 求值 + 宏执行 + 生成类绑定）在模型/标签 entry 链内触发，锁序图新增环④/环⑤——只修 entry 层则编译等待与装载发布的交叉等待环残余闭合，必须同变更集；C2-002 = 快路径无锁交出引用（:255-256/:286）与 clear 锁内销毁（:135-148）、destroyObject（:363-370）并发 = use-after-destroy 窗（无引用计数、无宽限期、无延迟销毁队列）。
- **2.5-C6-005（双轨联动）**：LettuceRedisConnectionProvider.refreshConfig（:92-100）经 resize 立即关闭被裁剪共享连接，已交付在用连接被热关、取用窗口内瞬时命令失败——其设施本体即 C1-001 的 RoundRobinSupplier 残余面（R1-02 未覆盖，勘误 5），使用面随批接入。

前批边界：R1-01（plan 2496）绝缘层、R1-02（plan 2497）池设施、R1-05（plan 2500）缓存失效门已落产物与本批共享代码位，零回退义务与交互边界见 §3.6。

## 三、核心设计

### 3.1 裁定①：退役代际平台机制（C1-001 / C1-003 / C1-014）

#### 3.1.1 机制契约（载体 = 唯一退役队列设施）

机制载体为 nop-commons 并发包内**新增的唯一设施**（包 `io.nop.commons.concurrent`，类名实现期定稿，下称退役队列），契约：

```text
retire(resource, closeAction):     # 退役登记：不入队即关闭的动作延后
    enqueue (resource, closeAction, deadline = CoreMetrics.currentTimeMillis() + grace)
    if no drain pending: scheduleDrain(at = deadline)   # 合并调度：同窗多笔退役只排一次

drainDue():                        # 排空：deadline 已到的退役动作依 FIFO 执行
    for each due entry: closeAction.accept(resource)    # 关闭/销毁动作唯一执行位

grace: Duration，配置键 nop.commons.concurrent.retirement.grace-period（默认 5s）
    ≤0 = 关闭档：retire 退化为同步立即执行 closeAction（= 现行语义，确定性测试缝）
排空调度不可达（调度器已销毁/拒绝）→ 退化为同步立即关闭 + debug 日志（fail-safe，无静默泄漏）
```

- **时间锚（F4）**：deadline 计算唯一经 `CoreMetrics.currentTimeMillis`（R4-32 换源自动覆盖）；不引入可注入时钟，确定性来自 grace 可配置 + 排空调度器可注入（测试注入可手动触发的调度器收集排空任务，零 sleep）。
- **排空任务体卸载**：关闭动作可能含阻塞 I/O（Lettuce 连接 close、ZipFile close），按池设施调度槽契约（pool-facility-hardening 裁定①）默认经 globalTimer 一次性调度 + `executeOn(globalWorker())` 卸载执行；测试经注入调度器确定性触发。
- **队列无界性声明**：退役速率上界 = 替换点触发频率（resize/refresh/失效为低频运维动作），排空延迟上界 = grace——队列长度有界（≤ 一个宽限窗内的退役笔数），无独立熔断必要。

#### 3.1.2 三站落点

| 站点 | 缺陷窗 | 退役形态 | 快照/读侧形态 |
|---|---|---|---|
| RoundRobinSupplier（C1-001 主 + 2.5-C6-005 设施面） | resize 收缩 :60-61 发布新数组后 finally :83-86 锁外 closeNext 旧数组尾段（:89-93）；并发 get :41-45 经快照取得已关闭资源 | 收缩分支：发布新数组后，旧数组**被裁剪尾段**（下标 ≥ size 的元素）逐元素 retire；扩容失败路径（:73-79）维持 inline close（本次新建资源从未交付，无读者） | get（:41-45）**零改动**：无锁快照 + AtomicReferenceArray 读，redis 每请求热路径零新增代价；快照所属代若为退役代，其元素在宽限期内保持存活，快照→解引用窗由退役机制消除而非加锁 |
| DefaultScheduledExecutor（C1-003） | destroy :118 无锁置空 × 七读点 checkEnabled-then-use（:144-149 判空 + :152-214/:111-113 解引用） | 本站退役对象为「已 shutdown 的 STPE 代际」：destroy 体（shutdownNow + :122-125 rejectChain + :125 periodicTasks.clear）**零改动**，置 null 保持 | **七读点局部快照**：`exec = this.executor`（一次 volatile 读）→ null 则抛类型化 ERR_SERVICE_NOT_ACTIVE（现有语义）→ 后续一律用 `exec`（消除判空与解引用之间的窗）；快照非 null 但代际已死（shutdownNow 后提交）→ JDK REE 在提交点捕获并转 `NopException(ERR_SERVICE_NOT_ACTIVE)`（fixedRate 路径在 rejectChain 前转换，拒绝终态 cause 类型化）；stats() :111-113 同型（null 快照 → 类型化错误） |
| DefaultVirtualFileSystem（C1-014） | refresh :70-77 / destroy :87-94 发布新 store 后 `IoHelper.safeCloseAll(oldZipFiles)`，发布前已捕获旧 store 的在飞读者经 ZipEntryResource :98-100 共享 ZipFile 直读 | refresh：`buildResourceStore()`（:79-84，发布点形态保持）之后旧 zipFiles 整代 retire；destroy：空 store 发布后旧 zipFiles 整代 retire（zipFiles 置 null 保持） | 读路径（getResource / ZipEntryResource）零改动：旧代 store 引用仍指向宽限期内存活的 ZipFile |

**close() 同族面（C1-001 F3③）**：`RoundRobinSupplier.close()`（:96-103）从 inline 全关闭改为**整代退役**（当前代全部元素 retire + 排空调度）——与收缩同一机制，消除 close × 并发 get 的同型窗；close 之后再 get 的语义维持现状（交付对象由调用方契约约束，本批不新增状态机）。构造失败路径（:33-37）维持 inline close（对象未发布、无并发读者、fail-fast 语义更优）。

#### 3.1.3 C1-003 形态定稿与交互声明

| 交互点 | 声明 |
|---|---|
| `isDestroyed()`（:140-142） | 零改动：`executor == null \|\| executor.isShutdown()` 两支与快照形态、置 null 保持均兼容 |
| R1-01 绝缘层 | destroy 体 / periodicTasks / trackPeriodicTask / rejectChain 零改动；PeriodicTaskHelper 逐轮重排经 `this.schedule(...)` 走快照检查，死代际类型化错误按「捕获位置」分类落入绝缘拒绝终态（链终止、future 有终态、不复活——语义不变，仅 cause 类型化） |
| R1-02 refreshConfig（:74-97） | 类型化语义零回退；其 :79 判空与 :90-91 解引用为同型 check-then-use，机械同修为局部快照（行为零变化：null 快照静默 return 保持） |
| 拒绝语义 | 裸 NPE 消失；窗口内提交要么成功（快照先于 destroy）要么获类型化 ERR_SERVICE_NOT_ACTIVE——与 R1-01「future 有终态、不悬挂」对齐 |

### 3.2 裁定②：三层监视器链统一不变式（C2-001 + C2-017 同变更集）

**统一不变式：监视器内只做同 key 去重 + 占位 + 发布；装载/编译体在监视器外。** C2-001 与 C2-017 同一变更集（家族裁定硬约束，commit message 声明绑定）——只修 entry 层则 LazyFn 监视器内完整编译（:734-770）与装载发布的交叉等待环（环④/环⑤）残余闭合。

#### 3.2.1 entry 层装载状态机（ResourceCacheEntry）

```text
getObject(checkChanged, loader):
    value = object                                  # 快路径无锁，零改动
    if value == null || checkChanged && shouldReloadObject():   # R1-05 门，零回改
        synchronized(this):
            if 已发布结果（含 Null 占位）且非本轮装载: return it     # 同 key 去重
            if state == LOADING: 等待协议（见下）                    # 占位命中
            state = LOADING; loaderThread = current                 # 占位安装（监视器内）
        result = loadObject(loader)                                 # 装载体在监视器外
        synchronized(this):
            发布 result（含 Null.NULL 占位 / 失败复位）；state = IDLE; notifyAll()
```

- **等待协议**：等待者在 entry 监视器上 wait（进入等待前不持有任何其他 entry/LazyFn 监视器——不变式保证「持有多监视器等待」集合恒空，经典锁序死锁结构性消除）；被唤醒后重入循环：已发布 → 返回；失败复位 → 按现行语义各自重试（失败不驻留，**禁 C2-015 式条目驻留**：不缓存失败哨兵、不留悬挂占位）。
- **环检测（同线程）**：装载中同线程再入（loadDslModel 环）→ `loaderThread == current` 即时抛类型化环错误（CoreErrors 新码，携带 path）——**SOE 面闭合**，不再无限重载。
- **环检测（跨线程，等待图）**：等待者在 wait 前向等待图登记「当前线程 → 目标装载线程」边并回溯目标线程的等待链；链回到自身 → 环命中，后到等待者抛类型化环错误（打断环上一条边，对方装载随后以失败复位收敛）。等待图与 3.2.3 的 LazyFn 编译等待共用同一登记机制（机制单点，落 ResourceCacheEntry 装载状态机文件内）。
- **有界等待兜底**：等待图回溯存在竞态漏检窗口（对方尚未登记等待边）→ 等待带上界（配置键 `nop.core.component.resource-cache.load-wait-timeout`，默认 60s；**≤0 = 不设上界档**）兜底：超时抛类型化超时错误（携带等待链快照，诊断可见）——**无永久悬挂路径**（环 → 即时类型化；漏检环 → 有界类型化；正常装载 → 有界内完成）。
- **与 R1-05 门/记忆化共存**：shouldReloadObject（:215-253）、verdict 记忆化状态（verdictCheckTime/verdictChanged）、双 mtime 守卫（executeLoadWithMtimeGuard :313-326）、loadObject 完成时记忆化重置（:303-304）全部零改动；状态机只在门判定通过后接管装载编排，门语义（deps-空豁免/maxAge/TRUE 侧限速）行为逐字节保持。`getNow()`/`getCacheEntryState()` 语义零变化。

#### 3.2.2 LazyFn 层编译单飞（LazyCompiledFunction.compile，:734）

同一不变式应用于 LazyFn 监视器：监视器内 = `compiled`/`compileException` 双检 + 「编译中」占位（同线程重入 → 类型化环错误，XLangErrors 新码）；**编译体（parseSource :798-825、runImportExprs :800、宏执行、compileTool.compileFunction、applyGeneratedClassBinding :777-784）整体移出监视器**；完成后监视器内发布 compiledFn/compileException + notifyAll。编译失败路径保持现有 compileException 记忆（:752-768 不可恢复状态防御零回退）。运行时首触（LazyCompiledExecutableFunction :80 getCompiled → getCompiledFn → compile）经单飞等待在飞编译完成；`invoke()`（:787-795）的 ERR_XPL_TAG_FUNC_IS_COMPILING 语义保持（纯执行路径不阻塞等待编译）。

#### 3.2.3 调用点边界与随案面

| 调用点 | 边界声明 |
|---|---|
| parseTag（:198-246，:210/:212 getObject + :226 lazyCompile + :232 宏立即执行） | entry 层修复后 getObject 返回即已释放 entry 监视器，:226 lazyCompile 与 :232 宏执行天然在监视器外——**零额外移位**，以运行时断言钉死（环④等效确定性断言：编译期用户代码执行时对对应 entry/LazyFn 监视器 tryLock 成功 = 未持有） |
| getFunctionModel（:651-656） | 同上：getObject 返回后 lazyCompile 在监视器外；经等待图覆盖其编译等待 |
| buildCompiledTag（:676-707，标签 entry 的 loader，:108 node 模式条目同案） | entry 层修复后 loader 本身在监视器外执行；其体为轻量构造（FunctionModel + LazyCompiledFunction 包装，无源编译无 I/O）——**随案覆盖 = 零独立改动**，由「装载体在监视器外」不变式自动覆盖 |
| ResourceComponentManager（ModelLoader :107-128） | 递归面经 entry 层状态机/环检测自动收敛，**零逻辑改动预期**（全部装载必经 entry 层，无绕过面） |
| ResourceLoadingCache（:163-167/:240-241） | 失效入口行为随 entry 层自动一致，**零逻辑改动预期**（javadoc 契约注记级） |

### 3.3 裁定③：失效/销毁时序（C2-002）

**终裁 = 标记失效 + 延迟销毁**（复用裁定①退役队列），拒绝读者登记分支（§四）。

- **两销毁点**：`clear()`（:135-148）监视器内只做 `this.object = null`（标记失效），旧对象 retire（销毁动作 = `listener.onDestroyed` + `DestroyHelper.safeDestroy`，排空期在任意 entry 监视器外执行）；`reloadObject→destroyObject`（:348-361/:363-370）同案：新对象发布（含 onCreated 回调，监视器内）后旧对象 retire。
- **两失效入口 + destroy 一致性**：ResourceLoadingCache.remove（:163-167）与 state_loadFrom putIfAbsent 失败（:240-241）均经 `entry.clear()` 到达同一延迟销毁路径；`destroy()`（:155-157）→ clear 同案。**终态为真实代码路径**（rule 16）：快路径交出的引用在宽限期内保持可用（use-after-destroy 窗消除为有界宽限），非 javadoc 声明、非 warn 日志。
- **事件顺序契约**：onDestroyed 相互保序（队列 FIFO）；同一对象 onCreated → onDestroyed 保序；onDestroyed 相对后续对象的 onCreated 不再保证即时先后（延迟 ≤ grace）——模型缓存 listener 生产面为 null 或无跨对象顺序依赖（6 直连使用方契约零变化），差异显式声明入 source-compat。
- **与裁定①同构复用声明**：销毁动作与关闭动作共用同一队列、同一 grace 键、同一排空调度缝与时间锚——退役代际族与失效链一个机制家族，行为可在三站 + 缓存销毁四站点断言中交叉验证。

### 3.4 裁定④：2.5-C6-005 双轨联动 + 测试缝

- **设施轨（裁定①产物）关闭时机契约**：缩容只退役**被裁剪尾段**连接（新数组头部元素为共享引用，仍被新代交付）；已交付在用连接自缩容时刻起存活 ≥ grace（有界自限的排空语义——共享 RoundRobin 无借还制，无法感知单次取用结束，宽限期是唯一可判定边界）；缩容后新取用只见存活代资源。参数 = 全局 grace 键（默认 5s，覆盖典型命令超时量级；部署按最大命令时延调大）。
- **使用面轨**：refreshConfig（:92-100）逻辑**零改动预期**（resize 行为随设施层自动一致）；改码面 = `getConnection`/`getAsyncCommands`/`getSyncCommands`（:61-89）javadoc 成文契约——共享连接由设施全权关闭，**调用方不得 close**（显式 close 绕过 autoReconnect 直接杀死共享连接）；`createPubSubConnection`（:140-146）为独立建连、调用方拥有、豁免于共享契约。
- **测试缝子句（随修复抽取，显式声明为 C6-005 修复面一部分）**：现状 `connectionSupplier` 字段 private、仅 doStart（:103-125）内硬构造、无 setter/工厂缝，refreshConfig 在 connectionSupplier==null 时跳过；测试类与 provider 异包（测试包 `io.nop.nosql.lettuce` vs provider 包 `io.nop.nosql.lettuce.impl`），现有测试无法注入 fake supplier。裁定：doStart 两分支（cluster :115-116 / standalone :122-123）改经 `protected` 工厂方法 `createConnectionSupplier(factory, n)` 构造；测试经子类覆写注入 fake factory/supplier，走**真实 refreshConfig→resize 链**（dockerless，无真实 redis）验证缩容排空语义。C10-026 义务：现有 testClientResourcesNotLeakedAcrossStartStop 不触发 resize（缩容修复不触红），新增缩容触发用例先红（修复前：在用共享连接被 close）后绿（延迟关闭语义、销毁时机可观测）。
- **双轨验收口径**：设施行为钉（RoundRobinSupplier 并发 shrink × get：交付连接在收缩窗口内存活过宽限、排空后关闭）+ 使用面行为钉（真实 provider refreshConfig 链：在用 fake 连接未被热关）各一，交叉验证。

### 3.5 裁定⑤：N2 钉改写契约

`TestDefaultVirtualFileSystemRefresh#testRefreshClosesOldZipOnlyAfterRebuild`（:41-68）与 C1-014 修复**同变更集**改写（同提交）。改写后断言语义：

1. **保留**：「重建期间不得提前关闭」（:65，buildResourceStore 覆盖点检查 closedDuringRebuild == false）——该断言保护的「先发布后关闭」性质在退役契约下依旧成立且加强。
2. **改写**（原 :66「refresh 返回后旧 zip 立即 closed」）：替换为退役契约的可观测等价二段断言——(a) refresh 返回时旧 zip **未**被同步关闭（`!isZipClosed(oldZip)`：已退役代宽限期内存活；修复前基线此断言红 = 立即关闭旧语义）；(b) 经注入排空调度器**确定性触发**排空后旧 zip 必然关闭（`isZipClosed(oldZip)`）。
3. **新增接线断言**：refresh 后经 getResource 读取解析到新代内容（重建真实生效，非仅句柄生命周期）。

确定性来源：注入排空调度器（手动触发收集到的排空任务）+ grace ≤0 关闭档双档（0 档退化为现行同步关闭 = 旧断言语义特例），零 sleep；线程交割断言纪律见 §六。

### 3.6 裁定⑥：共享代码位边界声明

| 前批产物 | 零回改清单 | 本批交互与回归门禁 |
|---|---|---|
| R1-01 绝缘层（plan 2496） | `InsulatedPeriodicTask`/`InsulatedPeriodicChain`/`PeriodicTaskHelper`/`IPeriodicTaskListener`、DefaultScheduledExecutor 的 destroy 拒绝路径（:120-125）/periodicTasks（:39）/trackPeriodicTask（:132-137）零改动 | C1-003 快照化只触碰七读点 + refreshConfig 同型读点；死代际类型化错误经绝缘「捕获位置」分类落入拒绝终态（§3.1.3）。门禁：`TestPeriodicTaskInsulation` 9 条 + 下游绝缘回归全绿 |
| R1-02 池设施（plan 2497） | `ExecutorHelper`（newScheduledExecutor/newThreadPoolExecutor/updateThreadPool）、`DefaultThreadPoolExecutor`、GlobalExecutors、CommonConfigs 既有键零改动；RoundRobinSupplier 的扩容失败 inline close 语义（C10-test 先例）保持 | C1-003 的 refreshConfig 快照化不改其类型化异常语义与 core-only 对齐行为；CommonConfigs 仅**新增** grace 键。门禁：C10-010 成员面（池工厂校验/热更双向）全绿 |
| R1-05 缓存门（plan 2500） | `shouldReloadObject`（:215-253）/verdict 记忆化（:64-67/:303-304）/双 mtime 守卫（:313-326）/deps-空豁免/`DefaultResourceChangeChecker` 判据链零改动 | C2-001 状态机在门判定之后接管装载编排（§3.2.1）；C2-002 延迟销毁不触碰门状态字段。门禁：CACHE-001 契约测试面 + `TestResourceCacheEntry` reload-null 占位语义钉全绿 |

**F4 时间机制声明**：本批新增时间机制 = 退役宽限期 deadline（§3.1.1）+ 装载等待上界（§3.2.1）——一律复用 `CoreMetrics.currentTimeMillis`，不引入可注入时钟；新增敏感点清单（退役队列 deadline 消费 + 等待上界判定两类消费点）随 Phase 3 R4-32 注记登记 cra-index/日志，既有敏感点（isRefreshEnabled/loadObject lastLoadTime）零改写。

**测试缝汇总**：①grace 与装载等待上界可配置且 ≤0 = 关闭档（现行同步/无界语义）；②退役队列排空调度器可注入（手动触发，零 sleep）；③`createConnectionSupplier` protected 工厂缝（裁定④）。三缝均为修复面声明部分，非仅测试便利。

### 3.7 从属编辑点枚举表（F2 单点修复位机械判定）

**机械约束声明**：机制载体（退役队列/等待图/装载状态机）为语义唯一实现位；下表全部从属编辑点只做代际登记、快照化、调用点传导与错误码机械登记，零内联第二套关闭/销毁/环判定实现。closure audit 操作化判据：①产品 diff 路径 ⊆ 下表「允许编辑」集合；②排空关闭动作与环检测裁决在载体外 grep 零命中；③七成员无第二修复位（站点层各自打补丁即违规）。测试代码不受本表约束，受 E2 同 diff 规则约束（N2 改写与 C1-014 同提交等）。

| # | 允许编辑点（文件 · 类 · 机制） | 归属裁定 | 允许的改动性质 |
|---|---|---|---|
| 1 | `io/nop/commons/concurrent/`（nop-commons）· 退役队列载体类（**新增**，类名实现期定稿）· 机制载体：retire/drain/grace/排空调度缝/时间锚 | ①③ | 新增文件；关闭/销毁动作唯一执行位 |
| 2 | `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/RoundRobinSupplier.java` · RoundRobinSupplier · 代际发布改退役登记 | ① | 收缩 toClose → 尾段 retire；close() → 整代 retire；构造失败路径维持 inline close；get :41-45 零改动 |
| 3 | `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultScheduledExecutor.java` · DefaultScheduledExecutor · 七读点快照化 + 死代际 REE 类型化 | ① | submit×2/schedule/scheduleAtFixedRate×2/scheduleWithFixedDelay/execute/stats + refreshConfig 同型读点快照化；REE→ERR_SERVICE_NOT_ACTIVE；destroy/periodicTasks/trackPeriodicTask 零改动 |
| 4 | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DefaultVirtualFileSystem.java` · DefaultVirtualFileSystem · refresh/destroy 旧代 zip 退役登记 | ①⑤ | refresh :74-77 与 destroy :87-94 的 safeCloseAll → 整代 retire；buildResourceStore 发布点形态保持 |
| 5 | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java` · ResourceCacheEntry · 装载状态机（LOADING 占位 + 监视器外装载 + 等待图环检测 + 有界等待兜底）+ clear/destroyObject 销毁延迟化 | ②③ | getObject/reloadObject 编排重构；等待图与环判定唯一实现；两销毁点 retire；R1-05 门/记忆化/mtime 守卫零回改 |
| 6 | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceLoadingCache.java` · ResourceLoadingCache · 失效入口行为随 entry 层一致 | ③ | 零逻辑改动预期（javadoc 契约注记级） |
| 7 | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java` · ModelLoader · 递归面经 entry 层收敛 | ② | 零逻辑改动预期（登记防越界：表外逻辑改动 = 违反单点纪律） |
| 8 | `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/xlib/XplLibTagCompiler.java` · LazyCompiledFunction（+XplLibTagCompiler 调用点）· compile 单飞化（双检+占位、编译体外移、同线程重入环类型化） | ② | compile :734-770 编排重构；parseTag/getFunctionModel/buildCompiledTag 随案声明（预期零移位）；invoke() is-compiling 语义保持 |
| 9 | `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/LazyCompiledExecutableFunction.java` · LazyCompiledExecutableFunction · 首触链经 LazyCompiledFunction 自动一致 | ② | 零改动预期 |
| 10 | `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java` · LettuceRedisConnectionProvider · createConnectionSupplier protected 工厂缝 + 三入口 javadoc 共享连接契约 | ④ | doStart 两分支改经工厂方法；refreshConfig 逻辑零改动预期；javadoc 成文 |
| 11 | `nop-kernel/nop-commons/src/main/java/io/nop/commons/CommonConfigs.java` · grace 配置键登记 | ①③④ | 机械登记（新增键） |
| 12 | `nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java` · 装载等待上界键登记 | ② | 机械登记（新增键） |
| 13 | `nop-kernel/nop-core/src/main/java/io/nop/core/CoreErrors.java` + `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangErrors.java` · 错误码登记 | ② | 机械登记：跨线程环/同线程环/装载等待超时（CoreErrors）+ LazyFn 编译环（XLangErrors），错误码名实现期定稿 |

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| RoundRobinSupplier.get 改锁内读取（synchronized 快照） | redis 每请求热路径加锁税；且锁内读取只消除 get 内部快照窗，不消除已交付连接的热关窗（2.5-C6-005）——单靠它修不完家族；退役机制对两窗同治，热路径零代价 |
| 代际引用计数（get 计数 / 在飞读者登记）替代宽限延迟关闭 | 共享 RoundRobin **无借还制**：get 交付后调用方持有时长对设施不可见（redis 命令时长、模型请求时长），计数永不归零或需借还 API 改造（全调用方 source-compat 破坏）；宽限期是有界自限的唯一可判定边界 |
| C1-014 取 AutoCloseZipEntryResource 全量迁移支路（按路径重开 zip） | 每次读付 zip 重开代价（模型装载热路径回归）；触达 zip store 构建链 diff 面大；退役机制与 C1-001 同机制收敛（本批目标即单机制家族收敛），重开形态归 VFS 后续治理批评估（plan Deferred 既定条款随本裁定成立） |
| C1-003 取 destroy 不置 null 形态 | 置空与快照形态对竞态修复等价，但「不置 null」需在 checkEnabled 增加 isShutdown 分支才能获得同等类型化语义（更多分支同收益）；保持置 null 使 destroy 体零改动（R1-01 交互面最小）、isDestroyed 语义零变化 |
| C1-003 仅快照不转 REE | 死代际 REE 仍是裸非类型化异常——与缺陷本体「类型化终态而非裸异常」同族残留；REE→ERR_SERVICE_NOT_ACTIVE 转换后七读点单一死代际错误 |
| entry 层装载等待者直接自旋/自装载（放弃单飞去重） | 放弃同 key 去重 = 高并发下同模型重复装载风暴（与现行 monitor 去重行为回退）；等待 + 等待图环检测 + 有界兜底三件套在保住去重的同时闭合死锁与悬挂 |
| 跨线程环仅靠有界等待超时（不做等待图环检测） | 循环依赖配置错误应即时类型化报告（「循环 xlib import 应为配置错误报告」），60s 超时才报错 = 错误延迟放大 + 超时值难以两全（正常大模型装载 vs 环检出时延）；等待图登记成本为每等待一次 O(链长) 内存操作 |
| C2-002 取读者登记（引用计数/租约）分支 | 快路径是刻意无锁热路径（gateway 每 HTTP 请求、xpl 每模板编译），逐读登记/归还为热路径加税且要求读方归还（调用方契约破坏）；延迟销毁保持快路径字节不变且与裁定①同机制 |
| C2-002 以 javadoc 契约/warn 日志声明「交出引用后勿失效」收敛 | rule 16 防假收敛：已确认 live defect 不得以声明替代修复；use-after-destroy 窗必须由时序机制真实消除 |
| C2-017 只修 entry 层（C2-001）不顺手单飞 LazyFn | 环④/环⑤残余闭合：LazyFn 监视器内完整编译（import 求值可再入模型装载）与装载发布的交叉等待仍可死锁——家族裁定同变更集硬约束 |
| invoke() 改为阻塞等待编译完成 | 运行时执行路径引入编译时长阻塞（宏/首触语义变更）；ERR_XPL_TAG_FUNC_IS_COMPILING 既有契约保持，编译等待只属编译发起路径 |
| 宽限期默认 0（保持现行即时关闭） | 0 = 缺陷形态常态化；默认 5s 才是修复，「0 关闭档」仅作确定性测试缝与显式回退通道 |
| 退役队列按站点各自实现（RoundRobin/VFS/cache 三套延迟关闭） | 平台机制单点义务：三套实现 = 三套 grace/调度/观测口径，行为无法交叉验证；单载体一裁定全族收敛 |

## 五、与已有设计的关系

- 同目录 [periodic-task-insulation.md](periodic-task-insulation.md)：R1-01 绝缘拒绝终态语义（future 有终态、不悬挂、链不复活）为 C1-003 死代际类型化的对接面（§3.1.3）；其 destroy 拒绝路径零回改。
- 同目录 [pool-facility-hardening.md](pool-facility-hardening.md)：退役队列排空任务体经 globalTimer + executeOn(globalWorker) 卸载即其调度槽契约的消费；R1-02 的 refreshConfig 类型化路径为 C1-003 快照化的邻接零回改面；RoundRobinSupplier 不在 R1-02 修复面（勘误 5），本批即该残余面主责闭环。
- 同目录 [cache-invalidation-single-point.md](cache-invalidation-single-point.md)：R1-05 门语义（verdict 记忆化/maxAge/deps-空豁免/双 mtime 守卫）零回改；本批 C2-001/C2-002 与其共享 ResourceCacheEntry 文件，交互边界见 §3.6；其裁定⑥「R2-01 锁纪律面零重构」以本设计为 successor 落地。
- **R2-04（roadmap 8.13，2.2-C1-007）**：上游发布协议归 R2-04，本批修下游取用窗，无代码依赖，Phase 5 cra-index 双行互注。
- **R4-13/R4-14（roadmap 8.53/8.54）**：G27 两束（C2-003 CHM bin 锁重计算、2.1-C6-005 平台模型缓存销毁链三断点等）依赖本批但不属本批；本批不改 LocalCache.destroy/destroyOnRemove 链（Non-Goal 防混）。
- **R4-32（墙钟批）**：本批新增时间敏感点（§3.6 F4 声明）随 Phase 3 注记并入其清单。
- **owner doc 去向预登记（Phase 4 落账）**：`docs-for-ai/04-reference/source-anchors.md`——退役代际机制锚点（RoundRobinSupplier/VFS refresh 契约/DefaultScheduledExecutor 快照纪律）+ 模型装载锁纪律锚点（三层链不变式/等待图环检测/循环依赖类型化语义）；`docs-for-ai/02-core-guides/ioc-and-config.md`——模型加载与配置刷新链并发契约节（refreshConfig→resize 链、循环依赖类型化、共享连接池缩容契约、grace/等待上界两配置键）。

## 六、七成员 → 裁定映射

| 成员（判级/台账） | 面 | 消费裁定 | 修复位（§3.7 行号） |
|---|---|---|---|
| 2.1-C1-001（P1 族主，cra-index L190，吸收 C2-021） | resize 收缩锁外关闭 × 并发 get 快照 use-after-close | ①（代际机制 + close 同族面） | #1 载体 + #2 |
| 2.1-C2-001（P1，RCE 死锁族主，cra-index L191） | entry 监视器内装载：双线程环死锁 / 单线程环 SOE | ②（装载状态机 + 等待图环检测 + 类型化收敛） | #5（#6/#7 零改动预期传导） |
| 2.1-C1-003（P2，G24） | 七读点 checkEnabled-then-use × destroy 置空 → 裸 NPE | ①（局部快照 + REE 类型化） | #3 |
| 2.1-C1-014（P2，G24，携 N2 钉） | VFS refresh/destroy 先发布后关 zip × 在飞读者 | ①（zip 整代退役）+ ⑤（N2 同变更集改写） | #4 + 测试同 diff |
| 2.1-C2-002（P2，G24；非 2.2-C2-002 防混） | 快路径无锁读 × clear/reload 锁内销毁 use-after-destroy | ③（标记失效 + 延迟销毁） | #5（销毁点）+ #1（队列复用） |
| 2.1-C2-017（P2，G24，与 C2-001 同变更集） | LazyFn 监视器内完整编译：环④/环⑤ | ②（compile 单飞 + 调用点边界 + buildCompiledTag 随案） | #8（#9 零改动预期） |
| 2.5-C6-005（P2，G48，双轨 §四#6） | refreshConfig 缩容热关已交付共享连接 | ④（设施关闭时机契约 + 使用面接入 + 测试缝） | #1/#2（设施）+ #10（使用面） |

## 七、死锁/时序测试纪律契约

本节为 Phase 2/3 死锁与线程交割测试的设计级定义（与 plan 硬门禁一致，此为内容源）：

- **夹具纪律**：先红复现用 **daemon 夹具线程** + latch 对齐（强制反序/窗口交错）+ **有界观察窗**（观察超时即判红并放行 JVM 退出，禁止无限 join 挂死构建）；或声明专用测试类 fork 策略。
- **通过形态禁令**：禁仅类型/修饰符存在断言（「synchronized 已移除」「字段已加 volatile」不构成通过形态）；每件必含修复后**可观测行为**断言。
- **修复后可观测预期（裁定①②③成文，E2 可追）**：

| 件 | 修复前红形态 | 修复后绿形态（可观测） |
|---|---|---|
| C2-001 双线程环 | 双线程相反顺序装载循环 import 模型夹具（x:config c:import 环或 xlib import 环，取审计实证路径）→ 有界观察窗内双线程 BLOCKED | 环检测即时类型化环错误（双线程各自获类型化失败，无 BLOCKED 残留、有界完成） |
| C2-001 单线程环 | 单线程 loadDslModel 循环 → StackOverflowError | 类型化环错误（无 SOE） |
| C2-017 环④ | 双线程反序（编译等待 × 装载发布交叉）→ BLOCKED；或等效确定性断言（宏标签编译期用户代码执行时对对应 entry/LazyFn 监视器 tryLock 成功 = 未持有） | 有界完成或类型化环错误；tryLock 断言持续成立 |
| C2-002 use-after-destroy | 读者持快路径交出引用窗口内触发 clear → 引用已被销毁 | 销毁延迟至读者窗口外（grace 内引用可用；确定性触发排空后销毁发生——销毁时机可观测） |
| C1-001/C6-005 交割 | 快照窗内收缩先于 get 交付 / 缩容先于命令 → 交付已关闭连接 | 交付连接存活过宽限；确定性排空后关闭（时机可观测） |
| C1-003 交割 | destroy × 提交窗口内裸 NPE | 提交成功或类型化 ERR_SERVICE_NOT_ACTIVE |
| C1-014 在飞读者 | refresh × 并发 getResource → `zip file closed` | 在飞读取宽限期内完成；排空后旧 zip 关闭 + 新读取解析新代（N2 改写断言） |
