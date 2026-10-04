# 18 WI18 静默违约门禁化

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI18，deps WI6✓；对应 MSG-F-01/F-03/F-53 的形态防复发）
> Related: `ai-dev/tools/check-silent-wrong-result.mjs`（既有协议：baseline 子集调和 + invariant-ok 裁决 + fixture 自检）

## Purpose

扩展 `ai-dev/tools/check-silent-wrong-result.mjs`：新增 message 域规则族（`msg-pre-poll-seek` / `msg-stub` / `msg-warn-per-call`），沿用既有 baseline 子集调和与 `// invariant-ok:` 裁决协议；接入 `ai-dev/tools/package.json` 与 `.github/workflows/compliance.yml`；提供正反双向 self-test；实测能捕获 MSG-F-01 / MSG-F-03 / MSG-F-53 三类**形态**（以 fixture 再现缺陷形状——三缺陷本体已于 WI7/WI8/WI6 修复，本门禁防复发）。

## Current Baseline

- 既有检查器：5 规则 × 统一 hit 结构（file/line/family/snippet/rule）、注释与字符串掩码、`--module` 目录过滤、`--baseline` 子集调和（count-aware：current ≤ baseline 绿）、`// invariant-ok:` 行裁决（移出违规集与 baseline 输入但保留可见）、`--fixture` 自检（正反双向：violation/compliant 成对 + allowed-comment + baseline reconcile）。
- roadmap 实测：该工具全仓退出 1。**审查 B1 实测：`--module nop-message` 现状退出 1，存量 4 条命中在 nop-message 内**：① DebeziumEngineConfig.java:74/:159/:221（locale ×3，无参 toLowerCase）；② KafkaConsumeTask.java:293（delim-key——WI16 的 failureAttempts 复合串键）。处置：① 修复为 `toLowerCase(Locale.ROOT)`；② `// invariant-ok:` 裁决（复合串键为有意设计，计数用途无分类语义，引用 WI16 plan）。
- F-01/F-03/F-53 已修复（WI7/WI8/WI6）；WI33 charter 已扩展慢 handler 踢出场景。
- F-01/F-03/F-53 已修复（WI7/WI8/WI6）；WI33 charter 已扩展慢 handler 踢出场景。
- CI：`.github/workflows/compliance.yml` 与 `maven.yml` 在位；`package.json` scripts 为登记点。

## Goals

- **三新规则（scoping 在编排层：scan 函数内 `relPath` 含 `nop-message` 或以 `fixture/nop-message/` 开头才生效——审查 M1，保证 fixture 可触发）**：
  1. `msg-pre-poll-seek`（INV-MSG-PRE-POLL-SEEK，F-01 形态；审查 M2 操作性定义）：类内存在 poll/receive（`consumer.poll(` / `pulsarConsumer.receive()`）且 `start()` 方法**花括号 span 内**（含 lambda，`/\bseek\w*\s*\(/` token 匹配）出现 seek 系调用 → hit。
  2. `msg-stub`（INV-MSG-STUB，F-03 形态；审查 B2 窗口修正）：方法体为空（仅空白/注释）且 **签名前 3 行 / 体内 / 闭合后 3 行**任一位置出现 `// TODO` / `// FIXME` → hit（真实 F-03 的 TODO 在签名上方）。
  3. `msg-warn-per-call`（INV-MSG-WARN-PER-CALL，F-53 形态）：方法名 ∈ {sendAsync, subscribe, consume, batchConsume, pollAndConsume, runTask} 体内出现 `LOG.warn(`，且该行与文件内均无 `warnOnce` 关联（行内或前 2 行含 `warnOnce` 豁免），且 warn 行不含终态 allow 标记（`terminate-consume` / `failure-callback-throw`；raw 文本匹配——masked 会清空消息串）。
- **fixture 正反双向**：每规则 violation + compliant 成对（新规则 fixture 路径约定 `fixture/nop-message/<label>`）；allowed-comment 与 baseline reconcile 既有机制对新规则生效（self-test 增补用例）。
- **存量处置**：DebeziumEngineConfig 3 处 locale 修复（Locale.ROOT）；KafkaConsumeTask failureAttempts 键行加 `// invariant-ok:` 裁决（引用本 plan）。
- **族名映射（审查 M3）**：roadmap「message-options 规则族与 warn-once 规则族」两族映射为本三规则——msg-pre-poll-seek/msg-stub 属「message-options 域缺陷形态」，msg-warn-per-call 即 warn-once 族；roadmap 「对应 MSG-F-46」由 WI4/WI32 契约测试承载，非本 plan。
- **实测三形态捕获**：fixture 样本分别再现 F-01/F-03/F-53 缺陷形状并断言命中；`--module nop-message` 实跑退出 0（无残留）。
- **接入**：package.json scripts（`check:silent-wrong-result` / `:module` 变体）+ compliance.yml 增加调用步骤（对 nop-message 模块跑新规则族）。

