# 维度 13：安全与权限模型（nop-ai-agent）

## 第 1 轮（初审）

### [维度13-1] DefaultAgentEngine 默认装配 AllowAllPathAccessChecker，敏感路径零防护

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:121-126, 166`
- **证据片段**:
  ```java
  public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                             ISessionStore sessionStore, IPermissionProvider permissionProvider,
                             IToolAccessChecker toolAccessChecker) {
      this(chatService, toolManager, sessionStore, permissionProvider,
              toolAccessChecker, new AllowAllPathAccessChecker());   // ← 默认放行所有路径
  }
  ...
  this.pathAccessChecker = pathAccessChecker != null ? pathAccessChecker : new AllowAllPathAccessChecker();
  ```
- **严重程度**: P1
- **现状**: `AllowAllPathAccessChecker.checkAccess` 永远返回 `allow()`。真正的 deny-list 实现 `DefaultPathAccessChecker`（拒绝 `~/.ssh/`、`/etc/`、`.env`、`id_rsa` 等）只在测试中被 `new` 出来，生产代码无任何自动装配。
- **风险**: 集成商构造 `new DefaultAgentEngine(chatService, toolManager)` 后，agent 可读/写任意路径。配合 toolkit 中已存在的 `ReadFileExecutor`、`WriteFileExecutor`、`DeleteFileExecutor`、`PatchFileExecutor`、`BashExecutor`，LLM（或 prompt injection）可读取 `~/.ssh/id_rsa`、`/etc/passwd`、`~/.aws/credentials`、覆盖 `~/.bashrc` 等。`ParentConstrainedPathAccessChecker` 的 root 限定仅在父 agent 声明了 `workDir` 时才生效；root agent 不声明 workDir → 整条调用链无路径约束。
- **建议**: (1) 将 `DefaultPathAccessChecker` 设为默认；(2) 在文档醒目位置标注"默认不安全，必须显式装配安全 checker"；(3) 启动时若 pathAccessChecker 仍是 AllowAll 且 toolAccessChecker 仍是 AllowAll，WARN 一次。
- **信心水平**: 高
- **误报排除**: 通过 grep 确认全仓库无 `new DefaultPathAccessChecker` 出现在 main 代码（仅 test），无 setters 自动注入。
- **复核状态**: 未复核

### [维度13-2] DefaultAgentEngine 默认装配 AllowAllToolAccessChecker，危险工具 deny-list 失效

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:116-119, 165`
- **证据片段**:
  ```java
  public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                             ISessionStore sessionStore, IPermissionProvider permissionProvider) {
      this(chatService, toolManager, sessionStore, permissionProvider, new AllowAllToolAccessChecker());
  }
  ...
  this.toolAccessChecker = toolAccessChecker != null ? toolAccessChecker : new AllowAllToolAccessChecker();
  ```
- **严重程度**: P1
- **现状**: `DefaultToolAccessChecker` 内置硬编码 deny-list（`bash`, `write-file`, `delete-file`, `move-file`, `patch-file`, `apply-delta`, `http-request`, `graphql-query`），但永远不会被引擎默认装配。LLM 调用 `bash` 工具直接通过 Layer 1。
- **风险**: toolkit 模块 ship 了 `BashExecutor`、`WriteFileExecutor`、`DeleteFileExecutor`、`PatchFileExecutor`、`ApplyDeltaExecutor`、`HttpRequestExecutor`、`GraphqlQueryExecutor` 等高危 executor。集成商把这些 executor 注册后，agent 默认可全部调用，无任何 deny-list 兜底。一次 prompt injection 即可让 agent 执行 `rm -rf` 或外发 HTTP 请求。
- **建议**: 默认装配 `DefaultToolAccessChecker`；或要求集成商显式 opt-in 危险工具。
- **信心水平**: 高
- **误报排除**: 通过 grep 确认 `new DefaultToolAccessChecker` 仅出现在 test 路径下。
- **复核状态**: 未复核

