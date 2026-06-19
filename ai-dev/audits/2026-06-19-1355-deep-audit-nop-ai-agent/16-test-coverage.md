# 维度 16（测试覆盖与质量）+ 维度 21（单元测试有效性）— nop-ai-agent

我已经收集了足够的信息来编写这份报告。请允许我整理一下我的发现。

## 维度 16 发现

### [维度16-01] security 包测试"表面覆盖率"虚高：39 个测试文件中相当比例为 P-1/P-4 反模式，对 73 个产品类的有效行为覆盖率被显著夸大

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestChannelKind.java:10-26`, `TestSecurityLevel.java:11-36`, `TestPassThroughPermissionMatrix.java:91-220`, `TestNoOpSecurityLevelResolver.java:99-252`, `TestNoOpDenialLedger.java:124-172`
- **证据片段**:
  ```java
  // TestChannelKind — 整文件 26 行只测两件事
  assertEquals(4, values.length, "ChannelKind must have exactly 4 values per design §5.3");
  assertEquals(ChannelKind.WEBUI, ChannelKind.valueOf("WEBUI"));
  // ChannelKind.java 只有 4 个枚举常量、零业务方法
  
  // TestPassThroughPermissionMatrix:91 — 测试内部类
  static final class DesignSpecRestrictiveMatrix implements IPermissionMatrix { ... }
  // 测试方法 restrictiveMatrixAllowsWebuiAllLevels 等共 8 个测的是这个 TEST-ONLY 内部类，
  // 不是被测产品类 PassThroughPermissionMatrix（其 check() 实际只是 return MatrixDecision.allow()）
  ```
- **严重程度**: P2
- **现状**: 主代码 security 包共 73 个文件，测试 39 个。但其中至少 11 个测试文件的核心 assertions 在测 Java 语言语义（枚举数量、`valueOf` 往返、`@DataBean` 的 getter/setter）或测试文件内部定义的"设计规约"实现（`DesignSpecRestrictiveMatrix` / `DesignSpecRuleTableResolver` / `InMemoryCountingLedger`），并未对产品代码形成有效约束。
- **风险**: 测试计数（39/73 ≈ 53%）会误导审计与维护者认为 security 包覆盖良好；当 P-1/P-4 占多数时，新增产品 bug（例如 `ChannelKind` 被错改为 `class`、`SecurityLevel` 排序被改）几乎都不会被这些测试发现，但绿色 CI 让人误以为安全。
- **建议**: 删除 `TestChannelKind`、`TestSecurityLevel`、`TestPassThroughPermissionMatrix` 中 8 个 `restrictiveMatrix*` 测试、`TestNoOpSecurityLevelResolver` 中 7 个 `ruleTable*` 测试、`TestNoOpDenialLedger.functionalLedgerCountingAndPauseIsObservableThroughContract` —— 这些场景已经被对应的 `TestDefaultPermissionMatrix` / `TestDefaultSecurityLevelResolver` / `TestDefaultDenialLedger` 覆盖（生产实现 + 行为测试）。
- **信心水平**: 确定
- **误报排除**: 这不是"测试太多"的口味问题——保留生产测试（`TestDefaultPermissionMatrix` 等），仅删除与生产测试逻辑重复、且其代码载体（内部类）从不交付的测试。如果未来某 `Default*` 实现被删除，再恢复 anti-hollow 内部类即可。
- **复核状态**: 未复核

### [维度16-02] DefaultPathAccessChecker 未覆盖 symlink 逃逸路径（dimension 13 发现的根因延续到测试侧）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:99-133` + `src/test/java/io/nop/ai/agent/security/TestDefaultPathAccessChecker.java:21-181`
- **证据片段**:
  ```java
  // 生产代码 normalizePathStatic():
  Path normalized = Paths.get(p).normalize();   // 仅 lexical normalize，不解析 symlink
  return normalized.toString().replace("\\", "/");
  
  // 测试覆盖的输入：
  // "/ETC/passwd", "~/.ssh/config", "../../etc/passwd", "C:\\Users\\admin\\.ssh\\id_rsa"
  // —— 全部基于字符串前缀匹配；没有任何用例覆盖 "symlink 指向 /etc/passwd" 的真实文件系统场景
  ```
- **严重程度**: P1
- **现状**: `DefaultPathAccessChecker.normalizePathStatic` 使用 `Paths.get(p).normalize()`，这是**词法规范化**，不会调用 `toRealPath()` 解析符号链接。测试文件 21 个用例全部基于字符串字面量，没有任何 `@TempDir` + `Files.createSymbolicLink` 的端到端验证。dimension 13 若已发现"symlink 可绕过路径白名单"，此处是测试侧的对应缺口。
- **风险**: 攻击者只要在允许目录下放一个 symlink 指向 `~/.ssh/id_rsa` 或 `/etc/passwd`，路径检查会基于 symlink 自身路径（在工作区内、非敏感前缀）通过；后续工具读取该 symlink 文件时会沿链接读取敏感内容。生产代码层无防护，测试层也无任何用例会在引入 `toRealPath()` 后回归。
- **建议**: 在 `TestDefaultPathAccessChecker` 增加 `@TempDir` 用例：在 work-dir 下创建 `link.txt → ~/.ssh/id_rsa`，断言 `checker.checkAccess(workdir/link.txt, ctx).isAllowed() == false`（或至少断言当前行为是有意为之，并打 TODO）。同时同步 dimension 13 决策——是否在产品代码层引入 `toRealPath()`。
- **信心水平**: 很可能（产品代码无 symlink 处理是确证；测试侧零覆盖是确证；是否构成产品 bug 取决于 dimension 13 的判定）
- **误报排除**: 不是"理论攻击场景"——symlink 在工作目录下被恶意 LLM 输出创建是真实存在的能力（只要模型可调用 write-file 系列工具），且既有测试覆盖了 `~/` 展开和 `..` 穿越说明设计意图就是堵这类绕过，唯独漏了 symlink 这一支路。
- **复核状态**: 未复核

### [维度16-03] DBMessageService 的 markConsumed 失败路径与 CLAIMED 状态恢复路径完全无测试（dimension 14-02 在测试侧的对应缺口）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:384-406` + `src/test/java/io/nop/ai/agent/message/TestDBMessageService.java:32-348`
- **证据片段**:
  ```java
  // 生产代码 markConsumed:
  try (Connection conn = ...; PreparedStatement ps = ...) {
      ps.setInt(1, AiAgentMessageTable.STATUS_CONSUMED);
      ...
      ps.executeUpdate();
  } catch (SQLException e) {
      LOG.error("nop.ai.agent.message.db-mark-consumed-error:sid={}", sid, e);
      // 吞掉异常 —— 消息永远停留在 CLAIMED 状态
  }
  
  // findPending 只 SELECT STATUS=PENDING —— CLAIMED 状态的消息不会被任何后续轮次重投
  ```
