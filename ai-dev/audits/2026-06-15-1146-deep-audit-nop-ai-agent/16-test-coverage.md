# 维度 16 + 21：测试覆盖与单元测试有效性（nop-ai-agent）

> 本文件合并维度 16（测试覆盖与质量）和维度 21（单元测试有效性）。两维度由同一子 agent 依据 `ai-dev/skills/unit-test-antipatterns.md` 的 P-1~P-8 反模式清单联合审计。

## 第 1 轮（初审）

**整体结论**：nop-ai-agent 的核心安全测试和持久化测试质量极高——`TestDefaultPathAccessChecker`、`TestDefaultToolAccessChecker`、`TestRuleBasedPathAccessChecker`、`TestParentConstrained*AccessChecker`、`TestDefaultPermissionProvider`、`TestActionFingerprint`、`TestFingerprintPostDenialGuard`、`TestDBDenialLedger`、`TestDispatchPathSecurityConsultation`、`TestPermissionInReActLoop`、`TestDBCheckpointManager` 等都真正验证 deny 语义、wiring（counter-based Anti-Hollow）、跨实例持久化生存、traversal guard——把对应 deny/allow 翻转或 counter 拆掉，测试会失败。**问题集中在"Phase 1 接口骨架"测试**：每个 NoOp/PassThrough/接口契约类都伴随一批纯 P-1/P-2 测试（枚举计数、值对象 getter 往返、isAssignableFrom 元数据），零缺陷捕获能力。

### [维度21-1] 多个枚举类测试是纯计数 + valueOf，零缺陷捕获能力
- **文件**: `security/TestChannelKind.java:10-26`、`security/TestSecurityLevel.java:13-35`、`guardrail/TestGuardrailDirection.java:9-19`、`guardrail/TestGuardrailMode.java:9-20`、`security/TestPathAccessDecision.java:13-18`
- **证据片段**（TestChannelKind）:
  ```java
  @Test void hasExactlyFourChannels() {
      ChannelKind[] values = ChannelKind.values();
      assertEquals(4, values.length,
              "ChannelKind must have exactly 4 values per design §5.3");
  }
  @Test void valuesMatchDesignSpec() {
      assertEquals(ChannelKind.WEBUI, ChannelKind.valueOf("WEBUI"));
      assertEquals(ChannelKind.API,  ChannelKind.valueOf("API"));
      ...
  }
  ```
- **严重程度**: P3
- **现状**: 5 个枚举类测试文件，约 12 个 @Test 方法，全部用于断言"枚举有几个值"和"valueOf('X') == X"。
- **风险**: 这些测试唯一能发现的"缺陷"是有人改了枚举成员；但改枚举成员不是 bug。
- **建议**: 删除 `hasExactly*` 和 `valuesMatchDesignSpec`/`containsAllModes`/`enumHasAllowAndDenyValues`；保留同文件中的 `fromString*` 行为测试。
- **信心水平**: 高
- **误报排除**: 已确认 `TestPathAccessDecision.fromStringDefaultsToDenyFor*` 是 fail-closed 行为测试，不计入本条。
- **复核状态**: 未复核
- **命中反模式**: P-1（枚举计数变体）+ P-2（value-of 元数据）

### [维度21-2] `implementsXxx` / `interfaceContractCanBeImplemented` 系列是元数据测试，不验证任何行为
- **文件**: `skill/TestNoOpSkillProvider.java:20-24`、`talent/TestNoOpTalent.java:34-38`、`skill/TestISkillProvider.java:13-27`、`talent/TestITalent.java:14-39`、`router/TestIModelRouter.java:17-32`、`completion/TestICompletionJudge.java:14-31`、`guardrail/TestNoOpContentGuardrail.java:60-62`
- **证据片段**（TestIModelRouter）:
  ```java
  @Test void interfaceContractCanBeImplemented() {
      IModelRouter router = new IModelRouter() {
          @Override public RoutingResult route(...) {
              return new RoutingResult(options, null, "test-impl");
          }
      };
      RoutingResult result = router.route(msgs, opts, null);
      assertTrue(result instanceof RoutingResult);   // 永远为 true
  }
  @Test void interfaceIsAssignableFromPassThrough() {
      assertTrue(IModelRouter.class.isAssignableFrom(PassThroughModelRouter.class));
  }
  ```
