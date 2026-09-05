# 3 nop-stream-runtime 模块审计（roadmap item 8）

> Plan Status: completed
> Last Reviewed: 2026-09-01
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 8（Phase M 第二个审计项，deps: item 6）；Phase M 审计统一模式（roadmap Work Items 分组说明）
> Related: `2026-09-01-0938-1-design-productization-gap-analysis.md`（item 6，**本 plan 的执行前置依赖**，其 Phase 3 产出 runtime 模块审计重点输入）；`2026-09-01-0938-2-core-module-audit.md`（前序 sibling 审计，其审计方法论与报告结构可复用；plan 0753-3 的 Flink/Beam 补评为 Phase M 级裁定、已在 item 7 plan 落定，本 plan 仅引用其结论）
> Mission: nop-stream-productization
> Work Item: roadmap item 8

## Purpose

对 `nop-stream-runtime` 按产品标准完成模块审计并收口：验证 2026-05-20 / 2026-06-30 两份历史审计中 runtime 相关发现的整改收口、执行产品化视角新增审计（含 D-GAP 下发的 runtime 审计重点，分布式执行/HA/supervision loop/数据面核心路径）、复查重复代码与核心逻辑优雅性/可靠性；小缺陷就地修复（含回归测试），大缺陷转为 roadmap Follow-up 工作项。

## Current Baseline

（2026-09-01 live 核对）

- `nop-stream/nop-stream-runtime`：65 个 main Java 文件 / 167 个 test Java 文件（`find -name "*.java"` 计数；`*Test*` 命名 160 个）——main 包：`checkpoint`（CheckpointCoordinator/JdbcCheckpointStorage/LocalFileCheckpointStorage 等）、`cluster`（集群注册/租约）、`coordinator`（JobCoordinator）、`execution`（GraphModelCheckpointExecutor/EmbeddedDistributedExecutor/RpcDistributedExecutor/SupervisionLoop）、`operators`、`rpc`（IStreamTaskRpcService 等）、`source`、`taskmanager`（TaskManager）、`transport`（wire codec + RemoteInputChannel/RemoteResultPartition 等 Remote* 类）。**归属澄清**：`TaskExecutor`/`Task`/`SubtaskTask`/`RecordWriter`/`RecordReader`/`InputGate`/`ResultPartition` 位于 `nop-stream-core`（2026-09-01 live 核实，与设计文档 README 1.2 表的包归属描述存在历史 drift）；05-20 报告中的 `BarrierAligner` 已随死代码清理删除（live 无此类）
- 独立进程入口 `JobCoordinatorMain`/`TaskManagerMain` 与 `MiniStreamCluster` 位于 test scope（multijvm/launch 包）；gated 多 JVM 测试经 `@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")` 启用（如 `TestMultiJvmExactlyOnceRecovery`/`TestMultiJvmCoordinatorFailover`）
- 前序 production roadmap 已 shipped 的 runtime 侧能力（roadmap Current baseline）：跨 JVM 控制面 RPC（fencing token 统一）、数据面 wire codec（SysDao/Pulsar/Kafka）、HA leader election、region-based failover、drain/reconnect、unaligned checkpoint、多并发 checkpoint、`MiniStreamCluster` 多 JVM 测试基建
- 2026-05-20 duplicate-code audit（Status: resolved）：runtime 相关组 = §3（runtime 大面积死代码，含 BarrierAligner/旧 CheckpointCoordinator 等 10 文件 2,854 行——**整改 live 收口状态尚未核验**）+ §5（TimerService 实现重复，实现层位于 runtime）+ §2 的 runtime 侧（CepWindowOperator）；§1/§4/§6/§8/§9 归属 item 7（core）、§7 模块级空壳由 item 7 统一核验
- 2026-06-30 code audit（全 9 模块，**无「分布式执行/HA/checkpoint 协调」命名章节**——章节为包结构/实现完整性/测试覆盖/关键问题/对比/总结）：runtime 相关发现的 seed 清单（本 plan Phase 1 核对基准）：§2.2 runtime 项（`JdbcCheckpointStorage` catch 块 return null ×15、`JdbcClusterRegistry` ×11、`LocalFileCheckpointStorage` ×8、`TaskManager` 心跳/lease/轮询间隔硬编码、`JdbcCheckpointStorage` 表名硬编码）+ §6.2 #5（runtime→cep 幽灵依赖，需核对已移除）+ §6.3 P1（真实跨 JVM E2E 缺口）；**派生规则**：报告中其余发现按「涉及文件位于 `nop-stream-runtime`」准则纳入；**跨模块规则**：位于 core 但影响 runtime 路径的发现（如 §2.2/§4.1 的 `InputGate` 项）本 plan 只记录并路由（归属 item 7 或 Follow-up），不在本 plan 修复
- 2026-08-06 audit baseline 方法论 + 其 remediation plans（`ai-dev/plans/nop-stream-production/2026-08-04-2300-1/2/3-*.md`）已收口结论——本 plan 需复核对 runtime 成立
- 工具：`ai-dev/tools/scan-hollow-implementations.mjs`（high/critical 发现退出码非 0，无白名单机制）；`ai-dev/tools/check-nop-stream-invariants.mjs`（nop-stream 不变式 CI fail-fast 门禁，`.github/workflows/maven.yml`）
- D-GAP 报告：**尚未存在**，由 plan `2026-09-01-0938-1`（item 6）产出——本 plan 执行时从 roadmap item 6 done 记录中取其路径（写回规则已明确含报告路径），消费其 runtime 审计重点条目（预期含 P-REQ-20 checkpoint 版本化/校验和等裁定中归属 runtime 证据面的核对项，以 D-GAP 实际输出为准）；若路径缺失或 item 6 未 done，本 plan 置 `blocked` 并上报 mission engine
- 验证基线：roadmap Cross-cutting concerns 约定 `./mvnw test -pl nop-stream -am -T 1C` 全绿；分布式验证真实性约束（MiniStreamCluster 真实多 JVM，禁止单进程模拟充当分布式证据）

