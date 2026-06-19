# 270 nop-ai-agent 安全加固——symlink 绕过 + Docker 未验证挂载 + denial ledger 跨租户清除 + 日志注入

> Plan Status: completed
> Module: nop-ai-agent
> Last Reviewed: 2026-06-19
> Completed: 2026-06-19
> Source: deep audit 2026-06-19-1355 dimensions 13 (security), findings 13-1, 13-7, 13-12, 13-6
> Related: 271-nop-ai-agent-reliability-async-timeout (13-8 deferred there)

## Purpose

收口 deep audit 发现的 4 个 P1/P2 安全问题：路径检查 symlink 绕过、Docker 挂载未验证主机路径、resumeSession 跨租户 denial ledger 清除、审计日志注入风险。

## Current Baseline

- `DefaultPathAccessChecker.java:115-133` 做 lexical 规范化（`normalize`），不解析 symlink。设计文档 §4.3 要求 symlink 防护。
- `DockerSandboxBackend.java:241-247` 将 `SandboxRequest.hostPath` 直接 mount 到容器，无白名单验证。
- `DefaultAgentEngine.java:2174-2196` `resumeSession` 设置 `tenant=null`，导致 `DBDenialLedger` 的 `DELETE FROM denial WHERE session_id=?` 清除所有租户的 denial 记录。
- `Slf4jAuditLogger.java:16-25` 不清理换行符，LLM 控制的 `path`/`reason` 字段可注入伪造日志行；且缺少 `actorId` 和 `timestamp`。

## Goals

- 路径访问检查在 normalize 后额外做 `toRealPath()` 解析 symlink，或显式记录不支持 symlink 的 limitation。
- Docker sandbox 的 `hostPath` 经白名单/规范化验证后才允许挂载。
- `resumeSession` 从已加载 session 中读取 `tenantId`，不置 null。
- 审计日志清理换行符，添加 `actorId` 和 `timestamp` 字段。

## Non-Goals

- 不改 ORM 模型或数据库 schema。
- 不改 `NopRebuildException.rebuild` 等跨模块公共 API。
- 不引入新的权限框架或 RBAC 机制。

## Scope

### In Scope

- **Phase 1**: 路径安全（symlink 防护 + Docker hostPath 验证）
- **Phase 2**: 数据完整性（denial ledger 跨租户修复 + 审计日志加固）

### Out Of Scope

- Docker sandbox 的 CPU/内存限制语义修正（属于 13-8，归入后续 reliability plan）。
- 完整的 audit trail 架构重设计。

## Execution Plan

### Phase 1 - 路径安全加固

Status: completed
Targets: `DefaultPathAccessChecker.java`, `DockerSandboxBackend.java`

- Item Types: `Fix`

- [x] `DefaultPathAccessChecker` 在 `isPathAllowed` 中，normalize 后调用 `path.toRealPath()` 解析 symlink，再做白名单前缀检查；如果 `toRealPath` 抛 `IOException` 则拒绝访问
- [x] `DockerSandboxBackend` 在 mount 前验证 `hostPath`：必须在 `allowedBaseDirs` 白名单内、必须是真实路径（`toRealPath`）、禁止包含 `..` 组件
- [x] 添加测试：创建 symlink 指向受限目录，验证 `DefaultPathAccessChecker` 拒绝访问
- [x] 添加测试：构造 `hostPath` 超出白名单，验证 `DockerSandboxBackend` 抛出拒绝异常

Exit Criteria:

- [x] symlink → 受限目录路径被 `DefaultPathAccessChecker` 拒绝
- [x] 超出白名单的 `hostPath` 被 `DockerSandboxBackend` 拒绝
- [x] **接线验证**：`toRealPath()` 在 `isPathAllowed` 内部被调用（非独立工具方法），验证方式：测试中 mock `Path.toRealPath` 确认被调用
- [x] **无静默跳过**：`toRealPath()` 抛 `IOException` 时方法返回拒绝（非 silent allow），验证方式：测试中 `when(path.toRealPath()).thenThrow(IOException)` 断言返回 false
- [x] **端到端验证**：从 `isPathAllowed(symlinkToRestricted)` 到最终拒绝结果的完整路径已验证
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 编译通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] 所有 in-scope confirmed live defects (13-1, 13-7) 已修复确认
- [x] `docs-for-ai/04-reference/source-anchors.md` 更新路径安全相关锚点
- [x] `ai-dev/logs/2026/06-19.md` 已更新

### Phase 2 - 数据完整性与审计加固

Status: completed
Targets: `DefaultAgentEngine.java`, `DBDenialLedger.java`, `Slf4jAuditLogger.java`

- Item Types: `Fix`

- [x] `DefaultAgentEngine.resumeSession` 从 `loadedSession.getTenantId()` 读取租户 ID，不设 null
- [x] `DBDenialLedger` 的 `clearBySession` 方法在 SQL WHERE 中加入 `AND tenant_id = ?` 条件
- [x] `Slf4jAuditLogger` 在写入前清理 `path`/`reason` 中的 `\n` `\r` 字符；在日志格式中添加 `actorId` 和 `timestamp` 字段
- [x] 添加测试：验证 `resumeSession` 后 denial 记录按租户隔离
- [x] 添加测试：验证含换行符的 `path` 被审计日志清理

