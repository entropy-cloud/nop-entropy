# 并发·异步·资源专项审计与修复 Roadmap（全模块滚动）

> Last updated: 2026-10-24
> Sources: `ai-dev/skills/concurrency-resource-audit-prompt.md`（审计方法论：C1–C10 十维度、F1–F11 缺陷族、归档与发现格式）,
> `ai-dev/skills/deep-audit-prompts.md`（「调度架构」「发现条目完整性守则」两节，审计调度沿用）,
> `ai-dev/skills/audit-remediation-roadmap-authoring-prompt.md`（P0 即时通道、R\*.0 展开器、closure audit 立项机制）,
> `ai-dev/audits/README.md`（审计归档规范、与 plans 的关系）,
> `docs-for-ai/01-repo-map/module-groups.md`（模块分组与依赖）

## 目的

对 nop-entropy **全部 Maven 模块组**执行 `ai-dev/skills/concurrency-resource-audit-prompt.md` 定义的「并发·异步·资源·内存·长时运行」专项深度审计，产出逐模块审计报告；随后基于全部报告做跨模块合并裁定，**分批拟制修复 plan 并执行修复**。终端目标：

1. 每个审计单元产出一份完整的 concurrency 审计目录（C1–C10 维度报告 + `summary.md`），全部 P0/P1 可追溯。
2. 跨模块同族缺陷（F1–F11）合并聚类，消除重复计数，形成全局发现台账。
3. 所有 P0 经即时通道修复；所有 P1 分批修复并通过独立 closure audit；P2 逐项裁决（修 / deferred + successor）。
4. 全量构建与测试保持绿色，owner docs 同步更新。

**与现有 roadmap 的关系**：`security-audit-roadmap`（安全域）、`full-code-review-roadmap`（质量域）、`nop-stream-independent-audit-roadmap`（流引擎专项）覆盖其他维度；本 roadmap 只覆盖并发·异步·资源域。发现重叠时在 7.1 合并裁定阶段去重并 cross-reference，不重复修复。

**当前状态：仅立项，暂不执行。** 全部工作项初始 `todo`。

## 审计方法论（工作内容）

每个审计工作项 = 按 `concurrency-resource-audit-prompt.md` 附录 B 执行一次完整滚动审计：

