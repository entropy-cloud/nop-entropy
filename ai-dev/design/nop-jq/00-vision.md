# nop-jq Vision

**日期**：2026-09-18
**状态**：草案

---

## 一、产品定位

nop-jq 为 Nop 平台提供**零外部依赖**的 JSON 查询和转换能力，同时支持两种语法：

- **jq 语法**：面向转换场景，支持管道、过滤、映射、归约等函数式操作
- **JsonPath 语法**：面向提取场景，支持路径表达式和谓词过滤

目标用户：在 Nop 平台上构建应用的开发者，以及 Nop 框架自身的模块（ORM、GraphQL、DSL 引擎等）。

## 二、成功标准

1. **零外部依赖**：不依赖 Jayway JsonPath、fastjson、Jackson 等第三方 JSON 库。nop-core 的 `JsonTool`、`BeanTool`、`ReflectionManager` 是允许的内部依赖。
2. **语法兼容**：JsonPath 支持 `$`、`.`、`..`、`[]`、`*`、`?()` 等标准语法；jq 支持 `.field`、`select`、`map`、`reduce`、管道 `|`、数组/对象构造等核心语法。
3. **性能持平**：对常见查询场景（单层属性访问、数组索引、简单过滤），编译缓存后的性能不低于 Jayway JsonPath。
4. **与 nop 体系深度集成**：可作为全局函数在 XLang/XSQL/XDef 等 DSL 中使用；ORM 的 `jsonPath` 属性可使用新实现。

## 三、Non-Goals

1. **不实现完整的 jq VM**：不支持 fork/backtrack、label/break、 `$ENV` 等 jq 高级特性。jq 常用语法通过翻译为 XLang 表达式实现。
2. **不实现流式 JSON 解析**：不支持直接从 JSON 字符串流式提取（fastjson 的 `extract()` 路径），所有操作基于已解析的内存对象。
3. **不提供 AST 序列化**：编译结果是内部数据结构，不暴露 AST 的 JSON/XML 序列化。
4. **不替代 nop-core 的 JsonTool**：本模块专注于查询和转换，不负责 JSON 的序列化/反序列化。

## 四、设计收敛路径

分两阶段交付：

- **Phase 1**：JsonPath 核心 + jq→XLang 翻译器。覆盖 80% 的日常使用场景。
- **Phase 2**（按需）：完整 jq AST + 字节码 VM。仅在 Phase 1 无法覆盖足够场景时启动。
