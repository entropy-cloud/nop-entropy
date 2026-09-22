# Apache Fineract 源码级调研：原生 maker-checker 机制全貌

> Status: resolved
> Date: 2026-09-22
> Scope: maker-checker 通用机制设计参考（命令管道/待审存储/审批重放/SoD/权限模型）
> Conclusion: Fineract 用"命令即审计的单表（`m_portfolio_command_source`，6 状态机）+ 两级开关（全局 flag × 每权限 `can_maker_checker` 位）+ 独立 `_CHECKER` 派生权限 + 执行后回滚（命中复核则抛异常回滚业务事务、仅留 AWAITING_APPROVAL 行）+ approve 用原 payload 重放同一管道 + 同人审批硬禁止（`enable-same-maker-checker` 可豁免）"实现 maker-checker；缺 staleness/乐观锁是主要短板。对本平台：证明 checker 侧可完全复用 maker 侧管道，权限派生（`METHOD_ENTITY` / `METHOD_ENTITY_CHECKER`）与幂等键设计可直接借形；入口手工 opt-in 与字符串无类型约定是反面教材。

## Context

- 上位调研：`ai-dev/analysis/2026-09/2026-09-22-maker-checker-mechanism-research.md` 将 Fineract 列为与 Nop 形态最接近的参考（"审批一次 API 调用"+ 全局/按 API 粒度开关）。本文在源码级验证其完整机制。
- 源码：本地 `~/sources/fineract`（git clone --depth 1，Apache-2.0），多模块新版（`fineract-provider` + `fineract-core` + 新 `fineract-command*` 模块）。文中路径均为仓库相对路径。

## 调研目标

1. maker 的写操作如何被捕获为命令；2. 待审命令的存储与状态机；3. 开关粒度的实现；4. checker approve 是否重放原始命令及幂等保证；5. SoD 校验；6. maker/checker 权限命名与校验层次；7. 待办 API 面；8. 审计与命令历史的关系；9. 资源冲突（staleness）处理。

## 调研结果

### 1. 命令管道：显式构造，非 AOP 拦截

- 每个 JAX-RS 资源方法用 `CommandWrapperBuilder` 构造 `CommandWrapper`（actionName/entityName/resourceId + JSON body）再调 `logCommandSource`。证据：`fineract-provider/src/main/java/org/apache/fineract/useradministration/api/UsersApiResource.java` `create()`（约 L165-172）。
- 链路：`fineract-core/.../commands/domain/CommandWrapper.java`（`taskPermissionName = actionName + "_" + entityName`，L103/315；`fromExistingCommand` 工厂 L83-91 供审批重放用）→ `fineract-core/.../commands/service/PortfolioCommandSourceWritePlatformServiceImpl.java` `logCommandSource`（L56-82：校验 maker 权限；**改自己资料绕过 maker-checker**，L61-66）→ `SynchronousCommandProcessingService.executeCommand`（resilience4j 重试，L89-103）。
- 幂等检查（`SynchronousCommandProcessingService` L241-264）：按 idempotency key 查既有命令，PROCESSED/UNDER_PROCESSING/ERROR 分别抛不同 `Idempotent*Exception`。
- 执行顺序：**独立事务先存初始 `CommandSource`（UNDER_PROCESSING）**（L136-143），再在业务事务内执行 handler。

### 2. 核心设计：执行后回滚（execute-then-rollback）

- `fineract-core/.../commands/service/CommandSourceService.java` `processCommandAndSaveResult`（L116-124）先真实执行业务 → `validateMakerChecker`（L126-143）判定；命中 maker-checker 且当前用户非 checker 时：`markAsAwaitingApproval()` 后抛 `RollbackTransactionNotApprovedException`（`fineract-core/.../commands/exception/RollbackTransactionNotApprovedException.java` L22-28 javadoc 明确此设计）——**业务事务回滚，只留下 AWAITING_APPROVAL 命令行**。
- 语义：maker 提交时在事务内真实试执行（尽早暴露全部业务校验错误），通过后丢弃结果只留审批行；approve 时重放即首次真实生效。库里没有 maker 的半成品，无需 undo。
- 状态机：`fineract-core/.../commands/domain/CommandProcessingResultType.java`（L32-37）：`INVALID(0)/PROCESSED(1)/AWAITING_APPROVAL(2)/REJECTED(3)/UNDER_PROCESSING(4)/ERROR(5)`。
- 脱敏保护：command JSON 被脱敏（sanitized）则直接拒绝进 maker-checker（`CommandSourceService` L134-137）。

### 3. 待审存储：一张表双用（待办 + 审计）

