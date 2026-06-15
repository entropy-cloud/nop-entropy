# 维度13：安全与权限模型

## 审核范围（已验证）

完整阅读 `security/` 包全部 57 个文件 + 引擎集成（`ReActAgentExecutor` 2005 行全文、`DefaultAgentEngine` 1201 行全文、`AgentToolExecuteContext`）、子 agent 流程（`CallAgentExecutor`）、端到端测试（`TestSubAgentPathPermissionEndToEnd`、`TestDefaultPathAccessChecker`）。引用类：`ToolPathArgKeys`、`AntPathMatcher`、`SessionIds`。

## 第 1 轮（初审）

### [维度13-01] DefaultAgentEngine 默认装配 AllowAll* 检查器，连保守的硬编码黑名单都不启用
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:104-168` 以及 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:464-499`
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:104-122 (constructor chain)
  public DefaultAgentEngine(IChatService chatService, IToolManager toolManager, ISessionStore sessionStore) {
      this(chatService, toolManager, sessionStore, new AllowAllPermissionProvider());
  }
  public DefaultAgentEngine(..., IPermissionProvider permissionProvider) {
      this(..., permissionProvider, new AllowAllToolAccessChecker());
  }
  public DefaultAgentEngine(..., IToolAccessChecker toolAccessChecker) {
      this(..., toolAccessChecker, new AllowAllPathAccessChecker());
  }
  // ReActAgentExecutor.java:475-477 (Builder.build final fallback)
  permissionProvider != null ? permissionProvider : new AllowAllPermissionProvider(),
  toolAccessChecker  != null ? toolAccessChecker  : new AllowAllToolAccessChecker(),
  pathAccessChecker  != null ? pathAccessChecker  : new AllowAllPathAccessChecker(),
  ```
- **严重程度**: P1
- **现状**: 默认构造的 `DefaultAgentEngine` 在三层 Layer-1 检查（permissionProvider / toolAccessChecker / pathAccessChecker）上都直接装配 `AllowAll*`；连 `DefaultToolAccessChecker`（硬编码拒绝 `bash`/`write-file`/`http-request` 等 9 类危险工具）和 `DefaultPathAccessChecker`（硬编码拒绝 `~/.ssh`、`/etc/`、`.env` 等）这些保守检查器都不会被默认使用，必须显式传入。
- **风险**: 任何用最简构造器 `new DefaultAgentEngine(chatService, toolManager)` 接入的应用，引擎对工具调用、路径访问、permission rule **不做任何检查**。一个被 prompt injection 诱导的 LLM 可以直接调用 `bash` 工具执行任意命令，或读取 `/etc/passwd`、`~/.ssh/id_rsa` 等敏感文件 —— 这些场景在 `Default*` 检查器里是被硬编码拒绝的。安全模块提供了保护，但默认值绕过了它们。
- **建议**: 把 `DefaultPermissionProvider`/`DefaultToolAccessChecker`/`DefaultPathAccessChecker` 改为引擎默认值，把 `AllowAll*` 显式标注为 "测试/受信任环境专用"；或者在 `DefaultAgentEngine` 的 javadoc 与构造器中显著标注此默认不安全。
- **信心水平**: 确定
- **误报排除**: 不是 "缺少权限注解" 类误报 —— 这里的问题是存在更安全的 `Default*` 实现却选择装配 `AllowAll*`，且 `AllowAll*` 实现没有任何文档/注解警告其危险。
- **复核状态**: 未复核

### [维度13-02] DefaultAgentEngine 没有 auditLogger 的 setter/构造器参数，安全审计日志默认静默关闭
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:1135-1160`（构建 executor 时未调用 `.auditLogger(...)`）；`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:478`
- **证据片段**:
  ```java
  // ReActAgentExecutor.java:478 — builder final fallback
  auditLogger != null ? auditLogger : new NoOpAuditLogger(),
  // DefaultAgentEngine.resolveExecutor(...) — builder call list (lines 1135-1160),
  // does NOT include .auditLogger(...). The engine has setPermissionMatrix,
  // setApprovalGate, setDenialLedger, setPostDenialGuard, setSecurityLevelResolver,
  // setLevelHintsProducer, setCheckpointManager — but NO setAuditLogger.
  ```
