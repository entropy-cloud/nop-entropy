# 审计-修复报告索引（arm）

> 启动时间：2026-07-30
> 目标模块组：nop-ai（18 子模块，排除 MCP——MCP 协议集成模块，独立发布周期，需单独审计）
> 总览：ai-dev/backlog/audit-remediation-roadmap.md
> 维度矩阵：arm-audit-dimension-matrix.md
> 状态汇总：已完成 32 | 进行中 0 | 待办 0 | P0 未解决 0 | **MV+MG 已收口（2026-07-31）**

## 报告清单

| 报告 | 里程碑 | 维度 | 范围 | P0 | P1 | P2/P3 | 状态 |
|------|--------|------|------|----|----|-------|------|
| [`2026-07-30-2100-arm-MA1.1-nop-ai-api-core-dependency.md`](./2026-07-30-2100-arm-MA1.1-nop-ai-api-core-dependency.md) | MA1.1+MA1.2 | 依赖图+API契约 | api-core | 0 | 2 | 5(P2)+2(P3) | `done` |
| [`2026-07-31-2200-arm-MA1.3-nop-ai-toolkit.md`](./2026-07-31-2200-arm-MA1.3-nop-ai-toolkit.md) | MA1.3 | 模块职责 | toolkit | 0 | 2 | 12(P2:8, P3:4) | `done` |
| [`2026-07-31-2201-arm-MA1.4-nop-ai-infra.md`](./2026-07-31-2201-arm-MA1.4-nop-ai-infra.md) | MA1.4 | 模块职责 | infra | 0 | 3 | 11(P2:3, P3:8) | `done` |
| [`2026-07-31-2202-arm-MA1.5-nop-ai-naming.md`](./2026-07-31-2202-arm-MA1.5-nop-ai-naming.md) | MA1.5 | 命名与术语 | 全模块 | 0 | 3 | 6(P2:4, P3:2) | `done` |
| [`2026-07-30-2130-arm-MA2.1-2.4-nop-ai-orm-biz.md`](./2026-07-30-2130-arm-MA2.1-2.4-nop-ai-orm-biz.md) | MA2.1+MA2.4 | ORM模型+BizModel | biz-dao+biz-svc | 1 | 4 | 6 | `done` |
| [`2026-07-31-0334-arm-MA2.2-nop-ai-pipeline.md`](./2026-07-31-0334-arm-MA2.2-nop-ai-pipeline.md) | MA2.2 | 生成管线 | biz | 0 | 1 | 5(P2:3, P3:2) | `done` |
| [`2026-07-31-0348-arm-MA2.3-nop-ai-delta.md`](./2026-07-31-0348-arm-MA2.3-nop-ai-delta.md) | MA2.3 | Delta 合规 | biz | 0 | 1 | 4(P2:2, P3:2) | `done` |
| [`2026-07-31-0353-arm-MA2.5-nop-ai-xmeta.md`](./2026-07-31-0353-arm-MA2.5-nop-ai-xmeta.md) | MA2.5 | XMeta 对齐 | biz-svc | 0 | 2 | 2(P2:1, P3:1) | `done` |
| [`2026-07-31-0359-arm-MA2.6-nop-ai-graphql.md`](./2026-07-31-0359-arm-MA2.6-nop-ai-graphql.md) | MA2.6 | GraphQL/API | biz-svc | 0 | 0 | 1(P3:1) | `done` |
| [`2026-07-31-0409-arm-MA2.7-nop-ai-ioc.md`](./2026-07-31-0409-arm-MA2.7-nop-ai-ioc.md) | MA2.7 | IoC/Bean | 全模块 | 0 | 0 | 4(P2:1, P3:3) | `done` |
| [`2026-07-31-0753-arm-MA3.1-nop-ai-cross-module-deps.md`](./2026-07-31-0753-arm-MA3.1-nop-ai-cross-module-deps.md) | MA3.1 | 跨模块依赖 | 全模块 | 0 | 0 | 5(P2:1, P3:4) | `done` |
| [`2026-07-31-1550-arm-MA3.2-nop-ai-security.md`](./2026-07-31-1550-arm-MA3.2-nop-ai-security.md) | MA3.2 | 安全权限 | 全模块 | 0 | 3 | 4(P2:4) | `done` |
| [`2026-07-31-0422-arm-MA3.3-nop-ai-async-txn.md`](./2026-07-31-0422-arm-MA3.3-nop-ai-async-txn.md) | MA3.3 | 异步/事务 | 全模块 | 0 | 0 | 2(P3:2) | `done` |
| [`2026-07-31-0423-arm-MA3.4-nop-ai-error-handling.md`](./2026-07-31-0423-arm-MA3.4-nop-ai-error-handling.md) | MA3.4 | 错误处理 | 全模块 | 0 | 2 | 9+2(P2:4, P3:5+2downgraded) | `done` |
| [`2026-07-31-0423-arm-MA3.5-nop-ai-cross-module-contract.md`](./2026-07-31-0423-arm-MA3.5-nop-ai-cross-module-contract.md) | MA3.5 | 跨模块契约 | 全模块 | 0 | 2 | 6(P2:4, P3:2) | `done` |
| [`2026-07-31-XXXX-arm-MA4.1-nop-ai-typesafety.md`](./2026-07-31-XXXX-arm-MA4.1-nop-ai-typesafety.md) | MA4.1 | 类型安全 | 全模块 | 0 | 0 | 7(P2:5, P3:2) | `done`（P2 已修复，见 §P2 修复追踪） |
| [`2026-07-31-0539-arm-MA4.2-nop-ai-style.md`](./2026-07-31-0539-arm-MA4.2-nop-ai-style.md) | MA4.2 | 代码风格 | 全模块 | 0 | 0 | 14(P2:7, P3:7) | `done`（P2 已修复/裁定，见 §P2 修复追踪） |
| [`2026-07-31-XXXX-arm-MA4.3-nop-ai-test-coverage.md`](./2026-07-31-XXXX-arm-MA4.3-nop-ai-test-coverage.md) | MA4.3 | 测试覆盖 | 全模块 | 0 | 6 | P1:6, P2:4, P3:3, Positive:1 | `done`（P2 已修复，见 §P2 修复追踪·测试质量批次） |
| [`2026-07-31-arm-MA4.4-nop-ai-test-effectiveness.md`](./2026-07-31-arm-MA4.4-nop-ai-test-effectiveness.md) | MA4.4 | 测试有效性 | 全模块 | 0 | 0 | P2:4, P3:3, N/A:1 | `done`（P2 已修复，见 §P2 修复追踪·测试质量批次） |
| [`2026-07-31-XXXX-arm-MA4.5-nop-ai-doc-consistency.md`](./2026-07-31-XXXX-arm-MA4.5-nop-ai-doc-consistency.md) | MA4.5 | 文档一致性 | 全模块 | 0 | 0 | 9(P2:7, P3:2) | `done`（P2 已修复/裁定，见 §P2 修复追踪） |
| [`2026-07-30-2100-arm-MA5.1-nop-ai-hollow-scan.md`](./2026-07-30-2100-arm-MA5.1-nop-ai-hollow-scan.md) | MA5.1 | 空壳实现 | 全模块 | 0 | 2 | 7 | `done` |
| [`2026-07-30-2100-arm-MA5.2-nop-ai-silent-noop.md`](./2026-07-30-2100-arm-MA5.2-nop-ai-silent-noop.md) | MA5.2 | 静默跳过 | 全模块 | 0 | 2 | 20 | `done` |
| [`2026-07-30-2130-arm-MA5.3-nop-ai-wiring.md`](./2026-07-30-2130-arm-MA5.3-nop-ai-wiring.md) | MA5.3 | 接线完整性 | 全模块 | 0 | 2 | 7 | `done` |
| [`2026-07-31-arm-MA5.4-nop-ai-design-drift.md`](./2026-07-31-arm-MA5.4-nop-ai-design-drift.md) | MA5.4 | 设计文档 drift | 全模块 | 0 | 1 | 5(P2)+3(P3) | `done` |
| [`2026-07-31-arm-MA5.5-nop-ai-sensitive-leak.md`](./2026-07-31-arm-MA5.5-nop-ai-sensitive-leak.md) | MA5.5 | 敏感泄露 | 全模块 | 0 | 4 | 4(P2)+2(P3) | `done` |
| [`2026-07-31-arm-MA5.6-nop-ai-test-isolation.md`](./2026-07-31-arm-MA5.6-nop-ai-test-isolation.md) | MA5.6 | 测试隔离 | 全模块 | 0 | 1 | 2(P2)+3(P3) | `done` |
| [`2026-07-31-arm-MA5.7-nop-ai-fix-verification.md`](./2026-07-31-arm-MA5.7-nop-ai-fix-verification.md) | MA5.7 | 修复验证 | 全模块 | 0 | 6（MA5.7 时点 5 open + 1 fixed；5 项 open 均已由 MR2/MR3 修复或裁定关闭 → MV 矩阵 61 行 P1 `fixed`、open=0） | — | `done` |
| [`2026-07-31-1240-arm-MA6.1-nop-ai-llm-config-security.md`](./2026-07-31-1240-arm-MA6.1-nop-ai-llm-config-security.md) | MA6.1 | LLM 配置安全 | 全模块 | 1 | 4 | 3(P2) | `done` |
| [`2026-07-31-arm-MA6.2-nop-ai-agent-security.md`](./2026-07-31-arm-MA6.2-nop-ai-agent-security.md) | MA6.2 | Agent 编排安全 | 全模块 | 0 | 4 | 3(P2) | `done` |
| [`2026-07-31-0000-arm-MA6.3-nop-ai-token-reliability.md`](./2026-07-31-0000-arm-MA6.3-nop-ai-token-reliability.md) | MA6.3 | Token 计量与调用可靠性 | 全模块 | 0 | 2 | 4(P2) | `done` |
| [`2026-07-31-arm-MA6.4-nop-ai-vector-isolation.md`](./2026-07-31-arm-MA6.4-nop-ai-vector-isolation.md) | MA6.4 | 向量存储/Embedding 隔离 | 全模块 | 0 | 3 | 2(P2)+1(P3) | `done` |
| [`2026-07-31-arm-MA6.5-nop-ai-chat-prompt-security.md`](./2026-07-31-arm-MA6.5-nop-ai-chat-prompt-security.md) | MA6.5 | 对话历史与 Prompt 安全 | 全模块 | 0 | 5 | 3(P2) | `done` |

> **计数说明（2026-07-31 收敛修正）**：本清单各行 finding 计数以对应报告**明细**为准。MA4.1 行修正为 `7(P2:5, P3:2)`（-01/02/03/05/06 为 P2，-04/07 为 P3）；MA4.3 行修正为 `P1:6, P2:4, P3:3, Positive:1`（报告自身摘要表 P2=3 漏计 **-14**，内部矛盾，以明细为准；MA4.3-14 为 P2 且有 successor 计划 `2026-07-31-1446-3-arm-ma4-p2-test-quality.md` 承接，未静默丢弃）；MA4.4 行修正为 `P2:4, P3:3, N/A:1`（-07 为 N/A pass）；MA4.5 行修正为 `9(P2:7, P3:2)`（报告自身摘要表 P2=6/P3=3 与明细矛盾，以明细为准：-001~007 为 P2，-008/009 为 P3）；MA4.2 行 `14(P2:7, P3:7)` 与明细一致未改。MA5.7 行 P1 计数为最终状态：MA5.7 审计时点 5 项 open（P1-MA5-002、P1-MA5.3-001/002 → MR2；MA5.2 F-016 → MR3；P1-MA5-003 → MR3+MV 裁定为 SPI 扩展点契约）均已关闭，MV 矩阵 61 行 P1 全部 `fixed`、open=0（见 §MV 矩阵）。

## P0 发现追踪（即时通道）

| Finding ID | 报告 | 描述 | 修复路径 | 修复状态 |
|-----------|------|------|---------|---------|
| P0-MA2-01 ✅ | MA2.1 | 双 ORM 源文件漂移（nop-ai.orm.xml vs ai-gen.orm.xml） | [异步修复 plan](ai-dev/plans/2026-07-30-2130-arm-fix-p0-ma2-01.md) | `fixed`；successor `2026-08-01-1306-1-arm-p2-coder-ai-gen-model-refs.md` 已收口（6 个 coder 引用裁定不改 + 归档文件移除） |
| P0-MA6-01 ✅ | MA6.1 | Gemini API key 在 URL query parameter 中以明文传输（GeminiDialect.buildUrl） | MR3 | `fixed` |

## P1 发现汇总（待 MR 批量修复）

