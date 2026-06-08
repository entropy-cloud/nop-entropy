# OpenSquilla 技术分析

> Status: open
> Date: 2026-06-08
> Scope: ~/ai/opensquilla — Token-Efficient Microkernel AI Agent Runtime
> Conclusion:

## Context

- OpenSquilla 是 OpenClaw 的后续项目（Apache-2.0），定位为"token-efficient microkernel AI agent runtime"
- 核心差异化：本地模型路由器（SquillaRouter）+ 统一 Turn 循环 + Meta-Skill DAG 编排 + 多通道消息
- 版本 0.3.1，Python 3.12+，约 182K 行源码 + 175K 行测试
- 调研目的：理解其微内核架构、TurnRunner 分阶段设计、路由系统、Meta-Skill 编排，为 Nop Agent 生态提供参考

## Analysis

### 项目定位

| 维度 | 值 |
|------|-----|
| **组织** | opensquilla (社区项目) |
| **许可** | Apache-2.0 |
| **版本** | 0.3.1 |
| **语言** | Python 3.12+ (Starlette + SQLModel + Pydantic) |
| **LOC** | ~182K 行源码 (626 .py) + ~175K 行测试 (588 .py) = **357K 行** |
| **核心框架** | Starlette ASGI + aiosqlite + pydantic-settings |
| **LLM 接入** | 30+ Provider（OpenRouter/OpenAI/Anthropic/Ollama/DeepSeek/Gemini 等） |
| **定位** | 面向个人用户的 Token 节约型 Agent 运行时——不是框架，是产品 |

### 顶层架构

```
opensquilla/
├── src/opensquilla/
│   ├── engine/                  # Agent 核心引擎 (~25K 行, 57 文件)
│   │   ├── agent.py             # Agent 状态机 + 核心循环 (~5,770 行)
│   │   ├── turn_runner/         # 8 阶段 TurnRunner 分解
│   │   │   ├── input_stage.py           # 1. 输入规范化
│   │   │   ├── provider_and_tools_stage.py  # 2. Provider + Tool 解析
│   │   │   ├── agent_bootstrap_stage.py  # 3. Agent 实例创建
│   │   │   ├── attachment_stage.py       # 4. 附件构建
│   │   │   ├── compaction_and_history_stage.py  # 5. 压缩 + 历史
│   │   │   ├── prompt_assembler_stage.py # 6. System Prompt 组装
│   │   │   ├── stream_consumer_stage.py  # 7. 流式消费 + Tool 处理
│   │   │   ├── turn_finalizer_stage.py   # 8. 持久化 + 记忆 + 成本
│   │   │   └── harness.py                # 适配器绑定 (1,129 行)
│   │   ├── pipeline.py          # Pre-turn Pipeline
│   │   ├── hooks/               # Turn/Tool/Compaction 钩子
│   │   ├── steps/               # 路由/预算/Token 步骤
│   │   └── subagent.py          # 子 Agent 管理
│   ├── gateway/                 # ASGI Gateway (~28K 行, 61 文件)
│   │   ├── app.py               # ASGI 应用工厂
│   │   ├── boot.py              # ServiceContainer + 启动编排
│   │   ├── websocket.py         # WebSocket 连接管理
│   │   ├── rpc/                 # 20+ RPC 处理器
│   │   └── static/              # Control UI 静态文件
│   ├── cli/                     # CLI 命令 (~21K 行, 133 文件)
│   ├── tools/                   # 工具系统 (~17K 行, 44 文件)
│   │   ├── dispatch.py          # 10 步 Tool 调度管线 (654 行)
│   │   ├── registry.py          # 工具注册表 + @tool 装饰器
│   │   └── builtin/             # 23 个内置工具模块
│   ├── skills/                  # 技能系统 (~21K 行, 95 文件)
│   │   ├── bundled/             # 62 个内置技能
│   │   ├── meta/                # Meta-Skill DAG 编排
│   │   ├── hub/                 # ClawHub/GitHub 外部技能市场
│   │   └── creator/             # 技能创建/发布
│   ├── memory/                  # 记忆系统 (~10K 行, 34 文件)
│   ├── channels/                # 消息通道 (~10K 行, 24 文件, 12 平台)
│   ├── provider/                # LLM Provider 层 (~7K 行, 19 文件)
│   ├── session/                 # 会话持久化 (~6K 行, 14 文件)
│   ├── scheduler/               # Cron 调度器 (~6K 行, 21 文件)
│   ├── sandbox/                 # 安全沙箱 (~4K 行, 15 文件)
│   ├── squilla_router/          # 本地模型路由 (~3K 行, 19 文件)
│   └── ...                      # + 15 个辅助模块
├── tests/                       # 588 个测试文件, ~175K 行
└── migrations/                  # 14 个数据库迁移 (V001–V014)
```

---

### 1. TurnRunner：8 阶段管道架构

OpenSquilla 的核心设计决策是将 Agent Turn 分解为 **8 个有序阶段**，每个阶段是一个自包含的类，通过 frozen dataclass 交换输入/输出。

#### 8 阶段流水线

| 阶段 | 文件 | 职责 |
|------|------|------|
| `InputStage` | `input_stage.py` (192 行) | 规范化用户输入，处理系统事件，持久化用户消息 |
| `ProviderAndToolsStage` | `provider_and_tools_stage.py` (217 行) | 解析 LLM Provider，构建 Tool 定义/Handler |
| `AgentBootstrapStage` | `agent_bootstrap_stage.py` | 创建 Agent 实例，注入预算和记忆快照 |
| `AttachmentStage` | `attachment_stage.py` | 构建附件消息（图片、文件） |
| `CompactionAndHistoryStage` | `compaction_and_history_stage.py` | Preflight 压缩，加载历史消息 |
| `PromptAssemblerStage` | `prompt_assembler_stage.py` | 组装 System Prompt，运行 Pipeline |
| `StreamConsumerStage` | `stream_consumer_stage.py` (933 行) | 消费 Agent 流，处理 Tool Call 和压缩 |
| `TurnFinalizerStage` | `turn_finalizer_stage.py` (623 行) | 持久化 Transcript，刷新记忆，汇总成本 |

