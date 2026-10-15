# XLang Lezer 语法方案 spike 结论（D5 附）

**日期**：2026-10-16
**范围**：XLang 浏览器端语法支持的三来源裁定、构建链评估、grammar 生产输入；实现归属 item 15
**状态**：active（三来源与范围裁定为最终裁定；follow-up 清单的推进归 item 15+ 演进）
**POC 产物**：`_tmp/xlang-lezer-spike/`（一次性证据，不入版本库——本文档自含全部关键证据）
**上层约束**：[05-frontend-architecture.md](05-frontend-architecture.md) §3.3（构建链落点）

---

## 一、裁定结论

| 来源方案 | 裁定 | 一句话理由 |
| --- | --- | --- |
| ① 参照 nop-idea-plugin 语法规则**新写 Lezer grammar** | **采纳** | POC 全绿（下文）；运行期零重依赖；坑已探明成清单；编辑器只需"够好的结构高亮"，不必复刻编译器词法 |
| ② 复用 nop-treesitter grammar 编写经验 | 拒绝 | treesitter **无现成 xlang grammar**（10 个 blob 逐一确认）；仓内先例是 vendor 上游 parser.c 转 blob，无 grammar.js 编写模式可承；从零写 grammar.js + 自建 blob 流水线 + 前端 WASM/绑定消费，链条最长且前端收益为零 |
| ③ 基于后端 XLangLexer.g4 转译 | 拒绝为主路径，**采纳为 token 语义对照** | 无 ANTLR→Lezer 自动转译工具；g4 的 125 条 lexer 规则含大量编辑器无关内容（被注释的 XML/STRING_EXPR mode 段）；手工转译成本不低于新写。真正价值：token 语义事实源（如模板串**双反引号转义**、`#{`=CpExprStart、`${}`/XML 不在 ANTLR 层）——spike 初版把转义写成 `''` 双写即为对照不严的错误，已按 g4 修正 |

**构建链评估**：flux 仓 `@lezer/generator` 原不存在（pnpm-lock 零命中）；独立目录 `npm install` 即用（版本 1.x，Node 25 兼容）。引入形态：flux-xlang-lang 的 devDependency + `gen:grammar` script + 生成物入库（落点与门禁豁免见 [05-frontend-architecture.md](05-frontend-architecture.md) §3.3）。与纯 tsc 包级 build 兼容（生成物是普通 ESM 源文件）。

## 二、POC 证据（自含）

- **grammar 覆盖面**（`xlang.grammar`，约 110 行）：import（字符串 + 点分路径）/let/function/if-else/for（C 式三段 + for-of）/while/return/块语句；赋值/三元/二元/前缀一元（`!`/`~`/`+`/`-`/`++`/`--`，经 `(UnaryOp | UpdateOp | BinaryOp)?` 前缀超集）/后缀（调用、成员、可选链 `?.`）、箭头函数（块体）、数组/对象字面量；模板串、`#{}` 宏表达式；行/块注释。**样例覆盖面如实陈述**：sample.xlang 覆盖上述全部构造族（含 else 分支/块注释/while/前缀负号）；后缀 `i++` 不在一期（见 follow-up #6）。
- **外部 tokenizer**（`tokens.js`，单 group `xlangExt`）：①反引号模板串整词扫描，转义 = **双反引号**（对齐 g4 L201）；②`#{...}` 括号配对扫描（引号感知、支持嵌套与转义）。
- **解析结果（最终轮实测，2026-10-16）**：代表性样例 `error nodes: 0`，结构断言（function/for-of/ternary/while/if-else/模板串/宏/注释等 12 项）全 ok，防吞体量断言在位；`@lezer/lr` 运行时正常消费外部 tokenizer（`tokenizers: [0,1,2,3,xlangExt]` 形态）。
- **假绿纠偏记录**：中间轮曾报"0 错误"——实为 `LineComment { "//" ![]* }` 的 `![]*` 匹配任意字符含换行，整个样例被吞成单个注释节点（空洞为真）。修正为 `![\n]*` 并给测试加防吞体量断言 + function/for-of/ternary 结构断言后，数字才可信。此教训同时是工程坑 #1。

## 三、grammar 范围裁定（一期 vs follow-up）

一期承诺（item 15）：script 层语法（POC 覆盖面）+ 高亮/折叠/缩进 + 诊断桥。XML 外壳（xpl 文件）编辑复用现有 `xml` 语言（IDEA 双轨同构：XML 手写轨 + 脚本注入轨）。

