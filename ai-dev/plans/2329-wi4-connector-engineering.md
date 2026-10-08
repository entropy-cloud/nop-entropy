# 2329 WI4 连接器工程化增强（registry 成熟度分级 + OptionRule 式参数规则 + sample 数据采样档）

> Plan Status: completed
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI4（M1 P0 主线）+ 对比分析 P0-3（SeaTunnel OptionRule / 质量分级对标）+ WI0 裁定记录 §一 P0-3
> Related: plan 2327（WI0，completed）；productization item 19/20 交付的 registry 与 conf-validate 基建（`docs-for-ai/03-modules/nop-stream-connectors.md` SPI 节）；WI3（新 Kafka 连接器将消费本 WI 的声明面）

## Purpose

把连接器 SPI registry 从「能力矩阵 + 参数名清单」升级为「成熟度分级 + 参数值级规则 + 数据采样」三件工程化面：扩生态的新连接器作者获得声明式参数校验与 sample 工具，配置错误在提交前 fail-fast，敏感值不泄漏。

**roadmap WI4 行「校验规则进 XDSL 配置校验」的范围解释（裁定落地）**：XDSL 类型化连接器声明维持 connectors.md SPI 节既有裁定不变（暂未引入），本 WI 的「配置校验」落在 conf-validate 的 **connector 校验模式**（`StreamConfValidator.validateConnector(direction, typeName, params, …)`，程序化 + 新 `sample` CLI 共同消费）——不扩 XDSL 声明面。

## Current Baseline

- `ConnectorCapabilityDescriptor`（core registry 包）已含：typeName/aliases/direction/componentClass/一致性/并行度/恢复语义 + `List<ConnectorParamDescriptor> params`（name/kind/required/description 四字段，item 19 交付）。
- 8 内建工厂静态 DESCRIPTOR 声明能力与参数；**内建参数全量清单（20 项，live 核实）**：file source `directoryPath`(STRING,required)；file sink `outputDir`(STRING,required)；jdbc-2pc 10 项（jdbcTemplate OBJECT required / tableName STRING required / columns STRING_LIST / recordMapper OBJECT required / dmlMode STRING optional / keyColumns STRING_LIST / opMapper OBJECT / deleteKeyMapper OBJECT / querySpace STRING / ledgerTableName STRING）；debezium-cdc 2 项（config OBJECT required / credentialProvider OBJECT）；batch-loader 1 项（loaderProvider OBJECT required）；batch-consumer 若干 OBJECT/构造参数；message 2 端点 topic 类参数。**口令类参数在内建描述符层为 0**（jdbc 凭据走 jdbcTemplate bean、debezium 凭据在 config OBJECT 内部）——sensitive 的真实落点是 SPI 能力交付 + 合成描述符钉定 + WI3 Kafka 参数首次真实消费。
- `StreamConfValidator.validateParamsFieldLevel`（flow validate 包）现只做两类校验：未知参数名 + required 缺失；**仅被 connector 校验模式调用**（`validateConnector`，程序化入口）；CLI `conf-validate file=<path>` 走 stream 模式（`validateStream`，端点是 bean，与描述符无反查关联）。两模式边界是既有架构（Item 20 交付时裁定），本 WI 不做 bean→描述符反查。
- `StreamConnectivityProber`（core）驱动 dry-run 层 3 连通性探测，分派序 = ConnectivityCheckable → FLIP-27 Source → 2PC 基契约 → 显式 SKIP；红线（connectors.md 探测表）：file 只读扫描不分配 split（H-1）、batch-loader 不消费批数据（H-3）、message 显式 SKIP、debezium 不拉引擎不连库。
- `StreamMaintenanceMain` 承载 `conf-validate file=<path> [--connect]` / `dry-run file=<path>` 子命令（exit 0/1/2 契约）。
- `ValidationIssue.Severity` 现为 {FAIL, SKIP} 两值；`StreamConfValidationReport.render()` 以 `issues.size()-failCount` 推 skipCount。
- `TestStreamConnectorRegistryDiscovery`（runtime）是能力矩阵的代码级单一事实源（含 listing 断言）；`StreamConnectorCatalog.renderListing` 输出注册清单。
- `FileSource`（FLIP-27）记录 = String 文本行；`FileSplitEnumerator` 用 `Files.walk`（跨文件顺序无契约），一文件一 split；split 分配经 `SplitEnumeratorContext` 注入的投递服务。

## Goals

