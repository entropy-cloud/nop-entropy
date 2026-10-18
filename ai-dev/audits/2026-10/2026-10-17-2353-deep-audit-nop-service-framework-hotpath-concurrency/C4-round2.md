# C4 线程池治理——请求热路径五模块 第 2 轮盲审（深挖）报告

## 审计单元

| 项 | 值 |
|---|---|
| 审计单元 | nop-service-framework 请求热路径五模块（8 源码根，406 文件 / 42,748 行） |
| 仓库 / 分支 | `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`（HEAD 5994c18211） |
| 维度 | C4——线程池与执行器治理 |
| 轮次 | 第 2 轮深挖（盲审） |
| 本轮输入 | 第 1 轮报告 `C4-thread-pools.md`（C4-001~006、CL-010/011、载体全景 9 站点）；任务下发时明示的跨维度线索：C3-010（无取消传播）、C3 轮 CL-007（DownloadHelper 定时删除 RuntimeException 静默吞）、C2 轮 CL-016（OAuthLoginServiceImpl refreshToken syncGet 在 runOnContext 任务队列线程——移交本维度终裁）；基线 `_tmp/cra-baseline-nop-service-framework-hotpath.txt`；全部结论以当前 live code 实测为准 |
| 日期 | 2026-10-18 |

## 盲审纪律声明

本轮为盲审轮次：**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件，唯一例外为本维度第 1 轮文件 `C4-thread-pools.md`（必要输入）。未读取同目录其他维度文件。跨维度关联仅使用任务下发时明示的三条线索描述（C3-010、C3 轮 CL-007、C2 轮 CL-016），其内容以本轮对 live code 的独立复核为准。实测过程中一次解包 quarkus-core 4.0.0.Beta1（本仓库 `pom.xml:80` 锁定版本）反编译 `ExecutorRecorder` 系字节码，用于载体参数实测，属依赖库事实核查，不构成越界。前轮已立案 6 条不重复立案；api-core（`nop-api-core`）与宿主（quarkus）代码仅作对照引用。

## 本轮目标（任务下发 ①~⑤）

1. **①** C4-003 失效面扩展（「JDK 全局 Delayer 单线程」族）：全站点 grep + 双层重试算术复核 + 与 C3-010 的修复耦合。
2. **②** C4-001 失效面：xbiz `executor` 属性使用率与声明站点清单（生产配置现实性）。
3. **③** C4-004 失效面：executeBlocking 载体的池身份与容量 + token 刷新同步执行无超时包装的后果量化。
4. **④** 执行载体全景表收口：抽验 4 行 + 补漏；CL-016 终裁（立案或维持线索）。
5. **⑤** C4-005 与 C3 轮 CL-007 同位点关系裁定（归并/关联）。

---

## ① C4-003 失效面扩展执行结果

### 1.1 `CompletableFuture.delayedExecutor` 全仓库站点

全仓库 Java 源码 grep（排除 `_gen`/`target`/`ai-dev`）显示 `delayedExecutor` 在**产品代码中仅 1 个站点**：

- `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/InvokeProcessor.java:200`（即 C4-003 本案，live code 未修，行号与第 1 轮一致）。

**五模块内无其他共享点**。第 1 轮全景表第 5 行「五模块内唯一 Delayer 用户」经全量 grep 确认无误。

### 1.2 Delayer 家族扩展：同 JVM 的 `orTimeout` 共存面（新登记 CL-022）

`delayedExecutor` 与 `CompletableFuture.orTimeout`/`completeOnTimeout` 在 JDK 内部共享同一个进程级单 daemon 线程（`CompletableFuture.Delayer`）。全仓库 grep `orTimeout` 发现 **10 个产品代码站点**（均在五模块之外，属 nop-ai / nop-message 审计单元）：

| 模块 | 站点 |
|---|---|
| nop-message-kafka | `KafkaMessageService.java:313`（sendTimeout） |
| nop-message-pulsar | `PulsarMessageService.java:273`（sendTimeout） |
| nop-ai-gateway | `ChannelMessageServiceImpl.java:351, 418`（dispatchTimeout） |
| nop-ai-agent | `LocalAgentMessenger.java:110`、`TeamTaskFlowOrchestrator.java:634`、`MemberFanOutDispatcher.java:322, 383`、`CallAgentExecutor.java:459`、`AgentToolDispatcher.java:690`、`AgentCallDelegate.java:93` |

