# 2551 CRA Phase 8.57 — R4-17 2.3 auth/流缓冲/dispatchAll 束 b（G36 后半，12 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: roadmap 工作项 8.57；批次定义 = batch-proposal.md §二 :132 R4-17 行；成员发现 = `ai-dev/audits/2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency/`
> Related: plan 2550（R4-16，done——CL-025 已落）、plan 2508（R2-05，done）、plan 2509（R2-06，done）、plan 2498（R1-03 C8-001，done）；设计文档 = `ai-dev/design/concurrency/service-auth-buffer-dispatch-b.md`
> **PA**：无 Protected Area（CL-025 原语已在 2550 落地，本批不新增 nop-http-api SPI 面）。

## Purpose

关闭 R4-17 的 **12 个 P2 工单**：执行器治理（3：C4-001/003 残余/004 残余 + C4-002 Proof）、资源卫生（4：C4-005、C8-003、C8-007、C8-009 残余）、热路径（3：C8-008、C9-003、C9-005 残余）+ C10-015 全条与 C10-018 之 C9-003 半边随本体承载。

**成员取证（fresh session @ 2026-11-10）**：原样 7（C4-001/005/008、C8-003/007/008、C9-003）+ 部分覆盖 4（C4-003/004、C8-009、C9-005——前批修复面见设计文档头部注记）+ 实质已修 1（C4-002，Proof 登记）。

## Baseline（live 亲读 @ 2026-11-10，取证 agent 独立核验）

- `BizActionService.java:70,82-94`：executor 按名 getBean，`defaultWorkExecutor`（`BizConstants:40`）全仓无 bean 定义，catch(Throwable) 吞为 null。
- `InvokeProcessor.java:285-287`：delayedFuture 用 JDK delayedExecutor（C4 勘误 8：JDK25 已多线程化，P1 支柱证伪）；fetch 无 per-request timeout（客户端缺省 30s/10s 有界）。
- `AuthHttpServerFilter.java:155`：executeBlocking 包裹含 next.get() 整链；`:357-410` 2508/2550 修复面在位。
- `DownloadHelper.java:40-44`：globalTimer().schedule + `resource.delete()` 返回值忽略，无对账。
- `JWKPublicKeyLocator.java:73-80,106-112`：synchronized 内同步 fetch 无 timeout，失败静默占 60s 速率窗。
- `OpenAiDeltaAccumulator.java:14,151`：两 StringBuilder 无字节上界（grep limit=0）。
- `JsonRpcWebSocketHandler.java:95,122-124,409-410`：maxActiveOperations=1000 硬编码，setter 零生产调用方；无跨连接闸。
- `DefaultFlowControlRunner.java:36-47`：entry 参数未用，单桶 tryAcquire；双消耗站点共享同桶。
- `JsonRpcWebSocketHandler.java:462-479,568-575,229-230,148-152`：发送失败仅 WARN、METHOD_PONG 空分支、无 idle timeout、onThrowable 不清订阅。
- `JwtAuthTokenProvider.java:87-88,40`：synchronized 锁内快检，非 volatile HashMap。
- `StreamingProcessor.java:167-178`：事件循环线程每元素 parse→拦截→serialize 往返（锁面已由 2509 修）。
- **基线测试签名**：`./mvnw -f nop-service-framework/pom.xml clean test`（clean，2550 先例形态）。

### Phase 1 - 设计与取证

Status: completed（设计十二裁定 A-L + C10 前置落位表；draft review 待审）

- [x] 设计文档（十二裁定 + 12 成员唯一形态 + 回归面）
- [x] 成员取证（fresh session：7 原样 / 4 部分覆盖 / 1 实质已修）
- [x] 独立 draft review（R1 must-fix-first → R2 ready-to-execute，记录见 Review 记录——closure audit 指出此处勾选遗漏，勘误补登）

### Phase 2 - 12 工单实现 + 回归测试

Status: completed
Targets: nop-biz / nop-biz-auth-core / nop-graphql-core / nop-gateway / nop-auth-sso / nop-network/nop-rpc/nop-rpc-core / nop-quarkus-web / nop-spring-web-starter + 测试树（R1-Major-6：裁定 J③ 适配器两模块入批）

- Item Types: `Fix`（9）、`Proof`（1：C4-002）、`Fix+Decision`（2：C4-001 含 beans 装配决策、C8-008 含分桶键控语义）

**实施顺序**
- 组 1（执行器治理）：C4-001 → C4-002（Proof）→ C4-003 残余 → C4-004 残余
- 组 2（资源卫生）：C4-005 → C8-003 → C8-007 → C8-009 残余（承载 C10-015）
- 组 3（热路径）：C8-008 → C9-003（承载 C10-018 半边）→ C9-005 残余

