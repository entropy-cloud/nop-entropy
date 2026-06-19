# 维度 13：安全与权限模型 — nop-ai-agent

## 审计范围

完整阅读 `security/` 包 73 个文件（路径访问、工具访问、沙箱、审批、账本、租户、内容信任、审计、护栏、权限矩阵、principal）。SQL 注入 + 命令注入全模块扫描。

## 关键正面结论（先列）

- `DefaultAgentEngine` 默认装配经代码确认（line 181-192, 469-471）：`DefaultToolAccessChecker`、`DefaultPathAccessChecker`、`Slf4jAuditLogger`、`DefaultSecurityLevelResolver`、`DefaultPermissionMatrix`、`DefaultApprovalGate`、`DefaultDenialLedger`、`DefaultPostDenialGuard`、`NoOpSandboxBackend`。**没有**生产路径默认装配任何 `AllowAll*` / `AutoApproveGate` / `NoOp*Logger` / `NoOpDenialLedger` / `PassThrough*`。设计文档 §4.6/§4.7/§4.8/§4.9 的 "secure by default" 承诺**已在代码层兑现**。
- `warnIfInsecureDefaults`（line 531-604）对所有 8 个组件做 `instanceof` 检查并发出一次性 WARN，覆盖完整。
- `DockerSandboxBackend` / `NoOpSandboxBackend` 均使用 `ProcessBuilder(List<String>)` 而非 `Runtime.exec(String)` —— 命令注入通过 ProcessBuilder argv 数组隔离。
- 所有 DB store 使用 `PreparedStatement` + `?` 占位符；列名来自 `static final String` 常量，**未发现**用户输入直接拼入 SQL。
- `ThreadLocalTenantResolver.set/clear` 在 engine 五个入口均有 try/finally 配对。
- 子 Agent 权限继承机制（`ParentPermissionConstraint`）使用 effective（clamped）集合传递，符合设计 §4.4。

## 第 1 轮（初审）

### [维度13-1] 路径规范化仅做词法分析，无法防御符号链接（Symlink）穿越

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:115-133`
- **证据片段**:
  ```java
  public static String normalizePathStatic(String path) {
      String p = path.replace("\\", "/");
      if (p.startsWith("~")) {
          if (HOME.isEmpty()) { return null; }
          if (p.equals("~") || p.startsWith("~/")) {
              p = HOME + p.substring(1);
          }
      }
      try {
          Path normalized = Paths.get(p).normalize();
          return normalized.toString().replace("\\", "/");
      } catch (Exception e) {
          return null;
      }
  }
  ```
- **严重程度**: P1
- **现状**: 路径规范化仅调用 `Paths.get(p).normalize()`，这是**词法规范化**（消除 `.` / `..` 段），**不解析符号链接**。模块内全量扫描确认**无任何** `toRealPath()` / `getCanonicalPath()` / `File.getCanonicalFile()` 调用。设计文档 §4.3 明确要求防御"符号链接绕过"，但实现未提供。
- **风险**: 攻击者（或被诱导的 LLM）在 workspace 内创建符号链接 `link -> /etc`，然后请求读取 `/workspace/link/passwd`。规范化返回 `/workspace/link/passwd`，**不**匹配敏感前缀 `/etc/`，路径检查放行。下游工具读取实际文件 `/etc/passwd`。该攻击链同样适用于 `~/.ssh/`、`~/.aws/`、`/proc/` 等所有敏感前缀保护。对于 DockerSandboxBackend，Docker 守护进程会解析宿主机符号链接，将 `/workspace/link -> /etc` 的绑定挂载解析为 `/etc`，容器内 `cat /workspace/shadow` 即可读取 `/etc/shadow`（即使 `--network none` 阻断网络外发，stdout 仍返回内容给调用方）。
- **建议**: 在路径规范化后追加 `Files.readSymbolicLinks(...)` / `toRealPath()` 解析（注意 TOCTOU 竞争）；或在文档明确声明"不防御符号链接，集成商需在容器/操作系统层防御"并降级设计承诺。
- **信心水平**: 确定
- **误报排除**: 不是"理论性"问题——`Files.toRealPath` 是 Java NIO 标准防御手段，设计文档 §4.3 把符号链接列为必须防御项；当前实现确实未调用任何符号链接解析 API。
- **复核状态**: 未复核

---

### [维度13-2] `DefaultPathAccessChecker.checkAccess` 对 null/空路径返回 allow（fail-open）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:43-46`
- **证据片段**:
  ```java
  public PathAccessResult checkAccess(String path, AgentExecutionContext ctx) {
      if (path == null || path.trim().isEmpty()) {
          return PathAccessResult.allow();
      }
      ...
  }
  ```
- **严重程度**: P2
- **现状**: 当路径参数为 null、空串或纯空白时，直接返回 `allow()` 而不是 `deny()` 或委托给 wrapped checker。这是一个 fail-open 决定。
- **风险**: 任何接受可选路径参数的工具（如 `cat` 无参数时读取 stdin、`ls` 无参数时列出 CWD）会绕过路径检查。如果工具实现在路径为空时回退到敏感默认（例如 `~/.ssh/id_rsa` 或 `/etc/passwd`），路径检查无法拦截。同时也意味着上游 `RuleBasedPathAccessChecker` / `ParentConstrainedPathAccessChecker` 在路径为空时都直接委托回此 checker 的 `allow()`，三层都 fail-open。
- **建议**: 改为 fail-closed（返回 deny），或在文档明确"路径检查仅在路径非空时生效，工具必须自行防御空路径回退行为"。当前实现既未 fail-closed 也未文档化。
- **信心水平**: 很可能
- **误报排除**: 设计文档 §4.2/§4.3 多处声明"deny-by-default"和"fail-closed"原则。null 路径返回 allow 与这些原则矛盾，是真实偏离。
- **复核状态**: 未复核

