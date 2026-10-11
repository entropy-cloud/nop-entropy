# 2420 WI8 IWindowOperatorFactory 收敛为 WindowSpec 单一契约

> Plan Status: active
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md（v1.2）Phase C WI8；E4 销号联动 nop-stream-production-roadmap.md P2-6
> Related: plan 2417（WI7 窗口算子归位 core，commit feecf09a5f）

## Purpose

把 `IWindowOperatorFactory` 从「4 抽象 + 13 default shim」的 17 方法参数蠕变接口收敛为 WindowSpec 单一契约（4 方法、零 default），消灭 642 行适配器的 17 连覆写，并按路线图 E4 销号 production roadmap P2-6。本 WI 完成后路线图 M2 里程碑（窗口算子归位）达成。

## Current Baseline

2026-10-11 在 commit feecf09a5f 工作树上逐条实证：

- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/IWindowOperatorFactory.java` 共 **342 行 / 17 个方法**：4 个抽象（create{Aggregate,Reduce,Apply,Process}Operator，各 8 参）+ 13 个 default shim，按 4 代参数蠕变排列（第 1 代 elementType〔仅 aggregate〕→ 第 2 代 lateDataOutputTag → 第 3 代 maxDelay/emitWindowEvents/windowEventsOutputTag → 第 4 代 accumulationMode），每代 default 丢弃新参。
- `WindowOperatorFactoryImpl.java`（现位于 `core/operators/windowing/`，WI7 搬入）**642 行**，覆写全部 17 个方法；所有覆写漏斗进私有 `configureBuilder(11 参)` + 4 个 builder 头（`builder.aggregate/reduce/apply/process`）；另承载 `inferAccumulatorType`、`warnIfUninferableElementType`、`createDummySerializer`、`inferWindowSerializer`、`TimeWindowSerializer`、`GlobalWindowSerializer` 六个私有助手/嵌套类。
- 生产调用点唯一：`WindowedStreamImpl.java` 4 处（apply :319、aggregate :339、reduce :358、process :376），头部统一取 `getEffectiveWindowAssigner()`（components 感知，:313/:331/:352/:370），尾部统一传 `lateDataOutputTag, maxDelay, emitWindowEvents, windowEventsOutputTag, accumulationMode`，另传 op 特有 function 与类型实参（keyClass 槽为 `(Class<K>)(Class<?>)Object.class` 强转，:322/:343/:361/:379）。flow 主代码**不经工厂**（`StreamModelDslBuilder`/`AdvancedTransforms` 走 `WindowedStream` fluent API）。
- **全仓 grep 证据：nop-stream 之外零代码消费者**（`IWindowOperatorFactory` 与 4 个 create 方法名在 nop-stream 目录外的 `.java` 命中数为 0；命中仅 docs-for-ai/source-anchors、ai-dev/design 与 analysis 文档）。flow 侧仅 2 个测试文件的 javadoc 文字提及。
- 测试面消费者（core）：`TestWindowedStreamAccumulationModeWiring`（CapturingFactory 实现 8 个方法并捕获第 4 代 mode 实参，5 用例）、`TestWindowOperatorFactoryAccumulationModeWiring`（6 处直接调用，覆盖多代重载）、`TestWindowRoundTripInvariant`（`DroppingAllowedLatenessFactory` 桩覆写 4 个基代方法并故意漏传 allowedLateness；8 个参数化执行行 + 1 个反例用例；判定组件 `WindowRoundTripAssertions` 以反射 `readField` 读 WindowOperator 字段；该文件 javadoc 另含陈旧行号引用 :120-121、:143 待顺手同步）、`TestWindowOperatorUnificationE2E`（5 处 `new WindowOperatorFactoryImpl()` + 1 处基代签名直调 :347）、`TestWindowedStreamFactoryFailFast`（经空 `StreamComponents`（工厂 getter 返回 null）构造 null-factory fail-fast 路径，不调 setDefaultFactory 也不调工厂方法）。
- 注入路径二条：`WindowedStreamImpl.setDefaultFactory`（静态 volatile）与 `StreamComponents.setWindowOperatorFactory`（:50 字段 + getter/setter）。二者签名不变即不受收敛影响。
- 门禁现状：`node ai-dev/tools/check-nop-stream-invariants.mjs inventory` **exit 0**；`gate-inventory.json` 对接口与实现类两个条目都只登记 4 个去重方法名（不记重载）——收敛后方法名不变，预期无需改表，以复跑 exit 0 验证。`invariant-catalog.md` §4.2 行号锚**已于 WI7 漂移**（非本计划才漂移）：接口行（catalog :207 记 ：22/:33/:44/:55）、WindowedStreamImpl 行（catalog :206 记 apply :186 等）及同行 I1 补表锚（trigger/evictor/allowedLateness/withComponents/transform）、WindowOperatorFactoryImpl 行（catalog :208 记 :31/:54/:77/:100）全部过期，需整行重钉。
- 文档面：`docs-for-ai/04-reference/source-anchors.md:272` STRM-019 为 WI8 变更预告文本；`ai-dev/design/nop-stream/window-design.md:27-28`（8 参数元组 round-trip 不变式 + 门禁陈述）与 :368（「工厂在 runtime 模块实现」——WI7 后已过时的遗留句）需同步。
- 销号对象：`ai-dev/backlog/nop-stream-production-roadmap.md` P2-6「IWindowOperatorFactory performative Class<...> 参数」（标题 :947，无 Status 行 = open）。
- 测试基线（WI7 收口钉，不得下降）：core **1952 pass / 0 fail / 1 skip（328 files）**、runtime **1195 / 0 / 10 skip（316 files）**、flow **315 / 0 / 0（66 files）**。
- 平台版本 2.0.0-SNAPSHOT；本计划编号 2420 为当前最大+1（2418/2419 已被并发会话的 nop-tools 与 tool-spi 计划占用）。

### 迁移裁定（Decision，路线图 126 行要求的显式裁定）

**不保留 `@Deprecated(forRemoval=true)` 旧接口适配层，直接替换为 WindowSpec 契约。** 依据：

1. 全仓证据：工厂接口与 create 方法在 nop-stream 之外零代码消费者；仓内全部消费者（1 生产 + 5 测试文件）随本计划同批迁移，deprecated 桥层将无任何调用者，成为死代码。
2. M2 里程碑语义是「17 方法接口**消失**」；保留 17 方法 deprecated 桥层与里程碑直接冲突。
3. 保留桥层即保留 default shim 的静默丢参失败模式——恰是本 WI 新增防蠕变测试要消灭的对象；桥层的 default 实现永远无法把新字段传给旧签名。
4. 平台 2.0.0-SNAPSHOT，nop-stream 为实验性模块，WI7 已同类硬替换（删除公开反射契约，无过渡层）。
5. 仓库先例（nop-ai-core chat API）的生命周期是 deprecated → 仓内迁移 → 物理删除（chat 包现已不存在）；本计划一步走完该生命周期。

## Goals

- `IWindowOperatorFactory` 方法数 17 → **4**（create{Aggregate,Reduce,Apply,Process}Operator，方法名保持），**零 default 方法**；每个方法首参为 `WindowSpec`，其余参数仅为操作函数与（仅 aggregate）accumulatorType。
- 新增不可变值对象 `WindowSpec<IN, K, W extends Window>`（`io.nop.stream.core.operators`，与接口同包），承载四代蠕变参数全集：windowAssigner / trigger / evictor / allowedLateness / keySelector / keyClass / elementType / lateDataOutputTag / maxDelay / emitWindowEvents / windowEventsOutputTag / accumulationMode（12 字段）；builder 构造，必填三项（assigner/keySelector/keyClass）缺失快速失败（`ERR_STREAM_NULL_ARG`），默认值与 `WindowedStreamImpl` 字段默认一致（allowedLateness=0、maxDelay=0、emitWindowEvents=false、其余 null）；支持从既有实例复制字段的 builder 入口（供测试反例桩改写单字段）。
- `WindowOperatorFactoryImpl` 从 642 行瘦身为 4 方法 + `configureBuilder(spec)` + 既有私有助手（助手与嵌套类保留，行为不变）。
- 两条路径的全量字段钉 + 变异探针：
  - 直调路径 `TestWindowSpecFieldTransfer`：自构全量 `WindowSpec` → 真实工厂 → 断言 12 字段 + 操作函数 + accumulatorType 全部到达 `WindowOperator`（复用 `WindowRoundTripAssertions.readField`）；探针目标为 `configureBuilder` 的 spec→builder 传递。
  - fluent 路径：`TestWindowedStreamAccumulationModeWiring` 的 CapturingFactory 扩展为记录整个捕获 spec 并断言 12 字段全量（覆盖 `buildSpec` 装配）；探针目标为 `buildSpec`。
- E4 双向销号 production roadmap P2-6；STRM-019 终稿描述；window-design.md 工厂契约节同步（含 :368 WI7 遗留句修正）；invariant-catalog.md §4.2 整行重钉。
- 三模块测试基线不下降（core 1952 / runtime 1195 / flow 315）。

## Non-Goals

- 不改 `WindowedStream` fluent 公开 API（trigger/evictor/maxDelay/emitWindowEvents/accumulationMode 等方法与语义零变化）。
- 不改 `WindowOperator` / `WindowOperatorBuilder` 的构造签名与行为（builder 仍从字段构算子；仅其上游工厂层换 spec 形态）。
- 不动 `WindowAssigner`/`Trigger`/`Evictor`/序列化器实现与窗口语义、状态布局、timer 触发（E1 行为保持）。
- 不消除 keyClass 占位 `Object.class` 强转与 dummy key serializer（P2-6 字面残余，销号时登记为 watch-only 残余，见 Phase 4 与 Deferred But Adjudicated）。
- 不处理 gate-inventory.json 之外的不变量表项；不执行 WI9-WI12 的任何内容。
- 不为 quickstart 模板或外部假想用户提供兼容层（Non-Goals 由上文迁移裁定第 1 条证据支撑）。

## Scope

### In Scope

- `nop-stream-core` main：新增 `WindowSpec.java`；改写 `IWindowOperatorFactory.java`；改写 `WindowOperatorFactoryImpl.java`；改写 `WindowedStreamImpl.java` 4 个调用点。
- `nop-stream-core` test：迁移 4 个工厂消费者测试；新增防蠕变测试（含 builder 校验与 copy-builder 断言）；`WindowRoundTripAssertions` javadoc 与断言面同步（8 参元组表述 → WindowSpec 契约表述）。
- `nop-stream-flow` test：2 个 javadoc 文字提及同步。
- 文档：`docs-for-ai/04-reference/source-anchors.md`（STRM-019）、`ai-dev/design/nop-stream/window-design.md`（:27-28、:368 及工厂节其余表述）、`ai-dev/audits/nop-stream-invariants/invariant-catalog.md`（§4.2 整行重钉）。
- roadmap：`nop-stream-production-roadmap.md` P2-6 销号（双向）、`nop-stream-structure-refactor-roadmap.md` WI8 + M2 状态翻转（mission-driver 解析器格式合规：单层全角括号、无嵌套/半角括号，翻转后复跑 `tools/mission-driver/src/roadmap-check.mjs` 确认 12 条目）。
- `ai-dev/logs/2026/10-11.md` 收口条目。

### Out Of Scope

- 任何 checkpoint / runtime / flow 主代码变更；`StreamComponents` 结构性变更（其字段类型随接口自动受益，无需编辑）。
- STRM-016/017/018 锚点（WI7 已收）；module-groups.md 11→13（WI12）。

## Execution Plan

### Phase 1 - WindowSpec 契约落地（core main）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/{WindowSpec.java, IWindowOperatorFactory.java}`、`.../operators/windowing/WindowOperatorFactoryImpl.java`、`.../datastream/WindowedStreamImpl.java`

