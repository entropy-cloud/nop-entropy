# nop-task 实体 CRUD 写保护设计

**日期**：2026-09-26
**范围**：`nop-task/nop-task-meta`（4 个实体 xmeta delta）、`nop-task/nop-task-service`（测试）
**状态**：active
**来源**：plan 364 Phase 1 [维度04-01]（ai-dev/audits/2026-09/2026-09-25-1410-deep-audit-nop-task-quality/04-security.md）

---

## 一、设计结论

1. nop-task 四个实体（NopTaskInstance、NopTaskStepInstance、NopTaskDefinition、NopTaskDefinitionAuth）的**引擎独占列通过 xmeta delta 的 `updatable="false"`（实例状态机列另加 `insertable="false"`）收敛为引擎独占写**；引擎自身经 `OrmEntityDao`（`saveEntityDirectly`/`updateEntityDirectly`）的写路径不经过 biz 校验器，完全不受影响。
2. 运行时强制点是平台已有的 `ObjMetaBasedValidator`：`validateForSave`/`validateForUpdate` 对不可插入/不可更新的 prop 直接从写入口丢弃（`ObjMetaBasedValidator.java:175-177`），`CrudBizModel` 的 `save`/`update`/`saveOrUpdate` 全部经此漏斗（`buildEntityDataForSave:712`、`buildEntityDataForUpdate:990`）。标准 GraphQL CRUD 入口（`update(data, context)` → `doUpdate(data, null, ...)`）不携带 inputSelection，过滤恒生效。
3. **四个对象禁用 `copyForNew`**（BizModel 覆写抛 `ERR_TASK_CRUD_WRITE_DISABLED`，对齐 nop-auth `MfaSensitiveTableBizModel` 先例）：该操作经 Java 反射合并为对外 GraphQL/RPC 操作（`BizObjectBuildHelper.addDefaultAction` + `ReflectionBizModelBuilder`），其 `cloneInstance()` 路径整行克隆源实体全部引擎列后落新行，会静默绕过 insert 锁——正是本设计要堵的伪造入口。`@BizModel(disabledActions)` 只作用于 xbiz 继承面（`BizObjectImpl.isAllowInheritAction`），对反射合并的 @BizMutation 方法无效，故不用。
4. **显式接受 codegen 收缩（nop-task-api 属 AGENTS.md plan-first 区域，plan 364 拥有此变更）**：codegen 绑定在默认构建生命周期（根 pom exec-maven-plugin），xmeta delta 修改后再生成的 `NopTaskInstanceInputBean` 失去 `status`、`NopTaskStepInstanceInputBean` 失去 `stepStatus`，`nop-task-web` 的 nop-task-web 生成视图（_gen 目录下 NopTaskInstance 与 NopTaskStepInstance 的 view.xml）再生（status/stepStatus 退出编辑表单）。这些再生 diff 随本设计提交。
5. 判定原则：**引擎在生命周期中写入的事实性/状态性/诊断性列对 CRUD 不可更新；业务管理员可维护的描述性列（分组、业务键、优先级、标签、备注、管理者、时限）保留可写。**
6. 已知 UX 边界（接受并文档化）：仅锁 update 的列（如 taskName、errCode）仍出现在再生编辑表单中，但更新在写入口被丢弃；管理端如需表单级一致，由手写 view delta 后续收缩（不阻塞本设计）。

## 二、背景与动机

四个实体是裸 `CrudBizModel` 且 ORM 列全部可更新：持有 `NopTaskInstance:mutation` 权限即可把运行中任务改成任意终态（绕过引擎 first-terminal-wins）、改写 `taskInputs`/`stateBeanData` 后借 resume 注入引擎恢复上下文、显式指定 `version` 定向覆写他人并发修改（audit 维度04-01，P1）。实例/步骤状态机列是引擎的记账事实，不属于 CRUD 用户的可写面。

## 三、核心设计

### 保护清单（updatable="false"）

**NopTaskInstance**：`status`（另 insertable="false"）、`taskName`、`taskVersion`、`taskInputs`、`startTime`、`endTime`、`parentTaskName`、`parentTaskVersion`、`parentTaskId`、`parentStepId`、`starterId`、`starterName`、`starterDeptId`、`jobInstanceId`、`workerId`、`errCode`、`errMsg`、`errorBeanData`、`errorStack`、`version`（另 insertable="false"；生成 xmeta 已锁定，显式重申防回退）、`createdBy`、`createTime`、`updatedBy`、`updateTime`（后四列与 version 同为生成值已锁，冗余重申）。

