# nop-lint 工具替代验证 Roadmap — AI 编码期核心缺陷拦截，逐工具分面裁定

> Last updated: 2026-09-28
> 定位权威: `ai-dev/design/nop-lint/00-overview.md` §1 定位增注（2026-09-28）
> 设计权威: `ai-dev/design/nop-lint/`（迁移路径 08-migration.md；PMD/EP 对齐 06-pmd-errorprone-alignment.md；mjs 账本 12-check-scripts-migration-manifest.md）
> 既有行级账本: [checkstyle-pmd-migration.md](../../nop-lint/docs/checkstyle-pmd-migration.md)（26 行）· design 12（24 行）· PMD/EP coverage manifest（186 行）

## Purpose

nop-lint 的使用目的：**AI 辅助开发的编码期防线**——在编程开发阶段就自动拦截**核心缺陷**（资源泄漏、空指针、吞异常等隐蔽 bug）与平台不变式违反。它不是通用质量检查台：**纯风格/可选惯例问题不在目标面**。分析原则：**全部基于源码**（tree-sitter CST + XNode + JavaParser/tsc 源级类型推导），**不进入字节码层**。

因此"能否替代工具 X"不是全量规则面问题，而是逐工具**分面裁定**：

| 面 | 归属 | 处置 |
|---|---|---|
| **核心缺陷发现面**（正确性/资源/并发/安全/数据流/平台不变式） | 职责内 | 逐行验证：映射 → 落规则 → 同语料对照 → 切换 |
| **风格/可选面**（star import、brace、命名格式、magic number…） | **out-of-purpose** | 显式记录不做；这不是替代债，是范围声明 |
| **字节码级检测**（SpotBugs 需 class-file 信息的 pattern 等） | **out-of-principle** | 纯源码原则，正式记录不追；重估触发 = 该原则本身被推翻 |
| **问题域外**（覆盖率、依赖 CVE、格式化、git 历史、IDE 交互面） | out-of-scope | 账本归档 |

## 定位三轴准入判据（AI 消费场景）

1. **高信号优先**：发现流由 AI/开发者消费，误报成本高于漏报——新规则必须声明误报控制面（constraints / 类型门控 / 豁免机制），纯噪音面不入库。
2. **编码期延迟预算**：fast 档 <50ms/文件（design 11 §6，JMH 锚点见 `nop-lint/docs/perf-baseline.md`）。
3. **可修复加分**：带 autofix/suggestion 的规则优先（AI 可自纠）。
4. **老规矩**：每规则 RuleTester fixtures + 行为对照。

## 在用工具清单（接线证据，2026-09-28 盘点）

| 工具 | 接线点 | 现行口径 |
|---|---|---|
| Checkstyle 10.21.1 | root pom qa profile（`pom.xml:472` 起） | `checkstyle.xml` 17 条激活规则，report-only（failOnViolation=false） |
| PMD 7.26.0 | root pom qa profile（`pom.xml:487` 起） | `pmd-ruleset.xml` 9 条规则，report-only |
| SpotBugs 4.9.8.3 | root pom qa profile（`pom.xml:521` 起）+ **nop-kernel 自带 plugin**（`nop-kernel/pom.xml:451`）+ `spotbugs-annotations`（`nop-kernel/nop-commons/pom.xml:75`） | failOnError=false、threshold=low；`spotbugs-exclude.xml` 全局排除生成物/Errors/Configs/Constants + 与 Nop 架构冲突 pattern |
| SonarQube | `sonar-maven-plugin`（`pom.xml:415`）+ `sonar.*` 属性组（`pom.xml:35–51`，含 jacoco 聚合覆盖率报告路径） | "主动触发，不参与日常构建"（pom 注释原文） |
| ArchUnit | `archunit-junit5` 测试依赖：`nop-ai/nop-ai-shell/pom.xml:27`、`nop-ai/nop-ai-agent/pom.xml:55` | 架构断言以 JUnit 测试形态存在 |
| check-\*.mjs ×24 | CI workflow / package.json / run-\*.sh（design 12 逐行登记） | 逐行实况：7 maintain-mjs / 7 exclude / 5 migrated-pending-switchover / 2 candidate / 3 deferred（design 12 汇总行已与逐行一致，2026-09-28 item 1 修正） |
| ErrorProne / NullAway | **未接线** | 无；design 06 §2/§7 已有 EP 能力对齐分析与 manifest 行 |

