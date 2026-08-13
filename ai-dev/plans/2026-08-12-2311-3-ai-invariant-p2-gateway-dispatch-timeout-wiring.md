# 3 AI Invariant Loop — P2 Gateway dispatchTimeoutMs 死旋钮接线（AR-9）

> Plan Status: completed
> Mission: nop-ai-invariant-loop
> Work Item: Cycle 2 / P2 — `dispatchTimeoutMs` 文档声称可配置但零接线（AR-9）
> Last Reviewed: 2026-08-12
> Source: `ai-dev/audits/2026-08-12-1119-open-audit-nop-ai-invariant-loop.md` P2 [AR-9]；roadmap `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md` `## Follow-up Backlog`
> Related: `2026-08-12-2311-1-ai-invariant-p2-engine-split-hygiene.md`、`2026-08-12-2311-2-ai-invariant-p2-gate-test-fixture-hygiene.md`（同批 P2）
> Review: 两轮独立子 agent 对抗性审查（fresh sessions ses_00975d3b5ffe / ses_0096e8a2bffe）——R1 两 Major（无 getDispatchTimeoutMs 观测机制 / assignConfigValue 覆盖断言钉死）+ 3 Minor 全修；R2 复核 Blocker 清零、Major 清零（@cfg 求值链端到端核实、反射观测先例核实、双 test beans.xml 核实），共识 = executable（3 Minor 已吸收）→ draft → active

## Purpose

消除 `ChannelMessageServiceImpl.dispatchTimeoutMs` 的死旋钮：javadoc 声称可由 `nop.ai.gateway.channel.dispatchTimeoutMs` 配置项控制（`ChannelMessageServiceImpl.java:96-100`），但生产装配（beans.xml）无任何接线——运维按文档调配置无效，永远是硬编码默认值 30000。修复 = 真实接线，使配置项生效（docs 承诺兑现），而非更正 javadoc（timeout 值确实需要可调，见 I3 R-2-1 引入背景）。

## Current Baseline

（2026-08-12 23:11 live 核实）

- **缺陷实锤**：`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java`——字段 `private volatile long dispatchTimeoutMs = DEFAULT_DISPATCH_TIMEOUT_MS;`（:105）+ setter `setDispatchTimeoutMs`（:192-197，拒绝 `<= 0`）+ javadoc「`nop.ai.gateway.channel.dispatchTimeoutMs`；setter rejects ...」（:100）；超时实际消费点 = **mode-2 广播 `orTimeout`（:351）与 mode-1 fan-out `orTimeout`（:418）**（:324-345 为 `dispatchInbound` 的 javadoc 区，非消费点）。
- **零接线**：`grep -rn "dispatchTimeoutMs" nop-ai/nop-ai-gateway/src/main/resources/` 零命中；`ai-gateway-defaults.beans.xml` 的 `channelMessageService` bean（:69-82）仅接 `userChannelResolver`/`channelConnectorManager`/`messageService` 三个 property；无 `@InjectValue("@cfg:...")`。
- **装配路径**：bean 定义于 `nop-ai/nop-ai-gateway/src/main/resources/_vfs/nop/ai/gateway/beans/ai-gateway-defaults.beans.xml:69-82`；该文件已有 `<property name="..." value="..."/>` 惯例（如 `frontendLlm` :12-13）。`@cfg:` 表达式语法 `@cfg:name|default` 有仓库 90+ 处生产先例（含同族 `nop-ai-core/ai-defaults.beans.xml:15` `@cfg:nop.ai.service.cache-ttl|0`）；求值链路 = `ConfigExpressionProcessor` → `ConfigValueResolver` → `BeanContainerImpl.getConfigValue` → `AppConfig` 的 configProvider（bean 创建时实时读取）。
- **观测机制（重要）**：`ChannelMessageServiceImpl` **没有 `getDispatchTimeoutMs()`**（全类仅 setter；grep 全 nop-ai 零命中）——测试观测须用**反射读字段**，镜像既有先例 `TestChannelMessageServiceIoC.java:180-188` 的 `readMessageService` 反射 helper；**不新增 public getter**（保持公共 API 不变）。
- **配置覆盖机制**：`IConfigProvider.assignConfigValue(String, Object)`（非 `updateConfigValue(IConfigReference, T)`——本配置键无 `AiGatewayConfigs.CFG_*` 引用常量）；`TestLlmConfigHelper.java:96-101` 先例 = `assignConfigValue` + try/finally 复位。`assignConfigValue` 写入全局 configProvider 静态值，**跨测试同 JVM 泄漏**——必须 finally 复位原值。
- **测试落点**：`nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/channel/TestChannelMessageServiceIoC.java` 已用 `AppBeanContainerLoader` + test beans.xml 从 IoC 容器取 `channelMessageService` bean（:66-84, :108-115）；**`channelMessageService` bean 定义被复制在 mode1 + mode2 两个 test beans.xml**（`test-channel-message-service-ioc-mode1.beans.xml` 与 `test-channel-message-service-ioc-mode2.beans.xml` 均含）——测试装配同步必须覆盖两个文件。`TestChannelFanOutTimeoutCancel`（既有）直接 `setDispatchTimeoutMs(300L)` 覆盖超时行为，不受本 plan 影响。
- **@cfg 测试先例缺失**：全仓库无 test beans.xml 用过 `@cfg:`——执行第一步需验证 @cfg 在测试容器可解析（失败模式 = 测试红而非静默），不可用时换 `-D`/config 注入并记录。
- **授权基线**：P2 自动修复授权 = plan 级裁定（I3 裁定 2026-08-12）；本项为已确认 doc-vs-code drift（配置项承诺落空），归类 `Fix`。不涉及公共 API 变更（`IChannelMessageService` 接口无此成员；setter 已存在，仅加装配接线）。

