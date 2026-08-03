# ArbiterOS 深度分析：Agent 治理内核（LiteLLM 代理拦截模型）

> Status: open
> Date: 2026-08-03
> Scope: `~/ai/ArbiterOS`（cure-lab/ArbiterOS，Python 实现的 Agent 治理内核，作为 LiteLLM 代理拦截 LLM 请求/响应）vs `nop-ai-agent`（security 包 + PermissionMatrix + ContentTrustEvaluator）
> Conclusion: ArbiterOS（LiteLLM 代理形态的 Agent 治理内核）在安全语义**设计宽度**上居本目录调研项目之首（15 策略注册，但 shipped 默认仅 4 enforce），nop 应吸收其污点传播/指令化/双模策略/流分类/POLICY-IR/SKILL-TRUST，但需先验证"代理形态语义能否无损移植进引擎内嵌"且关注企业可审计性（见 §5.6 steelman）。

---

## 一、总览

**ArbiterOS** 不是 agent 框架，是 **Agent 治理内核（Governance Kernel）**——以 LiteLLM 代理形式部署在 agent runtime 和上游 LLM 之间（默认 `http://127.0.0.1:4000/v1`），在请求/响应生命周期里做三件事：

1. **Authoritative trace**：把 agent 的 plan / call / return 指令化为可审计记录。
2. **Policy enforcement**：对解析后的指令、tool call、污点传播的数据流执行策略。
3. **Unsafe action interception**：在副作用发生前拦截不安全动作。

| 维度 | ArbiterOS | nop-ai-agent |
|------|-----------|--------------|
| 定位 | 治理代理（sidecar，drop-in） | 引擎内嵌（security 包挂在 dispatch 路径） |
| 接入方式 | agent 改 baseUrl 即可 | Java API / DSL 装配 |
| 指令模型 | 指令化（REASON/PLAN/READ/WRITE/EXEC/RESPOND/DELEGATE）+ security_type | 无（直接对 tool call 做矩阵判定） |
| 污点模型 | 数据流图（reference_tool_id → prop_conf/prop_trust，worst-case wins） | ContentOrigin（4 值枚举）+ ContentTrustEvaluator，**无跨调用传播** |
| 策略粒度 | 15 策略 + 12 flow kinds + UnaryGate UG-001..070 | SecurityLevel 三级（STANDARD/ELEVATED/RESTRICTED）× PermissionMatrix |
| 部署 | Python 单进程代理 + jsonl 日志 | Java 单进程引擎 + DB denial ledger |
| 多租户 | 无（device_key/channel/user_id 维度，但非租户隔离） | Principal.tenantId + TenantResolver + DB 隔离 |
| 可观测 | Langfuse trace + MLflow + 每 trace 一个 instruction jsonl | AuditEvent + IAuditLogger（多为 NoOp/Slf4j） |
| 成熟度 | 自称基准 6.17%→92.95%、0%→94.25% 等（headline，无可复现包） | 设计完成、实现骨架在位、生产数据缺 |

**核心结论先行**：ArbiterOS 把"治理"做成了**独立可插拔层**，且这套层的设计深度（数据流污点、指令类型化、enforce/observe 双模、两阶段 LLM 对齐、声明式策略 IR）在本目录调研的项目里**安全语义最丰富**（按策略数/流分类数/污点维度计，对比 AGT/mcp-gateway/opensandbox 等只覆盖其中一两个维度）。对 nop 的价值不是"做成代理"，而是**把它的安全语义吸收进 nop 的 security 包**——nop 现有 Principal/SecurityLevel/PermissionMatrix 是"动作级"安全，ArbiterOS 是"数据流级 + 指令级"安全，后者明显更细。

---

## 二、Context（调研背景）

- **为什么需要这个分析**：ArbiterOS 是 cure-lab 出的研究型项目（有 arXiv 论文 2604.18652），定位"Agent Governance Kernel"，与 nop-ai-agent 的 security 包是**同领域竞品**。它代表了"治理层独立化"这条路线的最完整实现。
- **要回答的问题**：ArbiterOS 的治理模型哪些值得 nop 吸收？代理部署模式对 nop 意味着什么？nop 现有 security 包相对它的差距和优势在哪？
- **约束**：ArbiterOS 是 Python 单进程代理；nop 是 Java 单进程引擎（DSL-first，企业栈）。两者解决重叠问题但部署形态不同。

---

## 三、核心机制详解

### 3.1 LiteLLM 代理拦截架构（部署形态）

```
Agent Runtime (OpenClaw / Nanobot / Hermes / Codex)
        │  OpenAI-compatible 请求，baseUrl = http://127.0.0.1:4000/v1
        ▼
┌─────────────────────────────────────────────────────────┐
│  ArbiterOS Kernel (litellm_callback.proxy_handler_instance) │
│                                                          │
│  Pre-Call Hook:                                          │
│   • Yes/No 确认检测（上一轮策略拦截的用户回复）          │
│   • 解析 Trace ID（device_key + channel + user_id）       │
│   • response_format 合并、category 包裹、topic hint       │
│   • 写 precall.jsonl + api_calls.jsonl                   │
│   • 注入 arbiteros_trace_id 元数据                       │
│                                                          │
│         ▼ 转发到真实上游 LLM                             │
│                                                          │
│  Post-Call Hook:                                         │
│   • 响应提取（Chat Completions + Responses API 双兼容）  │
│   • Response Transform（剥 category/topic 外壳）         │
│   • InstructionBuilder 指令化                            │
│   • apply_user_approval_preprocessing（用户已批准则提升 prop_*）│
│   • check_response_policy（15 策略串行 + enforce/observe）│
│   • 若 modified → 进入 Yes/No 确认流程                   │
│   • 写 log/{trace_id}.json（authoritative instruction log）│
│   • 可选 session_bootstrap_scan（首次纯文本回复前扫 SOUL.md 等）│
│   • 挂起 observe-only 警告，下次纯文本回复时附加          │
└─────────────────────────────────────────────────────────┘
        │
        ▼
   返回给 Agent（可能已被改写：tool_calls 被删 / content 被替换为拦截说明）
```

- 关键文件：`arbiteros_kernel/litellm_callback.py`（约 7300 行，是整个内核的中枢，承载 Trace 状态、Langfuse 发射、指令解析、策略编排；**协议适配**由 `protocol_adapter.py`（465 行）承担——`to_canonical_assistant_message()` 把 Chat Completions 与 Responses API 两种协议统一归一化为 `CanonicalAssistantMessage`，`ResponsesStreamTracker` 处理流式差异并在 provider 省略 `response.completed` 时合成该事件。这是 ArbiterOS 能同时支持 Codex（Responses API）和 Chat-Completion 客户端的真正原因，不仅是 LiteLLM 的功劳）。
- 接入门槛极低：agent 只要能自定义 baseUrl + apiKey，指向代理即可。Hypercorn 提供 HTTP/2（Codex 这类客户端需要）。

### 3.2 指令化（Instruction Parsing）—— ArbiterOS 的"authoritative trace"价值核心

