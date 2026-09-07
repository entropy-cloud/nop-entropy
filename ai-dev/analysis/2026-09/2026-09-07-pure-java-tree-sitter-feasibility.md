# Pure-Java Tree-sitter Runtime: Feasibility Analysis

> Status: open
> Date: 2026-09-07
> Scope: 在 Nop 生态中增加一个**纯 Java 实现的 Tree-sitter 运行时**，避免 JNI/FFM 依赖
> Conclusion: 待定（resolved 后填）

## Context

Nop 平台当前的代码分析基础设施基于 XLang AST + 自研的轻量 parser。要在 Nop 中集成 Tree-sitter
生态（200+ grammar、增量解析、Tree-sitter Query 语言），目前只有两条路：

1. **JNI/FFM 绑定**：`tree-sitter-ng`、`java-tree-sitter`（JDK 23+ FFM）等。需要 native lib，
   跨平台分发麻烦，GraalVM native-image 兼容性差，Android 需特殊处理。
2. **纯 Java 重写 runtime**：参考 `odvcencio/gotreesitter`（纯 Go，206 grammar）在 Go 上的做法，
   把 `lib/src/*.c` 的 runtime 用 Java 重写，grammar 表从上游 `parser.c` 抽取后直接复用。

本分析回答：
- 是否存在已有的纯 Java Tree-sitter 实现？（结论：**没有**，所有 Java 实现都依赖 JNI/FFM）
- 移植 runtime 的可行性、技术路线、工作量、风险
- 与 Nop 现有 `nop-xlang` 解析层的关系

## 调研结论

### 1. 各语言 Tree-sitter 实现现状

| 语言 | 实现 | 类型 | LOC |
|------|------|------|-----|
| C | `tree-sitter/tree-sitter` | 原生 runtime | ~18k C |
| JS/TS | `tree-sitter/lib/binding_web` (web-tree-sitter) | C → emscripten → WASM | 7k TS + WASM |
| JS/TS | `tree-sitter/crates/cli` (tree-sitter-cli) | Rust CLI + WASM 调用 | 63k Rust |
| Node.js | `tree-sitter/node-tree-sitter` | C++ N-API | 4.5k JS + 3.2k C++ |
| Python | `py-tree-sitter` | C extension | n/a |
| Rust | `tree-sitter` crate | FFI | n/a |
| Rust | `HerringtonDarkholme/tree-sitter` (ast-grep) | **纯 Rust 重写**（AI 辅助，30% 更快） | 78k |
| Rust | `shadaj/tree-sitter-c2rust` | c2rust 自动翻译 | 71k |
| Go | `tree-sitter/go-tree-sitter`, `smacker/go-tree-sitter` | CGO | n/a |
| Go | `odvcencio/gotreesitter` | **纯 Go 重写 + 206 grammar** | 726k |
| Java | `tree-sitter/java-tree-sitter`（官方） | jextract FFM（JDK 23+） | 4.9k |
| Java | `bonede/tree-sitter-ng` | JNI（JDK 8+） | 7k Java + 10k C |
| Java | `seart-group/java-tree-sitter` | JNI（JDK 11+） | 7.6k |
| Kotlin | `tree-sitter/kotlin-tree-sitter` | JNI / KMP（JDK 17+） | 8.7k |
| Java | `Marcono1234/jtreesitter-type-gen` | jtreesitter 之上的 codegen | 56k |

### 2. 用户提到的"JavaScript/TypeScript 实现"澄清

**没有"纯 JavaScript 重写 runtime"**。所有 JS/TS 实现都依赖编译后的 C 运行时：
- `web-tree-sitter` = C → WASM，JS 端只是包装
- `tree-sitter-cli` = Rust 实现 + 内部 WASM 调用
- `node-tree-sitter` = C++ N-API

Nop 如果要走"纯 JS"路线同样没有先例可参考。**gotreesitter 是目前唯一大规模成功的 runtime 重写案例**。

### 3. 参考仓库（已下载到 `~/sources/treesitter/`）

按优先级：

