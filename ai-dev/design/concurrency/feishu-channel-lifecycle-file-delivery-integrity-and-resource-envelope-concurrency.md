# feishu 通道生命周期/文件交付完整性与资源超时包络并发契约（R2-19 4.3 feishu/文件批 7 成员+测试缝裁定）

> Status: draft（plan 2522 Phase 1 产出物，独立 draft review 通过后转 active——转 active 后为该批实施唯一设计裁决权威）
> Source: 批次成员表、站点行号基线、PA 判定与勘误登记以执行计划 `ai-dev/plans/2522-cra-phase8.28-r2-19-network-feishu-file.md` 为准（本文档为最终状态文档，勘误不落此处）；成员发现详情 = WI 4.3 审计目录 `ai-dev/audits/2026-10/2026-10-21-0610-deep-audit-nop-network-integration-concurrency/`（C3-async-chains[C3-001/C3-005/C3-015/C3-024]/C1-shared-mutable-state[C1-009]/C6-resource-lifecycle[C6-005/C6-021]/merge-adjudication[MA-04/05/06]/C10-test-protection/inventory-p2-c10 :545/:801）；行号 = live @ `76512e5e99` 全文亲读核实值，与 plan 起草基线（`2178232af6`）逐锚核对零漂移（plan 勘误 3 已登记的 R2-02/C6-001 landed 漂移面按该勘误口径引用），再漂移以 live 为准并登记
> 命名裁定：本文落盘名 `feishu-channel-lifecycle-file-delivery-integrity-and-resource-envelope-concurrency.md`——较 plan Goals/Scope 行占位名（…file-delivery-and-resource-timeout…）演进为「交付完整性 integrity + 资源包络 envelope」措辞（覆盖 C3-001 future 终结与 C3-024 超时/C6-005 流关闭两类资源面更准确）；本行即命名调整登记载体，plan Phase 4 回写时按此名更新 Goals/Scope 引用
> 实现锚：nop-integration-feishu `client/FeishuClient`（scheduler 字段 :64 / 单线程 executor :101 / start :179-197[幂等早退 :186-189 / connect :194 / started=true :195 / scheduleHeartbeat :196] / stop :200-224[started=false :204 / transport.close :211 / shutdownNow :216 / reconnectAttempt.set(0) :223] / scheduleHeartbeat :258-265 / sendHeartbeat :268-281[send :277 / 失败 debug :279] / scheduleReconnect :283-295[!started :284-286 / attempt 递增 :287 / schedule :291] / attemptReconnect :297-310[!started :298 / getStreamEndpoint :302 / connect :304] / streamListener：onOpen :313-328[握手 send :324]、onBinary :330-369[ACK send :353]、onClose :371-375、onError :377-384]）、`client/JdkStreamTransport`（connect :36-95[listener 无条件复活 :41 / connectTimeout 15s :89 / buildAsync().join() :88-91] / send :97-111[sendBinary().get() 无参 :107] / close :113-125[sendClose().join() :118 / webSocket=null :122 / listener=null :124]）、`client/JdkFeishuHttpApi`（connectTimeout 15s :33 + .timeout(15s) 三调用点 :115/:135/:156——15s×4 同模块先例）、nop-http-client-okhttp `OkHttpClientImpl`（fetchAsync :59-92[bindCancelToken :64-66 / onResponse :75-89 / body().string() :77 / complete :88 无 catch] / doDownloadAsync :142-234[verifyAndRename 消费 :223 / sink.close :225 / catch→completeExceptionally :227-230] / uploadAsync :272-356[BASE64 :293-294 / bindCancelToken :332-333 / onResponse :340-353 try-with-resources 无 catch / body().string() :345 / complete :351]）、nop-http-api `FileDownloadSink`（verifyAndRename :162-175[expected==null early-return :163-164 / 校验失败删 .part :167-171 / Files.move :172-174] / close :177-185；javadoc :160「校验并把 .part 原子改名为目标文件」）、nop-http-client-jdk `JdkHttpClient`（verifyAndRename 消费 :497 / sink.close :500 / BASE64 :591）、nop-http-client-apache `ApacheHttpClient`（handleDownloadResult 校验+无条件 move :340-353[verifyChecksum null no-op :342-344 / move :350-352] / BASE64 :487）、nop-integration-sftp `SftpClient`（构造即建连 :61-69 / openConnection :131-166[session.connect() :163 / channel.connect() :165 均无参] / getInputStream :295-304 / 8 channel 操作 :168-304 同一无超时面 / newJsch protected seam :122-124）、`SftpConfig`（全文 119 行零 timeout 字段）、nop-kernel/nop-commons `IoHelper.readBytes`（:246-253[readAllBytes JDK 明文不关闭语义 :247-248]）、nop-http-api `DefaultHttpInputFile`（getInputStream 每调新建 FileInputStream :35-41）
> Related: [cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md](cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md)（plan 2521——本文结构模板 + R21 future 族 completeExceptionally(adapt) 范式引用不重开，裁定④同形）；[sse-backpressure-and-body-phase-lifecycle.md](sse-backpressure-and-body-phase-lifecycle.md)（plan 2505——body-idle-timeout 配置面形态参照[裁定①超时预算声明同构] + 2505 钉族零回退硬条款）；R11 错误以成功交付族（plan 2509/2520 先例——裁定⑤族纪律）；`ai-dev/design/nop-network/file-transfer-design.md`（§3.3 :118-119 rename 属完成语义、§3.4 :139 无校验来源仅豁免校验——裁定⑤契约归属 owner，本文只裁定实现形态）

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 4.3-C3-015（P1，MA-04 主；←C6-024/CL-005/CL-006 一处闭合；C10-005 面） | send/close/connect 三阻塞面统一有界化（预设）：send `sendBinary(...).get(sendTimeoutMillis)`——预算缺省 15s（对齐 JdkFeishuHttpApi 15s×4 同模块先例）、`JdkStreamTransport` 包私有字段+setter、`FeishuClient.setSendTimeoutMillis` 转发部署可调面；超时/失败统一失败处理单点（connected=false + close 有界 + warn + scheduleReconnect——onError 同源路径），三调用点 :277/:324/:353 全量覆盖，心跳失败 debug→warn；close 的 sendClose join 有界 5s（与 send 预算解耦）；connect 阻塞段移出 scheduler——`IStreamTransport` 新增 default `connectAsync`（default 委托同步 connect 保 fake 兼容，JdkStreamTransport override 真异步 buildAsync 链；[R2 Minor-m4]生产 override 异步武装链不在假体覆盖面——由既有 connect() 同口径承接（W6/集成面），与既有 connect() 无单测的覆盖口径一致），attemptReconnect :304 改非阻塞 whenComplete 消费，start() :194 维持同步 connect（调用方线程有界阻塞、幂等 start 契约与同步 onOpen 可观测形态不变）；休眠条款维持（ChannelConnectorManager 面零触碰）；与②③同文件交叉改写顺序成文（对齐条款 9） |
| ② | 4.3-C1-009（P1，MA-05 主，B1-07 转正承载；←C3-017；C10-005 面） | `AtomicBoolean reconnectPending` CAS(false→true) 单飞（scheduleReconnect 入口去重，attemptReconnect 完成回调收尾清零——失败重排经清零后 CAS 重新入飞，过期重排 CAS 失败即丢弃）[预设] + 世代令牌 `AtomicLong lifecycleGeneration` 单点语义一次定夺：**start 成功路径与 stop 各递增一次**（生命周期变迁计数——stop 递增使「stop 即作废全部旧代在途回调」成为单一谓词「捕获世代==当前世代」的直接结果，无复合 started 复查；纯 start 递增被拒）；回调闭包捕获调度世代、消费点世代不符丢弃；attemptReconnect 世代校验通过后先 `transport.close()` 旧连接再 connectAsync；前提(a)（JDK WebSocket onClose/onError 互斥性）live 契约读证：JDK 规范唯一硬承诺 = onClose 终态后无后续回调、onError 至多一次，互斥性未被承诺——双终态序列可达，双入口去重为必要防御（结论只影响失效面枚举，红②实施随钉登记）；C1-010/C1-011 P2 面禁顺手引入 |
| ③ | 4.3-C6-021（P1，MA-06 主，B1-08 转正承载；←C3-016；C10-005 面） | 世代校验守卫消费点矩阵[预设，与②同令牌]：  完成回调（attemptReconnect whenComplete 内）世代不符 → `transport.close()`（有界）+ 不武装 + 不置 connected——孤儿连接确定性消除；  `streamListener.onOpen` 入口比对「本代 connectGeneration 快照」（每次 connect 前记录的字段[R2 Major-2]）不符 → 不置 connected + 不发握手帧直接返回——封「建立与关闭之间 onOpen 先达」复活窗；  attemptReconnect 尾段成功路径世代复校早退；`JdkStreamTransport` :41 listener 无条件赋值**保持**（世代为 FeishuClient 生命周期概念，transport 层零世代语义侵入）；与②同文件同批实施、修复点互异不并族（cra-index :277/:278 分立回填）；C6-022 同构面零触碰（NettyTcpClient，MA-10→R4-26） |
| ④ | 4.3-C3-001（P1，独立，R21 future 族第 4 例；C10-014 半边） | fetchAsync（:75-89）与 uploadAsync（:340-353）两 onResponse 体 try/catch Exception → `future.completeExceptionally(NopException.adapt(e))`（预设——2521 adapt 范式与 doDownloadAsync :227-230 既有写法同形，引用不重开）；异常类型规范化登记（onFailure 既有 raw IOException 路径零触碰，两路类型不一致为登记现状）；零触碰：doDownloadAsync 既有 catch、C3-012 bindCancelToken 三行、C6-001 sink.close 两行；红④ mock 预设 = loopback HttpServer 短连接半关闭（零新依赖）；whenCompleteAsync+handle 链改写被拒（§六） |
| ⑤ | 4.3-C3-005（P1，独立，R11 族第 4 例；与⑥联动） | verifyAndRename null 分支也执行 rename[预设]：移除 :163-164 早退，`Files.move` 块改为 expected!=null 条件校验后的无条件落盘段（校验失败分支删 .part 语义不变，`partFile != null` 守卫保持——非文件模式无 .part 语义不变）；跨实现分歧消解：Apache :350-352（null 分支无条件 move，verifyChecksum null no-op live 亲证）即目标形态零触碰，修复后三实现行为一致；R11 族纪律 = 成功信号与产物落地原子性（跳校验不跳落盘收尾）；C6-001 sink.close 语义与顺序零回退 |
| ⑥ | 4.3-C3-005 PA 申报（三叶零触碰声明修正，与⑤联动） | 预设：nop-rpc-api/nop-integration-api 零触碰维持 + nop-http-api 限 `FileDownloadSink.java` 单文件触碰 + plan-first 修正申报（证据链 = plan 2522 + 本文档裁定⑤⑥节 + owner doc :36-46 契约句修正[Phase 3 落账] + 红⑤回归钉；实施等待 draft review 通过）；触碰形态 = 方法体行为演进**零签名变更**（verifyAndRename 签名 :162 不变，消费者全仓仅 OkHttp :223/Jdk :497 两处已枚举——不构成契约面演进）；先例 = R2-02 批内触碰 nop-http-api（roadmap 8.11「PA 面①批内闭环」）；Phase 4 回写 batch-proposal :299 PA 行 + L93 验证命令列（勘误 1 联动）；备选（裁定⑤备选胜出维持三叶零触碰字面）拒绝理由成文（§六） |
| ⑦ | 4.3-C3-024（P1，独立；C10-004 半边；C6-018[R2-20] 同文件边界） | `SftpConfig` 新增单一 `timeoutMillis` 字段（int 缺省 15_000，量级对齐 JdkFeishuHttpApi 15s 同仓先例；getter/setter 沿 strictHostKeyChecking 形态）[预设]；三段接线同值：`openConnection` 段 `session.setTimeout(timeoutMillis)` + `session.connect(timeoutMillis)` + `channel.connect(timeoutMillis)`；分段（connect/read 双值）配置被拒——jsch setTimeout 为 SO_TIMEOUT per-read 语义（活动传输逐读重置，长传输无需总时长预算），分段零收益配置面翻倍；8 个 channel 操作经 session SO_TIMEOUT session 级单点统一覆盖（jsch 无 per-op timeout 参数）；长传输读包络声明：静默停滞 ≤timeout 有界、总时长无上界（流式语义 by design，契约句 owner doc SFTP 新节登记）；**C6-018 构造回收面零触碰**（connect(ms) 超时异常路径 = R2-20 回收面触发前提的注记成文）；SMTP 次级同族注记不代做 |
| ⑧ | 4.3-C6-005（P1，独立；C10-004 半边；CL-012 协同注记）+ 测试缝与零回退钉 | BASE64 三调用点 try-with-resources[预设]：Apache :487 / Jdk :591 / OkHttp :293-294 包裹 `inputFile.getInputStream()`（OkHttp 既有 try/catch IOException 保留）；`IoHelper.readBytes` :246 javadoc 补「不关闭流——调用方负责关闭传入 InputStream」契约声明（**行为零变更**，禁改关闭语义——其他调用方零影响义务）；`DefaultHttpInputFile` :35-41 零触碰；CL-012（内存峰值面）不互吸注记、C6-016 死代码零触碰；红①–红⑦驱动器表 + 测试资产落位 + 既有钉零改写边界 + 零回退钉全列 + C10 三条半边集落名（§五） |

