# 2 arm-p3-error-handling-core-coder — 错误处理规范收口：nop-ai-core + nop-ai-coder 裸 IAE/RTE → NopException + ErrorCode（P3-MA3-2 + P3-MA3-3）

> Plan Status: completed
> Last Reviewed: 2026-08-01
> Source: `ai-dev/audits/2026-07-31-0423-arm-MA3.4-nop-ai-error-handling.md`（P3-MA3-2、P3-MA3-3）+ `docs-for-ai/02-core-guides/error-handling.md` + `ai-dev/audits/arm-index.md`
> Mission: audit-remediation
> Work Item: P3-MA3-2 + P3-MA3-3（第十二批 deferred successor 重开）
> Related: `2026-08-01-0936-1-arm-p3-error-handling-agent-shell.md`、`2026-08-01-0936-3-arm-p3-error-handling-tools-toolkit.md`（同批次兄弟计划；**注意：本计划编辑 `NopAiCoreErrors.java`，若 0936-3 选"扩展 NopAiCoreErrors"方案则共享该编辑面——登记顺序 = 执行顺序（1→2→3），共享面由 0936-3 在 core 转换完成后追加，避免并行写冲突**）

## Purpose

把 MA3.4 错误处理审计中 nop-ai-core（P3-MA3-2）与 nop-ai-coder（P3-MA3-3）的裸 `IllegalArgumentException` 集群收口：转换为 `NopException` + 模块 ErrorCode（英文消息保持），消除"核心模块内部错误绕过框架异常体系"的残留。nop-ai-maven/code-analyzer 同型转换已在批次 5 完成，本计划是同一规范工作的延续。

## Current Baseline

- **MA3.4 审计结论**：P3-MA3-2（nop-ai-core 5 处 IAE，audit 复核 Retain (P3)："All 5 confirmed in utility code... Low severity, but real inconsistency"）；P3-MA3-3（nop-ai-coder converter IAE，audit 复核 Retain (P3)："Converter classes throw IAE for unsupported conversion types. Module has direct dependency on nop-ai-api so NopAiException is directly available"）。
- **批次史**：与 P2-MA3-1 同批被 1834-3 以 "P3 项" 排除，从未在批次 6-11 重开或单独裁定。
- **live 现状（grep 复核 2026-08-01）**：
  - `nop-ai-core`：**7 处**（6 文件）：FileDiffGenerator.java:213、PromptModel.java:88、ModelBasedPromptTemplate.java:244、AiMessage.java:51、CosineSimilarity.java:48、ChatLogHelper.java:65/69。
  - `nop-ai-coder`：**4 处**（3 文件）：AiOrmDocumentConverter.java:28（"Unsupported conversion:...->..."）、AiXdefDocumentConverter.java:21、JavaMethodReplacer.java:24/33（audit 复核段"New finding from review"新增点名，无独立 finding ID，同属 P3-MA3-3 类别，本计划显式纳入）。
  - 基础设施：core 有 `NopAiCoreErrors`（25+ 码，含 ERR_AI_INVALID_RESPONSE/ERR_AI_MANDATORY_INPUT_IS_EMPTY 等通用码，可复用或新增）；coder 有 `AiCoderErrors`（2 码，需新增）；nop-ai-api 有 `NopAiException extends NopException`（coder 直接依赖 nop-ai-api）。
  - 测试断言影响面：nop-ai-core 2 个测试文件引用 IAE（TestChatLogHelper/TestCosineSimilarityAndRelevanceScore，grep 复核），nop-ai-coder 0 个。
  - **既有 ISE 集群（不属本计划 scope，裁定见 Deferred）**：nop-ai-core 另有 5 个文件 ~8 处 `throw new IllegalStateException`（mock 包 FileSystemResponseProvider/MockChatService/InMemoryResponseProvider、FileDiffApplier、Media 等）——audit 未点名（scope 仅 IAE），本计划不处理，显式登记为 watch-only residual。
  - **新增 ErrorCode 语言约定**：core 新增转换码沿用 2248-2 先例用**英文**描述（live 既有码如 ERR_AI_CHAT_GET_SESSION_DEPRECATED 亦为英文，既有中文码不动）；error-handling.md 默认"新增业务错误码用中文"与 agent/shell 英文例外的存量 doc drift 不在本计划范围（owner-doc 更新另记 Non-Blocking Follow-ups）。