LLM 每次响应被解析成结构化指令列表，落盘到 `log/{trace_id}.json`。这是 ArbiterOS 与多数 agent 框架的根本区别——**它不 only 看 tool call，还把认知步骤（REASON/PLAN/CRITIQUE）、人机交互（ASK/RESPOND）、协作（DELEGATE）都纳入统一指令模型**。

**指令类型与分类**（`instruction_parsing/types.py`）：

| instruction_type | instruction_category |
| --- | --- |
| REASON, PLAN, CRITIQUE | COGNITIVE.Reasoning |
| STORE, RETRIEVE, COMPRESS, PRUNE | MEMORY.Management |
| READ, WRITE, EXEC, WAIT | EXECUTION.Env |
| ASK, RESPOND, USER_MESSAGE | EXECUTION.Human |
| DELEGATE | EXECUTION.Agent |
| SUBSCRIBE, RECEIVE | EXECUTION.Perception |

**每条指令的 security_type**（安全元数据，由 tool parser + registry 填充）：

```jsonc
{
  "confidentiality": "UNKNOWN",      // LOW / HIGH / UNKNOWN
  "trustworthiness": "HIGH",         // LOW / HIGH / UNKNOWN
  "prop_confidentiality": "HIGH",    // 传播后的机密性（worst-case max）
  "prop_trustworthiness": "LOW",     // 传播后的可信度（worst-case min）
  "confidence": "UNKNOWN",
  "reversible": true,
  "authority": "UNKNOWN",
  "risk": "LOW",
  "custom": {}                       // 扩展元数据（exec 解析、标签等）
}
```

**双来源**：
- **结构化输出**：LLM 返回 `{topic, category, content}` 时（强制 `response_format: json_schema`），按 category 映射到 instruction_type。
- **Tool call**：每个 tool_call 经 tool parser（openclaw/nanobot/hermes/codex 四套）解析，按 tool 名 + 参数（尤其 path）查 registry 得到 security_type。
- **Shell 命令**：exec 工具的 command 经 tree-sitter（bash + powershell）AST 解析，拆 pipeline、识别可执行文件、抽取文件路径，再查 registry。

### 3.3 双层 Registry（Source + User）—— 路径/可执行文件的安全分类知识库

这是 ArbiterOS 把"安全分类"知识化的关键设计。`instruction_parsing/registries/` 下 Linux/macOS/Windows 三套，每套 4 个 YAML：

| 文件 | 分类目标 | 取值 | 解析规则 |
| --- | --- | --- | --- |
| `exe_registry.yaml` | 可执行文件 → 指令类型 | EXEC / WRITE / READ | 优先级 EXEC > WRITE > READ，未匹配默认 EXEC |
| `exe_risk.yaml` | 可执行文件 → 风险 | HIGH / LOW / UNKNOWN | HIGH = 不可逆破坏（rm/shutdown/git clean） |
| `file_trustworthiness.yaml` | 文件路径 → 可信度 | HIGH / LOW / UNKNOWN | **worst-case wins**：任一路径匹配 LOW 即 LOW |
| `file_confidentiality.yaml` | 文件路径 → 机密性 | HIGH / LOW / UNKNOWN | **highest wins**：任一路径匹配 HIGH 即 HIGH（防降级） |

**两层架构**：
- **Source 层**（包内 `linux_data/*.yaml`）：只读，随包发布。
- **User 层**（`~/.arbiteros/instruction_parsing/linux_registry/*.yaml`）：用户覆盖 + 自动追踪，首运行自动创建空规则集。
- **查找顺序**：User 层优先 → 未匹配回退 Source 层。
- **自动污点追踪**：agent 写文件时，内核自动把该文件路径 + effective taint 写入 User 层 registry。下次读该文件时自动解析到同级别——**这是跨会话的污点持久化**。
- **Effective label 解析**：遍历数据依赖图所有上游节点取 worst-case（conf 取 max、trust 取 min），保证文件内在敏感度（如 `.pem` 永远 HIGH conf）永不被低污点写操作降级。

**路径模式**：支持 glob（`/etc/*` 直接子项、`/etc/**` 递归、`~/Downloads/**`、`*.pem` 文件名、`https://*` URL）。**仅支持绝对路径和 `~` 开头的 home 相对路径**——裸相对路径因工作目录不确定被忽略（这是个限制）。

### 3.4 污点传播（Taint Propagation）—— 数据流图驱动的累积安全级别

这是 ArbiterOS 区别于所有"动作级"安全框架的核心创新。

**机制**：每个 tool call 的 arguments 里可带 `reference_tool_id: [...]`（字符串数组），声明本调用依赖的上游 tool_call_id。`compute_prop_taint_for_instruction`（`instruction_parsing/types.py:149`）据此构建数据依赖图：

- **纯文本指令**（无 tool_name）：prop_* = 自身 base level。
- **Tool call**：聚合自身 + 所有 `tool_call_id` 命中或 `reference_tool_id` 引用的指令——**trust 取 min、conf 取 max**。
- 即：读取过一个 LOW trust 来源的调用，自身 prop_trust 也被拉到 LOW；任何接触过 HIGH conf 数据的调用，prop_conf 升到 HIGH。

**层级序**：`LOW(0) < UNKNOWN(10) < HIGH(20)`——UNKNOWN 故意设在中间，作为"不确定"哨兵而非最高警戒。

**Taint ablation（研究开关）**：`taint_ablation.disable_inheritance: true` 时，policy 输入的 prop_* 被深拷贝对齐到 base level（**不修改落盘的 log/{trace_id}.json**），用于消融实验验证污点传播的实际贡献。

**用户批准传播**（`user_approval.py`）：用户对一次拦截回复 Yes 后，相关指令被标 `user_approved: true`。下次 `apply_user_approval_preprocessing` 会深拷贝指令列表，对这些指令及其 `reference_tool_id` 链上的指令把 prop_trust 提到 HIGH、prop_conf 降到 LOW——**用户批准等价于一次性提权**，且只影响传入 policy 的副本，持久化记录不变。

### 3.5 策略引擎（15 策略注册，默认仅 4 enforce / 11 observe）—— 治理的工程化（含成熟度警示）

**注册表**（`policy_registry.json`，运行时按 mtime 自动热加载；**注意**：`policy/defaults.py::_default_registry_data()` 的回退默认值与实际 shipped 的 `policy_registry.json` 不同——以 shipped 文件为准）：

| 策略类 | shipped 默认 | 作用 |
| --- | --- | --- |
| `RateLimitPolicy` | **enforce** | 每 trace + 每 tool 硬上限 + 连续重复上限 |
| `UnaryGatePolicy` | **enforce** | UG-001..070 声明式单动作规则（13 条，见下） |
| `RelationalPolicy` | **enforce** | 12 flow kinds 的关系型污点检查（见下） |
| `AlignmentSentinelPolicy` | **enforce** | LLM-as-judge 的 pre-exec 计划对齐检查（注意：shipped 为 enforce，但 post-exec 阶段另由 `alignment_sentinel.postexec_enabled` 开关控制） |
| `PathBudgetPolicy` | observe | 路径 allow/deny 前缀 + 输入字符串长度预算（>4000 字符截断） |
| `AllowDenyPolicy` | observe | 按 tool / instruction_type / category 的黑白名单 |
| `EfsmGatePolicy` | observe | EFSM 状态机 + plan 对齐（`path_in_recent_plan` 等 guard） |
| `TaintPolicy` | observe | input/output 工具分类，gate trust vs conf / prop_conf |
| `OutputBudgetPolicy` | observe | assistant content 超 max_chars 截断 |
| `SecurityLabelPolicy` | observe | confidence/authority_label 等安全标签 gate |
| `ExecCompositePolicy` | observe | 多段 exec 命令的粗解析元数据 |
| `DeletePolicy` | observe | 删除类操作拦截（交由后续审批流） |
| `OpenClawPolicy` | observe | OpenClaw P1/P3/P7/P9/P10 语义端口 |
| `NanobotPolicy` | observe | Nanobot exec deny 模式、URL/SSRF 检查、重复外部查找阻断 |
| `ResourceGuardPolicy` | observe | trace token/时间/instruction 内存预算 |