## 二、背景与动机

R2-19 批 7 项（P1×7）覆盖 feishu Stream 长连接与文件通道两类资源面：**feishu 通道域**（三单共居 `FeishuClient`+`JdkStreamTransport` 两文件——C3-015：WS 全部写经 `sendBinary(...).get()` 无超时跑在单线程 scheduler（心跳+重连唯一承载线程 :101）上，半开连接即心跳/重连整体死锁且 `connected` 恒 true 无告警面；C1-009：onClose/onError 双入口直达 scheduleReconnect 无去重 + `started` 一个布尔同时承担启停与重连代次两语义 → 双活/三活 WS；C6-021：stop「检查后建立」窗口无世代复核，stop 后新建 WS 孤儿连接无人关闭、listener 复活 `connected=true`）、**文件交付域**（C3-001：OkHttp fetchAsync/uploadAsync onResponse 无 catch——`body().string()` 中途断连 IOException 被 OkHttp `signalledCallback` 吞掉，future 永不完成，`fetch()/syncGet()` 同步调用方永久阻塞；C3-005：`verifyAndRename(null)` 直接 return，默认 DownloadOptions 无校验源时下载报成功但目标文件永不产生）；**资源包络域**（C3-024：jsch 默认 timeout=0，网络静默故障下业务线程无限挂起且部署无配置手段；C6-005：BASE64 上传三实现共漏流关闭，每次上传确定性泄漏 1 fd）。本文档一次裁定 7 成员 + 测试缝（裁定⑧），使实施只有一种合法形态；B1-07/B1-08 转正落账与 C10 三条半边口径随批承载（条款见四、五）。

## 三、核心设计契约

### 3.1 裁定①：send/close/connect 三阻塞面统一有界化（C3-015，MA-04 吸收面一处闭合）

