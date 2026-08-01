# 1 error-handling.md 消息语言例外条款与 nop-ai 错误码 live 基线同步

> Plan Status: completed
> Last Reviewed: 2026-08-01
> Source: `ai-dev/plans/2026-08-01-0936-2-arm-p3-error-handling-core-coder.md` Non-Blocking Follow-ups（"error-handling.md 新增业务错误码语言默认规则（中文 vs agent/shell/core 英文例外）的存量 doc drift 登记，另作文档治理规划"，:156/:176）；`docs-for-ai/02-core-guides/error-handling.md` §模式二 消息语言规则（:118-121，§模式二 起始于 :114）
> Related: `2026-08-01-0936-1/2/3`（错误处理规范批次）、`2026-07-31-1834-3`（NopAiMavenErrors/NopAiCodeAnalyzerErrors）、`2026-07-31-2248-2`（scan-hollow 基线清零）
> Mission: audit-remediation
> Work Item: MA3.4 错误处理规范 — error-handling.md doc drift 收口（0936-2 登记）

## Purpose

把 `docs-for-ai/02-core-guides/error-handling.md` 的消息语言规则例外条款从"仅 nop-ai-agent / nop-ai-shell"同步为与 live 代码一致的真实范围（agent / shell / core / coder / maven / code-analyzer / tools 的转换类错误码均为英文），收口 0936-2 计划 closure 时登记、指定"另作文档治理规划"处理的存量 owner-doc drift。纯文档计划，零代码变更。

## Current Baseline

- `docs-for-ai/02-core-guides/error-handling.md` §模式二（:114-121）消息语言规则：
  - 默认规则：`ErrorCode.define(...)` 描述消息使用**中文**（:119）
  - 例外条款（:120）：**仅**列 nop-ai-agent / nop-ai-shell 两个模块（`NopAiAgentErrors` / `NopAiShellErrors` 的 fail-fast 转换类错误码使用英文），并声明"**其他模块的新增业务错误码仍按默认规则使用中文**"，起源叙事为"由 `UnsupportedOperationException("...")` 消息转换而来（plan 2248-2）"
  - 非 ErrorCode 路径（字符串构造器）必须英文（:121）
- **live 代码实际状态（与本条款不一致）**——以下模块的转换类/校验类错误码描述为英文：
  - `NopAiCoreErrors`（nop-ai-core）：2248-2 新增 `ERR_AI_CHAT_GET_SESSION_DEPRECATED`（"Deprecated: use IChatService instead"，:169）；0936-2 新增 7 码（`ERR_AI_FILE_INVALID_EDIT_TYPE` / `ERR_AI_PROMPT_TEMPLATE_NULL` / `ERR_AI_UNSUPPORTED_PARSE_FROM_RESPONSE` / `ERR_AI_UNKNOWN_ROLE` / `ERR_AI_VECTOR_LENGTH_MISMATCH` / `ERR_AI_SESSION_ID_IS_EMPTY` / `ERR_AI_SESSION_ID_INVALID`）；0936-3 新增 7 码（`ERR_AI_TOOLS_INVALID_PROJECT_NAME` / `ERR_AI_TOOLS_THOUGHT_EMPTY` / `ERR_AI_TOOLS_INVALID_THOUGHT_NUMBER` / `ERR_AI_TOOLS_INVALID_TOTAL_THOUGHTS` / `ERR_AI_TOOLS_TOTAL_THOUGHTS_LESS_THAN_NUMBER` / `ERR_AI_TOOLS_INVALID_STAGE` / `ERR_AI_TOOLS_INVALID_MAX_RESULTS`）——均为英文
  - `AiCoderErrors`（nop-ai-coder）：0936-2 新增 3 码（`ERR_AI_CODER_UNSUPPORTED_CONVERSION` / `ERR_AI_CODER_METHOD_SIGNATURE_NOT_FOUND` / `ERR_AI_CODER_UNBALANCED_BRACES`）——英文
  - `NopAiMavenErrors`（nop-ai-maven）：1834-3 新增 2 码（`nop.err.ai.maven.vfs-invalid-arg` "invalid argument: {msg}" / `nop.err.ai.maven.vfs-io-failed` "VFS operation failed: {msg}"）——英文
  - `NopAiCodeAnalyzerErrors`（nop-ai-code-analyzer）：1834-3 新增 2 码（"statistics collection failed: {msg}" / "invalid maven dependency input: {msg}"）——英文
  - `NopAiShellErrors`（nop-ai-shell）/ `NopAiAgentErrors`（nop-ai-agent）：2248-2 + 0936-1 英文码，已在例外条款覆盖范围内
