# nop-treesitter

纯 Java 实现的 Tree-sitter runtime。

## 目标

把上游 [tree-sitter](https://github.com/tree-sitter/tree-sitter) 的 C 运行时（`lib/src/*.c`）
用 Java 重写，使 Nop 平台无需 JNI / FFM / native lib 即可使用 200+ Tree-sitter grammar。

参考实现：
- [`odvcencio/gotreesitter`](https://github.com/odvcencio/gotreesitter) — 纯 Go runtime + 206 grammar + `ts2go` parse table 抽取
- [`HerringtonDarkholme/tree-sitter`](https://github.com/HerringtonDarkholme/tree-sitter) — ast-grep 团队的纯 Rust 重写（AI 辅助）

源码参考集合在 `~/sources/treesitter/`（详见 `~/sources/treesitter/README.md`）。

## 模块布局

当前是单模块，后续根据规模可能拆分为 `nop-treesitter-api` / `nop-treesitter-core` / `nop-treesitter-loader`：

```
io.nop.treesitter            public API: TSLanguage, TSParser, TSTree, TSNode, TSQuery
io.nop.treesitter.lexer      lexer 状态机 + keyword 识别
io.nop.treesitter.parser     LR/GLR parser + graph-structured stack
io.nop.treesitter.subtree    Subtree arena（紧凑 int32 索引 + slot 复用）
io.nop.treesitter.query      S-expression query 编译器 + 执行器
io.nop.treesitter.scanner    external scanner 字节码 VM
io.nop.treesitter.language   parse-table 二进制 blob 加载器
io.nop.treesitter.cursor     tree cursor
io.nop.treesitter.util       interning + UTF-8 工具
io.nop.treesitter.provider   语法注册（NopIoC bean + ServiceLoader 扩展点）
io.nop.treesitter.biz        GraphQL @BizQuery 门面（TreeSitter__parseTreeSitter）
io.nop.treesitter.shell      shell 分析语义层（6 项能力：证明型拆分 / 字面量收割 /
                              heredoc 提取 / 动态构造标记 / 路径抽取 / 结构化摘要）
```

## 上游语法来源

vendored 上游 grammar 的来源记录在 `src/test/resources/upstream/grammars/` 下：
每个目录一份 `PROVENANCE.md`（upstream commit SHA、tag、license、generator
版本、逐文件 SHA-256、"never modified" 声明），目录级策略见
`src/test/resources/upstream/grammars/PROVENANCE.md`。

唯一的例外是四个 `*.dsl` 文件——typescript 的三个（`common/` 与两个 split grammar
各一份，内容相同）和 javascript 的一个。它们**不是**上游副本（上游根本不提供
`.dsl`），而是本仓库 scanner-DSL 管线（`ScannerCompiler` / `ScannerVM`）自己的输入。
详见对应目录的 `PROVENANCE.md`。

## 路线图与执行

- Roadmap: `ai-dev/backlog/nop-treesitter-roadmap.md`
- Mission: `missions/nop-treesitter.json`
- 模型: `opencode-go/deepseek-v4-flash` (variant: max)
- 工作分支: `feat-nop-treesitter`

## 构建与测试

```bash
# 在 worktree 根目录
./mvnw -pl nop-treesitter -am test -T 1C
./mvnw -pl nop-treesitter -am clean package -DskipTests -T 1C
```

## Nop 平台使用指南

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-treesitter</artifactId>
</dependency>
```

（版本由平台 BOM `nop-bom` 管理。）

### 直接使用解析 API

```java
Language json = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
TSTree tree = TSParser.parse(json, "{\"a\": 1}");
String sexp = tree.toSExpression();   // (document\n  (object ...))
```

内置语法（provider 注册，NopIoC / GraphQL 可解析）：`json`、`java`、`javascript`、
`typescript`、`tsx`、`python`、`bash` 共 **7** 个，blob 位于模块
`src/main/resources/grammars/<name>/`。另有 `go`、`rust`、`c-sharp` 三个 blob 已交付
但 provider 未注册，只能经 compat 适配类 `TreeSitterGo` / `TreeSitterRust` /
`TreeSitterCSharp` 触达；bash 两条路径都可用（`TreeSitterBash` 与 provider）。

输入有语法错误时不会抛异常——返回的树中包含 `(ERROR ...)` / `(MISSING "token")` 节点
（对齐上游 C runtime 的恢复行为）。

> **已知偏差**：错误恢复在极少数输入上会活锁并改为抛 `TreeSitterException`
> （`error recovery made no progress`），已复现的最小输入是 `echo ${x!y}`——非法 bash，
> 官方 C runtime 恢复为 `(ERROR ...)` 树。已用对照实验定位为 `GLRParser` 的恢复逻辑
> （bash scanner 本身忠实），修复由仓库内部计划承接。行为是**响亮失败**而非静默返回
> 错树。详见模块文档的「已知偏差」。

### NopIoC bean：语法提供者

`app-treesitter.beans.xml`（随模块自动加载）注册了：

Nop IoC **无注解扫描**，每个 bean 都要在这里显式声明：

- `nopTreeSitterLanguageProvider` — `DefaultTreeSitterLanguageProvider`，解析上述七个内置语法名（python 的缩进 scanner 与 bash 的 29 个外部 token scanner 由 provider 自动接线）；
- `io.nop.treesitter.biz.TreeSitterBizModel` — 注入上述 provider；
- `io.nop.treesitter.biz.ShellAnalysisBizModel` — 注入上述 provider，暴露 shell 分析语义层。

### 注册自定义语法

实现 `io.nop.treesitter.provider.ITreeSitterLanguageProvider` 并在 jar 的
`META-INF/services/io.nop.treesitter.provider.ITreeSitterLanguageProvider` 中声明实现类。
`DefaultTreeSitterLanguageProvider` 会通过 `ServiceLoader` 合并第三方 provider；
同名语法 custom 覆盖内置。

### GraphQL

```graphql
query {
    TreeSitter__parseTreeSitter(source: "{\"a\": 1}", language: "json")
    TreeSitterShell__analyzeShell(source: "cat /etc/passwd", language: "bash")
}
```

`TreeSitter__parseTreeSitter` 返回 S-expression 字符串。未知 `language` 返回错误码
`nop.err.treesitter.unknown-language`；`TreeSitterShell__*` 在语法已注册但不是 bash 时
返回 `nop.err.treesitter.unsupported-shell-language`。

### shell 分析语义层

把一段 shell 脚本转成结构化事实，供 AI agent 在执行前收窄判断范围。**不做危险判定、
不执行 shell、不做运行时求值。**

```java
Language bash = Language.fromClasspath("/grammars/bash/tree-sitter-bash-blob.bin");
bash.setExternalScannerFactory(BashScanner::new);      // 29 个外部 token 必须接线
ShellAnalysis analysis = new ShellAnalysis(bash);

// 能力 1：证明型拆分。返回 sealed 的两态之一。
ProvenSplit split = analysis.splitProven("cat /etc/passwd && ls -la");
if (split instanceof ProvenCommandSequence proof) {
    proof.commandNames();          // [cat, ls]  —— 可作为决策依据
} else {
    ((UnprovenScript) split).reason();   // 封闭原因集合，**不提供**命令列表
}

// 能力 2：字面量收割。不完整是构造使然，**绝不可**当作安全证明。
LiteralHarvest harvest = analysis.harvestLiterals("echo \"$(rm -rf /tmp/x)\"");
harvest.commandNames();            // [echo, rm]
harvest.commands().get(1).partial(); // false

// 能力 4：运行时算值的位置（带字节区间，从不求值）
analysis.findDynamicConstructs("rm -rf $TARGET /data");

// 能力 6：结构化摘要，证明状态是一等字段
analysis.summarize("if then rm -rf /data").proofStatus();   // NOT_PROVEN
```

能力 1 与能力 2 的返回类型**无公共父类型**、不可互换：只有前者可用于断定脚本安全。

**三条必须知道的限制**（否则「已证明」会被误读为「安全」）：

1. **「已证明字面量」≠「安全」。** `bash -c '<script>'` / `eval '<script>'` /
   `ssh host '<script>'` / `printf '<script>' | bash` / `trap '<script>' EXIT` 全部
   返回 **PROVEN** 且**无动态构造**——语法上确实没有动态值，但它们执行任意嵌套文本。
   本层回答的是「这里有没有运行时才算得出的值」，**不是**「这安全吗」；后者的判定属于
   agent guardrail，需要额外一层命令形状规则。
2. **返回的 argv 是源码文本，不是执行时的值。** `rm -rf "/data"` 的参数带引号；
   `cat a'b'c` 报 `a'b'c` 而 bash 传 `abc`。精确匹配的消费方需自己先剥引号。
3. **bash 关键字前缀可能被报成命令名。** `time rm -rf /` 报出 `time`。

契约全文见 `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md`。