- Item Types: `Feature`（WindowSpec 值对象）、`Fix`（17 方法蠕变接口与 642 行适配器——roadmap 已立项的结构缺陷）

- [x] 新增 `WindowSpec`：不可变、`Serializable`（显式 `serialVersionUID`）、全 final 字段、builder 构造、必填校验快速失败、默认值与 `WindowedStreamImpl` 现字段默认一致、支持从既有实例复制字段的 builder 入口；`toString` 供诊断；不实现 equals/hashCode（函数字段无值语义）
- [x] 改写 `IWindowOperatorFactory`：保留类名与 `extends Serializable`；4 个方法、方法名不变、零 default、零类级 javadoc 预告残留；类 javadoc 记录契约（spec 承载配置全集、函数与 accumulatorType 为唯一伴随参数）与 2026-10-11 直替裁定
- [x] 改写 `WindowOperatorFactoryImpl`：4 个覆写，各自 `configureBuilder(spec)` + 原 builder 头（aggregate 保留 `inferAccumulatorType` + `warnIfUninferableElementType` 语义：aggregate/reduce 的 listStateBacked=evictor!=null，apply/process 恒 true；`createDummySerializer`/`inferWindowSerializer`/两个嵌套 Serializer 原样保留）
- [x] 改写 `WindowedStreamImpl` 4 个调用点：私有 `buildSpec(assigner)` 从既有字段装配 spec——assigner 必须来自调用点的 `getEffectiveWindowAssigner()`（components 感知），elementType 仍由调用点 `inferElementClass(getType())` 推断后入 spec；4 处调用换新签名；fail-fast 分支与消息文本保持不变
- [x] Phase 内编译闭环：`./mvnw -o -q compile -pl nop-stream/nop-stream-core`（此时 test 尚未迁移，不跑 test-compile）
- [x] 本 Phase 不更新 owner docs：文档统一由 Phase 4 收口（显式递延声明，非跳过）