- **超时预算声明（预设，沿 plan 2505 body-idle-timeout 配置面参照）**：`JdkStreamTransport` 新增包私有 `sendTimeoutMillis`（缺省 `15_000`）+ 包私有 setter；`FeishuClient` 新增 `setSendTimeoutMillis(long)` 转发 setter（部署可调面，沿 `setHeartbeatIntervalSeconds` 既有 bean property 形态——feishu 无 config bean，配置经 IoC property 装配；15s 量级对齐 `JdkFeishuHttpApi` :33/:115/:135/:156 既有先例）。send 改 `ws.sendBinary(ByteBuffer.wrap(frameBytes), true).get(sendTimeoutMillis, MILLIS)`——超时抛 `TimeoutException` 进入失败单点；心跳/握手/ACK 三调用点共用同一预算（一次定夺不拆分：心跳帧为常量小帧，15s 上界远超正常耗时，拆分徒增配置面；误杀风险面在数据通道 send，由部署调参缓解）。
- **失败处理单点（MA-04「失败主动 close 并回调 onError 驱动重连」的落地形态）**：`FeishuClient` 新增私有 `handleSendFailure(site, e)`——`connected=false` + `transport.close()`（有界）+ `LOG.warn`（键含 site 维度，:277/:324/:353 三站点可辨识）+ `scheduleReconnect()`（与 `onError` :377-384 同源路径，重连调度去重由裁定②单飞守卫承载）；三调用点 catch 块统一收敛到该单点；心跳失败日志 debug→warn 升级（:279）；`sendHeartbeat` 的 `!connected` 早退语义不变。close 失败（半开连接 sendClose 也超时）不阻断重连调度——close 为尽力而为语义。
- **close 有界（C6-024 面）**：`JdkStreamTransport.close` 的 `sendClose(...).join()` :118 改 `get(CLOSE_TIMEOUT_MS=5_000, MILLIS)`——与 send 预算**解耦**（一次定夺：close 是尽力而为收尾且 `stop()` 持 FeishuClient 锁执行 close，长预算直接拉长停机延迟；5s 上界足够正常关闭往返，超时即放弃、webSocket 置 null 语义不变）。
- **connect 阻塞段移出 scheduler（CL-006 面）**：`IStreamTransport` 新增 `default CompletableFuture<Void> connectAsync(URI uri, IStreamListener listener)`——default 实现委托同步 `connect()` 后返回已完成 future（`FakeStreamTransport` 零改写兼容，seam 保持包私有零公共契约面扩）；`JdkStreamTransport` override 真异步：`this.listener = listener` 武装保持入口即刻生效（早期 onClose/onError 不丢），`buildAsync(uri, wsListener)`（connectTimeout 15s :89 既有）`.whenComplete((ws, err) -> 武装 webSocket / 异常包装 NopFeishuException)`，**不再 join**；`FeishuClient.attemptReconnect` :304 改调 `connectAsync` + `whenComplete` 非阻塞消费（成功 → 裁定③世代校验消费点；失败 → scheduleReconnect 重排）。**start() :194 维持同步 `connect()`**（调用方业务线程有界阻塞 ≤15s，start 幂等契约与「start 返回即 connected」可观测形态不变——`startTransitionsToConnected` 钉的同步可达性前提）。备选（start 路径也异步化）被拒见 §六。
- **scheduler 单线程语义保持**：本裁定不新增线程（异步完成回调在 HttpClient 线程执行，与 onOpen 握手 :324 同线程域）——scheduler 仍是心跳+重连唯一承载，但其上不再有任意阻塞点（send 有界/close 有界/connect 不在 scheduler 线程等待）。
- **休眠条款维持注记**：`ChannelConnectorManager` 渠道管理链面零触碰（family-clustering :568 对称条款随 MA-04 工单注记维持，本修复不解除不激活）；MA-04 休眠面对称条款不适用红①可达性论证（FeishuClient 直连生命周期面有实仓消费方 FeishuConnector，非休眠面）。
- **C10-005 前置核对与兼容核对表**：§3.2（11 钉逐条）；C10-005 假体升级三件套形态见 §5.2。

### 3.2 裁定①②③既有钉兼容核对表（TestFeishuClient 11 钉 + 值捕获钉，勘误 11 义务，Phase 2 落 `_tmp/` 留痕）

| # | 钉 | 裁定归属 | 兼容性判定 |
|---|----|---------|-----------|
| 1 | startTransitionsToConnected :60 | ①②③ | start 同步 connect 保持（fake default connectAsync→connect 同步）→ connectCount==1 与握手帧断言照常；世代在 start 成功路径递增不影响首连（守卫消费点在完成回调与 onOpen，fake 同步 onOpen 于 connect 内完成、世代在 connect 前 :194 已递增并记录快照 → onOpen 捕获快照==当前世代校验通过[R2 Major-2 口径]） |
| 2 | stopTransitionsToDisconnected :74 | ①③ | stop 世代递增 + close 有界 → isConnected()==false 与 closeCount==1 断言不变 |
| 3 | inboundMessageDeliveredToHandler :84 | ① | onBinary 解码与投递路径零改写；ACK send（:353）在 fake 同步完成不触发超时路径 |
| 4 | inboundMalformedFrameReportsErrorNotSilent :111 | 无 | 零触碰 |
| 5 | sendMessageAcquiresAndUsesAccessToken :122 | 无 | ensureToken/httpApi 面零触碰 |
| 6 | sendMessageWhileNotStartedFailsExplicitly :133 | 无 | 零触碰 |
| 7 | startWithNullCredentialsFailsExplicitly :140 | ② | 世代递增在参数校验与幂等早退**之后**（成功路径尾部）——null 凭证抛出路径不触达 connectCount 增量 |
| 8 | startWithNullHandlerFailsExplicitly :146 | ② | 同上 |
| 9 | doubleStartIsIdempotent :152 | ② | 幂等早退（:186-189）先于世代递增——二次 start 不递增不重连，connectCount==1 保持 |
| 10 | heartbeatSendsControlFrameAndIsWired :161 | ① | sendHeartbeat 直调 fake 同步完成（无异常路径）→ sentFrames+1 与 scheduleAtFixedRateCount 断言不变；debug→warn 升级不改变该钉断言面 |
| 11 | dataFrameIsAcknowledgedAndHeadersPassThrough :385 | ① | ACK 回发路径零改写（仅 send 有界化），sentFrames 计数与契约字段断言不变 |
| 附 | TestFeishuCredentialResolution 17 钉（含 reconnectUsesCapturedResolvedValues :248-264 值捕获钉） | ②③ | resolveEffectiveCredentials 面零触碰；该钉序列 = fireClose → 单飞 CAS 成功调度 1 条命令 → 手动执行 → 世代匹配（无 stop）→ captured 凭证经 :302 逻辑原样消费 → endpointCount==2/provider 不再解析断言全部保持；世代令牌引入不得改变 start 期值捕获契约（捕获先于 connectAsync，重连凭据仍取 start 已捕获组） |
| 附 | TestFeishuCredentialIdBinding 2 钉 / TestJdkFeishuHttpApiOAuth 5 钉[R2 Minor-m5 更正]；点名清单为高价值子集——feishu 53 另含 TestFeishuPbCodec 8/TestFeishuBindProvider 8/TestFeishuBindProviderServerIdentity 2，以模块级 log 为准 | 无 | credential binding 与 httpApi 面零触碰，全绿 |

### 3.3 裁定②：重连单飞 + 世代守卫（C1-009，MA-05，B1-07 转正承载）

