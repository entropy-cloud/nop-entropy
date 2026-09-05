# 作业提交前校验产品化：dry-run 连通性 + 凭据加密接入 + conf-validate 命令（roadmap item 20 / P-REQ-13/14）

> Plan Status: completed
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

Status: completed
Targets: `ai-dev/design/nop-stream/`（裁定记录落点按内容归属：连接器/提交校验相关决策进 connector-design.md 或新设计文档，Phase 1 自裁并记录）

- Item Types: `Decision`

- [x] **入口面裁定**：dry-run 与 conf-validate 的命令入口形态（加入 `StreamMaintenanceMain` 入口族子命令 vs 独立 main vs 两者）；REST 化是否纳入（默认不纳入，记录依据）；与 item 16 交付的运维入口收敛原则对齐
  — *落档 pre-submit-validation-design.md D1：加入入口族（conf-validate + dry-run 子命令）；REST 不纳入（本地工具无远程需求，follow-up 候选）；核心逻辑在 flow、入口在 runtime*
- [x] **bean 解析来源与容器引导裁定**（Phase 2/3 端到端的前置）：命令输入除 XDSL 路径外的 bean 来源形态（附加 beans.xml 路径参数 / NopIoC 容器引导（生产路径）/ 编程式 resolver 注入（测试/嵌入形态）——三者可组合）；对 S1/S2（bean 仅 test 侧程序化注册）验证该形态可解析；与 item 19 注册中心落地后的类型化声明路径的并存关系
  — *落档 D2：三形态（程序化 resolver=库级 API / CLI beans= 显式装配 / 缺省 GlobalBeanFunctionResolver）；S1/S2 以程序化形态可解析（E2E 装配形态）；与注册中心世界并列不互通*
- [x] **探测语义裁定**（逐族，消费审计 H-1..H-6 优先级）：① H-5 JDBC 探测形态（幂等 DDL vs `SELECT 1` vs beginTransaction+rollback 组合）；② H-3/H-4 的 `ConnectivityCheckable` additive 接口采纳与否（审计推荐采纳；若采纳，未实现该接口的连接器在 dry-run 中的语义 = 显式「不支持探测」报告，非静默通过）；③ MessageSource/MessageSink（`IMessageService` 无 health-check）探测语义 = send 探针 vs 显式 skip 报告；④ H-6（builder 级 validate 回调）采纳与否与插入点；⑤ Debezium 族探测深度（构造/参数级校验 vs 真实连库——审计已否决 run()+cancel() 因会拉起真引擎）；⑥ 探测副作用红线与「残留」定义：dry-run 不得产生**作业正常运行本身不会创建的对象**（幂等 DDL 建表属预期幂等对象不算残留；2PC 台账行/epoch 终文件/订阅位点算），rollback/清理语义逐族声明
  — *落档 D3 + §4.1 逐族探测表（含红线声明）：① begin+幂等 DDL+rollback 组合；② 采纳（四族实现，未实现=显式不支持报告）；③ 显式 skip；④ 不采纳（命令面不 execute，builder 零变更）；⑤ 构造/参数级+凭据解密可达；⑥ 红线定义 + 幂等对象豁免 + 残留逐族断言要求*
- [x] **凭据接入机制裁定**：作业配置如何引用 credentialId（bean 定义处引用 vs XDSL 声明处引用）；**密文驻留位置与解密时点机制**（含：解密值是否/如何进入 `DebeziumConfig`——注意其 config 为非 transient 序列化字段且 `testConfigSurvivesSerialization` 钉定跨 JVM 恢复语义，「构造前解密并写入明文」与「明文不进序列化路径」不可兼得，机制须自洽并显式拒绝不可行组合；**含是否需要变更 `nop-message-debezium` 的模块边界裁定**——若需变更平台消息模块且超出本 plan 边界，降级为函数构造侧机制并记录）；明文边界（对齐 `ICredentialProvider` Javadoc：明文不跨服务进程、fail-closed）；**凭据探测不依赖 `testCredential()`**（W2 恒失败桩——stream 侧凭据可达性探测语义另行裁定：凭据解密成功即「凭据可达」vs 显式「平台探测能力未实现」报告项）；kms-vault 透传性裁定（stream 侧仅消费 `ICredentialProvider`，kms-vault 作为后端实现透明——roadmap item 20「含 kms-vault」字样的消费方式）
  — *落档 D4：`credential:{id}#{field}` 引用语法（bean 配置处，不开 XDSL 新声明面）；引用驻留 Serializable 配置 + 引擎侧瞬态解密副本（不可行组合显式拒绝）；不变更 nop-message-debezium（解密点在 connector-debezium 边界内）；fail-closed；可达性=解密成功不依赖 testCredential；kms-vault 经接口透明；core 新增 nop-credential-api 唯一依赖*
