# 维度 18（文档-代码一致性）+ 维度 20（跨模块契约一致性）— nop-ai-agent

## 维度 18 发现

### [维度18-1] `ai-agent-tools.beans.xml` 文档声称的 auto-collect 路径在 NopIoC 加载机制下不成立（独立证据确认 08-1）

- **文件**:
  - `ai-dev/design/nop-ai-agent/01-architecture-baseline.md:85`（设计声称）
  - `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/beans/ai-agent-tools.beans.xml:5-7,19-22,28-35`（被声称的注册文件）
  - `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/AppBeanContainerLoader.java:170-185, 254-284`（IoC 加载机制权威实现）
- **证据片段**:
  ```markdown
  # 01-architecture-baseline.md:85 文档原文
  三个工具均在 `ai-agent-tools.beans.xml` 注册为 bean，被 toolkit 的
  `<ioc:collect-beans by-type="...IToolExecutor"/>` 自动收集
  ```
  ```xml
  <!-- ai-agent-tools.beans.xml:5-7 注释声称 -->
  <!-- Engine-aware tool executors ... These beans are auto-collected by the existing <ioc:collect-beans by-type="...IToolExecutor"/> ... -->
  ```
  ```java
  // AppBeanContainerLoader.java:170-185 IoC 实际加载 autoconfig 的机制
  List<IResource> getAutoConfigResources() {
      Collection<? extends IResource> resources = VirtualFileSystem.instance()
              .getChildren(IocConstants.VFS_PATH_AUTOCONFIG);   // = "/nop/autoconfig"
      // ...只接受 name.endsWith(".beans") 的资源
  }
  ```
  ```
  # 现实：find nop-ai-agent/src/main/resources/_vfs/nop/autoconfig -type f → 无输出（目录不存在）
  # 对照：nop-ai-toolkit / nop-ai-core / nop-ai-tools 都有 autoconfig 入口
  ```
- **严重程度**: P1
- **现状**: 文档反复声称 11 个 IToolExecutor bean 经 toolkit 的 `<ioc:collect-beans>` 自动收集；但 `ai-agent-tools.beans.xml` 既不匹配 `app.beans.xml` 模式，又无 `/nop/autoconfig/*.beans` 入口文件，所以该 beans.xml 永远不被 NopIoC 加载。
- **风险**: 任何按文档假设部署 nop-ai-agent 的应用，会在运行期发现这些工具全部缺失；测试通过自建 IToolManager 路由绕开此问题。
- **建议**: 二选一：(a) 新增 `/nop/autoconfig/nop-ai-agent.beans` 入口；(b) 修正文档的"auto-collect"声称。
- **信心水平**: 确定
- **误报排除**: 已在 NopIoC 加载器源码直接看到 `/nop/autoconfig` 是唯一加载入口；并已验证三个对照模块都有 autoconfig 入口而 nop-ai-agent 没有。
- **复核状态**: 未复核

---

### [维度18-2] 5 个 Team 工具无对应 `.tool.xml` schema 文件，与 baseline §四声称不符

- **文件**:
  - `ai-dev/design/nop-ai-agent/01-architecture-baseline.md:85`（声称）
  - `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/beans/ai-agent-tools.beans.xml:23-26, 36`（注册的 team 工具）
- **证据片段**:
  ```xml
  <bean id="ai-agent-tools:team-send-message" class="io.nop.ai.agent.tool.TeamSendMessageExecutor"/>
  <bean id="ai-agent-tools:team-status" class="io.nop.ai.agent.tool.TeamStatusExecutor"/>
  <bean id="ai-agent-tools:team-task-create" class="io.nop.ai.agent.tool.TeamTaskCreateExecutor"/>
  <bean id="ai-agent-tools:team-task-update" class="io.nop.ai.agent.tool.TeamTaskUpdateExecutor"/>
  <bean id="ai-agent-tools:team-execute-flow" class="io.nop.ai.agent.tool.TeamExecuteFlowExecutor"/>
  ```
  ```
  # find 全仓库 -name "team-*.tool.xml"（排除 target） → 无输出（0 个）
  # 对照：call-agent.tool.xml / send-message.tool.xml / read-memory.tool.xml 等都存在
  ```
- **严重程度**: P2
- **现状**: baseline §四声称所有在 beans.xml 注册的工具均"经各自的 .tool.xml schema 文件加载"。其他 5 个非 team 工具都有对应 .tool.xml；唯独 5 个 team 工具没有任何 .tool.xml schema 文件存在。
- **风险**: 即使维度 18-1 的 autoconfig 缺口被修复，ToolManager 仍无法通过 tool name 路由到 team 工具——因为 ToolManager 通过 .tool.xml schema 才知道 name↔executor 的映射。
- **建议**: (a) 补齐 5 个 team 工具的 .tool.xml；(b) 修正文档把 team 工具从"经 .tool.xml 加载"分组中剔除。
- **信心水平**: 确定
- **误报排除**: 已用 `find -name "team*.tool.xml"` 全仓库扫描返回 0 个结果，对照组非 team 工具都能找到。
- **复核状态**: 未复核

