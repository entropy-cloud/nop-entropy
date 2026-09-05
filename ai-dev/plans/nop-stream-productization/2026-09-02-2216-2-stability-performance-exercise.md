# 分布式稳定性与性能演练（roadmap item 15）

> Plan Status: completed
> Mission: nop-stream-productization
> Work Item: item 15 分布式稳定性与性能演练
> Last Reviewed: 2026-09-02
> Source: `ai-dev/design/nop-stream/distributed-runbook.md`（演练基建 + §5 已知边界）+ `ai-dev/design/nop-stream/composite-scenario-design.md`（C0—C3 验证矩阵，已 100% 落地）+ `ai-dev/bugs/2026-09/2026-09-02-distributed-scenario-engine-defects.md`（item 14 修复清单）+ D-GAP §2.4 F-3（P-REQ-21 不吸收）
> Related: `2026-09-02-2216-1-observability-ops-productization.md`（**建议先行**：本 plan 指标观察面以其交付为优——执行顺序见 Current Baseline 排序裁定）

## Purpose

产出产品级稳定性证据：在 `MiniStreamCluster` 真实多 JVM 基线上执行长时 soak（含状态增长）、chaos（随机 kill 循环 + 按 wire 后端能力裁定的分区模拟）、backpressure 行为观察（消费 item 16 指标面），形成演练矩阵执行报告与瓶颈/缺陷清单（路由 Follow-up 工作项），把「C0—C3 短时正确性基线」升级为「持续运行稳定性基线」。

## Current Baseline

（2026-09-02 live 核对）

- 短时正确性基线已成立：分布式验证矩阵 C0—C3 100% 逐格（item 14 done）——gated 启用态 13/13（S1/S2 基线、kill/recover/fencing、TM 2→3 跨集群恢复、sink 内节流两场景）+ legacy 7/7 绿；演练命令与 runbook §4 一一对应。
- **演练基建可复用范围（诚实边界）**：可复用的是 `MiniStreamCluster`（ProcessBuilder 多 JVM + H2 AUTO_SERVER、kill/restart TM/JC API）、`io.nop.stream.fraud.scenario` 场景包（S1/S2 + `ThrottledScenarioSinks` 节流装置）、gated 属性 `-Dnop.stream.test.multi-jvm.enabled=true`、`preserve-artifacts` 产物保留（`_tmp/mini-stream-cluster/<runId>/`）。**不可复用的缺口**：S1/S2 source 均为有界 fixture（`ReplayableCdcSourceFunction` 有界事件序列回放、`DirectoryFileSourceFunction` 目录单遍读取），无持续/状态增长负载生成能力；C1 类测试为定点单次 kill，无随机时点多轮 chaos 循环驱动；无周期指标采样装置——本 plan Phase 2 的交付对象。
- 已知边界（runbook §5，演练须对照）：① item 28 遗留——remote-deploy 全对订阅 + 1024 槽队列满后 dispatch 线程可能永久阻塞（有界 fixture、低速率未触发，**真实大流量下归属 item 28 收敛**；「演练 hang 优先核验此项归属」）；② item 28——JDBC 后端 `loadRetainedEpochManifests` override 缺失（当前演练均 LocalFile 存储，路径未触发）；③ item 25——manifest 无 checksum/version 字段；④ 2PC sink P>1 引擎硬门禁（演练管线一律有效并行度 1）。
- 指标观察面：runbook §5 明示「背压无直接指标（item 16 前提）……稳定性量化属 item 15」。
- 2026-09-02 两份 bug note（LOCAL 6 项 + 分布式 7 项引擎缺陷）已修复收口，无已知未修复阻塞。
- **执行顺序裁定**：本 plan 建议在 item 16（plan `2026-09-02-2216-1`）之后执行，以消费其指标面（本目录文件名序即执行序）。该顺序是**偏好而非硬前置**：若先于 item 16 执行，Phase 1 必须按 live baseline 裁定观察模式（runbook §5 代理观察降级 + 指标缺口记 Follow-up 候选，显式记录），不得静默等待。

## Goals

