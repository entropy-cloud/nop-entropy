# 可观测性与运维产品化（roadmap item 16 / P-REQ-1..12 落地）

> Plan Status: active
> Mission: nop-stream-productization
> Work Item: item 16 可观测性与运维产品化
> Last Reviewed: 2026-09-02
> Source: `ai-dev/analysis/2026-09/2026-09-01-competitor-productization-synthesis-and-p-req.md` §2.2（P-REQ-1..12 验收标准）+ `ai-dev/analysis/2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md` §3.3（item 16 裁剪输入：go×10 / defer×1 初步建议）+ §2.4/§2.5（F-2 过渡期 P-REQ-12 归属 item 16；P-REQ-10 与 reshard 工具入口收敛观察）
> Related: `2026-09-01-2217-3-composite-scenario-distributed-verification.md`（distributed-runbook.md 初稿来源）；`2026-09-02-2216-2-stability-performance-exercise.md`（item 15 消费本 plan 指标面，执行顺序建议在本 plan 之后）

## Purpose

把 nop-stream 从「无运维观测面」（live 仅 CheckpointMetrics 单点）推进到「运维级可观测」：分层指标体系、事件监听、Prometheus 暴露、REST 运维 API、checkpoint 观测、逻辑健康状态机、状态重置与生命周期治理、告警外发闭环——P-REQ-1..12 全部落到 met 或 adjudicated 状态，并以运维手册（runbook 深化 + docs-for-ai 落点）收口。

## Current Baseline

（2026-09-02 live 核对）

- 指标与状态现状：`CheckpointMetrics{,Snapshot}` 已含 `failureCause` 字段（P-REQ-6 失败原因分支**部分成立**——snapshot 级已有，overview/history 查询接口与历史保留仍缺）；`coordinator/JobStatus` 枚举 + `JobStatusResponse(jobStatus, failureCause)` + `rpc/IStreamCoordinatorRpcService` 已有 `getJobStatus` 查询面（P-REQ-5「详情」与 P-REQ-7 状态面是**既有面的扩展**，非 greenfield）；无分层指标注册体系、无 Prometheus/OpenMetrics 暴露（nop-stream 内无 REST/servlet/HTTP 类）。
- 平台可复用设施：`nop-kernel/nop-commons/.../metrics/`（`GlobalMeterRegistry`——micrometer 薄封装、`MeterPrinter`、`CountTimer`、`MeterPrintConfig`）；**nop-commons 已依赖 `micrometer-core` + `micrometer-registry-prometheus`**（P-REQ-3 复用落地锚点）；`nop-integration`/`nop-message` 有 email/sms/飞书渠道（无引擎侧 AlertChannel 框架）；D-GAP 结论均要求「优先复用平台设施，不引入新框架」。
- 控制面形态：runtime 为独立进程（JDBC 消息表轮询 RPC：`rpc/StreamControlRpcServer` 等），MiniStreamCluster 多 JVM 测试基建 + gated 测试启用机制已就绪（item 14 交付）。
- 运维手册：`ai-dev/design/nop-stream/distributed-runbook.md` 初稿（item 14 交付），其头部明确「item 16 在此基础上深化并迁移到 docs-for-ai」；§5 已知边界声明「背压无直接指标（item 16 前提）」。
- 文档路由现状：`docs-for-ai/03-modules/` 无 nop-stream 专属 owner doc（经 `01-repo-map/module-groups.md` 路由）。
- F-2 处置现状：stop-edit-restart（item 16 语义追加告警渠道）尚未执行，按 D-GAP §2.4 过渡安排，P-REQ-12 归属本 item。

## Goals

- P-REQ-1..12 逐条达到其验收标准（repo-observable），或落显式三态裁定（defer/exclude + 依据记录），最终验收（item 18）可直接引用。
- 交付运维操作面：REST 作业生命周期端点、状态重置工具、历史/日志生命周期治理配置。
- distributed-runbook 深化为运维手册并落到 `docs-for-ai/`，INDEX/source-anchors 同步。

## Non-Goals

