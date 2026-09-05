# 3 rocksdb / flow / fraud-example 三模块审计（roadmap item 11）

> Plan Status: completed
> Last Reviewed: 2026-09-01
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 11（Phase M 第五个审计项，deps: item 6 done）；Phase M 审计统一模式（roadmap Work Items 分组说明）
> Related: `2026-09-01-0938-1-design-productization-gap-analysis.md`（item 6，前置依赖，其 §3.1 产出 item 11 审计重点三项：RocksDB segment checksum 填充核验 / flow XDef 校验完备性 / fraud-example 完整度评估）；`2026-09-01-0938-2-core-module-audit.md`（item 7，sibling 审计：方法论与报告结构复用；其 §2.1 ① 已核 `SerializerFingerprint.schemaVersion` core 侧传播路径——结论「core 侧无阻碍，字段落地增量全在 runtime manifest 层」与本 plan 的 rocksdb 增量路径核验衔接；其 §1.1 §7 行空壳模块结论本 plan 引用不重复核验；其 Follow-up item 22 含 flow 1 个 test 文件通配符导入，本 plan 路由不修）；`2026-09-01-0938-3-runtime-module-audit.md`（item 8，sibling 审计：2300-2 rocksdb 侧归本 plan 复核；其 §2.6 checkpoint 存储健壮性抽查与 P-REQ-20 四分项证据衔接）；`2026-09-01-1457-1-cep-module-audit.md` / `2026-09-01-1457-2-connectors-module-audit.md`（items 9/10，前序 siblings，执行顺序在本 plan 之前）
> Mission: nop-stream-productization
> Work Item: roadmap item 11

## Purpose

对 `nop-stream-rocksdb` / `nop-stream-flow` / `nop-stream-fraud-example` 三模块按产品标准完成审计并收口：验证 2026-05-20 / 2026-06-30 两份历史审计中三模块相关发现的整改收口、复核 2026-08-04-2300-2 remediation plan 的 rocksdb 侧修复点 live 存在性、执行产品化视角新增审计（D-GAP 下发的三项重点逐条消化）、复查 RocksDB 状态后端健壮性、flow DSL 编译器与 XDef 合同、fraud-example 作为产品示例的完整度；小缺陷就地修复（含回归测试），大缺陷转为 roadmap Follow-up 工作项。本 plan 是 Phase M 收官项（closure 后 M2 解锁）。

## Current Baseline

（2026-09-01 live 核对）

