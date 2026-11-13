# WI 4.4 双宿主/反向钉束（R4-28，plan 2562）设计裁定

> Status: active
> Last Reviewed: 2026-11-11
> Source: `ai-dev/audits/2026-10/2026-10-21-1223-deep-audit-nop-host-concurrency/`（8 原样 + C1-003/C4-002 部分覆盖 fresh-session 取证 @ 2026-11-11；2498/2551/2524 锚点漂移已核）

## 〇、范围重划（R1 前置——2498/2551 结构性消除原始形态）

- **C1-003 范围重划**：2498 WebSocketSendPump 已消除「订阅流 vs keepalive」交叠主面——本批残余面 = **控制帧直发并入 sendPump**（handlePing :308/:310、handleSubscription-cancel :358、handleTokenRefresh :403、sendError :602/:606）。
- **C4-002 范围重划**：2498 A5 收敛已消灭 per-connection timer 原始缺陷形态——本批残余面 = **(a) no-op handle 语义名不副实 → 注册返回真句柄**（关联 LIVE_HANDLERS 逐出）；**(b) onOpen 装配窗口 handler 先入 LIVE_HANDLERS**（自愈非同 tick——装配完成后入表）；**(c) CL-007 Quarkus onOpen getBeanByType → tryGet 对齐**（Spring :74 已 tryGet）。
- **RP-2/RP-4 重定基线**：可抛 stub 已存在（TestJsonRpcKeepAliveInsulation throwOnSend）——取消断言改为「注册表逐出」断言。

## 一、设计结论（十条裁定 A-J）

