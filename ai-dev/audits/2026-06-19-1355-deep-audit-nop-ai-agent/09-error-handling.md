# 维度 09：错误处理与错误码 — nop-ai-agent

## 核心结论

模块总体遵循"模块异常类 + 英文字符串消息"策略（`NopAiAgentException` 已正确实现双构造器模式），74%+ 抛出使用 `NopAiAgentException`/`SandboxException`。发现 10 个局部反模式问题，集中在异常链丢失、`e.toString()` 代替 throwable、以及若干 silent swallow 边界 case。

## 模块异常类评估

### `NopAiAgentException`（engine 包）
完全符合规范。继承 `NopException`，同时提供 `(String)` / `(String, Throwable)` / `(ErrorCode)` / `(ErrorCode, Throwable)` 四个构造器。

### `SandboxException`（security 包）
合理设计。继承 `NopAiAgentException`，额外携带 `SandboxFailureReason` 枚举，让调用方可按 failure category 分支。

### `NopAiAgentException(ErrorCode)` 构造器现状
定义了，但**整个模块没有定义任何 ErrorCode 常量**（`grep "ErrorCode\.define"` 全模块零命中），ErrorCode 路径目前是"死代码"（详见 [维度09-6]）。

## 异常类型分布统计

| 异常类型 | 抛出次数 | 占比 | 用途 |
|---------|---------|------|------|
| `NopAiAgentException` | 301 | 74.4% | 主模块异常类（内部+公共） |
| `IllegalArgumentException` | 61 | 15.1% | 构造器/参数 precondition 检查 |
| `UnsupportedOperationException` | 23 | 5.7% | 接口 default 方法的"未实现/可选操作" |
| `IllegalStateException` | 10 | 2.5% | enum switch default 分支防御 + taskId 冲突防御 |
| `SandboxException` | 4 | 1.0% | 沙箱失败（继承 NopAiAgentException） |
| `CompletionException` | 3 | 0.7% | CompletableFuture 失败传播 |
| `NullPointerException` | 2 | 0.5% | 显式 precondition（DefaultOrphanRecoveryHandler） |
| `SQLException` | 1 | 0.2% | DBCheckpointManager.readClob 内部转换 |
| **总计** | **405** | **100%** | （不含 Objects.requireNonNull 隐式 NPE） |

**语言检查**：`grep "throw new.*\""` 全模块零中文消息命中。所有错误消息均为英文。
**日志规范**：`grep "System.out\|System.err"` 零命中；`grep "e.printStackTrace"` 零命中。所有日志走 SLF4J Logger。

## 第 1 轮（初审）

### [维度09-1] ToolSchemaConverter 静默吞异常并降级工具 schema

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ToolSchemaConverter.java:13-22`
- **证据片段**:
  ```java
  public static Map<String, Object> convert(XNode schema) {
      if (schema == null)
          return null;

      try {
          return doConvert(schema);
      } catch (Exception e) {
          return null;
      }
  }
  ```
- **严重程度**: P2
- **现状**: `convert` 在 schema 解析异常时静默返回 null，既不 rethrow、不 LOG，也不保留 cause。调用方 `ReActAgentExecutor.toToolDefinition`（line 3097-3102）只判断 `if (parameters != null)`，null 时生成不带 parameters 的 `ChatToolDefinition`，等同于"工具没有参数 schema"。
- **风险**: 工具 schema 解析失败时，LLM 会收到无参数描述的工具，导致工具调用参数错乱。运维/operator 完全无感知——没有任何日志记录"为什么这个工具突然没有参数 schema 了"。Bug 复现路径被切断。这是 `error-handling.md` "不要这样写" 表第 3 行（"catch 后丢弃异常，既不 rethrow 也没 LOG.info 以上"）的教科书反例。
- **建议**: 至少 `LOG.warn("ToolSchemaConverter: failed to convert schema, omitting parameters from tool definition", e)`，使故障可见；或者直接抛 `NopAiAgentException("Failed to convert tool schema for tool: " + ..., e)` 让上游决定降级策略。
- **信心水平**: 确定
- **误报排除**: 这不是 `IoHelper.safeClose` 类的资源关闭 catch（guide 允许 catch+swallow 仅限资源关闭且要求 IoHelper 包装），也不是 per-element 失败隔离（guide 允许 LOG.warn(e) + continue）。这里是单一非批量调用，且 catch 后用 `return null` 替代异常传播——直接命中 guide 第 200 行"内部函数 catch 异常转返回值"反模式。
- **复核状态**: 未复核

---

### [维度09-2] FileSystemSkillProvider 重抛时丢失原始异常链

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/skill/FileSystemSkillProvider.java:272-288`
- **证据片段**:
  ```java
  private static SkillTopPattern parseTopPattern(String value, String filePath) {
      try {
          return SkillTopPattern.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
          throw new NopAiAgentException(
                  "Invalid topPattern value '" + value + "' in skill file: file=" + filePath);
      }
  }

  private static SkillResourceScope parseResourceScope(String value, String filePath) {
      try {
          return SkillResourceScope.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
          throw new NopAiAgentException(
                  "Invalid resourceScope value '" + value + "' in skill file: file=" + filePath);
      }
  }
  ```
