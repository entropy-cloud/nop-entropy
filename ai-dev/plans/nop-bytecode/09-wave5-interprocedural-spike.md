# 09 Wave 5 跨过程调用图 spike 与 taint 面重估（roadmap items 10-11, M4）

> Plan Status: completed
> Last Reviewed: 2026-09-30
> Source: [nop-bytecode-analysis roadmap](../../backlog/nop-bytecode-analysis-roadmap.md) Wave 5 items 10-11 + [substrate-adjudication.md](../../design/nop-bytecode/substrate-adjudication.md) §一/§三/§五点五/§六 + [00-overview.md](../../design/nop-bytecode/00-overview.md) §2.5/§3.1/§4 + [gap-ledger.md](../../../nop-bytecode/docs/gap-ledger.md) G3/G4/G5 + [analysis 2026-09-29 Tai-e 倾向](../../analysis/2026-09/2026-09-29-spotbugs-tai-e-complexity-and-pta-lane-choice.md)
> Related: [01-wave0](01-wave0-gap-ledger-and-substrate-adjudication.md) · [07-spotbugs-dual-run](07-spotbugs-dual-run.md) · [08-ci-wiring-adjudication](08-ci-wiring-adjudication.md)
> Draft Review: 两轮独立子代理对抗性审查通过（agent_2d22bafa：4 Major + 6 Minor 全修复；agent_1973d992：10/10 FIXED 复审确认，无 Blocker/Major）

## Purpose

完成 Wave 5 收口（roadmap 最后一个 wave）：

1. **item 10（跨过程调用图 spike）**：以最小 spike 证据完成跨过程面 **adopt-or-skip 终裁**——候选 C 复评（SootUp 最小工程，roadmap 原文）+ 注解契约读取探针（G4）+ Tai-e 外部工具腿（design §2.5 具名例 + analysis 倾向）。数据与裁定落 `nop-bytecode/docs/interprocedural-spike.md`。
2. **item 11（taint 面重估）**：按 item 10 终裁走**条件分支**——成立 → taint 面重估记录落档（窄桥接形态 + successor 指针）；不成立 → 归档不追（重估触发在档）。
3. **M4 落档**（形态按裁定如实标注）；roadmap 全部 11 项收口。

裁定判据在本 plan 执行前钉死（见「裁定判据」节），防止事后合理化。

## Current Baseline

- roadmap items 10/11 = `todo`（唯一动态块原文）：
  - item 10：「**跨过程调用图 spike**：候选 C 复评（SootUp 最小工程）；参数 nullness 契约面；注解契约可读性（RuntimeVisibleAnnotations 在字节码层可直接读取）」
  - item 11：「**taint 面重估**：统一账本 Sonar 行 not-replaceable 在案；仅当 item 10 成立才进入，否则归档不追」
  - M4：「★ **Milestone M4（待裁形态）: 跨过程面 adopt-or-skip 落档**（unlocks when 10–11 done）」
