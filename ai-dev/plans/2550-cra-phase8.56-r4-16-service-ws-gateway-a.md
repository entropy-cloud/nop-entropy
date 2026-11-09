# 2550 CRA Phase 8.56 — R4-16 2.3 WS/网关束 a（G36 前半，12 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: roadmap 工作项 8.56；批次定义 = batch-proposal.md §二 :131 R4-16 行；成员发现 = `ai-dev/audits/2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency/`
> Related: plan 2508（R2-05 SSO 异步化+准入池，done）、plan 2509（R2-06 BoundedWindowBuffer，done）、plan 2549（C2-005 双检同型先例）；设计文档 = `ai-dev/design/concurrency/service-ws-gateway-async-chain-a.md`
> **PA**：nop-service-framework 批次表判「无」；**例外**：C3-010 前置原语 CL-025 落 `nop-http-api`（跨模块公共 API）= **plan-first**，授权载体 = 设计文档裁定 E + SPI 回归面。

## Purpose

关闭 R4-16 的 **12 个 P2 工单**：共享可变状态（4：C1-002/004/005/006）、锁内 I/O（2：C2-001←C9-007、C2-003）、异步链终态（3：C3-004 残余/C3-005 残余/C3-009）、WS 错误语义（1：C3-006 残余）、网关取消传播+CL-025 原语（1：C3-010）、Auth 会话刷新失败消费（1：C3-011）。

**基线勘误 E-2550-1**（fresh-session 取证 @ 2026-11-09）：C3-004/C3-005/C3-006/C3-010 为 2508/2509 部分覆盖（残余面见设计 E-2550-1）；其余 8 条原样在位。

## Baseline（live 亲读 @ 2026-11-09）

- `UserContextImpl.roles` 裸 HashSet：addRole :267 synchronized、isUserInRole :322 裸读。
- `GraphQLExecutionContext`：getRequest :127 synchronized / setRequest :132 无锁；dispatchAll :156 synchronized 锁内 dispatch。
- `JsonRpcWebSocketHandler`：validSubscription :403-415 containsKey；sendStreamingMessage :430-435 裸 put；keepAlive :440-448 已绝缘（2509）；onError/session-closed 分支未动。
- `MfaChallenge.verifiedAt` :81 非 volatile。
- `BizObjHelper.loadByProp` :32-42 computeIfAbsent 内 biz invoke。
- `BufferedStreamingPublisher`：2509 重构后 maxAttempts 零命中（重试上限仍委托 retryCallback）。
- `GraphQLSubscriptionPublisher` :52-70 cancel 已转发；context complete 终态待核对。
- `GraphQLServerCallHandler.onCancel` :128-130 空；setOnCancelHandler :250 无人调用。
- `InvokeProcessor`：取消检查零命中；Retry-After 钳制/在途许可池已落（:53/:207-300）。
- `AuthHttpServerFilter` :396-398 `whenComplete` 结果经 `FutureHelper.bindResult` 绑定，丢弃的是 lambda 内 `flushUserContextAsync(...)` 的内层 CompletionStage（R1-Minor-6 勘正）；`AbstractLoginService.flushUserContextAsync` :59 thenRun(clearDirty) 无失败分支。
- **基线测试签名**：`./mvnw -f nop-service-framework/pom.xml clean test`（R2-N-3 勘正统一反应堆形态）Phase 2 起点实际复跑为准。

### Phase 1 - 设计与授权载体

Status: completed（设计六裁定 A-F + E-2550-1 + CL-025 plan-first 授权；draft review 待审）

- [x] 设计文档（六裁定 + 12 成员唯一形态 + 回归面含 SPI 全实现）
- [x] 成员取证（fresh-session：8 条原样、4 条部分覆盖）
- [x] 独立 draft review（R1 must-fix-first → 2B+2M+4m 全闭环，见 Review 记录；R2 复审见下）

### Phase 2 - 12 工单实现 + 回归测试

Status: completed
Targets: nop-graphql-core / nop-gateway / nop-biz / nop-biz-auth-core / nop-http-api / nop-quarkus-web / nop-spring-web-starter + 各模块测试树（R1-Major-4 勘正：nop-auth-core 不存在；nop-service-framework 为聚合器，回归用 -f 反应堆命令）

- Item Types: `Fix`（11）、`Fix+Decision`（1：C3-010 含 CL-025 原语 SPI 面）

**实施顺序**
- 组 1（状态原子化）：C1-002 → C1-004 → C1-005 → C1-006
- 组 2（锁内 I/O）：C2-001 → C2-003
- 组 3（异步链）：C3-004 → C3-005 → C3-009
- 组 4（WS 错误）：C3-006
- 组 5（网关取消 + Auth）：CL-025 → C3-010 → C3-011

**工单 × 载体表**（全新建；每建一类后 grep 校验）

