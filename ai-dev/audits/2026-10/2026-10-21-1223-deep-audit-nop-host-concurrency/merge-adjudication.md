# 合并裁定 — nop-host（CRA WI 4.4，C1+C4+C6 三维度）

- 裁定日期：2026-10-21（独立合并裁定员；C10 审计与合并裁定合并任务的单次派发，先 C10 后裁定）
- 输入：三维度 `## 最终保留项` + `## 维度复核结论`（19 条复核保留：P0×0/P1×4/P2×13/P3×2）+ CL×8 + `ai-dev/audits/cra-index.md`（P1 台账 152 条口径全表查重）+ 本目录 `C10-test-protection.md`
- 裁定纪律：未读其他单元审计目录详情；复核裁定（降档/升格/家族登记）作为既定输入采纳不重开；同族相邻按「根因 + 失效层」合并；MA 编号按本单元从 MA-01 起

## 一、口径

1. **三态判定**：19 条逐条归入「缺陷族工单 / 独立工单 / watch-only」，逐条落位无遗漏（权威表见 §六）；族合并门槛 = 同根因 + 同失效层 + 修复批可同 diff/同批；仅同根因不同失效层的登记家族关联单列，不并工单。
2. **复核输入采纳**（5 项，全部采纳）：C1-002 降档 P2（触发链载体归因订正：ContextTaskQueue 无池）；C1-006 升格 P1（双宿主空链固化机制闭合）；C6-007 登记 future 永不完成族第 5 例（平台根因单列移交）；C6-008 登记错误以成功交付族传输层成员；C4-001 ↔ WI 4.3 MA-03 同族异站关联。
3. **判级继承**：工单判级 = 族内最高成员判级；复核后的 P1×4/P2×13/P3×2 不在合并层重判（判级冲突仅 §7.1 两项）。
4. **CL 处置口径**：并入工单 / 独立线索 / watch / 驳回 四态；CL-005 允许拆分处置。
5. **C10 缺保护登记（19 条）随本体映射**，不单独立项（§九）；反向钉死 4 处作为修复批前置约束随工单携带。
6. **零重复纪律**：cra-index P1 台账全表查重（§七），本单元 19 条零同站点重复声明成立；跨单元同族只单列关联，不重复立项。

## 二、缺陷族（MA-01 起，5 族吸收 7 条）

### MA-01 启动窗口请求面就绪门禁缺失 + 快照固化族（双宿主）

- **主登记**：nop-host-C1-006（P1，升格采纳）——getFilters synchronized 惰性缓存把启动窗口空链永久固化，认证链（AuthHttpServerFilter 等 nop-IoC bean）静默旁路直至重启；变体 B 双宿主成立（IocCoreInitializer 覆盖 facade 窗口横跨 NopApplication.run 秒级）。
- **成员**：nop-host-C4-003（P2）——同窗口请求面无就绪门禁（就绪探针存在但不被请求路径消费）；Quarkus 认证请求后果为悬挂（C6-007 (b) 面）而非 500。
- **并族依据**：同根因（宿主回调/入口对平台初始化态的读取无门禁，`CoreInitialization.isInitialized()` 已有状态未被消费）+ 同失效层（请求面、同启动窗口、filter 链相邻站点）+ 同修复原语（门禁 + 初始化完成后缓存重算）。C1-006 的固化是持久子型，C4-003 的 500/悬挂是瞬态子型，同一 PR 面。
- **修复批约束**：门禁失败必须 503/fail-fast 短路于 runOnContext **之外**——若门禁异常落入 C6-007 的 task 通道将恶化为悬挂（§7.1-3 联动裁定）；R2 负结果记录：QuarkusIntegration.start 本体微秒级无独立阻塞面，不另立。
- **工单判级 P1**；C10 随案 C10-006/C10-009。

### MA-02 future 永不完成族 · 宿主站点面（跨单元家族，本单元成员 1 条）

