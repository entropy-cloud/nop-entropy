# W11-impl(A) 凭证归属统一（scope=system|user + ownerId）

> Plan Status: completed
> Mission: nop-credential-mfa
> Work Item: W11-impl（Part A：归属统一；Part B：RBAC 授权为 successor plan，见"拆分裁定"）
> Last Reviewed: 2026-08-16
> Source: `ai-dev/design/nop-credential/02-phase2-design.md` §五（全部）+ §3.3 发起动作归属规则（W9 落地后的回补）+ §6.0 输入基线；roadmap `ai-dev/backlog/nop-credential-mfa-roadmap.md` W11-impl
> Related: W9-impl（`2026-08-14-2342-1`，**先行硬依赖**：本 plan Phase 2 回补其发起动作归属校验；其 Deferred 段已登记 successor = 本 plan）、W10-impl（`2026-08-14-2342-2`，先行执行，共改 `NopCredentialBizModel`）、W3（`2026-08-12-0615-3`，其 Deferred "RBAC Fine-Grained Authorization" 的归属面由本 plan 兑现、授权面由 Part B 兑现）

## Purpose

按 W9-design §五落地凭证归属统一：`NopCredential` 新增 `scope`（缺省 system，存量零迁移）+ `ownerId`（user 级必填、归属不可变）列；provider 明文出口 per-method 归属校验（`IUserContext.get()` 上下文捕获，SPI 签名零变更）；BizModel 读/写分级两层防御 + 继承动作面收口（含 draft review 发现的四个旁路动作）；W9-impl oauth2 发起动作归属校验回补。一期行为零回归（不传归属输入 = system 级，与一期完全一致）。

## 拆分裁定（design §八 规定的 plan-first 裁定，起草时执行）

W11-impl 交付面（归属 + RBAC 两主题）经独立 draft review 估算约 25-35 文件，超 roadmap 单 plan 规模（5-15 文件/200-500 行/1-4 phases）与设计 §八 拆分提示。按 roadmap W11-impl 条目"预估超出单 plan 规模先行拆分裁定，不硬撑单文件"，**本 plan 承载 Part A（归属统一）**；**Part B（RBAC 细粒度授权：`NopCredentialAuth` 实体 + §6.3 判定矩阵消费侧串联 + 授权管理面 + Web AMIS）为 successor plan**，依赖本 plan 的 scope 列落地（user 级不叠加角色授权的判定前提），由下一轮 plan 起草产出。W11-impl 工作项的 `done` 以两份 plan 全部收口为准。

## Current Baseline

（2026-08-14 live repo 核对，经独立 review 复核全部属实）

