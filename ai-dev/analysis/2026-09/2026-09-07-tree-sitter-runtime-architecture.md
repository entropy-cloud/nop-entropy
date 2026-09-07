# Tree-sitter Runtime Architecture: 移植到 Java 的模块拆解

> Status: open
> Date: 2026-09-07
> Scope: 详细拆解 Tree-sitter C runtime 的子模块，给纯 Java 移植提供组件划分参考
> Source: `~/sources/treesitter/tree-sitter/lib/src/*.c` + `~/sources/treesitter/gotreesitter/*.go`

## Context

要把 Tree-sitter runtime 移植到 Java，需要先理解原 C runtime 的模块边界、数据结构、算法要点。
本文档对照 C 源码与 gotreesitter 的 Go 实现，列出每个子模块的：
- 在 `parser.c` / `subtree.c` / `lexer.c` 中的位置
- 在 gotreesitter 中对应的 Go 文件
- 移植到 Java 时的关键设计点

## 模块总览

```
┌──────────────────────────────────────────────────────────────────┐
│                     tree-sitter runtime                          │
│                                                                  │
│  ┌─────────────┐   ┌─────────────┐   ┌────────────────────────┐ │
│  │   Lexer     │──▶│   Parser    │──▶│  Subtree Arena         │ │
│  │ (lexer.c)   │   │ (parser.c)  │   │   (subtree.c)          │ │
│  │  511 LOC    │   │  2312 LOC   │   │   1095 LOC             │ │
│  └─────────────┘   └──────┬──────┘   └────────────┬───────────┘ │
│                           │                        │             │
│                           ▼                        ▼             │
│                   ┌──────────────┐         ┌──────────────┐       │
│                   │ Stack        │         │ Tree         │       │
│                   │ (stack.c)    │         │ (tree.c)     │       │
│                   │ ~graph-      │         │  182 LOC     │       │
│                   │  structured  │         └──────────────┘       │
│                   └──────────────┘                                │
│                                                                  │
│  ┌─────────────┐   ┌─────────────┐   ┌────────────────────────┐ │
│  │ Query       │   │ Cursor      │   │ External Scanner       │ │
│  │ (query.c)   │   │ (tree_      │   │  (per-grammar C file)  │ │
│  │             │   │  cursor.c)  │   │                         │ │
│  └─────────────┘   └─────────────┘   └────────────────────────┘ │
│                                                                  │
│  ┌─────────────┐   ┌─────────────┐   ┌────────────────────────┐ │
│  │ Language    │   │ Node        │   │ get_changed_ranges      │ │
│  │ (language.c)│   │ (node.c)    │   │ (get_changed_ranges.c)  │ │
│  └─────────────┘   └─────────────┘   └────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

## 1. Lexer（`lib/src/lexer.c`, 511 LOC）

### 职责

把字符流（UTF-8 bytes）转为 token，每个 token 有：
- `type`: symbol id（命名 token 或匿名 token）
- `lookahead_size`: external scanner 需要的 lookahead
- `lookahead_char`: 给 LR parser 的预览字符

### 关键数据结构

```c
struct TSLexer {
    int32_t lookahead;             // -1 表示 EOF
    TSSymbol result_symbol;
    void (*advance)(TSLexer *, bool skip);
    void (*mark_end)(TSLexer *);
    uint32_t (*get_column)(TSLexer *);
    bool (*is_at_included_range_start)(const TSLexer *);
    bool (*eof)(const TSLexer *);
};
```

### 状态

`LexerState`:
- current position (byte offset + char offset)
- current lex mode（lex state）
- included ranges（多片段输入）
- keyword buffer（识别 keyword 时用）
- external scanner state（如果有 external scanner）

### gotreesitter 对应

- `lexer.go` — 主 lexer 循环
- `lex_dfa.go` — DFA 加速
- `external_lexer.go` — external scanner 入口
- `external_vm.go` — external scanner bytecode VM

### 移植到 Java 的设计点

| 难点 | C 方案 | Java 方案 |
|------|--------|----------|
| UTF-8 → codepoint | 直接算字节偏移 | 用 `String.getBytes(UTF_8)` + `ByteBuffer` 计算 |
| `advance` 闭包 | 函数指针 | `Consumer<LexContext>` + 状态对象 |
| keyword buffer | 固定数组 + 长度 | `byte[]` 复用，避免 `String` 分配 |
| 性能 | — | JIT 会内联；考虑 `MethodHandle` 替代 lambda |

## 2. Parser（`lib/src/parser.c`, 2312 LOC）

### 职责

LR parser 的核心：把 token stream 解析为 parse forest。

### 关键数据结构

```c
struct TSParser {
    const TSLanguage *language;
    TSLexer lexer;
    ParserState *states;       // 可变数组
    uint32_t state_count;
    SubtreeHeap subtree_pool;  // subtree arena
    ReduceActionSet reduce_set;
    // ...
};

