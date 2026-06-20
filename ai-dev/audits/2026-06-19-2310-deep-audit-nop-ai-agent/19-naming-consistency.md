# 维度 19：命名与术语一致性

## 检查范围

glossary.md 全术语对照；280 Java 文件 + 6 *Table.java + app.orm.xml + beans.xml + 2 register-model + agent.xdef/agent-plan.xdef；跨模块对比 nop-auth-dao/nop-ai-dao。

## 第 1 轮（初审）发现

### [维度19-01] AgentExecStatus 枚举值小写与模块其他 Status 枚举（UPPER_SNAKE_CASE）不一致，且 .name() 持久化形成跨表大小写漂移

- **文件**: `model/AgentExecStatus.java:3-27`；`team/TeamStatus.java:25-45`；`team/TeamTaskStatus.java:44-77`；`runtime/recovery/DefaultOrphanRecoveryHandler.java:182-198`；`team/DbTeamManager.java:69-75`
- **证据片段**:
  ```java
  public enum AgentExecStatus { pending, running, completed, failed, cancelled, forced_stopped, escalated, paused }  // 全小写
  public enum TeamStatus { CREATED, ACTIVE, DISBANDED }   // UPPER_SNAKE_CASE
  // DefaultOrphanRecoveryHandler:184-193 SQL 字面量小写
  "UPDATE " + ... + " SET STATUS=? WHERE SESSION_ID=? AND STATUS IN ('running','pending')"; ... ps.setString(1, AgentExecStatus.failed.name());  // "failed" 小写
  // DbTeamManager: STATUS='ACTIVE' WHERE STATUS='CREATED'  // 大写
  ```
- **严重程度**: P1 → **复核降级 P3**
- **现状**: 同模块同 STATUS 列同"枚举.name() 写入 VARCHAR"模式，两种 case：ai_agent_session.STATUS 小写、ai_agent_team[_task].STATUS 大写。其他 14 个相关枚举（AgentActorStatus/CircuitState/AgentMessageKind/...）全 UPPER_SNAKE_CASE，仅 AgentExecStatus 小写。
- **复核降级依据**: 大小写不一致属实，且 .name() 持久化确实产生跨表大小写漂移（ai_agent_session 小写、ai_agent_team 大写）。**但**全量搜索无任何跨表 JOIN on STATUS，每表 read 用各自枚举 valueOf(name)，表内自洽不会因大小写出 bug；没有任何功能性后果。这是纯命名风格漂移，无功能 bug/无数据损坏/当前查询不受影响——"跨表查询易踩坑"是未来风险非当下问题。
- **建议**: 后续重构时统一为 UPPER_SNAKE_CASE，配 DB 数据迁移 UPDATE ai_agent_session SET STATUS=UPPER(STATUS)；同步更新 javadoc/SQL 示例。
- **信心水平**: 高
- **误报排除**: 枚举 14 个清单确认仅 AgentExecStatus 偏离；.name() 写入与 SQL IN 字面量一致小写，是真实持久化契约非文档错误；构建无法覆盖此一致性。
- **复核状态**: **已复核——降级 P1→P3**（纯命名风格，无跨表 STATUS JOIN，表内自洽无功能影响）。

### [维度19-02] Agent 配置名一词三义：agentName / agentModel / agentId

- **文件**: `agent.xdef:52-58`(agentModel)；`team/TeamMemberSpec.java:14-67`(agentModel)；`team/AiAgentTeamMemberTable.java:23-29,50`(AGENT_MODEL)；`team/TeamSpec.java:48-93`(leadAgentName)；`tool/CallAgentExecutor.java:128-156`(agentId)；`engine/AgentNames.java:6-12`；`message/CallAgentRequestPayload.java:36-59`(targetAgentId)
- **证据片段**:
  ```xml
  <!-- agent.xdef:52-58 同段 DSL 同时用 leadAgentName 和 agentModel -->
  <team ... leadAgentName="!string" ...>
      <member ... agentModel="!string" .../>
  ```
  ```java
  // CallAgentExecutor:128-140 LLM 工具参数 agentId
  String agentId = getStringArg(args, call, "agentId");
  if ("self".equals(agentId)) { targetAgentId = agentCtx.getAgentName(); }  // agentId 直接赋给 agentName
  // AgentNames:6-8 注释自承同义混用
  /** Fail-closed validation for caller-supplied agentName / agentId values ... */
  ```
  glossary.md:9 规范术语为 `agentName`。
