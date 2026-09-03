# 作业提交前校验产品化：dry-run 连通性 + 凭据加密接入 + conf-validate 命令（roadmap item 20 / P-REQ-13/14）

> Plan Status: active
> Mission: nop-stream-productization
> Work Item: item 20 作业提交前校验产品化
> Last Reviewed: 2026-09-03
> Source: `ai-dev/analysis/2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md` §2.1（P-REQ-13/14 go 裁定及依据）+ §1.1（live 证据锚点）；`ai-dev/analysis/2026-09/2026-09-01-competitor-productization-synthesis-and-p-req.md` §2.2 P-REQ-13/14（要求与验收标准，SeaTunnel ST-2/ST-6 参照）；`ai-dev/analysis/2026-09/2026-09-01-nop-stream-connectors-module-audit.md` §2.1（候选钩子清单 H-1..H-6 + 消费建议 B——自包含，本 plan 直接消费）+ §2.2（XDef/构造期校验覆盖现状表）
> Related: `2026-09-03-0830-1-connector-ecosystem-spi-registry.md`（item 19 先于本 plan 执行——执行顺序 1 → 2；本 plan 的探测/校验面应消费其注册中心与能力描述，Phase 1 必答协调项）；`2026-09-02-2216-1-observability-ops-productization.md`（`StreamMaintenanceMain` 入口族先例——reset-state/reshard 已收敛该入口）；`ai-dev/design/nop-stream/composite-scenario-design.md` §3.2（已预留「dry-run 校验 S1 拓扑作为提交前步骤」）

## Purpose

把作业配置校验从「启动后才暴露连接问题」推进到「提交前可独立校验」：P-REQ-13（连接器 dry-run 连通性验证，fail-fast + 结构化错误）与 P-REQ-14（凭据加密接入 nop-credential + conf-validate 独立校验命令，不启动作业即字段级报错）双双达到验收标准，消费 item 10 审计的自包含钩子清单（H-1..H-6）落地最小侵入实现。

## Current Baseline

（2026-09-03 live 核对）