## Non-Goals

- 不清其他模块的存量违规（baseline 机制已支持渐进修复）。
- 不做跨文件数据流分析（规则为行/方法级启发式；复杂形态归 code review 与 WI33）。

## Scope

### In Scope

- `ai-dev/tools/check-silent-wrong-result.mjs`（扩展）、`ai-dev/tools/package.json`、`.github/workflows/compliance.yml`、日志、roadmap WI18 状态行。

### Out Of Scope

- 其他模块存量违规修复；WI22/WI33。

## Execution Plan

### Phase 1 - 规则族与接入

Status: completed
Targets: `ai-dev/tools/`

- Item Types: `Feature`

- [x] 三规则实现（编排层 scoping：relPath 含 nop-message 或 fixture/nop-message/ 前缀）+ RULES/INV_BY_RULE 登记 + generateSummary 标题 + fixture 正反用例（F-01 形状含 lambda 内 seek；F-03 形状 TODO 在签名上方）；文件头规则清单已更新（审计 Minor-2 处置）
- [x] 存量处置：DebeziumEngineConfig 3 处 Locale.ROOT 修复；KafkaConsumeTask failureAttempts 行 invariant-ok 裁决
- [x] package.json scripts + compliance.yml 调用步骤
- [x] 实测：`--fixture` 全 PASS；`--module nop-message` 退出 0；`--module nop-metadata` 既有行为不回归（存量违规照常报出）
- [x] F-01/F-03 fixture 从 git 历史（WI7/WI8 修复前版本）转写而非合成近似

Exit Criteria:

- [x] `node ai-dev/tools/check-silent-wrong-result.mjs --fixture` 退出 0 且含三新规则的 PASS 行
- [x] `node ai-dev/tools/check-silent-wrong-result.mjs --module nop-message` 退出 0
- [x] **正反双向 self-test**：三规则各有 violation（命中）与 compliant（不命中）fixture
- [x] **三形态捕获实测**：F-01/F-03/F-53 形状 fixture（git 历史转写）各命中对应规则
- [x] 既有 5 规则 fixture 零回归
- [x] `ai-dev/logs/` 条目
- [x] No owner-doc update required（工具自身使用说明在文件头注释与 package.json）

## Closure Gates

- [x] 三规则落地且 fixture 正反双向全 PASS
- [x] `--module nop-message` 退出 0 + compliance.yml 接线
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `node ai-dev/tools/check-silent-wrong-result.mjs --fixture` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/18-wi18-gate-rules.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 其他模块的存量违规纳入各自 mission（baseline 机制渐进）。

## Closure

Status Note: WI18 三规则（msg-pre-poll-seek/msg-stub/msg-warn-per-call）落地并经独立审计：fixture 24 项全 PASS（22 fixture 用例 + allowed-comment + baseline-reconcile 两机制检查）（含三缺陷形态正反双向）、存量 4 条命中处置（3 locale 修复 + 1 invariant-ok 裁决）、`--module nop-message` 退出 0、package.json 与 compliance.yml 接线完成、nop-metadata 存量行为不回归。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_f2a5c339-457d-4b37-a298-6f2e46e488b9（fresh session，自行重跑工具）
- Evidence:
  - 三规则实现 PASS：scan 函数 L612/L640/L676 + 注册 + scoping 双接线 + 标题
  - --fixture PASS：退出 0，24 项全 OK（6 新规则行 violation/compliant 成对；既有 16 条零回归）
  - --module nop-message PASS：退出 0（TOTAL 0 + 1 invariant-ok allowed）；3 处 Locale.ROOT；编译 exit 0
  - --module nop-metadata PASS：TOTAL 24 照常报出（不回归）
  - 接入 PASS：package.json 3 scripts；compliance.yml job（YAML 解析通过）
  - git 历史对照 PASS：F-03 ≡ b2aa08b195^ 形状；F-01 ≡ 88405f516f^ 形状
  - git 范围 PASS：8 归属文件；scan-hollow 0 发现
  - 已知 Minor：工具头注释已修；compliant fixture 的 warnOnce 豁免路径无成对 fixture（advisory，未来覆盖建议）