- Web 控制台实现（P-REQ-9 初步建议 defer——本 plan 仅落正式裁定，不实现）。
- K8s/YARN/HPA 编排（P-REQ-15 已裁定 defer/exclude，D-GAP §2.2）。
- 提交前校验/凭据加密（P-REQ-13/14 → Follow-up item 20）。
- 用户指南/连接器目录全量文档化（item 17；本 plan 只交付运维 owner doc 最小落点）。
- SLO/基准测试框架与稳定性演练（item 15，其指标观察面以本 plan 交付为前提）。
- 跨版本升级兼容测试基建（P-REQ-21 已 defer，F-3 维持建议级，不吸收）。

## Scope

### In Scope

- P-REQ-1..12 的正式三态裁定（D-GAP §3.3 仅为初步建议，正式裁定属本 plan）。
- go 项的代码交付 + focused 测试 + MiniStreamCluster e2e 断言（按 P-REQ 各自验收标准）。
- 设计文档 `ai-dev/design/nop-stream/observability-design.md`（架构决策 + 拒绝替代方案）。
- runbook 深化 + docs-for-ai owner doc 落点 + INDEX/source-anchors 同步。

### Out Of Scope

- 上列 Non-Goals 全部；性能调优本身（item 15）。

## Execution Plan

### Phase 1 - 正式裁定与设计基线

Status: completed
Targets: `ai-dev/design/nop-stream/observability-design.md`、`ai-dev/design/nop-stream/README.md`

- Item Types: `Decision`

- [x] P-REQ-1..12 逐条正式三态裁定（预期与 D-GAP §3.3 初步建议一致；若 live 证据支持修正，记录依据），P-REQ-9（Web 控制台）落 defer 裁定与 revisit 条件（满足 P-REQ-9 验收的「显式裁定」分支；item 18 验收时可复核）；P-REQ-5 内部附 thread-dump 诊断端点的分期裁定（优先交付 vs 后置）
- [x] 设计文档落地：分层指标模型（复用 `GlobalMeterRegistry` 的分层组织 + **指标命名规范** + **P-REQ-1 五层视图与 P-REQ-3 job/cluster/node 指标族视图的映射关系**——具体指标名表不在本文档，唯一权威位置见下条）、指标/REST 暴露载体决策（独立进程形态下 HTTP 端点载体；micrometer-prometheus 既有依赖优先复用）、**REST submit 的作业提交语义决策**（提交对象是什么——XDSL spec / 序列化 JobGraph / 工厂引用、由哪个进程受理、如何到达 deployTask 执行路径——P-REQ-5 的前置设计，缺此决策 Phase 4 无法执行）、逻辑健康状态机语义映射（以既有 `JobStatus`/coordinator 状态为基线扩展而非另起体系，七态参照按 nop-stream 连续流语义裁剪）、AlertChannel 抽象边界（复用 nop-integration/nop-message 渠道抽象 vs runtime 内轻量渠道的取舍及模块依赖成本）、状态重置工具与离线 reshard 工具的入口收敛设计（D-GAP §2.5 观察）——含被拒绝替代方案及原因
- [x] **owner doc 落点裁定**：nop-stream 运维文档载体（新建 `docs-for-ai/03-modules/nop-stream.md` owner doc vs 最小扩展 `01-repo-map/module-groups.md`）——Phase 2/3/5 的文档增量按本裁定落位，落位文档随首个增量创建（不晚于 Phase 6 收口）
- [x] 设计文档 README 索引更新

Exit Criteria:

- [x] 裁定表落 `observability-design.md`（12 条 × 三态 + 依据，逐条可追溯 P-REQ 验收标准）；owner doc 落点裁定已落档
- [x] 设计文档不含实现级类签名/伪代码（guide 规则 14），架构决策均含拒绝替代方案
- [x] **无静默跳过**：本 phase 为纯决策，无新增公共方法分支（不适用，显式声明）
- [x] `ai-dev/design/nop-stream/README.md` 索引含新文档；doc-links 检查通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 指标与事件基座（P-REQ-1/2/8）

Status: completed
Targets: `nop-stream/nop-stream-runtime/`（指标注册体系；算子/任务层仪表可能触及 `nop-stream-core/`）、`nop-stream/nop-stream-rocksdb/`（recorder）

- Item Types: `Fix | Proof`

