# 2399 full-code-review R1.33 — nop-ai 方言与工具面修复批（P1-6.7-001..004）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.33；findings P1-6.7-001..004（6.7-check-p1-batch-c.md），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.7-check-p1-batch-c.md`
> Related: plan 2284（AnthropicDialect tool_result 轮修复——异根面）；TestDialectBidirectionalConversion:38（落档自证）
> Owner Doc: `docs-for-ai/03-modules/nop-ai.md`

## Purpose

收口 nop-ai 方言与工具面四条 P1：①AnthropicDialect.getRole 把 assistant 映射为 "model"——多轮对话请求必 400；②三方言工具轮请求体被参数 JSON 污染 + 四方言 reasoning 双发；③LocalToolFileSystem.readLines totalLines 填请求值（假总行数）；④LocalToolFileSystem.grep 对文件路径输入静默返回空（假阴性）。

## Current Baseline

- **P1-6.7-001**：`AnthropicDialect.getRole:748-750` 把 assistant 映射为 "model"——Anthropic Messages API 请求侧只接受 user/assistant（model 仅出现在响应侧），多轮对话第二轮起必 400。TestAnthropicDialect:394 断言把缺陷行为钉死。
- **P1-6.7-002**：OpenAiDialect:418,425-432 / GeminiDialect:532-537,545-554 / OllamaDialect:383,389-396 工具轮请求体把 ChatToolCallMessage 的 arguments JSON 无条件置入 content/text part（3/4 方言）；ChatReasoningMessage 同时发裸文本 + thinking 载荷（4/4 方言 reasoning 双发）。ResponsesDialect 为正确范本。
- **P1-6.7-003**：`LocalToolFileSystem.readLines:143`——`new LineResult(path, lines.isEmpty() ? 0 : toLine, …)` 填请求值 toLine 而非真实总行数；`int[] totalLines={0}` :126 声明未用。
- **P1-6.7-004**：`LocalToolFileSystem.grep:353-355`——`if (!dir.exists() || !dir.isDirectory()) return result;` 对文件路径输入静默返回空。
- **测试基建**：TestAnthropicDialect / TestDialectBidirectionalConversion / SearchContentExecutorTest 已有。

## Goals

- getRole 移除 assistant→model 映射；TestAnthropicDialect 断言同步。
- 三方言工具轮跳过 content 置入（ResponsesDialect 范本）；四方言 ChatReasoningMessage 不再双发（仅发 thinking 载荷或裸文本其一—— ResponsesDialect 范本形态）。
- readLines totalLines 用真实总行数（countLines）；grep 对文件路径直 grepInFile。
- 判别测试四腿 stash 实证；nop-ai-core/nop-ai-toolkit 测试全绿。

## Non-Goals

- P1-6.7-005 ReActAgentExecutor（R1.34）；CL-1 thinking block signature 残余（audit 同批注记，随 001 修复自然消解则一并、否则归后续）；方言新增能力。

## Scope

### In Scope

- `AnthropicDialect.getRole` 修正 + TestAnthropicDialect 断言同步
- `OpenAiDialect`/`GeminiDialect`/`OllamaDialect` 工具轮 content 跳过 + 四方言 reasoning 双发消除
- `LocalToolFileSystem.readLines` totalLines 修正 + `grep` 文件路径分支
- 测试：四腿判别
- owner doc：nop-ai.md 方言契约注记

### Out Of Scope

- P1-6.7-005（R1.34）；ResponsesDialect 面；新方言能力

## Execution Plan