- **ADR（substrate-adjudication）**：SootUp API 可用性已实证（254 行 POC 跑通、jq 语料 toy 探针 2835 条与自研同量级）；依赖 33 jars / 9,056 KB；RSS ~289 MB；解析+CFG 416 ms。§三：Wave 5 item 10 以 SootUp 为首选外部框架候选（外部工具/窄桥接形态）。§六重估触发：SootUp 版本线收敛或依赖闭包显著瘦身时候选评估数据更新。
- **SootUp POC 闭包缺口**：`_tmp/nop-bytecode-poc/poc-sootup/libs` 的 33 jars 中 SootUp 构件仅三枚（core / java.core / java.bytecode 1.3.0），**调用图算法在 1.3.0 位于独立构件 `org.soot-oss:sootup.callgraph`**（central 已核实存在 1.3.0；闭包内已有 jgrapht-core 1.3.1）——spike 需补解析（连带其传递闭包）。
- **design 00-overview**：§2.5 外部能力使用形态 = 外部工具/窄桥接数据点（CI 内独立进程、消费结果报告），LGPL 整库依赖仅限构建工具边界，**禁止源码移植**；§3.1 外部桥接层行 = 「Wave 5，待裁」，失败语义 = 降级为「跨过程面缺席」并报告，不阻塞字节码通道主体；§4 已拒绝「从零重做全程序指针分析」（多年量级工程尾）与「移植 Tai-e/SpotBugs 源码」（许可证冲突）。
- **gap-ledger**：G3（跨过程 taint）待裁 / `open`（blocked on item 10）；G4（参数 nullness 注解契约读取）承接候选 / `open`（blocked on item 10，服务于 G3 同 spike 的输入面）；G5（全程序注解推导引擎）不承接 / `closed`——重估触发：「Wave 5 采纳外部框架（item 10 成立）时，全程序注解推导作为外部工具能力面重估——承接形态仍限窄桥接，不改自建禁令」。
- **analysis 2026-09-29（Status: open）**：Wave 5 倾向 = 直接使用 Tai-e 作为外部工具（maven central `net.pascal-lab:tai-e`，v0.5.4 已核实存在；LGPL-3.0；taint 分析 3.2k 行现成；pre-1.0 API churn 须 pin + 隔离）；SpotBugs 无 PTA 已否决（keep-tool 价值仅在既有 qa 接线）。四个 Open Questions（v65 支持范围 / 全仓 PTA 性能 / 框架型库入口点建模成本 / API 稳定性承诺）= 本 spike 的待采集项。
- **仓外资产**：Tai-e 源码浅克隆 `~/sources/lint/Tai-e`（GPL 源码留在仓外，仅作外部工具构建/运行源，不入本仓）；SootUp POC 脚手架 `_tmp/nop-bytecode-poc/poc-sootup/`（gitignore）；nop-jq 编译产物语料在档（POC/双跑同源，97 classes / 811 methods，class file v61）。
- **通道姿态**：CLI report-only（plan 04），CI 接线暂缓（plan 08 数据驱动裁定，触发在档）——本 spike 属能力裁定，不依赖也不改动 CI 接线。
- HEAD 含并行流提交（nop-stream plan 366-368）；nop-bytecode 相关路径已全部入库（plan 08 = `66eaa9ae91`）；今日日志 `ai-dev/logs/2026/09-30.md` 顶部已有并行流条目——写入时按实际文本锚点顶部插入。

## Goals

- 三条 spike 腿证据齐备且落档（含失败腿如实记录，ADR §四同款协议）：
  - 腿 1 = G4 注解契约读取探针（ASM 读 jq 语料注解属性，数字 + 样例）；
  - 腿 2 = SootUp 调用图最小工程（roadmap item 10 原文）；
  - 腿 3 = Tai-e 外部工具最小实跑（含 taint 探针可行性）。
- adopt-or-skip 终裁按「裁定判据」节逐条对照数据落档；无论哪个分支，四要素齐备（裁定 + 理由 + 机制面证据 + 重估触发，HC3）。
- gap-ledger G3/G4 翻转 + G5 重估触发兑现注记；design 00-overview 外部桥接层行待裁→终态；roadmap items 10/11 done + M4 落档 + `Last updated` 刷新；analysis 文档去向注记。
- 完成后单提交（选择性 add）。

## Non-Goals

- **不在 nop-bytecode 模块落地任何跨过程分析器实现**（adopt 时实现归 successor plan——roadmap M4 = adopt-or-skip 落档，不含实现）。
- **不改 `nop-bytecode/pom.xml`**：adopt 形态 = 外部进程窄桥接（构建工具边界），模块运行时依赖维持 JDK + ASM（design §3.2 零依赖并行裁定不动）。
- 不动 nop-lint 资产 / 统一账本 / SpotBugs 接线 / CI workflow / `.github/**`（HC1 纯增量；plan 08 姿态维持）。
- 不移植 Tai-e / SootUp 源码进本仓（许可证 + 清洁室纪律，design §4）。
- 不做 taint 分析器本体——即使 adopt，item 11 的交付 = 重估记录 + successor 所有权，不是实现。
- 不做全仓（100 模块）级 PTA 性能采集——spike 语料限单模块（jq）+ toy；全仓规模留 successor 实测。

## Scope

### In Scope