#### Harness + Adapter 模式

`harness.py` (1,129 行) 将 TurnRunner 实例方法绑定到 **Protocol 形式的端口**，通过延迟导入避免循环依赖。这个模式使得：
- 每个阶段可独立单元测试，不需要完整运行时
- 数据流通过 frozen dataclass 显式化，防止意外突变
- 适配器隔离了各阶段间的具体依赖

#### StreamConsumer 事件处理器

`StreamConsumerStage` 是最复杂的阶段（933 行），包含多个专用事件处理器：
- `_TextDeltaHandler` — 累积文本 delta，剥离协议泄漏
- `_ToolUseStartHandler` — 处理合成 Tool Call 剥离
- `_ToolResultHandler` — 捕获 Artifact 交付失败
- `_ErrorHandler` — 重写超时信封，抑制错误 yield
- `_DoneHandler` — 应用路由元数据，处理 savings 和 artifact 交付
- `_CompactionHandler` — 持久化压缩结果，刷新记忆快照

#### 与 opencode Agent Loop 的对比

| 维度 | opencode | OpenSquilla |
|------|---------|-------------|
| **循环结构** | 单一 `runLoop()` while 循环 (prompt.ts, 1,755 行) | 8 阶段顺序管道 |
| **状态管理** | DB-based session + step counter | Agent 状态机 (IDLE→THINKING→TOOL_CALLING→STREAMING→DONE) |
| **Tool 处理** | 循环内 `processor.process` | `StreamConsumerStage` 专用处理器 |
| **压缩触发** | overflow 检测 → compaction agent | `CompactionAndHistoryStage` preflight |
| **测试性** | 需要完整 session | 每个阶段可独立测试 |
| **语言** | TypeScript (Effect v4) | Python (asyncio) |

---

### 2. SquillaRouter：本地 ONNX 模型路由器

这是 OpenSquilla 最核心的差异化能力——一个**完全在本机运行的模型路由器**，用户 prompt 永远不会离开本机来做路由决策。

#### 路由架构

```
用户输入 → 特征提取 → ONNX 分类器 → 层级决策 → 后处理
                                              │
                    ┌─────────────────────────┼──────────────────────┐
                    │                         │                      │
               c0 (Flash)               c1 (Pro)              c3 (Opus)
              快速/廉价               默认/均衡              最强/最贵
```

#### 分类特征

- **BGE ONNX embeddings** — 语义理解（bundled int8 模型）
- **MLP 分类器 + 辅助头** — 复杂度评分
- **文本特征** — 长度、语言、代码、关键词

#### 层级配置（默认 OpenRouter profile）

| 层级 | 模型 | 用途 |
|------|------|------|
| **c0** | `deepseek/deepseek-v4-flash` | 简单聊天、短重写、提取、低风险 Q&A |
| **c1** | `deepseek/deepseek-v4-pro` | 默认均衡文本路由 |
| **c2** | `z-ai/glm-5.1` | 多步编码、结构化推理、大上下文 |
| **c3** | `anthropic/claude-opus-4.7` | 高难度规划、深度审查、高风险综合 |
| **image** | `moonshotai/kimi-k2.6` | 图像/视觉专用路由 |

#### 思考与 Prompt 控制器

- **Thinking Controller** — 4 级思考模式 (T0 无思考 → T3 深度思考)
- **Prompt Controller** — 3 级 Prompt 策略 (P0 压缩 → P2 完整)

#### 智能路由保护机制

| 机制 | 说明 |
|------|------|
| **置信度保护** | 低置信度分类回退到 `default_tier` |
| **投诉检测** | 检测不满（100+ 中英文术语），自动升级 |
| **反降级** | 配置窗口内阻止降级（KV cache 友好） |
| **大上下文底线** | >25K token → T2, >80K 或 >40% context → T3 |
| **路由历史** | 每会话有界历史（最多 5 条，30 分钟窗口） |
| **图像感知** | 自动选择支持视觉的层级 |

#### PinchBench 基准

| Agent | 基础模型 | 平均分 | 总输入 Token | 总输出 Token | 总成本 |
|-------|---------|-------|------------|------------|-------|
| OpenSquilla | 路由器 (Opus4.7 + GLM5.1 + DS4 Flash) | 0.9251 | 1,721,328 | 61,475 | $0.688 |
| OpenClaw | Claude Opus 4.7 | 0.9255 | 3,066,243 | 50,890 | $6.233 |

**关键发现**：相近的分数，但成本仅为 1/9（$0.688 vs $6.233），输入 token 减少 44%。

---

### 3. Tool 系统：10 步调度管线

#### 调度管线

`dispatch.py` (654 行) 的 `build_tool_handler()` 产生一个 10 步异步 handler：

1. 入口注入守卫
2. 注册表查找（miss 时返回结构化错误）
3. `ToolHook.before_tool` 扇出
4. 策略链（第一个拒绝即停止）
5. 运行预算预留
6. Handler 分发（`current_tool_context` ContextVar）
7. 运行预算提交/中止
8. `ToolHook.after_tool` 扇出
9. 结果最终化
10. ContextVar 重置

#### 23 个内置工具

| 工具 | 文件 | 功能 |
|------|------|------|
| `exec_command`, `background_process` | `shell.py` | Shell 执行 |
| `read_file`, `write_file`, `list_directory` | `filesystem.py` | 文件操作 |
| `edit_file`, `apply_patch` | `file_authoring.py` | 文件编辑 |
| `web_search` | `web.py` | Web 搜索 |
| `web_fetch`, `http_request` | `web_fetch.py` | URL 获取 |
| `execute_code` | `code_exec.py` | 代码执行 |
| `memory_search`, `memory_save` | `memory_tools.py` | 记忆管理 |
| `skill_view` | `skill_tools.py` | 技能查看 |
| `session_search` | `session_search.py` | 会话搜索 |
| `sessions_yield` | `sessions.py` | 子 Agent 控制 |
| Git 操作 | `git.py` | Git 工具 |
| Media 处理 | `media.py` | 媒体工具 |
| Artifact 管理 | `artifacts.py` | Artifact 工具 |
| Channel 消息 | `messaging.py` | 消息通道 |
| Agent 管理 | `agents.py` | Agent 管理 |
| Admin 工具 | `admin.py` | 管理工具 |
| Meta 工具 | `meta_tools.py` | Meta-Skill 内省 |
| Router 控制 | `router_control.py` | 路由层级控制 |

