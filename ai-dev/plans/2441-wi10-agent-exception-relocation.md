# 2441 WI10 NopAiAgentException 上移断包环

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI10；包环与 FQN 契约普查 = 2026-10-11 live HEAD 实测
> Draft Review: 2026-10-11 独立子代理对抗审查 round 1（agent_11c650b5）——2 Blocker（契约普查漏 TestFeishuConnector.java:138 / 零残留判据不可满足——历史记录按 Rule #20 排除）+ 4 Major（WI10 被 roadmap 点名须全量 install / design 同步目录指定错漏 gateway 文档 / Phase 1+2 须单一原子提交防破裂 HEAD / 简单名使用计数失真 main 3 + test 32）+ 5 Minor，全部修入正文；round 2（agent_46533758）裁定**可执行**——0 Blocker/0 Major/4 Minor（表述精度：Closure Gate 判据口径/契约计数含 javadoc-only/engine test 32+2 计数/17 文件集合交叠），已修入后转 active
> Related: WI9/plan 2438（错误码归位，completed）——`ERR_AI_AGENT_INVALID_ARG` 留守裁定的重启条件涉及本 WI 的 reliability 归属；`ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md`（在途 19 项全部未启动，涉及 engine/reliability 包消费方文件的 WI 将继承本 WI 的 FQN 变更，无同文件在途冲突）；plan 2440/2439（并发会话，Jackson3/GraalVM，无文件交集）

## Purpose

把 `NopAiAgentException` 与 `SessionIds` 从 `io.nop.ai.agent.engine` 上移到 agent 根包 `io.nop.ai.agent`，使 reliability→engine 方向 import 清零（消除 engine⇄reliability 包级环）、security→engine 方向从 30 条降至 16 条（`AgentExecutionContext` 单一类型的显式登记最小集），并同步全部跨模块 FQN/反射字符串契约。全部错误码/行为零变化——纯 FQN 迁移。

## Current Baseline

> 2026-10-11 live HEAD 全仓 grep 实测。

