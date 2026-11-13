# C10 测试保护力审计——nop-service-framework 热路径并发单元

- **审计日期**: 2026-10-18
- **审计人**: C10 维度（测试保护力）子代理，本单元最后批次
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`（git HEAD 5994c18211，与本单元六维审计基点一致，无审计后修复提交——矩阵全部按未修复现状判定）
- **纪律声明**: 本维度只读。未修改任何测试代码、产品代码、六个维度文件；仅新增本报告。
- **输出**: 保护力矩阵（P1 单列详表 + 归组表）、缺保护发现（`nop-service-framework-hotpath-C10-001` 起）、测试套件自身缺陷注记（N 系）、统计。

## 输入口径

以同目录 6 个维度文件（C1/C2/C3/C4/C8/C9）的 `## 最终保留项` 节 + `## 维度复核结论` 节为准，合计 **63 条保留发现**：

| 维度 | 保留 | P1 | P2 | P3 | 其他 |
|---|---|---|---|---|---|
| C1 共享可变状态 | 10 | 0（C1-001 已降 P2，与 C2-004 合并、主 ID C1-001） | 8 | 2 | — |
| C2 锁与同步 | 5 | 0 | 4 | 1 | C2-006 撤案转正面样例（不入 63） |
| C3 异步链 | 17 | 3（C3-002/003/007） | 10 | 4 | — |
| C4 线程池 | 8 | 1（C4-007） | 6 | 1 | — |
| C8 背压/过载 | 10 | 2（C8-001/002） | 7 | 1 | — |
| C9 热路径 | 13 | 1（C9-012，移交功能正确性维度） | 6 | 4 | 量化记录×2（C9-007/008） |
| **合计** | **63** | **7**（含 C9-012 移交案） | **41** | **13** | 2 |

## 一、测试资产盘点

### 1.1 模块级盘点（审计对象：5 模块 7 个含测试的构建块）

| 模块 | 测试 java 文件 | 测试类（含 @Test） | @Test 计数 | 备注 |
|---|---|---|---|---|
| nop-graphql/nop-graphql-core | 39 | 34 | 158 | 引擎/订阅/WS/JSON-RPC 主力套件；5 个 fixture 辅助类 |
| nop-graphql/nop-graphql-grpc | 7 | 4 | 8 | TestProtobuf×4 / TestGrpcServer×2 / DevDoc auth×1 / client×1；3 个 proto fixture |
| nop-graphql/nop-graphql-message | 0 | 0 | 0 | **无 src/test 目录（整模块测试空洞）** |
| nop-graphql/nop-graphql-orm | 2 | 2 | 8 | OrmBatchLoader 所在模块仅 fetcher 分页语义测试 |
| nop-biz | 30 | 28 | 131 | CRUD 写限制/语义为主；TestBizActionService 仅 2 例 |
| nop-gateway | 20 | 19 | 111 | 流式缓冲/重试/转发/限流/AI 拦截器；1 个 factory 辅助 |
| nop-biz-auth-core | 18 | 17 | 118 | 过滤器/登录服务/上下文/MFA/JWT 语义套件；1 个 MockClock |
| nop-biz-file-core | 2 | 2 | 5 | **仅 5 例（文件扩展名校验 + chunk API 显式未实现）——整模块准空洞** |
| **合计（审计对象）** | **118** | **106** | **439** | — |

口径外相邻套件（用于判定「单元外零回归面」）：nop-biz-auth-api（2 类 13 例，auth-api 契约）；nop-auth/nop-auth-sso（4 类，仅 JWK **解析**与 issuer 校验，无 refreshToken 执行链/JWK 定位器拉取锁测试）；nop-auth-service、nop-rpc-core、nop-http-api 不在本单元对象内。

### 1.2 按被测面归类

| 被测面 | 代表测试类（@Test 数） | 覆盖性质 |
|---|---|---|
| GraphQL 引擎执行/DataLoader | TestGraphQLDataLoaderDispatchLifecycle(4)、TestGraphQLExecutorFetchNext(2)、TestGraphQLEngine(3)、TestGraphQLSerialMutations(1)、TestGraphQLFragmentVariables/Depth(7/4) | 端到端功能语义（带超时防挂判定） |
| WS 订阅（JsonRpcWebSocketHandler） | TestJsonRpcWebSocketHandler(18)、TestGraphQLEngineWithWebSocket(7) | 协议/身份/幂等语义，全部单线程 |
| 订阅管理器（休眠路径） | TestGraphQLSubscriptionManager(21)、TestSubscriptionInfo(7)、TestSubscriptionDefaultsBeans(2) | 注册/容量/主题解析顺序语义 |
| 订阅 publisher 变换 | TestGraphQLSubscriptionPublisher(8)、TestSysCancelOwnership(4)、TestCancelTokenManagerOwnership(6) | 变换/完成/取消归属 |
| Gateway 流式缓冲/重订阅 | StreamingProcessorBufferingTest(12)、GatewayFixTest(6) | 缓冲接线/abort/尾部交付/生命周期 |
| Gateway 重试/转发 | InvokeProcessorRetryTest(10)、AiFailoverGatewayInterceptorTest(5)、TestForwardProcessorSemantics(9)、MappingProcessorTest(7)、TestGatewayHandler(6) | Retry-After 解析钳制、重试耗尽错误语义、fallback 上限 |
| Gateway 限流/AI | AiRateLimitGatewayInterceptorTest(7)、AiAuthGatewayInterceptorTest(6)、LoggingInterceptorTest(5)、TestGatewayRejectResponse(2) | 限流正常路径/桶上限/TTL、拒绝响应形态、日志聚合 |
| Auth 过滤器 | TestAuthHttpServerFilterSemantics(18)、TestSysUserContextContract(4)、TestRedirectValidation(4) | 凭证解析/半衰期判定/重定向防护/401 形态 |
| Auth 登录与上下文 | TestAbstractLoginServiceSemantics(12)、TestUserContextImplSemantics(12)、TestJwtAuthTokenProvider(6) | flush 契约/dirty 语义/角色判定/令牌用途混淆 |
| MFA/限次存储 | TestLocalMfaChallengeStore(16)、TestLocalEmailCodeStore(10)、TestLocalSmsCodeStore(8)、TestLocalLoginAttemptStore(4) | 含 4 个真并发断言用例（见 1.3） |
| biz CRUD/装饰器 | TestCrudBizModelWriteLimits(19)、TestGraphQLCrudSemantics(16)、TestCacheActionDecorator(6)、TestDownloadHelper(2) 等 | CRUD 语义/缓存装饰器顺序语义/下载流 |
| gRPC | TestProtobuf(4)、TestGrpcServer(2) | Int/String marshaller 往返 + 端到端服务 |

### 1.3 并发覆盖空洞（关键结构性事实）

全审计对象 439 个 @Test 中，**含多线程并发断言的用例仅 4 个**：

1. `TestLocalMfaChallengeStore#testIncrFailCountConcurrentNoLostUpdates`（16 线程×50 次无丢失更新）
2. `TestLocalMfaChallengeStore#testMarkVerifiedConcurrentExactlyOneWinner`（16 线程恰一胜者）
3. `TestLocalLoginAttemptStore#testConcurrentIncrementNoLostUpdates`
4. `TestBeanMethodBatchFetcher#testConcurrentFirstRegistrationSharesLoader`（并发首注册共享 loader）

另有 2 个伪并发/异步感知用例：`GatewayContextImplTest#testThreadSafety`（2 线程后**零断言**，见 N2）、`TestGraphQLSubscriptionPublisher`（SubmissionPublisher 异步投递，无线程断言）。其余 433 个用例全部单线程顺序执行——对本单元 63 条保留发现中 40+ 条竞态/取消/线程亲缘类缺陷，现有套件**结构上不可能产生红色信号**。

### 1.4 缺口明细（测试空洞登记）

- `BizObjHelper.loadByProp`/`MapCache` 锁内 invoke 面：0 引用。
- `TccContextInvoker`/`ContextBinder`/`TransformFetcher`/`EvalActionTransformFetcher`：0 引用（TransformFetcher 仅经引擎端到端间接触达）。
- `DefaultFlowControlRunner`/FlowControl：0 引用（C3-016/C8-008 双双裸奔）。
- `DaoUserContextCache`/`use-dao-user-context-cache`：测试域 0 引用。
- `AbstractServerEventSubscription`（nop-http-api）：5 模块测试域 0 引用。
- `LocalDateTimeFieldMarshaller`/grpc DateTime marshaller：0 引用（TestProtobuf 仅 Int/String）。
- keepAlive/globalTimer/GlobalExecutors：graphql-core 测试域 0 引用。
- executeBlocking 仅作为 FakeHttpContext 的同步内联实现出现（3 个 gateway/auth 测试文件），无任何载体断言。
- nop-graphql-message：整模块无测试目录；nop-biz-file-core：仅 5 例（C8-010 文件面零指标无对测面）。

## 二、P1 单列详表（7 条，逐条不归组）

