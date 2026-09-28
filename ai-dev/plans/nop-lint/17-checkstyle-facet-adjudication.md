# 17 checkstyle 12 行三轴归类与核心行升规则（roadmap item 3a）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap item 3](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [plan 15](15-tool-replacement-ledger.md) · [plan 16](16-rule-facet-review.md) · [checkstyle-pmd-migration 账本](../../../nop-lint/docs/checkstyle-pmd-migration.md) · [统一账本](../../../nop-lint/docs/tool-replacement-ledger.md)

## Purpose

按新定位对 checkstyle.xml 迁移映射的 12 条 keep-checkstyle 行逐行归入三轴：核心缺陷行升 nop-lint 规则（含同语料对照），风格行记 out-of-purpose（不迁移，随工具终裁归档）。本计划是 item 3 的前半——qa profile 配置段切换归 successor plan 18（切换纪律：独立 plan + 单 commit 回退）。

## Current Baseline

- checkstyle.xml（qa profile 接线，`failOnViolation=false` 报告态）激活 16+1 条规则；映射账本 26 行中 checkstyle 17 行 = landed 5 行（RegexpSingleline→no-system-out、EmptyCatchBlock→no-empty-catch、NoFinalizer→no-finalize、CyclomaticComplexity→method-cyclomatic-complexity、AvoidStarImport→no-star-import）+ keep-checkstyle 12 行。**口径注**：checkstyle 面内无双行同目标；"empty-catch 双行同目标"是 26 行全表口径（checkstyle+pmd 各一行同指 no-empty-catch）。
- 12 行 keep-checkstyle 实测配置（checkstyle.xml 行号在档）：CovariantEquals(52)、StringLiteralEquality(58)、IllegalToken(tokens=LITERAL_NATIVE,61)、UnusedImports(66)、RedundantImport(69)、MissingDeprecated(72)、MethodLength(max=150,countEmpty=false,88)、ParameterNumber(max=7,97)、AnonInnerLength(max=40,105)、MagicNumber(ignoreNumbers=离散列表 -1,0,1,2,3,10,100,113)、EmptyBlock(option=text,42)、IllegalThrows(illegalClassNames=java.lang.Error/RuntimeException/Throwable，ignoreOverriddenMethods 默认 true,122)。
- checkstyle 行为事实（R1 审查核实）：StringLiteralEquality 同时拦 `==` 与 `!=`；IllegalThrows 简单名与限定名双形态均命中、`@Override` 方法默认豁免；CovariantEquals 只对 Object 子类型参数的协变 equals 报（primitive 参数不报）、**每个协变方法一行**、覆盖 class/record/enum。
- 分面裁定（roadmap Purpose 分面表 + plan 16 判据，执行时逐行落账本）：
  - **core → 升规则（4）**：StringLiteralEquality、IllegalToken→no-native-method（native 禁令 = 平台不变式）、IllegalThrows→no-raw-throws（Nop 异常纪律声明面）、CovariantEquals（等价契约破坏）。
  - **out-of-purpose → 不迁移（8）**：EmptyBlock（核心子集已 landed；残余 for/do/switch 槽位风格面）、UnusedImports、RedundantImport、MissingDeprecated、MethodLength、AnonInnerLength、ParameterNumber、MagicNumber。
