# 01 Wave 0 — 缺口账本建立与底座终裁（roadmap items 1–2, Milestone M0）

> Plan Status: completed
> Last Reviewed: 2026-09-29
> Source: [nop-bytecode-analysis roadmap](../../backlog/nop-bytecode-analysis-roadmap.md) Wave 0（items 1–2）+ [design/nop-bytecode/00-overview.md](../../design/nop-bytecode/00-overview.md) + [ai-dev/analysis/2026-09/2026-09-29-spotbugs-tai-e-complexity-and-pta-lane-choice.md](../../analysis/2026-09/2026-09-29-spotbugs-tai-e-complexity-and-pta-lane-choice.md)
> Related: 姊妹 roadmap [nop-lint-tool-replacement-roadmap.md](../../backlog/nop-lint-tool-replacement-roadmap.md)（零修改，仅作锚点输入）

## Purpose

收口 roadmap Wave 0 两个工作项，落 Milestone M0（缺口账本与底座终裁落档）：

1. **item 1 缺口承接清单与专项账本建立**——以需求输入锚点三处为输入，逐缺口登记【承接/不承接 + 理由 + 机制面证据 + 重估触发】，范围锚 4 的永不承接面显式归档；
2. **item 2 底座裁定 spike**——A（ASM 自研）/ B（SpotBugs plugin）/ C（SootUp/WALA 系）三候选取证对比（POC 范围 = 单模块 class 解析 + 基础方法内 CFG + 一个 toy null-flow 探针跑通），五维数据留档，终裁 ADR 落 design 目录。

M0 达成后解锁 Wave 1（模块骨架 / 内核 / 通道三项均有终裁依据可立项）。

## Current Baseline

- roadmap Work Items item 1/2 均为 `todo`，无 plan；本 plan 建立即 flip `planned`（draft review 通过后）。
- 设计目录 `ai-dev/design/nop-bytecode/` 已在档：`00-overview.md`（状态=草案，自述"底座 ADR 落档后转 active"）+ `README.md`（待落档清单含 gap-ledger 与 substrate-adjudication 两行）。
- 需求输入锚点三处已逐条核实于 live repo（2026-09-29）：
  - tool-replacement roadmap Current baseline 第 3 条（资源泄漏 acquire/release 配对 + null-flow 解引用流 = 源码 lane 未覆盖面）——`ai-dev/backlog/nop-lint-tool-replacement-roadmap.md:122`；
  - plan 23 Deferred But Adjudicated「Option A：L3 路径敏感 acquire/release 配对分析器」（触发 = v1 保守面误报数据不支持）——`ai-dev/plans/nop-lint/23-resource-leak-v1.md:86`；
  - plan 24 空指针行三段归因（pattern 面 5 条 landed / null-flow Deferred / NullAway not-replaceable）——`ai-dev/plans/nop-lint/24-null-flow-adjudication.md`；统一账本 Sonar 行（跨过程 taint not-replaceable）——`nop-lint/docs/tool-replacement-ledger.md:23,45`。
