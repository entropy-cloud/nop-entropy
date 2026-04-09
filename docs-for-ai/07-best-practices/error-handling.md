# Error Handling

本页不再定义一套独立的服务层异常写法，只保留 Nop 特有约束和入口。

## Nop 特有约束

1. 业务错误优先使用 `NopException + ErrorCode`
2. 用 `.param(...)` 传递上下文，而不是拼接硬编码错误文本
3. 在普通 BizModel 场景下，异常示例应配合 `requireEntity()`、`doFindList()`、`updateEntity()` 等安全 API
4. 普通 `@BizMutation` 方法不要再叠加 `@Transactional`

## 权威入口

- `../04-core-components/exception-handling.md`
- `../12-tasks/error-codes-and-nop-exception.md`
- `../13-reference/source-anchors.md`
