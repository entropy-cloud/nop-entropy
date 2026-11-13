# 单元内合并裁定 — nop-network-integration（WI 4.3）

- 裁定人：WI 4.3 独立合并裁定员（与三维度初审/复核均不同 task）
- 日期：2026-10-21
- 输入基线：三维度复核后最终保留 74 条（P0×1/P1×21/P2×39/P3×13）+ 同案并入 7 条计入主登记 + CL×34 + C10 缺保护登记 15 条
- 结论速览：**74 条 → 58 张工单（17 缺陷族 MA-01…17 + 41 独立）+ 1 条 watch-only（C1-020）**；工单判级分布 **P0×1 / P1×15 / P2×32 / P3×10**；7 条并入登记全部落进缺陷族工单；CL×34 全落位（并入工单 7 / 重复合并 10 / 独立线索 12 / 移交 5 / 驳回 0）；跨单元关联 10 处单列零同站点重复；判级分歧 3 处留 7.1（主登记结构已由本级终裁）+ 本级新增裁定 9 处。

---

## 一、裁定口径与输入

### 1.1 三态判定规则

每条保留发现恰好落入以下一处，不得重复计数：

1. **缺陷族**：≥2 条发现同根因且同修复点，合并为一张工单（MA-NN）。复核已登记的「并入」条目（非证伪、本体成立、防重复计数）计入其主登记所在工单，不占独立席位。
2. **独立立案**：单独成一张工单（工单键 = 本体 ID）。同文件/同批次的协同关系以「修复批约束」注记，不改变工单独立性。
3. **watch-only**：登记观察、不立修复工单，须附升格条件。

工单判级取成员最高严重度（MA-03 双判级单列口径除外）；成员自身判级保留备查，不因合并抹平。

### 1.2 复核已登记裁定（作为已定事实输入，冲突时显式裁定）

- **同站点并入/合并 8 组**：C6-008→C1-002/C3-008（token 委派三登记）、C6-009→C3-006、C6-014→C1-001、C1-004→C3-010、C6-020⊂C1-008/C3-018（MQTT 三登记一工单 + C1-008 失效面①证伪修正）、C6-026①→C3-020、C1-015↔C3-031、C1-014↔C6-CL-027。
- **同根同批 2 组**：C1-013↔C6-022、C6-007↔C6-025。
- **判级分歧 3 处留 7.1**：C1-009(P1)↔C3-017(P2)、C6-021(P1)↔C3-016(P2)、C6-024 升格 P1 与 C3-015 对齐。
- **单元唯一 P0 = C6-001**（下载句柄泄漏，与 C3-005 同批设计、不同本体）。
- **跨单元既定口径**：future 永不完成族第 4 例（C3-001）、错误以成功交付族第 4 例（C3-005）、globalTimer 族不涉、WI 2.1 CL-021 feishu 点名（C1-011/C6-024 载体行为面链接）、WI 3.1 消费面回写（修复两侧同批）——家族关联单列、零同站点重复。

### 1.3 输入清单

| 输入 | 用途 |
|---|---|
| `C1-shared-mutable-state.md` §维度复核结论 + §最终保留项（19 条） | C1 侧主输入 |
| `C3-async-chains.md` §维度复核结论 + §最终保留项（30 条） | C3 侧主输入 |
| `C6-resource-lifecycle.md` §维度复核结论 + §最终保留项（25 条） | C6 侧主输入 |
| `C1-round2.md` / `C3-round2.md` / `C6-round2.md` | C1-018/019/020、C3-033、C6-028 条目本体与复核状态行；CL-033/CL-034 |
| `C10-test-protection.md` §缺保护登记 + §C10 复核结论（含修正 A1/A2、主从声明 M1、勘误 R1–R5） | §九 映射输入 |
| `ai-dev/audits/cra-index.md` P1 台账（WI 1.1–4.2 累计 137 条）+ CL 台账 | 跨单元查重与家族对账 |

不读其他单元审计目录详情；跨单元事实一律以 cra-index 台账与三维度复核的回写核对节为准。

---

## 二、缺陷族结构（编号 MA-01 起）

### P1 族（7 张）

**MA-01 MQTT 会话订阅竞态三登记（P1）**
- 成员：C1-008（主登记）+ C3-018 + C6-020（独有窗口①随同 diff）。
- 根因：`MqttSessionManager` 清理粒度（按 clientId 整组）与所有权（per-connection 登记）不匹配——`removeConnection` L44 无条件清空订阅 + `addConnection` L36 接管删除在 CONNACK 先于会话注册的顺序窗口下同样命中新连接。
- 修复点：订阅登记与连接属主绑定（对齐 `sessions.remove(key, value)` 两参守卫），删除前校验属主；`acceptEndpoint` 调整为先注册会话再 `endpoint.accept()` 消除窗口①。单 diff 覆盖双窗口。
- 本级修正落账：**C1-008 失效面①证伪修正**——C6-020① 前提下「L36 语义正确」不成立，随本工单失效面枚举修正（依据 C6-020 复核状态行）。
- 注记：休眠面激活回升条款留 7.1（vertx-mqtt 零装配，见 §五-3）；同族前案 check/nosql-cdc 2026-08-19 与 WI 2.5 MA-01（PubSub 侧兄弟，非同站点）。

**MA-02 JDK 响应体取消/遗弃族（P1）**
- 成员：C3-002（主登记，live 默认 client）+ C3-003 + C6-002（其② 并入 C1-CL-001，闭环）。
- 根因：JdkHttpClient 将取消/非正常出口映射为 `future.cancel` 或直接返回，对阻塞中的响应体读取无关闭/中断效果——下载泵（幽灵下载）、SSE readLine（线程按次泄漏）、416 重试与 SSE cancel-at-entry（响应体遗弃、连接不归还）四个出口同构。
- 修复点：响应体生命周期收敛——cancel/416/终态三出口统一 close + 可中断读，同一修复机制覆盖全部出口。
- 吸收线索：CL-026（下载 body 无超时、cancel 不可中断——jdk 泵面随同 diff）；C1-CL-001（已并入 C6-002②）。
- 注记：WI 3.1 C7-001 消费面回写本体落点，修复两侧同批（聚合层期限 + 传输层读超时/可中断）；WI 2.3 CL-025 实现面承接。

**MA-03 sidecar 探测回调线程阻塞（P1，双判级单工单）**
- 成员：C3-004（主登记；P1 apache 面 / P2 jdk+okhttp 面）+ C6-006。
- 根因：下载完成回调内同步 `get(30s)×2` 发起同客户端嵌套请求——Apache 阻塞共享 reactor 线程、JDK/OkHttp 占用回调池；超时后内层 future 不取消。
- 修复点：sidecar 探测移出回调线程（非阻塞链式 whenCompose）+ 超时后内层取消。三实现同 diff。
- 吸收线索：CL-021（ioThreadCount=1 自死锁配置面随同工单收敛）、C1-CL-002（复核已闭环）。
- 判级口径：P1（apache）/P2（jdk,okhttp）双判级随工单走，7.1 确认（§五-4）。

