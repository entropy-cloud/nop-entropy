# G3: nop-ai 子系统深度审计（首轮）

- **审计日期**: 2026-09-30
- **审计范围**: `nop-ai/` 全部子模块（api/core/agent/toolkit/tools/skills/coder/rag/shell/gateway/maven/dsl-orm/dao/meta/service/web/app/codegen/mcp-server/spring-mcp-server*），手写 main 代码为主，dao/web 生成代码不审
- **负责维度**: 01（依赖图与模块边界）、07（BizModel 规范遵循）、09（错误处理与错误码）、13（安全与权限模型）、21（单元测试有效性）
- **方法**: 静态深读，live code 为准；机械基线逐条核实（见下）

## 审计范围（含零发现维度说明）

### 机械基线核实结论

| 基线 | 核实结果 |
|------|---------|
| 裸异常 55 处 | main 代码仅 11 处 `throw new UnsupportedOperationException/IllegalArgumentException`：其中 10 处为接口 default 方法 / NoOp 桩的 UOE（`IHookRegistry`、`IToolExecuteContext`、`ILlmDialect`、`NoOpEmbeddingAdapter`、`NoOpActorRuntime`、`NoOpAgentMessenger`、`ExternalCommandAdapter`、`PlanReplanner`），javadoc 明示为"Minimum Rules #24 — No Silent No-Op"的文档化设计（fail-loud 桩）；1 处 `FailoverStreamFlow.java:187` 的 IAE 是 Reactive Streams 规范 3.9 强制要求（代码注释已豁免）。均不构成发现。其余 44 处全部在 test 代码（不报）。 |
| System.out/printStackTrace 55 处 | **全部 55 处在 test 代码**（`grep ... \| grep '/main/' | wc -l` = 0），main 代码零命中。不报。 |
| @Inject private 全仓 0 处 | nop-ai 内确认零命中。不报。 |

### 深读覆盖的关键文件（节选）

- **维度 01**：19+2 个子模块 pom.xml 全量提取内部依赖（见依赖图）；`nop-ai-service`/`nop-ai-mcp-server`/`nop-ai-rag`/`nop-ai-gateway` 依赖用途逐个回溯到 import。
- **维度 07**：全部 50 个手写 `@BizModel` 类清单化；深读 `ChannelLoginApiBizModel`、`NopAiRagBizModel`、`FileToolBizModel`、`AiFileTool`、`SequentialThinkingBizModel`、`NopAiModelBizModel`；xbiz/xmeta（44 xbiz = 22 实体 × 基+保留）核对。
- **维度 09**：`ReActAgentExecutor`、`DefaultAgentEngine`、`AgentSessionLifecycle`、`AgentToolDispatcher`、`AgentHookInvoker` 的全部 catch 块逐个判级；dialect 层（Ollama/Responses）catch-ignore 逐个看注释（均为"容忍模型畸形 JSON"文档化设计）。
- **维度 13**：`SsrfAddressGuard`/`SsrfGuardDnsResolver`/`HttpRequestExecutor`/`GraphqlQueryExecutor`（SSRF 双层防护）、`BashExecutor` + `HostBashSandbox`/`DockerBashSandbox`/`BashSandboxPaths`（沙箱 jail）、`ChannelLoginScanProcessor`（扫码登录 P0 加固）、`LocalFileOperator`/`LocalToolFileSystem`/`AiFileTool.getResource`（路径 jail）、`SkillExecutor`、`FailoverStreamFlow`（流式 failover）。
- **维度 21**：630 个测试文件反模式扫描（P-1~P-8 清单），抽读 `TestSkillModel`、`TestTeamSpec`、`DefaultShellExecutionContextTest`、`TestThoughtStorage`、`TestLlmConfigHelper`、`SkillExecutorVfsTest`、`TestK3PipelineE2E`。

### 依赖图（维度 01 实测，compile scope 除非标注）

