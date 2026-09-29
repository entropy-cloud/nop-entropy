# nop-deepwiki 外部工具链吸收设计

> 日期: 2026-09-30
> 状态: active（P0 待实施；P1 另立 plan；P2 仅登记）
> 范围: `.opencode/skills/nop-deepwiki/` 的 SKILL.md 与两脚本的下一步行为变更决策；证据锚点为本地检出的外部仓库源码（`~/sources/deepwiki/`、`~/ai/`）
> 灵感来源: openwiki（langchain）、deepwiki-open、CodeWiki、OpenDeepWiki、RepoWiki、open-wiki、openwiki-shariqriazz、git-wiki、GitNexus、Understand-Anything、codegraph、mind-expander、PageIndex

---

## 一、设计结论

1. **P0（脚本级，本轮采纳）**：行号写前全量校验、Mermaid 真渲染校验 + 优雅降级、代码块密度硬约束、提取回归 fixture 固化。全部落在现有两脚本与 SKILL.md 模板内，不新增运行时依赖。
2. **P1（结构性，逐项另立 plan）**：nop-code 符号索引接入 Phase 2、同模块共享上下文批次、存量 nop-task/nop-batch 按新规则重生成。
3. **P2（登记不动手）**：Claims 断言级增量、commit 触发的后台增量托管、MCP 读侧工具 / llms.txt。
4. **拒绝**：embedding/RAG、外部大型依赖作为能力源、MCP 文件旁路、个人知识 wiki 的检索与三层模式（§四）。

## 二、背景与动机

### 2.1 现状量化（对标基线）

对 `deepwiki/nop-orm/`（10 内容页）按 deepwiki.com 四个 Java 项目（spring-boot/maven/flink/junit5，payload 级基线）同口径实测：

| 指标 | 基线（四项目区间） | nop-orm 现状 | 判定 |
|---|---|---|---|
| 页均 H2 | 6.0 – 8.7 | 8.4 | 达标 |
| 页均表格 | 3 – 4 | 2.6 | 略低 |
| 页均 mermaid | 2.7 – 6.1 | 1.5 | **未达标** |
| 页均代码块 | 3.5 – 14.9 | 1.8（内容页多为 0） | **最大差距** |
| 页均可点击源码引用 | 文件级 16–69（junit5 约 20） | 59.1（行级 + 机检抽验） | **超出** |

结论：**目录 / 源码引用 / 符号级架构图三个维度已达 deepwiki.com 形态，溯源密度超过它；代码块与图密度未达标**。根因是派发模板只有表格/mermaid 下限，没有把"展示代码"当硬约束——是生成纪律问题，不是方法论问题。

### 2.2 外部机制盘点（按主题重述，证据为本地检出源码）

**溯源与验证**
- deepwiki-open 的 `_ground_citations`（`~/sources/deepwiki/deepwiki-open/api/services/codemap.py:186`）：LLM 给的行号默认不可信，生成后在真实文件中重新定位 snippet 并**覆盖行号**——"写前静默纠错"。
- openwiki 的 Claims 体系（`~/sources/deepwiki/openwiki/src/claims/guidance.ts`）：每条断言是可独立失效/重验的知识单元，绑定严格 `repo://path#L20-L48` 证据 URI；提交时稀疏对账（无 issue 自动确认，有 issue 必须显式决策）；page-manifest 记录**逐页**源指纹。
- openwiki-shariqriazz 的 citations 反抽校验（`crates/openwiki-wiki/src/citations.rs`）：生成后从 markdown 反抽引用并解析回 chunk 验证存在性。

**图表**
- openwiki 的无头渲染校验 + 优雅降级（`~/sources/deepwiki/openwiki/src/mermaid/validate.ts`）：mermaid.parse 失败的图自动转 `text` fence、解析错误写进 HTML 注释留给下次修复——坏图永不炸渲染。对比：我们 check-wiki 只做块首行正则识别，无真实解析。

**增量**
- OpenDeepWiki 的 commit 轮询 Worker + 原子任务认领 + 多实例心跳（`~/sources/deepwiki/OpenDeepWiki/.../IncrementalUpdateWorker.cs`）：把手动增量升级为 commit 触发的托管服务。
- CodeWiki 的"页面→引用符号"倒排索引反推受影响页面 + 级联传播（`~/sources/deepwiki/CodeWiki/codewiki/mcp/tools/analysis.py`）。
- 我们已有等价物：wiki-state 的 `pages[].sourceFiles` 映射即"变更文件→命中页面"，覆盖 CodeWiki 场景的主干；缺的是断言级粒度与触发自动化。

**检索与索引**
- open-wiki / openwiki-shariqriazz：tree-sitter 符号级索引（worker 并行 / chunk 级 hash）。
- codegraph（`~/ai/codegraph/`）：tree-sitter→SQLite+FTS5 符号图，MCP 暴露 `explore/callers/impact` 语义工具，一次 impact 查询替代 N 次 grep/read。
- **本仓库现成的 nop-code 索引是同类资产**，接入不需要引入新依赖。
- PageIndex（`~/ai/PageIndex/`）：vectorless TOC 树即索引、检索即树上推理，溯源内建于索引结构。

