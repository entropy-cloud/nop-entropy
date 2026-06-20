# 维度 15：类型安全与泛型使用

## 检查范围

78 接口定义、5 核心契约实现、所有 @SuppressWarnings("unchecked")（30 处）、ToolSchemaConverter/Parser/ArgumentValueCoercion/StructureRepair schema 解析路径、所有 IToolExecutor 实现、关键 DTO/record、所有 (String)/(Map/(List 强转。

## 第 1 轮（初审）发现

### [维度15-A] InMemoryTeamManager 将 ConcurrentMap 向下强转为 ConcurrentHashMap

- **文件**: `team/InMemoryTeamManager.java:218-220,245-247,271-273`；`team/Team.java:99-101`
- **证据片段**:
  ```java
  // Team.java:99 公共契约返回 ConcurrentMap
  public ConcurrentMap<String, TeamMember> getMembers() { return members; }
  // InMemoryTeamManager.java:218-220
  @SuppressWarnings("unchecked")
  ConcurrentHashMap<String, TeamMember> members = (ConcurrentHashMap<String, TeamMember>) team.getMembers();
  // 同文件 :338 正确写法 Map<String,TeamMember> members = team.getMembers();（无强转）
  ```
- **严重程度**: P1 → **复核降级 P2**
- **现状**: Team.getMembers() 返回 ConcurrentMap（更宽类型），构造器接受任意 ConcurrentMap。InMemoryTeamManager 3 处强转为具体类 ConcurrentHashMap，未 instanceof 校验，依赖未编码的实现细节。
- **复核降级依据**: 强转后调用的方法（size/putIfAbsent/remove/get/values/compute）**全部已在 ConcurrentMap 接口（或父接口 Map）上声明**（putIfAbsent abstract、compute default 都在 ConcurrentMap），故强转**完全无必要**——直接以 ConcurrentMap 引用调用即可编译。原子性由运行时实现类决定，非静态类型；传入非 ConcurrentHashMap 会在首次 mutation 处 fail-fast ClassCastException，不存在"静默丢失原子性"路径。实际不变量已被遵守（所有 caller 用 ConcurrentHashMap 构造）。
- **建议**: 局部变量类型改 ConcurrentMap，删除 @SuppressWarnings("unchecked")。
- **信心水平**: 高
- **误报排除**: 编译器层面验证可行；同文件 :338 已用更宽类型工作。
- **复核状态**: **已复核——降级 P1→P2**（强转无必要、无原子性丢失、fail-fast 非静默，属代码味道/类型契约不一致）。

### [维度15-B] AgentMessageEnvelopeJson.fromJson 多处 (String) cast 未 instanceof 校验且在 try 外

- **文件**: `message/AgentMessageEnvelopeJson.java:100-104,122`
- **证据片段**:
  ```java
  // 100-104（紧接 try/catch JSON 解析之后，不在 try 内）
  String senderId = (String) map.get(FIELD_SENDER_ID);
  String targetTopic = (String) map.get(FIELD_TARGET_TOPIC);
  String correlationId = (String) map.get(FIELD_CORRELATION_ID);
  String kindName = (String) map.get(FIELD_KIND);
  // 122
  String payloadClassName = (String) map.get(FIELD_PAYLOAD_CLASS_NAME);
  // 对比 117-119 timestamp 的安全处理：
  Object tsValue = map.get(FIELD_TIMESTAMP); if (tsValue instanceof Number) { timestamp = ((Number)tsValue).longValue(); }
  ```
- **严重程度**: P1
- **现状**: 5 字段直接 (String) map.get(...)，未 instanceof 校验，且位于 try/catch 块外（try 仅覆盖 86-98 JsonTool.parseNonStrict）。该类 javadoc(:78) 声明 @throws NopAiAgentException，toJson 的 catch(:65-71) 也确实包装成 NopAiAgentException，证明该类契约就是统一包装。非 String 值会抛裸 ClassCastException 绕过契约。
- **风险**: DB transport 反序列化（DBMessageService 使用），schema 漂移/第三方 producer/DB 损坏导致非 String 字段时，ClassCastException 作为裸异常绕过统一异常处理路径。
- **建议**: 沿用 :117 instanceof 守卫模式，或抽 asString(Map,key,fieldName) helper 对非 String 抛 NopAiAgentException。
- **信心水平**: 高
- **误报排除**: 该文件无任何 @SuppressWarnings（30 个匹配不含此文件），是裸强转；javadoc 明示用于 DB transport，外部 producer 不可控。影响范围限定：正常 round-trip（同类 toJson 写入）不会触发，跨实现/手改 DB 场景触发。
- **复核状态**: **已复核——成立（维持 P1）**，但触发概率较低（可同时记 P2 备注）。

### [维度15-C] AgentMessageEnvelopeJson.fromJson unchecked cast 未配 @SuppressWarnings

- **文件**: `message/AgentMessageEnvelopeJson.java:92`
- **证据片段**:
  ```java
  Object parsed = JsonTool.parseNonStrict(json);
  if (!(parsed instanceof Map)) { throw new NopAiAgentException(...); }
  map = (Map<String, Object>) parsed;   // 92 unchecked cast，无注解
  // 对比 SessionFileReader.java:77 正确：@SuppressWarnings("unchecked") Map<String,Object> map = (Map<String,Object>) parsed;
  ```
- **严重程度**: P3
- **现状**: instanceof Map 保证 raw 类型安全，但 (Map<String,Object>) 仍是 unchecked cast，应配注解。模块内 SessionFileReader:77/CheckpointSnapshotReader:62/ArgumentStructureRepairStage:48 都正确添加，唯独此处遗漏。
- **风险**: 编译警告噪音，掩盖未来真实类型问题；新人无法从注解推断 cast 是否被审视。
- **建议**: 补 @SuppressWarnings("unchecked")。
- **信心水平**: 高
- **误报排除**: grep 确认该文件无任何 @SuppressWarnings；对比同包 reader 确认惯例。mvn compile 不因警告失败，故真实"应修复但被绿灯掩盖"。
- **复核状态**: 未复核

### [维度15-D] 5 个 Tool Executor 的 doExecuteAsync 方法级 @SuppressWarnings("unchecked") 过度宽泛

- **文件**: `tool/SendMessageExecutor.java:54`、`tool/TeamSendMessageExecutor.java:72`、`tool/TeamTaskCreateExecutor.java:75`、`tool/TeamTaskUpdateExecutor.java:81`、`tool/CallAgentExecutor.java:103`
- **证据片段**:
  ```java
  // SendMessageExecutor:54
  @SuppressWarnings("unchecked")
  private CompletionStage<AiToolCallResult> doExecuteAsync(AiToolCall call, IToolExecuteContext context) {
      ... // 方法体内强转都是非 generic（如 (AgentToolExecuteContext) context），unchecked 实际只在 resolveArguments 内部
  // :133 实际 unchecked 操作在独立 helper，已有自己的注解
  @SuppressWarnings("unchecked")
  private Map<String, Object> resolveArguments(AiToolCall call) { ... }
  ```
- **严重程度**: P3
- **现状**: doExecuteAsync 方法体内强转都非 generic，unchecked 实际只在 resolveArguments（已自带注解）。@SuppressWarnings 不跨方法传播，故方法级注解冗余/过度宽泛。
- **风险**: 方法级注解会无意压制未来在该方法引入的真实 unchecked 操作警告。
- **建议**: 删除 5 处 doExecuteAsync 上的注解，保留 resolveArguments 上的。
- **信心水平**: 中-高
- **误报排除**: 已核验 SendMessageExecutor/CallAgentExecutor doExecuteAsync 无其他 unchecked；排除 ReActAgentExecutor:1926（CompletableFuture[] 数组创建合法）、AbstractMemoryToolExecutor:55（注解必要）。
- **复核状态**: 未复核

### [维度15-E] CallAgentResponsePayload.status 与 AgentMessageAck.status 用 String 而非枚举，跨多文件字面量比较

- **文件**: `message/CallAgentResponsePayload.java:31`；`engine/AgentMessageAck.java:6`；消费方 CallAgentExecutor:280、DefaultAgentEngine:846,818,871 等
- **证据片段**:
  ```java
  // CallAgentResponsePayload javadoc 值域闭集 success/failure，字段却是 String
  private final String status;   // :31
  // CallAgentExecutor:280
  if (!"success".equals(resp.getStatus())) { agentResult.setStatus("failure"); }
  ```
- **严重程度**: P3（与维度20-05 同源）
- **现状**: 两 DTO status 值域 javadoc 闭集，但字段 String，生产/消费两端裸字面量分布于 8+ 文件（55 处）。模块其他闭集字段（AgentExecStatus/TeamStatus 等）已普遍用 enum。
- **风险**: 编译器无法捕获拼写错误（"Success"/"sucess"）；重构易遗漏字面量。
- **建议**: 引入 CallAgentResponseStatus/AgentMessageAckStatus enum。
- **信心水平**: 中
- **误报排除**: 排除 AiToolCallResult.setStatus（属 nop-ai-toolkit 不在范围）；仅就本模块自身 DTO。
- **复核状态**: 未复核

### [维度15-F] ITeamAclChecker.checkAccess 的 action 参数用 String 表达封闭值集

- **文件**: `team/ITeamAclChecker.java:76-77`；消费方 5 个 Team 工具 Executor
- **证据片段**:
  ```java
  TeamAclDecision checkAccess(String teamId, String callerSessionId, String toolName, String action);  // action 值封闭 8 个
  // DefaultTeamAclChecker:73-87 静态映射表 key 为字面量
  m.put(key("team-task-update","claim"), TeamAclAction.EXECUTE);
  ```
- **严重程度**: P3
- **现状**: action 值集封闭（send/view/create/claim/complete/abandon-claimed/abandon-unclaimed），接口签名用 String。DefaultTeamAclChecker 查表未命中 fail-closed 拒绝，缓解运行时风险，但调用方传字面量。
- **风险**: 类型系统不强制闭集；新工具 Executor 传错 action 字面量只能 fail-closed 运行时捕获，编译期无提示。
- **建议**: 新增 TeamAclActionVerb enum，接口签名 action 改枚举（toolName 保持 String 因动态）。
- **信心水平**: 中
- **误报排除**: TeamAclAction（READ/WRITE/EXECUTE/ADMIN）已存在但是矩阵列权限，与调用方动词不同维度，需新枚举。
- **复核状态**: 未复核

## 已核验良好（未报告）

无 Raw Type（全量扫描 0 命中）；6 核心接口泛型精度精确（IAgentExecutor/IToolExecutor/ICheckpointManager/ITeamManager 等固定类型签名，无 IAgentExecutor<T> 泛型参数）；ToolSchemaConverter/Parser/ArgumentValueCoercion 的 Object 强转是 LLM/tool 动态 schema 合理边界（已克制）；DTO/record 为 final immutable + defensive copy + 完整 equals/hashCode；Contribution.payload 是显式 tagged union + instanceof 守卫；DefaultPermissionProvider.extractSessionPermissions 逐元素 instanceof 校验后强转（合理）；InMemoryActorRuntime.toSteeringMessage instanceof 守卫；ReActAgentExecutor:1926 @SuppressWarnings 用于 CompletableFuture[] 数组创建（合法）。

## 维度复核结论

[维度15-A] 独立复核：**降级 P1→P2**（强转无必要、无原子性丢失、fail-fast）。[维度15-B] 独立复核：**成立 P1**（5 处 cast 在 try 外属实，契约违反成立，触发概率低）。[维度15-C/D/E/F] 复核未发现反证，保留 P3。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 15-A | P2 | team/InMemoryTeamManager.java | ConcurrentMap 强转为 ConcurrentHashMap（已降级） |
| 15-B | P1 | message/AgentMessageEnvelopeJson.java | 5 处 (String) cast 在 try 外无 instanceof（违反 NopAiAgentException 契约） |
| 15-C | P3 | message/AgentMessageEnvelopeJson.java | unchecked cast 未配 @SuppressWarnings |
| 15-D | P3 | tool/*Executor.java(5处) | 方法级 @SuppressWarnings 过度宽泛 |
| 15-E | P3 | message/CallAgentResponsePayload.java | status 用 String 而非枚举（同20-05） |
| 15-F | P3 | team/ITeamAclChecker.java | action 参数用 String 表达封闭值集 |
