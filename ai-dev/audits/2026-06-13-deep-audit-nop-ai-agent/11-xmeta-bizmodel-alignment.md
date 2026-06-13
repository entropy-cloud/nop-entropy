# 维度 11：XMeta 与 BizModel 对齐 — nop-ai-agent

**目标模块**: `nop-ai/nop-ai-agent`
**审计基线**: live code
**审计日期**: 2026-06-13

## 第 1 轮（初审）

### 结论摘要

| 项目 | 结论 |
|------|------|
| 维度 11 是否适用 | **不适用** |
| 发现数量 | **0** |
| 是否需要复审 | 否 |

`nop-ai-agent` 是纯 Java 框架库（agent 执行引擎、会话/计划/记忆/护栏等组件）。该模块**没有** XMeta 文件、**没有** `@BizModel` 类、**没有** GraphQL schema、**没有** XBiz 文件，因此维度 11 所关注的 "xmeta 字段定义 ↔ BizModel 方法实现 ↔ GraphQL 类型" 三方对齐关系在本模块不存在可审计表面。主 agent 基线判断与 live code 完全一致。

### 搜索证据（确认维度表面为空）

**XMeta / XBiz / GraphQL schema 文件**: Glob `**/*.xmeta`/`**/*.xbiz`/`**/*.graphql`/`**/*.xdef` → 0 文件（xdef 源位于 nop-kernel/nop-xdefs，非本模块）。

**BizModel / GraphQL 注解（Java 源）**: Grep `@BizModel` → 0；`@Biz(Query|Mutation|Loader|Initializer|Args)` → 0；`@BizModel|@BizObject|@XMeta|@GraphQL|@Schema|@Query|@Mutation|GraphQLOperationFactory|bizModel` → 0。

**"graphql" 字面量出现的性质澄清**: Grep `graphql|GraphQL` 仅命中 2 处，均为字符串字面量 `"graphql-query"`（作为受管控的工具名标识，由 `DefaultToolAccessChecker` 使用），不是 GraphQL schema 定义:
- `security/DefaultToolAccessChecker.java:17`
- `src/test/.../TestDefaultToolAccessChecker.java:68`

**资源文件盘点**: `src/main/resources/_vfs/` 下仅有 record-mappings.xml 与两个 register-model.xml，均为 XDsl 注册元数据，不是 BizModel/GraphQL 表面。测试资源 test-*.agent.xml 是 agent DSL 实例数据文件，同样不是 xmeta/BizModel 表面。

### 相关说明（非维度 11 范畴）

- `model/_gen/_*.java` 是代码生成产物（AGENTS.md「Hard Stop: Generated Files」禁止审计），由 xdef 源生成。
- xdef 源位于跨模块的 nop-kernel/nop-xdefs，不属于本模块本体。若存在字段级失配，根因归属应为 xdef 源/代码生成模板的审计，而非维度 11。本次未发现需要标记的硬失配。

### 误报排除说明

1. "零发现"非"漏报"：维度 11 检查对象在本模块物理上不存在（已穷尽枚举）。这是表面为空，不是审计深度不足。
2. 不把工具名字符串 `"graphql-query"` 当 GraphQL 表面。
3. 不把 XDsl register-model 当 BizModel 注册。
4. 不把生成代码字段纳入发现。

## 复核结论表

| ID | 标题 | 严重程度 | 状态 | 备注 |
|----|------|----------|------|------|
| —  | 无发现 | —        | N/A  | 维度 11 在本模块无可审计表面 |

### 建议

无需针对维度 11 对本模块采取任何行动。若后续为 nop-ai-agent 增加 BizModel/GraphQL 出口（例如把 agent 引擎包装为 GraphQL service），届时需重新引入维度 11 审计。跨模块的 xdef ↔ consumer 字段对齐性建议在专门的 DSL 模型完整性维度审计。
