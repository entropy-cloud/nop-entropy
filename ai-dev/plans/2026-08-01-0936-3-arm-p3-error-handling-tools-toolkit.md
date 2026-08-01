# 3 arm-p3-error-handling-tools-toolkit — 错误处理规范收口：nop-ai-tools + nop-ai-toolkit 裸 IAE/RTE → NopException + ErrorCode（P3-MA3-5 + 同规范 toolkit 残余）

> Plan Status: active
> Last Reviewed: 2026-08-01
> Source: `ai-dev/audits/2026-07-31-0423-arm-MA3.4-nop-ai-error-handling.md`（P3-MA3-5）+ `docs-for-ai/02-core-guides/error-handling.md` + `ai-dev/audits/arm-index.md`
> Mission: audit-remediation
> Work Item: P3-MA3-5（第十二批 deferred successor 重开）+ nop-ai-toolkit 同规范残余
> Related: `2026-08-01-0936-1-arm-p3-error-handling-agent-shell.md`、`2026-08-01-0936-2-arm-p3-error-handling-core-coder.md`（同批次兄弟计划，模块面互不重叠可并行；登记顺序 = 执行顺序）

## Purpose

把 MA3.4 错误处理审计中 nop-ai-tools（P3-MA3-5：FileToolBizModel IAE）的裸 IAE 收口，并顺带清理 nop-ai-toolkit 中同规范残留（LocalToolFileSystem 9 处 IAE——audit 未单独列项但属同一错误处理规范缺口，且 toolkit 已直接依赖 nop-ai-api、`DefaultToolExecutorProvider` 已有 `NopAiException` 使用先例）。全部转换为 `NopException` + ErrorCode（英文消息保持）。

## Current Baseline

- **MA3.4 审计结论**：P3-MA3-5（FileToolBizModel IAE，audit 复核 Retain (P3)："Confirmed in protected method getProjectDir()"）。
- **批次史**：与 P2-MA3-1 同批被 1834-3 以 "P3 项" 排除，从未重开或单独裁定。
- **live 现状（grep 复核 2026-08-01）**：
  - `nop-ai-tools`：**7 处**（4 文件）：FileToolBizModel.java:237（"projectName must be valid file directory name:..."，audit 原文写 212，live 为 237，以 live 为准）、ThoughtData.java ×4、ThoughtStage.java、ThoughtAnalyzer.java（后三者 audit 未单独列项，属同 finding 类别的同模块残余，与 toolkit 同规范理由一并显式纳入）。
  - `nop-ai-toolkit`：**9 处**（1 文件）：LocalToolFileSystem.java:54/92/112/141/168/225/229/244/248（"Path not allowed"/"File not found"/"Directory not found"/"Target file already exists"——注意：`isPathAllowed` 路径校验是 MA6.2-003（arm-index 记 MA6.2 fixed/MR3）的修复面，转换时保持判定逻辑不变，仅异常类型变化）。
  - 基础设施：tools **无直接依赖 nop-ai-api**（pom 直接依赖 nop-ai-core/coder/graphql-core/biz；NopAiException 仅经 nop-ai-core 传递可用）；tools 模块已有先例 `SequentialThinkingBizModel`（P1-MA3-2 修复）使用 `NopException + NopAiCoreErrors` 的码（`ERR_AI_TOOLS_INVALID_THOUGHT` = `nop.err.ai.tools.invalid-thought`）——即"tools 复用 core 的 Errors 类、`nop.err.ai.tools.*` 前缀已在 NopAiCoreErrors 注册"。toolkit 直接依赖 nop-ai-api 且有 `NopAiException` 使用先例（DefaultToolExecutorProvider:51），LocalToolFileSystem 目前 import `NopException`（nop-api-core）。
  - 测试断言影响面：nop-ai-tools 0、nop-ai-toolkit 0 个测试文件引用 IAE（grep 复核）。
- **同规范先例**：1834-3（nop-ai-maven 13 处 IAE → NopException + NopAiMavenErrors）；2248-2（25 UOE → NopException + ErrorCode）。
- **绿色基线**：`ai-dev/logs/2026/08-01.md` 全量 test BUILD SUCCESS；scan-hollow exit 0。

## Goals

