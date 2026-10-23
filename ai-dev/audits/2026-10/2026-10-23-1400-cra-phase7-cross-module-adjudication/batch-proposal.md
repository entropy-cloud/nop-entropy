# batch-proposal — CRA Phase 7.1 修复批次划分提案（plan 2493 Phase 5）

- Status: open（Phase 5 提案子代理产出；待独立对抗审查后交 7.2 R1.0 展开器）
- 日期：2026-10-23。工作仓库：`nop-entropy-concurrency-audit`。
- 输入（只读，未改动）：本目录 `inventory-p1.md`（179 行 P1 表 + B1 清单）、`family-clustering.md`（186 族行 + §三 B1 终裁 + §四 重复消解 + §五 承接）、`p2-adjudication.md`（90 组：修 483[提前 44 + R4+ 439] / deferred 77）、`inventory-ledgers.md` §四（批次约束）；[cra-index](../../cra-index.md)（P1 台账 179 行 + P2 裁决台账 90 组行）；plan 契约 [2493-cra-phase7-cross-module-adjudication.md](../../plans/2493-cra-phase7-cross-module-adjudication.md)（「批次覆盖口径定义」五条 /「successor 合法目标集」/「批次记录 schema 定义」）。
- 写回（本提案两处）：①本文件；②cra-index P1 台账「裁定批次」列回填 + B1-04 计数联动（见 [cra-index](../../cra-index.md) P1 发现汇总节，2026-10-23 回填）。inventory / family-clustering / p2-adjudication / inventory-ledgers 四份输入未改动。
- **P2 裁决组行「批次[Phase 5 回填]」占位的批量替换依据 = 本文件 §二.5 组→批映射表**（本子任务写回范围限于 P1 台账，组行占位替换由主 agent 按 §二.5 执行或随 7.2 落地）。

---

## §一 批次记录 schema 定义（plan 2493 Phase 5 第一项；M6，与 roadmap Phase 8 六列映射）

roadmap Phase 8 工作项行六列 = `# | Work Item | Status | Owner Doc | Deps | Skill`。本 schema 字段映射：

| schema 字段 | roadmap Phase 8 列 | 说明 |
|---|---|---|
| 批号 + 标题 | `#` + `Work Item` | 批号形如 `R1-01`（R 前缀=轮次，见 §二.0） |
| finding ID 清单 | `Deps` | **分号分隔**，统一 WI 数字前缀格式（如 `2.1-C3-001`；P1 台账行与 P2 组成员同格式）；族主行内联吸收成员（`←X`）随族主 ID 承载不单列 |
| 涉及模块 + 验证命令 | 不占 roadmap 列 → **落每批修复 plan** | `./mvnw test -pl <模块> -am`（模块=仓库顶层聚合模块名）；idea-plugin 站点为 Gradle/harness 通道（CL-301，见 §四.1） |
| Owner Doc 锚点路径 | `Owner Doc` | `docs-for-ai/` 下路径；无 owner doc 的模块在批 exit criteria 执行缺口声明 |
| Protected Area 授权来源 | 不占 roadmap 列 → **落每批修复 plan** | 授权依据 = AGENTS.md「Protected Areas」表 + 修复 plan Dependencies 节声明（plan-first 须附设计文档+回归测试；ask-first 须附用户询问记录） |
| 依赖与可并行标注 | 不占 roadmap 列 → **落每批修复 plan** | `依赖: 无前置（可并行）` 或 `依赖: <批号>`（硬依赖=排序前置，软依赖=设计对齐） |
| Skill 列 | `Skill` | **全表 = none（修复 plan）**，治理批 R-G 在行内声明产出物 |
| exit criteria | 不占 roadmap 列 → **落每批修复 plan** | 通用六项（下）+ 批特有增补（§二 各批行末列） |

**通用 exit criteria 六项（每批默认继承，E1–E6）**：

- **E1 全量构建绿**：批末 `./mvnw clean install -T 1C` BUILD SUCCESS（最低限度：受影响聚合模块树 `-am` 全绿）。
- **E2 focused 测试**：批内每工单附强制并发回归测试；C10「验收前置」条目与本体**同 diff** 补测（映射 SSOT = `inventory-p2-c10.md` §二）；回归失败可复现（先红后绿或有变异/回退论证）。
- **E3 验证通道**：按批行「验证命令」列执行并留证；CL-301 站点按三选一通道（§四.1）。
- **E4 授权声明**：PA 授权列非「无」的批，修复 plan Dependencies 显式声明授权来源与审计/询问记录；Protected Area 触碰面为零的批在 plan 中作零触碰声明。
- **E5 owner doc 更新清单**：批行 Owner Doc 锚点所列文档随批更新；无 owner doc 模块补 `docs-for-ai/03-modules/` 条目或在 `docs-for-ai/01-repo-map/module-groups.md` 登记 drift。
- **E6 批级 closure**：每批=一份修复 plan，独立 closure audit PASS 后整批 done（roadmap Phase 8 行级门禁）。

**单批规模规则**：以「单次 AI 交付可完成」为限，目标 ≤15 成员/批；16–18 成员的批允许单批并注明可拆两次子交付（批次键与 exit criteria 不变）；>18 的 P2 大组一律拆分 a/b（成员对半，拆分明细在 §二 各批行 finding 清单中显式列出，每成员仍恰好一批）。

**轮次排序（roadmap Phase 8 建议排序原则）**：R1 = P0 收尾确认 + 跨模块同族 P1（基础设施层先行：nop-kernel/nop-api-core 设施工单，下游一次受益——R04 绝缘层/R13 平台池/R14 取消通道/R15 上下文传播/R16 Debezium/R24 缓存失效链/R01 F4 统一面/R09 沙箱族）→ R2 = 基础设施/平台服务剩余 P1（含并入的 R4+ 单元束搭车成员）→ R3 = 业务模块 P1（含该单元 R4+ 束并批）→ R4 = P2 裁决为修的单元束 + R28 墙钟跨单元批 + 移交接收轨批。

**批次数与 plan 预估的偏差声明**：plan 预估「8–15 批（以实际聚类结果为准）」。实际修复工作项 = P1 179 行 + P2 修 483 条 − MA-17 双载 1 = **661 项**；在「单批 ≤~15 成员」约束下算术下限 ≈ 45 批，且跨单元族同批约束（R01/R04/R13/R16/R22/R24/R09 统一批）与验证通道边界使进一步合并不可行。故本提案落 **72 个修复批 + 1 个 P0 确认批（R1-00）+ 1 个治理批（R-G）= 74 条批次记录**。7.2 展开器如需压缩 roadmap 行数，可将同模块强耦合相邻批合并为单行内子阶段（Deps 仍按本提案 finding 清单引用）。

---

## §二 批次划分

### §二.0 轮次与批号总览

| 轮次 | 批号段 | 批数 | 内容 |
|---|---|---|---|
| R1 | R1-00 ~ R1-08 | 9 | P0 收尾确认（R1-00）+ 跨模块平台/基础设施族 P1 批（R1-01~08，含 P2 提前搭车 31 条 + G26 5 条 + MA-17 回升行） |
| R2 | R2-01 ~ R2-21 | 21 | 基础设施/平台服务剩余 P1（含 P2 提前搭车 10 条 + R4+ 搭车组 G58） |
| R3 | R3-01 ~ R3-10 | 10 | 业务模块批（P1 + 该单元 R4+ 束并批） |
| R4 | R4-01 ~ R4-33 | 33 | P2 R4+ 单元束 + R4-32 墙钟跨单元批（R28）+ R4-33 移交接收轨批 |
| 治理 | R-G | 1 | 治理轨批次（附录 §五） |

> Skill 列全表 = none（修复 plan；R-G 为治理轨批次，行内声明产出物）。finding ID 格式 = `WI-主ID`（与 P2 裁决台账成员清单同格式）；`MA-xx` 等族工单按其 P1/P2 台账主 ID 书写。

### §二.1 R1 批次（P0 收尾 + 平台/基础设施跨模块族）