- **严重程度**: P1
- **现状**: `TestDBMessageService` 9 个用例覆盖了正常 send/poll/competing-consumer/restart/ConsumeLater/cancel/close，但**没有任何用例**验证：(a) `markConsumed` 抛 `SQLException` 时消息的命运；(b) 消费者在 `claimMessage` 成功后、`markConsumed` 前崩溃后，消息的命运。`markConsumed` 当前实现是吞异常仅记日志（line 403-405），意味着任何数据库瞬时故障（连接断、死锁、磁盘满）都会让消息**永久滞留在 CLAIMED 状态**，且 `findPending` 只看 PENDING，没有超时回收机制。
- **风险**: 这就是 dimension 14-02 发现的"消息丢失"风险，对应测试侧的盲点：当前测试套件不强制要求任何回收机制存在，未来重构 `markConsumed` 时也无回归保护。生产环境部署若遇到一次 DB 抖动，会丢失一批 agent 间消息且无告警（只有 ERROR 日志）。
- **建议**: (1) 增加 `markConsumedFailsLeavesMessageClaimedAndRedeliveredAfterRecovery`——通过自定义 DataSource 注入故障，断言失败发生 + 修复后消息恢复投递；(2) 增加 `consumerCrashAfterClaimRedeliversMessage`——通过关闭 service 模拟崩溃，断言超时回收（如果设计有）；如果产品决策是"不回收"，则至少增加文档化测试 `claimedMessagesAreNotAutoRedelivered` 固化当前行为。两选一，必须有一个测试锁定行为契约。
- **信心水平**: 很可能（生产代码已确证；测试侧 `grep -i "claimed\|markConsumed\|crash"` 在 `TestDBMessageService*` 三文件中零命中）
- **误报排除**: 不是"测覆盖率数字主义"——这是真实数据完整性场景，且 dimension 14 已经独立指出产品代码缺陷，测试侧缺口是修复产品代码后必须配套的回归保护。
- **复核状态**: 未复核

### [维度16-04] TestDBMessageService.competingConsumersOnlyOneInstanceConsumes 的"无重复投递"断言与"5 条都被消费"断言是同一条算术表达式，无去重保护

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/message/TestDBMessageService.java:135-176`
- **证据片段**:
  ```java
  waitForCondition(() -> (aReceived.get() + bReceived.get()) == 5, 5, TimeUnit.SECONDS);
  
  assertEquals(5, aReceived.get() + bReceived.get(),
          "all 5 messages should be consumed across both instances");
  assertTrue(aReceived.get() + bReceived.get() == 5,
          "no duplicate delivery: a=" + aReceived.get() + ", b=" + bReceived.get());
  // ↑ 第二条 assertion 与第一条完全是同一表达式，未独立验证"无重复"
  ```
- **严重程度**: P2
- **现状**: 该测试声称验证两点：(a) 5 条全部被消费、(b) 无重复投递。但代码用 `aReceived.get() + bReceived.get() == 5` 同时表达两个意图。如果同一条消息被两个消费者各消费一次（双重投递），只要另一条消息无人消费，总数仍为 5，**两个断言都会通过**。
- **风险**: 真正的"无重复投递"契约（CAS-claim 的核心保证）当前没有独立断言保护。未来若 `claimMessage` 的 SQL `WHERE STATUS=PENDING` 退化（例如被错改为不加条件），重复投递发生时这个测试不会失败。
- **建议**: 用 per-messageId `Set<String> delivered` 替换计数器，断言 `delivered.size() == 5` 且每个 messageId 仅出现一次。或用 spy 消费者记录每条 `sid` 列表，断言无重复。
- **信心水平**: 确定
- **误报排除**: 这不是"吹毛求疵的写法偏好"——`AtomicInteger` 计数器无法区分"5 条各一次"与"3 条各两次 + 0 条"，后者就是失败场景；用 `Set<String>` 是直接修正而非风格优化。
- **复核状态**: 未复核

### [维度16-05] TestReActAgentExecutor 只覆盖 happy path + LLM error，未测 retry-loop 的 FALLBACK 分支与 circuit-breaker 拒绝分支

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestReActAgentExecutor.java:40-348`（聚焦测试）对照 `src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1278-1374`
- **证据片段**:
  ```java
  // TestReActAgentExecutor 测试方法清单：
  // testNoToolCallImmediateReturn / testSingleToolCallLoop / testMultipleToolCalls
  // testMaxIterationsReached / testLlmCallFailure / testToolExecutionError
  
  // ReActAgentExecutor.java:1319-1364 —— 测试侧未触及的关键分支：
  if (outcome.isFallback()) {
      ChatOptions fallbackOptions = modelRouter.getFallback(routedOptions);
      if (fallbackOptions == null) { throw new NopAiAgentException(...); }
      routedOptions = fallbackOptions;
      request.setOptions(routedOptions);
      attempt = 0;            // 重置 retry 预算
      lastError = null;
      continue;
  }
  ```
- **严重程度**: P2
- **现状**: `TestReActAgentExecutor` 是 ReAct 主循环的聚焦单测，6 个测试方法全部走 happy path 或最浅层错误（LLM 返回 error、tool 返回 error）。复杂的 retry-loop 内部分支——`outcome.isRetry()` 触发的 sleep+重试、`outcome.isFallback()` 触发的模型切换 + attempt 重置、`circuitBreaker.allowCall()==false` 触发的 fail-fast——都**只在 wiring 层面**（`TestRetryPolicyWiring` / `TestCircuitBreakerWiring` / `TestStandardRetryPolicyEndToEnd`）有覆盖，缺少在 `ReActAgentExecutor.execute()` 入口的聚焦断言。
- **风险**: wiring 测试断言"组件被调用"，但不验证循环内部的 attempt 重置、`routedOptions` 重新赋值、`lastError=null` 等状态变迁是否正确。重构 retry 块时（这是 3327 行文件中最复杂的 100 行之一），错误的状态变迁不会被聚焦测试捕获。
- **建议**: 在 `TestReActAgentExecutor` 增加 3 个用例：(a) `retryLoopRetriesAndSucceedsOnTransientFailure`——chatService 第一次抛 `IOException` 第二次返回成功，断言 `result.getStatus()==completed` 且 chatService 被调用 2 次；(b) `fallbackSwitchesModelAndResetsAttempt`——chatService 在主模型上抛错、retryPolicy 返回 FALLBACK、router 提供回退模型，断言第二次调用使用的 options 是回退模型；(c) `openCircuitRejectsBeforeLlmCall`——breaker 返回 false，断言 `NopAiAgentException` 被抛出且 chatService 从未被调用。
- **信心水平**: 很可能
- **误报排除**: 不是"测试重复"——wiring 测试与 executor 聚焦测试是不同抽象层，前者验证依赖注入链路，后者验证循环内部状态机。两者都必要。
- **复核状态**: 未复核

### [维度16-06] 测试覆盖分布与"核心算法 vs 数据模型"的优先级分配原则存在系统性偏离

- **文件**: 涉及 `src/test/java/io/nop/ai/agent/security/`（39 文件）、`skill/`（11）、`memory/`（10）、`budget/`（5）、`team/`（43）、`engine/`（71）
- **证据片段**:
  ```
  主代码包大小 vs 测试包大小（按手写文件计）：
    security: 73 主 / 39 测试 — 测试/主 ≈ 0.53
    engine:   23 主 / 71 测试 — 测试/主 ≈ 3.09（最高密度，正确）
    team:     54 主 / 43 测试 — 测试/主 ≈ 0.80
    plan:     42 主 / 1  测试 — 测试/主 ≈ 0.02（极低，但全是 record-mapping @DataBean）
    memory:   17 主 / 10 测试
    budget:    3 主 / 5  测试
  
  security 39 个测试中有效行为测试的文件占比：
    - 高质量行为测试（DefaultXxx / FingerprintPostDenialGuard / ActionFingerprint / Sandbox / MultiTenant 等）≈ 22 个
    - 纯值对象/枚举/NoOp/Anti-Hollow 重复测试 ≈ 17 个（dimension 21 详述）
  ```