- 引擎/账本机制（plan 15/16 建立的经验证约束）：
  - 规则落地全链 = 主 rule.yml + suite 目录（x:extends）+ `TestNopRuleSuites`：`EXPECTED_RULE_IDS`（现 48）**+ `categoryOf` 的 exception 分支是显式 id 白名单（:162），exception/ 新规则须加 case** + `TestProductionRuleCount`（现 58 + size 断言）+ `gen-lint-rule-catalog`（self-test 现硬编码 58）+ 统一账本规则级分面表（checker census 常量 `RULE_FACET_CENSUS` = live + remove 行 = 现 62，新规则加行后升 66）。
  - manifest 不可加行（design 06 §1–§2 enum-set 钉死）只能升 tier：LiteralsFirstInComparisons(yml:74) 与 SignatureDeclareThrowsException(yml:234) 现为 tier 2 无 fixture 列；CovariantEquals 与 native 面无 manifest 行，无 manifest 动作。
  - migration-mapping 门禁 STATUS_VOCAB = {landed, keep-checkstyle, keep-pmd, deferred}——out-of-purpose 需新增词表值；**self-test control 2 的四词 replace 链必须同步扩 out-of-purpose**（否则 Phase 1 翻转后 `sampleSources[1]`=EmptyBlock 状态不再是四词之一，control 2 会假红）。
  - 对照纪律（Hard constraint 3）：核心面承接宣称须同语料命中集对照。checkstyle 侧产出 = 逐模块 `target/checkstyle-result.xml`（qa profile `consoleOutput=true`、`excludes **/_gen/**,**/_*.java`、默认 main-only 不含 test 源）；nop-lint CLI 支持 `--rules <id,...>` 白名单与 `--format checkstyle-xml|json`（item 39 面）；nop-lint `TargetScanner` 无 _gen 排除——同语料须显式枚举模块 src/main/java 并排除 `_gen/**`/`_*.java`。
- 测试基线：`./mvnw test -pl nop-lint/nop-lint-nop -am` 995/0 绿；六门禁全绿。

## Goals

- 12 行三轴归类落 checkstyle-pmd-migration.md：**checkstyle 17 行口径 = landed 9 行由 9 条规则承接（5 存量 + 4 新）+ out-of-purpose 8 行**；26 行全表口径另注（landed 11 行 → 10 规则，pmd:EmptyCatchBlock 双行同目标）。
- 4 条新规则落地（全带 fixtures + 命中集对照记录，规格与 checkstyle 行为逐点对齐，见 Phase 2 规格）。
- manifest 2 行 t2→t1（fixture 实存）；migration 门禁词表/self-test 扩展；census 数字链同步（58→62 / 48→52 / 62→66 / tier1 30→32）+ 散文计数同步清单（M7 五处，见 Phase 2）。

## Non-Goals

- qa profile checkstyle 配置段移除与 checkstyle.xml 删除（plan 18 独立 commit；**design 06 §8.1 判据文本全量修订也归 plan 18**——本计划只增注 migration doc §3 的 out-of-purpose 判据语义）。
- Checkstyle 工具级终裁回填（plan 18）。
- out-of-purpose 8 行的规则化（显式不迁移）。
- pmd-ruleset.xml 的 7 行 keep-pmd（item 4 同模式另 plan）。

## Scope

### In Scope

- `nop-lint/docs/checkstyle-pmd-migration.md`：8 行 out-of-purpose 翻转（Phase 1）+ 4 行 landed 翻转（Phase 2 对照后）+ §2 头部状态词表与尾部计数行 + §3 判据增注 + §4.2 对照记录。
- `ai-dev/tools/check-lint-tool-migration-mapping.mjs`：STATUS_VOCAB += out-of-purpose（含理由非空校验）+ self-test 增补 + control 2 链扩展。
- 新规则 4 份 rule.yml + 4 个 suite 目录 + TestNopRuleSuites（EXPECTED_RULE_IDS + categoryOf case）/ TestProductionRuleCount / gen-lint-rule-catalog.mjs 同步。
- `pmd-errorprone-coverage.yml` 2 行 t2→t1 + fixture；tier1 计数双处同步。
- 统一账本规则级分面表 +4 行（core/keep）+ subsection 标题与汇总行更新 + `RULE_FACET_CENSUS` 62→66。
- roadmap 散文计数同步：Current baseline 的"landed 7 / keep 19"与"62 条规则——分面复审后 58 条"两处；统一账本行级账本索引 checkstyle-pmd 行的"两份旧配置现均不可移除"半句（按 §3 新判据改写）。
- 对照记录 + roadmap item 3 状态翻转 + `ai-dev/logs/2026/09-28.md`。

### Out Of Scope

- qa profile / checkstyle.xml 文件本身零改动（plan 18）。
- `docs-for-ai/`：No owner-doc update required（规则准入 design 02 §5 已在档）。

