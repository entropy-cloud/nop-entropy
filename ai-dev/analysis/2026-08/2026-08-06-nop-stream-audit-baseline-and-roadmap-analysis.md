# nop-stream 深度审计基线与路线图分析

> Status: resolved
> Date: 2026-08-06
> Scope: `nop-stream/` 全部 10 个子模块、设计文档、历史审计、现有 remediation plans 与测试证据
> Conclusion: 后续工作应以独立审计路线图驱动；先关闭现有 3 份 active production remediation plans，再按端到端语义审计工作流验证 shipped claims，并仅从已复核发现拆分新的修复计划。

## Context

- `nop-stream` 已经历多轮设计、Flink 对标和 production 审计，历史记录同时包含已修复问题、已裁定 non-goal 和仍未实施的确认缺陷。
- 本分析回答：后续应直接写修复计划，还是先建立可审计的 current baseline 与路线图。
- 约束：不得把组件存在、历史 plan 标记 `completed` 或单元测试通过当作端到端生产语义成立的证据。

## Analysis

### Design Target

- 定位：Nop 平台内、模型优先、中小规模、可嵌入并可分布式运行的有状态流引擎，而不是 Flink 的全量替代。依据：`ai-dev/design/nop-stream/00-vision.md`。
- 三个入口 Java DataStream API、XDSL、Delta 应归一到 `StreamModel`，经 `StreamGraph -> JobGraph -> PartitionedPlan -> DeploymentPlan` 进入运行时。依据：`ai-dev/design/nop-stream/01-architecture-baseline.md`。
- 目标能力类别包括 state、time/window、CEP、checkpoint/recovery、连接器与 LOCAL/DISTRIBUTED 执行；具体入口、模式、后端和 guarantee 组合是否支持，必须由 capability matrix 取证，不得由设计愿景推定。
- 明确 non-goal 包括 SQL/Table API、双流 join、专用 BroadcastStream/BroadcastState、异步算子、PB 级吞吐和 Flink 全套 runtime。审计不得把这些裁定为“缺功能”，但必须验证 API/DSL 不会静默接受或错误宣称支持。

### Evidence State

| Evidence class | Can prove | Cannot prove |
|---|---|---|
| 设计文档 | 目标、约束、non-goal、应有语义 | 当前代码已实现 |
| 历史 audit | 当时发现与盲区 | 当前 HEAD 仍有该缺陷 |
| completed plan | 曾声明的修复与验证证据 | 后续改动未回归、所有相邻路径正确 |
| 单元测试 | 覆盖的组件行为 | 组件间运行时接线与故障语义 |
| 入口到出口 E2E 与故障注入 | 用户可用的实际行为 | 未覆盖的配置、拓扑或外部后端 |

### Historical Findings

- 5 至 6 月审计集中暴露 checkpoint、窗口、CEP、connector、并发、错误处理和测试有效性问题；`ai-dev/plans/00-plan-authoring-and-execution-guide.md` 以此前 checkpoint 空壳为反例，明确要求端到端与接线验证。
- 7 月 Flink 对标将差异汇总为 `G1-G68`/`D69-D73`，见 `ai-dev/analysis/nop-stream/08-gap-analysis.md`。该文档混入后续 closed/deferred 注记，不能单独作为 live baseline。
- `ai-dev/backlog/nop-stream-production-roadmap.md` 记录了大量已完成 stage；其价值是 ownership 和历史证据，不足以替代独立复验。
- 最新 production 审计 `ai-dev/audits/nop-stream-production/2026-08-02-2107-*.md` 在已完成 stage 之后仍发现恢复并发、checkpoint/state 持久化、CEP 和测试完整性问题，证明“completed plan”不是全模块 production-ready 结论。

### Current Blocking Baseline

以下确认缺陷已由 active plans 认领，不应由新审计路线图重新立项修复：

| Area | Confirmed issue | Owner plan |
|---|---|---|
| Coordinator/recovery | `JobCoordinator` recovery race、`InputGate` 跨线程对齐状态、TaskManager permit leak、SupervisionLoop zombie restart | `ai-dev/plans/nop-stream-production/2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md` |
| Checkpoint/state/CEP | incremental restore fail-fast 缺失、shared SST ref leak、PostgreSQL upsert、CEP SharedBuffer stack desync | `ai-dev/plans/nop-stream-production/2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md` |
| Contract/config/tests | state SPI/design drift、缺 `_module`、daemon 与 transformation 空心测试 | `ai-dev/plans/nop-stream-production/2026-08-04-2300-3-contract-drift-config-test-integrity.md` |