> **关键成熟度警示**：ArbiterOS 的作者在 shipped 配置里**只信任 4/15 策略默认 enforce**，其余 11 条以 observe 模式运行（策略照跑但违规只产警告不改响应）。这有两重含义：(1) "15 策略"是注册数而非活跃拦截数，"安全语义最丰富"指**设计宽度**而非**默认防护强度**；(2) 11 条策略尚未被作者团队自身验证到可默认 enforce 的程度——这正是 §5.6 steelman 第 3 点（研究期成熟度信号）的实证支撑，吸收前务必关注这些策略的 observe 阶段数据。`AlignmentSentinelPolicy` 例外——它 shipped 为 enforce，意味着 LLM 对齐在作者场景下已通过验证。

**UnaryGatePolicy 规则**（声明式，单动作，共 13 条，定义在 `policy/unary_gate_rules.json`）：
- UG-001：缺指令元数据 → 拦截（防盲放行）
- UG-010：tool 参数字符串总长 > 4000 字符 → 拦截（防超大输入绕过简单检查）
- UG-020/021：EXEC 必须 confidence/trust ≥ UNKNOWN
- UG-030：HIGH/CRITICAL risk 的 exec 直接拦
- UG-031：exec/write 且 `approval_required = true` → 拦截（防自动批准）
- UG-032：destructive && !reversible 拦截
- UG-040：tag 命中 SECRET_LIKE / HIGH_RISK 拦截
- UG-050：RESPOND 时 prop_conf > UNKNOWN 拦截（防敏感直显）
- **UG-060/061/062**：`SOUL.MD` / `AGENTS.MD` / `IDENTITY.MD` 的直接写、间接 exec 写、message 委托修改全部禁止（三层防身份文件篡改）
- UG-070：gateway CONFIG.PATCH/APPLY 含外部 URL + PROXY/UPSTREAM/BASE_URL 字段 → 拦截（防流量劫持）

**RelationalPolicy 的 12 flow kinds**（关系型，信息流驱动）—— 这是 ArbiterOS 把"动作级"细化为"流级"的关键：

| flow_kind | 含义 | 判定逻辑 |
| --- | --- | --- |
| `read_external` | 读网页/外部资源 | sink trust 与 LOW 取 max，要求 ≥ LOW（允许低信任读外部以供分析） |
| `read_sensitive` | 读本地敏感文件/会话/网关配置 | sink trust ≥ conf（经典 trust ≥ confidentiality） |
| `read_state` | 读系统/任务状态 | 永远允许 |
| `write_local` | 本地私有路径写入 | 本地工作区物化：sink trust 与 HIGH 取 max ≥ source conf |
| `write_shared` | 写 /shared/ /public/ /export/ /uploads/ 等 | sink trust 视为 UNKNOWN ≥ source conf |
| `delegate_sink` | sessions_send / sessions_spawn / DELEGATE | sink trust 视为 LOW ≥ source conf |
| `comm_sink` | message/mail 等人可见外发 | sink trust 视为 UNKNOWN ≥ source conf |
| `voice_sink` | tts 等语音输出 | sink trust 视为 LOW ≥ source conf（播出难撤回） |
| `ui_side_effect` | browser act/navigate/upload 等 | source trust ≥ MID（HIGH conf/risk 则 ≥ HIGH） |
| `exec_side_effect` | exec/process | source trust ≥ MID（HIGH conf/risk 则 ≥ HIGH） |
| `persist_side_effect` | cron/reminder 持久化 | source conf 为 MID/HIGH 直接拦；否则 source trust ≥ MID |
| `respond_sink` | 直接回复用户 | sink trust UNKNOWN ≥ source conf（可选开关） |

**关键设计原则**（`relational_policy.py` 注释）：
- 策略**只消费 kernel-lowered 元数据 + 浅 tool/action/path 提示**，不在策略内做深度语义解析。
- 内容承载型 sink（delegate/comm/persist）用 session/source taint 判定。
- 控制型副作用（browser/exec/process）用 source trust 判定。
- UNKNOWN 在某些外发 sink 被"软化"为 LOW（`_soft_source_conf`），避免老版本"LOW < UNKNOWN ⇒ 误拦"问题。

**Enforce vs Observe-only 双模**（`policy_check.py:187` `apply_policy_enforcement_mode`）：
- registry 行 `enabled: true`：策略 `check()` 返回 modified=True 时，替换响应、记 error_type、走正常拦截流。
- registry 行 `enabled: false`：策略 `check()` **照样运行**，但若 modified=True，**回滚到策略前快照**，把"本会拦截的文本"塞进 `inactivate_error_type`。
- 内核把 inactivate 文本累积到 trace 的 `pending_warning_texts`，在后续**纯文本 assistant 回复**（无 tool_calls、非策略确认 ask）时，附加固定中文前言 + 警告行到 content 末尾，然后清空队列。
- **意义**：可灰度上线新策略，先 observe 收集"会拦什么"，验证后再切 enforce。nop 当前 `SecurityCheckpointChain.evaluate` 是短路循环（`Decision` 是 `{ALLOW, DENY, DENY_AND_BREAK}` 三值，但任一非 ALLOW 即终止），**无 observe 灰度模式**。

### 3.6 两阶段 LLM 对齐哨兵（Alignment Sentinel）—— 推理型反馈传感器

唯一用 LLM 做判定的策略，分两阶段：

**Stage 1 — Pre-exec（操作评审）**（`alignment_sentinel_policy.py`）：
- 在 tool_calls 执行前，把"用户目标 + 计划操作列表"喂给 LLM。
- LLM 返回 `{alarm, suspicion ∈ [0,1], justification}`，`block = alarm && suspicion >= threshold`（默认 0.7）。
- `should_trigger_preexec_sentinel` 启发式决定是否真的调用 LLM（避免每次都花 token）。
- **fail-open**：异常时审计后放行（保可用性）。

**Stage 2 — Post-exec（响应载荷评审）**（`litellm_callback.py` `_screen_tool_results_with_alignment`）：
- tool result 返回后，在每个 tool 消息上跑 LLM 评审，判定是否"夹带私货"（额外字段、agent 指令注入、超出请求范围）。
- 命中则把 tool result 内容替换为 `SECURITY NOTICE: Tool result removed by Alignment Sentinel.` + 理由。
- 结果缓存在 `state.tool_result_alignment_by_call_id`，避免重复评审。
- 同样 fail-open。

