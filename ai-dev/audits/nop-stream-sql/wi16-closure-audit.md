# WI16 Closure Audit——19-wi16-subset-confirmation.md

- Audit 日期：2026-10-02（首轮 FAIL；同日修正复核后改判）
- Auditor：独立子 agent（fresh session，与实现者非同一 session；全部结论来自 live repo 实跑/实读，未采信 plan 勾选与日志自述）
- 裁定：**PASS（首轮 FAIL 1 Major，M-1/M-2/M-3 修正后复核通过，见 §6）**——§4a 九项 fail-fast 指认逐项实读全部成立、§4b 九行 live 对照成立（M-1 修正后 TUMBLE 行如实标注「grammar 变更尚未落地」+ TestDialectWindowSqlSnapshot 锚点）、§4c 与 §3 D4 逐句一致、§4d 抽验 9 行码串全部与钉码测试实测一致且零新增码（git diff 仅 2 个 md 文件 + 1 个新 plan）、D5 六排除项全覆盖、门禁全过（doc-links 0 / roadmap 解析 31+7 / 零代码变更实证）。首轮唯一 Major 为 §4b 行 5「TUMBLE(t, INTERVAL) 伪表函数（D4 语法面，WI1 grammar）」对 live 状态归属失实——WI1 grammar 交付的是 W1 四能力，TUMBLE/HOP/SESSION 伪表函数尚未进入 EQL 语法层（`TestDialectWindowSqlSnapshot.java:30` 明文注记 + DMLStatement.g4 表源仅三分支）；已按 §6.1 核实修正。

## 1. 逐条审计核验（对应审计指令 1-8）

### 1.1 §4a 不支持清单八项逐项对照（审计项 1）——7 项 PASS，intro 概括略强（M-5）

| # | 不支持项 | live 核验结果 |
|---|---|---|
| 1 | 全局 ORDER BY / LIMIT | 指认 roadmap §3.1 行实读成立（「无 sort / TopN 设施 ❌ D5 排除」）；`grep -ril "TopN|sortPartition|orderBy"` 对 nop-stream-core 主代码**零命中**，引擎无排序设施属实。fail-fast 形态明写「WI17 落地时报」——诚实的未来态标注，非当前代码路径（见 M-5） |
| 2 | 非等值 / 范围 join | `StreamModelDslBuilder.validateJoinDeclarations`（:492）实读：:522 「requires non-empty leftKeyExprs and rightKeyExprs (equi-join)」、:531 「equal arity」、:541 「exactly two upstream」——仅等值键集属实；`TestParameterizedJoinModel.keyCountMismatchFailsFast`（:132-139）实读，钉码 `nop.err.stream.invalid-arg` 一致 |
| 3 | retract / CDC | `StreamReduceOperator.processElement`（:87-106 实读）逐条 emit 当前归约值（首值直通、后续 `userFunction.reduce` 覆盖）——last-value-wins 属实；design doc §1 为既有落档 |
| 4 | Flink SQL 方言兼容 | roadmap Non-Goals 行（:29）实读含「Flink SQL 方言兼容」——边界声明属实 |
| 5 | DATE/TIMESTAMP/DECIMAL | `TestStreamSchemaConsumer` 开文件实读：九名 identity golden（:90-107，string/int/bigint/smallint/tinyint/float/double/boolean/bytes——九类闭集，无 DATE/TIMESTAMP/DECIMAL）+ `testUnknownTypeFailsFastWithFieldAnchored`（:110-122）对 `varchar2` 钉码 `nop.err.stream.invalid-arg` 且 detail 锚 schema/field/type——一致 |
| 6 | DISTINCT 聚合 | `TestStreamSqlAggregations.distinctFailsFast`（:119-125）实读：`spec.withDistinct(true).create(arg)` 抛 StreamException、`assertEquals("nop.err.stream.invalid-arg", e.getErrorCode())`——指认的 API 形态与钉码逐字一致 |
| 7 | 标量子集外表达式 | `StreamSqlExprCompiler.compileScalar`（:61-92 实读）：14 个 AST kind 白名单分派，default 分支 `throw RecordColumnAccess.unsupported(expr)`；`RecordColumnAccess.unsupported`（:153-159 实读）抛 `ERR_STREAM_INVALID_ARG` 且 detail 含 AST 节点 `getClass().getSimpleName()`——「分派默认分支抛 invalid-arg（含 AST 节点类型）」逐点属实。`TestStreamSqlExprCompiler.outOfSubsetFailsFastWithStreamSideCode`（:128-141）实读钉函数调用（`concat`，钉码断言）/CASE/CAST/标量位聚合/null AST 五形态 |
| 8 | CEP 级复杂编排 | roadmap Rules 边界行实读：「CEP、连接器、HA、部署编排不在本 roadmap 内」——属实 |