```
nop-ai-api        → nop-api-core
nop-ai-core       → nop-ai-api, nop-http-api, nop-xlang, nop-rule-core, nop-search-api (+optional nop-http-client-jdk)
nop-ai-toolkit    → nop-ai-api, nop-xlang, nop-http-api, nop-search-api, nop-diff, nop-jq
nop-ai-tools      → nop-ai-core, nop-converter, nop-graphql-core, nop-biz
nop-ai-agent      → nop-ai-toolkit, nop-ai-core, nop-task-core (+test: nop-dao, nop-message-core, nop-record-mapping)
nop-ai-rag        → nop-ai-core
nop-ai-shell      → nop-ai-toolkit
nop-ai-coder      → nop-ai-core, nop-ai-code-analyzer, nop-task-core, nop-orm-model, nop-dao, nop-rpc-model,
                    nop-report-core, nop-converter, nop-image, nop-markdown, nop-ooxml-markdown
nop-ai-gateway    → nop-gateway, nop-ai-api, nop-ai-core, nop-ai-agent, nop-ai-dao, nop-integration-api,
                    nop-integration-feishu, nop-biz-auth-core, nop-auth-api (+test: nop-auth-dao, nop-message-core)
nop-ai-mcp-server → nop-ai-coder                          ← 见 [G3-01-02]
nop-ai-dao        → nop-api-core, nop-orm (+test nop-ai-codegen)
nop-ai-meta       → (+test nop-ai-codegen, nop-ai-dao)
nop-ai-service    → nop-ai-dao, nop-ai-api, nop-ai-core, nop-ai-meta, nop-credential-api, nop-biz,
                    nop-biz-file-core, nop-config, nop-ioc, nop-sys-dao               ← 见 [G3-01-01]
nop-ai-web        → nop-ai-meta, nop-ai-service, nop-web
nop-ai-app        → nop-quarkus-web-orm-starter, nop-ai-service, nop-ai-web, nop-auth-web, nop-auth-service, ...
```

分层规则核对：无循环依赖；无 api→实现层反向边；core 不依赖 dao（agent 的 nop-dao 为 test scope，与 module-groups.md 声明一致）；gateway 的 agent/dao/auth-api/biz-auth-core 边与 `docs-for-ai/03-modules/nop-ai-gateway.md` 连带依赖清单一一对应；`nop-ai-agent` 的 store 层不依赖 nop-ai-dao（AI-2 裁定遵守）。整体分层健康，仅 2 条 P3 边界瑕疵（见发现）。

## 发现