- [x] P-REQ-1 分层指标标准集落码：引擎线程/任务/算子/输入输出/状态后端各层，每层 ≥3 个指标注册 + 单测；**指标名与语义文档化成表**（Phase 1 命名规范下的具体名表，落 Phase 1 裁定的 owner doc 落点——落位文档随本表创建，此为唯一权威名表，observability-design.md 不复制）
- [x] P-REQ-2 作业进度事件监听 API：监听接口 + ≥1 内建日志实现落码；MiniStreamCluster e2e 或单测断言监听器被回调
- [x] P-REQ-8 RocksDB 指标 recorder：block cache/memtable/compaction 级统计注册；单测或 e2e 断言非空读数

Exit Criteria:

- [x] 每层指标有注册与单测（P-REQ-1 验收原文）；指标名语义表存在且被文档引用
- [x] 监听器在 e2e/单测中被断言回调（P-REQ-2 验收原文）
- [x] RocksDB recorder 非空读数断言存在（P-REQ-8 验收原文）
- [x] **接线验证**：指标注册点在真实执行路径（MiniStreamCluster 或 LOCAL e2e）中被实际更新，非仅类型存在
- [x] **新功能必有测试**：上述三类新功能各自列出对应新增测试用例名
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime,nop-stream/nop-stream-rocksdb -am` 全绿
- [x] owner-doc 裁定记录（指标名语义表落点）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 暴露面（P-REQ-3/4/6）

Status: completed
Targets: `nop-stream/nop-stream-runtime/`、资源模板目录

- Item Types: `Fix | Proof`

- [x] P-REQ-3 Prometheus/OpenMetrics 暴露：核心指标经 HTTP 以 TextFormat 004 与 OpenMetrics 格式暴露（载体按 Phase 1 设计决策）
- [x] P-REQ-4 metrics 配置模板：模板文件随资源目录提供，含 ≥3 类 sink 注释样例；文档引用该模板
- [x] P-REQ-6 checkpoint 运维观测：overview/history 查询接口与数据结构（复用并扩展既有 CheckpointMetrics/CheckpointMetricsSnapshot——failureCause 已存在，增量是查询接口与历史保留；查询载体按 Phase 1 暴露载体决策）；失败记录含 failureCause 字段

Exit Criteria:

- [x] 启动配置后 `curl /metrics`（或等价测试客户端）返回 TextFormat 004 内容，指标族覆盖 job/cluster/node 至少三级；有 e2e 或单测断言输出（P-REQ-3 验收原文）
- [x] 模板文件存在且覆盖 ≥3 类 sink 样例（P-REQ-4 验收原文）
- [x] overview/history 查询接口存在且至少一次 e2e 断言其输出；失败记录含原因字段（P-REQ-6 验收原文）
- [x] **端到端验证**：从作业启动 → 指标产生 → `/metrics` 暴露输出的完整路径有一条测试跑通（组件级单测不替代）
- [x] **无静默跳过**：暴露面未配置/未实现时为显式错误或显式关闭语义，非静默空输出
- [x] **新功能必有测试**：列出验证 TextFormat 输出、模板存在性、history 查询的新增用例名
- [x] owner-doc 更新裁定：`/metrics` 暴露与 metrics 配置模板的使用说明已落 Phase 1 裁定的 owner doc 落点（或显式记录 No owner-doc update required 的理由）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 运维操作面（P-REQ-5/10/11）

Status: completed
Targets: `nop-stream/nop-stream-runtime/`

- Item Types: `Fix | Decision | Proof`

- [x] P-REQ-5 REST 运维 API：submit/stop/running-jobs（+作业详情）生命周期端点优先；thread-dump 诊断端点按 Phase 1 分期裁定执行（交付或后置并记录）——已交付（`GET /jobs/{jobId}/threaddump`，Phase 1 裁定「随本设计交付」）
- [x] P-REQ-10 作业状态重置工具：清理本地状态 + 重置输入位点，支持全新重跑；与离线 reshard 工具入口收敛（按 Phase 1 设计）——`StreamStateResetTool` + `StreamMaintenanceMain`（reset-state/reshard 同一入口族）
- [x] P-REQ-11 历史作业与日志生命周期治理：保留时长/滚动/定期清理配置项 + 合理默认值——`StreamGovernanceConfig` + `OpsJobManager` 治理扫描

Exit Criteria:

- [x] REST 端点有用例覆盖（submit/stop/running-jobs 至少三类）；接口文档落 owner doc（`docs-for-ai/03-modules/nop-stream.md` REST 运维 API 节）
- [x] 重置工具类 + 手册章节存在（runbook §4 状态重置与维护工具）；e2e 演示重置后从起点正确重放（`TestStreamStateResetTool#resetThenReplayFromStart`）
- [x] 治理配置项存在且默认值合理；有测试或文档断言清理行为（`TestOpsRestLifecycleE2E#governanceSweepPrunesExpiredHistoryAndTerminalRecords`）
- [x] **端到端验证**：一条测试从 REST 提交作业 → 运行 → stop → 列表/详情查询完整走通（`TestOpsRestLifecycleE2E#fullLifecycleSubmitRunStopListDetail`，真实 TaskManager + remote-deploy 路径）
- [x] **无静默跳过**：REST 端点对未知作业/非法参数返回显式错误码（404/400/409，`endpointErrorSemantics`）；重置工具对不可重置位点显式报错而非静默清空（refusesNonReplayableSource / refusesActiveCoordinator / refusesMissingStateDirectory）
- [x] **新功能必有测试**：`TestOpsRestLifecycleE2E`（fullLifecycleSubmitRunStopListDetail / endpointErrorSemantics / threadDumpEndpointReturnsLiveStacks / governanceSweepPrunesExpiredHistoryAndTerminalRecords）、`TestStreamStateResetTool`（resetThenReplayFromStart / refusesNonReplayableSource / refusesMissingStateDirectory / refusesBlankArguments / refusesActiveCoordinator）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿（907 tests / 0 failures；`TestRocksDBIncrementalRestoreAndBenchmark` 为 -T 1C 负载抖动 flake，隔离复跑通过，见当日 log）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 健康状态机与告警闭环（P-REQ-7/12）

