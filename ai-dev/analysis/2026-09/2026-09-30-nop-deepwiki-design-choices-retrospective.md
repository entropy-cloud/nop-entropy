# nop-deepwiki 设计选择全景：从 12 项目调研到依赖收敛的推导链

> Status: resolved（结论已沉淀至 [design/nop-deepwiki/00-overview.md](../../design/nop-deepwiki/00-overview.md) 与 [01-toolchain-absorption-design.md](../../design/nop-deepwiki/01-toolchain-absorption-design.md)；本文是推导链综述，不再作为 active doc）
> Date: 2026-09-30
> Scope: `.opencode/skills/nop-deepwiki/`（SKILL.md + gen-wiki-meta.mjs + check-wiki.mjs + selftest）及其产出 `deepwiki/`（五个模块 wiki）
> Conclusion: nop-deepwiki 的全部设计选择 = survey 12 项目收敛的方法论主轴（确定性工具负责结构事实、LLM 只做叙述、行级引用锁死）+ deepwiki.com 四 Java 项目量化基线对标 + 用户实测反馈驱动的四代溯源体系 + 用户工程约束裁定的依赖边界（ai-dev/tools 唯一 pnpm 根）；其余一切（形态六分法、PLAN 契约、密度模板、拒绝清单）都是该框架在具体问题上的推导结果

## Context

- 要回答的问题：nop-deepwiki skill 的每个具体设计选择是什么、为什么这样选、否决了什么、演化由什么驱动。
- 触发：用户要求"综合历史 plan 和 logs，以及讨论情况，把 deepwiki 的具体设计选择说清楚"，随后裁定归档为分析报告。
- 素材：logs 09-25/26/29/30 四天条目、plan 363/367、本目录 v3/v4 两份差距分析、12 份开源实现调研（`../deepwiki-survey/`）、design/nop-deepwiki 两篇、本会话四轮用户讨论。

## Analysis

### 1. 定位与边界（2026-08-04 即裁定，未再变）

- **知识百科，与 docs-for-ai 互补不互替**：docs-for-ai 是平台操作手册，deepwiki 是"代码库如何运转"的知识百科。wiki 产出放 `deepwiki/`，09-26 起纳入 check-doc-links 同一门禁。依据：[2026-08-04 对比分析](../2026-08/2026-08-04-docs-for-ai-vs-deepwiki-comparison.md)。
- **PLAN.md 是 wiki 内容契约**，不入 `ai-dev/plans/`；路径一旦锁定不得增改（结构漂移使交叉链接与增量机制全失效）。
- **受限页面集为标准交付**：恒含 5 页 + 机制章 ≥2 + 代表模块页 + 横切主题页；页面数由概念聚类决定，不设配额。用户清单（.devin/wiki.json 规格）可作替代规划入口，二选一语义。
- **产品形态全部 Deferred**（问答/MCP/托管/多语言/llms.txt）——v1 分析曾误把轴心放在产品形态，用户澄清后 v2 纠正为"内容质量差距"（[gap-analysis v3](2026-09-26-nop-deepwiki-gap-analysis.md) Revision 记录）。

### 2. 方法论主轴

**确定性工具负责结构与事实，LLM 只负责叙述与综合，行级引用把两者锁死。** survey 12 项目的收敛结论；反模式第 1 条即"跳过结构提取直接让 LLM 写 wiki"（纯 LLM 路线遗漏率高、不可复现、成本失控）。

### 3. 管线与结构契约（plan 363 的核心升级）

- 五阶段管线 + MODE update 增量；结构提取只用 Grep/构建文件/目录结构（零依赖、可复现）。
- **概念章而非目录投影**：v2 的 PLAN 章节直接映射包目录（组件清单），v3 改为概念聚类（机制说明书）——差距根因（v3 分析 §4）。机制章 `flows/` ≥2，命名写机制不写目录。
- 页面契约硬规则：每页源文件 ≥5；证据不足回退固定页集不硬凑；GATHER 退出 checklist + THINK gate + 机制深度检查表（源自 openwiki/OpenDeepWiki prompt 实证）。
- 派发纪律：同批 ≤5 子代理（6 并发实测 429）；限流降级串行（nop-orm 10 页靠此跑通）；父级页必须附子页实产清单。

### 4. 形态自适应（v4 最大增量）

