# 维度 13：安全与权限模型

## 检查范围

security 包（~70+ 类）全量；对照 security-and-permissions.md（四层接口）/security-audit-readiness-analysis.md。核验路径检查器、工具检查器、权限矩阵、审批门、拒绝账本、沙箱、内容信任、租户、审计。

## 第 1 轮（初审）发现

### [维度13-01] DBMessageService 后台 poller 线程不传播 tenant — 多租户消息跨租户泄漏

- **文件**: `message/DBMessageService.java:178-197`(start)；`290-376`(pollAllTopics/pollTopic/findPending)；`342-376`
- **证据片段**:
  ```java
  // 183-194 poller 新建单线程调度器，从不设 ThreadLocalTenantResolver
  poller = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r,"db-message-poller-"+consumerId); t.setDaemon(true); return t; });
  poller.scheduleWithFixedDelay(this::pollAllTopics, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
  // 344-353 findPending 调 currentTenant()，poller 线程上恒为 null
  String tenant = currentTenant();
  ...
  if (tenant != null) { sql += TenantSql.whereTenant(COL_TENANT_ID); }  // 永远走不到
  ```
- **严重程度**: P1
- **现状**: sendAsync 在调用线程正确写 TENANT_ID 列，但消息分发由独立 poller 线程驱动，该线程不经 ThreadLocalTenantResolver.set，导致 currentTenant() 在分发路径恒为 null，findPending SQL 永不带 tenant WHERE。
- **风险**: 多实例/多租户共享 DB 部署下，一个 tenant 的 consumer 注册到 topic-X 会收到所有 tenant 在 topic-X 的消息载荷；consumer 在 poller 线程（null tenant）执行 onMessage，其后续 DB 操作全无租户隔离。TestMultiTenantDbIsolation:212-217 注释自承该缺陷已知但未修。
- **建议**: (a)poller 拉取 message row 后读取 row 的 TENANT_ID 列，投递前 set 到 poller 线程 ThreadLocalTenantResolver，投递后 clear；(b)或 topic 改为 {tenantId}/{originalTopic} 物理隔离。需更新测试覆盖 poller tenant 传播。
- **信心水平**: 高
- **误报排除**: sendAsync 写入 tenant 正确但 poller 线程是独立线程无 ThreadLocal 继承；非生产路径已 grep。
- **复核状态**: **已复核——成立（维持 P1）**。独立复核确认：start() 无 tenant set（183-187）；findPending tenant=null 跳过 WHERE（344-352）；sendAsync 写 TENANT_ID 列存在；topic 不做租户命名空间→真泄漏；测试自承已知缺陷。

### [维度13-02] PathAccessChecker wrapper 委托传原始相对路径，Default 用 JVM CWD 解析（敏感前缀防御中和）

- **文件**: `security/RuleBasedPathAccessChecker.java:111,142-148`；`security/ParentConstrainedPathAccessChecker.java:107,139`；`security/DefaultPathAccessChecker.java:45,61-65,82,144-176`
- **证据片段**:
  ```java
  // RuleBasedPathAccessChecker:111 wrapper 内部解析为绝对
  String resolved = resolveAndNormalize(path, ctx);
  ...
  return delegate.checkAccess(path, ctx);   // :143 传原始 path，丢失 resolved
  // DefaultPathAccessChecker:45 checkAccess(path,ctx) 完全未使用 ctx；:82 resolveSymlinkRealPath 用 toRealPath（JVM CWD）
  ```