- **主登记**：nop-host-C6-007（P1）——withRoutingContext 将 `task.get()` 与 bindResult 合并为一个表达式，task supplier 同步异常逃出 lambda 被 ContextTaskQueue catch-Throwable-仅记日志吞掉，返回 RESTEasy 的 future 永不完成，畸形 JSON 即稳定制造悬挂连接（用户可控、无 5xx、无请求级日志）。
- **家族台账**：族第 5 例（先例 WI 4.2 C3-001 重试回调吞没 / WI 4.3 C3-001 OkHttp onResponse 吞没〔族第 4 例〕/ WI 3.2 C1-001 executeNow.get 同步抛出〔机制同构〕/ WI 1.7 C6-001 Error 逃逸；返回 future ≠ 挂接 future 变体 WI 1.6 C8-012）。
- **平台根因单列（不在本单元重复立项）**：ContextTaskQueue 吞异常语义 → WI 2.3 CL-018（ContextTaskQueue 承重原语，归 nop-kernel 台账）+ WI 2.1 C3-001（周期任务异常绝缘层，29 处生产站点统一）。本工单仅承担宿主层修复（try/catch → completeExceptionally，不动平台）；平台侧修复与族同批对齐（台账惯例）。
- **工单判级 P1**；C10 随案 C10-017（强制）。

### MA-03 错误以成功交付族 · servlet 传输层成员（跨单元家族，本单元成员 1 条）

- **主登记**：nop-host-C6-008（P2）——servlet 流式错误路径 `asyncCtx.complete()` 而非 `completeWithError(error)`：chunked 终止块被容器补齐，截断响应以完整 200 交付，消费端无法区分流结束与流故障（F2「半个成功」传输层投影）；vertx 孪生 `resp.reset()` 为正确对照形态。
- **家族台账**：先例 WI 3.4 C3-005（P1，升格案）/ WI 3.1 C7-002（P1）/ WI 4.3 C3-005（P1）。本成员为传输层（协议级截断）而非语义层（应用折叠）——判级 P2 维持，**家族判级对齐留 §7.1-1**。
- **工单判级 P2**；C10 随案 C10-018。

### MA-04 平台全局态与宿主生命周期绑定族（镜像对，双宿主）

- **主登记**：nop-host-C6-003（P2，复核补证）——Spring onClose 无条件 destroy 无归属守卫：多 Spring 上下文共 JVM 时先关闭者拆掉在役上下文的地板（initializers 链 + GlobalCacheRegistry + ReflectionManager + BeanContainer facade 回写宿主桥）。
- **成员**：nop-host-C6-002（P2）——Quarkus 集成只 start 不 stop：destroy 责任下放各 app main 自觉，dev 热重载残留无清理。
- **并族依据**：初审明确二者互为镜像、同根因（平台全局态与宿主生命周期无绑定，注册侧有覆盖语义而 destroy 侧无引用计数/归属校验）；合并裁定按「根因 + 修复批」并单工单——同一设计裁定（归属守卫 + Quarkus stop/ShutdownEvent 配对）一次落地，F8 双宿主对称修复。
- **工单判级 P2**；C10 随案 C10-012/C10-013。

### MA-05 上下文传播缺失族 · 宿主站点面（跨单元家族，本单元成员 1 条）

- **主登记**：nop-host-C1-002（P2，降档采纳）——g_serverContext ThreadLocal 仅在事件循环线程 set/remove，runOnContext 分支既不重设也不经参数传递，消费端 null-check 静默降级 emptyMap；匿名/认证请求分支语义非确定。降档事由采纳：BaseContext 实为 api-core ContextTaskQueue（无 StandardThreadPoolExecutor 池），仅 syncing>0 时移交他线程，多线程 drain 前提在 11 根内无已证调用链；传播缺失修复义务保留。
- **家族台账**：同族异站——WI 2.3 C3-007（BizActionService 裸 executor IContext 不传播）、WI 2.3 C3-002（invokeOperation 出口泵缺 thenOnContext）。家族修复（统一传播原语）归 2.3 轨道，本工单仅宿主站点。
- **工单判级 P2**；C10 随案 C10-002。

## 三、独立发现（11 条）