Exit Criteria:

- [x] `IWindowOperatorFactory.java` 声明位 default 方法数为 0（`grep -nE '^\s+default\s'` = 0），方法声明计数 = 4（grep 可验，javadoc 中 2 处 `default` 为文字非方法）
- [x] `WindowSpec` 全部 12 字段存在且 final；builder 必填项缺失时抛 `StreamException(ERR_STREAM_NULL_ARG)`（Phase 3 显式测试项承接）
- [x] `WindowedStreamImpl` 生产代码中不再出现 `lateDataOutputTag, maxDelay, emitWindowEvents` 五参尾块直传（grep 可验）
- [x] **无静默跳过**：WindowSpec 无空方法体/placeholder；4 个工厂方法全部完整实现

### Phase 2 - 测试面迁移（core + flow test）

Status: completed
Targets: core 5 个测试文件、flow 2 个 javadoc

- Item Types: `Fix`（测试随契约迁移）

- [x] `TestWindowedStreamAccumulationModeWiring`：CapturingFactory 收敛为 4 方法实现，捕获对象从第 4 代 mode 实参改为整个 spec（既有 5 用例断言 `spec.getAccumulationMode()`，含 `undeclaredModeStaysNullAtCallSite` 的 null 直达语义）；并为捕获 spec 增加全量 12 字段断言（fluent 路径钉，探针承接点）——其中新增一个全字段非默认 fluent 用例（trigger/evictor/allowedLateness/maxDelay/sideOutputLateData/emitWindowEvents/accumulationMode 全设非默认值），使 12 字段断言对任意字段皆非弱断言（复核建议落地）
- [x] `TestWindowOperatorFactoryAccumulationModeWiring`：6 处直调改为 spec 构造 + 新签名；三档判别（显式 DISCARDING/ACCUMULATING/null→ACCUMULATING 默认）语义保持
- [x] `TestWindowRoundTripInvariant`：`DroppingAllowedLatenessFactory` 改为复制 spec 并改写 allowedLateness 为漏传值（0）后委托真实实现——反例语义保持（判定 helper 必须仍能抓住）；本文件与 `WindowRoundTripAssertions` 的 javadoc 同步：8 参元组映射表改 WindowSpec 契约表述、陈旧行号引用（:120-121、:143 一类）一并修正
- [x] `TestWindowOperatorUnificationE2E` :347 直调改 spec 形态；其余 4 处仅 `new WindowOperatorFactoryImpl()` 不受影响
- [x] flow `TestAdvancedPipelineE2E` / `TestAdvancedTransforms` javadoc 文字同步（提及工厂契约处改为 WindowSpec 表述）
- [x] `TestWindowedStreamFactoryFailFast` 预期无需改动（走空 `StreamComponents` null-factory 路径，工厂签名变更不触及；若编译/断言受影响则修至原语义）
- [x] 本 Phase 不更新 owner docs：文档统一由 Phase 4 收口（显式递延声明，非跳过）

