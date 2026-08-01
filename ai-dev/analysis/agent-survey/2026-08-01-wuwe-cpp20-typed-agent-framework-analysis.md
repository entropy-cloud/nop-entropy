# Wuwe C++20 类型安全 Agent 框架深度分析 & Nop AI Agent 架构启发

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/Wuwe`（lkimuk/Wuwe，C++20 原生 AI agent 框架，v0.1.0，~37k 行）vs `nop-ai-agent`（Java DSL-first）。注：本项目不在 7 月博客 60 篇范围内，应用户要求补充分析。
> Conclusion:

## 一、总览

**Wuwe** 是 C++20 原生 AI agent 框架（147 头文件 + 38 .cpp，约 37,449 行），面向本地应用/服务/CLI。自研编译期反射库 **gmp（Generative Metaprogramming）** 作为类型安全基石。16 个子模块覆盖 agent 全栈能力。

| 维度 | wuwe | nop-ai-agent |
|------|------|--------------|
| 语言/范式 | C++20（concept/constraint/模板元编程） | Java 21（DSL-first / XDEF） |
| 工具类型安全 | 编译期 concept + gmp 反射（聚合体即契约） | DSL 声明（手写 schema） |
| 生命周期事件 | 22 种（observer + callbacks 双通道） | 12 种（AgentLifecyclePoint） |
| 治理分层 | capability/policy/approval/enforcement 四分离 | security 多层 + guardrail |
| 沙箱 | "能力诚实"合约（逐控制项 enforcement 级别） | 无显式沙箱合约 |
| 预算 | typed reserve（per-resource 独立闸） | — |
| checkpoint | file_plan_store（trunc 重写，非 append-only） | DBCheckpointManager（append-only INSERT） |
| 版本/成熟度 | v0.1.0，82 commits，单人早期项目 | 生产级 |

## 二、核心机制详解

### 2.1 编译期类型安全工具系统（`tools/tool.hpp`）
- **C++20 `concept tool_type`**（:50）：约束"聚合体 + description + invoke"——普通 struct 即工具契约。
- **gmp 编译期聚合体反射**：`build_object_json_schema`（:245）用 `gmp::member_names<T>()` 遍历字段，**零样板生成 JSON Schema**。
- `field<T>`（:64）：携带 description/default 的类型化字段。
- `tool_provider<Tools...>`（:649）：用 **fold expression** 分发——编译期展开所有工具的调用逻辑。
- 运行时零反射开销——全部在编译期模板展开。

### 2.2 Reasoning（`reasoning/reasoning_core.hpp`、`reasoning_runner.hpp`）
- **4 种推理模式**（`reasoning_core.hpp:27`）：simple / react / reflect_and_retry / plan_execute。
- `reasoning_runner::run`（:149）按 mode 分发。
- **预算即一等公民**：`reserve_model_call`（:698）/ `reserve_tool_call`（:728）/ `reserve_reflection_call`（:713）——超限产生 typed error（`reasoning_error_code` 枚举），而非静默继续。
- `std::jthread` + `std::stop_token` 协作取消（:233）。
- **22 种生命周期事件**（:158）经 observer/callbacks 双通道下发。

### 2.3 Reflection（`reflection/reflection_core.hpp`）
- **rubric + score 阈值 → action 映射**（:19）：pass / revise / retry / replan / block / escalate。
- `reflection_policy_engine::action_for`（:217）：按 **worst severity** 决策（最严重的问题决定 action）。
- 提供 result normalizer / merger。

### 2.4 Planning（`planning/plan.hpp`）
- `plan_step`（:112）：带依赖图（`depends_on`）、`assigned_tool/agent`、`requires_approval`。
- `plan_step_status`（:22）：pending / running / completed / failed / skipped / blocked——6 态。
- **`plan_validator`**（:314）：检查 id/依赖/环（`has_dependency_cycle_from`）——**编译期 + 运行期双重 DAG 校验**。
- `plan_runner`：找 ready step（依赖全完成）、并行 `max_parallel_steps`、重试、replan、checkpoint。
- `reset_running_steps_on_resume`（:161）：恢复时把 running 重置为 pending（防止"半完成"状态）。
- **注意**：`file_plan_store` 用 `std::ios::trunc` 整体重写（`plan_store.hpp:132`），**非 append-only**——崩溃安全性弱。

### 2.5 Memory（`memory/`）
- scope 锚定：user / app / conversation / agent。
- file/sqlite 持久化。
- `in_memory_vector_index`：**线性扫描 cosine 后 std::sort**（`in_memory_vector_index.hpp:48-81`，README 已诚实声明不适合大规模）。
- lexical/hybrid ranker；Qdrant 远程索引可选。

### 2.6 Capability/Approval（`capability/`、`approval/`）——四治理关注点分离
- **capability_request** + risk level（`capability.hpp:17`）：声明"我要做敏感动作"。
- **capability_policy 三态决策**（`capability_policy.hpp:12`）：allow / deny / **require_approval**。
- **approval_service 虚接口**（`approval_service.hpp:16`）+ deny_all/allow_all 两个默认实现（:28）：decision 带 **once/session/workspace scope**。
- 四层严格分离：**capability**（声明）→ **policy**（判定）→ **approval**（授权）→ **enforcement**（执行）。
- **框架不拥有身份/密钥/审批/留存**——全交 host。

### 2.7 Audit（`audit/audit.hpp`）
- 结构化 `audit_event`（:22）：module / name / **trace_id** / subject_id / **outcome 9 态**（attempted/allowed/denied/approved/started/completed/failed/cancelled/timed_out）/ timestamp / attributes。
- `audit_sink` 接口——**仅 in_memory 实现**（`audit_sink.hpp:18`），持久化由 host 负责。
- trace_id 贯穿 reasoning→plan→execution→audit。

### 2.8 Sandbox——"能力诚实"合约（`sandbox/sandbox.hpp:37`）
- `sandbox_enforcement_contract`：逐字段声明 **enforcement_level**（enforced / partial / not_enforced / planned）。
- **"能力诚实"设计**：按合约而非后端名推断——后端必须自报每个控制项的实际 enforcement 级别，拒绝用后端名暗示能力。
- README 明确声明 controlled_process "不是强沙箱"——诚实而非过度承诺。

### 2.9 Controlled Execution（`execution/`）
- `execution_runtime`（:19）：装配 backend+policy+audit+approvals。
- Python 子进程 + limits（timeout/stdout/CPU/内存/进程数）。
- `execution_policy`（:27）：声明 `require_approval_for_{network,write,shell}`。

### 2.10 Orchestration（`orchestration/flow.hpp`）
- 类型化管道 `operator|`（:145）——C++ 管道语法组装 agent 流。
- `if constexpr` 对 `recover_step`/`retry_if_step`/普通步骤分别组装（:92/:106/:128）。
- 同步单进程，非分布式。

### 2.11 MCP 全栈（`mcp/`）
- 16 个头文件：server/client/host/gateway/stdio/HTTP/process/security/telemetry。

## 三、对 nop-ai-agent 的借鉴要点

### 3.1 编译期类型安全工具（高价值）
- Wuwe 用 `concept`+`is_aggregate` 约束"聚合体即契约"——普通 struct 即工具定义。
- **Java 映射**：用 **record + 注解处理器（APT）+ Jakarta Validation 风格约束** 在编译/构建期生成 JSON Schema 并校验字段。把 Wuwe 的 `tool_provider<Tools...>`（tool.hpp:637）映射为 nop 的强类型 Tool 注册器，**消除手写 schema**。
- 与 browser-use 的"函数签名自动生成 schema"（`2026-08-01-browser-use-agent-loop-analysis.md`）方向一致，但 Wuwe 是编译期（更强）。

### 3.2 "能力诚实"沙箱合约（高价值）
- `sandbox_enforcement_contract` 逐控制项声明 enforcement 级别（enforced/partial/not_enforced/planned）。
- **nop 借鉴**：guardrail 可引入"逐控制项 enforcement 级别"模型，而非二元 sandbox 标志——对 timeout/cancel/fs-read/fs-write/network 等分别声明 enforcement 级别，让 `ICircuitBreaker` 等可靠性组件的能力边界**可被程序化查询而非靠文档**。与 opensandbox 的 Deny-by-default（`2026-08-01-opensandbox-deny-default-analysis.md`）互补：opensandbox 是网络出口策略，wuwe 是全控制项合约。

### 3.3 四治理关注点分离（中高价值）
- capability（声明敏感动作）→ policy（决定允许，三态）→ approval（host 授权，scope=once/session/workspace）→ enforcement（后端执行）四层解耦。
- **nop 借鉴**：把 guardrail 拆成三段管线：capability 判定（工具元数据声明敏感级）→ 策略门（三态 allow/deny/require_approval）→ 审批服务（人工/自动，scope 区分）。`require_approval` + scope（once/session/workspace）直接映射 AGT 审批流（`2026-08-01-agent-governance-toolkit-analysis.md`）和 openscience glob ask（`2026-08-01-openscience-declarative-agent-analysis.md`），但 wuwe 的分层更系统化。

### 3.4 预算 typed reserve（中价值）
- 每种资源（model/tool/reflection/plan 调用）独立预算闸，超限产生 typed error。
- **nop 借鉴**：在 12 个 AgentLifecyclePoint 的每个点增加 typed budget guard（per-resource reserve，超限抛特定异常），使 middleware 洋葱链每层都能消费/贡献预算。与 helicone 的 Wallet 预算（`2026-08-01-helicone-gateway-observability-analysis.md`）互补：helicone 是网关级，wuwe 是引擎内 per-lifecycle-point 级。

### 3.5 structured audit_event 9 态 outcome（中价值）
- module/name/trace_id/subject_id/outcome(9 态)/attributes 固定结构。
- **nop 借鉴**：作为 nop 事件模型的参考 schema——trace_id 贯穿 reasoning→plan→execution→audit（对应 exo 的不可变 canonical event log `2026-08-01-exo-self-evolving-analysis.md`）。9 态 outcome（尤其 attempted/approved/timed_out）比 nop 当前状态更丰富。

### 3.6 plan_validator DAG 环检测 + reset_running_steps_on_resume（中价值）
- `has_dependency_cycle_from`（plan.hpp:314）：DAG 环检测——nop plan 引入 DAG 依赖（jcode `2026-08-01-jcode-dag-first-agent-analysis.md`）时需要。
- `reset_running_steps_on_resume`（:161）：恢复时把 running 重置为 pending——防止"半完成"状态。nop checkpoint append-only 恢复时可参考此语义。

### 3.7 file_plan_store 是反面教材（对照价值）
- Wuwe 用 `std::ios::trunc` 整文件重写（`plan_store.hpp:132`），崩溃安全性弱。
- **nop 的 append-only checkpoint 在这一点上明显更优**，应坚持。但可借鉴 Wuwe 的 `plan_step_status`（6 态，含 blocked）丰富 nop 的 AgentExecStatus。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **预算 typed reserve**（`reasoning_runner.hpp:698,713,728`）：model/tool/reflection 调用独立预算闸——**超限即 typed error 停止**，不静默重试。
- **plan_step_status 6 态**（`plan.hpp:22`）：pending/running/completed/failed/skipped/**blocked**——失败/阻塞显式建模。
- **reset_running_steps_on_resume**（`plan.hpp:161`）：恢复时 running→pending——**防"半完成"状态**（重试从干净态开始）。
- **reflection rubric 阈值 → action**（`reflection_core.hpp:19`）：pass/revise/retry/replan/block/escalate——**rubric 评分驱动重试/重规划决策**。
- **对 nop 的启示**：reset_running_steps_on_resume 是 nop checkpoint 恢复的"半完成状态"处理参考；rubric→action 映射是 nop CompletionJudge 的细化。

## 四、优缺点

### 优点
1. **编译期类型安全工具**——C++20 concept + gmp 反射，零运行时开销，在 agent 框架中极为罕见。
2. **"能力诚实"沙箱合约**——按合约而非后端名推断，拒绝过度承诺。
3. **四治理关注点严格分离**——框架不拥有身份/密钥/审批/留存，全交 host。
4. **预算即 typed reserve**——每资源独立闸，超限 typed error。
5. **22 种生命周期事件**——双通道（observer + callbacks）下发。
6. **模块全栈**——16 子模块覆盖 agent 全部能力。

### 缺点
1. Vector 检索是线性扫描（已承认），不适合大规模记忆。
2. Audit 仅 in_memory，无内置持久 sink。
3. plan_store 非 append-only（trunc 重写），崩溃恢复弱。
4. 仅 Python 执行后端，container/wasm 未实现（仅合约占位）。
5. header-only + 重模板元编程 → 编译时间长、错误信息难读。
6. orchestration 同步单进程，无多 agent team 协调/分布式调度。
7. 早期单人项目（v0.1.0，82 commits），生态/成熟度低；macOS 未认证。

## 五、结论

Wuwe 的核心价值不在代码可直接移植（C++20 模板 vs Java DSL-first 范式差异大），而在**概念设计**：
- **编译期类型安全工具**（聚合体即契约）→ 启发 nop 用 APT + record 实现强类型工具；
- **"能力诚实"沙箱合约**（逐控制项 enforcement 级别）→ 启发 nop guardrail 的能力边界可程序化查询；
- **四治理关注点分离**（capability/policy/approval/enforcement）→ 启发 nop guardrail 三段管线；
- **预算 typed reserve**→ 启发 nop per-lifecycle-point 预算闸。

在 checkpoint 持久化（append-only 更优）、向量检索、多 agent team 方面，nop 现有设计反而更成熟——这些维度 Wuwe 不构成借鉴。综合：**架构启发价值高（B+），实现迁移价值中等**。

## Open Questions
- [ ] nop 的 APT 注解处理器能否在构建期生成工具 JSON Schema（对标 Wuwe 的 gmp 编译期反射）？
- [ ] "能力诚实"沙箱合约在 nop 中如何声明（AgentModel XDEF 属性 vs 运行时注册）？
- [ ] 四治理关注点分离是否适用于 nop 单引擎场景（Wuwe 面向嵌入式 host 场景）？

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D1**（4 推理模式+22 事件+预算 typed reserve）、**D2**（能力诚实沙箱合约）、**D5**（plan_step 6 态+plan_validator 环检测）、**D6**（四治理分离）、**D11**（audit_event 9 态）、**D12**（reset_running_steps_on_resume）。最完整覆盖之一。缺失/薄弱：D9（plan_store trunc 反面教材）。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **checkpoint**：nop `DBCheckpointManager` append-only INSERT 明显优于 wuwe 的 file_plan_store（std::ios::trunc 整写）——nop 持久化更健壮。
- **工具类型安全**：nop XDEF schema 校验（编译期）与 wuwe 的 concept + gmp 反射等价，且 nop 无模板元编程的编译时长/错误可读性成本。
- **治理**：nop security 6 层（ContentOrigin/ApprovalGate/AutoApproveGate）+ guardrail-contract 比 wuwe 的四治理分离更完整、更贴合 Java。
- **预算**：nop quota/usage 包与 wuwe 的 typed reserve 等价，nop 更原生。

**必要参考的增量（以超越方式吸收）**：
- **"能力诚实"沙箱合约**（逐控制项 enforcement 级别：enforced/partial/not_enforced/planned）：nop guardrail 可增加能力边界声明——真正增量（能力边界可程序化查询而非靠文档）。
- **reset_running_steps_on_resume**（恢复时 running→pending 防半完成态）：nop checkpoint 恢复可增加——增强。

**总评**：nop-ai-agent **全面超越** wuwe（checkpoint 更健壮、XDEF 无编译成本、安全更完整）；能力诚实合约 + 半完成态处理两个增量吸收。

## References
- `~/ai/Wuwe/include/wuwe/agent/`（tools/tool.hpp:50,64,245,637,649、reasoning/reasoning_core.hpp:27,158、reasoning_runner.hpp:149,233,698,713,728、reflection/reflection_core.hpp:19,217、planning/plan.hpp:22,112,161,314、plan_store.hpp:132、capability/capability.hpp:17、capability_policy.hpp:12、approval/approval_service.hpp:16,28、audit/audit.hpp:22、audit_sink.hpp:18、sandbox/sandbox.hpp:37、execution/execution_runtime.hpp:19、execution_policy.hpp:27、orchestration/flow.hpp:92,106,128,145、memory/in_memory_vector_index.hpp:48-81）
- `~/ai/Wuwe/docs/`（security-governance.md:9,72）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-dsl.md`、`guardrail-contract.md`、`nop-ai-agent-security-and-permissions.md`、`nop-ai-agent-hook-skill-engine.md`
- `ai-dev/analysis/agent-survey/2026-08-01-browser-use-agent-loop-analysis.md`、`2026-08-01-opensandbox-deny-default-analysis.md`、`2026-08-01-agent-governance-toolkit-analysis.md`、`2026-08-01-openscience-declarative-agent-analysis.md`、`2026-08-01-helicone-gateway-observability-analysis.md`、`2026-08-01-exo-self-evolving-analysis.md`、`2026-08-01-jcode-dag-first-agent-analysis.md`