- 依赖现状（live 核实）：仓库全部 pom 对 `org.ow2.asm` **零直接依赖**（grep 零命中）；本地 `~/.m2` 已有 asm / asm-tree / asm-commons 9.5–9.10.1、asm-analysis 9.6–9.10.1（合计满足 class file v65 需 ASM 9.5+，取 9.7.1 组合）；候选 C（SootUp）构件真实坐标为**点分名**（`org.soot-oss:sootup.core` / `sootup.java.core` / `sootup.java.bytecode`，无 `sootup-all` 聚合构件；三构件版本 skew——java.bytecode 止于 1.3.0，core 系至 3.0.1，**三构件共同版本 = 1.3.0，POC 钉此组合**，metadata 实测 2026-09-29），maven central 主机可达（repo1.maven.org HTTP 200 实测）。
- 现有字节码系接线（零改动，只读引用）：SpotBugs qa profile（root `pom.xml:489` spotbugs-maven-plugin + excludeFilterFile）+ nop-kernel 自带（`nop-kernel/pom.xml:428`）+ 根 `spotbugs-exclude.xml`。（roadmap Current baseline 引用行号 521/451 已因 pom 演化偏移，接线本体在档；既有 roadmap 文本零修改，以本 plan 为实况锚点。）
- 复杂度/成本输入在档：`ai-dev/analysis/2026-09/2026-09-29-spotbugs-tai-e-complexity-and-pta-lane-choice.md`——SpotBugs 202,880 行（资源泄漏机制本体 ~1.5k 行：ObligationAnalysis + ResourceValueAnalysis + FindOpenStream；npe 面 5.5k 状态组合；224 detector 长尾）；SpotBugs 无指针分析；Tai-e（LGPL，maven central net.pascal-lab/tai-e v0.5.4）pta 29.1k 行。
- **语料实况修正**：仓库编译目标 = **Java 17（class file v61）**（root pom `maven.compiler.release=17`，nop-jq / nop-treesitter / nop-ai-agent / nop-lint-core 实测 major version 61）——roadmap「Java 21 产物（class file v65）」是前向兼容要求而非仓库现状，v65 兼容口径须用 `javac --release 21` 生成 toy class 显式实测（本机 JDK 26 在位，可编 v65）。
- POC 语料与脚手架：`nop-jq/target/classes`（97 classes）、`nop-treesitter/target/classes`（115 classes）在档可作单模块语料（gitignored 构建产物，fresh 环境以 `./mvnw compile -pl nop-jq -am` 再生）；`_tmp/` 已 gitignore（`.gitignore:120`），POC 脚手架归此（不入库，ADR 留可复现命令与原始数据）。
- 平台约束输入：`ai-dev/design/self-contained-design.md`（核心能力自建；外部大型库只允许"外部工具/窄桥接"形态）；design overview §2.5 已把该约束具体化（自研面默认 ASM 候选；跨过程面外部框架限独立进程窄桥接；LGPL 仅限构建工具边界整库使用 + 禁止源码移植）。

## Goals

- 缺口账本 `gap-ledger.md` 落档：三锚点缺口逐条【承接/不承接 + 理由 + 机制面证据 + 重估触发】；范围锚 4 永不承接面显式归档；每个承接分析器声明误报控制面。
- 底座终裁 ADR `substrate-adjudication.md` 落档：A/B/C 三候选五维数据（解析吞吐 / 内存峰值 / 依赖体积 / 工程成本 / Java 21 class file 兼容）背书，明确终裁 + 拒绝理由 + 重估触发。
- `00-overview.md` 状态草案→active；`README.md` 待落档清单同步。
- roadmap item 1/2 状态流转（draft review 通过→`planned`；closure audit 通过→`done`）。

## Non-Goals

- 不建 `nop-bytecode` 模块、不写任何进入仓库源码树的分析器/内核代码（Wave 1 item 3 起）。
- 不修改 nop-lint 引擎、现有工具（SpotBugs/Sonar/ArchUnit/PMD/mjs 门禁族）的任何接线、配置、账本（Hard constraint 1；SpotBugs qa profile 仅**只读实跑**取成本基线）。
- 不裁定 Wave 5 跨过程面（item 10/11 另行；本 plan 仅在账本登记其 adopt-or-skip 待裁状态）。
- 不产出生产级 null-flow / 资源配对分析器（Wave 2/3；本 plan 的 toy 探针仅为底座取证）。
- 不做 CI 接线（Wave 4 item 9）。

## Scope

### In Scope

- `ai-dev/design/nop-bytecode/gap-ledger.md`（新建）
- `ai-dev/design/nop-bytecode/substrate-adjudication.md`（新建，ADR）
- `ai-dev/design/nop-bytecode/00-overview.md`（状态行翻转草案→active；不动其余内容）
- `ai-dev/design/nop-bytecode/README.md`（待落档清单→已落档互链）
- `ai-dev/backlog/nop-bytecode-analysis-roadmap.md`（仅 Work Items 动态块状态翻转 + Last updated）
- `_tmp/nop-bytecode-poc/`（POC 脚手架 + 原始数据，gitignore，不入库）
- `ai-dev/logs/{执行当日}.md`（执行日志）