| # | follow-up | 触发条件 | 已知路径 |
| --- | --- | --- | --- |
| 1 | 模板串 `${}` 插值拆分（嵌套表达式入树） | item 18+ 需要插值内高亮/断点定位精度 | InterpolationStart/End 外部 token + `@context` 深度计数（spike 已验证外部 token 机制可用；`?` 嵌套花括号歧义需 context tracker） |
| 2 | XML 混合嵌套 parse（xpl 整文件） | 编辑整文件的场景进入 item 19/21 范围 | Lezer 混合 parse（html+javascript 同款：Outer→props 委托内层 parser） |
| 3 | 表达式体箭头函数（`x => expr`） | 生产 grammar 补全时 | javascript.grammar 的 `!arrow` precedence 组 + ASI 机制；spike 以"块体强制"回避 |
| 4 | 分号可选化（ASI） | 用户样例兼容需求出现 | javascript.grammar `insertSemi`/`noSemi` 机制；spike 以"强制分号"回避 |
| 5 | `for...of` 之外的迭代形态、`switch`/`try`/`catch`、类与 XML 字面量等 g4 全集 | 按编辑场景实际使用频度渐进 | 以 XLangParser.g4 规则清单对照补齐（59 规则） |

## 四、生产工程注意事项（spike 坑清单全量转移）

1. `![set]` 是**消费字符**的否定匹配，不是零宽断言——`"=" ![=>]` 会吃掉后随空格；`![]*` 会吞掉整个文件（行注释必须 `![\n]*`）。**测试必须带结构/体量断言防"空洞为真"**（本 spike 实际踩中并纠偏）。
2. `@skip { A B C }` 是一个**序列** token；多规则用 `@skip { A | B | C }`。
3. 语句列表使 statement 的 FOLLOW 含其 FIRST——`(` 开头的表达式语句与调用括号歧义；消解组合：语句强制 terminator 收尾 + 控制流体/箭头体强制 Block。
4. 关键字 × Identifier 重叠：字面量 token + @tokens 块内 `@precedence` 排序即可（javascript.grammar 的 `kw<term> { @specialize[@name={term}]<identifier, term> }` 参数化 specialize 是另一正解）。
5. `@precedence` 两层语义不同：@tokens 块内 = token 重叠消解（**先者更高**）；顶层 = 算式优先级。
6. 赋值/三元按 javascript.grammar 模式：`AssignExpression { BinaryExpression AssignOp AssignExpression | TernaryExpression | BinaryExpression }`——三元不能只挂赋值整体的第一分支（RHS 三元会解析失败）。
7. 外部 tokenizer 以 group 名整体导出一个 `ExternalTokenizer`（`export const xlangExt = ...`）；生成器对 `from "./x"` 省略扩展名，Node ESM 下需补 `.js`。
8. **转义/词法语义以 g4 为权威**：编辑器 grammar 与编译器词法分叉会造成高亮/断点错位——item 15 每个争议 token 对照 XLangLexer.g4 裁定（spike 初版 `''` 转义错误即此类）。
9. token 重叠（`+=` vs `+`、`//` vs `/`）必须以 @tokens 内 @precedence 显式排序，否则生成器报 Overlapping tokens。
10. 一元负号：`UnaryOp` 仅 `!`/`~` 不够（g4 含 +/-）——spike 以 `(UnaryOp | UpdateOp | BinaryOp)?` 前缀超集消解同长度 token 争用（语法超集，语义无害）；后缀 `++` 见 follow-up #6。
11. 外部 tokenizer 的 EOF 兜底不得 acceptToken（未闭合 `#{` 会吞到文件尾——"吞没"家族第二例）。

## 五、item 15 实现输入清单

1. 起点 = spike `xlang.grammar`/`tokens.js`（按 §三 follow-up 与 §四坑清单生产化），高亮样式映射对照 g4 token 族（关键字/字符串/模板串/宏/注释/操作符）。
2. 包骨架/构建链/注册接线 = [05-frontend-architecture.md](05-frontend-architecture.md) §3.2/§3.3（registry 三分发点收敛 + generated/ 落点与门禁豁免）。
3. 验收锚点：生产样例超集解析 0 错误（含逐构造结构断言）、既有语言零回归（flux-code-editor 既有 vitest 断言全绿，仓内无快照基线）、`pnpm check` 全门禁绿（豁免清单见 05 §3.3——含 oversized-code-files 的 OVERSIZED_EXEMPTIONS 条目）。
4. 高亮/折叠对照资产：nop-idea-plugin `XLangScriptSyntaxHighlighter`（token→颜色映射先例）与 `docs/dev-guide/xlang/antlr.md`（ParseTree→AST 命名约定）。
