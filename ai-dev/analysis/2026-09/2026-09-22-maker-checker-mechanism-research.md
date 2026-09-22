# Maker-Checker（四眼原则）机制业内调研与本平台通用实现方案

> Status: open
> Date: 2026-09-22
> Scope: nop-biz / nop-graphql / nop-sys / nop-wf / nop-auth
> Conclusion: 业内实现可归纳为"变更意图快照 + 审批队列 + 校验后重放"三要素；本平台已内置完整 maker-checker 骨架（`@BizMakerChecker` 注解、xbiz DSL、GraphQL tryAction 拦截、`nop_sys_checker_record` 表），checker 侧未实现。建议按"轻量内置（L1）+ nop-wf 复杂审批（L2）"两层方案补全，L1 复用现有骨架做最小闭环。

## Context

- 需要回答的决策点：对"重要业务写操作须经第二人复核后才能生效"（maker-checker / 四眼原则）这一类需求，业内如何设计与实现；本平台如何以**通用机制**（而非逐业务点硬编码）支持。
- 涉及模块：nop-biz（服务层拦截）、nop-graphql（入口拦截）、nop-sys（审批记录）、nop-wf（复杂审批流）、nop-auth（身份与角色）。
- 关键背景：平台已内置部分接口——`docs/dev-guide/orm/orm.md` L50 明确写着"MakerChecker审核机制……此机制尚未完全实现，已经内置了接口"。

## 调研目标

1. maker-checker 的概念边界与业内公认的核心设计原则是什么。
2. 业内主流系统（银行核心系统、开源金融系统、BPM 引擎、云平台、DevOps 工具链）分别怎么实现，可归纳出哪几种实现形态。
3. "待审批变更"在数据层如何建模，哪种方案适合做成平台级通用机制。
4. 本平台已有基础与缺口，推荐什么落地路径。

---

## 一、业内调研结果

### 1.1 概念与核心原则

maker-checker（又称四眼原则 four-eyes principle、two-man rule）：一笔敏感操作由一人（maker）创建/提交，必须由另一名授权人（checker）复核批准后才生效。起源于金融业内控（职责分离 SoD），现广泛用于支付、管理配置变更、权限提升、代码合并等场景。