- **严重程度**: P1
- **现状**: 引擎的所有 6 条 deny 路径（L1 工具/权限/路径、L2 matrix、L3 approval/post-denial-guard、threshold-exceeded）都通过 `auditLogger.log(new AuditEvent(...))` 上报；但 `DefaultAgentEngine` 既没有 setter 也没有构造器参数把它接出来，所以默认走 `NoOpAuditLogger`，所有 ALLOW/DENY/ESCALATE 决策在生产部署里**完全不落审计**。
- **风险**: 一旦发生 prompt injection 或权限误用，事后取证无任何审计痕迹；攻击者可以反复尝试不被察觉。即便宿主应用意识到了风险并设置了 `setApprovalGate`、`setDenialLedger` 等更激进的策略，审计日志仍然空白，因为 `auditLogger` 不可注入。
- **建议**: 在 `DefaultAgentEngine` 增加 `setAuditLogger(IAuditLogger)` 字段 + setter，并在 `resolveExecutor` 的 builder 调用里传入；默认值改为 `new Slf4jAuditLogger()` 而不是 NoOp。
- **信心水平**: 确定
- **误报排除**: 已通过 `grep "setAuditLogger|auditLogger\("` 全仓搜索确认仅在 `ReActAgentExecutor.Builder` 与测试代码出现，`DefaultAgentEngine` 确实没有暴露该接口。
- **复核状态**: 未复核

### [维度13-03] 路径规范化仅做词法归一，不解析符号链接 —— 全局黑名单与 parent 路径根 confinement 均可被 symlink 绕过
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:115-133`（以及被 `RuleBasedPathAccessChecker.resolveAndNormalize`、`ParentConstrainedPathAccessChecker.resolveAndNormalize` / `isUnderAnyRoot`、`ReActAgentExecutor.isUnderAnyRoot` 共用）
- **证据片段**:
  ```java
  public static String normalizePathStatic(String path) {
      String p = path.replace("\\", "/");
      if (p.startsWith("~")) { ... p = HOME + p.substring(1); }
      try {
          Path normalized = Paths.get(p).normalize();   // 仅词法归一
          return normalized.toString().replace("\\", "/"); // 没有 toRealPath() / symlink 解析
      } catch (Exception e) {
          return null;
      }
  }
  ```
- **严重程度**: P1
- **现状**: 路径规范化使用 `Paths.get(p).normalize()`，这只做 `.`/`..` 的词法归一；不会调用 `File.getCanonicalFile()` 或 `Path.toRealPath()` 解析符号链接。所有路径决策（全局敏感路径黑名单、per-agent path-rules、parent-constraint path-root confinement）都基于这种词法归一形式做匹配。
- **风险**: 攻击场景：agent 的工作目录是 `/workspace/project-a`（被 parent constraint 限制），LLM 通过一个写入工具（`write-file`、`bash ln -s`、未在 `DefaultToolAccessChecker` 黑名单中的自定义工具）在 `/workspace/project-a/` 内创建符号链接 `secret -> /etc/passwd` 或 `key -> ~/.ssh/id_rsa`。然后 LLM 调用 `read-file` 读取 `/workspace/project-a/secret`：路径规范化看到的是 `/workspace/project-a/secret`，落在允许根内，且未命中 `SENSITIVE_PREFIX_PATTERNS` —— 检查通过。但实际文件读取时 OS 跟随符号链接，读取的是 `/etc/passwd`。这正是经典的 symlink-chroot-escape 攻击模式。结合 [13-01]（默认装配 AllowAllToolAccessChecker），创建符号链接这一步在默认配置下畅通无阻。
- **建议**: 在 `DefaultPathAccessChecker.normalizePathStatic` 增加 `Files.exists(p) ? p.toRealPath() : lexical_only` 的双轨归一；或文档明确要求所有路径处理工具实现侧也做相同的归一（不可移植、易漏）。至少在 javadoc/AGENTS.md 中显著标注此局限。
- **信心水平**: 很可能（确定逻辑漏洞，"可利用性" 取决于 LLM 是否能创建符号链接，而默认 AllowAll 下这是肯定的）
- **误报排除**: 这不是理论问题 —— 已通过查看 `TestDefaultPathAccessChecker` 确认测试集没有任何 `toRealPath`/`canonical`/`symlink` 用例；与代码里只用词法归一的事实一致。
- **复核状态**: 未复核

### [维度13-04] 路径访问检查只覆盖硬编码的 12 个参数键名 + 仅检查 String 值，存在系统性绕过
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/ToolPathArgKeys.java:18-21`；调用方 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1416-1449`
- **证据片段**:
  ```java
  // ToolPathArgKeys.java:18-21
  public static final Set<String> KEYS = Set.of(
          "path", "file", "filePath", "filename", "directory", "dir",
          "destination", "output", "input", "source", "target", "cwd"
  );
  // ReActAgentExecutor.checkPathAccess(...)
  for (Map.Entry<String, Object> entry : arguments.entrySet()) {
      if (!ToolPathArgKeys.KEYS.contains(entry.getKey())) continue;  // 不在白名单的 key 直接跳过
      Object value = entry.getValue();
      if (!(value instanceof String)) continue;                       // 非 String 直接跳过
      ...
  }
  ```
- **严重程度**: P1
- **现状**: dispatch-loop 的路径访问检查只检查参数 key 在这 12 个名字里、且 value 是 `String` 的字段。其它任何命名约定（`file_path`/`filepath`/`src`/`dst`/`dest`/`outFile`/`template_path`/`working_dir`/`basedir`/`url`/`command` 内含路径）以及复合类型（`List<String>`、嵌套 `Map`、JSON 字符串）都不参与检查。
- **风险**: 任何工具实现只要把路径参数取一个不在 12-键白名单里的名字（非常常见的命名习惯 —— snake_case 派 `file_path`、`working_dir`，缩写派 `src`/`dst`/`dest`，命令派 `command`），就能让路径检查整个被跳过。例如 `bash` 工具的 `command` 参数里可以包含 `cat /etc/passwd`、`scp x y:/etc/...`；git 工具的 `repo`/`url` 参数可以是 `file:///etc/...`。结合 [13-01] 默认 AllowAll，这条绕过使得即便宿主后来接入了 `DefaultPathAccessChecker`，依然存在大量覆盖漏洞。
- **建议**: (a) 由工具 schema（`AiToolModel.getSchema()`）声明每个参数的 "path-ness" 而不是靠全局硬编码键名；(b) 对 `String` 值做启发式 path-extraction（识别含 `/`、`\`、`~` 的子串），至少对 compound 命令型工具（`bash.command`、`http.url`）；(c) 至少在文档中显著标注此限制是 "best-effort"，不要让用户以为路径检查是完备的。
- **信心水平**: 确定
- **误报排除**: 这不是 "缺权限注解" 误报 —— 这里的问题是检查器的"键名白名单"策略本身不完备，与该模块明确承诺的 "Layer-1 path access check" 职责直接冲突。
- **复核状态**: 未复核

### [维度13-05] DefaultPathAccessChecker 是黑名单而非基于工作目录的白名单，大量敏感位置未被覆盖
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:13-76`
- **证据片段**:
  ```java
  private static final Set<String> SENSITIVE_FILENAMES = Set.of(
          "id_rsa", "id_ed25519", ".netrc", ".bash_history", ".zsh_history");
  // SENSITIVE_PREFIX_PATTERNS = HOME/.ssh, HOME/.aws, HOME/.azure,
  // HOME/.config/gcloud, HOME/.kube, /etc/, /boot/, /sys/, /proc/, /root/
  @Override
  public PathAccessResult checkAccess(String path, AgentExecutionContext ctx) {
      ...
      for (String prefix : SENSITIVE_PREFIX_PATTERNS) {
          if (lower.startsWith(prefix.toLowerCase())) return ...deny;
      }
      // 仅检查文件名是否是 .env / 上面 5 个敏感文件名
      ...
      return PathAccessResult.allow();   // 其它一律允许
  }
  ```