### [G3-13-01] SkillExecutor skillName 未校验即拼接 VFS 路径，`..` 可逃逸 /nop/skills 沙箱

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/SkillExecutor.java:85-100`
- **证据片段**:
  ```java
  private AiToolCallResult handleLoad(AiToolCall call, IToolExecuteContext context, String skillName) {
      if (skillName == null || skillName.isEmpty()) {
          return AiToolCallResult.errorResult(call.getId(), "skillName is required for load action");
      }

      IResource skillDir;
      try {
          skillDir = VirtualFileSystem.instance().getResource("/nop/skills/" + skillName);
      } catch (Exception e) {
  ```
  以及同文件 `loadSkillContent`/`readDescriptionFile`（127-176 行）对返回目录做 `getChildren` 列目录 + 读取 `README.txt/README.md/SKILL.md/skill.md/description.txt` 内容。
- **严重程度**: P2
- **现状**: LLM 可控的 `skillName` 只做 null/空检查，直接拼接 `"/nop/skills/" + skillName` 后交给 VFS。`DeltaResourceStore.getResource` 经 `ResourceHelper.getStdPath` → `StringHelper.normalizePath`（nop-kernel/nop-commons `StringHelper.java:2622-2680`）会解析并消除 `..` 段，因此 `skillName="../beans"` 实际解析为 `/nop/beans`。
- **风险**: 越界后可对任意 VFS 目录做文件名枚举（`getChildren` 全量列出），并读取任意 VFS 目录下名为 README.txt/README.md/SKILL.md/skill.md/description.txt 的文件内容（截断 4000 字符）。VFS 含各模块 beans 配置、模型文件等内部资源；被提示注入操控的 agent 可借此侦察部署结构。对比同层防护：`AiFileTool.getResource`（mcp-server）显式 fail-closed `ERR_MCP_PATH_ESCAPE`、`LocalToolFileSystem` 有 canonical jail，本工具缺同等级校验。
- **建议**: 在 `handleLoad` 中对 `skillName` 做 fail-closed 校验（对齐 `FileToolBizModel.getProjectDir` 的 M6-P1 模式）：拒绝包含 `/`、`\`、`..`、空段、`:` 的输入，仅允许单一安全目录名；并补 `../` 穿越（期望 errorResult）与合法名的回归测试（现有 `SkillExecutorVfsTest` 只覆盖 list/load/unknown）。
- **信心水平**: 确定（normalizePath 的 `..` 消解逻辑与 `DeltaResourceStore.getStdPath` 调用链均已读源码核实）
- **误报排除**: 不是"VFS 天然安全"的误报——已核实 VFS 路径规范化会解析 `..` 到父目录而非拒绝；也不是生成代码——SkillExecutor 为手写 main 代码。
- **复核状态**: 未复核

### [G3-13-02] BashExecutor 危险环境变量黑名单中 `BASH_FUNC_` 为死条目，精确匹配永远无法命中前缀型攻击变量名

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/BashExecutor.java:44-49, 209-217`
- **证据片段**:
  ```java
  private static final Set<String> DANGEROUS_ENV_VARS = Set.of(
          "LD_PRELOAD", "LD_LIBRARY_PATH", "LD_DEBUG", "LD_AUDIT",
          "SHELLOPTS", "BASH_ENV", "BASH_FUNC_",
          "IFS", "PATH", "PYTHONPATH", "PERLLIB",
          "PERL5LIB", "RUBYLIB", "DYLD_INSERT_LIBRARIES"
  );
  ...
  for (XNode envNode : envNodes) {
      String name = envNode.attrText("name");
      String value = envNode.attrText("value");
      if (name != null && value != null) {
          String upperName = name.toUpperCase();
          if (DANGEROUS_ENV_VARS.contains(upperName)) {   // Set.contains = 精确匹配
  ```
- **严重程度**: P2
- **现状**: `BASH_FUNC_` 是前缀型条目（bash 导出函数的环境变量形如 `BASH_FUNC_foo%%`），但校验用的是 `Set.contains(upperName)` 精确匹配，只有变量名恰好等于字面量 `BASH_FUNC_` 才会被拒。攻击形态 `BASH_FUNC_foo%%` 不会命中任何条目，直接进入 `env.put(name, value)` 下传给沙箱后端。
- **风险**: 通过 env 注入 bash 导出函数（Shellshock 式 `BASH_FUNC_x%%=() {...}`）的向量未被拦截。属 defense-in-depth 层失效（主控制是沙箱隔离；`DockerBashSandbox` 的 `ENV_KEY_PATTERN` 也只校验字符集、不拦 `%%` 后缀），但该黑名单条目的存在表明拦截意图明确，现为死代码。
- **建议**: 前缀型条目改用 `upperName.startsWith("BASH_FUNC_")`（或把 `BASH_FUNC_` 移出 Set 单独前缀判断）；补一条 `BASH_FUNC_foo%%` 被拒绝的回归测试（现有 `BashExecutorTest` 未覆盖前缀形态）。
- **信心水平**: 确定
- **误报排除**: 不是"黑名单本来就该绕过"的设计豁免——其余 13 个条目都是精确名且生效，仅此条目的语义（前缀）与实现（精确）不匹配，属实现与意图脱节。
- **复核状态**: 未复核

### [G3-09-01] ReActAgentExecutor.execute 终态 catch 丢弃 throwable 无任何日志，agent 失败无堆栈可查

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:484-495`
- **证据片段**:
  ```java
  } catch (Exception e) {
      if (ctx.isCancelRequested()) {
          Thread.currentThread().interrupt();
          handleCancellation(ctx, sessionId, agentName);
      } else {
          ctx.setStatus(AgentExecStatus.failed);
          ctx.setLastError(e.toString());

          hookInvoker.invokeOnError(ctx, agentName);
          hookInvoker.publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName, e.toString());
      }
  }

  return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
  ```
- **严重程度**: P2
- **现状**: 引擎边界 catch（按 `docs-for-ai/02-core-guides/error-handling.md` 属允许 catch 的系统边界）把异常转为 failed 结果，但既不 rethrow 也没有 `LOG.error("...", e)`——只把 `e.toString()` 写入 ctx 与事件。而 `AgentHookInvoker.publishErrorEvent`（238-243 行）仅在 `eventPublisher != null` 时发布事件，不落日志。
- **风险**: 违反 error-handling.md「丢弃异常前必须留证：rethrow with cause 或 LOG.info 及以上（throwable 作为末参数）」。堆栈全程丢失：无事件发布器的嵌入式部署中（eventPublisher 为 null 是合法装配形态）失败完全不可见；有事件发布器时运维日志也只有事件消费侧的 `e.toString()`，无法定位根因。对照同文件族的规范写法：`AgentSessionLifecycle.restorePendingSessions`（959-964 行）per-element 隔离 catch 正确使用 `LOG.warn("...", sessionId, t)`。
- **建议**: 在 else 分支补 `LOG.error("Agent execution failed: session={}, agent={}", sessionId, agentName, e)`（throwable 作末参数），再走现有 ctx/event 路径。`handleCancellation` 路径同理核查。
- **信心水平**: 确定
- **误报排除**: 不是"边界 catch 转结果即合规"的误报——两档策略明确要求丢弃前留证，且 `e.toString()` 丢 stack trace；也不是已收敛问题，live code 无任何 LOG 调用。
- **复核状态**: 未复核

### [G3-07-01] NopAiRagBizModel 使用全仓唯一的 `/` 前缀 bizObjName 且三个自定义 action 均无 @Auth

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/biz/NopAiRagBizModel.java:24-67`
- **证据片段**:
  ```java
  @BizModel("/NopAiRag")
  public class NopAiRagBizModel {
      ...
      @BizMutation
      public Map<String, Object> ingestDocument(
              @Name("indexId") String indexId,
              @Name("docId") String docId,
              @Name("content") String content) {
          int chunkCount = ingestService.ingest(indexId, docId, content);
          return Map.of("docId", docId, "chunkCount", chunkCount);
      }

      @BizQuery
      public List<Map<String, Object>> search(...) { ... }

      @BizQuery
      public String synthesize(...) { ... }
  }
  ```