#### Tool 注册模式

```python
@tool(name="read_file", description="...", params={...}, required=["path"])
async def read_file(path: str) -> str: ...
```

每个 `ToolSpec` 包含：`name`, `description`, `parameters`, `required`, `owner_only`, `exposed_by_default`, `execution_timeout_seconds`, `result_budget_class`。

---

### 4. Safety / Guardrail 全景（深入分析）

OpenSquilla 的安全体系是一个 **5 层纵深防御**架构，从 Tool Dispatch 管线到进程级沙箱，每一层都有独立的 fail-closed 保证。以下按数据流顺序从外到内逐层展开。

#### 4.1 第 1 层：Prompt 注入检测（`safety/injection_guard.py`，315 行）

这是所有外部内容进入 LLM 上下文前的第一道防线。

**四类威胁分类**（基于 Simon Willison 分类法、GARAK 基准、Anthropic 红队报告）：

| 威胁类 | 检测目标 | 正则示例 |
|--------|---------|---------|
| `prompt_override` | 试图让模型忽略/重置系统指令 | `ignore\s+(all\s+)?(prior|previous)\s+(instructions?|rules?)` |
| `role_hijack` | 伪装为 system/admin/root | `\[(system|admin|root)\][:>\s]` |
| `exfiltration` | 试图泄露 secrets/API keys/env vars | `(dump|leak|print|show)\s+(the\s+)?(secrets?|api[_\s-]?keys?)` |
| `invisible_char` | 零宽字符/BIDI 控制字符走私 | `[\u200b\u200c\u200d\ufeff]` / `[\u202a-\u202e]` |

**三层执行模式**：

| 模式 | 行为 |
|------|------|
| `off` | 不扫描，原样返回 |
| `report` | 扫描并记录 `InjectionFinding`，但**不修改内容** |
| `enforce` | 扫描并**替换匹配内容**为 `[BLOCKED: unsafe prompt content removed from {source}]` |

**结构化信封**：`wrap_untrusted(content, source)` 将所有外部内容包裹在 `<untrusted source='...'>...</untrusted>` 中：
- 内部 payload 做 XML 转义（`&`, `<`, `>`, `"`, `'`），防止闭合标签注入
- 防止注入 `<system>` / `<available_skills>` 等兄弟元素
- 下游 `extract_tool_call_refusal_reason()` 检查 Tool Call 的 origin trace 是否在 untrusted 块内，如果是则返回 `tool_call_inside_untrusted` 拒绝原因

#### 4.2 第 2 层：Tool 风险分层 + 权限矩阵（`safety/tool_tiers.py` + `safety/permission_matrix.py`）

**RiskTier 三层分级**（`tool_tiers.py`，105 行）：

| 层级 | 语义 | 执行条件 |
|------|------|---------|
| `SAFE` | 自动执行 | 无 ACK 门控 |
| `CONFIRM` | 需确认 | 阻塞等待 ACK |
| `ADMIN_ONLY` | 仅操作员 | 非操作员直接拒绝 |

**硬编码 ADMIN_ONLY 工具集**（不可降级，即使 `declare_tier` 调用也无法覆盖）：
- Shell 执行：`shell_exec`, `exec_command`, `background_process`
- 文件写入：`file_write`, `write_file`, `edit_file`, `apply_patch`
- Git：`git_push`
- 通道：`channel_send_as_admin`

**默认策略：fail-closed** — 未通过 `declare_tier()` 声明的工具默认为 `CONFIRM`。

**通道→工具权限矩阵**（`permission_matrix.py`，144 行）：

| 通道类型 | 允许的层级 | 特殊规则 |
|---------|-----------|---------|
| `webui` | SAFE + CONFIRM + ADMIN_ONLY | 完全访问 |
| `dm` | SAFE + CONFIRM | ADMIN_ONLY 被拒绝 |
| `group` | SAFE + CONFIRM | ADMIN_ONLY 被拒绝 |
| 未知通道 | SAFE + CONFIRM | **视为 DM（fail-closed）** |

**Principal 身份模型**：
```python
@dataclass(frozen=True)
class Principal:
    role: str = "user"            # "operator" 可绕过 ADMIN_ONLY
    channel_id: str | None = None # 用于 per-channel override
```

**Per-channel override 机制**：`register_channel_override(channel_id, extra_tiers)` 允许操作员为特定 DM/group 通道额外授予 ADMIN_ONLY 权限，而不改变全局矩阵。

#### 4.3 第 3 层：安全沙箱 + 治理系统（`sandbox/`，15 文件 ~3.6K 行）

这是 OpenSquilla 安全体系中最复杂也最完整的部分，包含 7 个子模块：

##### 4.3.1 安全等级与策略构建（`sandbox/types.py` + `sandbox/policy.py`）

**SecurityLevel 四级模型**（有序 IntEnum，`level >= STRICT` 语义有效）：

| 等级 | 标签 | 语义 | 资源限制 | 网络 | 审批 |
|------|------|------|---------|------|------|
| `DISABLED` (0) | L0-disabled | 仅 legacy 模式，需 `allow_legacy_mode=True` | 10x CPU, 4x MEM | HOST | 否 |
| `STANDARD` (1) | L1-standard | 正常工具执行 | 默认 | NONE | 否（除非 untrusted） |
| `STRICT` (2) | L2-strict | 高风险操作 | 50% CPU/MEM, 128 PIDs | NONE | 是 |
| `LOCKED` (3) | L3-locked | 最低可见性 deny-by-default | 33% CPU, 25% MEM, 64 PIDs | NONE | **必须** |

