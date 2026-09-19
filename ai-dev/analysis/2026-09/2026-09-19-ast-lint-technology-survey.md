# AST Lint 技术深度调研报告

> 日期: 2026-09-19
> 目标: 为 Nop 平台设计 YAML 驱动的 AST lint 系统提供技术基础

## 1. 调研范围

| 工具 | 语言 | 核心技术 | Stars | 定位 |
|------|------|---------|-------|------|
| ESLint | JS | Espree AST + Visitor | 25k+ | JS/TS 代码检查 |
| ast-grep | Rust | tree-sitter CST + Pattern | 15.7k | 结构化搜索/重写 |
| Semgrep | OCaml | tree-sitter + Generic AST | 10k+ | 安全扫描/代码检查 |
| GritQL | Rust | tree-sitter + Rewrite DSL | 2k+ | 代码转换/重写 |
| Checkstyle | Java | 自研 Java parser | 8k+ | Java 风格检查 |
| PMD | Java | JavaCC + Symbol Table | 8k+ | Java bug 检测 |
| ErrorProne | Java | javac 内部 AST | 7k+ | 编译时语义检查 |
| SpotBugs | Java | BCEL 字节码分析 | 5k+ | 字节码 bug 检测 |

## 2. 架构对比

### 2.1 解析层

| 工具 | 解析器 | AST 类型 | 增量解析 | 多语言 |
|------|--------|---------|---------|--------|
| ESLint | Espree (ESTree) | AST | ❌ | 仅 JS/TS |
| ast-grep | tree-sitter | CST | ✅ | 27+ |
| Semgrep | tree-sitter + pfff | Generic AST | ❌ | ~30 |
| GritQL | tree-sitter | CST | ✅ | ~15 |
| Checkstyle | 自研 JavaParser | DetailAST | ❌ | 仅 Java |
| PMD | JavaCC | 类型化 AST | ❌ | ~10 |
| ErrorProne | javac 内部 API | javac Tree | ❌ | 仅 Java |
| **nop-treesitter** | **纯 Java GLR** | **CST** | **✅** | **6** |

**关键发现**：tree-sitter 已成为 AST lint 事实标准解析器。nop-treesitter 是唯一的纯 Java 实现，具备独特优势。

### 2.2 Pattern Matching 算法

#### ESLint: Visitor 模式

```
Rule.create(context) → { NodeType(node) { ... } }
```

- 基于 ESTree 节点类型的事件驱动
- CSS 选择器支持（esquery）：`IfStatement > BlockStatement`
- 无 pattern 语言，必须用 JavaScript 编写匹配逻辑

#### ast-grep: CST Pattern Matching

```
YAML rule → pattern string → tree-sitter parse → Pattern AST → match against target CST
```

- **Meta-变量**: `$VAR` 匹配单个命名节点，`$$$VAR` 匹配零或多个
- **6 级 strictness**: cst → smart → ast → relaxed → signature → template
- **关系规则**: `inside`, `has`, `follows`, `precedes`
- **组合规则**: `all`, `any`, `not`
- **递归规则**: `matches: util-id` 支持自引用

**核心算法**：模式编译为 matcher 树 → 目标 CST 预序遍历 → 每节点尝试所有 pattern → 子节点匹配用 split-state step machine

#### Semgrep: Generic AST Semantic Matching

```
Pattern → language AST → AST_generic.ml (通用 IR) → Generic_vs_generic.ml matcher
```

- **省略号 `...`**: 匹配零或多个序列元素
- **深度表达式 `<... pattern ...>`**: 递归匹配嵌套子表达式
- **常量传播**: 通过赋值解析常量
- **AC 匹配**: 结合律/交换律匹配（`&&`, `||`, `+`）
- **类型化元变量**: `(java.util.logging.Logger $X).log(...)`

#### GritQL: Pattern + Rewrite DSL

```
GritQL query → pattern AST → tree-sitter CST match → rewrite in-place
```

- **一等重写**: `` `println($msg)` => `console.log($msg)` ``
- **Where 子句**: `<:`, `not`, `within`, `contains`, `after`, `some`, `bubble`
- **多文件模式**: 跨文件重构

### 2.3 规则配置格式

| 工具 | 格式 | 可编程性 | 自动修复 |
|------|------|---------|---------|
| ESLint | JS config + JSON Schema | 完全可编程 | ✅ multipass fixer |
| ast-grep | YAML | 声明式 | ✅ fix pattern |
| Semgrep | YAML | 声明式 + 模式 | ✅ autofix |
| GritQL | GritQL inline / YAML | DSL 可编程 | ✅ 一等 rewrite |
| Checkstyle | XML | 需 Java 编码 | ❌ |
| PMD | XML / Java / XPath | Java 或 XPath | ❌ |
| **目标系统** | **YAML** | **声明式 + 可扩展** | **✅** |

## 3. 性能基准（500 JS 文件，~75K LOC）

| 工具 | 简单搜索 | 变换 | 复杂搜索 | 内存 |
|------|---------|------|---------|------|
| **ast-grep** | **43ms** | **41ms** | **44ms** | **11MB** |
| GritQL | 80ms | 816ms | 1,486ms | 58MB |
| recast | 200ms | 200ms | 199ms | 95MB |
| jscodeshift | 754ms | 808ms | 764ms | 154MB |
| semgrep | 7,535ms | 13,111ms | 1,433ms | 250MB |

**ast-grep 的性能优势**:
1. tree-sitter 增量解析 + CST 遍历
2. Rust 原生实现
3. `kind` 规则 O(1) 过滤
4. 非捕获变量跳过 HashMap 分配

## 4. Nop 平台现有检查机制分析