**MA-04 feishu send 无超时单线程 scheduler 阻塞死锁（P1）**
- 成员：C3-015（主登记）+ C6-024（复核升格 P1 对齐，已定）。
- 根因：全部 WS 写（心跳/握手/ACK）经 `sendBinary(...).get()` 无超时阻塞，跑在单线程 scheduler 上——半开连接即整体死锁，心跳失败且不喂重连。
- 修复点：send/close 加超时 + 失败主动 close 并回调 onError 驱动重连；connect 的 `join()` 同步等待同批移出 scheduler（非阻塞链式回投）。
- 吸收线索：CL-005（含 C6-round2 扩展的 SocketClient 心跳同族佐证——同批覆盖注记）、CL-006（connect join 面）。
- 注记：WI 2.1 CL-021 feishu raw 线程载体行为面链接（先平台设施后使用方）；feishu 链激活回升条款留 7.1。

**MA-05 feishu 重连世代守卫（判级分歧，暂计 P1，留 7.1）**
- 成员：C1-009（主登记）+ C3-017。
- 根因：`attemptReconnect` 无单飞/connected/代际守卫——onClose/onError 双入口双调度 + 重启窗口陈旧回调 → 并行双活 WebSocket、消息双份消费。
- 修复点：重连代际令牌 + connected 守卫单点收敛（两登记同站点同修复点，本体无分歧、纯校准分歧）。
- **本级结构终裁**：主登记 = C1-009，合并单工单（采纳 C3 复核倾向）。前提(a)（onError→onClose 双终态序列，JDK WebSocket 契约两者互斥）的复核义务随本工单由 C1 侧回码执行——其结论只影响失效面枚举，不影响工单存在性：触发(b)（stop→start 重启窗口陈旧回调）经双方回码独立扎实，单独支撑双活后果 + 实仓消费方 FeishuConnector 可达 + C10-005 假体掩蔽三重证据。判级（P1 vs P2）仍留 7.1。

**MA-06 feishu stop×在途 connect 僵尸连接（判级分歧，暂计 P1，留 7.1）**
- 成员：C6-021（主登记）+ C3-016。
- 根因：`stop()` 无法取消在途 connect，完成回调无代际校验——stop 之后新建的 WS 孤儿连接无人关闭、listener 复活 connected 状态。
- 修复点：connect 代际校验（完成时核对世代，过期即关闭）+ 在途 connect 跟踪取消。本体与修复点无分歧（C3 复核确认），合并单工单，判级留 7.1。
- 注记：feishu 链激活回升条款留 7.1；与 MA-05 同文件相邻但修复点互异，不并族。

**MA-07 SimpleRpcClientFactory.init 非幂等（P1）**
- 成员：C1-001（主登记）+ C6-014（并入，每次 newInstance 重复 prepend）。
- 根因：init() 无幂等守卫——每次 newInstance 重复前插拦截器（共享列表原地变更）+ timer/executor 检查-创建竞态。
- 修复点：幂等守卫单点（已 init 检查 + timer/executor 原子创建）。
- 吸收线索：CL-015 的 timer 双建面（与 C1-001 检查-创建竞态同点，并入注记）。
- 注记：WI 2.1 CL-024 站点邻接（非同缺陷，C1 复核已辨）；nop-rpc-simple 测试空白面见 C10-008。

### P2 族（9 张）

**MA-08 oauth enhancer 锁内 IO + 增强覆盖缺口（P2）**
- 成员：C3-008（工单主键）+ C1-002（并列本体面：锁内 token HTTP 往返）+ C6-008（并入，含条目无配置移除清理 P3 子面随同 diff）。
- 根因：`AddAccessTokenHttpClientEnhancer` 双缺陷同站点——(a) `synchronized(authTokens)` 监视器内同步 token HTTP 往返（跨 provider 全局串行化）；(b) EnhancedClient 仅覆盖 onFetchBegin，download/upload/SSE 三入口完全绕过 token 注入（round2 失效面修正 1→3）。
- 修复点：锁粒度收敛（分 provider 锁/IO 移出锁）+ onRequestBegin 统一注入或四 onXxxBegin 全覆盖。同文件单 diff 单元。
- **本级裁定**：工单主键 = C3-008（承接 C1 复核「C1-002 并入 C3-008 修复单元」表述；C10-009 的「双主登记」按本裁定收敛为单工单双面注记）。

**MA-09 MqttServerMessageService dispatcher 注册配对（P2）**
- 成员：C1-012（主登记）+ C6-026（①并入 C3-020→本工单；②独立成立、随同 diff）+ C3-020（并入，正向双挂载子面并入本工单失效面）。
- 根因：dispatcher 挂载/摘除以「subscribers map 是否为空」兼作跨结构注册标志——check-then-act 重复 addPublishListener（双份分发）+ computeIfPresent 恒返回列表致摘除分支死码（注册表对称性破损）。
- 修复点：注册标志改 AtomicBoolean CAS + 空列表时 `remove(topic, list)` 真移除；对照 RpcMessageSubscriptions putIfAbsent/cancel-remove 正确先例。单 diff 覆盖 (a)(b)(c-) 全部面。
- 本级勘误落账：C1-012 子项(c) 死码勘误（其前提与 C6-026② 代码事实矛盾）随本工单失效面修正。
- 注记：C6-026② 的本体独立性按 C6 复核登记保持，计数归本工单不另立（§五-5）；跨单元族链 WI 4.1 MA-05。

**MA-10 NettyTcpClient stop×reconnect 竞态（P2）**
- 成员：C1-013（主登记，isStopping() 谓词错位）+ C6-022（stop 与在途重连交错、channel 复活 + connectFuture 停机后覆写）。
- 根因：stop 状态与重连生命周期无互斥/世代守卫——同一缺口的两种交错（预判复核已裁「同根不同交错」）。
- 修复点：stop 世代守卫单点覆盖「已调度重连」与「在途连接建立」两类复活路径。单工单双交错面。

**MA-11 feishu bind ticket 单消费 TOCTOU（P2）**
- 成员：C1-015（主登记）+ C3-031（并入，证据滞后于主登记）。
- 根因/修复点：get → 双次 HTTP 往返 → remove 非原子；修复 = 原子 remove（先删后用/`remove(key)` 拿到即胜出）。
- 注记：CL-019（票据表过期清理搭车）同文件协同线索；保护面 C10-013。

**MA-12 ApiContextInterceptor 同步抛豁口（P2）**
- 成员：C3-006（主登记）+ C6-009（并入，ThreadLocal 滞留子面同站点同根）。
- 根因/修复点：proceedAsync 同步抛时 binder 永不 close；修复 = catch-close-rethrow（含 ContextBinder 幂等连带核对）。
- 注记：「已关闭残留可见性」面归 CL-025 移交（非本工单）；保护面 C10-012。