- **严重程度**: P3
- **现状**: 测试只验证 Java 编译器/类层次结构已经保证的事实——接口可以被匿名实现、子类可以赋值给父类。`assertTrue(result instanceof RoutingResult)` 永远成立。
- **风险**: 如果有人把 PassThroughModelRouter 改成不实现 IModelRouter，编译就失败；测试本身不提供任何独立信号。
- **建议**: 删除全部 `implementsXxx`/`interfaceIsAssignableFromXxx`/`interfaceContractCanBeImplemented` 方法。
- **信心水平**: 高
- **误报排除**: `TestNoOpContentGuardrail.checkReturnsPass*` 是真行为断言，不计入本条。
- **复核状态**: 未复核
- **命中反模式**: P-2（测试类层次元数据而非行为）

### [维度21-3] 值对象的纯 getter/setter 往返测试，与 DataBean 自动生成物重复
- **文件**: `security/TestAuditEvent.java:13-107`、`security/TestPermission.java:13-35`、`security/TestPrincipal.java:16-46`、`security/TestLevelHints.java:17-34`、`skill/TestSkillModel.java:18-51`、`memory/TestAiMemoryItem.java:85-118`、`engine/TestAgentExecutionContext.java:37-59`
- **证据片段**（TestAuditEvent）:
  ```java
  @Test void testConstructionWithAllFields() {
      long ts = System.currentTimeMillis();
      AuditEvent event = new AuditEvent("sess-1", "agent-x", "actor-1",
              "calculator", AuditDecision.ALLOW, null, "allow_all", null, ts);
      assertEquals("sess-1", event.getSessionId());      // 构造时传什么，get 出什么
      assertEquals("agent-x", event.getAgentName());
      ...
  }
  ```
- **严重程度**: P3
- **现状**: 7+ 个值对象类配纯字段往返测试，约 30+ 个 @Test 方法。getter/setter 由 Java 编译器或代码生成器保证正确。
- **风险**: 这类测试无法发现任何业务逻辑缺陷。唯一的"失败模式"是有人重命名字段，而那是 refactor，不是 bug。
- **建议**: 删除纯字段往返的方法；只保留有真实逻辑的（TestAuditEvent.testEquality/testInequality、TestPermission.testEquals、TestPrincipal.equalsAndHashCodeByValue、TestLevelHints.equalsAndHashCodeByValue、TestAiMemoryItem.testTokenEstimateFallbackWhenContentSet）。
- **信心水平**: 高
- **误报排除**: 已逐方法核对，本条仅指"set 然后 assertEquals 同一值"型方法；fallback/equals/not-equals 方法排除。
- **复核状态**: 未复核
- **命中反模式**: P-1（纯 getter/setter 往返）

### [维度21-4] Result/sealed-hierarchy 测试中"concreteTypesExtendXxx" 是元数据断言
- **文件**: `completion/TestCompletionDecision.java:57-61`、`hook/TestHookResult.java:56-60`、`guardrail/TestGuardrailResult.java:13-17`
- **证据片段**（TestCompletionDecision）:
  ```java
  @Test void concreteTypesExtendCompletionDecision() {
      assertNotNull((CompletionDecision) CompletionDecision.Complete.instance());
      assertNotNull((CompletionDecision) new CompletionDecision.Continue("m"));
      assertNotNull((CompletionDecision) new CompletionDecision.Escalate("r"));
  }
  ```
