# docs-for-ai 与 DeepWiki 文档组织对比分析

> Status: open
> Date: 2026-08-04
> Scope: `docs-for-ai/` 文档体系 vs DeepWiki (`https://deepwiki.com/entropy-cloud/nop-entropy`) 自动生成文档
> Conclusion:（分析进行中，见 Conclusion 章节）

## Context

- **问题**：`docs-for-ai/` 是本仓库唯一的 AI/开发者使用手册，定位为"操作型任务路由"。DeepWiki 是第三方服务基于全仓库（`docs/`、`docs-en/`、`ai-dev/`、源码）自动生成的百科式文档。两者来源、结构、受众都不同。
- **动机**：评估 `docs-for-ai/` 是否存在结构性盲区，以及能否从 DeepWiki 的组织方式中借鉴改进点，同时不破坏其"AI 编码手册"的定位。
- **数据来源**：
  - `docs-for-ai/`：139 文件（129 个 `.md`），10 个子目录。
  - DeepWiki：约 21 个顶层章节、90+ 子页面（截至 2026-07-27 索引，commit `2d1fa816`）。

---

## Analysis

### 1. 组织哲学：任务驱动 vs 知识驱动

| 维度 | docs-for-ai | DeepWiki |
|------|-------------|----------|
| **组织主线** | 任务/决策驱动（"我要做 X，去哪？"） | 系统/组件驱动（"解释平台怎么工作"） |
| **入口形态** | 单一 `INDEX.md` 超大路由表（238 行，~90 条任务→文档映射） | 多层级目录树（Overview → Getting Started → Theory → 各引擎… → Glossary） |
| **阅读模式** | 跳跃式：按任务查表，定位到 1-2 个文件执行 | 线性/递进式：自顶向下构建心智模型 |
| **典型条目** | "写 BizModel 方法" → `03-runbooks/write-bizmodel-method.md` | "Business Model Framework (BizModel)" → 10.x 七个子页面层层展开 |
| **优化目标** | 让 AI agent 用最少 token 完成正确编码 | 让人类/学习者系统理解平台全貌 |

**核心判断**：两者**互补，非替代**。docs-for-ai 是"操作手册"，DeepWiki 是"教科书"。改进 docs-for-ai 不应把它变成 DeepWiki，而应补齐其作为操作手册所缺失的、对操作正确性有影响的"理解性"内容。

---

### 2. 内容覆盖差异

#### 2.1 DeepWiki 有、docs-for-ai 缺的内容

| DeepWiki 章节 | docs-for-ai 现状 | 影响评估 |
|---------------|------------------|----------|
| **Glossary**（21.1 / 21.2 术语表） | **无**。Nop 大量术语（BizModel / XBiz / XDSL / XDef / Delta / `x:extends` / `x:override` / CrudBizModel / IGraphQLEngine / S-N-V 加载）散落各处，无集中定义 | **高**。AI 易把同名概念混淆（如 XBiz action vs BizModel method） |
| **Core Theoretical Foundations**（3.x 可逆计算理论，含 `App = Δ x-extends Generator<DSL>` 公式、Δ 代数性质、S-N-V 加载管线） | 故意 defer 到 `docs/theory/`，仅 `06-extensibility/platform-extensibility-mechanism.md` 浅尝 | **中高**。AI 对 Delta/x:extends 的可逆性边界理解不足时，易写出"看似能扩展实则破坏可逆性"的代码 |
| **Architecture at a Glance**（1.2 单页架构图 + 请求流） | 分散在 `02-core-guides/architecture-principles.md` + `service-layer.md` + `api-and-graphql.md` | **中**。新 AI 无单页"全局心智模型"可读 |
| **Getting Started / Installation**（2.x clone/build/run demo） | `03-runbooks/bootstrap-new-application.md` 是"创建外部应用"，非"先跑通平台 demo" | **低-中**。docs-for-ai 受众是 app-builder，非首次接触者，优先级低 |
| **Comparison with Traditional Frameworks**（3.4） | 无（定位上不强调"为何选 Nop"） | **低**。不影响编码正确性 |
| **Native Image / GraalVM**（6.3） | 无 | **低**（除非项目走向 native image 部署） |
| **Frontend Microkernel Architecture**（14.3） | 仅 `frontend-rendering-pipeline.md` 触及 | **低-中** |
| **XLang 语言深度参考**（4.x 拆为 XDef / Xpl / XScript / XDSL 四个独立详细页） | `xlang-and-xpl-basics.md` + `xdef-and-xdsl.md` 压缩覆盖 | **中**。深度不如 DeepWiki，但作为"够用即可"的编码手册可接受 |