- `nop-stream/nop-stream-rocksdb`：17 个 main / 13 个 test Java 文件（`find -name "*.java"` 计数）——核心类 `RocksDBKeyedStateBackend`、`RocksDBKeyEncoder`（key-group layout v2）、`RocksDBSnapshotSerDe`、增量快照策略（`RocksDBIncrementalSnapshotStrategy`，D-GAP §1.1 P-REQ-20 证据锚点：SHA-256/contentHash 命中）、State TTL、状态迁移
- `nop-stream/nop-stream-flow`：73 个 main / 21 个 test Java 文件——XDSL 声明式编排 + Delta 定制（05-20 §7 时点空壳，现**已实现**——模块级结论由 item 7 §1.1 §7 行落定「勿重复立项」，本 plan 审计其内容质量：DSL 编译器与 XDef 合同）
- `nop-stream/nop-stream-fraud-example`：10 个 main / 5 个 test Java 文件——`FraudDetectionDemo` + 4 个 pattern（RapidTransaction/AccountTakeover/GeographicAnomaly/UnusualAmount）+ `DemoKeyedStateStore`（06-30 §2.1 记载 UOE 桩 `getReducingState/getAggregatingState`「demo 简化」低severity）+ `MockTransactionGenerator`
- 2026-05-20 duplicate-code audit：三模块无专属组（§7 空壳模块之 flow 已由 item 7 统一核验为「已实现」；rocksdb/fraud-example 无组）——本 plan Phase 1 显式记录归属核对，引用 item 7 §1.1 §7 行不重复核验
- 2026-06-30 code audit 三模块相关发现 seed 清单（本 plan Phase 1 核对基准）：§1.1 模块统计（fraud-example 10/5）+ §2.1 UOE 桩之 `DemoKeyedStateStore.getReducingState/getAggregatingState`（fraud demo，低）+ §3.1 测试覆盖（fraud-example 5 类 25 @Test，比 0.50）+ §2.2 通配符导入（live 复核：rocksdb test 4 / fraud-example test 5 文件残留；main 侧全模块已清零）+ §6.2 #6 空壳模块治理（06-30 时点 4 空壳——live 已由 item 7 核收口）；**派生规则**：报告中其余发现按「涉及文件位于三模块」准则纳入核对表
- 2026-08-04-2300-2 remediation plan（checkpoint-state-backend-cep-correctness）**rocksdb 侧修复点**：`RocksDBKeyedStateBackend.restoreState` 两条增量分支补 `RocksDBKeyEncoder.verifyKeyLayoutVersion(snapshotData, true)` fail-fast（防 legacy v1-layout SST 静默数据丢失）+ 配套测试（persist 失败分支修复点全在 runtime `CheckpointCoordinator`，item 8 报告 §1.3 已复核——rocksdb 侧无该修复点）——**本 plan 复核 rocksdb 侧 live 存在性**（2300-1/3 预期无 rocksdb/flow/fraud 侧修复点，执行时逐条核对确认）
- D-GAP 报告 §3.1 item 11 审计重点（三项，本 plan Phase 2 逐条消化）：① **`RocksDBIncrementalSnapshotStrategy` segment `checksum`/`schemaVersion` 实际填充核验**——每个增量 segment 是否都带有效 SHA-256 contentHash（P-REQ-20「segment 级 landed」结论的端到端真实性；core 侧 `StateSegmentDescriptor` 字段存在已由 D-GAP §1.1 锚定，本 plan 验证填充路径的真实性与覆盖率）；② **flow 模块 XDef 校验链路对连接器配置段的字段级校验完备性**（与 item 10 重点 ② 的 flow 侧消费路径衔接）；③ **fraud-example 作为快速起步脚手架（P-REQ-25，item 17 输入）的完整度评估**——现有示例盘点 + 3 个入门拓扑缺口清单（item 12 场景设计输入：fraud-example 产品化程度评估）
- 前序 production roadmap 已 shipped 的三模块能力（roadmap Current baseline）：RocksDB 状态后端 + 增量 checkpoint + State TTL + 状态迁移 + Key-Group rescale（含离线 reshard 工具）；XDSL 声明式编排 + Delta 定制
- core 侧衔接锚点（item 7 已核）：`SerializerFingerprint.schemaVersion` 恒 1 预留、指纹传播路径唯一构造点 `StateSchemaResolver:132` → 经 `rocksdb/RocksDBSnapshotSerDe` 持久化——本 plan 核验 rocksdb 侧接收端语义
- item 7 报告 §2.2：Flink/Beam 8 格 Phase M 级裁定一次落定（全部无需补评），本 plan 引用不重复裁定
- 工具：`ai-dev/tools/scan-hollow-implementations.mjs`（消息语义分级版；high/critical 退出码非 0）；`ai-dev/tools/check-nop-stream-invariants.mjs`（CI fail-fast 门禁）；多行 UOE 人工补审沿 items 7/8/9/10 同一基线
- 验证基线：roadmap Cross-cutting concerns 约定 `./mvnw test -pl nop-stream -am -T 1C` 全绿

## Goals