- **严重程度**: P2
- **现状**: 两 wrapper 内部用 workDir 解析相对路径判定规则/根约束，但 delegate.checkAccess(path,...) 传原始相对 path；DefaultPathAccessChecker 无 workDir 概念（完全不用 ctx），其 resolveSymlinkRealPath 用 Paths.get(p).toRealPath() 相对 JVM CWD 解析。两层用不同 CWD 来源。
- **风险**: workDir≠JVM CWD 时：wrapper 放行但 Default 检查错误位置→敏感前缀防御被中和（绕过）；或正常 workDir 下误拒。常见表现是误拒，绕过窗口较窄（需 workDir 指向敏感目录，且 .. 被 containsTraversal 拦截）。TestParentConstrainedPathAccessChecker 用 mock delegate 掩盖此不一致。
- **建议**: delegate.checkAccess(path,ctx) 改 delegate.checkAccess(resolved,ctx)；为 DefaultPathAccessChecker 增 workDir≠JVM CWD 的单测。
- **信心水平**: 中-高
- **误报排除**: DefaultPathAccessChecker 确会用 JVM CWD 解析相对路径（toRealPath 抛异常 fallback toAbsolutePath）；wrapper 已有 resolved 变量未透传，明显疏忽。
- **复核状态**: **已复核——成立（维持 P2）**。修正：文件名类检查（id_rsa/.env）不受影响，可绕过的仅前缀类（/etc/、~/.ssh/）；绕过窗口比初审暗示窄，常见表现是误拒。

### [维度13-03] DbModelSwitchedMessageWriter 完全缺失多租户隔离支持

- **文件**: `session/DbModelSwitchedMessageWriter.java:61-157`
- **证据片段**:
  ```java
  static final String SQL_INSERT_MESSAGE = "INSERT INTO " + TABLE_NAME + " (" + COL_ID + ", " + COL_SESSION_ID + ... + COL_UPDATE_TIME + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";  // 无 TENANT_ID 列
  public DbModelSwitchedMessageWriter(DataSource dataSource) { ... }  // 无 (ds, tenantResolver) 重载
  ```
- **严重程度**: P3
- **现状**: 所有其他 DB 类（DBDenialLedger/DBSessionStore/DBMessageService/DbTeamManager/DbTeamTaskStore/DBCheckpointManager/DbSessionTakeoverLock/DbDaemonCoordinator/DbUsageRecorder）都遵循双构造器模式 (dataSource)/(dataSource,ITenantResolver) 并加 TENANT_ID。DbModelSwitchedMessageWriter 是唯一例外——单构造器、SQL 硬编码 11 列、无 TENANT_ID。
- **风险**: 多租户部署中所有 tenant 的 model-switched 审计行混在同一表无 tenant 标识；集成商按双构造器模式推理会意外丢失 tenant 维度。此表无读路径故无跨租户数据泄漏——影响限于审计可观测性。
- **建议**: 补 (DataSource,ITenantResolver) 构造器，按模式在 INSERT 加条件 TENANT_ID；保持单参构造器 byte-identical（零回归）。
- **信心水平**: 高
- **误报排除**: 对照 9 个 DB 类构造器签名确认 pattern 一致；此 writer 唯一缺漏；本发现是一致性 gap，不构成跨租户数据泄漏。
- **复核状态**: 未复核

### [维度13-04] ToolPathArgKeys 静态固定白名单——非标准参数键传路径完全绕过路径访问检查

- **文件**: `security/ToolPathArgKeys.java:16-22`；调用点 `engine/ReActAgentExecutor.java:2744-2747`、`security/DefaultLevelHintsProducer.java:97`
- **证据片段**:
  ```java
  public static final Set<String> KEYS = Set.of("path","file","filePath","filename","directory","dir","destination","output","input","source","target","cwd");
  // ReActAgentExecutor:2744-2747
  for (Map.Entry<String,Object> entry : arguments.entrySet()) {
      if (!ToolPathArgKeys.KEYS.contains(entry.getKey())) { continue; }  // 非标准键直接跳过
  ```
- **严重程度**: P3
- **现状**: 路径访问检查仅扫描参数键在固定 12 键白名单的字符串值。工具用 repoLocation/module/configName/entityName 等非标准键承载路径时，checkPathAccess 完全跳过，不进 pathAccessChecker、不产审计事件、不计拒绝账本。DefaultLevelHintsProducer.evaluateWritesOutside 复用同一 KEYS，writesOutsideWorkspace hint 也漏报。
- **风险**: 非标准键很常见（每工具自定义参数名）；一旦工具未遵循"路径必须用标准键"的隐性契约，路径访问完全脱离 Layer 1 防御，仅靠 Layer 2/3（与参数无关）兜底。与设计 §4.3"每次文件操作前检查路径权限"冲突。
- **建议**: (a)javadoc 声明此契约并在装配 IToolExecutor 时校验工具 schema 路径参数用标准键；(b)或 tool.xdef 新增 pathArguments 元数据；(c)至少补"非标准键但值像路径"的审计 WARN。
- **信心水平**: 高
- **误报排除**: KEYS 固定；if(!KEYS.contains)continue 是 unconditional 跳过；两调用点同 KEYS。这是设计性契约但无运行时校验/文档强制/审计可见性。
- **复核状态**: 未复核

