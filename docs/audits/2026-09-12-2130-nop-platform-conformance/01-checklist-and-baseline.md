# 01 · 审计依据清单与平台基线校准

> 审计时间：2026-09-12 21:30 · 方法：`ai-dev/skills/nop-platform-conformance-audit-prompt.md` 模板 + `docs-for-ai/` 权威文档
> 本文件回答两个问题：**对照什么规则审**（§1），以及**平台自身参照实现长什么样**（§2）。所有深审发现的定性都以 §2 的基线为准绳。

---

## 1. 最佳实践检查清单（规则出处）

| # | 规则 | 出处 |
|---|------|------|
| R1 | 决策顺序 Model → Delta → Java；不手改 `_` 前缀生成物与 `_gen/` | `00-start-here/ai-defaults.md` |
| R2 | DSL 优先：能配置化（XDef/XDSL/字典/状态机）的不硬编码 Java | `02-core-guides/architecture-principles.md` §三 |
| R3 | 不自建已有能力：nop-search / nop-rule / nop-wf / nop-job / nop-message / nop-batch / nop-retry / nop-credential / nop-report | `02-core-guides/architecture-principles.md` §四 |
| R4 | 实体服务 `extends CrudBizModel<T>`；`@BizQuery` 查询 / `@BizMutation` 变更；方法末参 `IServiceContext`；参数 `@Name` | `02-core-guides/service-layer.md` |
| R5 | 取数优先 `requireEntity()/doFindList()/doFindPage()`，不以 `dao().getEntityById()/findAllByQuery()` 为模板 | `04-reference/safe-api-reference.md` |
| R6 | 跨实体访问：业务代码注入 `I*Biz`（走权限/Meta 管道）；`IDaoProvider`/`IOrmTemplate` 仅限 store/infra 底层代码，降级须注释说明 | `04-reference/safe-api-reference.md`、`00-start-here/ai-defaults.md` |
| R7 | BizModel public 方法必须同步 `I*Biz` 接口；接口方法必须有 `@BizQuery/@BizMutation/@BizAction` | `02-core-guides/service-layer.md` |
| R8 | BizModel 必须对应真实聚合根（有 xmeta）；禁止伪 BizModel、`*Service`/`*Controller` 命名 | `02-core-guides/service-layer.md`、`architecture-principles.md` §一 |
| R9 | `@BizMutation` 不叠加 `@Transactional`；`@Inject` 字段不能 `private`；配置注入用 `@InjectValue` | `00-start-here/ai-defaults.md` 硬规则表 |
| R10 | 领域逻辑落位：稳定只读事实 → Entity（`isXxx/canXxx/calculateXxx`）；对外动作/事务入口 → BizModel；多步编排/跨聚合/外部系统 → Processor；共用单步 → Step | `02-core-guides/domain-logic-and-ddd.md` |
| R11 | 实体跨实体只读用 `requireBiz()`（非 `BeanContainer`）；实体不做写操作 | `02-core-guides/domain-logic-and-ddd.md` §2 |
| R12 | 业务状态值优先字典（dict）/声明式状态机，不散落 if-else、不把易变状态固化成 enum | `02-core-guides/domain-logic-and-ddd.md`、`architecture-principles.md` §三 |
| R13 | 异常两层策略：框架核心/公共 API 用 `NopException` + `ErrorCode`（集中声明）；模块内部用模块异常类；禁裸 `RuntimeException/IllegalArgumentException` | `AGENTS.md` Code Conventions、`02-core-guides/error-handling.md` |
| R14 | 所有取当前时间一律 `CoreMetrics`；JSON 用 `JsonTool`；字符串用 `StringHelper`；资源访问走 VFS `IResource`；`getBytes()` 必须带字符集 | `04-reference/common-java-helpers.md`、`00-start-here/ai-defaults.md` |
| R15 | 跨模块实体引用用机制 B（`notGenCode="true"` + `biz:moduleId`）；Maven 依赖单向 DAG | `02-core-guides/cross-module-entity-reference.md` |
| R16 | BizModel 返回值优先 Entity（字段可见性由 xmeta 控制）；汇总/组合数据用 `@DataBean`；不用 `Map<String,Object>` 当复杂返回 | `02-core-guides/service-layer.md` |

---

## 2. 平台基线校准（参照模块实证）

> 参照对象：平台作者编写的 nop-auth / nop-wf / nop-job / nop-task / nop-sys。
> **本节结论直接修正一个常见误读**（见 2.1）。

### 2.1 Processor 的真实基线：复杂流程才拆，不存在"每方法一 Processor"

五个参照模块共 **73 个 BizModel**（auth 31 / sys 20 / wf 14 / job 4 / task 4），全代码库仅 4 个 `*Processor` 类，且均为后台扫描器/事件派发器（`JobCompletionProcessorImpl`、`BroadcastEventProcessor` 等），**没有一个是"业务方法配一个 Processor"的角色**。"Processor 职责"实际由三种形态承担：

1. **BizModel 方法 + private helper 直接编排**（最常见）：如 `nop-auth-service/.../entity/NopAuthUserBizModel.java` 的 `bindMfa` → 私有 `bindTotp/bindSms/bindEmail/bindWebauthn` 分派；`NopJobScheduleBizModel.triggerNow` → 私有 `buildManualFire` + 注入 store。
2. **-core 子模块引擎/Manager**：`nop-wf-core/.../engine/WorkflowEngineImpl.java`、`WorkflowManagerImpl.java`。
3. **-dao 子模块 store/helper**：`nop-wf-dao/.../store/DaoWorkflowStore.java`、`nop-job-dao/.../helper/JobScheduleStateMachine.java`。