**MA-13 RpcTaskMonitor 跨轮竞态（P2）**
- 成员：C3-010（主登记）+ C1-004（并入，checkFailCount 跨线程 RMW 同站点同根）。
- 根因/修复点：异步回调跨轮并发下 checkFailCount 丢失更新 + cancellable 非 volatile；修复 = AtomicInteger + volatile（或监视器收敛）。
- 注记：C6-011（同文件条件账本）独立工单同批注记；WI 2.3 CL-025 平台轮「C3-010/C4-003 修复先决」关系维持。

**MA-14 VertxMqttServer doStart 不观察 listenFuture（P2）**
- 成员：C6-023（主登记）+ C3-019（并入，同站点同根同修复）。
- 根因/修复点：listenFuture 无观察者——绑定失败生命周期仍 ACTIVE；修复 = doStart 观察/await listen future，失败即抛出或触发 stop。
- 注记：C6-028（doStop 关闭序）独立工单，与主登记「同一提交收敛」约束（见独立清单）；对照兄弟 NettyTcpServer/SocketServer 已闭合。

**MA-15 CompletionStage 同步抛逃逸族（P2）**
- 成员：C3-011（主登记，5 站点 + round2 扩展 MqttServerMessageService.sendAsync）+ C3-021（MQTT 下行扇出同步抛逃逸 + PUBACK 注释失实，字节码独立再证）+ C3-029（sendToChannel handler 缺失 NPE 同步抛，P3）。
- 根因：「返回 CompletionStage 的方法同步抛出」契约破坏（平台契约：不得同步抛、回调内异常必须 completeExceptionally）；已防站点（TestGraphqlRequestErrorsNotDropped/TestPollingRpcClientSyncThrow）与未防站点并存（F3 漏修形态）。
- 修复点：统一防护模式（catch → failed future / 防御守卫返回异常完成 future），三站点同模式收敛。
- 注记：CL-007 重复合并 → C3-021；RP-1 断言级反向钉死 2 处随本工单先改测试（4.2 先例）；保护面 C10-014 + C10-006（A2 补登）。

**MA-16 stop 不关底层 HttpClient 族（P2）**
- 成员：C6-007（主登记，JdkHttpClient.stop 不关 java.net.http.HttpClient）+ C6-025（feishu 渠道双 JDK HttpClient 停止期不关闭，同族 F3）。
- 根因/修复点：stop 路径遗漏底层 client 关闭——selector 线程与连接池跨启停周期累积；修复 = stop 收敛清单补 close（JDK 25 HttpClient implements AutoCloseable）。
- 注记：保护面 C10-004（启停周期线程计数断言）。

### P3 族（1 张）

**MA-17 非 final static 可变载体（P3）**
- 成员：C1-006（主登记，HttpRpcMessageTransformer.INSTANCE / DefaultApiResponseNormalizer.INSTANCE）+ C1-017（批次 2 清扫：非 final 单例/常量 + EnumSet/共享数组外泄，C1-006 同族）。
- 根因/修复点：非 final public static 可变字段 + 可变对象经公开访问外泄；修复 = final 化 + 不可变包装/封装（同模式清扫）。
- 注记：C10-015 对该组「不立项测试」与本工单（代码修复）不矛盾；仓库内无重赋值点，修复为卫生面。

---

## 三、独立立案清单（41 张）

### P0（1 张，即时通道候选）

| 工单 | 发现 | 一句话 | 约束 |
|---|---|---|---|
| C6-001 | C6-001（P0） | JDK/OkHttp 下载成功路径从不 close sink——resume=true 默认参数每次成功下载泄漏一个 fd，Windows Files.move 确定性失败 | 修复仅盖 JDK/OkHttp（Apache releaseResources 契约已闭合）；**与 C3-005 同批设计**（close 收敛与 rename 修复一体）；前轮 3.9「文件模式主路径已关闭」证伪在案 |

### P1（8 张）

| 工单 | 发现 | 一句话 | 同批/族约束 |
|---|---|---|---|
| C3-001 | C3-001（P1） | OkHttp onResponse 内抛 IOException 被吞——future 永不完成（fetchAsync/uploadAsync 两中招站点；doDownloadAsync 已防） | future 永不完成族跨单元第 4 例单列（§七-1）；同批族内对齐 completeExceptionally |
| C3-005 | C3-005（P1） | 无校验源 expected==null 早退不 rename——下载报成功但目标不产生（半个成功） | 与 C6-001(P0) 同批设计；错误以成功交付族第 4 例单列（§七-2）；设计契约 §3.3 完成状态机为修复锚 |
| C3-024 | C3-024（P1） | SftpClient 全链路无超时（jsch timeout=0=无限 + SftpConfig 无 timeout 字段）——业务线程与 SSH 会话无限挂起 | 与 C6-018 同文件同批（一次 PR 收敛）；可达性经 IFileServiceClient/FileServiceResource 证实 |
| C6-004 | C6-004（P1） | SSE 反压以无限期阻塞等待实现且无超时——阻塞承载线程（Apache 为共享 reactor 线程） | 独立于 MA-02（本体=demand 等待机制，修复点在基类/apache 面）；失效面③ 与 MA-02 同 diff 收敛；SSE 生命周期批次（与 MA-02、C3-033 同批）；WI 3.1 C7-001 本体落点 |
| C6-005 | C6-005（P1） | BASE64_FORM 上传 IoHelper.readBytes 不关闭输入流——三实现共漏 | 与 CL-012（OOM 峰值面线索）同站点不同本体，修复批协同 |
| C6-017 | C6-017（P1） | NettyTcpServer doStart bind 失败不关 boss/worker 事件循环组——每次启动失败泄漏整组 I/O 线程 | 对照 C6-014 族正确兄弟写法；保护面 C10-004 |
| C6-018 | C6-018（P1） | SftpClient 构造期「session 已连、channel 未开」失败——SSH 会话无人回收 | 与 C3-024 同文件同批 |
| C6-019 | C6-019（P1） | TencentEmailSender 每次 sendEmail 新建 SesClient 从不 shutdown——连接池随发送次数无界累积 | 保护面 C10-004/C10-013 |

### P2（23 张）