- 整改收口验证：05-20 归属核对（三模块无专属组，显式记录；flow 空壳→已实现引用 item 7 结论）+ 06-30 三模块相关发现（seed 清单 + 派生规则所得项）逐项核对 live 状态（landed / partial / regressed），每项有证据指针；2300-2 rocksdb 侧修复点复核 + 2300-1/3 归属核对
- 产品化新增审计：D-GAP item 11 三项重点逐条消化（含勾销清单无遗漏）——①增量 segment checksum/schemaVersion 填充真实性与覆盖率核验（抽样或全量、含异常路径：checksum 计算失败时的行为）；②flow XDef 校验链路对连接器配置段的字段级校验完备性结论；③fraud-example 完整度评估报告（现有示例盘点 + 3 个入门拓扑缺口清单，供 items 12/17 消费）+ 三模块核心路径审计（RocksDB 后端健壮性：SST 生命周期/句柄释放/异常恢复；flow DSL 编译器与 XDef 合同：编译错误报告质量/Delta 合并语义；fraud-example 代码健康度）+ hollow scan（三模块分别执行）+ 测试覆盖抽查
- 缺陷处置：单 plan 范围内可收敛的小缺陷就地修复（每个修复配 focused 回归测试）；跨模块/大规模缺陷转 roadmap Follow-up 工作项（编号顺延、来源标注本 plan）。小/大缺陷判定准则与 items 7—10 一致：修复限于三模块内、不改公共契约、无需新测试基建 → 小缺陷；否则 → 大缺陷转 Follow-up。**例外**：fraud-example 的大规模示例重写属 items 12/13 范围（roadmap item 11 stage details Out of scope），本 plan 只评估不重写
- 审计报告落地 `ai-dev/analysis/{执行当月}/`（遵循 `00-analysis-writing-guide.md`；结构对齐 items 7/8 报告）并完成 roadmap item 11 写回

## Non-Goals

- 跨模块重构（需 Follow-up 立项）
- 大规模示例重写（items 12/13 处理；本 plan 对 fraud-example 只做评估与最小修复）
- 修复位于 core/runtime 但影响三模块路径的发现——只记录并路由
- 实施 D-GAP go 裁定项的功能开发（manifest 级 stateFormatVersion/checksum 字段落地属 Follow-up item 25；本 plan 只核验 segment 级证据真实性）
- flow 1 个 test 文件通配符导入清理——路由 Follow-up item 22；rocksdb 4 / fraud-example 5 个 test 文件（live 计数）同路由 item 22（**item 22 现有枚举（core 169/runtime 108/cep 15/flow 1）未含 rocksdb/fraud 文件——系 item 7 时点部分计数，本 plan closure 写回时与 item 10 plan 的 connector 16 文件一并对 item 22 枚举做事实补全；若 mission owner 裁定属语义变更，则按 stop-edit-restart 提请，本 plan 不擅自扩 scope**）
- 重复裁定 Flink/Beam 8 格补评（item 7 报告 §2.2 已落定）、重核空壳模块结论（item 7 §1.1 §7 行已落定）
- cep/connectors 模块审计（items 9/10）
- 2026-05-20/2026-06-30 报告正文的历史重写

## Scope

### In Scope

- 审计与报告：`nop-stream/nop-stream-rocksdb/`、`nop-stream/nop-stream-flow/`、`nop-stream/nop-stream-fraud-example/` 三模块 + 新增审计报告一份（`ai-dev/analysis/{执行当月}/`，命名遵循 writing guide）
- 修复：三模块内小缺陷（含 focused 测试）
- 修改：`ai-dev/backlog/nop-stream-productization-roadmap.md`（Follow-up 追加 + Last updated + closure 后 item 11 写回 + item 22 枚举事实补全——见 Non-Goals 路由约定）
- 修改（条件性）：`ai-dev/tools/scan-hollow-implementations.mjs` 检测规则——仅当 hollow scan 误报经核实源于工具检测缺陷时允许修正
- 修改（条件性）：受修复影响的最小 owner-doc 同步（见 Phase 3；`ai-dev/design/nop-stream/state-management-design.md`、`stream-dsl-design.md` 若因修复产生事实漂移，仅做最小事实同步，不扩写、不重构）
- 只读输入：两份历史审计报告、2300-2 plan、D-GAP 报告、items 7/8 审计报告、`stream.xdef` schema、`~/sources` 竞品源码（Flink RocksDB state backend 如需对照）

### Out Of Scope

