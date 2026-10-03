# 2306 缺陷嫌疑批量修复（WI0-WI11 测试暴露的 44 项）

> Plan Status: active（rev2 复审通过：3 处一等笔误与 2 处格式已修，可进入执行）
> Last Reviewed: 2026-10-02
> Source: ai-dev/bugs/2026-10/ 下 11 个缺陷记录文件（43 项源条目 + 1 项 fraud 2PC = 44）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi13-closing-baseline.md（P1 优先清单）
> Related: ai-dev/plans/2293-2305（缺陷发现方）；ai-dev/bugs/00-bug-fix-note-writing-guide.md；docs-for-ai/02-core-guides/error-handling.md

## Purpose

修复 unit-test-coverage-roadmap 执行期暴露的 44 项产品缺陷嫌疑：逐项确认 → 最小修复 → 回归测试（翻转钉住测试为正向断言 / 解除 @Disabled / 无钉者新增）→ 模块与下游验证 → bug 记录文件回填 Fix/Tests 段。

## Current Baseline

- 44 项（43 源条目 + fraud）全部记录在 `ai-dev/bugs/2026-10/`。**钉住状态口径（审查勘误）**：多数项以"注释避让/探针复现/特征化锁定"记录，**真正有缺陷行为断言钉子的较少**——WI1 两项（dateBetween/like）为注释避让（只断言不受影响部分，需新增正向用例）；WI5 两项有 trip-wire（翻转即得）；wi2#1 有 @Disabled（:94，解除即得）；wi3/wi10 部分有特征化锁定（翻转即得）；其余无钉项按协议第 4 步新增回归测试。
- **protected area 声明**：本 plan 修改 nop-core / nop-xlang / nop-antlr4-common 产品内部，符合 AGENTS.md plan-first 要求（本 plan 即依据），每项修复带回归测试。
- **审查改判（rev2 Blocker 修复）**：wi3#5（JsPromise 三处偏离）已在 commit `df4e4f8fa7`（plan 2282-WS4）修复，TestJsPromise 现为正向断言——本 plan 改判 **already-fixed**，仅回填 bug 文件指向该 commit，不列入执行条目。
- 既有排除项（防误判遗漏）：TarjanSCC lowLink、retry 幂等键 P1 已被 plan 2282 WS2/WS3 覆盖；WI3"语义观察"3 条声明不构成缺陷指控——均不在本 plan。

## 源条目 ↔ plan 项号映射（数字口径声明）

**口径：44 = 43 个源 bug 条目 + 1 个 fraud 项。** 本 plan 40 个执行条目 + 1 个 already-fixed 改判。合并/拆分：wi1#6（3 子缺陷）→ 项 30；wi2#2+#3 → 项 31；wi7#1 → 项 35（含 getDomain 与 getModule 两处，同条目）；wi10#1-3 → 项 6、wi10#4 → 项 6b、wi10#5 → 项 7、wi10#6 → 项 39；wi3#5 → already-fixed。

| Phase | 执行条目 | 覆盖源条目 |
|---|---|---|
| Phase 1（10 条目） | 项 1-9 + 项 6b | wi1#1/#2/#5、wi3#1、wi4#1、wi5#1、wi10#1-5、wi11#1（12 条源目） |
| Phase 2（20 条目） | 项 10-29 | wi1#3/#4、wi2#1/#4、wi3#2/#3、wi4#2/#4、wi5#2、wi7#2/#4、wi8#1/#2、wi9#1-6、wi11#2（20 条源目） |
| Phase 3（9 条目） | 项 30-32、34-39 | wi1#6（3 子）、wi2#2/#3、wi3#4、wi4#3、wi7#1/#3、wi8#3、wi9#7、wi10#6（10 条源目） |
| already-fixed | — | wi3#5（回填指向 df4e4f8fa7） |
| Phase 4（1 条目） | fraud 项 | stream-2pc |

## Goals

- 43 个源条目 + fraud 逐项落地三态之一：`fixed` / `adjudicated-not-a-defect`（含 already-fixed 改判）/ `diagnosed-split`（fraud 项按诊断结论分流）。
- P1 级 9 项优先修复；汇总表按源条目口径可机械核对。

## Non-Goals

- 不修 build-infra 三项（nop-kernel 组 pom parent、nop-rg argLine、JDK profile 停用 activeByDefault）——独立立项。
- 大动作（janino 升级、JsPromise 引擎重写）只做最小修复，不可行则 `diagnosed-split` 到新 plan。
- 不为凑修复数把"确认属预期行为"的项强行改代码。

## 逐项修复协议（每项必须走完）

