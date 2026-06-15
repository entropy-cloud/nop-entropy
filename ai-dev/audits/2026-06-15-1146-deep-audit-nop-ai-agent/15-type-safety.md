# 维度 15：类型安全与泛型使用（nop-ai-agent）

## 第 1 轮（初审）

**总体评价**：维度 15 总体清洁。`@SuppressWarnings("unchecked")` 在手写代码中均配合 `instanceof Map/Collection/List/Number` 等运行时检查或在 JSON 反序列化立即校验。无 raw type 滥用。以下为低风险信息性发现。

### [维度15-1] SessionFileReader.readMessages 强转 List<ChatMessage> 缺少 runtime 类型校验

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/SessionFileReader.java:145-160`
- **证据片段**:
  ```java
  private static List<ChatMessage> readMessages(Object raw) {
      if (raw == null) {
          return null;
      }
      try {
          Object bean = JsonTool.jsonObjectToBean(raw, MESSAGES_LIST_TYPE);
          @SuppressWarnings("unchecked")
          List<ChatMessage> list = (List<ChatMessage>) bean;    // 154: 不检查 bean instanceof List
          return list;
      } catch (Exception e) {
          throw new NopAiAgentException(...);
      }
  }
  ```
- **严重程度**: P3
- **现状**: `jsonObjectToBean(raw, MESSAGES_LIST_TYPE)` 期望返回 `List<ChatMessage>`，但若 raw 不是数组（例如被恶意改成 JSON 对象），JsonTool 可能返回非 List 类型，unchecked cast 在此处不抛 ClassCastException，而是在调用方迭代时抛。
- **风险**: 实际风险低（写入侧 SessionFileWriter.serialize 保证 messages 是 List），但缺少防御性检查让错误诊断变难。
- **建议**: cast 前加 `if (!(bean instanceof List)) throw new NopAiAgentException("messages field must be a JSON array");`。
- **信心水平**: 中
- **误报排除**: JsonTool.jsonObjectToBean 在解析失败时通常抛异常已被 catch，仅在"解析成功但类型不匹配"路径才有该问题。
- **复核状态**: 未复核

### [维度15-2] resolveArguments 系列：JSON.parse 后强转 Map<String,Object> 缺少值类型校验

- **文件**:
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java:274-292`
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/SendMessageExecutor.java:133-149`
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/AbstractMemoryToolExecutor.java:55-71`
- **证据片段** (CallAgentExecutor):
  ```java
  @SuppressWarnings("unchecked")
  private Map<String, Object> resolveArguments(AiToolCall call) {
      Map<String, Object> args = new HashMap<>();
      if (call.getInput() != null && !call.getInput().isEmpty()) {
          try {
              Object parsed = JSON.parse(call.getInput());
              if (parsed instanceof Map) {
                  args.putAll((Map<String, Object>) parsed);   // 282: 仅检查 key/value 之一类型
              }
          } catch ...
      }
      return args;
  }
  ```
- **严重程度**: P3
- **现状**: `parsed instanceof Map` 只检查了 raw 类型，没检查 value 类型。Java 泛型擦除下强转 unchecked。
- **风险**: 实际风险极低——JSON 解析器返回的 Map 的 value 一定是 Object 的子类。这是 LLM tool calling 的固有特性，按审计口径需克制判断。报告为信息性。
- **建议**: 严格做法是 `Map<?, ?> m = (Map<?, ?>) parsed; for (Map.Entry<?, ?> e : m.entrySet()) { args.put(String.valueOf(e.getKey()), e.getValue()); }`，避免 unchecked。但当前实现在 LLM 上下文中可接受。
- **信心水平**: 高（误报可能性高，记录以便复核）
- **误报排除**: 三处实现都通过 instanceof Map + try-catch 保护；后续 args.get(key) 配合 getStringArg/val.toString() 处理任意值类型。
- **复核状态**: 未复核