三档口径：**有钉**＝存在现成测试，缺陷核心机制回归时会红；**部分**＝至少一个缺陷相关面有钉（给出测试类#方法），但缺陷主面无钉；**无保护**＝无任何对缺陷面有效的红色信号。判定均经实际打开测试文件核对。

### P1-1｜C3-002（invokeOperation 出口泵/递归泵无 thenOnContext，dispatch 于 future 完成线程脱离请求 IContext）——**部分**

- **有钉面（活性）**: `TestGraphQLDataLoaderDispatchLifecycle` 4 用例（testAsyncListBatchLoaderCompletes / testSyncListSingleBatch / testNestedAsyncFieldBatchLoaderCompletes / testAsyncSingleOpBatchLoaderCompletes）以 5s 超时端到端断言泵链活性与「恰一批」聚合——泵的 liveness 回归（P1-2.6-004 修复被回退）必红。**反向验证通过**：四用例的批计数/键覆盖断言真实有效，修复前形态（TimeoutException）在注释中有探针记录。
- **无钉面（本缺陷本体）**: 上下文传播维度零覆盖。fixture `batchName` biz loader 对 `IContext`/执行线程完全不敏感——dispatch 在完成线程执行、脱离请求 IContext/ORM 会话时，四个用例全部照常绿。无任何「loader 执行线程可见请求 context」断言。
- **缺口**: 建议 C10-001。

### P1-2｜C3-003（BufferedStreamingPublisher 背压断裂：越窗 demand=0 无界缓冲 + 自驱动 request(1) + onError 不 cancel 上游）——**部分**

- **有钉面（缓冲层功能契约）**: `StreamingProcessorBufferingTest` 12 用例——`inWindowFailureTriggersRetryCallbackAndResubscribes`（重订阅接线）、`bufferedTailElementsDeliveredBeforeComplete`（demand=0 时缓冲尾部不丢，**反向验证通过**：ManualDemandSubscriber 真实门控 demand，断言先不交付、request(2) 后全量交付再 complete）、`abortAfterResubscribeCancelsNewSubscription`（abort 委托新活跃 sub，且断言旧 sub 已取消）。
- **无钉面（缺陷本体）**: 无界增长与 demand 门控零覆盖——无任何「上游持续泵入 + 下游 demand=0 时缓冲有界/上游停注」断言；`onError 不 cancel 上游`路径无测试（`inWindowFailureCallbackDeclinesForwardsError` 断言断流报错，不断言上游订阅取消）。生产联动面（nop-ai-gateway 注入 retryCallback 使缓冲路径成为 AI 流式默认形态）在 gateway 测试域无对应 AI 流式端到端用例（AiFailover 测试全部非流式）。
- **附注**: `bufferedTailElementsDeliveredBeforeComplete` 钉住的是「缓冲后必交付」，与 demand 门控修复兼容（门控后缓冲不再增长、尾部交付语义不变），不构成钉死缺陷行为；但若修复改为超限熔断式，该用例需随行复核——见 N 系观察。
- **缺口**: 建议 C10-002。

### P1-3｜C3-007（BizActionService.callActionAsync 裸 execute 不传播调用方 IContext，池线程 context 跨任务残留）——**无保护**

- `TestBizActionService` 仅 2 用例，全部针对「未知 action 名/无 xbiz 模型抛语义化 NopException」（错误码 + bizObjName/actionName 参数）。**不触达 executor 提交路径**：两用例均在 actionModel 解析失败处断言抛出，从未走到裸 `execute` 提交；无 context 传播、无 ServiceContextImpl 附着/detach、无 worker 线程残留断言。nop-biz 其余 28 个测试类亦无 callActionAsync 成功路径测试（grep 全测试域仅 TestBizActionService 一个引用点）。
- **缺口**: 建议 C10-003。

### P1-4｜C4-007（SSO 半衰期刷新在认证载体线程同步阻塞 IdP，brownout 下全站吞吐坍缩）——**部分**

- **有钉面（触发判定）**: `TestAuthHttpServerFilterSemantics#testIsNeedRefreshHalfLife`——**反向验证通过**：真实断言「剩余寿命<half-life → 需刷新 / 新鲜 token → 不刷新 / autoRefresh=false → 永不刷新」三态，钉住进入刷新路径的门条件。
- **无钉面（缺陷本体＝执行链）**: 刷新执行链（executeBlocking 载体 → runOnContext 内联 → `:359` 同步 refreshToken → syncGet 阻塞）在 5 模块测试域零覆盖——18 个过滤器用例只调 getAuthToken/isNeedRefresh/checkLogoutUrl/responseNotLogin/processOAuthCode/newSysUserContext，无一执行 handleUserContext 刷新支路；无「刷新期间线程被钉住时长」「IdP 失败后不降频重复阻塞」断言。缺陷本体代码 `OAuthLoginServiceImpl.refreshToken:258` 在单元外 nop-auth-sso，其测试域（TestOAuthLoginServiceImplContract 等 4 类）同样只测 issuer/解析契约，**全仓无 refreshToken 执行链测试**。
- **缺口**: 建议 C10-004。

### P1-5｜C8-001（WS 推送同步阻塞发送，慢客户端钉死全局 4 线程 timer）——**部分**

- **有钉面（功能面）**: `TestJsonRpcWebSocketHandler` 18 用例（订阅/数据帧/complete 帧/4409 重复 id/身份绑定/tokenRefresh 4403）+ `TestGraphQLEngineWithWebSocket` 7 用例（真实引擎端到端订阅/退订/onClose）+ `TestGraphQLSubscriptionManager#testOnMessage_*` 5 用例（消息线程同步 push 行为）——推送功能语义回归有网。
- **无钉面（缺陷本体）**: 阻塞/线程域维度零覆盖——MockWebSocketSession.sendMessage 为内存 list 追加，永不阻塞、永不抛异常（见 N5）；无 keepAlive 定时任务测试（graphql-core 测试域 grep keepAlive/globalTimer = 0）；无「慢客户端不阻塞发布线程/timer 线程」断言；无 getAsyncRemote/getBasicRemote 选型守卫。
- **缺口**: 建议 C10-005。

### P1-6｜C8-002（Gateway 429 重试×failover 放大 ×7，在途无准入上界，高 QPS 确定性 OOM 轨迹）——**部分**

- **有钉面（放大链的两个组成面）**: `InvokeProcessorRetryTest` 10 用例（**反向验证通过**：`invokeUrl_429exhausted_throwsWithStatusAndBody`/`invokeUrl_5xxexhausted_throwsWithStatusAndBody` 真实断言重试耗尽后的错误码/httpStatus/responseBody 三参数；parseRetryAfter 七例钉住 Retry-After 解析与 30s 钳制）；`AiFailoverGatewayInterceptorTest#maxRetries_capsTotalFallbackAttempts`——**反向验证通过**：maxRetries=1 + 3 个 fallback URL 下断言 `client.size()==1`，fallback 腿上界真实受测试约束（配置项非无效契约）。
- **无钉面（P1 承重面）**: 在途并发上界/准入闸零覆盖（无任何并发在途计数断言）；主 URL 腿内部 1+3 重试次数无调用计数断言（耗尽测试未断言 client.size()==4）；InvokeProcessor×AiFailover 的 **加法组合**（4+3=7）无组合级测试；高 QPS 累积轨迹无负载形态测试（属压测范畴，单元测试域可不补，但准入闸回归测试必须补）。
- **缺口**: 建议 C10-006。

### P1-7｜C9-012（LocalDateTimeFieldMarshaller 声明定长 vs 实写 16–29 可变长，亚秒序列化必败/整点零秒解析必败；移交功能正确性维度）——**无保护**

- 测试域 grep `LocalDateTime|DateTime`（nop-graphql-grpc/src/test）＝0。`TestProtobuf` 4 用例仅覆盖 IntFieldMarshaller/StringFieldMarshaller 的 parse/往返；GenericObjSchema.toByteArray 的 computeSize/实写一致性契约（缺陷所在）无任何直接断言。两种失效模式（模式 A OutOfSpaceException / 模式 B 尾部脏 0x00 + invalid tag）均无对测。C9 复核的本地 probe（`_tmp/c9r2-cl015/Cl015Probe.java`）未回归化为测试。
- **缺口**: 建议 C10-007。

## 三、归组保护力矩阵（P2/P3，按同族设施归组）

