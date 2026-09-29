# nop 字节码分析专项 Roadmap — 纯增量通道，承接源码引擎原则外缺口

> Created: 2026-09-29
> Last updated: 2026-09-29（Wave 4 收口：items 8-9 done——双跑收敛+CI 裁定暂缓[数据驱动]，M3 达成；Wave 1-3 已收口）
> 设计权威: [ai-dev/design/nop-bytecode/00-overview.md](../design/nop-bytecode/00-overview.md)（Vision + Architecture Baseline 双职，草案——底座 ADR 落档后转 active）
> 发起：owner 指令（2026-09-29）——"原先的内容都不动，额外再引入字节码的分析工具专项分析其他内容"
> 姊妹 roadmap: [nop-lint-tool-replacement-roadmap.md](./nop-lint-tool-replacement-roadmap.md)（源码 lane；本 roadmap 对其**零修改**，Hard constraint 1）
> 需求输入锚点: 源码 lane 已知缺口三处——tool-replacement roadmap Current baseline 第 3 条（null-flow + 资源泄漏配对两类未覆盖面）、plan 23（item 7 Option A Deferred）、plan 24（item 8 null-flow Deferred + NullAway not-replaceable）；统一账本 Sonar 行（跨过程 taint not-replaceable）

## Purpose

源码 lane（nop-lint）的**纯源码原则**把两类深度缺陷面挡在门外：**空指针解引用路径分析**（null-flow：解引用前判空的路径敏感分析）与**资源泄漏 acquire/release 跨路径配对**——这两类恰是"隐蔽 bug"定位下信号最高的面，已在源码 lane 显式 Deferred（重估触发在档）。字节码层恰好在这两类面上具备结构性优势：class-file 类型全解析、控制流显式化、路径敏感抽象解释是成熟工程形态（SpotBugs 的 NullDerefAnalysis / ResourceTrackingAnalysis 即为此形态）。

本专项以**独立的字节码分析通道**承接这些原则外缺口。三句定位：

1. **不动 nop-lint**：nop-lint 引擎继续纯源码，其 out-of-principle 轴与重估触发原样有效，不被本 roadmap 拉动；
2. **不动现有工具**：SpotBugs / SonarQube / ArchUnit / PMD / check-*.mjs 的全部接线、终裁、账本零改动；
3. **新增一条字节码发现流**：独立底座（Wave 0 裁定）、独立账本、默认 report-only 起步进 CI，与现有工具只并行、不切换、不替代。

## 准入判据（AI 消费场景）

1. **高信号优先**（同源码 lane 判据 1）：发现流由 AI/开发者消费，误报成本高于漏报——每个分析器必须声明误报控制面（豁免机制 / 语义门控 / 白名单），纯噪音面不入库。
2. **档位定位**：本通道**不经编辑器实时档**——编码期实时分析归 tree-sitter 通道（编译未通过的半成品只有源码可分析，这是字节码层的结构性前提）。本通道定位 **CI/构建期档位**（分钟级预算），运行前提是编译产物存在。
3. **缺口归属单一**：每个缺口面归属唯一通道——源码 lane live 规则已覆盖的面本专项不重复做；本专项承接的面若源码 lane 未来落地，并行对照期去重裁定。禁止同缺陷同位置双报。
4. **老规矩**：每分析器 fixtures + 已知命中集对照 + 内核 JMH 基线。

## 底座候选（Wave 0 item 2 裁定；以下为候选框架与当前倾向，非预裁）

