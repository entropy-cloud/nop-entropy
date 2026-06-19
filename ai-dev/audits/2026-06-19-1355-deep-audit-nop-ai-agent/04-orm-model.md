# 维度 04：ORM 模型与实体设计 — nop-ai-agent

## 关键背景认定（影响所有后续发现的判级）

通过阅读 4 个 DB* 实现类（`DBMessageService`、`DBDenialLedger`、`DBSessionStore`、`DBCheckpointManager`）和 4 个 `*Table.java` DDL 常量类、模块 `pom.xml`、`application.yaml`（**不存在**）、`register-model.xml`、`record-mappings.xml`、单元测试，确认以下事实：

1. **app.orm.xml 从未被 Nop ORM 引擎加载**：模块 `pom.xml` 只依赖 `nop-dao`（JDBC 抽象），不依赖 `nop-orm`（ORM 引擎）；模块内不存在 `application.yaml`/`application.properties`，因此 `nop.orm.init-database-schema=false`（默认）；全模块无 `IEntityDao`/`@OrmEntity`/`@SqlLibMapper` 引用。
2. **4 个 ORM 实体类（`AiAgentMessage`/`AiAgentDenial`/`AiAgentSession`/`AiAgentCheckpoint`）在 Java 源码中不存在**（glob 返回 No files found）。
3. **真实建表/CRUD 走原始 JDBC**：每个 `DB*` 类的 `initSchema()` 执行 `*Table.java` 中的 `DDL_CREATE_TABLE`/`DDL_CREATE_INDEX` 字符串。
4. **测试只做 XML 结构断言**：用 `XNodeParser` 解析 XML 文本，不通过 ORM 引擎。
5. **app.orm.xml 是手写源（非生成）**：无 `x:extends="_app.orm.xml"`；归档计划 179 明确写 "是源 ORM 模型（非生成的 `_app.orm.xml`）... ORM 模型结构是 Protected Area（plan-first）"。

→ 这意味着 app.orm.xml 当前处于"被声明为源、却未接入运行时"的悬空状态。本维度大部分"模型层缺陷"对线上行为无影响，但会误导后续按"模型优先"约定工作的开发，且若有人开启 ORM 引擎或运行 codegen 会得到与运行时不一致的产物。

## 第 1 轮（初审）

### [维度04-01] ORM 源模型与运行时实现完全脱节（架构性问题）

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:29-160`（4 个实体声明）；运行时实际入口 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:173-181`
- **证据片段**:
  ```java
  // DBMessageService.initSchema —— 真实建表走原始 JDBC，不经过 ORM
  private void initSchema() {
      try (Connection conn = dataSource.getConnection();
           Statement stmt = conn.createStatement()) {
          stmt.execute(AiAgentMessageTable.DDL_CREATE_TABLE);
          stmt.execute(AiAgentMessageTable.DDL_CREATE_INDEX);
      } catch (SQLException e) {
          throw new NopAiAgentException("DBMessageService: failed to initialize schema: " + e.getMessage(), e);
      }
  }
  ```
  ```xml
  <!-- app.orm.xml:29-34 —— 声明了一个 Java 中并不存在的实体类 -->
  <entity name="io.nop.ai.agent.message.AiAgentMessage" tableName="ai_agent_message"
          displayName="AI Agent Message" registerShortName="true">
      <columns>
          <column name="sid" code="SID" propId="1" primary="true" mandatory="true"
                  domain="messageId" stdDataType="string" stdSqlType="VARCHAR"
                  displayName="Message SID"/>
  ```