- **严重程度**: P1
- **现状**: 同一概念（agent 配置文件名）在 3 个名字固化于公开契约：glossary/engine 代码 agentName；team 代码/DSL/DB 列 agentModel/AGENT_MODEL；LLM 工具参数 agentId；消息 payload targetAgentId。CallAgentExecutor:140 直接互赋证实语义等价。
- **风险**: (1)glossary 不可信（明示含 call-agent DSL 用 agentName，实际工具参数 agentId、DSL agentModel）；(2)与 actorId（运行时实例 UUID）冲突，agentId 易误为 actorId 造成 actor 寻址错误；(3)跨表关联 ai_agent_session.AGENT_NAME 与 ai_agent_team_member.AGENT_MODEL 引用同一配置但列名不同；(4)DB schema 已固化需数据迁移。
- **建议**: 选定 agentName 为单一术语；工具参数 agentId→agentName（评估 prompt 影响）；DSL agentModel→agentName（xdef 版本兼容保留旧别名一周期）；DB 列 AGENT_MODEL→AGENT_NAME（ALTER TABLE RENAME COLUMN）；glossary 扩展为强约束声明。
- **信心水平**: 高
- **误报排除**: 三者语义相同（CallAgentExecutor:140 互赋、TeamMemberSpec javadoc 自承"agent configuration/model name"、AgentNames 注释并列 agentName/agentId）；差异固化到 DB schema/DSL xdef/LLM 工具 schema 三处公开契约；glossary 与代码冲突即规范性偏差。
- **复核状态**: 未复核

### [维度19-03] 同模块 SQL 列名常量大小写不统一（7 表 UPPER vs 2 表 lowercase）

- **文件**: AiAgentSessionTable/AiAgentCheckpointTable/AiAgentMessageTable/AiAgentDenialTable/AiAgentTeamTable/AiAgentTeamTaskTable/AiAgentTeamMemberTable（UPPER）；`session/NopAiSessionMessageTable.java:24-47`、`usage/NopAiChatResponseTable.java:24-46`（lowercase）
- **证据片段**:
  ```java
  // AiAgentSessionTable:24-37 UPPER
  public static final String COL_SESSION_ID = "SESSION_ID";
  // NopAiSessionMessageTable:24-47 lowercase
  public static final String COL_SESSION_ID = "session_id";
  ```
  code-style.md:53-56 明确「column code | UPPER_SNAKE_CASE」。
- **严重程度**: P2
- **现状**: 9 个 *Table.java 分两组：7 本模块原生表（AiAgent*）列常量 UPPER；2 跨模块镜像表（Nop*，镜像 nop-ai-dao 生成实体）lowercase。两组 javadoc 都声称"mirrors its column definitions"但取值相反。
- **风险**: (1)新加列踩坑——7 UPPER 表肌肉记忆下给 Nop* 加列倾向写 UPPER，但镜像目标 column code 是 lowercase，运行时 SQL 会失败（quoted identifier 大小写敏感）；(2)跨表 JOIN 列引用混用 case；(3)审查噪声。
- **建议**: 与 nop-ai-dao 对齐 column code 为 UPPER（仅 code 属性不影响 name/precision，无需 DB 迁移）；对齐前在 Nop* 类 javadoc 顶部显式声明"列名 lowercase 以镜像 nop-ai-dao，与 AiAgent* 不同是有意的"。
- **信心水平**: 中-高
- **误报排除**: 7+2 分布非单点；两组 javadoc 都声称 mirror 但取值相反，证明非有意设计而是规范分裂；编译/构建不检查列常量值内容。
- **复核状态**: 未复核

### [维度19-04] 拒绝决策类同类字段命名不对称，getReason() 在 sibling 类返回不同类型

