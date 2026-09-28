# checkstyle.xml + pmd-ruleset.xml → nop-lint 迁移映射（roadmap item 40）

> 日期: 2026-09-24 · 状态: active 账本（门禁 `ai-dev/tools/check-lint-tool-migration-mapping.mjs` 是本表的对账与防腐机构）
> 口径: 本表是**映射与判据**交付物，不是切换执行记录——切换（移除旧配置段）是下述判据达成后的独立运维动作。

## 1. 现状与并行期运行口径

- 旧工具经根 pom `qa` profile 运行（checkstyle 10.21.1 / pmd 7.26.0，`failOnViolation=false` 报告态，非阻塞门禁）：`./mvnw checkstyle:check -Pqa` / `./mvnw pmd:check -Pqa`。
- nop-lint 全量运行口径：`java -cp <core+java+nop+treesitter> io.nop.lint.core.cli.NopLintCli check <module> --profile standard`（CLI）或 `nop-lint:check`（Maven goal）。
- **并行期即现状**：两套工具互不干扰；本 item 的双跑对照记录见 §4。

## 2. 映射表（26 行，状态词表 = landed / keep-checkstyle / keep-pmd / deferred / out-of-purpose，由门禁强制）

> **out-of-purpose 词表增注（2026-09-28，工具替代 roadmap item 3a，plan 17）**：行判为风格/可选面（out-of-purpose），显式不迁移——这不是替代债，是范围声明（roadmap Purpose）；该类行须附分面理由。

| source | status | target | note |
|---|---|---|---|
| checkstyle:RegexpSingleline | landed | quality/no-system-out | AST 面 `$M` 全方法名比正则 `System\.(out\|err)\.print` 宽；注释/字符串不误报；cli/boot/benchmark 豁免经 ruleset exemptions 表达（design 09） |
| checkstyle:EmptyBlock | out-of-purpose | — | 核心子集已落地（empty-if-block/empty-while-body/empty-finally-block/empty-sync-block/no-empty-catch，plan 16 在档）；残余 for/do/switch 槽位风格面不迁移（roadmap item 3a） | nop-lint 已落地分面规则（empty-if-block / empty-while-body / empty-finally-block / empty-sync-block / no-empty-catch）但不覆盖 for/do/switch 槽位，通用 EmptyBlock 面不完整；分面规则已被对应 manifest 行记账 |
| checkstyle:EmptyCatchBlock | landed | nop/no-empty-catch | manifest `PMD:EmptyCatchBlock` t1；delta：无 checkstyle `exceptionVariableName=expected` 豁免（`catch (expected) {}` 无注释将新报） |
| checkstyle:CovariantEquals | landed | quality/covariant-equals | item 3a（plan 17）：类/枚举遍历 xscript，Object 子类型参数、每方法一报、无 equals(Object) 才报；全仓对照零 diff（§4.2） |
| checkstyle:NoFinalizer | landed | quality/no-finalize | manifest `PMD:Finalize` t1 |
| checkstyle:StringLiteralEquality | landed | quality/string-literal-equality | item 3a（plan 17）：== / != 双面（checkstyle 同双面），literal 任一侧；全仓对照零 diff（§4.2） |
| checkstyle:IllegalToken | landed | quality/no-native-method | item 3a（plan 17）：配置实际面 = LITERAL_NATIVE native 禁令（平台不变式）；全仓对照零 diff（§4.2） |
| checkstyle:UnusedImports | out-of-purpose | — | import 卫生属风格/可选面，不迁移（roadmap item 3a 分面裁定；manifest `PMD:UnnecessaryImport` t3 路由不变） |
| checkstyle:RedundantImport | out-of-purpose | — | 同 UnusedImports：import 卫生风格面，不迁移（roadmap item 3a） |
| checkstyle:MissingDeprecated | out-of-purpose | — | 文档卫生面，不迁移（roadmap item 3a） |
| checkstyle:CyclomaticComplexity | landed | quality/method-cyclomatic-complexity | manifest `PMD:CyclomaticComplexity` t1；**delta：deep 档 only（requires METRICS，standard 档 skipByProfile——enforcement surface 注记：CLI 默认 standard 面等效 keep）、阈值 10 ≠ checkstyle 15、scope 仅 method_declaration 不含构造器** |
| checkstyle:MethodLength | out-of-purpose | — | 尺寸度量面（countEmpty=false），非缺陷拦截，不迁移（roadmap item 3a） |
| checkstyle:ParameterNumber | out-of-purpose | — | 尺寸度量面，非缺陷拦截，不迁移（roadmap item 3a） |
| checkstyle:AnonInnerLength | out-of-purpose | — | 尺寸度量面，非缺陷拦截，不迁移（roadmap item 3a） |
| checkstyle:MagicNumber | out-of-purpose | — | roadmap 定位明举的风格面（噪音即成本），不迁移（roadmap item 3a） |
| checkstyle:IllegalThrows | landed | exception/no-raw-throws | item 3a（plan 17）：illegalClassNames（Error/RuntimeException/Throwable，简单名+限定名）+ @Override 豁免逐点对齐；全仓对照零 diff（§4.2） |
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

**item 3a 后：landed 计 11 行 / out-of-purpose 8 / keep-pmd 7（EmptyCatchBlock 两行同目标）。**

## 3. 切换判据（按 enforcement surface 计）