- 实体 `fineract-core/.../commands/domain/CommandSource.java`（`@Table(name = "m_portfolio_command_source")`，L47）。字段（L50-145）：`action_name/entity_name/office_id/group_id/client_id/loan_id/savings_account_id/product_id/api_get_url/resource_id/subresource_id`、**`command_as_json`（payload）**、**`maker_id`（非空）/`made_on_date_utc`**、**`checker_id`（可空）/`checked_on_date_utc`**、`status`、`transaction_id`、`idempotency_key`、`result`（结果 JSON）/`result_status_code`、`client_ip`、`is_sanitized`。无 undo/redo 字段。
- 幂等唯一键：`UNIQUE_PORTFOLIO_COMMAND_SOURCE(action_name, entity_name, idempotency_key)`（`fineract-provider/src/main/resources/db/changelog/tenant/parts/0061_add_idempotency_key_to_command_source.xml` L39）。

### 4. 开关粒度：全局 flag × 每权限能力位

- 判定集中于 `fineract-provider/.../infrastructure/configuration/domain/ConfigurationDomainServiceJpa.java` `isMakerCheckerEnabledForTask`（L48-62）：
  - 全局：Global Configuration 属性 `maker-checker`（`fineract-core/.../infrastructure/configuration/api/GlobalConfigurationConstants.java` L23）；
  - 按 API：`m_permission.can_maker_checker` 布尔位（`fineract-core/.../useradministration/domain/Permission.java` L43-44，DDL 默认 true），经 `PermissionWritePlatformServiceJpaRepositoryImpl.updateMakerCheckerPermissions`（L46-77）开关，禁止对 `_CHECKER`/`READ_`/special 分组操作。
- 另有全局 `enable-same-maker-checker` 开关（同审批人豁免，L75）。

### 5. 审批执行 = 用原 payload 重放同一管道

- REST：`fineract-provider/.../commands/api/MakercheckersApiResource.java`（`@Path("/v1/makercheckers")`）：`GET /v1/makercheckers`（待办）、`GET /searchtemplate`（Checker Inbox 下拉数据，按请求者 checker 权限过滤）、`POST /{auditId}?command=approve|reject`、`DELETE /{auditId}`。
- `PortfolioCommandSourceWritePlatformServiceImpl.approveEntry`（L85-106）→ `validateMakerCheckerTransaction`（L120-139）→ **`CommandWrapper.fromExistingCommand` + `JsonCommand.fromExistingCommand` 从存储的 `command_as_json` 重建命令 → `executeCommand(..., true)` 走完全相同管道**。
- 幂等/防重：提交时业务事务已回滚（重放即首次生效）；重放复用既有 CommandSource 行（`executeCommandAttempt` L127-129）；状态机拦截（非 AWAITING_APPROVAL 抛 `CommandNotAwaitingApprovalException`）；idempotency 唯一键 + 三态异常。
- 重放时对**当前**库内实体重新跑全部业务校验，失败则命令落 ERROR 状态并记录（`persistFinalErrorResult` L205-223）。

### 6. SoD：有代码级硬校验

- `validateMakerCheckerTransaction` L129-137：除非全局配置 `enable-same-maker-checker` 开启或审批人是 `CHECKER_SUPER_USER`，`Objects.equals(appUser.getId(), maker.getId())` 命中即抛 `UnsupportedCommandException("Can not be checked by the same user.")`。

### 7. 权限命名：`ACTION_ENTITY` + 独立 `ACTION_ENTITY_CHECKER` 行

- maker 权限 = `actionName + "_" + entityName`（如 `CREATE_CLIENT`）（`CommandWrapper.taskPermissionName()` L314-316）；checker 权限是 **m_permission 中另一行** `CREATE_CLIENT_CHECKER`：`AppUser.validateHasCheckerPermissionTo`（`fineract-core/.../useradministration/domain/AppUser.java` L693-699）检查 `function.toUpperCase() + "_CHECKER"` 或 `CHECKER_SUPER_USER`。
- 校验在 JVM 应用层（遍历 roles → `m_role_permission` → `m_permission.code`，`AppUser.hasPermissionTo` L624-634；`ALL_FUNCTIONS` 短路）。
- `_CHECKER` 行由种子数据生成（`0002_initial_data.xml` L3524 起），动态 datatable 建表时自动生成 `CREATE/UPDATE/DELETE_<table>_CHECKER` 三行（`DatatableWriteServiceImpl.java` L190-191、L641-646）。
- 待办可见范围用一条 SQL join 圈定：checker 权限 join `m_permission × m_role_permission × m_role × m_appuser_role`（`AuditReadPlatformServiceImpl` L228-246），加 office hierarchy 隔离（L120-127）；强制 `status = 2`（`retrieveAllEntriesToBeChecked` L215-218）。

### 8. 审计与清理