1. **确认**：复现钉住测试/探针（无钉项先写失败测试），重读 bug 文件证据；确认属缺陷。
2. **爆炸半径**：grep 该 API/行为全部消费方；行为语义变化项（下表标注 ▲）必须**当期跑直接下游模块**（不仅 -am 上游），并在 bug 文件记录消费方清单与影响评估。
3. **最小修复**：遵守两级错误处理约定；新错误码需注册声明；错误消息/错误码/异常类型变化项按 testing.md「运行时字面量批量改写协议」grep `_cases`（已预检：现存 _cases 对本 plan 涉及错误码零命中）并全 reactor 验证。
4. **回归测试**：翻转钉住/特征化测试为正向断言；解除 @Disabled；无钉项新增回归测试。
5. **验证**：`mvnq -- test -pl :<module> -am -fae` + ▲ 项的下游模块当期验证。
6. **落账**：bug 文件回填 Fix/Tests/Affected Files（bug guide）；提交用精确路径 add。

## Scope

### In Scope

- 上表 44 项；对应模块产品代码 + 测试修改；bug 文件回填；项 6 含审查新发现的三处文法缺陷（PIE 重复定义、`Identifier` 未定义引用、checked-in 生成物与文法脱节）。

### Out Of Scope

- build-infra 三项；nop-lint-nop 普查（并行会话已修）；rule-service 快照（WI7 已修）；TarjanSCC/retry 幂等键（plan 2282 已覆盖）；WI3 语义观察 3 条。

## 执行条目明细

### Phase 1 - P1 核心（10 条目 / 12 源条目）

Status: completed

- Item Types: `Fix`

| # | 项 | 模块 | 修复方向 | 回归基座 | 下游义务 |
|---|---|---|---|---|---|
| 1 | dateBetween max 失效（wi1#1） | nop-core FilterOpHelper:269 | `toLocalDate(min)`→`(max)` | **无钉，新增**边界用例 | ▲ 已证模块外零直接消费方（仅 nop-core 内 FilterOp）：grep 证据入 bug 文件 + nop-core 自身测试 |
| 2 | like 方向反（wi1#2） | nop-core FilterOpHelper:63 | `SqlLikeUtils.sqlToRegexLike(s2)` 匹配 s1 | **无钉，新增** | ▲ 同项 1 口径 |
| 3 | 常量在左比较反转（wi3#1） | nop-xlang ExpressionToFilterBeanTransformer | 启用已计算的 reverseOp | 特征化用例翻转 | |
| 4 | initRefs 集合注册时序（wi4#1） | nop-orm-model | 两遍循环合一或回填 map | **无钉，新增** roundtrip | |
| 5 | GraphBFS root 未入 visited（wi5#1） | nop-core GraphBreadthFirstIterator | root 入 visited | trip-wire 翻转为正向错误码断言 | |
| 6 | mermaid 文法三项（wi10#1-3）：CLASS/STATE 重复（含审查新发现 PIE 重复 L9/L45）、DIRECTION 无词法（补 lexer 规则 + `Identifier` 未定义引用修正）、sequenceMessage/flowEdge 同构 | nop-mermaid `.g4` | token 去重/补规则/规则区分；**重生成流程：root pom exec-maven-plugin `precompile` execution（generate-sources 阶段）→ `nop-format/nop-mermaid/precompile/gen-mermaid-parser.xgen` → nop-codegen antlr 模板，产物 checked-in（.java/.tokens/.interp）须一并再生** | 3 个特征化用例必翻红（testClassKeywordLexesAsIdentifier:194、testStateKeywordLexesAsIdentifier:206、testSequenceMessageTokensDispatchToFlowEdge:100）+ 新增 class/state/direction 正向用例 | |
| 6b | SLL 阶段 NopException 绕过 LL 重试（wi10#4） | **nop-kernel/nop-antlr4-common** AbstractParseTreeParser:84-106（非 nop-mermaid！） | twoPhaseParse 增捕 NopException 触发 LL 重试——**repo 级爆炸半径**（xpl/expr/eql/xml/yaml 全部解析器公共路径） | nop-mermaid 不可恢复错误用例调整 | ▲ nop-xlang、nop-orm-eql 全量测试当期跑（实证消费方仅此两模块；nop-mermaid 自身由 -am 门覆盖） |
| 7 | pdf RCPath 合并单元格双重展开 | nop-pdf RCPathCellDataLocator:205 | 去重展开或边界钳制 | **无钉，新增**（含贴边崩溃输入） | |
| 8 | HealthStatus.merge 反转（wi11#1） | nop-cluster-core HealthStatus | worst-wins；**裁定点：OUT_OF_SERVICE(3) 是否视作"最差"需对齐 Spring Boot 语义后定序** | 表征测试翻转（TestCompositeHealthChecker:47-49） | |
| 9 | AStar scoreMap 未写入（wi1#5） | nop-core AStarPathFinder:101-145 | 补 scoreMap 写入 + reconstructPath | TestGraphAlgorithms:126-163 退化断言翻转 | |

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 10 条目全部 `fixed` 或 `adjudicated-not-a-defect`（记录理由）。——10/10 fixed；证据见 bugs/2026-10/ 各文件 Fix 段与汇总文件
- [x] 每项回归测试绿（翻转/新增/解除 @Disabled）；项 6 生成物与 .g4 同步入库。——nop-core 496 / nop-xlang 855 / nop-orm-model 32 / nop-wf-core 47 / nop-cluster-core 27 / nop-pdf 70 / nop-mermaid 26 / nop-antlr4-common 3 全绿；mermaid 生成物（.java/.tokens/.interp/_Visitor）已随 precompile 再生入库
- [x] ▲ 项（1/2/6b）下游模块当期验证绿；消费方 grep 记录入 bug 文件。——6b：nop-xlang 855 + nop-orm-eql 91 当期跑全绿；1/2 grep 证据在 wi1 bug 文件
- [x] 各模块 `-am` 全绿（既有测试零回归，除各行「回归基座」列声明翻转/调整的用例集合：项 3/5/6/8/9 与 6b 的 mermaid 错误路径用例）。——批次验证记录见 daily log；唯一非声明回归（TestXLangParser.testIdentifier）系 6b 首版宽捕引入，已按错误码分流修复并补三例合同测试
- [x] bug 文件 Fix/Tests 段回填，`ai-dev/logs/` 已更新。
- [x] Owner-doc 裁定：nop-core/nop-xlang 行为修复不改变公开契约文档（docs-for-ai 查询语义描述与修复后行为一致）——记录核查结论。——核查：dateBetween/like 查询语义、比较符交换语义在 docs-for-ai 中的描述与修复后一致；无需变更（结论记录于 wi1/wi3 bug 文件）