- **分界反例（live 证据，文档措辞必须以"来源"而非"语义类型"定义分界）**：`NopAiCoreErrors.ERR_AI_TOOLS_INVALID_THOUGHT`（:90，中文"思维处理请求无效: {value}"）与 0936-3 新码同处 `nop.err.ai.tools.*` 命名空间，但为 pre-batch 既有码、未在转换批次中——真实分界是**来源驱动**（批次内从裸 IAE/UOE/RTE 逐字转换来的码 → 英文；其余既有业务码 → 中文），不是"校验类 vs 业务码"语义类型驱动
- `NopAiCoreErrors` 同文件既有业务错误码（`no-default-llms` 等）仍为中文——"转换码英文 / 既有业务码中文"的分界在 live 代码中真实存在，文档需准确描述而非一刀切
- 0936-2 plan 已在 Non-Blocking Follow-ups（:156）与 Closure Follow-up（:176）两处显式登记本 drift，指定"另作文档治理规划"——本计划即为该登记的承接
- roadmap `audit-remediation-roadmap.md` 其余工作项全部 `done`，无其他可重开项

## Goals

- `error-handling.md` 消息语言规则例外条款与 live 代码一致：明确列出实际使用英文转换类错误码的全部 nop-ai 模块
- 准确表述分界语义——**来源驱动**（从裸 IAE/UOE/RTE 转换、逐字保留历史消息的错误码 → 英文；既有业务错误码 → 中文），防止后续批次按"语义类型"误判（`ERR_AI_TOOLS_INVALID_THOUGHT` 中文码与英文新码同命名空间共存即证明语义类型分界不成立）
- 在 arm-index 与 daily log 登记本 drift 收口，形成可追溯闭环

## Non-Goals

- 不改任何代码（`nop-ai/*/src/main/java`、`nop-ai/*/src/test/java` 零改动）
- 不改写任何错误码本身（值、描述语言、参数名均不动）
- 不把例外条款推广到 nop-ai 之外的模块（nop-metadata 等仍按默认中文规则；nop-metadata 连字符特例条款不动）
- 不重写 error-handling.md 的 i18n 机制章节、ErrorCode 命名章节等其他段落

## Scope

### In Scope

- `docs-for-ai/02-core-guides/error-handling.md` §模式二 消息语言规则例外条款（:120）更新
- 引用面核查：`docs-for-ai/` 与 `ai-dev/design/` 中引用该规则的其他文档（如命中则同步；未命中则明确 No owner-doc update）
- arm-index「P3 追踪（第十二批）」段补登记收口记录 + roadmap 尾段叙事补一句
- `ai-dev/logs/2026/08-01.md` 收口条目

### Out Of Scope

- 其他模块组的错误处理规范
- 任何代码 / 测试 / 配置变更
- 存量中文业务错误码的英文化（有意保留，属历史中文业务码范畴）

## Execution Plan

### Phase 1 - 盘点 live 错误码语言分布与引用面

Status: completed
Targets: `nop-ai/**/src/main/java/**/*Errors.java`（实际全集：agent `NopAiAgentErrors` / coder `AiCoderErrors` / core `NopAiCoreErrors` / dsl-orm `GptOrmErrors` / maven `NopAiMavenErrors` / mcp-server `McpServerErrors` / service `NopAiErrors` / shell `NopAiShellErrors` / code-analyzer `NopAiCodeAnalyzerErrors`——nop-ai-tools 与 nop-ai-toolkit 无 `*Errors.java`，tools 码在 `NopAiCoreErrors` 中）、`docs-for-ai/`、`ai-dev/design/`