- **严重程度**: P2
- **现状**: 四项偏差叠加：(1) `"/NopAiRag"` 是全仓唯一 `/` 前缀 bizObjName（`grep -r '@BizModel("/'` 全仓仅此一处），框架无任何 `/` 前缀语义处理（`BizModel` 注解 javadoc：对应 GraphQL type 名称），`GraphQLNameHelper.getOperationName` 生成的 `/NopAiRag__ingestDocument` 不是合法 GraphQL Name（`/` 不在 `[_A-Za-z][_0-9A-Za-z]*` 字符集），GraphQL 面不可达或污染 schema 序列化；(2) 三个自定义 action（含写操作 `ingestDocument`）均无 `@Auth(permissions=...)`，偏离 nop-ai.md 登记的自定义方法面基线（MR2 起 `FileTool`/`SequentialThinking`/`AiTool` 全部落了 `@Auth`）；(3) 无 xmeta、无 I*Biz 接口、无 `IServiceContext` 末参（偏离 service-layer.md 契约）；(4) `ingestDocument`/`search` 返回 `Map<String, Object>`/`List<Map<...>>` 而非 `@DataBean`。
- **风险**: bizObjName 的合法性未在任何测试中覆盖（`TestK3PipelineE2E` 直接 new 构造调用，不走 GraphQL/RPC 管道）——若引擎 schema 生成不容忍非法名则引入即炸，若容忍则是 RPC-only 的隐性约定但无文档；`ingestDocument` 对任何已认证调用者开放 RAG 索引写入（检索结果投毒面），`synthesize` 开放 LLM 调用（成本面），与同模块族其它工具 BizModel 的权限基线不一致。
- **建议**: 去掉 `/` 前缀改为 `NopAiRag` 并补 xmeta（或显式登记 RPC-only 约定）；三个 action 补 `@Auth(permissions = "NopAiRag:...")` 对齐基线；补一条经 GraphQL/RPC 管道的暴露路径测试。
- **信心水平**: 很可能（无 @Auth、命名唯一性、无 xmeta、无管道测试均为确定事实；GraphQL 面具体失败形态未运行验证）
- **误报排除**: 不是"编排级 BizModel 可无实体"的豁免场景——service-layer.md 允许编排入口 BizModel 但"仍需有 xmeta"；ChannelLoginApi 等同样无 xmeta 的 BizModel 是有文档登记 + 管道测试的显式契约，本类两者皆无。
- **复核状态**: 未复核

