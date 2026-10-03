# WI15 Closure Audit——17-wi15-design-docs.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与实现者非同一 session；全部结论来自 live repo 实跑/实读，未采信 plan 勾选与日志自述）
- 裁定：**PASS**——四份义务文档齐备且各含 index 层与实现层分离、三份新文档结构纪律符合 design guide「必须有/不要有」清单、19 处 live 代码锚点抽查全部一致、WI12/WI13/WI17 设计依赖各有明确章节可指、契约族 index（contract §0）覆盖全部九份 SQL 子系统设计文档、门禁实跑全 0、plan Phase 1 勾选与日志条目与 live 实况一致。Minor 项（§3）均非阻塞。

## 1. 逐条审计核验（对应审计指令 1-7）

### 1.1 四份义务文档齐备性（审计项 1）——PASS

roadmap WI15 行（live :253）完成判定原文：「产出 SQL 编译契约与多输入模型与 join 算子与参数化面**四份设计文档**，各含 index 层与实现层分离且被 WI8a 与 WI12 与 WI13 与 WI17 引用」。语义判断：

- 「四份」列举的是**四个设计面**（编译契约/多输入模型/join 算子/参数化面），非「四次新建」动作。SQL 编译契约面由既有 `sql-compiler-contract.md`（D7/D8/D13 落档 + WI8c 回改注记）承载——该文档本身就是四者中唯一在本 WI 前已存在者，plan Current Baseline 明文「缺口（WI15 完成判定要求的四份中**两份半**）」即按此口径记账（contract 为既有一份，多输入/join 为新建两份，参数化面为新建汇总一份）。该读法与 WI15 的 Purpose 门控定位（不触门、以 Purpose 表为准，:57/:245）一致：WI15 是文档收拢工作项，不是裁定重做工作项。
- 若判定义务为「四份全部新建」则 contract/landing-decision 等既有裁定文档须重写，直接违反 plan guide 规则 14 与本 plan Non-Goals（「不重写既有裁定文档，仅补 index 节」）——两种读法只有一种可执行，故采前者。
- **结论**：四份义务文档 = `sql-compiler-contract.md`（既有，本 WI 补 §0 index）+ `multi-input-model.md` + `join-operator.md` + `parameterized-declarations.md`（三份新建）。全部实存于 `ai-dev/design/nop-stream/`，齐备成立。

### 1.2 结构纪律（审计项 2）——PASS

对照 `ai-dev/design/00-design-writing-guide.md`「必须有 1-3 / 可以有 4-5 / 不要有 6-8」逐条：

| 检查项 | multi-input-model | join-operator | parameterized-declarations |
|---|---|---|---|
| index 层头块（覆盖/读者/实现锚点） | :3-7 四行头块齐（含相关裁定链接） | :3-7 齐（锚点标注「现状」） | :3-7 齐 |
| 决策+理由+拒绝项 | §1.1 五行决策表含「拒绝的替代」列 | §1 定位 + §3/§4 形态裁定 + §7 评估项显式登记 | §1.1 落点决策（base vs delta，理由 + 实验证据指引） |
| 约束和边界 | §3 语义契约 + §4 义务归属表 | §2 消费契约 + §5 复用义务（不可另建）+ §6 待验证义务 | §2 共同契约 + §5 边界与义务归属表 |
| 无 Proposed-vs-Current | 无（:11「此前只支持单输入」为一句背景动机，guide 模板明列「背景与动机」节，非对比结构） | 无 | 无 |
| 无演进叙事 | 无（WI6 交付的最终状态描述） | 无 | 无（D13 回改以「落点决策」最终结论呈现，回改过程留档在 contract §3.2 被引用而非复述） |
| 无类签名展开 | 无——接口/方法名仅作契约表达（UnionTransformation.getInputs 语义、StreamUnionOperator 转发面），无字段/私有方法/实现步骤 | 无（validateJoinDeclarations 以八项校验义务列出，非代码） | 无（SPI 全序以行为步骤契约列出，非代码片段） |

