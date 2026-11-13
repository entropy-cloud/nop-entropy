# C6 深挖第 2 轮 — nop-ai-gateway（WI 3.3）

- **审计日期**: 2026-10-20
- **审计员**: C6 维度深挖第 2 轮盲审员（盲审纪律同第 1 轮；编号续接 C6-005 起）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` L26–118 + L243–262（C6 正文）+ L351–377（附录 A）

## 深挖范围与方法

按派单深挖面逐一执行：

1. **订阅资源的关闭序（FailoverStreamFlow cancel 后上游连接）**：三条取消路径逐语句核对——①输出侧 `cancel()` → `teardownCurrentAttempt`（L323-334）：先 `upSub.cancel()`（唤醒阻塞 submit）后 `token.cancel`（断 HTTP）再 `releaseAttempt`，序正确；②取消落在 `publisher.subscribe` 之前：`AttemptSubscriber.onSubscribe` L510-513 的 `cancelled || state != attempt` 守卫兜底 `subscription.cancel()`，正确；③取消落在 `callStream` 之后 `subscribe` 之前：token 钩子（delegate 侧）+ C1-008 已登记的残余窗口。**关键否定验证：窗口内失败重订阅路径（handleStreamFailure → startAttemptWithOptions）不清旧 `upSub`——复核确认旧 attempt 上游已因 onError 到达而 terminal，订阅无泄漏**。`BufferedStreamingPublisher.handleError` 重订阅分支显式 `oldSub.cancel()`（L313-316）——网关侧反而更完备。
2. **HTTP 响应体关闭**：模块内零直接 HTTP 响应体接触点（全根无 `close()/getBody/InputStream` 于响应对象；HTTP 交互全部经 nop-http `IHttpClient`/`FeishuClient.httpApi`）——**无立案对象，负验证**。
3. **登录态资源**：`ChannelLoginScanProcessor` 会话引导链的资源终态核对 → 立案 C6-005。bind ticket 校验（L93-126）只读消费；`ChannelBindResult`/`ChannelBindingInfo` 为值对象。
4. **会话文件的句柄**：模块内零文件句柄获取点（`ChannelSessionStoreImpl` 为纯 ORM；无 `File/InputStream/RandomAccessFile` 使用；基线 C6b 节为空的复核维持）——**负验证**。
5. 补充核对：`FeishuClient.start`（nop-integration-feishu，证据引用）失败路径残留面——`resolveEffectiveCredentials`/`getStreamEndpoint` 抛错时 `FeishuClient.credentials/handler` 与 `FeishuConnector.context/botOpenId` 已先行赋值而 `started=false`——复核确认无资源泄漏（连接尚未建立、后续 start 全量覆盖重赋值、`sendMessage` 被 `!started` 守卫拒绝），仅字段残留，**负验证不立案**。

## 新增发现

### [C6-005] loginByScan 会话引导无终态回收契约：会话已创建但 accessCode 签发失败/调用方放弃交换时，存量会话与 MFA challenge 无失效路径——publicAccess 端点可重复触发，回收完全依赖上游 TTL

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/login/ChannelLoginScanProcessor.java:142-151, 165-197`；入口 `login/ChannelLoginApiBizModel.java:146-168`
- **证据片段**:
  ```java
  return sessionBootstrap.createSessionForUserAsync(binding.getPlatformUserId(), resolvedLoginType)
          .thenApply(userContext -> {                       // L144 —— 会话已在 auth 侧创建
              if (userContext == null) { throw ...; }
              return buildResult(userContext);              // L150 → generateAccessCode 可抛
          });
  ...
  private ScanLoginResult buildResult(IUserContext userContext) {
      String accessCode = authTokenProvider.generateAccessCode(userContext, accessCodeExpireSeconds); // L190
  ```