- `NopAiAgentException`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/NopAiAgentException.java`，31 行）：仅依赖 `nop-api-core`（ErrorCode/NopException），含 4 构造器 + 协变 `param()`（plan 356）。**依赖无关，可无级联上移。**
- `SessionIds`（`engine/SessionIds.java`，102 行）：仅 import `engine.NopAiAgentException` + 根包 `NopAiAgentErrors` + JDK——**随异常类上移后依赖无关。**
- `AgentExecutionContext`（`engine/AgentExecutionContext.java`，469 行）：**不随迁**。其 import 含 `budget.BudgetSnapshot`、`model.*`、`plan.model.AgentPlan`、`security.ChannelKind`/`Principal`、`api/core`；且 `plan.model.AgentPlan` import `plan.runtime.AgentPlanValidator`，plan/runtime 包有 14 个文件 import engine/security——整体上移将制造 root→security 与 root→plan.runtime→engine 新环，把环搬家而非断环。留守裁定见 Adjudication A1。
- 包环计量（main scope，import 语句口径）：security→engine = **30 条**（16 `AgentExecutionContext` + 14 `NopAiAgentException`，27 文件）；reliability→engine = **16 条**（15 `NopAiAgentException` + 1 `SessionIds`，15 文件）；engine→security 144 条/12 文件、engine→reliability 53 条/8 文件为主向编排依赖（保留，roadmap 明示）。
- 跨模块 FQN/反射契约（5 个 java 文件，其中 NopAiCoreException 为 javadoc-only，迁移必须同步）：
  - `nop-ai/nop-ai-gateway/.../channel/feishu/FeishuConnector.java` :346/:382/:383/:539 共 4 处 FQN 硬编码（catch/instanceof/cast/方法签名）。
  - `nop-task/nop-task-dao/.../store/TaskExceptionRegistry.java:69` 反射字符串 `registerReflective("io.nop.ai.agent.engine.NopAiAgentException")`（javadoc 自述部署缺类回退——漏改即静默运行时降级）。
  - `nop-task/nop-task-ext/src/test/.../TestTaskExceptionRegistry.java` 3 处 FQN 字面量。
  - `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/channel/feishu/TestFeishuConnector.java:138` FQN 构造（`new io.nop.ai.agent.engine.NopAiAgentException(`）——gateway test 编译面。
  - `nop-ai/nop-ai-core/.../NopAiCoreException.java:9` javadoc 提及旧 FQN。
- 消费面总量：旧 FQN/`agent.engine.NopAiAgentException` 全仓 java 命中 = agent main 159 文件 + agent test 121 文件 + 上述跨模块 5 文件（含 TestFeishuConnector）；guardrail/rule 子包另有 5 个消费文件随模块内 codemod 覆盖；`agent.engine.SessionIds` 全仓命中 = reliability 1 文件 + session 1 文件（main）+ test 若干（执行时以 grep 全量清单为准）。
- 同包无 import 使用（codemod FQN 替换覆盖不到，须单独处置）：engine **main** 仅 3 文件——`AgentPromptAssembly.java:164` 与 `AgentNames.java:23` 为纯 javadoc `{@link}`（改限定链接，不补 import 以免 unused）、`AgentSessionSupport.java:83` 为真代码调用（补 import）；另有 17 个 engine main 文件带冗余 FQN import（codemod 自动替换；其中 `AgentNames`/`AgentSessionSupport` 2 文件同时属简单名使用组，处置并集覆盖）。engine **test** 32 文件以简单名真代码使用（补 import），另 2 个专用测试文件（git mv 条目单独处置）同属补 import 集合。
- 非代码引用：`deepwiki/nop-task/modules/task-service-dao.md` 提及旧 FQN（文档，顺带更新）；`docs-for-ai/` 两个锚点文件零引用（已核）；`ai-dev/design/nop-ai-agent/` 如有提及执行时 grep 更新。
- 根包 `io.nop.ai.agent` 现仅 `NopAiAgentErrors.java`——两类上移后根包共 3 文件，零新依赖边（异常类→api-core，SessionIds→根包+JDK）。
- Autonomous roadmap 19 项全部未启动（状态复选框全空）；其 WI4/WI5/WI9/WI10/WI14 将改 reliability/engine 包的类（AgentLoopGuard/LlmErrorClassifier 等），均为本 WI 消费方，后动者继承新 FQN，无未决同文件写冲突。

## Goals

- `NopAiAgentException`、`SessionIds` 位于 `io.nop.ai.agent` 根包；旧 FQN 零残留判据（可满足口径）：**live java**（含字符串字面量，全仓）+ **living docs**（`deepwiki/`、`ai-dev/design/`）零命中；`ai-dev/` 历史记录（plans/logs/audits/archived）与 roadmap/plan 自身文本按 guide Rule #20 不回写、不纳入判据。
- reliability→engine main import = 0（engine⇄reliability 环消除）；security→engine main import = 16 条（`AgentExecutionContext` 最小集，显式登记）；包环计量 before/after 登记。
- 跨模块契约同步：FeishuConnector 4 FQN、TaskExceptionRegistry 反射字符串、TestTaskExceptionRegistry 3 字面量、NopAiCoreException javadoc。
- 运行时行为零变化：异常类语义、SessionIds 校验逻辑、TaskExceptionRegistry 反射注册成功路径全部不变。

## Non-Goals

- 不移动 `AgentExecutionContext`（A1 裁定留守）；不拆分其 security 面（ChannelKind/Principal 迁移属后续演进，登记 follow-up）。
- 不消除 engine→security 144 条主向编排依赖（roadmap 明示保留）。
- 不改异常类任何方法签名/构造器/错误码；不改 SessionIds 校验规则。
- 不处理 guardrail/test 包的 9 个消费文件（WI13 将整体移出 main，届时自然继承新 FQN；本 WI 机械更新其 import 即可）。
- 不动 nop-task-dao 的反射注册机制本身（只改字符串参数）。

## Adjudications

### A1 `AgentExecutionContext` 留守 engine（roadmap"连带评估"的评估结论）

- roadmap 要求"连带评估 AgentExecutionContext、SessionIds 中被跨包依赖的部分同批上移"。评估结论：SessionIds 依赖无关、随迁；AgentExecutionContext **不迁**——它 import `security.ChannelKind/Principal`（:10-11、:47-48、:251-263）且经 `plan.model.AgentPlan`→`plan.runtime.AgentPlanValidator` 传递依赖 plan.runtime（该包 14 文件 import engine/security），上移根包将新增 root→security 与 root→plan.runtime→engine 依赖边，使 engine⇄security 环变成 root⇄security 环 + 新增 root→engine 边——断环变搬环。
- 处置：security→engine 剩余 16 条 `AgentExecutionContext` import 登记为显式最小集（roadmap 允许的"降至显式登记的最小集"）。重启条件：若未来将 ChannelKind/Principal 下沉根包或拆分 AgentExecutionContext 的 security 面（Wiring 分离），可重启上移评估。
- Follow-up 登记：AgentExecutionContext security 面分离为 out-of-scope improvement（非本 plan 缺陷）。

### A2 codemod 策略：脚本化全仓替换 + 同包补 import

- 仓库的 `rename-agent-interfaces.py` 为设计文档命名工具，不适用 Java 迁移。执行用一次性 python codemod：①全仓 java FQN/import 字符串替换；②对 `engine` 包内以简单名使用两类且无 import 的文件补 `import io.nop.ai.agent.NopAiAgentException;`/`...SessionIds;`；③跨模块 4 契约点同步（含反射字符串）；④替换后跑 `check-import-order.mjs --module nop-ai` 校验排序并修正。
- 反射字符串 `"io.nop.ai.agent.engine.NopAiAgentException"` 与 FQN 文本形态一致，同一次替换覆盖；`TestTaskExceptionRegistry` 3 处字面量同理。

## Scope

### In Scope

- `nop-ai-agent`：2 文件 `git mv` 至根包 + package 声明更新；全模块 main/test import 与简单名消费补 import；engine 包内 javadoc `@link` 引用更新。
- 跨模块：gateway `FeishuConnector`、nop-task-dao `TaskExceptionRegistry`、nop-task-ext `TestTaskExceptionRegistry`、core `NopAiCoreException` javadoc、`deepwiki/nop-task/modules/task-service-dao.md`。
- 文档：包环计量 before/after 记入本 plan 与当日 log；roadmap WI10 状态行更新；`ai-dev/design/nop-ai-agent/` 若 grep 命中旧 FQN 则更新。

### Out Of Scope

- `AgentExecutionContext` 及其 security 面（A1）。
- engine→security/reliability 主向依赖。
- nop-task-dao 反射机制语义。

## Execution Plan

### Phase 1 - 上移两类 + 模块内 codemod

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/{NopAiAgentException,SessionIds}.java`（自 engine/ 上移）、agent 全模块 main/test

- Item Types: `Fix`

- [x] `git mv` 两文件至根包，package 声明改 `io.nop.ai.agent`；`SessionIds` 的 `engine.NopAiAgentException` import 改根包同包引用（去 import）；`SessionIds` :40-43 "Placed in the engine package..." javadoc 叙事改写为新位置理由（FQN grep 抓不到，专项处理）；专用测试 `TestNopAiAgentExceptionBaseClass`/`TestSessionIdValidation` 留守 engine test 包、仅补 import
- [x] codemod：全仓（nop-ai/nop-ai-agent 范围，**main + test 两 scope**）`io.nop.ai.agent.engine.NopAiAgentException` → `io.nop.ai.agent.NopAiAgentException`、`io.nop.ai.agent.engine.SessionIds` → `io.nop.ai.agent.SessionIds`（import 行 + FQN + 字符串字面量 + javadoc）
- [x] 同包简单名使用处置：engine main `AgentSessionSupport.java` 补根包 import；engine test 32 文件补根包 import；`AgentPromptAssembly.java`/`AgentNames.java` 的 javadoc `{@link}` 改限定链接（不补 import）
- [x] import 排序校验：`node ai-dev/tools/check-import-order.mjs --module nop-ai`（advisory）逐文件修正
- [x] agent 模块编译 + 测试：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（engine 包 109 测试文件为行为基线）

Exit Criteria:

- [x] nop-ai-agent 模块范围 java grep `agent\.engine\.(NopAiAgentException|SessionIds)` 零命中（全仓口径在 Phase 2 收敛——跨模块契约点此时已同步）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] No new test required：纯 FQN 迁移无新行为；既有 109 个 engine 测试文件 + 全模块测试为回归基线（guide Rule #25 注记）
- [x] `ai-dev/logs/` 对应日期条目已更新（收口统一写入）