struct ParserState {
    TSStateId id;              // 当前 LR state
    ParseStateStack stack;     // graph-structured stack 的节点
    uint32_t depth;
    SubtreeArray lookahead;
    // ...
};

struct ParseStateStack {
    ParseStateStackEntry *head;
    ParseStateStackEntry *tail;
    ParseStateStackEntry *capacity;
    ParseStateStackVersion *version_list;
};
```

### GLR 算法要点

1. **LR(1) 主路径**：99% 的输入走单一栈（fast path）
2. **遇到 conflict（shift/reduce 或 reduce/reduce）**：fork 出新栈版本
3. **Version reuse**：图结构共享公共前缀
4. **每个 entry 携带**：LR state、subtree 索引、版本号

### gotreesitter 对应

- `parser.go` — 主循环
- `glr.go` — GLR 算法入口
- `glr_forest.go` — forest（多棵候选树）管理
- `glr_gss.go` — Graph Structured Stack 实现

### 移植到 Java 的设计点

| 难点 | 说明 |
|------|------|
| 版本复用 | 用 `int[]` 存 version number；entry 包含 parent 索引 + branch 索引 |
| 节点 arena | `Subtree[]` 池 + slot reuse；参考 `arena.go` |
| 避免 boxing | state id / symbol id 全用 `int`，不装箱 `Integer` |
| 紧凑布局 | 考虑 `MemorySegment` (JDK 22+) 或 `byte[]` 视图 |

## 3. Subtree Arena（`lib/src/subtree.c`, 1095 LOC）

### 职责

所有 parse tree node 都在一个 arena 中，用 int32 索引引用。

### 关键结构

```c
typedef struct SubtreeHeap {
    SubtreeArray subtrees;
    SubtreeArray free_subtrees;
} SubtreeHeap;

typedef uint32_t Subtree;