- `_tmp/nop-bytecode-w5-spike/`（gitignore，spike 脚手架与原始数据）
- `nop-bytecode/docs/interprocedural-spike.md`（新建：§一 数据三腿 + §二 终裁 + §三 successor/归档）
- `nop-bytecode/docs/gap-ledger.md`（G3/G4/G5 行翻转）
- `ai-dev/design/nop-bytecode/00-overview.md`（§1 表 Wave 5 行 + §3.1 外部桥接层行）
- `ai-dev/backlog/nop-bytecode-analysis-roadmap.md`（items 10/11 + M4 + Last updated）
- `ai-dev/analysis/2026-09/2026-09-29-spotbugs-tai-e-complexity-and-pta-lane-choice.md`（Status/去向注记 + 坐标核实勘误）
- `ai-dev/logs/2026/09-30.md`

### Out Of Scope

- nop-bytecode 模块源码与 pom；nop-lint 全部资产；统一账本；`.github/**`；Tai-e/SootUp 源码仓

## 裁定判据（执行前钉死）

**Adopt（item 10 成立）须同时满足五条**：

1. **规模预算**：至少一个外部框架在 jq 语料完成调用图构建——wall time ≤ 120 s 且 RSS ≤ 2 GB（CI 分钟级预算口径，design §1 档位定位；阈值推导依据 = SpotBugs 框架级基线 8.42 s / 1.72 GB 留余量，随裁定写入 spike doc §二）。测量协议：两腿统一 `/usr/bin/time -l` 采集 max RSS，JVM 参数（`-Xmx` 等）逐腿记录随数据入档；
2. **入口点适配**：Nop 语义入口（注解驱动 bean 方法面）以 toy 注解类或语料内同型面映射到框架入口点声明机制——可观察定义 = 框架接受入口点声明并产出**以注解方法为根的可达边集非空**的调用图，产物留 `_tmp`；
3. **下游价值**：至少一面成立——(a) 参数 nullness 契约 × 调用边联合实例：腿 1 的注解读取与腿 2/3 的调用图可联合产出至少一条「caller 方法 → 带参数契约注解的 callee 方法」机器可读记录（toy 或 jq 语料，留 `_tmp`）；或 (b) taint 探针发现含 **source 方法定位与 sink 方法定位两个元素**（Tai-e taint 报告 schema 或等效输出可解析出两定位）；
4. **窄桥接形态可行**：外部进程调用 + 机器可读结果消费在 spike harness 内验证通过（design §2.5 形态约束的操作可行性）；
5. **v65 支持**（design §3.1 解析层「Java 21 产物（class file v65）必须支持」的兑现）：被采纳框架以 v65 toy 语料（`javac --release 21`，POC 先例）实测解析/分析通过——SootUp 已有在档证据（ADR §二 v65 ✅），Tai-e 由腿 3 采集；不支持者不得成为被采纳框架。

**Skip（任一即 skip）**：判据 1-3、5 任一在 time-box 内失败；或判据 4 失败（形态不可行即不可承接）。

**失败腿协议**（ADR §四同款）：单腿受阻不静默——记录已达成部分 / 失败点 / 根因归因 / 重估触发，该腿按「未证能力」计入裁定，不宣称未证面。time-box：单腿一日量级封顶。

## Execution Plan

### Phase 1 — Spike 三腿数据采集

Status: completed
Targets: `_tmp/nop-bytecode-w5-spike/`、`nop-bytecode/docs/interprocedural-spike.md` §一（数据）

- Item Types: `Proof`