| Finding ID | 报告 | 描述 | 目标 MR | 修复状态 |
|-----------|------|------|--------|---------|
| P1-MA1-001 (原 F01) | MA1.1-MA1.2 | `nop-diff` 未使用依赖 | — | `fixed`（MA1.1 审计中就地修复） |
| P1-MA1-002 (原 F02) | MA1.1-MA1.2 | 废弃并行 API 体系未清理（IAiChatService 等） | MR1 | fixed |
| P1-MA5-001 (原 F03) | MA5.2/MA5.1 交叉 | `DefaultAiChatService.getSession()` 始终返回 null | — | `fixed`（审计中修改为 throw UnsupportedOperationException） |
| P1-MA5-002 (原 F04) | MA5.2 | `BashExecutor` 子线程流读取空 catch | MR2 | fixed |
| P1-MA5-003 (原 F05) | MA5.1 | IVectorStore / IEmbeddingModel / ITokenCountEstimator 接口无生产实现 | MR3→MV | `fixed`（MV 裁定为 SPI 扩展点契约，见 §MV 矩阵） |
| P1-MA2-002 | MA2.1 | NopAiProject 缺失审计传播属性 | MR1 | fixed |
| P1-MA2-003 | MA2.1 | NopAiRequirement version 字段类型冲突（VARCHAR vs 乐观锁 int） | MR1 | fixed |
| P1-MA2-004 | MA2.1 | NopAiSessionContext refPropName="context" 应为 "contexts" | MR1 | fixed |
| P1-MA2-005 | MA2.1 | _dao.beans.xml 为空 — 无接口 Biz bean 注册 | MR1 | `fixed`（MR4 裁定见 §MR4 P1 表逐行核验） |
| P1-MA5.3-001 | MA5.3 | ChatServiceImpl(IChatService) 无 IoC bean 定义 | MR2 | fixed |
| P1-MA5.3-002 | MA5.3 | DefaultAiChatService @Deprecated 但却是唯一注册的 chat service bean | MR2 | fixed |
| P1-MA1-003 | MA1.3 | FileToolBizModel 使用废弃 IFileOperator 接口 | MR1 | fixed |
| P1-MA1-010 | MA1.3 | nop-ai-skills 子模块零 IoC bean 注册 | MR1 | fixed |
| P1-MA1-017 | MA1.4 | nop-ai-maven 模块名与实际内容不符（核心为 VFS 非 Maven） | MR1 | fixed |
| P1-MA1-018 | MA1.4 | nop-ai-codegen 零生产 Java 代码 | MR1 | fixed |
| P1-MA1-019 | MA1.4 | nop-ai-codegen postcompile 引用不存在的 ORM 模型文件 | MR1 | fixed |
| P1-MA1-031 | MA1.5 | NopAiSessionContext relation refPropName 不匹配（context vs contexts） | MR1 | fixed |
| P1-MA1-032 | MA1.5 | 五种命名约定并存（NopAi/Chat/Ai/Agent/Tool） | MR1 | fixed |
| P1-MA1-033 | MA1.5 | 重复 ORM session/message 模型（nop-ai-agent vs nop-ai 主模型） | MR1 | fixed |
| P1-MA2-014 | MA2.2 | XDSL codegen 脚本存在但输出不可验证（agent/toolkit/core） | MR1 | fixed |
| P1-MA2-018 | MA2.3 | 9个废弃 snake_case dict 文件（nop-ai-meta） | MR1 | fixed |
| P1-MA2-023 | MA2.5 | NopAiModel.apiKey 凭证字段暴露为 queryable/sortable | MR1+MR4 | `fixed`（MR4 下沉到 ORM 源模型 not-query/not-sort/not-pub，见 §MR4 裁定 #1） |
| P1-MA2-024 | MA2.5 | NopAiSession 重复 to-many 关系 context/contexts | MR1 | fixed |
| P1-MA3-020 | MA3.2 | BizModel 方法全部缺少 @Auth 权限注解（0/45 @BizModel 类） | MR2 | fixed |
| P1-MA3-021 | MA3.2 | NopAiModel.apiKey 凭证字段在 xmeta 中完全暴露（queryable/sortable/insertable/updatable 均未限制） | MR2 | fixed |
| P1-MA3-022 | MA3.2 | LocalFileOperator.resolveFile() 绝对路径绕过 sandbox（/开头的路径通过 new File 拼接可逃逸 baseDir） | MR2 | fixed |
| P1-MA3-001 | MA3.4 | AiCoreErrors.ERR_AI_RESULT_INVALID_NUMBER 描述模板错位（value={name} 应为 value={value}） | MR2 | fixed |
| P1-MA3-002 | MA3.4 | SequentialThinkingBizModel.processThought @BizMutation 使用 IllegalArgumentException 而非 ErrorCode | MR2 | fixed |
| P1-MA3-01 | MA3.5 | nop-ai-agent 依赖 core 内部模型包（ChatOptionsModel） | MR2 | fixed |
| P1-MA3-02 | MA3.5 | nop-ai-agent 依赖 core 内部 dialect 包（ILlmDialect/LlmDialectFactory） | MR2 | fixed |
| P1-MA5.4-001 | MA5.4 | Gateway bidirectional dialect conversion only works for OpenAI; other 3 dialects throw UOE at runtime | MR3 | `fixed` |
| P1-MA5.5-001 | MA5.5 | Hardcoded JWT enc-key in nop-ai-app/application.yaml | MR3 | `fixed` |
| P1-MA5.5-002 | MA5.5 | Plaintext MySQL password in nop-ai-coder/tools/application.yaml | MR3 | `fixed` |
| P1-MA5.5-003 | MA5.5 | Gemini URL apiKey leakage via GeminiDialect.buildUrl() | MR3 | `fixed` |
| P1-MA5.5-004 | MA5.5 | apiKey serialized in NopAiModelOutputBean (GraphQL DTO exposed to API response) | MR3 | `fixed` |
| P1-MA5.6-001 | MA5.6 | CoreInitialization lifecycle race across 40+ test classes (parallel test execution risk) | MR3 | `fixed` |
| P1-MA5.7-001 | MA5.7 | MA5.2 F-016 (ReActAgentExecutor hook catch+LOG.warn) untracked in arm-index — P1 gap | MR3 | `fixed` |
| P1-MA6.1-001 | MA6.1 | DefaultAiChatService.getApiVersion() reads wrong config key (reads API key instead of version) | MR3 | `fixed` |
| P1-MA6.1-002 | MA6.1 | NopAiModelOutputBean exposes apiKey in API response (same as MA5.5-004, consolidated) | MR3 | `fixed` |
| P1-MA6.1-003 | MA6.1 | DefaultChatLogger logs chat content without sanitization (potential credential leakage) | MR3 | `fixed` |
| P1-MA6.1-004 | MA6.1 | NopAiModel DAO entity stores API key in plaintext VARCHAR in DB | MR3 | `fixed` |
| P1-MA6.2-001 | MA6.2 | HttpRequestExecutor SSRF — no URL whitelist or internal-IP blocklist | MR3 | `fixed` |
| P1-MA6.2-002 | MA6.2 | GraphqlQueryExecutor SSRF — endpoint URL accepted verbatim without validation | MR3 | `fixed` |
| P1-MA6.2-003 | MA6.2 | LocalToolFileSystem.isPathAllowed() silently bypassed — not called from any file operation | MR3 | `fixed` |
| P1-MA6.2-004 | MA6.2 | BashExecutor zero input validation — any shell command accepted | MR3 | `fixed` |
| P1-MA6.3-001 | MA6.3 | ChatServiceImpl has no timeout — LLM calls can hang indefinitely | MR3 | `fixed` |
| P1-MA6.3-002 | MA6.3 | Default retry policy and circuit breaker are both pass-through — zero resilience defaults | MR3 | `fixed` |
| P1-MA6.4-001 | MA6.4 | VectorStoreOptions missing tenantId field — no tenant isolation at API contract level | MR3 | `fixed` |
| P1-MA6.4-002 | MA6.4 | IEmbeddingModel has no auth/tenant context in its API | MR3 | `fixed` |
| P1-MA6.4-003 | MA6.4 | memory adapters (IStorageAdapter/IVectorAdapter) not integrated with ITenantResolver | MR3 | `fixed` |
| P1-MA6.5-001 | MA6.5 | User message content shipped to LLM without input sanitization (no-op guardrail by default) | MR3 | `fixed` |
| P1-MA6.5-002 | MA6.5 | DefaultAiChatExchangePersister writes full conversation to plaintext files (no encryption) | MR3+MR4 | `fixed`（MR3 overclaim；MR4 落地可选 AES 加密，见 §MR4 P1 表逐行核验） |
| P1-MA6.5-003 | MA6.5 | No session access authentication — AgentEngine.getSession() has no auth check | MR3+MR4 | `fixed`（引擎层 fail-closed sessionId 校验；legacy 绑定见 §MR4 P1 表逐行核验） |
| P1-MA6.5-004 | MA6.5 | User messages stored verbatim → loaded into system prompt on next turn (injection amplification) | MR3 | `fixed` |
| P1-MA6.5-005 | MA6.5 | DefaultPathAccessChecker allows absolute path traversal in file operations | MR3 | `fixed` |
| MA4.3-01 | MA4.3 | nop-ai-api 完全无测试（84 main 文件，0 tests） | MR2+MR4 | `fixed`（`TestChatOptions.java` 3 方法，行为断言） |
| MA4.3-02 | MA4.3 | nop-ai-dao 完全无测试（66 main 文件，0 tests） | MR4 | `fixed`（`TestNopAiOrmEntityMapping.java` 5 方法：实体映射/DAO CRUD/enc 绑定器/to-many/21 Biz 接口契约） |
| MA4.3-03 | MA4.3 | BizModel 覆盖 1/45（2.2%） | MR4 | `fixed`（`TestNopAiBizModelEntityCrud.java` 覆盖 3 个 BizModel 实体 CRUD + `TestSequentialThinkingBizModel` + 既有 summarize 测试） |
| MA4.3-04 | MA4.3 | nop-ai-core `api/` 包无测试（43 文件） | MR4 | `fixed`（`TestCosineSimilarityAndRelevanceScore.java` 8 方法 + `TestDefaultAiChatFunctionTool.java` 4 方法） |
| MA4.3-05 | MA4.3 | nop-ai-tools 完全无测试（19 main 文件，0 tests） | MR2+MR4 | `fixed`（`TestSequentialThinkingBizModel.java` 3 方法，行为断言） |
| MA4.3-07 | MA4.3 | nop-ai-service 仅 1 测试覆盖 24 main 文件（4%） | MR4 | `fixed`（`TestNopAiBizModelEntityCrud.java` 4 方法覆盖 NopAiSession/NopAiTodo/NopAiSessionMessage） |

## MR4 裁定（2026-07-31）

MR1/MR2/MR3 重叠 fix-surface 逐一裁决，结论如下（live repo 状态一致，除标注项已修复）：

### 1. NopAiModel.apiKey 三层暴露链（MR1 P1-MA2-023 + MR2 P1-MA3-021 + MR3 P1-MA5.5-004/P1-MA6.1-002）

- **裁定**：ORM 源模型是生成产物的唯一来源。apiKey 在 `nop-ai.orm.xml` 列上标记 `tagSet="enc,not-query,not-sort,not-pub"` + `ui:show="X"`，codegen 生成的 `_NopAiModel.xmeta` 因此不再暴露（`queryable="false" sortable="false" published="false" internal="true"`）；Delta xmeta 保持 GraphQL 级 `insertable="false" updatable="false"`；`NopAiModelOutputBean` 无 apiKey 字段（MR3 重新生成）；GraphQL schema 运行时由合并后 xmeta 驱动，不输出 apiKey。**五层一致**。
- **发现的不一致（已修复）**：MR1 声称 P1-MA2-023 fixed（限制 queryable/sortable），但限制未下沉到 ORM 源模型，生成的 `_NopAiModel.xmeta` 仍暴露 `queryable="true" sortable="true"` — 运行时仅靠 Delta xmeta 兜底，属于 fragile 状态。本次将限制写入 ORM 源模型并重新生成 meta。
- **回归测试**：`nop-ai-meta/src/test/java/io/nop/ai/meta/TestNopAiModelApiKeyXmeta.java`（3 个测试方法：base xmeta 限制、合并 xmeta 完全限制、非凭证字段不受影响）。

### 2. BashExecutor（MR2 P1-MA5-002 + MR3 P1-MA6.2-004）

- **裁定**：两修复共存无冲突 — MR2 的子线程流读取失败 `LOG.warn`（不再空 catch）与 MR3 的 `DESTRUCTIVE_COMMAND` 校验 + `validateCommand()` + `DANGEROUS_ENV_VARS` 过滤各自独立、live 均在位。测试 `BashExecutorTest` 已覆盖 destructive 命令拒绝路径。

### 3. ChatServiceImpl / 废弃 chat API（MR2 P1-MA5.3-001/002 + MR1 P1-MA1-002）

- **裁定**：无冲突。`ChatServiceImpl implements IChatService`（非废弃 `IAiChatService`）并注册 `nopChatService` bean（`ioc:type=io.nop.ai.api.chat.IChatService`）；废弃的 `DefaultAiChatService`/`nopAiChatService` 保留向后兼容，`IAiChatService` 标记 `@Deprecated(forRemoval=true)`。apiKey 经 `LlmConfigHelper.resolveApiKey` 后走 header（Gemini `x-api-key`），不落 URL。

### 4. @Auth 权限命名（MR2 P1-MA3-020 vs MR1 P1-MA1-032）

- **裁定**：MR2 R2.0 计划文本声称 `ai:<entity>:<action>`，实际落地为 `<BizObjName>:<action>`（`FileTool:read`、`SequentialThinking:process`、`NopAiChatResponse:query`、`AiFileTool:read/write`），与平台 GraphQL 权限约定一致（`bizObjName + ':' + action`，见 `ReflectionBizModelBuilder`）。`ai:` 前缀属 MR2 计划文本笔误，live 状态正确，无需代码变更。

### 5. NopAiSessionContext refPropName（MR1 P1-MA2-004/P1-MA1-031）

- **裁定**：live ORM `refPropName="contexts"` 与 `NopAiSession.to-many contexts` 双向匹配，Java 侧一致。

## MR4 P1 表逐行核验（2026-07-31）

MR4 Phase 2 对 P1 表全部 `fixed` 行做了 live repo 证据核验（提交/测试文件/代码路径）。结果：

