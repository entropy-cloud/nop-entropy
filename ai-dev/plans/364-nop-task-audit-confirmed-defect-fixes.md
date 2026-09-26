# 364 nop-task 审计确认缺陷修复

> Plan Status: active
> Last Reviewed: 2026-09-26
> Source: ai-dev/audits/2026-09/2026-09-25-1410-deep-audit-nop-task-quality/（维度 01~06 六份报告 + 07-verification-addendum-2026-09-26.md 逐条复核判定）；2026-09-26 独立复核与两轮对抗性审查记录见 ai-dev/logs/2026/09-26.md
> Related: 349-nop-task-analysis-remediation.md（前置：终态 driver / 可靠性修复）；365-data-auth-genfrommodules-cross-module-cleanup.md（successor：跨模块 data-auth 坏标签）

## Purpose

把 2026-09-25 nop-task 深度审核中经 2026-09-26 独立子代理逐条复核确认的缺陷（66 CONFIRMED + 3 PARTIAL 的成立部分）修复收口：每条 in-scope 缺陷落地修复并有回归测试防护；纯重构/清理/优化类发现显式裁定出 scope。本计划直接引用审计发现编号（维度NN-XX），不重述证据；复核推翻的子断言在 Current Baseline 记录修正口径。

## Current Baseline

- 复核结论（2026-09-26，8 个独立子代理逐条对 live code 重验证）：69 条发现中 66 条 CONFIRMED、3 条 PARTIAL、0 条 REFUTED。逐条 verdict 与修正口径已落档：`ai-dev/audits/2026-09/2026-09-25-1410-deep-audit-nop-task-quality/07-verification-addendum-2026-09-26.md`。
- PARTIAL 修正记录（修复项口径以本计划为准）：
  - [维度03-02]："每步 7 次往返"是把父 composite 回写摊入子步的建模口径（叶子步骤实为 3 SELECT + 2 写）；"updateEntityDirectly 全列直写"不成立——ORM 按 `orm_dirtyPropIds()` 生成部分列 UPDATE 且同值 setter 不标脏（`JdbcEntityPersistDriver.java:282-290`）。该发现整体移入 Deferred（性能优化组）。
  - [维度06-01]：`TestTaskFlowAnalyzer.java:62-72,108-116` 已断言 `ARG_NEXT_STEP` 诊断参数（plan 255 引入）；盲区收窄为 `TaskStepHelper.newError` 5 个调用点（RateLimit/Throttle wrapper、SequentialTaskStep、LoopNTaskStep、CallStepTaskStep）的参数无断言。
  - [维度06-06]：测试树并非零并发原语（`TestGraphDrainRace` 用 CyclicBarrier+双线程、`TestTaskAuditFixes` 用 CountDownLatch）；缺口收窄为三种交错场景（kill×完成、同 step 行并发 save、SUSPENDED 后 kill）无用例。
- 复核补充的关键事实：
  - [维度01-02]：enter 循环（`GraphStepAnalyzer.java:74` 一带）与 exit 循环同样缺 `ARG_GRAPH_STEP_NAME` 绑定，修复需覆盖两处。
  - [维度04-02]：`XDslExtender.java:349` 在 gen-extends 上下文 `setAllowUnknownTag(true)`，坏标签更可能静默产出空 `<data-auth>`（fail-open）而非抛错；运行时确认仍是 Phase 1 第一项。坏标签为平台级断裂：live 树另有 11 个 app 配置（加 nop-task-app 共 12）+ 2 份 auth docs + 1 个 xgen 模板未同步，已开 successor 计划 365 承接。
  - [维度01-04]：`DaoTaskStateStore.java:456` 的 `setErrMsg` 写的是 step 表 ERR_MSG VARCHAR(4000)，task 级 VARCHAR(500) 上限核对只涉及 `:203`。
  - [维度01-12]：零引用常量实为 10 个——该项已整体移入死代码清理组，不入修复 scope。
- 04-01 机制约束（对抗性审查发现）：`ORM updatable="false"` 是"谁都不能写"——`GenSqlHelper.java:313-315` 在生成 update SQL 时对非 updatable prop 直接抛 `ERR_ORM_ENTITY_PROP_NOT_UPDATABLE`，引擎经 `DaoTaskStateStore` → `updateEntityDirectly` 的落盘路径会一并被打断。因此 04-01 的保护机制必须先裁定（Phase 1 Decision 项），不能默认套用 updatable="false"。
- 锚点修正（不影响缺陷成立）：first-terminal-wins 承诺原文在 `docs-for-ai/03-modules/nop-task.md:45`；`nop_task_step_instance` 的 `versionProp` 在 `nop-task/model/nop-task.orm.xml:259-262`。
- 测试基线：2026-09-25 `./mvnw -f nop-task/pom.xml test` → 263 tests / 0 failures（audit `_tmp/audit-baseline.txt`）；git status 确认此后 nop-task 无未提交代码变更。
- 保护区域：Phase 1（04-01 视裁定机制）与 Phase 3（03-01）涉及 `nop-task/model/nop-task.orm.xml`（ORM 模型结构，AGENTS.md plan-first 区域）；Phase 2 的 03-05 若裁定为方案 B（改 task.xdef）涉及 nop-xdef（框架核心，plan-first 区域）。本计划即 plan-first 载体；对应变更实施前需完成 plan audit。

