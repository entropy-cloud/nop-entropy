# W10-impl 凭证库外部 KMS/HSM 集成（主密钥材料交付模式）

> Plan Status: active
> Mission: nop-credential-mfa
> Work Item: W10-impl
> Last Reviewed: 2026-08-16
> Source: `ai-dev/design/nop-credential/02-phase2-design.md` §四（全部）；roadmap `ai-dev/backlog/nop-credential-mfa-roadmap.md` W10-impl
> Related: W1（`2026-08-12-0615-1`，master-keys 本地实现 + "External KMS / Vault SPI" follow-up → 本 plan 兑现）；W9-impl（`2026-08-14-2342-1`，先行执行）；W11-impl（`2026-08-14-2342-3`，后行，共改 `NopCredentialBizModel`）

## Purpose

按 W9-design §四落地外部 KMS/HSM 主密钥来源集成：`ICredentialKeyProvider` SPI 零变更前提下新增 KMS 实现模块（材料交付模式、启动期交付、运行期零托管端调用）、同名 bean 覆盖装配 + 配置门控、启动期 fail-closed 校验组（含对设计 §4.3 "模块缺失即启动失败"前提的平台机制修正）、迁移残余列表，并交付 reencryptAll 分页完备性修复（§4.3 登记的一期已知限制）。

## Current Baseline

（2026-08-14 live repo 核对，经独立 review 复核）

- `ICredentialKeyProvider`（`nop-credential-api/.../crypto/ICredentialKeyProvider.java`）：`getActiveKeyId`/`getKey`/`getKeyIds` 三方法。
- `DefaultCredentialKeyProvider`（`nop-credential-service/.../crypto/DefaultCredentialKeyProvider.java`）：`@cfg:nop.credential.master-keys`（keyId:passphrase 列表）+ `@cfg:nop.credential.active-key-id`；`@PostConstruct` 启动期加载构造 `AESTextCipher`，非法条目/缺 active 抛 `NopException`（fail-closed）。
- 装配：`credential-defaults.beans.xml` —— `nopCredentialKeyProvider` bean 带 `ioc:default="true"`。
- **NopIoC `ioc:default` 语义（draft review 核定，本 plan 关键前提）**：default bean id 被改写为 `$DEFAULT$<id>` 并自动附加 `missing-bean(<id>)` 条件（`BeanContainerBuilder.normalizeDefaultBean:260-277`）——它是**条件兜底**，与任何配置项的值无关。因此"配置指向 KMS 但模块未部署 → 启动失败"**不会自然发生**：default bean 会照常注册并回退 local（正是设计要防的假安全）。修正机制见 Phase 1（`DefaultCredentialKeyProvider` 增加 key-provider 守卫）。
- **beans 装载机制（draft review 核定）**：`AppBeanContainerLoader` 按模块自动装载 `/<moduleId>/beans/` 下 `app.beans.xml` / `app-*.beans.xml`（`isAppBeans:275-284`）；`credential-defaults.beans.xml`（非 app- 前缀）生效仅因 `app-service.beans.xml` import 它。同名覆盖 + 门控先例：nop-auth-sso 的 `sso-defaults.beans.xml:15-21`（gated `nopLoginService` 覆盖 default `LoginServiceImpl`，经 autoconfig 注册装载）。
- `ioc:condition` 读配置先例：`auth-service.beans.xml:42` `<if-property .../>`（W8，经 `AppConfig.var` 精确匹配）。
- `NopCredentialBizModel.reencryptAll`（live:289-322）：`query.setLimit(1000)` 单页查询无翻页循环（live:295-296 一期已知限制）。
- KMS 相关一切（key-provider 配置项、KMS 模块、门控装配、迁移残余列表、完备性验证）**均不存在**。
- 根 `pom.xml` **已注册** `nop-credential` 聚合模块（live:577）；新子模块只需注册进 `nop-credential/pom.xml <modules>`。
- scan-hollow 基线：live 执行即有 1 条 pre-existing high（`NopCredentialBizModel.java:105` 标准 save 禁用抛 `UnsupportedOperationException`，一期有意模式）——收口判据须区分 NEW 发现。
- stub HTTP server 测试先例：JDK `com.sun.net.httpserver.HttpServer`（nop-http-client-jdk/-apache 测试）；WARN 日志断言先例：logback `ListAppender`。

