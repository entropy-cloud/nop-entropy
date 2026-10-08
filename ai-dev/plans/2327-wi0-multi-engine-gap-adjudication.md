# 2327 WI0 五引擎对比缺口裁定门（go/defer/exclude 分类确认 + 三类清单写死）

> Plan Status: completed
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI0（M0 裁定门）+ `ai-dev/analysis/2026-10/2026-10-06a-nop-stream-vs-flink-tdengine-hazelcast-seatunnel-comparison.md`（P0/P1/P2 与不跟进清单）
> Related: `ai-dev/design/nop-stream/00-vision.md`（§四/§七 #G36）、`ai-dev/design/nop-stream/cdc-design.md`（schema evolution defer）、`ai-dev/backlog/nop-stream-productization-roadmap.md`（D-GAP P-REQ-15）、WI1..WI19（本 roadmap 全部下游 WI 的范围来源）

## Purpose

把五方对比分析核定缺口逐条裁定为 go/defer/exclude 并写死不跟进三类清单，回写 `ai-dev/design/nop-stream/00-vision.md` 裁决记录与对应设计文档，为本 roadmap WI1..WI19 提供唯一裁定依据（Kafka EO 边界、debezium B 层例外处置、两代归一迁移范围、WI7 落点、WI11 形态、WI15 defer 处置）。

## Current Baseline

- roadmap `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md`（v2，2026-10-06）已立项：20 个 WI（WI0..WI19），WI0 为唯一裁定门，deps 是唯一并行屏障。
- 对比分析 `2026-10-06a` 已核定 P0×6 / P1×9 / P2×5 与不跟进三类清单，但各项 go/defer/exclude 的正式裁定与回写未做。
- `ai-dev/design/nop-stream/00-vision.md` §四 Non-Goals 六行 + §七 #G36（BroadcastState 永久排除）现行有效；roadmap Rule 3 规定其解禁唯一通道 = WI0 且须 owner 人审。
- `ai-dev/design/nop-stream/cdc-design.md` schema evolution DDL 协调 defer 现行有效，触发条件 = 首个真实需求；roadmap 规定 WI15 仅在 WI0 撤销该 defer 后启动。
- productization D-GAP P-REQ-15：K8s defer（revisit 条件维持）、YARN/HPA/RuntimeTopology exclude——roadmap Rule 3 规定本 roadmap 不改其状态。
- `docs-for-ai/03-modules/nop-stream-connectors.md` Source API 冻结政策：`SourceFunction` 为 legacy 冻结代，已裁定例外 = debezium-cdc B 层表路由（`SubtaskShardedSourceFunction`）；例外撤销/迁移归属由 WI0 裁定（roadmap WI2 行）。
- 8 内建连接器工厂 + SPI registry（`io.nop.stream.core.connector.registry`）+ conf-validate/dry-run 已交付；Kafka 目前仅经 `nop-message-kafka` at-least-once 消息通道承载（`MessageSourceFunction`/`MessageSinkFunction`），无原生 EO 连接器。
- sql roadmap 31/31 done：静态维表 lookup join（`ITableLookup` + `IJdbcTemplate` + DSL 全链 + restore 缓存命中）已交付——WI11 的基线。

## Goals

- 对比分析 P0×6 / P1×9 / P2×5 逐条裁定 go/defer/exclude，与 roadmap WI1..WI19 一一对应或显式延期。
- 不跟进三类清单（A 类 vision §四/#G36、B 类 productization D-GAP、C 类 analysis-scope 排除）逐条确认写死。
- 六项下游范围裁定落档：Kafka EO 与 nop-message-kafka 边界、debezium B 层例外处置、两代归一迁移范围、WI7 元数据落点、WI11 形态与范围、WI15 defer 处置。
- 裁定结果回写 `00-vision.md` 裁决记录（按 #G36 先例格式）与 connectors.md 冻结政策注记，roadmap 留 v3 版本注记。

## Non-Goals

- 不推翻任何既有裁定：vision §四 六行与 #G36 零改动（不解除任何排除）、productization D-GAP 零状态变更、cdc-design defer 零撤销。
- 不实施任何 WI1..WI19 的代码变更（各 WI 以独立 plan 承载）。
- 不改写 roadmap WI 行内文（Rule 3：版本注记留痕，不就地改写）。
- 不重议 Follow-up Backlog 已登记项（CEP×RocksDB、message sink checkpoint、2PC 跨并行度等维持既有触发条件）。

## Scope

### In Scope