- dry-run 现状：零覆盖——`rg "dryrun|dry-run|ConnectorCheck"` nop-stream 零命中（D-GAP §1.1）；无 CLI/命令模块。但**入口族先例已存在**：item 16 交付 `StreamMaintenanceMain`（reset-state/reshard 同一入口族，拒绝语义显式）——本 plan 的命令面可加入该入口族或裁定新入口。
- 钩子清单（connectors 审计 §2.1，自包含，优先级序）：**H-5**（2PC sink：dry-run 调 `beginTransaction()+rollback()` 零 core 改动；JDBC 物理连通可追加幂等 DDL 或 `SELECT 1`，item 20 裁定）> **H-1**（FLIP-27 source：no-op AssignmentDeliveryService 调 `createEnumerator()+start()` 零 core 改动，FileSplitEnumerator.start 即目录可达性探测——四模块唯一 open 期连通性校验实证）> **H-3/H-4**（SourceFunction/SinkFunction 家族：审计推荐仿 `DrainableSource` 先例新增 `ConnectivityCheckable` 类 marker 接口（`void checkConnection()`），连接器按能力实现，dry-run 驱动器 instanceof 分派；BatchConsumerSinkFunction 构造期 setup 是天然探测点）> **H-6**（builder 级统一入口：`StreamModelDslBuilder.buildSource/buildSink` 解析后、execute 前插入 validate 回调，flow 侧 additive）。
- 审计显式记录的**设计缺口**（本 plan Phase 1 必答）：① `IMessageService` 无 health-check API——MessageSource/MessageSink 的探测语义需裁定（send 探针消息 vs 跳过并显式报告）；② H-5 的 JDBC 探测形态（`initializeLedgerTable()` 幂等 DDL vs `SELECT 1`）。
- 凭据现状：nop-credential 九模块 live（api/app/codegen/dao/kms-vault/meta/service/web/deploy）；`ICredentialProvider`（api）为平台唯一解密点（`cv1:` 密文，fail-closed 永不返回 null，Javadoc 明示明文边界）；`testCredential(credentialId)` API 存在但为 **W2 桩语义——恒返回 `success=false`（"test not implemented"），非可用连通性探测**（接口 Javadoc 与 `TestResult` Javadoc 显式记载，实现被测试钉定）；`CredentialLookup`/`ICredentialTypeRegistry`/`MaskedCredential` 可用。nop-stream 与 nop-credential **零集成**（D-GAP §1.1）。审计指定接入点：`DebeziumConfig` 20 字段含 `databaseUser/databasePassword`（明文 Serializable 进 checkpoint/TDD 序列化路径）——注意该类实际位于 `nop-message/nop-message-debezium/`（`io.nop.message.debezium.DebeziumConfig`，nop-stream 之外的模块；`nop-stream-connector-debezium` 仅含 `DebeziumCdcSourceFunction` 一个 main 类）；JDBC 侧凭据在 `IJdbcTemplate` 平台配置内（connector 不经手）。
- bean 解析事实：S1/S2 场景的连接器 bean（`cdcSource`/`jdbcSinkRapid` 等）**只存在于 test 侧程序化注册**（`ScenarioTestSupport.s1Resolver/s2Resolver` 的 `InMemoryBeanFunctionResolver.register`，fraud-example main 侧无任何 beans.xml）；生产路径 = `GlobalBeanFunctionResolver` → NopIoC `BeanContainer`。conf-validate/dry-run 若只接收 XDSL 路径，无法解析 S1/S2 的 bean 引用——bean 来源输入是必须裁定的命令契约（Phase 1 必答）。
- 校验链路现状（connectors 审计 §2.2）：模型层走 `stream.xdef` 字段级校验（解析期）；连接器配置层 8/8 bean 走构造期 typed 校验；conf-validate 命令可复用「构造 + H-1/H-5 探测」组合，不启动作业。缺独立校验命令与凭据接入。
- 复合场景预留：composite-scenario-design §3.2 S3 裁定行内联「Follow-up item 20 落地后，把 dry-run 校验 S1 拓扑作为 S1 的提交前步骤」——S1/S2 XDSL（`fraud-s1-cdc.stream.xml`/`fraud-s2-file.stream.xml`）是天然端到端验证对象。
- REST 运维面（item 16）已有 submit/stop/list/detail 端点与结构化错误先例（404/400/409）；是否暴露 dry-run 为 REST 端点未裁定。

## Goals

- P-REQ-13 验收达成：dry-run 入口类与测试存在；错误配置返回显式错误码而非静默通过（按连接器族逐族覆盖）。
- P-REQ-14 验收达成：凭据加密存储接入（作业配置引用凭据，明文不落作业配置/checkpoint 序列化路径）+ encrypt/conf-validate 等价命令存在；缺失必填项输出含选项名的字段级错误。
- 消费 H-1..H-6 钩子清单按优先级落地最小侵入实现（优先零 core 改动路径；新增接口仅 additive）。
- 文档收口：用户指南/运维 owner doc 补 dry-run 与 conf-validate 用法、凭据引用语法；INDEX/source-anchors 同步。

## Non-Goals

- 连接器 SPI 注册中心与 OLAP 扩展（item 19，先行；本 plan 消费其产出但不建设）。
- 平台侧改动：`IMessageService` 增加 health-check API、nop-credential 模块自身变更（本 plan 只做 stream 侧消费；若裁定需要平台侧变更，降级为显式裁定记录 + Follow-up 候选，不就地改平台模块）。
- 作业配置整文件加密（SeaTunnel EncryptConfigServlet 的全文件加密形态不照搬——只做凭据字段的引用/解密接入，Phase 1 裁定记录依据）。
- Web 控制台/REST 化 dry-run（除非 Phase 1 裁定纳入；默认 CLI/入口族交付）。
- flow DSL 编译器收敛（item 29）；运行期异常处理策略（P-REQ-17 已 defer）。

