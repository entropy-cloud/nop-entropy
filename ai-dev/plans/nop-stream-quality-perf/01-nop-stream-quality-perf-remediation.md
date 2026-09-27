# 01 nop-stream 代码质量与性能审计修复

> Plan Status: active
> Last Reviewed: 2026-09-28
> Source: `ai-dev/analysis/2026-09/2026-09-28-nop-stream-quality-perf-audit.md`（93 项发现，关键项已与 live 代码核对）
> Related: `ai-dev/plans/nop-stream-independent-audit/`、`ai-dev/plans/nop-stream-productization/`（历史计划，已关闭）
>
> 审查记录：两轮独立子代理对抗性审查（2026-09-28）。第一轮 9 问题（0 Blocker / 4 Major / 5 Minor）已全部回写；第二轮复核确认"有条件可执行"，条件（A12 行号、4 处计数同步）已落实。

## Purpose

把 nop-stream 审计发现中**尚未被 358/359/360/2277/2278/2279 六个先行计划处理**的部分收口到可验证状态：

1. 修复已确认且仍未处理的正确性缺陷（Phase 2，11 项，全部带聚焦测试）。
2. 对 3 个未被先前收敛轮裁定的性能候选（E3/F1/F2）补基准口径、实测、留舍（Phase 3）。
3. 完成 2278 之后仍残留的低风险死代码清理与重复逻辑合并（Phase 4）。
4. 延续 360/2279 的停止判据做收敛复验（Phase 5）：本轮触碰路径上无任何 ≥2% 低风险可收割项。

每个 Phase 完成并验证后立即 git commit（用户要求：每个计划完成后自动提交一次）。

## Current Baseline

> **基线修正（2026-09-28，执行期首项工作）**：本计划初稿的审计漏看了两块既有事实，已修正——
> (1) **基准设施已存在**：`nop-benchmark/nop-benchmark-stream`（plan 360 建、2279 扩展；13 类 / 20+ 方法，JMH 1.33，README 含运行与 JFR 方法论）覆盖了本计划原拟新建的全部场景，不新建模块。
> (2) **性能候选大半已被裁定**：plan `360-nop-stream-perf-jmh-jfr.md`（completed 2026-09-26）与 `2279-nop-stream-perf-r2-jmh-jfr.md`（completed 2026-09-27）已按"无 ≥2% 低风险可收割项"判据完成两轮收敛，裁定书在 `ai-dev/audits/evidence/nop-stream-perf-{360,2279}/convergence*.md`。本计划审计发现与之重叠的部分按其裁定归类（见 Deferred 节），仅 3 个候选未被裁定：E3、F1、F2。
> (3) **可读性整改已有两轮**：plan `359`（R1）与 `2278-nop-stream-readability-structure-r2.md`（completed 2026-09-27）。本计划 G 组发现取自 2278 之后的 HEAD，均为仍未处理项。

