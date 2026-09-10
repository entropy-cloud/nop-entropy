# nop-treesitter wiki 入口

一页式导航——从这里找到你需要的资料。

## 这是什么

纯 Java 的 Tree-sitter 运行时：读取上游 grammar 的 `parser.c` 提取出的二进制
blob，输出与 C runtime 字节一致的语法树（含 `ERROR`/`MISSING` 错误恢复）。
无 JNI、无 FFM、无 native 库。

## 我要……

- **解析一段源码** → `nop-treesitter/README.md` 的"直接使用解析 API"
- **在 Nop 服务里通过 GraphQL 解析** → README 的"NopIoC / GraphQL"小节
- **接入自己的语法** → README 的"注册自定义语法"（`ITreeSitterLanguageProvider` + `META-INF/services`）
- **理解树里的 `(ERROR ...)` / `(MISSING ...)` 节点** → `ai-dev/plans/nop-treesitter/2026-09-09-1600-1-error-recovery.md`
- **评估性能 / 对比 C runtime** → `nop-treesitter/docs/perf-tuning.md`
- **了解 blob 二进制格式** → `nop-treesitter/src/main/resources/blob-format.md`
- **系统性了解模块** → `docs-for-ai/03-modules/nop-treesitter.md`

## 常见问题

**Q：为什么解析不抛异常？**
A：错误恢复是 tree-sitter 的核心特性——坏输入返回含 `ERROR`/`MISSING` 节点的树，
编辑器场景依赖它。 unrecoverable 的解析器内部故障仍抛 `TreeSitterException`。

**Q：支持哪些语法？**
A：内置 json/java/javascript/typescript/tsx；其他上游 grammar 需要用模块的
提取器（`io.nop.treesitter.codegen.Ts2Java`）从 `parser.c` 生成 blob。

**Q：线程模型？**
A：`TSTree` 不可变可共享；`TSParser.parse` 每次调用独立，无共享可变状态。