---

### [维度18-3] 设计文档把 `IAgentMemory` 列为 Layer 1 核心对象/接口，但代码中无此 Java 类型

- **文件**:
  - `ai-dev/design/nop-ai-agent/01-architecture-baseline.md:64`（核心对象契约表）
  - `ai-dev/design/nop-ai-agent/glossary.md:38`（术语表）
- **证据片段**:
  ```markdown
  # 01-architecture-baseline.md:64
  | `IAgentMemory` | Agent 的三层记忆管理 | 短期记忆... Working Memory（`IAiMemoryStore`...）和长期记忆... |
  ```
  ```
  # find nop-ai-agent/src -name "IAgentMemory*" → 无输出
  # 实际存在的 memory 接口：IAiMemoryStore / IMemoryStoreProvider / IStorageAdapter / IEmbeddingAdapter / IVectorAdapter（分散在 5 个文件）
  ```
- **严重程度**: P2
- **现状**: 设计把 `IAgentMemory` 与 `IAgentEngine`/`IAgentExecutor` 等同列为 "Layer 1 核心对象" 并使用 `I` 前缀命名（暗示是 interface）。但代码中根本没有 `IAgentMemory` 这个 Java 类型——记忆能力被拆散到 5 个独立接口。
- **风险**: 文档读者会按"IAgentMemory 是 Layer 1 稳定接口"假设去定位实现、做 LSP lookup，发现找不到对应类型后产生困惑。
- **建议**: (a) 把 `IAgentMemory` 改为概念名（去除 `I` 前缀）；(b) 若确需统一接口，按设计落地 `IAgentMemory` Java 接口。
- **信心水平**: 确定
- **误报排除**: 已对 nop-ai-agent src/main 全目录做 grep 双重确认 0 命中；同时验证其他 `I*` 命名对象都有对应 Java 接口，仅 IAgentMemory 缺失。
- **复核状态**: 未复核

---

### [维度18-4] `IAgentEngine` 接口默认方法抛 `UnsupportedOperationException`，与设计"对不存在 sessionId 抛 NopAiAgentException"声称存在精度缺口

