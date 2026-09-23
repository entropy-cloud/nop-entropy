# nop-code 图探索与导出增强设计

**日期**：2026-09-23
**范围**：`nop-code-service`（探索分析器编排 + GraphQL 暴露 + Wiki 导出）、按需扩展 `nop-code-core`/`nop-graph`
**状态**：目标架构（部分依赖前置条件，见 §3.0）
**灵感来源**：graphify v2（复合惊奇评分、`suggest_questions`、`--wiki`、`--watch`/git hook）

---

## 一、设计结论

1. **吸收意外连接发现**：以复合惊奇评分找出非显而易见的连接（详见 §3.1），归属 `nop-code-service/.../service/graph/`（依赖 `KnowledgeGapAnalyzer` 同层的 `IGraph` + 社区结果）。
2. **新增图谱问题生成**：从图结构信号派生机器可执行的探索引导（每条含类型、目标符号、建议查询），归属同上。这是 GraphQL 数据层能力，不引入 MCP。
3. **新增图谱 Wiki 导出**：按社区/枢纽节点生成互链 Markdown 文章集（`GraphWikiDTO`），扩展 `GraphExporter` 的代码领域导出能力。
4. **自动重建改为外部触发 + GraphQL mutation**：nop-code 只暴露重建 mutation，由外部适配器（webhook/CI/nop-job）调用；不做本地 watch daemon，不在 nop-code 内建 HTTP webhook 入口（详见 §3.4）。
5. **不吸收 Hypergraph**：无明确用例；`nop_code_flow_membership` 仅覆盖"多符号同属执行流"这一特例，不是通用超边模型。需真实场景验证后再评估。
6. **不重复吸收已覆盖能力**：语义边模型、边置信度分级、图快照对比已由既有设计/代码覆盖（详见 §3.5）。

---

## 二、背景与动机

nop-code 已具备确定性图分析（社区检测、关键节点、知识缺口、图导出、图快照对比），但偏向**结构度量**。代码理解还需要**探索/引导导向**的能力：

| 缺失 | 场景 | 影响 |
|------|------|------|
| 非显而易见连接发现 | "这两个模块为什么会有耦合？" | 架构异味、隐藏依赖无法暴露 |
| 探索引导生成 | AI 代理面对大图无从下手 | token 浪费在无方向探索上 |
| 图结构文档化 | 需要给人类/代理一份可读架构摘要 | 只能看 GraphML/Mermaid，缺叙事 |
| 变更后自动更新 | 代码提交后索引陈旧 | 需人工触发增量索引 |

其中"探索引导生成"与调研中识别的 P0 缺口 `suggestedNextQueries` 是同一能力——本设计将其落到图谱层（见 §3.2）。

---

## 三、核心设计

### 3.0 前置条件与依赖顺序

> **重要**：以下能力依赖尚未落地的前置项。实施前必须按序解决，否则部分信号为空转。

| 前置项 | 现状 | 影响 |
|--------|------|------|
| 社区结果持久化 | ❌ 每次查询重算 Leiden（未持久化） | 惊奇评分的"跨社区"、问题生成全部依赖社区映射 |
| 介数中心性持久化 | ❌ 每次查询重算，且 >10000 节点时跳过 | 问题生成 `bridge_node` 类型在大图失效 |
| 带属性的边投影 | ⚠️ `CodeCallGraph` 只投影 CALLS 边且不填 `Edge.attrs` | 惊奇评分需 relation/confidence/filePath |
| INFERRED/AMBIGUOUS 边投影 | ⚠️ AMBIGUOUS 无生产者；INFERRED 仅存在于 call/heuristic 层（`InterfaceImplSynthesizer`/`SpringEventSynthesizer`/未解析调用），未投影进 `IGraph` 边属性 | 惊奇评分的"置信度"维度、问题生成的 `ambiguous_edge` 空转（`verify_inferred` 需先投影 INFERRED 调用边） |
| 多仓/文档节点模型 | ❌ 每 indexId 单项目、仅代码符号 | 惊奇评分的"跨文件类型"、"跨目录/仓库"维度不适用 |

**处理原则**：本设计的评分维度与问题类型在**前置未满足时显式降级**（跳过该维度或返回 `no_signal`），不静默产生错误结论。

### 3.1 意外连接发现（Surprising Connections）

**职责**：在已有边集上找出"非显而易见"的连接，按惊奇度排序并给出可解释原因。

**输入投影**：`IGraph` 的边视图 + 节点属性（degree、社区归属、源文件类型）。**节点集来源需显式提供**（`IGraph` 只暴露 `getOutEdges`/`getInEdges`，无枚举方法）——由符号表或社区/中心性结果给出全节点集。领域属性经 `Edge.attrs` 承载（relationType/confidence/sourceFilePath/targetFilePath）。社区映射来自社区结果（需持久化，见 §3.0）。