- `nop-credential/model/nop-credential.orm.xml`：`NopCredential` 16 列，**无 scope/ownerId 列**；`USAGE_SCOPE` 列（live:39-40，无默认值、全仓库无取值消费——设计 §5.1 结论 2 裁定废弃）；`NopCredentialUsage`（credentialId+consumerRef 唯一、无 delFlag 物理删除惯例）+ `usages` to-many cascadeDelete 关系（relation `tagSet="pub"`——经 GraphQL selection 可绕过 usage BizModel 查询面，见 Phase 1）。
- `CredentialProviderImpl`（live:46-229）：`loadActiveCredential`（live:169-187）仅 delFlag fail-closed（已删凭证抛 `ERR_CREDENTIAL_DELETED`，与 NOT_FOUND 为两个错误码，一期即如此）；**无归属校验**；唯一解密点不变式成立。
- `NopCredentialBizModel`：`saveCredential`（无 scope/ownerId 输入）/`get`/`findPage`（data 置空）/`maskList`/`typeList`/`test`/`reencryptAll`/`delete`（引用计数拦截 + status=disabled）；标准 `save` 已禁用（live:104-106）；**标准 `update`/`batchDelete` 未禁用**；`findList`/`findFirst` 等继承查询动作**无归属过滤**。
- **继承动作面旁路（draft review 核定，必须收口）**：`CrudBizModel.deleteByQuery`（live:1472，经 `doDeleteByQuery → doDeleteMulti → doDelete(id, invokeDefaultPrepareDelete)`，**不经过**本类覆盖的 `delete`/`prepareDeleteWithUsageCheck`）→ 绕过引用计数拦截（破坏一期契约锚点）；`updateByQuery`（live:1433-1436，`prepareQuery` 传 null）→ 绕过读过滤批量改元数据；`batchGet`（live:1015，`dao.batchGetEntitiesByIds` 直取）→ 绕过读过滤；`copyForNew`（live:1667，读源实体克隆保存）→ 绕过读过滤且在 saveCredential 之外复制凭证行（含密文）。（`batchUpdate`/`batchModify`/`saveOrUpdate` 内部委托 update/save/delete，禁用后自动失效。）
- `NopCredentialUsageBizModel`：15 行裸 CrudBizModel，**无管理员限制**。
- 读过滤钩子：覆盖 `CrudBizModel.defaultPrepareQuery`（live:520，经 `invokeDefaultPrepareQuery` 被 findPage/findList/findFirst/findCount 统一调用；全仓库尚无子类覆盖先例，机制存在）；单条 `get` 归一需覆盖后抛 `UnknownEntityException`（与基类 live:909 软删除同口径）。
- `IUserContext`（`nop-kernel/nop-api-core/.../auth/IUserContext.java`）：`getUserId()`(live:49)/`isUserInAnyRole(Collection)`(live:77)/`getRoles()`(live:79)，静态 `IUserContext.get()` 经 `ContextProvider`（测试注入先例：nop-auth AutoTest 用 `ContextProvider.setLoginUser`）。
- action-auth 现状（`nop-credential-web/.../auth/nop-credential.action-auth.xml`）：`NopCredential:saveCredential` roles=`admin`（**普通用户被 action 层 403，无法建 user 级凭证**——需 delta）；`NopCredential:mutation`（覆盖 test）roles=`admin,operator`；usage query 无收紧。
- 错误码/配置落点：`io/nop/credential/crypto/CredentialErrors`（全部 `nop.err.credential.*`）、`config/CredentialConfigs`（`service/NopCredentialErrors`/`NopCredentialConfigs` 为空占位，勿用）。
- nop-credential 对 nop-auth 模块零依赖（各 pom 核实）——设计 §6.1 结论 4 依赖边界前提成立；`NopAuthConstants` 事实管理员角色名 = `admin`/`nop-admin`。
- W9-impl 落地后（先行硬依赖）：oauth2 `beginOAuthFlow` 存在登录态 + 实例状态校验，**归属校验待本 plan 回补**。
- GraphQL 引擎级测试先例：nop-auth 测试 `graphQLEngine.newRpcContext`。
- `deploy/sql`：`_create_` 为 codegen 产物；`_add_tenant_nop-credential.sql` 为手写增量 alter 先例（本 mission 期间无加列迁移先例）。

## Goals

- `NopCredential` 新增 `scope`（缺省 system，存量 NULL 视同 system）+ `ownerId`（user 级必填、system 级强制空、归属不可变）列；`usageScope` 停止赋值并在 ORM 注释标注废弃。
- provider 明文出口 per-method 归属校验（§5.3 矩阵，解密之前、fail-closed、错误码 `nop.err.credential.*`）：`getCredential`/`getCredentialData` user 级 owner 唯一（admin 不例外、无上下文拒绝）；`mask`/`testCredential` user 级 owner+admin。
- BizModel 两层防御：读类结构性过滤（**含 `scope IS NULL` 分支**）、写类分级（system=管理员、user=owner+管理员）、单条越权归一"不存在"、标准 `update`/`batchDelete`/`updateByQuery`/`deleteByQuery`/`batchGet`/`copyForNew` 全收口、继承查询动作过滤、`reencryptAll`/usage 查询面限管理员。
- `nop.credential.admin-roles` 配置（CSV，缺省 `admin,nop-admin`，`IUserContext.isUserInAnyRole` 运行时判定）。
- action-auth delta：saveCredential/test 对登录用户开放（归属分级由 BizModel 运行时判定执行）、usage query 收紧 admin——否则普通用户的 user 级凭证功能在 GraphQL 入口即 403（空洞实现）。
- W9-impl oauth2 发起动作归属校验回补（system=管理员 / user=owner）。
- `docs-for-ai/03-modules/nop-credential.md` 归属章节。