- [x] **encrypt 等价物裁定**（P-REQ-14 验收「encrypt 工具/接口与 conf-validate 命令存在」的前半）：encrypt 等价物 = nop-credential 既有凭据写入/加密接口（指明具体平台面）+ 记录拒绝照搬 SeaTunnel `EncryptConfigServlet` 整文件加密形态的依据（Non-Goals 已定方向，此处落等价映射使验收可核对）
  — *落档 D5：= nop-credential 凭据 CRUD 平台面（cv1: 落库 + 平台唯一解密点）；拒绝整文件加密（破坏 XDef 校验/Delta/diff）依据在案*
- [x] **与 item 19 的协调裁定**（必答）：若 item 19 已落地（执行顺序在前），dry-run/conf-validate 经其注册中心与能力描述解析连接器并校验能力声明；若未落地（跳序执行等场景），直接走 bean 解析路径——两态都写明，保证本 plan 可独立成立
  — *落档 D6：已落地 → 连接器模式（类型名+描述符参数规格字段级校验+构造探测）；未落地 → 连接器模式显式不可用、XDSL 模式独立成立（两态在案）*
- [x] conf-validate 的分层校验范围与错误输出契约裁定：层 1 模型解析（stream.xdef 字段级）→ 层 2 连接器构造（typed 构造校验）→ 层 3 可选连通性探测（--connect 开关语义）；错误输出 = 结构化逐条（层号 + 元素/选项名 + 错误码 + 指引），P-REQ-14 验收「含选项名」对齐
  — *落档 D7：三层范围（层 2 XDSL 模式 = 完整图构建不 execute）+ 逐条结构化错误（选项名必含）+ exit 0/1/2 契约 + 未接线期 --connect 显式 not-yet-available*
- [x] 设计文档落盘（决策与契约，无实现级签名）；README 索引核对
  — *pre-submit-validation-design.md 新建（D1..D7 决策汇总表）；README 运维层条目 + 阅读顺序 15 + 扩展方向重编号 + header Updated*

Exit Criteria:

- [x] 七组裁定全部落档且各含拒绝替代方案；探测副作用红线与「残留」定义逐族声明；凭据机制裁定自洽（密文驻留 × 序列化路径约束无矛盾组合）
- [x] bean 解析来源裁定含对 S1/S2 bean（test 侧程序化注册形态）的可解析性说明——Phase 2 端到端按此裁定设计命令输入
- [x] 设计文档 doc-links 通过；不含实现级类签名/伪代码
- [x] **无静默跳过**：纯决策 phase（不适用，显式声明）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - conf-validate 独立校验命令（P-REQ-14 命令面）

Status: completed
Targets: 命令入口模块（按 Phase 1 裁定）+ `nop-stream/nop-stream-flow/`（若需 builder 级插入，H-6）

- Item Types: `Fix | Proof`

- [x] conf-validate 命令实现：输入作业定义（XDSL 路径 + Phase 1 裁定的 bean 来源输入形态），执行层 1+2 校验，不启动作业；`--connect` 开关联动层 3（Phase 3 交付后接线）
  — *`nop-stream-flow` validate 包 `StreamConfValidator/StreamConfValidationReport/ValidationIssue`（层 1+2，XDSL 模式 + 连接器模式）；runtime `StreamConfValidateCommand` + `StreamMaintenanceMain` 入口族子命令 `conf-validate`/`dry-run`；`BeanContainerFunctionResolver`（D2 形态 2）；flow 新增 nop-xlang 编译依赖（层 1 解析 owner 职责）*
- [x] 字段级错误输出：缺失必填/类型错误逐条输出含选项名（P-REQ-14 验收原文）；exit code 语义显式（校验失败非 0）
  — *ValidationIssue 携带 layer/target/errorCode/paramName（选项名）；exit 0/1/2 契约（用法错误 2 由命令层映射）*