| 独立工单（主 finding ID） | 判级 | 一句话 | 修复批/关联注记 |
|---|---|---|---|
| nop-host-C1-001 | **P1** | WebClientHolder 凭据对（cookie+authorization）跨线程无同步非原子双写，撕裂对驻留致认证循环失败 | 吸收 CL-004（同文件 SpringWebProxy：refresh 加 timeout + refreshAuthUrl 空时 Mono.error 终止重试）；平台 volatile 惯例对照 JsonRpcWebSocketHandler.authHeaders |
| nop-host-C1-003 | P2 | 两宿主 WS session 门面 basicRemote 非线程安全且无串行化，keepalive/订阅/onMessage 三类写线程交叠丢帧 | 跨单元注记：WI 2.3 C8-001 同组件（JsonRpcWebSocketHandler 发送面）异机制（阻塞钉死 globalTimer），异工单不重复 |
| nop-host-C1-004 | P2 | 懒初始化 check-then-put 无同步族（事务工厂 dialect + MCP callbacks），11 根全族穷举仅此两处未守卫 | 最小修复 volatile；良性约束：TestNopSpringTransactionFactory 惰性 pin 禁止急切化 |
| nop-host-C4-001 | P2 | 过滤链 syncGet 阻塞 servlet 工作线程独占至流结束（futureCall 内联已纠偏，非隐藏池） | 跨单元家族关联：WI 4.3 MA-03（sidecar get(30s) 阻塞 reactor 线程）同族异站异机制——家族判级口径统一留 §7.1-2 |
| nop-host-C4-002 | P2 | WS keepalive 构造即调度共享 globalTimer，装配滞留窗口（onOpen 中途抛错）致任务永久滞留+session 引用持有 | **吸收 CL-007**（Quarkus onOpen userProperties 无引用同一时序：tryGet 对齐 + 构造处 try/catch-cancel 兜底）；C10 前置：RP-2/RP-4（测试须先补抛错路径与取消断言） |
| nop-host-C4-004 | P2 | spring-delta 两注册回调（registrar + mybatis customizer，F3 孪生）在平台初始化前触达 ModuleManager，标准路径静默空发现 | 与 MA-01 同门禁原语但失效层不同（装配面 refresh 期 vs 请求面）→ 独立工单 + 家族关联单列；C10 前置：RP-3（须新增不预初始化测试上下文） |
| nop-host-C6-001 | **P1** | 上传流「先校验后消费」失败路径永不关闭——每被拒上传泄漏 1 个 fd，外部可稳定触发 | 跨单元注记：WI 4.3 C6-005（BASE64 上传）异站点不重复计数；平台侧 NopDatavExportTaskBizModel / ReportDeliveryExecutor 的 UploadRequestBean 构造点为同族提示**未逐验**（登记待验，不立项）；C10 先例：WI 4.3 TestFileDownloadSinkClose fd 计数形态 |
| nop-host-C6-004 | P2 | servlet startAsync 无 setTimeout 无 AsyncListener：长流被容器默认超时无提示截断 + 二次 ISE 噪声 | **吸收 CL-005(b)**（quarkus filter future 无超时同批对齐）；家族关联 WI 3.1 C7-001（流式聚合无全程期限）异站零重复；搭车裁定：C1-005 volatile 修复随本工单同文件落地 |
| nop-host-C6-005 | P2 | 数据源重建后 17 个 Gauge 仍绑旧池（Micrometer register 幂等返回旧表），可观测性静默失效 | 修复批注：与 MA-04/C6-002 同验证场景（热重载）不同修复（判存 remove / AtomicReference 重绑）；WI 2.5「C6-005 双轨」为本站独立（同名异站） |
| nop-host-C6-006 | P3 | ResponseOutputStream.write(int) 空实现吞字节（当前调用面安全，防御性） | 一行修复（appendByte）；与 C6-009 同文件批可并行 |
| nop-host-C6-009 | P3 | IHttpServerContext.sendResponse(int, InputStream) 释放契约未声明、两实现语义漂移（当前零调用面，潜伏） | **Protected Area 约束：修复触碰 nop-http-api 接口叶（javadoc/契约）→ plan-first**（对照 WI 4.3 三 api 叶先例）；建议契约"实现负责关闭 body"，两实现 F3 同修 |

## 四、watch-only（1 条）