- 新建裁定记录 `ai-dev/design/nop-stream/multi-engine-gap-adjudication.md`（P0/P1/P2 逐条三态 + 三类清单 + 六项范围裁定 + 授权链）。
- `ai-dev/design/nop-stream/00-vision.md` §七裁决记录新增 WI0 条目（边界零变更的确认性记录）。
- `docs-for-ai/03-modules/nop-stream-connectors.md` Source API 冻结政策节：B 层例外随 WI2 迁移撤销的注记。
- roadmap 头部 v3 版本注记（裁定落档指针）。
- `ai-dev/logs/2026/10-07.md` 日报条目。
- roadmap Work Item Status WI0 checkbox 勾选（独立 closure audit 通过后）。

### Out Of Scope

- WI1..WI19 的任何实施（各自独立 plan）。
- productization F 表销号（WI1/WI19 closure 时回写）。
- 任何代码、测试、构建配置变更。

## Execution Plan

### Phase 1 - 裁定草案与裁定记录落档

Status: completed
Targets: `ai-dev/design/nop-stream/multi-engine-gap-adjudication.md`、`ai-dev/design/nop-stream/README.md`（登记行）

- Item Types: `Decision`

- [x] 撰写裁定记录：P0×6 / P1×9 / P2×5 逐条三态表（含 roadmap WI 对应关系；P1-8 一条分析条目显式拆映射 WI9+WI10、P2-18 显式折入 WI11、P2-19/P2-20 标注为「确认既有处置」而非重议）；不跟进三类清单逐条确认；六项下游范围裁定（Kafka EO 边界 = 新增专用连接器模块、nop-message-kafka at-least-once 通道维持现状；debezium B 层例外 = WI2 迁移完成前维持、迁移后由 split 路由承接撤销；两代归一 = message/debezium-cdc/batch-loader 三个 legacy source 全量迁 FLIP-27；WI7 落点 = StreamRecord 引擎级可选变更元数据（RowKind + tableId，缺省 append 语义零回归）；WI11 形态 = 复用既有 lookup join 路径 + 维表本地物化 + 后台有界并发刷新，不新增异步算子（vision「异步算子」Non-Goal 维持不变；roadmap WI11 行 Deliverable 中的「形态裁定记录」由 WI11 plan 引用本裁定记录满足，WI11 不重开形态问题）；WI15 = cdc-design defer 维持、触发条件未触发、本周期显式延期；WI15 行内「协调形态（checkpoint 阻塞 vs fail-fast）二选一」不预裁，随 defer 撤销时的 owner 裁定作出）。
- [x] 授权链声明：本裁定依据用户 2026-10-07 对 roadmap 的执行指令（「建立一个 worktree，执行 nop-stream-multi-engine-comparison-roadmap 直到彻底完成，每个工作项按照 plan guide 拟制计划完成，每完成一个计划自动提交一次」，ZCode session 载体，当日日报 `ai-dev/logs/2026/10-07.md` 承载指令上下文）在 WI0 门内作出；全部裁定方向与 roadmap 既有文本预设方向一致（零推翻、零解禁、零 defer 撤销），未来任何边界变更（per-record 异步 lookup、broadcast state 重启、schema evolution defer 撤销、K8s defer 变更）均须按既有通道 owner 人审另议。
- [x] 在 `ai-dev/design/nop-stream/README.md` 登记新设计文档条目（子目录房规）。

Exit Criteria:

- [x] 裁定记录文件存在且覆盖对比分析全部 P0/P1/P2 条目与三类清单，每条含裁定、依据、下游归属
- [x] 全部裁定方向与 roadmap 既有文本一致性核对通过（无一条推翻既有裁定文本）
- [x] No owner-doc update required（本 Phase 产物即设计文档；owner doc 更新归属各实施 WI）

### Phase 2 - 回写 vision / connectors.md / roadmap 版本注记

Status: completed
Targets: `ai-dev/design/nop-stream/00-vision.md`、`docs-for-ai/03-modules/nop-stream-connectors.md`、`ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md`

- Item Types: `Decision`

- [x] `00-vision.md` §七裁决记录新增 WI0 条目：按 #G36 先例格式，记录「五引擎对比缺口裁定（WI0，2026-10-07）——§四 六行与 #G36 边界维持不变、per-record 异步 lookup 算子与 broadcast state 维持排除（措辞限定为 per-record 异步 lookup 算子，不否定 WI11 物化+后台刷新形态落在 Non-Goal 内）、WI11 形态见裁定记录」，指向裁定记录文件
- [x] connectors.md Source API 冻结政策节追加一句：B 层表路由例外随 WI2 两代归一迁移撤销（WI0 裁定 2026-10-07，迁移完成前例外维持）
- [x] roadmap 头部追加 v3 版本注记：WI0 裁定落档指针（指向裁定记录文件），不改动 WI 行内文
- [x] `ai-dev/logs/2026/10-07.md` 新建当日日报并写入条目（含用户执行指令上下文与裁定摘要）