### [G3-01-01] nop-ai-service 声明 nop-sys-dao compile 依赖但 main 代码零使用

- **文件**: `nop-ai/nop-ai-service/pom.xml`（dependencies 段）
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-sys-dao</artifactId>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-autotest-junit</artifactId>
  ```
- **严重程度**: P3
- **现状**: `grep -rn "io.nop.sys" nop-ai-service/src/main/java` 零命中；全 src 树唯一出现处是一条测试注释（`TestAiModelCredentialResolver.java:93` 解释如何避免拉起 SysDictLoader）。该依赖无任何 main 代码消费。
- **风险**: 业务模块间无谓的 compile 耦合：引入 nop-ai-service 的部署被动携带 nop-sys 全部 ORM 实体与表（运行时装载面、DB migration 面扩大），且掩盖了"sys 能力是否真的被消费"的判断；未来有人顺手 import io.nop.sys 时无构建信号。
- **建议**: 删除该依赖；若确需 sys 表由 app 装配层（nop-ai-app）显式引入。若保留需在 pom 注释登记用途。
- **信心水平**: 确定（grep 双口径核实：main import 零命中 + resources 零引用）
- **误报排除**: 不属于误报校准中"多模块必要的传递依赖显式声明"——该依赖不是传递声明而是无人消费的直接声明；也不属于平台核心包豁免名单（nop-sys-dao 是业务模块）。
- **复核状态**: 未复核

### [G3-01-02] nop-ai-mcp-server 依赖 nop-ai-coder 全量重量链，实际只消费两个 Simplifier 类

- **文件**: `nop-ai/nop-ai-mcp-server/pom.xml`；`nop-ai/nop-ai-mcp-server/src/main/java/io/nop/ai/mcp/server/AiFileTool.java:5-6`
- **证据片段**:
  ```java
  import io.nop.ai.coder.simplifier.JsonSimplifier;
  import io.nop.ai.coder.simplifier.XNodeSimplifier;
  ```
  ```xml
  <!-- nop-ai-mcp-server/pom.xml -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-ai-coder</artifactId>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: mcp-server 对 nop-ai-coder 的 main 代码消费仅为 `JsonSimplifier`/`XNodeSimplifier` 两个轻量类，但 coder 会传递引入 nop-dao、nop-report-core、nop-ooxml-markdown、nop-image、nop-rpc-model、nop-task-core 等整条重量链。module-groups.md 给 nop-ai-coder 的定位是"AI 编程助手"重型模块，MCP 集成模块则声明"独立发布周期"。
- **风险**: 与 M5-P1 裁定 A 的先例（nop-ai-tools 为解除 `tools→coder` 分层倒置，把 DslToolImpl 下沉、 expressly "不连带 coder 重量依赖链"）方向相悖：MCP server 部署被迫携带整条 coder 链，发布周期被 coder 绑架，独立发布声明名存实亡。
- **建议**: 仿 M5-P1 先例，把两个 Simplifier 下沉到 mcp-server 自身或更底层模块（如 nop-ai-core），解除 mcp-server→coder 边。
- **信心水平**: 很可能（依赖用途与 coder 传递链均核实；是否接受该重量为有意的部署取舍需维护者裁定，故不升级）
- **误报排除**: 不是"Maven 最佳实践的显式直接依赖声明"——问题恰是该直接依赖引入的传递面远超实际消费面，且有同仓先例裁定反向方向。
- **复核状态**: 未复核