- **严重程度**: P2
- **现状**: 两处都捕获了 `IllegalArgumentException e`，但 `new NopAiAgentException(message)` 调用没有传入 `e` 作为 cause。原始异常（含 `valueOf` 调用栈、具体哪个枚举值不匹配）被丢弃。
- **风险**: 当 skill 文件配置错误时，错误消息只告诉用户"topPattern value 'X' is invalid"，但调试时无法看到 `SkillTopPattern` 实际允许哪些值。运维人员排查 YAML 配置错误时丢失一半线索。这是 `error-handling.md` 第 207 行"丢失原始异常链 / 排查困难"反模式的直接命中。
- **建议**: 改为 `throw new NopAiAgentException("Invalid topPattern value '" + value + "' in skill file: file=" + filePath, e)`。注意同文件 line 126-127 和 134-135 已经使用了正确的 `(message, e)` 模式，证明这是局部疏漏而非风格选择。
- **信心水平**: 确定
- **误报排除**: 这不是"cause 已通过其他渠道保留"的情况——此处 cause 直接丢弃。同文件其他 4 处 catch+rethrow 中 2 处传了 cause、2 处没传，是明显的不一致疏漏。
- **复核状态**: 未复核

---

### [维度09-3] 多处 LOG.warn/LOG.error 使用 `e.toString()` 代替 throwable 末参数

