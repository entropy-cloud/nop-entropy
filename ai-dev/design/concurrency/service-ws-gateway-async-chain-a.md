# nop-service-framework WS/网关/异步链束 a（R4-16 G36 前半，12 成员）

> Status: active
> Last Reviewed: 2026-11-09
> Source: plan 2550（`ai-dev/plans/2550-cra-phase8.56-r4-16-service-ws-gateway-a.md`）Phase 1；批次定义 = batch-proposal.md §二 :131 R4-16 行；成员发现 = `ai-dev/audits/2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency/`
> PA：nop-service-framework 批次表判「无」；**例外**：C3-010 的前置原语 CL-025（`IHttpServerContext` 断连观测）落在 `nop-http-api` = 跨模块公共 API = **plan-first**（本设计 + 回归面为其授权载体）。其余站点非 PA。
> 前置：8.5（R1-04）/8.14（R2-05 SSO 异步化+准入池）/8.15（R2-06 BoundedWindowBuffer）均 done。
>
> **基线勘误 E-2550-1（fresh-session 取证 @ 2026-11-09）**：
> - C3-004 **部分覆盖**（2509 已重构 BoundedWindowBuffer：溢出不可重试终态/attempt-identity/出锁投递）；残余 = **maxAttempts 钳制 + 重试退避**（grep maxAttempts 零命中）。
> - C3-005 **部分覆盖**（cancel→上游 cancel 已补）；残余 = onError/onComplete 补 context complete。
> - C3-006 **部分覆盖**（keepAlive RuntimeException 断链已修）；残余 = onError 终止信号/session-closed 取消上游/同步异常穿透三分支。
> - C3-010 **取消传播本体未动**，且以 CL-025 断连观测原语为前置（live InvokeProcessor 零取消检查；Retry-After 钳制/在途许可池已由 2508 落地）。
> - 其余 8 条（C1-002/004/005/006、C2-001、C2-003、C3-009、C3-011）原样在位。

## 一、设计结论（六条裁定）

1. **裁定 A（共享可变状态最小原子化）**：C1-002、C1-004、C1-005、C1-006。
   - C1-002（R1-Minor-5 修订）：`UserContextImpl.roles` 由裸 HashSet 改 **`ConcurrentHashMap.newKeySet()`**（写 add/remove 与读 `getRoles()`/`serializeToJson` 一致视图，无锁读，读侧对 keySet 快照迭代）；**`setRoles(Set)` 入参必须拷贝进并发 Set**（否则 `addRole` 的 `roles == null` 惰性初始化不会转换非并发集合，修复不完整）。
   - C1-004：`GraphQLExecutionContext.setRequest` 补 `synchronized`（与 getRequest 同监视器，消除监视器失配；字段透传自 serviceContext，锁粒度保持）。**契约残注（R1-Minor-8）**：本类外其他 `serviceContext.setRequest` 写入方不在此同步域内，本裁定只收敛 GraphQLExecutionContext 自身读写对。
   - C1-005：`JsonRpcWebSocketHandler.sendStreamingMessage` 的 `activeOperations.put` 改 **`putIfAbsent`**（重复 id 并发不覆盖首订阅；冲突时对已存在订阅直接复用/拒绝——**取复用并返回既有**，与 C1-003 终态机协同）。
   - C1-006：`MfaChallenge.verifiedAt` → `volatile`（单行）。
2. **裁定 B（锁内 I/O 移出）**：C2-001（←并 C9-007）、C2-003。
   - C2-001：`GraphQLExecutionContext.dispatchAll` 改「`synchronized` 域内仅做快照+清空（dataLoaders map 取出并清空），`loader.dispatch(...)` 移到锁外执行」——消除上下文监视器内 DB I/O；**契约注释**（快照后新注册的 loader 归下一轮）纳入验收。回归 P1-2.6-004。
   - C2-003：`BizObjHelper.loadByProp` 的 computeIfAbsent 映射内 biz invoke（校验链+DB）改 **双检+锁外 invoke+`putIfAbsent` 回写**（竞争失败方丢弃自建结果——查询无资源需回收；与 2549 C2-005 同型不同站点）。
