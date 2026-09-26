# 363 nop-deepwiki 对标 deepwiki.com 的结构范式与消费协议升级

> Plan Status: draft
> Last Reviewed: 2026-09-26
> Source: `ai-dev/analysis/2026-09/2026-09-26-nop-deepwiki-gap-analysis.md`（差距分析，含 deepwiki.com 一手抽样与开源工具 2026-09 现状调研）
> Related: `ai-dev/analysis/deepwiki-survey/`（12 份基线）、`ai-dev/plans/361-nop-jq-deadcode-and-jpath-fix.md`、`ai-dev/plans/362-nop-code-index-column-truncation-fix.md`

## Purpose

把 nop-deepwiki skill 的产出从"9 页 flat 文档"升级到 deepwiki.com 的结构范式（十进制层级树、页数档位对齐 35-100+ 页、页面密度规范）并补齐 agent 消费协议层（llms.txt）。收口状态：skill 在一个真实模块（nop-jq 或更大模块）上按新范式产出 ≥20 页层级化 wiki，check-wiki 全绿，llms.txt 生成，页面密度达标率可验证。

## Current Baseline（2026-09-26 live 核实）

- skill 现状（`/Users/abc/app/nop-entropy-wt/nop-entropy-master/.opencode/skills/nop-deepwiki/`）：Phase 2 纯文本检索（2026-09-26 起 nop-code 已移除）；页面档位 compact=4-6 / standard=8-12 / deep=15-20 **内容页**；flat `modules/<name>.md` 目录；无页面字数/表格数规范（仅 ≥1 mermaid、页首源文件块、页尾 Sources）；`check-wiki.mjs` 校验断链/Mermaid/Sources/索引漂移/wiki-state 一致性；`gen-wiki-meta.mjs` 从 PLAN 契约表生成 index.md + 指纹。
- nop-jq 实测记录（logs 09-25/09-26）：9 页、子代理 33 万-158 万 token/页、模块页子代理通读 1899/1047/1058 行大文件——签名骨架配方已存在（Phase 2 §5）但未强制注入派发 prompt。
- deepwiki.com 实测范式（分析报告 §2）：十进制编号层级树、每页 ~1,000 词 + 3-4 mermaid + 3-4 表格、段末 Sources、首页 On this page 锚点、全站 `/llms.txt`（页面清单）+ MCP 三工具。
- 差距分析裁定（分析报告 §5）：本轮做 R1（结构范式+档位重标定）、R2（页面密度规范）、R3（llms.txt 消费层）、R4（骨架前置降 token）、R6（引用双形态可选）；不做 RAG/交互问答/多语言/MCP server（Non-Goals）。

## Goals

- 档位重标定并验证：standard 档产出 20-35 页层级化 wiki，deep 档 40+，页数由模块规模与 cluster 规划决定而非固定上限。
- PLAN 契约支持十进制编号层级目录树（章目录如 02-core/、页文件如 02-01-parser），check-wiki/index 按树序导航。
- 页面密度硬约束进派发模板与 check-wiki：≥800 词、模块页 ≥3 mermaid、每页 ≥2 表格、每个 H2 段末 Sources。
- `gen-wiki-meta.mjs` 生成 `llms.txt`（对齐 deepwiki.com 形态：页面清单+职责一句话）。
- 超长文件（>500 行）签名骨架强制注入派发 prompt，模块页 token 成本相对 nop-jq 基线（33 万-158 万/页）下降 ≥40%。
- 引用双形态：git 远程存在时 Sources 附 GitHub 永久锚点链接。

## Non-Goals

- 不做 RAG/embedding 索引（survey 结论：行级引用已满足溯源；概率性检索不进主路径）。
- 不做交互问答 / Deep Research / MCP server（需运行时服务，超出生成器定位；llms.txt 已覆盖 agent 发现）。
- 不做多语言翻译。
- 不改变 docs-for-ai 与 deepwiki 的定位边界（产出仍不入 `docs-for-ai/`，见 2026-08-04 对比报告结论）。
- 不回改 plan 361/362 与既有 skill 脚本的已定行为（仅扩展）。

## Scope

### In Scope

- `.opencode/skills/nop-deepwiki/SKILL.md`（档位/层级规划/密度规范/骨架注入/派发模板）
- `.opencode/skills/nop-deepwiki/scripts/gen-wiki-meta.mjs`（llms.txt + 层级树序 index）
- `.opencode/skills/nop-deepwiki/scripts/check-wiki.mjs`（密度/层级/llms.txt 一致性检查项）
- 一次真实模块的全流程验证产出（临时目录，不入库）

### Out Of Scope

- docsify/静态站点托管（分析报告 R5——本轮不做，单独评估）
- wiki-state 增量更新的实战轮次验证（另立验证任务）
- nop-code / nop-entropy 产品代码

## Execution Plan

### Phase 1 - 结构范式：档位重标定与层级树规划

Status: planned
Targets: `.opencode/skills/nop-deepwiki/SKILL.md`（Phase 0/3）、`.opencode/skills/nop-deepwiki/scripts/gen-wiki-meta.mjs`、`.opencode/skills/nop-deepwiki/scripts/check-wiki.mjs`

- Item Types: `Fix | Decision`