**NopTaskStepInstance**：`stepType`、`stepName`、`stepPath`、`stepStatus`（另 insertable="false"）、`taskInstanceId`、`subTaskId`、`subTaskName`、`subTaskVersion`、`startTime`、`finishTime`、`nextRetryTime`、`retryCount`、`internal`、`parentStepId`、`workerId`、`runId`、`bodyStepIndex`、`stateBeanData`、`errCode`、`errMsg`、`errorBeanData`、`errorStack`、`version`（另 insertable="false"；同上冗余重申）、`createdBy`、`createTime`、`updatedBy`、`updateTime`（冗余重申）。

**NopTaskDefinition / NopTaskDefinitionAuth**：`version`（生成 xmeta 已锁定，显式重申；引擎从不读写这两张表，任务定义从 .task-xml 资源加载）。

保留可写（描述性/管理面）：`taskGroup`、`bizKey`、`bizObjName`、`bizObjId`、`priority`、`signalText`、`tagText`、`remark`、`dueTime`、`manager*`（实例）；`displayName`、`priority`、`tagText`、`remark`、`dueTime`（步骤）。

### 裁定与边界

- **status/stepStatus/version 追加 insertable="false"**：实例行只应由引擎创建；手工经 CRUD 创建实例是伪造终态的入口。status 被过滤后不会进入必填校验（`_validate` 的 filter 检查先于 mandatory 检查），落库时由 DB NOT NULL 约束**响亮失败**（快败优于静默）。其余引擎事实列仅锁 update，不锁 insert，避免破坏生成页面的表单语义。
- **静默丢弃是平台既有语义**：非可更新 prop 在写入口被丢弃（非抛错）与平台对所有非 updatable prop 的处理一致；查询面（queryable）不受影响，读可见性是另一维度（audit 维度04-04 单独裁定）。
- **引擎写路径不受影响的依据**：`DaoTaskStateStore` 全部写操作走 `OrmEntityDao`（dao 层），不经过 `IBizObjectManager`/biz 校验器；xmeta 只约束 biz 层入口。
- 不改 ORM 源模型（`model/*.orm.xml`）、不改 nop-task-api 生成物、不改 xdef——InputBean 中残留的受保护字段在运行时被丢弃，无害。

## 四、拒绝了什么

- **ORM `updatable="false"`**：持久层全局语义——`GenSqlHelper` 生成 update SQL 时对非 updatable prop 直接抛 `ERR_ORM_ENTITY_PROP_NOT_UPDATABLE`（`GenSqlHelper.java:313-315`），会连带打断引擎自身的落盘路径，需要为引擎另行发明绕过通道，改动面横跨持久层。拒绝。
- **保留 copyForNew**：`copyForNew` 经 `cloneInstance()` 整行克隆引擎状态列落新行，静默绕过 insert 锁（live schema 实证其对外可达），与"实例行只应由引擎创建"直接冲突。禁用（`@BizModel(disabledActions="copyForNew")`）。
- **BizModel 逐类覆写 save/update 过滤**：四个类各写一份过滤逻辑，是 xmeta 声明式机制的手工复制品，后续新增实体易漏。拒绝。
- **xmeta 写权限（`write:perm` 字段级权限）**：把"引擎独占"表达成"特定角色可写"，仍然给角色留了写入口，与"引擎独占"语义不符。拒绝。

## 五、codegen 影响（显式认领）

- `NopTaskInstanceInputBean` 失去 `status` setter、`NopTaskStepInstanceInputBean` 失去 `stepStatus` setter（insertable=false 的列按 `InputBean.java.xgen` 的 `insertable || updatable` 过滤排除）；其余仅锁 update 的列 insertable 仍为 true，InputBean 不变。
- nop-task-web 生成视图（_gen 目录下 NopTaskInstance 与 NopTaskStepInstance 的 view.xml）再生，status/stepStatus 退出自动生成编辑表单；手写 view delta（`NopTaskInstance.view.xml` 等）不受影响。
- 仓内无对被删 setter 的调用方（plan-audit 已核实）；上述再生 diff 由 plan 364 Phase 1 提交。

## 五、与已有设计的关系

- 引擎状态机与 first-terminal-wins 语义：plan 349（349-nop-task-analysis-remediation.md）。
- 读出面分级（errCode/errMsg/errorStack/errorBeanData 的 query 暴露）由 plan 364 Phase 5 [维度04-04] 单独裁定，不在本设计内。
