# Cycle 3 / I2 — 不变式驱动审计（生产 wiring 门禁跨全部服务注入 API 与消费方）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Draft Review: 2 轮独立子 agent 对抗性审查通过（round 1：2 Major（Anti-Hollow 缺运行时 E2E 实跑证据 / I1 硬前置仅散文未机械门）+ 6 Minor（Rule #25 注缺失 / hollow 扫描门缺失 / roadmap 行流转未归属 / multi-audit 候选未预枚举 / 行号过期警示缺失 / CepOperator javadoc 误引），全部修复；round 2：全部修复在案（Phase 1 预检机械门 4 检 + Phase 4 E2E 复跑命令与失败处置 + P2 计数 21→23 修正 + roadmap 行注记惯例），残余 1 Minor（roadmap 行注记）已并入，verdict 可转 active）
> Source: roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Cycle 3 / I2 行（① 跑 I1 门禁跨全部服务注入 API 与消费方类 → 确定性 red list；② 对抗探查聚焦门禁未表达盲区（checkpoint/watermark 服务同族"仅测试注入"实例复探、注入/恢复时序组合面、恢复路径行为盲区）+ P2 backlog 触发条件评估；③ 标注已知族或新族）；前置 I1 产出 `ai-dev/audits/nop-stream-invariants/{cycle3-I1-input.md,wiring-registry.json,mjs-pins.json}`；`ai-dev/skills/open-ended-adversarial-review-prompt.md`；open-audit 总评 `2026-08-12-1217-open-audit-nop-stream-invariant-loop.md`（盲区自评 + 总评第 2/3 点：恢复路径行为 / 组合面）
> Related: 前置 `2026-08-13-0805-1-nop-stream-invariants-cycle3-I1-wiring-existence-gates.md`（Cycle 3 / I1，硬串行）；后续 `2026-08-13-0805-3-nop-stream-invariants-cycle3-I3-adjudication.md`（I3 裁决，依赖本 plan 的权威 red list）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 3 / I2. 不变式驱动审计

## Purpose

把 Cycle 3 / I1 沉淀的生产 wiring 门禁（不变式 #7）跑向全部服务注入 API 与全部消费方，产出**确定性 red list**（仅测试注入实例 / 接线点漂移 / 新消费方 / 行为漂移二分）；对门禁未表达的盲区做**聚焦对抗探查**（checkpoint/watermark 服务同族"仅测试注入"实例复探、注入/恢复时序组合面、恢复路径行为盲区、`TimestampsAndWatermarksOperator` watch-only residual 复核）；评估 P2 backlog 触发条件（复探触发时评估适用项）；**每条发现标注已知族（#7 wiring 族）或新族**。产出权威版 `red-list.md` + `cycle3-I2-probing-report.md` 作为 I3 裁决的唯一输入。本 plan 只审计，不修复、不做 P0-P3 严重度裁决（属 I3）、不新增门禁（新族门禁属 Cycle 4 / I1）、不派生 Cycle 4 work item（属 I6 收口）。

## Current Baseline

> 已核对 live repo（2026-08-13）：注入 API 面、生产接线点、消费方、门禁工具基线全部实测存在；I1 为前置依赖声明（I1 未 completed 前不开始执行，硬串行）。

