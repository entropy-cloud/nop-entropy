# 维度 21：单元测试有效性

**目标模块**: nop-ai-agent
**审计依据**: ai-dev/skills/unit-test-antipatterns.md（P-1 到 P-8 八类反模式）
**深挖轮次**: 1（初审充分覆盖）

## 总体结论

模块测试整体质量**偏中上**，明显优于"凑覆盖率"工程。高保护力测试（TestDefaultPermissionProvider/TestActionFingerprint/TestSubAgentPermissionWiring/TestTokenEstimatorWiring/TestPermissionInReActLoop/TestReActAgentExecutor/TestDefaultAgentEngineCancel）断言到具体数值/拒绝原因/事件类型，改实现即失败。**低价值测试比例约 22-28%**，集中在纯枚举/value object、NoOp/PassThrough 实现、assertDoesNotThrow 与 isAssignableFrom 类型断言。

有效测试与低价值测试比例（按方法数估算）：
- A. 高保护力测试 ≈ 60-65%
- B. 中等价值测试（手写 equals/hashCode 验证、真实 fromString 解析）≈ 12-15%
- C. 低价值测试（本报告命中项）≈ 22-28%

## 第 1 轮（初审）

### [维度21-01] TestAgentLifecyclePoint：纯枚举计数 + 逐个 assertNotNull（命中 P-1 + P-5）

- **文件**: `test/.../hook/TestAgentLifecyclePoint.java:13-60`
- **证据片段**:
  ```java
  void enumHasExactly12Values() {
      AgentLifecyclePoint[] values = AgentLifecyclePoint.values();
      assertEquals(12, values.length, ...);
  }
  void coreLayer1PointsExist() {
      assertNotNull(AgentLifecyclePoint.PRE_REASONING);
      assertNotNull(AgentLifecyclePoint.POST_REASONING);
      ...
  }
  ```
- **命中反模式**: P-1（纯枚举计数）+ P-5（assertNotNull 遍历每个枚举成员）
- **严重程度**: P1
- **现状**: 被测类 AgentLifecyclePoint 是裸 enum 无任何业务方法。5 个方法都在确认 Java 编译器保证的事实。
- **风险**: 把核心逻辑改错后测试不会捕获真正的 dispatch-path bug。
- **建议**: 删除该测试文件；如保留契约，合并到 TestDefaultHookRegistry 一行 assertEquals。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度21-02] TestSecurityLevel/TestChannelKind/TestGuardrailMode/TestGuardrailDirection：裸枚举值列举（命中 P-1）

- **文件**: `security/TestSecurityLevel.java:13-35`, `security/TestChannelKind.java:12-25`, `guardrail/TestGuardrailMode.java:9-20`, `guardrail/TestGuardrailDirection.java:9-19`
- **证据片段**:
  ```java
  void valuesMatchDesignSpec() {
      assertEquals(SecurityLevel.STANDARD, SecurityLevel.valueOf("STANDARD"));
      assertEquals(SecurityLevel.ELEVATED, SecurityLevel.valueOf("ELEVATED"));
      assertEquals(SecurityLevel.RESTRICTED, SecurityLevel.valueOf("RESTRICTED"));
  }
  ```
- **命中反模式**: P-1（测试枚举值数量、valueOf 返回同名常量）
- **严重程度**: P1
- **现状**: SecurityLevel/ChannelKind 都是裸 enum 无 fromValue/fallback。valueOf("STANDARD") 是 JDK 默认行为。
- **建议**: 删除这 4 个测试文件；design §5.1/§5.3 已在 TestPassThroughPermissionMatrix/TestDefaultLevelHintsProducer 通过实际 matrix 行为覆盖。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度21-03] TestPrincipal/TestPermission/TestAuditEvent：纯字段赋值往返（命中 P-1）

- **文件**: `security/TestPrincipal.java:16-30`, `security/TestPermission.java:13-35`, `security/TestAuditEvent.java:12-48`
- **证据片段**:
  ```java
  Principal p = new Principal(PrincipalRole.OPERATOR, "ch-1", "tenant-A");
  assertEquals(PrincipalRole.OPERATOR, p.getRole());
  assertEquals("ch-1", p.getChannelId());
  assertEquals("tenant-A", p.getTenantId());
  ```