- **严重程度**: P3
- **现状**: 依据 `unit-test-antipatterns.md` 的优先级清单（核心算法 > 集成点 > 防御性代码 > 数据模型 > 常量），`engine/`、`team/flow/`、`message/`、`runtime/lock/`、`compact/`、`reliability/` 的测试投入与重要性匹配（核心算法路径有最多测试）；但 `security/` 的投入结构有偏差——大量测试资源用在了无业务方法的枚举与值对象上（`ChannelKind` / `SecurityLevel` / `PathAccessDecision` 的枚举部分 / `PassThroughPermissionMatrix` 的 anti-hollow 部分）。
- **风险**: 维护成本：每加一个 security 枚举常量就要更新一个 `assertEquals(N, values.length)` 测试；新人会模仿这种风格继续写 P-1 测试，进一步稀释有效覆盖率。
- **建议**: 在 `ai-dev/skills/unit-test-antipatterns.md` 增加一条 nop-ai-agent 模块特定规则："security/ 包下：枚举（ChannelKind/SecurityLevel/AuditDecision/DenialReason/ApprovalDenialKind/PathAccessDecision）除非有 `fromString`/`fromValue` 等业务方法，否则禁止为其单独写 Test 类"。配合一次清理 PR 删除现有 P-1 测试。
- **信心水平**: 确定
- **误报排除**: 不是"反对测试覆盖"——是反对用低价值测试膨胀计数。`TestPathAccessDecision.fromStringDefaultsToDenyForUnrecognized` 等行为测试要保留，只删 `enumHasAllowAndDenyValues` 这种。
- **复核状态**: 未复核

---

## 维度 21 发现（依据 unit-test-antipatterns.md 的 P-1 到 P-8）

### [维度21-01] TestChannelKind 整文件命中 P-1（纯枚举数量+valueOf 往返）

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestChannelKind.java:13-25`
- **证据片段**:
  ```java
  @Test
  void hasExactlyFourChannels() {
      ChannelKind[] values = ChannelKind.values();
      assertEquals(4, values.length,
              "ChannelKind must have exactly 4 values per design §5.3");
  }

  @Test
  void valuesMatchDesignSpec() {
      assertEquals(ChannelKind.WEBUI, ChannelKind.valueOf("WEBUI"));
      assertEquals(ChannelKind.API, ChannelKind.valueOf("API"));
      // ChannelKind.java 自身只有 4 个枚举常量，零业务方法
  }
  ```
- **严重程度**: P3
- **现状**: 命中 **P-1**（"测试枚举值数量"+"assertNotNull 遍历每个枚举成员"两个反模式的等价变体）。`ChannelKind` 是 27 行的纯枚举（line 22-27：`WEBUI, API, DM, GROUP`），无 `fromValue`、无字段、无方法。`ChannelKind.valueOf("WEBUI") == ChannelKind.WEBUI` 是 `Enum.valueOf` 的语言保证。
- **风险**: 把核心逻辑改成错误（例如把 `WEBUI` 改名为 `WEB_UI`）后这个测试会失败，但更常见的真实 bug（例如设计要求新增 `SLACK` 渠道、或权限矩阵对 `GROUP` 的策略改变）这些测试不会保护。
- **建议**: 删除整个文件。design §5.3 的渠道×level 表已经被 `TestDefaultPermissionMatrix` 的真实矩阵断言覆盖（line 19-71 of that file）。
- **信心水平**: 确定
- **误报排除**: 不是"任何 enum 测试都该删"——`TestPathAccessDecision` 的 `fromStringDefaultsToDenyForUnrecognized` 是合法的，因为 `PathAccessDecision.fromString` 有真实 fallback 逻辑。`ChannelKind` 没有任何业务方法。
- **复核状态**: 未复核

### [维度21-02] TestSecurityLevel 部分命中 P-1（`valuesMatchDesignSpec`），但 `ordinalOrderIsStandardElevatedRestricted` 提供弱保护

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestSecurityLevel.java:14-35`
- **证据片段**:
  ```java
  @Test
  void valuesMatchDesignSpec() {                 // ← P-1
      assertEquals(SecurityLevel.STANDARD, SecurityLevel.valueOf("STANDARD"));
      assertEquals(SecurityLevel.ELEVATED, SecurityLevel.valueOf("ELEVATED"));
      assertEquals(SecurityLevel.RESTRICTED, SecurityLevel.valueOf("RESTRICTED"));
  }

  @Test
  void ordinalOrderIsStandardElevatedRestricted() {  // ← 弱保护
      assertEquals(0, SecurityLevel.STANDARD.ordinal());
      assertEquals(1, SecurityLevel.ELEVATED.ordinal());
      assertEquals(2, SecurityLevel.RESTRICTED.ordinal());
      assertEquals(-1, SecurityLevel.STANDARD.compareTo(SecurityLevel.ELEVATED));
  }
  ```
- **严重程度**: P3
- **现状**: 命中 **P-1**（`valuesMatchDesignSpec` 完全等同 `Enum.valueOf` 的语言保证）。`hasExactlyThreeLevels` 与 `ordinalOrder*` 提供弱保护：生产代码 `DefaultPermissionMatrix.check` 通过 `level.ordinal() <= cap.ordinal()` 比较（在 TestDefaultPermissionMatrix.elevatedAllowedExceptGroup 中被间接覆盖），所以 ordinal 顺序错乱会让生产测试失败。但 `TestSecurityLevel.ordinalOrder*` 是 5 行独立断言，其保护作用与 `TestDefaultPermissionMatrix` 完全重叠。
- **风险**: 删了无害，留着会让新人误以为"加新枚举值时只要同步更新这里就行"——但实际上更新点在 `DefaultPermissionMatrix.channelCap` 与 `DefaultSecurityLevelResolver` 的 switch 语句里。
- **建议**: 删除 `valuesMatchDesignSpec`；保留 `ordinalOrderIsStandardElevatedRestricted`（如有团队偏好），但加注释指向 `TestDefaultPermissionMatrix` 才是真实保护点。
- **信心水平**: 很可能
- **误报排除**: 不是 P-1 全部——ordinal 顺序确有产品代码消费，所以测试不是完全无价值，只是与上游测试重复。
- **复核状态**: 未复核