| 工单 | 发现 | 一句话 | 同批/族约束 |
|---|---|---|---|
| C1-003 | C1-003（P2） | RpcChannelState.prepareSend 容量守卫 check-size-then-put TOCTOU | C10-012（A1 补登归属） |
| C1-005 | C1-005（P2） | SimpleRpcClient 回调槽位双写者覆盖：工厂配置回调被构造器静默丢弃 + 晚设置无可见性 | rpc-simple 生命周期批次（C10-008） |
| C1-010 | C1-010（P2） | FeishuClient.stop 清空与 WS 回调线程无 happens-before——停机窗口僵尸投递/NPE 链 | feishu 修复批；WI 2.1 CL-021 载体注记 |
| C1-011 | C1-011（P2） | stop→start 重启：owned scheduler 已 shutdown 后 scheduleAtFixedRate 拒绝——started=true 半启动死态 | feishu 修复批；WI 2.1 CL-021 载体行为面（§七-5） |
| C1-014 | C1-014（P2） | NettySslEngineFactory 对 SslConfig 缓存 check-置位无同步 + 非 volatile 不安全发布 | C6-CL-027 重复合并闭环（主登记）；netty/socket 批次（C10-011） |
| C1-016 | C1-016（P2） | SocketClient 单读线程契约无守卫无声明——call/recv 双入口并发消费帧流交织 | socket 批次（C10-011） |
| C1-018 | C1-018（P2） | acceptEndpoint 回调挂载时序倒置：异步 auth 下 complete() 先于 setOnCloseCallback——close 回调永久丢失、死连接驻留 | 与 CL-010/CL-018（归位线索）修复批协同、互补保留；MQTT 批次（C10-006） |
| C1-019 | C1-019（P2） | SocketServer stop→start 重启 stopped 复位×旧 accept 循环退出判定竞态——每次重启泄漏一个常驻 accept 线程 | 「守卫谓词错位」族注记（与 C1-013 站点/机制均不同，独立立案）；先决=测试注入缝（C10-007 同类前提）；保护面 C10-011 |
| C3-007 | C3-007（P2） | binder.close 在异步完成线程执行——发起线程 ThreadLocal 串号 | 与 CL-011（平台根因）协同；止血点 getOrCreateContext isClosed 检查在案 |
| C3-009 | C3-009（P2） | SimpleRpcClient 发送失败完成的是派生 future——waitFutures 条目滞留至超时（timeout<=0 永久泄漏） | 对齐 MessageRpcClient onFailure 正确先例；rpc-simple 批次 |
| C3-012 | C3-012（P2） | cancelToken 监听器只增不删（全 scope 10 站点穷举）——长生命周期 token 无界累积 | 单工单内部多站点（bindCancelToken 单点修复）；WI 2.1 api-core 移交包 FutureHelper.bindCancel 协同点（§七-10）；保护面 C10-010 |
| C3-022 | C3-022（P2） | MqttConnection.sendAsync 跨线程触发 vertx nextMessageId() 非原子——packetId 碰撞 | 事件循环归位修复；字节码再证；MQTT 批次 |
| C3-023 | C3-023（P2） | PacketCodecHandler 吞编解码异常——静默吞帧仅超时兜底 | CL-008 重复合并 → 本工单；netty 批次（C10-011） |
| C3-025 | C3-025（P2） | TencentEmailSender 吞全部发送异常正常返回——「不抛即成功」契约下误信已发送 | CL-009/CL-029 重复合并 → 本工单；对齐 JavaEmailSender 重抛范式（F3 兄弟）；保护面 C10-013 |
| C3-026 | C3-026（P2） | SocketClient.recv 重连链耗尽后静默死亡——客户端「活着但收不到」 | CL-028 重复合并 → 本工单；终态处理器 + 消息级错误分流；保护面 C10-011 |
| C3-033 | C3-033（P2） | SSE 订阅终态后无终止隔离（迟到事件越过 onComplete/终态 park/假 onError） | 基类单点修复（round2 主登记）；与 C6-004 本体区分成立，SSE 生命周期批次同批；保护面 C10-003 |
| C6-003 | C6-003（P2，已知未修复） | JDK/OkHttp 416 整体重传携带陈旧 Range 头——恢复路径确定性失败 | 前案 2026-09-22 3.9 同站点 P2 已复核保留——并轨修复（本单元为登记载体，cross-ref 不重复立项）；与 MA-02 同文件 416 分支同批；保护面 C10-002 |
| C6-010 | C6-010（P2） | SimpleRpcClientFactory 创建的通道无回收路径：destroy 不关已建 SocketClient + destroy 后仍可 newInstance | rpc-simple 批次；保护面 C10-008 |
| C6-011 | C6-011（P2） | RpcTaskMonitor.activeTasks：statusMethod 为空任务进入不可移除状态（条件性只增不删账本） | 与 MA-13 同文件同批（账本卫生）；零生产消费方（嵌入方面）；保护面 C10-012 |
| C6-012 | C6-012（P2） | PollingRpcClient 轮询任务无总时长/次数上限——远端不完成且调用方放弃时永久轮询 | 保护面 C10-012 |
| C6-013 | C6-013（P2） | file 目标 + resume=false 下载直写最终路径——失败残留截断文件 | 下载链修复批（C6-001/C3-005 同批）；保护面 C10-001 |
| C6-027 | C6-027（P2） | SocketClient.connect 的 onChannelOpen 回调异常——socket 留开且状态机被 Guard 锁死 | socket 批次（C10-011） |
| C6-028 | C6-028（P2） | VertxMqttServer doStop 异步 close 不等待且 sessionManager 停机不清空——重启 bind 冲突静默吞掉 + 死会话驻留 | 与 MA-14 主登记同一提交收敛（启动/停机两面一次 PR）；前案 3.9 still-live P2-15 相邻面同批（§七-9）；保护面 C10-007 |

### P3（9 张）

| 工单 | 发现 | 一句话 | 约束 |
|---|---|---|---|
| C1-007 | C1-007（P3） | SimpleRpcServer.start 非原子守卫 + init 同族 prepend（单发语境低危） | rpc-simple 批次（C10-008） |
| C3-013 | C3-013（P3） | 服务端 filter MDC trace 异步延续线程丢失 | 记录性；C10-012 立项主从（primary=C10-012，M1 声明） |
| C3-014 | C3-014（P3） | OkHttp 忽略 request.getTimeout——跨实现超时语义分歧 | 若修随批补 per-call readTimeout 断言（C10-015 注记） |
| C3-027 | C3-027（P3） | RpcMessageHandler.checkTimeout 以单个可变异常实例完成多个 future——跨等待者串扰 | 每 future 独立异常；保护面 C10-011 |
| C3-028 | C3-028（P3） | NettyTcpClient.sendOneway 完成监听不校验连接结果——失败连接上静默写丢弃（含 isDone 分支） | FIRE_EXCEPTION_ON_FAILURE；保护面 C10-011 |
| C3-030 | C3-030（P3） | TencentSmsSender.sendMultiMessage 批量 fail-fast 无逐条结果（对照 sendMultiEmail 已声明） | 逐条结果或补 javadoc；保护面 C10-013 |
| C3-032 | C3-032（P3） | OssFileServiceClient.makeBucket check-then-act——并发首建 409 | S3 幂等惯用法（head-and-create/if-not-exists）；保护面 C10-013 |
| C6-015 | C6-015（P3） | SimpleRpcServer.stop 后无法重启：socketServer 未复位 | rpc-simple 批次（C10-008） |
| C6-016 | C6-016（P3） | FileDownloadSubscriber/HttpInputFileBodyPublisher 主源码无生产调用方——并行实现漂移（死代码） | 维护面收口：删除或接线；删除时同步迁移 TestBodyPublisherResourceFix（RP-4，4 用例） |