| 批号 | 标题 | finding ID 清单 | 模块 / 验证命令 | Owner Doc 锚点 | PA 授权来源 | 依赖（→=前置）/可并行 | exit criteria 增补（E1–E6 之外） |
|---|---|---|---|---|---|---|---|
| R1-00 | P0 收尾确认批（3 项已修 P0 回归确认） | 1.2-C6-001；4.3-C6-001；2.5-C6-002 | nop-stream；nop-network；nop-persistence / `./mvnw test -pl nop-stream -am`；`./mvnw test -pl nop-network -am`；`./mvnw test -pl nop-persistence -am` | `docs-for-ai/03-modules/nop-stream-connectors.md`；`docs-for-ai/03-modules/nop-network.md`；nop-persistence 无 owner doc→缺口声明 | 无（P0 已修，仅确认） | 无前置（可并行）；先行于全部修复批作基线门禁 | 三项 P0 回归测试在位且必红（producer close/stopIncrementalChains/fd 计数）；4.3 scan-hollow 门禁复跑退出码 0；P0 台账零 open 遗留；4.3 P0 修复仅限 impl 叶声明维持 |
| R1-01 | 周期任务异常绝缘层批（R04 平台批） | 2.1-C3-001；1.6-C8-001；1.7-C6-001；4.5-C4-001；2.2-C1-021；1.4-MA-05；1.4-MA-06；1.4-C4-003；1.7-C6-010；2.1-C3-015；3.1-MA-07；4.1-C3-009；4.2-C4-004；5.5-W-11 | nop-kernel（绝缘层本体）；nop-core-framework；nop-retry；nop-tcc；nop-batch；nop-job；nop-search；nop-message；nop-cluster；nop-rule / 本体 `./mvnw test -pl nop-kernel -am`；下游各 `./mvnw test -pl <模块> -am` | `docs-for-ai/03-modules/nop-job.md`；`docs-for-ai/03-modules/nop-batch.md`；`docs-for-ai/04-reference/source-anchors.md`（2.1-C3-001 30 点绝缘状态表锚点） | **框架核心引擎 plan-first**（nop-kernel 内部；AGENTS.md Protected Areas 表）——修复 plan 附设计文档+回归测试 | 无前置（可并行，R1 旗舰批）→ 下游 R1-03/R2-11/R2-13/R4-10 依赖本批平台方案 | 绝缘层统一语义=catch Throwable+completeExceptionally+重臂/观测；1.4-MA-06 修复位 worker 侧（checker 侧 plan340 契约测试+注释钉死不动）；1.7-C6-001 与 C6-010 同 diff（try 上移+catch Throwable）；3.1 Error 注入护栏义务（C10-008）保留 3.1 侧；4.1-C3-009 七屏障清单随单闭合；1.4-C4-003 载体 nop-commons BindScheduledExecutor 移交面闭合 |
| R1-02 | 平台池设施面批：globalTimer 死配置 + 池工厂（R13 设施） | 2.1-C4-001；2.1-C4-003；2.1-C4-004；2.1-C4-006；2.2-C4-001 | nop-kernel；nop-core-framework / `./mvnw test -pl nop-kernel -am`；`./mvnw test -pl nop-core-framework -am` | `docs-for-ai/04-reference/source-anchors.md`（GlobalExecutors/globalTimer 锚点）；`docs-for-ai/03-modules/nop-job.md`（调度契约） | **框架核心引擎 plan-first**（同 R1-01 授权路径） | 无前置（可并行）→ **R1-03 硬依赖本批**（先平台后使用方，3.1 §5.1-2 / 4.2 §五-5） | globalTimer 隔离/卸载方案按平台族一次裁决（消费方 R1-03 同口径）；池工厂零校验补 fail-fast+收缩 IAE；4.2-C4-003 globalTimer 两面映射 WI2.1 设施面闭合 |
| R1-03 | 平台池消费方批：隔离/卸载落地（R13 使用方） | 1.2-C4-004；1.5-C4-001；2.3-C8-001；3.1-C4-011；1.7-C8-003；3.1-C4-004；3.1-C4-009；3.2-C4-004；4.2-C4-003 | nop-stream；nop-wf；nop-task；nop-service-framework；nop-ai；nop-batch；nop-cluster / 各 `./mvnw test -pl <模块> -am` | `docs-for-ai/03-modules/nop-stream-connectors.md`；`docs-for-ai/03-modules/nop-task.md`；`docs-for-ai/02-core-guides/service-layer.md`；`docs-for-ai/03-modules/nop-ai.md` | 无（impl 叶） | **依赖 R1-02**（硬） | Debezium 引擎移出 globalWorker（心跳/超时预算）；WS 推送卸载共享池（ρ=N·L/40 下降）；1.7-C8-003 兜底 cached 池平台级统一评估（设施面 2.1-C4-004 已在 R1-02）；3.1-MA-16（R4-21）「不得指向 globalTimer」约束在本批落地后解除 |
| R1-04 | api-core 协同批：bindCancel 单点改造 + 上下文传播（R14+R15） | 2.3-C3-002；2.3-C3-007；2.1-C2-016；2.1-C3-004；2.1-C3-005；2.1-C3-007；2.1-C3-016；2.3-C3-012；2.3-C3-015；2.3-C3-017；2.3-C3-008；4.3-C3-007；4.3-C3-012；3.2-C4-008 | nop-kernel（io.nop.api.core.*）；nop-service-framework；nop-network；nop-ai / `./mvnw test -pl nop-kernel -am`；下游各 `./mvnw test -pl <模块> -am` | `docs-for-ai/04-reference/source-anchors.md`（api-core 锚点）；`docs-for-ai/02-core-guides/service-layer.md` | **nop-api-core plan-first**（AGENTS.md：`nop-api-core` 全平台公共 API；SPI 变更须列全部实现为回归面） | 无前置（可并行）→ R2-02/R2-16 消费方随后受益 | FutureHelper.bindCancel 单点改造（服务 2.1 三条+消费方 1.6/4.3/3.4 回归面全列执行）；executeWithContext/thenOnContext 统一（出口泵补齐）；4.1-CL-024（Local 红投）归属本族注记闭合 |
| R1-05 | 缓存失效传播链批（R24） | 2.1-C7-007；2.1-C9-001；2.1-C9-010；2.3-C9-002 | nop-kernel；nop-service-framework / `./mvnw test -pl nop-kernel -am` | `docs-for-ai/04-reference/source-anchors.md`（模型缓存锚点） | **框架核心引擎 plan-first**（nop-kernel 内部） | 无前置（可并行）；R3-05（5.5-W-03）验证联动 | C7-007 与 C9-001/C9-010 同链三面**单点修复**；handlerCache 别名（2.3-C9-002=同一缺陷 §四#4）随主单点不双计；若涉失效判据须联动 5.5-W-03 验证（R3-05 注记） |
| R1-06 | 沙箱/shell 工具执行链批（R09 沙箱清理族 + B1-03/B1-04 回升行） | 3.2-C6-001；3.2-C6-002；3.2-C6-003；3.2-C1-001；3.2-C4-003；3.1-MA-17；3.1-C7-014 | nop-ai / `./mvnw test -pl nop-ai -am` | `docs-for-ai/03-modules/nop-ai.md` | 无 | 无前置（可并行） | **B1-04 计数迁移落账确认**（3.1 P1×13→14/P2×29→28，cra-index 已回填）；**B1-03 族工单 MA-06 维持 P1**（取最严暂计转正；其 C6/C8 条化成员随 G56 条款，不单独立卷）；超时 destroyForcibly+reader 退出补偿+kill 失败可见化；Docker 侧 F3 残留（Host 已修未同修面）随 MA-02 补齐；护栏测试共享形态（3.1-C10-011，C10-001 单保护面覆盖 MA-06 五成员）；3.1-C7-014 携 3.1 反向钉死改写义务同批 |
| R1-07 | F4 账本保留统一批（R01 统一批次面，ledger §二#3） | 1.6-C7-001；1.7-C7-003；5.1-MA-01；5.5-W-02；5.6-W-01；5.2-MA-04 | nop-retry；nop-tcc；nop-batch；nop-credential；nop-rule；nop-duckdb；nop-sys / 各 `./mvnw test -pl <模块> -am` | `docs-for-ai/03-modules/nop-retry.md`；`docs-for-ai/03-modules/nop-batch.md`；`docs-for-ai/03-modules/nop-credential.md`；`docs-for-ai/03-modules/nop-rule.md`；`docs-for-ai/03-modules/nop-duckdb.md`；`docs-for-ai/03-modules/nop-sys.md` | **nop-auth ask-first**（5.1-MA-01 触认证域数据面：Session/Token 清理策略；AGENTS.md Protected Areas 表 + 修复 plan Dependencies 声明 + 用户询问记录） | 无前置（可并行）；保留策略设计=族级 SSOT（上限+逐出+fail-fast 包络声明，4.5 口径） | 统一保留期/TTL/行数上限设计一处定稿、五模块落地；1.6-C7-001↔1.7-C7-003 账本表批次对齐；5.2-MA-04 留存语义按族级纪律（owner doc 补 retention 声明后落地）；5.6-W-01 过期指纹误拒面同批闭合 |
| R1-08 | Debezium 双轨与消息交付语义批（R16/R17/R18 面，B1-05 三案） | 1.2-C3-014；1.2-C3-004；4.1-C3-002+C8-004；4.1-C6-002；4.1-C8-002；4.1-C6-006 | nop-stream；nop-message / `./mvnw test -pl nop-stream -am`；`./mvnw test -pl nop-message -am` | `docs-for-ai/03-modules/nop-stream-connectors.md`；`docs-for-ai/03-modules/nop-message.md`；`docs-for-ai/03-modules/nop-stream-cdc-cookbook.md` | 无 | 无前置（可并行）；**R4-24 设计联动**（REGISTRY 契约一次裁决禁止各修一半）；R2-16 对齐注记（consumeAwaitTimeout 契约） | B1-05 三案统一 P1 落账（4.1 §五-3 同批调整）；使用面/本体面批次对齐（4.1 §五-5）；C6-006 与 MA-05 同 map 改造；4.1-C8-002 Local 失败回调三层架构闭合 |

### §二.2 R2 批次（基础设施/平台服务剩余 P1）

