# REGISTRY 生命周期契约（NopStreamOffsetBackingStore）

> Status: active
> Last Reviewed: 2026-10-25（plan 2503 Phase 1a 产出；R4-24 实现契约一处定稿）
> Source: plan 2503（`ai-dev/plans/2503-cra-phase8.9-r1-08-debezium-delivery.md`）Phase 1；裁决输入 = WI 4.1 合并裁定（`ai-dev/audits/2026-10/2026-10-20-2210-deep-audit-nop-message-concurrency/merge-adjudication.md` §五-5：REGISTRY 契约一次裁决，工单内前置设计裁定，禁止各修一半）；三单发现事实源 = 同审计目录 C1-002（摘除竞态/守卫缺失面）、C5-001（内存演化面，MA-03 主登记）、C6-004（清理路径缺失 + stale-resume 语义面，MA-03 同 diff 成员）；行号以该审计 2026-10-20 live 回读值为准，漂移以 live 为准
> 实现锚：`nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java`（REGISTRY `:59-60`、建桶点 `forConnector:85` / `configure:109` / `ensureBound:230`、唯一移除点 `clearConnector:101-103`、`stop():136-139`、`setOffsets` REPLACE `:189-205`）；stop 配对释放调用点 = `DebeziumMessageSource.stop()` / `DebeziumEngineWrapper.stop()` 收尾
> 桥接缘起：`ai-dev/design/nop-stream/connector-design.md` §5.4.2 D1（Debezium 2.4.0 无 `using(OffsetBackingStore)`，connector-name registry 桥接 source-function 实例与引擎反射实例；3.7 复核仍需保留）；本文档为其未声明生命周期的补全契约，不重复 D1 的桥接选型论证

## 一、设计结论

1. **裁定①（生命周期三选一终裁）**：REGISTRY 桶（connector name → 共享 offset map）的生命周期契约裁定为 **stop 侧配对释放 + 绑定代条件移除**——即 C6-004 的 stop-clear 主干与 C1-002 的条件移除守卫**一次落成一个机制**（C1-002 的引用计数/绑定代形态为其实现表达）。效果：桶的存在期 = 某代引擎绑定运行期 + 显式恢复声明期；停机即配对释放；`clearConnector` 从无条件 remove 收紧为条件移除（活实例绑定期间的摘除 = typed 拒绝，不静默 no-op）。**同名重启续传契约随之定稿：同 JVM 内同名重启的唯一合法续传路径 = 显式 checkpoint 恢复（restoreState → `forConnector` + `setOffsets` REPLACE 重播种）；无 checkpoint 的同名重启 = 全新起点**——AR-03 静默 stale-resume 语义废除（C6-004 语义面闭合），无界账本随配对释放闭合（C5-001 内存面闭合，增长上界 = 并发活跃连接器名数）。
2. **裁定②（拒绝 TTL/evictIdle）**：offset 恢复基质不允许时间性不确定淘汰——quiescent 源库（低频 binlog、长时间无写入）的长活连接器会被误逐出，恢复位点静默丢失，恰在恢复最需要的时刻失效；TTL 数值无原则性依据；且 TTL 存续期内 stale-resume 依旧成立（只把不确定性拉长，C6-004 语义面未闭合）。内存治理由配对释放 + 驻留诊断指标承担，不以时间兜底。
3. **裁定③（软钉 #5：setOffsets REPLACE 契约维持，现状钉不改写）**：`setOffsets` 的 REPLACE（clear+put）语义是声明契约而非缺陷——nop checkpoint 是恢复 offset 的单一事实源（R5 CON-03），merge 语义会让 checkpoint 缺席的分区键（上代残留/更新代分区）存活，恢复后跳过 checkpoint 位点读。`TestNopStreamOffsetBackingStore.java:90-98` 现状钉**语义不改写**；R4-24 仅增不改：按「恢复声明窗」加守卫（§3.3），钉内两处调用均在窗内、断言零波及。
4. **裁定④（WI 2.5 消费面互斥分层维持）**：消费面三面（peri-C3-008 lag 探测键错位独立缺陷 / peri-CL-010 REGISTRY 只增不清台账 watch / peri-C1-005 同名 connector 双引擎 P1）与本体三单（C5-001 + C6-004 + C1-002）四方互斥维持，零重复工单——本体契约定「桶何时存在/释放」，不修消费方键派生与编排双跑；R4-24 实施不越界承接消费面，消费面批次（R2-15）实施引用本契约语义、不重开本体裁决。详见 §3.4。
5. **裁定⑤（与 C3-014 幽灵引擎修复的一致性）**：同名 registry 桶互覆残余（C3-014 风险③：ghost 与新引擎共享同名桶、ghost offset 覆写致恢复跳数）的语义归宿 = **双层防御结构闭合**——主防御是 C3-014 的 stop 契约「已发出 → 已终止」（ghost 不可再现）；本契约的配对释放 + 条件移除为第二层（即便过渡态残留 stuck ghost，其写入落孤儿 map，不再覆写新一代引擎的恢复位点）。一致性约束与 C3-004 恢复语义的衔接见 §3.5。
6. **裁定⑥（覆盖对象与禁各修一半）**：本契约 = 4.1 merge-adjudication §五-5「一次裁决」的成文，覆盖 **C5-001 + C6-004（MA-03 两成员）+ C1-002 三单 + 软钉 #5 + WI 2.5 消费面表态**；batch-proposal R4-24 行「覆盖 MA-03/MA-04/C1-002」中的 MA-04 系笔误/漂移（4.1-MA-04 = Local 重投 executor 族，无 REGISTRY 面），不属本契约覆盖面。R4-24（roadmap 8.64，硬依赖 8.9/R1-08）按本裁决实施：不得只落 stop-clear 不落条件移除（C1-002 面残留），也不得只落条件移除不落 stop 配对释放（MA-03 两面残留）；owner doc 补记（`docs-for-ai/03-modules/nop-message.md` 生命周期契约节）与驻留诊断指标为 R4-24 强制实施项。