## Goals

- 整改收口验证：2026-05-20 runtime 相关组（§3/§5/§2 runtime 侧）与 2026-06-30 runtime 相关发现（Current Baseline seed 清单 + 派生规则）逐组/逐项核对 live 状态（landed / partial / regressed），每组/项有证据指针
- 产品化新增审计：D-GAP runtime 审计重点逐条消化（对照勾销清单无遗漏）+ 分布式核心路径（supervision loop、region failover、fencing、数据面传输、checkpoint 协调）优雅性/可靠性审计 + 空壳/静默跳过扫描 + 测试覆盖抽查（含 gated 多 JVM 测试盘点）
- 缺陷处置：单 plan 范围内可收敛的小缺陷就地修复（每个修复配 focused 回归测试；触及分布式路径的修复需 gated 多 JVM 验证）；跨模块/大规模缺陷转 roadmap Follow-up 工作项（编号顺延、来源标注本 plan）。小/大缺陷判定准则与 item 7 一致：修复限于 runtime 模块内、不改公共契约、无需新测试基建 → 小缺陷；否则 → 大缺陷转 Follow-up
- 审计报告落地 `ai-dev/analysis/`（遵循 `00-analysis-writing-guide.md`；若 item 7 core 审计报告已存在则结构对齐，否则按 writing guide 独立成篇）并完成 roadmap item 8 写回

## Non-Goals

- 跨模块重构（需 Follow-up 立项）
- 修复位于 core 但影响 runtime 路径的发现（如 `InputGate`）——只记录并路由（item 7 或 Follow-up），item 7 已裁定的 Flink/Beam 补评结论本 plan 仅引用不重复裁定
- 实施 D-GAP go 裁定项的功能开发（如 checkpoint 版本化实施、升级兼容测试建设——属其归属执行项；本 plan 只核验现状与提供证据）
- 新分布式特性开发、性能压测（item 15）
- core/cep/connectors/rocksdb/flow/fraud-example 模块审计（items 7/9/10/11）
- 2026-05-20/2026-06-30 报告正文的历史重写

