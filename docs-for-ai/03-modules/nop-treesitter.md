# nop-treesitter — 纯 Java Tree-sitter 运行时

## 功能概览

nop-treesitter 把上游 [tree-sitter](https://github.com/tree-sitter/tree-sitter) C 运行时用纯 Java 重写（无 JNI / FFM / native lib），使 Nop 平台可直接消费 200+ 上游 grammar 生态：

- **解析**：GLR 解析器 + 图结构栈（C runtime 语义，线性栈快路径），输出与 C runtime 字节一致的 S-expression 树
- **错误恢复**：`ERROR` / `MISSING` 节点、缺失 token 注入、基于错误成本的变体选择（与上游 C 运行时行为对齐，见 `nop-treesitter/docs/perf-tuning.md` 与 item 11 计划）
- **增量重解析**：`parseIncremental` + `getChangedRanges`（叶子粒度子树复用，结果与全量重解析字节等价）
- **Query**：S-expression 查询编译器 + 执行器（captures / `#eq?` / `#match?` 谓词）
- **外部 scanner**：scanner.c 翻译为字节码 DSL，由 `ScannerVM` 解释执行
- **Nop 集成**：NopIoC 语法提供者 bean + GraphQL `parseTreeSitter` action

内置语法：`json`、`java`、`javascript`、`typescript`、`tsx`、`python`（全量上游 corpus：JS **116/116**、TS 110/111、TSX 110/111（唯一裁定节为 GLR tie-break 差异）、Python **115/117**（2 个多轮恢复形状裁定节）。

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-treesitter</artifactId>
</dependency>
```

### 直接使用解析 API

```java
Language json = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
TSTree tree = TSParser.parse(json, "{\"a\": 1}");
String sexp = tree.toSExpression();
```

输入有语法错误时**不抛异常**——返回的树包含 `(ERROR ...)` / `(MISSING "token")` 节点。

### NopIoC / GraphQL

模块的 `nop-treesitter/src/main/resources/_vfs/nop/treesitter/beans/app-treesitter.beans.xml` 注册 `treeSitterLanguageProvider`（语法解析）与 `TreeSitterBizModel`（GraphQL 门面）。GraphQL 查询：

```graphql
query {
    TreeSitter__parseTreeSitter(source: "{\"a\": 1}", language: "json")
}
```

未知 `language` 返回错误码 `nop.err.treesitter.unknown-language`。

### 注册自定义语法

实现 `io.nop.treesitter.provider.ITreeSitterLanguageProvider`，在 jar 的
`META-INF/services/io.nop.treesitter.provider.ITreeSitterLanguageProvider` 声明实现类。
`DefaultTreeSitterLanguageProvider` 通过 `ServiceLoader` 合并第三方 provider，
**同名语法 custom 覆盖内置**。

## 架构要点

| 组件 | 位置 | 说明 |
| --- | --- | --- |
| 公共 API | `io.nop.treesitter` | `TSParser` / `TSTree` / `TSNode` / `TSQuery` |
| parse-table blob | `io.nop.treesitter.language` + `codegen` | `parser.c` 提取为二进制 blob（格式 v4，`src/main/resources/blob-format.md` 为权威规格），运行时独立解码器交叉校验 |
| GLR + 恢复 | `io.nop.treesitter.parser.glr` | 图结构栈版本、三态 pause/resume、C `ts_parser__handle_error` 全序恢复 |
| 增量 | `io.nop.treesitter.parser.incremental` | `TSInputEdit`/`TSPoint`/`TSRange` 值类型 + 叶子粒度复用 + changed ranges |
| arena | `io.nop.treesitter.subtree` | 并行 `int[]` 列（无 per-node 堆对象），free-list 复用 |
| lexer | `io.nop.treesitter.lexer` | blob 内 DFA + keyword capture + 错误模式字符跳过 |
| scanner VM | `io.nop.treesitter.scanner` | scanner.c 的字节码 ISA + 解释器 |
| query | `io.nop.treesitter.query` | S-expression 模式编译 + 匹配 |
| compat 迁移层 | `io.nop.treesitter.compat` | 镜像 `org.treesitter`（bonede JNI）API 形状的桥接：`TSParser`/`TSTree`/`TSNode`/`TSPoint` + `TreeSitter{Python,Typescript}` 适配器。python 的缩进 scanner 由 `TreeSitterPython` 静态接线 `PythonScanner` 工厂（blob 内不含该 scanner），消费方无感 |
| provider/biz | `io.nop.treesitter.provider` / `.biz` | NopIoC bean + GraphQL 门面 |

**JNI 迁移状态**：`nop-code-lang-python` / `nop-code-lang-typescript` 已从
bonede JNI 依赖迁移到本模块 compat 层（仅换 import 与 pom 依赖，分析器逻辑
不变），仓库内已无 compile 作用域的 JNI 依赖（`nop-treesitter` 自身仅 test
scope 保留 JNI 用于等价性验证测试）。

锁定上游 tree-sitter v0.25.x 的 `parser.c` 格式。恢复/渲染语义与 C runtime
对齐（JS corpus 全量字节通过）；已知偏差：等错误成本下的多轮恢复树形、
`B<C>(D)<E>` 类继承 tie-break（详见仓库 ai-dev 计划目录的 error-recovery 计划）。

## 性能特征

Java vs C（同工作量 parse+serialize，arm64 开发机，JMH vs gcc -O2）：
json 10K **2.29x**、100K **2.56x**（≤3x 目标内）；json 1M 3.50x、java 8K 4.28x
（超目标，差距归因与优化候选见 `nop-treesitter/docs/perf-tuning.md`）。
分配强度 ≈550–600 B/源字节；arena ≈1 节点/2.2 源字节。

## 源码锚点

见 `04-reference/source-anchors.md` 的 `TS-001`..`TS-004`。

## 相关文档

- `nop-treesitter/README.md` — 用户指南（安装/注册/GraphQL 示例）
- `nop-treesitter/docs/perf-tuning.md` — 基准方法、Java vs C 数字、调优记录
- `nop-treesitter/docs/wiki.md` — 一页式导航
- `nop-treesitter/src/main/resources/blob-format.md` — blob 二进制格式权威规格