| 组 | 发现 ID（判级） | 缺陷面 | 有钉证据（测试类#方法） | 无钉面 | 判定 |
|---|---|---|---|---|---|
| G1 用户上下文设施 | C1-001(P2 主 ID)、C2-004(P2 并案)、C1-002(P2)、C1-009(P2) | UserContextImpl 混合同步/dirty 丢更新/锁面不闭合/ExtensibleBean 旁路 | `TestUserContextImplSemantics`×12（testAddRemoveRoleDirtySemantics、testTokenSettersAlwaysMarkDirty、testDirtyOnlyOnActualChange、testAttrsAndLastAccessTimeDirtySemantics——dirty/roles 顺序语义）；`TestAbstractLoginServiceSemantics`×12（testFlushCleanContextSkipsCacheSave、testFlushDirtyContextSavesAndClearsDirty——flush 契约） | 全部为单线程顺序钉；竞态/可见性/clearDirty 时序/R-CL-1 登出复活交错零并发测试。注意：现有顺序钉在「统一同步域或不可变快照」修复下应保持绿，可作修复回归网的一半 | **部分**（契约面有钉、竞态面零） |
| G2 订阅注册表设施 | C1-003(P2)、C1-005(P2)、C2-005(P2)、C3-014(P3 dormant)、C8-004(P2) | 注册/注销 TOCTOU、容量击穿、幽灵条目、孤儿双 map、僵尸账本 | `TestGraphQLSubscriptionManager`（testRegisterSubscription_duplicateOperation_throws、testRegisterSubscription_tooManySubscriptions_throws、testUnregisterSubscription、testCancelSessionSubscriptions）；`TestJsonRpcWebSocketHandler`（testDuplicateSubscriptionId→4409、testReuseIdAfterComplete、testReuseIdAfterError） | 全部单线程——TOCTOU 三连/双 map 孤儿交错在单线程下不可构造，现有钉对缺陷复现**无红色能力**（见 N7）；`subscriptionsByOperation` 条目数上界不变式无断言 | **部分**（顺序语义有钉、并发面零） |
| G3 上下文监视器失配 | C1-004(P2) | GraphQLExecutionContext getRequest synchronized / setRequest 非同步 | 无（引擎测试族无 monitor 断言） | 全部 | **无保护** |
| G4 MFA/限次存储字段 | C1-006(P2) | MfaChallenge.verifiedAt 非 volatile 外发共享 | `TestLocalMfaChallengeStore#testIncrFailCountConcurrentNoLostUpdates`、`#testMarkVerifiedConcurrentExactlyOneWinner`（**反向验证通过**：16 线程真并发、断言无丢失/恰一胜者）；`TestLocalLoginAttemptStore#testConcurrentIncrementNoLostUpdates` | 并发钉护住的是 compute 守护的安全路径；缺陷本体＝peek 外发对象的字段可见性（跨线程读 verifiedAt），无读侧可见性断言 | **部分** |
| G5 惰性 map 发布 | C1-007(P3，防御性立案) | buildSelectionMap 非同步惰性 HashMap | `TestGraphQLFieldDefinitionDeepClone#testDeepCloneCopiesRuntimeAssemblyFields`（仅深拷贝功能面） | 共享触达点现行未证实，无对测面 | **无保护**（附防御性注记） |
| G6 警告标志 | C1-008(P3) | actionAuthNoCheckerWarned 非 volatile | `TestGraphQLEngineActionAuthWarn`×2、`TestGraphQLActionAuthStrictModes#…(:68)` 语义有钉——但测试正是该字段唯一读取者（见 N3） | 可见性面（生产零读取，测试单线程读） | **部分**（语义有钉/可见性无意义级） |
| G7 Dao flush 写写冲突 | C1-010(P2) | DaoUserContextCache RMW/clearDirty 契约/flush 返回值丢弃 | 无（测试域 0 引用；代码在单元外 nop-auth-service） | 全部 | **无保护**（单元外同链，见 C10-015） |
| G8 dispatchAll 锁内批 I/O | C2-001(P2) | 持上下文监视器执行批量 biz 调用/ORM flush | `TestGraphQLDataLoaderDispatchLifecycle`×4（批生命周期/单批聚合）+ `TestBeanMethodBatchFetcher`×5（含并发首注册）——修复工单自引「回归 P1-2.6-004 对应测试」 | 锁持有时长/锁内 I/O 移出断言零（性能/临界区面） | **部分** |
| G9 流监视器内回调 | C2-002(P3) | BufferedStreamingPublisher 监视器内发起连接/重试回调/生命周期 | 无（nop-http-api 设施在 5 模块测试域 0 引用）；相邻：StreamingProcessorBufferingTest 钉回调经 context attribute 注入的功能面 | 锁域归属 | **无保护**（单元外设施） |
| G10 loadByProp 锁内 invoke | C2-003(P2) | CHM computeIfAbsent 内完整 biz invoke | 无（BizObjHelper/loadByProp 测试域 0 引用） | 全部 | **无保护** |
| G11 泵观测性/版本依赖 | C3-001(P3) | 批失败无日志、3.2.2 语义隐式依赖 | `TestGraphQLDataLoaderDispatchLifecycle`×4（成功路径泵活性） | 失败路径可观测性断言、依赖守卫声明 | **部分**（活性面有钉/观测性无钉） |
| G12 重试钳制缺失 | C3-004(P2) | 重订阅链无尝试上限/无退避/同步失败热循环 | `StreamingProcessorBufferingTest#inWindowFailureTriggersRetryCallbackAndResubscribes`（**单次**重试接线，非缺陷面） | maxAttempts 钳制/退避/与 AiGatewayFailoverInterceptor 预算对账——零断言（钳制尚不存在，测试无从护起） | **无保护**（接线面有钉不构成缺陷回归保护） |
| G13 订阅取消终态机 | C3-005(P2)、C3-009(P2) | cancel 不 complete context；gRPC onCancel 空转 | C3-005 相邻钉：`TestJsonRpcWebSocketHandler#testUnsubscribe`（publisherCancelled 断言）、`#testReuseIdAfterError`；`TestGraphQLSubscriptionPublisher`×8（变换/完成/错误 publisher）。C3-009：无（TestCancelTokenManagerOwnership/TestSysCancelOwnership 属 cancel 归属校验，另一设施） | context 终态（fireBeforeComplete/unregister 收尾）断言零；gRPC onCancel/cancelToken 接线零 | C3-005 **部分**；C3-009 **无保护** |
| G14 WS 错误语义五分支 | C3-006(P2) | 同步异常穿透关连接/onError 无终止帧/closed 分支不 cancel/keepAlive 静默终止 | `TestJsonRpcWebSocketHandler#testReuseIdAfterError`（onError 后连接不断开 + activeOperations 清理——分支 (b) 的相邻面） | (a)(c)(d) 三分支零覆盖；onError 是否向客户端发错误帧无断言 | **部分** |
| G15 TCC 上下文纪律 | C3-008(P2)、C3-017(P2) | set/restore 同线程假设、裸附着/条件 detach | 无（TccContextInvoker 测试域 0 引用） | 全部 | **无保护** |
| G16 Gateway 断连取消 | C3-010(P2) | 普通转发无断连挂接、重试不查取消态、无退避 | 无（提交 299f7765f0 明示「断连取消」暂缓，无对测）；相邻钉属 C8-002/C4-003 记录 | 取消传播全链 | **无保护** |
| G17 flush fire-and-forget | C3-011(P2) | saveUserContextAsync 失败异常无人消费 | 无（过滤器刷新执行链零覆盖，同 P1-4） | 全部 | **无保护** |
| G18 RPC 入口上下文 | C3-012(P2)、C3-013(P3 dormant) | gRPC/WS/消息/文件入口无 binder；ContextBinder dormant | 无（ContextBinder/newRpcContext 绑定语义测试域 0 断言；GraphQLWebService 测试无 tenant/locale 头绑定用例） | 全部 | **无保护** |
| G19 TransformFetcher 挂接 | C3-015(P2) | thenCompose 无 thenOnContext | 无（TransformFetcher/EvalActionTransformFetcher 测试域 0 直接引用；引擎端到端未带 context 敏感断言） | 全部 | **无保护** |
| G20 流控契约 | C3-016(P3)、C8-008(P2) | maxWaitMillis=0 契约；单全局桶无 keying、双站点共享 | 无（FlowControl 测试域 0 引用） | 全部 | **无保护** |
| G21 defaultWorkExecutor | C4-001(P2) | 全仓无 bean 定义、无启动期校验 | `TestBizActionService`×2 钉 action 缺失错误面（executor 解析在断言点之后未触达） | executor 名解析失败面/启动期预检 | **无保护** |
| G22 keepAlive 定时任务 | C4-002(P2) | 每连接周期任务挂全局 timer、接线失败永驻 | 无（keepAlive 测试域 0 引用） | 全部 | **无保护** |
| G23 网关重试链编排 | C4-003(P2 降级) | 幽灵重试（无取消传播）、≈90s 挂起、调度载体治理 | `AiFailoverGatewayInterceptorTest#maxRetries_capsTotalFallbackAttempts`（fallback 腿上界有钉——**反向验证通过**）；`InvokeProcessorRetryTest`（Retry-After 钳 30s） | 取消检查/幽灵腿/挂起时长上界 | **部分**（上界面有钉/取消面零） |
| G24 executeBlocking 载体 | C4-004(P2)、C9-011(P3) | 整链包 blocking 载体、强制双跳 | 无（FakeHttpContext.executeBlocking 同步内联，结构上掩蔽载体维度，见 N4） | 全部 | **无保护** |
| G25 临时 zip 清理 | C4-005(P2) | 一次性全局 timer 任务、delete boolean、异常吞没 | `TestDownloadHelper#testNewZipOutputFailureClosesUnderlyingStream`（异常路径即时删除——**正面对照面**有钉）、`#testDownload`（功能面） | 延迟任务/失败永驻/对账扫描——零断言 | **无保护**（正面对照面有钉） |
| G26 GrpcServer 池治理 | C4-006(P3) | destroy 无 awaitTermination、10000 深队列 | 无（TestGrpcServer 仅端到端调用 + initPropId） | 生命周期/队列深度 | **无保护** |
| G27 JWKS 定位器 | C4-008(P2) | 锁内无超时同步拉取、失败静默占速率窗 | 无（单元外 nop-auth-sso 测试仅 JWK 解析/错误码规范化） | 全部 | **无保护**（单元外同链，见 C10-015） |
| G28 流式聚合缓冲 | C8-003(P2) | OpenAiDeltaAccumulator 无响应长度上界 | `LoggingInterceptorTest#streaming_accumulatesDeltasAndLogsOnComplete`、`#streaming_accumulatesToolCalls`（聚合正确性有钉） | 上界/截断行为零断言 | **部分**（正确性有钉/上界面零） |
| G29 限流墙钟 | C8-005(P2) | 墙钟 refill、时钟回拨全桶停注、Retry-After 恒 1s | `AiRateLimitGatewayInterceptorTest`×7（underLimit/overLimit/独立桶/xff 解析/maxTrackedKeys 上限/idleTtl——正常路径与容量面有钉）；`TestGatewayRejectResponse#rateLimitRejection_preserves429AndRetryAfter`（拒绝形态） | 时钟回拨注入（MockClock 在 auth 域有先例，gateway 域未用）、负 elapsed 防护 | **部分** |
| G30 网关零指标 | C8-006(P2)、C8-010(P3) | 限流/在途/缓冲/重试、文件链路零指标 | 无（指标断言 0） | 全部 | **无保护** |
| G31 WS 聚合上界 | C8-007(P2) | per-connection 1000 硬编码 ×N 无准入 | 无（休眠 manager 容量钉属 G2；live 面per-connection 容量/聚合上界零断言） | 全部（live 面） | **无保护** |
| G32 WS 会话活性 | C8-009(P2) | 发送失败仅 WARN、单向 ping、无 idle timeout | 无（Mock session 永不失败，见 N5） | 全部 | **无保护** |
| G33 选择树重建 | C9-001(P2) | 每请求三遍重建合成选择树 | 无（TestGatewayHandler 不触达合成选择构建） | 全部（修复为缓存后的一致性网也缺位） | **无保护** |
| G34 模型缓存 stat | C9-002(P2) | handlerCache 无节流变更检查 | 无 | 全部 | **无保护** |
| G35 ensureKeys 串行点 | C9-003(P2) | synchronized 全平台认证串行 | 无（TestJwtAuthTokenProvider 单线程直构） | 全部 | **无保护** |
| G36 MAP/ANY 双序列化 | C9-004(P2) | computeSize 与 write 各序列化一次 | `TestProtobuf`×4（Int/String 往返——marshaller 框架正确性网） | MAP/ANY 路径零覆盖、序列化次数无断言 | **无保护**（框架面有钉） |
| G37 流式逐元素往返+锁 | C9-005(P2) | 每元素 parse→拦截器→serialize、监视器跨回调 | `StreamingProcessorBufferingTest`（映射链语义经 FakeServerEvent JSON 字符串真实断言）+ `GatewayFixTest#interceptedInvocation_proceedOnStreamElement_passesTransformedValue`（变换值传递） | 事件循环线程归属/锁面无断言 | **部分**（映射语义有钉） |
| G38 入口日志量 | C9-006(P2) | INFO 全量 query 日志 | 无 | 全部 | **无保护** |
| G39 网关上下文访问器 | C9-009(P3) | 8 个冗余 synchronized | `GatewayContextImplTest#testThreadSafety`——伪并发（零断言，见 N2） | 全部 | **无保护** |
| G40 双重 trie/拦截器重建 | C9-010(P3) | 路由匹配量化 | `TestGatewayHandler`×6（含 method 匹配语义）+ `MappingProcessorTest`×7——修复（预计算/复用）的路由语义回归网在场 | 量化面（性能无对测） | **部分**（语义有钉） |
| G41 auth 双扫描 | C9-013(P3) | isPublicPath 线性扫描×2 | 无 | 全部 | **无保护** |
| 量化记录 | C9-007（随 G8）、C9-008（正面样例） | dispatchAll/resultLock 竞争频率 | C9-007 随 G8 生命周期钉；C9-008 为 C2-006 撤案正面样例，处置＝「维持现状+契约注释」，**无保护需求** | — | 记录（不计数） |