- **nop-host-C1-005（P2 理论风险）**：ServletHttpServerContext.context/characterEncoding、VertxHttpServerContext.responseSent 跨线程读无 volatile——复核维持"理论风险，未具体化"（主执行流读写收敛于同一线程归属，11 根内无跨线程读调用实例）。
- **watch 理由**：无在库触发路径、修复为 volatile 一行 + 契约 javadoc，不满足独立工单门槛。
- **搭车裁定（闭合）**：volatile 修复随 nop-host-C6-004 工单同文件批落地（ServletHttpServerContext 同文件；VertxHttpServerContext 随 CL-008 线索批）；不单独立项、不进 Phase 8 批量修复清单；若宿主外出现异步访问 IHttpServerContext 的新调用方（nop-gateway 扩展面），回升 P2 立项。

## 五、开放裁定（7.1 议程，3 项）

1. **MA-03 家族判级对齐（C6-008）**：错误以成功交付族先例 3 例全 P1，本单元传输层成员 P2。传输层（协议级截断可见）与语义层（应用折叠 200）的判级口径需 7.1 统一——若随族升 P1，GatewayHttpFilter 经此路径的网关部署面是升格论据。
2. **C4-001 并入 WI 4.3 MA-03 家族判级议程**：4.3 MA-03 已留"apache P1 / jdk,okhttp P2"双判级；本单元 servlet 工作线程载体 P2（阻塞时长无上界但按部署是否用流式条件化）。三载体（reactor 回调线程 / servlet 工作线程）家族判级口径 7.1 一并终裁。
3. **MA-01 × MA-02 修复顺序与联动设计**：C1-006/C4-003 门禁若以抛错实现，Quarkus 认证请求的异常将落入 C6-007 的 task 通道恶化为悬挂（`getBeanByType(IGraphQLEngine)` 抛出面即 C6-007 失效面 (b)）。7.1 须裁定：门禁 503 短路位置（runOnContext 之外）+ MA-02 宿主修复（completeExceptionally）作为 MA-01 的同批前置/联动项，避免门禁修复单独上线时恶化启动窗口行为。

## 六、权威汇总表（19 条逐条落位，无遗漏）

| # | 编号 | 标题 | 判级 | 落位 | 工单判级 | 跨单元关联 | C10 随案 |
|---|---|---|---|---|---|---|---|
| 1 | C1-001 | WebClientHolder 凭据撕裂 | P1 | 独立 | P1 | — | C10-001（强制） |
| 2 | C1-002 | g_serverContext 不传播+静默降级 | P2（降自 P1） | MA-05 主 | P2 | 2.3 C3-007/C3-002 族 | C10-002 |
| 3 | C1-003 | WS basicRemote 并发写无串行化 | P2 | 独立 | P2 | 2.3 C8-001 注记 | C10-003 |
| 4 | C1-004 | 懒初始化 check-then-put 族 | P2 | 独立 | P2 | — | C10-004 |
| 5 | C1-005 | 上下文字段无 volatile（理论） | P2 | **watch** | — | — | C10-005（搭车 C6-004） |
| 6 | C1-006 | 启动窗口空链固化（认证旁路） | P1（升自 P2） | MA-01 主 | P1 | — | C10-006（强制） |
| 7 | C4-001 | syncGet 阻塞 servlet 工作线程 | P2 | 独立 | P2 | 4.3 MA-03 族（§7.1-2） | C10-007 |
| 8 | C4-002 | keepalive 共享 timer 滞留窗口 | P2 | 独立（吸收 CL-007） | P2 | — | C10-008 |
| 9 | C4-003 | 启动窗口无就绪门禁 | P2 | MA-01 成员 | P1 | C6-007 (b) 面联动（§7.1-3） | C10-009 |
| 10 | C4-004 | 注册回调静默空发现 | P2 | 独立 | P2 | MA-01 门禁原语关联 | C10-010 |
| 11 | C6-001 | 上传流校验失败不关闭 | P1 | 独立 | P1 | 4.3 C6-005 异站提示 | C10-011（强制） |
| 12 | C6-002 | Quarkus 只 start 不 stop | P2 | MA-04 成员 | P2 | — | C10-012 |
| 13 | C6-003 | destroy 无归属守卫 | P2 | MA-04 主 | P2 | — | C10-013 |
| 14 | C6-004 | startAsync 无超时无监听 | P2 | 独立（吸收 CL-005b、搭车 C1-005） | P2 | 3.1 C7-001 族异站 | C10-014 |
| 15 | C6-005 | 数据源 Gauge 旧绑定 | P2 | 独立 | P2 | 2.5 双轨口径（同名异站） | C10-015 |
| 16 | C6-006 | write(int) 吞字节 | P3 | 独立 | P3 | — | C10-016 |
| 17 | C6-007 | 同步异常被吞→请求悬挂 | P1 | MA-02 主 | P1 | future 族第 5 例 + 2.3 CL-018 + 2.1 C3-001 单列 | C10-017（强制） |
| 18 | C6-008 | complete() 洗白截断响应 | P2 | MA-03 主 | P2 | 错误交付族传输层成员（§7.1-1） | C10-018 |
| 19 | C6-009 | sendResponse 流契约未声明 | P3 | 独立 | P3 | —（Protected Area 约束） | C10-019 |

