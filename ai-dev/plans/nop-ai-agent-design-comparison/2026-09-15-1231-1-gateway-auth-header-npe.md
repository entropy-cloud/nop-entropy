---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-ROUND4-GATEWAY-AUTH-NPE
group: "2026-09-15-1231"
verify: [test]
---

# P2 round-4 gateway 认证头下沉 NPE 修复（sinkAuthHeader / GatewayStreamingRetryCallback 无守卫 config 解引用）

## Current Baseline

- 来源：deep-audit round 4 登记、roadmap `## Follow-up Backlog` 唯一未勾选项（`source: deep-audit round 4`），live repo 复核（2026-09-15）：
  1. **`AiGatewayFailoverInterceptor.sinkAuthHeader` 无守卫解引用**：`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/AiGatewayFailoverInterceptor.java:496-513`——`sinkAuthHeader` 内 `LlmModel config = LlmConfigHelper.loadConfig(candidate.getProvider())`（:497）后，:505 `LlmDialectFactory.getDialect(config.getApiStyle())` 与 :507 `config.getApiKeyHeader()` 直接解引用；`loadConfig`（LlmConfigHelper.java:70-73）对不存在 `/nop/ai/llm/{provider}.llm.xml` 的 provider 返回 null（ResourceComponentManager 未命中），同文件 `sinkCandidate`（:480-481）已用 `config != null ? config.getApiStyle() : null` 守卫——同一候选下沉链两处姿态不一致；路由候选 provider 无 `.llm.xml` 配置时 onRequest 路径（:193 sinkCandidate → :487 sinkAuthHeader）抛裸 NPE。
  2. **`GatewayStreamingRetryCallback.buildHttpRequest` 同型 NPE**：`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/GatewayStreamingRetryCallback.java:104-131`——:107 `loadConfig` 后 :113 `config.getApiStyle()`、:116 `config.getBaseUrl()`、:120 `config.getChatUrl()`、:128 `config.getApiKeyHeader()` 全部直接解引用；流式窗口内失败重执行路径（retry :91 sinkCandidate → :97 buildHttpRequest）对无配置候选同样抛裸 NPE。
  3. 同类第三站点 `ChatServiceFailoverAdapter.classifyStreamError`（:508-512）的 `config.getApiStyle()`（:511）位于 `try { ... } catch (Exception ignored)`（:516-518）内——NPE 被吞后回退 `LlmErrorClassifier`，不崩溃但属静默回退，须 Proof 复核其姿态是否与本计划裁定一致（不在崩溃面）。
  4. 归属模块：nop-ai-gateway（failover 包）。
  5. 验证面：运行时行为修复，涉及 `./mvnw test -pl nop-ai/nop-ai-gateway -am`（既有 TestAiGatewayFailoverInterceptorNonStreaming / TestAiGatewayFailoverInterceptorStreaming / TestChatServiceFailoverAdapter* 套件）；owner doc `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（W6 auth-header 下沉契约）如有相关描述需同步。

## Goals

- `sinkAuthHeader` 与 `GatewayStreamingRetryCallback.buildHttpRequest` 对 `config == null`（provider 无 `.llm.xml`）不再抛裸 NPE；行为按裁定落地（推荐：与文件内既有"无可用 key = 保持客户端头（零回归）"javadoc 语义一致的容错/跳过 + 显式日志，或 fail-loud，以裁定为准）。
- 与 `sinkCandidate`（:480-481）的既有 null 守卫姿态一致，消除同链两处姿态矛盾。
- 每处修复配套回归测试：无配置候选经公开入口（onRequest / retry 回调）不 NPE，且行为可观测（断言具体行为而非仅"不报错"）。
- owner doc（`02-account-failover-requirement.md`，按 live 检索）同步或显式 `No owner-doc update required`；`node ai-dev/tools/check-doc-links.mjs --strict` 0 error。

## Non-Goals

- 不改 `LlmConfigHelper.loadConfig` / `LlmDialectFactory` 契约（getDialect(null) 默认 openai 语义不动）；不改 auth-header 下沉的整体设计（B-9/B-14 语义）。
- 不处置 roadmap 其余 successor 候选（MFA 错误码上移 nop-auth-api 等，另开计划）。
- 不运行 mvn 全量构建。

## Phase 1 — sinkAuthHeader config null 守卫 + 回归

Status: planned

Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/AiGatewayFailoverInterceptor.java`、`nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/TestAiGatewayFailoverInterceptorNonStreaming.java` 等

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` config==null 语义裁定：(A) 容错跳过——`config == null` 时不下沉认证头（保持客户端头，与文件内 javadoc"无可用 key = 保持客户端头（零回归——客户端自持认证基线）"语义一致）+ WARN 日志（可观测，非静默）；或 (B) fail-loud——抛 `NopAiCoreErrors`/`NopAiGatewayErrors` 显式错误码。记录理由与备选（推荐 (A)：无配置时既无 apiStyle 也无 apiKeyHeader 信息，下沉无从谈起，保持客户端头是唯一诚实行为；:480-481 已确立 config==null 容错先例）。
- [x] `Fix` 按裁定落地 `sinkAuthHeader`：:505/:507 解引用前按裁定守卫（(A) 则 config==null 早退 + WARN；(B) 则抛显式错误码），:497 后不再直接解引用。
- [x] `Fix` 回归测试：候选 provider 无 `.llm.xml`（如不存在 provider 名）→ onRequest 公开入口（sinkCandidate → sinkAuthHeader）不 NPE；断言具体行为（(A) 客户端头保留 + WARN 日志存在；(B) 显式错误码 getErrorCode 断言）；对照有配置候选认证头正常下沉（零回归）。
- [x] `Proof` 复核：`sinkAuthHeader` 全部调用点清单（`sinkCandidate` :487 内部唯一调用；`sinkCandidate` 入口 onRequest :193 / invokeNonStreamingAttempt :264 / GatewayStreamingRetryCallback.retry :91），确认守卫在全部路径生效；与 `sinkCandidate` :480-481 守卫姿态一致性核对。

Exit Criteria:

- [x] `sinkAuthHeader` 对 config==null 不再抛裸 NPE（回归测试经公开入口断言）。
- [x] 行为与裁定一致且可观测（断言具体行为，非仅"不报错"）。
- [x] **端到端验证**：onRequest（候选选择 → sinkCandidate → sinkAuthHeader）→ 下游请求构建完整链路对无配置候选不 NPE（测试断言链路连通）。
- [x] **接线验证**：守卫确实在 `sinkAuthHeader` 运行时路径上被执行（非孤立方法测试）。
- [x] **无静默跳过**：config==null 处置有显式日志或错误码（不裸 return 无痕迹）。
- [x] 有配置候选认证头下沉零回归（既有断言或新增对照例）。
- [x] owner doc 更新：`02-account-failover-requirement.md` auth-header 下沉段如有 config==null 语义描述则同步；否则显式写 `No owner-doc update required`。
- [x] `./mvnw test -pl nop-ai/nop-ai-gateway -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 2 — GatewayStreamingRetryCallback.buildHttpRequest config null 守卫 + 回归