## Execution Plan

### Phase 1 - 8 行 out-of-purpose 归类 + 门禁词表扩展

Status: completed
Targets: `nop-lint/docs/checkstyle-pmd-migration.md`、`ai-dev/tools/check-lint-tool-migration-mapping.mjs`

- Item Types: `Decision`

- [x] 8 行 out-of-purpose 翻转（EmptyBlock/UnusedImports/RedundantImport/MissingDeprecated/MethodLength/ParameterNumber/AnonInnerLength/MagicNumber），每行附分面理由（EmptyBlock 行附已落地核心子集规则 id）；**4 core 行保持 keep-checkstyle + "升规则，对照见 §4.2" 增注（landed 翻转归 Phase 2，避免 landed 行 target 校验先红）**
- [x] migration doc §2 头部状态词表补 out-of-purpose 定义；§3 切换判据增注：行 = landed 或 out-of-purpose 即非迁移债（判据达成 = 该配置段全部行 landed/out-of-purpose）
- [x] `check-lint-tool-migration-mapping.mjs`：STATUS_VOCAB += `out-of-purpose`；out-of-purpose 行校验 note 非空（仿账本回填门）；self-test control 2 replace 链扩第五词 + 新增非法 status 用例
- [x] roadmap item 3 = `planned`（本 plan 过 draft review 后翻转）

Exit Criteria:

- [x] migration doc 8 行 out-of-purpose 带理由、4 core 行带升规则增注、§2/§3 增注在档
- [x] `node ai-dev/tools/check-lint-tool-migration-mapping.mjs` 主检查 + self-test exit 0
- [x] roadmap item 3 = `planned`
- [x] `ai-dev/logs/2026/09-28.md` 已含条目（Phase 3 完整化）

### Phase 2 - 4 条核心行升规则 + 对照 + census 同步

Status: completed
Targets: rules 主资源、suites、TestNopRuleSuites / TestProductionRuleCount / gen-lint-rule-catalog.mjs、manifest、统一账本、账本门禁、migration doc

- Item Types: `Fix`（规则落地）+ `Proof`（对照）

- [x] 4 条规则落地（severity=warning；version 1.0；source 注 roadmap item 3 / checkstyle 行）。规格（与 checkstyle 行为逐点对齐）：
  - quality/string-literal-equality：**kind: string_literal 捕获 + inside 关系式**（先例 no-sensitive-literal 形态，非 `"..."` 字面 pattern），覆盖 `==` 与 `!=` 两侧中字面量参与比较的形态（含左右互换位）
  - quality/no-native-method：method_declaration 带 native modifier
  - exception/no-raw-throws：**spike 步骤显式化**——先探针验证 throws 子句节点形态（仓内无先例），再落 pattern：throws 类型列表逐类型 ∈ {Throwable, Error, RuntimeException}（简单名 + java.lang. 限定名双形态）；**@Override 方法豁免**（对齐 ignoreOverriddenMethods=true）；fixture valid 含 throws Exception/IOException/NopException 与 @Override throws RuntimeException，invalid 含双形态三类名 + 多类型子句
  - quality/covariant-equals：class_declaration/record_declaration/enum_declaration xscript 遍历方法集；报告粒度 = **每个协变 equals 方法一行**（非类级一次）；协变判定 = 方法名 equals + 单参 + 参数类型为非 primitive 类型名且 ≠ Object/java.lang.Object；全类无 equals(Object) 才报（primitive 参数不算协变也不算 Object 基准）
