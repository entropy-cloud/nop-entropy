---
status: active
mission: nop-ai-agent-design-comparison
work-item: M5-P1
group: "2026-09-14-1314"
verify: [test]
---

# M5-P1 逐项修复：round-1 审计发现的 4 个 P1 缺陷

## Current Baseline

- deep-audit round 1（2026-09-14）登记 4×P1，全部经 live repo 复核仍成立：
  1. **plan 356 ErrorCode 化后 5 个错误码零测试钉子**：`ERR_AGENT_INVALID_ARGUMENT`/`ERR_AGENT_INVALID_STATE`/`ERR_AGENT_INTERNAL_DETAIL`（NopAiAgentErrors.java:26-32，ID 为 `nop.err.ai.agent.*`）+ `ERR_AI_CORE_INVALID_ARGUMENT`/`ERR_AI_CORE_INVALID_STATE`（NopAiCoreErrors.java:10-13，ID 为 `nop.err.ai.core.*`）共 628 处（agent main）+ 26 处（core main）throw 站点，agent/core 测试对**这些码**零 `getErrorCode()` 断言（全仓仅对其它码有断言，如 TestIAiMemoryStoreDefaultMethods 等）→ 错误码值/参数契约可静默漂移而测试全绿。
  2. **AiModelCredentialResolverImpl 三个 ErrorCode ID 违反 `nop.err.ai.*` 点号命名约定**：`ERR_AI_CREDENTIAL_FIELD_EMPTY`/`ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE`/`ERR_AI_CREDENTIAL_RESOLVER_NOT_CONFIGURED`（AiModelCredentialResolverImpl.java:64-96）用大写常量式 ID + 中文描述，经跨模块公共路径被 nop-ai-core `ChatServiceImpl`（service/ChatServiceImpl.java:17 经 `IAiModelCredentialResolver` 消费）调用，i18n/日志/前端按 `nop.err.*` 解析失效；同模块 `NopAiErrors` 已是正确格式，属契约面命名漂移。
  3. **nop-ai-tools → nop-ai-coder 分层倒置**：`FileToolBizModel.java:250-251` 直接 `new DslToolImpl(...)`（import io.nop.ai.coder.xdsl.DslToolImpl，:3-4），与 module-groups.md:17/87/91 分层（tools=具体工具实现、coder=AI 编程助手）相悖；复用 DSL 工具须连带引入 coder 重量传递依赖。
  4. **nop-ai-gateway 模块范围漂移**：channel 消息网关（ChannelMessageServiceImpl/FeishuConnector/ChannelSessionStoreImpl/IChannelConnector）与扫码登录编排（ChannelLoginApiBizModel/ChannelLoginScanProcessor）为生产面，但 docs-for-ai 的 nop-ai.md/nop-ai-gateway.md/module-groups.md 零文档覆盖（nop-ai-gateway.md 只讲 failover，258 行内 0 命中），消费者按文档引入模块会连带 nop-ai-dao/nop-auth-api/feishu 依赖。

## Goals

- 4 个 P1 缺陷逐一修复：错误码测试钉子补断言、错误码 ID 规范化、分层倒置收口（下沉契约或显式登记）、gateway 文档补全。
- 各归属模块 `./mvnw test -pl <module> -am` 通过；check-doc-links 0 error。

## Non-Goals

- 不修复 M6 的 6 项（另一计划）。
- 不修复 Follow-up Backlog 的 P2 项。
- 不改 NopAiAgentErrors/NopAiCoreErrors 既有错误码 ID（它们已符合 `nop.err.ai.*` 约定，只补测试钉子）。
- 不运行 mvn 全量构建。

## Phase 1 — 5 个错误码补 `getErrorCode()` 测试钉子（agent + core）

Status: planned

Targets: `nop-ai/nop-ai-agent/src/test/...`、`nop-ai/nop-ai-core/src/test/...`

- Item Types: `Fix | Proof`

- [x] `Proof` 盘点 5 个错误码（ERR_AGENT_INVALID_ARGUMENT/ERR_AGENT_INVALID_STATE/ERR_AGENT_INTERNAL_DETAIL + ERR_AI_CORE_INVALID_ARGUMENT/ERR_AI_CORE_INVALID_STATE）的代表性 throw 站点（每码 ≥2 个：agent 引擎路径与 core 服务路径），记录类:行号作为测试锚点。
- [x] `Fix` 为 agent 侧 3 码新增测试：触发代表站点后断言 `ex.getErrorCode()` 等于对应 `ErrorCode`（且 `ErrorCode.getErrorCode()` 字符串与 `nop.err.ai.agent.*` 一致），有参数时断言关键 param（如 `ARG_DETAIL`）存在。
- [x] `Fix` 为 core 侧 2 码新增测试：同上断言 `nop.err.ai.core.invalid-*`。
- [x] `Fix` 可选：若 5 码中有参数契约（detail 占位符），在测试中断言消息模板可渲染（`NopException.getDescription()` 非空）。

