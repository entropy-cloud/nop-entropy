# nop-treesitter wiki 入口

一页式导航——从这里找到你需要的资料。

## 这是什么

纯 Java 的 Tree-sitter 运行时：读取上游 grammar 的 `parser.c` 提取出的二进制
blob，输出与 C runtime 字节一致的语法树（含 `ERROR`/`MISSING` 错误恢复）。
无 JNI、无 FFM、无 native 库。

## 我要……

- **解析一段源码** → `nop-treesitter/README.md` 的"直接使用解析 API"
- **在 Nop 服务里通过 GraphQL 解析** → README 的"NopIoC / GraphQL"小节
- **解析 bash / 分析一段 shell 命令** → README 的"shell 分析语义层"小节；
  契约见 `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md`
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
A：两层口径。provider 注册（NopIoC / GraphQL 可解析）7 个：
json / java / javascript / typescript / tsx / python / **bash**。blob 已交付但
provider 未注册的另有 3 个：go / rust / c-sharp，只能经 compat 适配类触达。
其他上游 grammar 需要用模块的提取器（`io.nop.treesitter.codegen.Ts2Java`）从
`parser.c` 生成 blob。

**Q：bash 的 blob 能加载但树里有 `ERROR`，是 bug 吗？**
A：很可能不是。bash 的 29 个外部 token（heredoc、文件描述符、三种 regex、扩展
glob、`${...}` 运算符符号等）全部由外部 scanner 提供，blob 里不含 scanner。
必须接线：`new TreeSitterBash().language()`（compat 路径，静态接好）或经 provider
解析 `language="bash"`（provider 路径同样接好）。两条路径都必须能把含 heredoc 的
脚本解析成 `heredoc_start` / `heredoc_body` / `heredoc_end`。

**Q：脚本里有中文/重音字符，分析结果对吗？**
A：对。tree-sitter 的坐标是**字节偏移**而 Java 字符串不是，早期实现用字节偏移切解码后的
字符串会越界——破坏性命令会被报成乱名且**仍是 PROVEN**。现在所有取文本都走按字节切片
的 `ShellSource`，并且每个能力都有非 ASCII 回归断言（上游 corpus 是纯 ASCII，抓不到这类
问题）。

**Q：能力 1 的"证明"和普通解析有什么区别？**
A：解析只告诉你树长什么样；"证明"额外要求整棵树无语法错误、每个节点都在封闭
白名单内、每个参数都通过词法判据。三者任一不满足就返回带封闭原因的
`UnprovenScript`，而它**不提供**返回命令列表的访问器——因为"空列表"会被读成
"这段脚本什么都不做"。

**Q：线程模型？**
A：`TSTree` 不可变可共享；`TSParser.parse` 每次调用独立，无共享可变状态。
