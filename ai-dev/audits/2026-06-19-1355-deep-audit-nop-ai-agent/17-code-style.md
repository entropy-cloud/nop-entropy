# 维度 17（代码风格）+ 维度 19（命名一致性）— nop-ai-agent

## 维度 17 发现

### [维度17-1] ReActAgentExecutor.java 存在重复的 import 声明

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:78,82,111,112`
- **证据片段**:
  ```java
  // 行 78（第一处）
  import io.nop.ai.agent.security.DefaultPathAccessChecker;
  ...
  // 行 82（第一处）
  import io.nop.ai.agent.security.DefaultToolAccessChecker;
  ...
  // 行 111（重复 DefaultPathAccessChecker）
  import io.nop.ai.agent.security.DefaultPathAccessChecker;
  // 行 112（重复 DefaultToolAccessChecker）
  import io.nop.ai.agent.security.DefaultToolAccessChecker;
  import io.nop.ai.agent.security.ToolPathArgKeys;
  ```
- **严重程度**: P2
- **现状**: 同一个类 `DefaultPathAccessChecker` 和 `DefaultToolAccessChecker` 在 import 列表中被声明了两次。`grep -n '^import ' ... | sort | uniq -d` 确认这两行是文件中唯一的重复 import。
- **风险**: Java 编译器允许但通常会产生 warning；反映该文件的代码组装流程缺少自动化 lint。
- **建议**: 删除行 111-112 的重复 import；考虑在 CI 中接入 `check-import-order.sh` 覆盖 `nop-ai-agent`。
- **信心水平**: 确定（grep + 手动读行号交叉验证）
- **误报排除**: 这不是 IDE 自动 import 的合法产物（IDE 会去重）；也不是 `import static` vs `import` 的混淆。
- **复核状态**: 未复核

---

### [维度17-2] import 分组顺序与平台文档约定的方向完全相反，且模块内部分裂

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:3-140`（代表性样本，同类问题覆盖 122 个文件）
- **证据片段**:
  ```java
  // DefaultAgentEngine.java 实际顺序
  import io.nop.ai.agent.compact.IContextCompactor;     // io.nop.* 在最前
  ...
  import io.nop.ai.toolkit.api.IToolManager;
  import io.nop.api.core.message.IMessageSubscription;
  import io.nop.core.resource.component.ResourceComponentManager;
  import org.slf4j.Logger;                              // third-party 在中间
  import org.slf4j.LoggerFactory;

  import java.util.ArrayList;                           // java.* 在最后
  import java.util.Collection;
  ```
- **严重程度**: P3
- **现状**: 平台 `docs-for-ai/02-core-guides/code-style.md:17` 明确要求 `java.* -> jakarta.* -> third-party -> io.nop.*`。但本模块大量文件采用相反顺序（`io.nop.* -> third-party -> java.*`）。409 个手写 Java 文件中有 **122 个**（~30%）出现 `io.nop.*` import 出现在 `java.*` import 之前。
- **风险**: 跨模块阅读时增加心智负担；现成的 `check-import-order.sh` 因路径硬编码为 `nop-stream` 而不会检查本模块。
- **建议**: （a）扩展 `check-import-order.sh` 接受模块参数；（b）一次性 IDE 重排，纯格式变更、零行为风险。
- **信心水平**: 确定
- **误报排除**: 没有任何 `ai-dev/design/nop-ai-agent/` 文档声明本模块使用反向 import 顺序。
- **复核状态**: 未复核

---

