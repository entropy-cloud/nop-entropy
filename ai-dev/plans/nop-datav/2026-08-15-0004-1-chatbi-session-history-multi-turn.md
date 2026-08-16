# 1 ChatBI 会话历史持久化与多轮对话

> Plan Status: completed
> Mission: nop-datav
> Work Item: D6-1 deferred follow-up — ChatBI 会话历史持久化（多轮对话）
> Last Reviewed: 2026-08-15
> Source: D6-1 plan `ai-dev/plans/nop-datav/2026-08-10-1300-1-chatbi-nl-dataset-query.md` Deferred But Adjudicated「会话历史持久化（跨请求多轮对话上下文）」（classification: optimization candidate，注明「可复用 nop-ai NopAiSession」）
> Related: `ai-dev/plans/nop-datav/2026-08-10-1516-1-nl-dashboard-panel-generation.md`、`ai-dev/plans/nop-datav/2026-08-10-1516-2-ai-screen-generation.md`

## Purpose

把 ChatBI 从无状态单轮升级为服务端持久化会话的多轮对话：用户在同一会话内连续提问时，后续问题可以引用先前轮次的上下文（如「按月份细分」「换成柱状图的数据」），查询结果由服务端会话历史承载，跨请求生效。

## Current Baseline

以下事实均已对照 live repo 核实（2026-08-15）：

- ChatBI 三个 action 全部无状态单轮：`NopDatavChatBiBizModel`（`@BizModel("NopDatavChatBi")`）的 `chatToQuery(question)`（`@BizQuery`）、`chatToDashboard(description)` / `chatToScreen(description)`（`@BizMutation`）。每次调用 `new ChatBiToolCallingLoop(chatService, toolManager)` 后执行 `loop.run(userMessage, systemPrompt, operator, maxIterations, handler)`，请求间不共享任何上下文。
- `ChatBiToolCallingLoop.run(...)` 每次以全新 `ChatRequest` 起始（仅 system prompt + 本轮用户消息），不接收也不产生历史消息列表；结果 `ChatBiResult` 携带 answer/columns/rows/iterations/createdEntityId，无任何会话标识。
- nop-datav-service 对 nop-ai 的依赖刻意收窄为 `nop-ai-api` + `nop-ai-toolkit`（`nop-datav/nop-datav-service/pom.xml:74-85`），**不依赖 `nop-ai-dao`**。
- nop-ai 已有会话实体族：`NopAiSession`（聚合根，`nop-ai/model/nop-ai.orm.xml:935` 起）+ `NopAiSessionMessage`/`NopAiSessionContext`/`NopAiSessionInput`，面向 nop-ai-agent 引擎会话生命周期设计。复用即引入 `nop-ai-dao` 依赖及其语义耦合，是否复用需裁定。
- nop-datav 自身无任何会话/对话实体（`nop-datav/model/nop-datav.orm.xml` 无 chat/session 相关 entity）。
- 该缺口由 D6-1 plan 显式 defer（见 Source），backend 侧可落地，无 flux 依赖。

## Goals

- `chatToQuery` 支持会话化多轮：可选传入会话标识时，历史上下文注入本轮对话；不传时保持现有单轮行为完全不变（向后兼容）。
- 会话历史服务端持久化（每轮用户消息与最终结果落库），跨请求可续接。
- 提供会话管理查询：用户查看自己的会话列表、取回某会话的历史消息；会话可删除。
- 会话归属与隔离：会话仅属创建者，非创建者不可读写（对齐 nop-datav 既有 owner 语义）。
- 历史注入有明确上界（窗口/预算截断策略），防止上下文无限膨胀。

## Non-Goals

- `chatToDashboard` / `chatToScreen` 生成类 action 的多轮化（迭代式改稿「把图表改成柱状图」）——价值真实但引入「生成产物修订」语义，scope 独立，留 successor。
- 前端会话 UI（flux 侧）。
- ChatBI 可见数据集的细粒度权限（独立 watch-only residual，见 1300-1 Deferred）。
- 跨用户会话共享 / 会话协作。
- nop-ai-agent 引擎级会话能力（compaction/timeout/recovery）的引入或重建。

## Scope

### In Scope