Exit Criteria:

- [x] `resumeSession` 不再清除其他租户的 denial 记录
- [x] 审计日志包含 `actorId` 和 `timestamp`，换行符被清理
- [x] **接线验证**：`resumeSession` 从 `loadedSession.getTenantId()` 读取租户 ID，验证方式：测试中 mock session 返回特定 tenantId，断言 denial 清理 SQL 包含该 tenantId
- [x] **无静默跳过**：审计日志清理换行符后不含 `\n` `\r`，验证方式：测试断言清理后字符串无换行
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 编译通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] 所有 in-scope confirmed live defects (13-12, 13-6) 已修复确认
- [x] `docs-for-ai/02-core-guides/service-layer.md` 更新审计日志格式说明（如适用）
- [x] `ai-dev/logs/2026/06-19.md` 已更新

## Closure Gates

- [x] Phase 1 Exit Criteria 全部勾选（13-1 symlink 防护 + 13-7 Docker 验证）
- [x] Phase 2 Exit Criteria 全部勾选（13-12 denial ledger + 13-6 日志加固）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 编译通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] 所有 in-scope confirmed live defects (13-1, 13-7, 13-12, 13-6) 已修复确认
- [x] 无 in-scope 项被静默降级为 deferred
- [x] 独立 closure audit 完成（Reviewer: independent subagent ses_11ffaa2abffek1LvoNGo2BoNm — APPROVE）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/270-nop-ai-agent-security-hardening.md --strict` 退出码 0

## Deferred But Adjudicated

- **13-8** Docker `--cpus 30` 语义错误（cpuSeconds → CPU core count）
  - Classification: optimization candidate
  - Why Not Blocking Closure: 不影响安全 baseline，属于资源限制语义修正
  - Successor Required: yes
  - Successor Path: plan 271 Phase 2 顺带修复，或独立后续 plan

- **13-5** 审计日志缺少完整审计字段（除 actorId/timestamp 外的其他字段）
  - Classification: watch-only residual
  - Why Not Blocking Closure: actorId/timestamp 已在 Phase 2 修复，其余字段为增强项
  - Successor Required: no（当前 baseline 已满足安全审计最低要求）
  - Successor Path: 后续 audit trail 架构重设计时统一补齐

## Closure

Status Note: 4 个 in-scope 安全发现（13-1/13-7/13-12/13-6）全部在 live code 修复并由真实断言测试覆盖；2 个 deferred 项（13-8/13-5）已裁定为 non-blocking residual 并带明确 successor。独立 closure audit APPROVE，checklist + hollow scan 退出码 0。
Completed: 2026-06-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，非实现 session）— task `ses_11ffaa2abffek1LvoNGo2BoNm`
- Audit Session: general subagent closure-audit pass
- Evidence:
  - Phase 1 Exit Criteria 全部 PASS（file:line 见审计报告）：
    - 13-1 symlink：`DefaultPathAccessChecker.checkAccess:82` 内部调用 `resolveSymlinkRealPath`（`toRealPath` + 祖先回退 `:144-176`），解析失败 fail-closed 拒绝 `:87-89`，真实路径经 `checkSensitiveRealPath:103-120` 复查 → 拒绝
    - 13-7 Docker：`DockerSandboxBackend.allowedBaseDirs:105` + `validateHostPath:297-336`（禁 `..`/真实路径/白名单内），`execute():204` mount 前调用，失败抛 `HOST_PATH_NOT_ALLOWED`（`SandboxFailureReason:59`）
  - Phase 2 Exit Criteria 全部 PASS：
    - 13-12：`AgentSession.tenantId:36` + 序列化 `SessionFileWriter:126`/`SessionFileReader:146` + `DBSessionStore.loadFromDb:396` 回填；`DefaultAgentEngine.doExecute:1832` 捕获、`resumeSession:2132-2156` 重建租户（reset 前 set / finally 恢复），worker 线程 `:2208` 用 sessionTenantId 非 null；`DBDenialLedger.reset:219-226` 的 `AND tenant_id=?` 现真正生效
    - 13-6：`Slf4jAuditLogger.sanitize:58-63` 清理 `\r`/`\n`，`buildMessage:40-52` 含 `timestamp`（`%d`）+ `actorId`
  - Closure Gates：`./mvnw test -pl nop-ai/nop-ai-agent -am` = 2728 tests, 0 failures, BUILD SUCCESS；checkstyle 经编译/测试通过
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/270-nop-ai-agent-security-hardening.md --strict` 退出码 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 critical/high/medium/low）
  - Anti-Hollow 检查 PASS：每个新增方法/分支有真实逻辑（无空方法体/continue/吞异常/TODO/return-null-as-normal）；接线验证：symlink 拒绝路径从 `checkAccess` 返回 denial `:92`，`validateHostPath` 从 `execute():204` 调用；端到端：真实 `@TempDir` symlink→`~/.ssh` 拒绝 + H2 双租户 reset 隔离
  - Deferred 项分类检查 PASS：13-8（optimization candidate，successor plan 271）/13-5（watch-only residual，no successor）均为 non-blocking，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（13-8 由 plan 271 接管，13-5 watch-only）