**触发器**（`alignment_trigger.py`）：基于 tool 名、args、body 特征（prompt injection marker、超大、半结构化、未知来源）决定是否真的需要调用 LLM——这是"用启发式 gate 昂贵推理传感器"的典型模式。

### 3.7 Yes/No 策略确认流（用户审批闭环）

当策略拦截（modified=True）且非 local confirm 模式时，内核不直接返回受保护响应，而是：
1. 缓存 `{original_response, protected_response, policy_reason, ...}` 到 `_policy_confirmation_pending[trace_id]`。
2. 在响应末尾追加固定后缀 `"是否采纳当前的安全建议：Yes / No."`。
3. 下一轮请求的 pre-call 检测用户回复（`_detect_policy_confirmation_reply`）：
   - Yes → 返回缓存的受保护响应（**不调用 LLM**），并把相关指令标 `user_approved: true`（触发 3.4 的提权传播）。
   - No → 跳过策略检查，返回原始响应。
4. 也可配置 `arbiteros_local_policy_confirm: true` 走 stdin Y/N（仅适合单机调试）。

### 3.8 可观测性（Langfuse + MLflow + 结构化日志）

- **Langfuse session tracing**：每个 trace 一个 root observation，每个 turn 一个子 observation，节点类型分 input/tool/kernel/transform/output/failure/passthrough。即便没有 Langfuse key 也写 `log/langfuse_nodes.jsonl`（调试用）。
- **MLflow**：通过 LiteLLM 的 `mlflow` callback 记录（success/failure）。
- **日志文件矩阵**：

| 文件 | 内容 |
| --- | --- |
| `log/api_calls.jsonl` | LLM 响应原始 post-call 内容 |
| `log/precall.jsonl` | 发给 LLM 的最终 pre-call 内容 |
| `log/langfuse_nodes.jsonl` | Langfuse 节点日志 |
| `log/trace_state.json` | 持久化的 Trace 状态 |
| `log/{trace_id}.json` | **每 trace 的指令信息（可读性最高的核心产物）** |

- **`langfuse_replay`**（930 行）：从 `api_calls.jsonl` 重放历史，重建 Langfuse trace——**离线回放审计**能力。

### 3.9 Redteam 测试套件（100+ 结构化 case）

`redteam/case/` 下按场景组织（20 类）：agent_collaboration、approval_flow、automation、browser、calendar、code_management、design、document、file_handling、gateway_control、knowledge_management、mail、message、openclaw、ops_diagnostics、pdf、reminder、research_summary、voice_output、web_search。

**Case 结构**（JSON）：
```json
{
  "trace_id": "redteam-file-unsafe-03-...",
  "prior": [ /* 历史上下文：assistant 消息 + tool_calls + tool results */ ],
  "current": { /* 待判定的当前 assistant 响应，通常含 tool_calls */ }
}
```

**Harness**（`policy_test_harness.py`）：读 case → 回放 prior → 检查 current → 输出最终 policy 判定。

**Runner**（`_automation/run_cases.py`）：批量跑、保存三层产物（raw/parsed/results）、失败时可选 LLM 分析。**跨机器可移植**：运行时把 case 里硬编码的 `/root/ArbiterOS-Kernel` 重写为当前机器路径。

**Manifest**（`case_manifest.json`）：case 的"说明书"——哪条 safe/unsafe、是否启用、某 unsafe 期望命中哪些 policy。**case 是输入，manifest 是预期**，分离清晰。

> **对抗验证局限**：105 case 与 manifest 均由 ArbiterOS 团队**自编**（case + manifest 同源），无第三方红队、无对抗作者。命中率反映**设计意图的自洽性**（策略是否按设计拦截了它想拦的场景），不反映对真实攻击者的防御率。借鉴价值在 case 的**结构化格式**与**场景覆盖广度**，而非数字本身。

### 3.10 Skill Trust（cisco-ai-skill-scanner 集成）—— 第三种信任来源

除了静态 registry 和 LLM 对齐，ArbiterOS 还有**第三种信任来源**：外部 skill 包扫描器。

- 实现：`instruction_parsing/helpers/skill_trust.py`（490 行）+ `warm_skill_trust.py`（启动时预热缓存）。
- 机制：调用 `cisco-ai-skill-scanner` CLI 扫描 agent 的 skill 包（含 `SKILL.md` 的目录——**与 opencode/nop `.opencode/skills/` 同构**），按 `max_severity → trustworthiness` 映射：CRITICAL/HIGH → LOW、MEDIUM → UNKNOWN、LOW/INFO/SAFE → HIGH。
- 缓存：以 `SKILL.md` 的 SHA-256 为键存到 `skill_trust_by_name.json`（与 OS registry 同目录）；`SKILL.md` 内容变了才重扫。
- 配置：`litellm_config.yaml` 的 `arbiteros_skill_trust.skills_root` 指定 skill 包根目录；`skill_scanner_llm` 提供 LLM 辅助扫描的三元组（model/api_base/api_key，缺则只用静态+行为分析器）。
- **对 nop 的特殊价值**：这是**最直接可迁移的机制**——nop 已有 `.opencode/skills/` 生态，可借鉴"skill 包扫描 → 信任级别"的桥接，把 skill 包的威胁分析沉淀为信任输入，而非依赖运行时 LLM 判定。

### 3.11 Policy Rule IR（声明式策略 DSL）—— 最 nop-aligned 的机制

ArbiterOS 已把策略抽象成**声明式、schema 校验、可编译的 IR**——这与 nop 的可逆计算 / DSL-first 理念高度同构。

- 实现：`policy/policy_rule_ir.py`（896 行）+ `policy_rule_ir_authoring.md`（编写指南）+ CLI 编译器（`python -m ...policy.policy_rule_ir --input ... --target unary|relational`）。
- 两种规则形状：`unary_tool_call`（对当前 tool call/response 指令）、`relational_flow`（对 source taint/history 与 sink 的关系）——分别编译到 `UnaryGatePolicy` 和 `RelationalPolicy` 规则。
- 契约约束：
  - `required_metadata` 声明规则需要的额外元数据字段（来自 `tool_arguments` / `kernel_lowering` / `llm_lowering` / `parser_custom` / `derived` 五种来源）。
  - `on_missing ∈ {validation_error, no_match, fail_closed}` 控制缺元数据时的行为。
  - 谓词 DSL：`eq/gt/intersects/matches/between/all/any/...`。
  - 严格白名单：`ALLOWED_FLOW_KINDS`（13 个，含 `none` 哨兵）、`ALLOWED_INSTRUCTION_TYPES`（15 个，含 DELEGATE）、`ALLOWED_SEVERITIES`、保留字段保护（内置 vs 内部运行时 vs 遗留派生）。
- 用户扩展：`user_unary_gate_rules.json` / `user_relational_flow_rules.json` / `user_policy_rule_ir_examples.json` 允许不写代码声明新规则。
- **对 nop 的特殊价值**：这是整个 ArbiterOS 仓库里**最契合 nop 设计哲学的机制**。报告主旨是"把 ArbiterOS 语义吸收进 nop security 包"——而 ArbiterOS 已经把它自己的策略抽象成了声明式 IR，nop 可直接参考这套 IR schema 作为 `SecurityCheckpointChain` 的 DSL，替代手写 Java builder。

