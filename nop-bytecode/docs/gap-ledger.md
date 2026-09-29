# nop 字节码通道缺口账本（Gap Ledger）

> 日期: 2026-09-29
> 状态: active
> 定位: 字节码通道行级缺口状态的**唯一动态载体**。[roadmap](ai-dev/backlog/nop-bytecode-analysis-roadmap.md) 只跟踪 wave 进度与底座终裁（其 Hard constraint 7 的账本分工），本文档滚动更新每个缺口面的归属裁定与执行状态，两处禁止双写。
> 准入依据: [00-overview.md](ai-dev/design/nop-bytecode/00-overview.md) §1/§2（缺口面定位、高信号准入、范围锚）
> 执行输入锚点: 姊妹 roadmap [nop-lint-tool-replacement-roadmap.md](ai-dev/backlog/nop-lint-tool-replacement-roadmap.md) Current baseline 第 3 条；[plan 23](ai-dev/plans/nop-lint/23-resource-leak-v1.md) Deferred But Adjudicated（Option A）；[plan 24](ai-dev/plans/nop-lint/24-null-flow-adjudication.md) 空指针行三段归因；统一账本 [tool-replacement-ledger.md](nop-lint/docs/tool-replacement-ledger.md) Sonar 行
> 迁移记录: 2026-09-29 自 ai-dev/design/nop-bytecode/ 迁入本模块 docs 目录（roadmap item 3 / plan 02）；内部链接已统一为仓库根相对形式。
> 注: 本文件位于模块 docs 目录，`check-doc-links.mjs` 扫描域不含模块 docs——引用完整性由 plan 02 的人工核验项覆盖。

## 状态词表

| 字段 | 取值 | 含义 |
|---|---|---|
| 归属裁定 | `承接` / `不承接` / `待裁` | 该缺口面是否归本通道 |
| 执行状态 | `open` / `claimed` / `closed` | open=待立项；claimed=执行 plan 已建；closed=落地且对照在档 |

裁定行四要素：**归属裁定 + 理由/机制面证据 + 误报控制面（承接行必填）+ 重估触发**。缺任一要素的行视为未登记。

## 一、缺口登记

### G1 空指针解引用路径（null-flow，方法内路径敏感）

- 归属裁定: **承接**（Wave 2）
- 执行状态: `open`
- 来源锚点: 姊妹 roadmap Current baseline 第 3 条 + plan 24（null-flow Deferred）
- 理由/机制面证据: 源码 lane L3 数据流为方法内 def-use + 常量传播，无路径敏感 nullness 传播能力（plan 24 归因原文）；其 pattern 面 5 条规则（throw-null / equals-null / no-throw-npe / catch-npe / no-return-null）只覆盖**模式可表达子面**，解引用前判空的路径敏感面显式 Deferred。字节码层方法内 CFG 显式化 + 抽象解释（SpotBugs NullDerefAnalysis 同形态）是该面的成熟工程形态——本通道 Wave 1 内核（CFG + nullness lattice）直接承接。
- 误报控制面（声明，Wave 2 v1 落地时细化）: 豁免 = assert 语句 / `Objects.requireNonNull` 系调用语义门控 / 平台 `NopException` 前置检查形态；已知命中集对照 = SpotBugs 同语料实跑 + plan 24 裁定中的案例集。
- 重估触发: Wave 2 v1 已知命中集对照数据不支持时（与 plan 24 Deferred 触发共享语义：任一侧重估均重开双方归属对照）。

### G2 资源泄漏 acquire/release 跨路径配对

- 归属裁定: **承接**（Wave 3）
- 执行状态: `open`
- 来源锚点: 姊妹 roadmap Current baseline 第 3 条 + plan 23 Deferred But Adjudicated（Option A）
- 理由/机制面证据: plan 23 归因——路径敏感 acquire/release 配对需"路径敏感资源追踪 + 跨分支 release 状态合并"，超出源码 lane L3 通道；v1 Option B 保守面（closeable-not-closed，判据四条件交集）已落地，其误报数据是本面重估输入。方法内资源配对在字节码层有成熟参考形态（SpotBugs ObligationAnalysis / ResourceValueAnalysis / FindOpenStream 三件，机制本体量级约 1.5k 行——roadmap 底座候选表与 Wave 3 描述在案），属方法内路径敏感分析，本通道内核可承载。
- 误报控制面（声明，Wave 3 v1 落地时细化）: 豁免 = try-with-resources / finally-close 路径 + 已知 wrapper 形态白名单；资源注册表范围 = Closeable / 连接 / 锁三类。
- 重估触发: Wave 3 v1 对照数据不支持时；或源码 lane Option A（重估触发 = v1 保守面误报数据不支持）先落地——届时进入并行对照期去重裁定（准入判据 3）。

### G3 跨过程 taint 分析

