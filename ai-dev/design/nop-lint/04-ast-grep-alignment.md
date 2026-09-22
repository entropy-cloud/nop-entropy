# Nop Lint — ast-grep 能力对标

> 日期: 2026-09-19（修订 2026-09-20）· 状态: 设计草案（索引见 [00-nop-lint-design.md](./00-nop-lint-design.md)）

Nop Lint 必须完整复现 ast-grep 的所有核心能力，并在其上扩展。以下逐项对标。

> **实现列说明（2026-09-21）**：各表"Nop Lint 实现"列的类名（MetaVarMatcher/MultiVarMatcher/ChildMatcher/EllipsisMatcher/TrivialSkipper 等）是**语义映射**而非交付类名——实际交付归 `MetaVarEnv`/`PatternMatcher`/`Strictness`（nop-lint-core pattern 包）；算法语义以本表行为描述为准。

## 1. Pattern 编译管线

| ast-grep 能力 | 算法描述 | Nop Lint 实现 | 状态 |
|---------------|---------|--------------|------|
| Pattern 文本 → CST | `lang.pre_process_pattern($ → expando_char)` → tree-sitter parse | `SourcePatternCompiler.compile(patternText, language)`（用 TSParser 解析 pattern 片段） | 🔧 Phase 1 |
| CST → PatternNode 树 | 递归转换：leaf → Terminal/MetaVar，internal → Internal{kind_id, children} | `SourcePatternCompiler.convertToPatternNode(lintNode)` | 🔧 Phase 1 |
| Effective node 提取 | 自根沿唯一子节点链下降，遇多子 Internal 或叶即停（2026-09-22 修订：原"默认取最内层 >1 子节点的节点"措辞不可执行，与 ast-grep `is_single_node` 下降等效；见 plan 03 设计偏差声明 1） | `SourcePatternCompiler.extractEffectiveNode(root)` | 🔧 Phase 1 |
| Contextual pattern | `selector: "node_kind"` + `context: "周围代码"` | `SourcePatternCompiler.contextual(selector, context, lang)` | 🔧 Phase 1 |
| Expando char 预处理 | `$` → `_`（Python）等语言特定替换 | `PatternPreprocessor`（SourcePatternCompiler 第 1 步，见 01 §4） | 🔧 Phase 1 |

## 2. Meta-变量匹配算法

| ast-grep 能力 | 算法描述 | Nop Lint 实现 | 状态 |
|---------------|---------|--------------|------|
| `$VAR` 命名捕获 | 匹配单个命名节点，`env.insert(name, node)` | `MetaVarMatcher.matchNamed(node, env)` | 🔧 Phase 1 |
| `$$VAR` 匿名捕获 | 匹配命名或匿名节点 | `MetaVarMatcher.matchAnonymous(node, env)` | 🔧 Phase 1 |
| `$$$VAR` 多捕获 | 匹配零或多个节点序列，`env.insertMulti(name, nodes[])` | `MultiVarMatcher.match(node, env)` | 🔧 Phase 1 |
| `$_VAR` 丢弃捕获 | 匹配但不写入 env，无 HashMap 分配 | `DropVarMatcher.match(node)` | 🔧 Phase 1 |
| 重复变量一致性 | 同名变量第二次出现时，`does_node_match_exactly(existing, candidate)` | `MetaVarEnv.checkConsistency(name, node)` | 🔧 Phase 1 |
| `skip_cand_for_metavar` | metavar 前仅跳注释（`should_skip_comment && is_extra`）；SINGLE 对未命名候选 = NoMatch 整体失败（上游 strictness.rs L103-105——2026-09-21 勘误，原"跳过未命名候选"系误标） | `PatternMatcher.step`（SMART 分支） | 🔧 Phase 1 |

**关键算法：MetaVarEnv 一致性检查**
```
match_variable(id, candidate):
  if existing = single_matched.get(id):
    return does_node_match_exactly(existing, candidate)
    // 递归比较：相同 node_id → true
    // 叶节点 → 比较文本
    // 内部节点 → 相同 kind_id + 递归子节点
  return true  // 首次出现总是成功
```

## 3. 子节点匹配算法（核心）

| ast-grep 能力 | 算法描述 | Nop Lint 实现 | 状态 |
|---------------|---------|--------------|------|
| Lockstep 遍历 | goal 和 candidate 子节点同步前进 | `ChildMatcher.matchChildren(goal, cand, agg, strictness)` | 🔧 Phase 1 |
| 省略号匹配 | lookahead probe：克隆 aggregator 尝试匹配下一个 goal，失败则扩展省略号范围 | `EllipsisMatcher.match(goal, cand, agg)` | 🔧 Phase 1 |
| 跳过 trivial 节点 | `match_single_node_while_skip_trivial`：循环跳过不匹配的匿名节点 | `TrivialSkipper.shouldSkip(node, strictness)` | 🔧 Phase 1 |
| 尾部节点处理 | pattern 结束后，剩余 candidate 节点必须可跳过 | `Strictness.shouldSkipTrailing(node)` | 🔧 Phase 1 |