## 二、背景与动机

`NopStreamOffsetBackingStore.REGISTRY` 是 Debezium 嵌入引擎与 nop-stream checkpoint 协议之间的桥接基质：Debezium 2.4.0 起引擎经 `offset.storage` 反射实例化 offset store，与 source-function 侧实例只有经 connector-name 静态注册表才能共享同一张底层 map（connector-design §5.4.2 D1）。该桥接落档时只声明了「共享」语义，未声明**生命周期**：建桶点三处（`forConnector` / `configure` / `ensureBound`）与移除点一处（`clearConnector`，无条件 remove）无任何配对约束，`stop()` 只翻转 `started` 布尔——stop 语义与回收语义完全脱钩。WI 4.1 审计从三个互斥面立案：C5-001（账本单调增长：条目随 distinct 连接器名只增不删，无容量、无指标）、C6-004（清理调用点不在本模块生命周期内 + 独立使用路径同名重启静默从 stale offset 续传，恰好漏掉间隔期变更）、C1-002（`clearConnector` 无条件摘除可分裂活实例写入——孤儿 map 静默丢写）。三个修复方向（stop-clear / TTL / 条件移除）对「同名重启续传」期望的影响相反，且分属三个工单——若各修一半，续传契约将同时存在两套互相矛盾的语义。本设计把三者收敛为一次裁决，使 R4-24 的实施只有一种合法形态。

## 三、核心设计

### 3.1 生命周期契约（三选一终裁展开）

| 面 | 契约条款 | 闭合对象 |
|---|---------|---------|
| 建桶 | `forConnector` / `configure` / `ensureBound` 三点语义统一：桶创建即登记当前实例为该代持有者（绑定代/引用登记） | C1-002 守卫缺位（建侧） |
| 释放 | stop 收尾对本代绑定桶执行**配对释放**（`DebeziumMessageSource.stop()` / `DebeziumEngineWrapper.stop()` 一处承担即可，两级不得双计；实施归属 R4-24）；释放前提语义 = stop 契约「已终止」（§3.5） | C5-001（账本闭合）、C6-004（清理路径入生命周期） |
| 摘除 API | `clearConnector` 保留为公开 API，语义收紧为**条件移除**：仅当目标桶无在用绑定时摘除；活实例绑定期间的摘除请求 typed 拒绝（不静默 no-op）。nop-stream first-run `newFreshOffsetStore` 的 clear→forConnector 同线程调用序不变、仍合法（此时无在用绑定） | C1-002（摘除竞态） |
| 孤儿写入 | 结构性窗口显式声明：桶被释放后，旧代实例经 `data` volatile 仍持旧 map 引用，其后续写入**不得静默合并进任何新一代桶**。R4-24 加固义务：旧代实例访问已释放桶时显式失败或可观测（推荐 typed 拒绝——写路径失败使残留引擎快速显式死亡，与 C3-014 ghost 歼灭同向；最低限度 WARN 可观测） | C1-002 孤儿面、WI 2.5 C1-005 相邻面语义归宿 |
| 诊断 | REGISTRY 驻留诊断指标（桶数 + 每桶键数）为 R4-24 强制实施项（MA-03 修复点组成） | C5-001 可观测性 |
| owner doc | `docs-for-ai/03-modules/nop-message.md` 补记 REGISTRY 生命周期契约节（本契约的使用知识投影），R4-24 随批落 | MA-03 owner doc 义务 |