#### 2.2 docs-for-ai 有、DeepWiki 缺的内容

| docs-for-ai 内容 | DeepWiki 现状 | 价值 |
|------------------|---------------|------|
| **Runbooks**（40+ 个步骤型任务手册） | **完全无**。DeepWiki 是"是什么"，不是"怎么做" | docs-for-ai 的核心护城河 |
| **Autonomy Levels / Protected Areas / Hard-Stop 规则** | 无等价物 | 治理性内容，DeepWiki（自动生成）不可能产出 |
| **自检清单**（`bizmodel-method-selfcheck.md`、`debugging-checklist.md`） | 无 | 操作正确性的关键保障 |
| **任务路由表**（INDEX.md） | 仅目录式导航 | AI token 效率优势 |
| **`ai-dev/` 集成**（plan-first / ask-first / 日志） | 无 | 平台开发流程闭环 |
| **模块级使用文档**（`03-modules/` 16 个文件，"这个模块能做什么、怎么用"） | DeepWiki 15.x 把各模块当"高级特性"平铺，无"场景→模块"路由 | docs-for-ai 的场景驱动更实用 |

---

### 3. 来源、引用与维护机制

| 维度 | docs-for-ai | DeepWiki |
|------|-------------|----------|
| **生成方式** | 人工撰写、curated 规则 | 服务自动从全仓库 re-index 生成 |
| **引用源** | 主要 `docs-for-ai/` 自身 + `04-reference/source-anchors.md` 集中锚点 | 全仓库：`docs/`、`docs-en/`、`ai-dev/`、源码 `.java`、`pom.xml` 均纳入 |
| **行内引用** | 极少（多数只给路径） | **每一句断言都带 `file:line-range` 引用**（如 `README.md:18-27`） |
| **可信度验证** | 依赖人工同步 + doc-link checker | 引用即验证（指向真实代码行） |
| **漂移风险** | 高（人工维护，易与代码脱节） | 低（自动 re-index），但语义准确性依赖生成模型 |
| **图表** | 几乎纯文本 | 自动生成 Mermaid（架构图、请求流、Delta 合并、S-N-V 管线） |
| **语言** | 中文为主（代码/路径为英文） | 英文 |

**关键洞察**：DeepWiki 的**行内 `file:line` 引用**是其最有价值的机制——它让每条断言可验证、可定位，且代码漂移时引用失效会暴露（re-index 时）。docs-for-ai 依赖集中式 `source-anchors.md`，一旦锚点过时，core-guides 里的描述不会自动失效，**漂移更隐蔽**。

---

### 4. 结构深度对比（以 BizModel 为例）

**DeepWiki 10.x 拆分**（百科式纵深）：
1. BizObject System Overview
2. CrudBizModel and Built-in CRUD Operations
3. Query Validation and Filter System
4. Business Actions and Extension Points
5. QueryBean Left Joins and Security
6. XBiz XML Action Definitions
7. Processor and Step Architecture

**docs-for-ai 对应**（操作式路由）：
- `02-core-guides/service-layer.md`（主干规则）
- `03-runbooks/write-bizmodel-method.md`（怎么写）
- `03-runbooks/extend-crud-with-hooks.md`（扩展）
- `04-reference/bizmodel-method-selfcheck.md`（自检）
- `06-extensibility/`（可扩展机制案例）

