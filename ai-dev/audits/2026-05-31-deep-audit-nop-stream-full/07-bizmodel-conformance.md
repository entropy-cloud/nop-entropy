# 维度 07：BizModel 规范遵循

## 检查范围

| 搜索方式 | 模式/路径 | 结果 |
|---------|----------|------|
| Grep: `@BizModel` | `nop-stream/` 全部文件 | 零匹配 |
| Grep: `BizModel` | `nop-stream/` 全部文件 | 零匹配 |
| Grep: `@BizQuery|@BizMutation|@BizLoader|CrudBizModel|@BizAction` | `nop-stream/` 全部 `.java` | 零匹配 |
| Grep: `import.*BizModel|import.*CrudBizModel` | `nop-stream/` 全部 `.java` | 零匹配 |
| Glob: `**/*BizModel*` | `nop-stream/` | 零文件 |
| Glob: `**/*.biz.xml` | `nop-stream/` | 零文件 |

覆盖全部 10 个子模块。

## 结论

nop-stream 是一个框架/引擎模块，不包含面向 GraphQL API 层的 BizModel 业务服务类。所有 Java 文件均为引擎内部实现类（NFA 编译器、共享缓冲区、算子链、模式匹配等）。

**零发现。**
