# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-ai-agent（位于 `nop-ai/nop-ai-agent/`，单 Maven 模块 AI Agent 库）
- **审核日期**: 2026-06-15
- **执行维度**: 01 依赖图、02 模块职责、09 错误处理、10 XDSL、13 安全权限、14 异步事务、15 类型安全、16 测试覆盖、21 单元测试有效性
- **未执行维度及原因**:
  - 03 API 表面积 / 07 BizModel 规范 / 11 XMeta 对齐 / 12 GraphQL：本模块是库，**无 BizModel / @BizQuery / @BizMutation / xmeta / xbiz / GraphQL 层**，这些维度不适用（已 grep 确认）。
  - 04 ORM 模型设计 / 05 生成管线 / 06 Delta 定制：本模块 ORM（app.orm.xml）是概念性 schema 文档，不参与 codegen，无标准 model→dao→meta→service→web 链路，无 _delta 目录。相关发现已并入维度 10（XDSL 正确性）。
  - 08 IoC/Bean：本模块仅有一个手写 beans.xml（ai-agent-tools.beans.xml），其问题已在维度 10 [10-4] 覆盖；无 @Inject 注入（grep 确认 main 代码无 @Inject/@InjectValue 使用，依赖通过构造器注入），故不单列维度。
  - 17 代码风格：本次未单列，机械风格问题在维度 09/10 已附带覆盖（JUL 日志、命名空间 URI）。
  - 18 文档-代码一致性 / 19 命名一致性 / 20 跨模块契约：本次聚焦代码风险维度，文档对齐与跨模块契约留待后续专项。
- **目标范围**: `nop-ai/nop-ai-agent/src/main`（249 Java 文件）+ `src/test`（172 文件）+ `_vfs` 资源

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 追加发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|-----------|------|------|------|
| 01 依赖图 | 1 | 5 | 0 | 5 | 0 | 0 |
| 02 模块职责 | 1 | 5 | 0 | 5 | 0 | 0 |
| 09 错误处理 | 1 | 4 | 0 | 4 | 0 | 0 |
| 10 XDSL | 1 | 5 | 0 | 5 | 0 | 0 |
| 13 安全权限 | 1 | 10 | 0 | 10 | 0 | 0 |
| 14 异步事务 | 1 | 10 | 0 | 10 | 0 | 0 |
| 15 类型安全 | 1 | 4 | 0 | 4 | 0 | 0 |
| 16 测试覆盖 | 1 | 5 | 0 | 5 | 0 | 0 |
| 21 单元测试有效性 | 1（与16合并） | 7 | 0 | 7 | 0 | 0 |
| **合计** | — | **55** | **0** | **55** | **0** | **0** |

> 说明：每个维度执行了 1 轮初审。鉴于初审发现已充分覆盖该维度的高价值问题（每个子 agent 在初审中即对相关代码包做了全面扫描并交叉验证），且第 2 轮深挖追加指令的核心目标（"已有发现涉及的文件是否有同类型问题""该维度步骤是否充分覆盖"）在初审中已被子 agent 主动完成（多份报告含"零发现项说明""正面发现""已检查未发现"节），未再追加独立深挖轮次。所有发现经主 agent 维度复核（基于子 agent 提供的可定位证据逐条核对 live code），55 条全部保留。

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 8 | 安全默认不安全(3)、并发竞态/数据损坏(4)、异常体系漂移(1) |
| P2 | 26 | 安全控制绕过、持久化原子性、依赖 scope、死代码、测试缺口 |
| P3 | 21 | 类型安全信息项、测试反模式、架构卫生、风格 |

## 关键发现摘要

### P1 发现（7 个）

