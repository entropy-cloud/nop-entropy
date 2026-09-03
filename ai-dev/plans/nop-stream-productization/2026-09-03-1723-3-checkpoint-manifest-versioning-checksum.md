# checkpoint manifest 版本化与校验和落地（roadmap item 25，P-REQ-20 go 裁定收敛载体 / D-DRIFT-2 收敛）

> Plan Status: active
> Mission: nop-stream-productization
> Work Item: item 25（[Follow-up，来源 item 8 plan `2026-09-01-0938-3`（runtime 审计报告 §2.1 ①a 方向裁定：补字段落地，实施跨 core/runtime 转 Follow-up）]）
> Last Reviewed: 2026-09-03
> Source: `ai-dev/analysis/2026-09/2026-09-01-nop-stream-runtime-module-audit.md` §2.1 ①a（方向裁定：stateFormatVersion alias CheckpointSerDe 信封版本为单一版本真值 + checksum 覆盖范围定义）；`ai-dev/analysis/2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md` §1 D-DRIFT-2 + §2.2 P-REQ-20 go（窄增量：设计承诺未落地收敛）
> Related: `2026-09-01-0938-3-runtime-module-audit.md`（item 8，已补齐 torn-write 注入测试——P-REQ-20 剩余缺口仅 manifest 级两字段）；`2026-09-03-0830-3-state-serde-dedup-convergence.md`（item 21/30，其显式 Non-Goal「格式变更属 item 25」——本 plan 即该边界另一侧）；items 26/27/28（runtime 协调器结构/fencing/数据面治理，同区域不同缺陷面，不捆绑）

## Purpose

把 checkpoint-design §2.6 已承诺而代码缺失的 manifest 级 `stateFormatVersion` 与 `checksum` 两字段落地为代码（D-DRIFT-2 收敛），赋予 epoch manifest 自描述的格式版本与完整性校验能力：持久化产物携带两字段（写入位置 Phase 1 裁定），restore 路径校验 checksum（存在即校验、不匹配 typed fail-fast）、容忍旧 manifest（字段缺失），并以 round-trip/篡改/legacy/确定性/版本不匹配五类测试钉定——**不改变既有 checkpoint 产物的可恢复性**（旧 manifest 恢复路径行为不变）。

## Current Baseline

（2026-09-03 live 复算）