### Phase 2 - 跨模块契约同步

Status: completed
Targets: gateway `FeishuConnector.java` + `TestFeishuConnector.java`、nop-task-dao `TaskExceptionRegistry.java`、nop-task-ext `TestTaskExceptionRegistry.java`、core `NopAiCoreException.java`、`deepwiki/nop-task/modules/task-service-dao.md`

- Item Types: `Fix`

- [x] FeishuConnector 4 处 FQN（:346/:382/:383/:539）更新；确认 gateway 对 nop-ai-agent 的既有 compile 依赖下 import 可解析
- [x] TaskExceptionRegistry :69 反射字符串更新为新 FQN；javadoc 提及同步
- [x] TestTaskExceptionRegistry 3 处字面量（:43/:113/:143）更新；测试语义基线 = :43 注册集合成员断言 + :113/:143 缺类回退路径（nop-task-ext 无 nop-ai 依赖，Class.forName 必失败）——无 on-classpath 反射构造成功路径测试（该路径由 NopTaskFailException 覆盖），字面量更新后两断言路径语义不变
- [x] NopAiCoreException javadoc 旧 FQN 更新；deepwiki md 更新
- [x] `ai-dev/design/` 全目录 grep 旧 FQN——已知命中 `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md:274`（SINK-02 偏差说明以旧反射契约串为裁定理由，必须同步为新 FQN），其余命中一并更新

