# nop-service-framework auth/流缓冲/dispatchAll 束 b（R4-17 G36 后半，12 成员）

> Status: draft
> Last Reviewed: 2026-11-10
> Source: plan 2551（`ai-dev/plans/2551-cra-phase8.57-r4-17-service-auth-buffer-b.md`）Phase 1；批次定义 = batch-proposal.md §二 :132 R4-17 行；成员发现 = `ai-dev/audits/2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency/`
> PA：无 Protected Area（触碰面 nop-biz/nop-biz-auth-core/nop-graphql-core/nop-gateway/nop-auth-sso/nop-rpc-core 均非 PA；CL-025 已在 2550 落地不再触碰 nop-http-api 新 API 面——C4-003 只换延迟载体与加 timeout，不改 SPI）
> 前置：R4-16（plan 2550）done——CL-025 断连原语与三站点检查点在位，C4-003 的取消传播 leg 已闭合。
> 成员取证（fresh session @ 2026-11-10）：原样 7（C4-001/005/008、C8-003/007/008、C9-003）+ 部分覆盖 4（C4-003/004、C8-009、C9-005）+ 实质已修 1（C4-002）。

## 一、设计结论（十条裁定）

1. **裁定 A（缺省执行器存在性 fail-fast，R1-Major-3 修订）**：C4-001。`BizActionService.callActionAsync` 按名 `getBean(executor)`，默认名 `defaultWorkExecutor` 全仓无 bean 定义（`BizConstants:40` 为唯一出现点）→ 默认路径确定性失败（catch(Throwable) 吞为 null 快照）。唯一形态：①`nop-biz` 以**专用薄包装类**（`DefaultWorkExecutor`，非 factory-method）实现 `IThreadPoolExecutor` **与 `IPartitionedExecutor`**（bean 名 `defaultWorkExecutor`，beans.xml 显式定义；不新建线程池——execute 委托 `GlobalExecutors.globalWorker()`；`executeForPartition(partition, task)` 诚实形态 = 抛 `UnsupportedOperationException("no partition support in default executor")` 快速失败，并在②的启动校验中对 bizSequential+缺省执行器组合 fail-fast——序列化 action 与无分区缺省执行器的组合在启动期即被拒（列全 action 名，live biz-sequential 声明为 0，运行期不可达）；②启动期校验（类型感知）：`BizActionService.init()` 遍历全部 actionModel 的 executor 名做 `getBean` 预检——未知名 fail-fast（类型化 NopException，列全无效名）；`bizSequential` 动作解析到的 executor 须 `instanceof IPartitionedExecutor`，否则 fail-fast 列全违例动作名。拒绝方案：仅文档声明（守卫缺失才是缺陷）。
2. **裁定 B（C4-002 Proof 登记）**：实质已修（R1-03 plan 2498 C8-001 keepalive 共享链+send pump+注册表自清扫闭合全部三项缺陷面）。本批仅 Proof：登记验收 = 既有钉（`TestJsonRpcWsSendOffload_C8_001`/`TestSubscriptionPushOffload_C8_001`）复绿 + 残余面（worker 线程同步写/失败仅 WARN）移交 C8-009 本批承载。
3. **裁定 C（重试延迟载体 + fetch 超时）**：C4-003 残余。①`InvokeProcessor.delayedFuture` 由 JDK `CompletableFuture.delayedExecutor` 改平台 `GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker()).schedule(...)`（**R1-Major-4：必须 executeOn(globalWorker)——完成回调在 worker 池执行，重试发起（buildHttpRequest+fetch 提交）不得落 timer 线程**，否则把「重试发起在共享线程」的钉面原样搬到 globalTimer=复刻 C4-002 已修形态；仓内先例 `JsonRpcWebSocketHandler.registerKeepAlive`；结构钉同步钉 executeOn hop）（延迟触发入平台 timer 治理面，O(1) 计时器槽；C4 复核勘误 8：JDK25 已改 common pool 多线程，原 P1 支柱证伪——本改动为治理一致性而非容量修复，登记注记）；②上游 fetch 加显式超时：`HttpRequest.timeout(...)`（键 `nop.gateway.upstream-request-timeout` 缺省 30000ms，≤0=用客户端缺省）——429 挂起窗受双重上界（Retry-After 30s 钳制 × 超时）。
4. **裁定 D（下游链出 executeBlocking）**：C4-004 残余。`AuthHttpServerFilter:155` 的 `executeBlocking` callable 现包裹含 `next.get()` 的整链——改为：executeBlocking 仅承载用户上下文加载（blocking 段：token 校验/缓存读/刷新发起），下游 `next.get()` 链移出 blocking 载体（经 runOnContext 再入上下文队列——与 2508 已落的续段介质契约同形）。**边界**：本批不新建 `nop-auth-worker` 独立池（隔离池属设施增强，watch-only 残余登记）。
5. **裁定 E（下载临时文件清理可观测+对账）**：C4-005。①`DownloadHelper` 清理任务：`delete()` 返回 false 或抛异常时 WARN（结构化键 `nop.warn.download.temp-clean-fail`）+ 记录路径（不再静默吞）；②平台级对账：新增 `DownloadTempReconciler`（`nop-biz`，bean 装配）——周期扫描（`globalTimer().scheduleWithFixedDelay`，键 `nop.download.temp-reconcile-interval` 缺省 1h，≤0=关）`temp:/download` 整树，删除 mtime 超过阈值（键 `nop.download.temp-max-age` 缺省 24h）的遗留文件；**约束（R1-Minor-9 + R2-n-2 锚定修订）**：`DownloadHelper` 定义常量 `MAX_WAIT_MINUTES`（当前调用面最大等待窗，缺省上界锚点）；`temp-max-age` 必须 > `MAX_WAIT_MINUTES`（对账器构建期校验违例 fail-fast），扫描范围显式覆盖 `download/{day}/{uuid}` 全子树（载体②断言子树遍历）；对账删除同样 WARN 记录。拒绝方案：仅 try/catch（无兜底对账 = 重启遗留面未闭合）。
6. **裁定 F（JWKS 定位器异步单飞+grace）**：C4-008。`JWKPublicKeyLocator` 改：①synchronized 块内的同步 IdP 拉取改**单飞共享**——在飞 `CompletableFuture` 缓存（DCL：锁内只查/置在飞 future，拉取出锁执行），并发 kid 未命中共享同一次拉取；②`fetchAsync` + 显式超时（键 `nop.auth.jwks.fetch-timeout` 缺省 5000ms）；③失败 grace：拉取失败保留既有 currentKeys（若有）并 WARN，**仅在无旧钥时**向调用方失败（拆分 TTL 过期 vs kid 缺失两判据——kid 缺失且无旧钥才 fail）；④失败不占 60s 速率窗（失败不入成功 TTL 计时）。拒绝方案：保持 synchronized（跨 IdP RTT 占 monitor）。
7. **裁定 G（流式聚合字节上界）**：C8-003。`OpenAiDeltaAccumulator` 的 `contentBuilder`/`argumentsBuilder` 加字节上界（键 `nop.gateway.streaming.accumulate-max-bytes` 缺省 2MB）：追加前检查总长，超限截断 + 一次性 WARN（`nop.gateway.streaming-accumulate-truncated`）+ 截断标记注入结果（`[TRUNCATED]` 尾标）；**上界覆盖面（R1-Minor-7）**：contentBuilder 与**每个 `ToolCallAccumulator.argumentsBuilder`**（per-tool-call，N 个）分别受同一键上界约束——只界顶层会漏 tool-call 参数缓冲各自的线性增长；`LoggingInterceptor.onStreamError` 同步清理 accumulator 引用（错误终态不持堆）。拒绝方案：不加界（线性堆推高维持）。
8. **裁定 H（WS 订阅准入面，R1-Major-5 + Minor-11 修订）**：C8-007。①`maxActiveOperations` 配置化：**handler 直读静态配置键**（nop-graphql-core 既有 `GraphQLConfigs` 定义 `CFG_GRAPHQL_WS_MAX_ACTIVE_OPERATIONS`（R2-n-1：引用既有配置类，不新建），键 `nop.graphql.ws.max-active-operations` 缺省 1000=现行值；setter 保留供测试覆盖）——**不走端点 @InjectValue**（端点是宿主 CDI bean，Nop 注解不生效，R1-Major-5）；②跨连接全局闸：**单一机制** `HighWatermarkSemaphore`（键 `nop.graphql.ws.global-max-subscriptions` 缺省 10000；≤0=null=不设上界档旁路）——validSubscription 前全局 tryAcquire、订阅终止（cancelOperation/onError/onComplete 清理点）release；不另设 AtomicInteger 第二套计数。
9. **裁定 I（流控 runner 分桶，R1-Blocker-1 改写）**：C8-008。**限流原语零换型**——维持 `IRateLimiter`（QPS 速率）语义，仅按 `entry.getResource()`（含 resourceType 前缀 `resourceType:resource`）分桶：`ConcurrentHashMap<String, IRateLimiter>` buckets（仓内先例 `AiRateLimitGatewayInterceptor.buckets` 形态），每桶用 runner 既配置的 `maxPermitsPerSecond` 懒创建（computeIfAbsent，配置 setter 置新值时清空 buckets 重建）。**不做并发信号量化**（`FlowControlEntry` 无 maxConcurrent 字段，加字段须动 `nop-rpc-api` = plan-first PA——拒绝；审计原文即建议 resource 分桶的 IRateLimiter）。双消耗站点（`GraphQLFlowControlInvoker` resource=/graphql 与 `GraphQLExecutor.withFlowControl`）不再共享同桶，1+K 稀释消除。
10. **裁定 J（WS 会话活性面收口）**：C8-009 残余 + C10-015。①发送失败断连：`WebSocketSendPump` 投递失败（连续 3 次 IOException 级失败，计数器在 handler 侧）→ 复用 onClose 清理路径断连（不再仅 WARN）；②pong 记账：`case METHOD_PONG` 补 `lastPongAt` 更新；keepAlive 扫描对超时无 pong 会话（键 `nop.graphql.ws.idle-timeout` 缺省 120s，≤0=关）主动 close；③适配器 idle：`JakartaWebSocketSession`/`SpringWebSocketSession`（nop-quarkus-web / nop-spring-web-starter——**两模块列入 Targets/E3**，R1-Major-6）加 `setMaxIdleTimeout`（同键，适配器支持时设置；Spring 侧不支持的跳过档须**显式 WARN 注记**，非静默）；④`onThrowable` 补 activeOperations 全清（cancel 上游）。**测试载体随本裁定承载 C10-015**（MockWebSocketSession 加可注入 IOException/容量断言能力）。