- registry 成熟度分级：`ConnectorMaturity`（EXPERIMENTAL/PRODUCTION）进描述符，**判据 = 语义强度**：交付/恢复语义由平台引擎内建兑现（checkpoint 光标/offset/2PC）= PRODUCTION；recovery=NONE 或语义由外部后端承载 = EXPERIMENTAL。默认 EXPERIMENTAL，PRODUCTION 须显式声明。listing 展示（追加行尾，不动既有断言），能力矩阵同步加列。
- 参数值级规则：`ConnectorParamDescriptor` 增 `sensitive`/`allowedValues`/`pattern` 三规则字段（SPI 能力交付）；内建 20 参数**如实**落规则：dmlMode = allowedValues[insert,upsert,upsert_delete]、INT 型批量参数 = kind 校验练习对象、sensitive/pattern 内建落点为 0（由合成描述符测试钉定机制，WI3 消费）。conf-validate connector 模式增值级校验（kind 类型匹配 / allowedValues / pattern，typed 错误码），sensitive 参数值在任何 issue 文本/异常 param 中脱敏。
- sample 数据采样档（connector 模式 CLI）：`sample type=<typeName> param=value… [limit=<n>]` → registry 解析工厂 → 构造 source → `SampleableSource` 分派；file source 真实采样（只读旁路，不分配 split）；SINK 类型名 = connector 校验模式 typed direction-mismatch FAIL（exit 1，不进采样分派）；无采样契约的 SOURCE 族端点（message/debezium/batch-loader[H-3]）显式 SKIP + 原因；采样输出无新增泄漏面（file 行原样输出）。
- EXPERIMENTAL warning：connector 校验模式对 EXPERIMENTAL 端点出非阻断 warning（severity 语义修正后不计入 skip 数）。
- owner docs：connectors.md 能力矩阵加成熟度列 + 采样节（副作用预算）；nop-stream.md 命令族补 `sample` 用法（connector 模式承诺）。

## Non-Goals

- 不新建第二注册面；不做 bean→描述符反查（stream 模式 CLI 的值级校验/脱敏/warning 不可达是既有模式边界，本 WI 显式声明而非弥合——弥合属类型化 XDSL 声明裁定，暂未引入）。
- 不做 SeaTunnel connector-dev toolkit 全集（模板/脚手架不引入）。
- 不改既有 8 工厂的能力矩阵语义（consistency/parallelism/recovery 零变更——discovery 既有断言预期值不变，maturity 列追加行尾）。
- sample 档不写入/不提交任何数据（只读采样；sink 端点不可采样 = connector 校验模式 typed direction-mismatch FAIL exit 1，不进采样分派）。

## Scope

### In Scope

- `nop-stream-core`：`ConnectorMaturity` 枚举、描述符增 maturity、`ConnectorParamDescriptor` 增 sensitive/allowedValues/pattern、`SampleableSource` + `StreamDataSampler`。
- 四个连接器模块：8 工厂声明 maturity + 参数规则；`FileSource` 实现采样。
- `nop-stream-flow`：`StreamConfValidator` 值级校验 + sensitive 脱敏 + EXPERIMENTAL warning + `ValidationIssue.Severity` 增 WARNING 与 render 计数修正；新错误码（NopStreamErrors 既有 define 模式追加）。
- `nop-stream-runtime`：`StreamMaintenanceMain` 增 `sample` 子命令（connector 模式：`type=` + 参数键值 + limit）；`TestStreamConnectorRegistryDiscovery` 扩展 maturity 钉定。
- docs：`docs-for-ai/03-modules/nop-stream-connectors.md`、`docs-for-ai/03-modules/nop-stream.md`。
- 新测试：值级校验三路径（类型/allowedValues/pattern）、脱敏（合成 sensitive 描述符）、采样（file 真实采样 + SKIP 族封闭断言）、成熟度发现钉定、`sample` 命令接线。

### Out Of Scope

- Kafka 连接器（WI3 承载，将按本 WI 声明面注册）。
- XDSL stream.xdef 声明面扩展（维持既有裁定）。
- stream 模式 CLI 的描述符反查（见 Non-Goals）。
- 采样档对 sink 的写路径验证（属 dry-run 连通性既有 H-5/H-4 语义）。

## Execution Plan

### Phase 1 - registry 分级与参数规则（core + 四模块声明 + listing）

Status: completed
Targets: `nop-stream-core/connector/registry`、四连接器模块工厂、`docs-for-ai/03-modules/nop-stream-connectors.md` 矩阵列

- Item Types: `Feature`