### [维度21-03] TestPassThroughPermissionMatrix 的 8 个 `restrictiveMatrix*` 测试命中 P-4（测试与实现耦合 + 测试 test-only 内部类）

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestPassThroughPermissionMatrix.java:91-220`
- **证据片段**:
  ```java
  // 测试文件内部定义的"设计规约"实现：
  static final class DesignSpecRestrictiveMatrix implements IPermissionMatrix {
      @Override
      public MatrixDecision check(ChannelKind channel, Principal principal, SecurityLevel level) {
          if (level == SecurityLevel.RESTRICTED && principal != null
                  && principal.getRole() == PrincipalRole.OPERATOR) {
              return MatrixDecision.allow();
          }
          // ... 完整复刻 design §5.3 的渠道×level 表
      }
  }

  @Test
  void restrictiveMatrixDeniesApiRestrictedForUser() {  // ← P-4
      IPermissionMatrix matrix = new DesignSpecRestrictiveMatrix();
      MatrixDecision d = matrix.check(ChannelKind.API, Principal.user(), SecurityLevel.RESTRICTED);
      assertTrue(d.isDenied(), "API + RESTRICTED + user must be denied");
      // ↑ 测的是上面那个 TEST-ONLY 内部类，不是被声明为测试目标的 PassThroughPermissionMatrix
  }
  ```
- **严重程度**: P2
- **现状**: 命中 **P-4**（"测试与实现高度耦合"——测试内嵌了一个完整的设计规约实现，断言它自己实现的逻辑）+ 部分 **P-2**（测试元数据属性：注释声称"Anti-Hollow 证明 contract 不是空壳"，但真正的空壳检测应该用真实生产实现）。被测产品类 `PassThroughPermissionMatrix.check` 总共只有 `return MatrixDecision.allow();` 一行（30 行源文件），8 个 `restrictiveMatrix*` 测试与它无任何关系——它们都在测 `DesignSpecRestrictiveMatrix`，后者只存在于测试源码中、从不被生产代码引用。
- **风险**: 当产品侧的 `DefaultPermissionMatrix`（被 `TestDefaultPermissionMatrix` 覆盖）的设计表与 `DesignSpecRestrictiveMatrix` 漂移时，两套测试会给出矛盾信号；新人不知道哪个是 source of truth。把 `DefaultPermissionMatrix` 改错时，`TestDefaultPermissionMatrix` 会失败，但 `TestPassThroughPermissionMatrix` 仍绿——产生误判。
- **验证保护力**: 把 `DesignSpecRestrictiveMatrix.channelCap` 的 `case GROUP: STANDARD` 改为 `ELEVATED`（这等价于"把核心逻辑改成错误"），`restrictiveMatrixDeniesGroupElevatedAndRestricted` 会失败，但**任何产品代码都没改**——证明这 8 个测试保护的是测试 fixture，不是产品代码。
- **建议**: 删除 `DesignSpecRestrictiveMatrix` 内部类及其 8 个测试方法（line 91-207）；保留 4 个 `passThrough*` 测试（line 30-67，确证 pass-through 默认行为）和 `matrixDecisionFactoriesProduceCorrectAllowedFlag`（验证 `MatrixDecision` 工厂，但应迁到 `TestMatrixDecision`）。
- **信心水平**: 确定
- **误报排除**: 不是 P-2 "测试常量"——常量测试确实低价值但不构成误导；这里的核心问题是 8 个测试用 220 行代码测一份**只存在于测试源码中**的实现，伪装成对生产代码的覆盖。
- **复核状态**: 未复核

### [维度21-04] TestNoOpSecurityLevelResolver 的 8 个 `ruleTable*` 测试命中 P-4（同 03 模式）

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestNoOpSecurityLevelResolver.java:99-252`
- **证据片段**:
  ```java
  static final class DesignSpecRuleTableResolver implements ISecurityLevelResolver {
      @Override
      public SecurityLevel resolve(String actionKind, LevelHints hints) {
          LevelHints h = hints != null ? hints : LevelHints.defaults();
          if (eq(actionKind, "fs.read") || ...) return SecurityLevel.STANDARD;
          // ... 完整复刻 design §5.1 升级表
      }
  }

  @Test
  void ruleTableShellExecUpgradesByUntrustedSourceAndHighImpact() {  // ← P-4
      DesignSpecRuleTableResolver resolver = new DesignSpecRuleTableResolver();
      // ...
      assertEquals(SecurityLevel.RESTRICTED, resolver.resolve("shell.exec", highImpactTrusted),
              "shell.exec high impact → RESTRICTED (overrides trusted source)");
  }
  ```
- **严重程度**: P2
- **现状**: 命中 **P-4**。被测产品类 `NoOpSecurityLevelResolver.resolve()` 一行实现 `return SecurityLevel.STANDARD;`（252 行测试文件只覆盖这一行的 4 种输入组合）。8 个 `ruleTable*` 测试全部针对 `DesignSpecRuleTableResolver`（test-only）。设计规约已被 `TestDefaultSecurityLevelResolver` 完整覆盖（line 16-80 测同样的 fs.read/fs.write/shell.exec/network.fetch/unknown + trusted/untrusted 组合）。
- **风险**: 同 21-03：当 `DefaultSecurityLevelResolver` 与 `DesignSpecRuleTableResolver` 设计表漂移时产生误判；维护两份相同逻辑。
- **验证保护力**: 把 `DesignSpecRuleTableResolver` 中 `case "shell.exec"` 的高 impact 分支返回 ELEVATED 而非 RESTRICTED，`ruleTableShellExecUpgradesByUntrustedSourceAndHighImpact` 失败，但产品代码零变更——证明保护力指向测试 fixture。
- **建议**: 删除 `DesignSpecRuleTableResolver` 内部类及其 8 个测试方法（line 80-251）；保留 4 个 `noOp*` 测试（确证 NoOp 行为 + singleton）。
- **信心水平**: 确定
- **误报排除**: 同 21-03。
- **复核状态**: 未复核

### [维度21-05] TestNoOpDenialLedger 的 `functionalLedgerCountingAndPauseIsObservableThroughContract` 命中 P-4 + P-2

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestNoOpDenialLedger.java:88-172`
- **证据片段**:
  ```java
  /**
   * Test-internal functional ledger: ... Used to prove the IDenialLedger
   * contract surface can carry real counting/pausing state.
   */
  static final class InMemoryCountingLedger implements IDenialLedger {
      // ... 完整实现 recordDenial/isPaused/getDenialCount/reset（48 行）
  }

  @Test
  void functionalLedgerCountingAndPauseIsObservableThroughContract() {  // ← P-4
      IDenialLedger counting = new InMemoryCountingLedger(2);
      // ... 测 InMemoryCountingLedger 的 threshold 行为
      assertTrue(counting.isPaused("s1"));
  }
  ```
- **严重程度**: P2
- **现状**: 命中 **P-4**。被测产品类 `NoOpDenialLedger`（21 行）所有方法返回 0/false/不抛异常，前 6 个测试已完整覆盖。`functionalLedgerCountingAndPauseIsObservableThroughContract` 用 `InMemoryCountingLedger`（test-only 内部类）验证计数 + threshold + pause + reset 行为——而生产侧 `DefaultDenialLedger` 已被 `TestDefaultDenialLedger`（87 行）完整覆盖**完全相同的语义**（threshold=2、count++、isPaused 等）。
- **风险**: 同 21-03/04：两份独立实现 + 两份独立测试，未来漂移风险。`InMemoryCountingLedger` 用 `ConcurrentHashMap` + `AtomicInteger`，`DefaultDenialLedger` 用什么尚未审计，但任何差异都会引发"哪份是 source of truth"的争议。
- **建议**: 删除 `InMemoryCountingLedger` 内部类及 `functionalLedgerCountingAndPauseIsObservableThroughContract`（line 82-172）。`denialRecordFactoryCapturesAllFields` 等值对象测试可保留或迁移到 `TestDenialRecord`。
- **信心水平**: 确定
- **误报排除**: 这不是"测试可选"——而是要求**测试生产实现**而非测试 test-only 替身。
- **复核状态**: 未复核

### [维度21-06] TestISkillProvider 整文件命中 P-2 tautology（测自己写的匿名实现）

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/skill/TestISkillProvider.java:13-34`
- **证据片段**:
  ```java
  @Test
  void interfaceContractCanBeImplemented() {
      ISkillProvider provider = new ISkillProvider() {            // ← 测试自己写实现
          private final java.util.List<SkillModel> skills = java.util.List.of(buildSkill("a"));
          @Override
          public Collection<SkillModel> getSkills() {
              return skills;
          }
      };

      Collection<SkillModel> skills = provider.getSkills();
      assertNotNull(skills);
      assertEquals(1, skills.size());
      assertEquals("a", skills.iterator().next().getName());
      // ↑ 断言测试代码自己 hard-coded 的 "a"
  }

  @Test
  void implementationMayReturnEmpty() {
      ISkillProvider provider = () -> java.util.Collections.emptyList();  // ← 又一个匿名实现
      assertNotNull(provider.getSkills());
      assertTrue(provider.getSkills().isEmpty());
  }
  ```
- **严重程度**: P3
- **现状**: 命中 **P-2**（"测试元数据属性而非行为"的变体——这里测试的是"接口可以被实现"这一 Java 语言事实）。两个测试都用匿名内部类实现 `ISkillProvider`，然后断言该匿名实现返回**测试代码自己 hard-coded 的内容**。这是循环证明：测试 == 实现。
- **风险**: 把 `ISkillProvider` 接口签名改错（例如把 `getSkills()` 改成 `getSkillz()`）会让这个测试编译失败——但那不是"测试发现 bug"，那是"编译器发现 bug"。零运行时价值。
- **建议**: 删除整个文件。`ISkillProvider` 接口的真实覆盖应该由 `TestNoOpSkillProvider`（默认实现）+ `TestFileSystemSkillProvider`（生产实现）+ `TestSkillEngineInReActLoop`（端到端）承担。
- **信心水平**: 确定
- **误报排除**: 不是"测试接口无意义"——`TestLayer23SecureDefaultImpls.java` 是合法的接口默认行为测试，因为它测试**实际存在的默认实现**；这里测的是测试代码自己写的匿名实现，没有产品代码被验证。
- **复核状态**: 未复核