## Goals

- `nop.credential.key-provider` 配置项：`local`（缺省）/ `vault`；local 路径行为与一期完全一致（`credential-defaults.beans.xml` 装配不动）。
- KMS 参照实现模块（独立可选 Maven 模块）：启动期从托管端读取全部配置 keyId 材料 → 构造单钥加密器集合；同名 bean `nopCredentialKeyProvider` 覆盖 + 配置门控；**配置指向 KMS 而模块未部署时启动失败**（经 default-bean 守卫达成，修正设计 §4.3 前提）。
- 启动期 fail-closed 校验组：托管端不可达/认证失败/材料缺失/来源混合（master-keys 残留）/迁移残余含 active key → 启动拒绝；无本地降级。
- 迁移残余列表（`keyId:passphrase` 只解不加密、禁含 active key、WARN 审计）。
- reencryptAll 分页完备性修复：确定性排序 + 翻页循环处理全部未删除凭证。

## Non-Goals

- KMS proxy 模式（运算不出托管端：Vault Transit/非导出 CMK）——设计 §4.4 拒绝，§七#5 deferred。
- KMS 厂商间迁移编排（§七#6）；密钥热重载（§4.4 拒绝）；多厂商全家桶（§4.4 拒绝，仅一个参照实现）。
- `ICredentialKeyProvider` 接口任何变更、`cv1:` 密文格式变更、`credential-defaults.beans.xml` 缺省装配语义变更（local 行为零变化）。
- 归属/RBAC/OAuth（W11-impl / W9-impl）。
- `reencryptAll` 进度上报（W3 已裁定 optimization candidate；本 plan 只修分页完备性）。
- 密文 keyId 分布查询管理面（完备性交付物二选一，本 plan 裁定走分页修复路径，见 Phase 3 Decision）。

## Scope

### In Scope

- 新 Maven 模块 `nop-credential-kms-vault`（+ `nop-credential/pom.xml <modules>` 注册 + BOM）。
- Vault KV v2 参照实现（**经 `IHttpClient` HTTP API 读取，不引 Vault SDK**——Phase 1 Decision 裁定，对设计 §4.1 结论 5 "含厂商 SDK 依赖"表述的显式细化偏离，Phase 4 回写设计标注）。
- KMS 实现配置命名空间（地址/token/`keyId→secret 路径`映射/active key/迁移残余列表）。
- 同名 bean 覆盖 + `ioc:condition` 配置门控装配（新模块 beans 文件按自动装载规则命名/放置，Phase 1 裁定）。
- `DefaultCredentialKeyProvider` key-provider 守卫（修正 NopIoC default-bean 兜底导致模块缺失时静默回退 local 的漏洞）。
- 启动期 fail-closed 校验组 + 迁移残余 WARN 审计。
- `reencryptAll` 分页完备性修复（orderBy credentialId + 游标/offset 翻页，页大小可注入便于测试）。
- `docs-for-ai/03-modules/nop-credential.md` KMS 集成章节 + 设计文档 impl 裁定标注。

### Out Of Scope

- 其他厂商（AWS Secrets Manager/云 KMS/HSM）实现（使用方按 SPI 自行扩展，§4.4）。
- KMS 迁移关窗自动化编排。

## Execution Plan

### Phase 1 - 模块骨架 + KMS 参照实现 + 装配门控 + 缺失守卫

Status: planned
Targets: `nop-credential/nop-credential-kms-vault/`（新模块）、`nop-credential/pom.xml`、`nop-credential-bom`（如适用）、`DefaultCredentialKeyProvider.java`、KMS 模块 beans 文件、`io/nop/credential/crypto/CredentialErrors`

- Item Types: `Decision | Fix | Proof`

