# nop-deepwiki 产出 vs deepwiki.com：内容质量差距与方法论吸收分析

> Status: open
> Date: 2026-09-26
> Scope: `.opencode/skills/nop-deepwiki/` 生成 wiki 的**内容质量** vs deepwiki.com（facebook/react、entropy-cloud/nop-entropy 一手抽样）vs `ai-dev/analysis/deepwiki-survey/` 12 份开源实现的方法论
> Revision: v3（2026-09-26 补 §5.5 源码实证增补，~/sources/deepwiki 12 仓读源）｜v2——v1（同日）误把轴心放在产品形态（页数规模/llms.txt/MCP/托管），经裁定纠正：问题轴心是**同一仓库下生成内容的质量差距**与**可吸收的生成方法论**，产品形态维度降为附注（§8）
> Conclusion:（见 §5-§7）

## 1. Context

用户问题：我生成的 deepwiki **质量**比 deepwiki.com 差在哪、怎么改进、survey 里那些开源工具的做法有什么可吸收的——不是如何做一个同类软件。

调研输入：
- deepwiki.com 一手抽样（react 8 章 ~35 页、nop-entropy 21 章 100+ 页），含逐页要素分析；
- `ai-dev/analysis/deepwiki-survey/` 12 份文档（12 个开源实现的管线与方法论，本文引用格式 `[NN]`）；
- 自有产出：nop-jq wiki（compact 9 页）生成全程记录（logs 09-25/09-26）；
- 本地资源核实：`~/ai/deepwiki` 路径不存在；`~/ai` 下有 269 个已下载仓库（含 `deepagents`——OpenWiki 的 harness 底座 [01]，可用于后续读源取证），survey 的 12 个项目源码不在本地。

## 2. deepwiki.com 的页面在"讲什么"——内容质量的一手拆解

以 react wiki 2.1 页（Fiber Work Loop and Scheduling）为标本，它的页面不是"组件目录"，而是**机制说明书**：

1. **概念驱动组织**：章节是"Core Reconciler Architecture""Scheduling Flow"这类**横切代码目录的概念**；nop-entropy wiki 的章节叫 "3.2 The Delta Formula: App = Delta x-extends Generator<DSL>"——直接把仓库的核心理论写成章节。页面回答"系统如何工作"，不回答"这个包里有什么"。
2. **算法级深度**：Work Loop 页把调度循环拆成阶段表、Lane 常量与 bit 值表、Commit 子阶段表——**数据结构 + 不变式 + 边界条件**级解释，读者能据此推演行为。
3. **表格是密度主载体**：每页 3-4 个表格（实体汇总、常量表、阶段对照）——表格迫使生成者把模糊叙述收敛为精确枚举。
4. **全站叙事连贯**：章与章构成"从请求到渲染到提交"的完整叙事弧，页面间按概念互相引用成网。
5. **每页 ~1,000 词 + 3 个 mermaid + "Sources:" 段末归属**——中等篇幅高密度，不追求长文。

## 3. 我们产出的内容质量自评（nop-jq 9 页实证）

逐页诚实复盘（对照 §2 五条）：

| 维度 | 我们的实际形态 | 判定 |
|------|---------------|------|
| 组织轴 | `modules/` 按包切（json-value-model/jsonpath/jq-frontend/jq-runtime）——**目录驱动** | 差距核心 |
| 机制深度 | jq-runtime 页讲透了 List 流语义与 `|=` 实现（好样本）；但 jsonpath 页大量篇幅是"段类型清单"，quickstart/glossary 是工具页 | 好坏随缘，无机制章保障 |
| 跨切面叙事 | 一次查询的端到端旅程只有 architecture 页 1 张时序图带过；"一条 JSON Path 如何被编译优化"散在模块页 | 缺独立机制章 |
| 表格密度 | PLAN 无要求，产出随机（有的页 2 个表，有的 0 个） | 缺位 |
| 证据视野 | 子代理只读 PLAN 指定文件（每页 ≥5 个）+ 自行补充；跨模块证据（"这个函数被谁用、影响什么行为"）靠子代理自觉 | 视野受限 |
| 生成轮次 | GATHER→WRITE 一轮出稿，无自检修订 | 单轮 |
| 严谨性 | 行级引用 + check-wiki 链接校验 + 信任边界 | **优于 deepwiki.com**（其行号本身可幻觉，HN 231 分讨论串核心批评） |

## 4. 差距根因：我们的规划把"代码结构"当成了"知识结构"

根因一句话：**PLAN 的模块地图直接映射目录/包，章节=目录的投影；deepwiki.com 的章节=概念的综合。** 由此派生全部下游差距——机制章没有位置（目录里没有"求值语义"这个包）、跨切面叙事没有载体、表格没有模板约束、深度被页数档位（compact 9 页）锁死。