**同名重启续传契约**（三选一分歧的最终答案）：桶随 stop 释放后，同 JVM 内同名重启若无 checkpoint 恢复声明，`forConnector` 建新桶、从源库当前位点起步（fresh start）；续传只能经显式恢复链（restoreState 先于 start 的 coordinator 时序）以 `setOffsets` REPLACE 重播种。AR-03 静默续传（旧条目命中 → 从上轮 stale offset 继续、漏掉间隔期变更且无告警）自此为废除语义，不再是「未声明的行为」。

### 3.2 拒绝的两个替代案

- **拒绝 TTL/evictIdle（C5-001 原方案）**：见裁定②。补一点：TTL 形态下「桶是否存在」取决于墙钟而非生命周期事件，`forConnector` 的返回结果变成时间敏感——同一份 checkpoint 恢复代码在 TTL 窗内外行为不同，这是恢复基质不可接受的非确定性。
- **拒绝纯条件移除（C1-002 原方案单独成契约）**：条件移除只治「摘除竞态」，不治生命周期脱钩——无 stop 配对时桶仍只增不删（C5-001 未闭合），独立路径 stale-resume 依旧（C6-004 未闭合）。条件移除只作为 stop-clear 精化形态的竞态守卫纳入本契约（§3.1 摘除 API 行），不单独构成完整答案。

### 3.3 setOffsets REPLACE 契约与恢复声明窗（软钉 #5 裁决）

- **REPLACE 维持**：`setOffsets` 的 clear+put REPLACE 语义为声明契约，依据 = R5 CON-03「nop checkpoint 是恢复 offset 的单一事实源」——checkpoint 缺席的分区键必须不存活，否则恢复后首次快照携带幽灵分区、读取跳过 checkpoint 位点。`TestNopStreamOffsetBackingStore.java:90-98` 现状钉为该契约的保护钉，**语义不改写**。
- **恢复声明窗**：`setOffsets` 的合法调用窗 = `forConnector`（建桶/绑定）之后、engine start 之前。窗内 REPLACE 为声明语义；引擎运行中调用会把运行期新写入回滚为恢复值（C1-002 失效面登记的时序面）。R4-24 实施按恢复声明窗加守卫：窗外调用 typed 拒绝。当前唯一生产调用序（`initializeState` 先于 engine start）在窗内，零波及；软钉测试两处调用仍在窗内、断言零改动，窗外负向断言随 R4-24 批新增（先增后守，钉零回退）。

### 3.4 WI 2.5 消费面表态（互斥分层维持声明）

| 消费面项 | 内容与归属 | 与本契约的边界 |
|---|---|---|
| peri-C3-008 | lag 探测 connector 名与链注册名 base-i 键错位，rescale 主场景 lag 门禁观测量恒空平凡通过——独立缺陷，批次 R2-15 | 消费方键派生缺陷，不由本契约闭合，也不由 R4-24 承接；其 probe 读侧 `computeIfAbsent` 建空条目的增长面属消费方修复纪律（R2-15），不是本体契约条款 |
| peri-CL-010 | REGISTRY 按 connector 名只增不清（轻度 F4/F8）——台账 watch 身份维持 | 增长面恰由本契约 §3.1 配对释放闭合；watch 注销判定归 cra-index 台账回填（R4-24 批末），不因本契约成文自动闭合 |
| peri-C1-005 | 同名 connector 双引擎（阶段 TOCTOU + `runningJobs.put` 静默覆盖孤儿链 + 双消费双写）——peri MA-05 主登记成员，批次 R2-15 | 编排面双跑缺陷本体归 R2-15；其 registry 踩踏子断言经复核修正（`clearConnector` 只摘条目、旧实例 volatile data 持旧 map，checkpoint 快照不受踩踏）——本契约 §3.1 孤儿写入行即该相邻面的语义归宿，双引擎编排不由本契约或 R4-24 闭合 |

四方互斥（本体 C5-001/C6-004/C1-002 + 消费面三面）维持零重复：R4-24 实施面以本契约为准、不越界修消费面；R2-15 实施时引用本契约的孤儿写入/条件移除/REPLACE 语义，不重开本体裁决。

### 3.5 与 C3-014 幽灵引擎修复的一致性声明