- **文件**:
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/skill/LLMCurator.java:213-216`
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/recovery/ScheduledRecoveryManager.java:391-394`
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/completion/LlmCompletionJudge.java:89-93`
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:1602-1610`（`releaseLockQuietly`）
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1311-1314` 和 `1329-1333`
- **证据片段**（取 LLMCurator + DefaultAgentEngine + ReActAgentExecutor 三处代表性样例）:
  ```java
  // LLMCurator.java:213-216
  } catch (Exception e) {
      LOG.warn("LLMCurator: JSON parse failed: {}", e.toString());
      return SkillCurationResult.failed("llm", "unparseable response: JSON parse error");
  }

  // DefaultAgentEngine.java:1602-1610 (releaseLockQuietly)
  private void releaseLockQuietly(String sessionId, String ownerId) {
      try {
          sessionTakeoverLock.release(sessionId, ownerId);
      } catch (RuntimeException e) {
          LOG.warn("DefaultAgentEngine: failed to release takeover lock for sessionId={}, "
                  + "ownerId={} (the lease will auto-expire via TTL): {}", sessionId, ownerId,
                  e.toString());
      }
  }

  // ReActAgentExecutor.java:1311-1314 (retry loop, RETRY branch)
  LOG.warn("LLM call failed (classification={}, attempt={}), retrying after {} ms: {}",
          classification, attempt, outcome.getDelayMs(),
          ex.toString());
  ```
- **严重程度**: P3
- **现状**: 5 处日志调用都使用 `e.toString()`（仅打印 `ExceptionClass: message`）代替把 throwable 作为 logger 末参数（SLF4J 会打印完整 stack trace）。
- **风险**: 失去 stack trace，运维排查 LLM 调用失败、租约释放失败、JSON 解析失败时定位困难。`error-handling.md` 第 202 行明确列为反模式："LOG.warn(...:{}, e.getMessage()) 不传 throwable → 丢失 stack trace"。注意 `ReActAgentExecutor` retry 块虽然在 `lastError` 中保留了异常对象、最终通过 `throw (RuntimeException) lastError` 重抛，但 RETRY 分支的中间日志已永久丢失 stack trace（每次重试都丢失一次）。
- **建议**: 删除 `e.toString()` / `ex.toString()`，改为 throwable 作为最后参数（去掉 `{}` 占位符）。例如：
  ```java
  LOG.warn("LLMCurator: JSON parse failed", e);
  LOG.warn("DefaultAgentEngine: failed to release takeover lock for sessionId={}, ownerId={} (the lease will auto-expire via TTL)",
          sessionId, ownerId, e);
  ```
- **信心水平**: 确定
- **误报排除**: 这不是 SLF4J 不支持 throwable 的限制（SLF4J 1.x/2.x 都支持）。同代码库内已有 ~30 处正确写法（如 `DefaultAgentEngine.java:1789`、`InMemoryActorRuntime.java:410`、`DBMessageService.java:255`），证明这是局部疏漏而非风格选择。
- **复核状态**: 未复核

---

### [维度09-4] DefaultPathAccessChecker.normalizePathStatic catch+return null 无日志

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:115-133`
- **证据片段**:
  ```java
  /**
   * Normalize a path string: ... Returns {@code null} when normalization
   * fails (e.g. invalid traversal that escapes normalization, or tilde
   * expansion with no known home dir).
   */
  public static String normalizePathStatic(String path) {
      String p = path.replace("\\", "/");

      if (p.startsWith("~")) {
          if (HOME.isEmpty()) {
              return null;
          }
          // ...
      }

      try {
          Path normalized = Paths.get(p).normalize();
          return normalized.toString().replace("\\", "/");
      } catch (Exception e) {
          return null;
      }
  }
  ```
- **严重程度**: P3
- **现状**: `Paths.get(p).normalize()` 抛异常时（如 `InvalidPathException`）静默返回 null，无 LOG、无 rethrow。调用方 `checkAccess` (line 49-51) 检测到 null 后转为 `PathAccessResult.deny("Path normalization failed ...")`——这是合理的 fail-closed 语义，但丢失了"为什么路径无效"的根因。
- **风险**: 当路径因 InvalidPathException 失败时，安全审计无法看到原异常类（是 NUL 字符？编码问题？OS 特定限制？），仅看到 deny 消息。`error-handling.md` 第 201 行反模式："catch 后丢弃异常，既不 rethrow with cause 也没 LOG.info 以上"直接命中。
- **建议**: 至少 `LOG.debug("DefaultPathAccessChecker: path normalization failed for path={}, denying", path, e)`（debug 级别即可，避免对正常拒绝路径噪声），让运维开启 debug 时能看到根因。
- **信心水平**: 很可能
- **误报排除**: 这不是"返回 null 是合法 domain 表达"——文档明确说"returns null when normalization **fails**"，"fails"本身是异常语义而非业务结果。也不是 IoHelper.safeClose 的资源关闭场景。
- **复核状态**: 未复核

---

### [维度09-5] DockerSandboxBackend.killContainer / NoOpSandboxBackend.killTree catch+ignore 无日志

- **文件**:
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DockerSandboxBackend.java:327-338`（`killContainer`）
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/NoOpSandboxBackend.java:218-238`（`killTree`）
- **证据片段**（DockerSandboxBackend.killContainer）:
  ```java
  private static void killContainer(String containerName) {
      try {
          runShortCommand("docker", "kill", containerName);
      } catch (Exception ignored) {
          // best-effort — --rm should already clean up on kill
      }
      try {
          runShortCommand("docker", "rm", "-f", containerName);
      } catch (Exception ignored) {
          // best-effort fallback — container may already be gone
      }
  }
  ```
