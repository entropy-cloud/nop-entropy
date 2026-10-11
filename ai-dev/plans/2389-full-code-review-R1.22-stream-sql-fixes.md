# 2389 full-code-review R1.22 — nop-stream-sql 三修复（P1-2.8-001/002/003）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Completed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.22；findings `P1-2.8-001`（dim05-08）+ `P1-2.8-002`（dim14-02）+ `P1-2.8-003`（dim14-07），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.8-stream-sql-first-audit.md`
> Related: R1.23（同模块族 core 修复已落地并提交）
> Owner Doc: `docs-for-ai/03-modules/nop-stream.md`（引擎语义简注；`ai-dev/backlog/nop-stream-sql-roadmap.md` 为 mission-driver 严格格式且未登记本三条发现——不改写，裁定记入本 plan）

## Purpose

收口 nop-stream 双流等值 join / 有序缓冲三条 P1：①EquiJoinOperator null 等值键经 `String.valueOf` 变 "null" 与字面量 "null" 键互配发射幻影行、并使双方 matched=true 剥夺 OUTER 补齐（SQL 语义 NULL≠NULL）；②PerKeyOrderedBuffer.putAll 不推进 insertionSeq——restore 后新 add 的 seq 与恢复条目对位碰撞，TreeMap 同键覆盖使恢复记录从工作视图静默消失；③hash 形态 completeUnmatched 缺水位前缘过滤——ts>watermark 的未匹配记录被提前补 null 且留在缓冲再配对=双发（exactly-once 违反）。

## Current Baseline

- **P1-2.8-001**：`EquiJoinOperator.bufferKeyOf:368-374` 用 `String.valueOf(equiKey)`——null 变 "null"，与真实字符串键 "null" 的缓冲键碰撞；`emitMatches:269-285` 按缓冲键扫描对侧 → 两侧 null 键记录互配发幻影行且双方 matched=true → completeUnmatched 跳过（OUTER 补齐被剥夺）。keyBy(null) 经 partition 确定可达（审计 dim05-08）。
- **P1-2.8-002**：`PerKeyOrderedBuffer.putAll:56-61` 直接 `tree.putAll(e.getValue())` 搬 (ts,seq)→value 条目但不推进 `this.insertionSeq`——restore 后（EquiJoinOperator.restoreState:164 调用）新元素 `add():73` 从本实例计数器分配 seq，与恢复条目同 ts 同 seq 碰撞 → TreeMap.put 覆盖 → 恢复记录从工作视图消失（补全+匹配双通道失效）。`insertionSeq:37` 为实例字段，序列化后恢复为独立计数。**同面缺陷（审查 M1 勘正，裁定 stash-扩展一并修）**：生产恢复路径 restoreState **先于** open——SupervisionLoop.rebuildTask:849→934 对 deepCopy 新实例（transient buffer=null）调 restoreState，此后 invoke() 才 open（GraphModelCheckpointExecutor:1363/RescaleStateAssembler:454 同入口）——`restoreState:164` 对 null buffer 解引用 NPE（同族先例：R1.23 P1-6.7-008 修复 + WindowOperator.restoreState:662 stash 注释）。修复面=stash-then-apply-in-open（restoreState 暂存 restored buffer，open() 末尾 putAll——seq 推进逻辑不变，搬运时机后移）+ 测试腿② 增 restore-before-open 序用例。
- **P1-2.8-003**：`completeUnmatched:287-295` 无条件遍历 key 下全部条目——hash 形态 `completeAndTrimHash:205-216` 在 trimToWatermark **之前**调用，ts>currentWatermark 的乱序未匹配记录被提前补 null 发射，且留在缓冲（trim 只删 ts≤wm）→ 后续对侧到达再配对=同一逻辑行双发。javadoc（:38-39 D1=(a) exactly-once）违反。窗口形态调用点（fireWindow:254-263）因窗口触发条件 winStart+duration+timeout≤wm 保证全部条目 ts≤wm，前缘过滤对窗口形态无行为影响（可安全共用）。
- **测试基建**：join 语义测试在 nop-stream-runtime（TestEquiJoinHashSemantics 等 4 个，ListOutput harness 成熟）；PerKeyOrderedBuffer 自身测试在 **nop-stream-runtime**（windowing 包 TestPerKeyOrderedBuffer，WI12 落 runtime——构件本体后经 git-mv 上移 core 测试未随迁；审查 M3 勘正）。

## Goals