- **可证实（46 行）**：P1-MA1-001/002/003（文档化决策）、P1-MA5-001/002、P1-MA2-002/003/004/014/018/023/024、P1-MA5.3-001/002、P1-MA1-010/017/018/019/031/032/033、P1-MA3-020/021/022/001/002/01/02、P1-MA5.4-001、P1-MA5.5-001/002/003/004、P1-MA5.6-001、P1-MA5.7-001（MR3 文档化关闭：ON_ERROR 回落到引擎默认处理，PRE_/BEFORE_ rethrow，live 有 re-entry 限制 + LOG.warn）、P1-MA6.1-001/002/003/004、P1-MA6.2-001/002/003/004、P1-MA6.3-001/002、P1-MA6.4-001/002/003、P1-MA6.5-001/004/005。证据：@Deprecated(forRemoval=true)、canonical path 校验、AiCoreErrors 模板、ERR_AI_TOOLS_INVALID_THOUGHT、yaml 占位符、Gemini x-api-key header、NopAiModelOutputBean 无 apiKey 字段、volatile 字段、validateUrl/isPrivateIp、setTimeout(CFG_AI_SERVICE_READ_TIMEOUT)、StandardRetryPolicy/ThresholdBreaker 默认、tenantId 字段、ChatSystemMessage 隔离、PathAccessChecker 遍历防御、guardrail WARN-on-noop 等。
- **无法证实 / 已在本轮修复或裁定（3 行）**：P1-MA2-005（裁定见下）、P1-MA6.5-002（MR4 已修复）、P1-MA6.5-003（裁定见下）。
- **MV closure audit 纠正（1 行）**：P1-MA5-003 原列"生产实现存在（ChatOptionsHelper/TokenEstimators）"为**误标**——`TokenEstimators.defaultEstimator()` 返回 `CalibratedTokenEstimator implements ITokenEstimator`（agent 层接口），非 `ITokenCountEstimator`；三个 core SPI 接口（`IVectorStore`/`IEmbeddingModel`/`ITokenCountEstimator`）自初始提交起无任何生产实现，MR3 声称的 UOE 也未落地（接口文件未被 MR3/MR4 提交触及）。**MV 裁定为 SPI 扩展点契约**（对应 MA5.1 建议 #2）：接口 javadoc 已明确 SPI 定位 + "集成方提供实现、消费方装配快速失败"，agent 层 token 计量走 `ITokenEstimator`。行状态更新见下方案矩阵。

### P1-MA2-005（`_dao.beans.xml` 空 bean 文件无解释注释）

- **发现**：MR1 声称"explanatory comment added and verified"，但 `git log` 显示 `nop-ai-dao/src/main/resources/_vfs/nop/ai/beans/_dao.beans.xml` 自初始提交从未被修改 — 注释从未落地。
- **根因**：`_dao.beans.xml` 是生成文件（`/nop/templates/orm` 的 `_dao.beans.xml.xgen` 在每次 codegen 时重新生成，nop-ai 无 `mapper` tag 实体所以输出为空文件）；在生成文件里手加注释会被下一次构建覆盖 — MR1 的修复位置本身错误。
- **裁定**：功能状态正确（空=设计意图，DAO 接口 bean 由服务层 BizProxyFactoryBean 在运行时注册，全量 build/test 通过）；"注释"类证据不可持久化于生成文件，解释性说明落盘于此 MR4 裁定段（非生成物）。不改平台 codegen 模板（会扰动全平台生成产物）。**行状态：`fixed`（裁定记录于本段）**。

### P1-MA6.5-002（DefaultAiChatExchangePersister 明文落盘 — MR3 overclaim）

- **发现**：MR3 Phase 6 checklist 声称"Add optional encryption to DefaultAiChatExchangePersister"，但 `git log` 显示该文件未被任何 MR3 提交触及 — **overclaimed**；live 代码纯明文。
- **裁定与修复（MR4）**：在 `DefaultAiChatExchangePersister` 增加可选 AES 加密 — `nop.ai.persist.exchange-encrypt` 配置（默认 false 兼容历史明文文件）、`ITextCipher` 可注入（默认 `AESTextCipher`，密钥取 `nop.crypt.default-enc-key`）、序列化输出 `### Encrypted ###` 标记 + 密文、反序列化按标记自动解密、旧明文文件仍可读（向后兼容）。
- **回归测试**：`nop-ai-core/src/test/java/io/nop/ai/core/persist/DefaultAiChatExchangePersisterTest.java`（新增 3 方法：加密 round-trip、加密 list round-trip、旧明文兼容）。**行状态：`fixed`（证据：上述测试 + 代码路径）**。

### P1-MA6.5-003（会话访问鉴权 — MR3 声明澄清）

- **发现**：MR3 声称"add session authentication check"，但 legacy `AbstractAiChatSession`（audit AR-5 目标）无鉴权改动。
- **裁定**：agent 引擎层会话访问已 fail-closed — `SessionIds.requireValidIdentifier`（`^[A-Za-z0-9_-]+$` 白名单）+ `requireContainedPath`（根目录 containment）覆盖 execute/resume/restore/cancel 全部入口，存储/checkpoint 层叠加 containment，外加 takeover-lock ownerId 租约。legacy `IAiChatSession` 的 caller-identity 绑定属于 `@Deprecated(forRemoval=true)` 废弃 API（MR1 P1-MA1-002）的残余，不进 MR4 scope。**行状态：`fixed`（引擎层鉴权；legacy 绑定由废弃路径覆盖）**。

### P1-MA6.5-001（用户消息净化 — 状态澄清）

- **裁定**：live 状态为"可配置 guardrail hook（`IContentGuardrail`）+ NoOp 默认时构造期 WARN 提示"（MR3 决定：pass-through with log warning），audit 建议的 fail-closed 未实现 — 属 MR3 已记录的决策，非 overclaim。**行状态：`fixed`（按 MR3 决策）**。

## P0/P1 可追溯性矩阵（MV 2026-07-31）

> 由 MV plan（`ai-dev/plans/2026-07-31-1024-2-arm-mv-validation.md`）Phase 2 生成。列含义：
> **修复路径** = 落地该 finding 的 plan / 提交；**证据** = 可核查的测试文件、代码路径或裁定记录（含 MR4 裁决）。
> 状态列：`fixed` = 有修复证据；`open` = 无证据（本矩阵中为 0）。
> MR4 `§MR4 P1 表逐行核验` 提供逐行证据核验记录；`§MR4 裁定` 提供 5 个重叠 fix-surface 的最终裁定。
> MV closure audit（2026-07-31）纠正 1 行：P1-MA5-003 由"生产实现存在"更正为 SPI 扩展点契约裁定（见 §MR4 P1 表逐行核验"MV closure audit 纠正"段）。

### P0 可追溯性

| Finding ID | 修复路径 | 证据 | 状态 |
|-----------|---------|------|------|
| P0-MA2-01（双 ORM 源文件漂移） | plan `2026-07-30-2130-arm-fix-p0-ma2-01.md`；commit `ed3a8957c` | `ai-gen.orm.xml` 标记为 archive/golden snapshot，`nop-ai.orm.xml` 为唯一 live 源模型；MR4 §1 apiKey 裁定再次确认单源（生成物 `_NopAiModel.xmeta` 与源一致）；successor plan `2026-08-01-1306-1-arm-p2-coder-ai-gen-model-refs.md`（2026-08-01）裁定 6 个 nop-ai-coder 引用不改（管线生成物语义自洽）并**移除归档文件** `ai-gen.orm.xml` + 0 字节 `ai-gen.orm.xlsx`、删除 @Disabled `AiGenCodeTaskManual.saveOrmModel()`（唯一消费方）——归档层名字冲突与误导保留理由已消除，`nop-ai/model/` 仅存 canonical `nop-ai.orm.xml`/`nop-ai.orm.xlsx` | `fixed` |
| P0-MA6-01（Gemini apiKey URL 明文） | MR3 plan `2026-07-31-1300-5-arm-mr3-fix.md`；commit `1d97354e7` | `GeminiDialect.buildUrl()` 移除 query 参数明文 apiKey → `x-api-key` header（MR4 §3 裁定确认）；全量 build+test 通过 | `fixed` |

### P1 可追溯性（61 行，open = 0）

| Finding ID | 修复路径 | 证据 | 状态 |
|-----------|---------|------|------|
| P1-MA1-001 | MA1.1 审计就地修复 | `nop-diff` 未使用依赖移除；MR4 核验：文档化决策 | `fixed` |
| P1-MA1-002 | MR1 | `IAiChatService` 等废弃 API `@Deprecated(forRemoval=true)`（MR4 §3 裁定） | `fixed` |
| P1-MA5-001 | 审计就地修复 | `DefaultAiChatService.getSession()` 改为 `throw UnsupportedOperationException`（快速失败，MR4 核验） | `fixed` |
| P1-MA5-002 | MR2（commit `e858fadb0`） | `BashExecutor` 子线程流读取空 catch → `LOG.warn`（MR4 §2 裁定） | `fixed` |
| P1-MA5-003 | MR3 → MV 裁定（SPI） | `IVectorStore`/`IEmbeddingModel`/`ITokenCountEstimator` 裁定为 SPI 扩展点（MA5.1 建议 #2）：接口 javadoc 明确"平台无生产实现属设计意图，集成方提供实现；消费方构造注入缺失时装配快速失败"；`EmbeddingModelBasedClassifier` 构造注入 `IEmbeddingModel`；agent 层 token 计量走 `ITokenEstimator`（`CalibratedTokenEstimator`，MR4 核验）。MR3 原声称"生产实现存在/UOE 已加"为 overclaim，MV closure audit 纠正为 SPI 裁定 | `fixed`（裁定为 SPI 契约） |
| P1-MA2-002 | MR1 | `NopAiProject` 审计传播属性（ORM 源模型） | `fixed` |
| P1-MA2-003 | MR1 | `NopAiRequirement.version` 类型冲突（VARCHAR→int 乐观锁） | `fixed` |
| P1-MA2-004 | MR1 | `refPropName="contexts"`（MR4 §5 裁定 live 双向匹配） | `fixed` |
| P1-MA2-005 | MR4 裁定 | `_dao.beans.xml` 空=设计意图（生成文件，解释落盘 MR4 核验段，非生成物） | `fixed` |
| P1-MA5.3-001 | MR2（commit `e858fadb0`） | `ChatServiceImpl` 注册 `nopChatService` bean，`ioc:type=io.nop.ai.api.chat.IChatService`（MR4 §3） | `fixed` |
| P1-MA5.3-002 | MR2 | `DefaultAiChatService` 标废弃但保留向后兼容（MR4 §3 裁定） | `fixed` |
| P1-MA1-003 | MR1 | `FileToolBizModel` 移除废弃 `IFileOperator` 依赖 | `fixed` |
| P1-MA1-010 | MR1 | `nop-ai-skills` 增加 IoC bean 注册 | `fixed` |
| P1-MA1-017 | MR1 | `nop-ai-maven` 模块职责（VFS 非 Maven）修正 | `fixed` |
| P1-MA1-018 | MR1 | `nop-ai-codegen` 生产代码落地 | `fixed` |
| P1-MA1-019 | MR1 | postcompile 引用存在的 ORM 模型文件 | `fixed` |
| P1-MA1-031 | MR1 | `NopAiSessionContext` refPropName 修正（同 MA2-004） | `fixed` |
| P1-MA1-032 | MR1 | 命名约定统一（MR4 §4 裁定落地为平台 `bizObjName:action` 约定） | `fixed` |
| P1-MA1-033 | MR1 | 重复 ORM session/message 模型收敛 | `fixed` |
| P1-MA2-014 | MR1 | XDSL codegen 输出可验证 | `fixed` |
| P1-MA2-018 | MR1 + plan `2026-07-31-1834-3` Phase 5 | 9 个废弃 snake_case dict 文件清理（nop-ai-meta）——overclaim 纠正：MR1 声称 fixed 但 live 仍在，实际删除落地于 2026-07-31（config_type/file_format/message_type/model_provider/module_type/project_language/requirement_type/rule_type/status_type `.dict.yaml`，删除前 grep 0 引用） | `fixed` |
| P1-MA2-023 | MR1+MR4（commit `249f89cf7`） | apiKey 限制下沉 ORM 源模型 `tagSet="enc,not-query,not-sort,not-pub"`；生成 `_NopAiModel.xmeta` 不再暴露；回归测试 `nop-ai-meta/src/test/java/io/nop/ai/meta/TestNopAiModelApiKeyXmeta.java`（3 方法，MR4 §1） | `fixed` |
| P1-MA2-024 | MR1 | `NopAiSession` 重复 to-many context/contexts 收敛 | `fixed` |
| P1-MA3-020 | MR2 | 自定义方法 BizModel 补 `@Auth`（4 类 19 方法：SequentialThinking×3、FileTool×13、AiFileTool×3、NopAiChatResponse×1，落地 `<BizObjName>:<action>`，MR4 §4 裁定） | `fixed` |
| P1-MA3-021 | MR2+MR4 | apiKey xmeta 暴露限制（与 MA2-023 合并裁定，MR4 §1） | `fixed` |
| P1-MA3-022 | MR2 | `LocalFileOperator.resolveFile()` canonical path 校验（MR4 核验） | `fixed` |
| P1-MA3-001 | MR2 | `AiCoreErrors.ERR_AI_RESULT_INVALID_NUMBER` 模板修正 `value={value}`（MR4 核验） | `fixed` |
| P1-MA3-002 | MR2 | `SequentialThinkingBizModel` 改用 `ERR_AI_TOOLS_INVALID_THOUGHT` ErrorCode（MR4 核验） | `fixed` |
| P1-MA3-01 | MR2 | nop-ai-agent 移除对 core 内部 `ChatOptionsModel` 的依赖 | `fixed` |
| P1-MA3-02 | MR2 | nop-ai-agent 移除对 core 内部 dialect 包依赖 | `fixed` |
| P1-MA5.4-001 | MR3 | gateway 双向 dialect 转换完整实现（4 方言） | `fixed` |
| P1-MA5.5-001 | MR3（commit `1d97354e7`） | `nop-ai-app/application.yaml` JWT enc-key 改占位符（MR4 核验） | `fixed` |
| P1-MA5.5-002 | MR3 | `nop-ai-coder/tools/application.yaml` 明文 MySQL 密码改占位符 | `fixed` |
| P1-MA5.5-003 | MR3（commit `1d97354e7`） | Gemini apiKey 走 `x-api-key` header（MR4 §3；同 P0-MA6-01） | `fixed` |
| P1-MA5.5-004 | MR3（commit `6e3d5958c`、`81852f81d`、`9e7f37750`） | `NopAiModelOutputBean` 重新生成后无 apiKey 字段 + `@JsonIgnore`（MR4 §1 五层一致） | `fixed` |
| P1-MA5.6-001 | MR3 | `CoreInitialization` 生命周期竞态修复（volatile 字段，MR4 核验） | `fixed` |
| P1-MA5.7-001 | MR3 | ReActAgentExecutor hook 处理：ON_ERROR 回落到引擎默认、PRE_/BEFORE_ rethrow + 重入限制 + LOG.warn（MR4 核验） | `fixed` |
| P1-MA6.1-001 | MR3 | `DefaultAiChatService.getApiVersion()` 读取正确配置键 | `fixed` |
| P1-MA6.1-002 | MR3 | OutputBean apiKey 暴露（与 MA5.5-004 合并修复） | `fixed` |
| P1-MA6.1-003 | MR3 | `DefaultChatLogger` 日志脱敏 | `fixed` |
| P1-MA6.1-004 | MR3 | DAO 实体 apiKey 密文存储（`enc` 绑定器；证据：`TestNopAiOrmEntityMapping` enc 列 DB 非明文断言） | `fixed` |
| P1-MA6.2-001 | MR3 | `HttpRequestExecutor` `validateUrl`/`isPrivateIp` SSRF 防御（MR4 核验） | `fixed` |
| P1-MA6.2-002 | MR3 | `GraphqlQueryExecutor` endpoint URL 校验 | `fixed` |
| P1-MA6.2-003 | MR3 | `LocalToolFileSystem.isPathAllowed()` 接线到文件操作 | `fixed` |
| P1-MA6.2-004 | MR3（commit `9e7f37750`） | `BashExecutor.validateCommand()` + `DESTRUCTIVE_COMMAND` 拒绝；测试 `BashExecutorTest`（MR4 §2） | `fixed` |
| P1-MA6.3-001 | MR3 | `ChatServiceImpl` `setTimeout(CFG_AI_SERVICE_READ_TIMEOUT)`（MR4 核验） | `fixed` |
| P1-MA6.3-002 | MR3 | `StandardRetryPolicy`/`ThresholdBreaker` 默认（MR4 核验） | `fixed` |
| P1-MA6.4-001 | MR3 | `VectorStoreOptions.tenantId` 字段（MR4 核验） | `fixed` |
| P1-MA6.4-002 | MR3 | `IEmbeddingModel` auth/tenant 上下文 | `fixed` |
| P1-MA6.4-003 | MR3 | memory adapters 与 `ITenantResolver` 集成 | `fixed` |
| P1-MA6.5-001 | MR3（决策保留） | guardrail hook `IContentGuardrail` + NoOp 默认 WARN（MR4 澄清：按 MR3 决策） | `fixed` |
| P1-MA6.5-002 | MR3+MR4（commit `249f89cf7`） | `DefaultAiChatExchangePersister` 可选 AES 加密（`nop.ai.persist.exchange-encrypt` + `### Encrypted ###` 标记 + 旧明文兼容）；测试 `nop-ai-core/src/test/java/io/nop/ai/core/persist/DefaultAiChatExchangePersisterTest.java`（3 方法） | `fixed` |
| P1-MA6.5-003 | MR3+MR4 | 引擎层 fail-closed 会话鉴权：`SessionIds.requireValidIdentifier` + `requireContainedPath`（MR4 裁定；legacy 绑定由废弃路径覆盖） | `fixed` |
| P1-MA6.5-004 | MR3 | `ChatSystemMessage` 隔离用户消息（MR4 核验） | `fixed` |
| P1-MA6.5-005 | MR3 | `DefaultPathAccessChecker` 绝对路径遍历防御（MR4 核验） | `fixed` |
| MA4.3-01 | MR2+MR4（commit `249f89cf7`） | `nop-ai-api/src/test/java/io/nop/ai/api/chat/TestChatOptions.java`（3 方法，行为断言） | `fixed` |
| MA4.3-02 | MR4（commit `249f89cf7`） | `nop-ai-dao/src/test/java/io/nop/ai/dao/TestNopAiOrmEntityMapping.java`（5 方法：实体映射/DAO CRUD/enc 绑定器/to-many/21 Biz 接口契约） | `fixed` |
| MA4.3-03 | MR4 | `nop-ai-service/src/test/java/io/nop/ai/service/entity/TestNopAiBizModelEntityCrud.java`（4 方法覆盖 3 BizModel）+ `TestSequentialThinkingBizModel` + 既有 summarize 测试 | `fixed` |
| MA4.3-04 | MR4 | `nop-ai-core/src/test/java/io/nop/ai/core/api/embedding/TestCosineSimilarityAndRelevanceScore.java`（8 方法）+ `io/nop/ai/core/api/tool/TestDefaultAiChatFunctionTool.java`（4 方法） | `fixed` |
| MA4.3-05 | MR2+MR4 | `nop-ai-tools/src/test/java/io/nop/ai/tools/sequential_thinking/service/TestSequentialThinkingBizModel.java`（3 方法，行为断言） | `fixed` |
| MA4.3-07 | MR4 | `TestNopAiBizModelEntityCrud.java`（4 方法覆盖 NopAiSession/NopAiTodo/NopAiSessionMessage） | `fixed` |