| 工单 | 载体（测试类 / 方法 / 断言目标） | 模块 | 类型 |
|---|---|---|---|
| C1-002 | `TestUserContextRoles#pin2a`（并发 addRole×isUserInRole 一致视图，无 CME/权限误判；setRoles 拷贝进并发 Set） | nop-biz-auth-core | 行为钉 |
| C1-004 | `TestGraphQLContextRequest#pin4`（setRequest/getRequest 监视器一致——并发读写后读得最新值） | nop-graphql-core | 行为钉 |
| C1-005 | `TestJsonRpcSubscriptionRegister#pin5`（同 id 并发注册不覆盖首订阅——putIfAbsent） | nop-graphql-core（R2-N-1 勘正：JsonRpcWebSocketHandler 在 graphql-core，quarkus-web 仅传输包装） | 行为钉 |
| C1-006 | `TestMfaChallengeVisibility#pin6`（verifiedAt volatile——compute 写后 peek 读可见，构造窗断言） | nop-biz-auth-core | 行为钉 |
| C2-001 | `TestDispatchAllSnapshot#pin1`（dispatchAll 锁外 dispatch：慢 loader 不阻塞并发分支——latch 计时断言；P1-2.6-004 不回退） | nop-graphql-core | 行为钉 |
| C2-003 | `TestBizObjHelperDoubleCheck#pin2`（并发 loadByProp 双检：invoke 计数与结果一致性；bin 锁外构建断言） | nop-biz | 行为钉 |
| C3-004 | `TestStreamResubscribeBound#pin3`（重订阅次数达 maxAttempts 后停止 + 退避间隔生效——注入缝确定断言） | nop-gateway（R2-N-2 勘正：BufferedStreamingPublisher 在 gateway） | 行为钉 |
| C3-005 | `TestSubscriptionContextComplete#pin7`（onError/onComplete/cancel 三路径 context complete 均触发） | nop-graphql-core | 行为钉 |
| C3-009 | `TestGrpcCancelPropagation#pin9`（onCancel → abandoned 记录 + context complete + best-effort Future 取消——graphql-core 侧行为/结构钉承载；**勘误 E-2550-3**：nop-graphql-grpc 在 `nop-service-framework/nop-graphql/pom.xml` :21 被 pom 注释排除出反应堆——R1-Minor-7「测试树可建钉」误判（测试树存在但模块不构建），恢复 2547 E-2547-5 先例的编译豁免形态，主实现编译验证走 javac 直核） | nop-graphql-core（承载钉）+ nop-graphql-grpc（编译豁免） | 行为钉 |
| C3-006 | `TestWsErrorSemantics#pin10`（onError 发终止信号帧 + session-closed 取消上游） | nop-graphql-core（R2-N-1 勘正） | 行为钉 |
| C3-010 | `TestClientAbortPropagation#pin11`（CL-025 default 语义 + InvokeProcessor abort 检查点：断连后重试不触发；SPI 实现面 Vertx/Servlet 编译+测试） | nop-gateway + nop-http-api + SPI 两模块 | 行为钉 |
| C3-011 | `TestSessionFlushFailure#pin12`（flush 失败 → dirty 不清 + WARN；成功 → clearDirty） | nop-biz-auth-core | 行为钉 |
| **无静默跳过**（rule 24） | | |

## Exit Criteria

- [x] 12 工单全部实施（组 1–5 串行；C3-009 机制按 R1-Blocker-2 改写形态）
- [x] 载体表逐条存在且断言目标行为（biz-auth-core 7 + graphql-core 9 + biz 2 + gateway 7 = 25 钉全绿；C3-009 grpc 结构钉→javac 编译核替代，E-2550-3）
- [x] 关键项先红后绿或变异论证（≥3）：红① C2-001（TimeoutException）/ 红② C2-003（TimeoutException）/ 红③ C3-010（不抛 CancellationException）——总册 `_tmp/red-green-ledger-2550.md`
- [x] E1 全量编译门禁 BUILD SUCCESS（`./mvnw -o clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` EXIT=0，证据 `_tmp/e1-2550-full-build.log`）
- [x] E3：`./mvnw -f nop-service-framework/pom.xml clean test` **BUILD SUCCESS**（第 3 轮 0F/0E；第 1/2 轮各抓一处执行期问题——pin7 异步投递竞态 + SPB 同步假设，修正后复绿，非弱化）
- [x] **SPI 回归面**（R1-Blocker-1 勘正）：Vertx 适配器 14/14（TestVertxHttpServerContextAdapter + TestJsonRpcWebSocketEndpointLifecycle）+ Servlet 适配器 10/10（TestServletHttpServerContextAdapter）+ nop-auth-sso 桩编译（E1 通道）；**Servlet 6.0 无轮询断连 API 勘误 E-2550-2**（default 档登记）
- [x] **不回退 2508/2509**：SSO 异步化/准入池/StreamingProcessorBufferingTest 12/12 + GatewayFixTest 族全绿（E3 通道含）；BoundedWindowBufferTest 全绿
- [x] owner doc 落账：`service-layer.md`「共享可变状态原子化与断连传播契约（plan 2550）」节 + `source-anchors.md` `GW-002`
- [x] 工具门禁：check-doc-links --strict EXIT=0（顺手修复 3 处既有 broken link）；scan-hollow `--severity high` 逐叶子模块 EXIT=0（nop-biz-auth-core / nop-graphql-core / nop-biz / nop-gateway / nop-http-api 全 0；nop-graphql-grpc 反应堆外编译豁免——E-2550-3）
- [x] `ai-dev/logs/2026/11-10.md` 已更新