- **文件**: `security/ApprovalDecision.java:30-41,88-106`；`security/DenialResult.java:33-48,89-91`；`security/DenialReason.java`；`security/ApprovalDenialKind.java`；`security/MatrixDecision.java:12-60`
- **证据片段**:
  ```java
  // ApprovalDecision: categorical enum 字段 denialKind:ApprovalDenialKind，reason:String
  public ApprovalDenialKind getDenialKind() { return denialKind; }
  public String getReason() { return reason; }   // 返回 String
  // DenialResult: categorical enum 字段直接叫 reason:DenialReason
  public DenialReason getReason() { return reason; }   // 返回 DenialReason enum
  ```
- **严重程度**: P2
- **现状**: 三个 sibling 决策类对"分类枚举"和"自由文本"采用不对称命名：ApprovalDecision（denialKind:enum + reason:String）、DenialResult（reason:enum + message:String）、MatrixDecision（无枚举字段 + reason:String）。getReason() 在 ApprovalDecision/MatrixDecision 返回 String，在 DenialResult 返回 enum。DenialReason 与 ApprovalDenialKind 共享 HUMAN_REJECTED/TIMEOUT 值（DenialReason javadoc 自承对应 ApprovalDenialKind），同一分类轴命名为 Reason 与 Kind 两词。
- **风险**: (1)方法名陷阱——getReason() 期望 String 遇 DenialResult 返回 enum 需类型分支；(2)复制 ApprovalDecision 代码到 DenialResult 时 getReason().equals() 编译失败；(3)概念命名稀释 Kind/Reason 双词汇。
- **建议**: 选定 kind 作 categorical enum 字段名、reason 作自由文本 String 字段名（保持 ApprovalDecision 现状）；DenialResult 字段 reason:DenialReason 改名 kind（或 enum 改名 DenialKind）；glossary 明确"categorical enum 后缀统一 Kind"。
- **信心水平**: 中-高
- **误报排除**: 三个 sibling 字段定义对照命名不对称是事实；getReason() 返回类型不一致有编译期证据；DenialReason 与 ApprovalDenialKind 值重叠+javadoc 自承对应证明同一概念轴双词汇。
- **复核状态**: 未复核

### [维度19-05] app.orm.xml 全部 displayName 英文，缺 i18n-en:displayName 本地化

- 与维度04-04 同一发现。详见 `04-orm-model.md` [维度04-04]。严重程度 P2。

### [维度19-06] 本模块主键命名三选不一（sid/id/业务键），与平台规范 id+tagSet="seq" 不符

- **文件**: `app.orm.xml:32,64,96,122`；4 *Table.java；code-style.md:55-57
- **证据片段**:
  ```xml
  <!-- app.orm.xml 4 表 4 种 PK -->
  <column name="sid" code="SID" ... primary="true" ... domain="messageId" .../>           <!-- ai_agent_message -->
  <column name="sessionId" code="SESSION_ID" ... primary="true" ... domain="sessionId" .../>  <!-- ai_agent_session -->
  <column name="watermark" code="WATERMARK" ... primary="true" .../>                       <!-- ai_agent_checkpoint -->
  ```
  code-style.md:55-57「主键 id, VARCHAR(36), tagSet="seq"」；DBMessageService:235 `String sid = StringHelper.generateUUID();`（32 字符）。
- **严重程度**: P2
- **现状**: 4 原生表 PK 命名完全不统一且均偏离平台规范：0 表用 id 命名，0 列 tagSet="seq"；VARCHAR 长度 32/100 两种；业务键直接当 PK（sessionId/watermark）；sid 在 Nop 无先例。
- **风险**: (1)codegen 链路不识别（模板靠 tagSet="seq" 识别主键生成器），本模块全绕过手写 PK 生成；(2)跨表外键歧义；(3)sid 命名歧义（Surrogate ID?String ID?无 javadoc）；(4)VARCHAR(32) vs 平台 VARCHAR(36) UUID 两套长度并存。
- **建议**: 评估迁移到 id VARCHAR(36)+tagSet="seq"；或不便迁移则模块内统一一种约定（建议 id VARCHAR(36) 与 nop-ai-dao 一致）+ 文档化豁免；glossary 记录偏差。
- **信心水平**: 中
- **误报排除**: 平台规范要求 id（code-style.md 明文）；4 表全用业务键/sid 无一用 id；tagSet="seq" 4 PK 全缺；可能与"模块绕开 codegen 自管 schema"设计选择有关，建议含折衷路径。
- **复核状态**: 未复核