### [维度21-07] TestAuditEvent 多个方法命中 P-1（纯字段访问验证）

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestAuditEvent.java:12-108`
- **证据片段**:
  ```java
  @Test
  void testConstructionWithAllFields() {                  // ← P-1
      AuditEvent event = new AuditEvent("sess-1", "agent-x", "actor-1",
              "calculator", AuditDecision.ALLOW, null, "allow_all", null, ts);
      assertEquals("sess-1", event.getSessionId());
      assertEquals("agent-x", event.getAgentName());
      // ... 9 个字段逐一 getter 校验
  }

  @Test
  void testImmutability() {                                // ← P-1（命名误导）
      AuditEvent event = new AuditEvent("s", "a", "act", "t",
              AuditDecision.ALLOW, "r", "rule", "/p", 1L);
      assertEquals("s", event.getSessionId());
      // ... 又一遍 9 个字段 getter 校验，与 testConstructionWithAllFields 完全重复
  }

  @Test
  void testWithPathVariable() {                            // ← P-1 弱化版
      AuditEvent event = new AuditEvent(...);
      assertEquals("/etc/passwd", event.getPath());        // 只测一个字段
  }
  ```
- **严重程度**: P3
- **现状**: 命中 **P-1**。`AuditEvent` 源码 102 行，是纯不可变值类（构造器 + 9 个 getter + `Objects.equals/hash` 生成的 equals/hashCode + toString）。`testConstructionWithAllFields` 与 `testImmutability` 测试内容**完全相同**（都是构造后调 getter 断言原值返回），命名暗示后者测不可变性，但实际未尝试任何修改路径（也不可能——字段都是 final）。`testDenyEvent`、`testWithPathVariable`、`testNullableActorId` 共测 5 个字段读取，互相重复。
- **风险**: 真正有价值的测试是 `testEquality` / `testInequality`（验证 equals 在 decision 变化时区分），其余 5 个测试是噪音。新加字段时需要同步改 4-5 处断言，维护成本与价值不成比例。
- **建议**: 保留 `testEquality` + `testInequality`；删除其余 6 个测试方法，或合并为单个 `testRoundTripAndEquality`。
- **信心水平**: 确定
- **误报排除**: 不是"反对 @DataBean 测试"——`TestApprovalDecision`（同样形态）是合法的，因为 `ApprovalDecision.approve(null)` 有"approver 默认 'system'"的真实业务逻辑、`deny(null,...)` 有"kind 默认 OTHER"的真实业务逻辑。`AuditEvent` 的工厂只是 `new AuditEvent(...)`，零业务逻辑。
- **复核状态**: 未复核

### [维度21-08] TestPermission 整文件命中 P-1（Permission 是 64 行纯不可变值类）

- **文件**: `nop-ai/nop-entropy-wt/nop-entropy-master/nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestPermission.java:14-65` + `src/main/java/io/nop/ai/agent/security/Permission.java:5-64`
- **证据片段**:
  ```java
  // Permission.java 源码（关键）：
  public static Permission allow() { return new Permission(true, null, null); }
  public static Permission deny(String reason) { return new Permission(false, reason, null); }
  public static Permission deny(String reason, String ruleId) { return new Permission(false, reason, ruleId); }
  // 三个工厂零逻辑，只是构造器糖
  
  // 测试：
  @Test
  void testAllowFactory() {
      Permission p = Permission.allow();
      assertTrue(p.isAllowed());           // 工厂 allow() 直接传 true 给构造器
      assertNull(p.getReason());
      assertNull(p.getMatchedRuleId());
  }
  // 其余 5 个测试同模式：构造 + 断言构造参数被存下来
  ```
- **严重程度**: P3
- **现状**: 命中 **P-1**。Permission 是 64 行纯不可变值类，equals/hashCode 用 `Objects.equals/hash`（标准库保证）。6 个测试方法覆盖：3 个工厂 + equals + hashCode + toString。其中 `testEquals` 验证 `Permission.allow().equals(Permission.allow())` 等——这有价值，但只需要 1 个用例不是 4 行。
- **风险**: 工厂签名变更时需要同步多处断言，但不保护任何业务逻辑。
- **建议**: 删除 `testAllowFactory`、`testDenyFactoryWithReason`、`testDenyFactoryWithReasonAndRuleId`、`testHashCode`、`testToString`；保留 `testEquals`（合并 factory 用例进它的 setup）。
- **信心水平**: 确定
- **误报排除**: 同 21-07，对照 `TestApprovalDecision`（有 `approve(null)` → "system" 的真实业务逻辑）。
- **复核状态**: 未复核

### [维度21-09] TestSkillModel 多方法命中 P-1（fieldRoundTrip、defaultsAreNullUntilSet、enum 计数）

- **文件**: `nop-ai/nop-entropy-wt/nop-entropy-master/nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/skill/TestSkillModel.java:18-120`
- **证据片段**:
  ```java
  @Test
  void fieldRoundTrip() {                                 // ← P-1 典范
      SkillModel skill = new SkillModel();
      skill.setName("code-review");
      // ... 8 个 setter
      assertEquals("code-review", skill.getName());
      // ... 8 个 getter，断言返回 setter 刚刚传入的值
  }

  @Test
  void defaultsAreNullUntilSet() {                        // ← P-1
      SkillModel skill = new SkillModel();
      assertNull(skill.getName());
      // ... 7 个字段断言默认 null（Java 语言保证）
  }

  @Test
  void topPatternEnumHasPhase1Values() {                  // ← P-1（与 TestChannelKind 同）
      assertEquals(6, SkillTopPattern.values().length);
      assertEquals(SkillTopPattern.PREPARE, SkillTopPattern.valueOf("PREPARE"));
      // ...
  }
  ```
- **严重程度**: P3
- **现状**: 命中 **P-1**。`SkillModel` 是 `@DataBean`（默认 Lombok 风格生成 getter/setter）。文件 9 个测试方法中 5 个是纯 P-1（`fieldRoundTrip`、`defaultsAreNullUntilSet`、`topPatternEnumHasPhase1Values`、`resourceScopeEnumHasPhase1Values`、隐含在 builder 测试中的部分）。4 个是真实行为测试（`collectToolDependenciesAddsNonNullNonEmpty` 过滤 null/空、`collectResourceScopeHandlesNull` 防御性、`copyTagsReturnsMutableCopy` 验证独立性、`copyTagsReturnsEmptyForNull`）。
- **风险**: 测试文件给读者错觉"SkillModel 是有逻辑的类"，实际逻辑只在 `collectToolDependencies` 和 `copyTags` 中。
- **建议**: 删除 5 个 P-1 测试方法；保留 4 个行为测试。
- **信心水平**: 确定
- **误报排除**: 保留的 4 个测试是真实行为——`collectToolDependencies` 过滤 `Arrays.asList("read_file", null, "", "git_diff", "write_file")` 是 P-3 列表里"防御性代码 → 触发防御的输入"的典范。
- **复核状态**: 未复核

### [维度21-10] TestAiMemoryItem 部分命中 P-1（5 个方法）

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/memory/TestAiMemoryItem.java:14-119`
- **证据片段**:
  ```java
  @Test
  void testDefaultValues() {                              // ← P-1
      AiMemoryItem item = new AiMemoryItem();
      assertEquals(0, item.getPriority());               // Java int 默认 0
      assertFalse(item.isPinned());                      // Java boolean 默认 false
      assertNull(item.getChecksum());                    // Java Object 默认 null
  }

  @Test
  void testAllNewFieldsSetAndGet() {                      // ← P-1
      item.setPriority(5);
      // ... 6 个 setter
      assertEquals(5, item.getPriority());
      // ... 6 个 getter
  }

  @Test
  void testOriginalFieldsUnchanged() {                    // ← P-1
      item.setKey("k1");
      // ... 3 个 setter
      assertEquals("k1", item.getKey());
      // ... 3 个 getter
  }
  ```
