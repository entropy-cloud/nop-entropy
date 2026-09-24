# checkstyle.xml + pmd-ruleset.xml → nop-lint 迁移映射（roadmap item 40）

> 日期: 2026-09-24 · 状态: active 账本（门禁 `ai-dev/tools/check-lint-tool-migration-mapping.mjs` 是本表的对账与防腐机构）
> 口径: 本表是**映射与判据**交付物，不是切换执行记录——切换（移除旧配置段）是下述判据达成后的独立运维动作。

## 1. 现状与并行期运行口径

- 旧工具经根 pom `qa` profile 运行（checkstyle 10.21.1 / pmd 7.26.0，`failOnViolation=false` 报告态，非阻塞门禁）：`./mvnw checkstyle:check -Pqa` / `./mvnw pmd:check -Pqa`。
- nop-lint 全量运行口径：`java -cp <core+java+nop+treesitter> io.nop.lint.core.cli.NopLintCli check <module> --profile standard`（CLI）或 `nop-lint:check`（Maven goal）。
- **并行期即现状**：两套工具互不干扰；本 item 的双跑对照记录见 §4。

## 2. 映射表（26 行，状态词表 = landed / keep-checkstyle / keep-pmd / deferred，由门禁强制）

| source | status | target | note |
|---|---|---|---|
| checkstyle:RegexpSingleline | landed | quality/no-system-out | AST 面 `$M` 全方法名比正则 `System\.(out\|err)\.print` 宽；注释/字符串不误报；cli/boot/benchmark 豁免经 ruleset exemptions 表达（design 09） |
| checkstyle:EmptyBlock | keep-checkstyle | — | nop-lint 已落地分面规则（empty-if-block / empty-while-body / empty-finally-block / empty-sync-block / no-empty-catch）但不覆盖 for/do/switch 槽位，通用 EmptyBlock 面不完整；分面规则已被对应 manifest 行记账 |
| checkstyle:EmptyCatchBlock | landed | nop/no-empty-catch | manifest `PMD:EmptyCatchBlock` t1；delta：无 checkstyle `exceptionVariableName=expected` 豁免（`catch (expected) {}` 无注释将新报） |
| checkstyle:CovariantEquals | keep-checkstyle | — | 需 equals 参数类型分析（L2+）；manifest 无对应行 |
| checkstyle:NoFinalizer | landed | quality/no-finalize | manifest `PMD:Finalize` t1 |
| checkstyle:StringLiteralEquality | keep-checkstyle | — | 需 String 类型面；manifest `LiteralsFirstInComparisons` 仅 t2 |
| checkstyle:IllegalToken | keep-checkstyle | — | token 黑名单面 v1 无对应 |
| checkstyle:UnusedImports | keep-checkstyle | — | 跨编译单元引用计数（manifest `PMD:UnnecessaryImport` t3 路由不变） |
| checkstyle:RedundantImport | keep-checkstyle | — | 同 UnusedImports 面 |
| checkstyle:MissingDeprecated | keep-checkstyle | — | @Deprecated+@deprecated 注解组合面 |
| checkstyle:CyclomaticComplexity | landed | quality/method-cyclomatic-complexity | manifest `PMD:CyclomaticComplexity` t1；**delta：deep 档 only（requires METRICS，standard 档 skipByProfile——enforcement surface 注记：CLI 默认 standard 面等效 keep）、阈值 10 ≠ checkstyle 15、scope 仅 method_declaration 不含构造器** |
| checkstyle:MethodLength | keep-checkstyle | — | 长度面 v1 无对应 |
| checkstyle:ParameterNumber | keep-checkstyle | — | manifest `PMD:ExcessiveParameterList` t2 无规则文件 |
| checkstyle:AnonInnerLength | keep-checkstyle | — | 长度面 |
| checkstyle:MagicNumber | keep-checkstyle | — | item 35 候选池已撤销（魔法数噪音面未裁定） |
| checkstyle:IllegalThrows | keep-checkstyle | — | throws 面相邻规则（SignatureDeclareThrowsException）在 manifest t2 无规则文件 |
| checkstyle:AvoidStarImport | landed | quality/no-star-import | **delta：static-star 新告警面**——checkstyle 配置 `allowStaticMemberImports=true`（static star import 是项目惯例），nop-lint 双分支均报；切换需先裁 static-star 豁免 |
| pmd:EmptyCatchBlock | landed | nop/no-empty-catch | 同 checkstyle:EmptyCatchBlock 行 |
| pmd:JumbledIncrementer | keep-pmd | — | manifest 外规则；增量面 v1 无对应 |
| pmd:AvoidBranchingStatementAsLastInLoop | keep-pmd | — | manifest 外；控制流面 |
| pmd:ImplicitSwitchFallThrough | keep-pmd | — | manifest 外；fall-through 在候选池（关系+xscript 成本裁定） |
| pmd:CloneMethodMustImplementCloneable | keep-pmd | — | manifest 外；类型面 |
| pmd:CloneMethodReturnTypeMustMatchClassName | keep-pmd | — | manifest 外；类型面 |
| pmd:ProperCloneImplementation | keep-pmd | — | manifest 外；类型面 |
| pmd:HardCodedCryptoKey | landed | security/no-hardcoded-crypto | manifest t1 |
| pmd:InsecureCryptoIv | keep-pmd | — | manifest `PMD:InsecureCryptoIv` t2（机制面无规则文件——tier 语义带入：不能标 landed） |