## Goals

- 两条 P1 安全缺陷修复并有回归测试：04-01（CRUD 全列可写 → 引擎独占列收敛）、04-02（data-auth 死标签 fail-open）。
- 已确认缺陷全部修复并带针对性测试：错误诊断（01-01、01-02、01-03、01-19）、配置校验与契约（02-06、03-05）、持久化正确性（01-04、03-01、03-04、05-02、05-05）、终态与并发正确性（02-01、02-02、02-03、02-13/01-10、05-01 含 02-12、05-03、05-04、05-06、05-07）。
- 06-06 三种并发交错用例随对应修复落地。
- 授权假象与未声明边界完成裁定并同步 owner 文档（04-03、04-04、04-05、04-06、03-06、02-11 javadoc）。
- 测试有效性缺陷快修（06-03、06-04、06-05、06-11；06-01 窄口径随 01-01 落地）。

## Non-Goals

- 纯可读性/重构/死代码/性能优化/测试基础设施类发现不做（逐条见 Deferred But Adjudicated），不在缺陷修复 phase 中夹带。
- 其它模块的 `app.data-auth.xml` 坏标签批量修复不在本计划——已开 successor 计划 365 承接（含 codegen 模板与 docs，防止新模块再生坏标签）。
- 不新增功能：任务定义鉴权 enforcement（04-03）、任务表租户列建模（04-05）、data-auth 字段级裁剪实现（04-04，除非裁定为落地）、限流 key 租户维度（04-06）——本计划只完成裁定与文档同步，实现属 successor。

## Scope

### In Scope

- 审计发现编号：04-01、04-02、04-03（裁定+标注）、04-04（裁定）、04-05（文档）、04-06（文档）、01-01、01-02、01-03、01-04、01-19、02-01、02-02、02-03、02-06、02-11（javadoc）、02-12（并入 05-01 修复项）、02-13（=01-10，同一发现双编号）、03-01、03-04、03-05、03-06（文档）、05-01、05-02、05-03、05-04、05-05、05-06、05-07、06-01（窄口径）、06-02、06-03、06-04、06-05、06-06、06-11。
- 对账口径：69 条编号 = In Scope 列出 36 个编号 + Deferred 列出 32 个编号 + 1（01-10 与 02-13 为同一发现，清单只列 02-13，01-10 由其代表）；唯一发现 67 = In Scope 36 + Deferred 31（Deferred 的 32 个编号中 01-15 与 02-09 为同一发现双编号）。全量对账无遗漏。

### Out Of Scope

- Deferred But Adjudicated 全部条目；Non-Goals 所列跨模块与 successor 工作。

## Execution Plan

> Phase 依赖：Phase 3 先于 Phase 4（Phase 3 的 05-02 修复是交错测试②的前提）；其余 Phase 相互独立，可并行。

### Phase 1 - P1 安全缺陷（04-01、04-02）

Status: completed
Targets: `nop-task/model/nop-task.orm.xml`（视 04-01 裁定机制）、`nop-task/nop-task-app/src/main/resources/_vfs/nop/task/auth/app.data-auth.xml`、`nop-task-service`、`nop-task/nop-task-app`（新建测试树）

- Item Types: `Decision | Fix | Proof`