- nop-ai-tools 7 处 + nop-ai-toolkit 9 处裸 IAE 全部转换为 `NopException` + ErrorCode（tools 候选载体：扩展 `NopAiCoreErrors`（沿用 SequentialThinkingBizModel 先例，`nop.err.ai.tools.*` 前缀已在 core 注册）**或**新建模块 Errors 类 **或**复用 `NopAiException`——以处置表裁定为准；toolkit 复用 `NopAiException` 或新增 toolkit Errors 或复用 CommonErrors 文件错误码——以处置表裁定为准），转换后 grep 对应模块 main `throw new IllegalArgumentException|RuntimeException` = 0
- `LocalToolFileSystem.isPathAllowed` 安全判定逻辑零变更（P1-MA6.2-003 修复面不受影响）
- 消息语义与 cause 零变更；新增 focused 测试（tools/toolkit 当前 0 引用 IAE 断言，需为转换路径补值级测试）
- arm-index 登记第十二批

## Non-Goals

- 不处理 nop-ai-agent/nop-ai-shell（0936-1）与 nop-ai-core/nop-ai-coder（0936-2）——兄弟计划承接
- 不重开 P3-MA1-023~030、P3-MA1-039、MA4.2-01/-14
- 不改变 `LocalToolFileSystem.isPathAllowed` 的路径判定语义（仅异常类型转换）
- 不做消息内容优化或 i18n 化

## Scope

### In Scope

- `nop-ai/nop-ai-tools/src/main/java/` 下全部裸 IAE（预期 7 处/4 文件，含 audit 点名 FileToolBizModel:237 与同模块 ThoughtData/ThoughtStage/ThoughtAnalyzer）
- `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java` 9 处 IAE（同规范残余，audit 未单独列项——本计划显式纳入并记录理由）
- 相关测试断言更新 + 新增 focused 测试
- 异常载体裁定（扩展 `NopAiCoreErrors` / 新建 Errors 类 / `NopAiException` / CommonErrors 文件码）+ arm-index/roadmap 登记

### Out Of Scope

- 非 nop-ai 模块代码；其它模块 IAE（兄弟计划）
- `LocalToolFileSystem` 路径判定逻辑本身（安全语义，保持不动）
- 既有 catch IAE 调用点改造（Phase 1 盘点，预计 0）

## Execution Plan

### Phase 1 - 盘点与逐处裁定

Status: planned
Targets: 两模块 main grep + tools/toolkit pom 依赖确认

- Item Types: `Decision | Proof`
- [ ] grep 全量盘点 nop-ai-tools（预期 7 处/4 文件）与 nop-ai-toolkit（预期 9 处/1 文件）裸 IAE 清单（文件:行号:消息），输出逐处处置表（转换目标：ErrorCode 或 NopAiException）
- [ ] 裁定 tools 异常载体（三选项，理由入处置表）：(a) 扩展 `NopAiCoreErrors`（沿用 SequentialThinkingBizModel 先例——`ERR_AI_TOOLS_INVALID_THOUGHT` 已在 core 注册 `nop.err.ai.tools.*` 前缀；**注意：若选 (a)，与兄弟计划 0936-2 共享 `NopAiCoreErrors.java` 编辑面，需串行执行或仅在 core 转换完成后追加码，避免并行写冲突**）；(b) 新建模块 Errors 类（注意与 core 已注册的 `nop.err.ai.tools.*` 前缀并存属同前缀跨类重复风险，需显式规避）；(c) 复用 `NopAiException`（nop-ai-api 经 nop-ai-core 传递可用）
- [ ] 裁定 toolkit 载体：复用 `NopAiException`（DefaultToolExecutorProvider 先例）vs 新增 toolkit Errors vs 复用 `nop-commons.CommonErrors.ERR_FILE_*`（LocalFileOperator 已有跨模块文件错误先例，LocalToolFileSystem 同为工具文件 API）——理由入处置表
- [ ] 逐处裁定分类：(a) 校验/非法状态类 → 转换；(b) JDK 接口契约必须抛 IAE 的情形 → 显式裁定保留并记录理由
- [ ] 盘点 `catch (IllegalArgumentException` 调用面（预期 0），输出影响面清单；确认 `LocalToolFileSystem.isPathAllowed` 调用链在转换后判定逻辑不变
Exit Criteria:

- [ ] 逐处处置表落盘 daily log，两模块 IAE 总数与 live grep 一致
- [ ] tools/toolkit 异常载体裁定完成（含扩展 core Errors 与共享编辑面约束决定）
- [ ] catch 调用面清单完成（0 命中则记录 0）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - nop-ai-tools 转换（7 处）

Status: planned
Targets: `nop-ai/nop-ai-tools/src/main/java/`（4 文件）+ 新 Errors 类（如裁定）

