# W8 可观测性指标契约与实现（OBS-01）

> Plan Status: completed
> Last Reviewed: 2026-08-15
> Review Consensus: 三轮独立 fresh-session 对抗性审查达成共识——R1: 1 Major（熔断迁移并发近似语义未预置）+ 6 Minor；R2: 1 Major（scan-hollow `--module` 空扫描假绿，已实证）+ 2 Minor；R3: approve（实机验证 scan-hollow 位置路径真实扫描 + exit 0；全部引用 live 核实；0 Blocker 0 Major）
> Source: `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W8 OBS-01）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.6 可观测性 + §五 Q4）、`ai-dev/design/nop-ai-gateway/01-architecture.md`
> Related: `ai-dev/plans/2026-08-15-1116-2-nop-ai-gateway-w6-local-adapter-streaming-failover.md`、`ai-dev/plans/2026-08-15-1116-3-nop-ai-gateway-w7-gateway-interceptor-converter.md`、`ai-dev/plans/2026-08-15-1615-2-nop-ai-gateway-w8-regression-and-docs-closure.md`（W8 OBS-02/03/04，本计划的执行顺序 successor）
> Mission: nop-ai-gateway-failover
> Work Item: W8 (OBS-01)

## Purpose

生产化收口第一半——定义并落地 failover 可观测性指标契约（指标名/维度/单位/语义）与实现，覆盖本地适配器与网关拦截器两种形态的切换/重订阅、熔断器状态迁移、冷却期计数、账号成功率/延迟、并发饱和事件，经平台既有 micrometer 设施（`GlobalMeterRegistry`）暴露，并将契约落档 `02-account-failover-requirement.md` §3.6。指标观测不得改变既有 failover 行为（零回归），不侵入 nop-ai-core 原语内部实现。

## Current Baseline

- W1-W7 已落地（roadmap `done`）：编排代码位于 nop-ai-gateway `io.nop.ai.gateway.failover`（`ChatServiceFailoverAdapter` 本地形态、`AiGatewayFailoverInterceptor` 网关形态、`FailoverStreamFlow` 流式缓冲/重订阅、`GatewayStreamingLifecycleListener` 流生命周期并发计数、`FailoverProbeSupport` 熔断探活）；游走原语位于 nop-ai-core `io.nop.ai.core.routing`（`ModelClassRouter`/`ConcurrencyRegistry`）与 `io.nop.ai.core.reliability`（`ThresholdBreaker`/`CircuitState`/`ICircuitBreaker`）。
- **nop-ai-gateway 当前无任何指标埋点**（live repo 核实：`rg "Metrics" nop-ai/nop-ai-gateway/src/main/java` 零命中）。
- 平台指标设施：micrometer 经 nop-commons `io.nop.commons.metrics.GlobalMeterRegistry`（`instance()` 返回 `MeterRegistry`）；既有先例 = nop-job `IJobWorkerMetrics`/`JobWorkerMetricsImpl`（接口 + micrometer 实现 + GlobalMeterRegistry 构造，`nop-job/nop-job-worker`）。
- 可观察点已具备：`ThresholdBreaker.getState(modelKey)` 三态可查（CLOSED/OPEN/HALF_OPEN）；`ConcurrencyRegistry` 当前计数可查；编排层已拥有全部事件调用点（`recordFailure`/`recordSuccess`/`allowCall`、`acquire`/`release`、全池饱和 fail-loud `ERR_AI_MODEL_CLASS_SATURATED`、流式重订阅、熔断探活）。
- 需求规格已定语义面：§3.6 要求"切换次数、熔断器状态迁移、冷却期计数、各账号成功率/延迟指标"（**指标名在实现时定契约**，§五 Q4）；§3.3 要求全池饱和产出饱和指标（"并产出饱和指标（§3.6 可观测性）"）。
- **真实 gap**：指标契约未定义、指标服务不存在、事件点无埋点、§3.6 无契约落档。

## Goals

- 定义 failover 指标契约并落档 `02-account-failover-requirement.md` §3.6（指标名族、维度、单位、语义、触发事件），逐项覆盖：切换次数（含流式重订阅）、熔断器状态迁移、冷却期（探活/拒绝）计数、账号成功率/延迟、并发饱和（全池饱和 fail-loud）——§3.6 表格四类 + §3.3 饱和类联合覆盖（§3.6 表格实际列切换/熔断迁移/冷却期/成功率延迟四类，第 5 类饱和由 §3.3"并产出饱和指标"指向 §3.6，落档时以 §3.6 + §3.3 联合口径覆盖，不得只按 §3.6 表漏掉饱和）。
- 新增指标服务（接口 + 默认 micrometer 实现，bean 注册），从编排层全部相关事件调用点接线；指标采集缺省启用且零行为影响（不影响选择/切换/重试语义）。
- 每个指标类别至少有 1 个 focused 测试在对应事件路径上断言（计数/计时增量可观测）。
- 熔断状态迁移指标从编排层调用点派生（`recordFailure`/`recordSuccess`/`allowCall` 前后状态对比或等价观察机制），**不改动 `ThresholdBreaker`/`ConcurrencyRegistry` 内部状态机与公共方法语义**（nop-ai-core 原语保持零改动；若执行期裁定需要 core 侧观察点，必须先经 plan-first 评估且零行为变更）。
- 既有 failover 测试全量零回归（W6 32 用例 + W7 13 用例 + 既有网关用例）。

## Non-Goals

- 全链回归测试矩阵收口（OBS-02，归 successor plan `2026-08-15-1615-2` Phase 1）。
- `docs-for-ai/` 使用文档与 INDEX/source-anchors 同步（OBS-03，归 successor plan `2026-08-15-1615-2` Phase 2）。
- 需求文档 Q 表状态收口（OBS-04，含 Q4 状态标记，归 successor plan `2026-08-15-1615-2` Phase 3；本计划只落 §3.6 契约正文）。
- 多实例指标聚合、告警规则/阈值、运维面板、指标持久化（多实例一致性为 requirement §3.6 显式 non-goal；告警/面板/持久化为本期非要求范围，见 Deferred）。
- 总延迟上限（time-based retry budget）落地（W6/W7 已裁定默认 null，optimization candidate，见 Deferred）。
- 任何新 failover 功能或行为变更。

## Scope

### In Scope

- 指标契约定义与落档（`02-account-failover-requirement.md` §3.6：契约表 = 指标名/维度/单位/语义/触发事件/归属类）。
- 指标服务：接口 + 默认实现（micrometer，`GlobalMeterRegistry`，nop-job 先例模式）+ nop-ai-gateway bean 注册（`ai-gateway-defaults.beans.xml`）；micrometer 依赖经 nop-commons 传递，必要时显式声明。
- 接线（本地形态）：`ChatServiceFailoverAdapter` 非流式切换链（切换/重发、成功/失败、耗时）、流式重订阅（`FailoverStreamFlow` 重订阅点）、熔断记账/探活恢复调用点、全池饱和 fail-loud 路径、并发计数配对路径。
- 接线（网关形态）：`AiGatewayFailoverInterceptor` `onRequest`/`invoke`/`onError`/`onStreamElement` 对应事件点、`GatewayStreamingLifecycleListener` 流建立/终止点、`GatewayStreamingRetryCallback` 重执行（重订阅）点。
- 熔断器状态迁移与冷却期计数指标（编排层调用点派生）。
- focused 指标测试（新增测试类或扩展现有测试类）。
- `ai-dev/logs/` 对应日期条目。

### Out Of Scope

- OBS-02/03/04 全部交付物（归 `2026-08-15-1615-2`）。
- nop-ai-core 原语行为/API 变更（`ThresholdBreaker`/`ConcurrencyRegistry`/`ModelClassRouter` 内部不改；观察机制若需 core 侧支持，属 plan-first 前置评估，不在本计划默认 scope）。
- 指标对外的 HTTP/监控端点、告警、聚合（平台暴露面）。

## Execution Plan

### Phase 1 - 指标契约裁定与落档

Status: completed
Targets: `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.6）、live failover 代码盘点