- null 等值键 SQL 语义：null 键记录不与任何记录匹配（含另一 null 键），OUTER 形态下作为未匹配补齐（matched=false 保持），INNER 形态下随 trim 丢弃；缓冲键用保留哨兵避免与 String.valueOf 任何非 null 键的 "null" 形态碰撞。
- putAll 后本实例 insertionSeq 推进到 max(自身, 恢复最大 seq)+1——restore 后新 add 不与恢复条目碰撞。
- completeUnmatched 增水位前缘过滤（tsKey[0] ≤ currentWatermark 才补齐）——hash 形态消除双发；窗口形态行为不变。
- 判别测试三腿 stash 实证；join 四测试+buffer 测试+nop-stream-core/runtime 全量回归绿。

## Non-Goals

- equiKey 含 `\u0000` 分隔符的 fail-fast（P3-2.8-003 台账通道）；fireWindow 防御性重排 hardening（dim14-08 经不变量证明不可达已驳回不记账——本批不引入相关改动，无需代码动作）；窗口/OUTER 语义重构；P2-2.8 台账行。

## Scope

### In Scope

- `EquiJoinOperator`：bufferKeyOf null 哨兵 + emitMatches null 跳过 + completeUnmatched 前缘过滤（:287-295）+ javadoc 契约更新
- `PerKeyOrderedBuffer.putAll`：insertionSeq 推进
- 测试：TestEquiJoinHashSemantics 扩展（null 键族/前缘族）；PerKeyOrderedBuffer putAll 碰撞判别测试（core 模块）
- owner doc：nop-stream.md join 语义简注（null 键 SQL 语义 + 恢复后 seq 单调）

### Out Of Scope

- Non-Goals 全部；nop-stream-flow（builder 面）；P3 台账项

## Execution Plan

### Phase 1 - 三修 + 判别测试

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] PerKeyOrderedBuffer.putAll：合并后推进 insertionSeq（遍历 other 条目取最大 seq，insertionSeq = max(insertionSeq, maxSeq+1)）；javadoc 声明 restore 后 seq 单调不变量
- [x] EquiJoinOperator.restoreState stash-化（审查 M1 裁定 (a)）：restoreState 暂存 restored buffer 到 `pendingRestoredBuffer` 字段（不触碰 transient buffer），open() 创建 buffer 后 putAll+清引用——对齐 R1.23/WindowOperator 先例；harness 序（open→restore）行为不变
- [x] EquiJoinOperator.bufferKeyOf：null equiKey 用保留哨兵常量（如 `"\u0001null\u0001"`）替代 String.valueOf(null)="null"；类 javadoc 记录哨兵保留约定
- [x] EquiJoinOperator.emitMatches：`rec.getEquiKey() == null` 时直接 return（null 键永不匹配——SQL NULL 语义），记录仍入缓冲走补齐/trim
- [x] EquiJoinOperator.completeUnmatched：增 `tsKey[0] <= currentWatermark` 前缘过滤（hash 形态消除双发；窗口形态触发条件保证无行为变化——plan 已推演）
- [x] 测试：①TestEquiJoinHashSemantics 扩展 null 键族（null-L + null-R 不互配、null-L + "null"-R 不互配、FULL 下 null 键记录按未匹配补齐、"null" 字面量键正常配对）与前缘族（FULL：左记录 ts=50 于 wm=100 前到达未匹配 → wm=100 补齐一次；ts=150 乱序右记录于 wm=100 时不补齐、wm=200 时才补齐——修复前 ts=150 在 wm=100 即被补齐=双发判别）②PerKeyOrderedBuffer putAll 碰撞判别（**runtime 模块 TestPerKeyOrderedBuffer 扩展——审查 M3 勘正：WI12 测试在 runtime/windowing 非 core**：other 含 (ts=10,seq=5)，this.insertionSeq=0；putAll 后 this.add 同 ts=10 → 分配 seq≥6 不覆盖恢复条目，两条目共存）③EquiJoinOperator restore-before-open 序用例（审查 M1：restoreState(带 buffer 快照) 在 open() 之前调用 → open 后工作视图含恢复条目——修复前 NPE 判别）
- [x] **判别验证（四腿 stash）**：①stash bufferKeyOf+emitMatches→null 键族 FAIL（幻影配对）；②stash putAll 推进→碰撞判别 FAIL（恢复条目被覆盖）；③stash completeUnmatched 前缘→前缘族 FAIL（wm=100 时 ts=150 被提前补齐）；④stash restoreState stash-化→restore-before-open 用例 FAIL/NPE（生产序判别）——各腿恢复后绿
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 三修全落地（null 语义/seq 单调/前缘过滤）
- [x] 判别测试四腿绿 + 既有测试零回归
- [x] nop-stream-core/nop-stream-runtime 测试全绿
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-stream.md`

- Item Types: `Fix`

- [x] nop-stream.md：双流 join 语义简注（null 等值键不匹配按 OUTER 补齐；恢复后 insertion 序单调；补齐前缘=水位）
- [x] `ai-dev/design/nop-stream/join-operator.md` 规范性修订（审查 M2）：§3 补齐语义增「前缘=水位」约束；状态设计段 buffer key 构成增 null 哨兵保留约定
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.22 翻 done；index P1-2.8-001/002/003 → fixed；daily log 条目

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-2.8-001/002/003）
- [x] 行为/契约结果已达成（判别三腿 + 零回归）
- [x] 必要 focused verification 已完成（nop-stream-core/runtime 全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime` 全绿（core 1676/runtime 1238，执行轮+审计亲读 log 双验证）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- equiKey 含 `\u0000` 分隔符 fail-fast（P3-2.8-003 台账通道，勿双立）
- OverWindowOperator.restoreState:148 的同款 restore-before-open NPE 面（P2-2.8-001 为 dim14-01 setCurrentKey 泄漏系另一问题；本 NPE 面随其台账通道核验——EquiJoinOperator 侧已由本 plan stash-化修复）
- flow 模块 TestEquiJoinParallelismInvariant（第 5 个 join 测试）：INNER/非 null 键/无 checkpoint 管线对三修无行为牵动（审查 m4 已排除），回归门不覆盖它为显式裁定

