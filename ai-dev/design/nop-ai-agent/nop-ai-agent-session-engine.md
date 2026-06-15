# Nop AI Session Engine

## 1. 目标

本篇定义 Session 在 Java 引擎层中的运行时设计。

## 2. Session 引擎职责

Session 引擎负责：

1. 加载会话状态
2. 为一次执行构造会话态上下文
3. 在执行后回写会话状态
4. 管理快照、分叉和压缩前后状态切换

## 3. 推荐对象

- `SessionManager`
- `SessionLoader`
- `SessionWriter`
- `SessionForkService`
- `SessionCompressionCoordinator`

## 4. Session 与执行上下文的关系

- Session 是持久化状态源
- `AgentExecutionContext` 是一次执行的内存态工作集

## 5. 关键运行时流程

### 5.1 加载

1. 根据 `sessionId` 通过 VFS 加载 session（如快照不存在或过期，从 Event Log 按 session-and-storage.md §5.3 重建）
2. 从 `session_header` 获取 `planId`，加载对应 plan（独立存储，见 session-and-storage.md §6）
3. 构造内存态对象

### 5.2 分叉

1. 创建快照
2. 生成子 session
3. 写入父引用

### 5.3 压缩

1. 检查阈值
2. 生成快照
3. 更新活动消息集
4. 回写压缩记录

## 6. 关键接口

### 6.1 ISessionManager

```
ISessionManager:
  Session loadSession(sessionId, VFS)
  void saveSession(sessionId, Session, VFS)
  Session forkSession(parentSessionId, forkLabel)
  CompactionResult compactSession(sessionId, CompactionRequest)
  SessionSnapshot createSnapshot(sessionId)
```

### 6.2 ISessionStore（低层存储抽象）

`ISessionStore` 是 Session 持久化存储的低层接口，`ISessionManager` 在其基础上实现业务逻辑。

**Phase 1 接口**：基础 CRUD（`getOrCreate`, `get`, `remove`, `getAll`），`InMemorySessionStore` 实现。

**Phase 2 扩展策略**：直接在 `ISessionStore` 上加 default 方法（抛 `UnsupportedOperationException`），不引入子接口：

```java
public interface ISessionStore {
    // Phase 1: 基础 CRUD
    AgentSession getOrCreate(String sessionId, String agentName);
    AgentSession get(String sessionId);
    void remove(String sessionId);
    Collection<AgentSession> getAll();

    // Phase 1 (default UOE): 为 Phase 2 预留的扩展点
    default String forkSession(String parentSessionId, boolean inheritContext, Map<String, Object> props) {
        throw new UnsupportedOperationException("forkSession requires VfsSessionStore");
    }
    default long appendEvent(String sessionId, VfsEvent event) {
        throw new UnsupportedOperationException("appendEvent requires VfsSessionStore");
    }
    default CompactionResult compact(String sessionId, CompactConfig config) {
        throw new UnsupportedOperationException("compact requires VfsSessionStore");
    }
    default SessionSnapshot loadSnapshot(String sessionId, String snapshotId) {
        throw new UnsupportedOperationException("loadSnapshot requires VfsSessionStore");
    }
    default void setPlanRef(String sessionId, String planId) {
        throw new UnsupportedOperationException("setPlanRef requires VfsSessionStore");
    }
}
```

**设计决策**：选 default 方法抛 `UnsupportedOperationException`，而非新增 `IVfsSessionStore extends ISessionStore`。理由：
1. 单接口，消费者只需依赖一个类型，无需 `instanceof` 或双接口感知
2. Phase 1 的 `InMemorySessionStore` 覆盖了 `forkSession`（4 原始方法 + 1 具体覆盖，其余 4 个 default 自然继承 UOE）；`forkSession` 已在 `InMemorySessionStore` 中功能化实现
3. Phase 2 的 `VfsEventLogSessionStore` 覆盖全部 9 个方法，消费者无感升级
4. 抛 UOE 确保 Phase 2 代码误用尚未实现的 default 方法时立刻失败，而非静默忽略

### 6.3 IAiMemoryStore 与 Budgeted Injection（MiMoCode 吸收）

`IAiMemoryStore` 是 Agent 的 per-session 记忆存储接口。接口方法**不含 sessionId 参数**——每个实例代表一个 session 的记忆。per-session 隔离由 `IMemoryStoreProvider.getOrCreate(sessionId)` 解析（plan 189 Phase 1）。

**实现状态**：
- **接口（8 方法）✅**：L1-16 已落地 4 个抽象（`getAll`/`getLastN`/`search`/`add`）+ 4 个 default UOE（`readBudgeted`/`update`/`remove`/`batchAdd`）。接口 default 上的 UOE 保留作为其他实现的 fail-fast 选择
- **`InMemoryAiMemoryStore`（功能化 in-memory 实现）✅**（plan 189 Phase 1）：覆盖全部 8 方法（含 4 个 Phase 2 default 的真实逻辑，无 UOE 残留），线程安全（`ConcurrentHashMap` + `synchronized` 复合操作）。`search` 为 content/type/key 子串匹配（in-memory 无向量检索，L4-3 scope）
- **`IMemoryStoreProvider` + `InMemoryMemoryStoreProvider` ✅**（plan 189 Phase 1）：Provider 模式，`ConcurrentHashMap.computeIfAbsent` 原子创建 per-session store。`DefaultAgentEngine` 默认持有 `InMemoryMemoryStoreProvider`（开箱即用，同 `InMemorySessionStore` 一致）
- **分发循环接线 ✅**（plan 189 Phase 1）：`ReActAgentExecutor` 分发循环在构造 `AgentToolExecuteContext` 时，经 `provider.getOrCreate(sessionId)` 解析当前 session 的 store 注入 context。Working-memory 工具（read-memory / write-memory / search-memory，plan 189 Phase 2）从 `AgentToolExecuteContext.getMemoryStore()` 读取。Provider 为 null 时 context 中 store = null，工具 fail-fast（同 SendMessageExecutor 的 NoOp messenger 模式）