---

## 四、watch-only 清单（1 条）

| 发现 | 判级 | watch 理由 | 升格条件 |
|---|---|---|---|
| C1-020 | P3 | ConcatenatedBodyPublisher.currentSubscription 非 volatile——JMM 发布缺口真实但零当前触发路径（唯一生产消费方 JDK HttpClient 的 subscribe/request 大概率同线程序列化，复核裁定「P3 恰当」）；按「不为理论风险占修复预算」校准与 C10-015 测试 watch 口径对齐 | 该类被非 JDK 引擎复用、或观察到跨线程切换实例化时升格独立工单（修复动作已备：volatile + 锁内读引用锁外转发） |

除此之外显式空：其余 73 条均有具体修复动作且成本低于持续观察成本，不设 watch。C10-015 的 watch-only 是**测试立项口径**（6 案 P3 本体不立测试），其覆盖的 C1-006/C1-017 在缺陷口径仍立 MA-17 工单，两者不冲突。

---

## 五、开放裁定与 7.1 移交项

1. **MA-05 判级分歧（C1-009 P1 ↔ C3-017 P2）**：主登记结构已由本级终裁（C1-009 主，合并单工单，见 §二 MA-05）；**判级终裁留 7.1**。随案移交：前提(a) 双终态序列复核义务（C1 侧回码，按 JDK WebSocket onClose/onError 互斥契约核实），结论只改失效面枚举不改工单存在性。
2. **MA-06 判级分歧（C6-021 P1 ↔ C3-016 P2）**：合并单工单维持（双方本体/修复点无分歧、纯校准分歧），判级留 7.1。
3. **休眠面激活回升条款两组（对齐 WI 3.3/3.2 条件化工单先例）**：(a) feishu 链——生产激活唯一路径 `ChannelConnectorManager.startAll` 全仓非测试零调用，统一适用 MA-04/MA-05/MA-06（复核点名 C3-015/016/017 + C6-024）；(b) vertx-mqtt 面——nop-vertx-mqtt-server 全模块零装配，统一适用 MA-01 合并工单、MA-15 的 C3-021 站点、独立工单 C3-022。激活证据与回升条件由 7.1 统一裁定，本轮不单方面改变已定跨维判级。
4. **MA-03 双判级单工单**（P1 apache / P2 jdk,okhttp）口径确认留 7.1。
5. **C6-026② 归属确认**：②（摘除分支死码）本体独立性保持，修复随 MA-09 同 diff，计数归 MA-09 工单不另立（已在本级裁定，备案 7.1）。
6. **CL-034 netty close 语义核实**：RpcMessageHandler 超时轮依赖 channelInactive 兜底取消，「已注册未 active 的 channel」close 时是否派发 channelInactive 需按依赖版本（netty 4.1.x）字节码核实——移交 C7 维度（长时运行/失败路径资源残留口径），核实为缺陷则升格立案（防御性修复方向已给：调度下沉 channelActive 或 channelUnregistered 兜底）。
7. **CL-011/CL-018/CL-025 平台移交**：BaseContext.close 跨线程 ThreadLocal 根因、MQTT 异步 auth 线程归位、已关闭 context 残留可见性——移交 C2/平台上下文传播专题（nop-api-core 面），与 MA-12（未关闭面）、C1-018（挂载时序面）、C3-007（止血协同）保持工单间指针。
8. **WI 2.3 CL-025（IHttpResponse 断连观测平台原语）**：维持平台轮归属与「MA-13（C3-010）/C4-003 修复先决」关系；本单元实现面承接义务已由 MA-02/C6-004/CL-026 履行（§七-7）。

---

## 六、权威汇总表（74 条 → 58 工单；逐条落位无遗漏）

### 6.1 汇总统计

| 维度 | 保留 | 族工单成员 | 独立 | watch-only |
|---|---|---|---|---|
| C1 | 19 | 9 | 9 | 1（C1-020） |
| C3 | 30 | 13 | 17 | 0 |
| C6 | 25 | 10 | 15 | 0 |
| **合计** | **74** | **32** | **41** | **1** |

工单判级分布：

| 判级 | 族工单 | 独立工单 | 合计 |
|---|---|---|---|
| P0 | 0 | 1（C6-001） | **1** |
| P1 | 7（MA-01…07；MA-03 双判级计 P1，MA-05/06 分歧暂计 P1） | 8 | **15** |
| P2 | 9（MA-08…16；MA-15 含 P3 成员按最高计） | 23 | **32** |
| P3 | 1（MA-17） | 9 | **10** |
| **合计** | **17** | **41** | **58** |

**逐条落位无遗漏声明**：74 条保留发现 = 32（族成员）+ 41（独立）+ 1（watch-only），逐条见 6.2–6.4 三表，与三维度「最终保留项」清单逐一比对无缺漏、无重复落位；7 条并入登记（C1-004、C3-019/020/031、C6-008/009/014）全部落进 MA-13/14/09/11/08/12/07 七张族工单随主登记修复，复核累计 81 条（74 保留 + 7 并入）全数闭合。CL×34 与 C10×15 的落位分别见 §八、§九。

### 6.2 C1 权威表（19 条）

| ID | 最终判级 | 落位 | 工单 | 注记 |
|---|---|---|---|---|
| C1-001 | P1 | 族 | MA-07 | 主登记；C6-014 并入 |
| C1-002 | P2 | 族 | MA-08 | 并列本体面（工单主键 C3-008） |
| C1-003 | P2 | 独立 | C1-003 | — |
| C1-004 | — | 并入 | MA-13 | →C3-010（复核已定） |
| C1-005 | P2 | 独立 | C1-005 | rpc-simple 批次 |
| C1-006 | P3 | 族 | MA-17 | 主登记 |
| C1-007 | P3 | 独立 | C1-007 | — |
| C1-008 | P1 | 族 | MA-01 | 主登记；失效面①证伪修正落账 |
| C1-009 | P1 | 族 | MA-05 | 主登记（本级终裁）；判级留 7.1 |
| C1-010 | P2 | 独立 | C1-010 | feishu 批 |
| C1-011 | P2 | 独立 | C1-011 | WI 2.1 CL-021 载体链接 |
| C1-012 | P2 | 族 | MA-09 | 主登记；子项(c) 勘误落账 |
| C1-013 | P2 | 族 | MA-10 | 主登记 |
| C1-014 | P2 | 独立 | C1-014 | CL-027 闭环 |
| C1-015 | P2 | 族 | MA-11 | 主登记；C3-031 并入 |
| C1-016 | P2 | 独立 | C1-016 | — |
| C1-017 | P3 | 族 | MA-17 | 成员 |
| C1-018 | P2 | 独立 | C1-018 | CL-010/CL-018 协同 |
| C1-019 | P2 | 独立 | C1-019 | 守卫谓词错位族注记 |
| C1-020 | P3 | watch-only | — | §四 |