### Phase 1 - 四修 + 判别测试

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] AnthropicDialect.getRole：移除 assistant→model 映射（assistant 原样保留）；TestAnthropicDialect :394 断言同步为 "assistant"
- [x] OpenAiDialect/GeminiDialect/OllamaDialect：ChatToolCallMessage 轮跳过 content 置入（ResponsesDialect 范本——工具调用轮由 tool_calls 字段承载，不重复参数 JSON 到 content）——**已全部落地**：OpenAi `:419-427`、Ollama `:384-392` 不再置 content；Gemini `:537-539` 工具轮不再加 text part（functionCall 由 `:549-558` 承载）
- [x] 四方言 ChatReasoningMessage：消除 reasoning 双发（ResponsesDialect 范本形态）——**已完成（含 Anthropic 第 4 方言）**：`AnthropicDialect.java:700-708` 现仅产出 thinking block（2026-10-10 补修；原 `:700-715` 先 text 后 thinking 的双发已移除）；OpenAi `:428-430`、Gemini `:532-536`、Ollama `:393-395` 均单发
- [x] LocalToolFileSystem.readLines：totalLines 用 countLines 真实总行数（删除无用数组）
- [x] LocalToolFileSystem.grep：path 为文件时直接 grepInFile
- [x] 测试：①TestAnthropicDialect 断言更新（assistant 原样）②新增 `TestDialectBidirectionalConversion.testRequestBuildBody_anthropicReasoningEmitsOnlyThinkingBlock`（:81-102，断言恰 1 个 content block 且 type=thinking）③readLines totalLines 断言 `TestLocalToolFileSystemP1Fixes.testReadLinesTotalLinesIsRealCount` ④grep 文件路径用例 `TestLocalToolFileSystemP1Fixes.testGrepOnFilePathSearchesFile`
- [x] **判别验证（四腿）**：001/003 有在案 stash 记录；002 与 004 由本轮独立 closure audit 亲跑 patch-run 复核——临时还原 Anthropic 双发后 `testRequestBuildBody_anthropicReasoningEmitsOnlyThinkingBlock` FAIL（`expected: <1> but was: <2>`）；临时移除 grep 文件路径分支后 `testGrepOnFilePathSearchesFile` FAIL（`grep on a file path must search the file... expected: <false> but was: <true>`）；均恢复后绿
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 四修全落地（002 含 Anthropic reasoning 半边，2026-10-10 补修）
- [x] 判别测试四腿绿 + 既有测试零回归（002/004 本轮 patch-run 实证判别；001/003 在案记录）
- [x] nop-ai-core/nop-ai-toolkit 测试全绿
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-ai.md`

- Item Types: `Fix`

- [x] nop-ai.md：方言契约注记（Anthropic role 语义、工具轮 content 跳过、reasoning 单发、工具面 totalLines/grep 契约）——**已一致**：`docs-for-ai/03-modules/nop-ai.md:73-77`「LLM 方言请求序列化契约」与 live 逐条吻合（:77 Anthropic「thinking block」单发陈述经 2026-10-10 补修后成立）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.33 翻 done；index P1-6.7-001..004 → fixed；daily log 条目（收口动作已落盘；当前 `roadmap:164` 与 `index:153` 因上一轮 Cannot Close 被 Lead 置为 `in progress`——由 Lead 在本轮 closure 后翻 done/fixed，属 Lead 处置域）

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-6.7-001..004，含 P1-6.7-002 Anthropic reasoning 单发补修）
- [x] 行为/契约结果已达成（判别四腿 + 零回归）
- [x] 必要 focused verification 已完成（nop-ai-core/nop-ai-toolkit 测试全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（2026-10-10 重审 Can Close）
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-toolkit` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Prior Cannot Close (2026-10-10 独立 closure audit) — Resolved

上一轮独立 closure audit 曾判定 **Cannot Close**：P1-6.7-002 的 Anthropic 请求侧 reasoning 双发仍 live、owner-doc drift、缺请求侧回归测试。上述三项已于同日补修并经本轮独立重审确认闭环：

- **P1-6.7-002 Anthropic reasoning 单发（已修）**：`AnthropicDialect.java:700-708` 现对 `ChatReasoningMessage` 仅产出 `thinking` 块，原 `:700-715` 先 text 后 thinking 的双发已移除。
- **请求侧回归测试（已补）**：`TestDialectBidirectionalConversion.testRequestBuildBody_anthropicReasoningEmitsOnlyThinkingBlock`（:81-102）断言恰 1 个 content block 且为 thinking；本轮 patch-run 实证判别（还原双发 → `expected: <1> but was: <2>`）。
- **owner-doc drift（已消解）**：`docs-for-ai/03-modules/nop-ai.md:77` 现与 live 一致（Anthropic thinking block 单发成立）。
- **roadmap/index**：`ai-dev/backlog/full-code-review-roadmap.md:164` 与 `ai-dev/audits/.../index.md:153` 当前仍为 `in progress`（上一轮 Cannot Close 后的状态）；由 Lead 在本轮 closure 后翻 done/fixed——属 Lead 处置域，本 audit 不修改。

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- CL-1 thinking block signature 残余（audit 同批注记——若 001 修复后自然消解则闭环，否则归后续）
- 方言新增能力（非缺陷面）

## Closure