### [维度13-3] DefaultAgentEngine 不装配 IAuditLogger，且无 setAuditLogger 入口

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:1149-1180`（resolveExecutor 方法）
- **证据片段**:
  ```java
  IAgentExecutor resolveExecutor(AgentModel model, IToolAccessChecker toolAccessChecker,
                                 IPathAccessChecker pathAccessChecker) {
      String mode = model.getMode();
      if (mode == null || mode.isEmpty() || "react".equals(mode)) {
          ...
          return ReActAgentExecutor.builder()
                  .chatService(chatService)
                  .toolManager(toolManager)
                  ...
                  .sessionStore(this.sessionStore)
                  .memoryStoreProvider(this.memoryStoreProvider)
                  .build();   // ← 注意：没有 .auditLogger(...)
      }
  ```
- **严重程度**: P1
- **现状**: `ReActAgentExecutor.Builder.build()` 在 auditLogger 为 null 时回退到 `new NoOpAuditLogger()`，其 `log()` 仅打印 TRACE 级别"audit logging disabled"。即便集成商后续注册了 checker/gate，所有 7 处 `auditLogger.log(...)` 调用都被静默吞掉。`DefaultAgentEngine` 没有 `setAuditLogger` setter。
- **风险**: 安全决策（允许/拒绝/审批/路径拒绝）完全无持久化记录，事故后无法追溯。在合规场景下，这本身就构成合规违约。
- **建议**: 在 `DefaultAgentEngine` 增加 `private IAuditLogger auditLogger = new Slf4jAuditLogger();` 字段 + setter；resolveExecutor 中 `.auditLogger(this.auditLogger)`；默认值改为 Slf4jAuditLogger 而非 NoOp。
- **信心水平**: 高
- **误报排除**: 全文 grep 确认 `DefaultAgentEngine` 无 auditLogger 字段、无 setter、resolveExecutor 无对应 builder 调用。
- **复核状态**: 未复核

### [维度13-4] 默认配置下 Layer 2 / Layer 3 全链路 disabled，AutoApproveGate 对 RESTRICTED 也放行

- **文件**: `security/AutoApproveGate.java:36-40`、`NoOpSecurityLevelResolver.java:28-30`、`PassThroughPermissionMatrix.java:27-29`、`NoOpDenialLedger.java:37-49`、`PassThroughPostDenialGuard.java:41-63`
- **证据片段**:
  ```java
  // AutoApproveGate — 不区分 SecurityLevel
  public ApprovalDecision requestApproval(SecurityLevel level, String toolName, ...) {
      return ApprovalDecision.approve(APPROVER);   // STANDARD/ELEVATED/RESTRICTED 全部 approve
  }
  // NoOpSecurityLevelResolver — 所有动作归为 STANDARD
  public SecurityLevel resolve(String actionKind, LevelHints hints) {
      return SecurityLevel.STANDARD;
  }
  // PassThroughPermissionMatrix — 任意 channel × level 全 allow
  public MatrixDecision check(ChannelKind channel, Principal principal, SecurityLevel level) {
      return MatrixDecision.allow();
  }
  ```
- **严重程度**: P2
- **现状**: 默认 `DefaultAgentEngine`：securityLevelResolver=NoOp、permissionMatrix=PassThrough、approvalGate=AutoApprove、denialLedger=NoOp、postDenialGuard=PassThrough。即便集成商注册了真实 resolver，`AutoApproveGate` 仍会批准 RESTRICTED 操作。
- **风险**: 设计文档的分级管控在默认配置下完全不生效。一个被 prompt-injected 的 agent 可在 5 次 deny 后继续重试（NoOp denial ledger），不会被 pause。
- **建议**: (1) 在 AutoApproveGate 增加"是否对 RESTRICTED 也 approve"的构造参数，默认 false；(2) 启动时打印 WARN 摘要，列出哪些 layer 是 NoOp/PassThrough。
- **信心水平**: 高
- **误报排除**: 这些 NoOp 类的 Javadoc 自己写明"shipped default"是 pass-through，不是 bug 而是设计；但作为安全审计，必须把"默认全开"作为风险记录。
- **复核状态**: 未复核

### [维度13-5] checkPathAccess 使用大小写敏感的参数名 allow-list，命名变体直接绕过

- **文件**: `security/ToolPathArgKeys.java:18-21`、`engine/ReActAgentExecutor.java:1460-1472`
- **证据片段**:
  ```java
  // ToolPathArgKeys
  public static final Set<String> KEYS = Set.of(
          "path", "file", "filePath", "filename", "directory", "dir",
          "destination", "output", "input", "source", "target", "cwd"
  );
  // ReActAgentExecutor.checkPathAccess
  for (Map.Entry<String, Object> entry : arguments.entrySet()) {
      if (!ToolPathArgKeys.KEYS.contains(entry.getKey())) {   // ← 大小写敏感 Set.contains
          continue;
      }
  ```
- **严重程度**: P2
- **现状**: 路径参数名匹配是 `Set.contains` 精确字符串比较。LLM/工具若声明任何变体名——`Path`、`PATH`、`file_path`、`Filepath`、`filepath`、`uri`、`url`、`endpoint`、`location`、`to`、`from`、`outputPath`、`inputPath`、`dest`、`src`——都直接 continue 不做路径检查。
- **风险**: 第三方/自定义工具只要用非标准参数名承载路径，就完全绕过 Layer 1 路径检查。
- **建议**: (1) 改为不区分大小写匹配；(2) 扩展同义词集合；(3) 配合工具 schema 的 `format: path` 字段。
- **信心水平**: 中-高
- **误报排除**: 已读完整 `checkPathAccess` 方法，确认无任何 fallback 字段；`IToolManager.callTool` 内部也不会再次校验路径。
- **复核状态**: 未复核

### [维度13-6] checkPathAccess 不递归嵌套对象/数组，路径藏在子字段即绕过

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1455-1471`
- **证据片段**:
  ```java
  Map<String, Object> arguments = chatToolCall.getArguments();
  if (arguments == null || arguments.isEmpty()) {
      return null;
  }
  for (Map.Entry<String, Object> entry : arguments.entrySet()) {
      if (!ToolPathArgKeys.KEYS.contains(entry.getKey())) {
          continue;
      }
      Object value = entry.getValue();
      if (!(value instanceof String)) {   // ← 非 String 一律 skip；嵌套 Map/List 不递归
          continue;
      }
      ...
  }
  ```
- **严重程度**: P2
- **现状**: 仅迭代 arguments 的顶层 entry，且只处理 String 值。若工具 schema 为 `{"options": {"file": "/etc/passwd"}}` 或 `{"files": ["/etc/passwd"]}`，则完全绕过 Layer 1 路径检查。
- **风险**: 任何把路径封装在嵌套结构里的工具（batch 操作、配置类工具）完全绕过路径检查。
- **建议**: 改为递归遍历（限深 3-5 层），或基于工具 schema 中的 `format: path` 标记定位真实路径字段。
- **信心水平**: 中
- **误报排除**: 现有 toolkit executor 均用扁平 String 参数，未触发此问题；但对自定义工具是真实漏洞。
- **复核状态**: 未复核

### [维度13-7] DefaultPathAccessChecker 不解析符号链接，前缀式 deny-list 可被 symlink 绕过

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:115-133, 57-63`
- **证据片段**:
  ```java
  public static String normalizePathStatic(String path) {
      String p = path.replace("\\", "/");
      ...
      try {
          Path normalized = Paths.get(p).normalize();   // ← 仅词法 normalize，不解析 symlink
          return normalized.toString().replace("\\", "/");
      } catch (Exception e) { return null; }
  }
  ...
  for (String prefix : SENSITIVE_PREFIX_PATTERNS) {
      if (lower.startsWith(prefix.toLowerCase())) {     // ← 基于字符串前缀匹配
          return PathAccessResult.denyByRule("sensitive_path_prefix", normalized);
      }
  }
  ```
- **严重程度**: P2
- **现状**: `Paths.get(p).normalize()` 只做词法规范化，不调用 `toRealPath()`。若工作目录中存在指向 `/etc` 的符号链接 `link-to-etc`，则路径 `/workspace/link-to-etc/passwd` normalize 后仍不以 `/etc/` 开头 → 不被 deny。
- **风险**: 仅在集成商显式装配 `DefaultPathAccessChecker` 后才是真实可构造的绕过。前置条件：能在 workDir 内创建 symlink（需要已有写权限）。CI/CD、共享开发机、容器挂载场景下常见。
- **建议**: (1) 增加可选 `toRealPath()` 调用；(2) 至少在文档中明确"词法-only，不防 symlink"的限制。
- **信心水平**: 中
- **误报排除**: 此 finding 仅在 DefaultPathAccessChecker 被装配时成立；与 [13-1] 合并考虑时整体暴露面更大。
- **复核状态**: 未复核

### [维度13-8] SingleTurnExecutor 完全跳过所有安全检查、guardrail、审计

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/SingleTurnExecutor.java:26-76`
- **证据片段**:
  ```java
  public CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx) {
      ...
      try {
          ChatRequest request = new ChatRequest(new ArrayList<>(ctx.getMessages()));
          ChatResponse response = chatService.call(request, null);   // ← 直接调 LLM
          ...
          ChatAssistantMessage assistantMsg = response.getMessage();
          ctx.addMessage(assistantMsg);
          ...
          ctx.setStatus(AgentExecStatus.completed);
          ...  // 整个方法无 toolAccessChecker / pathAccessChecker / auditLogger / contentGuardrail 调用
  ```
- **严重程度**: P2
- **现状**: `mode="single-turn"` 的 agent 走 `SingleTurnExecutor`，只调一次 LLM 然后直接返回 assistant message，**全程不调用** `IContentGuardrail.check`、不写任何 `AuditEvent`、不经过任何 security checker。
- **风险**: 若用户在 `agent.xml` 中把处理不可信输入的 agent 配置为 `mode="single-turn"`，则 prompt injection 不被 guardrail 检测、LLM 输出不被 output guardrail 拦截、无审计记录。
- **建议**: (1) 在 SingleTurnExecutor 也接入 `contentGuardrail.check(INPUT/OUTPUT, ...)`；(2) 至少记录一条 AuditEvent；(3) 文档明确 single-turn 的安全语义。
- **信心水平**: 中
- **误报排除**: 已通读 `SingleTurnExecutor` 全文 91 行，确认无任何 guardrail/audit/checker 调用。
- **复核状态**: 未复核

### [维度13-9] ToolPathArgKeys 包含 "input"/"output" 等通用键，引发误判与"狼来了"效应

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/ToolPathArgKeys.java:18-21`
- **证据片段**:
  ```java
  public static final Set<String> KEYS = Set.of(
          "path", "file", "filePath", "filename", "directory", "dir",
          "destination", "output", "input", "source", "target", "cwd"
  );
  ```
- **严重程度**: P3
- **现状**: `call-agent` 工具的 `input` 参数是发给子 agent 的消息体，`send-message` 的 `input` 是消息正文，都不是路径。但 `checkPathAccess` 会把这些字符串当路径过 checker。
- **风险**: 装配真实 checker 后，LLM 发送的消息体里只要出现 `/etc/passwd` 等字符串就会被误拒。这会让集成商倾向于把 checker 关掉，反而放大 [13-1] 的风险。
- **建议**: `checkPathAccess` 排除当前已知非路径工具（call-agent、send-message），或从工具 schema 读取真实路径参数。
- **信心水平**: 中
- **误报排除**: 确认 `CallAgentExecutor`/`SendMessageExecutor` 的 `input` 字段语义，确为消息体。
- **复核状态**: 未复核

### [维度13-10] ParentConstrainedPathAccessChecker 在 root agent 不声明 workDir 时退化为 no-op

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/ParentConstrainedPathAccessChecker.java:91-103`、`engine/ReActAgentExecutor.java:1764-1774`
- **证据片段**:
  ```java
  // ParentConstrainedPathAccessChecker.checkAccess
  Set<String> roots = constraint.getAllowedPathRoots();
  ...
  if (roots == null && rules == null) {
      return delegate.checkAccess(path, ctx);   // ← 双 ABSENT 时直接透传 delegate
  }
  // ReActAgentExecutor.computeOwnDeclaredPathRoots
  private Set<String> computeOwnDeclaredPathRoots(AgentModel agentModel) {
      String workDir = agentModel.getWorkDir();
      if (workDir == null || workDir.trim().isEmpty()) {
          return null;                            // ← root agent 不声明 workDir → ABSENT
      }
      ...
  }
  ```
- **严重程度**: P2
- **现状**: root agent 默认不声明 `workDir`，则整条父子链都没有路径约束。
- **风险**: 与 [13-1] 叠加：默认配置 + 默认不声明 workDir → 所有 agent 都能访问任意路径。
- **建议**: (1) 引擎启动时若检测到 root agent 未声明 workDir 且 pathAccessChecker 是 AllowAll，强制 fail-fast 或 WARN；(2) 文档强调"声明 workDir 是启用路径收敛的前提"。
- **信心水平**: 中-高
- **误报排除**: 这是设计意图（backward-compat），但安全后果需要被显式记录。
- **复核状态**: 未复核

## 正面发现（防御到位）

1. sessionId/agentName 路径注入防御完整（`SessionIds.requireValidIdentifier` 严格正则 + fail-closed）
2. SQL 全部 PreparedStatement 参数化
3. 无 shell exec 字符串拼接
4. `PathAccessDecision.fromString` fail-closed
5. `ParentPermissionConstraint` 交集语义正确（retainAll），子 agent 无法获取父不具备的工具/路径
6. resumeSession/restoreSession 严格校验状态机

## 维度复核结论

| 发现 | 复核结论 | 理由 |
|------|---------|------|
| [维度13-1] 默认 AllowAllPathAccessChecker | **保留 P1** | 源码确证默认装配 AllowAll，DefaultPathAccessChecker 仅在 test。安全默认应 fail-closed。 |
| [维度13-2] 默认 AllowAllToolAccessChecker | **保留 P1** | 同 13-1，DefaultToolAccessChecker deny-list 仅在 test。 |
| [维度13-3] 不装配 AuditLogger 且无 setter | **保留 P1** | grep 确证无字段/无 setter/resolveExecutor 无调用。审计缺失。 |
| [维度13-4] Layer2/3 默认 disabled | **保留 P2** | NoOp/PassThrough 各类源码确证。AutoApproveGate 对 RESTRICTED 放行可验证。 |
| [维度13-5] 参数名大小写敏感绕过 | **保留 P2** | Set.contains 精确匹配确证。 |
| [维度13-6] 不递归嵌套对象 | **保留 P2** | instanceof String 检查确证，无递归。 |
| [维度13-7] symlink 不解析 | **保留 P2** | normalize 非 toRealPath 确证。需特定前置条件。 |
| [维度13-8] SingleTurnExecutor 跳过安全 | **保留 P2** | 全文确证无 guardrail/audit/checker 调用。 |
| [维度13-9] 通用键误判 | **保留 P3** | input/output 语义确为消息体。 |
| [维度13-10] root 无 workDir 退化为 no-op | **保留 P2** | computeOwnDeclaredPathRoots 返回 null 路径确证。 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 13-1 | P1 | engine/DefaultAgentEngine.java:121-126 | 默认装配 AllowAllPathAccessChecker，敏感路径零防护 |
| 13-2 | P1 | engine/DefaultAgentEngine.java:116-119 | 默认装配 AllowAllToolAccessChecker，危险工具 deny-list 失效 |
| 13-3 | P1 | engine/DefaultAgentEngine.java:1149-1180 | 不装配 AuditLogger 且无 setAuditLogger 入口 |
| 13-4 | P2 | security/AutoApproveGate.java 等 | 默认配置 Layer2/3 全链路 disabled |
| 13-5 | P2 | security/ToolPathArgKeys.java | 路径参数名大小写敏感匹配，命名变体绕过 |
| 13-6 | P2 | engine/ReActAgentExecutor.java:1455-1471 | checkPathAccess 不递归嵌套对象/数组 |
| 13-7 | P2 | security/DefaultPathAccessChecker.java:115 | 不解析 symlink，前缀 deny-list 可被绕过 |
| 13-8 | P2 | engine/SingleTurnExecutor.java:26-76 | 完全跳过安全检查、guardrail、审计 |
| 13-9 | P3 | security/ToolPathArgKeys.java | 含 input/output 通用键引发误判 |
| 13-10 | P2 | security/ParentConstrainedPathAccessChecker.java | root agent 不声明 workDir 时退化为 no-op |