**Budgeted Injection 算法**（来自 MiMoCode `readBudgeted()`，已功能化实现于 `InMemoryAiMemoryStore.readBudgeted`）：

```
readBudgeted(maxTokens, context):
  1. 分离 pinned vs 非 pinned
  2. pinned 始终包含（累计 tokenEstimate）
  3. 非 pinned 按 priority 降序排列（同 priority 按 createTime 升序）
  4. 从高到低取，累计 tokenEstimate，跳过超出 maxTokens 的项
  5. 返回选中列表
```

**AiMemoryItem 字段**（L1-16 已落地）：

| 字段 | 类型 | 用途 |
|------|------|------|
| priority | int | 重要性评分（越高越优先注入），默认 0 |
| tokenEstimate | int | 预估 token 数，用于 budget 控制，默认 content.length()/4 |
| pinned | boolean | 标记永久保留（系统指令等），不受 budget 裁剪，默认 false |
| checksum | String | 内容校验和，用于去重和变更检测，默认 null |
| lastAccessTime | LocalDateTime | 最近访问时间，用于 LRU 淘汰，默认 createTime |
| accessCount | int | 访问次数，用于 LFU 淘汰，默认 0 |

**接口上的 default UOE（保留给未来实现选择）**：
```java
default List<AiMemoryItem> readBudgeted(int maxTokens, Map<String, Object> context) {
    throw new UnsupportedOperationException("readBudgeted requires Phase 2");
}
default void update(String key, AiMemoryItem item) {
    throw new UnsupportedOperationException("update requires Phase 2");
}
default void remove(String key) {
    throw new UnsupportedOperationException("remove requires Phase 2");
}
default void batchAdd(List<AiMemoryItem> items) {
    throw new UnsupportedOperationException("batchAdd requires Phase 2");
}
```

**设计理由**：
- Budgeted Injection 解决了"记忆太多撑爆上下文"的问题——不是简单截断，而是按重要性智能选择
- `priority` + `tokenEstimate` 双字段控制，不需要外部排序服务
- `pinned` 标记确保关键记忆（系统指令、安全规则）不被 budget 裁剪
- MiMoCode 的实践经验证明此模式在长会话中显著提升记忆利用率
- Provider 模式保持 `IAiMemoryStore` 接口签名稳定（L1-16 契约不变），L4-3 `IMemoryAdapter` 可替换为 DB/vector 后端 Provider

**Deferred（L4-3 IMemoryAdapter successor scope）**：
- DB / 文件持久化：`InMemoryAiMemoryStore` 数据**不**随 `AgentSession` 序列化。进程重启后 memory 丢失
- 向量检索（embedding-based search）：当前 `search` 为子串匹配
- Session fork 时复制 memory：子 session 获得独立的空 memory
- Memory retention / TTL / 容量上限：`InMemoryAiMemoryStore` 无界保存

### 6.4 SessionSnapshot

快照用于审计追溯和分叉恢复：

- `snapshotId`: 唯一标识
- `sessionId`: 所属 session
- `createdAt`: 创建时间
- `messageCount`: 快照时的消息数量
- `tokenEstimate`: 快照时的 token 估算
- `storageRef`: VFS 上的存储路径

### 6.5 SessionLifecycleListener

Session 引擎在关键状态变更时发布事件，供其他引擎层订阅：

| 事件 | 触发时机 | 典型消费者 |
|------|---------|-----------|
| `session_created` | Session 首次创建 | 审计日志 |
| `session_loaded` | Session 加载完成 | 上下文引擎 |
| `session_compressed` | 压缩完成 | 审计日志、资源监控 |
| `session_forked` | 分叉创建 | 父子关系追踪 |
| `session_archived` | Session 归档 | 存储清理 |

## 7. 与其他引擎的关系

| 引擎 | 交互方式 |
|------|---------|
| ReAct 引擎 | ReAct 循环通过 `ISessionManager` 加载/保存 session；每轮 ReAct 结束后回写增量消息 |
| 上下文引擎 | 从 Session 加载消息列表构造 `AgentExecutionContext`；压缩后更新内存态 |
| Hook 引擎 | Hook 可订阅 `SessionLifecycleListener` 事件（如压缩前创建备份 Hook） |
| Security 引擎 | `IAuditLogger`（Layer 1）记录 Session 级别的安全决策（如分叉权限检查） |

## 8. 本篇结论

Session 不只是文件格式问题，也是 Java 引擎设计问题。Session 引擎作为持久化状态管理中心，通过 `ISessionManager` 接口向上层引擎提供加载/保存/分叉/压缩能力，通过 `SessionLifecycleListener` 事件机制解耦其他引擎层的关注点。