- 其余 7 个子模块的审计与修复
- `ai-dev/design/nop-stream/` 的结构性重写与历史回写
- item 12 复合场景设计（消费本 plan 的 fraud-example 评估输入，不由本 plan 设计场景）
- item 25 的 manifest 字段落地实施（本 plan 只提供 segment 级证据）

## Execution Plan

### Phase 1 - 历史审计整改收口验证

Status: completed
Targets: 三模块、审计报告 Phase 1 章节

- Item Types: `Proof`

- [x] 05-20 归属核对：逐组确认三模块无专属组（§7 flow 空壳→已实现引用 item 7 §1.1 §7 行结论），显式记录「无专属组」结论
- [x] 06-30 三模块相关发现逐项核对（seed 清单见 Current Baseline + 「涉及文件位于三模块」派生规则，执行时先落完整核对表再逐项核对，含 `DemoKeyedStateStore` UOE 桩现状；test 通配符导入 rocksdb 4 / fraud-example 5 文件归入核对表并路由 item 22——partial · 代码风格治理项，处置见 Non-Goals 路由约定）：landed / partial / regressed 三态 + 证据指针
- [x] 复核 2300-2 rocksdb 侧修复点 live 存在性（restoreState 两条增量分支的 verifyKeyLayoutVersion fail-fast + 配套测试），并确认 2300-1/2300-3 无 rocksdb/flow/fraud-example 侧修复点（Targets 逐条核对）
- [x] partial/regressed 项归类：按小/大缺陷判定准则进 Phase 3 或 Follow-up 候选，逐项记录理由

Exit Criteria:

- [x] 报告 Phase 1 章节含 05-20 归属核对结论 + 06-30 三模块发现完整核对表（先列全表再逐项核对，每行三态 + 证据指针，无「未核对」空行）+ 2300-2 rocksdb 侧复核结论（成立/不成立 + 抽查点）+ 2300-1/3 归属核对结论（报告 §1.1—§1.3：10 项核对表无 regressed，唯一 partial #3 路由 item 22；2300-2 双分支 :789/:801 live 成立；2300-1/3 零三模块修复点确认）
- [x] 全部 partial/regressed 项有归类结论（修复进 Phase 3 或 Follow-up 候选，附理由）（#3 路由 item 22 + closure 枚举补全；#2 unchanged+加固 FX-3；#5 过时记载由 Phase 2 闭合）
- [x] No owner-doc update required（纯核验章节）
- [x] No new test required: 纯核验章节，无代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 产品化视角新增审计

Status: completed
Targets: 三模块、审计报告 Phase 2 章节

- Item Types: `Proof`