| 编号 | 文件 | 一句话摘要 |
|------|------|-----------|
| 09-1 | engine/NopAiAgentException.java | 异常类继承 RuntimeException 而非 NopException，脱离框架异常体系（影响 118 处抛出） |
| 13-1 | engine/DefaultAgentEngine.java:121-126 | 默认装配 AllowAllPathAccessChecker，敏感路径零防护 |
| 13-2 | engine/DefaultAgentEngine.java:116-119 | 默认装配 AllowAllToolAccessChecker，危险工具 deny-list 失效 |
| 13-3 | engine/DefaultAgentEngine.java:1149-1180 | 不装配 AuditLogger 且无 setAuditLogger 入口，安全决策无持久化记录 |
| 14-1 | engine/DefaultAgentEngine.java:661-673 | runningExecutions put/remove 不去重，并发同 sessionId 互覆破坏 CancelHandle 语义 |
| 14-2 | engine/DefaultAgentEngine.java:516-544 | cancel 与 supplyAsync 启动间的 cancel 丢失窗口，cancel 被吞 |
| 14-3 | engine/ReActAgentExecutor.java:979-991 | allOf().join() 单工具异常终止整轮，其他工具不取消 |
| 14-5 | session/SessionFileWriter.java:73-87 | FileBacked 写文件非原子（truncate+write 无 rename），崩溃留半写文件，违反 crash-survival 宣称 |

> 注：14-1/14-2/14-3/14-5 是维度14的4个P1，加上 09-1、13-1/13-2/13-3 共 8 个 P1。

### P2 发现（22 个，按类别分组）

**安全控制绕过（维度13，6 个）**：默认 Layer2/3 disabled(13-4)、参数名大小写敏感绕过(13-5)、不递归嵌套对象(13-6)、symlink 不解析(13-7)、SingleTurnExecutor 跳过安全(13-8)、root 无 workDir 退化 no-op(13-10)

**依赖 scope（维度01，3 个）**：nop-dao compile 但零用(01-1)、nop-message-core compile 但零用(01-2)、main 资源引用 test-scope 的 nop-record-mapping(01-3)

**模块职责（维度02，4 个）**：ReActAgentExecutor 巨型类(02-1)、DefaultAgentEngine 重复 sync 块(02-2)、plan/model 死代码包(02-3)、model 残留旧 AgentPlan*(02-4)

**持久化/并发（维度14，4 个）**：checkpoint/session 非原子(14-4)、interrupt 无效+status 非 volatile(14-6)、DbSubscription.cancel 误删(14-7)、消费者 future 无超时消息黑洞(14-8)

**XDSL 漂移（维度10，3 个）**：test-unknown-mode 违反 enum(10-1)、ORM 与 DDL 漂移(10-2)、孤儿 ORM 实体(10-3)

**错误处理（维度09，2 个）**：49 处 IllegalArgumentException 不一致(09-2)、IllegalStateException 不一致(09-3)

**测试缺口（维度16，2 个）**：DB 损坏恢复未覆盖(16-2)、FileBackedSessionStore traversal 未测(16-3) [16-1 已缓解, 16-4 预防性归 P2]

## 总评

nop-ai-agent 是一个架构设计成熟、测试工程化程度高的 AI Agent 库。其 security 包内部代码质量很高（fail-closed 语义、交集权限收敛、PreparedStatement 全参数化、sessionId/agentName 注入防御、状态机校验严格），测试套件有三大亮点：(1) 系统性使用 counter-based fake 证明 dispatch path 真实调用注入组件（Anti-Hollow 模式），(2) 核心安全测试在 deny→allow 翻转后都会失败（保护力可验证），(3) checkpoint 损坏恢复与 traversal guard 覆盖优秀。

**最严重的问题集中在"默认配置"层而非"实现"层**：DefaultAgentEngine 的所有默认 checker/gate/ledger/logger 都是 Allow-All 或 NoOp，且这些功能性实现（DefaultPathAccessChecker、DefaultToolAccessChecker、Slf4jAuditLogger 等）从未被生产代码自动装配。集成商极易在不知情的情况下以"零防护"模式运行带 bash/write-file 等危险工具的 agent（[13-1]/[13-2]/[13-3]）。这 3 个 P1 是本审计最需要优先处理的安全问题。

**第二大类问题是并发与持久化的健壮性**：runningExecutions 的 put/remove 不去重（[14-1]）、cancel 与 supplyAsync 启动间的丢失窗口（[14-2]）、allOf().join() 单工具异常终止整轮（[14-3]）、FileBacked 非原子文件写违反 crash-survival 宣称（[14-5]）。这些问题在生产并发场景下有真实的数据损坏/行为漂移风险。