- ORM 变更（Protected Area，本 plan 即 plan-first 凭证）：Phase 1 裁定存储选型后的会话/消息模型（datav 自有实体或复用 nop-ai 实体）。
- `NopDatavChatBiBizModel`：`chatToQuery` 会话化改造（可选会话参数，默认单轮不变）+ 会话管理 action（列表/历史/删除）+ 权限点（`nop-datav.action-auth.xml`）；biz 接口按 Phase 1 S5 裁定（现状 ChatBI 无 biz 接口先例，不默认引入）。
- `ChatBiToolCallingLoop`：历史消息注入点（不改变既有单轮调用行为）。
- 配置项：历史注入上界（`NopDatavConfigs`）。
- owner doc：`ai-dev/design/nop-datav/ai-design.md` 增补多轮会话章节。

### Out Of Scope

- 生成类 action 多轮化、前端 UI、数据集权限（见 Non-Goals）。
- 会话内 tool-call 中间轨迹的完整持久化（若 Phase 1 裁定仅存用户消息+最终结果，则中间轨迹不落库）。

## Execution Plan

### Phase 1 - 设计裁定与 owner doc 增补

Status: completed
Targets: `ai-dev/design/nop-datav/ai-design.md`

- Item Types: `Decision`

- [x] S1 存储选型裁定：datav 自有轻量会话实体（如 NopDatavChatSession/ChatMessage，仅存多轮所需最小集）vs 复用 nop-ai `NopAiSession` 实体族（需引入 `nop-ai-dao` 依赖 + agent 会话语义耦合）。裁定须写明拒绝方案及原因（对齐 design doc 惯例）
- [x] S2 多轮上下文构造策略裁定：注入哪些历史（用户消息 + assistant 最终 answer 为最小集；tool-call 轨迹是否纳入）、上界形态（最近 N 轮 / 字符预算 / 两者取小）、超界截断顺序（最老优先丢弃）
- [x] S3 API 形态与会话创建生命周期裁定：`chatToQuery` 增加可选 `sessionId` 参数（缺省单轮）vs 独立会话化 action；返回结构如何携带会话标识供下一轮续接；**会话如何被创建**须三选一显式裁定——(a) 客户端生成会话标识、首轮传入即创建并区分「首轮新建」与「引用不存在会话」（拒绝后者）；(b) 显式 create action；(c) 首轮无参调用自动建会话并返回标识（若选此项须同步修订「缺省参数单轮不变」Goal 措辞）。裁定结果必须使「缺省单轮不变」「引用不存在/已删会话显式抛错」两条约束同时可成立
- [x] S4 会话归属与数据敏感性裁定：owner 字段取值（操作者 userName，经 `NopDatavOperatorResolver.resolveOperator`，对齐 nop-datav 既有 `createdBy`/RLS 的 userName 语义——注意返回的是 userName 非 userID）、历史中的查询结果行数据是否落库（数据行含业务数据，须裁定留存内容与删除语义）、**同会话并发写入时的消息追加顺序语义**（序号列 vs 时间戳，参考 nop-ai `NopAiSessionMessage.seq` 先例）
- [x] S5 会话管理 action 集与权限点裁定：列表/历史/删除三 action 的命名、`@BizQuery`/`@BizMutation` 归属、权限点角色绑定（镜像既有 ChatBI 权限点先例，`nop-datav.action-auth.xml:258-280`）；**是否为 ChatBI 建 biz 接口**须裁定——现状 ChatBI 三 action 无 biz 接口（直接经 `@BizModel` 暴露），若建接口须同步裁定结果类型落位模块（避免 dao→service 循环依赖；不建则维持现状先例）
- [x] `ai-design.md` 增补「多轮会话」章节（最终结论式，含上述裁定与拒绝方案）

Exit Criteria:

- [x] S1–S5 均有明确裁定并写入 `ai-design.md` 对应章节（含 ORM 变更结论，若选自有实体则含实体清单）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No new test required: 纯 Decision/文档 Phase，行为测试落在 Phase 2-3

### Phase 2 - 会话持久化与多轮 chatToQuery

Status: completed
Targets: `nop-datav/model/nop-datav.orm.xml`（若 S1 裁定自有实体）、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavChatBiBizModel.java`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/NopDatavConfigs.java`

