# 2399 full-code-review R1.33 — nop-ai 方言与工具面修复批（P1-6.7-001..004）

> Plan Status: active
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

Status: planned
Targets: 上列文件

- Item Types: `Fix`

- [ ] AnthropicDialect.getRole：移除 assistant→model 映射（assistant 原样保留）；TestAnthropicDialect :394 断言同步为 "assistant"
- [ ] OpenAiDialect/GeminiDialect/OllamaDialect：ChatToolCallMessage 轮跳过 content 置入（ResponsesDialect 范本——工具调用轮由 tool_calls 字段承载，不重复参数 JSON 到 content）
- [ ] 四方言 ChatReasoningMessage：消除 reasoning 双发（ResponsesDialect 范本形态）
- [ ] LocalToolFileSystem.readLines：totalLines 用 countLines 真实总行数（删除无用数组）
- [ ] LocalToolFileSystem.grep：path 为文件时直接 grepInFile
- [ ] 测试：①TestAnthropicDialect 断言更新（assistant 原样）②TestDialectBidirectionalConversion 或新用例——工具轮回放等价断言 ③readLines totalLines 断言 ④grep 文件路径用例
- [ ] **判别验证（四腿 stash）**：各修 stash 后对应测试 FAIL，恢复后绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 四修全落地
- [ ] 判别测试四腿绿 + 既有测试零回归
- [ ] nop-ai-core/nop-ai-toolkit 测试全绿
- [ ] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: planned
Targets: `docs-for-ai/03-modules/nop-ai.md`

- Item Types: `Fix`

- [ ] nop-ai.md：方言契约注记（Anthropic role 语义、工具轮 content 跳过、reasoning 单发、工具面 totalLines/grep 契约）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] roadmap R1.33 翻 done；index P1-6.7-001..004 → fixed；daily log 条目

Exit Criteria:

- [ ] owner doc 条目在位且与 live 行为一致
- [ ] doc-links 检查退出码 0
- [ ] 收口动作落盘

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（P1-6.7-001..004）
- [ ] 行为/契约结果已达成（判别四腿 + 零回归）
- [ ] 必要 focused verification 已完成（nop-ai-core/nop-ai-toolkit 测试全绿）
- [ ] 不存在被静默降级的 in-scope live defect
- [ ] 受影响 owner docs 已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-toolkit` 全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- CL-1 thinking block signature 残余（audit 同批注记——若 001 修复后自然消解则闭环，否则归后续）
- 方言新增能力（非缺陷面）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

-
