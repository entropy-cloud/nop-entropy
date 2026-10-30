# 2512 CRA Phase 8.18 — R2-09 1.3 编排薄层批（4 项 P1：UDF 注册表 / 游标 REPLACE / RuntimeContext 共享 / cancelled 残留）

> Plan Status: active
> Draft Review: R1 agent_c8da5df5 @ 2026-10-30（第 1 轮 FAIL[3 Major/0 Blocker/2 Minor]：复核结论挂错文件→改挂 C3-async-chains.md/C6-resource-lifecycle.md；勘误 1 live 值失真三处[FunctionUtils :51-56 反向纠错、DebeziumCdcSourceFunction 复位块 :247-252、run() :111]→改回；裁定③形态 B 缺可实施性约束→追加 RichFunction 契约不改约束句；Minor×2 闭合——第 2 轮复审 5/5 CLOSED PASS；Design Review: R2 agent_5f443adb @ 2026-10-30（第 1 轮 FAIL[2 Major/4 Minor]：裁定② 截断行为声明与 live 相反[:153-155 静默早退]→如实改写；裁定③ ShareRef 机制落点缺口→钉 core util 包落点[2511 判据单一来源平移，cep/debezium 私有实现零触碰]——第 2 轮 PASS，余 2 Minor[模块归属 fraud-example→nop-stream-flow、钉编号 1–8→1–10]由协调方修入）
> Last Reviewed: 2026-10-30
> Source: roadmap 工作项 8.18（R2-09，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L160，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-09 行（L83 全文：finding ID 清单 4 项[1.3-C1-001；C3-008；C3-009；C6-007] / 模块与验证命令 nop-stream `./mvnw test -pl nop-stream -am` / Owner Doc=`docs-for-ai/03-modules/nop-stream.md` + `docs-for-ai/03-modules/nop-stream-sql.md` / PA=无 / 依赖「无前置（可并行）；C3-009 根因载体 core 跨单元（与 1.3-C3-008 同批）」/ exit criteria 增补：**UDF 注册表并发容器化+运行期换绑契约（含 sql.md §3a docs bug 修正）；REPLACE 清零改增量恢复；RuntimeContext per-subtask 封闭；cancelled volatile 复位**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 1.3 审计目录 `ai-dev/audits/2026-10/2026-10-14-1440-deep-audit-nop-stream-orchestration-thin-concurrency/`（C1-shared-mutable-state.md C1-001 :23-69 + 复核结论 :398/:409；C3-round2.md C3-008 :12-69 / C3-009 :72-131 + 复核结论 C3-async-chains.md :412（另 :424/:430/:437）；C6-round3.md C6-007 :11-84 + 保留裁定等 C6-resource-lifecycle.md :322/:328/:351；C10-test-protection.md 映射 :83/:318/:355 + merge-adjudication.md 族表 MA-01 :42 / 独立案 C3-008 :58 / C3-009 :59 / C6-007 :62 / P1 四族 :107-116 / C10 映射表 :142-153 / 跨单元关联 :156-165 / 修复批次建议 :170-190），逐条回读路径见 Current Baseline；执行基线 @ `96e7e94cce`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.17 已 done，plan 2495–2511 均已完成或收口；本 Baseline 全部行号为该基线 live 回读值，2026-10-30 亲读，执行前如再漂移以 live 为准并登记）
> Related: plan 2511（R2-08，completed——同上游 nop-stream 门禁与三联裁定时序先例：本批验证命令同聚合 `./mvnw test -pl nop-stream -am`，其 debezium testcontainers fork timeout 三联裁定模式与 `TestCepOperatorSubtaskCopy` round-trip 深拷贝范式可引用不重开体）；plan 2510（R2-07，completed——**WI 1.1 core/runtime 同域先例**：其裁定与测试锚零回退引用；C3-009 根因载体 ProcessOperator/AbstractUdfStreamOperator 为该轮覆盖空洞（merge-adjudication §4.6 #1：WI 1.1 全目录零 ProcessOperator/RuntimeContext 面登记），本批闭合该空洞、非重做）；plan 2502（R1-07，completed——R01 SSOT：**本批无保留策略面，零 R01 传导**，若执行期裁定涉保留需引用时只走成员传导不改参数）；plan 2509（R2-06，completed——R03 锁内重活外移范式：本批 C1-001 的容器化为读写纪律成员投影，无锁内 I/O 面，零范式裁决重开）；**下游**：R4-05（roadmap 8.45，G11 十项 1.3 Replay/流语义与注册表束：MA-02/03/04/05 + C1-002/C1-003/C1-005/C3-005/C6-003/C6-005）软依赖 8.18（L187）——本批为同单元 P1 先行批，批末须回写就绪注记（含 C1-003 P2 本体与 C6-007 同文件联调边界、C6-005/MA-03 与 C6-007 同文件族前提）；**G91 无本体 C10 条目**：1.3-C10-007/008 归治理批 R-G（cra-index L475），其中 **C10-007 本批随 C3-008/C6-007 P1 加固搭车承接**（batch-proposal §五 + merge-adjudication :152 建议随批），C10-008 仍归 R-G（见 Baseline 勘误 4）；**并行协同**：无同门禁在飞批（8.19 R2-10 起未立项），本批可独立开跑
> Protected Area 授权：批次 PA=无（batch-proposal §二.2 R2-09 行授权列）。本批四成员站点分别位于 `nop-stream/nop-stream-sql`（C1-001）、`nop-stream/nop-stream-fraud-example`（C3-008/C6-007 本体）、`nop-stream/nop-stream-core`（C3-009 根因载体）——**均非 Protected Area**（AGENTS.md Protected Areas 表所列 `nop-core/`/`nop-xlang/`/`nop-api-core`/`nop-auth`/orm 模型/生成管线均不涉及；`nop-stream-core` 是 nop-stream 聚合内的业务模块，非框架核心引擎行）。live 回读勘误申报：本批不做 `_` 前缀生成物改动、不触 `model/*.orm.xml`；C3-009 修复落 `nop-stream-core` operators/util 非生成物 main 源码（WI 1.1 覆盖空洞闭合，plan 2510 已收口不回写）；**若 Phase 1 裁定把 per-subtask RuntimeContext 契约设施上收 `nop-api-core` 或 `nop-kernel` 平台层**（当前预判不需要——StreamingRuntimeContext/FunctionUtils 均在 nop-stream-core 内自洽），则照 2507–2511 先例在该裁定项内批内闭环申报 plan-first 后方可实施。本批不触碰 R4-05 的十项批外成员本体。