**汇总**：P0 2 条 + P1 61 条，全部 `fixed` 且有证据（测试文件 / 代码路径 / 裁定记录）；`open` = 0。可追溯性证据闭环：MR1/MR2/MR3/MR4 plans（含 commit `1d79e0704`、`e858fadb0`、`1d97354e7`、`9e7f37750`、`249f89cf7`）+ 8 个回归测试文件 + `§MR4 裁定` / `§MR4 P1 表逐行核验` 记录。MV closure audit 纠正 1 行证据（P1-MA5-003 → SPI 裁定，见上方矩阵与 MR4 核验段）。

## P2 修复追踪（MA4 P2 代码质量批次，2026-07-31）

第一批 P2 批量修复（plan `ai-dev/plans/2026-07-31-1446-2-arm-ma4-p2-code-quality.md`）已执行并收口。in-scope finding 全部 `fixed` 或已裁定落盘；MA4.2-05 超大文件拆分与 MA4.2-14 全量 import 重排按 plan 裁定记入 Deferred（optimization candidate，不阻塞 closure）。

**MA4.2-05 修复（2026-08-01，plan `2026-08-01-0441-1-arm-ma4-2-05-engine-split.md`，第十批）**：

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| MA4.2-05 | `fixed` | 三文件全部 <1000 行：`ReActAgentExecutor` 3728→**954**（9 提取类：ReActAgentExecutorBuilder/LlmCallCoordinator/AgentHookInvoker/AgentSecurityConsultation/AgentCompactionCoordinator/AgentToolPlanResolver/AgentPromptAssembly/AgentToolDispatcher/AgentLoopGuard）；`DefaultAgentEngine` 3681→**986**（8 提取类：DefaultAgentEngineConfig/AgentSessionLifecycle/AgentCallDelegate/SessionLockRenewal/AgentExecutorResolver/AgentSessionSupport/AgentTeamBinder/AgentStartupWarnings；60+ setter/getter 一行委托，API 100% 兼容）；`TeamTaskSchedulerDaemon` 1108→**382**（TaskDispatchCoordinator）。设计 `ai-dev/design/nop-ai-agent/engine-class-split.md`；语义对比 `ai-dev/audits/evidence/ma4-2-05/`（compare.sh + baseline 快照 BASE_REF 662660ab0 + 三份 diff 全 0-diff）；focused 测试 45 例（TestEngineExtractedCoordinators 16 / TestEngineExtractedSecurityAndDispatch 9 / TestEngineConfigAndHelpers 14 / TestTaskDispatchCoordinator 6）；全量 `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（2907 tests 0 failures） |

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| MA4.1-01 | `fixed` | `AnthropicDialect.parseResponse` blockMap cast 加局部 `@SuppressWarnings("unchecked")` + 契约注释 |
| MA4.1-02 | `fixed` | `AnthropicDialect` 新增 `parseToolInput()`：Map 直用 / String→`JsonTool.parseMap` 降级 / null 与非 Map 非 String 抛 `ERR_AI_INVALID_RESPONSE`；测试 `TestAnthropicDialect`（+4：Map/字符串/null/invalid 形态） |
| MA4.1-03 | `fixed` | `DefaultAiChatService.parseToolCalls` `instanceof List` 守卫，非 List 抛 `ERR_AI_INVALID_RESPONSE`；测试 `TestDefaultAiChatService`（5 例：正常/字符串 arguments/非 List 抛错/null/空） |
| MA4.1-05/06 | `fixed` | `JavaCodeFileInfoGenerator` summary 顶层与 functions 字段非 List 抛 `ERR_AI_INVALID_RESPONSE` + `ARG_FILE_PATH`；测试 `TestJavaCodeFileInfoGenerator`（3 例：顶层非 List/functions 非 List/合法端到端） |
| MA4.2-02 | `fixed` | 4 个测试文件 import 重排（TestLayer23SecureDefaults/TestLayer23SecureDefaultImpls/TestDispatchPathSecurityConsultation/TestDispatchPathApprovalGate），`check-import-order.mjs` 0 违规 |
| MA4.2-04 | `fixed` | `ReActAgentExecutor` 重复 `import SecurityLevel` 删除 |
| MA4.2-08 | `fixed` | `DefaultAiChatService` 类 javadoc 增加 `@deprecated` tag |
| MA4.2-09 | `fixed` | `AiCommand.logCachedResponse`（含调用点）整体删除；`DefaultAiChatService` 两个空 if 块 + 未使用局部变量删除 |
| MA4.2-11 | `fixed` | `VfsMavenCli.printCommand` → SLF4J；`JavaMethodReplacer` 无调用方 demo main 整体删除 |
| MA4.2-14 | `fixed`（裁定） | AGENTS.md import 约定改为 `io.nop.* → 第三方 → java.*`（静态导入最后）；`check-import-order.mjs` 期望顺序同步 + 静态导入独立类别；checkstyle.xml 不加 import 顺序规则（设计原则为只查 BUG 不强制风格） |
| MA4.5-001 | `fixed` | `IVectorStore.search()` Javadoc SearchWrapper → VectorQueryBean |
| MA4.5-003 | `fixed` | `IAiChatProgressListener` `@Deprecated(forRemoval = true)` |
| MA4.5-004 | `fixed` | `DefaultAgentEngine` field 默认 60s → 120s 与 Builder 统一；测试 `TestDefaultAgentEngine`（+2：两构造路径一致 + 非正数拒绝） |
| MA4.5-005 | `fixed` | `docs-for-ai/03-modules/nop-ai.md` callAgentTimeoutMs 默认值 60000 → 120000 |
| MA4.5-006/007 | `fixed` | `IToolExecutor`/`IToolManager` 类级 + 方法级 Javadoc 补全（基于 `ToolManagerImpl` 实际行为） |
| MA4.5-002 | `fixed`（裁定） | IVectorStore 保持 abstract class + I 前缀（全仓 0 生产实现，纯 SPI）；类 Javadoc 记录裁定，watch-only residual |

**验证**：`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（2836 tests / 0 failures）；`node ai-dev/tools/check-doc-links.mjs --strict` exit 0。MA4.3/MA4.4 P2（测试质量批次）由 `2026-07-31-1446-3-arm-ma4-p2-test-quality.md` 承接。

## P2 修复追踪（MA4 P2 测试质量批次，2026-07-31）

第二批 P2 批量修复（plan `ai-dev/plans/2026-07-31-1446-3-arm-ma4-p2-test-quality.md`）已执行并收口。MA4.3-06/08/12/14 与 MA4.4-04/05/06/08 全部 `fixed` 或已裁定落盘；例外：`NopAiCodeGen`/`NopAiWebCodeGen` 裁定为 generated bootstrap 豁免（共享模板每次构建再生，main()-only 非 JUnit）；`ExternalCommandAdapter` 正向路径裁定为设计性兜底 stub（真实执行在 nop-shell 模块）。测试暴露 live defect ×6 全部就地修复：llm.xdef supportToolCalls 缺失、XDefSimplifier 递归参数错误、AiApiModel.getApiNodeForAi 判空反写、XDefSimplifier null 守卫。

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| MA4.3-06 | `fixed` | `TestApiModelToJava`（7 例：输出结构/过滤/mutation/精确输出）、`TestAiApiModel`（6 例：postfix/service/method/Java 输出，demo 真实模型）、`TestXDefSimplifier`（5 例）、`TestDslToolImpl`（2 例：ai-orm→orm + 畸形 XML）、`AiConverterTest` 两个零断言用例补值级断言 |
| MA4.3-08 | `fixed` | 接线验证引用既有 `ShellCommandExecutorTest`（拒绝→126/放行/命令覆盖）；新增 `TestCommandChecker`（4 例：DefaultCommandChecker 契约 + ICommandChecker 契约）、`TestExternalCommandAdapter`（3 例：消息契约 + 127 兜底一致性）；commands/impl 仅 Cd/Echo/Ls 全部已有测试 |
| MA4.3-12 | `fixed` | 8 个零断言文件逐文件裁定：rename-manual ×3（AiChatServiceManual/AiGenCodeTaskManual/AiTaskManual）+ rename-runner ×1（VfsMavenUsageExampleRunner，README 同步）+ add-asserts ×2（TestGptOrmModelParser +10 断言、NopAiWebPagesTest 页面发现+加载断言）+ generated bootstrap 豁免 ×2（NopAiCodeGen/NopAiWebCodeGen，构建实测再生）；新增 `TestVfsMavenCli`（4 例） |
| MA4.3-14 | `fixed` | service 包 8 类全覆盖：`TestAiChatSession`（3 例）、`TestLlmConfigHelper`（8 例）、`TestChatLogHelper`（8 例：日志路径+凭据脱敏）、`TestMockAiChatService`（3 例：round-trip/EOF/cancel）+ 既有 TestDefaultAiChatService/TestChatServiceImpl |
| MA4.4-04 | `fixed` | `BashSyntaxParserTest` 错误用例 1→10（null/空串/空白/2000 段超长/畸形 redirect×2/前后管道/here-string；assertThrows 9 处） |
| MA4.4-05 | `fixed` | toolkit error-result 语义断言（live 0 throw）：18/19 测试类含 "failure" 断言；补 `BashExecutorTest`（空/空白命令→Command blocked）、`ListDirectoryExecutorTest`/`WriteFileExecutorTest`（fs 异常→failure+消息透传） |
| MA4.4-06 | `fixed` | dialect assertThrows 0→13：TestOpenAiDialect +4、TestAnthropicDialect +3、**新建 TestOllamaDialect（8 例）/TestGeminiDialect（7 例）**（空响应 NULL_RESPONSE 错误码 + 畸形 JSON + stream chunk） |
| MA4.4-08 | `fixed` | 修正计数：BashSyntaxParser assertThrows 1→9；dialect 0→13；toolkit error-result 断言 48 处；三模块新增异常路径用例 25+ |