Status Note: P1-6.7-001（Anthropic getRole 保持 assistant）、P1-6.7-002（四方言工具轮 content 跳过 + 四方言 reasoning 单发，Anthropic 第 4 方言于 2026-10-10 补修）、P1-6.7-003（readLines totalLines 真实总行数）、P1-6.7-004（grep 文件路径分支）经独立 closure audit 对 live 代码逐条复核全部闭环；判别四腿（001/003 在案 + 002/004 本轮 patch-run 亲证）、owner doc 与 live 一致、focused verification 全绿、无 in-scope live defect 降级。上一轮 Cannot Close 的 Anthropic reasoning 双发已修复。唯一未决为 Lead 处置域的 roadmap/index 记账翻账（本 audit 按约束不修改），不影响技术契约。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit (subagent)，未参与实现
- Audit Session: 独立 closure-audit 子会话（parent session-9601d130-1bfd-4608-86de-471049fd6c0e）
- Evidence:
  - **P1-6.7-001 PASS**：`AnthropicDialect.getRole`（:745-753）对非 tool_response 返回 `getBaseRole` 原值（无 assistant→model）；`TestAnthropicDialect.java:393` 断言 `turnList.get(1).get("role")=="assistant"`；`TestAnthropicDialect` 18/18 绿。
  - **P1-6.7-002 PASS**：
    - 工具轮 content 跳过：OpenAi `:419-427`、Ollama `:384-392` 不再置 content；Gemini `:537-539` 工具轮不加 text part（functionCall 由 `:549-558` 承载）。
    - reasoning 单发：Anthropic `:700-708` 仅 thinking 块；OpenAi `:428-430`、Ollama `:393-395`、Gemini `:532-536` 各自单发。
    - 回归：`TestDialectBidirectionalConversion.testRequestBuildBody_anthropicReasoningEmitsOnlyThinkingBlock`（:81-102）；类注释 :39-41 已更新为「002 由 dedicated 用例覆盖」。本轮 patch-run：还原双发 → FAIL `expected: <1> but was: <2>`；恢复后 21/21 绿。
  - **P1-6.7-003 PASS**：`LocalToolFileSystem.readLines`（:127-146）`int realTotalLines = countLines(path, Integer.MAX_VALUE)`（:130），`LineResult` 以之填 totalLines（:145），无用数组已删；`TestLocalToolFileSystemP1Fixes.testReadLinesTotalLinesIsRealCount`（10 行文件请求 1-2 → totalLines=10）。
  - **P1-6.7-004 PASS**：`LocalToolFileSystem.grep`（:357-361）`if (dir.exists() && dir.isFile()) { grepInFile(...); return; }`；`testGrepOnFilePathSearchesFile`（文件路径 2 命中）。本轮 patch-run：移除该分支 → FAIL `expected: <false> but was: <true>`；恢复后绿。
  - **focused verification PASS**：`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-toolkit -am -T 1C` → BUILD SUCCESS，exit 0。nop-ai-core 全绿（`TestAnthropicDialect` 18/18、`TestDialectBidirectionalConversion` 21/21——较上轮 20/20 +1 新回归）；nop-ai-toolkit 全绿（`TestLocalToolFileSystemP1Fixes` 2/2）。
  - **Anti-Hollow PASS**：`scan-hollow-implementations.mjs --module nop-ai-core` / `--module nop-ai-toolkit`（--severity high）均退出码 0（0 findings）；请求侧 reasoning 单发路径经本轮 patch-run 亲证为真实行为载体，非 interface-present/semantics-missing。
  - **owner doc PASS**：`docs-for-ai/03-modules/nop-ai.md:73-77` 与 live 逐条一致（:77 Anthropic thinking block 单发成立）；`check-doc-links.mjs --strict` 退出码 0。
  - **Deferred 分类检查 PASS**：`## Deferred But Adjudicated`（无）；`Non-Blocking Follow-ups` 仅 CL-1 signature 残余与方言新增能力，无 in-scope live defect 被降级。
  - **Closure Gate**：全部 PASS（技术契约、owner doc、独立 audit、focused verification、check-plan-checklist）。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2399-full-code-review-R1.33-ai-dialect-fixes.md --strict`：退出码 0（`completed` 且无未勾选项、Closure evidence 齐备）。

Follow-up:

- Lead 处置域：`ai-dev/backlog/full-code-review-roadmap.md:164` R1.33 翻 done、`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/index.md:153` P1-6.7-002 → fixed（当前仍 `in progress`，属上一轮 Cannot Close 后的状态；本 audit 不修改该两文件）。
- CL-1 thinking block signature 残余：随 001 修复自然消解，watch-only。
