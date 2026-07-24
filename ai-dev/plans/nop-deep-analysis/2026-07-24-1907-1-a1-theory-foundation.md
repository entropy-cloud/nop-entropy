# A1 理论基石与设计哲学（可逆计算 / XLang / XDSL / XDef）

> Plan Status: completed
> Mission: nop-deep-analysis
> Work Item: A1 理论基石与设计哲学（可逆计算 / XLang / XDSL / XDef）+ 联网对标模型驱动与 DSL 理论
> Last Reviewed: 2026-07-24
> Source: `ai-dev/design/nop-deep-analysis/nop-deep-analysis-roadmap.md` (Work Item A1)
> Related: roadmap 依赖图中 A2/A3 依赖本项建立的统一词汇表

## Purpose

把可逆计算原理如何在 nop-entropy 中落地为 XLang / XDSL / XDef 三件套讲清楚，产出一份分析文档，为后续 A2（核心引擎剖析）和 A3（模型驱动 / 代码生成 / Delta）建立统一的术语基线和理论参照。

## Current Baseline

**已有的理论语料（充足但偏理论阐述）：**

- `docs/theory/grc-explained.md` — 可逆计算（GRC）核心概念解释
- `docs/theory/generalized-reversible-computation-paper.md` 及 v2 — GRC 形式化论文
- `docs/theory/grc-delta-associativity-formal-proof.md` 及 v2 — Delta 结合律形式化证明
- `docs/theory/xlang-explained.md` — XLang 解释
- `docs/theory/xdsl-design.md` — XDSL 设计
- `docs/theory/delta-oriented-programming.md` — Delta-Oriented Programming 讨论
- `docs/theory/delta-vs-extension.md` — Delta vs Extension 区分
- `docs/theory/why-xml.md`、`xml-json-equivalence.md` — 为什么用 XML 作为 DSL 载体
- `docs/theory/reversible-compuation-vs-bidirectional-transformation.md` — 与双向变换对比（注意：仓库内文件名拼写为 "compuation"）
- `docs/theory/what-does-reversible-mean.md` — "可逆"的含义澄清
- `docs/theory/deep-dive-into-xdef.md` — XDef 深入讲解

**工程实现锚点（已记录于 source-anchors.md）：**

- `EXT-001`: `nop/schema/xdsl.xdef` — 平台所有 DSL 的元模型定义入口
- `EXT-002`: `XDslExtender.java` — `x:extends` / `x:gen-extends` / `x:post-extends` / `x:override` 的核心展开与合并执行链
- `EXT-003`: `DeltaResourceStore.java` — Delta/VFS 分层资源解析核心
- `EXT-004`: `DslModelParser.java` + `INeedInit.java` — XDSL 模型到运行时对象的统一解析
- `EXT-005`: `GlobalFunctions.java` (`loadDeltaJson`) — JSON/YAML 类模型可复用 Delta 机制
- `EXT-006`: `XplLibTagCompiler.java` — 编译期元编程的命名空间属性放宽
- `XLANG-001~008`: XPL / XLib / XDSL schema 锚点

**主要缺口（本 plan 要补齐）：**

- 缺一份把「理论公理 → 工程机制 → 代码锚点」串成一条线的综合分析
- 缺联网对标：nop 的可逆计算与 MDSD、Language Workbench（MPS）、Delta-Oriented Programming、双向变换（bx/lenses）的系统性差异定位
- 缺后续章节可复用的统一词汇表（GRC 公理、Delta 合并语义、XDef 元模型驱动、XDSL 可叠加性）

## Goals

- 产出 `ai-dev/analysis/2026-07/2026-07-24-nop-theory-foundation.md`，覆盖：可逆计算（GRC）核心公理、`x:extends` / Delta 合并语义、XDef 元模型驱动、XDSL 可叠加语言、`xpl` 模板与作用域
- 建立 A2–A7 后续章节可直接引用的统一术语表
- 联网调研并对标 MDSD、Language Workbench（JetBrains MPS / projectional editing）、Delta-Oriented Programming、双向变换（bx / lenses），说明 nop 的差异化定位，附来源链接
- 所有事实性论断用 source-anchors.md 锚点 + LSP/源码交叉核对

## Non-Goals

- 核心引擎内部实现细节剖析（属 A2）
- 代码生成管线与 model-first 开发范式的详细讲解（属 A3）
- GraphQL CRUD 自动暴露、服务层约定（属 A4）
- 业务模块矩阵（属 A5）
- 新功能实施或代码变更（仅产出分析文档）

