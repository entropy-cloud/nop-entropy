# C4 线程池治理——请求热路径五模块 第 3 轮盲审（收口轮）报告

## 审计单元

| 项 | 值 |
|---|---|
| 审计单元 | nop-service-framework 请求热路径五模块（8 源码根，406 文件 / 42,748 行） |
| 仓库 / 分支 | `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`（HEAD 5994c18211，与前轮一致；live code 复核确认 C4-001~007 全部未修、行号未漂移） |
| 维度 | C4——线程池与执行器治理 |
| 轮次 | 第 3 轮收口（盲审） |
| 本轮输入 | 本维度前两轮：`C4-thread-pools.md`（C4-001~006、CL-010/011）、`C4-round2.md`（C4-007、CL-022、勘误 1-6）；任务下发时明示的跨维度线索描述：C3-010（无取消传播）、C3 轮 CL-007（DownloadHelper 删除异常静默吞）、C3-CL-006（downloadZip 临时文件依赖进程内延迟任务，重启遗留）；基线 `_tmp/cra-baseline-nop-service-framework-hotpath.txt`。全部结论以本轮对 live code 的独立实测为准 |
| 日期 | 2026-10-18 |

## 盲审纪律声明

本轮为盲审轮次：**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件，唯一例外为本维度前序轮次文件 `C4-thread-pools.md` 与 `C4-round2.md`。未读取同目录其他维度文件，未读取 `ai-dev/plans/`、`ai-dev/backlog/` 下任何文件（工作区中存在的 `ai-dev/plans/2470-*.md`、backlog roadmap 变更均未打开）。跨维度关联仅使用任务下发时明示的三条线索文字描述（C3-010、C3 轮 CL-007、C3-CL-006），其内容以本轮 live 实测独立验证为准。nop-api-core、nop-http-api、nop-kernel、quarkus 宿主代码仅作对照引用。

## 本轮目标（任务下发收口四问）

1. **①** C4-007 修复设计核对：`ILoginSpi.refreshTokenAsync`（L59 闲置 SPI）签名与调用面、接入点（`AuthHttpServerFilter:359` 调用链改造面）、Local JWT 实现无害对照。
2. **②** C4-003 × C3-010 耦合落位：×16→×7 勘误后的乘法/加法语义最终表述；取消传播修复耦合落位核对。
3. **③** C4-005 + CL-007 合并修复面核对：boolean 主面 + RuntimeException 次面 + 周期对账清单完整性；与 C3-CL-006 的三方关系。
4. **④** 快扫：auth/auth-core 之外第三处「载体线程无超时远程调用」（syncGet + httpClient 组合站点）。

---

## ① C4-007 修复设计核对执行结果

### 1.1 SPI 签名与调用面（实测，第 2 轮表述需勘误）

`ILoginSpi.refreshTokenAsync`（`nop-biz-auth-core/.../spi/ILoginSpi.java:58-59`）签名确认：

```java
@BizMutation
CompletionStage<LoginResult> refreshTokenAsync(RefreshTokenRequest request, IServiceContext context);
```

**调用面实测（三项关键事实，均推翻「该 SPI 可直接接入过滤器」的隐含前提）**：

1. **全仓唯一实现是 `LoginApiBizModel`**（nop-auth-service，`@BizModel("LoginApi")`，`LoginApiBizModel.java:76,153-157`），且其 `refreshTokenAsync` **不做任何令牌刷新**：
   ```java
   AuthToken token = loginService.parseRefreshToken(request.getRefreshToken());
   return loginService.getUserContextAsync(token, context.getRequestHeaders()).thenApply(this::buildLoginResult);
   ```
   语义为「凭 refresh token 作为凭证恢复缓存会话」：`buildLoginResult`（`:595-600`）直接返回缓存中的 `userContext.getAccessToken()`——**不调 IdP、不轮换 access/refresh token、不调用 `ILoginService.refreshToken`**。这是对外 API 面（GraphQL/REST mutation `LoginApi__refreshToken`，`publicAccess=true`），不是过滤器可复用的内部刷新通道（语义错位登记 → CL-023）。
