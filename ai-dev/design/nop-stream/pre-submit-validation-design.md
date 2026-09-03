# 提交前校验设计：dry-run 连通性 + conf-validate 独立校验 + 凭据加密接入（item 20 / P-REQ-13/14）

> Status: active
> Created: 2026-09-03
> Revised: 2026-09-03
> Owner Plan: `ai-dev/plans/nop-stream-productization/2026-09-03-0830-2-pre-submit-validation-productization.md`
> 输入: item 10 connectors 审计 §2.1（钩子清单 H-1..H-6，自包含）+ D-GAP §2.1 P-REQ-13/14 go 裁定 + item 19 注册中心（`connector-design.md` §8，已落地）

本章为架构决策记录（选了什么/为什么/拒绝了什么）。代码事实源 = `nop-stream-flow` validate 包 + `nop-stream-core` connector/credentials 包 + 各连接器模块探测实现 + `nop-stream-runtime` 维护入口。

## 1. 定位与验收映射

把「启动后才暴露连接问题」推进到「提交前可独立校验」：

| P-REQ | 验收原文 | 落点 |
|---|---|---|
| P-REQ-13 | dry-run 入口类与测试存在；错误配置返回显式错误码而非静默通过 | D1 入口族 + D3 逐族探测 + `dry-run` 子命令 |
| P-REQ-14 | encrypt 工具/接口与 conf-validate 命令存在；缺失必填项输出含选项名的错误 | D5 encrypt 等价物映射 + D7 分层校验与错误契约（连接器模式字段级报错含参数名） |

## 2. D1 入口面裁定

**裁定：加入 `StreamMaintenanceMain` 入口族（nop-stream-runtime）作为新子命令**：`conf-validate`（层 1+2 校验，可选 `--connect` 接通层 3）与 `dry-run`（等价 `conf-validate --connect`，语义名即 P-REQ-13 验收所指 dry-run 入口）。校验核心逻辑落 `nop-stream-flow` validate 包（模型解析与 bean 解析的 owner 模块），runtime 维护入口仅做参数解析与调用——为此 runtime 新增对 flow 的编译依赖（其首个 core 之外的 stream 模块依赖）。

**REST 化不纳入**：dry-run/conf-validate 是本地提交前工具，消费方是提交脚本/CI；当前无远程提交工作流需求（roadmap 无对应场景）。已记录为 Non-Blocking Follow-up（条件 = 出现远程提交工作流需求），与 observability-design 的 REST 提交面（工厂引用形态）未来可自然衔接。

**拒绝的替代方案**：
- 独立 main（放 flow 模块）——制造第二个运维入口，违反 item 16 交付的入口收敛原则（状态维护操作共享一个入口、显式拒绝语义）；且校验命令本质是运维工具族成员。
- 校验逻辑整体放 runtime——模型解析/bean 解析属于 flow 的 owner 边界，runtime 会被迫复制模型知识；正确形态 = 核心 logic 在 flow、入口在 runtime。
- REST 端点（本期）——无需求证据；远程探测会把凭据解密拉进运维进程边界，复杂化明文边界（D4）。

## 3. D2 bean 解析来源与容器引导

**裁定：命令输入 = 作业定义 XDSL 路径 + bean 来源（三种可组合形态）**：

| 形态 | 输入 | 适用 |
|---|---|---|
| 程序化 resolver 注入 | 库级 API 直接传入 `BeanFunctionResolver`（如 `InMemoryBeanFunctionResolver`）；命令入口提供带 resolver 的重载（组合形态） | 嵌入/测试——**S1/S2 bean 的可解析形态**（其连接器 bean 仅存在于测试侧程序化注册，`ScenarioTestSupport.s1Resolver/s2Resolver`） |
| 显式容器装配 | 库级容器适配 resolver（包装任意 NopIoC `IBeanContainer`——宿主以 `BeanContainerBuilder` 显式装配后传入；item 19 注册中心装配同款模式） | 独立部署的校验（生产路径等价物，不进全局容器） |
| 全局容器 | CLI 无额外参数时回落 `GlobalBeanFunctionResolver`（生产 NopIoC 全局容器） | 应用内嵌提交 |