- Item Types: `Decision | Proof`

- [x] 盘点 live 事件点：本地形态（切换/重发、流式重订阅、熔断记账、探活、饱和、并发配对）与网关形态（onRequest/invoke/onError/onStreamElement、生命周期回调、重执行回调）的每个可观测事件，形成事件 → 指标类别映射表（切换次数、熔断状态迁移、冷却期计数、成功率/延迟、并发饱和）
- [x] 裁定指标契约：命名族（`nop.ai.gateway.failover.*` 或等价约定）、每个指标的名称/维度（provider/model/account 等）/单位/语义/触发事件；熔断迁移指标的观察机制裁定（编排层派生，core 原语零改动），且**必须落档并发近似语义**：`ThresholdBreaker.getState` 为无锁读（类 javadoc 明确），编排层 before/after 对比在并发交错下可能对同一次迁移重复计数或错误归因——契约语义列须显式写明"迁移计数为近似观测（并发下可能重复/归因近似）"，禁止把精确语义写进契约
- [x] 裁定冷却期计数的事件定义（三候选至少其一并强制映射：OPEN 迁移 = 冷却启动、allowCall=false 的未期满拒绝、HALF_OPEN 探活占用拒绝；不得选择"永不触发"的定义）
- [x] 契约落档 `02-account-failover-requirement.md` §3.6（契约表 + 落地状态注记，覆盖 §3.3"全池饱和产出饱和指标"与 §3.6 四类要求），并加一句"本契约即 §五 Q4 先行文档化的落地"
- [x] 配置形态裁定：指标采集是否可开关（默认启用；若引入开关，缺省值必须为启用/零行为变化）；**null-object 与 fail-fast 边界裁定**——指标 bean 未装配/缺依赖时，注入方行为是显式失败（fail-fast）还是降级 no-op（部署选择），裁定结果与"无空壳"要求（Phase 2 item 1）的边界一并落档，避免执行期自相矛盾

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 契约表已写入 §3.6：每类要求（切换/熔断迁移/冷却期/成功率延迟/并发饱和）对应 ≥1 个具名指标，含维度/单位/语义
- [x] 每个具名指标在 plan 内映射到 ≥1 个 live 代码事件点（类名 + 方法名可核）
- [x] 熔断迁移指标观察机制不改变 `ThresholdBreaker` 公共语义（review 核实无 core 公共 API 变更；如执行期裁定需 core 观察点，先 plan-first 评估再动）
- [x] 契约表已含并发近似语义落档（迁移计数 = 近似观测）与冷却期事件定义
- [x] null-object / fail-fast 裁定结果与"无空壳"边界已落档（§3.6 注记或执行记录，Phase 1 item 5 有对应 Exit 验证）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 指标服务实现与接线