### [维度15-3] DBMessageService.handleConsumerResult 强转 CompletionStage<Object> 类型擦除但语义合理

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:343-359`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  private void handleConsumerResult(String sid, Object result) {
      if (result instanceof CompletionStage) {
          ((CompletionStage<Object>) result).whenComplete((r, e) -> { ... });
      } ...
  ```
- **严重程度**: P3
- **现状**: 消费者返回类型是 `Object`，运行时检查 `instanceof CompletionStage` 后强转。由于 whenComplete 的 lambda 接收 Object，强转安全。
- **风险**: 无实际风险，记录为类型安全审计的覆盖证明。
- **建议**: 不需要修改。
- **信心水平**: 高
- **误报排除**: 已确认 IMessageConsumer.onMessage 返回 Object，后续处理都用 Object 语义。
- **复核状态**: 未复核

### [维度15-4] DBMessageService.findPending 用 rs.getString 读 CLOB 列，与 readClob 风格不一致

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:261-286`
- **证据片段**:
  ```java
  String sql = "SELECT " + AiAgentMessageTable.COL_SID + ", "
          + AiAgentMessageTable.COL_MESSAGE_BODY            // DDL 中是 CLOB NOT NULL
          + " FROM " + AiAgentMessageTable.TABLE_NAME
          ...
  try (ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
          rows.add(new MessageRow(rs.getString(1), rs.getString(2)));   // 278: getString 读 CLOB
      }
  }
  ```
  对照 `DBCheckpointManager.readClob:326-339` 用 `getClob` + `getCharacterStream` + `transferTo`。
- **严重程度**: P3
- **现状**: `MESSAGE_BODY` 列定义为 CLOB，但用 `rs.getString(2)` 读取。多数 JDBC 驱动对中等长度的 CLOB 支持 getString，但对超大 CLOB 会截断或抛异常。
- **风险**: LLM 工具调用消息体一般不会极大，但若 envelope 携带大型 payload 可能触发截断。一致性方面，DBCheckpointManager 用了正确的 readClob 模式。
- **建议**: 复用 `DBCheckpointManager.readClob(rs, COL_MESSAGE_BODY)` 模式，统一 CLOB 读取风格。
- **信心水平**: 中
- **误报排除**: 已确认 DDL 中 COL_MESSAGE_BODY 为 CLOB NOT NULL，DBCheckpointManager 对类似 CLOB 列用 readClob，存在风格分歧。
- **复核状态**: 未复核

## 零发现项（已检查未发现）

1. **Raw Type**：grep 全模块未发现手写代码（排除 _gen/import/javadoc）中使用 List/Map/Collection/Set/CompletableFuture/CompletionStage 的 raw 形式。
2. **接口泛型精度**：IAgentExecutor、ICheckpointManager、ISessionStore、IToolExecutor、IAgentMessenger 等核心接口的泛型签名准确。
3. **JSON 反序列化**：deserialize、parseCurationResponse、parseSkill 均在 cast 前做 instanceof Map 检查并 fail-fast。

## 维度复核结论

| 发现 | 复核结论 | 理由 |
|------|---------|------|
| [维度15-1] readMessages 缺 instanceof List | **保留 P3** | 强转确证，但写入侧保证 List，风险低。 |
| [维度15-2] resolveArguments 强转 | **保留 P3** | LLM 固有特性，误报可能性高，记录为信息性。 |
| [维度15-3] handleConsumerResult 强转 | **保留 P3** | 类型擦除下安全，信息性。 |
| [维度15-4] findPending 用 getString 读 CLOB | **保留 P3** | DDL 确证 CLOB 列，与 readClob 风格不一致。 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 15-1 | P3 | session/SessionFileReader.java:145-160 | readMessages 强转 List<ChatMessage> 缺 instanceof List 校验 |
| 15-2 | P3 | tool/CallAgentExecutor.java:274 等 | resolveArguments 强转 Map 缺值类型校验（LLM 固有） |
| 15-3 | P3 | message/DBMessageService.java:343-359 | handleConsumerResult 强转 CompletionStage（类型擦除下安全） |
| 15-4 | P3 | message/DBMessageService.java:278 | findPending 用 getString 读 CLOB 列，风格不一致 |
