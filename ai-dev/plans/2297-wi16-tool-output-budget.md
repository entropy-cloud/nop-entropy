# 2297 WI16 工具输出预算 schema 内建（refactor roadmap M5）

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI16（Deliverable: 代码变更 + 测试；deps: 无；波及面提示：schema 参数改变全部工具 LLM 定义面，必须评估前缀缓存影响）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI16 吸收；与 autonomous WI1 交互登记）
> Design Contract: Owner `ai-dev/design/nop-ai-agent/04-tool-invocation.md`
> Cross-Cutting: tool.xdef 位于 nop-kernel/nop-xdefs——平台级契约演进，plan-first + owner doc + 迁移说明；验证面扩展 nop-xdefs

## Purpose

unreal §7：输出预算是工具参数的一部分、模型按调用自选（默认 40000/上限 1000000），截断头尾保留+精确字节数+全文落盘指针。nop 现状：截断散落、阈值 8000 硬编码常量、与模型不可调；spill store 已有全文保留+指针回填但与预算参数未打通。本项实现 schema 内建的模型可调输出预算 + 统一截断协议。

## 前缀缓存影响评估（波及面提示的应答）

**裁定：不向任何已发布工具的 `<schema>` 自动注入预算参数——全部已发布工具的 LLM 定义面字节不变，前缀缓存零影响**（autonomous WI1 工具排序与 WI20 前缀稳定目标不受扰动）。模型可调通过两条 opt-in 路径：(a) 保留参数 `maxOutputChars` 由 dispatcher 在任意工具调用参数中识别（运行时兑现，未声明不占 schema 字节；工具若要向模型宣传该旋钮，自行在 `<schema>` 加可选参数——opt-in 才有定义面代价）；(b) tool.xdef 新增 `<output-budget>` 声明（同上，opt-in 元数据，不进 schema）。次影响：截断标记与 spill 指针文本随结果内容变化——这是既有行为（[TRUNCATED]/[SPILL_REF] 已存在），非新增漂移。

## Current Baseline

（执行时 HEAD `1511c09f57`，2026-10-03）

- `tool.xdef`（nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/tool/tool.xdef）：6 要素，无预算声明；`xdef:bean-package="io.nop.ai.toolkit.model"` → `_AiToolModel`（生成类在 nop-ai-toolkit/src/main/java/.../model/_gen/，随构建 codegen 再生）。
- 截断：`ToolResultTruncator`（nop-ai/nop-ai-agent/.../compact/ToolResultTruncator.java）常量 DEFAULT_TRUNCATION_THRESHOLD_CHARS=8000/HEAD_CHARS=6000/TAIL_CHARS=1000、`truncate`（头+精确移除数标记+尾）、`truncateIfAllowed`（NON_TRUNCATABLE_TOOLS 白名单跳过）、`CompactConfig.DEFAULT_TRUNCATION_THRESHOLD_CHARS` 同值。
- spill：`AgentToolDispatcher.spillIfOversized`（engine/AgentToolDispatcher.java:1015-1045，调用点 :760）——超阈值且可 spill 时全文 PUT spill store + `[SPILL_REF id=...] Full result stored; use read-spill to retrieve.` 指针回填（含预览），失败降级截断+warn；null-session 降级截断（文档化）。
- 预算解析可用输入：调用点 `chatToolCall.getArgumentsText()`（JSON 参数文本）在 ToolCallOutput 循环内可用（:746-760）；`toolManager.loadTool(toolName)` 可取 AiToolModel（:514 先例）。
- 既有测试：TestToolResultTruncator、TestSpillStore（端到端驱动 dispatcher，spill 回归主面）在 compact/ 包；engine/ 无 spill 专项测试。注意 ReActAgentExecutor.java:1076-1079 另有一处 8000 截断（LLM_TURN checkpoint 摘要，非工具输出面，勿误改）。

## Goals

