# nop-deepwiki 多类型对比差距分析 v4（Java 四形态 + COBOL legacy）

> Status: open
> Date: 2026-09-26
> Scope: v3 skill 产出（nop-jq 10 页受限集）vs deepwiki.com 四个不同形态 Java 项目（spring-boot/maven/flink/junit5 一手 payload 级量化）vs COBOL 覆盖实况（17 仓探测）
> Revision: v4——v3 轴心是"概念章 vs 目录投影"；v4 新增**形态自适应**与 **legacy 语言**两个维度（用户目标：多类型 Java + COBOL 对比）
> Conclusion:（见 §4-§6）

## 1. 调研方法与样本

- **Java 四形态**（deepwiki.com 一手，payload 级精确统计，非估算）：spring-boot（Web/微服务框架，10 章 48 页）、apache/maven（构建工具，9 章 35 页）、apache/flink（大数据流，15 章 63 页）、junit-team/junit5（小型测试库，5 章 21 页）。原始 HTML 与统计脚本存 `_tmp/`。
- **COBOL**：GitHub star Top 25 + 指定仓库共探测 17 个，12 个已建 wiki（含最贴近真实 legacy 的 aws-mainframe-modernization/carddemo）、5 个未索引（含真实生产系统 navikt/DSF）。
- 本地验证对象：nop-task（254 文件/28K 行/12 子模块/DSL 驱动任务流编排——与 nop-jq 形态迥异）。

## 2. deepwiki.com 的两条铁律（一手量化）

### 2.1 形态自适应：wiki 讲的是"repo 代码本身"，叙事跟着代码形态走

| 形态 | 核心叙事 | 证据 |
|------|---------|------|
| 框架实现仓库 | 讲仓库自身工程化（构建约定/文档管线/CI），**不讲框架用法** | spring-boot wiki 几乎无 Java 代码块（groovy/shell 为主——它在讲 Gradle 约定插件） |
| 构建/工具链仓库 | 讲内部实现流水线（resolver 流水线/请求缓存/transport 选择），不讲用户概念（mediation "nearest wins" 一笔带过） | maven 6.-dependency-resolution |
| 分布式运行时 | 讲一致性语义/失败恢复/组件对照；唯一使用 stateDiagram、sequenceDiagram 占比最高；概览页声明"细节见子页"（层级递进） | flink 6-state-management（54 词导语、9 H2、1670 词、61 源文件链接） |
| 使用者库 | 讲编程入口+可运行示例；**单页不缩水且代码密度最高**（14.9 代码块/页）；教学式收尾（Practical Examples/Conclusion） | junit5 3.1-launcher（1240 词、20 行级引用） |

### 2.2 密度底线（四项目共同，全形态不出现目录式空页）

| 指标 | spring-boot | maven | flink | junit5 |
|------|------------|-------|-------|--------|
| 页均 H2 | 6.2 | 8.7 | 6.0 | 8.1 |
| 页均 mermaid | 2.7 | 6.1 | 3.4 | 5.4 |
| 页均代码块 | 3.5 | 13.6 | 4.3 | 14.9 |
| 页均词数 | ~2600 | ~2400 | 3318 | ~2200 |
| 页长中位 | 2226 词 | 2171 | — | 2015 |

统一骨架（全形态一致）：H1 → "Relevant source files" 折叠列表（机制页 16-69 文件）→ **40-55 词导语段** → H2/H3 展开 → **页尾 "On this page" 锚点目录**。图型：flowchart 占 75-85% 绝对主导。

### 2.3 COBOL 实况：覆盖哑铃型 + 资产清单式组织

- 覆盖与"仓库现代化程度"强相关而非语言本身：教学/hobby/编译器类覆盖良好（12/17），真实生产 legacy（navikt/DSF，56★挪威政府系统）**未索引**。
- COBOL wiki 骨架不使用 DIVISION/SECTION/段落，而是**资产清单**：程序前缀（CO*=online/CB*=batch）、JCL 作业链、VSAM 数据集、copybook 名分别成章（carddemo 实测）。
- 已知短板（我们的差异化机会）：copybook 只被当名字列表（无字段级 PIC/COMP-3 布局表）；JCL 从未进引用框（DD/COND 承载的控制流没讲）；fixed-format 无预处理；巨型单文件按行区间切分而非调用图。

## 3. v3 skill 现状 vs 上述基准的差距