## Scope

### In Scope

- 可逆计算（GRC）核心公理与 nop 的落地映射（`x:extends`、`x:override`、`x:gen-extends`、`x:post-extends`）
- XDef 元模型驱动：DSL 的 schema 定义如何约束并生成解析链
- XDSL 可叠加语言：多 DSL 如何共享统一扩展语法
- `xpl` 模板语言与作用域、`xlib` 标签库的编译期元编程能力
- Delta 合并语义：`super:` / `x:extends` / `x:override` 的差量合并规则
- 联网对标：与 MDSD、MPS / projectional editing、Delta-Oriented Programming、bx/lenses 的差异定位

### Out Of Scope

- 各核心模块（core / xlang / xdef / dao / graphql / ioc）的逐行实现剖析（A2）
- ORM model-first 开发流程、codegen 模板、`_gen/` 生成物（A3）
- 具体业务 DSL（如 wf / batch / task）的字段级设计（A5）

## Execution Plan

### Phase 1 - 源材料梳理与 live baseline 交叉核对

Status: completed
Targets: `docs/theory/` 核心论文、`docs-for-ai/04-reference/source-anchors.md`（EXT-001~006, XLANG-001~008）、`nop-xlang`/`nop-xdef` 实现锚点类

- Item Types: `Proof | Decision`

- [x] 通读 GRC 核心论文与解释（`grc-explained.md`、`generalized-reversible-computation-paper*.md`、`grc-delta-associativity-formal-proof*.md`），提炼可逆计算的公理体系
- [x] 通读 XLang/XDSL/XDef 设计文档（`xlang-explained.md`、`xdsl-design.md`、`deep-dive-into-xdef.md`、`delta-oriented-programming.md`、`delta-vs-extension.md`），梳理理论到工程的映射
- [x] 搜索 `ai-dev/analysis/` 是否已有可复用的 Delta/XLang/可逆计算对比结论，避免重复研究
- [x] 对 source-anchors.md 中 EXT-001~006、XLANG-001~008 锚点做 LSP/源码确认，验证文档描述的合并语义、解析链与实际代码一致；交叉核对结果记录到当日 `ai-dev/logs/` 条目
- [x] 整理统一术语表草稿（GRC 公理、Delta 合并语义、XDef 元模型驱动、XDSL 可叠加性、xpl 作用域），存入当日 daily log

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 已提炼出可逆计算公理体系（至少包含：系统分解为可逆差量、Delta 结合律、生成即逆元）并标注每条公理对应的代码锚点
- [x] 已完成 EXT-001~006、XLANG-001~008 至少 8 个锚点的源码交叉核对，记录核对结果（PASS/FAIL + 锚点路径）—— 实际核对 11 个锚点，全部 PASS（见 `ai-dev/logs/2026/07-24.md` §5）
- [x] 统一术语表草稿已成型，覆盖 GRC / XDSL / XDef / xpl 四个维度（见 `ai-dev/logs/2026/07-24.md` §6，完整版在分析文档 §4）
- [x] 若核对中发现 source-anchors.md 或 docs/theory 描述与实际代码不一致，已记录为待修复项 —— 源码补充（8 个 override 模式 vs 文档暗示 5 个、DeltaMerger 类名、XLANG-008 渲染列表）记录为分析文档 Open Questions（非事实性偏差，仅完整性补充）
- [x] No owner-doc update required: Phase 1 为源材料梳理，不修改任何 owner doc
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/07-24.md`）

### Phase 2 - 联网调研与下一代框架对标

Status: completed
Targets: 外部框架文档与论文（web search）

- Item Types: `Proof`

- [x] 调研 Model-Driven Software Development（MDSD）：核心理念、与 nop 差异
- [x] 调研 Language Workbench / projectional editing（JetBrains MPS 为代表）：与 XDef 元模型驱动的对比
- [x] 调研 Delta-Oriented Programming（SPL 领域，Schaefer 等）：与 nop Delta 合并的对比
- [x] 调研双向变换（bx / lenses / synchronization）：与可逆计算"可逆"语义的对比
- [x] 每条调研附来源 URL + 访问日期，提炼 nop 的差异化定位；调研结果汇总到当日 daily log

Exit Criteria:

- [x] 至少覆盖 4 个对标方向（MDSD / Language Workbench / Delta-Oriented Programming / 双向变换），每个附 ≥1 来源链接
- [x] 每个方向有明确的「该框架做什么 vs nop 做什么 vs 差异点」三段式对照（见 `ai-dev/logs/2026/07-24.md` Phase 2，完整版在分析文档 §5）
- [x] 所有外部链接附有访问日期（2026-07-24；链接存活以调研时为准，closure audit 验证链接已记录而非实时可访问）
- [x] No owner-doc update required: Phase 2 为联网调研，不修改任何 owner doc
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 撰写分析文档与准确性终检

Status: completed
Targets: `ai-dev/analysis/2026-07/2026-07-24-nop-theory-foundation.md`

- Item Types: `Proof | Decision`

- [x] 按 `ai-dev/analysis/00-analysis-writing-guide.md` 模板撰写分析文档，含 Status / Date / Scope / Conclusion / References 元数据
- [x] 正文结构：① 可逆计算公理体系 → ② XLang/XDSL/XDef 三件套工程映射 → ③ xpl/xlib 编译期元编程 → ④ 统一术语表 → ⑤ 联网对标与差异定位 → ⑥ 开放问题
- [x] 所有平台内部引用使用 `file:line` 锚点格式
- [x] 完成全文事实性论断与源码的一致性终检（逐条核对文档论断 ↔ 代码锚点）—— 独立子 agent closure audit 全部 PASS

Exit Criteria:

- [x] 分析文档存在于 `ai-dev/analysis/2026-07/`，命名符合 `2026-07-XX-<slug>.md` 规范（实际 `2026-07-24-nop-theory-foundation.md`）
- [x] 文档含完整元数据（Status: resolved / Date / Scope / Conclusion / Superseded By），正文含 References 章节
- [x] 统一术语表章节存在，且术语可被 A2–A7 直接引用（§4，覆盖 GRC/XDSL/XDef/xpl 四维度）
- [x] 联网对标章节存在，含 ≥4 个对标方向且附来源链接（§5，MDSD/MPS/DOP/bx-lenses）
- [x] 准确性终检完成：文档中每条涉及代码的事实性论断都有对应 source-anchor 或源码路径可验证（独立 audit 抽查 8 override 模式 + DslModelParser init + DeltaMerger 实例化 + xdsl.xdef + 3 处理论文档引用，全部 PASS）
- [x] 若终检发现 docs/theory 或 source-anchors.md 有事实性偏差，已记录到 Open Questions 或建议修复项（§6 记录 5 项，均为完整性补充/溯源，非事实性偏差）
- [x] No owner-doc update required: 本 plan 仅产出分析文档，不修改 `docs-for-ai/` 或 `docs/theory/`（若发现偏差，记录到 Open Questions 供后续决策）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/07-24.md`）