仓库形态六分法（framework-repo / build-tool / distributed-runtime / consumer-library / legacy-business / compiler-parser）驱动叙事、密度、图型加权。一手依据：deepwiki.com 铁律"wiki 讲 repo 代码本身"（spring-boot 讲自身构建管线、maven 讲 resolver 内部、flink 唯一 stateDiagram、junit5 代码最密）。legacy/COBOL 模式（copybook 字段级映射、JCL 作业链）是相对 deepwiki.com 的差异化设计，**至今无本地样本实战**。已实战形态：nop-jq（产出随 plan 361 删除）、nop-task、nop-batch、nop-xlang（compiler-parser 首验）、nop-orm——四轮无新 G 级差距，09-26 判定迭代终止，后续演进全部由外部输入驱动。

### 5. 溯源与验证：四代演进（每代由真实缺陷驱动）

| 代 | 机制 | 动因 |
|---|---|---|
| v1（09-25） | 行级引用 `路径:行号` 手拼 | deepwiki.com 引用行号可幻觉（HN 231 分讨论串核心批评）；要求每条可验证 |
| v3（plan 363） | 松格式 `[path:line]()` + gen-wiki-meta 确定性重写（正确性从模型责任改为代码责任）；`--verify-claims` 抽样机检（越界恒 ERROR / 窗口零关键词 ERROR / 无关键词 SKIP） | 手拼错误率高；机检实战抓获 3 处真实缺陷（nop-task 轮） |
| 09-29 | git origin 推导 **blob 永久链接**（锚定生成时 commit）；空括号死链/残余绝对链接升 ERROR；Sources 单行纪律 | 用户实测："源码参考链接全是死链"——437 条站点根绝对路径 + 6 旧页纯文本 Sources 126 条漏网，全修 |
| plan 367 G1 | 重写期**全量行号边界机检**（越界降级为文件级链接并汇报） | 抽样 20 覆盖不到池外越界锚；借鉴 deepwiki-open `_ground_citations` 写前纠错 |

现态为双层防线：写前全量边界机检（便宜、全量）+ 写后抽检关键词命中（贵、抽样）。超出 deepwiki.com——它大多文件级引用、无机检。

### 6. 图表：密度 → 符号级 → 渲染永不失败

- 密度（v3）：模块页 ≥3 mermaid + 语法防坑清单（静态经验集）。
- 符号级（09-29 用户反馈"架构图不合适"）：节点/生命线必须真实类名，禁止概括词；nop-orm 架构页已达成。
- 真渲染校验（09-30 用户裁定驱动）：mermaid 11 + jsdom 经 `ai-dev/tools/` 唯一 pnpm 根，check-wiki 无头 `mermaid.parse`（openwiki dom-shim 同做法）；解析失败 = 块级 ERROR，依赖缺失 = 显式跳过回退正则。过程修复："No diagram type detected"（未知图型坏块）曾被误判为环境失败中止校验——plan 367 selftest 断言抓出。
- 失败图优雅降级（openwiki 的 text fence + 错误注释留档）未纳入，登记为后续增强（触发：真实生成频繁出现解析失败需就地降级）。

### 7. 密度对标：量化驱动

v4 分析建立 deepwiki.com 四 Java 项目 payload 级基线（H2 6.0-8.7 / 表 3-4 / mermaid 2.7-6.1 / 代码块 3.5-14.9 / 词 2200-3300）。09-29 实测 nop-orm：H2 8.4 与可点击引用 59/页达标或超出；**代码块 1.8、mermaid 1.5 未达标**——"骨架达标、密度欠火"。plan 367 G4 补代码块 ≥3 硬约束并重生成 16 个不达标页（并行会话执行中）。

### 8. 增量维护

wiki-state.json 内容哈希指纹（非 mtime）+ 每页 sourceFiles 映射（变更→命中页面）+ >20% 变更或目录移动触发 replan；commit 锚点双写 PLAN 与 state。CodeWiki 引用倒排级联经评估已有等价物，不重复建设。

### 9. 工程与依赖边界（09-30 两次用户裁定定型）

- 工具依赖唯一 pnpm 根 = `ai-dev/tools/`（既有自包含项目）；**禁止引用项目外工具脚本路径**；唯一例外 = mission driver 使用 AGE 模板（`$HOME/app/attractor-guided-engineering-template`）。
- 演化：初版误在仓库根另建第二个 pnpm 根，同日裁正并入（log 09-30 两条目）。脚本本体保持 Node 内置零依赖；依赖缺失时门禁显式跳过 + 轻量回退，不阻断、不误报。
- 平台约束锚点：[self-contained-design](../../design/self-contained-design.md)。

