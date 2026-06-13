# 维度 09：错误处理与错误码 — nop-ai-agent

**目标模块**: `nop-ai/nop-ai-agent`（仅 `src/main/java` 手写代码；排除 `_gen/` 与 `src/test/`）

## 第 1 轮（初审）

### 审计范围与 grep 基线

- `throw new (RuntimeException|IllegalArgumentException|IllegalStateException|NullPointerException|Exception)` → 1 命中（CalibratedTokenEstimator.java:41）
- `throw new` → 23 命中（多为 NopAiAgentException 与接口 default 方法的 UnsupportedOperationException）
- `catch (` → 9 命中，逐一审阅
- `System.(out|err)` → 0 命中；`printStackTrace` → 0 命中
- `LoggerFactory|Logger` → 30 命中（SLF4J 使用整体规范）
- 非 ASCII（含中文）→ 74 命中，全部位于 `_gen/` javadoc 或 `AiMemoryConfig.java:12` 注释。**手写错误消息无中文**。

### [维度09-01] 模块异常类 `NopAiAgentException extends RuntimeException`，未继承 `NopException`，缺 ErrorCode 构造器

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/NopAiAgentException.java:1-12`
- **证据片段**:
  ```java
  package io.nop.ai.agent.engine;

  public class NopAiAgentException extends RuntimeException {

      public NopAiAgentException(String message) {
          super(message);
      }

      public NopAiAgentException(String message, Throwable cause) {
          super(message, cause);
      }
  }
  ```
- **严重程度**: P1
- **现状**: 模块唯一的异常类直接继承 `java.lang.RuntimeException`，仅提供 `(String)`、`(String, Throwable)` 两个构造器，无 `serialVersionUID`，无 `ErrorCode` 构造器。该异常是 `IAgentEngine` 公共 API（`getSessionStatus`、`cancelSession`、`execute→loadAgentModel`、`resolveExecutor`）抛出的主异常类型。
- **风险**: 直接违反 `docs-for-ai/02-core-guides/error-handling.md`「不要这样写」表第 3 行硬性规定——「自定义异常类不继承 `NopException`（如 `extends RuntimeException`）：绕过框架异常体系，丢失 ErrorCode、i18n、结构化错误响应等能力。所有业务异常必须直接或间接继承 `NopException`」。当该引擎被 BizModel/GraphQL 层消费时，Nop 框架不会把 `RuntimeException` 转换为结构化错误响应，跨模块调用方无法按错误码匹配、无法国际化、`getMessage()` 之外的结构化参数全部丢失。同时缺 `ErrorCode` 构造器，使该模块未来无法平滑升级到 ErrorCode 模式（error-handling.md 明确要求模块异常类「同时提供 (String) 和 (ErrorCode) 构造器」）。
- **建议**: 改为 `extends NopException`，构造器转发 `super(message, null, true, true)` 与 `super(message, cause, true, true)`，新增 `NopAiAgentException(ErrorCode)` 与 `(ErrorCode, Throwable)`，并补 `private static final long serialVersionUID = 1L;`。参考 error-handling.md 的 `NopAiException` 示例与 nop-stream 的 `StreamException`。
- **信心水平**: 高
- **误报排除**: AGENTS.md 提示「模块内部可使用模块异常类 + 英文字符串消息」，但这并不豁免「必须继承 `NopException`」这一更强约束——后者是 error-handling.md「不要这样写」表的明文要求。证据是字面 `extends RuntimeException`，与平台规范文本直接冲突。
- **复核状态**: 未复核

### [维度09-02] `DefaultPathAccessChecker.normalizePath` 静默吞掉所有异常返回 null，且调用方将任意归一化失败误标为「invalid traversal」

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:111-117`（吞异常处），调用方 `:48-51`（误标原因）
- **证据片段**:
  ```java
  // normalizePath, 行 111-117
  try {
      Path normalized = Paths.get(p).normalize();
      return normalized.toString().replace("\\", "/");
  } catch (Exception e) {
      return null;
  }
  // 调用方 checkAccess, 行 48-51
  String normalized = normalizePath(path);
  if (normalized == null) {
      return PathAccessResult.deny("Path normalization failed (contains invalid traversal): " + path);
  }
  ```