次要根因：
- 生成协议单轮无修订（对比 [01] openwiki 的 plan→draft→claim-verify 循环、[11] 的 GATHER→THINK→WRITE 强制阶段——后者我们吸收了但 THINK 没有实质约束力）；
- 证据配比无指导（对比 [05] 的 60/20/20：相关证据 60%/结构上下文 20%/多样性补充 20%）；
- 大页面无多轮策略（对比 [05] 的 >50 chunk 转先大纲后填节）。

## 5. 方法论吸收清单（survey 12 份 → 我们 skill 的具体落点）

按"技法 → 来源 → 落点"列出，这是本报告的核心增量：

| # | 技法 | 来源 | 落进我们 skill 的哪一步 | 解决 §3 的哪条差距 |
|---|------|------|------------------------|-------------------|
| A1 | **概念聚类规划**：章节由 LLM 对组件做语义聚类产生（"更符合人类认知结构"），不直接映射目录；聚类失败回退固定页 | [03] CodeWiki cluster_modules | Phase 3 PLAN：新增"概念章规划"步骤——强模型基于 Phase 2 证据提出概念性章节（如"求值语义""路径赋值""错误模型"），目录映射只作为页面的证据来源而非章节轴 | 组织轴 |
| A2 | **Planner 追踪端到端流**：规划期显式产出"控制流/数据流"叙事清单，保证机制章存在 | [01] openwiki Planner 探索 manifests/entrypoints/public surfaces 并追踪 flows | Phase 3：PLAN 增"机制章"必选类型（≥2 个：核心数据流 + 一个核心子机制如路径赋值/过滤语义），模板例：`flow-*.md` | 跨切面叙事 |
| A3 | **Grounded Claims 抽样校验**：每个事实断言绑定行级证据且可机检 | [01] openwiki src/claims + inspect_claims | check-wiki 扩展 `--verify-claims`：按比例抽样页面断言中的 `file:line`，用 grep 验证该行确实包含断言关键词（行号幻觉检测）；closure/audit 抽人工复核 | 严谨性守恒（防 deepwiki.com 式引用幻觉） |
| A4 | **表格密度模板**：实体/常量/阶段对照表为页面标配 | deepwiki.com 实测（每页 3-4 表）+ [04] knowledge cards | Phase 4 派发模板硬约束：每页 ≥2 表格（实体表/常量表/流程阶段表任选）；模块页 ≥3 mermaid | 表格缺位 |
| A5 | **leaf-first 拓扑 + 父页引用实产**：子模块页先写，父级总览必须引用子页的真实产出（防父页空泛） | [03] CodeWiki 递归 agent 顺序 | Phase 4 批次纪律强化：父页派发 prompt 必须附"已产出的子页清单+各自一句话结论"，要求正文显式引用 | 全站叙事连贯 |
| A6 | **大页面两轮生成**：证据过载时先出大纲、确认后填节 | [05] >50 chunk 转先大纲后分节 | Phase 4 纪律条目已有雏形，规范化为：源材料 >30 文件或 >3000 行的页面强制两轮 | 机制深度（大页不被压缩） |
| A7 | **证据配比 60/20/20**：相关证据/结构上下文/多样性补充 | [05] shariqriazz/openwiki | Phase 4 派发 prompt 证据清单按此配比组织 | 证据视野 |
| A8 | **签名骨架前置**：超长文件给结构签名而非让子代理通读 | [04] AST 骨架、[12] tree-sitter 结构签名 | Phase 2 已有配方，改为强制：>500 行文件签名行进派发 prompt | token 成本（非内容质量，顺带） |
| A9 | **mindmap 全站导航图**：mermaid mindmap 一图总览全站 | [11] OpenDeepWiki 思维导图 | index.md 顶部生成 mindmap（gen-wiki-meta 脚本化，零 LLM） | 全站导航 |
| A10 | **页面快照锚点**：每页标注生成 commit + 日期 | deepwiki.com "Last indexed" | gen-wiki-meta 在 wiki-state 记录并在 index.md 页脚输出 | 可信度元信息 |
| A11 | **三层模型调度**：规划用强模型、页面生成用标准档 | [12] repositories-wiki Opus/Haiku/Sonnet | ZCode 会话内：PLAN 由主会话做（本就最强），页面子代理保持默认——现状已接近，显式写入纪律即可 | 一致性 |
| A12 | **质量评分抽样**：对产出做断言级抽检打分 | [03] CodeWiki quality score 方法论 | Phase 5 closure audit 增"断言抽检 10 条"固定项（与 A3 共用机制） | 整体质量闭环 |

**不吸收**（及理由）：RAG/embedding 检索（[01] 定位：行级 Claim 是确定性、RAG 是概率性；Grep+行号已满足）；硬编码固定章节（[06][09]——与 A1 概念规划冲突，取后者）；翻译/托管/MCP（产品形态，见 §8）。