### 4.1 现有工具链

> **勘误（2026-09-20，仓库实测复核）**：① 下表 check-\*.mjs 实际位于 `ai-dev/tools/`（共 24 个，下表为节选），仓库根 `tools/` 只有 mission-driver；② `nop-compliance-checker.sh` **已不存在**于仓库；③ checkstyle 激活规则实为 **17 条**、pmd 实为 **9 条**（本表 13/7 为旧数据，以设计文档 06 §8 为准）；④ 另有 `ai-dev/tools/rules/` 下 **3 条 ast-grep YAML 规则**（bare-runtimeexception / empty-catch / getmessage-only，经 run-java-lint.sh + sgconfig.yml 运行）未列入下表——它们是 nop-lint pattern DSL 的现成验收用例；⑤ checkstyle/pmd 仅 `-Pqa` profile 手动触发，CI 常规门禁中活跃的 mjs 只有 check-bean-naming.mjs。迁移底账以设计文档 [02-rule-library.md §3](../../design/nop-lint/02-rule-library.md) 的需求追溯表为权威。

| 工具 | 检查方式 | 规则数 | 局限 |
|------|---------|--------|------|
| `checkstyle.xml` | Checkstyle AST | 13→实为 17 | 仅 Java，无语义 |
| `pmd-ruleset.xml` | PMD AST | 7→实为 9 | 仅 Java，类型解析有限 |
| `check-ibiz-interfaces.mjs` | tree-sitter WASM | 2 | 仅 I*Biz 接口 |
| `check-silent-swallow.mjs` | regex | 1 | 非 AST，误报多 |
| `check-silent-wrong-result.mjs` | regex | 5 | 非 AST，误报多 |
| `check-sensitive-literal-leak.mjs` | regex | 1 | 非 AST，误报多 |
| `check-vfs-violations.mjs` | regex | 1 | 非 AST，误报多 |
| `check-import-order.mjs` | regex | 1 | 非 AST |
| `check-error-param-consistency.mjs` | AST + regex | 3 | 混合方式 |
| `nop-compliance-checker.sh` | grep | ~20 | **已不存在（勘误）** |

### 4.2 总计 Nop 平台特有规则

| 类别 | 规则数 | 现有实现 | 需迁移到 AST |
|------|--------|---------|-------------|
| 异常处理 | 7 | 3 regex + 2 checkstyle + 2 无 | ✅ 全部 |
| API 契约 | 3 | 2 tree-sitter + 1 无 | ✅ 全部 |
| 静默错误结果 | 5 | 5 regex | ✅ 误报多 |
| 安全/敏感数据 | 2 | 1 regex + 1 PMD | ✅ 全部 |
| VFS 违规 | 1 | 1 regex | ✅ 误报多 |
| Import 风格 | 4 | 2 regex + 2 checkstyle | ✅ 部分 |
| 命名规范 | 2 | 1 regex + 1 无 | ✅ 全部 |
| 复杂度 | 4 | 4 checkstyle | ✅ 已好 |
| Bug 检测 | 6 | 3 checkstyle + 3 PMD | ✅ 已好 |
| XLang/模板 | 1 | 1 regex | ✅ 需 AST |
| ORM 模型 | 3 | 3 XML 解析 | ❌ 非代码 |
| 查询安全 | 2 | 2 regex | ✅ 误报多 |
| 文档一致性 | 3 | 3 混合 | ❌ 非代码 |
| 反模式 | 8 | 0 | ✅ 全部新开发 |

**核心痛点**：25+ 规则依赖 regex 匹配，误报率高，维护成本大。

## 5. 现有工具的不足（我们的机会）

### 5.1 ESLint 的不足

- **单线程**：无法并行处理多文件
- **仅 JS/TS**：不支持 Java
- **无声明式 pattern**：必须写 JS 代码
- **无 tree-sitter**：ESTree AST 丢失具体语法细节

### 5.2 ast-grep 的不足

- **无跨节点约束**：`all`/`any` 只测试同一节点
- **无数据流分析**：不能追踪变量赋值链
- **无类型感知**：不理解 Java 类型系统
- **重写能力有限**：只能做 pattern-level 替换
- **无内置规则库**：需要社区贡献

### 5.3 Semgrep 的不足

- **极慢**：7.5s vs ast-grep 43ms（175x）
- **高内存**：250MB vs 11MB
- **OCaml 栈**：维护门槛高
- **规则语法复杂**：学习曲线陡

### 5.4 Java 工具的不足

- **碎片化**：Checkstyle + PMD + ErrorProne + SpotBugs 四套不兼容
- **无统一 YAML 格式**：每种工具自己的配置格式
- **无自动修复**：只报告不修
- **无跨语言支持**：仅 Java
- **扩展门槛高**：需要 Java 编码

## 6. 设计目标

基于以上调研，Nop Lint 系统应满足：

| 目标 | 理由 | 优先级 |
|------|------|--------|
| YAML 配置驱动 | 降低门槛，AI 可生成 | P0 |
| 多语言支持 | Java 为主，支持 JS/TS/Python | P0 |
| 声明式 pattern | 类 ast-grep 但更强 | P0 |
| 跨节点约束 | 超越 ast-grep 的 `all`/`any` | P0 |
| 高性能 | 利用 nop-treesitter 增量解析 | P0 |
| 自动修复 | 类 ESLint 的 fixer API | P1 |
| 数据流分析 | 变量追踪、常量传播 | P1 |
| 规则组合 | all/any/not + 递归 | P1 |
| 可扩展 | 插件机制支持自定义规则 | P1 |
| 与 Nop 集成 | IoC 注册、GraphQL API | P2 |