- **严重程度**: P2
- **现状**: `normalizePath` 用 `catch (Exception e)` 捕获 `Paths.get(p).normalize()` 可能抛出的 `InvalidPathException`，catch 体**完全为空**（无日志、不区分原因），直接 `return null`。调用方对 `null` 的处理是固定返回 `"Path normalization failed (contains invalid traversal)"`，而真正的 traversal 检测实际由 `:53` 的 `containsTraversal(path)` 独立完成——两条路径被混淆。
- **风险**: (1) 安全相关组件中静默吞异常且零日志，使任何归一化失败都成为无诊断的 deny，运维侧无法区分「恶意构造路径」与「环境/编码问题」；(2) deny 原因字符串恒为「contains invalid traversal」，对非 traversal 类失败（如 `InvalidPathException`）属于事实性误导，污染审计日志与上抛给调用方的 `reason` 字段。与 dim07 的 `ToolSchemaConverter.convert` 吞异常是**不同文件、不同方法、不同影响面**的独立问题（本处影响安全路径判定与审计语义）。
- **建议**: catch 内用 SLF4J `LOG.warn("path normalization failed: {}", path, e)` 记录原始异常；调用方按真实失败原因区分（如 `"Path normalization failed: invalid path characters"`），不要笼统标为 traversal。为该类补 `private static final Logger LOG`。
- **信心水平**: 高
- **误报排除**: 非 dim07 同类——dim07 是 `engine/ToolSchemaConverter.java:13-22`，本发现是 `security/DefaultPathAccessChecker.java` 的安全路径检查器，吞异常外还叠加了「deny 原因误导」这一独立行为缺陷。
- **复核状态**: 未复核

### [维度09-03] `SingleTurnExecutor.execute` 顶层 catch 仅存 `e.toString()`，且该类完全无 SLF4J logger，堆栈彻底丢失

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/SingleTurnExecutor.java:68-73`
- **证据片段**:
  ```java
  } catch (Exception e) {
      ctx.setStatus(AgentExecStatus.failed);
      ctx.setLastError(e.toString());

      publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName, e.toString());
  }
  ```
  （整个 `SingleTurnExecutor` 类无 `Logger` 字段、无任何 `LOG.*` 调用——`LoggerFactory` grep 在本类 0 命中。）
- **严重程度**: P2
- **现状**: 执行器顶层 `catch (Exception e)` 把异常压扁为 `e.toString()`（仅 `类名: message`），写入 `ctx.lastError` 与一个失败事件，然后正常返回 `completedFuture`（不 rethrow）。该类没有任何 SLF4J logger，堆栈跟踪既不入日志、也不入 result、也不保留 cause 链。
- **风险**: `SingleTurnExecutor` 是 `"single-turn"` 模式的主执行路径，任何 `chatService.call` 抛出的底层异常（网络/超时/反序列化）都以 `toString()` 形式蒸发，堆栈在生产环境永远不可见，排障只能靠复现；调用方拿到的是 `failed` 状态 + 一个无堆栈字符串，无法程序化区分错误类型。相比 `ReActAgentExecutor`（至少类内有 `LOG` 字段），本类连日志能力都缺失，是排障黑洞。
- **建议**: 为本类新增 `private static final Logger LOG = LoggerFactory.getLogger(...)`，在 catch 内 `LOG.error("single-turn execution failed: agentName={}, sessionId={}", agentName, sessionId, e)`（保留 `e` 以输出堆栈）；`ctx.setLastError` 至少保留完整 `message`，并考虑把异常对象存入 result 以保留 cause 链。
- **信心水平**: 高
- **误报排除**: 不是「catch 后正常返回 = 反模式」的泛化误报——执行器契约返回 `CompletionStage<AgentExecutionResult>`，把失败折叠进结果是设计预期；真正的结构性缺陷是「堆栈 100% 丢失且无日志」，可量化（grep 证明本类零 logger）。
- **复核状态**: 未复核

### [维度09-04] `ReActAgentExecutor.execute` 顶层 catch 有 LOG 字段却未使用，堆栈丢失；cancellation 分支整吞异常

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:505-516`
- **证据片段**:
  ```java
  } catch (Exception e) {
      if (ctx.isCancelRequested()) {
          Thread.currentThread().interrupt();
          handleCancellation(ctx, sessionId, agentName);
      } else {
          ctx.setStatus(AgentExecStatus.failed);
          ctx.setLastError(e.toString());

          invokeOnError(ctx, agentName);
          publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName, e.toString());
      }
  }
  ```
  （本类 `:69` 已声明 `private static final Logger LOG`，但该 catch 内任何分支均未调用 `LOG.error(..., e)`。）
- **严重程度**: P2
- **现状**: react 模式主循环的兜底 catch。`else` 分支与 09-03 同病——只存 `e.toString()`、不发堆栈；更严重的是 `if (ctx.isCancelRequested())` 分支：异常被**彻底丢弃**（无日志、无 `lastError`、无事件），仅中断线程后转走取消流程，若取消期间真的发生了非取消原因的异常（如循环内 OOM/工具异常恰好与取消并发），将无任何痕迹。
- **风险**: react 是默认执行模式（`resolveExecutor:279` `mode==null||"react"`），其顶层异常处理质量直接决定生产可观测性。本类明明持有 `LOG`（在 `invokeHooks`/`performCompaction` 中规范使用），却在最关键的兜底 catch 中不用，属于「能记而不记」的一致性缺陷；cancellation 分支的整吞还会掩盖取消与真实故障并发时的根因。
- **建议**: 两个分支都加 `LOG.error("react execution failed: agentName={}, sessionId={}, cancelled={}", agentName, sessionId, ctx.isCancelRequested(), e)`；cancellation 分支至少把 `e` 记入 `ctx.setLastError`，避免完全无痕。
- **信心水平**: 高
- **误报排除**: 与 09-03 是不同文件、不同严重度维度的独立发现（本类有 logger 却不用 + cancellation 整吞；09-03 是零 logger）。
- **复核状态**: 未复核