- **Cycle 3 / I1 为硬前置**：本 plan 执行前 `2026-08-13-0805-1-...`（Cycle 3 / I1）必须已 `completed`——wiring 门禁（`TestWiringExistenceInvariant` + `scan-wiring` + `wiring-registry.json`）全绿落地，`cycle3-I1-input.md` 落档。若执行时 I1 未 completed，本 plan 不得开始任何 Phase——立即返回 `blocked`。
- **注入 API 面（live 实测，2026-08-13）**：`AbstractStreamOperator.java` — `setOutput` :140 / `setProcessingTimeService` :148 / `setStateBackend` :152 / `setKeyedStateBackend` :165 / `setOperatorStateBackend` :173 / `setTimeServiceManager` :181 / `setSnapshotCallback` :320（执行时以 I1 注册表定稿版本为准，如有增减按 I1 权威版）。
- **生产接线点（live 实测）**：`StreamTaskInvokable.setupProcessingTimeServices` :456-477（`setProcessingTimeService` :462 + `setTimeServiceManager` :463；4 个构造函数调用 :151/:164/:180/:195）；`wireTailToRecordWriter` :420-428（`setOutput` :426）+ `wireOperators` 布线面；`setupSnapshotCallbacks` :430-438（`setSnapshotCallback` :435）；`GraphModelCheckpointExecutor` :735-747（`setStateBackend` 状态后端供给）。
- **消费方（live 实测）**：`getProcessingTimeService()` — `CepOperator.java:290/:300/:373/:381/:382/:401/:479/:490`、`WindowOperator.java:479`、`StreamTaskInvokable.java:353`；`getTimeServiceManager()` — `StreamTaskInvokable.java:345`；`registerTimerService` — `WindowOperator.java:471`。
- **门禁现状（I1 产出，前置声明）**：`node ai-dev/tools/check-nop-stream-invariants.mjs all` 预期 exit 0（inventory / sync / scan-iterations / scan-output-contract / scan-wiring / self-test）；JUnit 门禁 11 类（含 `TestWiringExistenceInvariant`）/ 测试数与 pin 明细以 `cycle3-I1-input.md` 为准（执行时核对）。
- **P2 backlog 触发条件清单（复探触发时评估适用项，open-audit P2 批次 10 条 + multi-audit P2 批次 23 条（P2-01~P2-23，以 live multi-audit 文档为准，roadmap 头注「21 条」为陈旧计数）**：与本 plan 探查面相关的候选——P2-03（`InMemoryClusterRegistry.getNodeLease` 无锁双 map 读 NPE，open-audit 批）；P2-01/P2-02（checkpointSuccessMap 无界增长 / onCompletePersistFailure 不 complete future，open-audit 批，checkpoint 面）；P2-04~P2-07（WindowOperator triggerAccumulators 永不裁剪 / evictor descriptor 路径 / pane 跟踪键错位 / purge 缺 clear，open-audit 批，窗口面——与 plan `2026-08-13-0132-3` 类别清扫相邻）；P2-08（CepOperator STEP-5 超时基准，CEP 面）；multi-audit 批各条按其触发条件评估（类别清扫触发时，预枚举见 Phase 3-e）；**本 plan 只评估触发条件是否成立并记录，不裁决严重度、不修复**（裁决属 I3）。
- **范围边界**：本 plan 与 `nop-stream-production` / `nop-stream-independent-audit` / `nop-stream-flink-comparison` roadmap 范围独立（roadmap「范围独立」条款）——探查为聚焦式，非全仓漫游式深度审计。

## Goals

- 跨全部服务注入 API 与全部消费方跑门禁（mjs all + JUnit 门禁子集），生成**确定性 red list**：V1 仅测试注入实例 / V2 新消费方 / V3 失效接线点 / V4 新注入 API / V5 失效消费方 / unpinned 违规 / stale pin 提示处置。
- 接线点注册表一致性复核（服务表 / 消费方表 / 接线点行号自洽）。
- 聚焦对抗探查门禁盲区（checkpoint/watermark 服务同族"仅测试注入"实例复探、注入/恢复时序组合面、恢复路径行为盲区、`TimestampsAndWatermarksOperator` residual 复核）+ P2 backlog 触发条件评估。
- 每条发现标注族归属：已知族（#7 wiring 族兄弟实例，可追溯注册表 / finding-ID）或新族（含不变式陈述候选 + 触发证据，供 I6 按 Loop Rule 派生 Cycle 4 / I1）。
- 产出权威版 `red-list.md`（合并门禁结果 + 注册表裁定 + 探查发现）与 `cycle3-I2-probing-report.md`，零悬挂移交 I3。

## Non-Goals

- **不修复任何 red list 项**（属 I4，经 I3 裁决后执行）。
- **不做 P0/P1/P2/P3 严重度裁决与派发**（属 I3）。
- **不新增门禁 / 不改门禁代码**（新族门禁属 Cycle 4 / I1；本 plan 只标注新族，不写门禁）。
- **不派生 Cycle 4 work item / 不改 roadmap work item 表**（派生属 I6 收口，按 Loop Rule 预授权执行）。
- **不做全仓漫游式深度审计**（范围 = wiring 族服务注入 API + 消费方 + 登记盲区；与 `nop-stream-independent-audit-roadmap.md` 不重叠）。
- **不改被测类代码**（即使发现缺陷，也只入 red list / 探查报告，不移改）。
- **不进入 `HG-01` 线协议设计**（人工确认门未过，属已登记待办）。

