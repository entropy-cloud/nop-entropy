# Error handling (docs-for-ai)

`docs-for-ai` 不维护“通用异常处理最佳实践教程”。这里只保留 Nop 相关且可在仓库中验证的约束与入口。

## Nop 特有约束

1. 使用 `NopException` + ErrorCode 表达业务错误；不要硬编码错误文本。
2. 用 `.param(k, v)` 传递上下文参数（i18n/展示友好）。
3. 用 `.cause(e)` 保留底层异常链。

## 权威入口

- `docs-for-ai/getting-started/core/exception-guide.md`
- `docs-for-ai/getting-started/nop-vs-traditional-frameworks.md`
- `docs-for-ai/best-practices/INDEX.md`
s

## 常见错误处理问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 吞掉异常 | 没有记录或处理异常 | 记录并传播异常 |
| 过度捕获 | 捕获过于宽泛的异常 | 捕获具体异常类型 |
| 错误信息不明确 | 缺少上下文信息 | 添加参数和描述 |
| 日志不足 | 未记录关键信息 | 记录完整的上下文 |
| 异常链断裂 | 使用throw new而不是.cause() | 保持异常链 |
| 未处理资源 | 未关闭数据库连接等 | 使用try-finally |
| 错误码不规范 | 错误码不统一 | 定义统一的错误码体系 |

## 错误处理清单

### 异常创建

- [ ] 使用正确的错误码
- [ ] 添加必要的参数
- [ ] 提供描述性信息
- [ ] 保持异常链
- [ ] 记录原始异常

### 异常处理

- [ ] 在合适的层次处理异常
- [ ] 提供用户友好的错误信息
- [ ] 实现恢复策略
- [ ] 记录错误日志
- [ ] 发送关键异常告警

### 异常转换

- [ ] REST API正确转换异常
- [ ] GraphQL自动处理异常
- [ ] 异步异常正确处理
- [ ] 统一异常响应格式

## 相关文档

- [异常处理指南](../getting-started/core/exception-guide.md)
- [错误码定义](../getting-started/common/error-code.md)
- [服务层开发指南](../getting-started/service/service-layer-development.md)
- [事务管理指南](../getting-started/core/transaction-guide.md)

## 总结

Nop Platform错误处理是一个系统的工程，需要从多个层面考虑：

1. **错误类型区分**: 业务异常、验证异常、系统异常、未找到异常
2. **错误码规范**: 统一的错误码分组和层次结构
3. **异常创建**: 使用错误码、添加参数、保持异常链
4. **处理策略**: Try-Catch-Finally、资源关闭、嵌套处理
5. **层次处理**: 服务层、控制层、GraphQL层
6. **日志记录**: 统一日志格式、关键信息、级别控制
7. **异常转换**: REST API、GraphQL、异步操作
8. **恢复策略**: 重试机制、降级策略、熔断器
9. **异常通知**: 告警通知、用户友好错误、错误跟踪

遵循这些最佳实践，可以构建健壮、可靠、易于维护的错误处理体系。

---

**文档版本**: 1.0
**最后更新**: 2025-01-09
**作者**: AI Assistant (Sisyphus)