## Goals

- `nop.ai.gateway.channel.dispatchTimeoutMs` 配置项真实生效：`channelMessageService` bean 经 `@cfg:` 表达式接线，默认值 = 30000（与 `DEFAULT_DISPATCH_TIMEOUT_MS` 一致），运维配置可覆盖。
- IoC 级测试证明接线运行时连通：bean 的 `dispatchTimeoutMs` 默认 = 30000，且 `assignConfigValue` 覆盖后 bean 值随之变化（接线验证规则——property → setter → 字段 → 超时消费点链路连通）。
- javadoc 承诺兑现，doc-vs-code drift 收敛（INV-2 族判定标准 (c) 配置项语义成立）。

## Non-Goals

- **不更正 javadoc 为"固定值"**（audit 建议二选一；选接线因 timeout 可调性是真实运维需求，且 setter 已实现校验，接线成本低于删承诺）。
- **不新增 public getter**（观测用反射，镜像 `readMessageService` 先例）。
- 不修改超时行为语义（默认 30000 与 fan-out cancel 逻辑不变）、不改 `IChannelMessageService` 接口。
- 不处理 AR-5~AR-8（各自 plan）。

## Scope

### In Scope

- `ai-gateway-defaults.beans.xml`：`channelMessageService` bean 新增 `dispatchTimeoutMs` property（`@cfg:nop.ai.gateway.channel.dispatchTimeoutMs|30000`）。
- `TestChannelMessageServiceIoC`（或同包新增 focused test）：接线验证——默认值断言 + `assignConfigValue` 覆盖断言（反射读字段观测）。
- test beans.xml 同步：mode1 + mode2 **两个文件**的 `channelMessageService` bean 复制定义同步补同一 property。

### Out Of Scope

- `ChannelMessageServiceImpl` 超时逻辑、`DEFAULT_DISPATCH_TIMEOUT_MS` 值、fan-out 行为。
- 其他 P2 / backlog 项。

## Execution Plan