### 10. 明确拒绝的方案（含否决理由）

| 拒绝项 | 来源 | 理由 |
|---|---|---|
| embedding/RAG | deepwiki-open 主线 | 行级确定性 Claim 已满足溯源；向量引入不可复现性，违背方法论主轴 |
| 外部大型依赖作能力源（mmdc 常驻/语言服务器） | CodeWiki 渲染链 | 自完备设计约束；mermaid 仅作 ai-dev/tools 内开发期校验依赖 |
| MCP 文件旁路 | CodeWiki | 单仓库 agent 场景无 payload 限制 |
| git-wiki 三层模式 + qmd 检索 | git-wiki | 个人笔记库场景不同构；log.md 审计思想已被 AGENTS.md 日志规范覆盖 |
| Process 业务流程抽象 | GitNexus | 依赖符号级调用图（P1-1 之后才可行）；flows/ 机制章已承载流程叙事 |
| 一次 LLM 调用建树 | GitNexus O(modules)+1 | 成本诱人，但置换掉 PLAN 契约"路径锁定+人工可审"的结构漂移防护——成本不可置换契约 |

### 11. 成本事实（显式取舍）

v2 33–158 万 token/页 → v3 26–258 万/页：**密度/深度导向的显式取舍，质量优先**（plan 363 新旧对照表）。对策"同模块共享上下文批次"登记 P1-2（Understand-Anything importMap 分批思想）。

## Conclusion

- 最终形态：五阶段确定性管线 + 形态六分法 + PLAN 路径锁定 + 松格式引用/blob 永久链接/双层机检的溯源体系 + 符号级图与真渲染校验 + 内容哈希增量；工程边界为 ai-dev/tools 唯一 pnpm 根、零项目外工具引用（mission driver AGE 模板唯一例外）。
- 被否决方案：见 §10 表（RAG、外部大型依赖、MCP 旁路、git-wiki 模式、Process 抽象、单次建树）。
- 后续工作：design 权威 = [00-overview](../../design/nop-deepwiki/00-overview.md) + [01-toolchain-absorption](../../design/nop-deepwiki/01-toolchain-absorption-design.md)；执行 = [plan 367](../../plans/367-nop-deepwiki-survey-absorption-and-density-upgrade.md)（P0 落地 + 密度重生成）。

## Open Questions

- [ ] legacy/COBOL 路径无本地样本，纸面设计未实战验证
- [ ] nop-code 符号索引接入 Phase 2（P1-1，需 live 索引服务）
- [ ] Claims 断言级增量 / commit 触发托管 / MCP 暴露（P2，带触发条件）
- [ ] 失败图优雅降级协议未实施（触发条件见 §6）

## References

- 调研：`ai-dev/analysis/deepwiki-survey/`（01–12 十二份开源实现深析）
- 差距分析：[v3 内容质量轴](2026-09-26-nop-deepwiki-gap-analysis.md)、[v4 多类型形态轴](../../../deepwiki/analysis/2026-09-26-multitype-gap-analysis.md)
- 定位对比：[2026-08-04 docs-for-ai vs deepwiki](2026-08-04-docs-for-ai-vs-deepwiki-comparison.md)
- 设计权威：`ai-dev/design/nop-deepwiki/`（README / 00-overview / 01-toolchain-absorption-design）
- 执行计划：[plan 363（completed）](../../plans/363-nop-deepwiki-structure-and-consumption-upgrade.md)、[plan 367（active）](../../plans/367-nop-deepwiki-survey-absorption-and-density-upgrade.md)
- 开发日志：`ai-dev/logs/2026/09-25.md`（skill 创建）、`09-26.md`（v3/v4 迭代与终止判定）、`09-29.md`（链接基准升级 + nop-orm）、`09-30.md`（依赖收敛 + tools 统一）
- 外部证据（本地检出）：`~/sources/deepwiki/`（openwiki、deepwiki-open、CodeWiki、OpenDeepWiki、RepoWiki、open-wiki、openwiki-shariqriazz、git-wiki）；`~/ai/`（GitNexus、Understand-Anything、codegraph、mind-expander、PageIndex）