---

### [维度13-3] `DefaultPathAccessChecker` 含 NUL 字节、URL 编码、Unicode 规范化的路径未清理

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:115-133`
- **证据片段**: 见 [维度13-1] 代码片段。仅做反斜杠转正斜杠 + tilde 展开 + `Paths.get(p).normalize()`，无 NUL 字节检查、无 URL 解码、无 Unicode NFC 规范化。
- **严重程度**: P2
- **现状**: 未对 NUL 字节（`\u0000`，OS 层会截断路径）、URL 编码（`%2e%2e%2f` 解码后是 `../`）、Unicode 规范化（如全角 `．．／` 或 NFC 合成字符）做防御。
- **风险**:
  - NUL 字节：Java String 含 `\0` 在传递给 native 文件 API 时被截断，可能导致路径检查看到长路径、实际 IO 看到截断后的短路径。
  - URL 编码：如果 LLM 输出 URL 编码路径（LLM 偶尔会这样），`%2e%2e%2f` 不会被识别为 `../`，敏感路径保护可被绕过。
  - Unicode：在 macOS HFS+（已规范化 NFC）和某些 Linux locale 下，合成字符可能产生匹配差异。
- **建议**: 在 `normalizePathStatic` 早期加入：NUL 字节检测（含则 deny）、URL 解码（先 `URLDecoder.decode` 再 normalize）、`Normalizer.normalize(p, Form.NFC)`。
- **信心水平**: 很可能（NUL 字节确定未防御；URL/Unicode 防御取决于上游是否已清理）
- **误报排除**: 任务说明明确要求检查这三项；代码确认无任何相关处理。
- **复核状态**: 未复核

---

### [维度13-4] `DefaultToolAccessChecker` 硬编码 deny-list 不完整且与设计文档不一致

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultToolAccessChecker.java:9-18`
- **证据片段**:
  ```java
  private static final Set<String> DENIED_TOOLS = Set.of(
          "bash",
          "write-file",
          "delete-file",
          "move-file",
          "patch-file",
          "apply-delta",
          "http-request",
          "graphql-query"
  );
  ```
- **严重程度**: P2
- **现状**: deny-list 仅含 8 个工具名（kebab-case）。设计文档 §4.2 同时提到 `shell_exec`、`file_write`、`file_delete`、`git_push`（snake_case）作为示例，这些**均不在** deny-list 中。LLM 实际可能调用的常见危险工具名变体——`shell-exec`、`shell_exec`、`shell`、`sh`、`exec`、`terminal`、`eval`、`git-push`、`git_push`、`curl`、`wget`、`http`、`http-get`、`http-post`、`fetch`、`download`——**全部**不在 deny-list 中。`DefaultLevelHintsProducer.HIGH_IMPACT_TOOLS`（line 49-52）反而把 `shell.exec`、`code.exec`、`bash`、`sh`、`rm` 等都列为 high-impact，与 deny-list 的工具命名口径不一致。
- **风险**: 任何使用 deny-list 未覆盖命名的危险工具实现都将被默认放行。在默认装配下，Layer 2 resolver 是 trusted-by-default（`shell.exec` → ELEVATED，被批准），所以默认情况下 `shell-exec` 工具可被无条件执行。
- **建议**: (1) 统一 deny-list 与 `DefaultLevelHintsProducer` 的命名口径（同一规范化函数）；(2) 扩展 deny-list 至少覆盖 `shell-exec`/`shell_exec`/`exec`/`git-push`/`git_push` 等设计文档列出的变体；(3) 设计文档 §4.2 承诺硬编码 deny 可经 XDSL 外部化但实现仍是 Java 常量——考虑外部化到配置。
- **信心水平**: 很可能
- **误报排除**: 这是 `toolAccessChecker`（默认装配的 Layer 1 唯一硬性安全边界），deny-list 的命名覆盖直接决定哪些工具被默认阻断。
- **复核状态**: 未复核

---