- [x] 消化 D-GAP 重点 ①：`RocksDBIncrementalSnapshotStrategy` 增量 segment 的 `checksum`/`schemaVersion` 填充核验——每个增量 segment 是否都带有效 SHA-256 contentHash（填充路径追踪 + 实际快照产物抽样验证），异常路径行为（checksum 计算失败/部分填充时的 fail-fast 或降级语义），结论回填 P-REQ-20「segment 级 landed」的端到端真实性（报告 §2.1：task+coordinator 双侧无条件填充 + TestRocksDBIncrementalSnapshotStrategy 64-hex 断言 + 异常路径 fail-fast——结论成立）
- [x] 消化 D-GAP 重点 ②：flow 模块 XDef 校验链路对连接器配置段的字段级校验完备性——`stream.xdef` 中连接器配置段定义与 flow 编译器的消费路径核对（哪些配置项字段级校验、哪些透传未校验），结论与 item 10 重点 ② 的表衔接（item 10 报告路径从 roadmap item 10 done 写回记录获取；本 plan 负责 flow 编译器侧深审，消费 item 10 报告的连接器侧结论表避免重复劳动）（报告 §2.2 逐配置段归属表 + item 10 §2.2 表互证）
- [x] flow `_gen` 生成纪律核验：`flow/model/_gen/` 生成模型文件（源自 `stream.xdef`）无手改痕迹（06-30 §2.4「`_gen` 仅在 cep」的记载系 flow 当时空壳、现已过时——本项补齐该核验缺口，与 item 9 对 cep `_gen` 的核验标准对称）（报告 §2.3：clean——统一生成形态 + git 零修改 + 再生成链路 live）
- [x] 消化 D-GAP 重点 ③：fraud-example 完整度评估——现有示例盘点（4 pattern + demo 入口 + state store）、作为快速起步脚手架的可用性评估（能否从零跑通、依赖与配置透明度、README/注释质量）、3 个入门拓扑缺口清单（供 item 12 场景设计与 item 17 文档消费，缺口以「入门用户视角」定义）（报告 §2.4：盘点表 + 可用性准则表 + Gap A/B/C 自包含）
- [x] 三模块核心路径审计：rocksdb（SST 文件生命周期/句柄与 Options 释放/异常路径恢复/TTL 与增量快照交互）、flow（DSL 编译器错误报告质量/Delta 合并语义/与 core StreamModel 的契约一致性）、fraud-example（代码健康度/示例正确性）的重复代码复查、错误处理一致性、边界条件（报告 §2.5：rocksdb 执行者全量读审 + flow/fraud 双 subagent 深审经逐条复核，发现处置进 Phase 3/§3.2）
- [x] 对三模块分别运行 `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/<module> --severity high`，high/critical 发现逐条核实（真实空壳 vs 误报——`DemoKeyedStateStore` 类 UOE 按 guard/stub 语义分级核实）+ 多行 UOE 人工补审；误报处置路径：修正代码模式或修正工具检测规则——「仅记录不改」不合法（退出码 0 为不可降级硬门禁）（报告 §2.6：3×exit 0，DemoKeyedStateStore guard 语义成立，多行 UOE 人工补审零真实空壳——无误报需处置）
- [x] 测试覆盖抽查：三模块各自代表性测试命中确认 + 按行数 top-3 文件（模块小）的直接测试覆盖检查；明显缺口记录（报告 §2.7：top-3 表 ×3 + 修复新增覆盖）
- [x] closure 前对照 D-GAP §3.1 item 11 三条重点勾销清单确认无遗漏（报告 §2.8）

Exit Criteria:

- [x] segment checksum/schemaVersion 填充核验结论落地（D-GAP 重点 ①：填充路径 + 产物证据 + 异常路径语义；P-REQ-20 segment 级结论的真实性裁定）
- [x] flow XDef 校验完备性结论表落地（D-GAP 重点 ②：逐配置段的校验链路归属）+ flow `_gen` 生成纪律核验结论落地
- [x] fraud-example 完整度评估落地（D-GAP 重点 ③：现有示例盘点 + 3 个入门拓扑缺口清单，自包含供 items 12/17 消费）
- [x] hollow scan 三模块退出码记录 + high/critical 发现逐条核实结论 + 多行 UOE 人工补审结论（真实发现进 Phase 3 或 Follow-up；误报处置动作已执行，非仅记录）（无 high 发现、无误报——门禁达成）
- [x] 三模块核心路径审计发现逐条带源码锚点（含严重度与处置归类）
- [x] 测试覆盖抽查结论表落地（每模块代表性测试命中情况 + top-3 文件覆盖结论）
- [x] No owner-doc update required（发现与评估记录在报告；owner doc 变更属 Phase 3 修复的附带裁定）
- [x] No new test required: 纯审计章节，无代码变更（Phase 3 修复的测试要求在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 缺陷处置与收口

Status: completed
Targets: 三模块、`ai-dev/backlog/nop-stream-productization-roadmap.md`、审计报告收口章节

- Item Types: `Fix | Follow-up`