## Scope

### In Scope

- Phase 1 七组设计裁定（见执行项）。
- dry-run 驱动实现 + `ConnectivityCheckable` 类 additive 接口（按 Phase 1 裁定）+ 各连接器族探测实现与 focused 测试。
- 凭据引用/解密接入（DebeziumConfig password 字段为审计指定必达点；其余连接器族按凭据字段存在性裁定覆盖面）。
- conf-validate 独立校验命令（模型解析 + 连接器构造校验 + 可选连通性探测，字段级结构化错误）。
- fraud-example S1 拓扑 dry-run 端到端验证（设计文档预留路径）。
- owner doc / user-guide / INDEX / source-anchors 同步。

### Out Of Scope

- 上列 Non-Goals 全部；item 21+24+30 状态路径重构；items 25—28 runtime 治理。

## Execution Plan

### Phase 1 - 设计裁定

Status: planned
Targets: `ai-dev/design/nop-stream/`（裁定记录落点按内容归属：连接器/提交校验相关决策进 connector-design.md 或新设计文档，Phase 1 自裁并记录）

- Item Types: `Decision`

- [ ] **入口面裁定**：dry-run 与 conf-validate 的命令入口形态（加入 `StreamMaintenanceMain` 入口族子命令 vs 独立 main vs 两者）；REST 化是否纳入（默认不纳入，记录依据）；与 item 16 交付的运维入口收敛原则对齐
- [ ] **bean 解析来源与容器引导裁定**（Phase 2/3 端到端的前置）：命令输入除 XDSL 路径外的 bean 来源形态（附加 beans.xml 路径参数 / NopIoC 容器引导（生产路径）/ 编程式 resolver 注入（测试/嵌入形态）——三者可组合）；对 S1/S2（bean 仅 test 侧程序化注册）验证该形态可解析；与 item 19 注册中心落地后的类型化声明路径的并存关系
- [ ] **探测语义裁定**（逐族，消费审计 H-1..H-6 优先级）：① H-5 JDBC 探测形态（幂等 DDL vs `SELECT 1` vs beginTransaction+rollback 组合）；② H-3/H-4 的 `ConnectivityCheckable` additive 接口采纳与否（审计推荐采纳；若采纳，未实现该接口的连接器在 dry-run 中的语义 = 显式「不支持探测」报告，非静默通过）；③ MessageSource/MessageSink（`IMessageService` 无 health-check）探测语义 = send 探针 vs 显式 skip 报告；④ H-6（builder 级 validate 回调）采纳与否与插入点；⑤ Debezium 族探测深度（构造/参数级校验 vs 真实连库——审计已否决 run()+cancel() 因会拉起真引擎）；⑥ 探测副作用红线与「残留」定义：dry-run 不得产生**作业正常运行本身不会创建的对象**（幂等 DDL 建表属预期幂等对象不算残留；2PC 台账行/epoch 终文件/订阅位点算），rollback/清理语义逐族声明
- [ ] **凭据接入机制裁定**：作业配置如何引用 credentialId（bean 定义处引用 vs XDSL 声明处引用）；**密文驻留位置与解密时点机制**（含：解密值是否/如何进入 `DebeziumConfig`——注意其 config 为非 transient 序列化字段且 `testConfigSurvivesSerialization` 钉定跨 JVM 恢复语义，「构造前解密并写入明文」与「明文不进序列化路径」不可兼得，机制须自洽并显式拒绝不可行组合；**含是否需要变更 `nop-message-debezium` 的模块边界裁定**——若需变更平台消息模块且超出本 plan 边界，降级为函数构造侧机制并记录）；明文边界（对齐 `ICredentialProvider` Javadoc：明文不跨服务进程、fail-closed）；**凭据探测不依赖 `testCredential()`**（W2 恒失败桩——stream 侧凭据可达性探测语义另行裁定：凭据解密成功即「凭据可达」vs 显式「平台探测能力未实现」报告项）；kms-vault 透传性裁定（stream 侧仅消费 `ICredentialProvider`，kms-vault 作为后端实现透明——roadmap item 20「含 kms-vault」字样的消费方式）
- [ ] **encrypt 等价物裁定**（P-REQ-14 验收「encrypt 工具/接口与 conf-validate 命令存在」的前半）：encrypt 等价物 = nop-credential 既有凭据写入/加密接口（指明具体平台面）+ 记录拒绝照搬 SeaTunnel `EncryptConfigServlet` 整文件加密形态的依据（Non-Goals 已定方向，此处落等价映射使验收可核对）
- [ ] **与 item 19 的协调裁定**（必答）：若 item 19 已落地（执行顺序在前），dry-run/conf-validate 经其注册中心与能力描述解析连接器并校验能力声明；若未落地（跳序执行等场景），直接走 bean 解析路径——两态都写明，保证本 plan 可独立成立
- [ ] conf-validate 的分层校验范围与错误输出契约裁定：层 1 模型解析（stream.xdef 字段级）→ 层 2 连接器构造（typed 构造校验）→ 层 3 可选连通性探测（--connect 开关语义）；错误输出 = 结构化逐条（层号 + 元素/选项名 + 错误码 + 指引），P-REQ-14 验收「含选项名」对齐
- [ ] 设计文档落盘（决策与契约，无实现级签名）；README 索引核对