### Out Of Scope

- 任何 `nop-*` 模块源码 / pom 变更
- 既有 roadmap（含姊妹 roadmap）与统一账本的内容修改
- SootUp/WALA 的选型深评（候选 C 仅按 POC 口径取证；Wave 5 复评另起）

## Execution Plan

### Phase 1 — 缺口账本（roadmap item 1）

Status: completed
Targets: `ai-dev/design/nop-bytecode/gap-ledger.md`、`ai-dev/backlog/nop-bytecode-analysis-roadmap.md`（Work Items 动态块）

- Item Types: `Decision`

执行输入：`ai-dev/design/00-design-writing-guide.md`（账本落 design 目录，格式与层级受其约束——先读再写）。

- [x] **draft review 通过后立即执行**：roadmap Work Items item 1 / item 2 状态 `todo`→`planned`（带本 plan 指针），roadmap 头部 `Last updated` 刷新
- [x] 建账本骨架：定位声明（行级状态唯一动态载体，roadmap 只跟踪 wave 进度——Hard constraint 7 分工）+ 状态词表（open/claimed/closed/not-pursued + 重估触发字段）
- [x] 三锚点缺口逐条登记，每条四要素（裁定/理由/机制面证据/重估触发）：
  - null-flow 解引用路径面（锚点：tool-replacement baseline 第 3 条 + plan 24）→ 承接（Wave 2）
  - 资源泄漏 acquire/release 跨路径配对面（锚点：baseline 第 3 条 + plan 23 Option A）→ 承接（Wave 3）
  - 跨过程 taint 面（锚点：统一账本 Sonar 行）→ Wave 5 adopt-or-skip 待裁（依赖 item 10 成立）
  - 全程序注解推导面（锚点：plan 24 NullAway not-replaceable）→ 归属裁定（本通道是否承接注解契约读取面，与 Wave 5 参数 nullness 契约面的关系写明）
- [x] 范围锚 4 永不承接面显式归档（各含理由 + 重估触发）：源码注释依赖面 / autofix 面 / 依赖闭包架构断言面 / 风格面 / 编辑器实时档
- [x] 与源码 lane 已覆盖面的去重边界写明（pattern 面 5 条已落地规则不重复；禁止同缺陷同位置双报——准入判据 3）
- [x] 每个承接分析器行声明误报控制面（豁免机制 / 语义门控 / 白名单——Wave 2/3 v1 落地时细化为准入判据 1 的验收项）
- [x] design README 待落档清单 gap-ledger 行翻已落档并互链

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `gap-ledger.md` 存在：每个缺口行四要素齐备（缺任一要素 = 不合格），永不承接面全部归档且各带重估触发
- [x] 账本与三锚点原文可对读：锚点文档→账本行有可追踪引用（引用路径在 repo 中存在，`check-doc-links.mjs --strict` 0 errors）
- [x] 语义核对：账本裁定的承接/不承接与三锚点原文的 Deferred/not-replaceable 定性一致（承接不推翻源码 lane 任何裁定——Hard constraint 1）
- [x] 纯文档 Phase，无代码变更：`No new test required`（文档裁定类工作项）；构建验证不适用
- [x] `ai-dev/logs/{执行当日}.md` 对应条目已更新

### Phase 2 — 底座 POC spike 与终裁 ADR（roadmap item 2）

Status: completed
Targets: `_tmp/nop-bytecode-poc/`（POC 脚手架）、`ai-dev/design/nop-bytecode/substrate-adjudication.md`、`ai-dev/design/nop-bytecode/00-overview.md`（状态翻转）、`ai-dev/design/nop-bytecode/README.md`