**landed 计 7 行 / keep-checkstyle 12 / keep-pmd 7（EmptyCatchBlock 两行同目标）。**

## 3. 切换判据（按 enforcement surface 计）

- 切换后门禁面 = `nop-lint check --profile standard`（CLI/Maven goal 默认档）。
- **deep-only 的 landed 行（CyclomaticComplexity）在默认 standard 面等效 keep**——切换判据不得把它计为已覆盖；除非切换决策同时把门禁面升为 `--profile deep`（需 metrics provider 装配）。
- 判据：某旧工具配置段的全部行，在 enforcement surface 上均为 landed（含附注 delta 被接受）→ 该段可移除。
- 按本表：**checkstyle.xml 与 pmd-ruleset.xml 现均不可移除**（keep 行占多数）。

## 4. 并行期实跑对照（2026-09-24，dogfood）

| 工具 | 目标模块 | 口径 | 结果 |
|---|---|---|---|
| nop-lint check --profile standard | nop-lint-core | 62 条规则库，standard 档 | 见 §4.1 实测记录 |
| checkstyle:check -Pqa | nop-lint-core | 报告态 | 见 §4.1 |
| pmd:check -Pqa | nop-lint-core | 报告态 | 见 §4.1 |

### 4.1 实测记录（2026-09-24 实跑，目标模块 = nop-lint/nop-lint-core）

| 工具 | 口径 | 结果 |
|---|---|---|
| nop-lint check --profile standard | 62 条规则库 | **121 files scanned, 140 diagnostics（error=7 / warning=133）**；deep 5 规则族 skipByProfile=847（api/no-nonslf4j-logger-call、api/no-proxy-hostile-method、method-cognitive/cyclomatic/npath-complexity、self-assigned-local、unused-local-variable）；7 条 error 全部 = `nop/silent-swallow`（NopLintCli/RuleSetRunner/FixApplier/RuleTestRunner 的 rethrow 型 catch——七信号白名单不含"记日志后抛 NopLintException 包装"形态，属规则豁免/代码修缮的后续裁定，非引擎缺陷） |
| checkstyle:check -Pqa | 16+1 条激活规则 | **48 violations**（报告态） |
| pmd:check -Pqa | 9 条规则 | **7 violations**（AvoidBranchingStatementAsLastInLoop 6 + CloneMethodMustImplementCloneable 1，报告态） |

**对照结论**：三工具各自报告、互不干扰——并行期成立。nop-lint 的覆盖面显著宽于旧工具（140 vs 48+7），且附带深度档规则可选项；发现的 `CommentSuppressionScanner`/`SuppressionSpan` javadoc 字面量触发抑制解析 fail-closed 中止（dogfood 首轮即暴露，已最小改写两处 javadoc 修复；**"prose 提及指令语法即中止"的引擎级语义裁定留 follow-up**——上下文感知的指令识别需设计裁定，非映射范畴）。

## 5. 回退预案

- 旧配置完全保留于 git 历史（`checkstyle.xml`/`pmd-ruleset.xml`/根 pom qa profile 在切换前零改动）。
- 切换动作 = 删除旧配置段 + 门禁面声明（单独 commit，单文件可 `git revert`）。
- 回退演练口径：任一时刻 `git revert <切换 commit>` 即恢复双工具并行态；nop-lint 侧无状态（规则库在 classpath VFS，无构建期绑定）。