- **严重程度**: P1
- **现状**: app.orm.xml 声明 4 个全限定实体类（`io.nop.ai.agent.{message,security,session,reliability}.AiAgent*`），但这些 Java 类在仓库中不存在；真实建表与 CRUD 由 4 个 `*Table.java`（手写 DDL 字符串）+ 原始 JDBC 完成，平台 ORM 引擎完全不参与。归档计划 179 把此文件标注为"源 ORM 模型 / Protected Area (plan-first)"，但实现路径已经偏离。
- **风险**: 任何按 AGENTS.md / `model-first-development.md` "改表回到 model/*.orm.xml" 约定工作的开发会误改此文件，期望触发 codegen 与 ORM 重生成——实际什么都不会发生，且与运行时 DDL 双向漂移；若有人开启 `nop.orm.init-database-schema=true`，引擎会按此 XML 建表，得到的表结构与 `*Table.java` 的 DDL **不一致**（见 04-05 索引缺失、04-08 审计字段缺失、04-10 7 张表缺失），导致运行时 `INSERT/SELECT` 列对不上。
- **建议**: 二选一并显式记录决策：(a) 真正接入 ORM 引擎（生成实体类、`_app.orm.xml`、`_dao.beans.xml`，删除手写 DDL），或 (b) 删除/降级 app.orm.xml 为纯文档（移到 `docs/` 或加 `<!-- documentation only, runtime schema is *Table.java -->` 显眼注释），并把归档计划 179 的"源 ORM 模型 / Protected Area"标注同步修订。当前模块新表（`AiAgentTeamTable`、`AiAgentTeamTaskTable`、`AiAgentSessionLockTable` 等）的 Javadoc 已经写了"Raw JDBC + Table 常量类（design 裁定 1）... No ORM / DAO / codegen pipeline is introduced"，说明设计已演进到 (b)，但老 4 张表的 ORM 文件没跟上。
- **信心水平**: 确定
- **误报排除**: 不是"看起来不优雅"。有可验证的硬证据：(1) Java 实体类 glob 返回空；(2) pom.xml 无 `nop-orm` 依赖；(3) 模块无 application.yaml，`init-database-schema` 必为默认 false；(4) `DBMessageService.initSchema` 直接执行 `*Table.DDL_*` 字符串。这构成结构性契约漂移，影响后续维护与运行时启用路径。
- **复核状态**: 未复核

---

### [维度04-02] AiAgentCheckpoint.tokenEstimate 复用 epochMillis 域（语义错误）

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:20`（域定义）+ `:152-154`（列引用）
- **证据片段**:
  ```xml
  <!-- 域定义：epochMillis 语义为"自纪元起的毫秒时间戳" -->
  <domain name="epochMillis" stdSqlType="BIGINT"/>
  ...
  <!-- tokenEstimate 列：实际语义是"token 数量计数"，却复用 epochMillis 域 -->
  <column name="tokenEstimate" code="TOKEN_ESTIMATE" propId="11" mandatory="true"
          domain="epochMillis" stdDataType="long" stdSqlType="BIGINT"
          displayName="Token Estimate"/>
  ```
- **严重程度**: P2
- **现状**: `tokenEstimate` 表达的是"消息 token 数估算"（一个计数器，非负整数语义），却绑定到名为 `epochMillis`（毫秒时间戳）的域。同列在 `AiAgentCheckpointTable.java:68` 为 `BIGINT NOT NULL`，运行时由 `Checkpoint.getTokenEstimate()` 写入——确认是 token 计数而非时间戳。
- **风险**: 语义错配的域会在前端控件匹配（`domain` → 控件 fallback 链）、XMeta 派生、未来 i18n、以及人类阅读模型时产生误导：后续开发者看到 `domain="epochMillis"` 会以为是时间戳字段，按时间戳做范围查询/格式化/索引。当前运行时无影响（ORM 未启用、无前端），但属于"会误导后续开发的元数据问题"。
- **建议**: 新增专用域（如 `<domain name="tokenCount" stdSqlType="BIGINT"/>`）或至少改用语义中性的 `longCounter`；`messageCount` 同理（见 04-03）。
- **信心水平**: 确定
- **误报排除**: 不是同类"看起来不优雅"误报。`epochMillis` 域在本文件中同时被 `createdAt`/`updatedAt`/`checkpointTimestamp` 正确使用（这些确实是毫秒时间戳），唯独 `tokenEstimate` 语义错配——这是可验证的语义违反，不是风格偏好。
- **复核状态**: 未复核

---

### [维度04-03] AiAgentCheckpoint.messageCount 复用 seqNumber 域（语义错配）

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:24`（域定义）+ `:149-151`（列引用）
- **证据片段**:
  ```xml
  <domain name="seqNumber" stdSqlType="INTEGER"/>
  ...
  <!-- messageCount：实际是会话累计消息计数 -->
  <column name="messageCount" code="MESSAGE_COUNT" propId="10" mandatory="true"
          domain="seqNumber" stdDataType="int" stdSqlType="INTEGER"
          displayName="Message Count"/>
  ```