## Non-Goals

- **RBAC 细粒度授权全部交付面**（`NopCredentialAuth` 实体、§6.3 判定矩阵消费侧串联、授权管理面、Web 授权编辑）——Part B successor plan（见拆分裁定）。
- 租户隔离（用户裁决：租户为平台全局能力）。
- `usageScope` 列物理删除（§七#7 optimization candidate——本 plan 标注废弃即闭合其语义面，见 Deferred）。
- user 级凭证共享/`status=disabled` 全局收紧（§七#2/#3 deferred）。
- `ICredentialProvider` SPI 签名变更（上下文捕获裁决）；nop-auth 任何模块依赖（§6.1 结论 4）。
- 消费链上下文丢失告警审计（§七#8 watch-only residual，归 A1-audit）。

## Scope

### In Scope

- `nop-credential.orm.xml`：`NopCredential` 加 scope/ownerId 列 + `usages` relation 暴露面收紧 + `usageScope` 废弃注释（model-first → codegen → `_create_` 再生成 + 三方言手写增量 alter 脚本）。
- `CredentialProviderImpl`：per-method 归属矩阵 + `IUserContext.get()` 上下文捕获。
- `NopCredentialBizModel`：saveCredential 可选 scope/ownerId 输入与校验规则、读类结构性过滤（`defaultPrepareQuery` 覆盖）、单条归一、写类分级、六个旁路动作收口、W9 发起动作回补。
- `NopCredentialUsageBizModel` 查询面限管理员；`reencryptAll` 限管理员。
- `nop.credential.admin-roles` 配置（`config/CredentialConfigs`）+ 运行时判定；新错误码（`crypto/CredentialErrors`）。
- action-auth delta（`nop-credential.action-auth.xml`）。
- Web：scope/ownerId 只读展示 + 创建表单归属选择（最小面）。
- `docs-for-ai/03-modules/nop-credential.md` 归属章节。

### Out Of Scope

- Part B（RBAC）；W16 深度迁移；A1-audit；PKCE。

## Execution Plan

### Phase 1 - ORM 归属字段 + provider 归属校验

Status: completed
Targets: `nop-credential/model/nop-credential.orm.xml`、`deploy/sql/*`、`CredentialProviderImpl`、`io/nop/credential/crypto/CredentialErrors`、`config/CredentialConfigs`

- Item Types: `Fix | Decision | Proof`

- [x] **Fix**：ORM——`NopCredential` 新增 `scope`（VARCHAR，**不加 DDL 默认值**：存量行 NULL 由校验/过滤侧视同 system，语义等价裁定记录于此；新写入恒显式值）与 `ownerId`（VARCHAR，可空）；`usageScope` 列保留但注释标注 deprecated；`usages` relation 收紧：`tagSet` 去掉 `pub`（usage 管理信息经 admin 限定的 `NopCredentialUsageBizModel` 查询面访问，堵 GraphQL selection 绕过——实施时若去 pub 破坏既有合法引用则回写替代裁定）。codegen 重生成（禁止手编 `_gen`/`_` 前缀生成物）+ `_create_` 再生成 + 存量部署手写增量 alter 脚本（循 `_add_tenant_` 先例，非 codegen 产物），三方言齐备。
- [x] **Fix**：`CredentialProviderImpl` 归属校验（§5.3 per-method 矩阵，解密之前、fail-closed，先序 delFlag）——`getCredential`/`getCredentialData`：scope=user 时无用户上下文或 `userId != ownerId` → 拒绝（admin 不例外）；`mask`/`testCredential`：user 级 owner 或管理员放行；scope=system（**含 NULL**）放行。新错误码入 `crypto/CredentialErrors`（归属不符，`nop.err.credential.*`），不返回 null/空；已删凭证维持一期 `ERR_CREDENTIAL_DELETED` 语义（先序 delFlag、不进入归属判定）。
- [x] **Fix**：管理员判定——`nop.credential.admin-roles`（CSV，缺省 `admin,nop-admin`）入 `config/CredentialConfigs` + `IUserContext.isUserInAnyRole` 运行时判定工具方法。
- [x] **Decision**：无用户上下文场景语义确认——后台任务/服务间调用取 user 级凭证一律拒绝（owner 消亡同 fail-closed，仅管理员可见/可删），测试固化该语义；一期消费链（W7-successor，用户请求线程内上下文可达）对 system 级零感知。
- [x] **Proof**：单测——user 级 owner 取明文成功 / 非 owner（含 admin）拒绝 / 无上下文拒绝 / system 级（含存量 NULL scope）零变化（一期回归）/ mask/test owner+admin 放行、他人拒绝 / delFlag 先于归属（已删凭证报 ERR_CREDENTIAL_DELETED，不泄露归属）。