**CLI 面不提供 `beans=` 装配参数**（装配形态为库级 API）：runtime 的 nop-ioc 依赖为 test-scope 且以 init-cap 隔离（pom 注释明示：提升为编译域会令下游全量 init 测试套件（fraud-example S1/S2）激活 IocCoreInitializer，行为回归风险不可控）——为一个便捷 CLI 参数引入该风险被拒绝；程序化 + 全局两形态已覆盖全部验收路径（S1/S2 E2E = 程序化形态；生产 = 全局容器）。

S1/S2 可解析性：E2E 用例以形态 1（程序化 resolver，命令入口重载）验证 S1 拓扑完整走通——与欺诈示例测试的既有装配形态一致；形态 2 由 focused 用例以显式装配容器覆盖（nop-ioc 在测试域可用）；形态 3 由 CLI 缺省参数用例覆盖。三种形态共享同一 `BeanFunctionResolver` 契约，与既有 XDSL bean 解析世界零变更。

**与 item 19 注册中心世界的并存**（衔接 `connector-design.md` §8.3 两世界并列裁定）：XDSL 模式走 bean 解析世界不变；连接器模式（D6）经注册中心类型名解析。两世界不合并、不互通。

**拒绝的替代方案**：
- 只支持全局容器引导——S1/S2 bean 无法解析（其 bean 不在任何 beans.xml），端到端验收不可达。
- 为校验命令发明第四种 bean 声明面——重复建设，违反 D5（item 19 §8.5）维持的 XDSL 声明形态不变裁定。

## 4. D3 探测语义裁定（逐族，消费 H-1..H-6 优先级）

**驱动形态**：dry-run 驱动器逐 source/sink 元素解析端点实例，按「能力接口 → FLIP-27 Source 契约 → TwoPhaseCommitSinkFunction 契约 → 显式不支持」的次序分派探测；汇总结构化报告（逐族结果 + 不支持探测的显式标注项，绝不静默通过）。驱动器对具体连接器模块零依赖（探测实现归属各连接器模块/核心契约）。

### 4.1 逐族探测表（含副作用红线与「残留」定义）

| 连接器族 | 钩子 | 探测语义 | 副作用红线声明（残留定义） |
|---|---|---|---|
| file source（FLIP-27） | H-1 | `createEnumerator()` + `start()`（no-op assignment 上下文）——目录可达性探测（不存在/非目录 = 显式失败） | 无残留：只读目录扫描，不分配 split（no-op 投递）、不建文件 |
| file source reader 路径 | H-2 | **不适用（显式处置：被 H-1 取代）**——reader 侧 openSplit 探测属 task 期延迟发现，需 assignment 基建才能触达；dry-run 走 coordinator 侧枚举探测（H-1）即先行失败于目录缺失，同一错误面更早暴露 | —（不走 reader 路径） |
| jdbc-2pc sink | H-5 | `beginTransaction()` + `initializeLedgerTable()` + `rollback()` 组合——方言/querySpace 解析（配置面）+ 物理连接（幂等 DDL） | 台账**行**是残留、绝不产生（探测无 commit 写入）；台账**表**经幂等 DDL 创建 = 「作业正常运行本身需要的预期幂等对象」，红线显式豁免 |
| file 2PC sink | H-5 | `beginTransaction()` + `rollback()`（构造期已验证并创建输出目录） | 无残留：探测无数据写入、无 epoch 终文件；输出目录 = 构造期预期幂等对象（作业本身写入目标） |
| batch-loader source | H-3 | 实现探测能力接口：`loaderProvider.setup()` + 关闭 loader——provider 连通性（如 JDBC loader 连接） | 无残留：loader 为 AutoCloseable 时关闭；探测不开批数据消费游标 |
| debezium-cdc source | H-3（裁定 ⑤） | 实现探测能力接口：构造/参数级校验（ctor typed 校验 + connector name 强制）+ 凭据引用解密可达（D4）；**不启动引擎、不连库** | 无残留：不拉起 Debezium engine、无 offset 写入、无订阅 |
| message source/sink | —（裁定 ③） | **显式「不支持探测」报告项**：`IMessageService` 无平台 health-check API，可达性结论如实呈现为不可探测 | 探测红线排除 send 探针消息（可见副作用 = 红线违例） |
| batch-consumer sink | H-4 | 构造期已探测（构造即 `consumerProvider.setup()`，审计认定的天然探测点）；dry-run 对已构造实例不再重复探测——报告为构造级已验证（经探测能力接口呈现） | 无残留：setup 语义由构造路径既有行为承担 |
| 其余/第三方 | — | 未实现任何探测契约的端点 = 显式「不支持探测」报告项（非静默通过） | — |