Exit Criteria:

- [x] `./mvnw -o test-compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-flow` 通过（全仓无旧签名残留引用）
- [x] 迁移后测试用例数不少于迁移前（5 + 6 + 9〔8 参数化 + 1 反例〕+ 4 个既有用例全部保留语义，仅形态迁移）
- [x] `grep -rn "lateDataOutputTag, maxDelay, emitWindowEvents" nop-stream/*/src/test` 无旧五参尾块残留

### Phase 3 - 防蠕变钉 + 行为保持验证（E1）

Status: completed
Targets: 新增 `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/operators/windowing/TestWindowSpecFieldTransfer.java`

- Item Types: `Feature`（新钉）、`Proof`（基线与探针）

- [x] 新增 `TestWindowSpecFieldTransfer`：
  - 直调路径：构造全量 WindowSpec（非 null evictor、allowedLateness/maxDelay 非零、lateDataOutputTag、emitWindowEvents=true + windowEventsOutputTag、accumulationMode=DISCARDING、elementType 非 Object.class）→ 真实 `WindowOperatorFactoryImpl` 四个方法各建一个算子 → 断言 12 字段 + 操作函数全部到达 `WindowOperator`（复用 `WindowRoundTripAssertions.readField`）
  - aggregate 断言口径（钉死）：accumulatorType 传 `Object.class` → 期望 `accClass == createAccumulator()` 推断类（P1-01 口径）；`spec.elementType` 的到达断言走 `ListStateDescriptor` valueType（+evictor 路径）；reduce 路径无推断，valueType 直落 accClass 与 ListStateDescriptor valueType
  - builder 校验：assigner/keySelector/keyClass 三必填缺失各一断言（`StreamException(ERR_STREAM_NULL_ARG)`）+ copy-builder 复制全字段一断言