**LevelHints 输入模型**（`policy.py`）：
```python
@dataclass(frozen=True)
class LevelHints:
    trusted_source: bool = True
    needs_network: bool = False
    writes_outside_workspace: bool = False
    crosses_trust_boundary: bool = False
    high_impact: bool = False
```

**`select_level(action_kind, hints)` 确定性规则表**：

| action_kind | 默认等级 | 升级条件 |
|-------------|---------|---------|
| `fs.read`, `fs.list`, `fs.grep` | STANDARD | — |
| `fs.write`, `fs.edit`, `patch.apply` | STANDARD | `writes_outside_workspace` 或 `crosses_trust_boundary` → STRICT |
| `git.read`, `git.write` | STANDARD | 同上 |
| `code.exec`, `shell.exec` | STANDARD | `!trusted_source` → STRICT; `high_impact` 或 `crosses_trust_boundary` → LOCKED |
| `network.fetch`, `web.fetch` | STANDARD | `!trusted_source` 或 `crosses_trust_boundary` → LOCKED |
| 其他 | STANDARD | `!trusted_source` → STRICT; `high_impact` → LOCKED |

**`build_policy()` 完整策略物化**：
- 挂载列表：workspace 必挂载；STANDARD 可加 extra_ro/rw；STRICT 降级为 ro；LOCKED 仅 workspace
- 环境白名单：`PATH`, `HOME`, `LANG`, `LC_ALL`, `TERM`, `SHELL`, `USER`, `LOGNAME`, `HOSTNAME`, `PWD`
- 审批要求：`LOCKED` 必须审批；`STRICT` + untrusted 必须审批

##### 4.3.2 审批门 + 拒绝账本（`sandbox/governance.py`，527 行）

**`gate_execution()` 顶层组合**（4 步顺序检查）：

```
1. Session 暂停检查 → 如果 session 已被标记为 paused → THRESHOLD_EXCEEDED 拒绝
2. 拒绝阈值检查 → 如果 session 累计拒绝 >= threshold → 标记 paused + THRESHOLD_EXCEEDED
3. Post-denial 守卫 → 如果与上次被拒的 fingerprint 相同且无有效 follow-up tag → REPEATED_SAME_INTENT
4. 审批门 → 如果 policy.require_approval → 入队等待人类审批
```

**DenialLedger 拒绝账本**（per-session，asyncio.Lock 保护）：
- 按 `(session_id, fingerprint)` 计数
- 默认阈值：3 次拒绝后自动暂停 autonomous 执行
- 每次 `record_denial()` 同时 purge StaleOutputCache 中的对应成功输出

**Post-denial 守卫（§8.4）**：
- 被拒后，只有 3 种合法的 follow-up：`LOWER_PRIVILEGE` / `EXPLAIN` / `NARROWER_APPROVAL`
- 无 tag 的盲重试（相同 fingerprint）被阻止
- 返回 `SuggestedNextStep` 指导 Agent 下一步行为

**DenialResult 结构化拒绝信封**：
```python
@dataclass(frozen=True)
class DenialResult:
    reason: DenialReason            # human_rejected / policy_denied / threshold_exceeded / repeated_same_intent / ...
    suggested_next_step: SuggestedNextStep  # replan / ask_user / lower_privilege / narrower_approval
    level: SecurityLevel
    action_fingerprint: str         # SHA-256[:32] of action identity
    message: str                    # 人类可读解释
    retryable: bool = True
    status: Literal["denied"] = "denied"
```

##### 4.3.3 Stale Output Cache（§8.3 卫生）（`sandbox/stale_output_cache.py`，154 行）

**问题**：Agent 执行了一个危险操作并得到成功输出后，如果该操作后来被拒绝，Agent 可能从 in-context memory 中回忆旧输出。

**解决方案**：
- `StaleOutputCache`：内存缓存，按 `(session_id, fingerprint)` 存储最近一次成功执行的 payload
- 每次 `record_denial()` 时自动 purge 对应 fingerprint 的缓存
- **局限性**（文档明确承认）：无法清除 LLM 的 in-context memory，只能清除 opensquilla 自有缓存

##### 4.3.4 敏感路径保护（`sandbox/sensitive_paths.py`，364 行）

**硬阻止（hard-block）**：不经过审批流程，直接阻止。只有 `operator elevated full` 模式可以覆盖。

**敏感路径前缀**：
```
~/.ssh, ~/.aws, ~/.azure, ~/.config/gcloud, ~/.docker/config,
~/.kube, ~/.npmrc, ~/.pypirc, ~/.netrc, ~/.gnupg,
~/.password-store, /etc, /boot, /sys, /proc, /dev, /root,
/var/log, /lib/systemd, /usr/lib/systemd
```

**敏感文件后缀**（不论所在目录）：
```
/id_rsa, /id_ed25519, /id_ecdsa, /id_dsa,
/known_hosts, /authorized_keys,
/.env, /.env.local, /.env.development, /.env.production, /.env.test,
/.bash_history, /.zsh_history, /.mysql_history, /.psql_history
```

**三重扫描 API**：
- `is_sensitive_path(path)` — 单路径检查
- `sensitive_path_in_text(text)` — 自由文本中扫描路径（用于 Shell 命令参数扫描）
- `sensitive_target_in_command(command)` — 破坏性命令目标扫描（`rm /tmp/ok /etc/bad` → 阻止整个命令）

**Workspace 感知**：容器部署通常将 workspace 放在 `/root/.opensquilla/workspace`，宽泛的 `/root` 前缀不应使合法 workspace 不可用。`sensitive_path_marker()` 会检查路径是否在 workspace 内，在 workspace 内的路径只阻止凭证类叶子文件（`.env`, 私钥）。

**环境逃生口**：`OPENSQUILLA_SENSITIVE_PATHS_DISABLED=1` 可全局关闭（仅用于可信单用户环境/E2E 测试）。

##### 4.3.5 SandboxSettings 配置验证（`sandbox/config.py`，162 行）