**判断**：docs-for-ai 的"规则 + runbook + 自检"三件套在**操作正确性**上优于 DeepWiki；但在**概念纵深**（如 XBiz XML action 与 BizModel method 的精确边界、Processor/Step 架构）上不如 DeepWiki 系统。这符合两者定位，但意味着 docs-for-ai 在"AI 需要做非常规扩展"时可能支撑不足。

---

## docs-for-ai 改进建议

> 按"对操作正确性的影响 × 实施成本"排序。所有建议遵循一个约束：**不把 docs-for-ai 变成 DeepWiki**，只补齐影响编码正确性的理解性缺口。

### P0 — 高价值、低成本（建议尽快做）

**G1. 增加 Glossary（术语表）**
- 现状：Nop 术语高度重载（BizModel / XBiz / XDSL / XDef / Delta / x:extends / x:override / CrudBizModel / I*Biz / S-N-V），散落各文件，无统一定义。
- 建议位置：`04-reference/glossary.md`，并在 INDEX.md 路由表增加入口。
- 收益：消除 AI 对同名概念的混淆（如 "XBiz action" vs "BizModel method" vs "xbiz 文件"），这是当前 AI 误读的高频源。
- 成本：低（一次性整理，维护负担小）。

**G2. core-guides 增加行内源码引用**
- 现状：core-guides 多为断言式规则（"CrudBizModel 提供 doFindPage"），不带 `file:line`。漂移时无自动暴露机制。
- 建议：在关键 core-guides（service-layer、api-and-graphql、delta-customization、xdef-and-xdsl）的每条"事实性断言"后追加 `file:line` 引用（如 `nop-service-framework/nop-biz/.../CrudBizModel.java:120`），复用 DeepWiki 的引用范式。
- 收益：①可验证；②代码重构时引用失效即暴露 stale；③降低集中式 `source-anchors.md` 的单点过时风险。
- 成本：中（需逐条核对，但可与 `nop-doc-audit` skill 结合批量补）。

### P1 — 中价值、中成本

**G3. 增加单页"平台概念总览"**
- 现状：新 AI/人类无单页全局心智模型；DeepWiki 1.x 的 Overview + Architecture at a Glance 填补了这一层。
- 建议位置：`00-start-here/platform-overview.md`，含：分层架构图、请求流（REST/GraphQL → BizModel → ORM）、Delta 合成示意、模块依赖主干。
- 形式：Mermaid 图 + 少量文字，不超过 150 行。
- 收益：让 AI 在执行任务前有"全局坐标"，减少局部正确但全局错误的决策。
- 成本：中（需画准架构边界）。
- 注意：保持"总览"性质，不堆细节，细节仍在 core-guides。

**G4. 增加精简版"可逆计算要义"**
- 现状：docs-for-ai 故意 defer 理论到 `docs/theory/`，但 DeepWiki 证明一份**精炼到与机制对应**的理论摘要有价值——它让 AI 理解"为什么 x:extends 可逆、什么时候不可逆"。
- 建议位置：`06-extensibility/reversible-computation-essentials.md`，只覆盖：
  - `App = Δ x-extends Generator<DSL>` 公式 → 对应 Nop 机制（Generator=nop-codegen，Δ=_delta/，x-extends=XDslExtender）。
  - XDef 作为"语义坐标系"（稳定 key，非行号）。
  - S-N-V 加载三阶段（Structure Merge → Normalization → Validation）。
  - Δ 的代数性质（Closure / Associativity / Tombstone）对编码的**实际约束**（如：删除字段为什么用 tombstone 而非物理删；为什么 delta 可预合并）。
- 收益：AI 在做非常规 Delta 扩展时有理论依据，减少"破坏可逆性"的代码。
- 成本：中（需从论文提炼到操作级，避免变成理论搬运）。

### P2 — 可选（视受众策略）