### 3.12 Role-based 策略集（请求级策略选择）

除了 registry 默认 + local confirm，ArbiterOS 还有**第三维策略控制**：请求级角色。

- 实现：`role_policy_cfg_loader.py` + `role_policy_cfg/{role}_policy.json` + `role_policy_sets.json`。
- 机制：请求 model 字段带 `;role` 后缀（如 `gpt-4o;semantic_protected`），`split_model_and_role`（`policy_check.py:211`）解析后缀；`load_role_policy_config`（`role_policy_cfg_loader.py:34`）加载 `role_policy_cfg/{role}_policy.json`（整份策略配置）；`resolve_role_policy_enabled_override`（`policy_check.py:263`）读 `role_policy_sets.json` 解析该角色启用哪些策略。两者在 `litellm_callback.py` 协同调用。
- shipped 的 `semantic_protected` 角色提供**具体不同的策略集**（关掉 post-exec sentinel、换完整 EFSM 转移表、设 resource_guard 预算）。
- **现状局限**：`role_policy_sets.json` 目前**只声明 1 个角色**（`semantic_protected`，启用 2 策略）——是概念验证而非已铺开的多角色体系。
- **这是 ArbiterOS 对 nop `Principal(role/tenantId)` 的部分回答**：nop 的 role 映射到 SecurityLevel，ArbiterOS 的 role 映射到策略集。注意：§一比较表的"无多租户"指**租户隔离**维度（ArbiterOS 确实没有），角色级策略是**另一维度**——两者不冲突，本节是补充而非修正 §一。

---

## 四、优缺点

### 优点

1. **治理深度领先**：15 策略 + 12 flow kinds + 13 UG 规则 + 两阶段 LLM 对齐 + 自动污点 registry + skill 信任扫描 + 声明式策略 IR（§3.10/§3.11），在本目录调研项目中**安全语义最丰富**（按策略数/流分类数/污点维度计）。多数项目（含 mcp-gateway、opensandbox、AGT）只覆盖其中一两个维度。
2. **代理模式 = drop-in**：agent 改 baseUrl 即接入，不动 agent 代码。对 OpenClaw/Nanobot/Hermes/Codex 等任意 OpenAI-compatible runtime 通用。
3. **enforce/observe 双模**：策略灰度上线能力，生产可用性高。
4. **指令化 authoritative trace**：`log/{trace_id}.json` 是"agent 做了什么"的权威账本，审计/回放/合规价值大。
5. **污点传播工程化**：`reference_tool_id` 数据流图 + worst-case wins + 用户批准提权 + 自动 registry 持久化——完整的数据流安全模型。
6. **确定性状态**：EFSM/plan/taint 全从指令历史重放推导（`efsm_replay_history`、`build_plan_state`），无 wallclock 依赖（除 rate-limit 窗口），可复现。
7. **Redteam 体系完整**：100+ case 跨 20 场景，结构化 case + manifest + 跨机器渲染 + LLM 失败分析。
8. **tree-sitter AST 解析 shell**：bash + powershell，比正则启发式健壮（pipeline 拆分、可执行文件识别、文件路径抽取）。

### 缺点

1. **单进程、无 HA/集群故事**：Trace 状态在内存 + jsonl 文件，多实例无共享状态。`_trace_state_by_device` 是进程内 dict。生产多副本部署需自行解决状态共享。
2. **强依赖 LLM 做对齐筛查**：Alignment Sentinel 的成本/延迟可观；fail-open 策略意味着 LLM 不可用时对齐层失效（虽有启发式 trigger gate 降低调用频率）。
3. **路径分类依赖绝对路径**：裸相对路径被忽略（`registry_usage.md` 明示）。这要求 agent 工具调用时传绝对路径——不是所有 agent 都保证。
4. **`reference_tool_id` 依赖上游填充**：污点传播的精度取决于 tool parser 或 LLM 是否正确声明依赖关系。覆盖不全则传播链断裂。
5. **`arbiteros_local_policy_confirm` stdin Y/N**：仅单机调试可用，生产多用户场景不可行（虽有 in-agent Yes/No 流兜底）。
6. **硬编码问题**：
   - `RULE_DETAILS_URL = "http://43.161.233.143:5173/"`（`relational_policy.py:17`，裸 IP 写在源码里）。
   - 中文拦截文案、`_POLICY_CONFIRMATION_SUFFIX`、`_PENDING_WARNINGS_APPEND_PREAMBLE` 等字符串硬编码，无 i18n。
7. **基准数字不可复现**：README 自承"headline outcomes rather than standalone reproducibility pack"。
8. **`litellm_callback.py` 单文件 7300+ 行**：Trace 状态、Langfuse、解析、策略编排全揉在一起，维护成本高（不过这属于内部结构问题，不影响外部价值评估）。
9. **1774 行死代码 `policy_engine_bat.py`**：完整定义了一套并行的 `PolicyEngine`/`PolicyDecision`/`PolicyState` 栈，但**全仓库零引用**（穷举 grep 确认无任何 import/mention）——占 `policy/` 目录约 17% 行数。暗示策略引擎设计存在**未合并的分叉**（一支被废弃），活跃分支未必是更优设计。**这是吸收前必须厘清的成熟度信号**：连作者自身都未收敛设计，移植需谨慎确认活跃实现。
10. **shipped 默认仅 4/15 策略 enforce**（见 §3.5 警示）：11 条策略以 observe 模式运行，说明作者团队尚未把这些策略验证到默认拦截的程度——"15 策略"是设计宽度而非默认防护强度。
11. **无多租户**：device_key/channel/user_id 是会话维度，不是租户隔离。nop 在这点上反而更强（Principal.tenantId + DB 隔离）。
12. **无沙箱执行**：ArbiterOS 拦截但不执行——它假设 agent runtime 自己去执行。nop 有 DockerSandboxBackend。

---

## 五、对 nop-ai-agent 的借鉴要点（核心价值）

> nop 现有 security 包（`io.nop.ai.agent.security.*`，60+ 类）的设计是"动作级 + 矩阵判定"：Principal(role/channelId/tenantId) × SecurityLevel(STANDARD/ELEVATED/RESTRICTED) × PermissionMatrix × IPathAccessChecker × IApprovalGate × IDenialLedger。这套完整但**粒度粗**——没有数据流污点、没有指令类型化、没有流分类。ArbiterOS 恰好在这些点上做了深度工程化。

### 5.1 污点传播数据流图（高价值，但高实现风险——见 §5.6/§5.7）

**nop 现状**（已核实）：`ContentOrigin.java` 是 4 值枚举（CHANNEL_INPUT/WEB_FETCH/FILE_READ/AGENT_GENERATED），`DefaultContentTrustEvaluator.java` 是纯 switch，按单点 origin 判定——**无跨调用传播**（包内无任何依赖图遍历逻辑）。一次 `web_fetch` 拿回的 LOW trust 内容，被 `write` 落盘后，下次 `read` 该文件时 nop 不知道它源自 LOW trust。