- **严重程度**: P3
- **并发场景**: 认证服务的会话创建线程（`createSessionForUserAsync` 内部）与回调线程（thenApply 执行 `generateAccessCode`）；调用方为 `publicAccess=true` 的 GraphQL/REST 端点（ChannelLoginApiBizModel L146-148），无频控。
- **触发时序**: T1 `createSessionForUserAsync` 成功——完整会话（roles/tenant/dept/token）已写入 `IUserContextCache`（nop-auth 侧）→ T2 `buildResult` 内 `generateAccessCode` 抛错（签名服务异常/配置缺失）→ thenApply stage 异常完成 → **已创建的会话成为无 accessCode 引用的存量**，模块链路无任何 cancel/invalidate 调用；次级时序：T1' 正常签发后调用方（前端）放弃交换或交换前过期——同一存量形态。MFA 分支：`buildMfaResult`（L171-187）仅转发 challenge 参数，challenge 在 `MfaChallengeStore` 的存量同样依赖上游 TTL（用户扫码后放弃 MFA 是常规行为）。
- **现状**: 四步编排的③（会话创建）与④（accessCode 签发）之间无补偿；编排代码与 javadoc 均未声明会话/challenge 的回收责任归属（隐式依赖 auth 侧 TTL）；端点 publicAccess 且无频控，重复扫描-放弃行为可持续制造存量。
- **风险**: 有界（TTL 过期兜底）但无界的**触发速率**：公开端点 + 自动化重放可在 TTL 窗口内累积会话缓存条目与 challenge 记录（nop-auth 侧内存/存储压力）；模块视角为资源生命周期责任悬空（F8 编排侧不对称：只创建、无失效句柄）。
- **建议**: 编排契约补终态语义：`buildResult` 抛错路径与 MFA 分支登记"已创建会话/challenge 存量"指标（复用 `IFailoverMetrics` 模式），并在 owner doc 声明回收责任 = auth 侧 TTL；部署侧为 `loginByScan` 加频控（按 ticket/extId 维度）；若 auth 侧提供 session revoke 句柄，`thenApply` 异常路径补 try/catch 内失效调用。
- **失效面枚举（F3）**: 登录编排资源获取点全量清单——①`createSessionForUserAsync` 会话（**本条**）；②`generateAccessCode` accessCode（一次性、自带 TTL，无存量问题——豁免）；③`MfaChallengeStore` challenge（上游创建，本条并列登记面，回收责任同在 auth 侧）；④bind ticket（provider 侧自校验过期，FeishuBindProvider fail-closed——豁免）；⑤对照 FeishuClient.start 失败残留（字段级、无资源、覆盖式重赋值——负验证不立案）。
- **信心水平**: 确定（编排结构可静态证实；存量速率依赖调用方行为）
- **误报排除**: 非"错误处理两档策略"越界（审的是资源终态配对而非错误码）；非已声明边界——owner doc 与 javadoc 均未声明会话存量的回收契约与速率包络；TTL 兜底使后果有界故 P3，不上 P2。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现（cra-index WI 1.1–3.2 台账零同站点）。机制回码证实：thenApply L144-151 内 buildResult→generateAccessCode L190 可抛 → 会话存量无失效调用；buildMfaResult L171-187 仅转发 challenge 参数；端点 @Auth(publicAccess = true) @BizMutation loginByScan（ChannelLoginApiBizModel L146-148）且模块内无频控。触发面 live 判定成立——登录编排不依赖连接器接线（IChannelBindProvider/ISessionBootstrap 为独立 bean，ChannelLoginApi bean 已注册于 ai-gateway-defaults.beans.xml W4 节；启用 nop-auth-service 的部署即可达，缺席时 fail-fast NOT_ENABLED）。TTL 兜底有界 → P3 恰当；与 WI 3.1 MA-03 邻接不重复（auth 侧存量回收属上游），频控建议随工单携带

## 维度集外线索（overlay）

—— 本维度第 2 轮无新增集外线索。

深挖面负验证留痕（不立案）：①FailoverStreamFlow 三取消路径关闭序正确（窗口内重订阅不清旧 upSub 属信号已 terminal 的非泄漏）；②模块零 HTTP 响应体直接接触；③零文件句柄；④FeishuClient.start 失败残留为字段级覆盖式、无资源；⑤`BufferedStreamingPublisher` 重订阅显式 cancel 旧订阅（比本地形态更完备，无面）。

## 继续判定

新增 1 条（P3×1，无 P0/P1/P2）。判定：**终止信号**——①第 1 轮 P1（C6-004 会话映射半个成功）的主站点在第 2 轮定向复核中未再扩展出同量级新面；②四个派单深挖面中三个（关闭序/响应体/句柄）为纯负验证闭合，唯一新发现为低危契约缺口；③资源获取点配对总表（第 1 轮）经补充核对后无新增未配对获取点。建议 C6 维度不再派第 3 轮，转入 Phase 4 复核合并。

---
*审计员：C6 深挖第 2 轮盲审。发现计数：P0=0，P1=0，P2=0，P3=1；CL 追加=0。*

## 复核结论（WI 3.3 独立复核，合并批 C6+C7）

- **C6-005**：保留 P3——机制回码证实（thenApply 内 generateAccessCode 可抛 → 会话存量无失效调用；MFA 分支仅转发 challenge 参数）；触发面 live 判定成立（publicAccess 端点不依赖连接器接线，bean 已注册）；TTL 兜底有界，P3 恰当。逐条状态行见上方条目；维度判定表与最终保留项见 C6-resource-lifecycle.md「维度复核结论」。
- 第 2 轮终止信号判定维持（四个深挖面三负验证一低危新立，与本复核回码结论一致；深挖面⑤ FeishuClient.start 失败残留负验证抽验维持——字段级覆盖式重赋值、无资源）。
- C6 维度合计判定：保留×1（C6-005 P3）、降级×4（C6-004 P1→P2 条件化；C6-001/002/003 P2→P3 条件化）、驳回×0。