- nop-stream 5 个模块（core/flow/runtime/cep/rocksdb）`./mvnw test` 全绿（2026-09-28，`_tmp/nop-stream-audit-baseline.log` EXIT=0）。
- HEAD = b62b7f7748（2277/2278/2279 收口后）。以下发现均在 HEAD 逐行核实：
  - 缺陷组（Phase 2 项）：`JobCoordinator.globalRecovery` 的 `recoveryPending` 仅在 fan-out finally 清除（:1410-1528）；`CheckpointCoordinator` abort 路径 `checkpointSuccessMap` 残留；`CheckpointBarrierTracker.getCurrentCheckpointId()` 无锁读（:339-347，兄弟方法均有锁）；`invokeSink`/`invokeSelfContained` 未复用 `closeChainAndGate`（:635 已存在）；`JobCoordinator:940-944` 日志占位错误；`RocksDBKeyedStateBackend:210-212` listColumnFamilies 静默吞异常；`RocksDBInternalAggregatingState:80` 用 `descriptor.getValueType()` 反序列化；`JdbcTwoPhaseCommitSink:301-307` 幂等路径不清 pendingCommits；`FileTwoPhaseCommitSink` manifest 转义缺失；`FileSplitEnumeratorStateSerializer` 保留字符不校验。
  - A12（审查阶段新发现）：`WindowOperator.addWindowElement:1397-1403` List 分支无写回。**严重度修正**：2279 F3 裁定认定兜底 MapState 布局经 DSL（WindowOperatorBuilder 全路径传非 null descriptor）生产不可达，但 public 构造器（:260/:275/:291）descriptor 均为 null → 该路径对直接构造 WindowOperator 的嵌入方/测试可达。定性为 **public-API 潜在缺陷（Fix）**，非生产主线数据丢失。
  - 性能组（未被裁定、本计划实测裁定）：
    - E3：`NFA.java:976-995` PROCEED 边用户条件被 `createDecisionGraph` 与 `findFinalStateAfterProceed` 双重评估（360 的 NFA 优化聚焦哈希/分配，未触此项）。
    - F1：`RocksDBInternal{List,Aggregating,Appending}State` 绕过 360 R1 建立的 `(key,ns)→byte[]` 前缀缓存直接 `buildStorageKey`（public state 路径已走缓存，internal 路径遗漏）。
    - F2：`MemoryInternal{Appending,List}State` 每访问 `new TypedNamespaceAndKey`，而 value/aggregating flavor 已被 360 R1 memoized（同族修复未覆盖 appending/list）。
  - 可读性组（Phase 5 项，HEAD 核实）：G4/G7/G8/G9/G11/G13/G15/G17 及三处注释掉的 ClosureCleaner 调用。
- `nop-benchmark/nop-benchmark-stream` 基准缺口（本计划 Phase 1 只补这两处）：MemoryKeyedStateBench 无 internal list/appending flavor；NfaProcessBench 条件均为廉价 lambda，E3 双重评估需要"计费条件"档位才可测。

## Goals

- Phase 2 的 11 项缺陷全部修复且每项有回归测试。
- E3/F1/F2 三个候选各有 JMH 前后对比数据（沿用 2279 方差协议：≥3 重复、2-5% 边界重跑、未触碰 ±3% 噪声带）；≥2% 且无他项回退则保留，否则 revert 并记录无收益证据——两种结果都是合格的收口。
- Phase 4 删除已确认死代码、合并重复逻辑，不引入行为变化（错误消息锚点改进除外，逐项列明）。
- 收敛复验：触碰路径全量 JMH + JFR 复跑，给出"无 ≥2% 可收割项"的明确裁定记录（延续 360/2279 停止判据）。
- 全程每 Phase 一次独立 commit。

## Non-Goals

- 不做 JobCoordinator（2356 行）/InputGate（1289 行）/CepOperator（1342 行）的整体拆分重构（successor backlog，见 Deferred）。
- 不修复需要 owner 语义确认的行为变更类缺陷：A3 终态 savepoint 失败仍报 FINISHED、A4 standby 关共享 coordinator、A5 materialization 溢出静默旁路、A6 SupervisionLoop 缺 key 跳过、B2 restore 非原子、B7 类加载白名单（全部记录在 analysis 文档，等待单独立项）。
- 不做 D4（CC 锁内 RPC 外移）等锁序重构。
- 不引入新的分布式能力或 API。

## Scope

### In Scope

- 基准缺口补建：`nop-benchmark/nop-benchmark-stream` 内新增 2 个口径（memory internal list/appending flavor；NFA"计费条件"档位）。
- 缺陷修复：analysis 表 A1、A2、A7、A8、A9、A12、B1、B3、B4、B5、B6。
- 性能候选实测裁定：E3、F1、F2（未被 360/2279 裁定的全部剩余项）。
- 可读性：G4、G7、G8（死代码删除 + 共享 helper 复用）、G9、G11、G13、G15（public→包私有）、G17、InputGate 内 `WatermarkValve`/`BarrierAlignmentTracker` 提取（带门禁，失败可裁决移出）。
- 文档：`ai-dev/logs/`、analysis 文档裁定交叉引用列。

### Out Of Scope

- Deferred 段列出的全部条目及其 successor 归属。