- [x] [04-02][Proof] 运行时确认修复前失败模式（解析抛错 vs 空模型 fail-open），结论记入 daily log 作为基线证据 —— **实测为第三种模式：解析直接抛 `ERR_XLANG_XDSL_NODE_UNEXPECTED_TAG_NAME`（无 xpl:lib 的未知标签被 DSL 节点校验拒绝），非静态推测的静默空模型；每次 data-auth 检查都会抛错**
- [x] [04-02][Fix] `app.data-auth.xml` 改用现存的 gen 标签（`GenDataAuthFromModules`）并补 `xpl:lib="/nop/auth/xlib/auth-gen.xlib"`，展开后包含 nop-task 模块的 `<objs>` 内容
- [x] [04-02][Proof] 新增回归测试（06-02：nop-task 的 data-auth 配置首次进入测试覆盖）：在 nop-task-app 新建测试树（补最小测试依赖），直接 parseFromResource 该 data-auth 文件，断言解析成功且 gen-extends 展开非空（防止再断裂 19 个月无信号）
- [x] [04-02][Proof] 接线验证：运行时 `DefaultDataAuthChecker` 经 `data-auth-config-path` 真实加载修复后的模型，`isPermitted`/`getFilter` 能看到 nop-task 模块的 objs（非仅 parse 层自证）—— **getFilter("NopAuthUser") 产出 tenantId 行级过滤（TestTaskAppDataAuthConfig 2/2 绿）**
- [x] [04-01][Decision] 裁定引擎独占列的写保护机制并记录到 `ai-dev/design/`。候选：(a) CRUD 入口收敛——BizModel 更新入口剥离/拒绝引擎列 + api InputBean/xmeta 暴露面收缩，引擎 ORM 写路径零改动；(b) ORM `updatable="false"` + 引擎写路径改造（注意 `GenSqlHelper.java:313-315` 对非 updatable prop 抛错，需评估引擎改走何种写路径及对其它模块的影响）；(c) xmeta 写权限维度 —— **裁定方案 (a) 变体：手写 xmeta delta `updatable/insertable=false`（平台原生 ObjMetaBasedValidator 强制）+ copyForNew 覆写禁用；独立 plan-audit 子代理批准（2 CONDITION 已落实：codegen 收缩显式认领、copyForNew 机制实测修正为覆写抛错——disabledActions 对反射合并方法无效）；设计文档 ai-dev/design/crud/nop-task-entity-write-protection-design.md**
- [x] [04-01][Fix] 按裁定机制落地：实例/定义表的状态机列与引擎数据列（status、stepStatus、version、taskInputs、stateBeanData、taskVersion 及同类）对 CRUD 不可写，展示性字段保留可写
- [x] [04-01][Proof] 回归测试：经 CRUD update 携带 status 改写不生效（被拒或忽略）；`DaoTaskStateStore` 引擎写状态路径经测试证明不受影响 —— **TestTaskAppCrudWriteProtection 5/5 绿：update 丢 status 保 remark、save 响亮失败、dao 直写不受影响、copyForNew 禁用、xmeta merge 断言**
- [x] [Fix] service 层补 1 个 CRUD 字段写入用例，钉死 04-01 修复面 —— **由 nop-task-app 集成用例覆盖（service 模块无测试基础设施，用例落于 app 模块真实 GraphQL 面）**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 04-02 失败模式有运行时结论并记入 daily log；修复后 parse 回归与接线验证均通过
- [x] 04-01 机制裁定记录在 `ai-dev/design/`；状态机列经 CRUD 不可写、引擎写路径不受影响的测试证据在档
- [x] 本 Phase 涉 ORM 源模型/xmeta/api bean 的变更已完成 plan audit（AGENTS.md 保护区域要求）
- [x] 若 CRUD 写路径契约变化影响使用方：`docs-for-ai/03-modules/nop-task.md` 已同步；否则写明 No owner-doc update required —— **owner doc 核心实体节已补 CRUD 写保护说明**
- [x] `./mvnw test -pl nop-task -am` 通过 —— **`./mvnw -f nop-task/pom.xml test` BUILD SUCCESS（263 基线 + 新增 7 用例全绿）**
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 错误诊断与配置校验（01-01、01-02、01-03、01-19、02-06、03-05）

Status: completed
Targets: `nop-task-core`（TaskStepHelper、GraphStepAnalyzer、TaskImpl、TaskStepEnhancer、TaskErrors）、`nop-kernel/nop-xdefs`（task.xdef，视 03-05 裁定）、`nop-runner/nop-cli-core` i18n（01-19）

- Item Types: `Fix | Decision | Proof`