## Scope

### In Scope

- 门禁全量运行与确定性 red list 生成（mjs all + 11 类 JUnit 门禁 + pin 复核 + 注册表复核）。
- 聚焦对抗探查（同族"仅测试注入"复探 + 时序组合面 + 恢复路径行为 + residual 复核 + P2 backlog 触发条件评估）。
- 发现族标注（已知族兄弟实例 / 新族）与 red-list.md 权威化 + 探查报告落档。
- `ai-dev/logs/` 更新。

### Out Of Scope

- 修复（I4）、裁决（I3）、Cycle 4 派生（I6）、新门禁（Cycle 4 / I1）、`HG-01` 线协议。
- ArchUnit 引入（既有 deferred 裁定：`optimization candidate`，未触发）。
- 任何公共 API / 被测类代码变更。

## Execution Plan

### Phase 1 - 门禁全量运行与确定性 red list 生成

Status: completed
Targets: `ai-dev/tools/check-nop-stream-invariants.mjs`；`ai-dev/audits/nop-stream-invariants/{mjs-pins.json,wiring-registry.json,red-list.md}`；11 类 JUnit 门禁测试类

- Item Types: `Proof | Decision`

- [x] **I1 硬前置预检（机械门，先于任何门禁运行）**：逐一验证 (a) `2026-08-13-0805-1-...` Plan Status == `completed`；(b) `ai-dev/audits/nop-stream-invariants/wiring-registry.json` 与 `cycle3-I1-input.md` 存在；(c) `node ai-dev/tools/check-nop-stream-invariants.mjs all` 输出包含 `scan-wiring` 子命令（防 I1 未落地时 5 命令假绿）；(d) JUnit 门禁类 ≥ 11 个（含 `TestWiringExistenceInvariant`）；任一不满足 → **立即返回 `blocked`**（不开始任何 Phase），记录缺项
- [x] 运行 `node ai-dev/tools/check-nop-stream-invariants.mjs all`（inventory / sync / scan-iterations / scan-output-contract / scan-wiring / self-test），逐命令记录输出；**处置二分**（沿 Cycle 2 / I2 Phase 1 先例）：a) 新增 **pre-existing residual**（此前未登记 / 未 pin 的已知行为，无行为漂移）→ 入 red list + 追加 pin 记录（若属可 pin 类目）→ mjs 恢复 exit 0——不允许通过静默忽略恢复绿色；b) **行为漂移**（**已登记记录的行为变化**——注册表描述与 live 行为不符）→ 入 red list + 显式升级标记（意味着被测代码在 I1 后被改动，超出 I2 处置权，plan 转 blocked 移交升级）
- [x] **stale pin 二分**（沿 Cycle 2 / I2 Phase 1 先例）：a) 违规消失 = 已真实解决 → 移除 pin + red-list 对应条目标记 verified + 注册表分类同步复核；**联动约束**：同时复核 JUnit 断言（若 JUnit 断言未同步 → 属行为漂移升级路径，plan 转 blocked，不允许"mjs 绿但 JUnit 红"的悬空状态）；b) pin key 行号漂移但违规仍在 → 重 pin 新行号 + 留痕（±3 行容差仅用于人工复核记录；mjs 层面 pin key 含行号，行号任何移动 = pin 失配 = 走重 pin）
- [x] 运行 11 类 JUnit 门禁 + 表完备性测试（`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'`），记录测试数 / 失败数；任何红项 → 同处置二分（执行前确认本地仓库已 install 上游依赖：`-pl` 不带 `-am` 是为避免 `-Dtest` 误作用于上游模块；若本地仓库缺依赖，回退 `./mvnw install -pl nop-stream -am -DskipTests -T 1C` 补装，I1/I5 先例）
- [x] 复核接线点注册表自洽性：7 个服务注入 API live 枚举一致、接线点行号无漂移、消费方枚举无遗漏（**重点核查**：`setStateBackend` / `setKeyedStateBackend` / `setOperatorStateBackend` 的处置一致性——I1 已裁定 `internal-creation`（后两者 main 零调用点，生产经 `stateBackend.createKeyedStateBackend()` 直建）；`CepOperator.java:82-92` 为 javadoc 契约注记（非接线点，不登记）——复核接线面 = `GraphModelCheckpointExecutor` :733-744（`setStateBackend` :742）+ `wireTaskCheckpointPipeline`（:687，重建路径，plan `2026-08-13-0132-2` 新增）是否全量登记）、test-only 豁免清单无 main 混入
- [x] 合并生成确定性 red list 初稿（写入 red-list.md：每条含 `文件:行` + 关联不变式 + 关联 finding/注册表条目 + 门禁/pin 来源）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] mjs `all` 退出码 0 且六命令输出记录在案；unpinned / stale 情况全部显式处置
- [x] 行为漂移处置路径已执行或确认无漂移（plan 转 blocked 升级路径存在且未触发）
- [x] 11 类 JUnit 门禁 + 表完备性测试运行记录在案（测试名 + 数量 + 0 failures；如有失败 → 同上二分处置）
- [x] 注册表自洽性复核表存在（7 服务注入 API + 接线点 live 行号 + 消费方枚举 + state backend 接线完备性专项）
- [x] 确定性 red list 初稿已写入 red-list.md
- [x] **无静默跳过**：任何门禁红项 / 复核不一致都显式进入 red list 或处置记录，无吞掉差异的路径
- [x] No owner-doc update required（纯审计运行，无行为契约变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 注册表一致性裁定与 pin 复核

