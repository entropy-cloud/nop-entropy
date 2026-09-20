# nop-treesitter 代码质量深度审查

> Status: resolved
> Date: 2026-09-20
> Scope: `nop-treesitter` 全模块（main 51 文件 ≈12.6k 行，test ≈9.3k 行）
> Conclusion: 模块整体质量高（语义对齐注释充分、corpus 字节级验证、fail-fast 风格彻底），但存在 1 个确定性 P0 缺陷（ThreadLocal 持有已解析 tree/source 的内存泄漏）、一组死代码、2 处 owner-doc drift 和若干热路径性能损耗。修复工作已拆入 `ai-dev/plans/2271-nop-treesitter-quality-fixes.md`。

## Context

- nop-treesitter 是 tree-sitter C runtime 的纯 Java 重写（roadmap 17/17 done，mission 已关闭；`nop-code-lang-python/typescript` 已从 bonede JNI 迁移到本模块 compat 层），已是生产级定位。
- 本次审查目标：在 mission 关闭后对整个模块做一次独立代码质量评估，确定是否需要一轮质量收口。
- 审查方法：通读全部 main 源码（GLRParser、Lexer、Language、TSTree/TSNode/TSParser、cursor、query、scanner、subtree、incremental、provider/biz/compat、codegen 主干），交叉 grep 死代码候选，对照 `ai-dev/backlog/nop-treesitter-roadmap.md`、`docs/perf-tuning.md`、`ai-dev/bugs/2026-09-10-treesitter-ts-recovery-nontermination.md` 的已知遗留，确认哪些是已知裁定、哪些是新发现。
- 基线：`./mvnw test -pl nop-treesitter -am` 405 个测试全绿（2026-09-20）。

## Analysis

### 现状（做得好的部分）

- **C 语义对齐可追溯**：几乎所有关键方法注释直接标注对应 C 函数（`ts_parser__handle_error`、`ts_stack_pop_count`、`ts_subtree__summarize_children` 等），偏差处显式标注 "Deviation from C"，与 `ai-dev/bugs/` 里的诊断史互相印证。
- **验证体系强**：六语法上游 corpus 字节级对拍（JS 116/116、Java 108/108、JSON 7/7、TS/TSX/Python 余 4 个已裁定节）+ JNI 等价性测试 + 双实现交叉校验（`Language` vs `BlobReader` 独立解码同一格式文档）。
- **fail-fast 风格彻底**：blob 格式 16 个 section 交叉计数校验、scanner 字节码加载期做 jump-target + 栈高度 fixpoint 分析、arena `checkLive`、`Subtree` dense-slot 不变式。无静默默认值路径。
- **已知遗留均已裁定**：多轮恢复树形残差（watch-only，4 corpus 节）、per-subtree refcount 回收（successor design，not scheduled）——这些不是本次发现。

### P0 — 确定性缺陷

**TS-0 ThreadLocal 持有已解析 tree / source，线程池环境下内存泄漏**

- `TSNode.java:109` 的 `SCRATCH`（`ThreadLocal<TSTreeCursor>`）被 `parent()`/`child()`/`childCount()` 等全部结构 accessor 复用，cursor 经 `TreeNavigator` 持有整个 `TSTree`（arena 列 + source 字节）。线程池线程解析大文件后归还线程池，最后一次解析的整棵树（含 source）被 ThreadLocal 钉死，无法回收。
- `ScannerVM.java:111` 的 `POOL`（`ThreadLocal<ScannerVM>`）同理：`reset()` 写入的 `program`、`source`、`validSymbols` 在 scan 结束后一直持有。
- 两者的复用本意是减少分配（perf-tuning 有记载），但都缺少"用完释放引用"的收尾。证据：`TSNode.releaseScratch()` 只递减 depth 不清引用；`ScannerVM.run()` 返回 `Result`（纯 int 值）后不清 VM 字段。
- 影响：Web 容器 / 线程池长寿命线程 × 大源文件 = 每线程泄漏最近一次解析的全部中间数据（对 1MB 源文件即 ~数 MB/线程）。与 `wiki.md` "TSTree 不可变可共享、GC 回收"的承诺冲突。

### P1 — 确定性问题

**TS-1 死代码组**（全部经 grep 确认零调用方）：

| 位置 | 内容 | 备注 |
| --- | --- | --- |
| `TreeSitterBootstrap.java` + 测试 | 自述 "Placeholder entry point. Will be removed once the parser skeleton lands" | parser 早已落地、mission 已关闭；`plannedModules()` 与实际包结构不符（缺 biz/provider/compat/codegen） |
| `GLRParser.parseError`（:2070） | 私有方法零调用 | |
| `GLRParser` `PausedToken.isEof()`（:2015） | 零调用（实际判断用 `lookaheadSymbol == Lexer.END_SYMBOL`） | |
| `GLRParser` `Iter.pending`（:1986） | 只写不读 | 记录了两次 `stackIter` javadoc 重复（:1855-1868）同属此文件清理 |
| `Lexer.next()`（:163） | public 但模块内外零调用 | 与 `nextForParse` 职责重叠，javadoc 声称对应 C `ts_parser__lex`（该角色已由 `nextForParse` 承担） |
| `TreeNavigator.childRef/scan/ChildRef/stepVisible/namedRelevant/foundVisibleGrandchildCount` | 分配式导航旧路径整链零调用 | 已被 primitive-scan（`locateChild`/`found*`）取代，属残留双实现 |

