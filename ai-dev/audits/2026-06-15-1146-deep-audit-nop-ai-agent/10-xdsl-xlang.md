# 维度 10：XDSL 与 XLang 正确性（nop-ai-agent）

## 第 1 轮（初审）

### [维度10-1] test-unknown-mode-agent.agent.xml 的 `mode="unknown"` 违反 agent.xdef 的 enum 约束

- **文件**: `nop-ai/nop-ai-agent/src/test/resources/_vfs/test-unknown-mode-agent.agent.xml:1-8`
- **证据片段**:
  ```xml
  <agent x:schema="/nop/schema/ai/agent.xdef" xmlns:x="/nop/schema/xdsl.xdef"
         name="test-unknown-mode-agent" mode="unknown">

      <description>Test agent for unknown mode</description>

      <prompt>You are a test assistant.</prompt>

  </agent>
  ```
  对照 `nop-kernel/nop-xdefs/.../nop/schema/ai/agent.xdef:9`:
  ```xml
  mode="enum:react,plan,single-turn|react">
  ```
- **严重程度**: P2
- **现状**: 文件自身声明 `x:schema="/nop/schema/ai/agent.xdef"`，但 `mode="unknown"` 违反该 schema 的 enum 约束。该文件未被任何 `loadComponentModel` 调用引用，所以验证从未触发。实际测试通过 `new AgentModel()` + `setMode("unknown")` 绕过 XML 加载。
- **风险**: 这是一份"看起来在为 unknown-mode 测试做 fixture、实际从不被加载"的孤儿文件，且其内容违反自身声明的 xdef。违反"XDSL 资源必须满足其声明 schema"的硬规则。
- **建议**: 二选一：(a) 删除该文件（测试已用程序化方式构造）；(b) 新增专门测试断言 `loadComponentModel` 抛出 XDSL 校验异常，并在文件头注释说明用途。
- **信心水平**: 高
- **误报排除**: 已 grep 全模块 `test-unknown-mode-agent` 字面量；唯一引用就是文件自身。已确认 `TestModeDispatch` 用 `new AgentModel()` 不经 XML 加载。
- **复核状态**: 未复核

### [维度10-2] `app.orm.xml` 与 `AiAgent*Table.java` 的 DDL 漂移：索引、列默认值在 ORM 中缺失

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml`（全文 148 行）vs 四个 `*Table.java`
- **证据片段**:

  `AiAgentMessageTable.java:32-47`（DDL 含 DEFAULT 和复合索引）：
  ```java
  + COL_STATUS + " INTEGER NOT NULL DEFAULT " + STATUS_PENDING + ", "
  ...
  + "CREATE INDEX IF NOT EXISTS IDX_" + TABLE_NAME + "_TOPIC_STATUS "
  + "ON " + TABLE_NAME + "(" + COL_TOPIC + ", " + COL_STATUS + ")";
  ```

  `app.orm.xml:40-48`（ORM 中 status 列无 defaultValue，实体无 indexes）：
  ```xml
  <column name="status" code="STATUS" propId="4" mandatory="true"
          domain="messageStatus" stdDataType="int" stdSqlType="INTEGER"
          displayName="Status"/>
  ```
  类似漂移出现在 AiAgentDenial/AiAgentSession/AiAgentCheckpoint 三个实体的索引缺失。
- **严重程度**: P2
- **现状**: 4 个 `AiAgent*Table.java` 顶部的 javadoc 都明确写"The table schema is defined in the ORM model at app.orm.xml"，把 ORM 标记为权威 schema 源。但实际 ORM 与 DDL 不一致：(a) DDL 中 4 个索引在 ORM 中完全缺失；(b) AiAgentMessage.STATUS 列的 DEFAULT 0 在 ORM 中没有 defaultValue。
- **风险**: 如果下游应用按本模块的 `app.orm.xml` 做 `x:extends` 合并由 codegen 生成 DDL，会得到比 `AiAgent*Table.java` 运行时实际执行的 schema 少了 4 个索引、STATUS 列没了默认值的数据库。
- **建议**: 二选一：(a) 在 `app.orm.xml` 补 `<indexes>` 和 `defaultValue`；(b) 修订 4 个 `*Table.java` 的 javadoc，明确"运行时以本类 DDL 为准"。
- **信心水平**: 高
- **误报排除**: 已逐列对比 4 个 `*Table.java` 的 DDL 与 `app.orm.xml`；类型/precision/mandatory/main 列均一致，仅索引与 DEFAULT 这两类有遗漏。
- **复核状态**: 未复核

### [维度10-3] `app.orm.xml` 声明的 4 个实体类在 Java 侧完全不存在（孤儿 ORM 模型）

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:28,57,86,109`
- **证据片段**:
  ```xml
  <entity name="io.nop.ai.agent.message.AiAgentMessage" tableName="ai_agent_message" ...>
  <entity name="io.nop.ai.agent.security.AiAgentDenial" tableName="ai_agent_denial" ...>
  <entity name="io.nop.ai.agent.session.AiAgentSession" tableName="ai_agent_session" ...>
  <entity name="io.nop.ai.agent.reliability.AiAgentCheckpoint" tableName="ai_agent_checkpoint" ...>
  ```
  Java 侧实际：
  ```
  $ find src/main/java -name "AiAgentMessage.java" ... (no output)
  ```
  4 个声明的实体类均不存在；实际持久化由 DB* 类通过 raw JDBC + *Table.java 完成。