- Item Types: `Proof`

- [x] 用 grep 盘点全部 nop-ai 模块 `*Errors.java` 的 `define()` 描述语言，产出"模块 → 错误码清单 → 语言"对照表（live 证据，不依赖历史计划文本）
- [x] 盘点 `docs-for-ai/` 与 `ai-dev/design/` 中引用"错误码语言规则/中文 vs 英文例外"的文档清单（grep 命中面）
- [x] 裁定例外条款新措辞：模块清单 + 分界定义（来源驱动：批次内从裸 IAE/UOE/RTE 逐字转换的码 → 英文）+ 起源叙事（UOE 与 IAE/RTE 两类转换来源），确保与 Phase 1 对照表逐条吻合

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 对照表覆盖全部 9 个 `*Errors.java`（grep 输出可复核，与 Targets 列清单逐一对应）
- [x] 引用面清单完成（grep 命令与命中文件列表落盘 daily log）
- [x] **双向核对**：每个英文错误码都被列出（英文侧无遗漏，含 `ERR_AI_CHAT_GET_SESSION_DEPRECATED`），且文档声称英文的类别中不含中文码（中文侧核对——`ERR_AI_TOOLS_INVALID_THOUGHT` 等既有中文码不得被误纳入英文类）
- [x] No owner-doc update required（本 Phase 只盘点不写文档）—— 或注明后续 Phase 统一更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 更新 error-handling.md 例外条款

Status: completed
Targets: `docs-for-ai/02-core-guides/error-handling.md`

- Item Types: `Fix`

- [x] 更新 :120 例外条款：模块清单扩展为 agent / shell / core / coder / maven / code-analyzer（tools 码在 `NopAiCoreErrors` 中，随 core 一并说明），分界语义改为**来源驱动**（从裸 IAE/UOE/RTE 转换、逐字保留历史消息的错误码 → 英文；既有业务错误码 → 中文），起源叙事覆盖 UOE（2248-2）与 IAE/RTE（0936-1/2/3、1834-3）两类转换来源
- [x] 按 Phase 1 引用面清单同步命中文档（如有），并保持 check-doc-links 通过

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `error-handling.md` 例外条款列出的模块与 Phase 1 对照表一致（逐模块核对）
- [x] 分界语义（来源驱动：转换码英文 / 既有业务码中文）在文档中准确表述，与 live 代码中中文码（如 `no-default-llms`、`ERR_AI_TOOLS_INVALID_THOUGHT`）共存事实一致——文档不得声称"所有校验类错误码为英文"
- [x] 起源叙事（UOE + IAE/RTE 两类转换来源）已更新，不再只提 2248-2
- [x] 引用面同步完成或明确 No owner-doc update required
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 登记收口

Status: completed
Targets: `ai-dev/audits/arm-index.md`、`ai-dev/backlog/audit-remediation-roadmap.md`、`ai-dev/logs/2026/08-01.md`

- Item Types: `Follow-up`