**两个独立开关的真值表**：

| sandbox | security_grading | 有效行为 |
|---------|-----------------|---------|
| `true` | `true` | 完全模式：隔离 + 级别选择 + 审批 |
| `true` | `false` | 隔离开，固定 STANDARD 策略，无审批 |
| `false` | `true` | **不一致 → 强制 grading=false + 警告** |
| `false` | `false` | Legacy 模式（默认） |

**`DISABLED` 级别保护**：选择 `DISABLED` 必须同时设置 `allow_legacy_mode=True`，需要两个显式动作。

##### 4.3.6 运行时门面 + `@sandboxed` 装饰器（`sandbox/integration.py`，592 行）

**`@sandboxed` 装饰器工厂**：

```python
@sandboxed(kind="shell.exec", hints=LevelHints(trusted_source=True))
async def exec_command(command: str, ...) -> str:
    # 装饰器自动执行：
    # 1. 构建 argv fingerprint
    # 2. 调用 gate_action() → select_level → build_policy → gate_execution
    # 3. 如果被拒 → 返回 JSON DenialResult
    # 4. 如果允许 → 执行 handler → record_success
    ...
```

**关键设计**：
- 装饰器接受 `_sandbox_followup` 隐藏 kwarg，Agent 可以在拒绝后标记为合法 follow-up
- `record_payload=True`（默认）将成功输出存入 StaleOutputCache
- `argv_factory` / `cwd_factory` 可自定义 fingerprint 和 workspace 解析

**`gate_action()` fail-closed 保证**：
- runtime 未配置 → `RUNTIME_UNCONFIGURED` 拒绝（不是静默放行）
- Windows + `backend=auto` → 自动降级到 sandbox=false（显式 backend 选择仍会硬失败）

##### 4.3.7 进程级沙箱后端（`sandbox/backend/`）

**BubblewrapBackend**（Linux，319 行）：
- 每次执行启动独立 `bwrap` 子进程
- 命名空间隔离：`--unshare-pid`, `--unshare-uts`, `--unshare-ipc`, `--unshare-cgroup-try`, `--unshare-user-try`
- 能力剥离：`--cap-drop ALL`
- 终端脱离：`--new-session`（防止 TIOCSTI 逃逸）
- 网络隔离：`NetworkMode.NONE` → `--unshare-net`
- 文件系统：tmpfs 根 + `/usr`, `/bin`, `/lib` 只读绑定 + workspace 按策略 ro/rw
- 输出上限：1 MiB/stream，超出截断
- 超时：`asyncio.wait_for` → SIGTERM → 2s grace → SIGKILL

**SeatbeltBackend**（macOS，536 行）：
- 生成 Apple Seatbelt profile（当前仅渲染，执行待实现）

**NoopBackend**（任意平台）：
- 直通执行，无隔离
- 仅当 `sandbox=false` 时使用

**安全不可降级保证**：`SandboxBackendError` 明确区分"沙箱启动失败"和"命令非零退出"。启动失败**绝不回退**到 unsandboxed 执行——调用方必须 surface 为拒绝或传播异常。

#### 4.4 第 4 层：敏感路径硬阻止（工具内嵌）

在 Tool Handler 内部，**在 `@sandboxed` 门之前**，还有一个独立的敏感路径检查层：

- `shell.py` → `sensitive_path_in_text(command)` 扫描整个命令
- `filesystem.py` → `sensitive_path_marker(resolved_path)` 检查每个文件路径
- `code_exec.py` → `sensitive_path_marker(literal)` 检查代码中的路径字面量
- `patch.py` → `sensitive_path_marker(resolved)` 检查 patch 目标
- `media.py` → `is_sensitive_path(resolved)` 检查媒体输出路径

**阻止行为**：返回 `build_block_envelope()` 结构化拒绝，`retryable=False`，告知 Agent "这是硬阻止，不论用户审批都不可执行，除非 operator 设置 /elevated full"。

#### 4.5 第 5 层：Tool Dispatch 管线注入守卫（`tools/dispatch.py`）

在 10 步调度管线的 **第 1 步**（入口注入守卫），对所有 Tool Call 做 injection guard 检查：

1. 提取 Tool Call 的 origin trace（产生 tool_use 块的 assistant 消息片段 + 上下文）
2. 调用 `extract_tool_call_refusal_reason(origin_trace)`
3. 如果 origin trace 包含 `<untrusted>` 块且其中有 tool_call marker → 返回结构化拒绝

**被阻止的 tool_call_markers**：`<tool_use`, `<tool_call`, `<function_call`, `"tool":`, `"function":`

**这是双重防护**：即使 LLM 被外部内容诱导生成 tool_call，只要 origin 在 untrusted 块内，执行前就会被阻止。

#### 4.6 五层纵深防御总结

```
外部内容（Web fetch / Channel / File read）
  │
  ├─ 第 1 层: Injection Guard ──── 分类 + 扫描 + wrap_untrusted()
  │                                 │
  │                                 └─ enforce 模式: 替换为 [BLOCKED]
  │
  ├─ Tool Call 产生
  │   │
  │   ├─ 第 5 层: Dispatch Injection Guard ──── 检查 origin trace
  │   │                                          │
  │   │                                          └─ untrusted 块内的 tool_call → 拒绝
  │   │
  │   ├─ 第 4 层: 敏感路径硬阻止 ──── shell/filesystem/code_exec 内
  │   │                                │
  │   │                                └─ sensitive_path → hard-block
  │   │
  │   ├─ 第 2 层: Tool Risk Tier + Permission Matrix
  │   │            │
  │   │            ├─ ADMIN_ONLY 工具 → 非 operator 拒绝
  │   │            └─ DM/Group 通道 → admin_only_denied
  │   │
  │   └─ 第 3 层: 沙箱治理系统
  │                │
  │                ├─ select_level(action_kind, hints) → SecurityLevel
  │                ├─ build_policy(level) → SandboxPolicy
  │                ├─ gate_execution():
  │                │   ├─ session paused? → THRESHOLD_EXCEEDED
  │                │   ├─ denial threshold? → pause + deny
  │                │   ├─ post-denial guard → REPEATED_SAME_INTENT
  │                │   └─ approval gate → human approval (300s timeout)
  │                ├─ backend.run() → bubblewrap/seatbelt/noop
  │                └─ stale output cache → purge on denial
```