## Closure Gates

> **纯文档计划**：本计划不涉及任何代码变更（仅产出 `ai-dev/analysis/` 下的分析文档），`./mvnw test`、`./mvnw compile` 等构建验证条目不适用。

- [x] 分析文档 `ai-dev/analysis/2026-07/2026-07-24-nop-theory-foundation.md` 已产出且含完整元数据
- [x] 可逆计算公理体系已建立并与代码锚点交叉核对（9 条公理 + 公理→代码锚点对照总表）
- [x] 统一术语表已成型（可供 A2–A7 引用，§4 覆盖 GRC/XDSL/XDef/xpl 四维度）
- [x] 联网对标章节覆盖 ≥4 个方向，附来源链接（§5：MDSD/MPS/DOP/bx-lenses，均附 URL + 访问日期）
- [x] 不存在被静默降级到 deferred 的 in-scope 分析要求（独立 audit Check E 确认 Open Questions 5 项均为完整性补充/溯源/A2 衔接，非 in-scope 降级）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（task `ses_06c1e0213ffe`，APPROVED FOR CLOSURE）
- [x] **Anti-Hollow Check**：closure audit 已验证文档中的关键论断（合并语义、解析链、元模型驱动）在源码中确实成立，非纯理论空谈。关键论断已覆盖：`x:extends` 合并语义（8 种 override 模式 + 默认 merge）、`DslModelParser` 初始化链（`INeedInit.init()` 调用点 L133-134）、XDef 驱动的解析与校验（`xdsl.xdef` 元模型入口 + `DeltaMerger`/`XDslExtender` 合并执行）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 已运行（A1 相关链接已修复为实际文件名；剩余 2026-07-XX 占位链接属 A2–A7 未执行 plan，本 mission 标记 non-blocking，见 mission-driver step 说明）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0（见下方 Closure 证据）

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- 若准确性终检发现 `docs/theory/` 或 `source-anchors.md` 有事实性偏差，记录到分析文档 Open Questions，不在本 plan 内修复（修复属独立的文档维护任务）
- 是否将统一术语表迁移到 `docs-for-ai/` 由 A7 capstone 综合评估后决定