### Phase 1 - beans.xml 接线 + IoC 接线验证测试

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/main/resources/_vfs/nop/ai/gateway/beans/ai-gateway-defaults.beans.xml`、`nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/channel/TestChannelMessageServiceIoC.java`、`nop-ai/nop-ai-gateway/src/test/resources/_vfs/test/beans/test-channel-message-service-ioc-mode1.beans.xml`、`nop-ai/nop-ai-gateway/src/test/resources/_vfs/test/beans/test-channel-message-service-ioc-mode2.beans.xml`（注意 `_vfs/` 段，VFS 资源路径 `/test/beans/...` 由 classpath `_vfs/` 根映射）

- Item Types: `Fix | Proof`

- [x] `Fix` `channelMessageService` bean（`ai-gateway-defaults.beans.xml:69-82`）新增 `<property name="dispatchTimeoutMs" value="@cfg:nop.ai.gateway.channel.dispatchTimeoutMs|30000"/>`（镜像该文件既有 `<property value="..."/>` 惯例；property 行附一行注释说明默认值 30000 须与 `DEFAULT_DISPATCH_TIMEOUT_MS` 常量保持同步）。
- [x] `Proof` 第一步验证 @cfg 在测试容器可解析：跑接线测试；若 @cfg 在 `AppBeanContainerLoader` 环境下不解析（测试红），**先排查 loader 解析链**（`ConfigExpressionProcessor` → `ConfigValueResolver` → `BeanContainerImpl.getConfigValue` → `AppConfig` 的 configProvider——同一条加载路径对全部 91 处生产 @cfg 生效，若失效属 loader 级重大问题），确认为 loader 级问题则作为 **Blocker 上报**，不得静默改用 `-D`/config 注入绕过（失败模式为红非静默，实际触发概率低）。
- [x] `Fix` 接线验证测试（扩展 `TestChannelMessageServiceIoC` 或同包新增）：经 IoC 容器取 `channelMessageService` bean → **反射读 `dispatchTimeoutMs` 字段**（镜像 `readMessageService` :180-188 先例）断言默认 = 30000；再以 `AppConfig.getConfigProvider().assignConfigValue("nop.ai.gateway.channel.dispatchTimeoutMs", <正数，如 12345>)` **在 container.start/getBean 之前**设置 → 重建/重启容器 → 断言 bean 值 = 12345 → finally 复位原值（防跨测试泄漏）。**禁止**走「test beans.xml 固定 property 值」弱路径——那只证明 property→setter 连通，不证明「运维配置可覆盖」Goal。
- [x] `Fix` 测试装配同步：mode1 + mode2 两个 test beans.xml 的 `channelMessageService` bean 复制定义同步补同一 property（保持测试 = 生产装配的镜像，避免默认值断言靠字段初始化兜底的假绿）。
- [x] `Proof` 全模块反查：`grep -rn "nop.ai.gateway.channel.dispatchTimeoutMs" nop-ai/nop-ai-gateway` 至少命中 beans.xml（接线点）+ javadoc（承诺点）+ 测试；`ChannelMessageServiceImpl` 内其他字段无同类无接线配置字段（同类发现登记 follow-up）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ai-gateway-defaults.beans.xml` 含 `dispatchTimeoutMs` 的 `@cfg:` 接线 property（grep 可验证）
- [x] IoC 测试断言默认 30000 通过 + `assignConfigValue` 覆盖断言通过（接线运行时连通，非文本存在；反射观测 + finally 复位）
- [x] mode1 + mode2 两个 test beans.xml 均同步生产 property
- [x] `./mvnw test -pl nop-ai/nop-ai-gateway -am -Dtest=TestChannelMessageServiceIoC -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] No owner-doc update required（javadoc 承诺由接线兑现；`nop.ai.gateway.channel` 在 docs-for-ai 零命中已核实）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-9（dispatchTimeoutMs 死旋钮）已修复：配置项接线生效，javadoc 承诺兑现
- [x] 接线验证完成：property → setter → 字段 → 超时消费点（:351/:418）的运行时链路连通（IoC 测试默认值 + 覆盖值断言）
- [x] 必要 focused verification 已完成（默认值 + 覆盖值断言，反射观测 + 复位）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证接线不是文本摆设（覆盖值断言证明配置真实影响 bean 状态），无空壳/静默跳过
- [x] `./mvnw compile -pl nop-ai/nop-ai-gateway -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-gateway -am`
- [x] checkstyle / 代码规范检查通过（`./mvnw checkstyle:check -pl nop-ai -am`，按 I5/I6 既有口径记录 pre-existing 基线）

## Deferred But Adjudicated

### `ChannelMessageServiceImpl` 其他字段的无接线配置项检查

- Classification: `watch-only residual`
- Why Not Blocking Closure: Phase 1 Proof 项反查覆盖；如零发现本项不成立；如发现同类字段，仅登记 follow-up（不扩本 plan scope——本 plan 只修 AR-9 的 `dispatchTimeoutMs`）。
- Successor Required: `no`（复触发登记内复核）

## Non-Blocking Follow-ups

无。

## Closure

Status Note: AR-9 `dispatchTimeoutMs` 死旋钮已通过 @cfg 接线兑现 javadoc 承诺：property → setter（拒绝 `<=0` 校验原样保留）→ `dispatchTimeoutMs` 字段 → 超时消费点（:351 mode-2 / :418 mode-1）链路运行时连通，IoC 测试默认值 + `assignConfigValue` 覆盖值断言（反射观测 + finally 复位）证明配置真实影响 bean 状态（非文本摆设）。默认 30000 与 `DEFAULT_DISPATCH_TIMEOUT_MS` 一致；公共 API 不变（无新 getter）；双 test beans.xml 镜像同步。独立 closure audit APPROVED（0 Blocker / 0 Major）。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_009079c68ffefOAJ8LXmwkGi1i`，review-only，general agent）
- Evidence:
  - Phase 1 Exit Criteria（7 条逐条 PASS）：
    1. 生产 beans.xml `@cfg:nop.ai.gateway.channel.dispatchTimeoutMs|30000` 接线 property——`ai-gateway-defaults.beans.xml:85-86`（channelMessageService bean :69-87）+ 同步注释 :82-84
    2. mode1 :25-26 / mode2 :32-33 双 test beans.xml 镜像同步
    3. `dispatchTimeoutMsDefaultsTo30000`（TestChannelMessageServiceIoC.java:156-167，反射读字段断言 30_000，`readDispatchTimeoutMs` :245-253）+ `dispatchTimeoutMsFollowsAssignedConfigValue`（:177-195，`assignConfigValue(key, 12_345L)` 于 container.start 前设置 → 断言 12_345 → finally 复位 `AppConfig.var(key)`；null 复位语义 = StaticValue.valueOf(null) → 回退默认，已源码核实）；`git diff` 零改动 `ChannelMessageServiceImpl.java`（无新增 public getter，grep 全 nop-ai 仅 setter）
    4. diff 范围 = 5 文件（plan md + 生产 beans.xml + 测试 java + 2 test beans.xml），无越界改动
    5. `./mvnw test -pl nop-ai/nop-ai-gateway -am -Dtest=TestChannelMessageServiceIoC -Dsurefire.failIfNoSpecifiedTests=false` BUILD SUCCESS，surefire `tests="4" errors="0" failures="0"`（2 既有 + 2 新增）
    6. grep `nop.ai.gateway.channel.dispatchTimeoutMs` 命中生产 beans.xml + 双 test beans.xml + 测试 java + javadoc `ChannelMessageServiceImpl.java:100`（接线点/承诺点/测试三角闭环）
    7. Phase 1 Status `completed` + 5 items + 6 Exit Criteria 全 `[x]`
  - Closure Gates（10 条逐条 PASS）：`./mvnw compile -pl nop-ai/nop-ai-gateway -am` PASS；`./mvnw test -pl nop-ai/nop-ai-gateway -am` 4/4；`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS 全绿；`./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS；checkstyle `-Pqa` EXIT=0（默认-config 直跑 9164 pre-existing violation 全在未触碰上游 nop-api-core，I5/I6 同口径，受处理文件零命中）；`check-import-order --module nop-ai` 164 pre-existing 零新增（受处理文件零命中）；`scan-hollow-implementations.mjs --module nop-ai --severity high` 2 条 pre-existing（PlanReplanner:272 / NoOpProviderFailoverQueue:34）零新增
  - Anti-Hollow：@cfg 求值链源码核实（`ConfigExpressionProcessor.parsePrefixExpr` → `ConfigValueResolver.resolveValue:78` → `BeanContainerImpl.getConfigValue:429-430` → `configProvider.getConfigValue` AbstractConfigProvider:70-86，`assignConfigValue` 经 `makeConfigRef` 写入 usedRefs → `IConfigReference.get()` 返回覆盖值）；覆盖值断言（12345 in → 12345 out）证明配置真实影响 bean 状态，非文本摆设；无空方法体/静默跳过
  - Deferred 项分类检查：Deferred But Adjudicated = watch-only residual（兄弟字段反查零发现，不成立）；Non-Blocking Follow-ups = 无；无 in-scope live defect 被降级
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit 0（closure 后复跑确认）
  - 2 条 informational（非阻塞）：(1) 日志条目 = 本条对应 `ai-dev/logs/2026/08-13.md` 首条；(2) leak-guard 语义核实安全（null 复位回退默认，两新测试任意顺序绿）

Follow-up:

- no remaining plan-owned work（兄弟字段反查零发现；P2 其余 AR-5~8 由各自 plan 处理）

## Optional Sections

### Risks And Rollback

- 风险：(1) @cfg 在测试容器无先例——第一步验证，失败则换注入方式并记录（红非静默）；(2) `assignConfigValue` 全局静态泄漏——finally 复位 + 覆盖值设于 container.start 之前；(3) 只同步一个 test beans.xml 会导致另一文件覆盖断言红——plan 已列名两文件；(4) 加 public getter 会扩公共 API——观测锁定反射。
- Rollback：单 XML property + 测试改动，`git revert` 即可回滚；行为（默认 30000 超时）不变，回滚无运行时影响。
