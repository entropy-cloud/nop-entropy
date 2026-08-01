# Grok-Build 确定性重放与多域 Checkpoint 深度分析 & Nop AI Agent 生命周期/恢复

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/grok-build`（xAI grok CLI/TUI，Rust，~3003 文件）vs `nop-ai-agent`（hook 12 生命周期点 + checkpoint append-only）
> Conclusion:

## 一、总览

**Grok-Build**（xAI 出品的 Rust 终端 coding agent）是本批调研中与 nop-ai-agent 设计**最同构**的项目——15 个 hook 生命周期事件（带 `gate` 语义 Observe/Tool/Stop）、Journal 确定性重放（seq+req_hash+发散检测）、多域 rewind checkpoint（FS/git/hunk 扇出 + truncate）。

| 维度 | grok-build | nop-ai-agent |
|------|-----------|--------------|
| Hook 生命周期 | 15 事件 + GateKind(Observe/Tool/Stop) | 12 生命周期点（AgentLifecyclePoint）+ middleware 洋葱链 |
| 确定性 | Journal(seq,kind,req_hash,result) 重放 + Divergence | 仅 messageCount 校验 |
| Checkpoint | 多域(FS/git/hunk)扇出 + checkpoint-<n>.json + truncate | DBCheckpointManager（append-only INSERT 多行，按 watermark 检索） |
| 脚本宿主 | Rhai VM（单线程） | DSL-first（XDEF） |
| agent-lifecycle | contributor data-only/能力注入分离 | middleware 洋葱链 |

## 二、核心机制详解

### 2.1 15 Hook 生命周期 + GateKind（`crates/codegen/xai-grok-hooks/src/event.rs:89-187`）
- 15 个事件：SessionStart / UserPromptSubmit / PreToolUse / PostToolUse / PostToolUseFailure / PermissionDenied / Stop / StopFailure / Notification / SubagentStart / SubagentStop / SubagentEnd / PreCompact / PostCompact / SessionEnd。
- 每事件带 `traits(gate, matcher, hub)`。
- **GateKind 三态**：**Observe**（仅观察，不可拦截——日志/指标）、**Tool**（工具调用链，可拦截——安全/审批）、**Stop**（终止链，可阻断——完成校验）。
- dispatcher 顺序执行 hook，仅 deny 中断链，失败 fail-open（`dispatcher.rs`）。

### 2.2 Journal 确定性重放（`crates/codegen/xai-workflow/src/journal.rs:11-44`、`engine.rs:42`）
- 工作流用 Rhai 脚本 VM（单线程）。
- 每次 host-call 记 `JournalEntry(seq, kind, req_hash, result)`：
  - `seq`：调用序号（单调递增）
  - `kind`：调用类型
  - `req_hash`：请求内容的哈希指纹
  - `result`：调用结果
- 重放时按 `req_hash` 比对：若同一 seq 位置的 hash 不一致 → 报 `Divergence`（发散检测）。
- 解决脚本 VM 非确定性：内容哈希保证可重放性，发散即停止。

### 2.3 多域 Checkpoint 扇出（`session/checkpoint.rs:1-36`、`checkpoint_store.rs:44-160`、`git.rs:1997`）
- 按 `prompt_index` 键，跨**三个域**分别捕获 `RewindCheckpoint`：
  - **FS 域**：文件系统变更
  - **git-HEAD 域**：git HEAD 引用
  - **hunk 域**：文件 hunk（补丁块）级别变更
- 磁盘落地：`checkpoint-<n>.json` + 内存缓存 + truncate（旧 checkpoint 清理）。
- GitCheckpointStore（`git.rs:1997`）：git 操作专用的 checkpoint 存储。

### 2.4 Agent-Lifecycle Contributor（`crates/codegen/xai-agent-lifecycle/src/lib.rs:1-3`）
- session/turn/command 三级 lifecycle 钩子。
- **contributor 只收 data-only 输入、能力注入，不持有循环控制**——观察者不控循环的安全边界。

## 三、对 nop-ai-agent 的借鉴要点

1. **GateKind 三态语义**（高价值）——给 nop 的 12 个 hook（AgentLifecyclePoint）增加显式分类：Observe 类（日志/指标，不可拦截）、Tool 类（安全/审批，可拦截）、Stop 类（完成校验，可阻断）。当前 nop 的 12 点是隐式区分，显式化后配置更清晰。与 parlant 的 CALL_NEXT/RESOLVE/BAIL（`2026-08-01-parlant-conversation-control-analysis.md`）是两种互补的拦截语义分类法。
2. **Journal 确定性重放**（最高价值）——nop checkpoint 当前只校验 messageCount；增加 `req_hash`（工具名+参数+输入指纹）后，restore 时可检测非确定性发散并降级。具体：checkpoint 行增加 `idempotency_key` 列（hash(toolName + callId + 输入指纹)），restore 时同水位 key 不一致 → 拒绝该 checkpoint，降级为 session 重放。与 hatchet 的 idempotency_key（`2026-08-01-hatchet-durable-execution-analysis.md`）互补：hatchet 是 entry 级去重，grok 是序列级发散检测。
3. **多域 checkpoint 扇出**（中价值）——nop 当前 checkpoint 仅消息级；借鉴其按"域"（对话状态/工具副作用/git 状态）分别捕获，回滚时按域一致。nop 的通用 agent 场景可抽象为"对话域 + 工具副作用域"两域。
4. **contributor data-only/能力注入分离**（中价值）——对应 nop middleware 洋葱链中"观察者不持有循环控制"的安全边界（Observe 类 hook 不能阻断循环）。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **Journal 确定性重放**（`xai-workflow/src/journal.rs:11-44`）：`JournalEntry(seq, kind, req_hash, result)` + 重放时按 `req_hash` 比对，发散报 `Divergence`——**重试的安全性保证**（重放可检测非确定性）。
- **多域 rewind checkpoint**（`session/checkpoint.rs:1-36`）：FS/git/hunk 三域扇出 + `checkpoint-<n>.json` + truncate——按域回滚重试。
- **Stop hook 失败处理**：Stop/StopFailure 事件——agent 想停止时钩子可阻止（完成校验门，失败则继续重试）。
- **对 nop 的启示**：req_hash 发散检测是 nop checkpoint 重试的安全网（与 hatchet idempotency_key 互补）；多域回滚对应 nop 的消息级 + 工具副作用级双域。

## 四、优缺点

### 优点
1. 15 个 hook 生命周期事件 + GateKind 三态是完整的拦截分类法。
2. Journal 确定性重放（req_hash 发散检测）解决了脚本 VM 非确定性问题。
3. 多域 checkpoint 扇出（FS/git/hunk）支持精确回滚。
4. contributor data-only 分离了观察与控制。

### 缺点
1. 工作流绑定 Rhai（单线程 VM），迁移到 Java DSL 需重写脚本宿主。
2. checkpoint 强依赖 git/文件系统语义，泛化到非代码场景需抽象。
3. 仓库为 monorepo 切片（SOURCE_REV 8d69c91），部分 crate 闭包庞大、边界需自行甄别。

## 五、结论

grok-build 与 nop-ai-agent 在生命周期/checkpoint 设计上近乎逐项对应，是落地范式参考。最高优先级借鉴：**Journal 确定性重放**（req_hash 发散检测，与 hatchet idempotency_key 互补）+ **GateKind 显式三态**（Observe/Tool/Stop 分类）。

## Open Questions
- [ ] nop 的 req_hash 计算范围（仅工具参数 vs 含上下文指纹）？
- [ ] GateKind 三态在 nop DSL 中如何声明（XDEF 属性 vs 注解）？
- [ ] 多域 checkpoint 的"工具副作用域"在 nop 中如何捕获（工具自报告 vs 引擎拦截）？

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D1**（15 hook 事件+GateKind 三态）、**D4**（Journal 确定性重放+多域 checkpoint）、**D11**（contributor data-only）、**D12**（req_hash 发散检测）。缺失/薄弱：D2、D6。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **hook 生命周期**：nop 12 个 AgentLifecyclePoint + middleware 洋葱链（可拦截/仅通知双轨）——grok-build 15 事件仅 3 类 GateKind 分类；nop 的 12 点 + 双轨设计更完整。
- **checkpoint 持久化**：nop `DBCheckpointManager` append-only INSERT（多行 + watermark 检索 + Journal 双写）——grok-build 的 checkpoint-<n>.json + truncate 是文件覆盖写，nop 更健壮。
- **并发模型**：nop 是 Java 21 多线程 + 引擎分离，grok-build 绑 Rhai 单线程 VM——nop 更通用。

**必要参考的增量（以超越方式吸收）**：
- **Journal 确定性重放（req_hash 发散检测）**：nop checkpoint 只校验 messageCount——增加 `idempotency_key`（hash(toolName+callId+输入指纹)）是真正增量（restore 时检测非确定性发散），以 nop 表列实现。
- **多域 checkpoint 扇出**（FS/git/hunk）：nop 可抽象为"对话域 + 工具副作用域"双域（nop 通用场景），作为增强。

**总评**：nop-ai-agent 在 hook/持久化/并发上**全面超越**（grok-build 15 事件 vs nop 12 点双轨更细）；req_hash 发散检测一个增量值得吸收（与 hatchet idempotency_key 同源，nop 统一实现）。

## References
- `~/ai/grok-build/crates/codegen/xai-grok-hooks/src/{event.rs:89-187,dispatcher.rs}`、`xai-workflow/src/{journal.rs:11-44,engine.rs:42}`、`session/{checkpoint.rs:1-36,checkpoint_store.rs:44-160,git.rs:1997}`、`xai-agent-lifecycle/src/lib.rs:1-3`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-hook-skill-engine.md`、`nop-ai-agent-reliability.md`
- `ai-dev/analysis/agent-survey/2026-08-01-hatchet-durable-execution-analysis.md`、`2026-08-01-parlant-conversation-control-analysis.md`