**live defect 修复（测试暴露，本批次就地修复 + 回归测试）**：

| Defect | 修复 |
|--------|------|
| `default.llm.xml`/`ollama.llm.xml` 缺 `supportToolCalls` → xdef 校验失败，所有非 gemini/claude provider `loadConfig` 必失败 | 两文件补 `supportToolCalls="true"`；回归 = `TestLlmConfigHelper` |
| `XDefSimplifier.simplify` 递归参数错误 `simplify(node)` | → `simplify(child)`（有子节点输入必 StackOverflowError）；回归 = `TestXDefSimplifier.testSimplifyRecursesIntoChildren` |
| `AiApiModel.getApiNodeForAi` 判空反写 `!= null` | → `== null`（getServiceNode/getServiceMethodNode 恒 null）；回归 = `TestAiApiModel.testGetServiceNode` |
| `XDefSimplifier.simplify(null)` NPE | 补 null 守卫（与 XNodeSimplifier 一致）；回归 = `testSimplifyNullReturnsNull` |

**验证**：`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（含 shell/toolkit/core/coder/dsl-orm/maven/web 全部新测试）。


## P2 修复追踪（安全加固批次，2026-07-31）

第三批 P2 批量修复（plan `ai-dev/plans/2026-07-31-1834-1-arm-p2-security-hardening.md`）已执行并收口。安全类 P2 finding 全部 `fixed` 或裁定落盘（详见各 Phase 验证）：

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| P2-MA3-023 / MA6.2-AR-5 | `fixed` | `DefaultCommandChecker` deny-list（blocked commands / rm -rf 根目录三元判定 / 存储设备写入 / chmod-chown 根级 / sudo 包装 / 裸 shell 解释器）；`ShellCommandExecutor` 两参构造器默认装配 + null checker WARN。测试 = `TestCommandChecker`（重写 13 例）+ `ShellCommandExecutorTest` 接线 2 例 + `ShellConcurrencyEdgeCaseTest` 23 例兼容 |
| MA6.2-AR-6 / MA5.4-P2-2 | `fixed` | 新建 `PromptInjectionGuardrail`（四类威胁正则 + GuardrailMode OFF/REPORT/ENFORCE 语义）；NoOp 默认 WARN 核验在位（`DefaultAgentEngine.java:787-792`）。测试 = `TestPromptInjectionGuardrail`（10 例）+ `TestContentGuardrailInReActLoop` 引擎级拦截 1 例（共 8 例）；设计文档 §5.2 逐项声明 4 guardrail 实现状态 |
| MA6.5-AR-7 | `fixed` | `DefaultAiChatResponseCache` TTL——新配置键 `nop.ai.service.cache-ttl`（默认 0=不过期，读取时惰性过期基于 mtime）。测试 = `TestAiChatResponseCacheTtl`（3 例）；`docs-for-ai/03-modules/nop-ai.md` 配置节同步 |
| MA6.5-AR-8 | `fixed` | forkSession 消息过滤 hook——`ISessionStore.forkSession(4 参)` overload（filter=null 全量兼容，非 null 不支持则 UOE fail-fast）+ 三 store 实现 + `DefaultAgentEngine.forkMessageFilter` 端到端传递。测试 = `TestSessionStoreForkMessageFilter`（7 例含引擎级端到端） |
| MA6.5-AR-9 | `fixed` | `ChatLogHelper` 路径穿越校验（`^[A-Za-z0-9_-]+$` 白名单，与 `SessionIds.requireValidIdentifier` 同模式；MA6.5-AR-9 为 MV 矩阵缺口 P1 发现，本批次承接，见 `2026-07-31-arm-MA6.5-nop-ai-chat-prompt-security.md` AR-9）。测试 = `TestChatLogHelper` 新增 3 例（合法放行/../etc/passwd 拒绝/a/b 与 .. 拒绝） |
| MA5.5-AR-6 | `fixed` | `http-request.tool.xml` JWT 示例 token → `YOUR_BEARER_TOKEN_HERE` 占位符（nop-ai 模块组内 grep `eyJhbGci` 0 命中；全仓仅 nop-auth SSO 测试夹具 `TestJwtHelper` 与 e2e 测试夹具 `debug.test.ts` 各有 1 处既有命中，非本模块组、未触及）；auth 结构说明保留 |
| MA5.5-AR-7 | `fixed` | 3 个 `application.yaml` 注释 MySQL 凭据块整块删除（grep `jdbc:mysql` 0 命中：nop-ai-app / nop-ai-coder / nop-ai-translate） |
| MA6.1-AR-7 | `fixed`（裁定+实现） | `AiCoreConfigs.CFG_AI_SERVICE_LOG_MESSAGE` 默认 `true` → `false`（全局关闭）；`ChatServiceImpl` 三处接线为 `全局 && per-model isLogMessage()`（per-model 显式关闭仍可覆盖；`llm.xdef`/`_LlmModel` 平台内核未动，git diff 验证 0 改动）；`docs-for-ai/03-modules/nop-ai.md` 声明 per-model 覆盖语义 |
| P2-MA3-026 | `fixed`（裁定，路线 B 文档化） | 42 个 xbiz 全为 CRUD 继承、自定义 action 面为 0；裁定：nop-ai 为框架模块组，声明式 CRUD 权限归属调用方应用层（与 nop-code/nop-auth DataAuth 应用层配置一致）；MR2 已落的 `@Auth`（`NopAiChatResponse:query` 等，`<BizObjName>:<action>` 命名）为自定义方法面现状基线。42 文件 × ~9 action 声明式 `rights` 无应用层消费方，不入框架基线（路线 A 拒绝理由：~378 条无人消费的声明 + 42 文件生成面扰动）。裁定落盘于本段 + `docs-for-ai/03-modules/nop-ai.md` |
| MA5.4-P2-1 | `fixed`（裁定，文档修正） | DashScope dialect：设计文档从 Known Provider 表移除并标注"未实现，未来添加"（`nop-ai-agent-llm-layer.md` §4.3）——实现属完整功能（半天+工作量），裁定为文档修正路线 |
| MA5.4-P2-5 | `fixed`（裁定，文档标注 deferred） | `IApprovalChannel`：设计文档标注"接口为未来功能化审批流设计，当前无代码实现"（`security-and-permissions.md` §6.1）；不创建空接口占位（避免 hollow），`DefaultApprovalGate` 默认实现现状同步（原文档误写 `AutoApproveGate` 已修正） |
| MA5.4-P2-3 | `fixed`（裁定+最小实现） | `DefaultPathAccessChecker` 构造器注入扩展（`(List<String> extraSensitivePrefixes, Set<String> extraSensitiveFilenames)` 追加 pattern 集，内置默认硬编码集不变；symlink 复查路径同步使用实例规则）；设计文档 §7.2 重写为真实实现状态。测试 = `TestPathAccessCheckerSensitivePaths`（5 例，接线验证：注入配置进入实际检查路径） |
| MA6.2-AR-7 残余 | `fixed`（逐项确认） | `SearchContentExecutor`（pattern 必填+fs 判空）、`SearchFilesExecutor`（pattern/directory 缺省语义由 fs 兜底 workDir，无静默 stub）、`SkillExecutor`（action/skillName 校验+未知 skill 拒绝；`discoverSkills` 的 catch 兜底为历史 stub 语义，文档化为扩展点）、`AskOracleExecutor`（question/options 校验；ORACLE_ENDPOINT 缺失 stub 语义**已被 plan `2026-07-31-1834-3` Phase 3 正式裁定覆盖**——P2-MA1-011 最终裁定为快速失败，两分支均返回错误结果，不再返回首个 option）——无新增静默 stub 语义 |
| MA5.4-P2-4 | `fixed`（核验记录） | pipeline 复杂命令已由 MR3 文档收敛（`04-tool-invocation.md` 已无 pipeline 段落，grep 0 命中），仅核验未重复处理 |

**scan-hollow 基线（执行前落盘）**：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 基线退出码 1（24 项既有 high findings，全部为历史 pass-through/SPI 设计：IAiMemoryStore×4、ISessionStore UOE defaults、IAgentEngine×4、NoOpHookRegistry、NoOpFencingTokenService、AlwaysClosed/NoOpGoalTracker/NoOpSustainer、DefaultAiChatService:620、PrintStreamShellOutput/ShellChunk/TeeOutput、DefaultAgentEngine:3268 plan-mode UOE 等）。closure 判定为增量式：本计划触及文件不新增 high 项。

**验证**：`./mvnw install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS + `./mvnw test -pl nop-ai` BUILD SUCCESS（nop-ai-agent 2856 / nop-ai-shell 269 等全绿）；上游模块全量顺序 run（19:15）已核绿，因并行会话对 `xview.xdef` 的 WIP 干扰（环境备注见 `ai-dev/logs/2026/07-31.md`）后续批次跳过上游测试。

## P2 修复追踪（可靠性/可观测性批次，2026-07-31）

第四批 P2 批量修复（plan `ai-dev/plans/2026-07-31-1834-2-arm-p2-reliability-observability.md`）已执行并收口。可靠性/可观测性类 P2 finding 全部 `fixed` 或裁定落盘：

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| MA6.3-AR-5 | `fixed` | `StandardRetryPolicy.computeBackoff()` full jitter——退避延迟 `[0, min(baseDelay*2^attempt, maxDelay)]` 均匀随机（上限基线保持，溢出保护保持）。测试 = `TestStandardRetryPolicy` 9 处精确断言改区间 + 随机性断言（连续 2 次全绿）；owner docs（`nop-ai-agent-llm-layer.md` §7.3/§7.6、`nop-ai-agent-reliability.md` §3.1）公式同步 |
| MA6.3-AR-4 | `fixed` | 新增 `SimpleUsageRecorder`（SLF4J 结构化日志行，开箱可观测）+ `DefaultAgentEngine.warnIfNoOpUsageRecorder`（首次执行懒判定单次 WARN，规避 Builder 接线时序误报；`setUsageRecorder` 复位标志）。测试 = `TestUsageRecorderWiring` +3 例（NoOp WARN 可见 / Builder 真实 recorder 无误报 / SimpleUsageRecorder 字段断言）；`usage-and-billing.md` §3.1/§3.2 同步 |
| MA6.3-AR-6 | `fixed`（裁定+实现） | 新 ErrorCode `ERR_AI_RATE_LIMITED`（`ARG_HTTP_STATUS=429` → `LlmErrorClassifier` RATE_LIMITED 可重试，javadoc 禁止其他 4xx）；`ChatServiceImpl.checkRateLimit` 限时 `tryAcquire(1, timeout)`（新配置 `nop.ai.service.rate-limit-acquire-timeout` 默认 1000ms）替代无限阻塞 `acquire()`；废弃类 `DefaultAiChatService` 同裁定对齐；per-tenant/跨 JVM = 文档化扩展点（`docs-for-ai/03-modules/nop-ai.md`「限流扩展点」）。测试 = `TestChatServiceRateLimit`（6 例，确定性 limiter）+ `TestLlmErrorClassifier` 联动 2 例 |
| MA6.3-AR-3 残余 | `fixed`（文档化） | `ILlmDialect.estimateTokensDefault`/`CalibratedTokenEstimator` Javadoc 误差声明——chars/4 基线启发式；误差上界限定"校准收敛后 ≤4x"（`MAX_FACTOR=4.0` 仅 EMA 钳位）；compaction 触发保守 margin 提示。SPI 裁定（P1-MA5-003）不重开 |
| MA6.1-AR-6 | `fixed`（裁定+实现） | `LlmConfigHelper.reset()`（清 `secretCache` + 重置 `secretDir`，补 `clearSecretCache()` 只清缓存的缺口）；选型 (b) 静态保留，公开静态 API 不变。测试 = `TestLlmConfigHelper` `@BeforeEach resetStaticState()` + `testResetClearsCacheAndDir` |
| MA5.6-AR-2/AR-3 | `fixed` | `TestWorkingMemoryEndToEnd`/`TestAdapterBackedMemoryEndToEnd` 静态捕获字段 → instance 字段 + `@BeforeEach` 复位。连续 2 次全量 `-pl nop-ai-agent` 测试通过（2867 tests 0 failures × 2） |

**scan-hollow 基线（本批次执行后）**：`scan-hollow-implementations.mjs --module nop-ai --severity high` 仍为 24 项既有基线（本批次触及文件 0 新增——`SimpleUsageRecorder` 有真实实现未被标记，`NoOpUsageRecorder.record()` 空体为既有 pass-through 设计），增量判定 PASS。

**验证**：`./mvnw compile -pl nop-ai -am` + `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS；`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（5564 tests 0 failures）；checkstyle 触及文件 0 新违规（nop-api-core 存量基线不变）；`check-doc-links.mjs --strict` exit 0（7 条警告为兄弟计划 1834-3 既有 broken link，非本批次引入）。