## Scope

### In Scope

- 审计与报告：`nop-stream/nop-stream-runtime/` 全模块 + 新增审计报告一份（`ai-dev/analysis/{执行当月}/`，命名遵循 writing guide）
- 修复：runtime 模块内小缺陷（含 focused 测试；触及分布式路径时按 gated 测试约定处理）
- 修改：`ai-dev/backlog/nop-stream-productization-roadmap.md`（仅 Follow-up 追加 + Last updated + closure 后 item 8 写回）
- 修改（条件性）：`ai-dev/tools/scan-hollow-implementations.mjs` 检测规则——仅当 hollow scan 误报经核实源于工具检测缺陷时允许修正（误报处置路径的载体）
- 修改（条件性）：受修复影响的最小 owner-doc 同步（见 Phase 3；与 Out Of Scope 的「不顺手重写」边界见该节修正表述）
- 只读输入：两份历史审计报告、2026-08-06 baseline、D-GAP 报告、core 审计报告（item 7，如已完成的报告结构/方法论复用）、`~/sources` 竞品源码（如需对照）

### Out Of Scope

- 其他 9 个子模块的审计与修复
- `ai-dev/design/nop-stream/` 的结构性重写与历史回写（若修复致 owner doc drift，仅做 Phase 3 约定的最小事实同步，不扩写、不重构）
- 长时稳定性/chaos 演练（item 15）

## Execution Plan

### Phase 1 - 历史审计整改收口验证

Status: completed
Targets: `nop-stream/nop-stream-runtime/`、审计报告 Phase 1 章节

- Item Types: `Proof`

- [x] 2026-05-20 audit 中 runtime 相关组（§3/§5/§2 runtime 侧，组清单见 Current Baseline）逐组核对 live 状态（已清除 / 已收编（原死代码被生产接线启用，如 §3 的 CheckpointCoordinator/存储实现已被协调器与分布式执行器接线）/ 部分残留 / 回潮新增），每组附源码路径或 absence 证据；其余组显式标注「归属 item 7/9」不在本表核对
- [x] 2026-06-30 code audit runtime 相关发现逐项核对（seed 清单见 Current Baseline + 「涉及文件位于 nop-stream-runtime」派生规则，执行时先落完整核对表再逐项核对）：landed / partial / regressed 三态 + 证据指针；位于 core 的发现（如 `InputGate` 项）按跨模块规则记录并路由，不纳入本表修复
- [x] 复核「2026-08-04-2300-1/2/3 remediation plans 已收口」结论对 runtime 模块成立（抽查其 runtime 侧修复点 live 存在性）
- [x] partial/regressed 项归类：按小/大缺陷判定准则进 Phase 3 或 Follow-up 候选，逐项记录理由

Exit Criteria:

- [x] 报告 Phase 1 章节含 05-20 runtime 相关组核对表 + 06-30 runtime 发现完整核对表（先列全表再逐项核对，每行三态 + 证据指针，无「未核对」空行；非 runtime 组的归属标注与跨模块路由记录在表外显式）
- [x] remediation plans 收口复核结论落地（成立/不成立 + 抽查点）
- [x] 全部 partial/regressed 项有归类结论（#4 TaskManager 心跳/lease 不可配置 → 小缺陷 R-1 进 Phase 3；#5 表名未配置化 → watch-only residual optimization candidate）
- [x] No owner-doc update required（纯核验章节）
- [x] No new test required: 纯核验章节，无代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 产品化视角新增审计

Status: completed
Targets: `nop-stream/nop-stream-runtime/`、审计报告 Phase 2 章节

- Item Types: `Proof`