- **严重程度**: P3
- **现状**: 部分命中 **P-1**。`AiMemoryItem` 是 `@DataBean`，但有 2 个真实业务方法：`getTokenEstimate()`（fallback 计算 `content.length() / 4`）和 `getLastAccessTime()`（fallback 到 `createTime`）。文件 9 个测试中 5 个是纯 P-1（`testDefaultValues`、`testTokenEstimateExplicitValue`、`testTokenEstimateExplicitZero`、`testAllNewFieldsSetAndGet`、`testOriginalFieldsUnchanged`）；3 个是真实行为测试（`testTokenEstimateFallbackWhenContentSet`、`testTokenEstimateFallbackWhenContentNull`、`testLastAccessTimeFallbackToCreateTime`、`testLastAccessTimeExplicitValue`、`testLastAccessTimeNullWhenCreateTimeAlsoNull`）。
- **风险**: 同 21-09。新加字段需同步改 P-1 测试，但不保护业务逻辑。
- **建议**: 删除 5 个 P-1 测试方法，保留 4 个 fallback 行为测试。
- **信心水平**: 确定
- **误报排除**: `testTokenEstimateFallbackWhenContentSet` 是真实测试——`item.setContent("12345678"); assertEquals(2, item.getTokenEstimate())` 验证了 `content.length() / 4` 的整数除法（如改为 `* 4` 或浮点会失败）。这是 P-1 反模式文件中**应该保留**的部分。
- **复核状态**: 未复核

### [维度21-11] TestLevelHints / TestPrincipal 的 fieldsAreStoredAndAccessible 命中 P-1

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestLevelHints.java:17-24`、`TestPrincipal.java:16-22`
- **证据片段**:
  ```java
  // TestLevelHints
  @Test
  void fieldsAreStoredAndAccessible() {                   // ← P-1
      LevelHints hints = new LevelHints(true, false, true, false, true);
      assertTrue(hints.isTrustedSource());
      assertFalse(hints.isWritesOutsideWorkspace());
      // ... 5 个 boolean 字段一一对照构造器参数
  }
  
  // TestPrincipal
  @Test
  void fieldsAreStoredAndAccessible() {                   // ← P-1（同名）
      Principal p = new Principal(PrincipalRole.OPERATOR, "ch-1", "tenant-A");
      assertEquals(PrincipalRole.OPERATOR, p.getRole());
      assertEquals("ch-1", p.getChannelId());
      assertEquals("tenant-A", p.getTenantId());
  }
  ```
- **严重程度**: P3
- **现状**: 命中 **P-1**。两个值对象的构造器只是赋值字段，无逻辑。`fieldsAreStoredAndAccessible` 测试名直白地承认了"测字段是否被存下来"——这就是 P-1 的字面定义。
- **风险**: 这两个文件的其他测试（`equalsAndHashCodeByValue`）有保留价值；P-1 测试是噪音。
- **建议**: 删除两个 `fieldsAreStoredAndAccessible`；保留 `equalsAndHashCodeByValue` 和（对 Principal）`nullFieldsAreAllowed`。
- **信心水平**: 确定
- **误报排除**: 这两个类的 equals 方法用 `Objects.equals`，是 IDE 自动生成的，但 `equalsAndHashCodeByValue` 测试仍有价值（验证字段顺序错位、null 处理）。
- **复核状态**: 未复核

### [维度21-12] TestTeamSpec 的 teamStatusAndMemberRoleEnumsAreStable + 部分 getter 测试命中 P-1

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/TestTeamSpec.java:48-60, 151-161`
- **证据片段**:
  ```java
  @Test
  void teamSpecGettersWork() {                            // ← P-1
      TeamSpec spec = new TeamSpec("team-a", "desc", "alice", members, 4);
      assertEquals("team-a", spec.getTeamName());
      assertEquals("desc", spec.getDescription());
      // ... 5 个 getter 一一对照构造器参数
  }

  @Test
  void teamStatusAndMemberRoleEnumsAreStable() {          // ← P-1（与 TestChannelKind 同模式）
      assertEquals(2, MemberRole.values().length);
      assertSame(MemberRole.LEAD, MemberRole.valueOf("LEAD"));
      // ...
  }
  ```
- **严重程度**: P3
- **现状**: 部分命中 **P-1**。同文件内 `teamSpecMemberSpecsIsDefensiveCopyAndUnmodifiable`（验证防御性拷贝 + 不可修改视图）、`teamMemberBindRejectsNulls`、`teamSpecRejectsNulls`（验证 `Objects.requireNonNull` 防御）是真实行为测试。
- **建议**: 删除 `teamSpecGettersWork`、`teamStatusAndMemberRoleEnumsAreStable`、`teamMemberSpecIsImmutableAndGettersWork`；保留 5 个行为测试。
- **信心水平**: 确定
- **误报排除**: 同 21-09。
- **复核状态**: 未复核

### [维度21-13] TestRoutingResult.toStringContainsFields 命中 P-5（弱 assertNotNull）

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/router/TestRoutingResult.java:57-66`
- **证据片段**:
  ```java
  @Test
  void toStringContainsFields() {
      ChatOptions options = new ChatOptions();
      options.setModel("test-model");
      RoutingResult result = new RoutingResult(options, "simple", "test-reason");
      String str = result.toString();
      assertNotNull(str);                                 // ← P-5
      // ↑ 方法名叫 toStringContainsFields 但只断言 not null，没检查任何字段是否在结果中
      // Object.toString() 永远不会返回 null（除非覆写后错误返回 null，那也是另一个测试）
  }
  ```
- **严重程度**: P3
- **现状**: 命中 **P-5**（"过度使用 assertNotNull"）+ **P-6**（"测试方法名不表达预期行为"——名字暗示 contains 但断言 not null）。方法名承诺验证 toString 含特定字段，实际只断言 toString 返回非 null（`Object.toString()` 的语言保证）。
- **风险**: 把 `RoutingResult.toString` 改成返回空字符串或 `"[REDACTED]"`，这个测试仍绿。完全无保护力。
- **建议**: 改为真实断言：`assertTrue(str.contains("test-model") && str.contains("simple") && str.contains("test-reason"))`，或直接删除（toString 测试价值低，文件中其他测试已足够）。
- **信心水平**: 确定
- **误报排除**: 不是"任何 assertNotNull 都是 P-5"——`assertNotNull(result.getOptions())` 在 `constructionWithAllFields` 中是合理的，因为 `options` 是入参、可能被错误地置 null。这里测的是 `String str = result.toString()`——`toString` 永不返回 null 是 Java 语言保证。
- **复核状态**: 未复核

### [维度21-14] TestHookResult.concreteTypesExtendHookResult 命中 P-2 tautology

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/hook/TestHookResult.java:55-60`
- **证据片段**:
  ```java
  @Test
  void concreteTypesExtendHookResult() {
      assertNotNull((HookResult) HookResult.PassResult.instance());
      assertNotNull((HookResult) new HookResult.VetoResult("r"));
      assertNotNull((HookResult) new HookResult.ReenterResult("m"));
      // ↑ 构造一个对象，强转为父类，断言它非 null。
      // 这是 Java 语言保证：new 永远返回非 null
  }
  ```