| 批号 | 标题 | finding ID 清单 | 模块 / 验证命令 | Owner Doc 锚点 | PA 授权来源 | 依赖 / 可并行 | exit criteria 增补 |
|---|---|---|---|---|---|---|---|
| R2-01 | 2.1 kernel 剩余批：退役代际族 + RCE 死锁族 | 2.1-C1-001；2.1-C2-001；2.1-C1-003；2.1-C1-014；2.1-C2-002；2.1-C2-017；2.5-C6-005 | nop-kernel；nop-persistence / `./mvnw test -pl nop-kernel -am` | `docs-for-ai/04-reference/source-anchors.md`；`docs-for-ai/02-core-guides/ioc-and-config.md` | **框架核心引擎 plan-first** | 无前置（可并行）；2.2-C1-007（R2-04）刷新链上游联动注记 | C1-014 携 N2 修复阻断预警钉（同批解除）；C2-017 与 C2-001 同变更集绑定；2.5-C6-005 双轨联动（refreshConfig 缩容热关共享连接取用窗，§四#6，已知未修复前案） |
| R2-02 | 流式取消/期限两侧同批（R14 消费面 + R05/R06/R11/R21 相邻） | 3.1-C3-001；3.1-C7-001；3.1-C7-002；4.3-C3-002；4.3-C6-004 | nop-ai；nop-network / `./mvnw test -pl nop-ai -am`；`./mvnw test -pl nop-network -am` | `docs-for-ai/03-modules/nop-ai.md`；`docs-for-ai/03-modules/nop-network.md` | 无 | 软依赖 R1-04（bindCancel/AttemptCancelToken 原语先行） | 4.3-MA-02 消费面回写与 3.1-C7-001 **两侧同批**（聚合层全程期限+传输层读超时/可中断，4.3 §七-6）；C6-004 不可中断面 daemon 化；C7-002 错误以成功交付族纪律（R11）；completeExceptionally 纪律（R21） |
| R2-03 | 2.2 插件生命周期/配置源/Shell 批 | 2.2-C1-010；2.2-C1-009；2.2-C6-001；2.2-C6-002；2.2-C6-003；2.2-C6-004 | nop-core-framework / `./mvnw test -pl nop-core-framework -am` | `docs-for-ai/02-core-guides/ioc-and-config.md`；`docs-for-ai/04-reference/source-anchors.md` | **框架核心引擎 plan-first** | 无前置（可并行）；R4-15（G31）同 PR 协同后置 | C6-001 与测试 N2 设计冲突消解；C6-004 外层 try finally（←C6-014）；C1-010 lifecycleLock 全迁移统一（含 C1-011/012 并案）；插件状态机恢复 API |
| R2-04 | 2.2 IoC 启动图/死锁 + 配置发布协议批（R22 刷新链） | 2.2-C4-003；2.2-C2-001；2.2-C2-002；2.2-C1-007；2.5-C1-007；2.5-C7-004；2.5-C1-010 | nop-core-framework；nop-persistence / `./mvnw test -pl nop-core-framework -am`；`./mvnw test -pl nop-persistence -am` | `docs-for-ai/02-core-guides/ioc-and-config.md`；`docs-for-ai/04-reference/source-anchors.md` | **框架核心引擎 plan-first**（IoC 内部） | 无前置（可并行）；R4-15 协同 | B1-01 终裁 P1 维持（无 join+失败不传导承载）；C4-003 同 PR 批次（C4-009/011+CL-019 在 R4-15）；配置发布协议三字段单一不可变 holder（7 写点×8 读站）；2.5-C1-007/C7-004 刷新链下游 volatile 化同批；C1-010 写门可见性半边（G48a）随批 |
| R2-05 | 2.3 服务热路径剩余批（R05 SSO + R25 重试放大） | 2.3-C4-007；2.3-C8-002 | nop-service-framework / `./mvnw test -pl nop-service-framework -am` | `docs-for-ai/02-core-guides/service-layer.md` | 无 | 无前置（可并行） | SSO token 刷新异步化+timeout API+thenCompose 领先 next.get；429 重试准入上界/时间整形（在途×90s OOM 轨迹消除） |
| R2-06 | 流式缓冲批 A（chain25/R02 双实现分叉，3.3 同文件批 A） | 2.3-C3-003；3.3-C2-001；3.3-C1-001；3.3-C3-001；3.3-C8-001；3.3-MA-05；3.3-C2-002；3.3-C2-009；3.3-C3-003；3.3-C3-007；3.3-C7-005 | nop-service-framework；nop-ai / `./mvnw test -pl nop-service-framework -am`；`./mvnw test -pl nop-ai -am` | `docs-for-ai/02-core-guides/service-layer.md`；`docs-for-ai/03-modules/nop-ai-gateway.md` | 无 | 无前置（可并行）；3.3-C7-002 拆出至 R4-32；G36 的 G-D 面（R4-17）批次对齐注记 | pendingComplete 正确先例对齐（同仓）；「共享有界缓冲组件」方向收敛双实现分叉；越窗上界+demand 修正；C2-002/C2-009 R03 锁内重活外移；C3-003 与 3.1 分类器跨模块协调注记 |
| R2-07 | 1.1 stream-core 批（并发正确性 + 资源包络） | 1.1-C1-001；1.1-C4-001；1.1-C4-002；1.1-C4-009；1.1-C7-011；1.1-C8-011；1.1-C1-006；1.1-C8-003；1.1-C5-010；1.1-FAM-1 | nop-stream / `./mvnw test -pl nop-stream -am` | `docs-for-ai/03-modules/nop-stream.md`；`docs-for-ai/03-modules/nop-stream-user-guide.md` | 无 | 无前置（可并行）；C1-006/C8-003 保留纪律对齐 R1-07（R01）；C5-010/FAM-1 对齐 R02 三范式 | 快照加锁；LOCAL 池互等死锁解除；OpsJobManager 提交窗口补偿；msg_queue/分配账本上限（R01）；materialization store 背压修复（FAM-1 ←C7-001/C8-001） |
| R2-08 | 1.2 连接器批 | 1.2-C1-002；1.2-C1-001；1.2-C2-002；1.2-C3-002；1.2-C5-002；1.2-C8-001；1.2-C8-006 | nop-stream / `./mvnw test -pl nop-stream -am` | `docs-for-ai/03-modules/nop-stream-connectors.md` | 无 | 无前置（可并行）；MA-05 保留策略与 R1-07 同设计；MA-04 R03 锁内 HTTP 外移 | gauge 锁外 dbRef 修复（SIGSEGV 窗口）；CEP copyForSubtask 三面封闭；Avro registry 请求超时+锁外建连；2PC 保留策略（R01）；checkpoint offset 对齐（≤256 条丢失面）；2PC 事务裕度守卫 |
| R2-09 | 1.3 编排薄层批 | 1.3-C1-001；1.3-C3-008；1.3-C3-009；1.3-C6-007 | nop-stream / `./mvnw test -pl nop-stream -am` | `docs-for-ai/03-modules/nop-stream.md`；`docs-for-ai/03-modules/nop-stream-sql.md` | 无 | 无前置（可并行）；C3-009 根因载体 core 跨单元（与 1.3-C3-008 同批） | UDF 注册表并发容器化+运行期换绑契约（含 sql.md §3a docs bug 修正）；REPLACE 清零改增量恢复；RuntimeContext per-subtask 封闭；cancelled volatile 复位 |
| R2-10 | 1.4 job 批 | 1.4-C4-001；1.4-C2-001 | nop-job / `./mvnw test -pl nop-job -am` | `docs-for-ai/03-modules/nop-job.md` | 无 | 无前置（可并行）；MA-01 R01 对齐（终结驱动五面）；C2-001 R03 设施面（chain28 两面切分，消费面=5.5 随 W-01） | rpcPoll 终结驱动+失败预算/deadline 兜底/DB 回传/PollEntry 精简/gauge 五面一次闭合；监视器内外部 invoker 外移 |
| R2-11 | 1.5 task/wf 批 | 1.5-C1-001；1.5-C3-001；1.5-C3-002；1.5-C5-001；1.5-C5-003；1.5-C7-001 | nop-task；nop-wf / `./mvnw test -pl nop-task -am`；`./mvnw test -pl nop-wf -am` | `docs-for-ai/03-modules/nop-task.md`；`docs-for-ai/03-modules/nop-wf.md` | 无 | 无前置（可并行）；MA-23 fencing 对齐 R1-01（C8-001 平台方案）+R2-13（1.7-C7-001）——**避免两套 fencing**；MA-05 R02；MA-08 R01 | 五站点快照竞态修复（组迁移/终结滞留/join 双实例/action 双跑/子流回滚）；sync+executor 嵌套死锁解除；闸门注册表上限（R01，plan 364 之上 owner 表态出口）；WfTaskScanner fencing（holderId+单飞+remindCount 原子） |
| R2-12 | 1.6 tcc 批（CAS/守卫 + 补偿/账本卫生） | 1.6-C1-001；1.6-C3-002；1.6-C3-003；1.6-C3-012；1.6-C6-002；1.6-C3-005；1.6-C3-006；1.6-C6-003；1.6-C7-003；1.6-C7-005；1.6-C7-012；1.6-C8-012；1.6-C8-017 | nop-retry；nop-tcc / `./mvnw test -pl nop-retry -am`；`./mvnw test -pl nop-tcc -am` | `docs-for-ai/03-modules/nop-retry.md`；`docs-for-ai/03-modules/nop-tcc.md` | 无 | 无前置（可并行）；C7-001/C8-001 平台方案在 R1-07/R1-01；C8-012 R21 纪律（C8-017 同 diff 搭车） | CAS 失败阻断补偿/回写内存/续租族闭合；簿记异常不覆盖业务结果；OVERWRITE 原子化+死信（R10）；僵尸记录终态化；清理 API 生产接线+索引；namespace 扫描修复；C8-012 返回 future=回调挂接 future（C10-001 覆盖） |
| R2-13 | 1.7 batch 批 | 1.7-C7-001；1.7-C8-001；1.7-C8-002；1.7-C5-001；1.7-C5-002；1.7-C6-002；1.7-C6-009；1.7-C7-011 | nop-batch / `./mvnw test -pl nop-batch -am` | `docs-for-ai/03-modules/nop-batch.md` | 无 | **依赖 R1-01**（C6-001/C6-010 同 diff 已落地；C7-001 fencing 对齐 R1-01+R2-11）→ **R4-11/R4-12 依赖本批**（C8-001 先于 C8-009） | C7-001 测试 N1 参数化改写**前置**；C5-002 测试 N2 同步改写；C5-001 C10-011 补回归钉；C8-002 biz 链穿透（C8-010 在 R4-12 同批）；C6-002 REE 裸逃逸封闭（C7-002A 搭车）；C6-009 紧随 RUNNING 死区族、C7-011 A2 注记搭车 |
| R2-14 | 2.4 ORM 并发批（会话/事务互斥 + 模型发布/缓存/异步） | 2.4-C1-001；2.4-C1-003；2.4-C1-015；2.4-C2-001；2.4-C2-002；2.4-C2-004；2.4-C2-005；2.4-C2-013；2.4-C2-015；2.4-C2-017；2.4-C3-001；2.4-C3-005；2.4-C6-001 | nop-persistence / `./mvnw test -pl nop-persistence -am` | `docs-for-ai/02-core-guides/model-first-development.md`；`docs-for-ai/04-reference/source-anchors.md` | **ORM 模型结构 plan-first**（若修复触 `model/*.orm.xml` 须 plan-first 声明；纯代码修复作零 orm 触碰声明） | 无前置（可并行）；C2-013/C2-015 自并单点（R10）；C2-004 与 C1-027（R4-19）共享设计注记 | IContext 注册表 get-or-create 单修复（ContextAttrs）；ormModel volatile 化；session 全生命周期互斥；事务注册协议原子性（半个成功消除）；实体缓存 read-repair 世代化；drainTo 等待窗口出事务 |
| R2-15 | 2.5 peri 批（PubSub 断链 + 链编排 fencing） | 2.5-C6-004；2.5-C8-001；2.5-C7-014；2.5-C6-003；2.5-C7-003；2.5-C7-001；2.5-C3-001；2.5-C1-003；2.5-C3-008；2.5-C3-009；2.5-C7-002 | nop-persistence / `./mvnw test -pl nop-persistence -am` | `docs-for-ai/02-core-guides/model-first-development.md`（迁移 owner 面）；`docs-for-ai/04-reference/source-anchors.md` | 无 | 无前置（可并行）；**批内次序：C3-008 先于 MA-06**（键派生修复顺序前置）；R19 前案 cross-ref（check/nosql-cdc 2026-08-19）；MA-02 携反向钉死改写 | B1-02 终裁 P1 落账（严重度采 2.5 O-1 建议）；PubSub 属主校验/停机对账/重订阅检测；IO 线程内联消费外移+ack 契约恢复；链状态行版本谓词+单飞/fencing；迁移历史表原子守卫；relocate querySpace 修复；写门持久化半边归 R2-04 注记 |
| R2-16 | 3.1 agent 编排/会话批 | 3.1-C1-008；3.1-C5-015；3.1-C4-010；3.1-C7-022；3.1-C5-008；3.1-C3-011；3.1-C3-028；3.1-C3-021；3.1-C7-012；3.1-C4-015 | nop-ai / `./mvnw test -pl nop-ai -am` | `docs-for-ai/03-modules/nop-ai.md` | 无 | 依赖 R1-04（取消契约原语）；软依赖 R1-02/R1-03（globalTimer 约束解除）；软依赖 R1-06（同仓沙箱面先行） | MA-03 F4 终态清理 29 站点（**gate3 反向钉死改写同批**：remove no-op 钉+F4 断言补全）；MA-04 spawn 池 close 接线（C4-015 同批）；MA-05/MA-08 取消契约补齐（依赖 R1-04 bindCancel）；MA-09 方言协议族单修复批；MA-11 与 R1-08 consumeAwaitTimeout 契约对齐；MA-19 接管锁 abort 语义（R12 fencing） |
| R2-17 | 3.4 AI REST 批 | 3.4-C6-001；3.4-C1-001；3.4-C1-003；3.4-C3-005 | nop-ai / `./mvnw test -pl nop-ai -am` | `docs-for-ai/03-modules/nop-ai.md` | 无（nop-ai-api plan-first 零触碰声明） | 无前置（可并行）；C3-005 R11 与 R2-02/R2-06 族批对齐 | schemaReady per-collection 化（三登记一处闭合）；parallelStream 并发容器；upsert 原子化（ON CONFLICT 范式）；isSuccess 检查（错误以成功交付族，IND-03 升格面闭合） |
| R2-18 | 4.2 cluster 批（选主/注册中心/重试链） | 4.2-C1-004；4.2-C1-001；4.2-C1-003；4.2-C3-001 | nop-cluster / `./mvnw test -pl nop-cluster -am` | nop-cluster 无 03-modules owner doc→缺口声明；`docs-for-ai/01-repo-map/module-groups.md` | 无 | 无前置（可并行）；MA-07 R21（吸收成员 C3-008/C8-008 同 diff 在 R4-25 落地） | doStop 复活锁序修复（leaderEpoch+监听器序）；ServiceInstance 不可变化（12 读者站点）；订阅快照版本守护；callAsync 回调同步异常 completeExceptionally+Sentinel entry 释放 |
| R2-19 | 4.3 feishu/文件通道批（B1-07/B1-08 转正 + R05/R11/R08 面） | 4.3-C3-015；4.3-C1-009；4.3-C6-021；4.3-C3-001；4.3-C3-005；4.3-C3-024；4.3-C6-005 | nop-network / `./mvnw test -pl nop-network -am` | `docs-for-ai/03-modules/nop-network.md` | 无（nop-http-api/nop-rpc-api/nop-integration-api 三叶零触碰声明） | 无前置（可并行）；B1-07/B1-08 **P1 转正落账**（对称条款：消费面回撤即转条件化 P2）；C6-018（R2-20）同文件协同 | FeishuClient send 有界化（半开连接整体死锁消除）；重连单飞/世代守卫+stop×reconnect 竞态（MA-05/MA-06 同文件相邻不并族、两单同批实施）；OkHttp future 完成保障；verifyAndRename 强制（错误以成功交付第 4 例）；SftpClient 全链超时+构造回收协同；BASE64_FORM 流关闭（与 CL-012 同站点不同本体协同注记） |
| R2-20 | 4.3 连接生命周期批（B1-06 + R26/R08 面） | 4.3-C1-008+C3-018+C6-020；4.3-C3-004；4.3-C1-001；4.3-C6-017；4.3-C6-018；4.3-C6-019 | nop-network / `./mvnw test -pl nop-network -am` | `docs-for-ai/03-modules/nop-network.md` | 无（三 api 叶零触碰声明） | 无前置（可并行）；vertx-mqtt 面=§三.5#9 部署包络确认项（随批确认）；MA-07 R26（2.1-CL-024 原始站点） | MQTT removeConnection 属主校验（三登记一工单）；sidecar 探测移出 I/O reactor 回调线程（apache 面承载 P1，jdk/okhttp P2 备注随单）；SimpleRpcClientFactory 幂等 init+世代令牌；bind 失败释放事件循环组（对照 C6-014 正确兄弟写法）；构造期会话回收；SesClient 生命周期配对 |
| R2-21 | 4.4 host 批 | 4.4-C1-006；4.4-C6-007；4.4-C6-001；4.4-C1-001 | nop-host 所在聚合模块 / `./mvnw test -pl <宿主聚合模块> -am`（以 nop-host 实际 maven 路径为准） | nop-host 无 03-modules owner doc→缺口声明；`docs-for-ai/01-repo-map/module-groups.md` | 无（C6-009 未入修复面；若后续立项触 nop-http-api 须 plan-first——批外注记） | 软依赖 R1-04/R1-01（C6-007 平台根因绝缘层移交 2.3-CL-018+2.1-C3-001 先行） | 双宿主启动窗口就绪门禁（空链固化消除）；ContextTaskQueue 异常 completeExceptionally（future 族第 5 例宿主侧）；上传流配对 close+fd 计数回归（C10-011 复用 4.3 TestFileDownloadSinkClose 先例）；凭据对 volatile 化 |