- [ ] **Decision（参照实现选型）**：HashiCorp Vault KV v2 引擎，经 `IHttpClient` 直接调用 HTTP API（GET `/v1/{mount}/data/{path}` + `X-Vault-Token` 头），**不引入 Vault Java SDK**。理由：材料交付模式只需一次性 GET，SDK 依赖树污染违背"独立可选模块轻量"初衷；协议面极小，HTTP 实现维护成本低于 SDK 版本耦合；`nop-http-api` 为平台自带 api 模块（W9-impl OAuth 同样复用）。这是对设计 §4.1 结论 5 "含厂商 SDK 依赖"的显式细化偏离（操作约束——独立可选模块 + 主模块零厂商依赖——被更好满足），Phase 4 回写设计标注；若审查否决，回写 SDK 方案理由后改用 SDK。
- [ ] **Decision（beans 装载路径）**：新模块 beans 文件放置于共享模块命名空间 `_vfs/nop/credential/beans/app-kms-vault.beans.xml`（app- 前缀 → `AppBeanContainerLoader` 自动装载，不依赖 import/autoconfig）；若实施发现 VFS 跨 jar 合并同目录的模块归属判定问题，备选 = 独立模块目录 `_vfs/nop/credential-kms-vault/beans/app.beans.xml` + `_module` 标记（`ModuleManager.discover` 发现），实施时定案并记录。两条路径都必须满足"文件被自动装载"（Proof 断言）。
- [ ] **Fix**：新 Maven 模块骨架（参照 nop-credential 既有子模块 pom 惯例；模块自身依赖 `nop-credential-api` + `nop-http-api`）+ `nop-credential/pom.xml <modules>` 注册 + BOM 构件登记。
- [ ] **Fix**：`VaultCredentialKeyProvider implements ICredentialKeyProvider`——配置：地址/token/`keyId→secret 路径`映射/active keyId/迁移残余列表（`nop.credential.vault.*` 命名空间，实施时定案）；`@PostConstruct` 启动期批量读取材料构造 `AESTextCipher` 集合；材料仅存进程内存；实现 bean 定义于 KMS 模块 beans 文件：**同名 bean id `nopCredentialKeyProvider`**（覆盖 `$DEFAULT$` 兜底）+ `ioc:condition` `<if-property name="nop.credential.key-provider" value="vault"/>` 门控；不新增第二个 `ICredentialKeyProvider` 独立 bean id。
- [ ] **Fix（缺失守卫，修正设计前提）**：`DefaultCredentialKeyProvider` 增加 `@cfg:nop.credential.key-provider|local` 注入与 `@PostConstruct` 守卫——取值非 `local` 时直接抛错（启动失败）。机制说明：模块已部署且门控命中时，default bean 被 `missing-bean` 条件排除、守卫不运行；模块缺失时 default bean 兜底注册、守卫触发 → "配置了 KMS 实际跑 local"的假安全被结构性堵死。`key-provider=local`/未配置时守卫零触发（一期行为不变）。**master-keys 残留校验（KMS 激活 + master-keys 非空 → 拒绝）同置于该守卫**（非 local 且 master-keys 非空 → 抛错），使模块缺失场景下该检查也可达。
- [ ] **Proof**：装配与守卫测试——`key-provider` 未配置/local：仅 `DefaultCredentialKeyProvider` 生效且行为与一期一致；`key-provider=vault` + 模块在 classpath：Vault bean 覆盖（断言注入类型/密钥来源）；`key-provider=vault` + **模块不在 classpath**：容器初始化抛错（守卫触发，fail-closed 成立）；`key-provider=vault` + master-keys 非空：抛错。beans 文件被自动装载（非仅存在于 jar 内）。

Exit Criteria:

- [ ] `./mvnw clean install -pl nop-credential -am -T 1C` 绿（含新模块）。
- [ ] 未引入 KMS 模块的部署零感知（`nop-credential-service` 依赖树无新第三方依赖，`mvn dependency:tree` 验证；`DefaultCredentialKeyProvider` 守卫在 local 下零触发）。
- [ ] **接线验证**：`key-provider=vault` 时 `CredentialCipher` 实际注入的是 Vault 实现（测试断言 bean 类型/密钥来源）。
- [ ] **新功能测试**：列出装配/门控/守卫测试类名。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 启动期 fail-closed 校验组 + 迁移残余列表

Status: planned
Targets: `VaultCredentialKeyProvider`、`io/nop/credential/crypto/CredentialErrors`

- Item Types: `Fix | Proof`

- [ ] **Fix**：启动校验组（bean 初始化抛错 = 应用拒绝启动）——托管端不可达/认证失败（401/403）/配置的 keyId 在托管端缺失（404）/材料非法（空/非字符串）→ 抛错；迁移残余列表包含 active keyId → 抛错（master-keys 残留检查已在 Phase 1 守卫落地，此处不重复）。
- [ ] **Fix**：迁移残余列表语义——残余 key 只并入解密 keyMap（不进入 active 候选）、每次启动输出 WARN（列出残余 keyId 提示收尾）；残余 key 材料形态与一期 passphrase 同构。
- [ ] **Proof**：fail-closed 单测（stub HTTP server 模拟不可达/401/404/空材料各分支 + 残余含 active key 拒绝 + master-keys 残留拒绝）；材料读取 happy path（stub Vault KV 响应 → 加解密 round-trip 与一期 `CredentialCipher` 语义一致）。工程注记：stub 用固定端口 + 静态初始化保证容器构建期可达（NopAutoTest 配置文件静态）；测试对 `IHttpClient` mock 或 test 依赖 `nop-http-client-jdk`；WARN 断言用 logback `ListAppender`（新模块补 logback-classic test 依赖）。
- [ ] **Proof**：无本地降级断言——全部故障分支均为启动失败，不存在"回退 DefaultCredentialKeyProvider/跳过该 key 继续启动"路径（测试覆盖 + 代码审查确认无降级分支）。

Exit Criteria:

- [ ] 每条 fail-closed 细则（§4.3 列表 + Phase 1 守卫）有可指认测试。
- [ ] **无静默跳过**：任何托管端/配置异常显式抛错，无 catch-continue。
- [ ] WARN 审计输出可观测（测试捕获日志断言含残余 keyId）。
- [ ] **新功能测试**：列出测试类与用例名。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - reencryptAll 分页完备性修复

Status: planned
Targets: `NopCredentialBizModel.reencryptAll`

- Item Types: `Decision | Fix | Proof`

- [ ] **Decision**：完备性交付物二选一裁定——走**分页完备性修复**（确定性排序 + 翻页循环），不建密文 keyId 分布查询管理面。理由：修复直接消除单次执行不全覆盖的根因，对所有部署（含未上 KMS 的 local 轮换场景）生效；分布查询是观测辅助，关窗判定最终仍依赖重加密执行完毕，二阶价值低。
- [ ] **Fix**：`reencryptAll` 改翻页循环——查询强制 `orderBy(credentialId)`（无确定性排序的 offset 分页会漏行/重行，恰是完备性缺陷的变形）；游标（`findPageByQuery` cursor 能力，`OrmEntityDao.findPageAndReturnCursor` 先例）或 offset 翻页直至取尽，二选一实施时定案；页大小改为可注入（便于测试缩小模拟，缺省保持 1000）；保持逐条提交可重跑/幂等跳过（active keyId 相同跳过）语义不变。
- [ ] **Proof**：单测——构造超页大小多条凭证（注入缩小页大小），断言单次执行全部处理；幂等重跑第二次返回 0。
- [ ] **Proof**：一期行为回归——既有 reencryptAll 测试全绿（轮换/幂等/失败 fail-closed 语义不变）。

Exit Criteria:

- [ ] 超批量场景单次执行全覆盖有测试证据（含确定性排序断言）。
- [ ] 幂等与逐条提交语义保持（既有测试 + 新增测试）。
- [ ] **新功能测试**：列出测试类与用例名。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 文档同步 + 收口验证