2. **SSO 实现 `OAuthLoginServiceImpl` 根本不实现 `ILoginSpi`**（仅 `extends AbstractLoginService`，`OAuthLoginServiceImpl.java:49`）；过滤器注入的是 `ILoginService`（`AuthHttpServerFilter.java:82-85`）。SSO 装配条件 live 复核确认（`sso-defaults.beans.xml:15-21`，`nop.auth.sso.enabled` 条件切换 `nopLoginService` 为 `OAuthLoginServiceImpl`）。
3. **签名形状不匹配接入点**：SPI 入参 `(RefreshTokenRequest, IServiceContext)`、返回 `LoginResult`；过滤器现场持有 `(IUserContext, AuthToken)`、需要的是 accessToken 字符串（`:362` 设响应头 + `:364` 写 cookie）。

**勘误 7**：第 2 轮 C4-007 建议①「`OAuthLoginServiceImpl.refreshToken` 改造为基于 `refreshTokenAsync`/`fetchAsync` 的异步链路」中「基于 refreshTokenAsync」**不可按字面落地**——闲置 SPI 在错误的层（对外 API 面）、错误的 bean（LoginApiBizModel）、错误的语义（无轮换）。**正确修复面为**：

- 契约层：`ILoginService` 新增 `refreshTokenAsync(IUserContext, AuthToken): CompletionStage<String>`（默认方法回退 `completedFuture(refreshToken(...))` 或 sync 包装，保持既有实现兼容）；
- SSO 实现层：`OAuthLoginServiceImpl` override 该异步面——`:258` 的内部链 `httpClient.fetchAsync(req, null).thenApply(res -> {...})` **本来就是异步形状**，仅需剥离 `FutureHelper.syncGet` 外壳即可，改造代价极低；
- 本地 JWT 实现层：override 为 `FutureHelper.success(refreshToken(...))`——无害对照确认（见 1.2）；
- 第 2 轮建议②③④⑤（超时、按会话聚合、与 C4-004 专用执行器合并规划）维持有效。

### 1.2 Local JWT 无害对照（live 确认）

`LoginServiceImpl.refreshToken:953-961`：`authTokenProvider.generateAccessToken(...)` + `generateRefreshToken(...)` + `userContext.setAccessToken/setRefreshToken`——纯 CPU 本地重签、微秒级、无 I/O、无阻塞形态。同一同步契约下无害实现的对照成立，佐证 C4-007 缺陷核在**远程实现的 syncGet 阻塞形态**而非契约存在本身（第 2 轮判断维持）。

### 1.3 过滤器接入点改造面（实测可行）

`AuthHttpServerFilter.handleUserContext:354-383` 的改造面核验：

- 现场结构：`runOnContext` 内联 lambda（`:357-378`）→ 刷新（`:359`）→ 设头/cookie（`:362-365`）→ `next.get()`（`:374`）→ `FutureHelper.bindResult(promise, future)`（`:377`）。**`bindResult` 模式已就位**，lambda 改为发起异步链（`refreshTokenAsync(...).thenCompose(accessToken -> { 设头/cookie; return next.get(); })`）并在链尾 bind，即完成异步化，无需改动方法签名与 `future` 语义。
- **排序约束（修复设计必须保持）**：刷新必须以 `thenCompose` 串行领先于 `next.get()`——刷新内部对 `userContext` 的变异（`setAccessToken/setRefreshToken`）对下游可见性、以及响应头/cookie 的新鲜度都依赖该顺序；禁止刷新与下游并行。
- **共享实例耦合（新增设计注记）**：`AbstractUserContextCache.getUserContextAsync`（`AbstractUserContextCache.java:102-119`）把**缓存中同一个 `UserContextImpl` 实例**返回给同会话并发请求——并发半衰期刷新变异的是共享可变对象。第 2 轮建议④的「按会话聚合合并刷新」与该结构天然契合（同一实例即天然合并点），但变异必须保持在串行组合路径内。
- **超时 API 就位**：`HttpRequest.timeout(long)` / `setTimeout(long)`（`nop-http-api/.../client/HttpRequest.java:123-141`）存在，`newRefreshTokenRequest:270-285` 未使用——修复面只需一行 `req.timeout(...)`。
- MDC 注记：异步化后刷新完成回调漂移到 HTTP 客户端完成线程；现有代码 MDC 绑定（`:352`）与解绑（`:382`）本就不覆盖漂移后的下游线程（下游本就在 `getUserContextAsync` 完成线程驱动），不构成新增危害类别。