### §二.3 R3 批次（业务模块批，P1 + 该单元 R4+ 束并批）

| 批号 | 标题 | finding ID 清单 | 模块 / 验证命令 | Owner Doc 锚点 | PA 授权来源 | 依赖 / 可并行 | exit criteria 增补 |
|---|---|---|---|---|---|---|---|
| R3-01 | 5.1 auth 凭据批 | 5.1-MA-02；5.1-MA-03；5.1-MA-04；5.1-I-01；5.1-I-02；5.1-I-06 | nop-credential / `./mvnw test -pl nop-credential -am` | `docs-for-ai/03-modules/nop-credential.md`；`docs-for-ai/03-modules/nop-auth.md` | **nop-auth ask-first**（认证模型；若触 `nop-auth/model/nop-auth.orm.xml` 另加 ORM plan-first——5.1-MA-06 version 列面） | 无前置（可并行；5.1-MA-01 在 R1-07） | I-02 携 **C10-007 反向钉死前置**（先改 TestCredentialOAuthRefresh.refreshFailureFailsClosed）；MA-03 与 I-01 同 PR 批；I-06 SSO 登出半成功修复（waitAll 聚合语义；Phase 3 默认裁修注记，owner 契约对照如推翻可改判）；CL-017 watch 搭车注记 |
| R3-02 | 5.2 sys-dyn 批 | 5.2-MA-01；5.2-I-01；5.2-I-02；5.2-MA-02；5.2-MA-03；5.2-MA-05；5.2-MA-06；5.2-I-03；5.2-I-04；5.2-I-05 | nop-sys；nop-dyn / `./mvnw test -pl nop-sys -am`；`./mvnw test -pl nop-dyn -am` | `docs-for-ai/03-modules/nop-sys.md`；`docs-for-ai/03-modules/nop-dyn.md` | **I-03 唯一索引触 orm = plan-first**（nop-sys-api/nop-dyn-api 零触碰前提下；实施前置 plan audit，§三.5#6 选型已裁） | 无前置（可并行）；I-02 R17（与 R1-08 4.1-MA-05 注册/注销配对修复对齐注记） | MA-01 holderId 互斥+租约续期 fencing；I-01 TreeMap COW；**MA-02 携反向钉死同 diff 改写**（TestInMemoryCodeCacheContract :177-194）；I-03 唯一索引方案落地（moduleName 并发双建防护）；I-04 照抄 5.1 函数级先例；5.2-MA-04 在 R1-07（本批注记） |
| R3-03 | 5.3 file-datav 批 | 5.3-W-01；5.3-W-04；5.3-CL-001；5.3-CL-002；5.3-CL-003；5.3-CL-004；5.3-CL-005；5.3-CL-010；5.3-CL-015；5.3-CL-016；5.3-CL-019；5.3-CL-020；5.3-CL-021；5.3-CL-023 | nop-datav；nop-file / `./mvnw test -pl nop-datav -am`；`./mvnw test -pl nop-file -am` | nop-datav 无 owner doc→缺口声明（5.3 drift 登记）；`docs-for-ai/03-modules/nop-file.md` | 无（nop-file-api plan-first 零触碰声明） | 无前置（可并行）；W-04 R02 与 1.5-MA-05/1.7-C5-001 批次对齐；CL-020 平台修复归 R1-01（本单接入点登记）；CL-010/021=R02 对齐 | **CL-003 反向钉死同 diff 改写**（testSlowExecutionRaceWorkerSuccessOverwritesScanFailed 反转，跨单元 fencing 族对齐 R12）；CL-005 假保护（顺序测试伪装）同 diff 修正；W-01 去重竞态原子化；W-04 rowLimit 上界（R02 三范式）；缺保护 3/4/5 验收前置 |
| R3-04 | 5.4 report-format 批 | 5.4-C5-001；5.4-C1-001；5.4-C1-002；5.4-C1-003；5.4-C1-005；5.4-C6-001；5.4-C9-001；5.4-C9-002；5.4-C5-004；5.4-C5-R2-001 | nop-report / `./mvnw test -pl nop-report -am` | `docs-for-ai/03-modules/nop-report.md`（nop-format 无 owner doc→缺口声明随 R-G） | 无 | 无前置（可并行）；C5-001 R02 主条目（与 1.7-C5-001 R2-13 联动）；C5-004 双轨不双计（§四#7） | **C5-001 lengthExpr 恶意长度测试 = 修复准入门槛**（先红后绿）；C1-003+C9-001、C1-002+C9-002 两组单设计联合闭合；C6-001 构造泄漏 fd 计数回归（R07/R08 先例）；C9-002 锁内字体 I/O 外移（R03 第 4 例） |
| R3-05 | 5.5 rule-metadata 批 | 5.5-W-01；5.5-W-03；5.5-W-04；5.5-W-05；5.5-W-07；5.5-W-08；5.5-W-09；5.5-W-10；5.5-W-12 | nop-rule；nop-metadata / `./mvnw test -pl nop-rule -am`；`./mvnw test -pl nop-metadata -am` | `docs-for-ai/03-modules/nop-rule.md`；`docs-for-ai/03-modules/nop-metadata.md` | 无（nop-rule-api/nop-metadata-api plan-first 零触碰声明） | 无前置（可并行）；**W-03 验证联动 R1-05**（2.1-C7-007 失效判据）；W-01 B1-10 P1（三登记点 C7-102/C7-001/C4-R2-001 统一）；W-07/W-08=G1 外部 JDBC 治理链批内协同 | **W-05 携反向钉定同 diff 改写**（TestMetaDataSourceConnectionSecurity:776-792 改断言连接属性路径；W-01 连接级 setNetworkTimeout 不触红该测试）；外部 JDBC 超时单点收口（withConnection/queryTimeout/setNetworkTimeout）；W-03 热更新失效传播（NopRuleNode 子行一等 CRUD 面）；W-12 半个成功归一（R10）；G2 retention×reJudge 同表契约注记（W-28=P3 条款） |
| R3-06 | 5.6 graph-duckdb 批 | 5.6-W-02；5.6-W-03；5.6-W-05；5.6-W-06；5.6-W-07；5.6-W-08 | nop-duckdb / `./mvnw test -pl nop-duckdb -am` | `docs-for-ai/03-modules/nop-duckdb.md`（nop-graph owner doc drift→缺口声明随 R-G） | **nop-graph-api plan-first**（W-06 唯一触碰 api 叶工单；实施前 plan audit） | 无前置（可并行）；W-04 在 R3-07；W-01 在 R1-07；W-08=R06 cross-ref 不并单（单元裁定维持） | W-02 open/close 世代守卫（IDuckDbEngine L16 契约修复）；W-03 CTAS table-exists 守卫（TABLE_EXISTS+bizFatal fail-fast 范式）；**W-06 plan audit 前置声明**；W-05/W-07 随组验收 |
| R3-07 | Leiden 交叉批（6.3↔5.6 两侧同批） | 6.3-W-03；5.6-W-04 | nop-code；nop-duckdb / `./mvnw test -pl nop-code -am`；`./mvnw test -pl nop-duckdb -am` | `docs-for-ai/03-modules/nop-code.md`；`docs-for-ai/03-modules/nop-duckdb.md` | 无 | 无前置（可并行；与 R3-06/R3-09 协调排期） | 独立+交叉引用两侧**同批**（6.3 立消费侧单飞缺失、5.6 立 detector 本体+调用面守卫——ledger §四.5）；CWTS 全库零 interrupt 面 daemon 化+有界执行；单飞/退避接入 6 触发点 |
| R3-08 | 6.2 tools-parse 批 | 6.2-W-01；6.2-W-02；6.2-W-03；6.2-W-04；6.2-W-05；6.2-W-07；6.2-W-08；6.2-W-09 | nop-tools / `./mvnw test -pl nop-tools -am` | `docs-for-ai/03-modules/nop-refactor.md`；`docs-for-ai/03-modules/nop-treesitter.md`（nop-lint/nop-jq owner doc drift→R-G） | **W-02 Route-B 触 nop-core plan-first**（`EvalExprProvider` 补 volatile；AGENTS.md 框架核心行）——Route-B 为可选项，替代路径=工单行显式登记（6.2 反向保护#2 锁定修复路径） | 无前置（可并行）；批内次序：**W-03 与 W-10 同点落地**（W-10=P3 条款，激活随批）；W-04 承接 2026-09 G10-01-02 超集 | **W-02 携反向保护#2**：TestLintDeadlineExecutor:84-85 全局槽钉定测试类重写同批；**W-08 携反向保护#8**（TestNopRefactorBizModel:200-202 同 diff）；W-01 代际栅栏（stale EOF/杀健康新进程/孤儿进程三失效面）；W-03 .tmp 互斥（atomicWriteText 模式） |
| R3-09 | 6.3 code-index 批 | 6.3-W-01；6.3-W-02 | nop-code / `./mvnw test -pl nop-code -am` | `docs-for-ai/03-modules/nop-code.md` | 无（零 api 叶声明） | 软依赖 R3-07（W-03 同轮协调）；**S2 反向约束写入本批 exit criteria** | **6.3-W-02 修复或任何索引并行化若引入 analyzer 缓存/跨文件复用 → 须先满足 6.4-C10-W-01 测试先置（adapter assertNotSame+CSharp 交错并发断言，今日全缺），并触发 6.4-CL-101 升 P1 复核**（6.4 裁定 §四.3/§四.4）；W-01 查询三动作 LIMIT/列投影（R02 三范式）+findByMetric 上界；W-02 修复面限于 nop-code-service 消费侧（无需延伸 lang-* 六模块）；G11-15-02 承接面（C1-001）在 R4-31 |
| R3-10 | 6.5 peripheral 批（CL-301 通道批） | 6.5-C1-002；6.5-C6-001；6.5-W-03；6.5-W-04；6.5-W-05；6.5-W-06 | nop-dev-tools（idea-plugin 站点）+ JavaScriptWorker 所在模块 / Maven 面 `./mvnw test -pl <模块> -am`；**idea-plugin 站点=Gradle/harness 通道（§四.1）** | nop-dev-tools 无 owner doc→缺口声明（6.5 四模块无 owner doc 整体登记→R-G） | 无（nop-api-debugger 近失裁定非契约叶） | 无前置（可并行）；W-01 R21 纪律对齐 | **CL-301 三选一通道写入验收**（gradlew harness 入 CI / 可测逻辑下沉 plain-JVM seam / 工单行显式登记「验证通道=Gradle/harness」）：覆盖 W-02（P1）与 W-03/W-05（G90 idea-plugin 站点）+ watch 级 W-07 宣读；W-02 socket 配对 close（TestRemoteDebugger.tearDown 参照，C10-002）；W-01 停机拒绝语义（future 族第 6 例，C10-001） |

### §二.4 R4 批次（P2 R4+ 单元束 + 跨单元特批）