- Item Types: `Fix`
- [ ] 按处置表将 7 处裸 IAE 转换为 `NopException` + ErrorCode（或 `NopAiException`），消息语义逐字保持，cause 保留
- [ ] 按 Phase 1 载体裁定落地：扩展 `NopAiCoreErrors`（沿用 `ERR_AI_TOOLS_INVALID_THOUGHT` 先例，新增码仍用 `nop.err.ai.tools.*` 前缀；若与 0936-2 共享该文件，按串行约束追加）或新建 Errors 类（规避同前缀跨类重复）或复用 `NopAiException`——裁定记录落盘
- [ ] 新增 focused 测试（值级断言：错误码/消息），覆盖每类新码至少 1 条

Exit Criteria:

- [ ] `grep -rn "throw new IllegalArgumentException\|throw new RuntimeException" nop-ai/nop-ai-tools/src/main/java` = 0 命中（JDK 契约保留项若存在则显式列出）
- [ ] 新 Errors 类/复用/扩展 core Errors 裁定落盘；focused 测试值级断言通过；测试数量不减少
- [ ] `./mvnw test -pl nop-ai/nop-ai-tools -am -T 1C` BUILD SUCCESS
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - nop-ai-toolkit 转换（9 处）

Status: planned
Targets: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java`

- Item Types: `Fix`
- [ ] 将 LocalToolFileSystem 9 处裸 IAE 转换为 `NopException` + ErrorCode 或 `NopAiException` 或 `CommonErrors.ERR_FILE_*`（按 Phase 1 裁定；`isPathAllowed` 判定逻辑、调用链零变更），消息语义逐字保持
- [ ] 新增 focused 测试（值级断言：路径拒绝场景错误码/消息；参照既有 LocalToolFileSystem 测试补强）

Exit Criteria:

- [ ] `grep -rn "throw new IllegalArgumentException\|throw new RuntimeException" nop-ai/nop-ai-toolkit/src/main/java` = 0 命中
- [ ] focused 测试值级断言通过；测试数量不减少；`isPathAllowed` 判定语义未变（对照既有安全测试）
- [ ] `./mvnw test -pl nop-ai/nop-ai-toolkit -am -T 1C` BUILD SUCCESS
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 全量验证 + 登记

Status: planned
Targets: `nop-ai` 全模块 + `arm-index.md` + `ai-dev/backlog/audit-remediation-roadmap.md`

- Item Types: `Proof`
- [ ] `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS + `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（全量回归，0 failures）
- [ ] `scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [ ] arm-index 登记 P3-MA3-5 → fixed + toolkit 同规范残余（计数+证据）；roadmap 登记第十二批

Exit Criteria:

- [ ] 全量 build + 全量 test 绿
- [ ] scan-hollow exit 0；check-doc-links exit 0
- [ ] arm-index 与 roadmap 登记完成
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] tools 7 处 + toolkit 9 处裸 IAE 全部转换或显式裁定保留（处置表齐全），两模块 main grep 0 残留
- [ ] 异常载体裁定（扩展 core Errors / 新建 Errors / NopAiException / CommonErrors）落盘；ErrorCode 英文描述、无前缀冲突、无重复；消息语义与 cause 零变更
- [ ] `isPathAllowed` 安全判定逻辑与调用链零变更（对照既有安全测试）
- [ ] 受影响测试断言同步且新增 focused 测试（值级断言），测试数量零减少
- [ ] 不存在被静默降级的 in-scope live defect
- [ ] arm-index 与 roadmap 已登记
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证转换后异常运行时可达（抽查 throw 路径 + focused 测试断言）
- [ ] `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（Phase 4 已执行，此处为 closure 复核）

## Deferred But Adjudicated

### 极少数 JDK 接口契约 IAE（若 Phase 1 发现）

- Classification: `watch-only residual`（若存在）
- Why Not Blocking Closure: 实现 JDK 接口契约时强制 IAE；此类保留不构成规范漂移。
- Successor Required: `no`

## Non-Blocking Follow-ups

- nop-ai-agent/nop-ai-shell 由兄弟计划 `2026-08-01-0936-1-arm-p3-error-handling-agent-shell.md` 承接；nop-ai-core/nop-ai-coder 由 `2026-08-01-0936-2-arm-p3-error-handling-core-coder.md` 承接（模块面不重叠，可并行）

## Closure

Status Note: （关闭时填写）
Completed: （关闭时填写）

Closure Audit Evidence:

（关闭时由独立子 agent 填写）

Follow-up:

（关闭时填写）

## Optional Sections

## Risks And Rollback

- 异常类型变化影响 catch IAE 调用点——Phase 1 盘点，预计 0。回滚 = 单 commit revert。
- `LocalToolFileSystem` 为工具安全边界，异常转换不得触碰路径判定逻辑——closure audit 对照既有安全测试验证。消息语义漂移风险——转换逐字保持，closure audit 抽查。