Status: completed
Targets: `nop-stream/nop-stream-runtime/`

- Item Types: `Fix | Proof`

- [x] P-REQ-7 流作业逻辑健康状态机：状态枚举 + 迁移合法性表落码 + 可注册状态监听器（以既有 `JobStatus`/coordinator 状态迁移为基线扩展，状态集按 Phase 1 设计裁剪自 KS 七态参照）——`health` 包（`StreamJobHealth` 七态 + `JobHealthStateMachine` 合法性表 fail-fast + `JobHealthListener`），接线进 JobCoordinator 真实生命周期路径
- [x] P-REQ-12 AlertChannel 抽象（按 Phase 1 裁定的渠道边界：runtime 内轻量渠道）+ ≥2 渠道实现落码——`alert` 包（`IAlertChannel`/`AlertEvent` + `LoggingAlertChannel`/`WebhookAlertChannel` + `AlertService` 路由），接线进 OpsJobManager 与 JobCoordinatorMain

Exit Criteria:

- [x] 状态枚举 + 迁移合法性表有单测（`TestJobHealthStateMachine` 8 例，含穷举合法性表对照）；监听器在 MiniStreamCluster e2e 中被断言调用（`TestMultiJvmHealthStateAndAlerts`，gated 启用态通过——coordinator 进程日志断言 health 迁移 + JOB_DEGRADED 事件 + alert 外发）
- [x] AlertChannel 抽象 + ≥2 渠道实现落码；故障注入测试断言事件外发（`TestAlertFaultInjectionE2E`：真实 TaskManager 部署 + 门控 source + 注入 sink 失败 → 真实失败链 FAILED report → recovery → cap → failJob → RECOVERY_STARTED/JOB_FAILED 告警断言）
- [x] **接线验证**：健康状态迁移由真实生命周期事件驱动（`TestJobCoordinatorHealthWiring` 5 例：start/globalRecovery/failJob/terminate/durable-checkpoint 回愈），告警事件由真实失败/恢复事件触发（故障注入断言），非仅类型存在
- [x] **新功能必有测试**：`TestJobHealthStateMachine`、`TestJobCoordinatorHealthWiring`、`TestAlertChannels`（路由表/渠道异常 containment/webhook JSON+重试/配置键 fail-fast 6 例）、`TestAlertFaultInjectionE2E`、`TestMultiJvmHealthStateAndAlerts`
- [x] owner-doc 更新裁定：健康状态语义表 + 告警配置键已落 `docs-for-ai/03-modules/nop-stream.md`（含 REST health 字段与 409 stop-during-recovery 语义）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿（runtime 928 tests / 0 failures；gated `TestMultiJvmHealthStateAndAlerts` 启用态绿）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 运维手册深化与文档收口