（C1 复核统计：保留 19 + 并入 1 = 20 条全对账。）

### 6.3 C3 权威表（30 条）

| ID | 最终判级 | 落位 | 工单 | 注记 |
|---|---|---|---|---|
| C3-001 | P1 | 独立 | C3-001 | future 永不完成族第 4 例单列 |
| C3-002 | P1 | 族 | MA-02 | 主登记（live 默认 client） |
| C3-003 | P1 | 族 | MA-02 | 成员 |
| C3-004 | P1/P2 | 族 | MA-03 | 主登记；双判级随工单 |
| C3-005 | P1 | 独立 | C3-005 | 与 C6-001 同批设计；错误以成功交付族第 4 例 |
| C3-006 | P2 | 族 | MA-12 | 主登记；C6-009 并入 |
| C3-007 | P2 | 独立 | C3-007 | CL-011 协同 |
| C3-008 | P2 | 族 | MA-08 | 工单主键（含 round2 三入口扩展面） |
| C3-009 | P2 | 独立 | C3-009 | — |
| C3-010 | P2 | 族 | MA-13 | 主登记；C1-004 并入 |
| C3-011 | P2 | 族 | MA-15 | 主登记（含 round2 扩展站点） |
| C3-012 | P2 | 独立 | C3-012 | 10 站点单工单 |
| C3-013 | P3 | 独立 | C3-013 | primary=C10-012 |
| C3-014 | P3 | 独立 | C3-014 | — |
| C3-015 | P1 | 族 | MA-04 | 主登记；C6-024 升格对齐 |
| C3-016 | P2 | 族 | MA-06 | 成员；判级留 7.1 |
| C3-017 | P2 | 族 | MA-05 | 成员；判级留 7.1 |
| C3-018 | P1 | 族 | MA-01 | 成员 |
| C3-019 | — | 并入 | MA-14 | →C6-023（复核已定） |
| C3-020 | — | 并入 | MA-09 | →C1-012（复核已定） |
| C3-021 | P2 | 族 | MA-15 | 成员；A2 补登→C10-006 |
| C3-022 | P2 | 独立 | C3-022 | — |
| C3-023 | P2 | 独立 | C3-023 | CL-008 并入 |
| C3-024 | P1 | 独立 | C3-024 | 与 C6-018 同批 |
| C3-025 | P2 | 独立 | C3-025 | CL-009/CL-029 并入 |
| C3-026 | P2 | 独立 | C3-026 | CL-028 并入 |
| C3-027 | P3 | 独立 | C3-027 | — |
| C3-028 | P3 | 独立 | C3-028 | — |
| C3-029 | P3 | 族 | MA-15 | 成员（工单判级 P2） |
| C3-030 | P3 | 独立 | C3-030 | — |
| C3-031 | — | 并入 | MA-11 | →C1-015（复核已定） |
| C3-032 | P3 | 独立 | C3-032 | — |
| C3-033 | P2 | 独立 | C3-033 | SSE 批次；与 C6-004 本体区分 |

### 6.4 C6 权威表（25 条）

| ID | 最终判级 | 落位 | 工单 | 注记 |
|---|---|---|---|---|
| C6-001 | P0 | 独立 | C6-001 | 即时通道候选；与 C3-005 同批设计 |
| C6-002 | P1 | 族 | MA-02 | 成员；C1-CL-001 并入② |
| C6-003 | P2 | 独立 | C6-003 | 前案 3.9 并轨；降级保留（P1→P2） |
| C6-004 | P1 | 独立 | C6-004 | SSE 批次；WI 3.1 C7-001 本体落点 |
| C6-005 | P1 | 独立 | C6-005 | — |
| C6-006 | P1 | 族 | MA-03 | 成员 |
| C6-007 | P2 | 族 | MA-16 | 主登记 |
| C6-008 | — | 并入 | MA-08 | →C1-002/C3-008（复核已定）；P3 子面随同 diff |
| C6-009 | — | 并入 | MA-12 | →C3-006（复核已定） |
| C6-010 | P2 | 独立 | C6-010 | 与 C1-001 同文件互补面 |
| C6-011 | P2 | 独立 | C6-011 | 与 MA-13 同文件同批 |
| C6-012 | P2 | 独立 | C6-012 | — |
| C6-013 | P2 | 独立 | C6-013 | 下载链批 |
| C6-014 | — | 并入 | MA-07 | →C1-001（复核已定） |
| C6-015 | P3 | 独立 | C6-015 | — |
| C6-016 | P3 | 独立 | C6-016 | RP-4 迁移义务 |
| C6-017 | P1 | 独立 | C6-017 | — |
| C6-018 | P1 | 独立 | C6-018 | 与 C3-024 同批 |
| C6-019 | P1 | 独立 | C6-019 | — |
| C6-020 | P1 | 族 | MA-01 | 成员；独有窗口①随同 diff |
| C6-021 | P1 | 族 | MA-06 | 主登记；判级留 7.1 |
| C6-022 | P2 | 族 | MA-10 | 成员 |
| C6-023 | P2 | 族 | MA-14 | 主登记；C3-019 并入 |
| C6-024 | P1 | 族 | MA-04 | 成员（升格对齐，已定） |
| C6-025 | P2 | 族 | MA-16 | 成员 |
| C6-026 | P2 | 族 | MA-09 | 成员（①并入、②随同 diff，§五-5） |
| C6-027 | P2 | 独立 | C6-027 | — |
| C6-028 | P2 | 独立 | C6-028 | 与 MA-14 同提交收敛 |

---

## 七、跨单元对账

**台账查重结论**：nop-network（nop-http/nop-rpc/nop-netty/nop-socket/nop-vertx/nop-codec）与 nop-integration 十叶在 WI 1.1–4.2 台账（P1 累计 137 条）中无既有审计单元覆盖（已审计单元为 stream/job/wf/task/retry-tcc/batch/kernel/core/service-framework/persistence/ai/message/cluster，见 C3 复核台账查重节）；本单元 74 条与 P1 台账逐族比对**零同站点重复立项**。家族/载体/消费面关联 10 处单列：