- [x] 腿 1（G4 探针）：ASM tree API 遍历 jq 语料，统计 Runtime(In)visibleAnnotations / Runtime(In)visibleParameterAnnotations 命中数与注解类型样例（jsr305/jetbrains/平台自有分类计数）；复跑命令与原始输出留 `_tmp`，数字入 spike doc §一.1——jq 契约空集（仅 3 @FunctionalInterface）；toy 全形态读取 PASS
- [x] 腿 2（SootUp 调用图）：补解析 `org.soot-oss:sootup.callgraph:1.3.0` 后在 jq 语料以显式入口点集构建调用图（CHA）；采集 wall time / RSS / reachable methods / call edges / 入口点声明 API 形态；入口点适配评估 = Nop 语义入口（toy 注解类）映射 demo PASS；数字入 spike doc §一.2——34 jars / 0.35 s / ~169 MB / api 面 20 entries→792 reachable·2601 edges
- [x] 腿 3（Tai-e 实跑）：获取 0.5.4——maven 构件 POM 无 main-class 打包（central POM 直查结论，复现命令随腿 3 留档），主路径 = 解析 `net.pascal-lab:tai-e:0.5.4` 连带传递闭包后手工拼 classpath 调用其主入口（或等效可运行形态）；不可行则仓外源码构建发行物（GPL 源码不出 `~/sources/lint/`）；最小实跑 = toy + jq 语料调用图/PTA；taint source/sink 配置探针产出玩具发现（可行则）；v65 支持验证（判据 5 采集）；wall time / RSS / 入口点机制 / 报告消费形态采集；数字入 spike doc §一.3——**实际走向：构件不可运行 → 仓外 master 构建 fatJar；current-JRE 模式（≤v69 运行时边界实证）；toy/jq CHA + taint 探针 1 flow 全通**
- [x] analysis 文档四 Open Questions 逐条在 spike doc 标注答案或如实记「未测」——四条全部结案（v65 ✓[带运行时边界] / 全仓 PTA 未测[Non-Goals，中间锚点在档] / 入口点四路径 / API churn 实锤）
- [x] `ai-dev/logs/2026/09-30.md` Phase 1 条目

Exit Criteria:

- [x] 腿 1 在 jq 语料产出可复核数字（命中数 = 0 也如实记录）且命令留档可复跑
- [x] 腿 2 调用图构建成功（或失败腿记录含已达成部分/失败点/根因/重估触发四件套），规模预算两维数据齐备
- [x] 腿 3 Tai-e 实跑成功（或失败腿四件套齐备），获取路径结论（maven 构件 vs 源码构建）明确在档
- [x] spike doc §一 数据与 `_tmp` 原始产物一致，无虚增面
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 本 Phase 不改 live baseline（spike 全在 `_tmp` + spike doc 数据节）：`No owner-doc update required`（spike doc 自身归 Phase 2 裁定面一并核对）

### Phase 2 — adopt-or-skip 终裁落档

Status: completed
Targets: spike doc §二/§三、gap-ledger、00-overview、roadmap、analysis 注记

- Item Types: `Decision`

- [x] draft review 通过后：roadmap items 10/11 `todo`→`planned`（本 plan 指针）+ `Last updated` 刷新
- [x] spike doc §二 终裁：按「裁定判据」五条逐条对照 Phase 1 数据，得出 adopt 或 skip；四要素齐备（裁定 + 理由 + 证据指针 + 重估触发）+ 判据 1 阈值推导依据注记——裁定 = **adopt 窄桥接**（首选 Tai-e / SootUp 候选），五判据逐条对照表落档
- [x] spike doc §三 分支落档：adopt → 跨过程面承接形态 = 窄桥接（外部进程 + 结果消费 + pin 版本），successor 面清单（调用图接线 / 参数契约传播 / taint source/sink 注册表）+ 禁改项重申（模块 pom / 自建禁令）
- [x] gap-ledger 翻转：adopt → G3 `承接`+`open`（successor 待立项，指针 = spike doc §三清单，符合账本词表「claimed=执行 plan 已建」不得提前用）+ 误报控制面字段补实（承接行必填）+ G4 `承接`（输入面）+ G5 重估触发兑现注记（Tai-e 无注解推断能力，不承接维持）——skip 分支未触发
- [x] design 00-overview：§1 表 Wave 5 行、§3.1 外部桥接层行待裁→终态（adopt = 窄桥接契约生效）；措辞按 design guide（最终状态，不写演进叙事）
- [x] roadmap items 10/11 `planned`→`done`（裁定语义如实 + plan 指针；audit id 于 Phase 3 收口回填）；M4 落档（adopt 窄桥接形态如实标注）+ `Last updated` 刷新
- [x] analysis 文档：Status `open`→`resolved`（结论已被本 plan spike 消费）+ 坐标勘误注记（central 版本列表实查 0.5.4 在档，search API latestVersion 字段陈旧）+ Open Questions 去向指针
- [x] spike doc / gap-ledger 人工链接核验（模块 docs 不在 check-doc-links 扫描域，plan 02 先例）——链接均为仓库根相对或 spike 文内引用，人工核对通过
- [x] 文本一致性核对 + 两门禁（check-doc-links / scan-hollow --module nop-bytecode）——check-plan-checklist 留 Phase 3 终门禁复跑（Phase 2 边界时 Phase 3 项必然未勾选，全文件严格校验此时不可满足，plan 08 先例措辞「最终回填后复跑」）——实测 doc-links exit 0（本 plan 遗留 warning 已修）+ hollow exit 0