- **严重程度**: P3
- **现状**: `messageCount` 是"会话累计消息数"，复用了语义为"递增序列号"的 `seqNumber` 域。同实体 `seq` 列（行 128-130）也用 `seqNumber` 域——那里语义正确。`AiAgentCheckpointTable.java:67` 确认为 `INTEGER NOT NULL`。
- **风险**: 比 04-02 轻：`messageCount` 与 `seqNumber` 都是递增整数计数器，控件匹配与类型推导都正确，只是域名语义不精确。主要影响是模型可读性。
- **建议**: 新增 `messageCount`/`intCounter` 域，或显式注释。可与 04-02 一并处理。
- **信心水平**: 确定
- **误报排除**: 同 04-02，是可验证的语义违反而非风格问题；严重程度更低因 INTEGER 计数与序列号在控件/索引行为上一致。
- **复核状态**: 未复核

---

### [维度04-04] createdAt/updatedAt 跨实体物理类型不一致（TIMESTAMP vs BIGINT）

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:47-49`（Message.createdAt = TIMESTAMP）、`:108-113`（Session.createdAt/updatedAt = BIGINT epochMillis）、`:85-87`（Denial.createdAt = TIMESTAMP）、`:131-133`（Checkpoint 仅 checkpointTimestamp = BIGINT，无 createdAt/updatedAt）
- **证据片段**:
  ```xml
  <!-- AiAgentMessage / AiAgentDenial: createdAt = TIMESTAMP -->
  <column name="createdAt" code="CREATED_AT" propId="6" mandatory="true"
          domain="createTime" stdDataType="timestamp" stdSqlType="TIMESTAMP"
          displayName="Created At"/>
  ...
  <!-- AiAgentSession: createdAt/updatedAt = BIGINT (epochMillis) -->
  <column name="createdAt" code="CREATED_AT" propId="5" mandatory="true"
          domain="epochMillis" stdDataType="long" stdSqlType="BIGINT"
          displayName="Created At"/>
  <column name="updatedAt" code="UPDATED_AT" propId="6" mandatory="true"
          domain="epochMillis" stdDataType="long" stdSqlType="BIGINT"
          displayName="Updated At"/>
  ```
- **严重程度**: P2
- **现状**: 同名 `createdAt` 列在 Message/Denial 用 `TIMESTAMP`，在 Session 用 `BIGINT`（epoch 毫秒），Checkpoint 完全没有 `createdAt`/`updatedAt`。运行时同样分裂：`DBMessageService.java:213` 用 `ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()))`，而 `DBSessionStore.java:320-321` 用 `ps.setLong(5, session.getCreatedAt())`。
- **风险**: (1) `ScheduledRecoveryManager` 的恢复扫描按 `ai_agent_session.UPDATED_AT` 判断超时（见 `runtime/recovery/DefaultSessionTimeoutHandler.java:27`、`TimeoutAction.java:10`），如果将来有人误把 Session.UPDATED_AT 当 TIMESTAMP 比较（例如用 `WHERE UPDATED_AT > ?` 传 `Timestamp`），会得到错误结果；(2) 跨表时间联合查询（如"按时间顺序列出 message+denial+checkpoint 事件流"）必须分别处理两套类型与单位；(3) BI/运维直接看 BIGINT 列需脑补毫秒转换。运行时目前正确，但维护负担真实存在。
- **建议**: 至少在 `*Table.java` 与 ORM 注释里统一声明"本模块时间戳一律 epochMillis BIGINT，唯独 Message/Denial 因依赖 `IMessageConsumeContext`/外部约定用 TIMESTAMP"——或者反过来统一到一种。
- **信心水平**: 很可能
- **误报排除**: 不是"风格不一致"。背后有运行时类型差异（`setTimestamp` vs `setLong`）与已知的恢复扫描查询路径，构成真实的跨实体契约不齐。
- **复核状态**: 未复核

---

### [维度04-05] 4 个实体在 ORM 模型层完全没有索引定义（DDL 层有，但模型层缺失）

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:28-160`（全文件无任何 `<indexes>` / `<index>` 节点）；对照 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/AiAgentDenialTable.java:53-55`
- **证据片段**:
  ```java
  // AiAgentDenialTable.java:37,53-55 —— DDL 层定义了索引
  public static final String INDEX_SESSION_ID = "IDX_AI_AGENT_DENIAL_SESSION_ID";
  ...
  public static final String DDL_CREATE_INDEX = ""
          + "CREATE INDEX IF NOT EXISTS " + INDEX_SESSION_ID + " "
          + "ON " + TABLE_NAME + "(" + COL_SESSION_ID + ")";
  ```
  ```xml
  <!-- app.orm.xml AiAgentDenial 实体（行 61-92）：无任何 <indexes> 节点 -->
  <entity name="io.nop.ai.agent.security.AiAgentDenial" tableName="ai_agent_denial"
          displayName="AI Agent Denial Record" registerShortName="true">
      <columns>
          ...
      </columns>
  </entity>
  ```
- **严重程度**: P2
- **现状**: app.orm.xml 4 个实体都没有 `<indexes>` 声明；但对应的 4 个 `*Table.java` 都定义了运行时索引（`IDX_AI_AGENT_MESSAGE_TOPIC_STATUS`、`IDX_AI_AGENT_DENIAL_SESSION_ID`、`IDX_AI_AGENT_SESSION_STATUS`、`IDX_AI_AGENT_CHECKPOINT_SESSION_SEQ`），且这些索引正是各 DB* 实现的核心查询路径。
- **风险**: 若启用 ORM 引擎或 codegen，生成的表会缺少全部索引，多实例 DB-backed 部署下会随数据增长线性退化。即使不启用 ORM，模型层与 DDL 层的索引定义单向漂移本身是元数据失真。
- **建议**: 把 4 个 DDL 中已存在的索引补回 app.orm.xml 的 `<indexes>` 节点；或者按 04-01 决策（b）把 ORM 降级为文档并显式声明。
- **信心水平**: 确定
- **误报排除**: 不是误报。运行时索引确实存在（DDL 有），但"源模型缺失索引"在启用 ORM 路径时会立即转为真实的全表扫描性能问题。
- **复核状态**: 未复核

---

### [维度04-06] 4 个实体之间完全没有关系定义（to-one / to-many）

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:61-92`（Denial，含 sessionId 无关系）、`:119-159`（Checkpoint，含 sessionId 无关系）、`:29-60`（Message，含 topic 无关系）；对照 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/DBSessionStore.java:170-192`（remove 只删 session 自身）
- **证据片段**:
  ```xml
  <!-- AiAgentDenial.sessionId 没有任何 <to-one> 关系声明指向 AiAgentSession -->
  <column name="sessionId" code="SESSION_ID" propId="2" mandatory="true"
          domain="sessionId" stdDataType="string" stdSqlType="VARCHAR"
          displayName="Session ID"/>
  ```
  ```java
  // DBSessionStore.remove：只 DELETE session 行，不级联清理 denial/checkpoint
  String deleteSql = "DELETE FROM " + AiAgentSessionTable.TABLE_NAME
          + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = ?";
  ```
- **严重程度**: P2
- **现状**: Denial.sessionId、Checkpoint.sessionId 在业务上是外键（指向 AiAgentSession.sessionId），但 4 个实体都没有任何 `<to-one>`/`<to-many>` 关系声明。运行时也确实没有级联：`DBSessionStore.remove` 只删 session 自身行；`DBCheckpointManager` Javadoc 明确写"The ai_agent_checkpoint table rows are never deleted"。
- **风险**: (1) 孤儿数据累积：session 删除后 denial/checkpoint 行永久残留，多租户场景下还会随 tenantId 漂移；(2) 无 GraphQL relation（本模块无 GraphQL 端点，此项当前不构成问题）；(3) 无平台级 cascade-delete，重置/清理脚本需手写多表 DELETE。
- **建议**: 至少声明 `AiAgentSession denialRecords` / `AiAgentSession checkpoints` 两个 `to-many` 关系；或者显式记录"不使用 ORM 关系，孤儿清理由独立的 sweep 任务负责"的决策。
- **信心水平**: 很可能
- **误报排除**: 不是误报。SKILL.md §6 与 `orm-model-design.md` 明确要求外键通过 `orm:ref-*` 在子表端声明关系。当前所有 sessionId 列只是裸 VARCHAR。严重程度因 ORM 未启用而降为 P2（无运行时影响）。
- **复核状态**: 未复核

---

### [维度04-07] tenantId 字段未启用平台多租户机制（无 tenantCol / 无多租户注解）

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:56-58,88-90,114-116,155-157`（4 实体的 tenantId 列）；对照 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/TenantSql.java` 及 `DBMessageService.java:304-306`
- **证据片段**:
  ```xml
  <!-- app.orm.xml：tenantId 只是一个普通列，实体无 tenantCol / no-tenant 等 tagSet -->
  <column name="tenantId" code="TENANT_ID" propId="9"
          domain="tenantId" stdDataType="string" stdSqlType="VARCHAR"
          displayName="Tenant ID"/>
  ```
  ```java
  // DBMessageService.findPending：运行时靠手工拼 TenantSql.whereTenant 注入 WHERE
  String tenant = currentTenant();
  ...
  if (tenant != null) {
      sql += TenantSql.whereTenant(AiAgentMessageTable.COL_TENANT_ID);
  }
  ```
- **严重程度**: P2
- **现状**: 4 个实体都声明了 tenantId 列，但实体的 `tagSet` 没有 `no-tenant`，`<entity>` 节点也没有 `tenantCol="TENANT_ID"` 类配置；平台标准多租户表达方式在本文件完全没有出现。多租户隔离实际由 `ITenantResolver` + `TenantSql.whereTenant()` 在每个 SQL 拼接处手工注入。
- **风险**: (1) 若启用 ORM 引擎，平台不会自动为这些实体注入 tenant 过滤，多租户隔离立即失效；(2) 每个新增 DB* 服务都必须记得在每个 SELECT/UPDATE/DELETE 上调用 `TenantSql.whereTenant`——4 个类都重复这一模式，遗漏一处即跨租户泄漏；(3) 与平台约定（声明式多租户）不一致，认知负担高。
- **建议**: 若坚持 raw-JDBC 路径，至少在 app.orm.xml 实体上加 `tenantCol="TENANT_ID"` 标注（文档作用）；或把 `TenantSql.whereTenant` 注入收口到一个统一的 SQL 构造器（减少重复）；或按 04-01 接入 ORM 后改用平台声明式多租户。
- **信心水平**: 很可能
- **误报排除**: 不是误报。平台确有标准多租户机制，本模块完全没用，靠手工 SQL 注入。但因运行时 SQL 注入路径已正确实现，降为 P2。
- **复核状态**: 未复核

---

### [维度04-08] 4 个实体均缺失 createdBy / updatedBy 审计字段

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:28-160`（全 4 实体）；对照 SKILL.md §4.1
- **证据片段**:
  ```xml
  <!-- AiAgentSession 实体只有 createdAt/updatedAt，无 createdBy/updatedBy -->
  <columns>
      <column name="sessionId" ... primary="true" .../>
      <column name="agentName" .../>
      <column name="status" .../>
      <column name="sessionData" .../>
      <column name="createdAt" .../>
      <column name="updatedAt" .../>
      <column name="tenantId" .../>
  </columns>
  <!-- AiAgentCheckpoint 连 createdAt/updatedAt 都没有，只有 checkpointTimestamp -->
  ```