Exit Criteria:

- [x] 5 个错误码各自至少 1 个 `getErrorCode()` 断言测试落地且通过（agent 3 码 + core 2 码全覆盖）。
- [x] 测试断言的是错误码值/参数契约（正确结果），非仅异常类型。
- [x] **端到端验证**：从公开入口（如引擎非法参数/非法状态路径、core 服务调用路径）触发真实 throw 站点后断言错误码，而非仅静态查表。
- [x] **接线验证**：测试触发的 throw 站点确实是生产代码路径（非测试专用 stub 抛错）。
- [x] **无静默跳过**：不适用（纯测试新增，无新实现路径，不触碰生产代码）。
- [x] `No owner-doc update required`（错误码 ID 不变，仅补测试）。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 2 — AiModelCredentialResolverImpl 错误码 ID 规范化为 `nop.err.ai.*`

Status: planned

Targets: `nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/infra/AiModelCredentialResolverImpl.java`、`NopAiErrors` 或新增模块级 Errors 容器、`TestAiModelCredentialResolver*.java`

- Item Types: `Fix | Proof`

- [x] `Fix` 将 `ERR_AI_CREDENTIAL_FIELD_EMPTY`/`ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE`/`ERR_AI_CREDENTIAL_RESOLVER_NOT_CONFIGURED` 的 ID 改为点号命名（如 `nop.err.ai.service.credential-field-empty` 等，参照同模块 `NopAiErrors` 格式），描述改英文（AGENTS.md 英文错误消息约定），静态字段名可保留或一并重命名（以调用点批量可改为准）。
- [x] `Proof` 全仓 grep 3 个旧 ID 的所有消费点（含跨模块 `ChatServiceImpl` 的捕获/传播路径与测试断言），逐一更新为新的点号 ID；确认无字符串精确匹配旧 ID 的残留（参照 ChannelLoginScanProcessor `MFA_REQUIRED_ERROR_CODE` 的字符串匹配教训）。
- [x] `Fix` 更新 `TestAiModelCredentialResolver.java`/`TestAiModelCredentialResolverWiring.java` 中断言为新的 `getErrorCode()` 值，并补充"错误码 ID 符合 `nop.err.ai.*` 前缀"的守卫断言（防回归）。
- [x] `Fix` 若 nop-ai-service 有错误码文档/错误码清单（docs-for-ai 或模块 README），同步更新；否则 `No owner-doc update required`。

Exit Criteria:

- [x] 3 个错误码 ID 全部为 `nop.err.ai.*` 点号格式、描述为英文；全仓无旧 ID 残留消费。
- [x] `TestAiModelCredentialResolver*` 断言新 ID 且通过。
- [x] **端到端验证**：从 `ChatServiceImpl`（nop-ai-core 消费方）捕获 resolver 异常的路径断言 `getErrorCode()` 为新 ID。
- [x] **接线验证**：消费链（resolver throw → ChatServiceImpl 捕获/传播）运行时连通且错误码一致。
- [x] **无静默跳过**：错误码重命名不做别名兼容映射（无旧 ID 字符串匹配分支）。
- [x] owner doc 同步如上（错误码表或显式 `No owner-doc update required`）。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 3 — nop-ai-tools → nop-ai-coder 分层倒置收口

Status: planned

