# 2 分享访问安全加固（速率限制 + 访问统计）

> Plan Status: completed
> Mission: nop-datav
> Work Item: D3-2 deferred follow-up — 分享访问安全加固（速率限制 + 访问统计）
> Last Reviewed: 2026-08-15
> Source: 删除生命周期 plan `ai-dev/plans/nop-datav/2026-08-14-2020-1-dashboard-screen-delete-cascade-lifecycle.md` Deferred But Adjudicated「分享访问速率限制」（classification: out-of-scope improvement，裁定「暴力探测属运营期加固，与删除生命周期正确性正交」）+ 分享 plan `ai-dev/plans/nop-datav/2026-08-10-1100-2-dashboard-sharing.md` Deferred But Adjudicated「分享访问点击统计/审计」（classification: optimization candidate）
> Related: `ai-dev/plans/nop-datav/2026-08-10-1100-1-dashboard-permission-and-audit-log.md`（D3-4 审计基线）

## Purpose

为匿名公共分享访问路径补齐生产级防护与可观测性：`getSharedDashboard` 是全 nop-datav 唯一 `publicAccess=true` 的匿名入口，当前对 token 探测与密码暴力尝试无任何速率约束，且匿名访问无留痕、分享创建者无法得知链接被访问情况。本计划把「限流 + 访问统计」两项被 defer 的加固收口到同一访问路径。

## Current Baseline

以下事实均已对照 live repo 核实（2026-08-15）：

- `NopDatavDashboardShareBizModel.getSharedDashboard(shareToken, password)`（`NopDatavDashboardShareBizModel.java:145-165`）为 `@Auth(publicAccess = true)` 匿名 action：`findShareByToken` → enabled 校验 → expireTime 校验 → `verifySharePassword`（`passwordEncoder.passwordMatches` 比对 `passwordHash`，`:196-207`）→ `requireDashboardAlive` → 返回最新快照。**无任何速率限制**：对同一 token 可无限次提交密码猜测（passwordHash 存在时密码可被在线爆破），对不存在的 token 可无限探测。
- share 实体（`nop-datav/model/nop-datav.orm.xml` 的 `NopDatavDashboardShare`）列集为 shareId/shareToken/dashboardId/passwordHash/expireTime/enabled/delFlag/version + 审计字段，**无访问计数/最近访问时间列**。
- 管理侧操作（createShare/revokeShare/toggleShare）已有 D3-4 `GraphQLAuditLogger` 审计 + `NopDatavDashboardShare__*` 审计 pattern（删除生命周期 plan 补齐）；**匿名访问路径无留痕**。
- 平台限流原语已存在：`io.nop.commons.concurrent.ratelimit.IRateLimiter` / `DefaultRateLimiter`（nop-commons，包装 Guava `RateLimiter`，平滑 permits/sec 速率语义，`tryAcquire(permits, timeout)`）。**per-key 限流有平台先例**：nop-ai `ChatServiceImpl`（`ConcurrentHashMap<String,IRateLimiter>` + protected `createRateLimiter` 工厂 seam）、`TaskFlowManagerImpl`（LocalCache + 容量上界配置）。注意 `DefaultRateLimiter` 无失败计数/锁定语义（见 R6），且 `getAcquireFailCount()` 存在返回错误计数器的平台缺陷。无分布式限流设施（集群形态需网关层，非本仓 scope）。
- 两项缺口分别由两份已关闭 plan 显式 defer（见 Source），均为 backend 侧可落地项，无 flux 依赖；defer 理由均为「与当期 plan 正交」，非「无价值」。

## Goals

- 密码暴力防护：对同一 share token 的密码失败尝试按配置阈值限流，超限显式拒绝（专用错误码），窗口过后自动恢复。
- token 探测防护：对匿名访问按「token + 来源」维度施加总速率上限，超限显式拒绝。
- 访问统计：成功访问留痕并可聚合查询——分享创建者在 `listShares` 结果中可见访问计数与最近访问时间（具体形态按 Phase 1 裁定）。
- 全部行为可配置（开关/阈值/窗口），默认值保守安全；限流关闭时行为与现状等价。
- 单轮语义约束：被限流请求快速失败，不消耗查询资源（限流检查与 token 查库/快照读取的次序按 R5 裁定，但被拒请求不得触发快照查询）。

