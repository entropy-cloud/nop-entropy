# nop-ai 模块结构审计——模块划分、分层与内部结构实证评估

> Status: resolved
> Date: 2026-10-11
> Scope: `nop-ai/` 全部 21 个 Maven 子模块（main 代码约 161k 行 / 约 1,046 个 java 文件）
> Conclusion: 分层骨架（api → core/toolkit → agent/coder/rag/… → gateway/web/app）方向正确、无编译期循环依赖与分层倒置，**不需要推倒式重构**；但存在四类结构性债务需要分阶段收敛——①废弃 chat 栈迁移半途（新旧 API 并存 8 个月）、②nop-ai-core 沦为堆场（混装 4 类异质关注点）、③agent 引擎装配面蔓延（48 参构造 + 51 字段配置 × 4 处重复暴露）、④微模块碎片与孤儿模块（3 个零消费者模块、3 个 MCP 模块共 5 个文件）。结论已编排为 `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md`（21 个工作项 / 4 个里程碑）。

## Context

- 回答用户问题：nop-ai 模块结构是否设计合理、是否需要重构组织、存在哪些 bad smell。
- 方法：3 个并行只读子代理分别深审（a）nop-ai-agent 内部结构、（b）nop-ai-core/nop-ai-api 分层与重复、（c）全组模块划分与依赖图；关键论断经本会话二次抽查验证（48 参构造、toInt 三份复制、41 个 NoOp、51 个配置字段、`servrer` 包名拼写、shell 孤儿 pom 注释、AiCommand 未标废弃、api/crud 零外部引用均逐条复核为实）。
- 审计基线 commit：`e7534f088e`（2026-10-11 工作树）。
- 与既有审计的关系：`ai-dev/audits/nop-ai-agent-audit-tracker.md` 记录的四轮安全/可靠性审计不覆盖本报告的结构/组织维度；`ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（已收口）覆盖执行模型重构，与本次模块组织维度互补，重叠面在各 WI 描述中显式标注。

## 现状

### 规模与模块清单（main 代码实测）

| 模块 | java 文件 | 行数 | 一句话定性 |
|---|---|---|---|
| nop-ai-agent | 555 | 76,789 | 执行引擎，占全组代码量 47% |
| nop-ai-core | 180 | 25,549 | LLM 调用内核 + 堆场（见 P1-3） |
| nop-ai-dao | 69 | 18,459 | 标准分层；手写仅约 500 行，其余为 `_gen` |
| nop-ai-api | 95 | 11,433 | 公开契约，但 70% 是应用实体契约（见 P2-5） |
| nop-ai-toolkit | 81 | 8,528 | 工具执行层，分层健康 |
| nop-ai-gateway | 27 | 5,201 | 三块不相关能力硬捆（见 P2-3） |
| nop-ai-shell | 44 | 4,508 | 生产零消费者孤儿（pom 注释自证） |
| nop-ai-coder | 23 | 2,222 | 名不符实：实为「AI 输出→Nop 元模型」转换器库 |
| nop-ai-tools | 22 | 1,904 | 走 BizModel 路线的另一套工具实现 |
| nop-ai-service | 27 | 863 | 22 个实体 BizModel |
| nop-ai-rag | 7 | 837 | 薄而完整（ingest/search/synthesize + PgVector） |
| nop-ai-maven | 6 | 1,008 | 全仓零外部引用 |
| nop-ai-dsl-orm | 4 | 226 | 与 coder/orm 重复，零外部引用 |
| nop-ai-mcp-server / nop-spring-mcp-server / nop-spring-mcp-server-support | 2 / 1 / 2 | 256 / 12 / 73 | 三个模块共 5 个文件 |
| nop-ai-app | 1 | 32 | quarkus 启动器，未装配 agent/gateway |
| nop-ai-meta / nop-ai-web / nop-ai-codegen / nop-ai-skills | 0 | 0 | Nop 标准分层惯例（model/页面/构建胶水/聚合 pom） |

### 依赖分层（compile scope，POM 实证）

```
第0层 契约      nop-ai-api（仅依赖 nop-api-core；grep 证实 0 条 import io.nop.ai.core）
第1层 内核      nop-ai-core(←api)   nop-ai-toolkit(←api)   nop-ai-dao(←orm)   nop-ai-maven   nop-ai-skills(聚合)
第2层 领域库    nop-ai-dsl-orm  nop-ai-rag  nop-ai-tools  nop-ai-coder  nop-ai-shell(←toolkit)
第3层 引擎      nop-ai-agent(←toolkit+core)   nop-ai-service(←dao+api+meta+core)
第4层 接入      nop-ai-gateway(←agent+dao+core+api+feishu+auth)   nop-ai-web   nop-ai-mcp-server(←coder)
第5层 启动      nop-ai-app(←service+web+auth+quarkus；不含 agent/gateway)
```

**正面结论（审计确认的健康面）**：

- 依赖方向全组正确：api 零反向依赖，core→api 单向，无 compile 级循环、无分层倒置（core 代码中出现的 toolkit/agent 字样均为 javadoc/废弃注释，非真实 import）。
- 异常纪律好：agent 模块 588 处统一 `NopAiAgentException`，错误码集中 `NopAiAgentErrors`，无 bare RuntimeException（仅 12 处裸 IllegalArgumentException 聚集在 tool 入口执行器）。
- 测试密度高：agent 主测文件比 1 : 0.83、行数比 1 : 1.59；横切机制均有 `*InReActLoop` 端到端测试。
- 模型层规范：agent/toolkit/dao 均走 meta 驱动 `_gen` 生成 + 手写子类，无重复实现。
- 多轮审计沉淀：代码注释带编号裁定（AR-06、plan 2301、MA4.2-05），可追溯性强。

## 问题分析

### P1 级缺陷

**P1-1 废弃 chat 栈迁移半途，卡点是一个未标废弃的锚点类。**
`core/api/chat/IAiChatService` 等于 2026-02-15 整体标 `@Deprecated(forRemoval=true)`（新 API `IChatService` 2026-02-14 落地于 nop-ai-api），至今 8 个月未收口。core main 中 25 个文件带废弃标记，但唯一的重要消费者 `core/command/AiCommand.java`（496 行，**自身未标废弃**）把 prompt+tool+retry+persist 全部绑在废弃栈上，下游 `nop-ai-tools` 的 `GraphQLToolProvider`/`GraphQLToolSetFactoryBean` 经它间接绑死。同时双消息模型并存：`api/chat/messages/`（ChatMessage 族 13 文件，外部消费者 258+ 处 import）vs `core/api/messages/`（AiMessage 族 13 文件，core 自留），代价实体化为 gateway 的 `AiDialectBackendMessageConverter` + `ChatServiceFailoverAdapter` 专职互转。

**P1-2 工具（Tool）抽象散布 5 处、3 套并行 SPI、4 套文件工具实现。**
SPI 三套：`nop-ai-api/api/tool/IToolDefinition`（新）、`core/api/tool/`（IAiChatFunctionTool 族 6 文件，已废弃）、`toolkit/api/IToolManager`+`IToolExecutor`（新运行时）。文件工具四份：toolkit 20 个 `*Executor` + `LocalToolFileSystem`、`nop-ai-tools` 的 `FileToolBizModel`（BizModel 路线，依赖 nop-biz）、`nop-ai-mcp-server/AiFileTool`（又一个 @BizModel 文件工具，为用 2 个 Simplifier 依赖整个 coder）、core `file/IFileOperator`（废弃仍保留）。两条注册路线（BizModel vs IToolExecutor）无裁定并存。agent 另有 `agent/tool/` 21 文件 dispatcher 与 9 个 `*.tool.xml`，toolkit 有 15 个 `*.tool.xml`，`_vfs/nop/ai/tools/` 两处同名目录。

**P1-3 nop-ai-core 是 god-module：LLM 内核混装 4 类异质关注点。**
强内聚的 LLM 内核（dialect 8 文件 4,622 行、model 39、reliability 20、prompt 8，约占 60%）之外混装：①RAG 的 SPI 与数学工具（`api/vectorstore` 4 + `api/embedding` 4 含 CosineSimilarity/RelevanceScore + `search/AiModelTextEmbedding`）；②编码代理关注点（`file/` 9 文件文件编辑原语 + `response/` 5 个结构化输出解析器——与「LLM 调用内核」无关）；③网关级模型选路（`routing/` 9 文件，且 `RuleBasedSelectionStrategy` 拖入 `nop-rule-core` 依赖，使 rag/coder/dsl-orm/agent 全部传递依赖规则引擎）；④测试设施在 main scope（`mock/` 6 文件含文件轮询+线程池的 record/replay 机制，与 `service/MockAiChatService` 两套并存；消费面全部是 test scope）。另 core 的 `NopAiCoreErrors` 代管上层模块错误码（`nop.err.ai.agent.*`、`nop.err.ai.tools.*`）。

**P1-4 agent 引擎装配面蔓延（config sprawl）。**
`ReActAgentExecutor` 构造函数 **48 个参数**（`engine/ReActAgentExecutor.java:209-256` 实测），配套 700 行 Builder；`DefaultAgentEngine`（1,300 行）有 **8 个 telescoping 构造函数**（`:304-345`）+ 约 40 个纯委托 getter/setter；`DefaultAgentEngineConfig` 51 个私有字段 × 4 处重复暴露（Config 字段 / Engine 委托 / Builder 方法 / Executor 构造参数）。新增一个引擎能力平均要改 5-6 个文件。配套发现：`NopAiAgentException` 放在 `engine` 包却被全模块 130 个文件引用，形成 engine⇄security（144/46 处互 import）、engine⇄reliability（53/16）两个包级环。已知裁定：agent-refactor roadmap（已收口）只覆盖执行模型语义，未触碰装配面——本次不重复、不冲突。

### P2 级缺陷

**P2-1 nop-ai-app 无产品组装闭环。**
555 文件的 agent 引擎没有任何默认宿主：`nop-ai-app/pom.xml` 只依赖 service/web/auth/quarkus，不依赖 agent 与 gateway；application.yaml 无 agent/gateway 配置。库层面划分精细，产品层面双栈（经典 Nop 栈 codegen→dao→meta→service→web→app 与 AI 引擎栈 api→core/toolkit→…→agent→gateway）只在 gateway 一点交汇。

**P2-2 dialect 家族结构性重复。**
五个 dialect（Anthropic 977 / Gemini 784 / Responses 770 / Ollama 580 / OpenAi 493 行）方法集完全同构；私有 helper 三份复制——`toInt(Object)`（`AnthropicDialect.java:665`、`OpenAiDialect.java:407`、`ResponsesDialect.java:764` 实测）、`getString(Map,String)` 三份、`reverseFinishReason` 两份，而 `AbstractLlmDialect` 已有 `getStringByPath` 未被复用；buildBody 骨架逐字同构，差异只是字段名（errorMappings 已证明配置驱动可行）。估计可净省 800-1,200 行。

**P2-3 nop-ai-gateway 三块不相关能力硬捆。**
`failover/`（11 文件）+ `channel/`（12 文件，飞书 IM）+ `login/`（4 文件，扫码登录，依赖 nop-auth）。后果：只用 LLM failover 也要传递依赖 IM 渠道、auth、agent、dao、netty 网关框架。与 `nop-ai-gateway-failover-roadmap.md`（active）的划界：该 roadmap 演进 failover 能力语义，本报告只裁定模块承载边界，不重复立项。

**P2-4 三个零消费者孤儿模块。**
①`nop-ai-shell`（4.5k 行 bash lexer/parser/执行器）：`nop-ai-agent/pom.xml:30-32` 注释自证 "no production consumer (orphan module, plan 2301 Known Residual)"，唯一引用是 2 个测试类；**但** `nop-ai-shell-bash-cst-frontend-roadmap.md`（2026-10-05，活跃）仍在投入其 bash parser 前端——处置必须先裁定，不能直接退役。②`nop-ai-maven`（6 文件）：全仓 `ai.maven.` 零外部引用。③`nop-ai-dsl-orm`（4 文件）：零外部引用，且与 coder/orm 重复（`GptOrmSqlType` vs `AiOrmSqlType`、`GptOrmModelParser` vs `AiOrmModel`）。

**P2-5 nop-ai-api 70% 是应用实体契约，约 1/3 是死代码。**
`api/beans/`（44 文件 7,224 行）+ `api/crud/`（22 个 13 行接口）= 66/95 文件，覆盖 Project/Requirement/TestCase/Todo 等 AI 开发平台应用实体；`api/crud/` 22 个接口全仓 0 外部消费者（实测）。真正的能力契约（chat/messages/stream、IToolDefinition、mcp/credential/secure/exceptions）约 25-30 文件。后果：只用 IChatService 的下游（toolkit、spring-mcp-server-support）被迫背 7,000 行实体 bean。

**P2-6 skill 概念三处异构。**
`agent/skill/`（14 文件 provider/curator 基础设施）、顶层 `nop-ai-skills`（code-analyzer 33 文件是真模块；deepwiki/translate 各 0 个 main java，纯 prompt 资源包却配了模块）、`toolkit/tools/SkillExecutor`（把 skill 当 list/load 文档工具）。三处抽象不一致，跨模块追溯成本高。

**P2-7 MCP 三模块 5 文件过碎 + 包名拼写错误。**
`nop-ai-mcp-server`（2 文件，依赖 coder 仅为 2 个工具类）、`nop-spring-mcp-server-support`（2 文件 spring-ai adapter，划分本身合理）、`nop-spring-mcp-server`（1 文件启动器，包名 `io/nop/spring/mcp/servrer/` ——servrer 拼写错误实测）。

### P3 级问题（风格/卫生）

- **agent 微包碎片化**：11 个包在 300-800 行（talent 2 文件、budget 3、recipe 1、fencing 5、quota 6…），每个包是「I 接口 + Default + NoOp」同一模式；全模块 **41 个 NoOp 类**（实测），多数无生产实现者。
- **`guardrail/test` 是 main 源码里的测试 harness**：24 文件 2,393 行（CheckpointTestHarness、AttackPlugin、CrescendoAttackTransform 等）+ 攻击语料打进 main resources，并 import engine 加深耦合。
- **命名冲突**：`agent/reliability`（长任务韧性：checkpoint/sustainer）与 `core/reliability`（调用层容错：retry/breaker）同名不同义。
- **dao 双限界上下文**：会话/聊天域（Session/ChatRequest/Model…）与 AI 项目管理域（Project/Requirement/TestCase/Todo/GenFile）混在一个 dao，后者只有 web CRUD 页面和 service BizModel 消费。
- **Eclipse 工程文件入库**：全部 21 个模块的 `.project`/`.classpath`/`.settings/` 在 git 跟踪中（根 .gitignore 已列但先入库文件不受影响）。
- **工作笔记混入代码树**：根级 `nop-ai/model/chat-history/` 产品分析笔记属 `ai-dev/` 性质的内容。
- 同一 compaction 关注点散在 3 个包（toolkit/api 的 ICompactionArchive、agent/compact 的实现、agent/session 的 Config/Result）。

## 改进建议

按「先收口迁移、再归位关注点、后动装配面、最后整合模块」顺序编排为 4 个里程碑 21 个工作项，完整立项见 `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md`。要点：

1. **M1 废弃栈收口与双轨合一**：迁走 AiCommand 链 → 删 25 个废弃类 → 工具 SPI 归一裁定 → gateway 桥接层简化。迁移面经 grep 验证很小，是低成本高杠杆的低垂果实。
2. **M2 core 瘦身与关注点归位**：RAG SPI 归 rag、routing（连同 rule-core 依赖）归 gateway、file/response 归 coder、mock 下沉 testkit、dialect 消重（净省约 1,000 行）、错误码归还各模块。
3. **M3 agent 装配面收敛**：NopAiAgentException 上移断包环、48 参构造改分组对象、8 个 telescoping 构造与委托 getter/setter 删除（净删约 800 行）、微包合并与 NoOp 审计、guardrail/test 移出 main。
4. **M4 模块整合与卫生**：api 瘦身（实体契约迁出）、MCP 合并 + servrer 更名、孤儿模块裁定（shell 必须与 CST roadmap 划界后裁定）、skills 拉平、dao 双上下文与 gateway 拆分裁定、仓库卫生。

被否决的方向：**推倒式重组**（按双栈拆两个顶层模块组）——依赖图证明两栈交汇面只有 gateway 与 service 两点、无编译期循环，推倒成本远超收益；**立即退役 nop-ai-shell**——存在活跃的 CST 前端 roadmap，须先裁定。

## Conclusion

- nop-ai 的分层骨架和工程纪律是健康的，问题不在「分错层」而在「迁移半途 + 堆场效应 + 装配面蔓延 + 碎片化」，全部可分阶段收敛，无需停止新功能开发。
- 后续工作：`ai-dev/backlog/nop-ai-structure-refactor-roadmap.md`（21 WI / 4 里程碑）；每个 WI 按平台惯例以独立 plan 执行并做 closure audit。
- 被否决方案及理由见上文。

## Open Questions

- [ ] `nop-ai-shell` 的终局：保留为沙箱后端并随 CST roadmap 演进，还是裁定退役后以 toolkit sandbox 为唯一 bash 执行面？（WI16 裁定项）
- [ ] `nop-ai-api` 的 beans/crud 实体契约去向：迁到 dao/service 侧、独立 `nop-ai-entity` 契约 jar，还是随 web CRUD 生成物内联？（WI14 裁定项）
- [ ] 工具注册路线二选一：IToolExecutor 路线（toolkit）为唯一路线、BizModel 路线仅作为其适配器，还是双路线正式共存并文档化？（WI2 裁定项）

## References

- `docs-for-ai/01-repo-map/module-groups.md`（nop-ai 分层速查，本审计的规范基线）
- `docs-for-ai/03-modules/nop-ai-gateway.md`
- `ai-dev/audits/nop-ai-agent-audit-tracker.md`（既有四轮安全/可靠性审计索引）
- `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（已收口的执行模型重构，划界对象）
- `ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md`（在途，划界对象）
- `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（active，划界对象）
- `ai-dev/backlog/nop-ai-shell-bash-cst-frontend-roadmap.md`（active，shell 处置的前置划界对象）
- `ai-dev/analysis/00-analysis-writing-guide.md`