| 候选 | 内容 | 优势 | 代价 / 风险 |
|---|---|---|---|
| **A. ASM 自研底座** | ASM tree API + `org.objectweb.asm.tree.analysis` 抽象解释原语，自建方法内 CFG + nullness lattice | 行业事实标准（JaCoCo / ArchUnit / Hibernate 系）；核心单 jar、无传递依赖、体积小；per-file 模型贴合平台；自研可控（self-contained 原则，见 `ai-dev/design/self-contained-design.md`）；`tree.analysis` 自带 BasicVerifier/SimpleVerifier 数据流骨架；Java 21 产物（class file v65）需 ASM 9.5+ | CFG / 调用图 / 分析器自建工程量；字节码指令层开发门槛高 |
| **B. SpotBugs plugin** | 以 SpotBugs plugin SPI 写自定义 detector，复用其 CFG + NullDerefAnalysis + ResourceTrackingAnalysis | 补缺口最快——两大缺口的引擎现成 | 绑定 `edu.umd.cs.findbugs` 内部 API（跨大版本不稳定）；发现流被框架形态约束；与自研可控相悖 |
| **C. SootUp / WALA** | 全程序分析框架（调用图、指针分析现成） | 跨过程面开箱即用 | 重量级依赖 + 学习/维护成本；与平台自包含原则冲突；对 v1（方法内面）过重 |

**当前倾向（记录非裁定）**：A 为 v1 主底座——两大缺口 v1 均为方法内路径敏感面，ASM 的 `tree.analysis` 抽象解释原语正是这层能力，且符合平台自研惯例；C 归 Wave 5 跨过程 spike 复评；B 不入选（发现流整合成本 + 内部 API 稳定性 + 自研原则），但其 bug pattern 目录继续作为"隐蔽 bug 分类学"输入（tool-replacement item 9 先例）。**终裁必须由 item 2 以 POC 数据证据化**，落 design 目录下新增的 nop-bytecode 子目录（随 item 2 执行建立）。

## Work Items

> **本块是唯一动态状态块。** 状态词表 todo/planned/done，与姊妹 roadmap 相同。

### Wave 0 — 定位与底座裁定

- 1. **缺口承接清单与专项账本建立**：以需求输入锚点三处为输入，逐缺口标注【承接 / 不承接 + 理由 + 重估触发】；范围锚 4 的永不承接面（源码注释依赖面、autofix 面、依赖闭包面）在此显式归档；账本落 design 目录 nop-bytecode 子目录的 gap-ledger（随本项执行建立，模块骨架落地后迁移至模块 docs 目录）: `done`（plan: ai-dev/plans/nop-bytecode/01-wave0-gap-ledger-and-substrate-adjudication.md——gap-ledger.md 落档，G1–G5 四要素齐备；closure audit agent_c610c083 fixes-applied APPROVE）
- 2. **底座裁定 spike**：A/B/C 三候选 POC 对比——POC 范围 = 单模块 class 解析 + 基础方法内 CFG + 一个 toy null-flow 探针跑通；产出数据（解析吞吐 / 内存 / 依赖体积 / 工程成本 / Java 21 class file 兼容）+ 终裁 ADR（substrate-adjudication）落 design 目录 nop-bytecode 子目录（随本项执行建立）: `done`（plan: 同 item 1——A=ASM 自研采纳 / B=拒绝（SPI 集成实测未跑通+成本三倍劣）/ C=限 Wave 5 复评；POC 数据在 ADR §二；closure audit 同上） — deps: 1
- ★ **Milestone M0: 缺口账本与底座终裁落档**（unlocks when 1–2 done）——**UNLOCKED 2026-09-29**

### Wave 1 — 内核最小闭环

- 3. **模块骨架**：模块组定名（默认候选 `nop-bytecode`，与 nop-treesitter 同型定位——底座库；最终名随 item 2 裁定）+ class 文件采集口径（Maven reactor target/classes / jar；增量采集策略）+ 与 nop-lint 的依赖关系裁定（零依赖并行，还是复用 nop-core 基础设施）: `done`（plan: ai-dev/plans/nop-bytecode/02-module-skeleton.md——模块 + 采集层 v0 落地，5/5 tests，零 nop-* 依赖；closure audit agent_ad5221af APPROVE，M1 已补测试处置） — deps: 2
- 4. **方法内 CFG + 抽象解释内核 v1**：nullness lattice 优先；`tree.analysis` 复用 vs 自建随 item 2 数据裁（ADR §五已裁自建）；内核 JMH 基线（对标 `nop-lint/docs/perf-baseline.md` 形态）: `done`（plan: ai-dev/plans/nop-bytecode/03-kernel-cfg-dataflow-nullflow.md——内核 v1 + nullness 分析 + JMH 基线，13/13 tests；closure audit agent_bc4ff98a REJECT→fix→APPROVE（M1 日志补档+清理项处置）） — deps: 3
- 5. **发现流通道**：独立 CLI / maven goal 形态裁定；诊断输出格式与 nop-lint 诊断结构对齐（统一 AI/开发者消费面）；准入判据 3 的去重口径在本项落实: `done`（plan: ai-dev/plans/nop-bytecode/04-discovery-channel-cli.md——CLI+诊断对齐+去重口径落地，20/20 tests；closure audit agent_176d6bd2 REJECT→4 Major 修复（manifest 响亮/命令真机复演/多版本 base 优先/补测试）→复审要点全过） — deps: 3
- ★ **Milestone M1: 内核 + 通道可跑，基线在档**（unlocks when 3–5 done）——**UNLOCKED 2026-09-29**