### 抽查反向验证记录（有钉/部分判定的证据可信度）

| # | 被验判定 | 验证内容 | 结论 |
|---|---|---|---|
| 1 | C8-002/C4-003「fallback 上限面有钉」 | 实读 `AiFailoverGatewayInterceptorTest#maxRetries_capsTotalFallbackAttempts`：maxRetries=1 + 3 个 fallback URL，断言 client.size()==1，RecordingHttpClient 真实计数 | 断言真实有效，非空跑 |
| 2 | C1-006「compute 安全路径并发有钉」 | 实读 `TestLocalMfaChallengeStore` 两并发用例：16 线程 + CountDownLatch 齐发，断言丢失更新为零/恰一胜者 | 真并发断言，非 smoke |
| 3 | C8-002「重试耗尽语义有钉」 | 实读 `InvokeProcessorRetryTest#invokeUrl_429exhausted_throwsWithStatusAndBody`：join future，断言 ERR_GATEWAY_UPSTREAM_429 + httpStatus + responseBody 三参数 | 真实断言 |
| 4 | C4-007「触发判定有钉」 | 实读 `TestAuthHttpServerFilterSemantics#testIsNeedRefreshHalfLife`：三态断言（半衰期内刷新/新鲜不刷/开关关不刷） | 真实断言，但仅及判定不及其执行链 |

## 四、缺保护发现登记（统一 P2）

> 归组原则与六维工单划分对齐，避免碎片化。每条含缺口描述、涉及缺陷 ID、建议回归测试形态（不写实现代码）。

### [nop-service-framework-hotpath-C10-001] C3-002 上下文传播维度零回归网（P1 主面无钉）

- **缺口**: 泵活性已被 TestGraphQLDataLoaderDispatchLifecycle 钉住，但「dispatch/递归泵在 future 完成线程执行、脱离请求 IContext」这一缺陷本体在全部 4 个用例下不可观测——fixture batch loader 对 context 无感。
- **涉及缺陷**: C3-002（连带 C3-015 同族挂接面）。
- **建议回归测试形态**: 在 DispatchLifecycle 式端到端夹具中增加一个 context 敏感型 @BizLoader（body 内断言 `Contexts.currentContext()` 非空且 tenantId 与请求一致，并把执行线程名记录到收集器），异步顶层 operation + 批量字段组合下断言批执行发生在挂接 context 的续行内；错误路径（顶层异步失败）同样断言 loader 不在裸线程执行。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-002] C3-003 背压门控/缓冲上界零回归网

- **缺口**: 缓冲层功能契约有钉，但「demand=0 时上游持续泵入导致无界缓冲」「onError 不 cancel 上游」两缺陷面无任何断言；生产 AI 流式默认走缓冲路径（retryCallback 注入）而无 AI 流式端到端用例。
- **涉及缺陷**: C3-003。
- **建议回归测试形态**: 手动 demand 订阅者 + 脚本化上游（发射计数器）场景：demand 恒 0 时上游 request 被 N 次调用后应停注（门控断言）或缓冲计数的可观测上界断言；错误路径断言上游 subscription.cancel() 被调用；补一条「retryCallback 注入 + 流式 route」的组合用例钉住缓冲层 engage 的生产默认形态。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-003] C3-007 callActionAsync 成功路径与上下文纪律零测试

- **缺口**: TestBizActionService 只测错误面；裸 executor 提交、IContext 不传播、ServiceContextImpl 池线程附着/残留（全单元唯一裸提交站点）零覆盖。
- **涉及缺陷**: C3-007（P1）；连带 C4-001 的 executor 解析面。
- **建议回归测试形态**: 端到端夹具注入受管测试 executor（可捕获提交的 Runnable 与提交时线程），调用 callActionAsync 后断言：worker 侧可见调用方 IContext（tenantId/userId）、执行完成后线程上无残留 context（getOrCreateContext 不新建）、@Async biz action 正常返回。可顺带断言 executor 名解析失败时的语义化错误（覆盖 C4-001 失败面）。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-004] C4-007 刷新执行链零回归网（触发判定有钉、执行链裸奔）

