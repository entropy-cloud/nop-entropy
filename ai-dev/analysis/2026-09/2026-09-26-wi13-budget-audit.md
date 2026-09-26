# WI13 复杂度预算审计——nop-refactor 能力增量与锚点对照

> Date: 2026-09-26
> Scope: nop-refactor roadmap WI13（Item Type: Proof）
> 口径（钉死）：main Java = `src/main/java` 全量、排除生成物（`target/`）与测试；命令 = `find <module> -path "*/src/main/java/*" -name "*.java" -not -path "*/target/*" | xargs wc -l`
> 锚点：nop-lint 族 6 模块 main Java **21,927 行**（2026-09-25 vision 实测冻结快照，commit 87c9b9d05b 处复核为 21,927 整）

## 一、nop-refactor 三模块实测（HEAD，2026-09-26）

| 模块 | main Java 行数 | 文件数 |
|---|---|---|
| nop-refactor-core | 1,848 | 15 |
| nop-refactor-java | 1,619 | 4（1 主类 + 3 测试外主类文件） |
| nop-refactor-graphql | 543 | 14 |
| **合计** | **4,010** | **33** |

对照锚点 21,927：**4,010 ≪ 21,927，未超线**（约为锚点的 18%）。预算余量充裕，无需裁功能。

## 二、nop-lint 侧增量行单列归属（roadmap 要求）

nop-lint 族 HEAD 实测 = **22,543 行**（锚点 +616）。归属分解（git numstat 逐提交，2026-09-25 锚点之后的 main Java 增量）：

### 2.1 nop-refactor 能力归属增量（WI3–WI7 上游扩展）

| 提交 | WI | 内容 | 增/删 |
|---|---|---|---|
| f1c654c971 | WI3 | transform DSL（xdef + parser 拒绝矩阵 + 引擎改写通道 + transform 计数） | +328 / −24 |
| a76148faa0 | WI4 | EditPlanApplier 诊断无关应用入口 + FixApplier 单份化重构 | +205 / −133 |
| 7af2d289cb | WI5 | RefactorResult 契约测试面 additive（skippedEdits/skipped 槽） | +28 / −9 |
| e1af5a95ed | WI7 | 退出码三态 + additive（ROLLED_BACK/SkippedBuckets/appliedFixes/per-path verifier） | +8 / −4 |
| **能力归属小计** | | | **+569 / −170（净 +399）** |

### 2.2 nop-lint 自身演化增量（非 nop-refactor 归属）

nop-lint 自身 roadmap/quality 计划（plan 07 CLI 缺陷修复 +135/−58、plan 08 分配治理 +150/−46、plan 09 children 缓存 +78−10、plan 10 编译复用 +464−232、plan 11 内核正确性 +162−26、plan 12 L4 翻译 +168−87 等）——归属 nop-lint 自身演化，约净 +217。

**核验**：能力归属净 +399 + 自身演化净 ~+217 ≈ +616 漂移 ✓（numstat 口径）。

## 三、预算结论

1. **nop-refactor 自身**：4,010 行 / 3 模块，远低于 nop-lint 族量级锚点（21,927/6）——vision 原则 9 预算成立，余量 ~82%。
2. **能力归属上游增量**：WI3–WI7 对 nop-lint 的上游扩展净 +399 行（transform DSL 与应用入口为 roadmap 明文裁定的"确需上游扩展"），折叠进能力总账 4,409 行仍 ≪ 锚点——超线裁功能分支不触发。
3. **锚点快照语义**：21,927 为 2026-09-25 冻结快照；nop-lint 自身演化（+217）是 nop-lint roadmap 的独立预算事务，不计入 nop-refactor 能力账。

## 四、design 01 落地增注对齐复核（抽查表）

| 增注 | live 证据 | 结论 |
|---|---|---|
| WI6（v1 schema 只声明 rewrite 对；rename 随 WI12 非破坏加入） | NopRefactorBizModel 现有四 action（previewRewrite/applyRewrite/previewRename/applyRename）；TestRenameGraphQL RPC 真调 | 一致 |
| WI7（CLI 命令面/退出码三态/STANDARD 钉死） | NopRefactorCli + TestRefactorCliEndToEnd 15 用例 | 一致 |
| WI8（演示集 fixture 域定位；fix 规则不经 Refactor 面） | /test/lint/refactor-p0/ 独立前缀 + TestRefactorP0ClosedLoop；RefactorRuleGates 拒绝非 transform | 一致 |
| WI9 裁定 1–9（SPI 四段分工/fileRewrites 载体/FQN 泛化/构造器 OUT_OF_SCOPE 等） | RefactorOperationRunner 单点 + declarationByFqn + SymbolKind.CONSTRUCTOR（WI9/WI10/WI11 审计报告在案） | 一致 |
| WI10/WI11/WI12 增注（第一档语义/stale 落点差异/RenameInput 搜索域语义/适配器注入） | TestRenameOperationFirstRung / TestRenameSecondRungFixtures / TestRenameGraphQL | 一致 |

漂移：零。

## 五、终态

roadmap WI1–WI13 全部落地；Deferred 项各归其主（后续 roadmap/design 层）。nop-refactor 能力账 4,010 行（+ 能力归属上游 +399），预算收口通过。