### Phase 2 - 确认机制类 20 条目（20 源条目）

Status: completed

- Item Types: `Fix`

| # | 项（源条目） | 模块 | 修复方向 | 回归基座 | 下游义务 |
|---|---|---|---|---|---|
| 10 | ModifierBuilder PUBLIC_MASK=0（wi1#3） | nop-core | `&`→`\|` | **无钉，新增** |
| 11 | GlobalStatManager 排序反（wi1#4） | nop-core | 修正 comparator | **无钉，新增**正向断言 |
| 12 | GraphQLResponseBean null Boolean NPE（wi2#1） | nop-api-core | null 安全 | @Disabled:94 解除 |
| 13 | MutableIntArray.addAll 校验不一致（wi2#4） | nop-commons | 补前置校验（AIOOBE→IAE **异常类型契约变化**） | **无钉，新增** |
| 14 | CallExpression.getArgument 越界（wi3#2） | nop-xlang | 参数数校验，复用既有 `ERR_FILTER_OP_INVALID_ARG_COUNT`（XLangErrors:184） | 特征化用例翻转 |
| 15 | union anyOf 放原始 ISchema（wi3#3） | nop-xlang | 放转换后 list | **无钉，新增** |
| 16 | OrmComputePropModel 裸 IAE（wi4#2） | nop-orm-model | NopException + **新错误码需注册** | **无钉，新增** |
| 17 | buildPageExpr 缺 OFFSET 0（wi4#4） | nop-orm-eql | 补 `OFFSET 0 ROWS` | **无钉，新增** |
| 18 | WfModelAnalyzer.checkEnd 死校验（wi5#2） | nop-wf-core | 补 isNextToAssigned 前置条件 | trip-wire errNotEndable 翻转 | ▲ nop-wf-service/_cases（testPublish 流）当期跑 |
| 19 | SysDictLoader 忽略 locale（wi7#2） | nop-sys-dao | 使用 locale 参数 | **无钉，新增** | — |
| 20 | ChangeLogInterceptor null 审计不分（wi7#4） | nop-sys-dao | null 值显式标记 | **无钉，新增** | — |
| 21 | GatewayHttpFilter null method NPE（wi8#1） | nop-gateway | null 守卫 | **无钉，新增** | — |
| 22 | saveOrUpdate 按 "id" 键判定（wi8#2） | nop-biz CrudBizModel:1436 | 按实体主键名判定；**同源点 batchSaveOrUpdate:1416 一并修**；:1429 反转的 i18n 文案裁定同步 | **无钉，新增**；既有 `TestGraphQLCrudSemantics.testSaveOrUpdateInsertsThenUpdates`（:136-151）按现行缺陷语义编写，**必改写**（更新调用改携 `sid`） | ▲ 公开 mutation 行为变化，下游 CRUD 消费模块当期跑 |
| 23 | UnitsHelper FixedPoint 负数不对称（wi9#1） | nop-excel | 统一符号语义 | **无钉，新增** | — |
| 24 | parseYYYYMMDDDate 无校验（wi9#2） | nop-excel | 分隔符+有效性校验 | **无钉，新增** | — |
| 25 | PredefinedColors 索引冲突（wi9#3） | nop-excel | 稳定注册语义（裁定 fail-fast 或首注册优先） | 特征化断言调整 | — |
| 26 | FLS 空值缺省不一致（wi9#4） | nop-record | 统一两路径缺省 | **无钉，新增** | — |
| 27 | RecordTemplateManager vars 无防御拷贝（wi9#5） | nop-record | 拷贝入可变 map | **无钉，新增** | — |
| 28 | 文本路径类型反推缺失（wi9#6） | nop-record | 按 stdDataType 转换 | **无钉，新增** | — |
| 29 | MultiRpcService 空映射未 fail-fast（wi11#2） | nop-rpc-core | Guard 判空 map | **无钉，新增** | — |

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 20 条目全部 `fixed` 或 `adjudicated-not-a-defect`（含理由）。——20/20 fixed
- [x] 回归测试到位；项 16 新错误码已注册（nop.err.orm.compute-prop-arg-missing，OrmModelErrors）；异常类型/消息变化项全 reactor 验证（项 13 IAE 契约，_cases 零命中成立）。
- [x] ▲ 项（18/22）下游当期验证绿。——nop-wf-service 115 / nop-biz 111 全绿
- [x] 各模块 `-am` 全绿。——19 模块当期全绿（明细见汇总文件验证证据节）
- [x] bug 文件回填，`ai-dev/logs/` 已更新。
- [x] Owner-doc 裁定：项 22 公开 mutation 语义变化核查 docs-for-ai/service-layer 描述一致性——记录结论。——核查：service-layer 文档"没有主键就新增，否则更新"与修复后行为一致，无需变更（记录于 wi8 bug 文件）