## Execution Plan

### Phase 1 - 基准缺口补建与候选基线

Status: completed
Targets: `nop-benchmark/nop-benchmark-stream`（既有模块，不新建）

- Item Types: `Proof`

- [x] `MemoryKeyedStateBench` 增加内部 list/appending flavor 口径（对齐 RocksDbKeyedStateBench 的参数化方式），使 F2 可测
- [x] `NfaProcessBench` 增加"计费条件"档位（条件含可观测工作量的 SimpleCondition 变体），使 E3 双重评估可测
- [x] 运行受影响基准取得候选基线数字（F1 用既有 WindowOperatorProcessElementBench backend=rocksdb 档 + RocksDbKeyedStateBench；F2/E3 用新口径），记入 `## Benchmark Rounds` Round-0

> No new test required: 基准口径扩展本身即验证设施；既有 13 类基准与本轮新增口径的可运行性由冒烟运行证明，Round-0 数据即其验收。

Exit Criteria:

- [x] `./mvnw -q compile -pl nop-benchmark/nop-benchmark-stream` 通过，新口径冒烟运行成功
- [x] Round-0 候选基线数字已写入 `## Benchmark Rounds`（E3/F1/F2 各 ≥1 组 + 运行命令）
- [x] 既有 5 模块 `./mvnw test` 不受影响（bench 模块在 nop-benchmark 聚合下，未触碰 nop-stream 代码）
- [x] No owner-doc update required: 基准口径扩展不改变生产代码（nop-benchmark-stream README 的基准集表格随口径补充更新即文档义务本身）
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] git commit 完成（Phase 1 独立提交）

### Phase 2 - 已确认缺陷修复

Status: planned
Targets: `nop-stream-runtime/...coordinator/JobCoordinator.java`、`...checkpoint/CheckpointCoordinator.java`、`...operators/windowing/WindowOperator.java`、`nop-stream-core/...execution/CheckpointBarrierTracker.java`、`...task/StreamTaskInvokable.java`、`nop-stream-rocksdb/...RocksDBKeyedStateBackend.java`、`RocksDBInternalAggregatingState.java`、`nop-stream-connector-jdbc/...JdbcTwoPhaseCommitSink.java`、`nop-stream-connector/...file/FileTwoPhaseCommitSink.java`、`FileSource.java`

- Item Types: `Fix`

- [ ] A1：`globalRecovery` 全体包入 try/finally 清除 `recoveryPending`（锁内任何异常不再永久卡死恢复与 checkpoint 触发）
- [ ] A2：abort 路径确保 `checkpointSuccessMap` 条目被移除（无 failedCommitParticipants 的 abort 也清理）
- [ ] A7：`CheckpointBarrierTracker.getCurrentCheckpointId()` 与兄弟方法同样加锁
- [ ] A8：`invokeSink`/`invokeSelfContained` 复用 `closeChainAndGate`（operatorChain.close 抛出不再跳过 closeInputGate）
- [ ] A9：修复 `JobCoordinator` 任务状态报告日志的两个 `getTerminalState()` 占位错误
- [ ] A12：`WindowOperator.addWindowElement` 的 `current instanceof List` 分支补 `setWindowContents` 写回——public 构造器路径（descriptor=null → MapState 回退）+ evictor + RocksDB 等拷贝语义后端时，第 2 条起记录被静默丢弃（内存后端靠活引用侥幸正确；DSL 主线路径不可达，见 2279 F3 裁定，但 public-API 路径可达）；回归测试须用拷贝语义后端断言多条记录全部累积
- [ ] B1：`listColumnFamilies` 失败改为 `LOG.warn`（行为保持：仍按空列表处理，但不再静默）
- [ ] B3：`RocksDBInternalAggregatingState.getAccumulator()` 改用 `storageValueType` 反序列化
- [ ] B4：`JdbcTwoPhaseCommitSink` 幂等 re-commit 路径清除 `pendingCommits` 条目（对齐 file sink 行为）
- [ ] B5：`FileTwoPhaseCommitSink` manifest 写出转义 `\`、`=`、前导 `#`/`!`（Properties.load 读回兼容）
- [ ] B6：`FileSplitEnumeratorStateSerializer.serialize` 写出前校验保留字符（与 split serializer 对称，序列化期快速失败）
- [ ] 为上述每项新增/补强聚焦测试（缺陷注入回归测试，验证正确行为而非仅无异常）