- **命中反模式**: P-1（getter/setter 往返）
- **严重程度**: P2（保留的 equals/hashCode 测试有边际价值，整体降一级）
- **现状**: 构造器只是 this.x=x，无校验/派生/转换。所有断言等价于"Java 赋值能取回"。
- **建议**: 保留 equals/hashCode 和工厂测试；删 fieldsAreStoredAndAccessible/testConstructionWithAllFields 等纯字段往返。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度21-04] TestSlf4jAuditLogger：5 个方法全部 assertDoesNotThrow，无可观察副作用（命中 P-5 + P-2）

- **文件**: `security/TestSlf4jAuditLogger.java:15-46`
- **证据片段**: 全文件 5 个方法 100% assertDoesNotThrow(...)，无 slf4j appender 捕获。
- **命中反模式**: P-5（assertDoesNotThrow 几乎不可能失败）+ P-2（测元数据而非行为）
- **严重程度**: P2
- **现状**: 把 Slf4jAuditLogger.log 改成 // do nothing 全部测试仍通过——"测试零价值"。
- **建议**: 用 ListAppender 捕获 ILoggingEvent，断言调用次数/message 模板/level。5 个方法可压缩到 1 个。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度21-05] TestNoOpAuditLogger：4 个方法都是 assertDoesNotThrow（命中 P-5 + P-8）

- **文件**: `security/TestNoOpAuditLogger.java:11-37`
- **证据片段**: testLogDoesNotThrow/testLogDenyDoesNotThrow/testLogNullEventDoesNotThrow/testMultipleLogsDoNotThrow 全 assertDoesNotThrow。
- **命中反模式**: P-5 + P-8（空输入只测"不会出错"）
- **严重程度**: P2
- **现状**: NoOp 类所有"测试"等价于对 void 方法调 assertDoesNotThrow。无法捕获"误装配"。
- **建议**: 保留 null 防御 + singleton 断言，共 2 个方法。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度21-06] "implementsIXxx" 系列：测试编译器已保证的类型层级（命中 P-2）

- **文件**: `talent/TestNoOpTalent.java:34-38`, `guardrail/TestNoOpContentGuardrail.java:59-62`, `skill/TestNoOpSkillProvider.java:20-24`, `skill/TestNoOpSkillCurator.java:18-22`, `repair/TestNoOpToolCallRepairer.java:52-55`
- **证据片段**: `assertTrue(ITalent.class.isAssignableFrom(NoOpTalent.class));`
- **命中反模式**: P-2（测试元数据属性而非行为，javac 已保证）
- **严重程度**: P3
- **现状**: 不调用任何业务方法，无输入无输出。删除后 NoOp 类编译不会失败。
- **建议**: 删除全部 5 个 implementsIXxx 方法。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度21-07] TestIModelRouter/TestICompletionJudge/TestISkillProvider：interface 契约自证测试（命中 P-1 + P-4）

- **文件**: `router/TestIModelRouter.java:16-32`, `completion/TestICompletionJudge.java:13-31`, `skill/TestISkillProvider.java:12-27`
- **证据片段**: `RoutingResult result = router.route(...); assertTrue(result instanceof RoutingResult);`
- **命中反模式**: P-1（instanceof 自证）+ P-4（测试和实现说同一件事）
- **严重程度**: P2
- **现状**: 接口契约由 javac 强制。把实现改 return null 测试仍通过（连 not null 都没断言）。
- **建议**: 删除 interfaceContractCanBeImplemented 方法。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度21-08] TestPathAccessDecision.enumHasAllowAndDenyValues：方法级 P-1（其余方法有效）

- **文件**: `security/TestPathAccessDecision.java:13-18`
- **证据片段**: `assertEquals(2, PathAccessDecision.values().length); assertEquals(ALLOW, valueOf("ALLOW"));`
- **命中反模式**: P-1（枚举计数 + valueOf）。同文件 fromStringParsesAllowCaseInsensitive/fromStringDefaultsToDenyForUnrecognized 才有价值。
- **严重程度**: P3
- **建议**: 仅删除 enumHasAllowAndDenyValues 这一个方法。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度21-09] TestSkillModel 两个枚举计数方法（命中 P-1）

- **文件**: `skill/TestSkillModel.java:99-120`
- **证据片段**: topPatternEnumHasPhase1Values/resourceScopeEnumHasPhase1Values 枚举计数+valueOf 列举。
- **命中反模式**: P-1
- **严重程度**: P3
- **现状**: 混在有价值的测试类里"凑数"。
- **建议**: 移除这两个枚举测试方法。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度21-10] TestAgentSession 3 个 RoundTrip 方法纯字段往返（命中 P-1）