- **严重程度**: P3
- **现状**: sealed result 类测试混合两类断言：(a) outcomesAreDistinguishable（真行为）；(b) concreteTypesExtendXxx（强转 + assertNotNull——元数据）。
- **风险**: 同 [21-2]，编译器已保证类型层级。
- **建议**: 删除 `concreteTypesExtend*`；保留 `outcomesAreDistinguishable`、`continueCarriesMessage`、`escalateCarriesReason` 等行为测试。
- **信心水平**: 高
- **误报排除**: 同文件 `outcomesAreDistinguishable` 验证互斥关系，是真行为，保留。
- **复核状态**: 未复核
- **命中反模式**: P-2（类层级元数据）+ P-5（强转后 assertNotNull）

### [维度21-5] 常量集合/阈值的镜像断言：测试与实现同义反复
- **文件**: `compact/TestMicroCompressionCompactor.java:298-305`、`compact/TestToolResultTruncator.java:107-115`、`security/TestRuleBasedPathAccessChecker.java:298-303`、`security/TestParentConstrainedPathAccessChecker.java:478-483`
- **证据片段**（TestMicroCompressionCompactor）:
  ```java
  @Test void compressibleToolsSetContainsExpectedTools() {
      assertTrue(MicroCompressionCompactor.COMPRESSIBLE_TOOLS.contains("read_file"));
      assertTrue(MicroCompressionCompactor.COMPRESSIBLE_TOOLS.contains("bash"));
      ...
  }
  ```
- **严重程度**: P3
- **现状**: 4 处常量镜像测试。改常量必须同步改测试，说明测试没有独立验证能力。
- **风险**: 如果把 DEFAULT_TRUNCATION_THRESHOLD_CHARS 从 8000 改到 4000，测试会失败，但那不是 bug。真正的截断行为已被 contentAboveThresholdIsTruncated 覆盖。
- **建议**: 删除常量镜像方法；通过输入/输出测试常量效果。
- **信心水平**: 中-高
- **误报排除**: `matchedRuleTokenIsStable` 略有文档化价值，可保留作为 contract pin。
- **复核状态**: 未复核
- **命中反模式**: P-4（测试与实现同义反复）

### [维度21-6] `TestRoutingResult.toStringContainsFields`：纯 assertNotNull，几乎不会失败
- **文件**: `router/TestRoutingResult.java:57-66`
- **证据片段**:
  ```java
  @Test void toStringContainsFields() {
      ChatOptions options = new ChatOptions();
      options.setModel("test-model");
      RoutingResult result = new RoutingResult(options, "simple", "test-reason");
      String str = result.toString();
      assertNotNull(str);   // Object.toString() 永不返回 null
  }
  ```
- **严重程度**: P3
- **现状**: 方法名声称"toString contains fields"，但只断言 assertNotNull(str)，没有验证 str.contains("test-model") 或 str.contains("test-reason")。
- **风险**: 如果 RoutingResult.toString() 被改成空字符串，测试仍通过——与方法名承诺的契约不符。
- **建议**: 要么删除该方法，要么补齐 `assertTrue(str.contains("test-reason"))`。
- **信心水平**: 高
- **误报排除**: 无。
- **复核状态**: 未复核
- **命中反模式**: P-5（过度 assertNotNull）+ P-6（方法名不表达真实断言）

### [维度21-7] `TestISkillProvider` / `TestNoOpSkillCurator` 的私有 `assertTrue` shim
- **文件**: `skill/TestISkillProvider.java:42-44`、`skill/TestNoOpSkillCurator.java:50-52`
- **证据片段**（TestISkillProvider）:
  ```java
  private static void assertTrue(boolean condition) {
      org.junit.jupiter.api.Assertions.assertTrue(condition);
  }
  ```
- **严重程度**: P3
- **现状**: 测试类自定义了私有 assertTrue shim 而不是直接 import static Assertions.assertTrue，且这些方法本身（implementationMayReturnEmpty）也是 P-2 元数据测试。
- **风险**: 不是缺陷，但表明这些"接口骨架测试"是批量生成的样板，未经过审慎设计。
- **建议**: 删除整套 NoOp/PassThrough 的 implementsXxx + interfaceContractCanBeImplemented 元数据测试时一并清理 shim。
- **信心水平**: 高
- **误报排除**: `factoryReturnsSingleton`/`returnsEmptyNonNullCollection` 是真行为，保留。
- **复核状态**: 未复核
- **命中反模式**: P-2 + P-6（shim 掩盖真实意图）