Exit Criteria:

- [ ] Phase 2 全部 checkbox 勾选，每项修复有对应测试（测试名与修复项一一对应可查）
- [ ] `./mvnw test -pl nop-stream-core,nop-stream-runtime,nop-stream-rocksdb,nop-stream-connector-jdbc,nop-stream-connector -am` 全绿
- [ ] A12 回归测试使用拷贝语义后端（RocksDB 或等效）验证 MapState 回退 + evictor 路径多条记录全部累积
- [ ] owner-doc 裁定：`ai-dev/analysis/2026-09/2026-09-28-nop-stream-quality-perf-audit.md` 的 A1/A2/A7/A8/A9/A12/B1/B3/B4/B5/B6 行追加已修复状态；其余 docs 无涉及契约变化，`No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] git commit 完成（Phase 2 独立提交）

### Phase 3 - 未裁定性能候选实测留舍（E3/F1/F2）

Status: planned
Targets: `nop-stream-cep/...nfa/NFA.java`、`nop-stream-rocksdb/RocksDBInternal{List,Aggregating,Appending}State.java`、`nop-stream-core/...memory/MemoryInternal{Appending,List}State.java`

- Item Types: `Fix`

裁定纪律（沿用 2279 协议）：每候选 ≥3 次重复或误差条重叠判定；2-5% 边界结果重跑一轮确认；未触碰基准在 ±3% 噪声带内。≥2% 且无他项回退 → 保留；否则 revert 并记录无收益证据。**两种结果都是合格收口**（用户判据：优化至无 ≥2% 收益为止）。

- [ ] F1：RocksDB 三个 Internal state 走 360 R1 已建立的 `(key,namespace)→bytes` 缓存路径（若需新增带缓存的二参变体，保持 namespace 双轨语义不变）；实测 WindowOperatorProcessElementBench backend=rocksdb 各布局档前后对比
- [ ] F2：`MemoryInternalAppendingState`/`MemoryInternalListState` 改用 backend 的缓存（对齐 value/aggregating flavor 的 360 R1 形态）；实测 MemoryKeyedStateBench 新口径前后对比
- [ ] F1/F2 聚焦测试：缓存路径与非缓存路径产出相同 storage key（含 setCurrentKey/setCurrentNamespace 切换后缓存失效正确性），namespace 双轨（internal state 自持 namespace）不因走缓存而错域
- [ ] E3：NFA PROCEED 边条件评估结果在 `createDecisionGraph` 内缓存复用（消除 `findFinalStateAfterProceed` 的重评估）；实测 NfaProcessBench 计费条件档前后对比；cep 全量测试守护语义（含跳过策略/超时路径）
- [ ] 每项留舍数字记入 `## Benchmark Rounds`

Exit Criteria:

- [ ] E3/F1/F2 每项有 JMH 前后对比数字与留舍裁定（保留或 revert+无收益证据，Round 表可查）
- [ ] F1/F2 聚焦测试存在且通过（storage key 等价 + 缓存失效 + 双轨错域防护）；E3 语义由 cep 全量测试套件守护并全绿
- [ ] **端到端验证**（Rule #22）：复跑 runtime e2e 套件中 `TestE2EWindowOperatorWithCheckpoint`、`TestE2EWindowAggregateRestore` 等 checkpoint 恢复类测试，确认 F1 改动不破坏端到端行为
- [ ] `./mvnw test -pl nop-stream-core,nop-stream-cep,nop-stream-rocksdb,nop-stream-runtime -am` 全绿
- [ ] No owner-doc update required: 行为保持的优化/留舍，无契约变化
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] git commit 完成（Phase 3 独立提交）

### Phase 4 - 可读性与可维护性治理