- [x] focused 测试：合法作业通过；逐类非法作业（模型层错误/构造层错误/缺失必填）各自断言错误内容与 exit code
  — *flow `TestStreamConfValidator`（10 用例：合法通过/层 1 未知元素/层 2 bean 缺失含选项名/required-body/连接器模式 4 例/--connect 显式 not-yet-available×2）+ `TestBeanContainerFunctionResolver`（4 用例）+ runtime `TestStreamConfValidateCommand`（7 用例：用法错误×3/合法 xpl 作业/非法模型/缺失 bean/usage 契约）*

Exit Criteria:

- [x] 命令存在且不启动作业即完成校验（P-REQ-14 验收）；缺失必填项错误含选项名的用例存在
- [x] **端到端验证**：一条测试从命令入口 →（按 Phase 1 bean 解析裁定构造输入，含 S1/S2 bean 的可解析形态）→ 解析真实 stream.xml → 输出校验结果完整走通
  — *fraud-example `TestConfValidatePreSubmitE2E`（3 用例：S1 拓扑 exit 0 / S2 拓扑 exit 0 / 缺失 bean exit 1 且报告含 bean 名——D2 形态 1 程序化 resolver 经命令入口）*
- [x] **无静默跳过**：无法识别的元素/配置显式报错；层 3 未接线时 `--connect` 显式「未实现/not yet available」错误而非静默忽略
- [x] **新功能必有测试**：列出命令通过/逐类失败用例名（见上：TestStreamConfValidator / TestBeanContainerFunctionResolver / TestStreamConfValidateCommand / TestConfValidatePreSubmitE2E）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（2026-09-03 BUILD SUCCESS）
- [x] owner-doc 裁定记录（用法文档落点；详表可在 Phase 5 统一同步，此处记录裁定）
  — *裁定：用法/错误样例落 `docs-for-ai/03-modules/nop-stream.md` + user-guide（Phase 5 统一同步）；设计契约已在 pre-submit-validation-design.md D1/D2/D7*
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - dry-run 连通性验证（P-REQ-13）

Status: completed
Targets: `nop-stream/nop-stream-core/`（additive 接口，按 Phase 1 裁定）+ 4 个连接器模块（各族探测实现）

- Item Types: `Fix | Proof`

- [x] dry-run 驱动实现（按 Phase 1 入口裁定）：逐 source/sink 元素执行探测，汇总结构化报告（逐族结果 + 不支持探测的显式标注）
  — *core `StreamConnectivityProber`（分派序：ConnectivityCheckable → FLIP-27 Source（H-1 no-op 上下文）→ TwoPhaseCommitSinkFunction（H-5 基形态）→ 显式 SKIP）+ `ConnectivityCheckable`/`ConnectivityProbeOutcome`；flow `StreamConfValidator.probeEndpoints` 逐 source/sink 解析端点并探测；report 增 probeLog + SKIP 语义（exit 0 允许显式 skip）*
- [x] 各连接器族探测落地（按 Phase 1 逐族语义）：H-5（JDBC/File 2PC sink）、H-1（FileSource 族）、H-3/H-4（SourceFunction/SinkFunction 家族按裁定——含 `ConnectivityCheckable` 接口若采纳、Message 族按裁定语义、Debezium 族按裁定 ⑤ 的探测深度（构造/参数级校验 vs 真实连库；凭据解密面归 Phase 4，本 phase 不依赖）、Batch 双族天然点）
  — *JdbcTwoPhaseCommitSink.checkConnection=begin+幂等 DDL+rollback；FileTwoPhaseCommitSink.checkConnection=begin+rollback+目录存在断言；BatchLoaderSourceFunction.checkConnection=setup+close；BatchConsumerSinkFunction.checkConnection=构造级 consumer 非空断言；DebeziumCdcSourceFunction.checkConnection=参数级（name 校验，⑤ 深度边界用例钉定不拉引擎）；Message 族不实现=显式 skip；catalog 新增 probeConnectivity（连接器模式层 3）*
- [x] conf-validate `--connect` 开关接线层 3（Phase 2 交付的开关在本 phase 接通真实探测；开关行为端到端验证）
  — *validateStream/validateConnector connect=true 走真实探测；runtime `TestStreamConfValidateCommand.connectSwitchProbesEndpointsEndToEnd`（CLI --connect → SKIP 项 + probe log + exit 0）；Phase 2 的 not-yet-available 桩测试同步替换为真实行为断言*
