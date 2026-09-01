# 3 复合场景分布式落地（roadmap item 14）

> Plan Status: active
> Last Reviewed: 2026-09-01
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 14（Phase S，critical path，deps: item 13）；stage details「复合场景分布式落地」
> Related: `2026-09-01-2217-2-composite-scenario-local-implementation.md`（item 13，前置依赖，其场景测试资产是本 plan 的输入）；`2026-09-01-2217-1-composite-scenario-design.md`（item 12，分布式验证矩阵定义来源）；`2026-09-01-0938-3-runtime-module-audit.md`（item 8，R-14 真实 3 进程 gated fencing 测试先例 + `MiniStreamCluster` 基建复核结论）
> Mission: nop-stream-productization
> Work Item: roadmap item 14

## Purpose

用 `MiniStreamCluster` 真实多 JVM DISTRIBUTED 模式验证全部复合场景：按设计文档的分布式验证矩阵执行「场景部署 → checkpoint → kill TaskManager → recover（fencing 断言）→ rescale → exactly-once 结果断言」；修复分布式路径发现的缺陷（配 gated 测试）；交付分布式运行手册初稿（供 items 16/17 深化）。本 plan 与 plan 2（item 13）共同构成 M3 解锁条件（13+14 done）。

## Current Baseline

（2026-09-01 live 核对；**执行前置条件**：plan 2（item 13）已 completed——场景 LOCAL 测试资产存在且绿；不存在则 blocked）

- **分布式验证基建**（live，roadmap Framework/platform reuse 表）：`MiniStreamCluster`（`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/MiniStreamCluster.java`，572 行，ProcessBuilder + H2 AUTO_SERVER）+ 独立进程入口 `JobCoordinatorMain`/`TaskManagerMain`（`runtime/test/.../launch/`，含 `SharedJdbcInfrastructure`/`PollingJdbcMessageService`/`ClusterLaunchConfig`）。**能力边界（live 核实，本 plan 的主要工程量所在）**：`JobCoordinatorMain` 硬编码 trivial 管线（`buildTrivialSourceSinkJobGraph`：空 `CollectionReplayableSource` 零记录 + 丢弃型 `PrintSinkFunction`，:171—189，javadoc 自述验证面是 deploy/recovery/fencing 基建而非端到端数据流）；`CheckpointCoordinator.startCheckpointScheduler()` 在 launch 路径无调用方（现有多 JVM 运行从不产生 checkpoint）；跨进程 sink 可观察面（S1 JDBC 表 / S2 文件产物）无传递与断言通道——「场景管线多 JVM 部署 + 真实数据流 + checkpoint 产生 + sink 断言」需本 plan 落地（Phase 1 第 1 项）
- **测试归属事实（live 核实）**：`nop-stream-runtime` pom **未导出 test-jar**（其对 `nop-stream-core` 的 test-jar 依赖是消费方；仅 core 导出 test-jar）——`MiniStreamCluster` 与全部 launch Main 位于 runtime `src/test/java`，其他模块无法 import。可行路径预定（Phase 1 裁定，二选一）：① runtime 挂 maven-jar-plugin test-jar 执行（构建配置变更，本 plan 显式批准）+ gated 测试放场景模块；② gated 测试放 runtime + 显式新增 test 依赖（cep/flow/connector-debezium 等），须核验不构成 reactor 环（场景模块若依赖 runtime 则禁止反向 test 依赖）
- **gated 多 JVM 测试先例**：`TestMultiJvmExactlyOnceRecovery`（288 行）/`TestMultiJvmCoordinatorFailover`（304 行）/`TestMiniStreamClusterProcessSpawn`，门禁 `@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")`；item 8 R-14：真实多 JVM（3 JVM：JC + TM 独立 spawn 进程 + 测试 JVM 扮 zombie）gated fencing 回滚防护测试（7/7 绿，`testZombieCoordinatorEpochRollbackRejectedAtRpcBoundary`）——kill/recover/fencing 断言模式与全套 helper（epoch rotation 轮询、assignment 行计数、coordinator log delta 读取）可复用；**其管线为 trivial 空数据流**，场景级数据流/checkpoint/sink 断言面是本 plan 新建（见上条）
- **fencing 断言语义**：fencing token 统一（跨 JVM 控制面 RPC）；`TestFencingTokenRejection`/`TestJobCoordinatorJdbcHaIntegration` 等单进程内测试存在——多 JVM 场景断言以 R-14 模式为准
- **验证矩阵来源**：`composite-scenario-design.md`（plan 1 交付）定义「场景 × 演练组合」矩阵（kill / kill+fencing / restore-rescale / backpressure 触发）+ D-GAP §3.2 约束 ⑤（MiniStreamCluster 真实多 JVM 基线，禁 K8s）与约束 ②（rescale 仅 restore-time / 离线 reshard 路径）
- **场景资产**：plan 2 交付的 XDSL 场景定义 + LOCAL 测试（S1: CDC → CEP → 窗口聚合 → 2PC JDBC sink；S2: file → keyBy + Delta → exactly-once 文件 sink + rescale）——本 plan 将其提升到 DISTRIBUTED 模式验证
- **exactly-once 断言语义边界**：D-GAP §3.2 约束 ④（最新 durable epoch manifest 恢复 + 结果无重复无丢失；不断言 manifest 级 checksum 行为——item 25 未落地）
- **已知分布式遗留**（Follow-up item 28 记载）：remote-deploy 数据面全对订阅 + 队列满阻塞泄漏、`JdbcCheckpointStorage.loadRetainedEpochManifests` override 缺失（Stage-31 重启恢复在 JDBC 后端降级）——场景演练若触发这些路径，按缺陷处置规则就地修复或核验 Follow-up 归属，**不得静默绕过**
- **运行手册定位**：roadmap stage details——分布式运行手册初稿供 item 16（可观测性与运维）/17（文档产品化）深化；交付位置 `ai-dev/design/nop-stream/` 或 `docs-for-ai/`（执行时按文档分工裁定：设计/契约入前者，使用指南入后者）
- **工具门禁**：hollow scan / invariants / doc-links / check-plan-checklist（同 items 7—11 基线）