1. **准备**：读 AGENTS.md、`docs-for-ai/INDEX.md`、该单元 owner doc 与 `ai-dev/design/` 相关设计文档；执行附录 C grep 基线 + 行数统计，圈定重点文件。
2. **维度裁剪**：按「适用判定」表确定维度集合（默认 C1–C10 全选；纯模型/配置/文档类裁剪为 C1+C6；C10 恒放该单元最后批次，因其依赖前序维度产出的缺陷清单）。
3. **派发初审**：各维度并行派发子代理（共享提示词前缀 + 维度正文，`{MODULE}` 已替换）。**盲审纪律**：初审子代理禁止阅读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/` 历史记录。
4. **深挖迭代**：对初审发现按需追加串行深挖轮次（调度流程沿 `deep-audit-prompts.md`「调度架构」）。
5. **复核与现状标注**：独立子代理复核每条发现（保留/降级/驳回），此阶段才允许对照历史审计标注「新发现 / 已知未修复 / 已修复」。
6. **合并裁定**：单单元内同族相邻发现按「根因 + 失效层」合并，防止同一缺陷被多维度重复计数。
7. **归档**：`ai-dev/audits/{YYYY}-{MM}/{YYYY-MM-DD}-{HHMM}-deep-audit-{单元标识}-concurrency/`，每维度一个文件（`{C编号}-{英文短名}.md`），汇总写 `summary.md`。归档即更新 campaign 索引（`ai-dev/audits/` 下的 cra-index.md，由工作项 0.2 创建）。
8. **closure audit**：独立子代理验证审计目录完整性（维度覆盖、发现格式 ≥10 行字段、F3 失效面枚举非空），通过后工作项方可 `done`。

**发现质量硬约束**（来自审计 prompt，验证 closure audit 时逐条核对）：

- 并发类发现必须写出具体触发时序（T1/T2 交错序列）；写不出的一律降 P2 并标注「理论风险，未具体化」。
- 每条发现必须带 F3 失效面枚举（grep 全部同族兄弟方法/入口并逐一登记），禁止留空。
- 规模判断对照 owner doc 声明的规模包络；超出包络的 OOM 风险单独标注，不与包络内缺陷混排。
- owner doc 明文声明且带 fail-fast 守卫的边界不是缺陷；守卫缺失、文档未声明、守卫可绕过才是。
- 发现条目使用审计 prompt 附录 A 统一格式；全局唯一 ID = `{单元标识}-C{N}-{序号}`。

**修复立项方法论**（沿 `audit-remediation-roadmap-authoring-prompt.md`）：

- **P0 即时通道**：任何审计工作项执行中发现 P0，当即就地修复（plan 内加修复 Phase）或异步注入独立修复 plan，**不得留到批量修复阶段**；在审计报告与 cra-index 中标注修复路径与状态。
- **P1 批量通道**：Phase 7 合并裁定后按批次展开（见 7.2 / Phase 8）。
- **P2 裁决通道**：P2 默认不在修复范围；7.1 逐项裁决为「修（in-scope）/ deferred（必须带 Why Not Blocking + successor 指针）」，结果写回 cra-index 台账。已确认 live 的 must-fix P2 不得降级 deferred。
- **P3**：记录在审计报告，不立项。

## Work Item Status

> **这是唯一的动态状态块。仅在此处更新状态。**
> Phase 8 修复批次工作项已由 7.2 展开器追加（plan 2494，2026-10-24，74 行）。

### Phase 0 — 基线与编排

| # | Work Item | Status | Owner Doc | Deps | Skill |
|---|-----------|--------|-----------|------|-------|
| 0.1 | 全量绿色基线：`./mvnw clean install -T 1C` 通过，记录基线 commit | done | — | — | none（命令） |
| 0.2 | 初始化 campaign 索引（ai-dev/audits/ 下 cra-index.md：报告清单 + P0 追踪 + P1/P2 台账骨架） | done | `ai-dev/audits/README.md` | 0.1 | none |
| 0.3 | 生成 mission 配置（仓库根 missions/ 目录下 concurrency-audit.json：roadmapPath 指向本文件，路径必须落在 `ai-dev/` 下）并 dry-run 验证 | done | `ai-dev/skills/audit-remediation-roadmap-authoring-prompt.md` 步骤 7 | 0.2 | `audit-remediation-roadmap-authoring-prompt.md` |

### Phase 1 — 审计 Wave 1：长驻引擎与协调器（最高并发风险）

| # | Work Item | Status | Owner Doc | Deps | Skill |
|---|-----------|--------|-----------|------|-------|
| 1.1 | nop-stream 核心（`nop-stream-core` + `nop-stream-runtime`：checkpoint/storage/rpc/transport/health） | done | `docs-for-ai/03-modules/nop-stream.md` | 0.3 | `concurrency-resource-audit-prompt.md`（C1–C5 C7–C10） |
| 1.2 | nop-stream 状态与连接器（`nop-stream-cep` + `nop-stream-rocksdb` + connector/-batch/-jdbc/-debezium/-kafka + `format-avro`） | done | `docs-for-ai/03-modules/nop-stream.md`、`nop-stream-connectors.md` | 0.3 | 同上（C1–C8 + C10） |
| 1.3 | nop-stream 编排薄层（`nop-stream-flow` + `nop-stream-sql` + `fraud-example`，维度裁剪） | done | `docs-for-ai/03-modules/nop-stream-user-guide.md`、`nop-stream-sql.md` | 0.3 | 同上（C1 C3 C6 + 抽查） |
| 1.4 | nop-job（定时调度引擎） | done | `docs-for-ai/03-modules/nop-job.md` | 0.3 | 同上（C1 C2 C4 C5 C7 + C10） |
| 1.5 | nop-wf + nop-task（工作流 / 任务引擎） | done | `docs-for-ai/03-modules/nop-wf.md`、`nop-task.md` | 0.3 | 同上（C1–C5 C7 C8 + C10） |
| 1.6 | nop-retry + nop-tcc（分布式重试 / TCC 事务协调） | done | `docs-for-ai/03-modules/nop-retry.md`、`nop-tcc.md` | 0.3 | 同上（C1 C3 C6 C7 C8 + C10） |
| 1.7 | nop-batch（chunk 批处理、断点续传） | done | `docs-for-ai/03-modules/nop-batch.md` | 0.3 | 同上（C1 C5 C6 C7 C8 + C10） |

### Phase 2 — 审计 Wave 2：热路径基础设施

| # | Work Item | Status | Owner Doc | Deps | Skill |
|---|-----------|--------|-----------|------|-------|
| 2.1 | nop-kernel 并发内核本体（`nop-commons` 的 io.nop.commons.concurrent/cache/pool/lock/ratelimit/semaphore + `nop-core` + `nop-xlang` + `nop-xdefs` 全局模型缓存） | done | `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`、`ioc-and-config.md` | 0.3 | 同上（C1–C7 C9 C10；唯一审计平台并发设施**自身实现**的工作项） |
| 2.2 | nop-core-framework（IoC/Config/Boot/Plugin 三子模块/Security/Log）+ nop-utils | done | `docs-for-ai/03-modules/nop-plugin.md`、`ioc-and-config.md` | 0.3 | 同上（C1 C2 C4 C6 + C10） |
| 2.3 | 请求热路径：`nop-graphql` + `nop-biz` + `nop-gateway` + `nop-biz-auth-core`/`nop-biz-file-core` | done | `docs-for-ai/02-core-guides/api-and-graphql.md`、`service-layer.md` | 0.3 | 同上（C1–C4 C8 C9 + C10） |
| 2.4 | nop-persistence 读写热路径（`nop-orm` + `nop-orm-eql` + `nop-orm-model` + `nop-dao` + `nop-orm-data`） | done | `docs-for-ai/02-core-guides/model-first-development.md` | 2.1 | 同上（C1–C6 C9 + C10；S 级拆分一） |
| 2.5 | nop-persistence 外围（`nop-dbtool` + `nop-db-migration` + `nop-orm-migration` 五状态机 + `nop-cdc` + `nop-nosql` + orm-drivers/geo/pdm/rpc） | done（closure audit PASS，plan 2472） | `docs-for-ai/02-core-guides/data-migration.md` | 0.3 | 同上（C1 C3 C6 C7 C8 + C10；S 级拆分二） |

### Phase 3 — 审计 Wave 3：AI 子系统

| # | Work Item | Status | Owner Doc | Deps | Skill |
|---|-----------|--------|-----------|------|-------|
| 3.1 | nop-ai-core + nop-ai-agent（LLM 异步调用链、Provider failover、agent 执行引擎） | done（closure audit PASS，plan 2473） | `docs-for-ai/03-modules/nop-ai.md` | 0.3 | 同上（C1 C3 C4 C5 C7 + C10） |
| 3.2 | nop-ai-toolkit + nop-ai-tools + nop-ai-shell（工具执行、进程/沙箱生命周期、mavenvfs） | done（closure audit PASS，plan 2474） | `docs-for-ai/03-modules/nop-ai.md` | 0.3 | 同上（C1 C4 C6 C8 + C10） |
| 3.3 | nop-ai-gateway（channel 长连接、会话存储、failover 路由、扫码登录） | done（closure audit PASS，plan 2475） | `docs-for-ai/03-modules/nop-ai-gateway.md` | 0.3 | 同上（C1–C4 C6 C7 C8 + C10） |
| 3.4 | nop-ai 其余（api/coder/dsl-orm/rag/codegen/dao/meta/service/web/app，裁剪 C1+C6；rag 补 C3 异步检索链） | done（closure audit PASS，plan 2476；Wave 3 收官） | `docs-for-ai/03-modules/nop-ai.md` | 0.3 | 同上（裁剪） |

### Phase 4 — 审计 Wave 4：消息、集群与宿主集成

| # | Work Item | Status | Owner Doc | Deps | Skill |
|---|-----------|--------|-----------|------|-------|
| 4.1 | nop-message 全族（codec/core/kafka/pulsar/debezium：消费 prefetch、ACK 语义、背压） | done（closure audit PASS，plan 2477） | `docs-for-ai/03-modules/nop-message.md` | 0.3 | 同上（C1 C3 C5 C6 C8 + C10） |
| 4.2 | nop-cluster 全族（admin/core/nacos/sentinel/hazelcast/rpc-cluster：RPC 异步、注册表、限流熔断） | done（closure audit PASS，plan 2478） | `docs-for-ai/01-repo-map/module-groups.md` | 0.3 | 同上（C1–C4 C8 + C10） |
| 4.3 | nop-network + nop-integration（HTTP 客户端连接池、MQTT、feishu 连接器） | done（closure audit PASS，plan 2479） | `docs-for-ai/03-modules/nop-network.md` | 0.3 | 同上（C1 C3 C6 + C10） |
| 4.4 | nop-spring（含 spring-mcp-server）+ nop-quarkus（宿主生命周期、类加载、bean 装配并发） | done（closure audit PASS，plan 2480） | `docs-for-ai/01-repo-map/module-groups.md` | 0.3 | 同上（C1 C4 C6 + C10） |
| 4.5 | nop-search（api/core/lucene）+ nop-runner + nop-autotest（测试基建并行执行与资源隔离） | done（closure audit PASS，plan 2481；Wave 4 收官） | `docs-for-ai/01-repo-map/module-groups.md` | 0.3 | 同上（C1 C4 C6 + C10，裁剪） |

### Phase 5 — 审计 Wave 5：业务与可复用模块

| # | Work Item | Status | Owner Doc | Deps | Skill |
|---|-----------|--------|-----------|------|-------|
| 5.1 | nop-auth + nop-credential（会话/token 并发、凭证缓存与加密） | done（closure audit PASS，plan 2482） | `docs-for-ai/03-modules/nop-auth.md`、`nop-credential.md` | 0.3 | 同上（C1 C2 C6 + C10） |
| 5.2 | nop-sys + nop-dyn（序列号、分布式锁、事件队列；动态模型运行时缓存） | done（closure audit PASS，plan 2483） | `docs-for-ai/03-modules/nop-sys.md`、`nop-dyn.md` | 0.3 | 同上（C1 C2 C6 C8 + C10） |
| 5.3 | nop-file + nop-datav（上传下载去重、数据视图） | done（closure audit PASS，plan 2484） | `docs-for-ai/03-modules/nop-file.md` | 0.3 | 同上（C1 C5 C6 + C10） |
| 5.4 | nop-report + nop-format（Excel/OOXML/PDF/office-model 大文件流式 vs 物化；chart/converter/mermaid/svg/tablesaw/record 抽查） | done（closure audit PASS with corrections，plan 2485；P0×0/P1×1/P2×9/P3×15，配额中断恢复执行） | `docs-for-ai/03-modules/nop-report.md` | 0.3 | 同上（C1 C5 C6 C9 + C10；S 级按功能裁剪） |
| 5.5 | nop-rule + nop-metadata（规则引擎决策；元数据 cron 调度、质量检查点、webhook 分发、对账） | done（closure audit PASS with corrections，plan 2486；P0×0/P1×3/P2×9/P3×17 工单级） | `docs-for-ai/03-modules/nop-rule.md`、`nop-metadata.md` | 0.3 | 同上（C1 C4 C5 C7 + C10） |
| 5.6 | nop-graph + nop-duckdb（图算法库；DuckDB 连接单写者约束、CSV/Parquet 读写面） | done（closure audit PASS，plan 2487；P0×0/P1×4/P2×4/P3×4；**Wave 5 收官**，含 WI 2.1 C6-CL-031 移交闭环） | `docs-for-ai/03-modules/nop-duckdb.md` | 0.3 | 同上（C1 C6 + 抽查） |

### Phase 6 — 审计 Wave 6：工具链与外围支撑

| # | Work Item | Status | Owner Doc | Deps | Skill |
|---|-----------|--------|-----------|------|-------|
| 6.1 | nop-tools 并行面：nop-rg + nop-find（FFM/Arena 并行遍历、SIMD）+ nop-bytecode | done（closure audit PASS with corrections，plan 2488；P0×0/**P1×0**[campaign 首例]/P2×4/P3×13） | `docs-for-ai/03-modules/nop-bytecode.md` | 0.3 | 同上（C1 C4 C6 C9 + C10） |
| 6.2 | nop-tools 解析面：nop-treesitter + nop-lint + nop-refactor + nop-jq | done（closure audit PASS with corrections，plan 2489；P0×0/P1×2/P2×7/P3×10；2026-09 G10 承接闭环） | `docs-for-ai/03-modules/nop-treesitter.md`、`nop-refactor.md` | 0.3 | 同上（C1 C6 + 抽查） |
| 6.3 | nop-code 索引面（`nop-code-core` + `nop-code-flow` + `nop-code-service`：长驻索引构建、图分析） | done（closure audit PASS with corrections，plan 2490；P0×0/P1×3/P2×16/P3×21；G11-15-02 承接闭环） | `docs-for-ai/03-modules/nop-code.md` | 0.3 | 同上（C1 C5 C6 C7 + C10；S 级拆分一） |
| 6.4 | nop-code 其余（lang-java/python/typescript/go/rust/csharp + codegen + api/dao/meta/web/app，裁剪） | done（closure audit PASS，plan 2491；**零立案 campaign 首例**；G11-03-01 闭环） | `docs-for-ai/03-modules/nop-code.md` | 0.3 | 同上（C1 C6 抽查；S 级拆分二） |
| 6.5 | 外围支撑抽查：nop-demo + demo + nop-frontend-support + nop-dev-tools + nop-entropy-e2e（C1+C6 抽查；nop-benchmark 不审——基准代码按审计 prompt 误报校准不按生产标准，仅当其暴露生产代码缺陷时转记台账） | done（closure audit PASS with corrections，plan 2492；P0×0/P1×2/P2×4/P3×5 工单级+C10 缺保护 6；**初审审计阶段收官 WI 1.1–6.5 全部完成**） | — | 0.3 | 同上（C1+C6 抽查） |

### Phase 7 — 合并裁定与修复立项

| # | Work Item | Status | Owner Doc | Deps | Skill |
|---|-----------|--------|-----------|------|-------|
| 7.1 | 跨模块合并裁定：同族缺陷按 F1–F11 × 模块聚类；跨报告重复发现消解；历史「已知未修复」并入台账；P2 逐项裁决（修 / deferred + successor）；P3 归档；产出**修复批次划分提案**（批次边界、依赖顺序、每批 exit criteria） | done（closure audit PASS with corrections，plan 2493；P1×179 入批/P2 修 483+deferred 77/74 批次记录+R-G 治理批，B1-04 计数联动 P1 180/P2 559） | ai-dev/audits/ 下 cra-index.md（campaign 台账） | 1.1–6.5 全部 done | `audit-remediation-roadmap-authoring-prompt.md` 步骤 2/5 |
| 7.2 | R1.0 展开器：依据 7.1 提案在本 roadmap 追加 Phase 8 修复批次工作项行（每行引用 finding ID 清单作为 Deps） | done（closure audit PASS with corrections，plan 2494；74 行[8.1–8.74]已追加，8.74=R-G 治理批） | 本文件 | 7.1 | `audit-remediation-roadmap-authoring-prompt.md` R\*.0 机制 |

### Phase 8 — 分批修复（由 7.2 展开器生成，plan 2494）

> 批次划分由 7.1 提案确定，建议排序原则：
> R1 = P0 收尾确认 + 跨模块同族 P1（先修基础设施层，下游模块一次受益）→
> R2 = 基础设施/平台服务剩余 P1 → R3 = 业务模块 P1 →
> R4+ = P2 裁决为修的项 + C10 登记的测试保护力缺口。
> 每批 = 一份 plan（修复 + 回归测试 + owner doc 更新），独立 closure audit 后整批 done。

| # | Work Item | Status | Owner Doc | Deps | Skill |
|---|-----------|--------|-----------|------|-------|
| 8.1 | R1-00 P0 收尾确认批（3 项已修 P0 回归确认） | done（closure audit PASS，plan 2495；三 focused 测试全绿+scan-hollow 0+impl 叶冻结维持） | `docs-for-ai/03-modules/nop-stream-connectors.md`；`docs-for-ai/03-modules/nop-network.md`；nop-persistence 无 owner doc→缺口声明 | batch-proposal.md §二 R1-00（3 项） | none（修复 plan） |
| 8.2 | R1-01 周期任务异常绝缘层批（R04 平台批） | done（closure audit PASS with corrections，plan 2496；14 项全修+nop-kernel 绝缘层本体+30 点表 12/16/2 处置，R1 旗舰批落地） | `docs-for-ai/03-modules/nop-job.md`；`docs-for-ai/03-modules/nop-batch.md`；`docs-for-ai/04-reference/source-anchors.md`（2.1-C3-001 30 点绝缘状态表锚点） | batch-proposal.md §二 R1-01（14 项） | none（修复 plan） |
| 8.3 | R1-02 平台池设施面批：globalTimer 死配置 + 池工厂（R13 设施） | done（closure audit PASS，plan 2497；5 项全修+池工厂 fail-fast+cached 上界熔断+收缩重排+nop-config 卸载，R1-03 硬依赖解除前置就绪） | `docs-for-ai/04-reference/source-anchors.md`（GlobalExecutors/globalTimer 锚点）；`docs-for-ai/03-modules/nop-job.md`（调度契约） | batch-proposal.md §二 R1-02（5 项） | none（修复 plan） |
| 8.4 | R1-03 平台池消费方批：隔离/卸载落地（R13 使用方） | done（closure audit PASS，plan 2498；9 项全修+A4/A5/D3/D4/SocketServer 承接面落账+MA-16 约束解除，六分片 24+ 新回归测试） | `docs-for-ai/03-modules/nop-stream-connectors.md`；`docs-for-ai/03-modules/nop-task.md`；`docs-for-ai/02-core-guides/service-layer.md`；`docs-for-ai/03-modules/nop-ai.md` | batch-proposal.md §二 R1-03（9 项）；硬依赖 8.3（R1-02） | none（修复 plan） |
| 8.5 | R1-04 api-core 协同批：bindCancel 单点改造 + 上下文传播（R14+R15） | done（closure audit PASS with corrections，plan 2499；14 项全修+bindCancel 双层同提交+C3-004 后置接入[git 序列可证]+上下文统一五入口零改码，SPI ×11 回归面留证） | `docs-for-ai/04-reference/source-anchors.md`（api-core 锚点）；`docs-for-ai/02-core-guides/service-layer.md` | batch-proposal.md §二 R1-04（14 项） | none（修复 plan） |
| 8.6 | R1-05 缓存失效传播链批（R24） | done（closure audit PASS，plan 2500；四成员单点收敛+宽限锚写入年龄修订[Phase 3 载体修订三态证据]+TestXplTask 恢复，G24 仅成员面收敛） | `docs-for-ai/04-reference/source-anchors.md`（模型缓存锚点） | batch-proposal.md §二 R1-05（4 项） | none（修复 plan） |
| 8.7 | R1-06 沙箱/shell 工具执行链批（R09 沙箱清理族 + B1-03/B1-04 回升行） | done（closure audit PASS，plan 2501；7 项全修+B1-04 落账确认+B1-03 MA-06 维持 P1 条款锚点裁定，反向钉 R4 全组正面改写） | `docs-for-ai/03-modules/nop-ai.md` | batch-proposal.md §二 R1-06（7 项） | none（修复 plan） |
| 8.8 | R1-07 F4 账本保留统一批（R01 统一批次面，ledger §二#3） | done（closure audit PASS，plan 2502；六成员全修+族级 SSOT 一处定稿[统一参数集七项]零分叉+ask-first 三处合规+trusted-device 第七面保守摘除归 R3-01） | `docs-for-ai/03-modules/nop-retry.md`；`docs-for-ai/03-modules/nop-batch.md`；`docs-for-ai/03-modules/nop-credential.md`；`docs-for-ai/03-modules/nop-rule.md`；`docs-for-ai/03-modules/nop-duckdb.md`；`docs-for-ai/03-modules/nop-sys.md` | batch-proposal.md §二 R1-07（6 项） | none（修复 plan） |
| 8.9 | R1-08 Debezium 双轨与消息交付语义批（R16/R17/R18 面，B1-05 三案） | done（closure audit PASS，plan 2503；六成员全修+B1-05 三案 P1 落账+R4-24 REGISTRY 契约前置就绪+对齐注记组五处） | `docs-for-ai/03-modules/nop-stream-connectors.md`；`docs-for-ai/03-modules/nop-message.md`；`docs-for-ai/03-modules/nop-stream-cdc-cookbook.md` | batch-proposal.md §二 R1-08（6 项） | none（修复 plan） |
| 8.10 | R2-01 2.1 kernel 剩余批：退役代际族 + RCE 死锁族 | done（closure audit PASS，plan 2504；7 项全修+退役代际平台机制 ResourceRetirementQueue+LoadWaitGraph 环检测+G24 缺陷族整组 fixed 解除+2.5-C6-005 双轨联动） | `docs-for-ai/04-reference/source-anchors.md`；`docs-for-ai/02-core-guides/ioc-and-config.md` | batch-proposal.md §二 R2-01（7 项） | none（修复 plan） |
| 8.11 | R2-02 流式取消/期限两侧同批（R14 消费面 + R05/R06/R11/R21 相邻） | done（closure audit PASS with corrections，plan 2505；5 项全修+两侧同批端到端四场景真实全链绿+PA 面①批内闭环 nop-http-api） | `docs-for-ai/03-modules/nop-ai.md`；`docs-for-ai/03-modules/nop-network.md` | batch-proposal.md §二 R2-02（5 项）；软依赖 8.5（R1-04） | none（修复 plan） |
| 8.12 | R2-03 2.2 插件生命周期/配置源/Shell 批 | done（closure audit PASS，plan 2506；6 项全修+双锁单向序/回调延迟契约/forceUnload 恢复 API/PluginClassLoader 构造期自清理+批前既有 HMR 伪 reload 承接[plan 2500 宽限期消费边界]） | `docs-for-ai/02-core-guides/ioc-and-config.md`；`docs-for-ai/04-reference/source-anchors.md` | batch-proposal.md §二 R2-03（6 项） | none（修复 plan） |
| 8.13 | R2-04 2.2 IoC 启动图/死锁 + 配置发布协议批（R22 刷新链） | done（closure audit PASS，plan 2507；7 项全修[2.2×4+2.5×3]+join 失败传导/getBean0 等待图环检出+有界获取/配置发布 holder 单发布/写门租约 fail-open 观察即起租+实施期 setClosed(false) 清零缺口修复；api-core 零签名 SPI 面全绿） | `docs-for-ai/02-core-guides/ioc-and-config.md`；`docs-for-ai/04-reference/source-anchors.md` | batch-proposal.md §二 R2-04（7 项） | none（修复 plan） |
| 8.14 | R2-05 2.3 服务热路径剩余批（R05 SSO + R25 重试放大） | done（closure audit PASS，plan 2508；2 项全修——SSO 刷新异步化[default 方法 + beans 接线 + runOnContext 再入链序] + Gateway 准入上界[共享许可池 + 拒绝按落点钉死 + equal jitter]；nop-auth-sso 触碰 PA 裁定执行形态改造） | `docs-for-ai/02-core-guides/service-layer.md` | batch-proposal.md §二 R2-05（2 项） | none（修复 plan） |
| 8.15 | R2-06 流式缓冲批 A（chain25/R02 双实现分叉，3.3 同文件批 A） | done（closure audit PASS，plan 2509；11 项全修——共享泛型缓冲核 BoundedWindowBuffer 收敛双实现分叉 + 溢出不可重试终态 + attempt 漏斗配对 + pendingComplete 对齐 + drain-loop 出锁投递 + allowCall 双面收敛[cooldownElapsed 只读追加] + C3-007 宿主半边） | `docs-for-ai/02-core-guides/service-layer.md`；`docs-for-ai/03-modules/nop-ai-gateway.md` | batch-proposal.md §二 R2-06（11 项） | none（修复 plan） |
| 8.16 | R2-07 1.1 stream-core 批（并发正确性 + 资源包络） | done（plan 2510 completed @ 2026-10-29，closure audit APPROVED） | `docs-for-ai/03-modules/nop-stream.md`；`docs-for-ai/03-modules/nop-stream-user-guide.md` | batch-proposal.md §二 R2-07（10 项） | none（修复 plan） |
| 8.17 | R2-08 1.2 连接器批 | done（plan 2511 completed @ 2026-10-30，closure audit APPROVED） | `docs-for-ai/03-modules/nop-stream-connectors.md` | batch-proposal.md §二 R2-08（7 项） | none（修复 plan） |
| 8.18 | R2-09 1.3 编排薄层批 | done（plan 2512 completed @ 2026-10-30，closure audit APPROVED） | `docs-for-ai/03-modules/nop-stream.md`；`docs-for-ai/03-modules/nop-stream-sql.md` | batch-proposal.md §二 R2-09（4 项） | none（修复 plan） |
| 8.19 | R2-10 1.4 job 批 | done（plan 2513 completed @ 2026-10-31，closure audit APPROVED） | `docs-for-ai/03-modules/nop-job.md` | batch-proposal.md §二 R2-10（2 项） | none（修复 plan） |
| 8.20 | R2-11 1.5 task/wf 批 | done（plan 2514 completed @ 2026-10-31，closure audit APPROVED） | `docs-for-ai/03-modules/nop-task.md`；`docs-for-ai/03-modules/nop-wf.md` | batch-proposal.md §二 R2-11（6 项） | none（修复 plan） |
| 8.21 | R2-12 1.6 tcc 批（CAS/守卫 + 补偿/账本卫生） | done（plan 2515 completed @ 2026-10-31，closure audit APPROVED） | `docs-for-ai/03-modules/nop-retry.md`；`docs-for-ai/03-modules/nop-tcc.md` | batch-proposal.md §二 R2-12（13 项） | none（修复 plan） |
| 8.22 | R2-13 1.7 batch 批 | done（plan 2516 completed @ 2026-10-31，closure audit APPROVED） | `docs-for-ai/03-modules/nop-batch.md` | batch-proposal.md §二 R2-13（8 项）；硬依赖 8.2（R1-01）+8.20（R2-11，勘误申报 @ 2026-10-30：原 Deps 列缺；fencing 引用 plan 2514 设计文档不重建） | none（修复 plan） |
| 8.23 | R2-14 2.4 ORM 并发批（会话/事务互斥 + 模型发布/缓存/异步） | done（plan 2517 completed @ 2026-10-31，closure audit APPROVED） | `docs-for-ai/02-core-guides/model-first-development.md`；`docs-for-ai/04-reference/source-anchors.md` | batch-proposal.md §二 R2-14（13 项） | none（修复 plan） |
| 8.24 | R2-15 2.5 peri 批（PubSub 断链 + 链编排 fencing） | done（plan 2518 completed @ 2026-10-31，closure audit APPROVED；勘误 10：C7-014 F5 前提证伪收窄为可观测缺失，cra-index 行已改写） | `docs-for-ai/02-core-guides/model-first-development.md`（迁移 owner 面）；`docs-for-ai/04-reference/source-anchors.md` | batch-proposal.md §二 R2-15（11 项） | none（修复 plan） |
| 8.25 | R2-16 3.1 agent 编排/会话批 | done（plan 2519 completed @ 2026-10-31，closure audit APPROVED；勘误 12/13 事实口径修订随行） | `docs-for-ai/03-modules/nop-ai.md` | batch-proposal.md §二 R2-16（10 项）；硬依赖 8.5（R1-04）；软依赖 8.3（R1-02）；软依赖 8.4（R1-03）；软依赖 8.7（R1-06） | none（修复 plan） |
| 8.26 | R2-17 3.4 AI REST 批 | active（plan 2520，draft review R1 修订转 active） | `docs-for-ai/03-modules/nop-ai.md` | batch-proposal.md §二 R2-17（4 项） | none（修复 plan） |
| 8.27 | R2-18 4.2 cluster 批（选主/注册中心/重试链） | todo | nop-cluster 无 03-modules owner doc→缺口声明；`docs-for-ai/01-repo-map/module-groups.md` | batch-proposal.md §二 R2-18（4 项） | none（修复 plan） |
| 8.28 | R2-19 4.3 feishu/文件通道批（B1-07/B1-08 转正 + R05/R11/R08 面） | todo | `docs-for-ai/03-modules/nop-network.md` | batch-proposal.md §二 R2-19（7 项） | none（修复 plan） |
| 8.29 | R2-20 4.3 连接生命周期批（B1-06 + R26/R08 面） | todo | `docs-for-ai/03-modules/nop-network.md` | batch-proposal.md §二 R2-20（6 项） | none（修复 plan） |
| 8.30 | R2-21 4.4 host 批 | todo | nop-host 无 03-modules owner doc→缺口声明；`docs-for-ai/01-repo-map/module-groups.md` | batch-proposal.md §二 R2-21（4 项）；软依赖 8.5（R1-04）；软依赖 8.2（R1-01） | none（修复 plan） |
| 8.31 | R3-01 5.1 auth 凭据批 | todo | `docs-for-ai/03-modules/nop-credential.md`；`docs-for-ai/03-modules/nop-auth.md` | batch-proposal.md §二 R3-01（6 项） | none（修复 plan） |
| 8.32 | R3-02 5.2 sys-dyn 批 | todo | `docs-for-ai/03-modules/nop-sys.md`；`docs-for-ai/03-modules/nop-dyn.md` | batch-proposal.md §二 R3-02（10 项） | none（修复 plan） |
| 8.33 | R3-03 5.3 file-datav 批 | todo | nop-datav 无 owner doc→缺口声明（5.3 drift 登记）；`docs-for-ai/03-modules/nop-file.md` | batch-proposal.md §二 R3-03（14 项） | none（修复 plan） |
| 8.34 | R3-04 5.4 report-format 批 | todo | `docs-for-ai/03-modules/nop-report.md`（nop-format 无 owner doc→缺口声明随 R-G） | batch-proposal.md §二 R3-04（10 项） | none（修复 plan） |
| 8.35 | R3-05 5.5 rule-metadata 批 | todo | `docs-for-ai/03-modules/nop-rule.md`；`docs-for-ai/03-modules/nop-metadata.md` | batch-proposal.md §二 R3-05（9 项） | none（修复 plan） |
| 8.36 | R3-06 5.6 graph-duckdb 批 | todo | `docs-for-ai/03-modules/nop-duckdb.md`（nop-graph owner doc drift→缺口声明随 R-G） | batch-proposal.md §二 R3-06（6 项） | none（修复 plan） |
| 8.37 | R3-07 Leiden 交叉批（6.3↔5.6 两侧同批） | todo | `docs-for-ai/03-modules/nop-code.md`；`docs-for-ai/03-modules/nop-duckdb.md` | batch-proposal.md §二 R3-07（2 项） | none（修复 plan） |
| 8.38 | R3-08 6.2 tools-parse 批 | todo | `docs-for-ai/03-modules/nop-refactor.md`；`docs-for-ai/03-modules/nop-treesitter.md`（nop-lint/nop-jq owner doc drift→R-G） | batch-proposal.md §二 R3-08（8 项） | none（修复 plan） |
| 8.39 | R3-09 6.3 code-index 批 | todo | `docs-for-ai/03-modules/nop-code.md` | batch-proposal.md §二 R3-09（2 项）；软依赖 8.37（R3-07） | none（修复 plan） |
| 8.40 | R3-10 6.5 peripheral 批（CL-301 通道批） | todo | —（nop-dev-tools 无 owner doc→缺口声明（6.5 四模块无 owner doc 整体登记→R-G）） | batch-proposal.md §二 R3-10（6 项） | none（修复 plan） |
| 8.41 | R4-01 1.1 流核并发正确性束（G01） | todo | `docs-for-ai/03-modules/nop-stream.md` | batch-proposal.md §二 R4-01（15 项）；软依赖 8.16（R2-07）——**8.16 已 done（plan 2510），软依赖解除**；裁定①–⑪与三范式/溢出语义结论见 `ai-dev/design/concurrency/stream-core-concurrency-and-resource-envelope.md` 可引用为既有裁定 | none（修复 plan） |
| 8.42 | R4-02 1.1 资源包络 + FAM-2 束（G02+G03） | todo | `docs-for-ai/03-modules/nop-stream.md` | batch-proposal.md §二 R4-02（7 项）；软依赖 8.16（R2-07）——**8.16 已 done（plan 2510），软依赖解除**；裁定①–⑪与三范式/溢出语义结论见 `ai-dev/design/concurrency/stream-core-concurrency-and-resource-envelope.md` 可引用为既有裁定 | none（修复 plan） |
| 8.43 | R4-03 1.2 连接器 MA 束 + 包络声明（G05+G09） | todo | `docs-for-ai/03-modules/nop-stream-connectors.md` | batch-proposal.md §二 R4-03（12 项）；软依赖 8.17（R2-08） | none（修复 plan） |
| 8.44 | R4-04 1.2 并发正确性 + 失败路径泄漏束（G07+G08） | todo | `docs-for-ai/03-modules/nop-stream-connectors.md` | batch-proposal.md §二 R4-04（11 项）；软依赖 8.17（R2-08） | none（修复 plan） |
| 8.45 | R4-05 1.3 Replay/流语义束（G11） | todo | `docs-for-ai/03-modules/nop-stream.md` | batch-proposal.md §二 R4-05（10 项）；软依赖 8.18（R2-09） | none（修复 plan） |
| 8.46 | R4-06 1.4 调度账本束 a（G13 前半） | todo | `docs-for-ai/03-modules/nop-job.md` | batch-proposal.md §二 R4-06（10 项）；硬依赖 8.2（R1-01）；硬依赖 8.19（R2-10） | none（修复 plan） |
| 8.47 | R4-07 1.4 调度账本束 b（G13 后半） | todo | `docs-for-ai/03-modules/nop-job.md` | batch-proposal.md §二 R4-07（10 项）；软依赖 8.46（R4-06） | none（修复 plan） |
| 8.48 | R4-08 1.5 闸门/throttle 束（G14） | todo | `docs-for-ai/03-modules/nop-task.md` | batch-proposal.md §二 R4-08（12 项）；软依赖 8.20（R2-11） | none（修复 plan） |
| 8.49 | R4-09 1.5 图执行/扫描 infra 束（G15，C7-006 拆出至 R4-32） | todo | `docs-for-ai/03-modules/nop-task.md` | batch-proposal.md §二 R4-09（13 项）；软依赖 8.20（R2-11）；软依赖 8.48（R4-08） | none（修复 plan） |
| 8.50 | R4-10 1.6 补偿/护栏束（G17，C7-011 拆出至 R4-32） | todo | `docs-for-ai/03-modules/nop-retry.md`；`docs-for-ai/03-modules/nop-tcc.md` | batch-proposal.md §二 R4-10（14 项）；硬依赖 8.21——**8.21 已 done（plan 2515），硬依赖解除**，裁定①–⑩可引用为既有裁定（R2-12）；硬依赖 8.8（R1-07） | none（修复 plan） |
| 8.51 | R4-11 1.7 generator/生命周期束 a（G23 前半） | todo | `docs-for-ai/03-modules/nop-batch.md` | batch-proposal.md §二 R4-11（11 项）；硬依赖 8.22——**8.22 已 done（plan 2516），硬依赖解除**（C8-001 先于 C8-009 顺序约束已满足；裁定①–⑧可引用为既有裁定）（R2-13） | none（修复 plan） |
| 8.52 | R4-12 1.7 读侧/护栏束 b（G23 后半） | todo | `docs-for-ai/03-modules/nop-batch.md` | batch-proposal.md §二 R4-12（11 项）；硬依赖 8.22——**8.22 已 done（plan 2516），硬依赖解除**（C8-001 先于 C8-009 顺序约束已满足；裁定①–⑧可引用为既有裁定）（R2-13） | none（修复 plan） |
| 8.53 | R4-13 2.1 锁纪律/调度器/destroy 束 a（G27 前半，C7-004 拆出至 R4-32） | todo | `docs-for-ai/04-reference/source-anchors.md`；`docs-for-ai/02-core-guides/ioc-and-config.md` | batch-proposal.md §二 R4-13（18 项）；硬依赖 8.2（R1-01）；硬依赖 8.3（R1-02）；硬依赖 8.10（R2-01） | none（修复 plan） |
| 8.54 | R4-14 2.1 销毁链/BlockingSource 束 b（G27 后半） | todo | 同 R4-13 | batch-proposal.md §二 R4-14（17 项）；软依赖 8.53（R4-13） | none（修复 plan） |
| 8.55 | R4-15 2.2 配置刷新/订阅/插件/IoC 启动图/销毁链束（G31） | todo | `docs-for-ai/02-core-guides/ioc-and-config.md` | batch-proposal.md §二 R4-15（17 项） | none（修复 plan） |
| 8.56 | R4-16 2.3 WS/网关束 a（G36 前半） | todo | `docs-for-ai/02-core-guides/service-layer.md` | batch-proposal.md §二 R4-16（12 项）；软依赖 8.5（R1-04）；软依赖 8.14（R2-05）；软依赖 8.15（R2-06） | none（修复 plan） |
| 8.57 | R4-17 2.3 auth/流缓冲/dispatchAll 束 b（G36 后半） | todo | `docs-for-ai/02-core-guides/service-layer.md` | batch-proposal.md §二 R4-17（12 项）；软依赖 8.56（R4-16） | none（修复 plan） |
| 8.58 | R4-18 2.4 ORM 切片束 a（G45 前半） | todo（软依赖 8.23 已就绪 @ 2026-10-31：世代号语义以 plan 2517 设计文档裁定⑥为 SSOT 消费不重建；C1-016 钉 TestLazyLoadOrmModel 零弱化义务继承） | `docs-for-ai/02-core-guides/model-first-development.md` | batch-proposal.md §二 R4-18（15 项）；软依赖 8.23（R2-14） | none（修复 plan） |
| 8.59 | R4-19 2.4 ORM 切片束 b（G45 后半） | todo（软依赖链就绪 @ 2026-10-31：C2-016 钉 testMultipleCommit 现状容忍边界经 plan 2517 零 diff 核对确认） | `docs-for-ai/02-core-guides/model-first-development.md` | batch-proposal.md §二 R4-19（15 项）；软依赖 8.58（R4-18） | none（修复 plan） |
| 8.60 | R4-20 2.5 链复制/PubSub 关联束（G47，C7-013 拆出至 R4-32） | todo（软依赖 8.24 已就绪 @ 2026-10-31：qs 身份键 SSOT=plan 2518 裁定⑨消费不重建；C1-008 与 MA-05 同文件同 diff 接缝在案；C7-008 健康子断言被 C3-008 修复推翻须按新形态复核） | `docs-for-ai/02-core-guides/model-first-development.md` | batch-proposal.md §二 R4-20（13 项）；软依赖 8.24（R2-15） | none（修复 plan） |
| 8.61 | R4-21 3.1 会话/配额/取消束 a（G52 前半，C7-004 拆出至 R4-32） | todo（硬依赖 8.25 已 done[plan 2519 @ 2026-10-31]，依赖解除；MA-11↔R4-24 consumeAwaitTimeout 对齐已由 plan 2519 双侧闭合） | `docs-for-ai/03-modules/nop-ai.md` | batch-proposal.md §二 R4-21（10 项）；硬依赖 8.25（R2-16） | none（修复 plan） |
| 8.62 | R4-22 3.1 资源卫生束 b（G52 后半） | todo | `docs-for-ai/03-modules/nop-ai.md` | batch-proposal.md §二 R4-22（10 项）；软依赖 8.61（R4-21） | none（修复 plan） |
| 8.63 | R4-23 3.2 工具输出/路径束（G54） | todo | `docs-for-ai/03-modules/nop-ai.md` | batch-proposal.md §二 R4-23（10 项）；软依赖 8.7（R1-06） | none（修复 plan） |
| 8.64 | R4-24 4.1 REGISTRY 生命周期/交付语义束（G62） | todo | `docs-for-ai/03-modules/nop-message.md` | batch-proposal.md §二 R4-24（13 项）；硬依赖 8.9（R1-08） | none（修复 plan） |
| 8.65 | R4-25 4.2 选主外生命周期/路由/Sentinel 束（G65） | todo | —（nop-cluster 无 owner doc→缺口声明） | batch-proposal.md §二 R4-25（9 项）；软依赖 8.27（R2-18） | none（修复 plan） |
| 8.66 | R4-26 4.3 交付与生命周期束 a（G67 前半） | todo | `docs-for-ai/03-modules/nop-network.md` | batch-proposal.md §二 R4-26（14 项）；软依赖 8.28（R2-19）；软依赖 8.29（R2-20） | none（修复 plan） |
| 8.67 | R4-27 4.3 交付与生命周期束 b（G67 后半） | todo | `docs-for-ai/03-modules/nop-network.md` | batch-proposal.md §二 R4-27（14 项）；软依赖 8.66（R4-26） | none（修复 plan） |
| 8.68 | R4-28 4.4 双宿主/反向钉束（G70） | todo | —（nop-host 无 owner doc→缺口声明） | batch-proposal.md §二 R4-28（10 项）；软依赖 8.30（R2-21） | none（修复 plan） |
| 8.69 | R4-29 4.5 Lucene/watch/测试基建束（G71） | todo | —（nop-search 无 03-modules owner doc→缺口声明） | batch-proposal.md §二 R4-29（5 项）；软依赖 8.2（R1-01） | none（修复 plan） |
| 8.70 | R4-30 6.1 搜索/FFM/聚合束（G85） | todo | —（nop-rg/nop-find 无 owner doc→缺口声明（6.1 drift 登记→R-G）） | batch-proposal.md §二 R4-30（4 项） | none（修复 plan） |
| 8.71 | R4-31 6.3 读路径/C5 族束（G88） | todo | `docs-for-ai/03-modules/nop-code.md` | batch-proposal.md §二 R4-31（14 项）；软依赖 8.39（R3-09）；软依赖 8.37（R3-07） | none（修复 plan） |
| 8.72 | R4-32 R28 墙钟敏感跨单元批（单调时钟注入） | todo | `docs-for-ai/04-reference/source-anchors.md`（IClock/ClockProvider 锚点）；各单元 owner doc | batch-proposal.md §二 R4-32（7 项）；软依赖 8.3（R1-02） | none（修复 plan） |
| 8.73 | R4-33 移交接收轨批（2.3-C1-010 → nop-auth-service） | todo | `docs-for-ai/03-modules/nop-auth.md` | batch-proposal.md §二 R4-33（1 项） | none（修复 plan） |
| 8.74 | R-G 治理批（产出物：触发条款登记册/接线评审 checklist/可观测性补齐/死代码清理/owner doc 补齐/Gradle 通道决定） | todo | 见批 exit criteria（E5 缺口声明） | batch-proposal.md §五（14 项） | none（治理轨，产出物见 Work Item） |

### Phase 9 — 终局验证与沉淀

| # | Work Item | Status | Owner Doc | Deps | Skill |
|---|-----------|--------|-----------|------|-------|
| 9.1 | 全量验证：`./mvnw clean install -T 1C` + 受影响模块 `./mvnw test -pl <模块> -am`；独立子代理对全部 P0 修复与关键 P1 修复做 closure audit；cra-index 台账对账——每条 P0/P1 均为「已修 / deferred+successor」，无悬空 | todo | ai-dev/audits/ 下 cra-index.md（campaign 台账） | Phase 8 全部 done | `plan-closure-audit-prompt.md` |
| 9.2 | 沉淀：新缺陷族补充进 `concurrency-resource-audit-prompt.md` F 族或 `ai-dev/lessons/`；高频修复模式沉淀 owner doc；更新 `docs-for-ai/INDEX.md` 与 `04-reference/source-anchors.md`（如有路由变化） | todo | `ai-dev/lessons/README.md` | 9.1 | — |

## Status values

| Status | Meaning |
|--------|---------|
| `todo` | 未开始，无 plan |
| `planned` | 有执行 plan 且草案已过评审 |
| `done` | 完成，已通过独立 closure audit |

> 里程碑（Phase）无状态字段；wave 全部 `done` 即视为该 Phase 完成。审计工作项的 `done` 门控 = 审计目录完整 + summary.md 归档 + cra-index 已更新 + closure audit PASS。

## 当前基线

- **本域专项审计从未执行过**：`ai-dev/audits/` 下无任何 `*-concurrency*` 目录，本 roadmap 是 C1–C10 口径的首次全模块滚动。
- **相关历史审计（供复核阶段对照，初审盲审不得阅读）**：nop-stream 多轮 adversarial-review/deep-audit/invariant-loop（r6 缺陷族已沉淀为 F1/F2/F4/F5/F6/F10/F11）、nop-code OOM 族（F7）、nop-job 多轮审计、nop-ai-gateway channel 首订阅 check-then-act 已修复（commit 3db616f46d，F10 族先例）。
- **平台并发设施清单**（修复方向优先复用，见 `io.nop.commons.concurrent` / `cache` / `pool`）：IThreadPoolExecutor、IBlockingQueue + QueueOverflowPolicy、IResourceLockManager、IRateLimiter、ISemaphore/HighWatermarkSemaphore、ICache/IAsyncCache + CacheConfig、IPool + PoolConfig、NamedThreadLocal。修复不得手写重复造轮子。
- **验证命令**：`./mvnw clean install -T 1C`（全量）、`./mvnw test -pl <模块> -am`（单模块）。

## 依赖图

```mermaid
graph LR
    P0["Phase 0 基线与编排"] --> W1["Wave 1 长驻引擎"]
    P0 --> W2["Wave 2 热路径基础设施"]
    P0 --> W3["Wave 3 AI 子系统"]
    P0 --> W4["Wave 4 消息与集成"]
    P0 --> W5["Wave 5 业务模块"]
    P0 --> W6["Wave 6 工具链与外围"]
    W1 --> C["7.1 跨模块合并裁定"]
    W2 --> C
    W3 --> C
    W4 --> C
    W5 --> C
    W6 --> C
    C --> E["7.2 R1.0 展开器"]
    E --> R["Phase 8 修复批次 R1..Rn（动态）"]
    R --> V["9.1 全量验证与对账"]
    P0CH["P0 即时通道"] -.审计期间即时修复.-> V
    V --> G["9.2 沉淀"]