Status: completed
Targets: `nop-ai/nop-ai-gateway`（`io.nop.ai.gateway.failover` + beans）

- Item Types: `Fix | Proof`

- [x] 新增指标服务接口（如 `IFailoverMetrics`）与默认实现（micrometer + `GlobalMeterRegistry`，nop-job `IJobWorkerMetrics`/`JobWorkerMetricsImpl` 模式），无空壳/无静默跳过（所有接口方法有真实实现或显式 UOE）
- [x] bean 注册 `ai-gateway-defaults.beans.xml`，注入本地适配器与网关拦截器（含生命周期监听器/重执行回调所需通道）；缺省启用、零行为影响
- [x] 本地形态接线：切换/重发计数、流式重订阅计数、成功/失败计数与耗时 Timer、熔断记账与迁移、探活/冷却期计数、并发饱和 fail-loud 计数、并发配对路径观测
- [x] 网关形态接线：onRequest 接管计数、invoke 非流式切换/重发与耗时、onError 分类降级、onStreamElement per-attempt 反向转换计数、生命周期回调（流建立/终止）、重执行回调（重订阅）
- [x] 熔断状态迁移与冷却期计数指标按 Phase 1 裁定机制落地（编排层调用点派生，core 原语零改动）
- [x] 指标采集失败/异常不得影响 failover 主流程（观测面独立于控制面，异常不外泄为业务错误；**若捕获异常必须记录日志**，禁止静默吞掉——与"无静默跳过"规则一致）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 每类契约指标（Phase 1 清单）在对应 live 事件点真实接线，`rg`/调用链可核（不是只建类型不调用）
- [x] **接线验证**：至少一个端到端指标断言——从适配器/拦截器入口触发事件到指标计数变化（如触发一次切换后对应计数器 +1，测试断言）
- [x] **无静默跳过**：指标服务无空方法体/吞异常/占位返回；未接线的事件点显式失败或已接线
- [x] 既有 failover 测试全量零回归（W6 32 + W7 13 + 既有网关用例）
- [x] `No owner-doc update required` 不适用——requirement doc §3.6 契约已在 Phase 1 更新；`ai-dev/logs/` 已更新