- [x] 变异探针×2（R5 纪律，恢复后以 grep/字节比对断言可执行行真实存在）：
  - 探针 A：破坏 `WindowOperatorFactoryImpl.configureBuilder` 单字段 spec→builder 传递（如丢 maxDelay）→ `TestWindowSpecFieldTransfer` 必红 → 恢复
  - 探针 B：破坏 `WindowedStreamImpl.buildSpec` 单字段装配（如丢 accumulationMode）→ `TestWindowedStreamAccumulationModeWiring` 全量断言必红 → 恢复
- [x] 三模块基线：core **1963/0/1**（≥1952 达成；+11 = 新钉 +7〔SpecFieldTransfer 6 + fullSpec 1〕+ WI7 quiet 日志不可逐类对账的口径差 +4，钉按「不低于 1952 且零 fail」下限口径成立）、runtime **1195/0/10** 精确持平、flow **315/0/0** 精确持平
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs inventory` exit 0（gate-inventory 方法名集不变的实证）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0
- [x] 本 Phase 不更新 owner docs：文档统一由 Phase 4 收口（显式递延声明，非跳过）

Exit Criteria:

- [x] 防蠕变测试全绿且两个探针证明其能抓住各自路径的丢参（探针 A：4 红/maxDelay；探针 B：5 红/accumulationMode；字节级恢复 + grep :101/:327 复验）
- [x] 三模块测试基线全部不下降（端到端口径：`WindowedStreamImpl` fluent 入口 → buildSpec → 工厂 → `WindowOperatorBuilder` → `WindowOperator` 的完整链路由既有 E2E（TestWindowOperatorUnificationE2E、TestWindowRoundTripInvariant、runtime 窗口 E2E 族）持续覆盖为绿——这是本收敛的接线验证）
- [x] inventory 门禁 exit 0

### Phase 4 - 文档同步 + E4 双向销号 + roadmap 翻转

Status: completed
Targets: source-anchors.md、window-design.md、invariant-catalog.md、两份 roadmap、daily log

- Item Types: `Fix`（owner-doc drift）

- [x] `docs-for-ai/04-reference/source-anchors.md` STRM-019 行改为终稿描述（WindowSpec 单一契约，路径不变）
- [x] `ai-dev/design/nop-stream/window-design.md`：:27-28 round-trip 不变式与门禁陈述改为 WindowSpec 契约表述（两条路径防蠕变测试入注）；:368「工厂在 runtime 模块实现」修正为 core 同模块直构（WI7 遗留句一并修正）；工厂节其余表述同步
- [x] `ai-dev/audits/nop-stream-invariants/invariant-catalog.md` §4.2 整行重钉（接口行、WindowedStreamImpl 行含 I1 补表锚、WindowOperatorFactoryImpl 行；新行号以收敛后实测为准）
- [x] production roadmap P2-6 补 Status 行，格式对齐既有条目：`- **Status**: ✅ Closed (E4 联动 plan 2420, 2026-10-11) — 17 方法 performative Class 参数蠕变面（四代 default shim）随 WindowSpec 单一契约消亡；字面残余（keyClass 占位 Object.class 强转 + dummy key serializer）登记为 watch-only 已知限制（无行为缺陷记录，见 plan 2420 Deferred 节）；双向记录见 structure-refactor roadmap WI8`
- [x] structure roadmap：WI8 状态行 `todo`→`done`（plan 2420、日期、关键交付注记，全角单层括号合规）；M2 里程碑行 `todo`→`done`；`node tools/mission-driver/src/roadmap-check.mjs` 复跑确认 12 条目不丢
- [x] `ai-dev/logs/2026/10-11.md` 顶部新增 WI8 收口条目
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本计划触及文件 0 断链（plan 自身 1 处相对路径 warning 已修）；剩余 1 error + 2 warnings 全部归属并发 nop-ai 会话未提交文件（nop-ai-structure-refactor-roadmap.md:88、plan 2421 ×2），已登记归因不入本计划提交

Exit Criteria:

- [x] 两份 roadmap 双向记录齐备（P2-6 closed ↔ WI8 done 注记互指），无「一处 done 一处 todo」
- [x] source-anchors / window-design / invariant-catalog 与 live 代码一致（抽查：STRM-019 路径与描述、:368 工厂位置、§4.2 行号锚）
- [x] roadmap-check 解析 12 条目〔8 done〕、M2 行翻转（★ 行按既有解析器行为不进分母，与 WI1-WI7 一致）
- [x] daily log 条目已写

## Closure Gates

- [x] 17 方法接口与 13 个 default shim 全部消失（接口 4 方法、0 default〔声明位 grep=0〕；适配器 4 覆写 + configureBuilder 漏斗；closure audit A1/A3 PASS）
- [x] 行为保持：三模块测试基线不下降（core 1963/0/1、runtime 1195/0/10、flow 315/0/0，日志 _tmp/wi8-*-test.log；E1 钉 = 既有测试全绿，audit B8 PASS）
- [x] 两条路径防蠕变钉落地且双探针验证有效（audit B5/B6 PASS；探针证据见 Phase 3）
- [x] E4 双向销号完成（P2-6 Closed 行 + structure roadmap WI8/M2 done 互指；audit D12 PASS；roadmap-check 12 条目 8 done）
- [x] 受影响 owner docs 已同步（STRM-019 终稿、window-design 不变式+§10.1、invariant-catalog 三行重钉；audit D13/D14/D15 PASS）
- [x] 独立子 agent closure audit 完成且证据写入本 plan Closure 段（见下方 Closure Audit Evidence）
- [x] **Anti-Hollow Check**：closure audit 逐环节读码验证 fluent 入口 → buildSpec → 工厂 → configureBuilder → builder 头 → buildWindowOperator → new WindowOperator 调用链连通（audit C9 PASS：12 字段 10 无条件 + 2 条件全部落位；scan-hollow 0 findings）
- [x] `./mvnw -o test` 三模块全绿（分模块跑：core 1963/0/1、runtime 1195/0/10、flow 315/0/0，BUILD SUCCESS ×3）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2420-wi8-window-operator-factory-windowspec-convergence.md --strict` exit 0（关闭前终跑）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0（实现阶段与 closure audit 各跑一次均 0）
- [x] 路径限定提交（git commit -- 指定路径），不卷入共享工作树中其他会话的未提交改动；WI7 遗留未提交的 nop-benchmark-stream 3 文件 import 修正（audit E20 发现，编译已验证）以单独补提交收编，不入本计划提交