- **单飞（预设）**：`AtomicBoolean reconnectPending`——`scheduleReconnect` 入口 `compareAndSet(false, true)` 成功方递增 `reconnectAttempt`（:287）并调度；CAS 失败即丢弃（在飞重连恰一）；清零点 = `attemptReconnect` 的 connectAsync 完成回调收尾，**守卫清零[R2 Major-1]**：调度 attempt 时捕获单调 `reconnectAttempt` 序号快照，回调收尾仅当「捕获序号 == 当前 reconnectAttempt」才清零——防「connect#1 完成 → 回调未及执行 → 新断连事件已 CAS 调度 attempt#2 → 回调执行无条件清零 → 下一事件再次 CAS 成功调度 #3 → #2/#3 双任务在飞双活」的窄竞态（全程同代，世代令牌不救，确定性注入也难触发）；失败分支守卫清零后 scheduleReconnect 经 CAS 重新入飞，过期世代分支守卫清零后**不重排**——旧世代重连链终止。`reconnectAttempt` 保持纯退避语义（一个字段一个语义纪律——不承担世代/去重职责）。备选（synchronized 重连任务串行化）被拒：锁须覆盖 attemptReconnect 全程含 HTTP 往返（≤15s）与 WS 建连，持锁面不可接受（与裁定③备选同理由，§六）。
- **世代令牌单点语义（一次定夺，与裁定③联动）**：`AtomicLong lifecycleGeneration`（FeishuClient 私有字段）；**递增点 = start() 成功路径与 stop() 各一次**——stop 在 `started=false`（:204）同临界区递增（MA-06 预设「stop 递增世代」），start 在 **:194 `transport.connect` 调用之前**递增并记录 `connectGeneration` 快照[R2 Major-2：live :194 connect 先于 :195 started=true，且 fake 同步 onOpen 于 connect 内触发（TestFeishuClient :183-185 实证）——若按 :195 字面 connect 后递增，同步 connect 下迟到 onOpen 读到新世代≠捕获世代被拒 → connected 恒 false，start 可观测形态破坏]；attemptReconnect 同样**先记录快照再 connectAsync**；守卫谓词单一 = 「回调/消费点捕获世代 == 当前世代」；**[R2 Minor-m8]既有 `!started` 早退（scheduleReconnect :284-286 / attemptReconnect :298）保留为防御性早退零删除**——世代谓词仅管在途回调作废，二者职责不重叠。纯 start 递增被拒：stop 窗口内世代不变，完成回调须复合 `started` 复查方可拦截「stop 后 connect 完成」，复合谓词违反单点纪律且与既有 :298 started 检查纠缠（§六）。`stop()` :223 既有 `reconnectAttempt.set(0)` 保持（退避重置语义，与世代独立）。
- **回调携带世代过期丢弃**：`scheduleReconnect` 调度闭包捕获当前世代；`attemptReconnect` 入口校验——世代不符直接返回（清零单飞标志，不重排）。`onClose`/`onError` 双终态序列（无论互斥与否）经同一 CAS 去重为恰一在飞。
- **attemptReconnect 先 close 旧连接**：世代校验通过后、connectAsync 之前 `transport.close()`（有界，裁定① close 预算）——旧 WS fd 不再依赖对端主动断开或半开悬挂；close 与新建之间旧 listener 已被 transport.close 置 null（:124），无事件串扰窗。
- **前提(a) 复核义务（随红②登记，只影响失效面枚举不改工单存在性）**：JDK `WebSocket.Listener` 契约 live 读证——`onClose` 后无后续方法调用、`onError` 至多一次为唯一硬承诺，`onError` 之后是否必然接 `onClose`（互斥 or 双终态序列）规范未承诺；故「双入口双调度」失效面按可达维持枚举，红②实施时以回码/javadoc 复核结论随钉登记；若复核推翻双终态前提，红②主判据降级为触发单形态（工单存在性不变——merge-adjudication MA-05 明文）。
- **C1-010/C1-011 P2 面禁顺手引入**：stop 置 null 僵尸投递（handler 生命周期语义）与 REE 半启动归 R4-26/R4-27——本裁定改写生命周期字段时禁顺手引入 SessionContext 不可变快照或 scheduler REE 生产行为（C10-005 建议② REE 模拟仅为测试假体能力，§5.2）。

### 3.4 裁定③：stop × 在途 connect 世代校验（C6-021，MA-06，B1-08 转正承载；两单同批实施）

- **守卫消费点矩阵（预设，与②同令牌单点语义）**：

| 消费点 | 守卫动作 | 语义 |
|--------|---------|------|
| (a) connectAsync 完成回调（attemptReconnect whenComplete 内，HttpClient 线程） | 世代不符 → `transport.close()`（有界 sendClose）+ 不武装 webSocket + 不置 connected + 单飞清零不重排 | 孤儿连接确定性消除——stop 返回后任何旧世代建连完成即被关闭；`connectCount - closeCount ≤ 1` 不变式（C10-005 建议③形态，红③判据） |
| (b) `streamListener.onOpen` 入口 | 世代不符 → 不置 connected + 不发握手帧直接返回 | 封「WS 已建立与 关闭之间 onOpen 先达」复活窗——connected 恒 false（F5 挂死不可见面对偶：停机后假活） |
| (c) attemptReconnect 尾段成功路径 | 世代复校早退（不重置 reconnectAttempt） | 三消费点冗余防御，漏一处即复发的面由矩阵全覆盖 |

- **`JdkStreamTransport` :41 listener 无条件赋值保持**：复活武装是 connect 入口语义（早期事件不丢），世代为 FeishuClient 生命周期概念不在 transport 层复制（备选「transport 层世代侵入」被拒——同一语义两处权威，§六）；复活后的行为由消费点 守卫约束。`stop()` 持 FeishuClient 锁执行（:200 synchronized 形态保持），世代递增后锁外完成回调（线程）读取世代即过期。
- **备选拒绝（一次定夺）**：attemptReconnect 全程 `synchronized(FeishuClient)`——connectAsync 完成回调在 HttpClient 线程，持锁跨 HTTP 往返（≤15s）与 stop 持锁 close join（≤5s）构成长持锁链，锁域扩大代价不可接受；世代谓词 O(1) 无锁。平台参照注记：`LifeCycleSupport` stop 二次校验形态（stop 后动作复核终态）为同构模式，本裁定以世代计数实现（比布尔终态多承载重启代际）。
- **不并族条款**：②守卫点 = 调度去重与陈旧回调丢弃；③守卫点 = 在途建连完成后的代际撤销——共用令牌基元、修复点互异，禁「顺手合并成单一守卫」模糊两工单边界（cra-index :277/:278 分立回填对应）；C6-022（NettyTcpClient 同构）零触碰归 R4-26/MA-10。

### 3.5 裁定④：OkHttp onResponse 体终结性（C3-001，R21 族纪律对齐）

- **终结协议（预设）**：fetchAsync onResponse 体（:75-89）与 uploadAsync onResponse 体（:340-353，try-with-resources 外层再包 catch——try 关闭 Response 语义保持）各加 try/catch Exception → `future.completeExceptionally(NopException.adapt(e))`。不变式：**onResponse 体内任何同步异常必终结返回 future**（rule 24——禁 catch 后仅记日志不终结）；禁依赖上层超时兜底（R21 族纪律明文）。类型规范化登记：catch 值经 `NopException.adapt` 包装（2521 范式），`onFailure` 既有 raw IOException 路径零触碰——两路异常类型不一致为登记现状（2521 R2 Minor-m6 同形）。
- **零触碰预设**：`doDownloadAsync` 既有完整 try/catch（:167-231，completeExceptionally :227-230）零回退；C3-012 bindCancelToken 收敛行（:64-66/:158-159/:332-333）零触碰；C6-001 sink.close 行（:225/:228）零触碰——fetchAsync/uploadAsync 包裹不改变 cancel/close 既有链。
- **红④ mock 形态一次定夺（预设）**：loopback `HttpServer` 短连接半关闭（响应头已到、body 传输中断——`sun` 层 socket 半关闭模拟断连，`TestFileTransfer`/`TestFileDownloadSinkClose` 既有 loopback 基建复用，零新依赖）；mockwebserver 依赖评估降 Non-Blocking Follow-up（仅当 loopback 形态被实施期证实不足时激活 E5 评估）。