## 二、附加裁定（C9 热路径 + 流式快径）

11. **裁定 K（JwtAuthTokenProvider 锁外快检）**：C9-003。①`ensureKeys()` synchronized 域内 isEmpty 快检外移（DCL：锁外读 volatile keys 引用判空，锁内二次确认）；②keys 容器改 volatile 不可变 map（整表替换发布，仓内先例 `AiRateLimitGatewayInterceptor.buckets`）；**`legacyKey` 一并并入不可变快照（或 volatile）**（R1-Minor-8：parseToken 锁外读 legacyKey，留在旧形态=未安全发布可变字段，热路径快检收益被部分抵消）；③本裁定同 diff 承载 **C10-018 之 C9-003 半边**：并发首调 singleflight 断言 + 失败可重试断言（载体 `TestJwtEnsureKeysHotPath`）。
12. **裁定 L（SSE 透传快径）**：C9-005 残余。`StreamingProcessor` 元素链加透传快速路径：拦截器链为空（无 proceedOnStreamElement 挂载）且元素已是目标字符串形态时，免 `JSON.parse`/再 serialize 往返（直接透传）；序列化结果按元素缓存一次（`GatewayHttpFilter.serializeStreamElement` 与 accumulator 输出共享已序列化形态）。**边界**：带拦截器的路由维持现路径（parse→拦截→serialize 语义零变化）——快径仅限「无拦截器」档，宽化属后续增强，watch-only 登记。