含义：C4-003 的失效面不止「本模块占用 JVM 全局单线程」，而是「网关重试链的延迟条目（单条最长驻留 30s）与重试发起重活，同 **11 个站点的 timer 任务共享唯一 Delayer 线程**」。orTimeout 条目本身轻量，但 InvokeProcessor 是其中唯一的「重活」用户——重试发起（`buildHttpRequest` 头复制 + `fetchAsync` 调起）在 Delayer 线程同步执行，直接推迟其他 10 个站点的超时判定。跨单元共享资源登记 → **CL-022**（本文末）。

### 1.3 「双层重试 ×16 / 90s」算术复核（第 1 轮勘误 ×2）

live code 链路（全部实测）：

- 内层：`InvokeProcessor.invokeUrl:135-136` `maxRetries=3` → `invokeUrlWithRetry`（429 分支 `:153-159`、5xx 分支 `:162-167`），**主 URL 最多 1+3=4 次 fetch**。
- 外层：`AiFailoverGatewayInterceptor`（`gateway-defaults.beans.xml:64-71` 默认装配，`maxRetries=3`、`fallbackUrls` 默认空列表）。`tryInvokeWithFallback:96-103` 在主链耗尽（内层 `retriesLeft==0` 才抛 `ERR_GATEWAY_UPSTREAM_429/FAILED`）后调 `tryFallbackUrl:111-145`——**fallback 直连 `httpClient.fetchAsync`，完全不经过 InvokeProcessor，每个 fallback URL 仅 1 次调用、无内层重试、无延迟**，`maxFallbackAttempts=min(|fallbackUrls|,3)`。

**勘误 1（×16 → ×7）**：第 1 轮「(1+3)×(1+3)=16 次」的乘法语义不成立。fallback 路径绕过重试引擎，实际语义为**加法**：单请求上游调用上限 = 4（主 URL 含重试）+ 3（fallback 单发）= **7 次**。放大倍数由 16× 修正为 7×，C4-003 的 P1 判级依据（放大 + 全局单线程 + 无取消）不受影响。
**勘误 2（5xx 退避序列）**：第 1 轮「5xx 退避 2-3s / 4-5s / 8-9s，挂起 14-22s」误用了 429 默认退避公式。live code 5xx 分支为 `2^(3-retriesLeft)` = 1s/2s/4s（+jitter ≤1s）→ **挂起 7-10s**；「2/4/8s（14-17s）」实际属于 `parseRetryAfter:230-232` 的 429 无头默认退避。**90s 上界（429 携带超大 Retry-After，`MAX_RETRY_DELAY_MS=30_000` 钳制 ×3 次）复核确认成立**：3×30s=90s，另加 4 次 RTT 与 fallback 段耗时。
**勘误 3（XPL 求值位置）**：第 1 轮称重试发起链包含 URL 表达式求值。实测 `invokeUrl:129` 的 `invoke.getUrl().invoke(...)` 求值**仅发生一次且在调用方线程**，重试复用已解析的 `url` 字符串；Delayer 线程上执行的是 `buildHttpRequest`（:242-256 头复制 + body 设置）与 `fetchAsync` 调起。原结论（重活占全局单线程）方向不变，量化口径收窄。
**附带面**：`buildHttpRequest` 与 `tryFallbackUrl` 均**未设置请求超时**（无 `setTimeout`），上游调用时长仅受 HTTP 客户端实现级/部署级配置约束（JDK 实现仅在配置 `readTimeout` 时才有界，`JdkHttpClient.java:218-221`）。

### 1.4 与 C3-010（无取消传播）的修复耦合

实测取消面：`GatewayHttpFilter` 全文仅 `:238-241` 一处 `streamingResponse.abort()`（流式路径）；非流式 invoke 链（`filterAsync:145-155` 返回的 future）**无任何 cancel/abort 挂钩**，`InvokeProcessor`/`AiFailoverGatewayInterceptor` 全链不观测取消。修复耦合结论：

- **只修载体不修取消**（如 delayedFuture → globalTimer）：幽灵重试照旧，死客户端的重试仍烧 7 次上游调用与 timer 槽位；
- **只修取消不修载体**：Delayer 占用收窄但首个 30s 延迟条目与重试发起仍在全局单线程上；
- **裁定**：两项修复必须同批落地（同一 PR 或 C3-010 先行），且 failover 层（C4-003 F3③）与流式重订阅（F3④）需复用同一取消检查原语。建议修复计划将 C4-003 与 C3-010 标记为**互为前置的耦合对**。

