# 3 nop-stream-runtime 模块审计（roadmap item 8）

> Plan Status: active
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

Status: planned
Targets: `nop-stream/nop-stream-runtime/`、审计报告 Phase 1 章节

- Item Types: `Proof`

- [ ] 2026-05-20 audit 中 runtime 相关组（§3/§5/§2 runtime 侧，组清单见 Current Baseline）逐组核对 live 状态（已清除 / 已收编（原死代码被生产接线启用，如 §3 的 CheckpointCoordinator/存储实现已被协调器与分布式执行器接线）/ 部分残留 / 回潮新增），每组附源码路径或 absence 证据；其余组显式标注「归属 item 7/9」不在本表核对
- [ ] 2026-06-30 code audit runtime 相关发现逐项核对（seed 清单见 Current Baseline + 「涉及文件位于 nop-stream-runtime」派生规则，执行时先落完整核对表再逐项核对）：landed / partial / regressed 三态 + 证据指针；位于 core 的发现（如 `InputGate` 项）按跨模块规则记录并路由，不纳入本表修复
- [ ] 复核「2026-08-04-2300-1/2/3 remediation plans 已收口」结论对 runtime 模块成立（抽查其 runtime 侧修复点 live 存在性）
- [ ] partial/regressed 项归类：按小/大缺陷判定准则进 Phase 3 或 Follow-up 候选，逐项记录理由

Exit Criteria:

- [ ] 报告 Phase 1 章节含 05-20 runtime 相关组核对表 + 06-30 runtime 发现完整核对表（先列全表再逐项核对，每行三态 + 证据指针，无「未核对」空行；非 runtime 组的归属标注与跨模块路由记录在表外显式）
- [ ] remediation plans 收口复核结论落地（成立/不成立 + 抽查点）
- [ ] 全部 partial/regressed 项有归类结论
- [ ] No owner-doc update required（纯核验章节）
- [ ] No new test required: 纯核验章节，无代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 产品化视角新增审计

Status: planned
Targets: `nop-stream/nop-stream-runtime/`、审计报告 Phase 2 章节

- Item Types: `Proof`

- [ ] 消化 D-GAP 报告的 runtime 审计重点清单：先对照 D-GAP 报告 runtime 条目建立勾销清单，再逐条执行对应审计/核验动作（含裁定归属 runtime 证据面的 P-REQ 核对项，如 P-REQ-20；D-GAP 判定「无额外重点」时显式记录该结论）；closure 前对照清单确认无遗漏条目
- [ ] 分布式核心路径优雅性/可靠性审计：supervision loop、region failover 恢复路径、fencing token 传播、数据面 wire codec 错误处理、checkpoint 协调器并发正确性（多并发/unaligned checkpoint 路径）的重复代码复查与边界条件审计
- [ ] 运行 `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high`，high/critical 发现逐条核实（真实空壳 vs 误报）；**误报处置路径**：修正代码模式或修正工具检测规则——「仅记录不改」不合法（退出码 0 为不可降级硬门禁）
- [ ] 测试覆盖抽查：runtime 主要包（taskmanager/transport/coordinator/execution/rpc/checkpoint/cluster/operators/source）各抽 ≥2 个代表性测试命中确认 + 按行数 top-5 文件的直接测试覆盖检查；gated 多 JVM 测试（`nop.stream.test.multi-jvm.enabled` 启用族）盘点——条目数、覆盖场景（kill/failover/rescale/exactly-once）、是否有长期未跑的 gated 测试
- [ ] checkpoint 存储实现健壮性抽查（LocalFile/JDBC 双实现的原子性/错误处理/null 返回路径），为 D-GAP 中 P-REQ-20 相关裁定提供 runtime 侧证据补充

Exit Criteria:

- [ ] D-GAP runtime 审计重点勾销清单落地（逐条消化记录 + 对照 D-GAP 报告 runtime 条目无遗漏；含显式「无额外重点」结论路径）
- [ ] hollow scan 退出码记录 + high/critical 发现逐条核实结论（真实发现进 Phase 3 或 Follow-up；误报处置动作已执行或已按缺陷流程进入 Phase 3，非仅记录）
- [ ] 分布式核心路径审计发现逐条带源码锚点
- [ ] 测试覆盖抽查结论表落地（每包 ≥2 代表性测试命中情况 + top-5 文件覆盖结论）
- [ ] gated 多 JVM 测试盘点表落地（条目数 + 覆盖场景 + 长期未跑项状态）
- [ ] checkpoint 存储健壮性抽查结论表落地（LocalFile/JDBC 各含错误处理/原子性路径结论 + P-REQ-20 证据小节）
- [ ] No owner-doc update required（同 item 7 原则）
- [ ] No new test required: 纯审计章节，无代码变更（Phase 3 修复的测试要求在 Phase 3）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 缺陷处置与收口