- **缺口**: 半衰期判定有钉；executeBlocking→runOnContext→同步 refreshToken 的阻塞执行链、失败不降频放大面在全仓（含单元外 nop-auth-sso）零测试。
- **涉及缺陷**: C4-007（P1）；连带 C3-011（flush fire-and-forget）、C4-004（载体）。
- **建议回归测试形态**: 过滤器级夹具：可控延迟/失败的 fake ILoginService.refreshToken（记录调用与线程），断言刷新在带超时的异步面上执行（如对 ILoginService 异步契约改造后，断言无 syncGet 调用形态——以「refreshToken 阻塞时长上界」或「载体线程在 IdP 挂起期间仍可处理其他请求」表达）；失败路径断言降频/退避状态存在；flush 失败路径断言错误被日志/回调消费。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-005] C8-001 WS 推送阻塞/线程域零回归网

- **缺口**: 推送功能面有钉；同步阻塞发送（basicRemote）、全局 timer 钉死、keepAlive 生命周期三面零覆盖，Mock session 结构上无法表达慢客户端。
- **涉及缺陷**: C8-001（P1）；连带 C4-002（keepAlive 挂 timer 与生命周期不对称）。
- **建议回归测试形态**: IWebSocketSession fake 增加「可控阻塞 sendMessage + 发送线程记录」能力；订阅推送用例断言 push 发生在非 timer/发布线程（或经异步发送面）；keepAlive 用例断言定时任务在会话关闭/端点停止后被取消（不依赖容器回调次序），并断言 keepAlive 任务抛 RuntimeException 不终止后续调度（C3-006(d) 同批）。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-006] C8-002 在途准入上界与放大组合零回归网

- **缺口**: fallback 腿上限与重试耗尽语义有钉；主腿重试次数、4+3 加法组合、在途并发上界（P1 承重面）零断言。
- **涉及缺陷**: C8-002（P1）；连带 C4-003（挂起时长上界）、C3-010（组合面）。
- **建议回归测试形态**: RecordingHttpClient 计数断言 429 场景主 URL 恰 1+maxRetries 次；组合级用例断言「InvokeProcessor 重试 × AiFailover fallback」总调用次数 == 4+min(|fallbacks|,maxRetries)；若落地准入闸（信号量/在途计数），补并发在途上界断言（超闸请求快速失败而非排队）。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-007] C9-012 marshaller 线格式契约零回归网

- **缺口**: LocalDateTimeFieldMarshaller 无任何测试；GenericObjSchema.toByteArray 的 computeSize/实写一致契约（越界即 OutOfSpaceException/脏字节）无对测；复核 probe 未回归化。
- **涉及缺陷**: C9-012（P1，移交功能正确性维度执行修复——回归测试仍应落在 graphql-grpc 测试域）。
- **建议回归测试形态**: marshaller 单测参数化 LocalDateTime 谱系（零秒零纳秒/整点/毫秒/微秒/纳秒/午夜）断言 computeSize==实写长度且 parse 往返还原；GenericObjSchema 级往返用例覆盖 DATETIME 字段声明长度一致性；可选：恒定长度 marshaller 的「长度谱系表钉」防止未来格式改动再破契约。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-008] 用户上下文设施并发回归缺失（六维修复工单 1 的验收半边缺位）

- **缺口**: 设施工单（C1-001 主 ID，含 C2-004/C1-002/C1-009 + R-CL-1 复活竞态 + clearDirty 契约）的全部交错面无并发测试；现有顺序钉只能验收功能不回归，不能验收竞态消除。
- **涉及缺陷**: C1-001、C2-004、C1-002、C1-009、R-CL-1。
- **建议回归测试形态**: 双线程交错夹具（latch 控制到行级）：并发 addRole/removeRole 与 saveUserContextAsync/clearDirty 交错 N 次后断言无丢更新（终态角色/attrs 与写序一致）；logout 与在途 flush 竞态用例断言已注销会话不被写回（removeUserContextAsync 后 putAsync 被守卫拒绝）；dirty CAS 认领并发 flush 恰一次持久化。可复用 TestAbstractLoginServiceSemantics 的 FakeCache/hook 骨架。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-009] 订阅注册表并发回归缺失（顺序钉对竞态无红色能力）

- **缺口**: G2 全组（C1-003/C1-005/C2-005/C3-014/C8-004）现有 30+ 用例全部单线程；TOCTOU 击穿、幽灵条目、孤儿列表在现有套件下复现即绿。
- **涉及缺陷**: C1-003、C1-005、C2-005、C3-014、C8-004。
- **建议回归测试形态**: 并发注册夹具（N 线程×同 id/超容量批量注册）：断言注册后 `subscriptionsById` 与 `subscriptionsByOperation` 双 map 严格一致（无孤儿）、条目数 ≤maxActiveSubscriptions、重复 id 恰一存活；并发 register+unregister 压力后断言零幽灵条目（验收工单已写明的「无幽灵条目 + 条目数上界」两条不变式）。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-010] Gateway 断连取消/幽灵重试零测试（修复暂缓项无看护）

- **缺口**: 「断连取消」在既有修复轮中明示暂缓，此后零测试看护；C3-010/C4-003 的取消传播、重试前取消态检查、非幂等方法重试守卫全部裸奔。
- **涉及缺陷**: C3-010、C4-003。
- **建议回归测试形态**: abort-after-start 夹具（复用 StreamingProcessorBufferingTest 的 silent publisher 形态）：普通转发路径客户端断连后断言不再发起新的上游 attempt（重试延时器不再触发）；InvokeProcessor 重试前断言 cancel token 已检查；非幂等 method 的重试守卫用例。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-011] C3-004 重试钳制/退避零看护

- **缺口**: 缓冲层单次重试接线有钉；maxAttempts 钳制、退避、同步失败热循环防护零断言（钳制尚不存在，落地即需回归网）。
- **涉及缺陷**: C3-004；对账面 AiGatewayFailoverInterceptor（其 retryBudget 现无测试）。
- **建议回归测试形态**: 回调恒返回新 HttpRequest 的场景断言尝试次数被钳制到 maxAttempts（RecordingHttpClient 计数）；断言退避期不持监视器（并发 abort 不被阻塞——以 abort 在退避窗口内及时生效表达）。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-012] C2-003 loadByProp 锁内 I/O 零测试

- **缺口**: BizObjHelper.loadByProp 的 computeIfAbsent 内 biz invoke（bin 锁内 DB 查询）在 5 模块测试域零引用、零覆盖。
- **涉及缺陷**: C2-003。
- **建议回归测试形态**: biz 对象级夹具：并发 loadByProp 同名对象 N 线程，断言底层构建函数恰执行一次（singleflight 语义）、无死锁（超时护栏）、miss 后回源不在映射函数内执行的线程断言（修复后形态）。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-013] TCC/RPC 入口上下文纪律零测试（C3-008/C3-012/C3-013/C3-017 合并治理的验收面）

- **缺口**: TccContextInvoker、ContextBinder、gRPC/WS/消息入口 context 建立、gateway 错误路径 locale 读取——上下文传播统一工单的四个组件零覆盖。
- **涉及缺陷**: C3-008、C3-012、C3-013、C3-017。
- **建议回归测试形态**: TCC 分支在池化线程发起后断言无 context 残留（任务前后 getOrCreateContext 恒等性）；入口级用例（gRPC/WS fake 入口）断言 tenant/locale/callExpireTime 从请求头落入线程 context；错误路径断言 locale 读自入参 context 而非残留。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-014] 单元外同链设施零回归面（台账内发现、测试域在单元外）

- **缺口**: C1-010（DaoUserContextCache，nop-auth-service）、C4-008（JWKPublicKeyLocator，nop-auth-sso）、C2-002（AbstractServerEventSubscription，nop-http-api）三条保留发现的缺陷代码与测试域均在本单元 5 模块之外，但台账归属本单元——现存全仓测试对其零覆盖。
- **涉及缺陷**: C1-010、C4-008、C2-002。
- **建议回归测试形态**: 移交接收轨补测或在本单元修复轮内约定跨模块验收：DaoUserContextCache 并发 flush RMW 用例（双线程同 session 刷新断言无一更新丢失，条件 UPDATE 后断言冲突降级路径）；JWK 定位器锁内拉取用例（慢 IdP 下第二请求不被锁钉住——singleflight future 形态）；ServerEventSubscription 锁外回调用例（阻塞回调不阻塞 cancel/request）。
- **严重度**: P2

### [nop-service-framework-hotpath-C10-015] WS 会话活性面零看护（C8-009 + C8-007 live 面）

- **缺口**: 发送失败断连、双向活性、idle timeout、per-connection 订阅容量——Mock session 永不失败导致整面不可测。
- **涉及缺陷**: C8-009、C8-007。
- **建议回归测试形态**: IWebSocketSession fake 增加「sendMessage 可注入 IOException」能力；推送失败用例断言会话被关闭/订阅被清理而非仅告警；容量用例断言 per-connection 第 1001 个订阅被拒且既有订阅不受影响。
- **严重度**: P2