#### 4.7 安全体系的关键设计原则

| 原则 | 体现 |
|------|------|
| **Fail-closed** | 未声明工具默认 CONFIRM；runtime 未配置默认拒绝；未知通道默认 DM 权限 |
| **不可降级** | 硬编码 ADMIN_ONLY 不可被 `declare_tier` 覆盖；sandbox 启动失败不回退 host 执行 |
| **双重确认** | DISABLED 级别需要两个显式动作（选择 + allow_legacy_mode） |
| **可审计** | 每次 gate_decision 都有结构化 debug log（fingerprint, level, decision, reason） |
| **可恢复** | DenialResult 携带 `suggested_next_step` 和 `retryable` 标志 |
| **可覆盖但不可静默** | operator 可覆盖 ADMIN_ONLY，但每条 override 都有 log |
| **环境感知** | Windows 自动降级但只限 auto backend；workspace 内的敏感前缀只阻止凭证叶子 |

---

### 5. 记忆系统

#### 存储

SQLite + **sqlite-vec** 向量索引 + **FTS5** 全文搜索，四张表：
- `files` — 跟踪的源文件
- `chunks` — 文本块 + embedding
- `embedding_cache` — embedding 计算缓存
- `meta` — schema 版本

#### 检索管线

1. **时间衰减** — 30 天半衰期指数衰减；`MEMORY.md` 永不过期
2. **Jaccard 相似度** — 多样性
3. **MMR 重排** — Maximal Marginal Relevance
4. 默认：`max_results=6`, `min_score=0.35`

#### Dream 系统

后台记忆整合：
- `candidates.py` — 选择整合候选
- `quarantine.py` — 隔离不确定内容
- `ranking.py` — 排名整合候选
- `receipts.py` — 持久化回执
- `runner.py` — 后台运行器

默认关闭，需显式启用（`preview_mode=true` + `auto_schedule=false`）。

---

### 6. Meta-Skill DAG 编排

这是 OpenSquilla 最有特色的编排系统——将 SOP 风格的文本计划编译为可执行 DAG。

#### 核心组件

| 组件 | 文件 | 行数 | 职责 |
|------|------|------|------|
| `MetaOrchestrator` | `orchestrator.py` | 1,396 | 运行 MetaPlan 为协调的子 Agent |
| `MetaPlan` / `MetaStep` | `types.py` | 218 | 计划/步骤数据模型 |
| SOP Compiler | `sop_compiler.py` | 1,013 | 将 SOP 文本翻译为 DAG |
| Scheduler | `scheduler.py` | — | DAG 并行 asyncio 执行 |
| Parser | `parser.py` | — | YAML 计划解析 |
| Templating | `templating.py` | — | 受限 Jinja 环境 |

#### 6 种步骤类型

| 类型 | 执行器 | 描述 |
|------|--------|------|
| `agent` | `executors/agent.py` | 完整子 Agent + Tool 循环 |
| `llm_classify` | `executors/llm_classify.py` | 受约束单次 LLM 调用 |
| `llm_chat` | `executors/llm_classify.py` | 自由单次 LLM 调用 |
| `tool_call` | `executors/tool_call.py` | 直接工具调用 |
| `skill_exec` | `executors/skill_exec.py` | 运行另一个技能 |
| `user_input` | `executors/user_input.py` | 暂停等待用户输入 |

`user_input` 步骤抛出 `MetaPaused` 异常，携带 schema（字段、类型、提示、选项），任何前端（Web/CLI/IM）都可以渲染澄清表单。

#### 62 个内置技能

包括：文档工具（pdf-toolkit、docx、pptx、xlsx）、研究工具（deep-research、multi-search-engine）、媒体工具（video-merger、voiceover-studio）、开发工具（git-diff、github、tmux）、Meta-Skills（meta-paper-write、meta-web-research-to-report、meta-short-drama）等。

---

### 7. Provider 层

#### 架构

```python
@runtime_checkable
class LLMProvider(Protocol):
    provider_name: str
    def chat(self, messages, tools, config) -> AsyncIterator[StreamEvent]: ...
    async def list_models(self) -> list[ModelInfo]: ...
```

#### Backend 分类

`ProviderBackend = Literal["openai_compat", "openai_responses", "anthropic", "ollama", "unsupported_oauth", "unsupported_responses"]`

30+ Provider 映射到 6 种 backend：
- `openai_compat`：OpenRouter、OpenAI、DeepSeek、Gemini、DashScope、Groq、Zhipu、Volcengine、SiliconFlow、vLLM、LM Studio 等
- `anthropic`：Anthropic、MiniMax
- `ollama`：Ollama
- `openai_responses`：OpenAI Responses API

#### Stream Event 类型

`TextDeltaEvent`, `ToolUseStartEvent`, `ToolUseDeltaEvent`, `ToolUseEndEvent`, `DoneEvent`, `ErrorEvent`, `ProviderHeartbeatEvent`

---

### 8. 会话持久化

#### 数据模型（SQLModel）

| 模型 | 表 | 用途 |
|------|-----|------|
| `SessionNode` | `sessions` | 会话状态、路由、Token、成本 |
| `TranscriptEntry` | `transcript_entries` | 消息（user/assistant/system/tool） |
| `SessionSummary` | `session_summaries` | 压缩摘要记录 |
| `SessionContextState` | `session_context_states` | Provider 特定上下文状态 |
| `MemoryDurableReceipt` | `memory_durable_receipts` | 记忆检查点 |
| `AgentTaskRecord` | `agent_tasks` | 任务运行账本 |

存储层使用 `aiosqlite`，schema 版本 7，14 个迁移。

---

### 9. 通道系统

#### 12 个平台适配器