- Item Types: `Proof | Decision`

- [x] **toy null-flow 探针统一口径**（三候选同题可比）：对语料每个方法——(1) 构建方法内 CFG；(2) 三值 lattice（NONNULL/MAYNULL/NULL）沿 CFG 传播引用 nullness；**分支敏感面 POC 限定为 IFNULL/IFNONNULL 两条 opcode**（javac 对 `x == null` / `x != null` 的编译产物，占判空守卫主体），其余分支边按 join-insensitive MAYNULL 合并——此为 POC 降配口径，与 Wave 2 正式口径（全条件分支敏感）的差异必须在 ADR 中注明；(3) 对解引用指令（字段访问/数组加载/实例方法调用）操作数在任一入边为 MAYNULL/NULL 时记一次命中，方法+指令去重。跑通标准 = 对语料产出命中清单且总数 > 0，命令与清单规模留档
- [x] **五维数据测量协议（三候选统一）**：
  - 吞吐：A/C = 语料全部 class 的「解析+CFG+探针」总 wall time（同进程单跑三次取中位）；B 的对应口径 = spotbugs 全 run wall time（框架级替代口径，**ADR 表中单列标注，不与 A/C 直读对比**）
  - 内存峰值：`/usr/bin/time -l` 取 max RSS，统一 JVM 启动参数 `-Xmx1g`
  - 依赖体积：实际解析到的运行时 classpath 全部 jar 文件字节数求和（传递闭包口径，逐 jar 列表留档）
  - 工程成本：POC 源码 LOC（不含构建脚本）+ 外部 API 触点数（import 的第三方包级 API 面计数）
  - Java 21 class file 兼容：v65 toy class 实测通过/失败 + v61 语料实跑结果并存
- [x] **失败处置（适用于任一 POC 腿 A/B/C）**：运行期失败（拉取失败/编译不过/实跑超时/探针零命中）必须显式记录失败点与原因；该腿降格为「失败记录 + 已文档化成本证据」，终裁对该腿的结论只能依赖其余两腿数据 + 该腿已文档化机制面证据，且 ADR 该行必须写明「POC 未跑通 + 失败点 + 重估触发」——禁止静默跳过或含糊带过
- [x] **POC-A（ASM）**：asm-tree 9.7.1（本地 .m2）解析单模块 class 文件 → 建 CFG → 探针跑通；按上述协议留档五维数据
- [x] **POC-B（SpotBugs）**：(a) 既有 qa profile 接线只读实跑一个模块取框架成本基线（wall time + max RSS，failOnError=false pom 内建口径，命令形如 `./mvnw -pl nop-jq -Pqa spotbugs:spotbugs`，零 pom 改动）；(b) 最小 plugin SPI detector 骨架（独立 `_tmp` 工程，不触碰仓库 pom），经 CLI 属性 `-Dspotbugs.pluginList=<jar>` 挂载实跑——**可观察判定 = 预置 toy class 触发该 detector 的自定义 bug code，spotbugs 输出（xml/log）中出现该 bug code**；内部 API 稳定性风险引用分析文档归因
- [x] **POC-C（SootUp）**：maven central 拉取点分坐标（`org.soot-oss:sootup.core` + `sootup.java.core` + `sootup.java.bytecode`，**pin 1.3.0 对齐组合**；如该组合运行期不兼容，按统一失败处置记录并记录实际尝试的版本组合）→ 同语料解析 + CFG + 探针跑通；数据同协议留档；失败按上文统一失败处置执行
- [x] **v65 兼容实测**：`javac --release 21` 编译 toy class（本机 JDK），三候选各解析通过一次，结果留档；v61 语料实跑数据并存
- [x] **ADR 落档**：`substrate-adjudication.md`——五维数据对照表 + 终裁（含模块默认底座与 Wave 5 复评关系）+ 每候选保留/拒绝理由 + 重估触发；零修改既有设计文档的结论性内容（00-overview 仅状态行翻转）
- [x] `00-overview.md` 状态行草案→active（ADR 已落档的前置条件满足）；README 待落档清单 substrate-adjudication 行翻已落档

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] ADR 中每候选五维数据齐备且**可复现**（数据旁留精确命令行；ADR 原始数字与 `_tmp` 留档一致；B 的吞吐为框架级替代口径并单列标注）——候选 C 未跑通时，以其统一失败处置的降格记录形态视为满足本条
- [x] toy 探针在 A/C 产出命中清单 > 0（任一腿失败时以统一失败处置记录形态视为满足）；B 以「实跑基线 + SPI 骨架挂载且输出含自定义 bug code」形态取证（框架自带 NullDerefAnalysis 即 CFG+null-flow 机制存在性的机制面证据，引用分析文档行号）
- [x] v65 兼容结果在档（每候选一行：通过/失败 + toy class 编译命令）；v61 语料数据并存
- [x] ADR 终裁明确：主底座候选唯一 + 拒绝项各带理由与重估触发；与 00-overview §2.5 外部能力使用形态无矛盾
- [x] `00-overview.md` 与 `README.md` 状态同步完成（grep 可验证状态行文本）
- [x] 本 Phase 无仓库源码变更（POC 全部在 `_tmp` 独立工程）：`No new test required`（spike 取证非产品功能）；`./mvnw` 构建验证不适用（POC 用独立 javac/maven 工程）；`scan-hollow-implementations.mjs` 不适用（无受影响模块）
- [x] `ai-dev/logs/{执行当日}.md` 对应条目已更新