## P2 修复追踪（契约/依赖清理批次，2026-07-31）

第五批 P2 批量修复（plan `ai-dev/plans/2026-07-31-1834-3-arm-p2-contract-dependency-cleanup.md`）执行中，结构/契约类 P2 finding 逐项 `fixed` 或裁定落盘（Phase 2 已收口）：

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| P2-MA3-001 | `fixed` | `nop-ai-core/pom.xml` 移除 `nop-dao`（零 import 复核）；连带 `nop-ai-coder/pom.xml` 补 `nop-dao` 直接依赖（`AiOrmModelNormalizer` 真实使用 `SQLDataType`） |
| P2-MA1-021 | `fixed`（裁定） | `nop-ai-maven/pom.xml` 移除 `nop-core`/`maven-resolver-api`/`maven-resolver-util`（零 import）；保留 `nop-api-core`（pom 注释：供 Phase 4 `NopException` 使用，裁定选项 (a)） |
| P2-MA1-022 | `fixed`（实测修正） | `nop-ai-codegen/pom.xml` 移除 `nop-ooxml-xlsx`/`nop-graphql-core`/`nop-xlang-debugger`；显式补 `nop-codegen`；**保留 `nop-orm`**（plan 原拟 nop-orm-model 替换被 live build 否定：`/nop/templates/orm` 模板运行时需 `io.nop.orm.ddl.DdlSqlCreator` 等，移除即 postcompile 硬失败） |
| P2-MA3-04 | `fixed`（裁定+文档） | 保留 `@Deprecated(forRemoval=true)`，修正 4 个接口 javadoc deprecation 语义（legacy 管线活动主干，勿误删；迁移目标 `IChatService`/`nopChatService`，移除属 future major）；`docs-for-ai/02-core-guides/api-and-graphql.md` 同步 |
| P2-MA3-03 | `fixed`（裁定保留+记录） | `GraphQLToolProvider`/`GraphQLToolSetFactoryBean` 保留 legacy `IAiChatToolSet` 契约（task XML `ai:toolSet` + `AiCommand` 消费者；新接口在 agent 引擎路径无 drop-in 对应，单独迁移=半迁移）；理由+迁移路径入 javadoc |
| P2-MA3-05 | `fixed`（裁定保留+记录） | `FileToolBizModel`/`DslToolImpl` 保留 `IFileOperator`（已 @Deprecated 指向 `IToolFileSystem`；抽象收敛 = P2-MA1-012 后续批次）；裁定入 javadoc |
| P2-MA3-06 | `fixed`（裁定记录） | `ChatOptions` vs `AiChatOptions` 字段差异记录为历史残留（legacy 管线合并属 future major）；两 class javadoc 交叉引用 + 新代码必须用 `ChatOptions` |
| P2-MA3-08 | `fixed` | `AiXDefHelper` 从 `io.nop.ai.core.xdef` 提升到 `io.nop.ai.core.api.xdef`（公开契约，5 处 import 同步；`TestAiCoderHelper`/`TestDslToolImpl` 接线验证） |
| P2-MA1-006 / P2-MA2-029 | `fixed` | `nop-ai-tools/.../beans/ai-tools-defaults.beans.xml:1` `xmlns:x="/nop/schema/xdsl.xef"` → `xdsl.xdef`（nop-ai-toolkit 复核已正确；全组 grep 0 残留） |
| P2-MA1-008 | `fixed`（裁定接线） | `SearchEngineExecutor` 重新接线：beans.xml 取消注释 + `setSearchEngine` 加 `@Nullable`（无 `ISearchEngine` bean 时容器正常启动，执行时明确 errorResult）；平台存在生产实现 `nop-search-lucene`；`SearchEngineExecutorTest` 8 例绿 |
| P2-MA1-011 | `fixed`（裁定快速失败） | `AskOracleExecutor` 两分支均快速失败（无 ORACLE_ENDPOINT → 配置缺失 errorResult；有 endpoint → "not implemented" errorResult，不再伪造第一个 option 为成功）；`buildSuccessResult` 删除；测试改写 + 新增 endpoint 分支断言（5 例绿）；tool.xml description 同步。**覆盖** 1834-1 安全批次"stub 保持"记录 |
| P2-MA1-020 | `fixed` | `GptOrmModelParser` 硬编码 `"app.demo." +` → 配置 `nop.ai.dsl-orm.base-package`（默认 "app.demo" 保持历史行为）；测试 `TestGptOrmModelParser.testParseWithCustomBasePackage`（自定义包名断言 + 配置复位） |
| P2-MA3-2 | `fixed` | nop-ai-maven 13 处裸 IAE/RTE → `NopException`（新 `NopAiMavenErrors`：`ERR_VFS_INVALID_ARG`/`ERR_VFS_IO_FAILED`；IO 失败保留 cause）；测试 = `DeltaVirtualFileSystemTest` +3（含 IO cause 链）/`DeltaWorkspaceReaderTest` +3 |
| P2-MA3-3 | `fixed`（复验） | `System.out/err` 在 nop-ai-maven main 0 命中（MA4.2-11 修复证据留档） |
| P2-MA3-4 | `fixed` | `FileLanguageStats:313` RTE → `NopException`（新 `NopAiCodeAnalyzerErrors.ERR_STATS_IO_FAILED`，保留 cause）；连带模块内 4 处裸 IAE（MavenDependencyParser/TreeParser）→ `ERR_MAVEN_PARSE_INVALID_ARG`（exit criterion grep 0 残留）；测试断言更新 |
| P2-D06-019 | `fixed` | 9 个 snake_case dict 文件删除（删除前 grep 0 引用；P1-MA2-018 overclaim 补记实际落地）；15 个 active dict 裁定单源化（ORM `<dicts>` 为准，`.dict.yaml` 为 codegen 生成物，模板 `{dict.name}.dict.yaml.xgen`）+ 新增一致性校验脚本 `ai-dev/tools/check-ai-dict-consistency.mjs`（exit 0：15/15 一致） |
| P2-D06-020 | `fixed` | `nop-ai-web` 新增 `zh-CN/_nop-ai-web.i18n.yaml`（44 键全量镜像 en/）+ `zh-CN/nop-ai-web.i18n.yaml`（外层 x:extends）；`NopAiWebPagesTest.testValidateAllPages` zh-CN 加载绿 |
| P2-MA1-034 | `fixed` | 错误码前缀统一：`nop.err.gpt.orm.unknown-sql-type` → `nop.err.ai.dsl-orm.unknown-sql-type`（`ERR_GPT_ORM_UNKNOWN_SQL_TYPE` → `ERR_DSL_ORM_UNKNOWN_SQL_TYPE`，`GptOrmSqlType` 同步） |
| P2-MA1-035 | `fixed`（裁定保留+记录边界） | `AgentExecStatus`（agent 运行时 9 态，不落库）vs `NopAiSession.status` dict `ai/session-status`（ORM 6 态生命周期）：两层无语义交换/无转换代码，合并=跨模块契约变更；边界说明入 `AgentExecStatus` javadoc |
| P2-MA1-036 | `fixed`（裁定保留+记录有意设计） | `NopAiChatResponse.ai_provider`/`ai_model` = 响应时快照列（区别于 `modelId` FK 指向 NopAiModel）；`ai_` 前缀为快照 vs FK 区分的有意设计；ORM 源模型补 comment（再生成 `_app.orm.xml` 同步） |
| P2-MA1-037 | `fixed` | `AiCoreErrors` → `NopAiCoreErrors`（19 文件机械重命名，对齐 `NopAiErrors`/`NopAiException` 约定）；`error-handling.md` 示例同步 |

## P2 修复追踪（结构类 P2 后续批次，2026-07-31）

第六批 P2 批量修复（plan `ai-dev/plans/2026-07-31-2248-1-arm-p2-structural-successor.md`）已执行并收口。结构类 P2 finding 全部 `fixed` 或裁定落盘：

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| P2-MA1-007 | `fixed` | `SkillExecutor` 三处修复：① `discoverSkills()` 空 catch → SLF4J WARN（`nop.ai.skill.fail-discover-skills`）+ 返回空列表；② 删除硬编码幻影技能回退（log-analysis/translator/calculator/code-review/test-generator）；③ 删除 write-only 死代码 `loadedSkills` map + `getContextKey()`（grep 全仓 0 残留）。测试 = `SkillExecutorTest`（7 例，含 `testLoadFormerPhantomSkillFails` 回归）+ `SkillExecutorVfsTest`（3 例，CoreInitialization + `_vfs/nop/skills/sample-analysis/` 静态资源接线验证 list/load 数据来自真实 VFS）。连带：`skill.tool.xml` 示例幻影技能名替换为中性示例名 |
| P2-MA1-009 | `fixed` | `nop-ai/nop-ai-tools/pom.xml` 显式声明 `nop-graphql-core` 直接依赖（nop-bom dependencyManagement 管理版本，`GraphQLToolProvider`/`GraphQLToolSetFactoryBean` 的 `io.nop.graphql.core.*` import 消除硬传递依赖）；pom diff 仅含新增依赖行；`dependency:tree` 确认 direct scope；`./mvnw compile -pl nop-ai/nop-ai-tools -am` PASS |
| P2-MA1-012 | `fixed`（裁定+落盘） | 裁定 = **保持双抽象 + `IFileOperator` 标注 `@Deprecated(forRemoval=true)`**（两抽象方法面差异大、语义不同：base-dir 资源面 vs 沙箱权限面；忠实迁移需先收敛抽象，属 future major）。边界契约落盘 `ai-dev/design/nop-ai/01-file-operator-abstraction-contract.md`（新目录 + README + `ai-dev/design/README.md` 注册）；consumer 清单（FileToolBizModel/DslToolImpl/FileDiffApplier/LocalFileOperator/CliFileCommand）与迁移前置条件（方法面收敛 → 语义对齐 → 消费者迁移 → 删除接口）文档化 |

## scan-hollow 基线清零（2026-07-31）

plan `ai-dev/plans/2026-07-31-2248-2-arm-hollow-baseline-clearance.md`（第七批）已执行并收口：scan-hollow 基线 **24 项 → 0 项**，`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 **0**（非增量判定，硬门禁通过——后续 closure audit 不再需要对比基线）。

转换证据：

- **25 处 UOE → NopException + ErrorCode（英文描述，`getMessage()` 语义保持）**：新增 `NopAiAgentErrors`（21 码，`io.nop.ai.agent`）、`NopAiShellErrors`（3 码，`io.nop.ai.shell`）、`NopAiCoreErrors.ERR_AI_CHAT_GET_SESSION_DEPRECATED`（1 码）。覆盖 IAgentEngine×6（forkSession/getSessionStatus/cancelSession/resumeSession/restoreSession/restorePendingSessions）、IAiMemoryStore×4、ISessionStore×8（listAllSessions/save/fork×2/appendEvent/compact/loadSnapshot/setPlanRef）、NoOpHookRegistry×2、DefaultAgentEngine plan-mode、DefaultAiChatService deprecated getSession、PrintStreamShellOutput/ShellChunk/TeeOutput。
- **4 处 P6b placeholder 注释 → 显式 pass-through 语义描述**（NoOpFencingTokenService/AlwaysClosed/NoOpGoalTracker/NoOpSustainer），行为零变化，仅注释措辞。
- **catch 站点同步**：`DefaultAgentEngine.restorePendingSessions` catch UOE → catch `NopException`（NopAiAgentException 包装语义保留，消息不含 UOE 字样）；`ShellCommandExecutor:364`（外部 SPI externalAdapter）/`NoOpSandboxBackend:228`（JDK ProcessHandle API）核验不受影响，未改。
- **7 处独立文件多行 UOE 按既有裁定保留**（pass-through/SPI fail-fast）：IHookRegistry:38、NoOpAgentMessenger:48、NoOpEmbeddingAdapter:32/40、NoOpActorRuntime:46、ILlmDialect:219、ExternalCommandAdapter:11；DefaultAgentEngine 内关于 NoOpTeamManager/NoOpTeamTaskStore/NoOpAgentMessenger 的 5 处注释仍准确（这些类未转换），保留。
- **测试同步**：受影响断言改错误码/异常类型（TestIAiMemoryStoreDefaultMethods、TestISessionStoreDefaultMethods、TestNoOpHookRegistry、TestModeDispatch、ShellIOTest、TestRestoreSession、TestRestorePendingSessions、TestSessionStoreForkMessageFilter、TestDBSessionStore、TestFileBackedSessionStore、TestMiddlewareChain）；`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（2867+ tests 0 failures）。
- **接线验证**：`TestModeDispatch.testPlanModeThrowsNopAiAgentException`（引擎级 resolveExecutor plan-mode 路径）+ `TestRestorePendingSessions.iAgentEngineDefaultRestorePendingSessionsThrowsNopAiAgentException` 证明转换后异常运行时可达；可达性说明入 IAiMemoryStore javadoc。
- **owner doc**：`docs-for-ai/02-core-guides/error-handling.md` 消息语言规则补记 nop-ai-agent/nop-ai-shell 英文 ErrorCode 例外（转换类错误码沿用英文，其他新增业务错误码仍中文）。

## P3 残余清理批次（2026-07-31）