- [x] `ConnectorMaturity` 枚举 + `ConnectorCapabilityDescriptor` builder 增 maturity 字段（默认 EXPERIMENTAL；javadoc 写明语义强度判据）
- [x] 8 工厂声明 maturity：file source/sink、jdbc-2pc、debezium-cdc、batch-loader、batch-consumer = PRODUCTION（checkpoint/offset/2PC 内建兑现）；message source/sink = EXPERIMENTAL（recovery=NONE，语义由外部后端承载）
- [x] `ConnectorParamDescriptor` 增 `sensitive`/`allowedValues`/`pattern`（新静态工厂/with 方法，既有 4 参构造与 required()/optional() 保持编译兼容）；内建 20 参数如实落规则（dmlMode allowedValues；sensitive/pattern 内建落点 0，机制由合成描述符测试钉定）
- [x] `renderListing` 追加 maturity 列（行尾）+ sensitive 标记（参数名旁 `[sensitive]`，listing 无值不涉掩码）
- [x] `TestStreamConnectorRegistryDiscovery` 扩展：8 工厂 maturity 逐项钉定（既有断言预期值不变）
- [x] `docs-for-ai/03-modules/nop-stream-connectors.md` 能力矩阵加「成熟度」列 + SPI 节补分级判据

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-connector,nop-stream/nop-stream-connector-jdbc,nop-stream/nop-stream-connector-debezium,nop-stream/nop-stream-connector-batch,nop-stream/nop-stream-runtime -am` 全绿
- [x] 成熟度默认值语义有钉子（未声明 = EXPERIMENTAL）；8 工厂分配与语义强度判据自洽（discovery 断言 = 判据可推导）
- [x] 既有能力矩阵语义零翻转（discovery 既有断言预期值零修改）
- [x] **接线验证**：maturity 经 `StreamConnectorCatalog` 枚举真实可达（discovery 即消费面）
- [x] **无静默跳过**：规则字段 null/空 = 显式「无规则不校验」语义（javadoc + 校验器分支），非静默吞
- [x] `docs-for-ai/03-modules/nop-stream-connectors.md` 已更新（矩阵列 + 判据）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 值级校验、脱敏、EXPERIMENTAL warning 与 sample 采样档

Status: completed
Targets: `nop-stream-flow/validate`、`nop-stream-core` sampler、`FileSource` 采样、`nop-stream-runtime` CLI、docs

- Item Types: `Feature`

- [x] `ValidationIssue.Severity` 增 WARNING + `StreamConfValidationReport.render()` 计数修正（warning 不计入 skip 数）
- [x] `StreamConfValidator.validateParamsFieldLevel` 增值级校验：kind 类型匹配（INT 可解析整数）、allowedValues 包含性、pattern 匹配（typed 错误码，照 NopStreamErrors define 模式）；sensitive 参数值在 issue 文本/异常 param 中以 `***` 呈现；EXPERIMENTAL 端点非阻断 warning
- [x] core 增 `SampleableSource`（契约：`List<Object> sample(int limit)`，记录类型由端点决定——file = String 行；批量上限 = limit 全局上限，跨 split 顺序 = 文件系统发现序、不做排序承诺；只读零副作用）+ `StreamDataSampler` 驱动（照 ConnectivityProber 分派模式：SampleableSource → 真实记录，否则显式 SKIP + 原因。closure 裁定（audit 首轮发现 2）：`sample` 仅接受 SOURCE 类型——SINK 类型名在校验层即 typed direction-mismatch FAIL（exit 1），不进采样分派；SKIP 封闭面 = 3 个未实现采样契约的内建 SOURCE（message×1 家族两端点中 source 侧/debezium-cdc/batch-loader））
- [x] `FileSource` 实现 `SampleableSource`：旁路只读实现（不经 enumerator 分配 split，延续 H-1「只读扫描，不分配 split」副作用预算——采样节红线同条文）
- [x] `StreamMaintenanceMain` 增 `sample type=<typeName> param=value… [limit=<n>]` 子命令（connector 模式：registry 解析 → conf 字段级校验（复用 validateConnector 的参数规则层）→ 构造 source → sampler 分派 → 输出采样行/SKIP 原因；sensitive 参数值不回显；exit 0/1/2 契约对齐；未知 type = 既有 typed 错误含已注册清单）
- [x] 新测试：值级校验三路径 + 脱敏（合成 sensitive 描述符：issue 文本断言 `***` 且原始值不出现）+ sampler（file 真实文件 → limit 行断言；SKIP 断言 = core 泛型 not-implementing 形状 + runtime message 族真实 SKIP + sink 类型 typed FAIL exit 1，与裁定口径一致）+ EXPERIMENTAL warning 断言 + `sample` 命令接线（CLI → registry → factory → 真实采样输出）
- [x] docs：`nop-stream.md` 命令族补 `sample` 用法（connector 模式承诺 + stream 模式边界声明）；`docs-for-ai/03-modules/nop-stream-connectors.md` 补采样节（副作用预算 + 封闭分派表）

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-flow,nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` 全绿
- [x] **端到端验证**（guide #22）：从 `sample type=file directoryPath=<dir> limit=<n>` CLI 入口到 file source 真实数据行输出的完整路径已验证
- [x] **接线验证**（guide #23）：`StreamDataSampler` 被运行链路（sample 命令）真实调用；值级校验被 connector 校验模式真实消费（断言 issue 错误码）
- [x] **无静默跳过**：不支持采样的 SOURCE 族端点显式 SKIP 并带原因（封闭分派断言：core 泛型 not-implementing 形状 + runtime message 族真实 SKIP + sink 类型 typed FAIL exit 1 断言），不返回空列表冒充成功（执行注记：原措辞「7 端点含 sink 全族 SKIP」与实现语义不符——closure audit 首轮发现 2，按上述裁定修正措辞，sink 的不可采样面由 direction-mismatch FAIL 承载）
- [x] 泄漏面钉死：sensitive 值在 issue 文本不出现（合成描述符断言）；采样输出无新增泄漏面（file 行原样输出断言）
- [x] owner docs 已更新（nop-stream.md + connectors.md）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - closure audit 与提交