---

## ② C4-001 失效面执行结果

### 2.1 xbiz `executor` 属性使用率：**0 / 717**

- 全仓库 `*.xbiz` 文件共 **717** 个（`find . -name "*.xbiz"`，排除 target/ai-dev）。
- grep `executor=` 于全部 xbiz：**0 命中**；grep `biz-sequential`（xdef 属性名 `bizSequential`）：**0 命中**。
- `getExecutor()` 的消费方全仓库仅 1 处：`BizActionService.getWorkExecutorBean:108-113`。
- `callActionAsync` 生产调用方：**仍为 0**（仅 nop-api-core 接口定义 + 本实现 + 2 个测试）。

### 2.2 声明站点清单与生产配置现实性

**声明站点清单 = 空集**。生产现实性结论：per-action 执行器指定这一特性在仓库内**零采用**；`callActionAsync` 若被接线，**全部 717 个 xbiz 的所有 action 都走默认路径** `getBean("defaultWorkExecutor")`——而该 bean 全仓库仍无定义（唯一出现即 `BizConstants.java:40` 常量）。第 1 轮 C4-001 的「默认路径确定性失败」因此从「未声明 executor 的 action 走默认」收紧为「**一切 action 走默认，无一例外**」。C4-001 判级维持 P2（前置条件仍是 callActionAsync 被接线，目前无生产调用方）。

### 2.3 新增失效证据：xdef 文档契约与实现背离

`nop-service-framework/nop-biz/_dump/nop-app/nop/schema/biz/xbiz.xdef:25`：

```
@executor executor + "Executor"为BeanLoader中的bean的id, 用于控制异步执行时的分组
```

该 schema 文档暗示 bean id 为「属性值 + `Executor` 后缀」的命名约定，但实现（`getWorkExecutorBean`）**原样使用属性值、不追加任何后缀**，全仓库也不存在按后缀约定的查找代码。按 schema 文档编写 xbiz 的作者会把 bean 命名为 `fooExecutor` 并声明 `executor="foo"`，而运行期查找的是 `foo`——首个请求必然 `ERR_IOC_UNKNOWN_BEAN_FOR_NAME`。该背离强化 C4-001 的「无启动期校验」面，并入 C4-001 F3④（范围修正为：声明站点空集 + 文档契约误导），不另立案。

---

## ③ C4-004 失效面执行结果

### 3.1 executeBlocking 载体的池身份与容量（实测）

调用链：`AuthHttpServerFilter._filterAsync:155` `routeContext.executeBlocking(...)` → `VertxHttpServerContext:411-413` → `QuarkusExecutorHelper.executeBlocking:69-82`：

- 已在工作线程（`BlockingOperationControl.isBlockingAllowed()`）：**当前线程内联**执行；
- 在 IO 线程：`ExecutorRecorder.getCurrent().execute(...)` → **quarkus 主执行器**。

载体参数（反编译 `quarkus-core-4.0.0.Beta1.jar` 实测，仓库 `pom.xml:80` 锁定该版本）：

- `ExecutorRecorder$MaxThreadsCalculator$Holder`：默认最大线程数 = `max(8 × availableProcessors, 200)`；
- `ExecutorRecorder.createExecutor`：`queueSize` 未配置时 `setQueueLimited(false)` → **默认无界队列**；线程名 `executor-thread-%t`。

**勘误 4**：第 1 轮 C4-004 触发时序中「quarkus 默认 worker ~128」无出处，修正为上述实测参数。容量修正的后果方向：池上限更大（8C 机器上 200），但**无界队列 + EnhancedQueueExecutor 增长算法意味着阻塞型任务（token 刷新、下游整链）可长期钉住线程、溢出进入无界排队**，饿死形态从「占满 max」修正为「钉住活跃线程 + 队列无界堆积（内存与排队延迟）」——缺陷方向不变，占用模型更精确。

### 3.2 token 刷新同步执行无超时的后果量化（第 1 轮降档理由被推翻）

第 1 轮 C4-004 判 P2 的降档理由是「仓库内无远程刷新实现」。**本轮推翻该前提**：