### Phase 3 - focused 测试收口

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/test`

- Item Types: `Proof`

- [x] focused 指标测试：每个契约指标类别 ≥1 断言（切换计数、重订阅计数、熔断迁移计数、探活/冷却计数、成功/失败计数与 Timer、饱和计数），经真实事件路径触发（复用 `FailoverTestSupport`/`W7GatewayTestSupport` 基建）
- [x] 指标维度断言（provider/model/account 维度标签存在且正确）
- [x] 测试稳定性：无 flaky 计时断言（Timer 断言用宽松阈值或只断言计时器存在/已记录）
- [x] 全量相关模块测试绿色：`./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 每个契约指标类别至少有 1 个 focused 测试断言（测试类名 + 断言内容可核）
- [x] 指标接线从入口到计数全链被测试覆盖（接线验证，Phase 2 端到端断言在测试套件内固化为回归）
- [x] 无 flaky：连续运行 2 次全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] §3.6 指标契约已落档（名/维度/单位/语义 + 事件映射）
- [x] 指标服务已实现并接线到两种形态的全部规定事件点
- [x] 熔断状态迁移/冷却期指标不改变 `ThresholdBreaker`/`ConcurrencyRegistry` 公共语义（core 原语零改动）
- [x] focused 指标测试全绿 + 既有 failover 测试零回归
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline（requirement §3.6 + §3.3 引用；`01-architecture.md` 如无变更显式确认 No owner-doc update required）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）指标接线在运行时确实被调用（入口 → 指标计数全链连通，测试断言实证），（b）无空方法体/静默跳过/no-op 作为指标实现
- [x] `./mvnw compile`（`-pl :nop-ai-gateway -am`）
- [x] `./mvnw test`（`-pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C`）
- [x] checkstyle / 代码规范检查通过（imports 分组 io.nop.* → 第三方 → java.*）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本 plan 文件> --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs nop-ai/nop-ai-gateway --severity high` 退出码 0（**用位置路径**：`--module nop-ai-gateway` 会解析到不存在的 `<root>/nop-ai-gateway` 空扫描恒绿，禁止使用）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（本 plan 修改 `ai-dev/design/` 下 requirement doc）

## Deferred But Adjudicated

### 总延迟上限（time-based retry budget）指标

- Classification: `optimization candidate`
- Why Not Blocking Closure: W6/W7 已裁定重试预算默认 null = 仅次数预算（Q3 执行期裁决）；次数预算已构成重试预算契约，总延迟上限是运维级可选约束，默认关闭不影响 §3.6 指标契约成立；账号成功率/延迟 Timer 已覆盖请求耗时观测面。
- Successor Required: `no`

### 多实例指标聚合 / 告警阈值 / 运维面板 / 指标持久化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: requirement §3.6 显式 non-goal（账号健康状态进程内、跨节点收敛不保证）；指标以进程内单例 `GlobalMeterRegistry` 暴露，聚合/告警属部署面增强。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 指标名族对外公布（`docs-for-ai/` 使用文档含指标清单）归 successor plan `2026-08-15-1615-2` Phase 2（OBS-03）。
- Q4（指标契约是否先行文档化）状态标记归 `2026-08-15-1615-2` Phase 3（OBS-04），本计划只落 §3.6 契约正文。

## Closure

Status Note: W8 OBS-01 全 3 Phase 落地——§3.6 指标契约（11 指标行覆盖切换/重订阅/熔断迁移/冷却期/成功率/延迟/饱和/并发配对/接管/降级/反向转换）+ `IFailoverMetrics`/`FailoverMetricsImpl`/`CircuitObservation`/`CooldownEventType` + 两形态全事件点接线 + 8 focused 指标测试；独立 closure audit（fresh general subagent，read-only）10 维全 PASS 且 Anti-Hollow 验证通过；nop-ai-core 原语零改动（git diff 空）。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，read-only）
- Audit Session: `ses_ffb504316ffeX7gS4Oa7gajPmu`
- Evidence:
  - Exit Criteria：Phase 1（6/6 PASS：契约表 §3.6 含近似语义/冷却期三事件/null-object-fail-fast 边界 + 事件点映射可核）；Phase 2（5/5 PASS：每类指标 live 事件点真实接线，rg/调用链逐点核 + 端到端指标断言固化在测试套件 + 无空方法体/吞异常 + 零回归）；Phase 3（4/4 PASS：每个契约类别 ≥1 focused 断言（TestFailoverMetricsLocal 5 + TestFailoverMetricsGateway 3）+ 维度标签断言 + Timer 仅断言已记录（flaky-free）+ 连续 2 次全绿）。
  - Closure Gates 14/14：contract 落档（`02-account-failover-requirement.md` §3.6:196-209 契约表 + :215 裁定）；接线（adapter/flow/interceptor/retry-callback/lifecycle-listener 全部事件点）；core 零改动（`git diff --stat nop-ai/nop-ai-core` 空，`ThresholdBreaker` javadoc 无锁读实证 :38-39）；测试（160 用例两次全绿：W6 32 + W7 13 + 既有网关 + 8 新指标）；deferred 分类诚实（time-based budget = optimization candidate；聚合/告警/面板 = out-of-scope improvement，均合 §3.6 non-goal）；owner doc 同步（requirement §3.6 + §3.3；`01-architecture.md` 无架构级变更 → No owner-doc update required）；Anti-Hollow（接线运行时连通实证：测试断言从入口到计数 + scan-hollow 0 finding）；compile PASS；test PASS ×2；checkstyle 无新违规（Maven 默认 Sun 规则集非有效门禁，与仓内既有基线一致，mission lint 命令 `|| echo 'lint not configured'` 回退）；`check-plan-checklist.mjs --strict` exit 0；`scan-hollow-implementations.mjs nop-ai/nop-ai-gateway --severity high`（位置路径）exit 0（Critical 0 / High 0）；`check-doc-links.mjs --strict` exit 0。
  - Deferred 项分类检查：无 in-scope live defect 被降级（audit 逐项核实）。
  - 非阻塞观察（audit）：`probe-rejected` 冷却事件仅 0-计数断言（正向触发需并发，代码路径真实可达 `CircuitObservation.java:62-63`，契约口径满足）——记录不阻塞。

Follow-up:

- no remaining plan-owned work（OBS-02/03/04 归 successor plan `2026-08-15-1615-2`）
