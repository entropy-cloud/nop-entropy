# 19 pmd 7 行三轴归类与核心行升规则（roadmap item 4a）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap item 4](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [plan 17](17-checkstyle-facet-adjudication.md)（机制全复用）· [plan 18](18-checkstyle-qa-profile-switchover.md) · [checkstyle-pmd-migration 账本](../../../nop-lint/docs/checkstyle-pmd-migration.md) · [统一账本](../../../nop-lint/docs/tool-replacement-ledger.md)

## Purpose

对 pmd-ruleset.xml 迁移映射的 7 条 keep-pmd 行逐行归入三轴（Clone 族/控制流面——先证是否属核心缺陷面再裁去向）：核心缺陷行升 nop-lint 规则（与 PMD 7.26.0 实测语义逐点对齐，含 pmd 同语料对照），风格/机制缺口行显式裁定。本计划是 item 4 的前半——pmd 配置段切换与 PMD 终裁回填归 successor plan 20（判据达成后执行；若 7 行未全部收口则 PMD 终裁按 replaced-partial/keep-tool 裁定，切换不执行）。

## Current Baseline

- pmd-ruleset.xml 实测 9 行引用：EmptyCatchBlock（landed）+ HardCodedCryptoKey（landed）+ keep-pmd 7 行 = JumbledIncrementer / AvoidBranchingStatementAsLastInLoop / ImplicitSwitchFallThrough / CloneMethodMustImplementCloneable / CloneMethodReturnTypeMustMatchClassName / ProperCloneImplementation（errorprone.xml）+ InsecureCryptoIv（security.xml）。qa 接线 `pom.xml:456–487`（failOnViolation=false、excludes `_gen/**`/`_*.java`、main-only）。
- **PMD 7.26.0 语义事实**（R1 审查以本地 jar 反汇编 + 7.26.0 tag 源码 + 官方文档三重核对，语料命中已脚本复现——`_tmp/pmd-audit/scan3.mjs` 留档）：
  - AvoidBranchingStatementAsLastInLoop：注册 break/continue/**return** 三种语句；**无末语句位置检查**（规则名历史遗留）——一层 Block 解包后直属于 for/foreach/while/do 体的任意 break/continue/return 都报；`checkReturnLoopTypes` 默认 for,do,while（foreach 上的 return **不报**）；violation 落在分支语句行。**foreach 与经典 for 同按 FOR 面处理**（ASTForeachStatement 显式并入 FOR 分支，无独立枚举值；checkReturnLoopTypes 默认含 FOR ⇒ foreach 体直连 return 也报——当前语料 4 命中即四个 ResolverDiscovery 的 enhanced-for 体 return）。另有循环体 try 的 finally 子句额外一层解包（finallyClause.ancestors().get(2)）也报（v1 face delta 记一行）。全仓另有 ~37 处 break/continue 候选。
  - ImplicitSwitchFallThrough：**dataflow 判定**（if/else 双 return 的组不报）+ 注释豁免 regex `/[/*].*\bfalls?[ -]?thr(ough|u)\b.*/i` 落于下一 case label 前；规则链同时访问 switch 语句与 switch 表达式，被排除的是 **arrow 分支**（AST 类型区分；colon 风格 switch 表达式在检查面内）；violation 落在下一个 case label。当前语料 0 命中。
  - CloneMethodMustImplementCloneable：isCloneMethod = 名 clone + arity 0 + 非 static（**无 public 要求**）；豁免 = 方法体恰为单条 `throw CloneNotSupportedException`；仅 class（enum/record/interface 不查）；Cloneable 判定 = `TypeTestUtil.isA` **全超类型闭包**（直接 implements、接口 extends、父类链都算）；violation 落在 clone() 方法。当前语料 1 命中（MetaVarEnv，class :22 / clone() :111）。
  - CloneMethodReturnTypeMustMatchClassName：clone() 返回类型须为类名（Object 返回也报）。语料 0 命中。
  - ProperCloneImplementation：clone()（非 static 非 abstract）在**非 final 类**中、方法体内构造**包围类自身实例**（类型符号相等，7.x **不检查 super.clone()**）；final 类豁免——`MetaVarEnv`（public final class，clone() 体内 `new MetaVarEnv()`）PMD 不报。语料 0 命中。
  - InsecureCryptoIv：除数组初始化字面量外还报**字符串字面量实参**与**一跳变量解析**（`iv = "x".getBytes()`）——前两者语法面可表达；被裁掉的是"一跳变量剩余 + 污点面"两层。语料 0 命中。
  - JumbledIncrementer：PMD 官方描述"usually a mistake, confusing even if intentional"。语料 0 命中。
- 分面裁定（执行时逐行落账本；对照数据可推翻）：
  - **core → 升规则（6）**（id 与对齐面）：
    - `quality/no-branching-in-loop-body`（AvoidBranchingStatementAsLastInLoop）：**对齐 PMD 7.26 面**——break/continue/return 直属 for/foreach/while/do 体（一层 Block 解包）即报，无末语句位置要求；**foreach 与 for 同面（体直连 return 也报）**；finally 子句解包面入 delta 注记；report 锚分支语句（line 对齐）；id 弃用 "last" 命名（对 7.26 不成立）。
    - `exception/implicit-switch-fall-through`：**v1 形状近似面**——switch_statement 遍历 switch_block_statement_group 序列，非末组末语句 kind ∉ {break, return, throw, continue} → report（report 节点 = 下一组 label，line 对齐）；**delta 预案**：dataflow 判定（if/else 双 return 不报）、注释豁免、arrow 分支排除（colon 风格 switch 表达式在 PMD 面内而 v1 statement 锚不含）三项为 PMD 精确语义，spike 后可表达者纳入、不可表达者记 delta（语料 0 命中，对照零 diff 平凡成立，fixtures 固化的是 v1 形状语义并注明）。
    - `quality/no-clone-without-cloneable`：锚 method_declaration（名 clone + arity 0 + 非 static；**无 public 要求**）；豁免 = 体恰为单 throw CloneNotSupportedException；仅 ancestor class_declaration（enum/record/interface 排除）；implements 子句无 Cloneable/java.lang.Cloneable → report 锚方法（line 对齐）。**delta**：PMD 全超类型闭包（接口 extends/父类链）不可语法表达——父链 Cloneable 形态会误报，delta 记录 + 对照裁定（语料 1 命中 MetaVarEnv 若属父链形态则成 delta 案例）。
    - `quality/clone-return-type-mismatch`：class 内 clone() 返回类型 text ≠ 类简单名 → report 锚方法。
    - `quality/proper-clone-implementation`：锚 method_declaration clone()（非 static 非 abstract），**ancestor class 非 final**，体内 object_creation_expression 的类型 text == 包围类简单名（语法代理，type-resolution delta 记录）→ **report 锚 clone() 方法**（PMD addViolation(method) 落点对齐）；final 豁免对齐（MetaVarEnv 反例即验证锚）。
    - `security/no-hardcoded-iv`：IvParameterSpec 构造实参 = 数组初始化字面量或字符串字面量 → report（语法面）；**分层 delta**：一跳变量解析（spike 后可表达则纳入）+ 污点面 = 机制缺口显式登记。
  - **out-of-purpose → 不迁移（1）**：JumbledIncrementer——增量写法可读性/混乱面（PMD 自述 "usually a mistake, confusing even if intentional" 属困惑性而非缺陷拦截），语料 0 命中，零控制流缺陷信号。
- 机制复用（plan 17/18 已验证）：规则落地全链、mapping 门禁 out-of-purpose 理由强制、对照协议（`./mvnw pmd:check -Pqa` → 逐模块 target/pmd.xml 解析 vs nop-lint CLI 6 规则白名单同语料；**JumbledIncrementer 命中仅记录不对照**）。
- **manifest 实测定论**：7 行中**仅 `PMD:InsecureCryptoIv` 有 manifest 行**（tier 2、无 fixture）；其余 6 行全部 manifest 外（与 plan 2026-09-24-2350-1 B1 口径"manifest 只含 PMD 9 条中 3 条"一致）——manifest 处置仅 1 行 t2→t1 + fixture + approximation 分层注记。
- 数字链现值：规则库 62、EXPECTED_RULE_IDS 52、RULE_FACET_CENSUS 66、tier1 32；**存量漂移**：统一账本行级账本索引行现写"tier 1 = 30"（plan 17 漏改，实测 manifest 32）——本计划随提升一并修正 30→33。
- 测试/门禁基线：全绿（plan 18 后）。

## Goals

- 7 行三轴归类落 checkstyle-pmd-migration.md：6 行 landed（新规则 + pmd 对照）、1 行 out-of-purpose；§3 pmd 侧判据文本同步（7 行翻转后"keep-pmd 7 行不可移除"断言失效）。
- 6 条新规则落地（与 PMD 7.26.0 实测语义逐点对齐 + fixtures + 同语料对照记录）。
- 全仓 pmd 命中集对照零 diff 或 delta 逐条裁定；计数链同步（62→68 / 52→58 / 66→72 / tier1 32→33 及账本索引行 30→33 修正）。

## Non-Goals

- pmd-ruleset.xml / pmd 插件块处置（plan 20）。
- PMD 工具级终裁回填（plan 20）。
- InsecureCryptoIv 一跳变量剩余面/污点面机制建设（机制缺口显式登记）。
- `docs-for-ai/`：No owner-doc update required。

## Scope

### In Scope

- `nop-lint/docs/checkstyle-pmd-migration.md`（7 行翻转 + §3 判据文本同步 + §4.3 对照记录）。
- 新规则 6 份 rule.yml + 6 suite 目录 + TestNopRuleSuites（EXPECTED + categoryOf）/ TestProductionRuleCount / gen-lint-rule-catalog.mjs / 统一账本分面表 + RULE_FACET_CENSUS / rule-catalog 再生成。
- `pmd-errorprone-coverage.yml` InsecureCryptoIv 行 t2→t1 + fixture + 分层 approximation 注记。
- roadmap item 4 状态 + 散文计数同步（roadmap baseline 映射分布/规则数/tier 计数、账本索引行 tier 30→33、分面表标题/汇总）+ `ai-dev/logs/2026/09-28.md`。

### Out Of Scope

- pmd 插件块 / pmd-ruleset.xml 零改动（plan 20）；`docs-for-ai/`。

## Execution Plan

### Phase 1 - 7 行归类 + 6 规则落地 + 对照

Status: completed
Targets: migration doc、rules 主资源、suites、测试三件套、gen/账本门禁、manifest

- Item Types: `Fix`（落地）+ `Decision`（归类）+ `Proof`（对照）

- [x] JumbledIncrementer 行 → out-of-purpose（理由与 PMD 自述对话在档）；其余 6 行 keep-pmd + 升规则增注（landed 翻转待对照后）
- [x] 6 条规则落地（规格 = Current Baseline"core → 升规则"六条，逐条按 PMD 7.26 实测语义；spike 优先验证：throws/switch group/foreach 判定/数组初始化 kind 形态；report 节点全部按 PMD violation 落点对齐——分支语句 / 下一组 label / clone() 方法）
- [x] 6 suite 目录 + EXPECTED_RULE_IDS 52→58 + categoryOf（exception/implicit-switch-fall-through 入 case）+ TestProductionRuleCount 62→68 + gen self-test 62→68 + 账本分面表 +6 行（core/keep）+ RULE_FACET_CENSUS 66→72 + rule-catalog 再生成
- [x] manifest：InsecureCryptoIv t2→t1 + fixture + 分层 approximation 注记（语法面 landed / 一跳剩余 + 污点面 = 机制缺口）；tier1 32→33；账本索引行 tier 30→33 修正（存量漂移）
- [x] pmd 对照：`./mvnw pmd:check -Pqa` 全仓实跑 → 逐模块 target/pmd.xml 解析 7 规则命中 vs nop-lint CLI 6 规则白名单同语料（JumbledIncrementer 命中仅记录）；比较键 file+line+rule；零 diff 或 delta 逐条裁定记录落 §4.3；零命中预案同 plan 17
- [x] 6 行 landed 翻转（对照后）+ §2 尾部计数行 + §3 pmd 侧判据文本同步（"keep-pmd 7 行不可移除"改为实际收口后状态；plan 20 判据输入）+ 散文计数同步（roadmap baseline 映射分布/规则数 68/tier 33、账本索引行、分面表标题"live 68 + remove 4 = 72"与汇总 core 50→56）
- [x] 全门禁 + `./mvnw test -pl nop-lint/nop-lint-nop -am` + hollow 扫描

**执行期裁定增注（2026-09-28）**：implicit-switch-fall-through 经全仓对照（形状近似 219 vs PMD 6）不可逐条裁定，按 Deferred 段裁定不入库（行 = deferred，机制缺口：dataflow 判定 + falls-through 注释豁免）；clone 族报告锚改方法名节点（PMD beginline 对齐 → 15/15、4/4、14/14 零 diff）；proper-clone 泛型钻石形态修正；AvoidBranching 对照 43/43（8 处批扫缺口经直接 CLI 复核补齐 + 1 NOPMD 抑制符 delta）。

Exit Criteria:

- [x] migration doc：7 行新 status（6 landed / 1 out-of-purpose）、§3 同步、§4.3 对照记录在档（比较键、零 diff 或 delta 裁定——**重点：no-branching-in-loop-body 与 PMD 命中数对齐（语料 return 形态命中必须被覆盖），clone 族 MetaVarEnv 命中的对照结论在档**）
- [x] `./mvnw test -pl nop-lint/nop-lint-nop -am` 全绿；六门禁 + catalog --check + doc-links strict + hollow 全绿；账本 census 72 一致
- [x] 6 新规则 fixtures 覆盖规格点（return 面含 foreach 同面、单 throw 豁免、final 豁免、数组/字符串字面量、形状 vs dataflow delta 注记）
- [x] 散文计数同步完毕（含账本索引行 tier 30→33 修正）；`ai-dev/logs/2026/09-28.md` 条目完整
- [x] No owner-doc update required（design 02 §5 已覆盖）

## Closure Gates

> 构建验证 = `./mvnw test -pl nop-lint/nop-lint-nop -am`（Phase 1 Exit Criteria）。

- [x] Phase 1 全部 Exit Criteria 勾选完毕
- [x] 7 行归类完成且 migration doc 与 live 一致；6 规则落地且对照通过（零 diff 或 delta 裁定在档；PMD 语义对齐点逐一有 fixture 或 delta 记录）
- [x] 全部门禁绿 + 测试全绿
- [x] roadmap item 4 维持 `planned`（done 归 plan 20 切换后）
- [x] 独立子 agent closure audit 完成且证据写入 Closure 段
- [x] Anti-Hollow Check：6 新规则 fixtures 真实命中；对照含语料证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/19-pmd-facet-adjudication.md --strict` exit 0