| # | 本单元落位 | 对端 | 关系 | 处置 |
|---|---|---|---|---|
| 1 | C3-001（独立 P1） | future 永不完成族：WI 1.6 C8-012 / WI 3.2 C1-001(MA-01) / WI 4.2 C3-001(MA-07) | 跨单元同族第 4 例 | 单列；修复批与族内三案同批对齐（失败路径必须 completeExceptionally） |
| 2 | C3-005（独立 P1） | 错误以成功交付族：WI 3.1 C7-002 / WI 3.3 C3-001 / WI 3.4 C3-005 | 跨单元同族第 4 例 | 单列；修复批族内对齐（成功信号与产物落地原子性） |
| 3 | MA-01 + C6-028 | check/nosql-cdc 2026-08-19 前案（Lettuce PubSub P2×2 未修复）及其 peri 侧修复 WI 2.5 MA-01 | 同前案 MQTT 面兄弟（非同站点） | 家族关联单列；修复批按族对齐属主校验/停机对账模式 |
| 4 | MA-09 | WI 4.1 MA-05（DebeziumMessageSource 幽灵订阅） | 「订阅生命周期注册/注销配对」族 | 单列；修复对齐 RpcMessageSubscriptions putIfAbsent/cancel-remove 正确先例 |
| 5 | C1-011（独立）+ MA-04（C6-024/C3-015 面） | WI 2.1 CL-021 feishu raw 线程载体轮 | 载体（平台设施）↔ 行为面（使用方） | 行为面工单修复注记链接载体轮（先平台设施后使用方）；raw 线程面本单元零重复立案（防重复正确） |
| 6 | MA-02（C3-002/C3-003/C6-002）+ C6-004 + CL-026 | WI 3.1 C7-001（SSE 流式无期限，P1 open） | 消费面（聚合层）↔ 本体面（传输层） | 回写闭环成立；**修复两侧同批**（聚合层期限 + 传输层读超时/可中断） |
| 7 | MA-02/C6-004/CL-026 | WI 2.3 CL-025（IHttpResponse 断连观测平台原语） | 实现面承接 ↔ 平台原语面 | 实现面由本单元工单承接；平台原语面维持 WI 2.3 平台轮归属及「MA-13（C3-010）/C4-003 修复先决」关系 |
| 8 | 全单元 | WI 2.3 CL-022（JDK Delayer orTimeout 11 站点） | 零交集（两树 orTimeout 0 命中实测） | 显式登记无交集，同族对照为各超时站点 |
| 9 | C6-003 + C6-028 | 2026-09-22 code-review-full 3.9 前案（P2 已复核保留 / still-live P2-15 相邻面） | 跨 campaign 前案 cross-ref | 并轨修复不重复立项；C6-001 对前轮 3.9「已关闭」结论的证伪在 P0 通道在案 |
| 10 | C3-012（独立）+ MA-15 | WI 2.1 api-core 移交包（FutureHelper.bindCancel 单点改造协同点） | 设施协同 | C3-012 修复与 api-core 批次协同（bindCancelToken 单点），零重复 |

**globalTimer 族**：不涉——本单元全部定时面为 owned scheduler/自建 timer（feishu scheduler、SimpleRpcClientFactory timer、RpcMessageHandler per-channel 轮），无 GlobalExecutors.globalTimer 消费站点，与 WI 1.5 MA-04/WI 2.1 C4-001 平台定时器族零关联。

---

## 八、CL 线索处置表（CL×34 全落位）

处置类别：**并入工单**（随缺陷工单修复）/ **重复合并**（并入其他线索或发现）/ **独立线索**（留存台账，归属维度复核）/ **移交**（转其他维度/平台轮）。驳回 0。

| CL | 内容一句话 | 处置 | 落点 |
|---|---|---|---|
| CL-001（C1 文件） | JDK SSE 取消竞态窗口 reader 永不关闭 | 并入工单 | C6-002②（MA-02），复核已闭环 |
| CL-002（C1 文件） | sidecar get(30s) 回调线程阻塞自等待 | 并入工单 | MA-03，复核已闭环 |
| CL-003（C1 文件） | maxConnPerRoute 无 >0 守卫默认 0 下推 | 重复合并 | →CL-023（同配置面，CL-023 含 HC5 字节码结论为主） |
| CL-004（C1 文件） | JdkHttpClient 单一小池长驻任务占满后队列拒绝 | 独立线索 | C4；与 CL-014 同面关联（显式小池 vs 默认回退两形态） |
| CL-005（C1 文件） | feishu send 无超时挂死单线程 scheduler + 心跳不喂重连 | 并入工单 | MA-04（含 C6-round2 SocketClient 心跳同族佐证同批覆盖） |
| CL-006（C1 文件） | feishu connect().join() 阻塞 scheduler 至 15s | 并入工单 | MA-04（scheduler 阻塞面两点收敛） |
| CL-007（C1 文件） | MqttConnection.sendAsync 同步抛半个成功 | 重复合并 | →C3-021（MA-15 成员，同站点同根） |
| CL-008（C1 文件） | PacketCodecHandler 吞 encode/decode 异常 | 重复合并 | →C3-023（同本体） |
| CL-009（C1 文件） | TencentEmailSender 吞 FrequencyLimit 等异常 | 重复合并 | →C3-025（与 CL-029 同点三登记一处置） |
| CL-010（C1 文件） | MQTT 异步 auth 下 acceptEndpoint 无 HB 保证 | 重复合并 | →CL-018（同站点同根线索合并，主键 CL-018） |
| CL-011（C3 文件） | BaseContext.close 跨线程不清 ThreadLocal 平台根因 | 移交 | C2/平台上下文传播专题（nop-api-core）；C3-007 止血协同 |
| CL-012（C3 文件） | BASE64 上传整文件入内存峰值 ≈3× | 独立线索 | C7 OOM 族；与 C6-005 同站点不同本体协同 |
| CL-013（C3 文件） | 非 2xx 响应体 SSE 消费器无界累积 | 独立线索 | C7（maxErrorBodyLength 上限裁定） |
| CL-014（C3 文件） | 下载/SSE 阻塞读回退 globalWorker | 独立线索 | C5；与 CL-004 同面关联 |
| CL-015（C3 文件） | SimpleRpcServer handler 无超时 + 工厂并发双 timer | 拆分 | timer 双建面并入 MA-07（C1-001 已覆盖检查-创建竞态）；handler 无超时面独立线索（C5，与 CL-017 同族） |
| CL-016（C3 文件） | OssFileServiceClient.listFiles 无分页 >1000 截断 | 独立线索 | C7（主登记，CL-032 并入） |
| CL-017（C3 文件） | SocketServer.processCommand 无超时 + cachedThreadPool | 独立线索 | C4/C5（主登记，CL-030 并入） |
| CL-018（C3 文件） | MQTT 异步 auth 完成线程未归位事件循环 | 移交 | C2（主键，含 CL-010）；随 C1-018 修复批协同（归位方案同治） |
| CL-019（C3 文件） | FeishuBindProvider 票据表过期清理只搭车读写路径 | 独立线索 | C7；与 MA-11 同文件协同 |
| CL-020（C3 文件） | Oss getInputStream 生命周期责任全外移调用方 | 独立线索 | C8（契约注释/统一包装器） |
| CL-021（C6 文件） | sidecar 嵌套请求同池 + ioThreadCount=1 自死锁 | 并入工单 | MA-03（同一修复单元配置面） |
| CL-022（C6 文件） | fetchAsync 响应体全量内存缓冲无上限 | 独立线索 | C5/C7 OOM 族（注意与 WI 2.3 CL-022 orTimeout 同号异物，无关联） |
| CL-023（C6 文件） | maxRequestsPerHost 默认 0 → HC5 回落内置 5（字节码核实） | 独立线索 | C8（主登记，CL-003 并入；OkHttp 0 跳过默认 64 对照在案） |
| CL-024（C6 文件） | fetchSidecarChecksum 默认 true 流量放大 | 独立线索 | C8 配置维度；与 MA-03 叠加注记 |
| CL-025（C6 文件） | Context 异步跳线程后已关闭 context ThreadLocal 残留可见性 | 移交 | C2/C9 平台轮；与 CL-011 同批 ThreadLocal 生命周期专题；未关闭子面已由 MA-12 承接 |
| CL-026（C6 文件） | JDK 下载 body 读取无超时且 cancel 不可中断 | 并入工单 | MA-02（jdk 泵面；WI 3.1 C7-001 本体落点同批） |
| CL-027（C6 文件） | SslConfig 缓存 check-then-act 无同步 | 重复合并 | →C1-014 独立工单（复核已定主登记闭环） |
| CL-028（C6 文件） | SocketClient.recv 重连耗尽静默死亡 | 重复合并 | →C3-026（同本体） |
| CL-029（C6 文件） | TencentEmailSender 吞异常违反消费契约 | 重复合并 | →C3-025（与 CL-009 同点） |
| CL-030（C6 文件） | SocketServer 默认 cachedThreadPool 每连接一线程 | 重复合并 | →CL-017（同面第二形态） |
| CL-031（C6 文件） | MQTT dispatch 在 event loop 内联执行慢消费者阻塞全部连接 | 移交 | C2；与 MA-09 同文件协同注记 |
| CL-032（C6 文件） | Oss listFiles 仅取 S3 首页 | 重复合并 | →CL-016（同本体） |
| CL-033（C1-round2） | NettyTcpServer.getChannelInfos 对未完成 channelActive 的 channel 返回 null 元素 | 独立线索 | C2/C9 健壮性（遍历-发布交错口径）；与 C1-018 装配序面及 C1-013/C6-022 佐证相邻（handler 链装配序同源），修复建议 null 过滤或 attr 赋值前移 |
| CL-034（C6-round2） | RpcMessageHandler 超时轮依赖 channelInactive 兜底——netty close 语义待核实 | 移交 | C7（§五-6；核实为缺陷则升格立案） |