- **tool.xdef**：新增可选元素 `<output-budget xdef:name="AiToolOutputBudgetModel" max-chars="int" spill="!boolean=true"/>`（带属性子元素必带 xdef:name——llm.xdef `<request xdef:name="LlmRequestModel">` 先例；生成 `io.nop.ai.toolkit.model.AiToolOutputBudgetModel` + 父类 `getOutputBudget()`；构建 precompile 阶段 gen-toolkit-xdsl.xgen 自动再生，禁手改 _gen/）（全零改动兼容：不声明 = 运行时默认）。
- **统一预算解析**（新 `ToolOutputBudgetResolver`，nop-ai-agent compact 包）：优先级 = 调用参数 `maxOutputChars`（>0，钳制到 ≤ 声明 max-chars 或全局硬上限 1_000_000，对齐 unreal cap）→ per-tool `<output-budget max-chars>` → 默认 8000（现值不变）；spill 开关 = per-tool `spill` 声明（默认 true）；NON_TRUNCATABLE_TOOLS 白名单语义保持最高（ask-oracle/ask-human 永不截断）。
- **统一截断协议**（ToolResultTruncator 增方法）：`applyBudget(content, maxChars)` 头尾保留 + 精确移除数标记，**小预算区间算法规格**：head/tail 按预算等比缩放——head = min(HEAD_CHARS, maxChars*0.75)、tail = min(TAIL_CHARS, maxChars*0.25)（向下取整，保 marker 余量）；maxChars ≤ 32 时整体截到 maxChars（无头尾保留语义可讲）；**永不越界**（round 1 审查 M1：模型可输入任意正整数，(0,7000) 区间在固定 HEAD/TAIL 下会 substring 越界或预算失效）；`spillIfOversized` 改用解析后的预算（替代硬编码 8000），spill 指针回填协议不变（含预览 + read-spill 指引）。
- **测试**：xdef 解析（声明/不声明/缺省）、预算优先级（per-call 钳制/per-tool/默认/白名单最高）、spill 与截断按预算联动、既有测试零回归。

## Non-Goals

- 向已发布工具 schema 自动注入参数（前缀缓存裁定，见上）。
- bash `timeoutMs` 封顶策略（autonomous 面已登记）；WI17 并发面。
- spill store / read-spill 工具本体改动。

## Scope

### In Scope

- tool.xdef + `_AiToolModel` 再生 + 前端编译（nop-xdefs、nop-ai-toolkit 验证面）；`ToolOutputBudgetResolver`、`ToolResultTruncator.applyBudget`、dispatcher `spillIfOversized` 预算化；owner doc + log。

### Out Of Scope

- 各工具 XML 的 schema 宣传（opt-in，未来逐工具）；ORM/Gateway 面。

## Execution Plan

### Phase 1 - xdef 契约 + 预算解析与接线

Status: completed
Targets: nop-xdefs/tool.xdef、nop-ai-toolkit（生成模型）、nop-ai-agent（resolver/truncator/dispatcher）

- Item Types: `Feature`

- [x] tool.xdef 增 `<output-budget xdef:name="AiToolOutputBudgetModel" max-chars="int" spill="!boolean=true"/>`；构建再生 `_AiToolModel`/`AiToolOutputBudgetModel`（codegen precompile 自动再生，非手改）
- [x] `ToolOutputBudgetResolver`：`resolve(toolName, argumentsText, toolManager)` → Budget{maxChars, spillAllowed}；优先级与钳制如 Goals；`ToolResultTruncator.applyBudget`
- [x] dispatcher：`spillIfOversized` 改收预算参数；:760 调用点经 resolver；既有 8000 默认行为字节级不变（无声明无 per-call 时）
- [x] 测试：xdef 解析正反例；resolver 优先级矩阵（含畸形 args/整体非 JSON/钳制边界/小预算区间不越界）；spill/截断预算联动；**端到端**（扩展 TestSpillStore 模式：声明 output-budget 的工具 + 带 maxOutputChars 调用 → SPILL_REF 生成与预算联动）；零回归