- **严重程度**: P2
- **现状**: `DefaultPathAccessChecker` 是一个相对短的黑名单。它不会拒绝：应用源代码（`/app/src`、`/opt/...`）、`.git/`、`/var/log`、`/var/lib`、`/tmp/.X11-unix`、`/proc/[pid]/maps`（被 `/proc/` 覆盖）、`/etc` 是被覆盖了但 macOS 上敏感配置在 `/usr/local/etc`、`/opt/homebrew/etc`；Windows 上 `%APPDATA%`、`C:\Windows\System32` 等都不在列表里。
- **风险**: 即便宿主正确接入了 `DefaultPathAccessChecker`，LLM 仍能读取大量本不该访问的文件（应用源代码、CI 配置、`.git` 历史中的密钥、容器内 `/run/secrets`、k8s service-account token `/var/run/secrets/kubernetes.io/serviceaccount/token` 等）。
- **建议**: 在 `AgentModel.workDir` 声明时切换为 "默认 deny 出 workDir" 模式（与 parent constraint 的 PRESENT({}) 语义一致），把 `DefaultPathAccessChecker` 退化为只保留绝对黑名单作为兜底；同时把容器常见 secrets 路径（`/run/secrets`、`/var/run/secrets`）纳入黑名单。
- **信心水平**: 很可能
- **误报排除**: 不是 "建议增加更多黑名单条目" 的吹毛求疵 —— 核心问题是该检查器作为默认/全局 checker，在缺失 workDir confinement 的链路上承担 "唯一把关者" 的角色（见 [13-01]），其覆盖面远低于这一职责所需。
- **复核状态**: 未复核