**关键算法：省略号 lookahead probe**
```
may_match_ellipsis_impl(goal_children, cand_children, agg, strictness):
  goal_children.next()  // 消费 $$$
  if goal_children.peek().is_none():
    return match_ellipsis(all remaining cand)
  // PROBE: 克隆 aggregator 防止 env 泄漏
  loop:
    probe = agg.clone()
    if match_node_impl(goal_next, cand_next, probe) == Matched:
      return match_ellipsis(agg, matched_cand_nodes)
    matched.push(cand_children.next())
```

## 4. 严格度系统（6 级）

| 级别 | Terminal 匹配 | 未命名节点 | 注释 | 文本检查 | 用途 |
|------|-------------|-----------|------|---------|------|
| **CST** | 全部保留 | 保留 | 保留 | ✅ | 精确语法匹配 |
| **Smart**（默认） | 全部 | **跳过** | 跳过 | ✅ | 日常使用 |
| **AST** | 命名节点 | **双方跳过** | 不跳过（注释是 named 节点，须被显式匹配；上游 strictness.rs `should_skip_comment` 对 Cst/Ast 均为 false——2026-09-21 勘误，原"跳过"系误标） | ✅ | 结构匹配 |
| **Relaxed** | AST 节点 | 跳过 | 跳过 | ✅ | 宽松匹配 |
| **Signature** | 命名节点 | 跳过 | 跳过 | **仅 kind** | 结构签名 |
| **Template** | 全部 | 跳过 | 跳过 | **仅文本** | 文本模板 |

**关键算法：strictness 决策矩阵**
```
match_terminal(goal, candidate, strictness):
  if goal.kind matches candidate.kind:
    if is_named OR text matches:
      return MatchedBoth
  if should_skip_comment(candidate):
    return SkipCandidate
  switch strictness:
    CST:       (skip_goal=false, skip_candidate=false) → NoMatch or SkipX
    Smart:     (skip_goal=false, skip_candidate=!is_named) → skip unnamed cand
    Ast:       (skip_goal=!is_named, skip_candidate=!is_named) → skip unnamed both
    Relaxed:   same as Ast (comments handled separately)
    Signature: if kind matches → MatchedBoth (ignores text)
    Template:  if text matches → MatchedBoth (ignores kind)
```

**Nop Lint 扩展**：在 ast-grep 6 级基础上增加 `TypeAware` 级别，结合类型信息做更精确的匹配。

## 5. 关系规则算法

> **落地注记（2026-09-22，item 23 Phase 1）**：四算子 + StopBy 三档 + field 约束已落地于 nop-lint-core `pattern/` 包，交付类名为 `RelationalMatcher`（内含 `Op` 枚举 = 四算子统一实现）、`StopBy`（Mode + `find(neighbor, multi, finder, env)`）、`NodeMatcher`/`PatternNodeMatcher`/`KindNodeMatcher`（节点级组合单元）。语义裁决（对照上游与下表）：(a) `neighbor` 档的"一次邻接"按算子分别定义——inside=直接父、has=**全部直接子节点**（`children().find_map`）、follows/precedes=紧邻兄弟一个；(b) `has` 的 end/rule 档走**前序 DFS**（惰性迭代器，首中即停）；(c) rule 档 `inclusive_until`：stop 节点本身先被 finder 尝试、再终止遍历；(d) `field` 语义——`has`+field 从 field 子节点为根搜索（neighbor 检其直接子节点），`inside`+field 要求候选占据祖先的该 field 槽位（`childByField` 等值），follows/precedes 拒绝 field（fail-closed）；(e) 兄弟遍历按**可见子节点原始序**，不跳注释——注释夹在两语句之间会阻断 neighbor 档（与上游 prev/next_sibling 语义一致），end 档不受影响。配套内核修正：`PatternMatcher.step` 的 candidate-keyed skip 此前仅覆盖 Terminal 目标，现按 §4 决策矩阵扩展到 Internal 目标（同 kind 结构失败仍硬失败——候选为 named 非 extra 时不可跳过）。

| ast-grep 能力 | 算法描述 | Nop Lint 实现 | 状态 |
|---------------|---------|--------------|------|
| **inside** (ancestor) | `stop_by.find(node.parent(), node.ancestors(), finder)` | `RelationalMatcher(Op.INSIDE)` | ✅ item 23（2026-09-22） |
| **has** (descendant) | `node.children().find_map(inner.match)` 或 DFS | `RelationalMatcher(Op.HAS)` | ✅ item 23（2026-09-22） |
| **follows** (after) | `stop_by.find(node.prev(), node.prev_all(), finder)` | `RelationalMatcher(Op.FOLLOWS)` | ✅ item 23（2026-09-22） |
| **precedes** (before) | `stop_by.find(node.next(), node.next_all(), finder)` | `RelationalMatcher(Op.PRECEDES)` | ✅ item 23（2026-09-22） |
| **stopBy: neighbor** | 仅检查直接兄弟 | `StopBy.neighbor()` | ✅ item 23（2026-09-22） |
| **stopBy: end** | 检查所有祖先/后代 | `StopBy.end()` | ✅ item 23（2026-09-22） |
| **stopBy: rule** | 检查到某个规则匹配为止（含） | `StopBy.rule(matcher)` | ✅ item 23（内核级；DSL `stopByRule` 引用 util 规则归 item 24） |
| **field 约束** | `inside`/`has` 限定子节点字段名 | `RelationalMatcher` field 参数 | ✅ item 23（2026-09-22） |

