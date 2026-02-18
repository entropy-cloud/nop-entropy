 # Nop Platform Security (docs-for-ai)

本文档已按 `docs-for-ai` 的写作约束做“去通用知识化”处理：**不重复业内通用安全最佳实践**（输入校验、SQL 注入、XSS、CSRF、密码策略、TLS、依赖扫描等），也**不提供 Spring/AOP/定时任务/第三方安全框架示例**。

在 Nop 项目中，安全相关内容通常需要结合：

1. 具体模块（如认证/权限/审计/网关/多租户/数据权限等）
2. Nop 的模型驱动、扩展点与运行时配置
3. 业务域的风险边界与合规要求

因此，这里只保留对 AI 和贡献者最有价值的“仓库内可验证入口”。

## 权威入口（优先阅读）

- `docs-for-ai/01-core-concepts/nop-vs-traditional.md`（避免把 Spring 安全/AOP 机制当成 Nop 默认写法）
- `docs-for-ai/07-best-practices/error-handling.md`（错误码/异常链/参数化信息：避免泄漏敏感信息）
- `docs-for-ai/04-core-components/ioc-container.md`（NopIoC 注入约束：避免 Spring-only 注解）

## docs-for-ai 的安全校验清单（只做 Nop 特有约束）

当你在本仓库写安全相关文档/示例时：