## Deferred But Adjudicated

### keyClass 占位 Object.class + dummy key serializer（P2-6 字面残余）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 纯 API 形态问题，无行为缺陷记录（dummy serializer 的 no-silent 契约已由既往修复收敛，见 WindowOperatorFactoryImpl javadoc）；消除它需给 fluent API 引入可选 TypeSerializer 注入面，属新功能设计，超出本 WI「接口收敛」的 scope；E4 裁定 P2-6 随 WI8 销号，残余随销号文本登记。
- Successor Required: `no`
- Successor Path: 如未来需要，归 production roadmap 新条目或 nop-stream 结构 roadmap 后续 WI。

## Non-Blocking Follow-ups

- 无剩余 plan-owned work。gate-inventory.json 的 24 条先存漂移 violations（`mjs all` 口径）归 WI12 重生成，与本计划无关（WI7 已裁决）。

## Closure

Status Note: WI8 全部四个 Phase 完成：接口 17→4 方法零 default（WindowSpec 单一契约）、642 行适配器瘦身为 4 覆写、双路径防蠕变钉 + 双探针实证、E4 双向销号 P2-6 与 M2 翻转、四处 owner docs 同步；三模块基线不下降（core 1963/0/1、runtime 1195/0/10、flow 315/0/0）。独立 closure audit 20 项全 PASS、无 Blocker、无 only-partial landing。M2 里程碑达成。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（general-purpose，fresh session，agent_9c859211-0e63-4eb7-aea7-6e3a182e1f52）
- Audit Session: agent_9c859211-0e63-4eb7-aea7-6e3a182e1f52
- Evidence:
  - A 组（接口收敛 live code）A1-A4 全 PASS：接口恰 4 方法（:37-56）零 default；WindowSpec 12 final 字段 + 三必填快速失败 + copy-builder（:46-57/:182-190/:86-97）；实现 4 @Override + 漏斗 + 六助手保留；buildSpec 12 字段装配与 4 调用点 + fail-fast 消息保留（:314-329/:341/:355/:372/:386）。
  - B 组（测试面）B5-B8 全 PASS：TestWindowSpecFieldTransfer 6 用例经 readField 真断言；fluent 全字段非默认钉存在；反例桩复制 spec 改写；三模块日志实测 core 1963/0/1、runtime 1195/0/10、flow 315/0/0 且含新测试类运行记录。
  - C 组（Anti-Hollow）C9-C11 全 PASS：调用链逐环节读码连通（configureBuilder 12 字段 10 无条件 + 2 条件全落位 builder）；warnIfUninferableElementType 四调用口径一致；新增代码无空方法体/静默 no-op。
  - D 组（E4 双向销号与文档）D12-D16 全 PASS：P2-6 Closed + WI8/M2 done 互指；STRM-019 终稿；window-design 无 runtime 工厂残留句；invariant-catalog 行号与 live 一致；daily log 条目在位。
  - E 组（门禁复跑）E17-E20 全 PASS：inventory exit 0；scan-hollow 0 findings；checklist 工具预关闭态 12 未勾均为关闭动作本身；git status 与计划文件清单精确吻合（附注：nop-benchmark-stream 3 文件为 WI7 遗留未提交 import 修正，单独补提交收编）。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2420-wi8-window-operator-factory-windowspec-convergence.md --strict` 终跑 exit 0（关闭后补录）。

Follow-up:

- no remaining plan-owned work。WI7 遗留的 nop-benchmark-stream 3 文件 import 修正随本 plan 收尾以独立补提交落地（编译已验证，非本 plan scope 内 defect）。