- [x] 消化 D-GAP 报告的 runtime 审计重点清单：先对照 D-GAP 报告 runtime 条目建立勾销清单，再逐条执行对应审计/核验动作（含裁定归属 runtime 证据面的 P-REQ 核对项，如 P-REQ-20；D-GAP 判定「无额外重点」时显式记录该结论）；closure 前对照清单确认无遗漏条目
- [x] 分布式核心路径优雅性/可靠性审计：supervision loop、region failover 恢复路径、fencing token 传播、数据面 wire codec 错误处理、checkpoint 协调器并发正确性（多并发/unaligned checkpoint 路径）的重复代码复查与边界条件审计
- [x] 运行 `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high`，high/critical 发现逐条核实（真实空壳 vs 误报）；**误报处置路径**：修正代码模式或修正工具检测规则——「仅记录不改」不合法（退出码 0 为不可降级硬门禁）
- [x] 测试覆盖抽查：runtime 主要包（taskmanager/transport/coordinator/execution/rpc/checkpoint/cluster/operators/source）各抽 ≥2 个代表性测试命中确认 + 按行数 top-5 文件的直接测试覆盖检查；gated 多 JVM 测试（`nop.stream.test.multi-jvm.enabled` 启用族）盘点——条目数、覆盖场景（kill/failover/rescale/exactly-once）、是否有长期未跑的 gated 测试
- [x] checkpoint 存储实现健壮性抽查（LocalFile/JDBC 双实现的原子性/错误处理/null 返回路径），为 D-GAP 中 P-REQ-20 相关裁定提供 runtime 侧证据补充

Exit Criteria:

- [x] D-GAP runtime 审计重点勾销清单落地（逐条消化记录 + 对照 D-GAP 报告 runtime 条目无遗漏；含显式「无额外重点」结论路径）（§2.1 两项重点四分项全消化）
- [x] hollow scan 退出码记录 + high/critical 发现逐条核实结论（真实发现进 Phase 3 或 Follow-up；误报处置动作已执行或已按缺陷流程进入 Phase 3，非仅记录）（exit 0、0 high/critical；79 medium 全部人工判定为合法语义，唯一行为级例外 R-7 已列修复）
- [x] 分布式核心路径审计发现逐条带源码锚点（§2.2 双表 + 执行者复核记录）
- [x] 测试覆盖抽查结论表落地（每包 ≥2 代表性测试命中情况 + top-5 文件覆盖结论）（§2.5）
- [x] gated 多 JVM 测试盘点表落地（条目数 + 覆盖场景 + 长期未跑项状态）（§2.5：6 用例/3 类，无失修项；rescale/unaligned 多 JVM 缺口记录为 Phase S 输入非失修）
- [x] checkpoint 存储健壮性抽查结论表落地（LocalFile/JDBC 各含错误处理/原子性路径结论 + P-REQ-20 证据小节）（§2.6 + §2.1 ①b/①c）
- [x] No owner-doc update required（同 item 7 原则；D-DRIFT-2 方向裁定为「补字段落地转 Follow-up」，checkpoint-design §2.6 字段表维持待 Follow-up 落地时同步——本 plan 不改设计文档正是裁定的组成部分）
- [x] No new test required: 纯审计章节，无代码变更（Phase 3 修复的测试要求在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 缺陷处置与收口

Status: completed
Targets: `nop-stream/nop-stream-runtime/`、`ai-dev/backlog/nop-stream-productization-roadmap.md`、审计报告收口章节

- Item Types: `Fix | Follow-up`