### 3.6 裁定⑤：verifyAndRename null 分支强制 rename（C3-005，R11 族纪律）

- **修复形态（预设）**：移除 `expected == null` 早退（:163-164），方法体收敛为——`expected != null` 时执行 `verifyChecksum`（失败删 .part 后抛出，:165-171 语义原样）→ `partFile != null` 时无条件 `Files.move(..., REPLACE_EXISTING)`（:172-174 块提出为公共落盘段）。`FileTransferHelper.verifyChecksum` 对 null 本身 no-op（:156-157 live 亲证），实质行为演进仅在 null 分支补 rename。非文件模式（`partFile == null`，out 直连目标）语义不变。
- **跨实现分歧消解声明**：Apache :340-353 即目标形态（null 分支无条件 move :350-352）——修复后 OkHttp/Jdk/Apache 三实现 null 分支行为一致，分歧消除以「单点共享修复」达成，Apache 零触碰。
- **R11 族纪律与契约归属**：成功信号与产物落地原子性（跳校验不跳落盘收尾）——null 分支强制 rename、仅校验失败分支删 .part；契约 SSOT = `file-transfer-design.md` §3.3 :118-119（rename 属完成语义）+ §3.4 :139（无校验来源仅豁免校验不豁免落盘收尾），owner doc `nop-network.md` :40 现状句（「完成并校验通过后原子 rename」）与缺陷态不符，Phase 3 原地修正为「rename 无条件强制、校验来源缺失不豁免落盘收尾」。
- **C6-001 同站点协同零回退**：verifyAndRename → sink.close 消费顺序（OkHttp :223-225 / Jdk :497-500）保持；`close()`（:177-185）与 fd 钉零回退（`TestFileDownloadSinkClose` 用显式 expectedSha256 :104——expected!=null 路径行为不变）。
- **备选拒绝**：impl 侧 `sink.getPartFile()` 两站点重复 move——逻辑重复（OkHttp/Jdk 各一份 move 逻辑）、api javadoc :160 契约（「校验并把 .part 原子改名为目标文件」）持续违约、三实现行为分歧重建（§六；与裁定⑥联动一次定夺，禁第三形态临场发明）。

### 3.7 裁定⑥：C3-005 PA 申报（三叶零触碰声明修正）

- **预设（与⑤联动，两案一体定夺）**：nop-rpc-api/nop-integration-api 零触碰维持；nop-http-api 触碰限 `FileDownloadSink.java` 单文件；plan-first 修正申报——证据链 = plan 2522（含 PA 判定表成员 5 行）+ 本文档 §3.6/§3.7 + owner doc :36-46 契约句修正（Phase 3 落账）+ 红⑤回归钉；实施等待 draft review 通过（AGENTS.md「跨模块公共 API（nop-*-api）plan-first」程序载体——非静默扩权）。先例 = R2-02（plan 2505）批内触碰 nop-http-api（roadmap 8.11「PA 面①批内闭环 nop-http-api」）。
- **触碰形态声明（零签名变更）**：`verifyAndRename(FileTransferHelper.ExpectedChecksum)` 签名 :162 不变、方法体行为演进；消费者全仓仅 OkHttp :223 / Jdk :497 两处（live grep 已枚举）——不构成契约面演进，无 migration plan 义务（行为修正即 migration 内容本体）。
- **Phase 4 回写义务（成文）**：batch-proposal :299 PA 行修正回写（「nop-rpc-api/nop-integration-api 零触碰维持，nop-http-api FileDownloadSink 单文件经 plan 2522 plan-first 申报闭环」）+ L93 验证命令列勘误注记（勘误 1 联动）；仅在预设成立时执行。
- **备选拒绝**：采纳裁定⑤备选形态维持三叶零触碰字面——三叶零触碰声明得以维持，但以「共享语义修复退化为 impl 侧重复修复 + api 层契约持续违约」为代价；PA 保护目标（限制 api 叶触碰范围）与代码正确性冲突时以正确性优先、范围受控触碰经 plan-first 程序申报（§六）。

### 3.8 裁定⑦：SftpClient 全链超时 + 配置面（C3-024，C6-018[R2-20] 同文件边界）

- **配置面（预设，一次定夺）**：`SftpConfig` 新增单一 `timeoutMillis`（int，缺省 `15_000`，量级对齐 JdkFeishuHttpApi 15s 同仓先例；getter/setter 沿 `strictHostKeyChecking` 既有形态；`@ConfigBean` 配置面新增为模块内手工维护源，非 `_` 生成物）。**分段配置（connect/read 双值）被拒**：jsch `setTimeout` 为底层 socket SO_TIMEOUT——per-read 语义，活动传输每个读操作重置计时，长传输天然无需「总时长」预算；分段零收益且配置面翻倍；OSS SDK 10s/50s 为单请求上界语义，与 SO_TIMEOUT 不同构不迁移（§六）。
- **三段接线（同值）**：`openConnection` 段——`session.setTimeout(config.getTimeoutMillis())`（:157 setConfig 之后、:163 connect 之前）+ `session.connect(config.getTimeoutMillis())`（:163 改参）+ `channel.connect(config.getTimeoutMillis())`（:165 改参）。连接段（TCP connect + banner 读 + channel 开启）与读段（SO_TIMEOUT）一次闭合；超时异常经既有 `ERR_SFTP_CONNECT_FAIL` 包装路径（:112-116）外抛，操作段异常经既有 per-op 包装路径外抛。
- **覆盖矩阵（8 个 channel 操作）**：listFiles/getFileStatus/uploadFile/uploadResource/downloadFile/downloadToStream/getInputStream/deleteFile 全经 session SO_TIMEOUT session 级单点统一覆盖——jsch 无 per-op timeout 参数，覆盖矩阵即 session 级接线，无需逐操作改动。
- **长传输读包络声明（契约句 owner doc SFTP 新节登记）**：`getInputStream`（:295-304）/`channel.get` 流式读受 SO_TIMEOUT per-read 有界——静默停滞（丢包/分区无 RST）≤timeout 即失败；总时长无上界（流式传输 by design，无总时长预算——与裁定① feishu send 单帧预算语义不同域）。
- **C6-018 边界注记（零触碰）**：构造期「session 已连、channel 未开」失败回收面（connect 失败路径 session.disconnect）归 R2-20——本批 `connect(ms)` 抛超时异常路径恰为 R2-20 回收面的触发前提（可观测形态预留达成）；两单共改 `SftpClient.java` connect 段，R2-19 先行落地后 R2-20 以 live 为基线续作（排序注记无硬依赖）；禁「顺手」补回收逻辑。SMTP 次级同族（配置义务未文档化）注记不代做。

## 四、对齐纪律条款（逐条成文）