- [x] [01-01][Fix] `TaskStepHelper.newError` 两个重载改为真正返回 `NopException`（去除方法体 `throw`），使 5 个调用点的 `.param(...)` 链生效 —— **已核实全部 8 个消费点兼容返回语义（5 个外部 `throw` 调用点 + 3 个内部 `return`/lambda 消费点）**
- [x] [01-01][Proof] 回归测试：至少一个调用点断言 `getParam` 含预期键（如 rate-limit 的 ARG_KEY），且错误消息渲染后不含未替换的 `{...}` 占位符 —— **TestTaskErrorParamDiagnostics.newError_returnsException_paramsReachable_messageRendered（FakeStepRt 单元契约 + 流程级消息渲染断言）**
- [x] [01-02][Fix] `GraphStepAnalyzer` enter/exit 两处 unknown-step 校验补齐 `ARG_GRAPH_STEP_NAME` + 正确 `ARG_STEP_NAME` 绑定；exit 循环变量改名 `exitStep`
- [x] [01-03][Fix] `TaskImpl.checkInputs` 的 `ARG_STEP_PATH` 改传真实步骤路径（或删除该 param，保留 taskName 定位）—— **传 `TaskConstants.MAIN_STEP_NAME`（与 DaoTaskStateStore 的 main 步路径口径一致）**
- [x] [01-02/01-03][Proof] 参数断言测试覆盖上述两处错误码 —— **unknownGraphEnterStep/unknownGraphExitStep/mandatoryInputError_stepPathIsMainStepPath（新增 test/graph-unknown-exit、graph-unknown-enter、task-mandatory-input-empty 三个测试任务）**
- [x] [01-19][Fix] 错误码 ID `should-no-be-async` 拼写更正，同步 en/zh-CN 两份 i18n；落地前确认无历史持久化消费依赖（errCode 列仅诊断用途）—— **全仓 grep should-no-be 残留 0**
- [x] [02-06][Fix] first-class 与 decorator 两路径配置校验语义对齐，显式矩阵：timeout 负值 → 两路径均抛配置错误；timeout 0/缺省 → 两路径均视为"未配置"（first-class 的 int 缺省即 0，无法区分显式 0，decorator 的显式 0 随"负值抛错"一并归入配置非法并在此注明）；rateLimit 节点存在但 requestPerSecond<=0 → 两路径均抛；retry 负值 → 两路径均抛（补齐 first-class buildRetryPolicy 校验）—— **TaskStepEnhancer 新增 invalidStepConfig 校验 + TaskErrors.ERR_TASK_STEP_CONFIG_INVALID**
- [x] [02-06][Proof] 测试：按矩阵断言两路径一致——timeout 负值两路径均抛；timeout 0 在 first-class 不抛（缺省=未配置，显式 0 不可区分）、在 decorator 抛（显式配置必须为正，矩阵注明项）；rateLimit 节点存在时 <=0 两路径均抛、retry 负值两路径均抛且 0 合法 —— **TestFirstClassConfigValidation 4/4；decorator 侧由既有 TestReliabilityDecorators honestFail_* 锁定**
- [x] [03-05][Decision] 裁定 per-step `saveState` 契约：方案 A 实现消费（显式 false 的步骤跳过 saveState 落盘）；方案 B 从 task.xdef 删除 per-step 承诺。裁定与理由写入 `ai-dev/design/`，实现 xdef、owner 文档、运行时行为三者一致 —— **裁定方案 A：TaskStepExecution 构造器注入 persistState（Boolean），门控 ACTIVE/挂起/终态三个落盘点；未配置（null）行为不变；不门控 load（显式 false 不再产生行，load 自然走新建）；设计文档 ai-dev/design/nop-task/task-save-state-contract-design.md；零接口变更（拒绝改 newStepRuntime 签名）**
- [x] [03-05][Proof] 按裁定结果落地验证：方案 A 补"显式 false 步骤不产生 DB 写"测试；方案 B 补文档断言 + 存量带 saveState 模型可加载的回归证据 —— **TestStepSaveStateConsumption（ext，DB 级）：saveState=false 步骤无状态行 + 未配置兄弟步骤有行（对照组）**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 上述每项 Fix/Decision 有对应测试证据（引用测试类名/用例名）
- [x] 01-01 修复后：`TaskStepHelper.newError` 方法体无 `throw` 语句；5 个调用点的参数断言测试在档（测试树可检索到对应 ARG_* 断言）
- [x] 03-05 裁定记录在 `ai-dev/design/` 且 xdef/文档/行为一致
- [x] owner 文档裁定：02-06/03-05 若改变用户可见行为 → `docs-for-ai/03-modules/nop-task.md` 已更新；否则写明 No owner-doc update required —— **owner doc"步骤怎么写"节补可靠性属性取值约束 + saveState 语义**
- [x] `./mvnw test -pl nop-task -am` 通过（若改 task.xdef 或 nop-cli-core i18n，加跑受影响模块）—— **03-05 裁定为方案 A 未改 task.xdef；i18n yaml 为静态资源，`./mvnw -f nop-task/pom.xml test` BUILD SUCCESS（ext 118 含新增 TestStepSaveStateConsumption）**
- [x] 若 03-05 裁定为方案 B：task.xdef 变更已完成 plan audit（nop-xdef 保护区要求）—— **不适用（裁定为方案 A，task.xdef 零变更）**
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 持久化正确性（01-04、03-01、03-04、05-02、05-05）

Status: completed
Targets: `nop-task-dao`（DaoTaskStateStore）、`nop-task-core`（TaskStepHelper retry、TaskStepStateBean）、`nop-task/model/nop-task.orm.xml`

- Item Types: `Fix | Proof`