- 演练驱动基建交付：持续/状态增长负载生成、随机 kill 循环驱动、周期指标采样装置（Phase 2）。
- 演练矩阵（soak/chaos/backpressure）全部执行并留档：每格有执行记录、指标（或代理）采集、结局判定。
- 演练矩阵执行报告落入 `ai-dev/analysis/{YYYY-MM}/`（按执行当月目录；复现命令 + 观察数据 + 结论）。
- 瓶颈/缺陷清单：引擎缺陷按 roadmap 自进化规则追加 Follow-up 工作项（非就地修复）；演练基建自身缺陷就地修复。
- item 28 遗留核验取得观察性证据（持续流量下是否触发 hang），显式记录、不静默绕过、不越权修复。

## Non-Goals

- SLO 定义与基准测试框架建设（roadmap Out of scope）。
- 引擎缺陷的就地修复（路由 Follow-up；与 items 13/14「就地修复」模式不同——本 item 交付物是证据与清单，roadmap 明文「瓶颈/缺陷清单 → Follow-up 工作项」）。
- 性能调优实施（只观察与记录，不改引擎性能行为）。
- 跨版本升级兼容测试（P-REQ-21 已 defer 且 F-3 裁定无吸收对象；revisit 触发 = 首次格式版本递增/首个发布版本，D-GAP §2.4）。
- K8s/容器编排环境演练（P-REQ-15 K8s defer；基线 = MiniStreamCluster 真实多 JVM，roadmap Cross-cutting 既定）。
- 新指标开发（item 16 交付；观察面缺口只记 Follow-up 候选）。

## Scope

### In Scope

- 演练驱动基建（负载生成 + chaos 驱动 + 采样装置）构建及其自身测试。
- 演练方案（矩阵定义、时长/速率/状态增长参数、结局分类、通过判据、资源预算）。
- soak / chaos / backpressure 三类演练执行 + 指标（或代理）采集。
- 执行报告 + Follow-up 追加 + runbook 已知边界证据更新。

### Out Of Scope

- 上列 Non-Goals 全部。

## Execution Plan

### Phase 1 - 演练方案设计（报告文件落档）

Status: completed
Targets: `ai-dev/analysis/{YYYY-MM}/`（执行当月目录；报告文件**本 phase 创建**，含矩阵定义章节，Phase 2—4 追加结果）

- Item Types: `Decision`

- [x] 创建演练报告文件（命名遵循 `00-analysis-writing-guide.md`），落矩阵定义：soak（时长下限、数据规模与状态增长曲线、检查点频率）、chaos（随机 kill 的频率/时长分布、kill JC/TM 变体、恢复断言、迭代轮数；网络分区模拟按 wire 后端能力正式裁定——SysDao JDBC 轮询后端可模拟的等价故障形态 vs 不可模拟项显式记录）、backpressure（节流档位、观察窗口、释放判据，扩展 `ThrottledScenarioSinks` 用法）
- [x] 定义每格通过/失败判据（无重复无丢失、checkpoint 持续推进、恢复后终态完整、内存/句柄无持续增长泄漏信号）与观察指标清单（映射 item 16 交付的指标名；若尚未交付，裁定代理观察模式并显式记录）
- [x] 定义**演练格结局分类**：`pass` / `fail`（新缺陷，路由新 Follow-up）/ `triggered-known-defect`（命中既有已知边界如 item 28，证据记录 + 归属路由确认）——后两者的闭环条件：证据锚点 + Follow-up/归属记录落档即视为该格闭环，不算通过也不阻塞 phase 收口；每格的最终判定必须对应一次**完整参数执行的留档 run**（演练基建修复后按原参数重跑是合法闭环路径；降档——缩短时长/降低速率——后标 pass 不允许）
- [x] 定义资源预算与产物规范：演练产物一律 `_tmp/`（禁系统 `/tmp`）且演练命令一律启用 `preserve-artifacts`（MiniStreamCluster 默认删除 runDir，不保留则 closure 抽样无据）；gated 命令模板与 runbook §4 对齐；单演练时长上限与总预算
- [x] item 28 遗留核验纳入 soak 观察项：持续较高速率下的队列/通道行为观察点定义（证据采集规范，非修复）
- [x] **观察模式再裁定钩子**：若 item 16 在本 plan 执行中途（Phase 1 之后、Phase 3 之前）落地，允许一次性升级观察模式并显式记录，或显式决定保持代理观察以保 run 间一致性——二选一落档，不得默认沿用

Exit Criteria:

- [x] 报告文件存在于 `ai-dev/analysis/{YYYY-MM}/`，矩阵每格有：场景（S1/S2/组合）、参数、gated 复现命令、通过判据、观察指标、结局分类——全部 repo/process-observable（Phase 1 完成时即已持久化，非 Phase 4 补写）
- [x] 网络分区模拟的三态裁定（可模拟形态/不可模拟项/替代观察代理）落档，依据 wire 后端能力
- [x] 指标观察模式裁定落档（直接指标 vs 代理观察，后者附缺口 Follow-up 候选——不得静默）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] No owner-doc update required（本 phase 只创建 analysis 文档，不改 owner doc；runbook 更新在 Phase 5）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 演练驱动基建

Status: completed
Targets: `nop-stream/nop-stream-fraud-example/`（场景包扩展：负载生成/chaos 驱动/采样装置，随场景资产落位）

- Item Types: `Fix | Proof`

- **边界声明**：本 phase 三装置均为测试域工具（随场景资产落在 fraud-example），不新增引擎能力、不触碰 main 代码路径语义。
- [x] 持续/状态增长负载生成装置：可配置速率/总量/状态增长曲线的 source 驱动（扩展现有 S1/S2 fixture 模式，非新引擎能力）——`ExerciseLoadGenerator`（S2 行级 / S1 CDC 事件级，速率=emitDelay/lineDelay、总量=durationSec、状态增长=每 N 行新 keyed 用户；期望集由同一生成计划推导）
- [x] 随机 kill 循环驱动装置：随机时点、多轮、kill JC/TM 变体（复用 `MiniStreamCluster` kill/restart API），每轮自动断言 fencing 严格递增 + 恢复后无重复无丢失——`ChaosKillPlanner`（种子化可复现时序 + TM kill/分区等价分配）+ 联合入口内 kill 循环（每轮 fencing 严格递增断言 + 恢复收敛终局断言；HA JC 变体经租约表断言）
- [x] 周期指标采样装置：按 Phase 1 裁定的观察模式采集（直接指标或代理观察），产物序列化存 `_tmp/`——`ExerciseSampler`（JC /metrics 直接指标 + msg_queue 深度 + durable epoch + retained manifests + 输出行数 + 进程存活 → `samples.jsonl` + 序列分析函数）
- [x] **参数化联合入口**（gated 测试类/运行器）：时长/轮数/速率/场景等参数取自 Phase 1 矩阵定义，soak/chaos/backpressure 演练格共用此入口执行——Phase 3/4 的「执行」即调用本入口，不另行拼装一次性测试——`TestStabilityExerciseMultiJvm`（6 方法 = 6 矩阵格；`-Dexercise.*` 参数化；preserve-artifacts 强制；失败格也写 run-summary.json 留证）
- [x] 三装置各自的单测/可重跑验证（装置行为可独立验证，不依赖完整演练才暴露问题）

Exit Criteria:

- [x] 三装置 + 参数化联合入口落码且各有独立验证用例（列出用例名——**新功能必有测试**）：`TestExerciseLoadGenerator`（8）、`TestExerciseSampler`（6）、`TestChaosKillPlanner`（5）、`TestSteppedThrottleSinks`（7，BP 档位装置）、`TestExerciseParams`（3，参数 fail-fast）
- [x] **端到端验证**：联合入口以缩比参数完整跑通一轮短时演练（负载生成 + kill 驱动 + 采样三装置同轮工作），证明可驱动完整路径——gated smoke：soak 20s 完整收敛 exactly-once（期望集逐行命中）；chaos 1 轮 kill tm-1 → fencing 1→2 + 恢复断言 + 三装置产物（samples.jsonl/chaos-events.jsonl/run-summary）全留档
- [x] **无静默跳过**：装置对配置非法值（速率 ≤0、轮数 ≤0 等）显式报错，不静默使用默认值——`TestExerciseParams` + 各装置单测 + live 证明（durationSec=60 < kill 调度需求 65s → 拒绝执行）
- [x] `./mvnw test -pl nop-stream/nop-stream-fraud-example -am` 全绿（105/0/0 + 上游模块全绿）
- [x] owner-doc 裁定：若新增 gated 演练测试类，runbook §4 演练表（或 item 16 迁移后的 owner doc 落点）同步登记——runbook §5（现演练表章节）登记 EX 演练矩阵行（参数化联合入口 + 三装置 + 判据指向演练报告）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - soak 演练执行

Status: completed
Targets: `_tmp/`（产物）、演练报告文件（追加结果）

- Item Types: `Proof | Fix`