1. **MA-04 吸收面一处闭合**：C3-015 单 diff 同时闭合 C6-024（close sendClose join 有界 :118 + connect join 阻塞段移出 scheduler）/CL-005（send 无超时挂死 + 心跳失败可见性 debug→warn）/CL-006（connect join 阻塞 scheduler 至 15s）——吸收成员不单列、不四处改；失败处理单点即 MA-04「失败主动 close 并回调 onError 驱动重连」的落地形态；MA-04 休眠面对称条款（ChannelConnectorManager 面）随批注记维持，本修复不解除不激活。
2. **MA-05 × MA-06 同文件相邻不并族、两单同批实施**：裁定②（调度去重 + 陈旧回调丢弃）与裁定③（在途建连代际撤销）共用 `lifecycleGeneration` 令牌基元但修复点互异（守卫点矩阵 §3.3/§3.4），Phase 2 同组实施、同文件协同 diff，禁合并成单一守卫；cra-index :277/:278 两行分立回填；世代令牌单点语义（start/stop 双点递增、单一相等谓词）由本文一次定夺。
3. **B1-07/B1-08 转正对称条款**：「消费面回撤即转条件化 P2」（family-clustering :518-519 SSOT，文本以 plan Current Baseline 节为准）——feishu 链休眠条款不适用 MA-05/MA-06（B1-07/08 维持 P1：FeishuConnector 实仓可达 + C10-005 假体掩蔽三重证据）；Phase 4 cra-index :277/:278 行内「判级留 7.1」注记改写为转正终态注记落账。
4. **C3-001 × R21 族纪律对齐**：completeExceptionally 终结模式对齐族先例（4.2-C3-001 plan 2521 范式引用不重开）；禁依赖上层超时兜底；族内三案零同站点重复单列（跨单元同号防混：4.3-C3-001 ≠ 4.2-C3-001）。
5. **C3-005 × R11 族纪律 + C6-001 同站点协同零回退**：null 分支强制 rename（成功信号与产物落地原子性）；与 C6-001 P0 sink.close 修复共居下载完成链——sink.close 语义与消费顺序零回退；4.3-C3-005 与 3.4-C3-005（plan 2520）同号异站防混。
6. **C6-005 × CL-012 同站点不同本体不互吸**：本批只落流关闭面（fd 泄漏本体）；CL-012 BASE64 内存峰值面（≈3× 文件 OOM 族）留 C5/C7 轨——IoHelper.readBytes javadoc 契约声明单行行为零变更，禁改 readBytes 关闭语义（其他调用方零影响义务）；4.3-C6-005 与 4.4-C6-005 同号异站防混。
7. **C3-024 × C6-018[R2-20] 同文件协同边界**：本批超时面（setTimeout/connect(ms)/配置项/包络声明）、R2-20 构造回收面（session.disconnect on channel 失败）零触碰；connect(ms) 超时异常路径 = R2-20 回收触发前提的注记成文；4.3-C6-021 与 C6-022（NettyTcpClient）同构异站零关联防混。
8. **C10 三条同 diff（E2，半边口径按 plan 勘误 5）**：C10-005[MA-04/05/06 全条]/C10-004[C6-005+C3-024 半边]/C10-014[C3-001 半边]与各自本体同 diff 落地，禁先修本体后补测拆分交付（§5.5 集落名）。
9. **裁定①②③同文件三单交叉改写顺序（勘误 11 义务）**：①先行落超时预算与 connectAsync seam（基建面）→ ②引入世代令牌 + 单飞标志（字段与守卫谓词）→ ③接线三消费点矩阵（消费②令牌与①完成回调）；单 diff 协同、同批一个提交序列，禁「顺手合并单一守卫」；落地后 §3.2 兼容核对表逐条留痕（Phase 2 落 `_tmp/`）。
10. **无跨批硬依赖（可并行）**：批内软序 = ②③同组实施 + ⑤⑥⑧同属下载/上传完成链建议同组实施；E3 盲区处置（勘误 1）：feishu/sftp 四载体经补充焦点腿 `./mvnw test -pl nop-integration/nop-integration-feishu,nop-integration/nop-integration-sftp` 强制覆盖。

## 五、裁定⑧：测试缝与零回退钉

### 5.1 红①–红⑦驱动器（先红后绿，E2；缺陷态红判据 → 修复后绿口径）

| 红 | 成员 | 载体形态 | 缺陷态红 → 修复后绿 |
|----|------|---------|-------------------|
| ① | C3-015 | 真实单线程 scheduler（非 NoopScheduler——死锁红须真实承载线程）+ FakeStreamTransport.send 可编程 latch 挂起（假体升级①）+ sendHeartbeat 直调触发 → 断言超时预算内 scheduler 可执行后续排程任务（第二心跳/排程探针任务） | 缺陷态：sendHeartbeat 占死唯一 scheduler 线程、后续任务永不执行红 → 有界 send 超时 → 失败单点 close + scheduleReconnect 绿。**挂死类：失败模式 = 超时，给足有限 timeout（≥5s）并在钉内注释失败模式语义**（2521 红④先例）；trial 上界 3（latch 确定性交错单 trial 即红证，上界防环境噪声） |
| ② | C1-009 | fake listener 注入 onError→onClose 双终态序列 + NoopScheduler.scheduledCommands 手动逐条执行（假体能力在案）→ 断言在飞重连恰一（connectCount==1）；stop→start 后旧世代 scheduled command 执行 → 断言被丢弃（connectCount 不增殖） | 缺陷态：双任务先后执行 connectCount==2 红 / 陈旧回调建第三连红 → CAS 单飞 + 世代过期丢弃绿；前提(a) 回码复核结论随钉登记；trial 上界 3（序列注入确定性） |
| ③ | C6-021 | FakeStreamTransport.connectAsync/connect latch 门控挂起（假体升级①）——挂起期间 stop() → 释放 → 断言新建立连接被世代守卫立即关闭（`connectCount - closeCount ≤ 1` 假体升级③计数断言）+ connected 保持 false | 缺陷态：孤儿连接存活、listener 复活 connected 红 → 消费点 世代关闭绿 + onOpen 复活窗 封堵绿；trial 上界 5（真实并发交错面，stop×在途时序 latch 门控确定性构造） |
| ④ | C3-001 | loopback HttpServer 短连接半关闭（响应头到、body 中断——零新依赖，§3.5 预设）→ fetch() 断言 `get(10s)` completeExceptionally；uploadAsync 同型第二钉（同测试类两钉） | 缺陷态：future 永不完成 get 超时红 → onResponse 体终结绿。**挂死类：失败模式 = 超时给足有限 timeout + 钉内注释** |
| ⑤ | C3-005 | nop-http-api sink 直测：begin→write→finish→`verifyAndRename(null)` → 断言目标文件存在且 .part 不存在（tmp 目录隔离） | **当前即红 = 先行失败测试**（非并发零 trial 义务）→ null 分支 rename 绿；伴生消费方钉（okhttp/jdk 下载全链 null 校验源 → 目标文件存在）随端到端义务承载 |
| ⑥ | C3-024 | recording-jsch 假体（`HostKeyRecordingJsch` 真实离线 Session 先例形态，TestSftpHostKeyChecking :48-63）+ `SftpConfig.setTimeoutMillis` 短值（如 500ms）注入： 传值钉——`session.getTimeout() == 配置值`（jsch 0.1.55 public getter live javap 亲证，plain-JVM 直证）； 超时包络行为钉——loopback `ServerSocket` banner 黑洞（accept 后不发 banner）→ 构造断言在 timeout+margin 内失败（`assertTimeoutPreemptively`） | 缺陷态：getTimeout()==0 ≠ 期望值（当前即红）且黑洞探针无限挂死（失败模式 = 超时，preemptive bound 红）→ 显式传值后两钉绿。**判据挂「超时包络真实生效」而非字面重载参数捕获**（jsch Session 不可子类化、connectTimeout 无 getter——字面捕获不可达，证据形态登记）；构造失败注入点断言 = 既有 `ERR_SFTP_CONNECT_FAIL` 包装钉保持（C10-004 建议②形态） |
| ⑦ | C6-005 | 三实现 BASE64_FORM 上传 N=30 次 fd 增量断言（`TestFileDownloadSinkClose` DOWNLOADS=40/MAX_FD_GROWTH=10 先例形态——上传变体 N=30/阈值 5，连接池常驻浮动余量） | **当前即红（线性泄漏）** → try-with-resources 绿。**平台门控[R1 Minor-F3]**：`@EnabledIf(openFileDescriptorCountAvailable)` 沿先例 :44——不可用平台 skip 而非红，届时以变异论证补红证（注释 close 行 → 钉红 → 恢复后 grep 断言目标行在位[R5 教训]） |