DingTalk、Discord、Feishu（Lark）、Matrix、MS Teams（隐藏）、QQ、Slack、Telegram、Terminal、WebSocket、WeCom、Artifact Delivery

#### 通道契约

每个适配器声明：
- `CAPABILITY_TIER`：`GREEN-shipping` / `YELLOW-experimental` / `RED-blocked`
- `DM_SAFETY_TIERS`：`safe` + `confirm`（无 `admin-only`）
- 27 个能力标签：`streaming`, `group_chat`, `mentions`, `typing_indicator`, `artifact_delivery` 等

---

### 10. Gateway

Starlette ASGI 服务器，默认绑定 `127.0.0.1:18791`：
- WebSocket RPC (`ReqFrame` / `ResFrame` / `EventFrame` / `PingFrame` / `PongFrame`)
- 20+ RPC 处理器（chat、sessions、agents、channels、cron、config 等）
- 嵌入式 Control UI (`/control/`)

---

### 11. MCP 客户端/服务器

**客户端**：`discover_and_register()` 自动发现 MCP 服务器并注册其工具。支持 SSE 和 stdio 传输。

**服务器**：FastMCP 服务器工厂，暴露 `conversations_list`, `session_resolve`, `messages_read`, `messages_send`, `events_wait`, `transcript_export` 作为 MCP 工具。

---

### 12. 上下文预算与压缩

#### 两层预算治理

| 层 | 策略 | 维度 |
|-----|------|------|
| **结果预算** | `ToolResultBudgetPolicy` | 单个结果大小、每 Turn 总大小 |
| **运行预算** | `ToolRunBudgetPolicy` | Web 搜索调用数、外部文本总量 |

当上下文超出窗口时，使用 LLM 总结旧的 Transcript 段，通过 `SessionSummary` 记录。

---

### 13. 子 Agent 系统

```python
@dataclass
class SubagentSpec:
    task: str
    label: str = ""
    model_id: str | None = None
    timeout: float = 300.0
    max_iterations: int = 0
    workspace_dir: str | None = None
```

`SubagentManager.spawn_group()` 支持并行子 Agent，`SubagentRegistry` 跟踪状态：`running`, `done`, `error`, `aborted`, `archived`, `orphaned`。支持 abort via `asyncio.Task.cancel()`。有最大深度限制。

---

## 与其他 Agent 框架的对比

| 维度 | OpenSquilla | opencode | Hermes Agent |
|------|------------|---------|-------------|
| **语言** | Python 3.12+ | TypeScript (Bun) | Python 3.11+ |
| **核心循环** | 8 阶段管道 | 单一 while 循环 | 同步 while 循环 |
| **LOC** | ~182K (src) | ~430K (25 packages) | ~2,027K |
| **模型路由** | 本地 ONNX 分类器 (4 层) | 无（单一模型） | 无 |
| **Tool 系统** | 10 步调度管线 + 23 工具 | 16 内置工具 + MCP | 82 工具文件 |
| **编排** | Meta-Skill DAG | 层次化 Task 委托 | 无中央编排 |
| **记忆** | SQLite + sqlite-vec + FTS5 + Dream | Session DB (SQLite) | SQLite + FTS5 + 9 Provider |
| **沙箱** | Bubblewrap/Seatbelt/Noop | 无 | 无 |
| **通道** | 12 平台 | CLI/TUI only | 30 平台适配器 |
| **会话持久化** | SQLModel (SQLite) | Drizzle (SQLite) | SQLite (FTS5) |
| **MCP** | 客户端 + 服务器 | 客户端 only | 客户端 |
| **技能/插件** | 62 内置 + ClawHub 市场 | Skill 文件 + 内建 | 90 内置 + 86 可选 |
| **差异定位** | Token 节约 + 个人助手 | IDE 编码 Agent | 全能自主助手 |

---

## 关键设计亮点

1. **8 阶段 TurnRunner** — 每个阶段自包含、可独立测试，通过 frozen dataclass 交换数据。这是目前调研的 Agent 中最结构化的 Turn 分解方案。

2. **本地 ONNX 模型路由** — Prompt 不离开本机做路由决策，4 层分类 + 智能保护（投诉检测、反降级、大上下文底线），PinchBench 验证成本降低 9 倍。

3. **Meta-Skill DAG** — SOP 文本编译为 DAG，6 种步骤类型，Jinja 模板，并行执行，`MetaPaused` 支持任何前端渲染澄清表单。这是 Agent 编排的一种创新模式。

4. **10 步 Tool 调度** — 策略链 + 预算治理 + Hook 扇出，每步都有明确的边界和错误处理。

5. **两层预算系统** — 结果预算（输出大小）+ 运行预算（调用次数），在 Tool 分发时应用，防止上下文膨胀。

6. **Dream 记忆整合** — 后台记忆巩固系统，包括候选选择、隔离、排名、回执跟踪。安全第一（默认关闭）。

7. **通道能力矩阵** — 27 个能力标签 + 风险分层 + 权限矩阵，每个通道声明能力等级（GREEN/YELLOW/RED）。

---

## 对 Nop 的设计参考价值

### 可借鉴的设计模式