**成本拓扑**
- GitNexus（`~/ai/GitNexus/gitnexus/src/core/wiki/generator.ts`）：一次 LLM 调用建模块树 + 自底向上逐模块生成，调用数 O(modules)+1；我们每页一个子代理是 O(pages)，实测 33–158 万 token/页。
- Understand-Anything（`~/ai/Understand-Anything/`）：importMap 语义分批——同批文件按 import 依赖聚拢，跨文件摘要一致性好于机械分批。

**质量评测**
- RepoWiki 的检索回归评测（`~/sources/deepwiki/RepoWiki_temp/evals/run_eval.py`）：fixture 问题 + 已知答案文件，低于基线 exit 2 可进 CI。我们的 `_tmp/deepwiki-fixture` 验证是一次性的，没有固化成可重复 eval。

## 三、核心设计（吸收决策）

### P0-1 行号写前全量校验

**决策**：`gen-wiki-meta.mjs` 重写松格式引用时，对每条带行号的引用做行号越界检查（起止行 > 文件行数即告警并保留松格式），越界清单随收尾输出；`check-wiki` 的 `--verify-claims` 保留为抽样关键词命中层，两层互补。

**理由**：借鉴 deepwiki-open 的写前纠错思路。越界检查是纯数字比较，对每条引用全量执行的成本可忽略；而关键词命中成本高，维持抽样。现状是"重写不校验、抽检 20 条"，行号漂移类缺陷（nop-task 轮曾实测抓到 3 处）依赖抽样运气。

**验收**：重写一条行号越界的引用时收尾输出告警；`check-wiki --strict` 对越界残余报 ERROR。

### P0-2 Mermaid 真渲染校验 + 优雅降级

**决策**：mermaid 解析器经 **`ai-dev/tools/`**（仓库工具依赖的唯一 pnpm 根，`ai-dev/tools/package.json`，pnpm 安装 mermaid + jsdom，node_modules 不入库；克隆后 `cd ai-dev/tools && pnpm install` 一次即启用）。`check-wiki.mjs` 从脚本位置上溯项目根、从 `ai-dev/tools/node_modules` 显式解析 mermaid（完整构建优先，core 回退），并以 jsdom 注入全局 DOM 后做无头 `mermaid.parse`（openwiki dom-shim 同做法）——**不引用项目外的工具脚本，裸 `import('mermaid')` 不可用（脚本目录链上无 node_modules），也不得另建第二个 pnpm 根**。解析失败输出 ERROR（含页面、块序号与解析器原文错误）；`ai-dev/tools/node_modules` 缺失或加载失败时显式输出一行跳过原因并回退块首正则白名单，不产生误报。

**理由**：借鉴 openwiki 的降级协议。SKILL.md 的"语法防坑清单"是静态经验集，防不住新形态语法错误；deepwiki.com 的图之所以"合适"，一是符号级内容，二是渲染从不失败——后者只能靠真解析保证。依赖收敛在仓库内既有 pnpm 根 `ai-dev/tools/`，符合"工具脚本不引用项目外路径"约束；无 Node 环境依赖时全部门禁仍可运行（自完备设计）。

**验收**：`cd ai-dev/tools && pnpm install` 后，check-wiki 对含语法错误 mermaid 块的页面报 ERROR 且错误含解析器信息；四个 wiki（task/batch/orm/xlang）全部存量块真实解析通过；删除 ai-dev/tools/node_modules 后 check 输出显式跳过行、其余门禁行为不变。

注：openwiki 的失败图优雅降级（解析失败块转 `text` fence + 错误写入 HTML 注释留档供下次修复）未纳入 P0 范围，登记为后续增强——触发条件：真实生成中频繁出现解析失败且需要就地降级而非阻断。

### P0-3 代码块密度硬约束

**决策**：SKILL.md Phase 4 派发模板增补——内容页（豁免面与表格规则一致）**真实代码摘录 ≥3 个**（从映射源文件截取，附 `路径:起-止行号`，禁止编造合成代码）；机制章 flows 页 ≥4；quickstart 既有代码要求不变。`check-wiki.mjs` 增补对应 WARN（非 mermaid 代码围栏计数，豁免页一致）。

**理由**：§2.1 实测的最大差距。deepwiki.com 的页面"展示"代码而我们的页面"描述"代码；表格迫使枚举精确，代码摘录迫使叙述锚定真实 API 形态，两者互补。摘录必须带行号引用——与溯源原则一致，防止"看似代码实则编造"。

**验收**：`check-wiki` 对代码块 <3 的内容页报 WARN；`--strict` 下不通过；新页面实测页均代码块进入基线区间。

### P0-4 提取回归 fixture 固化

**决策**：在 `.opencode/skills/nop-deepwiki/scripts/` 旁固化 `fixture/`（含故意注入断链/死 Mermaid/幽灵页/缺 Sources/纯文本 Sources/越界行号的样例 wiki）与 `eval.mjs`（对 fixture 跑 gen+check，断言全部预期问题被命中，exit code 可接 CI）；SKILL.md Phase 5 增"改动脚本后必跑 fixture eval"。