---

## ② C4-003 × C3-010 耦合落位执行结果

### 2.1 乘法/加法语义最终表述（live 复核）

`InvokeProcessor.java` 与 `AiFailoverGatewayInterceptor.java` live 未修，第 2 轮勘误 1 的加法语义经逐行复核**确认并最终表述**为：

> **单请求上游调用上限 = 4 + min(|fallbackUrls|, 3) = 7 次，为加法叠加，不存在乘法。**
> 内层段（`InvokeProcessor.invokeUrlWithRetry:139-196`）：主 URL 最多 1+3=4 次 fetch，429 延迟每次被 `MAX_RETRY_DELAY_MS=30_000` 钳制（`:208,235-237`），429 满额挂起 ≈90s；5xx 退避 1s/2s/4s（±1s jitter），挂起 7-10s。
> 外层段（`AiFailoverGatewayInterceptor.tryInvokeWithFallback:79-111` / `tryFallbackUrl:113-145`）：仅在主链耗尽（内层抛 `ERR_GATEWAY_UPSTREAM_429/FAILED`）后启动，**每个 fallback URL 至多 1 次调用**（429/5xx 按序推进到下一 URL，`nextFallbackIndex < maxFallbackAttempts()` 界定），fallback 之间无延迟、无内层重试。
> 两段是**串行相接的两个独立段**，fallback 直连 `httpClient.fetchAsync` 完全绕过 InvokeProcessor——乘法（嵌套重试×嵌套重试）在该实现中不存在。

### 2.2 取消传播 live 复核与耦合落位

- live 复核：三个链路文件（`InvokeProcessor` / `AiFailoverGatewayInterceptor` / `GatewayHttpFilter`）中 cancel/abort/timeout 记号**仅一处**——`GatewayHttpFilter:240` 流式路径 `streamingResponse.abort()`；`delayedFuture:198-202`、`buildHttpRequest:242-256`（无 timeout）、fallback 链（无 timeout）全部维持原状。C4-003 与 C3-010 均**未修**。
- **落位核对的新增硬事实**：`IHttpServerContext`（nop-http-api，服务端抽象）**没有任何断连观测 API**（`abort|disconnect|cancel|isClosed|closed` grep 零命中）。流式路径的取消之所以可行，仅因宿主提供的 `sendStreamingResponse` future 在断连时异常完成（`:238-241` 的 `whenComplete` 挂钩）；非流式 invoke 链返回的 `CompletionStage` 是响应发送器，不携带客户端生命周期语义。
- **落位结论**：
  1. C3-010 的「retry 链观测断连取消」修复在网关层**存在硬前置缺失**——当前抽象层无原语可挂，必须先在 nop-http-api 增加断连观测原语（如 `CompletionStage<Void> onClientDisconnect()`，由宿主实现填充），登记跨维度前置依赖 → CL-025。
  2. 耦合对裁定维持并具体化：**只修载体不修取消**（delayedFuture→globalTimer）→ 幽灵重试照旧烧 7 次上游调用；**只修取消不修载体** → 在原语落地前不可实现。C4-003 与 C3-010 必须同批落地（同一修复单元或 C3-010 原语先行），修复计划应将两者标记为互为前置。
  3. 取消检查原语必须一个原语三处消费：内层重试链（C4-003 F3①②）、failover fallback 链（F3③）、流式重订阅（F3④），避免三处各自造轮子。
  4. 修复时同步补 `buildHttpRequest:242-256` / `tryFallbackUrl:113-145` 的 `req.timeout(...)`（API 已就位，见 1.3）。

---

## ③ C4-005 + CL-007 合并修复面核对执行结果

### 3.1 live 复核与修复清单增补

`DownloadHelper.java:39-42` live 未修：定时任务 `() -> { resource.delete(); return null; }`，boolean 忽略、无 try/catch、返回的 ScheduledFuture 丢弃。`FileResource.delete():86-88` → `java.io.File.delete()` 返回 boolean——**boolean 主面确认**。本轮新增一处同族面：