- **严重程度**: P3
- **现状**: 命中 **P-2**（"测试元数据属性"——这里测的是"子类对象能赋值给父类引用"，这是 Java 多态的语言保证）。同模式出现在 `TestCompletionDecision.concreteTypesExtendCompletionDecision`（line 56-61）。
- **风险**: 零保护力。把 `HookResult` 改成接口、把 `PassResult` 改成独立类（不继承）会让测试编译失败，但那是编译器责任，不是测试。
- **建议**: 删除该方法；同模式 `TestCompletionDecision.concreteTypesExtendCompletionDecision` 也删除。
- **信心水平**: 确定
- **误报排除**: 同文件内 `passResultReportsCorrectType` 等测试是合法的（验证 `isPass() == true && isVeto() == false && isReenter() == false` 的正交性）。
- **复核状态**: 未复核

### [维度21-15] TestNoOpSkillProvider.implementsISkillProvider 命中 P-2（反射元数据测试）

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/skill/TestNoOpSkillProvider.java:21-24`
- **证据片段**:
  ```java
  @Test
  void implementsISkillProvider() {
      assertTrue(ISkillProvider.class.isAssignableFrom(NoOpSkillProvider.class));
      assertTrue(NoOpSkillProvider.noOp() instanceof ISkillProvider);
      // ↑ 反射检查类继承关系——这是 javac 已经保证的，运行时不可能失败
  }
  ```
- **严重程度**: P3
- **现状**: 命中 **P-2**。`class NoOpSkillProvider implements ISkillProvider` 是声明，编译器保证 `isAssignableFrom` 永远为 true。除非有人在生产代码里把 `implements` 关键字删掉——那也会编译失败。
- **风险**: 零保护力。
- **建议**: 删除该方法；保留 `factoryReturnsSingleton` + `returnsEmptyNonNullCollection`（验证 NoOp 行为）。
- **信心水平**: 确定
- **误报排除**: 反射测试在以下情况有价值——验证运行时加载的插件（SPI/ServiceLoader）；这里被测类是同一模块内的 final class，编译期已确定。
- **复核状态**: 未复核

### [维度21-16] TestNoOpAuditLogger / TestAllowAllPermissionProvider / TestPassThroughPostDenialGuard 系列命中 P-3（弱负面覆盖）+ P-5（assertDoesNotThrow 滥用）

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestNoOpAuditLogger.java:7-37`、`TestAllowAllPermissionProvider.java:9-32`、`TestPassThroughPostDenialGuard.java:17-59`
- **证据片段**:
  ```java
  // TestNoOpAuditLogger 4 个测试方法，全部是 assertDoesNotThrow：
  @Test void testLogDoesNotThrow() { assertDoesNotThrow(() -> logger.log(event)); }
  @Test void testLogDenyDoesNotThrow() { assertDoesNotThrow(() -> logger.log(event)); }
  @Test void testLogNullEventDoesNotThrow() { assertDoesNotThrow(() -> logger.log(null)); }
  @Test void testMultipleLogsDoNotThrow() {
      for (int i = 0; i < 100; i++) assertDoesNotThrow(() -> logger.log(event));
  }

  // TestAllowAllPermissionProvider 3 个测试方法，全部 assertTrue(p.isAllowed())：
  @Test void testAlwaysAllows() { Permission p = provider.resolve("any", "any", "any"); assertTrue(p.isAllowed()); }
  @Test void testNullArguments() { Permission p = provider.resolve(null, null, null); assertTrue(p.isAllowed()); }
  @Test void testMultipleCallsAlwaysAllow() {
      for (int i = 0; i < 10; i++) assertTrue(provider.resolve(...).isAllowed());
  }
  ```
- **严重程度**: P3
- **现状**: 命中 **P-3**（"只测 happy path"——这些类只有 happy path）+ **P-5**（`assertDoesNotThrow` 与 `assertTrue(p.isAllowed())` 是几乎不可能失败的断言）。`NoOpAuditLogger.log` 方法体是空（不抛异常是空方法的语言保证）；`AllowAllPermissionProvider.resolve` 是 `return Permission.allow()`；`PassThroughPostDenialGuard.checkBeforeDispatch` 是 `return null`。每个类的核心断言只有 1 个有效场景（NoOp 不抛 / Allow 返回 true / PassThrough 返回 null），却用 3-5 个测试方法重复验证。
- **风险**: 文件计数膨胀（共 12 个测试方法保护 3 个共 60 行代码的 NoOp 类），给维护者错觉"NoOp 行为有充分覆盖"。但 NoOp 类的本质就是"什么都不做"——一个测试足够。
- **验证保护力**: 把 `NoOpAuditLogger.log` 改成 `throw new RuntimeException()`，4 个测试都会失败——证明保护力存在但重复。把 `AllowAllPermissionProvider.resolve` 改成 `return Permission.deny("x")`，3 个测试都会失败——同上。
- **建议**: 每个文件保留 1 个测试方法（NoOp 不抛 + 默认返回值），删除其余 2-4 个。或合并为一个参数化测试。
- **信心水平**: 很可能
- **误报排除**: 这不是"反对 NoOp 测试"——`TestNoOpTeamManager` 是合法的，因为它验证 NoOp 抛 `UnsupportedOperationException` + 错误信息包含 "not enabled"（line 25-32），这是 NoOp 的真实语义。这里被批的是"无语义的 NoOp"——`log` 真的是 `{} `、`resolve` 真的是 `return allow()`。
- **复核状态**: 未复核

### [维度21-17] 测试方法命名质量整体良好，未发现大规模 P-6 反模式

- **文件**: 抽样 `TestFingerprintPostDenialGuard`、`TestMultiMemberFanOut`、`TestDefaultAgentEngineConcurrencyGuard`、`TestPathAccessDecision`、`TestParentConstrainedPathAccessChecker`、`TestDefaultPathAccessChecker`
- **证据片段**:
  ```java
  // 高质量命名示例（已确认）：
  void firstConsultationReturnsNull()
  void recordingThenIdenticalConsultationDenies()
  void differentArgumentsProduceDifferentFingerprintAndAreAllowed()
  void boundFanOutDiamondRealConcurrencyAndDAfterBoth()
  void concurrentExecuteFailFast()
  void finallyDoesNotMisremoveHandle()
  void cancelWindowHonored()
  void restoreSessionGuardConsistentWithExecute()
  void fromStringDefaultsToDenyForUnrecognized()
  void pathWithTraversalNormalizedBeforeMatching()
  void relativePathResolvingOutsideRootDenied()
  ```
- **严重程度**: N/A（这是正向观察）
- **现状**: 抽样 6 个高质量测试文件的方法命名都遵循 P-6 推荐的 `testXxxWhenYyyThenZzz` 或 `nounVerbCondition` 风格。命名本身就表达了"在什么条件下预期什么结果"。
- **风险**: 无。这是模块的强项。
- **建议**: 仅命中 P-6 的是 `TestRoutingResult.toStringContainsFields`（已在 21-13 单独报告）。
- **信心水平**: 确定
- **误报排除**: 列出这些是为了说明 P-6 不是 nop-ai-agent 的系统性问题，只存在于个别 P-1/P-5 文件中。
- **复核状态**: 未复核

### [维度21-18] 未发现 P-7（测试间隐式依赖）和 P-8（无效负面测试）的系统性问题