struct Subtree {
    bool is_inline;
    TSSymbol symbol;
    SubtreeChildCount child_count;
    // ...
    union {
        // inline 节点（无子树）
        struct {
            TSSymbol symbol;
            TSStateId parse_state;
            int8_t lookahead_char;
        };
        // external 节点
        struct { int32_t external_token_count; /* ... */ };
        // 节点有 children
        struct { uint32_t children; }; // 指向 MutableSubtree 的索引
    };
};
```

### 关键优化

- **inline small nodes**：symbol 是 keyword/punctuation 且无 children 时，直接把数据塞进 Subtree 联合体，省一次内存访问
- **symbol interning**：相同 symbol 只存一份
- **free list**：回收删除的 subtree slot

### gotreesitter 对应

- `arena.go` — arena 主逻辑
- `intern.go` — symbol interning
- `compact_reuse_dependency.go` — slot 复用

### 移植到 Java 的设计点

| 难点 | C 方案 | Java 方案 |
|------|--------|----------|
| union 内存布局 | 直接共用 4 字节 | 用 sealed interface + 多个 record；或 `byte[]` 视图 + 位运算 |
| symbol interning | 直接比 pointer | `Map<String, Integer>` + 数组池 |
| free list | 链表 | `int[] freeNext` 数组 |
| **GC 友好** | 手动内存管理 | **核心挑战**：必须用对象池或紧凑布局，不能每节点一个 `Subtree` 对象 |

参考 ast-grep 教训（`ai-dev/analysis/2026-09/2026-09-07-tree-sitter-rust-rewrite-summary.md` 中记录）：

> "The first arena reserved a huge virtual memory region every time a parser was created... Across a repository, however, that reservation happened thousands of times, and each one cost real work"

→ **Java arena 不能用 `ByteBuffer.allocateDirect(MB)` 一次性 reserve，要按需增长。**

## 4. Stack（`lib/src/stack.c`）

### 职责

LR parser 的 stack，GLR 模式下是 graph-structured。

### 关键操作

- `push(state, subtree)` — 入栈
- `pop()` — 出栈
- `split()` — fork 出新版本（GLR 用）
- `merge(version_a, version_b)` — 合并相同前缀

### 移植到 Java 的设计点

- entry 用 `int[]` 数组存储 `state`, `subtree`, `version`, `link`
- version list 用链表（数组+next 指针）
- 参考 gotreesitter `glr_gss.go`

## 5. Tree（`lib/src/tree.c`, 182 LOC）

### 职责

parse 完成后构造 immutable tree，供 query/cursor 使用。

### 关键结构

```c
typedef struct TSTree {
    Subtree root;
    mutable_subtree_array stack;  // 增量解析用
    TSTreeCursor *cursor;
    // ...
};
```

### 移植到 Java 的设计点

- `TSTree` 是一个轻量 wrapper：只持有 root subtree index + language reference
- query/cursor 都不修改 tree，天然适合 immutable
- Java 可直接做 `record TSTree(Subtree root, Language lang)` 或 `final class`

## 6. Cursor（`lib/src/tree_cursor.c`）

### 职责

高效的 tree 遍历，避免每次访问都构造新对象。

### 关键操作

- `gotoFirstChild()` / `gotoNextSibling()` — O(1)
- `gotoParent()` — O(1)
- `currentNode()` / `currentFieldName()` — O(1)
- 遍历时使用 field name lookup table（来自 grammar）

### 移植到 Java 的设计点

- cursor 用 stack of `CursorFrame`
- 每个 frame 包含：subtree 索引、child index、field name
- 比 iterator 更高效（不分配迭代器对象）

## 7. Query（`lib/src/query.c`）

### 职责

Tree-sitter Query 语言（S-expression）解析与执行。

### 两阶段

1. **Compile**：S-expression → `TSQuery` (内部 IR)
2. **Execute**：在 tree 上跑 query，匹配 pattern

### Query IR

```c
typedef struct TSQuery {
    uint32_t pattern_count;
    uint32_t capture_count;
    char **capture_names;
    TSQueryPredicateStep **patterns;
    // ...
};
```

### 移植到 Java 的设计点

- compile 阶段：解析 S-expression → 构造 `List<QueryPattern>`
- execute 阶段：tree cursor + 模式匹配
- 性能关键：避免每次匹配都遍历 tree；用 cursor 的栈模拟

## 8. Language（`lib/src/language.c`）

### 职责

TSLanguage 结构的加载与查询。

### TSLanguage 结构（从 `parser.c` 抽取的表）

```c
struct TSLanguage {
    uint32_t version;
    uint32_t symbol_count;
    const char **symbol_names;
    TSSymbolMetadata *symbol_metadata;
    TSStateId *large_state_count;
    TSStateId large_state_count;
    const uint16_t *parse_actions;       // 压缩的状态转移表
    const TSLexMode *lex_modes;
    const TSSymbolMetadata *symbol_metadata;
    const TSParseActionEntry *parse_actions;
    // ...大量数组指针
};
```

### ts2java 抽取策略

对照 gotreesitter `cmd/ts2go/extract.go`：

1. 解析 `parser.c` 中 `ts_language_<lang>` 函数体
2. 提取所有 `ts_external_token_...` 数组
3. 提取所有 `sym_<name>` 枚举值
4. 提取 `ts_parse_actions` 大表（int16 packed）
5. 提取 `ts_lex_modes`
6. 提取 `ts_keyword_lex_modes`
7. 提取 `ts_external_scanner_states`（如果有）

输出格式（建议）：
```
language.bin:
  [magic 4 bytes = "TSLB"]
  [version 4 bytes]
  [schema_version 2 bytes]
  [symbol_count 2 bytes]
  [symbols: string table]
  [parse_actions: int16 array, length prefix]
  [lex_modes: TSLexMode array]
  [keyword_lex_modes: ...]
  [external_scanner: optional, embedded bytecode]