### Phase 3 - 确认后处置 9 条目（10 源条目，含设计裁定）

Status: completed

- Item Types: `Fix`（确认属缺陷）/ `Decision`（确认属设计或特性移除裁定）

| # | 项（源条目） | 处置路径 | 回归基座 |
|---|---|---|---|
| 30 | 低危三项（wi1#6：compareTo 懒加载/噪声键/缺右括号） | 缺右括号直接修；另两项确认后修或记录 | 渲染断言调整（括号项） |
| 31 | SetFunctions.concat/flatMap 标量分支（wi2#2+#3） | 全仓 grep 消费方确认后修 | **无钉，新增** |
| 32 | JavaToXLangTransformer 字段丢失（wi3#4，janino 3.1.12） | 反射补字段遍历最小修复；不可行 diagnosed-split | 特征化用例翻转 |
| 34 | JdbcTransactionFactory.openConnection 不挂事务（wi4#3） | **Decision**：修语义或文档告警（设计评审裁定记录） | 按裁定新增/调整 |
| 35 | detached 实体 ref 访问抛错（wi7#1：getDomain+getModule 同条目） | 区分"未设置"与"需懒加载" | **无钉，新增** |
| 36 | existsDict 租户旁路（wi7#3） | 运行期 DB 检查或显式上下文要求 | **无钉，新增** |
| 37 | @InjectValue 纯 JVM 默认值（wi8#3） | 字段初始化默认值或注释裁定（Decision） | 按裁定 |
| 38 | record-template 记录级 generator 死代码（wi9#7） | 修通或移除特性（Decision 记录） | 按裁定 |
| 39 | DashPatternDetector 滑窗锚定（wi10#6） | 中断式匹配或文档化（Decision） | 既有稳健语义断言复核 |

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 9 条目逐项落地 `fixed` / `adjudicated-not-a-defect` / `diagnosed-split`（后者指向新 plan）。——7 fixed + 2 adjudicated（wi4#3 文档告警、wi9#7 探针证伪）；0 diagnosed-split
- [x] 修复项回归测试绿；Decision 项裁定记录于 bug 文件。——wi4#3/wi9#7/wi10#6 裁定均在 bug 文件留痕
- [x] 各模块 `-am` 全绿。
- [x] bug 文件回填，`ai-dev/logs/` 已更新。
- [x] Owner-doc 裁定：项 34 若改公开语义，`docs-for-ai` 事务使用文档同步——记录结论。——公开语义未变（文档告警路线），无需同步；openConnection 契约说明已写入接口 javadoc

