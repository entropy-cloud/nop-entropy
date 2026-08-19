# W2-review 设计文档独立审查 gate（≥2 轮独立子agent 至 PASS）

> Plan Status: active
> Last Reviewed: 2026-08-19
> Mission: xlang-execution-optimization
> Work Item: W2-review
> Source: ai-dev/backlog/xlang-execution-optimization-roadmap.md（W2-review 条目 + 审查纪律 1）
> Related: 2026-08-19-2050-1-w1-design-docs.md（前置）、2026-08-19-2050-3-w3-supplement-work-items.md（后继）

## Purpose

对 W1-design 产出的三组设计文档执行不少于两轮独立子agent 对抗性审查，逐轮修复，直到某一轮 PASS（0 P0/P1），关闭 W2-review gate，为 W3-supplement 解锁。

## Current Baseline

- 前置条件：`2026-08-19-2050-1-w1-design-docs.md` 已 `completed`（W1 三组文档成稿）。若 W1 未完成，本 plan 不得启动（各 Phase 保持 `planned`，plan 挂起等待）
- 审查报告目录已建：`ai-dev/audits/xlang-execution-optimization/`，README 规定 W2 报告命名模式（日期前缀 + design-review-round-N 后缀）
- 审查方法论资产（mission prompts 已引用）：`ai-dev/skills/deep-audit-prompts.md`、`ai-dev/skills/open-ended-adversarial-review-prompt.md`
- roadmap 硬约束（纪律 1）：不少于两轮独立子agent（每轮新开、不共享上下文）；PASS（0 P0/P1）前不得进 W3；报告持久化并在 W2 条目回链
- 审查对象（W1 产出）：`ai-dev/design/xlang-execution/`（00-vision + 01-architecture-baseline + README）、`ai-dev/design/xlang-java/`（01-architecture-baseline + README）、`ai-dev/design/xlang-truffle/`（00-vision + 02-architecture-baseline + README + 01 知识层）

## PASS 判定口径（本 plan 固化，审查报告须采用）

- **P0**：阻断正确性或可行性的设计缺陷——三后端语义不一致、依赖方向违规、对拍基准不可判定、与 live 代码事实矛盾到无法实现
- **P1**：会导致实现返工的重大缺口——roadmap W1 内容点缺失、关键决策无理由/无替代方案对比、01 知识层仍承载决策、Open Questions 未清账、跨文档决策冲突
- **P2**：改进项（表述、结构、可读性），不阻断 PASS
- **PASS** = 本轮报告 0 条 P0 且 0 条 P1（P2 允许遗留，但须逐条记录裁定）
- 与方法论资产的口径衔接：`deep-audit-prompts.md` / `open-ended-adversarial-review-prompt.md` 使用 P0-P3 四级且面向代码审查；派发审查时必须在 prompt 中声明本 plan 的三级口径（P3 归入 P2）与文档式证据格式（文档路径 + 章节/行定位，替代代码片段），未分级或无法归级的 finding 按高一级处理

## Goals

- 执行 ≥2 轮独立审查（每轮 fresh session 子agent），报告全部落盘到 `ai-dev/audits/xlang-execution-optimization/`
- 每轮之间的 P0/P1 发现全部修复（或以书面裁定驳回并说明理由），修复后进入下一轮
- 达到某一轮 PASS（0 P0/P1），该轮报告有明确 verdict
- roadmap `W2-review` 条目回链全部轮次报告（≥2 份），gate 关闭

## Non-Goals

- 不扩大设计范围：审查发现的新需求只作为 finding 记录，汇总于 PASS 轮报告的"移交 W3 清单"章节交 W3 裁定；不得为讨好审查而私自加需求
- 不修订 roadmap 阶段二 I1-I7（W3 的工作）
- 不做任何代码变更

## Scope

### In Scope

- 审查输入包组装、审查子agent 派发、报告落盘
- 审查发现的 P0/P1 修复（修改对象仅限 W1 产出的设计文档与 README）
- roadmap `W2-review` 条目回链与状态同步

### Out Of Scope

- 阶段二 I1-I7 内容与 `missions/xlang-execution-optimization.json`
- 设计文档范围外的新文档撰写
- 代码

## Execution Plan

### Phase 1 - Round 1 独立审查与修复

Status: planned
Targets: `ai-dev/audits/xlang-execution-optimization/{执行日期}-design-review-round-1.md`（新建）、W1 产出的三组设计文档（修复对象）

- Item Types: `Proof` | `Fix`

- [ ] 组装审查输入包：三组设计文档 + `01-truffle-knowledge.md` + 审查锚点清单（live 位置：`exec/` 137 节点、`ScriptCompilerRegistry`、`ResourceComponentManager`、`JaninoScriptCompiler`、`nop-kernel/nop-javac`、`nop-frontend-support/nop-js`；W1 移交的遗留分歧清单如有则一并附上）
- [ ] 派发独立子agent（fresh session，task id 记录在案）执行对抗性审查，审查维度至少覆盖：①roadmap W1 内容点逐点覆盖核对（4+6+5）②design-writing-guide 合规（决策三要素/无代码级展开/引用约束）③跨文档一致性（选择机制/边界/依赖方向/对拍基准）④与 live 代码可行性核对（引用的先例类/模块是否存在、约束是否可实现）⑤`01-truffle-knowledge.md` 决策迁出与 Open Questions 清账核对
- [ ] 报告按命名规范落盘，finding 逐条分级（P0/P1/P2）并附文档内定位
- [ ] 修复：全部 P0/P1 修复，或书面裁定驳回（附理由）记入报告回应段；P2 修复或裁定遗留