Status: completed
Targets: `StreamTaskInvokable.java`（setupProcessingTimeServices :456-477 / wireTailToRecordWriter :420-428 / setupSnapshotCallbacks :430-438）；`GraphModelCheckpointExecutor.java`（:735-747 + wireTaskCheckpointPipeline 面）；`wiring-registry.json`；`mjs-pins.json`；`TestWiringExistenceInvariant`（nop-stream-core）

- Item Types: `Proof | Decision`

- [x] **接线点 live 复核**：注册表每条生产接线点（文件:行）与 live 代码逐一核对（注入时机分类 = 构造 / 装配布线 / checkpoint 装配 / 快照）；行号任何移动 → 更新注册表 + 留痕（±3 行容差仅用于人工复核记录，注册表行号任何移动 → 更新 + 留痕）
- [x] **pin key 匹配复核**（如有 pin）：`key` 与 `scan-wiring` 实际输出违规串精确匹配（`compareViolationsToPins` 协议）；`removalTrigger` / 关联裁定 / invariant 字段完整
- [x] **注册表 ↔ pin ↔ JUnit 断言三方一致**：注册表分类 ↔ pin 语义 ↔ `TestWiringExistenceInvariant` 断言一致；发现不一致 → 按注册表为准修正并留痕
- [x] **接线路径复核（决策输入）**：注入时机面复核——(a) PTS/TSM 构造函数注入（invoke 前）与算子 open/restore 顺序（plan 1 已钉：`TestStreamTaskInvokableProcessingTimeWiring` 非 checkpoint 路径 + `CepOperator.open` 守卫）；(b) state backend 供给时序（`GraphModelCheckpointExecutor` 装配 vs `SupervisionLoop.rebuildTask` 重建路径重接——plan `2026-08-13-0132-2` 已修）；(c) `setOutput` 布线面（wireOperators / wireTailToRecordWriter / fan-out 多 writer 关闭面——plan `2026-08-13-0132-3` 已修）；确认生产接线判定依据仍成立，供 I3 参考
- [x] 裁定结论回写 red-list.md（每条：维持 / 移除 / 更新 + 依据）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 每条接线点存在复核记录（live 行为 + 行号 + 裁定结论）
- [x] 注册表 ↔ pin ↔ JUnit 断言三方一致性结论在案（一致或有差异修正记录）
- [x] 接线路径 live 复核记录在案（`文件:行` 可复核，含 state backend 重建路径专项）
- [x] 裁定结论已回写 red-list.md（状态明确 + 依据）
- [x] **无静默跳过**：每项裁定的理由显式写明
- [x] No owner-doc update required（复核不改变行为）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 聚焦对抗探查（门禁盲区 + P2 backlog 触发评估）

Status: completed
Targets: nop-stream wiring 族 `src/main`（服务注入 API 面 + 消费方 + 接线点）；`wiring-registry.json` 目标集；探查报告新文件 `ai-dev/audits/nop-stream-invariants/cycle3-I2-probing-report.md`（**新建文件，待产出，前向引用**——I1 先例同款前向引用标注（`check-doc-links` 对其报 BROKEN_LINK 属预期））