- [x] 探测副作用验证：dry-run 后无「残留」（按 Phase 1 定义：2PC 台账行/epoch 终文件/订阅位点等逐族断言或代码审查记录；幂等 DDL 类预期对象除外并显式标注）
  — *JDBC：台账表存在（豁免对象）+ 行数==0 断言（模块测试 + S1 E2E 双处）；file：输出目录存在（豁免）+ 目录空断言（无 epoch/manifest/tmp 文件）；batch-loader：loader close 断言；FLIP-27：no-op 投递（输入目录内容不变断言）*
- [x] focused 测试：错误配置（目录不存在/连接不可达）返回显式错误码而非静默通过（P-REQ-13 验收原文）——**可探测族**逐族至少一个失败路径用例；**不可探测族**（如 Message 族若 Phase 1 裁定显式 skip）断言输出显式 skip 报告项（与 Deferred 条目对齐）；凭据 fail-closed 失败路径用例归 Phase 4（凭据面届时才存在，见 Phase 4 执行项）
  — *flow `TestStreamConnectivityDryRun`（7：能力接口分派调用断言（接线验证）/FLIP-27 start 调用断言 + 缺目录 FAIL/第三方 2PC 基契约 fallback 断言/失败探测显式错误码含端点与 bean 名/无契约端点显式 skip×2/prober 直连契约）；connector `TestPreSubmitConnectivityProbe`（5：file source 目录可达 PASS+内容不变/缺目录 FAIL/file 2PC sink 无残留/message 双族显式 skip）；jdbc `TestJdbcPreSubmitConnectivityProbe`（3：台账行零+表存在豁免/重复探测无累积/连接不可达 FAIL 显式码）；batch `TestBatchPreSubmitConnectivityProbe`（5：loader setup+close/setup 抛 FAIL/null loader FAIL/consumer 构造级 PASS 不重复 setup/null consumer FAIL）；debezium `TestDebeziumPreSubmitConnectivityProbe`（3：合法参数 PASS/name 缺失 FAIL 显式码/不可达 host 仍 PASS=⑤ 深度边界钉定不连库）*

Exit Criteria:

- [x] dry-run 入口类与测试存在（P-REQ-13 验收原文）；可探测族错误配置显式错误码用例逐族覆盖；不可探测族显式 skip 报告用例存在
- [x] **端到端验证**：S1 拓扑（`fraud-s1-cdc.stream.xml`）dry-run 作为提交前步骤完整跑通（composite-scenario-design §3.2 预留路径），合法配置通过、注入错误配置失败；conf-validate `--connect` 开关经端到端验证接通层 3
  — *`TestConfValidatePreSubmitE2E.s1DryRunProbesAllEndpointsAsPreSubmitStep`（exit 0 + 4 sink PASS + cdc source PASS（debezium 参数级）+ 四台账表行数==0）+ `.s1DryRunWithUnreachableSinkFailsExplicitly`（exit 1 + connectivity-check-failed + 端点名）+ runtime CLI connectSwitch 端到端*
- [x] **接线验证**：`ConnectivityCheckable`（若采纳）确被 dry-run 驱动 instanceof 分派调用（测试断言或 mock verify）；H-1/H-5 复用路径的调用链经测试确认
  — *RecordingCheckable checkCount 断言（flow）；H-1 enumerator startCount 断言；H-5 begin/rollback count 断言（flow 第三方 2PC + jdbc 模块 begin/DDL/rollback 组合）；S1 E2E 四 sink PASS 行 = 生产分派证据*
- [x] **无静默跳过**：未实现探测的连接器族输出显式「不支持」结果项，不伪装成通过（message 双族 + 无契约端点 + xpl 端点三处用例）
- [x] **新功能必有测试**：列出各族探测通过/失败用例名（见上五类 23 用例 + E2E 2 + CLI 1）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（2026-09-03 BUILD SUCCESS）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 凭据加密接入（P-REQ-14 凭据面）

Status: completed
Targets: 凭据引用解析落点（按 Phase 1 裁定：flow builder / bean 装配层 / `DebeziumConfig` 所在层）+ 依赖变更（nop-credential-api）+（若 Phase 1 裁定需要且属边界内）`nop-message/nop-message-debezium/`