- **同规范先例**：1834-3（P2-MA3-2/P2-MA3-4：nop-ai-maven 13 处 + FileLanguageStats RTE → NopException + 模块 Errors）；2248-2（25 UOE → NopException + ErrorCode）。
- **绿色基线**：`ai-dev/logs/2026/08-01.md` 全量 `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS；scan-hollow exit 0。

## Goals

- nop-ai-core 7 处 + nop-ai-coder 4 处裸 IAE 全部转换为 `NopException` + ErrorCode（core 走 `NopAiCoreErrors` 或新增码；coder 走 `AiCoderErrors` 新增码，或 `NopAiException`（nop-ai-api）——以处置表裁定为准），转换后 grep 对应模块 main `throw new IllegalArgumentException|RuntimeException` = 0
- 消息语义与 cause 零变更；受影响测试断言同步（core 2 个测试文件）+ 新增 focused 测试
- arm-index 登记第十二批

## Non-Goals

- 不处理 nop-ai-agent/nop-ai-shell（兄弟计划 0936-1 承接）与 nop-ai-tools/nop-ai-toolkit（0936-3 承接）
- 不重开 P3-MA1-023~030、P3-MA1-039、MA4.2-01/-14（既有裁定不变）
- 不做消息内容优化或 i18n 化

## Scope

### In Scope

- `nop-ai/nop-ai-core/src/main/java/` 下全部裸 IAE（预期 7 处/6 文件）
- `nop-ai/nop-ai-coder/src/main/java/` 下全部裸 IAE（预期 4 处/3 文件）
- 相关测试断言更新 + 新增 focused 测试
- `NopAiCoreErrors`/`AiCoderErrors` 新增 ErrorCode（如需要）+ arm-index/roadmap 登记

### Out Of Scope

- 非 nop-ai 模块代码；其它模块 IAE（兄弟计划）
- 既有 catch IAE 调用点改造（Phase 1 盘点，预计 0）

## Execution Plan

### Phase 1 - 盘点与逐处裁定

Status: completed
Targets: 两模块 main grep + `NopAiCoreErrors.java` + `AiCoderErrors.java`

- Item Types: `Decision | Proof`
- [x] grep 全量盘点 nop-ai-core（预期 7 处/6 文件）与 nop-ai-coder（预期 4 处/3 文件）裸 IAE 清单（文件:行号:消息），与 audit 明细对照，输出逐处处置表（转换目标 ErrorCode）
- [x] 逐处裁定分类：(a) 校验/非法状态类 → 转换；(b) 实现 JDK 接口契约必须抛 IAE 的情形 → 显式裁定保留并记录理由
- [x] 规划新增/复用 ErrorCode（英文描述）：core 优先复用既有码（如 ERR_AI_MANDATORY_INPUT_IS_EMPTY/ERR_AI_INVALID_RESPONSE 语义不匹配时新增）；coder 新增如 `ERR_AI_CODER_UNSUPPORTED_CONVERSION`（带 ARG_FROM/ARG_TO）；码名执行时按处置表确定
- [x] 盘点 `catch (IllegalArgumentException` 调用面（预期 0），输出影响面清单

Exit Criteria:

- [x] 逐处处置表落盘 daily log，两模块 IAE 总数与审计/live grep 一致
- [x] ErrorCode 清单确定且未重复既有码
- [x] catch 调用面清单完成（0 命中则记录 0）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - nop-ai-core 转换（7 处）

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/`（6 文件）+ `NopAiCoreErrors.java`

- Item Types: `Fix`
- [x] 按处置表将 7 处裸 IAE 转换为 `NopException` + ErrorCode（消息经 ErrorCode 描述或 `.param()` 承载，语义逐字保持，cause 保留）
- [x] `NopAiCoreErrors` 新增规划 ErrorCode（英文描述；前缀沿用模块既有分类前缀风格，如 `nop.err.ai.service.*`/`nop.err.ai.tools.*` 等——live 码无 `nop.err.ai.core.*` 前缀，新码不强行造新前缀，按语义归类）
- [x] 更新 core 2 个测试文件断言 + 新增 focused 测试（值级断言：错误码/消息，覆盖每类新码至少 1 条）

Exit Criteria:

- [x] `grep -rn "throw new IllegalArgumentException\|throw new RuntimeException" nop-ai/nop-ai-core/src/main/java` = 0 命中（JDK 契约保留项若存在则显式列出）
- [x] 新增 ErrorCode 在 `NopAiCoreErrors` 内且英文描述；focused 测试值级断言通过；测试数量不减少
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am -T 1C` BUILD SUCCESS
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - nop-ai-coder 转换（4 处）

Status: completed
Targets: `nop-ai/nop-ai-coder/src/main/java/`（3 文件）+ `AiCoderErrors.java`

- Item Types: `Fix`
- [x] 按处置表将 4 处裸 IAE 转换为 `NopException` + `AiCoderErrors` 新增码（unsupported conversion 场景），消息语义逐字保持
- [x] 更新 coder 测试断言（如有）+ 新增 focused 测试（值级断言）

Exit Criteria:

- [x] `grep -rn "throw new IllegalArgumentException\|throw new RuntimeException" nop-ai/nop-ai-coder/src/main/java` = 0 命中
- [x] 新增 ErrorCode 在 `AiCoderErrors` 内且英文描述；focused 测试值级断言通过；测试数量不减少
- [x] `./mvnw test -pl nop-ai/nop-ai-coder -am -T 1C` BUILD SUCCESS
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 全量验证 + 登记

Status: completed
Targets: `nop-ai` 全模块 + `arm-index.md` + `ai-dev/backlog/audit-remediation-roadmap.md`

- Item Types: `Proof`
- [x] `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS + `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（全量回归，0 failures）
- [x] `scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] arm-index 登记 P3-MA3-2、P3-MA3-3 → fixed；roadmap 登记第十二批