- **字段宿主在 core**：`EpochManifest`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/EpochManifest.java`）现有字段 epochId/jobId/pipelineId/timestamp/checkpointType/state/taskSnapshots/streamModelFingerprint/segments/sourceEnumeratorSnapshots——**无 stateFormatVersion、无 checksum**（D-DRIFT-2 事实）。
- **单一序列化咽喉点在 runtime**：`CheckpointSerDe`（`nop-stream-runtime/.../checkpoint/storage/CheckpointSerDe.java`）`serializeEpochManifest`（:142）/`deserializeEpochManifest`（:205）——LinkedHashMap 定序组装 → `JsonTool.serialize` 字节；信封已携带 `FORMAT_VERSION_KEY = CURRENT_FORMAT_VERSION = 2`（`LEGACY_FORMAT_VERSION = 1`，:48-49）；双存储均经此咽喉：LocalFile 三方法（`storeEpochManifest`/`loadLatestEpochManifest`/`loadRetainedEpochManifests`）、JDBC 两方法（无 `loadRetainedEpochManifests` override——接口 default ≤1，audit W-8 已裁定承接归 item 28，**本 plan 不处理**）。`detectFormatVersion`（:546-552）现状无上界检查（`formatVersion > CURRENT` 被静默接受——读侧版本语义缺口，Phase 1 裁定）。
- **写入方**：`CheckpointCoordinator.buildEpochManifest`（两个调用点 :534/:641 + overload :1378/:1386 构造 `new EpochManifest(...)`）。**锁作用域事实**：非增量路径 build 发生在段 1 **持 coordinator monitor** 内（:511 注释），序列化被刻意移到段 2（persist executor 线程；**async 模式（默认）不持锁，sync-fallback 模式（`asyncSnapshotEnabled=false`）段 2 在 ACK 线程 monitor 内 inline**；checkpoint-design §2.2）——checksum 计算位置裁定必须尊重该锁作用域约束并显式裁定 sync-fallback 残余（见 Phase 1）。`new EpochManifest(` 全仓 21 处（main 2 + test 19）——构造器兼容面已知。
- **设计承诺（drift 源）**：checkpoint-design.md §2.6 字段表 :201 `stateFormatVersion`、:203 `checksum` 两行已存在（同表 `planFingerprint`/`requirements`/`sourceOffsets`/`sinkTransactions`/`participantStates`/`createdTime`/`durableTime` 等行亦无代码对应，但**非本 plan 范围**——本 plan 只收敛 P-REQ-20 go 裁定点名的两字段）。
- **既有参照**：segment 级 checksum/schemaVersion 已 landed（`StateSegmentDescriptor` :49-50，D-GAP §1.1 ③）；torn-write 注入测试已由 item 8 补齐；runtime 审计 §2.1 ①a 已裁定方向——**stateFormatVersion 必须 alias CheckpointSerDe 信封版本（单一版本真值，禁止新造第二套版本号）**；checksum 覆盖范围 = canonical 序列化去 checksum 字段后 SHA-256（canonical 形态待 Phase 1 设计）。
- **既有测试面**：manifest round-trip 既有用例（CheckpointSerDe/双存储测试族）、S1/S2 场景 LOCAL + gated DISTRIBUTED 测试默认套件全绿基线（3191/0/0，item 16 记录）。
- 模块依赖方向：runtime → core 单向；core 不得 import runtime（版本常量的落位裁定见 Phase 1）。

## Goals

- `EpochManifest` 增 `stateFormatVersion`（int）与 `checksum`（String）两字段；构造路径兼容（既有构造器/反序列化路径不破坏）。
- 单一版本真值成立：manifest `stateFormatVersion` ≡ 序列化信封 `CURRENT_FORMAT_VERSION`，实现形态（常量落位 core 由 runtime 引用 / 其它）由 Phase 1 裁定，禁止两处独立数字。
- checksum 语义：持久化产物携带 checksum（canonical 序列化去 checksum 字段后 SHA-256）；**计算与写入位置由 Phase 1 裁定**（候选：`buildEpochManifest` 构造期 vs `CheckpointSerDe.serializeEpochManifest` 咽喉期——硬约束：不得把 canonical 序列化的 CPU 开销移入 coordinator monitor 持锁段，尊重 §2.2 段 1/段 2 分工）；restore 加载时**存在即校验**——不匹配 → typed `StreamException`（错误码 + 定位参数，如 jobId/epochId），字段缺失（legacy manifest）→ 跳过校验照常恢复。
- stateFormatVersion 读侧语义：字段缺失（legacy）→ 容忍；**存在但 ≠ 当前版本 → 行为由 Phase 1 裁定**（fail-fast vs 容忍降级；现状 `detectFormatVersion` 无上界检查，静默接受高版本——本 plan 必须给出确定语义而非沿用静默）。
- 兼容性钉定：旧 manifest（无两字段）恢复行为不变；新 manifest round-trip 后 checksum 复算稳定（canonical 形态对 round-trip 确定性有硬要求，Phase 1 设计必须证明）。
- checkpoint-design.md §2.6 两字段行更新为「落地为代码」语义（对齐 sourceEnumeratorSnapshots 行的落地标注先例）；D-DRIFT-2 收敛记录。

## Non-Goals

- §2.6 表中其它无代码对应字段（planFingerprint/requirements/sourceOffsets/sinkTransactions/participantStates/createdTime/durableTime）的落地或删改（P-REQ-20 未点名；是否另行治理由 closure 时裁定归属，不在本 plan 擅自扩面）。
- 信封 `CURRENT_FORMAT_VERSION` 递增（新增可选字段 + null 容忍不构成版本 bump 依据；是否 bump 由 Phase 1 显式裁定并默认否决，除非发现破坏性证据）。
- segment 级 checksum/schemaVersion（已 landed）、torn-write 测试（item 8 已补齐）。
- items 26/27/28（协调器结构治理/fencing/数据面通道——同区域不同缺陷面）。
- 存储介质变更、checksum 之外的完整性机制（如 HMAC/签名）。

## Scope

### In Scope

- core：`EpochManifest` 两字段 + 构造器兼容；typed 错误码承载（`NopStreamErrors` 新增常量 vs 复用——Phase 1 裁定）。
- runtime：持久化路径写入两字段（**写入位置按 Phase 1 裁定**：`buildEpochManifest` vs `CheckpointSerDe.serializeEpochManifest` 咽喉）；`CheckpointSerDe` serialize/deserialize 两字段透传 + checksum 写入与校验（canonical 形态按 Phase 1 设计）+ `stateFormatVersion` 读侧语义；版本常量落位裁定落地。
- 测试：round-trip（含新字段）、legacy 兼容（无字段 fixture）、篡改 fail-fast、canonical 确定性、版本不匹配五类用例。
- 设计文档 §2.6 同步 + D-DRIFT-2 收敛记录；owner doc（`docs-for-ai/03-modules/nop-stream.md` 如涉及 checkpoint 契约面则同步）。

### Out Of Scope

- 上列 Non-Goals 全部。

## Execution Plan

### Phase 1 - canonical 形态与版本常量落位设计（决策 phase，不动生产代码）

Status: planned
Targets: `ai-dev/design/nop-stream/checkpoint-design.md`（§2.6 增补落地语义段）、本 plan 日志（设计裁定记录）

- Item Types: `Decision`

- [ ] **canonical 序列化形态设计**：checksum 的计算基准形态裁定——**机制是一体的两侧（必须同时定义且相互一致，不是二选一）**：store 侧（写入时对「按固定字段序组装、不含 checksum 键的 map」做 `JsonTool.serialize` 的字节取 SHA-256）+ load 侧（校验时对「原始解析 map 去 checksum 键后按同固定字段序重组」取哈希——不经 bean 往返，规避数字类型表示漂移）。**硬要求 1（round-trip 确定性）**：同一 manifest「写入时哈希 = 加载时复算哈希」必须相等。**硬要求 2（确定性基底层钉定）**：Phase 1 设计必须把以下基底层事实作为前提钉定并要求 Phase 2 确定性测试作 tripwire——①`JsonTool.parseMap` 逐层构建 `LinkedHashMap`（解析侧嵌套字段序 = 文档序保持，平台 `BuildObjectJsonHandler` 现状）；②**整数**文本稳定性（int/long 快路径文本表示往返稳定，平台 `TextScanner` 现状）；③**小数/浮点文本往返钉定（必须显式裁定）**：真实 manifest 可含小数值（`keyedStates` 原样透传、accumulator `localValue` 任意类型），load 侧解析经 `Double.parseDouble` 是文本→Double 收窄——若写侧产生的文本不是 parse∘serialize 的不动点（如 `BigDecimal "0.100"`、科学计数法形态），load 侧复算哈希必然偏离。Phase 1 必须三选一：钉定写侧小数产出面全为 `Double.toString` 不动点（并证明 keyedStates/accumulator 实际取值面）/ 定义 canonical 数字归一化规则 / 界定不稳定产出并排除出 checksum 覆盖面（显式记录排除边界）；若平台实现变更导致基底层失效，确定性测试必须红。**子裁定**：canonical 字段清单是否含信封 `formatVersion` 键；两新字段的省略规则（null 省略 vs 恒写）与 `stateFormatVersion` 的可空形态（`int` + 0 哨兵 vs `Integer` null——legacy 兼容与 Phase 2 legacy 测试的程序化构造都依赖此裁定）
- [ ] **checksum 计算与写入位置裁定**：候选 ①`buildEpochManifest` 构造期写入（注意：非增量路径 build 在 coordinator monitor 持锁段内执行（CheckpointCoordinator.java:511 段 1），构造期计算 checksum = 把 canonical 序列化 CPU 移入锁内——与 §2.2 段 1/段 2 分工及 item 26 已审计的「锁内 I/O」反模式冲突）；候选 ②`CheckpointSerDe.serializeEpochManifest` 咽喉期计算+注入（persist executor 线程，**async 模式**（默认）不持锁；**注意 sync-fallback 残余**：`asyncSnapshotEnabled=false` 时段 2 存储 I/O 含序列化在 ACK 线程 monitor 持锁内 inline 执行（CheckpointCoordinator.java:536-541）——若选 ② 须显式裁定接受/标注该非默认模式下的残余（其序列化本已在锁内，checksum 增量为同一锁内的第二次序列化 + SHA-256））。裁定标准：锁作用域合规 + 单一 canonical 组装路径（禁止 build 期与 serialize 期两套组装产生 drift）+ 字段进入 manifest 对象的时点（`EpochManifest` 字段 vs 序列化期注入的裁定一致性）
- [ ] **版本常量落位裁定**：单一版本真值的承载形态——候选：①core checkpoint 包持有 canonical 常量、runtime `CheckpointSerDe.CURRENT_FORMAT_VERSION` 改为引用/断言一致（core 不得依赖 runtime）；②runtime 持真值、core 字段仅存值（写入位置按上条裁定时传入）。裁定标准：单一真值可验证性 + 依赖方向合规 + 禁止两处独立数字
- [ ] **stateFormatVersion 读侧不匹配语义裁定**：manifest 携带版本存在但 ≠ `CURRENT_FORMAT_VERSION` 时的行为——fail-fast（typed 错误，自描述版本的本意）vs 容忍降级（何种条件下安全）；**含双版本面不一致子裁定**：信封 `formatVersion` 键与 `stateFormatVersion` 字段同时存在但互相矛盾时（如信封=2、字段=3）读侧以何者为准/如何报错；现状 `detectFormatVersion` 无上界检查、静默接受高版本——裁定后消除静默面（若裁定容忍必须给出非静默的可观察信号）
- [ ] **信封版本是否 bump 裁定**：默认否决 bump（可选字段 + legacy null 容忍 = 非破坏增量）；如发现必须 bump 的证据（旧代码读新 manifest 会错）则升级裁定并记录
- [ ] **校验时机与错误语义定义**：校验落点（`deserializeEpochManifest` 咽喉 = 双存储自动覆盖 vs 各存储调用点）；错误 = typed `StreamException` + 错误码（新增 vs 复用裁定）+ 定位参数（jobId/pipelineId/epochId/期望与实际值）——对齐仓库两级错误策略；legacy 缺字段跳过校验的显式判定（debug 日志与既有 formatVersion legacy 容忍路径对齐）
- [ ] 设计段回写：checkpoint-design.md §2.6 两字段行 + 落地语义段（canonical 形态、校验语义、legacy 容忍、版本 alias 关系——含拒绝替代方案）

Exit Criteria:

- [ ] 六项裁定记录（含拒绝替代方案与 round-trip 确定性论证）落日志；设计文档 §2.6 增补段已写入
- [ ] 裁定可直接执行：Phase 2 实现者读裁定即知改哪些文件、每个文件改什么（想象性分析通过）
- [ ] 本 phase 不动生产代码（`No owner-doc update required` 覆盖面外的 docs-for-ai 同步在 Phase 3 裁定；本 phase 的 checkpoint-design 变更跑 `node ai-dev/tools/check-doc-links.mjs --strict` exit 0）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 字段落地 + 写入/校验/兼容实现

Status: planned
Targets: `nop-stream/nop-stream-core/`（EpochManifest）、`nop-stream/nop-stream-runtime/`（CheckpointCoordinator、CheckpointSerDe）

- Item Types: `Fix`

- [ ] core：`EpochManifest` 增 `stateFormatVersion`/`checksum` 字段（既有构造器默认 null/0 兼容路径保留，新增带两字段的构造或 builder 入口——形态按 Phase 1 裁定）
- [ ] runtime：持久化路径写入两字段（stateFormatVersion 取 Phase 1 裁定的单一真值；checksum 按 canonical 形态与裁定位置计算——若裁定咽喉期注入则 `EpochManifest` 字段时点语义按裁定落实）；`CheckpointSerDe.serializeEpochManifest` 持久化两字段、`deserializeEpochManifest` 读回 + checksum 存在即校验（typed fail-fast）+ `stateFormatVersion` 读侧语义（Phase 1 裁定）+ 缺字段 legacy 跳过
- [ ] 测试五类落地：①round-trip——新 manifest serialize→deserialize→字段等值 + checksum 复算相等；②legacy——无两字段的 manifest 字节（fixture 或程序化构造旧格式）恢复成功且跳过校验有据（断言或日志级验证）；③篡改——对有效字节篡改任一载荷字段（如 epochId/timestamp）后加载抛 typed 错误（错误码 + 定位参数断言）；④确定性——对含全字段类型代表（taskSnapshots/fingerprint/segments/enumerator snapshots/**整数与小数/浮点代表值**/Base64）的 manifest 做「写入哈希 = 加载复算哈希」断言（Phase 1 硬要求的钉定 + 基底层 tripwire：若平台 JsonTool map/数字行为变更此测试必须红）；⑤版本不匹配——构造 `stateFormatVersion ≠ current` 的 manifest，断言 Phase 1 裁定的读侧语义（fail-fast 或显式容忍信号）；含双版本面不一致变体（信封与字段矛盾，断言子裁定语义）
- [ ] 双存储回归：LocalFile 与 JDBC 存储各自走一遍新 manifest 写读（两存储共用 CheckpointSerDe 咽喉，测试证明接线而非仅单测 SerDe）

Exit Criteria:

- [ ] 五类测试存在且全绿（类名/用例名可指认；篡改与版本不匹配用例断言错误码与参数——新行为 = 校验 fail-fast，测试必答）
- [ ] **无静默跳过**：checksum 不匹配走 typed 异常，无吞异常/警告后照常恢复分支（对照 guide 规则 24 自查）；legacy 缺字段跳过是**显式设计裁定**非静默绕过（Phase 1 裁定引用）
- [ ] **接线验证**（规则 23）：coordinator 真实 checkpoint 路径产出的 manifest 含两字段（非手工构造 manifest 单测）——用既有 coordinator/checkpoint 集成测试断言产物字段，或新增一条经 coordinator 完成真实 checkpoint 的用例
- [ ] **端到端验证**（规则 22）：S1/S2 场景默认套件绿（manifest 新字段经真实 checkpoint→恢复路径端到端不破坏）；`./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] 版本单一真值验证：代码中不存在两处独立版本数字（rg 复核 + 引用关系断言或一致性测试）
- [ ] owner-doc 裁定：checkpoint-design §2.6 已在 Phase 1 同步；`docs-for-ai/03-modules/nop-stream.md` checkpoint 契约面如提及 manifest 字段则同步，否则 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - gated 分布式复核 + D-DRIFT-2 收敛记录