## 五、测试套件自身缺陷注记（N 系）

| 编号 | 位置 | 问题 | 影响 |
|---|---|---|---|
| N1 | `GatewayFixTest#streamingProcessor_requestsOneByOne` | 测试名声称覆盖 StreamingProcessor 流控，实际仅自建 publisher/subscriber 演示 request(1) 模式，**未实例化任何被测类**；断言的是测试自己写的行为 | 对背压设施制造虚假覆盖感（C3-003 判定时须排除该用例） |
| N2 | `GatewayContextImplTest#testThreadSafety` | 双线程各 1000 次 set/get 后 **零断言**（注释「Should complete without exceptions」） | 伪并发 smoke；C9-009 等上下文线程安全面无实际保护 |
| N3 | `TestGraphQLActionAuthStrictModes:68`、`TestGraphQLEngineActionAuthWarn` | 直接读 `engine.actionAuthNoCheckerWarned`（C1-008 的非 volatile 字段）——测试套件是该字段全仓唯一读取者；字段被修复重构（volatile 化/移位）时测试先行破 | 钉住字段级契约而非行为契约；修复 C1-008 时须同步改造两处测试 |
| N4 | `TestAuthHttpServerFilterSemantics`/`OAuthFakeContext` 的 `FakeHttpContext.executeBlocking`（同步内联 task.call()） | executeBlocking 被所有 auth 夹具实现为同步内联，载体维度结构上不可观测 | 掩蔽 C4-004/C4-007/C9-011 的载体/线程跳跃缺陷类；补载体断言前须先改造 fake |
| N5 | `TestJsonRpcWebSocketHandler.MockWebSocketSession.sendMessage`（内存追加、永不阻塞/抛异常） | WS 会话 fake 无失败/慢速建模能力 | 掩蔽 C8-001/C8-009（阻塞发送、发送失败断连）整面 |
| N6 | `TestGraphQLDataLoaderDispatchLifecycle.batchName` loader（对 IContext 无感） | 批 loader 不校验执行线程上的请求上下文 | 掩蔽 C3-002 上下文传播面（仅活性可测） |
| N7 | G2 全组（TestGraphQLSubscriptionManager 21 例 + TestJsonRpcWebSocketHandler 注册族） | 纯单线程顺序钉守护一个缺陷本体为并发交错的设施；竞态复现时全部保持绿 | 存在「已覆盖」错觉；并发回归网缺位（C10-009） |

## 六、统计

### 6.1 保护力矩阵统计（63 条）

| 判定 | P1（7） | P2（41） | P3（13） | 合计 | 占比 |
|---|---|---|---|---|---|
| 有钉（缺陷核心机制回归必红） | 0 | 0 | 0 | **0** | 0% |
| 部分（至少一个缺陷相关面有钉） | 5（C3-002/003、C4-007、C8-001/002） | 16（C1-001/002/003/005/006/009、C2-001/004/005、C3-005/006、C4-003、C8-003/004/005、C9-005） | 4（C1-008、C3-001、C3-014、C9-010） | **25** | 40% |
| 无保护 | 2（C3-007、C9-012） | 25（C1-004/010、C2-003、C3-004/008/009/010/011/012/015/017、C4-001/002/004/008、C8-006/007/008/009、C9-001/002/003/004/006） | 9（C1-007、C3-013/016、C4-006、C8-010、C9-009/011/013） | **36** | 57% |
| 量化记录（不计数） | — | — | — | 2（C9-007 随 G8 部分钉；C9-008 正面样例，无保护需求） | — |

- **P1 保护力结论**：7 条 P1 全部处于「部分或无保护」——**0 条 P1 具备缺陷本体回归保护**；其中 C3-007、C9-012 完全裸奔。
- **并发回归网**：439 个 @Test 中仅 4 个真并发断言用例（全部位于 auth-core MFA/限次存储与 BeanMethodBatchFetcher），且无一落在本单元 P1/P2 缺陷的交错面上。
- **模块空洞**：nop-graphql-message 无测试目录；nop-biz-file-core 仅 5 例；nop-graphql-orm 仅 8 例且不触 OrmBatchLoader 上下文面。

### 6.2 本维度产出统计

- 缺保护发现：**16 条**（C10-001 ~ C10-016，统一 P2；其中 C10-001~007 对应 P1 七条的缺口面，C10-008~016 为 P2 缺口最重项按设施归组）。
- 测试套件自身缺陷注记：**7 条**（N1~N7）。
- 抽查反向验证：4 条（超出要求的 3 条），全部通过。

## 七、CL 注记（单列）

本维度对六维文件中的 CL 线索不做新裁定，仅登记测试保护力视角的对应关系（均已含在上文矩阵/缺口中）：

| CL | 归属（六维终裁） | C10 视角 |
|---|---|---|
| CL-001（keepAlive 容器缺失关闭滞留） | 确认（C1 集外） | 无任何测试（C10-005 keepAlive 生命周期面覆盖） |
| CL-002（subscriptionsByOperation 幽灵条目） | 并入 C1-003(b) | 随 C10-009 双 map 一致性不变式验收 |
| CL-003（令牌桶墙钟回填） | 确认（C7 形态） | 时钟回拨零覆盖（C8-005 无钉面，auth 域 MockClock 先例可移植） |
| CL-004（keepAlive 全局 timer 网络写） | 移交 C4/C7 → 并入 C4-002 | 无测试（C10-005） |
| CL-005（WS 注册 check-then-act 与清扫竞态） | 并入 C1-005 工单 | 随 C10-009 |
| CL-007（临时 zip 删除任务吞异常） | 移交 C6 | TestDownloadHelper 仅钉正面对照面（C4-005 无钉面） |
| CL-008（共享消息线程同步 push 队头阻塞） | dormant（随设施接线评估） | TestGraphQLSubscriptionManager 的 push 用例为单线程同步钉；接线时按 C10-005 补异步推送断言 |
| CL-012/013/014（转发环深度守卫/请求体物化/zip 配额） | 确认（集外） | TestForwardProcessorSemantics 不含深度守卫用例；集外不另立案，登记观测 |
| CL-015（= C9-012 移交案） | P1 移交功能线 | C10-007 回归网应随移交轨落地于 graphql-grpc 测试域 |
| CL-019（context 锁内 biz 批方法） | 移交 C2/C9 | TestBeanMethodBatchFetcher 钉注册语义；锁内 I/O 面随 C2-001/C9-007 处置，无独立测试需求 |
| CL-020（gateway 错误路径 locale 残留） | 随 C3-012/017 治理 | 随 C10-013 |
| CL-022/024/025（跨单元线索） | 供跨单元取用 | CL-025（断连原语）是 C10-010 回归测试的平台前置，测试形态依赖其落地 |

---

**边界声明**：本报告判定基点为 git HEAD 5994c18211 的未修复现状；「有钉/部分」中的设施级钉在对应六维工单修复落地后应复核其是否仍绿（尤其 G1/G2 的顺序语义钉与 N3 的字段级钉），避免修复与看护网脱节。

## C10 复核结论（独立复核子代理，2026-10-18）

复核方式：与 C10 初审代理相互独立，以怀疑姿态抽验矩阵行（逐条亲开被引测试核对断言）、逐条核对缺保护登记与 N 系注记、对六维文件 `## 最终保留项`/`## 维度复核结论` 做口径对账，并自行 grep 找未登记空洞。只读复核，仅追加本节。判定基点实测：审计仓库 HEAD 5994c18211、工作树 clean（`git status --porcelain -- nop-service-framework` 为空）。

### 一、矩阵抽验明细（13 行，超出最低 10 行要求）

