# WI8d Closure Audit——16-wi8d-parameterized-join.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与实现者非同一 session；全部结论来自 live repo 实跑/实读，未采信 plan 勾选与日志自述）
- 裁定：**PASS**——声明面（base stream.xdef joins 注册表 + join 元素）、typed 模型重生成、八项构造期校验（逐项具名测试 + 码串钉住）、buildJoin 显式 NOT_IMPLEMENTED 占位（非 hollow）、self-join 形态用例（执行期边计数修正为真）、两模块实跑（flow 153 + core 1663）、文档四处注记、工具门禁全 0、roadmap 解析 31+7 全部成立。Minor 项（§3）均非阻塞。

## 1. 逐条审计核验（对应审计指令 1-9）

### 1.1 xdef 声明面（审计项 1）——PASS

- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef` 实读：
  - 顶层 `<joins xdef:key-attr="joinId" xdef:body-type="list">`（:72-80），entry `<joinSpec>` 携带五项字段——`joinId="!string"`、`joinType="enum:io.nop.stream.core.model.JoinType"`（可选声明，构建期强制）、`leftKeyExprs="string"`、`rightKeyExprs="string"`、`windowStrategyRef="string"`、`timeout="string"`——外加 `<description>`，`xdef:name="StreamJoinSpecModel"` + `xdef:unique-attr="joinId"`；与 `<windowingStrategies>`（:48-55）、`<aggregators>`（:61-66）两个既有注册表逐属性同构，注释明文「运行时求值由 WI13 的 buildJoin 承接」。
  - `<transforms>` 内 `<join joinRef="!string" xdef:name="StreamJoinModel" xdef:ref="StreamTransformModel"/>`（:189）——joinRef 必填（`!`），对齐 `window@strategyRef` 先例；`xdef:ref` 复用共享 StreamTransformModel（id/type/parallelism 等）。
- `_gen` 重生成特征（git diff + live 实读，非手改痕迹）：
  - `_StreamJoinSpecModel.java`（新增）：javadoc `generate from /nop/schema/stream/stream.xdef`；字段 description/joinId/joinType（类型为 `io.nop.stream.core.model.JoinType`）/leftKeyExprs/rightKeyExprs/timeout/windowStrategyRef 逐一携带 `xml name:` 注释、getter/setter/outputJson/copyTo 齐备——与 xdef entry 属性一一对应。
  - `_StreamJoinModel.java`（新增）：`extends io.nop.stream.flow.model.StreamTransformModel`，字段 joinRef（+ 继承面的 type），同款 codegen 形态。
  - `_StreamModel.java`（修改，diff 62 行全为 `+`）：新增 `KeyedList<StreamJoinSpecModel> _joins` + `getJoins()/setJoins()` + **key-attr 查找器 `getJoinSpec(String name)`/`hasJoinSpec`/`addJoinSpec`/`keySet_joins`**，javadoc 原样携带 xdef 新增的 WI8d 中文注释——与 windowingStrategies 的 `getStrategy` 同一生成模式。
- wrapper：`StreamJoinModel.java`/`StreamJoinSpecModel.java` 均为非下划线手写保留文件（空构造 + javadoc 声明 retention 模式，同 `StreamAggregatorModel` 先例）——合法，不违 _gen 纪律。

### 1.2 八项校验逐一核对（审计项 2）——PASS

`StreamModelDslBuilder.validateJoinDeclarations`（:492-581，live 实读）插入点核实：`validateDag` 内 :361-362——`validateEdgeDeclarations(edges, byId)` 之后、返回 edges 之前，与 plan 钉死位置一致。逐项：

| # | 校验 | 代码位置 | 行为 |
|---|------|---------|------|
| 1 | 未知 joinRef | :501-508 | `model.getJoinSpec(ref)` null → `ERR_STREAM_REF_UNKNOWN`，ARG_REF_TYPE=`<joins>/<joinSpec>` 点名 |
| 2 | joinType 缺失 | :509-515 | `ERR_STREAM_INVALID_ARG`，"must declare joinType (INNER/LEFT/RIGHT/FULL)" |
| 3 | 键集缺失 | :516-524 | `countKeyExprs` 任一侧 0 → invalid-arg "requires non-empty leftKeyExprs and rightKeyExprs (equi-join)" |
| 4 | 键数不等 | :525-533 | invalid-arg "key count mismatch … equal arity" |
| 5 | 上游恰 2 | :534-544 | **按声明边计数** `edges.stream().filter(e -> t.getId().equals(e.getTo())).count() != 2` → invalid-arg "exactly two upstream edges (left/right)"；注释明文 self-join 同源两边会被 upstreams 去重集折叠——**修正为真**（见 §1.4 判别性） |
| 6 | HASH 边禁入 | validateEdgeDeclarations :457-464 | `byId.get(e.getTo()) instanceof StreamJoinModel` → `ERR_STREAM_EDGE_HASH_REDUNDANT`，ARG_DETAIL 明示 joinKey 由 joinSpec 双键集声明（对齐 union 先例 :447-454） |
| 7 | windowStrategyRef 命中 + INNER/LEFT 限制 | :552-568 | `model.getStrategy(ref)` null → ref-unknown；命中后 `joinType != INNER && != LEFT` → invalid-arg "supports joinType INNER/LEFT only; … WI13 evaluation item" |
| 8 | timeout 依赖窗口 + 格式 | :569-578 | 有窗口时 `AdvancedTransforms.parseDurationMillis(spec.getTimeout())`（非法即抛）；无 windowStrategyRef 而 timeout 声明 → invalid-arg "timeout only applies to window joins" |

另有无上游兜底（:545-551，ups 空集 invalid-arg），非八项之一，属防御冗余。

### 1.3 buildJoin 占位（审计项 3）——PASS

- `AdvancedTransforms.build`（:111-113）：`if (t instanceof StreamJoinModel) return buildJoin((StreamJoinModel) t);`——instanceof 分派正确接入全序（位于 window/aggregate 之后、reduce/process/union 等同级分支中，位置无关紧要，类型匹配即可达）。
- `buildJoin`（:462-470）：无条件抛 `ERR_STREAM_NOT_IMPLEMENTED`，ARG_DETAIL 明文 "declaration is validated, but the join runtime (hash join / window join operator) is delivered by WI13 buildJoin — not yet implemented"，`.loc(m.getLocation())` 带定位。**非 hollow**：显式 typed fail-fast，与 buildSideOutput 同款归类——scan-hollow 实测 flow 模块 Critical/High/Medium/Low 全 0（见 §1.6），即扫描器将其如实归类为合法占位而非静默空实现。方法体非空、无静默返回、无 TODO 残留（grep 实证）。

### 1.4 实跑与判别性抽查（审计项 4）——PASS

| 命令 | 结果 |
|---|---|
| `./mvnw test -pl nop-stream/nop-stream-flow` | `Tests run: 153, Failures: 0, Errors: 0`，BUILD SUCCESS（153 = WI8c 收口后 140 + 本 WI 新增 13，零退化） |
| `./mvnw test -pl nop-stream/nop-stream-core` | `Tests run: 1663, Failures: 0, Errors: 0, Skipped: 1`，BUILD SUCCESS；skip 定位为 `TestEventTimeWindowE2E`（既有跳过，与 JoinType/WI8d 无关） |
| `./mvnw test -pl nop-stream/nop-stream-flow -Dtest=TestParameterizedJoinModel`（隔离实跑） | `Tests run: 13, Failures: 0, Errors: 0` |

判别性抽查（TestParameterizedJoinModel 实读 + 隔离实跑）：

- **码串钉住**：八个 fail-fast 用例逐一断言 `ex.getErrorCode().toString()`——`nop.err.stream.ref-unknown`（unknownJoinRef/unknownWindowStrategyRef）、`nop.err.stream.invalid-arg`（missingJoinType/missingKeyExprs/keyCountMismatch/singleUpstream/fullWindowJoin/timeoutWithoutWindowStrategy/illegalTimeoutFormat）、`nop.err.stream.edge-hash-redundant`（hashEdgeIntoJoin）——且 7/8 附 message 关键词断言（"missing"/"joinType"/"leftKeyExprs and rightKeyExprs"/"arity"/"exactly two"/"join"/"nope"/"INNER/LEFT"/"windowStrategyRef"）。
- **self-join 用例真的走到 not-implemented 而非更早失败**：`selfJoinTopologyWalksValidationToRuntimePlaceholder`（:216-237）——一 source + 一 `<join joinRef>` 元素 + **两条 edge 同以 s1 为 from**；resolver 注册 srcFn 后 build，断言 `nop.err.stream.not-implemented` 且 message 含 "WI13"。该断言是执行期边计数修正的实症：若按 upstreams 去重集计数（size 1），恰 2 校验会在 invalid-arg 处先抛——not-implemented 到达即证明按边计数生效、八项校验全过、source bean 解析成功、拓扑接线真实可达 buildJoin。与 `singleUpstreamJoinFailsFast`（1 边 negative）构成恰 2 校验的正负对照。
- **合法声明往返**：`legalJoinDeclarationParsesAndRoundTrips` 断言 joinRef/joinType=LEFT/leftKeyExprs/rightKeyExprs/windowStrategyRef/timeout 六字段经 xdef 解析往返无损（同时证明 xdef + typed model 端到端可用）。
- **sanity**：`sourceBeanResolutionNotReachedBeforeValidation` 证明纯声明面失败点在构建期校验、无需 bean 容器（与 plan m3 承诺一致）。

### 1.5 self-join 形态用例与 roadmap 完成判定对照（审计项 5）——PASS

roadmap WI8d 完成判定（live 行 :250）逐项对照：

| 判定 | live 证据 |
|---|---|
| 新增 joins 注册表 | stream.xdef :72-80（base，非 delta） |
| join 的 joinRef | stream.xdef :189 + _StreamJoinModel |
| 字段 joinType/leftKeyExprs/rightKeyExprs/windowStrategyRef/timeout | joinSpec 五项全齐（joinType 可选 + builder 强制，符合 plan M-1 修订） |
| 只做声明与构造期校验，运行时归 WI13 buildJoin | buildJoin 显式 NOT_IMPLEMENTED + xdef/javadoc/错误消息三处边界明文 |
| windowStrategyRef 落点 WI10 windowingStrategies | 校验经 `model.getStrategy()`（WI10 key-attr 查找器）联动 |
| 含 TestParameterizedJoinModel | 实存，13 用例隔离实跑绿 |
| self-join 形态用例 | `selfJoinTopologyWalksValidationToRuntimePlaceholder`——「一 join 元素吃同一上游两条边」语义成立（非两个 join 元素、非 joinRef 复用） |

「本 WI 只做声明与构造期校验」边界同时被 plan 的 Deferred But Adjudicated 段正确记账（successor = WI13）。

### 1.6 工具门禁（审计项 7）——PASS（实跑记录）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `node ai-dev/tools/check-doc-links.mjs --strict` | 0 | 0 errors / 3 warnings（均为 nop-bytecode 旧 plan 既存，与本 plan 无关） |
| `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-flow --severity high` | 0 | Critical/High/Medium/Low 全 0——buildJoin 的 NOT_IMPLEMENTED 被如实归类，非 hollow 发现 |
| `node ai-dev/tools/check-nop-stream-invariants.mjs sync` | 0 | `sync: OK` |
| `parseRoadmapMarkdown`（tools/mission-driver/src/roadmap-check.mjs 实跑） | — | **items 31 + milestones 7，done 15，progress 0.48，无静默丢弃**；WI8d 行仍 `todo`（预期——audit 通过前不翻转，本 audit 即 Phase 3 第一项） |
| `check-plan-checklist --strict`（备案） | 0 | 非 completed plan 且 Phase 3/Closure Gates 未勾 → warnings only，符合收口前预期态 |

### 1.7 文档一致性（审计项 6）——PASS

- `sql-landing-decision.md` §4.1（:46 diff 实读）：新增「**IJoinResolver 作废注记（WI8d，2026-10-02）**：join 无构建期函数解析需求（声明面校验由 builder 前置承担，运行时 keyExpr 求值由 WI13 直接消费 WI9 编译器），预估的 IJoinResolver 不再建设」——与 plan「SPI 侧不做」口径一致；紧随其前的 WI8c 注记中「WI8d 为 IJoinResolver 同构」的历史预告由该作废注记就地解除（见 §3 M-4）。
- `sql-landing-decision.md` §5（:56）：WI8d 行「joins 注册表 + join 的 joinRef（joinType/leftKeyExprs/rightKeyExprs/windowStrategyRef/timeout）；仅声明与构造期校验，运行时求值归 WI13；windowStrategyRef 落点是 WI10 的 windowingStrategies 与 WI17 编译器」——与 plan Purpose/Goals 及 roadmap WI8d 行三处口径逐字一致。
- roadmap Cross-Cutting 4（:406）：WI8d 条目已带「（同回改裁定落 base stream.xdef）」括注，与 WI8c 条目的 D13 回改注记并列且与实际落点（base xdef，非 delta）一致。

### 1.8 plan 文本一致性（审计项 8）——PASS

- Phase 1 四项勾选全部 live 落地：JoinType + xdef（§1.1）、core/xdefs install + 重生成 + wrapper（§1.1，_StreamJoin* 两文件 git untracked 新增即重生成产物）、landing-decision §4.1 注记（§1.7）、日志条目（§1.9）。Exit Criteria 三项亦成立（flow typed 模型携带 join 面；StreamJoinModel extends StreamTransformModel 实证；§4.1/§5 注记齐）。
- Phase 2 五项勾选全部 live 落地：validateJoinDeclarations 位置与八项（§1.2）、buildJoin 分派与占位（§1.3）、TestParameterizedJoinModel 13 用例（§1.4）、日志更新。Exit Criteria 五项成立（八项各有具名测试 + 码串钉住——注意含 HASH 分支共九个断言点但归并八项语义无遗漏；无静默跳过；self-join 到占位；flow 153/core 1663 绿；日志已更新）。
- **执行期修正已记录两处**：plan Phase 2 条目原文括注「执行期修正：按声明边计数，upstreams 去重集会把 self-join 同源两边折叠成 1」；日志条目同款记录「按声明边计数而非 upstreams 去重集——self-join 同源两边被 Set 折叠的实测修正」。代码注释（:534-535）同义。三处一致。
- Phase 3 未勾、Closure Gates 未勾属预期（本 audit 即其第一项）。唯一文本瑕疵见 §3 M-1。

### 1.9 日志与 git 纪律（审计项 9）——PASS

- `ai-dev/logs/2026/10-02.md` 顶部新增 WI8d 条目（diff 实读）：五段与 live 实况逐项吻合——JoinType/xdef/重生成、八项校验（含边计数修正）、buildJoin 占位、13 用例、landing-decision 注记 + 「flow 153 绿 + core 1663 绿」。无虚报。
- `git status` 全量实读：改动面 = stream.xdef（源模型）、AdvancedTransforms（+20）、StreamModelDslBuilder（+122）、`_gen/_StreamModel.java`（重生成 diff，内容为 xdef 派生形态见 §1.1）、ai-dev 两文档 + 新 plan；untracked = JoinType（core model 包）、两个 wrapper、两个 _gen 新文件、TestParameterizedJoinModel。**零手改既有 `_` 前缀生成物**——join 相关 _gen 三文件均为本次重生成产物（javadoc 原样携带 xdef 注释为 codegen 特征），wrapper 非下划线合法。
- JoinType 落点：`nop-stream-core/src/main/java/io/nop/stream/core/model/JoinType.java`——与 plan Scope 及审查修订 B-1 一致；`isOuter()` 辅助方法现状评估见 §3 M-2。

## 2. 实跑证据汇总

- `./mvnw test -pl nop-stream/nop-stream-flow`：153/153 绿（exit 0）
- `./mvnw test -pl nop-stream/nop-stream-core`：1663/1663 绿，1 skipped（TestEventTimeWindowE2E 既有跳过，与本 WI 无关；exit 0）
- `./mvnw test -pl nop-stream/nop-stream-flow -Dtest=TestParameterizedJoinModel`：13/13 绿（隔离实跑）
- 四个门禁工具退出码全 0；roadmap 解析 31+7 实测（done 15，WI8d 仍 todo 属预期）

## 3. Minor 发现（均非阻塞）

- **M-1**：plan Phase 3 首项（L100）写作「声明面、**六项校验**、占位边界……」——「六项」系 WI8c 模板复用残留，本 WI 实际为**八项**校验（同 plan Goals、Phase 2、Closure Gate 均写「八项」）。Phase 3 收口翻转 Status 时顺手改为「八项」即可，不构成勾选与事实不符（该项本身未勾选）。
- **M-2**：`JoinType.isOuter()`（core :26-28）当前无生产调用方（builder 的窗口 join 限制用显式 `!= INNER && != LEFT` 比较）且无测试。评估：roadmap 完成判定不要求 JoinType 测试；isOuter 为面向 WI13 的语义预告（javadoc 明文），当前属声明面 API。**不要求本 WI 补 core 测试**；建议 WI13 消费该方法时补一个平凡断言（或在 WI13 plan 显式记账），避免长期 dead API。
- **M-3**：plan Scope「测试资源」列了 `_vfs/nop/stream/test/`，实际未创建任何测试资源文件——测试全部使用内联 XML 字符串 + `InMemoryBeanFunctionResolver` 手工注册。该做法比 plan 预列更简（与「无需 beans 文件」的同一句自我限定一致），非缺口；后续 plan 措辞宜避免预列未必需要的产物路径。
- **M-4**：landing-decision §4.1 WI8c 注记（:45）保留的历史预告「WI8d 为 IJoinResolver 同构」与紧随的 WI8d 作废注记（:46）并置。作废注记已明文解除该预告，语义无矛盾（:45 是 WI8c 时点的预测记录）；若追求零歧义可在 :45 该短语后补「（后经 WI8d 作废，见下）」，可选。

## 4. 无静默跳过检查

新增主代码 5 文件（JoinType / StreamJoinModel / StreamJoinSpecModel wrapper / _gen 两件为生成物）+ 两个 builder 改动：grep TODO/FIXME/XXX 零命中；buildJoin 方法体唯一语句为显式抛 `ERR_STREAM_NOT_IMPLEMENTED`（typed 失败，非空体非静默返回）；所有失败路径显式抛 `StreamException` 码串；scan-hollow flow 高危零发现。

## 5. 结论

WI8d 的声明面（base stream.xdef 第三张同构注册表）、typed 模型重生成管线、八项构造期校验（含 self-join 边计数执行期修正的判别性证明）、buildJoin 显式 NOT_IMPLEMENTED 占位（WI13 边界三处明文）、self-join 形态用例（roadmap 完成判定明文）、文档四处注记（landing-decision §4.1/§5、roadmap CC4、日志）全部在 live repo 成立；flow 153 + core 1663 实跑绿；门禁全 0；roadmap 31+7 解析无丢弃；_gen 纪律干净。

**裁定 PASS**：本 audit 构成 plan Phase 3 第一项证据。剩余收口动作（实现者执行，非本 audit 范围）：① 顺手修正 plan Phase 3 L100「六项」→「八项」（M-1）；② roadmap WI8d `todo` → `done`（括注单层无嵌套）+ `parseRoadmapMarkdown` 复核 31+7；③ plan Status → `completed` + Closure 段落与证据回填；④ `check-plan-checklist --strict` 与 `check-doc-links --strict` 复核退出码 0。M-2 建议转入 WI13 plan 记账，M-3/M-4 仅为记录，无动作义务。
