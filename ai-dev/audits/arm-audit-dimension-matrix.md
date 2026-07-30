# 审计-修复维度矩阵：nop-ai 全模块组

> 生成时间：2026-07-30
> 来源：ai-dev/skills/deep-audit-prompts.md（21 维度）+ 残留风险新维度
> 范围：nop-ai 全部 18 个子模块（排除 nop-ai-mcp-server, nop-spring-mcp-server*, deploy/, model/, target/）

## 子模块分组

| 编号 | 分组 | 子模块 | main 文件 | test 文件 | 角色 |
|------|------|--------|----------|----------|------|
| AC | api-core | nop-ai-api, nop-ai-core | 217 | 13 | API 契约 + 核心 AI 抽象（Chat/LLM Dialect/Embedding/VectorStore） |
| AG | agent | nop-ai-agent | 761 | 353 | Agent 引擎（ReAct 循环、Session、安全、可靠性、多 Agent） |
| TK | toolkit | nop-ai-toolkit, nop-ai-tools, nop-ai-skills | 119 | 33 | 工具 DSL 框架 + 工具实现 + 技能（代码分析） |
| BD | biz-dao | nop-ai-dao, nop-ai-meta | 45 | 0 | ORM 实体（21 实体）+ DAO + 数据字典（16）+ XMeta |
| BS | biz-svc | nop-ai-service, nop-ai-web | 27 | 3 | 21 CrudBizModel + 页面视图 + 权限配置 |
| AL | app-layer | nop-ai-shell, nop-ai-coder, nop-ai-app | 89 | 19 | Shell 执行器 + 代码生成 + 应用入口 |
| IF | infra | nop-ai-codegen, nop-ai-dsl-orm, nop-ai-maven, nop-ai-gateway, nop-ai-rag | 17 | 5 | Maven 插件、DSL ORM 解析、Gateway、RAG（占位） |
| — | **总计** | **18 子模块** | **~1275** | **~426** | — |

## 维度-模块覆盖矩阵

### 来源 A：既有 skill 维度

| # | 维度名称 | AC | AG | TK | BD | BS | AL | IF |
|---|---------|----|----|----|----|----|----|----|
| 01 | 依赖图与模块边界 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 02 | 模块职责与文件边界 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 03 | API 表面积与契约一致性 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 04 | ORM 模型与实体设计 | N/A | N/A | N/A | ❓ | N/A | N/A | N/A |
| 05 | 生成管线完整性 | N/A | N/A | N/A | ❓ | ❓ | N/A | ❓ |
| 06 | Delta 定制合规性 | N/A | N/A | N/A | ❓ | ❓ | N/A | ❓ |
| 07 | BizModel 规范遵循 | N/A | N/A | ❓ | N/A | ❓ | N/A | N/A |
| 08 | IoC 与 Bean 配置 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 09 | 错误处理与错误码 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 10 | XDSL 与 XLang 正确性 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 11 | XMeta 与 BizModel 对齐 | N/A | N/A | N/A | ❓ | ❓ | N/A | N/A |
| 12 | GraphQL 与 API 层 | N/A | N/A | N/A | N/A | ❓ | N/A | N/A |
| 13 | 安全与权限模型 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 14 | 异步与事务模式 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 15 | 类型安全与泛型使用 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 16 | 测试覆盖与质量 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 17 | 代码风格与规范 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 18 | 文档-代码一致性 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 19 | 命名与术语一致性 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 20 | 跨模块契约一致性 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ |
| 21 | 单元测试有效性 | ❓ | ✅ | ❓ | ❓ | ❓ | ❓ | ❓ |

### 来源 B：残留风险新维度

| # | 新维度 | 触发依据 | AC | AG | TK | BD | BS | AL | IF |
|---|--------|---------|----|----|----|----|----|----|----|
| H01 | 空壳实现扫描 | 已知反模式 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ |
| H02 | 静默跳过检测 | 空方法体/catch-and-swallow | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ |
| H03 | 接线完整性 | 组件间连接未验证 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ |
| H04 | 设计文档与代码 drift | 16+ design doc | ❓ | ❓ | ❓ | N/A | N/A | ❓ | N/A |
| H05 | 敏感信息泄露 | 日志/错误含敏感数据 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ |
| H06 | 测试隔离性 | 测试间状态污染 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ |
| H07 | 既有修复验证 | 历史 P0/P1 修复闭合度 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ |

### 图例

| 符号 | 含义 |
|------|------|
| ✅ | 已审计且所有 finding 已闭包 |
| ❓ | 未审计（本轮审计工作项来源） |
| N/A | 该维度不适用于该子模块 |

### 关键结论

- **nop-ai-agent (AG)** 是唯一经过深度审计的子模块（全部 finding 已闭包）
- **api-core (AC)** 作为最核心的基础模块，从未被审计
- **biz-dao/biz-svc (BD/BS)** 作为标准 ORM 业务骨架，涉及 ORM/BizModel/GraphQL 等多维度，从未被审计
- **toolkit (TK)** 的 IToolExecutor 接口体系从未被审计
- **app-layer (AL)** 的 shell/coder 从未被审计
- **infra (IF)** 的 gateway/dsl-orm 等从未被审计
- 残留风险 7 新维度全部子模块均未审计