| 行 | 判定 | 抽验内容（亲开实测） | 结果 |
|---|---|---|---|
| P1-1（C3-002） | 部分 | `TestGraphQLDataLoaderDispatchLifecycle`：恰 4 个 @Test 与所列同名；`execute()` 以 `.get(5, TimeUnit.SECONDS)` 超时判定（:170）；`BATCH_CALLS` 恰一批 + 键全覆盖断言（:207-208/:225-226/:243-244/:258-259）；类注释载明修复前 TimeoutException 与探针 `_tmp/fcr26-probe/`；`batchName` loader（:137-142）仅读 Tree 字段，对 IContext/执行线程零敏感 | **维持「部分」** |
| P1-2（C3-003） | 部分 | `StreamingProcessorBufferingTest` 恰 12 用例；`bufferedTailElementsDeliveredBeforeComplete`（:771-797）用 ManualDemandSubscriber（onSubscribe 不 request，:743-745）真实门控 demand，先断言不交付、request(2) 后全量交付再 complete；`inWindowFailureCallbackDeclinesForwardsError`（:397-422）断言断流报错 + `requests.size()==1`，不断言上游 cancel；`abortAfterResubscribeCancelsNewSubscription`（:624-671）同时断言新旧 sub 取消；ScriptedPublisher 首次 request(1) 单发全量，无「持续泵入 + demand=0」场景 | **维持「部分」** |
| P1-3（C3-007） | 无保护 | `TestBizActionService` 恰 2 用例，均在 actionModel==null 处 `assertThrows`（:30-34/:42-46）；`BizActionService.callActionAsync` 源码 :64-67 语义化异常先于 :70 `getWorkExecutorBean` 与 :90 裸 `executor.execute`，两用例从未触达提交路径；全测试域 grep `callActionAsync` 仅此一个引用文件 | **维持「无保护」** |
| P1-4（C4-007） | 部分 | `TestAuthHttpServerFilterSemantics` 恰 18 用例；`testIsNeedRefreshHalfLife`（:150-172）三态断言真实（半衰期内刷新/新鲜不刷/开关关永不刷）；测试域 `refreshToken` 命中均为 fake 契约方法（:478 返回 null，无任何用例驱动刷新支路）；nop-auth-sso 测试域恰 4 类（TestJwkKeyParsing/TestJwtHelper/TestOAuthLoginServiceImplContract/TestJwkErrorNormalization），grep `refreshToken` = 0 | **维持「部分」** |
| P1-5（C8-001） | 部分 | `TestJsonRpcWebSocketHandler` 恰 18 用例；`MockWebSocketSession.sendMessage` 为内存 list 追加（:644-647），永不阻塞/抛异常；graphql-core 测试域 grep `keepAlive|globalTimer|GlobalExecutors` = 0 | **维持「部分」** |
| P1-6（C8-002） | 部分 | `InvokeProcessorRetryTest` 恰 10 用例（parseRetryAfter×7 + invokeUrl×3）；`invokeUrl_429exhausted_throwsWithStatusAndBody`（:163-178）断言 ERR_GATEWAY_UPSTREAM_429 + httpStatus + responseBody 三参，全文件无任何 client/requests 计数断言；`AiFailoverGatewayInterceptorTest#maxRetries_capsTotalFallbackAttempts`（:199-217）maxRetries=1 + 3 fallback，断言 `client.size()==1`；该测试 5 用例全部非流式 | **维持「部分」** |
| P1-7（C9-012） | 无保护 | nop-graphql-grpc 测试域 grep `LocalDateTime|DateTime` = 0；`TestProtobuf` 恰 4 用例，仅 IntFieldMarshaller/StringFieldMarshaller（:125/:135），toByteArray 往返不触 DATETIME marshaller；`_tmp/c9r2-cl015/Cl015Probe.java` 在 `_tmp` 未回归化 | **维持「无保护」** |
| G4（C1-006） | 部分 | `TestLocalMfaChallengeStore#testIncrFailCountConcurrentNoLostUpdates`（:106，16 线程 + CountDownLatch，断言 == threads×perThread）与 `#testMarkVerifiedConcurrentExactlyOneWinner`（:208，16 线程）；两用例均无读侧 verifiedAt 可见性断言 | **维持「部分」** |
| G28（C8-003） | 部分 | `LoggingInterceptorTest` 恰 5 用例，`streaming_accumulatesDeltasAndLogsOnComplete`（:62）/`streaming_accumulatesToolCalls`（:113）聚合正确性有钉；全文件无 size/limit/truncat/bound 断言 | **维持「部分」** |
| G3（C1-004） | 无保护 | 5 模块测试域对 `getRequest()` 无任何 synchronized/monitor/lock 断言 | **维持「无保护」** |
| G10（C2-003） | 无保护 | 7 个测试根 grep `loadByProp|BizObjHelper` = 0 命中 | **维持「无保护」** |
| G37（C9-005） | 部分 | 所引 `GatewayFixTest#interceptedInvocation_proceedOnStreamElement_passesTransformedValue` 在 :142 存在 | 维持 |
| G29（C8-005） | 部分 | `AiRateLimitGatewayInterceptorTest` 恰 7 用例；`TestGatewayRejectResponse#rateLimitRejection_preserves429AndRetryAfter` 在 :103 存在 | 维持 |

**抽验结论：13 行 0 判定翻转**，抽查反向验证记录表 4 条全部复核通过。

### 二、修正行清单（4 项，均不动判定、只修口径/枚举/基点）

| # | 位置 | 问题 | 修正 |
|---|---|---|---|
| R-1 | §1.1 表 + §1.3「439 个 @Test」 | **盘点基点与声明不一致**：nop-graphql-core（39/34/158）与 nop-biz（30/28/131）按 master 工作副本（分支 master，HEAD c788657aa1，含 340b22e335 等未入审计分支的提交）统计，混入 4 个不存在于 5994c18211 的测试文件（TestReadOnlyCrudBizModel、NoServiceObjectBizModel(fixture)、TestNoServicePublish、TestGraphQLResponseHelper，合计 +11 例）；与「判定基点为 git HEAD 5994c18211」的边界声明矛盾 | 按审计分支实测改为：nop-graphql-core **38 文件/32 类/157 例**、nop-biz **27/26/121**，合计 **114/102/428**；§1.3「439」「其余 433」同步改 428/422。其余 6 个构建块计数与全部行级判定不受影响（per-class 计数 17 个逐一核对全部相符） |
| R-2 | §6.1 无保护 P2 枚举 | 列举 24 条但计数写 25——**漏列 C4-005**（G25 判定即「无保护（正面对照面有钉）」） | 枚举补 C4-005（C4-001/002/004/**005**/008），计数 25 才自洽 |
| R-3 | §6.1 无保护 P3 枚举 | 列举 8 条但计数写 9——**漏列 C2-002**（G9 判定即「无保护（单元外设施）」，C2 复核后 P3） | 枚举补 C2-002（C1-007、**C2-002**、C3-013/016、C4-006、C8-010、C9-009/011/013） |
| R-4 | §四 + §6.2 | **C10-016 缺失**：登记条目实际只有 C10-001~015 共 15 条（grep 无 C10-016 小节），§6.2 却写「16 条（C10-001 ~ C10-016）」 | 按实有 15 条更正，或由初审代理补写丢失的 C10-016 小节（推测候选归属：C3-009 订阅取消终态机或 C9 性能修复回归网——两者现均无登记，见本节四的 C10-017/018） |

另加口径注记（不改数）：§1.3「含多线程并发断言的用例仅 4 个」按「竞态不变式断言（无丢失更新/恰一胜者/singleflight）」口径成立且经实测（4 个用例全部亲验）；若按「跨线程行为断言」宽口径，`TestGraphQLEngineSubscription`（3 例，SubscriptionMockSchemaLoader :144/:167 后台线程投递 + 跨线程断言）与 `TestGraphQLSubscriptionPublisher`×8 亦涉异步——但无一落在 63 条缺陷的交错面上，结构性结论不变。

### 三、C10-001~016 缺保护登记裁定表

| 编号 | 涉及缺陷 | 矩阵自洽 | 建议形态可执行性（fixture/钩子实测） | 裁定 |
|---|---|---|---|---|
| C10-001 | C3-002、C3-015 | ✓（G19 无保护） | DispatchLifecycle 夹具在位（BATCH_CALLS 收集器形态可扩为线程名收集） | 成立 |
| C10-002 | C3-003 | ✓ | ScriptedPublisher 发射计数/ManualDemandSubscriber 均现成；RecordingHttpClient 先例在 AiFailoverGatewayInterceptorTest | 成立 |
| C10-003 | C3-007、C4-001 | ✓（G21） | 全测试域唯一 callActionAsync 引用点已实测；fakeBeanContainer Proxy 骨架可扩 | 成立 |
| C10-004 | C4-007、C3-011、C4-004 | ✓（G17/G24） | FakeHttpContext/OAuthFakeContext 在位；refreshToken fake 契约方法已有（:478），加时延/线程记录即可 | 成立 |
| C10-005 | C8-001、C4-002 | ✓（G22/G32） | MockWebSocketSession 需加阻塞注入（现 :644-647 纯内存追加，N5 实证） | 成立 |
| C10-006 | C8-002、C4-003、C3-010 | ✓（G16/G23） | RecordingHttpClient 计数能力实测在位（client.size()） | 成立 |
| C10-007 | C9-012 | ✓ | probe 已在 `_tmp/c9r2-cl015/`（实测存在），回归化路径清晰 | 成立 |
| C10-008 | C1-001、C2-004、C1-002、C1-009、R-CL-1 | ✓（含 C1 复核新增线索 R-CL-1，与 C1 最终保留项工单一致） | FakeCache/RecordingHook 骨架实测在位（TestAbstractLoginServiceSemantics :38-210） | 成立 |
| C10-009 | C1-003、C1-005、C2-005、C3-014、C8-004 | ✓（G2） | TestGraphQLSubscriptionManager 恰 21 例、ws 注册族用例（testDuplicateSubscriptionId:304/testReuseIdAfterComplete:331 等）实测全单线程（零 Thread/Executor/latch） | 成立 |
| C10-010 | C3-010、C4-003 | ✓（G16/G23） | 提交 299f7765f0 提交说明实测含「嵌套暂缓4(…断连取消…)」，暂缓无看护属实 | 成立 |
| C10-011 | C3-004 | ✓（G12） | gateway 测试域 grep `retryBudget` = 0，对账面无测试属实 | 成立 |
| C10-012 | C2-003 | ✓（G10） | loadByProp 0 引用已复现 | 成立 |
| C10-013 | C3-008、C3-012、C3-013、C3-017 | ✓（G15/G18） | TccContextInvoker/ContextBinder 测试域 0 引用实测；precision 注记：`newRpcContext` 在 6 个测试文件中作为夹具入口出现（TestGraphQLEngine:71 等），但零 tenant/locale 绑定语义断言——G18「0 断言」措辞准确，「0 引用」仅对 TCC/Binder 成立 | 成立（附措辞注记） |
| C10-014 | C1-010、C4-008、C2-002 | ✓（G7/G9/G27） | 5 模块测试域 grep 三设施名 = 0 实测；nop-auth-sso 测试恰 4 类且无 refreshToken | 成立 |
| C10-015 | C8-009、C8-007 | ✓（G31/G32） | 同 N5/MockWebSocketSession 能力缺口 | 成立 |
| C10-016 | — | **条目缺失** | — | **缺失**（见修正 R-4） |