- **`:49` catch 路径的 `resource.delete()` 同样忽略 boolean 返回值**（zip 构建失败时的即时清理，失败同样无感知）——并入修复清单（同族，代价一行）。

全仓检索确认**无任何平台级 temp 对账机制**（仅 `deleteOnExit` JVM 钩子散布于资源实现，`DownloadHelper` 未使用；该钩子仅覆盖正常退出且列表常驻，不构成修复项）。

### 3.2 三方关系闭合（C4-005 + C3 轮 CL-007 + C3-CL-006）

任务下发明示的 C3-CL-006（downloadZip 临时文件依赖进程内延迟任务，重启遗留）经 live 实测成立：清理任务挂在 `GlobalExecutors.globalTimer()` 的内存延迟队列，进程重启即消失，`temp:/download` 下既有文件永久遗留（`ResourceHelper.getTempResource("download"):225-228` → `temp:/download/{yyyyMMdd}/{uuid}`，按天分目录）。

**最终修复清单（一个修复单元闭环三个已立案项）**：

| # | 修复项 | 覆盖的立案 | 校验要点 |
|---|---|---|---|
| 1 | 检查 `delete()` boolean 返回值 + 失败日志/指标 | C4-005 主面、C3 轮 CL-007 | 仅修异常面修不到主路径（第 2 轮裁定维持） |
| 2 | try/catch 包装 + 失败日志 | 次级面（SecurityException + 调度器 Callable 吞异常语义） | |
| 3 | `:49` catch 路径 delete 同样检查返回值 | 本轮新增面 | |
| 4 | 平台级周期对账：扫描 `temp:/download` **整棵按天分区树**（非按已知文件名），按 mtime 清理超期文件 | C4-005 兜底自愈 + **C3-CL-006 重启遗留** | 阈值必须 > 最大 `waitMinutes`（避免与在途下载窗口竞态）；扫描范围必须覆盖 `download/{day}/{uuid}` 全子树（否则按天目录残留漏扫） |

**裁定**：修复项 4 是同时关闭 C4-005「无自愈」与 C3-CL-006「重启遗留」的唯一机制——三个立案项必须绑定到同一修复单元，不可按 C3-CL-006 单独立项重复建设。附注：`downloadZip` 全仓 **0 生产调用方**（grep 确认，公共 helper、潜伏面），「直接移除死代码」是可记录的产品范围选项（非审计裁定）。

---

## ④ 快扫：第三处「载体线程无超时远程调用」执行结果

### 4.1 五模块内：零新增（问题字面答案：否）

- `syncGet × httpClient/fetchAsync` 组合站点全仓生产代码 grep（-A2 上下文过滤）：**仅 1 处 = C4-007 本体**（`OAuthLoginServiceImpl.java:258`）。
- 五模块 8 源码根内全部 `syncGet` 站点 7 处，逐一分类：`IFileStore.java:20`（同步门面默认方法）、`IGraphQLEngine.java:101,110`（同步入口门面）、`CrudBizModel.java:2265,2280`（同步 CRUD 门面）、`GraphQLExecutor.java:618`（内部 future 求值）、`ILoginService.java:170`（第 2 轮 F3④ 已登记、模块内无调用方）——**无一为过滤器/请求载体线程上的无超时远程 HTTP 调用**。

### 4.2 同步 fetch 族扫描：发现 C4-008（auth 内漏网兄弟站点）

扩展扫描 `IHttpClient.fetch(...)` 同步默认方法（= `FutureHelper.syncGet(fetchAsync)`，`IHttpClient.java:23-25`）全仓调用面，命中 8 处。其中 **`JWKPublicKeyLocator.java:112` 位于认证热路径且被前两轮 F3 枚举遗漏**——与 C4-007 同模块（nop-auth-sso）、同载体（认证过滤器 worker 线程），缺陷核不同（monitor 串行化 + 无超时同步拉取），立案为本轮唯一新发现 **C4-008**（见下）。

### 4.3 审计单元外同步 fetch 族（登记 CL-024）