Exit Criteria:

- [x] 裁定与「裁定判据」节逐条对应，无判据外新标准；skip 分支下无未兑现的判据豁免
- [x] gap-ledger G3/G4 状态与裁定一致且四要素齐备（adopt 时 G3 误报控制面字段已补实）；G5 注记按分支如实（adopt=兑现 / skip=维持待触发）
- [x] design 00-overview 无残留「待裁」外部桥接表述；roadmap 无残留 items 10/11 `todo|planned`
- [x] 两门禁（check-doc-links / scan-hollow）退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] owner-doc 更新完成（gap-ledger + 00-overview + roadmap + analysis 注记）

### Phase 3 — 收口

Status: completed
Targets: plan 文件、daily log、git

- Item Types: `Proof`

- [x] 全文重读 plan；逐条核对 Phase 1/2 Exit Criteria 与 Closure Gates
- [x] 独立 fresh-session 子代理 closure audit（含 Anti-Hollow：本 plan 无代码链路，替代检查 = spike 数据可复跑性抽查 + 裁定四要素完备性 + 账本/设计/roadmap 一致性）+ evidence 写入 plan Closure 段——agent_5f8d0835 **APPROVE**（3 Minor 当场修复：spike doc RSS 中位数正、analysis Conclusion 时态注记、roadmap audit id 回填）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `ai-dev/logs/2026/09-30.md` 收口条目
- [x] 单提交（选择性 add：spike doc / gap-ledger / 00-overview / roadmap / analysis / log / 本 plan；工作区含并行流无关脏文件，`git status` 按本 plan 路径核对）

Exit Criteria:

- [x] closure audit APPROVE 且 evidence 写入（REJECT→fix→复审通过链如有则记录）——APPROVE 首轮通过
- [x] check-plan-checklist --strict 退出码 0
- [x] 提交完成且工作区无本 plan 遗漏文件（`git status` 按路径核对）

## Closure Gates

> 本 plan 为纯 spike + 文档计划（无模块代码变更，spike 产物在 gitignore 的 `_tmp`）：按 guide「纯文档计划」条款免除 `./mvnw compile/test` 门禁；模块回归由「未触碰模块源码」事实覆盖（`git status nop-bytecode/src` 为空）。