### 4.2 具体裁定项

- **① H-5 JDBC 探测形态 = beginTransaction + 幂等 DDL + rollback 组合**。拒绝：仅 `SELECT 1`——无零 core 改动注入点（sink 不暴露连接面，公开探测方法 = core 契约新增）；仅 begin/rollback——不触物理连接，「连接不可达 fail-fast」（P-REQ-13 验收）不可达；仅幂等 DDL——缺方言/querySpace 配置面的先行验证路径（beginTransaction 先行失败可给出更精确的配置错误）。
- **② `ConnectivityCheckable` 类 additive 能力接口（core connector 包，仿 `DrainableSource` marker 先例）：采纳**。jdbc-2pc/file-2pc/batch-loader/debezium-cdc 四族实现；message 族与 batch-consumer 族不实现（诚实 skip/构造级语义）。未实现该接口的连接器在 dry-run 中 = 显式「不支持探测」报告项。
- **③ Message 族 = 显式 skip 报告**（对齐 plan Deferred 条目：`IMessageService` 无 health-check API；真实 send 探针违反副作用红线；不可达时的可达性结论如实呈现为不可判定）。
- **④ H-6（builder 级 validate 回调）= 不采纳**。conf-validate 命令在 builder 之外完成「解析后、execute 前」校验（命令本身就不 execute），flow builder 零变更；拒绝理由：builder 内回调会与命令面重复建设同一校验，且 builder 深审归 item 29 邻域，本期不扰动。
- **⑤ Debezium 族 = 构造/参数级 + 凭据解密可达，不真实连库**。拒绝 run()+cancel()（拉起真引擎——审计已否决）；拒绝真实 JDBC 连库探测（Debezium 连接语义属引擎启动期，无法零侵入触达）。
- **⑥ 探测副作用红线**：dry-run 不得产生**作业正常运行本身不会创建的对象**。幂等 DDL 建表、输出目录创建 = 预期幂等对象（显式豁免，逐族声明于 4.1 表）；2PC 台账行、epoch 终文件、订阅位点、offset 写入 = 残留（逐族断言不产生）。rollback/清理语义：jdbc（rollback 清缓冲，无写入）、file（rollback 清缓冲，无文件）、batch-loader（loader 关闭）。

## 5. D4 凭据接入机制裁定

**引用语法**：`credential:{credentialId}#{field}`（如 `credential:mysql-prod#password`）——值置于连接器配置字段处（Debezium 族必达点 = `DebeziumConfig.databasePassword`/`databaseUser`）；语法解析与解密经 nop-credential 唯一解密点 `ICredentialProvider`（`getCredentialData(credentialId, field)`）。