- **严重程度**: P3
- **现状**: 两处 catch 块完全静默，未 LOG、未 rethrow。代码注释解释了 best-effort 语义（容器 `--rm` 自动清理；cleanup 失败不应掩盖原 timeout 异常）。
- **风险**: 如果 docker kill/rm 因非"容器已不存在"原因失败（如 docker daemon 临时不可达、权限错误），运维完全无感知，容器可能堆积。违反 `error-handling.md` 第 33-37 行的"丢弃异常前必须留证"规则。
- **建议**: 改为 `LOG.debug("DockerSandboxBackend: best-effort cleanup failed for container {} (safe to ignore if --rm already reaped)", containerName, e)`。debug 级别既不增加正常运行噪声，又保留可调查性。
- **信心水平**: 很可能
- **误报排除**: 这不是 IoHelper.safeClose 资源关闭模式，而是子进程命令执行的清理——guide 没有为该场景豁免。注释解释了"为什么丢弃"，但 guide 明确要求"即便在允许 catch 的位置，丢弃 throwable 前必须满足 LOG.info 及以上 或 rethrow with cause"。
- **复核状态**: 未复核

---

### [维度09-6] 模块未定义任何 ErrorCode，公共 API 失败仅有字符串消息

- **文件**:
  - 全模块无 ErrorCode 定义：`grep "ErrorCode\.define"` 零命中（`nop-ai/nop-ai-agent/src/main/java` 全部）
  - 公共接口实现：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`（`execute`、`forkSession`、`resumeSession`、`restoreSession` 等）
  - 异常类预留的 ErrorCode 构造器：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/NopAiAgentException.java:17-22`（已定义但全模块无调用方）
  - 混合风格证据：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/TeamTaskFlowOrchestrator.java:575-588`
- **证据片段**（TeamTaskFlowOrchestrator 混合风格）:
  ```java
  private BuiltGraph buildGraphForExecution(String teamId) {
      if (teamId == null) {
          throw new NopAiAgentException("nop.ai.team.flow.null-team-id: teamId must not be null");
      }

      List<TeamTask> tasks = taskStore.getTasksByTeam(teamId);
      if (tasks == null || tasks.isEmpty()) {
          throw new NopAiAgentException(
                  "nop.ai.team.flow.no-tasks: team has no tasks to orchestrate: teamId=" + teamId);
      }

      Team team = teamManager.getTeam(teamId)
              .orElseThrow(() -> new NopAiAgentException(
                      "nop.ai.team.flow.team-not-found: unknown team teamId=" + teamId));
      // ...
  }
  ```
- **严重程度**: P3
- **现状**: 整个 `nop-ai-agent` 模块没有定义任何 `ErrorCode` 常量（无 `*Errors.java` 接口、无 `ErrorCode.define(...)` 调用、`.param()` 全模块零调用）。所有失败路径使用 `new NopAiAgentException("english message with embedded params")`，包括 `IAgentEngine` 这类被其他模块/工具调用方消费的公共契约。`NopAiAgentException(ErrorCode)` 构造器形同死代码。`TeamTaskFlowOrchestrator` 出现混合风格——把看起来像 ErrorCode 的字符串嵌在消息前缀里，但仍是字符串而非 ErrorCode 常量。
- **风险**: 公共契约 `IAgentEngine.execute` / `IAgentEngine.resumeSession` 等失败时，调用方只能通过消息文本匹配来分类错误（fragile、不可国际化、不可程序化匹配）。对于 `DefaultAgentEngine.getSessionStatus` 抛 "session not found" vs `forkSession` 抛 "parent session not found" 这类语义相近的错误，调用方无法稳定区分。`TeamTaskFlowOrchestrator` 的混合风格暗示开发者已经意识到需要稳定 code，但未走完最后一步。
- **建议**:
  1. 为公共 API 的失败路径定义 `NopAiAgentErrors` 接口（如 `ERR_AGENT_SESSION_NOT_FOUND`、`ERR_AGENT_ALREADY_EXECUTING`、`ERR_AGENT_LOCKED_BY_ANOTHER_INSTANCE`、`ERR_AGENT_MODEL_NOT_FOUND`、`ERR_TEAM_TASK_TEAM_NOT_FOUND`），通过 `.param(ARG_SESSION_ID, sessionId)` 传递上下文。
  2. 内部辅助类（private 方法）继续使用字符串消息。
  3. 现有"伪 ErrorCode 字符串"（`nop.ai.team.flow.null-team-id:` 等）要么正式化为 ErrorCode，要么去掉前缀保持纯英文字符串一致性。
- **信心水平**: 很可能
- **误报排除**: 这不是"模块是纯内部实现，不需要 ErrorCode"——`IAgentEngine` 是平台级公共契约，被 `CallAgentExecutor`、`ScheduledRecoveryManager`、`TeamTaskFlowOrchestrator` 等多处程序化消费，且按错误类型分支。这与 `nop-stream` 模块（其 `StreamException` 同时提供 ErrorCode 构造器供公共 API 用）形成对比——`nop-stream` 实际使用 ErrorCode 路径，而 `nop-ai-agent` 完全没有。
- **复核状态**: 未复核

---

### [维度09-7] LLMCurator 内部函数 catch+返回 `SkillCurationResult.failed(...)`

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/skill/LLMCurator.java:91-116`
- **证据片段**:
  ```java
  private SkillCurationResult curateBatch(List<SkillModel> batch) {
      ChatRequest request = buildRequest(batch);

      ChatResponse response;
      try {
          response = config.getChatService().call(request, null);
      } catch (RuntimeException e) {
          LOG.warn("LLMCurator: chatService.call() threw for batch of {} skills",
                  batch.size(), e);
          return SkillCurationResult.failed("llm", "chatService.call() threw: " + e);
      }
      // ...
  ```