### Wave 2 — null-flow v1（缺口一）

- 6. **方法内路径敏感空指针解引用分析 v1**：解引用前判空路径分析；豁免面 = assert / `Objects.requireNonNull` 系语义门控 / 平台 `NopException` 前置检查形态；已知命中集对照 = SpotBugs 同语料实跑 + 源码 lane item 8 裁定中的案例集: `done`（plan: ai-dev/plans/nop-bytecode/05-null-flow-v1.md——正式口径 + 豁免面锁定 + 对照记录 nullflow-comparison.md，25/25 tests；closure audit agent_16d0ce33 两轮：REJECT（2 Blocker 反例实证）→修复→第二轮代码面 APPROVE（文档收尾后标 completed）） — deps: 4, 5
- ★ **Milestone M2a: 空指针缺口有 v1 且对照在档**——**UNLOCKED 2026-09-29**

### Wave 3 — 资源泄漏配对 v1（缺口二）

- 7. **方法内 acquire/release 路径配对 v1**：Closeable / 连接 / 锁三类资源注册表；try-with-resources 与 finally-close 豁免；已知 wrapper 形态白名单；对照口径同 item 6: `done`（plan: ai-dev/plans/nop-bytecode/06-resource-leak-v1.md——义务分析 v1 + 十二形态 fixture[8 原始+4 audit 回归] + 对照零 diff[九方法语料]；28/28 tests；v1 交付两类注册表[锁归 follow-up]；closure audit agent_d32d9340 REJECT→ARETURN 残留扫描+白名单接线+回归 fixture 修复→28/28） — deps: 4, 5
- ★ **Milestone M2b: 两大缺口类各有 v1 且对照在档**（unlocks when 6–7 done）——**UNLOCKED 2026-09-29**

### Wave 4 — 并行期与 CI 接线

- 8. **与 SpotBugs 并行双跑对照收敛**：同语料双跑，发现集 delta 逐条裁定（重复 / 互补 / 一方误报）；**SpotBugs 接线零改动**——并行不替代: `done`（plan: ai-dev/plans/nop-bytecode/07-spotbugs-dual-run.md——双跑零 diff[SpotBugs 26 条全异缺陷面]+equals-null 保留双报裁决+CI 噪音千条级结论，28/28 tests；closure audit agent_e9780106 [plan 04 审查员复核通道契约延续]) — deps: 6, 7
- 9. **CI 接线裁定**：默认 **report-only** 起步；升 hard gate 须独立 plan + 对照期误报数据背书（Hard constraint 6）: `done`（plan: ai-dev/plans/nop-bytecode/08-ci-wiring-adjudication.md——数据驱动裁定暂缓接线[触发=FP 收敛+重跑双跑]，CI 文件零改动；closure audit agent_9c67970d REJECT→落盘修复→见 plan Closure） — deps: 8
- ★ **Milestone M3: 专项进 CI（report-only），并行期对照收敛**（unlocks when 8–9 done）——**UNLOCKED 2026-09-29**（对照收敛在档；接线裁定 = 暂缓[数据驱动，触发在档]——done 语义为裁定完成）

### Wave 5 — 跨过程扩展（adopt-or-skip）