- Item Types: `Fix | Proof`

- [x] 凭据引用与解密接入：按 Phase 1 语法与**密文驻留/解密时点机制**裁定实现（机制须自洽满足两条约束：解密成功可供连接器使用 + 明文不进 Serializable 配置对象与 checkpoint 序列化路径——如 config 持久驻留 `cv1:` 密文、解密仅发生在引擎侧瞬态路径等由裁定选定）；Debezium 族（`databasePassword`/`databaseUser` 按裁定）为必达点——若裁定需变更 `nop-message-debezium` 的 `DebeziumConfig`，属跨 nop-stream 模块边界，按 Phase 1 边界裁定执行或降级记录（对齐 Non-Goals 的降级语义：不做平台侧变更时改为函数构造侧机制并记录）
  — *core `StreamCredentialSupport`（`credential:{id}#{field}` 解析 + 经 `ICredentialProvider` 解密 + 三个 fail-closed typed 错误码）；`DebeziumCdcSourceFunction`：provider 构造参数注入 + transient 字段（序列化路径不携带）+ `effectiveEngineConfig()` 引擎侧瞬态解密副本（序列化往返拷贝，原 config 永持引用串）+ `setCredentialProvider` 每 JVM 重注入点；SPI 工厂新增 optional `credentialProvider` OBJECT 参数；**nop-message-debezium 零变更**（D4 边界裁定兑现）*
- [x] 凭据不可用行为：fail-closed 对齐（凭据缺失/已删除 = 显式错误，不静默空串）；凭据可达性结论按 Phase 1 裁定呈现（解密成功即凭据可达，或显式「平台探测能力未实现（`testCredential` 为 W2 桩）」报告项——不产生系统性假阴性）
  — *checkConnection.verifyCredentialReferences：解密成功=可达；不调用 testCredential()（Javadoc 记录 W2 桩假阴性理由）；provider 缺失/凭据不存在/引用格式错=三类 typed 错误码（provider-missing/unresolved/ref-invalid）*
- [x] focused 测试：加密凭据引用 → 解密 → 连接器构造/使用成功路径；凭据缺失 fail-closed 路径（回填 Phase 3 清单的凭据失败用例归属）；序列化路径不含明文断言（与 `testConfigSurvivesSerialization` 既有语义对齐——密文可序列化恢复、明文不出现）
  — *core `TestStreamCredentialSupport`（7：语法识别/合法解析/畸形 fail-fast×4 形态/经 provider 解析/null provider fail-closed/未知凭据 fail-closed/未设字段 null 不静默替换）+ debezium `TestDebeziumCredentialIntegration`（9：**引擎收到瞬态解密副本且原 config 持引用断言**/无引用零开销同实例/provider 缺失 run+checkConnection 双路 fail-closed/未知凭据 typed 错/畸形引用 typed 错/可解析引用 dry-run PASS/**dry-run 探测显式呈现凭据失败（Phase 3 清单回填）**/**序列化字节含引用串不含明文 + transient provider 恢复后 null→fail-closed→重注入可达**/无引用序列化语义保持）*

Exit Criteria:

- [x] 凭据加密存储接入作业配置（P-REQ-14 验收）——Debezium 族必达，其余族覆盖面与 Phase 1 裁定一致（D4 边界事实：JDBC 凭据在 IJdbcTemplate 平台配置内 connector 不经手；file/message/batch 无凭据字段——设计文档在案）
- [x] **无静默跳过**：凭据解析失败显式错误（fail-closed 语义用例存在）；无依赖 W2 桩 `testCredential()` 产生假阴性的探测路径（可达性=解密成功，代码零调用 testCredential）
- [x] **新功能必有测试**：列出解密成功/fail-closed/无明文泄漏用例名（见上 16 用例）
- [x] 依赖与模块边界合规：仅新增 nop-credential-api（等 api 层）依赖；`nop-message-debezium` 变更与否与 Phase 1 边界裁定一致（裁定记录）
  — *core pom 唯一新增依赖 nop-credential-api（nop-bom 管版本，注释记录 D4）；nop-message-debezium git diff 为空*
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（2026-09-03 BUILD SUCCESS；一次并行构建下 TestMetricsExposureE2E @TempDir 清理竞态单发——隔离复跑×2 稳定通过，与本 phase 变更无关）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 文档收口与回归