- [x] 执行长时 soak（≥ Phase 1 定义的时长下限）：S1/S2 场景持续运行 + 状态增长，指标（或代理）周期采集存档——SOAK-1（600s S2 12000 行/120 用户）/SOAK-2（600s S1 6000 事件/60 窗）/SOAK-3（180s 200 行/s）全参数执行，samples.jsonl 180/180/96 条留档
- [x] 观察项执行：结果完整性、checkpoint 推进、状态增长与恢复行为、资源趋势、item 28 遗留核验观察点——item 28 三格证据合并定量（阈值 ~800—1000 条、停摆签名、无回压传导、队列水位代理曲线），归属确认落档报告
- [x] 演练基建缺陷（若有）就地修复（pump 锚定/APPEND CREATE/泄漏启发式，见报告）；引擎缺陷记录缺陷条目（现象、复现命令、日志/产物锚点 = 报告 Phase 3 节三行 + runId 产物），不就地修

Exit Criteria:

- [x] soak 至少一轮完整执行且产物留档（`_tmp/` 路径 + 报告引用）；每格按 Phase 1 结局分类判定——`triggered-known-defect`（如 item 28 hang 触发）以「证据 + 归属路由确认」闭环，不算通过也不阻塞本 phase 收口；不降档重跑粉饰为 pass（基建修复后原参数重跑合法，见 Phase 1 结局分类定义）——三格均 triggered-known-defect(item 28)，证据+归属确认落档报告 Phase 3 节
- [x] **hang 处置显式规则**：soak 出现 hang 时，先按 runbook §5 ① 核验 item 28 归属并留证，再判定新缺陷与否（Phase 4 同此规则）——停摆签名四要素 + RemoteInputChannel 日志锚点 + 健康对照，判定为 item 28 已知边界非新缺陷
- [x] 失败/触发格全部形成缺陷条目或归属路由记录，无静默丢弃（三格归属 item 28，报告含 runId/复现命令/观察数据）
- [x] **端到端验证**：演练本身即端到端（部署 → 多 JVM 运行 → kill/恢复 → sink 终态断言），断言与 runbook §4 同源
- [x] owner-doc 裁定：本 phase 基建就地修复若触及已文档化行为则同步 runbook（否则显式记录 No owner-doc update required）——No owner-doc update required（修复均为演练基建内部行为，runbook §7 更新在 Phase 5）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - chaos 与 backpressure 演练执行

Status: completed
Targets: `_tmp/`（产物）、演练报告文件（追加结果）

- Item Types: `Proof | Fix`

- [x] chaos：随机 kill 循环（≥ Phase 1 定义的轮数，随机时点，含 kill JC/TM 变体），每轮断言 fencing 严格递增 + 恢复后无重复无丢失；可模拟的分区等价故障形态执行，不可模拟项显式记录；经 Phase 2 参数化联合入口执行——CHAOS-1 8 轮全执行留档（轮 1—2 recovered / 轮 3—8 no-rotation：recovery cap 耗尽证据，系列判定 fail）；CHAOS-2 2 轮租约翻转 ✓（分区裁定替代代理验证）
- [x] backpressure：节流档位递增 + 观察（checkpoint 推进、吞吐、队列水位——直接指标或代理），释放后终态完整性断言（C3 模式扩展）；经 Phase 2 参数化联合入口执行——BP-1 三档全执行：50ms 档推进 ×18（C3 语义成立）+ 200/500 档 ×0（jam 阈值命中），释放后终态断言执行（不收敛→判 triggered）
- [x] 同 Phase 3 的缺陷处理纪律（基建就地修、引擎路由 Follow-up）与 hang 处置规则（hang → 先核验 item 28 归属留证 → 再判定新缺陷与否）——驱动装置加固 ×3 就地修 + 原参数重跑；引擎现象全部归因 item 28（含控制面继发失效新证据）

Exit Criteria:

