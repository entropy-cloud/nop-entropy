# 审计-修复报告索引（arm）

> 启动时间：2026-07-30
> 目标模块组：nop-ai（18 子模块，排除 MCP——MCP 协议集成模块，独立发布周期，需单独审计）
> 总览：ai-dev/backlog/audit-remediation-roadmap.md
> 维度矩阵：arm-audit-dimension-matrix.md
> 状态汇总：已完成 19 | 进行中 0 | 待办 11 | P0 未解决 0

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
| — | MA4.1 | 类型安全 | 全模块 | — | — | — | todo |
| — | MA4.2 | 代码风格 | 全模块 | — | — | — | todo |
| — | MA4.3 | 测试覆盖 | 全模块 | — | — | — | todo |
| — | MA4.4 | 测试有效性 | 全模块 | — | — | — | todo |
| — | MA4.5 | 文档一致性 | 全模块 | — | — | — | todo |
| [`2026-07-30-2100-arm-MA5.1-nop-ai-hollow-scan.md`](./2026-07-30-2100-arm-MA5.1-nop-ai-hollow-scan.md) | MA5.1 | 空壳实现 | 全模块 | 0 | 2 | 7 | `done` |
| [`2026-07-30-2100-arm-MA5.2-nop-ai-silent-noop.md`](./2026-07-30-2100-arm-MA5.2-nop-ai-silent-noop.md) | MA5.2 | 静默跳过 | 全模块 | 0 | 2 | 20 | `done` |
| [`2026-07-30-2130-arm-MA5.3-nop-ai-wiring.md`](./2026-07-30-2130-arm-MA5.3-nop-ai-wiring.md) | MA5.3 | 接线完整性 | 全模块 | 0 | 2 | 7 | `done` |
| — | MA5.4 | 设计文档 drift | 全模块 | — | — | — | todo |
| — | MA5.5 | 敏感泄露 | 全模块 | — | — | — | todo |
| — | MA5.6 | 测试隔离 | 全模块 | — | — | — | todo |
| — | MA5.7 | 修复验证 | 全模块 | — | — | — | todo |
| — | MA6.1 | LLM 配置安全 | 全模块 | — | — | — | todo |
| — | MA6.2 | Agent 编排安全 | 全模块 | — | — | — | todo |
| — | MA6.3 | Token 计量与调用可靠性 | 全模块 | — | — | — | todo |
| — | MA6.4 | 向量存储/Embedding 隔离 | 全模块 | — | — | — | todo |
| — | MA6.5 | 对话历史与 Prompt 安全 | 全模块 | — | — | — | todo |

## P0 发现追踪（即时通道）

| Finding ID | 报告 | 描述 | 修复路径 | 修复状态 |
|-----------|------|------|---------|---------|
| P0-MA2-01 ✅ | MA2.1 | 双 ORM 源文件漂移（nop-ai.orm.xml vs ai-gen.orm.xml） | [异步修复 plan](ai-dev/plans/2026-07-30-2130-arm-fix-p0-ma2-01.md) | `fixed` |

## P1 发现汇总（待 MR 批量修复）