### [维度17-3] 接口 `ActorRegistry` 未遵循本模块统一的 `I*` 前缀命名

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/ActorRegistry.java:27`
- **证据片段**:
  ```java
  public interface ActorRegistry {
      void register(AgentActor actor);
      void unregister(String actorId);
      Optional<AgentActor> get(String actorId);
      Optional<AgentActor> getBySession(String sessionId);
      Collection<AgentActor> getAll();
  }
  ```
- **严重程度**: P3
- **现状**: 本模块共 65 个接口，其中 64 个使用 `I` + PascalCase 前缀。**唯一例外**是 `ActorRegistry`——一个纯接口，缺少 `I` 前缀。`code-style.md:10` 明确要求"接口命名：`I` + PascalCase"。
- **风险**: 单点破坏模块内接口命名的一致性；新读者无法立即判断它是接口还是类。
- **建议**: 重命名为 `IActorRegistry`（破坏性变更，需同步更新引用点）。或若团队认为此名字是历史遗产，应在类级 Javadoc 显式标注。
- **信心水平**: 确定（统计 65 个接口仅此 1 个不带 I 前缀）
- **误报排除**: 同模块的 `IActorRuntime`/`IDaemonCoordinator`/`ISessionTakeoverLock` 等接口都遵守 `I*`，证明本模块的本地约定就是 `I*`。
- **复核状态**: 未复核

---

### 维度 17 检查项合规说明

- **命名规范（PascalCase/camelCase/UPPER_SNAKE_CASE）**: 抽样 10+ 文件，全部合规。
- **`I*` 接口前缀**: 65 个接口中 64 个合规。
- **包名**: 全部为 `io.nop.ai.agent.<subpackage>`。
- **行宽和缩进**: 4 空格缩进；全模块仅 5 行超 150 字符，0 行超 200 字符。
- **System.out/System.err**: 零命中。日志全部走 SLF4J。
- **不必要的 public 修饰**: 抽样检查合规。
- **过度注释**: 大量高密度注释是实质性的设计文档，不是模板化填充。

---

## 维度 19 发现

### [维度19-1] DB Table 常量类前缀分裂：`AiAgent*Table` vs `NopAi*Table`

- **文件**: 跨 5 个包的 11 个 Table 常量类
- **证据片段**:
  ```
  AiAgent* 前缀（9 个，本地 ORM / 纯 JDBC 表）:
    message/AiAgentMessageTable.java         -> ai_agent_message
    session/AiAgentSessionTable.java         -> ai_agent_session
    security/AiAgentDenialTable.java         -> ai_agent_denial
    reliability/AiAgentCheckpointTable.java  -> ai_agent_checkpoint
    team/AiAgentTeamTable.java               -> ai_agent_team
    team/AiAgentTeamMemberTable.java         -> ai_agent_team_member
    team/AiAgentTeamTaskTable.java           -> ai_agent_team_task
    runtime/coordination/AiAgentDaemonCoordTable.java -> ai_agent_daemon_coord
    runtime/lock/AiAgentSessionLockTable.java        -> ai_agent_session_lock

  NopAi* 前缀（2 个，镜像 nop-ai-dao 上游 ORM 实体）:
    session/NopAiSessionMessageTable.java    -> nop_ai_session_message
    usage/NopAiChatResponseTable.java        -> nop_ai_chat_response
  ```
- **严重程度**: P3
- **现状**: 同一模块内有两套 Table 常量类前缀约定。`NopAi*` 用于上游 `nop-ai-dao` 拥有的表，`AiAgent*` 用于本模块自有表。此约定仅以散落的 Javadoc 形式存在，没有文档化。
- **风险**: 维护者新增 Table 常量类时无明确规则可循；命名分组反映"表归属"信息，但这条隐式元数据没有显式文档载体。
- **建议**: 在 `ai-dev/design/nop-ai-agent/01-architecture-baseline.md` 或 `docs-for-ai/02-core-guides/code-style.md` 显式记录该约定。
- **信心水平**: 确定（11 个文件全部读取并归类）
- **误报排除**: 前缀差异有真实的语义（表归属），但语义没有被文档化。
- **复核状态**: 未复核

---

### [维度19-2] Table 列名大小写约定不一致：`AiAgent*` 用 UPPER_SNAKE，`NopAi*` 用 lower_snake

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/AiAgentSessionTable.java:26-37` vs `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/NopAiSessionMessageTable.java:28-47`
- **证据片段**:
  ```java
  // AiAgentSessionTable.java — UPPER_SNAKE_CASE 列名
  public static final String COL_SESSION_ID = "SESSION_ID";
  public static final String COL_AGENT_NAME = "AGENT_NAME";
  public static final String COL_STATUS = "STATUS";
  ...
  // NopAiSessionMessageTable.java — lower_snake_case 列名
  public static final String COL_ID = "id";
  public static final String COL_SESSION_ID = "session_id";
  public static final String COL_ROLE = "role";
  ...
  ```