- `ILoginService.refreshToken`（`ILoginService.java:123`，同步返回 `String`，接口在五模块内的 `nop-biz-auth-core`）有两个实现：
  - `LoginServiceImpl.refreshToken`（nop-auth-service）：`LoginServiceImpl.java:953-961`，本地 JWT 重签，纯 CPU 微秒级——**无害形态**；
  - `OAuthLoginServiceImpl.refreshToken`（nop-auth-sso）：`:247-268`，**`FutureHelper.syncGet(httpClient.fetchAsync(...))`——阻塞式 HTTP POST 到 IdP token 端点**（Keycloak 形态，`sso-defaults.beans.xml:33-34` `/realms/{realm}/protocol/openid-connect/token`）。
- SSO 装配：`sso-defaults.beans.xml:15-21`，`nop.auth.sso.enabled=true` 时 `nopLoginService` 切换为 `OAuthLoginServiceImpl`——**单属性切换的一等公民部署形态**。
- 超时面：`newRefreshTokenRequest:270-285` **未设置请求超时**；`FutureHelper.syncGet`（api-core `FutureHelper:158`）→ `ContextTaskQueue.syncGet` 无自身超时；JDK HTTP 实现仅部署配置 `readTimeout` 时才有界（`JdkHttpClient.java:218-221`）。调用点与契约层均无超时保证。
- 执行线程身份（CL-016 描述的核实）：`handleUserContext:357` `ctx.runOnContext(task)` → `BaseContext:228-236` → `ContextTaskQueue.enqueue:113-125`（无其他线程 sync 等待时返回 false）→ 调用方线程 `flush()` **内联执行**任务；任务内 `refreshToken` 的 `syncGet` 使该线程进入 `ContextTaskQueue.syncGet:46-105` 的 `queueReady.await()` 阻塞等待，**期间该线程持有 context 任务队列的 processingThread 角色**，该请求上下文的后续排队任务全部串行化在 IdP RTT 之后。

**量化**：`autoRefreshToken` 默认 true（`AuthHttpServerFilter:70-74`），token 剩余寿命 <50%（`isNeedRefresh:320-329`）即触发——稳态下约半数请求落在刷新窗口，每会话半衰期一次。SSO 部署中每次触发 = worker 载体线程被钉住一个完整 IdP RTT（正常 50-500ms；IdP 故障时数秒至无界）。并发 N 个半衰期请求 × RTT 直接在无隔离的全局 worker 池上叠加，IdP 慢响应/不可达（SSO 部署的常见故障条件）即演化为全站请求排队。此量化结果即新发现 **C4-007** 的立案依据（见下），同时将 C4-004 的后果表述从「很可能（依赖远程实现接入）」升级为「代码已确认（远程实现在仓）」，C4-004 判级维持 P2 不变（其本体为载体选择缺陷，与实现无关）。

---

## ④ 全景表抽验与补漏执行结果

### 4.1 抽验结果（抽验 6 行，超出要求 4 行）

| 行 | 抽验点 | 结果 |
|---|---|---|
| 2 | `AuthHttpServerFilter._filterAsync:155` executeBlocking 包住下游整链；载体=`QuarkusExecutorHelper:69-82` | **吻合**；池参数修正（见 3.1，勘误 4） |
| 3 | `JsonRpcWebSocketHandler` 构造器 `:82-84` `globalTimer().scheduleWithFixedDelay(sendKeepAlive,10,10,s)`——构造即启动 | **吻合**（C4-002 生命周期不对称的前提成立） |
| 4 | `GrpcServer:82-85` 自建 `DefaultThreadPoolExecutor`；`doStop:152-163` 先 `server.shutdownNow()` 后 `executor.destroy()` 无 awaitTermination | **吻合**（C4-006 维持） |
| 5 | `InvokeProcessor.delayedFuture:198-202` | **吻合**；补充参数勘误见 1.3（勘误 1-3） |
| 6 | `BizActionService.callActionAsync:70,82-94` 双分支 + `getWorkExecutorBean:108-113` | **吻合**；使用率实测 0/717（见 2.1） |
| 9 | `SubscriptionSubscriber.onNext:458-473` 源发射线程同步 `session.sendMessage`；`onComplete:485-495` 同 | **吻合**（C4-002 F3② 维持） |

补漏扫描：五模块 8 源码根全量 grep `new Thread|Executors.new|supplyAsync|runAsync|ScheduledExecutor|GlobalExecutors.|DefaultThreadPoolExecutor|.schedule`——除全景表已登记站点外**零新增载体**；`FlowControlFetcher/GraphQLFlowControlInvoker` 的 `runner.runAsync` 实测为 `DefaultFlowControlRunner:36-48` 内联执行（rateLimiter 阻塞等待，CL-011 口径确认），非隐藏池。全景表 9 行结构**维持不变**，仅第 2 行池参数按勘误 4 修正。