**合并统计**：19 条 → **16 工单 + 1 watch**（5 缺陷族工单吸收 7 条 + 11 独立工单）；工单判级 **P0×0（显式空执行）/ P1×4 / P2×10 / P3×2** + watch×1；吸收（族内成员）7 条、独立 11 条、watch 1 条。台账口径：P1 台账新增 4 行建议（nop-host-C1-001 / nop-host-C1-006（MA-01 主）/ nop-host-C6-001 / nop-host-C6-007（MA-02 主））。

## 七、跨单元对账

1. **cra-index 查重（零同站点重复声明）**：P1 台账（152 条口径）全表逐行核对——nop-host/nop-spring/nop-quarkus 单元零先期登记（dispatch-log 交叉印证：宿主并发主题直接审计 0 命中）；19 条保留发现零同站点重复。本单元为该 campaign 宿主集成层首批并发登记。
2. **家族关联单列清单（8 处，零重复立项）**：
   - C6-007 → future 永不完成族第 5 例（4.2 C3-001 / 4.3 C3-001 / 3.2 C1-001 / 1.7 C6-001；变体 1.6 C8-012）；平台根因 ContextTaskQueue 单列移交 **2.3 CL-018 + 2.1 C3-001**（绝缘层统一批），本单元仅宿主站点修复。
   - C6-008 → 错误以成功交付族传输层成员（3.4 C3-005 / 3.1 C7-002 / 4.3 C3-005）。
   - C4-001 → **4.3 MA-03**（nop-network-integration-C3-004）同族异站异机制，判级议程并档（§7.1-2）。
   - C1-002 → 上下文传播族（2.3 C3-007 / 2.3 C3-002）同族异站，家族修复归 2.3 轨道。
   - C6-004 → 3.1 C7-001 流式全程期限族，异站零重复。
   - C1-003 → 2.3 C8-001 同组件异机制注记（JsonRpcWebSocketHandler 发送面），异工单。
   - C6-001 → 4.3 C6-005 异站点（BASE64 上传）不重复计数；平台侧两 UploadRequestBean 构造点登记待验提示（不立项）。
   - C6-005 → 2.5「C6-005 双轨」同名异站独立维持。
3. **漂移复查闭环**：WI 2.1 CL-021（raw 线程点名）宿主零载体（C4a 基线 + R2 生命周期回调全表复现），预注"不含宿主"成立；WI 4.3 MA-02（JDK 取消语义）宿主无 cancelToken 绑定面，无同案。
4. **Protected Area 触碰申报**：nop-host-C6-009 修复触碰 `nop-http-api`（nop-*-api 公共接口叶，javadoc 契约声明）→ **plan-first**，立项时随 migration note；其余 15 工单 + watch 均限于宿主 impl 叶与 nop-graphql 既有 handler，不触 api 叶。
5. **索引回填建议**（本裁定不直接改 cra-index，随 WI 4.4 收尾任务执行）：报告清单增 WI 4.4 行 + P1 表增 4 行 + 本目录纳入报告清单。

