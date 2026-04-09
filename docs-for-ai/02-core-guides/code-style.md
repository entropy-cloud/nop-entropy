# 代码风格

本页只保留当前仓库里最稳定、最适合 AI 记住的风格约束。

## 命名与结构

- 类、接口：PascalCase
- 方法、变量：camelCase
- 常量：UPPER_SNAKE_CASE
- 接口命名：`I` + PascalCase
- 包名：`io.nop.<module-name>.*`

## 格式

- 4 空格缩进
- 行宽大致控制在 80-120 字符
- 保持 import 分组清晰：`java.* -> jakarta.* -> third-party -> io.nop.*`

## 当前仓库里的重要风格点

1. 避免 noisy refactor，diff 尽量聚焦。
2. 日志使用 SLF4J，不使用 `System.out` / `System.err`。
3. 错误处理优先 `NopException + ErrorCode + .param(...)`。
4. 不要把 Spring 专有注解、AOP 或注入模式当默认模板。

## IoC Bean 命名

- 平台内置 bean 使用 `nop` 前缀。
- 业务自定义 bean 不应使用 `nop` 前缀。
- 测试 bean 可使用 `test` / `testMock` 前缀。

## 相关文档

- `./service-layer.md`
- `./error-handling.md`
- `../00-start-here/ai-defaults.md`