Exit Criteria:

- [ ] 七组裁定全部落档且各含拒绝替代方案；探测副作用红线与「残留」定义逐族声明；凭据机制裁定自洽（密文驻留 × 序列化路径约束无矛盾组合）
- [ ] bean 解析来源裁定含对 S1/S2 bean（test 侧程序化注册形态）的可解析性说明——Phase 2 端到端按此裁定设计命令输入
- [ ] 设计文档 doc-links 通过；不含实现级类签名/伪代码
- [ ] **无静默跳过**：纯决策 phase（不适用，显式声明）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - conf-validate 独立校验命令（P-REQ-14 命令面）

Status: planned
Targets: 命令入口模块（按 Phase 1 裁定）+ `nop-stream/nop-stream-flow/`（若需 builder 级插入，H-6）

- Item Types: `Fix | Proof`

- [ ] conf-validate 命令实现：输入作业定义（XDSL 路径 + Phase 1 裁定的 bean 来源输入形态），执行层 1+2 校验，不启动作业；`--connect` 开关联动层 3（Phase 3 交付后接线）
- [ ] 字段级错误输出：缺失必填/类型错误逐条输出含选项名（P-REQ-14 验收原文）；exit code 语义显式（校验失败非 0）
- [ ] focused 测试：合法作业通过；逐类非法作业（模型层错误/构造层错误/缺失必填）各自断言错误内容与 exit code

Exit Criteria:

- [ ] 命令存在且不启动作业即完成校验（P-REQ-14 验收）；缺失必填项错误含选项名的用例存在
- [ ] **端到端验证**：一条测试从命令入口 →（按 Phase 1 bean 解析裁定构造输入，含 S1/S2 bean 的可解析形态）→ 解析真实 stream.xml → 输出校验结果完整走通
- [ ] **无静默跳过**：无法识别的元素/配置显式报错；层 3 未接线时 `--connect` 显式「未实现/not yet available」错误而非静默忽略
- [ ] **新功能必有测试**：列出命令通过/逐类失败用例名
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] owner-doc 裁定记录（用法文档落点；详表可在 Phase 5 统一同步，此处记录裁定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - dry-run 连通性验证（P-REQ-13）

Status: planned
Targets: `nop-stream/nop-stream-core/`（additive 接口，按 Phase 1 裁定）+ 4 个连接器模块（各族探测实现）

- Item Types: `Fix | Proof`