- **文件**: `session/TestAgentSession.java:122-146`
- **证据片段**: testParentSessionIdRoundTrip/testPlanIdRoundTrip/testCompactedAtRoundTrip 纯 setter/getter。
- **命中反模式**: P-1
- **严重程度**: P3
- **建议**: 删除这 3 个 RoundTrip 方法（列存在性已在 TestAiAgentSessionTable 覆盖）。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度21-11] TestNoOpContentGuardrail/TestNoOpCompletionJudge/TestPassThroughPostDenialGuard：NoOp 重复证明（命中 P-3 + P-5 + P-8）

- **文件**: `guardrail/TestNoOpContentGuardrail.java:14-50`, `completion/TestNoOpCompletionJudge.java:14-84`, `security/TestPassThroughPostDenialGuard.java:19-52`
- **证据片段**: TestNoOpCompletionJudge 4 个方法输入不同但断言完全相同 `assertTrue(result.isComplete())`。
- **命中反模式**: P-3（只测 happy path 且 NoOp 不变）+ P-5（重复断言同一常量）+ P-8（输入变化输出语义不变）
- **严重程度**: P2
- **现状**: 4 个方法等价于 1 个。值得肯定：每个文件额外测了 factoryReturnsSingleton 和反 hollow 检查。
- **建议**: 每个 NoOp 测试保留 1-2 个核心方法 + singleton 断言。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度21-12] 整体优先级分配：值对象/枚举测试占比过高，与项目优先级表不符

- **文件**: 聚合统计（security 30 测试中 11 个针对值对象；hook 6 中 3 个；guardrail 5 中 4 个）
- **证据片段**: 见 21-01 至 21-11 逐项引用。
- **命中反模式**: 违反 skill 文件优先级排序（核心算法应最详细测试；数据模型仅在有自定义逻辑时测；常量/配置基本不测）
- **严重程度**: P2（结构性问题）
- **现状**: security 包内值对象测试数 ≈ 真实算法测试数，比例失衡。
- **风险**: 未来 contributor 会模仿——继续往数据模型加 getter/setter 测试而非补 dispatch-path 边界。
- **建议**: 更新 skill 文件禁止新增纯枚举/字段测试；落地 21-01 至 21-11 删除项；CI 加 jacoco excludes 让覆盖率难注水。
- **信心水平**: 高
- **复核状态**: 已保留

## 关键正向样例（非发现，供对比）

- security/TestActionFingerprint.java：8 方法全验证哈希决定性/抗碰撞/规范化——每个对应可能的真实 bug。
- engine/TestTokenEstimatorWiring.java:177-213：用 AtomicReference 捕获 actualPromptTokens=777 断言到具体数值。
- engine/TestSubAgentPermissionWiring.java:317-346：fullWiring 断言 write-file/bash 被拒、read-file 通过——"改实现必失败"。
- security/TestNoOpDenialLedger.java:87-115：在 NoOp 测试嵌入 functional 实现（Anti-Hollow 模式）。

## 最终保留项

| 编号 | 严重程度 | 命中反模式 | 文件 | 一句话摘要 |
|------|---------|-----------|------|-----------|
| 21-01 | P1 | P-1,P-5 | TestAgentLifecyclePoint.java | 纯枚举计数+assertNotNull |
| 21-02 | P1 | P-1 | TestSecurityLevel/TestChannelKind/TestGuardrailMode/TestGuardrailDirection | 4 文件裸枚举值列举 |
| 21-03 | P2 | P-1 | TestPrincipal/TestPermission/TestAuditEvent | 纯字段赋值往返 |
| 21-04 | P2 | P-5,P-2 | TestSlf4jAuditLogger.java | 全 assertDoesNotThrow 无副作用捕获 |
| 21-05 | P2 | P-5,P-8 | TestNoOpAuditLogger.java | NoOp 反复证明 |
| 21-06 | P3 | P-2 | 5 个 TestNoOpXxx | implementsIXxx 测编译器保证 |
| 21-07 | P2 | P-1,P-4 | TestIModelRouter/TestICompletionJudge/TestISkillProvider | interface 契约自证 |
| 21-08 | P3 | P-1 | TestPathAccessDecision.java:13-18 | 方法级枚举计数 |
| 21-09 | P3 | P-1 | TestSkillModel.java:99-120 | 两枚举计数方法 |
| 21-10 | P3 | P-1 | TestAgentSession.java:122-146 | 3 个字段往返 |
| 21-11 | P2 | P-3,P-5,P-8 | 3 个 NoOp 测试 | NoOp 重复 happy-path 变体 |
| 21-12 | P2 | 优先级表 | security/hook/guardrail 包 | 值对象测试占比过高 |