- [x] 4 个 suite 目录；`EXPECTED_RULE_IDS` +4（48→52）；`categoryOf` 加 exception/no-raw-throws case；`TestProductionRuleCount` +4 / 58→62；`gen-lint-rule-catalog.mjs` self-test 58→62；统一账本规则级表 +4 行（core/keep）+ subsection 标题"62 条"改"66 行（live 62 + remove 4）"与汇总行 core 46→50 + `RULE_FACET_CENSUS` 62→66；rule-catalog 再生成
- [x] **4 行 keep-checkstyle → landed 翻转**（规则落地 + 对照通过后执行；landed 行 target = 新规则 id），复跑 mapping 门禁；migration doc §2 尾部计数行同步为 landed 11 / out-of-purpose 8 / keep-pmd 7
- [x] manifest 2 行 t2→t1 + fixture（LiteralsFirstInComparisons → string-literal-equality suite；SignatureDeclareThrowsException → no-raw-throws suite）；tier1 30→32 双处（roadmap baseline + 账本索引行）
- [x] **命中集对照（命令写实）**：
  - checkstyle 侧：`./mvnw checkstyle:check -Pqa`（或 install -Pqa）→ 逐模块 `target/checkstyle-result.xml`，按 4 规则名过滤命中行（file+line+rule 为比较键）；语料 = 各模块 main 源码（_gen/_*.java 已由插件 excludes 排除，test 源不在语料）
  - nop-lint 侧：CLI `--rules quality/string-literal-equality,quality/no-native-method,exception/no-raw-throws,quality/covariant-equals` + 显式枚举与 checkstyle 同语料的模块 src/main/java 路径集（排除 `_gen/**`/`_*.java`）
  - 零 diff 或 delta 逐条裁定，记录落 migration doc §4.2；**双方零命中情形预案**：以 checkstyle 报告总行数为语料证据 + 记录"含 test 源扩语料"备选口径，对照有效性由 fixtures + 语料证据共同背书
- [x] delta 裁定预案：covariant-equals 若出现 checkstyle 不报而新规则报的形态（泛型/通配符参数等语法面偏差），逐条裁定修 pattern 或记机制缺口降行（降行数字涟漪：census 66→65、EXPECTED 52→51、count 62→61、该行改 out-of-purpose）——不允许静默放宽
- [x] 散文计数同步（M7 清单，共五处）：roadmap Current baseline 两处（"landed 7 / keep 19"→ 新分布；"58 条"→"62 条（item 3a 后）"）；统一账本行级账本索引 checkstyle-pmd 行状态分布与"两份旧配置现均不可移除"半句（checkstyle 侧按 §3 新判据改为"可移除待 plan 18 切换"）；统一账本规则级分面表 subsection 标题与汇总行；migration doc §2 尾部计数行（见上条翻转步骤）

Exit Criteria:

- [x] `./mvnw test -pl nop-lint/nop-lint-nop -am` 全绿（census 62、EXPECTED 52、4 suite 绿、categoryOf 分派正确）
- [x] 六门禁 + catalog --check + doc-links strict 全绿；账本门禁 census 66 一致
- [x] migration doc：4 行 landed（target=新规则 id）、§4.2 对照记录在档（比较键 + 零 diff/delta 裁定 + 零命中预案结论）
- [x] manifest 门禁 exit 0（tiers {"1":32,...}）
- [x] 散文计数五处同步完毕，grep 无"62 条规则库"裸宣称残留于活文档（历史快照段豁免）
- [x] No owner-doc update required（design 02 §5 准入判据已覆盖新规则口径；design 06 §8.1 全量修订归 plan 18）
- [x] `ai-dev/logs/2026/09-28.md` 已记录 4 规则落地与对照结果

### Phase 3 - 回归与收口准备

Status: completed
Targets: 全部门禁

- Item Types: `Proof`

- [x] 全门禁联跑 + roadmap item 3 状态前置检查（plan 18 为 successor：qa profile 切换后 item 3 才翻 done）

Exit Criteria:

