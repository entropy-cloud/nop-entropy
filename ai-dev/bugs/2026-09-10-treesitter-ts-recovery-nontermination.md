# [P0/open] TypeScript 破坏输入恢复不终止（merge-path 位置回退循环）

- 状态：**fixed（2026-09-10 当日修复）**，由迁移验证（JNI vs 纯 Java 等价性测试）发现。修复：`materializeLookahead`/`getToken` 的错误叶子 padding 写成了相对间隙而 shift 写绝对起点——两种语义混用使错误包裹层的 span 计算出 0/负值，GSS 位置每轮回退。统一为绝对字节偏移后（GLRParser materializeLookahead/getToken + buildErrorComposite 以 push 基准位置度量 size），终止性恢复，且 **5 个此前 adjudicated 的恢复偏差（JSON multi-round ×2、Java ×2、TS ×1）全部变为与 C oracle 字节一致**（见 JsonErrorRecoveryTest.multiRoundRecoveryMatchesTheCOracleByteExact 等）。回归守护：`JniEquivalenceTest.brokenSourcesRecoveryNowMatchesLegacyRuntime`
- 影响面：nop-treesitter 错误恢复（item 11）在 TypeScript blob 上的特定破坏输入；JSON/Java corpus 未复现；合法输入不受影响
- 复现：`JniEquivalenceTest.disabled_brokenSourcesRecoveryStillLoops`（@Disabled，含输入）——输入
  `class Broken {\n    method( {\n        const x = ;\n    }\n}\nif (x { y(); }\n`
- 症状：strategy-2 skip 循环 32,698+ 轮 position 不进（42↔43 震荡），arena 每轮 +3，最终 `visibleChildCount` 递归 SOE（32k 层 error_repeat 嵌套）；C runtime 同输入**秒出**正确恢复树（`_tmp/ts-oracle/tsts` 可复现）：
  `(program (class_declaration name: (type_identifier) body: (class_body (ERROR (property_identifier) pattern: (object_pattern (shorthand_property_identifier_pattern) (ERROR (identifier)))))))`
- 根因诊断（实测 trace，见 git 历史的调试桩版本）：strategy-2 skip 的 merge 路径
  `popCount(version, 1)` 的 stop 节点在 error_repeat **之下**（pos 42），`renumberVersion(first.version, version)` 把版本头重置到 42，push 合并包裹（size 1）回到 43——位置每轮回退，同一 identifier（`x`）被反复消费。C 的对应路径能终止，说明 C 在 condense/prune 或 cost 门上与我们存在行为差（疑似：C 的 `better_version_exists`/condense 用 strategy-1 恢复出的 fork（低错误成本、可正常前进）剪掉了震荡的 skip 版本；我方 fork 与 skip 版本的位置/成本比较未触发剪枝，或 run() 主循环的进度判定放过了不前进的版本）
- 修复方向：以 `./tsts`（C oracle）+ C `parser.c:1476-1568` 的逐轮 trace 为基准做 C↔Java 步进对比，定位剪枝差异；修复后启用 disabled 测试断言与 C 树字节一致
- 关联：the `io.nop.treesitter.compat` package in `nop-treesitter/src/main/java` 迁移验证结论（等价性在双方可终止的输入上已证）

---

## 附录：python splatted-assignment 变体选择差异（open，roadmap item 15 跟踪）

- 输入：`a, *b.c = d\n`
- JNI bonede 0.25.3 + tree-sitter-python 0.23.4（native，verified）：`(module (expression_statement (assignment left: (pattern_list (identifier) (list_splat_pattern (attribute object: (identifier) attribute: (identifier)))) right: (identifier))))`
- 我方输出：`(module (assignment (pattern_list (identifier) (attribute (list_splat (identifier)) (identifier))) (identifier)))`
- 差异：我方在 pattern_list 内把 `*b.c` 解析为 `attribute(list_splat(b), c)`（`.` 绑定在 splat 之上），而 JNI/C 解析为 `list_splat_pattern(attribute(b, c))`（`.` 在 splat 内部）
- **确认非 grammar 差异**：bonede 使用 tree-sitter-python 0.23.4 的 native parse table，正确产生 `list_splat_pattern`。我方 blob 从同一 grammar 的 parser.c 提取——差异在运行时的 GLR 路径
- 注意 `*b = 1` 单独解析正确（`list_splat_pattern(b)`），仅在加 `.` 属性链时出错 → 是 `.` 触发了错误的 GLR 路径选择
- 修复方向：在 `.` lookahead 时，C 的 `ts_parser__select_tree` 与 `ts_parser__reduce` 的 dynamic precedence 裁决可能选择了不同 version。python parser.c 无 dynamic_precedence → 差异在 `compareTrees` 的 symbol-id 排序（list_splat symbol id < list_splat_pattern symbol id → 我们错误地选了 list_splat）。可能修复：在 `shouldReplace` 中，当两个 candidate 的 error_cost 和 dyn_prec 相等时，比较 alias 序列而不是裸 symbol id