### 4.2 CL-016 终裁：**立案 → C4-007**

C2 轮移交的 CL-016（OAuthLoginServiceImpl refreshToken syncGet 在 runOnContext 任务队列线程）经本轮独立复核，全部要素属实且**严重度高于线索级**，终裁立案（理由：远程阻塞实现在仓存在、无超时、异步 SPI 在仓存在而未被使用、执行载体为全局无隔离 worker——见下条完整立案）。与 C4-004 不构成重复：C4-004 立案本体是过滤器层的载体选择（`executeBlocking` 包住下游整链），C4-007 立案本体是登录服务层的**同步阻塞远程调用形态与契约缺陷**（`ILoginService.refreshToken` 同步契约 + `syncGet` 无超时 + `ILoginSpi.refreshTokenAsync` 异步能力闲置），文件、缺陷族、修复方向均不同；两者以 F3 互引。

---

## ⑤ C4-005 与 C3 轮 CL-007 同位点关系裁定

**裁定：关联（互引），不归并。**

- live code 复核（`DownloadHelper.java:39-42`）：定时删除任务为 `() -> { resource.delete(); return null; }`，返回的 `ScheduledFuture` 被丢弃；`resource.delete()` 的生产实现为 `FileResource:86-88` → `java.io.File.delete()`——**返回 boolean、失败不抛 RuntimeException**（仅 SecurityManager 场景抛 `SecurityException`）。
- 由此得到**主失效面修正**：C3 轮 CL-007 描述的「RuntimeException 静默吞」是**次级面**（SecurityException 路径 + 调度器 Callable 异常捕获语义）；**主失效面是 boolean 返回值被忽略**——文件被占用（Windows 下下载句柄/杀软扫描持锁）或权限不足时 `delete()` 返回 false，无异常、无日志、无重试，文件永驻。仅修复异常面（包装 try/catch + 日志）**修不到主路径**。
- 归并/关联裁定依据：两案同位点（`DownloadHelper.java:39-42` 单一站点）但缺陷族不同——C4-005 立案的是调度治理（共享全局 timer 载体 + 无兜底对账，C4 族），CL-007 是错误可见性（F9 静默吞，C3 族）。**保持 C4-005 为立案主体，CL-007 作为其 F3 互引线索**；修复任务必须一次覆盖三件事：① 检查 `delete()` boolean 返回值并记录失败（主面）；② try/catch 包装 + 失败日志（次面）；③ 平台级 temp 目录周期对账扫描（兜底自愈）。修复计划若按 CL-007 单独设计（仅异常包装）将留下主面缺口——此裁定需进入修复计划校验项。

---

## 新增发现

### [nop-service-framework-hotpath-C4-007] SSO 部署下半衰期 token 刷新在认证过滤器载体线程上执行无超时的阻塞 IdP HTTP 调用（FutureHelper.syncGet 包裹远程 fetchAsync）——IdP 慢响应即在无隔离的全局 worker 载体上级联钉线（CL-016 终裁立案；C4-004 F3② 的后果证实与实现层立案）

- **文件**: `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/login/OAuthLoginServiceImpl.java:247-268, 270-285`；调用点（五模块内）`nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:70-74, 320-329, 342-384`（`:359` 同步调用）；契约 `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/ILoginService.java:123, 168-171`；装配 `nop-auth/nop-auth-sso/src/main/resources/_vfs/nop/auth/beans/sso-defaults.beans.xml:15-21`；执行载体（对照）`nop-quarkus/nop-quarkus-web/.../QuarkusExecutorHelper.java:69-82`、`nop-kernel/nop-api-core/.../ContextTaskQueue.java:46-105, 113-125`
- **证据片段**:
  ```java
  // OAuthLoginServiceImpl.refreshToken:247-268 —— 同步阻塞远程调用
  public String refreshToken(IUserContext userContext, AuthToken authToken) {
      ...
      HttpRequest req = newRefreshTokenRequest(refreshToken);       // 未设置任何超时
      return FutureHelper.syncGet(httpClient.fetchAsync(req, null).thenApply(res -> {
          ...
          userContext.setAccessToken(response.getAccessToken());
          ...
      }));
  }
  ```
  ```java
  // AuthHttpServerFilter.handleUserContext:357-359 —— runOnContext 内联任务中同步调用
  ctx.runOnContext(() -> {
      if (needRefresh) {
          String accessToken = loginService.refreshToken(userContext, authToken);  // 阻塞 IdP RTT
  ```