Exit Criteria:

- [x] live java（全仓，含字符串字面量）+ living docs（deepwiki/、ai-dev/design/）grep `agent\.engine\.(NopAiAgentException|SessionIds)` 零命中；ai-dev 历史记录（plans/logs/audits/archived）与 roadmap/plan 文本按 Rule #20 排除在判据外
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-gateway,nop-task/nop-task-dao,nop-task/nop-task-ext -am` 全绿（BUILD SUCCESS，全测试类 0 失败 0 错误——计数随日志口径 2320-2411 浮动，含 TestTaskExceptionRegistry 11/11）
- [x] No new test required：同 Phase 1 注记；TestTaskExceptionRegistry 既有断言即为反射契约的 focused test
- [x] `ai-dev/logs/` 对应日期条目已更新（收口统一写入）

### Phase 3 - 包环计量、迁移说明与收口

Status: completed
Targets: 包环扫描、roadmap 状态行、当日 log、plan closure

- Item Types: `Proof`

- [x] 包环计量 after：security→engine 16 条/16 文件（仅 AgentExecutionContext）、reliability→engine 0、engine→security 144 / engine→reliability 53 不变；before/after 已记入本 plan Closure
- [x] 迁移说明（Protected Areas 要求）：本 plan Closure 段已记 FQN 新旧映射表 + 反射契约同步点清单；roadmap WI10 状态行括注登记
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-gateway,nop-task/nop-task-dao,nop-task/nop-task-ext -am` 全绿（含 ext 下游）；按 roadmap Cross-Cutting（WI10 被点名）跑 `./mvnw clean install -DskipTests -T 1C -fae` 一次全量确认——18 个 nop-ai 模块全 SUCCESS；失败恰 2 = nop-tcc-dao/nop-sys-dao（gen-aop-proxy-fail，2437/2439 日志登记的慢性偶发族，两模块 pom 零 nop-ai 依赖，零 WI10 归因）
- [x] roadmap `## Work Item Status` WI10 → `done`（括注：exception+SessionIds 上移、AgentExecutionContext 留守 A1 裁定、环计量 before/after）；重跑状态块解析核对
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`node ai-dev/tools/check-plan-checklist.mjs 2441-wi10-agent-exception-relocation.md --strict` 0
- [x] **单一原子提交**：Phase 1 与 Phase 2 的全部变更一次提交（Phase 1 完成时点 gateway/task-dao/task-ext 编译破裂——旧 FQN 已消失而契约点未同步，禁止中间 commit 污染并发会话的构建基面）；pathspec 限定（严禁 `git add -A`/无 pathspec amend）

Exit Criteria:

- [x] 上述验证全部通过且记入当日 log
- [x] roadmap 状态行更新后解析核对通过（21 WI 条目数不变）
- [x] `ai-dev/logs/2026/10-12.md` 条目含：改动摘要、验证命令与结果、A1 偏差登记、commit hash、doc-sync 裁定（closure 跨零点，按一天一文件规则落 10-12）

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria + 本节全部 `[x]`，且独立 closure audit 通过。

