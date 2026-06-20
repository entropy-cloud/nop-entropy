# 维度 21：单元测试有效性

## 检查范围

343 测试类；抽样 ~30 类（NoOp 全系列、enum/value 系列、Wiring 系列、Default method 系列、ReAct 核心）；基线 `ai-dev/skills/unit-test-antipatterns.md`（P-1..P-8）。

## 总体评估

模块测试整体质量较高。核心 ReAct 主循环（TestReActAgentExecutor/TestEndToEndReAct/TestBudgetProviderWiring/TestActionFingerprint/TestSessionIdValidation/TestModelSwitchedMessage 等）都做真实行为验证（断言调用次数/消息顺序/状态转换/防御边界），属"改成错误实现会失败"的有效测试。**但存在系统性问题**：大量 NoOp/enum/value/wiring 测试命中 P-1/P-2/P-4/P-5 反模式，对 NoOp 路径保护力较弱。结构性原因：模块按 Plan 196-242 大量生成 NoOp 默认实现，每个 Phase 1 生成"focused test"倾向枚举每字段/方法分别成方法（计划驱动非风险驱动）。

## 第 1 轮（初审）发现（代表性）

> 以下为命中反模式的代表性发现，每条均引用命中编号+证据+验证结论。完整的 15 条详见初审输出，此处保留最具代表性的并按反模式归类。

### [维度21-01] TestGuardrailMode/TestGuardrailDirection/TestChannelKind/TestAgentLifecyclePoint 纯枚举元数据测试（命中 P-1/P-5）

- **文件**: `guardrail/TestGuardrailMode.java:1-21`；`guardrail/TestGuardrailDirection.java:9-19`；`security/TestChannelKind.java:12-25`；`hook/TestAgentLifecyclePoint.java:1-61`
- **证据片段**:
  ```java
  // TestGuardrailMode
  assertEquals(3, GuardrailMode.values().length);                  // P-1 枚举计数
  assertEquals(GuardrailMode.OFF, GuardrailMode.valueOf("OFF"));   // valueOf 反射
  // TestAgentLifecyclePoint
  assertEquals(12, AgentLifecyclePoint.values().length, ...);      // P-1
  assertNotNull(AgentLifecyclePoint.PRE_CALL);                     // P-5 枚举常量永不为 null
  ```
- **严重程度**: P1（保护力极弱）
- **现状**: 多文件全为枚举数量断言+valueOf+assertNotNull 枚举常量。skill 明确把"测枚举数量"列反例。
- **风险**: 把枚举内部全返回占位实现，这些测试仍通过——只断言常量集合存在不验证任何使用行为。assertNotNull 枚举常量不可能失败（Java 语言保证）。
- **建议**: 删除这些文件/方法；如验证语义差异应在行为测试（如 TestContentGuardrailInReActLoop）以输入输出方式验证。
- **信心水平**: 高
- **误报排除**: 枚举无 fromValue/getLevel 等业务方法（find 确认 main 源码），无自定义逻辑可测。
- **复核状态**: 未复核

### [维度21-02] TestUsageRecord.allFieldsRoundTrip / TestAiMemoryItem / TestAgentExecutionContext / TestSkillModel 纯 getter/setter 往返（命中 P-1）

- **文件**: `usage/TestUsageRecord.java:17-52`；`memory/TestAiMemoryItem.java:84-119`；`engine/TestAgentExecutionContext.java:37-59`；`skill/TestSkillModel.java:17-51,99-120`
- **证据片段**:
  ```java
  // TestUsageRecord
  record.setSessionId("sess-1"); ... assertEquals("sess-1", record.getSessionId());  // set什么get什么，编译器已保证
  ```
- **严重程度**: P1
- **现状**: 多文件依次 set→get→assertEquals 零业务逻辑（UsageRecord 是 @DataBean 无校验）。同文件其他方法（如 AiMemoryItem 的 testTokenEstimateFallbackWhenContentSet 验证 fallback 计算）才是有价值行为测试。
- **风险**: skill 精确反例。setX(getX()) 不可能失败除非 Java 语言坏掉。
- **建议**: 删除纯往返方法，保留 fallback/factory 等真实行为测试。
- **信心水平**: 高
- **误报排除**: 已检查源码是 @DataBean POJO 无校验/派生字段（AiMemoryItem 的 getTokenEstimate 有 -1 fallback，故其 fallback 测试保留）。
- **复核状态**: 未复核

### [维度21-03] TestNoOpAuditLogger 全部 assertDoesNotThrow 弱断言（命中 P-5）

