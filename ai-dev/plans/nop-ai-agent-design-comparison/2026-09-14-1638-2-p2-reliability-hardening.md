---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-REL
group: "2026-09-14-1638"
verify: [test]
---

# P2 nop-ai 可靠性层健壮性修复（熔断逃生 / 并发计数 / connect timeout / retry 语义）

## Current Baseline

- 来源：deep-audit round 2 的 P2 Follow-up Backlog（roadmap 第 10/11/12/13/18 项），全部经 live 复核（HEAD `4582e780dad4`）：
  1. **ThresholdBreaker HALF_OPEN 探测位可永久卡死**（`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/ThresholdBreaker.java`）：:3-4 重复 static import `ERR_AI_CORE_INVALID_STATE`；HALF_OPEN 的 `probeInFlight`（:128/:135-136）仅由 `recordSuccess`（:176）与 `recordFailure`（:212）清除——探针调用永不回报（取消/hang/线程终止）时熔断器永久 HALF_OPEN 拒绝全部后续调用，无超时逃生门。
  2. **ConcurrencyRegistry.release 下溢补偿非原子 + 计数表永不收缩**（`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/routing/ConcurrencyRegistry.java:52-64`）：`decrementAndGet` 后 `incrementAndGet` 恢复与并发 `acquire` 竞争会永久 +1 幽灵计数（自诱导下溢）；`counts` map 按 (provider, accountKey) 只增不删，长跑网关缓慢内存泄漏。
  3. **CFG_AI_SERVICE_CONNECT_TIMEOUT 死配置**（`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/AiCoreConfigs.java:30-32`）：全仓零消费（`grep -rn CFG_AI_SERVICE_CONNECT_TIMEOUT --include=*.java nop-ai/` 仅定义处）；`ChatServiceImpl.buildHttpRequest`（:253-272）只 `httpRequest.setTimeout(CFG_AI_SERVICE_READ_TIMEOUT.get())`；`HttpRequest`（`nop-network/nop-http/nop-http-api/.../HttpRequest.java:28`）只有单一 `timeout` 字段、无独立 connect timeout → 声明的 30s 连接超时静默失效，连接挂起可远超预期阻塞。
  4. **RetryDecision/IRetryPolicy/LlmErrorClassifier javadoc 过期**：`RetryDecision.java:14-18` 称 FALLBACK = fail-loud STOP、"no fallback chain wired"；与 `StandardRetryPolicy.java:126-129`（QUOTA_EXCEEDED/AUTH_INVALID → `RetryOutcome.fallback()`）及 `LlmCallCoordinator` 账号链/provider 链实际路由矛盾。`LlmErrorClassifier` javadoc 称 ChatServiceImpl 抛 NopException，与 `ChatServiceImpl.java:146-152` 归一化为错误 ChatResponse 的实际不符。
  5. **LlmCallCoordinator FALLBACK 循环无总步数上限**（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/LlmCallCoordinator.java`，872 行）：每次 FALLBACK 切换 `st.attempt = 0` 重置（:341/:350/:362/:410），护栏仅 execution-veto cap 3 与 circuit scan 64 → 自定义 `IModelRouter`（A→B→A）可 `while(true)` 无限发真实 LLM 调用 + backoff 睡眠，与同文件其他显式 cap 风格不一致。
- 归属模块：nop-ai-core（1/2/3/4）、nop-ai-agent（4/5）；`nop-http-api` 仅涉及字段能力判定。
- 现有测试基建：`TestThresholdBreaker`、`TestConcurrencyRegistry`、`TestLlmCallCoordinator` 等已存在（执行时按 live 复核类名与基建）。

## Goals

- ThresholdBreaker：探针永不回报时有可观测逃生路径（HALF_OPEN 超时回落或允许新探针），既有状态机语义不回归；去重 import。
- ConcurrencyRegistry：消除 release 补偿竞态与幽灵计数；计数表可收缩（或显式有界策略）；回归测试覆盖并发下溢与长跑键增长。
- connect timeout 死配置收口：被真实消费（连接超时生效且可测）或删除并同步修正文档描述；不允许零消费死配置留存。
- RetryDecision/IRetryPolicy/LlmErrorClassifier javadoc 与 live 语义一致（FALLBACK 通道、错误归一化行为）。
- LlmCallCoordinator FALLBACK 循环有显式总步数上限，超限 fail-loud；回归测试覆盖 A→B→A 循环。
- 涉及模块 `./mvnw test -pl <module> -am` 通过；check-doc-links 0 error。

## Non-Goals

- 不改错误分类策略与 FALLBACK 路由语义（只补上限/文档）；不改 `IModelRouter` SPI 契约。
- 不改 `nop-http-api` 的 `HttpRequest` 公共契约：若无法在不改公共 API 的前提下让连接超时生效，则删除死配置并修正文档。
- 不处理 P2 其余项（文档类见 `2026-09-14-1638-1`，引擎卫生见 `2026-09-14-1638-3`）。
- 不运行 mvn 全量构建。

## Phase 1 — ThresholdBreaker HALF_OPEN 逃生门 + import 去重

Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/ThresholdBreaker.java`、`nop-ai/nop-ai-core/src/test/.../TestThresholdBreaker*.java`