引用纪律（guide「设计文档的引用约束」）：三份文档引用对象均为设计文档（sql-compiler-contract / sql-landing-decision / multi-input-model）、roadmap、plan——无 discussions/analysis 引用；源码引用全部为锚点定位用法。合规。

### 1.3 内容与 live 代码一致性抽查（审计项 3）——PASS（19 处锚点实测，远超 6 处下限）

**multi-input-model.md**：

| # | 文档断言 | live 证据 | 判定 |
|---|---|---|---|
| 1 | UnionTransformation：getInputs() 返回全部上游；outputType 取首输入 | `transformation/UnionTransformation.java` :60-62 getInputs 返回全列表；:46 `inputs.get(0).getOutputType()` | 一致 |
| 2 | StreamUnionOperator：record/watermark/watermarkStatus 全转发；processBarrier 继承基类；Shareable | `operators/StreamUnionOperator.java` :46-58 三转发方法；:60-61 注释明文继承快照协议；:41-43 isShareable=true + copyForSubtask 返回 this | 一致 |
| 3 | transformUnion：注册 unionIDs（链边界）、每输入各建一条 StreamEdge | `graph/StreamGraphGenerator.java` :317-344——`streamGraph.addUnionID(node.getId())` + for 循环每 input 一条 StreamEdge；注释明文 canChain 多入边不可链入 | 一致 |
| 4 | registerStreams：重复 `source->target` 键加 `#序号`（仅重复出现时） | 同文件 :169-186——`keyCounts.get(base) > 1 ? base + "#" + ordinal : base`，注释明文既有拓扑键形式不变 | 一致 |
| 5 | union 目标保留每声明边一条 JobEdge；非 union 目标顶点对去重 | `jobgraph/JobGraphGenerator.java` :561-584——`boolean unionTarget = streamGraph.isUnionNode(...); if (unionTarget \|\| createdEdges.add(edgeKey))`，注释明文链内扇入双写风险 | 一致 |
| 6 | 平行边 matrix 键控 IdentityHashMap | `execution/GraphExecutionPlan.java` :384-386——`new java.util.IdentityHashMap<>()` + 「WI6 constraint 2」注释明文 equals 相等须独立 matrix | 一致 |
| 7 | gate 配置一致性：declared-vs-declared 四字段不一致 fail-fast（ERR_STREAM_INVALID_ARG）；undeclared 让位 declared | 同文件 :566-596 + :607-615——`sameGateConfig` 逐值比较 flowControlPolicy/queueCapacity/receiveWindow/packetSize 四字段；mismatches 非空抛 ERR_STREAM_INVALID_ARG；注释明文 undeclared defers | 一致 |
| 8 | remote topic 消歧 `#序号`（仅重复对出现） | `nop-stream-runtime/.../RemoteGraphExecutionPlanBuilder.java` :199-216——pairCounts>1 时 edgeId 加 `#序号`，注释与文档同义 | 一致 |
| 9 | DataStream.union 校验非空数组与 null 元素 fail-fast | `datastream/DataStreamImpl.java` :158-180——两处 ERR_STREAM_INVALID_ARG（空数组/null 参数） | 一致 |
| 10 | HASH 边禁入 union（validateEdgeDeclarations fail-fast，指引 union 后 keyBy） | `flow/builder/StreamModelDslBuilder.java` :444-452——union 目标分支 ERR_STREAM_EDGE_HASH_REDUNDANT，ARG_DETAIL 明文「keyBy AFTER the union instead」 | 一致 |

**join-operator.md**：