## Non-Goals

- 分布式/集群级限流（进程内 `IRateLimiter` 为单节点语义；多节点部署的限流归属网关/负载层，design doc 显式记录该边界，不在本仓实现）。
- IP 黑名单 / 验证码 / 账户锁定式防护产品化。
- 每次访问的明细审计行（访问者 IP 明细流水）——若 Phase 1 裁定聚合统计即可满足，明细流水不做。
- 分享 CRUD 语义变更、嵌入 iframe（flux 侧）。
- `getSharedScreen` 类大屏分享（当前不存在该 action，不预造）。

## Scope

### In Scope

- `NopDatavDashboardShareBizModel` 访问路径限流（密码失败级 + 总速率级两层）+ 限流错误码（`NopDatavErrors`）+ 配置项（`NopDatavConfigs`）。
- 访问统计落点（按 Phase 1 裁定：share 实体聚合列 `visitCount`/`lastVisitTime` 或等价形态）+ `listShares` 结果暴露。
- owner doc：`ai-dev/design/nop-datav/permission-sharing-design.md` 增补「访问限流与访问统计」章节。
- ORM 变更（Protected Area，本 plan 即 plan-first 凭证）：仅当 Phase 1 裁定聚合列形态时新增 share 实体两列。

### Out Of Scope

- 集群限流、黑名单、验证码、明细流水（见 Non-Goals）。
- 大屏分享、分享 CRUD 变更（见 Non-Goals）。

## Execution Plan

### Phase 1 - 设计裁定与 owner doc 增补

Status: completed
Targets: `ai-dev/design/nop-datav/permission-sharing-design.md`

- Item Types: `Decision`

- [x] R1 限流键与来源识别裁定：键形态（shareToken 单维 vs shareToken+来源 IP 组合）；BizModel 层可达的来源 IP 途径有二——`IServiceContext.getRequestClientIp()`（读 `nop-client-addr` 头，由网关注入）与 web 层 `DefaultClientIpFetcher` 的 `X-Forwarded-For` 解析（仅 HTTP 层可达，service 层拿不到）——须分别评估采信规则与信任边界；取不到来源 IP 时的降级行为（退化为 token 单维，须显式记录）。测试注 IP 可用 `ServiceContextImpl.setRequestHeaders` 先例
- [x] R2 限流存储与生命周期裁定：进程内 per-key 限流器（平台 `io.nop.commons.concurrent.ratelimit`）+ key 淘汰策略（容量上界/LRU，先例：`TaskFlowManagerImpl` 的 LocalCache + 容量配置、nop-ai `ChatServiceImpl` 的 per-key `ConcurrentHashMap<String,IRateLimiter>`），单节点语义与集群边界写入 design doc（多节点部署须依赖网关层限流）。**可测试性硬要求**：限流器创建须经可注入 seam（镜像 nop-ai `ChatServiceImpl.createRateLimiter` protected 工厂 + `FakeRateLimiter` 测试先例），窗口推进经 fake 限流器/时钟驱动，禁止 `Thread.sleep` 盲等式窗口恢复测试
- [x] R3 两级阈值裁定：密码失败级（如 N 次失败/窗口 → 该 token 的密码尝试被拒）与总速率级（如 M 次/窗口）的默认值、窗口、配置项名；超限错误码语义（HTTP 层可观测、与现有 `ERR_DATAV_SHARE_*` 命名对齐）；**密码失败与成功访问是否共用计数器**须裁定（失败专用计数倾向）
- [x] R6 失败锁定语义到限流原语的映射裁定：平台 `DefaultRateLimiter` 包装 Guava `RateLimiter`，是平滑 permits/sec 速率限速器——**无失败计数、无锁定状态**，「达到失败阈值后即使密码正确也被拒 + 窗口过后恢复」的锁定语义无法由裸 `tryAcquire` 表达。须显式裁定实现形态（失败计数器 + 锁定状态 wrapper vs 自定义 `IRateLimiter` 实现 vs 复用 `AiRateLimitGatewayInterceptor` 手写 TokenBucket 先例）并写明所选原语的真实语义。注意：`DefaultRateLimiter.getAcquireFailCount()` 现返回 `acquireSuccessCount`（平台复制粘贴缺陷），测试不得依赖该 stats 断言
- [x] R4 访问统计形态裁定：share 实体聚合列（visitCount/lastVisitTime，ORM 加列）vs 独立访问日志实体；**须显式记录「一行配置得明细留痕」替代方案的考虑**（`GraphQLAuditLogger` 兼容匿名请求 userName="-"，把 `NopDatavDashboardShare__getSharedDashboard` 加入 `audit-query-patterns` 即得每次访问留痕——R4 须写明采纳/拒绝理由）；聚合列写放大与并发更新策略（每次匿名访问触发一次 update 的代价与合并方案）；**匿名统计写入的操作者身份**（`NopDatavOperatorResolver` 匿名回退值，审计列 mandatory 的满足方式）与 version bump 放大语义；统计口径（仅成功访问计数 vs 含被限流请求）
- [x] R5 限流检查次序裁定：限流先于 token 查库（省 DB 压力）vs 先查库后限流（区分「token 不存在」与「被限流」错误语义）——两难须显式裁定并写明理由
- [x] `permission-sharing-design.md` 增补「访问限流与访问统计」章节（最终结论式，含拒绝方案）