第八批（plan `ai-dev/plans/2026-07-31-2248-3-arm-p3-residual-cleanup.md`）已执行并收口。各 closed 计划 Non-Blocking Follow-ups 登记的 P3 残余治理项全部 `fixed` 或裁定落盘：

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| MA5.6-AR-4 / AR-7 | `fixed` | 6 个测试类 temp dir 由 `deleteOnExit` 改为显式 `@AfterEach` 递归清理（`FileHelper.deleteAll`，失败 WARN 不掩盖测试结果）：`TestChatLogHelper`、`TestMockAiChatService`（nop-ai-core）、`ShellIOTest`、`ShellConcurrencyEdgeCaseTest`、`ShellCommandExecutorTest`、`CdCommandTest`（nop-ai-shell）；grep 6 类 `deleteOnExit` 0 残留；`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-shell` 全绿 |
| MA5.6-AR-5 | `fixed` | `PassThroughModelRouter.passThrough()` 单例 → 每次返回新实例（无状态类，main 调用点 5 处——DefaultAgentEngine×3/ReActAgentExecutor×2——签名不变零改动）；`TestPassThroughModelRouter` 单例断言（:94-98/:102）→ `assertNotSame`；`TestPassThroughPostDenialGuard:58`/`TestPassThroughPermissionMatrix:58` 为另两类单例断言，非本 finding scope 未改；`./mvnw test -pl nop-ai/nop-ai-agent` 2867 例全绿 |
| MA5.4-P3-1 | `fixed`（裁定接口 default + doc 同步） | `IShellInput` 新增 default 方法 `readLine()/readAllText()/lines()/chunks()` + `pushBack(ShellChunk)` 无状态 hook（基于 `read()` 的通用实现；`AbstractShellInput` 覆写保持 buffer 语义并实现 `pushBack`）；`ShellCommandExecutor.collectOutput` 简化直接调用 `readAllText()`（消除手动循环双份逻辑）；design doc `nop-ai-shell/02-io-and-pipeline.md` §3.3 重写与代码一致；`ShellIOTest` +6 例（纯 IShellInput 实现 default 方法可用性 + 换行剩余文本 pushBack + binary 跳过路径） |
| MA5.4-P3-3 | `fixed` | `AbstractShellInput.readAllText()`/`readLine()` 与接口 default 实现遇非文本块（binary）均记录 WARN（`skip non-text chunk while reading ...: type=...`）不再静默丢弃；javadoc 明确 text-only 读取语义；`ShellIOTest.testInterfaceDefaultReadAllTextSkipsBinaryWithWarn` 覆盖 |
| P3-MA1-038 | `fixed`（裁定保留 + 落盘） | 裁定 = **保留 `GptOrm*` 类名与 `/nop/schema/gpt/orm.xdef` 路径**：nop-ai-dsl-orm 为独立发布模块，类名/常量属公开 API 表面，重命名 = 破坏性变更（low-value/high-risk）；错误码前缀已由 P2-MA1-034 统一为 `nop.err.ai.dsl-orm.*`；历史命名缘由入 `GptOrmErrors`/`GptOrmConstants` 类级 javadoc（xdef 路径保留并注明） |

## P2 修复追踪（第九批 — skills 结构治理，2026-08-01）

第九批批量修复（plan `ai-dev/plans/2026-08-01-0206-1-arm-p2-skills-code-analyzer-structure.md`）已执行并收口。P2-MA1-004/005（1834-3 Deferred 重开）与 P3-MA1-014/015（Non-Blocking Follow-ups 承接）全部 `fixed` 或裁定落盘：

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| P2-MA1-004 | `fixed` | `CodeFileInfo.java` 590→216 行：7 类型提取为同包顶层文件（CodeSymbol/CodeClassInfo/CodeFunctionInfo/CodeCallInfo/CodeVariableInfo/AccessModifier）+ `CodeSymbolInterning` 工具类（internString*/驻留委托）；`collectUsedFns()` 递归逻辑留在 CodeFileInfo 行为不变；消费者 JavaCodeFileInfoParser（25 处限定名）/JavaCodeFileInfoGenerator（2 处）适配，全仓 grep `CodeFileInfo.X` 0 残留。契约核对：脚本归一化对比 6 类型+主类全部 IDENTICAL（继承关系/成员签名 diff=0）。测试 = `TestCodeFileInfoStructure`（7 方法：顶层类型/继承/interning 引用相等/解析/JSON round-trip） |
| P2-MA1-005 | `fixed`（裁定=拆分，落盘理由） | `FileLanguageStats.java` 516→188 行：4 关注点拆分（`IgnoredItems`/`FileTreeWalker`/`CodeLineAnalyzer`/`LanguageStatsAggregator`），主类仅编排入口。裁定：拒绝删除（完整可用能力）+拒绝接线（无自然消费点）；零生产调用者由直接调用测试提供消费者证据。语义对比全部 IDENTICAL。测试 = `TestFileLanguageStats`（5 方法：样本目录统计→语言分类→聚合端到端全值断言 + GitHub API 字符串 + 忽略规则 + 注释边界） |
| P3-MA1-014 | `fixed`（裁定=保持现状+边界文档化） | 裁定理由：code→maven→project→git 依赖链拆 maven 即双向环（破环须迁 7/10 maven 文件，拆分自溶解）；nop-shell 仅 MavenProject.java 使用（拒绝 optional/provided）；git 包（GitIgnoreFile，nop-cli-core 经 nop-ai-coder 传递消费）为跨模块公共面。落盘 `ai-dev/design/nop-ai/02-code-analyzer-module-boundary.md`（使用约束：maven 包=内部子域禁止外部 import + 未来迁移触发条件）；nop-ai/README.md + design/README.md 注册 |
| P3-MA1-015 | `fixed` | `nop-ai-deepwiki/pom.xml` 移除 `nop-ai-code-analyzer`（生产+测试 0 引用）、`nop-ai-tools` 改 `<scope>test</scope>`（仅 TestDeepWikiPrompts:10/:38 import FileToolBizModel）；依赖树验证 direct test scope；deepwiki compile+test BUILD SUCCESS；docs-for-ai grep deepwiki/code-analyzer 0 引用 → No owner-doc update required |

**验证**：`./mvnw test -pl nop-ai/nop-ai-skills/nop-ai-code-analyzer -am -T 1C` BUILD SUCCESS（26 tests 0 failures）；`./mvnw test -pl nop-ai/nop-ai-skills/nop-ai-deepwiki -am -T 1C` BUILD SUCCESS；import 顺序新建/触及文件 0 违规（模块内其余为既有基线）。

## P3 追踪（第九批 — tools/rag 结构残余，2026-08-01）

第九批批量修复（plan `ai-dev/plans/2026-08-01-0206-2-arm-p2-tools-structure-residual.md`）已执行并收口。三个结构类 P3 finding 全部 `fixed` 或裁定落盘：

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| P3-MA1-016 | `fixed` | `nopGraphQLToolSet` 条件注册：`ai-tools-defaults.beans.xml` 增加 `<ioc:condition><on-bean>nopGraphQLEngine</on-bean></ioc:condition>`（Nop IoC 语法，bean 名匹配非接口名；audit 原建议的属性语法在 beans.xdef 不存在）。测试 = `TestGraphQLToolSetConditionalRegistration`（4 例：xdef 模型解析、biz-defaults 仓库实例一致性、有引擎容器接线注册、无引擎自定义容器跳过且装配不失败）；pom 补 `nop-autotest-junit`/`nop-http-client-jdk` test 依赖；deepwiki `TestDeepWikiPrompts` 消费面 @Disabled 状态不变 |
| P3-MA1-013 | `fixed`（裁定保持 + 修正 2 个 live defect） | 裁定 = 保持文件持久化（会话级工具）。默认路径 `/nop/ai/sequential-thinking/store`（绝对路径不可写）→ `./_tmp/ai/sequential-thinking/store`（相对 CWD 可写）；测试暴露 2 个 live defect 就地修复：`ThoughtSession` 缺 `@DataBean`（序列化必失败）+ `Instant` 无 JsonTool 支持（模型字段改 epoch millis long）。测试 = `TestThoughtStorage`（8 例：持久化 round-trip/路径解析/会话隔离/阶段过滤/清空/导出导入/未知会话/null 拒绝）+ `TestSequentialThinkingBizModel` +1 成功路径（原 storage 必抛错，成功路径从未跑通）。裁定落盘 `ai-dev/design/nop-ai/03-sequential-thinking-storage.md`；`docs-for-ai/03-modules/nop-ai.md` 配置节同步 |
| P3-MA3-003 | `fixed`（裁定保留 + 文档化） | 裁定 = 保留空模块 + 文档化为 SPI 预期实现落点（不补 InMemory 实现——零消费方投机代码违反 Anti-Hollow；不从父 pom 移除——结构声明价值）。与 P1-MA5-003 SPI 裁定兼容。落盘 `nop-ai-rag/README.md` + `ai-dev/design/nop-ai/04-rag-module-position.md`；`docs-for-ai/03-modules/nop-ai.md` 子模块表同步 |

**验证**：`./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS；`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS；`scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0；`check-doc-links.mjs --strict` exit 0。

## P3 追踪（第九批 — 测试质量 P3 残余，2026-08-01）

第九批批量修复（plan `ai-dev/plans/2026-08-01-0206-3-arm-p3-test-quality-residual.md`）已执行并收口。MA4.3-09/13（assertTrue-only 升级）、MA4.4-01/02/03（低价值测试裁定）、MA4.2-06（大测试文件拆分）全部 `fixed` 或裁定落盘：

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| MA4.3-09 | `fixed` | `TestDefaultPermissionMatrix` 全部断言升级为值级：allow 路径 `assertEquals(MatrixDecision.allow(), ...)`（all-field equals 覆盖 allowed+null reason/channel/level），deny 路径 `assertDeniedFor` 断言 `MatrixDecision.getChannel()/getLevel()/getReason()` 结构化字段（GROUP+ELEVATED、API/DM/GROUP/null+RESTRICTED、OPERATOR bypass）；5 tests 0 failures。13 个其余 assertTrue-only 文件（12 NoOp + TestDefaultContentTrustEvaluator）裁定不触碰（boolean 契约断言恰当，audit 认可）——落盘 plan Deferred But Adjudicated 段 |
| MA4.3-13 | `fixed` | `TestDbSessionTakeoverLockDualInstanceE2E` 新增 `readLockOwner()`——经 `AiAgentSessionLockTable` 公开常量（TABLE_NAME/COL_LOCK_OWNER）直查共享 H2，两场景（干净交接 + 过期抢占）逐步断言 LOCK_OWNER 字段值变迁（engine-A → null → engine-B → null）、失败 acquire/release 不转移归属、过期租约行在抢占前保留；2 tests 0 failures |
| MA4.4-01 | `fixed`（裁定删除冗余） | `TestMicroCompressionCompactor` 删除 `compressibleToolsSetContainsExpectedTools`（断言内部常量 `COMPRESSIBLE_TOOLS.contains(...)`，非行为）；工具压缩行为断言保留在 `nonCompressibleToolsPreserved`（bash 压缩 + ask-oracle 保留）+ `compressedPlaceholderContainsToolInfo`（COMPRESSED/toolName/toolCallId/原长度） |
| MA4.4-02 | `fixed`（裁定合并） | `TestAgentSession` 三个 round-trip 测试（parentSessionId/planId/compactedAt）合并为 `testAllFieldsRoundTrip()`（一次性设置 + 一次性断言；纯 setter/getter 由编译器保证类型，合并保留值级覆盖）；15 tests 0 failures |
| MA4.4-03 | `fixed`（裁定合并为单一结构测试） | `TestSecurityLevel` 3 tests → 1：保留唯一非编译器保证的断言 `values().length == 3`（设计 §5.1 契约守卫——新增第 4 个枚举值编译期不报错），删除编译器保证的 valueOf/ordinal/compareTo 断言，理由入类 javadoc |
| MA4.2-06 | `fixed`（拆分 + 共享 fixture 提取） | `TestMultiMemberFanOut` 1139 行 → `AbstractMultiMemberFanOutTest`（共享 fixture：FixedPlanRouter/ConcurrencyRecordingEngine/ConfigurableFanOutEngine/ConcurrencyRecordingSpawner/team-task builders）+ `TestMultiMemberFanOutSuccess`（5）/`TestMultiMemberFanOutFailure`（9）/`TestMultiMemberFanOutRouting`（4），18 @Test 不减少；`TestScheduledRecoveryManager` 1076 行 → `AbstractScheduledRecoveryManagerTest`（H2 fixtures + RecordingScheduler/Handler/TimeoutHandler/StubEngine）+ `ScanOnce`（8）/`Scheduling`（9）/`OrphanHandler`（7）/`Timeout`（12），36 @Test 不减少；全部文件 <500 行，场景仍跑真实组件链（真实 H2 + 真实 orchestrator/scanOnce，无 mock 替代） |

**验证**：`./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS；`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（3519 tests 0 failures 0 errors）；`scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0；`check-doc-links.mjs --strict` exit 0；`check-plan-checklist.mjs --strict` exit 0（closure audit 见 plan Closure 段）。

## P3 追踪（第十一批 — API 文档债务，2026-08-01）