八项指认无虚指。观察 M-5/M-6 见 §3。

### 1.2 §4b 纳入面九行对照（审计项 2）——八行成立，行 5 Major（M-1）

| 行 | live 核验结果 |
|---|---|
| SELECT 投影 | `TestStreamSqlExprCompiler` 全读：限定名列（:51 `t.amount` 取末段）/字面量（:61-67）/算术 + - * % 与乘法优先（:70-76）/**除法恒 double**（:79-82 + 编译器 `DIVIDE→arith(..., forceDouble=true)` :247-248、:353-356）/比较（:85-91）/AND-OR-NOT 短路（:94-101）/IS NULL（:104-108）/BETWEEN + IN 值列表（:111-118）——九类逐项成立 |
| WHERE | stream.xdef :169 `<filter>` 实读——既有通路属实 |
| 聚合 | `StreamSqlAggregations`（:26-34 实读）恰五 id；钉子测试 `TestStreamAggregatorFunctionResolver.builtinIdsAlignWithGrammarKeywords`（:81-86）断言 `builtinIds()=={sum,count,avg,min,max}`，注释引 BaseRule.g4——live 核对 `sqlIdentifier_agg_ : MAX | MIN | SUM | COUNT | AVG`（BaseRule.g4:272-273 实读）**对齐成立**；COUNT(*) 无参：grammar :219 `selectAll=ASTERISK_` + 测试 :87-93/:127-132 |
| GROUP BY | keyBy（stream.xdef :174 `keyExpr="!expr"`）；WI8c aggregatorRef 已交付；「或持续聚合（WI11）」为向前引用（WI11 todo）——design doc 允许含未实现目标架构，且未声称已交付（m-7） |
| 流时间窗口 | **M-1（Major）**：行文「TUMBLE(t, INTERVAL) 伪表函数（D4 语法面，WI1 grammar）」的 WI1 归属失实。live 三重证据：① `DMLStatement.g4:145-148` `sqlTableSource` 仅 sqlSingleTableSource/sqlSubqueryTableSource/sqlJoinTableSource 三分支，`sqlSingleTableSource`（:151-153）仅表名+别名——**无伪表函数变体**；② TUMBLE 残迹为注释（:216-218 `// window_type ::= TUMBLING...`）；③ WI1 交付面为其 plan :148 明文「四能力（单子句/空参/frame 三单位/命名窗口）」= W1 分析窗口，无 TUMBLE；④ `TestDialectWindowSqlSnapshot.java:30` 明文「**W2 时间切片窗口（TUMBLE/HOP/SESSION 伪表函数）尚未进入 EQL 语法层**，无翻译 golden」。§3 D4 裁定的「语法落 DMLStatement.g4 表源分支（新增伪表函数变体）」是**待执行落点**，其受影响 WI 中「WI1（若伪表函数语法随窗口 grammar 一并直增）」的条件分支**未触发**——TUMBLE 语法面的 grammar 变更当前无 WI 承接。行内「TUMBLE(t, INTERVAL) 伪表函数（D4 语法面）+ 三档（见 4c）」的裁定实质成立，「WI1 grammar」四字为对已完成工作的错误归属 |
| 双流等值 join | WI8d 声明面成立（validateJoinDeclarations 全套 + TestParameterizedJoinModel 13 用例）；「+ WI13 运行时」向前引用且 §4d not-implemented 行诚实标注占位（m-7）；join-operator.md 实存 |
| 静态维表 lookup | `ITableLookup` 实存（nop-stream-core connector/lookup/，WI14 已收口）；IJdbcTemplate/IBatchLoader 桥接口径与 WI14 audit 一致 |
| union 多流 | `DataStream.union(DataStream<T>... streams)`（:157 实读，javadoc 明文 WI6 pass-through + union 后 keyBy）；multi-input-model.md 实存 |
| 窗口声明 | stream.xdef :48-55 `windowingStrategies`/`strategy duration`/`allowedLateness` 实读——WI10 duration 参数化与 D9 放行的声明面成立 |

### 1.3 §4c W2 三档与 §3 D4 一致性（审计项 3）——PASS

逐句比对：T1「直通产物必须带 stream-only 标注」= §3「仅流目标，产物标注 stream-only」；外部事实未验证的标注口径两侧一致。T2「仓库内唯一可证映射为 oracle date→trunc」= §3「oracle.dialect.xml:184-186 date→trunc 唯一可证」；「以 WI3 实跑矩阵为准（sql-window-dialect-matrix.md）」是对 §3「以 WI3 实跑产出为准」的落地化引用（矩阵文档实存，WI3 已 done）——属确认非改判。T3「拒绝并给替代建议」细化为「给 T1 降级建议（stream-only 执行）」——roadmap WI16 完成判定明文允许「确认或微调」，且不与 §3 冲突，成立。

### 1.4 §4d 错误码表抽验（审计项 4）——9 行抽验全部钉码一致；两处引用瑕疵（M-2/M-3/m-4）

抽验行数超出要求的 5 行，全部开测试文件核对 assert 的码串：

| 行 | 抽验证据 |
|---|---|
| invalid-arg（声明面） | `TestAdvancedTransforms.aggregateWithoutBeanOrRefFailsFast`（:132-149）+ `aggregateWithBothBeanAndRefFailsFast`（:155-172）「恰一两用例」成立；`unionWithoutUpstreamFailsFast`（:476-482）钉码一致；`TestParameterizedJoinModel` invalid-arg 断言实为 **7** 用例（:112/:122/:132/:142/:179/:193/:203）——「六用例」与所列六触发一致但漏列 `fullWindowJoinFailsFast`（joinType FULL 排除，:179-190，M-3） |
| ref-unknown | `aggregateUnknownAggregatorRefFailsFast`（:203-219 钉码一致）；`TestParameterizedJoinModel.unknownJoinRefFailsFast`（:102）+ `unknownWindowStrategyRefFailsFast`（:168）两用例成立（类内另有第三处 ref-unknown 断言 :240 为校验顺序用例，触发语义不同，m-4） |
| upstream-type | `unionRejectsKeyedUpstream`（:488-498）+ 既有 `windowRejectsNonKeyedUpstream`（:88-99）钉码一致 |
| edge-hash-redundant | `hashEdgeIntoUnionFailsFast`（:504-517）+ `hashEdgeIntoJoinFailsFast`（:154-165）钉码一致 |
| not-implemented | `selfJoinTopologyWalksValidationToRuntimePlaceholder`（:216-237，消息含 "WI13"）+ `sideOutputTransformThrowsWithRuntimeGapMessage`（:523-532）钉码一致 |
| invalid-arg（编译期） | `TestStreamSqlExprCompiler`（:57/:131 钉码）、`TestStreamSqlAggregations.distinctFailsFast`、`TestStreamSchemaConsumer`（:117/:132）、`TestMultiEdgeGateConfigConsistency.testConflictingConfigsFailFast`（:116-124 钉 `ERR_STREAM_INVALID_ARG`）、`aggregateRefWithoutProviderFailsFastNamingDependency`（:178-197，消息含 "nop-stream-sql"）——「多入边 gate 配置冲突」「无 resolver provider」两触发抽验成立。**M-2**：「聚合 fnId 未知/expr 编译失败/参数个数/参数类型」四触发的实际钉码测试是 `TestStreamAggregatorFunctionResolver`（:89/:98/:107/:127，四用例全钉 invalid-arg，实读），未列入该行钉码测试列；**M-2 附**：所引方法名 `TestStreamSqlExprCompiler.outOfSubsetFailsFast` 不存在，实际为 `outOfSubsetFailsFastWithStreamSideCode`（§4a#7 用的全名是对的） |
| dialect-not-support-feature（运行期） | 码串实读 `OrmEqlErrors.java:195` = `nop.err.eql.dialect-not-support-feature`；消费点 `EqlTransformVisitor`（compile 包）实存；`TestEqlCompileSql` :384/:473/:495/:555 四处断言实读 |
| invalid-state / job-execute-failed（运行期） | 两码串在 NopStreamErrors 实存（:51/:153），「既有执行期错误面」定位诚实 |

**零新增码核实**：`git status --porcelain` 全量实读——tracked 改动仅 `ai-dev/design/nop-stream/sql-subset-and-semantics.md` 与 `ai-dev/logs/2026/10-02.md` 两个 md，untracked 仅本 plan；`git diff --stat` 无任何 .java/.g4/.xdef 变更——NopStreamErrors.java 与 OrmEqlErrors.java 零改动，「零新增码」声明成立。NopStreamErrors 五码定义行（:72/:600/:605/:608/:627）实读与码表码串吻合（测试断言的码串即证）。

### 1.5 D5 排除项覆盖确认（审计项 5）——PASS

roadmap 前置裁定 D5 行 + design doc §4 草案六项：全局 ORDER BY/LIMIT、非等值/范围 join、CDC retract 全链路、Flink SQL 方言兼容、DATE/TIMESTAMP/DECIMAL 列绑定、CEP 级复杂编排——**逐一映射到 §4a #1/#2/#3/#4/#5/#8，全覆盖**。§4a 另两项（#6 DISTINCT、#7 标量子集外）为 plan Goals 明文的「实现确认的 fail-fast 面」追加项，有 WI9 钉码测试背书——追加合法。措辞观察：plan Exit Criteria 把 DISTINCT/子集外一并写入「D5 排除项全覆盖」括号（严格说超出 D5 草案六项），实质覆盖无缺口（m-8）。

### 1.6 结构纪律（审计项 6）——PASS（一处记录）

新增四节均为最终状态表（清单/总表/三档/错误码表），无 Proposed vs Current 对比、无多轮演进叙事；§4a 引语「WI0b 落档的清单草案经 WI16 对照 live 实现定稿」为单句出处交代（design guide 规则 14 禁的是演进叙事章节，非出处标注；且 D5 §4 原行「定稿归 WI16」为既有裁定文本），记录为合规。§4d 按「声明面/编译期/运行期」三段分组与 plan Goals 第 4 点结构一致。

### 1.7 门禁（审计项 7）——PASS（实跑记录）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `node ai-dev/tools/check-doc-links.mjs --strict` | 0 | 0 errors / 3 warnings（均为 nop-bytecode 旧 plan 既存，与本 WI 无关；日志「doc-links strict 0」声称属实） |
| `parseRoadmapMarkdown`（tools/mission-driver/src/roadmap-check.mjs 实调） | — | **工作项 31 + 里程碑 7**，31 名唯一无丢弃，done 18 / todo 13，progress 0.58；**WI16 仍 `todo`（预期态**——翻转在 audit 通过后的 Phase 2）；WI14 = done |
| `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/19-...md --strict` | 0（warnings only） | 非 completed plan，Phase 2/Closure Gates 13 项未勾 + Closure 占位符——符合收口前预期态（备案） |
| 零代码变更 | — | `git status --porcelain`：`M sql-subset-and-semantics.md`、`M logs/2026/10-02.md`、`?? plans/.../19-....md`，**无任何代码/生成物 diff**；`_gen/`、`_*.xml` 零触碰 |

### 1.8 plan 文本一致性与日志（审计项 8）——Phase 1 勾选一项被本 audit 推翻其一

- Phase 1 三项勾选：「逐项核对 live 代码：每项有代码或测试指认」——**因 §4b 行 5「WI1 grammar」归属失实而不能全称成立**（TUMBLE 行无 live 语法面指认；这正是 M-1）；「定稿落档四节」与「日志更新」两项成立（四节实存、日志 WI16 条目实读）。
- Exit Criteria 五项勾选：「清单每项有 live 指认（文档内锚点）」同上受 M-1 影响；「D5 排除项全覆盖 + 三档确认」「错误码表三段完整且与钉码测试一致（零新增码）」「doc-links 退出码 0」「日志已更新」四项实跑成立。
- 日志 `ai-dev/logs/2026/10-02.md` WI16 条目实读：四节内容概括、零代码变更、doc-links 0 三项声称均属实；条目未发现虚报，但也未暴露 M-1（实现者未察觉，与「逐项核对」勾选矛盾——即 M-1 的过程根源）。
- plan Non-Goals「不新增/修改任何代码；不新增错误码；不改 EQL grammar」与 live 完全一致（git 实证）。

## 2. 实跑证据汇总

- 门禁：doc-links --strict **exit 0**（0 errors / 3 既存 warnings）；`parseRoadmapMarkdown` **31 + 7**（唯一名 31，done 18，WI16 todo 预期态）；plan-checklist --strict exit 0（warnings only 备案）
- git：diff 全量 = 2 md + 1 新 plan，零代码变更，NopStreamErrors/OrmEqlErrors 零改动
- 测试未实跑（本 WI 零代码变更，钉码测试均为 WI9/8c/8d/WI6/WI14 既有收口测试，其绿态由各 WI 收口审计与全量实跑背书；本 audit 以开文件核对 assert 码串为验证手段）

## 3. 发现清单

- **M-1（Major，首轮阻塞收口——已修正，复核见 §6.1）**：§4b 行 5「TUMBLE(t, INTERVAL) 伪表函数（D4 语法面，**WI1 grammar**）」。live 实证 TUMBLE/HOP/SESSION **尚未进入 EQL 语法层**（`TestDialectWindowSqlSnapshot.java:30` 明文；`DMLStatement.g4:145-153` 表源三分支无伪表函数变体；WI1 plan :148 四能力 = W1 分析窗口）。D4 §3 的语法落点为**待执行裁定**，受影响 WI 的 WI1 条件分支未触发，该 grammar 变更当前无 WI 承接。修正建议（二选一或并用）：① 行内改为「TUMBLE(t, INTERVAL) 伪表函数（**D4 已裁语法面；grammar 变更尚未落地**——落点 DMLStatement.g4 表源分支，见 §3；现状锚点 `TestDialectWindowSqlSnapshot.java:30`「尚未进入语法层」注记）」，并注明 grammar 落地归属需在 WI17 前指派（WI17 依赖边或 roadmap 补记）；② 同步在该行「依据」列把「WI1 grammar」改为「§3 D4 裁定」。修正后重跑 check-doc-links --strict 即可复核。
- **M-2（Minor，已修正，复核见 §6.2）**：§4d 编译期行引用测试方法名 `TestStreamSqlExprCompiler.outOfSubsetFailsFast` 不存在（实际 `outOfSubsetFailsFastWithStreamSideCode`，§4a#7 引用正确）；且「聚合 fnId 未知/expr 编译失败/参数个数/参数类型」四触发的钉码测试 `TestStreamAggregatorFunctionResolver`（:89/:98/:107/:127）未列入钉码测试列。
- **M-3（Minor，已修正，复核见 §6.3）**：§4d 声明面 invalid-arg 行漏列 joinType FULL 排除触发（`fullWindowJoinFailsFast` :179-190，builder :565 「supports joinType INNER/LEFT only」）——「六用例」计数与所列六触发自洽，但作为全量对账表宜补第 7 用例与该触发。
- **M-5（Minor，措辞）**：§4a 引语「每一项都有当前的显式失败路径」对 #1（WI17 未来 fail-fast）/ #4 / #8（roadmap 边界声明）概括略强；表内各行自身表述诚实（#1 明写「WI17 落地时」），引语可改为「除边界项外每项均有显式失败路径或明示的 fail-fast 落点」。
- **M-6（Minor，记录）**：§4a#7 触发清单中「IN 子查询/参数标记」两形态无独立用例（由分派 default 分支构造性保证——任何非 14 白名单 AST kind 均入 default；测试钉了函数调用/CASE/CAST/标量位聚合/null AST 五形态）。零代码变更约束下不要求补测，记录即可。
- **m-4/m-7/m-8（记录）**：ref-unknown「两用例」与类内第三处 ref-unknown 断言（校验顺序用例）之别；§4b 行 4/行 6 对 WI11/WI13（todo）为向前引用——design doc 时态允许含未实现目标架构，且 join 运行时占位在 §4d not-implemented 行诚实标注，与 M-1「对已完成工作的错误归属」性质不同；plan Exit Criteria 括号把 DISTINCT/子集外计入「D5 排除项」为措辞不精（覆盖实质无缺口）。

## 4. 无静默跳过检查

本 WI 零代码变更（git 实证），无新增方法/分支，hollow/silent-noop 维度不适用。设计文档侧的「静默跳过」等价物——清单项无指认——已逐项检查：§4a 八项、§4d 全部码行均有实存指认；唯一无 live 指认处即 §4b 行 5 的 grammar 半句（M-1），已在裁定中列明。

## 5. 结论

WI16 的实质交付成立：不支持清单九项（修正后）的 fail-fast 指认逐项实读真实、纳入面九行 live 对照成立、W2 三档与 D4 逐句一致、错误码表抽验 9 行全部与钉码测试实测一致且零新增码（git 仅 2 md + 1 plan）、D5 六排除项全覆盖、四节为最终状态描述、门禁全过（doc-links 0 / roadmap 31+7 / 零代码变更实证）。

首轮唯一 Major（§4b 行 5「WI1 grammar」归属失实）连同 M-2/M-3 两处引用瑕疵已由实现者修正并经本 agent 复核（§6）——**改判 PASS**。剩余收口动作（实现者执行，非本 audit 范围）：① roadmap WI16 `todo` → `done`（括注单层圆括号一对，注意 BULLET_RE 只允许单层非嵌套）+ `parseRoadmapMarkdown` 复核 31+7；② plan Phase 2 勾选、Status → `completed`、Closure 段落回填本 audit 证据（含首轮 FAIL → 修正 → PASS 轨迹）；③ 当日日志补一条审计修正记录（现条目仍写「§4a 八项」，修正后为九项——非时点性虚报，但收口时宜同步，避免审计链数字漂移，参照 WI14 M-1 先例）；④ `check-plan-checklist --strict` 与 `check-doc-links --strict` 复核退出码 0。M-5/M-6/m-4/m-7/m-8 仅为记录，无独立动作义务。

## 6. 复核记录（2026-10-02 第二轮，M-1/M-2/M-3 修正验证）

全部结论来自修正后 live 文件实读 + 门禁重跑：

### 6.1 M-1（§4b 行 5）——验证通过

修正后行文实读：「TUMBLE(t, INTERVAL) 伪表函数——**D4 已裁语法面（DMLStatement.g4 表源分支），grammar 变更尚未落地**（现状锚点：DMLStatement.g4 表源仅三分支、TUMBLE 仅注释残迹；W1 分析窗口四能力由 WI1 交付）需随 WI17 编译器接线前补 grammar 落地项；RDBMS 目标 T1/T2/T3 三档（见 4c）」，依据列改「sql-subset-and-semantics §3；**TestDialectWindowSqlSnapshot 注记**」。逐点核对：① 虚假归属「WI1 grammar」已移除，且 WI1 交付面被正确表述为 W1 四能力（与 WI1 plan :148 一致）；② 「尚未落地」现状与三重 live 证据（DMLStatement.g4:145-153 三分支、:216-218 注释残迹、TestDialectWindowSqlSnapshot:30）吻合；③ 「需随 WI17 编译器接线前补 grammar 落地项」明确了承接缺口，后续 WI17 起草者不再被误导。首轮 Major 的两个危害点（错误归属 + 语法面前置误判）均消除。

### 6.2 M-2（§4d 编译期行）——验证通过

实读修正后行：方法名改为 `TestStreamSqlExprCompiler.outOfSubsetFailsFastWithStreamSideCode`（与 §4a#7 全名一致，live 实存于 :128）；补列 `TestStreamAggregatorFunctionResolver（unknownFnIdFailsFast/exprCompileFailureFailsFast/missingArgumentOnNonCountFailsFast/sumOfNonNumericColumnFailsFastWithSchema）`——四个方法名与 live 测试逐一核对（:89/:98/:107/:127），四用例均钉 `nop.err.stream.invalid-arg`（首轮已实读断言体）。「聚合 fnId 未知/expr 编译失败/参数个数/参数类型」四触发现各有具名钉码指认。

### 6.3 M-3（§4a 新增第 8 行）——验证通过

新增 §4a#8「FULL 窗口 join（窗口补齐语义）」，原 #8 CEP 顺延 #9（实读确认顺延无遗漏，清单现为九项）。新增行的三个断言逐一 live 核对：① fail-fast 形态「窗口 join 的 joinType 限 INNER/LEFT，`nop.err.stream.invalid-arg`」——builder `validateJoinDeclarations` :556-566 实读，限制分支嵌套于 `spec.getWindowStrategyRef() != null` 之内，错误消息明文 "window join ... supports joinType INNER/LEFT only; FULL window completion is a WI13 evaluation item"；② 括注「FULL 非 window 补齐属 WI13 评估项」与上述错误消息逐字吻合；③ 「非窗口 FULL join 不受限」——代码结构证实：无 windowStrategyRef 的 joinSpec 完全绕过 INNER/LEFT 检查（else-if 分支仅拒绝 timeout-without-window）。「指认」列 `TestParameterizedJoinModel.fullWindowJoinFailsFast` 实存（:179-190，钉码 invalid-arg、消息含 "INNER/LEFT"）。§4d 声明面 invalid-arg 行的「六用例」与自身触发列保持自洽，FULL 排除现由 §4a#8 承载——首轮指出的对账缺口闭合。

### 6.4 门禁重跑

| 命令 | 退出码 | 结果 |
|---|---|---|
| `node ai-dev/tools/check-doc-links.mjs --strict` | 0 | 0 errors / 3 既存 warnings（nop-bytecode 旧 plan，与本 WI 无关） |
| `parseRoadmapMarkdown`（实调） | — | 31 工作项 + 7 里程碑、31 名唯一、WI16 仍 `todo`（预期态） |
| `git status --porcelain` | — | 仍为零代码变更：`M sql-subset-and-semantics.md`、`M logs/2026/10-02.md`、untracked 本 audit + plan；修订轮未触碰任何 .java/.g4/.xdef |

观察（非阻塞，归 Phase 2 收口顺手项）：当日日志 WI16 条目仍写「§4a 不支持清单（八项…）」与「§4b …九能力行」——条目撰写时点准确（首轮交付确为八项），修正后清单为九项，日志尚未补记审计修正轮；收口翻转 roadmap 时按 §5 ③ 同步即可。