### Phase 3 — 收口与状态流转

Status: completed
Targets: `ai-dev/backlog/nop-bytecode-analysis-roadmap.md`、本 plan

- Item Types: `Proof | Follow-up`

- [x] roadmap Work Items：item 1 / item 2 状态翻转 `planned`→（closure audit 通过后）`done`，格式对齐姊妹 roadmap 惯例（`done`（plan: …；closure audit agent_… APPROVE））；Milestone M0 行核对
- [x] roadmap 头部 `Last updated` 刷新
- [x] 文本一致性核对：Plan Status / 各 Phase Status / Exit Criteria / Closure Gates / daily log 五处一致
- [x] 终门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
- [x] 独立子代理 closure audit（fresh session）+ evidence 全文写入本 plan `## Closure`

Exit Criteria:

- [x] roadmap item 1/2 状态 = `done` 且带 plan 指针 + audit agent id；M0 两项解锁条件均 done
- [x] check-plan-checklist --strict 退出码 0（无未勾选 in-scope 项 + Closure Evidence 已写入）
- [x] check-doc-links --strict 0 errors
- [x] 独立 closure audit 完成，evidence 按 guide 模板写入 plan Closure 段
- [x] `ai-dev/logs/{执行当日}.md` 收口条目已更新

## Closure Gates

> 纯文档 + `_tmp` POC 计划：无仓库源码/模块变更，`./mvnw compile` / `./mvnw test` / checkstyle 不适用（POC 构建验证以 Phase 2 Exit Criteria 的可复现命令 + 数据留档替代）。

- [x] gap-ledger 落档且四要素齐备、与三锚点可对读、语义一致（无对源码 lane 既有裁定的推翻）
- [x] substrate ADR 落档：终裁唯一、五维数据可复现、v65 实测在档、拒绝项带理由与触发
- [x] 00-overview 状态翻转 + README 待落档清单同步（设计目录三文件互链一致）
- [x] roadmap 动态块状态与本 plan 状态一致（无 `todo` 残留于 item 1/2，无提前 `done`）
- [x] 不存在被静默降级的 in-scope 项（任一 POC 腿 A/B/C 若未跑通，必须有统一失败处置的显式记录：失败点 + 降格结论依据 + 重估触发，不得含糊带过）
- [x] 独立子代理 closure audit 完成并记录证据
- [x] `check-plan-checklist.mjs --strict` 与 `check-doc-links.mjs --strict` 均退出码 0

