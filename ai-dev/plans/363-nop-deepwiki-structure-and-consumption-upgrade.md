# 363 nop-deepwiki 内容质量升级：概念章规划、密度模板与严谨性闭环

> Plan Status: draft
> Last Reviewed: 2026-09-26
> Source: `ai-dev/analysis/2026-09/2026-09-26-nop-deepwiki-gap-analysis.md`（v3，内容质量轴 + §5.5 源码实证增补；`~/sources/deepwiki/` 12 仓读源取证）
> Related: `ai-dev/plans/361-nop-jq-deadcode-and-jpath-fix.md`、`ai-dev/plans/362-nop-code-index-column-truncation-fix.md`

## Purpose

把 nop-deepwiki 的产出从"目录的百科"升级为"机制的教科书"（对齐 deepwiki.com 的内容质量形态）。收口状态：在 nop-jq 上用升级后的管线重新生成 wiki（临时目录不入库），产出包含概念性机制章、页面密度达标、断言抽样机检通过，并与旧版 9 页做前后对照，结论记入 daily log。

## Current Baseline（2026-09-26 live 核实）

- skill 现状：Phase 2 纯文本检索；Phase 3 PLAN 模块地图**直接映射目录/包**（差距根因，见分析报告 §4）；Phase 4 派发模板只有 ≥1 mermaid + 页首源文件块 + 页尾 Sources，无表格/字数/段末 Sources 约束；生成单轮 GATHER→WRITE 无修订；check-wiki 校验断链/Mermaid/Sources/索引漂移/wiki-state 一致性，无断言真实性抽检。
- nop-jq 旧产出实证（logs 09-25/09-26）：compact 9 页、flat modules/、jsonpath 页以"段类型清单"为主、表格随机出现、跨切面机制只有 1 张时序图；子代理 33 万-158 万 token/页（通读大文件）。
- 差距分析 v2 裁定（分析报告 §5-§6）：本轮吸收 A1 概念聚类规划、A2 机制章必选、A3 Claims 抽样校验、A4 表格密度模板、A5 leaf-first 父页引实产、A6 大页两轮、A7 证据 60/20/20、A8 骨架前置、A9 mindmap、A10 commit 锚点、A11 模型调度、A12 质量抽检；不吸收 RAG/硬编码章节/产品形态项（§8 附注归档）。
- 源码取证（2026-09-26）：survey 12 项目源码在 `~/sources/deepwiki/`，读源产出十大技法增补分析报告 §5.5——其中 5 个 A 清单未覆盖的新技法（N1 THINK 退出 checklist+Phase Gate、N2 机制深度 10 项检查表、N3 宽松引用格式+确定性后处理、N4 Mermaid 防坑清单、N5 规划上下文压缩两段式、N6 结构化输出三级回退）与多个 A 项的源码级参数（聚类兜底链、Claim 实质性标准、大纲轮 1/3 预算）。

## Goals

- Phase 3 规划升级：PLAN 增"概念章规划"步骤——章节由概念聚类产生（机制章 ≥2：核心数据流 + 核心子机制），目录映射降为证据来源；页数不设人为上限。
- Phase 4 派发模板升级：每页 ≥2 表格、模块页 ≥3 mermaid、每个 H2 段末 Sources、大页两轮生成、证据 60/20/20 配比、骨架强制注入。
- 严谨性闭环：引用改松格式输出+确定性后处理（模型写 `Sources: [path:line]()`、check-wiki 解析校验拼锚）；check-wiki `--verify-claims` 抽样验证断言行号真实性；Mermaid 语法防坑进模板与检查。
- 导航与快照：index.md 顶部 mindmap + commit 锚点（gen-wiki-meta 脚本化）。
- 端到端前后对照：nop-jq 新旧产出对比（机制章有无、密度、严谨性），结论入 daily log。

## Non-Goals

- 不做 RAG/embedding（survey [01] 结论：行级 Claim 是确定性方案，已满足溯源）。
- 不做产品形态项：十进制编号对外规范、llms.txt/MCP server、静态站点托管、多语言、交互问答（分析报告 §8 附注归档，需要对外发布时另立）。
- 不改 docs-for-ai 定位边界（产出不入 `docs-for-ai/`）。
- 不改 plan 361/362 已定行为。