### [维度19-07] glossary.md 的 IMessageService 条目与代码实际架构不符（混淆 Agent 层与平台层抽象）

- **文件**: 文档 `glossary.md:39`；代码 `message/IAgentMessenger.java:1-33`、`message/LocalAgentMessenger.java:39-57`、`message/DBMessageService.java:38-67`
- **证据片段**:
  - 文档 glossary:39「IMessageService | 基础设施 | Agent 间内部通信（LocalMessageService / DB-backed）」。
  - 代码 IAgentMessenger:8-12「Agent-domain inter-agent messenger, layered on the platform IMessageService」；LocalAgentMessenger:39-57 wraps IMessageService 字段；DBMessageService:38-67 implements IMessageService（平台抽象，非 Agent 域）。
- **严重程度**: P3
- **现状**: glossary 把平台层 IMessageService 当作 Agent 间通信接口，列 LocalMessageService/DB-backed 作实现，漏掉 Agent 层抽象 IAgentMessenger，误标 LocalMessageService（平台 in-memory）为 Agent 通信实现，而真实 Agent 域 in-memory 实现是 LocalAgentMessenger。
- **风险**: 按 glossary 阅读的新开发者会找 IMessageService 实现 Agent-to-Agent 通信，绕过 IAgentMessenger 这层（send/request/registerHandler 三个 Agent 域语义方法）；想扩展（如 broadcast）按 glossary 找不到正确扩展点。
- **建议**: glossary 新增 IAgentMessenger 条目（Layer1 Agent 域 messenger）；修订 IMessageService 条目为"平台消息总线，Agent 域通过 IAgentMessenger 包装使用"；标注 LocalMessageService 为平台实现非本模块。
- **信心水平**: 高
- **误报排除**: IMessageService import 路径是 io.nop.api.core.message（平台包）非 Agent 模块；LocalAgentMessenger javadoc 自承 layered on platform IMessageService 证明双层架构是设计意图。
- **复核状态**: 未复核

## 一致项（已检查无问题）

glossary 标识符 sessionId/actorId 代码一致区分；核心接口大部分（IAgentEngine/IAgentExecutor/IApprovalGate/IDenialLedger 等）命名与定义一致；核心数据结构（FencingToken/SessionSnapshot 等）一致；错误码统一 NopAiAgentException（SandboxException extends 它是 design §7.1 明示特化）；包结构与术语一致；bean id ai-agent-tools:* 与类名一一对应；CANCELLED 英式拼写模块内统一（非漂移）。

## 维度复核结论

[维度19-01] 独立复核：**降级 P1→P3**（纯命名风格无功能影响）。[维度19-05] 与 04-04 同源去重。其余（19-02/03/04/06/07）复核未发现反证，保留。最高优为 19-02（agentName/agentModel/agentId 一词三义固化 DB/DSL/工具 schema）。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 19-01 | P3 | model/AgentExecStatus.java | 小写枚举 vs 其他 UPPER（已降级，纯风格） |
| 19-02 | P1 | agent.xdef + team/TeamMemberSpec + tool/CallAgentExecutor | agentName/agentModel/agentId 一词三义固化契约 |
| 19-03 | P2 | session/NopAiSessionMessageTable.java 等 | SQL 列常量大小写不统一（7 UPPER vs 2 lower） |
| 19-04 | P2 | security/ApprovalDecision.java 等 | denial 决策类字段命名不对称，getReason() 返回类型不一 |
| 19-05 | P2 | app.orm.xml | displayName 全英文缺 i18n-en（同04-04） |
| 19-06 | P2 | app.orm.xml | 主键命名三选不一，偏离平台 id+tagSet=seq 规范 |
| 19-07 | P3 | ai-dev/design/.../glossary.md | IMessageService 条目混淆 Agent 层与平台层 |