Exit Criteria:

- [x] 三处文档回写完成且相互一致（vision 条目、connectors 注记、roadmap v3 注记均指向同一裁定记录）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/2026/10-07.md` 已更新

### Phase 3 - Closure audit 与 roadmap 勾选

Status: completed
Targets: roadmap Work Item Status WI0

- Item Types: `Proof`

- [x] 独立子 agent closure audit（fresh session，与实现不同 task）：核对裁定记录覆盖面、三处回写一致性、零推翻声明真实性
- [x] roadmap WI0 checkbox 勾选
- [x] git commit（docs/nop-stream: WI0 裁定落档）

Exit Criteria:

- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI0 已勾选
- [x] 提交完成（commit message 按仓内惯例 `docs(ai-dev):` 风格）
- [x] No owner-doc update required（本 Phase 为 audit/勾选/提交动作，文档更新已在 Phase 2 完成）

## Closure Gates

> 纯文档计划（仅修改 `ai-dev/` 与 `docs-for-ai/` 文档，无任何代码变更，符合 guide 纯文档豁免口径）：`./mvnw compile` / `./mvnw test` / checkstyle 条目按 guide 规则删除，代之以文档工具门禁。

- [x] 裁定记录覆盖对比分析全部条目且三态无遗漏
- [x] 不跟进三类清单写死且与 roadmap Rules 7 同口径
- [x] vision / connectors.md / roadmap 三处回写一致
- [x] 不存在被静默降级的 in-scope 裁定项（每条 P0/P1/P2 均有三态归属）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] 独立子 agent closure-audit 已完成并记录证据

## Closure

Status Note: WI0 裁定门完成：P0×6/P1×9/P2×5 逐条三态 + 不跟进三类清单写死 + 六项下游范围裁定（R1..R6）落档 `ai-dev/design/nop-stream/multi-engine-gap-adjudication.md`；vision §七 #WI0 条目、connectors.md 冻结政策注记、roadmap v3 注记三处回写一致；全部裁定零推翻既有裁定文本（独立审计以 git diff 逐 hunk 核验）。roadmap WI0 checkbox 已勾选。
Completed: 2026-10-07

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_f3a5a770-8e78-4dc2-8c1a-839b8735d654，与实现会话隔离）
- Audit Session: agent_f3a5a770-8e78-4dc2-8c1a-839b8735d654（另有 plan 审查两轮：agent_237f61b8-0d18-4258-8da3-03719e21d1bc 初审 9 Minor 全修补、agent_5ece8329-d1b3-4a85-b123-9336b214a5a6 复审 8/8 PASS）
- Evidence:
  - A1 覆盖面 PASS：分析第四节 20/20 条全覆盖（P0 L16-21/P1 L28-35/P2 L41-45），每条三态+依据+归属
  - A2 三类清单 PASS：A 类 7 项、C 类 5 项与 Rules 7 同口径（B 类含 Web 控制台超集行，加强性扩展无冲突）
  - A3 三处回写一致 PASS：vision #WI0（00-vision.md:113-115）、roadmap v3（:3）、connectors.md（:60）指向同一裁定记录
  - A4 docs-for-ai 边界 PASS：connectors.md 注记不直接链接 ai-dev 路径
  - A5 日报 PASS：ai-dev/logs/2026/10-07.md 承载指令上下文与裁定摘要
  - 零推翻核验 PASS：git diff 978b397673 逐 hunk——vision 仅新增 #WI0 条目、cdc-design 零改动、productization roadmap 零改动、R1..R6 均在 roadmap 预设选项集内
  - Anti-Hollow PASS：20 条裁定依据锚定实测事实，R6 显式拒绝预裁为诚实留白
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（closure 回填后全勾选）
  - 审计 Minor 4 条均不阻塞：B 类超集行（加强性）、v3 注记措辞（勾选后对齐）、owner 确认条件式解读（审计报告即记录）、提交卫生（已执行：dependency-reduced-pom.xml 还原、_baseline-build.log 移入 _tmp/）
- Deferred 项分类检查：无 in-scope live defect 被降级；R6 WI15 延期为裁定性延期（defer 维持），非本 plan scope 内缺陷

Follow-up:

- no remaining plan-owned work（WI15 本周期延期的处置归属 roadmap 状态通道，非本 plan 遗留）
