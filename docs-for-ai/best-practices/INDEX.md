# 目录说明

`docs-for-ai` 面向 AI 的目标是：**只保留 Nop 平台特有、且必须依赖仓库源码才能回答的问题**。

“通用最佳实践”（如通用测试方法论、通用安全建议、通用性能建议、通用代码风格建议）大模型本身已具备，不应在 `docs-for-ai` 中重复。

因此本目录不再维护所谓“best practices”类的长篇教程。

请改用以下入口获取 Nop 特有规则与扩展点：

- Nop 与传统框架差异（防止把 Spring 写法当成 Nop 写法）：
	- `../getting-started/nop-vs-traditional-frameworks.md`
- 错误处理（NopException/ErrorCode 等）：
	- `../getting-started/core/exception-guide.md`
- IoC/DI 规则（@Inject/@InjectValue/private 字段限制等）：
	- `../getting-started/core/ioc-guide.md`
- 测试体系（NopAutoTest / @NopTestConfig）：
	- `../getting-started/test/autotest-guide.md`
