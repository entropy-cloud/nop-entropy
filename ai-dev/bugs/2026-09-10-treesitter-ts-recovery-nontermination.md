# [P0/open] TypeScript 破坏输入恢复不终止（merge-path 位置回退循环）

- 状态：**open（未修复）**，2026-09-10 由迁移验证（JNI vs 纯 Java 等价性测试）发现
- 影响面：nop-treesitter 错误恢复（item 11）在 TypeScript blob 上的特定破坏输入；JSON/Java corpus 未复现；合法输入不受影响
- 复现：`JniEquivalenceTest.disabled_brokenSourcesRecoveryStillLoops`（@Disabled，含输入）——输入
  `class Broken {\n    method( {\n        const x = ;\n    }\n}\nif (x { y(); }\n`
- 症状：strategy-2 skip 循环 32,698+ 轮 position 不进（42↔43 震荡），arena 每轮 +3，最终 `visibleChildCount` 递归 SOE（32k 层 error_repeat 嵌套）；C runtime 同输入**秒出**正确恢复树（`_tmp/ts-oracle/tsts` 可复现）：
  `(program (class_declaration name: (type_identifier) body: (class_body (ERROR (property_identifier) pattern: (object_pattern (shorthand_property_identifier_pattern) (ERROR (identifier)))))))`
- 根因诊断（实测 trace，见 git 历史的调试桩版本）：strategy-2 skip 的 merge 路径
  `popCount(version, 1)` 的 stop 节点在 error_repeat **之下**（pos 42），`renumberVersion(first.version, version)` 把版本头重置到 42，push 合并包裹（size 1）回到 43——位置每轮回退，同一 identifier（`x`）被反复消费。C 的对应路径能终止，说明 C 在 condense/prune 或 cost 门上与我们存在行为差（疑似：C 的 `better_version_exists`/condense 用 strategy-1 恢复出的 fork（低错误成本、可正常前进）剪掉了震荡的 skip 版本；我方 fork 与 skip 版本的位置/成本比较未触发剪枝，或 run() 主循环的进度判定放过了不前进的版本）
- 修复方向：以 `./tsts`（C oracle）+ C `parser.c:1476-1568` 的逐轮 trace 为基准做 C↔Java 步进对比，定位剪枝差异；修复后启用 disabled 测试断言与 C 树字节一致
- 关联：`nop-treesitter/compat` 迁移验证结论（等价性在双方可终止的输入上已证）