- 10. **跨过程调用图 spike**：候选 C 复评（SootUp 最小工程）；参数 nullness 契约面；注解契约可读性（RuntimeVisibleAnnotations 在字节码层可直接读取）：`todo` — deps: 9
- 11. **taint 面重估**：统一账本 Sonar 行 not-replaceable 在案；仅当 item 10 成立才进入，否则归档不追：`todo` — deps: 10
- ★ **Milestone M4（待裁形态）: 跨过程面 adopt-or-skip 落档**（unlocks when 10–11 done）

## Status values

| Status | Meaning |
|---|---|
| `todo` | 未开始，无 plan |
| `planned` | 已有执行 plan 且通过 draft review |
| `done` | 完成，通过独立 closure audit |

## Hard constraints

1. **纯增量原则（最高约束）**：零修改既有内容。nop-lint 引擎纯源码原则（tool-replacement roadmap Hard constraint 1）**原样有效**，其"重估触发"不被本 roadmap 拉动——本专项是独立能力通道，不是 nop-lint 引擎的底座变更；现有工具接线 / 终裁 / 账本 / plan 全部零改动；本专项与任何现有工具**只并行、不切换、不替代**；未来若提出"字节码通道替代工具 X"，必须另立 roadmap 走 tool-replacement 同款替代验证流程，不得在本 roadmap 内夹带。
2. **高信号准入**：新分析器必须声明误报控制面；发现流的消费者是 AI 与开发者，噪音即成本（同源码 lane 判据 1）。
3. **证据纪律**：任何承接 / 不承接 / keep 记录必须含机制面证据或对照数据 + 理由 + 重估触发；底座终裁必须 POC 数据背书；禁止无对照的能力宣称。
4. **范围锚**：只做 class-file 信息可及的面。依赖源码注释 / 源码语法结构的检测（fall-through 注释豁免、autofix 模板）**永不入本专项**（在 item 1 账本显式归档）；依赖闭包类架构断言不入（ArchUnit 已承担，并行不替代）；风格面不入。
5. **依赖纪律**：底座引入须评估依赖体积 / 传递依赖 / 维护面（平台 self-contained 原则输入）；重量级框架（SootUp / WALA）引入须独立 plan + spike 数据 + 终裁。
6. **硬门禁不对称纪律**：本专项新检查默认 report-only；升 CI hard gate 须独立 plan + 对照期误报数据背书——与源码 lane Minimum Rules #13 形成"下线要审计、上线要观察期"的双向纪律。
7. **账本分工**：本 roadmap 只跟踪 wave 进度与底座终裁；行级状态在专项缺口账本（item 1 建立）滚动更新，禁止双写。

## Current baseline

- **需求输入**：源码 lane 两类已知未覆盖面（tool-replacement roadmap Current baseline 第 3 条原文："资源泄漏类（acquire/release 路径配对）与空指针解引用流（null-flow）在当前 L3 数据流之上属未覆盖面——这正是'隐蔽 bug'定位下最需要补的两类"）；plan 23（资源泄漏 v1 Option A Deferred）；plan 24（null-flow Deferred + NullAway not-replaceable）；统一账本 Sonar 行（跨过程 taint not-replaceable，HC 口径）。
- **并行不替代的对照资产**（现有字节码系接线，零改动）：SpotBugs qa profile（root `pom.xml:521` 起）+ nop-kernel 自带 plugin（`nop-kernel/pom.xml:451`）+ 根 `spotbugs-exclude.xml`；Sonar 属性组（`pom.xml:35–51`）；ArchUnit 测试依赖（`nop-ai/nop-ai-shell/pom.xml:27`、`nop-ai/nop-ai-agent/pom.xml:55`）。
- **ASM 现状**：仓库**无直接 ASM 依赖**（全部 pom grep 零命中；SpotBugs 仅传递携带，未被直接消费）——底座引入为净新增依赖，依赖体积评估入 item 2 spike 口径。
- **工程先例**（纪律对标物）：源码 lane 的 RuleTester fixtures 纪律、JMH 基线形态（`nop-lint/docs/perf-baseline.md`）、CI 接线先例（`.github/workflows/compliance.yml` nop-lint CLI job）、替代验证的证据纪律（tool-replacement Hard constraint 3）。