### [维度16-1] `TestReActAgentExecutor` 自身不验证 security/permission 拒绝路径
- **文件**: `engine/TestReActAgentExecutor.java:85-303`
- **证据片段**: 6 个 @Test 方法（testNoToolCallImmediateReturn、testSingleToolCallLoop、testMultipleToolCalls、testMaxIterationsReached、testLlmCallFailure、testToolExecutionError）全部用 `ReActAgentExecutor.builder().chatService(...).toolManager(...).build()` 裸构建，**没有** wire permissionProvider/pathAccessChecker/toolAccessChecker/postDenialGuard。
- **严重程度**: P2
- **现状**: 此文件是 ReAct 主循环的"业务 happy/错误路径"测试，覆盖了 5 条路径，但全部绕过安全子系统。
- **风险**: 风险已被其他文件缓解——`TestPermissionInReActLoop`、`TestPathAccessCheckerInReActLoop`、`TestToolAccessCheckerInReActLoop`、`TestDispatchPathSecurityConsultation` 等都用真实 wire。因此主循环 + 安全 wiring 在整体测试矩阵中是被覆盖的。本条只标记此文件自身缺少，不是缺口。
- **建议**: 不需要新增测试；可在 javadoc 中说明"安全 wiring 见 TestPermissionInReActLoop 等"。
- **信心水平**: 高
- **误报排除**: 已交叉确认 `TestPermissionInReActLoop.testDeniedToolProducesErrorResponse` 通过 `toolCalled.get() == false` 真实捕获 deny 路径。
- **复核状态**: 未复核

### [维度16-2] 缺少 DB-backed 类的"损坏数据恢复"测试（journal.md 已覆盖，DB CLOB/SQL 注入未覆盖）
- **文件**: `reliability/TestDBCheckpointManager.java:1-551`、`reliability/TestCheckpointJournalSnapshotFormat.java:242-269`
- **证据片段**: `TestCheckpointJournalSnapshotFormat.corruptedSectionSkippedWithRemainingParsed` 优秀地验证了文件 journal.md 的损坏 section 跳过；但 `TestDBCheckpointManager` 没有 (a) CLOB 字段被截断、(b) 列被乱序、(c) SESSION_ID 含 SQL 转义字符（如 '）、(d) WATERMARK 含特殊字符的恢复测试。
- **严重程度**: P2
- **现状**: TestDBCheckpointManager.fieldIntegrityLargeClobContent 已测 5000 行大字符串 CLOB round-trip（很好），duplicateWatermarkInsertThrowsFailFast 已测 PK 约束（很好），但缺少"DB 数据被外部进程篡改/截断后读取行为"的边界。
- **风险**: 生产环境 DB 损坏时，DBCheckpointManager.getCheckpoint 行为未定义。
- **建议**: 增 1-2 个测试：手工 INSERT 一行 INPUT_SUMMARY 被截断的记录，调用 getCheckpoint 验证抛出 NopAiAgentException（fail-fast）或明确文档化。SESSION_ID 含 ' 的注入测试也值得加。
- **信心水平**: 中
- **误报排除**: 已确认 journal.md 的 corruption 测试覆盖了文件路径，本条只针对 DB 路径。
- **复核状态**: 未复核