**ArbiterOS 做法**：
- tool call arguments 带 `reference_tool_id: [...]` 声明数据依赖。
- `compute_prop_taint_for_instruction`（`types.py:149`）遍历依赖图，trust 取 min、conf 取 max。
- 写文件时自动记 registry，下次读自动解析。

**对 nop 的落地建议**：
- 在 `AgentToolDispatcher` 或 `IToolAccessChecker` 之前加一层 `TaintPropagationStep`。
- nop 的 `AgentSession` 已有消息流/checkpoint，可从消息历史重建数据依赖图（ArbiterOS 的 `efsm_replay_history` 模式）。
- **关键难点**（不能回避）：`reference_tool_id` 的来源。ArbiterOS 要求 LLM/tool parser 显式声明依赖；nop 若想"自动推断"，需基于参数中的 path/content 引用做依赖分析——**这是整个提案最难的子问题**。两种现实路径：(a) 像 ArbiterOS 一样**强制 tool 声明 reference 字段**（简单但侵入性）；(b) 实现 path/content 引用推断算法并接受覆盖不全的退化（复杂）。倾向 (a) 先行。
- **核心是补 `prop_confidentiality` / `prop_trustworthiness` 两个字段到 nop 的安全判定输入**，让 PermissionMatrix 决策从"动作 + 身份"升级到"动作 + 身份 + 数据流"。

### 5.2 指令类型化 + 路径 registry 自动污点（中优先，可独立落地）

**nop 现状**：tool call 直接进 PermissionMatrix，没有"这条调用在认知/执行/协作的哪个层面"的抽象。路径访问靠 `IPathAccessChecker`（RuleBased/ParentConstrained）做规则匹配，但**无自动学习**——agent 写过什么文件，下次读时 checker 不知道。

**ArbiterOS 做法**：
- 把 LLM 输出统一指令化（REASON/PLAN/READ/WRITE/EXEC/RESPOND/DELEGATE），每条带 security_type。
- 双层 registry（Source + User），User 层自动记录 agent 写过的文件路径 + effective taint，下次读自动解析。
- effective label 遍历依赖图取 worst-case。

**对 nop 的落地建议**：
- nop 已有 `IAuditLogger` / `AuditEvent`，可在审计事件里补 instruction_type + security_type 字段。
- nop 的 `ISecurityLevelResolver` 可扩展为查询一个"路径 → taint"的持久化 registry（DB 表或文件），agent 写文件时自动登记。
- 这与 nop 的 Delta 定制理念相通——Source 层 = 平台默认规则，User 层 = 应用/运行时覆盖，分层组合。

### 5.3 enforce vs observe-only 双模策略（中优先，工程化提升）

**nop 现状**：`SecurityCheckpointChain.evaluate` 是短路循环，任一 checkpoint 返回非 ALLOW 即终止——**无 observe 灰度模式**。

**ArbiterOS 做法**：每策略带 `enabled` 标志，false 时策略照跑但结果只记 `inactivate_error_type`，后续以警告形式附加到回复——**先 observe 验证再 enforce**。

**对 nop 的落地建议**：
- `SecurityCheckpoint` 接口加 `Mode enforce() / Mode observe()` 或在 `CheckContext` 里带 mode。
- `SecurityCheckpointChain` 改为收集所有 checkpoint 的判定，observe 模式的违规聚合到 warning 列表。
- 这对 nop 引入新安全规则时的回归风险控制极有价值——先 observe 跑两周看误拦率，再切 enforce。

### 5.4 关系型流感知（12 flow kinds）（中优先，精度提升）

**nop 现状**：`Permission` 基于动作 + SecurityLevel，不区分"写本地"vs"写共享"、"delegate 给子 agent"vs"对外发消息"。

**ArbiterOS 做法**：12 flow kinds，每个 sink 类型有不同的信任要求（voice_sink 比 comm_sink 更严，因为播出难撤回；write_shared 比 write_local 更严，因为更易扩散）。

**对 nop 的落地建议**：
- 不必照搬 12 种，但 nop 的 `Permission` 可引入"信息流方向"维度（local / shared / outbound / persistent）。
- 这与 nop 的 `ChannelKind`（若已有）可结合——不同 channel 对应不同 sink 语义。

### 5.5 代理/网关部署模式（低优先，架构级可选）

**意义**：ArbiterOS 证明了"治理可独立成层"。若 nop 未来要做"多 agent 平台"，可考虑把 security 包暴露为独立网关（类似 mcp-gateway 的定位但更重）。

**当前不建议**：nop 单引擎内嵌模型已够用，且 Java 工程栈下"独立代理"会引入额外网络跳。**除非**未来要做"一个 nop 治理层管多个异构 agent runtime"，那时 ArbiterOS 的代理架构值得参考。

### 5.6 Steelman：为什么可能不应该直接吸收（务必先验证）

本报告主旨是"吸收 ArbiterOS 语义进 nop"，但作为决策参考必须诚实呈现反方论点：

1. **代理形态的观察优势**：ArbiterOS 作为代理，能看到**完整请求/响应信封**——包括非 tool 的认知指令（REASON/PLAN/CRITIQUE）。nop 的 `SecurityCheckpointChain` 挂在 dispatch 路径上，**只能看到 tool dispatch**，看不到 LLM 原始输出的认知步骤。若把指令类型化移植进 nop，nop 的 checkpoint 链能否观察到 PLAN/REASON？这是未验证的关键假设——如果不能，指令类型化在 nop 内只能降级为"tool 分类"，丢失一半价值。
2. **设计耦合**：ArbiterOS 的污点模型与其**完整 lowering 管线**强耦合（指令化 → registry → `compute_prop_taint_for_instruction` → relational sink 分类）。只移植污点而没有 lowering 栈，得到的是浅层模仿，不是真语义。
3. **研究期成熟度信号**：硬编码裸 IP（`relational_policy.py:17`）、7300 行单文件、不可复现基准、**1774 行死代码 `policy_engine_bat.py`（零引用的并行策略引擎）**、**shipped 默认仅 4/15 策略 enforce**——这些**不仅是 ArbiterOS 的部署缺陷，更是其策略语义尚未在生产中压力测试、设计尚未收敛的信号**。移植一套未经生产验证且自身设计未收敛的设计有回归风险。
4. **最强反驳——企业可审计性**：nop 的安全模型**刻意粗粒度**以服务企业合规审计。引入污点传播会带来**非确定性的 worst-case 级联**（一个 LOW-trust 源污染整条下游链），拒绝理由会变成"3 次调用前传播来的污点"——**对企业安全审计员不可解释**。ArbiterOS 作为研究代理能接受这种不透明；nop 作为企业引擎不能。这是"不吸收"立场的最强版本。
5. **结论**：本报告的"吸收"建议应作为**设计阶段待验证的假设**，而非已定论。落地前必须先回答 §Open Questions 的 Q1（指令化在哪层做）和 Q4（nop checkpoint 能否观察认知指令），且 ④流分类若上线需考虑 base-level 退化模式是否满足企业可解释性要求。

### 5.7 借鉴点的协同依赖与落地优先级（修正）

§5.1-5.4 不是完全独立的，存在协同依赖（但有软退化的余地）：