其余 7 处均在五模块之外：`AddAccessTokenHttpClientEnhancer.java:131`（**在 `synchronized(authTokens)` 双检锁内做无超时 `client.fetch` 拉取 OAuth token**——外发请求载体线程形态，与 C4-008 同构，移交相应审计单元）、`OAuthTokenClient.java:91`（nop-credential-service）、`VaultCredentialKeyProvider.java:319`（nop-credential-kms-vault）、`HttpRequestExecutor.java:178`（nop-ai-toolkit，agent 工具执行载体）、`CheckpointActionDispatcher.java:261`（nop-metadata-service）、`HttpPluginResourceResolver.java:165,241`（nop-plugin-manager，插件解析线程）。

---

## 新增发现

### [nop-service-framework-hotpath-C4-008] JWKS 公钥定位器在 synchronized 块内执行无超时同步 IdP HTTP 拉取——认证过滤器载体线程每小时确定性阻塞一次，并发缓存未命中请求在 monitor 上排队；失败静默吞掉后进入 60 秒 401 窗口（前两轮 F3 枚举遗漏的同模块兄弟站点）

- **文件**: `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/jwk/JWKPublicKeyLocator.java:60-84, 87-94, 97-103, 106-126`；触发链 `OAuthLoginServiceImpl.java:238-245`（parseAuthToken 的 keyLocator lambda）、`:79-84`（init 创建单例 locator）；载体 `AuthHttpServerFilter.java:155, 415-432`；内联调用点 `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/jwt/JwtHelper.java:83,127`；参数 `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/SsoConfig.java:29-30`
- **证据片段**:
  ```java
  // JWKPublicKeyLocator.getPublicKey:60-84
  PublicKey publicKey = lookupCachedKey(publicKeyCacheTtl, currentTime, kid);   // TTL 过期→null
  if (publicKey != null) return publicKey;
  synchronized (this) {                                                          // 全部署单实例 monitor
      if (currentTime > lastRequestTime + minTimeBetweenRequests * 1000L) {
          sendRequest();                // ← 无超时同步 HTTP，锁内执行
          lastRequestTime = currentTime;
      }
      return lookupCachedKey(publicKeyCacheTtl, currentTime, kid);
  }
  ...
  // sendRequest:112 —— 无超时同步拉取（IHttpClient.fetch 默认 = syncGet(fetchAsync)）
  JSONWebKeySet jwks = httpClient.fetch(new HttpRequest().url(config.getFullUrl(config.getJwksUrl())), null)
          .getBodyAsBean(JSONWebKeySet.class);
  ...
  } catch (Exception e) {
      log.error("Error when sending request to retrieve realm keys", e);         // 失败静默，currentKeys 不变
  }
  ```