| 仓库 | 路径 | 用法 |
|------|------|------|
| **gotreesitter** | `~/sources/treesitter/gotreesitter` | **核心参考**：parser.go/lexer.go/subtree.go/external.go + cmd/ts2go |
| tree-sitter | `~/sources/treesitter/tree-sitter` | **规范来源**：lib/src/*.c + api.h |
| tree-sitter-rust-rewrite | `~/sources/treesitter/tree-sitter-rust-rewrite` | AI 辅助重写的工程经验 |
| tree-sitter-c2rust | `~/sources/treesitter/tree-sitter-c2rust` | 机械翻译方法论参考 |
| tree-sitter-ng | `~/sources/treesitter/tree-sitter-ng` | Java API 设计参考（虽基于 JNI） |
| java-tree-sitter | `~/sources/treesitter/java-tree-sitter` | 官方 Java API 风格 |
| grammars/{json,java,javascript,typescript,python} | `~/sources/treesitter/grammars/` | 移植后的 golden test |

## 移植可行性分析

### 路线 A：纯 Java runtime（gotreesitter 模式）

**核心思路**：
1. `ts2java` 工具：从 upstream `parser.c` 抽取 parse table（`ts_language_*` 函数返回的 `TSLanguage` 结构、symbol IDs、state transitions、lex modes），压缩为二进制 blob
2. Java runtime：parser（GLR + graph stack）、subtree arena、tree cursor、query engine、external scanner VM、incremental reparse
3. 加载 binary blob 运行（无需重新编译 grammar）

**优势**：
- 零 native 依赖，零交叉编译
- 单 JAR 分发，跨 win/mac/linux/arm/Android/GraalVM native-image
- 复用全部 upstream grammar（200+）
- 与 Nop 现有 `nop-xlang` 解析层共享类型基础设施（AST node、Range、Location）

**挑战**：
- **GLR 算法复杂**：graph-structured stack + version reuse + conflict resolution（参考 `glr.go` ~2000 行）
- **External scanner**：每个 grammar 有 hand-written C scanner，需要为每个 scanner 写 Java 翻译或做 mini VM 解释字节码
- **Subtree arena**：紧凑 int32 索引，Java 无 struct，必须用 `IntBuffer` 或 `byte[]` 视图
- **GC 压力**：避免每节点一个对象；学习 ast-grep 教训：早期过度 arena 会内存爆炸
- **测试对齐全性**：必须产出与 C runtime **byte-identical** 的 tree，否则 binding 生态不兼容
- **工作量**：单兵 6-12 月达到基本可用（参考 ast-grep 用 AI 加速仍花了数月；gotreesitter 单人长期投入）

### 路线 B：增强现有 JNI 绑定（短期）

**核心思路**：
- 主线用 `tree-sitter-ng`（JDK 8+，100% API 覆盖）
- 用 Zig 自动交叉编译 native lib（已内置）
- 集成 `jtreesitter-type-gen` 在编译期生成类型安全 wrapper
- 用 GraalVM `native-image` + `native-image` 重写 JNI 为 FFM（参考 java-tree-sitter）

**优势**：
- 短期可用（1-2 周集成）
- 立即获得 100% API 覆盖
- 不需要重新实现 runtime

**劣势**：
- 仍有 native lib 分发问题
- GraalVM native-image 兼容性仍有 edge cases
- Android 需要单独 build（NDK）

### 路线 C：混合模式（推荐）

**核心思路**：
- **短期（路线 B）**：在 Nop 中集成 `tree-sitter-ng`，立即可用
- **长期（路线 A）**：用 gotreesitter 经验渐进实现纯 Java runtime，逐 grammar 替换 JNI 路径

## 与 Nop 现有解析层的关系

Nop 现有 `nop-xlang` 提供了：
- AST node 抽象（XLang AST）
- XDef 元模型驱动的 schema 校验
- CodeGenerator 模板

**复用机会**：
- XLang AST 的 `SourceLocation` 可直接对应 Tree-sitter 的 `TSPoint` + `TSRange`
- XLang 的 `XNode` 可包装 `TSNode`
- Nop `JsonTool` 的 token 化经验可用于 lexer 实现
- `nop-report` 已有 source mapping 工具可对比 expected vs actual tree

**潜在冲突**：
- Nop XLang AST 是基于 XLang 元模型定义的；Tree-sitter 的 AST 是基于 grammar.js 动态生成
- 两者可能并存而非替代：Tree-sitter 用于外部代码分析（Java/Python/TS），XLang 用于 Nop 自身 DSL

## 风险评估

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| GLR 实现细节有 bug 导致 tree 与 C runtime 不一致 | 高 | 高 | 用 upstream test corpus 逐 grammar 验证 |
| External scanner 翻译成本高 | 高 | 中 | 先做"无 scanner" 的简单 grammar（JSON）打通管道 |
| Java GC 性能问题 | 中 | 中 | 用 `IntBuffer` 池 + 紧凑索引；参考 ast-grep arena 调优 |
| upstream `parser.c` 表格式变化 | 中 | 高 | 锁定 tree-sitter 版本；ts2java 输出加 schema 版本号 |
| 工作量超过预期 | 高 | 中 | 阶段化交付：先用路线 B 解决短期需求 |

## 阶段化交付路线图

### 阶段 0：基础设施（1 周）

- 选定目标 tree-sitter 版本（建议 v0.25.x，stable API）
- 写 `ts2java` 工具：从 `parser.c` 抽取 parse table → 二进制 blob
- 在 `nop-entropy` 下新增 `nop-xlang-treesitter` 模块（暂用 JNI/FFM）
- 集成 `tree-sitter-json` 作为 smoke test

### 阶段 1：JNI 绑定（2 周）

- 集成 `tree-sitter-ng` 或 `java-tree-sitter`
- 写 Nop wrapper：XLang `XNode` ↔ `TSNode` 适配
- 写 5 个 grammar 的 golden test（json/java/javascript/typescript/python）
- 提供 `treesitter.parse(source, language)` GraphQL action

### 阶段 2：Java runtime MVP（4-6 周）

- 实现 `Subtree` arena（紧凑 int32 索引 + 复用）
- 实现 LR(1) parser + linear stack
- 实现 basic lexer
- 通过 JSON grammar 的全部 corpus test

### 阶段 3：GLR + 复杂 grammar（6-8 周）

- 实现 graph-structured stack + version reuse
- 实现 external scanner VM（mini bytecode interpreter）
- 通过 Java/JavaScript/TypeScript grammar corpus

### 阶段 4：Query + Incremental（4 周）

- 实现 S-expression query compiler
- 实现 `getChangedRanges` + incremental reparse
- 通过 highlight/tags test corpus

### 阶段 5：生产化（持续）

- 性能 benchmark 对比 C runtime
- 错误恢复（error recovery）质量调优
- 文档 + 示例

## 决策点（待用户确认）

1. **路线选择**：A / B / C 中选哪一个作为正式 plan？
2. **目标 JDK**：8+（用 FFM 之外的 JNI）还是 17+/21+（可考虑 Vector API、MemorySegment）
3. **首要 grammar**：JSON（最快验证 runtime）/ Java（最有实用价值）/ TypeScript（最复杂，可压测 scanner）
4. **是否集成到 `nop-xlang`**：作为同级的 `nop-treesitter`，还是作为 `nop-xlang` 的可选 backend？
5. **是否贡献回上游**：与 `tree-sitter/java-tree-sitter` 维护者沟通，看是否愿意合并纯 Java 实现

## Open Questions

- [ ] Java Vector API 是否能加速 lex 阶段的批量 lookup？
- [ ] Java Panama FFM（MemorySegment + Arena）能否替代 byte[] 视图，且对 GC 更友好？
- [ ] gotreesitter 的 `ts2go` 完整覆盖了多少种 `parser.c` 表格式？tree-sitter 各版本间表格式是否稳定？
- [ ] external scanner VM 应该走 RISC 字节码（参考 LLVM bitcode）还是 stack-based（更易调试）？
- [ ] Nop 是否已有 language server 框架可复用？

## References

- 仓库索引：`~/sources/treesitter/README.md`
- 架构剖析：`ai-dev/analysis/2026-09/2026-09-07-tree-sitter-runtime-architecture.md`
- gotreesitter：https://github.com/odvcencio/gotreesitter
- ast-grep 重写经验：https://ast-grep.github.io/blog/tree-sitter-rust-rewrite
- 官方 C API：https://tree-sitter.github.io/
- Show HN: I ported Tree-sitter to Go：https://news.ycombinator.com/item?id=47155597
- AGENTS.md 中 Plan 编写规则