### 5.2 测试资产落位

- **feishu（nop-integration-feishu client 测试包，补充焦点腿）**：C10-005 假体升级三件套 = ①send/connect 可编程挂起 latch（FakeStreamTransport 增 latch 字段，**缺省 null 不挂起**——缺省形态与现状等价，11 钉载体零行为差）；②NoopScheduler shutdownNow 后 schedule 抛 REE（**开关缺省关**——仅红②/后续 R4-26 显式开启，不在生产代码引入 REE）；③连接计数断言 `connectCount - closeCount ≤ 1`（FakeStreamTransport connectCount/closeCount 计数在案，差值断言新增即可）。红①②③ = 新增三测试类（工单号后缀命名沿 `TestBodyPhaseCancel_C3_002` 惯例：`TestFeishuSendBounded_C3_015`/`TestFeishuReconnectSingleFlight_C1_009`/`TestFeishuStopRaceGeneration_C6_021`），显式开启 latch/REE 能力。既有 `TestFeishuClient`（11 钉 + fakes）零改写。
- **http 三 impl 叶 + nop-http-api**：红④两钉 = okhttp 测试包新增 `TestFetchFutureTermination_C3_001`（fetch/upload 两钉同类）；红⑤ = nop-http-api 新增 `TestFileDownloadSinkVerifyAndRename_C3_005`（sink 直测）+ okhttp/jdk 消费方端到端钉；红⑦ = 三 impl 测试包各一 fd 钉（`TestBase64FormUploadFdLeak_C6_005`，@EnabledIf 门控）。
- **sftp（nop-integration-sftp，补充焦点腿）**：红⑥ = 新增 `TestSftpClientTimeout_C3_024`（recording-jsch + 黑洞 ServerSocket 探针 + `ERR_SFTP_CONNECT_FAIL` 注入点断言）。**[R2 Major-3]证据形态降级等价论证**：字面 `session.connect(ms)`/`channel.connect(ms)` 参数不可捕获（jsch Session/Channel 不可子类化，recording 假体只能在代理层记录），判据改挂「超时包络真实生效」（`getTimeout()` 直证 + banner 黑洞行为探针）；该形态无法单靠行为区分「只调 setTimeout 漏改 connect(ms)」的部分修复——**jsch 语义等价论证**（javap 亲证 jsch 0.1.55：`Session.connect()` 无参 = `getfield timeout; invokevirtual connect(I)`，setTimeout 经 socket SO_TIMEOUT 实际覆盖 channel 阶段读——setTimeout-only 与全接线行为等价）**+ 源码级 grep 断言伴生钉**（测试内读取 `SftpClient.java` 源文件断言 `session.connect(config.getTimeoutMillis())` 与 `channel.connect(config.getTimeoutMillis())` 两行在位——编译期常量级变异论证，防部分修复静默通过红⑥）。
- 全部钉 plain-JVM 可跑（seam 注入/recording-jsch/loopback HttpServer/fd 计数），无 docker 门控；红绿快照落 `_tmp/red-green-2522-summary.md` 总册 + 逐 trial 日志（feishu/sftp 腿日志单列——E3 盲区对应）。端到端义务（rule 22）：红③ stop→在途 connect→世代守卫关闭→重连单飞→心跳恢复全链（stop 入口到连接计数出口）；红⑤+C6-001 下载全链（downloadAsync 入口→verifyAndRename(null)→目标文件存在→sink.close fd 配置，同链两本体一次端到端；[R2 Minor-m7]null 分支 move 执行于 sink.close 之前——对仍打开的 FileOutputStream 做 Files.move 在 Windows 有句柄占用语义，该 e2e 钉沿既有 `HttpFileTransferTestCondition` OS 门控，与 checksum 路径同形）+ 红⑦ 上传链（uploadAsync 入口到 fd 计数出口）+ 红⑥ 连接链（newClient→connect(ms)→传值断言，构造入口到 jsch 调用出口）。接线验证（rule 23）：①超时路径真实驱动重连断言（send 超时→close→scheduleReconnect 链真达）、②③世代令牌真实消费断言（过期回调真丢弃/孤儿连接真关闭）、④completeExceptionally 真实到达返回 future、⑤null 分支 rename 真实落盘、⑦超时传值真实到达 jsch。

### 5.3 既有钉兼容与改写边界

唯一允许的既有钉改写 = **无（零改写批）**。TestFeishuClient 11 钉 + 值捕获钉逐条判定（§3.2，Phase 2 落 `_tmp/` 留痕）；`TestFileDownloadSinkClose` fd 钉（expected!=null 路径行为不变 + close 语义零触碰）；`TestFileTransfer` 三叶 20 钉（jdk 5/okhttp 4/apache 11——下载钉均带显式 checksum 或 requireChecksum 断言路径，null 分支行为演进不触达；`testBase64FormUpload` 三钉在流关闭后内容语义不变；apache `testDownloadRequireChecksumWithoutSource` :311 抛出路径零触碰）；2505 钉族（`TestBodyPhaseCancel_C3_002` 6 钉/`TestOkHttpCancelListenerCleanup_C3_012`/`TestApacheSseReactorDecouple_C6_004`——fetchAsync 包裹不触 cancel 链）；`TestSftpClientCredential` 12 钉 + `TestSftpHostKeyChecking` 5 钉（credential 解析/setConfig/WARN 断言面零触碰，构造失败包装保持）；`TestFeishuCredentialIdBinding` 2 钉/`TestJdkFeishuHttpApiOAuth` 23 钉零触碰。

### 5.4 零回退钉全列（钉零回改逐条 git diff 为空核对）

- 基线 **279 tests**（2026-11-01 亲跑双 log 在案）：`_tmp/baseline-2522-nop-network.log`（209 tests 0F0E 7 skipped——skip = `TestJdkHttpClient` 3 + `TestApacheHttpClient` 4 类级 @Disabled 历史真实端点测试，确定性非环境抖动）+ `_tmp/baseline-2522-nop-integration-feishu-sftp.log`（70 tests 0F0E——feishu 53 + sftp 17）全量零回退。
- 点名钉族零触碰：TestFeishuClient 11 钉/TestFeishuCredentialResolution 17 钉（值捕获钉实名 `reconnectUsesCapturedResolvedValues`）/TestFeishuCredentialIdBinding 2 钉/TestFileTransfer 三叶 20 钉/TestFileDownloadSinkClose fd 钉/TestBodyPhaseCancel_C3_002 6 钉/TestOkHttpCancelListenerCleanup_C3_012/TestApacheSseReactorDecouple_C6_004/TestSftpClientCredential 12 钉/TestSftpHostKeyChecking 5 钉。
- 上游环境抖动按 plan 勘误 8 三联裁定（stash 基线同红 / solo 绿 / 恢复复跑全绿）处理留痕，不得静默重跑或跳过；nop-core `TestResourceCacheInvalidationChain` 负载 flake 在 `-am` 闭包内（2517–2521 五批先例）。

### 5.5 C10 集落名（三条成员面，随本体同 diff，E2；半边口径按 plan 勘误 5）