- Item Types: `Proof | Decision`

- [x] 按 `open-ended-adversarial-review-prompt.md` 对 wiring 族做**聚焦**对抗探查（范围 = 注册表目标集 + 盲区清单，不做全仓漫游），盲区清单：
  - a) **同族"仅测试注入"实例复探**（plan `2026-08-13-0132-1` Non-Blocking Follow-up 登记，触发条件 = 复探时评估）：checkpoint 服务 / watermark 服务 / 其他运行时服务是否存在 P0-01 同形态（main 零接线、测试全 mock 注入规避）实例——**逐服务 grep `set*Service` / 注入点调用方归属（main vs test）**；`TimestampsAndWatermarksOperator` 静默守卫形态（:82-84）复核——plan 1 裁定「接线后自然失效，语义不破坏」是否成立（live 复核接线后 PTS 非 null 路径 + 守卫分支可达性）
  - b) **注入/恢复时序组合面**（open-audit 总评第 3 点：P0-02 组合爆炸实例教训）：构造函数注入（PTS/TSM）vs `open()` vs `restoreState()` vs `rebuildTask` 重接顺序组合——`CepOperator.open` 守卫（:373 面）、`WindowOperator.open` registerTimerService（:471 面）、state backend 重建供给（`wireTaskCheckpointPipeline`）三者组合下是否存在新的 null 面 / 重复注入 / 顺序依赖破坏
  - c) **恢复路径行为盲区**（open-audit 总评第 2 点：门禁覆盖传递与注册，不覆盖恢复行为）：descriptor 路径 snapshot→restore 往返中服务注入面是否保持（timer 服务注册表恢复 / state backend 恢复后 getter 面）；与 P2-01/P2-02（checkpointSuccessMap 无界增长 / onCompletePersistFailure 不 complete future）触发条件评估联动
  - d) **窗口/CEP 清理语义盲区**（P2 批次触发评估）：P2-04~P2-07（triggerAccumulators 裁剪 / evictor descriptor 路径 / pane 键错位 / purge 缺 clear）与 P2-08（CepOperator STEP-5 超时基准）——评估触发条件（类别清扫相邻面）是否成立，成立则记录为复探确认项（附 live 证据），不裁决严重度。**行号过期警示**：open-audit 引用行号已过期（实测 triggerAccumulators ~:2044、timestamps 早退 ~:1337、pane computePaneInfo ~:965、STEP-5 块 ~:563/:573 vs open-audit 引用 :1944-1957/:1230-1233/:917-943/:540-554）——探查以 live 重定位为准，不追旧行号
  - e) **P2 backlog 触发条件评估**：open-audit P2 批次逐条对照触发条件（abort 高频场景复探 / 类别清扫 / 复探）+ **multi-audit P2 批次候选预枚举**（本探查面可合理触发的候选 = multi P2-11（两 beans.xml 同一 bean id streamMessageService，`stream-control-rpc.beans.xml:34-35` / `stream-data-plane.beans.xml:68-69`，与 beans 模板面相邻）、multi P2-01（WindowOperator keySerializer/windowSerializer 死字段 + dummy serializer createInstance null）、multi P2-03（RocksDB Options 未关闭）、multi P2-05（GraphModelCheckpointExecutor 上帝类）——其余 multi-audit 条目标注"本探查面不触发 + 一行依据"（如纯文档/测试治理类 P2-18~21）），**未触发标准 = 每条一行证据**（如"该文件/面本探查未接触"），已触发条目记录 live 复核证据，未触发条目维持原状（不升格不降级，供 I3）