| # | 文档断言 | live 证据 | 判定 |
|---|---|---|---|
| 11 | 八项构造期校验 + 恰 2 上游按声明边计数 | validateJoinDeclarations（WI8d audit 逐项钉住，:492-581）；本 audit 复核 :440-458 HASH 分支与 union/join 双目标并存形态实存 | 一致 |
| 12 | buildJoin 显式占位（NOT_IMPLEMENTED，WI13 边界） | `AdvancedTransforms.java` :112 分派 + :462-470 抛 ERR_STREAM_NOT_IMPLEMENTED，ARG_DETAIL 明文 WI13 | 一致 |
| 13 | JoinType.isOuter() 在算子分支消费（WI8d audit M-2 消费断言归 WI13） | `core/model/JoinType.java` :26-28 isOuter 实存，javadoc 明文 FULL 窗口 join 为 WI13 评估项——与文档 §3/§4 限制口径一致 | 一致 |
| 14 | joinKey 经 WI9 compileScalar 编译，join 不新建求值设施 | `nop-stream-sql/.../eval/StreamSqlExprCompiler.java` :57/:100 compileScalar 实存 | 一致 |
| 15 | A6 验证义务（TestEquiJoinParallelismInvariant 实测确认或推翻并回写 roadmap） | 与 roadmap WI13 行 :261 逐字同源（含测试名）——文档如实转写 roadmap 义务，无篡改 | 一致 |

**parameterized-declarations.md**：

| # | 文档断言 | live 证据 | 判定 |
|---|---|---|---|
| 16 | 三注册表 key-attr：schemas=id / aggregators=aggregatorId / joins=joinId | `nop-kernel/nop-xdefs/.../stream/stream.xdef` :91 / :61 / :72 逐一实读 | 一致 |
| 17 | SPI 全序：BeanContainer.isInitialized() 护栏 → tryGetBeanByType（缺失返回 null）→ null 即 fail-fast 点名 nop-stream-sql | `AdvancedTransforms.java` :403 `BeanContainer.isInitialized()` + :408 `.tryGetBeanByType(IAggregatorFunctionResolver.class)` | 一致 |
| 18 | 双 _module 布局：`_vfs/nop/stream-sql/_module`（两段发现入口）+ `_vfs/nop/stream/sql/_module`（moduleId 往返命中）+ `beans/app-*.beans.xml` | `nop-stream/nop-stream-sql/src/main/resources/` 下三路径实存，与文档 §4 布局图逐行一致 | 一致 |
| 19 | 九受管类型名闭集严格解析 BasicTypeInfo 九实例 | `flow/builder/StreamSchemaRegistry.java` :85-104 resolveManagedType 九分支（string/int/bigint/smallint/tinyint/float/double/boolean/bytes → STRING/INT/LONG/SHORT/BYTE/FLOAT/DOUBLE/BOOLEAN/BYTE_ARRAY） | 一致 |

另核：文档 §4 引用的自动装配证明测试 `TestStreamAggregatorFunctionResolver.resolverAutoAssemblesThroughModuleMechanism` 实存（sql 模块测试 :68，surefire 报告内为通过态）。

### 1.4 路由完整性（审计项 4）——PASS

- **WI12 依赖**（deps WI0b 与 WI10 与 WI15）：有序缓冲复用义务在 `join-operator.md` §5 复用义务表第一行——「每 key 有序缓冲（WI12 独立可测构件）→ window join 与 hash join 的双侧缓冲复用该构件，不另建排序缓冲」，并在 §7 与 WI12 清空语义对齐挂钩。roadmap WI13 行「必须复用 WI12 的每 key 有序缓冲构件而非另建」由此获得文档落点。§4 义务归属表另将多输入回归三件套归属 WI7。**可指**。
- **WI13 依赖**：`join-operator.md` 全档即 WI13 实现依据——§2 声明面消费契约（到达 buildJoin 时声明已合法、joinKey 经 compileScalar、无 keyBy）、§3/§4 双形态（hash join：union→keyBy→process + keyed state；window join：windowStrategyRef + timeout，限 INNER/LEFT，FULL 为评估项）、§6 A6 验证义务（TestEquiJoinParallelismInvariant + 回写 roadmap）。另 `multi-input-model.md` §1.1「union 后 keyBy + process 形态」与 §4 join 归属行构成 union 形态的文档锚。**可指**。
- **WI17 依赖**：`sql-compiler-contract.md` §0 文档族 index 实读——九行表格列出全部 SQL 子系统设计文档及覆盖面，表头明文「WI17 起草时按此路由」；三份新文档各占一行且覆盖面描述与文档内容吻合。`parameterized-declarations.md` §2 三注册表形状契约（WI17 产出消费声明面）+ §5 归属行「编译器产出这些声明面（SQL → stream.xml）| WI17」。roadmap WI17 行 :268 的聚合落 aggregatorRef / join 落 joinRef 完成判定在 §2 表中有逐字段承接。**可指**。