## 八、CL 处置（8 条全落位：并入工单 3 / 独立线索 5 / watch 1 / 驳回 0；CL-005 拆分）

| CL | 内容 | 处置 | 落点 |
|---|---|---|---|
| CL-001 | computeIfAbsent 锁内 I/O+YAML 解析、无负缓存 | 独立线索（C2/C9 面） | 关联 1.2 MA-04（Avro computeIfAbsent 锁内 HTTP）同族异站 |
| CL-002 | 静态资源每请求 exists() 探测无缓存 | 独立线索（C9，P3 候选） | 启动期路径集缓存建议随线索 |
| CL-003 | getRequestBody 全量物化无上界 | 独立线索（C5，超声明包络 OOM 候选） | Content-Length 上限 + 平台配置 |
| CL-004 | refresh 无超时 + refreshAuthUrl 空时必败重试 | **并入 C1-001 工单同 diff**（同文件 SpringWebProxy） | timeout + Mono.error 终止重试 |
| CL-005 | proceedAsync 双处理器幂等冗余 + quarkus filter future 无超时 | **拆分**：(a) watch 线索 P3（幂等冗余，防御性收敛可选）；(b) **并入 C6-004 工单**（超时策略 quarkus 侧对齐） | (a) watch；(b) C6-004 |
| CL-006 | webClients 常驻缓存无失效/TTL 通道 | 独立线索（C7 陈旧态，P3 候选） | ICache TTL / reload 端点 |
| CL-007 | WS 双回调幂等 + Quarkus onOpen userProperties 无引用 | **并入 C4-002 工单**（同一时序：tryGet 对齐 + 装配失败兜底清理） | C4-002 |
| CL-008 | isResponseSent 两宿主语义不对称 | 独立线索（C3，P3 候选） | 修复受 **RP-1 反向钉死约束**：改查询 ended()/closed() 形态须同 diff 更新 TestVertxHttpServerContextAdapter |

## 九、C10 缺保护登记映射（19 条随本体）

| C10 登记 | 判级 | 随案工单 | 修复批前置约束 |
|---|---|---|---|
| C10-001（强制） | P2 | nop-host-C1-001 | 平台 TestContextTaskQueue 锁冻结先例复用 |
| C10-002 | P2 | MA-05 | — |
| C10-003 | P2 | nop-host-C1-003 | 两宿主孪生同测（F3） |
| C10-004 | P2 | nop-host-C1-004 | 惰性 pin 良性约束保留 |
| C10-005 | P3 | watch C1-005（搭车 C6-004） | — |
| C10-006（强制） | P2 | MA-01 | 门禁/固化解除双用例 |
| C10-007 | P2 | nop-host-C4-001 | 同步路径零成本回归钉 |
| C10-008 | P2 | nop-host-C4-002（含 CL-007） | **RP-2/RP-4：stub 须可抛错 + 取消断言先于修复** |
| C10-009 | P2 | MA-01 | — |
| C10-010 | P2 | nop-host-C4-004 | **RP-3：须新增不预初始化测试上下文**（门禁与 initializeTo 部分级兼容已核：CoreInitialization.java:118） |
| C10-011（强制） | P2 | nop-host-C6-001 | fd 计数形态（WI 4.3 TestFileDownloadSinkClose 先例） |
| C10-012 | P2 | MA-04 | 结构对称性测试即可 |
| C10-013 | P2 | MA-04 | 多上下文拆毁场景 |
| C10-014 | P2 | nop-host-C6-004 | stub AsyncContext 断言 setTimeout/addListener |
| C10-015 | P2 | nop-host-C6-005 | — |
| C10-016 | P3 | nop-host-C6-006 | 一行往返 |
| C10-017（强制） | P2 | MA-02 | future.get(timeout) 断言悬挂消除；平台吞异常通道用例归 2.3 CL-018 面 |
| C10-018 | P2 | MA-03 | — |
| C10-019 | P3 | nop-host-C6-009 | 随 plan-first 修复批 |

**反向钉死总约束**：4 处（RP-1→CL-008 线索；RP-2/RP-4→C4-002 工单；RP-3→C4-004 工单）——相应修复 diff 必须先改/同改测试，禁止修码留钉。