## Deferred But Adjudicated

- ImplicitSwitchFallThrough 的 dataflow 判定/注释豁免若 spike 不可表达：v1 形状面照常 landed（delta 在档），精确面差异记 approximation——该行 status 用词表合法值（landed 或 deferred，无"机制缺口"词），降级时数字涟漪按实际联动。
- InsecureCryptoIv 一跳变量剩余面：spike 后不可表达则入机制缺口记录（Deferred），不阻塞 landed。

## Non-Blocking Follow-ups

- 污点面（非随机 IV 数据流）重估触发 = item 7 资源泄漏面机制落地后复用（同 acquire/release 配对机制面）。

## Closure

Status Note: 7 行三轴归类完成（5 landed + 1 deferred + 1 out-of-purpose，执行期裁定增注在档——Goals 中"6 行 landed/68/72/56"的目标数字由降级涟漪显式承载并按 67/71/55 落地），5 规则 landed 且同语料对照 43/15/4/14/0 全零 diff（审计员独立复现 PMD 计数 + MetaVarEnv final 豁免活体双跑 + DoubleCounter 精确同行），全门禁与 84 项模块测试绿。本计划关闭；roadmap item 4 维持 `planned`（pmd 配置段判据未全达，切换/终裁归 plan 20）。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_be230c23-8c4e-4bd1-a75c-da09dcf525cf（fresh session）
- Audit Session: agent_be230c23-8c4e-4bd1-a75c-da09dcf525cf
- Evidence:
  - 7 行归类：5 landed（target 精确）+ deferred（机制缺口理由）+ out-of-purpose（PMD 自述对话）；尾部计数 landed 16 / out-of-purpose 9 / deferred 1 与逐行加总吻合；mapping 门禁 + self-test exit 0
  - 5 规则：语义逐点核对（三语句/foreach 同面/单 throw 豁免/super_interfaces 链/非 final 豁免/钻石前缀比较/方法名锚）；EXPECTED 57 + census 67 + RULE_FACET_CENSUS 71；surefire 84/0（TestNopRuleSuites 64/64）
  - 对照真实性：386 份 pmd.xml 独立复现 43/15/4/14/0/6/0 全部计数；MetaVarEnv 活体双跑（no-clone :111 命中 = PMD beginline 精确同行；proper-clone final 豁免双侧对齐）；DoubleCounter 两规则 :70 精确同行；QuarkusFileService NOPMD delta 归因实证
  - deferred 诚实性：规则/suite/EXPECTED/categoryOf/census/catalog 全链清除，grep 残留仅 2 处合法裁定记录；行 status = deferred（词表合法）+ 重估触发在档
  - 审计 Minor-1/2 已消解：单 throw 豁免 fixture（cloneOnlyThrow 真实负向覆盖）+ 泛型钻石 fixture（GenericBox `new GenericBox<>()` 双命中）落地后 84/0 复绿；Minor-3 由本 Closure 引用执行期裁定增注消解
- Follow-up:

Follow-up:

- ImplicitSwitchFallThrough 精确面（dataflow + falls-through 注释豁免）重估触发：引擎表达力具备时（Deferred 在档）。
- InsecureCryptoIv 污点面重估触发：item 7 资源泄漏机制落地后（Deferred 在档）。