**评分算法**（`SurpriseConfig` 可配，权重为启发式初值）：

```
score = 0
score += confBonus(edge.confidence)        # AMBIGUOUS=3, INFERRED=2, EXTRACTED=1
if sourceFileKind(u) != sourceFileKind(v): score += 2    # 跨文件类型
if topLevelDir(u) != topLevelDir(v):       score += 2    # 跨目录/仓库
if community(u) != community(v):           score += 1    # 跨社区
if edge.relationType == semantically_similar_to:
    score = int(score * 1.5)               # 乘性加权，在加性项之后、边缘-枢纽之前
if min(degree(u), degree(v)) <= 2 and max(degree(u), degree(v)) >= 5:
    score += 1                             # 边缘→枢纽
reasons = [每个命中维度的可读原因]
```

**关键约定（修正 graphify 的欠定义）**：
- **运算顺序固定**：先累加所有加性项，再对 `semantically_similar_to` 做 `int(score × 1.5)`，最后加"边缘→枢纽"。顺序影响排序，必须固定。
- **阈值显式化**：边缘→枢纽为 `min(deg)≤2 && max(deg)≥5`。
- **降级模式**：当无跨文件边（单源语料）时，退化为"跨社区边 + 边介数中心性排序"，与 graphify 的两模式一致。
- **排除规则**：纯结构边（`imports`/`contains`）不参与；文件级 hub 节点与概念节点排除。
- **复杂度**：仅在已有边上评分，O(E)；**不含**社区检测本身的开销（社区需前置物化）。

**不适用维度**（前置未满足时跳过）：跨文件类型（无文档/图片节点）、跨目录/仓库（单项目）、置信度（AMBIGUOUS 无生产者；INFERRED 未投影进 `IGraph`）。

**GraphQL 契约**：`NopCodeIndex__getSurprisingConnections(indexId, topN=20, minScore=?)` → `List<SurprisingConnectionDTO>`。DTO 字段：`sourceSymbolId`、`targetSymbolId`、`sourceLabel`、`targetLabel`、`sourceFilePath`、`targetFilePath`、`relation`、`confidence`、`score`、`reasons[]`。排序稳定（score 降序，同分按 symbolId 字典序）。鉴权沿用 `NopCodeIndex:query`。

**归属**：`ISurprisingConnectionAnalyzer` 在 `nop-code-service/.../service/graph/`（与 `KnowledgeGapAnalyzer` 同层）。

### 3.2 图谱问题生成（Graph Question Generation）

**职责**：从图信号派生**机器可执行**的探索引导，每条含类型、目标、建议的下一步查询。

**输入**：`IGraph` + 社区结果 + 中心性结果 + 符号表（名称解析）。

**问题类型与触发**（阈值可配）：

| 类型 | 触发信号 | 目标字段 |
|------|---------|---------|
| `ambiguous_edge` | 存在 AMBIGUOUS 边 | `sourceSymbolId`/`targetSymbolId` |
| `bridge_node` | 介数中心性 top-N（大图 >10000 节点时跳过） | `targetSymbolId` |
| `verify_inferred` | 枢纽节点含 ≥2 条 INFERRED 边 | `targetSymbolId` |
| `isolated_nodes` | degree ≤ 1 且非排除类 | `targetSymbolIds[]` |
| `low_cohesion` | 社区内聚度 < 0.1 且规模 ≥ 5 | `communityLabel` + `targetSymbolIds[]` |

**输出契约**（机器可执行，修正 graphify 的纯文案问题）：

`ExplorationQuestionDTO`：
- `type`（上述枚举）
- `question`（人类可读问句，填充符号名）
- `why`（生成原因，含触发信号）
- `targetSymbolIds[]`（可执行目标）
- `suggestedQuery`（**可直接发给 GraphQL 的查询字符串**，按类型给出模板；见下方示例）
- `priority`（数值，用于排序）

**`suggestedQuery` 模板**（每类型一条，参数取自 DTO 目标）：

| 类型 | suggestedQuery 模板 |
|------|---------------------|
| `ambiguous_edge` | `NopCodeSymbol__getBySymbolId(id:"{targetSymbolId}", indexId:"{indexId}")` |
| `bridge_node` | `NopCodeSymbol__getCallHierarchy(indexId:"{indexId}", qualifiedName:"{qualifiedName}", direction:"both", maxDepth:2)` |
| `verify_inferred` | `NopCodeSymbol__getCallHierarchy(indexId:"{indexId}", qualifiedName:"{qualifiedName}", direction:"outgoing", maxDepth:1)` |
| `isolated_nodes` | `NopCodeSymbol__getBySymbolId(id:"{targetSymbolId}", indexId:"{indexId}")` |
| `low_cohesion` | `NopCodeIndex__getKnowledgeGaps(indexId:"{indexId}")` |