**工单 × 载体表**

| 工单 | 载体（测试类 / 方法 / 断言目标） | 模块 | 类型 |
|---|---|---|---|
| C4-001 | `TestBizAsyncExecutorResolution`（①defaultWorkExecutor bean 按名解析命中；②无效 executor 名启动期 fail-fast 类型化错误列全无效名；③bizSequential+非 IPartitionedExecutor 组合启动期 fail-fast 列全违例动作名——R1-Major-3） | nop-biz | 行为钉 |
| C4-002 | Proof 登记：`TestJsonRpcWsSendOffload_C8_001`/`TestSubscriptionPushOffload_C8_001` 复绿即验收；残余移交 C8-009 | nop-graphql-core | Proof |
| C4-003 | `TestUpstreamRetryTiming`（①delayedFuture 经平台 timer+executeOn(globalWorker) 触发（结构钉含 executeOn hop——R1-Major-4）；②fetch 带 timeout 键值断言；③2508 equal-jitter/准入钉不回退） | nop-gateway | 行为钉+结构钉 |
| C4-004 | `TestAuthChainExecutionMedium`（next.get() 不在 executeBlocking callable 内（结构断言）+ 上下文队列介质断言不回退 2508） | nop-biz-auth-core | 行为钉+结构钉 |
| C4-005 | `TestDownloadTempCleanup`（①delete 失败 WARN+路径记录不吞；②对账扫描超龄文件删除、未超龄保留；③interval≤0 关档） | nop-biz | 行为钉 |
| C4-008 | `TestJwkLocatorSingleflight`（并发 kid 未命中共享一次拉取；失败不占 60s 速率窗；有旧钥 grace 返回、无旧钥才 fail；fetch 带 timeout 键值断言） | nop-auth-sso | 行为钉 |
| C8-003 | `TestDeltaAccumulatorBound`（字节上界截断+一次性 WARN+截断标记；onStreamError 清理 accumulator） | nop-gateway | 行为钉 |
| C8-007 | `TestWsSubscriptionAdmission`（①静态配置键读入 maxActiveOperations 生效（setter 覆盖档）；②全局闸超限拒绝+订阅终止 release；③≤0 null 旁路不设上界档） | nop-graphql-core | 行为钉 |
| C8-009 | `TestWsSessionLiveness`（①连续发送失败→断连清理；②pong 记账+idle 超时断开；③onThrowable 清 activeOperations——承载 C10-015） | nop-graphql-core | 行为钉 |
| C8-008 | `TestFlowControlKeying`（不同 entry.resource 独立 IRateLimiter 桶互不稀释——R1-Blocker-1 改写：速率原语零换型，无 maxConcurrent 面；同 entry 共享桶；setter 置新速率后 buckets 重建） | nop-rpc-core | 行为钉 |
| C9-003 | `TestJwtEnsureKeysHotPath`（并发首调 singleflight——N 线程并发 ensureKeys 仅一次底层拉取；失败可重试；锁外快检结构断言——承载 C10-018 半边） | nop-biz-auth-core | 行为钉+结构钉 |
| C9-005 | `TestStreamingPassthrough`（无拦截器档字符串元素免 parse/再 serialize——事件循环调用计数断言；带拦截器路由语义零变化） | nop-gateway | 行为钉 |
| **无静默跳过**（rule 24） | | | |

## Exit Criteria