- **严重程度**: P2（限定 SSO 部署形态；触发为每小时确定性 + 事件性（冷启动/KID 轮换）；故障条件下升级路径明确但非稳态常态，故不判 P1）
- **并发场景**: SSO 部署下每个认证请求经 `AuthHttpServerFilter.parseAuthToken:424`（在 `executeBlocking` worker 载体上，即 C4-004 的无隔离全局 worker 池）→ `JwtHelper.parseToken:127` 内联 `keyLocator.apply(kid)` → `getPublicKey`。缓存参数：`publicKeyCacheTtl=3600`s、`minTimeBetweenJwksRequests=60`s（SsoConfig 默认）——**每小时**缓存 TTL 过期后首个未命中请求在锁内执行无超时 JWKS 拉取；启动后首个请求（`lastRequestTime=0`）与 IdP KID 轮换为事件性触发。
- **触发时序**: T1 缓存过期后请求 R1 携带任一 kid 到达 → `lookupCachedKey` 返回 null → R1 的 worker 载体线程进入 `synchronized(this)` 并阻塞于无超时 JWKS HTTP GET（正常 50-500ms）→ T2 并发到达的未命中请求 R2..Rn（各持不同 kid 或同 kid）全部在 monitor 上排队，串行等待 R1 的完整 RTT → T3a 拉取成功：队列逐个快速取钥放行（串行化代价=一次 RTT + n 次锁往返）；T3b 拉取失败（异常内吞，`currentKeys` 不更新，但 `lastRequestTime` 照常推进）→ 队列中全部请求 `lookupCachedKey` 得 null → **认证 401，且 60 秒速率窗内不再重试**——IdP 抖动即产生分钟级认证拒绝窗口；T3c IdP 不可达（SYN 黑洞、无部署级 connect/readTimeout 配置时 JDK 客户端无界）→ R1 载体线程无界钉住，monitor 上请求堆积与 C4-007 同型，与 C4-007 的刷新阻塞叠加在同一无隔离 worker 池。
- **现状**: 前两轮 C4-007 的 F3 枚举（refreshToken、newRefreshTokenRequest、getUserContext 同步包装、Redis store 族）均未覆盖本站点；本轮 ④ 快扫以 `IHttpClient.fetch` 同步默认方法族全仓扫描补出。锁结构本身是正确的单飞意图（防缓存击穿），缺陷在**锁内执行阻塞远程 I/O + 请求无超时 + 失败静默且占用速率窗**三件叠加；缓存命中快路径（绝大多数请求）不受影响。
- **风险**: 稳态下每小时一次的载体线程阻塞远程调用（与 C4-004 无隔离载体叠加）；IdP 故障时认证可用性与 IdP 可用性耦合（fail-closed 无重试协调，401 窗口）；KID 轮换瞬间的全量未命中 + monitor 串行化；停机/降级无统一取消点。
- **建议**: ① 锁内阻塞改异步单飞：`fetchAsync` + 在飞 future 登记表（同 kid 并发未命中共享同一次在途拉取），monitor 仅保护登记表；② 显式超时 `new HttpRequest().url(...).timeout(5_000)`（API 就位）；③ 失败保留旧钥宽限服务（grace window）+ 短退避重试，不吞入速率窗（区分「KID 未知需刷新」与「IdP 不可达」——现 `lookupCachedKey:97-103` 把 TTL 过期与 kid 缺失混同为 null，修复时一并拆分）；④ 刷新失败指标/告警；⑤ 与 C4-007 修复同批规划（同一 IdP 依赖、同一载体、同一部署条件）。
- **失效面枚举**（F3）: ① `getPublicKey` synchronized+锁内 sendRequest（:73-83）——**本案本体未修**；② `sendRequest` 无超时 fetch（:112）——**未修**；③ 异常静默吞 + 失败占用速率窗（:123-125）——**未修**（F9 面，与 C3 轮 CL-007 同族形态）；④ `reset()`（:87-94）同款锁内同步拉取——仓库内未检索到生产调用方（潜伏面），**未修**；⑤ `lookupCachedKey` TTL 过期/kid 缺失二义返回 null（:97-103）——**未修**（修复设计输入）；⑥ 同族对照（单元外）：`AddAccessTokenHttpClientEnhancer` 双检锁内无超时同步 fetch（CL-024 首站点）——移交相应审计单元。
- **信心水平**: 确定（锁结构、无超时、参数默认值、载体链、失败路径全部为代码事实；monitor 排队与 401 窗口为必然演化）
- **误报排除**: 非缓存击穿理论推演——TTL=3600s 过期后的未命中是每小时确定性事件而非概率场景；非 C1 重复立案——锁的正确性意图成立，缺陷核是锁内阻塞 I/O 的载体选择（C4 族）；非 C4-007 重复——不同文件、不同缺陷核（monitor 串行化 vs syncGet 契约）、已双向互引；缓存命中快路径已明示不受影响，非全量误伤。
- **复核状态**: 复核通过，**维持 P2**，附复核勘误 9/10（2026-10-18 维度复核）——①结构证据逐项亲核成立：`getPublicKey:60-84` synchronized 内 `sendRequest`、`sendRequest:112` 经 `IHttpClient.fetch` 默认方法（= `FutureHelper.syncGet(fetchAsync)`，`IHttpClient.java:21-23` 实核）同步拉取、`SsoConfig.java:29-30` 默认 ttl=3600s/rate-window=60s、`JwtHelper.java:83,127` 内联 `keyLocator.apply(kid)` 于认证过滤器载体线程、失败静默吞（:123-125）且 `lastRequestTime` 照常推进占用速率窗。②**勘误 10（401 窗口链收窄）**：T3b「拉取失败 → 队列中全部请求 `lookupCachedKey` 得 null → 认证 401」不成立——`lookupCachedKey:97-103` 的 TTL 门以 `lastRequestTime` 计算，失败后 `lastRequestTime` 已推进（:77），门判定为未过期、`currentKeys.get(kid)` 返回**旧钥**（stale-serve）：稳定态每小时刷新失败对已知 kid **不产生 401**；401 窗口仅存在于 (a) 冷启动首次拉取失败（`currentKeys` 为空）、(b) KID 轮换引入新 kid 且窗口内拉取失败。该 stale-serve 是（无意的）韧性行为，建议③修复设计时应保留该语义而非破坏。③**勘误 9（无界→有界）**：T3c「IdP 不可达……无界钉住」应更正为默认部署有界 ≈30s（`HttpClientConfig.java:35-37` 默认 readTimeout=30s/connectTimeout=10s）。④存活危害维持 P2：锁内阻塞远程 I/O + 无 per-request 超时 + monitor 串行化（每小时 TTL 确定性事件 + 冷启动/轮换事件驱动），IdP 慢响应时与 C4-007 同型钉线级联于同一无隔离 worker 池；「每小时确定性阻塞」表述成立（每 locator 实例每小时 1 次锁内远程调用 + 并发未命中排队）。现状=未修。