Status: planned

Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/GatewayStreamingRetryCallback.java`、`nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/TestAiGatewayFailoverInterceptorStreaming.java` 等

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` config==null 语义裁定：(A) 容错回退——`config == null` 时无法重建 URL/headers，按 retry 回调既有"null = 不重试（断流报错）"语义返回 null + WARN 日志（与 NON_TRANSIENT/预算耗尽分支同形）；或 (B) fail-loud——抛显式错误码（注意：回调抛异常会中断缓冲层既有断流路径，需评估传播面）。记录理由与备选（推荐 (A)：与类 javadoc"null = 不重试，断流报错"契约一致，且无配置候选本就无法构造请求，重试无意义）。
- [x] `Fix` 按裁定落地 `buildHttpRequest`：:107 `loadConfig` 后、:113/:116/:120/:128 解引用前按裁定守卫（(A) 则 WARN + return null；(B) 则抛显式错误码）。
- [x] `Fix` 回归测试：窗口内失败 + 无配置重选候选 → retry 回调公开入口不 NPE；断言具体行为（(A) 返回 null 断流 + WARN 日志；(B) 错误码传播）；对照有配置候选重试重建请求成功（零回归）。
- [x] `Proof` 复核：`retry` 全部路径（CACHE_STATE_LOST 原地重发 :56-68 / 切换类 :73-98）对无配置候选的行为一致性；与 `sinkCandidate` 守卫姿态一致性核对；`ChatServiceFailoverAdapter.classifyStreamError`（:508-512）静默回退姿态记录为本计划裁定对照（若裁定 (A) 则与 catch-ignored 回退姿态一致，登记说明；若裁定 (B) 则评估是否需顺带收敛该站点——仅登记，不在本计划扩展范围）。