- 同一张表双用：`retrieveAuditEntries("audit")`（查全部状态）与 `retrieveAllEntriesToBeChecked`（status=2）共用 schema（`AuditReadPlatformServiceImpl` 约 L100-260）；另有 `/v1/audits`、`/v2/audits` REST 面。
- 清理：`fineract-core/.../commands/jobs/PurgeProcessedCommandsTasklet.java`（L38-50）按配置天数删 PROCESSED 记录（REJECTED/AWAITING/ERROR 保留）。
- 外推：命令处理后发 webhook HookEvent（批量事务挂 afterCommit，`SynchronousCommandProcessingService.publishHookEvent` L388-454）。
- 新 v2 有独立审计流 `fineract-command-audit`（`AuditCommandHookBefore/After/Error` + `CommandStore`），但只服务 v2 命令框架。

### 9. 资源绑定与冲突处理：无 staleness 检查

- 绑定字段齐全（entityName/resourceId/subResourceId/各类业务 id/external id，索引齐全），但 **`CommandSource` 无 `@Version`、approve 重放时不比对目标资源当前状态与 maker 提交时刻**；兜底只有"重放时重跑业务校验，失败落 ERROR" + 状态机 + 幂等键。
- 附带保护：批处理作业运行时禁止一切更新（`validateIsUpdateAllowed` L141-143）。

### 10. 双轨教训

新 v2 命令框架（`fineract-command` 模块的 typed `Command/CommandDispatcher` + async/disruptor）**完全绕过 maker-checker**：如 `fineract-core/.../portfolio/paymenttype/api/PaymentTypeApiResource.java` `createPaymentType`（L83-92）直接 `dispatcher.dispatch(command)`。旧机制入口靠每个资源手工 opt-in（`CommandWrapperBuilder` 膨胀、数百处调用点 + `findCommandHandler` 硬编码分支）。

## 与当前项目的关系

- **可借鉴**：
  1. "执行后回滚 + 审批重放"：同一管道同一 handler 只写一份；Nop 的 tryAction（dry-run）与之等价于两种"提前校验"策略，但 Fineract 证明 checker 侧**完全复用** maker 侧管道是可行且省一套执行语义的；
  2. 命令即审计的单表设计 + 6 状态机 + `(action, entity, idempotencyKey)` 唯一键 + 三态幂等异常（天然支持客户端重试语义）——与 `nop_sys_checker_record` 契约互补（可补 idempotency key）；
  3. 两级开关 + 独立 `_CHECKER` 派生权限：Nop 可由 biz 元数据自动生成 `METHOD_ENTITY` 与 `METHOD_ENTITY_CHECKER` 两个权限 code，checker 待办可见范围用一条 join 圈定；
  4. 同人审批硬禁止 + 可配置豁免（`enable-same-maker-checker` + `CHECKER_SUPER_USER`）——比一刀切更完整，紧急场景有出口；
  5. 脱敏 payload 禁止进 maker-checker 的显式拒绝策略。
- **不可照搬**：
  1. 无 staleness/乐观锁——approve 重放可能用过期 payload 覆盖他人后续修改，只靠业务校验兜底；Nop 应在审批记录上绑定目标资源版本号，approve 时比对显式报"资源已变更"；
  2. 入口手工 opt-in + 无类型字符串约定——Nop 的 biz 方法拦截 + 元数据正好在框架层统一收口，避免 `CommandWrapperBuilder` 式膨胀；
  3. 新旧双轨并行（v2 绕过 maker-checker）——Nop 拦截点必须放在唯一入口（GraphQL/REST 都过 biz 层），防止新增通道漏覆盖；
  4. payload 明文落库——敏感字段需提前定义加密/掩码策略。

## Open Questions

- [ ] Nop 的缺省校验策略选 tryAction（dry-run，不落库）还是 Fineract 式 execute-then-rollback（试执行后回滚）？后者能复用真实执行路径、暴露更多校验错误，但要求 ORM 回滚语义干净。
- [ ] `nop_sys_checker_record` 是否补 idempotency key 列与唯一索引？

## References

- 本地源码：`~/sources/fineract`（clone 自 github.com/apache/fineract，--depth 1）
- 上位分析：`ai-dev/analysis/2026-09/2026-09-22-maker-checker-mechanism-research.md`
- 关键源码文件：`fineract-core/src/main/java/org/apache/fineract/commands/domain/CommandSource.java`、`fineract-core/src/main/java/org/apache/fineract/commands/service/CommandSourceService.java`、`fineract-core/src/main/java/org/apache/fineract/commands/service/SynchronousCommandProcessingService.java`、`fineract-core/src/main/java/org/apache/fineract/commands/exception/RollbackTransactionNotApprovedException.java`、`fineract-provider/src/main/java/org/apache/fineract/commands/api/MakercheckersApiResource.java`