**G5. Getting Started 快速上手**
- 现状：`bootstrap-new-application.md` 面向"建外部应用"，无"先 clone 本仓库、build、跑 demo、看 GraphQL playground"的入门。
- 建议：`00-start-here/first-run.md`。
- 优先级：低（docs-for-ai 受众明确是 app-builder，非首次接触者）。若平台想降低外部贡献门槛再做。

**G6. 关键入口英文版**
- 现状：docs-for-ai 中文为主，DeepWiki 英文，覆盖更广受众。
- 建议：至少 INDEX + start-here 提供英文版。
- 优先级：取决于国际化策略，属产品决策非文档质量决策。

**G7. 规范化图表**
- 现状：docs-for-ai 几乎纯文本。
- 建议：在 G3（总览）+ G4（理论要义）+ `delta-customization.md` 中加入 Mermaid 图（请求流、Delta 合并、S-N-V 管线）。
- 注意：Mermaid 对 AI 可读性增益有限，主要惠及人类；按需做。

---

## Conclusion

- **核心判断**：docs-for-ai 与 DeepWiki 是**互补关系**，不是替代关系。docs-for-ai 在"操作正确性"（runbooks、自检清单、治理规则、任务路由）上显著优于 DeepWiki；DeepWiki 在"概念纵深与可验证性"（理论体系、术语表、行内 file:line 引用、系统纵深页）上优于 docs-for-ai。
- **docs-for-ai 最值得补的 3 件事**（按 ROI）：
  1. **Glossary**（G1）——消除术语歧义，最低成本最高收益。
  2. **行内源码引用**（G2）——借用 DeepWiki 的可验证范式，降低漂移隐蔽性。
  3. **平台概念总览**（G3）——补齐单页心智模型缺口。
- **不建议做**：不要把 docs-for-ai 扩成 DeepWiki 式的百科。它的 40+ runbooks、autonomy 等级、protected areas、self-check 是 DeepWiki（自动生成）结构性无法提供的护城河，应继续深耕而非稀释。
- **否决的方案**：
  - "把 DeepWiki 内容搬进 docs-for-ai" —— 否决原因：定位冲突，会稀释操作手册的密度；且 DeepWiki 自动生成、搬运后立刻 stale。
  - "docs-for-ai 改全英文" —— 否决原因：当前 AI agent 中文任务为主，全英化是产品策略问题，非文档质量改进。
- **后续工作**：若采纳 G1-G4，建议分别开 plan（`ai-dev/plans/`）。G2 可与现有 `nop-doc-audit` skill 流程合并。

## Open Questions

- [ ] Glossary 应中英双语还是仅中文？DeepWiki 英文术语表是否可直接作为骨架翻译？
- [ ] 行内 `file:line` 引用是否会过度膨胀 core-guides 体积、降低 AI token 效率？需先在 1-2 个 core-guide 试点点验证密度。
- [ ] 是否需要一个自动化工具，在 `mvn` 构建时校验 core-guides 里的 `file:line` 引用是否仍存在（类似 doc-link checker 的扩展）？

## References

- `docs-for-ai/INDEX.md`（docs-for-ai 路由基线）
- `docs-for-ai/04-reference/source-anchors.md`（集中式锚点）
- `docs-for-ai/02-core-guides/service-layer.md`、`api-and-graphql.md`、`delta-customization.md`、`xdef-and-xdsl.md`（行内引用试点候选）
- `ai-dev/analysis/00-analysis-writing-guide.md`（本报告格式依据）
- DeepWiki: https://deepwiki.com/entropy-cloud/nop-entropy （索引基线 commit `2d1fa816`，2026-07-27）
  - Overview: `/entropy-cloud/nop-entropy/1-overview-of-nop-platform`
  - Repository Tour: `/entropy-cloud/nop-entropy/1.1-repository-tour-and-where-things-live`
  - Reversible Computation Theory: `/entropy-cloud/nop-entropy/3.1-reversible-computation-theory`
  - Glossary: `/entropy-cloud/nop-entropy/21.1-glossary-...` / `21.2-glossary-...`