1. **裁定 A（MA-03 complete 不洗白截断；R1-M1 修正）**：`ServletHttpServerContext` 三处错误路径 `asyncCtx.complete()` 移除——completeWithError（:303-307）+ writerFn fail（:368-372）+ **writerFn 外层 catch（:377-382 装配异常面）**；正常完成路径保持。**A/H 硬同 diff 依赖（显式约束）**：错误路径移除 complete 后连接仅经裁定 H async timeout 终止——两裁定若拆分则错误路径挂至容器缺省超时（零信号）；servlet 无 committed chunked response 的 reset 等价物（vertx 孪生立即 abort，servlet 路径等满超时后 dispatch——已声明语义差异；C10-018 断言 complete 未调 + future.completeExceptionally 保持，不断言连接关闭时点）。护栏 C10-018。
2. **裁定 B（MA-04 归属守卫+Quarkus stop 真语义；R1-B2 重设计）**：①`NopSpringCoreAutoConfig.onClose`（:36-38）加归属守卫（IocCoreInitializer facade 回写桥判定——仅本上下文拥有生命周期才 destroy）。②Quarkus 半：**`NopQuarkusBeanContainer.stop/restart 均空体（禁止作为配对目标——rule 24）**；真语义 = `QuarkusIntegration.stop()` 撤销 start 注册面（BeanContainer.registerInstance 回滚按 IocCoreInitializer.destroy :60-69 模式 + GlobalMeterRegistry 需新增 unregister/clear API + config override 回滚或显式 no-op-with-reason）；**钩子 = nop-quarkus-core-starter 新增 CDI `@Observes ShutdownEvent` bean**（Jandex 已索引，一处覆盖全部 app mains——~20 个 app main 已各自 observe ShutdownEvent 调 CoreInitialization.destroy，本 bean 仅撤销 quarkus 集成层注册面，不重复 destroy）。~20 app main 现状销毁链已闭合（NopAuthApplication:30-33 等先例）——C6-002 缺陷面重划为「quarkus 集成层注册面无撤销」，非「整个应用无 stop」。护栏 C10-013/C10-012。
3. **裁定 C（MA-05 TL 传播；R1-B1 修订——context 线程 remove 配对）**：`QuarkusExecutorHelper.runOnContext` 回调（:53-76）重设 `g_serverContext` ThreadLocal——**runOnContext lambda 内必须 finally `g_serverContext.remove()`**（缺配对 remove 会使 event-loop 线程持上一请求的 VertxHttpServerContext 跨请求泄漏——JsonRpcWebSocketEndpoint.extractInitialHeaders :114 经 TL 读 IP/headers，安全相关）；**2524 MA-02 语义冻结（try/catch→completeExceptionally、bindResult 在 try 内）零回退**，非字节级冻结（seam ④ 勘正）。载体断言 TL 在任务完成后于 context 线程为 null（不只可达）。
4. **裁定 D（C1-003 控制帧并入 sendPump；R1-M3 站点修正）**：仅转换**消息线程直发站点**——handlePing（:308/:310）、handleUnsubscribe（:358）、handleTokenRefresh（:403）、`onMessage`/`validSubscription` 路径的 sendError 调用；**泵内调用方保持包私有直发 helper**（sendErrorFromGraphQL :605 仅被泵内 onNext :689 调用、SubscriptionSubscriber.onError :712-719 已包 sendError——方法体级转换会嵌套入队且 submit 布尔 DROP_NEWEST 丢弃路径需决策：包私有直发即决策）；控制帧经单 FIFO 泵排序在待发数据帧之后（可接受，显式声明）。
5. **裁定 E（C4-002 keepalive 残余收敛；R1-M4 机制钉死）**：①**删除假装句柄字段**（:231 never-completing CF，unregisterKeepAlive 保持为唯一逐出路径——不做 cancel=eject 双形态）②**装配窗口**：两宿主 endpoint（Spring :71-79/Quarkus :67-76）装配完成后调用新 `markReady()`——构造期不入 LIVE_HANDLERS（仅 pending 字段），closed-before-ready 不注册；新机制严格优于现扫描自愈：keepalive ping 不再发往半装配 session（extractor 未绑定窗）。③Quarkus onOpen `getBeanByType`(:70) → `tryGetBeanByType`（NopQuarkusBeanContainer:106 已有）。RP-2/RP-4 钉面按新结构重写（注册表逐出断言；LIVE_HANDLERS 包私有+测试同包可直接断言）。护栏 C10-008。
6. **裁定 F（C4-001 syncGet 面）**：`SpringHttpServerFilterConfiguration`（:119 syncGet）——**批内仅登记现状回归钉**（同步路径零成本回归钉 C10-007）；阻塞面家族判级并档 4.3 MA-03（async dispatch 改造归 7.1 家族单 diff）。**门禁段 :94-108 冻结**。
7. **裁定 G（C4-004 预初始化守卫 + RP-3；R1-M5 语义钉死+爆破半径登记）**：`NopBeansAutoConfiguration`（:33-37）与 `NopMybatisSessionFactoryCustomizer`（:28-32，F3 孪生）加 `CoreInitialization.isInitialized()` 守卫——**取 typed 拒绝**（rule 24 fail-fast 缺省；WARN 跳过被拒）。**爆破半径显式登记**：variant-A 纯 `SpringApplication.run` 应用（SpringDemoMain 形态，无预初始化）现以静默空 discovery 启动——修复后将启动失败（审计意图即 fail-fast）；该行为变更由 RP-3 载体+本条显式登记承载。**RP-3 测试隔离**：未初始化测试上下文与同 surefire fork 其他测试共享 CoreInitialization 静态状态——测试用独立 fork 或 @DirtiesContext 形态隔离（partial-initialize 变体 viability 已核 CoreInitialization:118）。护栏 C10-010。
8. **裁定 H（C6-004 + C1-005 搭车；R1-M2 补 AsyncListener）**：`ServletHttpServerContext` startAsync 两站点（:249/:325）加 `setTimeout`（可配缺省 60s）**+ AsyncListener**（onTimeout → complete 收口；吸收 C6-004「二次 ISE 噪声」并闭合裁定 A 的超时-dispatch 循环；Servlet 规范允许 startAsync 后 setTimeout，时序无碍）——无超时无监听面消除；C1-005 搭车：`context`/`characterEncoding` volatile。
9. **裁定 I（C6-005 Gauge 判存重绑；R1-m3 核验注记）**：`AgroalDataSourceFactory` exportMetrics **恰 17 处 Gauge（11 count+6 duration）**，识别=meter name+`datasource` tag（确定性）→ 判存 remove 旧绑定后重注册（`MeterRegistry.find().remove()` API 充分；热重载不再叠绑）。
10. **裁定 J（owner doc 缺口声明）**：nop-host 无 owner doc——沿用缺口声明（module-groups + 本批契约落 `source-anchors.md` 新锚点行），不新建 03-modules doc。

## 二、载体表

| 工单 | 载体 | 模块叶 |
|---|---|---|
| MA-03 | `TestServletHttpServerContextCompleteSemantics`（新建：completeWithError 不调 asyncCtx.complete + 正常完成保持 + writerFn 外层 catch 面） | nop-spring-web-starter |
| MA-04 | `TestNopSpringCoreAutoConfig`（归属守卫）+ Quarkus stop 配对钉 | nop-spring-core-starter + nop-quarkus-core-starter |
| MA-05 | `TestQuarkusExecutorHelperPropagation`（runOnContext 后 TL 可达 + 2524 段零回退） | nop-quarkus-web |
| C1-003+C4-002 | `TestJsonRpcControlFramePump`（控制帧经 sendPump + 真句柄取消=注册表逐出 + tryGet 对齐） | nop-graphql-core |
| C4-001 | syncGet 面回归钉（现状登记） | nop-spring-web-starter |
| C4-004 | `TestNopSpringDeltaInitGuard`（未初始化 typed 拒绝 + RP-3 不预初始化上下文） | nop-spring-delta |
| C6-004+C1-005 | `TestServletHttpServerContext` 扩展（setTimeout 断言 + volatile 结构钉） | nop-spring-web-starter |
| C6-005 | `TestAgroalMetricsRebind`（判存 remove/重注册） | nop-quarkus-core-starter |

## 三、回归面与接缝

- 回归基线：`./mvnw -o test -pl nop-spring,nop-quarkus -am` + `-pl nop-service-framework/nop-graphql/nop-graphql-core -am`。
- 硬接缝：① ServletHttpServerContext 三合一（A+H+搭车）单 diff；② JsonRpcWebSocketHandler 双残余面（D+E）同 diff 候选；③ SpringHttpServerFilterConfiguration 门禁段 :94-108 冻结；④ QuarkusExecutorHelper 2524 MA-02 **语义冻结**（try/catch→completeExceptionally、bindResult 在 try 内零回退）——非字节级冻结（裁定 C 修订该段）。