### 1.5 引用义务判断（审计项 5）——PASS（「产出可被引用的设计」语义成立）

roadmap「被 WI8a 与 WI12 与 WI13 与 WI17 引用」的达成形态判断：

- **死锁论证**：WI12 deps 显式含 WI15（:260），WI17 依赖 WI8b/c/d 的声明面（:268）——若要求 WI15 关闭前引用已物理发生，则 WI12/WI17 的 plan 起草必须先于 WI15 done，而其 deps 又等 WI15，构成循环。故完成判定的唯一自洽语义是**「产出可被引用的设计」**：文档存在、覆盖对应 WI 设计面、路由入口就绪；物理引用在 WI12/13/17 plan 起草时发生。plan Current Baseline :18 同款记账（「被引用的证明=文档存在且内容覆盖对应 WI 的设计面；WI12/13/17 起草时按此路由」）。
- **WI8a（done）**：引用已实际发生——`ai-dev/plans/nop-stream-sql/10-wi8a-landing-decision.md` :6/:17/:82 与 `sql-landing-decision.md` :8/:43 均实引 `sql-compiler-contract.md`，引用链闭合。
- **WI12/WI13/WI17（todo）**：§1.4 已证三者的设计依赖在新文档中各有明确章节可指，contract §0 提供统一路由入口。当前状态满足 WI15 关闭条件。

### 1.6 门禁（审计项 6）——PASS（实跑记录）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `node ai-dev/tools/check-doc-links.mjs --strict` | 0 | 0 errors / 3 warnings（均为 nop-bytecode 旧 plan 既存，与本 plan 无关） |
| `parseRoadmapMarkdown`（`tools/mission-driver/src/roadmap-check.mjs` 实跑） | — | **items 31 + milestones 7，done 16，progress 0.52，无静默丢弃**；WI15 仍 `todo`（预期——audit 通过前不翻转，本 audit 即 Phase 2 第一项） |
| scan-hollow | 无需 | 本 plan Item Types 全为 `Proof`，零代码变更（git status 实证：仅 3 份新设计文档 + contract §0 追加 + 日志 + plan，无任何 .java/.xml 产品代码改动）——无 hollow 面可扫 |

### 1.7 plan 文本一致性（审计项 7）——PASS

- Phase 1 五项勾选全部 live 落地：guide 已读且纪律符合（§1.2）；multi-input-model.md 实存且覆盖 WI6 全部裁定——五项关键决策表（真实顶点/不溯源/平行边每边一 JobEdge/IdentityHashMap/二输入上界 FU-6）与约束 3/4 处置逐项有代码锚（§1.3 #1-10）；join-operator.md 覆盖声明面消费 + 双形态 + 四项复用义务 + A6 + isOuter + FULL 评估项（§1.3 #11-15）；parameterized-declarations.md 覆盖三注册表 + SPI + 双 _module 布局 + D13 回改落点（§1.3 #16-19）；contract §0 实读为九行文档族路由表（plan 称「九份文档路由表」——实数 9 行，本档 + 8 份，含三份新文档，一致）。
- Exit Criteria 四项成立（index/实现分层无 Proposed 叙事 §1.2；锚点一致 §1.3；doc-links 0 §1.6；日志条目见下）。
- 日志条目（`ai-dev/logs/2026/10-02.md` 顶部「WI15 设计文档产出（Phase 1 完成，待收口审计）」）与实际交付逐项吻合：三份文档各自内容概括、contract §0、结构纪律自述、doc-links strict 0——无虚报，且如实标注「待收口审计」未预领 Phase 2 结果。
- Phase 2 未勾、Closure Gates 未勾属预期（本 audit 即其第一项）。
- git 状态：改动面 = contract §0 追加（M）、日志（M）、三份新文档 + plan 17（untracked）——零 `_` 前缀生成物触碰、零产品代码改动，与纯文档 plan 的 Scope 一致。未提交属预期：既往 WI 收口惯例是 roadmap 翻转后随完成判定括注一并提交（对照 e53d0f5880 等 5 条 WI 提交），Phase 2 剩余动作含该提交点。