| 批号 | 标题 | finding ID 清单 | 模块 / 验证命令 | Owner Doc 锚点 | PA 授权来源 | 依赖 / 可并行 | exit criteria 增补 |
|---|---|---|---|---|---|---|---|
| R4-01 | 1.1 流核并发正确性束（G01） | 1.1-C1-003；1.1-C1-004；1.1-C1-005；1.1-C1-007；1.1-C1-008；1.1-C2-003；1.1-C2-004；1.1-C2-005；1.1-C3-009；1.1-C4-005；1.1-C4-006；1.1-C4-012；1.1-C7-005；1.1-C7-007；1.1-C7-012 | nop-stream / `./mvnw test -pl nop-stream -am` | `docs-for-ai/03-modules/nop-stream.md` | 无 | 软依赖 R2-07（同模块 P1 先行） | C10-010~016/023 共 8 条验收前置（另承 1.2-C10-006 锚定 1.1-C3-009） |
| R4-02 | 1.1 资源包络 + FAM-2 束（G02+G03） | 1.1-C5-004；1.1-C5-005；1.1-C5-006；1.1-C5-012；1.1-C8-005；1.1-C8-009；1.1-FAM-2 | nop-stream / `./mvnw test -pl nop-stream -am` | `docs-for-ai/03-modules/nop-stream.md` | 无 | 软依赖 R2-07 | C10-017~019/021 + C10-022 验收前置 |
| R4-03 | 1.2 连接器 MA 束 + 包络声明（G05+G09） | 1.2-MA-09；1.2-MA-10；1.2-MA-11；1.2-MA-13；1.2-MA-14；1.2-MA-15；1.2-MA-16；1.2-MA-17；1.2-MA-18；1.2-C8-002；1.2-C8-009；1.2-C8-012 | nop-stream / `./mvnw test -pl nop-stream -am` | `docs-for-ai/03-modules/nop-stream-connectors.md` | 无 | 软依赖 R2-08；MA-16=R01 保留策略对齐 R1-07；MA-11=R08 与 4.3-MA-16 同根因对批（R4-26 注记） | MA-11 fd 计数回归复用 R07 先例；MA-13 与 MA-05 保留策略配套 |
| R4-04 | 1.2 并发正确性 + 失败路径泄漏束（G07+G08） | 1.2-C3-005；1.2-C3-006；1.2-C3-010；1.2-C3-011；1.2-C3-012；1.2-C6-005；1.2-C6-006；1.2-C6-007；1.2-C6-011；1.2-C7-003；1.2-C7-010 | nop-stream / `./mvnw test -pl nop-stream -am` | `docs-for-ai/03-modules/nop-stream-connectors.md` | 无 | 软依赖 R2-08；**C3-006 与 4.1-MA-10 同案双轨同批**（R4-24，family-clustering §四#9） | C10-015 随 1.2-C3-011 |
| R4-05 | 1.3 Replay/流语义束（G11） | 1.3-MA-02；1.3-MA-03；1.3-MA-04；1.3-MA-05；1.3-C1-002；1.3-C1-003；1.3-C1-005；1.3-C3-005；1.3-C6-003；1.3-C6-005 | nop-stream / `./mvnw test -pl nop-stream -am` | `docs-for-ai/03-modules/nop-stream.md` | 无 | 软依赖 R2-09 | C10-005（MA-03 收口门）、C10-006（C1-003 trip-wire 反向约束，全矩阵唯一）验收前置 |
| R4-06 | 1.4 调度账本束 a（G13 前半） | 1.4-MA-02；1.4-MA-03；1.4-MA-04；1.4-MA-07；1.4-C1-003；1.4-C1-004；1.4-C2-004；1.4-C2-007；1.4-C4-002；1.4-C4-005 | nop-job / `./mvnw test -pl nop-job -am` | `docs-for-ai/03-modules/nop-job.md` | 无 | 依赖 R1-01/R2-10 | C4-005 跨单元关联 1.2-MA-06 批次对齐（R1-03）；C10-004/006/007/008 验收前置 |
| R4-07 | 1.4 调度账本束 b（G13 后半） | 1.4-C5-002；1.4-C5-004；1.4-C7-003；1.4-C7-004；1.4-C7-005；1.4-C7-006；1.4-C7-007；1.4-C7-009；1.4-C7-010；1.4-C7-011 | nop-job / `./mvnw test -pl nop-job -am` | `docs-for-ai/03-modules/nop-job.md` | 无 | 软依赖 R4-06 | C7-004=R01 三表保留族（对齐 R1-07 设计） |
| R4-08 | 1.5 闸门/throttle 束（G14） | 1.5-MA-06；1.5-MA-07；1.5-MA-10；1.5-MA-11；1.5-MA-12；1.5-MA-13；1.5-MA-14；1.5-MA-15；1.5-MA-16；1.5-MA-18；1.5-MA-20；1.5-MA-22 | nop-task；nop-wf / `./mvnw test -pl nop-task -am` | `docs-for-ai/03-modules/nop-task.md` | 无 | 软依赖 R2-11 | C10-002（MA-11）、C10-013（MA-13）验收前置 |
| R4-09 | 1.5 图执行/扫描 infra 束（G15，C7-006 拆出至 R4-32） | 1.5-C1-006；1.5-C3-003；1.5-C3-004；1.5-C4-003；1.5-C4-004；1.5-C4-007；1.5-C5-005；1.5-C5-006；1.5-C7-004；1.5-C7-007；1.5-C7-009；1.5-C7-010；1.5-C7-014 | nop-task；nop-wf / `./mvnw test -pl nop-task -am` | `docs-for-ai/03-modules/nop-task.md` | 无 | 软依赖 R2-11/R4-08 | C4-003 已知未修复按同案对齐一处修复（2026-09 P2-3.1-001 cross-ref）；C10-010/012/014 验收前置 |
| R4-10 | 1.6 补偿/护栏束（G17，C7-011 拆出至 R4-32） | 1.6-C1-002；1.6-C1-004；1.6-C1-007；1.6-C3-008；1.6-C3-009；1.6-C3-010；1.6-C6-015；1.6-C7-007；1.6-C7-008；1.6-C7-010；1.6-C7-014；1.6-C7-015；1.6-C8-003；1.6-C8-013 | nop-retry；nop-tcc / `./mvnw test -pl nop-retry -am` | `docs-for-ai/03-modules/nop-retry.md`；`docs-for-ai/03-modules/nop-tcc.md` | 无 | 依赖 R2-12/R1-07 | C1-002=C10-005；C8-003=CL-005 同位点主处置；跨条化成员面以 inventory-p2-c10 §二 1.6 为准 |
| R4-11 | 1.7 generator/生命周期束 a（G23 前半） | 1.7-C1-001；1.7-C1-002；1.7-C1-003；1.7-C1-006；1.7-C5-003；1.7-C5-004；1.7-C6-003；1.7-C6-004；1.7-C6-007；1.7-C6-008；1.7-C7-004 | nop-batch / `./mvnw test -pl nop-batch -am` | `docs-for-ai/03-modules/nop-batch.md` | 无 | 依赖 R2-13 | C5-004 与 5.4-C5-004 双轨联动（R3-04）不双计；C10-001/002 验收前置 |
| R4-12 | 1.7 读侧/护栏束 b（G23 后半） | 1.7-C7-005；1.7-C7-006；1.7-C7-007；1.7-C7-008；1.7-C7-009；1.7-C7-012；1.7-C8-004；1.7-C8-007；1.7-C8-008；1.7-C8-009；1.7-C8-010 | nop-batch / `./mvnw test -pl nop-batch -am` | `docs-for-ai/03-modules/nop-batch.md` | 无 | 依赖 R2-13（**C8-001 先于 C8-009**） | C8-007 与 C8-004 绑定、C8-008 为 C8-004 前置（三面一次接线）；C5-004 双轨注记 |
| R4-13 | 2.1 锁纪律/调度器/destroy 束 a（G27 前半，C7-004 拆出至 R4-32） | 2.1-C1-004；2.1-C1-009；2.1-C1-010；2.1-C1-011；2.1-C2-003；2.1-C2-004；2.1-C2-007；2.1-C2-008；2.1-C2-019；2.1-C3-002；2.1-C3-008；2.1-C3-009；2.1-C3-010；2.1-C3-011；2.1-C3-012；2.1-C3-013；2.1-C3-018；2.1-C4-002 | nop-kernel / `./mvnw test -pl nop-kernel -am` | `docs-for-ai/04-reference/source-anchors.md`；`docs-for-ai/02-core-guides/ioc-and-config.md` | **框架核心引擎 plan-first** | 依赖 R1-01/R1-02/R2-01 | C1-010/C7-011 为 C2-004 子面去重注记（同 diff 覆盖）；C7-015 与 C7-010 同批 （本批 18 成员>15——可拆两次子交付，§一 自规则）|
| R4-14 | 2.1 销毁链/BlockingSource 束 b（G27 后半） | 2.1-C4-005；2.1-C4-011；2.1-C4-012；2.1-C5-003；2.1-C6-001；2.1-C6-003；2.1-C6-004；2.1-C6-005；2.1-C6-006；2.1-C6-007；2.1-C6-012；2.1-C7-002；2.1-C7-008；2.1-C7-010；2.1-C7-011；2.1-C7-015；2.1-C9-004 | nop-kernel / `./mvnw test -pl nop-kernel -am` | 同 R4-13 | **框架核心引擎 plan-first** | 软依赖 R4-13 | C6-005=销毁链族④；C9-004 与 2.1-C5-001 条化联合设计注记（C5-001 本体 deferred/G28） （本批 17 成员>15——可拆两次子交付，§一 自规则）|
| R4-15 | 2.2 配置刷新/订阅/插件/IoC 启动图/销毁链束（G31） | 2.2-C1-001；2.2-C1-004；2.2-C1-005；2.2-C1-008；2.2-C1-018；2.2-C1-019；2.2-C2-005；2.2-C2-007；2.2-C2-008；2.2-C2-009；2.2-C2-013；2.2-C4-007；2.2-C4-009；2.2-C4-010；2.2-C6-007；2.2-C6-008；2.2-C6-009 | nop-core-framework / `./mvnw test -pl nop-core-framework -am` | `docs-for-ai/02-core-guides/ioc-and-config.md` | **框架核心引擎 plan-first** | **与 R2-03/R2-04 同 PR 批次协同**（2.2 §8.4 防碎片化；P1 批先行、本批后置） | C4-009/C4-011+CL-019 同 PR（与 P1 C4-003）；C10-002/004/009/010/016/017 成员面验收前置 （本批 17 成员>15——可拆两次子交付，§一 自规则）|
| R4-16 | 2.3 WS/网关束 a（G36 前半） | 2.3-C1-002；2.3-C1-004；2.3-C1-005；2.3-C1-006；2.3-C2-001；2.3-C2-003；2.3-C3-004；2.3-C3-005；2.3-C3-006；2.3-C3-009；2.3-C3-010；2.3-C3-011 | nop-service-framework / `./mvnw test -pl nop-service-framework -am` | `docs-for-ai/02-core-guides/service-layer.md` | 无 | 软依赖 R1-04/R2-05/R2-06 | C3-010/C4-003=CL-025 平台原语前置（R2-05 已落地 SSO 面注记） |
| R4-17 | 2.3 auth/流缓冲/dispatchAll 束 b（G36 后半） | 2.3-C4-001；2.3-C4-002；2.3-C4-003；2.3-C4-004；2.3-C4-005；2.3-C4-008；2.3-C8-003；2.3-C8-007；2.3-C8-008；2.3-C8-009；2.3-C9-003；2.3-C9-005 | nop-service-framework / `./mvnw test -pl nop-service-framework -am` | `docs-for-ai/02-core-guides/service-layer.md` | 无 | 软依赖 R4-16；G-D 三面与 R2-06 批 A 对齐（chain25）；C4-002 阻塞子面单点在 C8-001（R1-03） | C10-004~006/011/012/015/017/018 成员面验收前置 |
| R4-18 | 2.4 ORM 切片束 a（G45 前半） | 2.4-C1-002；2.4-C1-004；2.4-C1-005；2.4-C1-006；2.4-C1-007；2.4-C1-008；2.4-C1-012；2.4-C1-013；2.4-C1-016；2.4-C1-017；2.4-C1-018；2.4-C1-019；2.4-C1-024；2.4-C1-025；2.4-C1-027 | nop-persistence / `./mvnw test -pl nop-persistence -am` | `docs-for-ai/02-core-guides/model-first-development.md` | **ORM plan-first**（若触 orm.xml） | 软依赖 R2-14 | C1-016 全单元唯一真并发回归钉（**修复不得弱化该钉**）；C1-027 与 R2-14 C2-004 共享设计；C10-003/005/008/011~017 成员面验收前置 |
| R4-19 | 2.4 ORM 切片束 b（G45 后半） | 2.4-C2-007；2.4-C2-009；2.4-C2-014；2.4-C2-016；2.4-C2-018；2.4-C3-002；2.4-C3-003；2.4-C3-004；2.4-C4-001；2.4-C4-002；2.4-C4-003；2.4-C5-001；2.4-C5-002；2.4-C5-005；2.4-C9-001 | nop-persistence / `./mvnw test -pl nop-persistence -am` | `docs-for-ai/02-core-guides/model-first-development.md` | **ORM plan-first**（若触 orm.xml） | 软依赖 R4-18 | **C2-016=C10-017 现状容忍钉：修复须先改 testMultipleCommit 行为钉**；C2-018=CL-200 与 C1-016 同批设计；C2-009 补钉义务 |
| R4-20 | 2.5 链复制/PubSub 关联束（G47，C7-013 拆出至 R4-32） | 2.5-MA-07；2.5-MA-08；2.5-MA-09；2.5-MA-11；2.5-C1-001；2.5-C1-002；2.5-C1-008；2.5-C3-010；2.5-C3-011；2.5-C3-012；2.5-C7-006；2.5-C7-008；2.5-C7-009 | nop-persistence / `./mvnw test -pl nop-persistence -am` | `docs-for-ai/02-core-guides/model-first-development.md` | 无 | 软依赖 R2-15 | C1-008 与 MA-05 同文件同 diff；C7-008 对齐 P1 C3-008（R2-15）；C10 面以 inventory-p2-c10 §二 2.5 为准 |
| R4-21 | 3.1 会话/配额/取消束 a（G52 前半，C7-004 拆出至 R4-32） | 3.1-MA-10；3.1-MA-12；3.1-MA-13；3.1-MA-14；3.1-MA-15；3.1-MA-16；3.1-MA-18；3.1-MA-20；3.1-MA-21；3.1-C1-010 | nop-ai / `./mvnw test -pl nop-ai -am` | `docs-for-ai/03-modules/nop-ai.md` | 无 | 依赖 R2-16；**MA-16 不得指向 globalTimer（约束在 R1-03 落地后解除）** | C5-002=CL-041 安全面随单；C10-004/005/012~015/018/019 成员面验收前置 |
| R4-22 | 3.1 资源卫生束 b（G52 后半） | 3.1-C3-005；3.1-C3-006；3.1-C3-029；3.1-C5-002；3.1-C5-017；3.1-C7-003；3.1-C7-005；3.1-C7-006；3.1-C7-007；3.1-C7-015 | nop-ai / `./mvnw test -pl nop-ai -am` | `docs-for-ai/03-modules/nop-ai.md` | 无 | 软依赖 R4-21 | watch C3-008（P2 级不计数）护栏随 C10-001 注记 |
| R4-23 | 3.2 工具输出/路径束（G54） | 3.2-MA-07；3.2-C1-004；3.2-C1-005；3.2-C4-007；3.2-C6-007；3.2-C6-009；3.2-C8-007；3.2-C8-008；3.2-C8-010；3.2-C8-012 | nop-ai / `./mvnw test -pl nop-ai -am` | `docs-for-ai/03-modules/nop-ai.md` | 无 | 软依赖 R1-06 | C6-009 按 family-clustering §三.5#8 方案落地（cancel 条目标记+延迟清理，不改 remove 顺序对外契约）；C8-010 与 C8-012 同批 |
| R4-24 | 4.1 REGISTRY 生命周期/交付语义束（G62） | 4.1-MA-01；4.1-MA-03；4.1-MA-04；4.1-MA-06；4.1-MA-07；4.1-MA-10；4.1-C1-002；4.1-C1-012；4.1-C3-004；4.1-C3-005；4.1-C3-006；4.1-C8-005；4.1-C8-007 | nop-message / `./mvnw test -pl nop-message -am` | `docs-for-ai/03-modules/nop-message.md` | 无 | **依赖 R1-08（设计联动）**：REGISTRY 契约一次裁决（stop-clear vs TTL/evictIdle vs 条件移除，覆盖 MA-03/MA-04/C1-002 三单+2.5 消费面）禁止各修一半；**钉义务全在本批**：C1-012=反向钉死#3（TestDebeziumEngineProperties 四处 setup 先改）、C3-004=反向钉死#1 同批改写、C1-002=软钉#5 纳入裁决面；MA-10 与 1.2-C3-006 同批（R4-04）；MA-01 与 3.1-MA-11 consumeAwaitTimeout 契约对齐（R2-16） | C10-001~008/010 成员面验收前置 |
| R4-25 | 4.2 选主外生命周期/路由/Sentinel 束（G65） | 4.2-MA-03；4.2-MA-05；4.2-MA-08；4.2-MA-09；4.2-C1-005；4.2-C2-002；4.2-C8-001；4.2-C8-006；4.2-C8-007 | nop-cluster / `./mvnw test -pl nop-cluster -am` | nop-cluster 无 owner doc→缺口声明 | 无 | 软依赖 R2-18；**owner 裁决前置**：MA-08 零权重语义（§五-3，反向钉死#3 改写以裁决结论为契约）、C8-001 活跃计数实现形态（§五-4） | MA-05 反向钉死#1 同 diff 改写（rejected future+异常统计新断言）；C10-005/007/008/009/010 成员面验收前置 |
| R4-26 | 4.3 交付与生命周期束 a（G67 前半） | 4.3-MA-08；4.3-MA-09；4.3-MA-10；4.3-MA-11；4.3-MA-12；4.3-MA-13；4.3-MA-14；4.3-MA-15；4.3-MA-16；4.3-C6-028；4.3-C1-003；4.3-C1-005；4.3-C1-010；4.3-C1-011 | nop-network / `./mvnw test -pl nop-network -am` | `docs-for-ai/03-modules/nop-network.md` | 无（三 api 叶零触碰声明） | 软依赖 R2-19/R2-20 | **MA-15 携 RP-1 断言级反向钉死 2 处先改测试**（4.2 先例）；vertx-mqtt 面条化注记=§三.5#9；MA-08=R03 锁内 I/O 族；先行失败测试 16 处为验收物 |
| R4-27 | 4.3 交付与生命周期束 b（G67 后半） | 4.3-C1-014；4.3-C1-016；4.3-C1-018；4.3-C1-019；4.3-C3-009；4.3-C3-023；4.3-C3-025；4.3-C3-026；4.3-C3-033；4.3-C6-003；4.3-C6-010；4.3-C6-012；4.3-C6-013；4.3-C6-027 | nop-network / `./mvnw test -pl nop-network -am` | `docs-for-ai/03-modules/nop-network.md` | 无（三 api 叶零触碰声明） | 软依赖 R4-26 | C6-003 前案并轨不重复立项（2026-09 3.9 前案 cross-ref）；MA-14/C6-028 同提交收敛 |
| R4-28 | 4.4 双宿主/反向钉束（G70） | 4.4-MA-03；4.4-MA-04；4.4-MA-05；4.4-C1-003；4.4-C1-004；4.4-C4-001；4.4-C4-002；4.4-C4-004；4.4-C6-004；4.4-C6-005 | nop-host 所在聚合模块 / `./mvnw test -pl <宿主聚合模块> -am` | nop-host 无 owner doc→缺口声明 | 无 | 软依赖 R2-21；**RP-2/RP-4→C4-002（stub 可抛错+取消断言先于修复）、RP-3→C4-004（新增不预初始化测试上下文）**；watch C1-005 volatile 搭车 C6-004 同 diff | MA-03=B1-09 维持 P2+条件回升条款（族修复单 diff；回升条款=网关部署面实证消费方无法区分流结束/流故障即升 P1）；C4-001=§三.5#4 各自判级维持 |
| R4-29 | 4.5 Lucene/watch/测试基建束（G71） | 4.5-MA-01；4.5-MA-02；4.5-MA-03；4.5-MA-04；4.5-I-C4-002 | nop-search；nop-runner（以实际模块为准）/ `./mvnw test -pl <模块> -am` | nop-search 无 03-modules owner doc→缺口声明 | 无（Protected Area 零触碰） | 软依赖 R1-01（MA-01 R04 面已落地） | **MA-01=R01（S-04 正向约束：修复不得拆分）**；MA-02 修复陷阱=禁 watcher.stop() 统一（CL-011 footgun）；MA-04=测试基建本体（基础设施层批量受益）；C10-001/003/004/005/007 成员面验收前置 |
| R4-30 | 6.1 搜索/FFM/聚合束（G85） | 6.1-W-01；6.1-W-03；6.1-W-05；6.1-W-14 | nop-search / `./mvnw test -pl nop-search -am` | nop-rg/nop-find 无 owner doc→缺口声明（6.1 drift 登记→R-G） | 无（零 api 叶） | 无前置（可并行） | 先红后绿 P2-01~03 已登记；W-03=R27 双机实测复现面；W-14 对照 collectJar 正确形态（BG-5）；C10 缺保护 P2-01~04 验收前置 |
| R4-31 | 6.3 读路径/C5 族束（G88） | 6.3-W-04；6.3-W-06；6.3-W-07；6.3-W-08；6.3-W-09；6.3-W-11；6.3-W-12；6.3-W-13；6.3-W-14；6.3-W-15；6.3-W-16；6.3-W-17；6.3-W-18；6.3-W-19 | nop-code / `./mvnw test -pl nop-code -am` | `docs-for-ai/03-modules/nop-code.md` | 无 | 软依赖 R3-09/R3-07 | W-04=外部进程桥第 6 例（TP-004）；W-11 承接 2026-09 G11-15-02 超集防双计；W-13~W-19=R02 C5 族 7 张同单元同根因自并（与 R3-09 W-01/W-02 三范式对齐）；C10 缺保护 TP-004/006~009/011~019 验收前置 |
| R4-32 | R28 墙钟敏感跨单元批（单调时钟注入） | 1.5-C7-006；1.6-C7-011；2.1-C7-004；2.3-C8-005；2.5-C7-013；3.1-C7-004；3.3-C7-002 | nop-task；nop-retry；nop-kernel；nop-service-framework；nop-persistence；nop-ai / 各 `./mvnw test -pl <模块> -am` | `docs-for-ai/04-reference/source-anchors.md`（IClock/ClockProvider 锚点）；各单元 owner doc | **框架核心 plan-first**（若 IClock 设施落 nop-kernel/nop-api-core） | 软依赖 R1-02（2.1-C7-004 所在批）；3.3-C7-003（P3 条化回升面，随 B1-11）为同批第 8 面——激活时并入，现不入批 | IClock/ClockProvider 单调时钟注入设施一处设计、七站点落地；跨节点令牌桶（2.5-C7-013）与 2.3-C8-005 同批设计；C7-CL-034（2.1）随批 |
| R4-33 | 移交接收轨批（2.3-C1-010 → nop-auth-service） | 2.3-C1-010 | nop-auth / `./mvnw test -pl nop-auth -am` | `docs-for-ai/03-modules/nop-auth.md` | nop-auth 域变更 ask-first（如触认证模型） | 无前置（可并行） | **双登记维持**：CRA 台账保留发现行，修复计数归接收侧（nop-auth-service）；DaoUserContextCache 条件 UPDATE+刷新单飞；防双计注记（family-clustering §四#3） |