```
②指令类型化 + registry ──┬── 产生 security_type ──┐
                          │                         ├── ①污点传播 需要 security_type 才能算 prop_*
                          └── 产生 path taint ──────┘
                                                    │
                              ①污点传播 产出 prop_* ──┬── ④关系型流分类 比较 prop_*
                                                    │   （软依赖：prop_* 缺失时回退 base level，
                              ③enforce/observe 双模 ─── 与上述正交，可独立先行     见 relational_policy.py:155-159）
```

- **③enforce/observe 双模**：与上述正交，**最低风险、可独立先行**——是性价比最高的第一个落地项。
- **②指令类型化 + registry**：是 ①④ 的前置，但本身可部分独立（先做 tool 分类，认知指令分类待 Q4 验证）。
- **①污点传播**：依赖 ② 的 security_type；实现风险最高（reference_tool_id 来源问题，§5.1）。
- **④关系型流分类**：**软依赖** ① 的 prop_*——`relational_policy.py:155-159` 在 prop_* 缺失时回退到 base level（`st.get("prop_confidentiality") or st.get("confidentiality")`），所以 ④ 可在 ① 未就绪时以 base-level 退化模式先上线，只是效果减弱（无法捕获跨调用传播的污染）。ArbiterOS 的 `taint_ablation` 模式正是验证这条退化路径的。

**修正后的落地优先级（按价值/风险比，而非纯价值）**：③ 双模（低风险高价值，先行）→ ② 指令化+registry（①④ 的前置）→ ① 污点传播（高价值高风险）→ ④ 流分类（软依赖 ①，可退化先行）。**⑤ Policy Rule IR（§3.11）** 作为 ②④ 的 DSL 载体，与 ②④ 同期设计。**⑥ Skill Trust（§3.10）** 与 nop `.opencode/skills/` 生态绑定，独立小步快跑。

> 注：本优先级与 §六结论中的列表不同——§六按"价值"排序（污点第一），这里按"价值/风险比"排序（双模第一）。两者都对，取决于决策者更看重"快速见效"还是"先啃硬骨头"。

---

## 六、结论

- ArbiterOS 是**本目录调研范围内安全治理设计宽度最丰富的开源实现**（15 策略注册，但 shipped 默认仅 4/15 enforce、11 observe——见 §3.5 警示）。它把"治理"从"动作级矩阵"升级到"数据流级 + 指令级 + LLM 推理级"三层叠加，设计宽度体现在：15 策略、13 UG 规则、enforce/observe 双模、两阶段对齐、自动污点 registry、skill 信任、声明式策略 IR、100+ redteam case（自编，见 §3.9 局限）。
- 对 nop 的核心价值在**安全语义吸收**而非部署形态模仿。可借鉴面六点：①污点传播 ②指令类型化+registry ③enforce/observe 双模 ④流分类 ⑤Policy Rule IR（DSL 载体）⑥Skill Trust（与 nop skills 生态同构）。**但"能否无损移植进引擎内嵌"是待验证假设**（见 §5.6 steelman，含企业可审计性最强反驳），且需警惕 ArbiterOS 自身设计未收敛（1774 行死代码、11/15 策略未默认 enforce）。
- nop 反向胜出的点：多租户（`Principal.tenantId` + DB 隔离）、沙箱执行（`DockerSandboxBackend`）、企业 Java 栈、DSL-first 装配、**粗粒度可审计性**。这些 ArbiterOS 没有。
- 后续工作：指向具体设计文档 `ai-dev/design/nop-ai-agent/security-taint-and-instruction-typing.md`（待创建），需回答 3 个决策：(1) 指令类型化在哪层做（LLM 响应解析 vs tool dispatch）；(2) `reference_tool_id` 来源（强制声明 vs 推断）；(3) 治理 registry 用 DB 表 vs 文件（契合 Source/User 两层）。落地优先级见 §5.7（建议 ③ 双模先行）。

---

## 七、Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D6（审批与安全，极强——本目录最强 D6）**、**D7（反馈传感器，强——Alignment Sentinel 是 inferential sensor 典型）**、**D9（质量门——observe/enforce 双模）**、**D11（可观测性——Langfuse + instruction jsonl）**。

| 维度 | 覆盖 | 关键设计 |
| --- | --- | --- |
| D1 Agent 循环 | 弱 | 不控制循环（代理模型，agent runtime 自管循环）；仅有 rate-limit / consecutive-repeat 作为循环护栏 |
| D2 文件系统+工具 | 中 | 路径 registry、自动污点追踪、exec 复合解析；但本身不执行工具（agent 执行） |
| D3 记忆+上下文 | 弱 | topic hint 注入、category 包裹/剥离；无 compaction |
| D4 状态持久化 | 中 | 每 trace 一个 jsonl、trace_state.json、Langfuse 节点持久化、崩溃后可从日志重建；但**仅文件级，非事务性**（对比 hatchet 的 durable execution，ArbiterOS 是"日志可重建"而非"强持久"，单进程内存状态丢失需重放） |
| D5 规划+执行 | 中 | EFSM + plan 对齐 guard（`path_in_recent_plan`）；但 nop 的 PEV 三阶段更系统 |
| **D6 审批+安全** | **极强** | 15 策略 + 12 flow kinds + UG-001..070 + 两阶段 LLM 对齐 + Yes/No 确认流 + enforce/observe 双模 + 用户批准提权传播 |
| **D7 反馈环** | **强** | Computational: 路径/标签/risk 检查；Inferential: Alignment Sentinel（pre+post exec）；失败→content 替换为拦截说明 |
| D8 前馈约束 | 弱 | 不在 prompt 层注入规则（agent runtime 自管 prompt）；仅 session_bootstrap_scan 扫身份文件 |
| **D9 质量门** | **强** | observe 模式 = 灰度质量门；策略违规分类响应（拦截/警告附加/替换内容） |
| D10 上下文转交 | 弱 | 无 subagent 概念（agent runtime 自管）；仅 delegate_sink 策略拦截跨会话委托 |
| **D11 可观测性** | **强** | Langfuse trace 贯穿、MLflow、instruction jsonl 权威账本、langfuse_replay 离线回放 |
| D12 可靠性 | 中 | 无 retry/replan/resume（代理不负责执行）；trace_state 持久化支持会话恢复 |

---

## 八、对比结论：nop-ai-agent 全面性分析