```

参考 gotreesitter 的 `language_blob_envelope.go` 看实际 schema。

## 9. External Scanner（每个 grammar 一个 C 文件）

### 职责

处理 grammar.js 表达不出来的"复杂 token 识别"（如 JS 的 template literal ${...}）。

### 接口

```c
typedef struct TSLanguage {
    bool (*external_scanner_create)(void *);
    void (*external_scanner_destroy)(void *);
    unsigned (*external_scanner_scan)(void *, TSLexer *, const bool *);
    // ...
};
```

### 移植到 Java 的方案

参考 gotreesitter 的 `external_vm.go`：每个 external scanner 编译为一组 bytecode 指令，由 Java VM 执行。

指令集（建议 RISC）：

```
PUSH_BYTE b          # push byte onto stack
PUSH_BYTES n         # push n bytes from input
SPAN n               # mark token span
ACCEPT               # accept token
ADVANCE              # advance lexer
SKIP                 # skip whitespace
ERROR                # error recovery
JMP_IF_EQ offset     # conditional jump
```

每个 grammar 的 `scanner.c` 用一个工具（如 `scanner2java`）翻译为 bytecode → 嵌入 language.bin。

## 10. get_changed_ranges（`lib/src/get_changed_ranges.c`）

### 职责

增量解析：给定旧 tree + 编辑，返回哪些 byte range 的子树变化了。

### 算法

1. 在新旧 tree 上同步遍历
2. 找到第一个不匹配的 range
3. 报告所有变化 range

### 移植到 Java 的设计点

- 与 `incremental.go` 配合：parser 重用旧 subtree
- Java 实现相对简单，重点是测试覆盖（gotreesitter 有专门的 incremental test）

## 11. Node（`lib/src/node.c`）

### 职责

`TSNode` 是 cursor 友好的 tree 节点 view。

```c
typedef struct TSNode {
    Subtree subtree;       // 索引到 arena
    TSStateId parse_state;
    uint32_t context[2];   // parent chain
    const TSLanguage *language;
} TSNode;
```

### 移植到 Java 的设计点

- `record TSNode(int subtree, int parseState, int[] context, Language language)`
- 注意 `context` 是循环数组（避免每层 parent 都存）

## 12. Point（`lib/src/point.c`）

### 职责

`TSPoint` = (row, column)，用于错误报告。

```c
typedef struct TSPoint {
    uint32_t row;
    uint32_t column;
} TSPoint;
```

Java：直接 `record TSPoint(int row, int column)`。

---

## gotreesitter 测试规模参考

```
$ ls ~/sources/treesitter/gotreesitter/*_test.go | wc -l
~ 453 个 test 文件
```

覆盖：
- 每个 grammar 的 corpus test（与 upstream C runtime byte-equality 对比）
- incremental parse correctness
- external scanner checkpoint
- arena 复用
- conflict policy
- GLR 多版本正确性

**对纯 Java 实现的意义**：这些测试可以直接 fork 作为 Java 实现的对照 oracle。

---

## 移植优先级建议

| 阶段 | 模块 | 估时 | 验收 |
|------|------|------|------|
| P0 | Subtree arena + Language 加载 + JSON grammar | 2-3 周 | JSON grammar 全部 corpus test |
| P1 | LR(1) parser + basic lexer | 3-4 周 | JSON + Java 部分 test |
| P2 | GLR + conflict resolution | 4-6 周 | Java grammar 大部分 test |
| P3 | External scanner VM | 4 周 | JavaScript/TypeScript grammar |
| P4 | Query engine | 2-3 周 | highlight.scm 全部匹配 |
| P5 | Incremental reparse | 2-3 周 | gotreesitter incremental test |
| P6 | Cursor + performance tuning | 持续 | <2x C runtime 性能 |

## References

- 仓库索引：`~/sources/treesitter/README.md`
- 可行性分析：`ai-dev/analysis/2026-09/2026-09-07-pure-java-tree-sitter-feasibility.md`
- C runtime 源码：`~/sources/treesitter/tree-sitter/lib/src/`
- Go 重写：`~/sources/treesitter/gotreesitter/`
- Rust 重写：`~/sources/treesitter/tree-sitter-rust-rewrite/`
- ast-grep 性能教训：`https://ast-grep.github.io/blog/tree-sitter-rust-rewrite`（特别是 "arena memory explosion" 部分）