Exit Criteria:

- [x] `./mvnw clean install -pl nop-credential -am -T 1C` 绿；`_create_` 再生成 + 三方言增量 alter 脚本齐备。
- [x] **端到端验证**：模拟用户请求上下文（`ContextProvider` 注入 `IUserContext`）从 `ICredentialProvider.getCredential` 入口到明文/拒绝出口全路径测试；W7-successor 消费链回归：`./mvnw test -pl nop-ai -am` 绿（system 级凭证经 resolver 取用不变——注意 `-pl nop-credential -am` 覆盖不到 nop-ai）。
- [x] **无静默跳过**：所有拒绝分支显式抛错（测试覆盖矩阵每格）。
- [x] **新功能测试**：列出归属矩阵测试类与用例名。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - BizModel 归属过滤 + 写分级 + 继承动作面收口 + action-auth + W9 回补

Status: completed
Targets: `NopCredentialBizModel`、`NopCredentialUsageBizModel`、W9-impl `beginOAuthFlow`、`nop-credential-web/.../auth/nop-credential.action-auth.xml`、`nop-credential-web/.../pages/NopCredential/*`（scope/ownerId 最小展示/选择面）

- Item Types: `Fix | Proof`

- [x] **Fix**：`saveCredential` 新增可选输入 `scope`/`ownerId`——缺省不传 = system（一期行为不变）；**update 路径缺省不传 = 保持不变**（仅显式传入且与存量不符才拒）；普通用户建 user 级强制 ownerId=当前登录用户；管理员可代建（指定他人 owner，审计载体 = 行内 `createdBy ≠ ownerId` 自证 + 平台 ChangeLog，注意 ChangeLog 对插入需 `audit-save` tag 的事实）；建 system 级限管理员；scope=user 且 ownerId 空 / scope=system 且 ownerId 非空 → 拒绝；归属不可变（显式传变更值即拒）。
- [x] **Fix**：读类结构性过滤——覆盖 `defaultPrepareQuery` 注入条件：非管理员登录用户 **`scope=system ∨ scope IS NULL ∨ (scope=user ∧ ownerId=本人)`**（NULL 分支防存量行从普通用户视野消失）；管理员不加过滤；`get`/`maskList`/`test` 单条越权归一"不存在"语义（覆盖 `get` 抛 `UnknownEntityException` 与软删除同口径；**maskList/test 在 BizModel 层先做行级可见性预检、不可见即按 NOT_FOUND 归一**，再调 provider——否则 provider 的显式归属错误码会泄露归属存在性）；`typeList` 返回类型注册表、与行过滤无关（不在过滤面）。
- [x] **Fix**：写类分级——`saveCredential` 修改路径：system 级限管理员、user 级限 owner+管理员；`delete` 同级；标准 `update`/`batchDelete` **禁用**（抛 `UnsupportedOperationException`，与标准 `save` 同口径）；**`updateByQuery`/`deleteByQuery`/`batchGet`/`copyForNew` 四动作收口**（禁用或强制走过滤+分级语义，逐个裁定记录：`deleteByQuery` 必须禁用——绕过引用计数拦截破坏一期契约；`updateByQuery` 禁用——prepareQuery 旁路；`batchGet` 改走过滤语义或禁用；`copyForNew` 禁用——在 saveCredential 外复制密文行）；`reencryptAll` 限管理员。
- [x] **Fix**：`NopCredentialUsageBizModel` 查询面限管理员（action-auth 收紧 + BizModel 运行时判定双层）。
- [x] **Fix**：action-auth delta——`NopCredential:saveCredential`/`test` 对登录用户开放（角色分级由 BizModel 运行时判定执行，避免 action 层提前 403 使 user 级功能不可达）；usage query 资源收紧 admin。
- [x] **Fix**：**W9 回补**——oauth2 `beginOAuthFlow` 发起动作归属校验：system 级限管理员、user 级限 owner（与 §5.3 CRUD 矩阵一致）。〔实施裁定：plan 措辞 "user=owner" 与其自引的 §5.3 写类矩阵（user 级限 owner+管理员）冲突，按设计文档（Source of truth）落地 owner+管理员，测试固化两分支+admin 分支；W9 既有测试用户提升为 admin 角色以维持 system 级发起闭环〕
- [x] **Fix**：Web 最小面——`NopCredential` 列表/详情只读展示 scope/ownerId，创建表单增可选归属选择（scope + ownerId，输入契约与 `saveCredential` 分级校验一致：普通用户选 user 级时 ownerId 固定为本人；usage 页面无改动面）。
- [x] **Proof**：单测——普通用户 CRUD 可见性边界（见 system+NULL/不见他人 user 级）、越权单条归一（get/maskList/test）、六动作收口（update/batchDelete/updateByQuery/deleteByQuery/batchGet/copyForNew 均不可绕过）、归属不可变、管理员代建 `createdBy≠ownerId`、W9 发起动作归属两分支。
- [x] **Proof（GraphQL 级端到端）**：经 GraphQL 引擎（`graphQLEngine.newRpcContext` 先例）验证普通登录用户可建 user 级凭证、可见集正确、越权 id 归一——绕过 BizModel 直调的单测不能替代入口级验证。