- 切换后门禁面 = `nop-lint check --profile standard`（CLI/Maven goal 默认档）。
- **deep-only 的 landed 行（CyclomaticComplexity）在默认 standard 面等效 keep**——切换判据不得把它计为已覆盖；除非切换决策同时把门禁面升为 `--profile deep`（需 metrics provider 装配）。
- 判据（item 3a 增注，2026-09-28）：某旧工具配置段的全部行，status 均为 landed（含附注 delta 被接受）或 out-of-purpose（风格/可选面归档，非迁移债）→ 该段可移除。
- 按本表：**checkstyle.xml 与 pmd-ruleset.xml 现均不可移除**（keep 行占多数）。

## 4. 并行期实跑对照（2026-09-24，dogfood）

| 工具 | 目标模块 | 口径 | 结果 |
|---|---|---|---|
| nop-lint check --profile standard | nop-lint-core | 62 条规则库（2026-09-24 实测快照——分面复审后以 rule-catalog 与统一账本为准，2026-09-28 item 2 移除 4 条 + demote 8 条） | 见 §4.1 实测记录 |
| checkstyle:check -Pqa | nop-lint-core | 报告态 | 见 §4.1 |
| pmd:check -Pqa | nop-lint-core | 报告态 | 见 §4.1 |

### 4.1 实测记录（2026-09-24 实跑，目标模块 = nop-lint/nop-lint-core）

| 工具 | 口径 | 结果 |
|---|---|---|
| nop-lint check --profile standard | 62 条规则库 | **121 files scanned, 140 diagnostics（error=7 / warning=133）**；deep 5 规则族 skipByProfile=847（api/no-nonslf4j-logger-call、api/no-proxy-hostile-method、method-cognitive/cyclomatic/npath-complexity、self-assigned-local、unused-local-variable）；7 条 error 全部 = `nop/silent-swallow`（NopLintCli/RuleSetRunner/FixApplier/RuleTestRunner 的 rethrow 型 catch——七信号白名单不含"记日志后抛 NopLintException 包装"形态，属规则豁免/代码修缮的后续裁定，非引擎缺陷） |
| checkstyle:check -Pqa | 16+1 条激活规则 | **48 violations**（报告态） |
| pmd:check -Pqa | 9 条规则 | **7 violations**（AvoidBranchingStatementAsLastInLoop 6 + CloneMethodMustImplementCloneable 1，报告态） |

**对照结论**：三工具各自报告、互不干扰——并行期成立。nop-lint 的覆盖面显著宽于旧工具（140 vs 48+7），且附带深度档规则可选项；发现的 `CommentSuppressionScanner`/`SuppressionSpan` javadoc 字面量触发抑制解析 fail-closed 中止（dogfood 首轮即暴露，已最小改写两处 javadoc 修复；**"prose 提及指令语法即中止"的引擎级语义裁定留 follow-up**——上下文感知的指令识别需设计裁定，非映射范畴）。

### 4.2 item 3a 核心行升规则同语料对照（2026-09-28，plan 17）

- **语料**：checkstyle 侧 = `./mvnw checkstyle:check -Pqa` 全仓实跑（276 份模块 `target/checkstyle-result.xml`，累计 7,830 文件次扫描；主要源码集，`**/_gen/**`、`**/_*.java` 由插件 excludes 排除）；nop-lint 侧 = CLI `--rules` 4 规则白名单对 9,980 个 main 源文件（排除 `_gen/**`/`_*.java`，对齐 checkstyle 语料口径）。
- **语料证据**（排除"双方零查"空心对照）：qa 配置激活由同报告其他规则命中背书——MagicNumber 8,320 / UnusedImports 775 / CyclomaticComplexity 360 / ParameterNumber 163 等；个别 demo 模块（nop-spring-demo 等）跑默认配置不属对照面。
- **命中集对照**（比较键 file+line+rule）：

| 规则 | checkstyle 命中 | nop-lint 命中 | 结论 |
|---|---|---|---|
| checkstyle:StringLiteralEquality ↔ quality/string-literal-equality | 0 | 0 | 零 diff |
| checkstyle:CovariantEquals ↔ quality/covariant-equals | 0 | 0 | 零 diff |
| checkstyle:IllegalToken（native）↔ quality/no-native-method | 0 | 0 | 零 diff |
| checkstyle:IllegalThrows ↔ exception/no-raw-throws | 3（AopProxyHelper:21、ReflectionHelper:128/148，均 throws Throwable） | 3（同文件同行） | **零 diff** |

- **delta 裁定**：无未裁定 delta。附注（非 diff）：对照运行采用 4 规则白名单时，存量 `nop-lint-disable` 注解因目标规则不在白名单产生 1,406 条 unused-disable-directive 副产品警告——标准全库运行下注解正常匹配，不入对照面。
- **fixture 面**：4 规则各带 valid/invalid fixtures（TestNopRuleSuites 59/59 绿），覆盖 @Override 豁免、多类型 throws、枚举体 equals、拼接操作数排除等规格点。
- **record 面裁定**（closure audit Minor-1 消解）：checkstyle CovariantEquals 文档覆盖 class/record/enum；落地规则 v1 面为 class + enum（record 隐式生成 equals(Object)，协变 equals 语义存疑），全仓 276 份报告 CovariantEqualsCheck 0 命中、main 源无 record 含 equals 重载——零 diff 不受影响；record 面记为显式 delta，重估触发 = 出现 record 含 equals 重载的语料。

## 5. 回退预案

- 旧配置完全保留于 git 历史（`checkstyle.xml`/`pmd-ruleset.xml`/根 pom qa profile 在切换前零改动）。
- 切换动作 = 删除旧配置段 + 门禁面声明（单独 commit，单文件可 `git revert`）。
- 回退演练口径：任一时刻 `git revert <切换 commit>` 即恢复双工具并行态；nop-lint 侧无状态（规则库在 classpath VFS，无构建期绑定）。