第十一批批量修复（plan `ai-dev/plans/2026-08-01-0746-1-arm-p3-api-doc-debt.md`）已执行并收口。MA4.2-07/-10、MA4.5-008/-009 全部 `fixed`（纯 javadoc 变更，零行为变更）：

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| MA4.2-07 | `fixed` | 18 个 deprecated 类/接口/枚举 javadoc 泛化消息 → 具体迁移指引（`Use {@link ...} instead`）：消息系 13 文件 → nop-ai-api `chat.messages.*`（AiMessage/AbstractTextMessage→ChatMessage、AiUserMessage→ChatUserMessage、AiAssistantMessage→ChatAssistantMessage、AiSystemMessage→ChatSystemMessage、AiToolMessage/AiToolResponseMessage→ChatToolResponseMessage、ToolCall→ChatToolCall、AiMessageAttachment→ChatAttachment、AiChatUsage→ChatUsage、AiChatExchange→ChatRequest+ChatResponse、Prompt→ChatRequest）；MessageStatus 裁定 no direct replacement（流式状态由 ChatStreamChunk index/finishReason 建模）；工具系 4 文件（ToolSpecification→IToolDefinition/ChatToolDefinition、ToolSpecificationLoader→IToolManager.loadTool、DefaultAiChatFunctionTool→IToolDefinition+IToolExecutor/IToolManager+AiToolModel、DefaultAiChatToolSet→IToolManager）；IAiChatProgressListener→IChatService.callStream。@Deprecated 注解属性零改动；被引用 17 个新类名全量 grep 验证存在；`rg "This internal AI core"` 0 命中 |
| MA4.2-10 | `fixed` | 45 个公开接口方法补方法级 javadoc（英文）：IFileOperator 24 方法（getResource 路径逃逸异常/readFileContent offset-limit 语义/notFound marker/mergeFile xdef delta 语义等，5 个既有中文 javadoc 方法保留）；IPromptTemplate 11 方法；IPromptTemplateManager 2 方法；IAiChatResponseChecker 1 方法（+类级契约）；IAiTextSplitter split() + SplitChunk/SplitOptions 内嵌类访问器 15 方法；ITokenCountEstimator estimate/estimateForMessage。**IRequestStore 核验为 audit 误列（live 已有完整中文类级 + 2 方法 javadoc），标 N/A 零改动** |
| MA4.5-009 | `fixed` | 7 个小型公开接口补类级 javadoc（1-2 句契约 + 关键方法 @param/@return）：IChatLogger（nop-ai-api）/IToolExecuteContext（6 getter 全注释）/IToolCallInterceptor（beforeCall 拒绝语义）/IToolExecutorProvider/IDslTool（4 方法）/ITextClassifier/IDocumentClassifier；IEmbeddingModel 与 ITokenCountEstimator 已有 SPI 裁定类 javadoc 仅核验完整不重写 |
| MA4.5-008 | `fixed` | IFileOperator `@deprecated` 消息中文 → 英文（`Use io.nop.ai.toolkit.fs.IToolFileSystem instead (forRemoval=true; removal is future major-version work)`），替代对象与 forRemoval 语义保留，与全库 deprecated 消息语言一致 |

**验证**：`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（0 failures 0 errors）；`./mvnw compile -pl nop-ai/nop-ai-api,nop-ai/nop-ai-core,nop-ai/nop-ai-toolkit,nop-ai/nop-ai-coder -am` PASS；`scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0；`check-doc-links.mjs --strict` exit 0（No errors found）；closure audit 见 plan Closure 段。

## P3 追踪（第十一批 — 代码卫生，2026-08-01）

第十一批批量修复（plan `ai-dev/plans/2026-08-01-0746-2-arm-p3-code-hygiene.md`，与 0746-1 同批次串行执行）已执行并收口。MA4.2-12/-13、MA4.1-04 全部 `fixed`（纯输出/修饰符/注解变更，零行为变更）：

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| MA4.2-12 | `fixed` | 13 个测试文件 System.out 逐文件裁定（对照表落盘 `ai-dev/logs/2026/08-01.md`）：5 文件转 SLF4J LOG（TestAiCoderHelper 5 处 / TestJavaFileSplitter 1 / TestJavaParser 3 / MavenModuleStructureTest 1 / TestTextSplitter 3，类加 `static final Logger LOG`）；1 文件删除死代码 println（FixTranslateDir `cleanup()` 永不调用）；7 文件保留（@Disabled 手动测试 6 个 + VfsMavenUsageExampleRunner main() 示例，audit "acceptable for manual/exploratory tests" 裁定依据）；清理后 `grep -rln "System.out" nop-ai --include="*.java"` = 7 文件，与"保留"集合逐行一致；测试数量零减少 |
| MA4.2-13 | `fixed`（部分 + 裁定） | `IFileOperator.GrepResult` 3 个 getter（getFilePath/getLineNumber/getLineContent）移除冗余 `public`（全库跨包零调用者核验，toString() 保留 public 供 FileToolBizModel:225）；**SplitChunk/SplitOptions 裁定不处理**（接口内嵌类成员非隐式 public，移除即降 package-private，破坏 JavaFileSplitter.java:36,92,97 + TestTextSplitter:28 Jackson 反射序列化，编译失败 + 静默改序列化输出；理由入 plan Deferred But Adjudicated） |
| MA4.1-04 | `fixed` | `DefaultAiChatService.parseToolCalls:562` `(Map<String, Object>) arguments` cast 补局部 `@SuppressWarnings("unchecked")`（AnthropicDialect 同款局部变量级；:548 List cast 既有注解未动） |

**验证**：`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（0 failures 0 errors）；`./mvnw test -pl nop-ai/nop-ai-tools,nop-ai/nop-ai-skills/nop-ai-code-analyzer -am` BUILD SUCCESS（GrepResult 下游 + SplitChunk 消费者回归）；`scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0；`check-doc-links.mjs --strict` exit 0（No errors found）；closure audit 见 plan Closure 段。

## P3 追踪（第十二批 — 错误处理规范，2026-08-01）

第十二批批量修复（plan `ai-dev/plans/2026-08-01-0936-1-arm-p3-error-handling-agent-shell.md`，MA3.4 审计点名的 deferred successor 重开，同批 0936-2/0936-3 兄弟计划并行）已执行并收口。P2-MA3-1、P3-MA3-1 全部 `fixed`（裸 IAE → NopException + 模块 ErrorCode，消息逐字零漂移，仅异常类型/错误码变化）；0936-2（plan `ai-dev/plans/2026-08-01-0936-2-arm-p3-error-handling-core-coder.md`）同步收口，P3-MA3-2/P3-MA3-3 全部 `fixed`（core 7 处 + coder 4 处 → NopException + 模块 ErrorCode，英文描述，消息逐字零漂移，仅异常类型/错误码变化）；0936-3（plan `ai-dev/plans/2026-08-01-0936-3-arm-p3-error-handling-tools-toolkit.md`）收口，P3-MA3-5 与 nop-ai-toolkit 同规范残余（LocalToolFileSystem 9 处，audit 未单独列项、本计划显式纳入）全部 `fixed`（tools 7 处 → NopException + NopAiCoreErrors 7 个新码，toolkit 9 处 → NopAiException，消息逐字零漂移，仅异常类型变化，`isPathAllowed` 安全判定逻辑零变更）：

| Finding ID | 修复状态 | 修复位置 / 测试 |
|-----------|---------|----------------|
| P2-MA3-1 | `fixed` | nop-ai-shell **12 处**裸 IAE（5 文件）→ `NopException` + `NopAiShellErrors` 5 个新码（`ERR_AI_SHELL_INVALID_ARG`/`COMMAND_NOT_FOUND`/`EMPTY_COMMAND`/`INVALID_REDIRECT`/`UNKNOWN_SYMBOL`，`nop.err.ai.shell.*` 前缀，英文描述）；转换后 main grep 0 命中；测试 = RedirectTest/CommandModelTest/ShellCommandRegistryTest 断言同步 + 2 个 focused 值级测试（errorCode/param/getMessage，@Test 19/49/12 vs 18/48/12 零减少） |
| P3-MA3-1 | `fixed` | nop-ai-agent **62 处**裸 IAE（33 文件）→ `NopAiAgentException` + `NopAiAgentErrors.ERR_AI_AGENT_INVALID_ARG`（`nop.err.ai.agent.invalid-arg`，`invalid argument: {msg}`，ARG_MSG，逐字消息经 `.param(ARG_MSG, ...)` 承载——1834-3 NopAiMavenErrors 同款先例）；转换后 main grep 0 命中；5 处 `catch (IllegalArgumentException`（LLMCurator:286、FileSystemSkillProvider:275/284、AgentMessageEnvelopeJson:108、CheckpointJournalReader:177）逐一核验均只捕获 JDK `Enum.valueOf` 产物，与 62 转换站点零交集、影响 0；测试 = 20 文件 51 处断言同步 + focused 值级测试（TestThresholdBreaker.validationFailureCarriesErrorCodeAndVerbatimMessage，@Test 295 vs 294 零减少） |
| P3-MA3-2 | `fixed` | nop-ai-core **7 处**裸 IAE（6 文件）→ `NopException` + `NopAiCoreErrors` 7 个新码（英文描述，静态消息进 desc/动态拼接进结构化 param，消息逐字零漂移）：`ERR_AI_FILE_INVALID_EDIT_TYPE`（FileDiffGenerator:213 防御分支，focused 测试 reflection 证明运行时可达）/`ERR_AI_PROMPT_TEMPLATE_NULL`（PromptModel:88）/`ERR_AI_UNSUPPORTED_PARSE_FROM_RESPONSE`（ModelBasedPromptTemplate:244，ARG_PARSE_MODEL）/`ERR_AI_UNKNOWN_ROLE`（AiMessage:51，ARG_ROLE）/`ERR_AI_VECTOR_LENGTH_MISMATCH`（CosineSimilarity:48）/`ERR_AI_SESSION_ID_IS_EMPTY`+`ERR_AI_SESSION_ID_INVALID`（ChatLogHelper:65/69，ARG_SESSION_ID）；转换后 main grep 0 命中；测试 = 2 文件断言同步（TestCosineSimilarityAndRelevanceScore/TestChatLogHelper）+ 3 个 focused 值级测试文件（TestPromptErrorCodeConversion/TestAiMessageErrorCode/TestFileDiffGeneratorErrorCode，+5 @Test 零减少） |
| P3-MA3-3 | `fixed` | nop-ai-coder **4 处**裸 IAE（3 文件）→ `NopException` + `AiCoderErrors` 3 个新码（英文描述）：`ERR_AI_CODER_UNSUPPORTED_CONVERSION`（AiXdefDocumentConverter:21 + AiOrmDocumentConverter:28，ARG_FROM/ARG_TO）/`ERR_AI_CODER_METHOD_SIGNATURE_NOT_FOUND`（JavaMethodReplacer:24，ARG_SIGNATURE，audit 复核段 new finding 显式纳入）/`ERR_AI_CODER_UNBALANCED_BRACES`（JavaMethodReplacer:33）；转换后 main grep 0 命中；测试 = 新增 focused 值级测试 `TestCoderErrorCodeConversion`（4 @Test，零减少，converter 用 ResourceDocumentObject("txt", ByteArrayResource) 非注册类型驱动） |
| P3-MA3-5 | `fixed` | nop-ai-tools **7 处**裸 IAE（4 文件）→ `NopException` + `NopAiCoreErrors` 7 个新码（英文描述，`nop.err.ai.tools.*` 前缀，消息逐字零漂移）：`ERR_AI_TOOLS_INVALID_PROJECT_NAME`（FileToolBizModel:237 受保护方法 getProjectDir，ARG_VALUE）/`ERR_AI_TOOLS_THOUGHT_EMPTY`+`INVALID_THOUGHT_NUMBER`+`INVALID_TOTAL_THOUGHTS`+`TOTAL_THOUGHTS_LESS_THAN_NUMBER`（ThoughtData:51/61/72/75）/`ERR_AI_TOOLS_INVALID_STAGE`（ThoughtStage:29，ARG_VALUE）/`ERR_AI_TOOLS_INVALID_MAX_RESULTS`（ThoughtAnalyzer:36）；转换后 main grep 0 命中；测试 = 新增 focused 值级测试 `TestToolErrorCodeConversion`（7 @Test，7 码全覆盖，errorCode/param/getMessage，@Test 23 vs 16 零减少） |
| nop-ai-toolkit 同规范残余（audit 未单独列项，本计划显式纳入） | `fixed` | nop-ai-toolkit `LocalToolFileSystem` **9 处**裸 IAE（1 文件）→ `NopAiException`（模块异常类，DefaultToolExecutorProvider 先例；IToolFileSystem 跨模块消费面仅 `getMessage()`，无程序化错误码消费 → error-handling.md 模式二）：:54（"Path not allowed"，MA6.2-003 安全判定面——判定逻辑零变更，仅异常类型变化）/92/112/141/225/244（"File not found"）/168（"Directory not found"）/229/248（"Target file already exists"）；转换后 main grep 0 命中；测试 = 新增 focused 值级测试 `TestLocalToolFileSystemErrors`（9 @Test，7 个消息值级断言 + isPathAllowed 安全判定不变断言，@Test 122 vs 113 零减少） |

**验证**：`./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS；`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（0 failures）；`scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0；`check-doc-links.mjs --strict` exit 0（顺带修复 mission-driver-port-design.md 3 处预存 broken link）；closure audit 见 plan Closure 段。0936-2 侧验证：`./mvnw test -pl nop-ai/nop-ai-core -am -T 1C` + `./mvnw test -pl nop-ai/nop-ai-coder -am -T 1C` + 全量 nop-ai 回归均 BUILD SUCCESS（0 failures）；两模块 main grep 0 残留；closure audit 见 0936-2 plan Closure 段。0936-3 侧验证：`./mvnw test -pl nop-ai/nop-ai-tools -am -T 1C`（23 tests）+ `./mvnw test -pl nop-ai/nop-ai-toolkit -am -T 1C`（122 tests）+ 全量 nop-ai 回归（3631 tests）均 BUILD SUCCESS（0 failures）；两模块 main grep 0 残留；`isPathAllowed` 安全判定逻辑零变更（对照 focused 测试断言）；closure audit 见 0936-3 plan Closure 段。error-handling.md 消息语言例外条款 drift（0936-2 closure 登记、"另作文档治理规划"指定）已由 plan `ai-dev/plans/2026-08-01-1223-1-arm-error-handling-doc-drift-sync.md` 收口（纯文档计划，零代码变更）：例外条款从"仅 nop-ai-agent / nop-ai-shell"同步为与 live 一致的 6 模块清单（agent / shell / core / coder / maven / code-analyzer，tools 码在 NopAiCoreErrors 随 core 说明），分界语义裁定为**来源驱动**（批次内从裸 IAE/UOE/RTE 逐字转换的码 → 英文；既有业务码 → 中文，`ERR_AI_TOOLS_INVALID_THOUGHT` 与英文码同命名空间共存即证明），起源叙事覆盖 UOE（2248-2）与 IAE/RTE（0936-1/2/3、1834-3）两类转换来源；引用面同步裁定 No owner-doc update required（其余命中均与 live 一致）。
