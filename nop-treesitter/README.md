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
```

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