- [x] 三腿 spike 数据齐备（或失败腿四件套齐备），spike doc §一 与 `_tmp` 原始产物一致
- [x] adopt-or-skip 终裁按预钉判据落档，四要素齐备（HC3）
- [x] item 11 条件分支按 item 10 终裁如实落档（成立 → 重估记录 + successor；不成立 → 归档不追）
- [x] gap-ledger / 00-overview / roadmap / analysis 全部同步，无跨文档状态矛盾（文本一致性核对）
- [x] 不存在被静默降级的 in-scope live defect / contract drift（本 plan 无代码缺陷面；失败腿均按协议显式记录）
- [x] HC1 纯增量守住：nop-lint 资产 / 统一账本 / SpotBugs 接线 / CI workflow / `nop-bytecode/pom.xml` 零改动（`git status` 核对）
- [x] 独立子代理 closure audit 完成并记录证据（check-plan-checklist --strict 0 + Anti-Hollow 替代检查）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-bytecode --severity high` 退出码 0

## Deferred But Adjudicated

### 跨过程分析器本体实现（仅 adopt 分支）

- Classification: `out-of-scope improvement`（相对本 plan——roadmap M4 = adopt-or-skip 落档，实现超 roadmap 面）
- Why Not Blocking Closure: item 10/11 的交付物是终裁与重估记录；实现归属 successor，与 plan 08「接线归触发 plan」同一分桩逻辑
- Successor Required: `yes`
- Successor Path: 后续立项（adopt 时在 spike doc §三列出 successor 面清单：调用图接线 / 参数 nullness 契约传播 / taint source/sink 注册表——按 HC6 全部 report-only 起步）

### 全仓（100 模块）级 PTA 规模实测

- Classification: `optimization candidate`
- Why Not Blocking Closure: spike 判据锚定单模块语料 + CI 分钟级预算；全仓规模是 successor 实施期数据，不影响 adopt-or-skip 方向裁定
- Successor Required: `yes`
- Successor Path: 并入跨过程分析器本体 successor plan 的实施验收

## Non-Blocking Follow-ups

- SootUp 2.x/3.x 线（central 已有 3.0.1）与 1.3.0 的差异评估——仅当 adopt 且 successor 实施选版本时相关
- spotbugs-callgraph 类预生成 interproc 属性库（SpotBugs jdkBaseNonnullReturn.db 形态）作为轻量替代源的重估注记——随 successor 面清单一并评估

## Closure

Status Note: Wave 5 收口完成——item 10 三轮腿 spike 数据落档（G4 注解读取能力实证+jq 语料契约空集诚实记录 / SootUp 调用图 0.35s·~176MB+注解入口 demo PASS v61+v65 / Tai-e 仓外构建实跑 jq CHA 22.6k reachable 1.57s·1.13GB + taint 探针 1 flow）+ item 11 taint 重估随 adopt 分支落档。**裁定 = adopt 窄桥接**（五判据逐条对照全 PASS；首选 Tai-e / SootUp 候选；误报控制面 = taint source/sink 注册表；PTA 资源风险如实登记，模块级实测归 successor 第一验收项）。roadmap 全部 11 项 done，M4 UNLOCKED（adopt 窄桥接形态）。HC1 纯增量全程守住（nop-bytecode pom/src 与全部既有 lane 资产零改动）。
Completed: 2026-09-30

Closure Audit Evidence:

- Reviewer / Agent: agent_5f8d0835-b69a-485a-9850-bce947e2272e（独立 fresh-session 子代理）
- Audit Session: agent_5f8d0835
- Evidence:
  - Phase 1 Exit Criteria 6/6 PASS（逐条 live 实证：leg1×2 / leg2×6 / leg3×4 原始产物与 spike doc §一 数字逐一命中；`taie-build.log` 在档）
  - **Anti-Hollow 替代检查 PASS**：两条关键命令实际复跑——腿 1 输出与 leg1-jq.txt 完全一致；腿 2 toy 复跑 `TOY-ENTRY-DEMO=PASS` + `CALLER <toy.ToyService: void handle(java.lang.String)>`（产物可再生成，无一次性伪造面）
  - 裁定四要素完备 + 判据 1 阈值推导在仓内有锚（substrate-adjudication.md SpotBugs 框架级基线）；五判据逐条真实、无判据外新标准、无未兑现豁免
  - 跨文档一致性 PASS：gap-ledger G3/G4 词表合规（open 而非提前占用 claimed）+ G5 兑现注记；00-overview 零「待裁」残留、链接可达；roadmap items 10/11 done + M4 UNLOCKED、HC7 无双写；analysis Status resolved + Postscript 一致
  - HC1 纯增量 PASS：`git status` 定向核对 nop-lint / .github / nop-bytecode pom+src 零输出
  - 门禁：check-doc-links --strict exit 0（本 plan warning 已清；plan 06 三条历史 warning 按规则 20 不回写）；scan-hollow --module nop-bytecode exit 0
  - deferred 诚实性 PASS（两项 Deferred 均真实 non-blocking；负结果全部如实入档非走私）；资源风险如实性 PASS（PTA 13.4s/2.3GB 三处显式登记）
  - 3 Minor 当场修复：spike doc §一.2 RSS 中位数正（184,532,992 B≈176MB）、analysis Conclusion 时态注记、roadmap item 10 audit id 回填
  - `check-plan-checklist.mjs --strict` 最终回填后复跑退出码 0

Follow-up:

- 模块级 taint/PTA 窄桥接实施 plan（G3 successor，面清单 spike doc §三.1；单模块 PTA 资源实测 = 第一验收项）
- G4 参数契约面随「平台字节码注解契约约定」同案裁定（spike doc §三.2）
- Non-Blocking：SootUp 2.x/3.x 线差异评估；spotbugs-callgraph 预生成属性库替代源评估