- **C10-005 → MA-04/MA-05/MA-06（全条）**：假体升级三件套 + 红①②③ + 连接计数断言——feishu P1 三单全部承载；C1-010/C1-011 半边（stop 置 null 僵尸投递/REE 半启动）→ R4-26/R4-27 不代做。
- **C10-004 → C6-005 + C3-024（半边）**：红⑦三实现 fd 钉 + 红⑥超时传值/包络钉 + 构造失败注入点断言；其余保护对象 C6-017/C6-018/C6-019/MA-03[C6-006/C3-004] → R2-20、MA-16 → R4-26。
- **C10-014 → C3-001（半边）**：红④两钉（fetch/upload 终结性）；MA-15 半边（CompletionStage 同步抛逃逸族 + RP-1 反向钉死）→ R4-26 不代做。

## 六、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| ① 独立发送 executor（异步发送回调驱动） | 新增线程面 + 帧顺序语义（握手/ACK 按序）复杂化，diff 面大于缺陷本体；send get(timeout) 与 JdkFeishuHttpApi 同模块先例同形且失败单点已覆盖重连驱动 |
| ① send 预算硬编码常量（无配置面） | 慢链路大帧误杀不可缓解（plan Risks 明示）；2505 body-idle-timeout 配置面先例即配置化形态 |
| ① close 预算与 send 共用 15s | stop() 持 FeishuClient 锁执行 close——15s join 直接拉长停机延迟；close 尽力而为语义 5s 足够正常关闭往返 |
| ① start 路径 connect 一并异步化 | `startTransitionsToConnected` 钉的「start 返回即 connected」同步可达性破坏（fake 同步 onOpen 语义）；幂等 start 契约可观测形态漂移；start 阻塞在调用方业务线程（非 scheduler）本无缺陷 |
| ② synchronized 重连任务串行化 | 锁覆盖 attemptReconnect 全程含 HTTP 往返（≤15s）持锁面不可接受；CAS 单飞 O(1) 无锁且语义恰一 |
| ② 仅 reconnectPending 无世代 | stop→start 重启窗口陈旧回调面未闭合（B1-07 载体本体）——`started` 跨代复用缺陷仍在 |
| ② 仅世代无单飞 | 双入口（onError/onClose 双终态序列）同世代双调度两个任务均过世代校验 → 双活仍在；世代与单飞守卫互补不互替 |
| ②③ 世代纯 start 递增（stop 不递增） | stop 窗口内世代不变——完成回调须复合 started 复查才能拦截「stop 后 connect 完成」，复合谓词违反单一谓词纪律且与既有 :298 started 检查纠缠；stop 递增使「stop 即作废全部旧代在途回调」为单一谓词直接结果 |
| ②③ transport 层世代侵入（JdkStreamTransport 加世代字段） | 生命周期代际语义两处权威——transport 为无状态连接载体，世代属 FeishuClient 生命周期概念；复活武装（:41）保留入口语义、行为由消费点守卫约束 |
| ③ attemptReconnect 全程 synchronized | 完成回调在 HttpClient 线程，持锁跨 HTTP 往返 + stop 持锁 close join 构成长持锁链；世代谓词 O(1) 无锁 |
| ④ whenCompleteAsync+handle 链改写 | 重写 enqueue 载体 diff 面大于缺陷本体；try/catch 与 R21 族先例（2521 范式）同形引用不重开 |
| ④ onResponse 异常仅记日志不终结 | R21 族纪律明文禁依赖上层超时兜底——失败路径必须 completeExceptionally（rule 24） |
| ⑤ impl 侧 getPartFile() 两站点重复 move | 逻辑重复（两份 move 逻辑）+ api javadoc :160 契约持续违约 + 三实现行为分歧重建；单点共享修复一处闭合 |
| ⑤ null 分支删 .part（与校验失败同处理） | 语义颠倒——无校验来源不是损坏证据（file-transfer-design §3.4 尽力而为语义）；删 .part 使续传状态丢失、下载白做 |
| ⑤ requireChecksum 默认值改 true | 改默认 = 部署升级行为回退（无校验源端点升级即失败）；缺陷本体是落盘收尾而非校验策略 |
| ⑥ 三叶零触碰字面维持（裁定⑤备选胜出形态） | 正确性与 PA 声明冲突时以正确性优先——impl 重复修复 + api 契约违约的代价高于范围受控单文件触碰；经 plan-first 程序申报非静默扩权 |
| ⑦ 分段 connect/read 双配置 | jsch SO_TIMEOUT per-read 语义（活动传输逐读重置）使「读总时长」预算无意义；配置面翻倍零收益；OSS SDK 10s/50s 单请求上界语义不同构 |
| ⑦ 缺省 0 保持 jsch 无限语义 | 即缺陷本体（网络静默故障业务线程无限挂起）；缺省必须有界、显式 0 可作为部署方显式选择登记为不受支持面（timeoutMillis ≤0 视为非法配置抛错） |
| ⑦ 构造失败顺手补 session.disconnect 回收 | C6-018 本体归 R2-20（对齐条款 7）——顺手越批；connect(ms) 超时异常路径恰为 R2-20 回收触发前提，本批预留可观测形态 |
| ⑧ IoHelper.readBytes 改为关闭流 | 全局行为变更——其他调用方（非上传链）零影响义务违例；javadoc 契约声明 + 调用方 try-with-resources 为最小正确修复 |
| ⑧ DefaultHttpInputFile 缓存复用单流 | getInputStream 每调新建为接口契约语义（多次调用合法）；缓存单流破坏幂等契约且不解决「调用方不关流」本体 |
| 红④ mockwebserver 新依赖 | loopback HttpServer 半关闭零新依赖且先例基建在案（TestFileTransfer/TestFileDownloadSinkClose）；依赖评估降 Non-Blocking Follow-up 仅在预设形态实施期被证实不足时激活 |

## 七、与已有设计的关系

- [cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md](cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md)（plan 2521）：本文结构模板与独立终态守卫先例（裁定②世代令牌形态同族参照）；其 R21 族 completeExceptionally(adapt) 范式为裁定④引用基座——族纪律「不互相吸收」维持，跨单元同号（4.3-C3-001 ≠ 4.2-C3-001）零关联。
- [sse-backpressure-and-body-phase-lifecycle.md](sse-backpressure-and-body-phase-lifecycle.md)（plan 2505）：body-idle-timeout 配置面形态为裁定①超时预算声明同构参照；2505 钉族（C3-002/C3-003/C3-012/C6-004）零回退为硬条款；其 R2-02 批内触碰 nop-http-api 先例为裁定⑥申报先例。
- `ai-dev/design/nop-network/file-transfer-design.md`：§3.3/§3.4 契约 SSOT——裁定⑤仅裁定实现形态（null 分支 rename 强制），契约文本归属不变；owner doc `nop-network.md` :40 句修正 Phase 3 落账。
- R11 错误以成功交付族（plan 2509/2520 先例）：成功信号与产物落地原子性纪律第 4 例，模式引用不重开。
- **下游 R2-20（roadmap 8.29）**：C6-018 以本批落地后的 live `SftpClient.java` 为基线续作（connect(ms) 超时异常路径 = 回收触发前提，§3.8 边界注记）；**下游 R4-26/R4-27**：C1-010/C1-011（feishu P2 面）与 MA-15/MA-16 半边承接（禁顺手引入接缝已在 §3.3/§5.5 登记）。
- owner doc 落点（Phase 3 统一落账，本 Phase 零 owner doc 变更）：`docs-for-ai/03-modules/nop-network.md` 四处（feishu 通道生命周期契约新节/下载节 :40 rename 契约句修正/BASE64_FORM 节流关闭契约句/SFTP 超时契约新节）+ `docs-for-ai/04-reference/source-anchors.md` 新增 `NETWORK-CC-001` 锚点行（CLUSTER-CC-001 :104 之后按表序实际行号）。
