---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-ROUND4-ERR-MSG-LEGACY
group: "2026-09-15-1231"
verify: [test]
---

# P2 round-4 错误消息英文化 successor（NopAiCoreErrors 25 + GptOrmErrors 1 中文描述 → 英文）

## Current Baseline

- 来源：roadmap `## Follow-up Backlog` 已勾选项 line 188 的收口注记 + plan `2026-09-15-1029-1-err-msg-english-round4.md` 登记的 successor 候选（`source: deep-audit round 4`），live repo 复核（2026-09-15）：
  1. **`NopAiCoreErrors`（nop-ai-core）25 个中文描述**：`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/NopAiCoreErrors.java`——round-4 登记口径为 26 个，live grep `[\u4e00-\u9fff]` 实测 25 个含中文描述的 `define(...)` 站点（:72/:85/:88/:91/:106/:109/:112/:115/:118/:121/:153/:155/:157/:159/:162/:164/:167/:170/:173/:176/:178/:181/:184/:187/:190），全部为 `nop.err.ai.service.*` / `nop.err.ai.tools.*` / `nop.err.ai.*` 前缀既有业务码（NO_DEFAULT_LLMS/NO_BASE_URL/OPTION_NOT_SET/HTTP_ERROR/RATE_LIMITED/RESULT_*×4/INVALID_THOUGHT/INVALID_RESPONSE/MANDATORY_INPUT_OR_OUTPUT_EMPTY/PROMPT_*×6/UNKNOWN_TOOL_CALL/FILE_CONTENT_NO_PATH/COMMAND_NOT_FOUND/EMPTY_TOOLS_NODE/TOOLS_NODE_PARSE_FAILED/FILE_PATH_IS_EMPTY），跨模块被 nop-ai-core ChatServiceImpl / nop-ai-gateway / nop-ai-agent 消费。
  2. **`GptOrmErrors`（nop-ai-dsl-orm）1 个中文描述**：`nop-ai/nop-ai-dsl-orm/src/main/java/io/nop/ai/dsl/orm/GptOrmErrors.java:27-28` `ERR_DSL_ORM_UNKNOWN_SQL_TYPE`（`nop.err.ai.dsl-orm.unknown-sql-type`）"未识别的SQL类型:{sqlType}"。
  3. **前置先例**：plan `2026-09-15-1029-1` 已英文化 nop-ai-service `NopAiErrors.ERR_AI_SESSION_ID_REQUIRED` + nop-ai-coder `AiCoderErrors` 2 码（ID/参数契约不变，回归守卫：描述无 CJK + 抛错站点行为断言）；本计划承接其 Non-Goals 显式登记的跨模块存量 successor 候选。`docs-for-ai/02-core-guides/error-handling.md` 已确立"错误消息必须英文"两档策略（框架核心/公共 API 用 ErrorCode，描述英文）。
  4. 归属模块：nop-ai-core（25）+ nop-ai-dsl-orm（1）。
  5. 验证面：描述文本变更（ErrorCode ID / 参数契约 / 抛错站点不变），涉及 `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-dsl-orm -am`；owner doc `docs-for-ai/02-core-guides/error-handling.md` §模式二「消息语言规则」（原 line 119-123）**明确声称**了被本计划改变的描述文本（"既有业务错误码 → 中文"分界 + `no-default-llms` / `ERR_AI_TOOLS_INVALID_THOUGHT` 中文示例 + 例外模块清单缺 nop-ai-service / nop-ai-dsl-orm 等）——本计划完成后必须同步该文档（closure-audit 复核纠正了草稿期 "No owner-doc update required" 的误判，见 `## Revision Record`）。

## Goals

- `NopAiCoreErrors` 25 个 + `GptOrmErrors` 1 个 ErrorCode 描述英文化（ID `nop.err.ai.*` 与参数契约不变）。
- 两个文件复核确认无中文描述残留（`[\u4e00-\u9fff]` 零命中）；全仓错误码容器中文描述存量清零（或登记余量）。
- 回归测试守护：错误码 ID/参数契约不变 + 描述为英文（无 CJK 字符）——防止再次漂移为中文。
- `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`ai-dev/logs/` 收口条目。

## Non-Goals

- 不改任何 ErrorCode 的 ID、参数名、参数契约与抛错站点；不改任何日志/前端消费逻辑；不做 i18n 资源迁移。
- 不处置本轮 roadmap 其余未勾选项与 successor 候选（gateway sinkAuthHeader NPE 由兄弟计划 `2026-09-15-1231-1` 承接；MFA 错误码上移 nop-auth-api 等另开计划）。
- 不运行 mvn 全量构建。

## Phase 1 — NopAiCoreErrors 25 个中文描述英文化 + 回归守护

Status: planned

Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/NopAiCoreErrors.java`、`nop-ai/nop-ai-core/src/test/`（如 `TestNopAiCoreErrorsContract`）