### 四、复核补充发现（未登记空洞，续编 2 条）

**[nop-service-framework-hotpath-C10-017] 订阅取消终态机/gRPC onCancel 链零看护（C3-009 全部 + C3-006 (a)(c)(d) 分支 + C3-005 context 终态面）**

- **缺口**: C3-009（P2 无保护）是 36 条无保护中**唯一未被 C10-001~015 任何登记覆盖的 P2 缺陷 ID**——G13 判其无保护后无对应工单验收面。实测：nop-graphql-grpc 测试域 grep `onCancel|cancelToken|ServerCallHandler` = 0；TestGraphQLSubscriptionManager/WS 注册族全单线程，无「cancel 后 context 终态（fireBeforeComplete/unregister 收尾）」断言；C3-006 的同步异常穿透关连接/closed 分支不 cancel/keepAlive 静默终止三分支零覆盖。
- **涉及缺陷**: C3-009（连带 C3-006、C3-005 终态机 payload——C3 复核工单 4 明示终态机 payload 必须随迁）。
- **建议回归测试形态**: gRPC 端到端取消用例（fake client cancel 后断言 onCancel 触发 cancelTokenManager 登记/ctx.cancel，防止修复空转无信号）；WS 错误路径补「onError 后向客户端发错误帧 + 连接终态」断言；订阅 cancel 用例断言 executionContext 收尾恰好一次（可复用 TestGraphQLSubscriptionPublisher 的变换/完成断言骨架加终态探针）。
- **严重度**: P2

**[nop-service-framework-hotpath-C10-018] C9 请求热路径 P2 修复语义回归网缺位（C9-001 缓存化一致性 + C9-003 惰性初始化）**

- **缺口**: C9-001（G33 无保护）修复方向为「按返回类型名缓存合成选择树」——行为变更型缓存，G33 行已自注「修复为缓存后的一致性网也缺位」，且现无任何登记承接；C9-003（G35 无保护）修复方向 volatile 快检 + DCL/急切初始化，`TestJwtAuthTokenProvider` 恰 6 例、单线程直构（实测零并发原语），无法验收初始化语义（仅一次/singleflight）不回归。
- **涉及缺陷**: C9-001、C9-003（连带 C9-006 截断语义无对测，随工单顺带）。
- **建议回归测试形态**: 选择树缓存用例：同一返回类型连续两请求断言树结构一致，且第一响应携带的子选择/运行期装配字段修改不泄漏到第二请求（深拷贝隔离断言，防缓存化引入共享可变态——恰是 C1-007 同族面）；ensureKeys 用例：并发首调断言 keys 构建恰一次（singleflight）且失败后可重试；日志截断用例钉截断阈值行为。
- **严重度**: P2

### 五、N1~N7 注记复核（7/7 确认）

| 编号 | 复核实测 | 裁定 |
|---|---|---|
| N1 | `GatewayFixTest#streamingProcessor_requestsOneByOne`（:30-80）：自建 lambda publisher + 自建 subscriber 直连互订，断言的是测试自身写出的 request(1) 模式（6 次 request/5 次接收）；文件 import 无 StreamingProcessor，该方法未实例化任何被测类（同文件 Test 2 起才用 StreamingResponse） | 确认 |
| N2 | `GatewayContextImplTest#testThreadSafety`（:78-97）：2 线程 × 1000 次 set/get，方法体零断言，注释「Should complete without exceptions」 | 确认 |
| N3 | 全仓 grep `actionAuthNoCheckerWarned`：读取者仅 TestGraphQLEngineActionAuthWarn:19/:28 与 TestGraphQLActionAuthStrictModes:68；写者 GraphQLEngine:171/:392——测试套件为全仓唯一读取者 | 确认 |
| N4 | `TestAuthHttpServerFilterSemantics.FakeHttpContext.executeBlocking`（:637-643）同步内联 `task.call()`；全 5 模块测试域恰 3 个文件实现 executeBlocking（该文件 + TestGatewayHttpFilterSemantics + TestGatewayHttpFilterErrorResponse），载体维度结构上不可观测 | 确认 |
| N5 | `MockWebSocketSession.sendMessage`（:644-647）内存 list 追加，永不阻塞/抛异常 | 确认 |
| N6 | `batchName` loader（TestGraphQLDataLoaderDispatchLifecycle:137-142）仅读 Tree.id，无 context/线程校验 | 确认 |
| N7 | TestGraphQLSubscriptionManager（21 例）与 TestJsonRpcWebSocketHandler（18 例）grep `new Thread|Executors.|CountDownLatch` = 0，纯单线程顺序钉 | 确认 |

### 六、口径核对（与六维文件对账）

- **63 条总数与分布：一致，维持**。逐维对账：C1 10（P2×8/P3×2，C1-001 降 P2 主 ID）✓；C2 5（P2×4/P3×1，C2-006 撤案不入 63）✓；C3 17（P1×3/P2×10/P3×4）✓；C4 8（P1×1/P2×6/P3×1，C4-003 降 P2）✓；C8 10（P1×2/P2×7/P3×1）✓；C9 13（P1×1 移交/P2×6/P3×4 + 量化×2）✓。合计 63 = P1×7（含 C9-012）+ P2×41 + P3×13 + 量化×2 ✓。
- **6.1 判定分布：计数自洽，两处枚举漏项**（R-2/R-3）：有钉 0 + 部分 25（P1×5/P2×16/P3×4）+ 无保护 36（P1×2/P2×25/P3×9）+ 量化 2 = 63 ✓；修正枚举后分布不变。
- **63 条 ID 全覆盖核验**：C1×10/C2×5/C3×17/C4×8/C8×10/C9×13 逐一对照归组表 + P1 详表，无遗漏、无重复计分 ✓。
- **CL 注记表完整性注记**：本单元六维文件合计 24 条 CL 线索，§七仅列 16 条；未列的 CL-006/009/010/011/016/017/018/023 均为移交/驳回/单元外线索，不构成本单元矩阵行，故不构成登记缺口，但「均已含在上文矩阵/缺口中」表述应收窄为「本单元矩阵内相关 CL」。其中与本单元测试面相关的两条实际归属：CL-016（runOnContext 队列 syncGet）的看护面由 C10-004 承接；CL-009（CacheActionDecorator miss 并发回源无 singleflight）现有 `TestCacheActionDecorator`×6 为单线程顺序语义钉（testCacheHitAvoidsUnderlyingAction 等，实测无并发原语），并发回源面无钉——因 CL-009 已移交 C2/C9 轨，不另立案，登记观测。

### 七、最终统计（复核后）

- **保护力矩阵：维持**——63 条、有钉 0 / 部分 25 / 无保护 36 / 量化记录 2；13 行抽验 0 判定翻转；P1 结论维持（7 条全部部分或无保护，0 条具备缺陷本体回归保护；C3-007、C9-012 完全裸奔）。
- **缺保护发现：由 15 条实有（登记号至 C10-015，原报告误计 16）修正为 17 条** = C10-001~015 全部成立 + 复核补充 C10-017/018；C10-016 为编号空洞（R-4，建议初审补写或将 C10-017/018 视为补位）。
- **N 系注记：7 条全部维持**（7/7 实测确认）。
- **盘点口径：修正**——1.1 表按声明基点 5994c18211 应为 114 文件/102 测试类/428 @Test（原 118/106/439 混入 master 分支工作副本 4 文件 11 例）；「4 真并发断言用例」与模块空洞结论（nop-graphql-message 无测试目录、nop-biz-file-core 5 例）实测维持。

**复核边界声明**：本节全部判定基于审计仓库 HEAD 5994c18211 工作树亲测（与初审声明基点一致）；发现的两处枚举漏项与盘点基点混用均为口径/统计缺陷，不改变任何行级保护力判定与修复建议。