## Closure

Status Note: A1 理论基石与设计哲学分析已完成。产出 `ai-dev/analysis/2026-07/2026-07-24-nop-theory-foundation.md`，建立了可逆计算（GRC）9 条公理到 XDef/XDSL/xpl 三件套的完整工程映射，经 11 个 source-anchor 源码交叉核对（全部 PASS）+ 独立子 agent closure audit（APPROVED）。统一术语表（GRC/XDSL/XDef/xpl 四维度）可供 A2–A7 直接引用。4 方向联网对标（MDSD/MPS/DOP/bx-lenses）明确了差异化定位。本计划为纯文档产出，无代码变更。
Completed: 2026-07-24

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task id `ses_06c1e0213ffe`）
- Audit Session: `ses_06c1e0213ffe`（与执行阶段 3 个 explore 子 agent 不同 session）
- Evidence:
  - **Check A（文档完整性）PASS**：元数据齐全（Status: resolved / Date / Scope / Conclusion / Superseded By / References）；6 个正文章节齐全（公理体系/三件套映射/xpl 元编程/术语表/联网对标/开放问题）
  - **Check B（Anti-Hollow，关键）PASS**：
    - `XDefOverride.java:19-50` 确认 8 种 override 模式 + 默认 `merge`（`DeltaMerger.java:49` + `xdsl.xdef:70` 双重确认）
    - `DslModelParser.java:133-134` `INeedInit.init()` 调用点逐字核对一致
    - `DeltaMerger.java:40` 是合并类，`XDslExtender.java:78` 实例化、L457 调用 `merger.merge(...)` 均核对一致
    - `xdsl.xdef` 存在且为元模型入口（L14-19）
    - 4 方向联网调研各有 ≥1 来源 URL + 访问日期
    - 附加抽查 `XDslKeys.java:72,74,75,85`、`DeltaResourceStore.java:251-294`、`task.xlib:18` 均一致
  - **Check C（理论文档引用一致性）PASS**：合并顺序 `F -> E -> Model -> D -> C -> B -> A`（why-xlang L408-410）、`XNode = XNode + Delta`（delta-vs-extension L45）、"语言即坐标系"（paper-v2 §5.2 L259）、XLang 组成公式（xlang-explained3 §3 L38）均逐字核对，无捏造引用
  - **Check D（Exit Criteria 覆盖）PASS**：文档命名/元数据/术语表/联网对标/代码锚点可验证性全部满足
  - **Check E（deferred 诚实性）PASS**：§6 Open Questions 5 项均为完整性补充/溯源/A2 衔接，无 in-scope 分析要求被静默降级
  - **`check-plan-checklist.mjs --strict`** 退出码 0（见下方运行结果）
  - **Anti-Hollow 检查结果**：本计划为纯文档型，无新增代码/组件，故无调用链连通性/空方法体检查适用；改为验证"文档论断 ↔ 源码"一致性（Check B/C 已完成，全部 PASS）
  - **Deferred 项分类检查**：无 in-scope live defect 被降级；§6 的 5 项均为 watch-only residual / out-of-scope doc-maintenance
- `node ai-dev/tools/check-doc-links.mjs --strict`：A1 相关占位链接（`2026-07-XX-nop-theory-foundation.md`）已修复为实际文件名 `2026-07-24-nop-theory-foundation.md`；剩余 `2026-07-XX-` 占位链接属 A2–A7 未执行 plan 的交付物占位，本 mission 的 doc-links 检查标记为 non-blocking（见 mission-driver step 3a 说明）
- `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`：退出码 0（无未勾选项 + Closure Evidence 已写入）

Follow-up:

- §6 Open Questions 5 项（"生成即逆元"溯源、`x:override` 8 模式文档化、DeltaMerger 类名补充、XLANG-008 渲染列表、结合律形式覆盖范围）均为 non-blocking 文档完整性/溯源项，建议由 A7 capstone 综合评估后决定是否迁移到 `docs-for-ai/`
- 是否将统一术语表迁移到 `docs-for-ai/` 由 A7 capstone 决定
- 无剩余 plan-owned 工作