- **严重程度**: P1（限定 SSO 部署形态：`nop.auth.sso.enabled=true` 的单属性一等公民配置；该形态内触发条件为默认开启 + 常见故障）
- **并发场景**: SSO 部署下每个非公开请求经 `AuthHttpServerFilter` 链路：`executeBlocking` 载体（quarkus 主执行器，默认 max=max(8×cores,200)、无界队列）→ `getUserContextAsync` 完成线程 → `ctx.runOnContext` 内联任务 → `refreshToken` 同步阻塞 IdP HTTP POST。阻塞期间该线程持有 `ContextTaskQueue.processingThread` 角色，本请求上下文的所有排队任务串行化在 RTT 之后。
- **触发时序**: T1 `autoRefreshToken` 默认 true，token 剩余寿命 <50% 即 `isNeedRefresh` 为真（稳态约半数请求/每会话半衰期一次）→ T2 worker 载体线程进入 runOnContext 内联块，`syncGet` 阻塞等待 IdP token 端点响应（正常 50-500ms）→ T3a IdP 慢响应/网络故障：每次刷新钉住一个 worker 线程数秒至无界（调用点无超时、JDK 客户端实现仅部署级 `readTimeout` 配置时才有界）→ T4 并发半衰期请求持续到达，无隔离全局 worker 池活跃线程逐个被钉住、溢出任务进入无界队列，全站请求（含本地 JWT 部署不受影响的路径全部请求）排队饿死；T3b IdP 恢复后队列以 RTT 节奏缓慢排空。非竞态、负载放大类，故障条件下必然演化。
- **现状**: 平台已有异步刷新 SPI `ILoginSpi.refreshTokenAsync(RefreshTokenRequest, IServiceContext)`（`ILoginSpi.java:59`），但 `ILoginService.refreshToken` 同步契约使过滤器只能同步调用；SSO 实现选择 `FutureHelper.syncGet`（阻塞包装）而非异步链路；`LoginServiceImpl.refreshToken`（本地 JWT）为无害形态说明同步契约本身可行、问题在远程实现未走异步通道且无超时。C2 轮 CL-016 的「runOnContext 任务队列线程」描述经 `BaseContext:228-236` + `ContextTaskQueue:113-125, 46-105` 逐行核实成立。
- **风险**: IdP 故障（SSO 部署常态故障条件）→ 认证过滤器半衰期刷新把全局 worker 载体线程按 RTT 钉住 → 全站吞吐坍缩；无界队列使故障表现为延迟雪崩而非快速失败；停机/降级无统一取消点。与 C4-004 叠加：同一载体还承载全部下游请求处理，故障域完全无隔离。
- **建议**: ① `OAuthLoginServiceImpl.refreshToken` 改造为基于 `refreshTokenAsync`/`fetchAsync` 的异步链路，过滤器侧 `handleUserContext` 以 `thenCompose` 串联刷新与下游（消除 syncGet）；② 契约层为 `ILoginService` 补充 `refreshTokenAsync` 默认方法（或过滤器对无异步能力的实现降级为带超时的同步包装）；③ 调用点显式设置刷新请求超时（如 5-10s，`HttpRequest.set_timeout`/客户端级配置兜底）；④ 按用户/会话聚合刷新（同会话并发半衰期请求合并为一次刷新，后续请求等待在途刷新结果）；⑤ 与 C4-004 建议的 `nop-auth-worker` 专用有界执行器合并规划。
- **失效面枚举**（F3）: ① `OAuthLoginServiceImpl.refreshToken` 的 `syncGet` 远程调用（:258）——**本案本体未修**；② 调用点 `AuthHttpServerFilter.handleUserContext:359` 同步契约调用——**未修**（C4-004 F3② 互引，本案提供实现层证据）；③ `newRefreshTokenRequest:270-285` 无超时请求构造——**未修**；④ 契约同步面 `ILoginService.refreshToken:123` 与 sync 包装 `getUserContext:168-171`（`FutureHelper.syncGet(getUserContextAsync)`，五模块内当前无调用方，潜在面）——**未修**；⑤ 本地形态对照 `LoginServiceImpl.refreshToken:953-961` 纯本地重签——**不涉案**（同一契约的无害实现，佐证缺陷在远程实现形态而非契约存在本身）；⑥ `ContextTaskQueue.syncGet:46-105` 在 await 期间持有 processingThread 角色（上下文任务串行化）——nop-api-core F3 互引（WI 2.2 范畴），本案登记使用侧后果；⑦ Redis 同步面（nop-auth-service MFA/短信/限频 store 的 8 处 `syncGet`，如 `RedisMfaChallengeStore.java:93,152,199`）——五模块外，**同族线索移交相应审计单元**。
- **信心水平**: 确定（阻塞调用、无超时、异步 SPI 闲置、载体身份、装配条件全部为代码/字节码事实；全池饿死后果为故障条件下必然的负载演化）
- **误报排除**: 非远程实现假设——实现在仓且 beans 装配条件明确；非把本地 JWT 形态误报为远程——两实现已区分并分别定性；非与 C4-004 重复计数——本案缺陷核（syncGet 阻塞远程 + 无超时 + 异步 SPI 闲置）位于不同文件、不同模块、不同缺陷族，已双向互引；非宿主设施本体重审——quarkus 池参数仅作占用面引用。
- **复核状态**: 复核通过，**维持 P1**，附复核勘误 9（2026-10-18 维度复核，按任务特别指令 a-e 逐项终裁）——a) `OAuthLoginServiceImpl.refreshToken:247-268` 的 `FutureHelper.syncGet(httpClient.fetchAsync(...))`（:258）阻塞 IdP HTTP POST 亲核成立，`newRefreshTokenRequest:270-285` 未设请求超时成立；b) `AuthHttpServerFilter:359` 调用链亲核成立（`autoRefreshToken:70-75` 默认 true → `isNeedRefresh:320-329` 半衰期 → `handleUserContext:357-378` runOnContext 内联 lambda → :359 同步刷新 → :374 下游链）；c) `autoRefreshToken` 默认 true + 半衰期窗口成立，且实测 IdP 失败返回 null 后**不更新 cookie/不降频**——故障期间刷新窗口内每请求重复阻塞，放大机制成立；`ContextTaskQueue` 语义实测：await 期间外层 flush 的 `processingThread` 角色仍持于等待线程，本上下文全部任务被钉在该线程；d) 第 3 轮勘误 7 的修复面更正复核确认（`ILoginSpi.refreshTokenAsync:59` 唯一实现 `LoginApiBizModel:153-157` 无令牌轮换、SSO 实现不实现 ILoginSpi——`ILoginService` 契约异步面 + 剥离 syncGet 方向成立，`HttpRequest.timeout:123-141` API 在位）；e) **P1 维持**：P1 判定支柱（默认开启 + SSO 单属性一等部署 + IdP 过载/挂起 → 无隔离全局 worker 池被钉满 → 全站确定性吞吐坍缩）在勘误后仍成立。**勘误 9**：「数秒至无界」「仅部署级 readTimeout 时才有界」表述不完整——`HttpClientConfig.java:35-37` 默认 `readTimeout=30s/writeTimeout=30s/connectTimeout=10s`（`http-api.beans.xml` `nopHttpClientConfig` ioc:config 默认装配），默认部署每次刷新钉线**有界 ≈30s**；修正后论证：brownout（IdP 可达不响应）触发下，全会话在 TTL/2 后进入刷新窗口，吞吐坍缩至 ≈ max(8C,200)/30s——仍满足本 campaign P1 bar（确定性服务级停摆）。触发条件精确化：IdP 连接拒绝（快速失败）危害有限，P1 挂钩的是 brownout/慢响应形态。现状=未修。