### [维度13-05] DBSessionStore.listAllSessions 内联 OR-tenant 子句缺括号包覆（现状无害但脆弱）

- **文件**: `session/DBSessionStore.java:228-267`（特指 230-235）
- **证据片段**:
  ```java
  if (tenant != null) {
      selectAll += " WHERE " + COL_TENANT_ID + " = ?" + " OR " + COL_TENANT_ID + " IS NULL";  // 无括号
  }
  // 对比 TenantSql.whereTenant: " AND (" + col + " = ? OR " + col + " IS NULL)"
  ```
- **严重程度**: P3
- **现状**: 内联手写 WHERE TENANT_ID=? OR TENANT_ID IS NULL 未通过 TenantSql.whereTenant 复用，且缺括号。当前仅一个 WHERE 子句无 AND 串联，OR 语义当下正确。但 TenantSql.whereTenant 是项目统一抽象（9 DB 类全用），本处内联+无括号是双重不一致。
- **风险**: 未来加 `WHERE STATUS=? AND TENANT_ID=? OR TENANT_ID IS NULL`，AND 优先级高于 OR，语义变成 `(STATUS=? AND TENANT_ID=?) OR (TENANT_ID IS NULL)`——legacy null-tenant 行不论状态进入结果集，破坏状态过滤；review 易忽略。
- **建议**: 复用 TenantSql.whereTenant，WHERE 子句改 `WHERE 1=1 + whereTenant(...)`（与 loadFromDb:380/remove:178 一致）。
- **信心水平**: 高
- **误报排除**: 当前 SQL 因仅一个条件括号缺省不影响结果；防御性维护建议。
- **复核状态**: 未复核

## 已确认安全良好（未报告）

SQL 注入防御（11 DB 类全用 PreparedStatement 参数化）；secure-by-default（Default* 而非 AllowAll*/NoOp* 默认，warnIfInsecureDefaults 在构造期+setter 期触发）；AllowAll*/NoOp*/AutoApproveGate 是 public opt-in 非 default；子 agent 权限收敛（ParentConstrained* fail-closed ∩ 传播）；审批门+拒绝账本闭环（5 deny 点+post-denial guard 统一 recordDeniedAction）；.. 穿越/~/反斜杠归一化双层防御；审计日志无敏感数据（AuditEvent 不含 arguments 原文，Slf4jAuditLogger.sanitize 防日志注入）；DockerSandboxBackend 用 ProcessBuilder argv 非 shell、env 键 POSIX 校验、hostPath 白名单+toRealPath；多租户隔离除 13-01/13-03 外均正确注入 TenantSql；hardcoded deny 列表与设计 §4.6 一致。

## 维度复核结论

[维度13-01] 独立复核：**成立 P1**（真实跨租户泄漏，测试自承已知）。[维度13-02] 独立复核：**成立 P2**（修正：文件名类检查不受影响，绕过窗口窄，常见表现误拒）。[维度13-03/04/05] 复核未发现反证，保留。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 13-01 | P1 | message/DBMessageService.java | poller 不传播 tenant，多租户消息跨租户泄漏 |
| 13-02 | P2 | security/RuleBasedPathAccessChecker.java | wrapper 传原始 path，Default 用 JVM CWD 解析 |
| 13-03 | P3 | session/DbModelSwitchedMessageWriter.java | 完全缺多租户隔离支持 |
| 13-04 | P3 | security/ToolPathArgKeys.java | 固定白名单，非标准键绕过路径检查 |
| 13-05 | P3 | session/DBSessionStore.java | listAllSessions OR-tenant 缺括号包覆 |