### Phase 4 - fraud 2PC 诊断修复与全程收口

Status: planned

- Item Types: `Fix`

- [ ] TestParallel2PcJdbcE2E 诊断：嫌疑 A（commit-key 未含 subtask 身份/提交归集）vs 嫌疑 B（本地拓扑退化为单 subtask，断言前提不成立）——按证据裁定。
- [ ] 按结论修复（connector-jdbc 提交路径或 E2E 拓扑/断言），该测试转绿。
- [ ] 全仓验证：`mvnq -- test -T 1C -fae` 零失败（roadmap 收口后首次全仓全绿基线；若暴露本 plan 外新问题，逐条记录并裁定处置）。
- [ ] 汇总落账：44 项最终状态表（按源条目口径：fixed/adjudicated-not-a-defect/diagnosed-split 计数）写入 bugs/ 汇总文件；`ai-dev/logs/` 收口条目。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] fraud 2PC 诊断结论有证据、按结论修复/分流。
- [ ] 全仓 `test -T 1C` 零失败（或失败项全部为本 plan 外新暴露并记录裁定）。
- [ ] 44 项最终状态表（源条目口径）落位。
- [ ] `ai-dev/logs/` 对应日期条目已更新。
- [ ] Owner-doc 裁定：若 fraud 修复改变 exactly-once 语义文档面，同步 `docs-for-ai` 对应模块文档——记录结论。

## Closure Gates

- [ ] 44 项（源条目口径）逐项落地三态之一，汇总表可机械核对，无静默跳过
- [ ] 全部 `fixed` 项有回归测试（翻转钉子/解除 @Disabled/新增），既有测试零回归（声明翻转的用例除外）
- [ ] 消费方爆炸半径评估记录（行为语义变化项）；▲ 项下游当期验证证据
- [ ] protected area 变更（nop-core/nop-xlang/nop-antlr4-common）均有本 plan 依据 + 回归测试
- [ ] 错误消息/错误码/异常类型变化项全 reactor 验证
- [ ] bug 文件全部回填 Fix/Tests 段（bug guide 合规）；wi3#5 回填 already-fixed 指向 df4e4f8fa7
- [ ] 不存在被静默降级的项（adjudicated/diagnosed-split 均有理由与去向）
- [ ] Anti-Hollow Check：修复是真语义修复而非改测试凑绿（audit 抽查钉住测试翻转前后 diff）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module <受影响模块> --severity high` 退出码 0（逐模块）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2306-bug-suspects-remediation.md --strict` 退出码 0
- [ ] `ai-dev/logs/` 收口条目与 plan 一致

## Revision Note

- rev2（2026-10-02）：对抗性审查（agent_96ab768c）修订——[Blocker] wi3#5 JsPromise 改判 already-fixed（df4e4f8fa7 已修）；[Major] 项 6 扩围（PIE 重复/Identifier 未定义/生成物脱节）+ 写明 xgen 重生成流程 + 声明 3 个必翻红用例 + 子缺陷 4 拆出为项 6b（nop-antlr4-common，repo 级爆炸半径，独立下游验证）；[Major] ▲ 行为变化项增下游当期验证义务（1/2/6b/18/22）；[Major] 增源条目↔项号映射表与口径声明（44 = 43 源条目 + fraud）；[Minor] 项 22 纳入 batchSaveOrUpdate:1416 同源点与 i18n 文案裁定、项 8 标注 OUT_OF_SERVICE 裁定点、各 Phase 补 owner-doc 裁定行、Closure Gates 补 scan-hollow、Phase 3 补 Decision 类型、回归基座列标注无钉项、Current Baseline 钉住口径修正、排除项显式声明。

## Deferred But Adjudicated

（执行结束时按实测填写——预期容器：diagnosed-split 项）

## Non-Blocking Follow-ups

- build-infra 三项（nop-kernel parent、nop-rg argLine、JDK profile 停用 activeByDefault）——独立立项。
- janino 升级评估（若项 32 最小修复不可行）。

## Closure

Status Note: （完成时填写）

Completed:

Closure Audit Evidence:

- Reviewer / Agent:（待独立子 agent closure audit 后填写）

Follow-up:

- （待填写）