## Scope

### In Scope

- `.opencode/skills/nop-deepwiki/SKILL.md`（Phase 3/4 重写、纪律增补）
- `.opencode/skills/nop-deepwiki/scripts/check-wiki.mjs`（--verify-claims、密度检查项）
- `.opencode/skills/nop-deepwiki/scripts/gen-wiki-meta.mjs`（mindmap、commit 锚点）
- nop-jq 全流程验证产出（临时目录，不入库）

### Out Of Scope

- 分析报告 §8 附注的全部产品形态项
- wiki 增量更新实战轮次（另立任务）

## Execution Plan

### Phase 1 - 概念章规划（Q-1）

Status: planned
Targets: `.opencode/skills/nop-deepwiki/SKILL.md`（Phase 3）

- Item Types: `Fix | Decision`

- [ ] Decision: PLAN 增"概念章规划"步骤——主会话基于 Phase 2 证据（模块地图/fan-in/import 图）做概念聚类，章节轴=概念（机制/流程/不变式）而非目录；目录映射仅作为每页的证据来源栏。回退：模块划分证据不足时回退固定页集合（保留 [05] auto_plan 兜底思想）
- [ ] Fix: 机制章必选 ≥2（核心数据流章 + 核心子机制章），章节命名示例写入 SKILL.md（如"求值语义：表达式如何变成输出流"）
- [ ] Fix: 页面档位表移除固定页数上限（compact/standard/deep 改为"由概念章数量自然决定"，保留并发批次纪律）
- [ ] Fix: 恒含页保留（overview/architecture/quickstart/glossary/reading-guide），机制章为新增类型 `flow-*.md`；quickstart 排序到最后生成（导航/综合页后置——openwiki page-jobs.ts:181 先例）
- [ ] Fix: 概念章规划的证据输入采用两段式压缩——先产出 ≤1024 token 的分析结论（领域概念/分层/关键系统）再规划（openwiki-shariqriazz planner.rs:348-464）；页面路径即契约提交后锁定；PLAN 增 relatedPages 导航字段
- [ ] Fix: 组织轴措辞取 openwiki 原文——"Organize around owned systems, runtime domains, and cross-system workflows rather than mirroring the source tree"；构建/CI/配置类内容保证有落点页（CodeWiki artifact 兜底思想）

Exit Criteria:

- [ ] SKILL.md Phase 3 含概念聚类步骤、机制章必选、回退规则；档位表无固定页数
- [ ] 新旧 PLAN 模板对照（模板样例写入 SKILL.md）
- [ ] No owner-doc update required（skill 自身即交付物；分析报告为 source）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 生成协议升级（Q-2/Q-3）

Status: planned
Targets: `.opencode/skills/nop-deepwiki/SKILL.md`（Phase 4）

- Item Types: `Fix`

- [ ] Fix: 派发模板密度硬约束——每页 ≥2 表格（实体/常量/阶段对照任选）、模块页 ≥3 mermaid、每个 H2 段末附 "Sources:" 列表
- [ ] Fix: 父页派发 prompt 必须附"已产出子页清单+各自一句话结论"，要求正文显式引用（leaf-first 引实产）
- [ ] Fix: 大页两轮生成规范化——源材料 >30 文件或 >3000 行的页面先大纲后填节
- [ ] Fix: 派发证据清单按 60/20/20 配比组织（相关证据/结构上下文/多样性补充）
- [ ] Fix: >500 行文件签名骨架强制注入派发 prompt（已有配方改强制），记录各页 token 消耗用于对比
- [ ] Fix: GATHER/THINK 实质化——GATHER 退出 checklist（≥4-6 可用片段、完整数据流理解、失败/并发/边界/扩展点已核查；uncertainty 即回读源码）；页面机制深度 10 项检查表进模板（openwiki repository-prompts.ts:143-148）+ "Do not turn the page into a source-file inventory"
- [ ] Fix: Claim 实质性标准进模板（openwiki guidance.ts:7-24：断言须物质性改变读者理解而非符号存在性）；反幻觉闭环句（Do not infer, invent, or use external knowledge...）与 Every page must earn its place 进模板
- [ ] Fix: Mermaid 防坑清单进模板（deepwiki-open prompts.py:65-95：graph TD 禁 LR、节点 3-4 词、箭头语义表、ID 冲突、标签引号，每条带反例）