- **严重程度**: P2
- **现状**: 文件本身语法/语义对 `orm.xdef` 合规，但作为一份"声明了 4 个实体 + registerShortName="true" + 主键 + propId"的完整 ORM 模型，它的存在让阅读者误以为这些实体会被 codegen 生成、被 EQL 短名注册——实际都没有。
- **风险**: 与 AGENTS.md "Hard Stop: Generated Files" + 模型优先开发链路冲突。维护者改 `app.orm.xml` 期望触发实体重生成，会得到无效结果。
- **建议**: 在文件头注释中明确"本 ORM 仅作为概念性 schema 文档，不参与 codegen"，或改名为 `schema.orm.xml` / 加 `x:abstract="true"`。
- **信心水平**: 高
- **误报排除**: 已 `find` 整个 `src/main/java` 确认无 AiAgentMessage.java 等类；已读 DBSessionStore 确认走 raw JDBC。
- **复核状态**: 未复核

### [维度10-4] `ai-agent-tools.beans.xml` 缺少 `x:schema` 声明，且 `xmlns:ioc` URI 与平台标准不一致

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/beans/ai-agent-tools.beans.xml:1-3`
- **证据片段**:
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <beans xmlns:x="/nop/schema/xdsl.xdef"
         xmlns:ioc="urn: nop-ioc:1.0">
  ```
  对照同仓内核约定：
  ```xml
  <beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:ioc="ioc">
  ```
- **严重程度**: P3
- **现状**: 文件未声明 `x:schema="/nop/schema/beans.xdef"`。运行时由 `BeanContainerBuilder.addResource` 强制以 beans.xdef 解析，所以**运行时不会出错**。`xmlns:ioc="urn: nop-ioc:1.0"` 与平台标准的 `"ioc"` 不一致，但本文件全文未出现任何 `<ioc:*>` 元素，所以这个 URI 字符串实际是死代码。
- **风险**: (a) 缺少 `x:schema` 让 IDE / 编辑器 / 一切按"扫 x:schema 识别 XDSL 资源"的工具漏掉该文件，长期形成校验盲区；(b) 与 nop-ai-core/ai-defaults.beans.xml 仅隔一个目录却用不同的命名空间 URI，是内部分裂。
- **建议**: 改为 `<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:ioc="ioc">`。
- **信心水平**: 高
- **误报排除**: 已读 `DslNodeLoader.loadFromNode` 与 `BeanContainerBuilder.addResource` 确认运行时 fallback 路径，所以定级 P3 而非 P0/P1。
- **复核状态**: 未复核

### [维度10-5] `app.orm.xml` 声明 `xmlns:ext="ext"` 但全文无任何 `ext:` 使用

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:2-3`
- **证据片段**:
  ```xml
  <orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       xmlns:ext="ext">
  ```
- **严重程度**: P3
- **现状**: 命名空间声明存在但无引用，是死代码。
- **风险**: 极低。仅为风格一致性 / 冗余清理。
- **建议**: 删除 `xmlns:ext="ext"` 声明。
- **信心水平**: 高
- **误报排除**: 已对全文 grep 确认 `ext:` 0 引用。
- **复核状态**: 未复核

## 零发现项（已覆盖未发现）

| 检查项 | 结论 |
|---|---|
| agent.xdef 的 xdef:ref 路径解析 | 通过 |
| register-model.xml 的 loader class/schemaPath | 通过 |
| record-mappings.xml 的 c:import + GenReverseMappings | 通过 |
| app.orm.xml 的 domain/column/registerShortName/propId 与 orm.xdef 对齐 | 通过（仅索引/DEFAULT 缺失，见 10-2） |
| 19 个 test-*.agent.xml 必填元素 | 通过（除 10-1 的 mode 违规） |
| x:override 使用 | 通过（本模块未使用） |
| _vfs 目录结构 | 通过 |

## 维度复核结论

| 发现 | 复核结论 | 理由 |
|------|---------|------|
| [维度10-1] test-unknown-mode-agent 违反 enum | **保留 P2** | xdef enum 约束确证，文件违反自身声明 schema。 |
| [维度10-2] ORM 与 DDL 漂移 | **保留 P2** | 逐列对比确证索引/DEFAULT 缺失。 |
| [维度10-3] 孤儿 ORM 实体 | **保留 P2** | find 确证 4 实体类不存在。 |
| [维度10-4] beans.xml 缺 x:schema | **保留 P3** | 运行时 fallback 所以 P3；但校验盲区真实。 |
| [维度10-5] 冗余 xmlns:ext | **保留 P3** | 风格问题。 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 10-1 | P2 | test-unknown-mode-agent.agent.xml | mode="unknown" 违反 agent.xdef enum 约束 |
| 10-2 | P2 | app.orm.xml vs AiAgent*Table.java | ORM 缺少索引与列默认值，与 DDL 漂移 |
| 10-3 | P2 | app.orm.xml:28,57,86,109 | 声明的 4 个实体类 Java 侧不存在（孤儿 ORM） |
| 10-4 | P3 | ai-agent-tools.beans.xml:1-3 | 缺少 x:schema，xmlns:ioc URI 与平台标准不一致 |
| 10-5 | P3 | app.orm.xml:2-3 | 冗余 xmlns:ext 声明 |
