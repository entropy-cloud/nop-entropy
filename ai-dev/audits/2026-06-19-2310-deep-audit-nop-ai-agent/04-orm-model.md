# 维度 04：ORM 模型与实体设计

## 检查范围

`app.orm.xml`（手写源模型，161 行，审计对象）；11 个 `*Table.java` 元数据持有类；对照 nop-auth-dao/nop-ai-dao i18n 约定。注意：本模块无标准 codegen 链路，7/11 表走原生 DDL（文档化设计裁定，非发现）。

## 第 1 轮（初审）发现

### [维度04-01] ORM 模型缺失全部 4 个运行时二级索引

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml`（entities 段 28-160，全文 0 处 `<index>`）
- **证据片段**:
  ```xml
  <!-- app.orm.xml：4 entity，0 <index> -->
  <entity name="io.nop.ai.agent.message.AiAgentMessage" tableName="ai_agent_message" ...>
      <columns> ... </columns>  <!-- 无 <indexes> -->
  </entity>
  ```
  ```java
  // AiAgentMessageTable.java:51-53
  public static final String DDL_CREATE_INDEX = "CREATE INDEX IF NOT EXISTS IDX_" + TABLE_NAME + "_TOPIC_STATUS ON " + TABLE_NAME + "(" + COL_TOPIC + ", " + COL_STATUS + ")";
  // AiAgentSessionTable/AiAgentDenialTable/AiAgentCheckpointTable 同样有运行时 DDL 索引
  ```
- **严重程度**: P2
- **现状**: 4 个 ORM 实体在源模型无任何 `<index>` 声明，但对应 Table.java 运行时通过原生 DDL 创建 4 个二级索引（IDX_AI_AGENT_MESSAGE_TOPIC_STATUS / IDX_AI_AGENT_SESSION_STATUS / IDX_AI_AGENT_DENIAL_SESSION_ID / IDX_AI_AGENT_CHECKPOINT_SESSION_SEQ）。
- **风险**: app.orm.xml 是 Table.java Javadoc 明示的"schema source of truth"，但索引只存在 Java 常量里；以 ORM 为输入的工具（schema 文档/迁移脚本/ORM→DDL 同步/DBA 审计）会漏掉索引；切到正规 ORM 持久化会丢索引。
- **建议**: 在 app.orm.xml 每个 entity 补 `<indexes>` 段，使模型与运行时 DDL 单一来源一致。
- **信心水平**: 高
- **误报排除**: app.orm.xml 是手写源模型（无 _ 前缀，x:schema 指向 orm.xdef），合法审计对象；grep 确认 0 处 index；逐一对照 Table.java 索引真实存在。
- **复核状态**: 未复核

### [维度04-02] messageCount/tokenEstimate 复用语义不匹配的 domain

- **文件**: `app.orm.xml:149-154`（列）；`:20,24`（domain 定义）
- **证据片段**:
  ```xml
  <domain name="seqNumber" stdSqlType="INTEGER"/>      <!-- 序列号语义 -->
  <domain name="epochMillis" stdSqlType="BIGINT"/>     <!-- 墙钟毫秒语义 -->
  <column name="messageCount" ... domain="seqNumber" stdSqlType="INTEGER" .../>   <!-- 消息计数 -->
  <column name="tokenEstimate" ... domain="epochMillis" stdSqlType="BIGINT" .../>  <!-- token估算 -->
  ```
- **严重程度**: P3
- **现状**: 仅因底层类型相同复用语义完全不相关的 domain。好在每列显式写 stdDataType/stdSqlType 覆盖，当前 DDL/Java 类型正确。
- **风险**: domain 是语义标签；调整 epochMillis/seqNumber 会意外波及这两列，潜伏语义耦合。
- **建议**: 引入专用 domain（tokenCount/countInt）或不绑定 domain。
- **信心水平**: 高
- **误报排除**: AiAgentCheckpointTable:67-68 确认运行期类型正确（无功能性错误）。
- **复核状态**: 未复核（与维度10-03 同一发现）

### [维度04-03] AiAgentMessage.status 缺 defaultValue，与运行时 DDL 不一致

- **文件**: `app.orm.xml:41-43`；对照 `AiAgentMessageTable.java:42`
- **证据片段**:
  ```xml
  <column name="status" ... mandatory="true" domain="messageStatus" stdSqlType="INTEGER" .../>  <!-- 无 defaultValue -->
  ```
  ```java
  // AiAgentMessageTable.java:33-35,42
  public static final int STATUS_PENDING = 0;
  ... COL_STATUS + " INTEGER NOT NULL DEFAULT " + STATUS_PENDING + ", "
  ```
- **严重程度**: P3
- **现状**: ORM 仅 mandatory，无 defaultValue；运行时 DDL 写死 DEFAULT 0。
- **风险**: 以 ORM 为输入的 DDL 生成会丢默认值；模型对"PENDING=0 入口状态"无自描述。
- **建议**: 补 defaultValue="0"。
- **信心水平**: 中-高
- **误报排除**: 对照运行时 DDL 确认 ORM 缺；其余 3 实体列默认值一致。
- **复核状态**: 未复核

### [维度04-04] displayName 全部英文裸串，缺 i18n-en:displayName 本地化

- **文件**: `app.orm.xml`（4 entity + 37 column 全部 displayName 英文）
- **证据片段**:
  ```xml
  <entity ... displayName="AI Agent Message" ...>  <!-- 无 i18n-en:displayName -->
  <column ... displayName="Message SID"/>
  ```
  对照 nop-auth-dao/_app.orm.xml:42 / nop-ai-dao/_app.orm.xml:1005 均为 `displayName="中文" i18n-en:displayName="English"`。
- **严重程度**: P2
- **现状**: 全部 41 处 displayName 纯英文，放默认（应为中文）槽，无 i18n-en；模块内无任何 *.i18n.yaml。
- **风险**: 默认 locale zh-CN 下 admin/CRUD 页面展示英文，与同界面 nop-ai-dao 中文混杂；i18n 流回路缺失；与平台 6 模块+nop-ai-dao 规范分裂。
- **建议**: 补中文 displayName + i18n-en:displayName；或文件顶部声明"基础设施内部表不本地化"豁免。
- **信心水平**: 中-高
- **误报排除**: 交叉验证 nop-auth-dao/nop-job/nop-ai-dao 均用双语；模块内 find 0 个 i18n.yaml。
- **复核状态**: 未复核（与维度19-05 同一发现）

## 范围澄清（非发现）

- **7/11 表不在 ORM 中**：team/team_task/team_member/session_lock/daemon_coord/nop_ai_session_message/nop_ai_chat_response 在 Table.java Javadoc 明示"raw JDBC + Table 常量类，不引入 ORM/DAO/codegen"，是文档化设计裁定。
- **审计字段不完整**（无 createdBy/updatedBy/version/delFlag）：与"raw JDBC、不走 ORM 持久层"定位一致。
- **tenantId 可空**：null 表示无租户上下文（向后兼容单租户），与 Table.java 一致。

## 维度复核结论

4 项发现均以手写源模型 app.orm.xml 与 Table.java 为对象（非生成产物），事实核验成立。04-02 与维度10-03 同源、04-04 与维度19-05 同源，去重后保留。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 04-01 | P2 | app.orm.xml | 缺全部 4 个运行时二级索引声明 |
| 04-02 | P3 | app.orm.xml | messageCount/tokenEstimate 复用语义不符的 domain |
| 04-03 | P3 | app.orm.xml | AiAgentMessage.status 缺 defaultValue |
| 04-04 | P2 | app.orm.xml | displayName 全英文，缺 i18n-en 本地化 |