**TS-2 owner-doc drift**（AGENTS.md 规定 docs bug 应同任务修复）：

- `nop-treesitter/README.md` 内置语法列表漏 `python`（`DefaultTreeSitterLanguageProvider.BUILTIN_BLOBS` 实际 6 条含 python，且 provider 对 python 自动接线 `PythonScanner` 工厂，不经 compat 层也可用）。
- `docs-for-ai/03-modules/nop-treesitter.md` 性能节仍是 09-10 的超标值（json-1m 3.50x、java 4.28x），而 09-13 perf-closure 后 `nop-treesitter/docs/perf-tuning.md` 与同文档 corpus 结论均已收敛到 ≤3x（1.20x/1.26x/1.56x/2.76x）——同文档内自相矛盾。
- `DefaultTreeSitterLanguageProvider` 类 javadoc "the five shipped grammars" vs `BUILTIN_BLOBS` 6 条。

**TS-3 外部扫描热路径绕过 memoized validSymbols**：`Lexer.externalScan`（:213）用 `ScannerVM.validSymbols(language, parseState)`，每次外部扫描分配新 `boolean[]` 并重算；`Language.validSymbols(parseState)`（:727）本就是 memoized 版本（`ScannerVM.scan` 自己都用它）。perf-tuning.md 已有过 "scanner 校验每 token 重跑" 的教训（433→86 MB/op），这是同一模式的残留调用点。

### P2 — 性能 / 结构观察（含修复建议）

- **TS-4 `TSTree.writeNode`/`collectVisible` 渲染 O(children²)**：`collectVisible` 对每个可见子调 `effectiveSymbol(parent, childId)`，内部 `structuralIndexOf` 再线性扫一遍兄弟（`TSTree.java:404`）。`writeFlat` 路径（`childMeta`）已按索引传参，唯独 canonical sexp 路径是平方。修法简单：`collectVisible` 已在遍历中维护 `structuralIndex`，直接传下去。
- **TS-5 `TreeNavigator` javadoc "O(1)-per-step" 与实现不符**：`locateChild` 从 0 线性扫到 k，`visibleChildCount` 无缓存递归，`findNext` 的 relevance probe 对每个兄弟做完整子树计数。结构级优化（计数缓存）属优化候选（需专门 perf 验证），本次只修 javadoc 使其诚实，并把优化项记入 Non-Blocking Follow-ups。
- **TS-6 `Boolean.getBoolean("ts.debug")` 在 GLR 热路径逐次读系统属性**（reduce/shouldReplace/selectTree/skip 四处），`Boolean.getBoolean` 是同步 Properties 查找。改为类加载时读一次的 `static final`。测试中无动态设置 `ts.debug` 的用法，安全。
- **TS-7 UTF-8 解码三处重复**：`Lexer.decodeCodepoint`、`Lexer.decodePacked`（packed 热路径变体）、`ScannerVM.decodeCodepoint` 逐字节相同。提取 `io.nop.treesitter.util.Utf8` 共享（pom 模块描述本就声明 util 包含 "UTF-8 utilities"）；`decodePacked` 作为热路径变体保留在 Lexer。
- **TS-8 良性竞态（记录不改）**：`Language.validatedScannerProgram()` 的 lazy-validate 与 `noteNodeCount()` 的 max 合并存在并发重复计算/丢更新，但都是单调无害语义，注释已说明。
- **TS-9 已知设计限制（记录不改）**：`TSTree` 渲染/`copy`、GLR `buildParent` 等沿树递归，深度嵌套对抗输入有 SOE 风险（C runtime 同样递归，且 `docs/wiki.md` 已定义仅内部故障抛 `TreeSitterException`）；`TSQueryCursor` 每节点 × 每 pattern 回溯匹配，与 C step-machine 的复杂度差异是查询引擎的结构性优化候选。

## Conclusion

- 模块可以确认维持生产级定位：正确性由 corpus + JNI 等价 + 交叉解码背书，已知偏差全部有裁定记录。
- 否决的处理方式：**不做** TreeNavigator 计数缓存 / query step-machine 重写 / per-subtree refcount（分别属优化候选与既有 successor design，需独立 perf 与设计验证，混入本轮会放大回归面）；**不做** compat 包清理（JNI 迁移层是活跃消费方依赖的契约面）。
- 修复工作拆入 `ai-dev/plans/2271-nop-treesitter-quality-fixes.md`：P0 泄漏修复（含回归测试）→ 死代码清理 → 热路径小修 + UTF-8 去重 → owner-doc 同步。

## References

- 审查对象：`nop-treesitter/src/main/java/io/nop/treesitter/`（全量）
- 已知遗留：`ai-dev/backlog/nop-treesitter-roadmap.md`、`nop-treesitter/docs/perf-tuning.md`、`ai-dev/bugs/2026-09-10-treesitter-ts-recovery-nontermination.md`
- owner docs：`docs-for-ai/03-modules/nop-treesitter.md`、`nop-treesitter/README.md`、`nop-treesitter/docs/wiki.md`
- 修复计划：`ai-dev/plans/2271-nop-treesitter-quality-fixes.md`