- Item Types: `Fix | Decision | Proof`

- [x] 按 S1 落地存储模型（ORM 源模型 + `_gen` 再生成，禁止手改生成物；若 S1 裁定复用 nop-ai 实体，须同步在 `nop-datav/nop-datav-service/pom.xml` 新增 `nop-ai-dao` 依赖并记录该取舍）
- [x] 按 S3 改造 `chatToQuery`：带会话参数时读历史 → 按 S2 构造注入上下文 → 执行 → 本轮消息与结果落会话；不带参数时行为与现状逐字节等价
- [x] `ChatBiToolCallingLoop` 提供历史消息注入点（泛化不改既有单轮语义）
- [x] 历史上界配置项加入 `NopDatavConfigs`，超界按 S2 截断
- [x] focused tests：多轮上下文生效（第二轮引用第一轮实体/口径时，LLM 请求中包含历史——经可观测断言验证，如请求消息列表检查）；上界截断行为；无会话参数时单轮回归不变

Exit Criteria:

- [x] 测试证明：同一会话第二轮请求的 LLM 输入包含第一轮的用户消息与结果（非空壳：历史确实被注入循环）
- [x] 测试证明：不带会话参数的 `chatToQuery` 行为与改造前一致（既有 ChatBI 测试全绿即回归凭据）
- [x] 测试证明：超过上界的历史按 S2 裁定截断（最老丢弃），断言注入内容
- [x] 测试证明：会话历史每轮持久化，跨「请求边界」可续接（两次独立调用同会话）
- [x] **接线验证**：会话读写经真实持久层（非内存 map 假实现），`chatToQuery` 到循环到落库路径连通
- [x] **无静默跳过**：引用不存在/已删除/非本人会话均显式抛错（错误码 + param，`NopDatavErrors` 新增），无静默降级为单轮；会话创建按 S3 裁定语义执行（首轮新建路径有明确定义，非隐式行为）
- [x] ORM 变更经 `./mvnw install` 再生成链路验证（`_gen` 由源模型派生，非手改）
- [x] owner-doc 一致性：实现与 Phase 1 写入 `ai-design.md` 的裁定一致；若实现需偏离裁定，须先回写 `ai-design.md` 对应章节再勾选本项（无独立新增文档更新项，见 Minimum Rules #17）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 会话管理 action 与权限

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavChatBiBizModel.java`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/NopDatavErrors.java`、`nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml`（若 S5 裁定建 biz 接口，另含其落位模块）

- Item Types: `Fix | Proof`

- [x] 按 S5 实现会话列表/历史/删除三 action + 权限点（角色绑定镜像既有 ChatBI 权限点）；biz 接口按 S5 裁定（建或不建，不默认引入）
- [x] 归属隔离：非创建者访问会话显式拒绝（对齐 owner 语义先例）
- [x] 删除语义：会话删除后历史不可再续接（`chatToQuery` 引用已删会话显式报错，与 Phase 2 无静默跳过项一致）
- [x] focused tests：三 action 的正常路径 + 越权路径 + 已删会话路径

Exit Criteria:

- [x] 测试证明：会话列表仅含本人的会话；历史 action 返回按 S1 裁定的留存内容
- [x] 测试证明：非创建者对列表外会话的读写均被显式拒绝（断言错误码）
- [x] 测试证明：删除后再续接/查历史均显式报错
- [x] 权限点在 `nop-datav.action-auth.xml` 中存在且角色绑定与 S5 裁定一致
- [x] **接线验证**：管理 action 经 biz 层入口操作真实持久层
- [x] owner-doc 一致性：会话管理 action / 权限点 / 错误码实现与 S5 裁定一致，无未记录偏离（裁定已由 Phase 1 写入 `ai-design.md`，见 Minimum Rules #17）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端验证与文档收口

Status: completed
Targets: `nop-datav/nop-datav-service/src/test/`、`ai-dev/design/nop-datav/ai-design.md`

- Item Types: `Proof`

- [x] 端到端测试：按 S3 裁定语义开始会话 → 第一轮提问（引用某数据集）→ 第二轮追问（依赖第一轮上下文，如「按月份细分」）→ 断言第二轮请求携带历史且结果落库 → 查询历史 → 删除会话 → 续接报错