- [x] 门禁联跑全 exit 0（ledger、migration-manifest、mapping、coverage、gen-catalog --check、doc-links）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-nop --severity high` exit 0
- [x] 日志条目完整（12 行归类 + 4 规则落地 + 对照结论）

## Closure Gates

> 规则资源 + 测试变更：构建验证 = `./mvnw test -pl nop-lint/nop-lint-nop -am`（Phase 2 Exit Criteria）。

- [x] Phase 1–3 全部 Exit Criteria 勾选完毕
- [x] 12 行三轴归类完成且 migration doc 与 live 一致（mapping 门禁看守）
- [x] 4 条核心行升规则落地且同语料对照通过（零 diff 或 delta 裁定在档；双方零命中时语料证据在档）
- [x] 全部门禁绿 + nop-lint-nop 测试全绿
- [x] 无被静默降级的 in-scope live defect（对照发现的 delta 必须裁定，不得留 unstated delta）
- [x] roadmap item 3 维持 `planned`（done 归 plan 18 切换后）
- [x] 独立子 agent closure audit 完成且证据写入 Closure 段
- [x] Anti-Hollow Check：4 新规则不是空壳（fixtures 断言真实命中；对照含语料证据，非"双方零跑/零查"）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/17-checkstyle-facet-adjudication.md --strict` exit 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- CovariantEquals 若对照裁定为机制缺口（语法面无法 faithful），行降 out-of-purpose/机制缺口并按数字涟漪清单联动（census 66→65、EXPECTED 52→51、count 62→61），plan 18 切换时该行按非 landed 处置——不阻塞其余 3 规则。
- design 06 §8.1 判据文本全量修订归 plan 18（随切换一并落）。

## Closure

Status Note: 三个 Phase 全部完成且经独立 fresh-session 子 agent closure audit 逐项 live 复核通过（审计员独立复现对照：checkstyle 3 命中 ↔ nop-lint 同文件同行 3 命中零 diff，@Override 豁免双侧对齐实证；语料数字 276 报告/7,830 文件次/9,980 文件逐一复核一致），12 行三轴归类与 4 规则落地经六门禁 + 999 项 lint 模块测试全绿，roadmap item 3 维持 `planned`（done 归 plan 18 切换后）。本计划关闭。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_200546ad-a2a0-4f83-96ac-f222a8234a2b（fresh session）
- Audit Session: agent_200546ad-a2a0-4f83-96ac-f222a8234a2b
- Evidence:
  - 三轴归类：4 行 landed（target=新规则 id）+ 8 行 out-of-purpose 带理由；§2 词表/尾部计数（landed 11 / out-of-purpose 8 / keep-pmd 7）/§3 判据/§4.2 对照在档；mapping 门禁 + self-test（含 control 4 理由强制）exit 0
  - 4 规则：rule.yml 语义逐点核对（@Override 豁免/双形态类名/==!= 双面/Object 子类型/每方法一报/enum 体遍历）；fixtures 覆盖规格点（含 @Native 不报、@Override throws RuntimeException 豁免）；EXPECTED_RULE_IDS 52 + categoryOf 分派 + census 62 + RULE_FACET_CENSUS 66
  - 测试与门禁：-am 全链 2,960 tests 0 failures BUILD SUCCESS（nop-lint 模块 999）；六门禁 + hollow 0 + doc-links strict 全绿；coverage tiers {"1":32,"out-of-purpose":3}
  - 对照真实性（审计员独立复现）：276 份报告实存，IllegalThrowsCheck 3 命中与 §4.2 逐字一致；CLI 自跑 AopProxyHelper:21 / ReflectionHelper:128,148 同文件同行；反向抽查 throws RuntimeException 全为 javadoc 文本（0 误报）；AnnotationProxy @Override 豁免双侧对齐；语料数字（7,830/9,980）精确复核
  - 裁判定性：8 行 out-of-purpose 落风格/可选/尺寸面定义、4 行 core 落正确性/平台不变式，无 Hard constraint 2 违背
  - 审计 Minor-1（record 面未含且无书面裁定）已消解：§4.2 补 record 面显式 delta 裁定（v1 面为 class+enum，零 diff 不受影响，重估触发在档）；Minor-2/3 为表述与门禁调用口径提示，不阻塞
- Follow-up:

Follow-up:

- record 面重估触发：出现 record 含 equals 重载的语料时再评估（§4.2 裁定在档）。
- mapping 门禁 self-test 调用口径为 `self-test` 子命令（无 `--` 前缀），已在审计中确认，未来门禁统一化时再收口。