### [维度13-06] Slf4jAuditLogger 把不可信路径/原因直接以 `|` 分隔拼入日志，存在日志注入/伪造风险
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/Slf4jAuditLogger.java:11-26`
- **证据片段**:
  ```java
  @Override
  public void log(AuditEvent event) {
      if (event == null) return;
      String message = String.format("AUDIT|%s|session=%s|agent=%s|tool=%s|rule=%s|reason=%s|path=%s",
              event.getDecision(),
              nullSafe(event.getSessionId()),
              nullSafe(event.getAgentName()),
              nullSafe(event.getToolName()),
              nullSafe(event.getMatchedRule()),
              nullSafe(event.getReason()),     // ← 不可信（deny reason 拼接了路径）
              nullSafe(event.getPath()));       // ← 不可信（LLM 提供的路径）
      LOG.info(message);
  }
  ```
- **严重程度**: P2
- **现状**: `event.getPath()` 直接来自 LLM 提供的工具调用参数；`event.getReason()` 在路径拒绝场景里也拼接了路径。两者都未经任何转义就拼入 `|`-分隔的单行日志。
- **风险**: 攻击场景：LLM 调用工具时把路径设为 `\n2026-06-15 12:00:00 AUDIT|ALLOW|session=evil|tool=bash|rule=allow_trusted|reason=operator-approved|path=`，下游 SIEM/日志聚合看到伪造的 ALLOW 条目，混淆取证；或者注入额外的 `|` 字段使 `grep` 出错。这是经典的 log forging (CWE-117/93)。
- **建议**: 对所有不可信字段做转义（`\n`/`\r` → 空格或反斜杠转义，`|` → `\|`），或采用结构化日志（SLF4J MDC + JSON 格式器，让框架处理转义）。
- **信心水平**: 确定
- **误报排除**: 不是 "格式不好看" 风格的吐槽 —— 这是 OWASP/CWE-117 直接命名的日志注入模式，路径来源是 LLM（明确不可信）。
- **复核状态**: 未复核

### [维度13-07] ActionFingerprint 使用 Map.toString() 作为规范化序列化，存在碰撞（同指纹不同意图）
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/ActionFingerprint.java:69-84,97-109`
- **证据片段**:
  ```java
  public static ActionFingerprint compute(String actionKind, Map<String, Object> arguments,
                                          String workDir, Map<String, String> criticalEnv) {
      String kind = actionKind != null ? actionKind : "";
      String canonicalArgs = canonicalMap(arguments);   // = new TreeMap<>(arguments).toString()
      String dir = workDir != null ? workDir : "";
      String canonicalEnv = canonicalStringMap(criticalEnv);
      String material = kind + "|" + canonicalArgs + "|" + dir + "|" + canonicalEnv;
      ...
  }
  private static String canonicalMap(Map<String, Object> map) {
      if (map == null || map.isEmpty()) return "";
      return new TreeMap<>(map).toString();   // → "{a=1, b=2}" 形式，不转义
  }
  ```
- **严重程度**: P2
- **现状**: `TreeMap.toString()` 只是把键排序后写成 `{k1=v1, k2=v2}`，键和值都未做分隔符/边界转义；`material` 用 `|` 连接各字段，字段本身也未转义。这导致不同输入可以产生相同的 material 字符串。
- **风险**:
  - **假阳性 DoS**：合法动作 `{"a": "b,c=d"}` 与恶意动作 `{"a=b,c": "d"}` 的 `toString()` 都是 `{a=b,c=d}`，会算出同一指纹。如果 LLM 先尝试了恶意版本被拒，guard 记录了该指纹；之后 LLM 改用合法参数重试，guard 会误判为 "blind retry" 而阻断合法动作。
  - **actionKind / workDir 也可碰撞**：`kind="a|b", dir=""` 与 `kind="a", dir="b"` 的 material 字符串相同。
  - 文档声称的 "deterministic" 只保证 "相同输入 → 相同指纹"，但反过来（不同输入 → 不同指纹）并不成立 —— 这与 anti-replay 用途相违。