- **严重程度**: P3
- **现状**: SKILL.md §4.1 规定每个业务表"必须"包含 `created_by`/`create_time`/`updated_by`/`update_time`。本模块 4 实体：Message 有 `createdAt` 无 `createdBy`/`updatedBy`；Denial 同；Session 有 `createdAt`+`updatedAt` 无 `createdBy`/`updatedBy`；Checkpoint 连 `createdAt`/`updatedAt` 都没有。
- **风险**: 严重程度有限——这些是 AI 子系统内部运行时状态表，写入者全是系统组件，不存在人类操作者，`createdBy` 语义上无值。但如果将来需要在多租户/多用户场景追溯"哪个用户触发的 agent 写了这条记录"，需要回溯到 session→用户映射。
- **建议**: 显式记录"AI 子系统内部表，写入者为系统组件，故省略 createdBy/updatedBy"的决策；Checkpoint 应至少补一个 `createdAt` 用于排序/审计。
- **信心水平**: 很可能
- **误报排除**: 不是误报（确实缺失），但被判为 P3 因 SKILL.md 是面向业务表的规范、本模块的表确属基础设施表，缺失可辩护。
- **复核状态**: 未复核

---

### [维度04-09] tenantId 域精度与平台标准不一致（VARCHAR(100) vs VARCHAR(32)）

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:25`（域定义）
- **证据片段**:
  ```xml
  <!-- 本模块定义 -->
  <domain name="tenantId" precision="100" stdSqlType="VARCHAR"/>
  ```
  ```text
  # SKILL.md §5.1 标准定义
  | tenantId | VARCHAR | 32 | 租户ID |
  ```
- **严重程度**: P3
- **现状**: 本模块把 `tenantId` 域精度定义为 100，平台 SKILL.md 标准是 32。所有 4 实体的 tenantId 列与 4 个 `*Table.java` 的 DDL 都用 VARCHAR(100)。
- **风险**: 元数据层面与平台标准漂移；若多模块联合部署、跨模块按 tenantId 做 join，可能遇到精度不匹配的 SQL 错误。运行时目前无影响。
- **建议**: 评估是否能改回 VARCHAR(32) 对齐标准；若 AI 子系统的 tenantId 来源（`ITenantResolver` 实现）确实可能超过 32 字符，则在 owner doc 显式声明偏离原因。
- **信心水平**: 很可能
- **误报排除**: 不是误报（数值可验证）。判 P3 因运行时无影响，且模块内自洽。
- **复核状态**: 未复核

---

### [维度04-10] ORM 模型只覆盖 4/11 张表（不完整）

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:28-160`（仅 4 实体）；对照 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/{runtime,team,usage,session}/AiAgent*Table.java` 等 11 个 *Table.java
- **证据片段**:
  ```java
  // AiAgentTeamTable.java:10-17 —— 新表显式声明不走 ORM
  * <p><b>Raw JDBC + Table 常量类（design 裁定 1）</b>: this table is managed
  * via raw JDBC (not as an ORM entity) — consistent with the module's
  * established DB-persistence pattern ...
  * No ORM / DAO / codegen pipeline is introduced.
  ```
  本模块 `*Table.java` 清单（11 个）：
  - `message/AiAgentMessageTable`（在 ORM 中）
  - `security/AiAgentDenialTable`（在 ORM 中）
  - `session/AiAgentSessionTable`（在 ORM 中）
  - `reliability/AiAgentCheckpointTable`（在 ORM 中）
  - `runtime/lock/AiAgentSessionLockTable`（**不在** ORM 中）
  - `runtime/coordination/AiAgentDaemonCoordTable`（**不在** ORM 中）
  - `team/AiAgentTeamTable`（**不在** ORM 中）
  - `team/AiAgentTeamMemberTable`（**不在** ORM 中）
  - `team/AiAgentTeamTaskTable`（**不在** ORM 中）
  - `session/NopAiSessionMessageTable`（**不在** ORM 中）
  - `usage/NopAiChatResponseTable`（**不在** ORM 中）
- **严重程度**: P2
- **现状**: 模块共有 11 张运行时表，但 app.orm.xml 只声明最早的 4 张。后续添加的 7 张表的 Javadoc 明确写"不走 ORM/DAO/codegen"——说明设计已经演进到"全 raw-JDBC"，但老 4 张表的 ORM 文件没删，造成新旧不一致。
- **风险**: (1) 任何阅读 app.orm.xml 的人会误以为它是完整的 schema 来源，实际只能看到 36% 的表；(2) 4 张老表的 ORM 模型与 7 张新表的纯 DDL 形成两种并存的"模型表达"，认知负担高；(3) 若按 AGENTS.md "改 ORM 模型结构是 Protected Area (plan-first)" 规则，新增第 12 张表到底走哪条路不清晰。
- **建议**: 与 04-01 同步决策：要么补齐 ORM 模型覆盖全部 11 张表，要么删除老 4 张表的 ORM 模型。当前混态是最差选择。
- **信心水平**: 确定
- **误报排除**: 不是误报。11 个 `*Table.java` 的存在与 app.orm.xml 仅 4 实体可通过 glob 直接验证。
- **复核状态**: 未复核

---

### [维度04-11] 主键设计跨实体不一致（sid vs sessionId vs watermark）+ 自然键充当主键

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:32-34`（Message.sid VARCHAR(32)）、`:64-66`（Denial.sid VARCHAR(32)）、`:96-98`（Session.sessionId VARCHAR(100)）、`:122-124`（Checkpoint.watermark VARCHAR(100)）
- **证据片段**:
  ```xml
  <!-- Message / Denial: 代理主键 sid（VARCHAR 32） -->
  <column name="sid" code="SID" propId="1" primary="true" mandatory="true"
          domain="messageId" stdDataType="string" stdSqlType="VARCHAR"
          displayName="Message SID"/>
  ...
  <!-- Session: 业务自然键 sessionId 直接当主键（VARCHAR 100） -->
  <column name="sessionId" code="SESSION_ID" propId="1" primary="true" mandatory="true"
          domain="sessionId" stdDataType="string" stdSqlType="VARCHAR"
          displayName="Session ID"/>
  ```