### §二.5 P2 裁决组→批次映射表（90 组 + G91，机械对账基准）

> 修（in-scope）483 条 = 提前 44 + R4+ 439。提前 44 条按本表「批次」列落位（含 G50-MA-17 由 P1 台账新行承载）；R4+ 组整组或拆分 a/b 落位。deferred 77 条不入批（successor 见 p2-adjudication §一.3）。

| 组 | 成员数 | 批次 | 备注 |
|---|---|---|---|
| G01 | 15 | R4-01 | |
| G02 | 6 | R4-02 | |
| G03 | 1 | R4-02 | |
| G04 | 5 | — | deferred（CL/域留档） |
| G05 | 9 | R4-03 | |
| G06 | 1 | — | deferred（触发条款） |
| G07 | 5 | R4-04 | |
| G08 | 6 | R4-04 | |
| G09 | 3 | R4-03 | |
| G10 | 2 | — | deferred（C8-008→R-G；C8-011→CL） |
| G11 | 10 | R4-05 | |
| G12 | 3 | R1-01 | 提前（R04 同 diff） |
| G13 | 20 | R4-06 + R4-07 | 拆分 10/10 |
| G14 | 12 | R4-08 | |
| G15 | 14 | R4-09（13）+ R4-32（C7-006） | R28 拆出 |
| G16 | 3 | — | deferred（R-G） |
| G17 | 15 | R4-10（14）+ R4-32（C7-011） | R28 拆出 |
| G18 | 1 | R2-12 | 提前（C8-012 同 diff） |
| G19 | 5 | — | deferred（触发条款） |
| G20 | 1 | — | deferred（R-G） |
| G21 | 3 | R1-01（C6-010）+ R2-13（C6-009、C7-011） | 提前拆分 |
| G22 | 1 | R1-03 | 提前（R13） |
| G23 | 22 | R4-11 + R4-12 | 拆分 11/11 |
| G24 | 7 | R1-01（C3-015）+ R1-05（C9-001、C9-010）+ R2-01（C1-003、C1-014、C2-002、C2-017） | 提前拆分（随对应 P1 族批） |
| G25 | 3 | R1-02 | 提前（R13 设施面） |
| G26 | 5 | R1-04 | R4+ 口径提前执行（api-core plan-first 批） |
| G27 | 36 | R4-13（18）+ R4-14（17）+ R4-32（C7-004） | 拆分 + R28 拆出 |
| G28 | 4 | — | deferred（触发条款） |
| G29 | 2 | — | deferred（CL/域留档） |
| G30 | 6 | — | deferred（R-G×2、CL×2、触发条款×2） |
| G31 | 17 | R4-15 | |
| G32 | 1 | R1-02 | 提前（R13） |
| G33 | 1 | — | deferred（触发条款） |
| G34 | 1 | — | deferred（R-G） |
| G35 | 1 | — | deferred（CL/域留档） |
| G36 | 24 | R4-16 + R4-17 | 拆分 12/12 |
| G37 | 3 | R1-04 | 提前（R15） |
| G38 | 1 | R1-05 | 提前（别名随主 R24） |
| G39 | 1 | R1-04 | 提前（随 P1 C3-007） |
| G40 | 1 | R4-32 | R28 墙钟批 |
| G41 | 1 | R4-33 | 接收单元轨（nop-auth-service） |
| G42 | 4 | — | deferred（触发条款） |
| G43 | 2 | — | deferred（CL/域留档） |
| G44 | 2 | — | deferred（R-G） |
| G45 | 30 | R4-18 + R4-19 | 拆分 15/15 |
| G46 | 1 | — | deferred（触发条款） |
| G47 | 14 | R4-20（13）+ R4-32（C7-013） | R28 拆出 |
| G48 | 2 | R2-04（C1-010）+ R2-01（C6-005） | 提前拆分 |
| G49 | 4 | — | deferred（触发条款） |
| G50 | 3 | R1-01（MA-07）+ R1-06（MA-17=P1 新行承载、C7-014） | 提前；MA-17 计数迁移 |
| G51 | 3 | R1-03（C4-004、C4-009）+ R2-16（C4-015） | 提前拆分 |
| G52 | 21 | R4-21（10）+ R4-22（10）+ R4-32（C7-004） | 拆分 + R28 拆出 |
| G53 | 2 | — | deferred（触发条款） |
| G54 | 10 | R4-23 | |
| G55 | 2 | R1-03（C4-004）+ R1-04（C4-008） | 提前拆分 |
| G56 | 7 | — | deferred（触发条款；MA-05 反向钉-1 随条款） |
| G57 | 6 | — | deferred（B1-11 触发条款；护栏 checklist 入 R-G） |
| G58 | 7 | R2-06（6）+ R4-32（C7-002） | R4+ 组提前并批（3.3 同文件批 A）+ R28 拆出 |
| G59 | 1 | — | deferred（触发条款） |
| G60 | 5 | — | deferred（B1-12 触发条款） |
| G61 | 1 | — | deferred（触发条款） |
| G62 | 13 | R4-24 | |
| G63 | 2 | R1-01（C3-009）+ R1-08（C6-006） | 提前拆分 |
| G64 | 2 | — | deferred（触发条款） |
| G65 | 9 | R4-25 | |
| G66 | 2 | R1-03（C4-003）+ R1-01（C4-004） | 提前拆分 |
| G67 | 28 | R4-26 + R4-27 | 拆分 14/14 |
| G68 | 2 | R1-04 | 提前（R14/R15） |
| G69 | 2 | — | deferred（触发条款） |
| G70 | 10 | R4-28 | |
| G71 | 5 | R4-29 | |
| G72 | 1 | — | deferred（触发条款） |
| G73 | 5 | R3-01 | R4+ 并入单元批 |
| G74 | 7 | R3-02 | R4+ 并入单元批 |
| G75 | 1 | R1-07 | 提前（R01 族行） |
| G76 | 12 | R3-03 | R4+ 并入单元批 |
| G77 | 1 | — | deferred（触发条款） |
| G78 | 7 | R3-04 | R4+ 并入单元批 |
| G79 | 2 | R3-04 | 提前（R02 双轨/同 diff） |
| G80 | 7 | R3-05 | R4+ 并入单元批 |
| G81 | 1 | R1-01 | 提前（R04 绝缘层站点） |
| G82 | 1 | — | deferred（CL/域留档） |
| G83 | 3 | R3-06 | R4+ 并入单元批 |
| G84 | 1 | R3-06 | R4+ 并入单元批（plan-first 前置） |
| G85 | 4 | R4-30 | |
| G86 | 6 | R3-08 | R4+ 并入单元批 |
| G87 | 1 | — | deferred（触发条款） |
| G88 | 14 | R4-31 | |
| G89 | 2 | — | deferred（触发条款） |
| G90 | 4 | R3-10 | R4+ 并入单元批（CL-301 通道） |
| G91 | 4（无本体 C10，非 P2） | R-G | deferred（§五） |