**密文驻留位置与解密时点机制（自洽性声明）**：**引用串持久驻留在 Serializable 配置对象内**（`DebeziumConfig` 序列化/checkpoint 恢复路径携带的是 `credential:` 引用而非明文——跨 JVM 恢复后仍可再解密，语义自洽）；**解密只发生在引擎侧瞬态路径**——CDC 连接器启动引擎前对配置做瞬态解密副本传入引擎构造，原配置对象与 checkpoint 序列化路径永不含明文。「构造前解密并写入明文配置」被显式拒绝（与 `testConfigSurvivesSerialization` 钉定的序列化语义矛盾——两条约束不可兼得，机制选择 = 引用驻留 + 瞬态解密）。

**明文边界**（对齐 `ICredentialProvider` Javadoc）：明文仅存在于引擎启动瞬态局部路径，不跨服务进程、不进日志/报告；dry-run 的凭据可达性结论只报告「解密成功/失败」，不输出明文。

**凭据注入**：CDC 连接器经构造参数注入 `ICredentialProvider`（transient 字段——每 JVM 装配时注入，函数实例的 Java 序列化路径不携带 provider）。**fail-closed**：配置含凭据引用而 provider 缺失、或凭据不存在/已软删除 = 显式 typed 错误（引用串、credentialId、字段名入参），绝不静默空串。

**凭据可达性结论语义**：解密成功 = 凭据可达。**不依赖 `testCredential()`**（W2 恒失败桩，依赖它会产生系统性假阴性）。

**模块边界裁定：不变更 `nop-message-debezium`**。`DebeziumConfig` 为普通 Serializable POJO，`credential:` 引用串作为字符串值驻留无需平台侧感知；解密点落在 `nop-stream-connector-debezium`（nop-stream 边界内）。引用语法/解密辅助落 `nop-stream-core`（core 新增 `nop-credential-api` api 层依赖——唯一新增依赖）。

**kms-vault 透传性**：stream 侧只消费 `ICredentialProvider` 接口；kms-vault 作为 nop-credential 后端实现透明（roadmap item 20「含 kms-vault」字样的消费方式 = 经平台唯一解密点间接可达，不在 stream 侧感知后端形态）。

**覆盖面**：Debezium 族必达（databasePassword/databaseUser）；JDBC 凭据位于 `IJdbcTemplate` 平台配置内（connector 不经手——平台既有加密/配置体系覆盖，stream 侧不重复建设，记录为边界事实）；file/message/batch 族无凭据字段。

**拒绝的替代方案**：
- 构造前解密写明文进配置——违反「明文不进序列化路径」（不可行组合，见上）。
- 作业配置整文件加密（SeaTunnel EncryptConfigServlet 形态）——破坏 XDef 校验、Delta 定制与配置 diff 可读性；Non-Goals 已定方向。
- XDSL 声明处引用 credentialId（新增声明面属性）——D5（item 19 §8.5）已裁定不引入类型化连接器声明面，凭据引用随 bean 配置（装配层）走，不开新声明面。
- stream 侧自建密文加解密（绕过平台唯一解密点）——违反凭据安全边界（cv1 密文解密收敛于 nop-credential 单点）。

## 6. D5 encrypt 等价物裁定（P-REQ-14 验收前半映射）

**encrypt 等价物 = nop-credential 既有凭据写入/加密平台面**：凭据经 nop-credential 服务面（credential CRUD 管理接口/页面）写入，落库即 `cv1:` 密文（`CredentialCipher` 加密，平台内唯一解密点 = `CredentialProviderImpl`）。stream 作业配置以 `credential:` 引用消费（D4 语法），明文不落作业配置。验收映射：「encrypt 工具/接口存在」= 平台凭据写入接口（live 存在）+ 本 plan 交付的引用语法/解密接入；**拒绝照搬 SeaTunnel `EncryptConfigServlet` 整文件加密形态**（依据见 D5 拒绝项与 plan Non-Goals）。

## 7. D6 与 item 19 的协调裁定