- [x] [01-04][Fix] `DaoTaskStateStore` REMARK 写入守卫阈值改为与列 precision 一致的具名常量（200），替换裸字面量 4000；task 级 `setErrMsg`（`:203`）对 ERR_MSG VARCHAR(500) 的上限核对并处置 —— **REMARK_MAX_LEN=200 守卫 + 超长跳过告警；读取侧 parse 失败降级补 WARN；TASK_ERR_MSG_MAX_LEN=500 截断（完整诊断仍在 errorBeanData）；step 表 ERR_MSG 为 VARCHAR(4000) 无需截断（复核修正）**
- [x] [01-04][Proof] 回归测试：结果 JSON 长度 199/200/201 三档的写入与 resume 读取行为正确（不截断成非法 JSON、不 DB 报错）—— **TestDaoTaskStateStoreRemarkBoundary 4 用例（199/200 落库、201 跳过、errMsg 800→截断 500 且 errorBeanData 全量）**
- [x] [03-01][Fix] 源模型 `NopTaskStepInstance` 增加查找索引（taskInstanceId + stepPath），重新生成 DDL/ORM 生成物（不手改生成文件）—— **plan-audit 强制条件改单列：`IX_TASK_STEP_TASK_ID (taskInstanceId)`——stepPath VARCHAR(2000) 在 MySQL utf8mb4 复合键超 3072 字节上限不可部署；实测再生成 DDL 不含二级索引（平台 CreateTable 限制，审计勘误），已按审计条件新增 3 方言存量库迁移脚本 deploy/sql/*/upgrade-nop-task-step-instance-index.sql；_app.orm.xml 已再生含 `<indexes>`**
- [x] [03-01][Proof] 生成 DDL 中存在对应索引的证据在档 —— **按审计条件改写：ORM 模型断言测试 TestTaskOrmModelIndex（_app.orm.xml 含索引且单列 taskInstanceId）+ 3 方言 upgrade 脚本在档 + owner 文档登记**
- [x] [03-04][Fix] `TaskStepStateBean.getStateBean` Map 分支转换结果写回 `stateBean` 字段
- [x] [03-04][Proof] resume（Map 形态 stateBean）场景测试：同一 runtime 多次 getStateBean 转换结果稳定（断言写回后类型/内容一致）—— **TestTaskStepStateBeanGetStateBeanWriteBack（写回后第二次调用走 isInstance 快路径返回同一实例）**
- [x] [05-02][Fix] 持久化模式下同一 step 行并发写不再以乐观锁异常冒泡为分支失败：进程内按 (taskInstanceId, stepPath) 串行化，或有限次重读-合并-重试 —— **saveStepState 采用 64 槽条带锁（有界内存，不同行可能共享槽位过度串行化——持久化模式低频写可接受）；跨进程并发写仍由 owner 已知边界声明**
- [x] [05-02][Proof] 交错测试②：同 key 并发 saveStepState（双线程齐射）不抛 OrmException 且状态不丢 —— **TestDaoTaskStateStoreConcurrentSameRow：双线程各 50 次齐射零异常 + version 精确推进到 100（无丢更新）**
- [x] [05-05][Fix] retry 计数持久化顺序调整为"增量先落盘、再进入下一轮"，消除 crash 窗口重置预算 —— **复核确认顺序本已"增量→落盘→下一轮"；落点修正为：saveState 失败时 LOG.error 标记内存/DB 漂移后抛出（sync/async 两路径对称），崩溃窗口文档化**
- [x] [05-05][Proof] 测试：fail 之后、下一轮执行之前 store 中 retryAttempt 已递增（可用录制型 store 断言调用顺序）—— **TestTaskStepHelperRetryAttemptPersistOrder：录制型 runtime 断言 save 序列 [1,2] 且每轮 action 入口所见 attempt 均为上一轮已落盘值**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 上述每项修复有对应测试证据
- [x] 本 Phase ORM 源模型变更（03-01）已完成 plan audit —— **独立子代理审计批准（有条件），3 项强制条件全部落实：单列索引形态、Proof 目标改写、3 方言迁移脚本**
- [x] owner 文档裁定：05-02 将"运行期并发写同 step 行"从未声明变为已处理 → 补进 `docs-for-ai/03-modules/nop-task.md` 已知边界；其余写明 No owner-doc update required —— **已知边界补条带锁说明 + 03-01 索引与存量库 upgrade 脚本登记**
- [x] `./mvnw test -pl nop-task -am` 通过 —— **`./mvnw -f nop-task/pom.xml test` BUILD SUCCESS**
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 终态与并发正确性（02-01、02-02、02-13、05-01、05-03、05-04、05-06、05-07、06-06）

Status: completed
Targets: `nop-task-core`（TaskStepBuilder、TaskStepExecution、TaskStepStateBean、TaskImpl、TaskRuntimeImpl、TaskFlowManagerImpl、GraphTaskStep、GraphStepBuilder、ITaskStepState）

- Item Types: `Fix | Proof`

- [x] [02-01][Fix] `<simple bean="X">` 多步骤共享实例不再互相覆写配置：每步骤的 location/concurrent/persistVars 按各自模型生效 —— **新增 SimpleBeanTaskStep 包装：容器 bean 仅作执行体委托，initAbstractStep 配置落在包装**
- [x] [02-01][Proof] 测试：两个步骤引用同一 bean、persistVars/concurrent 不同，运行期各自生效互不串扰 —— **TestSimpleBeanStepIsolation：两包装独立 + 共享单例零改写**
- [x] [02-02][Fix] sync/async 两出口失败驱动统一为单一实现；nextOnError 配置下两条路径的 XPL stack 可观测性一致（对齐 sync 出口现语义）—— **抽取 driveStepFailure（cancel/FAILED 两分支共用），async 出口 addXplStack 不再位于 nextOnError 分支之后**
- [x] [02-02][Proof] 回归测试：sync 与 async 失败 + nextOnError 均断言异常含 XPL stack —— **TestPhase4TerminalAndConcurrency.sync/asyncFailure_withNextOnError_xplStackObservable（修复前 async 为 null）**
- [x] [02-13/01-10][Fix] `GraphStepNode` 构造器对入参集合防御性复制，不再改写模型 waitSteps/waitErrorSteps
- [x] [02-13][Proof] 测试：构建 graph 后模型集合与 DSL 原文一致（交集信息不丢失）—— **TestGraphStepNodeDefensiveCopy（交集元素保留 + 归一化正确）**
- [x] [05-01/02-12][Fix] 终态守卫补全：step 级 `fail()` 与 `succeed()` 对称守卫；task 级"判定+写入"收敛为原子入口；`ITaskStepState.fail` 补终态配对契约 javadoc（02-11）—— **fail() 加 isDone 守卫；TaskImpl 四 driver 的判定+写入+落盘收敛到 taskState 监视器原子序列**
- [x] [05-01][Proof] 交错测试①：KILLED 与 COMPLETED 双驱动交替调用，终态与 exception 同源、后到者不覆写 —— **TestPhase4TerminalAndConcurrency.terminalDrivers_alternatingOrder（20 轮两种顺序交替）**
- [x] [05-03][Fix] SUSPENDED 任务被 cancel 时直接驱动 KILLED 终态并落盘，不再等到 resume 才转移 —— **TaskRuntimeImpl.cancel 检测 SUSPENDED 状态同步驱动（synchronized + isTerminal 复查）；ACTIVE 任务仍走 token→driver 路径**
- [x] [05-03][Proof] 交错测试③：suspend 后 cancel，断言状态迁移为 KILLED 且无需 resume —— **TestPhase4TerminalAndConcurrency.suspendedTask_cancel_drivesKilledWithoutResume**
- [x] [05-04][Fix] 全局限流器/信号量不再因缓存驱逐分裂 permit 池（在用实例不被驱逐，或驱逐时告警且新实例继承计数）—— **Caffeine 有界缓存改强引用 ConcurrentHashMap（正确性优先）；容量配置转为准入告警阈值（每注册表一次 WARN）**
- [x] [05-04][Proof] 测试：极小缓存上限强制驱逐场景下 maxConcurrency/速率语义不失效 —— **机制已消除（无驱逐），TestPhase4TerminalAndConcurrency.globalGates_strongRefRegistry_sameInstanceAcrossManyKeys 断言跨 50 个新 key 后同 key 实例恒同源**
- [x] [05-06][Fix] `TaskStepRuntimeImpl` 跨线程字段可见性收敛：`stepState` 加 volatile（或等价可见性保证），类头声明其余字段的访问约束不变式
- [x] [05-06][Proof] No new test required: volatile 可见性无法确定性单测；验证方式为代码审查确认 volatile 修饰与类头不变式注释在档（closure audit 抽查项）
- [x] [05-07][Fix] graph `runningCount==0` 终结判定收敛为单一原子判据，消除对人工调序的依赖 —— **6 处检查收敛到 completeGraphIfDrained 单一判据；错误消费/错误转交路径级联顺序对齐成功路径（级联先于减计数），配套不变式记入 helper javadoc；异步派发残余窗口为理论性（本 harness 同步回调内确定闭合）**
- [x] [05-07][Proof] 交错测试：错误分支 × 并发成功分支组合不误判 ERR_TASK_GRAPH_NO_ACTIVE_STEP（扩展 TestGraphDrainRace）—— **TestGraphErrorSuccessRace：300 轮错误消费×成功竞速零误判且收敛到 exit**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 上述每项修复有对应测试证据或显式 No-new-test-required 理由；交错用例①③在档（②在 Phase 3 Exit Criteria）
- [x] first-terminal-wins 实现与 owner 文档承诺（`docs-for-ai/03-modules/nop-task.md:45`）一致，无 best-effort 残留
- [x] owner 文档裁定：05-03 改变挂起语义 → 补 owner 文档挂起章节；其余写明 No owner-doc update required —— **终态语义节补实现保障说明；挂起恢复节补"挂起任务 kill 即时驱动 KILLED"**
- [x] `./mvnw test -pl nop-task -am` 通过 —— **`./mvnw -f nop-task/pom.xml test` BUILD SUCCESS（592 tests / 0 failures）**
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 测试有效性修复与文档边界裁定（02-03、04-03、04-04、04-05、04-06、03-06、06-03、06-04、06-05、06-11）

Status: completed
Targets: `nop-task-core`（TaskConstants）、`nop-task-web` 管理页 delta 文件（04-03 标注，已确认为手写 `x:extends` 文件非生成物）、`docs-for-ai/03-modules/nop-task.md`、nop-task 测试文件

- Item Types: `Fix | Decision | Proof`

- [x] [02-03][Fix] 手写 `TaskConstants` 状态常量改为引用生成常量（同值单源，漂移编译期暴露）；修正注释指向真实守卫测试（TestPlan349Fixes）或抽出 TestTaskConstantsAlignment —— **10 个状态常量全部别名引用 _NopTaskCoreConstants；注释改为指向真实存在的 TestPlan349Fixes 断言**
- [x] [02-03][Proof] 既有常量对齐断言继续通过（引用重构不改值）—— **TestPlan349Fixes 19/19 绿**
- [x] [04-03][Decision] 消除授权假象：owner 文档 + 管理页标注"NopTaskDefinitionAuth 当前仅登记，不参与执行鉴权"；enforcement 接入列 Non-Blocking Follow-ups（successor ownership）—— **view.xml 头注释 + owner doc 安全边界节**
- [x] [04-04][Decision] 裁定 error 诊断列读出面分级：落地字段级裁剪，或裁定 residual-risk-only 并写入 owner 文档已知边界（附理由）—— **裁定 residual-risk-only：持久化为既定设计（plan 265/266），读出面无裁剪的风险（框架测绘/业务值泄露）已记 owner doc，缓解建议=query 权限授予可信角色**
- [x] [04-05][Fix] owner 文档已知边界补记：任务实例表族无租户列，多租户隔离需应用层过滤
- [x] [04-06][Fix] owner 文档补记 `global` 限流/信号量为跨租户共享语义；租户维度 key 变更列 follow-up 决策
- [x] [03-06][Fix] owner 文档控制结构部分补记：parallel/fork/fork-n/graph 仅聚合 promise，子步需配 executor 才真正并行
- [x] [06-03][Fix] `TestReflectionTaskStepBuilder.testBuild` 补关键映射断言（timeout/concurrent/next/outputs/enterSteps/exitSteps）—— **timeout/concurrent/next/enterSteps/exitSteps 断言落地；过程中新发现 @TaskStepOutput 不被反射构建器消费（记 Non-Blocking Follow-ups）**
- [x] [06-04][Fix] 修正 `TestReliabilityDecorators` 中与 `fail()` 现状矛盾的 stale 注释 —— **改为历史背景表述并指向权威测试 TestTaskStepStateBeanExceptionPersistence**
- [x] [06-05][Fix] `rateLimit_realLimitingFires` 首调断言收紧（首调必过：catch 分支加 fail，或拆为"首调许可/次调拒绝"两用例）—— **首调去除 try/catch（默认抛出即失败）**
- [x] [06-11][Fix] `retry_exhaustedHonestThrow` 收窄为 catch NopException + errorCode 断言，与兄弟用例对齐 —— **锁定 ERR_EXEC_CALL_FUNC_FAIL（xpl 包装的耗尽传播，E2E 路径实际契约）**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 06-03/06-04/06-05/06-11 修复后相关测试通过，断言强度提升可复核 —— **TestReliabilityDecorators 37/37、TestReflectionTaskStepBuilder 绿**
- [x] 04-03/04-04 裁定记录在案（daily log 或 design doc），无未裁定的授权假象残留 —— **owner doc 安全与授权边界节 + view.xml 标注**
- [x] `docs-for-ai/03-modules/nop-task.md` 已知边界/控制结构章节含 04-05、04-06、03-06 三项补记 —— **安全与授权边界节 + 并行语义节**
- [x] 其余纯测试类修复项（02-03/06-03/06-04/06-05/06-11）：No owner-doc update required
- [x] `./mvnw test -pl nop-task -am` 通过 —— **BUILD SUCCESS（592 tests / 0 failures）**
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全量对账：69 条编号 = In Scope 36 + Deferred 32 + 1（01-10 由 02-13 代表）；唯一发现 67 = In Scope 36 + Deferred 31；逐条 landed 或移入 Deferred 区，无遗漏、无未裁定孤儿
- [ ] 所有 in-scope confirmed live defects 已修复
- [ ] focused verification 在档且通过：交错用例①②③、01-01/01-02/01-03 参数断言、04-02 parse 回归与 checker 接线验证、04-01 CRUD 写拒绝
- [ ] 04-01/03-05/04-03/04-04 裁定与 04-05/04-06/03-06 文档同步完成，无 owner-doc drift 残留
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 区逐条可对账到非缺陷类）
- [ ] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow 检查：修复点运行时真实生效——`.param` 真进入异常 params、04-01 机制真拦住 CRUD 写且引擎写路径真通、04-02 后 checker 运行时真加载到非空 objs、并发写真不再冒泡乐观锁异常）
- [ ] `./mvnw compile -pl nop-task -am`（及 03-05/01-19 波及模块 compile）通过
- [ ] `./mvnw test -pl nop-task -am` 全绿
- [ ] 代码规范检查通过（imports 分组、无裸 RuntimeException、错误消息英文）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs 364-nop-task-audit-confirmed-defect-fixes.md --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-task --severity high` 退出码 0

## Deferred But Adjudicated

### 可读性/结构重构组（01-05、01-06、01-07、01-08、01-09、01-11、01-14、01-15/02-09、01-17、02-04、02-05、02-07、02-08、02-14、02-15）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 均不改变运行时行为契约（长方法、重复块、命名、行号注释导航、扩展面不对称、javadoc 反向链接），与缺陷修复混线会放大回归面；其中 01-08（接口参数改名）与 02-07（扩展点注入方式）属 API/设计变更，需独立设计裁定。01-06 的不可达死分支已经复核确认不可达（非 SUSPEND 契约缺口），清理时以复核记录为准，防止按矛盾注释重新立案。
- Successor Required: `yes`
- Successor Path: 后续 nop-task 可读性/结构专项计划（编号待开）

### 死代码清理组（01-12、01-13、01-16、02-10、02-16、02-17）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 零引用删除不影响行为，但涉及公共接口删减（`ITaskStepState.needSave`）与模块删除决策（nop-task-queue、nopTaskExecutionQueue bean 需产品裁定），不宜在缺陷修复 plan 内夹带。注意：02-10 内含锁不对称子项（`addStepCleanup` synchronized 而 `runStepCleanups` 无锁，后者在 TryTaskStepWrapper 有生产调用）——当前因 addStepCleanup 零生产调用方而是潜伏缺陷，successor 必须携带"删除死成员或补齐锁对称"的显式裁定，不得按纯死代码删除处理。
- Successor Required: `yes`
- Successor Path: 死代码清理专项（含 02-16/02-17 的删除-or-接入决策、02-10 锁对称裁定）

### 性能优化组（03-02、03-03、03-07、03-08）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 03-02 复核后"全列直写"子断言不成立、往返计数为口径换算；其余为量化收益未测量的优化项（O(K²) 序列化、sleep 100ms 轮询、反射正缓存）。持久化默认不启用（任务模型 `isDefaultSaveState()` 缺省 false → `newTaskRuntime` 走内存 store，`TaskFlowManagerImpl.java:93`），不构成当前 baseline 的正确性风险。
- Successor Required: `yes`
- Successor Path: nop-task 性能专项（建议先建量化基线再动）

### 可观测性一致性组（01-18、02-18）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 01-18（endStep 形参反命名、3-4 条日志缺 task 上下文）与 02-18（非 global 限流器首配置固化无告警，global 分支已有告警）均为日志/指标一致性增强，不改变任何运行时行为与契约；02-18 的主路径（global）已有 plan 349 告警覆盖，非 global 补告警属同一增强的补齐。形参改名涉及接口签名变更，宜与可读性组一起裁定。
- Successor Required: `yes`
- Successor Path: 并入 nop-task 可读性/结构专项计划

### 测试基础设施组（06-07、06-08、06-09、06-10）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 助手收敛/测试迁移/文件归位/wall-clock 重构均为工程质量项，不影响本计划修复项的契约成立；06-08 中"service 补 CRUD 字段写入用例"已并入 Phase 1。
- Successor Required: `yes`
- Successor Path: nop-task 测试基础设施专项

## Non-Blocking Follow-ups

- **执行中新发现**：`ReflectionTaskStepBuilder` 不消费 `@TaskStepOutput`（反射构建路径的 output/exportAs 映射整体缺失，grep 零处理点；TestReflectionTaskStepBuilder 注释已标注）——独立缺陷修复计划承接。
- **执行中新发现**：`nop-auth-web` 的 `/nop/auth/pages/NopAuthLoginAttempt/main.page.yaml` 页面缺陷（`formModel.layout` 为 null 时访问 `.simpleTable`，经 web.xlib 抛 NopEvalException），`nop.web.validate-page-model=true` 的部署 IoC 启动即失败——跨模块缺陷，独立 bug 修复承接（nop-task-app 测试经 surefire 系统属性规避，pom 注释注明）。
- 跨模块 data-auth 坏标签批量修复：已开 successor 计划 365-data-auth-genfrommodules-cross-module-cleanup.md 承接（12 个 app 配置 + 2 docs + 1 xgen 模板 + 防复发守卫）。
- 04-03 enforcement：在任务执行入口接入 definitionAuth 校验（需设计：鉴权点、缓存、默认策略）——本计划 Phase 5 先消除授权假象（标注），实现属 successor。
- 04-06 tenant 维度 cacheKey：多租户部署需求确认后改 key 构造，或在 xdef 标注共享语义。
- 02-16 `nopTaskExecutionQueue`、02-17 `nop-task-queue`：接入实际执行路径或删除（产品决策，归入死代码清理专项）。

## Closure

Status Note: （完成或关闭时填写）
Completed: （未完成）

Closure Audit Evidence:

- Reviewer / Agent: （关闭时填写：独立审阅者或独立子 agent）
- Audit Session: （关闭时填写 session ID）
- Evidence: （关闭时填写：每条 Exit Criterion / Closure Gate 的 PASS/FAIL + 证据来源 + Anti-Hollow 检查结果）

Follow-up:

- 见 Non-Blocking Follow-ups；无其他 plan-owned work（关闭时复核）