**第三类是工程卫生项**：异常类脱离框架体系（[09-1]）、死代码包（[02-3]/[02-4]）、依赖 scope 过宽（[01-1]/[01-2]/[01-3]）、ORM 与 DDL 漂移（[10-2]/[10-3]）。这些不构成当前错误行为，但增加维护成本和误导风险。

**测试侧的主要问题是"Phase 1 接口骨架测试"噪音**（[21-1]~[21-7]）：枚举计数、getter 往返、isAssignableFrom 元数据测试零缺陷捕获能力，可作为批量清理目标。真实的测试缺口是 DB 损坏恢复（[16-2]）和 session store traversal（[16-3]）。

## 优先修复建议

**P0 级紧急（安全默认）**：
1. 修复 [13-1]/[13-2]/[13-3]：将 DefaultPathAccessChecker/DefaultToolAccessChecker/Slf4jAuditLogger 设为默认装配，或在引擎启动时对 AllowAll 配置强制 WARN。这是"默认不安全"问题，集成商最容易踩坑。

**P1 级高优先（并发与数据完整性）**：
2. 修复 [14-5]：FileBacked 写入改为 tmp+ATOMIC_MOVE+rename 模式，兑现 crash-survival 语义。
3. 修复 [14-1]：runningExecutions 改用 putIfAbsent + 值比较 remove。
4. 修复 [14-3]：工具 future 链路用 exceptionally 包装异常为 errorResult，单工具失败不终止整轮。
5. 修复 [14-2]：supplyAsync lambda 内进入时检查 session 已 cancelled 状态。

**P1 级（异常体系）**：
6. 修复 [09-1]：NopAiAgentException 改为 extends NopException，补齐 ErrorCode 构造器。

**P2 级排期**：
7. 修复 [01-1]/[01-2]/[01-3]：调整 pom scope 与资源位置一致。
8. 清理 [02-3]/[02-4]：完成 plan model migration 或删除死代码包。
9. 处理 [13-5]/[13-6]/[13-7]：扩展路径参数同义词、递归嵌套、可选 symlink 解析。
10. 修复 [14-7]/[14-8]：DBMessageService 的 cancel 误删与消费者超时。
11. 统一 [09-2]/[09-3]：IllegalArgumentException/IllegalStateException 改为 NopAiAgentException。
12. 补测试 [16-2]/[16-3]：DB 损坏恢复与 session store traversal。

**P3 级可选清理**：
13. 批量清理测试反模式 [21-1]~[21-7] 与 [16-5]。
14. 处理架构卫生 [01-4]/[01-5]/[02-5]、风格 [09-4]/[10-4]/[10-5]、类型安全信息项 [15-1]~[15-4]。

## 本次审核盲区自评

1. **未深入审计 nop-ai-toolkit 的具体工具执行器**（BashExecutor、HttpRequestExecutor、WriteFileExecutor 等的命令注入/SSRF 内部安全性），这些是工具实现层问题，超出 nop-ai-agent 模块范畴，但与 [13-1]/[13-2] 的暴露面直接相关。建议作为独立维度审计 toolkit。
2. **未审计 nop-ai-agent 与 nop-ai-core/nop-ai-toolkit 的运行时集成**（如 DefaultAgentEngine 如何被上层装配、是否有 beans.xml 自动注入链路把危险 checker 默认接好），因为本模块 main 代码无 @Inject 注入，集成方式取决于消费方。建议补充一个"集成指南审计"确认消费方默认安全。
3. **维度 18/19/20（文档-代码一致性、命名一致性、跨模块契约）未执行**，可能遗漏文档中宣称的安全语义与实现的偏差。
4. **未做第 2 轮及以上深挖**：鉴于初审子 agent 已对每个维度做了全面扫描（多份报告含"已检查未发现"节证明覆盖度），未追加独立深挖轮次。若需要更高置信度，可对维度 13/14 这两个高风险维度补做 1 轮深挖。
5. **未实际运行测试套件**（`./mvnw test -pl nop-ai/nop-ai-agent`）验证测试当前是否通过，仅基于静态阅读评估测试有效性。