Exit Criteria:

- [x] 两层防御均落地：BizModel 过滤绕过时 provider 层仍拦截（测试模拟直接调 provider）。
- [x] Web 最小面已落地：`NopCredential/main.page.yaml` 展示列与创建表单归属选择与 `saveCredential` 输入契约一致（页面文件核对 + 模块构建绿）。
- [x] 一期零回归：不传 scope/ownerId 的既有调用、非管理员对 system 级（含 NULL）凭证的既有可见性、W7-successor 消费链、W9 oauth 闭环（回补后）全部既有测试绿（含 `./mvnw test -pl nop-ai -am`）。
- [x] **新功能测试**：列出测试类与用例名（含 GraphQL 级用例）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 文档同步 + 收口验证

Status: completed
Targets: `docs-for-ai/03-modules/nop-credential.md`、`ai-dev/design/nop-credential/02-phase2-design.md`（仅追加 impl 裁定标注）、`ai-dev/backlog/nop-credential-mfa-roadmap.md`

- Item Types: `Follow-up | Proof`

- [x] **Follow-up**：`docs-for-ai/03-modules/nop-credential.md` 补归属章节（scope/ownerId 语义与不可变规则、per-method 矩阵、NULL 视同 system、admin-roles 配置、单条归一语义、六个收口动作清单、usageScope 废弃说明、action-auth 变更说明）。
- [x] **Proof**：全模块验证 `./mvnw test -pl nop-credential -am` + `./mvnw test -pl nop-ai -am`；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-credential --severity high` **本 plan 触碰文件中 0 条 NEW high/critical 发现**（区分 pre-existing：live 已知 pre-existing = `NopCredentialBizModel.java:105` 标准 save 禁用抛 UnsupportedOperationException，为一期有意模式；本 plan 新增的同类禁用动作属同模式，附完整扫描输出对照裁定）。〔裁定：全量 6 条 high 均为 P1 UnsupportedOperationException——1 条 pre-existing（标准 save，行号漂移至 127）+ 5 条本 plan 新增（update/batchDelete/updateByQuery/deleteByQuery/copyForNew 五个旁路动作的有意禁用，Phase 2 明确交付物），每条均带原因的显式消息 + javadoc + 收口测试断言路径 fail-closed，属 plan 预裁定的同模式，非空壳实现〕
- [x] **Follow-up**：roadmap W11-impl 条目更新——本 plan 收口时在 roadmap 登记 Part B（RBAC successor）待办标注（防 Part A 收口后 W11-impl 被误标 done）；W11-impl 的 `done` 以 Part A/Part B 双 plan 全部 closure audit 通过后为准（本 plan 不代劳标 done）。

Exit Criteria:

- [x] 文档矩阵/配置项/收口动作清单与 live 实现一致（可对号）。
- [x] 验证命令通过（scan-hollow 按 NEW 发现口径，附输出）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 归属判定矩阵（§5.3 per-method）全格测试通过；NULL scope 分支有专项测试。
- [x] 一期零回归：不传归属输入的 CRUD、非管理员对 system/NULL 凭证可见性、W7-successor 消费链、W9 oauth 闭环全部既有测试绿。
- [x] 两层防御成立（BizModel 过滤 + provider 纵深校验，绕过面测试）。
- [x] 继承动作面六个旁路（update/batchDelete/updateByQuery/deleteByQuery/batchGet/copyForNew）全部收口，引用计数拦截无绕过路径。
- [x] 依赖边界守住：nop-credential 全模块 `mvn dependency:tree` 无 nop-auth 依赖引入（7 模块逐一心查 0 引用；触碰 Java 文件 `import io.nop.auth` 零匹配）。
- [x] ORM 变更经 model-first（源 → codegen → DDL），无手编生成物。
- [x] GraphQL 入口级端到端验证通过（非仅 BizModel 直调单测）。
- [x] 无空壳/静默跳过（scan-hollow NEW 发现为 0 + 矩阵分支测试覆盖；6 条 high 全量 = 1 pre-existing + 5 条 plan 预裁定同模式有意禁用，见 Phase 3 裁定）。
- [x] 受影响 owner docs 已同步。
- [x] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow Check：BizModel 过滤 → provider 校验 → 解密出口调用链追踪）。
- [x] `./mvnw clean install -pl nop-credential -am -T 1C` 绿。
- [x] `./mvnw test -pl nop-credential -am` + `./mvnw test -pl nop-ai -am` 绿。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（checklist 全勾选 + Closure Evidence 已写入）。
- [x] checkstyle / 代码规范检查通过（`./mvnw checkstyle:check -pl nop-credential -Pqa` exit 0；默认 checkstyle 配置对上游模块有 9164 条 pre-existing 违规，非项目门禁）。

## Deferred But Adjudicated

### RBAC 细粒度授权（NopCredentialAuth + 判定矩阵 + 授权管理面 + Web）

- Classification: `out-of-scope improvement`（时序拆分——Part B successor）
- Why Not Blocking Closure: 设计 §八 拆分提示 + roadmap 单 plan 规模约束（合并交付约 25-35 文件超标）；Part B 依赖本 plan 的 scope 列（user 级不叠加角色授权的判定前提）；拆分后两份 plan 各自可独立收口。
- Successor Required: yes
- Successor Path: 下一轮 plan 起草产出（W11-impl Part B；本 plan 收口前须在 roadmap 登记 Part B 待办，防止 W11-impl 被误标 done）

### `usageScope` 列物理删除（DDL 治理）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 设计 §七#7：语义已废弃（本 plan 完成停止赋值 + ORM/文档标注）、列无默认值无取值消费方，物理删除是纯 DDL 治理动作；涉及存量部署 ALTER 风险，独立裁定更稳。
- Successor Required: no（后续 DDL 治理批量处理时顺带）

### user 级凭证共享（n8n sharing 模式）/ `status=disabled` 全局收紧

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §七#3/#2 裁定：需求未实证 / 存量行为变更须独立评估，不随本 plan 静默带入。
- Successor Required: no（需求实证或 A1-audit 发现后重开）

## Non-Blocking Follow-ups

- 消费链上下文丢失告警审计（§七#8 watch-only residual——信任边界为有意设计，观测增强归 A1-audit 评估）。
- Web 归属筛选 UX 增强（本 plan 仅最小展示/选择面）。

## Closure

Status Note: Part A（归属统一）三个 Phase 全部执行完毕并经独立 closure audit 复核（0 Blocker/0 Major）。注意：本 plan 收口 ≠ W11-impl 工作项 done——Part B（RBAC successor）待办已在 roadmap 登记（W11-impl 保持 `doing`，`done` 以双 plan closure audit 通过为准）。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（opencode general subagent，task id `ses_ff511b504ffewORq0n1odh3Wq4`，2026-08-16）
- Evidence:
  - 逐项 PASS（file:line 实证）：ORM（scope/ownerId 无 DDL 默认值 + `_create_`/`_add_scope_owner_` 三方言齐备 + usages 去 pub → xmeta `published=false`）/ provider 矩阵（`assertOwnershipForPlaintext:492` owner-only、`assertOwnershipForMaskOrTest` owner+admin、先序 delFlag 后解密）/ CredentialOwnership（admin-roles CSV→isUserInAnyRole、writeDenialReason 分级）/ BizModel（saveCredential 归属输入与分级、`defaultPrepareQuery:385` 结构性过滤、get 归一 UnknownEntityException、maskList/test 预检归一 NOT_FOUND、写分级、六旁路收口 `:712/:723/:735/:747/:759` + batchGet 过滤 `:783`、reencryptAll admin）/ usage 查询面 admin / W9 回补（`assertBeginOwnership:128`）/ action-auth delta / 测试非空壳（12+20+6 用例，surefire 12/0/0、20/0/0、25/0/0，时间戳后于源码 mtime）/ 文档-roadmap-设计标注一致 / 依赖边界零 nop-auth。
  - Anti-Hollow 检查：(a) `invokeDefaultPrepareQuery` 经 `getThisObj().invoke` 派发至子类覆盖（CrudBizModel `findCount:281`/`findPage:310`/`findFirst:479`/`findList:1543` 全部传入）+ GraphQL 级过滤测试行为实证；(b) provider 调用链 `loadActiveCredential(delFlag) → assertOwnership* → decryptToData` 顺序确认；(c) 触碰文件无空方法体/静默跳过（两处 `catch (NopException ignored)` 为文档化的保守脱敏/类型容忍语义）；`scan-hollow --severity high` 复跑 = 6 条（1 pre-existing + 5 plan 预裁定有意禁用），与 Phase 3 裁定逐字一致。
  - Deferred 项分类检查：Part B（RBAC）= 时序拆分 successor（roadmap 已登记）；usageScope 物理删除 = optimization candidate；user 级共享/disabled 全局收紧 = 需求未实证——无 in-scope live defect 被降级。
  - Minor（audit）：plan 文本 "main.page.yaml 展示列" 实际落点为 view.xml（展示列继承 _gen，substance 满足）；W9 begin owner+admin 为已裁定记录偏离（非 mismatch）。
  - `check-plan-checklist.mjs --strict` 退出码 0；`check-doc-links.mjs --strict` 退出码 0（6 warnings 为 plan 反引号 VFS 相对路径提示，W9/W10 先例同口径）。
  - 构建门禁：`./mvnw clean install -pl nop-credential -am -T 1C` 绿（service 144 + kms-vault 32 + web 1）；`./mvnw test -pl nop-credential -am` + `./mvnw test -pl nop-ai -am` 绿（ai-service 25/ai-core 217/ai-gateway 84）；`./mvnw checkstyle:check -pl nop-credential -Pqa` exit 0。已知 pre-existing flake：`-T 1C` reactor 中 nop-auth-service 偶发 tenant-cache order 依赖失败（clean tree 同命令复现、模块单独运行绿、与 nop-credential 无依赖边，2026-08-16 双向验证）。

Follow-up:

- Part B（RBAC 细粒度授权）：successor plan 待起草（roadmap W11-impl 条目已登记，`done` 以双 plan 收口为准）。
- 消费链上下文丢失告警审计（§七#8 watch-only residual，归 A1-audit）；Web 归属筛选 UX 增强（non-blocking）。