## 工具级终裁词表（针对各工具的核心缺陷发现面裁定）

| 终裁 | 含义 |
|---|---|
| `core-face-replaced` | 该工具的**核心缺陷发现面**已由 nop-lint 承接（行级映射 + 对照通过 + 原接线点按判据处置）；风格/字节码残余按 out-of-purpose / out-of-principle 归档 |
| `replaced-partial` | 核心面部分承接；未承接部分的归因（机制缺口 vs 原则外）逐行记录 |
| `keep-tool` | 核心面大头超出 per-file 源码引擎问题域，工具整体保留 |
| `out-of-scope` | 非 lint 问题域（覆盖率/依赖 CVE/格式化/git 历史），归档不占用工作面 |

风格类工具（如 Checkstyle）的合理终裁形态往往是"少数核心行承接 + 主体 out-of-purpose"——这不是失败态，是新定位下的正常结论。

## Work Items

> **本块是唯一动态状态块。** 状态词表 todo/planned/done，与 nop-lint-roadmap 相同。

### Wave 1 — 定位基线与存量收口

- 1. 统一工具替代账本 [tool-replacement-ledger.md](../../nop-lint/docs/tool-replacement-ledger.md)（骨架已随本 roadmap 建立）：防腐门禁脚本（终裁词表 + 分面三轴 enum-set + self-test 正控）+ 逐工具终裁行回填机制。**顺带修正 design 12 汇总行与逐行状态的不一致**（逐行实况 migrated-pending-switchover 5 / candidate 2 / deferred 3；汇总写 3/5/2——门禁若放行该汇总则门禁同修）: `done`（plan: ai-dev/plans/nop-lint/15-tool-replacement-ledger.md；closure audit agent_28e0e96c APPROVE）
- 2. **现有 62 条生产规则分面复审**：按新定位逐条标注 core / optional 分面（预期 no-star-import、control-statement-braces 等风格面规则降出默认档、降 info 或移出库——逐条裁定），分面表落统一账本；此后新规则按准入判据执行: `done`（plan: ai-dev/plans/nop-lint/16-rule-facet-review.md；closure audit agent_a3d8c4ac APPROVE；裁定 core 46 / out-of-purpose 16（demote 8 / remove 4 / keep 4），"降出默认档"按引擎杠杆重裁定为降 info/移出库，见 design 02 §5）
- 3. checkstyle.xml 收口（新轴重裁）：keep-checkstyle 12 行逐行归入三轴——核心缺陷行升规则；风格行记 `out-of-purpose`（不迁移）；判据达成后 qa profile 配置段按行结果处置（独立 plan + closure audit + 单 commit 回退）: `planned`（plan 3a: ai-dev/plans/nop-lint/17-checkstyle-facet-adjudication.md；plan 3b 切换: 18 待立项） — deps: 1, 2
- 4. pmd-ruleset.xml 收口（同上）：keep-pmd 7 行集中 Clone 族/控制流面——先证是否属核心缺陷面再裁去向: `todo` — deps: 1, 2
- 5. mjs 账本切换收口：migrated-pending-switchover 5 行（#7/#12/#20/#21/#22；#20/#21 在 CI invariant-gate 硬门禁中优先）+ candidate 2 行（#3/#19）逐脚本对照切换: `todo` — deps: 1
- ★ **Milestone MT1: 定位基线确立 + 存量账还清**（unlocks when 1–5 done）

### Wave 2 — 核心缺陷面覆盖矩阵（本 roadmap 的中心工作）