Status: planned
Targets: `nop-stream/`（gated 测试）、`ai-dev/design/nop-stream/checkpoint-design.md`、`docs-for-ai/`（如需）

- Item Types: `Proof`

- [ ] gated 分布式抽查：启用态跑 C2 恢复演练一条——`TestS2RestoreRescaleMultiJvmE2E`（位于 **nop-stream-fraud-example** 模块 `src/test/.../scenario/`，启用参数 `-Dnop.stream.test.multi-jvm.enabled=true`），manifest 经存储跨 JVM 恢复路径含新字段完整走通——证明新字段在真实多 JVM checkpoint→restore 路径无兼容破坏
- [ ] D-DRIFT-2 收敛记录：设计文档 §2.6 增补段完成态复核 + D-GAP 报告处置引用链核对（item 8 裁定 → 本 plan 落地的闭环说明，落日志）
- [ ] 全量收口：`./mvnw test -pl nop-stream -am -T 1C` 默认态全绿 + gated 启用态抽查绿 + 四工具门禁（hollow/invariants/doc-links/plan-checklist）exit 0

Exit Criteria:

- [ ] gated 演练结果留档（runId/日志锚点）；默认态 + gated 双绿
- [ ] D-DRIFT-2 收敛记录存在（设计文档 + 日志双落点）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] `EpochManifest` 两字段存在且 coordinator 真实路径写入（接线证据）+ restore 校验语义（存在即校验/legacy 跳过）落地
- [ ] 单一版本真值成立（无第二套独立版本数字）
- [ ] 五类测试（round-trip/legacy/篡改/确定性/版本不匹配）+ 双存储回归 + gated 分布式抽查全绿
- [ ] 旧 manifest 恢复行为不变（legacy 兼容用例钉定）；新 manifest 除新增字段与校验语义外零行为变更
- [ ] checkpoint-design.md §2.6 两字段「落地为代码」+ D-DRIFT-2 收敛记录；owner doc 同步或显式 `No owner-doc update required`
- [ ] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow：checksum 计算与校验在真实 checkpoint 路径被调用，非孤岛工具方法）
- [ ] `./mvnw compile` + `./mvnw test`（`-pl nop-stream -am` 聚合）通过
- [ ] checkstyle / 代码规范检查通过（既有 ai-dev 门禁 exit 0）

## Deferred But Adjudicated

（预留——Phase 1 若裁定 §2.6 其它无代码字段「维持设计承诺、不落地」，在此记录为 out-of-scope improvement + 理由与潜在 successor 归属建议。）

## Non-Blocking Follow-ups

- §2.6 其余无代码对应字段的治理（收敛或改表）——P-REQ-20 未点名，不阻塞本 plan closure
- item 26（协调器 retention GC/三联克隆结构治理）——同区域独立缺陷面，由其自身排程

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