- **严重程度**: P3
- **现状**: `curateBatch` 是 LLM 驱动的 skill 整理流程的内部实现。捕获 `chatService.call()` 抛出的 RuntimeException 后返回 `SkillCurationResult.failed("llm", ...)`，而不是让异常传播。
- **风险**: LLM 调用的真实失败（网络、限流、auth 错误）被伪装成"skill 整理结果"，调用方无法把"curator 失败"和"LLM 系统性故障"区分开来。`error-handling.md` 第 200 行明列的"内部函数 catch 异常转返回值（`failureResult(...)`）"反模式。
- **建议**: 评估 `SkillCurationResult` 是否应当区分"业务级失败"（LLM 返回错误，可重试）与"系统级故障"（应当抛异常让上游决定）。当前实现已正确传 throwable `e` 给 logger，符合"留证"要求；问题仅在于"用返回值表达失败"这一选择本身。
- **信心水平**: 有趣的猜测
- **误报排除**: 这不是"per-element 失败隔离"——`curateBatch` 是单次 LLM 调用而非批量循环。是否构成反模式取决于 `SkillCurationResult` 是否被定义为"包含失败语义的 domain result 类型"。需复核调用方代码后定级。
- **复核状态**: 未复核

---

### [维度09-8] DefaultOrphanRecoveryHandler 使用显式 `throw new NullPointerException`

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/recovery/DefaultOrphanRecoveryHandler.java:114-128`
- **证据片段**:
  ```java
  public DefaultOrphanRecoveryHandler(RecoveryMode mode, IAgentEngine engine,
                                      DataSource dataSource, ITenantResolver tenantResolver) {
      this.mode = java.util.Objects.requireNonNull(mode, "mode must not be null");
      // Fail-fast: prevent silent misuse (Minimum Rules #24).
      if (mode == RecoveryMode.RESUME && engine == null) {
          throw new NullPointerException(
                  "DefaultOrphanRecoveryHandler: engine must not be null for RESUME mode");
      }
      if (mode == RecoveryMode.ABORT && dataSource == null) {
          throw new NullPointerException(
                  "DefaultOrphanRecoveryHandler: dataSource must not be null for ABORT mode");
      }
      this.engine = engine;
      // ...
  }
  ```
- **严重程度**: P3
- **现状**: 构造器条件 precondition 检查直接 `throw new NullPointerException(...)`。语义上等价于 `Objects.requireNonNull(engine, "...")`，但项目内同类场景普遍使用 `Objects.requireNonNull` 或 `NopAiAgentException`。
- **风险**: 与项目其余 ~50 处 precondition 风格不一致。建议模块级异常统一走 `NopAiAgentException`，把 NPE 留给 JDK 自动抛出。
- **建议**: 改为 `throw new NopAiAgentException("DefaultOrphanRecoveryHandler: engine must not be null for RESUME mode")`，与同模块 `DBDenialLedger.java:103` 风格一致。
- **信心水平**: 很可能
- **误报排除**: 审计口径明确允许 `Objects.requireNonNull` 的隐式 NPE 作为合理 precondition 检查。但**显式** `throw new NullPointerException(...)` 不是 `Objects.requireNonNull` 调用——这是手写的 NPE，绕过了 NopException 体系。同文件 line 116 已经在用 `Objects.requireNonNull(mode, ...)`，证明这是局部不一致。
- **复核状态**: 未复核

---

### [维度09-9] CompletableFuture 失败传播使用裸 `CompletionException` 包装

- **文件**:
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/SpawnMemberFanOutStep.java:181-194`
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/BoundMemberFanOutStep.java:178-184`
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/MixedMemberFanOutStep.java:155-161`
- **证据片段**（SpawnMemberFanOutStep）:
  ```java
  CompletableFuture<TaskStepReturn> steppedFuture = dispatched.handle((outcome, ex) -> {
      if (ex != null) {
          recorder.markFailed(taskId);
          throw ex instanceof CompletionException
                  ? (CompletionException) ex
                  : new CompletionException(ex);
      }
      if (outcome.isCompleted()) {
          recorder.markComplete(taskId);
          return TaskStepReturn.RETURN_RESULT("completed:" + taskId);
      }
      recorder.markFailed(taskId);
      throw new CompletionException(outcome.getCause());
  });
  ```