### [维度13-5] `Slf4jAuditLogger` 输出遗漏 `actorId` 与 `timestamp` 关键审计字段

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/Slf4jAuditLogger.java:11-26`
- **证据片段**:
  ```java
  @Override
  public void log(AuditEvent event) {
      if (event == null) {
          return;
      }
      String message = String.format("AUDIT|%s|session=%s|agent=%s|tool=%s|rule=%s|reason=%s|path=%s",
              event.getDecision(),
              nullSafe(event.getSessionId()),
              nullSafe(event.getAgentName()),
              nullSafe(event.getToolName()),
              nullSafe(event.getMatchedRule()),
              nullSafe(event.getReason()),
              nullSafe(event.getPath()));
      LOG.info(message);
  }
  ```
- **严重程度**: P2
- **现状**: `AuditEvent` 携带 9 个字段，但 `Slf4jAuditLogger` 输出**只格式化了 7 个**——**遗漏 `actorId` 和 `timestamp`**。`event.getActorId()` 与 `event.getTimestamp()` 永远不被使用。
- **风险**: 安全审计的核心要求是"谁在何时做了什么决策"。当前默认 logger 输出**无 actorId** 和**无 timestamp**。`audit-readiness-analysis.md` §4 已识别此为审计就绪度缺陷但实现未修。
- **建议**: 在格式串中加入 `|actor=%s|ts=%d`，使用 `event.getActorId()` 和 `event.getTimestamp()`。
- **信心水平**: 确定
- **误报排除**: dispatch path 上 actorId 参数**全部传 `null`**（ReActAgentExecutor.java:1645-1648）——意味着即使 logger 输出也只显示空。这是 dispatch path 上 actorId 完全未传递的结构性问题，不只是 logger 显示问题。
- **复核状态**: 未复核

---

### [维度13-6] `Slf4jAuditLogger` 未清洗换行符，存在日志注入（Log Injection / Log Forging）风险

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/Slf4jAuditLogger.java:16-25`
- **证据片段**: 见上。`event.getPath()` 和 `event.getReason()` 是工具调用参数/拒绝原因，可被 LLM 控制。例如 `path = "/etc/passwd\nAUDIT|ALLOW|session=admin|tool=bash|rule=none|reason=ok|path="`。String.format 将换行符直接拼入消息，SLF4J 输出多行，第二行是伪造的 ALLOW 审计条目。
- **严重程度**: P2
- **现状**: path 和 reason 直接拼接未清洗。
- **风险**: 攻击者（通过诱导 LLM）可注入伪造的审计日志条目，混淆真实拒绝事件。在事后审计调查时，调查者无法区分真实与伪造条目。日志聚合系统（如 ELK、Splunk）会按行索引，每行作为独立事件。
- **建议**: 在 `nullSafe(...)` 之外加 `sanitize(s)`：替换 `\n`/`\r` 为 `\\n`/`\\r`，或使用 SLF4J 的结构化日志。
- **信心水平**: 很可能
- **误报排除**: `path` 的来源链路是 `ReActAgentExecutor.checkPathAccess` —— `pathValue` 直接来自 `chatToolCall.getArguments()` 的 string 字段，是 LLM 输出，**无任何**换行符清洗。这是真实可控的注入源。
- **复核状态**: 未复核

---

### [维度13-7] `DockerSandboxBackend.buildDockerCommand` 工作目录挂载使用未验证的宿主路径

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DockerSandboxBackend.java:241-247`
- **证据片段**:
  ```java
  // Working directory mapping (read-write mount + workdir).
  if (request.getWorkingDirectory() != null) {
      String hostPath = request.getWorkingDirectory().getAbsolutePath();
      cmd.add("-v");
      cmd.add(hostPath + ":" + CONTAINER_WORKDIR);
      cmd.add("--workdir");
      cmd.add(CONTAINER_WORKDIR);
  }
  ```
- **严重程度**: P1
- **现状**: 容器的工作目录直接来自 `SandboxRequest.getWorkingDirectory()`，以**读写**模式（无 `:ro` 标记）挂载到容器 `/workspace`。`hostPath` 未经任何路径白名单校验，也未与 engine 的 `DefaultPathAccessChecker` 敏感路径 deny-list 联动。
- **风险**: 攻击场景：
  1. LLM 调用 shell-exec 工具，参数 `cwd=/etc`（或 `cwd=/workspace/link-to-etc`，配合 [维度13-1] 符号链接）。
  2. Shell-exec 构造 `SandboxRequest.workingDirectory = new File("/etc")`。
  3. `DockerSandboxBackend` 执行 `docker run -v /etc:/workspace --workdir /workspace alpine cat shadow`。
  4. 容器内 `cat /workspace/shadow` 读取 `/etc/shadow`，stdout 返回给 engine 调用方。
  5. 即使 `--network none` 阻断网络外发，敏感文件内容仍通过工具返回值回到 LLM。

  此外，`hostPath` 含冒号（如 macOS `/Users/a:b/file`）会破坏 `-v host:container` 语法解析。
- **建议**: (1) 在 `buildDockerCommand` 入口对 `hostPath` 做白名单校验或调用 `DefaultPathAccessChecker.checkAccess(hostPath)`；(2) 校验 `hostPath` 不含冒号或在挂载前对冒号 escape；(3) 默认以 `:ro` 挂载（如可读写在工具语义上非必需）。
- **信心水平**: 很可能（攻击链每一环都有代码佐证；唯一变量是上游 shell-exec 实现是否真的从 LLM 参数构造 workingDirectory）
- **误报排除**: ProcessBuilder 的 argv 数组隔离了 shell 解释，**但 Docker 自身的 `-v` 解析器仍按 `host:container` 切分**——这是 Docker CLI 的语义，与 ProcessBuilder 无关。宿主路径校验缺失是真实漏洞。
- **复核状态**: 未复核

---

### [维度13-8] `DockerSandboxBackend` 把 `cpuSeconds` 错误映射到 `--cpus` 标志（语义+资源限制 bug）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DockerSandboxBackend.java:231-235`
- **证据片段**:
  ```java
  // Resource limits.
  cmd.add("--cpus");
  cmd.add(Integer.toString(config.getCpuSeconds()));
  cmd.add("--memory");
  cmd.add(config.getMemoryMb() + "m");
  ```
  ```java
  // SandboxConfig.java:35-36
  /** Default CPU budget in seconds (design §7.1). */
  public static final int DEFAULT_CPU_SECONDS = 30;
  ```