- **建议**: 使用真正的结构化 canonicalization：每字段长度前缀（`len|value`）或 JSON 序列化并对字符串做转义；至少在 `material` 拼接时给每段加固定边界（如 `\u0001` 之类不会出现在用户输入里的字符）。
- **信心水平**: 很可能
- **误报排除**: 不是 "建议加 salt" 类泛泛建议 —— 这里的问题是基本分隔符碰撞，可被具体构造的输入触发（已给出可重现的输入对）。
- **复核状态**: 未复核

### [维度13-08] PathAccessResult / 路径检查器对 URL 编码与 Unicode 归一不做处理，依赖工具实现侧不解码
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:89-97`（`containsTraversal` 仅在 `/` 切分后比对 `..` 字面量）
- **证据片段**:
  ```java
  private boolean containsTraversal(String path) {
      String[] parts = path.replace("\\", "/").split("/");
      for (String part : parts) {
          if ("..".equals(part)) return true;   // 仅字面 ".."，不解码 %2e / \u002e
      }
      return false;
  }
  ```
- **严重程度**: P3
- **现状**: 检查器只识别 ASCII 字面 `..`。如果工具实现（或下层 file API）对路径做 URL 解码（接受 `file:` URI 的工具、`http-request` 类工具、WebDAV/s3 key 类工具），那么 `%2e%2e%2f` 会被工具解码为 `../` 但检查器看不到；同理 Unicode 等价点 `\u002e\u002e` 在做 NFKC 归一的系统上也会绕过。
- **风险**: 取决于下游工具是否会解码。在默认配置下（[13-01] AllowAll 工具层），任何被注册的 URL-aware 工具都可能引入这种绕过；该问题被 [13-04] 放大（路径检查根本不会检查 `url` 参数）。
- **建议**: 在 `normalizePathStatic` 入口处先做一次 URL decode（best-effort）再走 `containsTraversal`；或在文档明确要求所有路径处理工具实现侧"原样接收、不做解码"。
- **信心水平**: 有趣的猜测（需要具体工具实现侧配合才能利用）
- **误报排除**: 与 [13-03] 不同（那是 symlink 解析），与 [13-04] 不同（那是参数键名覆盖）；这是输入归一化顺序问题，独立的子类。
- **复核状态**: 未复核

### [维度13-09] FingerprintPostDenialGuard 仅在内存中追踪被拒动作，进程重启或跨实例时丢失追踪（与 DBDenialLedger 不对称）
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/FingerprintPostDenialGuard.java:33-36,84-90`；对照 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DBDenialLedger.java`
- **证据片段**:
  ```java
  // FingerprintPostDenialGuard.java
  // <p><b>Persistence</b>: in-memory only. A DB-backed successor persists the
  // per-session denied set across session recovery (deferred, symmetric to the
  // {@code DBDenialLedger} successor).
  private final ConcurrentHashMap<String, Set<String>> deniedFingerprints = new ConcurrentHashMap<>();
  ...
  @Override public void reset(String sessionId) {
      if (sessionId == null) return;
      deniedFingerprints.remove(sessionId);   // 进程重启也会自然清空（hashmap）
  }
  ```
- **严重程度**: P3
- **现状**: 同一个 defense-in-depth 链上，`IDenialLedger` 提供了 `DBDenialLedger` 实现用于跨进程持久化拒绝计数，但 `IPostDenialGuard` 没有对称实现 —— 只有内存版本。重启 / 故障切换到另一进程后，被拒动作的指纹集合丢失。
- **风险**: 一个被拒的敏感动作（如 `bash -c "rm -rf /"`）在进程崩溃后重启时可以再次提交 —— guard 看不到历史，把它当作新动作处理；如果新动作通过了 Layer-1/2/3 检查（例如 Layer 配置在重启后改宽松了，或临时 AllowAll 接入），就会执行。这是与 sticky-pause 协议（`IDenialLedger` 的恢复语义）的不对称破口。
- **建议**: 提供与 `DBDenialLedger` 对称的 `DBFingerprintPostDenialGuard`，复用同一 `ai_agent_denial` 表或新增 `ai_agent_denial_fingerprint` 表持久化指纹；或文档显著标注此非对称局限。
- **信心水平**: 很可能
- **误报排除**: 不是 "缺一个实现" 类泛泛建议 —— 当前模块自身在设计文档里承认这是 deferred successor，但 [13-09] 强调的是与 sibling `DBDenialLedger` 的不对称会破坏 recovery 语义。
- **复核状态**: 未复核

## 维度复核结论

待复核。

## 子项复核结论

待复核。

## 最终保留项

待复核后填写。