- [ ] dry-run 驱动实现（按 Phase 1 入口裁定）：逐 source/sink 元素执行探测，汇总结构化报告（逐族结果 + 不支持探测的显式标注）
- [ ] 各连接器族探测落地（按 Phase 1 逐族语义）：H-5（JDBC/File 2PC sink）、H-1（FileSource 族）、H-3/H-4（SourceFunction/SinkFunction 家族按裁定——含 `ConnectivityCheckable` 接口若采纳、Message 族按裁定语义、Debezium 族按裁定 ⑤ 的探测深度（构造/参数级校验 vs 真实连库；凭据解密面归 Phase 4，本 phase 不依赖）、Batch 双族天然点）
- [ ] conf-validate `--connect` 开关接线层 3（Phase 2 交付的开关在本 phase 接通真实探测；开关行为端到端验证）
- [ ] 探测副作用验证：dry-run 后无「残留」（按 Phase 1 定义：2PC 台账行/epoch 终文件/订阅位点等逐族断言或代码审查记录；幂等 DDL 类预期对象除外并显式标注）
- [ ] focused 测试：错误配置（目录不存在/连接不可达）返回显式错误码而非静默通过（P-REQ-13 验收原文）——**可探测族**逐族至少一个失败路径用例；**不可探测族**（如 Message 族若 Phase 1 裁定显式 skip）断言输出显式 skip 报告项（与 Deferred 条目对齐）；凭据 fail-closed 失败路径用例归 Phase 4（凭据面届时才存在，见 Phase 4 执行项）

Exit Criteria:

- [ ] dry-run 入口类与测试存在（P-REQ-13 验收原文）；可探测族错误配置显式错误码用例逐族覆盖；不可探测族显式 skip 报告用例存在
- [ ] **端到端验证**：S1 拓扑（`fraud-s1-cdc.stream.xml`）dry-run 作为提交前步骤完整跑通（composite-scenario-design §3.2 预留路径），合法配置通过、注入错误配置失败；conf-validate `--connect` 开关经端到端验证接通层 3
- [ ] **接线验证**：`ConnectivityCheckable`（若采纳）确被 dry-run 驱动 instanceof 分派调用（测试断言或 mock verify）；H-1/H-5 复用路径的调用链经测试确认
- [ ] **无静默跳过**：未实现探测的连接器族输出显式「不支持」结果项，不伪装成通过
- [ ] **新功能必有测试**：列出各族探测通过/失败用例名
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 凭据加密接入（P-REQ-14 凭据面）

Status: planned
Targets: 凭据引用解析落点（按 Phase 1 裁定：flow builder / bean 装配层 / `DebeziumConfig` 所在层）+ 依赖变更（nop-credential-api）+（若 Phase 1 裁定需要且属边界内）`nop-message/nop-message-debezium/`

- Item Types: `Fix | Proof`

- [ ] 凭据引用与解密接入：按 Phase 1 语法与**密文驻留/解密时点机制**裁定实现（机制须自洽满足两条约束：解密成功可供连接器使用 + 明文不进 Serializable 配置对象与 checkpoint 序列化路径——如 config 持久驻留 `cv1:` 密文、解密仅发生在引擎侧瞬态路径等由裁定选定）；Debezium 族（`databasePassword`/`databaseUser` 按裁定）为必达点——若裁定需变更 `nop-message-debezium` 的 `DebeziumConfig`，属跨 nop-stream 模块边界，按 Phase 1 边界裁定执行或降级记录（对齐 Non-Goals 的降级语义：不做平台侧变更时改为函数构造侧机制并记录）
- [ ] 凭据不可用行为：fail-closed 对齐（凭据缺失/已删除 = 显式错误，不静默空串）；凭据可达性结论按 Phase 1 裁定呈现（解密成功即凭据可达，或显式「平台探测能力未实现（`testCredential` 为 W2 桩）」报告项——不产生系统性假阴性）
- [ ] focused 测试：加密凭据引用 → 解密 → 连接器构造/使用成功路径；凭据缺失 fail-closed 路径（回填 Phase 3 清单的凭据失败用例归属）；序列化路径不含明文断言（与 `testConfigSurvivesSerialization` 既有语义对齐——密文可序列化恢复、明文不出现）