- **严重程度**: P2
- **现状**: `--cpus` 标志在 Docker 语义中表示**CPU 配额数**（小数，如 `1.5` 表示 1.5 个 CPU 核心），而 `config.getCpuSeconds()` 是**CPU 时间预算（秒）**。默认值 `30` 被解释为"30 个 CPU 核心"——大多数宿主机根本没有这么多核心，Docker 会接受但 cgroup 限额无意义。
- **风险**: 资源限制失效。设计 §7.1 表声称 `cpuSeconds=30` 是基线限制，但实际 `--cpus 30` 是给容器 30 个 CPU 核心配额——资源耗尽型 DoS 攻击（如 `while true; do :; done`）不被有效限制。
- **建议**: 改用 `--cpu-quota` + `--cpu-period`，或更名 `cpuSeconds` → `cpuCount` 并改为浮点（语义对齐 Docker `--cpus`）。
- **信心水平**: 确定（Docker `--cpus` 语义为 CPU 核心数，公开文档可查）
- **误报排除**: 这是实际可执行的代码路径，不是文档之争；配置默认值 30 与 `--cpus` 含义明确不匹配。
- **复核状态**: 未复核

---

### [维度13-9] `DockerSandboxBackend` 环境变量注入 Docker 选项（参数注入风险）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DockerSandboxBackend.java:249-252`
- **证据片段**:
  ```java
  // Environment overlay.
  for (Map.Entry<String, String> e : request.getEnvironmentVariables().entrySet()) {
      cmd.add("-e");
      cmd.add(e.getKey() + "=" + e.getValue());
  }
  ```
- **严重程度**: P3
- **现状**: 环境变量 key 与 value 直接拼接为 `-e KEY=VALUE`，未对 key 校验是否以 `-` 开头、是否含空格等控制字符。虽然 Docker 的 argv 解析器在看到 `-e` 后会消费下一个 argv 元素作为 env spec，但 Docker 不同版本、不同 CLI backend（如 Podman 兼容层）的解析行为存在差异。
- **风险**: 跨 Docker 版本/实现存在参数注入不确定性。某些边缘情况下，env key 形如 `--privileged=\n` 或包含特殊字符可能被错误解析为 Docker 标志，从而提权容器。
- **建议**: 在拼接前对 key 做 `^[A-Za-z_][A-Za-z0-9_]*$` 正则校验，拒绝非法 key。
- **信心水平**: 有趣的猜测（具体可利用性依赖 Docker 版本和实验确认；防御性编程原则建议校验）
- **误报排除**: ProcessBuilder 隔离了 shell 解释，**但 Docker CLI 自身的 argv 解析器**是另一层解析；env key 若由 LLM 影响则可被攻击者控制。需要追溯 shell-exec 实现确认。
- **复核状态**: 未复核

---

### [维度13-10] `DefaultPermissionProvider` 不是默认装配，Layer 1 实际仅靠硬编码 deny-list 兜底

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:469` 和 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/AllowAllPermissionProvider.java:6-8`
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:469
  this.permissionProvider = permissionProvider != null ? permissionProvider : new AllowAllPermissionProvider();

  // AllowAllPermissionProvider.java:6-8
  public Permission resolve(String toolName, String agentName, String sessionId) {
      return Permission.allow();
  }
  ```
- **严重程度**: P2
- **现状**: `DefaultAgentEngine` 的 `permissionProvider` 字段默认是 `AllowAllPermissionProvider`（恒返回 allow）。这意味着 dispatch flow 中 Layer 1 的"权限派生"是 no-op，真正的工具名 deny-list 只来自 `toolAccessChecker`（8 个硬编码名字，见 [维度13-4]）。`DefaultPermissionProvider`（含 3-source 合并、deny-first 语义）**存在但默认不装配**。
- **风险**: 在默认装配下，整个 Layer 1 工具权限模型 = 硬编码 8 名 deny-list + 其他全允许。任何工具实现只要命名避开 8 个 deny 名（极易），就完全绕过 Layer 1。Layer 2/3 仅在集成商显式装配功能性 resolver/matrix/gate 时才生效。
- **建议**: (1) 设计文档明确说明"permissionProvider 默认 AllowAll 是 by-design，因 toolAccessChecker 已提供硬编码 deny-list"，或 (2) 评估是否将某种"默认安全"变体作为默认。
- **信心水平**: 很可能
- **误报排除**: `DefaultToolAccessChecker` 的存在确实提供了"危险工具默认拒绝"的安全边界，所以这不是"完全裸奔"。但 deny-list 模式的脆弱性（命名覆盖不全）是真实问题。
- **复核状态**: 未复核

---

### [维度13-11] `resumeSession` 无身份/权限校验，`approver` 仅作审计字段

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:2101-2130`
- **证据片段**:
  ```java
  @Override
  public CompletableFuture<AgentExecutionResult> resumeSession(String sessionId, String approver, String reason) {
      AgentSession session = sessionStore.get(sessionId);
      if (session == null) {
          throw new NopAiAgentException("resumeSession failed: session not found: sessionId=" + sessionId);
      }
      if (session.getStatus() != AgentExecStatus.paused) {
          throw new NopAiAgentException("resumeSession failed: session is not paused (status=..."
                  + "), only paused sessions can be resumed: sessionId=" + sessionId);
      }
      // 无任何对 approver 身份的校验；无任何 RBAC/权限检查
      String agentName = session.getAgentName();
      int preResetDenialCount = denialLedger.getDenialCount(sessionId);
      denialLedger.reset(sessionId);
      ...
      resumePayload.put("approver", approver != null ? approver : "");
      ...
  }
  ```
