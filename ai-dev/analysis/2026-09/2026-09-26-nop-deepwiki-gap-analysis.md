# nop-deepwiki 产出 vs deepwiki.com 与开源工具：差距分析

> Status: open
> Date: 2026-09-26
> Scope: `.opencode/skills/nop-deepwiki/` 生成的 wiki（nop-jq 实测 9 页，2026-09-25/26） vs deepwiki.com 托管 wiki（facebook/react、entropy-cloud/nop-entropy 一手抽样）vs 开源工具 2026-09 现状
> Conclusion:（见 §5-§7）

## 1. Context

用户判断"我们生成的 deepwiki 差很多"。本报告以一手证据回答三个问题：差在哪、哪些其实不差、怎么改进。对比基准：

- **deepwiki.com**（Cognition/Devin 托管）：react wiki（8 顶层章 ~35 页）与 nop-entropy wiki（21 顶层章 100+ 页）的一手抓取，来源 [deepwiki.com/facebook/react](https://deepwiki.com/facebook/react)、[deepwiki.com/entropy-cloud/nop-entropy](https://deepwiki.com/entropy-cloud/nop-entropy)。
- **开源工具 2026-09 现状**：OpenWiki（16.8k stars，agent-native）、OpenDeepWiki v2.0.6（SaaS 化）、deepwiki-open（18k stars，进入维护态并分叉 grok-wiki）、grok-wiki（桌面 CLI-first）、deepwiki-rs/Litho（C4 路线）。
- **自有基线**：`ai-dev/analysis/deepwiki-survey/` 12 份文档 + `2026-08-04-docs-for-ai-vs-deepwiki-comparison.md` + nop-jq wiki 生成全程记录（`ai-dev/logs/2026/09-25.md`、`09-26.md`）。

## 2. 对标基准的稳定范式（deepwiki.com 一手结论）

deepwiki.com 的 wiki 有一套高度稳定的内容范式，逐项如下：

| 要素 | 实测形态 |
|------|---------|
| 目录结构 | **十进制编号层级树**（1. / 1.1 / 2.3…），react 8 章 35 页，nop-entropy 21 章 100+ 页，页数由 cluster-based planning 自动决定 |
| 首页 | 定位段 → Major Subsystems → 架构图（mermaid）→ Repository Structure → How Pieces Fit Together → Next Steps，带 "On this page" 锚点目录 |
| 每页密度 | react 2.1 页：6 个 H2 + 3 个 mermaid 图 + **3-4 个表格** + 正文 ~900-1,000 词；重链接、重表格 |
| 代码引用 | 行内 `路径+行号` 可见文本 + commit 固定锚点 GitHub 链接（`#L17-L18`），段末 "Sources:" 列表（每段 3-5 条） |
| 首页源文件 | "Relevant source files" 列出 ~30 个（react）/ ~100 个（nop-entropy）文件链接 |
| 收尾 | **全站 Glossary 收尾**（两例都有） |
| 消费协议 | `/llms.txt` 索引 + MCP server（read_wiki_structure / read_wiki_contents / ask_question，免认证） |
| 生成机制 | clone → embedding 索引 → cluster-based planning（多 agent）→ 逐页生成；wiki.json 可显式定制页面树（≤30/80 页）；快照式更新（"Last indexed" 日期 + commit SHA，手动 Refresh） |
| 力度档位 | Low 免费 / Medium ~5-10 ACU / High ~20-40 ACU——档位直接决定深度 |

开源阵营 2026 年的共识演进：**wiki 已从"给人看的文档站"变成"agent 的只读知识库"**——MCP 工具层 + llms.txt 层 + 行级锚点层三层消费协议成为头部工具标配（OpenWiki 的 openwiki_search/read、OpenDeepWiki 的仓库 scope 化 MCP 端点、grok-wiki 的 llms.txt 导出）。

## 3. 我们的产出实况（nop-jq wiki）

compact 档 9 页：PLAN + 恒含 5 页（overview/architecture/quickstart/glossary/reading-guide）+ modules 4 页。质量事实：

- ✅ **行级引用严格性高于 deepwiki.com**：每断言 `file:line` + 页尾 Sources，且 `check-wiki.mjs` 验证所有链接真实可解析（deepwiki.com 的引用行号本身可能错，HN 批评之一）。
- ✅ PLAN 结构契约、确定性收尾（index/指纹/checklist 工具）、诚实覆盖率、增量指纹机制。
- ❌ 规模：9 页 vs 35-100+ 页；flat `modules/` 目录 vs 十进制层级树。
- ❌ 子代理 token 成本：4 个模块页耗 33 万-158 万 token/页（通读 1899 行 JqBuiltins 等大文件），无结构骨架辅助。
- ❌ 页面密度：无逐页字数/图表数规范（jq-runtime 269 行算好的，其他页无下限保障）；表格要素没有强制。
- ❌ 无消费协议层（llms.txt/MCP）、无呈现层（裸 markdown 目录）、无多语言。

## 4. 差距清单（按维度，标注严重度）

| # | 维度 | deepwiki.com / 开源头部 | 我们 | 差距判定 |
|---|------|------------------------|------|---------|
| D1 | **覆盖广度** | 35-100+ 页，cluster planning 自动定页数 | compact 9 页，页数档位过保守（standard 也只有 8-12 内容页） | **最大差距**。nop-entropy 的 deepwiki 有 21 章 100+ 页，我们对同仓库只可能产出 15 页级 |
| D2 | **层级结构** | 十进制编号树（2 级-3 级），章-页导航 | flat `modules/`+恒含页，两层为止，大仓库必然扁平失控 | 大。直接影响大仓库可导航性 |
| D3 | **页面密度与形态** | 每页 ~1,000 词、3-4 mermaid、3-4 表格、H3 细分 | 有 mermaid 下限（≥1）但无字数/表格规范；表格要素随机出现 | 中。表格恰好是 AI 读者吸收密度最高的形态 |
| D4 | **代码引用** | commit 固定锚点 GitHub 链接，行号可能幻觉 | 相对路径 `file:line`，check-wiki 强制校验链接真实存在 | **我们更好**（可校验性），但缺 GitHub 锚点形态（对外发布时） |
| D5 | **agent 消费协议** | llms.txt + MCP 三工具已成行业标准 | 无 | **大**（对"wiki 给 AI 用"的定位而言这是最大缺失）。llms.txt 是零依赖可补的 |
| D6 | **呈现/托管** | 托管站点：On this page 锚点、侧边栏、搜索 | 裸 markdown 目录 | 中。人类阅读体验差距明显，agent 阅读影响小 |
| D7 | **生成深度机制** | 多 agent cluster planning + 按档位多轮深化 + wiki.json 显式定制 | PLAN 一次成型、单轮生成 | 中。页数/深度上限被档位锁死 |
| D8 | **token 成本结构** | 索引/RAG 前置减少读文件 | 子代理通读大文件（1.4M-1.6M token/页） | 中。签名骨架前置可省 30-50% |
| D9 | **增量更新** | 快照式（落后代码数天-周，被社区批评） | wiki-state 指纹（未实测）+ commit 锚点 | **我们设计更优**，但未经过实战轮次验证 |
| D10 | **交互问答** | Ask/Deep Research（RAG 问答） | 无 | 超出"生成器"定位，属产品形态差异——非本轮目标 |
| D11 | **多语言** | 官方无证实（常被误传有）；OpenDeepWiki 有 12 语翻译 | 无 | 低 |
| D12 | **幻觉风险** | 长尾仓库幻觉集中（HN 231 分讨论串核心批评） | 引用强制 + 链接校验 + 信任边界 prompt | **我们更好** |

**总体判断**：用户的判断成立，但差距是**结构性的而非质量性的**——单页质量（引用严格性、反幻觉）我们不输甚至更好；差在 D1 页数规模、D2 层级组织、D5 消费协议、D6 呈现层四个"产品形态"维度，以及 D7/D8 两个"生成经济学"维度。根因：skill 初版按"文档生成器"设计，而赛道已经进化为"分层知识产品"（生成层/协议层/呈现层/交互层）。

## 5. 改进路线（按 ROI 排序）

**R1 结构范式对齐（D1+D2，最高优先）**：页面档位重标定——standard=20-35 页、deep=40+ 页（对齐 react/nop-entropy 实测）；PLAN 契约支持十进制编号层级树（`01-xxx/01-01-yyy.md` 或前置数字目录）；恒含页保留，模块页允许多级。

**R2 页面密度规范（D3）**：每页硬约束升级——≥800 词、≥2 mermaid（模块页 ≥3）、≥2 个表格、每个 H2 段末 Sources（deepwiki.com 形态）；写入 Phase 4 派发模板与 check-wiki 检查项。

**R3 agent 消费层（D5，零依赖高 ROI）**：`gen-wiki-meta.mjs` 扩展生成 `llms.txt`（页面清单+一句话职责，对齐 deepwiki.com 形态）+ 每页 `On this page` 锚点目录可选。

**R4 大文件骨架前置（D8）**：Phase 2 的签名骨架（已有配方）强制注入超长文件的派发 prompt，并对 >2000 行文件用 Outline 替代通读——目标模块页 token 减半。

**R5 呈现层可选输出（D6）**：`docsify`/纯静态 index.html 一键托管（deepwiki.com 的锚点目录/侧边栏形态），作为可选步骤不进主流程。

**R6 引用双形态（D4 增强）**：Sources 链接同时输出相对路径（本地可校验）与 GitHub 永久锚点（对外发布），由目标仓库是否 git 远程决定。

**明确不做（本轮）**：交互问答/Deep Research（需运行时服务，超出生成器定位）、多语言翻译（ROI 低）、MCP server（llms.txt 已覆盖 agent 发现，MCP 留待有真实消费方时再议）、RAG/embedding 索引（12 份调研结论：行级引用已满足溯源，embedding 是概率性方案）。

## 6. 与 2026-08 对比报告的结论衔接

`2026-08-04-docs-for-ai-vs-deepwiki-comparison.md` 判定 docs-for-ai（操作手册）与 DeepWiki（教科书）互补不互替——本路线不改变该结论：deepwiki 产出仍不入 `docs-for-ai/`。该报告点名的 DeepWiki 三大价值（Glossary、行内引用、单页架构心智模型）已在我们的恒含页/硬约束中落地，本轮补的是"规模化"与"消费协议"。

## 7. 结论

1. 差距集中在**产品形态**（规模/层级/协议/呈现）而非内容质量；单页严谨性是我们的相对优势，应保持而非向 deepwiki.com 的幻觉容忍度看齐。
2. 改进路线 R1-R4 可在纯 skill 层实现（零外部服务），R5/R6 可选；D10/D11/D12 明确排除。
3. 赛道风向（OpenWiki 的 agent-native 路线）验证了"引用严格 + 协议化消费"是我们应该加倍的差异化，而非追赶 deepwiki.com 的托管形态。

## References

- 一手抓取：deepwiki.com/facebook/react、deepwiki.com/entropy-cloud/nop-entropy
- docs.devin.ai：deepwiki.md（wiki.json/档位/私有 repo）、deepwiki-mcp（三工具）
- HN item 45002092（幻觉批评集中讨论）、skywork.ai 深度体验文、howworks.ai 竞品对比
- GitHub：langchain-ai/openwiki、AIDotNet/OpenDeepWiki（CHANGELOG v2.0.3-2.0.6）、AsyncFuncAI/deepwiki-open + grok-wiki、sopaco/deepwiki-rs
- 本仓库：`ai-dev/analysis/deepwiki-survey/`（12 份）、`ai-dev/analysis/2026-08/2026-08-04-docs-for-ai-vs-deepwiki-comparison.md`、`ai-dev/logs/2026/09-25.md`、`09-26.md`