| 模式 | OpenSquilla 实现 | Nop 可借鉴方向 |
|------|----------------|--------------|
| **Turn 分解** | 8 阶段管道 + frozen dataclass | Nop Agent 的 Turn 可以拆分为输入/路由/Provider/Tool/持久化等阶段 |
| **本地模型路由** | ONNX 分类器 + 4 层 | Nop 可用 Java 版 ONNX Runtime 实现类似的 Token 节约路由 |
| **Tool 预算治理** | 结果预算 + 运行预算双层 | Nop 的 ToolDispatch 可以加入类似的预算控制 |
| **Meta-Skill DAG** | SOP → DAG 编译 + 6 步类型 | Nop 的 `nop-task` 可借鉴 DAG 编排模式 |
| **Prompt 注入防护** | 4 类威胁 + `<untrusted>` 包裹 | Nop 的 Agent 安全层可参考此分类 |
| **通道能力矩阵** | 27 标签 + GREEN/YELLOW/RED | Nop 的多通道接入可参考此声明式能力模型 |
| **5 层纵深防御** | Injection Guard → Tool Tier → Sandbox Governance → Sensitive Paths → Dispatch Guard | Nop Agent 应建立类似的多层安全架构 |
| **Denial Ledger + 阈值暂停** | per-session 拒绝计数 + 自动暂停 autonomous 模式 | Nop 的 Agent 安全层应包含拒绝计数和自动暂停机制 |
| **Post-denial 守卫** | 被拒后只允许 3 种合法 follow-up (lower_privilege/explain/narrower_approval) | Nop Agent 的 Tool 重试逻辑应加入类似的约束 |
| **Stale Output Cache** | 拒绝后 purge 之前的成功输出缓存 | Nop Agent 应在拒绝操作后清除对应的缓存输出 |
| **SecurityLevel 分级** | L0~L3 四级 + LevelHints 输入 + 确定性规则表 | Nop 可定义 `sandbox.xdef` schema，用 XDSL 配置化规则表 |
| **SandboxPolicy 物化** | level → mounts + network + limits + approval 的完整策略 | Nop 可用 BizModel BizAction 映射为完整策略对象 |

### Safety/Guardrail 对 Nop 的具体映射建议

Nop 的风格是 XDSL 模型驱动，不用 Java 注解。Safety 层应通过 XDSL 配置 + 复用已有模块实现：

```
Nop Agent Safety Architecture (建议)

外部内容 (Web fetch / API / 用户输入)
  │
  ├─ InjectionGuard (XLang XPL 实现)
  │   ├─ 4 类正则威胁检测 (同 OpenSquilla 分类)
  │   ├─ wrapUntrusted() XML 信封
  │   └─ extractToolCallRefusalReason() origin trace 检查
  │
  ├─ ToolDispatch 管线
  │   ├─ NopToolTier: SAFE / CONFIRM / ADMIN_ONLY
  │   │   ├─ 在 *.xmeta 中声明 riskTier 属性
  │   │   └─ 硬编码 ADMIN_ONLY: shell, file_write, edit, git_push
  │   ├─ PermissionMatrix: channel → allowed tiers
  │   │   └─ 复用 nop-auth 角色体系 (operator / user)
  │   └─ DispatchInjectionGuard: origin trace 检查
  │
  ├─ SensitivePathGuard
  │   ├─ 敏感路径 denylist: 外部配置文件 (支持 Delta 覆盖)
  │   ├─ workspace 感知排除
  │   └─ 返回结构化 DenialResult
  │
  ├─ SandboxGovernance (XDSL 模型驱动)
  │   ├─ SecurityLevel: L0~L3 — 定义在 XDSL schema (sandbox.xdef)
  │   ├─ LevelHints: trustedSource, needsNetwork, writesOutsideWorkspace, ...
  │   ├─ selectLevel: 确定性规则表 — 配置化 (XDSL)，非硬编码
  │   ├─ buildPolicy: level → SandboxPolicy 物化
  │   ├─ DenialLedger: per-session 拒绝计数 (持久化到 DB)
  │   │   └─ threshold → 自动暂停 autonomous
  │   ├─ PostDenialGuard: 只允许 LOWER_PRIVILEGE / EXPLAIN / NARROWER_APPROVAL
  │   ├─ ApprovalGate: 复用 nop-wf 工作流引擎
  │   └─ StaleOutputCache: 拒绝后清除
  │
  └─ 沙箱执行 (服务器端)
      ├─ Docker 容器隔离 (推荐)
      └─ 资源限制: cpu, memory, wall_time
```

### 不适用 Nop 的部分

| 特征 | 原因 |
|------|------|
| Python + asyncio 架构 | Nop 是 Java 21 |
| Starlette ASGI | Nop 用自己的 HTTP 服务器 |
| sqlite-vec 向量搜索 | Nop 用 MySQL/PG + 外部向量索引 |
| Bubblewrap/Seatbelt 进程沙箱 | Nop 部署在服务器端，应使用 Docker/容器隔离 |
| 单用户个人助手定位 | Nop 是企业平台，多租户——需要 tenant-aware DenialLedger |
| macOS/Windows 桌面沙箱 | Nop 服务器端不需要 |

### 潜在风险

- OpenSquilla 尚在 Alpha 阶段（`Development Status :: 3 - Alpha`），API 可能不稳定
- Agent 核心循环 `agent.py` 单文件 5,770 行，维护负担较大
- 沙箱后端不完整（macOS 仅渲染、Windows 无后端）
- 记忆 Dream 系统默认关闭，实际效果未验证
- DenialLedger 仅内存持久化，进程重启后丢失
- StaleOutputCache 无法清除 LLM in-context memory（文档明确承认的局限）

---

## Open Questions

- [ ] SquillaRouter 的 ONNX 模型训练数据集是什么？准确率如何？
- [ ] Meta-Skill DAG 编排在生产环境的实际使用情况？
- [ ] 子 Agent 的深度限制是多少？性能表现如何？
- [ ] 上下文压缩对长期会话的效果如何？信息丢失率？
- [ ] 62 个内置技能的实际覆盖率和质量？

## References

- `~/ai/opensquilla/` — OpenSquilla 完整源码
- `~/ai/opensquilla/src/opensquilla/engine/` — Agent 核心引擎
- `~/ai/opensquilla/src/opensquilla/squilla_router/` — 本地模型路由
- `~/ai/opensquilla/src/opensquilla/skills/meta/` — Meta-Skill DAG 编排
- `~/ai/opensquilla/src/opensquilla/tools/dispatch.py` — Tool 调度管线
- `~/ai/opensquilla/src/opensquilla/memory/` — 记忆系统
- `~/ai/opensquilla/opensquilla.toml.example` — 配置 Schema
- `~/ai/opensquilla/docs/` — 产品文档

> **注**：`~/ai/` 下的文件是调研时在宿主机器上的源码位置，不在 nop-entropy 仓库内。