---

## 维度集外线索

### [nop-service-framework-hotpath-CL-023]（维度集外线索，不立案展开）`LoginApi__refreshToken` 语义与接口文档背离：名为刷新、实为缓存会话恢复，不轮换任何令牌；SSO 部署下以 IdP refresh token 走本地 JWT 校验链

- 形态归属：认证 API 语义/安全维度（非 C4）。
- 证据：`ILoginSpi.refreshTokenAsync` javadoc「accessToken失效的时候可以用refreshToken去重新获取」（`ILoginSpi.java:53-59`）；唯一实现 `LoginApiBizModel.refreshTokenAsync:153-157` 仅 `parseRefreshToken → getUserContextAsync → buildLoginResult`，返回**缓存中的现有 accessToken**（`buildLoginResult:596-600`），从不生成新令牌、从不调 IdP。SSO 部署下 `parseRefreshToken` 回退 `parseAuthToken`（`ILoginService.java:112-114` → `OAuthLoginServiceImpl.parseAuthToken:238-245`），即用**access-token 校验链（JWK 定位、issuer/audience）去校验 IdP 的 refresh token**——Keycloak 形态下 refresh token 常为非 JWT 串或 typ 不符，直接解析失败。
- 与 C4 关系：无载体/池治理成分，不立案；为 C4-007 修复设计核对（①）的产出事实，修复计划不得把该端点当作刷新通道复用。

### [nop-service-framework-hotpath-CL-024]（维度集外线索，不立案展开）审计单元外的同步 fetch 族：7 处 `IHttpClient.fetch`（默认 syncGet 包裹 fetchAsync）调用站点，其一在双检锁内

- 形态归属：C4 同族（载体线程阻塞远程调用），主体在五模块之外。
- 证据（4.3 节清单）：`AddAccessTokenHttpClientEnhancer.java:131`（**`synchronized(authTokens)` 双检锁内 `client.fetch` 无超时拉 OAuth client-credentials token**——外发请求载体线程阻塞 + 锁串行化，与 C4-008 同构，风险最高的移交站点）；`OAuthTokenClient.java:91`；`VaultCredentialKeyProvider.java:319`；`HttpRequestExecutor.java:178`（AI 工具执行载体）；`CheckpointActionDispatcher.java:261`；`HttpPluginResourceResolver.java:165,241`（插件解析，启动/刷新期）。
- 与 C4 关系：供相应审计单元按 C4-007/C4-008 的「无超时 + 载体身份 + 锁内阻塞」三要素核对；超时 API `HttpRequest.timeout(long)` 已就位，修复代价一致。

### [nop-service-framework-hotpath-CL-025]（维度集外线索，不立案展开）`IHttpServerContext` 无断连观测原语——C3-010「retry 链观测断连取消」修复的硬平台前置