### [G3-07-02] AiFileTool 三处 `@18n:` 前缀拼写错误，i18n 解析不生效、原文直透 MCP 客户端

- **文件**: `nop-ai/nop-ai-mcp-server/src/main/java/io/nop/ai/mcp/server/AiFileTool.java:57,69,119`
- **证据片段**:
  ```java
  @Description("@18n:ai.get-nop-file-xdef|加载Nop文件的XDef元模型\n")
  @BizQuery
  @Auth(permissions = "AiTool:read")
  public String loadNopFileXDef(@Name("fileType") String fileType) { ... }

  @Description("@18n:ai.load-nop-file|加载Nop文件\n")
  @BizQuery
  @Auth(permissions = "AiTool:read")
  public String loadNopFile(@Name("path") String path, ...)
  ```
- **严重程度**: P3
- **现状**: 正确的前缀是 `@i18n:`（同仓对照：`NopAiModelBizModel.java:148` `@Description("@i18n:biz.delete|根据主键删除指定对象")`、nop-auth 多处）。`@18n` 不是注册的 value resolver 前缀，三个方法的描述不会被 i18n 机制解析，带错误前缀的字面文本原样进入 MCP tool description，最终展示给 MCP 客户端 / LLM。
- **风险**: MCP 工具描述携带噪声 token（`@18n:` 前缀 + 未解析的 key 形态），轻微干扰 LLM 工具选择；i18n 意图（多语言描述）静默失效，无任何报错。
- **建议**: 三处改为 `@i18n:`；同时在 i18n 资源文件补 `ai.get-nop-file-xdef`/`ai.load-nop-file`/`ai.save-nop-file` key（若尚未有）。
- **信心水平**: 确定
- **误报排除**: 不是风格偏好——`@18n` 与 `@i18n` 是 resolver 注册名的功能差异，有同仓正确用法对照，属实际生效行为缺陷（仅影响面小故 P3）。
- **复核状态**: 未复核