## 5.5 源码实证增补（2026-09-26 补：`~/sources/deepwiki/` 12 仓读源结果）

> A1-A12 原基于 survey 文档转述；本节为读源取证后的增补与修正。取证代理逐仓读 prompt 原文与实现，产出"十大可吸收技法"，其中 5 个为 A 清单未覆盖的新技法。路径相对 `~/sources/deepwiki/`。

### 新技法（A 清单未覆盖，按对内容质量影响排序）

| # | 技法 | 源码证据 | 落点 |
|---|------|---------|------|
| N1 | **THINK 实质化：可检查退出 checklist + Phase Gate**——GATHER 退出必须"≥4-6 可用代码片段、完整端到端数据流理解、边界/失败/并发/扩展点已核查"；"If ANY uncertainty exists → go back and read the source again"；Phase 2→3 显式 gate | OpenDeepWiki `src/OpenDeepWiki/prompts/content-generator.md:353-366,980-994`（1217 行 prompt） | 直击"THINK 无约束力"根因——THINK 绑定可检查清单而非一句"请思考" |
| N2 | **页面机制深度 10 项检查表**——responsibilities/entrypoints/mechanisms-control flow/relationships/state-lifecycle/invariants-failures/extension points/config-operations/tests + "Do not turn the page into a source-file inventory" + "seedPaths are starting points, not research boundaries" | openwiki `src/agent/repository-prompts.ts:143-148` | 现成反"目录百科"措辞，直接进派发模板 |
| N3 | **宽松引用格式 + 确定性后处理**——模型只写 `Sources: [path:line]()` 空括号松格式；代码负责解析成真链接、按最长路径/basename 反查纠错、重建 details 块、拼行锚 | deepwiki-open `api/services/wiki/content.py:84-151`、`prompts.py:108-118` | 把引用正确性从模型责任改为代码责任——check-wiki 直接对接松格式 |
| N4 | **Mermaid 防坑清单**——强制 `graph TD` 禁 LR、节点 3-4 词、sequenceDiagram 8 种箭头逐一给语义、subgraph ID `sg_` 前缀防冲突、标签必带引号、ER 字段单 token，每条配反例 | deepwiki-open `prompts.py:65-95`；OpenDeepWiki `content-generator.md:681-730` | 图渲染失败/退化是隐性质量杀手；check-wiki 现在只查围栏不查语法 |
| N5 | **规划上下文压缩**——分析→规划两段式：1024 token 的分析结论（领域概念/分层/关键系统）喂给规划器，而非原始代码；scope 匹配不到文件的提案直接丢弃 | openwiki-shariqriazz `crates/openwiki-wiki/src/planner.rs:348-464,606` | 强模型规划时证据密度更高；scope 严格解析 = 规划幻觉过滤器 |
| N6 | **结构化输出三级回退 + 每页独立重试/占位页**——zod schema 定页面字段；XML→code-fence 剥离→regex 抽块回退、截断补合成闭合；失败页占位不炸全局 | repositories-wiki `packages/common/src/types.ts:71-90`；deepwiki-open `structure.py:179-209, tasks.py:268-285` | 产出可靠性，间接保质量 |

### 已有 A 项的源码级强化

| A 项 | 源码证据 | 强化内容 |
|------|---------|---------|
| A1 概念聚类 | CodeWiki `src/be/cluster_modules.py:390,493,637`、`prompt_template.py:141-175` | **聚类的验证与兜底链**：聚类结果 ≤1-2 模块判无效回退整仓模式；artifact（构建/CI/配置）叶子覆盖率 <80% 强制兜底模块收容；批间同名模块合并；super-group 拒绝伪合并（"Name each subsystem by its architectural role, not by a directory name"）。CLUSTER prompt 原文："DO NOT include components that are not essential"，但 artifact 显式保护 |
| A2/A5 机制章与父页 | openwiki `repository-prompts.ts:43-83`、`page-jobs.ts:181` | Planner 三步曲原文："Explore before submitting the plan... trace representative end-to-end control and data flows... Do not stop at directory names"；**"Page paths are final once submitted"**（路径锁定）；quickstart **排序到最后生成**（"the synthesis/navigation page"）；planner 强制填 relatedPages 导航字段；**"Organize around owned systems, runtime domains, and cross-system workflows rather than mirroring the source tree"**（组织轴的现成措辞） |
| A3 Claims 校验 | openwiki `src/claims/evidence/repository/resolver.ts`、`claims/guidance.ts:7-24` | 行号漂移重定位算法（区间内容哈希全文件扫描→前后 3 行锚夹逼→歧义返回 null 强制复核，不猜）；**Claim 实质性标准**："Do not create a Claim merely because a symbol exists... unless that fact materially changes how a reader understands..."；**对账纪律**："stale ≠ 自动撤销，必须显式决定"、"The final page body and reconciled Claim set must agree" |
| A6 大页两轮 | openwiki-shariqriazz `generator.rs:434-505` | 实现参数：大纲轮刻意只给 **1/3 上下文预算**（逼看结构不看细节）；每节独立上下文与 token 预算（`section_budget = max/sections.len()`，下限 2048）；解析失败三级回退到单轮 |
| A8 骨架前置 | RepoWiki_temp `core/skeleton.py:53` | 实现样本："# [skeleton: N symbols from M lines]" + 签名 + 短 docstring |
| A11 模型调度 | repositories-wiki `cli.ts:35-46` | 三档显式 CLI 参数（planer/exploration/builder 分离 config） |
| A12 质量抽检 | OpenDeepWiki `content-generator.md:470-476` | 深度要求原文："Walk through the actual control flow and key algorithms step by step... the page must read like a definitive engineering reference. Length follows substance... Never truncate coverage to save space" |