- **现状互覆机制**（C3-014 风险③）：STARTING_TASKS 窗口 stop 被依赖方 `IllegalStateException` 按契约拒绝且被载体双层吞掉、`stopped` 一次性守卫使后续 stop 永久 no-op → 永久幽灵引擎（活 binlog 连接 + 引擎线程组 + offset 持续写入）。同 JVM 同名重部署时，桶从未被摘除，`forConnector` computeIfAbsent 命中同一桶——ghost 与新引擎共桶，ghost offset 覆写新引擎恢复位点，恢复后跳数（数据面后果）。
- **双层防御的语义归宿**：
  1. **主防御 = C3-014（plan 2503 Phase 3）**：stop 契约「已发出」→「已终止」（STARTING_TASKS 拒绝有预算重试或 typed 上抛 + 四停止面终止性验证）——ghost 不可再现，共桶窗结构闭合。
  2. **契约防御 = 本文档**：stop 收尾配对释放使旧代桶在停机时摘除；即便终止性验证失败的过渡态残留 stuck ghost，其 `data` 引用与新一代桶已分裂（条件移除保证新一代 `forConnector` 不命中旧桶）——ghost 写入落孤儿 map，不再覆写新引擎恢复位点；孤儿写入按 §3.1 加固义务显式失败或可观测（ghost 自暴露）。
- **一致性约束（R4-24 实施纪律）**：①本契约的配对释放以「stop 已终止」为前提语义；终止性验证失败（typed 失败/ghost 风险声明）时桶摘除**仍执行**（防互覆优先于保守驻留），但两条失败信号不互代、不互吞——终止性失败与孤儿写入告警各自显式可见。②配对释放的落点与 C3-014 的 stop 改造同文件（wrapper/source stop 收尾），plan 2503 Phase 3 与 R4-24 的 diff 边界以「谁先落地谁不得假设对方存在」为零回退纪律：R4-24 不得依赖 Phase 3 未落的终止性信号，Phase 3 不得改写 R4-24 已落的释放调用点语义。
- **与 C3-004 恢复语义的衔接**：恢复时序前提 `LocalSourceCoordinator` restoreState 恒先于 start；C3-004 修复后恢复态与新鲜份额求差集、每表恰一桶/恰一引擎。REPLACE 重播种（§3.3）保证桶内容 = 恢复态而非「恢复态 ∪ 上代残留」——若无 REPLACE，上代桶残留分区键使恢复后首次快照携带幽灵分区，与 C3-004 的分配差集语义直接矛盾。配对释放（桶不跨代残留）+ 条件移除（活代不被误摘）+ REPLACE（恢复内容单一事实源）三者合成闭环：**恢复位点的唯一合法来源 = nop checkpoint**。

## 四、拒绝了什么

- **拒绝 TTL/evictIdle**：时间性淘汰对恢复基质是非确定行为（quiescent 长活连接器被误逐出 → 恢复位静默丢失）；TTL 窗内外 `forConnector` 行为不一致；stale-resume 只是拉长未消除。
- **拒绝纯条件移除（不含 stop 配对释放）**：只闭 C1-002 竞态面，C5-001 无界增长与 C6-004 stale-resume 原样残留——恰是「各修一半」被禁止的形态本身。
- **拒绝无条件 stop-clear（不含条件移除守卫）**：C6-004 原方案形态——不治 C1-002 摘除竞态（活实例绑定期间被 clear 分裂孤儿写入），且对 first-run 之外的显式清理调用无在用判定。
- **拒绝改写 setOffsets REPLACE 为 merge 语义**：破坏「checkpoint 单一事实源」，恢复后跳位点读（R5 CON-03 已证缺陷形态）。
- **拒绝本契约越界承接消费面**：C3-008 键派生 / C1-005 编排双跑为消费方缺陷，本体契约落地不改变其缺陷成立性，混修将制造 R4-24 与 R2-15 的批次纠缠。

## 五、与已有设计的关系

- `../nop-stream/connector-design.md` §5.4.2 D1：REGISTRY 桥接的选型出处（Debezium 2.4.0 反射约束）；本契约补全其未声明的生命周期面，不重复桥接论证；D1 的 successor 条件（Debezium 暴露直接注入 API）触发时，本契约随桥接简化一并复审。
- `../nop-message/backend-semantics-design.md` §3.3.3（D12）：CDC 事件交付语义（毒事件 endpoint 两极同禁）为同一模块的另一裁定面——D12 管事件失败后交付终点如何行为，本契约管恢复位点账本何时存在与释放；D12 的桥接重放义务消费本契约 §3.5 的恢复位点单一事实源结论。
- C3-014 停止契约（plan 2503 Phase 3 实施面）：本契约 §3.5 为其一致性声明落点；「已终止」语义与四停止面终止性验证的设计归属该 plan，本文档不重复。