Exit Criteria:

- [x] 上述测试全绿；`./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（3513 全绿；codegen precompile 自动再生并编译验证）
- [x] **接线验证**：dispatcher 运行时经 resolver 走到预算解析（budgetedToolResultSpilledWithModelOverride 经 ReActAgentExecutor 真实装配 + 模型 getter 断言）（预算联动断言即证明）；xdef 声明经 loadTool 真实可读（模型 getter 断言即证明）
- [x] **无静默跳过**：非法 per-call 值（≤0/非数值/argumentsText 整体非 JSON——模型 args 常有轻微畸形）忽略并 debug 日志（不中断工具执行——预算旋钮不得成为故障面），声明非法由 xdef 校验 fail-loud
- [x] owner doc 归 Phase 2（显式归属）；`ai-dev/logs/` 条目已更新

### Phase 2 - owner doc 与收口

Status: completed
Targets: `ai-dev/design/nop-ai-agent/04-tool-invocation.md`、log、roadmap

- Item Types: `Proof`

- [x] owner doc 新增"工具输出预算"契约节（xdef 声明、解析优先级、钳制与硬上限、spill 联动、前缀缓存裁定、opt-in 宣传指引、迁移说明——xdef additive 旧工具零改动）
- [x] doc-links 0 error；roadmap WI16 勾选 + 锚点；独立 closure audit + 工具门 + git commit

Exit Criteria:

- [x] owner doc 在位且与 live 一致；全部验证门通过

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 roadmap WI16 checkbox → git commit → 本 plan 标 completed。

- [x] 输出预算端到端连通（xdef 声明→loadTool→resolver→spill/截断联动→指针回填——budgetedToolResultSpilledWithModelOverride 经真实 executor 装配断言 preview 遵循模型 500 预算）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（3513 全绿，3509→3513+e2e）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2297-wi16-tool-output-budget.md --strict` 退出码 0
- [x] checkstyle：commit 钩子 ast-grep 承担（No separate checkstyle gate required）
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（含全部 In Scope 文件；排除无关杂散改动）

## Non-Blocking Follow-ups

- 各已发布工具逐个 opt-in 宣传 maxOutputChars（需逐工具评估定义面/前缀代价）
- 预算超限事件 payload 暴露（WI9 typed payload 扩展）

## Closure

Status Note: xdef 契约 + 生成物同步（codegen precompile 自动再生，非手改）、resolver 优先级/钳制/容错、applyBudget 等比缩放与旧协议字节兼容、dispatcher 接线含 spill=false 与默认等价；两轮审查的 Blocker（xdef:name 缺失）与 Major（小预算越界）修复并有直接测试；audit 提出的 P2 端到端缺口已补（budgetedToolResultSpilledWithModelOverride 经真实 executor 装配）——WI16 交付全部落地。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_71b04a6c-457f-4d67-8c73-54d39e0b9e2a（fresh session）
- Evidence:
  - 9 验证点全 PASS（xdef+生成物同步经 git diff 核验 / resolver 优先级钳制容错 / applyBudget 无越界+字节兼容 / dispatcher 接线+spill=false+默认等价 / 测试真实性 / 35 定向+3513 全量全绿 / 文档面 / Anti-Hollow 链连通 / 全量日志 BUILD SUCCESS）
  - P2（端到端测试缺口）已在收口前补齐：TestSpillStore.budgetedToolResultSpilledWithModelOverride（17/17）
  - hollow exit 0；doc-links exit 0；plan-checklist exit 0
  - 无关杂散 .rels 改动已排除出 commit

Follow-up:

- 各已发布工具逐个 opt-in 宣传 maxOutputChars / 预算超限事件 payload（见 Non-Blocking Follow-ups）