Exit Criteria:

- [x] **端到端验证**：从 `chatToQuery` 入口到 LLM 上下文构造到会话落库到历史查询的完整路径测试存在且通过
- [x] `ai-design.md` 章节与实现一致（Phase 1 裁定无漂移）
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 多轮语义与 S1–S5 裁定一致，`ai-design.md` 同步无漂移
- [x] 单轮向后兼容有回归测试保障（既有 ChatBI 测试全绿）
- [x] 归属隔离、上界截断、已删会话路径均有 focused tests
- [x] 不存在被静默降级到 deferred 的 in-scope 项
- [x] 受影响 owner docs（`ai-design.md`）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：会话注入链路（action → 历史读取 → 循环上下文 → 落库）运行时连通，无内存假持久层/空壳注入
- [x] `./mvnw compile -pl nop-datav/nop-datav-service -am` 通过
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 通过（474/0/0）
- [x] checkstyle / 代码规范检查通过（仓库无独立 lint 命令，按 import 分组约定人工核对：新文件均为 io.nop.* → jakarta.* → java.* 分组 + 静态导入最后）

## Deferred But Adjudicated

（本 plan 起草时无预裁定的 deferred 项；执行中产生的延期项须按 guide 归类并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- 生成类 action（chatToDashboard/chatToScreen）的迭代式多轮改造（Non-Goals 显式移出，待本 plan 落地后评估 successor）。
- Cosmetic（closure audit 观察项，非阻塞）：`TestChatBiSessionMultiTurn.java` / `TestNopDatavChatBiSessionE2E.java` 声明 `package io.nop.datav.service.entity` 但位于 `src/test/.../chatbi/` 目录（与既有 `TestNopDatavChatBiE2E` 同模式，javac 合法且通过），后续测试目录整理时可对齐。

## Closure

Status Note: 四个 Phase 全部完成且逐项勾选；24 个新测试用例（9 focused 多轮/截断/回归 + 7 管理聚焦 + 7 RBAC 运行时 + 1 端到端）与既有 450 回归全绿（474/0/0，独立复跑确认）；实现与 `ai-design.md` §10 裁定 S1–S5 一致（S2/S5 的实现级精确化已在 Phase 2 回写）；无 in-scope 项被降级延期；独立子 agent closure audit 判定 CLOSABLE 且无阻塞发现。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（task id: ses_ffd6dd1cbffeG6HC8CDgf70RzJ，fresh session，非实现会话）
- Evidence:
  - Phase 1–4 全部 Exit Criteria：PASS（逐条 file:line 证据，如 ai-design.md:663-798、orm.xml:1188-1306、NopDatavChatBiBizModel.java:110-192、action-auth.xml:280-319、TestChatBiSessionMultiTurn.java:84-93/99-141/193-229、TestNopDatavChatBiSessionRbac 全 7 用例、TestNopDatavChatBiSessionE2E 全生命周期）
  - Closure Gates：全 PASS（含 import 分组人工核对）
  - Anti-Hollow：调用链 chatToQuery(sessionId) → requireSession → loadMessages → buildHistoryContext → loop.run(history) → appendTurn 全链路真实持久层（IDaoProvider，无内存 map/空方法体/no-op）；单轮路径完全绕开会话表并有 0 行断言
  - 独立复跑：`./mvnw test -pl nop-datav/nop-datav-service -am` → 474 tests, 0 failures, 0 errors；`check-doc-links.mjs --strict` 退出码 0（6 warnings 为无关既有项）
  - No-Silent-Skip：不存在/已删/非本人三路径均显式 NopException（两个独立错误码）且断言 LLM 零调用；物理删除经 DB 级断言
  - Deferred 项分类检查：Deferred But Adjudicated 为空，Non-Blocking Follow-ups 仅含显式 Non-Goal 与 cosmetic 观察项，无 in-scope live defect 降级
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（见下方执行记录）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0（0 findings）

Follow-up:

- 生成类 action 多轮化 successor 评估（Non-Goal 移出项）
- 测试目录 cosmetic 对齐（见 Non-Blocking Follow-ups）
- 其余无 plan-owned 遗留工作