Exit Criteria:

- [x] R1–R6 均有明确裁定并写入 `permission-sharing-design.md` 对应章节
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No new test required: 纯 Decision/文档 Phase，行为测试落在 Phase 2-3

### Phase 2 - 访问速率限制

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavDashboardShareBizModel.java`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/NopDatavConfigs.java`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/NopDatavErrors.java`

- Item Types: `Fix | Proof`

- [x] 按 R1–R3/R5/R6 实现两级限流：检查点落在 `getSharedDashboard` 访问路径（次序按 R5），密码失败锁定语义按 R6 裁定形态实现，超限抛专用错误码（含 token param，不含敏感信息）
- [x] 限流器经可注入 seam 创建（按 R2 可测试性硬要求），窗口推进在测试中经 fake 驱动
- [x] 限流配置项加入 `NopDatavConfigs`（开关 + 两级阈值 + 窗口），默认值按 R3
- [x] focused tests：密码连续失败达阈值后被拒（断言错误码）且窗口过后恢复；总速率超限被拒；正常低频访问不受影响；限流开关关闭时行为与现状等价；同一 token 不同来源（按 R1 键形态）互不误伤

Exit Criteria:

- [x] 测试证明：密码爆破路径被截断——达到失败阈值后的尝试即使密码正确也被拒绝（防爆破有效性，非仅计数）（`TestNopDatavShareRateLimit.testBruteForcePasswordLockedThenRecoversAfterWindow`）
- [x] 测试证明：限流拒绝快于快照读取（按 R5 次序断言——被拒请求错误码为限流专用码而非快照/查询错误码，或经 DAO spy 断言零查询）（`testTotalRateLimitRejectsAndRecoversAfterWindow` + `testNonexistentTokenProbingGetsRateLimited` 错误码断言：被拒请求错误码为 share-rate-limited/share-password-locked 专用码）
- [x] 测试证明：限流对不存在 token 的探测同样生效（按 R5 裁定断言其错误语义）（`testNonexistentTokenProbingGetsRateLimited`：前 M 次 TOKEN_NOT_FOUND → 超限后翻转为 RATE_LIMITED）
- [x] 测试证明：默认配置下正常访问路径（浏览+正确密码）零误伤（`testDefaultConfigNormalAccessZeroFalsePositive`）
- [x] **无静默跳过**：被限流请求显式抛错（专用错误码），无静默返回空快照/降级数据（全部拒绝路径断言专用错误码）
- [x] **接线验证**：限流检查在 `getSharedDashboard` 真实调用链上生效（非独立组件存在但未接线）（7 用例全部经注入的 `shareBiz.getSharedDashboard` 真实链路触发限流）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 访问统计与暴露

Status: completed
Targets: `nop-datav/model/nop-datav.orm.xml`（若 R4 裁定聚合列）、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavDashboardShareBizModel.java`