### [维度16-3] `TestFileBackedSessionStore` 的"恶意序列化 payload / path traversal"边界未覆盖
- **文件**: `session/TestFileBackedSessionStore.java`、`session/TestDBSessionStore.java`
- **证据片段**: `TestFileBackedCheckpointManager.saveCheckpointRejectsTraversalSessionIdAndWritesNoFile` 优秀地测了 path-traversal sessionIds（fail-closed）；但 session store 层的 path traversal / 反序列化漏洞测试覆盖较薄——`TestFileBackedCheckpointManager` 的 traversal 测试模板未同样应用到 `FileBackedSessionStore`。
- **严重程度**: P2
- **现状**: 文件系统层已有 `assertRootHasNoSessionArtifacts(root)` + `assertThrows(NopAiAgentException.class, () -> mgr.getLatestCheckpoint("../../etc/exploit"))` 模板，但只在 checkpoint manager 测了；session store 是同样的 root/sessionId 二级目录结构，应该有对称保护。
- **风险**: 如果 FileBackedSessionStore 内部用 Paths.get(root, sessionId) 而没有 traversal 校验，恶意 sessionId 可能写出 root 之外。
- **建议**: 在 TestFileBackedSessionStore 中增 traversal sessionId 测试，复用 assertRootHasNoSessionArtifacts 模式。
- **信心水平**: 中
- **误报排除**: 未读 FileBackedSessionStore.java 生产实现细节，假设结构与 FileBackedCheckpointManager 同构；如不同请降级。
- **复核状态**: 未复核

### [维度16-4] Mock 工具与真实 IToolManager.callTools 返回 null，存在"mock 不一致"风险
- **文件**: `engine/TestReActAgentExecutor.java:330-333`、`engine/TestAgentEventPublisher.java:330-333`，约 15 个测试文件
- **证据片段**（TestReActAgentExecutor.NoOpToolManager）:
  ```java
  @Override
  public CompletableFuture<AiToolCallsResponse> callTools(
          AiToolCalls calls, IToolExecuteContext context) {
      return null;   // 真实实现可能抛 UnsupportedOperationException 或返回 future
  }
  ```
- **严重程度**: P2
- **现状**: 大量测试的 mock IToolManager 对 callTools 返回 null。如果真实 IToolManager 实现要求 callTools 永不返回 null，mock 不符合契约。
- **风险**: ReAct 主循环目前用 callTool（单数）逐个调用，所以 callTools 返回 null 不会被触发。但如果未来重构为批量调用，所有这些 mock 都会让 NPE 静默通过。
- **建议**: 读真实 IToolManager 实现的 callTools 契约；如果禁止 null，mock 应改为 completedFuture 或 UnsupportedOperationException。
- **信心水平**: 中
- **误报排除**: 测试都通过，说明当前 ReAct 循环不调用 callTools；本条是预防性发现。
- **复核状态**: 未复核

### [维度16-5] TestAgentExecutionResult 与 TestAgentExecutionContext 的 P-1 集中度过高
- **文件**: `engine/TestAgentExecutionResult.java:20-105`、`engine/TestAgentExecutionContext.java:18-135`
- **证据片段**（TestAgentExecutionResult.testDirectConstructor）:
  ```java
  @Test public void testDirectConstructor() {
      List<ChatMessage> msgs = List.of(new ChatUserMessage("a"), new ChatUserMessage("b"));
      AgentExecutionResult result = new AgentExecutionResult(
              AgentExecStatus.completed, "done", msgs, 5, 1000L, 300L, null);
      assertEquals(AgentExecStatus.completed, result.getStatus());
      assertEquals("done", result.getFinalMessage());
      ...
  }
  ```
- **严重程度**: P3
- **现状**: 两个文件 ~13 个方法中，约半数是纯字段往返。值得保留的：testMessagesAreDefensiveCopy（验证不变性契约）、testResultMessagesAreImmutable（UnsupportedOperationException 断言）。
- **风险**: 同 [21-3]；值对象字段 getter 测试零缺陷捕获。
- **建议**: 保留 defensive-copy 和 immutable 测试；删除纯字段往返。
- **信心水平**: 高
- **误报排除**: `testMessagesAreDefensiveCopy` 是真不变性测试，明确排除。
- **复核状态**: 未复核
- **命中反模式**: P-1

## 反模式命中统计表