Exit Criteria:

- [x] 全量 build + 全量 test 绿
- [x] scan-hollow exit 0；check-doc-links exit 0
- [x] arm-index 与 roadmap 登记完成
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] core 7 处 + coder 4 处裸 IAE 全部转换或显式裁定保留（处置表齐全），两模块 main grep 0 残留
- [x] 新增 ErrorCode 英文描述、无重复；消息语义与 cause 零变更
- [x] 受影响测试断言全部同步且新增 focused 测试（值级断言），测试数量零减少
- [x] 不存在被静默降级的 in-scope live defect
- [x] arm-index 与 roadmap 已登记
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证转换后异常运行时可达（抽查 throw 路径 + focused 测试断言）
- [x] `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（Phase 4 已执行，此处为 closure 复核）

## Deferred But Adjudicated

### nop-ai-core 既有 IllegalStateException 集群（mock 包 + FileDiffApplier + Media 等）

- Classification: `watch-only residual`
- Why Not Blocking Closure: audit（MA3.4）scope 仅点名 IAE（P3-MA3-2），ISE 未被审计点名；mock 包 ISE 属测试辅助语义，FileDiffApplier/Media 的 ISE 为状态类异常，无 audit finding 归属。登记为 watch-only，不阻塞本计划 closure。
- Successor Required: `no`

### 极少数 JDK 接口契约 IAE（若 Phase 1 发现）

- Classification: `watch-only residual`（若存在）
- Why Not Blocking Closure: 实现 JDK 接口契约时强制 IAE；此类保留不构成规范漂移。
- Successor Required: `no`

## Non-Blocking Follow-ups

- nop-ai-agent/nop-ai-shell 由兄弟计划 `2026-08-01-0936-1-arm-p3-error-handling-agent-shell.md` 承接；nop-ai-tools/nop-ai-toolkit 由 `2026-08-01-0936-3-arm-p3-error-handling-tools-toolkit.md` 承接（模块 main 面不重叠，可与本计划并行；共享 `NopAiCoreErrors.java` 时按 Related 段串行约束追加）
- error-handling.md 新增业务错误码语言默认规则（中文 vs agent/shell/core 英文例外）的存量 doc drift 登记，另作文档治理规划

## Closure

Status Note: 全部 4 个 Phase 完成，独立 closure audit（fresh session，非实现 session）8/8 检查项 PASS 后关闭。nop-ai-core 7 处 + nop-ai-coder 4 处裸 IAE 全部转换为 NopException + 模块 ErrorCode（英文描述，消息逐字零漂移，cause 保持）；两模块 main grep 0 残留；测试断言全同步 + 4 个 focused 值级测试文件（+9 @Test 零减少）；arm-index（P3-MA3-2/P3-MA3-3 → fixed）与 roadmap（第十二批 0936-2 行 ✅ + header v13）已登记。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: general subagent（fresh closure-audit session，非实现 session）
- Audit Session: `ses_044b2c818ffekw83xKAOEikC2t`
- Evidence:
  - 8/8 检查项 PASS：① Phase 1-3 转换面与 plan baseline 一致（core 7/6、coder 4/3，git diff 逐处核对），两模块 main grep `throw new IllegalArgumentException|throw new RuntimeException` = 0 命中（core 转换后落点 FileDiffGenerator.java:217/PromptModel.java:89/ModelBasedPromptTemplate.java:246/AiMessage.java:55/CosineSimilarity.java:51/ChatLogHelper.java:70/73；coder JavaMethodReplacer.java:30/40/AiXdefDocumentConverter.java:26/AiOrmDocumentConverter.java:33）② 消息逐字零漂移（AI 消息 no-space `unknown role:{role}`、converter `Unsupported conversion:{from}->{to}`、CosineSimilarity 双空格逐字、ChatLogHelper 双 guard 静态 desc + ARG_SESSION_ID）③ Anti-Hollow：4 个 focused 测试文件值级断言（errorCode/message/param）实测 8/8 pass，2 个既有文件更新后 20/20 pass，grep test 目录 IAE 断言 0 残留，@Test core +5 / coder +4 零减少 ④ catch-IAE 调用面 0 命中 ⑤ 构建证据：executor 记录 clean install + 全量 test + scan-hollow exit 0 + check-doc-links exit 0；auditor 独立复核 `test-compile -pl nop-ai-core,nop-ai-coder` exit 0 ⑥ arm-index:488-497 P3-MA3-2/P3-MA3-3 `fixed` 行 + roadmap header v13 + :265 0936-2 行 ✅ ⑦ deferred 分类诚实（ISE 集群 8 处 watch-only residual 显式登记，无 in-scope live defect 静默降级）⑧ 文本一致性（4 Phase completed、全 checklist 勾选、Closure 证据写入）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-01-0936-2-arm-p3-error-handling-core-coder.md --strict` 退出码 0（最终核对）
  - Anti-Hollow 检查结果：转换后异常运行时可达性经 focused 测试证明（TestPromptErrorCodeConversion/TestAiMessageErrorCode/TestFileDiffGeneratorErrorCode reflection/TestCoderErrorCodeConversion 值级断言 + 28/28 实测绿）；`scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0
  - Deferred 项分类检查：nop-ai-core ISE 集群（mock 包 + FileDiffApplier + Media 8 处）为 audit 未点名的 watch-only residual（audit scope 仅 IAE），JDK 契约保留类别 Phase 1 裁定保留数 = 0，均 non-blocking，无 in-scope defect 被降级

Follow-up:

- nop-ai-tools/nop-ai-toolkit 由兄弟计划 `2026-08-01-0936-3-arm-p3-error-handling-tools-toolkit.md` 承接（共享 `NopAiCoreErrors.java` 编辑面时按 Related 段串行约束追加）
- error-handling.md 新增业务错误码语言默认规则（中文 vs agent/shell/core 英文例外）的存量 doc drift 登记，另作文档治理规划
- 变更未提交（11 modified + 4 new files），随本计划收口提交（nop-git-master skill）

## Optional Sections

## Risks And Rollback

- 异常类型变化影响 catch IAE 调用点——Phase 1 盘点，预计 0。回滚 = 单 commit revert。
- 消息语义漂移风险——转换逐字保持消息文本，closure audit 抽查。