Status: planned
Targets: `WindowOperator.java`、`StreamModelDslBuilder.java`、`StreamTaskInvokable.java`、`SubtaskTask.java`、`FileSplit.java`、`InputGate.java` 及重复 teardown 调用点

- Item Types: `Fix | Proof`

- [ ] G7：删除 `WindowOperator.Timer` 死类；G17：删除 `FileSplit.Cursor` 死类（执行前 grep 复核零引用）
- [ ] G8：删除 `StreamModelDslBuilder.resolveFunction`；`buildSource`/`buildSink` 路由到 `resolveFunctionOrXpl`（错误消息获得 `.loc` 锚点属预期改进，测试断言同步更新）
- [ ] G9：修复 `StreamTaskInvokable` 挂错方法的 javadoc
- [ ] G11：提取共享 `closeAll` 工具并替换 ≥5 处重复 teardown（行为逐字保持）
- [ ] G13：`SubtaskTask` 单次迭代 `while` 改 `if`
- [ ] G4：合并 `wireOperators` 两个重载的重复链组装逻辑
- [ ] G15：`StreamModelDslBuilder` 仅同包使用的 public 成员降为包私有（先 grep 确认无外部调用者）
- [ ] InputGate 提取 `WatermarkValve` 与 `BarrierAlignmentTracker`（包私有协作类，行为逐字保持）。门禁：提取前后现有 InputGate/runtime 测试全绿 + Phase 3 涉及基准无 >2% 退化；若门禁失败则裁决移出至 Deferred（可裁决，非缺陷项）
- [ ] 死注释清理：三处注释掉的 `ClosureCleaner.clean` 调用（执行前确认无恢复意图）

Exit Criteria:

- [ ] 上述每项完成后 grep/阅读验证：死代码零引用、重复逻辑单一出处
- [ ] `./mvnw test -pl nop-stream-core,nop-stream-flow,nop-stream-runtime -am` 全绿
- [ ] Phase 3 涉及的基准场景复跑无 >2% 退化（重构不伤性能）
- [ ] No owner-doc update required: 纯内部重构 + 死代码删除，无行为/契约变化（G8 错误锚点改进已被测试断言覆盖）
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] git commit 完成（Phase 4 独立提交）

### Phase 5 - 收敛复验（延续 360/2279 停止判据）

Status: planned
Targets: 本轮触碰路径相关基准 + JFR 归因

- Item Types: `Proof`

- [ ] 以 `-prof jfr` 对 Phase 3 保留项涉及的基准场景（NFA/状态后端/WindowOperator rocksdb 档）采样，输出热点清单（记录到 plan）
- [ ] 全量基准复跑一轮（fork=1 口径），未触碰基准在 ±3% 噪声带内、触碰基准无 >2% 退化，数字记入 `## Benchmark Rounds`
- [ ] 收敛裁定：对照 360/2279 裁定书 + 本轮 JFR 归因，给出"本轮触碰路径上无 ≥2% 低风险可收割项"或列出新登记候选的明确结论，写入 plan
- [ ] 若复验发现 ≥2% 且低风险的新候选：实施→实测→留舍（每项走 Phase 3 同款纪律），循环至无 ≥2% 项

Exit Criteria:

- [ ] `## Benchmark Rounds` 含 Phase 5 复验轮数据，收敛裁定结论已明确写出
- [ ] JFR 热点清单与候选取舍理由已记录
- [ ] `./mvnw test -pl nop-stream-core,nop-stream-flow,nop-stream-runtime,nop-stream-cep,nop-stream-rocksdb` 全绿
- [ ] No owner-doc update required: 优化迭代不改契约
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] git commit 完成（Phase 5 独立提交）

### Phase 6 - 文档同步与计划收口

Status: planned
Targets: `ai-dev/logs/`、analysis 文档、本 plan

- Item Types: `Proof`