- **严重程度**: P2
- **现状**: 设计 §6.2 决策 6 说"`resumeSession` 调用本身即设计要求的人类干预"。但实现层**没有**任何验证 `approver` 是真实人类、有权限恢复该 session 的机制。任何能调用 `resumeSession` 的代码路径都能清除任意 paused session 的拒绝计数。`approver` 字段仅作为审计 payload（line 2126），不参与任何强制。
- **风险**: 攻击场景：
  1. Agent 因触发拒绝阈值 3 次被 paused（`DefaultDenialLedger` 默认阈值）。
  2. 这是设计上的"安全暂停"——攻击者的工具调用被阻断。
  3. 攻击者（或被诱导的 LLM 通过另一工具）调用 `resumeSession(sessionId, "attacker", "legit reason")`。
  4. 拒绝计数被清零，session 恢复运行，攻击者继续触发被拒工具。

  这等于"暂停可被攻击者自己解除"，违背了 sticky-pause 治理语义。
- **建议**: 在 `resumeSession` 入口接入 RBAC 检查（要求 `PrincipalRole.OPERATOR` 或专门恢复权限），或要求 approver 提供独立审批令牌（外部审批系统签发）。设计文档应明确"approver 字段的强制语义 vs 仅审计语义"。
- **信心水平**: 很可能（设计文档承认这是 deferred enhancement；但生产部署的真实风险需要在审计报告中记录）
- **误报排除**: 设计 §6.2 决策 6(a) 明确说"`resumeSession` 调用本身即设计要求的人类干预"——这是**设计选择**，不是 bug。但选择本身在没有 RBAC 层的系统中存在被绕过风险，应在审计中标记为"设计性风险，需集成商在调用方加控制"。
- **复核状态**: 未复核

---