---

## §三 覆盖性核对（按 plan「批次覆盖口径定义」五条机械核对）

1. **P1 行账**：P1 台账 179 行 − 豁免 1 行（2.3-C9-012）= **178 行批次分配**；加 B1-04 终裁回升新增行（3.1-MA-17）1 行 = **179 行入批**；台账总行数 = **180**（2026-10-23 已回填 cra-index，见 §二.1 R1-06 批与 cra-index P1 台账注记）。
2. **每行恰好一批**：§二.1–§二.4 各批 finding 清单并集（P1 部分）= 179 行，逐行唯一批次；机械校验脚本 `_tmp/check-batch-coverage.mjs` 输出 `P1 UNION CHECK PASS: 179/179`（P1 台账行 ↔ 批次清单双向比对，重合/遗漏/重复均为 0）。逐单元分布：1.1×10、1.2×10、1.3×4、1.4×2、1.5×7、1.6×14、1.7×8、2.1×5、2.2×11、2.3×6（+豁免 1）、2.4×13、2.5×13、3.1×14（含 MA-17 新行）、3.2×5、3.3×4、3.4×4、4.1×3、4.2×4、4.3×15、4.4×4、4.5×1、5.1×2、5.2×3、5.3×2、5.4×1、5.5×3、5.6×4、6.2×2、6.3×3、6.5×2，合计 179。
3. **P2 修项全部入批**：修 483 = 提前 44 + R4+ 439，§二.5 组→批映射表逐组落位；批内 P2 记录 482 条 + MA-17（提前成员，由 P1 台账新行承载，P2 台账 G50 保留承接行）= 483。分批分布（机械核对 `_tmp/check-p2-batch-map.mjs` PASS）：提前 44 = R1 批 31 + R2 批 10 + R3 批 2（G79）+ MA-17（P1 行）；R4+ 439 = R1 批 5（G26 api-core 口径提前执行）+ R2 批 6（G58）+ R3 批 52（G73×5、G74×7、G76×12、G78×7、G80×7、G83×3、G84×1、G86×6、G90×4）+ R4 批 376（R4-01~R4-31 共 368 + R4-32 墙钟 7 + R4-33 移交轨 1）。每条 P2 修项 ID 恰好出现在一个批次 finding 清单中（P2 台账成员并集 560/560 校验维持不变，见 p2-adjudication §七）。
4. **豁免清单**（不入批次，cross-reference 关闭）：见 §六。
5. **C10 跟随本体**：全部 C10 缺保护条目随本体工单行归属，作为该批 exit criteria E2「验收前置：补测与修复同批」承载，不独立计批；无本体 C10 条目 4 条（1.2-C10-016、1.3-C10-007/008、1.4-C10-009，G91）→ 治理批 R-G（§五）；「R4+ 独立测试保护缺口」口径：类型①（本体在 R1–R3 修复但补测遗留）= **0 条**（全部随本体批），类型②（无在案缺陷的独立补测项）= G91 4 条 → R-G。

---

## §四 批次约束落位（inventory-ledgers §四 → 批次）

### §四.1 CL-301 Gradle 验证通道（写入 R3-10 exit criteria）

idea-plugin 站点回归验证**不得依赖 `./mvnw test`**（`nop-dev-tools/pom.xml:20-24` 不含；CL-301）。四站点 W-02/W-03/W-05/W-07 通道三选一写入验收标准：① `./gradlew test`（IntelliJ harness）纳入 CI；② 可测逻辑下沉 plain-JVM seam（connector 配对/registrar 引用计数）；③ cra-index 工单行显式登记「验证通道=Gradle/harness」。落批：**R3-10**（W-02 P1 + G90 的 W-03/W-05；watch 级 W-07 随批宣读）。Maven 反应器内工单（6.5-W-01、6.5-W-04、6.5-W-06）通道不变。

### §四.2 反向钉死/反向保护义务（修复捆绑测试改写）落批

| 来源 | 义务 | 落批 |
|---|---|---|
| 1.7 | C7-001 测试 N1 参数化改写前置；C5-002 测试 N2 同步改写 | R2-13 |
| 1.7 | C6-001 与 C6-010 同 diff（try 上移+catch Throwable） | R1-01 |
| 2.4 | C1-016 全单元唯一真并发回归钉（修复不得弱化） | R4-18 |
| 2.5 | MA-02 反向钉死改写随批 | R2-15 |
| 3.1 | gate3 测试字面钉 remove no-op（F4 断言补全，C10-003）；C7-014 携 R4 反向钉死改写 | R2-16；R1-06 |
| 3.2 | 反向钉-1 ShellIOTest:294-300（MA-05 变体）；反向钉 watch TestToolPathQueue | 随 G56 条款（激活时同批改写；R-G 条款登记册） |
| 3.4 | intern assertSame（修复 C1-008 必红；IND-11 强制测试改造项） | 随 G60 条款（激活时同批） |
| 4.1 | #1→C3-004、#3→C1-012、软钉→C1-002 → R4-24（exit criteria 已列）；#2→MA-08、#4→C1-008 = P3 本体未入批，钉随本体——按 4.1 裁定「与同文件 P2 工单同 PR 顺带」触及时同 diff 改写，登记 R-G 条款登记册 | R4-24 |
| 4.2 | 硬钉#1→MA-05、硬钉#3→MA-08（以 owner 零权重裁决为契约）、LeastActive 假保护→C8-001 | R4-25 |
| 4.3 | RP-1（MA-15 两处先改测试）+ 先行失败测试 16 处 = 验收物；P0 Windows 门控 | R4-26/R4-27；R1-00 |
| 4.4 | RP-2/RP-4→C4-002、RP-3→C4-004（先于修复）；RP-1→CL-008 watch | R4-28 |
| 4.5 | S-04 正向约束（MA-01 不得拆分）；locallyOwned 修复陷阱 | R4-29 |
| 5.1 | C10-007 反向钉死 = I-02 同 diff 前置 | R3-01 |
| 5.2 | TestInMemoryCodeCacheContract（:177-194）MA-02 修复同 diff 改写 | R3-02 |
| 5.3 | testSlowExecutionRaceWorkerSuccessOverwritesScanFailed 同 diff 反转（W-02） | R3-03 |
| 5.5 | TestMetaDataSourceConnectionSecurity:776-792 同 diff 改写（W-05） | R3-05 |
| 6.2 | #2→W-02（TestLintDeadlineExecutor 类重写，锁定 Route-B 路径）；#8→W-08 同 diff | R3-08 |
| 6.2 | #9→W-13、#11→W-15（P3 归档件，激活时随批删改） | 条款登记（R-G） |