- **文件**:
  - `ai-dev/design/nop-ai-agent/01-architecture-baseline.md:58`（设计声称）
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/IAgentEngine.java:37-47, 75-77, 114-117, 185-188`
- **证据片段**:
  ```markdown
  # 设计声称
  三者对不存在的 sessionId 均 fail-fast 抛 `NopAiAgentException`
  ```
  ```java
  // IAgentEngine.java:37-47（接口默认实现）
  default CompletableFuture<String> forkSession(...) {
      throw new UnsupportedOperationException("forkSession requires Phase 2 ISessionStore");
  }
  ```
- **严重程度**: P3
- **现状**: 设计文档把 "fail-fast 抛 NopAiAgentException" 写成 IAgentEngine 的契约约束，但实际只有 `DefaultAgentEngine` 覆盖实现满足此约束；接口默认实现抛的是 `UnsupportedOperationException`。
- **风险**: 低。文档读者若按"接口契约"设计 catch NopAiAgentException 的代码，遇到非 DefaultAgentEngine 实现时会漏接 UOE。
- **建议**: 在 baseline §四的 IAgentEngine 行明确实现语义 vs 接口占位的差异。
- **信心水平**: 确定
- **误报排除**: 设计文档明确把 "fail-fast 抛 NopAiAgentException" 作为契约约束陈述。
- **复核状态**: 未复核

---

### [维度18-5] `docs-for-ai/04-reference/source-anchors.md` 完全无 nop-ai-agent 实现锚点

- **文件**:
  - `docs-for-ai/04-reference/source-anchors.md`（133 行实现锚点表）
  - `docs-for-ai/03-modules/nop-ai.md:39`
  - `docs-for-ai/INDEX.md:156`
- **证据片段**:
  ```
  # 在 source-anchors.md 全文 grep "nop-ai-agent\|IAgentEngine\|AgentModel\|ai/agent" → 0 命中
  ```
  ```markdown
  # 03-modules/nop-ai.md:39
  | `nop-ai-agent` | Agent 框架 |
  ```
- **严重程度**: P2
- **现状**: nop-ai-agent 是规模最大、设计文档最丰富的子系统之一（~50K 行 Java + 41 篇 design 文档），但在 `docs-for-ai/` 中几乎是隐形的：source-anchors.md 无任何 nop-ai-agent 锚点；nop-ai.md 用 4 个字概括。
- **风险**: 按 AGENTS.md "若 docs-for-ai 不足，优先 source-anchors 锚点做 LSP lookup" 的工作流，任何想用 nop-ai-agent 的开发者会找不到锚点。
- **建议**: 在 source-anchors.md 增加 nop-ai-agent 关键锚点段；在 03-modules/nop-ai.md 扩写 nop-ai-agent 一节。
- **信心水平**: 确定
- **误报排除**: source-anchors.md 已存在 TEST-004 指向 nop-ai-toolkit 测试，且 nop-ai-agent 的 design 文档已经历 11+ plans 迭代。
- **复核状态**: 未复核

---

### [维度18-6] `ai-dev/design/nop-ai-agent/README.md` 索引不全：12 篇较新设计文档未在 README 出现

- **文件**: `ai-dev/design/nop-ai-agent/README.md`（133 行索引页）
- **证据片段**:
  ```
  # README 索引 21 篇；目录实际有 41 篇。差集（12+ 篇未索引）：
  #   nop-ai-agent-async-team-task-orchestration.md
  #   nop-ai-agent-cross-process-daemon-coordination.md
  #   nop-ai-agent-daemon-dispatch-parity.md
  #   nop-ai-agent-member-auto-spawn.md
  #   nop-ai-agent-multi-member-routing.md
  #   nop-ai-agent-orchestrator-auto-spawn.md
  #   nop-ai-agent-task-flow-integration.md
  #   nop-ai-agent-task-scheduler-daemon.md
  #   nop-ai-agent-task-step-decorator.md
  #   nop-ai-agent-team-execute-flow.md
  #   nop-ai-agent-team-task-reclaim.md
  #   agent-survey.md
  #   nop-ai-agent-security-audit-readiness-analysis.md
  ```
- **严重程度**: P2
- **现状**: README 的分类共索引 21 篇文档；但目录实际有 41 篇。12+ 篇 plan 系列设计文档（team / task / daemon / orchestrator / spawn / multi-member routing 等核心 Layer 4 自动化能力）未在 README 出现。
- **风险**: 按 README 导航的读者会错过整个 Layer 4 team 编排栈的设计依据。
- **建议**: 在 README.md 增加 "## Layer 4 自动化栈" 段，索引 12 篇 team/task/daemon 文档。
- **信心水平**: 确定
- **误报排除**: README 自己声明"按需深入"列到第 15 项；同时索引的 nop-ai-shell-design.md 等却出现，缺失不规则。
- **复核状态**: 未复核

---

## 维度 20 发现

### [维度20-1] `nop-task-dao` 硬编码反向引用 `io.nop.ai.agent.engine.NopAiAgentException` FQCN（跨模块反向耦合）

- **文件**: `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/TaskExceptionRegistry.java:55-71`
- **证据片段**:
  ```java
  // TaskExceptionRegistry.java:55-71（构造期硬编码注册）
  public TaskExceptionRegistry() {
      // === reflective 注册（nop-ai 子类，部署可能缺类，lazy 反射解析）===
      registerReflective("io.nop.ai.agent.engine.NopAiAgentException");
      registerReflective("io.nop.ai.api.exceptions.NopAiException");
  }
  ```
- **严重程度**: P2
- **现状**: nop-task-dao 的 TaskExceptionRegistry 构造期通过 `registerReflective("io.nop.ai.agent.engine.NopAiAgentException")` 反向引用了 nop-ai-agent 模块的具体异常类 FQCN。这是模块依赖图的反方向耦合（nop-task-dao 不依赖 nop-ai-agent）。这是 nop-task-dao 中**唯一**对其他业务模块异常类的反向引用。
- **风险**: 中低。运行期有反射兜底，不会破坏功能；但破坏了模块边界单向原则，且是隐式耦合（grep FQCN 才能发现）。
- **建议**: (a) 改为 SPI/ServiceLoader 模式；(b) 把 nop-ai-agent 的注册移到 nop-ai-agent 自己启动时调用。
- **信心水平**: 确定
- **误报排除**: 虽然运行期不抛错，但代码层面 `nop-task-dao/main` 含有 `io.nop.ai.agent.engine.NopAiAgentException` 字面字符串，是确凿的跨模块字符串硬编码。
- **复核状态**: 未复核

---

### [维度20-2] `IMessageService` 实际定义在 `nop-kernel/nop-api-core`，与共享上下文 "依赖 nop-message-core 提供消息服务抽象" 描述存在精度缺口

- **文件**:
  - `_tmp/audit-nop-ai-agent-context.md:35`（共享上下文描述）
  - `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/message/IMessageService.java:13`（实际接口位置）
  - `nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:24`（实现位置）
- **证据片段**:
  ```java
  // IMessageService.java:13（接口实际位置 = nop-api-core 内核包）
  package io.nop.api.core.message;
  public interface IMessageService extends IMessageSender, IMessageSubscriber { }
  ```
- **严重程度**: P3
- **现状**: 共享上下文把 `IMessageService` 抽象归到 "nop-message-core"；实际上 `IMessageService` 接口定义在 `nop-kernel/nop-api-core`，nop-message-core 只提供 `LocalMessageService` 实现。
- **风险**: 低。审核口径与设计文档讨论时会因为"接口在哪里"产生无谓争议。
- **建议**: 修订共享上下文为 "nop-message-core（提供 LocalMessageService 等实现；接口 IMessageService 在 nop-api-core 内核）"。
- **信心水平**: 确定
- **误报排除**: Java 文件路径是确定性事实。
- **复核状态**: 未复核

---

### [维度20-3] 跨模块契约 — `IToolExecutor` 接口签名与 nop-ai-agent 全部 10 个实现完全一致（合规，无发现）

接口定义 `String getToolName()` + `CompletionStage<AiToolCallResult> executeAsync(AiToolCall, IToolExecuteContext)`。10 个实现全部 `implements IToolExecutor` 并正确 override。**合规**。

---

### [维度20-4] 跨模块契约 — `IChatService` / `ILlmDialect` 使用与 nop-ai-core / nop-ai-api 契约一致（合规，无发现）

nop-ai-agent 仅调用公开方法，未泄露 `ChatServiceImpl` 具体实现；对 `ILlmDialect` 仅消费 `estimateTokens()` 抽象方法。**合规**。

---

### [维度20-5] `MarkdownDslResourceLoaderFactory` 类路径契约一致；但 `nop-record-mapping` 的 test scope 跨模块契约破坏仍然存在（复核 10-01 类路径 + 独立确认 scope）

- **文件**:
  - `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/agent-plan.register-model.xml:8`
  - `nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/md/MarkdownDslResourceLoaderFactory.java:9`
  - `nop-ai/nop-ai-agent/pom.xml:46-49`
- **证据片段**:
  ```xml
  <!-- agent-plan.register-model.xml:7-9 -->
  <loader fileType="agent-plan.md" mappingName="agent-plan.Markdown_to_AgentPlanModel" optional="true"
          class="io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory"/>
  ```
  ```xml
  <!-- pom.xml:46-49（scope = test） -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-record-mapping</artifactId>
      <scope>test</scope>
  </dependency>
  ```
- **严重程度**: P1
- **现状**:
  - **类路径部分（合规）**：register-model 引用 `io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory`（下划线），与实际 Java 包完全匹配；全仓库 grep 无任何 `record-mapping.md.`（连字符）错误引用。
  - **scope 部分（破坏跨模块契约）**：`nop-ai-agent/pom.xml:46-49` 把 `nop-record-mapping` 声明为 `<scope>test</scope>`，但 main 资源 `agent-plan.register-model.xml` 通过 `class` 属性字符串引用了其 main 类。消费方应用引入 `nop-ai-agent` 后，classpath 中没有 `nop-record-mapping`，框架反射加载该类时触发 ClassNotFoundException，被 `optional="true"` 兜底为静默跳过，导致 `agent-plan.md` 文件类型支持在消费方静默失效。
- **风险**: 高。`agent-plan.md` 是设计文档反复声称的"plan DSL markdown 同构入口"，但消费方部署后此功能静默不可用，且无错误信号。
- **建议**: 修订 `nop-ai-agent/pom.xml:46-49` 把 `nop-record-mapping` 改为 compile scope，或显著标注 scope 约束。
- **信心水平**: 确定
- **误报排除**: 把"10-01 的连字符 bug"分解为两个独立结论：类路径部分合规、scope 部分破坏契约。
- **复核状态**: 未复核

---

### [维度20-6] 跨模块硬编码依赖 — nop-ai-agent 对 nop-ai-core 的具体类 import 集中在稳定的 model/constants/factory 类（合规，无发现）

7 处 import 全部为 stable public type（model/enum/constants/interface/factory），无任何具体 Provider 实现类泄露。**合规**。

---

### [维度20-7] 下游消费方 — 全仓库 0 个模块依赖 nop-ai-agent（合规，IAgentEngine 当前无下游影响面）

无任何业务或框架模块通过 `<dependency>` 声明对 nop-ai-agent 的编译期依赖。**合规**。

## 维度复核结论

待独立复核子 agent 输出。

## 最终保留项

待复核完成后填写。

---

注：本文件合并了维度 18 和维度 20。