这些计划均为 `active` 且各 Phase 未完成。它们是新一轮深度审计的硬前置；审计可复核其 closure，不能把它们重复改写为新的 remediation work item。

### Design And Documentation Drift

- `docs-for-ai/INDEX.md` 与 `docs-for-ai/01-repo-map/module-groups.md` 的 nop-stream 子模块清单落后于 reactor 当前 10 模块布局。
- 多份 `ai-dev/design/nop-stream/` 文档混合目标态、历史阶段和 current-state 叙述；operator state、RocksDB、flow/XDSL、HA、region failover 等表述互相冲突。
- `nop-stream-production-roadmap.md` 也保存了大量 stage 历史细节；后续 audit 不应仅根据其 `done` 状态作结论。
- 因此首先需要建立 capability matrix，按“已端到端证明 / 仅组件证明 / 未验证 / fail-fast / non-goal”分类，而不是继续维护二元“已实现/未实现”描述。

### Required Audit Semantics

每个支持性结论至少必须具有：

1. 公开入口、DSL 节点或 SPI 的 live 实现锚点。
2. 从入口至 output/外部效果的运行时调用链。
3. 正确结果断言，不只是“不抛异常”。
4. 与能力相关的异常、取消、重启或恢复断言。
5. LOCAL、嵌入式 distributed、真实多 JVM 的适用范围。
6. 对未支持路径的 fail-fast 证明。

关键审计面：

- 三入口归一、stable identity、graph/plan 编译与远程本地重建。
- 分支、fan-out、parallelism、partition、backpressure、source/sink 生命周期。
- checkpoint barrier、multi-epoch、aligned/unaligned、state/timer/source/sink transaction、manifest、savepoint、schema/key-group migration。
- state backend、TTL、incremental SST、retention、segment integrity 与资源回收。
- watermark、window、session merge、pane、CEP branching/skip/shared buffer。
- 多 JVM RPC、IMessageService data plane、fencing、leader switch、failure detection、recovery 和 region failover。
- JDBC/file/message/CDC connector 的真实 capability，尤其是 strict exactly-once 不得由基础设施存在而误判。
- 测试有效性、disabled/gated tests、假测试、工具阳性对照和历史修复回归。

### Rejected Alternatives

- 直接新建一个“修复 nop-stream 全部问题”的 plan：范围不可关闭，与 3 份 active plan 冲突，且无法确保每项有可观察 exit criteria。
- 仅维护现有 production roadmap：它记录建设 ownership，不能独立证明 current behavior，且在状态叙述上已承担过多历史信息。
- 只做静态代码审查：无法证明 checkpoint、恢复、external effect、跨 JVM 传输与 IoC discovery 的接线语义。

## Conclusion

- 采用独立审计路线图 `ai-dev/backlog/nop-stream-independent-audit-roadmap.md`。
- 该路线图不替代 `ai-dev/backlog/nop-stream-production-roadmap.md`，也不改变其状态；它只定义对 production roadmap 的独立验证与新增发现的归属规则。
- 先完成并独立 closure-audit 三份 active production remediation plans；第一个 successor audit plan 必须只完成 capability inventory/evidence schema，随后单独完成环境资格认定，再按细分技术域取证。
- 每轮审计完成后，只有 confirmed live defect 才能拆成新的 execution plan；non-goal 与经裁定的 residual 必须保留理由和 owner。
- 每个 historical stage 和历史 finding（包括 P2）必须取得且仅取得一个 disposition：复验通过、已失效、发现 live defect 并归入 active/successor plan，或以明确 non-blocking 理由接受 residual risk。

## Open Questions

- [ ] 真实 PostgreSQL、Kafka/Pulsar 与多 JVM E2E 是否能在默认 CI 环境执行，还是需要 gated evidence lane？
- [ ] 三入口是否要求 byte-identical fingerprint，还是定义为等价逻辑 topology、stable operator identity 与等价恢复语义？
- [ ] 现有 design 文档应由独立 doc-reconciliation plan 收敛，还是在每个行为审计 work item 中就近更新？

## References

- `ai-dev/design/nop-stream/00-vision.md`
- `ai-dev/design/nop-stream/01-architecture-baseline.md`
- `ai-dev/analysis/nop-stream/02-nopstream-live-audit.md`
- `ai-dev/analysis/nop-stream/08-gap-analysis.md`
- `ai-dev/backlog/nop-stream-production-roadmap.md`
- `ai-dev/audits/nop-stream-production/2026-08-02-2107-multi-audit-nop-stream-production.md`
- `ai-dev/audits/nop-stream-production/2026-08-02-2107-open-audit-nop-stream-production.md`
- `ai-dev/plans/00-plan-authoring-and-execution-guide.md`