**理由**：借鉴 RepoWiki 的评测软门禁。两个脚本已多次迭代（每次都在真实 wiki 上发现新边界：顿号多区段、`_tmp` 前缀污染、裸 pom.xml 误配），没有回归保护，每次改动都在拿真实产出冒险。

**验收**：`node scripts/fixture-eval.mjs` 退出码 0/1 可判定；本次 P0 其余三项的验证直接复用它。

### P1（结构性，逐项另立 plan，此处只定方向与理由）

| # | 决策 | 理由 |
|---|---|---|
| P1-1 | Phase 2 结构提取接入 **nop-code 符号索引**（fan-in/调用关系升符号级，Grep 降级路径保留） | codegraph 实证语义化检索显著省 tool calls；本仓库自有资产，零新依赖；文件级 fan-in 的口径近似是 PLAN 覆盖缺口的常客 |
| P1-2 | 同模块页面**共享上下文批次**派发（模块内子代理共享模块事实包，跨模块仍隔离） | Understand-Anything 的 importMap 分批思想；直接压当前 33–158 万 token/页的成本曲线。注：v3 相对 v2 的成本上升（26–258 万/页）是密度/深度导向的**显式取舍**——质量优先，成本对策归本项，不回退密度约束 |
| P1-3 | 存量 nop-task/nop-batch 按 P0-3 新规则**增量重生成**（沿用 PLAN 契约只重做页面） | 旧页架构图仍是概括级节点、代码块密度不达标；是 P0-3 的存量补课 |

### P2（登记，触发条件出现才立项）

- **Claims 断言级增量**（openwiki）：wiki-state 升级为逐断言证据指纹 + 稀疏对账。触发：增量更新频繁到页面级重生成成本不可接受。
- **commit 触发后台增量托管**（OpenDeepWiki worker 模式）：需要常驻服务形态，与 skill 定位冲突。触发：wiki 覆盖模块数多到手动 update 遗漏成为实际问题。
- **MCP 读侧工具 / llms.txt**（open-wiki / RepoWiki）：产品形态 Deferred 项的具体化。触发：对外发布需求。

## 四、拒绝了什么

| 拒绝项 | 来源 | 理由 |
|---|---|---|
| embedding/RAG 检索 | deepwiki-open 主线 | 行级确定性 Claim 已满足溯源；向量检索引入不可复现性（同一仓库两次生成结果漂移），违背"确定性工具负责事实"原则。PageIndex 的 vectorless 思路保留为 P1-1 的设计参考而非引入对象 |
| mermaid-cli 常驻依赖 / 语言服务器 | CodeWiki 渲染链 | 自完备设计约束：外部大型工具不作能力源。npx 可选校验器 + 降级路径是边界内的形态 |
| MCP 文件旁路（大 payload 落盘、Agent 自读） | CodeWiki | 我们是单仓库 agent 场景，无 MCP payload 限制问题；引入徒增协议复杂度 |
| qmd 混合检索 / 个人知识 wiki 三层模式 | git-wiki | 那是"个人笔记库"场景（raw sources→pages→schema），与"代码库百科"问题不同构；其 log.md 审计思想已被 AGENTS.md 日志规范覆盖 |
| Process 业务流程抽象（从调用链自动提取流程节点） | GitNexus | 方向正确但依赖符号级调用图（P1-1 之后才可行）；且我们的 `flows/` 机制章已承载流程叙事，当前差距在密度不在章型 |
| 一次 LLM 调用建树替代 PLAN 契约 | GitNexus Phase 1 | 成本模型诱人，但 PLAN 契约的"路径锁定 + 人工可审"是结构漂移防护的核心（反模式 #2），不可用单次调用置换 |

## 五、与已有设计的关系

- 本文与 [00-overview.md](./00-overview.md) 共同构成 skill 的设计权威：00 管现状基线，01 管下一步行为变更；实施 P0 时同步回改 00 的门禁表（新增代码块密度行、mermaid 真解析行）。
- **P0 的实施计划已立项为 `ai-dev/plans/367-nop-deepwiki-survey-absorption-and-density-upgrade.md`（active，2026-09-29）**：实施以该计划为载体，但行为决策以本文为准——计划执行中若发现与本文 P0 决策冲突（如 mermaid 可选性的降级路径、密度阈值口径），先修本文再改计划。
- SKILL.md 是操作协议本体：P0 全部四项落地后，SKILL.md 的派发模板（代码块下限）、Phase 5 序列（fixture eval 必跑）、反模式（新增"不要用合成代码充当摘录"）随之更新，`deepwiki/README.md` 的门禁命令不变。
- 平台级约束 `ai-dev/design/self-contained-design.md`（自完备设计）对 P0-2 划定的边界：工具依赖收敛在仓库内既有 pnpm 根 `ai-dev/tools/`，**禁止引用仓库外的工具脚本路径，也禁止另建第二个 pnpm 根**（既有例外仅 mission driver 使用 AGE 模板）；`ai-dev/tools/node_modules` 缺失时全部门禁仍须可运行（显式跳过 + 轻量回退），校验器缺失不阻断其余检查。