## Deferred But Adjudicated

### C3-010 SPI 断连检测的各 server 深度实现
- Classification: `watch-only residual`
- Why Not Blocking Closure: 本批落 CL-025 default 语义（false/无操作）+ 已有实现的 aborted 检测接线；深度 socket 级检测（半开连接探测）属各 server 设施增强，不阻断「取消传播链路已闭合」。
- Successor Required: `no`。

### C3-009 幽灵执行的底层中断（R1-Blocker-2 勘正新增）
- Classification: `watch-only residual`
- Why Not Blocking Closure: `executeRpcAsync` 返回组合 `CompletionStage`，无可取消点；真实中断需引擎层取消句柄贯穿 `IGraphQLEngine`→binding ctx，属引擎 API 增强（P2 范围外）。本批交付 abandoned 记录 + context complete + best-effort Future 取消，取消链路可观测、上下文不泄漏。
- Successor Required: `no`（登记于 owner doc，随引擎演进承接）。

## Review 记录

**Draft review R1（agent_61dd5844，2026-11-10，VERDICT: must-fix-first）**：2 Blocker + 2 Major + 4 Minor。
- Blocker-1：设计裁定 E 的 SPI 回归面「Netty/Undertow/JdkHttp 三实现」经 live 取证不存在——真实实现面 = `VertxHttpServerContext`（nop-quarkus-web）+ `ServletHttpServerContext`（nop-spring-web-starter）+ nop-auth-sso 测试桩；且两模块不在 nop-service-framework 聚合器内，回归命令须单列。已勘正设计 + plan 回归面/E1/E3。
- Blocker-2：C3-009「cancel(true) executeRpcAsync 返回的 future」机制证伪——返回 `CompletionStage<ApiResponse<?>>` 无 cancel；机制改写为 abandoned 记录 + context complete + best-effort Future 取消，底层中断降 watch-only residual。钉改双形态（grpc 结构钉 + graphql-core 行为钉）。
- Major-3：E3 命令 `-pl nop-service-framework -am` 不含子模块 → 改 `-f nop-service-framework/pom.xml clean test`。
- Major-4：`nop-auth-core` 模块名不存在 → `nop-biz-auth-core`；载体表补模块列。
- Minor-5：C1-002 读侧方法名勘正（getRoles/serializeToJson）+ `setRoles` 必须拷贝进并发 Set（否则惰性初始化不转换）。
- Minor-6：baseline `:397` 表述勘正（丢弃的是内层 flushUserContextAsync stage，非 whenComplete 本身）。
- Minor-7：C3-009「模块仅编译豁免」勘正——nop-graphql-grpc 测试树存在，结构钉可建。
- Minor-8：C1-004 契约残注（类外写入方不在同步域）。

**Draft review R2（agent_61dd5844，2026-11-10，VERDICT: must-fix-first——残余载体表列勘正）**：8 项全部确认 FIXED；新发现 2 Major + 3 Minor：
- N-1：C1-005/C3-006 载体模块列误标 nop-quarkus-web——`JsonRpcWebSocketHandler` 在 nop-graphql-core（quarkus-web 仅传输包装 `JsonRpcWebSocketEndpoint`）。已勘正。
- N-2：C3-004 载体模块列误标 nop-graphql-core——`BufferedStreamingPublisher` 在 nop-gateway。已勘正。
- N-3：design §三 行 1 与 plan baseline 仍引旧 `-pl` 命令形态。已统一 `-f` 反应堆形态。
- N-4：design §三 裁定 F 行「nop-auth-core/nop-auth-service」→ nop-biz-auth-core；`saveUserContextAsync` 命名勘正为 `flushUserContextAsync` 调用点。已勘正。
- N-5：scan-hollow 门禁叶子模块清单已枚举（6 模块）。

