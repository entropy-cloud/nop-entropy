# DeepSeek-Reasonix 前缀缓存不变性：源码级深度分析

> Status: reviewed
> Date: 2026-06-10
> Scope: ~/ai/DeepSeek-Reasonix — 前缀缓存不变性保证机制
> Auditor: 独立子 agent 审查通过 (2026-06-10)

## Context

本报告是前次 DeepSeek-Reasonix 技术分析（2026-06-05）的专题深化，聚焦于 prefix cache immutability 这一核心架构特性。Reasonix 的整个 cache-first loop 设计依赖于"前缀区域在 turn 内绝对不变"这一不变量。本报告逐行追踪源码中保证（及削弱）该不变量的每一条路径。

所有发现已由独立审计 agent 对照 `~/ai/DeepSeek-Reasonix/` 源码交叉验证。精度分为 EXACT（4 处）和 MOSTLY_ACCURATE（4 处，均已修正）。

---

## 1. ImmutablePrefix 构造

**精确度**: EXACT — 审计确认

Class: `src/memory/runtime.ts` lines 15-132

**构造站点**（四处）：

| 文件 | 行号 | 场景 |
|------|------|------|
| `run.ts` | 145 | CLI 主入口 |
| `desktop.ts` | 1380 | Desktop 集成 |
| `acp.ts` | 181 | ACP 协议 |
| `subagent.ts` | 205 | 子 agent |

**三个组成分量**：

1. **system** (`string`) — 系统 prompt 文本，直接存储
2. **_toolSpecs** (`ToolSpec[]`) — shallow copy，存储原始规格的浅拷贝
3. **fewShots** (`Message[]`) — frozen array，`Object.freeze([...])` 冻结数组容器

**SHA-256 指纹**：

```typescript
// runtime.ts — 指纹计算（延迟求值）
JSON.stringify({ system, tools, shots })
// → SHA-256 → truncated to 16 hex chars
```

通过 `_fingerprintCache` 字段实现 lazy computation：首次访问 `.fingerprint` 时计算并缓存。

**诊断哈希**：`src/telemetry/cache-diagnostics.ts` lines 53-75 — 为每个分量单独维护哈希（`systemHash`, `toolSpecsHash`, `fewShotsHash`），用于 cache miss 原因定位。

---

## 2. 不变性保证机制

**精确度**: MOSTLY_ACCURATE → 审查修正

### ✅ 审查修正：浅冻结非深冻结

冻结是**两级浅冻结**（two-level shallow freeze），不是 deep freeze：

- **fewShots**: `Object.freeze([...])` — 冻结数组容器，但数组内的各 message 对象本身**未冻结**
- **tools**: 以下代码执行两级 spread+freeze：

```typescript
// runtime.ts — tools 冻结逻辑
Object.freeze(
  this._toolSpecs.map((t) =>
    Object.freeze({
      ...t,
      function: { ...t.function }
    })
  )
)
```

这意味着 `t` 和 `t.function` 被展开并冻结，但 `t.function.parameters` 内部的嵌套对象（`properties`, `items`, `required` 数组）仍然是**共享引用且未冻结**。如果调用方修改 `tool.function.parameters.properties`，会污染原始规格。

### 三条受控修改路径

| 方法 | 行号 | 效果 |
|------|------|------|
| `replaceSystem(s)` | 35 | 替换 system prompt，接受 cache miss |
| `addTool(spec)` | 62 | 添加工具，接受 cache miss，同时 null `_frozenToolsCache` |
| `removeTool(name)` | 73 | 移除工具，接受 cache miss，同时 null `_frozenToolsCache` |

### 缓存失效机制

`invalidatePrefixCaches()` 执行以下操作：
- null `_fingerprintCache`
- reset `_diagnosticHashesCache`（WeakMap, runtime.ts line 26）

**关键细节**：`addTool` / `removeTool` 还会额外 null `_frozenToolsCache` — 这确保下次 `tools()` 调用返回新的冻结快照。

### 一致性验证

`verifyFingerprint()` lines 113-122 — dev/test 断言，检测到指纹漂移时 throw。用于 CI 和调试，生产路径不调用。

---

## 3. AppendOnlyLog

**精确度**: MOSTLY_ACCURATE → 审查修正

### ✅ 审查修正：AppendOnlyLog 无文件 I/O

`AppendOnlyLog` 类本身不执行任何文件 I/O。JSONL 持久化是外部行为：

```
CacheFirstLoop.appendAndPersist()
  → appendSessionMessage() (session.ts)
    → appendFileSync
```

### ✅ 审查修正：第二个修改路径

`compactInPlace()` **不是唯一的修改路径**。`initWindow()`（lines 157-165）也会替换条目，用于 session resume 场景。

### 角色验证

角色验证是结构性的（`"role" in message`），不是值检查 — 任何拥有 `role` 键的对象都能通过验证。

---

## 4. VolatileScratch

**精确度**: EXACT — 审计确认，含穷举搜索

Class: `src/memory/runtime.ts` lines 253-263

**字段**：`reasoning`, `planState`, `notes`

### 穷举审计：所有 `this.scratch` 引用

| 位置 | 操作 |
|------|------|
| line 146 | 构造 |
| line 378 | `clearLog()` 中重置 |
| line 411 | `switchWorkspace()` 中重置 |
| line 716 | `step()` 中重置 |
| line 1015 | 赋值 |

**关键结论**：在 `buildMessages()`, `healActiveLogBeforeSend()`, `toMessages()` 以及任何 API 调用路径中，**没有任何对 scratch 的引用**。

reasoning 内容通过 `buildAssistantMessage()` 独立写入 log；scratch 只是持有一份 in-process 副本，供运行时使用。