- [x] 就地修复全部归类为小缺陷的项：每个修复附 focused 回归测试；修复不引入空壳/静默跳过；修复若落在 `_` 前缀生成文件，上移到源模型/模板层处理（生成文件禁改）（R-1..R-24 + G6/G7 测试缺口，全部位于 runtime main/test 源码，无生成文件）
- [x] **分布式路径修复的强制验证**：本 plan 定义「分布式路径」= 仅在 DISTRIBUTED 模式可达的类与机制（`rpc/`、`cluster/`、`transport/` 的 Remote* 类、coordinator fencing/region failover 恢复路径、跨 JVM checkpoint 协调）；触及该路径的修复必须新增或扩展 gated 多 JVM 测试复现修复场景（`-Dnop.stream.test.multi-jvm.enabled=true` 经 MiniStreamCluster 真实多 JVM 运行），单进程测试不能充当分布式验证（R-14 fencing 回滚防护新增 testZombieCoordinatorEpochRollbackRejectedAtRpcBoundary——测试 JVM 经真实 RPC proxy 跨 JVM 注入 stale epoch，MiniStreamCluster 真 3 进切实跑通过；R-15 缺陷机制为 JVM 内部时序条件、单/多 JVM 同一代码路径，focused 单测覆盖语义，裁定记录于报告 §3.4）
- [x] 大缺陷逐项转为 roadmap Follow-up 工作项：按 Rules 追加到 Work Items 末尾（编号顺延、状态 todo、来源标注本 plan + 证据锚点），并更新 Last updated（items 25—28：manifest 版本化与校验和 / 协调器结构治理 / cancelTask fencing / remote 通道收敛）
- [x] 全量回归：`./mvnw test -pl nop-stream -am -T 1C` 全绿（gated 分布式测试按 `nop.stream.test.multi-jvm.enabled=true` 约定执行并记录结果：7/7 绿）；`node ai-dev/tools/check-nop-stream-invariants.mjs` 通过（CI fail-fast 门禁）
- [x] 审计报告 Status 定稿（resolved + 遗留清单），含全部发现→处置零丢失映射表（结构对齐 item 7 报告）
- [x] 小缺陷修复若改变 live 行为且 owner doc 受影响：同步最小 owner-doc 更新或显式记录 `No owner-doc update required`（裁定与理由见报告 §3.4）

Exit Criteria:

- [x] 全部 in-scope 小缺陷修复落地且各有 focused 测试（新增测试清单列明：26 个新用例 + 1 处 mock 扩展；2 项纯文档/日志级修复按 Rule #25 显式豁免并注明）
- [x] 全部大缺陷已追加为 roadmap Follow-up 工作项（items 25—28；无遗漏——F-A/F-B/F-C/F-D/D-DRIFT-2 实施全数落位）
- [x] **端到端验证**（触及 checkpoint/恢复路径时）：barrier → 恢复 → 重处理的完整路径测试存在且通过（TestE2ECheckpointAndRecovery/TestMultiEpochCheckpointE2E/TestSupervisionLoopConsistentCut/TestDistributedExactlyOnce 随全量回归绿）；分布式路径修复有 gated 多 JVM 测试（真实多 JVM）覆盖并通过（R-14 真实 3 进程测试 7/7）
- [x] **无静默跳过**：修复代码无空方法体/吞异常模式（hollow scan 复跑退出码 0）
- [x] gated 多 JVM 测试套件已按 `nop.stream.test.multi-jvm.enabled=true` 运行且结果已记录（无论是否存在分布式路径修复，为 Phase 2 盘点提供运行证据）（7/7：ExactlyOnceRecovery 1 + CoordinatorFailover 3 含新增用例 + ProcessSpawn 3）
- [x] 修复改变 live 行为时：受影响 owner doc 已做最小事实同步，或显式记录 `No owner-doc update required`（含理由）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（报告新增后）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 2026-05-20 / 2026-06-30 runtime 相关发现（seed 清单 + 派生规则所得项）整改收口核对完成，无未处置的 partial/regressed 项；跨模块路由项有明确去向（item 7 / Follow-up）
- [x] D-GAP runtime 审计重点全部消化且对照勾销清单无遗漏（含显式「无额外重点」结论路径）（两项重点四分项，报告 §2.1）
- [x] 空壳扫描 high/critical 无未处置真实发现（误报已按处置路径消除，非仅记录）（exit 0 零发现，79 medium 全人工判定合法语义）
- [x] 小缺陷修复全部带 focused 测试；大缺陷全部 Follow-up 化（或显式无）（R-1..R-24 + items 25—28）
- [x] 分布式路径修复全部有 gated 多 JVM 测试覆盖（真实多 JVM 运行通过）（R-14 真实 3 进程测试；7/7 全绿）
- [x] gated 多 JVM 测试盘点完成且无长期失修项被忽略（失修项有处置结论）（6+1 用例/3 类，git 活跃无失修；rescale/unaligned 多 JVM 缺口记录为 Phase S 输入）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect（watch-only 9 项 + 拒绝 1 项全部带 Why-Not-Blocking 理由，报告 §3.3）
- [x] `./mvnw compile`（随 `-pl nop-stream -am` 构建覆盖）（clean install BUILD SUCCESS）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（runtime 868/0/9-skipped gated + 其余模块全绿）
- [x] checkstyle / 代码规范检查通过（随 mvnw 构建；imports 分组 io.nop.* → 第三方 → java.*、4-space 缩进符合 AGENTS.md——root pom checkstyle 配置整体注释的事实沿 item 7 先例按现状通过）
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] **Anti-Hollow Check**：closure audit 已验证修复组件被既有调用链在运行时确实调用、无空方法体/静默跳过/no-op 作为正常实现
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] roadmap item 8 状态写回（closure audit 通过后）

