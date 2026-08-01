# Grok-Build 确定性重放与多域 Checkpoint 深度分析 & Nop AI Agent 生命周期/恢复

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/grok-build`（xAI grok CLI/TUI，Rust）vs `nop-ai-agent`（hook 15 生命周期点 + checkpoint）
> Conclusion:

## 一、总览

**Grok-Build**（xAI 出品的 Rust 终端 coding agent）是本批调研中与 nop-ai-agent 设计**最同构**的项目——15 个 hook 生命周期事件（带 `gate` 语义 Observe/Tool/Stop）、Journal 确定性重放（seq+req_hash+发散检测）、多域 rewind checkpoint（FS/git/hunk 扇出 + truncate）。

| 维度 | grok-build | nop-ai-agent |
|------|-----------|--------------|
| Hook 生命周期 | 15 事件 + GateKind(Observe/Tool/Stop) | 15 生命周期点 + middleware 洋葱链 |
| 确定性 | Journal(seq,kind,req_hash,result) 重放 + Divergence | 仅 messageCount 校验 |
| Checkpoint | 多域(FS/git/hunk)扇出 + checkpoint-<n>.json + truncate | DBCheckpointManager 单行 |
| 脚本宿主 | Rhai VM（单线程） | DSL-first（XDEF） |

## 二、核心机制

### 2.1 15 Hook 生命周期 + GateKind（`crates/codegen/xai-grok-hooks/src/event.rs:89-187`）
- 事件：SessionStart/UserPromptSubmit/PreToolUse/PostToolUse/PostToolUseFailure/PermissionDenied/Stop/StopFailure/Notification/SubagentStart/Stop/End/PreCompact/PostCompact/SessionEnd。
- 每事件 `traits(gate, matcher, hub)`；GateKind 分 **Observe（仅观察）/ Tool（工具链）/ Stop（终止链）**。
- dispatcher 顺序执行，仅 deny 中断链，失败 fail-open（`dispatcher.rs`）。

### 2.2 Journal 确定性重放（`crates/codegen/xai-workflow/src/journal.rs:11-44`、`engine.rs:42`）
- 每次 host-call 记 `JournalEntry(seq, kind, req_hash, result)`；重放按 `req_hash` 比对，发散即 `Divergence` 报错。
- 解决脚本 VM 非确定性：内容哈希保证可重放。

### 2.3 多域 Checkpoint 扇出（`session/checkpoint.rs:1-36`、`checkpoint_store.rs:44-160`、`git.rs:1997`）
- 按 prompt_index 键，跨 **文件/git-HEAD/hunk** 多域捕获 `RewindCheckpoint`。
- 磁盘 `checkpoint-<n>.json` + 内存缓存 + truncate 清理。

## 三、对 nop-ai-agent 的借鉴要点

1. **GateKind 三态语义**——给 nop 的 hook 增加显式分类：Observe 类（日志/指标，不可拦截）、Tool 类（安全/审批，可拦截）、Stop 类（完成校验，可阻断）。当前 nop 的 15 点是隐式区分，显式化后配置更清晰。
2. **Journal 确定性重放**（最高价值）——nop checkpoint 现在只校验 messageCount；增加 `req_hash`（工具名+参数+输入指纹）后，restore 时可检测非确定性发散并降级。与 hatchet 的 idempotency_key（`2026-08-01-hatchet-durable-execution-analysis.md`）互补：hatchet 是 entry 级去重，grok 是序列级发散检测。
3. **多域 checkpoint 扇出**——nop 当前 checkpoint 仅消息级；借鉴其按"域"（对话状态/工具副作用/git 状态）分别捕获，回滚时按域一致。
4. **contributor data-only/能力注入分离**——对应 nop middleware 洋葱链中"观察者不持有循环控制"的安全边界。

## 四、结论

grok-build 与 nop-ai-agent 在生命周期/checkpoint 设计上近乎逐项对应，是落地范式参考。最高优先级借鉴：**Journal 确定性重放**（req_hash 发散检测）+ **GateKind 显式三态**。局限：Rhai VM 绑定、checkpoint 强依赖 git/FS 语义，泛化需抽象。

## References
- `~/ai/grok-build/crates/codegen/xai-grok-hooks/src/{event.rs,dispatcher.rs}`、`xai-workflow/src/{journal.rs,engine.rs}`、`session/{checkpoint.rs,checkpoint_store.rs,git.rs}`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-hook.md`、`nop-ai-agent-reliability.md`
- `ai-dev/analysis/agent-survey/2026-08-01-hatchet-durable-execution-analysis.md`