> `direction` 取值为 `outgoing`/`incoming`/`both`（小写），且 `getCallHierarchy` 需要 `indexId`——模板必须完整，否则代理无法直接执行。

无信号时返回显式 `no_signal` 结果（单项，`type=no_signal`，`question=null`），与 `query-api-design.md` 的"空结果返回空列表"约定冲突——**本类型采用显式对象，需同步更新 `query-api-design.md` 例外说明**。

**为什么是图能力而非 LLM**：问题发现是确定性图查询，LLM 只负责回答。放 graph 层可被非 AI 消费者复用。

**GraphQL 契约**：`NopCodeIndex__getExplorationQuestions(indexId, topN=10)` → `List<ExplorationQuestionDTO>`。鉴权同 §3.1。

**归属**：`IGraphQuestionGenerator`，`nop-code-service/.../service/graph/`。

### 3.3 图谱 Wiki 导出（Graph-to-Wiki）

**职责**：将图结构渲染为互链 Markdown 文章集，作为代码领域导出格式。

**输出契约**（多文件，不能用单 `String`）：

`GraphWikiDTO { index: String, articles: Map<String, String> }`（key 为相对文件名）。

```
index.md                ← 目录索引（节点/边/社区统计 + 社区列表（按规模）+ 枢纽节点列表）
社区文章                ← 每个社区一篇：关键概念（按度数）+ 跨社区关系 + 源文件 + 置信度审计轨迹
枢纽节点文章            ← 每个枢纽节点一篇：按关系类型分组的邻居（带置信度标记）
```

文章文件名 = `index` 或 slug(社区标签/枢纽节点标签) + `.md`。

**互链语法**：标准 Markdown 相对链接形式，**不使用** Obsidian 双链语法（与 §四 拒绝 Obsidian 保持一致）。

**确定性要求**：文件名 slug 规则确定（替换 `/`、`:`、` `、`$` 等）；文章排序确定；**上限约束**（`maxCommunities`、`maxHubNodes`）防止大图产出成千上万篇。

**枢纽节点 vs 桥接节点**：Wiki 文章使用 **Hub（度中心性）**，与桥接（介数中心性）区分；命名沿用 `graph-analysis-design.md` §二。

**为什么需要独立入口**：现有 `GraphExporter.export(...) → String` 与 `exportGraph(indexId, format, communityView) → String` 无法承载文件集。新增 `NopCodeIndex__exportGraphWiki(indexId, maxCommunities?, maxHubNodes?)` → `GraphWikiDTO`，或在导出格式枚举中新增 `MARKDOWN_WIKI` 但返回值类型改为结构化 DTO。**推荐前者**（独立查询，语义清晰）。

**归属**：`IGraphWikiExporter`，`nop-code-service/.../service/graph/`（代码领域；复用 `nop-graph` 社区/导出工具）。

### 3.4 自动重建触发（Auto-rebuild）

**职责**：代码变更后触发增量索引，保证图不过期。

**架构对齐**：nop-code 是 GraphQL-only 服务，不做本地 watch daemon，也**不内建 HTTP webhook 入口**（避免与"无自有 REST 端点"约束冲突）。nop-code 只提供一个 GraphQL mutation，**由外部适配器调用**：

| 触发源 | 机制 | 归属 |
|--------|------|------|
| Git post-commit hook | 提交后发 GraphQL 请求 | 开发者环境（外部脚本） |
| VCS webhook | 远端 push 事件 → 外部 receiver → GraphQL 请求 | 基础设施 / CI |
| 定时任务 | nop-job 周期比对 commit 并调用 mutation | 平台任务 |

**nop-code 侧契约**：
- 新增 GraphQL mutation `NopCodeIndex__triggerRebuildFromCommit(indexId, repoUrl, commitish)`（或复用 `triggerIncrementalIndex` 语义，按其现有签名以 manifest 为准）。
- **源码来源**：索引服务从受信远端 clone/fetch 到服务端工作区；不接受"开发者本机路径"。
- **repo→indexId 映射**：由调用方提供 `indexId`（或服务端注册表解析）。
- **去抖/合并**：同一 indexId 在配置窗口内的连续 commit 合并为一次重建。
- **循环防护**：索引/导出产物一律写数据库或 artifact store，**绝不写入被索引仓库路径**，因此不存在"产物触发重建"。
- **幂等**：同一 commitish 重复触发无副作用（fingerprint 检测后跳过无变更文件）。
- **权限**：mutation 需 `admin` 角色（与既有写操作一致）。

> **未决**：`triggerIncrementalIndex` 现有签名接收 `manifestPath` 而非 `commitish`，需在实施时确定由服务端从 commitish 计算变更集（`git diff`）并生成 manifest，还是新增独立入口。此决策在本设计范围外，登记为实施前置项。