- 与维度16-01 同一发现（TestNoOpAuditLogger 100 次循环只验证不抛）。严重程度 P1（保护力弱）。详见 `16-test-coverage.md` [维度16-01]。

### [维度21-04] TestNoOpSecurityLevelResolver 7 个 ruleTable 测试只验证测试桩（命中 P-4）

- **文件**: `security/TestNoOpSecurityLevelResolver.java:137-251`
- **证据片段**:
  ```java
  static final class DesignSpecRuleTableResolver implements ISecurityLevelResolver { ... }  // TEST-ONLY 桩
  @Test void ruleTableFsWriteUpgradesToElevatedWhenWritesOutsideWorkspace() {
      DesignSpecRuleTableResolver resolver = new DesignSpecRuleTableResolver();  // 桩，非生产代码
      assertEquals(SecurityLevel.ELEVATED, resolver.resolve("fs.write", outside));
  }
  ```
- **严重程度**: P1（覆盖率为零，测试自身代码的回归测试）
- **现状**: 注释称"Anti-Hollow check"，但 7 测试全测 test 文件内的 DesignSpecRuleTableResolver 桩，不测任何 main 源码。grep `DesignSpecRuleTableResolver src/main` 确认桩类不存在于 main。
- **风险**: 把 main 中真实 DefaultSecurityLevelResolver 改成永远返回 STANDARD（破坏升级规则），这 7 测试**仍全部通过**——根本不调用 DefaultSecurityLevelResolver。生产规则表保护由独立 TestDefaultSecurityLevelResolver 承担。这里给文件增加 100+ 行假覆盖率。
- **建议**: 删除 DesignSpecRuleTableResolver 类和全部 7 ruleTable 测试，保留前 4 个真正测 NoOpSecurityLevelResolver 的测试。
- **信心水平**: 高
- **误报排除**: grep 确认 DesignSpecRuleTableResolver 在 main 源码 0 引用。
- **复核状态**: 未复核

### [维度21-05] TestISessionStoreDefaultMethods/TestIAiMemoryStoreDefaultMethods 直接断言常量字符串（命中 P-4）

- **文件**: `session/TestISessionStoreDefaultMethods.java:14-42`；`memory/TestIAiMemoryStoreDefaultMethods.java`
- **证据片段**:
  ```java
  UnsupportedOperationException ex = assertThrows(UOE.class, () -> store.appendEvent("s1", event));
  assertEquals("appendEvent requires VfsSessionStore", ex.getMessage());  // P-4 字符串镜像
  ```
- **严重程度**: P2
- **现状**: assertThrows 部分有价值（验证 default 抛 UOE），但 assertEquals 字符串与实现常量耦合：改 message 措辞（非 bug）测试失败。
- **建议**: 保留 assertThrows，删 assertEquals(<message>)，或改 assertTrue(ex.getMessage().contains(...))。
- **信心水平**: 高
- **误报排除**: ISessionStore main 确实把 message 硬编码在 default 方法，但这就是耦合点。
- **复核状态**: 未复核

### [维度21-06] TestNoOpSkillProvider.implementsISkillProvider / TestHookResult.concreteTypesExtendHookResult 测类型系统（命中 P-2/P-5）

- **文件**: `skill/TestNoOpSkillProvider.java:21-24`；`hook/TestHookResult.java:56-60`；`completion/TestCompletionDecision.java:56-61`；`router/TestRoutingResult.java:57-66`
- **证据片段**:
  ```java
  assertTrue(ISkillProvider.class.isAssignableFrom(NoOpSkillProvider.class));  // P-2 不实现则编译失败，运行时不可能失败
  assertNotNull((HookResult) new HookResult.VetoResult("r"));                  // P-5 new X() 永不为 null
  // TestRoutingResult.toStringContainsFields 方法名说 containsFields 实际只 assertNotNull(str)
  ```
- **严重程度**: P1
- **现状**: 纯类型系统断言零保护力。TestRoutingResult.toStringContainsFields 方法名误导，Object.toString() 永不为 null。
- **建议**: 删除 implementsIXxx / concreteTypesExtend* / toStringContainsFields 方法，保留真实行为方法。
- **信心水平**: 高
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度21-07] TestAgentMessageTopics 大量断言常量格式字符串（命中 P-4）

- **文件**: `message/TestAgentMessageTopics.java:13-40`
- **证据片段**:
  ```java
  assertEquals("agent.sess-1.inbox", AgentMessageTopics.inboxTopic("sess-1"));   // 常量镜像
  ```