Exit Criteria:

- [ ] SKILL.md Phase 4 模板与纪律含全部上述约束
- [ ] fixture/模板样例中可见全部约束的实例形态
- [ ] No new test required: 模板类变更，Phase 4 端到端验证覆盖
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 严谨性闭环与导航（Q-4/Q-5）

Status: planned
Targets: `.opencode/skills/nop-deepwiki/scripts/check-wiki.mjs`、`.opencode/skills/nop-deepwiki/scripts/gen-wiki-meta.mjs`

- Item Types: `Fix`

- [ ] Fix: check-wiki 增 `--verify-claims N`：随机抽 N 条页面断言（`path:line` 形态），读取目标文件对应行验证断言关键词命中（行号幻觉检测），命中失败计 ERROR
- [ ] Fix: check-wiki 增密度检查项（每页表格数 ≥2、模块页 mermaid ≥3 可配置阈值，WARN 级）
- [ ] Fix: gen-wiki-meta 在 index.md 顶部生成全站 mermaid mindmap（从 PLAN 树序脚本化拼接，零 LLM）+ 页脚 commit/日期快照行
- [ ] Fix: 引用松格式约定——子代理只写 `Sources: [path:line]()` 松格式，check-wiki 负责解析为真实链接并校验（deepwiki-open content.py:84-151 模式：正确性从模型责任改为代码责任）
- [ ] `node --check` 两脚本

Exit Criteria:

- [ ] 对违规 fixture（错误行号断言）`--verify-claims` 报 ERROR、对合规 fixture 放行（双向验证）
- [ ] 密度检查对缺表格 fixture 报 WARN
- [ ] mindmap/快照在 fixture 上生成正确
- [ ] No new test required: fixture 双向验证证据代替（项目无 JS 测试基建）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端前后对照验证（Q 收口）

Status: planned
Targets: 临时目录产出（不入库）

- Item Types: `Proof`

- [ ] Proof: 在 nop-jq 上用升级管线重新生成 wiki（standard 流程），产出含 ≥2 机制章、`check-wiki --strict` 0 ERROR、`--verify-claims 20` 0 ERROR
- [ ] Proof: 新旧产出对照表（机制章有无/页面密度/表格数/引用严谨性/token 消耗），记入 daily log
- [ ] Proof: 与 deepwiki.com 的 react 2.1 页形态做逐要素对照（表格/mermaid/段末 Sources/机制深度），结论记入 daily log
- [ ] No owner-doc update required（分析报告 §6 已注明执行注记由 closure 补）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 行为/契约结果已达成（概念章规划落地 + 密度达标 + 断言抽检闭环 + mindmap/快照）
- [ ] 必要 focused verification 已完成（fixture 双向验证 + nop-jq 端到端前后对照）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [ ] 受影响的 owner docs 已同步（分析报告加执行注记、Status 视结果更新）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证新检查项真实生效（对违规 fixture 报错、合规 fixture 放行），概念章在真实产出中存在且非目录投影
- [ ] `node --check` 全部脚本
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### 产品形态项（十进制对外编号/llms.txt/MCP/静态托管/多语言）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属对外发布形态，与内容质量轴正交；分析报告 §8 已归档，需要时另立
- Successor Required: `no`

## Non-Blocking Follow-ups

- wiki 增量更新实战轮次验证
- `~/ai/` 269 仓库中选 1-2 个（如 deepagents）做跨仓库 scale 验证
- `~/ai/deepagents/` 读源取证 OpenWiki claim 系统实现细节（A3 的强化参考）

## Closure

Status Note: （关闭时填写）
Completed: （关闭时填写）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent）
- Evidence: （每条 Exit Criterion / Closure Gate 的 PASS/FAIL + live 证据）

Follow-up:

- 见 Non-Blocking Follow-ups