- **严重程度**: P2
- **现状**: 同一模块的 11 个 Table 常量类中，列名常量值的大小写策略分裂为两套：9 个 `AiAgent*Table` 类用 UPPER_SNAKE_CASE；2 个 `NopAi*Table` 类用 lower_snake_case。最关键的相同概念列出现两种写法：`COL_TENANT_ID` 在 `AiAgent*` 里值是 `"TENANT_ID"`，在 `NopAi*` 里值是 `"tenant_id"`。
- **风险**: 维护者复制粘贴 SQL/列名引用时极易在两套大小写间出错（H2/MySQL 默认大小写不敏感可掩盖，PostgreSQL/Oracle 则会报错）。
- **建议**: 二选一统一：（a）全部对齐到 UPPER_SNAKE_CASE，或（b）显式文档化差异。
- **信心水平**: 确定（11 个 Table 类全部读取，列名常量值逐一比对）
- **误报排除**: 本地 ORM 模型完全由本模块拥有，两套选择都是本模块的主动决定，构成真实的内部不一致。
- **复核状态**: 未复核

---

### [维度19-3] 本地 ORM 实体命名偏离平台及同子系统 `nop-ai-dao` 的 `NopAi*` / `nop_*` 约定

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:29,61,93,119`
- **证据片段**:
  ```xml
  <!-- nop-ai-agent 本地 ORM -->
  <entity name="io.nop.ai.agent.message.AiAgentMessage" tableName="ai_agent_message" ...>
  <entity name="io.nop.ai.agent.security.AiAgentDenial" tableName="ai_agent_denial" ...>
  <entity name="io.nop.ai.agent.session.AiAgentSession" tableName="ai_agent_session" ...>
  <entity name="io.nop.ai.agent.reliability.AiAgentCheckpoint" tableName="ai_agent_checkpoint" ...>
  ```
  对照 `nop-ai-dao`：
  ```xml
  <entity className="io.nop.ai.dao.entity.NopAiProject" ... tableName="nop_ai_project" ...>
  <entity className="io.nop.ai.dao.entity.NopAiModel" ... tableName="nop_ai_model" ...>
  ```
- **严重程度**: P3
- **现状**: `code-style.md:44-47` 要求 ORM 实体命名为 `Nop{模块Pascal}{实体Pascal}`、表名 `nop_{模块}_{实体snake_case}`。同 AI 子系统的 `nop-ai-dao` 严格遵守。但 `nop-ai-agent` 的本地 ORM 三方面偏离：类名缺 `Nop` 前缀、包路径无 `dao.entity` 子包、表名缺 `nop_` 前缀。
- **风险**: 跨模块读者无法从实体名/表名一眼判断"这是标准 DAO 实体还是轻量 raw-JDBC 表"；未来若升级到完整 DAO/codegen 管线，需要全量改名。
- **建议**: 显式说明"本模块的 `AiAgent*` 实体刻意不走 `Nop*` / `nop_*` 平台约定"。
- **信心水平**: 很可能
- **误报排除**: 同 AI 子系统的 `nop-ai-dao` 已采用 `NopAi*` / `nop_ai_*`，证明该约定在 AI 子系统也有效。
- **复核状态**: 未复核

---

### [维度19-4] 内存模型与 ORM 实体名不对称：`AgentSession` vs `AiAgentSession`

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/AgentSession.java:12` vs `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:93`
- **证据片段**:
  ```java
  // AgentSession.java — 内存 POJO（无 Ai 前缀）
  public class AgentSession {
      private final String sessionId;
      private final String agentName;
  }
  ```
  ```xml
  <!-- app.orm.xml — ORM 实体名（有 Ai 前缀） -->
  <entity name="io.nop.ai.agent.session.AiAgentSession" tableName="ai_agent_session" ...>
  ```