Status: completed
Targets: roadmap WI4、plan 2329

- Item Types: `Proof`

- [x] 独立子 agent closure audit（fresh session）：Phase 1/2 exit criteria + Closure Gates + 文档一致性
- [x] roadmap WI4 checkbox 勾选
- [x] git commit
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No owner-doc update required（docs-for-ai 已在 Phase 1/2 更新）

Exit Criteria:

- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI4 已勾选、提交完成

## Closure Gates

- [x] 成熟度分级落 registry 描述符 + 8 工厂显式声明（语义强度判据自洽）+ listing/矩阵/发现测试三面一致
- [x] 参数规则（sensitive/allowedValues/pattern）落描述符 + connector 校验模式值级校验生效 + 脱敏钉死 + 内建 20 参数规则如实
- [x] sample 采样档 CLI 可用：file 真实采样（旁路只读）、3 个 SOURCE 族端点显式 SKIP 封闭 + sink 类型 typed FAIL exit 1、sensitive 参数不回显
- [x] 既有 8 工厂能力矩阵语义零翻转
- [x] 不存在被静默降级的 in-scope 项
- [x] `./mvnw test -pl nop-stream/nop-stream-flow,nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-connector -am` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-import-order.mjs` 通过（受影响模块无新增违规）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` 退出码 0（flow/runtime 由 -am 回归面 + audit 抽查覆盖）
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

（无——如执行中发现新的相邻问题，按 plan guide 移入此处并写明 Why Not Blocking Closure）

## Closure

Status Note: WI4 完成：成熟度分级（语义强度判据，8 内建工厂声明 + listing + discovery 钉定）、参数值级规则（sensitive/allowedValues/pattern + 三路径校验 + 全出口脱敏 + EXPERIMENTAL 非阻断 warning）、sample 采样档（SampleableSource/StreamDataSampler 封闭分派 + FileSource 只读旁路 + CLI 端到端真实采样冒烟）；closure audit 三轮（R1 REJECTED 2 Major + 3 Minor → 裁定方案 (b) sink=typed FAIL / SKIP 封闭面=3 SOURCE 端点 → R2 文本收窄 4 处 → R3 APPROVED）。
Completed: 2026-10-07

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_99f09228-cefb-4656-8ae2-38e6b506c928，三轮审计）
- Audit Session: agent_99f09228-cefb-4656-8ae2-38e6b506c928（R1 REJECTED：Plan Status 滞后 + sink SKIP 承诺失实 + 3 Minor；R2 范围收窄 4 处文本；R3 轻量复核 APPROVED）
- Evidence:
  - Phase 1 PASS：枚举/描述符/8 工厂声明与判据自洽、discovery 既有断言零修改、connectors.md 一致
  - Phase 2 PASS：三路径值级校验 + 脱敏（原始值不出现断言）+ warning 计数、封闭分派（core 形状 + runtime message SKIP + sink FAIL exit 1 断言 6/6）、FileSource 只读旁路、CLI 真实冒烟（daily log）
  - Anti-Hollow PASS：调用链 main → sample 命令 → validateConnector → StreamDataSampler → FileSource.sample 全连通；无空实现
  - `check-doc-links.mjs --strict` exit 0；`scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` exit 0；`check-import-order.mjs` 新文件 0 违规（剩余 2 条为 FileSourceReader HEAD 既有）
  - 测试：TestStreamConfValidator 16/16、TestStreamDataSampler 3/3、TestFileSourceSample 5/5、TestStreamSampleCommand 6/6（审计独立重跑）；全量 flow/core/runtime/connector -am BUILD SUCCESS
  - Deferred 项分类检查：无 in-scope live defect 被降级；非阻塞备忘（成熟度段落形式、hasValueRules() 留 WI3 消费）均有归属

Follow-up:

- `hasValueRules()` SPI 方法暂无内建调用点——WI3 Kafka 连接器参数面为首个预期消费者（watch-only）
- connectors.md 成熟度以矩阵后段落落地（信息等价）；WI3 注册新连接器或 owner 偏好时可再收为表格列（out-of-scope improvement）