Exit Criteria:

- [x] `buildHttpRequest` 对 config==null 不再抛裸 NPE（回归测试经 retry 公开入口断言）。
- [x] 行为与裁定一致且可观测（断言具体行为，非仅"不报错"）。
- [x] **端到端验证**：窗口内失败 → retry 回调（分类 → 候选重选 → sinkCandidate → buildHttpRequest）→ 断流/重建完整链路对无配置候选不 NPE（测试断言链路连通）。
- [x] **接线验证**：守卫确实在 `buildHttpRequest` 运行时路径上被执行（非孤立方法测试）。
- [x] **无静默跳过**：config==null 处置有显式日志或错误码（不裸 return 无痕迹）。
- [x] 有配置候选重试重建请求零回归（既有断言或新增对照例）。
- [x] owner doc 更新：`02-account-failover-requirement.md` 流式重试段如有 config==null 语义描述则同步；否则显式写 `No owner-doc update required`。
- [x] `./mvnw test -pl nop-ai/nop-ai-gateway -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Closure Gates

> 关闭条件记录（01-file-ledger §4.3 消解为 §5.2 完成公式派生）：`sinkAuthHeader` 与 `buildHttpRequest` 两处 config==null 守卫落地 + 回归测试守护（具体行为断言 + 有配置候选零回归）+ 裁定记录 + owner doc 同步后，由独立子 agent closure-audit 完成并写入 `## Closure`；本 section 不保留可写 checkbox，机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-15-1231-1-gateway-auth-header-npe-1-3c8f1a92 to ses_f5c9daf20ffe4J7hcL1wD2BK2U
- 2026-09-15：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-15-1231-1-gateway-auth-header-npe-1-3c8f1a92

## Verification

- pass test 2026-09-15-1355-closure-audit exit=0

## Closure

- dispatch audit #audit-2026-09-14-110620-mission-driver-2026-09-15-1231-1-gateway-auth-header-npe-1-7e1a5ba0 to closer-session-2026-09-15-1355 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-2026-09-14-110620-mission-driver-2026-09-15-1231-1-gateway-auth-header-npe-1-7e1a5ba0：独立 closure audit 通过——Phase 1/2 两处 config==null 守卫（`sinkAuthHeader` / `buildHttpRequest`）按裁定 A 落地并经 live repo 复核（`loadLlmConfigOrNull` 容错加载 NopException→null+WARN、`sinkAuthHeader` 早退保持客户端头、`buildHttpRequest` null 断流、`sinkCandidate` :484 同链容错统一使既有 null 守卫可达）；本 visit 实跑 `./mvnw test -pl nop-ai/nop-ai-gateway -am` 全绿（196 tests 0 失败 0 错误，含新增端到端 `nonStreamingNoConfigCandidateKeepsClientHeader` / `streamingNoConfigReselectAbortsRetryWithWarn`，ListAppender 断言 WARN 日志 + 客户端头保留/断流，有配置候选零回归由既有套件守护）+ `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors，9 warnings 全部为其他历史计划/兄弟计划存量）；plan-check 26/26 全勾选 exit=0；语义复核 Phase 1-2 全过——无静默跳过（显式 WARN）、接线验证（守卫在 onRequest/retry 运行时路径执行）、端到端链路连通（fake 收到请求头保留 / 重试不发起二次 fetch）、Proof 调用点清单与 `classifyStreamError` catch-ignored 对照登记一致；owner doc（`02-account-failover-requirement.md` §3.4 裁定 A 条目）、roadmap P2-ROUND4-GATEWAY-AUTH-NPE 勾选、daily log 09-15.md 执行记录在位；无 in-scope defect 被降级