综合 [Opcito 的模式总结](https://www.opcito.com/blogs/maker-checker-pattern-dual-control-system-implementation)、[Fintrac Advisors](https://fintracadvisors.com/the-importance-of-the-maker-checker-control-mechanism/)、[Flagsmith](https://flagsmith.com/blog/what-is-the-four-eyes-principle/)、[Wikipedia: Maker-checker](https://en.wikipedia.org/wiki/Maker-checker) 与 [Apache Fineract 的实现说明](https://finecko.com/blog/maker-checker-fineract)，业内公认的硬性设计原则有四条：

1. **职责分离（SoD）**：maker ≠ checker，自我审批必须被机制性禁止（不能只靠 UI 隐藏按钮）。Fineract 明确"a user cannot approve their own submissions"。
2. **生效前置**：变更在批准前不得产生任何实际效果（银行场景下即"不入账"），不是"先生效后追认"。
3. **审批意图完整性**：checker 批准的必须是确定的变更意图（快照），执行时校验 payload 未被篡改、未过期（payload integrity check）。
4. **双向审计**：maker 提交与 checker 批准/驳回双方动作全量留痕。

### 1.2 六类业内实现形态

| 形态 | 代表系统 | 生效语义 | 审批对象 | 粒度配置 | 会签/多级 |
|------|---------|---------|---------|---------|----------|
| A. 核心/ERP 单据状态机 | Oracle Finacle、Temenos、SAP release strategy | 记录以 pending 状态入库，授权后才"生效/入账" | 业务单据本身 | 按用户角色配 maker/checker 权限 | 按配置支持多级 release |
| B. 开源核心金融系统 | Apache Fineract / Mifos | 提交进 pending entry 队列，approve 后生效 | API 调用（pending entry） | **全局开关 + 按 API 粒度开关**（admin → system → configuration） | 简单单级 |
| C. BPM 工作流引擎 | Camunda / Flowable / Activiti | 流程走到 approve 后续节点才执行生效动作 | 流程实例中的任务 | 每个业务操作显式建模流程 | 多实例会签、网关条件路由，最灵活 |
| D. 云平台 PAM/JIT | Azure Entra PIM、GCP PAM（AWS 无原生，需第三方/JIT 方案） | 批准后授予**时限性**生效窗口 | 权限提升请求 | 按角色/资源配置审批人 | 多级审批人链 |
| E. DevOps 门禁 | GitHub Environments required reviewers、Terraform plan/apply、GitOps PR | 批准后才执行 apply/merge | **计划/变更集快照**（plan file、PR diff） | 按环境配置 required reviewers（≤6 人，任一批准即过） | 简单 N-of-M |
| F. ITSM 审批队列 | ServiceNow approval | 审批通过后变更单推进 | 变更请求 | 按变更类型路由审批流 | 多级 |

形态间的关键分野是**"审批的对象是什么"**：

- A/C/F 审批的是**业务单据/任务本身**（单据型生命周期）；
- B 审批的是**一次 API 调用**（提交时把请求捕获为 pending entry）；
- D 审批的是**一类动作的许可**（时限授权）；
- E 审批的是**变更意图快照**（plan/PR），批准后**重放**该快照。

其中 E（尤其 Terraform 的 plan/apply 分离）对"通用机制"最有启发：`plan` 阶段不受限地计算并固化变更意图，`apply` 阶段在审批通过后**重放同一份 plan**而非重新计算——这天然保证了原则 3（意图完整性），且与具体业务完全解耦。GitHub Environments 的"最多 6 个 required reviewer、任一批准即通过"则是 N-of-M quorum 的最简实现。

### 1.3 待审批变更的数据建模：三种方案

| 方案 | 做法 | 优点 | 缺点 | 适用 |
|------|------|------|------|------|
| 状态标志位 | 业务表加 `status=pending/approved` 列 | 简单直接 | 侵入业务表；"变更内容"与"审批状态"耦合；每个业务重复实现 | 单据型业务（银行交易单） |
| 影子表（shadow/staging table） | 每张业务表配一张 `_pending` 副本表 | 结构清晰、可直接 diff | 表数量翻倍；通用化成本随表数量线性增长；与元数据驱动框架冲突 | 表少且固定的系统 |
| **变更集快照（change-set journal）** | 一张通用审批表存 `(对象类型, 对象ID, 动作, 请求快照/diff)`，批准后重放 | 与具体业务解耦，天然通用；审批表自身即审计记录 | 重放需要"执行引擎"配合；快照与活数据可能失配（staleness） | **平台级通用机制** |

方案 C 正是"审批 API 调用"形态（B/E）的数据基础，也是本平台 `nop_sys_checker_record` 已经采用的形态（见第二节）。

### 1.4 关键设计决策清单（业内共识）

1. **启用粒度**：全局开关 + 按对象/操作细粒度开关（Fineract 的 per-API 开关是标杆）。
2. **SoD 硬约束**：服务端校验 checker ≠ maker，可扩展为角色级约束（如必须为 maker 的上级角色）。
3. **快照语义与 staleness**：送审后业务对象又被修改 → 既有审批应失效（要求驳回重报），参照 plan 过期语义；常用手段是快照中记录 base 版本号，执行时乐观锁校验。
4. **dry-run 与执行分离**：maker 提交前先跑"试算/校验"（Terraform plan），通过才送审；checker 批准后重放的是**已通过校验的同一意图**。
5. **quorum / 多级 / 条件路由**：简单场景 N-of-M（任一/全部）；金额阈值等条件路由、串签会签属于工作流引擎领域，不应在轻量机制里重复造。
6. **时效性**：审批记录应有有效期，过期作废（云 PIM 的时限思想）；过期自动释放 maker 侧占用。
7. **驳回回环**：reject 必须回环到 maker（通知 + 状态回滚），支持修改后重新送审。
8. **紧急通道（break-glass）**：可配置的 bypass 角色，跳过复核立即执行，但强制标记 + 强化审计告警。
9. **并发与幂等**：同一对象同时只允许一条 pending 记录（或明确冲突策略）；"批准后重放"需要幂等键防重复执行。
10. **全链路审计**：提交、批准、驳回、过期、bypass 五类事件全部落审计日志。

---

## 二、本平台现状

### 2.1 已有骨架（比预期完整）

全仓调研发现，Nop 平台已经内置了 maker-checker 的**完整 maker 侧骨架**，且入口选在了 GraphQL 引擎层：

**注解与 DSL 声明**
- `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/biz/BizMakerChecker.java`：`@BizMakerChecker(tryMethod=..., cancelMethod=...)`，配套 `BizMakerCheckerMeta`。
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/biz/xbiz.xdef` L45：`<maker-checker tryMethod="var-name" cancelMethod="var-name"/>`，即 xbiz DSL 同样可声明（模型类 `io.nop.biz.model.BizMakerCheckerModel`）。
- 装配：`nop-service-framework/nop-biz/src/main/java/io/nop/biz/impl/ObjectDefinitionExtProcessor.java` 的 `initMakerChecker` 校验 try/cancel 方法存在并注入；`ReflectionBizModelBuilder` 负责注解读取。

**运行时（maker 侧已通）**
- `nop-service-framework/nop-biz/src/main/java/io/nop/biz/makerchecker/` 三件套：
  - `IMakerCheckerProvider.java`：SPI，javadoc 注明启用条件 = 全局开关 + 方法声明了 maker-checker + `isMakerCheckerEnabled(bizObjName, bizMethod)` 返回 true；
  - `MakerCheckerTryServiceAction.java`：拦截后执行 tryMethod（试算校验）→ `sendForCheckAsync` 保存审批记录 → 向 maker 返回"审批编号"而非真实结果；
  - `SendForCheckRequest.java`：送审请求。
- **GraphQL 引擎层拦截**：`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java`（L275 起）——全局开关 `nop.graphql.maker-checker.enabled`（`GraphQLConfigs.java` L41，默认 false）打开且字段声明了 `tryAction` 时，**用 tryAction 替换原 mutation 执行**，并已正确处理多 operation 文档按顶层字段分别取 tryAction。`GraphQLWebService` 负责把开关接入执行上下文。
- 单测已存在：`nop-service-framework/nop-biz/src/test/java/io/nop/biz/makerchecker/TestMakerCheckerTryServiceAction.java` 与 `TestMakerCheckerMultiOperation`。

**审批记录表（方案 C 形态，字段即契约）**
- `nop-sys/model/nop-sys.orm.xml` L198-247：实体 `io.nop.sys.dao.entity.NopSysCheckerRecord`，表 `nop_sys_checker_record`。表注释即设计意图："对重要的业务数据可以要求启用MakerChecker机制，要求在实际修改数据库之前必须经过审批确认"。
- 字段完整覆盖契约：`bizObjName / bizObjId / makerId / makerName / requestAction / requestData(1MB 快照) / requestTime / checkerId / checkerName / checkTime / tryResult / status / cancelAction / cbErrCode / cbErrMsg`——其中 `cbErrCode/cbErrMsg` 显然是为"审批通过后回调重放失败"预留的。
- 配套 BizModel `nop-sys/nop-sys-service/src/main/java/io/nop/sys/service/entity/NopSysCheckerRecordBizModel.java` 仅有裸 CRUD。
- `nop-sys/nop-sys-web` 下已生成 CheckerRecord 管理页面雏形。

**其他可复用基础设施**
- 身份与角色：`io.nop.api.core.auth.IUserContext` 提供 `getUserId()/getRoles()/isUserInRole()`，业务方法内经 `IServiceContext.getUserContext()` 获取——SoD 校验的数据源现成。
- 审计：`IAuditService/AuditRequest`（`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/audit/`）+ `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/audit/AuditServiceImpl.java` + `GraphQLAuditLogger`（按通配模式审计 mutation）。
- ORM 变更追踪：`IOrmEntity.orm_propDirty()/orm_useOldValues` 可拿到字段级前后值，可用于生成 diff 展示；`IOrmInterceptor` + XplOrmInterceptor 支持持久层拦截。
- **nop-wf 已有通用审批**：`nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/base/approval-support.xbiz` 通过 `x:extends` 可合并进任意 BizModel，注入 `submitForApproval / withdrawApproval / approve / reject / reverseApprove` 五个标准 mutation（配套 `IApprovableBiz`、`ApprovalFlowHelper`）；流程引擎支持会签/或签/串签/投票（`execGroupType = and/or/seq/vote-group`）、候选人/候选组、委托转办加签、条件路由、超时（见 `docs-for-ai/02-core-guides/workflow-configuration.md`、`docs-for-ai/03-modules/nop-wf.md`）。
- nop-task 具备 `<suspend>` + `resume-when` 的"挂起等人工"语义；nop-job 适合审批超时扫描等辅助调度。
- 用法示例残留：`docs/dev-guide/recipe/crud.md` L48 有 `@BizMakerChecker(tryMethod = METHOD_TRY_SAVE)` 示例；`CrudBizModel.java` 中 `trySave` 方法**被注释掉**（L683-690），印证"做到一半"。

### 2.2 缺口

1. **checker 侧审批 API 未实现**：无 approve/reject/withdraw 的标准 BizModel（`NopSysCheckerRecordBizModel` 仅裸 CRUD，无 SoD 校验、无状态机约束）。
2. **审批通过后的回调重放未实现**：以系统身份重放 `requestAction(requestData)` 的执行器缺失（`cbErrCode/cbErrMsg` 字段空置）。
3. **`IMakerCheckerProvider` 无缺省实现**：`isMakerCheckerEnabled` 的配置模型（哪些 obj/action 启用）没有落地——这是"通用"与否的关键缺口。
4. **SoD 无校验**：全仓未见 maker ≠ checker 的约束代码；`approval-support.xbiz` 的 approve 同样不校验。
5. **staleness 无处理**：快照未记录 base 版本，送审后对象再变更无感知。
6. **超时/过期、紧急通道、quorum、通知**：均无。
7. `CrudBizModel.trySave`（ORM CRUD 的缺省 try 方法）被注释掉，未提供通用 CRUD 场景的缺省接入。

---

## 三、实现方案建议：通用 Maker-Checker 两层设计

### 3.1 总体分层

- **L1 轻量内置**（nop-biz + nop-sys + nop-graphql，补全现有骨架）：面向"重要写操作需第二人复核"的通用场景，单级审批 + 可选简单 quorum，机制级开启，业务零代码（只加注解/DSL 声明）。
- **L2 复杂审批流**（nop-wf）：多级会签、条件路由、委托转办——这些是工作流引擎的领域，不在 L1 重复实现。
- **桥接**：L1 审批记录可配置"升级到 wf 流程"，由 `IMakerCheckerProvider` 的 wf 实现委托 `WorkflowService.startWorkflow`；两层共享 `nop_sys_checker_record` 契约。这与业内分层一致：Fineract 式轻量队列覆盖 80% 场景，BPM 式流程覆盖长尾。

### 3.2 核心机制设计（逐条对应 1.4 决策清单）

1. **启用配置（决策 1）**：三级——全局开关（已有）→ xbiz `<maker-checker>` 声明（已有，含 tryMethod/cancelMethod）→ 运行时规则（**新增**：`IMakerCheckerProvider` 缺省实现，按 `bizObjName + action` 维度的规则表或规则 DSL 决定是否启用；规则模型可用 xdef 定义，模仿 `<maker-checker>` 在 `xbiz.xdef` 的做法，天然获得 Delta 差量覆盖能力）。
2. **maker 侧（决策 4，已有大半）**：GraphQL 层 tryAction 替换即"plan/apply 分离"的平台化实现——`tryMethod` 的语义约定应明确为 **dry-run 校验、不产生副作用**；校验通过后把**规范化请求快照**写入 `nop_sys_checker_record`（`requestData` 建议补充 base 版本号字段用于 staleness 校验），向调用方返回审批编号。`cancelMethod` 用于送审后锁定资源/驳回后解锁。
3. **checker 侧（新增，决策 2/3/9）**：为 `NopSysCheckerRecord` 提供审批 BizModel（或复用 approval-support 风格注入标准动作）：
   - `queryPendingItems`：Checker Inbox 查询（按角色/租户过滤）；
   - `approve`：事务内依次校验——状态机（PENDING → APPROVED）、SoD（`checkerId != makerId`，角色规则可扩展）、staleness（快照 base 版本 == 当前实体版本，失配则要求重新送审）→ 通过后触发回调重放；
   - `reject`：必须填驳回原因，状态 PENDING → REJECTED，触发 `cancelMethod` 解锁，回环通知 maker；
   - 并发约束：同一 `(bizObjName, bizObjId)` 同时至多一条 PENDING（唯一索引或乐观锁）。
4. **回调重放（新增，决策 3/9 的执行面）**：以系统身份重放 `requestAction(requestData)`；建议 after-commit 执行 + 失败重试，失败落 `cbErrCode/cbErrMsg` 并可告警；幂等键 = 审批记录 id。重放语义上等同 Terraform apply 重放 plan：**执行的是批准过的快照，不是重新计算的新请求**。
5. **SoD（决策 2）**：approve 内置硬校验（服务端，非 UI 层）；可扩展配置"checker 必须具备某角色/maker 的上级角色"，数据源用 `IUserContext`。
6. **时效（决策 6）**：`nop_sys_checker_record` 增加 expireTime（或配置化 TTL）；nop-job 定时扫描过期 PENDING → EXPIRED + 调 `cancelMethod` 解锁。
7. **紧急通道（决策 8）**：配置 bypass 角色；走通道的动作标记 `emergency=true` 落审计并告警。
8. **审计（决策 10）**：审批表本身即审计记录；提交/批准/驳回/过期/bypass 五类事件另经 `IAuditService` 上报，GraphQL 层 `GraphQLAuditLogger` 现有机制可直接覆盖 mutation 面。

### 3.3 关键时序

```
maker:   mutation 调用 → GraphQL 引擎 tryAction 替换（开关+声明命中）
         → tryMethod dry-run 校验（无副作用）
         → sendForCheck：快照落 nop_sys_checker_record（PENDING，含 base 版本）
         → 返回审批编号（不执行真实变更）

checker: queryPendingItems 查看 inbox → approve
         → 校验：状态机 + checker≠maker（SoD）+ base 版本（staleness）
         → after-commit 重放 requestAction(requestData)，幂等键=记录 id
         → 成功：APPROVED；失败：cbErrCode/cbErrMsg + 重试/告警

reject/expire: cancelMethod 解锁 → 通知 maker 修改后重新送审
```

### 3.4 演进路线（建议，未排期）

- **Phase 1 最小闭环**：checker 侧 BizModel（approve/reject/withdraw）+ 回调重放 + SoD 硬校验 + 单条 PENDING 并发约束；打开端到端测试路径。
- **Phase 2 配置与体验**：`IMakerCheckerProvider` 缺省实现（per obj/action 规则）+ staleness base 版本 + Checker Inbox GraphQL API + `nop-sys-web` 页面完善。
- **Phase 3 周边完备**：过期扫描（nop-job）+ 通知 + 紧急通道 + 审计事件五类齐全。
- **Phase 4 通用化推广**：复活 `CrudBizModel.trySave` 作为 ORM CRUD 场景的缺省 try 方法；wf 桥接（L1→L2 升级）覆盖会签场景。

### 3.5 业内经验的可借鉴与不可照搬

- **可借鉴**：
  - Fineract：全局 + per-API 粒度开关、checker permission 命名、自我审批硬禁止——与本平台"注解/DSL 声明 + 引擎拦截"形态同构；
  - Terraform plan/apply：**审批意图快照 + 重放**语义，是通用机制解耦业务的核心；
  - GitHub Environments：N-of-M quorum 的极简配置面（≤6 reviewer、任一批准即过）；
  - 云 PIM：审批有效期/时限生效思想，用于过期与临时授权场景。
- **不可照搬**：
  - 影子表方案：表数量翻倍，与平台元数据驱动 + Delta 定制的思路冲突；
  - BPM 显式建模每个操作的审批流：对通用机制成本过高，仅 L2 长尾场景使用；
  - 把 SoD 只做在 UI 层（业内反复强调的失败模式）。

---

## Open Questions

- [ ] 回调重放放在 approve 事务内还是 after-commit？（涉及外部副作用与分布式一致性，倾向 after-commit + 重试）
- [ ] `requestData` 快照中敏感字段是否需要加密存储（`nop_sys_checker_record` 现为明文 1MB 字段）？
- [ ] 多租户场景下 checker 是否必须与 maker 同租户（表结构已有 tenant 支持，规则需明确）？
- [ ] L1 是否需要 N-of-M quorum，还是简单场景一律单级、需会签直接转 L2 nop-wf？
- [ ] `tryMethod` 的语义契约（必须无副作用）如何做机制级验证（如运行时检测 try 方法是否触发了 ORM 写）？

## References

- 内部：
  - `docs/dev-guide/orm/orm.md`（L50 官方状态：机制未完全实现、接口已内置）
  - `docs/dev-guide/recipe/crud.md`（`@BizMakerChecker` 用法示例）
  - `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/biz/xbiz.xdef`（L45 maker-checker DSL）
  - `nop-service-framework/nop-biz/src/main/java/io/nop/biz/makerchecker/`（运行时三件套）
  - `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java`（tryAction 拦截）
  - `nop-sys/model/nop-sys.orm.xml`（`nop_sys_checker_record` 表契约）
  - `nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/base/approval-support.xbiz`（wf 通用审批）
  - `docs-for-ai/02-core-guides/workflow-configuration.md`、`docs-for-ai/03-modules/nop-wf.md`、`docs-for-ai/03-modules/nop-sys.md`
- 外部：
  - [Wikipedia: Maker-checker](https://en.wikipedia.org/wiki/Maker-checker)
  - [Opcito: Maker-Checker Pattern — Dual-Control System Implementation](https://www.opcito.com/blogs/maker-checker-pattern-dual-control-system-implementation)
  - [Fintrac Advisors: The Importance of the Maker-Checker Control Mechanism](https://fintracadvisors.com/the-importance-of-the-maker-checker-control-mechanism/)
  - [Flagsmith: What is the Four Eyes Principle and How Does it Work?](https://flagsmith.com/blog/what-is-the-four-eyes-principle/)
  - [Finecko: Apache Fineract Maker-Checker 4-Eyes Approval Workflow](https://finecko.com/blog/maker-checker-fineract)
  - [Apache Fineract 官方文档](https://fineract.apache.org/)（maker-checker 全局/按 API 开关）
  - [Mifos API: Approve Maker Checker Entry](https://mifos.readme.io/)
  - [Oracle Finacle 文档: Pending for Approvals](https://docs.oracle.com/)
  - [Camunda Forum: four-eye principle 模式讨论](https://forum.camunda.io/)
  - [Camunda: Workflow diagram and pattern examples using BPMN models](https://camunda.com/blog/2022/09/workflow-patterns-bpmn/)
  - [GitHub Docs: Managing environments for deployment（required reviewers）](https://docs.github.com/en/actions/deployment/targeting-different-runners/managing-environments-for-deployment)
  - [AWS: Implementing just-in-time privileged access](https://aws.amazon.com/blogs/security/)
  - [Microsoft Learn: Azure PIM approval](https://learn.microsoft.com/en-us/entra/id-governance/privileged-identity-management/)