## Purpose

关闭 R2-09 批的 4 项已确认发现（P1×4，全部 open，cra-index 台账行 L155/L156/L157/L158）。核心结果面有四：①**UDF 注册表并发容器化 + 运行期换绑契约**——`StreamScalarFunctionResolver.functions` 非线程安全 LinkedHashMap 的逐记录热路径读 × 无守卫公开 register() 写竞态消除（C1-001，MA-01 主条目←C3-002/C2-001，含 `nop-stream-sql.md` §3a「编译期一次解析（非逐记录查找）」docs bug 同 diff 修正）。②**REPLACE 清零改增量恢复**——`DirectoryFileSourceFunction.initializeState` 的 clear+putAll REPLACE 语义在 region 重启 Phase-3 提交-继续编排下清零兄弟子任务已推进游标、后续 checkpoint 携带滞后源位点致恢复重放已反映窗口（C3-008，复核修正机制）。③**RuntimeContext per-subtask 封闭**——共享 UDF × 各子任务 open() 覆写共享 RuntimeContext → parallelism ≥2 键控状态系统性收敛单一 backend（C3-009，根因载体 nop-stream-core ProcessOperator/AbstractUdfStreamOperator 跨单元）。④**cancelled volatile 复位**——跨 run 永不复位 → 区域重启共享实例零事件静默 EOS 假成功（C6-007）；外加⑤ **1.3-C10-007 规模/边界包络探针随 C3-008/C6-007 修复搭车落地**（G91 无本体条目的批内处置，见 Baseline 勘误 4）。批末 roadmap 8.18 → done，cra-index 四行台账收敛，R4-05 软依赖解除。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-09 = 4 项成员（P1×4——merge-adjudication §4.2 P1 四族中除 MA-01 外的 C3-008/C3-009/C6-007 为独立案，MA-01 主条目即 C1-001）；cra-index 台账四行独立（L155 C1-001、L156 C3-008、L157 C3-009、L158 C6-007，均 open、批次列均=R2-09）；roadmap 8.18 行 Status=todo。前置：无前置（可并行），plan 2495–2511 前序批均收口——基线门禁已满足。
- **吸收成员（随主条目同 diff，不单列工单）**：C1-001←C3-002（namesList/registeredNames 编译期迭代 × put 的 CME 变体，证据与 C1-001 逐字同源）/C2-001（同一文件同一行段 :46-51/:88-97，抽查复核原提议随 C1-001 P1 归并）——MA-01 三面单修复（merge-adjudication §3 #3 追加归并）。cra-index 回填时以主 ID 收敛、吸收成员括注，不整行多 ID 重复立项。
- **C10 验收前置入批集（4 条本体收口门 + 1 条反向约束 + 1 条搭车，随本体同 diff 落地[E2]）**：C10-001（MA-01/C1-001 收口门——resolver 并发压测 trip-wire + namesList 无 CME 子用例 + 契约锚与修复选定语义二选一钉住；`TestStreamScalarFunctionResolver` 现有 9 用例全为单线程功能面，并发面零覆盖）；C10-002（C3-008 收口门——同实例 restore-vs-run 交错测试，多文件「已完成文件」变体为稳健红形态；`TestDirectoryFileSourceFunction` 现有 8 用例全为新鲜实例 resume 面，交错零覆盖）；C10-003（C3-009 收口门——LOCAL env P=2 有状态 keyed UDF 隔离测试，**core 根因修复的回归锚，归属登记须注明挂 core 工单**[merge-adjudication §4.6 #1]）；C10-004（C6-007 收口门——cancel 后同实例重跑断言全量重发 + 重跑后快照游标单调，**须「run 中 cancel」变体方在缺陷态红**）；反向约束 C10-006（C1-003 trip-wire，全矩阵唯一——`TestDirectoryFileSourceFunction#runEnteredGuardPreventsDoubleEmission` :213 既有钉：**本批 C6-007（及 C3-008）修复不得使并行首入落败者继续读取**，该钉零回退）；搭车 C10-007（G91 解耦登记——MultiFile ≥3 文件跨文件顺序 + BufferBoundary ≥8KB 块边界 resume 不丢不重 + 空目录 EOS 契约探针，与 C3-008 已完成文件游标修正机制面和 C6-007 假成功形状直接相关）。勘误：1.3-C10-008（emitter 线程有界终止断言 + double-subscribe 契约用例）关联本体 C3-007（P3）/MA-04（P2）/C6-005（P2）均在本批外（R4-05），**维持归 R-G 不搭车**（merge-adjudication :153 的「随对应族修复落地」条件在本批不成立）。

### 4 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `96e7e94cce` 2026-10-30 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 1.3-C1-001（P1；MA-01 主，←C3-002/C2-001；含 sql.md §3a docs bug） | `StreamScalarFunctionResolver.java`（nop-stream-sql `eval`）+ 发射点 `SqlScalarXplPrinter.java`（`compile`）+ `docs-for-ai/03-modules/nop-stream-sql.md` §3a | `defaultResolver` static volatile :31；`functions` LinkedHashMap :46；`register()` 公开无守卫 `functions.put` :49-51；静态 `call()` 逐记录 `r.functions.get(fnName)` :88-97（get :93）；编译期迭代面 `registeredNames()` :59-61 / `namesList()` :69-71 对 keySet 拷贝前读活 map；发射点 SqlScalarXplPrinter :186-191（SqlRegularFunction UDF 分支生成 `StreamScalarFunctionResolver.call(...)` 织入逐记录 xpl 体，顶层 `<sql>` 与 `<sqlTransform>` 两声明面共用） | UDF 注册表为非线程安全 LinkedHashMap，经静态 `call()` 在逐记录热路径无同步读 × 公开 `register()` 任意线程写（put 触发 rehash 时并发 get 可撕裂/返回 null/CME），且 `registerAsDefault()` 运行期静默换绑已编译作业共用的查表实例；volatile 只保证引用可见不约束 map 内部状态 | 裁定①（batch exit criteria「并发容器化+运行期换绑契约」）：`functions` 改 `ConcurrentHashMap`（预设默认分支——零锁热路径读，`registeredNames`/`namesList` 迭代面天然 CME 免疫[CHM 弱一致迭代器]；备选 `Collections.synchronizedMap`+读复制须论证 CHM 语义不足后方可采）；**运行期换绑契约一次定夺**：`register()`/`registerAsDefault()` 在 `getDefault()!=null && 非启动窗` 时至少 WARN 日志 + javadoc 文档化生命周期（多作业长驻进程下运行期换绑影响面=全部后续逐记录查表，是否升级 typed fail-fast 由 Phase 1 按公共 SPI 面声明裁定——审计 C1 复核 :409 勘误：生产注册为应用侧经自身 app-beans 调 `registerAsDefault()`，nop-stream-sql 模块自身无注册 app-beans）；**sql.md §3a docs bug 同 diff 修正**（:47「编译期一次解析（非逐记录查找）」——该表述仅对 `StreamSqlExprCompiler.compileFunction` Java 捕获闭包路径成立，声明面运行路径为逐记录静态查表，改为如实双路径表述）；C10-001 收口门随同 diff |
| 2 | 1.3-C3-008（P1；独立案，复核机制修正） | `DirectoryFileSourceFunction.java`（nop-stream-fraud-example `scenario`；编排面载体 nop-stream-runtime `SupervisionLoop` 本批只读不改） | javadoc 共享契约 :41-44（"runtime shares one source-function instance…deterministic under parallelism rescale"）；共享 `cursors` TreeMap :68；`initializeState` :261-286——非空 restore 走 `synchronized(cursors){ cursors.clear(); putAllCursors(restored) }` :281-284（REPLACE），非空守卫 :275-280 只防 restore-vs-restore；`advanceCursor` :224-227 / `currentCursor` :218-222 / `snapshotState` :247-257；REPLAYABLE 声明 :236-243；编排窗口 = runtime `SupervisionLoop.restartRegion` Phase-3 rebuild→restore→`executor.submitTask` 异步提交→循环继续（审计锚 :546-574，本批 live 复核执行时重读） | region 重启 Phase-3 流水线下兄弟子任务 initializeState 的 REPLACE（clear→putAll）清零已推进/已完成文件游标→此后每个 checkpoint 携带落后于同 epoch 下游进度的源位点→恢复重放已反映窗口（键控聚合重复计入，REPLAYABLE 声明违约；复核修正机制：非发现原陈述的同 run 重发， wiping 后游标余下运行期持续滞后） | 裁定②（batch exit criteria「REPLACE 清零改增量恢复」）：预设默认分支 = `initializeState` 弃对共享活 map 的 REPLACE——改**增量合并恢复**（`putAllCursors` 保留兄弟已推进的较新游标：按 per-file 位点单调性取 max 合并而非 clear 后覆盖；`restored.isEmpty()` 守卫维持），或等价「runEntered 为 false（实例静默）才允许 clear+putAll、否则拒绝/告警 typed」分支——两者一次定夺，Phase 1 以「不清零已推进游标 + 恢复语义仍确定（per-file 位点单调、checkpoint 位点不落后于已推进值）」为判据；**编排侧修复候选（runtime SupervisionLoop Phase-3 统一 submit）为移交项不入本批**（merge-adjudication §4.6 #2，属 nop-stream-runtime 单元——Non-Goal 声明）；CDC 兄弟面（`NopStreamOffsetBackingStore.setOffsets` REPLACE）已由 plan 2511 C8-001 位点协议收敛，本批零触碰；C10-002 收口门随同 diff |
| 3 | 1.3-C3-009（P1；独立案，根因载体 core 跨单元） | 根因载体 `ProcessOperator.java` + `AbstractUdfStreamOperator.java` + nop-stream-core util 包 FunctionUtils.java（operators/util）；失效面示例 `UserHistoryEnricher.java`（nop-stream-fraud-example） | `ProcessOperator.copyForSubtask()` :42-44 同一 userFunction 引用透传每个子任务；`AbstractUdfStreamOperator.open()` :74-77 `FunctionUtils.setFunctionRuntimeContext(userFunction, new StreamingRuntimeContext())` :76 无同步覆写共享实例；`ProcessOperator.open()` :92-103 对共享 context 装入本子任务 keyedStateBackend（`src.setKeyedStateStore(keyedStateBackend)` :98 + `setTimerService` :101）；`FunctionUtils.setFunctionRuntimeContext` :51-56 普通字段写；消费点 `UserHistoryEnricher` :47-55 懒初始化 `getRuntimeContext().getKeyedStateStore()` :51 / :62 | 共享 UDF（copyForSubtask 引用透传）× 各子任务 open() 互不同步覆写共享 RuntimeContext → parallelism ≥2 时系统性收敛 last-writer-wins 单一 keyed backend——跨子任务键控状态静默污染（顺序 open 亦然，非窄窗口竞态；shipped 场景 parallelism="1" 不触发） | 裁定③（batch exit criteria「RuntimeContext per-subtask 封闭」；**PA 申报注记：落点 nop-stream-core 非 Protected Area**[见头部 Protected Area 行]）：预设默认分支 = **per-subtask context 封闭**——共享 UDF 的 RuntimeContext/keyedStateStore 装配改为每子任务独立 context 实例（方向：`copyForSubtask` 对含状态 RichFunction UDF 做深拷贝私有化[plan 2511 裁定② round-trip 同族手段]或 per-subtask RuntimeContext holder 挂本实例算子——两种形态 Phase 1 按序列化约束/无状态 UDF 零回归取舍一次定夺；**形态 B（per-subtask holder）约束：须先给出不改动 `RichFunction` 公共契约（`getRuntimeContext/setRuntimeContext` 为 UDF 实例自身字段，`RichFunction.java` :92/:112；消费点 `UserHistoryEnricher` :51/:62 经 this 读取）的 per-subtask 绑定机制；给出不出即默认收敛为深拷贝私有化单分支**）；`AbstractUdfStreamOperator.open`/`ProcessOperator.open` 的共享实例字段写随之封闭；**失效面外溢范围（所有含状态 RichFunction UDF）由 Phase 1 以 C10-003 隔离测试矩阵佐证**（merge-adjudication §4.6 #1 建议的子项复核义务）；C10-003 收口门随同 diff（**回归锚归属登记注明 core 工单**）；`lastCityState` 懒初始化竞争面（P3 C1-006 独立案，R4-05）被本裁定主导不单独立案 |
| 4 | 1.3-C6-007（P1；独立案；与 C1-003[P2，R4-05]同文件不同机制维持两立） | `DirectoryFileSourceFunction.java`（nop-stream-fraud-example；对照生产基类复位契约 `DebeziumCdcSourceFunction.java` nop-stream-connector-debezium） | `cancelled` volatile 非 transient :82；唯一写点 `cancel()` :230-233（无任何复位写点）；`run()` 方法签名 :111、体 :112-130——`runEntered` CAS :114、循环顶/内层 `if (cancelled) return` 读点 :119/:124（另有 :169/:173）、finally 只复位 `runEntered` :128；teardown 必经 cancel：`StreamSourceOperator.close` :392-400 `sourceFunction.cancel()` :396；共享实例链 `StreamSourceOperator.copyForSubtask` :227-232 / `copyForSubtask(int)` :240-247（非 `SubtaskShardedSourceFunction` 共享同一实例）；对照复位契约 `DebeziumCdcSourceFunction` :247-252（running/draining/pendingError 复位三件套 + latch 重初始化） | cancel 置位的 volatile `cancelled` 跨 run 永不复位——区域重启按声明契约复用同一实例时，重启 run 在循环顶静默零事件退出，按 EOS 成功收尾（半个成功：REPLAYABLE 恢复声明违约 + maxRestartsPerRegion 重试预算零消耗的无声数据截断；纯顺序陈旧即可触发，无需交叠窗口） | 裁定④（batch exit criteria「cancelled volatile 复位」）：预设默认分支 = 对齐基类复位契约——`run()` 在 `runEntered` CAS 成功后复位 `this.cancelled = false`（写 volatile 先于循环读，本方法此后均由本任务线程读，无可见性缺口）；**联调约束（merge-adjudication :85/:152）**：修复须与同文件的 C1-003（P2 本体在 R4-05，runEntered CAS 守卫面）联调验证且**受 C10-006 trip-wire 反向约束**——`runEnteredGuardPreventsDoubleEmission`（`TestDirectoryFileSourceFunction` :213 既有钉）保持绿（修复不得使并行首入落败者继续读取）；两条重建路径（LOCAL `SupervisionLoop` 与 embedded-distributed deployTask）同时验证后收口（C6 复核 :351 义务）；C10-004 收口门（含 run 中 cancel 变体）随同 diff；**C10-007 搭车**（MultiFile/BufferBoundary/空目录 EOS 探针，裁定②④修复机制面共享——见勘误 4） |

### 平台设施与测试资产盘点（live 实读 @ `96e7e94cce`）

- **先例范式可引用不重开**：plan 2511 裁定② round-trip 深拷贝范式（含 xlang 编译叶子 ShareRef 豁免先例——若裁定③选 UDF 深拷贝形态且涉 xpl 包装函数叶子，可直接引用该豁免判据不重开裁定）；plan 2511 三联裁定模式（debezium testcontainers fork timeout + nop-kernel 计时 flake 预登记，见勘误 5）；merge-adjudication §5 批次 C/D 建议的「先落 C10 缺保护测试（缺陷态红）→ 修复（转绿）」次序纪律。
- **既有测试资产（回归门禁与改写/补测对象）**：`TestStreamScalarFunctionResolver`（9 用例，单线程功能面——C10-001 补测本体）、`TestUdfE2E`（UDF 声明面端到端钉，零回退）、`TestDirectoryFileSourceFunction`（8 用例——C10-002/C10-004 补测本体 + `runEnteredGuardPreventsDoubleEmission` :213 C10-006 反向钉零回退）、`TestProcessOperator`（6 用例——裁定③零回归门）、`TestOperatorSubtaskIsolation`（core 子任务隔离既有钉，裁定③零回退）、`TestS1CdcPipelineE2E` / `TestS2RecoveryAndRescaleE2E` / `TestS2FileAggregationE2E` / `TestParallel2PcFileE2E`（S1/S2 场景链路钉——C6-007/C3-008 修复后行为面回归；`TestS2RecoveryAndRescaleE2E` 为恢复/重分伸缩 E2E，裁定②③④组合行为的最近端到端载体）、gated 多 JVM 套件（C10-008 关联面，本批不触碰）。
- **owner doc 现状**（2026-10-30 亲读）：
  - `docs-for-ai/03-modules/nop-stream-sql.md`（85 行）：§3a 标量 UDF（WI30）:45-47——**:47「编译期一次解析（非逐记录查找）」为 docs bug 载体**（与声明面运行路径逐记录静态查表不符，裁定①同 diff 修正）；:47 后段 SPI 同构与 AutoCloseable 生命周期表述（C1-001 换绑契约落地后补运行期语义一句）；§3a 示例段 :50 起。**本批 owner doc 主落点**。
  - `docs-for-ai/03-modules/nop-stream.md`（356 行）：模块路由表 :14-21（nop-stream-core/sql/fraud-example 行——C3-009 根因载体修复后如用户可见契约变化按需补注记）；「Source reader 恢复时序契约」节 :26-28（SourceReaderOperator 恢复契约——与本批 source 面相邻的既有契约节，表述形态先例）；指标面 :38-92、运维面 :129-167（本批零改动）；分层校验 :187-202（本批零改动）。**fraud-example 的 source 共享实例/跨 run 复用契约与 ProcessOperator per-subtask 契约当前无 owner doc 表述**——裁定②③④落地后按 E5 补节或登记（放置于 nop-stream.md 恢复/契约区或以 ≤3 行摘要链接 user-guide，落点 Phase 3 定）。
  - `docs-for-ai/03-modules/nop-stream-user-guide.md`（471 行，相邻面只读）：WI11 sideInput「构建期 get() 恰一次并冻结只读快照」与实例锚点 S1/S2 行（C1-001/C3-008 的契约锚所在，本批零强制改动；如裁定①②④使命名/语义表述漂移则按 E5 联动登记）。

### Baseline 勘误（输入不一致登记）

1. **行号核对（审计→live @ `96e7e94cce`，漂移均为引用粒度级、零实质漂移）**：C1-001 `defaultResolver` :31 ✓、`functions` :46 ✓、`register` :49-51（审计 :50）✓、`call` :88-97（审计 :88-97，get :93）✓、`registeredNames` :59-61 / `namesList` :69-71 ✓、SqlScalarXplPrinter UDF 分支 :186-191（审计 :186-198 为 case 全区间，发射两行 live :190-191）✓；C3-008 `initializeState` live :261-285（审计 L261-285，±1 尾粒度登记[归并入 :285 尾行 vs :286 空行]）✓、clear+putAll live :281-284（审计 :282-284）✓、`cursors` :68 ✓、javadoc :41-44 ✓、`advanceCursor` :224-227（审计 :224-228）✓、`snapshotState` :247-257（审计锚入 :248）✓；C6-007 `cancelled` :82 ✓、`cancel` :230-233 ✓、`run` live 方法签名 :111、体 :112-130（审计 :110-130 为区间粒度）✓、cancelled 读点 live :119/:124/:169/:173（审计 :119/:124/:169/:173）✓、finally :128（审计 :130 为区间尾）✓、`StreamSourceOperator.copyForSubtask` 体 live :226-231（±1 尾粒度登记[javadoc :222-226 与方法体分界]）✓、close/cancel live :392-400（`sourceFunction.cancel` :396，审计 :392-400）✓、对照基类 `DebeziumCdcSourceFunction` 复位块 live :247-252（:246 注释行；running=:250/draining=:251/pendingError=:252——**:225-237 为 createMessageSource javadoc，初审勘误反向纠错**，执行时以 live 重读为准）；C3-009 `ProcessOperator.copyForSubtask` :42-44 ✓、`open` keyed 装配 live :92-103（`setKeyedStateStore` :98，审计 L92-102/L97 一致）✓、`AbstractUdfStreamOperator.open` live :74-77（`setFunctionRuntimeContext` :76，审计 L72-74 为区间前移粒度）✓、`FunctionUtils.setFunctionRuntimeContext` live :51-56（**审计值本来正确，初审勘误反向纠错**）✓、`UserHistoryEnricher` :47-55/:51/:62 ✓；C10-006 反向钉 `runEnteredGuardPreventsDoubleEmission` live `TestDirectoryFileSourceFunction` :213 ✓。
2. **C3-008 机制勘误承继**：发现原陈述「同 run 内 currentCursor 重读→重发 [cp,5000)」经复核不成立（currentCursor 每 emitRemaining 仅读一次）；本批修复对象为复核重建的违约路径（wipe 后游标余下运行期持续滞后→checkpoint 位点滞后→恢复重放已反映窗口）——裁定②的验收断言以复核机制为准（多文件「已完成文件」变体），不以原陈述机制写钉（merge-adjudication :58、C3 复核 :412/:418）。
3. **C6-007 触发路径承继**：Phase 2 `waitForTerminal` 的先行关系恰保证 teardown cancel happens-before 新 run 读 cancelled——纯顺序陈旧触发，不需交叠窗口；C1-003 复核修正（交叠窗口具体化，P2→R4-05）反而强化本条实例共享前提；两立不改（C6 复核 :328）。
4. **G91 C10-007/008 处置勘误**：cra-index L475 将 1.3-C10-007/008 整体归 R-G（含「1.3-C10-007 建议随 C3-008/C6-007 P1 批加固」括注）；batch-proposal §五同口径。本批裁定：**C10-007 搭车入批**（其探针面[MultiFile 顺序/8KB 块边界/空目录 EOS]与裁定②已完成文件游标修正机制、裁定④假成功形状直接相关，随批落地成本最低且消除 R-G 侧 1.3 半边——cra-index 回填时 C10-007 行注记「随 plan 2512 落地、G91 行消号 1.3 半边」）；**C10-008 维持 R-G 不搭车**（关联本体 C3-007/MA-04/C6-005 均在 R4-05，本批修不动其 emitter/double-subscribe 面，随批落地会造出无本体红的空壳断言——违反 plan guide rule 24 精神）。
5. **环境项预登记（批前既有）**：`./mvnw test -pl nop-stream -am` 存在 nop-stream-connector-debezium 测试的 docker fork timeout 环境抖动（plan 2510/2511 两轮批均触发重跑，非产品缺陷）与 nop-kernel 计时 flake。批内验证失败时按**「stash 基线同红 / solo 绿」三联裁定**处理：①`git stash` 后在基线重跑确认同红（环境项）；②单独重跑该测试确认绿（非本批改动引入）；③恢复改动复跑全量绿——三步留痕登记后方可继续，不得静默重跑或跳过。

## Goals

- Phase 1 设计裁定文档一次裁决四项裁定（独立 draft review 通过），无临场发明空间：①C1-001 UDF 注册表容器化 + 运行期换绑契约（MA-01 三面含 sql.md §3a docs bug）；②C3-008 REPLACE 清零改增量恢复（复核机制口径）；③C3-009 RuntimeContext per-subtask 封闭（core 跨单元载体 + 失效面外溢矩阵）；④C6-007 cancelled 复位（基类契约对齐 + C10-006 反向约束联调）——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。
- 4 项成员全部修复并各带回归测试（rule 25）；C10 四条收口门随本体同 diff 落地（C10-001/002/003/004）+ C10-006 反向钉零回退 + C10-007 搭车探针落地。
- 关键工单先红后绿或变异/回退论证留痕（E2）：红① 注册表竞态红（并发 register×call 压窗 trip-wire + namesList CME 注入，修复前红）；红② restore-vs-run 交错红（Phase-3 时序模拟——已完成文件游标被 wipe 后 checkpoint 位点滞后断言红→修复后增量合并位点不回退）；红③ keyed 隔离红（LOCAL P=2 有状态 keyed UDF——修复前两子任务收敛同一 backend 红[白盒断言 keyedStateStore 实例同一性]→修复后各子任务 context/backend 独立）；红④ 假成功红（cancel 后同实例重跑——修复前零事件红→修复后全量重发 + run 中 cancel 变体红）。
- 联调纪律显式守约（本批两条）：**C6-007×C1-003 同文件联调**——裁定④修复与 C1-003 runEntered 守卫面共验、C10-006 trip-wire 保持绿（R4-05 就绪注记中声明联调结论供其引用）；**C3-009 core 回归锚归属**——C10-003 登记注明挂 core 工单（WI 1.1 覆盖空洞闭合），R4-05 的 MA-02/C6-005 等同文件族可引用本批裁定④的复位契约结论。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-stream-sql.md` §3a（docs bug 修正 + 换绑契约表述）+ `docs-for-ai/03-modules/nop-stream.md`（source 跨 run 复用/per-subtask 契约补节或登记，按 Phase 3 裁定）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
- 下游就绪义务：批末向 R4-05（8.45）回写软依赖就绪注记（含 C1-003 联调结论、C6-005/MA-03/MA-04 同文件族前提、C10-008 维持 R-G 的边界声明）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-05 成员**（G11 十项：1.3-MA-02/MA-03/MA-04/MA-05 + 1.3-C1-002/C1-003/C1-005/C3-005/C6-003/C6-005）及其 C10-005/C10-008 面——软依赖本批，本批零承接零顺手修；特别地 **C1-003**（runEntered CAS 非粘性，P2）为 C6-007 同文件相邻工单——本批裁定④只落 cancelled 复位并共验 C10-006 反向钉，C1-003 本体修复（attempt 身份/代际守卫）归 R4-05 不代做；**C6-003**（resolver 静态 default 只注册不清除/close 空壳）与裁定①同文件生命周期相邻——本批只落并发容器化+换绑契约 WARN，解绑/close 语义归 R4-05 不代做（C1 复核 :409 的生命周期文档化义务以 §3a 修正为限）。
- **不做编排侧修复**（nop-stream-runtime `SupervisionLoop.restartRegion` Phase-3「全部 rebuild+restore 完成后再统一 submit」候选）——merge-adjudication §4.6 #2 移交项，属 runtime 单元；本批 C3-008 修复落 fraud-example 源类侧，编排面零触碰（`SupervisionLoop` 只读引用）。
- **不做 1.3-C10-008**（emitter 终止断言 + double-subscribe 契约用例）——关联本体在 R4-05，维持归 R-G（勘误 4）。
- 不触碰 plan 2511 已修面（rocksdb/cep/avro/2pc/debezium——零回退引用）、nop-message-debezium 引擎 commit 策略本体（`NopStreamOffsetBackingStore` 已由 2511 C8-001 收敛）、`model/*.orm.xml`、`_` 前缀生成物；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-stream/nop-stream-sql`：`eval/StreamScalarFunctionResolver`（C1-001，含 §3a docs bug）+ 测试（`TestStreamScalarFunctionResolver` 补测 + `TestUdfE2E` 零回退）。
- `nop-stream/nop-stream-fraud-example`：`scenario/DirectoryFileSourceFunction`（C3-008 + C6-007 同类两裁定）+ 测试（`TestDirectoryFileSourceFunction` 补测/反向钉 + C10-007 探针 + S2 场景 E2E 回归）。
- `nop-stream/nop-stream-core`：`operators/ProcessOperator` + `operators/AbstractUdfStreamOperator` + util 包 `FunctionUtils`（C3-009 根因载体，覆盖空洞闭合）+ 测试（`TestProcessOperator`/`TestOperatorSubtaskIsolation` 零回退 + C10-003 隔离测试）。
- 设计文档：`ai-dev/design/concurrency/stream-orchestration-thin-concurrency.md`（裁定①–④+测试缝+零回退钉，最终状态写法）；Owner doc：`docs-for-ai/03-modules/nop-stream-sql.md`、`docs-for-ai/03-modules/nop-stream.md`、`docs-for-ai/04-reference/source-anchors.md`（按实际）。

### Out Of Scope

- Non-Goals 所列全部项；R4-05 及后续批次工单；nop-stream-runtime 编排面（SupervisionLoop 只读）；nop-stream-connector 族与 2511 已修面（零回退）；nop-kernel/nop-api-core 平台落点（除非裁定触发 PA 申报流程）。

## Execution Plan

### Phase 1 - 编排薄层并发设计裁定文档 + 独立评审

Status: done
Targets: `ai-dev/design/concurrency/stream-orchestration-thin-concurrency.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下四项裁定+测试缝，每条唯一预设默认分支：
  - [x] **裁定① C1-001 注册表容器化 + 换绑契约**：预设默认分支 = `functions` 改 `ConcurrentHashMap` + `register`/`registerAsDefault` 运行期（default 已在位时）WARN + javadoc 生命周期契约；运行期换绑是否升级 typed fail-fast 一次定夺（判据：公共 SPI 面声明 vs 应用侧合法热更新场景）；sql.md §3a 双路径表述修正文案成文；与 R4-05 C6-003（close/解绑面）边界注记。
  - [x] **裁定② C3-008 增量恢复**：预设默认分支 = 增量合并（per-file 位点取 max，不清零）——与「runEntered 静默才 REPLACE」备选按判据一次定夺（判据：per-file 位点单调性 + 恢复确定性 + 已完成文件游标不被清零）；验收断言口径以复核机制（checkpoint 位点滞后→恢复重放）为准成文；编排侧移交边界（SupervisionLoop Phase-3 统一 submit 归 runtime 单元）注记。
  - [x] **裁定③ C3-009 per-subtask 封闭**：预设默认分支定夺（UDF 深拷贝私有化[2511 裁定② round-trip 同族手段，含 xlang 叶子豁免引用] vs per-subtask RuntimeContext holder）；序列化约束与无状态 UDF 零回归取舍成文；失效面外溢矩阵（含状态 RichFunction UDF 全型）+ PA 零触碰声明（落点 nop-stream-core，如需上收平台层则批内申报 plan-first）；C10-003 回归锚归属登记（挂 core 工单）成文。
  - [x] **裁定④ C6-007 cancelled 复位**：预设默认分支 = run 入口 CAS 成功后复位；与基类复位契约（`DebeziumCdcSourceFunction` :247-252：running/draining/pendingError/latch）的对齐关系成文；C1-003 联调边界 + C10-006 反向钉零回退义务成文；两条重建路径（LOCAL/distributed）验证矩阵成文；C10-007 搭车探针清单（MultiFile/BufferBoundary/空目录）与 C10-008 不搭车理由成文。
  - [x] **裁定⑤ 测试缝与零回退钉清单**：测试缝 = 并发 register×call 压窗器 + namesList CME 注入（①）、Phase-3 restore-vs-run 时序模拟 + 已完成文件变体（②）、LOCAL P=2 keyed UDF 隔离矩阵 + keyedStateStore 实例同一性白盒断言（③）、cancel 后同实例重跑 + run 中 cancel 变体（④）、C10-007 三探针；零回退钉 = `TestUdfE2E`、`TestDirectoryFileSourceFunction#runEnteredGuardPreventsDoubleEmission`（C10-006）、`TestProcessOperator`、`TestOperatorSubtaskIsolation`、`TestS1CdcPipelineE2E`、`TestS2RecoveryAndRescaleE2E`、`TestS2FileAggregationE2E`、`TestParallel2PcFileE2E`、`TestStreamScalarFunctionResolver`（补测外其余用例）全列，补测类（C10-001/002/003/004/007）标注「新增断言即新钉、缺陷态红→修复后绿」。
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑤全部项且每条有唯一预设默认分支，独立 draft review 记录在案
- [x] 裁定③含 core 落点 PA 核对声明与失效面外溢矩阵；裁定④含 C10-006 反向钉零回退义务与 C1-003 联调边界；裁定⑤含零回退钉全列与补测类缺陷态红口径
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 4 成员实现 + 回归测试（按模块三组）

Status: done
Targets: `nop-stream/nop-stream-sql/src/`、`nop-stream/nop-stream-fraud-example/src/`、`nop-stream/nop-stream-core/src/`——各自 main + test 树

- Item Types: `Fix`（4 项均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 收口门 + C10-007 搭车）

- [x] 组 sql：C1-001（容器化+换绑契约，按裁定①，含 sql.md §3a docs bug 同 diff——docs 修正随本体 commit 不拆）
- [x] 组 fraud-example：C3-008（增量恢复，按裁定②）+ C6-007（cancelled 复位，按裁定④，与裁定②同文件共验 C10-006 钉）+ C10-007 搭车探针
- [x] 组 core：C3-009（per-subtask 封闭，按裁定③）+ C10-003 隔离测试（归属登记注明 core）
- [x] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕）：
  - [x] C1-001/C10-001 红①：并发 register×call 压窗——修复前撕裂/null/CME（trip-wire 红）→ 修复后 CHM 零 CME、注册可见、namesList 无 CME 子用例绿；换绑 WARN/契约断言按裁定①形态
  - [x] C3-008/C10-002 红②：restore-vs-run 交错（已完成文件变体）——修复前 checkpoint 位点滞后断言红 → 修复后增量合并位点不回退、恢复重放零重复计入
  - [x] C3-009/C10-003 红③：LOCAL P=2 有状态 keyed UDF——修复前 keyedStateStore 实例同一性断言红（两子任务收敛同一 backend）→ 修复后各子任务 context/backend 独立、跨键零污染
  - [x] C6-007/C10-004 红④：cancel 后同实例重跑——修复前零事件红 → 修复后全量重发 + 快照游标单调；run 中 cancel 变体先红留痕
  - [x] C10-007 搭车：MultiFile ≥3 文件顺序 + ≥8KB 块边界 resume 不丢不重 + 空目录 EOS 契约探针（纯参数化入 CI）
- [x] **端到端验证**（rule 22）：≥1 条 S2 链路 E2E（源失败/取消 → region 重启 → 恢复重放零重复 + 零截断——覆盖裁定②④组合行为；`TestS2RecoveryAndRescaleE2E` 为最近载体，若其不覆盖 cancel 路径则补参数化 leg）；≥1 条 SQL UDF 声明面链路（`<sql>` 声明 → 编译 → 逐记录 call——覆盖裁定①，`TestUdfE2E` 扩展或新用例）
- [x] **接线验证**（rule 23）：裁定③ per-subtask context 真实被 `UserHistoryEnricher` 形态消费断言（非仅字段存在）；裁定②增量合并真实被 `initializeState`→`advanceCursor` 链消费断言；裁定① CHM 真实被生成 xpl 体 `call()` 消费断言
- [x] **无静默跳过**（rule 24）：裁定①换绑拒绝形态（若裁 typed fail-fast）显式失败而非静默换绑；裁定②若裁「静默才 REPLACE」则运行中 restore typed 拒绝而非静默跳过；新增公共分支无空方法体/TODO
- [x] **提交纪律**：设计文档先行单独提交；按组提交——sql / fraud-example / core，C10 测试与对应本体同一 commit；组间无耦合时可独立提交序列
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 4 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 四条收口门 + C10-007 搭车同 diff 落地；C10-006 反向钉保持绿
- [x] 必红证据（E2）：红①–红④全部先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [x] 零回退实证：零回退钉清单全部保持绿（钉零回改逐条 git diff 为空核对；C10-006 `runEnteredGuardPreventsDoubleEmission` 与 `TestUdfE2E` 为关键钉）
- [x] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [x] `./mvnw test -pl nop-stream -am` BUILD SUCCESS（debezium docker fork timeout 环境抖动按 Baseline 勘误 5 三联裁定处理并留痕）；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向；sql.md §3a 修正随组 sql 本体 commit 的除外）
- [x] `ai-dev/logs/` 对应日期条目已更新


- 偏差登记（实施期，core 组）：设计文档 §3.3「钉 fixture 均非 RichFunction」前提失实——ProcessFunction 本身是 RichFunction，ProcessOperator 的 UDF 全部落 round-trip 分支；零回退钉 TestOperatorSubtaskIsolation assertSame 属缺陷钉住断言，按 2511 C10-003 范式同 diff 反转为 assertNotSame（其余钉零触碰）；透传分支对通用工具其他形态仍有效。已同步登记至设计文档 §3.3 实施期勘误块。

- 偏差登记（2511 残留闭环，fraud fixture 面）：2511 偏差③ fraud fixture 适配（`ReplayableCdcSourceFunction.toChangeEvent` 补 lsn/serverName 满足 C8-001 typed 契约）的补全——C8-001 后 `snapshotState` 在 tracker 有位点时以合成 map（marker+per-server）整体替换 store 快照，fixture 私有恢复键 `fraud-s1-replay-idx` 被丢弃，恢复后从头重放（`offsetCheckpointAndResumeSkipConsumedPrefix` expected 3/2 was 6）。判定为 fixture 面而非产品语义缺陷（真实引擎按 per-server 合成条目恢复，契约自洽）；修法：fixture 覆写 `snapshotState` 将 store 中的 replay-idx 以同 toSerializable base64 形态并回 checkpoint map（emitter 同线程先 tracker 记账后写 idx，不超前 tracker 边界）。证据：`_tmp/red-green-2512-residual-C8-001-{red,green}.log`、`_tmp/2512-fraud-gate-r2.log`（EXIT=0，TestS1CdcPipelineE2E 3/3 绿）。

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: `nop-stream` 聚合全量测试；`docs-for-ai/03-modules/nop-stream-sql.md`；`docs-for-ai/03-modules/nop-stream.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2511 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-stream -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；环境抖动按三联裁定留痕；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `model/*.orm.xml`、`_` 前缀生成物、R4-05 批外成员本体（C1-003/C6-003/C6-005/MA-02~05 等）、nop-stream-runtime 编排面（`SupervisionLoop` 零 diff 核对）、nop-message-debezium（`git diff --name-only` 核对登记）
- [ ] Owner doc 更新：`nop-stream-sql.md` §3a——双路径表述修正（若 Phase 2 未随本体 commit 落账则此处复核）、换绑生命周期契约一句、（如裁 typed fail-fast）拒绝语义声明；`nop-stream.md`——source 跨 run 复用契约（cancelled 复位 + 增量恢复语义）与 ProcessOperator per-subtask 契约补节或以 ≤3 行摘要登记（落点裁定：恢复契约区相邻 vs user-guide 链接，一次定夺）；`source-anchors.md` 新增锚点（按实际）
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0（批前既有发现逐条裁定登记）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] nop-stream-sql.md §3a 修正 + nop-stream.md 契约节/登记 + source-anchors.md 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（orm.xml/生成物/R4-05 批外成员本体/SupervisionLoop/nop-message-debezium）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.18 行、cra-index 台账、R4-05 就绪注记

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（CHM 换绑契约/增量合并/per-subtask context/cancelled 复位运行时真实生效、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：L155/L156/L157/L158 四行状态收敛（open → fixed，注记 plan 2512；吸收成员 C3-002/C2-001 随 C1-001 主行括注）；C10-001/002/003/004/007 收敛注记（C10-007 注记 G91 行 1.3 半边消号、C10-008 维持 R-G）；roadmap 8.18 行 Status → done；R4-05（8.45）软依赖就绪注记（沿 2510/2511 先例——含 C1-003 联调结论、C6-005/MA-03/MA-04 同文件族前提、C6-003 边界、C10-008 归 R-G 边界声明）
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 4 行收敛 + C10 五条注记（含 G91 消号）、roadmap 8.18 = done、R4-05 就绪注记完成
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 4 项成员全部修复：C1-001（注册表容器化+换绑契约+§3a docs bug）、C3-008（REPLACE 改增量恢复）、C3-009（RuntimeContext per-subtask 封闭）、C6-007（cancelled 复位）
- [ ] batch-proposal R2-09 行 exit criteria 增补四段逐条达成并留证：①**UDF 注册表并发容器化+运行期换绑契约（含 sql.md §3a docs bug 修正）**（并发压窗 trip-wire+CME 注入+换绑契约断言）；②**REPLACE 清零改增量恢复**（restore-vs-run 交错+位点不回退断言）；③**RuntimeContext per-subtask 封闭**（P=2 隔离矩阵+实例同一性白盒断言）；④**cancelled volatile 复位**（同实例重跑+run 中 cancel 变体）。C10-007 搭车经裁定④/⑤承载（勘误 4 对应项）
- [ ] 联调纪律守约：C6-007×C1-003 同文件共验且 C10-006 `runEnteredGuardPreventsDoubleEmission` 零回退（git diff 为空核对）；C10-003 回归锚归属登记注明 core 工单
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红④均有先红后绿或变异/回退论证（E2）；C10 五条同 diff 落地
- [ ] 设计文档裁定①–⑤成文且经独立 draft review；既有钉（零回退钉清单全列）零回退实证
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-stream -am` BUILD SUCCESS（E1/E3；debezium 环境抖动三联裁定留痕）
- [ ] Owner doc 更新完成：nop-stream-sql.md §3a + nop-stream.md + source-anchors.md（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）CHM 换绑契约/增量合并/per-subtask context/cancelled 复位运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-stream 聚合）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 4 行收敛 + roadmap 8.18 → done + R4-05 就绪注记（E6）

## Deferred But Adjudicated

### R4-05 成员（G11 十项：1.3-MA-02/MA-03/MA-04/MA-05 + 1.3-C1-002/C1-003/C1-005/C3-005/C6-003/C6-005）及其 C10-005/C10-008

- Classification: `out-of-scope improvement`（batch-proposal R4-05 行显式安排，非本批降级）；Successor Required: `yes`；Successor Path: R4-05 批 plan（roadmap 8.45，软依赖 8.18）
- Why Not Blocking Closure: 判级对齐零重复——cra-index P2 台账 G11 行批次列=R4-05；本批已收敛同单元 P1 四族主体，R4-05 实施以本批后的 live 形态为基线，且可引用本批裁定④（C1-003 联调结论/C10-006 钉现状）与裁定①（C6-003 前提）为既有裁定不重开体。

### 1.3-C10-008（emitter 线程有界终止断言 + double-subscribe 契约用例，G91 无本体条目）

- Classification: `out-of-scope improvement`（batch-proposal §五 G91 → 治理批 R-G；关联本体 C3-007/MA-04/C6-005 均在 R4-05）；Successor Required: `yes`；Successor Path: R-G 治理批（或随 R4-05 对应族修复落地）
- Why Not Blocking Closure: 无在案缺陷本体在本批（关联 P2/P3 本体全在 R4-05）；本批强落会造无本体红的空壳断言；C10-007 已由本批搭车承接，1.3 半边消号。

### 编排侧修复候选（nop-stream-runtime `SupervisionLoop.restartRegion` Phase-3 统一 submit）

- Classification: `out-of-scope improvement`（merge-adjudication §4.6 #2 移交项，属 runtime 单元；非本批降级）；Successor Required: `yes`；Successor Path: 归 nop-stream-runtime 单元后续立项（cra-index 关联登记，非 R2-09/R4-05 成员）
- Why Not Blocking Closure: 本批裁定②在源类侧已闭合 C3-008 的违约路径（增量恢复不依赖编排改动）；编排侧候选是纵深防御优化且可同时闭合 CDC offset 兄弟面（该面已由 plan 2511 C8-001 收敛，纵深价值再评估后立项）。

## Non-Blocking Follow-ups

- **R4-05 对接注记**：本批落地后向 roadmap 8.45 行作「8.18 就绪」完成注记（软依赖解除落点）；R4-05 实施引用裁定①④为既有裁定，C1-003/C6-003 边界已在 cra-index 注记。
- **裁定①运行期换绑的观测面**：WARN 日志（或 typed 拒绝）在生产多作业负载下的触发频率归运维观测（owner doc 运维节留观测指引即可，不阻塞 closure）。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure

Status Note: （完成或关闭时填写）
Completed: （未完成）

Closure Audit Evidence:

- Reviewer / Agent: （待 closure audit）
- Evidence: （待记录）

Follow-up:

- （待 closure 时登记，或明确写 no remaining plan-owned work）