- [x] 非族候选评估（每条裁定：升格为新不变式族候选 / 不升格 + 理由）——门禁未表达的 wiring 相关残余（如 `setCurrentKey`/`setKeyContextElement` 非服务注入面、`HeapInternalTimerService` 计时器真实触发机制 vs 记账结构对齐（plan 3 Non-Blocking））
- [x] 每条探查发现标注族归属：已知族（#7 wiring 族兄弟实例，标注注册表条目 / finding-ID 或"新发现"）/ 新族（给出不变式陈述候选 + 触发证据 `文件:行`）
- [x] 探查报告写入 `ai-dev/audits/nop-stream-invariants/cycle3-I2-probing-report.md`（新建文件，待产出；同款格式：范围声明 / 发现含位置场景影响族标注 / 盲区处置 / 非族评估 / 盲区自评）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 盲区 a–e 每条有探查结论（live 证据 + 族标注），无空白盲区
- [x] 同族"仅测试注入"复探完成（逐服务结论：已接线 / 仅测试注入（red list 项）/ 不存在）
- [x] P2 backlog 触发条件评估表在案（逐条：触发 / 未触发 + 依据）
- [x] 每条发现含触发证据 `文件:行` + 族标注（已知族 / 新族 / 不升格 + 理由）
- [x] `cycle3-I2-probing-report.md` 新建落档
- [x] **无静默跳过**：探查盲区无"没看"项，全部显式结论
- [x] No owner-doc update required（探查为过程产出）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - red list 权威化移交 I3

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/red-list.md`（Cycle 3 / I2 权威版）

- Item Types: `Proof`

- [x] 合并门禁结果（Phase 1）+ 注册表/pin 裁定（Phase 2）+ 探查发现（Phase 3）为权威版 `red-list.md`（Cycle 3 / I2 权威版结构，Cycle 1/2 历史版保留为附录；每条含 `文件:行` + 关联不变式 + 关联 finding/注册表条目 + 门禁/pin 来源 + 裁决输入（供 I3 直接裁决））
- [x] **Anti-Hollow 运行时证据（Rule #22，接线族审计专用）**：复跑生产接线 E2E——`./mvnw test -pl nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='TestProcessingTimeWindowProductionE2E,TestCepProductionExecutionE2E,TestSupervisionLoopCheckpointReconnectE2E'`（runtime 3 用例 + cep 4 用例 + runtime 重连 1 用例，执行时以 live 计数为准）——记录 surefire 输出（测试数 / 0 failures），作为"生产接线链路入口→出口运行时连通"的实跑证据（静态追踪不能替代；审计发现若声称 wiring 连通/断裂，必须有运行证据支撑）。**失败处置**：任一 E2E 红 → 走 Phase 1 行为漂移升级路径（超出 I2 处置权，plan 转 blocked 移交升级）+ red list 对应条目如实修订，不允许静默重跑或标注跳过
- [x] 零悬挂复核：每条例目含 I3 可直接裁决的完整输入（严重度参考 + 影响面 + 修复方向候选），无"信息不足"条目；如有 → 本 plan 不标 completed，补齐后再移交
- [x] 门禁统计回写 `cycle3-I1-input.md` 或执行时裁定落点（不新建统计文件，Cycle 3 统计唯一落点原则）；`cycle3-I2-probing-report.md` 与 red-list.md 交叉引用一致
- [x] roadmap Cycle 3 / I2 行状态流转 `todo`→`planned`（激活时）→`done`（closure audit 通过后，不得提前），行文本追加本 plan 引用（`（plan 2026-08-13-0805-2）`，Cycle 2 行注记惯例）
- [x] **Rule #25 说明**：No new test required（纯审计运行，零代码变更；门禁与 E2E 复跑均为既有测试）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] red-list.md 权威版存在（Cycle 3 / I2 结构 + 历史版附录保留）且每条含 I3 裁决输入
- [x] 零悬挂复核表在案（无 NEEDS_SUPPLEMENT 条目）
- [x] 门禁统计 / 探查报告 / red list 三方数据一致
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No owner-doc update required（纯审计运行）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] I1（`2026-08-13-0805-1-...`）已完成（硬前置）；门禁运行范围与 I1 注册表一致
- [x] 确定性 red list 权威化完成（门禁 + 裁定 + 探查三源合并，零悬挂）
- [x] 同族"仅测试注入"复探完成（checkpoint/watermark 服务逐条结论）
- [x] P2 backlog 触发条件评估表在案（触发 / 未触发 + 依据，无静默跳过）
- [x] 每条发现标注族归属（已知族 #7 / 新族候选 / 不升格 + 理由）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（所有发现均显式进入 red list / 探查报告 / 触发评估表）
- [x] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证（a）门禁与探查发现真实对照 live 代码（非仅文件存在），（b）red list 每条可回溯到 live 证据（`文件:行` + 测试/扫描输出），（c）无空壳审计结论（每条裁定含实质依据），（d）**生产接线 E2E 复跑实跑证据在案**（`TestProcessingTimeWindowProductionE2E` / `TestCepProductionExecutionE2E` / `TestSupervisionLoopCheckpointReconnectE2E` surefire 输出记录）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs all` 退出码 0（审计运行不破坏门禁基线）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` findings 集与既有基线一致（14 项既有债，零新增——沿 Cycle 2 / I1 钉定判据 `34aed42c1`）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` 通过（0 failure）