- Item Types: `Fix | Proof`

- [x] `Fix` 为 HALF_OPEN 探针增加超时逃生：记录探针起始时间，超过逃生阈值（复用/新增配置，默认与 cooldown 同量级）后允许新探针或回落 OPEN；成功/失败回报路径行为不变。
- [x] `Fix` 删除 :4 重复 static import `ERR_AI_CORE_INVALID_STATE`。
- [x] `Proof` 逐状态核对 CLOSED→OPEN→HALF_OPEN→CLOSED/OPEN 转移矩阵，确认逃生路径不产生双探针并发或非法回退。
- [x] `Fix` 回归测试：只 `allowCall` 无 `record*` 的探针永不回报 → 超时后恢复可用；正常 recordSuccess/recordFailure 转移不变；并发探针唯一性保持。

Exit Criteria:

- [x] 探针永不回报场景下熔断器可恢复（测试为证）；正常状态机无回归。
- [x] 重复 import 消失。
- [x] **端到端验证**：从 `allowCall` 入口经 HALF_OPEN 到 record*/超时逃生的完整路径覆盖。
- [x] **接线验证**：逃生判定在 `allowCall` 运行时被消费（非仅新增字段）。
- [x] **无静默跳过**：超时逃生有明确语义与日志，不静默放行/静默拒绝。
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am` 通过。
- [x] owner doc：阈值/配置语义变化则更新对应 reliability owner doc；否则 `No owner-doc update required`。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 2 — ConcurrencyRegistry 下溢补偿竞态与计数表收缩

Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/routing/ConcurrencyRegistry.java`、对应测试

- Item Types: `Fix | Proof`

- [x] `Fix` release 下溢补偿改为无竞态实现（CAS 循环或先比较后减），下溢仍 fail-fast 抛既有错误码，但不制造幽灵 +1。
- [x] `Fix` 计数表收缩：计数为 0 的键安全移除（原子移除/定期清理）或登记显式有界策略；并发 acquire 与移除不得造成计数丢失/重复计数。
- [x] `Fix` 回归测试：多线程无匹配 release 竞争后 `currentCount` 不出现幽灵值；大量不同 key 释放后 map 规模回落（或按裁定有界）；acquire/release 配对计数正确。
- [x] `Proof` 复核 `currentCount` 消费者（router/健康视图读取路径）在收缩后语义不变。

Exit Criteria:

- [x] 并发下溢竞争测试下计数保持 0（无幽灵 +1）；下溢仍 fail-fast。
- [x] 计数表不再只增不删（或按显式裁定有界并记录理由）。
- [x] **端到端验证**：从 router/health 读取路径到 acquire/release 生命周期完整覆盖。
- [x] **接线验证**：收缩逻辑被 release/currentCount 运行时调用。
- [x] **无静默跳过**：下溢仍显式抛错，不静默钳制。
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am` 通过。
- [x] owner doc 更新或 `No owner-doc update required`。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 3 — CFG_AI_SERVICE_CONNECT_TIMEOUT 死配置收口

Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/AiCoreConfigs.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java`、相关配置文档

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` 裁定：A) 经既有客户端配置/请求 attr 让连接超时真实生效（不得改 `nop-http-api` 公共契约）；B) 删除死配置并修正 `@Description` 与文档中 30s 连接超时宣称。记录选择理由与备选方案。
- [x] `Fix` 按裁定落地：方案 A 新增可测路径（连接超时设置可断言）；方案 B 删除定义并全仓确认零残留。
- [x] `Proof` 复核 `ChatServiceImpl` 请求 timeout 语义（read timeout 保留），确认连接超时行为与文档一致。

Exit Criteria:

- [x] 不存在零消费的 `CFG_AI_SERVICE_CONNECT_TIMEOUT`（被消费或已删除）；文档/描述与 live 行为一致。
- [x] 若消费：连接超时设置可被测试断言；若删除：全仓 grep 零残留且 `nop.ai.service.connect-timeout` 声明修正。
- [x] **端到端验证**：请求构建入口到 HTTP 客户端 timeout 配置的路径覆盖（方案 A）或不适用（方案 B）。
- [x] **接线验证**（方案 A）：配置值被请求构建路径运行时读取。
- [x] **无静默跳过**：不保留"定义即生效"的假象，配置未被消费即删除。
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am` 通过。
- [x] owner doc（配置清单/可靠性文档）同步或 `No owner-doc update required`。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 4 — retry/fallback 语义对齐（javadoc + FALLBACK 总步数上限）

Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/{RetryDecision,IRetryPolicy,LlmErrorClassifier}.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/LlmCallCoordinator.java`、对应测试

- Item Types: `Fix | Proof`

- [x] `Fix` 更新 `RetryDecision`/`IRetryPolicy`/`LlmErrorClassifier` javadoc 至 live 语义：FALLBACK 已接线（QUOTA/AUTH → 账号链；TRANSIENT 等 → 模型 tier 路由），ChatServiceImpl 归一化错误响应的事实。
- [x] `Fix` `LlmCallCoordinator` 增加 FALLBACK 循环显式总步数上限（常量风格与 veto cap 3 / circuit scan 64 一致），超限 fail-loud 抛既有或新增错误码，不静默退出。
- [x] `Proof` 复核全部 `st.attempt = 0` 重置点（:341/:350/:362/:410），确认账号链与模型 tier 两通道的每次切换都计入总步数。
- [x] `Fix` 回归测试：自定义 `IModelRouter` A→B→A 循环触发上限后 fail-loud（LLM 调用次数 ≤ 上限 + 1）；正常单次 FALLBACK 不受影响。

Exit Criteria:

- [x] javadoc 与 live 路由语义一致（FALLBACK 不再被描述为 fail-loud STOP）。
- [x] A→B→A 循环在上限内终止并 fail-loud（测试断言调用次数有界）。
- [x] **端到端验证**：从 coordinator 调用入口经 FALLBACK 路由到终止的完整路径覆盖。
- [x] **接线验证**：上限计数被各 FALLBACK 分支运行时消费。
- [x] **无静默跳过**：超限抛错，不静默返回。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过。
- [x] owner doc 更新或 `No owner-doc update required`。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-14-1638-2-p2-reliability-hardening-1-9f4c2b81 to opencode-pid-72024
- 2026-09-14：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-14-1638-2-p2-reliability-hardening-1-9f4c2b81

## Verification

- pass test 2026-09-14-110620 exit=0

## Closure

- dispatch audit #audit-2026-09-14-110620-mission-driver-2026-09-14-1638-2-p2-reliability-hardening-1-10695cdb to opencode-go/deepseek-v4-flash models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-2026-09-14-110620-mission-driver-2026-09-14-1638-2-p2-reliability-hardening-1-10695cdb：独立 closure audit 复核通过——47/47 Phase 条目全勾选、frontmatter status: active（完成派生）；5 项 in-scope live defect 全部落地（ThresholdBreaker probeTimeoutMs 逃生门+重复 import 去重 / ConcurrencyRegistry CHM compute per-key 原子临界区+归零收缩 / CFG_AI_SERVICE_CONNECT_TIMEOUT 裁定 B 删除且 live 代码零残留 / 3 份 javadoc 对齐 live 语义 / LlmCallCoordinator MAX_FALLBACK_STEPS=16 四切换点计数+fail-loud）；本 visit 实跑验证：`node ai-dev/tools/check-doc-links.mjs --strict` exit=0（0 errors，8 warnings 均为其他 plan 既有）、`./mvnw test -pl nop-ai/nop-ai-core -am` exit=0（TestThresholdBreaker 22 例、TestConcurrencyRegistry 8 例全绿）、`./mvnw test -pl nop-ai/nop-ai-agent -am` exit=0（TestLlmCallCoordinatorFallbackCap 2 例全绿）；Anti-Hollow：scan-hollow-implementations.mjs 对 nop-ai-core/nop-ai-agent 均 0 high/critical，逃生判定/收缩逻辑/cap 计数分别在 allowCall、release/currentCount、runRetryLoop 运行时被消费（非仅新增字段）；owner docs（nop-ai-agent-reliability.md §3.1/§3.3/§5.1、02-account-failover-requirement.md §3.3）+ roadmap 5 项勾选 + daily log 已同步；无 in-scope defect 被降级
