# 维度 07：BizModel 规范遵循 — nop-ai-agent

## 第 1 轮（初审）

### 检查范围

nop-ai-agent 是框架级引擎模块，不包含 BizModel 服务类。审计调整为检查公开 API 设计模式。

**已检查接口/类（15 个）:**
- IAgentEngine (4 方法), IAgentExecutor (1), IAgentEventPublisher (3), IAgentEventSubscriber (1)
- IModelRouter (1), IContentGuardrail (1), IToolCallRepairer (1), IAgentLifecycleHook (1)
- ISessionStore (9), IContextCompactor (1), IPermissionProvider (1)
- IToolAccessChecker (1), IPathAccessChecker (1), IAuditLogger (1), IAiMemoryStore (8)

**已检查实现：** DefaultAgentEngine, ReActAgentExecutor, SingleTurnExecutor

---

### [维度07-01] AgentMessageAck.status 使用原始 String 而非枚举

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentMessageAck.java:6-14`
- **证据片段**:
  ```java
  public class AgentMessageAck {
      private final String sessionId;
      private final String status;

      public AgentMessageAck(String sessionId, String status) {
          this.sessionId = sessionId;
          this.status = status;
      }

      public AgentMessageAck(String sessionId) {
          this(sessionId, "accepted");
      }
  ```
- **严重程度**: P3
- **现状**: `status` 字段是原始 `String`，唯一硬编码值为 `"accepted"`。同模块中 AgentExecStatus、AuditDecision、GuardrailDirection 等状态字段均使用枚举。
- **风险**: 若未来增加 "rejected"、"queued" 等状态，调用方无法通过编译期类型检查发现拼写错误。
- **建议**: 引入枚举（如 `AgentMessageStatus`）或至少定义 `public static final` 常量。
- **信心水平**: 确定
- **误报排除**: 不是同类误报。同模块中已有枚举先例，此处的 String 是不一致之处。
- **复核状态**: 未复核

---

### [维度07-02] ToolSchemaConverter.convert() 静默吞没所有异常

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ToolSchemaConverter.java:13-22`
- **证据片段**:
  ```java
  public static Map<String, Object> convert(XNode schema) {
      if (schema == null)
          return null;

      try {
          return doConvert(schema);
      } catch (Exception e) {
          return null;     // 所有异常被静默吞没，无日志
      }
  }
  ```
- **严重程度**: P2
- **现状**: 当 schema 结构存在问题时，此方法返回 `null` 而不记录任何日志。调用方 `ReActAgentExecutor.buildToolDefinitions()` 第 667-671 行对 `null` 做了防御处理，会创建不含参数定义的 `ChatToolDefinition`。
- **风险**: 一个本应有参数 schema 的工具会被静默降级为无参数版本发送给 LLM，可能导致 LLM 生成无效的工具调用参数，产生难以溯源的运行时错误。
- **建议**: 在 catch 块中添加 `LOG.warn("Failed to convert tool schema", e)` 级别的日志，或对非预期异常向上抛出。
- **信心水平**: 确定
- **误报排除**: 不是同类误报。这不是"优雅"问题，而是真实的调试困难——schema 降级时无任何可观测性。
- **复核状态**: 未复核

---

### [维度07-03] IAiMemoryStore 使用 Map<String, Object> 作为过滤参数

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/memory/IAiMemoryStore.java:7,15`
- **证据片段**:
  ```java
  List<AiMemoryItem> getAll(Map<String, Object> filters);

  default List<AiMemoryItem> readBudgeted(int maxTokens, Map<String, Object> context) {
  ```
- **严重程度**: P3
- **现状**: `getAll` 的 filters 参数和 `readBudgeted` 的 context 参数均为 `Map<String, Object>`，调用方无法通过编译期约束获知合法 key/value。同模块中其他接口均使用具体类型参数。
- **风险**: 调用方无法通过编译期约束获知合法的 filter key 和 value 类型。
- **建议**: 待 Memory 模块进入 Phase 2 时，考虑用 `MemoryQuery` 或 `MemoryFilter` 等类型化对象替换。
- **信心水平**: 确定
- **误报排除**: 不是同类误报。这是 Phase 1 接口尚未定型，标记为低优先级。
- **复核状态**: 未复核