## Deferred But Adjudicated

### P2 backlog 条目未触发者

- Classification: `watch-only residual`（已裁定处置附依据即合规，roadmap Follow-up Backlog 机制）
- Why Not Blocking Closure: 本 plan 已按触发条件逐条评估（Phase 3-e）；未触发条目维持 backlog 原状，触发条目升级为复探确认项（入 red list / 探查报告）供 I3 裁决——评估动作即处置，不阻塞本 plan 关闭。
- Successor Required: `yes`
- Successor Path: Cycle 3 / I3（裁决触发条目）→ Cycle 3 / I4（P0/P1 修复）

### `TimestampsAndWatermarksOperator` 静默守卫形态

- Classification: `watch-only residual`（plan `2026-08-13-0132-1` 裁定延续）
- Why Not Blocking Closure: plan 1 已裁定「接线后自然失效，语义不破坏」；本 plan Phase 3-a 复核接线后守卫分支可达性——若复核发现守卫形态在接线后仍活跃且语义破坏，则升格 red list 供 I3 裁决；复核动作即处置。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 新族（如有）门禁沉淀 = Cycle 4 / I1 派生候选（I6 收口按 Loop Rule 评估）。
- ArchUnit 架构约束门禁（`optimization candidate`，触发时另立 Decision，先例延续）。

## Closure

Status Note: 门禁运行（mjs all exit 0 + JUnit 11 类 112 tests + E2E 8/8）+ 注册表裁定（9/9 维持 + 零 pin 三方一致）+ 聚焦探查（盲区 a-e + P2 触发评估 10+23 条）完成，red list 权威化（C3-RL-1..10）零悬挂移交 I3。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_0072a9085ffez8j8ZIp40n1KPa`），verdict **APPROVE**
- Evidence: 四 Phase Exit Criteria 逐条对照 live 工件 PASS（P1 mjs all exit 0 实跑 + JUnit 112/112 surefire 实跑；P2 9 接线点 spot-check StreamTaskInvokable:462/:463 + GraphModelCheckpointExecutor:742/:687/:660 + pins 空表 + TestWiringExistenceInvariant 10/10；P3 probing-report 盲区 a-e 全结论 + C3-PR-1..8 含 live 证据；P4 red-list C3-RL-1..10 每条 file:line + finding + 裁决输入，无 NEEDS_SUPPLEMENT，Cycle 2/1 附录保留）；Anti-Hollow（a）16 处 file:line 抽查全部命中 live 源码（checkpointSuccessMap:1117 / triggerAccumulators:2043-2071 / STEP-5:573 / getNodeLease:99-108 / beans:34+38+68 / RocksDB:206-210 / TestWindowOperatorCorrectness:551+607 等），（b）§0 门禁结果与实跑输出一致，（c）每条裁定含实质依据，（d）E2E 实跑复验 3/3+4/4+1/1 BUILD SUCCESS；checklist 工具 exit 0；无遗留 `[ ]` 项（Closure Gates 应用后全勾选）。2 项 minor 差异（消费方行数 10→11 计数陈旧 + mjs 成功静默输出措辞）——前者已修正，后者行为一致非问题。
- Checklist tools: `check-plan-checklist.mjs --strict` exit 0；`check-doc-links.mjs --strict` exit 0；`check-nop-stream-invariants.mjs all` exit 0；`scan-hollow-implementations --severity high` = 14 项既有债零新增（`34aed42c1` 判据）；JUnit 门禁 11 类 112 tests 0 failures。

Follow-up:

- no remaining plan-owned work（四 Phase 全勾选 + Closure Gates 全通过）；非阻塞项 = 触发确认项（C3-RL-4..9）移交 I3 裁决、multi-audit P0-01 已解决记录供 backlog 修订、plan `2026-08-13-0132-1` Non-Blocking 登记项（同族复探）可闭合