**统计**：并入工单 7（CL-001/002/005/006/015(timer 面)/021/026）＋ 重复合并 10（CL-003/007/008/009/010/027/028/029/030/032）＋ 独立线索 12（CL-004/012/013/014/016/017/019/020/022/023/024/033）＋ 移交 5（CL-011/018/025/031/034）＝ 34，驳回 0。

---

## 九、C10 缺保护登记映射（C10×15 → 本体工单）

C10 登记全部保留（立项 14 + watch-only 1，复核 15/15 维持）；映射随本体工单执行——各工单修复批的验收物包含其映射 C10 条目的建议测试（先行失败测试按复核勘误 R5 以 **16 处**计）；RP-1 断言级反向钉死 2 处随 MA-15 先改测试（修复 diff 必先改测试，4.2 先例）。

| C10 | 保护面（对应本体最高判级） | 映射工单 |
|---|---|---|
| C10-001 | 下载主路径 fd/句柄 + Windows 门控（P0） | C6-001（P0 即时通道）＋ C3-005 ＋ C6-013（下载链批） |
| C10-002 | 下载取消/416 恢复矩阵缺格（P1） | MA-02（C3-002/C6-002/C3-003 面）＋ C6-003 |
| C10-003 | SSE 取消/终态隔离/背压（P1） | MA-02（C3-003）＋ C6-004 ＋ C3-033（SSE 生命周期批） |
| C10-004 | HTTP/SFTP/邮件生命周期资源面（P1） | C6-005/C6-017/C6-018/C6-019 ＋ MA-03（C3-004/C6-006）＋ MA-16（C6-007/C6-025）＋ C3-024（超时面） |
| C10-005 | feishu 重连引擎假体保真度（P1×4） | MA-04/MA-05/MA-06 ＋ C1-010/C1-011（feishu 批；C1-015→C10-013） |
| C10-006 | MQTT 会话/订阅复合操作（P1 三登记） | MA-01 ＋ MA-09 ＋ C1-018 ＋ C3-022；A2 补登：C3-021（MA-15）RP-1 改写义务随此执行 |
| C10-007 | VertxMqttServer doStart/doStop 生命周期（P2） | MA-14 ＋ C6-028（同提交收敛批）；前置=NopVertx 注入缝 |
| C10-008 | nop-rpc-simple 工厂/服务器生命周期（P1 C1-001） | MA-07 ＋ C1-005/C1-007/C3-009/C6-010/C6-015（rpc-simple 批） |
| C10-009 | oauth 锁行为与增强覆盖（P2 工单） | MA-08（C1-002/C3-008/C6-008） |
| C10-010 | cancelToken 监听器生命周期（P2） | C3-012（10 站点单工单） |
| C10-011 | netty/socket 生命周期竞态与失败路径（P2） | MA-10 ＋ C1-014/C1-016/C1-019/C3-023/C3-026/C3-027/C3-028 ＋ MA-15 成员 C3-029 ＋ C6-027 |
| C10-012 | RPC 上下文传播与账本清理（P2） | MA-12 ＋ MA-13 ＋ C3-007/C6-011/C6-012/C3-013；A1 补登：C1-003 |
| C10-013 | 集成发送器失败语义与绑定单次性（P2） | C3-025/C3-030/C3-032 ＋ MA-11（C1-015，主登记←C3-031） |
| C10-014 | 异步完成契约面（P1 C3-001） | C3-001 ＋ MA-15（C3-011 未防三站点/C3-029） |
| C10-015（watch-only） | P3 防御性/理论面与死代码（P3） | MA-17（C1-006/C1-017）＋ C1-020（watch，口径对齐）＋ C3-013/C3-014/C6-016（随各工单低成本注记，不立专项测试） |

覆盖闭合确认：P0×1 + P1×21 全部 22 条本体均有唯一 C10 主归属（含复核修正 A1/A2），74/74 唯一主归属；主从声明 M1（C3-013 primary=C10-012、C3-029 primary=C10-011）已在 §三/§四 相应工单注记。