## Deferred But Adjudicated

（无——本 plan 范围内工作项不接受延期；POC-B 的 SPI 腿未跑通属「证据纪律处置」而非延期项，处置方式见 Phase 2 与 ADR §四。）

## Non-Blocking Follow-ups

- 账本门禁脚本（终裁词表 + enum-set 防腐，对标 nop-lint 统一账本门禁形态）——待 Wave 1 模块骨架落地后随模块 docs 迁移一并建（Why Not Blocking Closure: Wave 0 账本为静态新建文档，无既有内容漂移风险；门禁价值在后续滚动更新期）
- WALA 候选深评——Wave 5 item 10 spike 时一并复评（Why Not Blocking Closure: SootUp 与 WALA 同属候选 C 族，Wave 5 才有跨过程实证需求）

## Closure

Status Note: Wave 0 两项收口：gap-ledger 落档（G1–G5 四要素齐备，范围锚 4 永不承接面归档，与三锚点语义一致无推翻）；substrate ADR 落档（A=ASM 自研采纳，B=拒绝含 SPI 集成失败的证据纪律处置，C=限 Wave 5；五维数据可复现，v65 三候选通过，POC 降配口径与 Wave 2 正式口径差异已注明）。00-overview 转 active，M0 达成，Wave 1 三项可立项。POC-A 探针经独立审计员现场重跑复现（97 classes / 811 methods / hits=2791 与留档一致）。
Completed: 2026-09-29

Closure Audit Evidence:

- Reviewer / Agent: agent_c610c083-bf77-46f3-b643-2b5312a7a959（独立 fresh-session 子代理，两轮）
- Audit Session: sess_49c9956b-0ccc-48c1-ba6b-d6a1bd2eb130 / agent_c610c083-bf77-46f3-b643-2b5312a7a959
- Evidence:
  - Phase 1 Exit Criteria 5/5 PASS（gap-ledger 四要素+锚点对读 :122/:86/:23,45+语义一致+纯文档+log 条目）
  - Phase 2 Exit Criteria 7/7 PASS（五维数据逐项与 _tmp/nop-bytecode-poc/results 留档吻合；B 框架级口径单列；v65 三腿在档；ADR 终裁唯一+重估触发；POC-B 失败三要素齐备=非静默降级）
  - Phase 3 Exit Criteria 5/5 PASS（roadmap done+M0 UNLOCKED+audit id 回填；check-doc-links --strict 0 errors 实测；checklist 见下）
  - Closure Gates 7/7 PASS（含 Anti-Hollow：审计员独立重跑 PoCA 复现 hits=2791；失败腿非静默降级核查）
  - 首轮审计 REJECT（3 Major：M1 doc-links 4 errors / M2 ADR 缺复现命令 / M3 缺编译命令+C 腿 v65 工件；4 Minor）；修复后第二轮 fix-verification 9/9 PASS → **APPROVE**
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（evidence 写入后复跑，见收口提交）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors（修复本任务 1 处 + 其他流既有 20 处断链后）
  - Anti-Hollow 检查：POC 真实可复现（审计员重跑）；`scan-hollow-implementations.mjs` 不适用（无受影响仓库模块，POC 全在 gitignored `_tmp/`）
  - Deferred 项分类检查：两条 Follow-up（账本门禁脚本、WALA 深评）均真实 non-blocking 且带 Why Not Blocking Closure，无 in-scope defect 降级
  - 纯增量约束：git 全量核对——nop-lint 代码 / root pom / nop-kernel pom / spotbugs-exclude.xml / 统一账本 / 姊妹 roadmap 零改动

Follow-up:

- 账本门禁脚本（对标 nop-lint 统一账本防腐门禁形态）——Wave 1 模块骨架落地后随模块 docs 迁移一并建
- WALA 候选深评——Wave 5 item 10 spike 时与 SootUp 一并复评