- 归属裁定: **待裁**（Wave 5 adopt-or-skip，依赖 roadmap item 10 跨过程 spike 成立）
- 执行状态: `open`（blocked on item 10）
- 来源锚点: 统一账本 Sonar 行（跨过程 taint not-replaceable，HC 口径）
- 理由/机制面证据: 源码 lane 已裁 not-replaceable（跨过程数据流传播超 per-file 引擎问题域）；本通道范围锚 = class-file 信息可及面，跨过程 taint 需调用图 + 指针分析能力——自建为多年量级工程尾（设计权威 §4 已拒绝），唯一可行形态 = 外部全程序框架窄桥接（§2.5：独立进程、结果报告消费、LGPL 仅限构建工具边界）。是否采纳 = item 10 spike 的 adopt-or-skip 裁定。
- 误报控制面: 待裁后补（若采纳，taint 源/汇注册表白名单为最小控制面）。
- 重估触发: item 10 spike 结论（成立 → 进入本面承接细化；不成立 → 本行翻 `不承接` 归档，与统一账本 Sonar 行并行有效）；Tai-e 或同级外部框架使用形态变化。

### G4 参数 nullness 注解契约读取（RuntimeVisibleAnnotations 面）

- 归属裁定: **承接候选**（Wave 5 item 10 的组成部分，不独立立项）
- 执行状态: `open`（blocked on item 10）
- 来源锚点: plan 24（NullAway 归因：全程序注解推导超出源码 lane 引擎）+ roadmap item 10（注解契约可读性）
- 理由/机制面证据: `@Nullable` / `@NonNull` 编译后存于 RuntimeVisibleAnnotations/RuntimeInvisibleAnnotations，字节码层可直接读取——这是本通道对"注解契约面"的**读取**能力；它服务于跨过程参数 nullness 契约（G3 同一 spike 的输入面），不构成独立分析器。
- 误报控制面: 与 G3 共用（契约读取是输入面，非发现面）。
- 重估触发: 同 G3。

### G5 全程序注解推导引擎（NullAway 形态）

- 归属裁定: **不承接**（not-pursued）
- 执行状态: `closed`（裁定即终态，重估触发在案）
- 来源锚点: plan 24（NullAway not-replaceable）+ 设计权威 §4（从零重做全程序指针分析被拒）
- 理由/机制面证据: 全程序注解推导需调用图 + 过程间传播 + 注解缺省推断，属全程序指针分析族——设计权威 §4 以"工程尾为多年量级、本通道 mandate 是发现流而非研究引擎"显式拒绝；self-contained 约束（§2.5）禁止其作为平台内嵌引擎支柱。G4 的读取面 + G3 的窄桥接是本通道对该问题域的全部承接形态。
- 误报控制面: 不适用（不承接）。
- 重估触发: Wave 5 采纳外部框架（item 10 成立）时，"全程序注解推导"作为外部工具能力面重估——承接形态仍限窄桥接，不改自建禁令。

## 二、永不承接面归档（roadmap 范围锚 4）

| 面 | 理由 | 重估触发 |
|---|---|---|
| 源码注释依赖面（fall-through 注释豁免等） | 注释不进 class file——字节码层**结构性不可及** | 无（结构性边界，非能力边界） |
| autofix 面 | 修复动作载体是源码编辑；本通道运行在编译产物之上，无源码修改职责（nop-lint 准入判据 3 的 autofix 加分轴归源码 lane） | 本通道 mandate 扩展时——须另立 roadmap（roadmap HC 1 禁止夹带） |
| 依赖闭包类架构断言 | ArchUnit 已承担，并行不替代（roadmap HC 4） | ArchUnit 接线撤销时重估 |
| 风格面 | 非缺陷发现面；两 lane 准入判据（高信号优先）同排除 | 无 |
| 编辑器实时档（<50ms） | 编译产物前提在编码期不成立——半成品只有源码可分析（设计权威 §1 non-goals） | 无（结构性边界） |

## 三、与源码 lane 的去重边界（roadmap 准入判据 3）

- **空指针**：源码 lane pattern 面 5 条已落地规则归源码 lane；本通道只做路径敏感 null-flow 面，不重复模式可表达子面。
- **资源泄漏**：源码 lane closeable-not-closed（Option B 保守面，warning 档）归源码 lane live 规则；本通道只做路径敏感 acquire/release 配对面。
- **双报处置**：并行对照期若同缺陷同位置双报，归属裁定在 Wave 4 item 8 对照收敛中逐条裁决（重复/互补/一方误报三分类），裁决记录回填本账本对应行。
- **归属单一**：每个缺口面归属唯一通道；源码 lane 未来若落地 Deferred 面（Option A / null-flow），本通道对应行进入并行对照期，不撤销承接（互补优先，去重裁决在对照数据上做）。

## 四、执行状态总览

| # | 缺口面 | 归属裁定 | 执行状态 | 波次 |
|---|---|---|---|---|
| G1 | null-flow 解引用路径 | 承接 | `open` | Wave 2 |
| G2 | 资源泄漏 acquire/release 配对 | 承接 | `open` | Wave 3 |
| G3 | 跨过程 taint | 待裁 | `open`（blocked on item 10） | Wave 5 |
| G4 | 参数 nullness 注解契约读取 | 承接候选 | `open`（blocked on item 10） | Wave 5 |
| G5 | 全程序注解推导引擎 | 不承接 | `closed` | — |

> 滚动维护规则：Wave 2/3 立项时把 G1/G2 翻 `claimed`（带 plan 指针）；v1 对照在档后翻 `closed`（带对照记录指针）。本表只加行与翻状态，不删历史行。