---

## 维度集外线索

### [nop-service-framework-hotpath-CL-022]（维度集外线索，不立案展开）JVM 全局 `CompletableFuture.Delayer` 单线程的跨审计单元共存面：1 处 delayedExecutor（本案五模块）+ 10 处 orTimeout（nop-ai / nop-message 单元）

- 形态归属：C4 同族（进程级共享调度单线程治理），主体在五模块之外。
- 证据：JDK 语义 `delayedExecutor` 与 `orTimeout` 共享 `CompletableFuture.Delayer` 静态单 daemon 线程；站点清单见本文 1.2 表（`KafkaMessageService.java:313`、`PulsarMessageService.java:273`、`ChannelMessageServiceImpl.java:351,418`、`LocalAgentMessenger.java:110`、`TeamTaskFlowOrchestrator.java:634`、`MemberFanOutDispatcher.java:322,383`、`CallAgentExecutor.java:459`、`AgentToolDispatcher.java:690`、`AgentCallDelegate.java:93`）。
- 与 C4 关系：五模块内的 Delayer 用户即 C4-003 本案（已立案）；本线索供 nop-ai/nop-message 审计单元核对「orTimeout 超时完成回调在 Delayer 线程触发 + 非 async 依赖动作随之在 Delayer 线程执行」的占用与串行化面，以及各单元修复时统一评估是否迁移到平台 `globalTimer`/专用调度器。
- 建议（跨单元）：建立平台级「定时/超时载体选型规约」（globalTimer vs Delayer vs 专用调度器），避免 11 个站点各自为政。