**item 19 已落地（本 plan 执行时状态）**：conf-validate 提供**连接器模式**（库级 API，catalog 由宿主显式装配传入——与注册中心「显式容器装配」边界一致）——经注册中心（`StreamConnectorRegistry`/`StreamConnectorCatalog`，`connector-*.beans.xml` 显式装配）按类型名解析，能力描述符的参数规格作为字段级校验规格（缺失必填/未知参数逐条报错含参数名——P-REQ-14 验收直接对应物），构造探测复用 catalog 既有 probe 语义 + D3 层 3 连通性探测。XDSL 模式连接器解析走 bean 世界（D2）不变。

**若注册中心未落地（跳序场景）**：连接器模式整体不可用（显式报告「连接器模式需 SPI 注册中心（item 19）」），XDSL 模式独立成立——本 plan 可独立成立的保证。

**拒绝的替代方案**：把类型名解析并入 bean 解析世界（名称空间语义不同——`connector-design.md` §8.5 已拒绝）；registry 未落地时静默降级为仅 bean 模式而不显式报告（静默跳过禁令）。

## 8. D7 conf-validate 分层校验范围与错误输出契约

**层 1 模型解析**：XDSL 经平台 DSL 解析器按 `stream.xdef` 字段级校验（未知元素/属性、类型错误、必填缺失——错误含元素/属性名）。

**层 2 连接器构造**：XDSL 模式 = 完整图构建（`StreamModelDslBuilder` 不 execute——bean 解析、类型匹配、FL-1 拒绝面、xpl 编译全部触达）；连接器模式 = 注册中心描述符参数规格字段级校验（缺失必填/未知参数/类型不符逐条含参数名）+ 工厂构造。

**层 3 可选连通性探测**：`--connect` 开关（`conf-validate --connect` ≡ `dry-run`）——D3 逐族探测 + 凭据可达性（D4）。

**错误输出契约**：结构化逐条 = 层号 + 元素/端点（XDSL 元素 id/bean 名或连接器类型名）+ 错误码 + 指引；选项名（参数名/属性名/bean 名）必含（P-REQ-14 验收对齐）。**exit code 契约**：0 = 通过（允许含显式 skip 项）；1 = 校验失败（任一 FAIL 条目）；2 = 用法错误（对齐 `StreamMaintenanceMain` 既有语义）。层 3 未接线期间 `--connect` = 显式「未实现/not yet available」错误（非静默忽略）。

**拒绝的替代方案**：单错误截断输出（首错即停）——P-REQ-14 要求逐条字段级报错，截断隐藏其余配置问题；exit code 无语义区分（校验失败与用法错误同码）——脚本化消费无法区分输入错误与配置错误。

## 9. 决策汇总表

| # | 裁定 | 一句话 |
|---|---|---|
| D1 | 入口族收敛 | StreamMaintenanceMain 子命令 conf-validate/dry-run；核心逻辑在 flow；REST 不纳入 |
| D2 | 三形态 bean 来源 | 程序化 resolver（S1/S2 形态）/ beans.xml 显式装配 / 全局容器回落 |
| D3 | 逐族探测 + 红线 | H-5 组合/H-1 枚举/能力接口四族实现/message 显式 skip/H-6 不采纳/幂等对象豁免 |
| D4 | 引用驻留 + 瞬态解密 | `credential:{id}#{field}` 驻留 Serializable 配置，解密只在引擎侧瞬态路径，fail-closed |
| D5 | encrypt 等价物 = nop-credential 平台面 | 凭据 CRUD + cv1 落库；拒绝整文件加密 |
| D6 | 两模式并存 | XDSL 模式（bean 世界）+ 连接器模式（注册中心世界）；未落地则显式不可用 |
| D7 | 三层校验 + 结构化错误 | 层 1 xdef / 层 2 构造 / 层 3 探测（--connect）；逐条含选项名；exit 0/1/2 |
