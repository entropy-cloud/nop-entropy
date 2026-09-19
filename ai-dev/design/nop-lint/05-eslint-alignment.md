# Nop Lint — ESLint 能力对标

> 日期: 2026-09-19（修订 2026-09-20）· 状态: 设计草案（索引见 [00-nop-lint-design.md](./00-nop-lint-design.md)）
> 状态口径：**✅ 已有** 仅指 nop-treesitter 当前代码可演示的能力（如 TSParser 解析、增量解析、TSQuery 的 S-expression kind/field 匹配）。与 ESLint 等价的能力统一标 🔧 Phase N，与 08-migration.md 一致。

## 1. Visitor 模式与选择器

| ESLint 能力 | Nop Lint 实现 | 状态 |
|-------------|--------------|------|
| AST node type visitor | `kind:` 匹配器（SourcePatternCompiler kind 预计算） | 🔧 Phase 1 |
| CSS 选择器 (`CallExpression[callee.name="eval"]`) | `pattern` + `constraints`（regex/field）组合 | 🔧 Phase 2 |
| `:exit` 退出事件 | pattern 模型按节点匹配，无进入/退出区分；如需退出语义用 `inside` 关系规则表达 | 🔧 Phase 2（关系规则） |
| 选择器按节点类型预分桶 | kind 位图预过滤（any 并集/all 交集） | 🔧 Phase 1 |
| 特异性排序 | Rule priority 配置 | 🔧 Phase 2 |

## 2. Scope 分析

| ESLint 能力 | Nop Lint 实现 | 状态 |
|-------------|--------------|------|
| `sourceCode.getScope(node)` | `scopeAnalyzer.getScope(node)` | 🔧 Phase 3 |
| `scope.variables` / `scope.references` | `scopeAnalyzer.getVariables(scope)` | 🔧 Phase 3 |
| `scope.upper` 链式查找 | `scopeAnalyzer.walkUp(node, predicate)` | 🔧 Phase 3 |
| 变量定义解析 | `scopeAnalyzer.resolveDefinition(ref)` | 🔧 Phase 3 |
| 变量重命名 | `scopeAnalyzer.renameVariable(scope, oldName, newName)` | 🔧 Phase 3（backlog，非核心） |

> xscript 的 `scopeAnalyzer` 绑定与 Phase 3 对齐（依赖矩阵见 08 §2）；Phase 1–2 规则不得引用。

## 3. Code Path 分析

| ESLint 能力 | Nop Lint 实现 | 状态 |
|-------------|--------------|------|
| 控制流图构建 | `DataFlowAnalyzer.buildCFG(method)` | 🔧 Phase 3 |
| Fork/merge 上下文 | `ForkContext` 类 | 🔧 Phase 3 |
| Segment 可达性 | `CodePathSegment.reachable` | 🔧 Phase 3 |
| `onCodePathStart/End` 事件 | `constraint.control_flow`（01 §3.3） | 🔧 Phase 3 |
| 不可达代码检测 | `CodePathAnalyzer.detectUnreachable()` | 🔧 Phase 3 |

## 4. 修复系统

| ESLint 能力 | Nop Lint 实现 | 状态 |
|-------------|--------------|------|
| `fixer.replaceText(node, text)` | `Fixer.replace(node, template)` | 🔧 Phase 2 |
| `fixer.insertTextAfter(node, text)` | `Fixer.insertAfter(node, template)` | 🔧 Phase 2 |
| `fixer.remove(node)` | `Fixer.remove(node)` | 🔧 Phase 2 |
| Fix 冲突检测 | `Fixer.detectConflicts(fixes[])`（04 §7） | 🔧 Phase 2 |
| Multi-pass 修复（最多 10 次） | `LintEngine.maxFixPasses = 10` + 收敛检查（03 §3） | 🔧 Phase 2 |
| Suggestions（用户触发） | `Fix.suggest: true` | 🔧 Phase 2 |
| dry-run / 重解析校验 / 原子写入 | 03 §3 autofix 安全机制 | 🔧 Phase 2 |

## 5. 规则组合与抑制

| ESLint 能力 | Nop Lint 实现 | 状态 |
|-------------|--------------|------|
| 多规则共享配置 | `x:extends` delta 继承（02 §2、10 §3）；规则内基础 delta 合并 Phase 1 生效，**ruleset 级共享使用 Phase 2**（lint-ruleset.xdef 交付） | 🔧 Phase 2 |
| 按文件类型覆盖 | `files: { include: [...], exclude: [...] }`（01 §2） | 🔧 Phase 1 |
| 内联注释禁用 | `// nop-lint-disable`（09 §2） | 🔧 Phase 1 |
| `@SuppressWarnings` 集成 | 09 §3 | 🔧 Phase 1 |
| baseline 存量豁免 | 09 §5 | 🔧 Phase 2 |
| 规则选项 JSON Schema | `options` map（10 §2；键值对形式，不做 JS 对象级 schema 校验） | 🔧 Phase 2 |
| `context.options` | `rule.options`（元模型 `options` 字段，10 §2；lint-rule.xdef 随 Phase 1 定义，选项消费自 Phase 2 规则起使用） | 🔧 Phase 2 |
| `context.settings` | `rule.settings`（ruleset 层注入，10 §2；依赖 lint-ruleset.xdef） | 🔧 Phase 2 |

## 6. 框架特定检测（React/JSX 模式，TypeScript）

| ESLint 能力 | Nop Lint 实现 | 状态 |
|-------------|--------------|------|
| 组件注册表（WeakMap） | `ComponentRegistry` 类 | 🔧 Phase 3 |
| 置信度评分（0/1/2） | `Confidence` 枚举 | 🔧 Phase 3 |
| 父组件查找（scope chain） | `ScopeAnalyzer.findEnclosingComponent(node)` | 🔧 Phase 3 |
| Hook 检测 | `Analyzer.isHookCall(node)` | 🔧 Phase 3 |
| exhaustive-deps（依赖数组完整性） | Scope + 数据流：deps 数组解析 vs 闭包引用对比 | 🔧 Phase 3（依赖 Scope 分析） |
| Import 追踪 | `ImportTracker` 类 | 🔧 Phase 2 |
| 延迟报告（Program:exit） | `LintEngine.reportAtExit()` | 🔧 Phase 2 |
| TSX 语法 | tree-sitter tsx grammar（已有 blob） | 🔧 Phase 2 |
| 类型感知规则（tsc） | tsc bridge（program 缓存/降级策略见 06 §5.3） | 🔧 Phase 2 |

## 7. Nop 特有能力

| 能力 | 实现 | 状态 |
|------|------|------|
| BizModel 注解检查 | Pattern + xscript | 🔧 Phase 1 |
| ErrorCode 参数一致性 | xscript + AST 查询 | 🔧 Phase 2 |
| ORM 模型验证 | XNode Pattern（非 tree-sitter，见 01 §3.5） | 🔧 Phase 2 |
| VFS 违规检测 | Import Pattern + xscript | 🔧 Phase 1 |
| 查询安全检查 | xscript + 数据流 | 🔧 Phase 3 |