## Closure

Status Note: 三修+审查 M1 stash-扩张在 live 代码全部在位：null 哨兵解析自洽（sideOf/equiKeyOf/twinWindowKey 往返推演+哨兵避 SEP）、putAll seq 推进数学等价实现、前缘过滤窗口形态零行为变化推演成立、restoreState 双路分流对齐 R1.23 先例。判别四腿精确命中（回退态 3 FAIL+1 ERROR NPE、其余 10 旧用例不受扰；putAll 腿独立 1 FAIL），恢复后 core 1676+runtime 1238 全绿。收口五件套（roadmap/index/daily log/双 owner doc/doc-links 边界合规）落盘完成。

Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）agent_887c2d3a-088e-4466-8c6b-6a1607f955fa
- Evidence:
  - P1-2.8-001 PASS：EquiJoinOperator.java:100 哨兵/:426-434 bufferKeyOf/:313-315 emitMatches 早退；哨兵解析自洽推演（hash/window 双形态）+残余碰撞 javadoc 边界（P3-2.8-003 tolerance class）
  - P1-2.8-002 PASS：PerKeyOrderedBuffer.java:64-78 seq 推进（空 other/-1 边界+不回退语义正确）；EquiJoinOperator restoreState:188-205 双路分流+open:150-156 合并清引用
  - P1-2.8-003 PASS：completeUnmatched:343-346 前缘过滤；窗口形态恒放行推演成立；类 javadoc :44-50 契约同步
  - 测试证据：TestEquiJoinHashSemantics 11（+3）/TestEquiJoinWithCheckpoint 3（+1 生产序）/TestPerKeyOrderedBuffer 9（+1）全绿；_tmp/stream-tests2.log core 1676+runtime 1238 BUILD SUCCESS（log birth 晚于源码恢复时刻=最终修复态）
  - 判别证据：_tmp/disc-join.log（回退态：null×2 FAIL 幻影配对+前缘 FAIL 提前补齐+restoreBeforeOpen ERROR NPE；其余 10 旧用例不受扰）+ _tmp/disc-putall.log（1 FAIL "expected [restored, fresh] but was [fresh]"——TreeMap 覆盖实锤）
  - Anti-Hollow：pendingRestoredBuffer 写点↔消费点连通+三段断言；putAll 推进有失败注入实证；null 早退不跳过缓冲/持久化、前缘 continue 非丢弃
  - 收口完整性：roadmap :153 done；index :119-121 fixed；daily log R1.22 条目；nop-stream.md :27-29 注记节（ai-dev 引用为散文形式=边界合规）；join-operator.md §3 三要素在位
  - 工具门禁：doc-links --strict 0；checklist --strict 0（completed 态复跑）；hollow 扫描（nop-stream/core/runtime）退出码均 0
  - Follow-ups 诚实性：P3-2.8-003 台账独立缺陷面；OverWindowOperator:148 同款 NPE 亲读确认存在（随台账通道）；flow 测试排除裁定成立（runtime 同名测试已在门内跑绿）

Follow-up:

- OverWindowOperator.restoreState:148 同款 restore-before-open NPE（随 P2-2.8-001 台账通道核验）；P3-2.8-003 分隔符 fail-fast（台账通道）；restoreState 双调覆盖 stash 留观（生产 rebuild 单次恢复不可达，与 WindowOperator 先例同形态）