- **严重程度**: P3
- **现状**: 三处 fanout step 把失败 outcome 包装为 `java.util.concurrent.CompletionException` 抛入 `CompletableFuture` 失败通道。
- **风险**: 这是 JDK `CompletableFuture` 的标准失败传播机制（`CompletionException` 是 `CompletableFuture.join()` 默认包装类型），技术上合规。但与项目"业务异常必须直接或间接继承 NopException"原则存在张力——一旦 `CompletableFuture` 失败被某些 catch 路径捕获（而非 `.join()`/`.get()`），上层可能看到裸 `CompletionException` 而非 NopAiAgentException。
- **建议**: 在文档/Javadoc 中明确：fanout step 失败的 root cause 始终是 `NopAiAgentException`（即调用方应当 `ce.getCause() instanceof NopAiAgentException` 来取真正异常）。优先级低。
- **信心水平**: 有趣的猜测
- **误报排除**: 这不是"业务异常不继承 NopException"——`CompletionException` 是 JDK concurrency 基础设施的标准包装类，其 `cause` 才是业务异常。guide 第 204 行"自定义异常类不继承 NopException"针对的是**业务异常**类。但出于可见性，标记为低优先级提示。
- **复核状态**: 未复核

---

### [维度09-10] 大量 `IllegalArgumentException`/`IllegalStateException` 在内部辅助类中使用（风格不一致）

- **文件**: 多处（共 ~71 处），代表性样例：
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/ThresholdBreaker.java:108,142,152,187`（4 处 `"modelKey must not be null"`）
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/ThresholdBreaker.java:134,179,213`（3 处 enum switch default `"Unknown circuit state: " + entry.state`）
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/InMemoryTeamTaskStore.java:64,76`
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/StandardRetryPolicy.java:70,74,78`
- **证据片段**（ThresholdBreaker 枚举 switch + precondition）:
  ```java
  // reliability/ThresholdBreaker.java:131-136
  public boolean allowCall(String modelKey) {
      if (modelKey == null) {
          throw new IllegalArgumentException("modelKey must not be null");
      }
      // ...
      switch (entry.state) {
          // ...
          default:
              throw new IllegalStateException("Unknown circuit state: " + entry.state);
      }
  }
  ```