---

## 第 1 轮勘误表（本轮实测修正）

| # | 位置 | 第 1 轮表述 | 修正 | 影响 |
|---|---|---|---|---|
| 1 | C4-003 | 双层重试 (1+3)×(1+3)=16 次 | 加法语义：4+3=**7 次**（fallback 绕过 InvokeProcessor 单发） | 放大倍数下修，P1 维持 |
| 2 | C4-003 | 5xx 退避 2-3s/4-5s/8-9s、挂起 14-22s | 5xx 为 1-2s/2-3s/4-5s、挂起 **7-10s**；14-17s 属 429 无头默认退避 | 量化口径修正，90s（429 带头）上界不变 |
| 3 | C4-003 | 重试发起链含 XPL URL 求值 | 求值一次性在调用方线程（`invokeUrl:129`）；Delayer 线程执行 buildHttpRequest+fetchAsync 调起 | 结论方向不变，口径收窄 |
| 4 | C4-004 | quarkus 默认 worker ~128 | 实测 max=`max(8×cores,200)`、默认**无界队列**（quarkus-core 4.0.0.Beta1 字节码） | 占用模型修正为钉线+无界排队 |
| 5 | C4-004 | 远程刷新实现「仓库内无」 | `OAuthLoginServiceImpl.refreshToken` 在仓为阻塞远程实现 → 本轮立案 C4-007 | 降档理由撤销 |
| 6 | C4-003 F3 | （补强）上游 fetch 无请求超时 | `buildHttpRequest`/`tryFallbackUrl` 均未 setTimeout，时长仅受客户端部署配置约束 | F3 增补面 |

---

## 总计与终止信号判定

| 类别 | 数量 | 明细 |
|---|---|---|
| 本轮新增立案 | 1 | C4-007（P1×1，CL-016 终裁立案） |
| 本轮新增线索 | 1 | CL-022（Delayer 跨单元共存面） |
| 第 1 轮勘误 | 6 | 勘误表（C4-003×3、C4-004×2、F3 补强×1） |
| 同位点裁定 | 2 | CL-016→立案（C4-007）；C3 轮 CL-007→与 C4-005 关联不归并（新增 boolean 主失效面） |
| 全景表 | 9 行维持 | 抽验 6 行全部吻合；补漏扫描零新增载体；第 2 行参数勘误 4 |
| C4 维度累计 | 7 | P0×0，P1×2（C4-003、C4-007），P2×4（C4-001/002/004/005），P3×1（C4-006） |

**终止信号判定：不建议终止，建议第 3 轮（收口轮，限定范围）。**

依据深挖继续规则：本轮新增 P1×1（C4-007）→ 触发第 3 轮建议。同时如实记录正向终止信号：① 全景表 9 行经 6 行抽验 + 全量补漏扫描双重确认闭合，五模块内无未登记执行载体；② Delayer/xbiz-executor 两个族的全仓库 grep 已穷尽（1 + 0 站点），失效面扩展告罄；③ 本轮产出以勘误与补强为主（6 勘误 : 1 新立案），边际发现率显著下降。第 3 轮建议范围（不再全维度重扫）：1) C4-007 修复设计核对（异步刷新链 + 超时 + 聚合去抖三件的落地形态与回归面）；2) C4-003×C3-010 耦合对在修复计划中的落位核对（含本轮 7 次上限勘误进入计划文案）；3) C4-005+CL-007 合并修复的 boolean 主面覆盖核对；4) C4-002/C4-006 修复抽验。