Status: planned
Targets: `nop-stream/nop-stream-runtime/`、`ai-dev/backlog/nop-stream-productization-roadmap.md`、审计报告收口章节

- Item Types: `Fix | Follow-up`

- [ ] 就地修复全部归类为小缺陷的项：每个修复附 focused 回归测试；修复不引入空壳/静默跳过；修复若落在 `_` 前缀生成文件，上移到源模型/模板层处理（生成文件禁改）
- [ ] **分布式路径修复的强制验证**：本 plan 定义「分布式路径」= 仅在 DISTRIBUTED 模式可达的类与机制（`rpc/`、`cluster/`、`transport/` 的 Remote* 类、coordinator fencing/region failover 恢复路径、跨 JVM checkpoint 协调）；触及该路径的修复必须新增或扩展 gated 多 JVM 测试复现修复场景（`-Dnop.stream.test.multi-jvm.enabled=true` 经 MiniStreamCluster 真实多 JVM 运行），单进程测试不能充当分布式验证
- [ ] 大缺陷逐项转为 roadmap Follow-up 工作项：按 Rules 追加到 Work Items 末尾（编号顺延、状态 todo、来源标注本 plan + 证据锚点），并更新 Last updated
- [ ] 全量回归：`./mvnw test -pl nop-stream -am -T 1C` 全绿（gated 分布式测试按 `nop.stream.test.multi-jvm.enabled=true` 约定执行并记录结果）；`node ai-dev/tools/check-nop-stream-invariants.mjs` 通过（CI fail-fast 门禁）
- [ ] 审计报告 Status 定稿（resolved 或 open + 遗留清单），含全部发现→处置零丢失映射表（若 item 7 报告已存在则结构对齐，否则按 writing guide 独立成篇）
- [ ] 小缺陷修复若改变 live 行为且 owner doc 受影响：同步最小 owner-doc 更新或显式记录 `No owner-doc update required`

Exit Criteria:

- [ ] 全部 in-scope 小缺陷修复落地且各有 focused 测试（新增测试清单列明）
- [ ] 全部大缺陷已追加为 roadmap Follow-up 工作项（或显式记录「无大缺陷」）
- [ ] **端到端验证**（触及 checkpoint/恢复路径时）：barrier → 恢复 → 重处理的完整路径测试存在且通过；分布式路径修复有 gated 多 JVM 测试（真实多 JVM）覆盖并通过
- [ ] **无静默跳过**：修复代码无空方法体/吞异常模式（hollow scan 复跑退出码 0）
- [ ] gated 多 JVM 测试套件已按 `nop.stream.test.multi-jvm.enabled=true` 运行且结果已记录（无论是否存在分布式路径修复，为 Phase 2 盘点提供运行证据）
- [ ] 修复改变 live 行为时：受影响 owner doc 已做最小事实同步，或显式记录 `No owner-doc update required`（含理由）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（报告新增后）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 2026-05-20 / 2026-06-30 runtime 相关发现（seed 清单 + 派生规则所得项）整改收口核对完成，无未处置的 partial/regressed 项；跨模块路由项有明确去向（item 7 / Follow-up）
- [ ] D-GAP runtime 审计重点全部消化且对照勾销清单无遗漏（含显式「无额外重点」结论路径）
- [ ] 空壳扫描 high/critical 无未处置真实发现（误报已按处置路径消除，非仅记录）
- [ ] 小缺陷修复全部带 focused 测试；大缺陷全部 Follow-up 化（或显式无）
- [ ] 分布式路径修复全部有 gated 多 JVM 测试覆盖（真实多 JVM 运行通过）
- [ ] gated 多 JVM 测试盘点完成且无长期失修项被忽略（失修项有处置结论）
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect
- [ ] `./mvnw compile`（随 `-pl nop-stream -am` 构建覆盖）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] checkstyle / 代码规范检查通过（随 mvnw 构建）
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] **Anti-Hollow Check**：closure audit 已验证修复组件被既有调用链在运行时确实调用、无空方法体/静默跳过/no-op 作为正常实现
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] roadmap item 8 状态写回（closure audit 通过后）

## Deferred But Adjudicated

（执行中按需追加：仅允许 `watch-only residual | optimization candidate | out-of-scope improvement` 三类，逐条附 Why Not Blocking Closure）

## Non-Blocking Follow-ups

（执行中按需追加）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:

Follow-up:

- （closure 时填写）