**关键算法：StopBy**
```
StopBy.find(once, multi, finder):
  switch self:
    Neighbor: finder(once())  // 仅一个
    End:      multi().find_map(finder)  // 所有
    Rule(stop):
      multi().take_while(inclusive_until(stop)).find_map(finder)
      // inclusive_until: stop 节点本身也参与匹配
```

## 6. 组合规则算法

> **落地注记（2026-09-22，item 23 Phase 2）**：`all`/`not` 已落地于 nop-lint-core `pattern/` 包（`AllMatcher`/`NotMatcher`，与 `RelationalMatcher` 同为 `NodeMatcher` 家族）；xdef/`RuleDslModel`/`RuleDslParser` 同步扩展（10 §2）。**算子分界**：`matches` 递归 + `utils` + any 嵌套 refinement 归 item 24；DSL 嵌套面有界（容器 → all 元素 → 其 not 内层），越界 fail-closed（含 `any`/`all` 入 all/not、`not` 嵌 `not`——解析器与编译器双重拒绝，item 24 扩展时解除）。

| ast-grep 能力 | 算法描述 | Nop Lint 实现 | 状态 |
|---------------|---------|--------------|------|
| **all** (AND) | kind 交集预过滤 + scratchpad env + 全部匹配才提交 | `AllMatcher.match(node, env)` | ✅ item 23（2026-09-22） |
| **any** (OR，单层) | kind 并集 + 重置 env 尝试每个 + 第一个成功 | `AnyMatcher.match(node, env)` | ✅ **Phase 1**（顶层 any 分支，CompiledRule 逐支执行） |
| **any** 嵌套（作为子规则） | 同上，嵌套于 all/not 内 | `AnyMatcher` 复用 | 🔧 item 24 |
| **not** (NOT) | probe env 隔离 + `inner.match().xor(Some(node))` | `NotMatcher.match(node, env)` | ✅ item 23（2026-09-22） |
| **matches** (递归) | 引用 util 规则，支持自引用 | `ReferentMatcher.match(node, env)` | 🔧 item 24 |

**关键算法：Not 的 env 隔离**
```
Not.match_node_with_env(node, env):
  probe = Cow::Borrowed(env)  // 克隆引用，不修改原始 env
  self.inner.match_node_with_env(node, &mut probe)
  .xor(Some(node))  // inner 匹配 → None；inner 不匹配 → Some(node)
  // 关键：inner 的绑定永远不会泄漏到真实 env
```

## 7. Fix/Rewrite 系统

| ast-grep 能力 | 算法描述 | Nop Lint 实现 | 状态 |
|---------------|---------|--------------|------|
| Template 编译 | `"let $A = $B"` → fragments + vars + indent 信息 | `TemplateFix.compile(template)` | 🔧 Phase 2 |
| 变量替换 | 捕获节点文本 + deindent/reindent | `TemplateFix.apply(env, source)` | 🔧 Phase 2 |
| 扩展修复范围 | `expand_start`/`expand_end` 用 StopBy 向前/后扩展 | `Fixer.expand(range, stopBy)` | 🔧 Phase 2 |
| 多修复合并 | 按 range[0] 排序 + 贪心合并 + 冲突检测 | `Fixer.merge(fixes[])` | 🔧 Phase 2 |

## 8. 需要从 ast-grep 补充的能力

以下能力在当前设计中**尚未明确覆盖**，需要补充：

| 能力 | 重要性 | 说明 | 状态 |
|------|--------|------|------|
| **Pattern 必须是有效代码** | P0 | 不同于正则，pattern 必须能被 tree-sitter 解析为保证结构正确性的前提（01 §4 编译步骤 1–2） | 已纳入 SourcePatternCompiler 设计（Phase 1） |
| **Kind 交集/并集预计算** | P0 | `all` 规则取 kind 交集，`any` 规则取 kind 并集，编译时过滤（01 §4 编译步骤 5） | 已纳入设计（Phase 1/2） |
| **Aggregator 模式** | P0 | 匹配结果收集策略（真实 env vs probe 克隆，见 §3 省略号算法） | 已纳入设计（Phase 1） |
| **Limitations 感知** | P1 | 哪些模式 tree-sitter 无法表达（如跨函数追踪），文档化到规则作者指南 | Phase 2 文档任务 |