3. **裁定 C（异步链终态对称）**：C3-004（残余）、C3-005（残余）、C3-009。
   - C3-004：`BufferedStreamingPublisher`（BoundedWindowBuffer 宿主）重订阅链增 **maxAttempts 上界**（新键 `nop.graphql.stream.max-resubscribe-attempts` 缺省 5，`<=0`=无限保持）+ 指数退避（base 100ms × 2^n 封顶 5s，确定性注入缝=可注入 scheduler/退避计算函数）。2509 的溢出终态/attempt-identity 零回改。
   - C3-005：`GraphQLSubscriptionPublisher.onError/onComplete` 与 `JsonRpcWebSocketHandler` 流终止信号处**补执行上下文 `complete()`**（取消/错误/正常三条终止路径对称；核心=取消订阅泄漏上下文清理回调永不触发）。
   - C3-009（R1-Blocker-2 机制改写）：`executeRpcAsync` 返回 `CompletionStage<ApiResponse<?>>` 组合链，**其上不存在可取消点**——`cancel(true)` 语义不可交付，原「取消 future」机制证伪。改唯一形态为：`GraphQLServerCallHandler.onCancel` 接线——构造器/处理链持有 `setOnCancelHandler` 注册，onCancel 触发 ①响应 abandoned 记录（WARN 日志 + 计数，不写回客户端）②执行上下文 `complete()`（清理订阅/上下文）③对返回 stage 的 best-effort 取消（`instanceof Future` 时 `cancel(true)`）。**幽灵执行的底层中断登记为 watch-only residual**（需引擎层取消句柄贯穿，非本批 P2 范围）。钉形态 = nop-graphql-grpc 测试树结构钉（注册链存在）+ nop-graphql-core 行为钉（onCancel → abandoned + complete）。
4. **裁定 D（WS 错误语义收口）**：C3-006（残余三分支）。
   - onError 路径向客户端发**终止信号**（jsonrpc error 帧）后再关流；session-closed 时**取消上游订阅**（不再只静默）；同步异常不穿透 `catch(IOException)`——显式分类（IOException 关连接 / RuntimeException 走已落地的 keepAlive 绝缘同型）。
5. **裁定 E（网关取消传播 + CL-025 原语）**：C3-010（含 C4-003 幽灵重试子面）。
   - **CL-025 原语**（nop-http-api，plan-first）：`IHttpServerContext` 增 `default boolean isClientAborted() { return false; }` + `default void onClientAbort(Runnable task) {}`（默认实现保持零破坏；**SPI 变更，回归面 = 全部现存实现，R1-Blocker-1 勘正**：`VertxHttpServerContext`（`nop-quarkus/nop-quarkus-web`）+ `ServletHttpServerContext`（`nop-spring/nop-spring-web-starter`）+ `nop-auth-sso` 测试桩——设计原判「Netty/Undertow/JdkHttp 三实现」经 fresh 取证不存在，勘误登记；本批接 Vertx/Servlet 两实现的 aborted 检测，两模块位于 nop-service-framework 聚合器之外，回归命令单列）。
   - `InvokeProcessor`：转发前/重试延时器触发前检查 `isClientAborted()` → 取消链（不再重试/重发非幂等 POST）；`GatewayHttpFilter` 断连注册 onClientAbort → 关闭在途下游调用。AiFailover 链同检查点（退避已由 2508 equal jitter 落地）。
6. **裁定 F（Auth 会话刷新失败可观测）**：C3-011。
   - `AuthHttpServerFilter` 的 `flushUserContextAsync` 调用点（:397 lambda 内内层 CompletionStage，R2-N-4 勘正命名）补**失败消费**（`whenComplete` 异常分支 WARN `nop.warn.auth.session-flush-fail` + **失败时不清 dirty 标记**[下轮重试]）；`AbstractLoginService.flushUserContextAsync` 的 `thenRun(clearDirty)` 改 `whenComplete`（成功才 clearDirty）。2508 SSO 异步化零回改。

## 二、逐成员裁定表（方法名锚）