| # | 差距 | deepwiki.com 基准 | v3 现状 | 严重度 |
|---|------|------------------|---------|--------|
| G1 | **页面骨架**：导语段 + On this page 锚点 + Relevant source files 折叠列表 | 四项目一致 | 页首源文件引用块有了；无导语段规范；无 On this page | 中（结构一致性） |
| G2 | **形态自适应**：叙事/密度/图型按仓库形态走 | 四形态四样 | 全项目同一套模板；Phase 0 无形态判定 | **最大差距** |
| G3 | **密度底线**：页均 2200-3300 词、≥2.7 图 | 全形态达标 | 99-365 行波动（~600-2500 词），无词数视角 | 中 |
| G4 | **大仓库层级递进**：概览页声明细节见子页、单页上限 | flink 模式 | 无单页长度治理（v3 流程页未触发） | 低-中 |
| G5 | **legacy/非主流语言**：扩展名白名单、fixed-format 预处理、copybook/JCL 一等公民 | COBOL 覆盖哑铃型+四短板 | skill 隐含 Java/主流语言中心；Phase 1 跳过规则无 .cbl/.jcl 概念；无 legacy 模式 | 中（对本仓库外目标） |
| G6 | **图型形态加权**：分布式→sequence/state；API 库→class | flink state 3 张/junit5 class 13 张 | 有类型选择指南，无形态加权 | 低 |
| G7 | **收尾规范**：Glossary 章收尾（大仓库）/Recent Additions（小仓库追版本） | 四项目两例 Glossary | 恒含 glossary 已有 | 已达标 |

## 4. skill 改进项（本轮落地）

- **I1 形态判定**（G2 核心）：Phase 0 增"仓库形态六分法"——`framework-repo`（框架实现仓库）/ `build-tool` / `distributed-runtime` / `consumer-library`（使用者库）/ `legacy-business`（COBOL 等业务系统）/ `compiler-parser`。每形态绑定：核心叙事提示、代码块密度、图型加权、结构策略。判定依据=构建文件+README+目录形态。
- **I2 页面骨架固化**（G1）：派发模板增三件——40-55 词导语段（H1 后）、页尾 "On this page" 锚点目录（H2 清单）、机制页源文件折叠列表语义（现有页首引用块保留，等价物）。
- **I3 密度底线**（G3）：派发模板增"目标 1500-2500 词"（中位带）；check-wiki 增词数 WARN（<800 内容页）。
- **I4 legacy 模式**（G5）：Phase 1 扫描增语言扩展白名单概念（.cbl/.cob/.cpy/.jcl/.pco 不在默认忽略列表）；Phase 2 增 legacy 预处理步骤（fixed-format 剥离、copybook 映射表、JCL 作业链提取、段落行区间地图）；Phase 3 增 legacy 资产清单章型（程序清单/JCL 链/数据集表三套并行）。
- **I5 层级递进**（G4）：页面 >3000 词或 >15 H2 时强制"概览声明+细节下沉子页"（与大页两轮生成联动）。
- **I6 图型加权**（G6）：Mermaid 指南增形态加权行——distributed-runtime 加 stateDiagram；consumer-library 加 classDiagram+可运行示例代码块。
- 不做：交互问答/MCP/多语言（维持 Deferred）。

## 5. 验证计划

nop-task 受限页面集（形态=framework-repo 变体：DSL 编排运行时）：恒含 5 + flows 2（任务流执行管线/状态与恢复）+ modules 2（core 引擎/queue 或 service）+ topics 1 = 10 页。产出与分析信息存根目录 `deepwiki/nop-task/`。

## 6. 迭代协议

每轮：跑一个模块 → check-wiki 全绿 → 对照 §2 基线找新差距 → 改 skill → 下一模块。终止条件：连续一轮找不到 G 级（结构性）新差距，仅剩措辞级微调。

## References

- deepwiki.com 四项目一手抓取（_tmp/*.html + analyze_deepwiki.py）；COBOL 17 仓探测（deepwiki.com/aws-samples/aws-mainframe-modernization-carddemo 等 12 仓 URL 见调研底稿）
- docs.devin.ai/work-with-devin/deepwiki.md（wiki.json/档位）；arXiv 2510.24428（CodeWikiBench，COBOL 缺席）；arXiv 2601.13007（ArchAgent：legacy 缺文档 + local-scope bias）
- 前序：本目录 v3 报告、`ai-dev/analysis/deepwiki-survey/`、plan 363