### 3.5 已覆盖能力的边界确认

| graphify v2 能力 | nop-code 覆盖位置 | 状态 |
|------------------|-------------------|------|
| 语义相似度边（`semantically_similar_to`） | `semantic-edge-design.md` §4.2（`name-sim` 提取器）+ 已实现代码 | 已实现（确定性部分，仅 EXTRACTED） |
| 边置信度分级（EXTRACTED/INFERRED/AMBIGUOUS） | `EdgeConfidence` 枚举（已实现） | 枚举已实现；AMBIGUOUS 无生产者，INFERRED 未投影进 `IGraph` |
| 图快照对比 | `graph-analysis-design.md` §五（`GraphDiffer`） | 已实现 |

§3.1 的语义相似度加权依赖已存在的确定性语义边；`×1.5` 因子可直接生效（不依赖 LLM）。

---

## 四、拒绝了什么

| 方案 | 拒绝理由 |
|------|---------|
| **新造 `ICallGraph`/`ISymbolTable` 接口** | `nop-graph-api` 的 `IGraph` 已是"屏蔽底层存储"的抽象；新造会造成平台双图接口。本设计以 `IGraph` + `Edge.attrs` 为输入 |
| **Hypergraph（超边）** | `nop_code_flow_membership` 是二元 `(flowId, symbolId)` 表，只能表达"多符号同属执行流"，**不能**表达任意超边（如 N 个共变文件、1 个测试覆盖 N 个符号）。暂缓需明确用例验证后再评估 |
| **本地文件监听 daemon（watch）** | 要求常驻进程与内存状态，与集群式无状态定位冲突；VCS 事件/定时任务更可靠 |
| **nop-code 内建 HTTP webhook 入口** | 与"不新建自有 REST 端点"约束冲突；改为暴露 GraphQL mutation 由外部适配器调用 |
| **MCP server 暴露探索能力** | GraphQL 已是通用 API；探索能力作为 GraphQL 查询暴露 |
| **Obsidian Vault 导出 / 双链语法** | 特定工具格式；Wiki 互链使用标准 Markdown 相对链接 |
| **LLM 驱动的问题生成** | 问题发现是确定性图查询，LLM 只回答不发现，避免幻觉与成本 |
| **Pure 文案问题（无目标/无建议查询）** | 代理无法执行；本设计要求每条问题带 `targetSymbolIds` 与 `suggestedQuery` |
| **全对全相似度惊奇评分** | O(N²) 不可扩展；评分只在已有边上计算 |
| **意外连接独立为新模块** | 与 `KnowledgeGapAnalyzer` 同层同输入，归 `nop-code-service/.../service/graph/` 即可 |
| **复制 graphify 的调色板维度** | graphify 面向"代码+文档+图片"混合语料；nop-code 是纯代码单项目，跨文件类型/跨仓库维度不适用，显式跳过 |

---

## 五、与已有设计的关系

| 主题 | 文档 | 关系 |
|------|------|------|
| 产品定位、约束、non-goals | `00-vision.md` | 本设计的集群/无状态/无 MCP/框架边界依据；约束 9（框架不入核心）说明本设计不引入框架语义 |
| 模块边界、`IGraph` 抽象、实现状态 | `01-architecture-baseline.md` | 本设计输入用 `IGraph` + `Edge.attrs`；新分析器归 `nop-code-service`；§3.0 前置项对应 baseline §6.2 |
| 社区检测、关键节点、知识缺口、图导出、图快照 | `graph-analysis-design.md` | 本设计扩展其 §六"意外连接"；Wiki 导出扩展图导出；枢纽/桥接命名沿用其 §二 |
| 语义边模型与提取器 | `semantic-edge-design.md` | §3.1 语义相似度加权依赖其确定性提取器（已实现） |
| GraphQL API 归属与约定 | `query-api-design.md` | 新增 3 个查询/导出按索引级归属 `NopCodeIndex`；§3.2 的 `no_signal` 约定需在其例外说明中登记 |
| nop-search 集成 | `search-integration-design.md` | 无直接依赖；探索引导可作为搜索的引导层 |
| 顶层图算法库 | `docs-for-ai/01-repo-map/module-groups.md` | `nop-graph` 提供通用算法（Leiden/中心性/Bfs/导出） |

---

## 附录：待同步的既有文档

实施本设计时需同步修正（属本次审查发现）：

1. `01-architecture-baseline.md` 中 `ICallGraph`/`ISymbolTable` 引用 → 已在本轮校正为 `IGraph`。
2. `query-api-design.md` 需登记 `no_signal` 例外与新增 3 个 API。
3. `graph-analysis-design.md` §六 已指向本文档。