- [x] 12 工单全部实施（含 C4-002 Proof 登记；C8-008 裁定 I 按 R1-Blocker-1 改写 resource 分桶形态）
- [x] 载体表逐条存在且断言目标行为（**10 载体类 33 钉**[closure audit Minor-6 计数勘误 9→10]：biz 4+4、gateway 2+3+3、graphql-core 3+3、rpc-core 3、biz-auth-core 2+3[含 C4-004 补建 TestAuthChainExecutionMedium]、auth-sso 3 全绿）
- [x] 关键项先红后绿或变异论证（≥3）：红① C4-001 validateExecutors 抹除→pin_b Error；红② C8-008 分桶键抹除→pin_a Failure；红③ C9-003 volatile 抹除→pin_b Failure——总册 `_tmp/red-green-ledger-2551.md`
- [x] E1 全量编译门禁 BUILD SUCCESS（EXIT=0，证据 `_tmp/e1-2551-full-build.log`）
- [x] E3：`./mvnw -f nop-service-framework/pom.xml clean test` **BUILD SUCCESS**（第 2 轮 0F/0E；第 1 轮抓 LoggingInterceptorTest setAttribute(null) NPE——执行期修正为 removeAttribute，非弱化）+ nop-auth-sso SSO_EXIT=0 + nop-rpc-core RPC_EXIT=0 + nop-quarkus-web QW_EXIT=0 + nop-spring-web-starter SPRING_EXIT=0
- [x] **不回退 2508/2498/2509/2550**：SSO 刷新链（TestSsoRefreshAsyncChain SSO 通道绿）、WS 推送卸载/BoundedWindowBuffer/CL-025 断连链既有钉全绿（E3 反应堆通道含）
- [x] owner doc 落账：`service-layer.md`「异步 action 执行器治理与 WS 活性面契约（plan 2551）」节 + `source-anchors.md` `GW-003`
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--severity high` 八叶子模块全 EXIT=0
- [x] `ai-dev/logs/2026/11-10.md` 已更新

## Deferred But Adjudicated

### C4-004 auth 专用隔离执行池
- Classification: `watch-only residual`
- Why Not Blocking Closure: 下游链已出 executeBlocking 载体（裁定 D），残余仅在「宿主 worker 池仍承载 auth 上下文加载段」——无跨请求互占面；独立池属设施增强。
- Successor Required: `no`。

### C4-004 logout/OAuth 冷路径的 next.get() 残留
- Classification: `watch-only residual`
- Why Not Blocking Closure: 主 token 路径已出 blocking 载体（pin_a 结构钉）；logoutPath/OAuth-code 冷路径频次极低且链长有限——载体 pin_c 诚实登记其可见性。
- Successor Required: `no`。

### C9-005 带拦截器路由的序列化共享
- Classification: `watch-only residual`
- Why Not Blocking Closure: 快径仅限无拦截器档（事件循环往返主面已消除）；带拦截器路由的 parse→拦截→serialize 语义保持，宽化（序列化结果跨拦截器共享）需语义论证。
- Successor Required: `no`。

## Review 记录

（待 draft review）

## Closure

Status Note: 12 工单全修（10 载体类 33 钉全绿 + 三链变异红证；E1/E3/SPI 通道全绿）。closure audit R1 **APPROVED-with-corrections**（2 Major + 3 Minor + 2 计数勘误）→ 全部闭环：M-1 C4-004 载体补建 TestAuthChainExecutionMedium ×3 结构钉；M-2 validateExecutors 生产接线闭环（BizExecutorStartupValidator bean depends-on nopBizObjectManager，InjectValue 容器注入——初版 @Inject 按类型解析失败由 E3 抓获修正）；m-3 J③ 适配器接线（Jakarta/Spring setMaxIdleTimeout + ≤0 档显式 WARN）；m-4 全局闸泄漏路径闭环（handleSubscription 注册段对称归还）；m-5 JWKS 超时配置键（SsoConfig.jwksFetchTimeoutMillis）；计数勘误 9→10 载体类 + draft review 勾选补登。修正后 E3 反应堆第 3 轮 + E1 全量复绿。
Completed: 2026-11-10

Closure Audit Evidence:

- Reviewer / Agent: agent_c16880a4（fresh session 独立 closure audit R1 APPROVED-with-corrections → 修正清单全闭环，修正后复验 biz-auth-core 5/5 + auth-sso 3/3 + E3/E1 复绿）
- Audit Session: agent_c16880a4-2b93-4afc-86f0-f1a3b4788864
- Evidence:
  - 12 工单 live 逐一核验 PASS（auditor 独立复读全部触点文件）
  - 载体独立复跑：rpc-core 3/3、biz 8/8、gateway 8/8（auditor 自跑）+ 修正后 biz-auth-core 5/5、auth-sso 3/3
  - 变异红证：`_tmp/mut2551-{1,2,3}.log` genuine 确认（auditor 核验）
  - Anti-Hollow：auditor 判定 clean（DefaultWorkExecutor no-op 文档化/UOE 类型化 fail-fast/静态闸+源码钉披露诚实）
  - Deferred 分类：auditor 判定合法（C4-004 隔离池、C9-005 带变换序列化共享 watch-only；新增 C4-004 冷路径残注）
  - E1 BUILD SUCCESS（`_tmp/e1-2551-final.log`）；E3 反应堆 BUILD SUCCESS（`_tmp/e3-2551-final3.log`）；check-doc-links EXIT=0；scan-hollow 八叶子 EXIT=0
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` 见收口提交

Follow-up:

- no remaining plan-owned work（watch-only residuals 已登记于 Deferred 与 owner doc GW-003）