| Finding | 文件 / 方法 | 唯一形态 | 拒绝方案 |
|---|---|---|---|
| C1-002 | `UserContextImpl.roles`；`addRole`/`removeRole`/`isUserInRole`/`getRoles`/`setRoles`/`serializeToJson` | roles → `ConcurrentHashMap.newKeySet()`；`setRoles` 拷贝进并发 Set；读侧快照迭代 | 全方法 synchronized（读多写少场景锁放大）；CopyOnWriteArrayList（角色集语义） |
| C1-004 | `GraphQLExecutionContext.getRequest`/`setRequest` | setRequest 补 synchronized（与 getRequest 同监视器） | 只去 getRequest 的 synchronized（失配换向） |
| C1-005 | `JsonRpcWebSocketHandler.sendStreamingMessage`；`validSubscription` | `activeOperations.putIfAbsent` + 已存在复用 | 直接 put 覆盖（现状） |
| C1-006 | `MfaChallenge.verifiedAt` | volatile | AtomicLong（序列化兼容面） |
| C2-001 | `GraphQLExecutionContext.dispatchAll` | synchronized 域内快照+清空；dispatch 锁外 | 全程 synchronized（现状）；去 synchronized 无快照（竞态） |
| C2-003 | `BizObjHelper.loadByProp` | 双检+锁外 biz invoke+putIfAbsent（失败方丢弃自建结果） | computeIfAbsent 内 invoke（现状）；全局锁 |
| C3-004 | `BufferedStreamingPublisher` 重订阅链 | maxAttempts 新键缺省 5 + 指数退避（注入缝）；2509 语义零回改 | 无上界重试（现状）；固定 sleep |
| C3-005 | `GraphQLSubscriptionPublisher.onError/onComplete`；`JsonRpcWebSocketHandler` 流终止 | 三条终止路径对称补 context complete | 只取消不 complete（泄漏维持） |
| C3-006 | `JsonRpcWebSocketHandler` WS 错误三分支 | onError 发终止信号；session-closed 取消上游；同步异常分类（不穿透 IOException catch） | 静默关闭（现状） |
| C3-009 | `GraphQLServerCallHandler.onCancel`/`setOnCancelHandler` | onCancel → abandoned 记录 + context complete + best-effort Future 取消（底层中断=watch-only residual，R1-Blocker-2 机制改写） | 空实现（现状）；`CompletionStage.cancel` 直呼（类型上不存在） |
| C3-010 | CL-025 `IHttpServerContext`（nop-http-api，PA=plan-first）；`InvokeProcessor`；`GatewayHttpFilter`；`AiFailoverGatewayInterceptor` | default `isClientAborted()`/`onClientAbort(task)` + 三站点检查点接线；**SPI 回归面 = 全部 http server 实现** | 无原语直接轮询 socket（平台外语义） |
| C3-011 | `AuthHttpServerFilter`；`AbstractLoginService.flushUserContextAsync` | 失败消费：WARN + 失败不清 dirty（成功才 clearDirty） | fire-and-forget（现状） |

## 三、回归面清单

| 改动 | 消费方 | 回归 |
|---|---|---|
| 裁定 A/B（graphql/auth/biz） | nop-graphql-core、nop-biz、nop-biz-auth-core 各自测试树 | `./mvnw -f nop-service-framework/pom.xml clean test`（R2-N-3 勘正）+ 触点模块逐条 |
| C2-001 | P1-2.6-004 回归 | 该钉必绿（不回退） |
| 裁定 C/D（gateway/ws） | nop-graphql-core ws、nop-gateway | 两模块全量 |
| C3-009 | nop-graphql-grpc + nop-graphql-core | nop-graphql-grpc 测试树结构钉（R1-Minor-7 勘正：测试树存在，钉可建）+ nop-graphql-core 行为钉 |
| 裁定 E（CL-025 + 网关） | nop-http-api SPI 全部实现 + nop-gateway | nop-http-api 编译 + `nop-quarkus/nop-quarkus-web` + `nop-spring/nop-spring-web-starter` 编译/测试（聚合器外单列）+ nop-auth-sso 测试桩 + nop-gateway 全量 |
| 裁定 F（auth filter） | nop-biz-auth-core（R2-N-4 勘正：nop-auth-core 不存在） | 模块测试 |

**E1**：`./mvnw -o clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` BUILD SUCCESS。
**E3**：`./mvnw -f nop-service-framework/pom.xml clean test`（R1-Major-3 勘正：`-pl nop-service-framework -am` 只选上游依赖不含子模块，改 -f 聚合器反应堆）+ `./mvnw test -pl nop-quarkus/nop-quarkus-web -am` + `./mvnw test -pl nop-spring/nop-spring-web-starter -am` + 上表逐条。