```

> 实际执行为串行（按文档顺序取第一个 `todo`）；wave 内各单元在无 mission-driver 时可并行派发，互不依赖（2.4 依赖 2.1：ORM 层审计需以 nop-commons 并发设施口径为背景，仅校准误报，不构成阻塞审计）。

## 横切关注点

| Concern | Notes |
|---------|-------|
| 盲审纪律 | 初审子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`；现状标注只在复核阶段做 |
| P0 即时通道 | P0 当即就地修复或异步注入修复 plan；永不进入 Phase 8 批量修复 |
| 证据标准 | 并发发现必须有触发时序；无时序降 P2；禁止见共享字段即报「线程不安全」 |
| F3 失效面 | 每条发现必须枚举同族兄弟并逐一登记；修复 plan 必须覆盖全部失效面，禁止修实例不修类别 |
| Protected Areas | 审计阶段全程只读；修复阶段触碰 `nop-core`/`nop-xlang`/`nop-xdef` 内部、`model/*.orm.xml`、`nop-*-api`、`nop-api-core`、`nop-auth` 须按 AGENTS.md 走 plan-first/ask-first，并在对应修复 plan 的 Dependencies 声明授权来源 |
| 生成文件 | `_` 前缀文件与 `_gen/` 禁改；并发缺陷追溯到生成模板层时修模板再重新生成 |
| 规模包络 | 所有 OOM/内存结论对照 owner doc 声明包络；超包络风险单独标注 |
| 绿色基线 | 每个修复批次结束全量 build 必须通过；审计工作项不改代码（P0 即时修复除外），其 BUILD_VERIFY 为只读确认 |
| 归档纪律 | 报告产出即更新 campaign 索引（ai-dev/audits/ 下 cra-index.md）；索引是 9.1 对账的输入 |
| 文档义务 | 修复完成同步更新对应 owner doc；修改 `docs-for-ai/` 或 `ai-dev/` 任何文件后跑 `node ai-dev/tools/check-doc-links.mjs --strict` 必须 0 错误 |
| C10 位置 | 测试保护力维度恒为该审计单元最后批次；「缺保护」按 P2 立项进入裁决通道 |

## Rules

- 本文件是状态索引与粗粒度分解，不是执行计划；每个工作项由其执行 plan 拥有实现细节。
- 状态变更仅发生在顶部 Work Item Status 块；初始全 `todo`，不得预填。
- 审计发现存审计报告，本文件只引用 finding ID（`{单元标识}-C{N}-{序号}`），不复制发现内容。
- 归档目录名的 `{单元标识}` 取工作项中的单元名（如 `nop-stream-core-runtime`、`nop-ai-gateway`），同单元多次深挖用 HHMM 区分。
- 7.2 完成前不得开始任何 Phase 8 修复；9.1 对账完成前 roadmap 不得关闭。
- 与其他 roadmap 的发现重叠时，以先完成修复方为准，另一方台账登记 cross-reference 后关闭，不重复修复。
- mission 配置（仓库根 missions/ 目录下的 concurrency-audit.json）的 `roadmapPath`/`plansDir`/`auditsDir` 必须指向 `ai-dev/` 下路径（0.3 的验收条件）。
