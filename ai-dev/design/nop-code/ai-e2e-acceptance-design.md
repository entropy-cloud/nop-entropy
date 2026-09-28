# AI 端到端验收设计（N9.2）

**日期**：2026-09-28
**状态**：draft（N9.3-N9.6 执行时完善）

## 验收场景

### 场景 A：平台知识获取

20 个覆盖 nop 平台核心领域的问题，nop-code 辅助 agent 与仅 grep/read 基线 agent 对比。

| 领域 | 示例问题 |
|------|---------|
| BizModel | "NopAuthUser 的 GraphQL query 有哪些？" |
| ORM | "orm.xml 中 entity 的 tableName 是怎么配置的？" |
| Delta | "Delta 定制如何覆盖基线模型？" |
| IoC | "beans.xml 中 ioc:default 的含义是什么？" |
| XLang | "XPL 模板引擎如何输出表达式结果？" |
| CodeGen | "codegen 模板如何访问 model 属性？" |
| Workflow | "workflow 的 transition 如何配置 condition？" |
| Security | "权限校验的 interceptor 链是什么？" |

### 场景 B：应用开发任务

5 个基于 Nop 的开发任务（新增实体+CRUD 页面、实现审批流、编写 batch 任务、新增 GraphQL API、编写 XLang 脚本）。

### 评分维度

1. **准确性**：回答与 docs-for-ai ground truth 的一致性
2. **引用正确性**：引用的文件路径/类名/方法名是否正确
3. **工具调用效率**：nop-code 组的工具调用次数 vs 基线组
4. **token 效率**：nop-code 组的 token 消耗 vs 基线组

### 通过标准

- nop-code 组在准确性上不低于基线组
- nop-code 组在工具调用效率上优于基线组 ≥ 20%
- 如果不达标 → 缺口回灌为新的 Work Item（N9.7 负责）