## 三、C10 验收前置落位

| C10 | 状态 | 本批动作 |
|---|---|---|
| C10-004/005/006/011/012/017 | 已兑现（2508/2498/2550/2550/2550/2550） | 无动作（回归钉复绿） |
| C10-015 | 开放 | 裁定 J 同 diff 承载（C8-009/C8-007 活性面+准入面回归网：可注入 IOException Mock、全局闸容量断言、pong 记账断言） |
| C10-018（C9-003 半边） | 半开放 | 裁定 K 同 diff 承载（并发首调 singleflight + 失败可重试断言） |

## 四、回归面清单

| 改动 | 消费方 | 回归 |
|---|---|---|
| 裁定 A（biz executor） | nop-biz 全量 + 现有 async action 测试 | `-f nop-service-framework/pom.xml clean test`（全反应堆） |
| 裁定 C/D/F（gateway/auth） | nop-gateway、nop-biz-auth-core、nop-auth-sso | 各模块全量；2508 SSO 刷新链钉不回退 |
| 裁定 E（download） | nop-biz | 模块全量 |
| 裁定 G/H/J/L（流式/WS） | nop-gateway、nop-graphql-core、quarkus-web 适配器 | 各模块全量 + 2509 缓冲钉不回退 |
| 裁定 I（flowcontrol） | nop-rpc-core + graphql 消耗点 | 模块全量 |
| 裁定 K（jwt） | nop-biz-auth-core | 模块全量（MFA/auth 既有钉复绿） |

**E1**：`./mvnw -o clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` BUILD SUCCESS。
**E3**：`./mvnw -f nop-service-framework/pom.xml clean test`（clean）+ `./mvnw test -pl nop-auth/nop-auth-sso -am` + `./mvnw test -pl nop-network/nop-rpc/nop-rpc-core -am` + `./mvnw test -pl nop-quarkus/nop-quarkus-web -am` + `./mvnw test -pl nop-spring/nop-spring-web-starter -am`。