Status: completed
Targets: `docs-for-ai/03-modules/nop-stream.md` / `nop-stream-user-guide.md` / `nop-stream-connectors.md`（按裁定落点）、`docs-for-ai/INDEX.md`、`04-reference/source-anchors.md`

- Item Types: `Proof | Follow-up`

- [x] owner doc 同步：dry-run/conf-validate 用法（命令、层 1—3 语义、exit code、错误样例）、凭据引用语法与明文边界、逐族探测能力表（支持/不支持/副作用红线）
  — *`nop-stream.md` 新增「提交前校验（conf-validate / dry-run，P-REQ-13/14）」节（命令/bean 三形态/连接器模式/分层语义/exit code/错误输出格式/红线 + 逐族探测能力表）+「凭据引用与明文边界（P-REQ-14）」节（语法/驻留与解密时点/fail-closed 码族/可达性语义/encrypt 等价物）；`nop-stream-user-guide.md` 连接器指引新增两条路由；`nop-stream-connectors.md` 新增「提交前探测能力（dry-run）」节（逐组件探测表 + 测试锚点）*
- [x] INDEX/source-anchors 同步；conf-validate `--connect` 开关与层 3 的接线在 Phase 3 已交付——本 phase 复核最终态端到端一致（无条件复核项，非条件兜底）
  — *INDEX 新增提交前校验路由行；source-anchors 新增 STRM-052..055（探测契约与驱动/校验核心/入口族/凭据接入）；最终态复核 = CLI `--connect` 端到端测试 + S1 E2E dry-run + **live 抽查**：裸 JVM 直接跑 `StreamMaintenanceMain conf-validate`（用法错误→2 / 文件不存在→2 / 非法作业→1 与文档逐条一致——抽查发现并修复真实缺陷：VFS 未初始化时绝对路径崩溃退出 1，修为回落本地文件解释 + 回归测试 `uninitializedVfsDoesNotCrashAbsoluteLocalPathLookup`）*
- [x] 全量回归 + 工具门禁
  — *`./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS；`./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` + typecheck/lint echo 通过；`check-doc-links --strict` exit 0（并修复 docs-for-ai→ai-dev 引用违界一处）；`scan-hollow-implementations --module nop-stream --severity high` exit 0*

Exit Criteria:

- [x] 文档与 live 行为一致（抽查命令用法与错误样例可复现）——裸 JVM CLI 三态抽查与文档逐条一致（见上）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] P-REQ-13 验收原文逐条核对：dry-run 入口类与测试存在；错误配置返回显式错误码而非静默通过
  — *入口 `StreamConfValidateCommand` + `StreamMaintenanceMain` dry-run 子命令；错误配置显式码用例：缺目录（TestPreSubmitConnectivityProbe）/连接不可达（TestJdbcPreSubmitConnectivityProbe + S1 E2E 死池 sink）/凭据不可解析（TestDebeziumCredentialIntegration.dryRunProbeSurfacesCredentialFailureExplicitly）——closure audit A/B 核对 PASS*
- [x] P-REQ-14 验收原文逐条核对：**encrypt 工具/接口**与 conf-validate 命令存在（encrypt 等价物按 Phase 1 裁定的平台面映射核对）；缺失必填项输出含选项名的错误
  — *encrypt 等价物 = nop-credential CRUD 平台面（cv1: 落库 + 唯一解密点）+ 本 plan 引用语法/解密接入（D5 映射，owner doc「凭据引用与明文边界」节记录）；conf-validate 命令存在；选项名用例：mapBean/unknownOption/target/jdbcSinkGeo（closure audit A/E 核对 PASS）*
- [x] H-1..H-6 消费完整性：逐钩子处置记录（采纳/按裁定替代/显式不适用 + 理由），无静默忽略
  — *H-1 采纳（FLIP-27 枚举探测）；H-2 显式不适用（被 H-1 取代——reader 侧延迟发现，§4.1 表补显式处置行，closure audit F-1 收口）；H-3 采纳（batch-loader + debezium 参数级）；H-4 采纳（batch-consumer 构造级）；H-5 采纳（jdbc begin+DDL+rollback / file begin+rollback + 第三方 2PC 基契约兜底）；H-6 显式不采纳（命令面不 execute，builder 零变更，§4.2④）*
- [x] 探测副作用红线经测试或代码审查验证（无残留数据/位点/文件）
  — *台账行==0（模块 + S1 E2E 双处断言）；file 输出目录空断言；loader close 断言；FLIP-27 输入目录内容不变断言（closure audit B 核对 PASS）*
- [x] 独立子 agent closure audit 已完成并记录证据（含 Anti-Hollow：dry-run 驱动到各族探测的调用链运行时连通验证）
  — *general subagent fresh session `ses_f9a677b85ffeJzf9OP3uOSpdFH`，报告 `_tmp/closure-audit-0830-2.md`，**CLOSURE-AUDIT: APPROVED**（A—E 全 PASS；Anti-Hollow：调用计数断言 + 分派链核查 + 复跑 `./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS；F-1 Low H-2 处置行已按建议补齐，F-2/F-3 信息级无功能缺陷）*
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] roadmap item 20 写回 `done`（closure audit 通过后）

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