---

## 附录 2：blob 提取器 small state SHIFT action 遗漏（open）

- **根因确认**：`Language.tableCell(616, comma_symbol)` → cell 1557 → `actionGroup(1557)` 只有 **2 REDUCE actions**（symbol 179 和 189，child_count=1）。C 的 `ts_small_parse_table` 对同一 (state, symbol) 有 **4 actions：3 REDUCE + 1 SHIFT**（shift 到 state 1633，即 pattern route 的 `list_splat_pattern` 路径）。
- **影响**：GLR 在 `,` 后不 fork 出 pattern route version，导致 `*b.c` 在 pattern_list 上下文中只走 expression route（`list_splat`）而不走 pattern route（`list_splat_pattern`）
- **修复方向**：检查 `ParserCExtractor` 对 `ts_small_parse_table` 中包含 SHIFT+REDUCE 混合 action 的解析逻辑（可能只处理了 REDUCE 条目或只捕获了最后一个 action）
- **验证**：`Language.actionGroup(1557)` 应返回 4 actions（3 REDUCE + 1 SHIFT state=1633），修复后 `a, *b.c = d` 应产生 `list_splat_pattern(attribute(b, c))`

---

## 附录 3：state 232 (`*` in pattern route) SHIFT action 缺失（open）

- **确认**：`a, *b = 1` 正确产生 `list_splat_pattern(b)`（reduce → pattern route ✓），但 `a, *b.c = d` 产生 `attribute(list_splat(b), c)` ❌
- **定位**：`nextState(232, *)` = 0 → blob 的 action group at (232, *) 没有 SHIFT action（或有 REDUCE 但最后不是 SHIFT）。C trace 确认 version:1 在 state:232 shift `*` → state:934
- **根因**：与 state 616 的 `,` SHIFT 遗漏同类——`ParserCExtractor` 的 `extractParseActions` 或 `extractSmallParseTable` 在特定 small state 条目中遗漏 SHIFT action
- **修复**：需修复 `ParserCExtractor` 的 small state 解析逻辑，确保 SHIFT action 在 SHIFT+REDUCE 混合条目中被正确捕获

---

## 附录 4：完整诊断结论（2026-09-10 最终）

**所有 python 恢复/splat 差异的根因链路**：
1. `ParserCExtractor.extractParseActions` 将 C 的 `[3233] = {.count=1, .reusable=true}, SHIFT(2627)` 解析为 blob action group 1557（含 2 REDUCE）——**设计器编号 [3233] 与 blob 顺序编号 1557 之间的映射错误**
2. 导致 `Language.tableCell(616, comma)` 返回 1557（REDUCE group）而非 3233（SHIFT group）
3. GLR 在 state 616 遇到 `,` 时只有 REDUCE 没有 SHIFT → 不 fork 出 pattern route
4. `*b` 单独仍正确（reduce 路径走通），但 `*b.c` 的 `.` 触发不同路径选择时选到错误的 expression route version
5. zero-width NEWLINE/DEDENT 恢复循环是同一根因的另一个表现（错误路径下的 zero-width token 反复消费）

**修复计划**（需专项 session）：
1. 修 `extractParseActions` 的设计器编号映射（C `[N]` 设计器 ↔ blob 顺序索引的对应关系）
2. 重新生成 python blob
3. 验证 `tableCell(616, comma)` 返回 SHIFT group 且 `*b.c` 产生 `list_splat_pattern(attribute(...))`
4. 解除 PyCorpusTest 的 adjudication（预期 ≥95% → 接近 100%）
5. 同步修复 arena 池化（roadmap item 17）