## 2. 实跑证据汇总

- `node ai-dev/tools/check-doc-links.mjs --strict`：exit 0（0 errors / 3 既存 warnings）
- `parseRoadmapMarkdown`（roadmap-check.mjs 模块实调）：31 items + 7 milestones，done 16，progress 0.52；WI15/WI12/WI13/WI17 全 todo（预期态）
- git status 全量实读：4 个文档/plan 文件 + 1 日志改动，零代码、零 `_` 前缀文件
- 19 处代码锚点逐一实读（§1.3 表），无一处与文档断言冲突

## 3. Minor 发现（均非阻塞）

- **M-1**：`ai-dev/design/nop-stream/README.md`（attractor index，Updated 2026-09-04）未收录 SQL 子系统设计文档族（sql-compiler-contract/sql-landing-decision/三份新文档等均不在其文档清单）。此为 WI0a 以来多份 SQL 文档的**既存缺口**，非本 WI 引入；本 WI 已以 contract §0 作为该族的权威 index，plan 亦未把 README 更新列入 Scope。建议（可选）：WI17 起草前或 doc 维护任务中给 README 补 SQL 族小节指向 contract §0，避免两个 index 并存时路由漂移。
- **M-2**：join-operator.md §6 将「双侧缓冲 keyed state 描述符（namespace 划分 left/right）」留待实现时确定、只约束「keyed state + 可 checkpoint」——与 roadmap WI13 完成判定的证据义务（TestE2EWindowOperatorWithCheckpoint 同级用例）相容，但实现者须注意 §5 复用义务表的 checkpoint 证据行在 WI13 而非 WI12（WI12 的证据归 TestAnalysisWindowEventTime 链）。文档已用两张表分列归属，无歧义；仅作阅读提示。
- **M-3**：multi-input-model.md §2 提到「transformUnion……链边界——canChain 的多入边判据天然保证不可链入」与 `multi-input` 语义严格对齐（StreamGraphGenerator :329-330 注释），但「canChain 判据」的具体机制归 `graph-model-design.md` 承载，本文未交叉链接该档。可选改进：§2 补一行 `graph-model-design.md` 引用。不阻塞。

## 4. 无静默跳过检查

纯文档 plan：三份新文档 + contract §0 追加 + 日志条目全部落盘实读确认；contract 既有八节（§1-§3.2）内容未被改写（diff 仅新增 §0 一节，标题位置在既有头块之后、§1 之前，未扰动裁定正文）；三份文档中登记的未决项（FULL 窗口 join、缓冲清理策略、FU-6、FU-3 残余）均为显式登记而非静默省略。

## 5. 结论

WI15 的四份义务文档（contract 既有 + 三份新增）齐备且 index/实现分层成立；三份新文档结构纪律逐条符合 design guide；19 处 live 代码锚点抽查零冲突；WI12/WI13/WI17 的设计依赖各有明确章节可指且 contract §0 提供路由入口；「被引用」义务按唯一自洽语义（产出可被引用的设计）判定达成，WI8a 侧引用链已实际闭合；门禁实跑全 0；plan 勾选、日志与 git 状态三者一致。

**裁定 PASS**：本 audit 构成 plan Phase 2 第一项证据。剩余收口动作（实现者执行，非本 audit 范围）：① roadmap WI15 `todo` → `done`（括注单层无嵌套）+ `parseRoadmapMarkdown` 复核 31+7；② plan Status → `completed` + Closure 段落与本 audit 证据回填；③ `check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/17-wi15-design-docs.md --strict` 与 `check-doc-links.mjs --strict` 复核退出码 0；④ 随收口提交本批文档（对齐既往 WI 提交惯例）。M-1 建议作为可选 doc 维护项记账，M-2/M-3 仅为记录，无动作义务。