- [x] chaos ≥ Phase 1 定义的轮数全部执行留档，每轮 fencing/终态断言结果记录；结局分类逐格判定（同 Phase 3 规则）——8+2 轮全留档（chaos-events.jsonl 逐轮 fencing/租约/分区字段），CHAOS-1 fail（item 28 根因继发）/CHAOS-2 triggered
- [x] backpressure 观察数据（含指标/代理数值序列）留档，「节流期间 checkpoint 持续前进 + 释放后终态完整」有量化或留档证据——档位序列量化留档（50ms=18 次 vs 200/500=0 次）；释放后终态断言执行且失败证据留档（jam）
- [x] 分区模拟裁定项的执行情况逐条对账（执行了什么、没执行什么、为什么）——报告 Phase 4 节三态对账（SIGSTOP 轮 7—8 执行 ✓、不可模拟三项显式 ✗、租约代理 CHAOS-2 ✓）
- [x] owner-doc 裁定：本 phase 基建就地修复若触及已文档化行为则同步 runbook（否则显式记录 No owner-doc update required）——No owner-doc update required（驱动装置内部行为，runbook §7 更新在 Phase 5）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 执行报告收口与缺陷路由

Status: completed
Targets: 演练报告文件、`ai-dev/design/nop-stream/distributed-runbook.md`（或其 item 16 迁移后的 owner doc 落点）、`ai-dev/backlog/nop-stream-productization-roadmap.md`

- Item Types: `Fix | Follow-up`

- [x] 报告收口：矩阵 × 结果逐格汇总（复现命令、观察数据摘要、结局分类、结论）——Phase 1 定义与 Phase 3/4 结果同档对账（报告「收口汇总」节 6 格对账表 + Status → resolved + Conclusion）
- [x] 瓶颈/缺陷清单：引擎缺陷按 roadmap Rules 追加 Follow-up 工作项（编号顺延、来源标注本 plan）——Follow-up 31（item 28 证据锐化 + 修复范围扩展）+ 32（观察面缺口）；确认无缺陷的观察项显式记录「未复现/无异常」（HA 租约 failover / 50ms 档 C3 语义 / retained manifests 有界 / fencing cap 耗尽前严格递增）
- [x] runbook §5 已知边界按新证据更新（item 28 两项核验结果等）；若 Phase 2 新增了 gated 演练且 §4 未同步，此处补齐——§7 item 28 条目（定量触发证据 + 继发 cap 失效 + 复现锚点 + hang 判定路径）+ Stage-31 再次核验 + 背压条目 BP-1 量化补记；§5 EX 行已在 Phase 2 登记
- [x] roadmap 头部 Last updated 同步（Follow-up 追加时）——2026-09-03 item 15 done 条目 + Follow-up 31/32 追加（末尾顺延、todo、来源标注）

Exit Criteria:

- [x] 报告矩阵与 Phase 1 定义的每格一一对应，无缺格；每格结论可追溯到 `_tmp/` 产物锚点（6 runId 全 preserve-artifacts 留档）
- [x] 全部引擎缺陷已落 Follow-up 工作项（或显式记录无缺陷）；无缺陷被静默丢弃；`triggered-known-defect` 格的归属路由全部确认（item 28 / 31 / 32）
- [x] roadmap 修正符合自进化规则（追加仅在末尾、状态 todo、来源标注）（31/32 末尾追加）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（默认态；演练基建就地修复后回归）——十模块 0 failures（927+105 等全绿）
- [x] 独立 closure：报告与 live 产物抽查一致性核验（抽 ≥2 格对照 `_tmp/` 产物/日志——依赖 Phase 1 的 preserve-artifacts 规范）——独立子 agent closure audit 执行（见 Closure 节 evidence）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 演练矩阵（soak/chaos/backpressure，含分区裁定项）100% 执行且逐格留档（结局分类三态之一，无未判定格）——6/6 格全参数（SOAK-1/2/3 + CHAOS-1/2 + BP-1），runId 见报告收口表
- [x] 通过/失败/触发已知缺陷逐格判定；失败与瓶颈全部路由 Follow-up 或记录「未复现」证据（1 fail + 5 triggered → item 28/31/32；无异常项显式记录）
- [x] item 28 两项遗留核验取得显式证据记录（未静默绕过）——①队列满阻塞：SOAK-3 定量触发（阈值/签名/日志锚点）；②JDBC Stage-31：6 格均 LocalFile 路径未触发、runbook §7 再次核验记录
- [x] 演练驱动基建三装置落码且有独立验证用例——29 个装置单测（5 个测试类：8+6+5+7+3，closure audit 复核一致）默认态全绿
- [x] 执行报告完整落入 `ai-dev/analysis/{YYYY-MM}/` 且复现命令可执行——`2026-09/2026-09-03-distributed-stability-exercise-report.md`（resolved；gated 命令 = runbook §5 EX 行模板）
- [x] runbook §5（及 §4 若需）与新证据一致——§5 EX 行登记（P2）+ §7 item 28/Stage-31/背压三条目证据更新（P5）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（默认态）；gated 演练命令可复现——十模块 0 failures；6 格 gated 命令本轮实际执行即复现路径
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0（就地修复可能触及 main 代码，纳入门禁）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Closure 段完成后复验）
- [x] 独立子 agent closure audit 完成且 evidence 写入 Closure 段（CLOSURE-APPROVED，task ses_f9bd70681ffeIptxAnIo3efADX，2026-09-03）