## Goals

- 每个场景（S1/S2）至少一条真实多 JVM gated E2E 测试：场景部署（JobCoordinator + 多 TaskManager 独立进程）→ checkpoint 产生 → kill TaskManager → 恢复（含 fencing 断言：旧 attempt 的 mutation 被拒或等价可观察证明）→ exactly-once 结果断言（sink 端无重复无丢失）
- 验证矩阵逐格执行或显式裁定（不可执行格记录原因与归属——如 backpressure 行为演练若属 item 15 范围则显式路由，不留含糊）
- rescale 验证（S2 矩阵）：restore-time parallelism rescale 在多 JVM 下的结果一致性测试（离线 reshard 路径按设计矩阵裁定）
- 分布式路径缺陷：就地修复 + gated focused 测试；结构性大缺陷转 Follow-up（编号顺延）
- 分布式运行手册初稿：部署拓扑、启动顺序、checkpoint/恢复操作、kill/rescale 演练步骤（与场景测试命令对齐）
- gated 套件在 `-Dnop.stream.test.multi-jvm.enabled=true` 下全绿；默认关闭时其余测试不受影响
- roadmap item 14 写回；M3 解锁条件（items 13+14 done）随写回核对声明

## Non-Goals

- 长时 soak / chaos 矩阵 / backpressure 行为稳定性量化（item 15；本 plan 仅覆盖矩阵中场景验收所需的触发组合）
- K8s/YARN 部署（D-GAP 裁定 defer/exclude；验证以 `MiniStreamCluster` 为基线）
- 可观测性指标暴露/运维 API（item 16；运行手册只记录既有可观察面）
- flow item 29（xpl source 取消语义等）与 item 26/27/28 结构治理的主动实施（仅当场景演练触发其路径时核验归属；主动实施属各自 plan）
- standby 热备 / 状态查询 / 运行时自动 reshard（P-REQ-18/19/15 exclude+non-goal 边界，D-GAP §3.2）
- 用户指南/连接器目录（item 17）

## Scope

### In Scope