- **严重程度**: P3
- **现状**: 模块内 ~61 处 `throw new IllegalArgumentException(...)`、10 处 `throw new IllegalStateException(...)`。绝大多数是构造器/方法入口的参数 precondition 检查，或是 enum switch 的 unreachable-default 防御。
- **风险**: 项目内有不一致：同类 precondition 在某些类用 `NopAiAgentException`（如 `DBDenialLedger.java:103` 的 `denialThreshold must be positive`），某些类用 `IllegalArgumentException`（如 `DefaultDenialLedger.java:52` 的同语义检查），风格分裂。
- **建议**: 制定模块内 precondition 风格规范——要么"全用 NopAiAgentException"，要么"precondition 用 IllegalArgumentException、业务规则失败用 NopAiAgentException"。注意：审计口径明确说明"Objects.requireNonNull 的 NPE 是合理 precondition 检查"——同理 `IllegalArgumentException` 的 precondition 用法也是合理 Java 惯例，所以此项不应当成 P2 级问题。
- **信心水平**: 很可能
- **误报排除**: 这不是"反模式 ~61 处"简单计数——必须区分两种语义：(1) precondition 检查是合理 Java 惯例，不算反模式；(2) 业务规则失败应当用 NopAiAgentException。本条目的真正问题是**风格不一致**，不是"应当全部改成 NopAiAgentException"。
- **复核状态**: 未复核

---

## 检查覆盖范围说明（无发现的检查项）

| 检查项 | 结果 |
|--------|------|
| `DefaultAgentEngine.execute`/`doExecute`/`resumeSession`/`restoreSession`/`cancelSession`/`forkSession`/`getSessionStatus` 失败路径 | 全部使用 `NopAiAgentException`，消息含 sessionId/agentName/teamId 等上下文 |
| `ReActAgentExecutor.execute` 主循环失败路径 | Retry/Circuit breaker/Fallback 全部抛 `NopAiAgentException`（含 cause） |
| `DbTeamManager`/`DbTeamTaskStore`/`DBCheckpointManager`/`DBMessageService`/`DBSessionStore`/`DBDenialLedger` 所有 SQLException 处理 | 全部 catch + `throw new NopAiAgentException("...: " + e.getMessage(), e)`，正确传递 cause |
| `DockerSandboxBackend.execute` 失败路径 | 使用 `SandboxException(reason, message, cause)`，含分类 reason + cause 传递 |
| `IAgentExecutor`/`IAiMemoryStore`/`ITeamManager` 公共接口实现失败 | 实现类全部抛 `NopAiAgentException` 或 `SandboxException` |
| `handleCallAgentRequest` 系统边界 catch+返回响应 | 边界 catch 正确：`LOG.warn(..., e)` + 返回 `CallAgentResponsePayload("failure", ...)` |
| Actor 系统的 supervisor 契约 | `InMemoryActorRuntime.runConsumptionLoop` 异常时 `LOG.error(..., e)` + `actor.updateStatus(FAILED)` |
| 批处理 per-element 失败隔离 | `DefaultAgentEngine.restorePendingSessions` 等全部正确：`LOG.warn(..., e)` + continue |
| 资源关闭模式 | try-with-resources；未发现手写 `try { x.close() } catch` 反模式 |
| 中文错误消息 | 全模块 `throw new.*"` 零中文命中 |
| `System.out`/`System.err`/`e.printStackTrace` | 全模块零命中 |
| `Objects.requireNonNull` 隐式 NPE | 大量使用（构造器 precondition），合理 |
| `.param()` 使用 | 全模块零调用（因为未使用 ErrorCode 路径），消息字符串内嵌参数替代 |

## 维度复核结论

待独立复核子 agent 输出。

## 最终保留项

待复核完成后填写。