## Deferred But Adjudicated

### P-REQ-21 跨版本升级兼容测试基建

- Classification: `out-of-scope improvement`（D-GAP §2.1 defer 裁定 + F-3 维持建议级）
- Why Not Blocking Closure: 无已发布版本、无格式版本递增——升级测试当前无对象；revisit 触发（首次格式版本递增/首个发布版本）时再评估执行面归属
- Successor Required: no
- Successor Path: revisit 触发时按 D-GAP §2.4 评估（可落 item 15 successor 或独立 Follow-up）

## Non-Blocking Follow-ups

- 观察面缺口（若指标面不足以量化某演练格且已降级代理观察）：记录为 Follow-up 候选，随缺陷清单路由；不影响本 plan 以代理观察收口。

## Closure

Status Note: 6/6 演练格全参数执行并留档（结局：1 fail 根因 item 28 继发 + 5 triggered-known-defect(item 28)，无降档、无静默丢弃）；三装置演练基建 + 参数化联合入口落码并有 29 个独立单测；引擎缺陷全部路由（item 28 证据锐化 → Follow-up 31、观察面缺口 → Follow-up 32）；runbook §5/§7 与 roadmap 自进化同步；默认态全模块绿 + 四工具门禁 exit 0。分布式持续运行稳定性基线的成立被 item 28 阻塞（显式结论，非本 plan 缺口——本 plan 交付物即证据与清单）。
Completed: 2026-09-03

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，research-only，无实现参与）——task `ses_f9bd70681ffeIptxAnIo3efADX`
- Evidence:
  - **CLOSURE-APPROVED**（无 Blocker；3 Minor 记录性：单测计数 32→29 已修正；roadmap 预写 audit 结论在 Closure 填入后一致化；doc-links 1 条 warning 非 error、exit 0）
  - 逐区核验 PASS：P1 报告（6 格定义/判据/三态结局分类/分区三态/观察模式/预算规范全在档）；P2 代码+测试（11 文件 live 存在、gated 入口 6 方法、5 单测类默认态非 gated=29 @Test 与 plan 清单一致）；P3/4 live 产物抽查（6 runId samples.jsonl 非空 180/180/96/198/209/36 条、run-summary verdict 全部诚实 criteria-violated；报告数字与原始产物逐项核对精确匹配：SOAK-1 queue 163→22275/9 epochs/6-of-120 rows/Interrupted×3/abort×114、SOAK-3 2 epochs+queue 41662+0 输出、CHAOS-1 8 轮（1—2 recovered fencing 1→2→4，3—8 no-rotation 停在 4，JC 日志含 `Starting global recovery #3 (cap=3)`）、CHAOS-2 租约 coordinator-0:1→coordinator-2:2→coordinator-3:3 且 fencing 1000000 不变、BP-1 档位序列 50ms→18 advances/+6 rows vs 200/500ms→0）；P5 文档（roadmap 31/32 末尾追加 todo+来源标注、item 15 done、runbook §5 EX 行+§7 三条目）；gates（doc-links 0 errors/hollow 0 findings/plan-checklist exit 0）；anti-hollow（联合入口真实 spawn MiniStreamCluster 驱动三装置、参数 fail-fast、preserve-artifacts 强制）；honesty（1 fail+5 triggered 归因 28/31/32、无 pass 越权主张、P-REQ-21 deferred 完整）。

Follow-up:

- 引擎缺陷路由后的 Follow-up 工作项清单：**roadmap item 31**（item 28 证据锐化与修复范围扩展：通道收敛 + 队列满语义 + stall 恢复预算区分——解锁后按本 plan 报告以原矩阵参数重跑即得稳定性基线升级）+ **roadmap item 32**（观察面上收：TM 指标上报通道 + 队列水位直测 gauge）。除上述两项外 no remaining plan-owned work。