- 6. **核心缺陷类清单与覆盖矩阵**：资源泄漏（流/连接未关闭）、空指针（解引用/传 null）、吞异常（silent-swallow 已有）、错误处理契约（NopException 面）、并发（锁/线程面）、安全面（加密/命令执行/敏感信息，已有 6 条 security）、注入面、数据流 bug（unused/self-assign/constant-condition 已有）——逐类盘点：现有规则 / manifest 机制 tier / 机制缺口 / 优先级，矩阵落统一账本: `todo` — deps: 2
- 7. **资源泄漏面落地**：v1 机制裁定（L3 acquire/release 路径配对分析 vs pattern+scope 保守面——以误报控制数据定）+ 试点规则（Closeable 未关闭 / finally 缺 close / 泄漏形态豁免面）+ 对照: `todo` — deps: 6
- 8. **空指针面深度裁定**：pattern 面（现有 throw-null/equals-null/no-throw-npe）→ L3 null-flow（解引用前判空路径分析）的深度/成本/误报裁定；NullAway 式全程序注解推导超出当前引擎深度的结论落正式记录 + 重估触发: `todo` — deps: 6
- ★ **Milestone MT2: 核心缺陷面矩阵成立，资源泄漏/空指针两大类有机制裁定**（unlocks when 6–8 done）

### Wave 3 — SpotBugs（源码可检核心面的承接；字节码面原则外）

- 9. 盘点与三轴归类：实跑全仓（qa profile + nop-kernel 口径）收集实际触发面；bug pattern 目录整体过一遍三轴（源码可检核心 / 字节码专属 out-of-principle / 次要 out-of-purpose）——SpotBugs 目录同时是"隐蔽 bug 分类学"的免费输入，反哺 item 6 矩阵; `spotbugs-exclude.xml` 逐 pattern 归因: `todo` — deps: 1, 6
- 10. 源码可检核心面规则落地：按盘点优先级落规则（全带 fixtures + 同语料对照，准入判据把关）: `todo` — deps: 9
- 11. 工具级终裁：核心面承接结论 + **字节码残余按纯源码原则正式记录不追**；qa profile / nop-kernel 的 spotbugs 接线去留按对照数据裁定: `todo` — deps: 10
- ★ **Milestone MT3a: 字节码系工具分面裁定完成**

### Wave 4 — SonarQube（产品级工具拆解裁定）

- 12. 使用面拆解：盘点 `sonar.*` 属性与实际触发方式，回答"本仓库从 Sonar 实际消费什么"——拆成 [核心缺陷发现面 / 覆盖率面 / 平台工作流面] 三份清单: `todo` — deps: 1
- 13. 核心缺陷发现面对照：以本仓库实跑 Sonar 的发现集为语料（**限实际消费面，不做全量 sonar-java 规则映射**），抽样对照 nop-lint 覆盖 manifest + 缺口归因（可表达未落地 / 机制缺口 / out-of-purpose / out-of-principle）: `todo` — deps: 12
- 14. 终裁：taint/hotspot 面（跨过程源码分析，当前引擎范围外）落正式 not-replaceable 记录 + 重估触发；覆盖率/工作流面 out-of-scope；规则发现面按 13 的对照数据裁 keep-tool 或 replaced-partial: `todo` — deps: 13
- ★ **Milestone MT3b: 平台系工具分面裁定完成**

### Wave 5 — ErrorProne 与类型系工具（未接线，adopt-or-skip）

- 15. ErrorProne 实跑盘点：EP 使命（抓真 bug）与本定位最对齐——一次性实跑收集其在本语料的**核心缺陷发现面**，coverage manifest 的 EP 行证据化 → adopt-or-skip 终裁: `todo` — deps: 6
- 16. javac 归因精度差异实测：抽依赖 javac type attribution 的代表面（overload resolution、常量折叠）做 L2 对照样本，精度 delta 落 manifest 增注（兑现 design 08 §4 承诺）: `todo` — deps: 15
- 17. NullAway 终裁：NPE 属核心 mandate（item 8 已裁定引擎侧深度）；NullAway 的全程序注解推导作为"另一极"落对照结论 + 重估触发: `todo` — deps: 8, 16
- ★ **Milestone MT3c: 类型系工具分面裁定完成**

### Wave 6 — 架构断言与 out-of-scope 归档