**Draft review R3（agent_61dd5844，2026-11-10，VERDICT: ready-to-execute）**：R2 五项全确认 FIXED；12 工单全表一致性扫查通过（载体表×裁定表×live 锚点逐条对齐；E3/设计 §三/baseline 命令三处一致）。两条非阻塞备注：①plan Targets 行未列 nop-graphql-grpc（载体列为准，反应堆命令覆盖）；②nop-auth-sso 测试桩回归面实为编译级（-DskipTests/-pl 闭包不含其测试）。

**Closure audit R1（agent_ceb85fe3，2026-11-10，VERDICT: REJECTED——1 Blocker + 2 Minor）**：
- B-1（已闭环）：TestSessionFlushFailure `NopException("redis down")` 编译破损（NopException 无单 String ctor）——lint 修正遗留（TestWsErrorSemantics 同型已修、本处漏），且 lint 修正后未复跑该模块即入提交，「7/7 绿」声明与提交态不一致。修正：测试局部 `RedisDownException`，复跑 7/7 绿 EXIT=0（`_tmp/bac-pins-final.log`）；诚实性勘误已登记总册。
- m-2（已闭环）：E1 证据未持久化 → `_tmp/e1-2550-full-build.log` 落盘。
- m-3（无需动作）：ledger 已诚实披露 pin6-b 等结构钉无独立变异跑；≥3 变异要求由红①②③满足。
- 12 工单 live 逐一核验 PASS（anti-hollow 两处 default 档文档化充分）；Deferred 分类诚实；owner doc/锚点准确；doc-links EXIT=0。

**执行期勘误 E-2550-3（2026-11-10，实施启动时发现）**：R1-Minor-7 与 R2 的「nop-graphql-grpc 测试树存在 → 结构钉可建」为误判——`nop-service-framework/nop-graphql/pom.xml:21` 注释排除 `<module>nop-graphql-grpc</module>`，模块不在 Maven 反应堆内（即设计原稿「pom 注释模块，沿 2547 E-2547-5 先例」的由来，R1 评审原文有此事实但被 Minor-7 覆写后丢失）。恢复：C3-009 钉由 nop-graphql-core 承载，grpc 主实现改动以 javac 直核编译验证（classpath 对齐模块依赖），登记编译豁免。

## Closure

Status Note: 12 工单全修（25 钉全绿 + 三链变异红证；E1/E3/SPI 回归全绿）。closure audit R1 REJECTED（1B+2m）→ 修正（TestSessionFlushFailure 编译破损修复+诚实性勘误登记/E1 证据落盘）→ R2 **APPROVED**（agent_ceb85fe3 fresh session 独立复跑 7/7 证实）。Plan Status active→completed。
Completed: 2026-11-10

Closure Audit Evidence:

- Reviewer / Agent: agent_ceb85fe3（fresh session 独立 closure audit，R1 REJECTED → R2 APPROVED）
- Audit Session: agent_ceb85fe3-2ab5-4381-b2f0-16ae8ec061b9
- Evidence:
  - Exit Criteria 逐条：12 工单 live 逐一核验 PASS（UserContextImpl :229-240/:280-282、GraphQLExecutionContext :127-135/:160-178、BizObjHelper :40-66、JsonRpcWebSocketHandler :442-448/:575-581/:601-616、MfaChallenge :85、BufferedStreamingPublisher :630/:206-207/:295-299+GatewayConfigs :46-47、GraphQLSubscriptionPublisher 三终止路径、GraphQLServerCallHandler :149-167、IHttpServerContext :84-94 + Vertx :144-154 + Servlet :61-71、InvokeProcessor :108-111/:230-249、GatewayHttpFilter :154-165、AiFailover :88-143、AbstractLoginService :62-71 + AuthHttpServerFilter :396-412）
  - 载体复跑：biz-auth-core 7/7（auditor 独立复跑证实）+ graphql-core 9/9 + biz 2/2 + gateway 7/7 = 25 钉全绿
  - 变异红证 ≥3：红①②③（TimeoutException ×2 / 不抛 CancellationException），总册 `_tmp/red-green-ledger-2550.md`
  - `node ai-dev/tools/check-plan-checklist.mjs` 见收口提交（--strict EXIT=0）
  - Anti-Hollow：IHttpServerContext default 档/Servlet 6.0 不可观测档均为文档化 SPI 语义（E-2550-2），非静默跳过；`scan-hollow-implementations.mjs --severity high` 五叶子模块 EXIT=0
  - Deferred 分类检查：C3-010 深度 socket 检测 / C3-009 底层中断均 watch-only residual 且 owner doc 镜像登记，无 in-scope live defect 被降级
  - E1 BUILD SUCCESS（`_tmp/e1-2550-full-build.log`）；E3 反应堆 BUILD SUCCESS；check-doc-links --strict EXIT=0

Follow-up:

- no remaining plan-owned work（两条 watch-only residual 已登记于 Deferred 与 owner doc GW-002）