- [ ] owner docs 复核：Phase 2-5 若改变已文档化的契约/模式，同步对应 owner doc；复核结论逐项记录（预期多为 `No owner-doc update required`，缺陷修复均不改变对外契约）
- [ ] `ai-dev/logs/` 收口条目（全部 Phase 摘要 + 留舍/收敛数字）
- [ ] 独立子代理 closure audit（fresh session），evidence 写入 `## Closure`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-core --severity high` 退出码 0（runtime/cep/rocksdb 同样跑）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 文本一致性核对（Plan Status / Phase Status / Exit Criteria / Closure Gates / logs 五处一致）
- [ ] 最终 commit（closure）

Exit Criteria:

- [ ] 上述全部勾选；Closure Evidence 已写入 `## Closure`
- [ ] `Plan Status` 改为 `completed`

## Closure Gates

- [ ] 所有 in-scope confirmed live defects（Phase 2 十一项，含审查新增的 A12）已修复并有回归测试
- [ ] E3/F1/F2 每项有 JMH 前后数据与留舍裁定（保留或 revert+无收益证据）；Phase 5 收敛复验结论已记录（延续 360/2279 停止判据）
- [ ] Phase 4 重构不改变行为（错误锚点改进项已在测试中断言）
- [ ] 全部 5 模块 `./mvnw test` 全绿（收口时复跑）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（与 360/2279 裁定重叠项按其既有归属记录，InputGate 提取等可裁决项的移出已记录理由）
- [ ] 受影响的 owner docs 已同步（逐项复核结论已记录）
- [ ] 独立子代理 closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：closure audit 验证新增测试真实断言行为（非空跑）；新增基准口径真实调用生产代码路径（非 mock 空转）
- [ ] `./mvnw test -pl nop-stream-core,nop-stream-flow,nop-stream-runtime,nop-stream-cep,nop-stream-rocksdb` 全绿
- [ ] checkstyle / 代码规范：import 分组、4 空格缩进、错误码规范符合 AGENTS.md

## Benchmark Rounds

> 口径：JDK 26.0.1 Zulu / JMH 1.33 / `-f 1 -wi 3 -w 2s -i 5 -r 2s` / 同机（macOS arm64）。运行方式见 `nop-benchmark/nop-benchmark-stream/README.md`。

- Round-0（2026-09-28，Phase 1 基线）：
  - F2 口径 `MemoryKeyedStateBench.internalListAdd`：local **33.753 ± 0.116 ns/op**（alloc 232.0 B/op）、rotate **67.602 ± 12.329 ns/op**（alloc 232 B/op 量级）——对照同文件 aggregatingAdd 已优化形态（360 收敛后 local ~7.6ns/24B），余量显著
  - E3 口径 `NfaProcessBench.processEvent -p patternDepth=5`：cheap **3.795 ± 0.014 µs/op**、billable **4.447 ± 0.042 µs/op**（Δ=0.65µs=全部条件工作量；若双重评估成立，billable 档消除后理论可收割 ~其一半）
  - F1 口径 `WindowOperatorProcessElementBench -p backend=ROCKSDB`：TUMBLING **3.994 ± 0.174 µs/op**、SLIDING **24.766 ± 0.853**、EVICTOR **136.7/126.7 µs**（EVICTOR 主导项为已 Deferred 的 D2 列表格式问题，非 F1 目标）
- （后续轮次按 `日期 | 场景 | 前值 | 后值 | Δ% | 变更项` 格式追加）

## Deferred But Adjudicated

### 与 360/2279 既有裁定重叠的审计发现（不再重复实施）