Exit Criteria:

- [ ] round-1 报告存在于 `ai-dev/audits/xlang-execution-optimization/`，含分级 findings 与审查者 task/session 标识
- [ ] round-1 的全部 P0/P1 已修复或裁定驳回，且修复后的设计文档已落盘（报告回应段逐条对应）
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 2 - Round 2+ 独立复审至 PASS

Status: planned
Targets: `ai-dev/audits/xlang-execution-optimization/{执行日期}-design-review-round-N.md`（N≥2，新建）

- Item Types: `Proof` | `Fix`

- [ ] 派发新的独立子agent（不得复用 round-1 的 session/task）对修复后的文档全量复审（不是只复审 delta）
- [ ] round-N 报告落盘（同命名规范，含 verdict 字段）
- [ ] 若该轮存在 P0/P1：修复后开启 round-(N+1)（继续 fresh session），循环直到某轮 verdict=PASS；每轮非 PASS 报告均含"回应段"——该轮 P0/P1 的修复/裁定逐条记录在案，作为下一轮开启的前置条件（与 round-1 同一规范）
- [ ] 收敛保护：若连续 3 轮未 PASS 且 P0/P1 发现不再收敛（同质 finding 反复出现），停止循环，将 Phase 2 置为 `blocked`（plan 保持 `active`）并在当日 log 上报设计争议待用户/后继裁定；不得无限循环，不得降级口径换取 PASS
- [ ] PASS 轮的报告显式写明 `Verdict: PASS（0 P0/P1）`、遗留 P2 清单，以及"移交 W3 清单"章节（汇总审查中发现但裁定为 out-of-scope 的新需求/改进项，供 W3 回填阶段二时核对是否纳入）

Exit Criteria:

- [ ] 磁盘上审查轮次报告 ≥2 份，最后一轮 verdict 为 PASS（0 P0/P1）
- [ ] 相邻两轮之间都有修复/裁定记录（每轮报告的 finding 在下一轮开始前已处置）
- [ ] 每轮审查均由不同的 fresh session 子agent 执行（各报告含 task/session 标识，可核对互不相同）
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 3 - gate 收口与回链

Status: planned
Targets: `ai-dev/backlog/xlang-execution-optimization-roadmap.md`（W2-review 条目）、`ai-dev/audits/xlang-execution-optimization/README.md`

- Item Types: `Follow-up`

- [ ] roadmap `W2-review` 条目正文回链全部轮次报告（≥2 份，含 PASS 轮）
- [ ] audits 目录 README 索引补齐本次报告清单
- [ ] roadmap `W2-review` 状态与 plan 状态一致（closure audit 通过后 `done`）

Exit Criteria:

- [ ] roadmap W2 条目正文包含 ≥2 份报告的相对链接且链接有效
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 当日条目已更新

## Closure Gates

> 纯文档计划：`./mvnw test` / `./mvnw compile` 等构建验证条目按 guide 规则移除。

- [ ] 独立审查轮次 ≥2，且最后一轮 verdict 为 PASS（0 P0/P1），报告全部落盘
- [ ] 每轮的 P0/P1 发现在下一轮开始前已修复或书面裁定驳回（无静默跳过的 finding）
- [ ] 各轮审查者均为独立 fresh session（报告内 task/session 标识互不相同）
- [ ] roadmap `W2-review` 条目回链 ≥2 份报告，链接有效
- [ ] W1 产出的设计文档与最终 PASS 轮结论一致（修复未回退）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/xlang-execution-optimization/2026-08-19-2050-2-w2-design-review-gate.md --strict` 退出码 0（closure 时执行）
- [ ] roadmap `W2-review` 状态与 plan 状态一致
- [ ] No docs-for-ai update required: 本 plan 仅审查/修复 `ai-dev/design/` 文档
- [ ] 独立子 agent closure audit 已完成且证据写入下方 Closure 段
- [ ] `ai-dev/logs/` 收口条目已记录

## Deferred But Adjudicated

- PASS 轮遗留的 P2 findings
  - Classification: `out-of-scope improvement`（逐条记录于 PASS 轮报告）
  - Why Not Blocking Closure: PASS 口径已固化为 0 P0/P1；P2 为表述/结构改进，不影响可实现性与正确性。其中"移交 W3 清单"子集（out-of-scope 新需求/改进项）随 PASS 轮报告经 Phase 3 回链进入 roadmap W2 条目，W3 可达、有明确归属
  - Successor Required: no（如 W3/W4 发现需要处理，按 roadmap 顺序自然接管）

## Non-Blocking Follow-ups

- 无

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- no remaining plan-owned work（后继 W3-supplement 由 roadmap 既定顺序接管）