- Item Types: `Fix | Proof`

- [x] 按 R4 落地访问统计：成功访问后按裁定形态记录（聚合列或等价物），ORM 变更走源模型 + 再生成
- [x] `listShares` 返回结果暴露访问计数与最近访问时间（owner 已有 `requireDashboardOwnership` 校验，不新增权限面）
- [x] focused tests：成功访问后计数/时间更新；失败/被限流访问按 R4 口径处理（计数或不计）；owner 经 `listShares` 可见统计；并发访问下计数不丢失（按 R4 并发策略断言）

Exit Criteria:

- [x] 测试证明：两次成功访问后 `listShares` 返回的计数为 2、`lastVisitTime` 非空且晚于访问前（`TestNopDatavShareVisitStats.testTwoSuccessfulVisitsUpdateStatsVisibleInListShares`）
- [x] 测试证明：密码失败的访问按 R4 裁定口径处理（有断言，非未定义行为）（`testFailedAndLimitedAccessDoNotCount`：失败 + 被限流访问不计数，count=0/lastVisitTime=null）
- [x] ORM 变更（若有）经 `./mvnw install` 再生成链路验证（`clean install -pl nop-datav -am`：_gen entity/_app.orm.xml/xmeta/i18n/view/3 方言 deploy SQL 全部再生成，BUILD SUCCESS）
- [x] **接线验证**：统计写入在真实匿名访问路径上发生（非旁路未接线组件）（recordShareVisit 挂在 getSharedDashboard 快照读取成功之后，3 用例全部经真实 shareBiz 调用链断言落库）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端验证与文档收口

Status: completed
Targets: `nop-datav/nop-datav-service/src/test/`、`ai-dev/design/nop-datav/permission-sharing-design.md`

- Item Types: `Proof`

- [x] 端到端测试：创建带密码分享 → 匿名正确密码访问成功且统计可见 → 连续错误密码至阈值 → 被限流（正确密码也被拒）→ 窗口推进后恢复 → 撤销分享后访问被拒（`TestNopDatavShareHardeningE2E.testFullLifecycleShareAccessHardening`，单测试覆盖全链）

Exit Criteria:

- [x] **端到端验证**：从匿名 GraphQL 入口到限流判定到统计落库到 `listShares` 暴露的完整路径测试存在且通过（E2E 经真实 shareBiz 调用链 + 真实持久层 + 真实 guard bean + 真实统计 SQL，快照版本/统计/错误码逐段断言）
- [x] `permission-sharing-design.md` 章节与实现一致（Phase 1 裁定无漂移）（复核通过；补 1 处细节：R5 步骤 5「缺密码不计失败」与实现 verifySharePassword 对齐）
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 全绿（485/0/0）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 限流与统计行为与 R1–R5 裁定一致，`permission-sharing-design.md` 同步无漂移（独立审计 C 项逐条 file:line 比对 PASS：键形态/配置名与默认值/错误码/R5 次序/R4 统计策略全部一致）
- [x] 密码爆破截断、token 探测拒绝、零误伤、统计口径均有 focused tests（`TestNopDatavShareRateLimit` 7 用例 + `TestNopDatavShareVisitStats` 3 用例 + `TestNopDatavShareHardeningE2E` 1 用例）
- [x] 限流单节点语义与集群边界在 design doc 显式记录（防止误部署预期）（R2 节「单节点语义（部署边界，显式记录）」）
- [x] 不存在被静默降级到 deferred 的 in-scope 项（独立审计 D 项 PASS：Deferred 区为空；Non-Blocking Follow-ups 两项均为 plan 起草时 Non-Goal，非降级项）
- [x] 受影响 owner docs（`permission-sharing-design.md`）已同步（「访问限流与访问统计」章节 + R5 步骤 5 细节对齐；check-doc-links --strict 退出码 0）
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session task ses_ffd1b41ccffe6L2tvfc9qIh0Eu，verdict CLOSABLE，见 Closure Audit Evidence）
- [x] **Anti-Hollow Check**：限流与统计在 `getSharedDashboard` 真实调用链上生效，非孤立组件（审计 B 项 PASS：`checkShareAccessRateLimit` 为方法首语句、`recordShareVisit` 在快照读取后；guard bean 注册于 app-service.beans.xml 且测试共享 singleton 经 fake 时钟改变真实行为证明接线；`scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0）
- [x] `./mvnw compile -pl nop-datav/nop-datav-service -am` 通过（BUILD SUCCESS）
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 通过（收口最终复跑 485/0/0）
- [x] checkstyle / 代码规范检查通过（仓库无独立 lint 命令；import 分组约定人工核对：Guard/BizModel/3 测试文件均为 io.nop.* → jakarta/third-party → java.* 组间空行 + 静态导入最后）

## Deferred But Adjudicated

（本 plan 起草时无预裁定的 deferred 项；执行中产生的延期项须按 guide 归类并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- 集群部署形态下的网关层限流配置指引（部署文档性质，待宿主 app 集成时补充）。
- 访问明细流水（IP/时间明细）——若运营期出现审计合规需求再评估。

## Closure

Status Note: 4 Phase 全部完成且独立 closure audit 判定 CLOSABLE——两级限流（R1–R6 裁定形态：组合键 +
进程内固定窗口状态机 + 失败专用计数与锁定）与访问统计（R4 聚合列 + 定向 SQL 原子自增）均在
`getSharedDashboard` 真实调用链上生效，行为与 permission-sharing-design.md「访问限流与访问统计」章节
无漂移；11 个新测试（7 限流 + 3 统计 + 1 E2E）覆盖爆破截断/探测拒绝/零误伤/统计口径/并发不丢失/开关
等价全部 exit criteria；无 in-scope 项降级延期。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，task ses_ffd1b41ccffe6L2tvfc9qIh0Eu）
- Audit Session: ses_ffd1b41ccffe6L2tvfc9qIh0Eu
- Evidence:
  - Phase 1–4 每条 Exit Criterion：PASS（A 项，逐条 file:line / 测试名证据——设计章节
    permission-sharing-design.md:473-561；限流 7 用例 TestNopDatavShareRateLimit.java:79-279；
    统计 3 用例 TestNopDatavShareVisitStats.java:62-136；E2E
    TestNopDatavShareHardeningE2E.java:64-120；ORM 再生成链 orm.xml:541,544 →
    _gen/_NopDatavDashboardShare.java:77-82 → xmeta:76-80 → _app.orm.xml + 3 方言 deploy SQL）
  - 每条 Closure Gate：PASS（见上节勾选，evidence 来源为审计报告 A–E 项）
  - Anti-Hollow：调用链连通（checkShareAccessRateLimit 为 getSharedDashboard 首语句
    NopDatavDashboardShareBizModel.java:179；recordShareVisit :195 在快照读取后；guard bean
    app-service.beans.xml:12-13 注册且测试共享 singleton——fake 时钟改变真实行为证明运行时接线）；
    recordShareVisit 的 catch→WARN 为 design doc :533 显式裁定的 fail-open（非静默跳过）；
    `scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0
  - Deferred 项分类：Deferred But Adjudicated 为空；Non-Blocking Follow-ups 两项（集群网关指引/
    明细流水）均为 plan 起草时 Non-Goal（D 项 PASS，无 in-scope defect 降级）
  - 测试复跑：审计者独立复跑 3 次 485/0/0 BUILD SUCCESS；收口最终复跑 485/0/0 BUILD SUCCESS
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（勾选收口后确认）

Follow-up:

- 集群部署形态下的网关层限流配置指引（部署文档性质，Non-Blocking，见 Non-Blocking Follow-ups）
- 访问明细流水（IP/时间明细，运营期合规需求出现时一行 audit-query-patterns 配置可得）
- 其余无 plan-owned 遗留工作