Status: planned
Targets: `ai-dev/design/nop-stream/distributed-runbook.md`、`docs-for-ai/`

- Item Types: `Fix | Follow-up`

- [ ] runbook 深化：补 metrics/REST/健康/重置/治理操作章节（与 gated 命令一一对应），并按其头部声明迁移/落点到 `docs-for-ai/`（落点 = Phase 1 裁定的 owner doc 落点，收口完整文档——增量内容已由 Phase 2/3/5 先行落位，本 phase 保证文档成体系而非补写）
- [ ] `docs-for-ai/INDEX.md` + `04-reference/source-anchors.md` 同步新增路由锚点
- [ ] runbook §5「背压无直接指标」条目收口裁定：若已交付指标面覆盖背压量化则改指向具体指标名；否则显式记录「背压量化仍以代理观察为准 + 指标缺口记 Follow-up 候选」（两种结局均可勾选，不允许保留原文不动）

Exit Criteria:

- [ ] 运维手册含启动/停止/重置/savepoint-等价（恢复）操作章节，每章步骤可执行（与 live 行为一致性抽查 ≥2 章通过）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] owner-doc 更新裁定已记录（本 plan 改变 live baseline，owner doc 必须同步）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] P-REQ-1..12 逐条状态为 met（对应验收标准全部成立）或 adjudicated（defer/exclude + 依据记录于 observability-design.md），无悬空项
- [ ] 全部 go 项代码交付含 focused 测试 + e2e 断言（Anti-Hollow：组件在真实执行路径被调用）
- [ ] 不存在被静默降级到 deferred 的 in-scope P-REQ（P-REQ-9 defer 需含 revisit 条件）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿，且 gated 启用态绿（同命令加 `-Dnop.stream.test.multi-jvm.enabled=true`——runbook §4 场景集 + legacy 集合）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] 独立子 agent closure audit 完成且 evidence 写入 Closure 段

## Deferred But Adjudicated

### P-REQ-9 Web 控制台/流式页签

- Classification: `out-of-scope improvement`（roadmap P2；Phase 1 落正式 defer——满足 P-REQ-9 验收的「显式裁定」分支；item 18 最终验收时按 revisit 条件复核）
- Why Not Blocking Closure: REST + 指标 + 健康面落地后按用户反馈裁定（D-GAP §3.3 初步建议）；AMIS 低成本实现路径保留；defer 而非 exclude 保守保留选项
- Successor Required: no
- Successor Path: defer revisit 条件记录于 observability-design.md

## Non-Blocking Follow-ups

- thread-dump 诊断端点若 Phase 1 分期裁定为后置：记录于 observability-design.md，随首个用户反馈迭代（不阻塞 closure，P-REQ-5 验收以生命周期端点为准——D-GAP §3.3 建议原文）。
- F-2 stop-edit-restart 执行后（若发生），P-REQ-12 归属语义从过渡安排转正式——不影响本 plan 交付物。

## Risks And Rollback

- 向独立进程 runtime 引入 HTTP 端点（`/metrics`、REST）是本 plan 最大的行为面变更：暴露面默认关闭/按配置启用可最小化风险；若验收失败，暴露面配置项回退到关闭态即可恢复原行为，指标注册本身无副作用。
- AlertChannel 若引入 nop-integration 模块依赖被 Phase 1 裁定为过重，回退路径为 runtime 内轻量渠道（log/webhook）+ 渠道抽象保持不变——决策记录于 observability-design.md。

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- no remaining plan-owned work（或记录 non-blocking 项）