- Classification: `moved to explicit successor ownership`（沿用先行计划的裁定与 successor 归属，本计划不重复实施、不另立新裁定）
- Why Not Blocking Closure: 各项已由先行计划以"JFR 归因 + 实测"双证据裁定（保留/revert/量化关闭/Deferred），裁定书在 `ai-dev/audits/evidence/nop-stream-perf-{360,2279}/convergence*.md`；重复实施会违反已记录的实测结论，部分项还会违反已裁定契约（如 C2 公平 FIFO 语义、C1 的 250ms/150ms 次序契约）
- 对应关系：C1（InputGate 50ms 轮询→360 R2 不实施 + 2279 量化关闭：饱和零吞吐成本，仅空闲延迟小项）；C2（BufferPool 公平信号量/批量许可→2279 量化关闭：畅通边 <2%，批量记账需契约重设计）；C4/F5（每记录 4 次时钟读→2279 watch-only：G52 liveness 语义耦合）；D1（MapState 回退 RMW→2279 F3 裁定生产不可达）；D2（evictor 时间戳 RocksDB O(n²)→2279 F2 Deferred：需列表格式设计，128µs/op 已量化）；D3（checksum 算法→2279 F1 Deferred：读侧 78% 份额=canonical 契约本体，格式 v2 设计）；E1（SharedBuffer.advanceTime 全扫描→2279 Q1/Q1b 台账重构后终态 JFR 无此热点）；E2（NFA 分配池化→360 watch-only：分散无单点）；E4-E7（SharedBuffer 写穿/ScopedId 家族→360 LocalCache Deferred 家族）；F3（RocksDBListState O(n)→360 Deferred：需 merge operator/列表格式设计）；F4-F9（schema 指纹/WriteBatch/MapState 视图等→360 各轮实测带内或未登记为热点）
- Successor Required: `yes`（各项 successor 条件已在上述裁定书中登记）
- Successor Path: `ai-dev/audits/evidence/nop-stream-perf-360/convergence.md` 第四节、`ai-dev/audits/evidence/nop-stream-perf-2279/convergence-r2.md` 第三节

### JobCoordinator / InputGate / CepOperator 整体拆分（G1/G2/G3）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 结构性重构需独立设计与分阶段验证（2278 已完成其零风险搬移切面并显式登记中高风险切面为 Deferred），混入本计划违反单结果面原则
- Successor Required: `yes`
- Successor Path: plan 2278 Deferred 节（FencingEpochManager/RestartBudget/SubtaskLivenessTracker 等切面）+ analysis 文档 G1/G2/G3 条目

### 行为变更类缺陷（A3 终态 savepoint 假 FINISHED、A4 standby 关共享 coordinator、A5 溢出静默旁路、A6 SupervisionLoop 缺 key、B2 restore 非原子、B7 类加载白名单）

- Classification: `moved to explicit successor ownership`（已确认缺陷，不可降级为 watch-only；明确移交 successor，修复语义需 owner 确认）
- Why Not Blocking Closure: 修复会改变用户可见行为/兼容性契约，需要 owner 语义确认后单独立项；全部已在 analysis 文档 P0 表中显式记录并指定 successor 归属，不会丢失
- Successor Required: `yes`
- Successor Path: 与 owner 确认后立独立 plan（记录于 analysis 文档"改进建议与归属"节）

### D4 CheckpointCoordinator 锁内 RPC 外移、A11 锁序文档化

- Classification: `optimization candidate`
- Why Not Blocking Closure: 并发结构重构，收益依赖分布式部署形态；单机基准无法验证，需独立 plan + 分布式场景测试
- Successor Required: `yes`
- Successor Path: successor plan 待建

### 其余 P2 杂项（C3/C5-C8 交换路径微优化、D5-D11 控制面、G10/G12/G14/G16/G18 结构项、H 组 connector 杂项）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 均为 <2% 预估或控制面低频路径（360/2279 已实测同族路径处于 <2% 域或噪声带）；清单保留在 analysis 文档供 successor 复用
- Successor Required: `no`
- Successor Path: n/a（analysis 文档为持久清单）

## Non-Blocking Follow-ups

- D8 RetentionCleaner 元数据级清理、D9 tableExists 缓存、D10 removeTriggerAccumulators 索引化（触发条件：checkpoint 清理成为生产瓶颈时）
- B9 CEP processing-time 定时器取消空实现补全、B10/B11 引用计数防御（触发条件：出现相关现场问题）
- connector 杂项（H 组：死条件、静默吞异常、错误码统一）

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子代理 audit 填写）
- Evidence: （待填写：每条 Exit Criterion / Closure Gate 的 PASS/FAIL + 证据来源）

Follow-up:

- （收口时按实际填写，或明确写 no remaining plan-owned work）