- **严重程度**: P3
- **现状**: 4 实体主键命名/语义/长度全不同：Message/Denial 用 `sid`；Session 用 `sessionId`；Checkpoint 用 `watermark`。`orm-model-design.md` §主键设计规范明确要求"字段名固定为 `id`、`stdSqlType="VARCHAR" precision="36"`、`tagSet="seq"`"，本模块无任何实体遵循。
- **风险**: (1) Session/Checkpoint 的主键是业务自然键，若业务需要重塑 sessionId/watermark 需做昂贵的主键迁移；(2) `tagSet="seq"` 缺失意味着即使启用 ORM，引擎也不会自动生成 UUID；(3) 长度 100 与平台推荐 36 不一致，跨实体 join 时索引效率略低。
- **建议**: 这类基础设施表使用自然键当 PK 是可辩护的设计选择，无需强制改 `sid`。但应在 owner doc 记录"主键策略不同于平台业务表标准"的决策。
- **信心水平**: 很可能
- **误报排除**: 不是误报（主键名/长度可验证）。判 P3 因运行时正确，自然键当 PK 在 append-only/不可变场景下是合理工程取舍。
- **复核状态**: 未复核

---

## 已检查且合规的项

- **registerShortName 冲突检查**：4 张 `ai_agent_*` 表名只在本模块出现一次，无冲突。且 ORM 未加载故不生效。
- **displayName 硬编码英文（无 i18n key）**：合理，本模块无前端管理界面，displayName 仅作 ORM 元数据可读性。
- **version/delFlag 字段缺失**：合理，append-only 表/全状态覆盖语义不适用。
- **record-mappings 配置**：与 ORM 实体无关（Plan DSL 用）。
- **register-model.xml**：与 ORM 无关（Agent DSL 模型装载器注册）。

## 维度复核结论

待独立复核子 agent 输出。

## 最终保留项

待复核完成后填写。