## Deferred But Adjudicated

（执行中按需追加：仅允许 `watch-only residual | optimization candidate | out-of-scope improvement` 三类，逐条附 Why Not Blocking Closure）

## Non-Blocking Follow-ups

（执行中按需追加）

## Closure

Status Note: 三 Phase 全 completed（Phase 1 历史审计收口核验 / Phase 2 产品化新增审计 / Phase 3 缺陷处置与收口），Closure Gates 17/17 勾选。小缺陷 R-1..R-24 + G6/G7 全部就地修复（26 个新 focused 用例，R-14 配真实 3 进程 gated 多 JVM 测试）；大缺陷全数转 roadmap Follow-up items 25—28；审计报告 resolved 落库。独立 closure audit PASS 后关闭，roadmap item 8 写回 done。
Completed: 2026-09-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，非实现 session）
- Audit Session: `ses_fa44b7fbcffe8IZHXkYJotg2is`
- Evidence:
  - 逐 Gate 验证 17 项：PASS 15 项 + 2 项 PENDING 即本 closure 仪式自身动作（Closure Evidence 写入 + roadmap item 8 写回，随本条完成）；05-20/06-30 收口核对表、D-GAP 勾销清单、watch-only 裁定均对 live 报告锚点复核通过（报告 §1.1/§1.2/§2.1/§2.5/§3.3）
  - 修复抽查 12/24 项（R-1..R-9/R-14/R-15/R-16..R-23）源码锚点全部与 live 匹配；R-14 gated 测试 `TestMultiJvmCoordinatorFailover.java:158`（真实跨 JVM RPC proxy 注入 stale epoch，类级 `@EnabledIfSystemProperty(nop.stream.test.multi-jvm.enabled)` :34）
  - Anti-Hollow 检查：CONFIRMED CLEAN——修复全部位于生产调用路径（RPC @Override/存储接口/startJob 失败路径/adapter 消费路径），R-1 配置实际被 :194-199/:261 消费；新增 catch 全部带 WARN/ERROR；wiring-registry 钉点诚实同步（GMCE 742→743 行漂移）
  - Deferred 项分类检查：watch-only 9 项 + 拒绝 1 项全部带 Why-Not-Blocking 理由，无 in-scope live defect 被降级；F-* 全数落 items 25—28（roadmap :73-76 来源标注核实）
  - 门禁复跑（closure 当日）：`./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS（全模块绿）；`check-nop-stream-invariants.mjs` exit 0；`scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high` exit 0（0 发现）；`check-doc-links.mjs --strict` exit 0（0 errors）；`check-plan-checklist.mjs --strict` exit 0（57/57 勾选）；gated 多 JVM 7/7 绿（Phase 3 实跑记录，报告 §3.4）
  - 2 Minor 处置：①测试计数漂移 24→26（TestTaskManager +7→+6，grep/diff 逐文件核实 6+3+3+2+1+7+1+3=26；报告 §3.1/总结节 + 本 plan Exit Criteria 已同步修正，under-count 非虚报）；②gate 16/17 提前勾选 = closure 仪式动作自身，随本 Evidence 写入与 roadmap 写回完成
Follow-up:

- 无剩余 plan-owned work；结构治理与 fencing/通道收敛全数移交 roadmap Follow-up items 25—28（todo，来源本 plan）