- Item Types: `Fix | Proof`

- [x] `Fix` `NopAiCoreErrors.java` 25 个中文描述逐个改英文（保持 ID/参数/占位符语义：如 `NO_DEFAULT_LLMS`→"No default LLM is configured...", `HTTP_ERROR`→"LLM {llmName} call failed, HTTP status={httpStatus}", `RATE_LIMITED`→"LLM {llmName} call rate-limited (local quota exhausted)", 其余按语义直译），逐条核对参数占位符不丢。
- [x] `Fix` 回归测试：新增/扩展测试断言 25 个码描述为英文（无 CJK 字符）且 `getErrorCode()`/参数契约不变（复用 `TestNopAiCoreErrorsContract` 既有断言位 + 描述守卫）。
- [x] `Proof` 复核：`NopAiCoreErrors.java` 全文 grep `[\u4e00-\u9fff]` 零命中；全仓 nop-ai 错误码容器中文描述存量重新扫描（`NopAiErrors`/`AiCoderErrors`/`McpServerErrors` 等应为 0，发现即登记或一并处置）；抛错站点（ChatServiceImpl 等）仍指向同一 ID。

Exit Criteria:

- [x] 25 个描述为英文，ID `nop.err.ai.*` 与参数契约不变（diff 可核查）。
- [x] 文件 grep `[\u4e00-\u9fff]` 零命中。
- [x] 回归测试断言描述无 CJK + `getErrorCode()` 契约不变（断言具体行为，非仅"不报错"）。
- [x] **端到端验证**：至少一个代表码（如 `ERR_AI_SERVICE_HTTP_ERROR`）经 ChatServiceImpl 抛错路径实测描述为英文且 ID 不变（既有测试路径或新增断言）。
- [x] **接线验证**：抛错站点与 ErrorCode 定义仍指向同一 ID（grep 抽查 ≥5 个码）。
- [x] **无静默跳过**：无描述被留空或吞掉；所有处置显式落地。
- [x] owner doc 更新：`docs-for-ai/02-core-guides/error-handling.md` §模式二「消息语言规则」已同步——改写为 **nop-ai 模块族全量英文边界**（删除"既有业务错误码 → 中文"分界与 `no-default-llms` / `ERR_AI_TOOLS_INVALID_THOUGHT` 中文示例，例外模块清单补齐 nop-ai-service / nop-ai-dsl-orm / nop-ai-gateway / nop-ai-mcp-server / nop-ai-toolkit 共 11 个模块容器，登记边界演进：plan 1029-1 + 本计划收敛为模块族粒度）。原 "No owner-doc update required" 判定经 closure-audit 复核**不成立**（文档确实声称了被本计划改变的中文描述文本），已按修订后事实重写本条目（见 `## Revision Record`）。
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 2 — GptOrmErrors 1 个中文描述英文化 + 回归守护

Status: planned

Targets: `nop-ai/nop-ai-dsl-orm/src/main/java/io/nop/ai/dsl/orm/GptOrmErrors.java`、`nop-ai/nop-ai-dsl-orm/src/test/`

- Item Types: `Fix | Proof`

- [x] `Fix` `GptOrmErrors.ERR_DSL_ORM_UNKNOWN_SQL_TYPE` 描述改英文（"Unknown SQL type: {sqlType}"），ID `nop.err.ai.dsl-orm.unknown-sql-type`、参数 `{sqlType}` 不变；javadoc 同步（模块类名 GptOrm 保留裁定不动）。
- [x] `Fix` 回归测试：断言描述为英文（无 CJK）+ `getErrorCode()`/参数契约不变（dsl-orm 侧描述守卫）。
- [x] `Proof` 复核：`GptOrmErrors.java` grep `[\u4e00-\u9fff]` 零命中；抛错站点（如 `GptOrmSqlType` / `AiOrmSqlType` 等，按 live 检索）仍指向同一 ID。