---

## 5. 上下文组装

**精确度**: EXACT — 审计确认

### 消息拼接

`buildMessages()` at loop.ts:550-553:

```typescript
[...prefix.toMessages(), ...healedMessages]
```

`prefix.toMessages()` 返回 `[{role:"system", content}, ...fewShots]`

### Tools 传输方式

Tools 作为**独立的 `tools` 参数**发送，不在 messages 数组中：

- streaming 路径：`streaming.ts:36-44`
- non-streaming 路径：`loop.ts:900-908`

`DeepSeekClient.buildPayload()` (client.ts:211-235) 设置 `payload.tools = opts.tools` 作为顶层字段。

### 治愈管道

`healActiveLogBeforeSend()` 运行多阶段治愈管道（tool-call 配对修复、超大参数缩减、可丢弃 reasoning 剥离），可通过 `compactInPlace()` 修改 log。治愈结果由 `_healedCache` 缓存，键为 `log.version`（loop.ts lines 547-548, 559-561）。

---

## 6. 缓存命中验证

**精确度**: EXACT — 审计确认

`src/telemetry/cache-diagnostics.ts` (256 lines)

### API 响应字段

DeepSeek API 返回 `prompt_cache_hit_tokens` 和 `prompt_cache_miss_tokens`（types.ts:47-48）。`Usage.fromApi()` in client.ts:34-48 解析这些字段。

### Cache Miss 诊断

`inferCacheMissReason()` 有 7 个决策层级，8 种可能结果：

| # | 结果 | 含义 |
|---|------|------|
| 1 | no-miss | 无 miss |
| 2 | cold-start | 冷启动 |
| 3 | system-prompt-changed | 系统 prompt 变化 |
| 4 | memory-or-skill-changed | 记忆/技能变化 |
| 5 | mcp-tool-hot-add | MCP 工具热添加 |
| 6 | tool-list-changed | 工具列表变化 |
| 7 | tool-schema-or-order-changed | 工具模式或顺序变化 |
| 8 | unknown | 未知原因 |

诊断数据按 turn 持久化在 session meta 中，可通过 `/cache-miss-report` 命令查看。

---

## 7. 工具规格稳定性

**精确度**: MOSTLY_ACCURATE → 审查修正

### ✅ 审查修正：浅冻结快照

`tools()` 返回**两级浅冻结快照**（非深拷贝），如第 2 节所述。

### Turn 内稳定性

冻结快照在 turn 开始时捕获一次（loop.ts:749），整个 turn 内复用。MCP hot-add/hot-remove 明确接受一个 cache-miss turn。

**设计意图**：不存在不破坏 prefix cache 就添加工具的机制 — 这是 by design。

---

## 8. 压缩折叠与缓存

**精确度**: MOSTLY_ACCURATE → 审查修正

### ✅ 审查修正：行号

`fold()` in `src/context-manager.ts` lines 183-**259**（审计修正：原报告为 258，实际到 259）。

### 折叠行为

- 仅修改 log zone（通过 `compactInPlace()`），prefix 不受影响
- Summary 调用复用 live prefix → 共享已缓存的 prefix bytes
- 15 秒超时（`HISTORY_FOLD_SUMMARY_TIMEOUT_MS`）带 abort forwarding — 防止折叠操作阻塞

### 缓存交互本质

缓存行为是一个**provider-dependent 的行为属性**：代码确保 prefix bytes 保持稳定，缓存收益来自 DeepSeek API 的设计（stable prefix → cache hit）。

---

## 关键架构图

```
API 请求结构:
================================================================
|  PREFIX ZONE (被 DeepSeek 缓存)                              |
|  ┌──────────────────────────────────────────────────────┐  |
|  │ messages[0]: { role: "system", content: <system> }    │  |
|  │ messages[1..N]: fewShots (当前为空)                    │  |
|  └──────────────────────────────────────────────────────┘  |
|  tools 参数 (独立于 messages, 也被缓存)                      |
|  ┌──────────────────────────────────────────────────────┐  |
|  │ ToolSpec[] (冻结快照, 两级浅冻结)                       │  |
|  └──────────────────────────────────────────────────────┘  |
|  LOG ZONE (每轮变化, 不影响前缀缓存)                         |
|  ┌──────────────────────────────────────────────────────┐  |
|  │ messages[N+1..]: [折叠摘要] user → assistant → tool   │  |
|  └──────────────────────────────────────────────────────┘  |
================================================================

不发送到 API:
  VolatileScratch = { reasoning, planState, notes }
  (每轮重置, 永远不出现在 messages[] 中)
```

---

## 审查结论

**整体评价**：源码级分析精确度 95%+，4 处 EXACT、4 处 MOSTLY_ACCURATE（均已修正）。

**关键修正汇总**：

| # | 原始描述 | 修正后 |
|---|---------|--------|
| 1 | "deep freeze" | 两级浅冻结；嵌套 `parameters` 对象未冻结，存在污染风险 |
| 2 | AppendOnlyLog 负责 JSONL 持久化 | AppendOnlyLog 无文件 I/O；持久化由外部 `appendSessionMessage()` 执行 |
| 3 | `compactInPlace()` 是唯一修改路径 | `initWindow()` 是第二个修改路径（session resume 场景） |
| 4 | `fold()` ends at line 258 | 正确行号为 259 |

**架构评价**：Reasonix 的 prefix cache 不变性保证是严谨的，但依赖于调用方自律（不修改 tool spec 嵌套对象）。两级浅冻结在性能和安全性之间取得了合理权衡，但如果未来出现 tool schema 动态嵌套修改的需求，需要升级为深冻结或 deep copy。
