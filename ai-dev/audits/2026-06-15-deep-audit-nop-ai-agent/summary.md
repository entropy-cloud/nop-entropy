# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-ai-agent（nop-ai/nop-ai-agent）
- **审核日期**: 2026-06-15
- **执行维度**: 01 依赖图、02 模块职责、09 错误处理、13 安全与权限、14 异步与事务、15 类型安全、16 测试覆盖、21 单元测试有效性
- **目标范围**: nop-ai-agent 单 Maven 模块（240 主 Java 文件 + 163 测试文件，~26k 行），AI Agent 框架库（ReAct 循环、技能/检查点/安全权限/记忆/消息）
- **不适用维度**: 04/05/06/07/10/11/12（模块为框架库，无 BizModel/xmeta/xbiz/GraphQL/orm 模型/codegen 管线/delta）
- **方法论**: ai-dev/skills/deep-audit-prompts.md（迭代深挖 + 独立复核）

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 追加发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|-----------|------|------|------|
| 01 依赖图 | 1 | 2 | 0 | 2 | 0 | 0 |
| 02 模块职责 | 1 | 4 | 0 | 4 | 0 | 0 |
| 09 错误处理 | 1 | 8 | 0 | 8 | 0 | 0 |
| 13 安全与权限 | 2 | 14 | 10 | 24 | 2(降级) | 0 |
| 14 异步与事务 | 2 | 9 | 4 | 13 | 1(降级) | 0 |
| 15 类型安全 | 1 | 6 | 0 | 6 | 0 | 0 |
| 16 测试覆盖 | 1 | 8 | 0 | 8 | 0 | 0 |
| 21 单元测试 | 1 | 12 | 0 | 12 | 0 | 0 |
| **合计** | — | **63** | **14** | **77** | **3** | **0** |

注：独立复核对 8 条关键发现逐条判定，3 条降级（13-17 P1→P2、13-18 P1→P2、14-03 P1→P2），5 条维持，0 条驳回。

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| **P0** | 1 | 安全：sessionId 路径穿越→任意文件写/删 |
| **P1** | 12 | 安全默认放行、审计缺失、上帝类、并发竞态、非原子写、commonPool 死锁、异常类继承违规、低价值测试 |
| **P2** | 41 | 安全绕过面（path/tool/audit）、JDBC/事务缺陷、异常处理、类型安全、测试盲区 |
| **P3** | 23 | 代码风格、命名、警示标注、测试重复、低价值枚举测试 |

## 关键发现摘要

### P0 发现

- **[13-15]** `FileBackedSessionStore.java:289-291` + `DefaultAgentEngine.java:1173-1178` — **sessionId 路径穿越，任意文件写/删**。resolveSessionId 仅判空原样返回；sessionFilePath 用 rootDirectory.resolve(sessionId) 无边界校验；remove 调 Files.deleteIfExists。攻击者传 `../../etc/cron.d/x` 可在 rootDirectory 外写/删文件，结合 cron/SSH 可 RCE/提权。**已主 agent 独立复核确认 + 子 agent 独立复核维持 P0。** 这是教科书式路径穿越，库层无防御、无文档警示。

### P1 发现

- **[13-01]** `DefaultAgentEngine.java:94-122` — 默认装配一组 AllowAll/PassThrough 检查器，开箱全放行（工具/路径/权限/审批/审计全放行）。
- **[13-02]** `DefaultAgentEngine.java:1137-1162` — 从不装配 auditLogger，默认 NoOpAuditLogger 丢弃所有审计事件（既无 setter 又无默认非空装配）。
- **[02-01]** `ReActAgentExecutor.java` — 2005 行上帝类，承担 10+ 职责，execute() 单方法 600 行。
- **[09-01]** `NopAiAgentException.java:3` — extends RuntimeException 而非 NopException，违反平台反模式，丢失 .param()/ErrorCode/i18n/事务回滚控制等能力。
- **[14-01]** `DefaultAgentEngine.java:607-674` — 同 session 并发执行，runningExecutions.put 覆盖 CancelHandle，AgentSession.messages 非线程安全 ArrayList。
- **[14-02]** `DBDenialLedger.java:122-158` — INSERT+COUNT 跨两条独立 Connection，TOCTOU 竞态使拒绝阈值判定不可靠。
- **[14-04]** `SessionFileWriter.java:73-87` — 非原子写（TRUNCATE_EXISTING），crash 留截断 JSON，listAllSessions silent skip 致专为 crash-recovery 设计的子系统在 crash 时丢 session。
- **[14-10]** `DefaultAgentEngine.java:642` — 三处 supplyAsync 无 Executor（默认 commonPool），ReAct .join() 阻塞 worker，嵌套 call-agent 可致 JVM 范围 commonPool 死锁。
- **[21-01]** `TestAgentLifecyclePoint.java` — 纯枚举计数+assertNotNull，测试零保护力（命中 P-1+P-5）。
- **[21-02]** TestSecurityLevel/TestChannelKind/TestGuardrailMode/TestGuardrailDirection — 4 文件裸枚举值列举，测试零保护力（命中 P-1）。

## 总评