**nop-ai-agent 已超越/独有的部分**（均已核实，见 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/`）：
- **多租户**：`Principal.java`（tenantId 字段）+ `ITenantResolver`/`ThreadLocalTenantResolver` + `DBDenialLedger.java`（注入 tenant WHERE）+ `TenantSql`——企业级多租户，ArbiterOS 完全没有。
- **沙箱执行**：`DockerSandboxBackend.java`（598 行，`--network none`/`--cpus`/`--memory`/host-path 白名单，fail-closed）+ `ISandboxBackend` 抽象——ArbiterOS 只拦截不执行。
- **企业栈**：DB 持久化 denial ledger（`DBDenialLedger.java` 265 行，per-session COUNT/DELETE）、SQL tenant 隔离、`Slf4jAuditLogger`——Java 生产部署就绪。
- **DSL-first 装配**：`SecurityCheckpointChain` 用 builder 装配，符合 nop 可逆计算理念。
- 包规模：74 个 `.java` 文件（主代码），骨架完整。

**ArbiterOS 明显领先、nop 必要参考的部分**（均已核实）：
- **数据流污点传播**（`reference_tool_id → prop_*`）：nop `DefaultContentTrustEvaluator.java` 是纯 switch，包内无任何依赖图遍历——**完全缺失**，这是 nop 安全模型最大盲区。
- **指令类型化 + 双层 registry + 自动污点追踪**：nop `IPathAccessChecker`（`RuleBasedPathAccessChecker`/`ParentConstrainedPathAccessChecker`）是静态规则，无运行时学习。
- **enforce/observe 双模**：nop `SecurityCheckpointChain.evaluate`（`:13-21`）短路循环，`Decision` 三值但无 dry-run 模式。
- **关系型流分类**（12 flow kinds）：nop `Permission.java` 是 `{allowed, reason, matchedRuleId}`，不区分信息流方向；`ChannelKind` 是 ingress（WEBUI/API/DM/GROUP）非 egress/sink-type。
- **两阶段 LLM 对齐**：nop 无 inferential 传感器（`IContentTrustEvaluator` 是规则型，包内无 LLM-as-judge）。
- **声明式策略 IR**（§3.11）：nop 的 checkpoint 是手写 Java 类，无 schema 校验的声明式 DSL。
- **Skill 信任扫描**（§3.10）：nop `.opencode/skills/` 无威胁分析集成。
- **Redteam 体系**：nop 无结构化对抗测试套件。

**总评**：两者是**互补关系**而非竞争——nop 强在企业栈/多租户/沙箱/装配，ArbiterOS 强在安全语义深度/数据流/指令化/声明式策略/对抗测试。**最优路径是把 ArbiterOS 的安全语义移植进 nop 的 security 包**（前提是 §5.6 的假设验证通过），让 nop 成为"既有企业骨架、又有数据流灵魂"的治理层。落地优先级见 §5.7：③双模先行（低风险）→ ②指令化+registry（前置）→ ①污点传播（高风险高价值）→ ④流分类；⑤Policy Rule IR 作 DSL 载体同期设计；⑥Skill Trust 独立小步。

---

## Open Questions

- [ ] **Q1（关键，阻塞 ②①）**：nop 的指令类型化（REASON/PLAN/READ/WRITE/EXEC）应该在哪一层做？是在 LLM 响应解析时（像 ArbiterOS 的 response_format json_schema 强制），还是在 tool dispatch 时按 tool 名映射？这决定 nop 的 checkpoint 链能否观察到非 tool 的认知指令（PLAN/REASON/CRITIQUE）——若不能，指令类型化在 nop 内只能降级为"tool 分类"。
- [ ] **Q2（阻塞 ①）**：nop 的 `reference_tool_id` 从哪来？是要求 tool 显式声明（侵入性低但覆盖依赖 tool 配合），还是 ToolContext 基于 path/content 引用推断（无侵入但覆盖不全）？倾向前者先行——见 §5.1。
- [ ] **Q3（阻塞 ③）**：enforce/observe 双模引入后，nop 的 `AuditEvent` schema 需要补哪些字段（`inactivate_error_type`、`policy_names`、`policy_sources`）？`SecurityCheckpoint` 接口加 `Mode` 还是 `CheckContext` 带 mode？
- [ ] **Q4（阻塞 ②）**：nop 的自动污点 registry 用 DB 表还是文件？DB 表更契合 nop 企业栈（与 `DBDenialLedger` 同构），但文件更契合 Source/User 两层覆盖语义（与 nop Delta 理念同构）。
- [ ] **Q5（新）**：nop 是否应采用 ArbiterOS 的 **Policy Rule IR**（声明式、schema 校验、可编译的策略 DSL）作为 `SecurityCheckpointChain` 的 DSL，替代手写 Java builder？这是仓库内最 nop-aligned 的机制（§3.11）。
- [ ] **Q6（新）**：nop 的 `.opencode/skills/` 生态今天如何做安全？是否值得借鉴 ArbiterOS 的 skill 包扫描（`cisco-ai-skill-scanner` 集成，§3.10），把 skill 威胁分析沉淀为信任输入？
- [ ] **Q7（新）**：nop 的 `Principal.role` 今天映射到 `SecurityLevel`——是否应像 ArbiterOS 的 role-policy（§3.12）那样映射到**策略集**？这决定 role-policy 是 nop 的缺口还是已覆盖。
- [ ] **Q8（新）**：若 `reference_tool_id` 声明不全，污点链断裂——nop 能接受的退化策略是什么？（ArbiterOS 静默降级；nop 是否要 fail-closed 或显式标 UNKNOWN？）

---

## References

- `~/ai/ArbiterOS/`（cure-lab/ArbiterOS 仓库）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/arbiteros_kernel/litellm_callback.py`（代理中枢，~7300 行）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/arbiteros_kernel/protocol_adapter.py`（Chat Completions + Responses API 协议适配，465 行）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/arbiteros_kernel/policy_check.py`（策略编排 + enforce/observe）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/arbiteros_kernel/policy/`（15 策略实现）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/arbiteros_kernel/policy/policy_rule_ir.py`（声明式策略 IR，896 行 + CLI 编译器）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/arbiteros_kernel/policy/unary_gate_rules.json`（13 UG 规则）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/arbiteros_kernel/instruction_parsing/helpers/skill_trust.py`（skill 包扫描信任，490 行）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/arbiteros_kernel/role_policy_cfg_loader.py`（请求级角色策略集）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/arbiteros_kernel/policy_runtime.py`（RUNTIME 单例 + EFSM + taint + plan 状态）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/arbiteros_kernel/instruction_parsing/types.py`（指令类型 + `compute_prop_taint_for_instruction`）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/arbiteros_kernel/instruction_parsing/registries/linux.py`（双层 registry）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/arbiteros_kernel/user_approval.py`（用户批准提权传播）
- `~/ai/ArbiterOS/ArbiterOS-Kernel/redteam/README.md`（redteam 测试方案，20 场景 105 case）
- `~/ai/ArbiterOS/assets/docs/kernel.md` / `policy.md` / `kernel-policy_interface.md` / `registry_usage.md`（架构文档）
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/`（nop 现有 security 包，74 个 .java 文件；关键：`SecurityLevel.java`/`Principal.java`/`ContentOrigin.java`/`DefaultContentTrustEvaluator.java`/`SecurityCheckpointChain.java`/`Permission.java`/`DockerSandboxBackend.java`/`DBDenialLedger.java`）
- `ai-dev/analysis/agent-survey/2026-08-01-harness-mechanism-reference-framework.md`（D1-D12 参考框架）
- `ai-dev/analysis/agent-survey/2026-08-01-mcp-gateway-session-security-analysis.md`（mcp-gateway 三元组安全模型对照；注：该报告误称 nop"单租户"，本报告已核实 nop 实为多租户）
- `ai-dev/analysis/agent-survey/2026-08-01-agent-governance-toolkit-analysis.md`（AGT 策略引擎+审批流对照）
