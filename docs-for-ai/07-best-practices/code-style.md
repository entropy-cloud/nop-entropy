# Code Style

## 常用配置

- **命名规范**：PascalCase（类/接口）、camelCase（方法/变量）、UPPER_SNAKE_CASE（常量）
- **接口命名**：I + PascalCase（如 `IDaoProvider`、`IEntityDao`）
- **缩进**：4 个空格
- **行长度**：~80-120 字符
- **包名**：`io.nop.<module-name>.*`

## 注意事项

- 避免 Spring 专用写法（组件扫描、Spring AOP 等）作为默认模式
- 不要硬编码中文错误信息，使用错误码 + 参数
- 日志使用 SLF4J，不用 `System.out`/`System.err`
- 常量使用 `UPPER_SNAKE_CASE`

## IoC Bean 命名

- **Nop 平台内置 bean 必须使用 `nop` 前缀**，避免与业务 bean 冲突（例如 `nopMigrationEngine`）。
- 业务自定义 bean 不应使用 `nop` 前缀；测试用例可使用 `test`/`testMock` 前缀。