Status Note: item 20 全 scope 收口——P-REQ-13（dry-run 入口 + 逐族探测 + 显式错误码 + 副作用红线断言）与 P-REQ-14（凭据引用驻留 + 引擎侧瞬态解密 + fail-closed + encrypt 等价物映射 + conf-validate 命令含选项名字段级报错）双双达到验收；五个 Phase 全部 completed 且经独立 closure audit 复核 live 代码/测试/工具门禁通过；H-1..H-6 逐钩子处置在案无静默忽略；Deferred（message 族深度探测 watch-only residual）与 Follow-ups（REST 化 / IMessageService health-check / testCredential 平台实现）均为 non-blocking 且理由在案。
Completed: 2026-09-03

Closure Audit Evidence:

- Reviewer / Agent: independent general subagent, fresh session `ses_f9a677b85ffeJzf9OP3uOSpdFH`（未参与实现）
- Audit Session: ses_f9a677b85ffeJzf9OP3uOSpdFH；完整报告 `_tmp/closure-audit-0830-2.md`
- Evidence:
  - Phase 2 Exit Criteria：PASS（validate 三类 + 分层不 execute + 入口族 0/1/2 + 选项名断言 + S1/S2 E2E 命令入口走通）
  - Phase 3 Exit Criteria：PASS（分派序与设计一致 + 五族实现/Message 不实现 + --connect 接线 + 调用计数接线验证 + 残留断言 + S1 dry-run E2E 合法通过/死池失败）
  - Phase 4 Exit Criteria：PASS（nop-credential-api 唯一新依赖 + 三 fail-closed 码 + transient provider + 瞬态解密副本 + run() 消费 + **main 代码零 testCredential() 调用** + nop-message-debezium git-clean + 序列化字节含引用不含明文断言）
  - Phase 5 Exit Criteria：PASS（双 owner doc 节 + 探测表 + INDEX 行 + STRM-052..055 + 裸 JVM 抽查修复 VFS 缺陷）
  - Closure Gates：逐条 PASS（见上节勾选注记；P-REQ-13/14 验收映射 + H-1..H-6 处置 + 红线验证 + audit 独立性）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow 检查结果：驱动器→各族探测调用链经 RecordingCheckable/startCount/begin+rollback count 运行时断言连通；audit 复跑 `./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS（10 个新测试类全绿）；`scan-hollow-implementations.mjs` 退出码 0（0 findings）
  - Deferred 项分类检查：唯一 Deferred = message 族深度探测（watch-only residual，Successor Required: no）——无 in-scope live defect 被降级；Non-Blocking Follow-ups 三项均为增强候选（条件触发）
  - Audit findings：F-1（Low，H-2 缺显式处置行）已当场收口（§4.1 补行）；F-2/F-3 信息级（测试命名 / closure 前占位符属预期），无功能缺陷

Follow-up:

- REST 化 dry-run/conf-validate（条件 = 出现远程提交工作流需求）
- 平台侧 `IMessageService` health-check 提案（nop-sys/nop-message 侧候选，Message 族探测增强前提）
- 平台侧 `ICredentialProvider.testCredential()` 真实实现（nop-credential 侧候选；实现后可作为 dry-run 增强接入）
- no remaining plan-owned work（本 plan scope 内无剩余工作）>