Targets: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/file/FileToolBizModel.java`、`nop-ai/nop-ai-toolkit`（若下沉）或 `docs-for-ai/01-repo-map/module-groups.md`

- Item Types: `Decision | Fix`

- [x] `Decision` 裁定方案（二选一，依据契约下沉成本与既有消费面）：
  - 方案 A：把 `IDslTool`/`DslToolImpl` 契约从 nop-ai-coder 下沉到 nop-ai-toolkit（工具抽象层）或 nop-ai-tools 内，nop-ai-coder 反向依赖 tools/toolkit；FileToolBizModel 不再 import `io.nop.ai.coder.*`。
  - 方案 B：显式登记为有意设计——在 `docs-for-ai/01-repo-map/module-groups.md` 的 nop-ai-tools 行补注 "DSL 工具经 nop-ai-coder.xdsl 复用（有意设计，连带 coder 依赖），替代方案为下沉 toolkit（见 M5-P1 plan）"，并把模块依赖文档中 tools→coder 的边显式标注。
- [x] `Fix` 按裁定落地：方案 A 则移动/提取接口与实现并更新依赖与测试；方案 B 则仅更新 module-groups.md（含依赖方向说明），并在 `FileToolBizModel.getDslTool`（:250-251）补一行注释登记裁定来源（plan id）。
- [x] `Proof` 复核 `mvn dependency`/import 扫描：tools 模块 main 不再直接 import `io.nop.ai.coder`（方案 A），或 import 保留但文档已显式登记（方案 B）。

Exit Criteria:

- [x] 裁定结果已落盘（代码下沉 或 module-groups.md 显式登记，二选一且可追溯）。
- [x] 模块依赖文档与 live import 一致（方案 A：零 `io.nop.ai.coder` import；方案 B：文档明确标注有意设计）。
- [x] **接线验证**：`getDslTool` 返回的 DSL 工具仍被 saveDslFile 等运行时调用（既有工具功能不回归）。
- [x] **无静默跳过**：本项是架构裁定，不允许"既不下沉也不登记"的悬空状态。
- [x] `docs-for-ai/01-repo-map/module-groups.md`（或下沉方案下对应的 owner doc）已同步。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 4 — nop-ai-gateway 模块范围文档补全（channel 网关 + 扫码登录）

Status: planned

Targets: `docs-for-ai/03-modules/nop-ai-gateway.md`、`docs-for-ai/03-modules/nop-ai.md`（如适用）、`docs-for-ai/01-repo-map/module-groups.md`

- Item Types: `Fix | Proof`

- [x] `Proof` 盘点 gateway 生产面类清单与依赖：channel 消息网关（ChannelMessageServiceImpl/FeishuConnector/ChannelSessionStoreImpl/IChannelConnector）与扫码登录编排（ChannelLoginApiBizModel/ChannelLoginScanProcessor）的类锚点（包路径）、beans.xml 装配、以及连带依赖（nop-ai-dao/nop-auth-api/nop-integration-feishu）。
- [x] `Fix` 在 `docs-for-ai/03-modules/nop-ai-gateway.md` 新增章节：模块实际承载三块能力（LLM failover 网关 + channel 消息网关 + 扫码登录编排），每块列出关键类锚点与装配入口；标注 channel/login 两块引入时的连带依赖清单。
- [x] `Fix` 更新 `docs-for-ai/01-repo-map/module-groups.md` 的 nop-ai-gateway 行（:88 现为"LLM 网关（多 Provider 路由/转换）"）为完整范围描述，并补依赖边说明（gateway → nop-ai-dao/nop-auth-api/nop-integration-feishu）。
- [x] `Proof` 复核 `docs-for-ai/03-modules/nop-ai.md` 中涉及 gateway 的引用是否需要同步（实体表/模块清单），如需则一并更新。

Exit Criteria:

- [x] `nop-ai-gateway.md` 覆盖 channel 消息网关与扫码登录编排（关键类锚点 + 装配 + 连带依赖），check-doc-links 0 error。
- [x] `module-groups.md` 的 nop-ai-gateway 行描述与实际模块范围一致。
- [x] **端到端验证**：文档锚点可解析——文档中每个类名在 `nop-ai/nop-ai-gateway/src/main` 存在且包路径正确。
- [x] **接线验证**：文档记录的 beans.xml 装配点真实存在（如 ai-gateway-defaults.beans.xml 中 channel/login bean）。
- [x] **无静默跳过**：不把未文档化模块面标记为"文档化"——每块能力都有真实类锚点支撑。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Draft Review Record

（待独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-2026-09-14-1314-3-m5-p1-round1-remediation-1-09cbbf58 to opencode-pid-33628
- 2026-09-14：iteration 1，共识 approved #review-2026-09-14-110620-2026-09-14-1314-3-m5-p1-round1-remediation-1-09cbbf58

## Verification

- pass test 20260914-1633 exit=0

## Closure

- dispatch audit #audit-2026-09-14-110620-2026-09-14-1314-3-m5-p1-round1-remediation-1-a31dde9a to opencode-closure-audit-68082 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-2026-09-14-110620-2026-09-14-1314-3-m5-p1-round1-remediation-1-a31dde9a：4×P1 修复经独立 closure audit 全部在 live repo 落地且运行时接线连通（5 错误码测试钉子 8 例断言 getErrorCode 值/参数/模板渲染，3 凭证错误码 ID 点号化且零旧 ID 残留、ChatServiceImpl 消费链 E2E 断言新 ID，IDslTool/DslToolImpl 下沉 nop-ai-tools 且全仓零 io.nop.ai.coder.xdsl import 残留、gateway 文档类锚点 10/10 + beans 装配 5 处确认）；增量复跑 ./mvnw test -pl 五模块 -am（23 例 0 失败，BUILD SUCCESS）；check-doc-links 0 error；plan-check --strict 42/42 通过