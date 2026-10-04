# nop-treesitter — 纯 Java Tree-sitter 运行时

## 功能概览

nop-treesitter 把上游 [tree-sitter](https://github.com/tree-sitter/tree-sitter) C 运行时用纯 Java 重写（无 JNI / FFM / native lib），使 Nop 平台可直接消费 200+ 上游 grammar 生态：

- **解析**：GLR 解析器 + 图结构栈（C runtime 语义，线性栈快路径），输出与 C runtime 字节一致的 S-expression 树
- **错误恢复**：`ERROR` / `MISSING` 节点、缺失 token 注入、基于错误成本的变体选择（与上游 C 运行时行为对齐，见 `nop-treesitter/docs/perf-tuning.md` 与 item 11 计划）
- **增量重解析**：`parseIncremental` + `getChangedRanges`（叶子粒度子树复用，结果与全量重解析字节等价）
- **Query**：S-expression 查询编译器 + 执行器（captures / `#eq?` / `#match?` 谓词）
- **外部 scanner**：scanner.c 翻译为字节码 DSL，由 `ScannerVM` 解释执行
- **Nop 集成**：NopIoC 语法提供者 bean + GraphQL `@BizQuery parseTreeSitter` 与 `@BizQuery analyzeShell` 等门面

**内置语法的两层口径**（两者不同，勿混）：

| 层 | 语法 | 触达方式 |
|---|---|---|
| provider 注册（`BUILTIN_BLOBS`，NopIoC / GraphQL 可解析） | `json`、`java`、`javascript`、`typescript`、`tsx`、`python`、**`bash`**（7 个） | `DefaultTreeSitterLanguageProvider` / `ITreeSitterLanguageProvider.getLanguage(name)` |
| blob 已交付（provider 未注册） | 追加 `go`、`rust`、`c-sharp`（共 10 个） | 仅经 compat 适配类 `TreeSitterGo` / `TreeSitterRust` / `TreeSitterCSharp` / `TreeSitterBash` |

上游 corpus 实跑结果：bash **100/100**（5 文件 100 section，0 裁定节；scanner 为
`scanner.c` v0.25.1 全部 29 个外部 token 的忠实翻译，对照官方 tree-sitter CLI 0.25.10）。
其余语法的全量上游 corpus：JS **116/116**、TS 110/111、TSX 110/111（唯一裁定节为 GLR tie-break 差异）、Python **115/117**（2 个多轮恢复形状裁定节）、Go **65/67**（2 个多轮恢复形状裁定节；en route 修复 `Language.decode` 对"无 external scanner 但带 reserved words"grammar 的校验）、Rust **145/151**（6 个多轮恢复形状裁定节，逐条 C-oracle 对照；en route 修复两项 runtime 缺陷——`ts_lex_modes` 的 `(TSStateId)(-1)` no-lookahead 状态（此前被误读为主 lexer 模式）与 `mark_end` 缺省语义（C 语义:末次调用定 token 末尾,未调用取 return 位置））、C# **177/179**（2 条裁定:`#line`+方法声明组合的恢复形状差异与错误恢复-插值零宽交互，对照官方 tree-sitter CLI 0.25.8 corpus 实跑；scanner 为插值栈/raw string/lambda 投机扫描的忠实翻译）。

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

> **已知偏差（successor 承接中）**：错误恢复在极少数输入上会活锁并改为抛
> `TreeSitterException`（`error recovery made no progress`）。已复现的最小输入是
> `echo ${x!y}`——非法 bash，官方 C runtime 恢复为 `(ERROR ...)` 树，本运行时抛异常。
> 已用对照实验定位为 `GLRParser` 的恢复逻辑（不挂外部 scanner、挂 always-false stub
> 均正常），bash scanner 本身忠实；修复由仓库内部计划目录下的一份
> GLR 恢复活锁修复计划承接。行为是**响亮失败**而非静默返回错树，因此调用方不会拿到
> 错误结论，只会拿不到结论。

### NopIoC / GraphQL

Nop IoC **无注解扫描**，每个 bean 都必须在
`nop-treesitter/src/main/resources/_vfs/nop/treesitter/beans/app-treesitter.beans.xml`
显式声明。该文件注册 3 个 bean：

| bean id | 类型 | 作用 |
|---|---|---|
| `nopTreeSitterLanguageProvider` | `DefaultTreeSitterLanguageProvider` | 内置语法 blob + 第三方 provider 合并 |
| `io.nop.treesitter.biz.TreeSitterBizModel` | `@BizModel("TreeSitter")` | 解析门面 |
| `io.nop.treesitter.biz.ShellAnalysisBizModel` | `@BizModel("TreeSitterShell")` | shell 分析语义层门面 |

GraphQL 查询（均为 `@BizQuery`，平台无 "action" 概念）：

```graphql
query {
    TreeSitter__parseTreeSitter(source: "{\"a\": 1}", language: "json")
    TreeSitterShell__splitProvenShellCommands(source: "cat /etc/passwd", language: "bash")
    TreeSitterShell__analyzeShell(source: "cat /etc/passwd", language: "bash")
    TreeSitterShell__listLiteralCommands(source: "rm -rf $TARGET /data", language: "bash")
    TreeSitterShell__extractShellHeredoc(source: "cat <<EOF\nx\nEOF", language: "bash", allowedCommands: [])
}
```

错误码：

| 错误码 | 触发条件 |
|---|---|
| `nop.err.treesitter.unknown-language` | `language` 未注册 |
| `nop.err.treesitter.unsupported-shell-language` | 语法已注册但不是 bash（按 18 个必需节点名探测），语义层契约只对 bash 成立 |

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
| compat 迁移层 | `io.nop.treesitter.compat` | 镜像 `org.treesitter`（bonede JNI）API 形状的桥接：`TSParser`/`TSTree`/`TSNode`/`TSPoint` + `TreeSitter{Python,Typescript,Go,Rust,CSharp}` 适配器。python 的缩进 scanner 由 `TreeSitterPython` 静态接线 `PythonScanner` 工厂、rust 的 raw-string/float/comment scanner 由 `TreeSitterRust` 接线 `RustScanner`、C# 的插值/raw string/lambda scanner 由 `TreeSitterCSharp` 接线 `CSharpScanner`、bash 的 29 个外部 token scanner 由 `TreeSitterBash` 接线 `BashScanner`（blob 内均不含 scanner），消费方无感。bash 另有 provider 路径：`loadLanguage("bash")` 同样接线 `BashScanner::new`，两条路径都必须能解析含 heredoc 的脚本 |
| provider/biz | `io.nop.treesitter.provider` / `.biz` | NopIoC bean + GraphQL 门面 |
| shell 分析语义层 | `io.nop.treesitter.shell` | tree 之上 6 项能力（见下节）；只产出结构化事实与「可证明/不可证明」状态，不做危险判定、不执行 shell |

**JNI 迁移状态**：`nop-code-lang-python` / `nop-code-lang-typescript` 已从
bonede JNI 依赖迁移到本模块 compat 层（仅换 import 与 pom 依赖，分析器逻辑
不变），仓库内已无 compile 作用域的 JNI 依赖（`nop-treesitter` 自身仅 test
scope 保留 JNI 用于等价性验证测试）。

锁定上游 tree-sitter v0.25.x 的 `parser.c` 格式。恢复/渲染语义与 C runtime
对齐（JS corpus 全量字节通过）；已知偏差：等错误成本下的多轮恢复树形、
`B<C>(D)<E>` 类继承 tie-break（详见仓库 ai-dev 计划目录的 error-recovery 计划）。

## 公共 API：primary vs compat

primary `TSNode` 提供结构访问器（`type` / `named` / `isVisible` / `isExtra` /
`startByte` / `endByte` / `cursor` / `parent` / `nextSibling` / `childCount` /
`namedChildCount` / `child(int)` / `namedChild(int)`）以及两个 fail-closed 谓词：

| 方法 | 语义 | 边界行为 |
|---|---|---|
| `hasError()` | 以该节点为根的子树中是否存在 `ERROR` / `_ERROR` / 零宽 `MISSING` 节点（**含 ERROR 子树内部**，走 arena 子节点列表而非可见节点导航） | 无 |
| `childByFieldName(String)` | 该节点 production 中占据该 field 槽的首个可见子节点（C `ts_node_child_by_field_name`），底层复用 `TSTreeCursor` 的 field 导航 | `null` 入参、语法未定义的 field 名、本节点不使用该 field，三者一律返回 `null`（不抛异常、不返回根节点冒充成功） |

compat `TSNode.hasError()` / `getChildByFieldName(...)` **委派到 primary 实现**，两者不得漂移（compat 层的存在意义是"只换 import、行为不变"）。漂移会让迁移失去意义，因此一致性由 `cursor/TSNodeErrorAndFieldTest` 在同一棵树上断言。

> **已知偏差（successor 拥有）**：`compat` 的 `hasError()` 此前只看 `ERROR` / `_ERROR` 符号、不看 `MISSING`，现已与 primary 对齐为 C 语义的 fail-closed 读法。

## shell 分析语义层（`io.nop.treesitter.shell`）

把一段 shell 脚本转成结构化事实，供 AI agent 在执行前收窄判断范围。**不做危险判定、不执行 shell、不做运行时求值**——"这条命令危不危险"是 agent guardrail 的职责。

### 6 项能力

| 能力 | 回答什么 | 能否用来断定"这里没有运行时才算得出的值" |
|---|---|---|
| 1 `splitProven` | 这段脚本**在语法上**恰好做什么 | **能**，当返回 proven 时。**不等于"安全"**，见下方限制 1 |
| 2 `harvestLiterals` | 脚本任何位置（含控制流/管道/命令替换内）出现的字面量命令有哪些 | **不能，绝不** |
| 3 `extractHeredoc` | 脚本"只有一条裸命令 heredoc 语句"时的 body | 仅对 heredoc 自身文本 |
| 4 `findDynamicConstructs` | 哪些位置会在运行时算值（带字节区间） | 它只报告"存在动态值"，不报告值 |
| 5 `extractPaths` | 候选字面量文件路径（带读写方向与置信度） | 仅 proven 来源且全字面量的路径 |
| 6 `summarize` | 以上全部 + 证明状态的一个记录 | 取决于它携带的证明状态 |

"能否断定安全"**不是**本层回答的问题，见下方限制 1。

### 三条必须知道的限制（否则「已证明」会被误读为「安全」）

1. **「已证明字面量」不等于「安全」。** 语法全字面量的一行仍可以执行任意嵌套文本：
   `bash -c 'rm -rf /data'`、`sh -c "..."`、`eval '...'`、`ssh host '...'`、
   `printf 'rm -rf /data' | bash`、`trap '...' EXIT` 都返回 **PROVEN**，且
   `findDynamicConstructs` 报告**空**——因为语法上确实没有任何动态构造。这与成熟参考
   实现的行为一致，但参考实现把这类输入当作**危险样例**来检测。消费方若要覆盖它们，
   必须在语义层之外再加一层命令形状规则（例如对 `-c` 参数、`eval`、`trap`、管道右侧的
   解释器单独判定）。本层不做这一层，也不假装做了。
2. **能力 1 返回的 argv 是源码文本，不是执行时的值。** `rm -rf "/data"` 的参数是
   `""/data""`，`cat a'b'c` 的参数是 `"a'b'c"`（bash 传的是 `abc`）。做**精确匹配**
   的消费方必须自己先做一层引号剥离，否则会漏。词法判据已经处理了"引号会移除转义"
   这一类会影响**是否字面量**的情况，但它不去计算值。
3. **能力 1 可能把 bash 关键字前缀报成命令名。** `time rm -rf /` 与
   `coproc rm -rf /` 都以 `time` / `coproc` 作为 `command_name` 报出，后续参数另起
   一条命令。按名字做判定的消费方需要知道这一点。

### 能力 1 与能力 2 的类型级隔离

两者返回**无公共父类型**的两个 final 类型：

- `ProvenCommandSequence`（implements `ProvenSplit`）带 `isProvenLiteral()`；
- `LiteralHarvest` 带 `isNotASafetyProof()`。

`ProvenSplit` 是 `sealed interface permits ProvenCommandSequence, UnprovenScript`，消费方的 `switch` 穷尽且新增分支会在所有消费点编译失败。误用（把 harvest 传给只接受 proof 的入口）**由编译失败验证**，见 `shell/ShellTypeIsolationTest`（用 `javax.tools.JavaCompiler` 实编译误用片段并断言失败），而不是靠注释与更窄的可见性。

### 不可证明是一等状态

`splitProven` 返回 `UnprovenScript`（`sealed` 的第二态），携带**封闭的**原因集合与字节偏移。
原因集合恰为 5 项，多一项少一项都是契约变更：

| 原因 | 含义 |
|---|---|
| `NO_COMMAND` | 脚本里没有命令：空、纯空白，或树中无 `command` 节点 |
| `SYNTAX_ERROR` | 子树含 `ERROR` / `_ERROR` / 零宽 `MISSING` |
| `CONSTRUCT_OUTSIDE_WHITELIST` | 出现白名单之外的节点（如 `declaration_command` / `negated_command` / `( )` / `{ }` / 后台 `&` / 注释） |
| `DYNAMIC_CONSTRUCT` | 出现展开、替换、重定向等运行时才有值的构造 |
| `NON_LITERAL_LEXICAL_FORM` | 白名单内节点的文本不是字面量（glob / `~` / 花括号 / 受引号影响的转义 / 混合 `concatenation`） |

三条不变量：

- 子树含语法错误一律不可证明，无例外；
- `UnprovenScript` **不提供**任何返回命令列表的访问器——"空命令列表"会被读成"这段脚本什么都不做"，是最危险的误读；
- **已证明的序列永不为空**。`""` / `" "` / `"
"` 一律返回 `NO_COMMAND` 拒绝，而不是
  "已证明、零条命令"——后者与"什么都没找到"在消费方眼里无法区分。

### 白名单是封闭正向集合

可证明脚本中允许出现的命名节点只有 11 项（`program` / `list` / `pipeline` /
`command` / `command_name` / `word` / `number` / `string` / `string_content` /
`raw_string` / `concatenation`），允许的匿名 token 只有 6 个（`&&` `||` `;` `|` `"` `'`）。
显式在集合外并各有测试的构造：`declaration_command`（否则 `declare -x A=1 rm -rf /data`
会被读成已证明命令序列）、`test_command`（`[[ ]]`）、`negated_command`、`command` 内的
`variable_assignment`、三个 heredoc 节点、`file_redirect` / `redirected_statement`、
`( )` `{ }`、后台 `&`、四种展开/替换、`concatenation`（**字面量与展开的混合片段**，
漏标会让 `a"$X"b` 报"无动态构造点"= 安全漏洞）、全部控制流与 `function_definition`。

词法判据同样封闭：`word`/`number` 无命名子节点、不以 `=` 开头、不含
`{ } * ? [ ] \ ~ ^ # $ \``；双引号串全部命名子节点必须是 `string_content`，
且原文不含对 `$ \` `" \\ \n` 的转义（双引号抑制 glob 但仍移除转义；
`\t`/`\n` 被双引号保留，故不使其非字面量）；`concatenation` 逐 part 递归。
**未识别的节点类型一律判为非字面量**——早先的宽松缺省曾让 `simple_expansion`
落进"结构节点"分支返回 `true`，把 `rm -rf $TARGET /data` 的 `$TARGET` 收割成普通参数。

### 能力 2 的 partial 标记

遇到读不出的 child 时**跳过并继续**会让 `rm -rf $TARGET /data` 报成
`["rm","-rf","/data"]`，`/data` 看起来像字面目标而参数表其实并不完整——**高估**已知信息；
截断则**低估**并丢掉字面量部分。两者对 guardrail 都不可接受，因此该命令报为
`partial`：保留读到的字面量词 + 显式标记，让消费方自己判断。

### 坐标：byte offset，不是 char index

tree-sitter 的坐标是**字节偏移**，而 Java 的字符串不是字节。早期实现用
`source.substring(node.startByte(), node.endByte())` 取节点文本，于是任何含多字节字符的
脚本都会越界切片——`echo é; rm -rf /data` 会把破坏性命令报成名为 `m`、目标为空的命令，
**而且仍然是 PROVEN**。所有取文本的地方统一走 `ShellSource`（按字节切片后解码 UTF-8），
而不是各自分解码后的字符串。上游 corpus 是纯 ASCII，因此 corpus 100/100 抓不到这个问题；
`ShellProvenSplitTest` / `ShellPathExtractionTest` / `ShellHeredocExtractionTest` 各有
非 ASCII 回归断言。

### heredoc 提取走节点遍历

query 引擎明确不支持 anchor `.`（`query/TSQueryParser.java:83,199`）与 `#any-of?`
（`:234`），而成熟参考实现的 heredoc 约束正是由首尾 anchor 表达的。因此**节点遍历是
唯一路线**，不为单一消费方扩展共享组件。只接受两种形态：直接形态（根只有一个命名
子节点 → `redirected_statement` → `redirect:` 唯一子节点是 `heredoc_redirect` →
其三个命名子节点依次为 `heredoc_start` / `heredoc_body` / `heredoc_end`），与
`cd <one-arg> && <allowed-command>` 前置形态（分隔符**必须是字面 `&&`**，`;`/`||`/`|`
一律不匹配）。herestring（`<<<`）是另一个节点，不在本能力契约内。

### 动态构造点是封闭枚举

`command_substitution`（`$( )` 与反引号**同一节点**）/ `simple_expansion` /
`expansion` / `arithmetic_expansion` / `process_substitution` / `concatenation` /
`variable_assignment` / `file_redirect` / `redirected_statement` / heredoc 三节点 /
后台 `&`。后台 `&` 没有**命名**节点类型（是匿名 token），只走命名节点的遍历必然漏报它。

### 路径抽取不做存在性检查

六维度：来源命令的参数位置约定（`--` 之后一律为路径）、字面量形态（排除 `~` 前缀、
glob 元字符、未解引号的展开）、**来源置信度标注**、flag 排除、重定向目的地单列为
**WRITE** 类、不做文件系统存在性检查（否则就不是"字面量抽取"，且输出会依赖分析所在的
机器）。刻意**不**纳入参考实现"剥离 `head`/`tail`/`awk`/`wc` 以偏向主命令"的启发式：
它丢掉的正是 guardrail 最需要看到的路径。

四���容易做错、已各自有断言的细节：

- **按"参数角色"判定，不按位置表判定。** 几乎所有触碰文件的命令，其非 flag 参数
  **全是**路径，因此规则就是"全部是路径"——位置表无法安全表达这件事：一个自身就是
  文件的 flag 操作数（`grep -f <patterns>`）会把其后所有位置整体右移一位，而按位置
  判定时**搜索模式会被当成脚本读取的文件报出来**。因此 grep 族与 `chmod` / `chown`
  额外要求操作数**看起来像路径**（含分隔符或以 `.` 开头），模式无论落在哪一槽都被拒。
  `head -n 5` 的 `5` 这类"flag 的值"按纯数字或 `%` 格式串识别并剔除（代价是也会丢掉
  一个纯数字文件名——这是有意的取舍：漏一个路径可恢复，多报一个不是路径的路径不可恢复）。
- **整体加引号的路径要剥引号后再判定。** `cat "/etc/passwd"` 与 `cat /etc/passwd`
  指的是同一个文件；把引号字符直接判为非字面量会丢掉恰恰是最谨慎的写法。剥引号后仍含
  `$` / glob 的（`"$HOME/x"`）照旧拒绝。
- **置信度由整次抽取所用的那个判定给出，不由更弱的语法事实给出。** 一条含重定向的
  脚本不是 proven（重定向是动态构造），因此它的重定向目标也是 `HARVESTED` 而不是
  `PROVEN`。三档：`PROVEN`（来自证明）、`HARVESTED`（来自读全的收割 argv）、
  `HARVESTED_PARTIAL`（来自 `partial` 的收割 argv，参数集是**子集**，该命令可能还命名了
  列表里没有的路径）。三档不可混同。
- **重定向方向看运算符，不看节点类型。** `>` `>>` 是**写**，`<` 只可能是**读**；运算符是
  `file_redirect` 的匿名子节点，因此方向可以直接读到，不需要重新解析文本。
  `2>&1` 里的 `2` 是文件描述符不是文件，直接跳过。命令**参数**本身的方向不在本能力
  范围内（`tee /tmp/out` 是写、`cat /tmp/out` 是读，而本能力抽字面量不判意图）。

### GraphQL 边界的证明状态

`ProvenSplit` 的 sealed 结构无法穿过序列化边界，因此跨 GraphQL 时证明状态是
`summarize` / `splitProvenShellCommands` 返回值上的**显式 `proofStatus` 字段**；
结果展平为普通 map 而非注册 schema object type，就是为了让该字段无法被忽略。
`ShellAnalysisBizModel` 在调用前按 18 个必需节点名探测语法，非 bash 语法抛
`nop.err.treesitter.unsupported-shell-language` 而不是产出自信的错误结果。

设计裁定与实测指纹不在本仓库的对外文档范围内，见仓库内部 `ai-dev/` 下对应的
design / analysis 文档。

## 性能特征

Java vs C（同工作量 parse+serialize，arm64 开发机，JMH vs gcc -O2，2026-09-13
perf-closure 后）：json-10k **1.20x**、json-100k **1.26x**、json-1m **1.56x**、
java-single **2.76x**，四基准全部在 ≤3x 目标内；纯 Java 101.4 ops/s vs JNI 45.2。
优化史与残余归因见 `nop-treesitter/docs/perf-tuning.md`。
分配强度 ≈550–600 B/源字节；arena ≈1 节点/2.2 源字节。

## 异常处理约定

模块遵循 `docs-for-ai/02-core-guides/error-handling.md` 的两档策略，以下为本模块的裁定与边界（2026-09-20，plan 2272）：

- **业务/公共 API 路径**：模块异常类 `TreeSitterException`（继承 `NopException`，英文自由文本消息）与 GraphQL 门面的 `NopException + ErrorCode + .param(...)`（`TreeSitterErrors`）。
- **`IllegalStateException` / `IllegalArgumentException` 允许使用**（用户裁定）：用于 blob/lexer/scanner 程序格式校验、GLR/arena 不变式防护、`codegen` 提取工具（`ParserCExtractor`/`BlobWriter`/`Ts2Java`）的 fail-fast。不要求转换为模块异常类。
- **JDK 标准语义异常同样豁免**：`Subtree.child` 的 `IndexOutOfBoundsException`（对齐 `List.get` 集合契约）、未知 parse action type 抛 `UnsupportedOperationException`（快速失败的规范认可形态）。
- **catch 纪律**：全模块 catch 仅允许"rethrow with cause"或"尝试性解析失败 → 替代解析 → 显式兜底 throw"两种形态；不得用异常做控制流、不得静默降级（如需容错渲染须显式条件检查，参见 `GLRParser.appendTree`）。

## 源码锚点

见 `04-reference/source-anchors.md` 的 `TS-001`..`TS-004`。

## 相关文档

- `nop-treesitter/README.md` — 用户指南（安装/注册/GraphQL 示例）
- `nop-treesitter/docs/perf-tuning.md` — 基准方法、Java vs C 数字、调优记录
- `nop-treesitter/docs/wiki.md` — 一页式导航
- `nop-treesitter/src/main/resources/blob-format.md` — blob 二进制格式权威规格