nop-ai-agent 是一个架构设计相当完整的 AI Agent 框架：分层安全模型（Layer 1 路径/工具/权限 + Layer 2 矩阵 + Layer 3 审批/拒绝账本）、Principal/Channel/SecurityLevel 抽象、ParentConstraint 子 agent 隔离、DB-backed 持久化、检查点恢复、技能策展、ReAct 循环。接口抽象层次清晰，测试广度扎实（6 条 deny 路径各有专属 dispatch-path 测试，三层 A→B→C 权限端到端已覆盖），正向测试样例（TestActionFingerprint/TestSubAgentPermissionWiring）质量很高。

但本次审计暴露了**两类系统性问题**：

1. **安全默认 fail-open + 输入信任缺失**：默认配置层面存在系统性 fail-open 倾向——9 类安全组件全部默认放行，且无运行时信号提醒运维。这与"会执行任意工具的 AI Agent 框架"的安全要求严重不匹配。更严重的是，框架大量信任 LLM/调用方提供的标识符（sessionId/agentId/toolName/timeoutMs），缺乏 input validation 白名单，导致 P0 路径穿越和多个 P1 绕过。AI Agent 的核心威胁模型是 prompt injection 和 LLM 可控输出，本模块在这方面的防护有明显缺口。

2. **并发与持久化的可靠性缺陷**：多个 crash-recovery 关键路径（FileBacked 非原子写、DB* autoCommit 未设置、commonPool 死锁、同 session 并发执行、INSERT+COUNT 竞态）在并发或崩溃场景下会静默丢数据或死锁。这些恰恰是 checkpoint/session/denial-ledger 子系统——它们的存在意义就是 crash-survival 和高可靠，缺陷直接破坏核心契约。

**优先级建议**：先修 P0（sessionId 校验，一行正则即可）→ 再修 P1 安全默认（13-01/13-02，提供 production-safe 预设）+ P1 并发（14-01/14-04/14-10，成本均低）→ 然后批量处理 P2 安全绕过面（path/tool/audit 系列）和 JDBC 事务（autoCommit/原子性）→ 最后清理测试债务。

## 优先修复建议

### 立即修复（P0）
1. **[13-15]** resolveSessionId/sessionFilePath 入口校验 sessionId 仅含 `[A-Za-z0-9_-]`，拒绝 `/`、`\`、`..`；或 resolve+normalize 后校验 startsWith(rootDirectory)。

### 高优先级（P1）
2. **[13-01]** DefaultAgentEngine 提供 production-safe 预设构造器；启动期 log.warn 报告装配的 AllowAll 默认值；AllowAll* 类标注 NOT FOR PRODUCTION。
3. **[13-02]** DefaultAgentEngine 加 setAuditLogger() 并默认装配 Slf4jAuditLogger。
4. **[14-01]** doExecute 入口 putIfAbsent 抢占，已存在则拒绝；finally 条件 remove。
5. **[14-04]** SessionFileWriter/CheckpointSnapshotWriter 改 write-to-tmp + ATOMIC_MOVE + REPLACE_EXISTING。
6. **[14-10]** DefaultAgentEngine 注入可配置 Executor（固定线程池/虚拟线程），所有 supplyAsync 显式传入。
7. **[09-01]** NopAiAgentException 改 extends NopException（参照 nop-stream StreamRuntimeException）。
8. **[14-02]** DBDenialLedger INSERT+COUNT 放进单 Connection 显式事务。
9. **[02-01]** ReActAgentExecutor 抽出 DispatchPathSecurityChecker/EffectivePermissionComputer/CheckpointRecorder（重构，可排期）。
10. **[21-01/21-02]** 删除纯枚举测试文件（零保护力损失）。

### 中优先级（P2，批量处理）
- 安全绕过面：13-08（symlink）、13-09（tool 名变体）、13-10（非 String 路径）、13-23（KEYS 静态集）——建议统一加 input validation + 工具 schema 声明 path 参数。
- JDBC：14-03（autoCommit）、14-05（CLAIMED 回收）、14-06（负缓存 TTL）。
- 类型安全：15-01/15-02（AgentMessageEnvelopeJson 守卫式访问）。
- 测试盲区：16-01/16-02/16-04（补 hard exception/DB 失败路径测试）。

## 本次审核盲区自评

1. **运行时验证不足**：本次为静态代码审查，未实际构造 PoC 运行。P0 路径穿越、commonPool 死锁、INSERT+COUNT 竞态虽经源码逐行确认机制成立，但建议补充运行时复现测试。
2. **未审计的维度**：04/05/06/07/10/11/12 因模块性质不适用；若未来模块演进引入 BizModel/GraphQL，需补审。
3. **未深挖的维度**：03（API 表面积）、08（IoC beans）、17（代码风格）、18（文档一致性）、19（命名）、20（跨模块契约）未在本轮执行——03/17/19 对框架库价值有限，08/18/20 可在后续轮次补充。
4. **跨模块影响未评估**：本模块被 nop-ai-app/nop-ai-service 等下游使用，默认 AllowAll 装配对下游的实际安全影响取决于下游是否显式配置 checker，未追踪下游调用链。
5. **VFS 路径穿越的实际行为**：13-16（agentName 路径注入）的 VFS 层是否实际拒绝 `..` 取决于 ResourceComponentManager 实现，未深入验证。