- 形态归属：C3（取消传播）修复依赖面；nop-http-api（`nop-network/nop-http/nop-http-api/.../server/IHttpServerContext.java`）为单元外公共 API（Protected Area 对照引用）。
- 证据：接口内 `abort|disconnect|cancel|isClosed|closed` grep 零命中；流式路径取消仅依赖宿主提供的 `sendStreamingResponse` future 断连异常完成（`GatewayHttpFilter:238-241`），非流式 invoke 链无任何可挂接的客户端生命周期句柄。
- 与 C4 关系：C4-003×C3-010 耦合对的落位结论（②）：C3-010 修复须先在 nop-http-api 增加断连观测原语（宿主实现填充），C4-003 的重试/failover/流式重订阅三处消费同一原语；修复计划编排时 C3-010 原语项先行。

---

## 勘误表（本轮新增）

| # | 位置 | 前轮表述 | 修正 | 影响 |
|---|---|---|---|---|
| 7 | C4-007 建议①（第 2 轮） | 「改造为基于 refreshTokenAsync/fetchAsync 的异步链路」 | `ILoginSpi.refreshTokenAsync` 是对外 API 面（唯一实现 LoginApiBizModel），**不做令牌轮换**、且 SSO 实现不实现该 SPI——不可作为修复基础；正确面 = `ILoginService` 契约异步面 + `OAuthLoginServiceImpl` 剥离 `:258` syncGet 外壳（内部链本为异步形状） | 修复设计路径更正；建议②③④⑤ 维持 |

（第 2 轮勘误 1-6 维持有效；②③ 的复核未产生新数值勘误——×7、90s、5xx 退避序列、quarkus 池参数均逐行复核吻合。）

---

## 总计与终止信号判定

| 类别 | 数量 | 明细 |
|---|---|---|
| 本轮新增立案 | 1 | C4-008（P2×1，JWKPublicKeyLocator） |
| 本轮新增线索 | 3 | CL-023（LoginApi__refreshToken 语义错位）、CL-024（单元外同步 fetch 族 7 站点）、CL-025（IHttpServerContext 断连原语缺失，C3-010 修复前置） |
| 本轮勘误 | 1 | 勘误 7（C4-007 修复设计路径更正） |
| 修复清单裁定 | 2 | C4-005+CL-007+C3-CL-006 三方绑定同一修复单元（4 项清单，新增 ：49 面与对账阈值/范围约束）；C4-003×C3-010 互为前置 + 原语三处消费 |
| C4 维度累计 | 8 | P0×0，P1×2（C4-003、C4-007），P2×5（C4-001/002/004/005/008），P3×1（C4-006） |
| CL 累计（本维度各轮） | 6 | CL-010、CL-011、CL-022、CL-023、CL-024、CL-025 |

**终止信号判定：建议终止——C4 维度深挖在第 3 轮收口。**

四问全部闭合且无新 P0/P1，满足收口轮终止条件。正向信号如实记录：

1. **四问闭合质量**：① 修复设计核对产出可执行路径（勘误 7 更正后：契约异步面 + syncGet 剥离 + 一行超时 + 串行组合约束，全部 API 就位）；② 加法语义最终表述落定（4+3=7，逐行复核无新数值勘误），耦合对落位具体化为「原语先行 + 同批落地 + 三处消费」；③ 修复清单完整且三方（C4-005/C3 轮 CL-007/C3-CL-006）绑定同一修复单元，含本轮新增的 ：49 面与对账阈值/范围约束；④ 问题字面答案为否（五模块内零新增），快扫的扩展面（同步 fetch 族）已穷尽至 8 站点并全部定性。
2. **族枚举告罄**：`syncGet×fetchAsync` 组合（1 站点）、同步 `fetch` 族（8 站点）、五模块 `syncGet`（7 站点全分类）、`globalTimer` 五模块用户（2 站点，与前轮一致）四个族的全仓 grep 均已穷尽，无未定性站点。
3. **边际发现收敛**：本轮唯一新立案 C4-008 为 C4-007 同模块同部署条件的**族内补全**（快扫方法学差异所致），非新缺陷族；三线索均为移交/前置登记。8 立案的缺陷族谱系（默认装配缺失、共享 timer 载体、全局 Delayer、无隔离执行载体、清理失败静默、停机语义、同步阻塞远程调用 ×2）已无未探索分支。

若复核轮需要：修复落地后的抽验方向仅剩 C4-002/C4-006 的修复验证（第 2 轮已列为修复后事项，非本轮扫描范围）。