**审计定性口径**：超长过程式大方法（>80 行多步编排）是违规；但"没有为每个业务方法建 Processor"不是违规。nop-metadata 的 16 Processor/72 编排类形态合规；nop-ai-agent 的问题在 706 行巨型方法本身，不在"缺少 Processor 类"这个形式。

### 2.2 Entity 领域方法基线（富模型集中于 nop-wf）

- `nop-wf-dao/.../entity/NopWfInstance.java`：`transitToStatus(int)`（迁移+自动追加历史）、`willEnd()/markEnd()`、`addTag/removeTag`。
- `nop-wf-dao/.../entity/NopWfStepInstance.java`：`transitToStatus(int)` 带回退守卫（抛 `NopException(ERR_WF_INVALID_STEP_STATUS_TRANSITION).param(...)`）。
- `nop-auth-dao/.../entity/NopAuthUserSubstitution.java`：`isValid(LocalDateTime)`；`NopAuthResource`：`isTopMenu()/getRoot()`；`NopAuthUser`：`getRoles()` 经关联派生。
- `requireBiz()` 在五个参照模块中 **0 处调用**（机制存在但参照系用"实体自含方法 + BizModel 注入依赖"）。
- **审计定性口径**：实体 0 领域方法 + 状态判断散落 BizModel/Resolver（贫血模型）属于偏离参照系（nop-wf 是标杆），但 nop-auth 多数实体也偏薄——定 P2 而非 P1。

### 2.3 DAO 直接使用的合法层

1. BizModel 基类自带 `dao()/daoFor()`（`nop-biz/.../crud/CrudBizModel.java:256-267`），同实体操作可用；
2. -dao 子模块 store 层（`DaoWorkflowStore` 等）批量事务 + `@Transactional(REQUIRES_NEW)`；
3. 个别遗留/编排 BizModel（`LoginApiBizModel:104` 直接 `@Inject IDaoProvider`）——文档承认的边界场景。
- **审计定性口径**：跨聚合实体访问用 `daoFor()` 而非 `I*Biz`，且无注释说明、且规模成面（如 nop-metadata 85 处）→ P1；有注释的个别降级 → P3。

### 2.4 字典/状态机基线

- dict.yaml 放 `-meta` 子模块 `_vfs/dict/<module>/`（wf-status / schedule-status 等）；int 常量经 codegen 生成 `_NopWfCoreConstants` 镜像。
- 状态迁移守卫三形态：实体方法校验（`transitToStatus`）、final 谓词工具类（`JobScheduleStateMachine` 的 `canEnable/canPause...`）、`.xwf` DSL + xbiz action source（`approval-support.xbiz`）。

### 2.5 异常基线

主线是 **`NopException` + 模块 Errors 接口**（`NopAuthErrors/NopWfErrors/NopJobErrors`...，`ErrorCode.define` 集中声明 + `.param()`），而非自定义异常类；模块 Exception 类仅 3 个且用法特殊。AI 新模块的模块异常类（NopAiException 等）是 AGENTS.md 两层策略允许的形态，但**公共失败必须带 ErrorCode**。

### 2.6 I*Biz 基线

定义在 `-dao` 子模块 `io.nop.<module>.biz` 包，多为空标记接口（`extends ICrudBiz<T>`）；带方法的接口注解直接标在接口方法上（如 `INopJobScheduleBiz.enableSchedule/triggerNow`）。

---

## 3. 参照模块自身偏差（同尺度扫描结果）

参照模块不等于零偏差，以下为实测（src/main，排除 _gen/target/test）：

| 规则 | 结果 |
|------|------|
| `@Inject private` / `@BizMutation+@Transactional` / `new ObjectMapper` / Commons import / Spring `@Value` / `Files.readString` | **0 违规**（全部通过） |
| 裸时间 API | **9 文件约 26 处**：nop-auth MFA store 族（`DbMfaChallengeStore/RedisMfaChallengeStore/DbSmsCodeStore/DbEmailCodeStore` 等 `System.currentTimeMillis()`）、`DaoUserContextCache:96`（`LocalDateTime.now()`，同文件其余处已用 CoreMetrics——双标准）；nop-job 的 `BaseCalendar/ICronExpression` 属 Quartz 移植代码（豁免区） |
| 裸 `IllegalArgumentException` | **11 处**：`WfModelHelper:28`（消息已是错误码风格却用裸异常）、`TaskStepReturn:186`、job scanner/store 族 8 处、`WebAuthnAuthenticator:399` |
| `*ServiceImpl` 命名 | 6 个（`LoginServiceImpl/AuditServiceImpl/OAuthLoginServiceImpl` 等）——均实现内部 `I*Service` SPI 且经 beans.xml 注册，无 Spring 注解，**踩线但合规** |
| `UnsupportedOperationException` | 7 处（`IApprovableBiz` 5 处为设计声明的 fast-fail，javadoc 已说明） |

**结论**：AI 新写模块应以「CrudBizModel + I*Biz 标记接口 + 实体领域方法 + dao 模块 store/helper 承接批量事务 + -meta 模块 dict.yaml + NopException/Errors 接口」为基准；参照模块的 MFA store 裸时间、`IllegalArgumentException` 属于不应被复制的历史偏差。