### §四.3 plan-first / ask-first 授权来源落批

| 约束 | 来源（AGENTS.md Protected Areas 表 + 单元裁定） | 落批 |
|---|---|---|
| `nop-api-core`（io.nop.api.core.*）plan-first；SPI 变更列全部实现为回归面 | 2.1 api-core 移交包 | **R1-04** |
| 框架核心引擎（nop-kernel/nop-core-framework 内部）plan-first（设计文档+回归测试） | AGENTS.md 框架核心行 | R1-01、R1-02、R1-05、R2-01、R2-03、R2-04、R4-13、R4-14、R4-15、R4-32 |
| 权限/认证模型（nop-auth）ask-first | 5.1 裁定 §九 | **R1-07**（5.1-MA-01）、**R3-01**（全批）、**R4-33**（2.3-C1-010 移交接收轨） |
| ORM 模型结构（model/*.orm.xml）plan-first | 5.2-I-03 唯一索引（§三.5#6）；2.4 若触 orm | R3-02（I-03 实施前置 plan audit）、R2-14/R4-18/R4-19（零触碰则声明） |
| nop-graph-api plan-first（W-06 唯一触碰 api 叶） | 5.6 裁定 §六.5 | **R3-06** |
| Route-B 触 nop-core plan-first（EvalExprProvider volatile） | 6.2 裁定；P1 台账行已注记 | **R3-08**（W-02；替代路径=工单行显式登记） |
| 4.3 三 api 叶（nop-http-api/nop-rpc-api/nop-integration-api）plan-first——零触碰 | 4.3 计数口径 | R2-19/R2-20/R4-26/R4-27 零触碰声明 |
| 4.4-C6-009 触 nop-http-api 须 plan-first | 4.4 计数口径 | 批外注记（该工单未入 179/483 修复面；如未来立项随 migration note） |
| 5.1-MA-06 触 nop-auth.orm.xml ORM plan-first | 5.1 裁定 §九 | R3-01（若触发） |
| 5.3 nop-file-api / 5.5 双 api 叶 / 3.4 nop-ai-api / 6.4 nop-code-api / 6.5 零 api 叶 / 4.5 零触碰 | 各单元口径 | 对应批零触碰声明（R3-03、R3-05、R2-17、R3-10、R4-29） |
| 3.4-O-2（IEmbeddingModel SPI 无 ICancelToken）plan-first 在归属单元立项 | family-clustering §三.5#17 | 移交轨（R-G 条款登记册；不在本 campaign 立项） |

### §四.4 跨单元顺序协同约束 → 依赖标注（inventory-ledgers §四.5 摘要落位）

| 约束 | 落位 |
|---|---|
| globalTimer 先平台后使用方（3.1 §5.1-2；4.2 §五-5） | R1-02 → R1-03 硬依赖；R4-21（MA-16）约束解除注记 |
| 1.7 C8-001 先于 C8-009；C6-001/C6-010 同 diff | R2-13 → R4-12；R1-01 |
| 2.5 C3-008 修复顺序前置 MA-06 | R2-15 批内次序 |
| 4.1 §五-5 REGISTRY 契约一次裁决禁止各修一半 | R1-08 ↔ R4-24 设计联动 |
| 1.7 C7-001 与 WI 1.5 C7-001 / WI 1.6 C8-001 fencing 对齐（避免两套 fencing） | R2-13 依赖 R2-11 + R1-01 |
| 1.7 C7-003 与 1.6 C7-001 账本保留族批次对齐 | 同批 R1-07 |
| 1.7 C8-003 兜底池平台级统一评估 | R1-03（设施面 R1-02 同批先行） |
| 3.2 沙箱族批次协同 + 护栏共享形态 | 同批 R1-06 |
| 4.2 owner 裁决前置（零权重/活跃计数） | R4-25 exit criteria 前置声明 |
| 5.5 G2 retention×reJudge 同表契约 | R1-07（W-02）+ R3-05 注记（W-28=P3 条款） |
| 5.5 G3 告警通道三失效层 | R3-05（W-12）+ 条款（W-24/W-25=P3） |
| 6.2 批次约束 4 组（W-03+W-10 同点等） | R3-08 批内次序 + P3 条款 |
| 6.3-W-03 与 5.6-W-04 两侧同批 | 同批 R3-07 |
| 4.3-MA-02 与 3.1-C7-001 两侧同批 | 同批 R2-02 |
| 2.4 C2-004+C1-027 共享设计 | R2-14 ↔ R4-18 注记 |
| 2.2 §8.4 七修复批次防碎片化 | R2-03/R2-04/R4-15 协同标注 |
| 6.3-W-02 上游保证（修复面限于消费侧） | R3-09 exit criteria |
| 5.3 CL-020 平台级修复归 2.1-C3-001 | R3-03 注记（R1-01 承载） |
| 4.3 P0 修复仅限 impl 叶 | R1-00 声明维持 |
| 3.1-MA-07 移交后保留 Error 注入护栏义务（C10-008） | R1-01 exit criteria |
| 4.1 §五-6 consumeAwaitTimeout 契约对齐 | R1-08（C8-002）↔ R2-16（MA-11）互注 |
| 5.2-MA-04 维持 P2 入 R01 族行 | R1-07 |
| 3.2 沙箱族判级统一（装配后生产形态） | R1-06（B1-04 落账） |
| 2.4 缓存失效世代号共享设计 | 见 C2-004/C1-027 行 |
| 4.4-C6-007 平台根因绝缘层注记 | R2-21 软依赖 R1-04/R1-01 |
| 5.5-W-01 若判级联动（B1-10 已终裁 P1） | R3-05（三登记点统一） |
| 6.4 双契约 watch 脆弱性（C10-W-01 先置） | R3-09 exit criteria（S2 反向约束） |

---

## §五 治理批 R-G 附录（治理轨批次；与修复批一同由 7.2 追加为 Phase 8 工作项行，行内声明产出物）

- **批号/标题**：R-G 治理批（可观测性/测试基建/死代码/条件化激活监控/demo 治理/Gradle 门禁/owner doc drift）。
- **Skill**：none（治理轨批次，非修复 plan 形态——行内声明产出物）。
- **成员（deferred successor=R-G，10 条 P2）**：1.2-C8-008（G10）；1.5-MA-09；1.5-MA-17；1.5-C8-015（G16）；1.6-C8-008（G20）；2.1-C1-006；2.1-C7-005（G30）；2.2-C1-014（G34）；2.3-C8-006；2.3-C9-006（G44）。
- **无本体 C10 条目（G91，4 条，非 P2 计数）**：1.2-C10-016；1.3-C10-007；1.3-C10-008；1.4-C10-009（1.3-C10-007 建议随 R2-09 批的 C3-008/C6-007 P1 加固）。
- **demo 治理轨**：6.5 demo 降口径 P3（W-09/W-11）与 6.1 benchmark 计数面>立案面口径的治理声明；6.5 四模块 + 5.4 nop-format + 5.6 nop-graph + 6.1 nop-rg/nop-find + 6.2 nop-lint/nop-jq 的 **owner doc drift 治理**（补 `docs-for-ai/03-modules/` 条目或 module-groups.md 登记）。
- **条件化激活监控**：B1-11 十三张潮（G57；护栏=渠道栈接线变更评审 checklist：beans.xml init/destroy + startAll/stopAll/dispatchInbound 非测试调用扫描）、B1-12 四张（G60）、S6 七张（G56；MA-05 反向钉-1 随条款）及各触发条款组（G06/G19/G28/G30/G33/G42/G46/G49/G53/G56/G57/G59/G60/G61/G64/G69/G72/G77/G87/G89）统一为**触发条款登记册**产出物；3.4-O-2 SPI 移交轨（§三.5#17）登记在册。
- **Gradle 门禁治理**：6.5-CL-301（构建门禁盲区）的通道决定与 CI 接入（与 R3-10 exit criteria 衔接）。
- **测试基建治理**：会红=0 十三例结构性形态的测试基建补强路线登记（各批 C10 验收前置为主承载，本批收口横向缺口）。
- **exit criteria**：产出物（触发条款登记册/接线评审 checklist/可观测性补齐清单/死代码清理/owner doc 补齐/Gradle 通道决定）落档并通过独立审查；不涉及产品行为变更。

---

## §六 豁免清单（不入批次，cross-reference 关闭）

| 行 | 去向 | 依据 |
|---|---|---|
| 2.3-C9-012（P1 台账行，修复状态 open（接收轨落名）） | **cross-ref→功能正确性维度**（接收轨落名=nop-service-framework-hotpath-C9-012；修复计数不计本单元 58 口径——异-5 维持） | 覆盖口径第 3 条；family-clustering §五.2 |
| 3.3 渠道栈 B1-11 六张 P1 回升券（MA-02/MA-03/MA-04/C4-004/C4-005/C8-002） | 未入 179 行台账（cra-index 仅计数）；**deferred+激活条款**（激活=startAll/stopAll/dispatchInbound 非测试调用或 beans.xml init/destroy；P1×6 直接入 P1 批次）——登记于 P2 台账 G57 + R-G 条款登记册，不计入本提案批内行数 | B1-11 终裁（family-clustering §三.2） |
| 4.4-C6-009（触碰 nop-http-api） | 未入 179/483 修复面（P3/watch 载体）；如未来立项须 plan-first | inventory-ledgers §四.4 |
| 3.4-O-2（SPI 设计层缺失） | 移交归属单元轨（WI 3.1 plan-first 立项），7.1 仅登记 | family-clustering §三.5#17 |

> P1 转 deferred = **0 行**（B1-11 六张 P1 回升券不在 179 行台账内，以激活条款形态登记；179 行中 178 行入批 + 1 行豁免，无 deferred 行）。历史「已知未修复」与跨 roadmap 重叠项已由 family-clustering §四/§五 消解并入对应族行/工单（零悬空），不产生额外豁免行。

---

## §七 自检与门禁

- 覆盖性核对五条全部出数（§三）；机械校验脚本 `_tmp/check-batch-coverage.mjs`（P1 台账 ↔ 批次清单双向比对 + 批内无重复）输出 PASS。
- 本提案未修改 inventory-p1 / family-clustering / p2-adjudication / inventory-ledgers 四份输入。
- `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（收口时随 Phase 6 复跑确认）。

---

*产出（plan 2493 Phase 5 / 批次划分提案子代理）。判级与成员归属权威=各单元 merge-adjudication.md 与 cra-index 台账；跨模块终裁权威=family-clustering.md §三；P2 裁决权威=p2-adjudication.md + cra-index「P2 裁决台账」。本文件为 7.2 R1.0 展开器向 roadmap 追加 Phase 8 工作项行的直接输入。*