| Finding ID | 报告 | 描述 | 目标 MR | 修复状态 |
|-----------|------|------|--------|---------|
| P1-MA1-001 (原 F01) | MA1.1-MA1.2 | `nop-diff` 未使用依赖 | — | `fixed`（MA1.1 审计中就地修复） |
| P1-MA1-002 (原 F02) | MA1.1-MA1.2 | 废弃并行 API 体系未清理（IAiChatService 等） | MR1 | open |
| P1-MA5-001 (原 F03) | MA5.2/MA5.1 交叉 | `DefaultAiChatService.getSession()` 始终返回 null | — | `fixed`（审计中修改为 throw UnsupportedOperationException） |
| P1-MA5-002 (原 F04) | MA5.2 | `BashExecutor` 子线程流读取空 catch | MR2 | open |
| P1-MA5-003 (原 F05) | MA5.1 | IVectorStore / IEmbeddingModel / ITokenCountEstimator 接口无生产实现 | MR3 | open |
| P1-MA2-002 | MA2.1 | NopAiProject 缺失审计传播属性 | MR1 | open |
| P1-MA2-003 | MA2.1 | NopAiRequirement version 字段类型冲突（VARCHAR vs 乐观锁 int） | MR1 | open |
| P1-MA2-004 | MA2.1 | NopAiSessionContext refPropName="context" 应为 "contexts" | MR1 | open |
| P1-MA2-005 | MA2.1 | _dao.beans.xml 为空 — 无接口 Biz bean 注册 | MR1 | open |
| P1-MA5.3-001 | MA5.3 | ChatServiceImpl(IChatService) 无 IoC bean 定义 | MR2 | open |
| P1-MA5.3-002 | MA5.3 | DefaultAiChatService @Deprecated 但却是唯一注册的 chat service bean | MR2 | open |
| P1-MA1-003 | MA1.3 | FileToolBizModel 使用废弃 IFileOperator 接口 | MR1 | open |
| P1-MA1-010 | MA1.3 | nop-ai-skills 子模块零 IoC bean 注册 | MR1 | open |
| P1-MA1-017 | MA1.4 | nop-ai-maven 模块名与实际内容不符（核心为 VFS 非 Maven） | MR1 | open |
| P1-MA1-018 | MA1.4 | nop-ai-codegen 零生产 Java 代码 | MR1 | open |
| P1-MA1-019 | MA1.4 | nop-ai-codegen postcompile 引用不存在的 ORM 模型文件 | MR1 | open |
| P1-MA1-031 | MA1.5 | NopAiSessionContext relation refPropName 不匹配（context vs contexts） | MR1 | open |
| P1-MA1-032 | MA1.5 | 五种命名约定并存（NopAi/Chat/Ai/Agent/Tool） | MR1 | open |
| P1-MA1-033 | MA1.5 | 重复 ORM session/message 模型（nop-ai-agent vs nop-ai 主模型） | MR1 | open |
| P1-MA2-014 | MA2.2 | XDSL codegen 脚本存在但输出不可验证（agent/toolkit/core） | MR1 | open |
| P1-MA2-018 | MA2.3 | 9个废弃 snake_case dict 文件（nop-ai-meta） | MR1 | open |
| P1-MA2-023 | MA2.5 | NopAiModel.apiKey 凭证字段暴露为 queryable/sortable | MR1 | open |
| P1-MA2-024 | MA2.5 | NopAiSession 重复 to-many 关系 context/contexts | MR1 | open |
| P1-MA3-020 | MA3.2 | BizModel 方法全部缺少 @Auth 权限注解（0/45 @BizModel 类） | MR2 | open |
| P1-MA3-021 | MA3.2 | NopAiModel.apiKey 凭证字段在 xmeta 中完全暴露（queryable/sortable/insertable/updatable 均未限制） | MR2 | open |
| P1-MA3-022 | MA3.2 | LocalFileOperator.resolveFile() 绝对路径绕过 sandbox（/开头的路径通过 new File 拼接可逃逸 baseDir） | MR2 | open |
| P1-MA3-001 | MA3.4 | AiCoreErrors.ERR_AI_RESULT_INVALID_NUMBER 描述模板错位（value={name} 应为 value={value}） | MR2 | open |
| P1-MA3-002 | MA3.4 | SequentialThinkingBizModel.processThought @BizMutation 使用 IllegalArgumentException 而非 ErrorCode | MR2 | open |
| P1-MA3-01 | MA3.5 | nop-ai-agent 依赖 core 内部模型包（ChatOptionsModel） | MR2 | open |
| P1-MA3-02 | MA3.5 | nop-ai-agent 依赖 core 内部 dialect 包（ILlmDialect/LlmDialectFactory） | MR2 | open |