Exit Criteria:

- [ ] 凭据加密存储接入作业配置（P-REQ-14 验收）——Debezium 族必达，其余族覆盖面与 Phase 1 裁定一致
- [ ] **无静默跳过**：凭据解析失败显式错误（fail-closed 语义用例存在）；无依赖 W2 桩 `testCredential()` 产生假阴性的探测路径
- [ ] **新功能必有测试**：列出解密成功/fail-closed/无明文泄漏用例名
- [ ] 依赖与模块边界合规：仅新增 nop-credential-api（等 api 层）依赖；`nop-message-debezium` 变更与否与 Phase 1 边界裁定一致（裁定记录）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 文档收口与回归

Status: planned
Targets: `docs-for-ai/03-modules/nop-stream.md` / `nop-stream-user-guide.md` / `nop-stream-connectors.md`（按裁定落点）、`docs-for-ai/INDEX.md`、`04-reference/source-anchors.md`

- Item Types: `Proof | Follow-up`

- [ ] owner doc 同步：dry-run/conf-validate 用法（命令、层 1—3 语义、exit code、错误样例）、凭据引用语法与明文边界、逐族探测能力表（支持/不支持/副作用红线）
- [ ] INDEX/source-anchors 同步；conf-validate `--connect` 开关与层 3 的接线在 Phase 3 已交付——本 phase 复核最终态端到端一致（无条件复核项，非条件兜底）
- [ ] 全量回归 + 工具门禁

Exit Criteria:

- [ ] 文档与 live 行为一致（抽查命令用法与错误样例可复现）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] P-REQ-13 验收原文逐条核对：dry-run 入口类与测试存在；错误配置返回显式错误码而非静默通过
- [ ] P-REQ-14 验收原文逐条核对：**encrypt 工具/接口**与 conf-validate 命令存在（encrypt 等价物按 Phase 1 裁定的平台面映射核对）；缺失必填项输出含选项名的错误
- [ ] H-1..H-6 消费完整性：逐钩子处置记录（采纳/按裁定替代/显式不适用 + 理由），无静默忽略
- [ ] 探测副作用红线经测试或代码审查验证（无残留数据/位点/文件）
- [ ] 独立子 agent closure audit 已完成并记录证据（含 Anti-Hollow：dry-run 驱动到各族探测的调用链运行时连通验证）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [ ] roadmap item 20 写回 `done`（closure audit 通过后）

## Deferred But Adjudicated

### MessageSource/MessageSink 深度探测（若 Phase 1 裁定为显式 skip 报告）

- Classification: `watch-only residual`
- Why Not Blocking Closure: `IMessageService` 无平台 health-check API，dry-run 侧探针语义（真实 send 可见副作用）不符合探测副作用红线；显式「不支持探测」报告满足 P-REQ-13「fail-fast + 结构化错误」验收（不可达时的可达性结论如实呈现为不可判定）
- Successor Required: `no`（若平台侧未来提供 health-check，作为增强候选）
- Successor Path: —

## Non-Blocking Follow-ups

- REST 化 dry-run/conf-validate（若 Phase 1 裁定不纳入——作为 ops API 增强候选，条件 = 出现远程提交工作流需求）。
- 平台侧 `IMessageService` health-check 提案（若 Phase 1 裁定需要——记录为 nop-sys/nop-message 侧候选变更，不在本 mission 修平台模块）。
- 平台侧 `ICredentialProvider.testCredential()` 真实实现（现为 W2 恒失败桩，`nop-credential` 侧候选变更——stream 侧凭据探测按 Phase 1 裁定不依赖它；平台实现后可作为 dry-run 增强接入）。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent session/task id>>
- Evidence: <<逐条 Exit Criterion / Closure Gate 验证结果 + 工具退出码>>

Follow-up:

- <<只记录 non-blocking follow-up；或写 no remaining plan-owned work>>