- 18. ArchUnit 盘点与裁定：清单化 nop-ai-shell / nop-ai-agent 架构断言测试；pattern-expressible 且属平台不变式者迁规则，依赖图闭包类断言（超出 per-file 引擎问题域）→ keep-archunit 逐条记录: `todo` — deps: 1
- 19. out-of-purpose / out-of-scope 记录终稿：统一账本落两节清单（不做理由 + 重估触发）——风格行（来自 items 3/4/9）、覆盖率 JaCoCo、变异测试 PIT、依赖 CVE 扫描、格式化、IDE 交互面、PMD CPD（引 design 06 §7.2）、跨过程 taint（引 item 14）: `todo` — deps: 3, 4, 9, 14
- 20. 终裁汇总报告：[tool-replacement-ledger.md](../../nop-lint/docs/tool-replacement-ledger.md) 终裁表收敛为逐工具分面结论（核心面承接了多少 / out-of-purpose 多少 / out-of-principle 多少 + 证据链接），直接回答"nop-lint 对工具 X 的核心缺陷职责替代到什么程度": `todo` — deps: 5, 11, 14, 17, 18, 19
- ★ **Milestone MT4: 逐工具分面裁定完成且汇总成账**（unlocks when 1–19 done；MT1/MT2/MT3a/MT3b/MT3c 均为其子集）

## Status values

| Status | Meaning |
|---|---|
| `todo` | 未开始，无 plan |
| `planned` | 已有执行 plan 且通过 draft review |
| `done` | 完成，通过独立 closure audit |

## Hard constraints

1. **纯源码原则**：引擎不进字节码层。凡需 class-file/全程序字节码信息的检测 = out-of-principle 记录，**不作为替代债**、不立项字节码分析器；重估触发 = 该原则本身被推翻。
2. **高信号准入**：新规则必须声明误报控制面；风格/可选问题不入默认档。发现流的消费者是 AI 与开发者，噪音即成本。
3. **裁定必须证据化**：任何 out-of-purpose / out-of-principle / keep-tool / not-replaceable 记录必须含机制面证据或对照数据 + 理由 + 重估触发；任何"核心面已承接"宣称必须先有同语料命中集对照（零 diff 或 delta 逐条裁定）。禁止无对照的替代宣称。
4. **硬门禁不下线**：已进 CI fail-fast 的检查（invariant-gate mjs、ArchUnit 测试）在等价替代对照通过前保持 hard gate（延续 design 12 Minimum Rules #13）。
5. **切换纪律**：旧工具配置段/调用点移除 = 独立 plan + closure audit + 单 commit（`git revert` 即回退）；并行期双跑互不干扰（06 §8.1）。
6. **规则落地纪律**：新落规则全带 RuleTester fixtures；性能声称须 JMH 基线背书。
7. **账本分工**：本 roadmap 只跟踪工具级终裁与 wave 进度；行级状态在各账本文件滚动更新，经统一账本互链，禁止双写状态。
8. **范围锚**：源码语言 Java + TypeScript/TSX + XNode XML 不变；taint 在正式记录（item 14）落档前不得先做 taint 承诺。

## Current baseline

- 既有替代资产：checkstyle-pmd 迁移映射 26 行（item 3a 后 landed 11 / out-of-purpose 8 / keep-pmd 7——checkstyle 侧可移除待 plan 18 切换，门禁在档）；mjs 账本 24 行（汇总行已与逐行一致，2026-09-28 item 1 修正，门禁族排除口径在档）；PMD/EP coverage manifest v2（186 行，tier 1 = 32 已落地带 fixture——item 2 翻转 out-of-purpose 3 行后 item 3a 提升 2 行，2026-09-28）。
- 已验证能力锚点：nop-lint roadmap M1–M6 全 done（引擎 + 规则库：分面复审 62→58 后 item 3a 新增 4 条 checkstyle 核心承接规则 = 62 条，见统一账本规则级分面表；L1–L4 语义面 + maven/GraphQL/LSP/CLI 生态面）；JMH 基线在档（fast 档 <50ms 预算实测余量 ~50×）。
- 已知能力缺口（item 6 矩阵的输入）：资源泄漏类（acquire/release 路径配对）与空指针解引用流（null-flow）在当前 L3 数据流（方法内 def-use + 常量传播）之上属未覆盖面——这正是"隐蔽 bug"定位下最需要补的两类。
- 风险提示：定位收窄（out-of-purpose 剔除风格面）会显著缩小"替代债"的表面积——items 3/4 的收口判据从"全行 landed"改为"核心行 landed + 风格行显式归档"，判据文本须随 item 1/2 落地同步修订（design 06 §8.1 增注）。