- 新增（基建能力扩展）：launch/测试基建支持场景管线多 JVM 部署——场景 JobGraph/XDSL 来源（替代硬编码 trivial 管线）、真实数据源接入、checkpoint 调度启动（launch 路径接线 `startCheckpointScheduler` 或等价机制）、跨进程 sink 可观察面（S1 JDBC 表 / S2 文件产物路径）、并行度参数化；含「测试归属」二选一路径实施（Current Baseline 预定路径，构建配置变更显式批准）
- 新增：场景级 gated 多 JVM 测试（归属按 Phase 1 裁定）；每场景 ≥1 条 kill/recover/fencing/exactly-once 主链路测试 + 矩阵要求的 rescale/backpressure 触发组合测试
- 新增：分布式运行手册初稿（位置执行时裁定，见 Current Baseline）
- 修复：分布式路径缺陷（单 plan 范围内）+ gated focused 测试；`_` 前缀生成文件禁改
- 修改：`ai-dev/backlog/nop-stream-productization-roadmap.md`（Follow-up 追加（如有）+ item 14 closure 写回 + M3 声明 + Last updated）
- 修改（条件性）：受影响 owner-doc 最小同步（`failover-design.md`/`checkpoint-design.md` 等，仅当缺陷修复改变契约语义；否则显式 `No owner-doc update required`）
- 只读输入：`composite-scenario-design.md` 矩阵、plan 2 场景资产、item 8 报告 R-14 模式、`MiniStreamCluster`/launch 基建

### Out Of Scope

- 与场景验证无关的 `MiniStreamCluster`/launch 基建重构（**场景部署所需的能力扩展属 In Scope**；仅排除无关重构——item 28 的结构性治理归其自身 Follow-up）
- 单进程（LOCAL）行为变更（plan 2 已收口；本 plan 发现的 LOCAL 缺陷仅路由）
- `ai-dev/analysis/` 演练报告（属 item 15 的 soak/chaos 交付；本 plan 结果记录于 plan closure + 日志 + 运行手册）

## Execution Plan

### Phase 1 - 场景级多 JVM 主链路测试（kill → recover → fencing → exactly-once）

Status: planned
Targets: `nop-stream-runtime` launch/multijvm 测试基建（能力扩展）、场景 gated 测试（归属 Phase 1 裁定）、场景 XDSL 资产（plan 2 交付）

- Item Types: `Fix | Decision | Proof`

- [ ] 执行前置核对：plan 2 completed（场景 LOCAL 资产存在且绿）；否则置 blocked 并记录
- [ ] 基建能力扩展：launch/测试基建支持场景管线多 JVM 部署（场景 JobGraph 来源 / 真实数据源 / checkpoint 调度启动 / sink 可观察面 / 并行度参数化——Current Baseline「能力边界」条逐项落地）+ 测试归属裁定实施（Current Baseline 预定路径二选一，记录决策与理由）
- [ ] S1 多 JVM 主链路 gated 测试：部署（JC + 多 TM 独立进程）→ 数据流入 → checkpoint → kill TM → 恢复 → fencing 断言 → JDBC 2PC sink exactly-once 结果断言（无重复无丢失）
- [ ] S2 多 JVM 主链路 gated 测试：同主链路 + 文件 sink exactly-once 断言 + Delta 定制拓扑在 DISTRIBUTED 模式生效断言
- [ ] fencing 可观察断言设计落地（沿 R-14 模式：旧 epoch attempt 的 mutation 被拒的可观察证据，非仅进程存活）
- [ ] kill 时机受 durable manifest 约束：测试先断言 kill 前「最新 durable epoch manifest 已落盘」（checkpoint 存储可观察），再做 kill/恢复断言（否则恢复断言退化为「从零重跑」）
- [ ] 已知遗留路径核验：演练触发 item 28 记载路径（remote-deploy 通道 / JDBC 后端 Stage-31 恢复降级）时记录行为，缺陷不静默绕过——**执行提示**：remote-deploy 队列满泄漏在真实流量下可能以 gated 测试 **hang**（而非 fail）形式出现，处置时优先核验 item 28 归属，勿误判为新死锁而就地大改

Exit Criteria:

- [ ] 基建能力扩展落地且被场景 gated 测试真实消费（trivial 空管线不再是唯一部署路径；checkpoint 在多 JVM 运行中实际产生——存储产物可观察）（**接线验证**，Rule #23）
- [ ] 每场景 ≥1 条 gated 测试文件存在且在 `-Dnop.stream.test.multi-jvm.enabled=true` 下通过（**端到端验证**：多 JVM 部署到 sink 结果断言的完整路径，Rule #22）
- [ ] fencing 断言为行为级（可观察拒绝/拒绝日志/结果一致性证明），非进程存活级
- [ ] kill 前 durable manifest 存在性断言落地（恢复断言的确定性前提）
- [ ] exactly-once 断言落在 sink 端最终产物（JDBC 表内容 / 文件提交产物）
- [ ] **无静默跳过**（Rule #24）：基建扩展与测试无空方法体/吞异常/占位返回；遗留路径触发时显式失败或显式记录
- [ ] 默认（门禁关闭）构建不受影响：`./mvnw test -pl nop-stream -am -T 1C` 全绿（gated 用例 skip）
- [ ] 新增测试断言正确结果而非仅无异常（Rule #25 映射：每个演练断言列明验证什么；基建扩展若含新公共方法，未实现路径显式抛异常）
- [ ] owner-doc 裁定：基建扩展若改变 launch/运行契约（如 JobCoordinatorMain 参数面），相关文档最小同步或显式 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 矩阵收口（rescale + backpressure 触发组合）+ 缺陷处置