**其余可借用的 prompt 措辞**：≥5 文件双重强调（deepwiki-open prompts.py:36,118）；反幻觉闭环 "Do not infer, invent, or use external knowledge... If information is not present in the provided files, do not include it or explicitly state its absence"（同 :120）；"Every page must earn its place. Do not create filler pages" 并给 Quality/Performance/Cost 三条理由（repositories-wiki prompts.ts:32-40）；条件式章节目录（repowiki-plugin `agents/architect.md`：models→Database 章、docker→Operations 章，"A small utility deserves 4-6 sections"）；Knowledge Cards 页（RepoWiki_temp `wiki_builder.py:67-72`）；over-compression 判失败 + 文件/类页禁令的 few-shot 反例（OpenDeepWiki catalog-generator.md:24-33,63-66）。

## 6. 修订后的改进路线

- **Q-1 概念章规划**（A1+A2）：Phase 3 增概念聚类步骤与机制章必选——最高优先，直击根因。
- **Q-2 页面密度与形态**（A4）：表格/mermaid/段末 Sources 进模板与 check-wiki。
- **Q-3 生成协议升级**（A5+A6+A7+A11）：叶子先行强化、大页两轮、证据配比、模型调度显式化。
- **Q-4 严谨性闭环**（A3+A12）：check-wiki `--verify-claims` 抽样机检 + audit 抽检 10 条。
- **Q-5 导航与快照**（A9+A10）：index mindmap + commit 锚点，脚本化零成本。
- 附带：A8 骨架前置（成本项）。

产品形态项（页数档位 20-35、llms.txt、托管）移入 §8 附注——不在本轮质量计划内，页数上限只保留"不设人为上限、由概念章自然决定"一条。

## 7. 结论

1. 差距的本质：**我们生成的是"目录的百科"，deepwiki.com 生成的是"机制的教科书"**。单页严谨性我们占优，组织轴、机制深度、叙事连贯、内容密度全面落后，且都可追溯到规划阶段"章节=目录投影"这一个根因。
2. survey 12 份文档里已有现成解法（A1-A12），无需发明新机制；核心是三个：概念聚类规划（[03]）、Claims 抽样校验（[01]）、表格化密度模板（deepwiki.com 实测）。
3. 改进全部落在 skill 的 Phase 3/4/5 与 check-wiki，零外部依赖，可用 nop-jq 重新生成做前后对照验证。

## 8. 附注：产品形态维度（v1 内容的降级归档）

v1 报告的 D1（页数规模）/D2（层级树）/D5（llms.txt+MCP）/D6（呈现托管）属产品形态维度，与本轮质量轴正交：十进制编号树、llms.txt、静态托管在需要**对外发布**时再立项；`20-35 页`这类档位数字不再作为质量目标——页数由概念章规划自然决定。此裁定不影响 2026-08-04 对比报告（docs-for-ai 与 deepwiki 互补不互替）的结论。

## References

- 一手抓取：deepwiki.com/facebook/react、deepwiki.com/entropy-cloud/nop-entropy
- docs.devin.ai：deepwiki.md（wiki.json/档位）、deepwiki-mcp
- HN item 45002092（幻觉批评）；skywork.ai 评测；howworks.ai 对比
- 本仓库：`ai-dev/analysis/deepwiki-survey/01..12`（[NN] 引用）；`ai-dev/analysis/2026-08/2026-08-04-docs-for-ai-vs-deepwiki-comparison.md`；`ai-dev/logs/2026/09-25.md`、`09-26.md`
- 本地源码：`~/ai/deepagents/`（OpenWiki harness 底座，后续可读源取证 `~/ai/` 下 269 仓库）
