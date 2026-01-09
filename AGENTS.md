<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# AGENTS.md - Nop Platform Development Guidelines

Essential quick reference for coding, building, testing, and contributing to Nop Platform.

**📚 Comprehensive Documentation**: For detailed API guides, tutorials, architecture, and examples, see [docs-for-ai](./docs-for-ai/INDEX.md)

## Build Commands

### Full Build
```bash
mvn clean install -DskipTests
mvn clean install -DskipTests -Dquarkus.package.type=uber-jar
mvn clean install -DskipTests -T 1C
```

### Testing
```bash
mvn test
mvn test -Dtest=AiConverterTest
mvn test -Dtest=AiConverterTest#testConvertOrm
mvn test -Pcoverage
```

### Code Quality
```bash
mvn checkstyle:check
```

## Quick Reference

### Code Style
- **Naming**: PascalCase（类）、camelCase（方法/变量）、UPPER_SNAKE_CASE（常量）
- **Formatting**: 4空格缩进、80-120字符行长度、运算符前后加空格
- **Imports**: 按分组导入（java.* → jakarta.* → 第三方 → io.nop.*）
- 详细规范 → [Code Style](./docs-for-ai/best-practices/code-style.md)

### Error Handling
- 使用 `NopException` 统一异常处理
- 定义清晰的错误码和参数
- 记录日志并保持异常链
- 详细规范 → [Error Handling](./docs-for-ai/best-practices/error-handling.md)

### Testing
- 使用 JUnit 5 和 Nop AutoTest 框架
- 遵循 Given-When-Then 模式
- 追求高测试覆盖率
- 详细规范 → [Testing](./docs-for-ai/best-practices/testing.md)

### DO's and DON'Ts
✅ Use parameterized queries
✅ Log all exceptions with context
✅ Use SLF4J logging
✅ Use configuration references
❌ Use raw SQL with user input
❌ Suppress exceptions without logging
❌ Use System.out or System.err
❌ Hardcode configuration values
❌ Use Chinese in error messages

## IDE Setup
- Java 17+
- Maven 3.9.3+
- UTF-8 encoding
- Enable annotation processing

## Quick Lookup

| Task | Documentation |
|------|--------------|
| 开发规范 | [AI Development Guide](./docs-for-ai/getting-started/ai/nop-ai-development.md) |
| 服务层开发 | [Service Layer Guide](./docs-for-ai/getting-started/service/service-layer-development.md) |
| CRUD开发 | [CRUD Development](./docs-for-ai/getting-started/business/crud-development.md) |
| 数据访问 | [IEntityDao Guide](./docs-for-ai/getting-started/dao/entitydao-usage.md) |
| 事务管理 | [Transaction Guide](./docs-for-ai/getting-started/core/transaction-guide.md) |
| GraphQL开发 | [GraphQL Guide](./docs-for-ai/getting-started/api/graphql-guide.md) |
| Helper类 | [Helper Reference](./docs-for-ai/quick-reference/helper-quick-reference.md) |
| API参考 | [API Quick Reference](./docs-for-ai/quick-reference/api-quick-reference.md) |