Status: planned
Targets: multijvm 测试、（条件性）runtime/场景模块缺陷修复点

- Item Types: `Fix | Decision | Proof`

- [ ] S2 rescale 多 JVM 测试：restore-time parallelism rescale（如 TM 数/并行度变更后恢复）结果一致性断言；离线 reshard 工具路径按设计矩阵裁定执行或显式路由（含理由）
- [ ] 矩阵 backpressure 触发组合：按设计矩阵实现（如 bounded sink 限速下 checkpoint 仍推进 + 结果完整）；超出场景验收的稳定性量化显式路由 item 15（矩阵逐格有「已执行 / 路由 + 理由」结论，无含糊格）
- [ ] 分布式路径缺陷就地修复 + gated focused 测试；结构性大缺陷转 Follow-up（编号顺延、证据锚点）
- [ ] 运行手册初稿：部署拓扑 / 启动顺序 / checkpoint 与恢复操作 / kill/rescale 演练步骤（与 gated 测试命令一致）/ 已知边界（含 item 28 遗留如适用）

Exit Criteria:

- [ ] 设计矩阵逐格结论落库（已执行测试名 / 显式路由 + 理由），无未裁定格
- [ ] rescale 测试通过（恢复后结果一致 + 无重复丢失）；恢复走 restore-time/离线路径（D-GAP §3.2 约束 ② 合规）
- [ ] 全部就地修复配 gated focused 测试（或显式豁免附理由）；无静默绕过已知遗留（**无静默跳过**，Rule #24：遗留路径触发时显式失败或显式记录，不得吞掉）
- [ ] 运行手册初稿存在且步骤与测试命令一致（repo-observable 路径）；受影响 owner-doc 同步或显式 `No owner-doc update required`
- [ ] gated 套件启用态全绿（执行记录：测试数/通过数写入 plan 或当日 log）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 默认态全绿
- [ ] hollow scan / invariants / doc-links 三工具退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 每场景 kill → recover（fencing 行为级断言）→ exactly-once 主链路 gated 测试存在且启用态绿
- [ ] launch 基建支持场景管线部署（真实数据流 + checkpoint 产生 + sink 可观察面），且被场景 gated 测试真实消费（非仅 trivial 空管线旁路）
- [ ] 设计矩阵 100% 逐格结论（执行或显式路由），D-GAP §3.2 六条约束合规（多 JVM 基线 / rescale 路径 / 断言语义边界）
- [ ] 分布式缺陷已修复或 Follow-up 化（含 item 28 遗留的触发核验记录），无静默降级
- [ ] 运行手册初稿落库且与测试命令一致
- [ ] 受影响 owner docs 已同步或显式 `No owner-doc update required`
- [ ] `./mvnw compile` / `./mvnw test -pl nop-stream -am -T 1C`（默认态）全绿；gated 套件启用态执行记录在案
- [ ] checkstyle 随构建通过（沿现状基线）
- [ ] hollow scan / invariants / doc-links 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] **Anti-Hollow Check**：closure audit 验证多 JVM 调用链真实连通（进程 spawn → 部署 → 数据面 → checkpoint → 恢复 → sink），gated 断言为行为级
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] roadmap item 14 写回 + ★M3 解锁核对声明（items 13+14 均 done 后 M3 标 done）

## Deferred But Adjudicated

（执行时按需填写；允许类型仅 `watch-only residual | optimization candidate | out-of-scope improvement`，逐条附 Why Not Blocking Closure。预期：backpressure 稳定性量化 → item 15（optimization candidate/路由）；离线 reshard 多 JVM 路径（如矩阵裁定路由））

## Non-Blocking Follow-ups

- （执行时填写；confirmed live defect 不得出现在这里）

## Closure

Status Note: （closure 时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （closure 时填写）
- Evidence: （closure 时填写）

Follow-up:

- （closure 时填写）