Exit Criteria:

- [x] 描述为英文，ID 与参数契约不变（diff 可核查）。
- [x] 文件 grep `[\u4e00-\u9fff]` 零命中。
- [x] 回归测试断言描述无 CJK + `getErrorCode()` 契约不变（断言具体行为）。
- [x] **端到端验证**：未知 SQL 类型抛错路径（经模块入口）实测描述为英文且 ID 不变。
- [x] **接线验证**：抛错站点与 ErrorCode 定义仍指向同一 ID。
- [x] **无静默跳过**：无描述被留空或吞掉。
- [x] owner doc 更新：`error-handling.md` 已随 Phase 1 同步（nop-ai 模块族全量英文边界覆盖 `GptOrmErrors` / nop-ai-dsl-orm，例外模块清单含 nop-ai-dsl-orm）。原 "No owner-doc update required" 判定经 closure-audit 复核**不成立**，已修正（见 `## Revision Record`）。
- [x] `./mvnw test -pl nop-ai/nop-ai-dsl-orm -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Closure Gates

> 关闭条件记录（01-file-ledger §4.3 消解为 §5.2 完成公式派生）：`NopAiCoreErrors` 25 码 + `GptOrmErrors` 1 码描述英文化（ID `nop.err.ai.*` 与参数契约不变）+ 零 CJK 残留（文件级 grep 复核）+ 回归测试守护（描述无 CJK + `getErrorCode()` 契约不变）+ 抛错站点接线抽查 + 全仓错误码容器中文描述存量收口登记后，由独立子 agent closure-audit 完成并写入 `## Closure`；本 section 不保留可写 checkbox，机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-15-1231-2-err-msg-legacy-english-1-2f85a174 to opencode-pid-58679
- 2026-09-15：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-15-1231-2-err-msg-legacy-english-1-2f85a174

## Revision Record

- 2026-09-15 closure-audit round 2（mission-driver 反馈）：owner-doc drift 阻断关闭——plan 执行时登记的 "`error-handling.md` 无需更新 / No owner-doc update required" 判定不成立：`error-handling.md` §模式二「消息语言规则」明确声称"既有业务错误码 → 中文"并以 `no-default-llms`、`ERR_AI_TOOLS_INVALID_THOUGHT` 为例，与本计划英文化后的 live 行为矛盾，例外模块清单亦缺 nop-ai-service（plan 1029-1 已英文化）/ nop-ai-dsl-orm。修订：① `error-handling.md` 改写为 nop-ai 模块族全量英文边界（含边界演进登记，live 复核 `\p{Han}` 全 nop-ai 零命中）；② 本计划 Current Baseline item 5 与 Phase 1/2 owner-doc 退出条件按修订后事实重写；③ `ai-dev/logs/2026/09-15.md` 顶部追加收口修正条目；④ roadmap P2-ROUND4-ERR-MSG-LEGACY 注记补充 owner-doc 同步说明。代码/测试面无改动（closure-audit round 1 已核：`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-dsl-orm` BUILD SUCCESS、两文件 CJK 零命中、doc-links exit=0）。

## Verification

- pass test 2026-09-15T06-28-38-930Z exit=0

## Closure

- dispatch audit #audit-20260915-0629-2026-09-15-1231-2-err-msg-legacy-english-1-c7e2a9f4 to opencode-pid-closure-audit-2026-09-15-1231-2 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260915-0629-2026-09-15-1231-2-err-msg-legacy-english-1-c7e2a9f4：独立 closure audit 通过——NopAiCoreErrors 25 码 + GptOrmErrors 1 码描述英文化已落地且两文件 `[\u4e00-\u9fff]` 零命中（全仓错误码容器剩余 CJK 均属 javadoc/注释，非描述面），ID/参数契约/抛错站点接线（ChatServiceImpl.checkRateLimit / DefaultAiChatService 非 200 / GptOrmSqlType.getStdSqlType）与测试守护（TestNopAiCoreErrorsDescriptionEnglish 3/3 + TestGptOrmErrorsDescriptionEnglish 2/2 真实抛错站点端到端）经 `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-dsl-orm -am` BUILD SUCCESS 验证，doc-links `node ai-dev/tools/check-doc-links.mjs --strict` exit=0（0 errors），owner doc error-handling.md 已同步为 nop-ai 模块族全量英文边界，无 Deferred/Follow-up 静默降级