Status: planned
Targets: `docs-for-ai/03-modules/nop-credential.md`、`ai-dev/design/nop-credential/02-phase2-design.md`（追加 impl 裁定标注）、`ai-dev/backlog/nop-credential-mfa-roadmap.md`

- Item Types: `Follow-up | Proof`

- [ ] **Follow-up**：`docs-for-ai/03-modules/nop-credential.md` 补 KMS 章节：配置项表（key-provider/vault 命名空间/迁移残余）、装配模型（同名 bean 覆盖 + 门控 + **default-bean 守卫**）、fail-closed 语义、local→Vault 迁移路径五步（切换解耦/开窗/关窗前置/关窗后/反向回退）、keyId→材料不变式与配置禁区（厂商同名原地轮换）。
- [ ] **Follow-up**：设计文档标注两处——§4.1 结论 5：参照实现经 IHttpClient 零 SDK 达成（细化偏离记录）；§4.3：修正"无 bean 满足 → 启动失败"的前提表述为"default-bean 守卫达成"（NopIoC default 兜底语义事实）。
- [ ] **Proof**：全模块验证 `./mvnw test -pl nop-credential -am`；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-credential --severity high` **本 plan 触碰文件中 0 条 NEW high/critical 发现**（区分 pre-existing：live 已知 pre-existing = `NopCredentialBizModel.java:105`，附完整扫描输出对照）。
- [ ] **Follow-up**：roadmap W10-impl 状态更新（closure audit 通过后标 done，本 plan 不代劳）。

Exit Criteria:

- [ ] 文档配置项名/迁移步骤/守卫机制与 live 实现一致（可对号）。
- [ ] 验证命令通过（scan-hollow 按 NEW 发现口径，附输出）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] local 缺省路径一期零回归（不引模块、不配置 key-provider 时行为与 W1 完全一致；`credential-defaults.beans.xml` 装配未改）。
- [ ] fail-closed 全部启动期成立、无本地降级、无运行期托管端调用（代码追踪：`getKey` 路径无 HTTP 调用）。
- [ ] **配置指向 KMS 而模块缺失时启动失败**（default-bean 守卫测试证据——设计 §4.3 假安全场景被结构性堵死）。
- [ ] 密钥来源不混合（唯一例外 = 迁移残余列表，且禁含 active key + WARN 审计）。
- [ ] reencryptAll 分页完备性修复落地并有超批量测试证据。
- [ ] 无空壳/静默跳过（scan-hollow NEW 发现为 0）。
- [ ] 受影响 owner docs 与设计标注已同步。
- [ ] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow Check：门控装配 → bean 覆盖/守卫 → Cipher 注入链运行时追踪）。
- [ ] `./mvnw clean install -pl nop-credential -am -T 1C` 绿。
- [ ] `./mvnw test -pl nop-credential -am` 绿。
- [ ] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### KMS proxy 模式（Vault Transit / 非导出 CMK / 强合规 HSM）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §4.4/§七#5 裁定：材料交付模式已覆盖可交付形态；proxy 需新接口形态与性能论证，一期无需求。
- Successor Required: no（强合规需求出现时新开设计）

### 密文 keyId 分布查询管理面

- Classification: `optimization candidate`
- Why Not Blocking Closure: Phase 3 Decision 已裁定完备性交付物走分页修复路径（消除根因）；分布查询为观测辅助，关窗判定依赖重加密执行完毕本身。
- Successor Required: no

## Non-Blocking Follow-ups

- 其他厂商 KMS 实现按 SPI 自行扩展的示例文档（可选增强）。
- KMS 迁移关窗自动化（残余列表清空提示与 reencryptAll 联动编排）。
- `reencryptAll` 进度上报（W3 既定 follow-up，不在本 plan 翻案）。

## Closure

Status Note: (待收口时填写)
Completed: (未完成)

Closure Audit Evidence:

- Reviewer / Agent: (待独立 closure audit)
- Evidence: (待记录)