- [x] 两类已上移根包，旧 FQN 按 Goals 判据口径零残留（live java 含字符串 + living docs；ai-dev 历史记录与 roadmap/plan 文本按 Rule #20 排除）
- [x] reliability→engine import 清零；security→engine 降至 16 条显式登记最小集（A1）
- [x] 跨模块契约点全部同步（FeishuConnector 4 FQN / TestFeishuConnector 1 FQN / TaskExceptionRegistry 反射串 / TestTaskExceptionRegistry 3 字面量 / NopAiCoreException javadoc——5 个 java 文件 + 1 个 javadoc）
- [x] 行为零变化：agent+gateway+task 测试全绿（TestTaskExceptionRegistry 反射注册成功路径覆盖）
- [x] 不存在被静默降级的 in-scope live defect（A1 为显式裁定：AgentExecutionContext 留守含依据与重启条件）
- [x] owner docs 已裁定：docs-for-ai 零旧引用（已核）→ 除 roadmap 状态行外 No owner-doc update required；迁移说明落本 plan Closure
- [x] 独立子 agent closure audit 完成且证据写入本文件 Closure 段
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-gateway,nop-task/nop-task-dao,nop-task/nop-task-ext -am` 通过 + `./mvnw clean install -DskipTests -T 1C` 一次全量确认
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2441-wi10-agent-exception-relocation.md --strict` 退出码 0

## Deferred But Adjudicated

### AgentExecutionContext security 面分离（ChannelKind/Principal 下沉或 Wiring 拆分）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: security→engine 16 条为 roadmap 明示允许的"显式登记最小集"；其消解需先做 security 类型归属裁定（涉及 10+ 个 security 文件消费 Principal/ChannelKind），独立演进不影响本 WI 断环目标（engine⇄reliability 已消除、engine⇄security 已降至单类型）
- Successor Required: `no`（WI12 微包收敛顺带评估时复核）

## Non-Blocking Follow-ups

- `ai-dev/design/` 全目录（含 nop-ai-gateway 子目录）残留旧 FQN 的段落随 Phase 2 更新（已知 1 处）。

## Closure

Status Note: NopAiAgentException + SessionIds 上移 agent 根包，reliability→engine 清零（engine⇄reliability 环消除）、security→engine 30→16 条（AgentExecutionContext A1 显式登记最小集）；7 个契约点全部同步；行为零变化（类体相似度 96%/92%，仅 package/javadoc/import）；跨模块测试全绿 + 全量 install 18 个 nop-ai 模块 SUCCESS（仅文档化慢性偶发族 2 失败，零 WI10 归因）。
Completed: 2026-10-12

包环计量（main scope，import 语句口径，2026-10-12 实测）：

| 方向 | before | after | 说明 |
|---|---|---|---|
| security→engine | 30 条/27 文件 | **16 条/16 文件**（仅 AgentExecutionContext，A1 显式登记最小集） | 降 14 条 |
| reliability→engine | 16 条/15 文件 | **0** | engine⇄reliability 环消除 |
| engine→security | 144 条/12 文件 | 144（不变） | 主向编排依赖，roadmap 明示保留 |
| engine→reliability | 53 条/8 文件 | 53（不变） | 单向，无环 |

迁移映射（Protected Areas 迁移说明）：`io.nop.ai.agent.engine.NopAiAgentException` → `io.nop.ai.agent.NopAiAgentException`；`io.nop.ai.agent.engine.SessionIds` → `io.nop.ai.agent.SessionIds`。反射契约同步点：TaskExceptionRegistry:69 注册串、TestTaskExceptionRegistry :43/:113/:143、FeishuConnector :346/:382/:383/:539、TestFeishuConnector:138、NopAiCoreException javadoc、ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md:274、deepwiki/nop-task/modules/task-service-dao.md。

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（agent_0ebd4573，fresh session，2026-10-12）
- Evidence:
  - 10 条验证全 PASS：上移与 rename 在位；旧 FQN 零残留（唯一 live 命中 = ai-dev/audits 历史证据快照，Rule #20 排除）；包环 16/16 文件·0·144·53 与 Closure 表逐格一致；7 契约点全部指向新 FQN
  - 测试/安装证据：跨模块套件 BUILD SUCCESS 全绿（TestTaskExceptionRegistry 11/11）；全量 -fae install 18 个 nop-ai 模块 SUCCESS，失败恰 2 = 慢性偶发族（pom 零 nop-ai 依赖实证）；`test-compile -o` HEAD 抽查 exit 0
  - 行为零变化：git diff -M 相似度 96%/92%，仅 package/javadoc/import
  - Anti-Hollow：根包异常类 main 146 文件 import、606 处 new；SessionIds 三消费方在位；engine test 34 文件 import 齐
  - Deferred 诚实：A1 最小集符合 roadmap :134 许可条款
  - 裁定：APPROVE closure——0 Blocker / 0 Major / 2 Minor（plan :88 勾选遗漏、测试计数口径，均已在收口回写修正）
  - `node ai-dev/tools/check-plan-checklist.mjs 2441-wi10-agent-exception-relocation.md --strict` 退出码 0