| 反模式 | 命中文件数 | 典型方法数 | 严重程度 |
|--------|----------|----------|--------|
| P-1 纯 getter/setter 往返/枚举计数 | ~10 | ~40 | P3 |
| P-2 测试元数据/类层级而非行为 | ~10 | ~15 | P3 |
| P-3 只测 happy path | 1 | 6 | P2（已被兄弟文件缓解） |
| P-4 测试与实现同义反复 | 4 | ~5 | P3 |
| P-5 过度 assertNotNull | 1 | 1 | P3 |
| P-6 方法名不表达真实断言 | 2 | 2 | P3 |
| P-7 测试间隐式依赖 | 0 | 0 | — |
| P-8 无效的负面测试 | 0 | 0 | — |

## 重大正面发现（不是缺陷，是该项目的强项）

1. **Anti-Hollow counter wiring 模式**：项目系统性使用 CountingResolver/CountingMatrix/InMemoryCountingLedger 证明 dispatch path 真实调用注入组件。这是大多数项目缺失的测试类型。
2. **Deny 语义翻转实验通过**：5 个核心安全测试在 deny→allow 翻转后都会失败，证明安全测试有效。
3. **Checkpoint 损坏恢复 + traversal guard**：reliability 包覆盖了文件损坏 section 跳过、4 个入口的 traversal fail-fast、跨实例生存、COMPACTION truncation 的 audit capability，覆盖度极高。
4. **DB-backed 类的 direct SQL 验证**：TestDBDenialLedger/TestDBCheckpointManager/TestDBSessionStore 都用 countDenialRows(sid) 直接查 H2 表验证持久化。

## 维度复核结论

| 发现 | 复核结论 | 理由 |
|------|---------|------|
| [维度21-1]~[21-7] 测试反模式 | **保留 P3** | 反模式命中证据具体，是低风险噪音，可批量清理。 |
| [维度16-1] TestReActAgentExecutor 缺 deny 路径 | **保留 P2（已缓解）** | 此文件自身缺，但被兄弟文件缓解。 |
| [维度16-2] DB 损坏恢复未覆盖 | **保留 P2** | 真实覆盖缺口。 |
| [维度16-3] FileBackedSessionStore traversal 未测 | **保留 P2** | 真实覆盖缺口。 |
| [维度16-4] mock callTools 返回 null | **保留 P2** | 预防性，mock 契约不一致。 |
| [维度16-5] 值对象 P-1 集中 | **保留 P3** | 同 21-3。 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 21-1 | P3 | security/TestChannelKind.java 等 5 个 | 枚举类测试纯计数+valueOf，零缺陷捕获（P-1/P-2） |
| 21-2 | P3 | skill/TestISkillProvider.java 等 7 个 | implementsXxx 元数据测试不验证行为（P-2） |
| 21-3 | P3 | security/TestAuditEvent.java 等 7 个 | 值对象纯 getter/setter 往返测试（P-1） |
| 21-4 | P3 | completion/TestCompletionDecision.java 等 3 个 | concreteTypesExtendXxx 元数据断言（P-2/P-5） |
| 21-5 | P3 | compact/TestMicroCompressionCompactor.java 等 4 个 | 常量集合/阈值镜像断言（P-4） |
| 21-6 | P3 | router/TestRoutingResult.java:57-66 | toStringContainsFields 纯 assertNotNull（P-5/P-6） |
| 21-7 | P3 | skill/TestISkillProvider.java:42-44 | 私有 assertTrue shim（P-2/P-6） |
| 16-1 | P2 | engine/TestReActAgentExecutor.java | 自身不验证 security 拒绝路径（已被兄弟文件缓解） |
| 16-2 | P2 | reliability/TestDBCheckpointManager.java | 缺少 DB 损坏数据恢复/SQL 转义测试 |
| 16-3 | P2 | session/TestFileBackedSessionStore.java | 缺少 path traversal sessionId 测试 |
| 16-4 | P2 | engine/TestReActAgentExecutor.java:330 等 | mock callTools 返回 null，mock 契约不一致 |
| 16-5 | P3 | engine/TestAgentExecutionResult.java | 值对象 P-1 集中度过高（P-1） |