- **严重程度**: P2
- **现状**: 4 方法把生产格式字符串常量原样断言一遍。格式改（可能合理 UI/兼容调整）测试必失败但非 bug。同文件 null/empty 拒绝测试才有真实价值。
- **建议**: 合并为"格式契约"测试用 startsWith/endsWith 结构性断言；null 拒绝保留。
- **信心水平**: 高
- **误报排除**: topicNamespaceIsAgentPrefixed 已用常量做结构性断言是好的；纯字符串断言是耦合。
- **复核状态**: 未复核

## 反模式命中统计表

| 反模式 | 命中文件数 | 典型示例 | 优先级 |
|---|---|---|---|
| P-1（纯 getter/setter/枚举计数） | 8 文件 | `assertEquals(3, GuardrailMode.values().length)`；`record.setSessionId("x"); assertEquals("x", record.getSessionId())` | 删除优先 |
| P-2（测元数据/类型系统） | 3 文件 | `assertTrue(ISkillProvider.class.isAssignableFrom(...))`；`assertNotNull((HookResult) new VetoResult(...))` | 删除优先 |
| P-3（只测 happy path） | NoOp/enum 范围未显著命中（行为测试覆盖 fail/tool error 路径） | — | — |
| P-4（与实现/常量耦合） | 3 文件 | `assertEquals("appendEvent requires VfsSessionStore", ex.getMessage())`；7 ruleTable 测桩 | 重构优先 |
| P-5（过度 assertNotNull/assertDoesNotThrow） | 4 文件 | `for(i<100) assertDoesNotThrow(...)`；`assertNotNull(AgentLifecyclePoint.PRE_CALL)` | 删除优先 |
| P-6（方法名不表达意图） | 未发现典型命中（命名规范用 xxxWhenYyyThenZzz） | — | — |
| P-7（测试间隐式依赖） | 未命中（NoOp 测试工厂/字段自包含，无 static 共享可变状态） | — | — |
| P-8（无效负面测试） | 未显著命中（NoOp 的 null/empty 测试在 ITalent/IAuditLogger/IAgentMessenger 场景验证防御性不抛，有边际价值） | — | — |

## 优先级与覆盖分配评估

**强覆盖（保留）**：ReAct 主循环（TestReActAgentExecutor 6 真实场景）、安全防御（TestSessionIdValidation 10+ path traversal 拒绝向量、TestActionFingerprint 确定性+抗碰撞）、并发恢复（TestNoOpCheckpointAndValue 8 线程×200 次并发 thread safety、TestDefaultAgentEngineConcurrencyGuard）、端到端 wiring（TestBudgetProviderWiring/TestConflictStrategyWiring/TestModelSwitchedMessage）、工厂校验（TestNoOpCheckpointAndValue 4 个 checkpointFactoryRejects*）。

**过度测试**：5 enum 测试文件（GuardrailMode/Direction/SecurityLevel/ChannelKind/AgentLifecyclePoint）共 14 方法几乎全元数据；多个 value object 测试（UsageRecord/AiMemoryItem/SkillModel/AuditEvent/AgentExecutionContext）含纯 getter/setter 往返。

**估算比例（抽样）**：有效行为测试约 80%；低价值测试（P-1/P-5）约 15%；与实现耦合（P-4）约 5%。

## 维度复核结论

7 类代表性发现均命中 skill 反模式清单，事实成立。属测试质量改进（保护力提升/噪声削减），非产品行为 bug。与维度16 互补（16 看覆盖缺口，21 看测试有效性）。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 21-01 | P1 | guardrail/TestGuardrailMode.java 等4文件 | 纯枚举元数据测试（P-1/P-5） |
| 21-02 | P1 | usage/TestUsageRecord.java 等4文件 | 纯 getter/setter 往返（P-1） |
| 21-03 | P1 | security/TestNoOpAuditLogger.java | 全 assertDoesNotThrow 弱断言（P-5，同16-01） |
| 21-04 | P1 | security/TestNoOpSecurityLevelResolver.java | 7 ruleTable 测试只验证 test-only 桩（P-4） |
| 21-05 | P2 | session/TestISessionStoreDefaultMethods.java | 断言常量字符串镜像（P-4） |
| 21-06 | P1 | skill/TestNoOpSkillProvider.java 等4文件 | 测类型系统/纯 assertNotNull（P-2/P-5） |
| 21-07 | P2 | message/TestAgentMessageTopics.java | 断言常量格式字符串（P-4） |