- [x] arm-index「P3 追踪（第十二批）」段补一句：error-handling.md 语言例外条款 drift 已由本计划收口
- [x] roadmap 尾段叙事或第十二批行补一句登记（保持 0936-2 → 本计划的追溯链），并同步 bump 头部版本号 v14 → v15
- [x] daily log 收口条目（Phase 1 对照表 + Phase 2 diff 摘要 + 验证结果）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] arm-index 与 roadmap 已登记，0936-2 follow-up → 本计划的追溯链完整（grep 可查）
- [x] daily log 条目含 Phase 1 对照表与最终 diff 摘要
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本计划文件> --strict` 退出码 0（closure 时）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。
>
> **纯文档计划**：本计划不涉及任何代码变更（仅修改 `docs-for-ai/` 与 `ai-dev/` 下的文件），按 Plan Guide 纯文档计划豁免，`./mvnw test`、`./mvnw lint` 等构建验证条目删除。

- [x] 例外条款与 live 错误码语言分布一致（Phase 1 对照表逐条核对）
- [x] 无 in-scope live defect 或 contract drift 被静默降级（本 drift 为 Fix 项，已在本计划收口）
- [x] `docs-for-ai/02-core-guides/error-handling.md` 已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证文档描述与 live 代码一致（非仅改状态字；grep 命令输出可复核）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无——本计划 scope 内无延期项；其他批次 watch-only residual 均已在各自计划显式裁定 Successor Required: no）

## Non-Blocking Follow-ups

- 存量中文业务错误码（`NopAiCoreErrors` 的 `no-default-llms` 等）保持中文：属历史业务码范畴，无 audit finding 归属，不并入本计划（分界语义已在文档中说明）
- 其他模块组（nop-metadata 等）错误码语言一致性：超出本 roadmap 范围，如需要另行规划

## Closure

Status Note: 纯文档计划，三 Phase 全部完成，独立子 agent closure audit 通过（无 Blocker/Major），文档与 live 错误码分布逐条核对一致，checkers 全绿，可关闭。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，task id `ses_0445cc030ffemaIZOlGPae5wel`，fresh session）
- Audit Session: `ses_0445cc030ffemaIZOlGPae5wel`
- Evidence:
  - Phase 1 Exit Criteria 5/5 PASS：对照表覆盖全部 9 个 `*Errors.java`（独立 rg 计数复核：agent 22 / shell 8 / maven 2 / code-analyzer 2 全英文、core 40=25 中文+15 英文、coder 5=2 中文+3 英文）；引用面清单落盘 daily log；双向核对 PASS（`ERR_AI_CHAT_GET_SESSION_DEPRECATED` 英文确认；`ERR_AI_TOOLS_INVALID_THOUGHT`/`no-default-llms` 中文未被误纳入英文类）
  - Phase 2 Exit Criteria 6/6 PASS：`error-handling.md:120-121` 6 模块清单 + 来源驱动分界 + UOE/IAE-RTE 双起源叙事（git diff 复核与 plan 描述一致）；文档未声称"所有校验类错误码为英文"（显式保留中文码共存实例）；引用面同步裁定 No owner-doc update required；`check-doc-links.mjs --strict` exit 0
  - Phase 3 Exit Criteria 5/5 PASS：arm-index「P3 追踪（第十二批）」:501 收口句、roadmap v15 header + :268 尾段叙事（0936-2 → 1223-1 追溯链 grep 可查）、daily log 收口条目（对照表 + diff 摘要 + 验证结果）
  - Closure Gates 全部 PASS（见下）；Anti-Hollow：独立审计逐模块核对文档清单 vs live `*Errors.java` 语言分布 6/6 MATCH
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-01-1223-1-arm-error-handling-doc-drift-sync.md --strict` 退出码 0
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（1886 files，0 errors）
  - 纯文档计划豁免记录：git status 证实零代码变更（仅 4 个文档文件 + plan 文件），plan Closure Gates 已按 Plan Guide 纯文档豁免删除 mvnw test/lint 条目；`scan-hollow-implementations.mjs` 不适用（无代码变更）
  - Deferred 分类检查 PASS：无 in-scope live defect 被降级（drift 为本计划 in-scope Fix 已收口；follow-up 仅含历史中文业务码与越界模块组两类 non-blocking 项）

Follow-up:

- no remaining plan-owned work
- 存量中文业务错误码保持中文（历史业务码范畴，文档分界语义已说明，见 Non-Blocking Follow-ups）

## Optional Sections

## Risks And Rollback

- 措辞争议风险：模块清单与"转换类 vs 业务码"分界存在解释空间——Phase 1 对照表先行 + closure audit 逐条核对缓解
- 回滚 = 单 commit revert（纯文档改动，无依赖顺序）