- [x] 就地修复全部归类为小缺陷的项：每个修复附 focused 回归测试（验证正确结果而非仅无异常）；修复不引入空壳/静默跳过；修复若落在 `_` 前缀生成文件，上移到源模型/模板层处理（生成文件禁改）（报告 §3.1：19 项——rocksdb RK-1..8 / flow FL-1..7 / fraud FX-1..5；15 个新 focused 用例；4 项显式豁免附理由；无 `_` 前缀文件触及）
- [x] 大缺陷逐项转为 roadmap Follow-up 工作项：按 Rules 追加到 Work Items 末尾（编号顺延、状态 todo、来源标注本 plan + 证据锚点），并更新 Last updated（或显式记录「无大缺陷」）；fraud-example 大规模改造路由 items 12/13（本 plan 只记录评估结论）；test 通配符导入 rocksdb 4 / fraud-example 5 文件按 Non-Goals 路由约定对 item 22 枚举做事实补全（connector 16 文件由 item 10 plan 先行补全——若其已执行则仅核对 9 文件已入枚举，不重复编辑）（报告 §3.2：Follow-up items 29/30 落库 + items 12/13 显式路由 + item 22 枚举补全（connector 16 已由 item 10 补全，本轮补 rocksdb 4 + fraud 5））
- [x] 触及状态快照/恢复语义的修复：至少一条端到端验证（snapshot → 恢复 → 重处理路径，rocksdb 增量快照下）；触及 flow 编译语义的修复：至少一条 XDSL 定义到执行的端到端验证（报告 §3.1 末段：既有 TestRocksDBSnapshotRestore 21 用例 + 增量族 4 测试承载恢复语义面；FL-3 配 timestampsWithoutAssignerExecutesWithoutNpe XDSL→执行 E2E）
- [x] 全量回归：`./mvnw test -pl nop-stream -am -T 1C` 全绿；`node ai-dev/tools/check-nop-stream-invariants.mjs` 通过（CI fail-fast 门禁）（868/0 + exit 0）
- [x] 审计报告 Status 定稿（resolved + 遗留清单），含全部发现→处置零丢失映射表（结构对齐 items 7/8 报告）（§2.5 采信清单 + §3.1 修复表 + §3.2 路由/watch-only/否决——零丢失映射）
- [x] 小缺陷修复若改变 live 行为且 owner doc（state-management-design.md / stream-dsl-design.md）受影响：同步最小 owner-doc 更新或显式记录 `No owner-doc update required`（§3.3 末段：No owner-doc update required，含理由）

Exit Criteria:

- [x] 全部 in-scope 小缺陷修复落地且各有 focused 测试（新增测试清单列明：每个测试验证了哪个新行为；纯清理类修复按 guide Rule #25 显式豁免并注明）（§3.1：TestRocksDBAuditFixes 4 用例（legacy 键/TTL 保持/WARN/损坏 marker）、TestStreamFlowAuditFixes 7 用例（fail-fast ×5 + 正控制 + NoOp assigner E2E）、TestFraudAuditFixes 4 用例（多用户钉定 ×3 + demo 冒烟）、TestGeographicAnomalyPatternFix 重写 2 用例；豁免：RK-1/RK-2（JNI 无公共可观察 API）、RK-6（JNI 异常注入无 mock 基建）、FX-2 失败路径（静态依赖替换超最小范围）、RK-7/FL-5/FL-7/FX-3/FX-4（日志/码化/纯文档，Rule #25））
- [x] 全部大缺陷已追加为 roadmap Follow-up 工作项（或显式记录「无大缺陷」）（items 29/30 + items 12/13 显式路由 + item 20 邻域）
- [x] **端到端验证**（如适用）：状态语义修复有 snapshot→恢复→重处理完整路径测试且通过；flow 语义修复有 XDSL→执行完整路径测试且通过（§3.1 末段）
- [x] **无静默跳过**：修复代码无空方法体/吞异常模式（hollow scan 复跑三模块退出码 0）
- [x] 修复改变 live 行为时：受影响 owner doc 已做最小事实同步，或显式记录 `No owner-doc update required`（含理由）（§3.3）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（868/0/9 gated）
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（报告新增后）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 2026-05-20（归属核对）与 2026-06-30（seed 清单 + 派生规则所得项）整改收口核对完成，无未处置的 partial/regressed 项；2300-2 rocksdb 侧复核结论落地；2300-1/3 归属核对结论落地（报告 §1.1—§1.3；closure audit A.1 PASS）
- [x] D-GAP item 11 三项重点全部消化且对照勾销清单无遗漏（segment 填充核验 / flow XDef 完备性 / fraud-example 评估含 3 缺口清单）（报告 §2.1—§2.4/§2.8；closure audit A.2 PASS）
- [x] 空壳扫描（三模块）high/critical 无未处置真实发现（误报已按处置路径消除，非仅记录）（3×exit 0，无误报需处置；closure audit A.2 独立复跑）
- [x] 小缺陷修复全部带 focused 测试；大缺陷全部 Follow-up 化（或显式无）；fraud-example 改造路由明确；test 通配符导入 9 文件的 item 22 枚举事实补全已落地（19 项修复 + 15 新用例 + 4+5 显式豁免附理由；items 29/30 落库；items 12/13 路由；rocksdb 4 + fraud 4 已入 item 22 枚举）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect（closure audit A.7 诚实性核查 PASS：watch-only 10 组逐条 Why-Not-Blocking；FX-1 如实定性为复制模板修复并记录 fix-revert 实验）
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required（§3.3 末段含理由）
- [x] `./mvnw compile`（随 `-pl nop-stream -am` 构建覆盖）（clean install -DskipTests BUILD SUCCESS）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（868/0/9 gated；closure audit 独立复跑三模块 93/81/29 零失败）
- [x] checkstyle / 代码规范检查通过（随 mvnw 构建；root pom checkstyle 配置整体注释的事实沿 items 7/8 先例按现状通过）
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-rocksdb --severity high` 及 flow、fraud-example 两模块同命令退出码 0（执行者 + closure audit 双重复跑）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（报告/README/roadmap/log 新增后复跑；closure audit 复跑 0 errors/30827 refs）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 收口后执行，见 Closure Evidence）
- [x] **Anti-Hollow Check**：closure audit 已验证修复组件被既有调用链在运行时确实调用、无空方法体/静默跳过/no-op 作为正常实现（closure audit A.4 PASS：fail-fast 方法位于 buildSource:481/buildSink:584/buildCustom 派发:118→:349 真实路径；NoOpTimestampAssigner 位于无 bean 时的真实执行路径；新测试断言正确结果而非仅无异常）
- [x] 独立子 agent closure-audit 已完成并记录证据（session `ses_fa2be1d58ffeK9Lb2nWgs2fmfE`，**CLOSURE-APPROVED**，A.1—A.10 全 PASS + 3 Minor 均处置）
- [x] roadmap item 11 状态写回（closure audit 通过后）；M2 解锁条件（items 6—11 全 done）在写回时一并核对声明（roadmap item 11 done + ★M2 done + Last updated 同步；P2 backlog 双 rocksdb 条目 Closed 写回）

## Deferred But Adjudicated

（无需追加：全部延期项为 watch-only residual / optimization candidate / out-of-scope improvement 三类，逐条附 Why Not Blocking Closure 见报告 §3.2 watch-only 表 W-R1..W-C3；结构治理两项为 confirmed 非缺陷债 → Follow-up items 29/30，不属 deferred）

## Non-Blocking Follow-ups

- Follow-up items 29/30 已落 roadmap（todo）：flow DSL 编译器产品化收敛（xpl source 取消语义 / build 期错误源位置 / per-transform parallelism 消费）+ rocksdb SerDe 克隆家族收敛（与 item 21 联动）。
- fraud-example 三缺口（Gap A/B/C）与 demo 重写路由 items 12/13（roadmap stage details 既定）；flow params/capability 实际消费路由 items 12/13 + item 20 邻域（fail-fast 过渡后无静默契约违反残留）。
- restore 侧 segment 内容 hash 重算校验维持 production P2 backlog 既有裁定。

## Closure

Status Note: 三 Phase 全部完成并经独立 closure audit 核准。历史审计收口（05-20 归属/06-30 十项核对/2300-2 rocksdb 侧 live 复核/2300-1/3 零修复点确认）+ D-GAP item 11 三项重点全消化（P-REQ-20 segment 级真实性成立、flow XDef 完备性表 + silently-dropped 面收敛 fail-fast、fraud-example 评估 + 3 缺口清单自包含）+ 19 项小缺陷就地修复（15 新 focused 用例；rocksdb 双 P2 backlog 条目收口）+ Follow-up items 29/30 落库 + item 22 枚举补全 + 全量回归绿 + 全门禁 exit 0。Phase M 收官，M2 解锁（items 6—11 全 done）。
Completed: 2026-09-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（fresh session）
- Audit Session: `ses_fa2be1d58ffeK9Lb2nWgs2fmfE`
- Evidence:
  - **Verdict: CLOSURE-APPROVED（A.1—A.10 全 PASS）**
  - A.1 Phase 1 exit criteria PASS：报告 §1.1—§1.3 表齐备；2300-2 双分支 verify-before-restore live 锚点复核（:799/:811，RK-8 后行号）；TestRocksDBIncrementalRestoreFailFast 存在；2300-1/3 零三模块修复点。
  - A.2 Phase 2 exit criteria PASS：D-GAP ① task 侧每 SST 无 skip 分支（strategy :85-104）+ coordinator 无条件 descriptor（:669-674）；② 5 类 fail-fast live 存在且被调用（buildSource:481/buildSink:584/buildCustom :118→:349/ordered loop :358-366）；③④⑥⑦⑧ 齐备；hollow 三模块独立复跑 3×exit 0。
  - A.3 Phase 3 修复存在性抽验 12/12 live（RK-1/RK-2/RK-3/RK-4/RK-6/RK-7/RK-8/FL-3/FL-4/FX-1/FX-2/FX-3 锚点）；新增测试计数核实（4+7+4+2 + fixture）。
  - A.4 Anti-Hollow PASS：fail-fast 位于真实 build 路径；NoOp assigner 位于无 bean 时真实执行路径（AT :443-448）；修复未引入空方法体/吞异常；新测试断言正确结果（TTL 42→0 过期断言、legacy 恢复 12L、ListAppender WARN、错误码 + declared=4、sink ["a","b","c"]、匹配数 + userId）。
  - A.5 门禁独立复跑全 exit 0：三模块 mvnw（rocksdb 93/0、flow 81/0、fraud-example 29/0）+ invariants + doc-links（0 errors/30827 refs）+ 3× hollow。
  - A.6 roadmap 写回核实：item 11 done + ★M2 done + item 22 枚举（rocksdb 4/fraud 4）+ items 29/30 todo（来源 item 11）+ Last updated；P2 backlog 双 rocksdb 条目 ✅ Closed。
  - A.7 deferred 诚实性 PASS：watch-only 10 组逐条理由；FX-1 复制模板定性 + fix-revert 实验记录如实；无 in-scope live defect 藏匿。
  - A.8 文本一致性：三 Phase completed + 全部 checklist [x]；日志条目存在。
  - A.9 无 `_` 前缀生成文件修改（git status 30 entries 全在三模块 + ai-dev；test-smoke.stream.xml 为手写 fixture，变更已记录于报告 §3.1）。
  - A.10 Verdict: CLOSURE-APPROVED；3 Minor 均处置：①行号漂移已修正（报告 §1.3 :799/:811）②fraud 通配符计数 5→4 已修正（roadmap item 22 + 报告 §1.2 #3 + 结论行）③session ID 已以真实值写回（roadmap item 11 行）。
  - `node ai-dev/tools/check-plan-checklist.mjs <this-plan> --strict` 退出码 0（Closure Evidence 写入后终验，见下）。

Follow-up:

- no remaining plan-owned work（结构治理 → items 29/30；fraud 改造 → items 12/13；conf 数据面 → item 20 邻域；均为 roadmap 承接，非本 plan 遗留）