### [维度09-05] `CalibratedTokenEstimator` 构造器抛裸 `IllegalArgumentException`，与本模块既有约定不一致

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/CalibratedTokenEstimator.java:39-42`
- **证据片段**:
  ```java
  public CalibratedTokenEstimator(ILlmDialect dialect, ApiStyle apiStyle) {
      if (dialect == null) {
          throw new IllegalArgumentException("dialect must not be null");
      }
  ```
  对比同模块 `ReActAgentExecutor.Builder.build()`（`:202-207`）对同语义空值检查：
  ```java
  if (chatService == null) {
      throw new NopAiAgentException("chatService must not be null");
  }
  ```
- **严重程度**: P3
- **现状**: 构造器对 `dialect==null` 抛 JDK 原生 `IllegalArgumentException`，绕过模块异常体系。模块内对「必填参数为 null」这一相同前置条件，`ReActAgentExecutor` 已统一使用 `NopAiAgentException`。
- **风险**: 低。消息为英文、语义清晰、属构造期编程错误。但与模块自定约定不一致，会污染调用方对「该模块抛什么」的预期，随模块扩张放大为风格分裂；且按 error-handling.md「不要这样写」第 1 行，业务异常不应随手抛 JDK 原生异常类。
- **建议**: 统一改为 `throw new NopAiAgentException("dialect must not be null")`，与 `ReActAgentExecutor.Builder` 对齐。
- **信心水平**: 中（缺陷真实但影响有限，故 P3）
- **误报排除**: 非「把一切 IllegalArgumentException 都当反模式」的泛化误报——本模块已存在对同语义场景的既定约定（`NopAiAgentException`），此处属模块内一致性违约，有对照代码可佐证。
- **复核状态**: 未复核

### 已评估并明确排除的项（非发现）

1. `IAgentEngine.java`/`ISessionStore.java`/`IAiMemoryStore.java`/`NoOpHookRegistry.java` 的 `UnsupportedOperationException`：均为接口 default 方法实现的「可选操作未实现」语义，等价于 `java.util.Collection.add` 标准模式。`DefaultAgentEngine` 已用 `NopAiAgentException` 覆写真正被调用的方法。
2. `DefaultAgentEngine.java:320-324` 的 catch 链：正确保留 cause，分层正确，合规。
3. `ReActAgentExecutor.invokeHooks:602-611` 与 `invokeOnError:619-621` 的 catch：按 hook 生命周期点区分处理并 `LOG.warn/error(..., e)` 输出堆栈，before_* 分支正确 rethrow 原始 `e`，合规。
4. `DefaultAgentEventPublisher.java:18-24` 的 catch：正确传递 `e` 作最后一个 SLF4J 参数，合规。
5. 中文 grep 命中（74 处）：全部位于 `_gen/` javadoc 或代码注释，无手写错误消息含中文。
6. `System.out/System.err`、`printStackTrace`：0 命中。
7. SLF4J logger 声明：标准姿势（仅 `SingleTurnExecutor` 缺失，已计入 09-03）。

## 复核结论索引

| 编号 | 严重程度 | 文件 | 一句话 | 复核状态 |
|------|---------|------|--------|---------|
| 09-01 | P1 | `NopAiAgentException.java:1-12` | 异常类 `extends RuntimeException` 而非 `NopException`，缺 ErrorCode 构造器 | 未复核 |
| 09-02 | P2 | `DefaultPathAccessChecker.java:111-117` | 归一化异常被静默吞掉 + deny 原因误标为 traversal | 未复核 |
| 09-03 | P2 | `SingleTurnExecutor.java:68-73` | 顶层 catch 仅存 `toString()`，整类无 logger，堆栈全丢 | 未复核 |
| 09-04 | P2 | `ReActAgentExecutor.java:505-516` | 有 LOG 却不在顶层 catch 使用，cancellation 分支整吞异常 | 未复核 |
| 09-05 | P3 | `CalibratedTokenEstimator.java:39-42` | 裸 `IllegalArgumentException` 与模块 `NopAiAgentException` 约定不一致 | 未复核 |

**汇总**: 5 项发现（1×P1，3×P2，1×P3）。最高优先级 09-01。已知 dim07 的 `ToolSchemaConverter.convert` 吞异常已按要求未重复报告。