- [ ] Decision: 页面档位重标定为 standard=20-35 / deep=40+ 内容页（compact 移除——实测 9 页不足以覆盖 127 文件模块的子系统面），页数由 Phase 2 模块地图的子系统数量×子系统深度决定，写明 cluster 式规划步骤（子系统→章，子系统内主题→页）
- [ ] Fix: PLAN.md 页面契约表支持层级路径（编号目录树：`02-core/` 章目录下 `02-01-parser` 页面文件），gen-wiki-meta 按 PLAN 树序生成层级化 index.md（章→页两级缩进）
- [ ] Fix: check-wiki 增加层级一致性检查（index 层级与 PLAN 树一致、无游离页面）
- [ ] Fix: 恒含页保留（overview/architecture/quickstart/glossary/reading-guide），glossary 仍为全站收尾页

Exit Criteria:

- [ ] SKILL.md 档位表与 Phase 3 规划步骤包含上述 Decision 内容
- [ ] gen-wiki-meta 对层级 PLAN 生成两级缩进 index.md（用测试 fixture 验证）
- [ ] check-wiki 对层级布局的 fixture 通过/对游离页报错（fixture 双向验证）
- [ ] `node --check` 两脚本通过
- [ ] No new test required: 脚本验证以 fixture dry-run 证据代替（项目无 JS 测试基建）
- [ ] No owner-doc update required（skill 自身即交付物）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 页面密度规范与骨架注入（生成经济学）

Status: planned
Targets: `.opencode/skills/nop-deepwiki/SKILL.md`（Phase 2/4 派发模板）

- Item Types: `Fix`

- [ ] Fix: Phase 4 派发模板密度硬约束——每页 ≥800 词、每页 ≥2 表格、模块页 ≥3 mermaid、每个 H2 段末附 "Sources:" 列表（对齐 deepwiki.com 每段 3-5 条形态）
- [ ] Fix: Phase 2 产出的超长文件签名骨架（>500 行）**强制**进入对应页面的派发 prompt（子代理按行区段精读，不通读全文）
- [ ] Fix: 限流保护维持同批 ≤5 并发；批次数随页数增加（20+ 页时分 4-5 批，叶子页先行、总览殿后顺序不变）

Exit Criteria:

- [ ] SKILL.md 派发模板含全部密度约束与骨架注入要求
- [ ] Phase 3 端到端产出中：抽 5 页验证密度达标（词数/mermaid/表格/Sources 段计数），记录每页子代理 token 消耗
- [ ] 模块页平均 token 相对 nop-jq 基线（33 万-158 万/页）下降 ≥40%（同一目标模块对比口径）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端验证与消费协议

Status: planned
Targets: `.opencode/skills/nop-deepwiki/scripts/gen-wiki-meta.mjs`、临时验证产出

- Item Types: `Fix | Proof`

- [ ] Fix: gen-wiki-meta 增生成 `llms.txt`（页面清单+一句话职责+层级前缀，对齐 deepwiki.com 的 agent 发现形态）
- [ ] Fix: 引用双形态——目标仓库有 git 远程时，Sources 链接附 GitHub 永久锚点（`blob/<commit>/path#L10-L20`），本地相对链接保留（可校验性不降级）
- [ ] Proof: 在真实模块（nop-jq）上按新范式全流程跑 standard 档：产出 ≥20 页层级化 wiki 到临时目录，`check-wiki.mjs --strict` 0 ERROR，llms.txt 与页面清单一致，index 层级与 PLAN 一致
- [ ] Proof: 与 deepwiki.com 的 nop-entropy wiki 做一次抽样对照（结构要素逐项：层级/密度/引用/llms.txt），结论记入 daily log
- [ ] No owner-doc update required（skill 自身即交付物；分析报告为 source）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 行为/契约结果已达成（standard 档 ≥20 页层级化产出 + llms.txt + 密度达标）
- [ ] 必要 focused verification 已完成（fixture 双向验证 + 抽页密度计数 + token 对比口径）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [ ] 受影响的 owner docs 已同步（分析报告 Status 翻 resolved 或加执行注记）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证新检查项真实生效（对违规 fixture 报错、对合规 fixture 放行），而非仅存在于文档描述
- [ ] `node --check` 全部脚本
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### 静态站点托管（分析报告 R5）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 呈现层服务人类读者体验，agent 消费链路（llms.txt+markdown）已闭环；单独评估 ROI 后再立项
- Successor Required: `no`

### 交互问答 / MCP server / 多语言（分析报告 D10/D11）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需运行时服务或产出后处理管线，超出生成器 skill 定位；llms.txt 已覆盖 agent 发现与消费
- Successor Required: `no`

## Non-Blocking Follow-ups

- wiki-state 增量更新的实战轮次验证（改代码→update 模式实测）——本轮端到端只验证全量生成。
- token 成本口径若无法同模块对比（nop-jq 已按旧版生成过），改用同规模模块或注明口径差异。

## Closure

Status Note: （关闭时填写）
Completed: （关闭时填写）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent）
- Evidence: （每条 Exit Criterion / Closure Gate 的 PASS/FAIL + live 证据）

Follow-up:

- 见 Non-Blocking Follow-ups