- **文件**: 抽样 `TestDefaultAgentEngineConcurrencyGuard`、`TestMultiMemberFanOut`、`TestDbSessionTakeoverLockDualInstanceE2E`、`TestFingerprintPostDenialGuard`
- **证据片段**:
  ```java
  // 每个测试方法都自行构造数据（无 static 共享可变状态）：
  @Test
  void concurrentExecuteFailFast() throws Exception {
      CountDownLatch firstEntered = new CountDownLatch(1);    // 局部变量
      CountDownLatch firstProceed = new CountDownLatch(1);
      BlockingScriptedChatService chatService = new BlockingScriptedChatService(...);  // 局部
      DefaultAgentEngine engine = new DefaultAgentEngine(...);   // 局部
      // ...
  }
  
  // 负面测试是有效的（验证行为而非"不出错"）：
  @Test
  void memberAutoBindFailsFastWhenNoActiveTeamExists() {
      CompletionException ce = assertThrows(CompletionException.class, () -> ...);
      Throwable cause = unwrap(ce);
      assertTrue(cause instanceof NopAiAgentException);
      assertTrue(cause.getMessage().contains("no ACTIVE team"));   // 验证错误信息
  }
  ```
- **严重程度**: N/A（这是正向观察）
- **现状**: 抽样未发现 `@TestMethodOrder` 滥用、未发现 static 共享可变状态、未发现 `try/catch + fail()` 但 catch 块只 `assertNotNull(e)` 的弱负面测试。负面测试普遍使用 `assertThrows` + 验证异常类型与错误消息。
- **风险**: 无。
- **建议**: 无。
- **信心水平**: 确定（基于抽样；336 文件未全检，但 6 个高风险包已扫完）
- **误报排除**: 列出是为了避免审计读者假设"全维度都有问题"。
- **复核状态**: 未复核

### [维度21-19] 测试优先级分配偏离（与维度16-06 同根因，从 P-1 角度表述）

- **文件**: 模块整体
- **证据片段**:
  ```
  按 unit-test-antipatterns.md §"优先级排序" 分级：
  
  1. 核心算法/业务逻辑 → 最详细测试
     ✓ engine/ (71 测试 / 23 主) — ReAct 循环、并发、cancel、restore、fork — 充分
     ✓ team/flow/ (15 测试) — fanout/reduction/real concurrency — 充分
     ✓ runtime/lock/ — DbSessionTakeoverLock 全 CAS/lease/renew/stale — 充分
     ✓ message/ — DBMessageService 9 测试（除 dimension 16-03 缺口外）— 充分
     ✓ reliability/ (25 测试) — retry/circuit/goal/sustain 全 wiring + e2e — 充分
  
  2. 集成点 → 正常 + 错误路径
     ✓ tool/ (28 测试) — call-agent / send-message / memory / team tools 端到端
  
  3. 防御性代码 → 触发防御的输入
     ✓ security/TestDefaultPathAccessChecker (21 测试) — 路径前缀/traversal/case/tilde 全覆盖
     ✓ security/TestParentConstrainedPathAccessChecker (38 测试) — 边界全包含
     ⚠ security/TestDefaultPathAccessChecker — symlink 缺口（dimension 16-02）
  
  4. 数据模型 (@DataBean) → 只在有自定义逻辑时测试
     ✗ TestAiMemoryItem 5/9 测试是 P-1（应只保留 4 个 fallback 测试）
     ✗ TestSkillModel 5/9 测试是 P-1（应只保留 4 个行为测试）
     ✗ TestAuditEvent 5/8 测试是 P-1（应只保留 2 个 equality 测试）
     ✗ TestPermission 5/6 测试是 P-1
     ✗ TestPrincipal 1/5 测试是 P-1
     ✗ TestLevelHints 1/5 测试是 P-1
     ✗ TestTeamSpec 3/11 测试是 P-1
  
  5. 常量/配置 → 基本不测
     ✗ TestChannelKind 整文件 P-1
     ✗ TestSecurityLevel 部分方法 P-1
     ✗ TestPathAccessDecision.enumHasAllowAndDenyValues P-1（同文件其他测试优秀）
     ✗ TestSkillModel.topPatternEnumHasPhase1Values + resourceScopeEnumHasPhase1Values P-1
     ✗ TestTeamSpec.teamStatusAndMemberRoleEnumsAreStable P-1
  ```
- **严重程度**: P3
- **现状**: 优先级 1-3 测试投入与价值匹配（模块的核心算法、并发、集成点、防御性代码都有充分覆盖）。优先级 4-5 存在系统性偏离：约 30+ 个测试方法投入到无业务逻辑的 @DataBean 字段往返与纯枚举常量上。`unit-test-antipatterns.md` 的"本项目具体规则 §1 §2"明确禁止这类测试，但仓库现状与之偏离。
- **风险**: 维护成本（每加字段/枚举需同步改 P-1 测试）+ 误导信号（高计数掩盖有效覆盖率虚高）+ 新人模仿（看到既有 P-1 测试会继续写）。
- **建议**: 一次性 cleanup PR：删除 dimension 21-01/02/07/08/09/10/11/12/14/15 列出的所有 P-1/P-2 测试方法（约 35-40 个方法，分布在 12 个文件中），保留各文件内的真实行为测试。同步在 `ai-dev/skills/unit-test-antipatterns.md` 增加 nop-ai-agent 模块特定规则与正反例。
- **信心水平**: 确定
- **误报排除**: 与 dimension 16-06 互补：16-06 从覆盖分布角度、21-19 从优先级分配角度，结论一致（security/skill/memory 包的值对象/枚举被过度测试）。
- **复核状态**: 未复核

---

## 总结

**高价值测试（不计为发现，明确说明）**：
- `TestDefaultAgentEngineConcurrencyGuard`、`TestDbSessionTakeoverLock*`、`TestDBMessageService*`（除 16-04 的小瑕疵）、`TestMultiMemberFanOut`、`TestFingerprintPostDenialGuard`、`TestActionFingerprint`、`TestAuditLoggerDefault`、`TestSecureByDefault`、`TestDefaultApprovalGate`、`TestDefaultPathAccessChecker`、`TestParentConstrainedPathAccessChecker`、`TestDefaultSecurityLevelResolver`、`TestDefaultPermissionMatrix`、`TestDefaultDenialLedger`、`TestBudgetSnapshot`、`TestInMemoryBudgetProvider`、`TestDockerSandboxBackend`、`TestNoOpSandboxBackend`、`TestMultiTenantDbIsolation`、`TestAgentPlanRecordMapping`、`TestTeamAutoBinding`、`TestRestoreSession`、`TestInMemoryAiMemoryStore`、`TestPathAccessDecision`（除 `enumHasAllowAndDenyValues`）、`TestApprovalDecision`、`TestDenialResult`、`TestSkillAssemblyResult` —— 这些是高质量行为测试，验证真实业务逻辑、并发原子性、防御性契约，**不计入 dimension 21 发现**。

**核心结论**：nop-ai-agent 模块的核心算法路径（engine/team/lock/message/reliability）测试质量很高，并发与原子性场景使用真实 H2 + 真实线程 + CountDownLatch 端到端验证；问题集中在 security/skill/memory 包的值对象与枚举上，存在系统性 P-1/P-4 反模式（约 35-40 个测试方法保护零业务逻辑），以及 TestPassThroughPermissionMatrix/TestNoOpSecurityLevelResolver/TestNoOpDenialLedger 三个文件的 anti-hollow 内部类测试与生产实现重复覆盖。dimension 16 的真实覆盖缺口集中在 DefaultPathAccessChecker 的 symlink 场景与 DBMessageService 的 markConsumed 失败/CLAIMED 恢复路径——这两处与 dimension 13/14 的产品代码发现直接对应，应作为修复产品代码后的配套回归测试。
</task_result>

## 维度复核结论

待独立复核子 agent 输出。

## 最终保留项

待复核完成后填写。

---

注：本文件合并了维度 16（测试覆盖与质量）和维度 21（单元测试有效性），因 nop-ai-agent 是非标准 AI 子系统模块，两者检查对象高度重叠（336 个测试文件）。