### [G3-21-01] 枚举计数镜像测试：改枚举即改测试，无独立验证意义（命中 P-1 变体）

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/skill/TestSkillModel.java:61-70`；`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/TestTeamSpec.java:122-134`
- **证据片段**:
  ```java
  @Test
  void topPatternEnumHasPhase1Values() {
      // Design §4.1: PREPARE | ACT | VERIFY | MANAGE | RETRIEVE | TRANSFORM
      assertEquals(6, SkillTopPattern.values().length);
  }

  @Test
  void resourceScopeEnumHasPhase1Values() {
      // Design §4.1: MEMORY | LOCAL_FS | CODEBASE | NETWORK | CREDENTIALS
      assertEquals(5, SkillResourceScope.values().length);
  }
  ```
  ```java
  @Test
  void teamStatusAndMemberRoleEnumsAreStable() {
      // Sanity: the enum values used by the contract exist as documented.
      assertEquals(2, MemberRole.values().length);
      assertSame(MemberRole.LEAD, MemberRole.valueOf("LEAD"));
      ...
      assertEquals(3, TeamStatus.values().length);
  ```
- **严重程度**: P3
- **现状**: 命中 `ai-dev/skills/unit-test-antipatterns.md` P-1 明文反模式（"测试枚举值数量：改了枚举就改测试，没有独立验证意义"；`valueOf` 往返亦只是 Java 语言语义）。核心判据不满足：把枚举语义改错（如删错一个值）测试会失败，但任何**合法**的枚举演进（新增值）同样失败——测试镜像实现常量而非行为。
- **风险**: 保护力为零的同时增加维护摩擦：每次按设计演进枚举都要同步改测试数字。注：同文件其余测试（`collectResourceScope`、`copyTagsReturnsMutableCopy` 等）是高质量行为测试，此 3 个方法是局部污点。
- **建议**: 删除这 3 个方法；若枚举集合确是跨模块契约需 drift sentinel，应断言具名成员存在（`assertSame(SkillTopPattern.PREPARE, ...)`）而非计数，并在注释登记契约方。
- **信心水平**: 确定
- **误报排除**: 不是 P-3/P-5 误报（这些测试有具体 assertEquals 而非 assertNotNull-only）；也承认 DBSessionStore 系列测试里的 `values().length` 是"每状态建一个会话再计数"的真实行为断言（用途正当），本条只针对纯计数镜像。
- **复核状态**: 未复核

## 维度复核结论

（首轮初审，待主 agent 派发复核后追加）

## 最终保留项

（待复核后填充）

---

### 审计补充说明（供主 agent 汇总）

1. **核对过但判定不报的项**（防复核重复劳动）：
   - SSRF 防护（`SsrfAddressGuard` + `SsrfGuardDnsResolver` 双层）实现完整：userinfo 旁路不存在（`URI.getHost()` 已剥离）、IPv6 字面量/IPv4-mapped/十进制十六进制记法均经 `InetAddress.getByName` 本地规范化、多答案集 rebinding 逐地址校验、fail-closed。JDK/OkHttp client 的 redirect-hop 级防护缺口为 owner 文档已登记的已知限制（nop-ai.md「SSRF 解析时防护」节），不重复报告。
   - 扫码登录（`ChannelLoginScanProcessor`）已有 P0 加固：ticket owner 服务端身份断言 + extId 绑定归属交叉校验 + fail-closed，身份伪造向量已闭合；MFA 错误码字符串契约有 drift sentinel 测试登记。
   - Bash 沙箱 fail-closed 默认、`BashSandboxPaths` null workDir fail-closed（F-AI2-1 已修）、Docker argv 无 shell 拼接、`-v` 挂载经 allowedBaseDirs jail。
   - `FailoverStreamFlow` 并发跳过递归受 router `attempted` 集合单调增长约束（池有限 → 饱和 fail-loud），非无限递归；预算语义（initial + budget）与文档一致。
   - dialect 层 catch-ignore（畸形 arguments JSON 留空）为注释登记的设计容忍，调用方有后续处理。
   - CRUD BizModel（22 个 `NopAiXxxBizModel`）继承 `CrudBizModel<T>` + `setEntityName` 模式规范；`NopAiModelBizModel` 的凭证引用计数（bind/换绑/解绑 + 三删除动作注销）与 nop-ai.md 登记语义一致。
2. **P0/P1**: 本轮零条。nop-ai 子系统的高风险面（工具执行层、沙箱、SSRF、扫码登录、流式 failover）此前已历多轮加固，本轮发现集中在遗留死角（skill 工具路径校验、env 黑名单死条目）与规范漂移。

## 子项复核结论

复核人：独立复核代理 R3（2026-09-30）

| 发现编号 | 判定 | 复核说明 |
|---|---|---|
| [G3-13-01] | 驳回 | 原发现的核心机制断言与 live code 不符。实测调用链：`SkillExecutor.java:92` → `DefaultVirtualFileSystem.getResource`（nop-kernel/nop-core `DefaultVirtualFileSystem.java:145-168`）在进入 DeltaResourceStore 之前即执行 `ResourceHelper.checkNormalVirtualPath(path)`（line 165），而 `isNormalVirtualPath`（`ResourceHelper.java:317-331`）→ `StringHelper.isCanonicalFilePath`（`StringHelper.java:2484-2508`）**显式拒绝** `../`、`/..`、整段 `..`、`/./`，含 `:` 的路径走 namespace 分支同样抛 `ERR_RESOURCE_INVALID_PATH/UNKNOWN_NAMESPACE`；`DeltaResourceStore.getResource`（`DeltaResourceStore.java:121-122`）入口另有 `Guard.checkArgument(isNormalVirtualPath)`。即 `skillName="../beans"` 的实际结果是 fail-closed 的 errorResult，而非原发现断言的"实际解析为 /nop/beans"。原发现误把 `getStdPath→normalizePath`（确实消解 `..`，`StringHelper.java:2622-2682`）归入 getResource 链——该归一化实际只存在于 `DeltaResourceStore.getChildren`（`DeltaResourceStore.java:164`），且 SkillExecutor 传入 getChildren 的路径来自已通过 canonical 校验的 resource。由于拼接前缀固定为 `/nop/skills/`，canonical 路径无法离开该 jail，所称的任意 VFS 目录枚举/读取风险不存在。`SkillExecutorVfsTest` 确无 `../` 向量（已核对），但补该测试只会钉死一个已 fail-closed 的行为，不构成 P2 缺陷。 |
| [G3-13-02] | 保留（维持 P2）| 逐行核实 `BashExecutor.java:44-49`（`DANGEROUS_ENV_VARS` 含 `"BASH_FUNC_"`）与 `:209-226`（`DANGEROUS_ENV_VARS.contains(upperName)` 精确匹配）：`BASH_FUNC_foo%%` 永不命中任一条目，死条目属实，实现与拦截意图脱节的定性成立。一处细节修正：原发现括注"DockerBashSandbox 的 ENV_KEY_PATTERN 也只校验字符集、不拦 %% 后缀"不准确——实测 `DockerBashSandbox.java:47` 的 `^[A-Za-z_][A-Za-z0-9_]*$` 会**拒绝**含 `%` 的键名，Docker 路径的次级防护实际有效，残余暴露面集中在显式 opt-in 的 `HostBashSandbox` 路径。核心判定不受影响：安全黑名单死条目属真实局部缺陷，修复成本一行，维持 P2。 |
| [G3-09-01] | 保留（维持 P2）| 全部证据核实：`ReActAgentExecutor.java:484-495` 的终态 catch 原文与报告一致，分支内仅 `ctx.setLastError(e.toString())` + hook 调用，无任何 LOG 调用且不 rethrow；`AgentHookInvoker.publishErrorEvent`（`AgentHookInvoker.java:238-243`）在 `eventPublisher == null` 时静默 no-op；调用侧 `DefaultAgentEngine.java:878` 对返回的 completed future `join()`，异常已在本类内被吞，上游无补日志点——堆栈确实全程丢失。规范依据复核：`docs-for-ai/02-core-guides/error-handling.md:30-35`「丢弃异常前必须留证」明确要求 LOG.info 及以上且 throwable 作末参；对照正例 `AgentSessionLifecycle.java:960-963` 的 `LOG.warn(..., sessionId, t)` 在位。维持 P2。 |
| [G3-07-01] | 保留（维持 P2）| 四项偏差全部独立核实：(1) `@BizModel("/NopAiRag")` 为全仓唯一 `/` 前缀（repo 级 grep 仅此一处），`ReflectionBizModelBuilder.java:105-115` 对 bizObjName 只做非空校验、无合法性检查，`GraphQLNameHelper.getOperationName`（`GraphQLNameHelper.java:23-28`）产出 `/NopAiRag__xxx` 形态，`/` 不在 GraphQL Name 字符集；(2) `NopAiRagBizModel.java:24-67` 三个 action（含写操作 `ingestDocument`）均无 @Auth，repo 级扫描确认其为 nop-ai 手写工具/编排类 BizModel 中唯一无 @Auth 者，同族基线核实：`FileToolBizModel.java:58-181`、`SequentialThinkingBizModel.java:50-111` 每个 action 均 `@Auth(permissions=...)`；(3) `GraphQLActionAuthChecker.isAllowAccess`（`GraphQLActionAuthChecker.java:118-120`）对 `auth == null` 返回 true，无 @Auth 即放开 action 级鉴权；(4) nop-ai-rag 无任何 xmeta/xbiz 文件，bean 经 `rag-defaults.beans.xml:9-11` 无条件注册，`TestK3PipelineE2E.java:34` 直接 new 构造、无管道测试。RAG 索引写入对任意已认证调用者开放（检索投毒面），维持 P2。 |

### 复核中发现的附带线索

- `SkillExecutor.handleLoad` 的 `skillName` 仍可考虑按原发现建议做输入白名单（拒绝 `/`、`\`、`..`、`:`）作为纵深加固并补 `../` 拒绝的钉死测试（P3 级，非缺陷——VFS canonical 校验已 fail-closed，见 [G3-13-01] 驳回说明）。原发现中的建议若采纳，应表述为加固而非漏洞修复。