- **严重程度**: P3
- **现状**: 同一概念在内存层叫 `AgentSession`，在 ORM/DB 层叫 `AiAgentSession`。`AiAgentSessionTable.java:6` 的 Javadoc 明确写 "Each row records the full state of a single `AgentSession`"。
- **风险**: 同包内 `AgentSession`、`AiAgentSession`、`AiAgentSessionTable`、`AiAgentSessionLockTable` 四个相关标识符共存，新读者需要时间理清。
- **建议**: 在 `AgentSession.java` 或 `app.orm.xml` 的注释中显式标注二者的对应关系。
- **信心水平**: 确定
- **误报排除**: Javadoc 明确说 Table 持久化的就是 `AgentSession` 的状态；这是同一概念在不同层的有意命名差异。
- **复核状态**: 未复核

---

### [维度19-5] 异常消息前缀约定仅 `team/flow/` 包使用 `nop.ai.team.flow.*:` 结构化前缀

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/TeamTaskFlowOrchestrator.java:577,583,588,729`、`MemberFanOutDispatcher.java`、`MemberExecOutcome.java` 等（共 10 个文件、33 处）；对照 `engine/`、`memory/`、`reliability/`、`security/`、`session/`、`skill/` 等包
- **证据片段**:
  ```java
  // team/flow/TeamTaskFlowOrchestrator.java — 结构化前缀（10 个文件统一）
  throw new NopAiAgentException("nop.ai.team.flow.null-team-id: teamId must not be null");
  throw new NopAiAgentException(
      "nop.ai.team.flow.no-tasks: team has no tasks to orchestrate: teamId=" + teamId);

  // 对照 memory/AdapterBackedAiMemoryStore.java — 自由英文，无前缀
  throw new NopAiAgentException("IStorageAdapter must not be null");

  // 对照 session/DBSessionStore.java — ClassName.method: 前缀（第三种风格）
  throw new NopAiAgentException("DBSessionStore.save: session must not be null");
  ```
- **严重程度**: P3
- **现状**: 全模块共 301 处 throw `NopAiAgentException(String)`。消息字符串格式约定不统一：`team/flow/` 包（10 文件 / 33 处）用 `nop.ai.team.flow.<kebab-key>:` 结构化前缀；其他包（~268 处）自由格式，至少 3 种风格混用。
- **风险**: 异常消息不可机器归类；新代码作者无规则可循，会延续自由英文风格。
- **建议**: 在 `NopAiAgentException.java` 的类级 Javadoc 中明文规定消息前缀约定，然后增量对齐其他包。
- **信心水平**: 确定（`grep '"nop\.ai\.'` 全模块仅命中 `team/flow/` 包的 33 处）
- **误报排除**: `team/flow/` 包在同一模块、同一异常类下引入了结构化前缀实践，证明可行；问题在于该实践未被模块内其他包采纳。
- **复核状态**: 未复核

---

### 维度 19 检查项合规说明

- **bean 名称前缀**: 维度 08 已确认 `ai-agent-tools:*` 前缀合规。
- **xmeta displayName**: 本模块无 xmeta，N/A。
- **ORM 列的 prop name vs code name**: 严格遵循约定。
- **status/state、type/kind 同义词混用**: 各枚举命名各有其语义边界，无同义词在同概念上混用。
- **"Team" / "Memory" 概念命名一致性**: 各包内一致。

## 维度复核结论

待独立复核子 agent 输出。

## 最终保留项

待复核完成后填写。

---

注：本文件合并了维度 17 和维度 19。