### [维度13-12] `resumeSession` 跨租户重置拒绝账本（多租户隔离漏洞）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:2174-2196` 和 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DBDenialLedger.java:212-234`
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:2174-2180
  return CompletableFuture.supplyAsync(() -> {
      // Plan 232: set/clear tenant context on the worker thread.
      // resumeSession has no Principal source in the foundational
      // slice (no request parameter), so the tenant context is null
      // = all data visible (recovery-path semantics). The structure
      ThreadLocalTenantResolver.set(null);  // <-- 显式设为 null
      ...
  ```
  ```java
  // DBDenialLedger.java:212-228
  public void reset(String sessionId) {
      if (sessionId == null) { return; }
      String tenant = currentTenant();  // <-- resume 时返回 null
      String deleteSql = "DELETE FROM " + AiAgentDenialTable.TABLE_NAME
              + " WHERE " + AiAgentDenialTable.COL_SESSION_ID + " = ?";
      if (tenant != null) {  // <-- 跳过 tenant 过滤
          deleteSql += TenantSql.whereTenant(AiAgentDenialTable.COL_TENANT_ID);
      }
      ...
  }
  ```
- **严重程度**: P2
- **现状**: `resumeSession` 显式将 tenant 上下文设为 null（注释说"recovery-path semantics"），随后调用 `denialLedger.reset(sessionId)`。在 `DBDenialLedger.reset` 中，当 tenant 为 null 时，DELETE 语句**不加** `WHERE TENANT_ID = ?` 过滤，即**跨所有租户**删除该 sessionId 的拒绝记录。
- **风险**: 多租户部署场景：租户 A 的 session 因被攻击而 paused，积攒了拒绝记录（`TENANT_ID = 'A'`）。同一 sessionId 在租户 B 中也存在拒绝记录。`resumeSession` 删除租户 A 的记录时连带删除租户 B 的记录。更严重的是：如果不同租户使用同一 sessionId（如租户共享某些约定），一个租户的恢复操作清空了其他租户的拒绝状态。
- **建议**: `resumeSession` 应从 session 本身（已加载的 `AgentSession`）读取 tenantId 并传入 ThreadLocal，而不是设为 null。`AgentSession` 应携带 `tenantId` 字段（或在 sessionStore 中可查）。
- **信心水平**: 很可能
- **误报排除**: 设计文档 §6.2 隐含期望 denial-ledger 是 per-session（且按 tenantId 隔离），但 resumeSession 路径的 tenant=null 是真实代码事实。
- **复核状态**: 未复核

---

### [维度13-13] `RuleBasedPathAccessChecker` 使用大小写敏感的 glob 匹配，在 Windows/macOS HFS+ 可绕过

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/RuleBasedPathAccessChecker.java:64, 134`
- **证据片段**:
  ```java
  private static final IPathMatcher MATCHER = new AntPathMatcher();
  ...
  if (MATCHER.match(matchPattern, resolved)) {
      PathAccessDecision decision = rule.getAccessDecision();
      ...
  }
  ```
- **严重程度**: P2
- **现状**: `AntPathMatcher` 默认大小写敏感。`DefaultPathAccessChecker` 对敏感前缀做了 `toLowerCase()` 比对（line 57-60），但 `RuleBasedPathAccessChecker` 和 `ParentConstrainedPathAccessChecker`（line 128）都直接调用 `MATCHER.match(pattern, resolved)`，**不做大小写折叠**。
- **风险**: 在大小写不敏感的文件系统（Windows NTFS、macOS HFS+/APFS 默认、FAT32）上，规则 `DENY /etc/**` 不会匹配 `/ETC/passwd` 或 `/Etc/passwd`。LLM 输出大小写变体即可绕过 per-agent path-rules 和 parent path-rules。设计 §4.3 列出"大小写折叠（Windows）"为必须防御项。
- **建议**: 在 `RuleBasedPathAccessChecker` / `ParentConstrainedPathAccessChecker` 的 match 调用前，将 `pattern` 和 `resolved` 都 `toLowerCase(Locale.ROOT)`；或使用 `AntPathMatcher` 的 case-insensitive 变体。
- **信心水平**: 很可能（AntPathMatcher 默认行为可查；设计文档明确要求大小写折叠）
- **误报排除**: 与 [维度13-1] 不同，这是独立的另一类大小写变体绕过；与 `DefaultPathAccessChecker` 已做小写化的实现也不一致。
- **复核状态**: 未复核

---

### [维度13-14] `DBDenialLedger` / `DefaultDenialLedger` / `FingerprintPostDenialGuard` 无 TTL/清理任务

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DBDenialLedger.java:122-260`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultDenialLedger.java:43-104`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/FingerprintPostDenialGuard.java:39-90`
- **证据片段**:
  ```java
  // DefaultDenialLedger.java:43
  private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
  // 无任何 TTL/eviction/cleanup；仅 reset(sessionId) 显式移除
  ```
- **严重程度**: P3
- **现状**: 三个状态持有组件均**无 TTL/eviction/cleanup 机制**。`DefaultDenialLedger.counts` 永久增长直到 OOM；`DBDenialLedger` 表无清理任务；`FingerprintPostDenialGuard.deniedFingerprints` 同样永久累积。
- **风险**: 长时间运行的引擎处理大量 session 时：(1) `DefaultDenialLedger` JVM 堆内存无限增长最终 OOM；(2) `DBDenialLedger` 表无限增长导致查询性能下降、磁盘耗尽。即使 session 自然结束，其拒绝记录仍残留。
- **建议**: (1) 为 `DefaultDenialLedger` / `FingerprintPostDenialGuard` 加 `sessionStore.cleanup` 回调或弱引用 key；(2) 为 `DBDenialLedger` 提供 scheduled cleanup SQL `DELETE WHERE CREATED_AT < ?`；(3) 设计文档明确 retention 策略。
- **信心水平**: 确定（无任何清理代码，无任何 scheduled task 注册点）
- **误报排除**: 这不是"过度设计建议"——Long-running agent 引擎是设计目标，session 数量随时间累积是确定性场景；现有 `reset()` 仅在显式 `resumeSession` 时调用，已 completed/failed 的 session 永不触发。
- **复核状态**: 未复核

---

### [维度13-15] `NoOpContentGuardrail` 是默认装配，Layer 2 内容护栏（prompt 注入检测、`<untrusted>` 信封）未实现

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/NoOpContentGuardrail.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:472`
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:472
  this.contentGuardrail = contentGuardrail != null ? contentGuardrail : NoOpContentGuardrail.noOp();
  ```
- **严重程度**: P3
- **现状**: 设计文档 §5.2 描述了 Layer 2 内容护栏（4 类 prompt 注入检测：prompt_override / role_hijack / exfiltration / invisible_char；`<untrusted>` 信封包裹；PII 检测）。模块中**仅有**接口 + NoOp 实现，**无任何功能性实现**。
- **风险**: Web fetch 工具拉取的页面（含 prompt 注入）直接进入 LLM 上下文。如果默认装配下 `http-request` 工具被 Layer 1 deny-list 阻断（已实现），此攻击被阻断；但任何**不在** deny-list 的网络工具会被无护栏执行。
- **建议**: 文档明确"默认无内容护栏是 by-design，集成商需在 LLM 输入边界自行加护栏"；或实现 OpenSquilla 4 类正则检测作为 shipped 默认。
- **信心水平**: 确定
- **误报排除**: 设计 §5.2 文档明确这是 Layer 2 deferred 特性；但作为安全审计，必须记录"默认部署的引擎对 prompt 注入无防御"。
- **复核状态**: 未复核

---

### [维度13-16] `ToolPathArgKeys.KEYS` 路径参数键白名单不完整，非标准键名绕过路径检查

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/ToolPathArgKeys.java:18-21`
- **证据片段**:
  ```java
  public static final Set<String> KEYS = Set.of(
          "path", "file", "filePath", "filename", "directory", "dir",
          "destination", "output", "input", "source", "target", "cwd"
  );
  ```
- **严重程度**: P2
- **现状**: `ReActAgentExecutor.checkPathAccess`（line 2570-2573）只对参数 key 在此 Set 中的字符串值做路径检查。任何工具定义中使用**非标准键名**表示路径——如 `dataFile`、`repository`、`module`、`project`、`artifact`、`backup`、`configFile`、`scriptPath`、`outputPath`——其值**完全不被路径检查覆盖**。
- **风险**: 自定义工具实现（集成商或社区提供）若用非标准键名暴露路径参数，LLM 可传入 `/etc/passwd` 或 `~/.ssh/id_rsa` 而不触发任何路径检查。
- **建议**: (1) 文档明确"工具实现必须使用 ToolPathArgKeys.KEYS 中的标准键名，否则需自行做路径校验"；(2) 考虑改用工具元数据声明（如 tool DSL 中标注参数 `kind=path`）；(3) 扩展白名单至常见变体。
- **信心水平**: 很可能
- **误报排除**: 这是真实可达绕过路径——LLM 可控 `chatToolCall.getArguments()` 的键和值；只要工具实现使用非标准键名接收路径，框架层完全失明。
- **复核状态**: 未复核

---

### [维度13-17] `PathAccessResult.denyByRule` 自动拼接用户路径进入拒绝原因，下游审计/日志未清洗

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/PathAccessResult.java:25-27`
- **证据片段**:
  ```java
  public static PathAccessResult denyByRule(String ruleName, String path) {
      return new PathAccessResult(false, "Path denied by rule '" + ruleName + "': " + path, ruleName);
  }
  ```
- **严重程度**: P3
- **现状**: `denyByRule` 把用户提供的 `path`（LLM 可控）直接拼入拒绝原因字符串。该原因字符串随后：(1) 通过 `ChatToolResponseMessage.error(...)` 返回给 LLM；(2) 通过 `AuditEvent.reason` 进入 `Slf4jAuditLogger`；(3) 通过 `AgentEvent` payload 发布。整个链路无换行/控制字符清洗。
- **风险**: 与 [维度13-6] 叠加——`normalized` 是用户路径，进入审计日志后可触发日志注入。同时，工具错误消息直接回显给 LLM，可能泄露路径规范化逻辑细节。
- **建议**: 拒绝原因中不回显完整用户路径（仅回显匹配规则名），或对路径做截断/脱敏。
- **信心水平**: 很可能
- **误报排除**: 这是 [维度13-6] 的具体来源之一（独立修复点）。
- **复核状态**: 未复核

---

### [维度13-18] `DefaultPathAccessChecker.containsTraversal` 在 normalize 之后执行，且只检查标准 `..` 段

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:43-55, 89-97`
- **证据片段**:
  ```java
  public PathAccessResult checkAccess(String path, AgentExecutionContext ctx) {
      if (path == null || path.trim().isEmpty()) {
          return PathAccessResult.allow();
      }
      String normalized = normalizePath(path);
      if (normalized == null) {
          return PathAccessResult.deny("Path normalization failed (contains invalid traversal): " + path);
      }
      if (containsTraversal(path)) {  // <-- 在 normalize 之后，对原始 path 检查
          return PathAccessResult.denyByRule("path_traversal_defense", path);
      }
      ...
  }
  private boolean containsTraversal(String path) {
      String[] parts = path.replace("\\", "/").split("/");
      for (String part : parts) {
          if ("..".equals(part)) { return true; }
      }
      return false;
  }
  ```
- **严重程度**: P3
- **现状**: `containsTraversal` 只检查分割后的段**完全等于** `".."`。诸如 `.../`（三个点）、`./..`（混合）、Unicode `．．`（全角）等变体**不被检测**。
- **风险**: 主要是 `...`（三个点）/ Unicode 全角等边缘 `..` 变体可绕过 `containsTraversal` 检测。但这些变体在标准 OS 文件系统下不解析为 `..`，所以实际不构成目录穿越。仍是健壮性短板。
- **建议**: 在 normalize 前对 path 做更严格的字符白名单，拒绝异常字符序列。
- **信心水平**: 有趣的猜测（边缘绕过的实际可利用性低，因 OS 不解释这些变体）
- **误报排除**: `Paths.get(p).normalize()` 行为对非标准路径有惊讶结果（如 Windows `\\?\` 前缀、保留名 `CON`、`PRN`），值得防御性处理。
- **复核状态**: 未复核

---

### [维度13-19] `TenantSql.whereTenant` 使用非严格 `OR TENANT_ID IS NULL`，迁移期跨租户数据可见

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/TenantSql.java:35-37`
- **证据片段**:
  ```java
  public static String whereTenant(String tenantColumn) {
      return " AND (" + tenantColumn + " = ? OR " + tenantColumn + " IS NULL)";
  }
  ```
- **严重程度**: P3
- **现状**: 多租户 WHERE 子句采用 `(TENANT_ID = ? OR TENANT_ID IS NULL)` 而非严格 `TENANT_ID = ?`。设计文档明确这是"迁移期兼容性"决定。
- **风险**: 在多租户部署中，若任一写入路径漏写 tenantId（如 [维度13-12] 的 `resumeSession` 路径写入），或迁移期遗留数据未清理，**所有租户**都能读到这些 NULL-tenant 行。设计承认这是过渡期决定，但未指定何时切换到严格模式。
- **建议**: (1) 文档明确"迁移完成时切换为严格 WHERE 的截止日期/触发条件"；(2) 提供 config flag `strictTenantIsolation=true` 让集成商 opt-in；(3) 监控 NULL-tenant 行数量。
- **信心水平**: 很可能
- **误报排除**: 设计文档明确说明这是 by-design 决定；但过渡期决定的"无明确终点"和"无监控"是真实可审计问题。
- **复核状态**: 未复核

---

### [维度13-20] `DefaultPathAccessChecker` 敏感前缀 deny-list 未覆盖 macOS/Windows 特定位置，且未提供 `ISensitivePathProvider` 替代实现

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:23-40`
- **证据片段**:
  ```java
  static {
      List<String> prefixes = new ArrayList<>();
      if (!HOME.isEmpty()) {
          prefixes.add(HOME + "/.ssh/");
          prefixes.add(HOME + "/.aws/");
          prefixes.add(HOME + "/.azure/");
          prefixes.add(HOME + "/.config/gcloud/");
          prefixes.add(HOME + "/.kube/");
      }
      prefixes.add("/etc/");
      prefixes.add("/boot/");
      prefixes.add("/sys/");
      prefixes.add("/proc/");
      prefixes.add("/root/");
      SENSITIVE_PREFIX_PATTERNS = prefixes.toArray(new String[0]);
  }
  ```
- **严重程度**: P3
- **现状**: 敏感前缀 deny-list 是硬编码 Java 常量，仅覆盖 Linux/Unix 主要敏感路径。**未覆盖**：Windows（`C:\Windows\System32\`、`%APPDATA%\Microsoft\Credentials\`）、macOS（`/Library/Keychains/`、`~/Library/Cookies/`）、Linux 其他（`/var/log/auth.log`、`~/.gnupg/`、`~/.docker/config.json`、`~/.npmrc`、`~/.pypirc`、`~/.m2/settings.xml`）。设计 §7.2 承诺 `ISensitivePathProvider` 接口作为 Layer 4 外部化机制，但模块扫描确认**无任何 `ISensitivePathProvider` 实现类**。
- **风险**: 跨平台部署下，平台特定敏感位置不被保护。LLM 可读取 `~/.gnupg/secring.gpg`、`~/Library/Cookies/Cookies.binarycookie`、`%APPDATA%\Microsoft\Credentials\*` 等凭证文件。
- **建议**: (1) 扩展硬编码列表覆盖三大平台；(2) 实现 `DefaultSensitivePathProvider` 让集成商可通过 XDSL 配置覆盖；(3) 至少把 `~/.gnupg/`、`~/.docker/config.json` 加入现有列表。
- **信心水平**: 很可能
- **误报排除**: LLM 攻击场景常针对 GPG keys、Docker tokens 等凭证文件，这些在 OpenSquilla 等参考实现中均列入 deny-list。
- **复核状态**: 未复核

---

## 检查项零发现说明

1. **命令注入（ProcessBuilder 使用）**：全部 `ProcessBuilder` 调用均使用 `List<String>` 形式，**不**调用 `Runtime.exec(String)`。容器名是 UUID 生成。无 shell 解释风险。
2. **Runtime.exec / Runtime.getRuntime().exec**：模块内仅 2 处 `Runtime.getRuntime()`，都是 `availableProcessors()`（CPU 数检测，安全）。
3. **SQL 注入**：所有 DB store 使用 `PreparedStatement` + `?` 占位符。列名全部来自 `static final String` 常量。唯一列名变量拼接在 `DbTeamTaskStore.selectByColumn(String column):397`，但调用方传入的 `column` 均为常量，无用户输入路径。
4. **AllowAll*/NoOp*/AutoApproveGate 默认启用风险**：经 `DefaultAgentEngine.java:181-192, 469-471` 与 `warnIfInsecureDefaults:531-604` 双重确认，**所有 8 个安全组件的默认装配均为 `Default*` 或 `Slf4jAuditLogger`**。设计 §4.6/§4.7/§4.8/§4.9 的 "secure by default" 承诺**已在代码层兑现**。
5. **NoOpSandboxBackend 默认装配**：是设计 §7.1 明确的 Layer 1 设计性基线。不视为安全违约——是"沙箱层隔离能力未交付"而非"默认降级"。
6. **子 Agent 权限继承（工具与路径）**：`ParentConstrainedToolAccessChecker` / `ParentConstrainedPathAccessChecker` 正确实施。`ParentPermissionConstraint` 使用 effective（clamped）集合而非 declared 集合（设计 §4.4 关键安全属性）。嵌套委派（A→B→C）安全。
7. **沙箱失败语义**：`DockerSandboxBackend.execute` 失败时抛 `SandboxException`，**绝不**回退到 host 执行。设计 §7.1 "安全不可降级保证"已实施。
8. **审批决策持久化与防重放**：当前 `DefaultApprovalGate` / `AutoApproveGate` 是无状态同步决策，不涉及持久化（持久化是功能性 gate 的 successor 工作）。
9. **ThreadLocal 清理**：`ThreadLocalTenantResolver.set/clear` 在 `DefaultAgentEngine` 五个入口均有 try/finally 配对。未发现泄漏路径。
10. **审计日志关键安全事件覆盖**：dispatch loop 的全部 6 个 deny 点均调用 `auditLogger.log(new AuditEvent(...))`。审计覆盖完整（除了 [维度13-5] 的字段遗漏问题）。

## 维度复核结论

待独立复核子 agent 输出。

## 最终保留项

待复核完成后填写。
